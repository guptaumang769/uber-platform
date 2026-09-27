package com.umang.uber.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.payment.entity.Payment;
import com.umang.uber.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, kafkaTemplate, objectMapper);
    }

    @Test
    void capture_persistsPayment_andPublishesEvent() {
        when(paymentRepository.findByTripId(55L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        Payment result = paymentService.capture(55L, new BigDecimal("142.50"));

        assertThat(result.getTripId()).isEqualTo(55L);
        assertThat(result.getAmount()).isEqualByComparingTo("142.50");
        // First capture publishes exactly one PaymentCompletedEvent.
        verify(kafkaTemplate).send(eq(KafkaTopics.PAYMENT_EVENTS), eq("55"), anyString());
    }

    @Test
    void capture_isIdempotent_sameTripTwice_isOnePayment() {
        Payment existing = Payment.builder()
                .id(1L).tripId(55L).amount(new BigDecimal("142.50")).capturedAt(Instant.now()).build();
        // Second delivery: the tripId already has a payment.
        when(paymentRepository.findByTripId(55L)).thenReturn(Optional.of(existing));

        Payment result = paymentService.capture(55L, new BigDecimal("142.50"));

        assertThat(result.getId()).isEqualTo(1L);
        // No second insert and NO second event on the replayed delivery.
        verify(paymentRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void capture_nullFare_storesZero() {
        // TripStatusEvent may carry null fare if pricing-service was down when the trip was created
        // and the fare was never updated. The service must not NPE; it falls back to ZERO.
        when(paymentRepository.findByTripId(77L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.capture(77L, null);

        assertThat(result.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void capture_twoDistinctTrips_areTwoPayments() {
        when(paymentRepository.findByTripId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.findByTripId(2L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.capture(1L, BigDecimal.TEN);
        paymentService.capture(2L, BigDecimal.TEN);

        verify(paymentRepository, times(2)).save(any(Payment.class));
    }
}
