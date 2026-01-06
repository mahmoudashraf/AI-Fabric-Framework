package com.ai.infrastructure.rag.service;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.rag.VectorDatabaseService;
import com.ai.infrastructure.spi.RAGProvider;
import com.ai.infrastructure.vector.VectorDatabase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) Service implementation.
 * 
 * <p>This service provides RAG capabilities by combining retrieval and generation.
 * It can index content, perform semantic search, and generate context-aware responses.</p>
 * 
 * <p>Queries passed to this service are assumed to be pre-processed by the
 * orchestrator (PII redacted, sanitized, normalized). The service performs no
 * additional PII detection or sanitization.</p>
 * 
 * <p>This is the default implementation of {@link RAGProvider} SPI, providing:</p>
 * <ul>
 *   <li>Content indexing with embeddings</li>
 *   <li>Semantic search with vector similarity</li>
 *   <li>Hybrid and contextual search modes</li>
 *   <li>Context building for generation</li>
 * </ul>
 * 
 * <p><strong>Thread Safety:</strong> This service is thread-safe and can be used
 * concurrently by multiple threads.</p>
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 * @see RAGProvider
 * @since 1.0
 */
@Slf4j
@Service("ragService")
@RequiredArgsConstructor
public class RAGService implements RAGProvider {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String PROVIDER_NAME = "default-rag-service";
    
    // Metadata keys
    private static final String METADATA_KEY_OPTIMIZED_QUERY_PROVIDED = "optimizedQueryProvided";
    private static final String METADATA_KEY_EMBEDDING_QUERY = "embeddingQuery";
    private static final String METADATA_KEY_OPTIMIZED_QUERY = "optimizedQuery";
    
    // Result keys
    private static final String RESULT_KEY_CONTENT = "content";
    private static final String RESULT_KEY_SCORE = "score";
    private static final String RESULT_KEY_SIMILARITY = "similarity";
    private static final String RESULT_KEY_ID = "id";
    private static final String RESULT_KEY_TITLE = "title";
    private static final String RESULT_KEY_TYPE = "type";
    private static final String RESULT_KEY_METADATA = "metadata";
    private static final String RESULT_KEY_RAW = "raw";
    
    // Default values
    private static final double DEFAULT_SEARCH_THRESHOLD = 0.7;
    
    // Messages
    private static final String NO_CONTEXT_MESSAGE = "No relevant context found.";
    private static final String CONTEXT_HEADER = "Relevant Context:\n\n";
    private static final String NO_INFO_MESSAGE = "I don't have enough information to answer your question: ";
    private static final String BASED_ON_INFO_MESSAGE = "Based on the available information: ";
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final AIProviderConfig config;
    private final AIEmbeddingService embeddingService;
    private final VectorDatabaseService vectorDatabaseService;
    private final VectorDatabase vectorDatabase;
    private final AISearchService searchService;
    
    // =========================================================================
    // JSON Processing
    // =========================================================================
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    
    // =========================================================================
    // RAGProvider Implementation
    // =========================================================================
    
