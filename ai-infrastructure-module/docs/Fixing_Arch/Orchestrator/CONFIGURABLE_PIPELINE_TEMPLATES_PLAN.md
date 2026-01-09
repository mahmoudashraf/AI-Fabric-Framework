# Configurable Pipeline Templates Implementation Plan

## Executive Summary

**Goal:** Enable flexible pipeline configurations without separating the orchestrator into a separate module. Instead of module separation, we introduce **Pipeline Templates** - pre-configured pipeline variants optimized for different use cases.

**Current State:**
- `RAGOrchestrator` with single `DefaultOrchestrationPipeline`
- All 10 steps always execute (unless terminated early)
- No way to run partial pipelines for specific use cases

**Proposed State:**
- Rename `RAGOrchestrator` to `Orchestrator` (cleaner, more generic)
- Multiple pipeline templates (Security, Compliance, Batch, Full, etc.)
- Configuration-driven step inclusion/exclusion
- `PipelineFactory` to create appropriate pipelines
- **Default = FULL pipeline (all steps)** - same behavior as current
- Runtime selection of pipeline based on use case

**Key Benefits:**
- Different latency profiles for different use cases
- Cost optimization (skip expensive LLM calls when not needed)
- Simpler testing (can test individual pipeline configurations)
- No module separation complexity
- Runtime flexibility via configuration

---

## Design Principles

1. **Configuration Over Code** - Enable/disable steps via properties, not code changes
2. **Backward Compatible** - Default behavior unchanged
3. **Composable** - Steps can be combined in any valid order
4. **Type-Safe** - Pipeline templates are strongly typed enums
5. **Testable** - Each configuration testable in isolation
6. **Performance Aware** - Templates optimized for specific latency/cost profiles

---

## Pipeline Templates

### Template Definitions

| Template | Steps | Use Case | Latency | Cost |
|----------|-------|----------|---------|------|
| **FULL** | All 10 steps | Standard RAG queries | ~500ms | High |
| **SECURITY_ONLY** | 10, 20, 40 | Pre-screening, rate limiting | <50ms | Low |
| **COMPLIANCE_AUDIT** | 10, 20, 30, 40, 90 | Data privacy scanning | ~100ms | Low |
| **INTENT_ANALYSIS** | 10-50 | NLU classification only | ~200ms | Medium |
| **BATCH** | 10-90 (skip 100) | Bulk processing | ~450ms | High |
| **ANONYMOUS** | All (blocks ACTION) | Public self-service | ~500ms | High |
| **LIGHTWEIGHT** | 10, 20, 50, 60, 70 | Fast RAG without extras | ~300ms | Medium |

### Step Reference

```
Order 10: SecurityAnalysisStep      - Block malicious requests
Order 20: AccessControlStep         - Verify permissions
Order 30: PIIDetectionStep          - Detect/redact PII
Order 40: ComplianceCheckStep       - Validate compliance
Order 50: IntentExtractionStep      - LLM intent analysis
Order 60: IntentHandlingStep        - Execute actions/RAG
Order 70: MetadataBuildingStep      - Enrich results
Order 80: SmartSuggestionsStep      - Generate recommendations
Order 90: ResponseSanitizationStep  - Clean output
Order 100: HistoryPersistenceStep   - Record for analytics
```

---

## Phase 1: Core Infrastructure

### 1.1 Create PipelineTemplate Enum

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/PipelineTemplate.java`

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * Pre-defined pipeline templates optimized for different use cases.
 *
 * <p>Each template defines which pipeline steps should be included.
 * Templates are selected based on use case requirements:</p>
 * <ul>
 *   <li>FULL - Standard RAG with all features</li>
 *   <li>SECURITY_ONLY - Fast security screening</li>
 *   <li>COMPLIANCE_AUDIT - Privacy/compliance validation</li>
 *   <li>INTENT_ANALYSIS - Intent extraction without execution</li>
 *   <li>BATCH - Bulk processing without history persistence</li>
 *   <li>ANONYMOUS - Public access with action restrictions</li>
 *   <li>LIGHTWEIGHT - Fast RAG without suggestions/sanitization</li>
 * </ul>
 *
 * @see PipelineFactory
 * @see PipelineStep
 * @since 2.0
 */
@Getter
@RequiredArgsConstructor
public enum PipelineTemplate {

    /**
     * Full pipeline with all 10 steps.
     * Use for standard authenticated RAG queries.
     */
    FULL(
        "Full Pipeline",
        "Standard RAG with all security, compliance, and analytics features",
        Set.of(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
    ),

    /**
     * Security-only pipeline for pre-screening.
     * Fastest option for threat detection and access validation.
     */
    SECURITY_ONLY(
        "Security Only",
        "Fast security screening without RAG processing",
        Set.of(10, 20, 40)
    ),

    /**
     * Compliance audit pipeline.
     * Security + PII + Compliance + Response sanitization.
     */
    COMPLIANCE_AUDIT(
        "Compliance Audit",
        "Data privacy and compliance validation",
        Set.of(10, 20, 30, 40, 90)
    ),

    /**
     * Intent analysis pipeline.
     * Extracts intent without executing actions or recording history.
     */
    INTENT_ANALYSIS(
        "Intent Analysis",
        "NLU classification and intent extraction only",
        Set.of(10, 20, 30, 40, 50)
    ),

    /**
     * Batch processing pipeline.
     * Full pipeline without history persistence.
     */
    BATCH(
        "Batch Processing",
        "Bulk document processing without analytics recording",
        Set.of(10, 20, 30, 40, 50, 60, 70, 80, 90)
    ),

    /**
     * Anonymous access pipeline.
     * Full pipeline but action intents are blocked in IntentHandlingStep.
     * Note: Uses same steps as FULL but with context-based action blocking.
     */
    ANONYMOUS(
        "Anonymous Access",
        "Public self-service with action restrictions",
        Set.of(10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
    ),

    /**
     * Lightweight RAG pipeline.
     * Security + Intent + Handling + Metadata only.
     * Skips PII, compliance, suggestions, sanitization, history.
     */
    LIGHTWEIGHT(
        "Lightweight RAG",
        "Fast RAG without extras for internal/trusted use",
        Set.of(10, 20, 50, 60, 70)
    ),

    /**
     * Custom template - steps determined by configuration.
     * Use with PipelineProperties to define custom step sets.
     */
    CUSTOM(
        "Custom Pipeline",
        "User-defined step configuration",
        Set.of() // Steps provided via configuration
    );

    private final String displayName;
    private final String description;
    private final Set<Integer> includedStepOrders;

    /**
     * Check if this template includes a specific step order.
     *
     * @param stepOrder the step order to check
     * @return true if the step is included
     */
    public boolean includesStep(int stepOrder) {
        return includedStepOrders.contains(stepOrder);
    }

    /**
     * Get the number of steps in this template.
     *
     * @return step count
     */
    public int getStepCount() {
        return includedStepOrders.size();
    }
}
```

