package com.umang.uber.rider.web.dto;

import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.rider.entity.RideRequest;

public record RideResponse(
        Long id,
        Long riderId,
        double pickupLat,
        double pickupLng,
        double dropLat,
        double dropLng,
        TripStatus status) {

    public static RideResponse from(RideRequest r) {
        return new RideResponse(r.getId(), r.getRiderId(), r.getPickupLat(), r.getPickupLng(),
                r.getDropLat(), r.getDropLng(), r.getStatus());
    }
}
