# RAG Orchestrator Workflow Refactoring

## Problem Statement

The current `RAGOrchestrator` class is a **600+ line monolith** with:
- 14 dependencies injected via constructor
- 10+ distinct processing steps in a single method
- Mixed responsibilities (security, PII, compliance, intent, RAG, sanitization)
- Difficult to test individual steps in isolation
- Hard to extend or modify individual steps

## Current Flow Analysis

```
┌─────────────────────────────────────────────────────────────────┐
│                    RAGOrchestrator.orchestrate()                │
│                         (600+ lines)                            │
├─────────────────────────────────────────────────────────────────┤
│ 1. Security Analysis          → AISecurityService               │
│ 2. Access Control Check       → AIAccessControlService          │
│ 3. PII Detection/Redaction    → PIIDetectionService             │
│ 4. Compliance Validation      → AIComplianceService             │
│ 5. Intent Extraction          → IntentQueryExtractor            │
│ 6. Intent Handling            → handleSingleIntent/Compound     │
│ 7. Metadata Building          → (inline logic)                  │
│ 8. Smart Suggestions          → RAGService                      │
│ 9. Response Sanitization      → ResponseSanitizer               │
│ 10. History Persistence       → IntentHistoryService            │
└─────────────────────────────────────────────────────────────────┘
```

---

## Proposed Solution: Pipeline Pattern

### Design Goals

1. **Single Responsibility**: Each step does one thing
2. **Composable**: Steps can be added, removed, or reordered
3. **Testable**: Each step can be unit tested independently
4. **Extensible**: New steps can be added without modifying existing code
5. **Observable**: Easy to add logging, metrics, tracing per step
6. **Fail-Fast**: Early termination on errors with clear failure points

### Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                        OrchestrationPipeline                          │
├──────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐               │
│  │  Security   │───▶│   Access    │───▶│     PII     │               │
│  │    Step     │    │    Step     │    │    Step     │               │
│  └─────────────┘    └─────────────┘    └─────────────┘               │
│         │                  │                  │                       │
│         ▼                  ▼                  ▼                       │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐               │
│  │ Compliance  │───▶│   Intent    │───▶│   Intent    │               │
│  │    Step     │    │ Extraction  │    │  Handling   │               │
│  └─────────────┘    └─────────────┘    └─────────────┘               │
│         │                  │                  │                       │
│         ▼                  ▼                  ▼                       │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐               │
│  │  Metadata   │───▶│   Smart     │───▶│  Response   │               │
│  │  Building   │    │ Suggestions │    │ Sanitization│               │
│  └─────────────┘    └─────────────┘    └─────────────┘               │
│         │                                    │                       │
│         ▼                                    ▼                       │
│  ┌─────────────┐                      ┌─────────────┐               │
│  │   History   │                      │   Result    │               │
│  │ Persistence │─────────────────────▶│  Assembly   │               │
│  └─────────────┘                      └─────────────┘               │
│                                                                       │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Implementation Design

### Core Interfaces

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

/**
 * Represents the shared context passed through all pipeline steps.
 * Immutable carrier of data accumulated during orchestration.
 */
@Data
@Builder(toBuilder = true)
public class PipelineContext {
    
    // Input
    private final String originalQuery;
    private final OrchestrationContext orchestrationContext;
    private final LocalDateTime requestTimestamp;
    private final String requestId;
    
    // Accumulated state (each step adds to this)
    private final String processedQuery;
    private final List<String> detectedPiiTypes;
    private final MultiIntentResponse intentResponse;
    private final OrchestrationResult intentResult;
    private final Map<String, Object> metadata;
    private final Map<String, Object> smartSuggestion;
    private final Map<String, Object> sanitizedPayload;
    
    // Control flow
    private final boolean shouldTerminate;
    private final OrchestrationResult earlyTerminationResult;
    
    /**
     * Create initial context from request
     */
    public static PipelineContext from(String query, OrchestrationContext context) {
        return PipelineContext.builder()
            .originalQuery(query)
            .orchestrationContext(context)
            .requestId(context.getOrGenerateRequestId())
            .requestTimestamp(LocalDateTime.now())
            .processedQuery(query)
            .detectedPiiTypes(new ArrayList<>())
            .metadata(new LinkedHashMap<>())
            .shouldTerminate(false)
            .build();
    }
    
