package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyStoreVectorizationTriggerProperties;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationEventEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWebhookEventRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationAutomationSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationEventSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreVectorizationEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ShopifyStoreVectorizationEventService {

    private static final Logger log = LoggerFactory.getLogger(ShopifyStoreVectorizationEventService.class);

    private final ShopifyStoreVectorizationEventRepository repository;
    private final ShopifyStoreVectorizationTriggerProperties properties;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public ShopifyStoreVectorizationEventService(ShopifyStoreVectorizationEventRepository repository,
                                                 ShopifyStoreVectorizationTriggerProperties properties,
                                                 PlatformAuditService platformAuditService,
                                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.properties = properties;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ingest(ShopifyStoreConnectionEntity store, RecordShopifyStoreWebhookEventRequest request) {
        if (store == null || request == null || request.sourceCategory() == null || request.sourceCategory().isBlank()) {
            return;
        }
        String sourceCategory = ShopifyStoreVectorizationConstants.normalizeSourceCategory(request.sourceCategory());
        String entityType = ShopifyStoreVectorizationConstants.entityTypeForCategory(sourceCategory);
        Instant now = Instant.now();
        ShopifyStoreVectorizationEventEntity event = new ShopifyStoreVectorizationEventEntity();
        event.setId("sve-" + UUID.randomUUID().toString().substring(0, 8));
        event.setEventType("SHOPIFY_SOURCE_CHANGE");
        event.setEventSource("SHOPIFY_WEBHOOK");
        event.setSchemaVersion("shopify-indexing-event.v1");
        event.setShopDomain(store.getShopDomain().trim().toLowerCase(Locale.ROOT));
        event.setDeploymentId(store.getDeploymentId());
        event.setSourceCategory(sourceCategory);
        event.setEntityType(entityType);
        event.setSourceObjectId(trimToNull(request.sourceObjectId()));
        event.setOperation(ShopifyStoreVectorizationConstants.normalizeOperation(request.operation()));
        event.setAggregateKey(ShopifyStoreVectorizationConstants.aggregateKey(store.getShopDomain(), entityType));
        event.setDedupeKey(buildDedupeKey(store.getShopDomain(), request, entityType));
        event.setCorrelationId(firstNonBlank(request.shopifyWebhookId(), UUID.randomUUID().toString()));
        event.setCausationId(trimToNull(request.shopifyWebhookId()));
        event.setShopifyWebhookId(trimToNull(request.shopifyWebhookId()));
        event.setShopifyTopic(trimToNull(request.topic()));
        event.setDeliveryAttempt(request.deliveryAttempt());
        event.setPayloadChecksum(trimToNull(request.payloadChecksum()));
        event.setSourceRecordVersion(trimToNull(request.sourceRecordVersion()));
        event.setOccurredAt(now);
        event.setAvailableAt(now);
        event.setQueuedAt(now);
        event.setNextAttemptAt(now);
        event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_QUEUED);
        event.setAttemptCount(0);
        event.setRetentionExpiresAt(now.plus(properties.deadLetterRetention()));
        event.setPayloadJson(writeJson(Map.of(
            "topic", request.topic(),
            "eventType", request.eventType(),
            "sourceCategory", sourceCategory,
            "operation", event.getOperation(),
            "sourceObjectId", event.getSourceObjectId(),
            "sourceRecordVersion", event.getSourceRecordVersion(),
            "message", request.message()
        )));
        event.setHeadersJson(writeJson(Map.of(
            "shopifyWebhookId", request.shopifyWebhookId(),
            "deliveryAttempt", request.deliveryAttempt(),
            "payloadChecksum", request.payloadChecksum()
        )));
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        try {
            repository.save(event);
            platformAuditService.record(
                "SHOPIFY_STORE_VECTORIZATION_EVENT_INGESTED",
                "SHOPIFY_STORE_CONNECTION",
                store.getShopDomain(),
                Map.of(
                    "shopDomain", store.getShopDomain(),
                    "eventId", event.getId(),
                    "aggregateKey", event.getAggregateKey(),
                    "sourceCategory", sourceCategory,
                    "entityType", entityType,
                    "operation", event.getOperation()
                )
            );
        } catch (DataIntegrityViolationException duplicate) {
            log.info("Shopify vectorization event deduped: shopDomain={}, dedupeKey={}", store.getShopDomain(), event.getDedupeKey());
        }
    }

    @Transactional(readOnly = true)
    public List<ShopifyStoreVectorizationEventSummary> listRecent(String shopDomain, int limit) {
        int normalizedLimit = Math.max(1, Math.min(limit, properties.recentEventsLimit()));
        return repository.findTop100ByShopDomainOrderByOccurredAtDesc(shopDomain).stream()
            .limit(normalizedLimit)
            .map(this::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public ShopifyStoreVectorizationAutomationSummary summarizeAutomation(String shopDomain) {
        Collection<String> queued = List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_QUEUED);
        Collection<String> leased = List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_LEASED);
        Collection<String> dispatched = List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_DISPATCHED);
        Collection<String> skipped = List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_SKIPPED);
        Collection<String> failed = List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_FAILED);
        Collection<String> dead = List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_DEAD_LETTERED);

        ShopifyStoreVectorizationEventEntity lastAny = repository.findTopByShopDomainAndStatusInOrderByOccurredAtDesc(
            shopDomain,
            List.of(
                ShopifyStoreVectorizationConstants.EVENT_STATUS_QUEUED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_LEASED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_DISPATCHED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_COMPLETED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_SKIPPED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_FAILED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_DEAD_LETTERED
            )
        ).orElse(null);
        ShopifyStoreVectorizationEventEntity lastSuccess = repository.findTopByShopDomainAndStatusInOrderByOccurredAtDesc(
            shopDomain,
            List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_COMPLETED)
        ).orElse(null);
        ShopifyStoreVectorizationEventEntity lastFailure = repository.findTopByShopDomainAndStatusInOrderByOccurredAtDesc(
            shopDomain,
            List.of(ShopifyStoreVectorizationConstants.EVENT_STATUS_FAILED, ShopifyStoreVectorizationConstants.EVENT_STATUS_DEAD_LETTERED)
        ).orElse(null);

        List<String> degradedReasons = new ArrayList<>();
        if (repository.countByShopDomainAndStatusIn(shopDomain, dead) > 0) {
            degradedReasons.add("Dead-lettered live update events require operator recovery.");
        }
        if (repository.countByShopDomainAndStatusIn(shopDomain, failed) > 0) {
            degradedReasons.add("Failed live update events are waiting for retry.");
        }

        return new ShopifyStoreVectorizationAutomationSummary(
            degradedReasons.isEmpty(),
            (int) repository.countByShopDomainAndStatusIn(shopDomain, queued),
            (int) repository.countByShopDomainAndStatusIn(shopDomain, leased),
            (int) repository.countByShopDomainAndStatusIn(shopDomain, dispatched),
            (int) repository.countByShopDomainAndStatusIn(shopDomain, skipped),
            (int) repository.countByShopDomainAndStatusIn(shopDomain, failed),
            (int) repository.countByShopDomainAndStatusIn(shopDomain, dead),
            lastAny == null ? null : lastAny.getOccurredAt(),
            lastSuccess == null ? null : lastSuccess.getCompletedAt(),
            lastFailure == null ? null : lastFailure.getOccurredAt(),
            lastSuccess == null ? null : lastSuccess.getCoalescedRunId(),
            List.copyOf(degradedReasons)
        );
    }

    @Transactional(readOnly = true)
    public List<String> nextAggregateKeys(Instant now, int limit) {
        LinkedHashSet<String> aggregateKeys = new LinkedHashSet<>();
        for (ShopifyStoreVectorizationEventEntity event : repository.findAvailableForLease(
            List.of(
                ShopifyStoreVectorizationConstants.EVENT_STATUS_QUEUED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_FAILED
            ),
            now
        )) {
            aggregateKeys.add(event.getAggregateKey());
            if (aggregateKeys.size() >= limit) {
                break;
            }
        }
        return List.copyOf(aggregateKeys);
    }

    @Transactional
    public List<ShopifyStoreVectorizationEventEntity> leaseAggregate(String aggregateKey, String leaseOwner, Instant leaseExpiresAt) {
        List<ShopifyStoreVectorizationEventEntity> events = repository.findByAggregateKeyForUpdate(
            aggregateKey,
            ShopifyStoreVectorizationConstants.ACTIVE_EVENT_STATUSES
        );
        boolean leasedByOther = events.stream().anyMatch(event ->
            ShopifyStoreVectorizationConstants.EVENT_STATUS_LEASED.equals(event.getStatus())
                && event.getLeaseExpiresAt() != null
                && event.getLeaseExpiresAt().isAfter(Instant.now())
                && !leaseOwner.equals(event.getLeaseOwner())
        );
        if (leasedByOther) {
            return List.of();
        }
        Instant now = Instant.now();
        events.forEach(event -> {
            event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_LEASED);
            event.setLeaseOwner(leaseOwner);
            event.setLeaseExpiresAt(leaseExpiresAt);
            event.setLastAttemptAt(now);
            event.setUpdatedAt(now);
            repository.save(event);
        });
        return List.copyOf(events);
    }

    @Transactional
    public void requeue(List<ShopifyStoreVectorizationEventEntity> events, Instant nextAttemptAt, String notes) {
        Instant now = Instant.now();
        for (ShopifyStoreVectorizationEventEntity event : events) {
            event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_QUEUED);
            event.setLeaseOwner(null);
            event.setLeaseExpiresAt(null);
            event.setNextAttemptAt(nextAttemptAt);
            event.setNotes(notes);
            event.setUpdatedAt(now);
            repository.save(event);
        }
    }

    @Transactional
    public void markSkipped(ShopifyStoreVectorizationEventEntity event, String notes) {
        markTerminal(event, ShopifyStoreVectorizationConstants.EVENT_STATUS_SKIPPED, notes, null, null, properties.completedRetention());
    }

    @Transactional
    public void markCoalesced(ShopifyStoreVectorizationEventEntity event, String runId, String notes) {
        markTerminal(event, ShopifyStoreVectorizationConstants.EVENT_STATUS_COALESCED, notes, null, runId, properties.completedRetention());
    }

    @Transactional
    public void markDispatched(ShopifyStoreVectorizationEventEntity event, String triggerReason, String runId, String notes) {
        Instant now = Instant.now();
        event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_DISPATCHED);
        event.setTriggerReason(triggerReason);
        event.setCoalescedRunId(runId);
        event.setNotes(notes);
        event.setLeaseOwner(null);
        event.setLeaseExpiresAt(null);
        event.setCompletedAt(null);
        event.setRetentionExpiresAt(now.plus(properties.completedRetention()));
        event.setUpdatedAt(now);
        repository.save(event);
    }

    @Transactional
    public void markRunCompletion(String runId, boolean success, String notes, String failureCode) {
        Instant now = Instant.now();
        for (ShopifyStoreVectorizationEventEntity event : repository.findByCoalescedRunId(runId)) {
            if (!ShopifyStoreVectorizationConstants.EVENT_STATUS_DISPATCHED.equals(event.getStatus())
                && !ShopifyStoreVectorizationConstants.EVENT_STATUS_COALESCED.equals(event.getStatus())) {
                continue;
            }
            if (success) {
                markTerminal(event, ShopifyStoreVectorizationConstants.EVENT_STATUS_COMPLETED, notes, null, runId, properties.completedRetention());
                continue;
            }
            failEvent(event, failureCode, notes);
        }
    }

    @Transactional
    public void failEvent(ShopifyStoreVectorizationEventEntity event, String failureCode, String summary) {
        Instant now = Instant.now();
        int attempts = event.getAttemptCount() + 1;
        event.setAttemptCount(attempts);
        event.setFailureCode(failureCode);
        event.setLastErrorSummary(summary);
        event.setNotes(summary);
        event.setLeaseOwner(null);
        event.setLeaseExpiresAt(null);
        event.setLastAttemptAt(now);
        event.setUpdatedAt(now);
        if (attempts >= properties.maxAttempts()) {
            event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_DEAD_LETTERED);
            event.setDeadLetteredAt(now);
            event.setCompletedAt(now);
            event.setRetentionExpiresAt(now.plus(properties.deadLetterRetention()));
        } else {
            event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_FAILED);
            event.setNextAttemptAt(now.plus(properties.retryBackoff()));
        }
        repository.save(event);
    }

    @Transactional
    public void replay(String shopDomain, String eventId) {
        ShopifyStoreVectorizationEventEntity event = repository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Shopify vectorization event not found: " + eventId));
        if (!shopDomain.equalsIgnoreCase(event.getShopDomain())) {
            throw new IllegalArgumentException("Shopify vectorization event does not belong to store " + shopDomain + ".");
        }
        Instant now = Instant.now();
        event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_QUEUED);
        event.setFailureCode(null);
        event.setLastErrorSummary(null);
        event.setDeadLetteredAt(null);
        event.setLeaseOwner(null);
        event.setLeaseExpiresAt(null);
        event.setCompletedAt(null);
        event.setNextAttemptAt(now);
        event.setUpdatedAt(now);
        repository.save(event);
    }

    @Transactional
    public int replayRunEvents(String shopDomain, String runId) {
        int replayed = 0;
        Instant now = Instant.now();
        for (ShopifyStoreVectorizationEventEntity event : repository.findByCoalescedRunId(runId)) {
            if (!shopDomain.equalsIgnoreCase(event.getShopDomain())) {
                continue;
            }
            event.setStatus(ShopifyStoreVectorizationConstants.EVENT_STATUS_QUEUED);
            event.setFailureCode(null);
            event.setLastErrorSummary(null);
            event.setDeadLetteredAt(null);
            event.setLeaseOwner(null);
            event.setLeaseExpiresAt(null);
            event.setCompletedAt(null);
            event.setNextAttemptAt(now);
            event.setUpdatedAt(now);
            repository.save(event);
            replayed++;
        }
        return replayed;
    }

    @Transactional
    public void pruneExpired() {
        for (ShopifyStoreVectorizationEventEntity event : repository.findByStatusInAndRetentionExpiresAtBefore(
            List.of(
                ShopifyStoreVectorizationConstants.EVENT_STATUS_COALESCED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_COMPLETED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_SKIPPED,
                ShopifyStoreVectorizationConstants.EVENT_STATUS_DEAD_LETTERED
            ),
            Instant.now()
        )) {
            repository.delete(event);
        }
    }

    private void markTerminal(ShopifyStoreVectorizationEventEntity event,
                              String status,
                              String notes,
                              String failureCode,
                              String runId,
                              java.time.Duration retention) {
        Instant now = Instant.now();
        event.setStatus(status);
        event.setFailureCode(failureCode);
        event.setNotes(notes);
        event.setCoalescedRunId(runId == null ? event.getCoalescedRunId() : runId);
        event.setLeaseOwner(null);
        event.setLeaseExpiresAt(null);
        event.setCompletedAt(now);
        event.setRetentionExpiresAt(now.plus(retention));
        event.setUpdatedAt(now);
        repository.save(event);
    }

    private ShopifyStoreVectorizationEventSummary toSummary(ShopifyStoreVectorizationEventEntity event) {
        return new ShopifyStoreVectorizationEventSummary(
            event.getId(),
            event.getSourceCategory(),
            event.getEntityType(),
            event.getSourceObjectId(),
            event.getShopifyTopic(),
            event.getOperation(),
            event.getStatus(),
            event.getTriggerReason(),
            event.getFailureCode(),
            event.getCoalescedRunId(),
            event.getShopifyWebhookId(),
            event.getOccurredAt(),
            event.getQueuedAt(),
            event.getLastAttemptAt(),
            event.getCompletedAt(),
            event.getNotes()
        );
    }

    private String buildDedupeKey(String shopDomain, RecordShopifyStoreWebhookEventRequest request, String entityType) {
        return sha256(String.join("|",
            normalize(shopDomain),
            normalize(entityType),
            normalize(request.sourceCategory()),
            normalize(request.operation()),
            normalize(request.sourceObjectId()),
            normalize(firstNonBlank(request.shopifyWebhookId(), request.payloadChecksum(), request.topic()))
        ));
    }

    private String writeJson(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Shopify vectorization event metadata.", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute Shopify vectorization dedupe hash.", ex);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
