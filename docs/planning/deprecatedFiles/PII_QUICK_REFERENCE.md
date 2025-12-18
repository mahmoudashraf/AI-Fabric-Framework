# PII Detection Directions - Quick Reference

## 🎯 At a Glance

```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH  # INPUT | OUTPUT | BOTH (default)
```

## 🔄 Three Modes

| Mode | What It Does | Best For |
|------|-------------|----------|
| **INPUT** 🔒 | Redact PII BEFORE sending to LLM | Privacy-first, prevent LLM exposure |
| **OUTPUT** 🛡️ | Detect PII in LLM responses | Safety net, catch accidental leaks |
| **BOTH** 🔐 | Both INPUT + OUTPUT | Production (comprehensive security) |

## 📊 Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ INPUT MODE: Prevent LLM Exposure                            │
├─────────────────────────────────────────────────────────────┤
│ User: "Card 4111-1111-1111-1111 was charged"               │
│  ↓                                                           │
│ DETECT & REDACT: "Card [REDACTED] was charged"             │
│  ↓                                                           │
│ SEND TO LLM: LLM only sees redacted version                │
│ ✅ LLM never sees sensitive data                            │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ OUTPUT MODE: Safety Net                                      │
├─────────────────────────────────────────────────────────────┤
│ User: "Card 4111-1111-1111-1111 was charged"               │
│  ↓                                                           │
│ SEND TO LLM: LLM sees original                             │
│  ↓                                                           │
│ LLM Response: "Regarding card 4111-1111-1111-1111..."     │
│  ↓                                                           │
│ DETECT & REDACT RESPONSE: "Regarding card [REDACTED]..."  │
│ ✅ Catches accidental PII leaks from LLM                    │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ BOTH MODE: Defense-in-Depth (RECOMMENDED) 🏆               │
├─────────────────────────────────────────────────────────────┤
│ User: "Card 4111-1111-1111-1111 was charged"               │
│  ↓                                                           │
│ [INPUT DETECTION] Redact before sending                     │
│  ↓                                                           │
│ SEND TO LLM: "Card [REDACTED] was charged"                │
│  ↓                                                           │
│ LLM Response: "..."                                         │
│  ↓                                                           │
│ [OUTPUT DETECTION] Scan response for PII                    │
│  ↓                                                           │
│ ✅ Maximum protection against all scenarios                 │
└─────────────────────────────────────────────────────────────┘
```

## 🔧 Configuration Examples

### INPUT Mode (Privacy-First)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: INPUT  # Only scan input
    mode: REDACT                # Redact before sending to LLM
```

### OUTPUT Mode (Safety Net)
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: OUTPUT  # Only scan output
    mode: DETECT_ONLY            # Just detect, don't redact input
```

### BOTH Mode (Production - Recommended) ✅
```yaml
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH    # Scan input AND output
    mode: REDACT                 # Redact everything
    audit-logging-enabled: true
    patterns:
      CREDIT_CARD:
        enabled: true
      SSN:
        enabled: true
      EMAIL:
        enabled: true
```

## 📝 Use Cases

### Use INPUT When...
- 🔒 You want maximum privacy (never expose PII to LLM)
- 🏥 HIPAA/PCI-DSS compliance is critical
- 💾 Your LLM is less trusted
- ⚡ You want best performance

### Use OUTPUT When...
- 🎓 Research/Analysis (you accept PII exposure)
- ⚡ High performance needed
- 🔬 Testing LLM behavior
- 📊 Data study environments

### Use BOTH When...
- 🏢 Production systems (recommended)
- 💰 Financial/Healthcare apps
- 🔐 Multiple-layer security required
- 🛡️ Defense-in-depth strategy

### Disable When...
- 🧪 Local testing/development
- 📚 No PII in data
- ⚙️ Performance benchmarking

## 🚀 Quick Start

1. **Enable with defaults (BOTH mode):**
   ```yaml
   ai:
     pii-detection:
       enabled: true
   ```

2. **Switch to INPUT mode:**
   ```yaml
   ai:
     pii-detection:
       enabled: true
       detection-direction: INPUT
   ```

3. **Switch to OUTPUT mode:**
   ```yaml
   ai:
     pii-detection:
       enabled: true
       detection-direction: OUTPUT
   ```

## 📊 Response Metadata

All modes include detection metadata:

```json
{
  "sanitization": {
    "direction": "BOTH",
    "detectedTypes": ["CREDIT_CARD"],
    "risk": "HIGH_RISK",
    "detectionMode": "DETECT_ONLY"
  }
}
```

## 🛠️ API Changes

### RAGOrchestrator Constructor
```java
// Old (before)
orchestrator = new RAGOrchestrator(
    intentQueryExtractor, actionHandlerRegistry, ragService,
    responseSanitizer, intentHistoryService, smartSuggestionsProperties,
    piiDetectionService
);

// New (after) - Added PIIDetectionProperties
orchestrator = new RAGOrchestrator(
    intentQueryExtractor, actionHandlerRegistry, ragService,
    responseSanitizer, intentHistoryService, smartSuggestionsProperties,
    piiDetectionService, piiDetectionProperties  // ← NEW
);
```

### PIIDetectionProperties Enum
```java
public enum PIIDetectionDirection {
    INPUT,   // Detect only in user queries
    OUTPUT,  // Detect only in LLM responses
    BOTH     // Detect in both (default)
}
```

## 📈 Performance Impact

| Mode | Latency Impact | Memory | CPU | Notes |
|------|---|---|---|---|
| INPUT | ~100-200ms | Low | Low | Runs before LLM call |
| OUTPUT | ~100-200ms | Low | Low | Runs after LLM response |
| BOTH | ~200-400ms | Low | Medium | Runs twice |
| Disabled | 0ms | 0 | 0 | No overhead |

## ✅ Testing

Test configuration uses **BOTH** mode:

```yaml
# application-real-api-test.yml
ai:
  pii-detection:
    enabled: true
    detection-direction: BOTH
    mode: DETECT_ONLY
    patterns:
      CREDIT_CARD:
        regex: "(?<!\\d)(?:\\d[ -]?){13,16}(?!\\d)"
        enabled: true
```

## 🐛 Debugging

Enable debug logging:
```yaml
logging:
  level:
    com.ai.infrastructure.privacy.pii: DEBUG
    com.ai.infrastructure.intent.orchestration: DEBUG
```

Watch for logs:
```
PII detected in user query - types: [CREDIT_CARD] (mode: INPUT_REDACTION)
PII INPUT detection is disabled (configuration: OUTPUT)
PII OUTPUT detection is disabled (configuration: INPUT)
```

## 🎓 Summary

```
Need to prevent LLM exposure?     → Use INPUT
Need to catch accidental leaks?   → Use OUTPUT  
Need maximum security?            → Use BOTH ✅
Just testing locally?             → Disable
```

**Recommendation for Production:** Always use **BOTH** mode 🔐