    /**
     * Create terminating context (early exit)
     */
    public PipelineContext terminate(OrchestrationResult result) {
        return this.toBuilder()
            .shouldTerminate(true)
            .earlyTerminationResult(result)
            .build();
    }
}
```

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

/**
 * A single step in the orchestration pipeline.
 * Each step receives context, processes it, and returns updated context.
 */
public interface PipelineStep {
    
    /**
     * Process this step of the pipeline
     * 
     * @param context the current pipeline context
     * @return updated context (may include termination signal)
     */
    PipelineContext process(PipelineContext context);
    
    /**
     * Get the name of this step (for logging/metrics)
     */
    String getStepName();
    
    /**
     * Get the order of this step (lower = earlier)
     */
    default int getOrder() {
        return 100; // Default middle priority
    }
    
    /**
     * Whether this step should be skipped based on context
     */
    default boolean shouldSkip(PipelineContext context) {
        return context.isShouldTerminate();
    }
}
```

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

/**
 * Executes a series of pipeline steps in order.
 */
public interface Pipeline {
    
    /**
     * Execute all steps in the pipeline
     */
    OrchestrationResult execute(String query, OrchestrationContext context);
    
    /**
     * Get all registered steps
     */
    List<PipelineStep> getSteps();
}
```

---

### Step Implementations

#### Step 1: Security Analysis Step

```java
package com.ai.infrastructure.intent.orchestration.pipeline.steps;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAnalysisStep implements PipelineStep {
    
    private final AISecurityService securityService;
    
    @Override
    public String getStepName() {
        return "SecurityAnalysis";
    }
    
    @Override
    public int getOrder() {
        return 10;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Executing security analysis for request {}", context.getRequestId());
        
        AISecurityRequest request = AISecurityRequest.builder()
            .requestId(context.getRequestId())
            .userId(context.getOrchestrationContext().getUserId())
            .sessionId(context.getOrchestrationContext().getSessionId())
            .content(context.getOriginalQuery())
            .operationType("INTENT_QUERY")
            .timestamp(context.getRequestTimestamp())
            .ipAddress(context.getOrchestrationContext().getIpAddress())
            .userAgent(context.getOrchestrationContext().getUserAgent())
            .build();
        
        AISecurityResponse response = securityService.analyzeRequest(request);
        
        if (Boolean.TRUE.equals(response.getShouldBlock())) {
            log.warn("Security blocked request {}", context.getRequestId());
            return context.terminate(
                OrchestrationResult.error("Request blocked by security controls.")
            );
        }
        
        return context; // Pass through unchanged
    }
}
```

#### Step 2: Access Control Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessControlStep implements PipelineStep {
    
    private final AIAccessControlService accessControlService;
    
    @Override
    public String getStepName() {
        return "AccessControl";
    }
    
    @Override
    public int getOrder() {
        return 20;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Checking access control for request {}", context.getRequestId());
        
        AIAccessControlRequest request = AIAccessControlRequest.builder()
            .requestId(context.getRequestId())
            .userId(context.getOrchestrationContext().getUserId())
            .sessionId(context.getOrchestrationContext().getSessionId())
            .resourceId("rag:intent")
            .operationType("READ")
            .context(context.getOriginalQuery())
            .timestamp(context.getRequestTimestamp())
            .build();
        
        AIAccessControlResponse response = accessControlService.checkAccess(request);
        
        if (!Boolean.TRUE.equals(response.getAccessGranted())) {
            log.warn("Access denied for request {}", context.getRequestId());
            return context.terminate(
                OrchestrationResult.error("Access denied by policy.")
            );
        }
        
        return context;
    }
}
```

