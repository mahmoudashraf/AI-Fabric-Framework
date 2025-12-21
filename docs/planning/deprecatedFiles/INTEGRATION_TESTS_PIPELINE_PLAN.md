# Integration Tests Pipeline Plan - Manual Trigger

## Overview
This plan covers adding all integration test modules to the CI/CD pipeline with **manual trigger** support using GitHub Actions `workflow_dispatch`.

---

## 📋 Integration Test Modules Identified

### 1. **AI Infrastructure Integration Tests** 
- **Location:** `ai-infrastructure-module/integration-tests`
- **Test Count:** 75+ test files
- **Test Categories:**
  - Real API Provider Tests (OpenAI, Azure, Cohere, Anthropic)
  - RAG (Retrieval-Augmented Generation) Tests
  - Vector Database Tests (Lucene, Pinecone, Weaviate, Qdrant, Milvus)
  - Search Tests (Caching, Pagination, Filtering, Semantic)
  - Security Tests (Rate limiting, PII detection, Access policies)
  - Performance Tests
  - ONNX Embedding Tests

### 2. **AI Behavior Analytics Integration Tests**
- **Status:** Removed (behavior integration test module deleted)

### 3. **Relationship Query Integration Tests**
- **Location:** `ai-infrastructure-module/relationship-query-integration-tests`
- **Test Count:** 3 test files
- **Test Categories:**
  - Financial Fraud Detection
  - E-Commerce Scenarios
  - Law Firm Case Management

### 4. **Backend Application Integration Tests**
- **Location:** `backend/src/test`
- **Test Count:** 20+ test files
- **Test Categories:**
  - AI Controller Tests
  - AI Service Tests
  - Security Integration Tests
  - Advanced RAG Tests
  - Compliance Tests
  - Configuration Tests

---

## 🎯 Implementation Strategy

### Approach: Single Workflow with Job Matrix + Manual Trigger

Create a comprehensive workflow that:
- ✅ Supports **manual trigger** via `workflow_dispatch`
- ✅ Allows selecting specific test modules to run
- ✅ Runs tests in parallel using GitHub Actions matrix strategy
- ✅ Provides detailed test reports
- ✅ Can be triggered on-demand without code changes

---

## 📝 Configuration Requirements per Module

### Module 1: AI Infrastructure Integration Tests

**Maven Configuration:**
- **Artifact:** `ai-infrastructure-integration-tests`
- **Working Directory:** `ai-infrastructure-module`
- **Test Command:** `mvn test -pl integration-tests -B`

**Dependencies Required:**
- JDK 21
- Maven 3.8+
- Docker (for Testcontainers)
- PostgreSQL container
- WireMock for API mocking

**Environment Variables:**
```yaml
TESTCONTAINERS_RYUK_DISABLED: false
OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}  # Optional for real API tests
AZURE_OPENAI_API_KEY: ${{ secrets.AZURE_OPENAI_API_KEY }}  # Optional
COHERE_API_KEY: ${{ secrets.COHERE_API_KEY }}  # Optional
ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}  # Optional
```

**Test Profiles Available:**
- `unit-tests` - Unit tests only (default)
- `integration-tests` - Integration tests using failsafe
- `real-api-tests` - Tests requiring real API keys
- `performance-tests` - Performance benchmarks
- `all-tests` - Run everything

**Recommended Timeout:** 30 minutes

**Resource Requirements:**
- Memory: 4GB
- Disk: 10GB (for Docker images)

---

### Module 2: AI Behavior Analytics Integration Tests

This module has been removed (behavior integration tests deleted).

---

### Module 3: Relationship Query Integration Tests

**Maven Configuration:**
- **Artifact:** `relationship-query-integration-tests`
- **Working Directory:** `ai-infrastructure-module`
- **Test Command:** `mvn test -pl relationship-query-integration-tests -B`

**Dependencies Required:**
- JDK 21
- Maven 3.8+
- H2 Database (in-memory, no Docker needed)

**Environment Variables:**
```yaml
SPRING_PROFILES_ACTIVE: test
OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}  # Required for real API tests
```

**Database Setup:**
- H2 in-memory database
- Auto-configured by Spring Boot

**Recommended Timeout:** 15 minutes

**Resource Requirements:**
- Memory: 2GB
- Disk: 5GB

---

### Module 4: Backend Application Integration Tests

**Maven Configuration:**
- **Artifact:** `easyluxury-backend`
- **Working Directory:** `backend`
- **Test Command:** `mvn test -B`

