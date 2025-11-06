# PII and Sensitive Data Handling Strategy

## Your Question
**"If user's query contains personal information (PII) or risky internal data to be exposed publicly, what are our options?"**

---

## The Problem

### Examples of Risky Queries

```
✗ "My credit card is 4532-1234-5678-9012 and CVV is 123"
✗ "My SSN is 123-45-6789"
✗ "I'm suing you, here's my case number: XYZ"
✗ "Our database password is prod-db-password-123"
✗ "Our payment processing API key is sk_live_abc123def456"
✗ "My health records show I have [condition]"
✗ "My bank account is [number]"
```

### Why It's Critical

```
Security Risk:
  → Sensitive data in logs
  → Sensitive data in audit trails
  → Sensitive data in LLM responses
  → Sensitive data exposed to admin/support

Compliance Risk:
  → GDPR violations
  → CCPA violations
  → HIPAA violations
  → PCI-DSS violations
  → SOC 2 violations
```

---

## 5 Defense Layers Strategy

```
┌─────────────────────────────────────────┐
│ Layer 1: Detection & Classification     │
├─────────────────────────────────────────┤
│ Identify PII before processing          │
└─────────────────────────────────────────┘
             ↓
┌─────────────────────────────────────────┐
│ Layer 2: Masking & Redaction            │
├─────────────────────────────────────────┤
│ Remove/mask sensitive data from logs    │
└─────────────────────────────────────────┘
             ↓
┌─────────────────────────────────────────┐
│ Layer 3: Intent Routing                 │
├─────────────────────────────────────────┤
│ Don't send PII to LLM if not needed     │
└─────────────────────────────────────────┘
             ↓
┌─────────────────────────────────────────┐
│ Layer 4: Secure Storage                 │
├─────────────────────────────────────────┤
│ Encrypt PII at rest and in transit      │
└─────────────────────────────────────────┘
             ↓
┌─────────────────────────────────────────┐
│ Layer 5: Response Sanitization          │
├─────────────────────────────────────────┤
│ Remove PII before returning to user     │
└─────────────────────────────────────────┘
```

---

## Layer 1: Detection & Classification

### Option 1A: Pattern-Based Detection (Fast)

```java
@Service
@Slf4j
public class SensitiveDataDetector {
    
    private static final Map<String, Pattern> PATTERNS = Map.ofEntries(
        // Credit Card
        Map.entry("CREDIT_CARD", Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
        )),
        
        // SSN
        Map.entry("SSN", Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
        )),
        
        // Email
        Map.entry("EMAIL", Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
        )),
        
        // Phone
        Map.entry("PHONE", Pattern.compile(
            "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
        )),
        
        // API Key
        Map.entry("API_KEY", Pattern.compile(
            "(?i)(api[_-]?key|api[_-]?secret|sk_live_|sk_test_)\\s*[=:]{1,2}\\s*[\\w-]{20,}"
        )),
        
        // Database Password
        Map.entry("DB_PASSWORD", Pattern.compile(
            "(?i)(password|passwd|pwd)\\s*[=:]{1,2}\\s*[^\\s]+"
        ))
    );
    
    /**
     * Detect sensitive data in text
     */
    public SensitiveDataDetectionResult detect(String text) {
        SensitiveDataDetectionResult result = new SensitiveDataDetectionResult();
        
        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            String dataType = entry.getKey();
            Pattern pattern = entry.getValue();
            Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                result.addDetection(
                    dataType,
                    matcher.group(),
                    matcher.start(),
                    matcher.end()
                );
            }
        }
        
        return result;
    }
}

@Data
@Builder
public class SensitiveDataDetectionResult {
    @Builder.Default
    private Map<String, List<Detection>> detections = new HashMap<>();
    
    public void addDetection(String type, String value, int start, int end) {
        detections.computeIfAbsent(type, k -> new ArrayList<>())
            .add(new Detection(value, start, end));
    }
    
    public boolean hasDetections() {
        return !detections.isEmpty();
    }
    
    @Data
    @AllArgsConstructor
    public static class Detection {
        private String value;
        private int start;
        private int end;
    }
}
```

### Option 1B: ML-Based Detection (Accurate)

