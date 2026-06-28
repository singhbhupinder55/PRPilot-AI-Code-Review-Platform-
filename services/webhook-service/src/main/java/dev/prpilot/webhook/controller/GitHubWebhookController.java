package dev.prpilot.webhook.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.prpilot.webhook.kafka.PullRequestEventProducer;
import dev.prpilot.webhook.model.PullRequestEvent;
import dev.prpilot.webhook.security.HmacSignatureVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/webhooks/github")
@RequiredArgsConstructor
@Slf4j
public class GitHubWebhookController {

    private static final Set<String> RELEVANT_PR_ACTIONS =
            Set.of("opened", "reopened", "synchronize", "ready_for_review");

    private final HmacSignatureVerifier signatureVerifier;
    private final PullRequestEventProducer producer;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<String> receive(
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader("X-GitHub-Delivery") String deliveryId,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody) {

        log.info("Received event={} delivery={}", eventType, deliveryId);

        // 1. Verify signature
        if (!signatureVerifier.isValid(signature, rawBody)) {
            log.warn("Invalid signature for delivery={}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
        }

        // 2. Only care about pull_request events
        if (!"pull_request".equals(eventType)) {
            log.debug("Ignoring event type: {}", eventType);
            return ResponseEntity.ok("ignored");
        }

        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String action = payload.path("action").asText();

            // 3. Only care about actions that change PR content
            if (!RELEVANT_PR_ACTIONS.contains(action)) {
                log.debug("Ignoring PR action: {}", action);
                return ResponseEntity.ok("ignored");
            }

            // 4. Build the event
            JsonNode pr   = payload.path("pull_request");
            JsonNode repo = payload.path("repository");

            PullRequestEvent event = PullRequestEvent.builder()
                    .deliveryId(deliveryId)
                    .action(action)
                    .repoFullName(repo.path("full_name").asText())
                    .prNumber(pr.path("number").asLong())
                    .prTitle(pr.path("title").asText())
                    .prAuthor(pr.path("user").path("login").asText())
                    .headSha(pr.path("head").path("sha").asText())
                    .baseBranch(pr.path("base").path("ref").asText())
                    .headBranch(pr.path("head").path("ref").asText())
                    .htmlUrl(pr.path("html_url").asText())
                    .receivedAt(Instant.now())
                    .build();

            // 5. Publish to Kafka (fire-and-forget, but we log the result async)
            producer.publish(event);

            return ResponseEntity.accepted().body("queued");
        } catch (Exception e) {
            log.error("Failed to process delivery={}", deliveryId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error");
        }
    }
}