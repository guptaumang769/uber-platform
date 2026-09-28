package com.umang.uber.common.event;

/**
 * Emitted by rider-service (via the outbox) when a rider requests a ride. Consumed by
 * matching-service, which finds the nearest available driver. Carries pickup + drop coordinates
 * so downstream services need no callback to rider-service on the hot matching path.
 */
public record RideRequestedEvent(
        Long rideRequestId,
        Long riderId,
        double pickupLat,
        double pickupLng,
        double dropLat,
        double dropLng,
        long requestedAtEpoch) {
}
