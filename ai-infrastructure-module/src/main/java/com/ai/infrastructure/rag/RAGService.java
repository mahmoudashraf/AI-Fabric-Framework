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
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.vector.VectorDatabase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
@Service
@RequiredArgsConstructor
public class RAGService {
    
    private final AIProviderConfig config;
    private final AIEmbeddingService embeddingService;
    private final VectorDatabaseService vectorDatabaseService;
    private final VectorDatabase vectorDatabase;
    private final AISearchService searchService;
    
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
            log.debug("Performing RAG query: {} for entity type: {}", query, entityType);
            
            // Generate embedding for query
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(query)
                .entityType(entityType)
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            
            // Perform vector search
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(query)
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
            log.debug("Performing RAG query: {}", request.getQuery());
            
            long startTime = System.currentTimeMillis();
            
            // Generate query embedding
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(request.getQuery())
                .model(config.getOpenaiEmbeddingModel())
                .build();
            
            var embeddingResponse = embeddingService.generateEmbedding(embeddingRequest);
            List<Double> queryVector = embeddingResponse.getEmbedding();
            
            // Create search request
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(request.getQuery())
                .entityType(request.getEntityType())
                .limit(request.getLimit())
                .threshold(request.getThreshold())
                .build();
            
            // Perform search
            AISearchResponse searchResponse;
            if (request.getEnableHybridSearch()) {
                searchResponse = performHybridSearch(queryVector, request.getQuery(), searchRequest);
            } else if (request.getEnableContextualSearch()) {
                searchResponse = performContextualSearch(queryVector, request.getContext().toString(), searchRequest);
            } else {
                searchResponse = vectorDatabase.search(queryVector, searchRequest);
            }
            
            // Build context
            String context = buildContext(searchResponse);
            
            // Generate response (simplified for now)
            String response = generateResponse(request.getQuery(), context);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
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
                .model(config.getOpenaiEmbeddingModel())
                .success(true)
                .hybridSearchUsed(request.getEnableHybridSearch())
                .contextualSearchUsed(request.getEnableContextualSearch())
                .originalQuery(request.getQuery())
                .entityType(request.getEntityType())
                .searchedCategories(request.getCategories())
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
                .model(config.getOpenaiEmbeddingModel())
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
        return RAGResponse.RAGDocument.builder()
            .id((String) result.get("id"))
            .content((String) result.get("content"))
            .title((String) result.get("title"))
            .type((String) result.get("type"))
            .score(((Number) result.getOrDefault("score", 0.0)).doubleValue())
            .similarity(((Number) result.getOrDefault("similarity", 0.0)).doubleValue())
            .metadata((Map<String, Object>) result.getOrDefault("metadata", new HashMap<>()))
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
            log.debug("Performing RAG operation for query: {}", request.getQuery());
            
            // Generate embedding for the query
            AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
                .text(request.getQuery())
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
                .query(request.getQuery())
                .entityType(request.getEntityType())
                .limit(request.getLimit())
                .threshold(request.getThreshold())
                .context(contextString)
                .filters(filtersString)
                .build();
            
            AISearchResponse searchResponse = searchService.search(queryVector, searchRequest);
            
            // Convert search results to RAG response
            List<RAGResponse.RAGDocument> documents = searchResponse.getResults().stream()
                .map(result -> RAGResponse.RAGDocument.builder()
                    .id((String) result.get("id"))
                    .content((String) result.get("content"))
                    .title((String) result.get("title"))
                    .type((String) result.get("type"))
                    .score((Double) result.get("score"))
                    .similarity((Double) result.get("similarity"))
                    .metadata((Map<String, Object>) result.get("metadata"))
                    .build())
                .collect(Collectors.toList());
            
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
                .originalQuery(request.getQuery())
                .entityType(request.getEntityType())
                .model(config.getOpenaiModel())
                .timestamp(java.time.LocalDateTime.now())
                .metadata(request.getMetadata())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing RAG operation", e);
            return RAGResponse.builder()
                .success(false)
                .errorMessage("Failed to perform RAG operation: " + e.getMessage())
                .build();
        }
    }
}
