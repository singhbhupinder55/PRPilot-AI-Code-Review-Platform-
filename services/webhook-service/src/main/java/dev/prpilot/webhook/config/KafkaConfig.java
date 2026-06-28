package dev.prpilot.webhook.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {

    @Value("${prpilot.kafka.topics.pr-events}")
    private String prEventsTopic;

    /**
     * Auto-creates the pr.events topic on startup if it doesn't exist.
     * 3 partitions = up to 3 consumers can read in parallel.
     */
    @Bean
    public NewTopic prEventsTopic() {
        return new NewTopic(prEventsTopic, 3, (short) 1);
    }
}