**Dependencies Required:**
- JDK 21
- Maven 3.8+
- Docker (for Testcontainers)
- PostgreSQL container

**Environment Variables:**
```yaml
TESTCONTAINERS_RYUK_DISABLED: false
SPRING_PROFILES_ACTIVE: test
OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
JWT_SECRET: ${{ secrets.JWT_SECRET }}
DATABASE_URL: jdbc:tc:postgresql:15:///testdb  # Testcontainers URL
```

**Database Setup:**
- PostgreSQL via Testcontainers
- Liquibase migrations applied automatically
- Test data managed by test classes

**Recommended Timeout:** 25 minutes

**Resource Requirements:**
- Memory: 4GB
- Disk: 10GB

---

## 🔧 GitHub Actions Workflow Configuration

### Workflow File: `.github/workflows/integration-tests-manual.yml`

**Key Features:**
1. **Manual Trigger (workflow_dispatch):**
   - Input to select which modules to run
   - Input for timeout configuration
   - Input for test profile selection

2. **Matrix Strategy:**
   - Run multiple test modules in parallel
   - Separate jobs for each module
   - Independent failure isolation

3. **Test Reporting:**
   - Upload test reports as artifacts
   - Publish test results summary
   - Comment on PR (if triggered from PR)

4. **Caching:**
   - Maven dependencies cache
   - Docker layer cache
   - Test containers cache

**Workflow Structure:**
```yaml
name: Integration Tests (Manual Trigger)

on:
  workflow_dispatch:
    inputs:
      modules:
        description: 'Test modules to run'
        required: true
        default: 'all'
        type: choice
        options:
          - all
          - ai-infrastructure
          - behavior-analytics
          - relationship-query
          - backend
      test_profile:
        description: 'Test profile'
        required: false
        default: 'default'
        type: choice
        options:
          - default
          - real-api-tests
          - performance-tests
          - all-tests
      timeout_minutes:
        description: 'Job timeout (minutes)'
        required: false
        default: '30'
        type: number

jobs:
  # Job 1: AI Infrastructure Tests
  ai-infrastructure-tests:
    name: AI Infrastructure Integration Tests
    runs-on: ubuntu-latest
    timeout-minutes: ${{ github.event.inputs.timeout_minutes || 30 }}
    if: |
      github.event.inputs.modules == 'all' || 
      github.event.inputs.modules == 'ai-infrastructure'
    
    steps:
      # ... (detailed steps below)

  # Job 2: Behavior Analytics Tests
  behavior-analytics-tests:
    name: Behavior Analytics Integration Tests
    runs-on: ubuntu-latest
    timeout-minutes: ${{ github.event.inputs.timeout_minutes || 20 }}
    if: |
      github.event.inputs.modules == 'all' || 
      github.event.inputs.modules == 'behavior-analytics'
    
    steps:
      # ... (detailed steps below)

  # Job 3: Relationship Query Tests
  relationship-query-tests:
    name: Relationship Query Integration Tests
    runs-on: ubuntu-latest
    timeout-minutes: ${{ github.event.inputs.timeout_minutes || 15 }}
    if: |
      github.event.inputs.modules == 'all' || 
      github.event.inputs.modules == 'relationship-query'
    
    steps:
      # ... (detailed steps below)

  # Job 4: Backend Application Tests
  backend-tests:
    name: Backend Application Integration Tests
    runs-on: ubuntu-latest
    timeout-minutes: ${{ github.event.inputs.timeout_minutes || 25 }}
    if: |
      github.event.inputs.modules == 'all' || 
      github.event.inputs.modules == 'backend'
    
    steps:
      # ... (detailed steps below)

  # Summary Job
  test-summary:
    name: Test Summary
    runs-on: ubuntu-latest
    needs: [ai-infrastructure-tests, behavior-analytics-tests, relationship-query-tests, backend-tests]
    if: always()
    steps:
      # Aggregate results and create summary
```

---

## 📦 Secrets Required

Add these secrets to GitHub repository settings:

| Secret Name | Description | Required For |
|------------|-------------|--------------|
| `OPENAI_API_KEY` | OpenAI API key for real API tests | All modules with real API tests |
| `AZURE_OPENAI_API_KEY` | Azure OpenAI API key | AI Infrastructure module |
| `COHERE_API_KEY` | Cohere API key | AI Infrastructure module |
| `ANTHROPIC_API_KEY` | Anthropic API key | AI Infrastructure module |
| `JWT_SECRET` | JWT secret for backend tests | Backend module |

