package com.jeeny.rqe.service.engine;

import com.jeeny.rqe.model.AnomalyType;
import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PhysicsValidatorTest {

    private PhysicsValidator validator;
    private UUID tripId;

    @BeforeEach
    void setUp() {
        validator = new PhysicsValidator(150.0, 10);
        tripId = UUID.randomUUID();
    }

    // ── empty / single point ──

    @Test
    void validate_nullPointsReturnsEmptyResult() {
        PhysicsValidator.ValidationResult result = validator.validate(tripId, null);
        assertTrue(result.anomalies().isEmpty());
        assertTrue(result.segmentSpeedsKmh().isEmpty());
    }

    @Test
    void validate_emptyListReturnsEmptyResult() {
        PhysicsValidator.ValidationResult result = validator.validate(tripId, Collections.emptyList());
        assertTrue(result.anomalies().isEmpty());
        assertTrue(result.segmentSpeedsKmh().isEmpty());
    }

    @Test
    void validate_singlePointReturnsEmptyResult() {
        List<GpsPoint> pts = List.of(makePoint(24.7, 46.7, Instant.now(), 0));
        PhysicsValidator.ValidationResult result = validator.validate(tripId, pts);
        assertTrue(result.anomalies().isEmpty());
        assertTrue(result.segmentSpeedsKmh().isEmpty());
    }

    // ── normal speed ──

    @Test
    void validate_normalSpeedProducesNoAnomalies() {
        // ~40 km/h: 0.001 deg lat ≈ 111m in 10s => 40 km/h
        Instant base = Instant.now();
        List<GpsPoint> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(makePoint(24.7 + i * 0.001, 46.7, base.plusSeconds(i * 10), i));
        }

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);

        boolean hasSpeedAnomaly = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.UNREALISTIC_SPEED);
        assertFalse(hasSpeedAnomaly, "Normal speed should not trigger anomaly");
    }

    @Test
    void validate_normalSpeedHasCorrectSegmentCount() {
        Instant base = Instant.now();
        List<GpsPoint> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            points.add(makePoint(24.7 + i * 0.001, 46.7, base.plusSeconds(i * 10), i));
        }

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);
        assertEquals(4, result.segmentSpeedsKmh().size());
    }

    // ── unrealistic speed ──

    @Test
    void validate_unrealisticSpeedProducesAnomaly() {
        Instant base = Instant.now();
        // 0.01 deg lat ≈ 1.11km in 1 second => ~4000 km/h
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.71, 46.7, base.plusSeconds(1), 1));

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);

        boolean hasSpeed = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.UNREALISTIC_SPEED);
        assertTrue(hasSpeed, "Speed > 150 km/h should produce UNREALISTIC_SPEED anomaly");
    }

    @Test
    void validate_speedExactlyAtThresholdNoAnomaly() {
        Instant base = Instant.now();
        // 150 km/h = 150/3600 km per second = 0.04167 km/s
        // Over 10 seconds = 0.4167 km = 416.7 m
        // 416.7m in lat degrees ≈ 416.7/111320 ≈ 0.003744
        double latStep = (150.0 / 3600.0 * 10.0) / 111.32; // degrees for 150km/h over 10s
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.7 + latStep * 0.99, 46.7, base.plusSeconds(10), 1)); // just under 150

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);
        boolean hasSpeed = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.UNREALISTIC_SPEED);
        assertFalse(hasSpeed, "Speed just under 150 km/h should NOT trigger anomaly");
    }

    // ── signal gap ──

    @Test
    void validate_signalGapProducesAnomaly() {
        Instant base = Instant.now();
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.7001, 46.7, base.plusSeconds(15), 1)); // 15s gap > 10s threshold

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);

        boolean hasGap = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.SIGNAL_GAP);
        assertTrue(hasGap, "Time gap > 10s should produce SIGNAL_GAP anomaly");
    }

    @Test
    void validate_signalGapExactlyAtThresholdNoAnomaly() {
        Instant base = Instant.now();
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.7001, 46.7, base.plusSeconds(10), 1)); // exactly 10s

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);

        boolean hasGap = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.SIGNAL_GAP);
        assertFalse(hasGap, "Gap of exactly 10s should NOT trigger anomaly (threshold is >10)");
    }

    @Test
    void validate_normalGapNoAnomaly() {
        Instant base = Instant.now();
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.7001, 46.7, base.plusSeconds(5), 1)); // 5s gap

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);
        boolean hasGap = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.SIGNAL_GAP);
        assertFalse(hasGap);
    }

    // ── both anomalies ──

    @Test
    void validate_canDetectBothSpeedAndGapAnomalies() {
        Instant base = Instant.now();
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.7001, 46.7, base.plusSeconds(2), 1),
                makePoint(24.7002, 46.7, base.plusSeconds(20), 2), // 18s gap
                makePoint(24.8, 46.7, base.plusSeconds(21), 3));   // ~11 km in 1s => crazy speed

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);

        boolean hasGap = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.SIGNAL_GAP);
        boolean hasSpeed = result.anomalies().stream()
                .anyMatch(a -> a.getAnomalyType() == AnomalyType.UNREALISTIC_SPEED);

        assertTrue(hasGap, "Should detect signal gap");
        assertTrue(hasSpeed, "Should detect unrealistic speed");
    }

    // ── anomaly metadata ──

    @Test
    void validate_anomalyHasCorrectTripId() {
        Instant base = Instant.now();
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.7001, 46.7, base.plusSeconds(15), 1));

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);
        assertFalse(result.anomalies().isEmpty());
        result.anomalies().forEach(a -> assertEquals(tripId, a.getTripId()));
    }

    // ── zero time diff ──

    @Test
    void validate_zeroTimeDiffRecordsZeroSpeed() {
        Instant base = Instant.now();
        List<GpsPoint> points = List.of(
                makePoint(24.7, 46.7, base, 0),
                makePoint(24.7001, 46.7, base, 1)); // same timestamp

        PhysicsValidator.ValidationResult result = validator.validate(tripId, points);
        assertEquals(1, result.segmentSpeedsKmh().size());
        assertEquals(0.0, result.segmentSpeedsKmh().get(0), 1e-10);
    }

    private GpsPoint makePoint(double lat, double lng, Instant ts, int seq) {
        return GpsPoint.builder()
                .tripId(tripId)
                .latitude(lat)
                .longitude(lng)
                .timestamp(ts)
                .pointType(PointType.CORRECTED)
                .sequenceIndex(seq)
                .build();
    }
}
