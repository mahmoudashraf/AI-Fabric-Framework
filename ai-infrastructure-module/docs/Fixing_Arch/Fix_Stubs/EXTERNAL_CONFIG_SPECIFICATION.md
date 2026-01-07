# External Configuration Specification

## Which Configurations Should Be Loaded from External Sources?

This document specifies which configuration values should be loaded from external configuration backends (Consul, Vault, AWS AppConfig, etc.) when implementing `AIConfigurationService.loadFromExternalSources()`.

---

## Configuration Categories

### 🔴 **Category 1: Secrets & Credentials** (HIGHEST PRIORITY)

**Why External**: Security best practice - secrets should never be in code or config files

#### Provider API Keys
```yaml
# Should load from external source (e.g., Vault, AWS Secrets Manager)
ai.providers.openai.api-key
ai.providers.anthropic.api-key
ai.providers.azure.api-key
ai.providers.cohere.api-key
ai.providers.rest.api-key
```

#### Vector Database Credentials
```yaml
# Should load from external source
ai.providers.pinecone.api-key
ai.providers.pinecone.project-id
ai.providers.weaviate.api-key
ai.providers.qdrant.api-key
ai.providers.milvus.username
ai.providers.milvus.password
```

#### Database & Service Credentials
```yaml
# If using external databases/services
spring.datasource.password
spring.redis.password
# Any other service credentials
```

#### Encryption Keys
```yaml
# Encryption keys for data at rest
ai.security.encryption-key
ai.security.jwt-secret
```

**Implementation Priority**: ⭐⭐⭐⭐⭐ **CRITICAL**

**Recommended Backend**: 
- **Vault** (HashiCorp Vault) - Best for secrets
- **AWS Secrets Manager** - For AWS deployments
- **Azure Key Vault** - For Azure deployments
- **Kubernetes Secrets** - For K8s deployments

---

### 🟡 **Category 2: Runtime-Changeable Configuration** (HIGH PRIORITY)

**Why External**: Enable zero-downtime config updates, A/B testing, feature flags

#### Feature Flags
```yaml
# Should support hot-reload from external source
ai.service.features.enable-rag
ai.service.features.enable-embeddings
ai.service.features.enable-search
ai.service.features.enable-generation
ai.service.features.enable-caching
ai.service.features.enable-monitoring
ai.service.features.enable-analytics
ai.service.features.enable-auto-scaling
ai.service.features.enable-multi-provider
```

#### Provider Selection & Priorities
```yaml
# Runtime provider switching
ai.providers.llm-provider
ai.providers.embedding-provider
ai.providers.enable-fallback
ai.providers.openai.priority
ai.providers.anthropic.priority
ai.providers.azure.priority
```

#### Rate Limiting & Throttling
```yaml
# Adjustable without restart
ai.service.rate-limit.requests-per-minute
ai.service.rate-limit.requests-per-hour
ai.service.rate-limit.requests-per-day
ai.service.rate-limit.enabled
ai.service.rate-limit.strategy
```

#### Timeout & Retry Configuration
```yaml
# Tuneable for performance
ai.service.timeout.default-timeout
ai.service.timeout.embedding-timeout
ai.service.timeout.generation-timeout
ai.service.timeout.search-timeout
ai.service.timeout.rag-timeout
ai.service.retry.max-attempts
ai.service.retry.initial-delay
ai.service.retry.backoff-multiplier
```

#### Circuit Breaker Settings
```yaml
# Adjustable resilience
ai.service.circuit-breaker-enabled
ai.service.circuit-breaker-threshold
ai.service.circuit-breaker-timeout
```

**Implementation Priority**: ⭐⭐⭐⭐ **HIGH**

**Recommended Backend**:
- **Consul** - Key-value store with watch support
- **etcd** - Distributed key-value store
- **AWS AppConfig** - Managed configuration service
- **Spring Cloud Config** - Spring-native solution

---

### 🟢 **Category 3: Environment-Specific Configuration** (MEDIUM PRIORITY)

**Why External**: Different values per environment (dev, staging, prod)

