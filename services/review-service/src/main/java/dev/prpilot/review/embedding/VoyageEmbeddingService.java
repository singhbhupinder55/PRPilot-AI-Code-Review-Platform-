package dev.prpilot.review.embedding;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
public class VoyageEmbeddingService {

    private final RestClient restClient;
    private final String model;

    public VoyageEmbeddingService(
            @Value("${prpilot.voyage.api-key}") String voyageKey,
            @Value("${prpilot.voyage.api-url}") String apiUrl,
            @Value("${prpilot.voyage.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + voyageKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Embeds a query string (PR diff text) for similarity search.
     * Uses input_type "query" — Voyage optimizes differently for queries
     * vs documents, improving retrieval relevance.
     */
    public String embedQuery(String text) {
        log.debug("Embedding query of length {}", text.length());

        VoyageRequest request = new VoyageRequest(List.of(text), model, "query");

        VoyageResponse response = restClient.post()
                .body(request)
                .retrieve()
                .body(VoyageResponse.class);

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new IllegalStateException("Voyage API returned empty response for query");
        }

        float[] vector = toFloatArray(response.data().get(0).embedding());
        return toLiteral(vector);
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }

    private String toLiteral(float[] vector) {
        String joined = IntStream.range(0, vector.length)
                .mapToObj(i -> String.valueOf(vector[i]))
                .collect(Collectors.joining(","));
        return "[" + joined + "]";
    }

    private record VoyageRequest(
            List<String> input,
            String model,
            @JsonProperty("input_type") String inputType
    ) {}

    private record VoyageResponse(List<EmbeddingData> data) {}
    private record EmbeddingData(List<Double> embedding, int index) {}
}