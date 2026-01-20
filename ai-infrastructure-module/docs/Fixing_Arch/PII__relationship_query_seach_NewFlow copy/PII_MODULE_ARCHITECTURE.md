# PII Detection Module - Separate Module Architecture

**Module Name:** `ai-infrastructure-pii-detection`  
**Status:** ✅ Architecture Ready  
**Philosophy:** Optional feature, separate module, user-extensible

---

## Why Separate Module?

### Benefits:

✅ **Optional Feature** - Not all applications need PII detection  
✅ **Specialized Focus** - PII detection is a distinct concern  
✅ **Lighter Core** - Reduce ai-infrastructure-core size  
✅ **Independent Versioning** - Update PII detection without touching core  
✅ **User Choice** - Include only if needed  
✅ **Compliance** - Deploy only where regulations require  

### Follows Framework Pattern:

Same pattern as:
- `ai-infrastructure-relationship-query` (optional)
- `ai-infrastructure-behavior` (optional)
- `ai-infrastructure-chat-session` (optional)

---

## Module Structure

```
ai-infrastructure-module/
├── ai-infrastructure-core/                  # Core (PII detection removed)
│   └── spi/
│       └── PIIDetectionProvider.java        # SPI interface only
│
└── ai-infrastructure-pii-detection/        # NEW SEPARATE MODULE
    ├── pom.xml
    └── src/
        ├── main/java/com/ai/infrastructure/pii/
        │   ├── detector/
        │   │   ├── PIIDetector.java           # Interface
        │   │   ├── EmailDetector.java
        │   │   ├── CreditCardDetector.java
        │   │   ├── PhoneDetector.java
        │   │   ├── SSNDetector.java
        │   │   ├── DateOfBirthDetector.java
        │   │   └── PIIDetectorRegistry.java
        │   ├── service/
        │   │   ├── PIIDetectionService.java
        │   │   └── PIITokenizationService.java
        │   ├── integration/
        │   │   └── PIIDetectionProviderImpl.java  # Implements core SPI
        │   ├── model/
        │   │   ├── PIIDetectionResult.java
        │   │   ├── PIIToken.java
        │   │   └── DetectedPII.java
        │   ├── config/
        │   │   ├── PIIDetectionProperties.java
        │   │   └── PIIDetectionAutoConfiguration.java
        │   └── exception/
        │       └── PIIDetectionException.java
        └── test/java/...
```

---

## Core Module Changes (Minimal)

### 1. Define SPI in Core

```java
package com.ai.infrastructure.spi;

/**
 * SPI for PII detection and tokenization.
 * 
 * <p>Framework provides a default implementation in ai-infrastructure-pii-detection module.
 * Users can override with custom PII detection logic.</p>
 * 
 * <p><strong>Default Implementation:</strong> Use ai-infrastructure-pii-detection module.</p>
 * <p><strong>Custom Implementation:</strong> Implement this interface with your own logic.</p>
 */
public interface PIIDetectionProvider {
    
    /**
     * Detects PII in content and returns tokenized version.
     * 
     * @param content Content to scan
     * @param mode Detection mode (DETECT_ONLY, REDACT, TOKENIZE)
     * @return Detection result with processed content
     */
    PIIDetectionResult detectAndProcess(String content, PIIDetectionMode mode);
    
    /**
     * Retrieves original PII value from token.
     * 
     * @param token PII token string
     * @return Original value, or null if token not found
     */
    String getOriginalValue(String token);
    
    /**
     * Checks if value is a PII token.
     * 
     * @param value Value to check
     * @return true if value is a PII token
     */
    boolean isPIIToken(String value);
    
    /**
     * Clears token registry (must be called after query execution).
     */
    void clearTokenRegistry();
}

enum PIIDetectionMode {
    DETECT_ONLY,   // Just detect, don't modify
    REDACT,        // Replace with [REDACTED]
    TOKENIZE       // Replace with tokens (for database queries)
}
```

### 2. Use SPI in Core

```java
package com.ai.infrastructure.intent.orchestration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    // Optional PII detection (only if module included)
    private final Optional<PIIDetectionProvider> piiDetectionProvider;
    
    public OrchestrationResult orchestrate(String query, OrchestrationContext context) {
        // ... existing code ...
        
        String processedQuery = query;
        
        // If PII detection module present, use it
        if (piiDetectionProvider.isPresent()) {
            PIIDetectionResult piiResult = piiDetectionProvider.get()
                .detectAndProcess(query, PIIDetectionMode.TOKENIZE);
            
            processedQuery = piiResult.getProcessedContent();
            // ... use tokenized query
        }
        
        // ... rest of orchestration
    }
}
```

---

## PII Detection Module Implementation

### 1. Module Configuration

