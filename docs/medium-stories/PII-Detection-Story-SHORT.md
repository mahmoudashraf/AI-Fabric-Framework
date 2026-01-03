# 🔒 PII Detection: When "My SSN is 123-45-6789" Becomes Safe

*How we built automatic PII detection that redacts sensitive data before it reaches your LLM—GDPR-compliant by default, not as an afterthought*

🚧 **Under active development | Q1 2026 release | Production-tested privacy protection**

---

## The Problem: Users Share Sensitive Data

**User types in chat:**

```
"My SSN is 123-45-6789, email is john@example.com, 
and my credit card is 4532-1234-5678-9010"
```

**Without PII detection:**
- ❌ SSN sent to LLM (OpenAI, Anthropic, etc.)
- ❌ Email stored in logs
- ❌ Credit card in database
- ❌ GDPR violation
- ❌ Potential data breach
- ❌ Legal liability

**Every AI application's nightmare.**

---

## Our Approach: Automatic Detection & Redaction

**Framework detects PII automatically. Redacts before LLM. Encrypts for audit.**

```java
// User input
String query = "My SSN is 123-45-6789";

// Framework detects & redacts automatically
PIIDetectionResult result = piiDetectionService.detectAndProcess(query);

// Output: "My SSN is ***-**-****"
String safeQuery = result.getProcessedQuery();

// PII never reaches LLM or database
```

**From PIIDetectionService.java (line 73-126):**

```java
public PIIDetectionResult detectAndProcess(String query) {
    if (!properties.isEnabled()) {
        return buildResult(query, query, Collections.emptyList(), false, 
                          PIIMode.PASS_THROUGH, null);
    }
    
    PIIMode mode = properties.getMode();
    List<DetectionMatch> detections = detect(query);
    boolean hasPii = !detections.isEmpty();
    
    String processedQuery = query;
    if (hasPii && mode == PIIMode.REDACT) {
        processedQuery = redact(query, detections);
    }
    
    // Optional: Encrypt original for audit
    if (hasPii && properties.isStoreEncryptedOriginal()) {
        EncryptionPayload payload = securePayload(query);
        // Store encrypted version
    }
    
    return buildResult(query, processedQuery, detections, hasPii, mode, metadata);
}
```

**Result:** PII detected. Redacted. Never stored. GDPR-compliant.

---

## The 3 Detection Modes

**From PIIMode.java (actual enum):**

```java
public enum PIIMode {
    PASS_THROUGH,   // Skip detection (disabled)
    DETECT_ONLY,    // Detect but don't redact (logging)
    REDACT          // Detect and redact (production)
}
```

### 1. REDACT (Production Mode)

```
Input:  "My SSN is 123-45-6789"
    ↓
Detect: SSN found at position 11-22
    ↓
Redact: "My SSN is ***-**-****"
    ↓
Output: Safe text (PII removed)
```

**Use case:** Production environments, GDPR compliance

---

### 2. DETECT_ONLY (Logging Mode)

```
Input:  "My SSN is 123-45-6789"
    ↓
Detect: SSN found at position 11-22
    ↓
Log:    "PII detected: SSN at position 11-22"
    ↓
Output: Original text (unchanged)
```

**Use case:** Development, testing, compliance audits

---

### 3. PASS_THROUGH (Disabled)

```
Input:  "My SSN is 123-45-6789"
    ↓
Skip:   No detection
    ↓
Output: Original text (unchanged)
```

**Use case:** Internal systems, non-sensitive data

---

## The Complete Flow

