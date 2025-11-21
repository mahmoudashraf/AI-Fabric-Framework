package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.infrastructure.it.AbstractBehaviorIntegrationTest;
import com.ai.infrastructure.it.TestApplication;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import redis.embedded.RedisServer;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TestApplication.class,
    properties = {
        "ai.behavior.sink.type=redis",
        "ai.behavior.sink.redis.ttl-seconds=2"
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RedisEventSinkIntegrationTest extends AbstractBehaviorIntegrationTest {

    private static RedisServer redisServer;
    private static int redisPort;

    @BeforeAll
    static void startRedis() throws IOException {
        redisPort = findAvailablePort();
        redisServer = new RedisServer(redisPort);
        redisServer.start();
    }

    @AfterAll
    static void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
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
    private StringRedisTemplate redisTemplate;

    @Test
    void redisSinkStoresEventWithTtl() {
        BehaviorSignal event = ingestionService.ingest(BehaviorSignal.builder()
            .userId(UUID.randomUUID())
            .sessionId("redis-session")
            .schemaId("engagement.view")
            .entityType("product")
            .entityId("redis-product")
            .timestamp(LocalDateTime.now())
            .attributes(new HashMap<>(Map.of("channel", "web")))
            .build());

        String key = "behavior:event:" + event.getId();
        String payload = redisTemplate.opsForValue().get(key);
        assertThat(payload).isNotBlank();
        assertThat(redisTemplate.getExpire(key)).isGreaterThan(0);

        Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .until(() -> redisTemplate.opsForValue().get(key) == null);
    }
}
