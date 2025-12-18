# Getting Started with Dynamic Provider Matrix Integration Tests

## 🚀 Quick Start (2 minutes)

### Step 1: Set Your API Key
```bash
export OPENAI_API_KEY="sk-..."
```

### Step 2: Run a Test
```bash
cd /workspace

# Run with OpenAI + ONNX (cheapest option)
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx
```

### Step 3: Check Results
The output will show:
```
✓ [1] PASSED: LLM=openai | Embedding=onnx
```

**That's it! Your first provider matrix test is running.** ✨

---

## 📖 Documentation

Choose your learning style:

| Document | Best For | Length |
|----------|----------|--------|
| **PROVIDER_MATRIX_QUICK_REFERENCE.md** | Quick lookup, command examples | 500 lines |
| **DYNAMIC_PROVIDER_MATRIX_GUIDE.md** | Complete documentation, tutorials | 2,200 lines |
| **IMPLEMENTATION_SUMMARY_PROVIDER_MATRIX.md** | Technical details, architecture | 400 lines |
| **PROVIDER_MATRIX_DEPLOYMENT_CHECKLIST.md** | Deployment, validation | 300 lines |

**Recommended order for first-time users**:
1. Start with this file (GETTING_STARTED_PROVIDER_MATRIX.md)
2. Use PROVIDER_MATRIX_QUICK_REFERENCE.md for commands
3. Read DYNAMIC_PROVIDER_MATRIX_GUIDE.md if you need more details

---

## 💡 Common Commands

### Cost Optimization (Fastest & Cheapest)
```bash
export OPENAI_API_KEY="sk-..."

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx
```
- **Time**: 45-60 seconds
- **Cost**: ~$0.10-0.15
- **Perfect for**: Development, quick validation

### Enterprise Azure
```bash
export AZURE_OPENAI_API_KEY="..."
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com"

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=azure:azure
```
- **Time**: 50-65 seconds
- **Cost**: Depends on your Azure pricing
- **Perfect for**: Enterprise validation

### Best Quality (Claude LLM)
```bash
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=anthropic:openai
```
- **Time**: 50-65 seconds
- **Cost**: ~$0.30-0.40
- **Perfect for**: Quality validation

### Comprehensive Testing (All Combinations)
```bash
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."
export AZURE_OPENAI_API_KEY="..."
export AZURE_OPENAI_ENDPOINT="https://..."

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx,openai:openai,anthropic:openai,azure:azure"
```
- **Time**: 3-4 minutes
- **Cost**: ~$0.50-0.80
- **Perfect for**: Pre-release testing, provider comparison

---

## 📋 Matrix Syntax

### Basic Format
```
llm:embedding
```

**Examples**:
- `openai:onnx` - OpenAI LLM with local embeddings
- `anthropic:openai` - Claude LLM with OpenAI embeddings
- `azure:azure` - Azure for everything
- `openai:openai` - OpenAI for everything

### Multiple Combinations
```
combo1,combo2,combo3
```

**Example**:
```bash
-Dai.providers.real-api.matrix="openai:onnx,anthropic:openai,azure:azure"
```

This runs the entire test suite **3 times**:
1. Once with OpenAI + ONNX
2. Once with Anthropic + OpenAI embeddings
3. Once with Azure + Azure

### Extended Format (Future)
```
llm:embedding:vectordb
```

**Example** (when vector DB selection is enabled):
```bash
-Dai.providers.real-api.matrix="openai:onnx:memory,openai:onnx:pinecone"
```

---

## 🔧 Available Providers

### LLM Providers
- **openai** - GPT-4o-mini (default)
- **anthropic** - Claude 3.5 Sonnet
- **azure** - Azure OpenAI
- cohere - (coming soon)

### Embedding Providers
- **onnx** - Local (all-MiniLM-L6-v2)
- **openai** - text-embedding-3-small
- **azure** - text-embedding-ada-002
- **rest** - Custom REST endpoint
- cohere - (coming soon)

### Vector Databases
- **memory** - In-memory (test/dev)
- **lucene** - Embedded search
- qdrant - Open-source vector DB
- weaviate - Vector database
- milvus - Vector DB platform
- pinecone - Serverless (coming soon)

---

## 🔐 Environment Variables

### Minimum Required
```bash
export OPENAI_API_KEY="sk-your-key-here"
```

### Optional (for other providers)
```bash
export ANTHROPIC_API_KEY="sk-ant-your-key"
export AZURE_OPENAI_API_KEY="your-azure-key"
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com"
```

### Where to Get Keys
- **OpenAI**: https://platform.openai.com/api-keys
- **Anthropic**: https://console.anthropic.com/
- **Azure**: Azure Portal → Cognitive Services → OpenAI

---

## 📊 What Gets Tested

For each provider combination, these 11 test classes run:

1. ✓ Core API functionality
2. ✓ ONNX fallback behavior
3. ✓ Input validation
4. ✓ Vector operations
5. ✓ Hybrid search toggle
6. ✓ Intent history tracking
7. ✓ Error recovery
8. ✓ Workflow execution
9. ✓ Multi-provider failover
10. ✓ Smart recommendations
11. ✓ PII detection & redaction

