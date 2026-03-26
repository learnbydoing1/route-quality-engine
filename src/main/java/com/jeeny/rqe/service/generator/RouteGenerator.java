package com.jeeny.rqe.service.generator;

import com.jeeny.rqe.model.GpsPoint;
import com.jeeny.rqe.model.PointType;
import com.jeeny.rqe.util.GeoUtils;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class RouteGenerator {

    private static final int NUM_WAYPOINTS = 8;
    private static final double MAX_LATERAL_OFFSET_METERS = 200.0;

    /**
     * Generates a realistic planned route between start and end coordinates.
     * Creates intermediate waypoints with slight lateral offsets to simulate
     * actual road geometry rather than a straight line.
     */
    public List<GpsPoint> generatePlannedRoute(UUID tripId, double startLat, double startLng,
                                                double endLat, double endLng) {
        return generatePlannedRoute(tripId, startLat, startLng, endLat, endLng, new Random());
    }

    public List<GpsPoint> generatePlannedRoute(UUID tripId, double startLat, double startLng,
                                                double endLat, double endLng, Random random) {
        List<double[]> waypoints = new ArrayList<>();
        waypoints.add(new double[]{startLat, startLng});

        for (int i = 1; i <= NUM_WAYPOINTS; i++) {
            double fraction = (double) i / (NUM_WAYPOINTS + 1);
            double lat = startLat + fraction * (endLat - startLat);
            double lng = startLng + fraction * (endLng - startLng);

            double perpAngle = Math.atan2(endLng - startLng, endLat - startLat) + Math.PI / 2;
            double offsetMeters = (random.nextGaussian() * 0.3) * MAX_LATERAL_OFFSET_METERS;
            lat += GeoUtils.metersToLatDegrees(offsetMeters * Math.cos(perpAngle));
            lng += GeoUtils.metersToLngDegrees(offsetMeters * Math.sin(perpAngle), lat);

            waypoints.add(new double[]{lat, lng});
        }

        waypoints.add(new double[]{endLat, endLng});

        List<GpsPoint> plannedPoints = new ArrayList<>();
        int seq = 0;
        Instant baseTime = Instant.now();

        for (int i = 0; i < waypoints.size() - 1; i++) {
            double[] from = waypoints.get(i);
            double[] to = waypoints.get(i + 1);
            double segDistKm = GeoUtils.haversineKm(from[0], from[1], to[0], to[1]);
            int pointsInSegment = Math.max(2, (int) (segDistKm * 100));

            for (int j = 0; j < pointsInSegment; j++) {
                double t = (double) j / pointsInSegment;
                double lat = from[0] + t * (to[0] - from[0]);
                double lng = from[1] + t * (to[1] - from[1]);

                plannedPoints.add(GpsPoint.builder()
                        .tripId(tripId)
                        .latitude(lat)
                        .longitude(lng)
                        .timestamp(baseTime.plusSeconds(seq))
                        .pointType(PointType.PLANNED)
                        .sequenceIndex(seq++)
                        .build());
            }
        }

        double[] last = waypoints.get(waypoints.size() - 1);
        plannedPoints.add(GpsPoint.builder()
                .tripId(tripId)
                .latitude(last[0])
                .longitude(last[1])
                .timestamp(baseTime.plusSeconds(seq))
                .pointType(PointType.PLANNED)
                .sequenceIndex(seq)
                .build());

        return plannedPoints;
    }
}
