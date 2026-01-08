# Real API Tests Restructuring Plan

## Executive Summary

This document outlines a comprehensive plan to restructure the Real API test execution flow across all integration test modules (integration-tests, relationship-query, behavior) to:
1. **Support extensible provider matrix** (LLM, Embedding, Vector DB)
2. **Enable flexible test chunking** for fast, targeted execution
3. **Unify test execution** across all modules
4. **Simplify GitHub Actions workflow** configuration

---

## Current State Analysis

### 1. Test Modules Structure

```
integration-Testing/
├── integration-tests/              # Main AI Infrastructure tests
│   ├── run-provider-matrix-tests.sh
│   └── RealAPIProviderMatrixIntegrationTest.java
├── relationship-query-integration-tests/
│   └── run-relationship-query-realapi-tests.sh
└── behavior-integration-tests/
    └── run-behavior-realapi-tests.sh
```

### 2. Current Execution Flow

#### AI Infrastructure Tests (`integration-tests`)
- **Script**: `run-provider-matrix-tests.sh`
- **Test Class**: `RealAPIProviderMatrixIntegrationTest`
- **Matrix Format**: `llm:embedding:vectordb:storageStrategy`
- **Chunking**: ✅ Supported (core, vector, intent-actions, advanced, all)
- **Provider Discovery**: ✅ Automatic via Spring context
- **Matrix Spec**: ✅ Supports comma-separated combinations

#### Relationship Query Tests
- **Script**: `run-relationship-query-realapi-tests.sh`
- **Matrix Format**: `llm:embedding:vectordb`
- **Chunking**: ❌ Not supported
- **Provider Discovery**: ❌ Manual parsing only
- **Matrix Spec**: ❌ Single combination only

#### Behavior Tests
- **Script**: `run-behavior-realapi-tests.sh`
- **Matrix Format**: `llm:embedding:vectordb`
- **Chunking**: ❌ Not supported
- **Provider Discovery**: ❌ Manual parsing only
- **Matrix Spec**: ❌ Single combination only

### 3. Current Limitations

#### ❌ **Inconsistent Matrix Support**
- Only `integration-tests` supports full matrix (multiple combinations, storage strategies)
- Relationship-query and behavior only support single combination
- No support for comma-separated combinations in relationship-query/behavior

#### ❌ **No Unified Test Chunking**
- Chunking only exists in `integration-tests`
- Relationship-query and behavior run all tests or nothing
- No way to run targeted subsets across modules

#### ❌ **Provider Discovery Limitations**
- Only `integration-tests` auto-discovers available providers
- Relationship-query and behavior require manual provider specification
- No way to test all available provider combinations automatically

#### ❌ **GitHub Actions Workflow Complexity**
- Each module has separate job with duplicate configuration
- Test chunk selection only applies to `integration-tests`
- No unified way to run same provider combination across all modules
- Limited provider options in UI (hardcoded list)

#### ❌ **No Cross-Module Test Coordination**
- Cannot run same provider combination across all modules in one command
- No way to ensure consistency across modules
- Separate timeout/configuration per module

---

## Proposed Restructuring Strategy

### Phase 1: Unified Test Execution Framework

#### 1.1 Create Unified Test Runner Script

**New Script**: `integration-Testing/run-unified-realapi-tests.sh`

**Features**:
- Accepts provider matrix spec (same format across all modules)
- Supports test chunk selection per module
- Can run single module or all modules
- Auto-discovers available providers
- Handles provider-specific configuration (e.g., OpenAI dimension reduction)

**Usage**:
```bash
# Run all modules with same provider combination
./run-unified-realapi-tests.sh \
  --matrix "openai:openai:lucene" \
  --modules "all" \
  --chunks "integration-tests:core,relationship-query:all,behavior:all"

# Run specific module with chunk
./run-unified-realapi-tests.sh \
  --matrix "anthropic:openai:pinecone" \
  --modules "integration-tests" \
  --chunks "integration-tests:vector"

# Run multiple provider combinations
./run-unified-realapi-tests.sh \
  --matrix "openai:onnx:lucene,openai:openai:lucene,anthropic:openai:pinecone" \
  --modules "all"
```