**Total**: 11 tests × number of combinations

---

## ⏱️ Performance Expectations

| Setup | Duration | Cost |
|-------|----------|------|
| Single (openai:onnx) | 45-60s | ~$0.10 |
| Dual (openai:onnx + anthropic:openai) | 2 min | ~$0.40 |
| Full matrix (4 combinations) | 3.5-4.5 min | ~$0.60 |

---

## 🐛 Troubleshooting

### "API Key is not configured"
```bash
# Set your API key
export OPENAI_API_KEY="sk-..."

# Verify it's set
echo $OPENAI_API_KEY  # Should print your key
```

### "Provider not available"
```bash
# Check available providers by listing what's discovered
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test
# This shows all available combinations
```

### "Tests timeout"
- Increase timeout in configuration
- Check your API rate limits
- Verify network connectivity

### "Maven command not found"
```bash
# Install Maven or use
./mvnw -pl integration-tests -am test ...  # If using Maven wrapper
```

---

## 🚀 Using the Shell Script

Instead of typing the long Maven command:

```bash
cd /workspace/ai-infrastructure-module/integration-tests

# Run with default (openai:onnx)
./run-provider-matrix-tests.sh

# Run specific combination
./run-provider-matrix-tests.sh "openai:onnx"

# Run multiple combinations
./run-provider-matrix-tests.sh "openai:onnx,anthropic:openai"
```

**The script**:
- ✓ Checks Java and Maven are installed
- ✓ Verifies API keys are set
- ✓ Runs with proper logging
- ✓ Shows timing and results

---

## 📚 Next Steps

1. **Try a test**: Run the Quick Start command above
2. **Pick your provider combo**: Use PROVIDER_MATRIX_QUICK_REFERENCE.md
3. **Learn the details**: Read DYNAMIC_PROVIDER_MATRIX_GUIDE.md
4. **For your project**: Update your CI/CD pipeline
5. **For benchmarking**: Run multiple combinations and compare

---

## 💬 Examples

### Example 1: Development Loop
You're testing locally and want to save money:
```bash
# Fast, cheap testing (45 seconds, ~$0.10)
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx
```

### Example 2: Pre-Release Testing
Testing before shipping to production:
```bash
# Comprehensive testing (4 minutes, ~$0.60)
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx,openai:openai,anthropic:openai,azure:azure"
```

### Example 3: Provider Evaluation
Deciding which provider to use:
```bash
# Compare Azure vs OpenAI
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx,azure:azure"

# Then compare results and choose the best one
```

### Example 4: CI/CD Pipeline
Running tests on every commit:
```bash
# In your GitHub Actions / GitLab CI / Jenkins
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx"
```

---

## 📖 Complete Documentation

For comprehensive information:

- **Quick Answers**: PROVIDER_MATRIX_QUICK_REFERENCE.md
- **Full Guide**: DYNAMIC_PROVIDER_MATRIX_GUIDE.md
- **Architecture**: IMPLEMENTATION_SUMMARY_PROVIDER_MATRIX.md
- **Deployment**: PROVIDER_MATRIX_DEPLOYMENT_CHECKLIST.md

---

## ✅ Success Indicators

You'll know it's working when you see:

```
═══════════════════════════════════════════════════════════════
Provider Matrix Integration Tests - Starting
Total combinations to test: 1
─────────────────────────────────────────────────────────────
  • LLM=openai | Embedding=onnx
═══════════════════════════════════════════════════════════════

[1/1] Running tests for: LLM=openai | Embedding=onnx
...
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0
✓ [1] PASSED: LLM=openai | Embedding=onnx
```

---

## 🎯 Key Takeaways

1. **One Command Away**: `mvn test -Dai.providers.real-api.matrix=openai:onnx`
2. **Flexible**: Use any LLM + any embedding provider
3. **Scalable**: Test single or multiple combinations
4. **Fast**: 45-60 seconds per combination
5. **Documented**: Comprehensive guides and examples
6. **Cost-Effective**: Optimize for your budget
7. **Production-Ready**: Used across the infrastructure

---

## 🆘 Need Help?

1. **Quick commands?** → PROVIDER_MATRIX_QUICK_REFERENCE.md
2. **How does it work?** → DYNAMIC_PROVIDER_MATRIX_GUIDE.md
3. **Architecture details?** → IMPLEMENTATION_SUMMARY_PROVIDER_MATRIX.md
4. **Deploying?** → PROVIDER_MATRIX_DEPLOYMENT_CHECKLIST.md
5. **Something wrong?** → Check troubleshooting section above

---

## 🎉 Ready?

**Go run your first test!**

```bash
export OPENAI_API_KEY="sk-..."

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx
```

Your integration tests are now dynamic, flexible, and ready for any provider combination! 🚀

---

**Version**: 1.0  
**Status**: ✅ Production Ready  
**Last Updated**: 2025-11-14
