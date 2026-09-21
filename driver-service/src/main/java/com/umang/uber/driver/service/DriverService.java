package com.umang.uber.driver.service;

import com.umang.uber.common.enums.DriverStatus;
import com.umang.uber.common.event.DriverLocationEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.driver.entity.Driver;
import com.umang.uber.driver.repository.DriverRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registers drivers, manages their availability, and relays GPS pings.
 *
 * <p>A location ping updates the driver's last-known position AND publishes a DriverLocationEvent
 * to Kafka so location-service can GEOADD it to the Redis geo set. Publishing here (not via an
 * outbox) is acceptable because location is high-frequency, best-effort telemetry — a dropped ping
 * is simply superseded by the next one, so at-most-once is fine and cheaper than an outbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public Driver register(String name, String email, String vehicle) {
        // New drivers start OFFLINE until they explicitly go online.
        return driverRepository.save(Driver.builder()
                .name(name).email(email).vehicle(vehicle).status(DriverStatus.OFFLINE).build());
    }

    @Transactional(readOnly = true)
    public Driver getById(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No driver " + id));
    }

    @Transactional
    public Driver goOnline(Long id) {
        Driver driver = getById(id);
        // A driver mid-trip stays ON_TRIP; going "online" only makes an idle driver AVAILABLE.
        if (driver.getStatus() == DriverStatus.OFFLINE) {
            driver.setStatus(DriverStatus.AVAILABLE);
        }
        return driverRepository.save(driver);
    }

    @Transactional
    public Driver goOffline(Long id) {
        Driver driver = getById(id);
        driver.setStatus(DriverStatus.OFFLINE);
        return driverRepository.save(driver);
    }

    @Transactional
    public void recordLocation(Long id, double lat, double lng) {
        Driver driver = getById(id);
        driver.setCurrentLat(lat);
        driver.setCurrentLng(lng);
        driverRepository.save(driver);

        DriverLocationEvent event =
                new DriverLocationEvent(id, lat, lng, Instant.now().toEpochMilli());
        kafkaTemplate.send(KafkaTopics.DRIVER_LOCATIONS, String.valueOf(id), serialize(event));
        log.debug("Driver {} location ping ({}, {}) published", id, lat, lng);
    }

    /** Called by the TripMatchedEvent consumer to take a driver off the available pool. */
    @Transactional
    public void markOnTrip(Long driverId) {
        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null) {
            log.error("TripMatchedEvent for unknown driver {}", driverId);
            return;
        }
        driver.setStatus(DriverStatus.ON_TRIP);
        driverRepository.save(driver);
        log.info("Driver {} marked ON_TRIP", driverId);
    }

    /**
     * Called when a trip reaches COMPLETED or CANCELLED. Puts the driver back into AVAILABLE so
     * they re-enter the matching pool. Only transitions from ON_TRIP; a driver who somehow already
     * went OFFLINE (e.g. they called goOffline) is left in their current state.
     */
    @Transactional
    public void freeFromTrip(Long driverId) {
        Driver driver = driverRepository.findById(driverId).orElse(null);
        if (driver == null) {
            log.error("TripStatusEvent (terminal) for unknown driver {}", driverId);
            return;
        }
        if (driver.getStatus() == DriverStatus.ON_TRIP) {
            driver.setStatus(DriverStatus.AVAILABLE);
            driverRepository.save(driver);
            log.info("Driver {} freed from trip -> AVAILABLE", driverId);
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
