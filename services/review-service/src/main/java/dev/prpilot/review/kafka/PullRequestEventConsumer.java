package dev.prpilot.review.kafka;

import dev.prpilot.review.claude.ClaudeReviewService;
import dev.prpilot.review.embedding.VoyageEmbeddingService;
import dev.prpilot.review.model.PullRequestEvent;
import dev.prpilot.review.model.Review;
import dev.prpilot.review.repository.ReviewRepository;
import dev.prpilot.review.retrieval.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PullRequestEventConsumer {

    private final VoyageEmbeddingService embeddingService;
    private final RagRetrievalService ragRetrievalService;
    private final ClaudeReviewService claudeReviewService;
    private final ReviewRepository reviewRepository;

    @Value("${prpilot.anthropic.model}")
    private String modelUsed;

    @KafkaListener(
            topics = "${prpilot.kafka.topics.pr-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPullRequestEvent(PullRequestEvent event) {
        log.info("Consumed event: delivery={}, repo={}, pr=#{}",
                event.deliveryId(), event.repoFullName(), event.prNumber());

        // Idempotency check — don't re-review the same delivery
        if (reviewRepository.findByDeliveryId(event.deliveryId()).isPresent()) {
            log.info("Skipping duplicate delivery={}", event.deliveryId());
            return;
        }

        // Create a PENDING review record immediately
        Review review = Review.builder()
                .repoFullName(event.repoFullName())
                .prNumber(event.prNumber())
                .headSha(event.headSha())
                .deliveryId(event.deliveryId())
                .status("PENDING")
                .build();
        reviewRepository.save(review);

        try {
            // 1. Build a query text from PR metadata
            String queryText = buildQueryText(event);

            // 2. Embed the query (using "query" input_type for better retrieval)
            String queryEmbedding = embeddingService.embedQuery(queryText);

            // 3. Retrieve relevant code chunks via pgvector similarity search
            List<String> relevantChunks = ragRetrievalService
                    .retrieveRelevantChunks(event.repoFullName(), queryEmbedding);

            // 4. Call Claude with the context and generate review
            String reviewBody = claudeReviewService.generateReview(
                    event.prTitle(),
                    event.prAuthor(),
                    event.repoFullName(),
                    event.headSha(),
                    relevantChunks);

            // 5. Persist the completed review
            review.setStatus("COMPLETED");
            review.setReviewBody(reviewBody);
            review.setModelUsed(modelUsed);
            review.setChunksUsed(relevantChunks.size());
            review.setCompletedAt(Instant.now());
            reviewRepository.save(review);

            log.info("Review completed for delivery={}, chunks={}, model={}",
                    event.deliveryId(), relevantChunks.size(), modelUsed);

        } catch (Exception e) {
            review.setStatus("FAILED");
            reviewRepository.save(review);
            log.error("Review failed for delivery={}", event.deliveryId(), e);
        }
    }

    private String buildQueryText(PullRequestEvent event) {
        return """
                Pull request: %s
                Repository: %s
                Author: %s
                Branch: %s -> %s
                """.formatted(
                event.prTitle(),
                event.repoFullName(),
                event.prAuthor(),
                event.headBranch(),
                event.baseBranch());
    }
}