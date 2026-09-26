package com.umang.uber.trip.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.TripMatchedEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes trip-events and creates a Trip when a TripMatchedEvent arrives.
 *
 * <p>The topic also carries the TripStatusEvents this service itself emits, so we branch on the
 * JSON: only TripMatchedEvent has a {@code driverId}. We ignore our own status events to avoid a
 * self-consumption loop.
 *
 * <p>TripMatchedEvent carries pickup/drop coordinates forwarded by matching-service from the
 * original RideRequestedEvent, so trip-service can price the real route immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripMatchedConsumer {

    private final TripService tripService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = "trip-service",
            containerFactory = "stringListenerFactory")
    public void onTripEvent(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        // TripMatchedEvent carries rideRequestId; TripStatusEvents (this service's own outbox) do not.
        if (node.hasNonNull("rideRequestId")) {
            TripMatchedEvent event = objectMapper.treeToValue(node, TripMatchedEvent.class);
            tripService.createFromMatch(event,
                    event.pickupLat(), event.pickupLng(), event.dropLat(), event.dropLng());
        }
    }
}
