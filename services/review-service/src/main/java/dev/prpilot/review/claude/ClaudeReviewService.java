package dev.prpilot.review.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Calls the Anthropic Claude API to generate a code review.
 * Uses retrieved code chunks as context (the "augmented" part of RAG).
 */
@Service
@Slf4j
public class ClaudeReviewService {

    private final RestClient restClient;
    private final String model;
    private final int maxTokens;

    public ClaudeReviewService(
            @Value("${prpilot.anthropic.api-key}") String apiKey,
            @Value("${prpilot.anthropic.api-url}") String apiUrl,
            @Value("${prpilot.anthropic.model}") String model,
            @Value("${prpilot.anthropic.max-tokens}") int maxTokens) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Generates a code review given the PR metadata, diff, and
     * retrieved context chunks from the codebase.
     */
    public String generateReview(
            String prTitle,
            String prAuthor,
            String repoFullName,
            String headSha,
            List<String> contextChunks) {

        String context = buildContext(contextChunks);
        String prompt = buildPrompt(prTitle, prAuthor, repoFullName, headSha, context);

        log.debug("Calling Claude {} with {} context chunks", model, contextChunks.size());

        ClaudeRequest request = new ClaudeRequest(
                model,
                maxTokens,
                List.of(new Message("user", prompt))
        );

        ClaudeResponse response = restClient.post()
                .body(request)
                .retrieve()
                .body(ClaudeResponse.class);

        if (response == null || response.content() == null || response.content().isEmpty()) {
            throw new IllegalStateException("Claude API returned empty response");
        }

        String review = response.content().get(0).text();
        log.info("Generated review ({} chars) using model={}", review.length(), model);
        return review;
    }

    private String buildContext(List<String> chunks) {
        if (chunks.isEmpty()) {
            return "No relevant code context was found in the repository.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            sb.append("--- Chunk ").append(i + 1).append(" ---\n");
            sb.append(chunks.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    private String buildPrompt(
            String prTitle,
            String prAuthor,
            String repoFullName,
            String headSha,
            String context) {

        return """
                You are an expert code reviewer. Review the following pull request and provide
                constructive, specific feedback.

                **Repository:** %s
                **PR Title:** %s
                **Author:** %s
                **Commit:** %s

                **Relevant codebase context (retrieved via semantic search):**
                %s

                Please provide a code review covering:
                1. **Summary** — what this PR appears to do based on the context
                2. **Potential issues** — bugs, edge cases, security concerns
                3. **Code quality** — readability, maintainability, naming
                4. **Suggestions** — concrete improvements with examples where helpful
                5. **Overall assessment** — approve, request changes, or needs discussion

                Be specific and actionable. Reference actual code from the context where relevant.
                """.formatted(repoFullName, prTitle, prAuthor, headSha, context);
    }

    // --- Request/response DTOs ---

    private record ClaudeRequest(
            String model,
            @com.fasterxml.jackson.annotation.JsonProperty("max_tokens") int maxTokens,
            List<Message> messages
    ) {}

    private record Message(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ClaudeResponse(List<ContentBlock> content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ContentBlock(String type, String text) {}
}