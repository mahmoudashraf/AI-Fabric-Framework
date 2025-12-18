# PII Detection Direction - YAML Configuration Flow

## 🔄 Configuration Flow Architecture

```
application.yml (or application-real-api-test.yml)
        ↓
Spring Boot @ConfigurationProperties
        ↓
PIIDetectionProperties (Java Bean)
        ↓
@Autowired in RAGOrchestrator
        ↓
Runtime PII Detection Logic
```

## 📝 YAML Configuration

### application-real-api-test.yml
```yaml
ai:
  pii-detection:
    enabled: true                    # Master switch
    mode: DETECT_ONLY               # DETECT_ONLY | REDACT
    detection-direction: BOTH        # INPUT | OUTPUT | BOTH ← NEW!
    store-encrypted-original: false
    audit-logging-enabled: true
    patterns:
      CREDIT_CARD:
        field-name: "credit_card"
        regex: "(?<!\\d)(?:\\d[ -]?){13,16}(?!\\d)"
        replacement: "****-****-****-****"
        enabled: true
      SSN:
        field-name: "ssn"
        regex: "\\b\\d{3}-?\\d{2}-?\\d{4}\\b"
        replacement: "***-**-****"
        enabled: true
      EMAIL:
        field-name: "email"
        regex: "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}"
        replacement: "***@***.***"
        enabled: true
```

## 🔧 Java Configuration Class

### PIIDetectionProperties.java
```java
@Data
@Validated
@NoArgsConstructor
@ConfigurationProperties(prefix = "ai.pii-detection")
public class PIIDetectionProperties {

    private boolean enabled = false;
    private PIIMode mode = PIIMode.PASS_THROUGH;
    
    // NEW: Detection direction from YAML config
    private PIIDetectionDirection detectionDirection = PIIDetectionDirection.BOTH;
    
    private boolean storeEncryptedOriginal = false;
    private boolean auditLoggingEnabled = true;
    private Map<String, PatternConfig> patterns = defaultPatterns();
    
    // Enum for directional control
    public enum PIIDetectionDirection {
        INPUT,   // Detect PII in user queries only
        OUTPUT,  // Detect PII in LLM responses only
        BOTH     // Detect in both directions
    }
}
```

## 💉 Dependency Injection

### RAGOrchestrator.java
```java
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    
    private final PIIDetectionService piiDetectionService;
    
    // ← Spring automatically injects from YAML config
    private final PIIDetectionProperties piiDetectionProperties;
    
    public OrchestrationResult orchestrate(String query, String userId) {
        
        // Read from injected properties (coming from YAML)
        boolean isEnabled = piiDetectionProperties.isEnabled();
        PIIDetectionDirection direction = piiDetectionProperties.getDetectionDirection();
        PIIMode mode = piiDetectionProperties.getMode();
        
        // Use the configuration
        boolean detectInput = isEnabled && 
            (direction == PIIDetectionDirection.INPUT || 
             direction == PIIDetectionDirection.BOTH);
        
        if (detectInput) {
            // Detect & redact PII from user input
            PIIDetectionResult analysis = piiDetectionService.analyze(query);
            String processedQuery = analysis.getProcessedQuery();
            // ... send to LLM
        }
    }
}
```

## 🔗 Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. YAML Configuration                                        │
├─────────────────────────────────────────────────────────────┤
│ application-real-api-test.yml:                              │
│   ai:                                                        │
│     pii-detection:                                           │
│       enabled: true                                          │
│       detection-direction: BOTH                              │
│       mode: DETECT_ONLY                                      │
│       patterns: {...}                                        │
└──────────────┬──────────────────────────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. Spring Boot Configuration Properties                      │
├─────────────────────────────────────────────────────────────┤
│ @ConfigurationProperties(prefix = "ai.pii-detection")       │
│ public class PIIDetectionProperties {                        │
│   private boolean enabled;                                   │
│   private PIIDetectionDirection detectionDirection;          │
│   private PIIMode mode;                                      │
│   private Map<String, PatternConfig> patterns;               │
│ }                                                            │
└──────────────┬──────────────────────────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. Bean Instantiation & Validation                           │
├─────────────────────────────────────────────────────────────┤
│ Spring creates PIIDetectionProperties bean                   │
│ Validates all @Validated annotations                         │
│ Defaults applied to missing properties                       │
└──────────────┬──────────────────────────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. Dependency Injection                                      │
├─────────────────────────────────────────────────────────────┤
│ @Service                                                     │
│ @RequiredArgsConstructor                                     │
│ public class RAGOrchestrator {                               │
│   private final PIIDetectionProperties piiDetectionProperties;│
│                                                              │
│   // Spring auto-wires the configured bean                  │
│ }                                                            │
└──────────────┬──────────────────────────────────────────────┘
               │
               ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. Runtime Logic                                             │
├─────────────────────────────────────────────────────────────┤
│ orchestrate(String query, String userId) {                   │
│   boolean detectInput = piiDetectionProperties.isEnabled() &&│
│     (piiDetectionProperties.getDetectionDirection() ==      │
│      PIIDetectionDirection.INPUT ||                         │
│      PIIDetectionDirection.BOTH);                           │
│                                                              │
│   if (detectInput) {                                         │
│     // Execute INPUT-direction PII detection                │
│   }                                                          │
│ }                                                            │
└─────────────────────────────────────────────────────────────┘
```

## 📋 Application Profiles

### Development Profile (application.yml)
```yaml
ai:
  pii-detection:
    enabled: false  # Disabled for development
