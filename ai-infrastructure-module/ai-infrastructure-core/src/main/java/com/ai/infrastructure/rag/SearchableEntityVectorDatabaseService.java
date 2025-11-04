package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.ai.infrastructure.util.MetadataJsonSerializer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decorator for {@link VectorDatabaseService} that keeps {@link AISearchableEntity} synchronized
 * with vector operations.
 */
@Slf4j
public class SearchableEntityVectorDatabaseService implements VectorDatabaseService {

    private final VectorDatabaseService delegate;
    private final AISearchableEntityRepository repository;
    private final AIEntityConfigurationLoader configurationLoader;

    public SearchableEntityVectorDatabaseService(VectorDatabaseService delegate,
                                                 AISearchableEntityRepository repository,
                                                 AIEntityConfigurationLoader configurationLoader) {
        this.delegate = delegate;
        this.repository = repository;
        this.configurationLoader = configurationLoader;
    }

    @Override
    public String storeVector(String entityType, String entityId, String content,
                              List<Double> embedding, Map<String, Object> metadata) {
        String vectorId = delegate.storeVector(entityType, entityId, content, embedding, metadata);
        registerRollbackCleanup(entityType, entityId, vectorId);
        upsertSearchableEntity(entityType, entityId, content, metadata, vectorId);
        return vectorId;
    }

    @Override
    public boolean updateVector(String vectorId, String entityType, String entityId,
                                String content, List<Double> embedding, Map<String, Object> metadata) {
        boolean updated = delegate.updateVector(vectorId, entityType, entityId, content, embedding, metadata);
        if (updated) {
            upsertSearchableEntity(entityType, entityId, content, metadata, vectorId);
        }
        return updated;
    }

    @Override
    public Optional<com.ai.infrastructure.dto.VectorRecord> getVector(String vectorId) {
        return delegate.getVector(vectorId);
    }

    @Override
    public Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
        return delegate.getVectorByEntity(entityType, entityId);
    }

    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        return delegate.search(queryVector, request);
    }

    @Override
    public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
        return delegate.searchByEntityType(queryVector, entityType, limit, threshold);
    }

    @Override
    public boolean removeVector(String entityType, String entityId) {
        boolean removed = delegate.removeVector(entityType, entityId);
        if (removed) {
            repository.deleteByEntityTypeAndEntityId(entityType, entityId);
        }
        return removed;
    }

    @Override
    public boolean removeVectorById(String vectorId) {
        boolean removed = delegate.removeVectorById(vectorId);
        if (removed) {
            repository.deleteByVectorId(vectorId);
        }
        return removed;
    }

    @Override
    public List<String> batchStoreVectors(List<VectorRecord> vectors) {
        List<String> vectorIds = delegate.batchStoreVectors(vectors);
        for (int i = 0; i < vectors.size(); i++) {
            VectorRecord record = vectors.get(i);
            if (i < vectorIds.size()) {
                registerRollbackCleanup(record.getEntityType(), record.getEntityId(), vectorIds.get(i));
                upsertSearchableEntity(record.getEntityType(), record.getEntityId(), record.getContent(),
                    record.getMetadata(), vectorIds.get(i));
            }
        }
        return vectorIds;
    }

    @Override
    public int batchUpdateVectors(List<VectorRecord> vectors) {
        int updated = delegate.batchUpdateVectors(vectors);
        for (VectorRecord record : vectors) {
            if (record.getVectorId() != null) {
                upsertSearchableEntity(record.getEntityType(), record.getEntityId(), record.getContent(),
                    record.getMetadata(), record.getVectorId());
            }
        }
        return updated;
    }

    @Override
    public int batchRemoveVectors(List<String> vectorIds) {
        int removed = delegate.batchRemoveVectors(vectorIds);
        vectorIds.forEach(repository::deleteByVectorId);
        return removed;
    }

    @Override
    public List<VectorRecord> getVectorsByEntityType(String entityType) {
        return delegate.getVectorsByEntityType(entityType);
    }

    @Override
    public long getVectorCountByEntityType(String entityType) {
        return delegate.getVectorCountByEntityType(entityType);
    }

    @Override
    public boolean vectorExists(String entityType, String entityId) {
        return delegate.vectorExists(entityType, entityId);
    }

    @Override
    public Map<String, Object> getStatistics() {
        return delegate.getStatistics();
    }

    @Override
    public long clearVectors() {
        long cleared = delegate.clearVectors();
        repository.deleteAll();
        return cleared;
    }

    @Override
    public long clearVectorsByEntityType(String entityType) {
        long cleared = delegate.clearVectorsByEntityType(entityType);
        repository.deleteByEntityType(entityType);
        return cleared;
    }

    private void upsertSearchableEntity(String entityType, String entityId, String content,
                                        Map<String, Object> metadata, String vectorId) {
        AISearchableEntity entity = repository.findByEntityTypeAndEntityId(entityType, entityId)
            .orElseGet(() -> AISearchableEntity.builder()
                .entityType(entityType)
                .entityId(entityId)
                .createdAt(LocalDateTime.now())
                .build());

        entity.setSearchableContent(content);
        entity.setVectorId(vectorId);
        entity.setVectorUpdatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        AIEntityConfig config = configurationLoader != null ? configurationLoader.getEntityConfig(entityType) : null;
        entity.setMetadata(MetadataJsonSerializer.serialize(metadata, config));

        repository.save(entity);
    }

    private void registerRollbackCleanup(String entityType, String entityId, String vectorId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        delegate.removeVectorById(vectorId);
                    } catch (Exception ex) {
                        log.warn("Unable to rollback vector {} for entity {}:{}", vectorId, entityType, entityId, ex);
                    }
                }
            }
        });
    }
}
