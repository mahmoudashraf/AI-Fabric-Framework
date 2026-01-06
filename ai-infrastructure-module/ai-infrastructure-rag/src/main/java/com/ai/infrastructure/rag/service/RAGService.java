package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.rag.dto.RAGRequest;
import com.ai.infrastructure.rag.dto.RAGResponse;
import com.ai.infrastructure.rag.spi.RAGProvider;
import com.ai.infrastructure.spi.ContentRetriever;
import com.ai.infrastructure.vector.VectorDatabase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RAG Service - Retrieval-focused implementation.
 * 
 * <p>This service performs <strong>retrieval operations only</strong>:</p>
 * <ul>
 *   <li>Embedding generation for queries</li>
 *   <li>Vector similarity search</li>
 *   <li>Content indexing and removal</li>
 * </ul>
 * 
 * <p><strong>What this service does NOT do:</strong></p>
 * <ul>
 *   <li>PII detection - handled by orchestrator's PIIDetectionStep</li>
 *   <li>LLM generation - handled by orchestrator's generation step</li>
 *   <li>Response sanitization - handled by orchestrator's ResponseSanitizationStep</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This service is thread-safe.</p>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @see RAGProvider
 * @since 1.0
 */
@Slf4j
@Service("ragService")
@RequiredArgsConstructor
public class RAGService implements RAGProvider, ContentRetriever {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String PROVIDER_NAME = "default-rag-service";
    
    // Result keys from search response
    private static final String KEY_ID = "id";
    private static final String KEY_CONTENT = "content";
    private static final String KEY_TITLE = "title";
    private static final String KEY_TYPE = "type";
    private static final String KEY_SCORE = "score";
    private static final String KEY_SIMILARITY = "similarity";
    private static final String KEY_METADATA = "metadata";
    private static final String KEY_SOURCE = "source";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final AIProviderConfig config;
    private final AIEmbeddingService embeddingService;
    private final VectorDatabaseService vectorDatabaseService;
    private final VectorDatabase vectorDatabase;
    private final AISearchService searchService;
    
    // =========================================================================
    // RAGProvider Implementation
    // =========================================================================
    
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    /**
     * Resolve conflicting default method from both interfaces.
     */
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public RAGResponse retrieve(RAGRequest request) {
        try {
            log.debug("Performing retrieval (entityType={}, requestId={})",
                request.getEntityType(), request.getRequestId());
            
            long startTime = System.currentTimeMillis();
            
            // Generate embedding for the query
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(request.getQuery())
                .build();
            
            AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            List<Double> queryVector = embeddingResponse.getEmbedding();
            
            // Build search request
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(request.getQuery())
                .entityType(request.getEntityType())
                .limit(request.getLimit() != null ? request.getLimit() : DEFAULT_LIMIT)
                .threshold(request.getThreshold() != null ? request.getThreshold() : DEFAULT_THRESHOLD)
                .metadata(request.getMetadata())
                .build();
            
            // Perform search
            AISearchResponse searchResponse;
            if (Boolean.TRUE.equals(request.getEnableHybridSearch())) {
                searchResponse = vectorDatabase.search(queryVector, searchRequest);
            } else if (Boolean.TRUE.equals(request.getEnableContextualSearch())) {
                searchResponse = vectorDatabase.search(queryVector, searchRequest);
            } else {
                searchResponse = searchService.search(queryVector, searchRequest);
            }
            
            // Convert results to retrieved documents
            List<RAGResponse.RetrievedDocument> documents = searchResponse.getResults().stream()
                .map(this::convertToDocument)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            // Apply filters if provided
            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
                documents = filterDocuments(documents, request.getFilters());
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return RAGResponse.builder()
                .documents(documents)
                .totalDocuments(searchResponse.getTotalResults())
                .returnedDocuments(documents.size())
                .success(true)
                .maxScore(documents.stream()
                    .mapToDouble(d -> d.getScore() != null ? d.getScore() : 0.0)
                    .max().orElse(0.0))
                .averageScore(documents.stream()
                    .mapToDouble(d -> d.getScore() != null ? d.getScore() : 0.0)
                    .average().orElse(0.0))
                .relevanceScores(documents.stream()
                    .map(RAGResponse.RetrievedDocument::getScore)
                    .collect(Collectors.toList()))
                .processingTimeMs(processingTime)
                .requestId(request.getRequestId())
                .query(request.getQuery())
                .entityType(request.getEntityType())
                .timestamp(LocalDateTime.now())
                .hybridSearchUsed(Boolean.TRUE.equals(request.getEnableHybridSearch()))
                .contextualSearchUsed(Boolean.TRUE.equals(request.getEnableContextualSearch()))
                .searchedCategories(request.getCategories())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing retrieval", e);
            return RAGResponse.builder()
                .success(false)
                .errorMessage("Retrieval failed: " + e.getMessage())
                .documents(Collections.emptyList())
                .totalDocuments(0)
                .returnedDocuments(0)
                .timestamp(LocalDateTime.now())
                .requestId(request.getRequestId())
                .build();
        }
    }
    
