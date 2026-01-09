# Integration Tests Consistency Fixes

## Summary

This document tracks the fixes applied to ensure all integration test modules work consistently with provider selection and configuration.

## Issues Fixed

### 1. Provider Enablement Missing
**Problem**: Cohere and other providers were not being enabled when selected, causing "No AI providers available" errors.

**Root Cause**: 
- Test scripts didn't set `ai.providers.{provider}.enabled=true` system properties
- GitHub Actions workflow didn't set `{PROVIDER}_ENABLED=true` environment variables
- Application YAML files had incomplete provider configurations

**Files Fixed**:
- ✅ `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/run-relationship-query-realapi-tests.sh`
- ✅ `ai-infrastructure-module/integration-Testing/behavior-integration-tests/run-behavior-realapi-tests.sh`
- ✅ `.github/actions/configure-providers/action.yml`
- ✅ `.github/workflows/integration-tests-manual.yml`

### 2. Missing Provider Configurations
**Problem**: Relationship-query and behavior modules didn't have complete provider configurations in their YAML files.

**Files Fixed**:
- ✅ `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests/src/test/resources/application-realapi.yml`
- ✅ `ai-infrastructure-module/integration-Testing/behavior-integration-tests/src/test/resources/application.yml`

### 3. Hardcoded Provider Dependencies
**Problem**: Many RealAPI tests had hardcoded OpenAI dependencies, causing tests to abort when other providers were selected.

**Files Fixed**:
- ✅ `RealAPITestSupport.java` - Made provider-agnostic
- ✅ All test classes with `assumeOpenAIConfigured()` methods (10 files)
- ✅ All test static blocks with hardcoded `System.setProperty("LLM_PROVIDER", "openai")` (8 files)

### 4. Missing Model Defaults
**Problem**: When providers were selected without specifying models, validation failed.

**Files Fixed**:
- ✅ `.github/workflows/integration-tests-manual.yml` - Added default models for OpenAI, Anthropic, Cohere
- ✅ Workflow steps for relationship-query and behavior modules

### 5. Inconsistent Spring Profile Usage
**Problem**: Scripts didn't explicitly set Spring profiles, causing configuration mismatches.

**Files Fixed**:
- ✅ `run-relationship-query-realapi-tests.sh` - Added `-Dspring.profiles.active=realapi`
- ✅ `run-behavior-realapi-tests.sh` - Added `-Dspring.profiles.active=realapi`

## Consistency Checklist

### All Modules Now Have:
- ✅ Dynamic API key checking based on selected providers
- ✅ Provider enablement logic in test scripts
- ✅ Complete provider configurations in YAML files (OpenAI, Anthropic, Gemini, Cohere, Azure, ONNX)
- ✅ Model defaults in GitHub Actions workflow
- ✅ Provider-agnostic test assumptions
- ✅ Consistent Spring profile usage

### Test Scripts Pattern:
All three modules (`integration-tests`, `relationship-query-integration-tests`, `behavior-integration-tests`) now follow the same pattern:

1. **API Key Validation**: Dynamic checking based on selected providers
2. **Provider Enablement**: Sets `-Dai.providers.{provider}.enabled=true` and `-D{PROVIDER}_ENABLED=true`
3. **Spring Profile**: Sets `-Dspring.profiles.active={profile}`
4. **Model Configuration**: Supports model overrides via environment variables

### Application YAML Pattern:
All modules now have:
- Provider configurations for all supported providers
- Environment variable support for dynamic configuration
- Model defaults that can be overridden
- Enabled flags that respect environment variables

## Remaining Work (From Analysis Document)

### High Priority (Not Blocking Current Functionality):
1. ⏳ **Unified Test Runner** - Create `run-unified-realapi-tests.sh` for all modules
2. ⏳ **Matrix Test Classes** - Create `RelationshipQueryProviderMatrixIT` and `BehaviorProviderMatrixIT` for test chunking

### Medium Priority:
3. ⏳ **Provider Configuration Resolver** - Java utility class to reduce shell script duplication
4. ⏳ **GitHub Actions Matrix Input** - Support multiple provider combinations in single run

### Low Priority:
5. ⏳ **Discovery Caching** - Cache provider discovery results

## Testing Verification

To verify all modules work consistently:

```bash
# Test with Cohere (previously failing)
cd ai-infrastructure-module/integration-Testing/integration-tests
COHERE_API_KEY=xxx bash run-provider-matrix-tests.sh "cohere:onnx:lucene"

cd ../relationship-query-integration-tests
COHERE_API_KEY=xxx bash run-relationship-query-realapi-tests.sh "cohere:cohere:lucene"

cd ../behavior-integration-tests
COHERE_API_KEY=xxx bash run-behavior-realapi-tests.sh "cohere:onnx:lucene"
```

All should now:
- ✅ Enable Cohere provider correctly
- ✅ Use default Cohere models if not specified
- ✅ Pass provider validation
- ✅ Execute tests successfully
