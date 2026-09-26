package com.umang.uber.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Captures the fare when a trip completes. Consumption is idempotent on tripId (unique constraint)
 * so a redelivered trip-completed event never double-charges. On capture it publishes a
 * PaymentCompletedEvent — the tail of the ride saga.
 */
@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
