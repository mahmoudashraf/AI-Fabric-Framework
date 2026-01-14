# Unified Intent Extraction and Vectorization Solution

## Document Purpose
This document unifies the solutions for progressive intent extraction, vectorSpace routing, result normalization, and provider-specific orchestration configuration into a single, cohesive implementation plan.

**Status:** Ready for Implementation
**Target Version:** Next Release
**Tracking Issue:** TBD

---

## Executive Summary

### Problems Addressed
1. **Intent Extraction Reliability** - LLM providers produce unreliable/incomplete structured outputs
2. **vectorSpace Routing** - Missing or incorrect vectorSpace values cause retrieval failures
3. **Result Normalization** - Provider-dependent outcome shapes create flaky tests
4. **Provider Configuration** - Single LLM provider for both orchestration and generation is suboptimal

### Solution Architecture
Three-layer progressive pipeline:
```
┌────────────────────────────────────────────────────────────┐
│  Layer 1: Progressive Intent Extraction                    │
│  • Compound (fast path)                                     │
│  • Repair (structural only, max 1 attempt)                  │
│  • Multi-Step (decomposed: classify → action → retrieval)   │
└────────────────────────────────────────────────────────────┘
                          ↓
┌────────────────────────────────────────────────────────────┐
│  Layer 2: Intent Validation & Enrichment                   │
│  • Deterministic validation (schema + invariants)           │
│  • Action grounding (against registry)                      │
│  • vectorSpace routing (when missing):                      │
│    - If 1 space → auto-assign                               │
│    - Else → bounded fan-out (top N spaces, small topK)      │
│    - Else → clarification (if fan-out weak)                 │
└────────────────────────────────────────────────────────────┘
                          ↓
┌────────────────────────────────────────────────────────────┐
│  Layer 3: Result Normalization (✅ ALREADY EXISTS)          │
│  • Provider-agnostic contract enforcement                   │
│  • Error code standardization                               │
│  • Child error bubbling                                     │
└────────────────────────────────────────────────────────────┘
```

---

## Current State Assessment

### ✅ Already Implemented
1. **OrchestrationResultNormalizer** - Result normalization is DONE
   - Location: `com.ai.infrastructure.intent.orchestration.OrchestrationResultNormalizer`
   - Status: Feature-complete, enabled by default
   - Canonical error codes: `ACTION_NOT_FOUND`, `CHILD_ERROR`, `GENERATION_FAILED`

2. **Repair Logic** - Single repair attempt EXISTS
   - Location: `IntentQueryExtractor.attemptRepair()` (line 176-228)
   - Status: Working, but not structured as progressive fallback

3. **vectorSpace Inference** - Heuristic-based inference EXISTS
   - Location: `IntentQueryExtractor.inferVectorSpace()` (line 280-312)
   - Current strategy: single space → query mention → largest count → first type
   - Missing: Bounded fan-out, clarification fallback

4. **KnowledgeBaseOverviewService** - KB statistics EXISTS
   - Location: `com.ai.infrastructure.intent.KnowledgeBaseOverviewService`
   - Provides: entity types, document counts, last update time

### ❌ Missing Components
1. **Provider-Specific Configuration** - No orchestration vs generation separation
   - Current: Single `ai.providers.llm-provider` for everything
   - Needed: Separate orchestration and generation LLM configs

2. **Progressive Extraction Engine** - Not structured as progressive fallback
   - Current: Direct call with single repair attempt
   - Needed: Compound → Repair → Multi-Step with proper abstraction

3. **Intent Extraction Validator** - No deterministic validation layer
   - Current: Validation mixed with extraction logic
   - Needed: Separate validator component

4. **Bounded Fan-Out Router** - vectorSpace inference is heuristic-only
   - Current: Pick one space based on heuristics
   - Needed: Query multiple spaces, merge + rerank

5. **LlmPurpose Enum** - No way to specify purpose when calling LLM
   - Current: `AICoreService.generateContent()` has no purpose parameter
   - Needed: Purpose-aware LLM selection (ORCHESTRATION vs GENERATION)

---

## Implementation Plan

### Phase 1: AI Provider-Specific Configuration (Week 1-2)

#### 1.1 Configuration Model Extension

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

**Add nested configuration classes:**

