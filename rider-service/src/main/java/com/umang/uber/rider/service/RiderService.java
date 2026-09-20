package com.umang.uber.rider.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.common.event.RideRequestedEvent;
import com.umang.uber.rider.entity.OutboxEvent;
import com.umang.uber.rider.entity.RideRequest;
import com.umang.uber.rider.entity.Rider;
import com.umang.uber.rider.repository.OutboxEventRepository;
import com.umang.uber.rider.repository.RideRequestRepository;
import com.umang.uber.rider.repository.RiderRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers riders and accepts ride requests.
 *
 * <p>On ride request: the RideRequest row and its RideRequestedEvent OUTBOX row are written in ONE
 * transaction, so we never risk the DB committing while the Kafka publish fails (or vice versa).
 * matching-service consumes the event and finds the nearest available driver.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderService {

    private final RiderRepository riderRepository;
    private final RideRequestRepository rideRequestRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Rider register(String name, String email, String phone) {
        return riderRepository.save(Rider.builder().name(name).email(email).phone(phone).build());
    }

    @Transactional(readOnly = true)
    public Rider getById(Long id) {
        return riderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No rider " + id));
    }

    @Transactional
    public RideRequest requestRide(Long riderId, double pickupLat, double pickupLng,
                                   double dropLat, double dropLng) {
        // Fail fast if the rider does not exist.
        riderRepository.findById(riderId)
                .orElseThrow(() -> new IllegalArgumentException("No rider " + riderId));

        Instant now = Instant.now();
        RideRequest request = rideRequestRepository.save(RideRequest.builder()
                .riderId(riderId)
                .pickupLat(pickupLat)
                .pickupLng(pickupLng)
                .dropLat(dropLat)
                .dropLng(dropLng)
                .status(TripStatus.REQUESTED)
                .createdAt(now)
                .build());

        RideRequestedEvent event = new RideRequestedEvent(
                request.getId(), riderId, pickupLat, pickupLng, dropLat, dropLng, now.toEpochMilli());
        // Same transaction as the ride-request insert — this is the whole point of the outbox.
        outboxRepository.save(OutboxEvent.builder()
                .aggregateId(String.valueOf(request.getId()))
                .eventType(RideRequestedEvent.class.getSimpleName())
                .payload(serialize(event))
                .published(false)
                .createdAt(now)
                .build());

        log.info("Ride request {} by rider {} + outbox event written in one tx",
                request.getId(), riderId);
        return request;
    }

    @Transactional(readOnly = true)
    public RideRequest getRide(Long id) {
        return rideRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No ride request " + id));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event", e);
        }
    }
}
