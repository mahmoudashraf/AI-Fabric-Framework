# Pipeline Steps Reference

## Overview

This document provides a detailed reference for all pipeline steps in the orchestration pipeline.

## Step Summary

| Order | Step | Package | Can Terminate | Dependencies |
|-------|------|---------|---------------|--------------|
| 10 | SecurityAnalysisStep | `.steps` | Yes | AISecurityService |
| 20 | AccessControlStep | `.steps` | Yes | AIAccessControlService |
| 30 | PIIDetectionStep | `.steps` | No | PIIDetectionService, PIIDetectionProperties |
| 40 | ComplianceCheckStep | `.steps` | Yes | AIComplianceService |
| 50 | IntentExtractionStep | `.steps` | Yes | IntentQueryExtractor |
| 60 | IntentHandlingStep | `.steps` | Yes | AIActionRegistry, RAGService |
| 70 | MetadataBuildingStep | `.steps` | No | None |
| 80 | SmartSuggestionsStep | `.steps` | No | SmartSuggestionsProperties, RAGService |
| 90 | ResponseSanitizationStep | `.steps` | No | ResponseSanitizer, PIIDetectionProperties |
| 100 | HistoryPersistenceStep | `.steps` | No | IntentHistoryService |

---

## SecurityAnalysisStep

**Order:** 10  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** Yes

### Purpose

Analyzes the incoming request for security threats using the AI security service. Blocks requests that are deemed malicious or suspicious.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `AISecurityService` | Service | Performs threat analysis |

### Behavior

1. Builds `AISecurityRequest` from context (query, userId, sessionId, IP, user agent, metadata)
2. Calls `securityService.analyzeRequest(request)`
3. If `shouldBlock=true`: Terminates with `SECURITY_BLOCKED`
4. Otherwise: Continues to next step

### Termination Conditions

| Condition | Result Type | Message |
|-----------|-------------|---------|
| `shouldBlock=true` | `SECURITY_BLOCKED` | "Request blocked by security analysis" |

### Context Updates

None (pass-through if allowed)

### Example

```java
// Security blocks suspicious query
AISecurityResponse response = AISecurityResponse.builder()
    .shouldBlock(true)
    .threatLevel("HIGH")
    .reason("SQL injection detected")
    .build();
// → Terminates with SECURITY_BLOCKED
```

---

## AccessControlStep

**Order:** 20  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** Yes

### Purpose

Verifies that the user has permission to access the system. Implements **fail-closed** security model.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `AIAccessControlService` | Service | Checks access permissions |

### Behavior

1. Builds `AIAccessControlRequest` from context
2. Calls `accessControlService.checkAccess(request)`
3. If `accessGranted=false`: Terminates with `ACCESS_DENIED` (fail-closed)
4. Otherwise: Continues to next step

### Termination Conditions

| Condition | Result Type | Message |
|-----------|-------------|---------|
| `accessGranted=false` | `ACCESS_DENIED` | "Access denied" |

### Context Updates

Adds to metadata:
- `accessControl.permissions`: Granted permissions
- `accessControl.roles`: User roles

### Security Note

This step implements **fail-closed** security: any ambiguous or failed response results in denial.

---

## PIIDetectionStep

**Order:** 30  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** No

### Purpose

Detects and optionally redacts Personally Identifiable Information (PII) from the input query.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `PIIDetectionService` | Service | Detects PII in text |
| `PIIDetectionProperties` | Config | Detection configuration |

### Behavior

1. Checks if PII detection is enabled
2. Checks if detection direction includes `INPUT`
3. Calls `piiDetectionService.detectAndProcess(query)`
4. Updates context with processed query and detected PII types
5. Always continues (never terminates)

### Context Updates

| Field | Description |
|-------|-------------|
| `processedQuery` | Query with PII redacted (if configured) |
| `detectedPiiTypes` | List of detected PII types (e.g., "EMAIL", "PHONE") |

### Configuration

```yaml
ai:
  infrastructure:
    pii-detection:
      enabled: true
      detection-direction: INPUT_OUTPUT  # INPUT_ONLY, OUTPUT_ONLY, INPUT_OUTPUT
```

---

## ComplianceCheckStep

**Order:** 40  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** Yes

### Purpose

Validates that the request complies with regulatory and business requirements.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `AIComplianceService` | Service | Checks compliance rules |

### Behavior

1. Builds `AIComplianceRequest` from context
2. Calls `complianceService.checkCompliance(request)`
3. If `overallCompliant=false`: Terminates with `COMPLIANCE_VIOLATION`
4. Otherwise: Continues to next step

