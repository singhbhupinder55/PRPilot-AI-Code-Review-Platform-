package dev.prpilot.ingestion.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class VoyageEmbeddingService {

    private final RestClient restClient;
    private final String model;
    private final int batchSize;

    public VoyageEmbeddingService(
            @Value("${voyage.api-key}") String apiKey,
            @Value("${voyage.api-url}") String apiUrl,
            @Value("${voyage.model}") String model,
            @Value("${voyage.batch-size}") int batchSize) {

        this.model = model;
        this.batchSize = batchSize;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<float[]> embed(List<String> texts) {
        List<float[]> results = new ArrayList<>();

        for (int i = 0; i < texts.size(); i += batchSize) {
            List<String> batch = texts.subList(i, Math.min(i + batchSize, texts.size()));
            results.addAll(embedBatchWithRetry(batch, 3));
        }

        return results;
    }

    private List<float[]> embedBatchWithRetry(List<String> batch, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return embedBatch(batch);
            } catch (HttpClientErrorException.TooManyRequests e) {
                long backoffMs = 20_000L * attempt; // 20s, 40s, 60s
                log.warn("Voyage API rate limited (attempt {}/{}), backing off {}ms",
                        attempt, maxAttempts, backoffMs);
                sleep(backoffMs);
            }
        }
        throw new IllegalStateException("Voyage API still rate-limited after " + maxAttempts + " attempts");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private List<float[]> embedBatch(List<String> batch) {
        log.debug("Embedding batch of {} texts via {}", batch.size(), model);

        VoyageRequest request = new VoyageRequest(batch, model, "document");

        VoyageResponse response = restClient.post()
                .body(request)
                .retrieve()
                .body(VoyageResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("Voyage API returned an empty response");
        }

        return response.data().stream()
                .map(VoyageEmbeddingData::embedding)
                .map(this::toFloatArray)
                .toList();
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }

    private record VoyageRequest(
            List<String> input,
            String model,
            @JsonProperty("input_type") String inputType
    ) {}

    private record VoyageResponse(List<VoyageEmbeddingData> data) {}

    private record VoyageEmbeddingData(List<Double> embedding, int index) {}
}