# PII Detector Pattern Design - Extensible & Maintainable

**Problem:** `detectAndTokenize()` grows with every new PII pattern  
**Solution:** Registry-based PII detector pattern  
**Result:** Add new patterns without modifying core logic

---

## The Problem

### Current Approach (Vulnerable to Change):

```java
public PIIDetectionResult detectAndTokenize(String query) {
    // Email detection (20 lines)
    if (emailPattern.matcher(query).find()) { ... }
    
    // Credit card detection (20 lines)
    if (cardPattern.matcher(query).find()) { ... }
    
    // Phone detection (20 lines)
    if (phonePattern.matcher(query).find()) { ... }
    
    // SSN detection (20 lines)
    if (ssnPattern.matcher(query).find()) { ... }
    
    // Every new pattern = +20 lines ❌
    // Method grows to 200+ lines ❌
    // Hard to test each pattern ❌
}
```

**Issues:**
- ❌ Violates Open/Closed Principle
- ❌ Method grows unbounded
- ❌ Hard to add new patterns
- ❌ Difficult to test individual detectors
- ❌ Can't enable/disable specific detectors

---

## The Solution: Registry Pattern

### Architecture:

```
PIIDetectorRegistry
  ├─ EmailDetector
  ├─ CreditCardDetector
  ├─ PhoneDetector
  ├─ SSNDetector
  ├─ DateOfBirthDetector
  └─ [Future detectors...] ← Easy to add!
```

---

## 1. PIIDetector Interface

```java
package com.ai.infrastructure.privacy.pii.detector;

import java.util.List;

/**
 * Strategy interface for detecting specific PII patterns.
 * 
 * <p>Implementations detect one type of PII (email, credit card, etc.) and
 * generate tokens for detected values.</p>
 * 
 * <p><strong>Extensibility:</strong> Add new PII types by implementing this interface
 * and registering with PIIDetectorRegistry.</p>
 */
public interface PIIDetector {
    
    /**
     * Detects PII patterns in content.
     * 
     * @param content Content to scan
     * @return List of detected PII instances (empty if none found)
     */
    List<DetectedPII> detect(String content);
    
    /**
     * Gets the PII type this detector handles.
     * 
     * @return PII type name (e.g., "EMAIL", "CREDIT_CARD")
     */
    String getPIIType();
    
    /**
     * Gets the database field name typically used for this PII type.
     * 
     * @return Field name (e.g., "email", "cardNumber")
     */
    String getFieldName();
    
    /**
     * Checks if this detector is enabled.
     * 
     * @return true if detector should be used
     */
    default boolean isEnabled() {
        return true;
    }
}

/**
 * Represents a detected PII instance.
 */
@Data
@Builder
class DetectedPII {
    private String piiType;        // "EMAIL", "CREDIT_CARD", etc.
    private String value;          // Actual PII value
    private int startIndex;        // Position in original string
    private int endIndex;          // End position
    private String fieldName;      // Database field name
}
```

---

## 2. Concrete Detector Implementations

### 2.1 EmailDetector

```java
package com.ai.infrastructure.privacy.pii.detector;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Detects email addresses in content.
 * 
 * <p>Pattern: standard email format (RFC 5322 simplified)</p>
 */
@Component
public class EmailDetector implements PIIDetector {
    
    private static final String PII_TYPE = "EMAIL";
    private static final String FIELD_NAME = "email";
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        List<DetectedPII> detected = new ArrayList<>();
        Matcher matcher = EMAIL_PATTERN.matcher(content);
        
        while (matcher.find()) {
            detected.add(DetectedPII.builder()
                .piiType(PII_TYPE)
                .value(matcher.group())
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .fieldName(FIELD_NAME)
                .build());
        }
        
        return detected;
    }
    
    @Override
    public String getPIIType() {
        return PII_TYPE;
    }
    
    @Override
    public String getFieldName() {
        return FIELD_NAME;
    }
}
```

### 2.2 CreditCardDetector

