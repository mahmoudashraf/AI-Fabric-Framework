# Real Apps (Demo + Acceptance Apps)

`Real_Apps/` contains **standalone Spring Boot applications** that act as:
- Realistic demo apps for AI Fabric
- “Living” integration examples (they should keep compiling/booting as the framework evolves)
- Setup-scenario fixtures (each app demonstrates one specific configuration path)

## Principles

1. **One app = one setup scenario**
   - Don’t build a “kitchen sink” app. Each app should prove a single integration story clearly.
2. **Realistic domain**
   - The app should feel like a real product/service (entities, endpoints, data seed, basic UX/API).
3. **Minimum annotations**
   - Prefer configuration-driven integration.
   - The framework auto-configuration should drive bean creation; the app only opts-in with `@EnableAIInfrastructure`.

## Current Apps

- `Real_Apps/sub-management-hub-simple/`
  - Scenario: “minimal integration” (config-driven entity setup).
  - Uses `ai.config.default-file: ai-entity-config.yml` and avoids AI entity annotations.
- `Real_Apps/sub-management-hub/`
  - Scenario: “advanced/explicit” (shows optional entity annotations like `@AICapable`, `@AISearchable`, `@AIContext`).
  - Still uses `ai-entity-config.yml` so the framework has a single consistent source of truth.
- `Real_Apps/it-support-action-bot/`
  - Scenario: “provider-only action bot” (no vector DB / no indexing / no RAG).
  - Validates orchestrator + action handling with only an LLM provider configured.
- `Real_Apps/privacy-first-customer-facing-support/`
  - Scenario: “privacy-first support” (PII detection + redaction + optional encrypted/hash original storage).
  - No vector DB / indexing / providers required; driven entirely by `ai.pii-detection.*` configuration.
- `Real_Apps/smart-faq-assistant/`
  - Scenario: “offline semantic search” (H2 + ONNX embeddings + Lucene vector DB), optional contextual answer generation.
  - Uses DB text (FAQ articles), not file parsing/document ingestion.
- `Real_Apps/migration-enabled-product-catalog/`
  - Scenario: “migration-enabled backfill” (seed DB first, then bulk index via `DataMigrationService` + async indexing worker).
  - Default stack: H2 + ONNX + Lucene (no external services/keys).

## How To Create A New Real App

### 1) Pick the scenario (be explicit)

Create a short scenario statement and encode it in the app folder name:
- `local-onnx-lucene-h2` (no external services)
- `openai-qdrant-postgres` (cloud LLM + external vector DB)
- `pii-redaction-audit` (privacy + auditing)
- `migration-enabled` (schema/data migration path)

Keep one scenario per app.

### 2) Create the app skeleton

Recommended structure:

```
Real_Apps/<app-name>/
  pom.xml
  src/main/java/.../<App>.java
  src/main/resources/application.yml
  src/main/resources/ai-entity-config.yml
```

Keep `groupId`/`artifactId` unique per app to avoid confusion when building locally.

### 3) Add the minimal integration entry point

Your main class should be the standard Spring Boot entry + one annotation:

```java
@SpringBootApplication
@EnableAIInfrastructure
public class MyApp { }
```

No manual `@ComponentScan` for framework packages.

### 4) Add the minimum dependencies

At minimum:
- `com.ai.fabric:ai-fabric-starter` (core + indexing)
- `com.ai.fabric:ai-fabric-provider-starter` (provider-only / core-only scenarios)
- one **LLM provider** module (ex: `ai-infrastructure-provider-cohere`)
- one **vector database** module (ex: `ai-infrastructure-vector-lucene`)
- one **embedding** module (ex: `ai-infrastructure-onnx-starter`)

### 5) Configure the app (config-driven)

`src/main/resources/application.yml` should include:

```yml
ai:
  config:
    default-file: ai-entity-config.yml
  providers:
    llm-provider: cohere
    embedding-provider: onnx
  vector-db:
    type: lucene
```

Optional (scenario-based) toggles:
- `ai.indexing.enabled: true|false`
- `ai.pii-detection.enabled: true|false`
- `ai.pii-detection.mode: DETECT_ONLY|REDACT|PASS_THROUGH`
- `ai.migration.enabled: true` (only for migration scenarios)

### 6) Define AI entity behavior via `ai-entity-config.yml`

Minimal example (field names must match your entity fields):

```yml
ai-entities:
  product:
    entity-type: "product"
    auto-embedding: true
    indexable: true
    enable-search: true
    searchable-fields:
      - name: "name"
        weight: 2.0
      - name: "description"
        weight: 1.5
    metadata-fields:
      - name: "category"
        type: "string"
      - name: "price"
        type: "decimal"
```

Use entity annotations (`@AICapable`, `@AISearchable`, `@AIContext`) only when the scenario is explicitly “annotation-based” or when you need per-field overrides.

## How To Build + Run

1) Install the framework artifacts locally:

`cd ai-infrastructure-module && mvn -DskipTests install`

2) Build and run the app:

`cd Real_Apps/<app-name> && mvn -DskipTests package && java -jar target/*.jar`

## Suggested App Scenarios To Add Next

- **Local/offline**: ONNX + Lucene + H2, no external API keys required (best “first run” experience).
- **Cloud RAG**: OpenAI/Azure + external vector DB + Postgres, with environment-variable secrets.
- **Privacy first**: PII detection in `REDACT` mode + response sanitization + audit logging.
- **Indexing scaling**: async worker tuned (batch size, retry behavior) + sample load generator endpoint.
- **Migration**: enable `ai.migration.enabled=true` and show an upgrade path for stored searchable entities/vectors.
