# AI Provider Modular Architecture Plan
## Independent LLM and Embedding Provider Selection

## Executive Summary

This plan outlines a **modular provider architecture** where:
1. **Each AI provider module includes its embedding provider** (if the provider supports embeddings)
2. **LLM and embedding providers can be selected independently** - users can mix and match
3. **Auto-discovery via Spring Boot** - providers register themselves automatically
4. **Follows Spring Boot starter pattern** - similar to Spring Data modules

### Key Design Principle

**"Each provider module is self-contained, but selection is independent"**

- OpenAI module → provides `OpenAIProvider` (LLM) + `OpenAIEmbeddingProvider` (embeddings)
- Azure module → provides `AzureOpenAIProvider` (LLM) + `AzureOpenAIEmbeddingProvider` (embeddings)
- ONNX module → provides only `ONNXEmbeddingProvider` (no LLM)
- **User can configure**: LLM=OpenAI, Embedding=ONNX ✅

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Core Library                              │
│  ┌──────────────────┐      ┌──────────────────────────┐     │
│  │ AIProvider       │      │ EmbeddingProvider        │     │
│  │ Interface        │      │ Interface                │     │
│  └──────────────────┘      └──────────────────────────┘     │
│           ▲                           ▲                       │
│           │                           │                       │
│  ┌──────────────────┐      ┌──────────────────────────┐     │
│  │ AIProviderManager│      │ AIEmbeddingService       │     │
│  │ (auto-discovers) │      │ (selects independently)  │     │
│  └──────────────────┘      └──────────────────────────┘     │
└─────────────────────────────────────────────────────────────┘
           ▲                           ▲
           │                           │
    ┌──────┴──────┐           ┌──────┴──────┐
    │             │           │             │
┌───┴────┐  ┌─────┴───┐  ┌───┴────┐  ┌────┴────┐
│ OpenAI │  │ Azure   │  │ ONNX   │  │ Cohere  │
│ Module │  │ Module  │  │ Module │  │ Module │
├────────┤  ├─────────┤  ├────────┤  ├────────┤
│ LLM ✅ │  │ LLM ✅  │  │ LLM ❌ │  │ LLM ✅ │
│ Emb ✅ │  │ Emb ✅  │  │ Emb ✅ │  │ Emb ✅ │
└────────┘  └─────────┘  └────────┘  └────────┘
```

---

## Module Structure

```
ai-infrastructure-module/
├── pom.xml (parent)
│
├── ai-infrastructure-core/              # Core library (no provider dependencies)
│   ├── src/main/java/com/ai/infrastructure/
│   │   ├── provider/
│   │   │   ├── AIProvider.java         # Interface (stays in core)
│   │   │   ├── AIProviderManager.java  # Auto-discovers LLM providers
│   │   │   └── ProviderConfig.java     # Configuration model
│   │   ├── embedding/
│   │   │   └── EmbeddingProvider.java  # Interface (stays in core)
│   │   └── config/
│   │       └── AIInfrastructureAutoConfiguration.java
│   └── pom.xml
│
├── providers/
│   ├── ai-infrastructure-provider-openai/   # OpenAI provider module
│   │   ├── src/main/java/com/ai/infrastructure/provider/openai/
│   │   │   ├── OpenAIProvider.java              # Implements AIProvider
│   │   │   ├── OpenAIEmbeddingProvider.java     # Implements EmbeddingProvider
│   │   │   └── OpenAIAutoConfiguration.java     # Creates both beans
│   │   └── pom.xml
│   │
│   ├── ai-infrastructure-provider-azure/    # Azure OpenAI provider module
│   │   ├── src/main/java/com/ai/infrastructure/provider/azure/
│   │   │   ├── AzureOpenAIProvider.java          # Implements AIProvider
│   │   │   ├── AzureOpenAIEmbeddingProvider.java # Implements EmbeddingProvider
│   │   │   └── AzureOpenAIAutoConfiguration.java # Creates both beans
│   │   └── pom.xml
│   │
│   ├── ai-infrastructure-provider-anthropic/ # Anthropic Claude provider module
│   │   ├── src/main/java/com/ai/infrastructure/provider/anthropic/
│   │   │   ├── AnthropicProvider.java           # Implements AIProvider
│   │   │   ├── AnthropicEmbeddingProvider.java  # Implements EmbeddingProvider (if supported)
│   │   │   └── AnthropicAutoConfiguration.java  # Creates beans
│   │   └── pom.xml
│   └── ...
│
├── ai-infrastructure-onnx-starter/       # ONNX module (embeddings only)
│   ├── src/main/java/com/ai/infrastructure/embedding/onnx/
│   │   ├── ONNXEmbeddingProvider.java       # Implements EmbeddingProvider
│   │   └── ONNXAutoConfiguration.java      # Creates only embedding bean
│   └── pom.xml
│
└── integration-tests/
    └── ...
