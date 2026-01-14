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
Progressive stabilization through dedicated pipeline steps:
```
Pipeline Orchestration Flow:
═══════════════════════════════════════════════════════════════

Order 50: IntentExtractionStep (Enhanced with Progressive Engine)
┌────────────────────────────────────────────────────────────┐
│  Progressive Intent Extraction Engine                       │
│  • Compound (fast path)                                     │
│  • Repair (structural only, max 1 attempt)                  │
│  • Multi-Step (decomposed: classify → action → retrieval)   │
│                                                              │
│  Output: Structurally valid MultiIntentResponse            │
└────────────────────────────────────────────────────────────┘
                          ↓
Order 55: VectorSpaceResolutionStep (NEW)
┌────────────────────────────────────────────────────────────┐
│  VectorSpace Resolution Policy                              │
│  • If 1 space → auto-assign                                 │
│  • If >1 space → bounded fan-out (top N spaces, topK/each)  │
│  • If fan-out weak → clarification                          │
│  • Deterministic validation & enrichment                    │
│                                                              │
│  Output: Fully resolved retrieval plan (no missing spaces)  │
└────────────────────────────────────────────────────────────┘
                          ↓
Order 60: IntentHandlingStep (Existing)
┌────────────────────────────────────────────────────────────┐
│  Intent Execution                                           │
│  • Route to action handlers                                 │
│  • Execute RAG retrieval                                    │
│  • Generate responses                                       │
└────────────────────────────────────────────────────────────┘
                          ↓
Order 65: OrchestrationResultNormalizationStep (✅ EXISTS)
┌────────────────────────────────────────────────────────────┐
│  Result Normalization                                       │
│  • Provider-agnostic contract enforcement                   │
│  • Error code standardization                               │
│  • Child error bubbling (with soft error handling)          │
│                                                              │
│  Output: Canonical OrchestrationResult                      │
└────────────────────────────────────────────────────────────┘
```

**Key Architectural Decision:** VectorSpace resolution is a **separate pipeline step** (not embedded in extraction logic) because:
- Intent extraction should not own retrieval policies
- Retrieval must never run with missing vectorSpace
- Clean separation enables independent testing and feature toggling
- Follows existing pipeline pattern

---

## Architectural Principles

These principles guide the design and implementation of this solution, ensuring alignment with the AI Fabric Framework philosophy.

### 1. Separation of Concerns

**Intent Extraction** → produces structured intent (structural validation only)
**VectorSpace Resolution** → resolves routing (semantic policy)
**Result Normalization** → enforces contract (system-fact driven)

Each layer has ONE clear responsibility. No mixing.

### 2. Repair is Structural-Only (Never Semantic)

**Critical Rule:** Repair fixes JSON/schema correctness, NOT semantic decisions.

```java
// ✅ CORRECT - Repair fixes structure:
// Input:  {"type":"ACTION","action":"search_products" // ← Missing closing brace
// Repair: {"type":"ACTION","action":"search_products"}

// ❌ WRONG - Repair should NOT do this:
// Input:  {"type":"ACTION","requiresRetrieval":true,"vectorSpace":""}
// Repair: {"type":"ACTION","requiresRetrieval":true,"vectorSpace":"products"} // ← Guessing!
```

**Why this matters:**
- If `vectorSpace` cannot be deterministically derived from schema/structure, leave it unset
- Routing policy (VectorSpaceResolutionStep) handles missing vectorSpace semantically
- Repair should never "infer" missing fields using query analysis

### 3. System-Fact-Driven Normalization

**Normalize based on deterministic system facts:**
- ✅ Action handler exists in registry → valid
- ✅ Action handler missing → `ERROR` with `ACTION_NOT_FOUND`
- ✅ Child intent failed → bubble error to parent
- ❌ Provider wrapper type → ignore (provider-dependent)
- ❌ Provider prose/wording → ignore (provider-dependent)

**Contract invariants:**
- `type`: canonical top-level outcome
- `success`: derived from system facts (not provider boolean)
- `errorCode`: stable identifier for clients
- `message`: product-owned (not dependent on provider phrasing)

