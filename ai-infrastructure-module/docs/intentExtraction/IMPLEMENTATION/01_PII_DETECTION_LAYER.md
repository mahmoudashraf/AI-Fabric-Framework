# Layer 1: PII Detection & Redaction (Optional)

## Overview

First layer processes user query to detect and optionally redact sensitive data.

**Status:** Optional (configurable via `application.yml`)

---

## 🎯 Minimal Implementation

### Step 1: Configuration (Optional Enablement)

```yaml
# application.yml
ai:
  pii-detection:
    enabled: false                    # Enable/disable PII layer
    mode: REDACT                      # REDACT, DETECT_ONLY, PASS_THROUGH
    store-encrypted-original: false   # Store encrypted original query
    
  security:
    sensitive-fields:
      - credit_card
      - ssn
      - phone_number
      - email
      - password
      - personal_id
```

### Step 2: PII Detection Service

```java
@Service
public class PIIDetectionService {
    
    @Value("${ai.pii-detection.enabled:false}")
    private boolean piiDetectionEnabled;
    
    @Value("${ai.pii-detection.mode:PASS_THROUGH}")
    private PIIMode piiMode;
    
    public PIIDetectionResult detectAndRedact(String query) {
        // If disabled, return as-is
        if (!piiDetectionEnabled) {
            return PIIDetectionResult.builder()
                .originalQuery(query)
                .redactedQuery(query)
                .hasPII(false)
                .detections(Collections.emptyList())
                .build();
        }
        
        // Detect PII patterns
        List<PIIDetection> detections = detectPatterns(query);
        boolean hasPII = !detections.isEmpty();
        
        // Apply mode
        String processedQuery = query;
        if (hasPII && piiMode == PIIMode.REDACT) {
            processedQuery = redactSensitiveData(query, detections);
        }
        
        return PIIDetectionResult.builder()
            .originalQuery(query)
            .redactedQuery(processedQuery)
            .hasPII(hasPII)
            .detections(detections)
            .build();
    }
    
    private List<PIIDetection> detectPatterns(String query) {
        List<PIIDetection> detections = new ArrayList<>();
        
        // Credit card pattern
        if (query.matches(".*\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}.*")) {
            detections.add(new PIIDetection("CREDIT_CARD", "credit_card"));
        }
        
        // Email pattern
        if (query.matches(".*[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}.*")) {
            detections.add(new PIIDetection("EMAIL", "email"));
        }
        
        // Phone pattern
        if (query.matches(".*\\+?\\d{1,3}[\\s-]?\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{4}.*")) {
            detections.add(new PIIDetection("PHONE", "phone_number"));
        }
        
        // SSN pattern
        if (query.matches(".*\\d{3}-?\\d{2}-?\\d{4}.*")) {
            detections.add(new PIIDetection("SSN", "ssn"));
        }
        
        return detections;
    }
    
    private String redactSensitiveData(String query, List<PIIDetection> detections) {
        String redacted = query;
        
        for (PIIDetection detection : detections) {
            if (detection.getType().equals("CREDIT_CARD")) {
                redacted = redacted.replaceAll("\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}", 
                    "****-****-****-****");
            }
            if (detection.getType().equals("EMAIL")) {
                redacted = redacted.replaceAll("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", 
                    "***@***.***");
            }
            if (detection.getType().equals("PHONE")) {
                redacted = redacted.replaceAll("\\+?\\d{1,3}[\\s-]?\\d{3}[\\s-]?\\d{3}[\\s-]?\\d{4}", 
                    "***-***-****");
            }
            if (detection.getType().equals("SSN")) {
                redacted = redacted.replaceAll("\\d{3}-?\\d{2}-?\\d{4}", "***-**-****");
            }
        }
        
        return redacted;
    }
}

// DTOs
@Data
@Builder
public class PIIDetectionResult {
    private String originalQuery;
    private String redactedQuery;
    private boolean hasPII;
    private List<PIIDetection> detections;
}

@Data
public class PIIDetection {
    private String type;          // CREDIT_CARD, EMAIL, PHONE, SSN
    private String fieldName;     // credit_card, email, phone_number, ssn
}

public enum PIIMode {
    PASS_THROUGH,    // Don't detect, just pass through
    DETECT_ONLY,     // Detect but don't redact
    REDACT           // Detect and redact
}
```

### Step 3: Integration in Main Flow

```java
@Service
public class RAGService {
    
    @Autowired
    private PIIDetectionService piiDetectionService;
    
    @Autowired
    private IntentQueryExtractor intentExtractor;
    
    public RAGResponse performRAGQuery(String userQuery, String userId) {
        // LAYER 1: PII Detection & Redaction
        PIIDetectionResult piiResult = piiDetectionService.detectAndRedact(userQuery);
        String queryToProcess = piiResult.getRedactedQuery();
        
        // LAYER 2: Intent Extraction (with redacted query)
        MultiIntentResponse intents = intentExtractor.extract(queryToProcess, userId);
        
        // ... rest of flow
        
        // Store original PII flag for intent history
        return RAGResponse.builder()
            .intents(intents)
            .hasSensitiveData(piiResult.isHasPII())
            .build();
    }
}
```

---

## ⚙️ Configuration Examples

### Example 1: Disabled (Development)

```yaml
ai:
  pii-detection:
    enabled: false              # No PII processing
```

**Result:** All queries pass through as-is, no redaction

---

### Example 2: Detect Only (Monitoring)

```yaml
ai:
  pii-detection:
    enabled: true
    mode: DETECT_ONLY           # Log but don't redact
```

**Result:** Detects PII patterns, logs them, but passes original query forward

---

### Example 3: Full Protection (Production)

```yaml
ai:
  pii-detection:
    enabled: true
    mode: REDACT                # Detect and redact
    store-encrypted-original: true  # Keep encrypted copy for audit
```

**Result:** Redacts all PII, passes clean query forward

---

## 📋 Implementation Checklist

- [ ] Add PIIDetectionService
- [ ] Add PIIDetectionResult DTO
- [ ] Add PIIMode enum
- [ ] Add configuration properties
- [ ] Integrate into RAGService
- [ ] Add logging for PII detection
- [ ] Test with sample PII data
- [ ] Document in README

---

## ✅ What User Implements

**Nothing!** This layer is library code. Just configure via `application.yml`.

---

## 🔄 Flow

```
User Query
    ↓
PIIDetectionService (if enabled)
├─ Detect PII patterns
├─ Redact if REDACT mode
└─ Flag if hasPII
    ↓
Redacted Query (or original if disabled)
    ↓
→ Next Layer: IntentQueryExtractor
```

---

## 📊 Output Example

**Input:** "Cancel my subscription, my CC is 4532-1234-5678-9012 and email is john@example.com"

**Output (REDACT mode):**
```json
{
  "originalQuery": "Cancel my subscription, my CC is 4532-1234-5678-9012 and email is john@example.com",
  "redactedQuery": "Cancel my subscription, my CC is ****-****-****-**** and email is ***@***.***",
  "hasPII": true,
  "detections": [
    {"type": "CREDIT_CARD", "fieldName": "credit_card"},
    {"type": "EMAIL", "fieldName": "email"}
  ]
}
```

Next: Go to `02_INTENT_EXTRACTION_LAYER.md`

