package com.umang.uber.matching.client;

import com.umang.uber.matching.client.LocationClient.NearbyDriver;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resilient facade over {@link LocationClient}. Matching depends on location-service to find
 * candidates; a @CircuitBreaker with an empty-list fallback means a location outage degrades to
 * "no match this round" (the ride request can be retried) instead of blocking the Kafka consumer
 * thread on a dead dependency.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationGateway {

    private final LocationClient locationClient;

    @CircuitBreaker(name = "locationService", fallbackMethod = "emptyFallback")
    public List<NearbyDriver> nearby(double lat, double lng, double radiusKm) {
        var resp = locationClient.nearby(lat, lng, radiusKm);
        return resp != null && resp.getData() != null ? resp.getData() : List.of();
    }

    @SuppressWarnings("unused") // referenced by name from @CircuitBreaker(fallbackMethod=...)
    private List<NearbyDriver> emptyFallback(double lat, double lng, double radiusKm, Throwable t) {
        log.warn("location-service unavailable for ({},{}) — no candidates this round: {}",
                lat, lng, t.toString());
        return List.of();
    }
}