#### Step 3: PII Detection Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class PIIDetectionStep implements PipelineStep {
    
    private final PIIDetectionService piiDetectionService;
    private final PIIDetectionProperties piiDetectionProperties;
    
    @Override
    public String getStepName() {
        return "PIIDetection";
    }
    
    @Override
    public int getOrder() {
        return 30;
    }
    
    @Override
    public boolean shouldSkip(PipelineContext context) {
        if (context.isShouldTerminate()) {
            return true;
        }
        
        PIIDetectionDirection direction = piiDetectionProperties.getDetectionDirection();
        return !piiDetectionProperties.isEnabled() ||
               (direction != PIIDetectionDirection.INPUT && 
                direction != PIIDetectionDirection.INPUT_OUTPUT);
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Detecting PII in query for request {}", context.getRequestId());
        
        PIIDetectionResult result = piiDetectionService.analyze(context.getOriginalQuery());
        
        List<String> detectedTypes = new ArrayList<>();
        if (result.isPiiDetected()) {
            detectedTypes = result.getDetections().stream()
                .map(PIIDetection::getType)
                .filter(t -> t != null && !t.isBlank())
                .distinct()
                .collect(Collectors.toList());
            
            log.info("PII detected in request {} - types: {}", 
                context.getRequestId(), detectedTypes);
        }
        
        return context.toBuilder()
            .processedQuery(result.getProcessedQuery())
            .detectedPiiTypes(detectedTypes)
            .build();
    }
}
```

#### Step 4: Compliance Check Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplianceCheckStep implements PipelineStep {
    
    private final AIComplianceService complianceService;
    
    @Override
    public String getStepName() {
        return "ComplianceCheck";
    }
    
    @Override
    public int getOrder() {
        return 40;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Checking compliance for request {}", context.getRequestId());
        
        AIComplianceRequest request = AIComplianceRequest.builder()
            .requestId(context.getRequestId())
            .userId(context.getOrchestrationContext().getUserId())
            .content(context.getProcessedQuery())
            .timestamp(context.getRequestTimestamp())
            .build();
        
        AIComplianceResponse response = complianceService.checkCompliance(request);
        
        if (Boolean.FALSE.equals(response.getOverallCompliant())) {
            log.warn("Compliance check failed for request {}", context.getRequestId());
            return context.terminate(
                OrchestrationResult.error("Request failed compliance validation.")
            );
        }
        
        return context;
    }
}
```

#### Step 5: Intent Extraction Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentExtractionStep implements PipelineStep {
    
    private final IntentQueryExtractor intentQueryExtractor;
    
    @Override
    public String getStepName() {
        return "IntentExtraction";
    }
    
    @Override
    public int getOrder() {
        return 50;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Extracting intent for request {}", context.getRequestId());
        
        MultiIntentResponse intentResponse = intentQueryExtractor.extract(
            context.getProcessedQuery(), 
            context.getOrchestrationContext()
        );
        
        if (!intentResponse.hasIntents()) {
            log.warn("No intents extracted for request {}", context.getRequestId());
            return context.terminate(
                OrchestrationResult.error("Unable to determine user intent.")
            );
        }
        
        return context.toBuilder()
            .intentResponse(intentResponse)
            .build();
    }
}
```

#### Step 6: Intent Handling Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class IntentHandlingStep implements PipelineStep {
    
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final RAGService ragService;
    
    @Override
    public String getStepName() {
        return "IntentHandling";
    }
    
    @Override
    public int getOrder() {
        return 60;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Handling intent for request {}", context.getRequestId());
        
        MultiIntentResponse intentResponse = context.getIntentResponse();
        OrchestrationResult result;
        
        if (intentResponse.isCompound() || intentResponse.getIntents().size() > 1) {
            result = handleCompoundIntents(intentResponse, context.getOrchestrationContext());
        } else {
            result = handleSingleIntent(
                intentResponse.getIntents().get(0), 
                context.getOrchestrationContext()
            );
        }
        
        if (result == null) {
            log.error("Intent handling returned null for request {}", context.getRequestId());
            return context.terminate(
                OrchestrationResult.error("Internal error: intent handling failed")
            );
        }
        
        return context.toBuilder()
            .intentResult(result)
            .build();
    }
    
    private OrchestrationResult handleSingleIntent(Intent intent, OrchestrationContext ctx) {
        return switch (intent.getType()) {
            case ACTION -> handleAction(intent, ctx);
            case INFORMATION -> handleInformation(intent, ctx);
            case OUT_OF_SCOPE -> handleOutOfScope(intent);
            case COMPOUND -> handleOutOfScope(intent); // Delegate to compound handler
            default -> OrchestrationResult.error("Unknown intent type: " + intent.getType());
        };
    }
    
    // ... individual intent handlers (can be further extracted to separate classes)
}
```

