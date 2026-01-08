# Adding a New Provider Guide

## Overview

This guide explains how to add a new AI provider (LLM or Embedding) to the AI Infrastructure module. The process is designed to be extensible - you only need to update the provider registry and implement the provider code.

## Step-by-Step Process

### Step 1: Create Provider Module

Create a new Maven module for your provider:

```
ai-infrastructure-module/providers/ai-infrastructure-provider-{provider-name}/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/ai/infrastructure/provider/{provider-name}/
        │       ├── {Provider}Provider.java          # LLM provider
        │       ├── {Provider}EmbeddingProvider.java # Embedding provider (if needed)
        │       └── {Provider}AutoConfiguration.java # Spring Boot auto-configuration
        └── resources/
            └── META-INF/
                └── spring/
                    └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

### Step 2: Implement Provider Classes

#### LLM Provider

Implement `AIProvider` interface:

```java
@Slf4j
public class MyProvider implements AIProvider {
    
    private final ProviderConfig config;
    private final RestTemplate restTemplate;
    
    @Override
    public String getProviderName() {
        return "myprovider";
    }
    
    @Override
    public boolean isAvailable() {
        return config.isValid() && 
               hasText(config.getApiKey()) &&
               // Add other availability checks
    }
    
    @Override
    public AIGenerationResponse generateContent(AIGenerationRequest request) {
        // Implement API call
    }
    
    @Override
    public ProviderStatus getStatus() {
        // Return provider status
    }
    
    @Override
    public ProviderConfig getConfig() {
        return config;
    }
}
```

#### Embedding Provider (if needed)

Implement `EmbeddingProvider` interface:

```java
@Slf4j
public class MyProviderEmbeddingProvider implements EmbeddingProvider {
    
    @Override
    public String getProviderName() {
        return "myprovider";
    }
    
    @Override
    public boolean isAvailable() {
        // Check availability
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        // Implement embedding API call
    }
    
    @Override
    public List<AIEmbeddingResponse> generateEmbeddings(List<String> texts) {
        // Implement batch embedding API call
    }
}
```

#### Auto-Configuration

Create Spring Boot auto-configuration:

```java
@AutoConfiguration
@ConditionalOnClass(MyProvider.class)
public class MyProviderAutoConfiguration {
    
    @Bean
    @ConditionalOnProperty(prefix = "ai.providers.myprovider", name = "enabled", havingValue = "true")
    public ProviderConfig myProviderConfig(AIProviderConfig providerConfig) {
        // Create ProviderConfig
    }
    
    @Bean
    @ConditionalOnBean(name = "myProviderConfig")
    public MyProvider myProvider(@Qualifier("myProviderConfig") ProviderConfig config,
                                 AIProviderConfig aiProviderConfig,
                                 ObjectProvider<RestTemplate> restTemplateProvider) {
        // Create provider instance
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "myprovider")
    public EmbeddingProvider myProviderEmbeddingProvider(AIProviderConfig config) {
        // Create embedding provider
    }
}
```

### Step 3: Add to Parent POM

Add module to parent POM:

**File**: `ai-infrastructure-module/pom.xml`

```xml
<modules>
    <!-- ... existing modules ... -->
    <module>providers/ai-infrastructure-provider-myprovider</module>
</modules>

