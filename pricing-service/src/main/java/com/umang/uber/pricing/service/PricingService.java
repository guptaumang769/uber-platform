package com.umang.uber.pricing.service;

import com.umang.uber.common.geo.GeoUtil;
import com.umang.uber.pricing.web.dto.FareEstimate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Fare = base + (distanceKm x perKmRate), all scaled by the current surge multiplier for the
 * pickup cell. Requesting an estimate also records demand in that cell, so a burst of estimates in
 * one area naturally pushes that area's surge up.
 */
@Service
@RequiredArgsConstructor
public class PricingService {

    private final SurgeTracker surgeTracker;

    @Value("${uber.pricing.base-fare:30.0}")
    private double baseFare;

    @Value("${uber.pricing.per-km-rate:12.0}")
    private double perKmRate;

    public FareEstimate estimate(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        double distanceKm = GeoUtil.haversineKm(pickupLat, pickupLng, dropLat, dropLng);
        double surge = surgeTracker.surgeFor(pickupLat, pickupLng);

        double raw = (baseFare + distanceKm * perKmRate) * surge;
        BigDecimal fare = BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);

        // Requesting a quote is a demand signal for this cell.
        surgeTracker.recordDemand(pickupLat, pickupLng);

        return new FareEstimate(
                BigDecimal.valueOf(distanceKm).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(surge).setScale(2, RoundingMode.HALF_UP),
                fare);
    }
}
