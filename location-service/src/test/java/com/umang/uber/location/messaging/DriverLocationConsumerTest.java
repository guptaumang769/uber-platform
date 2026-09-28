package com.umang.uber.location.messaging;

import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.location.repository.DriverGeoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DriverLocationConsumerTest {

    @Mock
    private DriverGeoRepository geoRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private DriverLocationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DriverLocationConsumer(geoRepository, objectMapper);
    }

    @Test
    void onLocation_deserializesEvent_andUpsertsToGeoRepository() throws Exception {
        String payload = """
                {"driverId":42,"lat":12.971,"lng":77.594,"epoch":1700000000}""";

        consumer.onLocation(payload);

        verify(geoRepository).upsert(42L, 12.971, 77.594);
    }

    @Test
    void onLocation_handlesNegativeCoordinates() throws Exception {
        String payload = """
                {"driverId":7,"lat":-33.868,"lng":151.209,"epoch":1700000001}""";

        consumer.onLocation(payload);

        verify(geoRepository).upsert(7L, -33.868, 151.209);
    }

    @Test
    void onLocation_twoConsecutivePings_eachUpserted() throws Exception {
        String ping1 = """
                {"driverId":10,"lat":12.97,"lng":77.59,"epoch":1700000000}""";
        String ping2 = """
                {"driverId":10,"lat":12.98,"lng":77.60,"epoch":1700000005}""";

        consumer.onLocation(ping1);
        consumer.onLocation(ping2);

        verify(geoRepository).upsert(10L, 12.97, 77.59);
        verify(geoRepository).upsert(10L, 12.98, 77.60);
    }
}