### Termination Conditions

| Condition | Result Type | Message |
|-----------|-------------|---------|
| `overallCompliant=false` | `COMPLIANCE_VIOLATION` | "Request does not meet compliance requirements" |

### Context Updates

Adds to metadata:
- `compliance.violations`: List of violation details (if any)
- `compliance.policies`: Checked policies

---

## IntentExtractionStep

**Order:** 50  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** Yes

### Purpose

Extracts the user's intent(s) from the processed query using AI/LLM-based analysis.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `IntentQueryExtractor` | Service | Extracts intents from query |

### Behavior

1. Calls `intentQueryExtractor.extract(processedQuery, orchestrationContext)`
2. If no intents extracted: Terminates with `ERROR`
3. Otherwise: Updates context with `MultiIntentResponse`

### Termination Conditions

| Condition | Result Type | Message |
|-----------|-------------|---------|
| No intents | `ERROR` | "Unable to determine intent" |

### Context Updates

| Field | Description |
|-------|-------------|
| `intentResponse` | `MultiIntentResponse` with extracted intents |

### Intent Types

- `ACTION`: User wants to perform an action
- `INFORMATION`: User wants information
- `OUT_OF_SCOPE`: Query outside supported domain
- `CONFIRMATION_POSITIVE`: User confirms a pending action
- `CONFIRMATION_NEGATIVE`: User rejects a pending action

Multi-intent is represented by returning multiple root `intents[]` entries (there is no `type=COMPOUND`).

---

## IntentHandlingStep

**Order:** 60  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** Yes

### Purpose

Routes and processes extracted intents based on their type. This is the core business logic step.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `AIActionRegistry` | Registry | Registered `@AIAction` handlers |
| `RAGService` | Service | RAG operations |

### Behavior

Routes based on intent type:

#### ACTION Intent

1. Finds handler via `actionRegistry.findHandler(action)`
2. Validates action allowed via `handler.validateActionAllowed(actionContext)` (userId/sessionId)
3. If `requiresConfirmation=true`, returns `CONFIRMATION_REQUIRED` and stores a pending action
4. Otherwise executes via `handler.executeAction(params, actionContext)`
5. Returns `ACTION_EXECUTED` or `ACTION_DENIED`

#### INFORMATION Intent

1. Builds `RAGRequest` with query, entity type, metadata
2. If `requiresGeneration=true`: Calls `ragService.performRAGQuery()`
3. Otherwise: Calls `ragService.performRag()`
4. Returns `INFORMATION_PROVIDED`

#### Multi-intent (intents[].size > 1)

1. Processes each intent in order
2. Collects results into children list
3. Returns `COMPOUND_HANDLED`

#### OUT_OF_SCOPE Intent

1. Returns guidance message
2. Returns `OUT_OF_SCOPE`

### Termination Conditions

| Condition | Result Type |
|-----------|-------------|
| Anonymous user + ACTION | `ACTION_DENIED` |
| Handler not found | `ERROR` |
| Action validation failed | `ACTION_DENIED` |
| Action execution error | `ERROR` |

### Context Updates

| Field | Description |
|-------|-------------|
| `intentResult` | `OrchestrationResult` with handling outcome |

---

## MetadataBuildingStep

**Order:** 70  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** No

### Purpose

Builds and attaches metadata to the orchestration result for debugging, logging, and downstream processing.

### Dependencies

None

### Behavior

1. Collects metadata from context:
   - `requestId`
   - `sessionId`
   - `intentsCount`
   - `compound` flag
   - `authenticated` flag
   - Intent-level metadata
2. Attaches to `OrchestrationResult.metadata`

### Context Updates

| Field | Description |
|-------|-------------|
| `metadata` | Map with request metadata |

### Metadata Fields

| Key | Type | Description |
|-----|------|-------------|
| `requestId` | String | Unique request identifier |
| `sessionId` | String | Session identifier |
| `intentsCount` | Integer | Number of intents processed |
| `compound` | Boolean | Whether request was compound |
| `authenticated` | Boolean | Whether user was authenticated |
| `intentMetadata` | Map | Metadata from intent extraction |

---

## SmartSuggestionsStep

**Order:** 80  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** No

### Purpose

Generates smart suggestions based on next-step recommendations from intent handling. Proactively provides helpful follow-up information.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `SmartSuggestionsProperties` | Config | Suggestion configuration |
| `RAGService` | Service | RAG for suggestion content |

### Behavior