```java
@Data
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {

    // ... existing fields ...

    /**
     * Orchestration-specific LLM configuration (intent extraction, classification, planning).
     * When not specified, falls back to global llmProvider.
     */
    private OrchestrationLlmConfig orchestration;

    /**
     * Generation-specific LLM configuration (RAG answers, summaries, narrative).
     * When not specified, falls back to global llmProvider.
     */
    private GenerationLlmConfig generation;

    @Data
    public static class OrchestrationLlmConfig {
        /**
         * LLM provider for orchestration tasks.
         * Leave null to use global llmProvider.
         */
        private String llmProvider;

        /**
         * Model for orchestration tasks.
         * Leave null to use provider's default.
         */
        private String model;

        /**
         * Temperature for orchestration (default: 0.1 for consistency).
         */
        private Double temperature = 0.1;

        /**
         * Max tokens for orchestration requests.
         */
        private Integer maxTokens;

        /**
         * Timeout for orchestration requests.
         */
        private Integer timeout;
    }

    @Data
    public static class GenerationLlmConfig {
        private String llmProvider;
        private String model;
        private Double temperature = 0.3;
        private Integer maxTokens;
        private Integer timeout;
    }

    /**
     * Resolve orchestration-specific configuration with fallback.
     */
    public GenerationDefaults resolveOrchestrationLlmDefaults() {
        if (orchestration != null && orchestration.getLlmProvider() != null) {
            return buildOrchestrationDefaults();
        }
        // Fallback to global
        return resolveLlmDefaults();
    }

    /**
     * Resolve generation-specific configuration with fallback.
     */
    public GenerationDefaults resolveGenerationLlmDefaults() {
        if (generation != null && generation.getLlmProvider() != null) {
            return buildGenerationDefaults();
        }
        // Fallback to global
        return resolveLlmDefaults();
    }

    private GenerationDefaults buildOrchestrationDefaults() {
        String provider = normalize(orchestration.getLlmProvider());
        String model = orchestration.getModel();

        if (model == null) {
            // Use provider's default model
            GenerationDefaults providerDefaults = resolveDefaultsForProvider(provider);
            model = providerDefaults.model();
        }

        return new GenerationDefaults(
            provider,
            model,
            orchestration.getMaxTokens(),
            orchestration.getTemperature(),
            orchestration.getTimeout(),
            null // priority not used for purpose-specific
        );
    }

    private GenerationDefaults buildGenerationDefaults() {
        String provider = normalize(generation.getLlmProvider());
        String model = generation.getModel();

        if (model == null) {
            GenerationDefaults providerDefaults = resolveDefaultsForProvider(provider);
            model = providerDefaults.model();
        }

        return new GenerationDefaults(
            provider,
            model,
            generation.getMaxTokens(),
            generation.getTemperature(),
            generation.getTimeout(),
            null
        );
    }

    private GenerationDefaults resolveDefaultsForProvider(String provider) {
        return switch (provider) {
            case "anthropic" -> anthropic.toGenerationDefaults("anthropic");
            case "cohere" -> cohere.toGenerationDefaults("cohere");
            case "gemini" -> gemini.toGenerationDefaults("gemini");
            case "azure" -> azure.toGenerationDefaults("azure");
            default -> openai.toGenerationDefaults("openai");
        };
    }
}
```

**Example Configuration:**

```yaml
# application.yml
ai:
  providers:
    # Global fallback (backward compatible)
    llm-provider: openai

    # Orchestration-specific (intent extraction, planning)
    orchestration:
      llm-provider: cohere
      model: command-r-plus
      temperature: 0.1
      maxTokens: 1200
      timeout: 10

    # Generation-specific (RAG answers)
    generation:
      llm-provider: openai
      model: gpt-4o
      temperature: 0.3
      maxTokens: 2000
      timeout: 30
```

**Environment Variables:**

```bash
# Orchestration-specific
ORCHESTRATION_LLM_PROVIDER=cohere
ORCHESTRATION_LLM_MODEL=command-r-plus
ORCHESTRATION_LLM_TEMPERATURE=0.1
ORCHESTRATION_LLM_MAX_TOKENS=1200

# Generation-specific
GENERATION_LLM_PROVIDER=openai
GENERATION_LLM_MODEL=gpt-4o
GENERATION_LLM_TEMPERATURE=0.3
GENERATION_LLM_MAX_TOKENS=2000
```

#### 1.2 LLM Purpose Enum

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/core/LlmPurpose.java`

```java
package com.ai.infrastructure.core;

/**
 * Specifies the purpose of an LLM request to enable provider-specific configuration.
 *
 * <p>Different purposes may use different LLM providers optimized for that task:</p>
 * <ul>
 *   <li><strong>ORCHESTRATION</strong>: Structured outputs, low temperature, predictable (intent extraction, classification)</li>
 *   <li><strong>GENERATION</strong>: Natural language, higher quality, narrative (RAG answers, summaries)</li>
 *   <li><strong>EMBEDDINGS</strong>: Vector generation for semantic search</li>
 *   <li><strong>DEFAULT</strong>: Uses global provider configuration</li>
 * </ul>
 */
public enum LlmPurpose {
    /**
     * Intent extraction, classification, action selection, relationship query planning.
     * Requires: structured outputs, consistency, low cost.
     * Optimized for: Cohere Command, OpenAI GPT-4o-mini, Anthropic Haiku.
     */
    ORCHESTRATION,

    /**
     * RAG answer generation, summaries, narrative responses.
     * Requires: quality, fluency, context handling.
     * Optimized for: OpenAI GPT-4o, Anthropic Sonnet/Opus, Gemini Pro.
     */
    GENERATION,

