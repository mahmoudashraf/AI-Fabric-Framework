package com.ai.infrastructure.rag;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

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
        return vectorDatabaseService.getStatistics();
    }
}
