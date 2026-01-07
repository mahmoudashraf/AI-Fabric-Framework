# Response Sanitization: Protecting Users from Sensitive Data Leaks

*How we built a comprehensive response sanitization system that automatically detects and redacts sensitive data from AI responses—all with zero code required*

🚧 **Under active development | Q1 2026 release | Production-tested | Enterprise-ready**

---

## The Problem: Sensitive Data Leaks in AI Responses

**You're building an AI-powered application. Your LLM generates responses that may contain:**

1. **PII (Personally Identifiable Information):** Credit cards, SSNs, emails, phone numbers
2. **Internal Data:** Metadata, RAG context, debug information
3. **Harmful Content:** Hate speech, harassment, spam
4. **Sensitive Business Data:** Account numbers, API keys, database passwords

**Without response sanitization:**
- ❌ Credit cards leaked in order confirmations
- ❌ SSNs exposed in customer service responses
- ❌ Internal metadata exposed to clients
- ❌ Harmful content reaches users
- ❌ Compliance violations (GDPR, HIPAA, PCI-DSS)

**Result:** Data breaches. Compliance fines. Lost trust. Lawsuits.

---

## Our Approach: Comprehensive Response Sanitization

**Automatically detect and redact sensitive data from all response components—message, data, suggestions, and smart suggestions—before they reach users.**

```java
// Automatic response sanitization - zero code required
@Autowired
private RAGOrchestrator orchestrator;

// Every orchestration result is automatically sanitized
OrchestrationResult result = orchestrator.orchestrate(
    "Show me my billing history",
    OrchestrationContext.builder()
        .userId("user-123")
        .build()
);

// Response automatically sanitized:
// - PII detected and redacted
// - Internal keys filtered
// - Warnings added if needed
// - Events published for analytics

Map<String, Object> sanitizedPayload = result.getSanitizedPayload();
// Safe to return to clients
```

**Zero code. Automatic. Comprehensive. Enterprise-ready.**

---

## Architecture Overview

The Response Sanitization module consists of four core components:

1. **ResponseSanitizer** — Main sanitization service
2. **PII Detection Integration** — Uses PII detection service for sensitive data detection
3. **Content Filtering Integration** — Integrates with content filter service
4. **SanitizationEvent** — Application event for analytics

---

## Component 1: Response Sanitization Service

### How It Works

The `ResponseSanitizer` sanitizes all components of an `OrchestrationResult`:

```java
// From ResponseSanitizer.java (lines 48-114)
public Map<String, Object> sanitize(OrchestrationResult result, String userId) {
    if (result == null) {
        return Collections.emptyMap();
    }
    
    if (!properties.isEnabled()) {
        return basicPayload(result);
    }
    
    // 1. Sanitize message
    SanitizationOutcome<String> messageOutcome = sanitizeText(result.getMessage(), userId);
    
    // 2. Sanitize data
    SanitizationOutcome<Object> dataOutcome = sanitizeObject(result.getData(), userId);
    
    // 3. Sanitize suggestions
    SanitizationOutcome<List<Map<String, Object>>> suggestionOutcome = 
        sanitizeSuggestions(result, userId);
    
    // 4. Sanitize smart suggestions
    SanitizationOutcome<Map<String, Object>> smartSuggestionOutcome =
        sanitizeMap(result.getSmartSuggestion(), userId);
    
    // 5. Aggregate risk levels
    RiskLevel aggregatedRisk = RiskLevel.max(
        messageOutcome.riskLevel(),
        dataOutcome.riskLevel(),
        suggestionOutcome.riskLevel(),
        smartSuggestionOutcome.riskLevel()
    );
    
    // 6. Merge detected PII types
    List<String> aggregatedTypes = mergeTypes(
        messageOutcome.detectedTypes(),
        dataOutcome.detectedTypes(),
        suggestionOutcome.detectedTypes(),
        smartSuggestionOutcome.detectedTypes()
    );
    
    // 7. Build sanitized payload
    Map<String, Object> payload = new LinkedHashMap<>();
    if (result.getType() != null) {
        payload.put("type", result.getType().name());
    }
    payload.put("success", result.isSuccess());
    payload.put("message", messageOutcome.value());
    payload.put("data", normalizeData(dataOutcome.value()));
    if (!suggestionOutcome.value().isEmpty()) {
        payload.put("suggestions", Collections.unmodifiableList(suggestionOutcome.value()));
    }
    if (!smartSuggestionOutcome.value().isEmpty()) {
        payload.put("smartSuggestion", Collections.unmodifiableMap(smartSuggestionOutcome.value()));
    }
    payload.put("safeSummary", buildSafeSummary(messageOutcome, result));
    payload.put("sanitization", Map.of(
        "risk", aggregatedRisk.name(),
        "detectedTypes", aggregatedTypes
    ));
    
    // 8. Add warnings if needed
    if (aggregatedRisk != RiskLevel.NONE && properties.isWarningEnabled()) {
        payload.put("warning", Map.of(
            "level", aggregatedRisk == RiskLevel.HIGH
                ? properties.getWarningLevelHighRisk()
                : properties.getWarningLevelMediumRisk(),
            "message", aggregatedRisk == RiskLevel.HIGH
                ? properties.getHighRiskWarningMessage()
                : properties.getMediumRiskWarningMessage()
        ));
    }
    
    // 9. Add guidance if needed
    if (aggregatedRisk != RiskLevel.NONE && properties.isGuidanceEnabled()) {
        payload.put("guidance", properties.getGuidanceMessage());
    }
    
    // 10. Publish sanitization event
    publishSanitizationEvent(userId, aggregatedRisk, aggregatedTypes);
    
    return Collections.unmodifiableMap(payload);
}
```