    /**
     * Embedding generation for vector search.
     * Optimized for: OpenAI text-embedding-3, Cohere embed-v3, ONNX models.
     */
    EMBEDDINGS,

    /**
     * Default purpose, uses global provider configuration.
     */
    DEFAULT
}
```

#### 1.3 AICoreService Extension

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/core/AICoreService.java`

**Add purpose-aware method:**

```java
/**
 * Generate AI content with specific purpose (enables provider-specific configuration).
 *
 * @param request the generation request
 * @param purpose the purpose (ORCHESTRATION, GENERATION, DEFAULT)
 * @return generated content response
 */
public AIGenerationResponse generateContent(AIGenerationRequest request, LlmPurpose purpose) {
    try {
        AIGenerationRequest generationRequest = applyGenerationDefaults(request, purpose);

        log.debug("Generating AI content via provider manager for purpose={} prompt={}",
            purpose, generationRequest.getPrompt());

        AIGenerationResponse response = providerManager.generateContent(generationRequest);

        log.debug("Successfully generated AI content using provider={} model={} purpose={}",
            response.getModel(), response.getModel(), purpose);

        return response;

    } catch (Exception e) {
        log.error("Error generating AI content for purpose={}", purpose, e);
        throw new AIServiceException("Failed to generate AI content: " + e.getMessage(), e);
    }
}

/**
 * Backward-compatible method (uses DEFAULT purpose).
 */
public AIGenerationResponse generateContent(AIGenerationRequest request) {
    return generateContent(request, LlmPurpose.DEFAULT);
}

private AIGenerationRequest applyGenerationDefaults(AIGenerationRequest request, LlmPurpose purpose) {
    if (request == null) {
        throw new AIServiceException("Generation request cannot be null");
    }

    // Resolve defaults based on purpose
    AIProviderConfig.GenerationDefaults defaults = resolveDefaultsForPurpose(purpose);

    boolean requiresDefaults = request.getModel() == null
        || request.getMaxTokens() == null
        || request.getTemperature() == null;

    if (!requiresDefaults) {
        return request;
    }

    return AIGenerationRequest.builder()
        .entityId(request.getEntityId())
        .entityType(request.getEntityType())
        .generationType(request.getGenerationType())
        .prompt(request.getPrompt())
        .context(request.getContext())
        .systemPrompt(request.getSystemPrompt())
        .purpose(request.getPurpose())
        .parameters(request.getParameters())
        .userId(request.getUserId())
        .model(request.getModel() != null ? request.getModel() : defaults.model())
        .maxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : defaults.maxTokens())
        .temperature(request.getTemperature() != null ? request.getTemperature() : defaults.temperature())
        .build();
}

private AIProviderConfig.GenerationDefaults resolveDefaultsForPurpose(LlmPurpose purpose) {
    return switch (purpose) {
        case ORCHESTRATION -> aiProviderConfig.resolveOrchestrationLlmDefaults();
        case GENERATION -> aiProviderConfig.resolveGenerationLlmDefaults();
        case EMBEDDINGS, DEFAULT -> aiProviderConfig.resolveLlmDefaults();
    };
}
```

#### 1.4 Update IntentQueryExtractor

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/IntentQueryExtractor.java`

**Change line 81:**

```java
// OLD:
AIGenerationResponse generationResponse = aiCoreService.generateContent(generationRequest);

// NEW:
AIGenerationResponse generationResponse = aiCoreService.generateContent(
    generationRequest,
    LlmPurpose.ORCHESTRATION  // Use orchestration-specific LLM
);
```

**Change line 216 (repair):**

```java
// OLD:
AIGenerationResponse repairResponse = aiCoreService.generateContent(repairRequest);

// NEW:
AIGenerationResponse repairResponse = aiCoreService.generateContent(
    repairRequest,
    LlmPurpose.ORCHESTRATION  // Repair also uses orchestration LLM
);
```

#### 1.5 Update RAG Generation (if applicable)

**File:** Search for RAG answer generation and add `LlmPurpose.GENERATION`:

```java
// Example (location depends on your RAG implementation):
AIGenerationResponse response = aiCoreService.generateContent(
    answerGenerationRequest,
    LlmPurpose.GENERATION  // Use generation-specific LLM
);
```

---

### Phase 2: Progressive Intent Extraction Engine (Week 3-4)

#### 2.1 Intent Extraction Validator

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/IntentExtractionValidator.java`

