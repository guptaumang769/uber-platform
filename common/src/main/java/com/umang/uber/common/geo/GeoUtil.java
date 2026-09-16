package com.umang.uber.common.geo;

/**
 * Pure-Java geospatial helpers, kept dependency-free so they are trivially unit-testable and
 * reusable across services (pricing surge cells, matching distance sorts, tests).
 *
 * <ul>
 *   <li><b>haversineKm</b> — great-circle distance between two lat/lng points, used for fare
 *       estimation and for reasoning about driver proximity.</li>
 *   <li><b>geohash</b> — encodes a lat/lng into a base-32 geohash string. A geohash prefix is a
 *       rectangular cell on Earth; longer prefixes = smaller cells. We use it as the surge
 *       "geo-cell" key (demand/supply are bucketed per cell) — the same idea Redis GEO uses
 *       internally to make radius queries O(log n).</li>
 * </ul>
 */
public final class GeoUtil {

    private static final double EARTH_RADIUS_KM = 6371.0088;
    private static final char[] BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz".toCharArray();

    private GeoUtil() {
    }

    /** Great-circle distance in kilometres between two WGS84 points. */
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
     * Encode a lat/lng to a geohash of {@code precision} characters. Classic interleaved-bits
     * algorithm: alternately bisect the longitude and latitude ranges, packing one bit per step
     * into groups of five to index the base-32 alphabet.
     */
    public static String geohash(double lat, double lng, int precision) {
        double[] latRange = {-90.0, 90.0};
        double[] lngRange = {-180.0, 180.0};
        StringBuilder hash = new StringBuilder(precision);

        boolean even = true; // even bit -> bisect longitude, odd bit -> bisect latitude
        int bit = 0;
        int ch = 0;

        while (hash.length() < precision) {
            double mid;
            if (even) {
                mid = (lngRange[0] + lngRange[1]) / 2;
                if (lng >= mid) {
                    ch |= (1 << (4 - bit));
                    lngRange[0] = mid;
                } else {
                    lngRange[1] = mid;
                }
            } else {
                mid = (latRange[0] + latRange[1]) / 2;
                if (lat >= mid) {
                    ch |= (1 << (4 - bit));
                    latRange[0] = mid;
                } else {
                    latRange[1] = mid;
                }
            }
            even = !even;
            if (bit < 4) {
                bit++;
            } else {
                hash.append(BASE32[ch]);
                bit = 0;
                ch = 0;
            }
        }
        return hash.toString();
    }
}