```java
package com.ai.infrastructure.privacy.pii.detector;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Detects credit card numbers in content.
 * 
 * <p>Pattern: 16 digits with optional separators (-, space)</p>
 */
@Component
public class CreditCardDetector implements PIIDetector {
    
    private static final String PII_TYPE = "CREDIT_CARD";
    private static final String FIELD_NAME = "cardNumber";
    
    private static final Pattern CARD_PATTERN = Pattern.compile(
        "\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        List<DetectedPII> detected = new ArrayList<>();
        Matcher matcher = CARD_PATTERN.matcher(content);
        
        while (matcher.find()) {
            detected.add(DetectedPII.builder()
                .piiType(PII_TYPE)
                .value(matcher.group())
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .fieldName(FIELD_NAME)
                .build());
        }
        
        return detected;
    }
    
    @Override
    public String getPIIType() {
        return PII_TYPE;
    }
    
    @Override
    public String getFieldName() {
        return FIELD_NAME;
    }
}
```

### 2.3 PhoneDetector

```java
package com.ai.infrastructure.privacy.pii.detector;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Detects phone numbers in content.
 * 
 * <p>Patterns supported:</p>
 * <ul>
 *   <li>555-123-4567</li>
 *   <li>(555) 123-4567</li>
 *   <li>555.123.4567</li>
 *   <li>+1-555-123-4567</li>
 * </ul>
 */
@Component
public class PhoneDetector implements PIIDetector {
    
    private static final String PII_TYPE = "PHONE";
    private static final String FIELD_NAME = "phone";
    
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "\\b(?:\\+?1[-.]?)?\\(?\\d{3}\\)?[-.]?\\d{3}[-.]?\\d{4}\\b"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        List<DetectedPII> detected = new ArrayList<>();
        Matcher matcher = PHONE_PATTERN.matcher(content);
        
        while (matcher.find()) {
            detected.add(DetectedPII.builder()
                .piiType(PII_TYPE)
                .value(matcher.group())
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .fieldName(FIELD_NAME)
                .build());
        }
        
        return detected;
    }
    
    @Override
    public String getPIIType() {
        return PII_TYPE;
    }
    
    @Override
    public String getFieldName() {
        return FIELD_NAME;
    }
}
```

### 2.4 SSNDetector

```java
package com.ai.infrastructure.privacy.pii.detector;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Detects Social Security Numbers in content.
 * 
 * <p>Pattern: XXX-XX-XXXX</p>
 */
@Component
public class SSNDetector implements PIIDetector {
    
    private static final String PII_TYPE = "SSN";
    private static final String FIELD_NAME = "ssn";
    
    private static final Pattern SSN_PATTERN = Pattern.compile(
        "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        List<DetectedPII> detected = new ArrayList<>();
        Matcher matcher = SSN_PATTERN.matcher(content);
        
        while (matcher.find()) {
            detected.add(DetectedPII.builder()
                .piiType(PII_TYPE)
                .value(matcher.group())
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .fieldName(FIELD_NAME)
                .build());
        }
        
        return detected;
    }
    
    @Override
    public String getPIIType() {
        return PII_TYPE;
    }
    
    @Override
    public String getFieldName() {
        return FIELD_NAME;
    }
}
```

### 2.5 DateOfBirthDetector

```java
package com.ai.infrastructure.privacy.pii.detector;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Detects dates of birth in content.
 * 
 * <p>Triggered only when context indicates DOB (e.g., "born on", "date of birth").</p>
 * <p>Pattern: YYYY-MM-DD</p>
 */
@Component
public class DateOfBirthDetector implements PIIDetector {
    
    private static final String PII_TYPE = "DATE_OF_BIRTH";
    private static final String FIELD_NAME = "dateOfBirth";
    
    private static final Pattern DOB_PATTERN = Pattern.compile(
        "\\b\\d{4}-\\d{2}-\\d{2}\\b"
    );
    
    // Context keywords that indicate DOB
    private static final List<String> DOB_KEYWORDS = List.of(
        "born on", "date of birth", "dob", "birth date", "birthdate"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        // Only detect dates if context indicates DOB
        String lowerContent = content.toLowerCase();
        boolean hasDOBContext = DOB_KEYWORDS.stream()
            .anyMatch(lowerContent::contains);
        
        if (!hasDOBContext) {
            return List.of();  // Not a DOB search
        }
        
        List<DetectedPII> detected = new ArrayList<>();
        Matcher matcher = DOB_PATTERN.matcher(content);
        
        while (matcher.find()) {
            detected.add(DetectedPII.builder()
                .piiType(PII_TYPE)
                .value(matcher.group())
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .fieldName(FIELD_NAME)
                .build());
        }
        
        return detected;
    }
    
    @Override
    public String getPIIType() {
        return PII_TYPE;
    }
    
    @Override
    public String getFieldName() {
        return FIELD_NAME;
    }
}
```

