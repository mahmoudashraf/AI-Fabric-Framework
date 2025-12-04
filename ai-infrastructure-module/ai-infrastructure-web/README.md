# AI Infrastructure Web Module

REST API controllers for AI Infrastructure.

## Installation

```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Endpoints

- **Advanced RAG**: 3 endpoints (`/api/ai/advanced-rag/*`)
- **Audit**: 11 endpoints (`/api/ai/audit/*`)
- **Compliance**: 2 endpoints (`/api/ai/compliance/*`)
- **Monitoring**: 15 endpoints (`/api/ai/monitoring/*`)
- **Profile**: 22 endpoints (`/api/ai/profiles/*`)
- **Security**: 6 endpoints (`/api/ai/security/*`)

**Total: 59 REST endpoints**

## Configuration

```yaml
ai:
  web:
    enabled: true
    controllers:
      audit: false  # Disable specific controllers
```

See full documentation for details.
