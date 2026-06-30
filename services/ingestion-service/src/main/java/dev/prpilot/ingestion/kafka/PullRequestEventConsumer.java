package dev.prpilot.ingestion.kafka;

import dev.prpilot.ingestion.chunking.CodeChunker;
import dev.prpilot.ingestion.embedding.VectorFormat;
import dev.prpilot.ingestion.embedding.VoyageEmbeddingService;
import dev.prpilot.ingestion.git.RepoCloner;
import dev.prpilot.ingestion.model.CodeChunk;
import dev.prpilot.ingestion.model.PullRequestEvent;
import dev.prpilot.ingestion.repository.CodeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PullRequestEventConsumer {

    private final RepoCloner repoCloner;
    private final CodeChunker codeChunker;
    private final CodeChunkRepository codeChunkRepository;
    private final VoyageEmbeddingService embeddingService;

    @KafkaListener(topics = "${prpilot.kafka.topics.pr-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void onPullRequestEvent(PullRequestEvent event) {
        log.info("Consumed event: delivery={}, repo={}, pr=#{}",
                event.deliveryId(), event.repoFullName(), event.prNumber());

        Path repoDir = null;
        try {
            repoDir = repoCloner.clone(event.repoFullName(), event.headSha());

            List<CodeChunker.Chunk> chunks = codeChunker.chunkRepository(repoDir);

            List<CodeChunk> entities = chunks.stream()
                    .map(chunk -> CodeChunk.builder()
                            .repoFullName(event.repoFullName())
                            .filePath(chunk.filePath())
                            .chunkIndex(chunk.chunkIndex())
                            .content(chunk.content())
                            .headSha(event.headSha())
                            .build())
                    .toList();

            List<CodeChunk> saved = codeChunkRepository.saveAll(entities);
            log.info("Persisted {} chunks for {} @ {}",
                    saved.size(), event.repoFullName(), event.headSha());

            embedAndStore(saved);

        } catch (Exception e) {
            log.error("Failed to process event delivery={}", event.deliveryId(), e);
        } finally {
            if (repoDir != null) {
                repoCloner.cleanup(repoDir);
            }
        }
    }

    private void embedAndStore(List<CodeChunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }

        List<String> texts = chunks.stream().map(CodeChunk::getContent).toList();
        List<float[]> embeddings = embeddingService.embed(texts);

        for (int i = 0; i < chunks.size(); i++) {
            String literal = VectorFormat.toLiteral(embeddings.get(i));
            codeChunkRepository.updateEmbedding(chunks.get(i).getId(), literal);
        }

        log.info("Embedded and stored {} vectors", chunks.size());
    }
}