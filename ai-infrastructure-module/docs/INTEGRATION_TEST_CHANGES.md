# Integration Test Changes for Modular Providers

## Overview

Integration tests need to be updated to work with the new modular provider architecture. Tests should be parameterized to work with different provider combinations.

## Test Configuration Updates

### Provider-Specific Test Profiles

Create separate test profiles for each provider combination:

**File**: `integration-tests/src/test/resources/application-test-openai-llm-onnx-embed.yml`

```yaml
ai:
  enabled: true
  providers:
    llm-provider: openai
    embedding-provider: onnx
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY:test-key}
      model: gpt-4o-mini
    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
  vector-db:
    type: memory
```

**File**: `integration-tests/src/test/resources/application-test-azure-both.yml`

```yaml
ai:
  enabled: true
  providers:
    llm-provider: azure
    embedding-provider: azure
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY:test-key}
      endpoint: https://test.openai.azure.com
      deployment-name: gpt-4
      embedding-deployment-name: text-embedding-ada-002
  vector-db:
    type: memory
```

**File**: `integration-tests/src/test/resources/application-test-anthropic-llm-openai-embed.yml`

```yaml
ai:
  enabled: true
  providers:
    llm-provider: anthropic
    embedding-provider: openai
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY:test-key}
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY:test-key}
      embedding-model: text-embedding-3-small
  vector-db:
    type: memory
```

## Parameterized Tests

### Example: Provider-Agnostic Test

**File**: `integration-tests/src/test/java/com/ai/infrastructure/it/ProviderAgnosticIntegrationTest.java`

```java
package com.ai.infrastructure.it;

import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import com.ai.infrastructure.core.AIEmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that work with any provider combination
 */
@SpringBootTest
class ProviderAgnosticIntegrationTest {

    @Autowired
    private AIProviderManager providerManager;
    
    @Autowired
    private AIEmbeddingService embeddingService;

    @Test
    void testProviderAutoDiscovery() {
        // Verify providers are auto-discovered
        assertNotNull(providerManager);
        assertTrue(providerManager.getAvailableProviders().size() > 0);
    }

    @Test
    void testEmbeddingProviderSelection() {
        // Verify embedding provider is selected independently
        assertNotNull(embeddingService);
        // Test embedding generation works
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "azure", "anthropic"})
    void testLLMProviderSelection(String providerName) {
        // Test that specific LLM provider can be selected
        AIProvider provider = providerManager.getProvider(providerName);
        if (provider != null && provider.isAvailable()) {
            assertNotNull(provider);
            assertEquals(providerName, provider.getProviderName());
        }
    }
}
```

### Example: Provider-Specific Test

**File**: `integration-tests/src/test/java/com/ai/infrastructure/it/provider/OpenAIProviderIntegrationTest.java`

```java
package com.ai.infrastructure.it.provider;

import com.ai.infrastructure.provider.AIProvider;
import com.ai.infrastructure.provider.AIProviderManager;
import com.ai.infrastructure.embedding.EmbeddingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAI-specific integration tests
 */
@SpringBootTest
@ActiveProfiles("test-openai")
class OpenAIProviderIntegrationTest {

    @Autowired
    private AIProviderManager providerManager;
    
    @Autowired(required = false)
    private EmbeddingProvider openAIEmbeddingProvider;

    @Test
    void testOpenAIProviderAvailable() {
        AIProvider provider = providerManager.getProvider("openai");
        assertNotNull(provider, "OpenAI provider should be available");
        assertTrue(provider.isAvailable(), "OpenAI provider should be available");
    }

    @Test
    void testOpenAIEmbeddingProviderAvailable() {
        // OpenAI module should provide embedding provider
        assertNotNull(openAIEmbeddingProvider, "OpenAI embedding provider should be available");
        assertEquals("openai", openAIEmbeddingProvider.getProviderName());
    }
}
```

## Test Dependencies

### Update Test POM

**File**: `integration-tests/pom.xml`

```xml
<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    
    <!-- Provider modules (include what you want to test) -->
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
        <artifactId>ai-infrastructure-onnx-starter</artifactId>
    </dependency>
    
    <!-- Vector database modules -->
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-vector-memory</artifactId>
    </dependency>
    
    <!-- Test dependencies -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
    </dependency>
</dependencies>
```

## Test Execution

### Run Tests with Specific Provider

```bash
# Test with OpenAI LLM + ONNX embeddings
mvn test -Dspring.profiles.active=test-openai-llm-onnx-embed

# Test with Azure for both
mvn test -Dspring.profiles.active=test-azure-both

# Test with Anthropic LLM + OpenAI embeddings
mvn test -Dspring.profiles.active=test-anthropic-llm-openai-embed
```

### Matrix Testing

Create a test matrix to test all provider combinations:

**File**: `integration-tests/test-matrix.sh`

```bash
#!/bin/bash

# Test all provider combinations
PROFILES=(
    "test-openai-llm-onnx-embed"
    "test-openai-both"
    "test-azure-both"
    "test-anthropic-llm-openai-embed"
)

for profile in "${PROFILES[@]}"; do
    echo "Testing with profile: $profile"
    mvn test -Dspring.profiles.active=$profile
done
```

## Real API Tests

### Update Real API Test Configuration

**File**: `integration-tests/src/test/resources/application-real-api-test.yml`

```yaml
ai:
  enabled: true
  providers:
    llm-provider: ${LLM_PROVIDER:openai}
    embedding-provider: ${EMBEDDING_PROVIDER:onnx}
    
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      embedding-model: text-embedding-3-small
    
    azure:
      enabled: ${AZURE_ENABLED:false}
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: ${AZURE_OPENAI_ENDPOINT}
      deployment-name: ${AZURE_DEPLOYMENT_NAME:gpt-4}
    
    anthropic:
      enabled: ${ANTHROPIC_ENABLED:false}
      api-key: ${ANTHROPIC_API_KEY}
    
    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
```

### Environment Variables

```bash
# Test with OpenAI LLM + ONNX embeddings
export LLM_PROVIDER=openai
export EMBEDDING_PROVIDER=onnx
export OPENAI_API_KEY=sk-...

# Test with Azure for both
export LLM_PROVIDER=azure
export EMBEDDING_PROVIDER=azure
export AZURE_ENABLED=true
export AZURE_OPENAI_API_KEY=...
export AZURE_OPENAI_ENDPOINT=https://...

mvn test -Dspring.profiles.active=real-api-test
```

## Test Coverage

### Required Test Scenarios

1. **Provider Auto-Discovery**
   - Verify providers are discovered automatically
   - Verify embedding providers are discovered independently

2. **Provider Selection**
   - Test LLM provider selection
   - Test embedding provider selection
   - Test independent selection (different providers)

3. **Fallback Scenarios**
   - Test LLM provider fallback
   - Test embedding provider fallback

4. **Provider-Specific Features**
   - Test OpenAI-specific features
   - Test Azure-specific features
   - Test Anthropic-specific features

5. **Error Handling**
   - Test unavailable provider handling
   - Test invalid configuration handling
   - Test API error handling

## Migration Checklist

- [ ] Create provider-specific test profiles
- [ ] Update test POM with provider module dependencies
- [ ] Create parameterized tests
- [ ] Create provider-specific tests
- [ ] Update real API test configuration
- [ ] Create test matrix script
- [ ] Document test execution process
