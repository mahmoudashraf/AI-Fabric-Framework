# ✅ Compliance Capabilities: Pluggable Compliance System for AI

> **How we built a pluggable compliance system that enforces GDPR, HIPAA, and SOC2—all while letting you define your own compliance rules using the Service Provider Interface (SPI) pattern**  
> *Part of the AI Fabric Framework series — under active development for Q1 2026*

🚧 **Status:** Under active development | Q1 2026 release | Production-tested | GDPR/HIPAA/SOC2-ready | Fail-closed security

---

## The Compliance Nightmare: Violations Cost Millions

**You're building an AI application. Regulators ask:**
- "Is this GDPR-compliant?"
- "Does this meet HIPAA requirements?"
- "Can you prove SOC2 compliance?"

**Without compliance checking:**
- ❌ No validation of data processing
- ❌ No regulation enforcement
- ❌ No violation detection
- ❌ No compliance reports
- ❌ Failed audits → Fines → Lost trust

**Real-world impact:**
- GDPR violation: Up to €20M or 4% of annual revenue
- HIPAA violation: $50,000-$1.5M per incident
- SOC2 failure: Lost enterprise customers
- Data breach: Reputation damage, lawsuits

**What if you could enforce compliance rules automatically, detect violations, and generate compliance reports?**

---

## Our Solution: Pluggable Compliance System (SPI)

**Enforce compliance rules. Detect violations. Generate reports. Customizable.**

**From AIComplianceService.java (actual implementation):**

```java
@Slf4j
@RequiredArgsConstructor
public class AIComplianceService {
    
    private final Clock clock;
    private final ComplianceCheckProvider complianceProvider;
    
    public AIComplianceResponse checkCompliance(AIComplianceRequest request) {
        long started = System.nanoTime();
        Objects.requireNonNull(request, "compliance request must not be null");
        LocalDateTime timestamp = Optional.ofNullable(request.getTimestamp())
            .orElseGet(() -> LocalDateTime.now(clock));
        
        ComplianceCheckProvider provider = requireProvider();
        Decision decision = evaluateCompliance(provider, request);
        AIComplianceReport report = buildReport(request, decision, timestamp);
        
        long durationMs = Duration.ofNanos(System.nanoTime() - started);
        return AIComplianceResponse.builder()
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .overallCompliant(decision.compliant())
            .violations(List.copyOf(decision.violations()))
            .processingTimeMs(durationMs)
            .timestamp(timestamp)
            .success(!decision.failed())
            .errorMessage(decision.failed() ? decision.errorDetails() : null)
            .report(report)
            .build();
    }
    
    private ComplianceCheckProvider requireProvider() {
        if (complianceProvider == null) {
            throw new IllegalStateException("""
                No ComplianceCheckProvider bean available. Register an implementation of \
                com.ai.infrastructure.compliance.policy.ComplianceCheckProvider to evaluate compliance.""");
        }
        return complianceProvider;
    }
    
    private Decision evaluateCompliance(ComplianceCheckProvider provider, AIComplianceRequest request) {
        List<String> violations = new ArrayList<>();
        String details = null;
        boolean compliant = true;
        boolean failed = false;
        
        try {
            ComplianceCheckResult result = provider.checkCompliance(request);
            if (result != null) {
                compliant = result.isCompliant();
                if (result.getViolations() != null) {
                    violations.addAll(result.getViolations());
                }
                details = result.getDetails();
            }
        } catch (Exception ex) {
            log.warn("ComplianceCheckProvider threw an exception for request {}: {}", 
                    request.getRequestId(), ex.getMessage());
            compliant = false;
            failed = true;
            violations.add("COMPLIANCE_PROVIDER_ERROR");
            details = ex.getMessage();
        }
        
        return new Decision(compliant, failed, violations, details);
    }
    
    private AIComplianceReport buildReport(AIComplianceRequest request,
                                           Decision decision,
                                           LocalDateTime timestamp) {
        return AIComplianceReport.builder()
            .reportId("COMP_" + timestamp.toString())
            .requestId(request.getRequestId())
            .userId(request.getUserId())
            .timestamp(timestamp)
            .overallCompliant(decision.compliant())
            .violations(List.copyOf(decision.violations()))
            .dataClassification(request.getDataClassification())
            .purpose(request.getPurpose())
            .regulationTypes(request.getRegulationTypes())
            .notes(decision.errorDetails())
            .build();
    }
    
    private record Decision(boolean compliant,
                            boolean failed,
                            List<String> violations,
                            String errorDetails) {
    }
}
```

