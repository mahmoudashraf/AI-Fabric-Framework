package com.ai.infrastructure.core;

import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.exception.AIServiceException;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for AI embedding generation
 * 
 * This service handles the generation of vector embeddings for text content
 * using OpenAI's embedding models. It provides caching and batch processing
 * capabilities for efficient embedding generation.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
// @Service // Removed - already defined as @Bean in AIInfrastructureAutoConfiguration
@RequiredArgsConstructor
public class AIEmbeddingService {
    
    private final AIProviderConfig config;
    private OpenAiService openAiService;
    
    // Performance metrics
    private final AtomicLong totalEmbeddingsGenerated = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> cacheHits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheMisses = new ConcurrentHashMap<>();
    
    /**
     * Initialize OpenAI service with configuration
     */
    private void initializeOpenAI() {
        if (openAiService == null) {
            openAiService = new OpenAiService(
                config.getOpenaiApiKey(),
                Duration.ofSeconds(config.getOpenaiTimeout())
            );
        }
    }
    
    /**
     * Generate embedding for text content with caching
     * 
     * @param request the embedding request
     * @return embedding response with vector data
     */
    @Cacheable(value = "embeddings", key = "#request.text + '_' + #request.model")
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        try {
            initializeOpenAI();
            
            log.debug("Generating embedding for text: {}", request.getText());
            
            long startTime = System.currentTimeMillis();
            
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(request.getModel() != null ? request.getModel() : config.getOpenaiEmbeddingModel())
                .input(List.of(request.getText()))
                .build();
            
            EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
            var embedding = result.getData().get(0).getEmbedding();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Update metrics
            totalEmbeddingsGenerated.incrementAndGet();
            totalProcessingTime.addAndGet(processingTime);
            
            log.debug("Successfully generated embedding with {} dimensions in {}ms", 
                embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(request.getModel() != null ? request.getModel() : config.getOpenaiEmbeddingModel())
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            throw new AIServiceException("Failed to generate embedding", e);
        }
    }
    
    /**
     * Generate embeddings for multiple texts in batch with optimization
     * 
     * @param texts list of texts to embed
     * @param entityType type of entity for context
     * @return list of embedding responses
     */
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts, String entityType) {
        try {
            initializeOpenAI();
            
            log.debug("Generating embeddings for {} texts", texts.size());
            
            long startTime = System.currentTimeMillis();
            
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(config.getOpenaiEmbeddingModel())
                .input(texts)
                .build();
            
            EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            List<AIEmbeddingResponse> responses = result.getData().stream()
                .map(data -> AIEmbeddingResponse.builder()
                    .embedding(data.getEmbedding())
                    .model(config.getOpenaiEmbeddingModel())
                    .dimensions(data.getEmbedding().size())
                    .processingTimeMs(processingTime / texts.size())
                    .requestId(UUID.randomUUID().toString())
                    .build())
                .toList();
            
            log.debug("Successfully generated {} embeddings in {}ms", 
                responses.size(), processingTime);
            
            return responses;
                
        } catch (Exception e) {
            log.error("Error generating batch embeddings", e);
            throw new AIServiceException("Failed to generate batch embeddings", e);
        }
    }
    
    /**
     * Preprocess text for embedding generation
     * 
     * @param text the text to preprocess
     * @return preprocessed text
     */
    public String preprocessText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        // Basic text preprocessing
        return text.trim()
            .replaceAll("\\s+", " ") // Replace multiple spaces with single space
            .replaceAll("[\\r\\n]+", " ") // Replace line breaks with spaces
            .substring(0, Math.min(text.length(), 8000)); // Limit to 8000 characters
    }
    
    /**
     * Chunk text for embedding generation
     * 
     * @param text the text to chunk
     * @param maxChunkSize maximum size of each chunk
     * @return list of text chunks
     */
    public List<String> chunkText(String text, int maxChunkSize) {
        if (text == null || text.trim().isEmpty()) {
            return List.of();
        }
        
        String preprocessed = preprocessText(text);
        
        if (preprocessed.length() <= maxChunkSize) {
            return List.of(preprocessed);
        }
        
        // Simple chunking by sentences
        String[] sentences = preprocessed.split("[.!?]+");
        List<String> chunks = new java.util.ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentences) {
            if (currentChunk.length() + sentence.length() + 1 <= maxChunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append(" ");
                }
                currentChunk.append(sentence.trim());
            } else {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder(sentence.trim());
                }
            }
        }
        
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }
        
        return chunks;
    }
    
    /**
     * Generate embeddings asynchronously for better performance
     * 
     * @param request the embedding request
     * @return CompletableFuture with embedding response
     */
    @Async
    public CompletableFuture<AIEmbeddingResponse> generateEmbeddingAsync(AIEmbeddingRequest request) {
        try {
            AIEmbeddingResponse response = generateEmbedding(request);
            return CompletableFuture.completedFuture(response);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Generate embeddings for multiple texts asynchronously
     * 
     * @param texts list of texts to embed
     * @param entityType type of entity for context
     * @return CompletableFuture with list of embedding responses
     */
    @Async
    public CompletableFuture<List<AIEmbeddingResponse>> generateEmbeddingsAsync(List<String> texts, String entityType) {
        try {
            List<AIEmbeddingResponse> responses = generateEmbeddings(texts, entityType);
            return CompletableFuture.completedFuture(responses);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Get performance metrics for monitoring
     * 
     * @return map of performance metrics
     */
    public java.util.Map<String, Object> getPerformanceMetrics() {
        long totalEmbeddings = totalEmbeddingsGenerated.get();
        long totalTime = totalProcessingTime.get();
        double avgProcessingTime = totalEmbeddings > 0 ? (double) totalTime / totalEmbeddings : 0.0;
        
        return java.util.Map.of(
            "totalEmbeddingsGenerated", totalEmbeddings,
            "totalProcessingTimeMs", totalTime,
            "averageProcessingTimeMs", avgProcessingTime,
            "cacheHits", cacheHits,
            "cacheMisses", cacheMisses
        );
    }
    
    /**
     * Clear performance metrics
     */
    public void clearMetrics() {
        totalEmbeddingsGenerated.set(0);
        totalProcessingTime.set(0);
        cacheHits.clear();
        cacheMisses.clear();
    }
}
