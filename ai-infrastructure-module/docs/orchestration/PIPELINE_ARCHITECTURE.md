# Pipeline Architecture

## Overview

The orchestration pipeline is a chain-of-responsibility implementation that processes requests through a series of ordered steps. Each step has a single responsibility and can either continue processing or terminate the pipeline early.

## Design Principles

### 1. Single Responsibility

Each step handles exactly one concern:

| Step | Single Responsibility |
|------|----------------------|
| SecurityAnalysisStep | Threat detection and blocking |
| AccessControlStep | Permission verification |
| PIIDetectionStep | PII detection and redaction |
| ComplianceCheckStep | Regulatory compliance |
| IntentExtractionStep | Query intent parsing |
| IntentHandlingStep | Intent routing and execution |
| MetadataBuildingStep | Result metadata enrichment |
| SmartSuggestionsStep | Next-step recommendations |
| ResponseSanitizationStep | Output sanitization |
| HistoryPersistenceStep | Interaction logging |

### 2. Immutable Context

The `PipelineContext` is immutable. Steps create new instances via `toBuilder()`:

```java
// Correct: Create new context with updated state
return context.toBuilder()
    .processedQuery(sanitizedQuery)
    .detectedPiiTypes(piiTypes)
    .build();

// Wrong: Never mutate context directly
// context.setProcessedQuery(sanitizedQuery);  // Not possible - no setters
```

### 3. Fail-Closed Security

Security-related steps terminate on any denial (fail-closed):

```java
// AccessControlStep - fail-closed pattern
if (!response.isAccessGranted()) {
    log.warn("Access denied for request {}", context.getRequestId());
    return context.terminate(OrchestrationResult.builder()
        .type(OrchestrationResultType.ACCESS_DENIED)
        .success(false)
        .message("Access denied")
        .build());
}
```

### 4. Order-Based Execution

Steps declare their execution order. Lower numbers execute first:

```java
@Override
public int getOrder() {
    return 10;  // Security runs first
}
```

Standard order ranges:
- **1-19**: Pre-security (reserved)
- **20-49**: Security gates (security, access, compliance)
- **50-69**: Core processing (intent extraction, handling)
- **70-89**: Enrichment (metadata, suggestions)
- **90-99**: Finalization (sanitization, history)
- **100+**: Post-processing (custom extensions)

### 5. Skip on Termination

Steps automatically skip if the pipeline is already terminated:

```java
@Override
public boolean shouldSkip(PipelineContext context) {
    return context != null && context.isShouldTerminate();
}
```

## Data Flow

```
                    ┌──────────────────┐
                    │   Input Query    │
                    │   + Context      │
                    └────────┬─────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                      PipelineContext                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ originalQuery: "Cancel my subscription"                  │    │
│  │ orchestrationContext: { userId, sessionId, ... }         │    │
│  │ requestId: "req-abc-123"                                 │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Security (10) │───▶│ Access (20)   │───▶│ PII (30)      │
│               │    │               │    │ +processedQry │
│ [may term]    │    │ [may term]    │    │ +piiTypes     │
└───────────────┘    └───────────────┘    └───────────────┘
                                                  │
        ┌─────────────────────────────────────────┘
        ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Compliance(40)│───▶│ Intent (50)   │───▶│ Handling (60) │
│               │    │ +intentResp   │    │ +intentResult │
│ [may term]    │    │ [may term]    │    │ [may term]    │
└───────────────┘    └───────────────┘    └───────────────┘
                                                  │
        ┌─────────────────────────────────────────┘
        ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Metadata (70) │───▶│ Suggest (80)  │───▶│ Sanitize (90) │
│ +metadata     │    │ +smartSuggest │    │ +sanitizedPay │
└───────────────┘    └───────────────┘    └───────────────┘
                                                  │
                                                  ▼
                                          ┌───────────────┐
                                          │ History (100) │
                                          │ [non-fatal]   │
                                          └───────────────┘
                                                  │
                                                  ▼
                                    ┌──────────────────────┐
                                    │ OrchestrationResult  │
                                    └──────────────────────┘
```

## Step Categories

### Security Gates (Orders 10-40)

These steps protect the system and can terminate processing:

```
SecurityAnalysisStep (10)
    │
    ├── Pass: Continue
    └── Block: TERMINATE → SECURITY_BLOCKED

AccessControlStep (20)
    │
    ├── Granted: Continue
    └── Denied: TERMINATE → ACCESS_DENIED

PIIDetectionStep (30)
    │
    └── Always continues (updates processedQuery)

ComplianceCheckStep (40)
    │
    ├── Compliant: Continue
    └── Non-compliant: TERMINATE → COMPLIANCE_VIOLATION
```

### Core Processing (Orders 50-60)

Intent extraction and handling:

