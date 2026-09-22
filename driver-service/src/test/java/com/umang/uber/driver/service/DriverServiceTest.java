package com.umang.uber.driver.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.DriverStatus;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.driver.entity.Driver;
import com.umang.uber.driver.repository.DriverRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class DriverServiceTest {

    @Mock
    private DriverRepository driverRepository;
    @Mock
    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private DriverService driverService;

    @BeforeEach
    void setUp() {
        driverService = new DriverService(driverRepository, kafkaTemplate, objectMapper);
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubDriver(Long id, DriverStatus status) {
        when(driverRepository.findById(id)).thenReturn(Optional.of(
                Driver.builder().id(id).name("D").email("d@x.com").status(status).build()));
    }

    @Test
    void goOnline_movesOfflineDriverToAvailable() {
        stubDriver(1L, DriverStatus.OFFLINE);
        Driver result = driverService.goOnline(1L);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
    }

    @Test
    void goOnline_doesNotDisturbDriverOnTrip() {
        stubDriver(1L, DriverStatus.ON_TRIP);
        Driver result = driverService.goOnline(1L);
        // Going "online" must not pull a driver out of an active trip.
        assertThat(result.getStatus()).isEqualTo(DriverStatus.ON_TRIP);
    }

    @Test
    void goOffline_movesAvailableDriverToOffline() {
        stubDriver(1L, DriverStatus.AVAILABLE);
        Driver result = driverService.goOffline(1L);
        assertThat(result.getStatus()).isEqualTo(DriverStatus.OFFLINE);
    }

    @Test
    void recordLocation_publishesDriverLocationEvent() {
        stubDriver(1L, DriverStatus.AVAILABLE);
        driverService.recordLocation(1L, 12.97, 77.59);
        // Location ping must be relayed to Kafka for the Redis GEO index.
        verify(kafkaTemplate).send(eq(KafkaTopics.DRIVER_LOCATIONS), eq("1"), anyString());
    }

    @Test
    void markOnTrip_setsDriverToOnTrip() {
        stubDriver(5L, DriverStatus.AVAILABLE);
        driverService.markOnTrip(5L);
        // Driver must be taken off the matching pool while the trip is active.
        verify(driverRepository).save(
                org.mockito.ArgumentMatchers.argThat(d -> d.getStatus() == DriverStatus.ON_TRIP));
    }

    @Test
    void markOnTrip_unknownDriver_doesNotThrow() {
        when(driverRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        // A missing driver ID (late event, DB lag) must not crash the consumer.
        driverService.markOnTrip(99L);
        verify(driverRepository, org.mockito.Mockito.never()).save(any(Driver.class));
    }

    @Test
    void freeFromTrip_setsOnTripDriverToAvailable() {
        stubDriver(5L, DriverStatus.ON_TRIP);
        driverService.freeFromTrip(5L);
        // Driver must re-enter the available pool after a completed or cancelled trip.
        verify(driverRepository).save(
                org.mockito.ArgumentMatchers.argThat(d -> d.getStatus() == DriverStatus.AVAILABLE));
    }

    @Test
    void freeFromTrip_offlineDriver_remainsOffline() {
        stubDriver(5L, DriverStatus.OFFLINE);
        driverService.freeFromTrip(5L);
        // A driver who went offline during a trip must not be forced back to AVAILABLE.
        verify(driverRepository, org.mockito.Mockito.never()).save(any(Driver.class));
    }

    @Test
    void freeFromTrip_unknownDriver_doesNotThrow() {
        when(driverRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        // Late or out-of-order event for a deleted driver must not crash the consumer.
        driverService.freeFromTrip(99L);
        verify(driverRepository, org.mockito.Mockito.never()).save(any(Driver.class));
    }
}