#### 1.2 Extend Matrix Support to All Modules

**Relationship Query**:
- Create `RelationshipQueryProviderMatrixIntegrationTest` extending `AbstractProviderMatrixIntegrationTest`
- Support multiple combinations via matrix spec
- Add test chunking support

**Behavior**:
- Create `BehaviorProviderMatrixIntegrationTest` extending `AbstractProviderMatrixIntegrationTest`
- Support multiple combinations via matrix spec
- Add test chunking support

### Phase 2: Enhanced Provider Discovery & Configuration

#### 2.1 Dynamic Provider Discovery

**Current**: Only `integration-tests` auto-discovers providers
**Proposed**: All modules auto-discover available providers

**Implementation**:
- Extend `AbstractProviderMatrixIntegrationTest.discoverAvailableCombinations()`
- Cache discovered providers to avoid repeated Spring context initialization
- Support provider filtering via environment variables or properties

#### 2.2 Provider-Specific Auto-Configuration

**Current**: Manual dimension reduction for OpenAI + Lucene
**Proposed**: Automatic provider-specific configuration

**Examples**:
- OpenAI + Lucene → Auto-set `embedding-dimensions=512`
- Azure OpenAI + Lucene → Auto-set `embedding-dimensions=512`
- ONNX + Any Vector DB → No special config needed
- Pinecone → Auto-configure dimensions based on embedding provider

**Implementation**:
- Create `ProviderConfigurationResolver` utility
- Auto-detect incompatible combinations and apply fixes
- Log configuration changes for transparency

#### 2.3 Extensible Provider Matrix

**Current**: Hardcoded provider lists in GitHub Actions UI
**Proposed**: Dynamic provider discovery + extensible matrix

**Options**:
1. **Discovery Mode**: Auto-discover all available providers
2. **Manual Mode**: Specify exact combinations
3. **Matrix Mode**: Generate cartesian product of specified providers

**GitHub Actions Enhancement**:
```yaml
provider_selection_mode:
  description: 'Provider selection mode'
  type: choice
  options:
    - auto-discover    # Discover all available providers
    - manual           # Specify exact combinations
    - matrix           # Generate matrix from provider lists
```

### Phase 3: Intelligent Test Chunking

#### 3.1 Unified Chunking Strategy

**Current Chunks** (integration-tests only):
- `core`: Basic functionality (4 classes)
- `vector`: Vector operations (3 classes)
- `intent-actions`: Intent & Actions (4 classes)
- `advanced`: Advanced features (4 classes)
- `all`: All tests (15 classes)

**Proposed Chunks** (all modules):

**Integration Tests**:
- `core`, `vector`, `intent-actions`, `advanced`, `all`

**Relationship Query**:
- `basic`: Basic relationship queries
- `complex`: Complex traversal queries
- `performance`: Performance tests
- `all`: All tests

**Behavior**:
- `analytics`: Analytics API tests
- `processing`: Processing tests
- `worker`: Worker tests
- `all`: All tests

#### 3.2 Smart Chunk Selection

**Strategy 1: Time-Based Chunking**
- Estimate execution time per chunk
- Select chunks that fit within timeout
- Prioritize critical chunks first

**Strategy 2: Dependency-Based Chunking**
- Identify test dependencies
- Run prerequisite chunks first
- Skip dependent chunks if prerequisites fail

**Strategy 3: Provider-Specific Chunking**
- Some chunks only relevant for specific providers
- Skip chunks that don't apply to selected provider
- Example: Vector chunks only for vector DB providers