---

## 3. PIIDetectorRegistry

```java
package com.ai.infrastructure.privacy.pii;

import com.ai.infrastructure.privacy.pii.detector.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Registry of PII detectors with automatic discovery.
 * 
 * <p><strong>Extensibility:</strong> Add new PII detectors by:</p>
 * <ol>
 *   <li>Implement PIIDetector interface</li>
 *   <li>Annotate with @Component</li>
 *   <li>Spring auto-discovers and registers</li>
 *   <li>NO changes to this registry needed!</li>
 * </ol>
 * 
 * <p><strong>Example - Adding IP Address Detector:</strong></p>
 * <pre>{@code
 * @Component
 * public class IPAddressDetector implements PIIDetector {
 *     // Implementation
 * }
 * // That's it! Automatically discovered and used.
 * }</pre>
 */
@Slf4j
@Service
public class PIIDetectorRegistry {
    
    private final List<PIIDetector> detectors;
    
    /**
     * Constructor with automatic detector discovery.
     * Spring injects ALL PIIDetector implementations.
     * 
     * @param detectors All PIIDetector beans (auto-discovered by Spring)
     */
    public PIIDetectorRegistry(List<PIIDetector> detectors) {
        this.detectors = detectors != null ? detectors : List.of();
        
        log.info("PIIDetectorRegistry initialized with {} detectors:", this.detectors.size());
        this.detectors.forEach(detector -> 
            log.info("  - {} (field: {})", detector.getPIIType(), detector.getFieldName())
        );
    }
    
    /**
     * Gets all registered detectors.
     * 
     * @return Unmodifiable list of detectors
     */
    public List<PIIDetector> getAllDetectors() {
        return Collections.unmodifiableList(detectors);
    }
    
    /**
     * Gets enabled detectors only.
     * 
     * @return List of enabled detectors
     */
    public List<PIIDetector> getEnabledDetectors() {
        return detectors.stream()
            .filter(PIIDetector::isEnabled)
            .toList();
    }
    
    /**
     * Gets detector by PII type.
     * 
     * @param piiType PII type name
     * @return Optional containing detector if found
     */
    public Optional<PIIDetector> getDetector(String piiType) {
        return detectors.stream()
            .filter(d -> d.getPIIType().equals(piiType))
            .findFirst();
    }
}
```

---

## 4. Refactored PIIDetectionService