```

### Real API Test Profile (application-real-api-test.yml)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH  # Full protection for tests
    mode: DETECT_ONLY
```

### Production Profile (application-prod.yml)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH  # Recommended for production
    mode: REDACT               # Actively redact sensitive data
    audit-logging-enabled: true
```

### API-Only Profile (application-api.yml)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: INPUT  # Only protect input to LLM
    mode: REDACT
```

## 🔐 How the Configuration Works

### Step 1: Load from YAML
Spring Boot reads `ai.pii-detection.*` properties from YAML files

### Step 2: Bind to Java Bean
```java
@ConfigurationProperties(prefix = "ai.pii-detection")
public class PIIDetectionProperties {
    // YAML key: ai.pii-detection.enabled
    // Java field: private boolean enabled
    
    // YAML key: ai.pii-detection.detection-direction
    // Java field: private PIIDetectionDirection detectionDirection
}
```

### Step 3: Auto-Wire to Services
```java
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {
    // Spring automatically injects PIIDetectionProperties
    private final PIIDetectionProperties piiDetectionProperties;
}
```

### Step 4: Read Configuration at Runtime
```java
public OrchestrationResult orchestrate(String query, String userId) {
    // These values come from YAML config
    boolean isEnabled = piiDetectionProperties.isEnabled();
    PIIDetectionDirection direction = piiDetectionProperties.getDetectionDirection();
    String mode = piiDetectionProperties.getMode().toString();
}
```

## 🎯 Profile-Specific Configuration

### Run with Profile
```bash
# Use real-api-test profile
mvn test -Dspring.profiles.active=real-api-test

# Use development profile (default)
mvn spring-boot:run

# Use production profile
java -Dspring.profiles.active=prod -jar app.jar
```

## ✨ Configuration Examples

### Example 1: INPUT Only (Privacy-First)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: INPUT
    mode: REDACT
```
**Result:** Redacts PII BEFORE sending to LLM, no response scanning

### Example 2: OUTPUT Only (Safety Net)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: OUTPUT
    mode: DETECT_ONLY
```
**Result:** Detects PII in LLM responses only

### Example 3: BOTH (Recommended)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH
    mode: REDACT
    audit-logging-enabled: true
```
**Result:** Comprehensive protection in both directions

### Example 4: Disabled
```yaml
ai:
  pii-detection:
    enabled: false
```
**Result:** No PII detection

## 📊 Config Override Hierarchy (Spring Boot)

```
1. Environment Variables      (highest priority)
   AI_PII_DETECTION_ENABLED=true
   AI_PII_DETECTION_DETECTION_DIRECTION=INPUT

2. Command-Line Arguments
   --ai.pii-detection.enabled=true
   --ai.pii-detection.detection-direction=BOTH

3. application-{profile}.yml
   (e.g., application-real-api-test.yml)

4. application.yml            (lowest priority)
```

## 🧪 Test Configuration

The test automatically uses `application-real-api-test.yml`:

```yaml
# application-real-api-test.yml
test:
  enable-mock-provider: false

ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH  # ← Comes from YAML
    mode: DETECT_ONLY
```

**Java Test:**
```java
@SpringBootTest
@ActiveProfiles("real-api-test")  // Loads application-real-api-test.yml
public class RealAPIIntegrationTest {
    
    @Autowired
    private RAGOrchestrator orchestrator;  // Gets config from YAML
    
    @Autowired
    private PIIDetectionProperties piiProps;  // Can also inject directly
    
    @Test
    void testRealRAGSixLayerPipeline() {
        // piiProps.isEnabled() = true (from YAML)
        // piiProps.getDetectionDirection() = BOTH (from YAML)
        // piiProps.getMode() = DETECT_ONLY (from YAML)
    }
}
```

## 🔍 Debugging Configuration

### Check What Configuration is Loaded
```java
@SpringBootTest
class ConfigDebugTest {
    @Autowired
    private PIIDetectionProperties piiProps;
    
    @Test
    void debugConfiguration() {
        System.out.println("Enabled: " + piiProps.isEnabled());
        System.out.println("Direction: " + piiProps.getDetectionDirection());
        System.out.println("Mode: " + piiProps.getMode());
        System.out.println("Patterns: " + piiProps.getPatterns().keySet());
    }
}
```

### Enable Debug Logging
```yaml
logging:
  level:
    org.springframework.boot.context.properties: DEBUG
    com.ai.infrastructure.config: DEBUG
```

## ✅ Summary

The PII detection direction is now **fully configurable via YAML**:

1. ✅ Define in `application.yml` or profile-specific files
2. ✅ Spring Boot automatically binds to `PIIDetectionProperties`
3. ✅ Services inject the properties via `@RequiredArgsConstructor`
4. ✅ Runtime logic reads configuration values
5. ✅ No hardcoding needed!

**Best Practice:** Use YAML configuration for all environment-specific settings.

