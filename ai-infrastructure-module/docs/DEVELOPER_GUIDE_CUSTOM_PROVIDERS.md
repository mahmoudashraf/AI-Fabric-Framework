# Developer Guide: Creating Custom Provider Modules

## Overview

This guide explains how to create a custom AI provider module that integrates with the AI Infrastructure library using Spring Boot auto-configuration.

## Module Structure

```
ai-infrastructure-provider-{your-provider}/
├── pom.xml
└── src/main/java/com/ai/infrastructure/provider/{your-provider}/
    ├── {YourProvider}Provider.java              # Implements AIProvider
    ├── {YourProvider}EmbeddingProvider.java     # Implements EmbeddingProvider (optional)
    └── {YourProvider}AutoConfiguration.java     # Spring Boot auto-configuration
```

## Step 1: Create Module POM

**File**: `pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>ai-infrastructure-provider-{your-provider}</artifactId>
    <packaging>jar</packaging>

    <name>AI Infrastructure {Your Provider} Provider</name>
    <description>{Your Provider} provider implementation for AI Infrastructure</description>

    <dependencies>
        <!-- Core dependency -->
        <dependency>
            <groupId>com.ai.infrastructure</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
        </dependency>

        <!-- Your provider SDK -->
        <dependency>
            <groupId>com.your.provider</groupId>
            <artifactId>sdk</artifactId>
            <version>1.0.0</version>
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

## Step 2: Implement AIProvider Interface

**File**: `{YourProvider}Provider.java`

```java
package com.ai.infrastructure.provider.yourprovider;

import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.ProviderConfig;
import com.ai.infrastructure.provider.ProviderStatus;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YourProviderProvider implements AIProvider {
    
    private final ProviderConfig config;
    
    @Override
    public String getProviderName() {
        return "your-provider";
    }
    
    @Override
    public boolean isAvailable() {
        // Check if provider is available
        return config != null && 
               config.isValid() && 
               config.isEnabled() &&
               config.getApiKey() != null;
    }
    
    @Override
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        // Implement content generation using your provider SDK
        try {
            // Your provider-specific implementation
            String content = callYourProviderAPI(request);
            
            return AIGenerationResponse.builder()
                .content(content)
                .model(config.getDefaultModel())
                .processingTimeMs(/* calculate */)
                .requestId(/* generate */)
                .build();
        } catch (Exception e) {
            log.error("Failed to generate content", e);
            throw new RuntimeException("Content generation failed", e);
        }
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        // If your provider supports embeddings, implement here
        // Otherwise, throw UnsupportedOperationException
        throw new UnsupportedOperationException(
            "YourProvider does not support embeddings. Use separate embedding provider.");
    }
    
    @Override
    public ProviderStatus getStatus() {
        return ProviderStatus.builder()
            .providerName(getProviderName())
            .available(isAvailable())
            .healthy(isAvailable())
            .build();
    }
    
    @Override
    public ProviderConfig getConfig() {
        return config;
    }
    
    private String callYourProviderAPI(AIGenerationRequest request) {
        // Your provider-specific API call
        return "Generated content";
    }
}
```

## Step 3: Implement EmbeddingProvider (Optional)

**File**: `{YourProvider}EmbeddingProvider.java`

```java
package com.ai.infrastructure.provider.yourprovider;

import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class YourProviderEmbeddingProvider implements EmbeddingProvider {
    
    private final ProviderConfig config;
    
    @Override
    public String getProviderName() {
        return "your-provider";
    }
    
    @Override
    public boolean isAvailable() {
        return config != null && config.isValid() && config.isEnabled();
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        // Implement embedding generation
        List<Double> embedding = callYourProviderEmbeddingAPI(request.getText());
        
        return AIEmbeddingResponse.builder()
            .embedding(embedding)
            .model(config.getDefaultEmbeddingModel())
            .dimensions(embedding.size())
            .processingTimeMs(/* calculate */)
            .requestId(/* generate */)
            .build();
    }
    
    @Override
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
        // Implement batch embedding generation
        return texts.stream()
            .map(text -> {
                AIEmbeddingRequest request = AIEmbeddingRequest.builder()
                    .text(text)
                    .build();
                return generateEmbedding(request);
            })
            .collect(Collectors.toList());
    }
    
    @Override
    public int getEmbeddingDimension() {
        return 1536; // Your provider's embedding dimension
    }
    
    @Override
    public Map<String, Object> getStatus() {
        return Map.of(
            "provider", getProviderName(),
            "available", isAvailable(),
            "dimension", getEmbeddingDimension()
        );
    }
    
    private List<Double> callYourProviderEmbeddingAPI(String text) {
        // Your provider-specific embedding API call
        return List.of();
    }
}
```

## Step 4: Create Auto-Configuration

**File**: `{YourProvider}AutoConfiguration.java`

```java
package com.ai.infrastructure.provider.yourprovider;

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

