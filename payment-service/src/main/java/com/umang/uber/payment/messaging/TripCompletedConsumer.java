package com.umang.uber.payment.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.common.event.TripStatusEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes trip-events and captures the fare when a trip reaches COMPLETED. Only COMPLETED status
 * events trigger a capture; the TripMatchedEvent that also rides this topic carries a
 * {@code rideRequestId} and is ignored here.
 *
 * <p>TripStatusEvent now carries the actual {@code fare} set when the trip was created, so the
 * captured amount reflects the real pricing-service quote instead of a hardcoded ZERO placeholder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripCompletedConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = "payment-service",
            containerFactory = "stringListenerFactory")
    public void onTripEvent(String payload) throws Exception {
        var node = objectMapper.readTree(payload);
        // TripMatchedEvent carries rideRequestId; ignore it and any other non-status message.
        if (node.hasNonNull("rideRequestId") || !node.hasNonNull("status")) {
            return;
        }
        TripStatusEvent event = objectMapper.treeToValue(node, TripStatusEvent.class);
        if (event.status() == TripStatus.COMPLETED) {
            paymentService.capture(event.tripId(), event.fare());
        }
    }
}
