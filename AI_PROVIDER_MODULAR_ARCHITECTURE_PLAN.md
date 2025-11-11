# AI Provider Modular Architecture Plan

## Executive Summary

This plan outlines a **modular provider architecture** where AI providers are separated into independent modules that auto-register with the core library via Spring Boot's auto-configuration and bean discovery mechanisms. This follows the Spring Boot starter pattern (similar to `spring-boot-starter-data-jpa`, `spring-boot-starter-data-mongodb`).

## Architecture Decision: Separate Modules Per Provider (Recommended)

**Recommendation: Option B - Separate module per provider**

### Why Separate Modules?

1. **True Modularity**: Customers only include providers they need
2. **Independent Versioning**: Each provider can have its own release cycle
3. **Smaller Artifacts**: Only ship what's needed (reduces JAR size)
4. **Dependency Isolation**: Provider-specific SDKs don't pollute core
5. **License Flexibility**: Some providers may have different licenses
6. **Follows Spring Boot Pattern**: Similar to Spring Data modules
7. **Better Testing**: Test providers independently

### Alternative: Single Provider Module

**Pros**: Easier dependency management, single artifact  
**Cons**: Forces all providers together, larger artifact, less flexible

---

## Module Structure

```
ai-infrastructure-module/
├── pom.xml (parent)
├── ai-infrastructure-core/              # Core library (no provider dependencies)
│   ├── src/main/java/com/ai/infrastructure/
│   │   ├── provider/
│   │   │   ├── AIProvider.java         # Interface (stays in core)
│   │   │   ├── AIProviderManager.java  # Auto-discovers providers
│   │   │   └── ProviderConfig.java     # Configuration model
│   │   └── config/
│   │       └── AIInfrastructureAutoConfiguration.java
│   └── pom.xml
│
├── ai-infrastructure-provider-openai/   # OpenAI provider module
│   ├── src/main/java/com/ai/infrastructure/provider/openai/
│   │   ├── OpenAIProvider.java
│   │   ├── OpenAIEmbeddingProvider.java
│   │   └── OpenAIAutoConfiguration.java
│   └── pom.xml
│
├── ai-infrastructure-provider-azure/    # Azure OpenAI provider module
│   ├── src/main/java/com/ai/infrastructure/provider/azure/
│   │   ├── AzureOpenAIProvider.java
│   │   ├── AzureOpenAIEmbeddingProvider.java
│   │   └── AzureOpenAIAutoConfiguration.java
│   └── pom.xml
│
├── ai-infrastructure-provider-anthropic/ # Anthropic Claude provider module
│   ├── src/main/java/com/ai/infrastructure/provider/anthropic/
│   │   ├── AnthropicProvider.java
│   │   ├── AnthropicEmbeddingProvider.java
│   │   └── AnthropicAutoConfiguration.java
│   └── pom.xml
│
├── ai-infrastructure-provider-cohere/   # Cohere provider module
│   └── ...
│
├── ai-infrastructure-provider-gemini/    # Google Gemini provider module
│   └── ...
│
├── ai-infrastructure-provider-mistral/   # Mistral provider module
│   └── ...
│
├── ai-infrastructure-onnx-starter/       # Existing ONNX module (keep as-is)
│   └── ...
│
└── integration-tests/                   # Integration tests
    └── ...
```

---

## Core Library Changes

### 1. Remove Provider Dependencies from Core

**File**: `ai-infrastructure-module/ai-infrastructure-core/pom.xml`

**Changes**:
- ❌ Remove OpenAI SDK dependency
- ✅ Keep only `AIProvider` interface and `AIProviderManager`
- ✅ Core should have **zero** provider-specific dependencies

```xml
<!-- REMOVE THIS -->
<dependency>
    <groupId>com.theokanning.openai-gpt3-java</groupId>
    <artifactId>service</artifactId>
</dependency>
```

### 2. Update AIProviderManager for Auto-Discovery

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/provider/AIProviderManager.java`

**Current**: Takes `List<AIProvider>` via constructor (already good!)

**Enhancement**: Add provider capability detection and filtering

```java
@Service
@RequiredArgsConstructor
public class AIProviderManager {
    
