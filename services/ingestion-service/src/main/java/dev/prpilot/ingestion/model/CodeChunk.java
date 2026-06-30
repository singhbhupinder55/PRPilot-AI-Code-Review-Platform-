package dev.prpilot.ingestion.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A chunk of source code with its vector embedding.
 * review-service will query these via similarity search to find
 * relevant context when reviewing a PR (the "R" in RAG).
 */
@Entity
@Table(name = "code_chunks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeChunk {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "repo_full_name", nullable = false)
    private String repoFullName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // Embedding is intentionally NOT mapped here yet — pgvector's `vector` type
    // needs a custom Hibernate type (we'll add this when we wire up real embeddings
    // next session). For now, ingestion writes chunks without embeddings via native SQL.

    @Column(name = "head_sha", nullable = false)
    private String headSha;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}