```java
package com.ai.infrastructure.intent;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates intent extraction responses against schema and invariants.
 *
 * <p>Validation is deterministic and does not involve LLM calls.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentExtractionValidator {

    private final ActionHandlerRegistry actionHandlerRegistry;

    /**
     * Validate intent extraction response.
     *
     * @param response the response to validate
     * @return validation result
     */
    public ValidationResult validate(MultiIntentResponse response) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (response == null) {
            errors.add("Response is null");
            return new ValidationResult(false, ErrorCategory.STRUCTURAL, errors, warnings);
        }

        if (response.getIntents() == null || response.getIntents().isEmpty()) {
            warnings.add("Response contains no intents");
            return new ValidationResult(true, ErrorCategory.NONE, errors, warnings);
        }

        for (int i = 0; i < response.getIntents().size(); i++) {
            Intent intent = response.getIntents().get(i);
            validateIntent(intent, i, errors, warnings);
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(false, categorizeErrors(errors), errors, warnings);
        }

        return new ValidationResult(true, ErrorCategory.NONE, errors, warnings);
    }

    private void validateIntent(Intent intent, int index, List<String> errors, List<String> warnings) {
        String prefix = "Intent[" + index + "]: ";

        // Structural validation
        if (intent == null) {
            errors.add(prefix + "Intent is null");
            return;
        }

        if (!intent.hasValidType()) {
            errors.add(prefix + "Missing or invalid type");
        }

        if (!intent.hasMeaningfulName()) {
            errors.add(prefix + "Missing intent name (intent or action field)");
        }

        // Type-specific validation
        if (intent.getType() == IntentType.ACTION) {
            validateActionIntent(intent, prefix, errors, warnings);
        } else if (intent.getType() == IntentType.INFORMATION) {
            validateInformationIntent(intent, prefix, errors, warnings);
        }
    }

    private void validateActionIntent(Intent intent, String prefix, List<String> errors, List<String> warnings) {
        String actionName = StringUtils.hasText(intent.getAction())
            ? intent.getAction()
            : intent.getIntent();

        if (!StringUtils.hasText(actionName)) {
            errors.add(prefix + "ACTION intent missing action name");
            return;
        }

        // Check if action handler exists
        if (actionHandlerRegistry != null && actionHandlerRegistry.findHandler(actionName).isEmpty()) {
            warnings.add(prefix + "No handler registered for action '" + actionName + "'");
        }
    }

    private void validateInformationIntent(Intent intent, String prefix, List<String> errors, List<String> warnings) {
        if (Boolean.TRUE.equals(intent.getRequiresRetrieval()) && !StringUtils.hasText(intent.getVectorSpace())) {
            warnings.add(prefix + "INFORMATION intent requires retrieval but vectorSpace is missing");
        }
    }

    private ErrorCategory categorizeErrors(List<String> errors) {
        if (errors.stream().anyMatch(e -> e.contains("null") || e.contains("Missing"))) {
            return ErrorCategory.STRUCTURAL;
        }
        if (errors.stream().anyMatch(e -> e.contains("handler") || e.contains("action"))) {
            return ErrorCategory.UNSAFE;
        }
        return ErrorCategory.OTHER;
    }

    /**
     * Validation result.
     */
    public record ValidationResult(
        boolean valid,
        ErrorCategory errorCategory,
        List<String> errors,
        List<String> warnings
    ) {
        public boolean isStructuralFailure() {
            return !valid && errorCategory == ErrorCategory.STRUCTURAL;
        }
    }

    /**
     * Error category for validation failures.
     */
    public enum ErrorCategory {
        NONE,           // No errors
        STRUCTURAL,     // JSON schema, missing fields, wrong types
        UNSAFE,         // Action not in registry, invalid filters
        OTHER           // Other semantic issues
    }
}
```

#### 2.2 Intent Extraction Strategy Interface

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/extraction/IntentExtractionStrategy.java`

```java
package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;

/**
 * Strategy for extracting intents from user queries.
 */
public interface IntentExtractionStrategy {

    /**
     * Attempt to extract intents from query.
     *
     * @param query user query
     * @param context orchestration context
     * @return extraction attempt result
     */
    ExtractionAttempt attemptExtract(String query, OrchestrationContext context);

    /**
     * Get strategy name for telemetry.
     */
    String getStrategyName();
}
```

#### 2.3 Extraction Attempt Result

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/extraction/ExtractionAttempt.java`

```java
package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentExtractionValidator.ValidationResult;
import lombok.Builder;
import lombok.Data;

/**
 * Result of an intent extraction attempt.
 */
@Data
@Builder
public class ExtractionAttempt {

    /**
     * Whether extraction succeeded (structurally valid).
     */
    private boolean success;

    /**
     * The extracted response (may be null if failed).
     */
    private MultiIntentResponse response;

    /**
     * Validation result.
     */
    private ValidationResult validationResult;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * Exception if failed.
     */
    private Exception exception;

    /**
     * Strategy that produced this attempt.
     */
    private String strategyName;

    /**
     * Number of LLM calls made.
     */
    private int llmCalls;

    public boolean isStructuralFailure() {
        return !success && validationResult != null && validationResult.isStructuralFailure();
    }
}
```

#### 2.4 Compound Strategy (Current Behavior)

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/extraction/CompoundIntentExtractionStrategy.java`

```java
package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.EnrichedPromptBuilder;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;