@Slf4j
@Configuration
@ConditionalOnClass(name = "com.your.provider.Client") // Your provider SDK class
@ConditionalOnProperty(
    prefix = "ai.providers.your-provider",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@EnableConfigurationProperties(AIProviderConfig.class)
public class YourProviderAutoConfiguration {

    @Bean("yourProviderConfig")
    public ProviderConfig yourProviderConfig(AIProviderConfig config) {
        AIProviderConfig.YourProviderConfig yourConfig = config.getYourProvider();
        if (yourConfig == null || !Boolean.TRUE.equals(yourConfig.getEnabled())) {
            return null;
        }
        
        return ProviderConfig.builder()
            .providerName("your-provider")
            .apiKey(yourConfig.getApiKey())
            .baseUrl(yourConfig.getBaseUrl())
            .defaultModel(yourConfig.getModel())
            .defaultEmbeddingModel(yourConfig.getEmbeddingModel())
            .maxTokens(yourConfig.getMaxTokens())
            .temperature(yourConfig.getTemperature())
            .timeoutSeconds(yourConfig.getTimeout())
            .priority(yourConfig.getPriority() != null ? yourConfig.getPriority() : 50)
            .enabled(true)
            .build();
    }

    @Bean
    public AIProvider yourProvider(@Qualifier("yourProviderConfig") ProviderConfig config) {
        if (config == null || !config.isValid() || !config.isEnabled()) {
            log.debug("YourProvider not configured or disabled");
            return new UnavailableYourProvider();
        }
        log.info("Creating YourProvider LLM provider");
        return new YourProviderProvider(config);
    }

    @Bean
    public EmbeddingProvider yourProviderEmbeddingProvider(
            @Qualifier("yourProviderConfig") ProviderConfig config) {
        if (config == null || !config.isValid() || !config.isEnabled()) {
            log.debug("YourProvider embedding provider not configured or disabled");
            return null;
        }
        log.info("Creating YourProvider embedding provider");
        return new YourProviderEmbeddingProvider(config);
    }
}
```

## Step 5: Add Configuration Properties

**Update**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

```java
@Data
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {
    
    // ... existing properties
    
    private YourProviderConfig yourProvider;
    
    @Data
    public static class YourProviderConfig {
        private Boolean enabled = false;
        private String apiKey;
        private String baseUrl;
        private String model = "your-default-model";
        private String embeddingModel = "your-embedding-model";
        private Integer maxTokens = 2000;
        private Double temperature = 0.3;
        private Integer timeout = 60;
        private Integer priority = 50;
    }
}
```

## Step 6: Configuration Example

**application.yml**:

```yaml
ai:
  providers:
    llm-provider: your-provider
    embedding-provider: your-provider  # or use different provider
    
    your-provider:
      enabled: true
      api-key: ${YOUR_PROVIDER_API_KEY}
      base-url: https://api.yourprovider.com
      model: your-model-name
      embedding-model: your-embedding-model
      priority: 50
```

## Step 7: Add to Parent POM

**Update**: `ai-infrastructure-module/pom.xml`

```xml
<modules>
    <module>ai-infrastructure-core</module>
    <module>ai-infrastructure-onnx-starter</module>
    <module>ai-infrastructure-provider-openai</module>
    <module>ai-infrastructure-provider-azure</module>
    <module>ai-infrastructure-provider-your-provider</module> <!-- Add your module -->
    <module>integration-tests</module>
</modules>
```

## Step 8: Testing

Create a test to verify your provider works:

```java
@SpringBootTest
@ActiveProfiles("test-your-provider")
class YourProviderIntegrationTest {
    
    @Autowired
    private AIProviderManager providerManager;
    
    @Test
    void testYourProviderAvailable() {
        AIProvider provider = providerManager.getProvider("your-provider");
        assertNotNull(provider);
        assertTrue(provider.isAvailable());
    }
}
```

## Best Practices

1. **Error Handling**: Always handle API errors gracefully
2. **Logging**: Use SLF4J for consistent logging
3. **Configuration**: Make all settings configurable via YAML
4. **Testing**: Create integration tests for your provider
5. **Documentation**: Document provider-specific features
6. **Status**: Implement proper status reporting
7. **Metrics**: Track performance metrics

## Common Patterns

### Pattern 1: LLM Only Provider

If your provider doesn't support embeddings:

```java
@Bean
public AIProvider yourProvider(...) {
    // Create LLM provider
}

// Don't create EmbeddingProvider bean
```

### Pattern 2: Embeddings Only Provider

If your provider only supports embeddings:

```java
// Don't create AIProvider bean

@Bean
public EmbeddingProvider yourProviderEmbeddingProvider(...) {
    // Create embedding provider only
}
```

### Pattern 3: Both LLM and Embeddings

If your provider supports both:

```java
@Bean
public AIProvider yourProvider(...) {
    // Create LLM provider
}

@Bean
public EmbeddingProvider yourProviderEmbeddingProvider(...) {
    // Create embedding provider
}
```

## Troubleshooting

### Provider Not Discovered

- Check `@ConditionalOnClass` matches your SDK class
- Verify module is in parent POM
- Check `@ConditionalOnProperty` matches configuration

### Configuration Not Loaded

- Verify `@EnableConfigurationProperties(AIProviderConfig.class)`
- Check property prefix matches configuration
- Verify `enabled: true` in configuration

### Bean Creation Failed

- Check all required dependencies are available
- Verify API key is configured
- Check provider SDK is on classpath

## Next Steps

1. Implement your provider module
2. Add configuration properties
3. Create integration tests
4. Document provider-specific features
5. Submit for review
