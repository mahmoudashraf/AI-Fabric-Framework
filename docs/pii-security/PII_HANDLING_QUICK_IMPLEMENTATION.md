# PII Handling - Quick Implementation (4 hours)

## Overview

Create a **5-layer security system** to detect, redact, and protect sensitive data.

---

## Step 1: Create PII Detection Service (30 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/security/PIIDetectionService.java`

```java
package com.ai.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PIIDetectionService {
    
    private static final Map<String, Pattern> PII_PATTERNS = Map.ofEntries(
        // Credit Card: 4532-1234-5678-9012
        Map.entry("CREDIT_CARD", Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
        )),
        
        // SSN: 123-45-6789
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
            "(?i)(api[_-]?key|sk_live_|sk_test_)\\s*[=:]{1,2}\\s*[\\w-]{20,}"
        )),
        
        // Database Password
        Map.entry("DB_PASSWORD", Pattern.compile(
            "(?i)(password|passwd|pwd)\\s*[=:]{1,2}\\s*[^\\s]+"
        ))
    );
    
    public PIIAnalysisResult analyze(String text) {
        log.debug("Analyzing text for PII");
        
        Map<String, List<PIIMatch>> detections = new HashMap<>();
        
        for (Map.Entry<String, Pattern> entry : PII_PATTERNS.entrySet()) {
            String type = entry.getKey();
            Pattern pattern = entry.getValue();
            Matcher matcher = pattern.matcher(text);
            
            while (matcher.find()) {
                detections.computeIfAbsent(type, k -> new ArrayList<>())
                    .add(new PIIMatch(
                        matcher.group(),
                        matcher.start(),
                        matcher.end(),
                        type
                    ));
            }
        }
        
        RiskSeverity severity = calculateSeverity(detections.keySet());
        
        return PIIAnalysisResult.builder()
            .hasDetections(!detections.isEmpty())
            .detections(detections)
            .severity(severity)
            .build();
    }
    
    private RiskSeverity calculateSeverity(Set<String> types) {
        if (types.contains("CREDIT_CARD") || 
            types.contains("SSN") || 
            types.contains("API_KEY") ||
            types.contains("DB_PASSWORD")) {
            return RiskSeverity.HIGH;
        }
        
        if (types.contains("PHONE")) {
            return RiskSeverity.MEDIUM;
        }
        
        return RiskSeverity.LOW;
    }
}
```

---

## Step 2: Create Redaction Service (30 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/security/SensitiveDataRedactor.java`

```java
package com.ai.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SensitiveDataRedactor {
    
    /**
     * Completely redact sensitive data
     */
    public String redactCompletely(String text, PIIAnalysisResult analysis) {
        if (!analysis.hasDetections()) {
            return text;
        }
        
        String redacted = text;
        
        // Sort detections by position (reverse) to maintain indices
        List<PIIMatch> allMatches = analysis.getAllMatches().stream()
            .sorted(Comparator.comparingInt(PIIMatch::getStart).reversed())
            .toList();
        
        for (PIIMatch match : allMatches) {
            String placeholder = "[REDACTED_" + match.getType() + "]";
            redacted = redacted.substring(0, match.getStart()) 
                + placeholder 
                + redacted.substring(match.getEnd());
        }
        
        log.debug("Text redacted. Original length: {}, Redacted length: {}", 
            text.length(), redacted.length());
        
        return redacted;
    }
    
    /**
     * Partially mask for user-friendly display
     */
    public String maskPartially(String text, PIIAnalysisResult analysis) {
        if (!analysis.hasDetections()) {
            return text;
        }
        
        String masked = text;
        
        for (PIIMatch match : analysis.getAllMatches()) {
            String maskedValue = maskValue(match);
            masked = masked.replaceFirst(
                Pattern.quote(match.getValue()),
                maskedValue
            );
        }
        
        return masked;
    }
    
    private String maskValue(PIIMatch match) {
        switch (match.getType()) {
            case "CREDIT_CARD":
                String cc = match.getValue().replaceAll("[^0-9]", "");
                return "****-****-****-" + cc.substring(cc.length() - 4);
                
            case "EMAIL":
                int atIndex = match.getValue().indexOf("@");
                return "***@" + match.getValue().substring(atIndex + 1);
                
            case "PHONE":
                String digits = match.getValue().replaceAll("[^0-9]", "");
                return "***-***-" + digits.substring(digits.length() - 4);
                
            case "SSN":
                return "***-**-" + match.getValue().substring(7);
                
            default:
                return "[REDACTED]";
        }
    }
}

@lombok.Data
@lombok.AllArgsConstructor
class PIIMatch {
    private String value;
    private int start;
    private int end;
    private String type;
}
```

