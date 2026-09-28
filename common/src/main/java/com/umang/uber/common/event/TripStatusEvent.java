package com.umang.uber.common.event;

import com.umang.uber.common.enums.TripStatus;
import java.math.BigDecimal;

/**
 * Emitted by trip-service on every trip state transition. Consumed by notification-service
 * (pushes to the rider's live SSE stream), driver-service (frees the driver on terminal states),
 * and, on COMPLETED, drives the payment saga with the actual fare.
 *
 * <p>{@code driverId} allows driver-service to flip the driver back to AVAILABLE on COMPLETED or
 * CANCELLED without a separate lookup. {@code fare} carries the trip fare so payment-service never
 * captures ZERO — the fare is set once when the trip is created and carried here unchanged.
 */
public record TripStatusEvent(
        Long tripId,
        TripStatus status,
        Long driverId,
        BigDecimal fare) {
}
