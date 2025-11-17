package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import com.ai.behavior.storage.BehaviorEventRepository;
import com.ai.infrastructure.it.TestApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TestApplication.class,
    properties = {
        "ai.behavior.sink.type=hybrid",
        "ai.behavior.sink.hybrid.hot-retention-seconds=20",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
    }
)
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HybridEventSinkIntegrationTest {

    private static EmbeddedPostgres POSTGRES;
    private static RedisServer redisServer;
    private static int redisPort;

    @BeforeAll
    static void startInfrastructure() throws IOException {
        POSTGRES = EmbeddedPostgres.builder().setPort(0).start();
        redisPort = findAvailablePort();
        redisServer = new RedisServer(redisPort);
        redisServer.start();
    }

    @AfterAll
    static void stopInfrastructure() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
        if (POSTGRES != null) {
            POSTGRES.close();
        }
    }

    @DynamicPropertySource
    static void hybridProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> redisPort);
    }

    private static int findAvailablePort() {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to allocate Redis port", ex);
        }
    }

    @Autowired
    private BehaviorIngestionService ingestionService;

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void hybridSinkWritesToRedisAndDatabase() {
        List<BehaviorEvent> ingested = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BehaviorEvent event = ingestionService.ingest(BehaviorEvent.builder()
                .userId(UUID.randomUUID())
                .sessionId("hybrid-session")
                .eventType(EventType.VIEW)
                .entityType("article")
                .entityId("article-" + i)
                .timestamp(LocalDateTime.now().minusSeconds(i))
                .metadata(new HashMap<>(Map.of("index", i)))
                .build());
            ingested.add(event);
        }

        assertThat(behaviorEventRepository.count()).isEqualTo(3);
        ingested.forEach(event -> assertThat(behaviorEventRepository.findById(event.getId())).isPresent());

        for (BehaviorEvent event : ingested) {
            String key = "behavior:hot:" + event.getId();
            Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(redisTemplate.opsForValue().get(key)).isNotBlank());
            assertThat(redisTemplate.getExpire(key)).isGreaterThan(0);
        }
    }
}
