package com.umang.uber.notification.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.DriverLocationEvent;
import com.umang.uber.common.event.TripStatusEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.notification.service.TripStreamRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Bridges Kafka to the SSE streams.
 *
 * <ul>
 *   <li>trip-events (TripStatusEvent) -> push a "status" SSE event to that trip's subscribers.</li>
 *   <li>driver-locations (DriverLocationEvent) -> push a "location" SSE event. Location isn't
 *       keyed by trip, so we broadcast it; a fuller implementation would map driver->trip and
 *       target only the relevant rider.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventConsumer {

    private final TripStreamRegistry registry;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = "notification-service",
            containerFactory = "stringListenerFactory")
    public void onTripEvent(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        // TripMatchedEvent carries rideRequestId; TripStatusEvent carries status but not rideRequestId.
        if (node.hasNonNull("status") && !node.hasNonNull("rideRequestId")) {
            TripStatusEvent event = objectMapper.treeToValue(node, TripStatusEvent.class);
            registry.publish(event.tripId(), "status", event);
        }
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_LOCATIONS, groupId = "notification-service",
            containerFactory = "stringListenerFactory")
    public void onDriverLocation(String payload) throws Exception {
        DriverLocationEvent event = objectMapper.readValue(payload, DriverLocationEvent.class);
        // Location isn't trip-scoped in this demo; broadcast to all open trip streams.
        registry.broadcast("location", event);
    }
}