#### Step 7: Metadata Building Step

```java
@Slf4j
@Component
public class MetadataBuildingStep implements PipelineStep {
    
    @Override
    public String getStepName() {
        return "MetadataBuilding";
    }
    
    @Override
    public int getOrder() {
        return 70;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", context.getRequestId());
        metadata.put("sessionId", context.getOrchestrationContext().getSessionId());
        metadata.put("intentsCount", context.getIntentResponse().getIntents().size());
        metadata.put("compound", context.getIntentResponse().isCompound());
        metadata.put("authenticated", context.getOrchestrationContext().isAuthenticated());
        
        if (!CollectionUtils.isEmpty(context.getIntentResponse().getMetadata())) {
            metadata.put("intentMetadata", context.getIntentResponse().getMetadata());
        }
        
        // Update the intent result with metadata
        context.getIntentResult().setMetadata(Collections.unmodifiableMap(metadata));
        
        return context.toBuilder()
            .metadata(metadata)
            .build();
    }
}
```

#### Step 8: Smart Suggestions Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class SmartSuggestionsStep implements PipelineStep {
    
    private final SmartSuggestionsProperties smartSuggestionsProperties;
    private final RAGService ragService;
    
    @Override
    public String getStepName() {
        return "SmartSuggestions";
    }
    
    @Override
    public int getOrder() {
        return 80;
    }
    
    @Override
    public boolean shouldSkip(PipelineContext context) {
        return context.isShouldTerminate() || 
               !smartSuggestionsProperties.isEnabled() ||
               context.getIntentResult() == null ||
               CollectionUtils.isEmpty(context.getIntentResult().getNextSteps());
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Generating smart suggestions for request {}", context.getRequestId());
        
        List<NextStepRecommendation> nextSteps = context.getIntentResult().getNextSteps();
        
        NextStepRecommendation candidate = nextSteps.stream()
            .filter(Objects::nonNull)
            .filter(step -> step.getConfidence() != null && 
                           step.getConfidence() >= smartSuggestionsProperties.getMinConfidence())
            .max(Comparator.comparingDouble(NextStepRecommendation::getConfidence))
            .orElse(null);
        
        if (candidate == null) {
            return context;
        }
        
        try {
            Map<String, Object> suggestion = generateSuggestion(candidate, context);
            context.getIntentResult().setSmartSuggestion(Collections.unmodifiableMap(suggestion));
            
            return context.toBuilder()
                .smartSuggestion(suggestion)
                .build();
        } catch (Exception ex) {
            log.warn("Failed to generate smart suggestion: {}", ex.getMessage());
            return context;
        }
    }
    
    private Map<String, Object> generateSuggestion(NextStepRecommendation candidate, 
                                                    PipelineContext context) {
        // ... suggestion generation logic
    }
}
```

#### Step 9: Response Sanitization Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseSanitizationStep implements PipelineStep {
    
    private final ResponseSanitizer responseSanitizer;
    private final PIIDetectionProperties piiDetectionProperties;
    
    @Override
    public String getStepName() {
        return "ResponseSanitization";
    }
    
    @Override
    public int getOrder() {
        return 90;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Sanitizing response for request {}", context.getRequestId());
        
        String identifier = context.getOrchestrationContext().getIdentifier();
        Map<String, Object> sanitizedPayload = responseSanitizer.sanitize(
            context.getIntentResult(), 
            identifier
        );
        
        // Merge PII detection info
        sanitizedPayload = mergePIIDetectionInfo(sanitizedPayload, context);
        
        context.getIntentResult().setSanitizedPayload(sanitizedPayload);
        
        return context.toBuilder()
            .sanitizedPayload(sanitizedPayload)
            .build();
    }
    
    private Map<String, Object> mergePIIDetectionInfo(Map<String, Object> payload, 
                                                       PipelineContext context) {
        // ... merge logic for PII types
    }
}
```

