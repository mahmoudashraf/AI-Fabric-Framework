# Integration Tests Usage Guide

## 🎯 Overview

This guide explains how to run integration tests using the manual trigger workflow. The workflow allows you to run all integration test modules or select specific ones on-demand.

---

## 📋 Available Test Modules

| Module | Description | Test Count | Duration |
|--------|-------------|------------|----------|
| **AI Infrastructure** | Tests for AI providers, RAG, vectors, search, security | 75+ | ~15-25 min |
| **Behavior Analytics** | Tests for behavior pattern detection and analytics | 6 | ~8-12 min |
| **Relationship Query** | Tests for relationship query engine | 3 | ~5-10 min |
| **Backend Application** | Tests for backend controllers and services | 20+ | ~10-20 min |

---

## 🚀 How to Run Tests

### Method 1: GitHub UI (Easiest)

1. Navigate to your repository on GitHub
2. Click on the **Actions** tab
3. In the left sidebar, select **"Integration Tests (Manual Trigger)"**
4. Click the **"Run workflow"** button (top right)
5. Fill in the form:
   - **Branch:** Select the branch to test (e.g., `main`, `develop`)
   - **Test modules to run:** Choose which modules to test
     - `all` - Run all test modules (recommended)
     - `ai-infrastructure` - Only AI Infrastructure tests
     - `behavior-analytics` - Only Behavior Analytics tests
     - `relationship-query` - Only Relationship Query tests
     - `backend` - Only Backend Application tests
   - **Test profile:** Choose test profile (for AI Infrastructure module)
     - `default` - Standard tests without real API calls
     - `real-api-tests` - Tests with real API providers (requires API keys)
     - `performance-tests` - Performance benchmark tests
     - `all-tests` - Run all test types
   - **Job timeout:** Set timeout in minutes (default: 30)
6. Click **"Run workflow"**

**Result:** The workflow will start immediately and you can monitor progress in real-time.

---

### Method 2: GitHub CLI

