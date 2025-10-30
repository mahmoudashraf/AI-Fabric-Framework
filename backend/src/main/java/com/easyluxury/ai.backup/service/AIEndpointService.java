package com.easyluxury.ai.service;

import com.easyluxury.ai.dto.*;
import com.easyluxury.ai.facade.AIFacade;
import com.easyluxury.ai.config.EasyLuxuryAIConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * AI Endpoint Service
 * 
 * Service layer for AI endpoint operations providing business logic
 * and validation for AI operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(EasyLuxuryAIConfig.EasyLuxuryAISettings.class)
public class AIEndpointService {
    
    private final AIFacade aiFacade;
    
    // ==================== AI Generation Operations ====================
    
    /**
     * Process AI generation request with validation and logging
     * 
     * @param request the AI generation request
     * @return processed AI generation response
     */
    @Transactional(readOnly = true)
    public AIGenerationResponse processGenerationRequest(AIGenerationRequest request) {
        log.info("Processing AI generation request for user: {}", request.getUserId());
        
        // Validate request
        validateGenerationRequest(request);
        
        // Add request metadata
        request.setUserId(request.getUserId() != null ? request.getUserId() : "anonymous");
        request.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30);
        
        // Process through facade
        AIGenerationResponse response = aiFacade.generateContent(request);
        
        // Add processing metadata
        response.setRequestId(request.getUserId());
        response.setTimestamp(LocalDateTime.now());
        
        log.info("AI generation request processed successfully. Generation ID: {}", response.getGenerationId());
        