    @Override
    public void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata) {
        try {
            log.debug("Indexing content (entityType={}, entityId={})", entityType, entityId);
            
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(content)
                .entityType(entityType)
                .entityId(entityId)
                .build();
            
            AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            
            vectorDatabaseService.storeVector(entityType, entityId, content, 
                embeddingResponse.getEmbedding(), metadata);
            
            log.debug("Successfully indexed content (entityType={}, entityId={})", entityType, entityId);
            
        } catch (Exception e) {
            log.error("Error indexing content (entityType={}, entityId={})", entityType, entityId, e);
            throw new AIServiceException("Failed to index content", e);
        }
    }
    
    @Override
    public void removeContent(String entityType, String entityId) {
        try {
            log.debug("Removing content (entityType={}, entityId={})", entityType, entityId);
            vectorDatabaseService.removeVector(entityType, entityId);
            log.debug("Successfully removed content (entityType={}, entityId={})", entityType, entityId);
        } catch (Exception e) {
            log.error("Error removing content (entityType={}, entityId={})", entityType, entityId, e);
            throw new AIServiceException("Failed to remove content", e);
        }
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("vectorDatabaseService", vectorDatabaseService.getStatistics());
        stats.put("vectorDatabase", vectorDatabase.getStatistics());
        return stats;
    }
    
    // =========================================================================
    // ContentRetriever Implementation (Generic interface for pipeline)
    // =========================================================================
    
    @Override
    public String getRetrieverName() {
        return PROVIDER_NAME;
    }
    
    @Override
    public Object getExtendedResult(String query, String entityType, int limit, double threshold, Map<String, Object> metadata) {
        try {
            RAGRequest request = RAGRequest.builder()
                .query(query)
                .entityType(entityType)
                .limit(limit)
                .threshold(threshold)
                .metadata(metadata)
                .build();
            RAGResponse response = retrieve(request);
            log.debug("getExtendedResult returning RAGResponse (success: {}, documents: {})", 
                response.getSuccess(), response.getDocuments() != null ? response.getDocuments().size() : 0);
            return response;
        } catch (Exception e) {
            log.error("Error in getExtendedResult: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @Override
    public RetrievalResult retrieve(String query, String entityType, int limit, double threshold, Map<String, Object> metadata) {
        RAGRequest request = RAGRequest.builder()
            .query(query)
            .entityType(entityType)
            .limit(limit)
            .threshold(threshold)
            .metadata(metadata)
            .build();
        
        RAGResponse response = retrieve(request);
        
        if (!Boolean.TRUE.equals(response.getSuccess())) {
            return RetrievalResult.error(response.getErrorMessage());
        }
        
        List<RetrievedDocument> docs = response.getDocuments().stream()
            .map(d -> new RetrievedDocument(
                d.getId(),
                d.getContent(),
                d.getTitle(),
                d.getType(),
                d.getScore() != null ? d.getScore() : 0.0,
                d.getMetadata()
            ))
            .collect(Collectors.toList());
        
        return RetrievalResult.success(
            docs,
            response.getTotalDocuments() != null ? response.getTotalDocuments() : docs.size(),
            response.getMaxScore() != null ? response.getMaxScore() : 0.0,
            response.getAverageScore() != null ? response.getAverageScore() : 0.0,
            response.getProcessingTimeMs() != null ? response.getProcessingTimeMs() : 0L
        );
    }
    
    // =========================================================================
    // Helper Methods
    // =========================================================================
    
    private RAGResponse.RetrievedDocument convertToDocument(Map<String, Object> result) {
        if (result == null) {
            return null;
        }
        
        return RAGResponse.RetrievedDocument.builder()
            .id(getString(result, KEY_ID))
            .content(getString(result, KEY_CONTENT))
            .title(getString(result, KEY_TITLE))
            .type(getString(result, KEY_TYPE))
            .score(getDouble(result, KEY_SCORE))
            .similarity(getDouble(result, KEY_SIMILARITY))
            .metadata(getMap(result, KEY_METADATA))
            .source(getString(result, KEY_SOURCE))
            .build();
    }
    
    private List<RAGResponse.RetrievedDocument> filterDocuments(
            List<RAGResponse.RetrievedDocument> documents, 
            Map<String, Object> filters) {
        return documents.stream()
            .filter(doc -> matchesFilters(doc.getMetadata(), filters))
            .collect(Collectors.toList());
    }
    
    private boolean matchesFilters(Map<String, Object> metadata, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        if (metadata == null) {
            return false;
        }
        
        for (Map.Entry<String, Object> filter : filters.entrySet()) {
            Object expected = filter.getValue();
            Object actual = metadata.get(filter.getKey());
            
            if (expected != null && !String.valueOf(expected).equalsIgnoreCase(String.valueOf(actual))) {
                return false;
            }
        }
        return true;
    }
    
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
    
    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        // Handle JSON string from Lucene vector database
        if (value instanceof String jsonString && !jsonString.isBlank()) {
            try {
                return parseJsonToMap(jsonString);
            } catch (Exception e) {
                log.warn("Failed to parse metadata JSON: {}", jsonString, e);
            }
        }
        return Collections.emptyMap();
    }
    
    /**
     * Parse a JSON string to a Map.
     * Handles simple JSON objects with string values.
     */
    private Map<String, Object> parseJsonToMap(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isBlank() || !json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }
        
        // Remove braces and parse key-value pairs
        String content = json.substring(1, json.length() - 1);
        if (content.isBlank()) {
            return result;
        }
        
        // Simple JSON parser for "key":"value" pairs
        int i = 0;
        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) i++;
            if (i >= content.length()) break;
            
            // Parse key
            if (content.charAt(i) != '"') break;
            int keyStart = ++i;
            while (i < content.length() && content.charAt(i) != '"') i++;
            if (i >= content.length()) break;
            String key = content.substring(keyStart, i);
            i++; // Skip closing quote
            
            // Skip colon
            while (i < content.length() && (Character.isWhitespace(content.charAt(i)) || content.charAt(i) == ':')) i++;
            if (i >= content.length()) break;
            
            // Parse value
            if (content.charAt(i) != '"') break;
            int valueStart = ++i;
            while (i < content.length() && content.charAt(i) != '"') i++;
            if (i >= content.length()) break;
            String value = content.substring(valueStart, i);
            i++; // Skip closing quote
            
            result.put(key, value);
            
            // Skip comma and whitespace
            while (i < content.length() && (Character.isWhitespace(content.charAt(i)) || content.charAt(i) == ',')) i++;
        }
        
        return result;
    }
}
