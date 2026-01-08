# GitHub Actions Provider Guide

## Overview

This guide explains how to use the GitHub Actions workflow for running Real API integration tests with different provider combinations.

## Workflow Location

**File**: `.github/workflows/integration-tests-manual.yml`

## Triggering the Workflow

### Manual Trigger

1. Go to **Actions** tab in GitHub
2. Select **Integration Tests (Manual Trigger)**
3. Click **Run workflow**
4. Fill in the required inputs
5. Click **Run workflow**

### Workflow Inputs

#### Module Selection

- **`modules`**: Which test modules to run
  - Options: `all`, `ai-infrastructure`, `relationship-query`, `behavior`
  - Default: `all`

#### Provider Selection

- **`llm_provider`**: LLM provider to use
  - Options: `openai`, `anthropic`, `gemini`, `cohere`, `azure`, `rest`
  - Default: `openai`

- **`embedding_provider`**: Embedding provider to use
  - Options: `onnx`, `openai`, `anthropic`, `gemini`, `cohere`, `azure`, `rest`
  - Default: `onnx`

#### API Keys

Provide API keys for the selected providers:

- **`openai_api_key`**: Required if using OpenAI for LLM or Embedding
- **`anthropic_api_key`**: Required if using Anthropic for LLM or Embedding
- **`gemini_api_key`**: Required if using Gemini for LLM or Embedding
- **`cohere_api_key`**: Required if using Cohere for LLM or Embedding
- **`azure_api_key`**: Required if using Azure for LLM or Embedding
- **`azure_endpoint`**: Required if using Azure (e.g., `https://your-resource.openai.azure.com/openai/v1`)
- **`azure_deployment_name`**: Required if using Azure for LLM
- **`azure_embedding_deployment_name`**: Required if using Azure for Embedding

#### Other Configuration

- **`vector_database`**: Vector database to use
  - Options: `lucene`, `pinecone`, `weaviate`, `qdrant`, `milvus`, `memory`
  - Default: `lucene`

- **`persistence_database`**: Persistence database
  - Options: `h2`, `postgresql`
  - Default: `h2`

- **`storage_strategy`**: Storage strategy
  - Options: `SINGLE_TABLE`, `PER_TYPE_TABLE`, `CUSTOM`
  - Default: `SINGLE_TABLE`

- **`test_chunk`**: Test chunk to run (faster execution)
  - Options: `all`, `core`, `vector`, `intent-actions`, `advanced`
  - Default: `all`

- **`timeout_minutes`**: Job timeout
  - Default: `30`

- **`logging_level`**: Maven logging level
  - Options: `quiet`, `normal`, `verbose`, `debug`
  - Default: `quiet`

## Example Workflow Runs

### Example 1: OpenAI + ONNX (Fast, Local Embeddings)

```
modules: ai-infrastructure
llm_provider: openai
embedding_provider: onnx
openai_api_key: sk-...
vector_database: lucene
test_chunk: core
```

### Example 2: Gemini + Gemini (Full Google Stack)

```
modules: all
llm_provider: gemini
embedding_provider: gemini
gemini_api_key: AIzaSy...
vector_database: lucene
test_chunk: all
```

### Example 3: Azure + Azure (Full Azure Stack)

```
modules: ai-infrastructure
llm_provider: azure
embedding_provider: azure
azure_api_key: F93lTwne...
azure_endpoint: https://your-resource.openai.azure.com/openai/v1
azure_deployment_name: Llama-4-Maverick-17B-128E-Instruct-FP8
azure_embedding_deployment_name: text-embedding-ada-002
vector_database: lucene
test_chunk: core
```

### Example 4: Cohere + Cohere (Full Cohere Stack)

```
modules: ai-infrastructure
llm_provider: cohere
embedding_provider: cohere
cohere_api_key: 7GC502ZDSVS6...
vector_database: lucene
test_chunk: all
```

## Provider Configuration

The workflow uses the `configure-providers` composite action to set up environment variables automatically based on your selections.

### What Happens

