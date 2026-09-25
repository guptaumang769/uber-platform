package com.umang.uber.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SurgeCalculatorTest {

    private static final double CAP = 3.0;

    @Test
    void noSurge_whenSupplyMeetsDemand() {
        // supply >= demand -> exactly 1.0 (no surge).
        assertThat(SurgeCalculator.multiplier(5, 5, CAP)).isEqualTo(1.0);
        assertThat(SurgeCalculator.multiplier(3, 10, CAP)).isEqualTo(1.0);
    }

    @Test
    void surgeRises_asDemandOutstripsSupply() {
        double low = SurgeCalculator.multiplier(6, 5, CAP);   // slight excess
        double mid = SurgeCalculator.multiplier(10, 5, CAP);  // larger excess
        assertThat(low).isGreaterThan(1.0);
        assertThat(mid).isGreaterThan(low);
    }

    @Test
    void surgeIsCapped() {
        // Huge demand, tiny supply would blow past the cap without clamping.
        assertThat(SurgeCalculator.multiplier(1000, 1, CAP)).isEqualTo(CAP);
    }

    @Test
    void handlesZeroSupply_withoutDivideByZero() {
        double m = SurgeCalculator.multiplier(4, 0, CAP);
        assertThat(m).isGreaterThan(1.0).isLessThanOrEqualTo(CAP);
    }

    @Test
    void fareIsDistanceTimesRateTimesSurge() {
        // Demonstrates the composition used by PricingService: base + dist*rate, then *surge.
        double base = 30.0;
        double perKm = 12.0;
        double distance = 5.0;
        double surge = SurgeCalculator.multiplier(8, 4, CAP); // 1 + 4/4 = 2.0
        assertThat(surge).isEqualTo(2.0);
        double fare = (base + distance * perKm) * surge;
        assertThat(fare).isEqualTo((30.0 + 60.0) * 2.0);
    }
}
