package com.jeeny.rqe.service.generator;

import com.jeeny.rqe.model.ChaosConfig;
import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.util.GeoUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class ChaosInjector {

    /**
     * Apply chaos effects to ideal telemetry points, returning noisy RAW points.
     */
    public List<GpsPoint> injectChaos(List<GpsPoint> idealTelemetry, ChaosConfig config) {
        return injectChaos(idealTelemetry, config, new Random());
    }

    public List<GpsPoint> injectChaos(List<GpsPoint> idealTelemetry, ChaosConfig config, Random random) {
        if (idealTelemetry == null || idealTelemetry.isEmpty()) {
            return new ArrayList<>();
        }

        List<GpsPoint> result = new ArrayList<>();
        for (GpsPoint point : idealTelemetry) {
            result.add(point.toBuilder().build());
        }

        result = applyJitter(result, config.getJitterMeters(), random);
        result = applyTunnel(result, config.getTunnelFraction(), random);
        result = applyDrift(result, config.getDriftMeters(), config.getDriftProbability(), random);

        reindex(result);
        return result;
    }

    /**
     * Add Gaussian noise to each point's lat/lng.
     */
    List<GpsPoint> applyJitter(List<GpsPoint> points, double jitterMeters, Random random) {
        if (jitterMeters <= 0) return points;

        for (GpsPoint p : points) {
            double noiseMetersLat = random.nextGaussian() * jitterMeters;
            double noiseMetersLng = random.nextGaussian() * jitterMeters;
            p.setLatitude(p.getLatitude() + GeoUtils.metersToLatDegrees(noiseMetersLat));
            p.setLongitude(p.getLongitude() + GeoUtils.metersToLngDegrees(noiseMetersLng, p.getLatitude()));
        }
        return points;
    }

    /**
     * Delete contiguous chunks of points to simulate tunnel/signal loss.
     */
    List<GpsPoint> applyTunnel(List<GpsPoint> points, double tunnelFraction, Random random) {
        if (tunnelFraction <= 0 || points.size() < 3) return points;

        int pointsToRemove = (int) (points.size() * tunnelFraction);
        if (pointsToRemove < 1) return points;

        int maxStart = points.size() - pointsToRemove - 1;
        if (maxStart < 1) maxStart = 1;
        int chunkStart = 1 + random.nextInt(Math.max(1, maxStart));
        int chunkEnd = Math.min(chunkStart + pointsToRemove, points.size() - 1);

        List<GpsPoint> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (i < chunkStart || i >= chunkEnd) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    /**
     * Randomly teleport points by large offsets to simulate GPS drift/lag.
     */
    List<GpsPoint> applyDrift(List<GpsPoint> points, double driftMeters, double driftProbability, Random random) {
        if (driftMeters <= 0 || driftProbability <= 0) return points;

        for (int i = 1; i < points.size() - 1; i++) {
            if (random.nextDouble() < driftProbability) {
                GpsPoint p = points.get(i);
                double angle = random.nextDouble() * 2 * Math.PI;
                double offsetLat = GeoUtils.metersToLatDegrees(driftMeters * Math.cos(angle));
                double offsetLng = GeoUtils.metersToLngDegrees(driftMeters * Math.sin(angle), p.getLatitude());
                p.setLatitude(p.getLatitude() + offsetLat);
                p.setLongitude(p.getLongitude() + offsetLng);
            }
        }
        return points;
    }

    private void reindex(List<GpsPoint> points) {
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setSequenceIndex(i);
        }
    }
}