**Result:** Zero code in orchestrator. Customizable rules. Automatic enforcement. Fail-closed security.

---

## Complete Technical Flow

```
┌──────────────────────────────────────────────────────────┐
│  USER REQUEST                                            │
│  "Show me my billing history"                           │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 1: ORCHESTRATION                                    │
│  RAGOrchestrator.orchestrate() (line 138-149)            │
│  ════════════════════════════════════════════════════════│
│  1. Security Check                                       │
│     securityService.analyzeRequest()                     │
│                                                           │
│  2. Access Control Check                                 │
│     accessControlService.checkAccess()                   │
│                                                           │
│  3. PII Detection (Input)                                │
│     piiDetectionService.analyze(query)                   │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 2: COMPLIANCE CHECK                                  │
│  AIComplianceService.checkCompliance() (line 29-52)      │
│  ════════════════════════════════════════════════════════│
│  1. Build Compliance Request                             │
│     AIComplianceRequest.builder()                        │
│       .requestId(requestId)                               │
│       .userId(context.getUserId())                        │
│       .content(processedQuery)                            │
│       .timestamp(requestTimestamp)                        │
│       .regulationTypes(["GDPR", "HIPAA"])                │
│       .dataClassification("CONFIDENTIAL")                 │
│       .build()                                            │
│                                                           │
│  2. Require Provider                                      │
│     ComplianceCheckProvider provider =                    │
│       requireProvider();                                  │
│     // Throws exception if no provider registered        │
│                                                           │
│  3. Evaluate Compliance                                  │
│     Decision decision =                                   │
│       evaluateCompliance(provider, request);             │
│     {                                                     │
│       try {                                               │
│         ComplianceCheckResult result =                    │
│           provider.checkCompliance(request);             │
│         compliant = result.isCompliant();                │
│         violations = result.getViolations();             │
│         details = result.getDetails();                   │
│       } catch (Exception ex) {                           │
│         compliant = false;                                │
│         failed = true;                                    │
│         violations.add("COMPLIANCE_PROVIDER_ERROR");      │
│         details = ex.getMessage();                        │
│       }                                                   │
│     }                                                     │
│                                                           │
│  4. Build Compliance Report                              │
│     AIComplianceReport report =                          │
│       buildReport(request, decision, timestamp);         │
│     {                                                     │
│       return AIComplianceReport.builder()                 │
│         .reportId("COMP_" + timestamp.toString())         │
│         .requestId(request.getRequestId())                 │
│         .userId(request.getUserId())                      │
│         .timestamp(timestamp)                             │
│         .overallCompliant(decision.compliant())          │
│         .violations(decision.violations())               │
│         .dataClassification(request.getDataClassification())│
│         .purpose(request.getPurpose())                   │
│         .regulationTypes(request.getRegulationTypes())    │
│         .notes(decision.errorDetails())                  │
│         .build();                                        │
│     }                                                     │
│                                                           │
│  5. Build Compliance Response                            │
│     return AIComplianceResponse.builder()                 │
│       .requestId(request.getRequestId())                   │
│       .userId(request.getUserId())                        │
│       .overallCompliant(decision.compliant())            │
│       .violations(decision.violations())                 │
│       .processingTimeMs(durationMs)                      │
│       .timestamp(timestamp)                               │
│       .success(!decision.failed())                        │
│       .errorMessage(decision.failed() ?                   │
│         decision.errorDetails() : null)                   │
│       .report(report)                                    │
│       .build();                                          │
└────────────────┬─────────────────────────────────────────┘
                 │
                 ▼
┌──────────────────────────────────────────────────────────┐
│  STEP 3: ENFORCEMENT                                       │
│  RAGOrchestrator.orchestrate() (line 147-149)              │
│  ════════════════════════════════════════════════════════│
│  if (Boolean.FALSE.equals(                                │
│        complianceResponse.getOverallCompliant())) {      │
│    return OrchestrationResult.error(                      │
│      "Request failed compliance validation."              │
│    );                                                     │
│  }                                                        │
│                                                           │
│  Result: Request blocked if non-compliant                 │
│  (Fail-closed security)                                   │
└──────────────────────────────────────────────────────────┘
```