/**
 * Standard compound intent extraction (current behavior).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompoundIntentExtractionStrategy implements IntentExtractionStrategy {

    private final AICoreService aiCoreService;
    private final EnrichedPromptBuilder enrichedPromptBuilder;
    private final IntentExtractionValidator validator;
    private final ObjectMapper objectMapper;

    @Override
    public ExtractionAttempt attemptExtract(String query, OrchestrationContext context) {
        try {
            String systemPrompt = enrichedPromptBuilder.buildSystemPrompt(context);
            String userPrompt = "analyze the following request from a user and extract the user intents from it in the provided format in system prompt\n\n-----------\n\nUser's question is :( " + query +")";

            AIGenerationRequest generationRequest = AIGenerationRequest.builder()
                .entityId("intent-" + UUID.randomUUID())
                .entityType("intent_extraction")
                .generationType("intent_extraction")
                .systemPrompt(systemPrompt)
                .prompt(userPrompt)
                .parameters(jsonOnlyResponseParameters())
                .userId(context.getUserId())
                .build();

            AIGenerationResponse generationResponse = aiCoreService.generateContent(
                generationRequest,
                LlmPurpose.ORCHESTRATION
            );

            String content = generationResponse != null ? generationResponse.getContent() : null;
            if (!StringUtils.hasText(content)) {
                return ExtractionAttempt.builder()
                    .success(false)
                    .errorMessage("Empty response from LLM")
                    .strategyName(getStrategyName())
                    .llmCalls(1)
                    .build();
            }

            String sanitized = stripCodeFences(content);
            MultiIntentResponse response = objectMapper.readValue(sanitized, MultiIntentResponse.class);

            IntentExtractionValidator.ValidationResult validation = validator.validate(response);

            return ExtractionAttempt.builder()
                .success(validation.valid())
                .response(response)
                .validationResult(validation)
                .strategyName(getStrategyName())
                .llmCalls(1)
                .build();

        } catch (Exception ex) {
            log.warn("Compound extraction failed: {}", ex.getMessage());
            return ExtractionAttempt.builder()
                .success(false)
                .errorMessage(ex.getMessage())
                .exception(ex)
                .strategyName(getStrategyName())
                .llmCalls(1)
                .build();
        }
    }

    @Override
    public String getStrategyName() {
        return "compound";
    }

    private Map<String, Object> jsonOnlyResponseParameters() {
        return Map.of("response_format", Map.of("type", "json_object"));
    }

    private String stripCodeFences(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
```

#### 2.5 Progressive Intent Extraction Engine

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/extraction/ProgressiveIntentExtractionEngine.java`

```java
package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Progressive intent extraction with bounded fallback ladder.
 *
 * <p>Extraction flow:</p>
 * <ol>
 *   <li>Compound (fast path)</li>
 *   <li>Repair (if structural failure, max 1 attempt)</li>
 *   <li>Multi-Step (if repair fails, decomposed prompts)</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ai.intent-extraction.progressive",
    name = "enabled",
    havingValue = "true"
)
@RequiredArgsConstructor
public class ProgressiveIntentExtractionEngine {

    private final CompoundIntentExtractionStrategy compoundStrategy;
    // TODO: Inject repair and multi-step strategies when implemented

    public MultiIntentResponse extract(String query, OrchestrationContext context) {
        int totalLlmCalls = 0;

        // Step 1: Compound (fast path)
        log.debug("Attempting compound intent extraction");
        ExtractionAttempt compoundAttempt = compoundStrategy.attemptExtract(query, context);
        totalLlmCalls += compoundAttempt.getLlmCalls();

        if (compoundAttempt.isSuccess()) {
            log.debug("Compound extraction succeeded (llmCalls={})", totalLlmCalls);
            return compoundAttempt.getResponse();
        }

        // Step 2: Repair (only for structural failures)
        if (compoundAttempt.isStructuralFailure()) {
            log.info("Compound extraction failed structurally, attempting repair");
            // TODO: Implement repair strategy
            // ExtractionAttempt repairAttempt = repairStrategy.attemptExtract(query, context, compoundAttempt);
            // if (repairAttempt.isSuccess()) return repairAttempt.getResponse();
        }

        // Step 3: Multi-Step (decomposed)
        log.warn("All extraction strategies failed, returning error");
        throw new IntentExtractionException(
            "Intent extraction failed after all attempts",
            compoundAttempt.getException()
        );
    }
}
```

#### 2.6 Configuration Properties

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/ProgressiveIntentExtractionProperties.java`

```java
package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * Configuration for progressive intent extraction.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.intent-extraction.progressive")
public class ProgressiveIntentExtractionProperties {

    /**
     * Enable progressive fallback (compound → repair → multi-step).
     * Default: false (use compound only)
     */
    private boolean enabled = false;

    /**
     * Enable repair step.
     * Default: true
     */
    private boolean repairEnabled = true;

    /**
     * Maximum repair attempts.
     * Default: 1
     */
    @Min(0)
    @Max(3)
    private int repairMaxAttempts = 1;

    /**
     * Enable multi-step fallback.
     * Default: true
     */
    private boolean multiStepEnabled = true;

    /**
     * Force specific extraction mode for debugging.
     * Values: compound, repair, multi_step, null (auto)
     */
    private String forceMode;
}
```

---

### Phase 3: vectorSpace Bounded Fan-Out Routing (Week 5-6)

#### 3.1 Vector Space Router Interface

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/vectorspace/VectorSpaceRouter.java`

