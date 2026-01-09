package com.ai.infrastructure.provider.openai;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * OpenAI Provider Implementation
 * 
 * Real implementation of AI provider using OpenAI API.
 * Provides content generation and embedding services.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class OpenAIProvider implements AIProvider {
    
    private final ProviderConfig config;
    private final RestTemplate restTemplate;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastSuccess = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastError = new AtomicReference<>();
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>();
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    
    private static final String PATH_CHAT_COMPLETIONS = "/chat/completions";
    private static final String PATH_EMBEDDINGS = "/embeddings";
    
    @Override
    public String getProviderName() {
        return "openai";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            return config.isValid() && config.isEnabled() && 
                   config.getApiKey() != null && !config.getApiKey().trim().isEmpty();
        } catch (Exception e) {
            log.warn("OpenAI provider not available: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            log.debug("Generating content with OpenAI: model={}, prompt={}", 
                     request.getModel(), request.getPrompt().substring(0, Math.min(100, request.getPrompt().length())));
            
            String url = normalizeBaseUrl(config.getBaseUrl()) + PATH_CHAT_COMPLETIONS;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            // Build messages with system and user roles for better prompt control
            List<Map<String, String>> messages = new ArrayList<>();
            
            // Add system prompt if provided
            if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
                messages.add(Map.of(
                    "role", "system",
                    "content", request.getSystemPrompt()
                ));
            }
            
            // Add user prompt
            messages.add(Map.of(
                "role", "user",
                "content", request.getPrompt()
            ));
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel() != null ? request.getModel() : config.getDefaultModel());
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : config.getMaxTokens());
            requestBody.put("temperature", request.getTemperature() != null ? request.getTemperature() : config.getTemperature());
            requestBody.put("top_p", 0.1);  // Lower top_p for more deterministic responses
            
            // IMPORTANT: Never use System.out for provider logging; it bypasses log levels and makes CI noisy.
            // Keep request/response metadata at DEBUG and only include content at TRACE.
            if (log.isDebugEnabled()) {
                log.debug(
                    "OpenAI API request: url={}, model={}, temperature={}, topP={}, maxTokens={}, messages={}",
                    url,
                    requestBody.get("model"),
                    requestBody.get("temperature"),
                    requestBody.get("top_p"),
                    requestBody.get("max_tokens"),
                    messages.size()
                );
                if (log.isTraceEnabled()) {
                    for (int i = 0; i < messages.size(); i++) {
                        Map<String, String> msg = messages.get(i);
                        String role = msg.get("role");
                        String content = msg.get("content");
                        int len = content != null ? content.length() : 0;
                        String snippet = content == null ? "" : content.substring(0, Math.min(500, len));
                        log.trace(
                            "OpenAI API request message[{}]: role={}, contentLength={}, contentSnippet={}",
                            i,
                            role,
                            len,
                            snippet
                        );
                    }
                }
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            @SuppressWarnings("unchecked")
            Map<String, String> message = (Map<String, String>) choices.get(0).get("message");
            String content = message.get("content");
            
            if (log.isDebugEnabled()) {
                Object finishReason = choices.get(0).get("finish_reason");
                int contentLength = content != null ? content.length() : 0;
                log.debug(
                    "OpenAI API response: responseTimeMs={}, model={}, finishReason={}, contentLength={}",
                    responseTime,
                    responseBody.get("model"),
                    finishReason,
                    contentLength
                );
                if (log.isTraceEnabled() && content != null) {
                    log.trace(
                        "OpenAI API response contentSnippet={}",
                        content.substring(0, Math.min(500, content.length()))
                    );
                }
            }
            
            log.debug("OpenAI content generation completed in {}ms", responseTime);
            
            return AIGenerationResponse.builder()
                .content(content)
                .model((String) responseBody.get("model"))
                .usage(createUsageFromResponse(responseBody))
                .processingTimeMs(responseTime)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, responseTime);
            
            log.error("OpenAI content generation failed", e);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(e.getMessage());
            
            throw new RuntimeException("OpenAI content generation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            log.debug("Generating embedding with OpenAI: model={}, text={}", 
                     request.getModel(), request.getText().substring(0, Math.min(100, request.getText().length())));
            
            String url = normalizeBaseUrl(config.getBaseUrl()) + PATH_EMBEDDINGS;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel() != null ? request.getModel() : config.getDefaultEmbeddingModel());
            requestBody.put("input", request.getText());
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, Map.class);
            
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            @SuppressWarnings("unchecked")
            List<Double> embeddingValues = (List<Double>) data.get(0).get("embedding");
            
            log.debug("OpenAI embedding generation completed in {}ms", responseTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embeddingValues)
                .model((String) responseBody.get("model"))
                .dimensions(embeddingValues.size())
                .processingTimeMs(responseTime)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, responseTime);
            
            log.error("OpenAI embedding generation failed", e);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(e.getMessage());
            
            throw new RuntimeException("OpenAI embedding generation failed: " + e.getMessage(), e);
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
        
        log.debug("Updated OpenAI metrics: success={}, responseTime={}ms, successRate={}", 
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
        Map<String, Object> usageData = (Map<String, Object>) responseBody.get("usage");
        if (usageData != null) {
            usage.put("prompt_tokens", usageData.get("prompt_tokens"));
            usage.put("completion_tokens", usageData.get("completion_tokens"));
            usage.put("total_tokens", usageData.get("total_tokens"));
        }
        
        return usage;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}