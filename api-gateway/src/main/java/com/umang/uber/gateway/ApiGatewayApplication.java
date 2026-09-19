package com.umang.uber.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point for external clients. The gateway owns cross-cutting concerns — routing,
 * load-balanced service discovery (lb://), CORS, and edge resilience (a circuit-breaker filter
 * with a fallback) — so individual services stay focused on domain logic.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
