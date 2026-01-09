# Cohere Provider Test Execution - Log Analysis Guide

## GitHub Actions Run
- **Run ID**: 20829010337
- **Job ID**: 59838010900
- **Workflow**: Integration Tests (Manual Trigger)
- **Status**: Failure

## Key Sections to Check in the Log

### 1. Pre-flight Checks
Look for the section: `═══════════════════════════════════════════════════════════════`
`Pre-flight Checks`
`═══════════════════════════════════════════════════════════════`

**Check for:**
- ✓ Java found
- ✓ Maven found
- ✓ COHERE_API_KEY environment variable validation
  - Should see: `✓ Cohere LLM` and `✓ Cohere Embedding` if API key is set
  - If missing: `✗ COHERE_API_KEY environment variable is not set`

### 2. Provider Configuration
Look for: `Configure Providers` step

**Check for:**
- `LLM_PROVIDER=cohere`
- `EMBEDDING_PROVIDER=cohere`
- `COHERE_API_KEY` is set (value should be masked)
- Model names if custom models were specified:
  - `COHERE_MODEL` (default: `command-r7b-12-2024`)
  - `COHERE_EMBEDDING_MODEL` (default: `embed-english-v3.0`)

### 3. Test Execution
Look for: `Executing Tests` section

**Maven Command Should Include:**
```bash
-Dai.providers.llm-provider=cohere
-Dai.providers.embedding-provider=cohere
-Dai.providers.cohere.model=<model-name>
-Dai.providers.cohere.embedding-model=<embedding-model>
```

### 4. Common Cohere-Specific Errors

#### API Key Issues
- `401 Unauthorized` - Invalid or missing API key
- `COHERE_API_KEY environment variable is not set`
- `Failed to authenticate with Cohere API`

#### Model Issues
- `404 Not Found` - Model name incorrect
  - LLM: Should be `command-r7b-12-2024` or similar
  - Embedding: Should be `embed-english-v3.0` or similar
- `400 Bad Request` - Invalid model parameters

#### Rate Limiting
- `429 Too Many Requests` - Rate limit exceeded
- `Rate limit exceeded for Cohere API`

#### Network/Timeout Issues
- `Connection timeout`
- `Read timeout`
- `Failed to connect to api.cohere.ai`

### 5. Test Results
Look for: `Test Execution Complete` or `Test Execution Failed`

**Expected Output:**
- Tests run count
- Failures count
- Errors count
- Skipped count

### 6. Provider-Specific Log Messages

**Look for Cohere provider initialization:**
- `CohereProvider initialized`
- `CohereEmbeddingProvider initialized`
- `Using Cohere model: <model-name>`

**Error Messages:**
- `Failed to initialize Cohere provider`
- `Cohere API error: <error-message>`
- `Cohere embedding generation failed`

## Configuration Details

### Default Cohere Configuration (from application-real-api-test.yml)
```yaml
cohere:
  enabled: ${COHERE_ENABLED:true}
  api-key: ${COHERE_API_KEY:}
  base-url: ${COHERE_BASE_URL:https://api.cohere.ai/v1}
  model: ${COHERE_MODEL:command-r7b-12-2024}
  embedding-model: ${COHERE_EMBEDDING_MODEL:embed-english-v3.0}
  max-tokens: 4096
  temperature: 0.3
  timeout: 60
  priority: 85
```

### Environment Variables Required
- `COHERE_API_KEY` - Required for both LLM and Embedding
- `COHERE_MODEL` - Optional (defaults to `command-r7b-12-2024`)
- `COHERE_EMBEDDING_MODEL` - Optional (defaults to `embed-english-v3.0`)

## Quick Diagnostic Commands

If you have access to the runner, you can check:

```bash
# Check if API key is set
echo "COHERE_API_KEY is ${COHERE_API_KEY:+set}"

# Test Cohere API directly
curl -X POST https://api.cohere.ai/v1/generate \
  -H "Authorization: Bearer $COHERE_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"command-r7b-12-2024","prompt":"test","max_tokens":10}'
```

## What to Share

If you need help debugging, please share:
1. The "Pre-flight Checks" section
2. The "Configure Providers" step output
3. Any error messages containing "cohere" or "Cohere" (case-insensitive)
4. The final test results summary
5. Any stack traces related to Cohere provider initialization or API calls
