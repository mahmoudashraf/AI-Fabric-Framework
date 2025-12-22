# FinancialFraudRealApiIntegrationTest - Debug & Fix Guide

**Error**: `FinancialFraudRealApiIntegrationTest.shouldDetectMirrorCounterpartyWires` returns **500 INTERNAL_SERVER_ERROR** at line 110

**Root Cause**: Missing or invalid OpenAI API Key configuration

---

## 🔍 Problem Analysis

### What's Happening

1. **Test Profile**: Uses `@ActiveProfiles("realapi")`
2. **Configuration**: `application-realapi.yml` attempts to use real OpenAI API
3. **API Key Loading**: `BackendEnvTestConfiguration` tries to load from `../../backend/.env`
4. **Failure Point**: OpenAI API key is not available → API calls fail → 500 error

### Configuration Chain

```
application-realapi.yml
  ↓
  ai.providers.openai.api-key: ${OPENAI_API_KEY:sk-real-api-test}
  ↓
BackendEnvTestConfiguration
  ↓
  Looks for: ../../backend/.env
  ↓
  If not found: Falls back to "sk-real-api-test" (INVALID)
  ↓
  OpenAI API call fails → 500 error
```

---

## 🛠️ Solution Options

### Option 1: Set OPENAI_API_KEY Environment Variable (QUICKEST)

```bash
# Export your OpenAI API key
export OPENAI_API_KEY="sk-your-actual-key-here"

# Run the test
mvn test -Dtest=FinancialFraudRealApiIntegrationTest#shouldDetectMirrorCounterpartyWires
```

### Option 2: Create Backend .env File

**Location**: `backend/.env` (in parent directory of ai-infrastructure-module)

**File Contents**:
```env
OPENAI_API_KEY=sk-your-actual-key-here
OPENAI_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
```

### Option 3: Modify Test to Use Mock Profile (FOR TESTING ONLY)

Change line 35 in `FinancialFraudRealApiIntegrationTest.java`:

```java
// FROM:
@ActiveProfiles("realapi")

// TO (for testing):
@ActiveProfiles("mock")  // Uses mock services instead of real API
```

### Option 4: Use System Properties

```bash
mvn test \
  -Dtest=FinancialFraudRealApiIntegrationTest#shouldDetectMirrorCounterpartyWires \
  -DOPENAI_API_KEY="sk-your-actual-key-here"
```

---

## 📋 Step-by-Step Fix

### Step 1: Verify Backend Folder Structure

```bash
cd TheBaseRepo
ls -la backend/  # Should exist
ls -la backend/.env  # Should exist with OPENAI_API_KEY
```

### Step 2: Check What Files Exist

```bash
# From project root:
ls -la ../backend/.env
# OR try from ai-infrastructure-module:
ls -la ../../backend/.env
```

### Step 3: If backend/.env Doesn't Exist

Create it:

```bash
# From TheBaseRepo root
cat > backend/.env << 'EOF'
# OpenAI Configuration
OPENAI_API_KEY=sk-your-actual-key-from-openai-here
OPENAI_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small

# Optional: Add other configs
PINECONE_API_KEY=your-pinecone-key-if-used
EOF

# Make sure file is readable
chmod 644 backend/.env
```

### Step 4: Verify Configuration

The test uses these environment variable providers (in order):

1. System properties (highest priority)
2. System environment variables
3. Fallback value from `application-realapi.yml` (lowest priority)

```bash
# Check current configuration
echo $OPENAI_API_KEY  # Should print your key
```

---

## 🔗 Related Files

### Test File
- **Path**: `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/java/com/ai/infrastructure/relationship/it/realapi/FinancialFraudRealApiIntegrationTest.java`
- **Line 35**: `@ActiveProfiles("realapi")`
- **Line 97-115**: `shouldDetectMirrorCounterpartyWires()` test

### Configuration Files
- **Config**: `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/resources/application-realapi.yml`
- **Loader**: `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/java/com/ai/infrastructure/relationship/it/config/BackendEnvTestConfiguration.java`

### Key Configuration Lines

**application-realapi.yml (Line 58)**:
```yaml
api-key: ${OPENAI_API_KEY:sk-real-api-test}
```

**BackendEnvTestConfiguration.java (Line 57)**:
```java
@Value("${relationship-test.backend-env-path:../../backend/.env}")
private String backendEnvPath;
```

---

## 📊 Configuration Precedence

The configuration uses this precedence order (highest to lowest):

| Priority | Source | Example |
|----------|--------|---------|
| 1 (Highest) | System Property | `-DOPENAI_API_KEY=...` |
| 2 | Environment Variable | `export OPENAI_API_KEY=...` |
| 3 | Backend .env file | `backend/.env` (loaded by BackendEnvTestConfiguration) |
| 4 (Lowest) | Fallback in YAML | `${OPENAI_API_KEY:sk-real-api-test}` (INVALID!) |

---

## ✅ Verification Checklist

