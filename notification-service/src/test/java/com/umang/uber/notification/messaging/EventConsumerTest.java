package com.umang.uber.notification.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.notification.service.TripStreamRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private TripStreamRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private EventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new EventConsumer(registry, objectMapper);
    }

    @Test
    void onTripEvent_statusEvent_publishesToCorrectTrip() throws Exception {
        String payload = """
                {"tripId":55,"status":"ONGOING","driverId":10,"fare":120.50}""";

        consumer.onTripEvent(payload);

        verify(registry).publish(eq(55L), eq("status"), any());
    }

    @Test
    void onTripEvent_tripMatchedEvent_isIgnored() throws Exception {
        // TripMatchedEvent has rideRequestId but no status — should NOT publish.
        String payload = """
                {"rideRequestId":101,"driverId":10,"riderId":7,\
                "pickupLat":12.97,"pickupLng":77.59,"dropLat":12.93,"dropLng":77.62}""";

        consumer.onTripEvent(payload);

        verify(registry, never()).publish(any(), any(), any());
    }

    @Test
    void onDriverLocation_broadcastsToAllStreams() throws Exception {
        String payload = """
                {"driverId":42,"lat":12.971,"lng":77.594,"epoch":1700000000}""";

        consumer.onDriverLocation(payload);

        verify(registry).broadcast(eq("location"), any());
    }
}
