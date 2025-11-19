package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.ingestion.BehaviorSignalSink;
import com.ai.behavior.ingestion.impl.HybridEventSink;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.storage.BehaviorSignalRepository;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.it.config.PostgresTestContainerConfig;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.annotation.Import;
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
        "ai.behavior.sink.hybrid.hot-retention-seconds=120",
        "logging.level.com.ai.behavior.ingestion.impl=DEBUG"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(PostgresTestContainerConfig.class)
@Slf4j
public class HybridEventSinkIntegrationTest {

    private static RedisServer redisServer;
    private static int redisPort;

    @BeforeAll
    static void startInfrastructure() throws IOException {
        redisPort = findAvailablePort();
        redisServer = new RedisServer(redisPort);
        redisServer.start();
    }

    @AfterAll
    static void stopInfrastructure() throws IOException {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void hybridProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", () -> "localhost");
        registry.add("spring.redis.port", () -> redisPort);
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> redisPort);
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
    private BehaviorSignalSink behaviorEventSink;

    @Autowired
    private BehaviorSignalRepository behaviorEventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Test
    void hybridSinkWritesToRedisAndDatabase() {
        List<BehaviorSignal> ingested = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BehaviorSignal event = ingestionService.ingest(BehaviorSignal.builder()
                .userId(UUID.randomUUID())
                .sessionId("hybrid-session")
                .schemaId("engagement.view")
                .entityType("article")
                .entityId("article-" + i)
                .timestamp(LocalDateTime.now().minusSeconds(i))
                .attributes(new HashMap<>(Map.of("index", i)))
                .build());
            ingested.add(event);
            log.info("Test ingested behavior event {}", event.getId());
        }

        assertThat(behaviorEventRepository.count()).isEqualTo(3);
        ingested.forEach(event -> assertThat(behaviorEventRepository.findById(event.getId())).isPresent());

        assertThat(((LettuceConnectionFactory) redisConnectionFactory).getPort()).isEqualTo(redisPort);
        assertThat(behaviorEventSink).isInstanceOf(HybridEventSink.class);

        for (BehaviorSignal event : ingested) {
            String key = "behavior:hot:" + event.getId();
            Awaitility.await()
                .pollDelay(Duration.ZERO)
                .pollInterval(Duration.ofMillis(200))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    var currentKeys = redisTemplate.keys("behavior:hot:*");
                    String payload = redisTemplate.opsForValue().get(key);
                    assertThat(payload)
                        .withFailMessage("Redis payload missing for key %s; current keys: %s", key, currentKeys)
                        .isNotBlank();
                    Long ttlSeconds = redisTemplate.getExpire(key);
                    assertThat(ttlSeconds)
                        .withFailMessage("TTL missing for key %s; current keys: %s", key, currentKeys)
                        .isNotNull();
                    assertThat(ttlSeconds).isGreaterThan(5);
                });
        }
    }
}