### 1.2 Create PipelineProperties Configuration

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/PipelineProperties.java`

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for pipeline templates.
 *
 * <p>Example configuration:</p>
 * <pre>
 * ai:
 *   orchestration:
 *     pipeline:
 *       default-template: FULL
 *       steps:
 *         security-analysis:
 *           enabled: true
 *         pii-detection:
 *           enabled: false  # Disable for dev
 *         history-persistence:
 *           enabled: true
 *           fail-silently: true
 *       custom:
 *         enabled-steps: [10, 20, 50, 60, 70]
 * </pre>
 *
 * @see PipelineTemplate
 * @see PipelineFactory
 * @since 2.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.orchestration.pipeline")
public class PipelineProperties {

    /**
     * Default pipeline template to use when not specified.
     * Defaults to FULL.
     */
    private PipelineTemplate defaultTemplate = PipelineTemplate.FULL;

    /**
     * Per-step configuration.
     * Allows enabling/disabling individual steps.
     */
    private Map<String, StepConfig> steps = new HashMap<>();

    /**
     * Custom pipeline configuration.
     * Used when template is CUSTOM.
     */
    private CustomPipelineConfig custom = new CustomPipelineConfig();

    /**
     * Named pipeline configurations.
     * Allows defining custom named templates via configuration.
     */
    private Map<String, Set<Integer>> namedPipelines = new HashMap<>();

    /**
     * Configuration for individual steps.
     */
    @Data
    public static class StepConfig {
        /**
         * Whether this step is enabled.
         * Disabled steps are skipped even if included in template.
         */
        private boolean enabled = true;

        /**
         * Whether failures in this step should be silent (non-fatal).
         * Only applicable to non-critical steps like history persistence.
         */
        private boolean failSilently = false;

        /**
         * Step-specific timeout in milliseconds.
         * 0 means use default timeout.
         */
        private long timeoutMs = 0;
    }

    /**
     * Custom pipeline configuration for CUSTOM template.
     */
    @Data
    public static class CustomPipelineConfig {
        /**
         * Set of step orders to include in custom pipeline.
         */
        private Set<Integer> enabledSteps = new HashSet<>();
    }

    /**
     * Get step configuration, returning defaults if not configured.
     *
     * @param stepName the step name (e.g., "security-analysis")
     * @return step configuration
     */
    public StepConfig getStepConfig(String stepName) {
        return steps.getOrDefault(stepName, new StepConfig());
    }

    /**
     * Check if a specific step is enabled globally.
     *
     * @param stepName the step name
     * @return true if enabled
     */
    public boolean isStepEnabled(String stepName) {
        return getStepConfig(stepName).isEnabled();
    }
}
```

### 1.3 Update application.yml Schema

**Location:** `ai-infrastructure-core/src/main/resources/application.yml` (add section)

```yaml
ai:
  orchestration:
    pipeline:
      # Default template for standard orchestration
      default-template: FULL

      # Per-step configuration
      steps:
        security-analysis:
          enabled: true
        access-control:
          enabled: true
        pii-detection:
          enabled: ${AI_PII_DETECTION_ENABLED:true}
        compliance-check:
          enabled: true
        intent-extraction:
          enabled: true
        intent-handling:
          enabled: true
        metadata-building:
          enabled: true
        smart-suggestions:
          enabled: ${AI_SMART_SUGGESTIONS_ENABLED:true}
        response-sanitization:
          enabled: true
        history-persistence:
          enabled: ${AI_HISTORY_ENABLED:true}
          fail-silently: true  # Don't fail orchestration if history fails

      # Custom pipeline configuration (used when template is CUSTOM)
      custom:
        enabled-steps: []

      # Named custom pipelines
      named-pipelines:
        internal-only: [10, 20, 50, 60, 70]
        security-scan: [10, 20, 40]
```

---

## Phase 2: Pipeline Factory

