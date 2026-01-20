# PII Detection Module - Complete Implementation Specification
## Separate Module with Registry Pattern for Secure PII-Based Searches

**Module:** `ai-infrastructure-pii-detection`  
**Version:** 1.0  
**Status:** ✅ Ready for Implementation  
**Date:** January 2026

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Why Separate Module](#2-why-separate-module)
3. [Architecture Overview](#3-architecture-overview)
4. [Module Structure](#4-module-structure)
5. [Core SPI Interface](#5-core-spi-interface)
6. [Detector Registry Pattern](#6-detector-registry-pattern)
7. [Detector Implementations](#7-detector-implementations)
8. [PII Detection Service](#8-pii-detection-service)
9. [Query Builder Integration](#9-query-builder-integration)
10. [Migration Plan](#10-migration-plan)
11. [Configuration](#11-configuration)
12. [Testing](#12-testing)
13. [User Guide](#13-user-guide)

---

## 1. Executive Summary

### 1.1 The Problem

Users need to search by PII (email, credit card, phone, SSN, DOB) but sending PII to external LLMs violates compliance regulations.

### 1.2 The Solution

**PII Tokenization Flow:**
1. Detect PII in query → Generate token → Replace PII with token
2. Send tokenized query to LLM (LLM sees token, not real PII)
3. LLM generates plan using token
4. Query builder substitutes token with real PII
5. Database query uses real PII
6. Clear tokens after execution

### 1.3 The Design

**Separate Module** (`ai-infrastructure-pii-detection`) with:
- ✅ **Registry Pattern** - Extensible detector architecture
- ✅ **SPI Integration** - Clean interface with core
- ✅ **Optional** - Users include only if needed
- ✅ **Extensible** - Users can add custom detectors

---

## 2. Why Separate Module

### 2.1 Benefits

✅ **Optional Feature** - Not all apps need PII detection  
✅ **Lighter Core** - Core becomes 510 lines lighter  
✅ **User Choice** - Include only where needed  
✅ **Compliance** - Deploy only in regulated environments  
✅ **Independent** - Version/test/deploy separately  
✅ **Extensible** - Users add custom detectors  

### 2.2 Comparison

| Aspect | In Core | Separate Module |
|--------|---------|-----------------|
| Core size | +610 lines | ✅ -510 lines |
| Optional | ❌ Always loaded | ✅ User choice |
| For non-regulated apps | ❌ Unnecessary code | ✅ Exclude |
| Versioning | Coupled | ✅ Independent |
| Testing | With core | ✅ Independent |

---

## 3. Architecture Overview

### 3.1 Module Dependencies

```
┌─────────────────────────────────────────┐
│ ai-infrastructure-core                   │
│ Defines: PIIDetectionProvider (SPI)     │
│ Uses: Optional<PIIDetectionProvider>    │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ ai-infrastructure-pii-detection         │
│ Implements: PIIDetectionProvider        │
│ Provides: Detectors + Tokenization      │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ ai-infrastructure-relationship-query    │
│ Uses: PIIDetectionProvider (via core)  │
│ Substitutes tokens in query builder     │
└─────────────────────────────────────────┘
```

**No direct dependency between PII and Relationship Query!**

### 3.2 The Flow

```
User Query: "find customer with email john@example.com"
    ↓
RAGOrchestrator
    ↓
PIIDetectionProvider.detectAndTokenize()
    ↓
Tokenized: "find customer with email [EMAIL_REDACTED_abc123]"
Registry: {abc123: "john@example.com"}
    ↓
LLM Planning (sees token)
    ↓
Plan: {field: "email", value: "[EMAIL_REDACTED_abc123]"}
    ↓
Query Builder (substitutes token → real PII)
    ↓
JPQL: WHERE email = :p1
Params: {p1: "john@example.com"}
    ↓
Database Execution (uses real PII)
    ↓
Cleanup (clear tokens)
```

---

## 4. Module Structure

```
ai-infrastructure-pii-detection/
├── pom.xml
└── src/
    ├── main/java/com/ai/infrastructure/pii/
    │   ├── detector/
    │   │   ├── PIIDetector.java              # Interface
    │   │   ├── PIIDetectorRegistry.java      # Registry (auto-discovery)
    │   │   ├── EmailDetector.java            # 40 lines
    │   │   ├── CreditCardDetector.java       # 40 lines
    │   │   ├── PhoneDetector.java            # 40 lines
    │   │   ├── SSNDetector.java              # 40 lines
    │   │   └── DateOfBirthDetector.java      # 50 lines
    │   ├── service/
    │   │   ├── PIIDetectionService.java      # Main service (50 lines - constant!)
    │   │   └── PIITokenizationService.java   # Token management
    │   ├── integration/
    │   │   └── PIIDetectionProviderImpl.java # Implements core SPI
    │   ├── model/
    │   │   ├── PIIDetectionResult.java
    │   │   ├── PIIToken.java
    │   │   └── DetectedPII.java
    │   ├── config/
    │   │   ├── PIIDetectionProperties.java
    │   │   └── PIIDetectionAutoConfiguration.java
    │   ├── event/
    │   │   └── PIIDetectedEvent.java
    │   └── exception/
    │       └── PIIDetectionException.java
    └── test/java/com/ai/infrastructure/pii/
        ├── detector/
        │   ├── EmailDetectorTest.java
        │   ├── CreditCardDetectorTest.java
        │   └── ...
        └── service/
            └── PIIDetectionServiceTest.java
```

---

## 5. Core SPI Interface

### 5.1 Define in Core

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/spi/PIIDetectionProvider.java`

```java
package com.ai.infrastructure.spi;

import java.util.List;

/**
 * SPI for PII detection and tokenization.
 * 
 * <p>Framework provides default implementation in ai-infrastructure-pii-detection module.
 * Users can provide custom implementation.</p>
 * 
 * <p><strong>Usage:</strong></p>
 * <ul>
 *   <li>With PII module: Include ai-infrastructure-pii-detection dependency</li>
 *   <li>Without PII: Don't include (graceful degradation)</li>
 *   <li>Custom: Implement this interface</li>
 * </ul>
 */
public interface PIIDetectionProvider {
    
    /**
     * Analyzes content for PII (detection only).
     */
    PIIDetectionResult analyze(String content);
    
    /**
     * Detects and tokenizes PII for secure queries.
     * Tokens can be substituted with real values in query builder.
     */
    PIIDetectionResult detectAndTokenize(String content);
    
    /**
     * Retrieves original PII value from token.
     */
    String getOriginalValue(String token);
    
    /**
     * Checks if value is a PII token.
     */
    boolean isPIIToken(String value);
    
    /**
     * Clears token registry (call after query execution).
     */
    void clearTokenRegistry();
}

// Minimal DTOs in SPI
record PIIDetectionResult(
    String processedQuery,
    List<PIIDetection> detections,
    boolean isPiiDetected,
    int tokenCount
) {}

record PIIDetection(
    String type,
    String value,
    int startIndex,
    int endIndex
) {}
```

### 5.2 Use in Core

**File:** `ai-infrastructure-core/.../orchestration/RAGOrchestrator.java`

**Change:**
```java
// OLD:
private final PIIDetectionService piiDetectionService;

// NEW:
private final Optional<PIIDetectionProvider> piiDetectionProvider;

// Usage:
if (piiDetectionProvider.isPresent()) {
    PIIDetectionResult result = piiDetectionProvider.get().detectAndTokenize(query);
    processedQuery = result.processedQuery();
} else {
    log.debug("PII detection not available");
    processedQuery = query;
}
```

---

## 6. Detector Registry Pattern

### 6.1 PIIDetector Interface

```java
package com.ai.infrastructure.pii.detector;

import com.ai.infrastructure.pii.model.DetectedPII;
import java.util.List;

/**
 * Strategy interface for detecting specific PII patterns.
 * 
 * <p>Implementations detect ONE type of PII.</p>
 * 
 * <p><strong>To add new PII type:</strong></p>
 * <ol>
 *   <li>Implement this interface</li>
 *   <li>Annotate with @Component</li>
 *   <li>Done! Auto-discovered and used</li>
 * </ol>
 */
public interface PIIDetector {
    
    /**
     * Detects PII in content.
     */
    List<DetectedPII> detect(String content);
    
    /**
     * PII type name (e.g., "EMAIL", "CREDIT_CARD").
     */
    String getPIIType();
    
    /**
     * Database field name (e.g., "email", "cardNumber").
     */
    String getFieldName();
    
    /**
     * Whether detector is enabled (default: true).
     */
    default boolean isEnabled() {
        return true;
    }
}
```

### 6.2 PIIDetectorRegistry

```java
package com.ai.infrastructure.pii.detector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * Registry with automatic detector discovery via Spring.
 * 
 * <p><strong>Zero modifications needed when adding detectors!</strong></p>
 */
@Slf4j
@Service
public class PIIDetectorRegistry {
    
    private final List<PIIDetector> detectors;
    
    /**
     * Spring injects ALL PIIDetector implementations automatically.
     */
    public PIIDetectorRegistry(List<PIIDetector> detectors) {
        this.detectors = detectors != null ? detectors : List.of();
        
        log.info("PIIDetectorRegistry initialized with {} detectors:", this.detectors.size());
        this.detectors.forEach(d -> 
            log.info("  - {} (field: {})", d.getPIIType(), d.getFieldName())
        );
    }
    
    public List<PIIDetector> getAllDetectors() {
        return Collections.unmodifiableList(detectors);
    }
    
    public List<PIIDetector> getEnabledDetectors() {
        return detectors.stream().filter(PIIDetector::isEnabled).toList();
    }
}
```

---

## 7. Detector Implementations

### 7.1 EmailDetector (Framework Provides)

```java
package com.ai.infrastructure.pii.detector;

@Component
public class EmailDetector implements PIIDetector {
    
    private static final String PII_TYPE = "EMAIL";
    private static final String FIELD_NAME = "email";
    private static final Pattern PATTERN = Pattern.compile(
        "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );
    
    @Override
    public List<DetectedPII> detect(String content) {
        List<DetectedPII> detected = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(content);
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
    public String getPIIType() { return PII_TYPE; }
    
    @Override
    public String getFieldName() { return FIELD_NAME; }
}
```

**Similar implementations for:**
- CreditCardDetector (16 digits with separators)
- PhoneDetector (various phone formats)
- SSNDetector (XXX-XX-XXXX)
- DateOfBirthDetector (YYYY-MM-DD with context keywords)

---

## 8. PII Detection Service

```java
package com.ai.infrastructure.pii.service;

/**
 * PII detection service using registry pattern.
 * 
 * <p><strong>Key Feature:</strong> Method NEVER grows when adding new PII types!</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PIIDetectionService {
    
    private static final String TOKEN_FORMAT = "[%s_REDACTED_%s]";
    
    private final PIIDetectorRegistry detectorRegistry;
    private final ThreadLocal<Map<String, PIIToken>> tokenRegistry = 
        ThreadLocal.withInitial(HashMap::new);
    
    /**
     * Detects and tokenizes ALL PII using registered detectors.
     * 
     * <p>THIS METHOD NEVER CHANGES - Always ~50 lines!</p>
     */
    public PIIDetectionResult detectAndTokenize(String content) {
        String processedContent = content;
        List<String> detectedTypes = new ArrayList<>();
        Map<String, PIIToken> tokens = new HashMap<>();
        
        // Iterate ALL registered detectors (auto-discovered)
        for (PIIDetector detector : detectorRegistry.getEnabledDetectors()) {
            List<DetectedPII> detected = detector.detect(processedContent);
            
            for (DetectedPII pii : detected) {
                PIIToken token = createToken(pii.getPiiType(), pii.getValue());
                processedContent = processedContent.replace(pii.getValue(), token.getTokenString());
                tokens.put(token.getTokenId(), token);
                
                if (!detectedTypes.contains(pii.getPiiType())) {
                    detectedTypes.add(pii.getPiiType());
                }
            }
        }
        
        tokenRegistry.get().putAll(tokens);
        
        return PIIDetectionResult.builder()
            .processedQuery(processedContent)
            .detectedTypes(detectedTypes)
            .isPiiDetected(!detectedTypes.isEmpty())
            .tokenCount(tokens.size())
            .build();
    }
    
    public String getOriginalValue(String tokenString) {
        String tokenId = extractTokenId(tokenString);
        PIIToken token = tokenRegistry.get().get(tokenId);
        return token != null ? token.getActualValue() : null;
    }
    
    public boolean isPIIToken(String value) {
        return value != null && value.matches("\\[.+_REDACTED_.+\\]");
    }
    
    public void clearTokenRegistry() {
        tokenRegistry.get().clear();
        tokenRegistry.remove();
    }
    
    private PIIToken createToken(String piiType, String actualValue) {
        String tokenId = UUID.randomUUID().toString().substring(0, 8);
        return PIIToken.builder()
            .tokenId(tokenId)
            .tokenString(String.format(TOKEN_FORMAT, piiType, tokenId))
            .piiType(piiType)
            .actualValue(actualValue)
            .build();
    }
    
    private String extractTokenId(String tokenString) {
        int lastUnderscore = tokenString.lastIndexOf('_');
        int closeBracket = tokenString.indexOf(']');
        return tokenString.substring(lastUnderscore + 1, closeBracket);
    }
}
```

**Key Point:** This method stays ~50 lines regardless of how many PII types are added!

---

## 9. Query Builder Integration

```java
package com.ai.infrastructure.relationship.service;

@Service
@RequiredArgsConstructor
public class DynamicJPAQueryBuilder {
    
    // Access PII detection via core SPI
    private final Optional<PIIDetectionProvider> piiDetectionProvider;
    
    public JpqlQuery buildQuery(RelationshipQueryPlan plan) {
        // ... build SELECT, FROM, JOIN ...
        
        // Build WHERE with token substitution
        for (FilterCondition filter : filters) {
            Object value = filter.getValue();
            
            // Check if value is PII token
            if (value instanceof String && 
                piiDetectionProvider.isPresent() && 
                piiDetectionProvider.get().isPIIToken((String) value)) {
                
                // Substitute with real PII
                String actualValue = piiDetectionProvider.get().getOriginalValue((String) value);
                parameters.put(paramName, actualValue);  // Real PII for database
            } else {
                parameters.put(paramName, value);  // Normal value
            }
        }
        
        return JpqlQuery.builder()
            .jpql(jpql)
            .parameters(parameters)
            .build();
    }
}
```

---

## 10. Migration Plan

### 10.1 Files to Move from Core

| File | From | To |
|------|------|-----|
| PIIDetectionService.java | `core/privacy/pii/` | `pii-detection/service/` |
| PIIDetection.java | `core/dto/` | `pii-detection/model/` |
| PIIDetectionResult.java | `core/dto/` | `pii-detection/model/` |
| PIIMode.java | `core/dto/` | `pii-detection/model/` |
| PIIDetectionProperties.java | `core/config/` | `pii-detection/config/` |
| PIIDetectedEvent.java | `core/event/policy/` | `pii-detection/event/` |

**Total:** 6 files (~610 lines)

### 10.2 Core Files to Update

| File | Change |
|------|--------|
| RAGOrchestrator.java | Use `Optional<PIIDetectionProvider>` |
| ResponseSanitizer.java | Use `Optional<PIIDetectionProvider>` |
| ComplianceEventSubscriber.java | Use `Optional<PIIDetectionProvider>` |

**Total:** 3 files (small changes)

### 10.3 New Files to Create

**In Core:**
- `spi/PIIDetectionProvider.java` (SPI interface)

**In PII Module:**
- `detector/PIIDetector.java` (interface)
- `detector/PIIDetectorRegistry.java`
- `detector/EmailDetector.java`
- `detector/CreditCardDetector.java`
- `detector/PhoneDetector.java`
- `detector/SSNDetector.java`
- `detector/DateOfBirthDetector.java`
- `integration/PIIDetectionProviderImpl.java` (SPI impl)
- `model/PIIToken.java`
- `model/DetectedPII.java`
- `config/PIIDetectionAutoConfiguration.java`

---

## 11. Configuration

```yaml
ai:
  pii:
    enabled: true                    # Enable PII detection module
    
    # Detection modes
    detection-direction: INPUT_OUTPUT  # INPUT, OUTPUT, INPUT_OUTPUT
    
    # Individual detectors (can disable specific types)
    detectors:
      email: true
      credit-card: true
      phone: true
      ssn: true
      date-of-birth: true
    
    # Tokenization
    tokenization-enabled: true       # For relationship queries
    
    # Security
    require-permission-for-pii-search: true
    audit-pii-searches: true
```

---

## 12. Testing

### 12.1 Detector Tests (5 files × 5 tests each = 25 tests)

```java
@DisplayName("EmailDetector")
class EmailDetectorTest {
    ✅ shouldDetectSingleEmail
    ✅ shouldDetectMultipleEmails  
    ✅ shouldNotDetectInvalidEmails
    ✅ shouldProvideCorrectFieldName
    ✅ shouldProvideCorrectPIIType
}

// Similar for: CreditCardDetector, PhoneDetector, SSNDetector, DateOfBirthDetector
```

### 12.2 Service Tests (10+ tests)

```java
@DisplayName("PIIDetectionService")
class PIIDetectionServiceTest {
    ✅ shouldUseAllRegisteredDetectors
    ✅ shouldTokenizeAllDetectedPII
    ✅ shouldStoreTokensInRegistry
    ✅ shouldRetrieveOriginalValues
    ✅ shouldClearRegistryAfterQuery
    ✅ shouldHandleMultiplePIITypes
    ✅ shouldHandleNoPIIDetected
    ✅ shouldGenerateUniqueTokens
}
```

### 12.3 Integration Tests (5+ tests)

```java
@DisplayName("PII Module Integration")
class PIIModuleIntegrationTest {
    ✅ shouldIntegrateWithRAGOrchestrator
    ✅ shouldTokenizeAndSubstituteInQueries
    ✅ shouldWorkWithoutPIIModule (graceful degradation)
    ✅ shouldSupportCustomDetectors
    ✅ shouldClearTokensAfterExecution
}
```

---

## 13. User Guide

### 13.1 Include PII Detection

**Step 1: Add Dependency**
```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-pii-detection</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Step 2: Enable**
```yaml
ai:
  pii:
    enabled: true
```

**Step 3: Use**
```java
// Automatically used by RAGOrchestrator and RelationshipQuery
// No code changes needed!
```

### 13.2 Add Custom Detector

```java
package com.myapp.security;

@Component
public class EmployeeIDDetector implements PIIDetector {
    
    private static final Pattern PATTERN = Pattern.compile("\\bEMP-\\d{5}\\b");
    
    @Override
    public List<DetectedPII> detect(String content) {
        // Implementation
    }
    
    @Override
    public String getPIIType() { return "EMPLOYEE_ID"; }
    
    @Override
    public String getFieldName() { return "employeeId"; }
}
```

**Done! Framework automatically uses it.**

---

## 14. Implementation Checklist

### Phase 1: Module Creation
- [ ] Create ai-infrastructure-pii-detection module structure
- [ ] Define PIIDetectionProvider SPI in core
- [ ] Create PIIDetector interface
- [ ] Create PIIDetectorRegistry

### Phase 2: Detectors
- [ ] Implement EmailDetector
- [ ] Implement CreditCardDetector
- [ ] Implement PhoneDetector
- [ ] Implement SSNDetector
- [ ] Implement DateOfBirthDetector

### Phase 3: Service
- [ ] Implement PIIDetectionService (registry-based)
- [ ] Implement PIITokenizationService
- [ ] Implement PIIDetectionProviderImpl (SPI)

### Phase 4: Core Migration
- [ ] Move 6 files from core to new module
- [ ] Update RAGOrchestrator to use Optional<SPI>
- [ ] Update ResponseSanitizer to use Optional<SPI>
- [ ] Remove old imports, add new SPI imports

### Phase 5: Integration
- [ ] Update DynamicJPAQueryBuilder for token substitution
- [ ] Test with PII module
- [ ] Test without PII module (graceful degradation)

### Phase 6: Testing
- [ ] Unit tests for each detector (25 tests)
- [ ] Service tests (10 tests)
- [ ] Integration tests (5 tests)
- [ ] End-to-end PII search tests

### Phase 7: Documentation
- [ ] Module README
- [ ] User guide (add custom detectors)
- [ ] Migration guide for existing users
- [ ] Update core README (PII now optional)

---

## 15. Summary

### What We're Building

**A separate PII detection module that:**

✅ Uses **Registry Pattern** - Extensible, maintainable  
✅ Implements **Core SPI** - Clean integration  
✅ Is **Optional** - Users include if needed  
✅ Provides **5 detectors** - Email, Card, Phone, SSN, DOB  
✅ Allows **custom detectors** - Users add their own  
✅ Keeps **core light** - 510 lines removed from core  
✅ Enables **secure PII searches** - Tokenization for databases  

### Key Benefits

| Benefit | Description |
|---------|-------------|
| **Solid** | Each detector is single responsibility |
| **Clear** | Self-contained detector classes |
| **Minimal changes** | Add detectors without touching core |
| **Extensible** | Open/Closed principle |
| **Optional** | Separate module, user choice |
| **Compliant** | GDPR, HIPAA, PCI-DSS friendly |

### To Add New PII Type

```java
// Create one file:
@Component
public class NewDetector implements PIIDetector { }

// Done! Zero other changes needed.
```

---

**Document Version:** 1.0 - Consolidated  
**Status:** ✅ Ready for Implementation  
**Supersedes:** PII_MODULE_ARCHITECTURE.md, PII_MODULE_MIGRATION_PLAN.md, PII_DETECTOR_PATTERN_DESIGN.md  

---

**This is the single source of truth. Implement exactly as specified.**

