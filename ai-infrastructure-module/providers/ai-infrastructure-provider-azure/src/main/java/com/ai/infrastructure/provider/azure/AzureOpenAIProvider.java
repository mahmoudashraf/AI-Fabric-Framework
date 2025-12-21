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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
            return config.isValid()
                && azureConfig.isEnabled()
                && hasText(config.getApiKey())
                && hasText(azureConfig.getEndpoint())
                && hasText(azureConfig.getDeploymentName());
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
            if (hasText(request.getSystemPrompt())) {
                messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
            }
            if (hasText(request.getPrompt())) {
                messages.add(Map.of("role", "user", "content", request.getPrompt()));
            }

            Map<String, Object> body = new HashMap<>();
            body.put("messages", messages);
            body.put("temperature", Optional.ofNullable(request.getTemperature()).orElse(config.getTemperature()));
            body.put("max_tokens", Optional.ofNullable(request.getMaxTokens()).orElse(config.getMaxTokens()));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

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
            if (!hasText(deployment)) {
                throw new AIServiceException("Azure embedding deployment name is not configured");
            }

            String url = buildEmbeddingsUrl(deployment);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HEADER_API_KEY, config.getApiKey());

            Map<String, Object> body = new HashMap<>();
            body.put("input", List.of(request.getText()));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

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
        return String.format("%s/openai/deployments/%s/chat/completions?api-version=%s",
            normalizeEndpoint(azureConfig.getEndpoint()),
            config.getDefaultModel(),
            azureConfig.getApiVersion());
    }

    private String buildEmbeddingsUrl(String deployment) {
        return String.format("%s/openai/deployments/%s/embeddings?api-version=%s",
            normalizeEndpoint(azureConfig.getEndpoint()),
            deployment,
            azureConfig.getApiVersion());
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
}
