package com.umang.uber.trip.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilient facade over {@link PricingClient}. Creating a trip must not fail just because pricing
 * is momentarily down; a @CircuitBreaker with a zero-fare fallback lets the trip be created and
 * the fare reconciled later (e.g. re-quoted at completion) rather than blocking the match consumer.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingGateway {

    private final PricingClient pricingClient;

    @CircuitBreaker(name = "pricingService", fallbackMethod = "zeroFallback")
    public BigDecimal fare(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        var resp = pricingClient.estimate(pickupLat, pickupLng, dropLat, dropLng);
        if (resp != null && resp.getData() != null && resp.getData().fare() != null) {
            return resp.getData().fare();
        }
        return BigDecimal.ZERO;
    }

    @SuppressWarnings("unused") // referenced by name from @CircuitBreaker(fallbackMethod=...)
    private BigDecimal zeroFallback(double pickupLat, double pickupLng,
                                    double dropLat, double dropLng, Throwable t) {
        log.warn("pricing-service unavailable — deferring fare (0.0): {}", t.toString());
        return BigDecimal.ZERO;
    }
}
