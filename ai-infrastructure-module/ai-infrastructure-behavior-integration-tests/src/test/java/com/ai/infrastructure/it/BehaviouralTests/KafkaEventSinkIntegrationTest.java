package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.api.dto.BehaviorHealthResponse;
import com.ai.behavior.ingestion.BehaviorIngestionMetrics;
import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.service.BehaviorMonitoringService;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.it.config.PostgresTestContainerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(topics = KafkaEventSinkIntegrationTest.TOPIC, partitions = 1)
@SpringBootTest(
    classes = TestApplication.class,
    properties = {
        "ai.behavior.sink.type=kafka",
        "ai.behavior.sink.kafka.topic=" + KafkaEventSinkIntegrationTest.TOPIC,
        "ai.behavior.providers.aggregated.enabled=false"
    }
)
@TestPropertySource(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PostgresTestContainerConfig.class)
public class KafkaEventSinkIntegrationTest {

    static final String TOPIC = "behavior-events-test";

    @Autowired
    private BehaviorIngestionService ingestionService;

    @Autowired
    private BehaviorMonitoringService monitoringService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private BehaviorIngestionMetrics ingestionMetrics;

    private Consumer<String, String> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void kafkaSinkPublishesEventsAndMonitoringReflectsActivity() {
        consumer = buildConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, TOPIC);

        UUID userId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            ingestionService.ingest(BehaviorSignal.builder()
                .userId(userId)
                .sessionId("session-" + i)
                .schemaId("conversion.transaction")
                .entityType("order")
                .entityId("order-" + i)
                .timestamp(LocalDateTime.now().minusSeconds(i))
                .attributes(new HashMap<>(Map.of("index", i, "total", 100 + i)))
                .build());
        }

        List<String> payloads = new ArrayList<>();
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.forEach(record -> payloads.add(record.value()));
            return payloads.size() >= 5;
        });

        assertThat(payloads).hasSize(5);
        assertThat(ingestionMetrics.totalCount()).isEqualTo(5);

        BehaviorHealthResponse health = monitoringService.health();
        assertThat(health.getSinkType()).isEqualTo("kafka");
        assertThat(health.getRecentEvents()).isEqualTo(5);
        assertThat(health.isHealthy()).isTrue();
    }

    private Consumer<String, String> buildConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("behavior-it", "false", embeddedKafka);
        props.put("key.deserializer", StringDeserializer.class);
        props.put("value.deserializer", StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }
}
