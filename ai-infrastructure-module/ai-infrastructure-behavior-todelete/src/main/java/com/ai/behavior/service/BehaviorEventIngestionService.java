package com.ai.behavior.service;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorEventProcessingStatus;
import com.ai.behavior.repository.BehaviorEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorEventIngestionService {

    private final BehaviorEventRepository behaviorEventRepository;
    private final BehaviorModuleProperties properties;
    private final BehaviorAuditService auditService;
    private final BehaviorMetricsService metricsService;

    @Transactional
    public BehaviorEventEntity ingestSingleEvent(BehaviorEventEntity event) {
        Objects.requireNonNull(event, "event must not be null");
        BehaviorEventEntity prepared = applyDefaults(event);
        BehaviorEventEntity saved = behaviorEventRepository.save(prepared);
        metricsService.incrementEventIngested(1);
        auditService.logEventIngested(saved);
        log.debug("Stored behavior event {} for user {}", saved.getId(), saved.getUserId());
        return saved;
    }

    @Transactional
    public List<BehaviorEventEntity> ingestBatchEvents(List<BehaviorEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }
        int batchLimit = Math.max(1, properties.getEvents().getBatchSize());
        if (events.size() > batchLimit) {
            throw new IllegalArgumentException("Batch size exceeds limit of " + batchLimit);
        }
        List<BehaviorEventEntity> prepared = events.stream()
            .filter(Objects::nonNull)
            .map(this::applyDefaults)
            .toList();
        List<BehaviorEventEntity> saved = behaviorEventRepository.saveAll(prepared);
        metricsService.incrementEventIngested(saved.size());
        auditService.logBatchIngested(saved);
        log.info("Stored {} behavior events", saved.size());
        return saved;
    }

    @Transactional
    public void markProcessed(UUID eventId, BehaviorEventProcessingStatus status) {
        behaviorEventRepository.findById(eventId).ifPresent(event -> {
            event.setProcessed(true);
            event.setProcessingStatus(status != null ? status : BehaviorEventProcessingStatus.COMPLETED);
            behaviorEventRepository.save(event);
        });
    }

    @Transactional
    public void markFailed(UUID eventId, String reason) {
        behaviorEventRepository.findById(eventId).ifPresent(event -> {
            event.setProcessed(false);
            event.setProcessingStatus(BehaviorEventProcessingStatus.FAILED);
            event.setLastError(reason);
            event.setRetryCount(event.getRetryCount() + 1);
            behaviorEventRepository.save(event);
        });
    }

    @Transactional(readOnly = true)
    public List<BehaviorEventEntity> getUnprocessedEvents(int maxResults) {
        int size = Math.max(1, maxResults);
        Pageable pageable = PageRequest.of(0, size);
        return behaviorEventRepository.findUnprocessedEvents(pageable);
    }

    private BehaviorEventEntity applyDefaults(BehaviorEventEntity event) {
        if (event.getRetryCount() < 0) {
            event.setRetryCount(0);
        }
        if (event.getProcessingStatus() == null) {
            event.setProcessingStatus(BehaviorEventProcessingStatus.PENDING);
        }
        event.setProcessed(false);
        OffsetDateTime createdAt = event.getCreatedAt() != null ? event.getCreatedAt() : OffsetDateTime.now();
        event.setCreatedAt(createdAt);
        if (event.getExpiresAt() == null) {
            event.setExpiresAt(createdAt.plusDays(getTempTtlDays()));
        }
        return event;
    }

    private long getTempTtlDays() {
        int configured = properties.getRetention().getTempEventsTtlDays();
        return configured > 0 ? configured : 30;
    }
}
