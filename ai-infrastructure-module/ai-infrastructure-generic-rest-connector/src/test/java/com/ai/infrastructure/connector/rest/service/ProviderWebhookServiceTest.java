package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.InMemoryIntegrationStateRepository;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderWebhookServiceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-09-27T01:00:00Z");

    @Test
    void verifiesRawBodyBeforeParsingAndTracksCompletionDuplicatesRejectionsAndReplay() throws Exception {
        RestRoutingConfig config = config();
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        HttpDataSyncService syncService = mock(HttpDataSyncService.class);
        IntegrationStateRepository.SyncState completed = new IntegrationStateRepository.SyncState(
            "neutral-source",
            "COMPLETED",
            null,
            "fixture-v1",
            null,
            null,
            new IntegrationStateRepository.SyncCounts(1, 1, 1, 0, 1, 1, 0),
            NOW,
            NOW,
            null,
            null,
            null,
            NOW
        );
        when(syncService.reconcileAsync("neutral-source"))
            .thenAnswer(ignored -> CompletableFuture.completedFuture(completed));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderWebhookService service = new ProviderWebhookService(
            config,
            new WebhookVerificationService(clock),
            new ProtectedResourceService(config, OBJECT_MAPPER),
            repository,
            syncService,
            OBJECT_MAPPER,
            clock
        );

        byte[] body = "{\"eventId\":\"event-1\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\"}"
            .getBytes(StandardCharsets.UTF_8);
        String signature = signature("fixture-webhook-secret", NOW.getEpochSecond(), body);

        ProviderWebhookService.WebhookReceipt accepted = service.accept(
            "neutral-hook", "POST", "application/json; charset=utf-8", signature, body
        );
        ProviderWebhookService.WebhookReceipt duplicate = service.accept(
            "neutral-hook", "POST", "application/json", signature, body
        );
        byte[] conflictingBody = "{\"eventId\":\"event-1\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\",\"revision\":2}"
            .getBytes(StandardCharsets.UTF_8);
        ProviderWebhookService.WebhookReceipt conflict = service.accept(
            "neutral-hook",
            "POST",
            "application/json",
            signature("fixture-webhook-secret", NOW.getEpochSecond(), conflictingBody),
            conflictingBody
        );
        ProviderWebhookService.WebhookReceipt rejected = service.accept(
            "neutral-hook", "POST", "application/json", "t=" + NOW.getEpochSecond() + ",v1=wrong", body
        );
        ProviderWebhookService.WebhookReceipt replay = service.replay("neutral-hook", "event-1");

        assertThat(accepted.accepted()).isTrue();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(conflict.accepted()).isFalse();
        assertThat(conflict.errorClass()).isEqualTo("WEBHOOK_EVENT_ID_CONFLICT");
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.errorClass()).isEqualTo("WEBHOOK_SIGNATURE_INVALID");
        assertThat(replay.status()).isEqualTo("REPLAY_QUEUED");

        IntegrationStateRepository.WebhookEvent event = repository.event("neutral-hook", "event-1").orElseThrow();
        assertThat(event.status()).isEqualTo("COMPLETED");
        assertThat(event.attemptCount()).isEqualTo(2);
        assertThat(event.duplicateCount()).isEqualTo(1);
        assertThat(event.replayCount()).isEqualTo(1);
        assertThat(repository.recentEvents("neutral-hook", 20))
            .noneMatch(item -> "REJECTED".equals(item.status()));
        assertThat(repository.eventRejectionCount("neutral-hook")).isEqualTo(2);
    }

    @Test
    void failedReconciliationBecomesDurableDeadLetterAtConfiguredAttemptLimit() throws Exception {
        RestRoutingConfig config = config();
        config.getWebhooks().get("neutral-hook").setMaxReconcileAttempts(1);
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        HttpDataSyncService syncService = mock(HttpDataSyncService.class);
        when(syncService.reconcileAsync("neutral-source"))
            .thenReturn(CompletableFuture.failedFuture(
                new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 503, "fixture unavailable")
            ));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderWebhookService service = new ProviderWebhookService(
            config,
            new WebhookVerificationService(clock),
            new ProtectedResourceService(config, OBJECT_MAPPER),
            repository,
            syncService,
            OBJECT_MAPPER,
            clock
        );
        byte[] body = "{\"eventId\":\"event-failed\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\"}"
            .getBytes(StandardCharsets.UTF_8);

        service.accept(
            "neutral-hook",
            "POST",
            "application/json",
            signature("fixture-webhook-secret", NOW.getEpochSecond(), body),
            body
        );

        IntegrationStateRepository.WebhookEvent event = repository.event("neutral-hook", "event-failed").orElseThrow();
        assertThat(event.status()).isEqualTo("DEAD_LETTER");
        assertThat(event.attemptCount()).isEqualTo(1);
        assertThat(event.errorClass()).isEqualTo("SERVICE_UNAVAILABLE");
    }

    @Test
    void millisecondSignedWebhookPersistsRecordKeyAndRunsTargetedReconciliation() throws Exception {
        RestRoutingConfig config = config();
        RestRoutingConfig.HttpDataSource dataSource = config.getDataSources().get("neutral-source");
        dataSource.getTargetedRecordFetch().setEnabled(true);
        dataSource.getTargetedRecordFetch().getRecordKeyPlacement().setTarget(
            RestRoutingConfig.RecordKeyPlacement.Target.QUERY
        );
        dataSource.getTargetedRecordFetch().getRecordKeyPlacement().setField("recordId");
        RestRoutingConfig.WebhookSource webhook = config.getWebhooks().get("neutral-hook");
        webhook.setReconciliationStrategy(
            RestRoutingConfig.WebhookSource.ReconciliationStrategy.FETCH_CURRENT_RECORD
        );
        webhook.setRecordKeyJsonPointer("/recordId");
        webhook.getVerification().setTimestampUnit(
            RestRoutingConfig.WebhookVerification.TimestampUnit.MILLISECONDS
        );
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        HttpDataSyncService syncService = mock(HttpDataSyncService.class);
        when(syncService.reconcileRecordAsync("neutral-source", "record-42"))
            .thenReturn(CompletableFuture.completedFuture(new HttpDataSyncService.RecordReconcileResult(
                "neutral-source", "record-42", "COMPLETED", 1, 0, 0, null
            )));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderWebhookService service = new ProviderWebhookService(
            config,
            new WebhookVerificationService(clock),
            new ProtectedResourceService(config, OBJECT_MAPPER),
            repository,
            syncService,
            OBJECT_MAPPER,
            clock
        );
        byte[] body = "{\"eventId\":\"event-targeted\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\",\"recordId\":\"record-42\"}"
            .getBytes(StandardCharsets.UTF_8);
        long timestampMillis = NOW.toEpochMilli();

        ProviderWebhookService.WebhookReceipt receipt = service.accept(
            "neutral-hook",
            "POST",
            "application/json",
            signature("fixture-webhook-secret", timestampMillis, body),
            body
        );

        assertThat(receipt.accepted()).isTrue();
        IntegrationStateRepository.WebhookEvent event = repository
            .event("neutral-hook", "event-targeted")
            .orElseThrow();
        assertThat(event.recordKey()).isEqualTo("record-42");
        assertThat(event.status()).isEqualTo("COMPLETED");
        verify(syncService).reconcileRecordAsync("neutral-source", "record-42");
    }

    @Test
    void rejectsOversizedEventIdentityBeforePersistenceAndHonorsReplayPolicy() throws Exception {
        RestRoutingConfig config = config();
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        HttpDataSyncService syncService = mock(HttpDataSyncService.class);
        when(syncService.reconcileAsync("neutral-source"))
            .thenReturn(CompletableFuture.completedFuture(null));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderWebhookService service = new ProviderWebhookService(
            config,
            new WebhookVerificationService(clock),
            new ProtectedResourceService(config, OBJECT_MAPPER),
            repository,
            syncService,
            OBJECT_MAPPER,
            clock
        );
        byte[] oversized = ("{\"eventId\":\"" + "x".repeat(241)
            + "\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\"}")
            .getBytes(StandardCharsets.UTF_8);

        ProviderWebhookService.WebhookReceipt rejected = service.accept(
            "neutral-hook",
            "POST",
            "application/json",
            signature("fixture-webhook-secret", NOW.getEpochSecond(), oversized),
            oversized
        );

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.errorClass()).isEqualTo("WEBHOOK_EVENT_IDENTITY_INVALID");
        assertThat(repository.recentEvents("neutral-hook", 20)).isEmpty();
        assertThat(repository.eventRejectionCount("neutral-hook")).isEqualTo(1);

        config.getWebhooks().get("neutral-hook").setManualReplayEnabled(false);
        byte[] valid = "{\"eventId\":\"event-no-replay\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\"}"
            .getBytes(StandardCharsets.UTF_8);
        service.accept(
            "neutral-hook",
            "POST",
            "application/json",
            signature("fixture-webhook-secret", NOW.getEpochSecond(), valid),
            valid
        );
        assertThatThrownBy(() -> service.replay("neutral-hook", "event-no-replay"))
            .isInstanceOf(ProviderCallException.class)
            .extracting(error -> ((ProviderCallException) error).errorClass())
            .isEqualTo(ProviderErrorClass.RESOURCE_ACCESS_DENIED);
    }

    @Test
    void compositeIdentityDeduplicatesRetriesButAcceptsLaterUpdatesForTheSameResource() throws Exception {
        RestRoutingConfig config = config();
        RestRoutingConfig.WebhookSource webhook = config.getWebhooks().get("neutral-hook");
        webhook.setEventIdJsonPointer(null);
        webhook.setEventIdentityJsonPointers(List.of("/recordId", "/occurredAt"));
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        HttpDataSyncService syncService = mock(HttpDataSyncService.class);
        IntegrationStateRepository.SyncState completed = new IntegrationStateRepository.SyncState(
            "neutral-source", "COMPLETED", null, "fixture-v1", null, null,
            new IntegrationStateRepository.SyncCounts(1, 1, 1, 0, 1, 1, 0),
            NOW, NOW, null, null, null, NOW
        );
        when(syncService.reconcileAsync("neutral-source"))
            .thenReturn(CompletableFuture.completedFuture(completed));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderWebhookService service = new ProviderWebhookService(
            config,
            new WebhookVerificationService(clock),
            new ProtectedResourceService(config, OBJECT_MAPPER),
            repository,
            syncService,
            OBJECT_MAPPER,
            clock
        );
        byte[] firstBody = "{\"recordId\":\"record-7\",\"occurredAt\":\"2026-09-27T00:59:00Z\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\"}"
            .getBytes(StandardCharsets.UTF_8);
        byte[] laterBody = "{\"recordId\":\"record-7\",\"occurredAt\":\"2026-09-27T01:00:00Z\",\"eventType\":\"record.changed\",\"scope\":\"scope-9\"}"
            .getBytes(StandardCharsets.UTF_8);

        ProviderWebhookService.WebhookReceipt first = service.accept(
            "neutral-hook", "POST", "application/json",
            signature("fixture-webhook-secret", NOW.getEpochSecond(), firstBody), firstBody
        );
        ProviderWebhookService.WebhookReceipt duplicate = service.accept(
            "neutral-hook", "POST", "application/json",
            signature("fixture-webhook-secret", NOW.getEpochSecond(), firstBody), firstBody
        );
        ProviderWebhookService.WebhookReceipt later = service.accept(
            "neutral-hook", "POST", "application/json",
            signature("fixture-webhook-secret", NOW.getEpochSecond(), laterBody), laterBody
        );

        assertThat(first.accepted()).isTrue();
        assertThat(first.eventId()).startsWith("evt-").hasSize(68);
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.eventId()).isEqualTo(first.eventId());
        assertThat(later.accepted()).isTrue();
        assertThat(later.duplicate()).isFalse();
        assertThat(later.eventId()).isNotEqualTo(first.eventId());
        assertThat(repository.recentEvents("neutral-hook", 20)).hasSize(2);
    }

    @Test
    void retryTickRecoversAcceptedEventLeftBehindByRestart() {
        RestRoutingConfig config = config();
        InMemoryIntegrationStateRepository repository = new InMemoryIntegrationStateRepository();
        repository.registerEvent(new IntegrationStateRepository.WebhookEvent(
            "neutral-hook",
            "event-after-restart",
            "record.changed",
            null,
            "resource-fingerprint",
            "payload-hash",
            "ACCEPTED",
            0,
            0,
            0,
            null,
            NOW.minusSeconds(120),
            NOW.minusSeconds(120)
        ));
        HttpDataSyncService syncService = mock(HttpDataSyncService.class);
        IntegrationStateRepository.SyncState completed = new IntegrationStateRepository.SyncState(
            "neutral-source",
            "COMPLETED",
            null,
            "fixture-v1",
            null,
            null,
            new IntegrationStateRepository.SyncCounts(1, 1, 1, 0, 1, 1, 0),
            NOW,
            NOW,
            null,
            null,
            null,
            NOW
        );
        when(syncService.reconcileAsync("neutral-source"))
            .thenReturn(CompletableFuture.completedFuture(completed));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        ProviderWebhookService service = new ProviderWebhookService(
            config,
            new WebhookVerificationService(clock),
            new ProtectedResourceService(config, OBJECT_MAPPER),
            repository,
            syncService,
            OBJECT_MAPPER,
            clock
        );

        service.retryFailedEvents();

        IntegrationStateRepository.WebhookEvent recovered = repository
            .event("neutral-hook", "event-after-restart")
            .orElseThrow();
        assertThat(recovered.status()).isEqualTo("COMPLETED");
        assertThat(recovered.attemptCount()).isEqualTo(1);
    }

    private RestRoutingConfig config() {
        RestRoutingConfig config = new RestRoutingConfig();
        RestRoutingConfig.ConnectionProfile profile = new RestRoutingConfig.ConnectionProfile();
        profile.setEnvironment("fixture");
        profile.setBaseUrl("https://provider.fixture.invalid");
        profile.setAllowedHosts(List.of("provider.fixture.invalid"));
        config.getConnectionProfiles().put("neutral-profile", profile);

        RestRoutingConfig.ProtectedResourceBinding binding = new RestRoutingConfig.ProtectedResourceBinding();
        binding.setConnectionProfileRef("neutral-profile");
        binding.setEnvironment("fixture");
        binding.setResourceType("scope");
        binding.setResourceId("scope-9");
        config.getProtectedResources().put("neutral-binding", binding);

        RestRoutingConfig.HttpDataSource source = new RestRoutingConfig.HttpDataSource();
        source.setConnectionProfileRef("neutral-profile");
        source.setProtectedResourceBindingRef("neutral-binding");
        config.getDataSources().put("neutral-source", source);

        RestRoutingConfig.WebhookSource webhook = new RestRoutingConfig.WebhookSource();
        webhook.setMethod("POST");
        webhook.setRegistrationExpected(true);
        webhook.setManualReplayEnabled(true);
        webhook.setProtectedResourceBindingRef("neutral-binding");
        webhook.setEventIdJsonPointer("/eventId");
        webhook.setEventTypeJsonPointer("/eventType");
        webhook.setResourceJsonPointer("/scope");
        webhook.setAllowedEventTypes(List.of("record.changed"));
        webhook.setReconcileDataSourceRef("neutral-source");
        webhook.getVerification().setStrategy(
            RestRoutingConfig.WebhookVerification.Strategy.HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY
        );
        webhook.getVerification().setSignatureHeader("X-Fixture-Signature");
        webhook.getVerification().setSecret("fixture-webhook-secret");
        config.getWebhooks().put("neutral-hook", webhook);
        return config;
    }

    private String signature(String secret, long timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        byte[] digest = mac.doFinal(body);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return "t=" + timestamp + ",v1=" + hex;
    }
}
