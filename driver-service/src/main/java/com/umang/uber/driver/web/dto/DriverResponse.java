package com.umang.uber.driver.web.dto;

import com.umang.uber.common.enums.DriverStatus;
import com.umang.uber.driver.entity.Driver;

public record DriverResponse(
        Long id,
        String name,
        String email,
        String vehicle,
        DriverStatus status,
        Double currentLat,
        Double currentLng) {

    public static DriverResponse from(Driver d) {
        return new DriverResponse(d.getId(), d.getName(), d.getEmail(), d.getVehicle(),
                d.getStatus(), d.getCurrentLat(), d.getCurrentLng());
    }
}
