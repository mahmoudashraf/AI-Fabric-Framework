# Orchestrator Pipeline Refactoring

## Overview

This document describes the refactoring of the `RAGOrchestrator` from a monolithic implementation to a composable pipeline architecture.

## Problem Statement

### Before Refactoring

The original `RAGOrchestrator` was a monolithic class with:

- **~600 lines of code** in a single file
- **12 injected dependencies** directly managed
- **Mixed concerns**: Security, access control, PII, compliance, intent handling, RAG, and history all in one class
- **Difficult to test**: Required mocking all 12 dependencies for any test
- **Hard to extend**: Adding new functionality required modifying the monolith
- **Poor observability**: No clear step boundaries for metrics/logging

```java
// Before: Monolithic constructor
public RAGOrchestrator(
    IntentQueryExtractor intentQueryExtractor,
    ActionHandlerRegistry actionHandlerRegistry,
    RAGService ragService,
    ResponseSanitizer responseSanitizer,
    IntentHistoryService intentHistoryService,
    SmartSuggestionsProperties smartSuggestionsProperties,
    PIIDetectionService piiDetectionService,
    PIIDetectionProperties piiDetectionProperties,
    AISecurityService securityService,
    AIAccessControlService accessControlService,
    AIComplianceService complianceService,
    Clock clock
) { ... }
```

## Solution: Pipeline Pattern

### Design Goals

1. **Single Responsibility**: Each step handles one concern
2. **Composable**: Steps can be added, removed, or reordered
3. **Testable**: Each step can be unit tested in isolation
4. **Extensible**: New steps are added by implementing `PipelineStep`
5. **Observable**: Clear boundaries for metrics and logging
6. **Fail-Fast**: Early termination on security/compliance failures

### After Refactoring

The `RAGOrchestrator` now delegates to a `Pipeline`:

```java
// After: Clean, focused orchestrator
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    private final Pipeline pipeline;
    
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        return pipeline.execute(query, context);
    }
}
```

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       RAGOrchestrator                            │
│                   (Single dependency: Pipeline)                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                 DefaultOrchestrationPipeline                     │
│                                                                  │
│   Steps (sorted by order):                                       │
│   ┌────────────────────────────────────────────────────────┐    │
│   │ SecurityAnalysisStep (10)                               │    │
│   │ AccessControlStep (20)                                  │    │
│   │ PIIDetectionStep (30)                                   │    │
│   │ ComplianceCheckStep (40)                                │    │
│   │ IntentExtractionStep (50)                               │    │
│   │ IntentHandlingStep (60)                                 │    │
│   │ MetadataBuildingStep (70)                               │    │
│   │ SmartSuggestionsStep (80)                               │    │
│   │ ResponseSanitizationStep (90)                           │    │
│   │ HistoryPersistenceStep (100)                            │    │
│   └────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

## Core Interfaces

### PipelineStep

```java
public interface PipelineStep {
    /**
     * Process the context and return updated context.
     * May terminate pipeline via context.terminate(result).
     */
    PipelineContext process(PipelineContext context);
    
    /** Step identifier for logging/metrics */
    String getStepName();
    
    /** Execution order (lower = earlier) */
    default int getOrder() { return 100; }
    
    /** Skip if pipeline already terminated */
    default boolean shouldSkip(PipelineContext context) {
        return context != null && context.isShouldTerminate();
    }
}
```

### PipelineContext

Immutable data carrier flowing through steps:

```java
@Data
@Builder(toBuilder = true)
public class PipelineContext {
    // Input
    private final String originalQuery;
    private final OrchestrationContext orchestrationContext;
    private final String requestId;
    
    // Accumulated state
    private final String processedQuery;
    private final List<String> detectedPiiTypes;
    private final MultiIntentResponse intentResponse;
    private final OrchestrationResult intentResult;
    private final Map<String, Object> metadata;
    private final Map<String, Object> smartSuggestion;
    private final Map<String, Object> sanitizedPayload;
    
    // Control flow
    private final boolean shouldTerminate;
    
    /** Factory method from query and context */
    public static PipelineContext from(String query, OrchestrationContext context);
    
    /** Terminate pipeline with result */
    public PipelineContext terminate(OrchestrationResult result);
}
```

