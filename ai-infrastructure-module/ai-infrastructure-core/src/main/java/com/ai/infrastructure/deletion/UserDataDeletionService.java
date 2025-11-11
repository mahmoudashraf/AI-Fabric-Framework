package com.ai.infrastructure.deletion;

import com.ai.infrastructure.audit.AIAuditService;
import com.ai.infrastructure.audit.AuditService;
import com.ai.infrastructure.deletion.policy.UserDataDeletionProvider;
import com.ai.infrastructure.deletion.policy.UserDataDeletionProvider.UserEntityReference;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.repository.BehaviorRepository;
import com.ai.infrastructure.rag.VectorDatabaseService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Infrastructure service orchestrating GDPR/CCPA style user deletions by delegating domain logic
 * to {@link UserDataDeletionProvider} hooks while handling infrastructure owned data stores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDataDeletionService {

    private final BehaviorRepository behaviorRepository;
    private final AISearchableEntityRepository searchableEntityRepository;
    private final VectorDatabaseService vectorDatabaseService;
    private final AIAuditService aiAuditService;
    private final AuditService auditLogger;
    private final Clock clock;
    private final UserDataDeletionProvider userDataDeletionProvider;

    /**
     * Execute a full deletion workflow for the supplied user identifier.
     *
     * @param userId unique identifier of the user whose data should be deleted
     * @return structured summary of the deletion
     */
    public UserDataDeletionResult deleteUser(String userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        UserDataDeletionProvider provider = requireProvider();

        if (!provider.canDeleteUser(userId)) {
            log.info("User {} cannot be deleted according to provider policy", userId);
            return UserDataDeletionResult.builder()
                .userId(userId)
                .status(UserDataDeletionResult.Status.SKIPPED)
                .timestamp(LocalDateTime.now(clock))
                .message("Deletion blocked by UserDataDeletionProvider.canDeleteUser")
                .build();
        }

        LocalDateTime timestamp = LocalDateTime.now(clock);
        int behaviorsDeleted = deleteBehaviors(userId);
        IndexedDeletionStats indexedDeletionStats = deleteIndexedEntities(userId, provider);
        int domainRecordsDeleted = safelyDeleteDomainData(provider, userId);
        int auditEntriesDeleted = deleteAuditTrail(userId);

        notifyProvider(provider, userId);
        logDeletionEvent(userId, behaviorsDeleted, indexedDeletionStats, domainRecordsDeleted, auditEntriesDeleted, timestamp);

        return UserDataDeletionResult.builder()
            .userId(userId)
            .status(UserDataDeletionResult.Status.COMPLETED)
            .behaviorsDeleted(behaviorsDeleted)
            .indexedEntitiesDeleted(indexedDeletionStats.entitiesDeleted())
            .vectorsDeleted(indexedDeletionStats.vectorsDeleted())
            .domainRecordsDeleted(domainRecordsDeleted)
            .auditEntriesDeleted(auditEntriesDeleted)
            .timestamp(timestamp)
            .build();
    }

    private UserDataDeletionProvider requireProvider() {
        if (userDataDeletionProvider == null) {
            throw new IllegalStateException("""
                No UserDataDeletionProvider bean available. Register an implementation of \
                com.ai.infrastructure.deletion.policy.UserDataDeletionProvider to enable deletion workflows.""");
        }
        return userDataDeletionProvider;
    }

    private int deleteBehaviors(String userId) {
        try {
            UUID userUuid = UUID.fromString(userId);
            List<Behavior> behaviors = behaviorRepository.findByUserIdOrderByCreatedAtDesc(userUuid);
            if (CollectionUtils.isEmpty(behaviors)) {
                return 0;
            }
            behaviorRepository.deleteAllInBatch(behaviors);
            return behaviors.size();
        } catch (IllegalArgumentException ex) {
            log.debug("Skipping behavior deletion: userId {} is not a UUID", userId);
            return 0;
        } catch (Exception ex) {
            log.warn("Failed to delete behavior history for user {}: {}", userId, ex.getMessage());
            return 0;
        }
    }

    private IndexedDeletionStats deleteIndexedEntities(String userId, UserDataDeletionProvider provider) {
        Set<String> processedKeys = new HashSet<>();
        AtomicInteger entitiesDeleted = new AtomicInteger();
        AtomicInteger vectorsDeleted = new AtomicInteger();

        List<UserEntityReference> references = Optional.ofNullable(provider.findIndexedEntities(userId))
            .orElse(List.of());
        references.forEach(ref -> removeReference(ref, processedKeys, entitiesDeleted, vectorsDeleted));

        // Attempt a metadata fallback search
        String metadataSnippet = "\"" + userId + "\"";
        List<AISearchableEntity> metadataMatches = searchableEntityRepository.findByMetadataContainingSnippet(metadataSnippet);
        if (!CollectionUtils.isEmpty(metadataMatches)) {
            metadataMatches.forEach(entity -> {
                String key = entity.getEntityType() + "::" + entity.getEntityId();
                if (processedKeys.add(key)) {
                    removeVector(entity.getEntityType(), entity.getEntityId(), vectorsDeleted);
                    searchableEntityRepository.deleteByEntityTypeAndEntityId(entity.getEntityType(), entity.getEntityId());
                    entitiesDeleted.incrementAndGet();
                }
            });
        }

        return new IndexedDeletionStats(entitiesDeleted.get(), vectorsDeleted.get());
    }

    private void removeReference(UserEntityReference reference,
                                 Set<String> processedKeys,
                                 AtomicInteger entitiesDeleted,
                                 AtomicInteger vectorsDeleted) {
        if (reference == null || !StringUtils.hasText(reference.entityType()) || !StringUtils.hasText(reference.entityId())) {
            return;
        }
        String cacheKey = reference.entityType() + "::" + reference.entityId();
        if (!processedKeys.add(cacheKey)) {
            return;
        }
        try {
            removeVector(reference.entityType(), reference.entityId(), vectorsDeleted);
            searchableEntityRepository.deleteByEntityTypeAndEntityId(reference.entityType(), reference.entityId());
            entitiesDeleted.incrementAndGet();
        } catch (Exception ex) {
            log.warn("Failed to remove indexed entity {}:{} - {}", reference.entityType(), reference.entityId(), ex.getMessage());
        }
    }

    private void removeVector(String entityType, String entityId, AtomicInteger vectorsDeleted) {
        try {
            if (vectorDatabaseService.removeVector(entityType, entityId)) {
                vectorsDeleted.incrementAndGet();
            }
        } catch (Exception ex) {
            log.debug("Vector removal failed for {}:{} - {}", entityType, entityId, ex.getMessage());
        }
    }

    private int safelyDeleteDomainData(UserDataDeletionProvider provider, String userId) {
        try {
            return provider.deleteUserDomainData(userId);
        } catch (Exception ex) {
            log.warn("UserDataDeletionProvider.deleteUserDomainData failed for user {}: {}", userId, ex.getMessage());
            return 0;
        }
    }

    private int deleteAuditTrail(String userId) {
        try {
            List<?> existing = aiAuditService.getAuditLogs(userId);
            int count = existing != null ? existing.size() : 0;
            aiAuditService.clearAuditLogs(userId);
            return count;
        } catch (Exception ex) {
            log.warn("Failed to clear audit logs for user {}: {}", userId, ex.getMessage());
            return 0;
        }
    }

    private void notifyProvider(UserDataDeletionProvider provider, String userId) {
        try {
            provider.notifyAfterDeletion(userId);
        } catch (Exception ex) {
            log.debug("UserDataDeletionProvider.notifyAfterDeletion threw for user {}: {}", userId, ex.getMessage());
        }
    }

    private void logDeletionEvent(String userId,
                                  int behaviorsDeleted,
                                  IndexedDeletionStats indexedDeletionStats,
                                  int domainRecordsDeleted,
                                  int auditEntriesDeleted,
                                  LocalDateTime timestamp) {
        auditLogger.logOperation(
            "delete-" + userId,
            userId,
            "USER_DATA_DELETION",
            List.of(
                "behaviors=" + behaviorsDeleted,
                "indexedEntities=" + indexedDeletionStats.entitiesDeleted(),
                "vectors=" + indexedDeletionStats.vectorsDeleted(),
                "domainRecords=" + domainRecordsDeleted,
                "auditEntries=" + auditEntriesDeleted
            ),
            timestamp
        );
    }

    private record IndexedDeletionStats(int entitiesDeleted, int vectorsDeleted) { }
}
