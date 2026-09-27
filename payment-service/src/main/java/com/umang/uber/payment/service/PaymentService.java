package com.umang.uber.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.PaymentCompletedEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.payment.entity.Payment;
import com.umang.uber.payment.repository.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures the fare for a completed trip.
 *
 * <p>IDEMPOTENCY (reused from the UPI pattern): the capture is keyed by tripId, which is UNIQUE in
 * the DB. If the same trip-completed event is redelivered we short-circuit and return the existing
 * payment — the rider is charged exactly once no matter how many times the event arrives. The
 * PaymentCompletedEvent is only published on the FIRST capture, so downstream consumers also see
 * it once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public Payment capture(Long tripId, BigDecimal amount) {
        // Replay short-circuit: same tripId already captured -> return the stored payment.
        var existing = paymentRepository.findByTripId(tripId);
        if (existing.isPresent()) {
            log.info("Idempotent replay for trip {} -> returning existing payment", tripId);
            return existing.get();
        }

        Payment payment = paymentRepository.save(Payment.builder()
                .tripId(tripId)
                .amount(amount != null ? amount : BigDecimal.ZERO)
                .capturedAt(Instant.now())
                .build());

        // Publish only on first capture so PaymentCompletedEvent is emitted exactly once per trip.
        PaymentCompletedEvent event = new PaymentCompletedEvent(tripId, payment.getAmount());
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, String.valueOf(tripId), serialize(event));

        log.info("Captured fare {} for trip {} and published PaymentCompletedEvent",
                payment.getAmount(), tripId);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment getByTripId(Long tripId) {
        return paymentRepository.findByTripId(tripId)
                .orElseThrow(() -> new IllegalArgumentException("No payment for trip " + tripId));
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }
    }
}