```

---

## Configuration Model

### Independent Provider Selection

**application.yml**:
```yaml
ai:
  enabled: true
  
  providers:
    # LLM Provider Selection (for content generation)
    llm-provider: openai  # Options: openai, azure, anthropic, cohere, gemini
    
    # Embedding Provider Selection (for vector embeddings)
    embedding-provider: onnx  # Options: onnx, openai, azure, cohere, rest
    
    # Enable fallback between providers
    enable-fallback: true
    
    # Provider-specific configurations
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      embedding-model: text-embedding-3-small
      priority: 100
    
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com
      deployment-name: gpt-4
      embedding-deployment-name: text-embedding-ada-002
      priority: 90
    
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-opus-20240229
      # Note: Anthropic may not support embeddings - module won't create EmbeddingProvider bean
    
    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
      tokenizer-path: classpath:/models/embeddings/tokenizer.json
```

### Example: Mix and Match

**Scenario 1**: Use OpenAI for LLM, ONNX for embeddings (cost optimization)
```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
    onnx:
      enabled: true
```

**Scenario 2**: Use Azure for LLM, Azure for embeddings (enterprise)
```yaml
ai:
  providers:
    llm-provider: azure
    embedding-provider: azure
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com
```

**Scenario 3**: Use Anthropic for LLM, OpenAI for embeddings (best of both)
```yaml
ai:
  providers:
    llm-provider: anthropic
    embedding-provider: openai
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
```

---

## Core Library Changes

### 1. Update AIProviderConfig

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

```java
@Data
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {
    
    // LLM Provider Selection (for content generation)
    private String llmProvider = "openai";  // Default
    
    // Embedding Provider Selection (for vector embeddings)
    private String embeddingProvider = "onnx";  // Default
    
    // Enable fallback between providers
    private Boolean enableFallback = true;
    
    // Provider-specific configurations
    private OpenAIConfig openai;
    private AzureConfig azure;
    private AnthropicConfig anthropic;
    private ONNXConfig onnx;
    private RestConfig rest;
    private CohereConfig cohere;
    private WeaviateConfig weaviate;
    private QdrantConfig qdrant;
    private MilvusConfig milvus;
    
    // Legacy support (deprecated)
    @Deprecated
    public String getEmbeddingProvider() {
        return embeddingProvider != null ? embeddingProvider : "onnx";
    }
    
    @Data
    public static class OpenAIConfig {
        private Boolean enabled = true;
        private String apiKey;
        private String model = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";
        private Integer maxTokens = 2000;
        private Double temperature = 0.3;
        private Integer timeout = 60;
        private Integer priority = 100;
    }
    
    @Data
    public static class AzureConfig {
        private Boolean enabled = false;
        private String apiKey;
        private String endpoint;
        private String deploymentName;
        private String embeddingDeploymentName;
        private String apiVersion = "2024-02-15-preview";
        private Integer priority = 90;
    }
    
    @Data
    public static class AnthropicConfig {
        private Boolean enabled = false;
        private String apiKey;
        private String model = "claude-3-opus-20240229";
        private Integer maxTokens = 4096;
        private Double temperature = 0.3;
        private Integer timeout = 60;
        private Integer priority = 80;
        // Note: Anthropic may not support embeddings
    }
    
    @Data
    public static class ONNXConfig {
        private Boolean enabled = true;
        private String modelPath = "classpath:/models/embeddings/all-MiniLM-L6-v2.onnx";
        private String tokenizerPath = "classpath:/models/embeddings/tokenizer.json";
        private Integer maxSequenceLength = 512;
        private Boolean useGpu = false;
    }

    @Data
    public static class WeaviateConfig {
        private Boolean enabled = false;
        private String scheme = "https";
        private String host;
        private Integer port = 443;
        private String apiKey;
        private Integer timeout = 30;
        private Boolean consistencyLevelStrong = false;
    }

    @Data
    public static class QdrantConfig {
        private Boolean enabled = false;
        private String host = "localhost";
        private Integer port = 6333;
        private String apiKey;
        private Integer timeout = 30;
        private Integer grpcPort = 6334;
        private Boolean preferGrpc = false;
    }

    @Data
    public static class MilvusConfig {
        private Boolean enabled = false;
        private String host = "localhost";
        private Integer port = 19530;
        private String username = "";
        private String password = "";
        private String databaseName = "default";
        private Integer timeout = 30;
        private Boolean secure = false;
    }
}
```

### 2. Update AIProviderManager for LLM Selection

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/AIProviderManager.java`

