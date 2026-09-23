package com.umang.uber.location.web;

import com.umang.uber.common.api.ApiResponse;
import com.umang.uber.location.repository.DriverGeoRepository;
import com.umang.uber.location.web.dto.NearbyDriverDto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

    private final DriverGeoRepository geoRepository;

    /** Nearby available drivers to a point, sorted nearest-first (GEOSEARCH under the hood). */
    @GetMapping("/nearby")
    public ApiResponse<List<NearbyDriverDto>> nearby(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5.0") double radiusKm) {
        List<NearbyDriverDto> drivers = geoRepository.nearby(lat, lng, radiusKm).stream()
                .map(d -> new NearbyDriverDto(d.driverId(), d.distanceKm()))
                .toList();
        return ApiResponse.ok(drivers);
    }
}
