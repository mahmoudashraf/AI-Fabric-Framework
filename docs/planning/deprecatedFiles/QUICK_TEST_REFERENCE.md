# Quick Test Reference Card

## 🚀 Run Tests in 3 Steps

### Step 1: Go to Actions
Visit: `https://github.com/YOUR-ORG/YOUR-REPO/actions`

### Step 2: Select Workflow
Click: **"Integration Tests (Manual Trigger)"**

### Step 3: Run
Click: **"Run workflow"** → Select options → **"Run workflow"**

---

## 📋 Module Options

| Option | What It Tests | Duration | When to Use |
|--------|---------------|----------|-------------|
| `all` | Everything (104+ tests) | ~25-30 min | Before release, weekly check |
| `ai-infrastructure` | AI providers, RAG, vectors | ~15-25 min | AI feature changes |
| `behavior-analytics` | Behavior patterns | ~8-12 min | Analytics changes |
| `relationship-query` | Relationship engine | ~5-10 min | Query engine changes |
| `backend` | Backend app | ~10-20 min | Backend changes |

---

## 🎯 Test Profile Options

| Profile | API Calls | Duration | Cost | When to Use |
|---------|-----------|----------|------|-------------|
| `default` | Mocked | Fastest | Free | Most of the time |
| `real-api-tests` | Real | Slower | $$ | Before release |
| `performance-tests` | Mocked | Longest | Free | Performance check |
| `all-tests` | Mixed | Very long | $$$ | Full validation |

---

## 💻 CLI Commands

### Run Everything (Default)
```bash
gh workflow run integration-tests-manual.yml --ref main -f modules=all
```

### Run Specific Module
```bash
gh workflow run integration-tests-manual.yml --ref main -f modules=backend
```

### Run with Real APIs
```bash
gh workflow run integration-tests-manual.yml --ref main -f modules=all -f test_profile=real-api-tests
```

### Check Status
```bash
gh run list --workflow=integration-tests-manual.yml
```

### View Logs
```bash
gh run view <run-id> --log
```

---

## 🔑 Required Secrets

### Must Have:
- `OPENAI_API_KEY` - For AI tests
- `JWT_SECRET` - For backend tests

### Optional (Real API Tests):
- `AZURE_OPENAI_API_KEY`
- `COHERE_API_KEY`
- `ANTHROPIC_API_KEY`

**Add secrets:** Settings → Secrets and variables → Actions → New repository secret

---

## 📊 Understanding Results

### Status Symbols:
- ✅ = Passed
- ❌ = Failed
- ⏭️ = Skipped
- 🚫 = Cancelled

### Where to Find Results:
1. **Summary:** Actions → Workflow run → Summary tab
2. **Logs:** Actions → Workflow run → Job name → Step logs
3. **Reports:** Actions → Workflow run → Artifacts section

---

## 🐛 Quick Troubleshooting

| Problem | Solution |
|---------|----------|
| Timeout | Increase `timeout_minutes` |
| API key error | Check secrets configuration |
| Docker error | Check Testcontainers logs |
| OOM error | Reduce parallel tests or increase timeout |
| Flaky test | Rerun workflow |

---

## 📚 Full Documentation

- **Detailed Plan:** `INTEGRATION_TESTS_PIPELINE_PLAN.md`
- **Usage Guide:** `docs/INTEGRATION_TESTS_USAGE_GUIDE.md`
- **Implementation:** `INTEGRATION_TESTS_IMPLEMENTATION_SUMMARY.md`

---

## ⚡ Common Scenarios

### Before Merging PR
```bash
# Quick validation with mocked APIs
gh workflow run integration-tests-manual.yml --ref feature/my-branch -f modules=all
```

### Before Release
```bash
# Full validation with real APIs
gh workflow run integration-tests-manual.yml --ref release/v1.0 -f modules=all -f test_profile=real-api-tests
```

### After Fixing Bug in Backend
```bash
# Test only backend
gh workflow run integration-tests-manual.yml --ref fix/backend-bug -f modules=backend
```

### Performance Check
```bash
# Run performance tests
gh workflow run integration-tests-manual.yml --ref main -f modules=ai-infrastructure -f test_profile=performance-tests
```

---

## 🎯 Best Practices

✅ Use `default` profile for quick checks  
✅ Use `real-api-tests` before releases  
✅ Run specific modules during development  
✅ Run `all` modules before merging to main  
✅ Check test reports when tests fail  
✅ Keep API keys secure (never commit!)  

---

**Need Help?** Check the full documentation or create an issue!

**Ready to Test?** → [Go to Actions](https://github.com/YOUR-ORG/YOUR-REPO/actions) 🚀