### 2.1 Create PipelineFactory

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/PipelineFactory.java`

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Factory for creating pipeline instances based on templates.
 *
 * <p>The factory:</p>
 * <ul>
 *   <li>Receives all available pipeline steps via injection</li>
 *   <li>Creates pipelines by filtering steps based on template</li>
 *   <li>Caches pipeline instances for reuse</li>
 *   <li>Respects per-step configuration (enabled/disabled)</li>
 * </ul>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * // Get full pipeline (default)
 * Pipeline fullPipeline = pipelineFactory.getPipeline(PipelineTemplate.FULL);
 *
 * // Get security-only pipeline
 * Pipeline securityPipeline = pipelineFactory.getPipeline(PipelineTemplate.SECURITY_ONLY);
 *
 * // Get pipeline by name
 * Pipeline namedPipeline = pipelineFactory.getPipelineByName("internal-only");
 * }</pre>
 *
 * @see PipelineTemplate
 * @see Pipeline
 * @since 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineFactory {

    private static final String LOG_PREFIX = "[PipelineFactory]";

    /**
     * All available pipeline steps (injected by Spring).
     */
    private final List<PipelineStep> allSteps;

    /**
     * Pipeline configuration properties.
     */
    private final PipelineProperties properties;

    /**
     * Cache of created pipelines by template.
     */
    private final Map<PipelineTemplate, Pipeline> templateCache = new ConcurrentHashMap<>();

    /**
     * Cache of named pipelines.
     */
    private final Map<String, Pipeline> namedCache = new ConcurrentHashMap<>();

    /**
     * Get or create a pipeline for the given template.
     *
     * <p>Pipelines are cached for reuse. The cache is invalidated if
     * configuration changes.</p>
     *
     * @param template the pipeline template
     * @return the pipeline instance
     */
    public Pipeline getPipeline(PipelineTemplate template) {
        return templateCache.computeIfAbsent(template, this::createPipeline);
    }

    /**
     * Get the default pipeline based on configuration.
     *
     * @return the default pipeline
     */
    public Pipeline getDefaultPipeline() {
        return getPipeline(properties.getDefaultTemplate());
    }

    /**
     * Get a named pipeline from configuration.
     *
     * @param name the pipeline name
     * @return the pipeline, or default if name not found
     */
    public Pipeline getPipelineByName(String name) {
        return namedCache.computeIfAbsent(name, this::createNamedPipeline);
    }

    /**
     * Create a custom pipeline with specific step orders.
     *
     * <p>This method creates a one-off pipeline that is not cached.</p>
     *
     * @param stepOrders the step orders to include
     * @return the custom pipeline
     */
    public Pipeline createCustomPipeline(Set<Integer> stepOrders) {
        List<PipelineStep> filteredSteps = filterSteps(stepOrders);
        return new ConfigurablePipeline(filteredSteps, "Custom(" + stepOrders + ")");
    }

    /**
     * Get all available step names and their orders.
     *
     * @return map of step name to order
     */
    public Map<String, Integer> getAvailableSteps() {
        return allSteps.stream()
            .collect(Collectors.toMap(
                PipelineStep::getStepName,
                PipelineStep::getOrder
            ));
    }

    /**
     * Clear the pipeline cache.
     * Call this if configuration changes at runtime.
     */
    public void clearCache() {
        templateCache.clear();
        namedCache.clear();
        log.info("{} Pipeline cache cleared", LOG_PREFIX);
    }

    // =========================================================================
    // Private Methods
    // =========================================================================

    private Pipeline createPipeline(PipelineTemplate template) {
        Set<Integer> stepOrders = getEffectiveStepOrders(template);
        List<PipelineStep> filteredSteps = filterSteps(stepOrders);

        String pipelineName = template.name();
        log.info("{} Created {} pipeline with {} steps: {}",
            LOG_PREFIX, pipelineName, filteredSteps.size(),
            filteredSteps.stream()
                .map(PipelineStep::getStepName)
                .collect(Collectors.joining(" -> ")));

        return new ConfigurablePipeline(filteredSteps, pipelineName);
    }

    private Pipeline createNamedPipeline(String name) {
        Set<Integer> stepOrders = properties.getNamedPipelines().get(name);

        if (stepOrders == null || stepOrders.isEmpty()) {
            log.warn("{} Named pipeline '{}' not found, using default", LOG_PREFIX, name);
            return getDefaultPipeline();
        }

        List<PipelineStep> filteredSteps = filterSteps(stepOrders);
        log.info("{} Created named pipeline '{}' with {} steps",
            LOG_PREFIX, name, filteredSteps.size());

        return new ConfigurablePipeline(filteredSteps, name);
    }

    private Set<Integer> getEffectiveStepOrders(PipelineTemplate template) {
        if (template == PipelineTemplate.CUSTOM) {
            return properties.getCustom().getEnabledSteps();
        }
        return template.getIncludedStepOrders();
    }

    private List<PipelineStep> filterSteps(Set<Integer> stepOrders) {
        return allSteps.stream()
            .filter(step -> stepOrders.contains(step.getOrder()))
            .filter(this::isStepEnabled)
            .sorted(Comparator.comparingInt(PipelineStep::getOrder))
            .collect(Collectors.toList());
    }

    private boolean isStepEnabled(PipelineStep step) {
        String stepName = toConfigName(step.getStepName());
        return properties.isStepEnabled(stepName);
    }

    /**
     * Convert step class name to config property name.
     * e.g., "SecurityAnalysis" -> "security-analysis"
     */
    private String toConfigName(String stepName) {
        return stepName
            .replaceAll("([a-z])([A-Z])", "$1-$2")
            .toLowerCase();
    }
}
```

