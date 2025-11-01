# ONNX No-Fallback Configuration Guide

## Overview

This document explains how to ensure **ONNX is the only embedding provider** used, with **no fallback** to other providers (OpenAI, REST).

---

## Configuration

### 1. Auto-Configuration Settings

The `AIInfrastructureAutoConfiguration` class has been configured to:

- ✅ **ONNX is @Primary**: Marked with `@Primary` and `@Order(HIGHEST_PRECEDENCE)`
- ✅ **ONNX is Default**: Uses `@ConditionalOnProperty(..., matchIfMissing = true)` so it's created by default
- ✅ **Other Providers Conditional**: REST and OpenAI only created when explicitly requested
- ✅ **Validation Logging**: Warns if ONNX is not available or not being used

### 2. Application Configuration

Set in your `application.yml` or `application-onnx-test.yml`:

```yaml
ai:
  enabled: true
  providers:
    # CRITICAL: Set embedding-provider to "onnx"
    embedding-provider: onnx  # Must be "onnx" (not "rest" or "openai")
    
    # ONNX Configuration
    onnx-model-path: ./models/embeddings/all-MiniLM-L6-v2.onnx
    onnx-tokenizer-path: ./models/embeddings/tokenizer.json
    onnx-max-sequence-length: 512
    onnx-use-gpu: false
    
    # DO NOT SET: These would enable other providers
    # embedding-provider: rest  # ❌ Would use REST instead
    # embedding-provider: openai  # ❌ Would use OpenAI instead
    # openai-api-key: ...  # ❌ Not needed - won't be used
```

---

## How It Works

### Bean Creation Order

1. **ONNX Provider** (Always created if `embedding-provider=onnx` or not set)
   ```java
   @Bean
   @Primary
   @Order(HIGHEST_PRECEDENCE)
   @ConditionalOnProperty(name = "ai.providers.embedding-provider", 
                         havingValue = "onnx", 
                         matchIfMissing = true)
   public EmbeddingProvider onnxEmbeddingProvider(...) {
       // Creates ONNX provider
   }
   ```

2. **REST Provider** (Only if `embedding-provider=rest`)
   ```java
   @Bean
   @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "rest")
   @ConditionalOnMissingBean(name = "onnxEmbeddingProvider")  // Won't create if ONNX exists
   public EmbeddingProvider restEmbeddingProvider(...) {
       // Only created if explicitly requested AND ONNX not available
   }
   ```

3. **OpenAI Provider** (Only if `embedding-provider=openai`)
   ```java
   @Bean
   @ConditionalOnProperty(name = "ai.providers.embedding-provider", havingValue = "openai")
   @ConditionalOnMissingBean(name = "onnxEmbeddingProvider")  // Won't create if ONNX exists
   public EmbeddingProvider openaiEmbeddingProvider(...) {
       // Only created if explicitly requested AND ONNX not available
   }
   ```

### Dependency Injection

`AIEmbeddingService` is injected with the `@Primary` bean (ONNX):

```java
@Bean
public AIEmbeddingService aiEmbeddingService(AIProviderConfig config, 
                                             EmbeddingProvider embeddingProvider) {
    // Spring will inject the @Primary bean (ONNX) if multiple providers exist
    // Logs warning if not using ONNX
}
```

---

## Validation & Logging

### Startup Logs

When using ONNX correctly, you'll see:

```
INFO  Creating ONNX Embedding Provider (primary/default)
INFO  ONNX will be used for all embedding generation - no fallback to other providers
INFO  ✅ AIEmbeddingService configured to use ONNX provider (no fallback to other providers)
```

### If ONNX Not Available

```
WARN  WARNING: ONNX Embedding Provider is not available. Model file may be missing.
WARN  Please ensure the ONNX model file exists at: ./models/embeddings/all-MiniLM-L6-v2.onnx
```

### If Wrong Provider Used

