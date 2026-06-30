package dev.prpilot.ingestion.git;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Clones a GitHub repo to a temp directory for processing.
 * Uses a shallow clone (depth=1) since we only need the current state
 * of the code, not full history — much faster and lighter on disk.
 */
@Component
@Slf4j
public class RepoCloner {

    public Path clone(String repoFullName, String headSha) {
        try {
            Path tempDir = Files.createTempDirectory("prpilot-clone-");
            String cloneUrl = "https://github.com/" + repoFullName + ".git";

            log.info("Cloning {} (sha={}) into {}", repoFullName, headSha, tempDir);

            try (Git git = Git.cloneRepository()
                    .setURI(cloneUrl)
                    .setDirectory(tempDir.toFile())
                    .setDepth(1)
                    .call()) {
                log.info("Clone complete: {}", repoFullName);
            }

            return tempDir;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone repo: " + repoFullName, e);
        }
    }

    /**
     * Recursively deletes the temp clone directory.
     * Critical to call this after processing, or we leak disk space
     * with every PR event.
     */
    public void cleanup(Path repoDir) {
        try {
            Files.walk(repoDir)
                    .sorted((a, b) -> b.compareTo(a)) // delete children before parents
                    .map(Path::toFile)
                    .forEach(File::delete);
            log.debug("Cleaned up temp clone: {}", repoDir);
        } catch (IOException e) {
            log.warn("Failed to clean up temp directory: {}", repoDir, e);
        }
    }
}