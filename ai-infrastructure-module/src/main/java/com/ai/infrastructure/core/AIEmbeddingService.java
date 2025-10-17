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
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

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
@Service
@RequiredArgsConstructor
public class AIEmbeddingService {
    
    private final AIProviderConfig config;
    private OpenAiService openAiService;
    
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
     * Generate embedding for text content
     * 
     * @param request the embedding request
     * @return embedding response with vector data
     */
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
     * Generate embeddings for multiple texts in batch
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
}
