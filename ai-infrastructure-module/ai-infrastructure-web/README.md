# AI Fabric Web Module

REST API controllers for the AI Fabric Framework.

## Installation

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-web</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Endpoints

- **Advanced RAG**: 3 endpoints (`/api/ai/advanced-rag/*`) - **Enterprise**
- **Audit**: 11 endpoints (`/api/ai/audit/*`) - **Community**
- **Compliance**: 2 endpoints (`/api/ai/compliance/*`) - **Enterprise**
- **Monitoring**: 15 endpoints (`/api/ai/monitoring/*`) - **Community**
- **Profile**: 22 endpoints (`/api/ai/profiles/*`) - **Community**
- **Security**: 6 endpoints (`/api/ai/security/*`) - **Enterprise**

**Total: 59 REST endpoints**

## Configuration

```yaml
ai:
  web:
    enabled: true
    controllers:
      audit: false  # Disable specific controllers
```

See the main [LICENSE](../../LICENSE) for feature availability between Community and Enterprise editions.
