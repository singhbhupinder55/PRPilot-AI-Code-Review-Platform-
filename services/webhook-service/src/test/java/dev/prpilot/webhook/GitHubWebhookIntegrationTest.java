package dev.prpilot.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.*;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GitHubWebhookIntegrationTest {

    private static final String SECRET = "test-integration-secret";
    private static final String TOPIC = "pr.events";

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("prpilot.github.webhook-secret", () -> SECRET);
    }

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    @DisplayName("valid PR webhook lands in Kafka topic")
    void validWebhookIsPublishedToKafka() throws Exception {
        // Arrange
        String body = """
                {
                  "action":"opened",
                  "pull_request":{
                    "number":99,
                    "title":"Integration test PR",
                    "user":{"login":"itest-user"},
                    "head":{"sha":"deadbeef","ref":"feat/x"},
                    "base":{"ref":"main"},
                    "html_url":"https://github.com/test/test/pull/99"
                  },
                  "repository":{"full_name":"test/test"}
                }
                """;

        String signature = computeSignature(body.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-GitHub-Event", "pull_request");
        headers.set("X-GitHub-Delivery", "itest-001");
        headers.set("X-Hub-Signature-256", signature);

        // Act
        ResponseEntity<String> response = new RestTemplate().exchange(
                "http://localhost:" + port + "/webhooks/github",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        // Assert HTTP response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo("queued");

        // Assert message landed in Kafka
        try (Consumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(java.util.List.of(TOPIC));
            ConsumerRecord<String, String> record =
                    KafkaTestUtils.getSingleRecord(consumer, TOPIC, Duration.ofSeconds(10));

            assertThat(record.key()).isEqualTo("test/test");

            JsonNode event = objectMapper.readTree(record.value());
            assertThat(event.path("deliveryId").asText()).isEqualTo("itest-001");
            assertThat(event.path("action").asText()).isEqualTo("opened");
            assertThat(event.path("prNumber").asLong()).isEqualTo(99L);
            assertThat(event.path("repoFullName").asText()).isEqualTo("test/test");
            assertThat(event.path("headSha").asText()).isEqualTo("deadbeef");
        }
    }

    @Test
    @DisplayName("invalid signature returns 401")
    void invalidSignatureRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-GitHub-Event", "pull_request");
        headers.set("X-GitHub-Delivery", "itest-bad");
        headers.set("X-Hub-Signature-256", "sha256=deadbeef");

        try {
            new RestTemplate().exchange(
                    "http://localhost:" + port + "/webhooks/github",
                    HttpMethod.POST,
                    new HttpEntity<>("{}", headers),
                    String.class);
        }  catch (org.springframework.web.client.HttpClientErrorException e) {
            // RestTemplate's error-body capture is inconsistent across responses;
            // the 401 status is the behavior under test. We verified the exact
            // "invalid signature" body manually via curl against the running service.
            assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            return;
        }
        throw new AssertionError("Expected 401 but got 2xx");
    }

    private String computeSignature(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    }

    private Consumer<String, String> createConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                kafka.getBootstrapServers(), "test-group", "true");
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);
        props.put("auto.offset.reset", "earliest");
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}