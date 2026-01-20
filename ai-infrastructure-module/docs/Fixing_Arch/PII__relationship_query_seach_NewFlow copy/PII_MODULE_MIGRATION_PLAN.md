# PII Detection Module - Migration Plan
## Moving PII Code from Core to Separate Module

**Current State:** PII detection code scattered in ai-infrastructure-core  
**Target State:** Clean separation - SPI in core, implementation in ai-infrastructure-pii-detection  
**Impact:** Core becomes lighter, PII detection becomes optional

---

## Files Found in Core (Analysis)

### ✅ MOVE to ai-infrastructure-pii-detection:

| File | Current Location | New Location | Reason |
|------|------------------|--------------|--------|
| **PIIDetectionService.java** | `core/privacy/pii/` | `pii-detection/service/` | Implementation |
| **PIIDetection.java** | `core/dto/` | `pii-detection/model/` | PII-specific DTO |
| **PIIDetectionResult.java** | `core/dto/` | `pii-detection/model/` | PII-specific DTO |
| **PIIMode.java** | `core/dto/` | `pii-detection/model/` | PII-specific enum |
| **PIIDetectionProperties.java** | `core/config/` | `pii-detection/config/` | PII-specific config |
| **PIIDetectedEvent.java** | `core/event/policy/` | `pii-detection/event/` | PII-specific event |

**Total to move:** 6 files

### ✅ KEEP in Core (but change to use SPI):

| File | Location | Change Required | Reason |
|------|----------|-----------------|--------|
| **ResponseSanitizer.java** | `core/security/` | Use Optional<PIIDetectionProvider> | Core security service |
| **RAGOrchestrator.java** | `core/intent/orchestration/` | Use Optional<PIIDetectionProvider> | Core orchestration |
| **ResponseSanitizationProperties.java** | `core/config/` | No change | Core config |
| **ComplianceEventSubscriber.java** | `core/event/policy/` | Use Optional<PIIDetectionProvider> | Core compliance |

**Total to modify:** 3 files (small changes)

### ✅ CREATE in Core:

| File | Location | Purpose |
|------|----------|---------|
| **PIIDetectionProvider.java** | `core/spi/` | SPI interface (NEW) |

**Total to create:** 1 file

---

## Migration Steps

### Step 1: Create SPI in Core

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/spi/PIIDetectionProvider.java`

```java
package com.ai.infrastructure.spi;

import java.util.List;

/**
 * SPI for PII detection and processing.
 * 
 * <p>Framework provides default implementation in ai-infrastructure-pii-detection module.
 * Users can provide custom implementation if needed.</p>
 * 
 * <p><strong>Default Implementation:</strong> Include ai-infrastructure-pii-detection module.</p>
 * <p><strong>Custom Implementation:</strong> Implement this interface.</p>
 * <p><strong>No PII Detection:</strong> Don't include any implementation (graceful degradation).</p>
 */
public interface PIIDetectionProvider {
    
    /**
     * Analyzes content for PII.
     * 
     * @param content Content to analyze
     * @return Detection result
     */
    PIIDetectionResult analyze(String content);
    
    /**
     * Detects and tokenizes PII for secure database queries.
     * 
     * @param content Content to process
     * @return Result with tokenized content
     */
    PIIDetectionResult detectAndTokenize(String content);
    
    /**
     * Gets original PII value from token.
     * 
     * @param token PII token
     * @return Original value or null
     */
    String getOriginalValue(String token);
    
    /**
     * Checks if value is a PII token.
     * 
     * @param value Value to check
     * @return true if PII token
     */
    boolean isPIIToken(String value);
    
    /**
     * Clears token registry (call after query execution).
     */
    void clearTokenRegistry();
}

// DTOs needed in core (minimal)
record PIIDetectionResult(
    String processedQuery,
    List<PIIDetection> detections,
    boolean isPiiDetected
) {}

