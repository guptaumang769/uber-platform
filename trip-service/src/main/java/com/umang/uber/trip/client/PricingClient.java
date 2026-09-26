package com.umang.uber.trip.client;

import com.umang.uber.common.api.ApiResponse;
import java.math.BigDecimal;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Load-balanced client to pricing-service — fetches the fare estimate for a trip. */
@FeignClient(name = "pricing-service")
public interface PricingClient {

    @GetMapping("/api/v1/pricing/estimate")
    ApiResponse<FareEstimate> estimate(
            @RequestParam("pickupLat") double pickupLat,
            @RequestParam("pickupLng") double pickupLng,
            @RequestParam("dropLat") double dropLat,
            @RequestParam("dropLng") double dropLng);

    /** Mirrors pricing-service's FareEstimate. */
    record FareEstimate(BigDecimal distanceKm, BigDecimal surgeMultiplier, BigDecimal fare) {
    }
}
