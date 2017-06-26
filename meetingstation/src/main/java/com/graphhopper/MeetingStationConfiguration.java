/*
 * Copyright 2017 GraphHopper GmbH.
 *
 * All rights reserved.
 *
 */

package com.graphhopper;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import org.hibernate.validator.constraints.NotEmpty;

public class MeetingStationConfiguration extends Configuration {

    @NotEmpty
    private String graphLocation;

    @NotEmpty
    private String gtfsFile;

    @JsonProperty
    public String getGraphLocation() {
        return graphLocation;
    }

    @JsonProperty
    public void setGraphLocation(String graphLocation) {
        this.graphLocation = graphLocation;
    }

    @JsonProperty
    public String getGtfsFile() {
        return gtfsFile;
    }

    @JsonProperty
    public void setGtfsFile(String gtfsFile) {
        this.gtfsFile = gtfsFile;
    }

}
