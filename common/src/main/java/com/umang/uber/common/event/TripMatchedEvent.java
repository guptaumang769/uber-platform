package com.umang.uber.common.event;

/**
 * Emitted by matching-service once a rider has been paired with a driver. Consumed by
 * trip-service (creates the Trip in MATCHED) and driver-service (flips the driver to ON_TRIP).
 *
 * <p>Carries pickup/drop coordinates so trip-service can request a real fare from pricing-service
 * immediately — without these coordinates the fare would be computed at (0,0) and stored as the
 * base fare regardless of actual route distance.
 */
public record TripMatchedEvent(
        Long tripId,
        Long rideRequestId,
        Long driverId,
        Long riderId,
        double pickupLat,
        double pickupLng,
        double dropLat,
        double dropLng) {
}
