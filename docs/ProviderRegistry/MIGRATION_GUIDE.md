# Migration Guide: Old to New Provider System

## Overview

This guide helps you migrate from the old hardcoded provider system to the new extensible Provider Registry system.

## What Changed

### Before (Old System)

- Providers hardcoded in multiple places
- Manual updates required in workflow files
- No centralized provider metadata
- Limited error messages
- Difficult to add new providers

### After (New System)

- Single source of truth: `providers-registry.yml`
- Automatic provider discovery
- Centralized metadata and validation
- Detailed error messages
- Easy to add new providers

## Migration Steps

### Step 1: Understand the New System

Read these guides first:
- [Provider Registry Guide](./PROVIDER_REGISTRY_GUIDE.md)
- [GitHub Actions Provider Guide](./GITHUB_ACTIONS_PROVIDER_GUIDE.md)

### Step 2: Verify Registry

Check that your providers are in the registry:

```bash
bash .github/scripts/validate-provider-registry.sh
```

This will show:
- All providers in registry
- Workflow sync status
- Any issues

### Step 3: Update Environment Variables

The new system uses the same environment variable names, but validation is stricter:

**Old**: Variables could be missing without clear errors
**New**: Clear error messages if variables are missing

Ensure all required variables are set:

```bash
# For OpenAI
export OPENAI_API_KEY="your-key"

# For Anthropic
export ANTHROPIC_API_KEY="your-key"

# For Gemini
export GEMINI_API_KEY="your-key"

# For Cohere
export COHERE_API_KEY="your-key"

# For Azure
export AZURE_API_KEY="your-key"
export AZURE_ENDPOINT="https://your-resource.openai.azure.com/openai/v1"
export AZURE_DEPLOYMENT_NAME="your-deployment"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="your-embedding-deployment"
```

### Step 4: Update Test Execution

#### Old Way

```bash
mvn test -Dtest=RealAPIIntegrationTest \
  -Dai.providers.llm-provider=openai \
  -Dai.providers.embedding-provider=onnx
```

#### New Way (Same, but with better validation)

```bash
# Same command, but now includes:
# - Automatic provider discovery
# - Pre-execution validation
# - Better error messages
mvn test -Dtest=RealAPIIntegrationTest \
  -Dai.providers.llm-provider=openai \
  -Dai.providers.embedding-provider=onnx
```

### Step 5: Update GitHub Actions Usage

#### Old Workflow Inputs

- Limited provider options
- Single API key input
- Manual provider configuration

#### New Workflow Inputs

- All providers available
- Provider-specific API key inputs
- Automatic configuration via composite action

**No changes needed to your workflow runs** - the new system is backward compatible!

### Step 6: Update Custom Scripts

If you have custom scripts that check for providers:

#### Old Way

```bash
if [ -z "$OPENAI_API_KEY" ]; then
    echo "OpenAI key missing"
fi
```

#### New Way

```bash
# Use validation script
bash .github/scripts/validate-provider-availability.sh openai onnx
```

## Breaking Changes

### None!

The new system is **fully backward compatible**. All existing:
- Test commands work the same
- Environment variables work the same
- Provider names work the same
- Configuration files work the same

## New Features You Can Use

### 1. Automatic Provider Discovery

The system now automatically discovers available providers:

```java
// Old: Manual provider list
List<String> providers = Arrays.asList("openai", "anthropic");

// New: Automatic discovery
List<ProviderDefinition> providers = registry.getAvailableLLMProviders();
```

### 2. Better Error Messages

**Old Error**:
```
Provider not available
```

**New Error**:
```
Missing required environment variables: OPENAI_API_KEY. 
Set these variables to use provider 'openai'
```

### 3. Provider Validation

Providers are validated before test execution:

```java
// Automatically validates:
// - Provider exists in registry
// - Required env vars are set
// - Provider is enabled
validateProviderCombination(combo);
```

### 4. Provider Filtering

You can now filter provider combinations:

