package com.ai.infrastructure.embedding;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.exception.AIServiceException;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// @Component removed - using @Bean in auto-configuration instead

import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OpenAI Embedding Provider
 * 
 * Wraps existing OpenAI embedding logic as an EmbeddingProvider.
 * Fallback option when ONNX/REST providers are unavailable.
 * 
 * Uses OpenAI's embedding API (text-embedding-3-small, etc.)
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "openai")
public class OpenAIEmbeddingProvider implements EmbeddingProvider {
    
    private final AIProviderConfig config;
    private OpenAiService openAiService;
    private boolean available = false;
    private int embeddingDimension = 1536; // Default for text-embedding-3-small
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing OpenAI Embedding Provider");
            
            if (config.getOpenaiApiKey() == null || config.getOpenaiApiKey().trim().isEmpty()) {
                log.warn("OpenAI API key not configured. Provider will not be available.");
                available = false;
                return;
            }
            
            openAiService = new OpenAiService(
                config.getOpenaiApiKey(),
                Duration.ofSeconds(config.getOpenaiTimeout())
            );
            
            // Test connection with a small embedding call
            try {
                EmbeddingRequest testRequest = EmbeddingRequest.builder()
                    .model(config.getOpenaiEmbeddingModel())
                    .input(List.of("test"))
                    .build();
                
                EmbeddingResult testResult = openAiService.createEmbeddings(testRequest);
                if (!testResult.getData().isEmpty()) {
                    available = true;
                    embeddingDimension = testResult.getData().get(0).getEmbedding().size();
                    log.info("OpenAI Embedding Provider initialized successfully with dimension: {}", embeddingDimension);
                } else {
                    available = false;
                }
            } catch (Exception e) {
                log.warn("OpenAI Embedding Provider test call failed: {}", e.getMessage());
                available = false;
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize OpenAI Embedding Provider", e);
            available = false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "openai";
    }
    
    @Override
    public boolean isAvailable() {
        return available && openAiService != null;
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        try {
            if (!isAvailable()) {
                throw new AIServiceException("OpenAI Embedding Provider is not available");
            }
            
            log.debug("Generating embedding using OpenAI for text: {}", request.getText());
            
            long startTime = System.currentTimeMillis();
            
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                .model(request.getModel() != null ? request.getModel() : config.getOpenaiEmbeddingModel())
                .input(List.of(request.getText()))
                .build();
            
            EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
            var embedding = result.getData().get(0).getEmbedding();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.debug("Successfully generated OpenAI embedding with {} dimensions in {}ms", 
                     embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(request.getModel() != null ? request.getModel() : config.getOpenaiEmbeddingModel())
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating OpenAI embedding", e);
            throw new AIServiceException("Failed to generate OpenAI embedding", e);
        }
    }
    
    @Override
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
        try {
            if (!isAvailable()) {
                throw new AIServiceException("OpenAI Embedding Provider is not available");
            }
            
            log.debug("Generating {} embeddings using OpenAI", texts.size());
            
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
                .collect(Collectors.toList());
            
            log.debug("Successfully generated {} OpenAI embeddings in {}ms", 
                     responses.size(), processingTime);
            
            return responses;
                
        } catch (Exception e) {
            log.error("Error generating batch OpenAI embeddings", e);
            throw new AIServiceException("Failed to generate batch OpenAI embeddings", e);
        }
    }
    
    @Override
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }
    
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "openai");
        status.put("available", isAvailable());
        status.put("model", config.getOpenaiEmbeddingModel());
        status.put("embeddingDimension", embeddingDimension);
        status.put("timeout", config.getOpenaiTimeout());
        
        if (isAvailable()) {
            status.put("status", "ready");
        } else {
            status.put("status", "unavailable");
            status.put("message", "OpenAI API key not configured or service unavailable");
        }
        
        return status;
    }
}

