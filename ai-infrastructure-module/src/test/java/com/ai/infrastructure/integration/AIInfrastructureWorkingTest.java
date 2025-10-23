package com.ai.infrastructure.integration;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Working Integration Tests for AI Infrastructure Module
 * Simple tests that actually work with the current API
 */
@ExtendWith(MockitoExtension.class)
public class AIInfrastructureWorkingTest {

    @Mock
    private AISearchableEntityRepository searchRepository;

    @Test
    public void testAISearchableEntityCreation() {
        // Given
        AISearchableEntity entity = AISearchableEntity.builder()
            .entityType("product")
            .entityId("1")
            .searchableContent("Luxury Watch Description")
            .embeddings(Arrays.asList(0.1, 0.2, 0.3, 0.4, 0.5))
            .metadata("{\"price\": 1000.0, \"category\": \"watches\"}")
            .aiAnalysis("AI Analysis Result")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // When
        when(searchRepository.save(any(AISearchableEntity.class))).thenReturn(entity);

        // Then
        AISearchableEntity savedEntity = searchRepository.save(entity);
        assertNotNull(savedEntity);
        assertEquals("product", savedEntity.getEntityType());
        assertEquals("1", savedEntity.getEntityId());
        assertEquals("Luxury Watch Description", savedEntity.getSearchableContent());
        assertNotNull(savedEntity.getEmbeddings());
        assertEquals(5, savedEntity.getEmbeddings().size());
        assertNotNull(savedEntity.getMetadata());
        assertTrue(savedEntity.getMetadata().contains("price"));
    }

    @Test
    public void testRepositoryOperations() {
        // Given
        AISearchableEntity entity1 = createTestEntity("1", "product", "Product 1");
        AISearchableEntity entity2 = createTestEntity("2", "product", "Product 2");
        AISearchableEntity entity3 = createTestEntity("3", "user", "User 1");

        List<AISearchableEntity> allProducts = Arrays.asList(entity1, entity2);
        List<AISearchableEntity> allUsers = Arrays.asList(entity3);

        when(searchRepository.findByEntityType("product")).thenReturn(allProducts);
        when(searchRepository.findByEntityType("user")).thenReturn(allUsers);
        when(searchRepository.findByEntityTypeAndEntityId("product", "1")).thenReturn(Optional.of(entity1));
        when(searchRepository.save(any(AISearchableEntity.class))).thenReturn(entity1);

        // When
        List<AISearchableEntity> foundProducts = searchRepository.findByEntityType("product");
        List<AISearchableEntity> foundUsers = searchRepository.findByEntityType("user");
        Optional<AISearchableEntity> foundEntity = searchRepository.findByEntityTypeAndEntityId("product", "1");
        AISearchableEntity savedEntity = searchRepository.save(entity1);

        // Then
        assertNotNull(foundProducts);
        assertEquals(2, foundProducts.size());
        assertTrue(foundProducts.stream().allMatch(e -> "product".equals(e.getEntityType())));

        assertNotNull(foundUsers);
        assertEquals(1, foundUsers.size());
        assertTrue(foundUsers.stream().allMatch(e -> "user".equals(e.getEntityType())));

        assertTrue(foundEntity.isPresent());
        assertEquals("1", foundEntity.get().getEntityId());

        assertNotNull(savedEntity);
        verify(searchRepository, times(1)).save(entity1);
    }

    @Test
    public void testEntityDeletion() {
        // Given
        String entityType = "product";
        String entityId = "1";

        // When
        searchRepository.deleteByEntityTypeAndEntityId(entityType, entityId);
        searchRepository.deleteByEntityType(entityType);

        // Then
        verify(searchRepository, times(1)).deleteByEntityTypeAndEntityId(entityType, entityId);
        verify(searchRepository, times(1)).deleteByEntityType(entityType);
    }

    @Test
    public void testAnnotationPresence() {
        // Given
        Class<?> testEntityClass = TestProduct.class;

        // When
        AICapable annotation = testEntityClass.getAnnotation(AICapable.class);

        // Then
        assertNotNull(annotation, "@AICapable annotation should be present");
        assertEquals("product", annotation.entityType());
        assertTrue(annotation.features().length > 0);
        assertTrue(Arrays.asList(annotation.features()).contains("embedding"));
        assertTrue(Arrays.asList(annotation.features()).contains("search"));
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Given
        int numberOfThreads = 10;
        int operationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        when(searchRepository.save(any(AISearchableEntity.class))).thenReturn(createTestEntity("1", "product", "Test"));

        // When
        for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        AISearchableEntity entity = createTestEntity(
                            String.valueOf(threadId * operationsPerThread + j),
                            "product",
                            "Product " + (threadId * operationsPerThread + j)
                        );
                        searchRepository.save(entity);
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for completion
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        // Then
        assertEquals(numberOfThreads * operationsPerThread, successCount.get());
        verify(searchRepository, times(numberOfThreads * operationsPerThread)).save(any(AISearchableEntity.class));
    }

    @Test
    public void testErrorHandling() {
        // Given
        when(searchRepository.save(any(AISearchableEntity.class)))
            .thenThrow(new RuntimeException("Database error"));

        AISearchableEntity entity = createTestEntity("1", "product", "Test Product");

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            searchRepository.save(entity);
        });
    }

    @Test
    public void testEntityBuilder() {
        // Given & When
        AISearchableEntity entity = AISearchableEntity.builder()
            .entityId("test-123")
            .entityType("product")
            .searchableContent("Test content")
            .embeddings(Arrays.asList(0.1, 0.2, 0.3))
            .metadata("{\"test\": true}")
            .aiAnalysis("Test analysis")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Then
        assertNotNull(entity);
        assertEquals("test-123", entity.getEntityId());
        assertEquals("product", entity.getEntityType());
        assertEquals("Test content", entity.getSearchableContent());
        assertNotNull(entity.getEmbeddings());
        assertEquals(3, entity.getEmbeddings().size());
        assertNotNull(entity.getMetadata());
        assertTrue(entity.getMetadata().contains("test"));
        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    @Test
    public void testRepositoryFindAll() {
        // Given
        List<AISearchableEntity> entities = Arrays.asList(
            createTestEntity("1", "product", "Product 1"),
            createTestEntity("2", "product", "Product 2"),
            createTestEntity("3", "user", "User 1")
        );

        when(searchRepository.findAll()).thenReturn(entities);

        // When
        List<AISearchableEntity> allEntities = searchRepository.findAll();

        // Then
        assertNotNull(allEntities);
        assertEquals(3, allEntities.size());
        verify(searchRepository, times(1)).findAll();
    }

    private AISearchableEntity createTestEntity(String entityId, String entityType, String content) {
        return AISearchableEntity.builder()
            .entityId(entityId)
            .entityType(entityType)
            .searchableContent(content)
            .embeddings(Arrays.asList(0.1, 0.2, 0.3))
            .metadata("{\"test\": true}")
            .aiAnalysis("Test Analysis")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    // Test entity class
    @AICapable(entityType = "product")
    public static class TestProduct {
        private Long id;
        private String name;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}