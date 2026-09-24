package com.umang.uber.pricing.service;

import com.umang.uber.common.geo.GeoUtil;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Tracks per-geo-cell demand and supply in Redis and derives the surge multiplier.
 *
 * <p>A "cell" is a geohash prefix (see {@link GeoUtil#geohash}); nearby pickups fall in the same
 * cell, so surge is localized to a neighbourhood rather than global. Counters carry a TTL so they
 * decay — they represent RECENT activity (a sliding window), not all-time totals. INCR is atomic,
 * so concurrent demand bumps are safe without locking.
 */
@Component
@RequiredArgsConstructor
public class SurgeTracker {

    private static final int CELL_PRECISION = 6; // ~1.2km x 0.6km cells
    private static final String DEMAND_PREFIX = "surge:demand:";
    private static final String SUPPLY_PREFIX = "surge:supply:";

    private final StringRedisTemplate redis;

    @Value("${uber.pricing.max-surge:3.0}")
    private double maxSurge;

    @Value("${uber.pricing.demand-window-seconds:300}")
    private long windowSeconds;

    /** Record a ride request in this cell (bumps demand for the sliding window). */
    public void recordDemand(double lat, double lng) {
        bump(DEMAND_PREFIX + cell(lat, lng));
    }

    /** Record an available driver in this cell (bumps supply for the sliding window). */
    public void recordSupply(double lat, double lng) {
        bump(SUPPLY_PREFIX + cell(lat, lng));
    }

    /** Current surge multiplier for the cell containing this point. */
    public double surgeFor(double lat, double lng) {
        String cell = cell(lat, lng);
        long demand = readCount(DEMAND_PREFIX + cell);
        long supply = readCount(SUPPLY_PREFIX + cell);
        return SurgeCalculator.multiplier(demand, supply, maxSurge);
    }

    private String cell(double lat, double lng) {
        return GeoUtil.geohash(lat, lng, CELL_PRECISION);
    }

    private void bump(String key) {
        Long value = redis.opsForValue().increment(key);
        // (Re)set the TTL on every bump so the counter reflects a rolling recent window.
        if (value != null) {
            redis.expire(key, Duration.ofSeconds(windowSeconds));
        }
    }

    private long readCount(String key) {
        String raw = redis.opsForValue().get(key);
        return raw == null ? 0L : Long.parseLong(raw);
    }
}
