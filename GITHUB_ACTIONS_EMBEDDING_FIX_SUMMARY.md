# GitHub Actions Embedding Provider Auto-Configuration Fix

## Problem Fixed

When using the GitHub Actions workflow (`integration-tests-manual.yml`) with:
- **Embedding Provider**: `openai`
- **Vector Database**: `lucene`

The tests would fail because:
1. OpenAI's `text-embedding-3-small` generates **1536 dimensions** by default
2. Lucene vector database supports **maximum 1024 dimensions**
3. The `embedding-dimensions` property was not automatically configured

## Solution Implemented

Updated all three test runner scripts to **automatically detect** the `openai + lucene` combination and add the required dimension reduction property:

### Files Updated

1. **`ai-infrastructure-module/integration-Testing/integration-tests/run-provider-matrix-tests.sh`**
   - Auto-detects `openai` embedding provider with `lucene` vector database
   - Automatically adds `-Dai.providers.openai.embedding-dimensions=512`

2. **`ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/run-relationship-query-realapi-tests.sh`**
   - Same auto-detection logic for relationship query tests

3. **`ai-infrastructure-module/integration-Testing/behavior-integration-tests/run-behavior-realapi-tests.sh`**
   - Same auto-detection logic for behavior tests

### Detection Logic

The scripts now check:
```bash
if [ "$EMBEDDING_PROVIDER" == "openai" ] && [ "$AI_INFRASTRUCTURE_VECTOR_DATABASE" == "lucene" ]; then
    # Add -Dai.providers.openai.embedding-dimensions=512
fi
```

For matrix specs (multiple combinations), the scripts also check if any combination uses `openai` as the embedding provider when the vector database is `lucene`.

## How It Works

### Before Fix
1. User selects `embedding_provider: openai` and `vector_database: lucene` in GitHub Actions
2. Workflow passes `"openai:openai:lucene"` to test scripts
3. Scripts execute without dimension reduction property
4. **Result**: Tests fail with dimension mismatch error ❌

### After Fix
1. User selects `embedding_provider: openai` and `vector_database: lucene` in GitHub Actions
2. Workflow passes `"openai:openai:lucene"` to test scripts
3. Scripts **automatically detect** the combination
4. Scripts **automatically add** `-Dai.providers.openai.embedding-dimensions=512`
5. **Result**: Tests pass with 512-dimension embeddings ✅

## Benefits

1. **Works in CI/CD**: GitHub Actions workflow now handles this automatically
2. **Works locally**: Same auto-detection works when running scripts manually
3. **No user action required**: Users don't need to remember to set the property
4. **Backward compatible**: Doesn't break existing configurations
5. **Clear logging**: Scripts log when auto-configuration is applied

## Test Scenarios

| Scenario | Embedding Provider | Vector DB | Auto-Config? | Result |
|----------|-------------------|-----------|--------------|--------|
| Default | `onnx` | `lucene` | N/A | ✅ Works (384 dims) |
| Fixed | `openai` | `lucene` | ✅ Yes | ✅ Works (512 dims) |
| Other DB | `openai` | `pinecone` | N/A | ✅ Works (1536 dims) |
| Other DB | `openai` | `weaviate` | N/A | ✅ Works (1536 dims) |

## Verification

To verify the fix works:

1. **Run GitHub Actions workflow** with:
   - `embedding_provider: openai`
   - `vector_database: lucene`
   - Should see log: "Auto-configured OpenAI embedding dimensions to 512 for Lucene compatibility"
   - Tests should pass

2. **Run locally**:
   ```bash
   cd ai-infrastructure-module/integration-Testing/integration-tests
   ./run-provider-matrix-tests.sh "openai:openai:lucene"
   ```
   - Should see auto-configuration message
   - Tests should pass

## Related Documentation

- `GITHUB_ACTIONS_EMBEDDING_PROVIDER_ANALYSIS.md` - Detailed analysis of the issue
- `OPENAI_EMBEDDING_DIMENSIONS.md` - OpenAI embedding model dimension information
- `TEST_DIFFERENT_EMBEDDING_PROVIDERS.md` - Guide for testing different providers
