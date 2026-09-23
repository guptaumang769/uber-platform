package com.umang.uber.matching.service;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Atomic per-driver assignment lock backed by Redis SETNX (SET ... NX).
 *
 * <p>THE RACE: matching-service runs multiple partitions/instances, so two RideRequestedEvents can
 * be processed concurrently. If both see the same nearest AVAILABLE driver in the GEOSEARCH result
 * and both try to assign, we would double-assign one driver to two riders. Redis {@code SET key
 * value NX} is a single atomic operation that succeeds for EXACTLY ONE caller — the winner takes
 * the driver; the loser gets {@code false} and moves on to the next-nearest candidate. This is the
 * classic distributed-lock primitive; doing the same check in application code (read-then-write)
 * would be racy because the read and write aren't atomic across instances.
 *
 * <p>The lock carries a TTL so that if a matcher crashes after locking but before publishing the
 * match, the driver is not stranded forever — the lock self-expires and the driver becomes
 * assignable again.
 */
@Component
@RequiredArgsConstructor
public class DriverLock {

    private static final String LOCK_PREFIX = "match:driver:";

    private final StringRedisTemplate redis;

    @Value("${uber.matching.lock-ttl-ms:15000}")
    private long lockTtlMs;

    /** Try to claim a driver. Returns true iff this caller won the atomic SETNX. */
    public boolean tryLock(Long driverId, Long rideRequestId) {
        Boolean acquired = redis.opsForValue().setIfAbsent(
                LOCK_PREFIX + driverId,
                String.valueOf(rideRequestId),
                Duration.ofMillis(lockTtlMs));
        return Boolean.TRUE.equals(acquired);
    }

    /** Release a lock (e.g. if publishing the match fails and we want the driver reassignable). */
    public void release(Long driverId) {
        redis.delete(LOCK_PREFIX + driverId);
    }
}
