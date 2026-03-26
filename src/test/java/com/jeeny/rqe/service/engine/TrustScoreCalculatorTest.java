package com.jeeny.rqe.service.engine;

import com.jeeny.rqe.model.Anomaly;
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

class TrustScoreCalculatorTest {

    private TrustScoreCalculator calculator;
    private UUID tripId;

    @BeforeEach
    void setUp() {
        calculator = new TrustScoreCalculator(0.40, 0.30, 0.30, 50.0);
        tripId = UUID.randomUUID();
    }

    // ── perfect data ──

    @Test
    void calculate_perfectDataScoreNear100() {
        List<Double> deviations = List.of(0.0, 0.0, 0.0, 0.0, 0.0);
        List<GpsPoint> corrected = makePoints(10);
        List<Anomaly> anomalies = Collections.emptyList();

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 10, 10, anomalies, corrected);

        assertTrue(result.totalScore() >= 95.0,
                "Perfect data should score near 100, got " + result.totalScore());
    }

    @Test
    void calculate_perfectDataSpatialScoreIs100() {
        List<Double> deviations = List.of(0.0, 0.0, 0.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 5, 5, Collections.emptyList(), corrected);

        assertEquals(100.0, result.spatialScore(), 0.1);
    }

    @Test
    void calculate_perfectCoverageScoreIs100() {
        List<Double> deviations = List.of(0.0, 0.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 100, 100, Collections.emptyList(), corrected);

        assertEquals(100.0, result.coverageScore(), 0.1);
    }

    // ── high deviation → low spatial ──

    @Test
    void calculate_highDeviationLowSpatialScore() {
        List<Double> deviations = List.of(50.0, 50.0, 50.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 5, 5, Collections.emptyList(), corrected);

        assertTrue(result.spatialScore() < 10.0,
                "Average deviation = maxDeviation should give spatial ≈ 0, got " + result.spatialScore());
    }

    @Test
    void calculate_deviationExceedingMaxGivesZeroSpatial() {
        List<Double> deviations = List.of(100.0, 100.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 5, 5, Collections.emptyList(), corrected);

        assertEquals(0.0, result.spatialScore(), 0.1);
    }

    // ── missing points → low coverage ──

    @Test
    void calculate_halfPointsLowCoverageScore() {
        List<Double> deviations = List.of(0.0, 0.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 50, 100, Collections.emptyList(), corrected);

        assertEquals(50.0, result.coverageScore(), 0.1);
    }

    @Test
    void calculate_zeroCoverageReturnsZero() {
        List<Double> deviations = List.of(0.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 0, 100, Collections.emptyList(), corrected);

        assertEquals(0.0, result.coverageScore(), 0.1);
    }

    @Test
    void calculate_zeroExpectedPointsGivesZeroCoverage() {
        List<Double> deviations = List.of(0.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 50, 0, Collections.emptyList(), corrected);

        assertEquals(0.0, result.coverageScore(), 0.1);
    }

    // ── many anomalies → low temporal ──

    @Test
    void calculate_manyAnomaliesLowTemporalScore() {
        List<Double> deviations = List.of(0.0);
        List<GpsPoint> corrected = makePoints(10); // 9 segments
        List<Anomaly> anomalies = List.of(
                makeAnomaly(9)); // 9 affected points = all segments

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 10, 10, anomalies, corrected);

        assertEquals(0.0, result.temporalScore(), 0.1);
    }

    @Test
    void calculate_noAnomaliesHighTemporalScore() {
        List<Double> deviations = List.of(0.0);
        List<GpsPoint> corrected = makePoints(10);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 10, 10, Collections.emptyList(), corrected);

        assertEquals(100.0, result.temporalScore(), 0.1);
    }

    // ── all zeros / edge cases ──

    @Test
    void calculate_emptyDeviationsGivesZeroSpatial() {
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                Collections.emptyList(), 5, 5, Collections.emptyList(), corrected);

        assertEquals(0.0, result.spatialScore(), 0.1);
    }

    @Test
    void calculate_nullDeviationsGivesZeroSpatial() {
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                null, 5, 5, Collections.emptyList(), corrected);

        assertEquals(0.0, result.spatialScore(), 0.1);
    }

    @Test
    void calculate_nullCorrectedPointsGivesZeroTemporal() {
        List<Double> deviations = List.of(0.0);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 10, 10, Collections.emptyList(), null);

        assertEquals(0.0, result.temporalScore(), 0.1);
    }

    @Test
    void calculate_singleCorrectedPointGivesZeroTemporal() {
        List<Double> deviations = List.of(0.0);
        List<GpsPoint> corrected = makePoints(1);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 10, 10, Collections.emptyList(), corrected);

        assertEquals(0.0, result.temporalScore(), 0.1);
    }

    // ── score clamping ──

    @Test
    void calculate_totalScoreClampedTo100() {
        List<Double> deviations = List.of(0.0, 0.0);
        List<GpsPoint> corrected = makePoints(10);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 200, 100, Collections.emptyList(), corrected);

        assertTrue(result.totalScore() <= 100.0, "Total should be clamped to 100");
    }

    @Test
    void calculate_totalScoreClampedToZero() {
        List<Double> deviations = List.of(200.0, 200.0);
        List<GpsPoint> corrected = makePoints(1);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 0, 100, Collections.emptyList(), corrected);

        assertTrue(result.totalScore() >= 0.0, "Total should not be negative");
    }

    // ── weights ──

    @Test
    void calculate_weightsApplyCorrectly() {
        // spatial=100%, coverage=0%, temporal=100%  →  total= 0.4*100 + 0.3*0 + 0.3*100 = 70
        List<Double> deviations = List.of(0.0, 0.0);
        List<GpsPoint> corrected = makePoints(5);

        TrustScoreCalculator.ScoreResult result = calculator.calculate(
                deviations, 0, 100, Collections.emptyList(), corrected);

        assertEquals(70.0, result.totalScore(), 1.0);
    }

    private List<GpsPoint> makePoints(int count) {
        List<GpsPoint> points = new ArrayList<>();
        Instant base = Instant.now();
        for (int i = 0; i < count; i++) {
            points.add(GpsPoint.builder()
                    .tripId(tripId)
                    .latitude(24.7 + i * 0.0001)
                    .longitude(46.7)
                    .timestamp(base.plusSeconds(i))
                    .pointType(PointType.CORRECTED)
                    .sequenceIndex(i)
                    .build());
        }
        return points;
    }

    private Anomaly makeAnomaly(int affectedPoints) {
        return Anomaly.builder()
                .tripId(tripId)
                .anomalyType(AnomalyType.GPS_JITTER)
                .startIndex(0)
                .endIndex(affectedPoints)
                .affectedPoints(affectedPoints)
                .description("test anomaly")
                .build();
    }
}
