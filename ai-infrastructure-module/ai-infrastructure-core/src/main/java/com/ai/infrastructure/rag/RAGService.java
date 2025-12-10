package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.core.AISearchService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.dto.PIIMode;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.vector.VectorDatabase;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) Service
 * 
 * This service provides RAG capabilities by combining retrieval and generation.
 * It can index content, perform semantic search, and generate context-aware responses.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service("ragService")
@RequiredArgsConstructor
public class RAGService {
    
    private final AIProviderConfig config;
    private final AIEmbeddingService embeddingService;
    private final VectorDatabaseService vectorDatabaseService;
    private final VectorDatabase vectorDatabase;
    private final AISearchService searchService;
    private final PIIDetectionService piiDetectionService;
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {};
    
    /**
     * Index content for RAG
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     * @param content the text content to index
     * @param metadata additional metadata
     */
    public void indexContent(String entityType, String entityId, String content, Map<String, Object> metadata) {
        try {
            log.debug("Indexing content for entity {} of type {}", entityId, entityType);
            
            // Generate embedding for content
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(content)
                .entityType(entityType)
                .entityId(entityId)
                .metadata(metadata != null ? metadata.toString() : null)
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            
            // Store in vector database
            vectorDatabaseService.storeVector(entityType, entityId, content, 
                embeddingResponse.getEmbedding(), metadata);
            
            log.debug("Successfully indexed content for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error indexing content", e);
            throw new AIServiceException("Failed to index content", e);
        }
    }
    
    /**
     * Perform RAG query
     * 
     * @param query the search query
     * @param entityType the type of entities to search
     * @param limit maximum number of results
     * @return RAG response with retrieved context
     */
    public AISearchResponse performRAGQuery(String query, String entityType, int limit) {
        try {
            PIIDetectionResult piiDetectionResult = piiDetectionService.detectAndProcess(query);
            log.debug("Performing RAG query for entity type={} (piiDetected={}, mode={})",
                entityType, piiDetectionResult.isPiiDetected(),
                Optional.ofNullable(piiDetectionResult.getModeApplied()).map(Enum::name).orElse(PIIMode.PASS_THROUGH.name()));
            String sanitizedQuery = piiDetectionResult.getProcessedQuery();
            
            // Generate embedding for query
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(sanitizedQuery)
                .entityType(entityType)
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            
            // Perform vector search
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(sanitizedQuery)
                .entityType(entityType)
                .limit(limit)
                .threshold(0.7)
                .build();
            
            AISearchResponse searchResponse = vectorDatabaseService.search(embeddingResponse.getEmbedding(), searchRequest);
            
            log.debug("RAG query completed with {} results", searchResponse.getTotalResults());
            
            return searchResponse;
            
        } catch (Exception e) {
            log.error("Error performing RAG query", e);
            throw new AIServiceException("Failed to perform RAG query", e);
        }
    }
    
