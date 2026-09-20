package com.umang.uber.rider.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.common.event.TripMatchedEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.rider.repository.RideRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code ride_requests.status} in sync with the trip lifecycle so riders can query their
 * current request status via the rider-service API.
 *
 * <p>TripMatchedEvent (identified by {@code rideRequestId}) → set status to MATCHED. This is the
 * most visible inconsistency: without this consumer a rider's GET /rides/{id} always returns
 * REQUESTED even after a driver has been assigned.
 *
 * <p>Subsequent transitions (EN_ROUTE, ONGOING, COMPLETED, CANCELLED) are not propagated here
 * because TripStatusEvent carries {@code tripId} — not {@code rideRequestId} — and the
 * ride_requests table has no tripId column. A future improvement is to store the assigned tripId on
 * the RideRequest (a migration + FK), at which point all statuses can be mirrored here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripEventConsumer {

    private final RideRequestRepository rideRequestRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = "rider-service",
            containerFactory = "stringListenerFactory")
    @Transactional
    public void onTripEvent(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        if (node.hasNonNull("rideRequestId")) {
            TripMatchedEvent event = objectMapper.treeToValue(node, TripMatchedEvent.class);
            rideRequestRepository.findById(event.rideRequestId()).ifPresent(req -> {
                req.setStatus(TripStatus.MATCHED);
                rideRequestRepository.save(req);
                log.info("RideRequest {} -> MATCHED (driver {})", event.rideRequestId(), event.driverId());
            });
        }
    }
}
