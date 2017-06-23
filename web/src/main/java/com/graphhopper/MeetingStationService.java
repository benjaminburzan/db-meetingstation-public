/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.graphhopper;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Stop;
import com.graphhopper.reader.gtfs.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import io.dropwizard.lifecycle.Managed;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Path("stations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MeetingStationService implements Managed {

    private final MeetingStationConfiguration configuration;

    private PtFlagEncoder ptFlagEncoder;
    private Map<Integer, String> stopNodes;
    private GraphHopperStorage graphHopperStorage;
    private GtfsStorage gtfsStorage;
    private LocationIndex locationIndex;
    private TripFromLabel tripFromLabel;
    private TranslationMap translationMap;

    MeetingStationService(MeetingStationConfiguration configuration) {
        this.configuration = configuration;
    }

    @GET
    public Collection<Stop> getStations() {
        return gtfsStorage.getGtfsFeeds().get("gtfs_0").stops.values();
    }

    static class StopWithMeetingStationLabel {
        public Stop stop;
        public MeetingStationLabel label;
        public Trip plan;

        public StopWithMeetingStationLabel(Stop stop, MeetingStationLabel label, Trip plan) {
            this.stop = stop;
            this.label = label;
            this.plan = plan;
        }
    }

    class MeetingStationLabel {
        public Instant arrivalTime;
        public Duration travelTime;

        public MeetingStationLabel(Instant arrivalTime, Duration travelTime) {
            this.arrivalTime = arrivalTime;
            this.travelTime = travelTime;
        }
    }

    static class StationRequest {
        public @NotNull Stop sourceStation;
        public Collection<Stop> targetStations;
        public Instant departureTime = Instant.now();
        public boolean includePlans = false;
    }



    @POST
    public List<StopWithMeetingStationLabel> getStations(@Valid StationRequest request) {
        final GTFSFeed db = gtfsStorage.getGtfsFeeds().get("gtfs_0");

        final Predicate<? super StopWithMeetingStationLabel> filter;
        if (request.targetStations != null) {
            final Set<String> targetIds = request.targetStations.stream().map(targetStation -> targetStation.stop_id).collect(Collectors.toSet());
            filter = label -> targetIds.contains(label.stop.stop_id);
        } else {
            filter = label -> true;
        }

        Set<Integer> visitedNodes = new HashSet<>();
        final Supplier<Boolean> goOn;
        if (request.targetStations != null) {
            final Set<Integer> targetIds = request.targetStations.stream().map(targetStation -> gtfsStorage.getStationNodes().get(targetStation.stop_id)).collect(Collectors.toSet());
            goOn = () -> !visitedNodes.containsAll(targetIds);
        } else {
            goOn = () -> true;
        }
        final Integer stationNode = gtfsStorage.getStationNodes().get(request.sourceStation.stop_id);
        if (stationNode == null) {
            throw new BadRequestException(String.format("station id %s not found", request.sourceStation.stop_id));
        }
        final PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(ptFlagEncoder, 0.0);
        final MultiCriteriaLabelSetting.Visitor visitor = nEdge -> visitedNodes.add(nEdge.adjNode);
        final MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(new GraphExplorer(graphHopperStorage, weighting, ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(), false), weighting, false, Double.MAX_VALUE, Double.MAX_VALUE, false, Integer.MAX_VALUE, visitor, goOn);
        router.calcPaths(stationNode, Collections.emptySet(), request.departureTime, request.departureTime);

        final Map<Integer, Label> tree = router.fromMap.asMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().min(Comparator.comparingLong(l -> l.currentTime)).get()));
        Translation tr = translationMap.getWithFallBack(Locale.GERMAN);
        return tree
            .entrySet().stream().filter(e -> stopNodes.containsKey(e.getKey()))
            .sorted(Comparator.comparingLong(e -> e.getValue().currentTime))
            .map(e -> new StopWithMeetingStationLabel(
                    db.stops.get(stopNodes.get(e.getKey())),
                    new MeetingStationLabel(Instant.ofEpochMilli(
                            e.getValue().currentTime),
                            e.getValue().nTransfers > 0 ?
                                    Duration.between(Instant.ofEpochMilli(e.getValue().firstPtDepartureTime), Instant.ofEpochMilli(e.getValue().currentTime)) :
                                    Duration.ZERO),
                    request.includePlans ?
                            new Trip(tripFromLabel.getTrip(false, ptFlagEncoder, tr, graphHopperStorage, weighting, e.getValue())) :
                            null))
            .filter(filter)
            .collect(Collectors.toList());
    }

    @Override
    public void start() throws Exception {
        ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory(configuration.getGraphLocation());
        gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, false, Collections.singletonList(configuration.getGtfsFile()), Collections.emptyList());
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        stopNodes = gtfsStorage.getStationNodes().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        tripFromLabel = new TripFromLabel(gtfsStorage);
        translationMap = GraphHopperGtfs.createTranslationMap();
    }

    @Override
    public void stop() throws Exception {
        locationIndex.close();
        graphHopperStorage.close();
    }
}
