package com.ai.infrastructure.provider.rest;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.exception.AIServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API Embedding Provider
 * 
 * Provides embedding generation via REST API endpoint.
 * Supports Docker containers running sentence-transformers-serve or similar services.
 * 
 * Configuration:
 * - baseUrl: Base URL of the REST API service (e.g., http://localhost:8000)
 * - endpoint: Embedding endpoint (e.g., /embed)
 * - batchEndpoint: Batch embedding endpoint (e.g., /embed/batch)
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class RestEmbeddingProvider implements EmbeddingProvider {
    
    private final AIProviderConfig config;
    private RestTemplate restTemplate;
    private boolean available = false;
    private int embeddingDimension = 384; // Default for all-MiniLM-L6-v2
    
    @PostConstruct
    public void initialize() {
        try {
            log.info("Initializing REST Embedding Provider");
            
            // Create REST template with configured timeouts
            var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
            Integer timeout = timeout();
            if (timeout != null) {
                requestFactory.setConnectTimeout(timeout);
                requestFactory.setReadTimeout(timeout);
            }
            restTemplate = new RestTemplate(requestFactory);
            
            // Test connection
            testConnection();
            
            log.info("REST Embedding Provider initialized successfully with base URL: {}", baseUrl());
            
        } catch (Exception e) {
            log.warn("REST Embedding Provider initialization failed: {}", e.getMessage());
            log.info("REST Embedding Provider will be unavailable until service is available");
            available = false;
        }
    }
    
    /**
     * Test connection to REST API
     */
    private void testConnection() {
        try {
            String healthUrl = baseUrl() + "/health";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                healthUrl,
                HttpMethod.GET,
                entity,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                available = true;
                // Try to get embedding dimension from health check or first test call
                try {
                    AIEmbeddingRequest testRequest = AIEmbeddingRequest.builder()
                        .text("test")
                        .build();
                    AIEmbeddingResponse testResponse = generateEmbedding(testRequest);
                    embeddingDimension = testResponse.getDimensions();
                } catch (Exception e) {
                    log.debug("Could not determine embedding dimension from test call: {}", e.getMessage());
                }
                log.info("REST Embedding Provider connection successful");
            } else {
                available = false;
            }
        } catch (RestClientException e) {
            log.debug("REST Embedding Provider health check failed (this is OK if service is not running): {}", e.getMessage());
            available = false;
        }
    }
    
    @Override
    public String getProviderName() {
        return "rest";
    }
    
    @Override
    public boolean isAvailable() {
        // Retry connection test if previously unavailable
        if (!available) {
            testConnection();
        }
        return available;
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        try {
            if (!isAvailable()) {
                throw new AIServiceException("REST Embedding Provider is not available. Service may not be running at: " + baseUrl());
            }
            
            log.debug("Generating embedding using REST API for text: {}", request.getText());
            
            long startTime = System.currentTimeMillis();
            
            // Prepare request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", request.getText());
            requestBody.put("model", model());
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // Make REST API call
            String url = baseUrl() + endpoint();
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AIServiceException("REST API returned non-success status: " + response.getStatusCode());
            }
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new AIServiceException("REST API returned empty response");
            }
            
            // Extract embedding from response
            Object embeddingObj = responseBody.get("embedding");
            List<Double> embedding;
            
            if (embeddingObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> embeddingList = (List<Object>) embeddingObj;
                embedding = embeddingList.stream()
                    .map(obj -> {
                        if (obj instanceof Number) {
                            return ((Number) obj).doubleValue();
                        } else if (obj instanceof Double) {
                            return (Double) obj;
                        } else if (obj instanceof Float) {
                            return ((Float) obj).doubleValue();
                        } else {
                            return Double.parseDouble(obj.toString());
                        }
                    })
                    .collect(Collectors.toList());
            } else {
                throw new AIServiceException("Unexpected embedding format in REST API response");
            }
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            log.debug("Successfully generated REST embedding with {} dimensions in {}ms", 
                     embedding.size(), processingTime);
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model("rest:" + model())
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
                
        } catch (RestClientException e) {
            log.error("Error calling REST Embedding API", e);
            available = false; // Mark as unavailable for next check
            throw new AIServiceException("Failed to generate REST embedding: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error generating REST embedding", e);
            throw new AIServiceException("Failed to generate REST embedding", e);
        }
    }
    
    @Override
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
        try {
            if (!isAvailable()) {
                throw new AIServiceException("REST Embedding Provider is not available");
            }
            
            log.debug("Generating {} embeddings using REST API", texts.size());
            
            long startTime = System.currentTimeMillis();
            
            // Prepare batch request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("texts", texts);
            requestBody.put("model", model());
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            // Make REST API call
            String url = baseUrl() + batchEndpoint();
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AIServiceException("REST API returned non-success status: " + response.getStatusCode());
            }
            
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new AIServiceException("REST API returned empty response");
            }
            
            // Extract embeddings from response
            Object embeddingsObj = responseBody.get("embeddings");
            List<AIEmbeddingResponse> responses = new ArrayList<>();
            
            if (embeddingsObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> embeddingsList = (List<Object>) embeddingsObj;
                
                for (Object embeddingObj : embeddingsList) {
                    if (embeddingObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> embeddingList = (List<Object>) embeddingObj;
                        List<Double> embedding = embeddingList.stream()
                            .map(obj -> {
                                if (obj instanceof Number) {
                                    return ((Number) obj).doubleValue();
                                } else {
                                    return Double.parseDouble(obj.toString());
                                }
                            })
                            .collect(Collectors.toList());
                        
                        responses.add(AIEmbeddingResponse.builder()
                            .embedding(embedding)
                            .model("rest:" + model())
                            .dimensions(embedding.size())
                            .processingTimeMs((System.currentTimeMillis() - startTime) / texts.size())
                            .requestId(UUID.randomUUID().toString())
                            .build());
                    }
                }
            } else {
                // Fallback: generate one by one if batch endpoint doesn't work
                log.warn("Batch endpoint not available or returned unexpected format, generating one by one");
                for (String text : texts) {
                    AIEmbeddingRequest request = AIEmbeddingRequest.builder()
                        .text(text)
                        .model(model())
                        .build();
                    responses.add(generateEmbedding(request));
                }
            }
            
            log.debug("Successfully generated {} REST embeddings", responses.size());
            return responses;
            
        } catch (RestClientException e) {
            log.error("Error calling REST Embedding API for batch", e);
            available = false;
            throw new AIServiceException("Failed to generate batch REST embeddings: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error generating batch REST embeddings", e);
            throw new AIServiceException("Failed to generate batch REST embeddings", e);
        }
    }
    
    @Override
    public int getEmbeddingDimension() {
        return embeddingDimension;
    }
    

    private AIProviderConfig.RestConfig restConfig() {
        AIProviderConfig.RestConfig rest = config.getRest();
        if (rest == null) {
            throw new IllegalStateException("REST provider configuration is not defined");
        }
        return rest;
    }

    private String baseUrl() {
        return restConfig().getBaseUrl();
    }

    private String endpoint() {
        return restConfig().getEndpoint();
    }

    private String batchEndpoint() {
        return restConfig().getBatchEndpoint();
    }

    private String model() {
        return restConfig().getModel();
    }

    private Integer timeout() {
        return restConfig().getTimeout();
    }


    @Override
    public Map<String, Object> getStatus() {
        testConnection(); // Refresh availability
        Map<String, Object> status = new HashMap<>();
        status.put("provider", "rest");
        status.put("available", isAvailable());
        status.put("baseUrl", baseUrl());
        status.put("endpoint", endpoint());
        status.put("batchEndpoint", batchEndpoint());
        status.put("model", model());
        status.put("embeddingDimension", embeddingDimension);
        status.put("timeout", timeout());
        
        if (isAvailable()) {
            status.put("status", "ready");
        } else {
            status.put("status", "unavailable");
            status.put("message", "REST API service not available at " + baseUrl());
        }
        
        return status;
    }
}

