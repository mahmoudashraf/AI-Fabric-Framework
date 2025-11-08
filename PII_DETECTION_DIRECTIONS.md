# PII Detection Directions - Configurable Feature

## Overview

PII (Personally Identifiable Information) detection can now be configured to work in **three directional modes**:
- **INPUT**: Detect PII in user queries BEFORE sending to LLM
- **OUTPUT**: Detect PII in LLM responses (safety net for accidental leaks)
- **BOTH**: Detect PII in both directions (comprehensive security - default)

## Configuration

### YAML Configuration

Add the `detection-direction` property to your `application.yml`:

```yaml
ai:
  pii-detection:
    enabled: true
    mode: DETECT_ONLY  # or REDACT
    # NEW: Configure directional PII detection
    detection-direction: BOTH  # INPUT | OUTPUT | BOTH (default: BOTH)
    
    # ... rest of PII configuration ...
    patterns:
      CREDIT_CARD:
        field-name: "credit_card"
        regex: "(?<!\\d)(?:\\d[ -]?){13,16}(?!\\d)"
        replacement: "****-****-****-****"
        enabled: true
```

### Programmatic Configuration

```java
PIIDetectionProperties piiProps = new PIIDetectionProperties();
piiProps.setEnabled(true);
piiProps.setDetectionDirection(PIIDetectionProperties.PIIDetectionDirection.INPUT);
```

## Three Detection Modes Explained

### 1. INPUT Mode
```
User Query → [Detect & Redact PII] → [Send to LLM] → LLM Response
           ↓
      Redacted Query sent to LLM
      (LLM never sees sensitive data)
```

**Use Case:** When you want to prevent LLM exposure to sensitive data
- Detects PII in user input BEFORE sending to LLM
- PII is redacted from the query
- LLM response is NOT scanned for PII
- Detected PII types are stored in audit logs

**Example Configuration:**
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: INPUT
```

### 2. OUTPUT Mode
```
User Query → [Send to LLM] → LLM Response → [Detect PII] → [Redact if needed]
                                                    ↓
                                          LLM may have leaked PII
                                          (caught by output detection)
```

**Use Case:** When you want to catch accidental PII leaks from the LLM
- User input is sent as-is to LLM
- LLM response is scanned for PII
- Acts as a safety net to catch model hallucinations or training data leaks
- Detected PII is redacted from response

**Example Configuration:**
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: OUTPUT
```

### 3. BOTH Mode (Default)
```
User Query → [Detect & Redact PII] → [Send to LLM] → LLM Response → [Detect & Redact PII]
           ↓                                                              ↓
      Prevent exposure                                           Catch accidental leaks
      (proactive)                                                (defensive)
```

**Use Case:** Comprehensive security (recommended for production)
- Detects & redacts PII BEFORE sending to LLM (proactive)
- Also scans LLM response for PII (defensive)
- Defense-in-depth security posture
- Detected PII types are merged and reported

**Example Configuration:**
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH  # Default
```

## Response Metadata

Regardless of detection direction, the response includes PII metadata:

```json
{
  "safeSummary": "...",
  "sanitization": {
    "risk": "HIGH_RISK",
    "detectedTypes": ["CREDIT_CARD"],
    "detectionMode": "DETECT_ONLY",
    "direction": "BOTH"
  },
  "warning": {
    "message": "Sensitive payment information was detected..."
  }
}
```

## Code Implementation

### RAGOrchestrator - Orchestration Flow

```java
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    private final PIIDetectionService piiDetectionService;
    private final PIIDetectionProperties piiDetectionProperties;
    
    public OrchestrationResult orchestrate(String query, String userId) {
        // STEP 1: Check if INPUT detection is enabled
        boolean detectInput = piiDetectionProperties.isEnabled() && 
            (piiDetectionProperties.getDetectionDirection() == PIIDetectionDirection.INPUT ||
             piiDetectionProperties.getDetectionDirection() == PIIDetectionDirection.BOTH);
        
        String processedQuery = query;
        List<String> detectedPiiTypes = new ArrayList<>();
        
        if (detectInput) {
            // Detect & redact PII BEFORE sending to LLM
            PIIDetectionResult analysis = piiDetectionService.analyze(query);
            processedQuery = analysis.getProcessedQuery();
            detectedPiiTypes = analysis.getDetections().stream()
                .map(PIIDetection::getType)
                .collect(Collectors.toList());
        }
        
        // STEP 2: Send processed query to LLM
        MultiIntentResponse response = intentQueryExtractor.extract(processedQuery, userId);
        
        // STEP 3: Check if OUTPUT detection is enabled
        boolean detectOutput = piiDetectionProperties.isEnabled() && 
            (piiDetectionProperties.getDetectionDirection() == PIIDetectionDirection.OUTPUT ||
             piiDetectionProperties.getDetectionDirection() == PIIDetectionDirection.BOTH);
        
        if (detectOutput) {
            // Scan LLM response for any leaked PII
            Map<String, Object> sanitizedPayload = responseSanitizer.sanitize(result, userId);
        }
        
        return result;
    }
}
```

## Configuration Examples

### Example 1: Prevent LLM Exposure (INPUT only)
```yaml
# Block PII before it reaches the LLM
ai:
  pii-detection:
    enabled: true
    detection-direction: INPUT
    mode: REDACT