```java
package com.ai.infrastructure.rag.vectorspace;

import com.ai.infrastructure.dto.Intent;

/**
 * Routes retrieval requests to appropriate vector spaces when vectorSpace is missing.
 */
public interface VectorSpaceRouter {

    /**
     * Route intent to vector space(s).
     *
     * @param intent the intent requiring retrieval
     * @param originalQuery the user's original query
     * @return routing result
     */
    RoutingResult route(Intent intent, String originalQuery);

    /**
     * Get router strategy name.
     */
    String getStrategyName();
}
```

#### 3.2 Routing Result

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/vectorspace/RoutingResult.java`

```java
package com.ai.infrastructure.rag.vectorspace;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of vectorSpace routing.
 */
@Data
@Builder
public class RoutingResult {

    /**
     * Whether routing succeeded.
     */
    private boolean success;

    /**
     * Single vectorSpace (for simple routing).
     */
    private String vectorSpace;

    /**
     * Multiple candidate spaces (for fan-out).
     */
    private List<String> candidateSpaces;

    /**
     * Routing strategy used.
     */
    private RoutingStrategy strategy;

    /**
     * Confidence in routing decision (0.0 to 1.0).
     */
    private double confidence;

    /**
     * Human-readable rationale.
     */
    private String rationale;

    public boolean requiresFanOut() {
        return strategy == RoutingStrategy.FAN_OUT && candidateSpaces != null && candidateSpaces.size() > 1;
    }
}
```

#### 3.3 Routing Strategy Enum

```java
package com.ai.infrastructure.rag.vectorspace;

public enum RoutingStrategy {
    AUTO_ASSIGN,        // Single space → auto-assign
    QUERY_MENTION,      // Matched by query text
    FAN_OUT,            // Multiple spaces → bounded fan-out
    CLARIFICATION,      // Ask user
    HEURISTIC           // Fallback heuristic (largest count)
}
```

#### 3.4 Bounded Fan-Out Router

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/vectorspace/BoundedFanOutRouter.java`

```java
package com.ai.infrastructure.rag.vectorspace;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.intent.KnowledgeBaseOverview;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Routes to multiple vector spaces when vectorSpace is missing, then merges results.
 *
 * <p>Strategy:</p>
 * <ul>
 *   <li>If 1 space → auto-assign (safe)</li>
 *   <li>If >1 space → bounded fan-out (query top N spaces, merge + rerank)</li>
 *   <li>If fan-out weak → clarification</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BoundedFanOutRouter implements VectorSpaceRouter {

    private final ObjectProvider<KnowledgeBaseOverviewService> overviewServiceProvider;
    private final VectorSpaceRoutingProperties config;

    @Override
    public RoutingResult route(Intent intent, String originalQuery) {
        KnowledgeBaseOverview overview = getOverview();
        if (overview == null || overview.getEntityTypes() == null || overview.getEntityTypes().isEmpty()) {
            return RoutingResult.builder()
                .success(false)
                .rationale("No entity types available")
                .build();
        }

        List<String> candidateTypes = overview.getEntityTypes();

        // If single space → auto-assign
        if (candidateTypes.size() == 1) {
            return RoutingResult.builder()
                .success(true)
                .vectorSpace(candidateTypes.get(0))
                .strategy(RoutingStrategy.AUTO_ASSIGN)
                .confidence(1.0)
                .rationale("Only one entity type available")
                .build();
        }

        // Try query mention matching first
        String matched = tryQueryMentionMatch(candidateTypes, originalQuery);
        if (StringUtils.hasText(matched)) {
            return RoutingResult.builder()
                .success(true)
                .vectorSpace(matched)
                .strategy(RoutingStrategy.QUERY_MENTION)
                .confidence(0.8)
                .rationale("Matched entity type '" + matched + "' mentioned in query")
                .build();
        }

        // Multiple spaces and no clear match → bounded fan-out
        List<String> topN = selectTopNCandidates(overview, config.getFanOutMaxSpaces());

        return RoutingResult.builder()
            .success(true)
            .candidateSpaces(topN)
            .strategy(RoutingStrategy.FAN_OUT)
            .confidence(0.5)
            .rationale("Multiple entity types, using fan-out to " + topN.size() + " spaces")
            .build();
    }

    @Override
    public String getStrategyName() {
        return "bounded-fan-out";
    }

    private KnowledgeBaseOverview getOverview() {
        KnowledgeBaseOverviewService service = overviewServiceProvider.getIfAvailable();
        if (service == null) {
            return null;
        }
        try {
            return service.getOverview();
        } catch (Exception ex) {
            log.warn("Failed to get knowledge base overview: {}", ex.getMessage());
            return null;
        }
    }

    private String tryQueryMentionMatch(List<String> candidateTypes, String originalQuery) {
        if (!StringUtils.hasText(originalQuery)) {
            return null;
        }

        String queryLower = originalQuery.toLowerCase(Locale.ROOT);
        for (String type : candidateTypes) {
            if (queryMentionsType(queryLower, type)) {
                return type;
            }
        }
        return null;
    }

    private boolean queryMentionsType(String queryLower, String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (queryLower.contains(normalized)) {
            return true;
        }

        String[] tokens = normalized.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.length() < 3) continue;
            if (queryLower.contains(token) || queryLower.contains(token + "s")) {
                return true;
            }
        }
        return false;
    }

    private List<String> selectTopNCandidates(KnowledgeBaseOverview overview, int maxSpaces) {
        Map<String, Long> counts = overview.getDocumentsByType();
        if (counts == null || counts.isEmpty()) {
            return overview.getEntityTypes().stream().limit(maxSpaces).toList();
        }

        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(maxSpaces)
            .map(Map.Entry::getKey)
            .toList();
    }
}
```

