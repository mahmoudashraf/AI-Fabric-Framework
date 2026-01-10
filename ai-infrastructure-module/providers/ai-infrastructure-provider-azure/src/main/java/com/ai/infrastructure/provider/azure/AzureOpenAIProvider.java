package com.ai.infrastructure.provider.azure;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.ProviderConfig;
import com.ai.infrastructure.provider.ProviderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Azure OpenAI Provider implementation supporting both LLM and embedding APIs.
 */
@Slf4j
public class AzureOpenAIProvider implements AIProvider {

    private static final String HEADER_API_KEY = "api-key";

    private final ProviderConfig config;
    private final AIProviderConfig.AzureConfig azureConfig;
    private final RestTemplate restTemplate;

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastSuccess = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastError = new AtomicReference<>();
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>();
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public AzureOpenAIProvider(ProviderConfig config,
                               AIProviderConfig.AzureConfig azureConfig,
                               RestTemplate restTemplate) {
        this.config = Objects.requireNonNull(config, "ProviderConfig must not be null");
        this.azureConfig = Objects.requireNonNull(azureConfig, "Azure configuration must not be null");
        this.restTemplate = restTemplate != null ? restTemplate : buildRestTemplate();
    }

    @Override
    public String getProviderName() {
        return "azure";
    }

    @Override
    public boolean isAvailable() {
        try {
            if (!config.isValid() || !azureConfig.isEnabled() || !hasText(config.getApiKey()) || !hasText(azureConfig.getEndpoint())) {
                return false;
            }
            
            // For Azure AI Services (Foundry) or OpenAI-compatible format, deployment name is not required in URL
            // Check if endpoint contains /models or /openai/v1 (Foundry/OpenAI-compatible format)
            String endpoint = azureConfig.getEndpoint();
            if (endpoint.contains("/models") || endpoint.contains("/openai/v1") || endpoint.contains("services.ai.azure.com")) {
                // Deployment name is still needed for the model field in request body
                return hasText(azureConfig.getDeploymentName()) || hasText(config.getDefaultModel());
            }
            
            // For Azure OpenAI, deployment name is required
            return hasText(azureConfig.getDeploymentName());
        } catch (Exception ex) {
            log.warn("Azure provider validation failed: {}", ex.getMessage());
            return false;
        }
    }

    @Override
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        ensureAvailability();
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        try {
            String url = buildChatCompletionsUrl();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_API_KEY, config.getApiKey());

            List<Map<String, String>> messages = new ArrayList<>();
            
