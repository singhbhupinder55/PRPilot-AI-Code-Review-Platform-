package dev.prpilot.ingestion.chunking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Splits source files into fixed-size, line-based chunks.
 *
 * This is a simple baseline strategy (split every N lines). More advanced
 * approaches (splitting by function/class boundaries using a language parser)
 * are a future improvement — noted as a v2 idea.
 */
@Component
@Slf4j
public class CodeChunker {

    private static final int LINES_PER_CHUNK = 60;
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".go", ".rb", ".md"
    );
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "build", "target", "dist", ".gradle"
    );

    public record Chunk(String filePath, int chunkIndex, String content) {}

    public List<Chunk> chunkRepository(Path repoDir) {
        List<Chunk> chunks = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(repoDir)) {
            List<Path> sourceFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .filter(this::isNotIgnored)
                    .toList();

            log.info("Found {} source files to chunk", sourceFiles.size());

            for (Path file : sourceFiles) {
                chunks.addAll(chunkFile(repoDir, file));
            }
        } catch (IOException e) {
            log.error("Failed to walk repository directory: {}", repoDir, e);
        }

        log.info("Generated {} chunks total", chunks.size());
        return chunks;
    }

    private List<Chunk> chunkFile(Path repoDir, Path file) {
        List<Chunk> fileChunks = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relativePath = repoDir.relativize(file).toString();

            int chunkIndex = 0;
            for (int start = 0; start < lines.size(); start += LINES_PER_CHUNK) {
                int end = Math.min(start + LINES_PER_CHUNK, lines.size());
                String content = String.join("\n", lines.subList(start, end));

                if (!content.isBlank()) {
                    fileChunks.add(new Chunk(relativePath, chunkIndex++, content));
                }
            }
        } catch (IOException e) {
            log.warn("Skipping unreadable file: {}", file, e);
        }
        return fileChunks;
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private boolean isNotIgnored(Path path) {
        return IGNORED_DIRS.stream().noneMatch(ignored ->
                path.toString().contains("/" + ignored + "/"));
    }
}