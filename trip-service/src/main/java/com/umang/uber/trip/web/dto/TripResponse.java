package com.umang.uber.trip.web.dto;

import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.trip.entity.Trip;
import java.math.BigDecimal;

public record TripResponse(
        Long id,
        Long rideRequestId,
        Long riderId,
        Long driverId,
        TripStatus status,
        BigDecimal fare) {

    public static TripResponse from(Trip t) {
        return new TripResponse(t.getId(), t.getRideRequestId(), t.getRiderId(),
                t.getDriverId(), t.getStatus(), t.getFare());
    }
}