```java
@Service
@RequiredArgsConstructor
public class AIProviderManager {
    
    private final List<AIProvider> providers;  // Auto-injected by Spring
    private final AIProviderConfig config;
    
    @PostConstruct
    public void initialize() {
        log.info("Auto-discovered {} LLM providers: {}", 
            providers.size(), 
            providers.stream()
                .map(AIProvider::getProviderName)
                .collect(Collectors.joining(", "))
        );
        
        // Validate selected provider
        String selectedProvider = config.getLlmProvider();
        AIProvider provider = getProvider(selectedProvider);
        if (provider != null && provider.isAvailable()) {
            log.info("Selected LLM provider: {} (priority: {})", 
                selectedProvider, 
                provider.getConfig().getPriority());
        } else {
            log.warn("Selected LLM provider '{}' is not available. Available providers: {}", 
                selectedProvider,
                providers.stream()
                    .map(AIProvider::getProviderName)
                    .collect(Collectors.joining(", "))
            );
        }
    }
    
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        String providerName = config.getLlmProvider();
        AIProvider selectedProvider = getProvider(providerName);
        
        if (selectedProvider == null || !selectedProvider.isAvailable()) {
            if (config.getEnableFallback()) {
                log.warn("Provider {} not available, trying fallback", providerName);
                selectedProvider = selectFallbackProvider();
            }
            
            if (selectedProvider == null) {
                throw new RuntimeException("No LLM provider available. Requested: " + providerName);
            }
        }
        
        try {
            return selectedProvider.generateContent(request);
        } catch (Exception e) {
            if (config.getEnableFallback()) {
                return tryFallbackProviders(request, selectedProvider);
            }
            throw e;
        }
    }
    
    // ... rest of implementation
}
```

