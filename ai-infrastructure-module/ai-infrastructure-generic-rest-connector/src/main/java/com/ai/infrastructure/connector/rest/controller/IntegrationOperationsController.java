package com.ai.infrastructure.connector.rest.controller;

import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.service.HttpDataSyncService;
import com.ai.infrastructure.connector.rest.service.ProviderTokenService;
import com.ai.infrastructure.connector.rest.service.ProviderWebhookService;
import com.ai.infrastructure.connector.rest.service.ProtectedResourceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/integrations")
public class IntegrationOperationsController {

    private final RestRoutingConfig config;
    private final RestConnectorServiceProperties properties;
    private final HttpDataSyncService syncService;
    private final ProviderWebhookService webhookService;
    private final ProviderTokenService tokenService;
    private final ProtectedResourceService protectedResources;
    private final IntegrationStateRepository repository;

    public IntegrationOperationsController(
        RestRoutingConfig config,
        RestConnectorServiceProperties properties,
        HttpDataSyncService syncService,
        ProviderWebhookService webhookService,
        ProviderTokenService tokenService,
        ProtectedResourceService protectedResources,
        IntegrationStateRepository repository
    ) {
        this.config = config;
        this.properties = properties;
        this.syncService = syncService;
        this.webhookService = webhookService;
        this.tokenService = tokenService;
        this.protectedResources = protectedResources;
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> overview() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("persistence", Map.of(
            "enabled", properties.getPersistence() != null && properties.getPersistence().isEnabled(),
            "schema", properties.getPersistence() != null ? properties.getPersistence().getSchema() : "integration_connector"
        ));
        out.put("connectionProfiles", connectionProfiles());
        out.put("protectedResources", resourceBindings());
        out.put("tokenPosture", tokenService.posture());
        out.put("sources", config.getDataSources().keySet().stream().sorted().map(this::sourceSummary).toList());
        out.put("webhooks", config.getWebhooks().keySet().stream().sorted().map(this::webhookSummary).toList());
        out.put("runtimeDataSync", Map.of(
            "enabled", config.getRuntimeDataSync() != null && config.getRuntimeDataSync().isEnabled(),
            "baseUrlConfigured", config.getRuntimeDataSync() != null && StringUtils.hasText(config.getRuntimeDataSync().getBaseUrl()),
            "serviceCredentialConfigured", config.getRuntimeDataSync() != null && StringUtils.hasText(config.getRuntimeDataSync().getApiKeyValue())
        ));
        return out;
    }

    @GetMapping("/sources/{sourceId}")
    public Map<String, Object> source(@PathVariable String sourceId) {
        return sourceSummary(sourceId);
    }

    @PostMapping("/sources/{sourceId}/reconcile")
    public ResponseEntity<?> reconcile(@PathVariable String sourceId) {
        IntegrationStateRepository.SyncState state = syncService.reconcile(sourceId);
        return ResponseEntity.status(state.counts().failedWorkCount() > 0 ? HttpStatus.MULTI_STATUS : HttpStatus.OK).body(state);
    }

    @GetMapping("/webhooks/{sourceId}/events")
    public List<Map<String, Object>> events(@PathVariable String sourceId) {
        return webhookService.recent(sourceId).stream().map(this::webhookEventSummary).toList();
    }

    @PostMapping("/webhooks/{sourceId}/events/{eventId}/replay")
    public ResponseEntity<?> replay(@PathVariable String sourceId, @PathVariable String eventId) {
        return ResponseEntity.accepted().body(webhookService.replay(sourceId, eventId));
    }