```

### Example 2: Safety Net (OUTPUT only)
```yaml
# Let LLM see original data, catch accidental leaks
ai:
  pii-detection:
    enabled: true
    detection-direction: OUTPUT
    mode: REDACT
```

### Example 3: Full Protection (BOTH - Recommended)
```yaml
# Comprehensive security: prevent exposure + catch leaks
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH
    mode: DETECT_ONLY  # or REDACT
    audit-logging-enabled: true
    patterns:
      CREDIT_CARD:
        enabled: true
      SSN:
        enabled: true
      EMAIL:
        enabled: true
```

### Example 4: Disabled (for development)
```yaml
ai:
  pii-detection:
    enabled: false
    # detection-direction is ignored when disabled
```

## Testing

The test configuration in `application-real-api-test.yml` uses **BOTH** mode:

```yaml
test:
  enable-mock-provider: false

ai:
  pii-detection:
    enabled: true
    mode: DETECT_ONLY
    detection-direction: BOTH  # Default: comprehensive security
    patterns:
      CREDIT_CARD:
        field-name: "credit_card"
        regex: "(?<!\\d)(?:\\d[ -]?){13,16}(?!\\d)"
        enabled: true
```

## Logs

When PII is detected, you'll see logs like:

```
[INPUT Detection]
PII detected in user query - types: [CREDIT_CARD] (mode: INPUT_REDACTION)
Original query length: 250, Redacted query length: 210

[OUTPUT Detection]
PII detected - totalDetections=1, mode=DETECT_ONLY, sensitiveFields=credit_card
```

## Security Benefits

| Mode | Prevents LLM Exposure | Catches Leaks | Performance | Recommended |
|------|----------------------|--------------|-------------|------------|
| INPUT | ✅ | ❌ | Minimal | Dev/API mode |
| OUTPUT | ❌ | ✅ | Fast | Research/Analysis |
| BOTH | ✅ | ✅ | Balanced | ✅ Production |
| Disabled | ❌ | ❌ | Fastest | Dev/Testing |

## Migration Guide

If you're upgrading from the previous implementation:

1. **No breaking changes** - defaults to `BOTH` (existing behavior)
2. **Update your YAML** to explicitly set `detection-direction` if needed:
   ```yaml
   ai:
     pii-detection:
       detection-direction: BOTH  # or INPUT/OUTPUT as needed
   ```
3. **Existing tests** will continue to work without modification
4. **Update RAGOrchestratorTest** to pass new dependency:
   ```java
   orchestrator = new RAGOrchestrator(
       intentQueryExtractor, actionHandlerRegistry, ragService,
       responseSanitizer, intentHistoryService, smartSuggestionsProperties,
       piiDetectionService, piiDetectionProperties
   );
   ```

## Files Modified

1. **PIIDetectionProperties.java** - Added `detectionDirection` property and enum
2. **RAGOrchestrator.java** - Implemented directional PII detection logic
3. **RAGOrchestratorTest.java** - Updated test setup with new parameter

## Conclusion

The configurable PII detection directions provide **flexible security** for different use cases:
- **INPUT**: Privacy-first approach (prevent LLM exposure)
- **OUTPUT**: Safety-net approach (catch accidental leaks)
- **BOTH**: Defense-in-depth (recommended for production)

Choose the mode that best fits your security requirements!

