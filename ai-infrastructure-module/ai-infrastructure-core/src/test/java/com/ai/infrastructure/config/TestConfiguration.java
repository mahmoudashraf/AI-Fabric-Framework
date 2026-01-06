package com.ai.infrastructure.config;

import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.VectorRecord;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.spi.ContentRetriever;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.Mockito.mock;

/**
 * Test configuration supplying deterministic provider and vector service doubles
 * so core module tests can run without external modules (including RAG module).
 * 
 * <p>Uses ContentRetriever (generic interface from core) instead of RAGProvider
 * to avoid dependency on the RAG module.</p>
 */
@SpringBootApplication(scanBasePackages = "com.ai.infrastructure")
@Import(AIInfrastructureAutoConfiguration.class)
@EntityScan(basePackages = "com.ai.infrastructure.entity")
@EnableJpaRepositories(basePackages = "com.ai.infrastructure.repository")
public class TestConfiguration {

    @Bean
    @Primary
    public AIProviderManager aiProviderManager() {
        return mock(AIProviderManager.class);
    }

    @Bean
    @Primary
    public EmbeddingProvider testEmbeddingProvider() {
        return new TestEmbeddingProvider();
    }

    @Bean
    @Primary
    public VectorDatabaseService testVectorDatabaseService() {
        return new InMemoryVectorDatabaseService();
    }

    /**
     * Test ContentRetriever implementation for core module tests.
     * Uses generic interface (ContentRetriever) from core, NOT RAGProvider from RAG module.
     */
    @Bean
    @Primary
    public ContentRetriever testContentRetriever(VectorDatabaseService vectorDatabaseService) {
        return new TestContentRetriever(vectorDatabaseService);
    }

    /**
     * Test ContentRetriever implementation for core module tests.
     * Provides basic retrieval functionality using the in-memory vector database.
     */
    private static final class TestContentRetriever implements ContentRetriever {
        private final VectorDatabaseService vectorDatabaseService;

        TestContentRetriever(VectorDatabaseService vectorDatabaseService) {
            this.vectorDatabaseService = vectorDatabaseService;
        }

        @Override
        public RetrievalResult retrieve(String query, String entityType, int limit, double threshold, Map<String, Object> metadata) {
            long startTime = System.currentTimeMillis();
            
            // Generate test embedding
            List<Double> queryVector = new ArrayList<>(384);
            for (int i = 0; i < 384; i++) {
                queryVector.add(0.1 + (i % 7) * 0.01);
            }
            
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(query)
                .entityType(entityType)
                .limit(limit)
                .threshold(threshold)
                .metadata(metadata)
                .build();
            
            AISearchResponse searchResponse = vectorDatabaseService.search(queryVector, searchRequest);
            
            List<RetrievedDocument> documents = new ArrayList<>();
            for (Map<String, Object> result : searchResponse.getResults()) {
                documents.add(new RetrievedDocument(
                    (String) result.get("vectorId"),
                    (String) result.get("content"),
                    null, // title
                    (String) result.get("entityType"),
                    result.get("score") instanceof Number ? ((Number) result.get("score")).doubleValue() : 0.0,
                    result.get("metadata") instanceof Map ? (Map<String, Object>) result.get("metadata") : Map.of()
                ));
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            double maxScore = documents.stream().mapToDouble(RetrievedDocument::score).max().orElse(0.0);
            double avgScore = documents.stream().mapToDouble(RetrievedDocument::score).average().orElse(0.0);
            
            return RetrievalResult.success(documents, searchResponse.getTotalResults(), maxScore, avgScore, processingTime);
        }

        @Override
        public void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata) {
            List<Double> embedding = new ArrayList<>(384);
            for (int i = 0; i < 384; i++) {
                embedding.add(0.1 + (i % 7) * 0.01);
            }
            vectorDatabaseService.storeVector(entityType, entityId, content, embedding, metadata);
        }

