package com.jeeny.rqe.service.generator;

import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import com.jeeny.rqe.util.GeoUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class TelemetryGenerator {

    private static final double AVERAGE_SPEED_KMH = 40.0;
    private static final double SPEED_VARIATION = 0.2;

    /**
     * Generates ideal (noise-free) telemetry by sampling the planned route
     * at approximately 1-second intervals based on realistic driving speed.
     */
    public List<GpsPoint> generateIdealTelemetry(UUID tripId, List<GpsPoint> plannedRoute) {
        if (plannedRoute == null || plannedRoute.size() < 2) {
            return new ArrayList<>();
        }

        double totalDistanceKm = GeoUtils.totalDistanceKm(plannedRoute);
        double totalTimeSeconds = (totalDistanceKm / AVERAGE_SPEED_KMH) * 3600.0;

        List<GpsPoint> telemetry = new ArrayList<>();
        Instant startTime = plannedRoute.get(0).getTimestamp();
        int seq = 0;

        double[] segDistances = new double[plannedRoute.size() - 1];
        double cumulative = 0.0;
        double[] cumulativeDistances = new double[plannedRoute.size()];
        cumulativeDistances[0] = 0.0;

        for (int i = 0; i < plannedRoute.size() - 1; i++) {
            GpsPoint a = plannedRoute.get(i);
            GpsPoint b = plannedRoute.get(i + 1);
            segDistances[i] = GeoUtils.haversineKm(a.getLatitude(), a.getLongitude(),
                    b.getLatitude(), b.getLongitude());
            cumulative += segDistances[i];
            cumulativeDistances[i + 1] = cumulative;
        }

        if (totalDistanceKm < 0.001) {
            telemetry.add(GpsPoint.builder()
                    .tripId(tripId)
                    .latitude(plannedRoute.get(0).getLatitude())
                    .longitude(plannedRoute.get(0).getLongitude())
                    .timestamp(startTime)
                    .pointType(PointType.RAW)
                    .sequenceIndex(0)
                    .build());
            return telemetry;
        }

        int numSamples = Math.max(2, (int) Math.ceil(totalTimeSeconds));

        for (int s = 0; s <= numSamples; s++) {
            double fraction = (double) s / numSamples;
            double targetDistKm = fraction * totalDistanceKm;

            int segIndex = 0;
            for (int i = 1; i < cumulativeDistances.length; i++) {
                if (cumulativeDistances[i] >= targetDistKm) {
                    segIndex = i - 1;
                    break;
                }
                segIndex = i - 1;
            }

            double segStart = cumulativeDistances[segIndex];
            double segLen = segDistances[segIndex];
            double t = segLen > 0 ? (targetDistKm - segStart) / segLen : 0.0;
            t = Math.max(0.0, Math.min(1.0, t));

            GpsPoint a = plannedRoute.get(segIndex);
            GpsPoint b = plannedRoute.get(segIndex + 1);

            double lat = a.getLatitude() + t * (b.getLatitude() - a.getLatitude());
            double lng = a.getLongitude() + t * (b.getLongitude() - a.getLongitude());

            telemetry.add(GpsPoint.builder()
                    .tripId(tripId)
                    .latitude(lat)
                    .longitude(lng)
                    .timestamp(startTime.plusSeconds(s))
                    .pointType(PointType.RAW)
                    .sequenceIndex(seq++)
                    .build());
        }

        return telemetry;
    }
}