```java
package com.ai.infrastructure.privacy.pii;

import com.ai.infrastructure.privacy.pii.detector.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PII detection service with registry-based detector pattern.
 * 
 * <p><strong>Clean Design:</strong></p>
 * <ul>
 *   <li>Delegates to individual detectors (single responsibility)</li>
 *   <li>No hardcoded patterns (open/closed principle)</li>
 *   <li>Easy to add new detectors (just implement interface)</li>
 *   <li>Easy to test (test each detector independently)</li>
 * </ul>
 * 
 * <p><strong>Adding New PII Type:</strong></p>
 * <pre>{@code
 * // 1. Create detector
 * @Component
 * public class PassportDetector implements PIIDetector { ... }
 * 
 * // 2. That's it! Auto-discovered and used.
 * //    NO changes to PIIDetectionService needed!
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PIIDetectionService {
    
    // Token format constants
    private static final String TOKEN_FORMAT = "[%s_REDACTED_%s]";
    
    private final PIIDetectorRegistry detectorRegistry;
    
    // Thread-local token registry (request-scoped)
    private final ThreadLocal<Map<String, PIIToken>> tokenRegistry = 
        ThreadLocal.withInitial(HashMap::new);
    
    /**
     * Detects and tokenizes ALL PII types using registered detectors.
     * 
     * <p><strong>Extensibility:</strong> Automatically uses all registered PIIDetector
     * implementations. Add new detectors without modifying this method!</p>
     * 
     * @param request Detection request
     * @return Result with tokenized content
     */
    public PIIDetectionResult detectAndTokenize(PIIDetectionRequest request) {
        String content = request.getContent();
        String processedContent = content;
        List<String> detectedTypes = new ArrayList<>();
        Map<String, PIIToken> tokens = new HashMap<>();
        
        // Iterate through ALL registered detectors
        for (PIIDetector detector : detectorRegistry.getEnabledDetectors()) {
            
            // Detect PII instances
            List<DetectedPII> detected = detector.detect(processedContent);
            
            if (detected.isEmpty()) {
                continue;  // No PII of this type found
            }
            
            // Tokenize each detected instance
            for (DetectedPII pii : detected) {
                PIIToken token = createToken(pii.getPiiType(), pii.getValue());
                
                // Replace in content
                processedContent = processedContent.replace(pii.getValue(), token.getTokenString());
                
                // Store token
                tokens.put(token.getTokenId(), token);
                
                // Track type
                if (!detectedTypes.contains(pii.getPiiType())) {
                    detectedTypes.add(pii.getPiiType());
                }
                
                log.debug("{} detected and tokenized: token={} (value not logged)",
                    pii.getPiiType(), token.getTokenString());
            }
        }
        
        // Store in thread-local registry
        tokenRegistry.get().putAll(tokens);
        
        log.info("PII detection complete: {} types detected, {} tokens created",
            detectedTypes.size(), tokens.size());
        
        return PIIDetectionResult.builder()
            .processedQuery(processedContent)
            .detectedTypes(detectedTypes)
            .isPiiDetected(!detectedTypes.isEmpty())
            .tokenCount(tokens.size())
            .build();
    }
    
    /**
     * Creates a PII token.
     */
    private PIIToken createToken(String piiType, String actualValue) {
        String tokenId = UUID.randomUUID().toString().substring(0, 8);
        String tokenString = String.format(TOKEN_FORMAT, piiType, tokenId);
        
        return PIIToken.builder()
            .tokenId(tokenId)
            .tokenString(tokenString)
            .piiType(piiType)
            .actualValue(actualValue)
            .createdAt(System.currentTimeMillis())
            .build();
    }
    
    /**
     * Retrieves original PII value from token.
     */
    public String getOriginalValue(String tokenString) {
        String tokenId = extractTokenId(tokenString);
        if (tokenId == null) {
            return null;
        }
        
        PIIToken token = tokenRegistry.get().get(tokenId);
        return token != null ? token.getActualValue() : null;
    }
    
    /**
     * Checks if value is a PII token.
     */
    public boolean isPIIToken(String value) {
        return value != null && value.matches("\\[.+_REDACTED_.+\\]");
    }
    
    /**
     * Extracts token ID from token string.
     */
    private String extractTokenId(String tokenString) {
        if (!isPIIToken(tokenString)) {
            return null;
        }
        
        int lastUnderscore = tokenString.lastIndexOf('_');
        int closeBracket = tokenString.indexOf(']');
        
        if (lastUnderscore > 0 && closeBracket > lastUnderscore) {
            return tokenString.substring(lastUnderscore + 1, closeBracket);
        }
        
        return null;
    }
    
    /**
     * Clears token registry.
     * CRITICAL: Must be called after query execution.
     */
    public void clearTokenRegistry() {
        Map<String, PIIToken> registry = tokenRegistry.get();
        if (!registry.isEmpty()) {
            log.debug("Clearing {} PII tokens from registry", registry.size());
            registry.clear();
        }
        tokenRegistry.remove();
    }
}

/**
 * PII Token data structure.
 */
@Data
@Builder
class PIIToken {
    private String tokenId;        // "abc123"
    private String tokenString;    // "[EMAIL_REDACTED_abc123]"
    private String piiType;        // "EMAIL"
    private String actualValue;    // Real PII value
    private long createdAt;        // Timestamp
}
```

---

## 5. Adding New PII Detectors

### Example: Adding Passport Number Detector

**Just create a new class:**