```java
@Service
@Slf4j
public class MLSensitiveDataDetector {
    
    private final PredictorClient predictorClient;  // AWS Macie, Google DLP, etc.
    
    /**
     * Use ML to detect sensitive data with high accuracy
     */
    public SensitiveDataDetectionResult detectWithML(String text) {
        try {
            // Call cloud service (AWS Macie, Google Cloud DLP, etc.)
            DLPAnalysisResult dlpResult = predictorClient.analyzeSensitiveData(text);
            
            return convertToDetectionResult(dlpResult);
            
        } catch (Exception e) {
            log.error("Error detecting sensitive data with ML", e);
            // Fallback to pattern-based
            return new SensitiveDataDetector().detect(text);
        }
    }
    
    private SensitiveDataDetectionResult convertToDetectionResult(DLPAnalysisResult dlpResult) {
        // Convert cloud service result to our format
        return null;
    }
}
```

### Recommendation
- **Start with:** Pattern-based (Layer 1A) for speed
- **Add later:** ML-based (Layer 1B) for accuracy
- **Combine both:** Pattern for fast path, ML for verification

---

## Layer 2: Masking & Redaction

### Option 2A: Complete Redaction (Most Secure)

```java
@Service
@Slf4j
public class SensitiveDataRedactor {
    
    /**
     * Completely remove/redact sensitive data
     */
    public String redactCompletely(String text, SensitiveDataDetectionResult detections) {
        String redacted = text;
        
        // Sort by position (reverse) to maintain indices
        List<SensitiveDataDetectionResult.Detection> allDetections = 
            detections.getAllDetections().stream()
                .sorted(Comparator.comparingInt(Detection::getStart).reversed())
                .collect(Collectors.toList());
        
        for (SensitiveDataDetectionResult.Detection detection : allDetections) {
            String placeholder = "[REDACTED_" + detection.getType() + "]";
            redacted = redacted.substring(0, detection.getStart()) 
                + placeholder 
                + redacted.substring(detection.getEnd());
        }
        
        return redacted;
    }
}
```

### Option 2B: Partial Masking (User-Friendly)

```java
@Service
public class SensitiveDataMasker {
    
    /**
     * Mask but preserve some info for user context
     */
    public String maskPartially(String text, SensitiveDataDetectionResult detections) {
        String masked = text;
        
        for (SensitiveDataDetectionResult.Detection detection : detections.getAllDetections()) {
            String masked Value = maskValue(detection);
            masked = masked.replaceFirst(
                Pattern.quote(detection.getValue()),
                maskedValue
            );
        }
        
        return masked;
    }
    
    private String maskValue(SensitiveDataDetectionResult.Detection detection) {
        String value = detection.getValue();
        String type = detection.getType();
        
        switch (type) {
            case "CREDIT_CARD":
                // Show last 4 digits
                return "****-****-****-" + value.replaceAll("[^0-9]", "").substring(12);
                
            case "EMAIL":
                // Show domain only
                return "***@" + value.substring(value.indexOf("@") + 1);
                
            case "PHONE":
                // Show last 4 digits
                String digits = value.replaceAll("[^0-9]", "");
                return "***-***-" + digits.substring(digits.length() - 4);
                
            case "SSN":
                // Show last 4 digits
                return "***-**-" + value.substring(7);
                
            case "API_KEY":
            case "DB_PASSWORD":
                // Never expose
                return "[REDACTED_" + type + "]";
                
            default:
                return "[REDACTED]";
        }
    }
}
```

### Recommendation
- **For logs:** Use complete redaction (2A)
- **For user display:** Use partial masking (2B)
- **For sensitive fields:** Never expose

---

## Layer 3: Intent Routing

### Option 3: Smart Intent Routing

```java
@Service
@Slf4j
public class SafeIntentQueryExtractor extends IntentQueryExtractor {
    
    private final SensitiveDataDetector sensitiveDataDetector;
    private final SensitiveDataRedactor redactor;
    
    public MultiIntentResponse extract(String rawQuery, String userId) {
        // Step 1: Detect sensitive data
        SensitiveDataDetectionResult detections = 
            sensitiveDataDetector.detect(rawQuery);
        
        if (detections.hasDetections()) {
            log.warn("Sensitive data detected in query for user: {}", userId);
            
            // Step 2: Log redacted version only
            String redactedQuery = redactor.redactCompletely(rawQuery, detections);
            auditLog.log("Query with PII detected: " + redactedQuery);
            
            // Step 3: Route carefully
            return handleQueryWithSensitiveData(rawQuery, detections, userId);
        }
        
        // Normal flow for safe queries
        return super.extract(rawQuery, userId);
    }
    
    /**
     * Handle queries containing sensitive data
     */
    private MultiIntentResponse handleQueryWithSensitiveData(
        String rawQuery,
        SensitiveDataDetectionResult detections,
        String userId) {
        
        // Step 1: Redact query before sending to LLM
        String redactedQuery = redactor.redactCompletely(rawQuery, detections);
        
        // Step 2: Extract intents from redacted query
        MultiIntentResponse intents = super.extract(redactedQuery, userId);
        
        // Step 3: Mark as sensitive
        intents.setSensitiveDataDetected(true);
        intents.setDetectedSensitiveTypes(
            detections.detections.keySet()
        );
        
        // Step 4: Add warning
        intents.addWarning(
            "This query contains sensitive information. " +
            "It has been handled securely."
        );
        
        return intents;
    }
}
```

