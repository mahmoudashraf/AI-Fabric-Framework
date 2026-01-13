package com.ai.infrastructure.cleanup;

import com.ai.infrastructure.config.AICleanupProperties;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import com.ai.infrastructure.service.VectorManagementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scheduled cleanup routines covering orphaned vectors, failed indexing attempts,
 * and long-term retention policies.
 */
@Slf4j
@RequiredArgsConstructor
public class SearchableEntityCleanupScheduler {

    private final AICleanupProperties properties;
    private final CleanupPolicyProvider policyProvider;
    private final AISearchableEntityStorageStrategy storageStrategy;
    private final VectorManagementService vectorManagementService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Scheduled(cron = "${ai.cleanup.orphaned-entities.cron:0 0 4 * * SUN}")
    @Transactional
    public void cleanupOrphanedEntities() {
        if (!properties.isEnabled() || !properties.getOrphanedEntities().isEnabled()) {
            return;
        }

        List<AISearchableEntity> entities = storageStrategy.findByVectorIdIsNotNull();
        if (CollectionUtils.isEmpty(entities)) {
            return;
        }

        int orphaned = 0;
        for (AISearchableEntity entity : entities) {
            if (!vectorExists(entity)) {
                deleteEntity(entity);
                orphaned++;
            }
        }
        if (orphaned > 0) {
            log.info("Cleaned {} orphaned searchable entities", orphaned);
        }
    }

    @Scheduled(cron = "${ai.cleanup.no-vector-entities.cron:0 0 5 * * SUN}")
    @Transactional
    public void cleanupEntitiesWithoutVectors() {
        if (!properties.isEnabled() || !properties.getNoVectorEntities().isEnabled()) {
            return;
        }

        Duration retention = properties.getNoVectorEntities().getRetention();
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(retention);

        List<AISearchableEntity> entities = storageStrategy.findByVectorIdIsNull();
        if (CollectionUtils.isEmpty(entities)) {
            return;
        }

        int cleaned = 0;
        for (AISearchableEntity entity : entities) {
            if (shouldCleanup(entity.getCreatedAt(), cutoff)) {
                deleteEntity(entity);
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.info("Removed {} stale searchable entities without vectors", cleaned);
        }
    }

    @Scheduled(cron = "${ai.cleanup.retention-cron:0 30 3 * * *}")
    @Transactional
    public void cleanupByRetentionPolicy() {
        if (!properties.isEnabled()) {
            return;
        }

        for (Map.Entry<String, Integer> entry : properties.getRetentionDays().entrySet()) {
            String entityType = entry.getKey();
            if ("default".equalsIgnoreCase(entityType)) {
                continue;
            }
            int retentionDays = entry.getValue();
            LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);

            List<AISearchableEntity> entities = storageStrategy.findByEntityType(entityType);
            for (AISearchableEntity entity : entities) {
                if (shouldCleanup(entity.getCreatedAt(), cutoff)) {
                    applyPolicy(entityType, entity);
                }
            }
        }
    }

    private void applyPolicy(String entityType, AISearchableEntity entity) {
        CleanupStrategy strategy = policyProvider.getStrategy(entityType);
        switch (strategy) {
            case SOFT_DELETE -> softDelete(entity);
            case ARCHIVE -> archiveEntity(entity);
            case HARD_DELETE, CASCADE -> deleteEntity(entity);
        }
    }

    private void softDelete(AISearchableEntity entity) {
        evictVector(entity);
        ObjectNode metadataNode = readMetadata(entity.getMetadata());
        metadataNode.put("_softDeleted", true);
        metadataNode.put("_deletedAt", LocalDateTime.now(clock).toString());
        entity.setMetadata(metadataNode.toString());
        entity.setSearchableContent(null);
        entity.setVectorId(null);
        entity.setVectorUpdatedAt(null);
        entity.setUpdatedAt(LocalDateTime.now(clock));
        storageStrategy.save(entity);
        log.debug("Soft deleted searchable entity {}:{}", entity.getEntityType(), entity.getEntityId());
    }

    private void archiveEntity(AISearchableEntity entity) {
        evictVector(entity);
        storageStrategy.delete(entity);
        log.debug("Archived searchable entity {}:{}", entity.getEntityType(), entity.getEntityId());
    }

    private void deleteEntity(AISearchableEntity entity) {
        evictVector(entity);
        storageStrategy.delete(entity);
        log.debug("Deleted searchable entity {}:{}", entity.getEntityType(), entity.getEntityId());
    }

    private void evictVector(AISearchableEntity entity) {
        if (entity == null || entity.getEntityType() == null || entity.getEntityId() == null) {
            return;
        }
        try {
            vectorManagementService.removeVector(entity.getEntityType(), entity.getEntityId());
        } catch (Exception ex) {
            log.warn("Failed removing vector for {}:{}", entity.getEntityType(), entity.getEntityId(), ex);
        }
    }

    private boolean vectorExists(AISearchableEntity entity) {
        if (entity == null || entity.getEntityType() == null || entity.getEntityId() == null) {
            return false;
        }
        return vectorManagementService.vectorExists(entity.getEntityType(), entity.getEntityId());
    }

    private boolean shouldCleanup(LocalDateTime timestamp, LocalDateTime cutoff) {
        return timestamp != null && timestamp.isBefore(cutoff);
    }

    private ObjectNode readMetadata(String metadata) {
        try {
            if (metadata != null) {
                JsonNode node = objectMapper.readTree(metadata);
                if (node instanceof ObjectNode objectNode) {
                    return objectNode;
                }
            }
        } catch (Exception ignored) {
        }
        return objectMapper.createObjectNode();
    }
}
