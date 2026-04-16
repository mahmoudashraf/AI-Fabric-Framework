# External Configuration Cleanup - Consolidated Implementation Plan

**Version:** 1.0
**Date:** January 2026
**Status:** Ready for Implementation
**Approach:** Greenfield - No Backward Compatibility

---

## Executive Summary

This document consolidates two related cleanup initiatives:
1. **AIConfigurationService Cleanup** - Remove redundant configuration loading service
2. **Provider Configuration Validation** - Remove hard-coded defaults and add validation

Both initiatives align with the AI Fabric Framework philosophy of clean architecture, Spring Boot best practices, and greenfield development without backward compatibility concerns.

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Problems Identified](#problems-identified)
3. [Implementation Strategy](#implementation-strategy)
4. [Detailed Changes](#detailed-changes)
5. [Validation Requirements](#validation-requirements)
6. [Configuration Examples](#configuration-examples)
7. [Migration Path](#migration-path)
8. [Testing Strategy](#testing-strategy)

---

## Current State Analysis

### Existing Configuration Services (Validated Against Source Code)

#### 1. AIConfigurationService (config package) - `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIConfigurationService.java`

**Purpose:** Dynamic configuration management with hot-reload
**Status:** Redundant - duplicates Spring Boot functionality
**Key Features:**
- Hot-reload support (300s default interval)
- Dynamic config map (ConcurrentHashMap)
- Environment variable loading (AI_* prefix)
- External source loading (placeholder)
- Validation logic

**Dependencies:**
- AIProviderConfig
- AIServiceConfig

**Usage:** Registered as bean in AIInfrastructureAutoConfiguration (line 365-367)

#### 2. AIConfigurationService (service package) - `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/service/AIConfigurationService.java`

**Purpose:** Configuration retrieval and validation service
**Status:** Keep with modifications
**Key Features:**
- DTO generation (AIConfigurationDto)
- Provider validation (OpenAI, Anthropic, Cohere, Azure)
- Service config validation (timeouts, retries, etc.)
- Feature flag checking
- Configuration summary

**Dependencies:**
- AIProviderConfig
- AIServiceConfig

**Usage:** Registered as `@Service("aiServiceConfigurationService")`

**Consumers:**
- AIHealthIndicator (line 27) - Uses for health checks

#### 3. AIProviderConfig - `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

**Purpose:** Provider configuration properties
**Status:** Needs cleanup - remove hard-coded defaults
**Current Hard-coded Defaults:**

**OpenAI (lines 126-151):**
- baseUrl: `https://api.openai.com/v1`
- model: `gpt-4o-mini`
- maxTokens: `2000`
- temperature: `0.3`
- timeout: `60`
- priority: `100`
- embeddingModel: `text-embedding-3-small`

**Azure (lines 154-178):**
- apiVersion: `2024-02-15-preview`
- timeout: `60`
- priority: `90`

**Anthropic (lines 180-201):**
- baseUrl: `https://api.anthropic.com/v1`
- model: `claude-3-opus-20240229`
- maxTokens: `4096`
- temperature: `0.3`
- timeout: `60`
- priority: `80`

**Cohere (lines 203-229):**
- baseUrl: `https://api.cohere.ai/v1`
- model: `command`
- maxTokens: `2000`
- temperature: `0.3`
- timeout: `60`
- priority: `70`
- embeddingModel: `embed-english-v3.0`

**ONNX (lines 231-243):**
- modelPath: `classpath:/models/embeddings/all-MiniLM-L6-v2.onnx`
- tokenizerPath: `classpath:/models/embeddings/tokenizer.json`
- maxSequenceLength: `512`
- useGpu: `false`
- modelAlias: `all-MiniLM-L6-v2`

**Weaviate (lines 245-254):**
- scheme: `https`
- port: `443`
- timeout: `30`
- consistencyLevelStrong: `false`

**Qdrant (lines 256-265):**
- host: `localhost`
- port: `6333`
- timeout: `30`
- grpcPort: `6334`
- preferGrpc: `false`

**Milvus (lines 267-277):**
- host: `localhost`
- port: `19530`
- username: `""`
- password: `""`
- databaseName: `default`
- timeout: `30`
- secure: `false`

**Rest (lines 279-291):**
- baseUrl: `http://localhost:8000`
- endpoint: `/embed`
- batchEndpoint: `/embed/batch`
- timeout: `30000`
- model: `all-MiniLM-L6-v2`

**Pinecone (lines 293-300):**
- environment: `us-east-1-aws`
- indexName: `ai-infrastructure`
- dimensions: `1536`

---

## Problems Identified

### Problem 1: Duplicate Configuration Loading (AIConfigurationService in config package)

**Issue:** Framework duplicates Spring Boot's configuration mechanisms

**Evidence:**
```java
// Line 254-272: Loads from environment variables
private void loadFromEnvironment() {
    String[] envKeys = { "AI_DEFAULT_PROVIDER", ... };
    // Spring Boot already does this via @ConfigurationProperties
}

// Line 233-248: loadConfiguration() - redundant with Spring Boot
// Line 38: ConcurrentHashMap dynamicConfig - unused by consumers
// Line 294-309: Hot-reload setup - should use Spring Cloud Config
```

**Impact:**
- Maintenance burden for duplicate functionality
- Confusion about which configuration source to use
- Unused hot-reload infrastructure
- Complexity without benefit

### Problem 2: Service Package AIConfigurationService Validation Logic

**Issue:** Validation exists but should be extracted to dedicated validator

**Evidence:**
```java
// Lines 194-266: Switch-based validation mixed with service logic
// Lines 269-307: Service config validation mixed with retrieval
```

**Impact:**
- Single Responsibility Principle violation
- Hard to test validation independently
- Not aligned with framework philosophy

### Problem 3: Hard-coded Provider Defaults

**Issue:** AIProviderConfig contains environment-specific values as defaults

**Categories:**

**Should Remove (Required Values):**
- API keys (must be supplied by user)
- Endpoints (environment-specific: OpenAI, Azure, Anthropic endpoints)
- Deployment names (Azure-specific)
- Index names (user-specific)

**Should Externalize (Environment-specific):**
- Model names (changes frequently)
- Timeouts (environment-dependent)
- Priorities (user preference)
- Base URLs (may use proxies)
- Host/port combinations (deployment-specific)

**Can Keep (Framework Defaults):**
- Boolean flags (enabled, useGpu, secure)
- Consistency levels
- Sequence lengths

**Impact:**
- Users can't easily identify required configuration
- Hard-coded values may be wrong for their environment
- Updates to models require framework updates
- Confuses "optional" with "required" configuration

### Problem 4: AIHealthIndicator Dependency

**Issue:** Health indicator depends on config service that will be removed

**Evidence:**
```java
// Line 27: private final AIConfigurationService configurationService;
// Line 33: Uses configurationService.getHealthStatus()
```

**Impact:**
- Breaking change when removing config service
- Needs migration to direct config bean injection

---

## Implementation Strategy

### Approach: Greenfield Cleanup (No Backward Compatibility)

Following the AI Fabric Framework philosophy:
- Remove wrong code immediately
- No deprecated methods
- Clean breaks over compatibility hacks
- Trust users to migrate properly

### Three-Phase Implementation

#### Phase 1: Provider Configuration Cleanup
1. Create AIProviderConfigValidator service
2. Remove hard-coded defaults from AIProviderConfig
3. Update application-*.yml with explicit configuration examples
4. Add startup validation with fail-fast behavior

#### Phase 2: Service Consolidation
1. Extract validation logic to AIProviderConfigValidator
2. Simplify service package AIConfigurationService
3. Update AIHealthIndicator to use configs directly
4. Remove redundant methods

#### Phase 3: Remove Redundant Config Service
1. Delete config package AIConfigurationService
2. Remove bean registration from AIInfrastructureAutoConfiguration
3. Update documentation
4. Verify no remaining dependencies

---

## Detailed Changes

### Change 1: Create AIProviderConfigValidator

**New File:** `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIProviderConfigValidator.java`

```java
package com.ai.infrastructure.validation;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates AI provider configuration at application startup.
 * Implements fail-fast validation per AI Fabric Framework philosophy.
 */
@Slf4j
@Component
public class AIProviderConfigValidator {

    private final AIProviderConfig providerConfig;
    private final AIServiceConfig serviceConfig;

    public AIProviderConfigValidator(AIProviderConfig providerConfig, AIServiceConfig serviceConfig) {
        this.providerConfig = providerConfig;
        this.serviceConfig = serviceConfig;
    }

    @PostConstruct
    public void validateOnStartup() {
        log.info("Validating AI provider configuration...");

        ValidationResult result = validate();

        if (!result.isValid()) {
            // Fail-fast: Stop application startup on invalid configuration
            String errorMessage = buildErrorMessage(result);
            log.error("AI Provider Configuration Validation Failed:\n{}", errorMessage);
            throw new IllegalStateException("Invalid AI provider configuration. See logs for details.");
        }

        if (!result.getWarnings().isEmpty()) {
            result.getWarnings().forEach(warning ->
                log.warn("Configuration Warning: {}", warning));
        }

        log.info("AI provider configuration validation successful");
    }

    public ValidationResult validate() {
        ValidationResult result = new ValidationResult();

        // Validate LLM provider
        validateLlmProvider(result);

        // Validate embedding provider
        validateEmbeddingProvider(result);

        // Validate service configuration
        validateServiceConfig(result);

        return result;
    }

    private void validateLlmProvider(ValidationResult result) {
        String provider = normalize(providerConfig.getLlmProvider());

        switch (provider) {
            case "openai" -> validateOpenAI(result, true);
            case "anthropic" -> validateAnthropic(result);
            case "cohere" -> validateCohere(result);
            case "azure" -> validateAzure(result);
            default -> result.addError("llmProvider",
                "Unknown LLM provider: " + provider + ". Must be one of: openai, anthropic, cohere, azure");
        }
    }

    private void validateEmbeddingProvider(ValidationResult result) {
        String provider = normalize(providerConfig.getEmbeddingProvider());

        switch (provider) {
            case "openai" -> validateOpenAI(result, false);
            case "azure" -> validateAzure(result);
            case "rest" -> validateRest(result);
            case "onnx" -> validateOnnx(result);
            default -> result.addError("embeddingProvider",
                "Unknown embedding provider: " + provider + ". Must be one of: openai, azure, rest, onnx");
        }
    }

    private void validateOpenAI(ValidationResult result, boolean isLlm) {
        AIProviderConfig.OpenAIConfig config = providerConfig.getOpenai();

        // FATAL: Missing required fields
        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.openai.api-key",
                "OpenAI API key is required. Set via environment variable OPENAI_API_KEY or application property.");
        }

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.openai.base-url",
                "OpenAI base URL is required. Must be explicitly configured.");
        }

        if (isLlm && isBlank(config.getModel())) {
            result.addError("ai.providers.openai.model",
                "OpenAI model is required when OpenAI is the LLM provider.");
        }

        if (!isLlm && isBlank(config.getEmbeddingModel())) {
            result.addError("ai.providers.openai.embedding-model",
                "OpenAI embedding model is required when OpenAI is the embedding provider.");
        }

        // WARNING: Missing optional fields
        if (config.getTimeout() == null || config.getTimeout() <= 0) {
            result.addWarning("ai.providers.openai.timeout is not configured. Requests may hang indefinitely.");
        }
    }

    private void validateAnthropic(ValidationResult result) {
        AIProviderConfig.AnthropicConfig config = providerConfig.getAnthropic();

        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.anthropic.api-key",
                "Anthropic API key is required. Set via environment variable ANTHROPIC_API_KEY or application property.");
        }

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.anthropic.base-url",
                "Anthropic base URL is required. Must be explicitly configured.");
        }

        if (isBlank(config.getModel())) {
            result.addError("ai.providers.anthropic.model",
                "Anthropic model is required. Must be explicitly configured (e.g., claude-3-5-sonnet-20241022).");
        }
    }

    private void validateCohere(ValidationResult result) {
        AIProviderConfig.CohereConfig config = providerConfig.getCohere();

        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.cohere.api-key",
                "Cohere API key is required. Set via environment variable COHERE_API_KEY or application property.");
        }

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.cohere.base-url",
                "Cohere base URL is required. Must be explicitly configured.");
        }

        if (isBlank(config.getModel())) {
            result.addError("ai.providers.cohere.model",
                "Cohere model is required. Must be explicitly configured.");
        }
    }

    private void validateAzure(ValidationResult result) {
        AIProviderConfig.AzureConfig config = providerConfig.getAzure();

        if (isBlank(config.getApiKey())) {
            result.addError("ai.providers.azure.api-key",
                "Azure API key is required. Set via environment variable AZURE_OPENAI_API_KEY or application property.");
        }

        if (isBlank(config.getEndpoint())) {
            result.addError("ai.providers.azure.endpoint",
                "Azure endpoint is required. Must be your Azure OpenAI resource endpoint.");
        }

        if (isBlank(config.getDeploymentName())) {
            result.addError("ai.providers.azure.deployment-name",
                "Azure deployment name is required. Must match your deployed model name.");
        }
    }

    private void validateRest(ValidationResult result) {
        AIProviderConfig.RestConfig config = providerConfig.getRest();

        if (isBlank(config.getBaseUrl())) {
            result.addError("ai.providers.embedding-provider",
                "Only supported embedding providers should be configurable.");
        }

        if (isBlank(config.getEndpoint())) {
            result.addError("ai.providers.embedding-provider",
                "Custom embedding endpoints are not supported in the greenfield provider matrix.");
        }
    }

    private void validateOnnx(ValidationResult result) {
        AIProviderConfig.ONNXConfig config = providerConfig.getOnnx();

        if (isBlank(config.getModelPath())) {
            result.addError("ai.providers.onnx.model-path",
                "ONNX model path is required. Must point to a valid .onnx model file.");
        }

        if (isBlank(config.getTokenizerPath())) {
            result.addWarning("ai.providers.onnx.tokenizer-path is not configured. Using default tokenizer.");
        }
    }

    private void validateServiceConfig(ValidationResult result) {
        if (serviceConfig.getDefaultTimeout() == null || serviceConfig.getDefaultTimeout() <= 0) {
            result.addWarning("ai.service.default-timeout is not configured or invalid. Using system defaults.");
        }

        if (serviceConfig.getMaxRetries() != null && serviceConfig.getMaxRetries() < 0) {
            result.addError("ai.service.max-retries",
                "Max retries cannot be negative.");
        }

        if (serviceConfig.getThreadPoolSize() != null && serviceConfig.getThreadPoolSize() <= 0) {
            result.addError("ai.service.thread-pool-size",
                "Thread pool size must be positive.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String buildErrorMessage(ValidationResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== AI Provider Configuration Errors ===\n");
        result.getErrors().forEach((key, message) ->
            sb.append(String.format("  ❌ %s: %s\n", key, message)));
        return sb.toString();
    }

    public static class ValidationResult {
        private final List<ValidationError> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String field, String message) {
            errors.add(new ValidationError(field, message));
        }

        public void addWarning(String message) {
            warnings.add(message);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public List<ValidationError> getErrors() {
            return errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    public record ValidationError(String field, String message) {}
}
```

### Change 2: Clean AIProviderConfig - Remove Hard-coded Defaults

**File:** `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

**Changes Required:**

```java
// Lines 126-151: OpenAIConfig - Remove all defaults except boolean flags
@Data
public static class OpenAIConfig {
    private boolean enabled = true;
    private String apiKey;        // Remove default - required
    private String baseUrl;       // Remove default - required
    private String model;         // Remove default - required
    private Integer maxTokens;    // Remove default - optional
    private Double temperature;   // Remove default - optional
    private Integer timeout;      // Remove default - optional
    private Integer priority;     // Remove default - optional
    private String embeddingModel; // Remove default - required when used

    GenerationDefaults toGenerationDefaults(String providerName) {
        return new GenerationDefaults(
            providerName,
            model,
            maxTokens,
            temperature,
            timeout,
            priority
        );
    }

    EmbeddingDefaults toEmbeddingDefaults(String providerName) {
        return new EmbeddingDefaults(providerName, embeddingModel);
    }
}

// Lines 154-178: AzureConfig - Remove all defaults
@Data
public static class AzureConfig {
    private boolean enabled = false; // Keep boolean default
    private String apiKey;           // Remove default
    private String endpoint;         // Remove default
    private String deploymentName;   // Remove default
    private String embeddingDeploymentName; // Remove default
    private String apiVersion;       // Remove default
    private Integer timeout;         // Remove default
    private Integer priority;        // Remove default

    GenerationDefaults toGenerationDefaults(String providerName) {
        return new GenerationDefaults(
            providerName,
            deploymentName,
            2000, // Can use reasonable default for maxTokens
            0.3,  // Can use reasonable default for temperature
            timeout,
            priority
        );
    }

    EmbeddingDefaults toEmbeddingDefaults(String providerName) {
        return new EmbeddingDefaults(providerName, embeddingDeploymentName);
    }
}

// Lines 180-201: AnthropicConfig - Remove all defaults
@Data
public static class AnthropicConfig {
    private boolean enabled = false;
    private String apiKey;     // Remove default
    private String baseUrl;    // Remove default
    private String model;      // Remove default
    private Integer maxTokens; // Remove default
    private Double temperature; // Remove default
    private Integer timeout;   // Remove default
    private Integer priority;  // Remove default

    GenerationDefaults toGenerationDefaults(String providerName) {
        return new GenerationDefaults(
            providerName,
            model,
            maxTokens,
            temperature,
            timeout,
            priority
        );
    }
}

// Lines 203-229: CohereConfig - Remove all defaults
@Data
public static class CohereConfig {
    private boolean enabled = false;
    private String apiKey;          // Remove default
    private String baseUrl;         // Remove default
    private String model;           // Remove default
    private Integer maxTokens;      // Remove default
    private Double temperature;     // Remove default
    private Integer timeout;        // Remove default
    private Integer priority;       // Remove default
    private String embeddingModel;  // Remove default

    GenerationDefaults toGenerationDefaults(String providerName) {
        return new GenerationDefaults(
            providerName,
            model,
            maxTokens,
            temperature,
            timeout,
            priority
        );
    }

    EmbeddingDefaults toEmbeddingDefaults(String providerName) {
        return new EmbeddingDefaults(providerName, embeddingModel);
    }
}

// Lines 231-243: ONNXConfig - Remove hard-coded paths
@Data
public static class ONNXConfig {
    private boolean enabled = true;
    private String modelPath;       // Remove default - required
    private String tokenizerPath;   // Remove default - optional
    private Integer maxSequenceLength = 512; // Keep - reasonable default
    private Boolean useGpu = false;  // Keep - reasonable default
    private String modelAlias;       // Remove default - optional

    EmbeddingDefaults toEmbeddingDefaults(String providerName) {
        return new EmbeddingDefaults(providerName, modelAlias);
    }
}

// Lines 245-254: WeaviateConfig - Keep reasonable defaults for ports/schemes
@Data
public static class WeaviateConfig {
    private boolean enabled = false;
    private String scheme = "https";         // Keep - reasonable default
    private String host;                     // Remove default - required
    private Integer port = 443;              // Keep - standard HTTPS port
    private String apiKey;                   // Remove default - required
    private Integer timeout = 30;            // Keep - reasonable default
    private Boolean consistencyLevelStrong = false; // Keep - reasonable default
}

// Lines 256-265: QdrantConfig - Remove localhost defaults
@Data
public static class QdrantConfig {
    private boolean enabled = false;
    private String host;            // Remove default - required
    private Integer port = 6333;    // Keep - standard Qdrant port
    private String apiKey;          // Remove default - optional
    private Integer timeout = 30;   // Keep - reasonable default
    private Integer grpcPort = 6334; // Keep - standard Qdrant gRPC port
    private Boolean preferGrpc = false; // Keep - reasonable default
}

// Lines 267-277: MilvusConfig - Remove localhost defaults
@Data
public static class MilvusConfig {
    private boolean enabled = false;
    private String host;            // Remove default - required
    private Integer port = 19530;   // Keep - standard Milvus port
    private String username;        // Remove default - optional
    private String password;        // Remove default - optional
    private String databaseName = "default"; // Keep - reasonable default
    private Integer timeout = 30;   // Keep - reasonable default
    private Boolean secure = false; // Keep - reasonable default
}

// Lines 279-291: RestConfig - Remove localhost defaults
@Data
public static class RestConfig {
    private boolean enabled = false;
    private String baseUrl;         // Remove default - required
    private String endpoint = "/embed"; // Keep - standard endpoint pattern
    private String batchEndpoint = "/embed/batch"; // Keep - standard endpoint pattern
    private Integer timeout = 30000; // Keep - reasonable default
    private String model;            // Remove default - optional

    EmbeddingDefaults toEmbeddingDefaults(String providerName) {
        return new EmbeddingDefaults(providerName, model);
    }
}

// Lines 293-300: PineconeConfig - Remove all defaults
@Data
public static class PineconeConfig {
    private boolean enabled = false;
    private String apiKey;          // Remove default - required
    private String environment;     // Remove default - required
    private String indexName;       // Remove default - required
    private Integer dimensions = 1536; // Keep - OpenAI embedding standard
    private String projectId;       // Remove default - optional
    // ... rest unchanged
}
```

### Change 3: Update AIHealthIndicator

**File:** `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/health/AIHealthIndicator.java`

**Changes:**

```java
package com.ai.infrastructure.health;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.service.AIConfigurationService;
import com.ai.infrastructure.dto.AIHealthDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI Health Indicator
 *
 * Spring Boot Actuator health indicator for AI infrastructure services.
 * Now uses configuration beans directly instead of config service.
 *
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class AIHealthIndicator {

    // Changed: Now inject service package AIConfigurationService (kept)
    private final AIConfigurationService aiServiceConfigurationService;
    private final AIServiceConfig aiServiceConfig;
    private final AIProviderConfig aiProviderConfig;

    /**
     * Get health status as DTO
     */
    public AIHealthDto getHealthStatus() {
        AIHealthDto.AIHealthDtoBuilder builder = AIHealthDto.builder()
                .enabled(aiServiceConfig.getEnabled())
                .lastChecked(LocalDateTime.now().toString());

        // Get configuration summary from service
        Map<String, Object> summary = aiServiceConfigurationService.getConfigurationSummary();
        builder.configurationValid(true); // Validated at startup

        // Add feature and service counts from summary
        if (summary.containsKey("featuresEnabled")) {
            builder.featuresEnabled((Integer) summary.get("featuresEnabled"));
        }
        if (summary.containsKey("totalFeatures")) {
            builder.totalFeatures((Integer) summary.get("totalFeatures"));
        }
        if (summary.containsKey("servicesEnabled")) {
            builder.servicesEnabled((Integer) summary.get("servicesEnabled"));
        }
        if (summary.containsKey("totalServices")) {
            builder.totalServices((Integer) summary.get("totalServices"));
        }

        return builder.build();
    }

    /**
     * Get health status
     */
    public Map<String, Object> health() {
        try {
            log.debug("Checking AI infrastructure health...");

            // Check if AI services are enabled
            if (!aiServiceConfig.getEnabled()) {
                return Map.of(
                    "status", "DOWN",
                    "reason", "AI services are disabled",
                    "timestamp", LocalDateTime.now()
                );
            }

            // Get comprehensive health information
            AIHealthDto healthInfo = getHealthStatus();

            // Determine overall health status
            String status = determineHealthStatus(healthInfo);

            // Build health response
            return Map.of(
                "status", status,
                "enabled", healthInfo.isEnabled(),
                "configurationValid", healthInfo.isConfigurationValid(),
                "featuresEnabled", healthInfo.getFeaturesEnabled(),
                "totalFeatures", healthInfo.getTotalFeatures(),
                "servicesEnabled", healthInfo.getServicesEnabled(),
                "totalServices", healthInfo.getTotalServices(),
                "llmProvider", aiProviderConfig.getLlmProvider(),
                "embeddingProvider", aiProviderConfig.getEmbeddingProvider(),
                "timestamp", LocalDateTime.now()
            );

        } catch (Exception e) {
            log.error("Error checking AI infrastructure health", e);
            return Map.of(
                "status", "DOWN",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now()
            );
        }
    }

    // ... rest of methods unchanged
}
```

### Change 4: Simplify Service Package AIConfigurationService

**File:** `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/service/AIConfigurationService.java`

**Changes:**

```java
// Remove validation logic (lines 187-314) - now handled by AIProviderConfigValidator
// Keep:
// - getConfiguration() - DTO generation
// - getProviderConfiguration() - provider info
// - getServiceConfiguration() - service info
// - isFeatureEnabled() - feature flag checking
// - isServiceEnabled() - service status
// - getConfigurationSummary() - summary generation

// The validation logic is now in AIProviderConfigValidator
// This service becomes purely a configuration accessor/DTO generator
```

### Change 5: Delete Config Package AIConfigurationService

**File to Delete:** `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIConfigurationService.java`

**Reason:**
- Duplicates Spring Boot functionality
- Hot-reload not used
- Dynamic config map not used
- Environment loading redundant

### Change 6: Update AIInfrastructureAutoConfiguration

**File:** `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`

**Changes:**

```java
// Remove bean registration for config package AIConfigurationService (around line 365-367)
// Add bean registration for AIProviderConfigValidator

@Bean
public AIProviderConfigValidator aiProviderConfigValidator(
        AIProviderConfig providerConfig,
        AIServiceConfig serviceConfig) {
    return new AIProviderConfigValidator(providerConfig, serviceConfig);
}

// Keep bean registration for service package AIConfigurationService
// (Already exists as @Service, but verify it's registered)
```

---

## Configuration Examples

### Example 1: OpenAI as LLM Provider (application.yml)

```yaml
ai:
  providers:
    enabled: true
    llm-provider: openai
    embedding-provider: onnx
    enable-fallback: true

    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}  # Required from environment
      base-url: https://api.openai.com/v1
      model: gpt-4o-mini
      max-tokens: 2000
      temperature: 0.3
      timeout: 60
      priority: 100
      embedding-model: text-embedding-3-small

    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
      tokenizer-path: classpath:/models/embeddings/tokenizer.json
      max-sequence-length: 512
      use-gpu: false
      model-alias: all-MiniLM-L6-v2

  service:
    enabled: true
    default-timeout: 30000
    max-retries: 3
    thread-pool-size: 10
```

### Example 2: Azure OpenAI with Pinecone (application.yml)

```yaml
ai:
  providers:
    enabled: true
    llm-provider: azure
    embedding-provider: openai
    enable-fallback: false

    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: https://your-resource.openai.azure.com
      deployment-name: gpt-4o-deployment
      embedding-deployment-name: embedding-deployment
      api-version: 2024-02-15-preview
      timeout: 60
      priority: 100

    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1
      embedding-model: text-embedding-3-small

    pinecone:
      enabled: true
      api-key: ${PINECONE_API_KEY}
      environment: us-east-1-aws
      index-name: my-ai-index
      dimensions: 1536
      project-id: ${PINECONE_PROJECT_ID}
```

### Example 3: Anthropic with REST Embeddings (application.yml)

```yaml
ai:
  providers:
    enabled: true
    llm-provider: anthropic
    embedding-provider: rest

    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
      base-url: https://api.anthropic.com/v1
      model: claude-3-5-sonnet-20241022
      max-tokens: 4096
      temperature: 0.3
      timeout: 120
      priority: 100

    rest:
      enabled: true
      base-url: http://embedding-service:8000
      endpoint: /embed
      batch-endpoint: /embed/batch
      timeout: 30000
      model: all-MiniLM-L6-v2
```

### Example 4: Environment Variables (Production)

```bash
# LLM Provider
export AI_PROVIDERS_LLM_PROVIDER=openai
export OPENAI_API_KEY=sk-...
export AI_PROVIDERS_OPENAI_BASE_URL=https://api.openai.com/v1
export AI_PROVIDERS_OPENAI_MODEL=gpt-4o-mini
export AI_PROVIDERS_OPENAI_TIMEOUT=60

# Embedding Provider
export AI_PROVIDERS_EMBEDDING_PROVIDER=onnx
export AI_PROVIDERS_ONNX_MODEL_PATH=/app/models/embeddings/all-MiniLM-L6-v2.onnx
export AI_PROVIDERS_ONNX_TOKENIZER_PATH=/app/models/embeddings/tokenizer.json

# Service Configuration
export AI_SERVICE_ENABLED=true
export AI_SERVICE_DEFAULT_TIMEOUT=30000
export AI_SERVICE_MAX_RETRIES=3
```

---

## Validation Requirements

### Startup Validation (Fail-Fast)

**When Application Starts:**

1. **AIProviderConfigValidator.validateOnStartup()** runs via @PostConstruct
2. Validates all required configuration based on selected providers
3. **If validation fails:** Application startup fails with clear error message
4. **If warnings exist:** Logged but application continues

### Validation Levels

**FATAL (Fail Startup):**
- Missing API keys for selected provider
- Missing required endpoints/URLs
- Missing required model names
- Invalid provider selection
- Negative/zero values for critical timeouts

**WARNING (Log Only):**
- Missing optional configuration
- Suboptimal settings
- Missing optional timeouts
- Unset priorities

### Error Message Format

```
=== AI Provider Configuration Errors ===
  ❌ ai.providers.openai.api-key: OpenAI API key is required. Set via environment variable OPENAI_API_KEY or application property.
  ❌ ai.providers.openai.model: OpenAI model is required when OpenAI is the LLM provider.
  ⚠️  ai.providers.openai.timeout is not configured. Requests may hang indefinitely.
```

---

## Migration Path

### For Framework Users

#### Step 1: Update Configuration Files

**Before (Old):**
```yaml
# Relied on hard-coded defaults
ai:
  providers:
    llm-provider: openai
    openai:
      api-key: ${OPENAI_API_KEY}
      # Everything else used framework defaults
```

**After (New - Required):**
```yaml
ai:
  providers:
    llm-provider: openai
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1  # Now required
      model: gpt-4o-mini                   # Now required
      timeout: 60                          # Recommended
      embedding-model: text-embedding-3-small  # If using for embeddings
```

#### Step 2: Remove Deprecated Service Usage

**Before (If Used):**
```java
@Autowired
private AIConfigurationService configService; // config package version

// Hot-reload functionality
configService.reloadConfiguration();
configService.getConfig("key", String.class);
```

**After:**
```java
@Autowired
private AIProviderConfig providerConfig;  // Direct injection

@Autowired
private AIServiceConfig serviceConfig;    // Direct injection

// Use Spring Boot @ConfigurationProperties directly
String model = providerConfig.getOpenai().getModel();
Integer timeout = serviceConfig.getDefaultTimeout();
```

#### Step 3: Update Health Check Integration

**Before:**
```java
@Autowired
private AIConfigurationService configService;  // config package

Map<String, Object> summary = configService.getConfigurationSummary();
```

**After:**
```java
@Autowired
private com.ai.infrastructure.service.AIConfigurationService configService;  // service package

Map<String, Object> summary = configService.getConfigurationSummary();
```

#### Step 4: Test Startup Validation

```bash
# Start application - will fail if configuration invalid
./mvnw spring-boot:run

# Check logs for validation errors
# Fix configuration based on error messages
# Restart until validation passes
```

---

## Testing Strategy

### Unit Tests

#### Test 1: AIProviderConfigValidator - Valid Configuration

```java
@Test
void shouldPassValidation_whenAllRequiredFieldsPresent() {
    // Given
    AIProviderConfig config = new AIProviderConfig();
    config.setLlmProvider("openai");
    config.getOpenai().setApiKey("sk-test");
    config.getOpenai().setBaseUrl("https://api.openai.com/v1");
    config.getOpenai().setModel("gpt-4o-mini");

    AIServiceConfig serviceConfig = new AIServiceConfig();
    serviceConfig.setDefaultTimeout(30000);

    AIProviderConfigValidator validator = new AIProviderConfigValidator(config, serviceConfig);

    // When
    ValidationResult result = validator.validate();

    // Then
    assertThat(result.isValid()).isTrue();
    assertThat(result.getErrors()).isEmpty();
}
```

#### Test 2: AIProviderConfigValidator - Missing Required Fields

```java
@Test
void shouldFailValidation_whenRequiredFieldsMissing() {
    // Given
    AIProviderConfig config = new AIProviderConfig();
    config.setLlmProvider("openai");
    // Missing: apiKey, baseUrl, model

    AIServiceConfig serviceConfig = new AIServiceConfig();
    AIProviderConfigValidator validator = new AIProviderConfigValidator(config, serviceConfig);

    // When
    ValidationResult result = validator.validate();

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors()).hasSize(3);
    assertThat(result.getErrors())
        .extracting(ValidationError::field)
        .contains(
            "ai.providers.openai.api-key",
            "ai.providers.openai.base-url",
            "ai.providers.openai.model"
        );
}
```

#### Test 3: AIProviderConfigValidator - Provider-Specific Validation

```java
@Test
void shouldValidateAzure_whenAzureIsLlmProvider() {
    // Given
    AIProviderConfig config = new AIProviderConfig();
    config.setLlmProvider("azure");
    config.getAzure().setApiKey("test-key");
    config.getAzure().setEndpoint("https://test.openai.azure.com");
    // Missing: deploymentName

    AIServiceConfig serviceConfig = new AIServiceConfig();
    AIProviderConfigValidator validator = new AIProviderConfigValidator(config, serviceConfig);

    // When
    ValidationResult result = validator.validate();

    // Then
    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrors())
        .extracting(ValidationError::field)
        .contains("ai.providers.azure.deployment-name");
}
```

### Integration Tests

#### Test 4: Application Startup with Valid Config

```java
@SpringBootTest
@TestPropertySource(properties = {
    "ai.providers.llm-provider=openai",
    "ai.providers.openai.api-key=sk-test",
    "ai.providers.openai.base-url=https://api.openai.com/v1",
    "ai.providers.openai.model=gpt-4o-mini"
})
class ApplicationStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldStartSuccessfully_withValidConfiguration() {
        assertThat(context).isNotNull();
        assertThat(context.getBean(AIProviderConfigValidator.class)).isNotNull();
    }
}
```

#### Test 5: Application Startup Failure with Invalid Config

```java
@SpringBootTest
@TestPropertySource(properties = {
    "ai.providers.llm-provider=openai"
    // Missing required: api-key, base-url, model
})
class ApplicationStartupFailureTest {

    @Test
    void shouldFailToStart_withInvalidConfiguration() {
        assertThatThrownBy(() -> {
            new SpringApplicationBuilder(Application.class)
                .properties("ai.providers.llm-provider=openai")
                .run();
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid AI provider configuration");
    }
}
```

### Manual Testing Checklist

- [ ] Start application with minimal OpenAI config - should pass
- [ ] Start application without API key - should fail with clear error
- [ ] Start application without model - should fail with clear error
- [ ] Start application with Azure provider - validate Azure-specific fields
- [ ] Start application with Anthropic provider - validate Anthropic-specific fields
- [ ] Check health endpoint returns correct provider info
- [ ] Verify configuration summary includes all required info
- [ ] Test with environment variables only (no YAML)
- [ ] Test with YAML only (no environment variables)
- [ ] Test with mixed YAML + environment variables (env wins)

---

## Implementation Checklist

### Phase 1: Provider Configuration Cleanup

- [ ] Create AIProviderConfigValidator class
- [ ] Implement validateOnStartup() with @PostConstruct
- [ ] Implement provider-specific validation methods
- [ ] Add fail-fast behavior on validation failure
- [ ] Update AIProviderConfig to remove hard-coded defaults
- [ ] Update application-dev.yml with explicit examples
- [ ] Update application-prod.yml with explicit examples
- [ ] Add validation unit tests

### Phase 2: Service Consolidation

- [ ] Update AIHealthIndicator to use service package AIConfigurationService
- [ ] Remove validation logic from service package AIConfigurationService
- [ ] Simplify service to pure accessor/DTO generator
- [ ] Update AIInfrastructureAutoConfiguration bean registrations
- [ ] Add integration tests for health indicator
- [ ] Update service unit tests

### Phase 3: Remove Redundant Config Service

- [ ] Delete config package AIConfigurationService.java
- [ ] Remove bean registration from AIInfrastructureAutoConfiguration
- [ ] Verify no other dependencies exist
- [ ] Update documentation to remove references
- [ ] Run full test suite
- [ ] Manual smoke tests

### Documentation Updates

- [ ] Update README with new configuration requirements
- [ ] Create CONFIGURATION.md with all provider examples
- [ ] Update MIGRATION.md with upgrade path
- [ ] Add validation error reference guide
- [ ] Update Development_Guides with new patterns
- [ ] Update Orchestrator_User_Guide.md if needed

---

## Success Criteria

### Technical

✅ Application fails to start with invalid configuration (fail-fast)
✅ Clear error messages identify missing/invalid configuration
✅ No hard-coded API endpoints in production code
✅ No hard-coded model names in production code
✅ Validation runs at startup before any services initialize
✅ Health checks work without config package AIConfigurationService
✅ All tests pass with new validation
✅ Configuration via environment variables works
✅ Configuration via YAML works
✅ Mixed configuration (YAML + env vars) works correctly

### Philosophy Alignment

✅ Follows greenfield approach (no backward compatibility)
✅ Fail-fast on errors (security and correctness first)
✅ Clear separation of concerns (validation extracted)
✅ No redundant code (removed duplicate config service)
✅ Trust users (removed hard-coded defaults)
✅ Spring Boot best practices (use @ConfigurationProperties)
✅ Clean code (no magic strings, clear error messages)

---

## Risk Assessment

### Low Risk
- Adding AIProviderConfigValidator (new code, no changes to existing)
- Removing hard-coded defaults (users must provide values anyway)
- Updating health indicator (internal change, same API)

### Medium Risk
- Deleting config package AIConfigurationService (breaking change)
  - **Mitigation:** Search codebase for all usages first
  - **Mitigation:** Fail-fast validation catches configuration errors early

### High Risk
- Startup failures due to missing configuration
  - **Mitigation:** Clear error messages guide users to fixes
  - **Mitigation:** Comprehensive documentation with examples
  - **Mitigation:** Validation at startup (not runtime surprises)

---

## Timeline Estimate

**Phase 1:** 3-4 days
- Create validator: 1 day
- Clean provider config: 1 day
- Update application.yml examples: 0.5 day
- Unit tests: 1 day

**Phase 2:** 2-3 days
- Update health indicator: 0.5 day
- Simplify service: 1 day
- Integration tests: 1 day

**Phase 3:** 1-2 days
- Delete config service: 0.5 day
- Verify dependencies: 0.5 day
- Full testing: 1 day

**Documentation:** 1-2 days
- Update all docs: 1 day
- Review and polish: 1 day

**Total:** 7-11 days (1.5-2 weeks)

---

## Appendix A: Files Changed Summary

### New Files
- `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/validation/AIProviderConfigValidator.java`

### Modified Files
- `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`
- `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/service/AIConfigurationService.java`
- `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/health/AIHealthIndicator.java`
- `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`
- `/ai-infrastructure-core/src/main/resources/application-dev.yml`
- `/ai-infrastructure-core/src/main/resources/application-prod.yml`

### Deleted Files
- `/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIConfigurationService.java`

---

## Appendix B: Validation Error Reference

### OpenAI Errors

| Error | Message | Solution |
|-------|---------|----------|
| openai.api-key | OpenAI API key is required | Set `OPENAI_API_KEY` environment variable |
| openai.base-url | OpenAI base URL is required | Set `ai.providers.openai.base-url=https://api.openai.com/v1` |
| openai.model | OpenAI model is required | Set `ai.providers.openai.model=gpt-4o-mini` |
| openai.embedding-model | OpenAI embedding model is required | Set `ai.providers.openai.embedding-model=text-embedding-3-small` |

### Azure Errors

| Error | Message | Solution |
|-------|---------|----------|
| azure.api-key | Azure API key is required | Set `AZURE_OPENAI_API_KEY` environment variable |
| azure.endpoint | Azure endpoint is required | Set `ai.providers.azure.endpoint=https://your-resource.openai.azure.com` |
| azure.deployment-name | Azure deployment name is required | Set `ai.providers.azure.deployment-name=your-deployment` |

### Anthropic Errors

| Error | Message | Solution |
|-------|---------|----------|
| anthropic.api-key | Anthropic API key is required | Set `ANTHROPIC_API_KEY` environment variable |
| anthropic.base-url | Anthropic base URL is required | Set `ai.providers.anthropic.base-url=https://api.anthropic.com/v1` |
| anthropic.model | Anthropic model is required | Set `ai.providers.anthropic.model=claude-3-5-sonnet-20241022` |

### ONNX Errors

| Error | Message | Solution |
|-------|---------|----------|
| onnx.model-path | ONNX model path is required | Set `ai.providers.onnx.model-path=/path/to/model.onnx` |

---

**Document Status:** Ready for Implementation
**Review Required:** Architecture Team
**Approval Required:** Technical Lead

---

*This document follows the AI Fabric Framework Philosophy: Clean code, fail-fast, no backward compatibility, trust users to configure correctly.*
