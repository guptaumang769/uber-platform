package com.umang.uber.location.web.dto;

/** Serialized nearby-driver result. matching-service deserializes this via its Feign client. */
public record NearbyDriverDto(Long driverId, double distanceKm) {
}