**Implementation**:
```java
public enum TestChunk {
    CORE("core", Set.of("all"), Duration.ofMinutes(5)),
    VECTOR("vector", Set.of("lucene", "pinecone", "weaviate"), Duration.ofMinutes(10)),
    INTENT_ACTIONS("intent-actions", Set.of("all"), Duration.ofMinutes(8)),
    ADVANCED("advanced", Set.of("all"), Duration.ofMinutes(12));
    
    private final String name;
    private final Set<String> applicableProviders;
    private final Duration estimatedDuration;
}
```

#### 3.3 Chunk Selection UI

**GitHub Actions Enhancement**:
```yaml
test_chunks:
  description: 'Test chunks to run (comma-separated, module:chunk format)'
  required: false
  default: 'all:all'
  type: string
  # Examples:
  #   "integration-tests:core,relationship-query:all"
  #   "all:core"  # core chunk for all modules
  #   "integration-tests:vector,intent-actions"
```

### Phase 4: GitHub Actions Workflow Restructuring

#### 4.1 Unified Workflow Structure

**Current**: 3 separate jobs (ai-infrastructure, relationship-query, behavior)
**Proposed**: Single unified job with matrix strategy

**Option A: Single Job with Module Matrix**
```yaml
strategy:
  matrix:
    module: [ai-infrastructure, relationship-query, behavior]
    include:
      - module: ai-infrastructure
        test_chunk: ${{ github.event.inputs.test_chunk }}
      - module: relationship-query
        test_chunk: all
      - module: behavior
        test_chunk: all
```

**Option B: Parallel Jobs with Shared Configuration**
- Keep separate jobs for better isolation
- Share common configuration via reusable workflow
- Unified test summary aggregation

**Recommended**: **Option B** (better isolation, easier debugging)

#### 4.2 Enhanced Workflow Inputs

**Current Inputs**:
- `llm_provider`: Single choice
- `embedding_provider`: Single choice
- `vector_database`: Single choice
- `test_chunk`: Single choice (only for integration-tests)

**Proposed Inputs**:
```yaml
provider_matrix:
  description: 'Provider combinations (comma-separated, format: llm:embedding:vectordb:storage)'
  required: false
  default: 'openai:onnx:lucene:SINGLE_TABLE'
  type: string
  # Examples:
  #   "openai:onnx:lucene"
  #   "openai:openai:lucene,anthropic:openai:pinecone"
  #   "openai:onnx:lucene:SINGLE_TABLE,openai:onnx:lucene:PER_TYPE_TABLE"

provider_selection_mode:
  description: 'How to select providers'
  type: choice
  options:
    - manual           # Use provider_matrix input
    - auto-discover    # Discover all available combinations
    - matrix           # Generate matrix from provider lists

test_chunks:
  description: 'Test chunks (format: module:chunk or chunk for all modules)'
  required: false
  default: 'all:all'
  type: string
  # Examples:
  #   "integration-tests:core"
  #   "all:core"
  #   "integration-tests:vector,relationship-query:all"
```

#### 4.3 Provider Auto-Configuration in Workflow

**Add Step**: Auto-configure provider-specific settings
```yaml
- name: Auto-configure Provider Settings
  run: |
    # Auto-detect OpenAI + Lucene and set dimensions
    if [[ "${{ github.event.inputs.embedding_provider }}" == "openai" ]] && \
       [[ "${{ github.event.inputs.vector_database }}" == "lucene" ]]; then
      echo "AI_PROVIDERS_OPENAI_EMBEDDING_DIMENSIONS=512" >> $GITHUB_ENV
    fi
```

---

## Implementation Plan

### Phase 1: Foundation (Week 1-2)

#### Task 1.1: Create Unified Test Runner
- [ ] Create `run-unified-realapi-tests.sh`
- [ ] Support matrix spec parsing
- [ ] Support module selection
- [ ] Support chunk selection per module
- [ ] Add provider auto-configuration logic

#### Task 1.2: Extend Matrix Support
- [ ] Create `RelationshipQueryProviderMatrixIntegrationTest`
- [ ] Create `BehaviorProviderMatrixIntegrationTest`
- [ ] Add test chunking to relationship-query
- [ ] Add test chunking to behavior