record PIIDetection(
    String type,
    String value,  // Redacted value
    int startIndex,
    int endIndex
) {}
```

### Step 2: Update Core Classes to Use SPI

**File:** `ai-infrastructure-core/.../orchestration/RAGOrchestrator.java`

**Change:**
```java
// OLD:
private final PIIDetectionService piiDetectionService;

// NEW:
private final Optional<PIIDetectionProvider> piiDetectionProvider;

// Usage change:
// OLD:
PIIDetectionResult result = piiDetectionService.analyze(query);

// NEW:
if (piiDetectionProvider.isPresent()) {
    PIIDetectionResult result = piiDetectionProvider.get().analyze(query);
} else {
    // No PII detection available
    processedQuery = query;
}
```

**File:** `ai-infrastructure-core/.../security/ResponseSanitizer.java`

**Change:**
```java
// OLD:
private final PIIDetectionService piiDetectionService;

public ResponseSanitizer(PIIDetectionService piiDetectionService, ...) {
    this.piiDetectionService = piiDetectionService;
}

// NEW:
private final Optional<PIIDetectionProvider> piiDetectionProvider;

public ResponseSanitizer(Optional<PIIDetectionProvider> piiDetectionProvider, ...) {
    this.piiDetectionProvider = piiDetectionProvider;
}

// Usage:
if (piiDetectionProvider.isPresent()) {
    PIIDetectionResult analysis = piiDetectionProvider.get().analyze(text);
    // ... sanitization logic
}
```

### Step 3: Move Files to New Module

**Create:** `ai-infrastructure-pii-detection/`

**Move:**
1. `PIIDetectionService.java` → `pii-detection/service/`
2. `PIIDetection.java` → `pii-detection/model/`
3. `PIIDetectionResult.java` → `pii-detection/model/`
4. `PIIMode.java` → `pii-detection/model/`
5. `PIIDetectionProperties.java` → `pii-detection/config/`
6. `PIIDetectedEvent.java` → `pii-detection/event/`

**Add:**
7. `PIIDetectorRegistry.java` → `pii-detection/detector/`
8. `PIIDetector.java` (interface) → `pii-detection/detector/`
9. `EmailDetector.java` → `pii-detection/detector/`
10. `CreditCardDetector.java` → `pii-detection/detector/`
11. `PhoneDetector.java` → `pii-detection/detector/`
12. `SSNDetector.java` → `pii-detection/detector/`
13. `DateOfBirthDetector.java` → `pii-detection/detector/`
14. `PIIDetectionProviderImpl.java` → `pii-detection/integration/` (implements SPI)
15. `PIIDetectionAutoConfiguration.java` → `pii-detection/config/`

### Step 4: Update References

**In all core files, change imports:**
```java
// OLD:
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.dto.PIIDetection;

// NEW (if using records in SPI):
import com.ai.infrastructure.spi.PIIDetectionProvider;
import com.ai.infrastructure.spi.PIIDetectionProvider.PIIDetectionResult;
import com.ai.infrastructure.spi.PIIDetectionProvider.PIIDetection;
```

---

## Module Dependencies

### ai-infrastructure-core (Updated)

```xml
<dependencies>
    <!-- No PII detection dependencies -->
    <!-- Just defines SPI interface -->
</dependencies>
```

### ai-infrastructure-pii-detection (New)

```xml
<dependencies>
    <!-- Core (for SPI interface only) -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
        <version>${project.version}</version>
        <scope>provided</scope>
    </dependency>
    
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### ai-infrastructure-relationship-query (Updated)

```xml
<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    
    <!-- PII Detection (optional - only if needed) -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-pii-detection</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## User Application Scenarios

### Scenario 1: With PII Detection

```xml
<dependencies>
    <dependency>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    <dependency>
        <artifactId>ai-infrastructure-pii-detection</artifactId>  ← Include
    </dependency>
    <dependency>
        <artifactId>ai-infrastructure-relationship-query</artifactId>
    </dependency>