### Pipeline

```java
public interface Pipeline {
    /** Execute all steps and return final result */
    OrchestrationResult execute(String query, OrchestrationContext context);
    
    /** Get configured steps (unmodifiable) */
    List<PipelineStep> getSteps();
}
```

## Implementation Details

### DefaultOrchestrationPipeline

```java
@Component
@RequiredArgsConstructor
public class DefaultOrchestrationPipeline implements Pipeline {
    
    private final List<PipelineStep> steps;
    
    @PostConstruct
    void init() {
        // Sort steps by order
        steps.sort(Comparator.comparingInt(PipelineStep::getOrder));
    }
    
    @Override
    public OrchestrationResult execute(String query, OrchestrationContext context) {
        PipelineContext pipelineContext = PipelineContext.from(query, context);
        
        for (PipelineStep step : steps) {
            if (step.shouldSkip(pipelineContext)) {
                continue;  // Skip if terminated
            }
            
            pipelineContext = step.process(pipelineContext);
            
            if (pipelineContext.isShouldTerminate()) {
                return pipelineContext.getIntentResult();
            }
        }
        
        return pipelineContext.getIntentResult();
    }
}
```

### Step Implementation Example

```java
@Component
@RequiredArgsConstructor
public class SecurityAnalysisStep implements PipelineStep {
    
    private static final String STEP_NAME = "SecurityAnalysis";
    private static final int STEP_ORDER = 10;
    
    private final AISecurityService securityService;
    
    @Override
    public String getStepName() { return STEP_NAME; }
    
    @Override
    public int getOrder() { return STEP_ORDER; }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        AISecurityRequest request = buildSecurityRequest(context);
        AISecurityResponse response = securityService.analyzeRequest(request);
        
        if (response.isShouldBlock()) {
            return context.terminate(OrchestrationResult.builder()
                .type(OrchestrationResultType.SECURITY_BLOCKED)
                .success(false)
                .message("Request blocked by security analysis")
                .build());
        }
        
        return context;  // Continue to next step
    }
}
```

## Benefits Achieved

### Code Metrics

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| RAGOrchestrator LOC | ~600 | ~110 | **82% reduction** |
| Dependencies | 12 | 1 (Pipeline) | **92% reduction** |
| Cyclomatic complexity | High | Low per step | **Significant** |

### Qualitative Benefits

1. **Testability**: Each step tested in isolation
2. **Maintainability**: Changes isolated to specific steps
3. **Extensibility**: New steps added without touching existing code
4. **Observability**: Step-level timing, logging, metrics
5. **Flexibility**: Steps can be conditionally enabled/disabled

## Migration Notes

### Backward Compatibility

The public API of `RAGOrchestrator` is **unchanged**:

```java
// Still works exactly the same
OrchestrationResult result = orchestrator.orchestrate(query, context);
```

### Deprecated Method

The legacy signature is preserved but deprecated:

```java
@Deprecated(forRemoval = true)
public OrchestrationResult orchestrate(String query, String userId) {
    return orchestrate(query, OrchestrationContext.forUser(userId));
}
```

### Test Updates

Existing tests work unchanged. New tests can leverage step isolation:

```java
// Test individual step
@Test
void securityStep_shouldBlock_whenThreatDetected() {
    when(securityService.analyzeRequest(any()))
        .thenReturn(AISecurityResponse.builder().shouldBlock(true).build());
    
    PipelineContext result = securityStep.process(context);
    
    assertThat(result.isShouldTerminate()).isTrue();
}
```

## Future Enhancements

1. **Async Steps**: Parallel execution of independent steps
2. **Step Metrics**: Automatic timing/counting per step
3. **Conditional Steps**: Configuration-driven step enablement
4. **Step Hooks**: Pre/post processing hooks for cross-cutting concerns

## References

- [Pipeline Architecture](PIPELINE_ARCHITECTURE.md)
- [Pipeline Steps Reference](PIPELINE_STEPS_REFERENCE.md)
- [RAG Extraction Assessment](RAG_EXTRACTION_ASSESSMENT.md)