```java
// Override in test class
protected boolean shouldIncludeCombination(ProviderCombination combo) {
    // Skip certain combinations
    if (combo.llmProvider().equals("provider1") && 
        combo.embeddingProvider().equals("provider2")) {
        return false;
    }
    return true;
}
```

## Compatibility Matrix

| Feature | Old System | New System | Compatible? |
|---------|-----------|------------|-------------|
| Environment Variables | ✅ | ✅ | ✅ Yes |
| Provider Names | ✅ | ✅ | ✅ Yes |
| Test Commands | ✅ | ✅ | ✅ Yes |
| Workflow Inputs | ✅ | ✅ | ✅ Yes (enhanced) |
| Provider Discovery | Manual | Automatic | ✅ Enhanced |
| Error Messages | Basic | Detailed | ✅ Enhanced |
| Provider Registry | ❌ | ✅ | ✅ New Feature |

## Testing Your Migration

### 1. Verify Registry Loads

```bash
cd ai-infrastructure-module
mvn test -Dtest=ProviderRegistryServiceTest
```

Should show: `Tests run: 10, Failures: 0`

### 2. Test Provider Discovery

```bash
export OPENAI_API_KEY="test-key"
cd integration-Testing/integration-tests
mvn test -Dtest=RealAPIProviderMatrixIntegrationTest -Dtest=providerMatrix
```

Should discover OpenAI automatically.

### 3. Test Validation

```bash
# Should fail with helpful error
unset OPENAI_API_KEY
mvn test -Dtest=RealAPIIntegrationTest \
  -Dai.providers.llm-provider=openai
```

Should show: `Missing required environment variables: OPENAI_API_KEY`

### 4. Test GitHub Actions

1. Trigger workflow manually
2. Select a provider
3. Verify configuration works
4. Check test execution

## Rollback Plan

If you need to rollback:

1. **Remove registry usage**: The system falls back to Spring context discovery automatically
2. **Keep old workflow**: Old workflow inputs still work
3. **No code changes needed**: All changes are additive

## FAQ

### Q: Do I need to update my existing tests?

**A**: No, all existing tests work without changes.

### Q: Do I need to update my CI/CD pipelines?

**A**: No, but you can take advantage of new features like validation scripts.

### Q: What if a provider is missing from the registry?

**A**: The system falls back to Spring context discovery (backward compatible).

### Q: Can I still use hardcoded provider lists?

**A**: Yes, but using the registry is recommended for better error messages.

### Q: How do I add a provider that's not in the registry?

**A**: See [Adding New Provider Guide](./ADDING_NEW_PROVIDER.md)

## Troubleshooting

### Registry Not Found

**Error**: `Provider registry file not found`

**Solution**: Ensure `providers-registry.yml` is in the correct location:
```
ai-infrastructure-module/ai-infrastructure-core/src/main/resources/providers-registry.yml
```

### Provider Not Discovered

**Error**: Provider not in available list

**Solutions**:
1. Check provider is in registry
2. Verify `enabled: true` in registry
3. Ensure required env vars are set
4. Check auto-configuration is correct

### Validation Errors

**Error**: `Provider combination validation failed`

**Solutions**:
1. Check error message for specific issue
2. Verify environment variables
3. Check provider is enabled in registry
4. Review provider definition in registry

## Next Steps

After migration:

1. ✅ Verify all providers work
2. ✅ Test with different provider combinations
3. ✅ Update documentation if needed
4. ✅ Train team on new features
5. ✅ Consider adding custom provider filtering

## Related Documentation

- [Provider Registry Guide](./PROVIDER_REGISTRY_GUIDE.md)
- [GitHub Actions Provider Guide](./GITHUB_ACTIONS_PROVIDER_GUIDE.md)
- [Adding New Provider Guide](./ADDING_NEW_PROVIDER.md)
- [Restructuring Plan V2](../planning/REALAPI_TESTS_RESTRUCTURING_PLAN_V2.md)
