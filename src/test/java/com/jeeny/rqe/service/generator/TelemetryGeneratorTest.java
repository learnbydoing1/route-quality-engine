package com.jeeny.rqe.service.generator;

import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import com.jeeny.rqe.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryGeneratorTest {

    private TelemetryGenerator generator;
    private RouteGenerator routeGenerator;
    private UUID tripId;

    @BeforeEach
    void setUp() {
        generator = new TelemetryGenerator();
        routeGenerator = new RouteGenerator();
        tripId = UUID.randomUUID();
    }

    @Test
    void generateIdealTelemetry_returnsNonEmptyFromValidRoute() {
        List<GpsPoint> planned = routeGenerator.generatePlannedRoute(
                tripId, 24.7136, 46.6753, 24.7500, 46.7200);
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);
        assertFalse(telemetry.isEmpty());
    }

    @Test
    void generateIdealTelemetry_allPointsHaveRawType() {
        List<GpsPoint> planned = routeGenerator.generatePlannedRoute(
                tripId, 24.7136, 46.6753, 24.7500, 46.7200);
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);
        for (GpsPoint p : telemetry) {
            assertEquals(PointType.RAW, p.getPointType());
        }
    }

    @Test
    void generateIdealTelemetry_nullPlannedRouteReturnsEmpty() {
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, null);
        assertTrue(telemetry.isEmpty());
    }

    @Test
    void generateIdealTelemetry_emptyPlannedRouteReturnsEmpty() {
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, Collections.emptyList());
        assertTrue(telemetry.isEmpty());
    }

    @Test
    void generateIdealTelemetry_singlePointPlannedRouteReturnsEmpty() {
        List<GpsPoint> singlePoint = List.of(makePoint(24.7, 46.7, 0));
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, singlePoint);
        assertTrue(telemetry.isEmpty());
    }

    @Test
    void generateIdealTelemetry_pointsLieNearPlannedRoute() {
        List<GpsPoint> planned = routeGenerator.generatePlannedRoute(
                tripId, 24.7136, 46.6753, 24.7500, 46.7200);
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);

        for (GpsPoint t : telemetry) {
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < planned.size() - 1; i++) {
                GpsPoint a = planned.get(i);
                GpsPoint b = planned.get(i + 1);
                double dist = GeoUtils.pointToSegmentDistanceMeters(
                        t.getLatitude(), t.getLongitude(),
                        a.getLatitude(), a.getLongitude(),
                        b.getLatitude(), b.getLongitude());
                minDist = Math.min(minDist, dist);
            }
            assertTrue(minDist < 5.0,
                    "Ideal telemetry point should be within 5m of planned route, was " + minDist + "m");
        }
    }

    @Test
    void generateIdealTelemetry_hasSequentialIndices() {
        List<GpsPoint> planned = routeGenerator.generatePlannedRoute(
                tripId, 24.7136, 46.6753, 24.7500, 46.7200);
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);
        for (int i = 0; i < telemetry.size(); i++) {
            assertEquals(i, telemetry.get(i).getSequenceIndex());
        }
    }

    @Test
    void generateIdealTelemetry_allPointsHaveTimestamps() {
        List<GpsPoint> planned = routeGenerator.generatePlannedRoute(
                tripId, 24.7136, 46.6753, 24.7500, 46.7200);
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);
        for (GpsPoint p : telemetry) {
            assertNotNull(p.getTimestamp());
        }
    }

    @Test
    void generateIdealTelemetry_timestampsAreOneSecondApart() {
        List<GpsPoint> planned = routeGenerator.generatePlannedRoute(
                tripId, 24.7136, 46.6753, 24.7500, 46.7200);
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);

        for (int i = 1; i < telemetry.size(); i++) {
            long diff = telemetry.get(i).getTimestamp().getEpochSecond()
                    - telemetry.get(i - 1).getTimestamp().getEpochSecond();
            assertEquals(1, diff, "Consecutive timestamps should be 1 second apart");
        }
    }

    @Test
    void generateIdealTelemetry_allPointsHaveCorrectTripId() {
        List<GpsPoint> planned = routeGenerator.generatePlannedRoute(
                tripId, 24.7136, 46.6753, 24.7500, 46.7200);
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);
        for (GpsPoint p : telemetry) {
            assertEquals(tripId, p.getTripId());
        }
    }

    @Test
    void generateIdealTelemetry_twoCoincidentPointsReturnsSinglePoint() {
        List<GpsPoint> planned = List.of(
                makePoint(24.7, 46.7, 0),
                makePoint(24.7, 46.7, 1));
        List<GpsPoint> telemetry = generator.generateIdealTelemetry(tripId, planned);
        assertFalse(telemetry.isEmpty());
        assertEquals(PointType.RAW, telemetry.get(0).getPointType());
    }

    private GpsPoint makePoint(double lat, double lng, int seq) {
        return GpsPoint.builder()
                .tripId(tripId)
                .latitude(lat)
                .longitude(lng)
                .timestamp(Instant.now().plusSeconds(seq))
                .pointType(PointType.PLANNED)
                .sequenceIndex(seq)
                .build();
    }
}