**Deliverables**:
- Unified test runner script
- Matrix support in all modules
- Test chunking in all modules

### Phase 2: Provider Discovery (Week 2-3)

#### Task 2.1: Enhanced Provider Discovery
- [ ] Cache provider discovery results
- [ ] Support provider filtering
- [ ] Add provider validation

#### Task 2.2: Auto-Configuration
- [ ] Create `ProviderConfigurationResolver`
- [ ] Implement dimension reduction logic
- [ ] Add configuration logging

**Deliverables**:
- Cached provider discovery
- Automatic provider configuration
- Configuration validation

### Phase 3: GitHub Actions Enhancement (Week 3-4)

#### Task 3.1: Workflow Restructuring
- [ ] Update workflow inputs
- [ ] Add provider auto-configuration step
- [ ] Update test execution steps
- [ ] Enhance test summary

#### Task 3.2: Documentation
- [ ] Update workflow documentation
- [ ] Create provider matrix guide
- [ ] Document test chunking strategy

**Deliverables**:
- Updated GitHub Actions workflow
- Comprehensive documentation

### Phase 4: Testing & Validation (Week 4)

#### Task 4.1: Test New Framework
- [ ] Test unified runner with all modules
- [ ] Test provider matrix combinations
- [ ] Test chunk selection
- [ ] Validate auto-configuration

#### Task 4.2: Performance Validation
- [ ] Measure execution time improvements
- [ ] Validate chunk time estimates
- [ ] Optimize slow combinations

**Deliverables**:
- Validated test framework
- Performance metrics
- Optimization recommendations

---

## Test Chunking Strategy

### Chunk Selection Criteria

#### 1. **By Test Type**
- **Core**: Basic CRUD, basic queries
- **Vector**: Vector operations, similarity search
- **Intent**: Intent extraction, action handling
- **Advanced**: Complex scenarios, edge cases

#### 2. **By Provider Dependency**
- **Provider-Agnostic**: Run with any provider
- **Provider-Specific**: Only run with specific providers
- **Vector-DB-Specific**: Only run with vector databases

#### 3. **By Execution Time**
- **Fast** (< 2 min): Core, basic tests
- **Medium** (2-5 min): Vector, intent tests
- **Slow** (> 5 min): Advanced, performance tests

### Chunk Selection Algorithm

```java
public List<TestChunk> selectChunks(
    String chunkSpec,
    List<ProviderCombination> providers,
    Duration maxDuration
) {
    // Parse chunk spec (e.g., "core,vector" or "all")
    List<String> requestedChunks = parseChunkSpec(chunkSpec);
    
    // Filter by provider compatibility
    List<TestChunk> compatibleChunks = requestedChunks.stream()
        .map(this::getChunk)
        .filter(chunk -> isCompatible(chunk, providers))
        .collect(toList());
    
    // Filter by time constraints
    if (maxDuration != null) {
        compatibleChunks = filterByTime(compatibleChunks, maxDuration);
    }
    
    return compatibleChunks;
}
```

### Chunk Execution Order

1. **Core** → Foundation tests, must pass first
2. **Vector** → Vector-specific tests
3. **Intent-Actions** → Intent and action tests
4. **Advanced** → Complex scenarios

**Rationale**: Fail fast on core tests, skip dependent tests if core fails.

---

## Provider Extensibility Strategy

### Adding New Providers

#### Step 1: Implement Provider
- Create provider implementation
- Register as Spring bean
- Implement `isAvailable()` method

#### Step 2: Auto-Discovery
- Provider automatically discovered by `AbstractProviderMatrixIntegrationTest`
- No code changes needed in test framework

#### Step 3: Configuration (if needed)
- Add provider-specific config to `ProviderConfigurationResolver`
- Handle dimension limits, API keys, etc.

