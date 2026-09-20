package com.umang.uber.rider.web.dto;

import jakarta.validation.constraints.NotNull;

public record RideRequestDto(
        @NotNull Long riderId,
        double pickupLat,
        double pickupLng,
        double dropLat,
        double dropLng) {
}