```
WARN  WARNING: Not using ONNX provider. Current provider: rest
WARN  WARNING: AIEmbeddingService is NOT using ONNX provider. Current provider: rest
```

---

## Ensuring No Fallback

### 1. Configuration Check

```yaml
# ✅ CORRECT: ONNX will be used
ai:
  providers:
    embedding-provider: onnx

# ❌ WRONG: Would use REST instead
ai:
  providers:
    embedding-provider: rest

# ❌ WRONG: Would use OpenAI instead
ai:
  providers:
    embedding-provider: openai
```

### 2. Runtime Validation

The `AIEmbeddingService` validates provider at runtime:

```java
String providerName = embeddingProvider.getProviderName();
if (!providerName.equals("onnx")) {
    log.warn("WARNING: Not using ONNX provider. Current provider: {}", providerName);
} else {
    log.debug("Using ONNX provider for embedding generation (no fallback)");
}
```

### 3. Provider Availability

ONNX provider checks availability:

```java
@Override
public boolean isAvailable() {
    return ortSession != null && ortEnvironment != null;
}
```

If ONNX is not available, embedding generation will **fail** (no automatic fallback):

```java
if (embeddingProvider == null || !embeddingProvider.isAvailable()) {
    throw new AIServiceException("Embedding provider is not available");
}
```

---

## Testing Configuration

### Integration Test Profile

`application-onnx-test.yml`:

```yaml
ai:
  enabled: true
  providers:
    # ONNX ONLY - no fallback
    embedding-provider: onnx
    onnx-model-path: ./models/embeddings/all-MiniLM-L6-v2.onnx
    # ... other ONNX config
    
    # Other providers NOT configured
    # openai-api-key: ...  # Not set - prevents OpenAI provider
```

### Test Execution

```bash
# Run tests with ONNX profile
mvn test -Dtest=SimpleIntegrationTest -Dspring.profiles.active=onnx-test

# Verify logs show ONNX being used
# Should see: "Using ONNX provider for embedding generation"
```

---

## Troubleshooting

### Issue: Wrong Provider Being Used

**Symptoms**: Logs show "WARNING: Not using ONNX provider"

**Solutions**:
1. Check `application.yml`: Ensure `embedding-provider: onnx`
2. Check profile: Ensure correct profile is active
3. Check logs: Look for "Creating ONNX Embedding Provider"

### Issue: ONNX Not Available

**Symptoms**: "ONNX Embedding Provider is not available"

**Solutions**:
1. Check model file exists: `./models/embeddings/all-MiniLM-L6-v2.onnx`
2. Check path configuration: Verify `onnx-model-path` in config
3. Check logs: Look for model file path resolution

### Issue: Fallback Occurring

**Symptoms**: Using REST/OpenAI when ONNX should be used

**Solutions**:
1. Verify `embedding-provider=onnx` in configuration
2. Check Spring bean creation logs
3. Ensure `@Primary` annotation is on ONNX bean (it is!)
4. Check no other profile overriding settings

---

## Summary

✅ **ONNX is always used** when:
- `embedding-provider=onnx` (or not set)
- Model file exists and is accessible
- ONNX provider successfully initializes

✅ **No fallback occurs** because:
- Other providers only created when explicitly requested
- `@ConditionalOnMissingBean` prevents creation if ONNX exists
- Runtime validation throws exception if provider unavailable

✅ **Validation ensures ONNX**:
- Logging confirms ONNX provider creation
- Runtime checks verify correct provider
- Warnings if wrong provider detected

---

## Quick Reference

| Configuration | Provider Used |
|---------------|---------------|
| `embedding-provider: onnx` | ✅ ONNX |
| `embedding-provider: rest` | ❌ REST (ONNX not used) |
| `embedding-provider: openai` | ❌ OpenAI (ONNX not used) |
| Not set (default) | ✅ ONNX (matchIfMissing=true) |

---

**Result**: With proper configuration, **ONNX is the only provider used** with **no automatic fallback** to other providers! 🚀

