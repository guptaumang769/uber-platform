package com.umang.uber.pricing.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.DriverLocationEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.pricing.service.SurgeTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds driver GPS pings into the surge supply counters.
 *
 * <p>Every driver location ping is a signal that a driver is present and potentially available in
 * that geo cell. Incrementing the supply counter on each ping lets {@link SurgeTracker#surgeFor}
 * compute a realistic demand/supply ratio rather than a one-sided demand-only curve.
 *
 * <p>The supply key has the same TTL as demand, so supply naturally decays if a driver stops
 * pinging (goes offline, closes the app). No explicit "driver went offline" event is required —
 * stale supply counters simply expire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverSupplyConsumer {

    private final SurgeTracker surgeTracker;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.DRIVER_LOCATIONS, groupId = "pricing-service",
            containerFactory = "stringListenerFactory")
    public void onDriverLocation(String payload) throws Exception {
        DriverLocationEvent event = objectMapper.readValue(payload, DriverLocationEvent.class);
        surgeTracker.recordSupply(event.lat(), event.lng());
        log.debug("Supply bumped for driver {} at ({}, {})", event.driverId(), event.lat(), event.lng());
    }
}