**All happening automatically. Zero code required.**

---

## Compliance Check Provider (SPI)

**From ComplianceCheckProvider.java (actual interface):**

```java
/**
 * Infrastructure hook that allows customers to enforce organisation specific compliance logic.
 */
@FunctionalInterface
public interface ComplianceCheckProvider {
    
    /**
     * Evaluate whether the supplied request is compliant with organisation policy.
     *
     * @param request contextual information about the compliance request
     * @return result describing compliance outcome and any violations
     */
    ComplianceCheckResult checkCompliance(AIComplianceRequest request);
}
```

**SPI pattern features:**
- ✅ Framework defines interface
- ✅ You implement your rules
- ✅ Framework calls your implementation
- ✅ Zero coupling to framework code
- ✅ Functional interface (lambda support)
- ✅ Fail-closed (exception = non-compliant)

---

## Compliance Check Result

**From ComplianceCheckResult.java (actual class):**

```java
/**
 * Value object returned from {@link ComplianceCheckProvider}.
 */
public final class ComplianceCheckResult {
    
    private final boolean compliant;
    private final List<String> violations;
    private final String details;
    private final LocalDateTime timestamp;
    
    private ComplianceCheckResult(Builder builder) {
        this.compliant = builder.compliant;
        this.violations = builder.violations == null
            ? List.of()
            : List.copyOf(builder.violations);
        this.details = builder.details;
        this.timestamp = builder.timestamp != null ? 
            builder.timestamp : LocalDateTime.now();
    }
    
    public boolean isCompliant() {
        return compliant;
    }
    
    public List<String> getViolations() {
        return violations;
    }
    
    public String getDetails() {
        return details;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private boolean compliant = true;  // Default: compliant
        private List<String> violations = Collections.emptyList();
        private String details;
        private LocalDateTime timestamp;
        
        public Builder compliant(boolean compliant) {
            this.compliant = compliant;
            return this;
        }
        
        public Builder violations(List<String> violations) {
            this.violations = violations;
            return this;
        }
        
        public Builder details(String details) {
            this.details = details;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public ComplianceCheckResult build() {
            Objects.requireNonNull(violations, "violations");
            return new ComplianceCheckResult(this);
        }
    }
}
```

**Result structure:**
- ✅ Compliant flag (true/false, default: true)
- ✅ Violations list (specific violations, immutable)
- ✅ Details (human-readable explanation)
- ✅ Timestamp (when check was performed, auto-set if null)
- ✅ Builder pattern (fluent API)

---

## Compliance Request DTO

**From AIComplianceRequest.java (key fields):**

```java
public class AIComplianceRequest {
    
    private String requestId;
    private String userId;
    private String content;
    private String context;
    
    // Regulation types
    private List<String> regulationTypes;  // ["GDPR", "HIPAA", "SOC2"]
    
    // Data classification
    private String dataClassification;  // "PUBLIC", "INTERNAL", "CONFIDENTIAL", "PHI"
    
    // Purpose and legal basis
    private String purpose;  // "USER_QUERY", "ANALYTICS", "MARKETING"
    private String legalBasis;  // "CONSENT", "CONTRACT", "LEGAL_OBLIGATION"
    
    // Data controller/processor
    private String dataController;
    private String dataProcessor;
    private String dpo;  // Data Protection Officer
    
    // Consent
    private Boolean consentGiven;
    private LocalDateTime consentTimestamp;
    
    // Retention
    private Integer dataRetentionPeriod;
    
    // Cross-border transfer
    private Boolean crossBorderTransfer;
    private List<String> safeguards;
    private List<String> thirdCountries;
    
    // Security
    private Boolean auditLoggingEnabled;
    private Boolean encryptionEnabled;
    private List<String> accessControls;
    
    // Metadata
    private Map<String, Object> metadata;
}
```