- [ ] **OpenAI API Key Available**
  ```bash
  echo $OPENAI_API_KEY
  # Should print: sk-xxxxxxxxxxxxxxxxxxxxxxxx
  ```

- [ ] **Backend .env File Exists**
  ```bash
  cat backend/.env | grep OPENAI_API_KEY
  # Should print: OPENAI_API_KEY=sk-...
  ```

- [ ] **Test Configuration Correct**
  ```bash
  grep "@ActiveProfiles" ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/java/com/ai/infrastructure/relationship/it/realapi/FinancialFraudRealApiIntegrationTest.java
  # Should show: @ActiveProfiles("realapi")
  ```

- [ ] **ONNX Models Available** (for embeddings)
  ```bash
  ls -la src/test/resources/models/embeddings/
  # Should have: all-MiniLM-L6-v2.onnx, tokenizer.json
  ```

---

## 🚀 Run the Test

Once configured, run:

```bash
# Option A: From project root
cd ai-infrastructure-module
mvn test -Dtest=FinancialFraudRealApiIntegrationTest#shouldDetectMirrorCounterpartyWires

# Option B: Run all FinancialFraud tests
mvn test -Dtest=FinancialFraudRealApiIntegrationTest

# Option C: With environment variable
OPENAI_API_KEY="sk-your-key" \
mvn test -Dtest=FinancialFraudRealApiIntegrationTest#shouldDetectMirrorCounterpartyWires
```

---

## 🔧 Troubleshooting

### Problem: "Backend .env file not found"

**Solution**:
```bash
# Create the file
mkdir -p backend
echo "OPENAI_API_KEY=sk-your-key" > backend/.env

# Or export as environment variable
export OPENAI_API_KEY="sk-your-key"
```

### Problem: "401 Unauthorized from OpenAI"

**Cause**: Invalid API key

**Solution**:
- Get a valid key from https://platform.openai.com/api-keys
- Make sure the key starts with `sk-`
- Ensure the key has credits/billing enabled

### Problem: "Cannot find ../../backend/.env"

**Solution**: The path is relative to the test class location. Verify:

```bash
# From: ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/
# Path: ../../backend/.env
# Should resolve to: TheBaseRepo/backend/.env

# Check path resolution:
cd ai-infrastructure-module/integration-Testing/relationship-query-integration-tests
ls -la ../../backend/.env
```

### Problem: "Model not found: gpt-4o-mini"

**Cause**: OpenAI account doesn't have access to this model

**Solution**: Use available model:
```bash
# Edit application-realapi.yml
# Change:
openai:
  model: ${OPENAI_MODEL:gpt-3.5-turbo}  # Use gpt-3.5-turbo instead
  embedding-model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
```

---

## 📝 Test Flow

### What the Test Does

1. **Setup** (Line 60-71):
   - Clears database & vector indexes
   - Seeds test transactions
   - Indexes transactions for search

2. **Execute Query** (Line 97-108):
   - Sends `shouldDetectMirrorCounterpartyWires` query
   - Query: "Find high-risk wire transfers..."
   - Uses RAG to search and match results

3. **Assert Results** (Line 110-114):
   - **Line 110**: Expects HTTP 200 OK
   - **FAILS HERE**: Gets 500 error instead

### Why It Fails

When the test tries to execute the RAG query:
1. RAG orchestrator calls LLM (OpenAI)
2. OpenAI requires valid API key
3. API key is `sk-real-api-test` (INVALID fallback)
4. OpenAI returns 401 error
5. Application catches error and returns 500

---

## ✨ Prevention

### For Future Tests

Add validation in `BackendEnvTestConfiguration`:

```java
@PostConstruct
void validateConfiguration() {
    String apiKey = System.getProperty("OPENAI_API_KEY");
    if (apiKey == null || apiKey.equals("sk-real-api-test")) {
        throw new IllegalStateException(
            "OPENAI_API_KEY not configured. Please set it via:\n" +
            "1. Environment: export OPENAI_API_KEY=sk-...\n" +
            "2. Backend file: backend/.env\n" +
            "3. System prop: -DOPENAI_API_KEY=sk-..."
        );
    }
}
```

---

## 📚 References

- **OpenAI API Keys**: https://platform.openai.com/api-keys
- **Test Configuration**: BackendEnvTestConfiguration.java
- **YAML Config**: application-realapi.yml
- **Test Class**: FinancialFraudRealApiIntegrationTest.java

---

## ✅ Summary

| Item | Status | Action |
|------|--------|--------|
| Root Cause | ✅ Identified | Missing OpenAI API Key |
| Fix Effort | ✅ Low | 1-2 minutes |
| Priority | 🔴 High | Blocks realapi tests |
| Solution | ✅ Ready | Use Option 1 or 2 above |

**QUICK FIX**:
```bash
export OPENAI_API_KEY="sk-your-actual-key"
mvn test -Dtest=FinancialFraudRealApiIntegrationTest#shouldDetectMirrorCounterpartyWires
```

---

**Status**: Debug guide complete. Follow one of the solution options above to fix the 500 error.