#### Step 4: GitHub Actions (optional)
- Add to workflow UI options (for manual selection)
- Or rely on auto-discovery

### Provider Compatibility Matrix

| LLM Provider | Embedding Provider | Vector DB | Compatible? | Notes |
|--------------|-------------------|-----------|-------------|-------|
| openai | openai | lucene | ✅ | Requires dimension reduction |
| openai | onnx | lucene | ✅ | Default, always works |
| anthropic | openai | pinecone | ✅ | No dimension limits |
| azure-openai | azure-openai | lucene | ✅ | Requires dimension reduction |
| any | onnx | any | ✅ | ONNX works with all |

**Auto-Configuration Rules**:
- OpenAI/Azure + Lucene → Set `embedding-dimensions=512`
- ONNX + Any → No special config
- Pinecone/Weaviate/Qdrant → No dimension limits

---

## Benefits of Restructuring

### 1. **Consistency**
- ✅ Same matrix format across all modules
- ✅ Same chunking strategy across all modules
- ✅ Unified execution flow

### 2. **Flexibility**
- ✅ Run any provider combination
- ✅ Run targeted test chunks
- ✅ Mix and match modules

### 3. **Extensibility**
- ✅ Easy to add new providers
- ✅ Easy to add new test chunks
- ✅ Easy to add new modules

### 4. **Efficiency**
- ✅ Faster execution with chunking
- ✅ Parallel execution across modules
- ✅ Smart provider selection

### 5. **Maintainability**
- ✅ Single unified runner
- ✅ Shared test infrastructure
- ✅ Centralized configuration

---

## Migration Path

### Step 1: Parallel Implementation
- Keep existing scripts working
- Implement new unified runner alongside
- Test new runner with subset of tests

### Step 2: Gradual Migration
- Migrate one module at a time
- Update GitHub Actions to use new runner
- Keep old scripts as fallback

### Step 3: Full Migration
- Remove old scripts
- Update all documentation
- Final validation

---

## Success Metrics

### Execution Time
- **Target**: 50% reduction in CI execution time via chunking
- **Measure**: Compare full run vs. chunked run

### Test Coverage
- **Target**: 100% provider combination coverage
- **Measure**: Number of provider combinations tested

### Developer Experience
- **Target**: Single command to run any test combination
- **Measure**: Number of commands needed for common scenarios

### Maintainability
- **Target**: Single script to maintain instead of 3
- **Measure**: Lines of code, complexity

---

## Next Steps

1. **Review & Approve Plan**: Get stakeholder approval
2. **Create Implementation Issues**: Break down into tasks
3. **Start Phase 1**: Implement unified test runner
4. **Iterate**: Get feedback, adjust plan as needed

---

## Appendix: Example Commands

### Unified Runner Examples

```bash
# Run all modules with default provider
./run-unified-realapi-tests.sh --modules all

# Run specific module with specific provider
./run-unified-realapi-tests.sh \
  --matrix "openai:openai:lucene" \
  --modules "integration-tests" \
  --chunks "core"

# Run multiple provider combinations
./run-unified-realapi-tests.sh \
  --matrix "openai:onnx:lucene,openai:openai:lucene,anthropic:openai:pinecone" \
  --modules "all" \
  --chunks "all:core"

# Run with auto-discovery
./run-unified-realapi-tests.sh \
  --mode auto-discover \
  --modules "integration-tests" \
  --chunks "core,vector"
```

### GitHub Actions Examples

```yaml
# Single provider combination
provider_matrix: "openai:onnx:lucene"
test_chunks: "all:core"

# Multiple provider combinations
provider_matrix: "openai:onnx:lucene,openai:openai:lucene"
test_chunks: "integration-tests:core,relationship-query:all"

# Auto-discover all combinations
provider_selection_mode: "auto-discover"
test_chunks: "all:core"
```

---

**Document Version**: 1.0  
**Last Updated**: 2026-01-08  
**Author**: AI Infrastructure Team
