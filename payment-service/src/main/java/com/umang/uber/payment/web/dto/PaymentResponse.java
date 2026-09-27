package com.umang.uber.payment.web.dto;

import com.umang.uber.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(Long id, Long tripId, BigDecimal amount, Instant capturedAt) {

    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getId(), p.getTripId(), p.getAmount(), p.getCapturedAt());
    }
}