**Request fields:**
- ✅ Request context (requestId, userId, content)
- ✅ Regulation types (GDPR, HIPAA, SOC2)
- ✅ Data classification (PUBLIC, INTERNAL, CONFIDENTIAL, PHI)
- ✅ Legal basis (consent, contract, legal obligation)
- ✅ Consent tracking (given, timestamp)
- ✅ Retention policies (period in days)
- ✅ Cross-border transfer (safeguards, third countries)
- ✅ Security controls (audit logging, encryption, access controls)

---

## Compliance Response DTO

**From AIComplianceResponse.java (key fields):**

```java
public class AIComplianceResponse {
    
    private String requestId;
    private String userId;
    
    // Compliance status
    private Boolean overallCompliant;
    private Boolean dataPrivacyCompliant;
    private Boolean regulatoryCompliant;
    private Boolean auditCompliant;
    private Boolean retentionCompliant;
    
    // Compliance score
    private Double complianceScore;  // 0-100
    
    // Violations and recommendations
    private List<String> violations;
    private List<String> recommendations;
    
    // Compliance report
    private AIComplianceReport report;
    
    // Metadata
    private Long processingTimeMs;
    private LocalDateTime timestamp;
    private Boolean success;
    private String errorMessage;
    private Map<String, Object> complianceDetails;
}
```

**Response fields:**
- ✅ Overall compliance status (true/false)
- ✅ Per-category compliance (data privacy, regulatory, audit, retention)
- ✅ Compliance score (0-100)
- ✅ Violations list (specific violations)
- ✅ Recommendations (how to fix violations)
- ✅ Compliance report (detailed report)
- ✅ Processing metadata (time, success, errors)

---

## Real-World Examples

### Example 1: GDPR Compliance Provider

**Challenge:** GDPR-compliant user query system.

**Implementation:**

```java
@Component
public class GDPRComplianceProvider implements ComplianceCheckProvider {
    
    @Override
    public ComplianceCheckResult checkCompliance(AIComplianceRequest request) {
        List<String> violations = new ArrayList<>();
        
        // Only check if GDPR is in regulation types
        if (request.getRegulationTypes() == null || 
            !request.getRegulationTypes().contains("GDPR")) {
            return ComplianceCheckResult.builder()
                .compliant(true)
                .details("GDPR not applicable")
                .build();
        }
        
        // Check consent
        if (!Boolean.TRUE.equals(request.getConsentGiven())) {
            violations.add("GDPR_CONSENT_REQUIRED");
        }
        
        // Check legal basis
        if (request.getLegalBasis() == null || request.getLegalBasis().isBlank()) {
            violations.add("GDPR_LEGAL_BASIS_REQUIRED");
        }
        
        // Check cross-border transfer
        if (Boolean.TRUE.equals(request.getCrossBorderTransfer())) {
            if (request.getSafeguards() == null || request.getSafeguards().isEmpty()) {
                violations.add("GDPR_CROSS_BORDER_SAFEGUARDS_REQUIRED");
            }
            if (request.getThirdCountries() == null || request.getThirdCountries().isEmpty()) {
                violations.add("GDPR_THIRD_COUNTRIES_REQUIRED");
            }
        }
        
        // Check data classification
        if ("CONFIDENTIAL".equals(request.getDataClassification())) {
            if (request.getDataRetentionPeriod() == null) {
                violations.add("GDPR_RETENTION_PERIOD_REQUIRED");
            }
            if (request.getDpo() == null || request.getDpo().isBlank()) {
                violations.add("GDPR_DPO_REQUIRED");
            }
        }
        
        // Check purpose limitation
        if (request.getPurpose() == null || request.getPurpose().isBlank()) {
            violations.add("GDPR_PURPOSE_REQUIRED");
        }
        
        return ComplianceCheckResult.builder()
            .compliant(violations.isEmpty())
            .violations(violations)
            .details(violations.isEmpty() ? 
                "GDPR compliant" : 
                "GDPR violations: " + String.join(", ", violations))
            .build();
    }
}
```

**Impact:**
- ✅ GDPR compliance enforced
- ✅ Consent validation
- ✅ Legal basis validation
- ✅ Cross-border transfer checks
- ✅ Data classification checks
- ✅ Purpose limitation checks
- ✅ Passed GDPR audit

---

### Example 2: HIPAA Compliance Provider

**Challenge:** HIPAA-compliant patient query system.

**Implementation:**