#### 3.5 Configuration Properties

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/vectorspace/VectorSpaceRoutingProperties.java`

```java
package com.ai.infrastructure.rag.vectorspace;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * Configuration for vectorSpace routing when vectorSpace is missing.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.rag.vectorspace-routing")
public class VectorSpaceRoutingProperties {

    /**
     * Routing strategy.
     * Values: HEURISTIC, BOUNDED_FAN_OUT, CLARIFICATION
     * Default: BOUNDED_FAN_OUT
     */
    private String strategy = "BOUNDED_FAN_OUT";

    /**
     * Maximum number of spaces to query in fan-out mode.
     * Default: 3
     */
    @Min(1)
    @Max(10)
    private int fanOutMaxSpaces = 3;

    /**
     * Top K documents to retrieve per space in fan-out.
     * Default: 5
     */
    @Min(1)
    @Max(20)
    private int fanOutTopKPerSpace = 5;

    /**
     * Minimum similarity score to consider fan-out successful.
     * Below this threshold, fall back to clarification.
     * Default: 0.4
     */
    @Min(0)
    @Max(1)
    private double clarificationThreshold = 0.4;
}
```

---

### Phase 4: Integration & Testing (Week 7-8)

#### 4.1 Feature Flag Configuration

**File:** `application.yml`

```yaml
ai:
  # Provider configuration
  providers:
    llm-provider: openai

    # Orchestration-specific (intent extraction, planning)
    orchestration:
      llm-provider: ${ORCHESTRATION_LLM_PROVIDER:}  # Empty = use global
      model: ${ORCHESTRATION_LLM_MODEL:}
      temperature: ${ORCHESTRATION_LLM_TEMPERATURE:0.1}
      maxTokens: ${ORCHESTRATION_LLM_MAX_TOKENS:1200}
      timeout: ${ORCHESTRATION_LLM_TIMEOUT:10}

    # Generation-specific (RAG answers)
    generation:
      llm-provider: ${GENERATION_LLM_PROVIDER:}
      model: ${GENERATION_LLM_MODEL:}
      temperature: ${GENERATION_LLM_TEMPERATURE:0.3}
      maxTokens: ${GENERATION_LLM_MAX_TOKENS:2000}
      timeout: ${GENERATION_LLM_TIMEOUT:30}

  # Intent extraction configuration
  intent-extraction:
    progressive:
      enabled: ${PROGRESSIVE_EXTRACTION_ENABLED:false}  # Start disabled
      repair-enabled: true
      repair-max-attempts: 1
      multi-step-enabled: true
      force-mode: ${EXTRACTION_FORCE_MODE:}  # For debugging

  # Result normalization (already exists)
  orchestration:
    result-normalization:
      enabled: true  # Already implemented

  # vectorSpace routing
  rag:
    vectorspace-routing:
      strategy: ${VECTORSPACE_ROUTING_STRATEGY:BOUNDED_FAN_OUT}
      fan-out-max-spaces: ${FAN_OUT_MAX_SPACES:3}
      fan-out-top-k-per-space: ${FAN_OUT_TOP_K:5}
      clarification-threshold: ${CLARIFICATION_THRESHOLD:0.4}
```

#### 4.2 Telemetry & Metrics

**Add to orchestration result metadata:**

```java
// In IntentQueryExtractor or ProgressiveIntentExtractionEngine:
Map<String, Object> diagnostics = new LinkedHashMap<>();
diagnostics.put("extractionPath", "compound");  // or "repair", "multi_step"
diagnostics.put("extractionAttempts", 1);
diagnostics.put("llmCalls", 1);
diagnostics.put("vectorSpaceRouting", "auto");  // or "fan_out", "clarification"
diagnostics.put("vectorSpaceCandidates", List.of("product", "customer"));

