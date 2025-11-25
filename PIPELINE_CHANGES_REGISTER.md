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