### 4. Bounded Fallback Behavior

**No unbounded retry loops. All fallbacks are strictly bounded:**

| Layer | Fallback | Bound |
|-------|----------|-------|
| Intent Extraction | Repair loop | Max 1 attempt (default) |
| Intent Extraction | Multi-step | 3-4 steps max |
| vectorSpace Routing | Fan-out | Max N spaces × topK docs |
| Overall | Total LLM calls | Max 5 per request (configurable) |

### 5. No Silent Misrouting

**For multi-domain knowledge bases:**
- "Confident-but-wrong" is worse than clarification
- Prefer bounded fan-out (coverage + bounded cost)
- When uncertain → ask for clarification (correctness-first)
- Avoid "largest-count as final answer" for production multi-domain KBs

### 6. Provider-Agnostic Determinism

**Same input + same configuration → same outcome, regardless of provider:**
- OpenAI, Anthropic, Cohere should produce same final `OrchestrationResult` type
- Tests assert canonical invariants (`type`, `success`, `errorCode`)
- Tests never assert provider-specific wrappers or prose

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

### Phase 3: VectorSpace Resolution Pipeline Step (Week 5-6)

**New Pipeline Step:** `VectorSpaceResolutionStep` (Order 55)

**Pipeline Integration:**
```
Order 50: IntentExtractionStep → Order 55: VectorSpaceResolutionStep → Order 60: IntentHandlingStep
```

**Why a separate pipeline step:**
- Intent extraction should not own retrieval policies (separation of concerns)
- Retrieval must never run with missing vectorSpace (guarantee safety)
- Clean separation enables independent testing and feature toggling
- Follows existing pipeline pattern (consistent with framework architecture)

#### 3.1 VectorSpace Resolution Step Implementation

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/VectorSpaceResolutionStep.java`

```java
package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.rag.vectorspace.VectorSpaceRouter;
import com.ai.infrastructure.rag.vectorspace.RoutingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Pipeline step that resolves missing vectorSpace for retrieval intents.
 *
 * <p>Guarantees: If an intent requires retrieval, a safe retrieval plan is resolved
 * before reaching IntentHandlingStep.</p>
 *
 * <p><strong>Order:</strong> 55 (after IntentExtractionStep, before IntentHandlingStep)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSpaceResolutionStep implements OrchestrationPipelineStep {

    private final VectorSpaceRouter vectorSpaceRouter;

    @Override
    public int getOrder() {
        return 55; // After IntentExtractionStep (50), before IntentHandlingStep (60)
    }

    @Override
    public StepResult execute(OrchestrationContext context) {
        MultiIntentResponse intents = context.getExtractedIntents();

        if (intents == null || intents.getIntents() == null) {
            return StepResult.continueProcessing();
        }

        for (Intent intent : intents.getIntents()) {
            if (requiresVectorSpaceResolution(intent)) {
                resolveVectorSpace(intent, context);
            }
        }

        return StepResult.continueProcessing();
    }

    private boolean requiresVectorSpaceResolution(Intent intent) {
        return intent.getType() == IntentType.INFORMATION
            && Boolean.TRUE.equals(intent.getRequiresRetrieval())
            && !hasValidVectorSpace(intent);
    }

    private boolean hasValidVectorSpace(Intent intent) {
        return intent.getVectorSpace() != null && !intent.getVectorSpace().isBlank();
    }

    private void resolveVectorSpace(Intent intent, OrchestrationContext context) {
        log.debug("Resolving missing vectorSpace for intent");

        RoutingResult routing = vectorSpaceRouter.route(intent, context.getOriginalQuery());

        if (routing.isSuccess()) {
            if (routing.requiresFanOut()) {
                // Store fan-out candidates for retrieval handler
                intent.setMetadata("vectorSpace.fanOut", true);
                intent.setMetadata("vectorSpace.candidates", routing.getCandidateSpaces());
                intent.setVectorSpace(String.join(",", routing.getCandidateSpaces()));
            } else {
                intent.setVectorSpace(routing.getVectorSpace());
            }

            // Store routing metadata
            intent.setMetadata("vectorSpace.routing", routing.getStrategy().name());
            intent.setMetadata("vectorSpace.confidence", routing.getConfidence());
            intent.setMetadata("vectorSpace.rationale", routing.getRationale());

            log.info("Resolved vectorSpace: strategy={}, result={}",
                routing.getStrategy(), routing.getVectorSpace());
        } else {
            log.warn("VectorSpace resolution failed: {}", routing.getRationale());
            // Keep vectorSpace null - IntentHandlingStep will handle clarification
        }
    }
}
```

#### 3.2 Vector Space Router Interface

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

#### 3.5 Fan-Out Result Merging (Rank-Based Strategy)

**Critical Design Decision:** Use **rank-based merging** instead of score normalization.

**Why Rank-Based Merging:**
- Different vector DBs use different similarity metrics (cosine similarity, L2 distance, dot product, TF-IDF)
- Score scales are not comparable across providers (e.g., Pinecone [0,1] vs Milvus [0,∞])
- Normalizing scores across providers is complex and error-prone
- Rank-based merging is deterministic and provider-agnostic

**New File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/vectorspace/RankBasedMerger.java`

