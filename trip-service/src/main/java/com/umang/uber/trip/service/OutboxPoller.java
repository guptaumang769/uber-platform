package com.umang.uber.trip.service;

import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.trip.entity.OutboxEvent;
import com.umang.uber.trip.repository.OutboxEventRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays unpublished TripStatusEvent outbox rows to Kafka. Same at-least-once guarantee as the
 * other services: the status change and the event were committed atomically to the DB, so the
 * payment saga (COMPLETED) can never be lost even if the broker was briefly unreachable.
 */
@Slf4j
@Component
public class OutboxPoller {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int batchSize;

    public OutboxPoller(OutboxEventRepository outboxRepository,
                        KafkaTemplate<String, String> kafkaTemplate,
                        @Value("${uber.outbox.batch-size:50}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${uber.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending =
                outboxRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize));
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            // Trip status events go on the trip-events topic, keyed by tripId for partition order.
            kafkaTemplate.send(KafkaTopics.TRIP_EVENTS, event.getAggregateId(), event.getPayload());
            event.setPublished(true);
        }
        outboxRepository.saveAll(pending);
        log.info("Published {} trip outbox event(s) to Kafka", pending.size());
    }
}
