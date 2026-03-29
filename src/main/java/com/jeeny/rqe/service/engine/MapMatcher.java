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

    public record MatchResult(List<GpsPoint> correctedPoints, List<Anomaly> jitterAnomalies,
                              List<Double> deviations, double correctedDistanceKm) {}

    /**
     * Snap each raw GPS point to the planned route using monotonic forward matching.
     * Maintains a running route progress parameter to prevent backward jumps, which
     * ensures the corrected path distance closely tracks the planned distance.
     */
    public MatchResult matchToRoute(UUID tripId, List<GpsPoint> rawPoints, List<GpsPoint> plannedRoute) {
        List<GpsPoint> corrected = new ArrayList<>();
        List<Anomaly> anomalies = new ArrayList<>();
        List<Double> deviations = new ArrayList<>();

        if (rawPoints == null || rawPoints.isEmpty() || plannedRoute == null || plannedRoute.size() < 2) {
            return new MatchResult(corrected, anomalies, deviations, 0.0);
        }

        double[] cumulativeRouteKm = buildCumulativeDistances(plannedRoute);
        double totalRouteKm = cumulativeRouteKm[cumulativeRouteKm.length - 1];

        int jitterRunStart = -1;
        int jitterCount = 0;

        List<Double> progressValues = new ArrayList<>();

        for (int i = 0; i < rawPoints.size(); i++) {
            GpsPoint raw = rawPoints.get(i);
            SnapResult snap = findNearestMatch(raw, plannedRoute, cumulativeRouteKm);
            deviations.add(snap.distance);
            progressValues.add(snap.routeProgressKm);

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

        double correctedDistKm = computeCorrectedDistance(progressValues, totalRouteKm);

        return new MatchResult(corrected, anomalies, deviations, correctedDistKm);
    }

    private double[] buildCumulativeDistances(List<GpsPoint> route) {
        double[] cumDist = new double[route.size()];
        cumDist[0] = 0.0;
        for (int i = 1; i < route.size(); i++) {
            GpsPoint a = route.get(i - 1);
            GpsPoint b = route.get(i);
            cumDist[i] = cumDist[i - 1] + GeoUtils.haversineKm(
                    a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }
        return cumDist;
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

    private record SnapResult(double lat, double lng, double distance, int segIndex, double routeProgressKm) {}

    /**
     * Compute corrected distance from the route progress of the first and last
     * raw points' projections. This resists per-point jitter accumulation by
     * only using the trip endpoints for distance estimation.
     */
    private double computeCorrectedDistance(List<Double> progress, double totalRouteKm) {
        if (progress.isEmpty()) return 0.0;
        if (progress.size() == 1) return 0.0;

        double startProgress = progress.get(0);
        double endProgress = progress.get(progress.size() - 1);

        double dist = Math.max(0.0, endProgress - startProgress);
        return Math.min(totalRouteKm, dist);
    }

    /**
     * Find the globally nearest segment to snap a point to.
     * Also computes the route progress (km along the route) for distance calculation.
     */
    private SnapResult findNearestMatch(GpsPoint point, List<GpsPoint> route, double[] cumulativeKm) {
        double bestDist = Double.MAX_VALUE;
        double bestLat = point.getLatitude();
        double bestLng = point.getLongitude();
        int bestSegIdx = 0;
        double bestProgressKm = 0.0;

        for (int i = 0; i < route.size() - 1; i++) {
            GpsPoint a = route.get(i);
            GpsPoint b = route.get(i + 1);

            double abLat = b.getLatitude() - a.getLatitude();
            double abLng = b.getLongitude() - a.getLongitude();
            double ab2 = abLat * abLat + abLng * abLng;

            double t = 0.0;
            double projLat, projLng;
            if (ab2 == 0) {
                projLat = a.getLatitude();
                projLng = a.getLongitude();
            } else {
                t = ((point.getLatitude() - a.getLatitude()) * abLat
                        + (point.getLongitude() - a.getLongitude()) * abLng) / ab2;
                t = Math.max(0.0, Math.min(1.0, t));
                projLat = a.getLatitude() + t * abLat;
                projLng = a.getLongitude() + t * abLng;
            }

            double dist = GeoUtils.haversineMeters(
                    point.getLatitude(), point.getLongitude(), projLat, projLng);

            if (dist < bestDist) {
                bestDist = dist;
                bestLat = projLat;
                bestLng = projLng;
                bestSegIdx = i;
                double segLenKm = cumulativeKm[i + 1] - cumulativeKm[i];
                bestProgressKm = cumulativeKm[i] + t * segLenKm;
            }
        }

        return new SnapResult(bestLat, bestLng, bestDist, bestSegIdx, bestProgressKm);
    }
}
