package com.jeeny.rqe.util;

import com.jeeny.rqe.model.GpsPoint;

import java.util.List;

public final class GeoUtils {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoUtils() {}

    /**
     * Haversine distance between two points in kilometers.
     */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    /**
     * Haversine distance in meters.
     */
    public static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        return haversineKm(lat1, lng1, lat2, lng2) * 1000.0;
    }

    /**
     * Initial bearing from point 1 to point 2 in degrees (0-360).
     */
    public static double bearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    /**
     * Perpendicular distance from a point to a line segment defined by two endpoints.
     * Returns distance in meters.
     */
    public static double pointToSegmentDistanceMeters(
            double pLat, double pLng,
            double aLat, double aLng,
            double bLat, double bLng) {

        double apLat = pLat - aLat;
        double apLng = pLng - aLng;
        double abLat = bLat - aLat;
        double abLng = bLng - aLng;

        double ab2 = abLat * abLat + abLng * abLng;
        if (ab2 == 0) {
            return haversineMeters(pLat, pLng, aLat, aLng);
        }

        double t = (apLat * abLat + apLng * abLng) / ab2;
        t = Math.max(0.0, Math.min(1.0, t));

        double closestLat = aLat + t * abLat;
        double closestLng = aLng + t * abLng;

        return haversineMeters(pLat, pLng, closestLat, closestLng);
    }

    /**
     * Project a point onto a line segment, returning the closest point on the segment.
     */
    public static double[] projectOntoSegment(
            double pLat, double pLng,
            double aLat, double aLng,
            double bLat, double bLng) {

        double abLat = bLat - aLat;
        double abLng = bLng - aLng;

        double ab2 = abLat * abLat + abLng * abLng;
        if (ab2 == 0) {
            return new double[]{aLat, aLng};
        }

        double t = ((pLat - aLat) * abLat + (pLng - aLng) * abLng) / ab2;
        t = Math.max(0.0, Math.min(1.0, t));

        return new double[]{aLat + t * abLat, aLng + t * abLng};
    }

    /**
     * Total distance of a polyline defined by ordered GPS points, in km.
     */
    public static double totalDistanceKm(List<GpsPoint> points) {
        if (points == null || points.size() < 2) return 0.0;
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            GpsPoint a = points.get(i - 1);
            GpsPoint b = points.get(i);
            total += haversineKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        }
        return total;
    }

    /**
     * Convert meters offset to approximate latitude degrees.
     */
    public static double metersToLatDegrees(double meters) {
        return meters / 111_320.0;
    }

    /**
     * Convert meters offset to approximate longitude degrees at a given latitude.
     */
    public static double metersToLngDegrees(double meters, double atLatitude) {
        return meters / (111_320.0 * Math.cos(Math.toRadians(atLatitude)));
    }
}
