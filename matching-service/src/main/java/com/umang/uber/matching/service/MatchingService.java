package com.umang.uber.matching.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.RideRequestedEvent;
import com.umang.uber.common.event.TripMatchedEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.matching.client.LocationClient.NearbyDriver;
import com.umang.uber.matching.client.LocationGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Core matching algorithm.
 *
 * <p>Flow: ask location-service for candidate drivers near the pickup, sorted nearest-first
 * (GEOSEARCH already returns them ascending by distance). Walk that list and assign the FIRST
 * driver we can atomically lock. The lock — not just "pick index 0" — is what makes this safe
 * under concurrency: two ride requests processed at the same time may both see driver D as their
 * nearest, but only one wins {@link DriverLock#tryLock}; the loser transparently falls through to
 * the next-nearest candidate. Without the lock we would double-assign the same driver.
 *
 * <p>On a successful assignment we publish a TripMatchedEvent (trip-service creates the Trip;
 * driver-service flips the driver to ON_TRIP). We deliberately do NOT release the lock on success:
 * driver-service marks the driver ON_TRIP, and the lock's TTL cleans it up regardless.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final LocationGateway locationGateway;
    private final DriverLock driverLock;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${uber.matching.search-radius-km:5.0}")
    private double searchRadiusKm;

    /** Attempt to match a ride request to the nearest lockable driver. Returns the winner, or null. */
    public Long match(RideRequestedEvent event) {
        List<NearbyDriver> candidates = locationGateway.nearby(
                event.pickupLat(), event.pickupLng(), searchRadiusKm);

        if (candidates.isEmpty()) {
            log.warn("No candidate drivers near ride request {} — will not match this round",
                    event.rideRequestId());
            return null;
        }

        // Candidates are already sorted nearest-first by GEOSEARCH; take the first we can lock.
        for (NearbyDriver candidate : candidates) {
            Long driverId = candidate.driverId();
            if (driverLock.tryLock(driverId, event.rideRequestId())) {
                // We won the driver. Publish the match.
                publishMatch(event, driverId);
                log.info("Matched ride request {} -> driver {} ({} km away)",
                        event.rideRequestId(), driverId, candidate.distanceKm());
                return driverId;
            }
            // Someone else already grabbed this driver — try the next-nearest.
            log.debug("Driver {} already taken (lost lock) — trying next candidate", driverId);
        }

        log.warn("All {} candidate drivers for ride request {} were already taken",
                candidates.size(), event.rideRequestId());
        return null;
    }

    private void publishMatch(RideRequestedEvent event, Long driverId) {
        // tripId is left null: trip-service owns Trip identity and mints it on consume.
        // Coordinates are forwarded so trip-service can compute the real fare via pricing-service.
        TripMatchedEvent matched = new TripMatchedEvent(
                null, event.rideRequestId(), driverId, event.riderId(),
                event.pickupLat(), event.pickupLng(), event.dropLat(), event.dropLng());
        try {
            kafkaTemplate.send(KafkaTopics.TRIP_EVENTS,
                    String.valueOf(event.rideRequestId()),
                    objectMapper.writeValueAsString(matched));
        } catch (JsonProcessingException e) {
            // Publishing failed — free the driver so a retry can reassign them.
            driverLock.release(driverId);
            throw new IllegalStateException("Failed to publish TripMatchedEvent", e);
        }
    }
}
