package dev.prpilot.ingestion.kafka;

import dev.prpilot.ingestion.chunking.CodeChunker;
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

            codeChunkRepository.saveAll(entities);

            log.info("Persisted {} chunks for {} @ {}",
                    entities.size(), event.repoFullName(), event.headSha());

        } catch (Exception e) {
            log.error("Failed to process event delivery={}", event.deliveryId(), e);
            // TODO: dead-letter queue for failed events — v2 improvement
        } finally {
            if (repoDir != null) {
                repoCloner.cleanup(repoDir);
            }
        }
    }
}