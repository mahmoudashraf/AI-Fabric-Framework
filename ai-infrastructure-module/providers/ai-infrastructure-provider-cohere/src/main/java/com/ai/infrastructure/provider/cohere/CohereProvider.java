package com.ai.infrastructure.provider.cohere;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.ProviderConfig;
import com.ai.infrastructure.provider.ProviderStatus;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
            
            String url = COHERE_BASE_URL + "/chat";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel() != null ? request.getModel() : 
                          (config.getDefaultModel() != null ? config.getDefaultModel() : "command-r7b-12-2024"));
            
            // Cohere Chat API uses messages array
            List<Map<String, Object>> messages = new java.util.ArrayList<>();
            
            // Add system prompt if present
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().trim().isEmpty()) {
                // For intent extraction, enhance the system prompt to be very explicit about JSON-only responses
                String systemPrompt = request.getSystemPrompt();
                if (request.getGenerationType() != null && request.getGenerationType().equals("intent_extraction")) {
                    String jsonInstruction = "CRITICAL JSON REQUIREMENT: You are a JSON-only API endpoint. " +
                        "You MUST respond with ONLY valid JSON. No markdown code blocks (no ```json or ```), " +
                        "no explanations, no text before or after the JSON, no comments, no additional formatting. " +
                        "Just the raw JSON object. If you include any text other than JSON, the response will fail to parse.\n\n";
                    
                    systemPrompt = jsonInstruction + systemPrompt;
                    
                    log.info("Enhanced system prompt for intent extraction with JSON-only requirement (length: {})", systemPrompt.length());
                    log.debug("Enhanced system prompt preview: {}", systemPrompt.substring(0, Math.min(200, systemPrompt.length())));
                }
                requestBody.put("preamble", systemPrompt);
            }
            
            // Add user message
            messages.add(Map.of("role", "user", "content", request.getPrompt()));
            requestBody.put("message", request.getPrompt());
            
            requestBody.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : config.getMaxTokens());
            requestBody.put("temperature", request.getTemperature() != null ? request.getTemperature() : config.getTemperature());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            String generatedText = (String) responseBody.get("text");
            
            log.debug("Cohere content generation completed in {}ms", responseTime);
            
            // Extract model from response or use request model
            String model = (String) responseBody.get("model");
            if (model == null) {
                model = request.getModel() != null ? request.getModel() : config.getDefaultModel();
            }
            
            return AIGenerationResponse.builder()
                .content(generatedText)
                .model(model)
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
            // Cohere Chat API uses different structure
            @SuppressWarnings("unchecked")
            Map<String, Object> tokens = (Map<String, Object>) meta.get("tokens");
            if (tokens != null) {
                Object inputTokens = tokens.get("input_tokens");
                Object outputTokens = tokens.get("output_tokens");
                if (inputTokens != null && outputTokens != null) {
                    usage.put("prompt_tokens", inputTokens);
                    usage.put("completion_tokens", outputTokens);
                    int total = ((Number) inputTokens).intValue() + ((Number) outputTokens).intValue();
                    usage.put("total_tokens", total);
                }
            }
            // Also check for billed_units (newer API format)
            @SuppressWarnings("unchecked")
            Map<String, Object> billedUnits = (Map<String, Object>) meta.get("billed_units");
            if (billedUnits != null && tokens == null) {
                Object inputTokens = billedUnits.get("input_tokens");
                Object outputTokens = billedUnits.get("output_tokens");
                if (inputTokens != null) {
                    usage.put("prompt_tokens", inputTokens);
                }
                if (outputTokens != null) {
                    usage.put("completion_tokens", outputTokens);
                }
                if (inputTokens != null && outputTokens != null) {
                    int total = ((Number) inputTokens).intValue() + ((Number) outputTokens).intValue();
                    usage.put("total_tokens", total);
                }
            }
        }
        
        return usage;
    }
}