### 3. Update AIEmbeddingService for Independent Selection

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`

```java
@Bean
public AIEmbeddingService aiEmbeddingService(
        AIProviderConfig config,
        List<EmbeddingProvider> embeddingProviders,  // Auto-discovered from all modules
        ObjectProvider<CacheManager> cacheManagerProvider,
        @Qualifier("onnxFallbackEmbeddingProvider") @Nullable EmbeddingProvider fallbackEmbeddingProvider) {
    
    // Select embedding provider independently from LLM provider
    String selectedEmbeddingProvider = config.getEmbeddingProvider();
    
    EmbeddingProvider selectedProvider = embeddingProviders.stream()
        .filter(provider -> provider != null && provider.getProviderName() != null)
        .filter(provider -> provider.getProviderName().equalsIgnoreCase(selectedEmbeddingProvider))
        .findFirst()
        .orElseGet(() -> {
            log.warn("Requested embedding provider '{}' not found. Available: {}. Using first available.",
                selectedEmbeddingProvider,
                embeddingProviders.stream()
                    .map(EmbeddingProvider::getProviderName)
                    .collect(Collectors.joining(", "))
            );
            return embeddingProviders.isEmpty() ? null : embeddingProviders.get(0);
        });

    String providerName = selectedProvider != null ? selectedProvider.getProviderName() : "null";
    log.info("Creating AIEmbeddingService with embedding provider: {} (independent from LLM provider: {})", 
        providerName, 
        config.getLlmProvider());
    
    CacheManager cacheManager = cacheManagerProvider.getIfAvailable(NoOpCacheManager::new);
    return new AIEmbeddingService(config, selectedProvider, cacheManager, fallbackEmbeddingProvider);
}
```

---

## Provider Module Template

### OpenAI Provider Module Example

**File**: `ai-infrastructure-module/providers/ai-infrastructure-provider-openai/src/main/java/com/ai/infrastructure/provider/openai/OpenAIAutoConfiguration.java`

```java
package com.ai.infrastructure.provider.openai;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.ProviderConfig;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for OpenAI provider
 * 
 * Creates BOTH:
 * 1. OpenAIProvider (for LLM/content generation)
 * 2. OpenAIEmbeddingProvider (for embeddings)
 * 
 * Both beans are independently selectable via configuration.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "com.theokanning.openai-gpt3-java.service.OpenAiService")
