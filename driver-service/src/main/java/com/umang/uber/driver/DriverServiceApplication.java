package com.umang.uber.driver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Owns drivers. Location pings are published to Kafka (driver-locations) for location-service to
 * index in Redis GEO. A consumer for TripMatchedEvent flips the matched driver to ON_TRIP.
 */
@SpringBootApplication
public class DriverServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DriverServiceApplication.class, args);
    }
}