#### Service Endpoints & URLs
```yaml
# Environment-specific endpoints
ai.providers.openai.base-url
ai.providers.azure.endpoint
ai.providers.rest.base-url
ai.providers.weaviate.host
ai.providers.qdrant.host
ai.providers.milvus.host
```

#### Environment Identifiers
```yaml
# Environment context
ai.providers.pinecone.environment
ai.providers.pinecone.index-name
```

#### Model Selection
```yaml
# May vary by environment
ai.providers.openai.model
ai.providers.openai.embedding-model
ai.providers.anthropic.model
ai.providers.cohere.model
```

**Implementation Priority**: ⭐⭐⭐ **MEDIUM**

**Recommended Backend**:
- **Spring Cloud Config** - Profile-based configuration
- **Consul** - Environment-specific keyspaces
- **Kubernetes ConfigMaps** - Environment-specific configs

---

### 🔵 **Category 4: Operational Configuration** (LOW PRIORITY)

**Why External**: Centralized operational control, but less critical

#### Monitoring & Observability
```yaml
ai.service.monitoring.enable-metrics
ai.service.monitoring.enable-tracing
ai.service.monitoring.enable-logging
ai.service.monitoring.metrics-prefix
ai.service.monitoring.metrics-interval
```

#### Health Check Settings
```yaml
ai.service.health-checks-enabled
ai.service.monitoring.enable-health-checks
ai.service.monitoring.health-check-interval
```

#### Logging Configuration
```yaml
ai.service.logging-enabled
# Log levels, appenders, etc.
```

#### Cache Configuration
```yaml
ai.service.cache.enabled
ai.service.cache.default-ttl
ai.service.cache.max-size
ai.service.cache.eviction-policy
```

**Implementation Priority**: ⭐⭐ **LOW**

**Recommended Backend**:
- **Consul** - For centralized management
- **Spring Cloud Config** - Standard Spring approach

---

### ⚪ **Category 5: Static/Application Configuration** (NOT RECOMMENDED)

**Why NOT External**: Application-level settings that rarely change

#### Application Structure
```yaml
# Keep in application.yml/properties
ai.service.enabled
ai.service.auto-configuration
ai.service.thread-pool-size
ai.service.batch-size
```

#### Model Paths (ONNX)
```yaml
# Application resources
ai.providers.onnx.model-path
ai.providers.onnx.tokenizer-path
```

#### Default Values
```yaml
# Framework defaults
ai.providers.onnx.max-sequence-length
ai.providers.onnx.use-gpu
```

**Implementation Priority**: ⭐ **NOT RECOMMENDED**

**Reason**: These are application-level settings that should be version-controlled with the application code.

---

## Recommended Implementation Strategy

### Phase 1: Secrets Only (Minimum Viable)

```java
private void loadFromExternalSources() {
    if (secretsBackendEnabled) {
        // Load ONLY secrets/credentials
        loadSecretsFromVault();
        // or loadSecretsFromAWSSecretsManager();
        // or loadSecretsFromAzureKeyVault();
    }
}
```

**Load**:
- All API keys
- All passwords
- Encryption keys

**Don't Load**:
- Everything else (use Spring Boot config)

---

### Phase 2: Secrets + Feature Flags

```java
private void loadFromExternalSources() {
    if (secretsBackendEnabled) {
        loadSecretsFromVault();
    }
    
    if (configBackendEnabled) {
        // Load runtime-changeable config
        loadFeatureFlagsFromConsul();
        loadProviderConfigFromConsul();
    }
}
```

**Load**:
- Secrets (from Vault)
- Feature flags (from Consul/AppConfig)
- Provider priorities
- Rate limits

---

### Phase 3: Full External Configuration

```java
private void loadFromExternalSources() {
    // Secrets from Vault
    if (vaultEnabled) {
        loadSecretsFromVault();
    }
    
    // Runtime config from Consul/AppConfig
    if (configBackendEnabled) {
        loadRuntimeConfigFromBackend();
    }
    
    // Environment-specific from ConfigMaps
    if (k8sConfigMapEnabled) {
        loadEnvironmentConfigFromK8s();
    }
}
```

**Load**:
- All Category 1 (Secrets)
- All Category 2 (Runtime config)
- All Category 3 (Environment-specific)
- Selected Category 4 (Operational)

