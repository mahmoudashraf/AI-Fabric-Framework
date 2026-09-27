package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.service.HttpDataSyncService;
import com.ai.infrastructure.connector.rest.service.ProviderTokenService;
import com.ai.infrastructure.connector.rest.service.ProviderWebhookService;
import com.ai.infrastructure.connector.rest.service.ProtectedResourceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntegrationOperationsControllerTest {

    @Test
    void webhookProjectionOmitsPayloadAndProtectedResourceFingerprints() {
        Fixture fixture = fixture();
        Instant now = Instant.parse("2026-09-27T12:00:00Z");
        when(fixture.webhookService.recent("neutral-hook")).thenReturn(List.of(
            new IntegrationStateRepository.WebhookEvent(
                "neutral-hook",
                "event-1",
                "record.changed",
                "protected-resource-fingerprint",
                "payload-sha256",
                "COMPLETED",
                1,
                0,
                0,
                null,
                now,
                now
            )
        ));

        List<Map<String, Object>> events = fixture.controller.events("neutral-hook");

        assertThat(events).singleElement().satisfies(event -> assertThat(event)
            .containsEntry("eventId", "event-1")
            .containsEntry("status", "COMPLETED")
            .doesNotContainKeys("payloadSha256", "resourceFingerprint")
        );
    }

    @Test
    void sourceProjectionOmitsProviderRecordIdentityFromWorkRows() {
        Fixture fixture = fixture();
        RestRoutingConfig.HttpDataSource source = new RestRoutingConfig.HttpDataSource();
        source.setEntityType("neutral-record");
        source.setVectorSpace("neutral-record");
        source.setConnectionProfileRef("neutral-profile");
        source.setProtectedResourceBindingRef("neutral-binding");
        fixture.config.getDataSources().put("neutral-source", source);
        Instant now = Instant.parse("2026-09-27T12:00:00Z");
        when(fixture.syncService.state("neutral-source")).thenReturn(new IntegrationStateRepository.SyncState(
            "neutral-source",
            "COMPLETED",
            null,
            "source-v1",
            null,
            null,
            new IntegrationStateRepository.SyncCounts(1, 1, 1, 0, 1, 1, 0),
            now,
            now,
            null,
            null,
            null,
            now
        ));
        when(fixture.tokenService.posture()).thenReturn(Map.of());
        when(fixture.repository.workStates("neutral-source")).thenReturn(List.of(
            new IntegrationStateRepository.IndexWorkState(
                "work-1",
                "neutral-source",
                "provider-record-secret",
                "UPSERT",
                "COMPLETED",
                null,
                now,
                now
            )
        ));

        Map<String, Object> sourceSummary = fixture.controller.source("neutral-source");

        assertThat(sourceSummary.get("work")).isInstanceOf(List.class);
        List<?> rows = (List<?>) sourceSummary.get("work");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) rows.getFirst();
        assertThat(row)
            .containsEntry("workId", "work-1")
            .doesNotContainKey("recordId");
    }

    private Fixture fixture() {
        RestRoutingConfig config = new RestRoutingConfig();
        HttpDataSyncService syncService = mock(HttpDataSyncService.class);
        ProviderWebhookService webhookService = mock(ProviderWebhookService.class);
        ProviderTokenService tokenService = mock(ProviderTokenService.class);
        ProtectedResourceService protectedResources = mock(ProtectedResourceService.class);
        IntegrationStateRepository repository = mock(IntegrationStateRepository.class);
        IntegrationOperationsController controller = new IntegrationOperationsController(
            config,
            new RestConnectorServiceProperties(),
            syncService,
            webhookService,
            tokenService,
            protectedResources,
            repository
        );
        return new Fixture(
            controller,
            config,
            syncService,
            webhookService,
            tokenService,
            repository
        );
    }

    private record Fixture(
        IntegrationOperationsController controller,
        RestRoutingConfig config,
        HttpDataSyncService syncService,
        ProviderWebhookService webhookService,
        ProviderTokenService tokenService,
        IntegrationStateRepository repository
    ) {
    }
}
