package com.ai.infrastructure.storage.strategy.impl;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
@RequiredArgsConstructor
public class PerTypeTableStorageStrategy implements AISearchableEntityStorageStrategy {

    private final PerTypeRepositoryFactory repositoryFactory;

    @Override
    public void save(AISearchableEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getEntityType())) {
            return;
        }
        repositoryFactory.getRepositoryForType(entity.getEntityType()).save(entity);
    }

    @Override
    public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
        if (!StringUtils.hasText(entityType)) {
            return Optional.empty();
        }
        return repositoryFactory.getRepositoryForType(entityType).findByEntityId(entityId);
    }

    @Override
    public List<AISearchableEntity> findByEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return List.of();
        }
        return repositoryFactory.getRepositoryForType(entityType).findAll();
    }

    @Override
    public Optional<AISearchableEntity> findByVectorId(String vectorId) {
        if (!StringUtils.hasText(vectorId)) {
            return Optional.empty();
        }
        for (PerTypeRepository repo : repositoryFactory.getAllRepositories().values()) {
            Optional<AISearchableEntity> found = repo.findByVectorId(vectorId);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    @Override
    public void delete(AISearchableEntity entity) {
        if (entity == null || !StringUtils.hasText(entity.getEntityType())) {
            return;
        }
        repositoryFactory.getRepositoryForType(entity.getEntityType()).delete(entity);
    }

    @Override
    public void deleteByEntityTypeAndEntityId(String entityType, String entityId) {
        if (!StringUtils.hasText(entityType)) {
            return;
        }
        repositoryFactory.getRepositoryForType(entityType).deleteByEntityId(entityId);
    }

    @Override
    public void deleteByEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return;
        }
        repositoryFactory.getRepositoryForType(entityType).deleteAll();
    }

    @Override
    public void deleteByVectorId(String vectorId) {
        for (PerTypeRepository repo : repositoryFactory.getAllRepositories().values()) {
            if (repo.deleteByVectorId(vectorId) > 0) {
                return;
            }
        }
    }

    @Override
    public void deleteByVectorIdIsNull() {
        repositoryFactory.getAllRepositories().values()
            .forEach(PerTypeRepository::deleteByVectorIdIsNull);
    }

    @Override
    public void deleteAll() {
        repositoryFactory.getAllRepositories().values()
            .forEach(PerTypeRepository::deleteAll);
    }

    @Override
    public List<AISearchableEntity> findByVectorIdIsNotNull() {
        return repositoryFactory.getAllRepositories().values()
            .stream()
            .flatMap(repo -> repo.findByVectorIdIsNotNull().stream())
            .collect(Collectors.toList());
    }

    @Override
    public List<AISearchableEntity> findByVectorIdIsNull() {
        return repositoryFactory.getAllRepositories().values()
            .stream()
            .flatMap(repo -> repo.findByVectorIdIsNull().stream())
            .collect(Collectors.toList());
    }

    @Override
    public long countByVectorIdIsNotNull() {
        return repositoryFactory.getAllRepositories().values()
            .stream()
            .mapToLong(PerTypeRepository::countWithVectors)
            .sum();
    }

    @Override
    public Optional<AISearchableEntity> findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc() {
        List<AISearchableEntity> latestPerType = new ArrayList<>();
        for (PerTypeRepository repo : repositoryFactory.getAllRepositories().values()) {
            repo.findLatestWithVector().ifPresent(latestPerType::add);
        }
        return latestPerType.stream()
            .filter(e -> e.getVectorUpdatedAt() != null)
            .max(Comparator.comparing(AISearchableEntity::getVectorUpdatedAt));
    }

    @Override
    public List<AISearchableEntity> findByMetadataContainingSnippet(String snippet) {
        if (!StringUtils.hasText(snippet)) {
            return List.of();
        }
        return repositoryFactory.getAllRepositories().values()
            .stream()
            .flatMap(repo -> repo.findByMetadataContainingSnippet(snippet).stream())
            .collect(Collectors.toList());
    }

    @Override
    public List<AISearchableEntity> findByEntityTypeAndVectorIdIsNotNull(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return List.of();
        }
        return repositoryFactory.getRepositoryForType(entityType).findByVectorIdIsNotNull();
    }

    @Override
    public List<AISearchableEntity> findByEntityTypeAndVectorIdIsNull(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return List.of();
        }
        return repositoryFactory.getRepositoryForType(entityType).findByVectorIdIsNull();
    }

    @Override
    public boolean isHealthy() {
        try {
            for (PerTypeRepository repo : repositoryFactory.getAllRepositories().values()) {
                repo.count();
            }
            return true;
        } catch (Exception ex) {
            log.warn("Per-type storage health check failed", ex);
            return false;
        }
    }

    @Override
    public String getStrategyName() {
        return "PER_TYPE_TABLE";
    }

    @Override
    public List<String> getSupportedEntityTypes() {
        return repositoryFactory.getKnownEntityTypes();
    }
}
