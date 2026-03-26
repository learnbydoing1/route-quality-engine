package com.jeeny.rqe.util;

import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilsTest {

    private static final UUID TRIP_ID = UUID.randomUUID();
    private static final double TOLERANCE = 0.01;

    // ── haversineKm ──

    @Test
    void haversineKm_knownDistanceBetweenRiyadhAndJeddah() {
        // Riyadh (24.7136, 46.6753) to Jeddah (21.4858, 39.1925) ≈ 845 km
        double dist = GeoUtils.haversineKm(24.7136, 46.6753, 21.4858, 39.1925);
        assertEquals(845.0, dist, 845.0 * 0.05); // within 5%
    }

    @Test
    void haversineKm_samePointReturnsZero() {
        double dist = GeoUtils.haversineKm(24.7136, 46.6753, 24.7136, 46.6753);
        assertEquals(0.0, dist, 1e-10);
    }

    @Test
    void haversineKm_symmetricDistance() {
        double d1 = GeoUtils.haversineKm(24.7, 46.7, 21.5, 39.2);
        double d2 = GeoUtils.haversineKm(21.5, 39.2, 24.7, 46.7);
        assertEquals(d1, d2, 1e-10);
    }

    @Test
    void haversineKm_shortDistanceOneKm() {
        // ~1 km north from a point: 1 km ≈ 0.00899 degrees lat
        double dist = GeoUtils.haversineKm(0.0, 0.0, 0.00899, 0.0);
        assertEquals(1.0, dist, 0.01);
    }

    // ── haversineMeters ──

    @Test
    void haversineMeters_returnsKmTimes1000() {
        double km = GeoUtils.haversineKm(24.7, 46.7, 24.8, 46.8);
        double m = GeoUtils.haversineMeters(24.7, 46.7, 24.8, 46.8);
        assertEquals(km * 1000.0, m, 1e-6);
    }

    @Test
    void haversineMeters_samePointReturnsZero() {
        assertEquals(0.0, GeoUtils.haversineMeters(10.0, 20.0, 10.0, 20.0), 1e-10);
    }

    // ── bearing ──

    @Test
    void bearing_dueNorthIsZero() {
        double b = GeoUtils.bearing(0.0, 0.0, 1.0, 0.0);
        assertEquals(0.0, b, 0.5);
    }

    @Test
    void bearing_dueEastIs90() {
        double b = GeoUtils.bearing(0.0, 0.0, 0.0, 1.0);
        assertEquals(90.0, b, 0.5);
    }

    @Test
    void bearing_dueSouthIs180() {
        double b = GeoUtils.bearing(1.0, 0.0, 0.0, 0.0);
        assertEquals(180.0, b, 0.5);
    }

    @Test
    void bearing_dueWestIs270() {
        double b = GeoUtils.bearing(0.0, 1.0, 0.0, 0.0);
        assertEquals(270.0, b, 0.5);
    }

    @Test
    void bearing_resultAlwaysBetween0And360() {
        double b = GeoUtils.bearing(10.0, 20.0, -5.0, -30.0);
        assertTrue(b >= 0.0 && b < 360.0);
    }

    // ── pointToSegmentDistanceMeters ──

    @Test
    void pointToSegmentDistance_pointOnLineReturnsNearZero() {
        double dist = GeoUtils.pointToSegmentDistanceMeters(
                0.005, 0.0, // midpoint of segment
                0.0, 0.0,
                0.01, 0.0);
        assertEquals(0.0, dist, 1.0); // within 1 meter
    }

    @Test
    void pointToSegmentDistance_perpendicularPoint() {
        // Segment along equator from (0,0) to (0,0.01), point at (0.001, 0.005) is off to the side
        double dist = GeoUtils.pointToSegmentDistanceMeters(
                0.001, 0.005,
                0.0, 0.0,
                0.0, 0.01);
        assertTrue(dist > 50.0, "Should be > 50m away from the segment");
        assertTrue(dist < 200.0, "Should be < 200m away (0.001 deg ≈ 111m)");
    }

    @Test
    void pointToSegmentDistance_pointProjectsBeyondSegmentEnd() {
        // Point is past end-B of the segment
        double dist = GeoUtils.pointToSegmentDistanceMeters(
                0.02, 0.0, // well past end
                0.0, 0.0,
                0.01, 0.0);
        double endDist = GeoUtils.haversineMeters(0.02, 0.0, 0.01, 0.0);
        assertEquals(endDist, dist, 1.0);
    }

    @Test
    void pointToSegmentDistance_pointProjectsBeforeSegmentStart() {
        double dist = GeoUtils.pointToSegmentDistanceMeters(
                -0.01, 0.0,
                0.0, 0.0,
                0.01, 0.0);
        double startDist = GeoUtils.haversineMeters(-0.01, 0.0, 0.0, 0.0);
        assertEquals(startDist, dist, 1.0);
    }

    @Test
    void pointToSegmentDistance_degenerateSegment() {
        // When A == B, should return distance from point to A
        double dist = GeoUtils.pointToSegmentDistanceMeters(
                0.01, 0.01,
                0.0, 0.0,
                0.0, 0.0);
        double expected = GeoUtils.haversineMeters(0.01, 0.01, 0.0, 0.0);
        assertEquals(expected, dist, 1e-6);
    }

    // ── projectOntoSegment ──

    @Test
    void projectOntoSegment_midpointProjectsToItself() {
        double[] result = GeoUtils.projectOntoSegment(
                0.005, 0.0,
                0.0, 0.0,
                0.01, 0.0);
        assertEquals(0.005, result[0], 1e-6);
        assertEquals(0.0, result[1], 1e-6);
    }

    @Test
    void projectOntoSegment_clampsToStartIfBefore() {
        double[] result = GeoUtils.projectOntoSegment(
                -0.01, 0.0,
                0.0, 0.0,
                0.01, 0.0);
        assertEquals(0.0, result[0], 1e-6);
        assertEquals(0.0, result[1], 1e-6);
    }

    @Test
    void projectOntoSegment_clampsToEndIfBeyond() {
        double[] result = GeoUtils.projectOntoSegment(
                0.02, 0.0,
                0.0, 0.0,
                0.01, 0.0);
        assertEquals(0.01, result[0], 1e-6);
        assertEquals(0.0, result[1], 1e-6);
    }

    @Test
    void projectOntoSegment_degenerateSegmentReturnsA() {
        double[] result = GeoUtils.projectOntoSegment(
                1.0, 1.0,
                5.0, 5.0,
                5.0, 5.0);
        assertEquals(5.0, result[0], 1e-10);
        assertEquals(5.0, result[1], 1e-10);
    }

    // ── totalDistanceKm ──

    @Test
    void totalDistanceKm_nullListReturnsZero() {
        assertEquals(0.0, GeoUtils.totalDistanceKm(null));
    }

    @Test
    void totalDistanceKm_emptyListReturnsZero() {
        assertEquals(0.0, GeoUtils.totalDistanceKm(Collections.emptyList()));
    }

    @Test
    void totalDistanceKm_singlePointReturnsZero() {
        List<GpsPoint> pts = List.of(makePoint(0.0, 0.0));
        assertEquals(0.0, GeoUtils.totalDistanceKm(pts));
    }

    @Test
    void totalDistanceKm_twoPoints() {
        GpsPoint a = makePoint(0.0, 0.0);
        GpsPoint b = makePoint(0.01, 0.0); // ~1.11 km
        double dist = GeoUtils.totalDistanceKm(List.of(a, b));
        assertTrue(dist > 1.0 && dist < 1.2, "Should be approximately 1.11 km, was " + dist);
    }

    @Test
    void totalDistanceKm_multiplePointsAdditive() {
        GpsPoint a = makePoint(0.0, 0.0);
        GpsPoint b = makePoint(0.01, 0.0);
        GpsPoint c = makePoint(0.02, 0.0);

        double abDist = GeoUtils.haversineKm(0.0, 0.0, 0.01, 0.0);
        double bcDist = GeoUtils.haversineKm(0.01, 0.0, 0.02, 0.0);
        double total = GeoUtils.totalDistanceKm(List.of(a, b, c));
        assertEquals(abDist + bcDist, total, 1e-10);
    }

    // ── metersToLatDegrees ──

    @Test
    void metersToLatDegrees_111320metersIsApproximatelyOneDegree() {
        double deg = GeoUtils.metersToLatDegrees(111_320.0);
        assertEquals(1.0, deg, 0.001);
    }

    @Test
    void metersToLatDegrees_zeroReturnsZero() {
        assertEquals(0.0, GeoUtils.metersToLatDegrees(0.0), 1e-15);
    }

    @Test
    void metersToLatDegrees_negativeMeters() {
        double deg = GeoUtils.metersToLatDegrees(-111_320.0);
        assertEquals(-1.0, deg, 0.001);
    }

    // ── metersToLngDegrees ──

    @Test
    void metersToLngDegrees_atEquatorSameAsLatDegrees() {
        double latDeg = GeoUtils.metersToLatDegrees(111_320.0);
        double lngDeg = GeoUtils.metersToLngDegrees(111_320.0, 0.0);
        assertEquals(latDeg, lngDeg, 0.001);
    }

    @Test
    void metersToLngDegrees_atHigherLatitudeLargerDegreeValue() {
        double atEquator = GeoUtils.metersToLngDegrees(1000.0, 0.0);
        double at60 = GeoUtils.metersToLngDegrees(1000.0, 60.0);
        assertTrue(at60 > atEquator,
                "Longitude degrees should be larger at higher latitude for the same distance");
    }

    @Test
    void metersToLngDegrees_zeroMetersReturnsZero() {
        assertEquals(0.0, GeoUtils.metersToLngDegrees(0.0, 45.0), 1e-15);
    }

    private GpsPoint makePoint(double lat, double lng) {
        return GpsPoint.builder()
                .tripId(TRIP_ID)
                .latitude(lat)
                .longitude(lng)
                .timestamp(Instant.now())
                .pointType(PointType.RAW)
                .sequenceIndex(0)
                .build();
    }
}
