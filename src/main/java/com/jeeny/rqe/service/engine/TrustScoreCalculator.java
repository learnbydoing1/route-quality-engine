package com.jeeny.rqe.service.engine;

import com.jeeny.rqe.model.Anomaly;
import com.jeeny.rqe.model.GpsPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TrustScoreCalculator {

    private final double spatialWeight;
    private final double coverageWeight;
    private final double temporalWeight;
    private final double maxDeviationMeters;

    public TrustScoreCalculator(
            @Value("${rqe.scoring.spatial-weight:0.40}") double spatialWeight,
            @Value("${rqe.scoring.coverage-weight:0.30}") double coverageWeight,
            @Value("${rqe.scoring.temporal-weight:0.30}") double temporalWeight,
            @Value("${rqe.scoring.max-deviation-meters:50.0}") double maxDeviationMeters) {
        this.spatialWeight = spatialWeight;
        this.coverageWeight = coverageWeight;
        this.temporalWeight = temporalWeight;
        this.maxDeviationMeters = maxDeviationMeters;
    }

    /**
     * Detailed trust score breakdown.
     */
    public record ScoreResult(double spatialScore, double coverageScore, double temporalScore,
                              double totalScore) {}

    /**
     * Compute a Trust Score (0-100) based on spatial fidelity, route coverage,
     * and temporal consistency.
     *
     * @param deviations    per-point deviation in meters from planned route
     * @param rawPointCount number of raw GPS points received
     * @param expectedPointCount number of points expected (from ideal telemetry)
     * @param anomalies     all detected anomalies
     * @param correctedPoints corrected GPS points
     */
    public ScoreResult calculate(
            List<Double> deviations,
            int rawPointCount,
            int expectedPointCount,
            List<Anomaly> anomalies,
            List<GpsPoint> correctedPoints) {

        double spatialScore = calculateSpatialScore(deviations);
        double coverageScore = calculateCoverageScore(rawPointCount, expectedPointCount);
        double temporalScore = calculateTemporalScore(anomalies, correctedPoints);

        double total = (spatialScore * spatialWeight + coverageScore * coverageWeight
                + temporalScore * temporalWeight) * 100.0;

        total = Math.max(0.0, Math.min(100.0, total));

        return new ScoreResult(
                Math.round(spatialScore * 1000.0) / 10.0,
                Math.round(coverageScore * 1000.0) / 10.0,
                Math.round(temporalScore * 1000.0) / 10.0,
                Math.round(total * 10.0) / 10.0);
    }

    /**
     * Spatial score: how close raw points are to the planned route.
     * Score = 1 - (avgDeviation / maxDeviation), clamped [0,1].
     */
    double calculateSpatialScore(List<Double> deviations) {
        if (deviations == null || deviations.isEmpty()) return 0.0;

        double avgDeviation = deviations.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        return Math.max(0.0, Math.min(1.0, 1.0 - (avgDeviation / maxDeviationMeters)));
    }

    /**
     * Coverage score: fraction of expected telemetry points actually received.
     */
    double calculateCoverageScore(int rawPointCount, int expectedPointCount) {
        if (expectedPointCount <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0, (double) rawPointCount / expectedPointCount));
    }

    /**
     * Temporal score: 1 - (anomalous segments / total segments).
     */
    double calculateTemporalScore(List<Anomaly> anomalies, List<GpsPoint> correctedPoints) {
        if (correctedPoints == null || correctedPoints.size() < 2) return 0.0;

        int totalSegments = correctedPoints.size() - 1;
        int anomalousSegments = 0;

        if (anomalies != null) {
            for (Anomaly a : anomalies) {
                anomalousSegments += Math.max(1, a.getAffectedPoints());
            }
        }

        anomalousSegments = Math.min(anomalousSegments, totalSegments);
        return Math.max(0.0, 1.0 - ((double) anomalousSegments / totalSegments));
    }
}