        return response;
    }
    
    /**
     * Process quick generation request with default parameters
     * 
     * @param prompt the generation prompt
     * @param maxTokens maximum tokens to generate
     * @param temperature generation temperature
     * @param userId user identifier
     * @return processed AI generation response
     */
    @Transactional(readOnly = true)
    public AIGenerationResponse processQuickGeneration(String prompt, Integer maxTokens, Double temperature, String userId) {
        log.info("Processing quick AI generation request for user: {}", userId);
        
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt(prompt)
            .maxTokens(maxTokens != null ? maxTokens : 500)
            .temperature(temperature != null ? temperature : 0.7)
            .userId(userId)
            .timeoutSeconds(30)
            .build();
        
        return processGenerationRequest(request);
    }
    
    // ==================== AI Embedding Operations ====================
    
    /**
     * Process AI embedding request with validation and logging
     * 
     * @param request the AI embedding request
     * @return processed AI embedding response
     */
    @Transactional(readOnly = true)
    public AIEmbeddingResponse processEmbeddingRequest(AIEmbeddingRequest request) {
        log.info("Processing AI embedding request for user: {}", request.getUserId());
        
        // Validate request
        validateEmbeddingRequest(request);
        
        // Add request metadata
        request.setUserId(request.getUserId() != null ? request.getUserId() : "anonymous");
        request.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30);
        
        // Process through facade
        AIEmbeddingResponse response = aiFacade.generateEmbeddings(request);
        
        // Add processing metadata
        response.setRequestId(request.getUserId());
        response.setTimestamp(LocalDateTime.now());
        
        log.info("AI embedding request processed successfully. Embedding ID: {}", response.getEmbeddingId());
        
        return response;
    }
    
    // ==================== AI Search Operations ====================
    
    /**
     * Process AI search request with validation and logging
     * 
     * @param request the AI search request
     * @return processed AI search response
     */
    @Transactional(readOnly = true)
    public AISearchResponse processSearchRequest(AISearchRequest request) {
        log.info("Processing AI search request for user: {}", request.getUserId());
        
        // Validate request
        validateSearchRequest(request);
        
        // Add request metadata
        request.setUserId(request.getUserId() != null ? request.getUserId() : "anonymous");
        request.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 30);
        
        // Process through facade
        AISearchResponse response = aiFacade.performSearch(request);
        
        // Add processing metadata
        response.setRequestId(request.getUserId());
        response.setTimestamp(LocalDateTime.now());
        
        log.info("AI search request processed successfully. Search ID: {}", response.getSearchId());
        
        return response;
    }
    
    /**
     * Process quick search request with default parameters
     * 
     * @param query the search query
     * @param limit maximum results to return
     * @param index search index
     * @param userId user identifier
     * @return processed AI search response
     */
    @Transactional(readOnly = true)
    public AISearchResponse processQuickSearch(String query, Integer limit, String index, String userId) {
        log.info("Processing quick AI search request for user: {}", userId);
        
        AISearchRequest request = AISearchRequest.builder()
            .query(query)
            .limit(limit != null ? limit : 10)
            .index(index)
            .userId(userId)
            .timeoutSeconds(30)
            .build();
        
        return processSearchRequest(request);
    }
    
    // ==================== RAG Operations ====================
    
    /**
     * Process RAG request with validation and logging
     * 
     * @param request the RAG request
     * @return processed RAG response
     */
    @Transactional(readOnly = true)
    public RAGResponse processRAGRequest(RAGRequest request) {
        log.info("Processing RAG request for user: {}", request.getUserId());
        
        // Validate request
        validateRAGRequest(request);
        
        // Add request metadata
        request.setUserId(request.getUserId() != null ? request.getUserId() : "anonymous");
        request.setTimeoutSeconds(request.getTimeoutSeconds() != null ? request.getTimeoutSeconds() : 60);
        
        // Process through facade
        RAGResponse response = aiFacade.performRAG(request);
        
        // Add processing metadata
        response.setRequestId(request.getUserId());
        response.setTimestamp(LocalDateTime.now());
        
        log.info("RAG request processed successfully. RAG ID: {}", response.getRagId());
        
        return response;
    }
    
    // ==================== Validation Methods ====================
    
    /**
     * Validate AI generation request
     * 
     * @param request the request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateGenerationRequest(AIGenerationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Generation request cannot be null");
        }
        
        if (request.getPrompt() == null || request.getPrompt().trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        
        if (request.getPrompt().length() > 4000) {
            throw new IllegalArgumentException("Prompt cannot exceed 4000 characters");
        }
        
        if (request.getMaxTokens() != null && (request.getMaxTokens() < 1 || request.getMaxTokens() > 4000)) {
            throw new IllegalArgumentException("Max tokens must be between 1 and 4000");
        }
        
        if (request.getTemperature() != null && (request.getTemperature() < 0.0 || request.getTemperature() > 2.0)) {
            throw new IllegalArgumentException("Temperature must be between 0.0 and 2.0");
        }
    }
    
    /**
     * Validate AI embedding request
     * 
     * @param request the request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateEmbeddingRequest(AIEmbeddingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Embedding request cannot be null");
        }
        
        if (request.getInput() == null || request.getInput().trim().isEmpty()) {
            throw new IllegalArgumentException("Input cannot be null or empty");
        }
        
        if (request.getInput().length() > 8000) {
            throw new IllegalArgumentException("Input cannot exceed 8000 characters");
        }
        
        if (request.getDimensions() != null && (request.getDimensions() < 1 || request.getDimensions() > 3072)) {
            throw new IllegalArgumentException("Dimensions must be between 1 and 3072");
        }
    }
    
    /**
     * Validate AI search request
     * 
     * @param request the request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateSearchRequest(AISearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Search request cannot be null");
        }
        
        if (request.getQuery() == null || request.getQuery().trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }
        
        if (request.getQuery().length() > 1000) {
            throw new IllegalArgumentException("Query cannot exceed 1000 characters");
        }
        
        if (request.getLimit() != null && (request.getLimit() < 1 || request.getLimit() > 100)) {
            throw new IllegalArgumentException("Limit must be between 1 and 100");
        }
        
        if (request.getOffset() != null && request.getOffset() < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }
    }
    
    /**
     * Validate RAG request
     * 
     * @param request the request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRAGRequest(RAGRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("RAG request cannot be null");
        }
        
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            throw new IllegalArgumentException("Question cannot be null or empty");
        }
        
        if (request.getQuestion().length() > 2000) {
            throw new IllegalArgumentException("Question cannot exceed 2000 characters");
        }
        
        if (request.getMaxDocuments() != null && (request.getMaxDocuments() < 1 || request.getMaxDocuments() > 20)) {
            throw new IllegalArgumentException("Max documents must be between 1 and 20");
        }
        
        if (request.getMaxTokens() != null && (request.getMaxTokens() < 100 || request.getMaxTokens() > 4000)) {
            throw new IllegalArgumentException("Max tokens must be between 100 and 4000");
        }
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Get service statistics
     * 
     * @return service statistics
     */
    public Map<String, Object> getServiceStatistics() {
        return Map.of(
            "serviceName", "AIEndpointService",
            "version", "1.0.0",
            "timestamp", LocalDateTime.now(),
            "status", "ACTIVE"
        );
    }
    
    /**
     * Health check for the service
     * 
     * @return health status
     */
    public Map<String, Object> healthCheck() {
        return Map.of(
            "service", "AIEndpointService",
            "status", "HEALTHY",
            "timestamp", LocalDateTime.now(),
            "uptime", System.currentTimeMillis()
        );
    }
}