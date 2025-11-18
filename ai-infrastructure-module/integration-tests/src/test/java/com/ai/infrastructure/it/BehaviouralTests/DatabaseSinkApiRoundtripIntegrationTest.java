package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.api.dto.BehaviorEventRequest;
import com.ai.behavior.api.dto.BehaviorEventResponse;
import com.ai.behavior.api.dto.BehaviorIngestionResponse;
import com.ai.behavior.model.EventType;
import com.ai.behavior.storage.BehaviorEventRepository;
import com.ai.infrastructure.it.TestApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "ai.behavior.sink.type=database",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
    }
)
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class DatabaseSinkApiRoundtripIntegrationTest {

    private static EmbeddedPostgres POSTGRES;

    @BeforeAll
    static void startPostgres() throws IOException {
        POSTGRES = EmbeddedPostgres.builder()
            .setPort(0)
            .start();
    }

    @AfterAll
    static void stopPostgres() throws IOException {
        if (POSTGRES != null) {
            POSTGRES.close();
        }
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private BehaviorEventRepository behaviorEventRepository;

    @Test
    void databaseSinkPersistsAndQueryReturnsEvents() {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        BehaviorEventRequest viewEvent = buildRequest(userId, "session-a", EventType.VIEW, "product-1",
            now.minusMinutes(5), Map.of("category", "luxury", "rank", 1));
        BehaviorEventRequest purchaseEvent = buildRequest(userId, "session-a", EventType.PURCHASE, "order-55",
            now.minusMinutes(1), Map.of("amount", 2500, "currency", "USD"));

        ResponseEntity<BehaviorIngestionResponse> firstIngest = restTemplate.postForEntity(
            "/api/ai-behavior/ingest/event", viewEvent, BehaviorIngestionResponse.class);
        ResponseEntity<BehaviorIngestionResponse> secondIngest = restTemplate.postForEntity(
            "/api/ai-behavior/ingest/event", purchaseEvent, BehaviorIngestionResponse.class);

        assertThat(firstIngest.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(secondIngest.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(behaviorEventRepository.count()).isEqualTo(2);

        ParameterizedTypeReference<List<BehaviorEventResponse>> typeRef = new ParameterizedTypeReference<>() {};
        ResponseEntity<List<BehaviorEventResponse>> response = restTemplate.exchange(
            "/api/ai-behavior/users/" + userId + "/events?limit=10",
            HttpMethod.GET,
            null,
            typeRef);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<BehaviorEventResponse> events = response.getBody();
        assertThat(events).hasSize(2);

        BehaviorEventResponse first = events.get(0);
        BehaviorEventResponse second = events.get(1);

        assertThat(first.getEventType()).isEqualTo(EventType.PURCHASE);
        assertThat(first.getMetadata()).containsEntry("amount", 2500);
        assertThat(second.getEventType()).isEqualTo(EventType.VIEW);
        assertThat(second.getMetadata()).containsEntry("category", "luxury");

        assertThat(first.getIngestedAt()).isNotNull();
        assertThat(first.getIngestedAt()).isAfterOrEqualTo(second.getIngestedAt());

        behaviorEventRepository.findById(first.getId())
            .ifPresent(entity -> assertThat(entity.getIngestedAt()).isEqualTo(first.getIngestedAt()));
    }

    private BehaviorEventRequest buildRequest(UUID userId,
                                              String sessionId,
                                              EventType eventType,
                                              String entityId,
                                              LocalDateTime timestamp,
                                              Map<String, Object> metadata) {
        BehaviorEventRequest request = new BehaviorEventRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setEventType(eventType);
        request.setEntityType("product");
        request.setEntityId(entityId);
        request.setTimestamp(timestamp);
        request.setChannel("web");
        request.setSource("integration-test");
        request.setMetadata(metadata);
        return request;
    }
}