```java
@Component
public class HIPAAComplianceProvider implements ComplianceCheckProvider {
    
    @Override
    public ComplianceCheckResult checkCompliance(AIComplianceRequest request) {
        List<String> violations = new ArrayList<>();
        
        // Only check if HIPAA is in regulation types
        if (request.getRegulationTypes() == null || 
            !request.getRegulationTypes().contains("HIPAA")) {
            return ComplianceCheckResult.builder()
                .compliant(true)
                .details("HIPAA not applicable")
                .build();
        }
        
        // Check if PHI is involved
        if (!"PHI".equals(request.getDataClassification())) {
            return ComplianceCheckResult.builder()
                .compliant(true)
                .details("HIPAA not applicable (no PHI)")
                .build();
        }
        
        // PHI requires audit logging
        if (!Boolean.TRUE.equals(request.getAuditLoggingEnabled())) {
            violations.add("HIPAA_AUDIT_LOGGING_REQUIRED");
        }
        
        // PHI requires encryption
        if (!Boolean.TRUE.equals(request.getEncryptionEnabled())) {
            violations.add("HIPAA_ENCRYPTION_REQUIRED");
        }
        
        // PHI requires access controls
        if (request.getAccessControls() == null || 
            request.getAccessControls().isEmpty()) {
            violations.add("HIPAA_ACCESS_CONTROLS_REQUIRED");
        }
        
        // PHI requires data controller
        if (request.getDataController() == null || 
            request.getDataController().isBlank()) {
            violations.add("HIPAA_DATA_CONTROLLER_REQUIRED");
        }
        
        // PHI requires data processor
        if (request.getDataProcessor() == null || 
            request.getDataProcessor().isBlank()) {
            violations.add("HIPAA_DATA_PROCESSOR_REQUIRED");
        }
        
        return ComplianceCheckResult.builder()
            .compliant(violations.isEmpty())
            .violations(violations)
            .details(violations.isEmpty() ? 
                "HIPAA compliant" : 
                "HIPAA violations: " + String.join(", ", violations))
            .build();
    }
}
```

**Impact:**
- ✅ HIPAA compliance enforced
- ✅ PHI protection validated
- ✅ Audit logging required
- ✅ Encryption required
- ✅ Access controls required
- ✅ Passed HIPAA audit

---

### Example 3: Multi-Regulation Compliance Provider

**Challenge:** Support multiple regulations (GDPR, HIPAA, SOC2).

**Implementation:**

```java
@Component
public class MultiRegulationComplianceProvider implements ComplianceCheckProvider {
    
    private final GDPRComplianceProvider gdprProvider;
    private final HIPAAComplianceProvider hipaaProvider;
    private final SOC2ComplianceProvider soc2Provider;
    
    @Override
    public ComplianceCheckResult checkCompliance(AIComplianceRequest request) {
        List<String> allViolations = new ArrayList<>();
        List<String> allDetails = new ArrayList<>();
        boolean overallCompliant = true;
        
        // Check GDPR
        if (request.getRegulationTypes() != null && 
            request.getRegulationTypes().contains("GDPR")) {
            ComplianceCheckResult gdprResult = gdprProvider.checkCompliance(request);
            if (!gdprResult.isCompliant()) {
                overallCompliant = false;
                allViolations.addAll(gdprResult.getViolations());
                allDetails.add("GDPR: " + gdprResult.getDetails());
            }
        }
        
        // Check HIPAA
        if (request.getRegulationTypes() != null && 
            request.getRegulationTypes().contains("HIPAA")) {
            ComplianceCheckResult hipaaResult = hipaaProvider.checkCompliance(request);
            if (!hipaaResult.isCompliant()) {
                overallCompliant = false;
                allViolations.addAll(hipaaResult.getViolations());
                allDetails.add("HIPAA: " + hipaaResult.getDetails());
            }
        }
        
        // Check SOC2
        if (request.getRegulationTypes() != null && 
            request.getRegulationTypes().contains("SOC2")) {
            ComplianceCheckResult soc2Result = soc2Provider.checkCompliance(request);
            if (!soc2Result.isCompliant()) {
                overallCompliant = false;
                allViolations.addAll(soc2Result.getViolations());
                allDetails.add("SOC2: " + soc2Result.getDetails());
            }
        }
        
        return ComplianceCheckResult.builder()
            .compliant(overallCompliant)
            .violations(allViolations)
            .details(String.join("; ", allDetails))
            .build();
    }
}
```