```java
package com.ai.infrastructure.pii.config;

import com.ai.infrastructure.pii.detector.*;
import com.ai.infrastructure.pii.integration.PIIDetectionProviderImpl;
import com.ai.infrastructure.pii.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Auto-configuration for PII Detection module.
 * 
 * <p><strong>Optional Module:</strong> Only active when:</p>
 * <ul>
 *   <li>Module is included in dependencies</li>
 *   <li>Configuration: ai.pii.enabled=true</li>
 * </ul>
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.ai.infrastructure.pii")
@EnableConfigurationProperties(PIIDetectionProperties.class)
@ConditionalOnProperty(
    prefix = "ai.pii",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true  // Enabled by default if module present
)
public class PIIDetectionAutoConfiguration {
    
    @Bean
    public PIIDetectorRegistry piiDetectorRegistry(List<PIIDetector> detectors) {
        log.info("Initializing PII Detector Registry with {} detectors", detectors.size());
        return new PIIDetectorRegistry(detectors);
    }
    
    @Bean
    public PIIDetectionService piiDetectionService(PIIDetectorRegistry registry) {
        log.info("Initializing PII Detection Service");
        return new PIIDetectionService(registry);
    }
    
    @Bean
    public PIIDetectionProviderImpl piiDetectionProvider(PIIDetectionService service) {
        log.info("Registering PII Detection Provider (SPI implementation)");
        return new PIIDetectionProviderImpl(service);
    }
}
```

### 2. SPI Implementation

```java
package com.ai.infrastructure.pii.integration;

import com.ai.infrastructure.spi.PIIDetectionProvider;
import com.ai.infrastructure.pii.service.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements core SPI for PII detection.
 * 
 * <p>Bridges PII detection module to core framework via SPI.</p>
 */
@Component
@RequiredArgsConstructor
public class PIIDetectionProviderImpl implements PIIDetectionProvider {
    
    private final PIIDetectionService piiDetectionService;
    
    @Override
    public PIIDetectionResult detectAndProcess(String content, PIIDetectionMode mode) {
        return switch (mode) {
            case TOKENIZE -> piiDetectionService.detectAndTokenize(content);
            case REDACT -> piiDetectionService.detectAndRedact(content);
            case DETECT_ONLY -> piiDetectionService.detectOnly(content);
        };
    }
    
    @Override
    public String getOriginalValue(String token) {
        return piiDetectionService.getOriginalValue(token);
    }
    
    @Override
    public boolean isPIIToken(String value) {
        return piiDetectionService.isPIIToken(value);
    }
    
    @Override
    public void clearTokenRegistry() {
        piiDetectionService.clearTokenRegistry();
    }
}
```

---

## Dependencies

### pom.xml (PII Detection Module)

```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-fabric-spring-boot-starter</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>ai-infrastructure-pii-detection</artifactId>
    <name>AI Infrastructure PII Detection Module</name>
    <description>PII detection, redaction, and tokenization for AI queries</description>
    
    <dependencies>
        <!-- Core (only for SPI interface) -->
        <dependency>
            <groupId>com.ai.fabric</groupId>
            <artifactId>ai-infrastructure-core</artifactId>
            <version>${project.version}</version>
            <scope>provided</scope>  <!-- Only need SPI interface -->
        </dependency>
        
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

---

## User Application Setup

### Scenario 1: With PII Detection (Recommended)

```xml
<dependencies>
    <!-- Core -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- PII Detection -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-pii-detection</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- Relationship Query -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-relationship-query</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

**Configuration:**
```yaml
ai:
  pii:
    enabled: true  # PII detection active
```

**Result:**
- ✅ PII detected and tokenized
- ✅ Secure searches by email, card, phone, etc.
- ✅ PII never sent to LLM

### Scenario 2: Without PII Detection

```xml
<dependencies>
    <!-- Core only -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    
    <!-- NO PII detection module -->
    
    <!-- Relationship Query -->
    <dependency>
        <groupId>com.ai.fabric</groupId>
        <artifactId>ai-infrastructure-relationship-query</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

**Result:**
- ✅ Core functionality works
- ❌ No PII tokenization
- ⚠️ PII would be sent to LLM if present in queries

---

## Module Interactions

```
┌─────────────────────────────────────────┐
│ User Application                         │
│                                          │
│ Uses:                                    │
│ - ai-infrastructure-core ✅              │
│ - ai-infrastructure-pii-detection ✅     │
│ - ai-infrastructure-relationship-query ✅│
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ ai-infrastructure-core                   │
│                                          │
│ Defines: PIIDetectionProvider (SPI)     │
│ Uses: Optional<PIIDetectionProvider>    │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ ai-infrastructure-pii-detection         │
│                                          │
│ Implements: PIIDetectionProvider (SPI)  │
│ Provides:                                │
│ - Email detector                         │
│ - Credit card detector                   │
│ - Phone detector                         │
│ - SSN detector                           │
│ - Date of birth detector                 │
│ - Tokenization service                   │
└────────────┬────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────┐
│ ai-infrastructure-relationship-query    │
│                                          │
│ Uses: PIIDetectionProvider (via core)  │
│ - Query builder substitutes tokens      │
└─────────────────────────────────────────┘
```

---

## Dependency Flow

```
Core Module (defines SPI):
  └─ PIIDetectionProvider interface

