# Real API Tests Restructuring - Implementation Guide

## Overview

This document provides detailed implementation guidance for restructuring the Real API test execution flow based on comprehensive analysis of the current codebase. It builds on the strategic plan in `REALAPI_TESTS_RESTRUCTURING_PLAN.md` with specific, actionable implementation steps.

---

## Table of Contents

1. [Current Architecture Analysis](#current-architecture-analysis)
2. [Test Module Inventory](#test-module-inventory)
3. [Provider Configuration Matrix](#provider-configuration-matrix)
4. [Implementation Roadmap](#implementation-roadmap)
5. [Code Examples](#code-examples)
6. [Performance Optimization](#performance-optimization)
7. [Testing Strategy](#testing-strategy)

---

## Current Architecture Analysis

### Test Execution Flow

#### Integration Tests Module
**Location**: `ai-infrastructure-module/integration-Testing/integration-tests/`

**Key Components**:
- **Test Class**: `RealAPIProviderMatrixIntegrationTest.java` (15 test classes)
- **Runner Script**: `run-provider-matrix-tests.sh`
- **Configuration**: `application-real-api-test.yml`

**Capabilities**:
- ✅ Full matrix support: `llm:embedding:vectordb:storage`
- ✅ Test chunking: `core`, `vector`, `intent-actions`, `advanced`, `all`
- ✅ Auto-discovery of available providers
- ✅ Multiple combination support via comma-separated spec
- ✅ Auto-configuration (OpenAI dimensions for Lucene)

**Current Test Classes** (15 total):
```
CHUNK_CORE (4 classes):
  - RealAPIIntegrationTest
  - RealAPIIntegrationTestV2
  - RealAPIONNXFallbackIntegrationTest
  - RealAPISmartValidationIntegrationTest

CHUNK_VECTOR (3 classes):
  - RealAPIVectorLifecycleIntegrationTest
  - RealAPIHybridRetrievalToggleIntegrationTest
  - IndexingStrategyIntegrationTest

CHUNK_INTENT_ACTIONS (4 classes):
  - RealAPIIntentHistoryAggregationIntegrationTest
  - RealAPIActionErrorRecoveryIntegrationTest
  - RealAPIActionFlowIntegrationTest
  - RealAPIIntentGenerationRoutingIntegrationTest

CHUNK_ADVANCED (4 classes):
  - RealAPIMultiProviderFailoverIntegrationTest
  - RealAPISmartSuggestionsIntegrationTest
  - RealAPIPIIEdgeSpectrumIntegrationTest
  - RealAPICreativeAIScenariosIntegrationTest
```

#### Relationship Query Module
**Location**: `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/`

**Current Capabilities**:
- ❌ Single combination only
- ❌ No test chunking
- ❌ No auto-discovery
- ✅ Failsafe integration tests
- ✅ Matrix format: `llm:embedding:vectordb`

**Test Pattern**: `*RealApiIntegrationTest.java` in `realapi/` directory

#### Behavior Module
**Location**: `ai-infrastructure-module/integration-Testing/behavior-integration-tests/`

**Current Capabilities**:
- ❌ Single combination only
- ❌ No test chunking
- ❌ No auto-discovery
- ✅ Failsafe integration tests
- ✅ Matrix format: `llm:embedding:vectordb`

**Test Pattern**: `*IT.java` and `*IntegrationIT.java`

### GitHub Actions Workflow Analysis

**File**: `.github/workflows/integration-tests-manual.yml`

**Current Features**:
- ✅ Manual workflow dispatch
- ✅ Module selection: `all`, `ai-infrastructure`, `relationship-query`, `behavior`
- ✅ Provider selection via UI dropdowns
- ✅ Test chunk parameter (integration-tests only)
- ✅ Configurable timeout, logging level
- ✅ Storage strategy selection

**Current Limitations**:
- ❌ Single provider combination per run
- ❌ Chunk selection only for integration-tests
- ❌ No provider auto-discovery mode
- ❌ No cross-module chunk coordination

---

## Test Module Inventory

### Integration Tests - Detailed Breakdown

#### Core Tests (Estimated: 5-8 minutes)
**Purpose**: Basic AI infrastructure functionality, CRUD operations, validation

1. **RealAPIIntegrationTest** (2-3 min)
   - Basic embedding generation
   - Simple queries
   - CRUD operations

2. **RealAPIIntegrationTestV2** (1-2 min)
   - Enhanced API coverage
   - Additional edge cases

3. **RealAPIONNXFallbackIntegrationTest** (1-2 min)
   - Provider fallback mechanism
   - ONNX as fallback provider

4. **RealAPISmartValidationIntegrationTest** (1-2 min)
   - Input validation
   - Smart validation rules

**Dependencies**: None (can run independently)
**Provider Requirements**: Any LLM + Any Embedding + Any Vector DB

#### Vector Tests (Estimated: 8-12 minutes)
**Purpose**: Vector database operations, similarity search, indexing

1. **RealAPIVectorLifecycleIntegrationTest** (3-5 min)
   - Vector CRUD operations
   - Lifecycle management
   - Dimension compatibility

2. **RealAPIHybridRetrievalToggleIntegrationTest** (2-3 min)
   - Hybrid search (vector + keyword)
   - Toggle between modes

3. **IndexingStrategyIntegrationTest** (3-4 min)
   - Different indexing strategies
   - Index performance

**Dependencies**: Requires Core tests to pass
**Provider Requirements**:
- Vector DB: `lucene`, `pinecone`, `weaviate`, `qdrant`, `milvus`, or `memory`
- Special: OpenAI + Lucene requires dimension reduction

#### Intent-Actions Tests (Estimated: 6-10 minutes)
**Purpose**: Intent extraction, action handling, workflow processing

1. **RealAPIIntentHistoryAggregationIntegrationTest** (2-3 min)
   - Intent history tracking
   - Aggregation logic

2. **RealAPIActionErrorRecoveryIntegrationTest** (2-3 min)
   - Error handling
   - Recovery mechanisms

3. **RealAPIActionFlowIntegrationTest** (2-3 min)
   - Action workflow execution
   - Flow state management

4. **RealAPIIntentGenerationRoutingIntegrationTest** (2-3 min)
   - Intent generation
   - Routing logic

**Dependencies**: Requires Core tests to pass
**Provider Requirements**: LLM with good intent understanding (OpenAI, Anthropic preferred)

#### Advanced Tests (Estimated: 10-15 minutes)
**Purpose**: Complex scenarios, multi-provider, PII handling, creative use cases

1. **RealAPIMultiProviderFailoverIntegrationTest** (3-4 min)
   - Multi-provider configuration
   - Failover mechanisms
   - Priority handling

2. **RealAPISmartSuggestionsIntegrationTest** (2-3 min)
   - AI-powered suggestions
   - Context-aware recommendations

3. **RealAPIPIIEdgeSpectrumIntegrationTest** (3-4 min)
   - PII detection
   - Edge cases (credit cards, SSN, etc.)
   - Redaction logic

4. **RealAPICreativeAIScenariosIntegrationTest** (3-5 min)
   - Creative AI use cases
   - Complex prompt handling

**Dependencies**: Requires Core and Vector tests to pass
**Provider Requirements**: Varies by test, some require specific providers

### Proposed Relationship Query Test Chunks

Based on typical relationship query test patterns:

#### Basic Chunk (Estimated: 3-5 minutes)
- Simple relationship queries
- One-hop traversals
- Basic filtering

#### Complex Chunk (Estimated: 5-8 minutes)
- Multi-hop traversals
- Complex filtering and aggregation
- Nested relationships

#### Performance Chunk (Estimated: 5-10 minutes)
- Large dataset queries
- Performance benchmarks
- Optimization validation

### Proposed Behavior Test Chunks

Based on behavior module patterns:

#### Analytics Chunk (Estimated: 3-5 minutes)
- Analytics API tests
- Metric collection
- Data aggregation

#### Processing Chunk (Estimated: 4-6 minutes)
- Event processing
- Batch operations
- Stream processing

#### Worker Chunk (Estimated: 3-5 minutes)
- Background worker tests
- Job scheduling
- Queue processing

---

## Provider Configuration Matrix

### Supported Providers

#### LLM Providers
```yaml
openai:
  - Model: gpt-4o-mini
  - Temperature: 0.0
  - Max Tokens: 2000
  - Requires: OPENAI_API_KEY

anthropic:
  - Model: claude-3-sonnet
  - Requires: ANTHROPIC_API_KEY

azure-openai:
  - Requires: AZURE_OPENAI_API_KEY, AZURE_OPENAI_ENDPOINT

cohere:
  - Requires: COHERE_API_KEY

rest:
  - Generic REST API provider
  - Requires custom configuration
```

#### Embedding Providers
```yaml
onnx:
  - Model: all-MiniLM-L6-v2
  - Dimensions: 384
  - Local model (no API key)
  - Works with all providers

openai:
  - Model: text-embedding-3-small (default)
  - Dimensions: 1536 (default), configurable: 512, 768, 1024
  - Model: text-embedding-3-large
  - Dimensions: 3072 (default), configurable: 512, 768, 1024
  - Requires: OPENAI_API_KEY

azure-openai:
  - Same models as OpenAI
  - Requires: AZURE_OPENAI_API_KEY, AZURE_OPENAI_ENDPOINT
```

#### Vector Databases
```yaml
lucene:
  - Max dimensions: 1024
  - Local (no external service)
  - Index path: configurable
  - Compatible with: All embedding providers (with dimension limit)

pinecone:
  - Max dimensions: 20000
  - Cloud service
  - Requires: PINECONE_API_KEY, PINECONE_ENVIRONMENT

weaviate:
  - Flexible dimensions
  - Can be local or cloud
  - Requires configuration

qdrant:
  - Flexible dimensions
  - Can be local or cloud
  - Requires configuration

milvus:
  - Flexible dimensions
  - Can be local or cloud
  - Requires configuration

memory:
  - In-memory vector store
  - No persistence
  - For testing only
```

### Auto-Configuration Rules

#### Rule 1: OpenAI + Lucene Dimension Reduction
```yaml
Condition:
  embedding_provider: openai OR azure-openai
  vector_database: lucene

Action:
  Set: ai.providers.openai.embedding-dimensions=512

Reason:
  OpenAI default (1536) > Lucene max (1024)
  512 is optimal for quality vs. performance

Location:
  - run-provider-matrix-tests.sh:220-235
  - run-relationship-query-realapi-tests.sh:177-183
  - run-behavior-realapi-tests.sh:118-124
```

#### Rule 2: ONNX Compatibility
```yaml
Condition:
  embedding_provider: onnx

Action:
  No special configuration needed

Reason:
  ONNX (384 dimensions) < Lucene max (1024)
  Works with all vector databases
```

#### Rule 3: PostgreSQL vs H2
```yaml
Condition:
  persistence_database: postgresql

Action:
  Use embedded PostgreSQL or external instance
  Configure connection details

Default:
  persistence_database: h2
  In-memory for faster tests
```

### Compatibility Matrix

| LLM | Embedding | Vector DB | Auto-Config | Status | Notes |
|-----|-----------|-----------|-------------|---------|-------|
| openai | onnx | lucene | None | ✅ Ready | Default, always works |
| openai | openai | lucene | Dimensions=512 | ✅ Ready | Requires auto-config |
| openai | onnx | pinecone | None | ✅ Ready | No dimension limits |
| openai | openai | pinecone | None | ✅ Ready | No dimension limits |
| anthropic | onnx | lucene | None | ✅ Ready | Works well |
| anthropic | openai | lucene | Dimensions=512 | ✅ Ready | Requires auto-config |
| anthropic | openai | pinecone | None | ✅ Ready | Optimal combination |
| azure-openai | azure-openai | lucene | Dimensions=512 | ✅ Ready | Requires auto-config |
| azure-openai | onnx | lucene | None | ✅ Ready | Works well |
| cohere | onnx | lucene | None | ⚠️ Limited | Cohere support limited |
| any | onnx | memory | None | ✅ Ready | Testing only |

---

## Implementation Roadmap

### Phase 1: Foundation (Week 1-2)

#### Task 1.1: Create Abstract Base Class for All Modules

**File**: `ai-infrastructure-module/integration-Testing/base/AbstractProviderMatrixIT.java`

```java
package com.ai.infrastructure.it.base;

/**
 * Shared base class for all provider matrix integration tests
 * Provides:
 * - Provider discovery
 * - Matrix parsing
 * - Test chunking
 * - Auto-configuration
 */
public abstract class AbstractProviderMatrixIT {
    // Moved from integration-tests to shared location
    // All modules extend this class
}
```

**Benefits**:
- Single source of truth
- Consistent behavior across modules
- Easy to add new features

#### Task 1.2: Extend Relationship Query Tests

**New File**: `relationship-query-integration-tests/src/test/java/RelationshipQueryProviderMatrixIT.java`

```java
package com.ai.infrastructure.relationshipquery;

import com.ai.infrastructure.it.base.AbstractProviderMatrixIT;

public class RelationshipQueryProviderMatrixIT extends AbstractProviderMatrixIT {

    @Override
    protected Class<?>[] suiteTestClasses() {
        String chunk = getTestChunk();
        return switch (chunk) {
            case "basic" -> BASIC_TESTS;
            case "complex" -> COMPLEX_TESTS;
            case "performance" -> PERFORMANCE_TESTS;
            default -> ALL_TESTS;
        };
    }

    @Override
    protected List<String> storageStrategies() {
        return List.of("SINGLE_TABLE", "PER_TYPE_TABLE");
    }
}
```

**Test Class Discovery**:
```bash
# Find all relationship query test classes
find relationship-query-integration-tests -name "*RealApi*Test.java" -o -name "*IT.java"
```

**Chunk Definition**:
```java
private static final Class<?>[] BASIC_TESTS = {
    // Simple relationship queries (1-2 hops)
    BasicRelationshipRealApiIT.class,
    OneHopTraversalRealApiIT.class,
    // Add discovered classes
};

private static final Class<?>[] COMPLEX_TESTS = {
    // Complex multi-hop queries
    MultiHopTraversalRealApiIT.class,
    NestedRelationshipRealApiIT.class,
    // Add discovered classes
};

private static final Class<?>[] PERFORMANCE_TESTS = {
    // Large dataset performance tests
    LargeDatasetRealApiIT.class,
    PerformanceBenchmarkRealApiIT.class,
    // Add discovered classes
};
```

#### Task 1.3: Extend Behavior Tests

**New File**: `behavior-integration-tests/src/test/java/BehaviorProviderMatrixIT.java`

```java
package com.ai.infrastructure.behavior;

import com.ai.infrastructure.it.base.AbstractProviderMatrixIT;

public class BehaviorProviderMatrixIT extends AbstractProviderMatrixIT {

    @Override
    protected Class<?>[] suiteTestClasses() {
        String chunk = getTestChunk();
        return switch (chunk) {
            case "analytics" -> ANALYTICS_TESTS;
            case "processing" -> PROCESSING_TESTS;
            case "worker" -> WORKER_TESTS;
            default -> ALL_TESTS;
        };
    }
}
```

**Test Class Discovery**:
```bash
# Find all behavior test classes
find behavior-integration-tests -name "*IT.java" -o -name "*IntegrationIT.java"
```

#### Task 1.4: Create Unified Test Runner

**New File**: `ai-infrastructure-module/integration-Testing/run-unified-realapi-tests.sh`

```bash
#!/bin/bash

###############################################################################
# Unified Real API Integration Test Runner
#
# Runs integration tests across all modules with provider matrix support
#
# Usage:
#   ./run-unified-realapi-tests.sh [OPTIONS]
#
# Options:
#   --matrix SPEC           Provider matrix (llm:emb:vec:storage)
#   --modules MODULES       Comma-separated modules (all, integration-tests, relationship-query, behavior)
#   --chunks CHUNKS         Test chunks (module:chunk,module:chunk or chunk for all)
#   --mode MODE             Selection mode (manual, auto-discover, matrix)
#   --timeout MINUTES       Timeout per module
#   --logging LEVEL         Logging level (quiet, normal, verbose, debug)
#
# Examples:
#   # Run all modules with default provider
#   ./run-unified-realapi-tests.sh
#
#   # Run specific module with chunk
#   ./run-unified-realapi-tests.sh \
#     --matrix "openai:openai:lucene" \
#     --modules "integration-tests" \
#     --chunks "integration-tests:core"
#
#   # Run multiple combinations across all modules
#   ./run-unified-realapi-tests.sh \
#     --matrix "openai:onnx:lucene,openai:openai:lucene,anthropic:openai:pinecone" \
#     --modules "all" \
#     --chunks "all:core"
###############################################################################

# Default values
MATRIX_SPEC="${MATRIX_SPEC:-openai:onnx:lucene}"
MODULES="${MODULES:-all}"
CHUNKS="${CHUNKS:-all:all}"
MODE="${MODE:-manual}"
TIMEOUT="${TIMEOUT:-30}"
LOGGING="${LOGGING:-quiet}"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --matrix) MATRIX_SPEC="$2"; shift 2 ;;
        --modules) MODULES="$2"; shift 2 ;;
        --chunks) CHUNKS="$2"; shift 2 ;;
        --mode) MODE="$2"; shift 2 ;;
        --timeout) TIMEOUT="$2"; shift 2 ;;
        --logging) LOGGING="$2"; shift 2 ;;
        *) echo "Unknown option: $1"; exit 1 ;;
    esac
done

# Function to run module tests
run_module_tests() {
    local module=$1
    local chunk=$2
    local matrix=$3

    echo "Running $module tests (chunk: $chunk, matrix: $matrix)"

    case $module in
        integration-tests)
            cd integration-tests
            bash run-provider-matrix-tests.sh "$matrix" "" "$chunk"
            ;;
        relationship-query)
            cd relationship-query-integration-tests
            # Enhanced script with chunk support
            bash run-relationship-query-realapi-tests.sh "$matrix" "" "$chunk"
            ;;
        behavior)
            cd behavior-integration-tests
            # Enhanced script with chunk support
            bash run-behavior-realapi-tests.sh "$matrix" "" "$chunk"
            ;;
    esac
}

# Main execution logic
# Parse modules and chunks
# Execute tests for each module
# Aggregate results
# Report summary
```

**Benefits**:
- Single entry point for all tests
- Consistent interface across modules
- Easier to use in CI/CD

### Phase 2: Enhanced Provider Discovery (Week 2-3)

#### Task 2.1: Create Provider Configuration Resolver

**New File**: `integration-tests/src/test/java/com/ai/infrastructure/it/config/ProviderConfigurationResolver.java`

```java
package com.ai.infrastructure.it.config;

import java.util.Map;
import java.util.HashMap;

/**
 * Resolves provider-specific configuration automatically
 */
public class ProviderConfigurationResolver {

    /**
     * Auto-configure provider combination
     */
    public static Map<String, String> autoConfigureProviders(
        String llmProvider,
        String embeddingProvider,
        String vectorDb
    ) {
        Map<String, String> config = new HashMap<>();

        // Rule 1: OpenAI/Azure + Lucene dimension reduction
        if (isOpenAIEmbedding(embeddingProvider) && "lucene".equals(vectorDb)) {
            config.put("ai.providers.openai.embedding-dimensions", "512");
            config.put("ai.providers.openai.embedding-model", "text-embedding-3-small");
            log.info("Auto-configured OpenAI embedding dimensions to 512 for Lucene compatibility");
        }

        // Rule 2: Azure OpenAI specific configuration
        if ("azure-openai".equals(llmProvider) || "azure-openai".equals(embeddingProvider)) {
            validateAzureConfig(config);
        }

        // Rule 3: Pinecone configuration
        if ("pinecone".equals(vectorDb)) {
            configurePinecone(config, embeddingProvider);
        }

        return config;
    }

    private static boolean isOpenAIEmbedding(String provider) {
        return "openai".equals(provider) || "azure-openai".equals(provider);
    }

    private static void validateAzureConfig(Map<String, String> config) {
        if (System.getenv("AZURE_OPENAI_API_KEY") == null) {
            throw new IllegalStateException("AZURE_OPENAI_API_KEY not set");
        }
        if (System.getenv("AZURE_OPENAI_ENDPOINT") == null) {
            throw new IllegalStateException("AZURE_OPENAI_ENDPOINT not set");
        }
    }

    private static void configurePinecone(Map<String, String> config, String embeddingProvider) {
        // Pinecone has flexible dimensions, no special config needed
        // But validate API key is present
        if (System.getenv("PINECONE_API_KEY") == null) {
            throw new IllegalStateException("PINECONE_API_KEY not set");
        }
    }
}
```

#### Task 2.2: Implement Provider Discovery Cache

**Enhancement to AbstractProviderMatrixIT**:

```java
private static final Map<String, List<ProviderCombination>> DISCOVERY_CACHE = new ConcurrentHashMap<>();

protected List<ProviderCombination> discoverAvailableCombinations() {
    String cacheKey = discoveryProfile();

    // Check cache first
    if (DISCOVERY_CACHE.containsKey(cacheKey)) {
        log.debug("Using cached provider discovery for profile: {}", cacheKey);
        return DISCOVERY_CACHE.get(cacheKey);
    }

    // Perform discovery
    List<ProviderCombination> combinations = performDiscovery();

    // Cache results
    DISCOVERY_CACHE.put(cacheKey, combinations);

    return combinations;
}
```

**Benefits**:
- Avoid repeated Spring context initialization
- Faster test startup (5-10 seconds saved per run)
- Consistent results across test runs

### Phase 3: GitHub Actions Enhancement (Week 3-4)

#### Task 3.1: Update Workflow Inputs

**File**: `.github/workflows/integration-tests-manual.yml`

**New Input Section**:
```yaml
on:
  workflow_dispatch:
    inputs:
      # New unified matrix input
      provider_matrix:
        description: 'Provider combinations (format: llm:emb:vec:storage, comma-separated)'
        required: false
        default: 'openai:onnx:lucene:SINGLE_TABLE'
        type: string

      # Provider selection mode
      provider_selection_mode:
        description: 'Provider selection mode'
        required: false
        default: 'manual'
        type: choice
        options:
          - manual           # Use provider_matrix input
          - auto-discover    # Discover all available providers

      # Enhanced test chunks
      test_chunks:
        description: 'Test chunks (format: module:chunk,module:chunk or chunk for all)'
        required: false
        default: 'all:all'
        type: string

      # Keep existing inputs for backward compatibility
      modules:
        description: 'Test modules to run'
        required: true
        default: 'all'
        type: choice
        options:
          - all
          - integration-tests
          - relationship-query
          - behavior

      # Legacy inputs (maintained for backward compatibility)
      llm_provider:
        description: 'LLM Provider (legacy, use provider_matrix instead)'
        required: false
        type: choice
        options:
          - openai
          - anthropic
          - azure-openai
          - cohere

      embedding_provider:
        description: 'Embedding Provider (legacy, use provider_matrix instead)'
        required: false
        type: choice
        options:
          - onnx
          - openai
          - azure-openai

      vector_database:
        description: 'Vector Database (legacy, use provider_matrix instead)'
        required: false
        type: choice
        options:
          - lucene
          - pinecone
          - weaviate
          - qdrant
          - milvus
          - memory
```

#### Task 3.2: Enhance Job Steps

**New Step: Convert Legacy Inputs to Matrix**:
```yaml
- name: Convert legacy inputs to provider matrix
  id: convert_inputs
  run: |
    # Check if new provider_matrix input is used
    if [ -n "${{ github.event.inputs.provider_matrix }}" ]; then
      MATRIX="${{ github.event.inputs.provider_matrix }}"
    else
      # Convert legacy inputs to matrix format
      LLM="${{ github.event.inputs.llm_provider }}"
      EMB="${{ github.event.inputs.embedding_provider }}"
      VEC="${{ github.event.inputs.vector_database }}"
      STORAGE="${{ github.event.inputs.storage_strategy }}"
      MATRIX="${LLM}:${EMB}:${VEC}:${STORAGE}"
    fi
    echo "matrix=${MATRIX}" >> $GITHUB_OUTPUT
    echo "Provider matrix: ${MATRIX}"
```

**Enhanced Test Execution Step**:
```yaml
- name: Run Integration Tests (Unified)
  run: |
    cd ai-infrastructure-module/integration-Testing
    bash run-unified-realapi-tests.sh \
      --matrix "${{ steps.convert_inputs.outputs.matrix }}" \
      --modules "${{ github.event.inputs.modules }}" \
      --chunks "${{ github.event.inputs.test_chunks }}" \
      --logging "${{ github.event.inputs.logging_level }}" \
      --timeout "${{ github.event.inputs.timeout_minutes }}"
  env:
    OPENAI_API_KEY: ${{ github.event.inputs.openai_api_key }}
    ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
    AZURE_OPENAI_API_KEY: ${{ secrets.AZURE_OPENAI_API_KEY }}
    AZURE_OPENAI_ENDPOINT: ${{ secrets.AZURE_OPENAI_ENDPOINT }}
```

### Phase 4: Testing & Validation (Week 4)

#### Test Scenarios

##### Scenario 1: Single Provider, All Modules
```yaml
provider_matrix: "openai:onnx:lucene"
modules: "all"
test_chunks: "all:core"
Expected: Run core tests across all 3 modules
Duration: ~5-10 minutes
```

##### Scenario 2: Multiple Providers, Single Module
```yaml
provider_matrix: "openai:onnx:lucene,openai:openai:lucene,anthropic:openai:pinecone"
modules: "integration-tests"
test_chunks: "integration-tests:core,vector"
Expected: Run core+vector chunks with 3 provider combinations
Duration: ~15-25 minutes
```

##### Scenario 3: Targeted Chunk Testing
```yaml
provider_matrix: "openai:openai:lucene"
modules: "all"
test_chunks: "integration-tests:vector,relationship-query:basic,behavior:analytics"
Expected: Run specific chunks for each module
Duration: ~10-15 minutes
```

##### Scenario 4: Auto-Discovery Mode
```yaml
provider_selection_mode: "auto-discover"
modules: "integration-tests"
test_chunks: "integration-tests:core"
Expected: Discover all available providers, run core tests with each
Duration: Varies based on available providers
```

---

## Code Examples

### Example 1: Extending AbstractProviderMatrixIT

```java
package com.ai.infrastructure.relationshipquery;

import com.ai.infrastructure.it.base.AbstractProviderMatrixIT;
import org.springframework.util.StringUtils;
import java.util.List;

public class RelationshipQueryProviderMatrixIT extends AbstractProviderMatrixIT {

    // Define test chunks
    private static final Class<?>[] BASIC_TESTS = {
        BasicRelationshipRealApiIT.class,
        SimpleQueryRealApiIT.class,
        OneHopTraversalRealApiIT.class
    };

    private static final Class<?>[] COMPLEX_TESTS = {
        MultiHopTraversalRealApiIT.class,
        NestedRelationshipRealApiIT.class,
        ComplexFilteringRealApiIT.class
    };

    private static final Class<?>[] PERFORMANCE_TESTS = {
        LargeDatasetRealApiIT.class,
        PerformanceBenchmarkRealApiIT.class
    };

    private static final Class<?>[] ALL_TESTS = {
        // Combine all chunks
        BasicRelationshipRealApiIT.class,
        SimpleQueryRealApiIT.class,
        OneHopTraversalRealApiIT.class,
        MultiHopTraversalRealApiIT.class,
        NestedRelationshipRealApiIT.class,
        ComplexFilteringRealApiIT.class,
        LargeDatasetRealApiIT.class,
        PerformanceBenchmarkRealApiIT.class
    };

    @Override
    protected Class<?>[] suiteTestClasses() {
        String chunk = System.getProperty("ai.providers.real-api.test-chunk");
        if (!StringUtils.hasText(chunk)) {
            chunk = System.getenv("AI_PROVIDERS_REAL_API_TEST_CHUNK");
        }

        if (!StringUtils.hasText(chunk) || "all".equalsIgnoreCase(chunk)) {
            return ALL_TESTS;
        }

        return switch (chunk.toLowerCase()) {
            case "basic" -> BASIC_TESTS;
            case "complex" -> COMPLEX_TESTS;
            case "performance" -> PERFORMANCE_TESTS;
            default -> ALL_TESTS;
        };
    }

    @Override
    protected String discoveryProfile() {
        return "realapi";
    }

    @Override
    protected List<String> storageStrategies() {
        // Relationship query tests both storage strategies
        return List.of("SINGLE_TABLE", "PER_TYPE_TABLE");
    }

    @Override
    protected Map<String, Object> additionalDiscoveryProperties() {
        String indexPath = "data/test-lucene-index/relationship-query-" + System.nanoTime();
        return Map.of(
            "spring.liquibase.enabled", "false",
            "ai.config.default-file", "ai-entity-config.yml",
            "ai.vector-db.lucene.index-path", indexPath
        );
    }
}
```

### Example 2: Enhanced Test Runner Script

```bash
#!/bin/bash

run_module_tests() {
    local module=$1
    local chunk=$2
    local matrix=$3

    echo "========================================"
    echo "Module: $module"
    echo "Chunk: $chunk"
    echo "Matrix: $matrix"
    echo "========================================"

    case $module in
        integration-tests)
            cd integration-tests || exit 1
            bash run-provider-matrix-tests.sh "$matrix" "" "$chunk"
            local result=$?
            cd ..
            return $result
            ;;

        relationship-query)
            cd relationship-query-integration-tests || exit 1
            # Use new enhanced script with chunk support
            bash run-relationship-query-realapi-tests.sh "$matrix" "" "$chunk"
            local result=$?
            cd ..
            return $result
            ;;

        behavior)
            cd behavior-integration-tests || exit 1
            # Use new enhanced script with chunk support
            bash run-behavior-realapi-tests.sh "$matrix" "" "$chunk"
            local result=$?
            cd ..
            return $result
            ;;

        *)
            echo "Unknown module: $module"
            return 1
            ;;
    esac
}

# Parse chunks for specific module
parse_chunk_for_module() {
    local module=$1
    local chunk_spec=$2

    # Format: "module:chunk,module:chunk" or "chunk" for all modules

    # Check if chunk_spec contains module-specific chunks
    if echo "$chunk_spec" | grep -q ":"; then
        # Extract chunk for specific module
        local module_chunk=$(echo "$chunk_spec" | tr ',' '\n' | grep "^${module}:" | cut -d':' -f2)
        if [ -n "$module_chunk" ]; then
            echo "$module_chunk"
        else
            echo "all"
        fi
    else
        # Same chunk for all modules
        echo "$chunk_spec"
    fi
}

# Main execution
MODULES_TO_RUN=$(echo "$MODULES" | tr ',' ' ')
SUCCESS_COUNT=0
FAILURE_COUNT=0

for module in $MODULES_TO_RUN; do
    if [ "$module" == "all" ]; then
        MODULES_TO_RUN="integration-tests relationship-query behavior"
        break
    fi
done

for module in $MODULES_TO_RUN; do
    chunk=$(parse_chunk_for_module "$module" "$CHUNKS")

    if run_module_tests "$module" "$chunk" "$MATRIX_SPEC"; then
        ((SUCCESS_COUNT++))
        echo "✅ $module tests PASSED"
    else
        ((FAILURE_COUNT++))
        echo "❌ $module tests FAILED"
    fi
done

echo ""
echo "========================================"
echo "Test Summary"
echo "========================================"
echo "Modules run: $((SUCCESS_COUNT + FAILURE_COUNT))"
echo "Successes: $SUCCESS_COUNT"
echo "Failures: $FAILURE_COUNT"
echo "========================================"

if [ $FAILURE_COUNT -gt 0 ]; then
    exit 1
fi
```

---

## Performance Optimization

### Chunk-Based Parallel Execution

**Current**: Sequential execution of all tests
**Proposed**: Parallel execution of independent chunks

**GitHub Actions Matrix Strategy**:
```yaml
strategy:
  matrix:
    chunk:
      - core
      - vector
      - intent-actions
      - advanced
  fail-fast: false
  max-parallel: 4

steps:
  - name: Run chunk tests
    run: |
      bash run-provider-matrix-tests.sh \
        "${{ github.event.inputs.provider_matrix }}" \
        "" \
        "${{ matrix.chunk }}"
```

**Benefits**:
- 4x faster execution (parallel chunks)
- Fail-fast option for quick feedback
- Independent chunk execution

### Provider Discovery Optimization

**Current**: Discovery on every test run
**Proposed**: Cache discovery results

**Implementation**:
```java
// Cache discovery for 1 hour
private static final Duration CACHE_TTL = Duration.ofHours(1);
private static final Map<String, CachedDiscovery> CACHE = new ConcurrentHashMap<>();

static class CachedDiscovery {
    List<ProviderCombination> combinations;
    Instant timestamp;

    boolean isExpired() {
        return Duration.between(timestamp, Instant.now()).compareTo(CACHE_TTL) > 0;
    }
}
```

**Benefits**:
- 5-10 seconds saved per test run
- Consistent results within cache window
- Automatic expiration for fresh discovery

### Smart Chunk Selection

**Algorithm**:
```java
public List<String> selectOptimalChunks(Duration maxDuration, List<ProviderCombination> providers) {
    List<ChunkInfo> chunks = getAllChunks();

    // Filter by provider compatibility
    chunks = chunks.stream()
        .filter(chunk -> isCompatibleWithProviders(chunk, providers))
        .collect(toList());

    // Sort by priority (core first, advanced last)
    chunks.sort(Comparator.comparing(ChunkInfo::getPriority));

    // Select chunks that fit within time budget
    List<String> selected = new ArrayList<>();
    Duration totalTime = Duration.ZERO;

    for (ChunkInfo chunk : chunks) {
        Duration chunkDuration = chunk.getEstimatedDuration();
        if (totalTime.plus(chunkDuration).compareTo(maxDuration) <= 0) {
            selected.add(chunk.getName());
            totalTime = totalTime.plus(chunkDuration);
        }
    }

    return selected;
}
```

**Usage in Workflow**:
```yaml
- name: Smart chunk selection
  run: |
    # Calculate optimal chunks based on timeout
    TIMEOUT=${{ github.event.inputs.timeout_minutes }}
    CHUNKS=$(./select-optimal-chunks.sh --timeout $TIMEOUT --providers "$MATRIX")
    echo "Selected chunks: $CHUNKS"
    echo "chunks=$CHUNKS" >> $GITHUB_OUTPUT
```

---

## Testing Strategy

### Unit Testing the Test Framework

**Test Coverage**:
1. Provider matrix parsing
2. Chunk selection logic
3. Auto-configuration rules
4. Provider discovery
5. Cache management

**Example Test**:
```java
@Test
void testProviderMatrixParsing() {
    String matrixSpec = "openai:onnx:lucene,anthropic:openai:pinecone";
    List<ProviderCombination> combinations = parseMatrixSpec(matrixSpec);

    assertThat(combinations).hasSize(2);
    assertThat(combinations.get(0).llmProvider()).isEqualTo("openai");
    assertThat(combinations.get(0).embeddingProvider()).isEqualTo("onnx");
    assertThat(combinations.get(0).vectorDbProvider()).isEqualTo("lucene");
}

@Test
void testAutoConfiguration_OpenAILucene() {
    Map<String, String> config = ProviderConfigurationResolver.autoConfigureProviders(
        "openai", "openai", "lucene"
    );

    assertThat(config).containsEntry("ai.providers.openai.embedding-dimensions", "512");
}

@Test
void testChunkSelection_TimeConstraint() {
    Duration maxDuration = Duration.ofMinutes(10);
    List<String> chunks = selectOptimalChunks(maxDuration, defaultProviders());

    assertThat(chunks).contains("core");
    assertThat(getTotalDuration(chunks)).isLessThanOrEqualTo(maxDuration);
}
```

### Integration Testing

**Test Scenarios**:
1. Single provider, single module, single chunk
2. Multiple providers, single module, all chunks
3. Single provider, all modules, targeted chunks
4. Auto-discovery mode
5. Legacy input compatibility

**Test Matrix**:
```bash
# Scenario 1: Smoke test
./run-unified-realapi-tests.sh \
  --matrix "openai:onnx:lucene" \
  --modules "integration-tests" \
  --chunks "core"

# Scenario 2: Full matrix
./run-unified-realapi-tests.sh \
  --matrix "openai:onnx:lucene,openai:openai:lucene,anthropic:openai:pinecone" \
  --modules "all" \
  --chunks "all:all"

# Scenario 3: Targeted chunks
./run-unified-realapi-tests.sh \
  --matrix "openai:onnx:lucene" \
  --modules "all" \
  --chunks "integration-tests:core,relationship-query:basic,behavior:analytics"
```

### Validation Checklist

**Before merging**:
- [ ] All existing tests pass with new framework
- [ ] Backward compatibility maintained (legacy inputs work)
- [ ] Auto-configuration works for all provider combinations
- [ ] Chunk selection correctly filters tests
- [ ] Provider discovery cached and refreshed correctly
- [ ] Documentation updated
- [ ] Performance benchmarks show improvement
- [ ] GitHub Actions workflow validated

---

## Migration Strategy

### Step 1: Parallel Implementation (Week 1)
- Implement new unified runner
- Keep existing scripts functional
- No changes to existing tests

### Step 2: Add Matrix Support to Modules (Week 2)
- Create `RelationshipQueryProviderMatrixIT`
- Create `BehaviorProviderMatrixIT`
- Test with subset of providers

### Step 3: Update Workflow (Week 3)
- Add new workflow inputs
- Maintain backward compatibility
- Test with legacy and new inputs

### Step 4: Full Migration (Week 4)
- Update all module scripts to use unified runner
- Update documentation
- Deprecate old individual scripts

### Step 5: Cleanup (Future)
- Remove deprecated scripts
- Remove legacy workflow inputs
- Final documentation update

---

## Appendix

### Quick Reference

**Common Commands**:
```bash
# Default run (openai:onnx:lucene, all modules, all tests)
./run-unified-realapi-tests.sh

# Quick smoke test (core tests only)
./run-unified-realapi-tests.sh --chunks "all:core"

# Test specific provider combination
./run-unified-realapi-tests.sh --matrix "anthropic:openai:pinecone"

# Test with multiple providers
./run-unified-realapi-tests.sh --matrix "openai:onnx:lucene,openai:openai:lucene"

# Module-specific chunks
./run-unified-realapi-tests.sh --chunks "integration-tests:vector,relationship-query:complex"

# Auto-discover providers
./run-unified-realapi-tests.sh --mode auto-discover --chunks "all:core"
```

**Environment Variables**:
```bash
# Required
export OPENAI_API_KEY="sk-..."

# Optional (for specific providers)
export ANTHROPIC_API_KEY="sk-ant-..."
export AZURE_OPENAI_API_KEY="..."
export AZURE_OPENAI_ENDPOINT="https://..."
export PINECONE_API_KEY="..."
export PINECONE_ENVIRONMENT="us-east-1-aws"

# Optional (for configuration)
export MAVEN_LOGGING_LEVEL="quiet"  # quiet, normal, verbose, debug
export AI_INFRASTRUCTURE_PERSISTENCE_DATABASE="h2"  # h2, postgresql
```

**Troubleshooting**:
```bash
# Check available providers
./run-unified-realapi-tests.sh --mode auto-discover --dry-run

# Verbose logging for debugging
./run-unified-realapi-tests.sh --logging debug

# Test single chunk with verbose output
./run-unified-realapi-tests.sh \
  --modules "integration-tests" \
  --chunks "core" \
  --logging verbose
```

---

**Document Version**: 1.0
**Last Updated**: 2026-01-08
**Status**: Implementation Guide
**Related Documents**:
- `REALAPI_TESTS_RESTRUCTURING_PLAN.md` - Strategic plan
- `REALAPI_TESTS_RESTRUCTURING_SUMMARY.md` - Executive summary