```
User: "My SSN is 123-45-6789"
    ↓
┌──────────────────────────────────────────┐
│ ORCHESTRATOR ENTRY                        │
│ orchestrator.orchestrate(query, context) │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 1: PII DETECTION                    │
│ PIIDetectionService.detectAndProcess()   │
│ ════════════════════════════════════════│
│ 1. Check if enabled                      │
│ 2. Run detection patterns               │
│ 3. Find matches (SSN, email, etc.)      │
│ 4. Apply mode (REDACT/DETECT_ONLY)       │
│ 5. Encrypt original (if configured)     │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 2: PATTERN MATCHING                 │
│ detect() (line 207-225)                  │
│ ════════════════════════════════════════│
│ Patterns:                                │
│ ├─ SSN: \\b\\d{3}-?\\d{2}-?\\d{4}\\b   │
│ ├─ EMAIL: [A-Z0-9._%+-]+@[A-Z0-9.-]+... │
│ ├─ PHONE: (?:(?:\\+?\\d{1,3}...))       │
│ └─ CREDIT_CARD: (?<!\\d)(?:\\d[ -]?)... │
│                                          │
│ Match found:                             │
│ {                                        │
│   type: "SSN",                          │
│   startIndex: 11,                        │
│   endIndex: 22,                          │
│   maskedValue: "***-**-****"             │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 3: REDACTION                        │
│ redact() (line 236-252)                   │
│ ════════════════════════════════════════│
│ Original: "My SSN is 123-45-6789"        │
│                                          │
│ Replace "123-45-6789" with "***-**-****" │
│                                          │
│ Result: "My SSN is ***-**-****"          │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 4: ENCRYPTION (Optional)            │
│ securePayload() (line 254-289)            │
│ ════════════════════════════════════════│
│ If storeEncryptedOriginal = true:        │
│ ├─ Encrypt original with AES-GCM         │
│ ├─ Store encrypted version               │
│ └─ Store salt/IV for decryption          │
│                                          │
│ Original never stored in plain text      │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 5: RETURN RESULT                    │
│ PIIDetectionResult                        │
│ ════════════════════════════════════════│
│ {                                        │
│   originalQuery: "My SSN is 123-45-6789",│
│   processedQuery: "My SSN is ***-**-****",│
│   piiDetected: true,                    │
│   detections: [{                        │
│     type: "SSN",                         │
│     startIndex: 11,                      │
│     endIndex: 22,                        │
│     maskedValue: "***-**-****"           │
│   }],                                    │
│   modeApplied: "REDACT"                 │
│ }                                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│ STEP 6: CONTINUE WITH SAFE QUERY         │
│ RAGOrchestrator (line 111-136)           │
│ ════════════════════════════════════════│
│ processedQuery = result.getProcessedQuery();│
│                                          │
│ // Safe query sent to LLM                │
│ MultiIntentResponse intents =             │
│   intentQueryExtractor.extract(          │
│     processedQuery, context              │
│   );                                     │
│                                          │
│ // PII never reaches LLM                 │
└──────────────────────────────────────────┘
```

---

## Built-in Detection Patterns

**From PIIDetectionProperties.java (line 86-129):**

**Default patterns:**

1. **SSN (Social Security Number)**
   - Pattern: `\b\d{3}-?\d{2}-?\d{4}\b`
   - Replacement: `***-**-****`
   - Example: `123-45-6789` → `***-**-****`

2. **Email**
   - Pattern: `[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}`
   - Replacement: `***@***.***`
   - Example: `john@example.com` → `***@***.***`

3. **Phone Number**
   - Pattern: `(?:(?:\+?\d{1,3}[\s.-]?)?(?:\(\d{3}\)|\d{3})[\s.-]?\d{3}[\s.-]?\d{4})`
   - Replacement: `***-***-****`
   - Example: `(555) 123-4567` → `***-***-****`

4. **Credit Card**
   - Pattern: `(?<!\d)(?:\d[ -]?){13,16}(?!\d)`
   - Replacement: `****-****-****-****`
   - Example: `4532-1234-5678-9010` → `****-****-****-****`

5. **IBAN (International Bank Account)**
   - Pattern: `\b[A-Z]{2}\d{2}[A-Z0-9]{10,30}\b`
   - Replacement: `****IBAN****`
   - Example: `GB82WEST12345698765432` → `****IBAN****`

**All configurable. Add your own patterns.**

---

## Configuration

**From application.yml:**