        @Override
        public void removeContent(String entityType, String entityId) {
            vectorDatabaseService.removeVector(entityType, entityId);
        }

        @Override
        public String getRetrieverName() {
            return "test-content-retriever";
        }
    }

    private static final class TestEmbeddingProvider implements EmbeddingProvider {
        private static final int DIMENSION = 384;

        @Override
        public String getProviderName() {
            return "onnx";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
            return AIEmbeddingResponse.builder()
                .embedding(buildVector())
                .model(request != null && request.getModel() != null ? request.getModel() : "onnx-test-model")
                .dimensions(DIMENSION)
                .processingTimeMs(5L)
                .requestId(UUID.randomUUID().toString())
                .build();
        }

        @Override
        public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
            List<AIEmbeddingResponse> responses = new ArrayList<>(texts.size());
            for (String text : texts) {
                responses.add(generateEmbedding(AIEmbeddingRequest.builder().text(text).build()));
            }
            return responses;
        }

        @Override
        public int getEmbeddingDimension() {
            return DIMENSION;
        }

        @Override
        public Map<String, Object> getStatus() {
            return Map.of(
                "provider", getProviderName(),
                "available", true,
                "dimensions", DIMENSION
            );
        }

        private List<Double> buildVector() {
            List<Double> values = new ArrayList<>(DIMENSION);
            for (int i = 0; i < DIMENSION; i++) {
                values.add(0.1 + (i % 7) * 0.01);
            }
            return values;
        }
    }

    private static final class InMemoryVectorDatabaseService implements VectorDatabaseService {
        private final Map<String, VectorRecord> store = new ConcurrentHashMap<>();
        private final Map<String, Map<String, String>> entityIndex = new ConcurrentHashMap<>();

        @Override
        public String storeVector(String entityType, String entityId, String content,
                                  List<Double> embedding, Map<String, Object> metadata) {
            String vectorId = buildVectorId(entityType, entityId);
            VectorRecord record = VectorRecord.builder()
                .vectorId(vectorId)
                .entityType(entityType)
                .entityId(entityId)
                .content(content)
                .embedding(embedding != null ? List.copyOf(embedding) : Collections.emptyList())
                .metadata(metadata != null ? new HashMap<>(metadata) : new HashMap<>())
                .build();
            store.put(vectorId, record);
            entityIndex.computeIfAbsent(entityType, key -> new ConcurrentHashMap<>()).put(entityId, vectorId);
            return vectorId;
        }

        @Override
        public boolean updateVector(String vectorId, String entityType, String entityId,
                                    String content, List<Double> embedding, Map<String, Object> metadata) {
            String id = vectorId != null ? vectorId : buildVectorId(entityType, entityId);
            storeVector(entityType, entityId, content, embedding, metadata);
            return store.containsKey(id);
        }

        @Override
        public java.util.Optional<VectorRecord> getVector(String vectorId) {
            return java.util.Optional.ofNullable(store.get(vectorId));
        }

        @Override
        public java.util.Optional<VectorRecord> getVectorByEntity(String entityType, String entityId) {
            Map<String, String> byEntity = entityIndex.get(entityType);
            if (byEntity == null) {
                return java.util.Optional.empty();
            }
            String vectorId = byEntity.get(entityId);
            return vectorId == null ? java.util.Optional.empty() : getVector(vectorId);
        }

        @Override
        public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
            List<VectorRecord> candidates;
            if (request != null && request.getEntityType() != null) {
                candidates = getVectorsByEntityType(request.getEntityType());
            } else {
                candidates = new ArrayList<>(store.values());
            }

            List<Map<String, Object>> results = new ArrayList<>(candidates.size());
            for (VectorRecord record : candidates) {
                Map<String, Object> row = new HashMap<>();
                row.put("vectorId", record.getVectorId());
                row.put("entityId", record.getEntityId());
                row.put("entityType", record.getEntityType());
                row.put("content", record.getContent());
                row.put("metadata", record.getMetadata());
                row.put("score", 1.0);
                results.add(row);
            }

            return AISearchResponse.builder()
                .results(results)
                .totalResults(results.size())
                .maxScore(results.isEmpty() ? 0.0 : 1.0)
                .processingTimeMs(1L)
                .query(request != null ? request.getQuery() : null)
                .model("in-memory-test")
                .build();
        }

        @Override
        public AISearchResponse searchByEntityType(List<Double> queryVector, String entityType, int limit, double threshold) {
            AISearchRequest request = AISearchRequest.builder()
                .entityType(entityType)
                .limit(limit)
                .threshold(threshold)
                .build();
            return search(queryVector, request);
        }

        @Override
        public boolean removeVector(String entityType, String entityId) {
            Map<String, String> byEntity = entityIndex.get(entityType);
            if (byEntity == null) {
                return false;
            }
            String vectorId = byEntity.remove(entityId);
            if (vectorId != null) {
                store.remove(vectorId);
                return true;
            }
            return false;
        }

        @Override
        public boolean removeVectorById(String vectorId) {
            VectorRecord removed = store.remove(vectorId);
            if (removed != null) {
                Map<String, String> byEntity = entityIndex.get(removed.getEntityType());
                if (byEntity != null) {
                    byEntity.remove(removed.getEntityId());
                }
                return true;
            }
            return false;
        }

        @Override
        public List<String> batchStoreVectors(List<VectorRecord> vectors) {
            List<String> ids = new ArrayList<>(vectors.size());
            for (VectorRecord record : vectors) {
                ids.add(storeVector(record.getEntityType(), record.getEntityId(), record.getContent(),
                    record.getEmbedding(), record.getMetadata()));
            }
            return ids;
        }

        @Override
        public int batchUpdateVectors(List<VectorRecord> vectors) {
            int updated = 0;
            for (VectorRecord record : vectors) {
                if (updateVector(record.getVectorId(), record.getEntityType(), record.getEntityId(),
                        record.getContent(), record.getEmbedding(), record.getMetadata())) {
                    updated++;
                }
            }
            return updated;
        }

        @Override
        public int batchRemoveVectors(List<String> vectorIds) {
            int removed = 0;
            for (String id : vectorIds) {
                if (removeVectorById(id)) {
                    removed++;
                }
            }
            return removed;
        }

        @Override
        public List<VectorRecord> getVectorsByEntityType(String entityType) {
            Map<String, String> byEntity = entityIndex.get(entityType);
            if (byEntity == null) {
                return Collections.emptyList();
            }
            List<VectorRecord> records = new ArrayList<>(byEntity.size());
            for (String vectorId : byEntity.values()) {
                VectorRecord record = store.get(vectorId);
                if (record != null) {
                    records.add(record);
                }
            }
            return records;
        }

        @Override
        public long getVectorCountByEntityType(String entityType) {
            Map<String, String> byEntity = entityIndex.get(entityType);
            return byEntity == null ? 0 : byEntity.size();
        }

        @Override
        public boolean vectorExists(String entityType, String entityId) {
            Map<String, String> byEntity = entityIndex.get(entityType);
            return byEntity != null && byEntity.containsKey(entityId);
        }

        @Override
        public Map<String, Object> getStatistics() {
            return Map.of(
                "totalVectors", store.size(),
                "entityTypes", entityIndex.keySet().size()
            );
        }

        @Override
        public long clearVectors() {
            int count = store.size();
            store.clear();
            entityIndex.clear();
            return count;
        }

        @Override
        public long clearVectorsByEntityType(String entityType) {
            Map<String, String> byEntity = entityIndex.remove(entityType);
            if (byEntity == null) {
                return 0;
            }
            for (String vectorId : byEntity.values()) {
                store.remove(vectorId);
            }
            return byEntity.size();
        }

        private String buildVectorId(String entityType, String entityId) {
            return entityType + "::" + entityId;
        }
    }
}
