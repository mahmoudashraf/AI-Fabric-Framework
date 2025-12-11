package com.ai.infrastructure.storage.strategy;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class SingleTableStorageStrategy implements AISearchableEntityStorageStrategy {

    private final AISearchableEntityRepository repository;

    @Override
    public void save(AISearchableEntity entity) {
        if (entity == null) {
            return;
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
    }

    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        return repository.findByEntityTypeAndEntityId(entityType, entityId);
    }

    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        return repository.findByEntityType(entityType);
    }

    @Override
    public Optional<AISearchableEntity> findByVectorId(String vectorId) {
        return repository.findByVectorId(vectorId);
    }

    @Override
    public void delete(AISearchableEntity entity) {
        if (entity != null) {
            repository.delete(entity);
        }
    }

    @Override
    public void deleteByEntityTypeAndEntityId(String entityType, String entityId) {
        repository.deleteByEntityTypeAndEntityId(entityType, entityId);
    }

    @Override
    public void deleteByEntityType(String entityType) {
        repository.deleteByEntityType(entityType);
    }

    @Override
    public void deleteByVectorId(String vectorId) {
        repository.deleteByVectorId(vectorId);
    }

    @Override
    public void deleteByVectorIdIsNull() {
        repository.deleteByVectorIdIsNull();
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

    @Override
    public List<AISearchableEntity> findByVectorIdIsNotNull() {
        return repository.findByVectorIdIsNotNull();
    }

    @Override
    public List<AISearchableEntity> findByVectorIdIsNull() {
        return repository.findByVectorIdIsNull();
    }

    @Override
    public long countByVectorIdIsNotNull() {
        return repository.countByVectorIdIsNotNull();
    }

    @Override
    public Optional<AISearchableEntity> findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc() {
        return repository.findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc();
    }

    @Override
    public List<AISearchableEntity> findByMetadataContainingSnippet(String snippet) {
        return repository.findByMetadataContainingSnippet(snippet);
    }

    @Override
    public List<AISearchableEntity> findByEntityTypeAndVectorIdIsNotNull(String entityType) {
        return repository.findByEntityTypeAndVectorIdIsNotNull(entityType);
    }

    @Override
    public List<AISearchableEntity> findByEntityTypeAndVectorIdIsNull(String entityType) {
        return repository.findByEntityTypeAndVectorIdIsNull(entityType);
    }

    @Override
    public boolean isHealthy() {
        try {
            repository.count();
            return true;
        } catch (Exception ex) {
            log.warn("Single-table storage health check failed", ex);
            return false;
        }
    }

    @Override
    public String getStrategyName() {
        return "SINGLE_TABLE";
    }

    @Override
    public List<String> getSupportedEntityTypes() {
        return List.of("*");
    }
}
