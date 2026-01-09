package com.ai.infrastructure.provider.openai;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.exception.AIServiceException;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.embedding.EmbeddingResult;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
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
public class OpenAIEmbeddingProvider implements EmbeddingProvider {
    
    private final AIProviderConfig config;
    private OpenAiService openAiService;
    private RestTemplate restTemplate;
    private boolean available = false;
    private int embeddingDimension = 1536; // Default for text-embedding-3-small
    private boolean useDirectHttp = false; // Use direct HTTP when dimension reduction is needed
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing OpenAI Embedding Provider");
            
            AIProviderConfig.OpenAIConfig openai = config.getOpenai();
            
            // Debug: Log all OpenAI config values
            log.info("OpenAI Config - embeddingModel: {}, embeddingDimensions: {}, apiKey: {}", 
                openai.getEmbeddingModel(), 
                openai.getEmbeddingDimensions(),
                openai.getApiKey() != null ? "***" : "null");

            if (openai.getApiKey() == null || openai.getApiKey().trim().isEmpty()) {
                log.warn("OpenAI API key not configured. Provider will not be available.");
                available = false;
                return;
            }
            
            openAiService = new OpenAiService(
                openai.getApiKey(),
                Duration.ofSeconds(openai.getTimeout())
            );
            
            // Initialize RestTemplate for direct HTTP calls (needed for dimension reduction)
            restTemplate = new RestTemplate();
            
            // Check if dimension reduction is needed
            Integer requestedDimensions = openai.getEmbeddingDimensions();
            log.info("Checking dimension reduction: requestedDimensions={}, embeddingModel={}", 
                requestedDimensions, openai.getEmbeddingModel());
            
            if (requestedDimensions != null) {
                // Dimension reduction requested - use direct HTTP calls since library doesn't support it
                useDirectHttp = true;
                embeddingDimension = requestedDimensions;
                log.info("Dimension reduction configured: {} dimensions. Using direct HTTP calls to OpenAI API.", requestedDimensions);
            }
            
