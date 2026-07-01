package dev.prpilot.review.retrieval;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Retrieves the most semantically similar code chunks from pgvector
 * for a given query embedding. This is the retrieval step in RAG.
 *
 * Uses native SQL with pgvector's <=> cosine distance operator directly
 * via JdbcTemplate — cleaner than a JPA native query for this use case
 * since we're passing a raw vector literal.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagRetrievalService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${prpilot.retrieval.top-k}")
    private int topK;

    /**
     * Finds the top-K most similar chunks to the query embedding,
     * filtered to the specific repo being reviewed.
     */
    public List<String> retrieveRelevantChunks(
            String repoFullName,
            String queryEmbeddingLiteral) {

        log.debug("Retrieving top-{} chunks for repo={}", topK, repoFullName);

        String sql = """
                SELECT content
                FROM code_chunks
                WHERE repo_full_name = ?
                  AND embedding IS NOT NULL
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT ?
                """;

        List<String> chunks = jdbcTemplate.queryForList(
                sql,
                String.class,
                repoFullName,
                queryEmbeddingLiteral,
                topK);

        log.info("Retrieved {} relevant chunks for repo={}", chunks.size(), repoFullName);
        return chunks;
    }
}