### 2.2 Create ConfigurablePipeline

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/ConfigurablePipeline.java`

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A configurable pipeline implementation that executes a specific set of steps.
 *
 * <p>Unlike {@link DefaultOrchestrationPipeline} which auto-wires all steps,
 * this pipeline executes only the steps provided at construction time.</p>
 *
 * <p>Created by {@link PipelineFactory} based on templates or custom configuration.</p>
 *
 * @see PipelineFactory
 * @see PipelineTemplate
 * @since 2.0
 */
@Slf4j
public class ConfigurablePipeline implements Pipeline {

    private static final String LOG_PREFIX = "[Pipeline]";
    private static final String ERROR_NULL_QUERY = "query must not be null";
    private static final String ERROR_BLANK_QUERY = "query must not be blank";
    private static final String ERROR_NULL_CONTEXT = "context must not be null";
    private static final String ERROR_STEP_FAILED_PREFIX = "Pipeline step failed: ";
    private static final String ERROR_NO_RESULT = "Pipeline completed but no result was produced";

    private final List<PipelineStep> steps;
    private final String pipelineName;

    /**
     * Create a configurable pipeline with the given steps.
     *
     * @param steps the steps to execute (in order)
     * @param pipelineName name for logging
     */
    public ConfigurablePipeline(List<PipelineStep> steps, String pipelineName) {
        this.steps = List.copyOf(steps); // Immutable copy
        this.pipelineName = pipelineName;

        String stepNames = this.steps.stream()
            .map(PipelineStep::getStepName)
            .collect(Collectors.joining(" -> "));

        log.debug("{} [{}] Initialized with {} steps: {}",
            LOG_PREFIX, pipelineName, this.steps.size(), stepNames);
    }

    @Override
    public OrchestrationResult execute(String query, OrchestrationContext context) {
        // Validate inputs
        Objects.requireNonNull(query, ERROR_NULL_QUERY);
        if (query.isBlank()) {
            throw new IllegalArgumentException(ERROR_BLANK_QUERY);
        }
        Objects.requireNonNull(context, ERROR_NULL_CONTEXT);
        context.validate();

        // Create initial context
        PipelineContext pipelineContext = PipelineContext.from(query, context);
        String requestId = pipelineContext.getRequestId();

        log.debug("{} [{}] Starting execution for request {} with {} steps",
            LOG_PREFIX, pipelineName, requestId, steps.size());

        long pipelineStartTime = System.currentTimeMillis();

        // Execute each step
        for (PipelineStep step : steps) {
            // Check if step should be skipped
            if (step.shouldSkip(pipelineContext)) {
                log.debug("{} [{}] Skipping step {} for request {} (terminated={})",
                    LOG_PREFIX, pipelineName, step.getStepName(),
                    requestId, pipelineContext.isShouldTerminate());
                continue;
            }

            log.debug("{} [{}] Executing step {} for request {}",
                LOG_PREFIX, pipelineName, step.getStepName(), requestId);

            long stepStartTime = System.currentTimeMillis();

            try {
                pipelineContext = step.process(pipelineContext);

                long stepDuration = System.currentTimeMillis() - stepStartTime;
                log.debug("{} [{}] Step {} completed in {}ms for request {}",
                    LOG_PREFIX, pipelineName, step.getStepName(), stepDuration, requestId);

            } catch (Exception ex) {
                log.error("{} [{}] Step {} failed for request {}: {}",
                    LOG_PREFIX, pipelineName, step.getStepName(), requestId, ex.getMessage(), ex);
                return OrchestrationResult.error(ERROR_STEP_FAILED_PREFIX + step.getStepName());
            }

            // Check for early termination
            if (pipelineContext.isShouldTerminate()) {
                log.debug("{} [{}] Pipeline terminated early at step {} for request {}",
                    LOG_PREFIX, pipelineName, step.getStepName(), requestId);

                OrchestrationResult terminationResult = pipelineContext.getEarlyTerminationResult();
                if (terminationResult != null) {
                    return terminationResult;
                }
                break;
            }
        }

        long pipelineDuration = System.currentTimeMillis() - pipelineStartTime;
        log.debug("{} [{}] Pipeline execution completed in {}ms for request {}",
            LOG_PREFIX, pipelineName, pipelineDuration, requestId);

        // Return the final result
        OrchestrationResult result = pipelineContext.getIntentResult();
        if (result == null) {
            log.error("{} [{}] {} for request {}", LOG_PREFIX, pipelineName, ERROR_NO_RESULT, requestId);
            return OrchestrationResult.error(ERROR_NO_RESULT);
        }

        return result;
    }

    @Override
    public List<PipelineStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }

    /**
     * Get the pipeline name.
     *
     * @return the pipeline name
     */
    public String getPipelineName() {
        return pipelineName;
    }
}
```

---

## Phase 3: Orchestrator (Replaces RAGOrchestrator)

### 3.1 Create Orchestrator

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/Orchestrator.java`

> **Note:** This replaces `RAGOrchestrator`. Delete the old class after creating this one.

```java
package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.intent.orchestration.pipeline.Pipeline;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineFactory;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Set;

