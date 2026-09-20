package com.umang.uber.rider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Owns riders and ride requests. Requesting a ride writes the RideRequest row and a
 * RideRequestedEvent OUTBOX row in one transaction; a scheduled poller relays the event to Kafka
 * for matching-service to consume — so a ride request is never lost, even if Kafka is briefly down.
 */
@SpringBootApplication
@EnableScheduling
public class RiderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiderServiceApplication.class, args);
    }
}
