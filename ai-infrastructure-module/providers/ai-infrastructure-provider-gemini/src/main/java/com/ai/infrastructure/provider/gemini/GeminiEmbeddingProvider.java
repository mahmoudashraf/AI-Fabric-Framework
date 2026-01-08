package com.ai.infrastructure.provider.gemini;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Google Gemini Embedding Provider
 * 
 * Implementation of EmbeddingProvider using Google Gemini's embedding API.
 * Supports models like embedding-001 and text-embedding-004.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class GeminiEmbeddingProvider implements EmbeddingProvider {
    
    private final AIProviderConfig aiProviderConfig;
    private RestTemplate restTemplate;
    private boolean available = false;
    private int embeddingDimension = 768; // Default for text-embedding-004 (actual dimension will be determined at runtime)
    
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Gemini Embedding Provider");
            
            AIProviderConfig.GeminiConfig gemini = aiProviderConfig.getGemini();
            
            if (gemini.getApiKey() == null || gemini.getApiKey().trim().isEmpty()) {
                log.warn("Gemini API key not configured. Provider will not be available.");
                available = false;
                return;
            }
            
            restTemplate = new RestTemplate();
            
            // Test connection with a small embedding call
            try {
                AIEmbeddingRequest testRequest = AIEmbeddingRequest.builder()
                    .text("test")
                    .model(gemini.getEmbeddingModel() != null ? gemini.getEmbeddingModel() : "text-embedding-004")
                    .build();
                
                AIEmbeddingResponse testResponse = generateEmbedding(testRequest);
                if (testResponse != null && testResponse.getEmbedding() != null && !testResponse.getEmbedding().isEmpty()) {
                    available = true;
                    embeddingDimension = testResponse.getEmbedding().size();
                    log.info("Gemini Embedding Provider initialized successfully with dimension: {}", embeddingDimension);
                } else {
                    available = false;
                }
            } catch (Exception e) {
                log.warn("Gemini Embedding Provider test call failed: {}", e.getMessage());
                available = false;
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize Gemini Embedding Provider", e);
            available = false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "gemini";
    }
    
    @Override
    public boolean isAvailable() {
        return available && restTemplate != null;
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        if (!isAvailable()) {
            throw new AIServiceException("Gemini Embedding Provider is not available");
        }
        
        try {
            AIProviderConfig.GeminiConfig gemini = aiProviderConfig.getGemini();
            String model = request.getModel() != null ? request.getModel() : 
                          (gemini.getEmbeddingModel() != null ? gemini.getEmbeddingModel() : "text-embedding-004");
            
            log.debug("Generating embedding using Gemini for text: {}", request.getText());
            
            long startTime = System.currentTimeMillis();
            
            String url = GEMINI_BASE_URL + "/models/" + model + ":embedContent?key=" + gemini.getApiKey();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            
            // Gemini embedding API structure
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", request.getText());
            content.put("parts", List.of(part));
            requestBody.put("content", content);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> embeddingData = (Map<String, Object>) responseBody.get("embedding");
            @SuppressWarnings("unchecked")
            List<Double> values = (List<Double>) embeddingData.get("values");
            
            // Keep as List<Double> for embedding
            List<Double> embedding = new ArrayList<>(values);
            
            log.debug("Successfully generated Gemini embedding with {} dimensions in {}ms", 
                     embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(model)
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating Gemini embedding", e);
            throw new AIServiceException("Failed to generate Gemini embedding", e);
        }
    }
    
    @Override
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
        return texts.stream()
            .map(text -> AIEmbeddingRequest.builder().text(text).build())
            .map(this::generateEmbedding)
            .collect(Collectors.toList());
    }
    
    @Override
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }
    
    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("provider", getProviderName());
        status.put("available", isAvailable());
        status.put("dimension", embeddingDimension);
        return status;
    }
}
