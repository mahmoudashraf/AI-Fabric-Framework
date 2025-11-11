# Migration Guide: From Monolithic to Modular Providers

## Overview

This guide helps you migrate from the current monolithic architecture (all providers in core) to the new modular architecture (separate modules per provider).

## Migration Phases

### Phase 1: Preparation (No Code Changes)

1. **Review Current Configuration**
   - Document your current `application.yml` configuration
   - List all providers you're using
   - Note any custom configurations

2. **Identify Dependencies**
   - Check which provider modules you need
   - Verify API keys are available
   - Plan for any breaking changes

3. **Backup Configuration**
   - Backup your current `application.yml`
   - Document any custom provider configurations

### Phase 2: Update Dependencies

**Before** (monolithic):
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>
```

**After** (modular):
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>

<!-- Add provider modules you need -->
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-provider-openai</artifactId>
</dependency>

<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
</dependency>
```

### Phase 3: Update Configuration

**Before** (monolithic):
```yaml
ai:
  providers:
    embedding-provider: onnx
    openai-api-key: ${OPENAI_API_KEY}
    openai-model: gpt-4o-mini
    openai-embedding-model: text-embedding-3-small
```

**After** (modular):
```yaml
ai:
  providers:
    llm-provider: openai          # NEW: Explicit LLM provider selection
    embedding-provider: onnx       # Keep existing embedding provider
    
    openai:                        # NEW: Nested configuration
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      embedding-model: text-embedding-3-small
    
    onnx:                          # NEW: Explicit ONNX configuration
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
```

### Phase 4: Update Code (If Needed)

Most code changes are not required - the API remains the same. However, if you're directly accessing providers:

**Before**:
```java
@Autowired
private OpenAIProvider openAIProvider;  // Direct injection
```

**After**:
```java
@Autowired
private AIProviderManager providerManager;  // Use manager instead

// Or get specific provider
AIProvider provider = providerManager.getProvider("openai");
```

### Phase 5: Testing

1. **Unit Tests**
   - Update test configurations
   - Add provider module dependencies to test POM
   - Update test profiles

2. **Integration Tests**
   - Test provider auto-discovery
   - Test provider selection
   - Test fallback scenarios

3. **End-to-End Tests**
   - Test with real API keys
   - Verify all features work
   - Check performance

## Migration Scenarios

### Scenario 1: Using OpenAI Only

**Current**:
```yaml
ai:
  providers:
    embedding-provider: openai
    openai-api-key: ${OPENAI_API_KEY}
```

**Migration**:
1. Add OpenAI provider module to POM
2. Update configuration:
```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: openai
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
```

### Scenario 2: Using ONNX Embeddings + OpenAI LLM

**Current**:
```yaml
ai:
  providers:
    embedding-provider: onnx
    openai-api-key: ${OPENAI_API_KEY}
```

**Migration**:
1. Add both modules to POM:
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-provider-openai</artifactId>
</dependency>
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
</dependency>
```

2. Update configuration:
```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
    onnx:
      enabled: true
```

### Scenario 3: Using Multiple Providers

**Current**:
```yaml
ai:
  providers:
    embedding-provider: onnx
    openai-api-key: ${OPENAI_API_KEY}
    # Azure configured but not used
```

**Migration**:
1. Add all provider modules to POM
2. Update configuration:
```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx
    enable-fallback: true
    
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      priority: 100
    
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://...
      priority: 90
    
    onnx:
      enabled: true
```

## Breaking Changes

### 1. Configuration Structure

**Breaking**: Flat configuration → Nested configuration

**Migration**: Update YAML structure (see Phase 3)

### 2. Provider Dependencies

**Breaking**: All providers in core → Separate modules

**Migration**: Add provider modules to POM (see Phase 2)

### 3. Direct Provider Injection

**Breaking**: Direct `@Autowired OpenAIProvider` may not work

**Migration**: Use `AIProviderManager` instead (see Phase 4)

### 4. Default Provider

**Breaking**: Default provider may change

**Migration**: Explicitly set `llm-provider` and `embedding-provider`

## Rollback Plan

If migration fails:

1. **Revert Dependencies**
   - Remove new provider modules
   - Use old core version

2. **Revert Configuration**
   - Restore backup configuration
   - Use old flat structure

3. **Revert Code**
   - Restore direct provider injection
   - Use old API calls

## Migration Checklist

- [ ] Phase 1: Preparation
  - [ ] Review current configuration
  - [ ] Identify dependencies
  - [ ] Backup configuration

- [ ] Phase 2: Update Dependencies
  - [ ] Add provider modules to POM
  - [ ] Update version numbers
  - [ ] Verify dependencies resolve

- [ ] Phase 3: Update Configuration
  - [ ] Update YAML structure
  - [ ] Add explicit provider selection
  - [ ] Test configuration loads

- [ ] Phase 4: Update Code
  - [ ] Replace direct provider injection
  - [ ] Use AIProviderManager
  - [ ] Update any custom code

- [ ] Phase 5: Testing
  - [ ] Run unit tests
  - [ ] Run integration tests
  - [ ] Run end-to-end tests
  - [ ] Verify performance

- [ ] Phase 6: Deployment
  - [ ] Deploy to staging
  - [ ] Monitor logs
  - [ ] Verify functionality
  - [ ] Deploy to production

## Common Issues

### Issue 1: Provider Not Found

**Symptom**: `No provider found: openai`

**Solution**: 
- Verify provider module is in POM
- Check `enabled: true` in configuration
- Verify API key is configured

### Issue 2: Configuration Not Loading

**Symptom**: Default values used instead of configuration

**Solution**:
- Check YAML syntax
- Verify property names match exactly
- Check profile is active

### Issue 3: Bean Creation Failed

**Symptom**: `Error creating bean`

**Solution**:
- Check provider SDK is on classpath
- Verify `@ConditionalOnClass` matches SDK class
- Check API key is valid

## Support

If you encounter issues during migration:

1. Check logs for error messages
2. Verify configuration matches examples
3. Test with minimal configuration
4. Review migration guide
5. Contact support if needed