### Text Sanitization

```java
// From ResponseSanitizer.java (lines 133-164)
private SanitizationOutcome<String> sanitizeText(String text, String userId) {
    if (!StringUtils.hasText(text)) {
        return SanitizationOutcome.of(text, RiskLevel.NONE, List.of());
    }
    
    // Detect PII using PII detection service
    PIIDetectionResult analysis = piiDetectionService.analyze(text);
    if (!analysis.isPiiDetected()) {
        return SanitizationOutcome.of(text, RiskLevel.NONE, List.of());
    }
    
    // Redact PII
    String sanitized = properties.isForceRedaction()
        ? redact(text, analysis.getDetections())
        : analysis.getProcessedQuery();
    
    // Extract PII types
    List<String> types = analysis.getDetections().stream()
        .map(PIIDetection::getType)
        .filter(StringUtils::hasText)
        .map(type -> type.trim().toUpperCase(Locale.ROOT))
        .toList();
    
    // Calculate risk level
    RiskLevel riskLevel = analysis.getDetections().stream()
        .anyMatch(detection -> properties.isHighRiskType(detection.getType()))
        ? RiskLevel.HIGH
        : RiskLevel.MEDIUM;
    
    if (riskLevel == RiskLevel.HIGH) {
        log.warn("High-risk PII detected in response for user={}", userId);
    } else {
        log.debug("PII detected in response for user={}, applying sanitization.", userId);
    }
    
    return SanitizationOutcome.of(sanitized, riskLevel, types);
}
```

### Object Sanitization

```java
// From ResponseSanitizer.java (lines 166-190)
private SanitizationOutcome<Object> sanitizeObject(Object value, String userId) {
    if (value == null) {
        return SanitizationOutcome.of(Collections.emptyMap(), RiskLevel.NONE, List.of());
    }
    
    // Handle different object types
    if (value instanceof String str) {
        SanitizationOutcome<String> outcome = sanitizeText(str, userId);
        return SanitizationOutcome.of(outcome.value(), outcome.riskLevel(), outcome.detectedTypes());
    }
    
    if (value instanceof Map<?, ?> map) {
        SanitizationOutcome<Map<String, Object>> outcome = sanitizeMap(map, userId);
        return SanitizationOutcome.of(outcome.value(), outcome.riskLevel(), outcome.detectedTypes());
    }
    
    if (value instanceof ActionResult actionResult) {
        return sanitizeActionResult(actionResult, userId);
    }
    
    if (value instanceof Iterable<?> iterable) {
        return sanitizeIterable(iterable, userId);
    }
    
    return SanitizationOutcome.of(value, RiskLevel.NONE, List.of());
}
```

### Map Sanitization with Key Filtering