/**
 * Main orchestrator for processing queries through configurable pipelines.
 *
 * <p>The orchestrator supports multiple pipeline templates for different use cases:</p>
 * <ul>
 *   <li>{@link #orchestrate(String, OrchestrationContext)} - Default FULL pipeline (all steps)</li>
 *   <li>{@link #orchestrate(String, OrchestrationContext, PipelineTemplate)} - Specific template</li>
 *   <li>{@link #orchestrateSecurityOnly} - Fast security screening</li>
 *   <li>{@link #orchestrateBatch} - Batch processing without history</li>
 *   <li>{@link #orchestrateIntentOnly} - Intent extraction without execution</li>
 * </ul>
 *
 * <p><strong>Usage:</strong></p>
 * <pre>{@code
 * // Standard orchestration (FULL pipeline - all steps)
 * OrchestrationResult result = orchestrator.orchestrate(query, context);
 *
 * // Security screening only (fast)
 * OrchestrationResult security = orchestrator.orchestrateSecurityOnly(query, context);
 *
 * // Specific template
 * OrchestrationResult batch = orchestrator.orchestrate(query, context, PipelineTemplate.BATCH);
 * }</pre>
 *
 * @see PipelineTemplate
 * @see PipelineFactory
 * @since 2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Orchestrator {

    private static final String ERROR_NULL_QUERY = "query must not be null";
    private static final String ERROR_NULL_CONTEXT = "context must not be null";
    private static final String ERROR_NULL_TEMPLATE = "template must not be null";

    private final PipelineFactory pipelineFactory;

    // =========================================================================
    // Core Orchestration Methods
    // =========================================================================

    /**
     * Orchestrate using the FULL pipeline (all steps).
     *
     * <p>This is the default method - executes all 10 pipeline steps.</p>
     *
     * @param query the user's query
     * @param context the orchestration context
     * @return the orchestration result
     */
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        return orchestrate(query, context, PipelineTemplate.FULL);
    }

    /**
     * Orchestrate using a specific pipeline template.
     *
     * @param query the user's query
     * @param context the orchestration context
     * @param template the pipeline template to use
     * @return the orchestration result
     */
    public OrchestrationResult orchestrate(
            String query,
            OrchestrationContext context,
            PipelineTemplate template) {
        validateInputs(query, context);
        Objects.requireNonNull(template, ERROR_NULL_TEMPLATE);

        Pipeline pipeline = pipelineFactory.getPipeline(template);
        log.debug("Orchestrating with {} pipeline for user: {}",
            template.name(), context.getIdentifier());

        return pipeline.execute(query, context);
    }

    /**
     * Orchestrate using a named pipeline from configuration.
     *
     * @param query the user's query
     * @param context the orchestration context
     * @param pipelineName the configured pipeline name
     * @return the orchestration result
     */
    public OrchestrationResult orchestrate(
            String query,
            OrchestrationContext context,
            String pipelineName) {
        validateInputs(query, context);
        Objects.requireNonNull(pipelineName, "pipelineName must not be null");

        Pipeline pipeline = pipelineFactory.getPipelineByName(pipelineName);
        log.debug("Orchestrating with named pipeline '{}' for user: {}",
            pipelineName, context.getIdentifier());

        return pipeline.execute(query, context);
    }

    // =========================================================================
    // Convenience Methods for Common Use Cases
    // =========================================================================

    /**
     * Fast security screening only.
     *
     * <p>Executes only: SecurityAnalysis -> AccessControl -> ComplianceCheck</p>
     * <p>Typical latency: &lt;50ms</p>
     *
     * @param query the content to screen
     * @param context the orchestration context
     * @return security screening result
     */
    public OrchestrationResult orchestrateSecurityOnly(String query, OrchestrationContext context) {
        return orchestrate(query, context, PipelineTemplate.SECURITY_ONLY);
    }

    /**
     * Intent extraction without action execution.
     *
     * <p>Extracts intent for analysis/classification without executing actions.</p>
     * <p>Useful for NLU analysis, intent analytics, query classification.</p>
     *
     * @param query the user's query
     * @param context the orchestration context
     * @return intent extraction result
     */
    public OrchestrationResult orchestrateIntentOnly(String query, OrchestrationContext context) {
        return orchestrate(query, context, PipelineTemplate.INTENT_ANALYSIS);
    }

    /**
     * Batch processing without history persistence.
     *
     * <p>Full pipeline except history recording.</p>
     * <p>Suitable for bulk document processing, ETL pipelines.</p>
     *
     * @param query the query to process
     * @param context the orchestration context
     * @return processing result
     */
    public OrchestrationResult orchestrateBatch(String query, OrchestrationContext context) {
        return orchestrate(query, context, PipelineTemplate.BATCH);
    }

    /**
     * Lightweight RAG for trusted/internal use.
     *
     * <p>Skips PII, compliance, suggestions, sanitization, history.</p>
     * <p>Faster but less secure - use only for internal/trusted contexts.</p>
     *
     * @param query the query to process
     * @param context the orchestration context
     * @return RAG result
     */
    public OrchestrationResult orchestrateLightweight(String query, OrchestrationContext context) {
        return orchestrate(query, context, PipelineTemplate.LIGHTWEIGHT);
    }

    /**
     * Compliance audit pipeline.
     *
     * <p>Security + PII detection + Compliance + Response sanitization.</p>
     * <p>Suitable for data privacy audits, sensitive data scanning.</p>
     *
     * @param query the content to audit
     * @param context the orchestration context
     * @return compliance audit result
     */
    public OrchestrationResult orchestrateComplianceAudit(String query, OrchestrationContext context) {
        return orchestrate(query, context, PipelineTemplate.COMPLIANCE_AUDIT);
    }

    // =========================================================================
    // Custom Pipeline Orchestration
    // =========================================================================

    /**
     * Orchestrate with a custom set of step orders.
     *
     * <p>Creates a one-off pipeline with the specified steps.</p>
     * <p>Use for dynamic/programmatic pipeline configuration.</p>
     *
     * @param query the query to process
     * @param context the orchestration context
     * @param stepOrders the step orders to include
     * @return orchestration result
     */
    public OrchestrationResult orchestrateCustom(
            String query,
            OrchestrationContext context,
            Set<Integer> stepOrders) {
        validateInputs(query, context);
        Objects.requireNonNull(stepOrders, "stepOrders must not be null");

        Pipeline pipeline = pipelineFactory.createCustomPipeline(stepOrders);
        log.debug("Orchestrating with custom pipeline {} for user: {}",
            stepOrders, context.getIdentifier());

        return pipeline.execute(query, context);
    }

    // =========================================================================
    // Private Methods
    // =========================================================================

    private void validateInputs(String query, OrchestrationContext context) {
        Objects.requireNonNull(query, ERROR_NULL_QUERY);
        Objects.requireNonNull(context, ERROR_NULL_CONTEXT);
    }
}
```

---

## Phase 4: Profile-Based Configuration

### 4.1 Development Profile

**Location:** `ai-infrastructure-core/src/main/resources/application-dev.yml`

```yaml
ai:
  orchestration:
    pipeline:
      default-template: FULL

      steps:
        # Disable expensive steps in dev
        pii-detection:
          enabled: false
        smart-suggestions:
          enabled: true
        history-persistence:
          enabled: true
          fail-silently: true

      # Dev-specific named pipelines
      named-pipelines:
        fast-dev: [10, 20, 50, 60, 70]
```

### 4.2 Production Profile

**Location:** `ai-infrastructure-core/src/main/resources/application-prod.yml`

```yaml
ai:
  orchestration:
    pipeline:
      default-template: FULL

      steps:
        security-analysis:
          enabled: true
        access-control:
          enabled: true
        pii-detection:
          enabled: true
        compliance-check:
          enabled: true
        intent-extraction:
          enabled: true
        intent-handling:
          enabled: true
        metadata-building:
          enabled: true
        smart-suggestions:
          enabled: true
        response-sanitization:
          enabled: true
        history-persistence:
          enabled: true
          fail-silently: true  # Don't fail orchestration if history fails

      named-pipelines:
        high-security: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100]
        internal-api: [10, 20, 50, 60, 70]
```

### 4.3 Batch Processing Profile

**Location:** `ai-infrastructure-core/src/main/resources/application-batch.yml`

```yaml
ai:
  orchestration:
    pipeline:
      # Default to batch template for batch profile
      default-template: BATCH

      steps:
        # Disable analytics recording for batch
        history-persistence:
          enabled: false
        # Disable real-time suggestions for batch
        smart-suggestions:
          enabled: false