If you have [GitHub CLI](https://cli.github.com/) installed:

```bash
# Run all tests with default profile
gh workflow run integration-tests-manual.yml \
  --ref main \
  -f modules=all \
  -f test_profile=default \
  -f timeout_minutes=30

# Run only AI Infrastructure tests with real API profile
gh workflow run integration-tests-manual.yml \
  --ref develop \
  -f modules=ai-infrastructure \
  -f test_profile=real-api-tests \
  -f timeout_minutes=30

# Run only Backend tests
gh workflow run integration-tests-manual.yml \
  --ref main \
  -f modules=backend \
  -f test_profile=default \
  -f timeout_minutes=25
```

**View running workflows:**
```bash
gh run list --workflow=integration-tests-manual.yml
```

**View workflow logs:**
```bash
gh run view <run-id> --log
```

---

### Method 3: GitHub API (Advanced)

Using `curl` or any HTTP client:

```bash
# Set your GitHub token
export GITHUB_TOKEN="your_github_token"
export REPO_OWNER="your-org"
export REPO_NAME="your-repo"

# Run all tests
curl -X POST \
  -H "Authorization: token $GITHUB_TOKEN" \
  -H "Accept: application/vnd.github.v3+json" \
  https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/actions/workflows/integration-tests-manual.yml/dispatches \
  -d '{
    "ref": "main",
    "inputs": {
      "modules": "all",
      "test_profile": "default",
      "timeout_minutes": "30"
    }
  }'
```

---

## 🔑 Required Secrets

### For All Tests
These secrets should be configured in GitHub repository settings:

| Secret Name | Description | Used By |
|------------|-------------|---------|
| `OPENAI_API_KEY` | OpenAI API key | All modules (optional for non-real-API tests) |
| `JWT_SECRET` | JWT secret for backend | Backend module |

### For Real API Tests (Optional)
Only needed if running with `test_profile=real-api-tests` or `test_profile=all-tests`:

| Secret Name | Description |
|------------|-------------|
| `AZURE_OPENAI_API_KEY` | Azure OpenAI API key |
| `COHERE_API_KEY` | Cohere API key |
| `ANTHROPIC_API_KEY` | Anthropic/Claude API key |

**Note:** If API keys are missing, real API tests will be skipped (not failed).

---

## 📊 Understanding Test Results

### Viewing Results

1. **In GitHub Actions UI:**
   - Navigate to the workflow run
   - See summary table showing pass/fail for each module
   - Click on individual jobs to see detailed logs
   - Check artifacts section for detailed test reports

2. **Test Reports:**
   - Each module uploads test reports as artifacts
   - Download artifacts to view detailed results
   - Reports include:
     - Test execution time
     - Pass/fail status
     - Error messages and stack traces
     - Test coverage (if enabled)

3. **Test Summary:**
   - Automatic summary created at the end
   - Shows status for each module
   - Overall pass/fail indicator
   - Links to detailed reports

### Status Indicators

| Symbol | Meaning |
|--------|---------|
| ✅ | Tests passed |
| ❌ | Tests failed |
| ⏭️ | Tests skipped (module not selected) |
| 🚫 | Tests cancelled |

---

## 🔧 Test Profiles Explained

### Default Profile
- **What runs:** Standard integration tests
- **API calls:** Mocked using WireMock
- **Duration:** Fastest
- **Use case:** Quick validation, CI/CD checks
- **API keys needed:** None (optional)

### Real API Tests Profile
- **What runs:** Tests that call actual AI provider APIs
- **API calls:** Real calls to OpenAI, Azure, Cohere, Anthropic
- **Duration:** Slower (network latency)
- **Use case:** End-to-end validation before release
- **API keys needed:** Yes (OPENAI_API_KEY, etc.)
- **Cost:** May incur API usage costs

### Performance Tests Profile
- **What runs:** Performance benchmarks and load tests
- **API calls:** Mocked
- **Duration:** Longest
- **Use case:** Performance regression testing
- **API keys needed:** None

### All Tests Profile
- **What runs:** Everything (unit + integration + real API + performance)
- **API calls:** Mix of mocked and real
- **Duration:** Longest
- **Use case:** Comprehensive validation
- **API keys needed:** Yes

---

## 🐛 Troubleshooting

### Common Issues

#### 1. Tests Timeout
**Problem:** Tests exceed timeout limit

**Solutions:**
- Increase timeout in workflow inputs
- Check for hanging tests in logs
- Review Testcontainers startup logs

#### 2. Testcontainers Fails to Start
**Problem:** Docker containers don't start

**Solutions:**
- Check GitHub Actions has Docker available
- Review container logs in test reports
- Verify network connectivity

#### 3. API Key Missing
**Problem:** Real API tests fail with authentication error

**Solutions:**
- Verify secrets are configured in GitHub
- Check secret names match exactly
- Ensure API keys are valid and not expired

#### 4. Out of Memory
**Problem:** Tests fail with OutOfMemoryError

**Solutions:**
- Reduce parallel test execution
- Increase timeout to reduce pressure
- Review test for memory leaks

#### 5. Flaky Tests
**Problem:** Tests pass/fail inconsistently

**Solutions:**
- Review test for race conditions
- Check for timing dependencies
- Add proper wait conditions
- Increase timeout for specific tests

---

## 📈 Best Practices

### When to Run Tests

#### Run All Tests
- Before merging to main branch
- Before creating a release
- After major refactoring
- Weekly scheduled run

#### Run Specific Modules
- When working on specific feature
- Quick validation during development
- Debugging specific component

#### Run with Real API Tests
- Before production deployment
- Monthly integration check
- After provider API updates
- When debugging provider issues

### Tips for Faster Test Execution

1. **Use Default Profile for Quick Checks**
   - Mocked tests run much faster
   - Save real API tests for pre-release

2. **Run Specific Modules**
   - Don't run all tests if you only changed one module
   - Target specific module for faster feedback

3. **Parallel Execution is Automatic**
   - All modules run in parallel by default
   - No need to run them sequentially

4. **Cache is Your Friend**
   - Maven dependencies are cached
   - Subsequent runs are faster

---

## 📝 Examples

### Example 1: Quick Validation Before PR
```bash
# Run all tests with default profile (mocked APIs)
gh workflow run integration-tests-manual.yml \
  --ref feature/my-feature \
  -f modules=all \
  -f test_profile=default \
  -f timeout_minutes=30
```

### Example 2: Test Only Changed Module
```bash
# Only run backend tests if you changed backend code
gh workflow run integration-tests-manual.yml \
  --ref feature/backend-fix \
  -f modules=backend \
  -f test_profile=default \
  -f timeout_minutes=25
```

### Example 3: Pre-Release Validation
```bash
# Run all tests including real API calls
gh workflow run integration-tests-manual.yml \
  --ref release/v1.2.0 \
  -f modules=all \
  -f test_profile=real-api-tests \
  -f timeout_minutes=45
```

### Example 4: Performance Testing
```bash
# Run performance benchmarks
gh workflow run integration-tests-manual.yml \
  --ref main \
  -f modules=ai-infrastructure \
  -f test_profile=performance-tests \
  -f timeout_minutes=60
```

---

## 🔒 Security Considerations

1. **API Keys:**
   - Never commit API keys to code
   - Always use GitHub Secrets
   - Rotate keys regularly
   - Use separate keys for testing

2. **Test Data:**
   - Don't use production data in tests
   - Generate synthetic test data
   - Clean up test data after runs

3. **Secrets Access:**
   - Limit who can trigger workflows
   - Review workflow run logs for leaks
   - Use masked outputs for sensitive data

---

## 📞 Getting Help

- **Documentation:** See `INTEGRATION_TESTS_PIPELINE_PLAN.md` for detailed architecture
- **Issues:** Create GitHub issue with `test` label
- **Questions:** Ask in team chat or create discussion
- **Logs:** Always attach workflow logs when reporting issues

---

## 🔄 Continuous Improvement

This workflow is designed to be flexible and maintainable. If you encounter issues or have suggestions for improvement:

1. Document the issue
2. Propose a solution
3. Update this guide
4. Share with the team

**Remember:** Good tests are the foundation of confident deployments! 🚀
