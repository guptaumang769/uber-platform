package com.umang.uber.rider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.rider.entity.OutboxEvent;
import com.umang.uber.rider.entity.RideRequest;
import com.umang.uber.rider.entity.Rider;
import com.umang.uber.rider.repository.OutboxEventRepository;
import com.umang.uber.rider.repository.RideRequestRepository;
import com.umang.uber.rider.repository.RiderRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RiderServiceTest {

    @Mock
    private RiderRepository riderRepository;
    @Mock
    private RideRequestRepository rideRequestRepository;
    @Mock
    private OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private RiderService riderService;

    @BeforeEach
    void setUp() {
        riderService = new RiderService(
                riderRepository, rideRequestRepository, outboxRepository, objectMapper);
    }

    @Test
    void requestRide_unknownRider_throwsIllegalArgumentException() {
        when(riderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> riderService.requestRide(99L, 12.97, 77.59, 12.93, 77.62))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No rider 99");
    }

    @Test
    void getRide_unknownRequest_throwsIllegalArgumentException() {
        when(rideRequestRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> riderService.getRide(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No ride request 404");
    }

    @Test
    void requestRide_savesRequest_andWritesOutboxEvent() {
        when(riderRepository.findById(7L))
                .thenReturn(Optional.of(Rider.builder().id(7L).name("Asha").email("a@x.com").build()));
        when(rideRequestRepository.save(any(RideRequest.class))).thenAnswer(inv -> {
            RideRequest r = inv.getArgument(0);
            r.setId(101L);
            return r;
        });

        RideRequest result = riderService.requestRide(7L, 12.97, 77.59, 12.93, 77.62);

        assertThat(result.getId()).isEqualTo(101L);
        assertThat(result.getStatus()).isEqualTo(TripStatus.REQUESTED);
        // Outbox row must be written in the same flow (atomic write, no dual-write to Kafka).
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("RideRequestedEvent");
        assertThat(outbox.getAggregateId()).isEqualTo("101");
        assertThat(outbox.isPublished()).isFalse();
        // Serialized payload carries the pickup coordinates.
        assertThat(outbox.getPayload()).contains("12.97").contains("77.59");
    }
}
