# Configuration Guide

This reference lists the configuration surface for the relationship query module along with sensible defaults and rationale.

## 1. Core Properties (`ai.infrastructure.relationship.*`)

| Property | Default | Description |
| --- | --- | --- |
| `enabled` | `true` | Master switch for the module. |
| `max-traversal-depth` | `3` | Maximum relationship hops the planner will follow. Increase if you need deeper graphs. |
| `enable-vector-search` | `true` | Allows the planner to include semantic/vector stages. |
| `fallback-to-metadata` | `true` | Enables metadata traversal when JPA fails. |
| `fallback-to-vector-search` | `true` | Enables semantic fallback when JPA/metadata produce low recall. |
| `enable-query-validation` | `true` | Activates injection/guard checks (Phase 4.5). |
| `enable-query-caching` | `true` | Turns on plan/result caching. |
| `query-cache-ttl-seconds` | `3600` | Global TTL when fine-grained region values are not overriden. |
| `default-return-mode` | `IDS` | Options: `IDS`, `FULL`. |
| `default-query-mode` | `STANDALONE` | Controls orchestration expectations. |

### Cache Regions (`ai.infrastructure.relationship.cache.*`)
- `plan.ttl-seconds` (default `3600`)
- `plan.max-entries` (default `10_000`)
- `embedding.ttl-seconds` (`86_400`)
- `result.ttl-seconds` (`1_800`)

### Planner (`ai.infrastructure.relationship.planner.*`)
- `log-plans` (`false`): emit plan JSON to logs.
- `min-confidence-to-execute` (`0.55`): LLM confidence gate.

## 2. Provider Properties (`ai.providers.*`)
- `llm-provider` — e.g., `openai`
- `embedding-provider` — `onnx` for deterministic local tests
- `enable-fallback` — set to `false` in repeatable test suites
- `openai.api-key` — set via env var or secret manager

## 3. Vector Database (`ai.vector-db.*`)
| Property | Example | Notes |
| --- | --- | --- |
| `type` | `lucene` | Module ships with Lucene vector backend. |
| `lucene.index-path` | `/tmp/relationship-query-lucene` | Ensure writable location. |
| `lucene.vector-dimension` | `384` | Must match embedding model dimension. |
| `lucene.similarity-threshold` | `0.6` | Tune for precision/recall. |

## 4. Test Profiles
`IntegrationTestSupport` programmatically sets:
- H2 (PostgreSQL mode) datasource
- Allow bean definition overriding
- `ai.infrastructure.relationship.schema.auto-discover=false` (tests register schemas manually)
- Temp Lucene index path

Use these values for local integration tests to align with CI.

## 5. Environment Variables
| Env Var | Purpose |
| --- | --- |
| `OPENAI_API_KEY` | Used by both LLM provider config and tests. |
| `JAVA_TOOL_OPTIONS` | Append `-Dorg.apache.lucene.store.MMapDirectory.enableMemorySegments=false` if you hit off-heap limits. |

## 6. Validation & Security
- The `RelationshipQueryValidator` now enforces a regex-based deny list. The property `enable-query-validation` must remain `true` in production to leverage Phase 4.5 guards.
- Rate limiting resides in the broader AI security stack; integrate with `AISecurityService` if the host application requires global throttling.

## 7. Overriding Beans
The auto-configuration uses `@ConditionalOnMissingBean`. You can provide custom beans by defining your own `RelationshipQueryPlanner`, `VectorDatabaseService`, or `AIEmbeddingService` in the application context—set `spring.main.allow-bean-definition-overriding=true` if needed (tests already do this).
