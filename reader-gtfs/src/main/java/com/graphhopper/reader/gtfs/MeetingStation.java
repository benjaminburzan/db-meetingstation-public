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

package com.graphhopper.reader.gtfs;

import com.conveyal.gtfs.GTFSFeed;
import com.graphhopper.Trip;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.graphhopper.reader.gtfs.Label.reverseEdges;

public class MeetingStation {

    public static void main(String[] args) {
        final PtFlagEncoder ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory("target/db");
        GtfsStorage gtfsStorage = GraphHopperGtfs.createGtfsStorage();
        GraphHopperStorage graphHopperStorage = new GraphHopperStorage(directory, encodingManager, false, gtfsStorage);
        graphHopperStorage.loadExisting();
        LocationIndex locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        final TranslationMap translationMap = GraphHopperGtfs.createTranslationMap();
        GraphHopperGtfs graphHopper = GraphHopperGtfs.createFactory(ptFlagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage)
                .createWithoutRealtimeFeed();

        final GTFSFeed db = gtfsStorage.getGtfsFeeds().get("gtfs_0");
        System.out.println(db.stops.size());
        db.stops.forEach((id, stop) -> System.out.printf("[%s] %s\n", id, stop.stop_name));

        Map<Integer, String> mapInversed =
                gtfsStorage.getStationNodes().entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

        int[] sources = new int[] {
            gtfsStorage.getStationNodes().get("8503000"),
                gtfsStorage.getStationNodes().get("8011160"),



        };

        Arrays.stream(sources)
                .mapToObj(source -> {
                    final PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(ptFlagEncoder, 0.0);
                    final MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(new GraphExplorer(graphHopperStorage, weighting, ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(), false), weighting, false, Double.MAX_VALUE, Double.MAX_VALUE, true, Integer.MAX_VALUE);
                    final Translation translation = translationMap.getWithFallBack(Locale.GERMAN);

                    final Set<Label> labels = router.calcPaths(source, Collections.emptySet(), Instant.now().minus(10, ChronoUnit.HOURS), Instant.now().minus(10, ChronoUnit.HOURS));
                    labels.forEach(label -> {
                        final List<Trip.Leg> legs = getLegs(ptFlagEncoder, graphHopperStorage, graphHopper, weighting, translation, label);

                        System.out.println(legs.toString());
                    });
                    System.out.println(router.getVisitedNodes());
                    return router.fromMap.asMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().mapToLong(l -> l.currentTime).min().getAsLong()));
                }).reduce(new HashMap<Integer, Long>(), (m, n) -> {
            final HashMap<Integer, Long> stringIntegerHashMap = new HashMap<>();
            m.forEach((k, v) -> stringIntegerHashMap.merge(k, v, (v1, v2) -> v1 + v2));
            n.forEach((k, v) -> stringIntegerHashMap.merge(k, v, (v1, v2) -> v1 + v2));
            return stringIntegerHashMap;
        }).entrySet().stream().filter(e -> mapInversed.containsKey(e.getKey()))
                .sorted(Comparator.comparingLong(Map.Entry::getValue))
                .limit(10).forEach(e -> {
            System.out.println(db.stops.get(mapInversed.get(e.getKey())).stop_name);
        });


    }

    private static List<Trip.Leg> getLegs(PtFlagEncoder ptFlagEncoder, GraphHopperStorage graphHopperStorage, GraphHopperGtfs graphHopper, PtTravelTimeWeighting weighting, Translation translation, Label label) {
        List<Label.Transition> transitions = new ArrayList<>();
        reverseEdges(label, graphHopperStorage, ptFlagEncoder, true)
                .forEach(transitions::add);
        Collections.reverse(transitions);
        return graphHopper.getLegs(ptFlagEncoder, translation, graphHopperStorage, weighting,
                graphHopper.getPartitions(transitions));
    }

}
