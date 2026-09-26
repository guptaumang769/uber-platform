package com.umang.uber.trip.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.common.event.TripMatchedEvent;
import com.umang.uber.common.event.TripStatusEvent;
import com.umang.uber.trip.client.PricingGateway;
import com.umang.uber.trip.entity.OutboxEvent;
import com.umang.uber.trip.entity.Trip;
import com.umang.uber.trip.repository.OutboxEventRepository;
import com.umang.uber.trip.repository.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Trip aggregate. Enforces the {@link TripStateMachine} on every transition and emits a
 * TripStatusEvent via the OUTBOX in the same transaction as the state change — so the saga (the
 * COMPLETED event that triggers payment) can never be lost or phantomed relative to the DB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final OutboxEventRepository outboxRepository;
    private final PricingGateway pricingGateway;
    private final ObjectMapper objectMapper;

    /**
     * Create a Trip from a match. Idempotent on rideRequestId (at-least-once Kafka delivery): a
     * duplicate TripMatchedEvent returns the existing trip instead of creating a second one.
     * Fare is fetched from pricing-service now; if pricing is down the gateway returns 0 and the
     * fare is re-quoted at completion.
     */
    @Transactional
    public Trip createFromMatch(TripMatchedEvent event,
                                double pickupLat, double pickupLng,
                                double dropLat, double dropLng) {
        return tripRepository.findByRideRequestId(event.rideRequestId()).orElseGet(() -> {
            BigDecimal fare = pricingGateway.fare(pickupLat, pickupLng, dropLat, dropLng);
            Instant now = Instant.now();
            Trip trip = tripRepository.save(Trip.builder()
                    .rideRequestId(event.rideRequestId())
                    .riderId(event.riderId())
                    .driverId(event.driverId())
                    .status(TripStatus.MATCHED)
                    .fare(fare)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            writeStatusOutbox(trip);
            log.info("Created trip {} (MATCHED) for ride request {} fare={}",
                    trip.getId(), event.rideRequestId(), fare);
            return trip;
        });
    }

    @Transactional
    public Trip start(Long tripId) {
        // EN_ROUTE -> ONGOING (driver has picked the rider up).
        return transition(tripId, TripStatus.ONGOING);
    }

    @Transactional
    public Trip enRoute(Long tripId) {
        // MATCHED -> EN_ROUTE (driver heading to pickup).
        return transition(tripId, TripStatus.EN_ROUTE);
    }

    @Transactional
    public Trip complete(Long tripId) {
        // ONGOING -> COMPLETED. The emitted TripStatusEvent is what payment-service consumes.
        return transition(tripId, TripStatus.COMPLETED);
    }

    @Transactional
    public Trip cancel(Long tripId) {
        return transition(tripId, TripStatus.CANCELLED);
    }

    @Transactional(readOnly = true)
    public Trip getById(Long tripId) {
        return tripRepository.findById(tripId)
                .orElseThrow(() -> new IllegalArgumentException("No trip " + tripId));
    }

    /** Apply a validated state transition and emit the status event via the outbox. */
    private Trip transition(Long tripId, TripStatus target) {
        Trip trip = getById(tripId);
        TripStateMachine.assertCanTransition(trip.getStatus(), target);
        trip.setStatus(target);
        trip.setUpdatedAt(Instant.now());
        tripRepository.save(trip);
        writeStatusOutbox(trip);
        log.info("Trip {} -> {}", tripId, target);
        return trip;
    }

    private void writeStatusOutbox(Trip trip) {
        TripStatusEvent event = new TripStatusEvent(
                trip.getId(), trip.getStatus(), trip.getDriverId(), trip.getFare());
        outboxRepository.save(OutboxEvent.builder()
                .aggregateId(String.valueOf(trip.getId()))
                .eventType(TripStatusEvent.class.getSimpleName())
                .payload(serialize(event))
                .published(false)
                .createdAt(Instant.now())
                .build());
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }
}
