package dev.prpilot.ingestion.repository;

import dev.prpilot.ingestion.model.CodeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CodeChunkRepository extends JpaRepository<CodeChunk, UUID> {

    List<CodeChunk> findByRepoFullNameAndHeadSha(String repoFullName, String headSha);
}