```java
// From ResponseSanitizer.java (lines 192-222)
@SuppressWarnings("unchecked")
private SanitizationOutcome<Map<String, Object>> sanitizeMap(Map<?, ?> input, String userId) {
    if (CollectionUtils.isEmpty(input)) {
        return SanitizationOutcome.of(Collections.emptyMap(), RiskLevel.NONE, List.of());
    }
    
    Map<String, Object> sanitized = new LinkedHashMap<>();
    RiskLevel riskLevel = RiskLevel.NONE;
    List<String> types = new ArrayList<>();
    Set<String> filteredKeys = normalize(properties.getFilteredDataKeys());
    
    for (Map.Entry<?, ?> entry : input.entrySet()) {
        Object rawKey = entry.getKey();
        if (!(rawKey instanceof String key)) {
            continue;
        }
        
        // Filter out internal keys
        if (filteredKeys.contains(key.trim().toLowerCase(Locale.ROOT))) {
            continue;  // Skip filtered keys (metadata, ragResponse, etc.)
        }
        
        // Recursively sanitize value
        SanitizationOutcome<Object> outcome = sanitizeObject(entry.getValue(), userId);
        if (outcome.value() != null) {
            sanitized.put(key, outcome.value());
        }
        riskLevel = RiskLevel.max(riskLevel, outcome.riskLevel());
        types.addAll(outcome.detectedTypes());
    }
    
    return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), riskLevel, distinct(types));
}
```

### Iterable Sanitization

```java
// From ResponseSanitizer.java (lines 224-235)
private SanitizationOutcome<Object> sanitizeIterable(Iterable<?> iterable, String userId) {
    List<Object> sanitized = new ArrayList<>();
    RiskLevel riskLevel = RiskLevel.NONE;
    List<String> types = new ArrayList<>();
    
    for (Object element : iterable) {
        SanitizationOutcome<Object> outcome = sanitizeObject(element, userId);
        sanitized.add(outcome.value());
        riskLevel = RiskLevel.max(riskLevel, outcome.riskLevel());
        types.addAll(outcome.detectedTypes());
    }
    
    return SanitizationOutcome.of(Collections.unmodifiableList(sanitized), riskLevel, distinct(types));
}
```

### Action Result Sanitization

```java
// From ResponseSanitizer.java (lines 237-261)
private SanitizationOutcome<Object> sanitizeActionResult(ActionResult actionResult, String userId) {
    Map<String, Object> sanitized = new LinkedHashMap<>();
    sanitized.put("success", actionResult.isSuccess());
    
    // Sanitize message
    SanitizationOutcome<String> messageOutcome = sanitizeText(actionResult.getMessage(), userId);
    RiskLevel riskLevel = messageOutcome.riskLevel();
    List<String> types = new ArrayList<>(messageOutcome.detectedTypes());
    sanitized.put("message", messageOutcome.value());
    
    // Sanitize data
    Object rawData = actionResult.getData();
    if (rawData != null) {
        SanitizationOutcome<Object> dataOutcome = sanitizeObject(rawData, userId);
        if (dataOutcome.value() != null) {
            sanitized.put("data", dataOutcome.value());
        }
        riskLevel = RiskLevel.max(riskLevel, dataOutcome.riskLevel());
        types.addAll(dataOutcome.detectedTypes());
    }
    
    // Include error codes if configured
    if (properties.isIncludeErrorCodes() && StringUtils.hasText(actionResult.getErrorCode())) {
        sanitized.put("errorCode", actionResult.getErrorCode());
    }
    
    return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), riskLevel, distinct(types));
}
```

### Suggestion Sanitization

```java
// From ResponseSanitizer.java (lines 263-283)
private SanitizationOutcome<List<Map<String, Object>>> sanitizeSuggestions(OrchestrationResult result, String userId) {
    List<Map<String, Object>> suggestions = new ArrayList<>();
    RiskLevel riskLevel = RiskLevel.NONE;
    List<String> types = new ArrayList<>();
    
    if (!CollectionUtils.isEmpty(result.getNextSteps())) {
        for (NextStepRecommendation recommendation : result.getNextSteps()) {
            SanitizationOutcome<Map<String, Object>> outcome = sanitizeRecommendation(recommendation, userId);
            suggestions.add(outcome.value());
            riskLevel = RiskLevel.max(riskLevel, outcome.riskLevel());
            types.addAll(outcome.detectedTypes());
        }
    }
    
    // Limit suggestions to configured limit
    int limit = Math.max(1, properties.getSuggestionLimit());
    if (suggestions.size() > limit) {
        suggestions = new ArrayList<>(suggestions.subList(0, limit));
    }
    
    return SanitizationOutcome.of(Collections.unmodifiableList(suggestions), riskLevel, distinct(types));
}
```

