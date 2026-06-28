package dev.prpilot.webhook.kafka;

import dev.prpilot.webhook.model.PullRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes PR events to Kafka. Key = repoFullName, so all events
 * for a given repo land on the same partition (ordering guarantee per repo).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PullRequestEventProducer {

    private final KafkaTemplate<String, PullRequestEvent> kafkaTemplate;

    @Value("${prpilot.kafka.topics.pr-events}")
    private String topic;

    public CompletableFuture<Void> publish(PullRequestEvent event) {
        log.debug("Publishing event to {}: delivery={}, repo={}, pr=#{}",
                topic, event.deliveryId(), event.repoFullName(), event.prNumber());

        return kafkaTemplate.send(topic, event.repoFullName(), event)
                .thenAccept(result -> log.info(
                        "Published delivery={} to {}-{} at offset {}",
                        event.deliveryId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset()))
                .exceptionally(ex -> {
                    log.error("Failed to publish delivery={}", event.deliveryId(), ex);
                    throw new RuntimeException(ex);
                });
    }
}