#### Step 10: History Persistence Step

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class HistoryPersistenceStep implements PipelineStep {
    
    private final IntentHistoryService intentHistoryService;
    
    @Override
    public String getStepName() {
        return "HistoryPersistence";
    }
    
    @Override
    public int getOrder() {
        return 100;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Persisting history for request {}", context.getRequestId());
        
        try {
            intentHistoryService.recordIntent(
                context.getOrchestrationContext().getIdentifier(),
                context.getOrchestrationContext().getSessionId(),
                context.getOriginalQuery(),
                context.getIntentResponse(),
                context.getIntentResult()
            );
        } catch (Exception ex) {
            log.warn("Failed to persist intent history: {}", ex.getMessage());
            // Non-fatal - don't fail the request
        }
        
        return context;
    }
}
```

---

### Pipeline Implementation

```java
package com.ai.infrastructure.intent.orchestration.pipeline;

@Slf4j
@Component
public class DefaultOrchestrationPipeline implements Pipeline {
    
    private final List<PipelineStep> steps;
    
    public DefaultOrchestrationPipeline(List<PipelineStep> steps) {
        // Sort steps by order
        this.steps = steps.stream()
            .sorted(Comparator.comparingInt(PipelineStep::getOrder))
            .collect(Collectors.toList());
        
        log.info("Initialized orchestration pipeline with {} steps: {}", 
            this.steps.size(),
            this.steps.stream().map(PipelineStep::getStepName).collect(Collectors.joining(" → ")));
    }
    
    @Override
    public OrchestrationResult execute(String query, OrchestrationContext context) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(context, "context must not be null");
        context.validate();
        
        PipelineContext pipelineContext = PipelineContext.from(query, context);
        
        for (PipelineStep step : steps) {
            if (step.shouldSkip(pipelineContext)) {
                log.debug("Skipping step {} for request {}", 
                    step.getStepName(), pipelineContext.getRequestId());
                continue;
            }
            
            log.debug("Executing step {} for request {}", 
                step.getStepName(), pipelineContext.getRequestId());
            
            long startTime = System.currentTimeMillis();
            
            try {
                pipelineContext = step.process(pipelineContext);
                
                long duration = System.currentTimeMillis() - startTime;
                log.debug("Step {} completed in {}ms", step.getStepName(), duration);
                
            } catch (Exception ex) {
                log.error("Step {} failed for request {}: {}", 
                    step.getStepName(), pipelineContext.getRequestId(), ex.getMessage(), ex);
                return OrchestrationResult.error("Pipeline step failed: " + step.getStepName());
            }
            
            // Check for early termination
            if (pipelineContext.isShouldTerminate()) {
                log.debug("Pipeline terminated early at step {} for request {}", 
                    step.getStepName(), pipelineContext.getRequestId());
                return pipelineContext.getEarlyTerminationResult();
            }
        }
        
        return pipelineContext.getIntentResult();
    }
    
    @Override
    public List<PipelineStep> getSteps() {
        return Collections.unmodifiableList(steps);
    }
}
```

---

### Simplified Orchestrator

```java
package com.ai.infrastructure.intent.orchestration;

