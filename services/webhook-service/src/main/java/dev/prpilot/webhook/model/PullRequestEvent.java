package dev.prpilot.webhook.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;

/**
 * The event we publish to Kafka after receiving a GitHub PR webhook.
 * Downstream services (ingestion, review) consume this.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestEvent(
        String deliveryId,      // GitHub's unique webhook delivery ID (idempotency key)
        String action,          // "opened", "synchronize", "closed", etc.
        String repoFullName,    // e.g. "singhbhupinder55/prpilot"
        Long   prNumber,        // PR number, e.g. 42
        String prTitle,
        String prAuthor,
        String headSha,         // commit SHA of the PR head
        String baseBranch,      // e.g. "main"
        String headBranch,      // e.g. "feature/login"
        String htmlUrl,         // PR URL on GitHub
        Instant receivedAt
) {}