    /**
     * {@inheritDoc}
     */
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata) {
        try {
            log.debug("Indexing content for entity {} of type {}", entityId, entityType);
            
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(content)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata != null ? metadata.toString() : null)
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            
            vectorDatabaseService.storeVector(entityType, entityId, content, 
                embeddingResponse.getEmbedding(), metadata);
            
            log.debug("Successfully indexed content for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error indexing content for entity {} of type {}", entityId, entityType, e);
            throw new AIServiceException("Failed to index content", e);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void removeContent(String entityType, String entityId) {
        try {
            log.debug("Removing content for entity {} of type {}", entityId, entityType);
            
            vectorDatabaseService.removeVector(entityType, entityId);
            
            log.debug("Successfully removed content for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error removing content for entity {} of type {}", entityId, entityType, e);
            throw new AIServiceException("Failed to remove content", e);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIndexed", vectorDatabaseService.getStatistics());
        stats.put("vectorDatabase", vectorDatabase.getStatistics());
        return stats;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public RAGResponse performRag(RAGRequest request) {
        try {
            String processedQuery = request.getQuery();
            String embeddingQuery = resolveEmbeddingQuery(request, processedQuery);
            
            log.debug("Performing RAG operation (entityType={}, requestId={})",
                request.getEntityType(),
                request.getRequestId());

            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(embeddingQuery)
                .build();
            
            AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            List<Double> queryVector = embeddingResponse.getEmbedding();
            
            String contextString = request.getContext() != null ? request.getContext().toString() : null;
            String filtersString = request.getFilters() != null ? request.getFilters().toString() : null;
            
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(embeddingQuery)
                .entityType(request.getEntityType())
                .limit(request.getLimit())
                .threshold(request.getThreshold())
                .context(contextString)
                .filters(filtersString)
                .metadata(request.getMetadata())
                .build();
            
            AISearchResponse searchResponse = searchService.search(queryVector, searchRequest);
            
            Map<String, Object> filters = request.getFilters();

            List<RAGResponse.RAGDocument> documents = searchResponse.getResults().stream()
                .map(result -> {
                    Map<String, Object> normalizedMetadata = normalizeMetadata(result.get(RESULT_KEY_METADATA));
                    if (!matchesFilters(normalizedMetadata, filters)) {
                        return null;
                    }

                    return RAGResponse.RAGDocument.builder()
                        .id((String) result.get(RESULT_KEY_ID))
                        .content((String) result.get(RESULT_KEY_CONTENT))
                        .title((String) result.get(RESULT_KEY_TITLE))
                        .type((String) result.get(RESULT_KEY_TYPE))
                        .score((Double) result.get(RESULT_KEY_SCORE))
                        .similarity((Double) result.get(RESULT_KEY_SIMILARITY))
                        .metadata(normalizedMetadata)
                        .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            Map<String, Object> aggregatedMetadata = buildAggregatedMetadata(
                request.getMetadata(), embeddingQuery);
            
            return RAGResponse.builder()
                .documents(documents)
                .totalDocuments(searchResponse.getTotalResults())
                .usedDocuments(documents.size())
                .relevanceScores(documents.stream()
                    .map(RAGResponse.RAGDocument::getScore)
                    .collect(Collectors.toList()))
                .success(true)
                .totalResults(searchResponse.getTotalResults())
                .returnedResults(documents.size())
                .maxScore(documents.stream()
                    .mapToDouble(RAGResponse.RAGDocument::getScore)
                    .max().orElse(0.0))
                .averageScore(documents.stream()
                    .mapToDouble(RAGResponse.RAGDocument::getScore)
                    .average().orElse(0.0))
                .processingTimeMs(searchResponse.getProcessingTimeMs())
                .requestId(request.getRequestId())
                .originalQuery(processedQuery)
                .entityType(request.getEntityType())
                .model(config.resolveLlmDefaults().model())
                .timestamp(java.time.LocalDateTime.now())
                .metadata(Collections.unmodifiableMap(aggregatedMetadata))
                .build();
                
        } catch (Exception e) {
            log.error("Error performing RAG operation", e);
            return RAGResponse.builder()
                .success(false)
                .errorMessage("Failed to perform RAG operation: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public RAGResponse performRAGQuery(RAGRequest request) {
        try {
            String processedQuery = request.getQuery();
            String embeddingQuery = resolveEmbeddingQuery(request, processedQuery);
            
            log.debug("Performing RAG query (entityType={}, requestId={})",
                request.getEntityType(),
                request.getRequestId());
            
            long startTime = System.currentTimeMillis();
            
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(embeddingQuery)
                .model(config.resolveEmbeddingDefaults().model())
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            List<Double> queryVector = embeddingResponse.getEmbedding();
            
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(embeddingQuery)
                .entityType(request.getEntityType())
                .limit(request.getLimit())
                .threshold(request.getThreshold())
                .build();
            
            AISearchResponse searchResponse;
            if (Boolean.TRUE.equals(request.getEnableHybridSearch())) {
                searchResponse = performHybridSearch(queryVector, processedQuery, searchRequest);
            } else if (Boolean.TRUE.equals(request.getEnableContextualSearch())) {
                searchResponse = performContextualSearch(queryVector, 
                    request.getContext() != null ? request.getContext().toString() : "", searchRequest);
            } else {
                searchResponse = vectorDatabase.search(queryVector, searchRequest);
            }
            
            String context = buildContext(searchResponse);
            String response = generateResponse(processedQuery, context);
            
            long processingTime = System.currentTimeMillis() - startTime;

            Map<String, Object> aggregatedMetadata = buildAggregatedMetadata(
                request.getMetadata(), embeddingQuery);
            
            return RAGResponse.builder()
                .response(response)
                .context(context)
                .documents(convertToRAGDocuments(searchResponse.getResults()))
                .totalDocuments(searchResponse.getTotalResults())
                .usedDocuments(Math.min(searchResponse.getTotalResults(), request.getLimit()))
                .confidenceScore(calculateConfidence(searchResponse))
                .relevanceScores(searchResponse.getResults().stream()
                    .map(doc -> (Double) doc.get(RESULT_KEY_SIMILARITY))
                    .collect(Collectors.toList()))
                .processingTimeMs(processingTime)
                .requestId(request.getRequestId())
                .model(config.resolveEmbeddingDefaults().model())
                .success(true)
                .hybridSearchUsed(Boolean.TRUE.equals(request.getEnableHybridSearch()))
                .contextualSearchUsed(Boolean.TRUE.equals(request.getEnableContextualSearch()))
                .originalQuery(processedQuery)
                .entityType(request.getEntityType())
                .searchedCategories(request.getCategories())
                .metadata(Collections.unmodifiableMap(aggregatedMetadata))
                .build();
                
        } catch (Exception e) {
            log.error("Error performing RAG query", e);
            return RAGResponse.builder()
                .response("")
                .context("")
                .documents(Collections.emptyList())
                .totalDocuments(0)
                .usedDocuments(0)
                .confidenceScore(0.0)
                .relevanceScores(Collections.emptyList())
                .processingTimeMs(0L)
                .requestId(request.getRequestId())
                .model(config.resolveEmbeddingDefaults().model())
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }
    
    // =========================================================================
    // Additional Public Methods (for backward compatibility)
    // =========================================================================
    
    /**
     * Perform RAG query with basic parameters.
     * 
     * @param query the search query
     * @param entityType the type of entities to search
     * @param limit maximum number of results
     * @return RAG response with retrieved context
     */
    public AISearchResponse performRAGQuery(String query, String entityType, int limit) {
        try {
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(query)
                .entityType(entityType)
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(query)
                .entityType(entityType)
                .limit(limit)
                .threshold(DEFAULT_SEARCH_THRESHOLD)
                .build();
            
            AISearchResponse searchResponse = vectorDatabaseService.search(
                embeddingResponse.getEmbedding(), searchRequest);
            
            log.debug("RAG query completed with {} results", searchResponse.getTotalResults());
            
            return searchResponse;
            
        } catch (Exception e) {
            log.error("Error performing RAG query", e);
            throw new AIServiceException("Failed to perform RAG query", e);
        }
    }
    
    /**
     * Build context from search results.
     * 
     * @param searchResponse the search results
     * @return formatted context string
     */
    public String buildContext(AISearchResponse searchResponse) {
        if (searchResponse.getResults().isEmpty()) {
            return NO_CONTEXT_MESSAGE;
        }
        
        StringBuilder context = new StringBuilder();
        context.append(CONTEXT_HEADER);
        
        for (int i = 0; i < searchResponse.getResults().size(); i++) {
            Map<String, Object> result = searchResponse.getResults().get(i);
            context.append(String.format("%d. %s (Score: %.3f)\n", 
                i + 1, result.get(RESULT_KEY_CONTENT), result.get(RESULT_KEY_SCORE)));
        }
        
        return context.toString();
    }
    
    // =========================================================================
    // Private Helper Methods
    // =========================================================================
    
    private AISearchResponse performHybridSearch(List<Double> queryVector, String queryText, 
            AISearchRequest request) {
        return vectorDatabase.search(queryVector, request);
    }
    
    private AISearchResponse performContextualSearch(List<Double> queryVector, String context, 
            AISearchRequest request) {
        return vectorDatabase.search(queryVector, request);
    }
    
    private String generateResponse(String query, String context) {
        if (context.isEmpty() || NO_CONTEXT_MESSAGE.equals(context)) {
            return NO_INFO_MESSAGE + query;
        }
        
        int maxLength = Math.min(context.length(), 500);
        return BASED_ON_INFO_MESSAGE + context.substring(0, maxLength) + "...";
    }
    
    private double calculateConfidence(AISearchResponse searchResponse) {
        if (searchResponse.getResults().isEmpty()) {
            return 0.0;
        }
        
        return searchResponse.getResults().stream()
            .mapToDouble(doc -> {
                Object similarity = doc.get(RESULT_KEY_SIMILARITY);
                return similarity instanceof Number ? ((Number) similarity).doubleValue() : 0.0;
            })
            .average()
            .orElse(0.0);
    }
    
    private List<RAGResponse.RAGDocument> convertToRAGDocuments(List<Map<String, Object>> results) {
        return results.stream()
            .map(this::convertToRAGDocument)
            .collect(Collectors.toList());
    }
    
    private RAGResponse.RAGDocument convertToRAGDocument(Map<String, Object> result) {
        Map<String, Object> metadata = normalizeMetadata(result.get(RESULT_KEY_METADATA));

        Object scoreValue = result.getOrDefault(RESULT_KEY_SCORE, 0.0);
        Object similarityValue = result.getOrDefault(RESULT_KEY_SIMILARITY, 0.0);

        double score = scoreValue instanceof Number ? ((Number) scoreValue).doubleValue() 
            : parseDouble(scoreValue) != null ? parseDouble(scoreValue) : 0.0;
        double similarity = similarityValue instanceof Number ? ((Number) similarityValue).doubleValue() 
            : parseDouble(similarityValue) != null ? parseDouble(similarityValue) : 0.0;

        return RAGResponse.RAGDocument.builder()
            .id((String) result.get(RESULT_KEY_ID))
            .content((String) result.get(RESULT_KEY_CONTENT))
            .title((String) result.get(RESULT_KEY_TITLE))
            .type((String) result.get(RESULT_KEY_TYPE))
            .score(score)
            .similarity(similarity)
            .metadata(metadata)
            .build();
    }
    
    private String resolveEmbeddingQuery(RAGRequest request, String processedQuery) {
        String optimized = extractOptimizedQuery(request.getMetadata());
        if (StringUtils.hasText(optimized)) {
            return optimized;
        }
        return processedQuery;
    }

    private String extractOptimizedQuery(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object candidate = metadata.get(METADATA_KEY_OPTIMIZED_QUERY);
        if (candidate instanceof String str && StringUtils.hasText(str)) {
            return str;
        }
        return null;
    }

    private Map<String, Object> normalizeMetadata(Object metadata) {
        if (metadata instanceof Map<?, ?> rawMap) {
            return rawMap.entrySet().stream()
                .collect(Collectors.toMap(entry -> String.valueOf(entry.getKey()), Map.Entry::getValue));
        }
        if (metadata instanceof String metadataJson && !metadataJson.trim().isEmpty()) {
            try {
                Map<String, Object> parsed = OBJECT_MAPPER.readValue(metadataJson, MAP_TYPE_REFERENCE);
                return parsed != null ? parsed : Collections.emptyMap();
            } catch (Exception e) {
                log.debug("Unable to parse metadata JSON: {}", metadataJson, e);
                return Collections.singletonMap(RESULT_KEY_RAW, metadataJson);
            }
        }
        return Collections.emptyMap();
    }

    private boolean matchesFilters(Map<String, Object> metadata, Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }

        Map<String, Object> safeMetadata = metadata != null ? metadata : Collections.emptyMap();

        return filters.entrySet().stream()
            .allMatch(entry -> matchesFilter(safeMetadata, entry.getKey(), entry.getValue()));
    }

    private boolean matchesFilter(Map<String, Object> metadata, String key, Object expected) {
        if (expected == null) {
            return true;
        }

        Object actual = metadata.get(key);
        if (actual == null) {
            return false;
        }

        if (expected instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> valuesEqual(actual, item));
        }

        if (expected instanceof Map<?, ?> rangeMap) {
            Double actualValue = parseDouble(actual);
            if (actualValue == null) {
                return false;
            }

            Double min = parseDouble(rangeMap.get("min"));
            Double max = parseDouble(rangeMap.get("max"));

            if (min != null && actualValue < min) {
                return false;
            }
            if (max != null && actualValue > max) {
                return false;
            }
            return true;
        }

        return valuesEqual(actual, expected);
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }
        return String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            if (value instanceof Number number) {
                return number.doubleValue();
            }
            String normalized = String.valueOf(value).replaceAll("[^0-9.\\-]", "");
            if (normalized.isEmpty()) {
                return null;
            }
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
    
    private Map<String, Object> buildAggregatedMetadata(Map<String, Object> requestMetadata,
            String embeddingQuery) {
        Map<String, Object> aggregatedMetadata = new HashMap<>();
        if (requestMetadata != null) {
            aggregatedMetadata.putAll(requestMetadata);
        }
        aggregatedMetadata.put(METADATA_KEY_OPTIMIZED_QUERY_PROVIDED, 
            extractOptimizedQuery(requestMetadata) != null);
        aggregatedMetadata.put(METADATA_KEY_EMBEDDING_QUERY, embeddingQuery);
        return aggregatedMetadata;
    }
}
