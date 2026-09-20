package com.umang.uber.rider.service;

import com.umang.uber.common.messaging.KafkaTopics;
import com.umang.uber.rider.entity.OutboxEvent;
import com.umang.uber.rider.repository.OutboxEventRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Relays unpublished outbox rows to Kafka.
 *
 * <p>WHY a poller instead of publishing inline: publishing to Kafka inside the DB transaction is a
 * dual write — if the DB commits but the broker send fails (or the process dies between them) the
 * ride request is lost; if the send succeeds but the DB rolls back we emit a phantom ride. Writing
 * the event to the DB atomically with the ride request and letting this poller publish gives
 * at-least-once delivery. matching-service dedupes by rideRequestId so duplicates are safe.
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
            // Key by rideRequestId so all events for one request keep partition order.
            kafkaTemplate.send(KafkaTopics.RIDE_REQUESTS, event.getAggregateId(), event.getPayload());
            event.setPublished(true);
        }
        outboxRepository.saveAll(pending);
        log.info("Published {} ride-request outbox event(s) to Kafka", pending.size());
    }
}
