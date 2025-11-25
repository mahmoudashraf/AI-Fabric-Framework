# Pipeline Changes Register

## Changes to be registered below:

---

### Change #1: Ignore Backend Module
- **Change:** Backend module will be excluded from the integration tests pipeline
- **Reason:** [To be specified]
- **Impact:** Only 3 modules will be available in the pipeline:
  1. AI Infrastructure
  2. Behavior Analytics
  3. Relationship Query
- **Status:** Pending implementation

---

### Change #2: Use OpenAI Key from Backend Env Files
- **Change:** Extract OPENAI_API_KEY from backend environment files (e.g., `.env.dev`) and use it across all integration tests
- **Implementation:** 
  - Read from `backend/.env.dev` or `backend/.env.example`
  - Set as environment variable for all test modules
  - Remove dependency on GitHub Secrets for OPENAI_API_KEY
- **Impact:** 
  - No need to manually configure OPENAI_API_KEY as GitHub Secret
  - Consistent API key usage across all modules
  - Simplifies configuration
- **Files to modify:**
  - `.github/workflows/integration-tests-manual.yml` - Add step to extract and export key
  - All test job steps need to use the extracted key
- **Status:** Pending implementation

---

### Change #3: Manual Trigger Only - No Scheduled or Push Triggers
- **Change:** Integration tests will ONLY run manually via GitHub Actions UI
- **Implementation:**
  - Remove any scheduled triggers (cron)
  - Remove any push/pull_request triggers
  - Keep only `workflow_dispatch` trigger
  - Tests can be triggered from GitHub Actions UI
- **Impact:**
  - Tests run on-demand only
  - No automatic execution on push or PR
  - No scheduled runs
  - Full control over when tests run
  - Saves CI/CD resources and costs
- **Files to modify:**
  - `.github/workflows/integration-tests-manual.yml` - Ensure only `workflow_dispatch` is in `on:` section
- **Status:** Already implemented ✅ (workflow_dispatch is already the only trigger)

---

### Change #4: Disable All Performance Related Tests
- **Change:** Exclude all performance tests from the pipeline
- **Implementation:**
  - Remove `performance-tests` profile option from workflow inputs
  - Remove `all-tests` profile option (which includes performance tests)
  - Keep only `default` and `real-api-tests` profiles
  - Ensure Maven excludes performance test classes (e.g., `**/*PerformanceTest.java`, `**/*LoadTest.java`)
- **Impact:**
  - Faster test execution
  - Reduced resource usage
  - Only functional and real API tests will run
  - Performance benchmarks won't be available in pipeline
- **Files to modify:**
  - `.github/workflows/integration-tests-manual.yml` - Remove performance profile options and job steps
  - Update `test_profile` input choices to only include: `default`, `real-api-tests`
- **Affected test files:**
  - `PerformanceIntegrationTest.java`
  - `LuceneVectorPerformanceIntegrationTest.java`
  - Any other `*PerformanceTest.java` or `*LoadTest.java` files
- **Status:** Pending implementation

---

### Change #5: Single Profile Only - Real API Tests
- **Change:** Remove all test profile options, keep only real-api tests
- **Implementation:**
  - Remove `test_profile` input parameter entirely from workflow
  - Remove conditional steps for different profiles (default, performance, all-tests)
  - All tests will run with real API calls using the real-api-tests profile
  - Simplify workflow to single execution path
- **Impact:**
  - Simpler workflow configuration
  - No profile selection needed
  - All tests use real API providers
  - Tests will consume API credits/costs
  - Consistent test execution every time
- **Files to modify:**
  - `.github/workflows/integration-tests-manual.yml` - Remove `test_profile` input and all conditional test steps
  - Keep only real-api test execution steps
- **Status:** Pending implementation

---

### Change #6: Make OpenAI Key Injectable in GitHub
- **Change:** Allow OpenAI API key to be provided as a workflow input parameter when triggering tests
- **Implementation:**
  - Add `openai_api_key` as a workflow input parameter (type: string, secret)
  - User can provide the key when triggering the workflow via GitHub UI
  - If not provided, fall back to extracting from backend env files (Change #2)
  - Pass the key to all test job steps as environment variable
- **Impact:**
  - Flexibility: Users can use different API keys for different test runs
  - Security: Key can be entered at runtime instead of stored
  - Override capability: Can override the backend env file key if needed
  - Better control over API usage and costs
- **Files to modify:**
  - `.github/workflows/integration-tests-manual.yml` - Add `openai_api_key` input parameter
  - Add logic to use input key if provided, otherwise extract from backend env
- **Status:** Pending implementation
- **Note:** This modifies/enhances Change #2

---

### Change #7: Make Provider and Database Combinations Configurable in GitHub
- **Change:** Allow users to select different provider and database combinations when triggering tests
- **Implementation:**
  - Add workflow input parameters for:
    - **LLM Provider:** OpenAI, Azure OpenAI, Cohere, Anthropic, REST
    - **Embedding Provider:** OpenAI, Azure OpenAI, ONNX (local)
    - **Vector Database:** Lucene, Pinecone, Weaviate, Qdrant, Milvus, Memory (in-memory)
    - **Persistence Database:** H2 (in-memory), PostgreSQL (Testcontainers)
  - Each parameter should be a dropdown/choice in GitHub UI
  - Pass selected combinations as environment variables to test jobs
  - Tests should configure Spring profiles or properties based on selections
- **Impact:**
  - Test different provider combinations without code changes
  - Validate compatibility between different providers
  - Test cost-effective combinations (e.g., ONNX embeddings + Lucene)
  - Test production-like combinations (e.g., OpenAI + Pinecone + PostgreSQL)
  - Flexibility for integration testing scenarios
- **Files to modify:**
  - `.github/workflows/integration-tests-manual.yml` - Add input parameters for all combinations
  - Test jobs need to configure Spring properties based on inputs
  - May need to add application-test-*.yml profiles for different combinations
- **Possible combinations to test:**
  - Cost-effective: ONNX + Lucene + H2
  - Production: OpenAI + Pinecone + PostgreSQL
  - Hybrid: Azure OpenAI + Weaviate + PostgreSQL
  - Testing: OpenAI + Memory + H2
- **Status:** Pending implementation

---