```java
package com.ai.infrastructure.privacy.pii.detector;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.regex.*;

/**
 * Detects passport numbers.
 * Pattern: 2 letters + 7 digits (e.g., US passport: AB1234567)
 */
@Component  // ← That's it! Auto-discovered.
public class PassportDetector implements PIIDetector {
    
    private static final String PII_TYPE = "PASSPORT";
    private static final String FIELD_NAME = "passportNumber";
    
    private static final Pattern PASSPORT_PATTERN = Pattern.compile(
        "\\b[A-Z]{2}\\d{7}\\b"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        List<DetectedPII> detected = new ArrayList<>();
        Matcher matcher = PASSPORT_PATTERN.matcher(content);
        
        while (matcher.find()) {
            detected.add(DetectedPII.builder()
                .piiType(PII_TYPE)
                .value(matcher.group())
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .fieldName(FIELD_NAME)
                .build());
        }
        
        return detected;
    }
    
    @Override
    public String getPIIType() {
        return PII_TYPE;
    }
    
    @Override
    public String getFieldName() {
        return FIELD_NAME;
    }
}
```

**NO changes needed anywhere else!**
- ✅ Spring auto-discovers `@Component`
- ✅ Registry auto-includes it
- ✅ Detection service auto-uses it

---

## 6. Benefits of This Design

### 6.1 Comparison

| Aspect | Old (Monolithic) | New (Registry) |
|--------|------------------|----------------|
| **Adding new PII type** | Modify `detectAndTokenize()` | Create new detector class |
| **Method size** | Grows unbounded | Stays constant (~50 lines) |
| **Testing** | Test giant method | Test each detector independently |
| **Enable/Disable** | Hard (comment out code) | Easy (detector.isEnabled()) |
| **Code reuse** | Duplicate logic | Single responsibility classes |
| **Extensibility** | Closed (edit core) | Open (add components) |

### 6.2 Code Metrics

**Before:**
```
PIIDetectionService.detectAndTokenize(): 200+ lines
  - Email detection: 20 lines
  - Card detection: 20 lines
  - Phone detection: 20 lines
  - SSN detection: 20 lines
  - DOB detection: 20 lines
  - Add 5 more types: +100 lines → 300+ line method!
```

**After:**
```
PIIDetectionService.detectAndTokenize(): 50 lines (constant!)
EmailDetector: 40 lines
CreditCardDetector: 40 lines
PhoneDetector: 40 lines
SSNDetector: 40 lines
DateOfBirthDetector: 50 lines
PIIDetectorRegistry: 60 lines

Add 5 more types: +200 lines spread across 5 files
Core method: Still 50 lines ✅
```

### 6.3 Open/Closed Principle

**Open for extension:**
```java
// Add new detector - NO modifications to existing code
@Component
public class DriversLicenseDetector implements PIIDetector { }

@Component
public class IPAddressDetector implements PIIDetector { }

@Component
public class BankAccountDetector implements PIIDetector { }
```

**Closed for modification:**
```java
// PIIDetectionService.detectAndTokenize() NEVER changes
// PIIDetectorRegistry NEVER changes
// Existing detectors NEVER change
```

---

## 7. Configuration

### 7.1 Enable/Disable Specific Detectors

```yaml
ai:
  privacy:
    pii:
      enabled: true
      tokenization-enabled: true
      
      # Enable/disable specific detector types
      detectors:
        email: true
        credit-card: true
        phone: true
        ssn: true
        date-of-birth: true
        passport: false  # Disabled
```

**Implementation:**

```java
@Component
@ConditionalOnProperty(
    prefix = "ai.privacy.pii.detectors",
    name = "email",
    havingValue = "true",
    matchIfMissing = true
)
public class EmailDetector implements PIIDetector {
    // Only loaded if enabled in config
}
```

---

## 8. Testing Strategy

### 8.1 Test Each Detector Independently

```java
@DisplayName("EmailDetector Tests")
class EmailDetectorTest {
    
    private EmailDetector detector = new EmailDetector();
    
    @Test
    void shouldDetectSingleEmail() {
        List<DetectedPII> detected = detector.detect("Contact john@example.com");
        
        assertThat(detected).hasSize(1);
        assertThat(detected.get(0).getValue()).isEqualTo("john@example.com");
        assertThat(detected.get(0).getPiiType()).isEqualTo("EMAIL");
    }
    
    @Test
    void shouldDetectMultipleEmails() {
        List<DetectedPII> detected = detector.detect("Email john@example.com or jane@example.com");
        
        assertThat(detected).hasSize(2);
    }
    
    @Test
    void shouldNotDetectInvalidEmails() {
        List<DetectedPII> detected = detector.detect("Not an email: invalid@");
        
        assertThat(detected).isEmpty();
    }
}
```