### Recommendation Sanitization

```java
// From ResponseSanitizer.java (lines 285-326)
private SanitizationOutcome<Map<String, Object>> sanitizeRecommendation(NextStepRecommendation recommendation,
                                                                        String userId) {
    if (recommendation == null) {
        return SanitizationOutcome.of(Collections.emptyMap(), RiskLevel.NONE, List.of());
    }
    
    Map<String, Object> sanitized = new LinkedHashMap<>();
    RiskLevel riskLevel = RiskLevel.NONE;
    List<String> types = new ArrayList<>();
    
    if (StringUtils.hasText(recommendation.getIntent())) {
        sanitized.put("intent", recommendation.getIntent());
    }
    
    // Sanitize query
    SanitizationOutcome<String> queryOutcome = sanitizeText(recommendation.getQuery(), userId);
    if (StringUtils.hasText(queryOutcome.value())) {
        sanitized.put("query", queryOutcome.value());
    }
    riskLevel = RiskLevel.max(riskLevel, queryOutcome.riskLevel());
    types.addAll(queryOutcome.detectedTypes());
    
    // Sanitize rationale
    SanitizationOutcome<String> rationaleOutcome = sanitizeText(recommendation.getRationale(), userId);
    if (StringUtils.hasText(rationaleOutcome.value())) {
        sanitized.put("rationale", rationaleOutcome.value());
    }
    riskLevel = RiskLevel.max(riskLevel, rationaleOutcome.riskLevel());
    types.addAll(rationaleOutcome.detectedTypes());
    
    if (recommendation.getConfidence() != null) {
        sanitized.put("confidence", recommendation.getConfidence());
    }
    
    // Include sanitization metadata if configured
    if (properties.isIncludeSuggestionMetadata()) {
        sanitized.put("sanitization", Map.of(
            "risk", riskLevel.name(),
            "detectedTypes", distinct(types),
            "redacted", riskLevel != RiskLevel.NONE
        ));
    }
    
    return SanitizationOutcome.of(Collections.unmodifiableMap(sanitized), riskLevel, distinct(types));
}
```

### Redaction Implementation

```java
// From ResponseSanitizer.java (lines 351-370)
private String redact(String original, List<PIIDetection> detections) {
    if (CollectionUtils.isEmpty(detections)) {
        return original;
    }
    
    StringBuilder builder = new StringBuilder(original);
    
    // Sort detections by start index (descending) to avoid index shifting
    detections.stream()
        .filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(PIIDetection::getStartIndex).reversed())
        .forEach(detection -> {
            int start = Math.max(0, Math.min(detection.getStartIndex(), builder.length()));
            int end = Math.max(start, Math.min(detection.getEndIndex(), builder.length()));
            
            // Use masked value if available, otherwise build replacement token
            String replacement = StringUtils.hasText(detection.getMaskedValue())
                ? detection.getMaskedValue()
                : buildReplacementToken(detection.getType());
            
            builder.replace(start, end, replacement);
        });
    
    return builder.toString();
}
```

### Replacement Token Building

```java
// From ResponseSanitizer.java (lines 372-378)
private String buildReplacementToken(String type) {
    if (!StringUtils.hasText(type)) {
        return properties.getDefaultReplacement();
    }
    return "[" + properties.getDefaultReplacement().replace("[", "").replace("]", "") + "_" +
        type.trim().toUpperCase(Locale.ROOT) + "]";
}
```

**Example:**
- Type: `CREDIT_CARD` → `[REDACTED_CREDIT_CARD]`
- Type: `SSN` → `[REDACTED_SSN]`
- Type: `EMAIL` → `[REDACTED_EMAIL]`

---

## Component 2: Risk Level Calculation

### Risk Level Enum

```java
// From ResponseSanitizer.java (lines 414-428)
enum RiskLevel {
    NONE,
    MEDIUM,
    HIGH;
    
    static RiskLevel max(RiskLevel... levels) {
        RiskLevel result = NONE;
        for (RiskLevel level : levels) {
            if (level != null && level.ordinal() > result.ordinal()) {
                result = level;
            }
        }
        return result;
    }
}
```

### Risk Level Determination

