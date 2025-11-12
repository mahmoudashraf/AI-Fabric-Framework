# Quick Start Guide: Modular Provider Architecture

## Overview

This guide helps you get started with the modular provider architecture in 5 minutes.

## Step 1: Add Dependencies

Add the core library and provider modules you need:

```xml
<dependencies>
    <!-- Core library -->
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Provider modules (choose what you need) -->
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-provider-openai</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Embedding provider (if different from LLM) -->
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-onnx-starter</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

## Step 2: Configure Providers

Create `application.yml`:

```yaml
ai:
  enabled: true
  
  providers:
    # Select your providers
    llm-provider: openai          # For content generation
    embedding-provider: onnx      # For vector embeddings
    
    # OpenAI configuration
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
    
    # ONNX configuration (local embeddings)
    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
```

## Step 3: Set Environment Variables

```bash
export OPENAI_API_KEY=sk-your-key-here
```

## Step 4: Use in Your Code

```java
@Service
public class MyService {
    
    @Autowired
    private AIProviderManager providerManager;
    
    @Autowired
    private AIEmbeddingService embeddingService;
    
    public void generateContent() {
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Hello, world!")
            .build();
        
        AIGenerationResponse response = providerManager.generateContent(request);
        System.out.println(response.getContent());
    }
    
    public void generateEmbedding() {
        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text("Hello, world!")
            .build();
        
        AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
        System.out.println("Embedding dimensions: " + response.getDimensions());
    }
}
```

## Common Configurations

### Configuration 1: OpenAI for Everything

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: openai
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
```

**Dependencies**: `ai-infrastructure-provider-openai`

### Configuration 2: Cost Optimization (OpenAI LLM + ONNX Embeddings)

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

**Dependencies**: 
- `ai-infrastructure-provider-openai`
- `ai-infrastructure-onnx-starter`

### Configuration 3: Azure Enterprise

```yaml
ai:
  providers:
    llm-provider: azure
    embedding-provider: azure
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com
      deployment-name: gpt-4
```

**Dependencies**: `ai-infrastructure-provider-azure`

### Configuration 4: Best Quality (Anthropic LLM + OpenAI Embeddings)

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
      embedding-model: text-embedding-3-small
```

**Dependencies**:
- `ai-infrastructure-provider-anthropic`
- `ai-infrastructure-provider-openai`

## Next Steps

1. **Read Full Documentation**: See [AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md](AI_PROVIDER_MODULAR_ARCHITECTURE_PLAN.md)
2. **Configuration Reference**: See [CONFIGURATION_REFERENCE.md](CONFIGURATION_REFERENCE.md)
3. **Troubleshooting**: See [TROUBLESHOOTING_GUIDE.md](TROUBLESHOOTING_GUIDE.md)
4. **Migration**: See [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md)

## Quick Troubleshooting

### Provider Not Found?
- Check provider module is in dependencies
- Verify `enabled: true` in configuration
- Check API key is set

### Configuration Not Loading?
- Check YAML syntax
- Verify property names match exactly
- Check profile is active

### Need Help?
- Check [TROUBLESHOOTING_GUIDE.md](TROUBLESHOOTING_GUIDE.md)
- Review logs with `logging.level.com.ai.infrastructure=DEBUG`
