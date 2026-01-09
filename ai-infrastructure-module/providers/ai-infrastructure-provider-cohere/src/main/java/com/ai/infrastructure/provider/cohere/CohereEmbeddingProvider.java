package com.ai.infrastructure.provider.cohere;

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
 * Cohere Embedding Provider
 * 
 * Implementation of EmbeddingProvider using Cohere's embedding API.
 * Supports models like embed-english-v3.0, embed-multilingual-v3.0, etc.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class CohereEmbeddingProvider implements EmbeddingProvider {
    
    private final AIProviderConfig aiProviderConfig;
    private RestTemplate restTemplate;
    private boolean available = false;
    private int embeddingDimension = 1024; // Default for embed-english-v3.0
    
    private static final String COHERE_BASE_URL = "https://api.cohere.ai/v1";
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing Cohere Embedding Provider");
            
            AIProviderConfig.CohereConfig cohere = aiProviderConfig.getCohere();
            
            if (cohere.getApiKey() == null || cohere.getApiKey().trim().isEmpty()) {
                log.warn("Cohere API key not configured. Provider will not be available.");
                available = false;
                return;
            }
            
            restTemplate = new RestTemplate();
            
            // Test connection with a small embedding call
            try {
                // Temporarily set available to true for initialization test
                available = true;
                
                AIEmbeddingRequest testRequest = AIEmbeddingRequest.builder()
                    .text("test")
                    .model(cohere.getEmbeddingModel() != null ? cohere.getEmbeddingModel() : "embed-english-v3.0")
                    .build();
                
                AIEmbeddingResponse testResponse = generateEmbedding(testRequest);
                if (testResponse != null && testResponse.getEmbedding() != null && !testResponse.getEmbedding().isEmpty()) {
                    available = true;
                    embeddingDimension = testResponse.getEmbedding().size();
                    log.info("Cohere Embedding Provider initialized successfully with dimension: {}", embeddingDimension);
                } else {
                    available = false;
                }
            } catch (Exception e) {
                log.warn("Cohere Embedding Provider test call failed: {}", e.getMessage());
                available = false;
            }
            
        } catch (Exception e) {
            log.error("Failed to initialize Cohere Embedding Provider", e);
            available = false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "cohere";
    }
    
    @Override
    public boolean isAvailable() {
        return available && restTemplate != null;
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        if (!isAvailable()) {
            throw new AIServiceException("Cohere Embedding Provider is not available");
        }
        
        try {
            AIProviderConfig.CohereConfig cohere = aiProviderConfig.getCohere();
            String model = request.getModel() != null ? request.getModel() : 
                          (cohere.getEmbeddingModel() != null ? cohere.getEmbeddingModel() : "embed-english-v3.0");
            
            log.debug("Generating embedding using Cohere for text: {}", request.getText());
            
            long startTime = System.currentTimeMillis();
            
            String url = COHERE_BASE_URL + "/embed";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + cohere.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("texts", List.of(request.getText()));
            requestBody.put("input_type", "search_document");

            if (log.isInfoEnabled()) {
                log.info("=== COHERE EMBEDDING API REQUEST ===");
                log.info(
                    "Cohere embedding request: url={}, model={}, inputType={}, textLength={}",
                    url,
                    model,
                    requestBody.get("input_type"),
                    request.getText() != null ? request.getText().length() : 0
                );
                String text = request.getText();
                int len = text != null ? text.length() : 0;
                String snippet = text == null ? "" : text.substring(0, Math.min(300, len));
                log.info("Cohere embedding request textSnippet={}", snippet);
                log.info("=== END COHERE EMBEDDING API REQUEST ===");
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            @SuppressWarnings("unchecked")
            List<List<Double>> embeddings = (List<List<Double>>) responseBody.get("embeddings");
            List<Double> embedding = embeddings.get(0);

            if (log.isInfoEnabled()) {
                log.info("=== COHERE EMBEDDING API RESPONSE ===");
                log.info(
                    "Cohere embedding response: responseTimeMs={}, model={}, dimensions={}",
                    processingTime,
                    responseBody != null ? responseBody.get("model") : null,
                    embedding != null ? embedding.size() : 0
                );
                log.info("=== END COHERE EMBEDDING API RESPONSE ===");
            }
            
            log.debug("Successfully generated Cohere embedding with {} dimensions in {}ms", 
                     embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(model)
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating Cohere embedding", e);
            throw new AIServiceException("Failed to generate Cohere embedding", e);
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