    private final List<AIProvider> providers;  // Auto-injected by Spring
    
    @PostConstruct
    public void initialize() {
        log.info("Auto-discovered {} AI providers: {}", 
            providers.size(), 
            providers.stream()
                .map(AIProvider::getProviderName)
                .collect(Collectors.joining(", "))
        );
        
        // Validate providers
        providers.forEach(provider -> {
            if (provider.isAvailable()) {
                log.info("Provider {} is available", provider.getProviderName());
            } else {
                log.warn("Provider {} is not available", provider.getProviderName());
            }
        });
    }
    
    // Existing methods remain the same...
}
```

### 3. Remove Hardcoded Provider Configuration

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`

**Current Issue**: Lines 385-402 create hardcoded OpenAI `ProviderConfig` bean

**Change**: Remove this bean - let provider modules create their own beans

```java
// REMOVE THIS ENTIRE METHOD (lines 385-402)
@Bean
@ConditionalOnMissingBean
public com.ai.infrastructure.provider.ProviderConfig providerConfig(AIProviderConfig aiProviderConfig) {
    return com.ai.infrastructure.provider.ProviderConfig.builder()
        .providerName("openai")
        .apiKey(aiProviderConfig.getOpenaiApiKey())
        // ... hardcoded OpenAI config
        .build();
}
```

**Replace with**: Provider modules will create their own `ProviderConfig` beans conditionally.

### 4. Update Configuration Properties

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

**Change**: Make provider-specific properties optional and organized

```java
@Data
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {
    
    // Common configuration
    private String activeProvider = "openai";  // Default, can be overridden
    private Boolean enableFallback = true;
    
    // OpenAI Configuration (optional - only if provider module included)
    private OpenAIConfig openai;
    
    // Azure OpenAI Configuration (optional)
    private AzureConfig azure;
    
    // Anthropic Configuration (optional)
    private AnthropicConfig anthropic;
    
    // ... other providers
    
    @Data
    public static class OpenAIConfig {
        private String apiKey;
        private String model = "gpt-4o-mini";
        private String embeddingModel = "text-embedding-3-small";
        private Integer maxTokens = 2000;
        private Double temperature = 0.3;
        private Integer timeout = 60;
    }
    
    @Data
    public static class AzureConfig {
        private String apiKey;
        private String endpoint;
        private String deploymentName;
        private String embeddingDeploymentName;
        private String apiVersion = "2024-02-15-preview";
    }
    
    // ... other provider configs
}
```

---

## Provider Module Template

### Structure

Each provider module follows this pattern:

```
ai-infrastructure-provider-{name}/
├── pom.xml
└── src/main/java/com/ai/infrastructure/provider/{name}/
    ├── {Name}Provider.java              # Implements AIProvider
    ├── {Name}EmbeddingProvider.java     # Implements EmbeddingProvider (if supported)
    └── {Name}AutoConfiguration.java     # Spring Boot auto-configuration
```

### Example: OpenAI Provider Module

**File**: `ai-infrastructure-module/ai-infrastructure-provider-openai/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>ai-infrastructure-provider-openai</artifactId>
    <packaging>jar</packaging>

    <name>AI Infrastructure OpenAI Provider</name>
    <description>OpenAI provider implementation for AI Infrastructure</description>

    <dependencies>
        <!-- Core dependency -->
        <dependency>
            <groupId>com.ai.infrastructure</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
        </dependency>

        <!-- OpenAI SDK -->
        <dependency>
            <groupId>com.theokanning.openai-gpt3-java</groupId>
            <artifactId>service</artifactId>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
</project>
```

