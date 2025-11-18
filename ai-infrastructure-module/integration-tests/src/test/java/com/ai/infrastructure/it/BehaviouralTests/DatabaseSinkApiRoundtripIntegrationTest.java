package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.api.dto.BehaviorIngestionResponse;
import com.ai.behavior.api.dto.BehaviorSignalRequest;
import com.ai.behavior.api.dto.BehaviorSignalResponse;
import com.ai.behavior.storage.BehaviorSignalRepository;
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
    private BehaviorSignalRepository behaviorSignalRepository;

    @Test
    void databaseSinkPersistsAndQueryReturnsEvents() {
        UUID userId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        BehaviorSignalRequest viewEvent = buildRequest(userId, "session-a", "engagement.view", "product-1",
            now.minusMinutes(5), Map.of("category", "luxury", "rank", 1));
        BehaviorSignalRequest purchaseEvent = buildRequest(userId, "session-a", "conversion.transaction", "order-55",
            now.minusMinutes(1), Map.of("amount", 2500, "currency", "USD"));

        ResponseEntity<BehaviorIngestionResponse> firstIngest = restTemplate.postForEntity(
            "/api/ai-behavior/signals", viewEvent, BehaviorIngestionResponse.class);
        ResponseEntity<BehaviorIngestionResponse> secondIngest = restTemplate.postForEntity(
            "/api/ai-behavior/signals", purchaseEvent, BehaviorIngestionResponse.class);

        assertThat(firstIngest.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(secondIngest.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(behaviorSignalRepository.count()).isEqualTo(2);

        ParameterizedTypeReference<List<BehaviorSignalResponse>> typeRef = new ParameterizedTypeReference<>() {};
        ResponseEntity<List<BehaviorSignalResponse>> response = restTemplate.exchange(
            "/api/ai-behavior/users/" + userId + "/signals?limit=10",
            HttpMethod.GET,
            null,
            typeRef);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        List<BehaviorSignalResponse> events = response.getBody();
        assertThat(events).hasSize(2);

        BehaviorSignalResponse first = events.get(0);
        BehaviorSignalResponse second = events.get(1);

        assertThat(first.getSchemaId()).isEqualTo("conversion.transaction");
        assertThat(first.getAttributes()).containsEntry("amount", 2500);
        assertThat(second.getSchemaId()).isEqualTo("engagement.view");
        assertThat(second.getAttributes()).containsEntry("category", "luxury");

        assertThat(first.getIngestedAt()).isNotNull();
        assertThat(first.getIngestedAt()).isAfterOrEqualTo(second.getIngestedAt());

        behaviorSignalRepository.findById(first.getId())
            .ifPresent(entity -> assertThat(entity.getIngestedAt()).isEqualTo(first.getIngestedAt()));
    }

    private BehaviorSignalRequest buildRequest(UUID userId,
                                              String sessionId,
                                              String schemaId,
                                              String entityId,
                                              LocalDateTime timestamp,
                                              Map<String, Object> metadata) {
        BehaviorSignalRequest request = new BehaviorSignalRequest();
        request.setUserId(userId);
        request.setSessionId(sessionId);
        request.setSchemaId(schemaId);
        request.setEntityType("product");
        request.setEntityId(entityId);
        request.setTimestamp(timestamp);
        request.setChannel("web");
        request.setSource("integration-test");
        request.setAttributes(metadata);
        return request;
    }
}
