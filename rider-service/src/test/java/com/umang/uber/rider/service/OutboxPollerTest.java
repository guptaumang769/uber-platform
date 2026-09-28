package com.umang.uber.rider.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.rider.entity.OutboxEvent;
import com.umang.uber.rider.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPollerTest {

    @Mock
    private OutboxEventRepository outboxRepository;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    private OutboxPoller poller;

    @BeforeEach
    void setUp() {
        poller = new OutboxPoller(outboxRepository, kafkaTemplate, 50);
    }

    @Test
    void publishPending_sendsToKafka_andMarksPublished() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L).aggregateId("101").eventType("RideRequestedEvent")
                .payload("{\"rideRequestId\":101}").published(false)
                .createdAt(Instant.now()).build();
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc(any(PageRequest.class)))
                .thenReturn(List.of(event));

        poller.publishPending();

        verify(kafkaTemplate).send(eq(KafkaTopics.RIDE_REQUESTS), eq("101"), eq("{\"rideRequestId\":101}"));
        assertThat(event.isPublished()).isTrue();
        verify(outboxRepository).saveAll(List.of(event));
    }

    @Test
    void publishPending_noPending_doesNothing() {
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc(any(PageRequest.class)))
                .thenReturn(List.of());

        poller.publishPending();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxRepository, never()).saveAll(any());
    }

    @Test
    void publishPending_multipleEvents_publishesInOrder() {
        OutboxEvent e1 = OutboxEvent.builder()
                .id(1L).aggregateId("100").eventType("RideRequestedEvent")
                .payload("p1").published(false).createdAt(Instant.now()).build();
        OutboxEvent e2 = OutboxEvent.builder()
                .id(2L).aggregateId("101").eventType("RideRequestedEvent")
                .payload("p2").published(false).createdAt(Instant.now()).build();
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc(any(PageRequest.class)))
                .thenReturn(List.of(e1, e2));

        poller.publishPending();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, org.mockito.Mockito.times(2))
                .send(eq(KafkaTopics.RIDE_REQUESTS), keyCaptor.capture(), anyString());
        assertThat(keyCaptor.getAllValues()).containsExactly("100", "101");
        assertThat(e1.isPublished()).isTrue();
        assertThat(e2.isPublished()).isTrue();
    }
}