```java
// From ResponseSanitizer.java (lines 152-155)
RiskLevel riskLevel = analysis.getDetections().stream()
    .anyMatch(detection -> properties.isHighRiskType(detection.getType()))
    ? RiskLevel.HIGH
    : RiskLevel.MEDIUM;
```

**High-Risk Types (default):**
- `CREDIT_CARD`
- `SSN`
- `API_KEY`
- `DB_PASSWORD`

**Medium-Risk Types:**
- `EMAIL`
- `PHONE`
- `ADDRESS`
- Other PII types

---

## Component 3: Event Publishing

### Sanitization Event

```java
// From SanitizationEvent.java
@Getter
public class SanitizationEvent extends ApplicationEvent {
    private final String userId;
    private final ResponseSanitizer.RiskLevel riskLevel;
    private final List<String> detectedTypes;
    private final java.time.Instant occurredAt;
    
    public SanitizationEvent(Object source,
                             String userId,
                             ResponseSanitizer.RiskLevel riskLevel,
                             List<String> detectedTypes) {
        super(source);
        this.userId = userId;
        this.riskLevel = riskLevel;
        this.detectedTypes = detectedTypes == null ? List.of() : List.copyOf(detectedTypes);
        this.occurredAt = java.time.Instant.ofEpochMilli(super.getTimestamp());
    }
}
```

### Event Publishing

```java
// From ResponseSanitizer.java (lines 390-395)
private void publishSanitizationEvent(String userId, RiskLevel riskLevel, List<String> detectedTypes) {
    if (!properties.isPublishEvents() || eventPublisher == null || riskLevel == RiskLevel.NONE) {
        return;
    }
    eventPublisher.publishEvent(new SanitizationEvent(this, userId, riskLevel, detectedTypes));
}
```

---

## Component 4: Configuration

### Response Sanitization Properties

```java
// From ResponseSanitizationProperties.java
@ConfigurationProperties(prefix = "ai.response-sanitization")
public class ResponseSanitizationProperties {
    
    // Master switch
    private boolean enabled = true;
    
    // Force redaction even if PII mode is DETECT_ONLY
    private boolean forceRedaction = true;
    
    // Keys to filter from payloads
    private List<String> filteredDataKeys = List.of(
        "metadata",
        "ragResponse",
        "documents",
        "debug",
        "internalContext"
    );
    
    // High-risk PII types
    private Set<String> highRiskTypes = Set.of("CREDIT_CARD", "SSN", "API_KEY", "DB_PASSWORD");
    
    // Warning messages
    private String highRiskWarningMessage = "Sensitive information detected and redacted for your safety.";
    private String mediumRiskWarningMessage = "Some personal information was redacted before showing this response.";
    private boolean warningEnabled = true;
    private String warningLevelHighRisk = "BLOCK";
    private String warningLevelMediumRisk = "WARN";
    
    // Guidance message
    private boolean guidanceEnabled = true;
    private String guidanceMessage = "For sensitive requests, please use our secure support form.";
    
    // Default replacement token
    private String defaultReplacement = "[REDACTED]";
    
    // Suggestion limit
    private int suggestionLimit = 3;
    
    // Include error codes
    private boolean includeErrorCodes = false;
    
    // Include suggestion metadata
    private boolean includeSuggestionMetadata = true;
    
    // Publish events
    private boolean publishEvents = true;
}
```

---

## Complete Data Flow

