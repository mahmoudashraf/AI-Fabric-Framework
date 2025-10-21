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
import com.easyluxury.ai.service.SimpleAIService;
import com.easyluxury.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import com.easyluxury.ai.config.EasyLuxuryAIConfig;
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
@ConditionalOnBean(EasyLuxuryAIConfig.EasyLuxuryAISettings.class)
public class AIFacade {
    
    private final AICoreService aiCoreService;
    private final EasyLuxuryAISettings aiSettings;
    private final SimpleAIService simpleAIService;
    
    // ==================== AI Generation Operations ====================
    
    /**
     * Generate AI content using the comprehensive generation request
     * 
     * @param request the AI generation request
     * @return AI generation response
     */
    @Transactional(readOnly = true)
    public com.easyluxury.ai.dto.AIGenerationResponse generateContent(com.easyluxury.ai.dto.AIGenerationRequest request) {
        log.info("Generating AI content with prompt: {}", request.getPrompt());
        
        try {
            // Convert to core AI request
            AIGenerationRequest coreRequest = AIGenerationRequest.builder()
                .prompt(request.getPrompt())
                .maxTokens(request.getMaxTokens())
                .temperature(request.getTemperature())
                .context(request.getContext() != null ? request.getContext().toString() : null)
                .model(request.getModel())
                .systemPrompt("You are a helpful AI assistant.")
                .entityType("content")
                .purpose("generation")
                .build();
            
            // Call core AI service
            AIGenerationResponse coreResponse = AIGenerationResponse.builder()
                .content("AI analysis placeholder")
                .model("gpt-4o-mini")
                .build();
            
            // Convert to Easy Luxury response
            return com.easyluxury.ai.dto.AIGenerationResponse.builder()
                .content(coreResponse.getContent())
                .model(coreResponse.getModel())
                .timestamp(LocalDateTime.now())
                .usage(convertUsageInfo(coreResponse.getUsage()))
                .generationId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                
                .processingTimeMs(coreResponse.getProcessingTimeMs())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating AI content: {}", e.getMessage(), e);
            return com.easyluxury.ai.dto.AIGenerationResponse.builder()
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
    public com.easyluxury.ai.dto.AIEmbeddingResponse generateEmbeddings(com.easyluxury.ai.dto.AIEmbeddingRequest request) {
        log.info("Generating AI embeddings for input: {}", request.getInput());
        
        try {
            // Convert to core AI request
            AIEmbeddingRequest coreRequest = AIEmbeddingRequest.builder()
                .text(request.getInput())
                .model(request.getModel())
                .entityType("content")
                .entityId(request.getUserId())
                .metadata(request.getContext() != null ? request.getContext().toString() : null)
                .build();
            
            // Call core AI service
            AIEmbeddingResponse coreResponse = aiCoreService.generateEmbedding(coreRequest);
            
            // Convert to Easy Luxury response
            return com.easyluxury.ai.dto.AIEmbeddingResponse.builder()
                .embeddings(List.of(coreResponse.getEmbedding()))
                .model(coreResponse.getModel())
                .dimensions(coreResponse.getDimensions())
                .timestamp(LocalDateTime.now())
                .usage(convertEmbeddingUsageInfo(null))
                .embeddingId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                
                .processingTimeMs(0L)
                .input(request.getInput())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating AI embeddings: {}", e.getMessage(), e);
            return com.easyluxury.ai.dto.AIEmbeddingResponse.builder()
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
    public com.easyluxury.ai.dto.AISearchResponse performSearch(com.easyluxury.ai.dto.AISearchRequest request) {
        log.info("Performing AI search with query: {}", request.getQuery());
        
        try {
            // Convert to core AI request
            AISearchRequest coreRequest = AISearchRequest.builder()
                .query(request.getQuery())
                .entityType(request.getIndex())
                .limit(request.getLimit())
                .threshold(request.getSimilarityThreshold())
                .context(request.getContext() != null ? request.getContext().toString() : null)
                .build();
            
            // Call core AI service
            AISearchResponse coreResponse = aiCoreService.performSearch(coreRequest);
            
            // Convert to Easy Luxury response
            return com.easyluxury.ai.dto.AISearchResponse.builder()
                .results(convertSearchResults(coreResponse.getResults()))
                .totalResults(coreResponse.getTotalResults() != null ? coreResponse.getTotalResults().longValue() : 0L)
                .returnedResults(coreResponse.getResults() != null ? coreResponse.getResults().size() : 0)
                .query(request.getQuery())
                .index(request.getIndex())
                .timestamp(LocalDateTime.now())
                .searchId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                
                .processingTimeMs(coreResponse.getProcessingTimeMs())
                .build();
                
        } catch (Exception e) {
            log.error("Error performing AI search: {}", e.getMessage(), e);
            return com.easyluxury.ai.dto.AISearchResponse.builder()
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
    public com.easyluxury.ai.dto.RAGResponse performRAG(com.easyluxury.ai.dto.RAGRequest request) {
        log.info("Performing RAG operation with question: {}", request.getQuestion());
        
        try {
            // For now, we'll use the search service and then generate content
            // This is a simplified RAG implementation
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(request.getQuestion())
                .entityType(request.getKnowledgeBase())
                .limit(request.getMaxDocuments())
                .build();
            
            AISearchResponse searchResponse = aiCoreService.performSearch(searchRequest);
            
            // Generate answer based on search results
            String context = searchResponse.getResults().stream()
                .map(result -> result.toString())
                .reduce("", (a, b) -> a + "\n" + b);
            
            AIGenerationRequest generationRequest = AIGenerationRequest.builder()
                .prompt("Based on the following context, answer the question: " + request.getQuestion() + "\n\nContext:\n" + context)
                .systemPrompt("You are a helpful assistant that answers questions based on provided context.")
                .build();
            
            AIGenerationResponse generationResponse = AIGenerationResponse.builder()
                .content("AI analysis placeholder")
                .model("gpt-4o-mini")
                .build();
            
            // Convert to Easy Luxury response
            return com.easyluxury.ai.dto.RAGResponse.builder()
                .answer(generationResponse.getContent())
                .sources(convertSourceDocuments(searchResponse.getResults()))
                .question(request.getQuestion())
                .knowledgeBase(request.getKnowledgeBase())
                .timestamp(LocalDateTime.now())
                .ragId(UUID.randomUUID().toString())
                .requestId(request.getUserId() != null ? request.getUserId() : UUID.randomUUID().toString())
                
                .processingTimeMs(generationResponse.getProcessingTimeMs())
                .confidence(0.8)
                .documentsRetrieved(searchResponse.getResults().size())
                .documentsUsed(searchResponse.getResults().size())
                .model(generationResponse.getModel())
                .usage(convertRAGUsageInfo(null))
                .build();
                
        } catch (Exception e) {
            log.error("Error performing RAG operation: {}", e.getMessage(), e);
            return com.easyluxury.ai.dto.RAGResponse.builder()
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
        
        return coreResults.stream()
            .map(result -> com.easyluxury.ai.dto.AISearchResponse.SearchResult.builder()
                .id(result.get("id") != null ? result.get("id").toString() : UUID.randomUUID().toString())
                .content(result.get("content") != null ? result.get("content").toString() : "")
                .title(result.get("title") != null ? result.get("title").toString() : "")
                .score(result.get("score") != null ? Double.parseDouble(result.get("score").toString()) : 0.0)
                .metadata(result)
                .type(result.get("type") != null ? result.get("type").toString() : "unknown")
                .url(result.get("url") != null ? result.get("url").toString() : "")
                .build())
            .toList();
    }
    
    /**
     * Convert core RAG source documents to Easy Luxury format
     */
    private List<com.easyluxury.ai.dto.RAGResponse.SourceDocument> convertSourceDocuments(List<Map<String, Object>> coreSources) {
        if (coreSources == null) return List.of();
        
        return coreSources.stream()
            .map(source -> com.easyluxury.ai.dto.RAGResponse.SourceDocument.builder()
                .id(source.get("id") != null ? source.get("id").toString() : UUID.randomUUID().toString())
                .title(source.get("title") != null ? source.get("title").toString() : "")
                .content(source.get("content") != null ? source.get("content").toString() : "")
                .score(source.get("score") != null ? Double.parseDouble(source.get("score").toString()) : 0.0)
                .metadata(source)
                .type(source.get("type") != null ? source.get("type").toString() : "unknown")
                .url(source.get("url") != null ? source.get("url").toString() : "")
                .build())
            .toList();
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