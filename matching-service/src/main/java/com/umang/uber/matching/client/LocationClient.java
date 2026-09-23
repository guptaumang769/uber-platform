package com.umang.uber.matching.client;

import com.umang.uber.common.api.ApiResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Load-balanced client to location-service — resolves the nearest candidate drivers. */
@FeignClient(name = "location-service")
public interface LocationClient {

    @GetMapping("/api/v1/locations/nearby")
    ApiResponse<List<NearbyDriver>> nearby(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam("radiusKm") double radiusKm);

    /** Mirrors location-service's NearbyDriverDto. */
    record NearbyDriver(Long driverId, double distanceKm) {
    }
}