```

---

## Phase 5: Testing

### 5.1 PipelineFactoryTest

**Location:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/pipeline/PipelineFactoryTest.java`

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineFactoryTest {

    @Mock private PipelineStep securityStep;
    @Mock private PipelineStep accessControlStep;
    @Mock private PipelineStep piiStep;
    @Mock private PipelineStep complianceStep;
    @Mock private PipelineStep intentExtractionStep;
    @Mock private PipelineStep intentHandlingStep;

    private PipelineProperties properties;
    private PipelineFactory factory;

    @BeforeEach
    void setUp() {
        // Configure mock steps
        when(securityStep.getStepName()).thenReturn("SecurityAnalysis");
        when(securityStep.getOrder()).thenReturn(10);

        when(accessControlStep.getStepName()).thenReturn("AccessControl");
        when(accessControlStep.getOrder()).thenReturn(20);

        when(piiStep.getStepName()).thenReturn("PIIDetection");
        when(piiStep.getOrder()).thenReturn(30);

        when(complianceStep.getStepName()).thenReturn("ComplianceCheck");
        when(complianceStep.getOrder()).thenReturn(40);

        when(intentExtractionStep.getStepName()).thenReturn("IntentExtraction");
        when(intentExtractionStep.getOrder()).thenReturn(50);

        when(intentHandlingStep.getStepName()).thenReturn("IntentHandling");
        when(intentHandlingStep.getOrder()).thenReturn(60);

        List<PipelineStep> allSteps = List.of(
            securityStep, accessControlStep, piiStep,
            complianceStep, intentExtractionStep, intentHandlingStep
        );

        properties = new PipelineProperties();
        factory = new PipelineFactory(allSteps, properties);
    }

    @Test
    void getPipeline_fullTemplate_returnsAllSteps() {
        Pipeline pipeline = factory.getPipeline(PipelineTemplate.FULL);

        assertThat(pipeline.getSteps()).hasSize(6);
        assertThat(pipeline.getSteps().get(0).getStepName()).isEqualTo("SecurityAnalysis");
        assertThat(pipeline.getSteps().get(5).getStepName()).isEqualTo("IntentHandling");
    }

    @Test
    void getPipeline_securityOnlyTemplate_returnsSecuritySteps() {
        Pipeline pipeline = factory.getPipeline(PipelineTemplate.SECURITY_ONLY);

        // SECURITY_ONLY includes steps 10, 20, 40
        assertThat(pipeline.getSteps()).hasSize(3);
        assertThat(pipeline.getSteps())
            .extracting(PipelineStep::getStepName)
            .containsExactly("SecurityAnalysis", "AccessControl", "ComplianceCheck");
    }

    @Test
    void getPipeline_intentAnalysisTemplate_returnsIntentSteps() {
        Pipeline pipeline = factory.getPipeline(PipelineTemplate.INTENT_ANALYSIS);

        // INTENT_ANALYSIS includes steps 10, 20, 30, 40, 50
        assertThat(pipeline.getSteps()).hasSize(5);
        assertThat(pipeline.getSteps())
            .extracting(PipelineStep::getStepName)
            .containsExactly(
                "SecurityAnalysis", "AccessControl", "PIIDetection",
                "ComplianceCheck", "IntentExtraction"
            );
    }

    @Test
    void getPipeline_disabledStep_excludesStep() {
        // Disable PII detection
        PipelineProperties.StepConfig piiConfig = new PipelineProperties.StepConfig();
        piiConfig.setEnabled(false);
        properties.getSteps().put("pii-detection", piiConfig);

        Pipeline pipeline = factory.getPipeline(PipelineTemplate.FULL);

        assertThat(pipeline.getSteps())
            .extracting(PipelineStep::getStepName)
            .doesNotContain("PIIDetection");
    }

    @Test
    void getPipeline_cachesPipelines() {
        Pipeline first = factory.getPipeline(PipelineTemplate.FULL);
        Pipeline second = factory.getPipeline(PipelineTemplate.FULL);

        assertThat(first).isSameAs(second);
    }

    @Test
    void clearCache_invalidatesCache() {
        Pipeline first = factory.getPipeline(PipelineTemplate.FULL);
        factory.clearCache();
        Pipeline second = factory.getPipeline(PipelineTemplate.FULL);

        assertThat(first).isNotSameAs(second);
    }

    @Test
    void createCustomPipeline_usesProvidedSteps() {
        Set<Integer> customSteps = Set.of(10, 50, 60);

        Pipeline pipeline = factory.createCustomPipeline(customSteps);

        assertThat(pipeline.getSteps()).hasSize(3);
        assertThat(pipeline.getSteps())
            .extracting(PipelineStep::getStepName)
            .containsExactly("SecurityAnalysis", "IntentExtraction", "IntentHandling");
    }

    @Test
    void getPipelineByName_existingName_returnsPipeline() {
        properties.getNamedPipelines().put("test-pipeline", Set.of(10, 20));

        Pipeline pipeline = factory.getPipelineByName("test-pipeline");

        assertThat(pipeline.getSteps()).hasSize(2);
    }

    @Test
    void getPipelineByName_unknownName_returnsDefault() {
        properties.setDefaultTemplate(PipelineTemplate.SECURITY_ONLY);

        Pipeline pipeline = factory.getPipelineByName("unknown");

        // Should return default (SECURITY_ONLY)
        assertThat(pipeline.getSteps()).hasSize(3);
    }

    @Test
    void getAvailableSteps_returnsAllSteps() {
        Map<String, Integer> available = factory.getAvailableSteps();

        assertThat(available)
            .containsEntry("SecurityAnalysis", 10)
            .containsEntry("AccessControl", 20)
            .containsEntry("PIIDetection", 30)
            .containsEntry("ComplianceCheck", 40)
            .containsEntry("IntentExtraction", 50)
            .containsEntry("IntentHandling", 60);
    }
}
```

### 5.2 OrchestratorTest

**Location:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/OrchestratorTest.java`