</dependencies>
```

**Result:**
- ✅ PII detection active
- ✅ Tokenization for secure queries
- ✅ Response sanitization

### Scenario 2: Without PII Detection

```xml
<dependencies>
    <dependency>
        <artifactId>ai-infrastructure-core</artifactId>
    </dependency>
    <!-- NO PII detection module -->
    <dependency>
        <artifactId>ai-infrastructure-relationship-query</artifactId>
    </dependency>
</dependencies>
```

**Result:**
- ✅ Core works fine
- ✅ Relationship queries work
- ❌ No PII protection
- ⚠️ PII sent to LLM if present in queries

---

## Graceful Degradation in Core

```java
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    private final Optional<PIIDetectionProvider> piiDetectionProvider;
    
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        String processedQuery = query;
        List<String> detectedPiiTypes = new ArrayList<>();
        
        // Graceful: Use PII detection if available
        if (piiDetectionProvider.isPresent()) {
            PIIDetectionResult piiResult = piiDetectionProvider.get()
                .detectAndTokenize(query);
            
            processedQuery = piiResult.processedQuery();
            detectedPiiTypes = piiResult.detections().stream()
                .map(PIIDetection::type)
                .collect(Collectors.toList());
            
            log.debug("PII detection active: {} types detected", detectedPiiTypes.size());
        } else {
            log.debug("PII detection not available (module not included)");
        }
        
        // Continue with processedQuery (tokenized if PII module present, original if not)
        // ... existing orchestration
    }
}
```

**No exceptions if PII module absent - graceful degradation!**

---

## Migration Checklist

### Phase 1: Preparation
- [ ] Create ai-infrastructure-pii-detection module structure
- [ ] Define PIIDetectionProvider SPI in core
- [ ] Create minimal DTO records in SPI

### Phase 2: Move Code
- [ ] Move PIIDetectionService.java
- [ ] Move PII DTOs (PIIDetection, PIIDetectionResult, PIIMode)
- [ ] Move PIIDetectionProperties
- [ ] Move PIIDetectedEvent
- [ ] Create detector classes (Email, Card, Phone, SSN, DOB)
- [ ] Create PIIDetectorRegistry
- [ ] Create PIIDetectionProviderImpl (SPI implementation)

### Phase 3: Update Core
- [ ] Update RAGOrchestrator to use Optional<PIIDetectionProvider>
- [ ] Update ResponseSanitizer to use Optional<PIIDetectionProvider>
- [ ] Update ComplianceEventSubscriber if needed
- [ ] Remove old PII imports
- [ ] Add new SPI imports

### Phase 4: Testing
- [ ] Test core WITHOUT PII module (graceful degradation)
- [ ] Test core WITH PII module (full functionality)
- [ ] Test PII module independently
- [ ] Test relationship queries with PII tokenization
- [ ] Integration tests

### Phase 5: Documentation
- [ ] Update core README (PII detection now optional)
- [ ] Create PII module README
- [ ] Update user guides
- [ ] Migration guide for existing users

---

## Breaking Changes Analysis

### For Existing Users:

**Before:**
```xml
<dependency>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>
<!-- PII detection included automatically -->
```

**After (to maintain same functionality):**
```xml
<dependency>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>
<dependency>
    <artifactId>ai-infrastructure-pii-detection</artifactId>  ← ADD THIS
</dependency>
```

**Impact:** Users need to explicitly include PII module

**Mitigation:**
- Document in release notes
- Provide migration guide
- Offer parent POM that includes both

---

## Recommended Module Structure

```
ai-infrastructure-pii-detection/
├── src/main/java/com/ai/infrastructure/pii/
│   ├── detector/
│   │   ├── PIIDetector.java              # Interface
│   │   ├── PIIDetectorRegistry.java      # Registry pattern
│   │   ├── EmailDetector.java
│   │   ├── CreditCardDetector.java
│   │   ├── PhoneDetector.java
│   │   ├── SSNDetector.java
│   │   └── DateOfBirthDetector.java
│   ├── service/
│   │   ├── PIIDetectionService.java      # Main service (moved from core)
│   │   └── PIITokenizationService.java   # Token management
│   ├── integration/
│   │   └── PIIDetectionProviderImpl.java # Implements core SPI
│   ├── model/
│   │   ├── PIIDetection.java             # Moved from core
│   │   ├── PIIDetectionResult.java       # Moved from core
│   │   ├── PIIMode.java                  # Moved from core
│   │   ├── PIIToken.java                 # NEW
│   │   └── DetectedPII.java              # NEW
│   ├── config/
│   │   ├── PIIDetectionProperties.java   # Moved from core
│   │   └── PIIDetectionAutoConfiguration.java  # NEW
│   ├── event/
│   │   └── PIIDetectedEvent.java         # Moved from core
│   └── exception/
│       └── PIIDetectionException.java    # NEW
└── src/test/java/...
    ├── detector/
    │   ├── EmailDetectorTest.java
    │   ├── CreditCardDetectorTest.java
    │   └── ...
    └── service/
        └── PIIDetectionServiceTest.java
