package com.ai.infrastructure.core;

import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for AI embedding generation
 * 
 * This service handles the generation of vector embeddings for text content
 * using swappable embedding providers (ONNX, REST, OpenAI).
 * It provides caching and batch processing capabilities for efficient embedding generation.
 * 
 * Uses EmbeddingProvider abstraction for easy swapping between providers:
 * - ONNX (default): Local, no API calls
 * - REST: Docker/sentence-transformers container
 * - OpenAI: Cloud API (fallback)
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
// @Service // Removed - already defined as @Bean in AIInfrastructureAutoConfiguration
@RequiredArgsConstructor
public class AIEmbeddingService {
    
    private final AIProviderConfig config;
    private final EmbeddingProvider embeddingProvider;
    
    // Performance metrics
    private final AtomicLong totalEmbeddingsGenerated = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final ConcurrentHashMap<String, Long> cacheHits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheMisses = new ConcurrentHashMap<>();
    
    /**
     * Generate embedding for text content with caching
     * 
     * Delegates to configured EmbeddingProvider (ONNX, REST, or OpenAI)
     * 
     * @param request the embedding request
     * @return embedding response with vector data
     */
    @Cacheable(value = "embeddings", key = "#request.text + '_' + #request.model + '_' + #embeddingProvider.getProviderName()")
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        try {
            if (embeddingProvider == null || !embeddingProvider.isAvailable()) {
                throw new AIServiceException("Embedding provider is not available. Provider: " + 
                    (embeddingProvider != null ? embeddingProvider.getProviderName() : "null"));
            }
            
            // Ensure we're using ONNX (if configured to do so)
            String providerName = embeddingProvider.getProviderName();
            if (!providerName.equals("onnx")) {
                log.warn("WARNING: Not using ONNX provider. Current provider: {}", providerName);
            } else {
                log.debug("Using ONNX provider for embedding generation (no fallback)");
            }
            
            log.debug("Generating embedding using {} provider for text: {}", 
                     providerName, request.getText());
            
            long startTime = System.currentTimeMillis();
            
            // Delegate to embedding provider
            AIEmbeddingResponse response = embeddingProvider.generateEmbedding(request);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Update metrics
            totalEmbeddingsGenerated.incrementAndGet();
            totalProcessingTime.addAndGet(processingTime);
            
            log.debug("Successfully generated embedding with {} dimensions in {}ms using {} provider", 
                response.getDimensions(), processingTime, embeddingProvider.getProviderName());
            
            return response;
                
        } catch (Exception e) {
            log.error("Error generating embedding", e);
            throw new AIServiceException("Failed to generate embedding", e);
        }
    }
    
    /**
     * Generate embeddings for multiple texts in batch with optimization
     * 
     * Delegates to configured EmbeddingProvider for batch processing
     * 
     * @param texts list of texts to embed
     * @param entityType type of entity for context
     * @return list of embedding responses
     */
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts, String entityType) {
        try {
            if (embeddingProvider == null || !embeddingProvider.isAvailable()) {
                throw new AIServiceException("Embedding provider is not available");
            }
            
            log.debug("Generating {} embeddings using {} provider", 
                     texts.size(), embeddingProvider.getProviderName());
            
            long startTime = System.currentTimeMillis();
            
            // Delegate to embedding provider for batch processing
            List<AIEmbeddingResponse> responses = embeddingProvider.generateEmbeddings(texts);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            // Update metrics
            totalEmbeddingsGenerated.addAndGet(texts.size());
            totalProcessingTime.addAndGet(processingTime);
            
            log.debug("Successfully generated {} embeddings in {}ms using {} provider", 
                responses.size(), processingTime, embeddingProvider.getProviderName());
            
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
