/*
 * Copyright 2017 GraphHopper GmbH.
 *
 * All rights reserved.
 *
 */

package com.graphhopper;

import com.codahale.metrics.health.HealthCheck;

public class MeetingStationHealthCheck extends HealthCheck {

    private final MeetingStationService meetingStationService;

    public MeetingStationHealthCheck(MeetingStationService meetingStationService) {
        this.meetingStationService = meetingStationService;
    }

    @Override
    protected Result check() throws Exception {
        return meetingStationService.getStations().stream().limit(1).count() == 1 ?
                Result.healthy() :
                Result.unhealthy("Stations database is empty.");
    }

}
