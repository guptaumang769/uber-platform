package com.umang.uber.common.messaging;

/** Single source of truth for topic names shared across producers and consumers. */
public final class KafkaTopics {

    private KafkaTopics() {
    }

    /** Ride requests emitted by rider-service (via outbox) — consumed by matching-service. */
    public static final String RIDE_REQUESTS = "ride-requests";

    /** Driver GPS pings emitted by driver-service — consumed by location + notification services. */
    public static final String DRIVER_LOCATIONS = "driver-locations";

    /** Trip lifecycle events (matched / status changes) — feeds driver, notification, payment. */
    public static final String TRIP_EVENTS = "trip-events";

    /** Payment lifecycle events (payment completed) emitted by payment-service. */
    public static final String PAYMENT_EVENTS = "payment-events";

    /** Dead-letter suffix convention: <topic>.DLT */
    public static final String DLT_SUFFIX = ".DLT";
}