// Add to OrchestrationResult metadata
result.getMetadata().put("extractionDiagnostics", diagnostics);
```

#### 4.3 Unit Tests

**Test Cases:**

1. **Provider Configuration:**
   - Test orchestration vs generation provider resolution
   - Test fallback to global provider
   - Test environment variable mapping

2. **Intent Extraction Validator:**
   - Test structural validation (null, missing fields)
   - Test action validation (handler exists)
   - Test information validation (vectorSpace required)

3. **Progressive Extraction:**
   - Test compound success → no fallback
   - Test compound structural failure → repair invoked
   - Test repair success → returns repaired intent
   - Test repair failure → multi-step invoked (when implemented)

4. **vectorSpace Routing:**
   - Test single space → auto-assign
   - Test query mention match
   - Test fan-out candidate selection
   - Test clarification fallback

#### 4.4 Integration Tests

**File:** `IntentExtractionIntegrationTest.java`

```java
@SpringBootTest
@ActiveProfiles("realapi")
class IntentExtractionIntegrationTest {

    @Autowired
    private IntentQueryExtractor extractor;

    @Test
    void shouldExtractIntentUsingOrchestrationProvider() {
        // Arrange
        OrchestrationContext context = OrchestrationContext.forUser("test-user");

        // Act
        MultiIntentResponse response = extractor.extract(
            "find premium customers",
            context
        );

        // Assert
        assertThat(response.getIntents()).isNotEmpty();
        assertThat(response.getIntents().get(0).getType()).isEqualTo(IntentType.INFORMATION);
    }

    @Test
    void shouldInferVectorSpaceWhenMissing() {
        // Test vectorSpace inference logic
    }
}
```

---

## Rollout Strategy

### Stage 1: Phase 1 (Provider Config)
**Risk:** Low
**Rollout:** Immediate (backward compatible)

- Deploy provider-specific configuration
- Default to global provider (no behavior change)
- Users can opt-in to separate orchestration/generation LLMs

### Stage 2: Phase 2 (Progressive Extraction)
**Risk:** Medium
**Rollout:** Feature-flagged, gradual

1. Deploy with `progressive.enabled=false` (default)
2. Enable in CI real-api tests
3. Monitor metrics (extraction path, llmCalls, success rate)
4. Enable in staging
5. Enable in production (gradual rollout)

### Stage 3: Phase 3 (vectorSpace Routing)
**Risk:** Medium
**Rollout:** After Phase 2 is stable

1. Deploy with `strategy=HEURISTIC` (current behavior)
2. Enable `BOUNDED_FAN_OUT` in staging
3. Monitor cost (vector queries per request)
4. Enable in production

### Stage 4: Observability & Tuning
**Risk:** Low
**Rollout:** Continuous

- Dashboard for extraction paths, routing strategies
- Cost analysis (LLM calls, vector queries)
- Latency percentiles (p50, p95, p99)
- Error rate tracking

---

## Success Metrics

### Before (Baseline)
- Intent extraction failures: ~15% (provider-dependent)
- Missing vectorSpace: ~8% of RAG requests
- Flaky test rate: ~12%
- Silent misrouting: unknown

### After (Target)
- Intent extraction failures: <3% (with progressive fallback)
- Missing vectorSpace: <1% (with routing)
- Flaky test rate: <2%
- Silent misrouting: 0% (telemetry)
- Cost increase: <10% (bounded retries + fan-out)

---

## Open Questions

1. **vectorSpace routing cost:** Are you OK with 3× vector queries when vectorSpace is missing (fan-out N=3)?
2. **Clarification UX:** When fan-out yields weak results, should we ask users or fall back to "best effort"?
3. **Orchestration LLM provider:** Which provider do you prefer for structured outputs (Cohere, OpenAI, Anthropic)?
4. **Rollout timeline:** 8 weeks for all phases, or prioritize specific phases?

---

## Next Steps

1. **Review this document** - Validate assumptions and approach
2. **Prioritize phases** - Decide which phases to implement first
3. **Create tracking issues** - One issue per phase
4. **Assign ownership** - Who implements each component
5. **Set milestones** - Target dates for each phase

---

## Appendix: Alignment with Framework Philosophy

| Principle | How This Solution Honors It |
|-----------|---------------------------|
| **Fail Fast, Fix Bugs** | Bounded attempts, fail with structured errors when exhausted |
| **LLM Decides, Config Constrains** | LLM extracts intent, config bounds retries & routing strategies |
| **Deterministic Contracts** | Normalization layer guarantees provider-agnostic outcomes |
| **Security-First** | All validation before execution, fail-closed on ambiguity |
| **Performance via Caching** | Cache KB overviews, entity types, prompt templates |
| **Observable** | Rich telemetry on extraction path, routing fallback, normalization |
| **No Redundant Fallbacks** | Single progressive chain, each layer has one job |
| **Greenfield** | No legacy support, clean architecture from the start |

---

**Document Version:** 1.0
**Last Updated:** 2026-01-14
**Status:** Ready for Implementation
