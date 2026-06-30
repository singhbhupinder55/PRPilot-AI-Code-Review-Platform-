package dev.prpilot.ingestion.chunking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodeChunkerTest {

    private final CodeChunker chunker = new CodeChunker();

    @Test
    @DisplayName("chunks a single small file into one chunk")
    void smallFileProducesOneChunk(@TempDir Path repoDir) throws IOException {
        writeFile(repoDir, "Hello.java", "public class Hello {\n    // small file\n}\n");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).filePath()).isEqualTo("Hello.java");
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).content()).contains("public class Hello");
    }

    @Test
    @DisplayName("splits a large file into multiple chunks")
    void largeFileProducesMultipleChunks(@TempDir Path repoDir) throws IOException {
        String content = "// line\n".repeat(150); // 150 lines, well over the 60-line window
        writeFile(repoDir, "Big.java", content);

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        // 150 lines / 60 per chunk = 3 chunks (60 + 60 + 30)
        assertThat(chunks).hasSize(3);
        assertThat(chunks).extracting(CodeChunker.Chunk::chunkIndex)
                .containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("ignores unsupported file extensions")
    void ignoresUnsupportedExtensions(@TempDir Path repoDir) throws IOException {
        writeFile(repoDir, "image.png", "not real image data, just text for the test");
        writeFile(repoDir, "Notes.txt", "plain text notes");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("ignores files inside vendor/build directories")
    void ignoresVendorDirectories(@TempDir Path repoDir) throws IOException {
        writeFile(repoDir, "src/Main.java", "public class Main {}\n");
        writeFile(repoDir, "node_modules/lib/Thing.js", "module.exports = {};\n");
        writeFile(repoDir, "build/Generated.java", "public class Generated {}\n");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).filePath()).isEqualTo("src/Main.java");
    }

    @Test
    @DisplayName("returns empty list for empty repository")
    void emptyRepoProducesNoChunks(@TempDir Path repoDir) {
        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("empty file produces zero chunks, not a blank chunk")
    void emptyFileProducesNoChunks(@TempDir Path repoDir) throws IOException {
        writeFile(repoDir, "Empty.java", "");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("file with exactly the chunk-size line count produces exactly one chunk")
    void exactBoundaryProducesOneChunk(@TempDir Path repoDir) throws IOException {
        // 60 lines exactly matches LINES_PER_CHUNK - this is the classic off-by-one risk
        String content = "// line\n".repeat(60);
        writeFile(repoDir, "Exact.java", content);

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
    }

    @Test
    @DisplayName("file with one more line than the chunk size spills into a second chunk")
    void oneOverBoundaryProducesTwoChunks(@TempDir Path repoDir) throws IOException {
        // 61 lines - one over the boundary, must produce a second (1-line) chunk
        String content = "// line\n".repeat(61);
        writeFile(repoDir, "OneOver.java", content);

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).hasSize(2);
        assertThat(chunks).extracting(CodeChunker.Chunk::chunkIndex)
                .containsExactly(0, 1);
    }

    @Test
    @DisplayName("an unreadable file is skipped without aborting the rest of the run")
    void unreadableFileDoesNotAbortOtherFiles(@TempDir Path repoDir) throws IOException {
        // Write a file containing invalid UTF-8 bytes directly, bypassing String-based writes
        Path badFile = repoDir.resolve("Corrupt.java");
        Files.write(badFile, new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0x00, (byte) 0x01});

        writeFile(repoDir, "Good.java", "public class Good {}\n");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        // The good file should still be chunked even though the corrupt one failed
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).filePath()).isEqualTo("Good.java");
    }

    @Test
    @DisplayName("ignores vendor directories even when nested, not just at repo root")
    void ignoresNestedVendorDirectories(@TempDir Path repoDir) throws IOException {
        writeFile(repoDir, "src/Main.java", "public class Main {}\n");
        writeFile(repoDir, "src/vendor/node_modules/lib/Thing.js", "module.exports = {};\n");
        writeFile(repoDir, "frontend/build/Generated.java", "public class Generated {}\n");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).filePath()).isEqualTo("src/Main.java");
    }

    @Test
    @DisplayName("ignores files with no extension, like Makefile or Dockerfile")
    void ignoresFilesWithNoExtension(@TempDir Path repoDir) throws IOException {
        writeFile(repoDir, "Makefile", "build:\n\tjavac Main.java\n");
        writeFile(repoDir, "Dockerfile", "FROM eclipse-temurin:21\n");
        writeFile(repoDir, "Main.java", "public class Main {}\n");

        List<CodeChunker.Chunk> chunks = chunker.chunkRepository(repoDir);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).filePath()).isEqualTo("Main.java");
    }


    private void writeFile(Path repoDir, String relativePath, String content) throws IOException {
        Path file = repoDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}