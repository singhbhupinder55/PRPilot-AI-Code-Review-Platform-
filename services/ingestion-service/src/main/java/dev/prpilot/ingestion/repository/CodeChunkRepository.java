package dev.prpilot.ingestion.repository;

import dev.prpilot.ingestion.model.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, UUID> {

    List<CodeChunk> findByRepoFullNameAndHeadSha(String repoFullName, String headSha);

    @Transactional
    @Modifying
    @Query(value = "UPDATE code_chunks SET embedding = CAST(:embedding AS vector) WHERE id = :id", nativeQuery = true)
    void updateEmbedding(@Param("id") UUID id, @Param("embedding") String embedding);
}