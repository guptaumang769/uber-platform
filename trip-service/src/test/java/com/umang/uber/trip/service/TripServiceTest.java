package com.umang.uber.trip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.enums.TripStatus;
import com.umang.uber.common.event.TripMatchedEvent;
import com.umang.uber.trip.client.PricingGateway;
import com.umang.uber.trip.entity.OutboxEvent;
import com.umang.uber.trip.entity.Trip;
import com.umang.uber.trip.repository.OutboxEventRepository;
import com.umang.uber.trip.repository.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock
    private TripRepository tripRepository;
    @Mock
    private OutboxEventRepository outboxRepository;
    @Mock
    private PricingGateway pricingGateway;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private TripService tripService;

    private static final TripMatchedEvent MATCH_EVENT =
            new TripMatchedEvent(null, 10L, 3L, 7L, 12.97, 77.59, 12.93, 77.62);

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, outboxRepository, pricingGateway, objectMapper);
    }

    private Trip savedTrip(Long id, Long rideRequestId, TripStatus status, BigDecimal fare) {
        return Trip.builder()
                .id(id)
                .rideRequestId(rideRequestId)
                .riderId(7L)
                .driverId(3L)
                .status(status)
                .fare(fare)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createFromMatch_persistsTripWithCorrectFields() {
        when(tripRepository.findByRideRequestId(10L)).thenReturn(Optional.empty());
        when(pricingGateway.fare(12.97, 77.59, 12.93, 77.62)).thenReturn(new BigDecimal("120.00"));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            t = savedTrip(100L, t.getRideRequestId(), t.getStatus(), t.getFare());
            return t;
        });

        Trip result = tripService.createFromMatch(MATCH_EVENT, 12.97, 77.59, 12.93, 77.62);

        assertThat(result.getRideRequestId()).isEqualTo(10L);
        assertThat(result.getDriverId()).isEqualTo(3L);
        assertThat(result.getRiderId()).isEqualTo(7L);
        assertThat(result.getStatus()).isEqualTo(TripStatus.MATCHED);
        assertThat(result.getFare()).isEqualByComparingTo("120.00");
    }

    @Test
    void createFromMatch_writesOutboxEventWithDriverIdAndFare() {
        when(tripRepository.findByRideRequestId(10L)).thenReturn(Optional.empty());
        when(pricingGateway.fare(12.97, 77.59, 12.93, 77.62)).thenReturn(new BigDecimal("120.00"));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            return savedTrip(100L, t.getRideRequestId(), t.getStatus(), t.getFare());
        });

        tripService.createFromMatch(MATCH_EVENT, 12.97, 77.59, 12.93, 77.62);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getEventType()).isEqualTo("TripStatusEvent");
        assertThat(outbox.isPublished()).isFalse();
        // Outbox payload must carry driverId and fare for payment-service
        assertThat(outbox.getPayload()).contains("\"driverId\":3");
        assertThat(outbox.getPayload()).contains("120.00");
    }

    @Test
    void createFromMatch_idempotent_duplicateEventReturnsExistingTrip() {
        Trip existing = savedTrip(100L, 10L, TripStatus.MATCHED, new BigDecimal("120.00"));
        when(tripRepository.findByRideRequestId(10L)).thenReturn(Optional.of(existing));

        Trip result = tripService.createFromMatch(MATCH_EVENT, 12.97, 77.59, 12.93, 77.62);

        assertThat(result.getId()).isEqualTo(100L);
        // No second insert and no duplicate outbox event.
        verify(tripRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createFromMatch_pricingDown_storesZeroFare() {
        when(tripRepository.findByRideRequestId(10L)).thenReturn(Optional.empty());
        // Pricing circuit-breaker fallback returns ZERO on failure.
        when(pricingGateway.fare(12.97, 77.59, 12.93, 77.62)).thenReturn(BigDecimal.ZERO);
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> {
            Trip t = inv.getArgument(0);
            return savedTrip(100L, t.getRideRequestId(), t.getStatus(), t.getFare());
        });

        Trip result = tripService.createFromMatch(MATCH_EVENT, 12.97, 77.59, 12.93, 77.62);

        assertThat(result.getFare()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void enRoute_transitions_MATCHED_to_EN_ROUTE_andWritesOutbox() {
        Trip trip = savedTrip(1L, 10L, TripStatus.MATCHED, new BigDecimal("120.00"));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        Trip result = tripService.enRoute(1L);

        assertThat(result.getStatus()).isEqualTo(TripStatus.EN_ROUTE);
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void start_transitions_EN_ROUTE_to_ONGOING() {
        Trip trip = savedTrip(1L, 10L, TripStatus.EN_ROUTE, new BigDecimal("120.00"));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        Trip result = tripService.start(1L);

        assertThat(result.getStatus()).isEqualTo(TripStatus.ONGOING);
    }

    @Test
    void complete_transitions_ONGOING_to_COMPLETED_andOutboxCarriesFare() {
        Trip trip = savedTrip(1L, 10L, TripStatus.ONGOING, new BigDecimal("120.00"));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        Trip result = tripService.complete(1L);

        assertThat(result.getStatus()).isEqualTo(TripStatus.COMPLETED);
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(captor.capture());
        // payment-service reads fare from the outbox payload — it must be present
        assertThat(captor.getValue().getPayload()).contains("120.00");
    }

    @Test
    void cancel_transitions_MATCHED_to_CANCELLED() {
        Trip trip = savedTrip(1L, 10L, TripStatus.MATCHED, new BigDecimal("120.00"));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any(Trip.class))).thenAnswer(inv -> inv.getArgument(0));

        Trip result = tripService.cancel(1L);

        assertThat(result.getStatus()).isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void illegalTransition_throwsIllegalStateException() {
        Trip trip = savedTrip(1L, 10L, TripStatus.COMPLETED, new BigDecimal("120.00"));
        when(tripRepository.findById(1L)).thenReturn(Optional.of(trip));

        // Completing an already-COMPLETED trip must be rejected.
        assertThatThrownBy(() -> tripService.complete(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Illegal trip transition");
    }

    @Test
    void getById_unknownTrip_throwsIllegalArgumentException() {
        when(tripRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getById(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No trip 999");
    }
}
