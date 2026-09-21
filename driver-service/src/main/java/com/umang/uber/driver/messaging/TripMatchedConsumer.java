package com.umang.uber.driver.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.common.event.TripMatchedEvent;
import com.umang.uber.common.event.TripStatusEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.driver.service.DriverService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes trip-events and manages driver availability across the full trip lifecycle.
 *
 * <ul>
 *   <li>TripMatchedEvent (identified by {@code rideRequestId}) — flip driver to ON_TRIP.</li>
 *   <li>TripStatusEvent COMPLETED/CANCELLED (identified by {@code status}) — flip driver back to
 *       AVAILABLE so they re-enter the matching pool for future rides.</li>
 * </ul>
 *
 * <p>Discriminator: only TripMatchedEvent carries {@code rideRequestId}; TripStatusEvent carries
 * {@code status}. This is more robust than checking {@code driverId} because TripStatusEvent now
 * also carries driverId (so the driver can be freed without a DB lookup in trip-service).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripMatchedConsumer {

    private final DriverService driverService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = "driver-service",
            containerFactory = "stringListenerFactory")
    public void onTripEvent(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        if (node.hasNonNull("rideRequestId")) {
            // TripMatchedEvent: driver is now on a trip.
            TripMatchedEvent event = objectMapper.treeToValue(node, TripMatchedEvent.class);
            driverService.markOnTrip(event.driverId());
        } else if (node.hasNonNull("status")) {
            // TripStatusEvent: free the driver when the trip ends.
            TripStatusEvent event = objectMapper.treeToValue(node, TripStatusEvent.class);
            if ((event.status() == TripStatus.COMPLETED || event.status() == TripStatus.CANCELLED)
                    && event.driverId() != null) {
                driverService.freeFromTrip(event.driverId());
            }
        }
    }
}
