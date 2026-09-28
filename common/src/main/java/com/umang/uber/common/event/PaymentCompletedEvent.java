package com.umang.uber.common.event;

import java.math.BigDecimal;

/** Emitted by payment-service once a trip's fare has been captured. */
public record PaymentCompletedEvent(
        Long tripId,
        BigDecimal amount) {
}
