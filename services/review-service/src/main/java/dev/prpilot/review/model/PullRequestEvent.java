package dev.prpilot.review.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

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