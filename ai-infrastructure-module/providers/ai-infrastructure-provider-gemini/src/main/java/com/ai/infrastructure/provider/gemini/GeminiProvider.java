package com.ai.infrastructure.provider.gemini;

import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.ProviderConfig;
import com.ai.infrastructure.provider.ProviderStatus;
import com.ai.infrastructure.http.HttpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Google Gemini Provider Implementation
 * 
 * Real implementation of AI provider using Google Gemini API.
 * Provides content generation and embedding services.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class GeminiProvider implements AIProvider {
    
    private final ProviderConfig config;
    private final HttpClient httpClient;
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulRequests = new AtomicLong(0);
    private final AtomicLong failedRequests = new AtomicLong(0);
    private final AtomicReference<LocalDateTime> lastSuccess = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastError = new AtomicReference<>();
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>();
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final int MAX_RETRY_ATTEMPTS = 5;
    
    @Override
    public String getProviderName() {
        return "gemini";
    }
    
    @Override
    public boolean isAvailable() {
        try {
            return config.isValid() && config.isEnabled() && 
                   config.getApiKey() != null && !config.getApiKey().trim().isEmpty();
        } catch (Exception e) {
            log.warn("Gemini provider not available: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            log.debug("Generating content with Gemini: model={}, generationType={}, prompt={}", 
                     request.getModel(), request.getGenerationType(), 
                     request.getPrompt() != null ? request.getPrompt().substring(0, Math.min(100, request.getPrompt().length())) : "null");
            
            String model = request.getModel() != null ? request.getModel() : config.getDefaultModel();
            if (model == null || model.trim().isEmpty()) {
                model = "gemini-2.5-flash"; // Default Gemini model
            }
            
            String url = GEMINI_BASE_URL + "/models/" + model + ":generateContent?key=" + config.getApiKey();
            String safeUrl = url.replaceAll("([?&]key=)[^&]+", "$1***");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            
            // Build contents array - Gemini uses "contents" instead of "messages"
            List<Map<String, Object>> contents = new ArrayList<>();
            Map<String, Object> content = new HashMap<>();

            Map<String, Object> requestParams = request.getParameters() != null ? request.getParameters() : Map.of();
            boolean jsonMimeTypeRequested = isJsonMimeTypeRequested(requestParams);

            // Add system instruction if present (Gemini uses systemInstruction in generationConfig)
            String systemPrompt = request.getSystemPrompt() != null ? request.getSystemPrompt() : "";

            boolean jsonOnlyTask = jsonMimeTypeRequested
                || hasOpenAIStyleJsonHint(requestParams)
                || isJsonOnlySystemPrompt(systemPrompt)
                || isJsonTaskGenerationType(request.getGenerationType());
            
            // For JSON-sensitive tasks, enhance the system prompt to be very explicit about JSON-only responses.
            // (Additionally, set generationConfig.responseMimeType to application/json when requested.)
            if (jsonOnlyTask || (request.getGenerationType() != null && request.getGenerationType().contains("intent_extraction"))) {
                String jsonInstruction = "CRITICAL JSON REQUIREMENT: You are a JSON-only API endpoint. " +
                    "You MUST respond with ONLY valid JSON. No markdown code blocks (no ```json or ```), " +
                    "no explanations, no text before or after the JSON, no comments, no additional formatting. " +
                    "Just the raw JSON object. If you include any text other than JSON, the response will fail to parse.\n\n";
                
                systemPrompt = jsonInstruction + systemPrompt;
                
                log.info("Enhanced system prompt with JSON-only requirement (length: {})", systemPrompt.length());
            }
            
            // Gemini uses "parts" array with text content
            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", request.getPrompt()));
            content.put("parts", parts);
            contents.add(content);
            requestBody.put("contents", contents);
            
            // Add generation config
            Map<String, Object> generationConfig = new HashMap<>();
            if (request.getMaxTokens() != null) {
                generationConfig.put("maxOutputTokens", request.getMaxTokens());
            } else if (config.getMaxTokens() != null) {
                generationConfig.put("maxOutputTokens", config.getMaxTokens());
            }
            if (request.getTemperature() != null) {
                generationConfig.put("temperature", request.getTemperature());
            } else if (config.getTemperature() != null) {
                generationConfig.put("temperature", config.getTemperature());
            }

            // Gemini supports enforcing JSON output via responseMimeType.
            // Only enable this when explicitly requested for Gemini (response_mime_type / responseMimeType).
            // OpenAI's response_format hints are provider-specific and can unintentionally constrain Gemini output.
            if (jsonMimeTypeRequested) {
                generationConfig.put("responseMimeType", "application/json");
            }
            
            // Add system instruction if present
            if (!systemPrompt.trim().isEmpty()) {
                Map<String, Object> systemInstruction = new HashMap<>();
                systemInstruction.put("parts", List.of(Map.of("text", systemPrompt)));
                requestBody.put("systemInstruction", systemInstruction);
            }
            
            if (!generationConfig.isEmpty()) {
                requestBody.put("generationConfig", generationConfig);
            }

            if (log.isInfoEnabled()) {
                log.info("=== GEMINI API REQUEST ===");
                Object temperature = generationConfig.get("temperature");
                Object maxOutputTokens = generationConfig.get("maxOutputTokens");
                log.info(
                    "Gemini API request: url={}, model={}, temperature={}, maxOutputTokens={}, hasSystemInstruction={}, promptLength={}",
                    safeUrl,
                    model,
                    temperature,
                    maxOutputTokens,
                    requestBody.containsKey("systemInstruction"),
                    request.getPrompt() != null ? request.getPrompt().length() : 0
                );
                String prompt = request.getPrompt();
                int len = prompt != null ? prompt.length() : 0;
                String snippet = prompt == null ? "" : prompt.substring(0, Math.min(500, len));
                log.info("Gemini API request promptSnippet={}", snippet);
                log.info("=== END GEMINI API REQUEST ===");
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = exchangeWithRetry(url, HttpMethod.POST, entity, Map.class, "generateContent");
            
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Gemini returned an empty response body");
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) responseBody.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new RuntimeException("Gemini returned no candidates");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> candidate = candidates.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> contentResponse = (Map<String, Object>) candidate.get("content");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> partsResponse = (List<Map<String, Object>>) contentResponse.get("parts");
            String generatedText = "";
            if (partsResponse != null && !partsResponse.isEmpty()) {
                generatedText = partsResponse.stream()
                    .map(part -> part != null ? (String) part.get("text") : null)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining());
            }

            if (log.isInfoEnabled()) {
                log.info("=== GEMINI API RESPONSE ===");
                Object finishReason = candidate.get("finishReason");
                int contentLength = generatedText != null ? generatedText.length() : 0;
                log.info(
                    "Gemini API response: responseTimeMs={}, model={}, finishReason={}, contentLength={}, candidates={}",
                    responseTime,
                    model,
                    finishReason,
                    contentLength,
                    candidates != null ? candidates.size() : 0
                );
                if (generatedText != null) {
                    log.info(
                        "Gemini API response contentSnippet={}",
                        generatedText.substring(0, Math.min(500, generatedText.length()))
                    );
                }
                log.info("=== END GEMINI API RESPONSE ===");
            }
            
            log.debug("Gemini content generation completed in {}ms", responseTime);
            
            // Extract usage information if available
            @SuppressWarnings("unchecked")
            Map<String, Object> usageMetadata = (Map<String, Object>) responseBody.get("usageMetadata");
            
            // Create usage map
            Map<String, Object> usage = null;
            if (usageMetadata != null) {
                usage = new HashMap<>();
                usage.put("promptTokens", getIntValue(usageMetadata, "promptTokenCount"));
                usage.put("completionTokens", getIntValue(usageMetadata, "candidatesTokenCount"));
                usage.put("totalTokens", getIntValue(usageMetadata, "totalTokenCount"));
            }
            
            return AIGenerationResponse.builder()
                .content(generatedText)
                .model(model)
                .usage(usage)
                .processingTimeMs(responseTime)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, responseTime);
            
            log.error("Gemini content generation failed", e);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(e.getMessage());
            
            throw new RuntimeException("Gemini content generation failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        long startTime = System.currentTimeMillis();
        totalRequests.incrementAndGet();
        
        try {
            log.debug("Generating embedding with Gemini: model={}, text={}", 
                     request.getModel(), request.getText().substring(0, Math.min(100, request.getText().length())));
            
            String model = request.getModel() != null ? request.getModel() : config.getDefaultEmbeddingModel();
            if (model == null || model.trim().isEmpty()) {
                model = "text-embedding-004"; // Default Gemini embedding model
            }
            
            String url = GEMINI_BASE_URL + "/models/" + model + ":embedContent?key=" + config.getApiKey();
            String safeUrl = url.replaceAll("([?&]key=)[^&]+", "$1***");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            
            // Gemini embedding API structure
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();
            part.put("text", request.getText());
            content.put("parts", List.of(part));
            requestBody.put("content", content);

            if (log.isInfoEnabled()) {
                log.info("=== GEMINI EMBEDDING API REQUEST ===");
                log.info(
                    "Gemini embedding request: url={}, model={}, textLength={}",
                    safeUrl,
                    model,
                    request.getText() != null ? request.getText().length() : 0
                );
                String text = request.getText();
                int len = text != null ? text.length() : 0;
                String snippet = text == null ? "" : text.substring(0, Math.min(300, len));
                log.info("Gemini embedding request textSnippet={}", snippet);
                log.info("=== END GEMINI EMBEDDING API REQUEST ===");
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = exchangeWithRetry(url, HttpMethod.POST, entity, Map.class, "embedContent");
            
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(true, responseTime);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            @SuppressWarnings("unchecked")
            Map<String, Object> embeddingData = (Map<String, Object>) responseBody.get("embedding");
            @SuppressWarnings("unchecked")
            List<Double> values = (List<Double>) embeddingData.get("values");
            
            // Keep as List<Double> for embedding
            List<Double> embedding = new ArrayList<>(values);

            if (log.isInfoEnabled()) {
                log.info("=== GEMINI EMBEDDING API RESPONSE ===");
                log.info(
                    "Gemini embedding response: responseTimeMs={}, model={}, dimensions={}",
                    responseTime,
                    model,
                    embedding != null ? embedding.size() : 0
                );
                log.info("=== END GEMINI EMBEDDING API RESPONSE ===");
            }
            
            log.debug("Gemini embedding generation completed in {}ms, dimension: {}", responseTime, embedding.size());
            
            return AIEmbeddingResponse.builder()
                .embedding(embedding)
                .model(model)
                .dimensions(embedding.size())
                .processingTimeMs(responseTime)
                .requestId(java.util.UUID.randomUUID().toString())
                .build();
                
        } catch (Exception e) {
            long responseTime = System.currentTimeMillis() - startTime;
            updateMetrics(false, responseTime);
            
            log.error("Gemini embedding generation failed", e);
            lastError.set(LocalDateTime.now());
            lastErrorMessage.set(e.getMessage());
            
            throw new RuntimeException("Gemini embedding generation failed: " + e.getMessage(), e);
        }
    }

    private <T> ResponseEntity<T> exchangeWithRetry(String url,
                                                    HttpMethod method,
                                                    HttpEntity<?> entity,
                                                    Class<T> responseType,
                                                    String operation) {
        long backoffMs = 400;
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return httpClient.exchange(url, method, entity, responseType);
            } catch (HttpStatusCodeException ex) {
                HttpStatusCode statusCode = ex.getStatusCode();
                int rawStatus = statusCode != null ? statusCode.value() : ex.getRawStatusCode();
                if (attempt < MAX_RETRY_ATTEMPTS && isRetryableStatus(rawStatus)) {
                    log.warn(
                        "Gemini {} call failed with HTTP {} (attempt {}/{}). Retrying after {}ms.",
                        operation,
                        rawStatus,
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        backoffMs
                    );
                    sleepWithJitter(backoffMs);
                    backoffMs = Math.min(8000, backoffMs * 2);
                    continue;
                }
                throw ex;
            } catch (ResourceAccessException ex) {
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    log.warn(
                        "Gemini {} call failed due to network/timeout (attempt {}/{}). Retrying after {}ms. Cause: {}",
                        operation,
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        backoffMs,
                        ex.getMessage()
                    );
                    sleepWithJitter(backoffMs);
                    backoffMs = Math.min(8000, backoffMs * 2);
                    continue;
                }
                throw ex;
            }
        }
        throw new RuntimeException("Gemini " + operation + " call failed after retries");
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
    
    @Override
    public ProviderStatus getStatus() {
        return ProviderStatus.builder()
            .providerName(getProviderName())
            .available(isAvailable())
            .totalRequests(totalRequests.get())
            .successfulRequests(successfulRequests.get())
            .failedRequests(failedRequests.get())
            .lastSuccess(lastSuccess.get())
            .lastError(lastError.get())
            .lastErrorMessage(lastErrorMessage.get())
            .averageResponseTime(averageResponseTime.get())
            .build();
    }
    
    @Override
    public ProviderConfig getConfig() {
        return config;
    }
    
    private void updateMetrics(boolean success, long responseTime) {
        if (success) {
            successfulRequests.incrementAndGet();
            lastSuccess.set(LocalDateTime.now());
        } else {
            failedRequests.incrementAndGet();
            lastError.set(LocalDateTime.now());
        }
        
        // Update average response time
        long total = totalRequests.get();
        double currentAvg = averageResponseTime.get();
        double newAvg = ((currentAvg * (total - 1)) + responseTime) / total;
        averageResponseTime.set(newAvg);
    }
    
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private boolean isJsonMimeTypeRequested(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }
        Object responseMimeType = parameters.get("response_mime_type");
        if (responseMimeType == null) {
            responseMimeType = parameters.get("responseMimeType");
        }
        if (responseMimeType == null) {
            return false;
        }
        String value = String.valueOf(responseMimeType).trim().toLowerCase();
        return value.contains("json");
    }

    private boolean hasOpenAIStyleJsonHint(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return false;
        }
        Object responseFormat = parameters.get("response_format");
        if (responseFormat == null) {
            responseFormat = parameters.get("responseFormat");
        }
        if (responseFormat == null) {
            return false;
        }

        if (responseFormat instanceof Map<?, ?> map) {
            Object type = map.get("type");
            if (type == null) {
                type = map.get("Type");
            }
            return type != null && String.valueOf(type).trim().toLowerCase().contains("json");
        }

        return String.valueOf(responseFormat).trim().toLowerCase().contains("json");
    }

    private boolean isJsonOnlySystemPrompt(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return false;
        }
        return systemPrompt.toLowerCase().contains("json");
    }

    private boolean isJsonTaskGenerationType(String generationType) {
        if (generationType == null || generationType.isBlank()) {
            return false;
        }
        String value = generationType.toLowerCase();
        return value.contains("planning")
            || value.contains("intent_extraction")
            || value.contains("intent-extraction");
    }
}
