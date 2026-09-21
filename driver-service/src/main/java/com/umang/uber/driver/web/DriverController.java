package com.umang.uber.driver.web;

import com.umang.uber.common.api.ApiResponse;
import com.umang.uber.driver.service.DriverService;
import com.umang.uber.driver.web.dto.DriverResponse;
import com.umang.uber.driver.web.dto.LocationPing;
import com.umang.uber.driver.web.dto.RegisterDriverRequest;
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
@RequestMapping("/api/v1/drivers")
@RequiredArgsConstructor
public class DriverController {

    private final DriverService driverService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DriverResponse> register(@Valid @RequestBody RegisterDriverRequest req) {
        return ApiResponse.ok(DriverResponse.from(
                driverService.register(req.name(), req.email(), req.vehicle())));
    }

    @GetMapping("/{id}")
    public ApiResponse<DriverResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(DriverResponse.from(driverService.getById(id)));
    }

    @PostMapping("/{id}/online")
    public ApiResponse<DriverResponse> online(@PathVariable Long id) {
        return ApiResponse.ok(DriverResponse.from(driverService.goOnline(id)));
    }

    @PostMapping("/{id}/offline")
    public ApiResponse<DriverResponse> offline(@PathVariable Long id) {
        return ApiResponse.ok(DriverResponse.from(driverService.goOffline(id)));
    }

    @PostMapping("/{id}/location")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<Void> location(@PathVariable Long id, @RequestBody LocationPing ping) {
        driverService.recordLocation(id, ping.lat(), ping.lng());
        return ApiResponse.ok(null);
    }
}
