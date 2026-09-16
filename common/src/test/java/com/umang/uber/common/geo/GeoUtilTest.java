package com.umang.uber.common.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeoUtilTest {

    @Test
    void haversine_isZeroForSamePoint() {
        assertThat(GeoUtil.haversineKm(12.97, 77.59, 12.97, 77.59)).isZero();
    }

    @Test
    void haversine_matchesKnownDistance() {
        // Bangalore MG Road (12.9750, 77.6060) to Bangalore Airport (13.1986, 77.7066)
        // is ~30 km great-circle. Allow a small tolerance for the earth-radius constant.
        double km = GeoUtil.haversineKm(12.9750, 77.6060, 13.1986, 77.7066);
        assertThat(km).isBetween(25.0, 30.0);
    }

    @Test
    void haversine_isSymmetric() {
        double ab = GeoUtil.haversineKm(12.97, 77.59, 13.10, 77.70);
        double ba = GeoUtil.haversineKm(13.10, 77.70, 12.97, 77.59);
        assertThat(ab).isEqualTo(ba);
    }

    @Test
    void geohash_hasRequestedLength() {
        assertThat(GeoUtil.geohash(12.97, 77.59, 7)).hasSize(7);
    }

    @Test
    void geohash_nearbyPointsSharePrefix() {
        // Two points ~100m apart must fall in the same coarse geo-cell (shared 6-char prefix),
        // which is exactly what surge bucketing per cell relies on.
        String a = GeoUtil.geohash(12.9716, 77.5946, 6);
        String b = GeoUtil.geohash(12.9720, 77.5949, 6);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void geohash_knownValueForBangalore() {
        // tdr1 is the well-known geohash prefix for the Bangalore region.
        assertThat(GeoUtil.geohash(12.9716, 77.5946, 4)).isEqualTo("tdr1");
    }
}