### Flow: How It Works

```
User Query: "My credit card is 4532-1234-5678-9012"
    ↓
1. Detect sensitive data
   └─ CREDIT_CARD: 4532-1234-5678-9012
    ↓
2. Log redacted version only
   └─ Audit log: "My credit card is [REDACTED_CREDIT_CARD]"
    ↓
3. Redact before LLM
   └─ Query to LLM: "My credit card is [REDACTED_CREDIT_CARD]"
    ↓
4. LLM can't see actual CC number
   └─ Extracts: "INFORMATION intent: credit_card_update"
    ↓
5. Response to user (without exposing data)
   └─ "We've noted your request, but please use a secure form"
```

---

## Layer 4: Secure Storage & Transmission

### Option 4A: Encryption at Rest

```java
@Service
@Slf4j
public class SensitiveDataEncryptor {
    
    private final KeyManagementService keyService;
    
    /**
     * Encrypt sensitive data before storage
     */
    public String encrypt(String sensitiveData) throws Exception {
        SecretKey key = keyService.getMasterKey();
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        
        byte[] encryptedData = cipher.doFinal(sensitiveData.getBytes());
        return Base64.getEncoder().encodeToString(encryptedData);
    }
    
    /**
     * Decrypt only when needed
     */
    public String decrypt(String encryptedData) throws Exception {
        SecretKey key = keyService.getMasterKey();
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, key);
        
        byte[] decodedData = Base64.getDecoder().decode(encryptedData);
        byte[] decryptedData = cipher.doFinal(decodedData);
        return new String(decryptedData);
    }
}
```

### Option 4B: Database Encryption

```java
@Entity
public class UserQuery {
    
    @Id
    private String id;
    
    private String userId;
    
    // Sensitive query stored encrypted
    @Column(columnDefinition = "VARBINARY(MAX)")
    @Convert(converter = EncryptedStringConverter.class)
    private String encryptedQuery;
    
    // Non-sensitive version for LLM
    private String redactedQuery;
    
    // Timestamp for TTL
    private LocalDateTime createdAt;
}
```

### Recommendation
- ✅ Use field-level encryption for highly sensitive data
- ✅ Use database encryption for all user data
- ✅ Use TLS/HTTPS for transmission
- ✅ Use key rotation policies

---

## Layer 5: Response Sanitization

### Option 5: Clean Response Before Returning

```java
@Service
@Slf4j
public class ResponseSanitizer {
    
    private final SensitiveDataDetector detector;
    private final SensitiveDataRedactor redactor;
    
    /**
     * Clean response before returning to user
     */
    public String sanitizeResponse(String response, String userId) {
        // Detect any sensitive data that might have leaked
        SensitiveDataDetectionResult detections = detector.detect(response);
        
        if (detections.hasDetections()) {
            log.warn("Sensitive data detected in LLM response for user: {}", userId);
            
            // Redact before returning
            String sanitized = redactor.redactCompletely(response, detections);
            
            // Log incident
            incidentLog.log(
                "PII_LEAK_PREVENTED",
                userId,
                "Response contained: " + detections.detections.keySet()
            );
            
            return sanitized;
        }
        
        return response;
    }
}
```

---

## Complete Implementation

### Step 1: Create PII Detection Service

```java
@Service
@Slf4j
public class PIIDetectionService {
    
    private final SensitiveDataDetector detector;
    private final SensitiveDataRedactor redactor;
    
    /**
     * Analyze query for PII/sensitive data
     */
    public PIIAnalysisResult analyzeQuery(String query) {
        log.debug("Analyzing query for PII");
        
        SensitiveDataDetectionResult detections = detector.detect(query);
        
        return PIIAnalysisResult.builder()
            .query(query)
            .hasSensitiveData(detections.hasDetections())
            .detectedTypes(detections.getDetections().keySet())
            .redactedQuery(redactor.redactCompletely(query, detections))
            .severity(calculateSeverity(detections))
            .build();
    }
    
    private RiskSeverity calculateSeverity(SensitiveDataDetectionResult detections) {
        // HIGH: Credit card, SSN, API keys
        // MEDIUM: Email, phone
        // LOW: Generic PII
        
        Set<String> types = detections.getDetections().keySet();
        
        if (types.contains("CREDIT_CARD") || 
            types.contains("SSN") || 
            types.contains("API_KEY")) {
            return RiskSeverity.HIGH;
        }
        
        if (types.contains("EMAIL") || types.contains("PHONE")) {
            return RiskSeverity.MEDIUM;
        }
        
        return RiskSeverity.LOW;
    }
}

@Data
@Builder
public class PIIAnalysisResult {
    private String query;
    private boolean hasSensitiveData;
    private Set<String> detectedTypes;
    private String redactedQuery;
    private RiskSeverity severity;
}

public enum RiskSeverity {
    LOW,
    MEDIUM,
    HIGH
}
```

