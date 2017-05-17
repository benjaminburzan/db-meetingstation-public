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
import com.graphhopper.reader.gtfs.*;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GHDirectory;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.util.Translation;
import com.graphhopper.util.TranslationMap;
import io.dropwizard.lifecycle.Managed;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.graphhopper.reader.gtfs.Label.reverseEdges;

@Path("stations")
@Produces(MediaType.APPLICATION_JSON)
public class MeetingStationService implements Managed {

    private PtFlagEncoder ptFlagEncoder;
    private Map<Integer, String> mapInversed;
    private GraphHopperStorage graphHopperStorage;
    private GtfsStorage gtfsStorage;
    private TranslationMap translationMap;
    private GraphHopperGtfs graphHopper;
    private LocationIndex locationIndex;

    @GET
    public List<String> getStations() {
        final GTFSFeed db = gtfsStorage.getGtfsFeeds().get("gtfs_0");

        int[] sources = new int[] {
            gtfsStorage.getStationNodes().get("8000105"),
                gtfsStorage.getStationNodes().get("8011160"),



        };

        final BiFunction<Long, Long, Long> aggregation = Math::max;

        return Arrays.stream(sources)
            .mapToObj(source -> {
                final PtTravelTimeWeighting weighting = new PtTravelTimeWeighting(ptFlagEncoder, 0.0);
                final MultiCriteriaLabelSetting router = new MultiCriteriaLabelSetting(new GraphExplorer(graphHopperStorage, weighting, ptFlagEncoder, gtfsStorage, RealtimeFeed.empty(), false), weighting, false, Double.MAX_VALUE, Double.MAX_VALUE, true, Integer.MAX_VALUE);
                final Translation translation = translationMap.getWithFallBack(Locale.GERMAN);

                final Set<Label> labels = router.calcPaths(source, Collections.emptySet(), Instant.now(), Instant.now());
                labels.forEach(label -> {
                    final List<Trip.Leg> legs = getLegs(ptFlagEncoder, graphHopperStorage, graphHopper, weighting, translation, label);

                    System.out.println(legs.toString());
                });
                System.out.println(router.getVisitedNodes());
                return router.fromMap.asMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().mapToLong(l -> l.currentTime).min().getAsLong()));
            })
            .reduce(new HashMap<>(), (m, n) -> {
                final HashMap<Integer, Long> stringIntegerHashMap = new HashMap<>();
                m.forEach((k, v) -> stringIntegerHashMap.merge(k, v, aggregation));
                n.forEach((k, v) -> stringIntegerHashMap.merge(k, v, aggregation));
                return stringIntegerHashMap;
            })
            .entrySet().stream().filter(e -> mapInversed.containsKey(e.getKey()))
            .sorted(Comparator.comparingLong(Map.Entry::getValue))
            .limit(10)
            .map(e -> db.stops.get(mapInversed.get(e.getKey())).stop_name)
            .collect(Collectors.toList());
    }

    private static List<Trip.Leg> getLegs(PtFlagEncoder ptFlagEncoder, GraphHopperStorage graphHopperStorage, GraphHopperGtfs graphHopper, PtTravelTimeWeighting weighting, Translation translation, Label label) {
        List<Label.Transition> transitions = new ArrayList<>();
        reverseEdges(label, graphHopperStorage, ptFlagEncoder, true)
                .forEach(transitions::add);
        Collections.reverse(transitions);
        return graphHopper.getLegs(ptFlagEncoder, translation, graphHopperStorage, weighting,
                graphHopper.getPartitions(transitions));
    }


    @Override
    public void start() throws Exception {
        ptFlagEncoder = new PtFlagEncoder();
        EncodingManager encodingManager = new EncodingManager(Arrays.asList(ptFlagEncoder), 8);
        GHDirectory directory = GraphHopperGtfs.createGHDirectory("target/db");
        gtfsStorage = GraphHopperGtfs.createGtfsStorage();

        graphHopperStorage = GraphHopperGtfs.createOrLoad(directory, encodingManager, ptFlagEncoder, gtfsStorage, false, Collections.singletonList("/Users/michaelzilske/git/db-fv-gtfs/2017/2017.zip"), Collections.emptyList());

        locationIndex = GraphHopperGtfs.createOrLoadIndex(directory, graphHopperStorage);
        translationMap = GraphHopperGtfs.createTranslationMap();
        graphHopper = GraphHopperGtfs.createFactory(ptFlagEncoder, translationMap, graphHopperStorage, locationIndex, gtfsStorage)
                .createWithoutRealtimeFeed();

        mapInversed = gtfsStorage.getStationNodes().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    }

    @Override
    public void stop() throws Exception {
        locationIndex.close();
        graphHopperStorage.close();
    }
}
