package com.umang.uber.location.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.DriverLocationEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.location.repository.DriverGeoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes driver GPS pings and GEOADDs each into the Redis geo set. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriverLocationConsumer {

    private final DriverGeoRepository geoRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.DRIVER_LOCATIONS, groupId = "location-service",
            containerFactory = "stringListenerFactory")
    public void onLocation(String payload) throws Exception {
        DriverLocationEvent event = objectMapper.readValue(payload, DriverLocationEvent.class);
        geoRepository.upsert(event.driverId(), event.lat(), event.lng());
        log.debug("GEOADD driver {} -> ({}, {})", event.driverId(), event.lat(), event.lng());
    }
}
