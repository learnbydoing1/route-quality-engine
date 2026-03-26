package com.jeeny.rqe.service.generator;

import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RouteGeneratorTest {

    private RouteGenerator generator;
    private UUID tripId;

    private static final double START_LAT = 24.7136;
    private static final double START_LNG = 46.6753;
    private static final double END_LAT = 24.7500;
    private static final double END_LNG = 46.7200;

    @BeforeEach
    void setUp() {
        generator = new RouteGenerator();
        tripId = UUID.randomUUID();
    }

    @Test
    void generatePlannedRoute_returnsNonEmptyList() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        assertFalse(route.isEmpty());
    }

    @Test
    void generatePlannedRoute_firstPointNearStart() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        GpsPoint first = route.get(0);
        assertEquals(START_LAT, first.getLatitude(), 0.001);
        assertEquals(START_LNG, first.getLongitude(), 0.001);
    }

    @Test
    void generatePlannedRoute_lastPointNearEnd() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        GpsPoint last = route.get(route.size() - 1);
        assertEquals(END_LAT, last.getLatitude(), 0.001);
        assertEquals(END_LNG, last.getLongitude(), 0.001);
    }

    @Test
    void generatePlannedRoute_allPointsHavePlannedType() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        for (GpsPoint p : route) {
            assertEquals(PointType.PLANNED, p.getPointType());
        }
    }

    @Test
    void generatePlannedRoute_sequenceIndicesAreSequential() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        for (int i = 0; i < route.size(); i++) {
            assertEquals(i, route.get(i).getSequenceIndex(),
                    "Expected sequence index " + i + " at position " + i);
        }
    }

    @Test
    void generatePlannedRoute_allPointsHaveTimestamps() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        for (GpsPoint p : route) {
            assertNotNull(p.getTimestamp(), "All points must have a timestamp");
        }
    }

    @Test
    void generatePlannedRoute_timestampsAreNonDecreasing() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        for (int i = 1; i < route.size(); i++) {
            assertTrue(
                    !route.get(i).getTimestamp().isBefore(route.get(i - 1).getTimestamp()),
                    "Timestamps must be non-decreasing");
        }
    }

    @Test
    void generatePlannedRoute_allPointsHaveCorrectTripId() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        for (GpsPoint p : route) {
            assertEquals(tripId, p.getTripId());
        }
    }

    @Test
    void generatePlannedRoute_deterministicWithSeededRandom() {
        Random r1 = new Random(42);
        Random r2 = new Random(42);
        List<GpsPoint> route1 = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG, r1);
        List<GpsPoint> route2 = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG, r2);

        assertEquals(route1.size(), route2.size());
        for (int i = 0; i < route1.size(); i++) {
            assertEquals(route1.get(i).getLatitude(), route2.get(i).getLatitude(), 1e-12);
            assertEquals(route1.get(i).getLongitude(), route2.get(i).getLongitude(), 1e-12);
        }
    }

    @Test
    void generatePlannedRoute_hasMultiplePoints() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, END_LAT, END_LNG);
        assertTrue(route.size() > 10, "Route should have many interpolated points, got " + route.size());
    }

    @Test
    void generatePlannedRoute_sameStartAndEnd() {
        List<GpsPoint> route = generator.generatePlannedRoute(tripId, START_LAT, START_LNG, START_LAT, START_LNG);
        assertFalse(route.isEmpty());
        assertEquals(PointType.PLANNED, route.get(0).getPointType());
    }
}
