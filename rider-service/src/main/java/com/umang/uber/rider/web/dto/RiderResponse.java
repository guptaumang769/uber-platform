package com.umang.uber.rider.web.dto;

import com.umang.uber.rider.entity.Rider;

public record RiderResponse(Long id, String name, String email, String phone) {

    public static RiderResponse from(Rider rider) {
        return new RiderResponse(rider.getId(), rider.getName(), rider.getEmail(), rider.getPhone());
    }
}