```java
package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.intent.orchestration.pipeline.Pipeline;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineFactory;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorTest {

    @Mock private PipelineFactory pipelineFactory;
    @Mock private Pipeline fullPipeline;
    @Mock private Pipeline securityPipeline;
    @Mock private Pipeline batchPipeline;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new Orchestrator(pipelineFactory);

        when(pipelineFactory.getPipeline(PipelineTemplate.FULL)).thenReturn(fullPipeline);
        when(pipelineFactory.getPipeline(PipelineTemplate.SECURITY_ONLY)).thenReturn(securityPipeline);
        when(pipelineFactory.getPipeline(PipelineTemplate.BATCH)).thenReturn(batchPipeline);
    }

    @Test
    void orchestrate_default_usesFullPipeline() {
        OrchestrationContext context = OrchestrationContext.forUser("user-1");
        OrchestrationResult expected = OrchestrationResult.success("test");
        when(fullPipeline.execute(anyString(), any())).thenReturn(expected);

        OrchestrationResult result = orchestrator.orchestrate("test query", context);

        assertThat(result).isEqualTo(expected);
        verify(pipelineFactory).getPipeline(PipelineTemplate.FULL);
    }

    @Test
    void orchestrate_withTemplate_usesSpecifiedTemplate() {
        OrchestrationContext context = OrchestrationContext.forUser("user-1");
        OrchestrationResult expected = OrchestrationResult.success("security");
        when(securityPipeline.execute(anyString(), any())).thenReturn(expected);

        OrchestrationResult result = orchestrator.orchestrate(
            "test query", context, PipelineTemplate.SECURITY_ONLY
        );

        assertThat(result).isEqualTo(expected);
        verify(pipelineFactory).getPipeline(PipelineTemplate.SECURITY_ONLY);
    }

    @Test
    void orchestrateSecurityOnly_usesSecurityTemplate() {
        OrchestrationContext context = OrchestrationContext.forUser("user-1");
        when(securityPipeline.execute(anyString(), any()))
            .thenReturn(OrchestrationResult.success("ok"));

        orchestrator.orchestrateSecurityOnly("test", context);

        verify(pipelineFactory).getPipeline(PipelineTemplate.SECURITY_ONLY);
    }

    @Test
    void orchestrateBatch_usesBatchTemplate() {
        OrchestrationContext context = OrchestrationContext.forUser("user-1");
        when(batchPipeline.execute(anyString(), any()))
            .thenReturn(OrchestrationResult.success("ok"));

        orchestrator.orchestrateBatch("test", context);

        verify(pipelineFactory).getPipeline(PipelineTemplate.BATCH);
    }

    @Test
    void orchestrateCustom_createsCustomPipeline() {
        OrchestrationContext context = OrchestrationContext.forUser("user-1");
        Pipeline customPipeline = mock(Pipeline.class);
        Set<Integer> customSteps = Set.of(10, 20, 50);

        when(pipelineFactory.createCustomPipeline(customSteps)).thenReturn(customPipeline);
        when(customPipeline.execute(anyString(), any()))
            .thenReturn(OrchestrationResult.success("custom"));

        orchestrator.orchestrateCustom("test", context, customSteps);

        verify(pipelineFactory).createCustomPipeline(customSteps);
        verify(customPipeline).execute("test", context);
    }

    @Test
    void orchestrate_nullQuery_throwsException() {
        OrchestrationContext context = OrchestrationContext.forUser("user-1");

        assertThatThrownBy(() -> orchestrator.orchestrate(null, context))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("query");
    }

    @Test
    void orchestrate_nullContext_throwsException() {
        assertThatThrownBy(() -> orchestrator.orchestrate("test", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("context");
    }
}
```

### 5.3 Integration Test

**Location:** `integration-tests/src/test/java/com/ai/infrastructure/orchestration/PipelineTemplatesIntegrationTest.java`

```java
package com.ai.infrastructure.orchestration;

import com.ai.infrastructure.intent.orchestration.ConfigurableOrchestrator;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineFactory;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PipelineTemplatesIntegrationTest {

    @Autowired
    private Orchestrator orchestrator;

    @Autowired
    private PipelineFactory pipelineFactory;

    @Test
    void fullPipeline_processesAllSteps() {
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("test-user")
            .sessionId("test-session")
            .build();

        // Default orchestrate() uses FULL pipeline
        OrchestrationResult result = orchestrator.orchestrate(
            "What is the refund policy?",
            context
        );

        assertThat(result).isNotNull();
        // Full pipeline should process all steps
    }

    @Test
    void securityOnlyPipeline_fastExecution() {
        OrchestrationContext context = OrchestrationContext.forUser("test-user");

        long start = System.currentTimeMillis();
        OrchestrationResult result = orchestrator.orchestrateSecurityOnly(
            "Test security screening",
            context
        );
        long duration = System.currentTimeMillis() - start;

        assertThat(result).isNotNull();
        // Security-only should be fast (no LLM calls)
        assertThat(duration).isLessThan(200); // Adjust threshold as needed
    }

    @Test
    void batchPipeline_skipsHistoryPersistence() {
        OrchestrationContext context = OrchestrationContext.forUser("batch-user");

        OrchestrationResult result = orchestrator.orchestrateBatch(
            "Process this document",
            context
        );

        assertThat(result).isNotNull();
        // Verify history was not persisted (check DB or mock)
    }

    @Test
    void pipelineFactory_createsDifferentPipelines() {
        var fullPipeline = pipelineFactory.getPipeline(PipelineTemplate.FULL);
        var securityPipeline = pipelineFactory.getPipeline(PipelineTemplate.SECURITY_ONLY);
        var intentPipeline = pipelineFactory.getPipeline(PipelineTemplate.INTENT_ANALYSIS);

        assertThat(fullPipeline.getStepCount()).isEqualTo(10);
        assertThat(securityPipeline.getStepCount()).isEqualTo(3);
        assertThat(intentPipeline.getStepCount()).isEqualTo(5);
    }

    @Test
    void customPipeline_usesSpecifiedSteps() {
        OrchestrationContext context = OrchestrationContext.forUser("test-user");
        Set<Integer> customSteps = Set.of(10, 20, 50, 60); // Security + Intent only

        OrchestrationResult result = orchestrator.orchestrateCustom(
            "Custom pipeline test",
            context,
            customSteps
        );

        assertThat(result).isNotNull();
    }
}
```

