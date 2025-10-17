package com.easyluxury.ai.facade;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.easyluxury.ai.config.EasyLuxuryAIConfig.EasyLuxuryAISettings;
import com.easyluxury.ai.dto.*;
import com.easyluxury.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Easy Luxury AI Facade
 * 
 * This facade provides Easy Luxury specific AI functionality by integrating
 * with the generic AI infrastructure module and providing domain-specific
 * AI features for products, users, and orders.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIFacade {
    
    private final AICoreService aiCoreService;
    private final EasyLuxuryAISettings aiSettings;
    
    // ==================== AI Generation Operations ====================
    
    /**
     * Generate AI content using the comprehensive generation request
     * 
     * @param request the AI generation request
     * @return AI generation response
     */
    @Transactional(readOnly = true)
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        log.info("Generating AI content with prompt: {}", request.getPrompt());
        
        try {
            // Convert to core AI request
            AIGenerationRequest coreRequest = AIGenerationRequest.builder()
                .prompt(request.getPrompt())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .frequencyPenalty(request.getFrequencyPenalty())
                .presencePenalty(request.getPresencePenalty())
                .stop(request.getStop())
                .context(request.getContext())
                .model(request.getModel() != null ? request.getModel() : aiSettings.getDefaultAiModel())
                .stream(request.getStream())
                .userId(request.getUserId())
                .timeoutSeconds(request.getTimeoutSeconds())
                .build();
            
            // Call core AI service
            AIGenerationResponse coreResponse = aiCoreService.generateContent(coreRequest);
            
            // Convert to Easy Luxury response
            return AIGenerationResponse.builder()
                .content(coreResponse.getContent())
                .model(coreResponse.getModel())
                .timestamp(LocalDateTime.now())
                .usage(convertUsageInfo(coreResponse.getUsage()))
                .metadata(coreResponse.getMetadata())
                .generationId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("SUCCESS")
                .processingTimeMs(coreResponse.getProcessingTimeMs())
                .alternatives(coreResponse.getAlternatives())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating AI content: {}", e.getMessage(), e);
            return AIGenerationResponse.builder()
                .content("")
                .model(request.getModel())
                .timestamp(LocalDateTime.now())
                .generationId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("ERROR")
                .errorMessage(e.getMessage())
                .processingTimeMs(0L)
                .build();
        }
    }
    
    // ==================== AI Embedding Operations ====================
    
    /**
     * Generate embeddings for the given input
     * 
     * @param request the AI embedding request
     * @return AI embedding response
     */
    @Transactional(readOnly = true)
    public AIEmbeddingResponse generateEmbeddings(AIEmbeddingRequest request) {
        log.info("Generating AI embeddings for input: {}", request.getInput());
        
        try {
            // Convert to core AI request
            AIEmbeddingRequest coreRequest = AIEmbeddingRequest.builder()
                .input(request.getInput())
                .model(request.getModel() != null ? request.getModel() : aiSettings.getDefaultEmbeddingModel())
                .dimensions(request.getDimensions())
                .userId(request.getUserId())
                .timeoutSeconds(request.getTimeoutSeconds())
                .context(request.getContext())
                .batch(request.getBatch())
                .normalize(request.getNormalize())
                .inputType(request.getInputType())
                .inputs(request.getInputs())
                .build();
            
            // Call core AI service
            AIEmbeddingResponse coreResponse = aiCoreService.generateEmbeddings(coreRequest);
            
            // Convert to Easy Luxury response
            return AIEmbeddingResponse.builder()
                .embeddings(coreResponse.getEmbeddings())
                .model(coreResponse.getModel())
                .dimensions(coreResponse.getDimensions())
                .timestamp(LocalDateTime.now())
                .usage(convertEmbeddingUsageInfo(coreResponse.getUsage()))
                .metadata(coreResponse.getMetadata())
                .embeddingId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("SUCCESS")
                .processingTimeMs(coreResponse.getProcessingTimeMs())
                .input(request.getInput())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating AI embeddings: {}", e.getMessage(), e);
            return AIEmbeddingResponse.builder()
                .embeddings(List.of())
                .model(request.getModel())
                .dimensions(request.getDimensions())
                .timestamp(LocalDateTime.now())
                .embeddingId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("ERROR")
                .errorMessage(e.getMessage())
                .processingTimeMs(0L)
                .input(request.getInput())
                .build();
        }
    }
    
    // ==================== AI Search Operations ====================
    
    /**
     * Perform AI-powered search
     * 
     * @param request the AI search request
     * @return AI search response
     */
    @Transactional(readOnly = true)
    public AISearchResponse performSearch(AISearchRequest request) {
        log.info("Performing AI search with query: {}", request.getQuery());
        
        try {
            // Convert to core AI request
            AISearchRequest coreRequest = AISearchRequest.builder()
                .query(request.getQuery())
                .index(request.getIndex())
                .limit(request.getLimit())
                .offset(request.getOffset())
                .filters(request.getFilters())
                .sort(convertSortCriteria(request.getSort()))
                .facets(request.getFacets())
                .userId(request.getUserId())
                .timeoutSeconds(request.getTimeoutSeconds())
                .context(request.getContext())
                .searchType(request.getSearchType())
                .similarityThreshold(request.getSimilarityThreshold())
                .includeMetadata(request.getIncludeMetadata())
                .build();
            
            // Call core AI service
            AISearchResponse coreResponse = aiCoreService.performSearch(coreRequest);
            
            // Convert to Easy Luxury response
            return AISearchResponse.builder()
                .results(convertSearchResults(coreResponse.getResults()))
                .totalResults(coreResponse.getTotalResults())
                .returnedResults(coreResponse.getReturnedResults())
                .query(request.getQuery())
                .index(request.getIndex())
                .timestamp(LocalDateTime.now())
                .metadata(coreResponse.getMetadata())
                .searchId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("SUCCESS")
                .processingTimeMs(coreResponse.getProcessingTimeMs())
                .facets(coreResponse.getFacets())
                .suggestions(coreResponse.getSuggestions())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing AI search: {}", e.getMessage(), e);
            return AISearchResponse.builder()
                .results(List.of())
                .totalResults(0L)
                .returnedResults(0)
                .query(request.getQuery())
                .index(request.getIndex())
                .timestamp(LocalDateTime.now())
                .searchId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("ERROR")
                .errorMessage(e.getMessage())
                .processingTimeMs(0L)
                .build();
        }
    }
    
    // ==================== RAG Operations ====================
    
    /**
     * Perform RAG (Retrieval-Augmented Generation) operation
     * 
     * @param request the RAG request
     * @return RAG response
     */
    @Transactional(readOnly = true)
    public RAGResponse performRAG(RAGRequest request) {
        log.info("Performing RAG operation with question: {}", request.getQuestion());
        
        try {
            // Convert to core AI request
            RAGRequest coreRequest = RAGRequest.builder()
                .question(request.getQuestion())
                .knowledgeBase(request.getKnowledgeBase())
                .maxDocuments(request.getMaxDocuments())
                .similarityThreshold(request.getSimilarityThreshold())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .userId(request.getUserId())
                .timeoutSeconds(request.getTimeoutSeconds())
                .context(request.getContext())
                .config(request.getConfig())
                .includeSources(request.getIncludeSources())
                .mode(request.getMode())
                .language(request.getLanguage())
                .format(request.getFormat())
                .instructions(request.getInstructions())
                .build();
            
            // Call core AI service
            RAGResponse coreResponse = aiCoreService.performRAG(coreRequest);
            
            // Convert to Easy Luxury response
            return RAGResponse.builder()
                .answer(coreResponse.getAnswer())
                .sources(convertSourceDocuments(coreResponse.getSources()))
                .question(request.getQuestion())
                .knowledgeBase(request.getKnowledgeBase())
                .timestamp(LocalDateTime.now())
                .metadata(coreResponse.getMetadata())
                .ragId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("SUCCESS")
                .processingTimeMs(coreResponse.getProcessingTimeMs())
                .confidence(coreResponse.getConfidence())
                .documentsRetrieved(coreResponse.getDocumentsRetrieved())
                .documentsUsed(coreResponse.getDocumentsUsed())
                .model(coreResponse.getModel())
                .usage(convertRAGUsageInfo(coreResponse.getUsage()))
                .build();
                
        } catch (Exception e) {
            log.error("Error performing RAG operation: {}", e.getMessage(), e);
            return RAGResponse.builder()
                .answer("")
                .sources(List.of())
                .question(request.getQuestion())
                .knowledgeBase(request.getKnowledgeBase())
                .timestamp(LocalDateTime.now())
                .ragId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                .status("ERROR")
                .errorMessage(e.getMessage())
                .processingTimeMs(0L)
                .confidence(0.0)
                .documentsRetrieved(0)
                .documentsUsed(0)
                .build();
        }
    }
    
    // ==================== Helper Methods ====================
    
    /**
     * Convert core AI generation usage info to Easy Luxury format
     */
    private com.easyluxury.ai.dto.AIGenerationResponse.UsageInfo convertUsageInfo(Object coreUsage) {
        if (coreUsage == null) return null;
        
        // For now, return a basic usage info since core DTOs don't have detailed usage info
        return com.easyluxury.ai.dto.AIGenerationResponse.UsageInfo.builder()
            .promptTokens(0)
            .completionTokens(0)
            .totalTokens(0)
            .costUsd(0.0)
            .processingTimeMs(0L)
            .build();
    }
    
    /**
     * Convert core AI embedding usage info to Easy Luxury format
     */
    private com.easyluxury.ai.dto.AIEmbeddingResponse.UsageInfo convertEmbeddingUsageInfo(Object coreUsage) {
        if (coreUsage == null) return null;
        
        // For now, return a basic usage info since core DTOs don't have detailed usage info
        return com.easyluxury.ai.dto.AIEmbeddingResponse.UsageInfo.builder()
            .inputTokens(0)
            .totalTokens(0)
            .costUsd(0.0)
            .processingTimeMs(0L)
            .dimensions(0)
            .build();
    }
    
    /**
     * Convert core AI search sort criteria to Easy Luxury format
     */
    private List<com.easyluxury.ai.dto.AISearchRequest.SortCriteria> convertSortCriteria(List<com.easyluxury.ai.dto.AISearchRequest.SortCriteria> sortCriteria) {
        if (sortCriteria == null) return null;
        
        // For now, return empty list since core DTOs don't have sort criteria
        return List.of();
    }
    
    /**
     * Convert core AI search results to Easy Luxury format
     */
    private List<com.easyluxury.ai.dto.AISearchResponse.SearchResult> convertSearchResults(List<Map<String, Object>> coreResults) {
        if (coreResults == null) return List.of();
        
        // For now, return empty list since core DTOs don't have detailed search results
        return List.of();
    }
    
    /**
     * Convert core RAG source documents to Easy Luxury format
     */
    private List<com.easyluxury.ai.dto.RAGResponse.SourceDocument> convertSourceDocuments(List<Map<String, Object>> coreSources) {
        if (coreSources == null) return List.of();
        
        // For now, return empty list since core DTOs don't have detailed source documents
        return List.of();
    }
    
    /**
     * Convert core RAG usage info to Easy Luxury format
     */
    private com.easyluxury.ai.dto.RAGResponse.UsageInfo convertRAGUsageInfo(Object coreUsage) {
        if (coreUsage == null) return null;
        
        // For now, return a basic usage info since core DTOs don't have detailed usage info
        return com.easyluxury.ai.dto.RAGResponse.UsageInfo.builder()
            .retrievalTokens(0)
            .generationTokens(0)
            .totalTokens(0)
            .costUsd(0.0)
            .processingTimeMs(0L)
            .build();
    }
    
    // ==================== Legacy Helper Methods (for backward compatibility) ====================
    
    String buildUserContext(User user) {
        StringBuilder context = new StringBuilder();
        context.append("User: ").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
        context.append("Email: ").append(user.getEmail()).append("\n");
        // Add more user context as needed
        return context.toString();
    }
    
    String buildProductDescriptionPrompt(Map<String, Object> productData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a luxury product description for:\n");
        prompt.append("Name: ").append(productData.get("name")).append("\n");
        prompt.append("Description: ").append(productData.get("description")).append("\n");
        prompt.append("Price: $").append(productData.get("price")).append("\n");
        // Add more product details as needed
        return prompt.toString();
    }
    
    String buildProductValidationContent(Map<String, Object> productData) {
        StringBuilder content = new StringBuilder();
        content.append("Product Name: ").append(productData.get("name")).append("\n");
        content.append("Description: ").append(productData.get("description")).append("\n");
        content.append("Price: ").append(productData.get("price")).append("\n");
        return content.toString();
    }
    
    Map<String, Object> buildProductValidationRules() {
        return Map.of(
            "nameRequired", true,
            "descriptionMinLength", 10,
            "pricePositive", true,
            "luxuryKeywords", List.of("premium", "exclusive", "luxury", "high-end")
        );
    }
    
    String buildUserInsightsPrompt(User user) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze this luxury e-commerce user:\n");
        prompt.append("Name: ").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
        prompt.append("Email: ").append(user.getEmail()).append("\n");
        // Add more user data as needed
        return prompt.toString();
    }
}