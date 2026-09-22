package com.umang.uber.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Maintains a live spatial index of drivers in Redis. Consumes DriverLocationEvent and GEOADDs
 * each driver's position; serves nearest-driver radius queries via GEOSEARCH for matching-service.
 */
@SpringBootApplication
public class LocationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocationServiceApplication.class, args);
    }
}
