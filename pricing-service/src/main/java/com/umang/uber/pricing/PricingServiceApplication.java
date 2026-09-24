package com.umang.uber.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Estimates fares. Base fare is derived from the haversine trip distance; a surge multiplier
 * (demand vs supply per geo-cell, tracked in Redis) scales it up when a neighbourhood is busy.
 */
@SpringBootApplication
public class PricingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PricingServiceApplication.class, args);
    }
}