```yaml
ai:
  pii-detection:
    enabled: true
    mode: REDACT                    # REDACT, DETECT_ONLY, PASS_THROUGH
    detection-direction: INPUT_OUTPUT  # INPUT, INPUT_OUTPUT
    store-encrypted-original: true
    encryption-secret: ${PII_ENCRYPTION_KEY}
    audit-logging-enabled: true
    
    patterns:
      SSN:
        enabled: true
        field-name: ssn
        regex: "\\b\\d{3}-?\\d{2}-?\\d{4}\\b"
        replacement: "***-**-****"
        confidence: 1.0
        context-note: "Social security number redacted"
      
      EMAIL:
        enabled: true
        field-name: email
        regex: "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"
        replacement: "***@***.***"
        confidence: 1.0
      
      # Add custom patterns
      PASSPORT:
        enabled: true
        field-name: passport_number
        regex: "\\b[A-Z]{1,2}\\d{6,9}\\b"
        replacement: "****PASSPORT****"
        confidence: 0.9
```

---

## Real-World Example: Healthcare Chatbot

**Challenge:** Patients share medical info, SSNs, insurance numbers.

**Configuration:**

```yaml
ai:
  pii-detection:
    enabled: true
    mode: REDACT
    detection-direction: INPUT_OUTPUT
    store-encrypted-original: true
    encryption-secret: ${PII_ENCRYPTION_KEY}
    
    patterns:
      SSN:
        enabled: true
        replacement: "***-**-****"
      MEDICAL_RECORD:
        enabled: true
        field-name: medical_record_number
        regex: "MRN-\\d{8}"
        replacement: "MRN-********"
      INSURANCE:
        enabled: true
        field-name: insurance_number
        regex: "INS-\\d{10}"
        replacement: "INS-**********"
```

**User input:**

```
"My SSN is 123-45-6789, MRN is MRN-12345678, 
and my insurance is INS-9876543210"
```

**Processed (sent to LLM):**

```
"My SSN is ***-**-****, MRN is MRN-********, 
and my insurance is INS-**********"
```

**Original encrypted and stored for audit (HIPAA compliance).**

**Result:** HIPAA-compliant. PII never in logs. Safe for LLM.

---

## Integration with Orchestrator

**From RAGOrchestrator.java (line 111-136):**

```java
// STEP 1: Detect & Redact PII in user query
boolean detectInput = piiDetectionProperties.isEnabled() &&
    (detectionDirection == INPUT || detectionDirection == INPUT_OUTPUT);

if (detectInput) {
    PIIDetectionResult queryPiiAnalysis = piiDetectionService.analyze(query);
    if (queryPiiAnalysis.isPiiDetected()) {
        detectedPiiTypes = queryPiiAnalysis.getDetections().stream()
            .map(PIIDetection::getType)
            .distinct()
            .collect(Collectors.toList());
        log.info("PII detected in user query - types: {}", detectedPiiTypes);
    }
    processedQuery = queryPiiAnalysis.getProcessedQuery();
}

// STEP 2: Send processed (redacted) query to LLM
MultiIntentResponse intents = intentQueryExtractor.extract(processedQuery, context);
```

**PII detection happens automatically before LLM. No code changes needed.**

---

## The Bottom Line

**PIIDetectionService = Automatic PII detection & redaction.**  
**Pattern-based = Regex matching for common PII types.**  
**Encryption = Optional audit trail (encrypted original).**

**What you get:**
- 🔒 Automatic detection (5 built-in patterns)
- 🎯 Multiple modes (REDACT, DETECT_ONLY, PASS_THROUGH)
- 🔐 Encryption support (AES-GCM for audit trail)
- 📋 Audit logging (compliance-ready)
- ⚙️ Configurable patterns (add your own)
- 🚀 Zero code changes (automatic in orchestrator)

**What you configure:**
- Enable/disable detection
- Choose mode (REDACT/DETECT_ONLY)
- Add custom patterns
- Set encryption secret

**Result:** GDPR-compliant by default. PII never reaches LLM. Safe for production.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [PII Detection Guide](link)  
💬 **Community:** [Join discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- [Custom Access Policy: Fail Closed Security](link)
- **PII Detection: Privacy by Default** (you are here)
- [The Core: Foundation](link)

---

*Built with ❤️ for developers who want privacy-by-default architecture*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your PII detection patterns
- 🔄 Follow for Q1 2026 launch

**Stop leaking PII. Start detecting & redacting. GDPR-compliant by default.** 🔒

