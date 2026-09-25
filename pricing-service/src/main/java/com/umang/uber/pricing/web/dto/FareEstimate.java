package com.umang.uber.pricing.web.dto;

import java.math.BigDecimal;

public record FareEstimate(BigDecimal distanceKm, BigDecimal surgeMultiplier, BigDecimal fare) {
}