### Step 2: Update RAGOrchestrator

```java
@Service
@Slf4j
public class SecureRAGOrchestrator extends RAGOrchestrator {
    
    private final PIIDetectionService piiDetectionService;
    private final ResponseSanitizer responseSanitizer;
    
    @Override
    public OrchestrationResult orchestrate(String rawQuery, String userId) {
        // Step 1: Analyze for PII
        PIIAnalysisResult piiAnalysis = piiDetectionService.analyzeQuery(rawQuery);
        
        if (piiAnalysis.hasSensitiveData()) {
            log.warn("PII detected in query from user: {}. Severity: {}", 
                userId, piiAnalysis.getSeverity());
            
            // Handle high-risk cases
            if (piiAnalysis.getSeverity() == RiskSeverity.HIGH) {
                return handleHighRiskQuery(piiAnalysis, userId);
            }
            
            // Use redacted query for processing
            rawQuery = piiAnalysis.getRedactedQuery();
        }
        
        // Step 2: Process normally
        OrchestrationResult result = super.orchestrate(rawQuery, userId);
        
        // Step 3: Sanitize response
        if (result.getMessage() != null) {
            result.setMessage(
                responseSanitizer.sanitizeResponse(result.getMessage(), userId)
            );
        }
        
        return result;
    }
    
    private OrchestrationResult handleHighRiskQuery(
        PIIAnalysisResult piiAnalysis,
        String userId) {
        
        log.error("HIGH RISK PII detected: {}. User: {}. Types: {}", 
            piiAnalysis.getDetectedTypes(), userId);
        
        // Alert security team
        securityAlertService.triggerAlert(
            "HIGH_RISK_PII_DETECTED",
            userId,
            piiAnalysis.getDetectedTypes()
        );
        
        // Don't process - return safe message
        return OrchestrationResult.failure(
            "We detected sensitive information in your request. " +
            "Please use our secure forms to submit sensitive data. " +
            "The security team has been notified."
        );
    }
}
```

### Step 3: Configuration

```yaml
security:
  pii:
    # Detection
    enabled: true
    detection-strategy: pattern  # pattern or ml
    
    # Handling
    redaction-strategy: complete  # complete or partial
    mask-pii-in-logs: true
    mask-pii-in-audit-trails: true
    
    # High-risk actions
    high-risk-types:
      - CREDIT_CARD
      - SSN
      - API_KEY
      - DB_PASSWORD
    
    # Encryption
    encryption:
      enabled: true
      algorithm: AES-256
      key-rotation-days: 90
    
    # Logging & Monitoring
    incident-logging: true
    security-team-alerts: true
    pii-retention-days: 7  # Auto-delete after 7 days
```

---

## Decision Tree: What to Do

```
Query contains PII?
    ↓
NO → Process normally
    └─ No additional checks needed
    
YES → Analyze severity
    ├─ LOW (generic info)
    │   └─ Mask in logs, process with redaction
    │
    ├─ MEDIUM (email, phone)
    │   └─ Redact, process, sanitize response
    │
    └─ HIGH (credit card, SSN, API key)
        ├─ Reject with error message
        ├─ Log incident
        ├─ Alert security team
        └─ Don't process further
```

---

## Best Practices

### ✅ DO

1. **Detect early** - Before any processing
2. **Redact at entry** - Don't store raw PII
3. **Encrypt in database** - Field-level or full DB
4. **Log redacted** - Never log actual values
5. **Sanitize output** - Clean responses
6. **Alert on high-risk** - Notify security team
7. **Rotate keys** - Regular key rotation
8. **Audit everything** - Track all PII access
9. **TTL on storage** - Auto-delete sensitive data
10. **Test thoroughly** - PII handling edge cases

### ❌ DON'T

