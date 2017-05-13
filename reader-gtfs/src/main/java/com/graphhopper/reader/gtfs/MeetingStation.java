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
import java.util.*;

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

        int a = gtfsStorage.getStationNodes().get("8000237");
        int b = gtfsStorage.getStationNodes().get("8000238");

        final PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(ptFlagEncoder, 0.0);
        final MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(new GraphExplorer(graphHopperStorage, weighting, ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(), false), weighting, false, Double.MAX_VALUE, Double.MAX_VALUE, true, 10000);
        final Translation translation = translationMap.getWithFallBack(Locale.GERMAN);

        final Set<Label> labels = router.calcPaths(a, Collections.singleton(b), Instant.now(), Instant.now());
        labels.forEach(label -> {
            final List<Trip.Leg> legs = getLegs(ptFlagEncoder, graphHopperStorage, graphHopper, weighting, translation, label);

            System.out.println(legs.toString());
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
