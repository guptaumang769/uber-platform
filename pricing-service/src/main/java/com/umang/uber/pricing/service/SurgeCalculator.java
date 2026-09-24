package com.umang.uber.pricing.service;

/**
 * Pure surge math, split out from the Redis-backed tracker so it is trivially unit-testable.
 *
 * <p>SURGE MODEL: per geo-cell we track recent ride-request count (demand) and available-driver
 * count (supply) over a sliding window. The multiplier is {@code 1 + max(0, demand - supply) /
 * max(1, supply)} — i.e. surge only kicks in once demand OUTSTRIPS supply, and it grows in
 * proportion to how far ahead demand is per available driver. It is clamped to {@code [1, cap]}
 * so a spike can never produce an unbounded fare. When supply >= demand the multiplier is exactly
 * 1.0 (no surge).
 */
public final class SurgeCalculator {

    private SurgeCalculator() {
    }

    public static double multiplier(long demand, long supply, double cap) {
        long effectiveSupply = Math.max(1, supply);
        long excessDemand = Math.max(0, demand - supply);
        double surge = 1.0 + ((double) excessDemand / effectiveSupply);
        return Math.min(surge, cap);
    }
}
