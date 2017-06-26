/*
 * Copyright 2017 GraphHopper GmbH.
 *
 * All rights reserved.
 *
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

        Set<String> visitedNodes = new HashSet<>();
        final Supplier<Boolean> goOn;
        if (request.targetStations != null) {
            final Set<String> targetStations = request.targetStations.stream().map(targetStation -> targetStation.stop_id).collect(Collectors.toSet());
            goOn = () -> !visitedNodes.containsAll(targetStations);
        } else {
            goOn = () -> true;
        }
        final Integer stationNode = gtfsStorage.getStationNodes().get(request.sourceStation.stop_id);
        if (stationNode == null) {
            throw new BadRequestException(String.format("station id %s not found", request.sourceStation.stop_id));
        }
        final PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(ptFlagEncoder, 0.0);
        final Translation tr = translationMap.getWithFallBack(Locale.GERMAN);
        final MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(new GraphExplorer(graphHopperStorage, weighting, ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(), false), weighting, false, Double.MAX_VALUE, Double.MAX_VALUE, false, false, Integer.MAX_VALUE);
        final Iterator<StopWithMeetingStationLabel> labelStream = router.getLabelStream(stationNode, -1, request.departureTime)
                .filter(label -> stopNodes.containsKey(label.node))
                .map(label -> new StopWithMeetingStationLabel(
                        db.stops.get(stopNodes.get(label.node)),
                        new MeetingStationLabel(Instant.ofEpochMilli(
                                label.currentTime),
                                label.nTransfers > 0 ?
                                        Duration.between(Instant.ofEpochMilli(label.departureTime), Instant.ofEpochMilli(label.currentTime)) :
                                        Duration.ZERO),
                        request.includePlans ?
                                new Trip(tripFromLabel.getTrip(false, ptFlagEncoder, tr, graphHopperStorage, weighting, label)) :
                                null))
                .filter(filter)
                .iterator();
        List<StopWithMeetingStationLabel> response = new ArrayList<>();
        while (labelStream.hasNext() && goOn.get()) {
            final StopWithMeetingStationLabel label = labelStream.next();
            visitedNodes.add(label.stop.stop_id);
            response.add(label);
        }
        return response;
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
