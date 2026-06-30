package dev.prpilot.ingestion.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Mirrors the event published by webhook-service to the pr.events topic.
 * Field names must match exactly for Jackson to deserialize correctly.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestEvent(
        String deliveryId,
        String action,
        String repoFullName,
        Long prNumber,
        String prTitle,
        String prAuthor,
        String headSha,
        String baseBranch,
        String headBranch,
        String htmlUrl,
        Instant receivedAt
) {}