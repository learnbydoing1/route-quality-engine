package com.jeeny.rqe.service.engine;

import com.jeeny.rqe.model.AnomalyType;
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

class MapMatcherTest {

    private MapMatcher matcher;
    private UUID tripId;

    @BeforeEach
    void setUp() {
        matcher = new MapMatcher(50.0);
        tripId = UUID.randomUUID();
    }

    // ── empty inputs ──

    @Test
    void matchToRoute_nullRawPointsReturnsEmptyResult() {
        List<GpsPoint> planned = makePlannedRoute(10);
        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, null, planned);
        assertTrue(result.correctedPoints().isEmpty());
        assertTrue(result.jitterAnomalies().isEmpty());
        assertTrue(result.deviations().isEmpty());
    }

    @Test
    void matchToRoute_emptyRawPointsReturnsEmptyResult() {
        List<GpsPoint> planned = makePlannedRoute(10);
        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, Collections.emptyList(), planned);
        assertTrue(result.correctedPoints().isEmpty());
    }

    @Test
    void matchToRoute_nullPlannedRouteReturnsEmptyResult() {
        List<GpsPoint> raw = makeRawPoints(10, 0.0);
        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, null);
        assertTrue(result.correctedPoints().isEmpty());
    }

    @Test
    void matchToRoute_singlePointPlannedRouteReturnsEmpty() {
        List<GpsPoint> planned = List.of(makePoint(24.7, 46.7, PointType.PLANNED, 0));
        List<GpsPoint> raw = makeRawPoints(5, 0.0);
        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);
        assertTrue(result.correctedPoints().isEmpty());
    }

    // ── points on route ──

    @Test
    void matchToRoute_pointsOnRouteHaveNearZeroDeviation() {
        List<GpsPoint> planned = makePlannedRoute(20);
        List<GpsPoint> raw = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            GpsPoint p = planned.get(i * 2);
            raw.add(makePoint(p.getLatitude(), p.getLongitude(), PointType.RAW, i));
        }

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);
        for (double dev : result.deviations()) {
            assertTrue(dev < 1.0, "On-route point should have near-zero deviation, was " + dev);
        }
        assertTrue(result.jitterAnomalies().isEmpty(), "No jitter anomalies expected for on-route points");
    }

    // ── points far from route ──

    @Test
    void matchToRoute_farPointsFlaggedAsJitter() {
        List<GpsPoint> planned = makePlannedRoute(10);
        List<GpsPoint> raw = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            raw.add(makePoint(24.7 + i * 0.0001, 46.71, PointType.RAW, i)); // far from route on lng=46.7
        }

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);

        boolean hasJitter = result.jitterAnomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.GPS_JITTER);
        assertTrue(hasJitter, "Points far from route should produce GPS_JITTER anomalies");
    }

    // ── corrected points count ──

    @Test
    void matchToRoute_correctedPointsCountEqualsRawCount() {
        List<GpsPoint> planned = makePlannedRoute(20);
        List<GpsPoint> raw = makeRawPoints(15, 0.0002);

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);
        assertEquals(raw.size(), result.correctedPoints().size());
    }

    // ── corrected point type ──

    @Test
    void matchToRoute_allCorrectedPointsHaveCorrectedType() {
        List<GpsPoint> planned = makePlannedRoute(10);
        List<GpsPoint> raw = makeRawPoints(8, 0.0001);

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);
        for (GpsPoint p : result.correctedPoints()) {
            assertEquals(PointType.CORRECTED, p.getPointType());
        }
    }

    // ── deviations list ──

    @Test
    void matchToRoute_deviationsListSizeEqualsRawCount() {
        List<GpsPoint> planned = makePlannedRoute(10);
        List<GpsPoint> raw = makeRawPoints(7, 0.0001);

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);
        assertEquals(raw.size(), result.deviations().size());
    }

    // ── corrected points are on the planned route ──

    @Test
    void matchToRoute_correctedPointsLieOnPlannedRoute() {
        List<GpsPoint> planned = makePlannedRoute(20);
        List<GpsPoint> raw = makeRawPoints(10, 0.001); // offset from route

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);

        for (GpsPoint cp : result.correctedPoints()) {
            double minDist = Double.MAX_VALUE;
            for (int i = 0; i < planned.size() - 1; i++) {
                GpsPoint a = planned.get(i);
                GpsPoint b = planned.get(i + 1);
                double dist = GeoUtils.pointToSegmentDistanceMeters(
                        cp.getLatitude(), cp.getLongitude(),
                        a.getLatitude(), a.getLongitude(),
                        b.getLatitude(), b.getLongitude());
                minDist = Math.min(minDist, dist);
            }
            assertTrue(minDist < 1.0,
                    "Corrected point should lie on planned route, was " + minDist + "m away");
        }
    }

    // ── sequencing ──

    @Test
    void matchToRoute_correctedPointsHaveSequentialIndices() {
        List<GpsPoint> planned = makePlannedRoute(10);
        List<GpsPoint> raw = makeRawPoints(5, 0.0001);

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);
        for (int i = 0; i < result.correctedPoints().size(); i++) {
            assertEquals(i, result.correctedPoints().get(i).getSequenceIndex());
        }
    }

    // ── anomaly details ──

    @Test
    void matchToRoute_jitterAnomalyContainsCorrectType() {
        List<GpsPoint> planned = makePlannedRoute(5);
        List<GpsPoint> raw = List.of(
                makePoint(25.0, 47.0, PointType.RAW, 0), // very far
                makePoint(25.0, 47.0, PointType.RAW, 1),
                makePoint(25.0, 47.0, PointType.RAW, 2));

        MapMatcher.MatchResult result = matcher.matchToRoute(tripId, raw, planned);
        assertFalse(result.jitterAnomalies().isEmpty());
        result.jitterAnomalies().forEach(a -> {
            assertEquals(AnomalyType.GPS_JITTER, a.getAnomalyType());
            assertEquals(tripId, a.getTripId());
        });
    }

    private List<GpsPoint> makePlannedRoute(int count) {
        List<GpsPoint> points = new ArrayList<>();
        Instant base = Instant.now();
        for (int i = 0; i < count; i++) {
            points.add(GpsPoint.builder()
                    .tripId(tripId)
                    .latitude(24.7 + i * 0.0005)
                    .longitude(46.7 + i * 0.0005)
                    .timestamp(base.plusSeconds(i))
                    .pointType(PointType.PLANNED)
                    .sequenceIndex(i)
                    .build());
        }
        return points;
    }

    private List<GpsPoint> makeRawPoints(int count, double latOffset) {
        List<GpsPoint> points = new ArrayList<>();
        Instant base = Instant.now();
        for (int i = 0; i < count; i++) {
            points.add(GpsPoint.builder()
                    .tripId(tripId)
                    .latitude(24.7 + i * 0.0005 + latOffset)
                    .longitude(46.7 + i * 0.0005)
                    .timestamp(base.plusSeconds(i))
                    .pointType(PointType.RAW)
                    .sequenceIndex(i)
                    .build());
        }
        return points;
    }

    private GpsPoint makePoint(double lat, double lng, PointType type, int seq) {
        return GpsPoint.builder()
                .tripId(tripId)
                .latitude(lat)
                .longitude(lng)
                .timestamp(Instant.now())
                .pointType(type)
                .sequenceIndex(seq)
                .build();
    }
}
