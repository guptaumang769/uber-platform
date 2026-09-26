package com.umang.uber.trip.web;

import com.umang.uber.common.api.ApiResponse;
import com.umang.uber.trip.service.TripService;
import com.umang.uber.trip.web.dto.TripResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @GetMapping("/{id}")
    public ApiResponse<TripResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(TripResponse.from(tripService.getById(id)));
    }

    /** Driver reached the rider and moved off — EN_ROUTE -> ONGOING. */
    @PostMapping("/{id}/start")
    public ApiResponse<TripResponse> start(@PathVariable Long id) {
        return ApiResponse.ok(TripResponse.from(tripService.start(id)));
    }

    /** Driver is heading to the pickup — MATCHED -> EN_ROUTE. */
    @PostMapping("/{id}/enroute")
    public ApiResponse<TripResponse> enRoute(@PathVariable Long id) {
        return ApiResponse.ok(TripResponse.from(tripService.enRoute(id)));
    }

    /** Trip finished — ONGOING -> COMPLETED (emits the event that drives payment). */
    @PostMapping("/{id}/complete")
    public ApiResponse<TripResponse> complete(@PathVariable Long id) {
        return ApiResponse.ok(TripResponse.from(tripService.complete(id)));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<TripResponse> cancel(@PathVariable Long id) {
        return ApiResponse.ok(TripResponse.from(tripService.cancel(id)));
    }
}