```java
package com.ai.infrastructure.rag.vectorspace;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Merges retrieval results from multiple vector spaces using rank-based strategy.
 *
 * <p><strong>Why rank-based:</strong> Different vector databases use incompatible similarity
 * metrics. Instead of normalizing scores (complex and error-prone), we use document rank
 * within each space, which is provider-agnostic and deterministic.</p>
 */
@Slf4j
@Component
public class RankBasedMerger {

    /**
     * Merge results from multiple spaces using rank-based strategy.
     *
     * <p>Strategy:</p>
     * <ol>
     *   <li>Take top K documents from each space (already ranked by similarity)</li>
     *   <li>Interleave by rank to avoid bias toward first space</li>
     *   <li>Preserve source space metadata for observability</li>
     * </ol>
     *
     * @param resultsBySpace map of space name to ranked documents
     * @param topKPerSpace max documents to take from each space
     * @return merged list of documents (interleaved by rank)
     */
    public <T> List<T> mergeByRank(Map<String, List<T>> resultsBySpace, int topKPerSpace) {
        if (resultsBySpace == null || resultsBySpace.isEmpty()) {
            return List.of();
        }

        log.debug("Merging results from {} spaces using rank-based strategy", resultsBySpace.size());

        // Interleave by rank: take rank-1 from each space, then rank-2, etc.
        List<T> merged = new ArrayList<>();
        int maxRank = topKPerSpace;

        for (int rank = 0; rank < maxRank; rank++) {
            for (Map.Entry<String, List<T>> entry : resultsBySpace.entrySet()) {
                List<T> docs = entry.getValue();
                if (rank < docs.size()) {
                    merged.add(docs.get(rank));
                }
            }
        }

        log.debug("Merged {} documents from {} spaces", merged.size(), resultsBySpace.size());
        return merged;
    }

    /**
     * Example usage in RAG retrieval handler:
     *
     * <pre>{@code
     * // Fan-out to multiple spaces
     * Map<String, List<Document>> resultsBySpace = new HashMap<>();
     * for (String space : candidateSpaces) {
     *     List<Document> docs = vectorDB.search(query, space, topK);
     *     resultsBySpace.put(space, docs);
     * }
     *
     * // Merge using rank-based strategy
     * List<Document> merged = rankBasedMerger.mergeByRank(resultsBySpace, topKPerSpace);
     *
     * // Continue with generation using merged context
     * }</pre>
     */
}
```

**Comparison: Score Normalization vs Rank-Based**

