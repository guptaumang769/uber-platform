package com.umang.uber.matching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * The signature matching engine. Consumes RideRequestedEvent, asks location-service for the
 * nearest candidate drivers, and assigns the closest one that it can atomically lock — the lock is
 * what prevents two concurrent ride requests from grabbing the same driver.
 */
@SpringBootApplication
@EnableFeignClients
public class MatchingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MatchingServiceApplication.class, args);
    }
}