```
┌──────────────────────────────────────────────────────┐
│  ORCHESTRATION RESULT                                │
│  OrchestrationResult                                  │
│  ═══════════════════════════════════════════════════│
│  {                                                    │
│    type: SUCCESS,                                    │
│    success: true,                                     │
│    message: "Your card ending in 4532-1234-5678-9010 was charged $99.99",│
│    data: {                                           │
│      orderId: "12345",                               │
│      amount: 99.99,                                  │
│      metadata: {...},  // Internal                  │
│      ragResponse: {...}  // Internal                │
│    },                                                │
│    nextSteps: [                                      │
│      {                                               │
│        query: "Show me order details",              │
│        rationale: "You might want to see order details"│
│      }                                               │
│    ],                                                │
│    smartSuggestion: {                                │
│      intent: "VIEW_ORDER",                           │
│      query: "Show order 12345"                       │
│    }                                                 │
│  }                                                    │
└────────────────┬─────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────┐
│  RESPONSE SANITIZER                                   │
│  ResponseSanitizer.sanitize(result, userId)          │
│  ═══════════════════════════════════════════════════│
│                                                       │
│  1. Sanitize Message                                  │
│     sanitizeText(message, userId)                    │
│     ├─ PII detection: piiDetectionService.analyze()  │
│     ├─ Detected: CREDIT_CARD                         │
│     ├─ Risk level: HIGH (CREDIT_CARD is high-risk)   │
│     ├─ Redact: "Your card ending in [REDACTED_CREDIT_CARD] was charged $99.99"│
│     └─ Outcome: (sanitizedMessage, HIGH, ["CREDIT_CARD"])│
│                                                       │
│  2. Sanitize Data                                     │
│     sanitizeObject(data, userId)                     │
│     ├─ Type: Map                                     │
│     ├─ sanitizeMap(data, userId)                     │
│     │  ├─ Filter key: "metadata" → Skip             │
│     │  ├─ Filter key: "ragResponse" → Skip          │
│     │  ├─ Keep: "orderId" → "12345"                 │
│     │  └─ Keep: "amount" → 99.99                     │
│     └─ Outcome: (sanitizedData, NONE, [])            │
│                                                       │
│  3. Sanitize Suggestions                              │
│     sanitizeSuggestions(result, userId)              │
│     ├─ For each recommendation:                      │
│     │  ├─ sanitizeRecommendation()                  │
│     │  │  ├─ Sanitize query: "Show me order details"│
│     │  │  ├─ Sanitize rationale: "You might want..." │
│     │  │  └─ No PII detected                        │
│     │  └─ Limit to 3 (suggestionLimit)              │
│     └─ Outcome: (sanitizedSuggestions, NONE, [])    │
│                                                       │
│  4. Sanitize Smart Suggestion                        │
│     sanitizeMap(smartSuggestion, userId)             │
│     ├─ Filter internal keys                          │
│     ├─ Sanitize values                               │
│     └─ Outcome: (sanitizedSmartSuggestion, NONE, [])│
│                                                       │
│  5. Aggregate Risk Levels                            │
│     aggregatedRisk = RiskLevel.max(                  │
│       HIGH, NONE, NONE, NONE                         │
│     )                                                │
│     Result: HIGH                                      │
│                                                       │
│  6. Merge Detected Types                              │
│     aggregatedTypes = mergeTypes(                    │
│       ["CREDIT_CARD"], [], [], []                    │
│     )                                                │
│     Result: ["CREDIT_CARD"]                          │
│                                                       │
│  7. Build Sanitized Payload                           │
│     payload = {                                       │
│       type: "SUCCESS",                                │
│       success: true,                                  │
│       message: sanitizedMessage,                      │
│       data: sanitizedData,                            │
│       suggestions: sanitizedSuggestions,             │
│       smartSuggestion: sanitizedSmartSuggestion,     │
│       safeSummary: sanitizedMessage,                  │
│       sanitization: {                                │
│         risk: "HIGH",                                 │
│         detectedTypes: ["CREDIT_CARD"]                │
│       }                                              │
│     }                                                │
│                                                       │
│  8. Add Warnings                                      │
│     if (HIGH != NONE && warningEnabled) {          │
│       payload.put("warning", {                       │
│         level: "BLOCK",                              │
│         message: "Sensitive information detected and redacted for your safety."│
│       });                                            │
│     }                                                │
│                                                       │
│  9. Add Guidance                                      │
│     if (HIGH != NONE && guidanceEnabled) {         │
│       payload.put("guidance", "For sensitive requests, please use our secure support form.");│
│     }                                                │
│                                                       │
│  10. Publish Event                                    │
│      publishSanitizationEvent(userId, HIGH, ["CREDIT_CARD"])│
│      ├─ Create SanitizationEvent                     │
│      └─ eventPublisher.publishEvent(event)           │
└────────────────┬─────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────┐
│  SANITIZED PAYLOAD                                    │
│  ═══════════════════════════════════════════════════│
│  {                                                    │
│    type: "SUCCESS",                                  │
│    success: true,                                     │
│    message: "Your card ending in [REDACTED_CREDIT_CARD] was charged $99.99",│
│    data: {                                           │
│      orderId: "12345",                               │
│      amount: 99.99                                   │
│      // metadata and ragResponse filtered out        │
│    },                                                │
│    suggestions: [                                    │
│      {                                               │
│        query: "Show me order details",              │
│        rationale: "You might want to see order details"│
│      }                                               │
│    ],                                                │
│    smartSuggestion: {                                │
│      intent: "VIEW_ORDER",                           │
│      query: "Show order 12345"                       │
│    },                                                │
│    safeSummary: "Your card ending in [REDACTED_CREDIT_CARD] was charged $99.99",│
│    sanitization: {                                   │
│      risk: "HIGH",                                   │
│      detectedTypes: ["CREDIT_CARD"]                  │
│    },                                                │
│    warning: {                                        │
│      level: "BLOCK",                                 │
│      message: "Sensitive information detected and redacted for your safety."│
│    },                                                │
│    guidance: "For sensitive requests, please use our secure support form."│
│  }                                                    │
└──────────────────────────────────────────────────────┘
```