```java
// ❌ WRONG - Score normalization (complex, error-prone):
public List<Document> mergeByScore(Map<String, List<Document>> results) {
    List<Document> all = new ArrayList<>();

    for (Map.Entry<String, List<Document>> entry : results.entrySet()) {
        String provider = getProviderForSpace(entry.getKey());

        for (Document doc : entry.getValue()) {
            // Nightmare: different similarity metrics per provider
            double normalized = switch (provider) {
                case "pinecone" -> doc.getScore(); // cosine [0, 1]
                case "milvus" -> 1.0 / (1.0 + doc.getScore()); // L2 distance [0, ∞]
                case "lucene" -> normalizeT fIdf(doc.getScore()); // TF-IDF [0, ∞]
                default -> doc.getScore();
            };
            doc.setNormalizedScore(normalized);
            all.add(doc);
        }
    }

    // Sort by normalized score (but normalization is approximate!)
    all.sort(Comparator.comparingDouble(Document::getNormalizedScore).reversed());
    return all;
}

// ✅ CORRECT - Rank-based (simple, deterministic):
public List<Document> mergeByRank(Map<String, List<Document>> results, int topK) {
    List<Document> merged = new ArrayList<>();

    // Interleave by rank (no score normalization needed)
    for (int rank = 0; rank < topK; rank++) {
        for (List<Document> docs : results.values()) {
            if (rank < docs.size()) {
                merged.add(docs.get(rank)); // Already ranked within space
            }
        }
    }

    return merged; // Deterministic, provider-agnostic
}
```

#### 3.6 Configuration Properties

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

#### 4.3 Soft Error Behavior Documentation (IMPORTANT for Tests)

**Existing Behavior in `OrchestrationResultNormalizer`:**

The current implementation includes **deliberate soft error handling** for compound intents. This behavior is NOT a bug - it's a feature that affects test expectations.

**Rule:** For compound intents, if the **primary child succeeds** and a **non-primary child** has a "soft error," normalization **promotes the primary success** rather than failing the entire request.

**Soft Errors:**
- `ACTION_NOT_FOUND` (common: "summarize this" misclassified as ACTION)
- `GENERATION_FAILED` (non-primary generation failed but retrieval succeeded)

**Why This Matters:**

```java
// Scenario: User asks compound query
User: "Get me premium customers and summarize the results"

// LLM returns compound intent:
MultiIntentResponse {
  intents: [
    Intent {                    // PRIMARY (first, most important)
      type: INFORMATION,
      requiresRetrieval: true,
      vectorSpace: "customer"
    },
    Intent {                    // NON-PRIMARY (secondary)
      type: ACTION,
      action: "summarize"       // ← No handler exists!
    }
  ]
}

// Execution:
// 1. Primary intent (retrieval) → ✅ SUCCEEDS (finds premium customers)
// 2. Non-primary intent (action) → ❌ FAILS (ACTION_NOT_FOUND)

// Normalization Decision:
// Option A (strict): Return ERROR (fail entire request)
// Option B (pragmatic): Return INFORMATION_PROVIDED (primary succeeded) ← CURRENT BEHAVIOR

// Result:
OrchestrationResult {
  type: INFORMATION_PROVIDED,    // ← Promoted primary success
  success: true,
  data: { customers: [...] },
  metadata: {
    softChildErrorCode: "ACTION_NOT_FOUND"  // ← Error preserved for observability
  }
}
```

**Test Implications:**

```java
// ❌ WRONG TEST (expects strict error bubbling):
@Test
void shouldFailEntireCompoundWhenAnyChildFails() {
    // Arrange: compound with primary success + non-primary ACTION_NOT_FOUND
    // Act: execute orchestration
    // Assert:
    assertThat(result.getType()).isEqualTo(OrchestrationResultType.ERROR); // ← FAILS!
}

// ✅ CORRECT TEST (expects soft error handling):
@Test
void shouldPromotePrimarySuccessWhenNonPrimaryHasSoftError() {
    // Arrange: compound with primary INFORMATION success + non-primary ACTION_NOT_FOUND
    // Act: execute orchestration
    // Assert:
    assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED); // ← PASSES
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getMetadata().get("softChildErrorCode")).isEqualTo("ACTION_NOT_FOUND");
}
```

**Hard Errors (Always Bubble):**

If non-primary child has a **hard error** (not ACTION_NOT_FOUND or GENERATION_FAILED), normalization WILL bubble to top-level ERROR:

```java
// Hard errors (always fail compound):
- SECURITY_VIOLATION
- VALIDATION_ERROR
- DATABASE_ERROR
- UNEXPECTED_ERROR
```

