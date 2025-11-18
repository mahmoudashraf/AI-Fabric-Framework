package com.ai.infrastructure.it.BehaviouralTests;

import com.ai.behavior.adapter.ExternalAnalyticsAdapter;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.model.EventType;
import com.ai.infrastructure.it.TestApplication;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = TestApplication.class,
    properties = {
        "ai.behavior.providers.aggregated.enabled=true",
        "ai.behavior.providers.aggregated.provider-order[0]=external",
        "ai.behavior.providers.aggregated.max-providers=1"
    }
)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExternalAnalyticsAdapterContractTest {

    private static final WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

    static {
        wireMockServer.start();
    }

    @Autowired
    private ExternalAnalyticsAdapter externalAnalyticsAdapter;
    
    @AfterAll
    void stopServer() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.behavior.providers.external.enabled", () -> "true");
        registry.add("ai.behavior.providers.external.base-url", wireMockServer::baseUrl);
        registry.add("ai.behavior.providers.external.api-key", () -> "test-key");
        registry.add("ai.behavior.providers.external.query-path", () -> "/behavior/query");
        registry.add("ai.behavior.providers.external.timeout", () -> "PT2S");
    }

    @Test
    void adapterSendsPayloadAndParsesResponse() {
        wireMockServer.stubFor(post(urlEqualTo("/behavior/query"))
            .withHeader("X-Api-Key", equalTo("test-key"))
            .willReturn(okJson("""
                [
                  {
                    "id": "00000000-0000-0000-0000-000000000001",
                    "userId": "123e4567-e89b-12d3-a456-426614174000",
                    "eventType": "VIEW",
                    "timestamp": "2025-11-17T00:00:00",
                    "metadata": {"source":"wiremock"}
                  },
                  {
                    "id": "00000000-0000-0000-0000-000000000002",
                    "userId": "123e4567-e89b-12d3-a456-426614174000",
                    "eventType": "CLICK",
                    "timestamp": "2025-11-17T00:01:00",
                    "metadata": {"source":"wiremock"}
                  }
                ]
                """)));

        BehaviorQuery query = BehaviorQuery.builder()
            .userId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
            .eventType(EventType.VIEW)
            .limit(25)
            .build();

        List<BehaviorSignal> events = externalAnalyticsAdapter.fetchEvents(query);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo(EventType.VIEW);
        assertThat(events.get(1).getEventType()).isEqualTo(EventType.CLICK);

        wireMockServer.verify(postRequestedFor(urlEqualTo("/behavior/query"))
            .withHeader("X-Api-Key", equalTo("test-key"))
            .withRequestBody(containing("\"limit\":25"))
            .withRequestBody(containing("\"eventType\":\"VIEW\"")));
    }
}
