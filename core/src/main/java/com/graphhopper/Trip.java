package com.graphhopper;

import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.InstructionList;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import java.time.Instant;
import java.util.List;

public class Trip {
    public static abstract class Leg {
        public final String type;
        public final String departureLocation;
        public final Instant departureTime;
        public final Geometry geometry;
        public final double distance;
        public final Instant arrivalTime;

        public Leg(String type, String departureLocation, Instant departureTime, Geometry geometry, double distance, Instant arrivalTime) {
            this.type = type;
            this.departureLocation = departureLocation;
            this.geometry = geometry;
            this.distance = distance;
            this.departureTime = departureTime;
            this.arrivalTime = arrivalTime;
        }

        public double getDistance() {
            return distance;
        }
    }

    public static class Stop {
        public final String stop_id;
        public final String name;
        public final Point geometry;

        public final Instant arrivalTime;
        public final Instant departureTime;

        public Stop(String stop_id, String name, Point geometry, Instant arrivalTime, Instant departureTime) {
            this.stop_id = stop_id;
            this.name = name;
            this.geometry = geometry;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
        }
    }
    public static class WalkLeg extends Leg {
        public final InstructionList instructions;

        public WalkLeg(String departureLocation, Instant departureTime, List<EdgeIteratorState> edges, Geometry geometry, double distance, InstructionList instructions, Instant arrivalTime) {
            super("walk", departureLocation, departureTime, geometry, distance, arrivalTime);
            this.instructions = instructions;
        }
    }
    public static class PtLeg extends Leg {
        public final String feedId;
        public final boolean isInSameVehicleAsPrevious;
        public final String trip_headsign;
        public final long travelTime;
        public final List<Stop> stops;
        public final String tripId;
        public final String routeId;

        public PtLeg(String feedId, boolean isInSameVehicleAsPrevious, String tripId, String routeId, List<EdgeIteratorState> edges, Instant departureTime, List<Stop> stops, double distance, long travelTime, Instant arrivalTime, Geometry geometry) {
            super("pt", stops.get(0).name, departureTime, geometry, distance, arrivalTime);
            this.feedId = feedId;
            this.isInSameVehicleAsPrevious = isInSameVehicleAsPrevious;
            this.tripId = tripId;
            this.routeId = routeId;
            this.trip_headsign = edges.get(0).getName();
            this.travelTime = travelTime;
            this.stops = stops;
        }

        public String toString() {
            return trip_headsign;
        }

    }

    public final List<Leg> legs;

    public Trip(List<Leg> legs) {
        this.legs = legs;
    }
}