1. ❌ Store PII unencrypted
2. ❌ Log raw PII values
3. ❌ Send raw PII to LLM
4. ❌ Return PII in responses
5. ❌ Trust client-side validation
6. ❌ Keep PII forever
7. ❌ Process without detection
8. ❌ Ignore high-risk patterns
9. ❌ Skip security alerts
10. ❌ Forget about compliance

---

## Compliance & Regulations

### GDPR Requirements
```
✅ Detect personal data
✅ Encrypt in transit & at rest
✅ Minimize data collection
✅ Right to be forgotten
✅ Data access logs
```

### CCPA Requirements
```
✅ Know what PII you have
✅ Encrypt sensitive data
✅ User privacy rights
✅ Opt-out mechanisms
✅ Breach notification
```

### PCI-DSS Requirements (if handling payments)
```
✅ Never store full card numbers
✅ Tokenize payments
✅ Encrypt transmission
✅ Access controls
✅ Audit logs
```

### HIPAA Requirements (if handling health data)
```
✅ Encrypt health information
✅ Access controls
✅ Audit trails
✅ Data integrity
✅ Breach notification
```

---

## Testing Strategy

### Test 1: Detection Works
```java
@Test
void shouldDetectCreditCard() {
    String query = "My card is 4532-1234-5678-9012";
    PIIAnalysisResult result = piiDetectionService.analyzeQuery(query);
    
    assertThat(result.hasSensitiveData()).isTrue();
    assertThat(result.getDetectedTypes()).contains("CREDIT_CARD");
}
```

### Test 2: Redaction Works
```java
@Test
void shouldRedactCreditCard() {
    String query = "My card is 4532-1234-5678-9012";
    String redacted = piiDetectionService.analyzeQuery(query).getRedactedQuery();
    
    assertThat(redacted).doesNotContain("4532");
    assertThat(redacted).contains("REDACTED");
}
```

### Test 3: Response Cleaned
```java
@Test
void shouldSanitizeResponse() {
    String response = "Your card 4532-1234-5678-9012 is updated";
    String sanitized = responseSanitizer.sanitizeResponse(response, "user-123");
    
    assertThat(sanitized).doesNotContain("4532");
}
```

### Test 4: No Leaks to LLM
```java
@Test
void shouldNotSendPIItoLLM() {
    String query = "My card is 4532-1234-5678-9012";
    
    // Mock LLM call
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    
    orchestrator.orchestrate(query, "user-123");
    
    // Verify LLM never saw raw card number
    verify(aiCoreService).generateText(captor.capture(), any());
    assertThat(captor.getValue()).doesNotContain("4532");
}
```

---

## Summary: 5-Layer Defense

| Layer | Purpose | How |
|-------|---------|-----|
| 1 | Detect | Pattern + ML-based detection |
| 2 | Redact | Mask/remove sensitive data |
| 3 | Route | Handle carefully, redact before LLM |
| 4 | Store | Encrypt at rest & in transit |
| 5 | Output | Sanitize responses before return |

---

## Implementation Priority

### Phase 1: Essential (Do First)
```
✅ Layer 1: Detection (pattern-based)
✅ Layer 2: Redaction in logs
✅ Layer 3: Basic routing
✅ Layer 5: Response sanitization
```

### Phase 2: Important (Do Soon)
```
✅ Layer 4: Encryption
✅ High-risk alerting
✅ Incident logging
```

### Phase 3: Advanced (Do Later)
```
✅ ML-based detection
✅ Key rotation
✅ Advanced analytics
```

---

## Conclusion

**Defense in Depth Strategy:**
- Multiple layers = no single failure point
- Pattern detection for speed
- Encryption for protection
- Redaction for safety
- Sanitization for assurance

**This is enterprise security!** 🔒

---

## Files Needed

1. `PIIDetectionService.java` - Detection logic
2. `SensitiveDataDetector.java` - Pattern matching
3. `SensitiveDataRedactor.java` - Redaction
4. `ResponseSanitizer.java` - Output cleaning
5. `SecureRAGOrchestrator.java` - Integration
6. `PIIAnalysisResult.java` - DTO
7. Tests for all components

**Total: ~1500 lines of code**
**Time: 8-10 hours implementation**

---

## Next Steps

1. ✅ Implement Layer 1 (Detection)
2. ✅ Implement Layer 2 (Redaction)
3. ✅ Implement Layer 3 (Routing)
4. ✅ Implement Layer 4 (Encryption)
5. ✅ Implement Layer 5 (Sanitization)
6. ✅ Write comprehensive tests
7. ✅ Deploy with monitoring
8. ✅ Regular security audits

**Start with Layers 1-3 this week, then add 4-5!** 🚀

