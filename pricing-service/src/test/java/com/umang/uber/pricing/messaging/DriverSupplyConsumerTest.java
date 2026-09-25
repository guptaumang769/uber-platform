package com.umang.uber.pricing.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.pricing.service.SurgeTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverSupplyConsumerTest {

    @Mock
    private SurgeTracker surgeTracker;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private DriverSupplyConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DriverSupplyConsumer(surgeTracker, objectMapper);
    }

    @Test
    void onDriverLocation_callsRecordSupplyWithCorrectCoordinates() throws Exception {
        String payload = """
                {"driverId":42,"lat":12.97,"lng":77.59,"timestampMs":1000000}
                """;

        consumer.onDriverLocation(payload);

        // Each driver ping must bump the supply counter for that geo cell so surge multiplier
        // has a real denominator instead of always reading 0.
        verify(surgeTracker).recordSupply(12.97, 77.59);
    }

    @Test
    void onDriverLocation_malformedJson_throws() {
        assertThatThrownBy(() -> consumer.onDriverLocation("not-json"))
                .isInstanceOf(Exception.class);
    }
}