---

## Step 3: Create Response Sanitizer (30 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/security/ResponseSanitizer.java`

```java
package com.ai.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResponseSanitizer {
    
    private final PIIDetectionService piiDetectionService;
    private final SensitiveDataRedactor redactor;
    
    /**
     * Clean response before returning to user
     */
    public String sanitize(String response, String userId) {
        if (response == null) {
            return response;
        }
        
        // Detect PII in response
        PIIAnalysisResult analysis = piiDetectionService.analyze(response);
        
        if (analysis.hasDetections()) {
            log.warn("PII detected in response for user: {}. Types: {}", 
                userId, analysis.detections.keySet());
            
            // Log incident
            logIncident("PII_LEAKED_IN_RESPONSE", userId, analysis);
            
            // Redact and return
            return redactor.redactCompletely(response, analysis);
        }
        
        return response;
    }
    
    private void logIncident(String type, String userId, PIIAnalysisResult analysis) {
        // Log to security system
        log.error("SECURITY_INCIDENT: {} for user: {}. Detected: {}", 
            type, userId, analysis.detections.keySet());
    }
}
```

---

## Step 4: Create Result DTOs (20 min)

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/security/PIIAnalysisResult.java`

```java
package com.ai.infrastructure.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.*;

@Data
@Builder
public class PIIAnalysisResult {
    
    private boolean hasDetections;
    private Map<String, List<PIIMatch>> detections;
    private RiskSeverity severity;
    
    public List<PIIMatch> getAllMatches() {
        return detections.values().stream()
            .flatMap(List::stream)
            .toList();
    }
    
    public boolean isHighRisk() {
        return severity == RiskSeverity.HIGH;
    }
}

public enum RiskSeverity {
    LOW,
    MEDIUM,
    HIGH
}
```

---

## Step 5: Integrate with Orchestrator (60 min)

**File:** Update `RAGOrchestrator.java`

```java
@Service
@Slf4j
public class SecureRAGOrchestrator extends RAGOrchestrator {
    
    @Autowired
    private PIIDetectionService piiDetectionService;
    
    @Autowired
    private SensitiveDataRedactor redactor;
    
    @Autowired
    private ResponseSanitizer responseSanitizer;
    
    @Override
    public OrchestrationResult orchestrate(String rawQuery, String userId) {
        // Step 1: Analyze for PII
        PIIAnalysisResult piiAnalysis = piiDetectionService.analyze(rawQuery);
        
        if (piiAnalysis.hasDetections()) {
            log.warn("PII detected in query from user: {}. Severity: {}", 
                userId, piiAnalysis.getSeverity());
            
            // Handle high-risk
            if (piiAnalysis.isHighRisk()) {
                return handleHighRiskQuery(piiAnalysis, userId);
            }
            
            // Redact for processing
            rawQuery = redactor.redactCompletely(rawQuery, piiAnalysis);
        }
        
        // Step 2: Process normally
        OrchestrationResult result = super.orchestrate(rawQuery, userId);
        
        // Step 3: Sanitize response
        if (result.getMessage() != null) {
            String sanitized = responseSanitizer.sanitize(result.getMessage(), userId);
            result.setMessage(sanitized);
        }
        
        return result;
    }
    
    private OrchestrationResult handleHighRiskQuery(
        PIIAnalysisResult analysis,
        String userId) {
        
        log.error("HIGH RISK PII DETECTED for user: {}. Types: {}", 
            userId, analysis.detections.keySet());
        
        return OrchestrationResult.failure(
            "Sensitive information detected. Please use our secure forms."
        );
    }
}
```

---

## Step 6: Add Configuration (10 min)

**File:** `application.yml`

```yaml
security:
  pii:
    enabled: true
    detection-strategy: pattern
    redaction-strategy: complete
    
    # High-risk types trigger rejection
    high-risk-types:
      - CREDIT_CARD
      - SSN
      - API_KEY
      - DB_PASSWORD
    
    # Mask in logs
    mask-in-logs: true
    mask-in-audit: true
    
    # Alert on high risk
    alert-on-high-risk: true
    
    # Data retention
    retention-days: 7

logging:
  level:
    com.ai.infrastructure.security: DEBUG
```

---

## Step 7: Write Tests (60 min)

**File:** `PIIDetectionServiceTest.java`

```java
@SpringBootTest
class PIIDetectionServiceTest {
    
