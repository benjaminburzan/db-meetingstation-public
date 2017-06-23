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

import com.bedatadriven.jackson.datatype.jts.JtsModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.dropwizard.Application;
import io.dropwizard.setup.Environment;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

public class MeetingStationApplication extends Application<MeetingStationConfiguration> {

    public static void main(String[] args) throws Exception {
        new MeetingStationApplication().run(args);
    }

    @Override
    public void run(MeetingStationConfiguration configuration, Environment environment) throws Exception {
        environment.getObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        environment.getObjectMapper().setSerializationInclusion(NON_NULL);
        environment.getObjectMapper().registerModule(new JavaTimeModule());
        environment.getObjectMapper().registerModule(new JtsModule());

        final MeetingStationService meetingStationService = new MeetingStationService(configuration);
        environment.lifecycle().manage(meetingStationService);
        environment.jersey().register(meetingStationService);

        environment.healthChecks().register("stations-database", new MeetingStationHealthCheck(meetingStationService));
    }
}