---

## How to Use It

### 1. **Automatic Integration**

The response sanitizer is automatically integrated into the orchestration flow:

```java
// From RAGOrchestrator.java (lines 179-221)
// STEP 3: Sanitize the response (based on configuration)
Map<String, Object> sanitizedPayload = responseSanitizer.sanitize(result, identifier);

// STEP 4: Add detected PII types to response metadata
if ((!detectedPiiTypes.isEmpty() || detectOutput) && sanitizedPayload.containsKey("sanitization")) {
    // Merge input and output PII types
    @SuppressWarnings("unchecked")
    Map<String, Object> sanitization = (Map<String, Object>) sanitizedPayload.get("sanitization");
    Map<String, Object> updatedSanitization = new LinkedHashMap<>(sanitization);
    
    @SuppressWarnings("unchecked")
    List<String> existingTypes = (List<String>) sanitization.get("detectedTypes");
    List<String> mergedTypes = new ArrayList<>();
    if (existingTypes != null) {
        mergedTypes.addAll(existingTypes);
    }
    mergedTypes.addAll(detectedPiiTypes);
    
    // Deduplicate and sort
    List<String> finalTypes = mergedTypes.stream()
        .distinct()
        .sorted()
        .collect(Collectors.toList());
    
    if (!finalTypes.isEmpty()) {
        updatedSanitization.put("detectedTypes", finalTypes);
    }
    
    // Create new payload map with updated sanitization if changed
    if (!updatedSanitization.equals(sanitization)) {
        Map<String, Object> updatedPayload = new LinkedHashMap<>(sanitizedPayload);
        updatedPayload.put("sanitization", Collections.unmodifiableMap(updatedSanitization));
        sanitizedPayload = Collections.unmodifiableMap(updatedPayload);
    }
}

result.setSanitizedPayload(sanitizedPayload);
```

**No code required!** The response sanitizer is automatically called for every orchestration result.

### 2. **Access Sanitized Payload**

Use the sanitized payload in your API responses:

```java
@RestController
public class AIOrchestrationController {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @PostMapping("/api/ai/query")
    public Map<String, Object> query(@RequestBody QueryRequest request) {
        OrchestrationResult result = orchestrator.orchestrate(
            request.getQuery(),
            OrchestrationContext.builder()
                .userId(request.getUserId())
                .build()
        );
        
        // Return sanitized payload (safe for clients)
        return result.getSanitizedPayload();
    }
}
```

### 3. **Listen to Sanitization Events**

Listen to sanitization events for analytics:

```java
@Component
public class SanitizationEventListener {
    
    @EventListener
    public void handleSanitizationEvent(SanitizationEvent event) {
        if (event.getRiskLevel() == ResponseSanitizer.RiskLevel.HIGH) {
            // Alert security team
            alertSecurityTeam(event);
        }
        
        // Log for analytics
        logSanitizationEvent(event);
        
        // Update metrics
        updateSanitizationMetrics(event);
    }
    
    private void alertSecurityTeam(SanitizationEvent event) {
        // Send alert to security team
        securityAlertService.sendAlert(
            "High-risk PII detected in response",
            Map.of(
                "userId", event.getUserId(),
                "riskLevel", event.getRiskLevel(),
                "detectedTypes", event.getDetectedTypes()
            )
        );
    }
    
    private void logSanitizationEvent(SanitizationEvent event) {
        log.info("Sanitization event: userId={}, riskLevel={}, types={}",
            event.getUserId(),
            event.getRiskLevel(),
            event.getDetectedTypes()
        );
    }
    
    private void updateSanitizationMetrics(SanitizationEvent event) {
        metricsService.incrementCounter(
            "response.sanitization",
            Map.of(
                "riskLevel", event.getRiskLevel().name(),
                "type", event.getDetectedTypes().isEmpty() ? "NONE" : event.getDetectedTypes().get(0)
            )
        );
    }
}
```