1. **Provider Selection**: Workflow reads your provider selections
2. **Configuration**: `configure-providers` action sets appropriate environment variables
3. **Validation**: Scripts validate that required variables are set
4. **Test Execution**: Tests run with the configured providers

### Environment Variables Set

The action automatically sets:

- `LLM_PROVIDER`: Selected LLM provider name
- `EMBEDDING_PROVIDER`: Selected embedding provider name
- `ai.providers.llm-provider`: Spring property for LLM provider
- `ai.providers.embedding-provider`: Spring property for embedding provider
- Provider-specific API keys (e.g., `OPENAI_API_KEY`, `GEMINI_API_KEY`)

## Test Chunks

Test chunks allow you to run subsets of tests for faster execution:

### Core Tests (5-8 minutes)
- Basic functionality
- CRUD operations
- Validation

**Chunk**: `core`

### Vector Tests (8-12 minutes)
- Vector database operations
- Similarity search
- Indexing strategies

**Chunk**: `vector`

### Intent-Actions Tests (6-10 minutes)
- Intent extraction
- Action handling
- Workflow processing

**Chunk**: `intent-actions`

### Advanced Tests (10-15 minutes)
- Multi-provider scenarios
- PII handling
- Complex use cases

**Chunk**: `advanced`

### All Tests (30-45 minutes)
- Complete test suite

**Chunk**: `all`

## Validation

### Pre-Flight Validation

The workflow includes validation steps:

1. **Registry Validation**: Checks that `providers-registry.yml` is valid
2. **Provider Availability**: Validates that selected providers have required API keys
3. **Configuration Validation**: Ensures environment variables are set correctly

### Error Messages

If validation fails, you'll see helpful error messages:

```
❌ ERROR: LLM provider 'azure' requires environment variable: AZURE_API_KEY
❌ ERROR: Embedding provider 'azure' requires environment variable: AZURE_ENDPOINT
```

## Viewing Results

### Test Reports

After workflow completion:

1. Go to **Actions** tab
2. Click on the workflow run
3. Expand **Upload test reports** step
4. Download artifacts to view detailed reports

### Test Summary

The workflow generates a summary in the **Test Summary** job showing:
- Module status
- Configuration used
- Overall result

## Troubleshooting

### Provider Not Available

**Issue**: Workflow shows "Provider not available"

**Solutions**:
1. Check that API key is set correctly
2. For Azure: Ensure both `azure_api_key` and `azure_endpoint` are provided
3. Check provider registry for required environment variables

### Tests Skipped

**Issue**: Tests are skipped with "No provider API key configured"

**Solutions**:
1. Ensure you provided the API key for the selected provider
2. Check that the API key input name matches the provider (e.g., `openai_api_key` for OpenAI)
3. Verify the API key is not empty

### Workflow Timeout

**Issue**: Workflow times out before completing

**Solutions**:
1. Increase `timeout_minutes` input
2. Use a smaller `test_chunk` (e.g., `core` instead of `all`)
3. Run tests for a single module instead of `all`

### Azure Configuration Issues

**Issue**: Azure provider not working

**Solutions**:
1. Verify endpoint format:
   - OpenAI-compatible: `https://your-resource.openai.azure.com/openai/v1`
   - Foundry format: `https://your-resource.services.ai.azure.com/models`
2. Ensure deployment names are correct
3. Check that API key has access to the deployments

## Best Practices

### API Key Security

- **Never commit API keys** to the repository
- Use GitHub Secrets for sensitive keys (if automating)
- Use workflow inputs for manual runs (keys are masked in logs)

### Test Execution

- Start with `test_chunk: core` to verify configuration
- Use `modules: ai-infrastructure` for faster iteration
- Run `test_chunk: all` for comprehensive testing

### Provider Selection

- Use `onnx` for embeddings when testing LLM providers (faster, no API costs)
- Use provider's own embeddings for end-to-end testing
- Test with multiple providers to ensure compatibility

## Related Documentation

- [Provider Registry Guide](./PROVIDER_REGISTRY_GUIDE.md)
- [Adding New Provider Guide](./ADDING_NEW_PROVIDER.md)
- [Migration Guide](./MIGRATION_GUIDE.md)