1. Checks if smart suggestions enabled
2. Finds highest confidence next-step recommendation
3. If confidence >= threshold:
   - Performs RAG query for suggestion context
   - Builds smart suggestion map
   - Attaches to result
4. Updates context with suggestion

### Configuration

```yaml
ai:
  infrastructure:
    smart-suggestions:
      enabled: true
      min-confidence: 0.7
      primary-confidence: 0.85
      retrieval-limit: 5
      retrieval-threshold: 0.7
```

### Context Updates

| Field | Description |
|-------|-------------|
| `smartSuggestion` | Map with suggestion details |

### Suggestion Fields

| Key | Type | Description |
|-----|------|-------------|
| `intent` | String | Suggested intent |
| `title` | String | Human-readable title |
| `response` | String | RAG-generated content |
| `confidence` | Double | Confidence score |
| `priority` | String | PRIMARY or SECONDARY |

---

## ResponseSanitizationStep

**Order:** 90  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** No

### Purpose

Sanitizes the response before returning to the client. Merges PII detection information from input and output phases.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `ResponseSanitizer` | Service | Sanitizes response content |
| `PIIDetectionProperties` | Config | PII configuration |

### Behavior

1. Calls `responseSanitizer.sanitize(result, identifier)`
2. If OUTPUT detection enabled and PII detected in input:
   - Merges PII types into sanitization metadata
3. Sets sanitized payload on result

### Configuration

```yaml
ai:
  infrastructure:
    pii-detection:
      detection-direction: INPUT_OUTPUT  # Enables output detection
```

### Context Updates

| Field | Description |
|-------|-------------|
| `sanitizedPayload` | Sanitized response map |

---

## HistoryPersistenceStep

**Order:** 100  
**Package:** `com.ai.infrastructure.intent.orchestration.pipeline.steps`  
**Can Terminate:** No

### Purpose

Records the interaction in the intent history for analytics, debugging, and audit purposes. This step is **non-fatal** - errors do not affect the response.

### Dependencies

| Dependency | Type | Description |
|------------|------|-------------|
| `IntentHistoryService` | Service | History persistence |

### Behavior

1. Extracts identifier (userId or sessionId)
2. Calls `intentHistoryService.recordIntent(...)`
3. On error: Logs warning but continues (non-fatal)
4. Returns context unchanged

### Error Handling

```java
try {
    intentHistoryService.recordIntent(identifier, requestId, query, result, metadata);
} catch (Exception e) {
    log.warn("Failed to record intent history (non-fatal): {}", e.getMessage());
    // Continue - do not terminate
}
```

### Recorded Data

| Field | Description |
|-------|-------------|
| `identifier` | User ID or session ID |
| `requestId` | Unique request ID |
| `query` | Original query |
| `result` | Orchestration result |
| `metadata` | Request metadata |

---

## Creating Custom Steps

### Step Template

```java
package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomStep implements PipelineStep {
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    private static final String STEP_NAME = "CustomStep";
    private static final int STEP_ORDER = 75;  // Between metadata and suggestions
    
    // =========================================================================
    // Dependencies
    // =========================================================================
    
    private final CustomService customService;
    
    // =========================================================================
    // PipelineStep Implementation
    // =========================================================================
    
    @Override
    public String getStepName() {
        return STEP_NAME;
    }
    
    @Override
    public int getOrder() {
        return STEP_ORDER;
    }
    
    @Override
    public PipelineContext process(PipelineContext context) {
        log.debug("Processing custom step for request {}", context.getRequestId());
        
        // Custom logic here
        CustomResult result = customService.process(context.getProcessedQuery());
        
        // Option 1: Continue with updated context
        return context.toBuilder()
            .metadata(mergeMetadata(context.getMetadata(), result))
            .build();
        
        // Option 2: Terminate on failure
        // if (result.hasFailed()) {
        //     return context.terminate(OrchestrationResult.builder()
        //         .type(OrchestrationResultType.ERROR)
        //         .message(result.getErrorMessage())
        //         .build());
        // }
    }
}
```

### Order Selection Guide

| Range | Use Case |
|-------|----------|
| 1-9 | Pre-security (avoid unless necessary) |
| 10-19 | Security analysis |
| 20-29 | Access control |
| 30-39 | Input processing (PII, normalization) |
| 40-49 | Compliance |
| 50-59 | Intent extraction |
| 60-69 | Intent handling |
| 70-79 | Metadata and enrichment |
| 80-89 | Suggestions and recommendations |
| 90-99 | Response processing |
| 100+ | History, audit, cleanup |
