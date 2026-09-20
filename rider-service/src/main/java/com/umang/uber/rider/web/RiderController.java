package com.umang.uber.rider.web;

import com.umang.uber.common.api.ApiResponse;
import com.umang.uber.rider.service.RiderService;
import com.umang.uber.rider.web.dto.RegisterRiderRequest;
import com.umang.uber.rider.web.dto.RideRequestDto;
import com.umang.uber.rider.web.dto.RideResponse;
import com.umang.uber.rider.web.dto.RiderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;

    @PostMapping("/riders")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RiderResponse> register(@Valid @RequestBody RegisterRiderRequest req) {
        return ApiResponse.ok(RiderResponse.from(
                riderService.register(req.name(), req.email(), req.phone())));
    }

    @GetMapping("/riders/{id}")
    public ApiResponse<RiderResponse> getRider(@PathVariable Long id) {
        return ApiResponse.ok(RiderResponse.from(riderService.getById(id)));
    }

    @PostMapping("/rides")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RideResponse> requestRide(@Valid @RequestBody RideRequestDto req) {
        return ApiResponse.ok(RideResponse.from(riderService.requestRide(
                req.riderId(), req.pickupLat(), req.pickupLng(), req.dropLat(), req.dropLng())));
    }

    @GetMapping("/rides/{id}")
    public ApiResponse<RideResponse> getRide(@PathVariable Long id) {
        return ApiResponse.ok(RideResponse.from(riderService.getRide(id)));
    }
}
