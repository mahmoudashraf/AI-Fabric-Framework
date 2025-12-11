package com.ai.infrastructure.storage.strategy;

import com.ai.infrastructure.entity.AISearchableEntity;
import java.util.List;
import java.util.Optional;

/**
 * Strategy interface for persisting {@link AISearchableEntity}.
 */
public interface AISearchableEntityStorageStrategy {

    void save(AISearchableEntity entity);

    default void update(AISearchableEntity entity) {
        save(entity);
    }

    Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId);

    List<AISearchableEntity> findByEntityType(String entityType);

    Optional<AISearchableEntity> findByVectorId(String vectorId);

    void delete(AISearchableEntity entity);

    void deleteByEntityTypeAndEntityId(String entityType, String entityId);

    void deleteByEntityType(String entityType);

    void deleteByVectorId(String vectorId);

    void deleteByVectorIdIsNull();

    void deleteAll();

    List<AISearchableEntity> findByVectorIdIsNotNull();

    List<AISearchableEntity> findByVectorIdIsNull();

    long countByVectorIdIsNotNull();

    Optional<AISearchableEntity> findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc();

    List<AISearchableEntity> findByMetadataContainingSnippet(String snippet);

    List<AISearchableEntity> findByEntityTypeAndVectorIdIsNotNull(String entityType);

    List<AISearchableEntity> findByEntityTypeAndVectorIdIsNull(String entityType);

    boolean isHealthy();

    String getStrategyName();

    List<String> getSupportedEntityTypes();
}
