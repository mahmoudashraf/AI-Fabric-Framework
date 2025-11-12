# MODULE Developer Guide – AI Providers

**Document Purpose:** Production-ready implementation and operations guide for the AI provider subsystem  
**Audience:** Backend engineers, platform engineers, SREs supporting AI workloads  
**Last Updated:** November 2025  
**Status:** ✅ Production Ready  

---

## 📋 Table of Contents

1. [Quick Start](#-quick-start)
2. [Architecture Overview](#-architecture-overview)
3. [Provider Selection & Failover](#-provider-selection--failover)
4. [Provider Implementations](#-provider-implementations)
5. [Configuration & Secrets](#-configuration--secrets)
6. [Observability & Operations](#-observability--operations)
7. [Testing & Validation](#-testing--validation)
8. [Deployment Checklist](#-deployment-checklist)
9. [Troubleshooting Guide](#-troubleshooting-guide)
10. [Quick Reference](#-quick-reference)

---

## 🚀 Quick Start

### Prerequisites
- Java 21+, Maven 3.8+, Docker (for vector DB services)
- Production API credentials (OpenAI required, Cohere/Anthropic optional)
- Outbound HTTPS access to provider endpoints
- Secrets management platform (Vault, AWS Secrets Manager, etc.)

### Minimal Setup
1. **Bootstrap dependencies**
   ```bash
   cd ai-infrastructure-module
   mvn -pl ai-infrastructure-core clean verify
   ```
2. **Expose secrets securely**
   ```bash
   export OPENAI_API_KEY=<your_openai_key>
   export COHERE_API_KEY=<optional_cohere_key>
   export ANTHROPIC_API_KEY=<optional_anthropic_key>
   ```
3. **Production profile snippet (`application-prod.yml`)**
   ```yaml
   ai:
     enabled: true
     providers:
       openai-api-key: ${OPENAI_API_KEY}
       openai-model: gpt-4o-mini
       embedding-provider: onnx
       enable-fallback: true
     service:
       default-provider: openai
       health-checks:
         enabled: true
       metrics:
         enabled: true
   ```
   > ⚠️ Never commit raw API keys. Use your platform’s secret interpolation (Spring Cloud Config, Vault, KMS).

4. **Verify bean wiring**
   ```bash
   mvn -pl ai-infrastructure-module/integration-tests test -Dtest=ProviderManagerIntegrationTest
   ```

### Real API Smoke (non-mocked)
Run real-provider smoke tests before promoting to production. Costs apply.
```bash
cd ai-infrastructure-module
./run-real-api-tests.sh
```
The script fails fast if `OPENAI_API_KEY` is not set and executes the dedicated real API integration suite using the `real-api-test` Spring profile.

---

## 🏗️ Architecture Overview

### Core Components
- **`AIProviderManager`** – Orchestrates provider selection, fallback, and status tracking during content or embedding flows.
- **Provider implementations** – `OpenAIProvider`, `CohereProvider`, `AnthropicProvider`; each speaks to real external endpoints with retry-aware metrics.
- **`ProviderConfig` / `ProviderStatus`** – Central configuration and live telemetry structure shared across providers.
- **`AICoreService`** – Entry point used by the rest of the platform; delegates generation to the manager and embeddings to `AIEmbeddingService`.

```48:116:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/AIProviderManager.java
// ... existing code ...
public AIGenerationResponse generateContent(AIGenerationRequest request) {
    log.debug("Generating content with provider manager");
    List<AIProvider> availableProviders = getAvailableProviders();
    if (availableProviders.isEmpty()) {
        throw new RuntimeException("No AI providers available");
    }
    AIProvider selectedProvider = selectProvider(availableProviders, "generation");
    try {
        AIGenerationResponse response = selectedProvider.generateContent(request);
        updateProviderStatus(selectedProvider.getProviderName(), true);
        return response;
    } catch (Exception e) {
        updateProviderStatus(selectedProvider.getProviderName(), false);
        return tryFallbackProviders(request, availableProviders, selectedProvider);
    }
}
```

```40:88:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/core/AICoreService.java
// ... existing code ...
public AIGenerationResponse generateContent(AIGenerationRequest request) {
    AIGenerationRequest generationRequest = applyGenerationDefaults(request);
    log.debug("Generating AI content via provider manager for prompt: {}", generationRequest.getPrompt());
    AIGenerationResponse response = providerManager.generateContent(generationRequest);
    log.debug("Successfully generated AI content using provider response model: {}", response.getModel());
    return response;
}
```

### Interaction Flow
```
Client → AICoreService → AIProviderManager
                              ├── OpenAIProvider (default)
                              ├── CohereProvider (optional)
                              └── AnthropicProvider (optional, generation-only)
```
Embeddings are routed through `AIEmbeddingService`, which prefers ONNX locally and can fail over to remote providers when configured.

---

## 🔄 Provider Selection & Failover

`AIProviderManager` evaluates providers in the following order: priority override → health → performance → random fallback. Fallbacks are attempted automatically on failures for both generation and embedding workloads.

```197:221:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/AIProviderManager.java
private AIProvider selectProvider(List<AIProvider> availableProviders, String operationType) {
    if (availableProviders.isEmpty()) {
        throw new RuntimeException("No providers available");
    }
    AIProvider priorityProvider = selectByPriority(availableProviders);
    if (priorityProvider != null) {
        return priorityProvider;
    }
    AIProvider healthProvider = selectByHealth(availableProviders);
    if (healthProvider != null) {
        return healthProvider;
    }
    AIProvider performanceProvider = selectByPerformance(availableProviders);
    if (performanceProvider != null) {
        return performanceProvider;
    }
    return availableProviders.get(new Random().nextInt(availableProviders.size()));
}
```

```264:320:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/AIProviderManager.java
private AIGenerationResponse tryFallbackProviders(AIGenerationRequest request,
                                                 List<AIProvider> availableProviders,
                                                 AIProvider failedProvider) {
    List<AIProvider> fallbackProviders = availableProviders.stream()
        .filter(p -> !p.getProviderName().equals(failedProvider.getProviderName()))
        .collect(Collectors.toList());
    for (AIProvider provider : fallbackProviders) {
        try {
            AIGenerationResponse response = provider.generateContent(request);
            updateProviderStatus(provider.getProviderName(), true);
            return response;
        } catch (Exception e) {
            updateProviderStatus(provider.getProviderName(), false);
        }
    }
    throw new RuntimeException("All providers failed for content generation");
}
```

Operational notes:
- Configure priorities explicitly; higher numbers win.
- Health scoring relies on each provider’s `ProviderStatus` metrics (success rate, response time, recent activity).
- Fallback failures are logged with provider names for quick remediation.

---

## 🤖 Provider Implementations

### OpenAIProvider
- Uses official REST endpoints for chat completions and embeddings with bearer authentication.
- Tracks total/success/failed counts, moving average latency, and last error.
- Logs payload metadata (model, token usage) at debug level for observability.

```68:220:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/OpenAIProvider.java
// ... existing code ...
headers.set("Authorization", "Bearer " + config.getApiKey());
Map<String, Object> requestBody = new HashMap<>();
requestBody.put("model", request.getModel() != null ? request.getModel() : config.getDefaultModel());
requestBody.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : config.getMaxTokens());
requestBody.put("temperature", request.getTemperature() != null ? request.getTemperature() : config.getTemperature());
ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
updateMetrics(true, responseTime);
return AIGenerationResponse.builder()
    .content(content)
    .model((String) responseBody.get("model"))
    .processingTimeMs(responseTime)
    .build();
```

### CohereProvider
- Real HTTPS client with API key header (`Authorization: Bearer …`).
- Supports generation and embeddings; both reuse `ProviderConfig` defaults and update metrics consistently.

```68:147:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/CohereProvider.java
headers.set("Authorization", "Bearer " + config.getApiKey());
requestBody.put("model", request.getModel() != null ? request.getModel() : config.getDefaultModel());
requestBody.put("prompt", request.getPrompt());
ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
updateMetrics(true, responseTime);
return AIGenerationResponse.builder()
    .content(generatedText)
    .model((String) responseBody.get("model"))
    .processingTimeMs(responseTime)
    .build();
```

### AnthropicProvider
- Supports text generation; embeddings delegate back to other providers and currently throw a controlled `UnsupportedOperationException`.
- Keep Anthropic deactivated for embedding workloads to avoid runtime exceptions.

```128:149:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/AnthropicProvider.java
// ... existing code ...
throw new UnsupportedOperationException("Anthropic does not provide embedding services directly. " +
    "Please use OpenAI or another provider for embeddings.");
```

### Embedding Service (ONNX-first)
- ONNX is the primary embedding engine; REST or OpenAI embeddings can be enabled via configuration.
- Fallback ONNX provider is registered automatically when `ai.providers.enable-fallback=true`.

```115:187:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java
@Bean
@ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "onnx", matchIfMissing = true)
public EmbeddingProvider onnxEmbeddingProvider(AIProviderConfig config) {
    log.info("Creating ONNX Embedding Provider (primary/default)");
    return new ONNXEmbeddingProvider(config);
}
@Bean
public AIEmbeddingService aiEmbeddingService(AIProviderConfig config,
                                             List<EmbeddingProvider> embeddingProviders,
                                             ObjectProvider<CacheManager> cacheManagerProvider,
                                             @Qualifier("onnxFallbackEmbeddingProvider") @Nullable EmbeddingProvider fallbackEmbeddingProvider) {
    EmbeddingProvider selectedProvider = embeddingProviders.stream()
        .filter(provider -> provider.getProviderName().equalsIgnoreCase(config.getEmbeddingProvider()))
        .findFirst()
        .orElseGet(() -> embeddingProviders.isEmpty() ? null : embeddingProviders.get(0));
    return new AIEmbeddingService(config, selectedProvider, cacheManagerProvider.getIfAvailable(NoOpCacheManager::new), fallbackEmbeddingProvider);
}
```

---

## ⚙️ Configuration & Secrets

### ProviderConfig defaults
```15:129:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/ProviderConfig.java
@Builder
public class ProviderConfig {
    private String providerName;
    private String apiKey;
    private String baseUrl;
    private String defaultModel;
    private String defaultEmbeddingModel;
    private Integer maxTokens;
    private Double temperature;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private Long retryDelayMs;
    private Integer rateLimitPerMinute;
    private Integer rateLimitPerDay;
    private boolean enabled;
    private Integer priority;
    private Map<String, Object> customConfig;
    public boolean isValid() { /* ... */ }
}
```

```430:448:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java
@Bean
@ConditionalOnMissingBean
public ProviderConfig providerConfig(AIProviderConfig aiProviderConfig) {
    return ProviderConfig.builder()
        .providerName("openai")
        .apiKey(aiProviderConfig.getOpenaiApiKey())
        .baseUrl("https://api.openai.com/v1")
        .defaultModel(aiProviderConfig.getOpenaiModel())
        .defaultEmbeddingModel(aiProviderConfig.getOpenaiEmbeddingModel())
        .maxTokens(aiProviderConfig.getOpenaiMaxTokens())
        .temperature(aiProviderConfig.getOpenaiTemperature())
        .timeoutSeconds(aiProviderConfig.getOpenaiTimeout())
        .maxRetries(3)
        .retryDelayMs(1000L)
        .rateLimitPerMinute(60)
        .rateLimitPerDay(10000)
        .enabled(true)
        .priority(1)
        .build();
}
```

| Property | Description | Production Guidance |
| --- | --- | --- |
| `ai.providers.openai-api-key` | OpenAI secret | Inject via secret manager; rotate quarterly |
| `ai.providers.embedding-provider` | `onnx`, `rest`, or `openai` | Keep `onnx` for latency-sensitive workloads; document migrations before switching |
| `ai.providers.enable-fallback` | Toggle ONNX fallback | Leave enabled to guarantee local failover |
| `ai.service.default-provider` | Base provider for generation | Align with highest priority provider |
| `ai.service.health-checks.enabled` | Enables `AIHealthIndicator` | Keep on in all prod/staging environments |

#### Multiple Providers
To onboard Cohere or Anthropic in addition to OpenAI:
1. Provide provider-specific API keys via environment variables or your secrets backend.
2. Override the default `ProviderConfig` bean in your application configuration to supply provider-specific credentials and priorities.
3. Update `ai.service.default-provider` to the intended primary and adjust priorities accordingly.
   > ℹ️ The default auto-configuration publishes a single `ProviderConfig` bean shared by every provider. When powering multiple providers simultaneously, create a custom configuration that wires provider-specific `ProviderConfig` instances (using qualifiers or factory methods) so that credentials are not accidentally reused.

---

## 📈 Observability & Operations

### Metrics & Status
`ProviderStatus` exposes availability, health, request counts, and latency figures for dashboards and alerts.
```21:128:ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/ProviderStatus.java
private boolean available;
private boolean healthy;
private long totalRequests;
private long successfulRequests;
private long failedRequests;
private double averageResponseTime;
private double successRate;
public boolean isOperational() {
    return available && healthy && successRate > 0.8;
}
```

Recommended operational hooks:
- Poll `AIProviderManager.getProviderStatistics()` on `/actuator` or custom endpoints to surface provider descriptions, totals, and success rates.
- Emit alerts when `successRate < 0.8` for more than 5 minutes or when `availableProviders` drops below 1.
- Track rate-limit headers returned by providers (OpenAI & Cohere expose quota information in responses).

### Logging Standards
- Keep provider loggers at `INFO` in production; enable `DEBUG` only during controlled investigations due to verbose payload dumps.
- Mask request/response bodies before persisting logs if they can contain regulated data.

### Health Monitoring
- Ensure `ai.service.health-checks.enabled=true` so `AIHealthIndicator` surfaces aggregated status.
- Include provider readiness checks in production readiness probes; block rollout when all providers are unhealthy.

---

## 🧪 Testing & Validation

| Stage | Command | Notes |
| --- | --- | --- |
| Unit & module tests | `mvn -pl ai-infrastructure-core test` | Stubs validate provider wiring without hitting external endpoints. |
| Integration suite | `mvn -pl ai-infrastructure-module/integration-tests test` | Exercises provider manager end to end. |
| Real API smoke | `./run-real-api-tests.sh` | Calls live OpenAI endpoints; watch costs. |

Testing guidelines:
- Run real API smoke before major releases or credential rotations.
- Use dedicated API keys for automated testing to avoid exhausting production quotas.
- For Anthropic/Cohere, craft environment-specific suites once credentials are available; avoid mocking production flows.

---

## 🚀 Deployment Checklist

- ✅ Secrets sourced from approved vaults (no plain-text values in configs).  
- ✅ Primary and fallback providers verified in staging (run smoke suite).  
- ✅ Rate limiting and retry policies aligned with provider quotas.  
- ✅ Observability dashboards monitoring `successRate`, `averageResponseTime`, and failure counts.  
- ✅ Runbooks updated with provider-specific escalation contacts.  
- ✅ Network egress rules allow outbound HTTPS to provider endpoints.

---

## 🛠️ Troubleshooting Guide

| Symptom | Likely Cause | Remediation |
| --- | --- | --- |
| `No AI providers available` exception | Missing/invalid API keys or providers disabled | Verify `ProviderConfig.isValid()` inputs and secrets injection. |
| Continuous fallback to ONNX | Primary provider rate-limited or unhealthy | Inspect `ProviderStatus.lastErrorMessage`, rotate keys, or lower load. |
| Anthropic embedding failure | Anthropic embeddings not supported | Route embeddings through OpenAI/ONNX; keep Anthropic enabled for generation only. |
| Slow responses (>3s) | Provider latency spike | Review `averageResponseTime`, adjust priority or throttle requests. |
| Runbook missing failover steps | Operator knowledge gap | Document fallback commands and notify on-call rotation. |

---

## 📚 Quick Reference

- **Primary entrypoints**: `com.ai.infrastructure.core.AICoreService`, `com.ai.infrastructure.provider.AIProviderManager`
- **Provider beans**: `OpenAIProvider`, `CohereProvider`, `AnthropicProvider`, `ONNXEmbeddingProvider`
- **Key configuration**: `ai.providers.*` (see `AIProviderConfig`)
- **Real API testing**: `ai-infrastructure-module/run-real-api-tests.sh`
- **Vector storage**: ONNX + Lucene by default; Pinecone available via `ai.vector-db.type=pinecone`

Maintain this guide with every provider addition (Amazon Bedrock, Azure OpenAI, etc.) so the production readiness envelope stays intact.