**File**: `ai-infrastructure-module/ai-infrastructure-provider-openai/src/main/java/com/ai/infrastructure/provider/openai/OpenAIAutoConfiguration.java`

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
 * This configuration is automatically loaded when:
 * 1. OpenAI SDK is on classpath (ConditionalOnClass)
 * 2. ai.providers.openai.enabled=true (or not set, defaults to true)
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

    @Bean
    public ProviderConfig openAIProviderConfig(AIProviderConfig config) {
        AIProviderConfig.OpenAIConfig openaiConfig = config.getOpenai();
        if (openaiConfig == null) {
            log.warn("OpenAI configuration not found. Provider will be unavailable.");
            return null;
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
            .priority(100)  // High priority
            .build();
    }

    @Bean
    public AIProvider openAIProvider(ProviderConfig config) {
        if (config == null || !config.isValid()) {
            log.warn("OpenAI provider configuration invalid. Provider will be unavailable.");
            return new UnavailableOpenAIProvider();
        }
        log.info("Creating OpenAI provider");
        return new OpenAIProvider(config);
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "ai.providers.embedding",
        name = "provider",
        havingValue = "openai",
        matchIfMissing = false
    )
    public EmbeddingProvider openAIEmbeddingProvider(ProviderConfig config) {
        if (config == null || !config.isValid()) {
            log.warn("OpenAI embedding provider configuration invalid.");
            return null;
        }
        log.info("Creating OpenAI embedding provider");
        return new OpenAIEmbeddingProvider(config);
    }
}
```

**File**: `ai-infrastructure-module/ai-infrastructure-provider-openai/src/main/java/com/ai/infrastructure/provider/openai/OpenAIProvider.java`

```java
package com.ai.infrastructure.provider.openai;

import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.ProviderConfig;
// ... existing implementation moves here from core
```

**Key Points**:
- Module only loads if OpenAI SDK is on classpath (`@ConditionalOnClass`)
- Module only loads if `ai.providers.openai.enabled=true`
- Creates `ProviderConfig` bean from `AIProviderConfig`
- Creates `AIProvider` bean that auto-registers with `AIProviderManager`
- Creates `EmbeddingProvider` bean conditionally

---

## Vector Database Modules (Similar Pattern)

### Module Structure

```
ai-infrastructure-vector-{name}/
├── pom.xml
└── src/main/java/com/ai/infrastructure/vector/{name}/
    ├── {Name}VectorDatabaseService.java
    └── {Name}VectorDatabaseAutoConfiguration.java
```

### Example: Weaviate Vector Database Module

**File**: `ai-infrastructure-module/ai-infrastructure-vector-weaviate/pom.xml`

```xml
<dependencies>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    <!-- Weaviate Java client -->
    <dependency>
        <groupId>io.weaviate</groupId>
        <artifactId>client</artifactId>
        <version>4.0.0</version>
    </dependency>
</dependencies>
```

**File**: `ai-infrastructure-module/ai-infrastructure-vector-weaviate/src/main/java/com/ai/infrastructure/vector/weaviate/WeaviateVectorDatabaseAutoConfiguration.java`

```java
@Configuration
@ConditionalOnClass(name = "io.weaviate.client.WeaviateClient")
@ConditionalOnProperty(
    prefix = "ai.vector-db",
    name = "type",
    havingValue = "weaviate"
)
public class WeaviateVectorDatabaseAutoConfiguration {
    
