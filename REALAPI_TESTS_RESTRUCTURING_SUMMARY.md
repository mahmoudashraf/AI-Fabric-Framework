# Real API Tests Restructuring - Executive Summary

## Current State Issues

### ❌ **Inconsistency Across Modules**
- Only `integration-tests` supports full provider matrix
- Relationship-query and behavior only support single combinations
- No unified test chunking strategy

### ❌ **Limited Extensibility**
- Hardcoded provider lists in GitHub Actions
- Manual provider configuration required
- No auto-discovery for relationship-query/behavior modules

### ❌ **Inefficient Execution**
- No way to run targeted test subsets
- All tests run even when only specific functionality changed
- No cross-module coordination

---

## Proposed Solution

### 🎯 **Unified Test Execution Framework**

**Key Components**:
1. **Unified Runner Script** (`run-unified-realapi-tests.sh`)
   - Single script for all modules
   - Supports provider matrix across all modules
   - Supports test chunking per module

2. **Extended Matrix Support**
   - All modules support provider matrix
   - All modules support test chunking
   - Consistent format: `llm:embedding:vectordb:storage`

3. **Auto-Discovery & Configuration**
   - Auto-discover available providers
   - Auto-configure provider-specific settings (e.g., dimension reduction)
   - Validate provider combinations

4. **Enhanced GitHub Actions**
   - Unified workflow inputs
   - Support for multiple provider combinations
   - Flexible test chunk selection

---

## Key Benefits

| Benefit | Current | Proposed |
|---------|---------|----------|
| **Consistency** | 3 different scripts | 1 unified script |
| **Matrix Support** | 1 module | All modules |
| **Test Chunking** | 1 module | All modules |
| **Provider Discovery** | Manual | Automatic |
| **Execution Time** | Full suite always | Targeted chunks |
| **Extensibility** | Hardcoded | Dynamic |

---

## Implementation Phases

### Phase 1: Foundation (Week 1-2)
- ✅ Create unified test runner
- ✅ Extend matrix support to all modules
- ✅ Add test chunking to all modules

### Phase 2: Provider Discovery (Week 2-3)
- ✅ Enhanced provider discovery
- ✅ Auto-configuration system

### Phase 3: GitHub Actions (Week 3-4)
- ✅ Workflow restructuring
- ✅ Enhanced inputs and configuration

### Phase 4: Testing & Validation (Week 4)
- ✅ Framework validation
- ✅ Performance optimization

---

## Test Chunking Strategy

### Current Chunks (integration-tests only)
- `core`: Basic functionality (4 classes)
- `vector`: Vector operations (3 classes)
- `intent-actions`: Intent & Actions (4 classes)
- `advanced`: Advanced features (4 classes)

### Proposed Chunks (all modules)

**Integration Tests**: `core`, `vector`, `intent-actions`, `advanced`, `all`

**Relationship Query**: `basic`, `complex`, `performance`, `all`

**Behavior**: `analytics`, `processing`, `worker`, `all`

### Chunk Selection
- **By Test Type**: Core, Vector, Intent, Advanced
- **By Provider Dependency**: Provider-agnostic vs. provider-specific
- **By Execution Time**: Fast (< 2 min), Medium (2-5 min), Slow (> 5 min)

---

## Provider Extensibility

### Adding New Providers

1. **Implement Provider** → Register as Spring bean
2. **Auto-Discovery** → Automatically discovered by test framework
3. **Configuration** → Add to `ProviderConfigurationResolver` if needed
4. **GitHub Actions** → Optional: Add to UI or rely on auto-discovery

### Auto-Configuration Rules

- **OpenAI/Azure + Lucene** → Auto-set `embedding-dimensions=512`
- **ONNX + Any** → No special config needed
- **Pinecone/Weaviate/Qdrant** → No dimension limits

---

## GitHub Actions Enhancement

### Current Inputs
```yaml
llm_provider: openai
embedding_provider: onnx
vector_database: lucene
test_chunk: all  # Only for integration-tests
```

### Proposed Inputs
```yaml
provider_matrix: "openai:onnx:lucene:SINGLE_TABLE"
# Or multiple: "openai:onnx:lucene,openai:openai:lucene"

provider_selection_mode: manual | auto-discover | matrix

test_chunks: "all:all"
# Or specific: "integration-tests:core,relationship-query:all"
```

---

## Success Metrics

- **Execution Time**: 50% reduction via chunking
- **Test Coverage**: 100% provider combination coverage
- **Developer Experience**: Single command for any scenario
- **Maintainability**: 1 script instead of 3

---

## Quick Start Examples

### Unified Runner
```bash
# Run all modules with default provider
./run-unified-realapi-tests.sh --modules all

# Run specific module with chunk
./run-unified-realapi-tests.sh \
  --matrix "openai:openai:lucene" \
  --modules "integration-tests" \
  --chunks "core"

# Run multiple provider combinations
./run-unified-realapi-tests.sh \
  --matrix "openai:onnx:lucene,openai:openai:lucene" \
  --modules "all"
```

### GitHub Actions
```yaml
# Single combination
provider_matrix: "openai:onnx:lucene"
test_chunks: "all:core"

# Multiple combinations
provider_matrix: "openai:onnx:lucene,openai:openai:lucene"
test_chunks: "integration-tests:core,relationship-query:all"
```

---

## Next Steps

1. **Review Plan**: Stakeholder review of full plan document
2. **Create Issues**: Break down into implementation tasks
3. **Start Phase 1**: Implement unified test runner
4. **Iterate**: Get feedback, adjust as needed

---

**Full Plan Document**: `REALAPI_TESTS_RESTRUCTURING_PLAN.md`  
**Last Updated**: 2026-01-08
