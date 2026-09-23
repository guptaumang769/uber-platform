package com.umang.uber.matching.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.umang.uber.common.event.RideRequestedEvent;
import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.matching.service.MatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes ride-requests and drives the matching algorithm for each. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RideRequestConsumer {

    private final MatchingService matchingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.RIDE_REQUESTS, groupId = "matching-service",
            containerFactory = "stringListenerFactory")
    public void onRideRequested(String payload) throws Exception {
        RideRequestedEvent event = objectMapper.readValue(payload, RideRequestedEvent.class);
        matchingService.match(event);
    }
}