---

## Phase 6: Refactoring Tasks

### 6.1 Replace RAGOrchestrator with Orchestrator

**Steps:**
1. Create new `Orchestrator.java` with the code from Phase 3
2. Update all imports from `RAGOrchestrator` to `Orchestrator`
3. Delete `RAGOrchestrator.java`
4. Update test classes accordingly

**Find & Replace:**
```
RAGOrchestrator -> Orchestrator
```

**Files to Update:**
- All files importing `RAGOrchestrator`
- All test files using `RAGOrchestrator`
- Any configuration referencing the bean name

### 6.2 Usage Examples

```java
@Autowired
private Orchestrator orchestrator;

public void process(String query, OrchestrationContext context) {
    // Default: FULL pipeline (all 10 steps)
    OrchestrationResult result = orchestrator.orchestrate(query, context);

    // Specific template
    OrchestrationResult batch = orchestrator.orchestrate(query, context, PipelineTemplate.BATCH);

    // Convenience methods
    OrchestrationResult security = orchestrator.orchestrateSecurityOnly(query, context);
}
```

### 6.3 New Configuration

```yaml
ai:
  orchestration:
    pipeline:
      default-template: FULL  # All steps by default
      steps:
        pii-detection:
          enabled: true
        history-persistence:
          enabled: true
          fail-silently: true
```

---

## Implementation Checklist

### Phase 1: Core Infrastructure
- [ ] Create `PipelineTemplate` enum with all templates
- [ ] Create `PipelineProperties` configuration class
- [ ] Add configuration schema to application.yml
- [ ] Write unit tests for `PipelineTemplate`

### Phase 2: Pipeline Factory
- [ ] Create `PipelineFactory` component
- [ ] Create `ConfigurablePipeline` class
- [ ] Implement caching for pipeline instances
- [ ] Write unit tests for `PipelineFactory`

### Phase 3: Orchestrator (Replace RAGOrchestrator)
- [ ] Create new `Orchestrator` class
- [ ] Implement overloaded `orchestrate()` methods
- [ ] Add convenience methods (securityOnly, batch, etc.)
- [ ] Add custom pipeline support
- [ ] Delete old `RAGOrchestrator` class
- [ ] Update all imports/references
- [ ] Write unit tests for `Orchestrator`

### Phase 4: Profile Configuration
- [ ] Update `application-dev.yml` with pipeline config
- [ ] Update `application-prod.yml` with pipeline config
- [ ] Create `application-batch.yml` profile
- [ ] Test profile switching

### Phase 5: Testing
- [ ] Unit tests for all new classes
- [ ] Integration tests for pipeline templates
- [ ] Performance tests for different templates

### Phase 6: Documentation
- [ ] Update README with new usage examples
- [ ] Document configuration options
- [ ] Add JavaDoc to all public APIs

---

## Success Criteria

- [ ] New `Orchestrator` works with all templates
- [ ] Default `orchestrate()` uses FULL pipeline (all 10 steps)
- [ ] Security-only pipeline executes in <100ms
- [ ] Configuration changes take effect without code changes
- [ ] Named pipelines can be defined via configuration
- [ ] All tests updated and passing
- [ ] Documentation complete and clear

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Orchestrator                             │
│  ┌─────────────┬─────────────┬─────────────┬─────────────────┐  │
│  │ orchestrate │ orchestrate │ orchestrate │ orchestrate     │  │
│  │   (FULL)    │ (template)  │  (named)    │    Custom       │  │
│  └──────┬──────┴──────┬──────┴──────┬──────┴───────┬─────────┘  │
│         │             │             │              │             │
│         ▼             ▼             ▼              ▼             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    PipelineFactory                        │   │
│  │  ┌─────────────────────────────────────────────────────┐ │   │
│  │  │ Template Cache: FULL, SECURITY_ONLY, BATCH, etc.    │ │   │
│  │  └─────────────────────────────────────────────────────┘ │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │                                   │
│                              ▼                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  ConfigurablePipeline                     │   │
│  │  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐  ┌─────┐   │   │
│  │  │ S10 │→ │ S20 │→ │ S30 │→ │ S40 │→ │ S50 │→ │ S60 │   │   │
│  │  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘  └─────┘   │   │
│  │  (Steps filtered based on template)                       │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

Templates:
┌────────────────┬─────────────────────────────────────────────┐
│ FULL           │ S10 → S20 → S30 → S40 → S50 → S60 → ... S100│
│ SECURITY_ONLY  │ S10 → S20 → S40                             │
│ INTENT_ANALYSIS│ S10 → S20 → S30 → S40 → S50                 │
│ BATCH          │ S10 → S20 → S30 → S40 → S50 → ... S90       │
│ LIGHTWEIGHT    │ S10 → S20 → S50 → S60 → S70                 │
└────────────────┴─────────────────────────────────────────────┘
```

---

## Benefits Summary

| Benefit | Description |
|---------|-------------|
| **No Module Separation** | Avoids complex refactoring and dependency management |
| **Runtime Flexibility** | Switch pipelines based on use case at runtime |
| **Configuration-Driven** | Enable/disable steps via properties |
| **Performance Optimization** | Use lightweight pipelines for fast operations |
| **Cost Optimization** | Skip expensive LLM calls when not needed |
| **Testability** | Test individual pipeline configurations in isolation |
| **Cleaner API** | Simple `Orchestrator` class with overloaded methods |
| **Profile Support** | Different defaults for dev/prod/batch environments |

---

**Document Version:** 1.0
**Created:** 2026-01-09
**Status:** Ready for Implementation
**Owner:** AI Infrastructure Team
