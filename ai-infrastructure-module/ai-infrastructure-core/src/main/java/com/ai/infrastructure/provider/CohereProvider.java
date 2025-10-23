package com.ai.infrastructure.provider;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cohere Provider Implementation
 * 
 * Real implementation of AI provider using Cohere API.
 * Provides content generation and embedding services.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CohereProvider implements AIProvider {
    
    private final ProviderConfig config;
    private final RestTemplate restTemplate;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastSuccess = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastError = new AtomicReference<>();
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>();
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    
    private static final String COHERE_BASE_URL = "https://api.cohere.ai/v1";
    
    @Override
    public String getProviderName() {
        return "cohere";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            return config.isValid() && config.isEnabled() && 
                   config.getApiKey() != null && !config.getApiKey().trim().isEmpty();
        } catch (Exception e) {
            log.warn("Cohere provider not available: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            log.debug("Generating content with Cohere: model={}, prompt={}", 
                     request.getModel(), request.getPrompt().substring(0, Math.min(100, request.getPrompt().length())));
            
            String url = COHERE_BASE_URL + "/generate";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel() != null ? request.getModel() : config.getDefaultModel());
            requestBody.put("prompt", request.getPrompt());
            requestBody.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : config.getMaxTokens());
            requestBody.put("temperature", request.getTemperature() != null ? request.getTemperature() : config.getTemperature());
            requestBody.put("stop_sequences", List.of("Human:", "Assistant:"));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> generations = (List<Map<String, Object>>) responseBody.get("generations");
            String generatedText = (String) generations.get(0).get("text");
            
            log.debug("Cohere content generation completed in {}ms", responseTime);
            
            return AIGenerationResponse.builder()
                .content(generatedText)
                .model((String) responseBody.get("model"))
                .usage(createUsageFromResponse(responseBody))
                .processingTimeMs(responseTime)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, responseTime);
            
            log.error("Cohere content generation failed", e);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(e.getMessage());
            
            throw new RuntimeException("Cohere content generation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            log.debug("Generating embedding with Cohere: model={}, text={}", 
                     request.getModel(), request.getText().substring(0, Math.min(100, request.getText().length())));
            
            String url = COHERE_BASE_URL + "/embed";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel() != null ? request.getModel() : config.getDefaultEmbeddingModel());
            requestBody.put("texts", List.of(request.getText()));
            requestBody.put("input_type", "search_document");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            @SuppressWarnings("unchecked")
            List<List<Double>> embeddings = (List<List<Double>>) responseBody.get("embeddings");
            List<Double> embedding = embeddings.get(0);
            
            log.debug("Cohere embedding generation completed in {}ms", responseTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model((String) responseBody.get("model"))
                .dimensions(embedding.size())
                .processingTimeMs(responseTime)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, responseTime);
            
            log.error("Cohere embedding generation failed", e);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(e.getMessage());
            
            throw new RuntimeException("Cohere embedding generation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ProviderStatus getStatus() {
        return ProviderStatus.builder()
            .providerName(getProviderName())
            .available(isAvailable())
            .healthy(isHealthy())
            .lastSuccess(lastSuccess.get())
            .lastError(lastError.get())
            .lastErrorMessage(lastErrorMessage.get())
            .totalRequests(totalRequests.get())
            .successfulRequests(successfulRequests.get())
            .failedRequests(failedRequests.get())
            .averageResponseTime(averageResponseTime.get())
            .successRate(calculateSuccessRate())
            .lastUpdated(LocalDateTime.now())
            .build();
    }
    
    @Override
    public ProviderConfig getConfig() {
        return config;
    }
    
    /**
     * Check if provider is healthy
     * 
     * @return true if healthy
     */
    private boolean isHealthy() {
        if (!isAvailable()) {
            return false;
        }
        
        // Check if we have recent successful requests
        LocalDateTime recentSuccess = lastSuccess.get();
        if (recentSuccess == null) {
            return false;
        }
        
        // Consider healthy if last success was within last 5 minutes
        return recentSuccess.isAfter(LocalDateTime.now().minusMinutes(5));
    }
    
    /**
     * Calculate success rate
     * 
     * @return success rate
     */
    private double calculateSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) successfulRequests.get() / total;
    }
    
    /**
     * Update metrics
     * 
     * @param success success flag
     * @param responseTime response time in milliseconds
     */
    private void updateMetrics(boolean success, long responseTime) {
        if (success) {
            successfulRequests.incrementAndGet();
            lastSuccess.set(LocalDateTime.now());
        } else {
            failedRequests.incrementAndGet();
        }
        
        // Update average response time
        long total = totalRequests.get();
        double currentAvg = averageResponseTime.get();
        double newAvg = ((currentAvg * (total - 1)) + responseTime) / total;
        averageResponseTime.set(newAvg);
        
        log.debug("Updated Cohere metrics: success={}, responseTime={}ms, successRate={}", 
                 success, responseTime, calculateSuccessRate());
    }
    
    /**
     * Create usage object from response
     * 
     * @param responseBody response body
     * @return usage object
     */
    private Object createUsageFromResponse(Map<String, Object> responseBody) {
        Map<String, Object> usage = new HashMap<>();
        
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) responseBody.get("meta");
        if (meta != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokens = (Map<String, Object>) meta.get("tokens");
            if (tokens != null) {
                usage.put("prompt_tokens", tokens.get("input_tokens"));
                usage.put("completion_tokens", tokens.get("output_tokens"));
                usage.put("total_tokens", ((Number) tokens.get("input_tokens")).intValue() + 
                                         ((Number) tokens.get("output_tokens")).intValue());
            }
        }
        
        return usage;
    }
}