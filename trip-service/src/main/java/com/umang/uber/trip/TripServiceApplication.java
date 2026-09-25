package com.umang.uber.trip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Owns the Trip aggregate and its lifecycle state machine. Creates a Trip on TripMatchedEvent,
 * fetches the fare from pricing-service, and drives MATCHED -> EN_ROUTE -> ONGOING -> COMPLETED
 * (or CANCELLED from early states). Every state change emits a TripStatusEvent via the outbox; on
 * COMPLETED that event drives the payment saga.
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients
public class TripServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TripServiceApplication.class, args);
    }
}