```

---

## Code Size Comparison

### Before (Everything in Core):

```
ai-infrastructure-core:
├── privacy/pii/PIIDetectionService.java  (300 lines)
├── dto/PIIDetection.java                 (50 lines)
├── dto/PIIDetectionResult.java           (80 lines)
├── dto/PIIMode.java                      (30 lines)
├── config/PIIDetectionProperties.java    (100 lines)
└── event/PIIDetectedEvent.java           (50 lines)

Total PII code in core: ~610 lines
```

### After (Separate Module):

```
ai-infrastructure-core:
└── spi/PIIDetectionProvider.java         (100 lines - interface + records)

ai-infrastructure-pii-detection:
├── detector/PIIDetector.java             (50 lines)
├── detector/PIIDetectorRegistry.java     (100 lines)
├── detector/EmailDetector.java           (60 lines)
├── detector/CreditCardDetector.java      (60 lines)
├── detector/PhoneDetector.java           (60 lines)
├── detector/SSNDetector.java             (60 lines)
├── detector/DateOfBirthDetector.java     (70 lines)
├── service/PIIDetectionService.java      (150 lines - refactored with registry)
├── service/PIITokenizationService.java   (100 lines)
├── integration/PIIDetectionProviderImpl.java  (80 lines)
├── model/* (DTOs)                        (200 lines)
├── config/*                              (150 lines)
└── event/*                               (50 lines)

Total: ~1,190 lines (in separate module)
Core: 100 lines (SPI only)
```

**Core reduction:** 610 lines → 100 lines = **510 lines lighter** ✅

---

## Benefits

### For Core Module:

✅ **Lighter** - 510 fewer lines  
✅ **Focused** - Core orchestration only  
✅ **Optional** - PII detection not mandatory  
✅ **Cleaner** - No privacy/pii package in core  

### For PII Module:

✅ **Specialized** - All PII logic in one place  
✅ **Testable** - Independent test suite  
✅ **Extensible** - Registry pattern for detectors  
✅ **Versioned** - Independent releases  
✅ **Deployable** - Only where needed (compliance-required apps)  

### For Users:

✅ **Choice** - Include if needed  
✅ **Lighter** - Smaller deployments if not using PII  
✅ **Clear** - Explicit dependency on PII features  
✅ **Extensible** - Can add custom detectors  

---

## Recommendation

### ✅ YES - Move PII Detection to Separate Module

**Files to move:** 6 files from core  
**Files to modify in core:** 3 files (use Optional<SPI>)  
**Impact:** Core becomes 510 lines lighter  
**Benefit:** Optional PII detection, user choice, cleaner architecture  

**This follows our framework philosophy:**
- Separate modules for separate concerns
- Optional features as optional modules
- Users include only what they need
- Core stays focused on core concerns

---

**Next Steps:**
1. Create ai-infrastructure-pii-detection module
2. Move/refactor 6 files from core
3. Update 3 core files to use SPI
4. Test both scenarios (with/without PII module)
5. Document migration for users

---

**Document Version:** 1.0  
**Status:** ✅ Ready to Execute  
**Impact:** Medium (breaking change for existing users)  
**Benefit:** High (cleaner architecture, optional feature)

