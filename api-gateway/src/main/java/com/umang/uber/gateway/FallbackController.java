package com.umang.uber.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Target of the gateway circuit-breakers' fallbackUri. When a downstream service is failing or
 * slow and its breaker opens, requests are routed here instead of piling up on the dead
 * dependency — the client gets a fast, clear 503 rather than a hung connection.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping(value = "/trips", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> tripsFallback() {
        return unavailable("TRIP_SERVICE_UNAVAILABLE",
                "Trip service is temporarily unavailable, please retry.");
    }

    @GetMapping(value = "/pricing", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> pricingFallback() {
        return unavailable("PRICING_SERVICE_UNAVAILABLE",
                "Pricing service is temporarily unavailable, please retry.");
    }

    private Mono<ResponseEntity<String>> unavailable(String code, String message) {
        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"success\":false,\"error\":{\"code\":\"" + code + "\","
                        + "\"message\":\"" + message + "\"}}"));
    }
}
