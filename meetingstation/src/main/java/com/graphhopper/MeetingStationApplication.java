/*
 * Copyright 2017 GraphHopper GmbH.
 *
 * All rights reserved.
 *
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