    @Bean
    @Primary
    public VectorDatabaseService weaviateVectorDatabaseService(
            AIProviderConfig config,
            AISearchableEntityRepository repository,
            AIEntityConfigurationLoader loader) {
        WeaviateVectorDatabaseService delegate = new WeaviateVectorDatabaseService(config);
        return new SearchableEntityVectorDatabaseService(delegate, repository, loader);
    }
}
```

---

## Migration Steps

### Phase 1: Refactor Core (Week 1)

1. **Remove OpenAI dependency from core**
   - Remove from `ai-infrastructure-core/pom.xml`
   - Move `OpenAIProvider` to new module
   - Move `OpenAIEmbeddingProvider` to new module

2. **Update AIProviderManager**
   - Enhance logging for auto-discovery
   - Add provider capability detection

3. **Update Configuration**
   - Refactor `AIProviderConfig` to nested structure
   - Remove hardcoded provider beans from `AIInfrastructureAutoConfiguration`

### Phase 2: Create OpenAI Provider Module (Week 1-2)

1. **Create module structure**
   ```
   ai-infrastructure-provider-openai/
   ├── pom.xml
   └── src/main/java/com/ai/infrastructure/provider/openai/
   ```

2. **Move OpenAI implementations**
   - Move `OpenAIProvider.java` from core
   - Move `OpenAIEmbeddingProvider.java` from core
   - Create `OpenAIAutoConfiguration.java`

3. **Update parent POM**
   - Add module to parent `pom.xml`

4. **Test**
   - Verify auto-discovery works
   - Verify existing tests still pass

### Phase 3: Create Additional Provider Modules (Week 2-4)

1. **Azure OpenAI Module**
   - Create `ai-infrastructure-provider-azure`
   - Implement `AzureOpenAIProvider`
   - Add Azure SDK dependency

2. **Anthropic Module**
   - Create `ai-infrastructure-provider-anthropic`
   - Implement `AnthropicProvider`
   - Add Anthropic SDK dependency

3. **Cohere Module**
   - Create `ai-infrastructure-provider-cohere`
   - Implement `CohereProvider`

4. **Gemini Module**
   - Create `ai-infrastructure-provider-gemini`
   - Implement `GeminiProvider`

### Phase 4: Vector Database Modules (Week 3-5)

1. **Weaviate Module**
   - Create `ai-infrastructure-vector-weaviate`
   - Implement `WeaviateVectorDatabaseService`

2. **Qdrant Module**
   - Create `ai-infrastructure-vector-qdrant`
   - Implement `QdrantVectorDatabaseService`

3. **Milvus Module**
   - Create `ai-infrastructure-vector-milvus`
   - Implement `MilvusVectorDatabaseService`

### Phase 5: Update Integration Tests (Week 5-6)

1. **Create provider-specific test profiles**
   - `application-test-openai.yml`
   - `application-test-azure.yml`
   - `application-test-anthropic.yml`

2. **Update test dependencies**
   - Add provider modules to test POM
   - Update test configurations

3. **Parameterize tests**
   - Use JUnit parameterized tests
   - Test with different providers

---

## Configuration Examples

### Single Provider (OpenAI)

**application.yml**:
```yaml
ai:
  enabled: true
  providers:
    active-provider: openai
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
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
        <artifactId>ai-infrastructure-provider-openai</artifactId>
    </dependency>
</dependencies>
```

### Multiple Providers with Fallback

**application.yml**:
```yaml
ai:
  enabled: true
  providers:
    active-provider: openai
    enable-fallback: true
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      priority: 100
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com
      deployment-name: gpt-4
      priority: 90
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      priority: 80
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
        <artifactId>ai-infrastructure-provider-azure</artifactId>
    </dependency>
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-provider-anthropic</artifactId>
    </dependency>
</dependencies>
```

### Vector Database Selection

**application.yml**:
```yaml
ai:
  vector-db:
    type: weaviate  # or lucene, pinecone, qdrant, milvus
    weaviate:
      url: http://localhost:8080
      api-key: ${WEAVIATE_API_KEY}
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
        <artifactId>ai-infrastructure-vector-weaviate</artifactId>
    </dependency>
