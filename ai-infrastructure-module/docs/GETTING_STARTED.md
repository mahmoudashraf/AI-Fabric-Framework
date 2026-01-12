# AI Fabric Framework — Getting Started (Minimum Config)

This guide shows the minimum dependencies + configuration needed to boot the framework in a Spring Boot app.

## 1) Dependencies (Maven)

Add `ai-fabric-core` plus:
- one LLM provider module
- one embedding provider module
- one vector database module

Example: OpenAI (LLM) + ONNX (embeddings) + Lucene (vector DB)

```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-fabric-core</artifactId>
  <version>${ai.fabric.version}</version>
</dependency>

<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-fabric-provider-openai</artifactId>
  <version>${ai.fabric.version}</version>
</dependency>

<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-infrastructure-onnx-starter</artifactId>
  <version>${ai.fabric.version}</version>
</dependency>

<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-infrastructure-vector-lucene</artifactId>
  <version>${ai.fabric.version}</version>
</dependency>
```

Optional modules:

```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-infrastructure-rag</artifactId>
  <version>${ai.fabric.version}</version>
</dependency>

<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-infrastructure-web</artifactId>
  <version>${ai.fabric.version}</version>
</dependency>

<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-infrastructure-relationship-query</artifactId>
  <version>${ai.fabric.version}</version>
</dependency>
```

## 2) Enablement (Optional)

Auto-configuration is enabled by default when the JARs are on the classpath. For an explicit “happy path” entry point, you can add:

```java
@SpringBootApplication
@EnableAIInfrastructure
public class MyApplication {}
```

## 3) Minimum `application.yml`

This configuration is the minimum required to satisfy provider validation for the example dependency set above:

```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx

    openai:
      api-key: ${OPENAI_API_KEY}
      base-url: https://api.openai.com/v1
      model: gpt-4o-mini

  vector-db:
    type: lucene
```

Notes:
- The ONNX starter supplies default values for `ai.providers.onnx.model-path` and `ai.providers.onnx.tokenizer-path` via its bundled `application.properties` (override if needed).
- Global enable/disable: `ai.enabled=false` disables the core auto-configuration.
- Module toggles (enable/disable aliases):
  - RAG: `ai.rag.enabled` (alias for `ai.infrastructure.rag.enabled`)
  - Relationship Query: `ai.relationship.enabled` (alias for `ai.infrastructure.relationship.enabled`)
- Core HTTP defaults are configurable via `ai.http.connect-timeout` and `ai.http.read-timeout`.