**Documentation Location:**

This behavior is implemented in:
- `OrchestrationResultNormalizer.normalize()` (lines 38-64)
- See `promoteCompoundPrimary()` method

**Why This Design:**

User intent was primarily to **get premium customers**. The "summarize" was secondary. Failing the entire request because we can't summarize would be poor UX when we successfully retrieved what they asked for.

#### 4.4 Unit Tests

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

### Extraction Metrics

**Intent Extraction Path Distribution:**
| Metric | Baseline | Target | Measurement |
|--------|----------|--------|-------------|
| Compound fast-path success rate | 85% | 92% | `intentExtraction.path=compound AND success=true` |
| Repair invocations | Unknown | <12% | `intentExtraction.path=repair` |
| Multi-step fallback usage | 0% (doesn't exist) | <3% | `intentExtraction.path=multi_step` |
| Structural failure rate | ~15% overall | <3% | Failed to produce valid MultiIntentResponse |

**Per-Provider Structural Failures:**
| Provider | Baseline (estimate) | Target |
|----------|---------------------|--------|
| OpenAI GPT-4o | ~5% | <2% |
| Cohere Command-R | ~10% | <3% |
| Anthropic Claude | ~8% | <2% |
| Gemini Pro | ~18% | <5% |

**Latency by Extraction Path:**
| Path | p50 | p95 | p99 |
|------|-----|-----|-----|
| Compound (baseline) | 800ms | 1500ms | 2500ms |
| Compound + Repair | 1600ms | 3000ms | 5000ms |
| Multi-Step (target) | 2400ms | 4500ms | 7000ms |

### Routing Metrics

**vectorSpace Resolution:**
| Metric | Baseline | Target | Measurement |
|--------|----------|--------|-------------|
| Missing vectorSpace rate | ~8% | <1% | Intents with `requiresRetrieval=true AND vectorSpace=null` |
| Auto-assign (single space) | Unknown | 40-50% | `routing.vectorSpace.policy=single` |
| Fan-out usage | 0% (doesn't exist) | 30-40% | `routing.vectorSpace.policy=fanout` |
| Clarification requests | 0% (crashes instead) | 5-10% | `routing.vectorSpace.policy=clarify` |
| Heuristic last-resort | ~8% (all cases) | <5% | `routing.vectorSpace.policy=heuristic` |

**Fan-Out Cost Tracking:**
| Metric | Baseline | Target | Notes |
|--------|----------|--------|-------|
| Vector queries per request | 1 (when space known) | 1-3 (avg 1.8) | Max N spaces with topK per space |
| Fan-out similarity distributions | N/A | p50 > 0.7, p95 > 0.5 | Track weak results that trigger clarification |
| Weak-results rate (below threshold) | N/A | <15% | Results below `clarificationThreshold=0.4` |

### Normalization Metrics

**Result Normalization:**
| Metric | Baseline | Target | Measurement |
|--------|----------|--------|-------------|
| Results normalized vs unchanged | Unknown | 15-20% | Results modified by normalizer |
| Flaky test rate | ~12% | <2% | Tests asserting provider-dependent wrappers |
| Provider-agnostic contract compliance | ~70% | 100% | All results have canonical `type`, `success`, `errorCode` |

**Top Error Codes (Expected Distribution):**
| Error Code | % of Errors | Notes |
|------------|-------------|-------|
| `ACTION_NOT_FOUND` | 45% | Most common: unregistered actions |
| `CHILD_ERROR` | 25% | Bubbled child failures |
| `GENERATION_FAILED` | 15% | LLM generation issues |
| `VECTORSPACE_MISSING` | 10% | Should decrease to <1% |
| `VALIDATION_ERROR` | 5% | Schema/invariant violations |

**Compound Soft Child Errors:**
| Metric | Baseline | Target | Notes |
|--------|----------|--------|-------|
| Frequency of soft errors | Unknown | 8-12% of compound intents | `metadata.softChildErrorCode` present |
| Primary success promotion rate | Unknown | 100% | When primary succeeds + non-primary soft error |

### Cost & Performance Metrics

**LLM Cost Analysis:**
| Scenario | Baseline LLM Calls | Target LLM Calls | Cost Impact |
|----------|-------------------|------------------|-------------|
| Simple query (compound succeeds) | 1 | 1 | 0% |
| Structural failure (repair) | 1 (fails) | 2 (1+repair) | +100% for affected queries (~12%) |
| Complex query (multi-step) | 1 (fails) | 3-4 | +300% for affected queries (<3%) |
| **Weighted average** | 1.0 | 1.14 | **+14% overall** |

**Vector Query Cost:**
| Scenario | Baseline | Target | Cost Impact |
|----------|----------|--------|-------------|
| vectorSpace known | 1 | 1 | 0% |
| vectorSpace missing (crashes) | 0 (crashes) | 3 (fan-out) | +200% for affected queries (~8%) |
| **Weighted average** | 0.92 (crashes excluded) | 1.16 | **+26% overall** |

**Combined Cost Impact:** +18% overall (LLM: +14%, Vector: +26% on subset)

**Performance SLA Targets:**
| Metric | Baseline | Target | Acceptable Max |
|--------|----------|--------|----------------|
| p50 latency | 800ms | 900ms | 1200ms |
| p95 latency | 1500ms | 2000ms | 3000ms |
| p99 latency | 2500ms | 3500ms | 5000ms |
| Timeout rate | ~5% | <1% | <2% |

### Reliability Metrics

**System Reliability:**
| Metric | Baseline | Target | Measurement |
|--------|----------|--------|-------------|
| Silent misrouting rate | Unknown (no telemetry) | 0% | All routing decisions logged |
| Crash rate (null vectorSpace) | ~8% | 0% | VectorSpaceResolutionStep guarantees resolution |
| Test stability | ~88% (12% flaky) | >98% | Tests assert canonical contracts |
| Provider-switching regression rate | Unknown | <1% | Same query+config → same outcome |

### Observability & Debugging

**Telemetry Coverage:**
| Metric | Baseline | Target |
|--------|----------|--------|
| Extraction path logged | No | 100% |
| Routing strategy logged | No | 100% |
| Normalization applied logged | No | 100% |
| Diagnostic metadata attached | <10% | 100% |
| Cost per request tracked | No | 100% |

---

## Monitoring Dashboard (Recommended)

```yaml
# Grafana/Datadog Dashboard

Extraction Panel:
  - Gauge: Structural failure rate (target: <3%)
  - Pie chart: Extraction path distribution (compound/repair/multi-step)
  - Line graph: p95 latency by path (last 24h)
  - Table: Per-provider failure rates

Routing Panel:
  - Gauge: Missing vectorSpace rate (target: <1%)
  - Pie chart: Routing strategy distribution (auto/fan-out/clarify/heuristic)
  - Line graph: Fan-out vector queries per request (cost tracking)
  - Histogram: Fan-out similarity score distributions

Normalization Panel:
  - Gauge: Flaky test rate (target: <2%)
  - Bar chart: Top error codes (frequency)
  - Counter: Soft child error frequency
  - Line graph: Normalization application rate

Cost Panel:
  - Counter: Total LLM calls (per hour)
  - Counter: Total vector queries (per hour)
  - Line graph: Cost per request trend (rolling average)
  - Alert: Cost anomalies (>30% increase)
```

---

## Open Decisions (Resolve Before Implementation)

These questions require team discussion and explicit decisions before proceeding with implementation.

### 1. Clarification Outcome Representation

**Decision Needed:** How should clarification requirements be surfaced to clients?

**Option A: New `OrchestrationResultType.CLARIFICATION_REQUIRED` (Recommended)**
```java
public enum OrchestrationResultType {
    INFORMATION_PROVIDED,
    ACTION_EXECUTED,
    ERROR,
    OUT_OF_SCOPE,
    COMPOUND_HANDLED,
    CLARIFICATION_REQUIRED  // ← NEW
}
```

**Pros:**
- ✅ Explicit, clear semantics for clients
- ✅ Tests can assert specifically for clarification
- ✅ Aligns with greenfield philosophy (clean enums)

**Cons:**
- ❌ Public API change (new enum value)
- ❌ Clients must handle new type

**Option B: Reuse `OUT_OF_SCOPE` with structured metadata**
```java
OrchestrationResult {
    type: OUT_OF_SCOPE,
    message: "Please specify which domain you're asking about",
    metadata: {
        reason: "CLARIFICATION_REQUIRED",
        candidates: ["products", "customers", "orders"]
    }
}
```

**Pros:**
- ✅ No public API change
- ✅ Backward compatible

**Cons:**
- ❌ Less explicit (clients must check metadata)
- ❌ Overloads OUT_OF_SCOPE semantics

**Team Decision:** _______________ (Option A or B)

---

### 2. Fan-Out Merge Strategy Details

**Decision Needed:** Should we support optional score-based merging in the future?

**Current Plan: Rank-Based Only**
- Simple, deterministic, provider-agnostic
- Avoids complex score normalization

**Future Option: Hybrid (Rank + Score)**
- Allow score-based merging when ALL spaces use same vector DB provider
- Fall back to rank-based when mixed providers

**Team Decision:**
- [ ] Rank-based only (sufficient)
- [ ] Add score-based as future enhancement (when?)
- [ ] Implement hybrid approach from start

---

### 3. Router Stage (Mid-Term Enhancement)

**Decision Needed:** When to invest in explicit LLM-based router?

**Current Plan: Rules-Based Routing**
- Query mention matching
- Heuristic (largest count, first type)
- Bounded fan-out when ambiguous

**Future Option: LLM-Based Router Stage**
```java
// Explicit routing step with LLM analysis
RoutingAnalysis {
    vectorSpace: "products",
    confidence: 0.85,
    rationale: "Query mentions 'laptop' and 'price' which are product attributes"
}
```

**Triggers for Router Investment:**
| Metric | Threshold | Rationale |
|--------|-----------|-----------|
| Heuristic last-resort usage | >15% | Rules insufficient |
| Clarification request rate | >20% | Over-asking users |
| Silent misrouting reports | >5 per month | Correctness issues |

**Team Decision:**
- Trigger thresholds: _____________
- Timeline: Q__ 202__ (if triggered)
- Preferred approach: LLM-based / Hybrid / ML classifier

---

### 4. Provider Selection for Orchestration vs Generation

**Decision Needed:** Which LLM providers for each purpose in production?

**Orchestration (Structure-First):**
| Provider | Model | Pros | Cons | Recommendation |
|----------|-------|------|------|----------------|
| Cohere | Command-R Plus | Fast, cheap, good JSON | Smaller context | ✅ Primary |
| OpenAI | GPT-4o-mini | Fast, cheap | Medium quality | ✅ Fallback |
| Anthropic | Haiku | Very fast | Lower accuracy | ⚠️ Testing only |

**Generation (Quality-First):**
| Provider | Model | Pros | Cons | Recommendation |
|----------|-------|------|------|----------------|
| OpenAI | GPT-4o | High quality | Expensive | ✅ Primary |
| Anthropic | Sonnet | Excellent quality | Expensive | ✅ Alternative |
| Cohere | Command-R Plus | Good quality, cheap | Lower than GPT-4o | ⚠️ Budget option |

**Team Decision:**
- Orchestration primary: _______________
- Orchestration fallback: _______________
- Generation primary: _______________
- Generation fallback: _______________

---

### 5. Cost Controls & Limits

**Decision Needed:** Are these cost control limits appropriate for production?

**Current Proposed Limits:**
| Control | Limit | Impact | Adjustable? |
|---------|-------|--------|-------------|
| Max LLM calls per request | 5 | Prevents runaway costs | Yes (config) |
| Max repair attempts | 1 | Bounded structural fallback | Yes (config) |
| Fan-out max spaces | 3 | Bounded vector queries | Yes (config) |
| Fan-out topK per space | 5 | Total: 15 docs max | Yes (config) |
| Multi-step max steps | 4 | Prevents long chains | Code constant |

**Cost Scenarios:**
- Worst case: 5 LLM calls + 15 vector queries = ~$0.08 per request
- Average case: 1.14 LLM calls + 1.16 vector queries = ~$0.012 per request

**Team Decision:**
- [ ] Limits are acceptable as-is
- [ ] Adjust limits (specify): _______________
- [ ] Add per-user/per-tenant limits
- [ ] Add budget alerts at: $___/day

---

### 6. Rollout Timeline & Prioritization

**Decision Needed:** 8 weeks for all phases, or prioritize?

**Proposed Timeline:**
| Phase | Duration | Priority | Dependencies |
|-------|----------|----------|--------------|
| Phase 1: Provider Config | Week 1-2 | HIGH | None |
| Phase 2: Progressive Extraction | Week 3-4 | HIGH | Phase 1 |
| Phase 3: vectorSpace Routing | Week 5-6 | MEDIUM | Phase 2 |
| Phase 4: Testing & Tuning | Week 7-8 | HIGH | All phases |

**Alternative: Prioritize Critical Path**
- Week 1-2: Phase 1 (Provider Config)
- Week 3-4: Phase 3 (vectorSpace Routing) ← Addresses 8% crash rate
- Week 5-6: Phase 2 (Progressive Extraction)
- Week 7-8: Phase 4 (Testing & Tuning)

**Team Decision:**
- [ ] Sequential (Phase 1 → 2 → 3 → 4)
- [ ] Prioritize routing (Phase 1 → 3 → 2 → 4)
- [ ] Parallel implementation (Phase 1 + 3 in parallel)
- [ ] Custom timeline: _______________

---

### 7. Clarification UX Strategy

**Decision Needed:** When fan-out yields weak results, what should happen?

**Option A: Ask for Clarification (Correctness-First)**
```
System: "I found some results, but I'm not confident. Which domain are you asking about?"
Options: [Products, Customers, Orders]
```

**Pros:**
- ✅ Avoids wrong answers
- ✅ User learns system capabilities

**Cons:**
- ❌ Extra friction
- ❌ May annoy users

**Option B: Best-Effort with Disclaimer (UX-First)**
```
System: "Here are results from multiple domains (I wasn't sure which you meant):"
[Shows results from top 2 spaces]
"Was this helpful? If not, please specify: Products / Customers / Orders"
```

**Pros:**
- ✅ No blocking
- ✅ User gets something

**Cons:**
- ❌ May return wrong results
- ❌ User trust issues if wrong

**Option C: Adaptive (Confidence-Based)**
- High confidence (>0.7): auto-select
- Medium confidence (0.4-0.7): best-effort with disclaimer
- Low confidence (<0.4): ask for clarification

**Team Decision:** _______________ (Option A, B, or C)

---

### 8. Multi-Step Extraction Prompt Design

**Decision Needed:** What should the multi-step extraction prompts look like?

**Proposed Steps:**
1. **Classify:** `IntentType` + `requiresRetrieval` + `requiresGeneration`
2. **Action (if ACTION):** Select from available actions (hard constraint)
3. **Retrieval (if INFORMATION):** `vectorSpace` + filters + sort
4. **Relationship (if needed):** Source/target entities + relationship type

**Alternative: Fewer Steps**
- Combine 1+3 into single retrieval prompt

**Team Decision:**
- [ ] 4-step (as proposed)
- [ ] 3-step (combine classify + retrieval)
- [ ] Custom: _______________

---

## Decision Tracking

| Decision # | Status | Decided By | Date | Notes |
|------------|--------|------------|------|-------|
| 1. Clarification Outcome | ⏳ Pending | - | - | - |
| 2. Merge Strategy | ⏳ Pending | - | - | - |
| 3. Router Stage | ⏳ Pending | - | - | - |
| 4. Provider Selection | ⏳ Pending | - | - | - |
| 5. Cost Controls | ⏳ Pending | - | - | - |
| 6. Rollout Timeline | ⏳ Pending | - | - | - |
| 7. Clarification UX | ⏳ Pending | - | - | - |
| 8. Multi-Step Prompts | ⏳ Pending | - | - | - |

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
