# Behavior Real API Integration Tests

This document explains how to run the Real API integration tests for the behavior module.

## Overview

The Real API tests in `src/test/java/com/ai/behavior/realapi/` make actual API calls to external providers (e.g., OpenAI) and are excluded from the default Maven build. They require:
- OpenAI API key
- Specific provider configurations
- Additional execution time

## Test Classes

### Real API Tests (realapi package)
1. **BehaviorRealApiIntegrationTest** - Base test class for Real API tests
2. **BehaviorRealApiSemanticSearchIntegrationTest** - Semantic search with real API
3. **BehaviorRealApiOrchestratedSearchIntegrationTest** - Orchestrated search with real API
4. **BehaviorRealApiProviderMatrixIntegrationTest** - Matrix test that runs Real API tests with multiple provider combinations

## Running Real API Tests

### Method 1: Using the Script (Recommended)

Use the provided script with your OpenAI API key:

```bash
cd ai-infrastructure-module/ai-infrastructure-behavior

export OPENAI_API_KEY='your-api-key-here'

# Run with default (openai:onnx:lucene)
./run-realapi-tests.sh

# Run with specific combination
./run-realapi-tests.sh "openai:onnx:lucene"

# Run with different providers
./run-realapi-tests.sh "openai:openai:lucene"
```

### Method 2: Using Maven Directly

Run with the `realapi-tests` profile:

```bash
cd ai-infrastructure-module/ai-infrastructure-behavior

export OPENAI_API_KEY='your-api-key-here'

# Run all Real API tests
mvn test -P realapi-tests \
  -Dspring.profiles.active=real-api-test \
  -Dai.behavior.realapi.enabled=true \
  -Dai.providers.llm-provider=openai \
  -Dai.providers.embedding-provider=onnx \
  -Dai.providers.vector-database=lucene

# Run a specific test
mvn test -P realapi-tests \
  -Dtest=BehaviorRealApiSemanticSearchIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.behavior.realapi.enabled=true \
  -Dai.providers.llm-provider=openai \
  -Dai.providers.embedding-provider=onnx \
  -Dai.providers.vector-database=lucene
```

### Method 3: Run Matrix Test

The Real API matrix test runs multiple provider combinations:

```bash
cd ai-infrastructure-module/ai-infrastructure-behavior

export OPENAI_API_KEY='your-api-key-here'

mvn test -P realapi-tests \
  -Dtest=BehaviorRealApiProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.behavior.realapi.enabled=true \
  -Dai.behavior.realapi.matrix="openai:onnx" \
  -Dai.providers.llm-provider=openai \
  -Dai.providers.embedding-provider=onnx \
  -Dai.providers.vector-database=lucene
```

**Note**: The standard matrix test (`BehaviorProviderMatrixIntegrationTest` in `it/matrix/`) uses mocked services and does not require an OpenAI key. It is excluded from the `realapi-tests` profile and should be run separately if needed.

## Required Environment Variables

- **OPENAI_API_KEY** (required) - Your OpenAI API key
- **AI_INFRASTRUCTURE_LLM_PROVIDER** (optional) - LLM provider, default: `openai`
- **AI_INFRASTRUCTURE_EMBEDDING_PROVIDER** (optional) - Embedding provider, default: `onnx`
- **AI_INFRASTRUCTURE_VECTOR_DATABASE** (optional) - Vector database, default: `lucene`
- **AI_INFRASTRUCTURE_PERSISTENCE_DATABASE** (optional) - Persistence database, default: `postgres`

## Provider Combinations

The test format is: `LLM_PROVIDER:EMBEDDING_PROVIDER:VECTOR_DATABASE`

Examples:
- `openai:onnx:lucene` - OpenAI LLM, ONNX embeddings, Lucene vector DB
- `openai:openai:lucene` - OpenAI LLM, OpenAI embeddings, Lucene vector DB
- `openai:onnx` - OpenAI LLM, ONNX embeddings (vector DB uses default)

## Notes

- These tests are **excluded** from default `mvn verify` builds
- They require an active internet connection and valid API keys
- Tests may take longer to execute due to real API calls
- Costs may be incurred based on API usage
- The `realapi-tests` Maven profile must be activated to run these tests

## Troubleshooting

### Tests are skipped
- Ensure `-Dai.behavior.realapi.enabled=true` is set
- Check that `OPENAI_API_KEY` is configured
- Verify the `realapi-tests` profile is activated with `-P realapi-tests`

### Bean definition conflicts
- The `realapi` package is excluded from component scanning in `TestBehaviorApplication`
- Real API tests use `BehaviorRealApiTestApplication` instead

### Missing dependencies
- Ensure all required provider modules are in the test classpath
- Check that `ai-infrastructure-provider-openai` and `ai-infrastructure-onnx-starter` are available