```
IntentExtractionStep (50)
    │
    ├── Intents found: Continue with intentResponse
    └── No intents: TERMINATE → ERROR

IntentHandlingStep (60)
    │
    ├── ACTION: Execute handler → ACTION_EXECUTED/ACTION_DENIED
    ├── INFORMATION: RAG query → INFORMATION_PROVIDED
    ├── COMPOUND: Process all → COMPOUND_HANDLED
    └── OUT_OF_SCOPE: Guidance → OUT_OF_SCOPE
```

### Enrichment (Orders 70-90)

Result enhancement:

```
MetadataBuildingStep (70)
    │
    └── Adds metadata to result

SmartSuggestionsStep (80)
    │
    ├── High confidence: RAG for suggestion
    └── Low confidence: Skip

ResponseSanitizationStep (90)
    │
    └── Sanitizes and adds PII info
```

### Finalization (Order 100)

```
HistoryPersistenceStep (100)
    │
    └── Records to history (non-fatal on error)
```

## Error Handling

### Step-Level Errors

Each step handles its own errors:

```java
@Override
public PipelineContext process(PipelineContext context) {
    try {
        // Step logic
        return context.toBuilder()
            .intentResult(result)
            .build();
    } catch (Exception e) {
        log.error("Error in step {}: {}", getStepName(), e.getMessage(), e);
        return context.terminate(OrchestrationResult.builder()
            .type(OrchestrationResultType.ERROR)
            .success(false)
            .message("Step failed: " + e.getMessage())
            .build());
    }
}
```

### Pipeline-Level Errors

The pipeline catches unhandled exceptions:

```java
public OrchestrationResult execute(String query, OrchestrationContext context) {
    try {
        // Execute steps
    } catch (Exception e) {
        log.error("Pipeline execution failed", e);
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.ERROR)
            .success(false)
            .message("Pipeline error: " + e.getMessage())
            .build();
    }
}
```

### Non-Fatal Steps

Some steps (like history) are non-fatal:

```java
// HistoryPersistenceStep - non-fatal
try {
    intentHistoryService.recordIntent(...);
} catch (Exception e) {
    log.warn("Failed to record history (non-fatal): {}", e.getMessage());
    // Continue - don't terminate
}
return context;
```

## Extension Points

### Adding a Custom Step

1. Implement `PipelineStep`:

```java
@Component
public class AuditLoggingStep implements PipelineStep {
    
    private static final int ORDER = 95;  // After sanitization
    
    @Override
    public PipelineContext process(PipelineContext context) {
        auditService.log(context.getRequestId(), context.getIntentResult());
        return context;
    }
    
    @Override
    public String getStepName() { return "AuditLogging"; }
    
    @Override
    public int getOrder() { return ORDER; }
}
```

2. The step is auto-discovered via `@Component`

### Conditional Steps

Steps can be conditionally enabled:

```java
@Component
@ConditionalOnProperty(name = "ai.audit.enabled", havingValue = "true")
public class AuditLoggingStep implements PipelineStep {
    // Only active when ai.audit.enabled=true
}
```

### Step Composition

Steps can delegate to other services:

```java
@Component
@RequiredArgsConstructor
public class IntentHandlingStep implements PipelineStep {
    
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final RAGService ragService;
    
    @Override
    public PipelineContext process(PipelineContext context) {
        // Delegate based on intent type
        switch (intent.getType()) {
            case ACTION -> handleAction(context, intent);
            case INFORMATION -> handleInformation(context, intent);
            // ...
        }
    }
}
```

## Thread Safety

### Stateless Steps

Steps should be stateless and thread-safe:

```java
@Component
public class SecurityAnalysisStep implements PipelineStep {
    
    // Injected services should be thread-safe
    private final AISecurityService securityService;
    
    // No mutable instance state
    // private List<String> cache;  // WRONG
    
    @Override
    public PipelineContext process(PipelineContext context) {
        // All state flows through context
        return context.toBuilder()
            .metadata(updatedMetadata)
            .build();
    }
}
```

### Context Immutability

The immutable `PipelineContext` ensures thread safety:

```java
// Each step gets its own context instance
PipelineContext ctx1 = step1.process(initialContext);
PipelineContext ctx2 = step2.process(ctx1);
// ctx1 is unchanged, ctx2 has updates
```

## Monitoring

### Step Timing

The pipeline can track step execution time:

```java
for (PipelineStep step : steps) {
    long start = System.currentTimeMillis();
    pipelineContext = step.process(pipelineContext);
    long duration = System.currentTimeMillis() - start;
    
    log.debug("Step {} completed in {}ms", step.getStepName(), duration);
    metrics.recordStepDuration(step.getStepName(), duration);
}
```

### Step Metrics

Each step can emit its own metrics:

```java
@Override
public PipelineContext process(PipelineContext context) {
    meterRegistry.counter("orchestration.security.checks").increment();
    
    if (response.isShouldBlock()) {
        meterRegistry.counter("orchestration.security.blocks").increment();
    }
    
    return context;
}
```