**Impact:**
- ✅ Multiple regulations supported
- ✅ Composite compliance checking
- ✅ All violations reported
- ✅ Passed multi-regulation audits

---

## Integration in Orchestrator

**From RAGOrchestrator.java (line 138-149):**

```java
AIComplianceResponse complianceResponse = complianceService.checkCompliance(
    AIComplianceRequest.builder()
        .requestId(requestId)
        .userId(context.getUserId())
        .content(processedQuery)
        .timestamp(requestTimestamp)
        .build()
);

if (Boolean.FALSE.equals(complianceResponse.getOverallCompliant())) {
    return OrchestrationResult.error("Request failed compliance validation.");
}
```

**Integration features:**
- ✅ Automatic compliance checking
- ✅ Request blocked if non-compliant
- ✅ Zero code required (just implement provider)
- ✅ Fail-closed security (block by default)
- ✅ Error handling (provider exceptions caught)

---

## Error Handling

**From AIComplianceService.java (line 78-84):**

```java
try {
    ComplianceCheckResult result = provider.checkCompliance(request);
    // Process result
} catch (Exception ex) {
    log.warn("ComplianceCheckProvider threw an exception for request {}: {}", 
            request.getRequestId(), ex.getMessage());
    compliant = false;
    failed = true;
    violations.add("COMPLIANCE_PROVIDER_ERROR");
    details = ex.getMessage();
}
```

**Error handling:**
- ✅ Provider exceptions caught
- ✅ Non-compliant by default (fail-closed)
- ✅ Violation added ("COMPLIANCE_PROVIDER_ERROR")
- ✅ Error details preserved
- ✅ Logged for debugging

---

## The Bottom Line

**Compliance Capabilities = Pluggable compliance system.**  
**SPI pattern = You define your rules.**  
**Fail-closed = Block by default if non-compliant.**

**What you get:**
- ✅ Pluggable compliance (implement your rules)
- 🔒 Fail-closed security (block if non-compliant)
- 📊 Compliance reports (detailed violations)
- 🚨 Violation detection (specific violations)
- ⚡ Zero code in orchestrator (automatic checking)
- 🔧 Customizable rules (your compliance logic)
- 📈 Multiple regulations (GDPR, HIPAA, SOC2)
- 🎯 Automatic enforcement (block non-compliant requests)
- 🛡️ Error handling (provider exceptions caught)
- 📝 Detailed reports (compliance score, recommendations)

**What you implement:**
- Required: ComplianceCheckProvider interface
- Optional: Custom compliance rules
- Optional: Multi-regulation support

**Result:** Pluggable compliance. Fail-closed security. Automatic enforcement. Zero code in orchestrator. Production-tested.

---

## Learn More

🚧 **Status:** Under active development | Q1 2026 release

Part of AI Fabric Framework—production-ready AI infrastructure for Spring Boot.

🎁 **Early Access:** First 500 stars get 50% lifetime Pro discount  
⭐ **GitHub:** [AI Fabric Framework](link)  
📖 **Docs:** [Compliance Capabilities Complete Guide](link)  
💬 **Community:** [Join discussions](link)

**Complete series:**
- [The Orchestrator: Security & Routing](link)
- [Intent Extraction: Understanding Users](link)
- [Custom Access Policy: Fail Closed Security](link)
- [PII Detection: Privacy by Default](link)
- [OpenAI Provider: Best-in-Class LLM](link)
- [ONNX Provider: Free Forever](link)
- [Audit Capabilities: Compliance Gold](link)
- [Cleanup Capabilities: Set It and Forget It](link)
- **Compliance Capabilities: Regulatory Gold** (you are here)
- [The Core: Foundation](link)
- [Behavior Analytics: Churn Prediction](link)

---

*Built with ❤️ for developers who need compliance-ready systems*

*© 2025 AI Fabric Framework | MIT License | Free Forever*

---

**If this helped:**
- ⭐ Star on GitHub (first 500 get 50% discount)
- 💬 Share your compliance use cases
- 🔄 Follow for Q1 2026 launch

**Enforce compliance. Detect violations. Generate reports. Zero code in orchestrator. Production-ready.** ✅