</dependencies>
```

---

## Benefits of This Architecture

### 1. **Separation of Concerns**
- Core library doesn't know about specific providers
- Providers are independent modules
- Easy to add/remove providers

### 2. **Dependency Management**
- Only include providers you need
- Smaller JAR sizes
- No dependency conflicts

### 3. **Versioning**
- Each provider can have independent version
- Update providers without touching core
- Backward compatibility per provider

### 4. **Testing**
- Test providers independently
- Mock providers easily
- Test core without provider dependencies

### 5. **Extensibility**
- Customers can create custom provider modules
- Follow same pattern as official providers
- Auto-discovery works automatically

### 6. **Spring Boot Integration**
- Uses standard Spring Boot patterns
- Auto-configuration works out of the box
- Configuration properties work seamlessly

---

## Code Changes Summary

### Files to Modify

1. **Core Module**:
   - `ai-infrastructure-core/pom.xml` - Remove OpenAI dependency
   - `AIInfrastructureAutoConfiguration.java` - Remove hardcoded provider beans
   - `AIProviderConfig.java` - Refactor to nested structure
   - `AIProviderManager.java` - Enhance auto-discovery logging

2. **New Provider Modules** (create):
   - `ai-infrastructure-provider-openai/` - Move OpenAI implementation
   - `ai-infrastructure-provider-azure/` - New Azure implementation
   - `ai-infrastructure-provider-anthropic/` - New Anthropic implementation
   - `ai-infrastructure-provider-cohere/` - New Cohere implementation
   - `ai-infrastructure-provider-gemini/` - New Gemini implementation

3. **New Vector Modules** (create):
   - `ai-infrastructure-vector-weaviate/` - Weaviate implementation
   - `ai-infrastructure-vector-qdrant/` - Qdrant implementation
   - `ai-infrastructure-vector-milvus/` - Milvus implementation

4. **Parent POM**:
   - `ai-infrastructure-module/pom.xml` - Add new modules

5. **Integration Tests**:
   - Update test POMs to include provider modules
   - Create provider-specific test profiles
   - Parameterize tests for multiple providers

---

## Testing Strategy

### Unit Tests
- Test each provider module independently
- Mock external API calls
- Test auto-configuration conditions

### Integration Tests
- Test provider auto-discovery
- Test provider selection and fallback
- Test with multiple providers enabled
- Test vector database switching

### Real API Tests
- Create provider-specific test profiles
- Test with real API credentials (via env vars)
- Test fallback scenarios

---

## Migration Checklist

- [ ] Phase 1: Refactor core module
  - [ ] Remove OpenAI dependency from core
  - [ ] Update AIProviderManager
  - [ ] Refactor AIProviderConfig
  - [ ] Remove hardcoded provider beans

- [ ] Phase 2: Create OpenAI provider module
  - [ ] Create module structure
  - [ ] Move OpenAI implementations
  - [ ] Create auto-configuration
  - [ ] Update parent POM
  - [ ] Test auto-discovery

- [ ] Phase 3: Create additional provider modules
  - [ ] Azure OpenAI module
  - [ ] Anthropic module
  - [ ] Cohere module
  - [ ] Gemini module

- [ ] Phase 4: Create vector database modules
  - [ ] Weaviate module
  - [ ] Qdrant module
  - [ ] Milvus module

- [ ] Phase 5: Update integration tests
  - [ ] Create provider-specific profiles
  - [ ] Parameterize tests
  - [ ] Update test dependencies

- [ ] Phase 6: Documentation
  - [ ] Update README with module structure
  - [ ] Create provider-specific guides
  - [ ] Update configuration examples

---

## Next Steps

1. **Review and approve** this architecture plan
2. **Create GitHub issues** for each phase
3. **Start with Phase 1** - refactor core module
4. **Create first provider module** (OpenAI) as template
5. **Iterate** on additional providers

---

## Questions & Considerations

1. **Naming Convention**: 
   - `ai-infrastructure-provider-{name}` vs `ai-infrastructure-{name}-provider`?
   - Recommendation: `ai-infrastructure-provider-{name}` (consistent with Spring Boot)

2. **Versioning**:
   - Should provider modules version independently?
   - Recommendation: Start with same version, allow independent versioning later

3. **Backward Compatibility**:
   - How to handle existing configurations?
   - Recommendation: Support both old and new config format during migration

4. **Default Provider**:
   - Should core have a default provider?
   - Recommendation: No default - require explicit provider module inclusion

5. **Embedding Providers**:
   - Should embedding providers be in same module as LLM providers?
   - Recommendation: Yes - keep together (OpenAI has both LLM and embeddings)

---

## Conclusion

This modular architecture provides:
- ✅ True separation of concerns
- ✅ Flexible dependency management
- ✅ Easy extensibility
- ✅ Spring Boot best practices
- ✅ Better testing capabilities
- ✅ Smaller artifacts

The approach follows established Spring Boot patterns and will make the library more maintainable and extensible.
