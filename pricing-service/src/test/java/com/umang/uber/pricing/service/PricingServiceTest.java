package com.umang.uber.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umang.uber.pricing.web.dto.FareEstimate;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private SurgeTracker surgeTracker;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        pricingService = new PricingService(surgeTracker);
        ReflectionTestUtils.setField(pricingService, "baseFare", 30.0);
        ReflectionTestUtils.setField(pricingService, "perKmRate", 12.0);
    }

    @Test
    void estimate_noSurge_returnBasePlusDistanceTimesRate() {
        when(surgeTracker.surgeFor(anyDouble(), anyDouble())).thenReturn(1.0);

        // ~5.5 km trip, no surge → fare = (30 + 5.5*12) * 1.0 = 96.00
        FareEstimate est = pricingService.estimate(12.97, 77.59, 12.93, 77.62);

        assertThat(est.fare()).isGreaterThan(BigDecimal.ZERO);
        assertThat(est.surgeMultiplier()).isEqualByComparingTo("1.00");
    }

    @Test
    void estimate_withSurge_fareScalesByMultiplier() {
        when(surgeTracker.surgeFor(anyDouble(), anyDouble())).thenReturn(1.0).thenReturn(2.0);

        FareEstimate noSurge = pricingService.estimate(12.97, 77.59, 12.93, 77.62);
        FareEstimate doubleSurge = pricingService.estimate(12.97, 77.59, 12.93, 77.62);

        assertThat(doubleSurge.fare()).isGreaterThan(noSurge.fare());
        BigDecimal ratio = doubleSurge.fare().divide(noSurge.fare(), 2, java.math.RoundingMode.HALF_UP);
        assertThat(ratio).isEqualByComparingTo("2.00");
    }

    @Test
    void estimate_recordsDemandForPickupLocation() {
        when(surgeTracker.surgeFor(anyDouble(), anyDouble())).thenReturn(1.0);

        pricingService.estimate(12.97, 77.59, 12.93, 77.62);

        verify(surgeTracker).recordDemand(12.97, 77.59);
    }

    @Test
    void estimate_zeroDistanceTrip_returnsBaseFareOnly() {
        when(surgeTracker.surgeFor(anyDouble(), anyDouble())).thenReturn(1.0);

        FareEstimate est = pricingService.estimate(12.97, 77.59, 12.97, 77.59);

        assertThat(est.distanceKm()).isEqualByComparingTo("0.00");
        assertThat(est.fare()).isEqualByComparingTo("30.00");
    }
}