---

## Configuration Key Mapping

### Proposed Key Structure for External Backend

```
# Vault Path Structure (for secrets)
secret/ai-fabric/{environment}/providers/openai/api-key
secret/ai-fabric/{environment}/providers/anthropic/api-key
secret/ai-fabric/{environment}/vector-db/pinecone/api-key

# Consul Key Structure (for runtime config)
ai-fabric/{environment}/features/enable-rag
ai-fabric/{environment}/features/enable-embeddings
ai-fabric/{environment}/providers/llm-provider
ai-fabric/{environment}/rate-limit/requests-per-minute

# Spring Cloud Config Structure
ai-fabric-{environment}.yml
  ai:
    providers:
      openai:
        api-key: ${vault:secret/ai-fabric/providers/openai/api-key}
    service:
      features:
        enable-rag: ${consul:ai-fabric/features/enable-rag}
```

---

## Priority Summary Table

| Category | Priority | Load from External? | Backend Type | Example |
|----------|----------|---------------------|--------------|---------|
| **Secrets & Credentials** | ⭐⭐⭐⭐⭐ | ✅ **YES** | Vault/Secrets Manager | API keys, passwords |
| **Feature Flags** | ⭐⭐⭐⭐ | ✅ **YES** | Consul/AppConfig | enable-rag, enable-embeddings |
| **Rate Limits** | ⭐⭐⭐⭐ | ✅ **YES** | Consul/AppConfig | requests-per-minute |
| **Provider Config** | ⭐⭐⭐⭐ | ✅ **YES** | Consul/AppConfig | llm-provider, priorities |
| **Timeouts/Retries** | ⭐⭐⭐ | ✅ **OPTIONAL** | Consul/AppConfig | timeout values |
| **Endpoints/URLs** | ⭐⭐⭐ | ✅ **OPTIONAL** | ConfigMap/Consul | base-url, host |
| **Monitoring** | ⭐⭐ | ⚠️ **MAYBE** | Consul | metrics settings |
| **Static Config** | ⭐ | ❌ **NO** | application.yml | thread-pool-size |

---

## Implementation Example

### Minimal Implementation (Phase 1)

```java
private void loadFromExternalSources() {
    // Only load secrets - highest priority
    if (vaultEnabled) {
        try {
            // Load API keys from Vault
            String openaiKey = vaultClient.read("secret/ai-fabric/providers/openai/api-key");
            if (openaiKey != null) {
                setConfig("ai.providers.openai.api-key", openaiKey);
            }
            
            String anthropicKey = vaultClient.read("secret/ai-fabric/providers/anthropic/api-key");
            if (anthropicKey != null) {
                setConfig("ai.providers.anthropic.api-key", anthropicKey);
            }
            
            // Load vector DB credentials
            String pineconeKey = vaultClient.read("secret/ai-fabric/vector-db/pinecone/api-key");
            if (pineconeKey != null) {
                setConfig("ai.providers.pinecone.api-key", pineconeKey);
            }
            
            log.info("Loaded {} secrets from Vault", dynamicConfig.size());
        } catch (Exception e) {
            log.error("Failed to load secrets from Vault", e);
        }
    }
}
```

---

## Conclusion

### What to Load from External Sources:

1. **✅ MUST LOAD**: All secrets and credentials (API keys, passwords)
2. **✅ SHOULD LOAD**: Feature flags, rate limits, provider config
3. **⚠️ MAY LOAD**: Environment-specific endpoints, monitoring settings
4. **❌ DON'T LOAD**: Static application configuration

### Recommended Approach:

**Start with Phase 1** (Secrets only) - This provides the most value with minimal complexity. Secrets management is critical for security and is the primary use case for external configuration backends.

**Expand to Phase 2** (Feature flags) when you need runtime configuration changes without restarts.

**Full Phase 3** is only needed for complex multi-environment, multi-tenant deployments.

---

## References

- `AIProviderConfig.java` - Provider configuration structure
- `AIServiceConfig.java` - Service configuration structure
- `AIConfigurationService.java:286-289` - Current placeholder
- Spring Cloud Config Documentation
- HashiCorp Vault Documentation
- AWS AppConfig Documentation

