package com.umang.uber.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.RideRequestedEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.matching.client.LocationClient.NearbyDriver;
import com.umang.uber.matching.client.LocationGateway;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private LocationGateway locationGateway;
    @Mock
    private DriverLock driverLock;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MatchingService matchingService;

    private static RideRequestedEvent request(long id) {
        return new RideRequestedEvent(id, 1L, 12.97, 77.59, 12.93, 77.62, 0L);
    }

    @BeforeEach
    void setUp() {
        matchingService = new MatchingService(locationGateway, driverLock, kafkaTemplate, objectMapper);
        ReflectionTestUtils.setField(matchingService, "searchRadiusKm", 5.0);
    }

    @Test
    void picksNextNearest_whenClosestDriverIsAlreadyTaken() {
        // Nearest-first: driver 10 (0.2km) then driver 20 (0.5km).
        when(locationGateway.nearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new NearbyDriver(10L, 0.2), new NearbyDriver(20L, 0.5)));
        // Driver 10 is already taken (lock fails); driver 20 is free.
        when(driverLock.tryLock(eq(10L), eq(1L))).thenReturn(false);
        when(driverLock.tryLock(eq(20L), eq(1L))).thenReturn(true);

        Long matched = matchingService.match(request(1L));

        // Must skip the taken nearest and pick the next-nearest.
        assertThat(matched).isEqualTo(20L);
    }

    @Test
    void returnsNull_whenNoCandidates() {
        when(locationGateway.nearby(anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of());
        assertThat(matchingService.match(request(1L))).isNull();
    }

    @Test
    void returnsNull_whenAllCandidatesTaken() {
        when(locationGateway.nearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new NearbyDriver(10L, 0.2), new NearbyDriver(20L, 0.5)));
        when(driverLock.tryLock(eq(10L), eq(1L))).thenReturn(false);
        when(driverLock.tryLock(eq(20L), eq(1L))).thenReturn(false);
        assertThat(matchingService.match(request(1L))).isNull();
    }

    @Test
    void publishedTripMatchedEvent_includesPickupAndDropCoordinates() {
        // Coordinates must flow from RideRequestedEvent → TripMatchedEvent → Kafka so that
        // trip-service can request a real fare from pricing-service. Before the fix the event
        // was created with hardcoded (0,0,0,0) making all fares wrong.
        when(locationGateway.nearby(12.97, 77.59, 5.0))
                .thenReturn(List.of(new NearbyDriver(10L, 0.2)));
        when(driverLock.tryLock(10L, 1L)).thenReturn(true);

        matchingService.match(request(1L));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq(KafkaTopics.TRIP_EVENTS),
                org.mockito.ArgumentMatchers.anyString(),
                payloadCaptor.capture());
        String json = payloadCaptor.getValue();
        assertThat(json).contains("12.97");   // pickupLat
        assertThat(json).contains("77.59");   // pickupLng
        assertThat(json).contains("12.93");   // dropLat
        assertThat(json).contains("77.62");   // dropLng
    }

    @Test
    void twoConcurrentMatches_doNotDoubleAssignTheSameDriver() throws Exception {
        // Both ride requests see the SAME single candidate driver (id 10).
        lenient().when(locationGateway.nearby(anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(new NearbyDriver(10L, 0.3)));

        // Simulate Redis SETNX: only the FIRST tryLock for a given driver wins, all others lose.
        ConcurrentHashMap<Long, Boolean> claimed = new ConcurrentHashMap<>();
        lenient().when(driverLock.tryLock(eq(10L), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> claimed.putIfAbsent(10L, Boolean.TRUE) == null);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Long> a = pool.submit(() -> matchingService.match(request(1L)));
        Future<Long> b = pool.submit(() -> matchingService.match(request(2L)));
        Long ra = a.get();
        Long rb = b.get();
        pool.shutdown();

        // Exactly one request wins driver 10; the other gets no match (null).
        AtomicInteger winners = new AtomicInteger();
        if (Long.valueOf(10L).equals(ra)) {
            winners.incrementAndGet();
        }
        if (Long.valueOf(10L).equals(rb)) {
            winners.incrementAndGet();
        }
        assertThat(winners.get()).isEqualTo(1);
    }
}