@ConditionalOnProperty(
    prefix = "ai.providers.openai",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(AIProviderConfig.class)
public class OpenAIAutoConfiguration {

    /**
     * Create ProviderConfig bean for OpenAI
     * Used by both LLM and embedding providers
     */
    @Bean("openAIProviderConfig")
    public ProviderConfig openAIProviderConfig(AIProviderConfig config) {
        AIProviderConfig.OpenAIConfig openaiConfig = config.getOpenai();
        if (openaiConfig == null || !Boolean.TRUE.equals(openaiConfig.getEnabled())) {
            log.debug("OpenAI configuration disabled or not found");
            return null;
        }

        if (openaiConfig.getApiKey() == null || openaiConfig.getApiKey().trim().isEmpty()) {
            log.warn("OpenAI API key not configured. Provider will be unavailable.");
            return ProviderConfig.builder()
                .providerName("openai")
                .enabled(false)
                .build();
        }

        return ProviderConfig.builder()
            .providerName("openai")
            .apiKey(openaiConfig.getApiKey())
            .baseUrl("https://api.openai.com/v1")
            .defaultModel(openaiConfig.getModel())
            .defaultEmbeddingModel(openaiConfig.getEmbeddingModel())
            .maxTokens(openaiConfig.getMaxTokens())
            .temperature(openaiConfig.getTemperature())
            .timeoutSeconds(openaiConfig.getTimeout())
            .maxRetries(3)
            .retryDelayMs(1000L)
            .rateLimitPerMinute(60)
            .rateLimitPerDay(10000)
            .enabled(true)
            .priority(openaiConfig.getPriority() != null ? openaiConfig.getPriority() : 100)
            .build();
    }

    /**
     * Create OpenAIProvider bean (for LLM/content generation)
     * This bean is auto-discovered by AIProviderManager
     */
    @Bean
    public AIProvider openAIProvider(@Qualifier("openAIProviderConfig") ProviderConfig config) {
        if (config == null || !config.isValid() || !config.isEnabled()) {
            log.debug("OpenAI provider not configured or disabled");
            return new UnavailableOpenAIProvider();
        }
        log.info("Creating OpenAI LLM provider");
        return new OpenAIProvider(config);
    }

    /**
     * Create OpenAIEmbeddingProvider bean (for embeddings)
     * This bean is auto-discovered by AIEmbeddingService
     * Can be selected independently from LLM provider
     */
    @Bean
    public EmbeddingProvider openAIEmbeddingProvider(@Qualifier("openAIProviderConfig") ProviderConfig config) {
        if (config == null || !config.isValid() || !config.isEnabled()) {
            log.debug("OpenAI embedding provider not configured or disabled");
            return null;  // Return null instead of unavailable bean - allows other providers to be selected
        }
        log.info("Creating OpenAI embedding provider");
        return new OpenAIEmbeddingProvider(config);
    }
}
```

### ONNX Module Example (Embeddings Only)

**File**: `ai-infrastructure-module/ai-infrastructure-onnx-starter/src/main/java/com/ai/infrastructure/embedding/onnx/ONNXAutoConfiguration.java`

```java
package com.ai.infrastructure.embedding.onnx;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for ONNX embedding provider
 * 
 * Note: ONNX does NOT provide LLM capabilities, only embeddings
 * This module creates ONLY EmbeddingProvider bean
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "ai.onnxruntime.OnnxTensor")
@ConditionalOnProperty(
    prefix = "ai.providers.onnx",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(AIProviderConfig.class)
public class ONNXAutoConfiguration {

    /**
     * Create ONNXEmbeddingProvider bean
     * This is the ONLY bean created by this module (no LLM provider)
     */
    @Bean
    public EmbeddingProvider onnxEmbeddingProvider(AIProviderConfig config) {
        AIProviderConfig.ONNXConfig onnxConfig = config.getOnnx();
        if (onnxConfig == null || !Boolean.TRUE.equals(onnxConfig.getEnabled())) {
            log.debug("ONNX configuration disabled or not found");
            return null;
        }
        
        log.info("Creating ONNX embedding provider");
        ONNXEmbeddingProvider provider = new ONNXEmbeddingProvider(config);
        
        if (!provider.isAvailable()) {
            log.warn("ONNX embedding provider is not available. Model file may be missing.");
            return null;
        }
        
        return provider;
    }
}
```

### Anthropic Module Example (LLM Only, No Embeddings)

**File**: `ai-infrastructure-module/providers/ai-infrastructure-provider-anthropic/src/main/java/com/ai/infrastructure/provider/anthropic/AnthropicAutoConfiguration.java`

```java
package com.ai.infrastructure.provider.anthropic;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.ProviderConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Anthropic Claude provider
 * 
 * Note: Anthropic does NOT support embeddings API
 * This module creates ONLY AIProvider bean (no EmbeddingProvider)
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "com.anthropic.sdk.Anthropic")
@ConditionalOnProperty(
    prefix = "ai.providers.anthropic",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@EnableConfigurationProperties(AIProviderConfig.class)
public class AnthropicAutoConfiguration {

    @Bean("anthropicProviderConfig")
    public ProviderConfig anthropicProviderConfig(AIProviderConfig config) {
        AIProviderConfig.AnthropicConfig anthropicConfig = config.getAnthropic();
        if (anthropicConfig == null || !Boolean.TRUE.equals(anthropicConfig.getEnabled())) {
            return null;
        }
        
        return ProviderConfig.builder()
            .providerName("anthropic")
            .apiKey(anthropicConfig.getApiKey())
            .baseUrl("https://api.anthropic.com")
            .defaultModel(anthropicConfig.getModel())
            .maxTokens(anthropicConfig.getMaxTokens())
            .temperature(anthropicConfig.getTemperature())
            .timeoutSeconds(anthropicConfig.getTimeout())
            .priority(anthropicConfig.getPriority() != null ? anthropicConfig.getPriority() : 80)
            .enabled(true)
            .build();
    }

    /**
     * Create AnthropicProvider bean (for LLM only)
     * Note: Anthropic does NOT provide embeddings
     */
    @Bean
    public AIProvider anthropicProvider(@Qualifier("anthropicProviderConfig") ProviderConfig config) {
        if (config == null || !config.isValid() || !config.isEnabled()) {
            log.debug("Anthropic provider not configured or disabled");
            return new UnavailableAnthropicProvider();
        }
        log.info("Creating Anthropic LLM provider (no embeddings support)");
        return new AnthropicProvider(config);
    }

    // NO EmbeddingProvider bean - Anthropic doesn't support embeddings
}
```

---

## Usage Examples

### Example 1: Cost Optimization
**Use OpenAI for LLM, ONNX for embeddings (free local embeddings)**

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
    onnx:
      enabled: true
```

**pom.xml**:
```xml
<dependencies>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-provider-openai</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-onnx-starter</artifactId>
    </dependency>
</dependencies>
```

### Example 2: Enterprise Azure
**Use Azure for both LLM and embeddings**

```yaml
ai:
  providers:
    llm-provider: azure
    embedding-provider: azure
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com
```

**pom.xml**:
```xml
<dependencies>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-provider-azure</artifactId>
    </dependency>
</dependencies>
```

### Example 3: Best of Both Worlds
**Use Anthropic for LLM (best quality), OpenAI for embeddings (best compatibility)**

```yaml
ai:
  providers:
    llm-provider: anthropic
    embedding-provider: openai
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      # Only embedding model needed
      embedding-model: text-embedding-3-small
```

**pom.xml**:
```xml
<dependencies>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-provider-anthropic</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-provider-openai</artifactId>
    </dependency>
</dependencies>
```

---

## Benefits of This Architecture

### 1. **Flexibility**
- Mix and match providers based on needs
- Cost optimization (free embeddings + paid LLM)
- Best quality per use case

### 2. **Modularity**
- Each provider module is self-contained
- Include only what you need
- Independent versioning

### 3. **Clear Separation**
- LLM selection independent from embedding selection
- Configuration makes intent clear
- Easy to understand and maintain

### 4. **Backward Compatibility**
- Existing configurations still work
- Gradual migration path
- No breaking changes

---

## Migration Checklist

- [ ] Phase 1: Refactor Core
  - [ ] Update `AIProviderConfig` with `llmProvider` and `embeddingProvider`
  - [ ] Update `AIProviderManager` to select LLM provider
  - [x] Update `AIEmbeddingService` to select embedding provider independently
  - [x] Remove hardcoded provider dependencies from core

- [x] Phase 2: Create OpenAI Provider Module
  - [x] Create module structure
  - [x] Move `OpenAIProvider` from core
  - [x] Move `OpenAIEmbeddingProvider` from core
  - [x] Create `OpenAIAutoConfiguration` (creates both beans)
  - [ ] Test independent selection

- [ ] Phase 3: Create Additional Provider Modules
  - [x] REST module (Embeddings only)
  - [x] Azure module (LLM + Embeddings)
  - [x] Anthropic module (LLM only)
  - [x] Cohere module (LLM + Embeddings)
  - [x] Update ONNX module (Embeddings only)

- [ ] Phase 4: Update Integration Tests
  - [ ] Test LLM=OpenAI, Embedding=ONNX
  - [ ] Test LLM=Azure, Embedding=Azure
  - [ ] Test LLM=Anthropic, Embedding=OpenAI
  - [ ] Test fallback scenarios

- [ ] Phase 5: Documentation
  - [ ] Update README with mix-and-match examples
  - [ ] Create provider selection guide
  - [ ] Document cost optimization strategies

---

## Summary

This architecture provides:
- ✅ **Each provider module includes its embedding provider** (if supported)
- ✅ **Independent selection** - LLM and embedding providers can be different
- ✅ **Auto-discovery** - Spring Boot discovers providers automatically
- ✅ **Flexibility** - Mix and match based on needs
- ✅ **Modularity** - Include only what you need
- ✅ **Clear configuration** - Intent is explicit in YAML

The key insight: **Provider modules are self-contained, but selection is independent.**
