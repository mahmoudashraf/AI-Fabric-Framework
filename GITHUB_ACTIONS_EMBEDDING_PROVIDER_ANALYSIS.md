# GitHub Actions Embedding Provider Configuration Analysis

## Issue Identified

The GitHub Actions workflow (`integration-tests-manual.yml`) allows users to select:
- **Embedding Provider**: `onnx`, `openai`, or `azure-openai`
- **Vector Database**: `lucene`, `pinecone`, `weaviate`, `qdrant`, `milvus`, or `memory`

However, when using **OpenAI embeddings with Lucene**, the workflow does **NOT** automatically configure the `embedding-dimensions` property, which will cause tests to fail.

## Problem

### Current Behavior
1. User selects `embedding_provider: openai` and `vector_database: lucene` in GitHub Actions UI
2. Workflow passes this as matrix spec: `"openai:openai:lucene"` to test scripts
3. Test scripts execute without `-Dai.providers.openai.embedding-dimensions=512`
4. **Result**: OpenAI generates 1536 dimensions → Lucene fails (max 1024) → Tests fail ❌

### Why This Happens
- OpenAI's `text-embedding-3-small` produces **1536 dimensions** by default
- Lucene vector database supports **maximum 1024 dimensions**
- The dimension reduction property (`ai.providers.openai.embedding-dimensions`) must be explicitly set
- **Current scripts don't detect this combination and auto-add the property**

## Solution Options

### Option 1: Auto-Detect in Test Scripts (Recommended)
Update test scripts to automatically add `-Dai.providers.openai.embedding-dimensions=512` when detecting:
- `embedding_provider == "openai"` AND
- `vector_database == "lucene"`

**Pros:**
- Works for both GitHub Actions and local execution
- No workflow changes needed
- Handles edge cases automatically

**Cons:**
- Requires updating multiple test scripts

### Option 2: Update GitHub Actions Workflow
Add conditional logic in the workflow to inject the property when the combination is detected.

**Pros:**
- Centralized configuration
- Clear in workflow file

**Cons:**
- Only works in CI, not locally
- More complex workflow logic

### Option 3: Document as Known Limitation
Add documentation warning users not to use `openai:openai:lucene` without manual configuration.

**Pros:**
- No code changes

**Cons:**
- Users will hit failures
- Poor user experience

## Recommended Fix: Option 1

Update the following test scripts to auto-detect and configure:

1. **`run-provider-matrix-tests.sh`** (AI Infrastructure tests)
2. **`run-relationship-query-realapi-tests.sh`** (Relationship Query tests)
3. **`run-behavior-realapi-tests.sh`** (Behavior tests)

### Implementation Logic

```bash
# After parsing matrix spec, check if OpenAI + Lucene combination
if [ "$EMBEDDING_PROVIDER" == "openai" ] && [ "$AI_INFRASTRUCTURE_VECTOR_DATABASE" == "lucene" ]; then
    MAVEN_COMMAND="$MAVEN_COMMAND -Dai.providers.openai.embedding-dimensions=512"
    print_info "Auto-configured OpenAI embedding dimensions to 512 for Lucene compatibility"
fi
```

## Test Matrix Compatibility

| Embedding Provider | Vector DB | Dimensions | Works? | Notes |
|-------------------|-----------|------------|--------|-------|
| `onnx` | `lucene` | 384 | ✅ Yes | Default, always works |
| `openai` (no prop) | `lucene` | 1536 | ❌ No | **Needs fix** |
| `openai` (with prop) | `lucene` | 512 | ✅ Yes | After fix |
| `openai` | `pinecone` | 1536 | ✅ Yes | No dimension limit |
| `openai` | `weaviate` | 1536 | ✅ Yes | No dimension limit |
| `azure-openai` | `lucene` | 1536 | ❌ No | Similar issue (needs separate fix) |

## Current Status

- ✅ **Dimension reduction feature implemented** in `OpenAIEmbeddingProvider.java`
- ✅ **Property binding working** (`ai.providers.openai.embedding-dimensions`)
- ❌ **Auto-configuration missing** in test scripts
- ❌ **GitHub Actions workflow doesn't handle this automatically**

## Next Steps

1. Update test scripts to auto-detect `openai:openai:lucene` combinations
2. Add the `embedding-dimensions=512` property automatically
3. Test the fix with GitHub Actions workflow
4. Document the auto-configuration behavior
