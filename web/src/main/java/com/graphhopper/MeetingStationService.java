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
import io.dropwizard.lifecycle.Managed;

import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

@Path("stations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MeetingStationService implements Managed {

    private PtFlagEncoder ptFlagEncoder;
    private Map<Integer, String> stopNodes;
    private GraphHopperStorage graphHopperStorage;
    private GtfsStorage gtfsStorage;
    private LocationIndex locationIndex;

    @GET
    public Collection<Stop> getStations() {
        return gtfsStorage.getGtfsFeeds().get("gtfs_0").stops.values();
    }

    static class StopWithMeetingStationLabel {
        public Stop stop;
        public MeetingStationLabel label;

        public StopWithMeetingStationLabel(Stop stop, MeetingStationLabel label) {
            this.stop = stop;
            this.label = label;
        }
    }

    class MeetingStationLabel {
        public Instant arrivalTime;

        public MeetingStationLabel(Instant arrivalTime) {
            this.arrivalTime = arrivalTime;
        }
    }

    static class StationRequest {
        public @NotNull Collection<Stop> sourceStations;
        public Instant departureTime = Instant.now();
    }

    @POST
    public List<StopWithMeetingStationLabel> getStations(StationRequest request) {
        final GTFSFeed db = gtfsStorage.getGtfsFeeds().get("gtfs_0");

        final BiFunction<Long, Long, Long> aggregation = Math::max;

        return request.sourceStations.stream()
            .map(stop -> {
                final Integer stationNode = gtfsStorage.getStationNodes().get(stop.stop_id);
                if (stationNode == null) {
                    throw new BadRequestException(String.format("station id %s not found", stop.stop_id));
                }
                return stationNode;
            })
            .map(source -> {
                final PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(ptFlagEncoder, 0.0);
                final MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(new GraphExplorer(graphHopperStorage, weighting, ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(), false), weighting, false, Double.MAX_VALUE, Double.MAX_VALUE, true, Integer.MAX_VALUE);
                router.calcPaths(source, Collections.emptySet(), request.departureTime, request.departureTime);
                return router.fromMap.asMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().mapToLong(l -> l.currentTime).min().getAsLong()));
            })
            .reduce(new HashMap<>(), (m, n) -> {
                final HashMap<Integer, Long> stringIntegerHashMap = new HashMap<>();
                m.forEach((k, v) -> stringIntegerHashMap.merge(k, v, aggregation));
                n.forEach((k, v) -> stringIntegerHashMap.merge(k, v, aggregation));
                return stringIntegerHashMap;
            })
            .entrySet().stream().filter(e -> stopNodes.containsKey(e.getKey()))
            .sorted(Comparator.comparingLong(Map.Entry::getValue))
            .map(e -> new StopWithMeetingStationLabel(db.stops.get(stopNodes.get(e.getKey())), new MeetingStationLabel(Instant.ofEpochMilli(e.getValue()))))
            .collect(Collectors.toList());
    }

    @Override
    public void start() throws Exception {
        ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory("target/db");
        gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, false, Collections.singletonList("2017.zip"), Collections.emptyList());
        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        stopNodes = gtfsStorage.getStationNodes().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    }

    @Override
    public void stop() throws Exception {
        locationIndex.close();
        graphHopperStorage.close();
    }
}