/**
 * Simplified RAGOrchestrator that delegates to the pipeline.
 * Now only 30 lines instead of 600+!
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    private final Pipeline pipeline;
    
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        return pipeline.execute(query, context);
    }
    
    @Deprecated(forRemoval = true)
    public OrchestrationResult orchestrate(String query, String userId) {
        log.warn("Using deprecated orchestrate(query, userId)");
        return orchestrate(query, OrchestrationContext.forUser(userId));
    }
}
```

---

## Benefits of This Refactoring

### Before vs After

| Aspect | Before | After |
|--------|--------|-------|
| **Lines of code** | 600+ in one class | ~50-80 per step class |
| **Dependencies** | 14 in constructor | 1-3 per step |
| **Testability** | Hard to test steps in isolation | Each step fully testable |
| **Extensibility** | Modify large method | Add new step class |
| **Readability** | Complex flow in one method | Clear single-purpose classes |
| **Debugging** | Hard to find issues | Step-by-step tracing |
| **Reusability** | None | Steps can be reused |

### Testing Improvements

```java
// Before: Hard to test just PII detection
@Test
void shouldDetectPII() {
    // Need to mock 14 dependencies
    // Need to go through entire flow
}

// After: Easy to test individual step
@Test
void shouldDetectPII() {
    PIIDetectionStep step = new PIIDetectionStep(piiService, piiProperties);
    PipelineContext input = PipelineContext.from("My SSN is 123-45-6789", context);
    
    PipelineContext output = step.process(input);
    
    assertThat(output.getProcessedQuery()).contains("[REDACTED]");
    assertThat(output.getDetectedPiiTypes()).contains("SSN");
}
```

### Adding New Steps

```java
// Want to add rate limiting? Just create a new step:
@Component
@RequiredArgsConstructor
public class RateLimitingStep implements PipelineStep {
    
    private final RateLimiter rateLimiter;
    
    @Override
    public String getStepName() { return "RateLimiting"; }
    
    @Override
    public int getOrder() { return 5; } // Before security
    
    @Override
    public PipelineContext process(PipelineContext context) {
        if (!rateLimiter.tryAcquire(context.getOrchestrationContext().getUserId())) {
            return context.terminate(OrchestrationResult.error("Rate limit exceeded"));
        }
        return context;
    }
}
// That's it! Spring auto-wires it into the pipeline.
```

### Conditional Steps

```java
// Want audit logging only in production?
@Component
@ConditionalOnProperty(name = "ai.audit.enabled", havingValue = "true")
public class AuditLoggingStep implements PipelineStep {
    // ...
}
```

---

## File Structure After Refactoring

```
com/ai/infrastructure/intent/orchestration/
├── RAGOrchestrator.java              # Simplified (30 lines)
├── OrchestrationContext.java
├── OrchestrationResult.java
├── OrchestrationResultType.java
└── pipeline/
    ├── Pipeline.java                  # Interface
    ├── PipelineStep.java              # Interface
    ├── PipelineContext.java           # Context carrier
    ├── DefaultOrchestrationPipeline.java
    └── steps/
        ├── SecurityAnalysisStep.java
        ├── AccessControlStep.java
        ├── PIIDetectionStep.java
        ├── ComplianceCheckStep.java
        ├── IntentExtractionStep.java
        ├── IntentHandlingStep.java
        ├── MetadataBuildingStep.java
        ├── SmartSuggestionsStep.java
        ├── ResponseSanitizationStep.java
        └── HistoryPersistenceStep.java
```

---

## Migration Steps

### Phase 1: Create Pipeline Infrastructure (Week 1)
1. Create `Pipeline`, `PipelineStep`, `PipelineContext` interfaces
2. Create `DefaultOrchestrationPipeline` implementation
3. Add unit tests for pipeline execution

### Phase 2: Extract Steps (Week 2-3)
1. Extract each step one at a time, starting with the simplest (MetadataBuilding)
2. Add unit tests for each step
3. Keep original orchestrator working during migration

### Phase 3: Wire Together (Week 3)
1. Register all steps as Spring beans
2. Switch orchestrator to use pipeline
3. Run integration tests

### Phase 4: Cleanup (Week 4)
1. Remove old inline code from orchestrator
2. Update documentation
3. Performance testing

---

## Configuration

```yaml
ai:
  orchestration:
    pipeline:
      # Enable/disable specific steps
      steps:
        security-analysis:
          enabled: true
        access-control:
          enabled: true
        pii-detection:
          enabled: true
        compliance-check:
          enabled: true
        smart-suggestions:
          enabled: true
        history-persistence:
          enabled: true
      
      # Logging
      logging:
        log-step-timing: true
        log-context-changes: false
```

---

## Conclusion

This pipeline-based refactoring:

✅ **Is definitely possible** - It's a well-established pattern  
✅ **Is a good idea** - Massive improvement in maintainability  
✅ **Lower risk than module extraction** - No breaking changes to API  
✅ **Immediate benefits** - Better testing, debugging, extensibility  
✅ **Enables future changes** - Easy to add/remove/modify steps  

**Recommendation**: Do this refactoring FIRST, before considering module extraction. It will make the codebase much easier to work with, and if you later decide to extract modules, the clean separation will make it easier.
