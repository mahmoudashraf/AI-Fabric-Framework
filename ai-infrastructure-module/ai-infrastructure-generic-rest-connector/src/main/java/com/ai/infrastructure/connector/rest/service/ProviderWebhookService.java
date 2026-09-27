package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.util.Hashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProviderWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProviderWebhookService.class);

    private final RestRoutingConfig config;
    private final WebhookVerificationService verificationService;
    private final ProtectedResourceService protectedResources;
    private final IntegrationStateRepository repository;
    private final HttpDataSyncService syncService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Set<String> runningEvents = ConcurrentHashMap.newKeySet();

    public ProviderWebhookService(
        RestRoutingConfig config,
        WebhookVerificationService verificationService,
        ProtectedResourceService protectedResources,
        IntegrationStateRepository repository,
        HttpDataSyncService syncService,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.config = config;
        this.verificationService = verificationService;
        this.protectedResources = protectedResources;
        this.repository = repository;
        this.syncService = syncService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public WebhookReceipt accept(
        String sourceId,
        String method,
        String contentType,
        String signatureHeader,
        byte[] rawBody
    ) {
        RestRoutingConfig.WebhookSource source = requireSource(sourceId);
        if (!source.getMethod().equalsIgnoreCase(method)) {
            return reject(sourceId, "WEBHOOK_METHOD_NOT_ALLOWED");
        }
        String normalizedContentType = normalizedContentType(contentType);
        if (!StringUtils.hasText(normalizedContentType)
            || source.getAllowedContentTypes() == null
            || source.getAllowedContentTypes().stream().map(this::normalizedContentType)
                .noneMatch(normalizedContentType::equals)) {
            return reject(sourceId, "WEBHOOK_CONTENT_TYPE_NOT_ALLOWED");
        }
        if (rawBody == null || rawBody.length == 0 || rawBody.length > source.getMaxBodyBytes()) {
            return reject(sourceId, "WEBHOOK_BODY_SIZE_INVALID");
        }
        WebhookVerificationService.VerificationResult verification = verificationService.verify(
            source.getVerification(), signatureHeader, rawBody
        );
        if (!verification.accepted()) {
            return reject(sourceId, verification.errorClass());
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            return reject(sourceId, "WEBHOOK_BODY_MALFORMED");
        }
        String eventId = scalar(root, source.getEventIdJsonPointer());
        String eventType = scalar(root, source.getEventTypeJsonPointer());
        if (!StringUtils.hasText(eventId) || !StringUtils.hasText(eventType)) {
            return reject(sourceId, "WEBHOOK_EVENT_IDENTITY_MISSING");
        }
        if (eventId.length() > 240 || eventType.length() > 160) {
            return reject(sourceId, "WEBHOOK_EVENT_IDENTITY_INVALID");
        }
        if (source.getAllowedEventTypes() == null || !source.getAllowedEventTypes().contains(eventType)) {
            return reject(sourceId, "WEBHOOK_EVENT_TYPE_NOT_ALLOWED");
        }
        RestRoutingConfig.ProtectedResourceBinding binding = protectedResources.requireBinding(
            source.getProtectedResourceBindingRef(),
            requireDataSource(source.getReconcileDataSourceRef()).getConnectionProfileRef()
        );
        String resource = scalar(root, source.getResourceJsonPointer());
        if (!binding.getResourceId().equals(resource)) {
            return reject(sourceId, "WEBHOOK_RESOURCE_MISMATCH");
        }
        IntegrationStateRepository.WebhookEvent event = new IntegrationStateRepository.WebhookEvent(
            sourceId,
            eventId,
            eventType,
            protectedResources.fingerprint(binding),
            Hashing.sha256Hex(rawBody),
            "ACCEPTED",
            0,
            0,
            0,
            null,
            clock.instant(),
            clock.instant()
        );
        if (!repository.registerEvent(event)) {
            IntegrationStateRepository.WebhookEvent existing = repository.event(sourceId, eventId).orElse(null);
            if (existing == null
                || !eventType.equals(existing.eventType())
                || !event.resourceFingerprint().equals(existing.resourceFingerprint())
                || !event.payloadSha256().equals(existing.payloadSha256())) {
                return reject(sourceId, "WEBHOOK_EVENT_ID_CONFLICT");
            }
            repository.markEventDuplicate(sourceId, eventId);
            return new WebhookReceipt(true, true, "DUPLICATE", eventId, null);
        }
        queueReconciliation(sourceId, eventId, source);
        return new WebhookReceipt(true, false, "ACCEPTED", eventId, null);
    }

    public WebhookReceipt replay(String sourceId, String eventId) {
        RestRoutingConfig.WebhookSource source = requireSource(sourceId);
        if (!source.isManualReplayEnabled()) {
            throw new ProviderCallException(
                ProviderErrorClass.RESOURCE_ACCESS_DENIED,
                0,
                "Manual webhook replay is disabled by the installed provider contract."
            );
        }
        IntegrationStateRepository.WebhookEvent event = repository.event(sourceId, eventId)
            .orElseThrow(() -> new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 404, "Webhook event was not found."));
        if ("REJECTED".equals(event.status())) {
            throw new ProviderCallException(
                ProviderErrorClass.BAD_REQUEST,
                409,
                "Rejected webhook evidence cannot be replayed. Send a new correctly authenticated event."
            );
        }
        repository.markEventReplay(sourceId, eventId);
        queueReconciliation(sourceId, eventId, source);
        return new WebhookReceipt(true, false, "REPLAY_QUEUED", event.eventId(), null);
    }

    @Scheduled(fixedDelayString = "${REST_CONNECTOR_WEBHOOK_RETRY_TICK_MS:10000}")
    public void retryFailedEvents() {
        config.getWebhooks().forEach((sourceId, source) -> {
            if (source == null || !source.isEnabled()) {
                return;
            }
            repository.recentEvents(sourceId, 500).stream()
                .filter(event -> Set.of("ACCEPTED", "RECONCILIATION_QUEUED", "FAILED_RETRYABLE")
                    .contains(event.status()))
                .filter(event -> event.attemptCount() < source.getMaxReconcileAttempts())
                .filter(event -> event.updatedAt() == null
                    || Duration.between(event.updatedAt(), clock.instant()).getSeconds() >= source.getRetryDelaySeconds())
                .forEach(event -> queueReconciliation(sourceId, event.eventId(), source));
        });
    }

    public List<IntegrationStateRepository.WebhookEvent> recent(String sourceId) {
        requireSource(sourceId);
        return repository.recentEvents(sourceId, 100);
    }

    private void queueReconciliation(String sourceId, String eventId, RestRoutingConfig.WebhookSource source) {
        String eventKey = sourceId + "\u0000" + eventId;
        if (!runningEvents.add(eventKey)) {
            return;
        }
        repository.beginEventAttempt(sourceId, eventId, "RECONCILIATION_QUEUED");
        syncService.reconcileAsync(source.getReconcileDataSourceRef()).whenComplete((state, failure) -> {
            try {
                if (failure == null && state != null && state.counts().failedWorkCount() == 0
                    && !"FAILED".equals(state.status()) && !"PARTIAL".equals(state.status())) {
                    repository.updateEvent(sourceId, eventId, "COMPLETED", null);
                    return;
                }
                IntegrationStateRepository.WebhookEvent current = repository.event(sourceId, eventId).orElse(null);
                boolean exhausted = current != null && current.attemptCount() >= source.getMaxReconcileAttempts();
                repository.updateEvent(
                    sourceId,
                    eventId,
                    exhausted ? "DEAD_LETTER" : "FAILED_RETRYABLE",
                    reconciliationErrorClass(state, failure)
                );
            } finally {
                runningEvents.remove(eventKey);
            }
        });
    }

    private String reconciliationErrorClass(
        IntegrationStateRepository.SyncState state,
        Throwable failure
    ) {
        Throwable cause = failure;
        while (cause != null && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof ProviderCallException providerFailure) {
            return providerFailure.errorClass().name();
        }
        if (state != null && StringUtils.hasText(state.errorClass())) {
            return state.errorClass();
        }
        return ProviderErrorClass.SERVICE_UNAVAILABLE.name();
    }

    private WebhookReceipt reject(String sourceId, String errorClass) {
        repository.recordEventRejection(sourceId, errorClass);
        LOGGER.warn("Rejected provider webhook sourceId={} errorClass={}", sourceId, errorClass);
        return WebhookReceipt.rejected(errorClass);
    }

    private String normalizedContentType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int separator = value.indexOf(';');
        String normalized = separator >= 0 ? value.substring(0, separator) : value;
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private RestRoutingConfig.WebhookSource requireSource(String sourceId) {
        RestRoutingConfig.WebhookSource source = StringUtils.hasText(sourceId) ? config.getWebhooks().get(sourceId.trim()) : null;
        if (source == null || !source.isEnabled()) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 404, "Webhook source is not configured.");
        }
        return source;
    }

    private RestRoutingConfig.HttpDataSource requireDataSource(String sourceId) {
        RestRoutingConfig.HttpDataSource source = StringUtils.hasText(sourceId) ? config.getDataSources().get(sourceId.trim()) : null;
        if (source == null) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0, "Webhook reconciliation source is not configured.");
        }
        return source;
    }

    private String scalar(JsonNode root, String pointer) {
        JsonNode node = StringUtils.hasText(pointer) ? root.at(pointer) : root;
        return node.isValueNode() ? node.asText("").trim() : "";
    }

    public record WebhookReceipt(
        boolean accepted,
        boolean duplicate,
        String status,
        String eventId,
        String errorClass
    ) {
        private static WebhookReceipt rejected(String errorClass) {
            return new WebhookReceipt(false, false, "REJECTED", null, errorClass);
        }
    }
}
