package com.umang.uber.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Real-time push to the rider's browser. Consumes trip status + driver location events off Kafka
 * and streams them to connected clients over Server-Sent Events (SSE), powering the live map.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