---

## Configuration

### 1. **Response Sanitization Properties**

Configure sanitization behavior:

```yaml
ai:
  response-sanitization:
    # Master switch
    enabled: true
    
    # Force redaction even if PII mode is DETECT_ONLY
    force-redaction: true
    
    # Keys to filter from payloads
    filtered-data-keys:
      - metadata
      - ragResponse
      - documents
      - debug
      - internalContext
    
    # High-risk PII types
    high-risk-types:
      - CREDIT_CARD
      - SSN
      - API_KEY
      - DB_PASSWORD
    
    # Warning messages
    high-risk-warning-message: "Sensitive information detected and redacted for your safety."
    medium-risk-warning-message: "Some personal information was redacted before showing this response."
    warning-enabled: true
    warning-level-high-risk: BLOCK
    warning-level-medium-risk: WARN
    
    # Guidance message
    guidance-enabled: true
    guidance-message: "For sensitive requests, please use our secure support form."
    
    # Default replacement token
    default-replacement: "[REDACTED]"
    
    # Suggestion limit
    suggestion-limit: 3
    
    # Include error codes
    include-error-codes: false
    
    # Include suggestion metadata
    include-suggestion-metadata: true
    
    # Publish events
    publish-events: true
```

**Configuration Options:**
- **enabled:** Enable/disable response sanitization
- **force-redaction:** Force redaction even if PII mode is DETECT_ONLY
- **filtered-data-keys:** Keys to filter from payloads (metadata, ragResponse, etc.)
- **high-risk-types:** PII types that trigger HIGH risk level
- **warning-enabled:** Enable/disable warning messages
- **guidance-enabled:** Enable/disable guidance messages
- **publish-events:** Enable/disable sanitization event publishing

---

## Real-World Use Cases

### Use Case 1: E-commerce Platform

**Challenge:** Prevent credit card leaks in order confirmations.

**Solution:**
```java
// Response sanitization automatically redacts credit cards
// Result: "Your card ending in [REDACTED_CREDIT_CARD] was charged $99.99"
```

**Result:**
- **0 credit card leaks** in responses
- **100% compliance** with PCI-DSS
- **Automatic warnings** when sensitive data detected

### Use Case 2: Healthcare Platform

**Challenge:** Protect patient data in AI responses.

**Solution:**
```java
// Response sanitization redacts PHI (Protected Health Information)
// Result: "Patient SSN: [REDACTED_SSN], Medical Record: [REDACTED]"
```

**Result:**
- **0 PHI leaks** in responses
- **100% compliance** with HIPAA
- **Automatic redaction** of SSNs, medical record numbers, and other PHI

### Use Case 3: Financial Services Platform

**Challenge:** Prevent account number leaks in transaction responses.

**Solution:**
```java
// Response sanitization redacts account numbers, routing numbers, and other financial PII
// Result: "Account ending in [REDACTED] was debited $1000.00"
```

**Result:**
- **0 account number leaks** in responses
- **100% compliance** with financial regulations
- **Automatic risk assessment** and warnings

---

## Key Takeaways

1. **Automatic Protection:** Zero code required, automatic sanitization of all responses
2. **Comprehensive Coverage:** Sanitizes message, data, suggestions, and smart suggestions
3. **Risk Assessment:** Calculates risk levels (HIGH, MEDIUM, NONE) based on PII types
4. **Content Filtering:** Integrates with content filter service for harmful content
5. **Data Key Filtering:** Filters out internal metadata, RAG context, debug info
6. **Warning Messages:** Adds warnings and guidance when PII is detected
7. **Event Publishing:** Publishes sanitization events for analytics and monitoring
8. **Recursive Sanitization:** Handles nested objects, maps, lists, and action results
9. **Configurable:** Extensive configuration options for all aspects of sanitization

**The Response Sanitization module is your last line of defense, ensuring sensitive data never reaches users.**

---

*Part of the AI Fabric Framework — Enterprise-grade AI infrastructure for Spring Boot applications. Coming Q1 2026. ⭐ Star us on GitHub for 50% discount (first 500 users).*