**Note:** Real API tests can be skipped if keys are not provided (tests will be marked as skipped).

---

## 🎨 Test Report Configuration

### Artifact Uploads
Each job uploads:
- Surefire test reports (`target/surefire-reports/`)
- Failsafe test reports (`target/failsafe-reports/`)
- Test logs
- Coverage reports (if enabled)

### Test Result Publishing
- Use `EnricoMi/publish-unit-test-result-action@v2`
- Displays pass/fail summary in GitHub UI
- Shows test duration and flaky tests

### PR Comments
- Automatic comment with test results
- Shows which tests passed/failed
- Links to full reports

---

## 🚀 Execution Instructions

### How to Trigger Manually:

1. **Via GitHub UI:**
   - Go to Actions tab
   - Select "Integration Tests (Manual Trigger)"
   - Click "Run workflow"
   - Select branch
   - Choose modules to run
   - Select test profile (optional)
   - Set timeout (optional)
   - Click "Run workflow"

2. **Via GitHub CLI:**
```bash
gh workflow run integration-tests-manual.yml \
  --ref main \
  -f modules=all \
  -f test_profile=default \
  -f timeout_minutes=30
```

3. **Via API:**
```bash
curl -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/OWNER/REPO/actions/workflows/integration-tests-manual.yml/dispatches \
  -d '{"ref":"main","inputs":{"modules":"all","test_profile":"default"}}'
```

---

## 📊 Expected Test Coverage

| Module | Unit Tests | Integration Tests | Total Tests | Avg Duration |
|--------|-----------|-------------------|-------------|--------------|
| AI Infrastructure | ~30 | ~75 | ~105 | 15-25 min |
| Behavior Analytics | ~0 | ~6 | ~6 | 8-12 min |
| Relationship Query | ~40 | ~3 | ~43 | 5-10 min |
| Backend | ~10 | ~20 | ~30 | 10-20 min |
| **TOTAL** | **~80** | **~104** | **~184** | **38-67 min** |

**Note:** Running all tests in parallel reduces total time to ~25-30 minutes.

---

## 🔍 Test Execution Strategy

### Parallel Execution
- All 4 modules run simultaneously
- Each module has isolated resources
- No shared state between jobs

### Failure Handling
- Each job fails independently
- Test summary job always runs
- Failed tests don't block other modules

### Retry Logic
- Flaky tests automatically retried (3 attempts)
- Testcontainers retry on startup failure
- Network timeouts handled gracefully

---

## 🛠 Maintenance Considerations

### Regular Updates Needed:
1. **Dependencies:** Keep Maven dependencies up to date
2. **Secrets:** Rotate API keys regularly
3. **Docker Images:** Update Testcontainers versions
4. **Test Data:** Refresh test data periodically

### Monitoring:
- Track test execution time trends
- Monitor flaky test rates
- Review test coverage reports
- Check resource usage

### Cost Optimization:
- Use GitHub Actions cache effectively
- Only run necessary tests
- Consider scheduled runs for expensive tests
- Use self-hosted runners for frequent runs

---

## 📋 Implementation Checklist

- [ ] Create `.github/workflows/integration-tests-manual.yml`
- [ ] Add all required secrets to GitHub repository
- [ ] Configure branch protection rules (if needed)
- [ ] Test workflow with single module first
- [ ] Test workflow with all modules
- [ ] Verify test reports are uploaded correctly
- [ ] Verify PR comments work correctly
- [ ] Document workflow in project README
- [ ] Train team on manual trigger usage
- [ ] Set up notifications for test failures

---

## 🎯 Success Criteria

✅ All test modules can be triggered manually  
✅ Tests run in parallel and complete within 30 minutes  
✅ Test reports are accessible via GitHub UI  
✅ Failed tests provide clear error messages  
✅ No flaky tests (or properly handled)  
✅ Resource usage is optimized  
✅ Team can easily trigger and monitor tests  

---

## 📚 Additional Resources

- [GitHub Actions Manual Triggers](https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows#workflow_dispatch)
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/)
- [Testcontainers Documentation](https://www.testcontainers.org/)
- [GitHub Actions Matrix Strategy](https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs)

---

**Next Steps:**
1. Review this plan with the team
2. Prioritize which modules to implement first
3. Create the workflow file
4. Test in a feature branch
5. Deploy to main branch
