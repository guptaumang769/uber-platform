package com.umang.uber.pricing.web;

import com.umang.uber.common.api.ApiResponse;
import com.umang.uber.pricing.service.PricingService;
import com.umang.uber.pricing.web.dto.FareEstimate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @GetMapping("/estimate")
    public ApiResponse<FareEstimate> estimate(
            @RequestParam double pickupLat,
            @RequestParam double pickupLng,
            @RequestParam double dropLat,
            @RequestParam double dropLng) {
        return ApiResponse.ok(pricingService.estimate(pickupLat, pickupLng, dropLat, dropLng));
    }
}
