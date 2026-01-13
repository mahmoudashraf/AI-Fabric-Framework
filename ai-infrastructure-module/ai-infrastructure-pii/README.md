# AI Infrastructure PII Module

This module provides optional PII detection/redaction for AI Fabric via Spring Boot auto-configuration.

## What it provides

- `PIIDetectionService` implementation (`DefaultPIIDetectionService`)
- Orchestration pipeline input step (`PIIDetectionStep`) when `ai.pii-detection.enabled=true`

## Enable

Add the module dependency (or use `ai-fabric-starter`, which includes it):

```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-infrastructure-pii</artifactId>
</dependency>
```

Then enable it:

```yaml
ai:
  pii-detection:
    enabled: true
    mode: REDACT # or DETECT_ONLY
```