    /**
     * Build context from search results
     * 
     * @param searchResponse the search results
     * @return formatted context string
     */
    public String buildContext(AISearchResponse searchResponse) {
        if (searchResponse.getResults().isEmpty()) {
            return "No relevant context found.";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("Relevant Context:\n\n");
        
        for (int i = 0; i < searchResponse.getResults().size(); i++) {
            Map<String, Object> result = searchResponse.getResults().get(i);
            context.append(String.format("%d. %s (Score: %.3f)\n", 
                i + 1, result.get("content"), result.get("score")));
        }
        
        return context.toString();
    }
    
    /**
     * Remove content from RAG index
     * 
     * @param entityType the type of entity
     * @param entityId the unique identifier
     */
    public void removeContent(String entityType, String entityId) {
        try {
            log.debug("Removing content for entity {} of type {}", entityId, entityType);
            
            vectorDatabaseService.removeVector(entityType, entityId);
            
            log.debug("Successfully removed content for entity {} of type {}", entityId, entityType);
            
        } catch (Exception e) {
            log.error("Error removing content", e);
            throw new AIServiceException("Failed to remove content", e);
        }
    }
    
    /**
     * Get RAG statistics
     * 
     * @return map of RAG statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIndexed", vectorDatabaseService.getStatistics());
        stats.put("vectorDatabase", vectorDatabase.getStatistics());
        return stats;
    }
    
    /**
     * Perform RAG query with advanced features
     * 
     * @param request the RAG request
     * @return RAG response with generated content
     */
    public RAGResponse performRAGQuery(RAGRequest request) {
        try {
            PIIDetectionResult piiDetectionResult = piiDetectionService.detectAndProcess(request.getQuery());
            String sanitizedQuery = piiDetectionResult.getProcessedQuery();
            String embeddingQuery = resolveEmbeddingQuery(request, sanitizedQuery);
            log.debug("Performing RAG query (entityType={}, requestId={}, piiDetected={}, mode={})",
                request.getEntityType(),
                request.getRequestId(),
                piiDetectionResult.isPiiDetected(),
                Optional.ofNullable(piiDetectionResult.getModeApplied()).map(Enum::name).orElse(PIIMode.PASS_THROUGH.name()));
            
            long startTime = System.currentTimeMillis();
            
            // Generate query embedding
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(embeddingQuery)
                .model(config.resolveEmbeddingDefaults().model())
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            List<Double> queryVector = embeddingResponse.getEmbedding();
            
            // Create search request
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(embeddingQuery)
                .entityType(request.getEntityType())
                .limit(request.getLimit())
                .threshold(request.getThreshold())
                .build();
            
            // Perform search
            AISearchResponse searchResponse;
            if (request.getEnableHybridSearch()) {
                searchResponse = performHybridSearch(queryVector, sanitizedQuery, searchRequest);
            } else if (request.getEnableContextualSearch()) {
                searchResponse = performContextualSearch(queryVector, request.getContext().toString(), searchRequest);
            } else {
                searchResponse = vectorDatabase.search(queryVector, searchRequest);
            }
            
            // Build context
            String context = buildContext(searchResponse);
            
            // Generate response (simplified for now)
            String response = generateResponse(sanitizedQuery, context);
            
            long processingTime = System.currentTimeMillis() - startTime;

            Map<String, Object> aggregatedMetadata = new HashMap<>();
            if (request.getMetadata() != null) {
                aggregatedMetadata.putAll(request.getMetadata());
            }
            aggregatedMetadata.put("piiDetection", Map.of(
                "detected", piiDetectionResult.isPiiDetected(),
                "mode", Optional.ofNullable(piiDetectionResult.getModeApplied()).map(Enum::name).orElse(PIIMode.PASS_THROUGH.name()),
                "detectionsCount", piiDetectionResult.getDetections().size(),
                "encryptedOriginalStored", piiDetectionResult.getEncryptedOriginalQuery() != null
            ));
            aggregatedMetadata.put("optimizedQueryProvided", extractOptimizedQuery(request.getMetadata()) != null);
            aggregatedMetadata.put("embeddingQuery", embeddingQuery);
            
            return RAGResponse.builder()
                .response(response)
                .context(context)
                .documents(convertToRAGDocuments(searchResponse.getResults()))
                .totalDocuments(searchResponse.getTotalResults())
                .usedDocuments(Math.min(searchResponse.getTotalResults(), request.getLimit()))
                .confidenceScore(calculateConfidence(searchResponse))
                .relevanceScores(searchResponse.getResults().stream()
                    .map(doc -> (Double) doc.get("similarity"))
                    .collect(Collectors.toList()))
                .processingTimeMs(processingTime)
                .requestId(request.getRequestId())
                .model(config.resolveEmbeddingDefaults().model())
                .success(true)
                .hybridSearchUsed(request.getEnableHybridSearch())
                .contextualSearchUsed(request.getEnableContextualSearch())
                .originalQuery(sanitizedQuery)
                .entityType(request.getEntityType())
                .searchedCategories(request.getCategories())
                .piiDetectionResult(piiDetectionResult)
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
    
    /**
     * Perform hybrid search combining vector and text search
     */
    private AISearchResponse performHybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
        // This would integrate with the VectorSearchService for hybrid search
        // For now, fall back to regular vector search
        return vectorDatabase.search(queryVector, request);
    }
    
    /**
     * Perform contextual search with additional context
     */
    private AISearchResponse performContextualSearch(List<Double> queryVector, String context, AISearchRequest request) {
        // This would integrate with the VectorSearchService for contextual search
        // For now, fall back to regular vector search
        return vectorDatabase.search(queryVector, request);
    }
    
    /**
     * Generate response based on query and context
     */
    private String generateResponse(String query, String context) {
        // This is a simplified response generation
        // In a real implementation, this would use an LLM to generate the response
        if (context.isEmpty()) {
            return "I don't have enough information to answer your question: " + query;
        }
        
        return "Based on the available information: " + context.substring(0, Math.min(context.length(), 500)) + "...";
    }
    
    /**
     * Calculate confidence score for the response
     */
    private double calculateConfidence(AISearchResponse searchResponse) {
        if (searchResponse.getResults().isEmpty()) {
            return 0.0;
        }
        
        // Calculate average similarity score as confidence
        return searchResponse.getResults().stream()
            .mapToDouble(doc -> (Double) doc.get("similarity"))
            .average()
            .orElse(0.0);
    }
    
    /**
     * Convert search results to RAG documents
     */
    private List<RAGResponse.RAGDocument> convertToRAGDocuments(List<Map<String, Object>> results) {
        return results.stream()
            .map(this::convertToRAGDocument)
            .collect(Collectors.toList());
    }
    
    /**
     * Convert a single search result to RAG document
     */
    private RAGResponse.RAGDocument convertToRAGDocument(Map<String, Object> result) {
        Map<String, Object> metadata = normalizeMetadata(result.get("metadata"));

        Object scoreValue = result.getOrDefault("score", 0.0);
        Object similarityValue = result.getOrDefault("similarity", 0.0);

        double score = scoreValue instanceof Number number ? number.doubleValue() : parseDouble(scoreValue) != null ? parseDouble(scoreValue) : 0.0;
        double similarity = similarityValue instanceof Number number ? number.doubleValue() : parseDouble(similarityValue) != null ? parseDouble(similarityValue) : 0.0;

        return RAGResponse.RAGDocument.builder()
            .id((String) result.get("id"))
            .content((String) result.get("content"))
            .title((String) result.get("title"))
            .type((String) result.get("type"))
            .score(score)
            .similarity(similarity)
            .metadata(metadata)
            .build();
    }
    
    /**
     * Perform RAG operation
     * 
     * @param request the RAG request
     * @return RAG response
     */
    public RAGResponse performRag(RAGRequest request) {
        try {
            PIIDetectionResult piiDetectionResult = piiDetectionService.detectAndProcess(request.getQuery());
            String sanitizedQuery = piiDetectionResult.getProcessedQuery();
            String embeddingQuery = resolveEmbeddingQuery(request, sanitizedQuery);
            log.debug("Performing RAG operation (entityType={}, requestId={}, piiDetected={}, mode={})",
                request.getEntityType(),
                request.getRequestId(),
                piiDetectionResult.isPiiDetected(),
                Optional.ofNullable(piiDetectionResult.getModeApplied()).map(Enum::name).orElse(PIIMode.PASS_THROUGH.name()));

            // Generate embedding for the query

            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(embeddingQuery)
                .build();
            
            AIEmbeddingResponse embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            List<Double> queryVector = embeddingResponse.getEmbedding();
            
            // Perform semantic search using the existing search method
            String contextString = null;
            if (request.getContext() != null) {
                contextString = request.getContext().toString();
            }
            
            String filtersString = null;
            if (request.getFilters() != null) {
                filtersString = request.getFilters().toString();
            }
            
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

            // Convert search results to RAG response applying metadata filters if provided
            List<RAGResponse.RAGDocument> documents = searchResponse.getResults().stream()
                .map(result -> {
                    Map<String, Object> normalizedMetadata = normalizeMetadata(result.get("metadata"));
                    if (!matchesFilters(normalizedMetadata, filters)) {
                        return null;
                    }

                    return RAGResponse.RAGDocument.builder()
                        .id((String) result.get("id"))
                        .content((String) result.get("content"))
                        .title((String) result.get("title"))
                        .type((String) result.get("type"))
                        .score((Double) result.get("score"))
                        .similarity((Double) result.get("similarity"))
                        .metadata(normalizedMetadata)
                        .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            Map<String, Object> aggregatedMetadata = new HashMap<>();
            if (request.getMetadata() != null) {
                aggregatedMetadata.putAll(request.getMetadata());
            }
            aggregatedMetadata.put("piiDetection", Map.of(
                "detected", piiDetectionResult.isPiiDetected(),
                "mode", Optional.ofNullable(piiDetectionResult.getModeApplied()).map(Enum::name).orElse(PIIMode.PASS_THROUGH.name()),
                "detectionsCount", piiDetectionResult.getDetections().size(),
                "encryptedOriginalStored", piiDetectionResult.getEncryptedOriginalQuery() != null
            ));
            aggregatedMetadata.put("optimizedQueryProvided", extractOptimizedQuery(request.getMetadata()) != null);
            aggregatedMetadata.put("embeddingQuery", embeddingQuery);
            
            return RAGResponse.builder()
                .documents(documents)
                .totalDocuments(searchResponse.getTotalResults())
                .usedDocuments(documents.size())
                .relevanceScores(documents.stream().map(RAGResponse.RAGDocument::getScore).collect(Collectors.toList()))
                .success(true)
                .totalResults(searchResponse.getTotalResults())
                .returnedResults(documents.size())
                .maxScore(documents.stream().mapToDouble(RAGResponse.RAGDocument::getScore).max().orElse(0.0))
                .averageScore(documents.stream().mapToDouble(RAGResponse.RAGDocument::getScore).average().orElse(0.0))
                .processingTimeMs(searchResponse.getProcessingTimeMs())
                .requestId(request.getRequestId())
                .originalQuery(sanitizedQuery)
                .entityType(request.getEntityType())
                .model(config.resolveLlmDefaults().model())
                .timestamp(java.time.LocalDateTime.now())
                .piiDetectionResult(piiDetectionResult)
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
    
    private String resolveEmbeddingQuery(RAGRequest request, String sanitizedQuery) {
        String optimized = extractOptimizedQuery(request.getMetadata());
        if (StringUtils.hasText(optimized)) {
            try {
                return piiDetectionService.detectAndProcess(optimized).getProcessedQuery();
            } catch (Exception ex) {
                log.debug("Unable to sanitize optimized query, using as provided: {}", ex.getMessage());
                return optimized;
            }
        }
        return sanitizedQuery;
    }

    private String extractOptimizedQuery(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Object candidate = metadata.get("optimizedQuery");
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
                return Collections.singletonMap("raw", metadataJson);
            }
        }
        return Collections.emptyMap();
    }
    
    private String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
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
}