            // Handle system prompt and enhance for intent extraction
            String systemPrompt = request.getSystemPrompt();
            if (hasText(systemPrompt)) {
                // For intent extraction, enhance the system prompt to be very explicit about JSON-only responses
                if (request.getGenerationType() != null && request.getGenerationType().equals("intent_extraction")) {
                    String jsonInstruction = "CRITICAL JSON REQUIREMENT: You are a JSON-only API endpoint. " +
                        "You MUST respond with ONLY valid JSON. No markdown code blocks (no ```json or ```), " +
                        "no explanations, no text before or after the JSON, no comments, no additional formatting. " +
                        "Just the raw JSON object. If you include any text other than JSON, the response will fail to parse.\n\n";
                    
                    systemPrompt = jsonInstruction + systemPrompt;
                    
                    log.info("Enhanced system prompt for intent extraction with JSON-only requirement (length: {})", systemPrompt.length());
                    log.debug("Enhanced system prompt preview: {}", systemPrompt.substring(0, Math.min(200, systemPrompt.length())));
                }
                messages.add(Map.of("role", "system", "content", systemPrompt));
            }
            if (hasText(request.getPrompt())) {
                messages.add(Map.of("role", "user", "content", request.getPrompt()));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("messages", messages);
            body.put("temperature", Optional.ofNullable(request.getTemperature()).orElse(config.getTemperature()));
            body.put("max_tokens", Optional.ofNullable(request.getMaxTokens()).orElse(config.getMaxTokens()));
            
            // For OpenAI-compatible format (/openai/v1), include model in request body
            String endpoint = azureConfig.getEndpoint();
            if (endpoint != null && endpoint.contains("/openai/v1")) {
                String deployment = config.getDefaultModel();
                if (deployment == null || deployment.isEmpty()) {
                    deployment = azureConfig.getDeploymentName();
                }
                if (deployment != null && !deployment.isEmpty()) {
                    body.put("model", deployment);
                }
            }

            if (log.isInfoEnabled()) {
                log.info("=== AZURE OPENAI API REQUEST ===");
                log.info(
                    "Azure OpenAI request: url={}, messages={}, temperature={}, maxTokens={}, hasModelField={}",
                    url,
                    messages.size(),
                    body.get("temperature"),
                    body.get("max_tokens"),
                    body.containsKey("model")
                );
                String prompt = request.getPrompt();
                int len = prompt != null ? prompt.length() : 0;
                String snippet = prompt == null ? "" : prompt.substring(0, Math.min(500, len));
                log.info("Azure OpenAI request promptLength={}, promptSnippet={}", len, snippet);
                log.info("=== END AZURE OPENAI API REQUEST ===");
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = exchangeWithRetry(url, HttpMethod.POST, entity, Map.class, "chat.completions");

            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new AIServiceException("Azure response body was empty");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AIServiceException("Azure response did not contain choices");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = message != null ? (String) message.get("content") : "";

            if (log.isInfoEnabled()) {
                log.info("=== AZURE OPENAI API RESPONSE ===");
                Object model = responseBody.get("model");
                Object finishReason = choices.get(0).get("finish_reason");
                int contentLength = content != null ? content.length() : 0;
                log.info(
                    "Azure OpenAI response: responseTimeMs={}, model={}, finishReason={}, contentLength={}",
                    responseTime,
                    model,
                    finishReason,
                    contentLength
                );
                if (content != null) {
                    log.info("Azure OpenAI response contentSnippet={}", content.substring(0, Math.min(500, content.length())));
                }
                log.info("=== END AZURE OPENAI API RESPONSE ===");
            }

            return AIGenerationResponse.builder()
                .content(content)
                .model(config.getDefaultModel())
                .processingTimeMs(responseTime)
                .requestId(UUID.randomUUID().toString())
                .usage(createUsageFromResponse(responseBody))
                .build();
        } catch (Exception ex) {
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, responseTime);
            log.error("Azure content generation failed", ex);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(ex.getMessage());
            throw wrapException("Azure content generation failed", ex);
        }
    }

    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        ensureAvailability();
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        try {
            String deployment = azureConfig.getEmbeddingDeploymentName();
            // For Azure AI Services (Foundry), deployment name is not required in URL
            String endpoint = azureConfig.getEndpoint();
            if ((endpoint.contains("/models") || endpoint.contains("services.ai.azure.com")) && !hasText(deployment)) {
                // Foundry format - deployment name not needed in URL
                deployment = null;
            } else if (!hasText(deployment)) {
                throw new AIServiceException("Azure embedding deployment name is not configured");
            }

            String url = buildEmbeddingsUrl(deployment);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_API_KEY, config.getApiKey());

            Map<String, Object> body = new HashMap<>();
            body.put("input", List.of(request.getText()));

            if (log.isInfoEnabled()) {
                log.info("=== AZURE OPENAI EMBEDDING API REQUEST ===");
                log.info(
                    "Azure OpenAI embedding request: url={}, inputCount={}, textLength={}",
                    url,
                    1,
                    request.getText() != null ? request.getText().length() : 0
                );
                String text = request.getText();
                int len = text != null ? text.length() : 0;
                String snippet = text == null ? "" : text.substring(0, Math.min(300, len));
                log.info("Azure OpenAI embedding request textSnippet={}", snippet);
                log.info("=== END AZURE OPENAI EMBEDDING API REQUEST ===");
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = exchangeWithRetry(url, HttpMethod.POST, entity, Map.class, "embeddings");

            long processingTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, processingTime);

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new AIServiceException("Azure embedding response body was empty");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseBody.get("data");
            if (data == null || data.isEmpty()) {
                throw new AIServiceException("Azure embedding response did not contain data");
            }

            @SuppressWarnings("unchecked")
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");
            if (embedding == null) {
                throw new AIServiceException("Azure embedding data missing");
            }

            if (log.isInfoEnabled()) {
                log.info("=== AZURE OPENAI EMBEDDING API RESPONSE ===");
                log.info(
                    "Azure OpenAI embedding response: responseTimeMs={}, dimensions={}",
                    processingTime,
                    embedding.size()
                );
                log.info("=== END AZURE OPENAI EMBEDDING API RESPONSE ===");
            }

            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(deployment)
                .dimensions(embedding.size())
                .processingTimeMs(processingTime)
                .requestId(UUID.randomUUID().toString())
                .build();
        } catch (Exception ex) {
            long processingTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, processingTime);
            log.error("Azure embedding generation failed", ex);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(ex.getMessage());
            throw wrapException("Azure embedding generation failed", ex);
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
            .details("Endpoint=" + azureConfig.getEndpoint())
            .lastUpdated(LocalDateTime.now())
            .build();
    }

    @Override
    public ProviderConfig getConfig() {
        return config;
    }

    private void ensureAvailability() {
        if (!isAvailable()) {
            throw new AIServiceException("Azure OpenAI provider is not available. Check API key and endpoint configuration.");
        }
    }

    private RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Optional.ofNullable(azureConfig.getTimeout()).orElse(60) * 1000;
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return new RestTemplate(factory);
    }

    private String buildChatCompletionsUrl() {
        String endpoint = normalizeEndpoint(azureConfig.getEndpoint());
        String apiVersion = azureConfig.getApiVersion() != null ? azureConfig.getApiVersion() : "2024-02-15-preview";
        
        // Check if endpoint already contains /models/chat/completions (Azure AI Services/Foundry format)
        if (endpoint.contains("/models/chat/completions")) {
            // Azure AI Services (Foundry) format - endpoint already includes the path
            if (endpoint.contains("?")) {
                return endpoint; // Already has query params
            }
            return String.format("%s?api-version=%s", endpoint, apiVersion);
        }
        
        // Check if endpoint contains /openai/v1 (OpenAI-compatible format on Azure AI Services)
        if (endpoint.contains("/openai/v1")) {
            // OpenAI-compatible format - use /chat/completions endpoint
            String deployment = config.getDefaultModel();
            if (deployment == null || deployment.isEmpty()) {
                deployment = azureConfig.getDeploymentName();
            }
            // For OpenAI-compatible format, model is passed in the request body, not URL
            return String.format("%s/chat/completions", endpoint);
        }
        
        // Check if endpoint contains /models (Azure AI Services/Foundry base format)
        if (endpoint.contains("/models")) {
            // Azure AI Services (Foundry) format - add chat/completions
            return String.format("%s/chat/completions?api-version=%s", endpoint, apiVersion);
        }
        
        // Azure OpenAI format - traditional deployment-based
        String deployment = config.getDefaultModel();
        if (deployment == null || deployment.isEmpty()) {
            deployment = azureConfig.getDeploymentName();
        }
        return String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
            endpoint, deployment, apiVersion);
    }

    private String buildEmbeddingsUrl(String deployment) {
        String endpoint = normalizeEndpoint(azureConfig.getEndpoint());
        String apiVersion = azureConfig.getApiVersion() != null ? azureConfig.getApiVersion() : "2024-02-15-preview";
        
        // Check if endpoint already contains /models/embeddings (Azure AI Services/Foundry format)
        if (endpoint.contains("/models/embeddings")) {
            // Azure AI Services (Foundry) format - endpoint already includes the path
            if (endpoint.contains("?")) {
                return endpoint; // Already has query params
            }
            return String.format("%s?api-version=%s", endpoint, apiVersion);
        }
        
        // Check if endpoint contains /models (Azure AI Services/Foundry base format)
        if (endpoint.contains("/models")) {
            // Azure AI Services (Foundry) format - add embeddings
            return String.format("%s/embeddings?api-version=%s", endpoint, apiVersion);
        }
        
        // Azure OpenAI format - traditional deployment-based
        if (deployment == null || deployment.isEmpty()) {
            deployment = azureConfig.getEmbeddingDeploymentName();
        }
        return String.format("%s/openai/deployments/%s/embeddings?api-version=%s",
            endpoint, deployment, apiVersion);
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        return endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
    }

    private void updateMetrics(boolean success, long responseTime) {
        if (success) {
            successfulRequests.incrementAndGet();
            lastSuccess.set(LocalDateTime.now());
        } else {
            failedRequests.incrementAndGet();
        }

        long total = totalRequests.get();
        double currentAvg = averageResponseTime.get();
        double newAvg = total <= 1 ? responseTime : ((currentAvg * (total - 1)) + responseTime) / total;
        averageResponseTime.set(newAvg);
    }


    private double calculateSuccessRate() {
        long total = totalRequests.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) successfulRequests.get() / total;
    }

    private boolean isHealthy() {
        if (!isAvailable()) {
            return false;
        }
        LocalDateTime recent = lastSuccess.get();
        return recent != null && recent.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    private Map<String, Object> createUsageFromResponse(Map<String, Object> responseBody) {
        Map<String, Object> usage = new HashMap<>();
        Object usageNode = responseBody.get("usage");
        if (usageNode instanceof Map<?, ?> usageMap) {
            usage.putAll((Map<String, Object>) usageMap);
        }
        return usage;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private AIServiceException wrapException(String message, Exception ex) {
        return ex instanceof AIServiceException serviceException
            ? serviceException
            : new AIServiceException(message + ": " + ex.getMessage(), ex);
    }

    private <T> ResponseEntity<T> exchangeWithRetry(String url,
                                                    HttpMethod method,
                                                    HttpEntity<?> entity,
                                                    Class<T> responseType,
                                                    String operation) {
        long backoffMs = 400;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return restTemplate.exchange(url, method, entity, responseType);
            } catch (HttpStatusCodeException ex) {
                HttpStatusCode statusCode = ex.getStatusCode();
                int rawStatus = statusCode != null ? statusCode.value() : ex.getRawStatusCode();
                if (attempt < MAX_RETRY_ATTEMPTS && isRetryableStatus(rawStatus)) {
                    log.warn(
                        "Azure {} call failed with HTTP {} (attempt {}/{}). Retrying after {}ms.",
                        operation,
                        rawStatus,
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        backoffMs
                    );
                    sleepWithJitter(backoffMs);
                    backoffMs = Math.min(3000, backoffMs * 2);
                    continue;
                }
                throw ex;
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn(
                        "Azure {} call failed due to network/timeout (attempt {}/{}). Retrying after {}ms. Cause: {}",
                        operation,
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        backoffMs,
                        ex.getMessage()
                    );
                    sleepWithJitter(backoffMs);
                    backoffMs = Math.min(3000, backoffMs * 2);
                    continue;
                }
                throw ex;
            } catch (RestClientException ex) {
                // Preserve previous behavior for non-HTTP RestClientExceptions.
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn(
                        "Azure {} call failed (attempt {}/{}). Retrying after {}ms. Cause: {}",
                        operation,
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        backoffMs,
                        ex.getMessage()
                    );
                    sleepWithJitter(backoffMs);
                    backoffMs = Math.min(3000, backoffMs * 2);
                    continue;
                }
                throw ex;
            }
        }
        throw new AIServiceException("Azure " + operation + " call failed after retries");
    }

    private boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || (status >= 500 && status < 600);
    }

    private void sleepWithJitter(long baseBackoffMs) {
        long jitter = ThreadLocalRandom.current().nextLong(0, 200);
        long sleepMs = baseBackoffMs + jitter;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