            // Test connection with a small embedding call
            try {
                AIEmbeddingRequest testRequest = AIEmbeddingRequest.builder()
                    .text("test")
                    .build();
                
                // If using direct HTTP, call it directly to avoid availability check
                AIEmbeddingResponse testResponse;
                if (useDirectHttp && requestedDimensions != null) {
                    long startTime = System.currentTimeMillis();
                    testResponse = generateEmbeddingViaHttp(testRequest, requestedDimensions, startTime);
                } else {
                    // Temporarily set available to true for initialization test
                    available = true;
                    testResponse = generateEmbedding(testRequest);
                }
                if (testResponse != null && testResponse.getEmbedding() != null && !testResponse.getEmbedding().isEmpty()) {
                    available = true;
                    if (requestedDimensions == null) {
                        embeddingDimension = testResponse.getEmbedding().size();
                    }
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
            AIProviderConfig.OpenAIConfig openai = config.getOpenai();
            Integer requestedDimensions = openai.getEmbeddingDimensions();
            
            // Use direct HTTP call if dimension reduction is needed (library doesn't support it)
            // Skip availability check for HTTP calls as they don't need the library service
            if (useDirectHttp && requestedDimensions != null) {
                long startTime = System.currentTimeMillis();
                if (log.isInfoEnabled()) {
                    log.info("=== OPENAI EMBEDDING API REQUEST ===");
                    log.info(
                        "OpenAI embedding request (http): model={}, dimensions={}, textLength={}",
                        request.getModel() != null ? request.getModel() : openai.getEmbeddingModel(),
                        requestedDimensions,
                        request.getText() != null ? request.getText().length() : 0
                    );
                    if (log.isTraceEnabled()) {
                        String text = request.getText();
                        int len = text != null ? text.length() : 0;
                        String snippet = text == null ? "" : text.substring(0, Math.min(300, len));
                        log.trace("OpenAI embedding request textSnippet={}", snippet);
                    }
                    log.info("=== END OPENAI EMBEDDING API REQUEST ===");
                }
                log.debug("Generating embedding using OpenAI via HTTP for text: {}", request.getText());
                return generateEmbeddingViaHttp(request, requestedDimensions, startTime);
            }
            
            // For library-based calls, check availability
            if (!isAvailable()) {
                throw new AIServiceException("OpenAI Embedding Provider is not available");
            }
            
            log.debug("Generating embedding using OpenAI for text: {}", request.getText());
            
            long startTime = System.currentTimeMillis();
            
            // Use library for standard requests
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                  .model(request.getModel() != null ? request.getModel() : openai.getEmbeddingModel())
                .input(List.of(request.getText()))
                .build();
            
            EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
            var embedding = result.getData().get(0).getEmbedding();
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (log.isInfoEnabled()) {
                log.info("=== OPENAI EMBEDDING API RESPONSE ===");
                log.info(
                    "OpenAI embedding response (library): responseTimeMs={}, model={}, dimensions={}",
                    processingTime,
                    request.getModel() != null ? request.getModel() : openai.getEmbeddingModel(),
                    embedding != null ? embedding.size() : 0
                );
                log.info("=== END OPENAI EMBEDDING API RESPONSE ===");
            }

            log.debug("Successfully generated OpenAI embedding with {} dimensions in {}ms", 
                     embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(request.getModel() != null ? request.getModel() : openai.getEmbeddingModel())
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating OpenAI embedding", e);
            throw new AIServiceException("Failed to generate OpenAI embedding", e);
        }
    }
    
    /**
     * Generate embedding via direct HTTP call to OpenAI API.
     * This is used when dimension reduction is needed, as the library doesn't support it.
     */
    private AIEmbeddingResponse generateEmbeddingViaHttp(AIEmbeddingRequest request, Integer dimensions, long startTime) {
        try {
            AIProviderConfig.OpenAIConfig openai = config.getOpenai();
            String baseUrl = openai.getBaseUrl() != null ? openai.getBaseUrl() : "https://api.openai.com/v1";
            String url = baseUrl + "/embeddings";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openai.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", request.getModel() != null ? request.getModel() : openai.getEmbeddingModel());
            requestBody.put("input", List.of(request.getText()));
            requestBody.put("dimensions", dimensions);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new AIServiceException("OpenAI API returned empty response");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new AIServiceException("OpenAI API response missing data");
            }
            
            @SuppressWarnings("unchecked")
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");
            if (embedding == null) {
                throw new AIServiceException("OpenAI API response missing embedding vector");
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            if (log.isInfoEnabled()) {
                log.info("=== OPENAI EMBEDDING API RESPONSE ===");
                log.info(
                    "OpenAI embedding response (http): responseTimeMs={}, model={}, dimensions={}",
                    processingTime,
                    responseBody.get("model"),
                    embedding != null ? embedding.size() : 0
                );
                log.info("=== END OPENAI EMBEDDING API RESPONSE ===");
            }

            log.debug("Successfully generated OpenAI embedding with {} dimensions via HTTP in {}ms", 
                     embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(request.getModel() != null ? request.getModel() : openai.getEmbeddingModel())
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            log.error("Error generating OpenAI embedding via HTTP", e);
            throw new AIServiceException("Failed to generate OpenAI embedding via HTTP", e);
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
            
            AIProviderConfig.OpenAIConfig openai = config.getOpenai();
            Integer requestedDimensions = openai.getEmbeddingDimensions();
            
            // Use direct HTTP call if dimension reduction is needed
            if (useDirectHttp && requestedDimensions != null) {
                return generateEmbeddingsViaHttp(texts, requestedDimensions, startTime);
            }
            
            // Use library for standard requests
            EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
                  .model(openai.getEmbeddingModel())
                .input(texts)
                .build();
            
            EmbeddingResult result = openAiService.createEmbeddings(embeddingRequest);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            List<AIEmbeddingResponse> responses = result.getData().stream()
                .map(data -> AIEmbeddingResponse.builder()
                    .embedding(data.getEmbedding())
                      .model(openai.getEmbeddingModel())
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
    
    /**
     * Generate multiple embeddings via direct HTTP call to OpenAI API.
     */
    private List<AIEmbeddingResponse> generateEmbeddingsViaHttp(List<String> texts, Integer dimensions, long startTime) {
        try {
            AIProviderConfig.OpenAIConfig openai = config.getOpenai();
            String baseUrl = openai.getBaseUrl() != null ? openai.getBaseUrl() : "https://api.openai.com/v1";
            String url = baseUrl + "/embeddings";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openai.getApiKey());
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openai.getEmbeddingModel());
            requestBody.put("input", texts);
            requestBody.put("dimensions", dimensions);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new AIServiceException("OpenAI API returned empty response");
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new AIServiceException("OpenAI API response missing data");
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            List<AIEmbeddingResponse> responses = data.stream()
                .map(item -> {
                    @SuppressWarnings("unchecked")
                    List<Double> embedding = (List<Double>) item.get("embedding");
                    return AIEmbeddingResponse.builder()
                        .embedding(embedding)
                        .model(openai.getEmbeddingModel())
                        .dimensions(embedding != null ? embedding.size() : 0)
                        .processingTimeMs(processingTime / texts.size())
                        .requestId(UUID.randomUUID().toString())
                        .build();
                })
                .collect(Collectors.toList());
            
            if (log.isInfoEnabled()) {
                log.info("=== OPENAI EMBEDDING API RESPONSE ===");
                log.info(
                    "OpenAI embedding batch response (http): responseTimeMs={}, model={}, embeddings={}, dimensions={}",
                    processingTime,
                    responseBody.get("model"),
                    responses.size(),
                    dimensions != null ? dimensions : -1
                );
                log.info("=== END OPENAI EMBEDDING API RESPONSE ===");
            }

            log.debug("Successfully generated {} OpenAI embeddings via HTTP in {}ms", 
                     responses.size(), processingTime);
            
            return responses;
                
        } catch (Exception e) {
            log.error("Error generating batch OpenAI embeddings via HTTP", e);
            throw new AIServiceException("Failed to generate batch OpenAI embeddings via HTTP", e);
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
        AIProviderConfig.OpenAIConfig openai = config.getOpenai();
        status.put("model", openai.getEmbeddingModel());
        status.put("embeddingDimension", embeddingDimension);
        status.put("timeout", openai.getTimeout());
        
        if (isAvailable()) {
            status.put("status", "ready");
        } else {
            status.put("status", "unavailable");
            status.put("message", "OpenAI API key not configured or service unavailable");
        }
        
        return status;
    }
}