<dependencyManagement>
    <dependencies>
        <!-- ... existing dependencies ... -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-provider-myprovider</artifactId>
            <version>${project.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### Step 4: Add Configuration to AIProviderConfig

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

```java
public class AIProviderConfig {
    // ... existing configs ...
    private final MyProviderConfig myprovider = new MyProviderConfig();
    
    // Add to resolveGenerationDefaults()
    case "myprovider" -> myprovider.toGenerationDefaults("myprovider");
    
    // Add to resolveEmbeddingDefaults() (if embedding supported)
    case "myprovider" -> myprovider.toEmbeddingDefaults("myprovider");
    
    // Add config class
    @Data
    public static class MyProviderConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String model;
        private String embeddingModel;
        // ... other fields
        
        GenerationDefaults toGenerationDefaults(String providerName) {
            return new GenerationDefaults(providerName, model, maxTokens, temperature, timeout, priority);
        }
        
        EmbeddingDefaults toEmbeddingDefaults(String providerName) {
            return new EmbeddingDefaults(providerName, embeddingModel);
        }
    }
}
```

### Step 5: Add to Provider Registry

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml`

```yaml
providers:
  llm:
    myprovider:
      name: "myprovider"
      displayName: "My Provider (Brand Name)"
      apiKeyEnvVar: "MYPROVIDER_API_KEY"
      required: true
      defaultModel: "model-name"
      baseUrl: "https://api.myprovider.com/v1"
      enabled: true
      description: "My Provider description"
      supportedFeatures:
        - "chat_completions"
        - "streaming"
  
  embedding:
    myprovider:
      name: "myprovider"
      displayName: "My Provider Embeddings"
      apiKeyEnvVar: "MYPROVIDER_API_KEY"
      required: true
      defaultModel: "embedding-model"
      baseUrl: "https://api.myprovider.com/v1"
      enabled: true
      description: "My Provider embeddings"
      dimensions: 768
      supportedFeatures:
        - "text_embeddings"
```

### Step 6: Update Test Configuration

**File**: `ai-infrastructure-module/integration-Testing/integration-tests/src/test/resources/application-real-api-test.yml`

```yaml
ai:
  providers:
    myprovider:
      enabled: ${MYPROVIDER_ENABLED:true}
      api-key: ${MYPROVIDER_API_KEY:}
      base-url: ${MYPROVIDER_BASE_URL:https://api.myprovider.com/v1}
      model: ${MYPROVIDER_MODEL:model-name}
      embedding-model: ${MYPROVIDER_EMBEDDING_MODEL:embedding-model}
      max-tokens: 2000
      temperature: 0.3
      timeout: 60
      priority: 85
```

### Step 7: Update Test Discovery

**File**: `ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/realapi/AbstractProviderMatrixIntegrationTest.java`

Add to `additionalDiscoveryProperties()`:

```java
String myproviderKey = System.getenv("MYPROVIDER_API_KEY");
if (myproviderKey != null && !myproviderKey.trim().isEmpty()) {
    props.put("ai.providers.myprovider.api-key", myproviderKey);
    props.put("ai.providers.myprovider.apiKey", myproviderKey);
    props.put("ai.providers.myprovider.enabled", true);
}
```

### Step 8: Update GitHub Actions Workflow

**File**: `.github/workflows/integration-tests-manual.yml`

#### Add to LLM Provider Options

```yaml
llm_provider:
  options:
    - openai
    - anthropic
    - gemini
    - cohere
    - azure
    - myprovider  # Add here
    - rest
```

#### Add to Embedding Provider Options

```yaml
embedding_provider:
  options:
    - onnx
    - openai
    - anthropic
    - gemini
    - cohere
    - azure
    - myprovider  # Add here
    - rest
```

#### Add API Key Input

```yaml
myprovider_api_key:
  description: 'My Provider API Key (required for My Provider LLM/Embedding)'
  required: false
  type: string
```

#### Add to Environment Variables

In all test job steps, add:

```yaml
env:
  # ... existing vars ...
  MYPROVIDER_API_KEY: ${{ github.event.inputs.myprovider_api_key }}
```

#### Update Composite Action

**File**: `.github/actions/configure-providers/action.yml`

Add input:

```yaml
myprovider_api_key:
  description: 'My Provider API Key'
  required: false
```

Add configuration step:

```yaml
- name: Configure My Provider
  if: inputs.llm_provider == 'myprovider' || inputs.embedding_provider == 'myprovider'
  shell: bash
  run: |
    if [ -n "${{ inputs.myprovider_api_key }}" ]; then
      echo "MYPROVIDER_API_KEY=${{ inputs.myprovider_api_key }}" >> $GITHUB_ENV
      echo "✅ Configured My Provider API key"
    else
      echo "⚠️  WARNING: My Provider selected but no API key provided"
    fi
```

### Step 9: Update Test Assumptions

Update test assumption methods to include your provider:

**Files**: 
- `RealAPIIntegrationTest.java`
- `RealAPIONNXFallbackIntegrationTest.java`
- `RealAPISmartValidationIntegrationTest.java`
- `RealAPIProviderMatrixIntegrationTest.java`

Add to `assumeOpenAIConfigured()` or `hasProviderKey()`:

```java
boolean hasMyProvider = StringUtils.hasText(System.getProperty("MYPROVIDER_API_KEY")) ||
                       StringUtils.hasText(System.getenv("MYPROVIDER_API_KEY"));
// Add to the OR condition
```

### Step 10: Add Integration Test Dependency

**File**: `ai-infrastructure-module/integration-Testing/integration-tests/pom.xml`

```xml
<dependencies>
    <!-- ... existing dependencies ... -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-provider-myprovider</artifactId>
    </dependency>
</dependencies>
```

### Step 11: Test Your Provider

1. **Build the module**:
   ```bash
   cd ai-infrastructure-module
   mvn clean install -DskipTests
   ```

2. **Run unit tests**:
   ```bash
   mvn test -pl providers/ai-infrastructure-provider-myprovider
   ```

3. **Test with integration tests**:
   ```bash
   export MYPROVIDER_API_KEY="your-api-key"
   cd integration-Testing/integration-tests
   mvn test -Dtest=RealAPIIntegrationTest#testRealEmbeddingGeneration \
     -Dai.providers.llm-provider=myprovider \
     -Dai.providers.embedding-provider=myprovider
   ```

4. **Test in GitHub Actions**:
   - Trigger workflow manually
   - Select `myprovider` as LLM or Embedding provider
   - Provide API key
   - Verify tests run successfully

## Checklist

- [ ] Provider module created
- [ ] LLM provider implemented (`AIProvider`)
- [ ] Embedding provider implemented (`EmbeddingProvider`) - if needed
- [ ] Auto-configuration created
- [ ] Added to parent POM
- [ ] Configuration added to `AIProviderConfig`
- [ ] Added to provider registry YAML
- [ ] Test configuration updated
- [ ] Test discovery updated
- [ ] GitHub Actions workflow updated
- [ ] Test assumptions updated
- [ ] Integration test dependency added
- [ ] Unit tests written and passing
- [ ] Integration tests passing
- [ ] Documentation updated

## Common Patterns

### API Key Authentication

Most providers use Bearer token:

```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.setBearerAuth(config.getApiKey());
```

### Query Parameter Authentication

Some providers (like Gemini) use query parameters:

```java
String url = baseUrl + "/endpoint?key=" + config.getApiKey();
```

### Custom Headers

Some providers use custom header names:

```java
headers.set("api-key", config.getApiKey()); // Azure
headers.set("x-api-key", config.getApiKey()); // Some providers
```

### Intent Extraction Enhancement

For providers that need JSON-only responses:

```java
if (request.getGenerationType() != null && 
    request.getGenerationType().equals("intent_extraction")) {
    String jsonInstruction = "CRITICAL JSON REQUIREMENT: ...";
    systemPrompt = jsonInstruction + systemPrompt;
}
```

## Testing Tips

1. **Start Simple**: Test with a single embedding generation first
2. **Use ONNX**: Test LLM with ONNX embeddings to isolate issues
3. **Check Logs**: Enable debug logging to see API calls
4. **Validate Responses**: Ensure response parsing matches API format
5. **Test Edge Cases**: Empty strings, null values, rate limits

## Troubleshooting

### Provider Not Discovered

- Check auto-configuration file exists: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Verify module is in parent POM
- Check Spring Boot auto-configuration is enabled

### Provider Not Available

- Verify API key is set correctly
- Check `isAvailable()` implementation
- Ensure all required environment variables are set

### Tests Fail

- Check API endpoint URLs
- Verify request/response format matches API documentation
- Check error handling for API failures
- Validate response parsing logic

## Related Documentation

- [Provider Registry Guide](./PROVIDER_REGISTRY_GUIDE.md)
- [GitHub Actions Provider Guide](./GITHUB_ACTIONS_PROVIDER_GUIDE.md)
- [Migration Guide](./MIGRATION_GUIDE.md)