    private List<Map<String, Object>> connectionProfiles() {
        return config.getConnectionProfiles().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            RestRoutingConfig.ConnectionProfile profile = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("profileId", entry.getKey());
            value.put("environment", profile.getEnvironment());
            value.put("baseUrl", profile.getBaseUrl());
            value.put("approvedHosts", profile.getAllowedHosts());
            value.put("authStrategy", profile.getAuth() != null ? profile.getAuth().getStrategy() : null);
            value.put("capabilityGrants", profile.getCapabilityGrants());
            return Map.copyOf(value);
        }).toList();
    }

    private List<Map<String, Object>> resourceBindings() {
        return config.getProtectedResources().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(entry -> {
            RestRoutingConfig.ProtectedResourceBinding binding = entry.getValue();
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("bindingId", entry.getKey());
            value.put("connectionProfileRef", binding.getConnectionProfileRef());
            value.put("environment", binding.getEnvironment());
            value.put("resourceType", binding.getResourceType());
            value.put("displayValue", StringUtils.hasText(binding.getDisplayValue()) ? binding.getDisplayValue() : null);
            value.put("fingerprint", protectedResources.fingerprint(binding));
            value.put("capabilityGrants", binding.getCapabilityGrants());
            value.values().removeIf(java.util.Objects::isNull);
            return Map.copyOf(value);
        }).toList();
    }

    private Map<String, Object> sourceSummary(String sourceId) {
        RestRoutingConfig.HttpDataSource source = config.getDataSources().get(sourceId);
        if (source == null) {
            throw new IllegalArgumentException("Unknown integration source: " + sourceId);
        }
        IntegrationStateRepository.SyncState state = syncService.state(sourceId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", sourceId);
        out.put("enabled", source.isEnabled());
        out.put("connectionProfileRef", source.getConnectionProfileRef());
        out.put("protectedResourceBindingRef", source.getProtectedResourceBindingRef());
        out.put("vectorSpace", source.getVectorSpace());
        out.put("entityType", source.getEntityType());
        out.put("scheduleSeconds", source.getScheduleSeconds());
        out.put("state", state);
        Long lagSeconds = state.lastSuccessAt() != null
            ? Math.max(0, Duration.between(state.lastSuccessAt(), Instant.now()).getSeconds())
            : null;
        out.put("lagSeconds", lagSeconds);
        out.put("freshnessState", lagSeconds == null
            ? "NOT_SYNCED"
            : lagSeconds > Math.max(60L, source.getScheduleSeconds() * 2L) ? "STALE" : "CURRENT");
        ProviderTokenService.TokenPosture tokenPosture = tokenService.posture().get(source.getConnectionProfileRef());
        out.put("preflightState", tokenPosture == null
            ? "NOT_VERIFIED"
            : "AUTH_FAILED".equals(tokenPosture.status()) ? "FAILED"
            : "READY".equals(tokenPosture.status()) ? "READY" : "NOT_VERIFIED");
        out.put("work", repository.workStates(sourceId).stream()
            .limit(100)
            .map(this::workSummary)
            .toList());
        out.values().removeIf(java.util.Objects::isNull);
        return Map.copyOf(out);
    }

    private Map<String, Object> webhookSummary(String sourceId) {
        RestRoutingConfig.WebhookSource source = config.getWebhooks().get(sourceId);
        List<IntegrationStateRepository.WebhookEvent> events = repository.recentEvents(sourceId, 20);
        boolean verifiedEventReceived = events.stream().anyMatch(event ->
            !"REJECTED".equalsIgnoreCase(event.status())
        );
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", sourceId);
        out.put("enabled", source.isEnabled());
        out.put("registrationState", !source.isEnabled()
            ? "DISABLED"
            : verifiedEventReceived ? "ACTIVE"
            : source.isRegistrationExpected() ? "PENDING_VERIFICATION" : "NOT_REGISTERED");
        out.put("manualReplayEnabled", source.isManualReplayEnabled());
        out.put("method", source.getMethod());
        out.put("signatureHeader", source.getVerification() != null ? source.getVerification().getSignatureHeader() : null);
        out.put("verificationConfigured", source.getVerification() != null && StringUtils.hasText(source.getVerification().getSecret()));
        out.put("reconcileDataSourceRef", source.getReconcileDataSourceRef());
        long validEvents = events.stream().filter(event -> !"REJECTED".equalsIgnoreCase(event.status())).count();
        long duplicates = events.stream().mapToLong(IntegrationStateRepository.WebhookEvent::duplicateCount).sum();
        long replays = events.stream().mapToLong(IntegrationStateRepository.WebhookEvent::replayCount).sum();
        out.put("counts", Map.of(
            "expected", source.isEnabled() ? 1L : 0L,
            "received", validEvents + duplicates,
            "rejected", repository.eventRejectionCount(sourceId),
            "duplicate", duplicates,
            "replayed", replays,
            "deadLetter", events.stream().filter(event -> "DEAD_LETTER".equalsIgnoreCase(event.status())).count()
        ));
        out.put("recentEvents", events.stream().map(this::webhookEventSummary).toList());
        out.values().removeIf(java.util.Objects::isNull);
        return Map.copyOf(out);
    }

    private Map<String, Object> workSummary(IntegrationStateRepository.IndexWorkState work) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("workId", work.workId());
        out.put("sourceId", work.sourceId());
        out.put("operation", work.operation());
        out.put("status", work.status());
        out.put("errorCode", work.errorCode());
        out.put("createdAt", work.createdAt());
        out.put("updatedAt", work.updatedAt());
        out.values().removeIf(java.util.Objects::isNull);
        return Map.copyOf(out);
    }

    private Map<String, Object> webhookEventSummary(IntegrationStateRepository.WebhookEvent event) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sourceId", event.sourceId());
        out.put("eventId", event.eventId());
        out.put("eventType", event.eventType());
        out.put("status", event.status());
        out.put("attemptCount", event.attemptCount());
        out.put("duplicateCount", event.duplicateCount());
        out.put("replayCount", event.replayCount());
        out.put("errorClass", event.errorClass());
        out.put("receivedAt", event.receivedAt());
        out.put("updatedAt", event.updatedAt());
        out.values().removeIf(java.util.Objects::isNull);
        return Map.copyOf(out);
    }
}
