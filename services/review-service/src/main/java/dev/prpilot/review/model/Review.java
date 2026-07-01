package dev.prpilot.review.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    @Column(name = "pr_number", nullable = false)
    private Long prNumber;

    @Column(name = "head_sha", nullable = false)
    private String headSha;

    @Column(name = "delivery_id", nullable = false, unique = true)
    private String deliveryId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "review_body", columnDefinition = "TEXT")
    private String reviewBody;

    @Column(name = "model_used")
    private String modelUsed;

    @Column(name = "chunks_used")
    private Integer chunksUsed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}