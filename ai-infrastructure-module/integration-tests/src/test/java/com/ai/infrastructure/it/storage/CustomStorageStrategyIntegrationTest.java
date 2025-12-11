package com.ai.infrastructure.it.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.it.TestApplication;
import com.ai.infrastructure.storage.strategy.AISearchableEntityStorageStrategy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = {TestApplication.class, CustomStorageStrategyIntegrationTest.CustomStorageConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "ai-infrastructure.storage.strategy=CUSTOM")
class CustomStorageStrategyIntegrationTest {

    @TestConfiguration
    static class CustomStorageConfig {
        @Bean
        AISearchableEntityStorageStrategy customStorageStrategy() {
            return new InMemoryCustomStrategy();
        }
    }

    private static class InMemoryCustomStrategy implements AISearchableEntityStorageStrategy {
        private final Map<String, AISearchableEntity> store = new ConcurrentHashMap<>();

        @Override
        public void save(AISearchableEntity entity) {
            if (entity == null) {
                return;
            }
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            entity.setUpdatedAt(LocalDateTime.now());
            store.put(key(entity.getEntityType(), entity.getEntityId()), entity);
        }

        @Override
        public Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId) {
            return Optional.ofNullable(store.get(key(entityType, entityId)));
        }

        @Override
        public List<AISearchableEntity> findByEntityType(String entityType) {
            return store.values().stream()
                .filter(e -> entityType.equals(e.getEntityType()))
                .toList();
        }

        @Override
        public Optional<AISearchableEntity> findByVectorId(String vectorId) {
            return store.values().stream()
                .filter(e -> vectorId != null && vectorId.equals(e.getVectorId()))
                .findFirst();
        }

        @Override
        public void delete(AISearchableEntity entity) {
            if (entity != null) {
                store.remove(key(entity.getEntityType(), entity.getEntityId()));
            }
        }

        @Override
        public void deleteByEntityTypeAndEntityId(String entityType, String entityId) {
            store.remove(key(entityType, entityId));
        }

        @Override
        public void deleteByEntityType(String entityType) {
            store.values().removeIf(e -> entityType.equals(e.getEntityType()));
        }

        @Override
        public void deleteByVectorId(String vectorId) {
            store.values().removeIf(e -> vectorId != null && vectorId.equals(e.getVectorId()));
        }

        @Override
        public void deleteByVectorIdIsNull() {
            store.values().removeIf(e -> e.getVectorId() == null);
        }

        @Override
        public void deleteAll() {
            store.clear();
        }

        @Override
        public List<AISearchableEntity> findByVectorIdIsNotNull() {
            return store.values().stream()
                .filter(e -> e.getVectorId() != null)
                .toList();
        }

        @Override
        public List<AISearchableEntity> findByVectorIdIsNull() {
            return store.values().stream()
                .filter(e -> e.getVectorId() == null)
                .toList();
        }

        @Override
        public long countByVectorIdIsNotNull() {
            return findByVectorIdIsNotNull().size();
        }

        @Override
        public Optional<AISearchableEntity> findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc() {
            return store.values().stream()
                .filter(e -> e.getVectorUpdatedAt() != null)
                .sorted((a, b) -> b.getVectorUpdatedAt().compareTo(a.getVectorUpdatedAt()))
                .findFirst();
        }

        @Override
        public List<AISearchableEntity> findByMetadataContainingSnippet(String snippet) {
            if (snippet == null) {
                return List.of();
            }
            return store.values().stream()
                .filter(e -> e.getMetadata() != null && e.getMetadata().contains(snippet))
                .toList();
        }

        @Override
        public List<AISearchableEntity> findByEntityTypeAndVectorIdIsNotNull(String entityType) {
            return store.values().stream()
                .filter(e -> entityType.equals(e.getEntityType()) && e.getVectorId() != null)
                .toList();
        }

        @Override
        public List<AISearchableEntity> findByEntityTypeAndVectorIdIsNull(String entityType) {
            return store.values().stream()
                .filter(e -> entityType.equals(e.getEntityType()) && e.getVectorId() == null)
                .toList();
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public String getStrategyName() {
            return "CUSTOM";
        }

        @Override
        public List<String> getSupportedEntityTypes() {
            return new ArrayList<>(store.values().stream()
                .map(AISearchableEntity::getEntityType)
                .filter(type -> type != null && !type.isBlank())
                .distinct()
                .toList());
        }

        private String key(String type, String id) {
            return (type == null ? "" : type) + "::" + (id == null ? "" : id);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private AISearchableEntityStorageStrategy storage;

    @Test
    void customStrategyIsUsedForCrud() {

        AISearchableEntity entity = AISearchableEntity.builder()
            .entityType("custom-type")
            .entityId("123")
            .searchableContent("hello world")
            .vectorId("vec-1")
            .metadata("{\"source\":\"custom\"}")
            .build();

        storage.save(entity);

        Optional<AISearchableEntity> found = storage.findByEntityTypeAndEntityId("custom-type", "123");
        assertTrue(found.isPresent());
        assertEquals("vec-1", found.get().getVectorId());
        assertEquals("CUSTOM", storage.getStrategyName());
        assertTrue(storage.isHealthy());
    }
}