    @Autowired
    private PIIDetectionService piiDetectionService;
    
    @Test
    void shouldDetectCreditCard() {
        String query = "My card is 4532-1234-5678-9012";
        PIIAnalysisResult result = piiDetectionService.analyze(query);
        
        assertThat(result.hasDetections()).isTrue();
        assertThat(result.detections).containsKey("CREDIT_CARD");
        assertThat(result.severity).isEqualTo(RiskSeverity.HIGH);
    }
    
    @Test
    void shouldDetectSSN() {
        String query = "My SSN is 123-45-6789";
        PIIAnalysisResult result = piiDetectionService.analyze(query);
        
        assertThat(result.hasDetections()).isTrue();
        assertThat(result.detections).containsKey("SSN");
    }
    
    @Test
    void shouldNotDetectNormalText() {
        String query = "What's your return policy?";
        PIIAnalysisResult result = piiDetectionService.analyze(query);
        
        assertThat(result.hasDetections()).isFalse();
    }
}

class RedactionServiceTest {
    
    private SensitiveDataRedactor redactor = new SensitiveDataRedactor();
    
    @Test
    void shouldRedactCreditCard() {
        String text = "My card is 4532-1234-5678-9012";
        PIIAnalysisResult analysis = new PIIDetectionService().analyze(text);
        
        String redacted = redactor.redactCompletely(text, analysis);
        
        assertThat(redacted).doesNotContain("4532");
        assertThat(redacted).contains("[REDACTED_CREDIT_CARD]");
    }
    
    @Test
    void shouldMaskEmail() {
        String text = "Contact user@example.com";
        PIIAnalysisResult analysis = new PIIDetectionService().analyze(text);
        
        String masked = redactor.maskPartially(text, analysis);
        
        assertThat(masked).contains("***@example.com");
    }
}
```

---

## Step 8: Integration Test (30 min)

```java
@SpringBootTest
class SecureRAGOrchestratorTest {
    
    @Autowired
    private SecureRAGOrchestrator orchestrator;
    
    @Test
    void shouldRejectHighRiskQuery() {
        String query = "My credit card is 4532-1234-5678-9012";
        
        OrchestrationResult result = orchestrator.orchestrate(query, "user-123");
        
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Sensitive information");
    }
    
    @Test
    void shouldRedactLowRiskQuery() {
        String query = "Cancel my order. Email: user@example.com";
        
        OrchestrationResult result = orchestrator.orchestrate(query, "user-123");
        
        // Should process but with redacted data
        assertThat(result).isNotNull();
    }
    
    @Test
    void shouldSanitizeResponse() {
        // If LLM somehow returns PII (shouldn't happen)
        String response = "Your card 4532-1234-5678-9012 is updated";
        
        String sanitized = responseSanitizer.sanitize(response, "user-123");
        
        assertThat(sanitized).doesNotContain("4532");
    }
}
```

---

## Total Time: 4 Hours

- Step 1 (Detection): 30 min
- Step 2 (Redaction): 30 min
- Step 3 (Sanitization): 30 min
- Step 4 (DTOs): 20 min
- Step 5 (Integration): 60 min
- Step 6 (Configuration): 10 min
- Step 7 (Tests): 60 min
- Step 8 (Integration test): 30 min

**Total: ~4 hours to production-ready**

---

## Deployment Checklist

- [ ] PII Detection Service created
- [ ] Redaction Service created
- [ ] Response Sanitizer created
- [ ] Configuration added
- [ ] RAGOrchestrator updated
- [ ] All tests pass
- [ ] Performance tested (detection shouldn't add >10ms)
- [ ] Security team reviewed
- [ ] Deploy to staging
- [ ] Monitor for false positives
- [ ] Deploy to production

---

## Success Indicators

After implementation:

✅ PII detected before processing
✅ Queries with PII rejected (high-risk) or redacted (low-risk)
✅ No PII reaches LLM
✅ Responses sanitized
✅ No PII in logs
✅ High-risk alerts triggered
✅ All compliance requirements met

---

## Performance Impact

Expected overhead:
- PII detection: 5-10ms per query
- Redaction: 1-5ms per query
- Response sanitization: 2-5ms per response

**Total: ~15-20ms overhead (acceptable)**

---

## Next Steps

1. ✅ Implement all 4 hours
2. ✅ Test thoroughly
3. ✅ Get security review
4. ✅ Deploy to staging
5. ✅ Monitor metrics
6. ✅ Deploy to production
7. ✅ Regular audits

---

**Start today! Your users' data security depends on it! 🔒**