### 8.2 Test Integration

```java
@DisplayName("PIIDetectionService Integration Tests")
class PIIDetectionServiceTest {
    
    @Test
    void shouldUseAllRegisteredDetectors() {
        // Arrange
        PIIDetectorRegistry registry = new PIIDetectorRegistry(List.of(
            new EmailDetector(),
            new CreditCardDetector(),
            new PhoneDetector()
        ));
        
        PIIDetectionService service = new PIIDetectionService(registry);
        
        String query = "Contact john@example.com or call 555-1234 with card 4532-1234-5678-9010";
        
        // Act
        PIIDetectionResult result = service.detectAndTokenize(
            PIIDetectionRequest.builder().content(query).build()
        );
        
        // Assert
        assertThat(result.getDetectedTypes())
            .containsExactlyInAnyOrder("EMAIL", "PHONE", "CREDIT_CARD");
        assertThat(result.getTokenCount()).isEqualTo(3);
        assertThat(result.getProcessedQuery())
            .contains("[EMAIL_REDACTED_")
            .contains("[PHONE_REDACTED_")
            .contains("[CARD_REDACTED_")
            .doesNotContain("john@example.com")
            .doesNotContain("555-1234")
            .doesNotContain("4532-1234-5678-9010");
    }
}
```

---

## 9. Future Extensibility

### 9.1 Adding New Detector (Zero Core Changes)

**Step 1: Create Detector**
```java
@Component
public class IPAddressDetector implements PIIDetector {
    private static final Pattern IP_PATTERN = Pattern.compile(
        "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        // Implementation
    }
    
    @Override
    public String getPIIType() {
        return "IP_ADDRESS";
    }
    
    @Override
    public String getFieldName() {
        return "ipAddress";
    }
}
```

**Step 2: Done!**

Spring auto-discovers it → Registry includes it → Service uses it

**NO modifications to:**
- ❌ PIIDetectionService
- ❌ PIIDetectorRegistry
- ❌ Existing detectors
- ❌ Configuration (unless you want to disable it)

### 9.2 Custom Detector (User-Provided)

Users can add their own detectors:

```java
package com.myapp.security;

import com.ai.infrastructure.privacy.pii.detector.*;
import org.springframework.stereotype.Component;

/**
 * Custom detector for company-specific employee IDs.
 */
@Component
public class EmployeeIDDetector implements PIIDetector {
    
    // Pattern: EMP-XXXXX
    private static final Pattern PATTERN = Pattern.compile("\\bEMP-\\d{5}\\b");
    
    @Override
    public List<DetectedPII> detect(String content) {
        // Custom implementation
    }
    
    @Override
    public String getPIIType() {
        return "EMPLOYEE_ID";
    }
    
    @Override
    public String getFieldName() {
        return "employeeId";
    }
}
```

**Framework automatically uses it!**

---

## 10. Summary

### Changes Made:

**Before:**
```java
// One giant method with all patterns
public PIIDetectionResult detectAndTokenize(String query) {
    // 200+ lines
    // Hard to extend
    // Hard to test
}
```

**After:**
```java
// Clean service with registry
public PIIDetectionResult detectAndTokenize(String query) {
    for (PIIDetector detector : registry.getEnabledDetectors()) {
        // Delegate to detector
    }
    // 50 lines (constant size!)
}

// Individual detector classes
EmailDetector: 40 lines
CreditCardDetector: 40 lines
PhoneDetector: 40 lines
// Easy to add more
```

### Benefits:

✅ **Solid Implementation** - Single Responsibility Principle  
✅ **Clear** - Each detector is self-contained  
✅ **Minimal Changes** - Add detectors without touching core  
✅ **Testable** - Test each detector independently  
✅ **Extensible** - Open/Closed Principle  
✅ **Maintainable** - Small, focused classes  
✅ **Configurable** - Enable/disable per detector  

### To Add New PII Type:

```java
// 1. Create detector class (40 lines)
@Component
public class NewPIIDetector implements PIIDetector { ... }

// 2. Done!
```

**Zero changes to core logic!**

---

**Document Version:** 1.0  
**Pattern:** Registry + Strategy  
**Compliance:** SOLID Principles  
**Status:** ✅ Production Ready

