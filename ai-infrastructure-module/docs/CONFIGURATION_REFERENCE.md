# Configuration Reference Guide

## Overview

Complete reference for all configuration properties in the modular provider architecture.

## Provider Selection

### LLM Provider Selection

```yaml
ai:
  providers:
    llm-provider: openai  # Options: openai, azure, anthropic, cohere, gemini
```

### Embedding Provider Selection

```yaml
ai:
  providers:
    embedding-provider: onnx  # Options: onnx, openai, azure, cohere, rest
```

### Fallback Configuration

```yaml
ai:
  providers:
    enable-fallback: true  # Enable automatic fallback to other providers
```

## OpenAI Configuration

```yaml
ai:
  providers:
    openai:
      enabled: true                    # Enable OpenAI provider
      api-key: ${OPENAI_API_KEY}       # OpenAI API key (required)
      model: gpt-4o-mini               # Default LLM model
      embedding-model: text-embedding-3-small  # Default embedding model
      max-tokens: 2000                 # Maximum tokens per request
      temperature: 0.3                # Temperature (0.0-2.0)
      timeout: 60                      # Request timeout in seconds
      priority: 100                    # Provider priority (higher = preferred)
```

## Azure OpenAI Configuration

```yaml
ai:
  providers:
    azure:
      enabled: true                    # Enable Azure OpenAI provider
      api-key: ${AZURE_OPENAI_API_KEY} # Azure API key (required)
      endpoint: https://your-resource.openai.azure.com  # Azure endpoint (required)
      deployment-name: gpt-4          # LLM deployment name
      embedding-deployment-name: text-embedding-ada-002  # Embedding deployment
      api-version: 2024-02-15-preview  # API version
      priority: 90                     # Provider priority
```

## Anthropic Configuration

```yaml
ai:
  providers:
    anthropic:
      enabled: true                    # Enable Anthropic provider
      api-key: ${ANTHROPIC_API_KEY}    # Anthropic API key (required)
      model: claude-3-opus-20240229   # Default model
      max-tokens: 4096                 # Maximum tokens
      temperature: 0.3                 # Temperature
      timeout: 60                      # Request timeout
      priority: 80                     # Provider priority
      # Note: Anthropic does not support embeddings
```

## ONNX Configuration

```yaml
ai:
  providers:
    onnx:
      enabled: true                    # Enable ONNX provider
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
      tokenizer-path: classpath:/models/embeddings/tokenizer.json
      max-sequence-length: 512        # Maximum sequence length
      use-gpu: false                  # Use GPU acceleration
      # Note: ONNX only supports embeddings, not LLM
```

## REST Embedding Configuration

```yaml
ai:
  providers:
    rest:
      enabled: false                  # Enable REST embedding provider
      base-url: http://localhost:8000 # REST API base URL
      endpoint: /embed                # Embedding endpoint
      batch-endpoint: /embed/batch   # Batch embedding endpoint
      timeout: 30000                  # Request timeout (ms)
      model: all-MiniLM-L6-v2        # Model name
```

## Cohere Configuration

```yaml
ai:
  providers:
    cohere:
      enabled: false                  # Enable Cohere provider
      api-key: ${COHERE_API_KEY}     # Cohere API key
      model: command                  # LLM model
      embedding-model: embed-english-v3.0  # Embedding model
      max-tokens: 2000
      temperature: 0.3
      priority: 70
```

## Vector Database Configuration

### Lucene (Default)

```yaml
ai:
  vector-db:
    type: lucene
    lucene:
      index-path: ./data/lucene-vector-index
      similarity-threshold: 0.7
      max-results: 100
      vector-dimension: 1536
```

### Pinecone

```yaml
ai:
  vector-db:
    type: pinecone
    pinecone:
      api-key: ${PINECONE_API_KEY}
      environment: us-east-1-aws
      index-name: ai-infrastructure
      dimensions: 1536
      project-id: ${PINECONE_PROJECT_ID}  # Optional
      api-host: ${PINECONE_API_HOST}      # Optional
```

### Weaviate

```yaml
ai:
  vector-db:
    type: weaviate
    weaviate:
      url: http://localhost:8080
      api-key: ${WEAVIATE_API_KEY}
      scheme: http  # or https
      host: localhost
      port: 8080
```

### Qdrant

```yaml
ai:
  vector-db:
    type: qdrant
    qdrant:
      url: http://localhost:6333
      api-key: ${QDRANT_API_KEY}  # Optional
      collection-name: vectors
```

### Milvus

```yaml
ai:
  vector-db:
    type: milvus
    milvus:
      host: localhost
      port: 19530
      collection-name: vectors
      database-name: default
```

### InMemory (Testing)

```yaml
ai:
  vector-db:
    type: memory
```

## Complete Configuration Example

```yaml
ai:
  enabled: true
  
  providers:
    # Provider Selection
    llm-provider: openai
    embedding-provider: onnx
    enable-fallback: true
    
    # OpenAI Configuration
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      embedding-model: text-embedding-3-small
      max-tokens: 2000
      temperature: 0.3
      timeout: 60
      priority: 100
    
    # Azure Configuration (optional)
    azure:
      enabled: false
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com
      deployment-name: gpt-4
      embedding-deployment-name: text-embedding-ada-002
      priority: 90
    
    # Anthropic Configuration (optional)
    anthropic:
      enabled: false
      api-key: ${ANTHROPIC_API_KEY}
      model: claude-3-opus-20240229
      priority: 80
    
    # ONNX Configuration
    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
      tokenizer-path: classpath:/models/embeddings/tokenizer.json
  
  # Vector Database Configuration
  vector-db:
    type: lucene
    lucene:
      index-path: ./data/lucene-vector-index
      similarity-threshold: 0.7
      max-results: 100
```

## Environment Variables

All API keys and sensitive configuration should use environment variables:

```bash
export OPENAI_API_KEY=sk-...
export AZURE_OPENAI_API_KEY=...
export AZURE_OPENAI_ENDPOINT=https://...
export ANTHROPIC_API_KEY=...
export PINECONE_API_KEY=...
```

## Configuration Precedence

1. **Environment Variables** (highest priority)
2. **Command-line arguments** (`--ai.providers.openai.api-key=...`)
3. **Profile-specific YAML** (`application-prod.yml`)
4. **Base YAML** (`application.yml`)

## Validation

Configuration is validated at startup:

- Required fields must be present
- API keys must be non-empty
- Provider must be enabled to be used
- Selected providers must be available

## Troubleshooting

### Provider Not Available

Check:
- Provider module is in dependencies
- `enabled: true` in configuration
- API key is configured
- Provider SDK is on classpath

### Wrong Provider Selected

Check:
- `llm-provider` matches desired provider name
- `embedding-provider` matches desired provider name
- Provider is enabled and available

### Configuration Not Loading

Check:
- YAML syntax is correct
- Property names match exactly
- Profile is active
- Configuration file is in correct location
