package com.jeeny.rqe.service.engine;

import com.jeeny.rqe.model.Anomaly;
import com.jeeny.rqe.model.AnomalyType;
import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import com.jeeny.rqe.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MapMatcher {

    private final double maxDeviationMeters;

    public MapMatcher(@Value("${rqe.scoring.max-deviation-meters:50.0}") double maxDeviationMeters) {
        this.maxDeviationMeters = maxDeviationMeters;
    }

    /**
     * Result of map matching: corrected points and detected jitter anomalies.
     */
    public record MatchResult(List<GpsPoint> correctedPoints, List<Anomaly> jitterAnomalies,
                              List<Double> deviations) {}

    /**
     * Snap each raw GPS point to the nearest point on the planned route.
     * Points that deviate beyond the threshold are flagged as GPS_JITTER anomalies
     * but still snapped to produce corrected output.
     */
    public MatchResult matchToRoute(UUID tripId, List<GpsPoint> rawPoints, List<GpsPoint> plannedRoute) {
        List<GpsPoint> corrected = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();
        List<Double> deviations = new ArrayList<>();

        if (rawPoints == null || rawPoints.isEmpty() || plannedRoute == null || plannedRoute.size() < 2) {
            return new MatchResult(corrected, anomalies, deviations);
        }

        int jitterRunStart = -1;
        int jitterCount = 0;

        for (int i = 0; i < rawPoints.size(); i++) {
            GpsPoint raw = rawPoints.get(i);
            SnapResult snap = findNearestSegmentPoint(raw, plannedRoute);
            deviations.add(snap.distance);

            GpsPoint correctedPoint = GpsPoint.builder()
                    .tripId(tripId)
                    .latitude(snap.lat)
                    .longitude(snap.lng)
                    .timestamp(raw.getTimestamp())
                    .pointType(PointType.CORRECTED)
                    .sequenceIndex(i)
                    .build();
            corrected.add(correctedPoint);

            boolean isJitter = snap.distance > maxDeviationMeters;
            if (isJitter) {
                if (jitterRunStart == -1) {
                    jitterRunStart = i;
                    jitterCount = 1;
                } else {
                    jitterCount++;
                }
            } else {
                if (jitterRunStart != -1) {
                    anomalies.add(buildJitterAnomaly(tripId, jitterRunStart, i - 1, jitterCount));
                    jitterRunStart = -1;
                    jitterCount = 0;
                }
            }
        }

        if (jitterRunStart != -1) {
            anomalies.add(buildJitterAnomaly(tripId, jitterRunStart, rawPoints.size() - 1, jitterCount));
        }

        return new MatchResult(corrected, anomalies, deviations);
    }

    private Anomaly buildJitterAnomaly(UUID tripId, int start, int end, int count) {
        return Anomaly.builder()
                .tripId(tripId)
                .anomalyType(AnomalyType.GPS_JITTER)
                .startIndex(start)
                .endIndex(end)
                .affectedPoints(count)
                .description(String.format("GPS jitter detected: %d points deviated >%.0fm from planned route (indices %d-%d)",
                        count, maxDeviationMeters, start, end))
                .build();
    }

    private record SnapResult(double lat, double lng, double distance) {}

    private SnapResult findNearestSegmentPoint(GpsPoint point, List<GpsPoint> route) {
        double bestDist = Double.MAX_VALUE;
        double bestLat = point.getLatitude();
        double bestLng = point.getLongitude();

        for (int i = 0; i < route.size() - 1; i++) {
            GpsPoint a = route.get(i);
            GpsPoint b = route.get(i + 1);

            double[] projected = GeoUtils.projectOntoSegment(
                    point.getLatitude(), point.getLongitude(),
                    a.getLatitude(), a.getLongitude(),
                    b.getLatitude(), b.getLongitude());

            double dist = GeoUtils.haversineMeters(
                    point.getLatitude(), point.getLongitude(),
                    projected[0], projected[1]);

            if (dist < bestDist) {
                bestDist = dist;
                bestLat = projected[0];
                bestLng = projected[1];
            }
        }

        return new SnapResult(bestLat, bestLng, bestDist);
    }
}
