package com.umang.uber.common.event;

/**
 * A single driver GPS ping. Emitted by driver-service on every location update, consumed by
 * location-service (GEOADD to the Redis geo set) and notification-service (live map stream).
 * {@code epoch} lets consumers age out stale positions.
 */
public record DriverLocationEvent(
        Long driverId,
        double lat,
        double lng,
        long epoch) {
}
