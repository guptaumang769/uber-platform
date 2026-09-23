package com.umang.uber.location.repository;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Wraps the Redis GEO set {@code drivers:geo}.
 *
 * <p>WHY Redis GEO instead of a {@code SELECT ... WHERE lat BETWEEN ... AND lng BETWEEN ...} scan:
 * Redis stores each member's lat/lng as a 52-bit geohash score in a sorted set, so a radius query
 * (GEOSEARCH) is an O(log n + m) range scan over that ordered index rather than an O(n) table
 * scan. That is exactly the "find the nearest available drivers to this pickup" query on the hot
 * matching path, where we need millisecond latency over a constantly-moving fleet.
 */
@Repository
@RequiredArgsConstructor
public class DriverGeoRepository {

    private static final String GEO_KEY = "drivers:geo";
    private static final String PRESENCE_PREFIX = "driver:seen:";

    private final StringRedisTemplate redis;

    @Value("${uber.location.driver-ttl-seconds:60}")
    private long driverTtlSeconds;

    /** GEOADD the driver's position and refresh a short TTL presence marker. */
    public void upsert(Long driverId, double lat, double lng) {
        // Redis GEO takes (longitude, latitude) order.
        redis.opsForGeo().add(GEO_KEY, new Point(lng, lat), String.valueOf(driverId));
        // Presence marker with a TTL so a driver who stops pinging ages out of "recently seen".
        redis.opsForValue().set(PRESENCE_PREFIX + driverId, "1",
                java.time.Duration.ofSeconds(driverTtlSeconds));
    }

    /** Nearby driverIds with distance (km), nearest first. Skips drivers whose presence expired. */
    public List<NearbyDriver> nearby(double lat, double lng, double radiusKm) {
        Circle within = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs().includeDistance().sortAscending();

        GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                redis.opsForGeo().radius(GEO_KEY, within, args);

        List<NearbyDriver> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : results) {
            String driverId = r.getContent().getName();
            // Stale positions (no live presence marker) are excluded so we never route to a
            // driver who has gone dark.
            if (Boolean.TRUE.equals(redis.hasKey(PRESENCE_PREFIX + driverId))) {
                out.add(new NearbyDriver(Long.valueOf(driverId), r.getDistance().getValue()));
            }
        }
        return out;
    }

    /** A candidate driver returned by a radius query. */
    public record NearbyDriver(Long driverId, double distanceKm) {
    }
}