PII Module (implements SPI):
  └─ PIIDetectionProviderImpl implements PIIDetectionProvider

Relationship Query Module (uses SPI):
  └─ DynamicJPAQueryBuilder uses PIIDetectionProvider (via core)
```

**No direct dependency between PII and Relationship Query!**  
**Both depend on core (SPI), not on each other.**

---

## Adding Custom PII Detectors (User-Provided)

### Users can add their own detectors:

```java
package com.myapp.security;

import com.ai.infrastructure.pii.detector.PIIDetector;
import org.springframework.stereotype.Component;

/**
 * Custom detector for company-specific employee IDs.
 */
@Component  // ← Auto-discovered by PII module
public class EmployeeIDDetector implements PIIDetector {
    
    private static final Pattern PATTERN = Pattern.compile("\\bEMP-\\d{5}\\b");
    
    @Override
    public List<DetectedPII> detect(String content) {
        // Implementation
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

**Framework automatically includes it!**

---

## Configuration

### Module Enable/Disable

```yaml
# Scenario 1: PII detection enabled (module included)
ai:
  pii:
    enabled: true
    detectors:
      email: true
      credit-card: true
      phone: true
      ssn: true
      date-of-birth: true

# Scenario 2: PII detection disabled (module included but turned off)
ai:
  pii:
    enabled: false

# Scenario 3: Module not included (no configuration needed)
# ai.pii config section doesn't exist
```

---

## Benefits Summary

| Aspect | Monolithic (In Core) | Separate Module |
|--------|---------------------|-----------------|
| **Core Module Size** | Larger (+PII code) | ✅ Smaller |
| **Optional Feature** | Always loaded | ✅ Optional (don't include) |
| **User Choice** | Can't remove | ✅ Include if needed |
| **Compliance Apps** | Extra code if not needed | ✅ Add only where required |
| **Non-Compliance Apps** | Unnecessary dependency | ✅ Exclude entirely |
| **Versioning** | Coupled to core | ✅ Independent versions |
| **Testing** | Test with core | ✅ Independent test suite |
| **Deployment** | Always deployed | ✅ Deploy where needed |

---

## Migration Path

### Before (PII in Core):
```
ai-infrastructure-core/
└── privacy/pii/
    ├── PIIDetectionService.java
    ├── PIIDetector.java
    └── ...
```

### After (PII as Module):
```
ai-infrastructure-core/
└── spi/
    └── PIIDetectionProvider.java  # SPI only

ai-infrastructure-pii-detection/  # New module
└── src/main/java/...
    ├── detector/
    ├── service/
    └── integration/
```

**Core:** Defines interface  
**PII Module:** Implements functionality  
**Users:** Choose to include or not

---

## Summary

### Should PII Detection Be a Separate Module?

**✅ YES - Absolutely!**

**Reasons:**
1. ✅ **Not all apps need PII detection** (optional feature)
2. ✅ **Specialized concern** (compliance, security, privacy)
3. ✅ **Lighter core** (reduce mandatory dependencies)
4. ✅ **User choice** (include if regulated industry)
5. ✅ **Independent evolution** (update PII without core changes)
6. ✅ **Follows framework pattern** (same as other modules)

### Module Design:

**Core provides:** SPI interface (`PIIDetectionProvider`)  
**PII module provides:** Implementation (detectors, tokenization, registry)  
**Users provide:** Custom detectors (optional)

### Usage:

**With PII module:**
```java
Optional<PIIDetectionProvider> piiProvider;  // Present
// PII tokenization active
```

**Without PII module:**
```java
Optional<PIIDetectionProvider> piiProvider;  // Empty
// No PII detection (queries sent as-is)
```

**This follows our philosophy perfectly:**
- Separate modules for separate concerns
- Optional features as optional modules
- Users include only what they need
- Clean, modular architecture

---

**Recommendation:** ✅ **Make PII Detection a separate module**

**Next Step:** Create `ai-infrastructure-pii-detection` module with registry-based detectors.

---

**Document Version:** 1.0  
**Architecture:** Separate Optional Module  
**Status:** ✅ Ready for Implementation

