# Vector Database Modular Architecture Plan

## Overview

Vector databases follow the same modular pattern as AI providers - each vector database implementation is in its own module with auto-discovery.

## Module Structure

```
ai-infrastructure-module/
├── ai-infrastructure-vector-lucene/      # Lucene (default, dev/test)
├── ai-infrastructure-vector-pinecone/    # Pinecone (production)
├── ai-infrastructure-vector-weaviate/   # Weaviate (self-hosted)
├── ai-infrastructure-vector-qdrant/      # Qdrant (self-hosted)
├── ai-infrastructure-vector-milvus/      # Milvus (self-hosted)
└── ai-infrastructure-vector-memory/     # InMemory (testing)
```

## Current State

**Current**: Vector database implementations now reside in dedicated modules:
- `ai-infrastructure-vector-lucene` (Lucene)
- `ai-infrastructure-vector-pinecone` (Pinecone)
- `ai-infrastructure-vector-memory` (In-Memory test support)
- `ai-infrastructure-vector-weaviate` (Weaviate HTTP API)
- `ai-infrastructure-vector-qdrant` (Qdrant REST API)
- `ai-infrastructure-vector-milvus` (Milvus gRPC SDK)

**Next**: Stand up a Milvus cluster (Testcontainers or shared infra) and record smoke-test scripts so the module can be exercised in CI and staging.

## Module Implementations

### Weaviate (`ai-infrastructure-vector-weaviate`)
- Uses Spring's `RestTemplate` to call Weaviate's REST endpoints for CRUD and search.
- Automatically provisions schema lookups and logs helpful diagnostics when classes are missing.
- Configuration block (`ai.weaviate`): `scheme`, `host`, `port`, `apiKey`, and request timeout.

### Qdrant (`ai-infrastructure-vector-qdrant`)
- Talks to Qdrant's REST API for upsert, search, scroll, and delete operations.
- Supports raw JSON filter expressions via `AISearchRequest.filters` and auto-builds simple equality filters from `metadata`.
- Configuration block (`ai.qdrant`): `host`, `port`, optional `apiKey`, timeout, and `preferGrpc` flag (reserved for future gRPC integration).

### Milvus (`ai-infrastructure-vector-milvus`)
- Uses the official Milvus Java SDK over gRPC to create collections, upsert vectors, run similarity search, and manage metadata.
- Lazily provisions collections per entity type, creating IVF_FLAT indexes and loading them automatically.
- Configuration block (`ai.milvus`): `host`, `port`, `username`, `password`, `databaseName`, `secure`, `timeout`.

Each module contributes an auto-configuration entry under `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`, exposing both the raw delegate (`VectorDatabaseService` implementation) and the shared `SearchableEntityVectorDatabaseService` wrapper when selected with `ai.vector-db.type`.

## Configuration

**application.yml** (excerpt):
```yaml
ai:
  vector-db:
    type: qdrant   # lucene | pinecone | weaviate | qdrant | milvus | memory

    weaviate:
      host: localhost
      port: 8080
      scheme: http
      api-key: ${WEAVIATE_API_KEY:}
      timeout: 30

    qdrant:
      host: localhost
      port: 6333
      api-key: ${QDRANT_API_KEY:}
      timeout: 30

    milvus:
      host: localhost
      port: 19530
      username: root
      password: ${MILVUS_PASSWORD:}
      database-name: default
      secure: false
      timeout: 30
```

## Migration Steps

1. [x] **Move existing implementations** from core to modules
2. [x] **Create auto-configuration** for each vector database
3. [x] **Update parent POM** to include new modules
4. [x] **Update tests** to use new modules
5. [x] **Implement Weaviate integration**
6. [x] **Implement Qdrant integration**
7. [x] **Finalize Milvus integration (SDK wiring + tests)**

## Benefits

- ✅ Only include vector databases you need
- ✅ Independent versioning
- ✅ Smaller artifacts
- ✅ Easy to add new vector databases


## Roadmap: Additional Provider Modules

The next vector integrations share the same packaging conventions used for Lucene, Pinecone, and the in-memory adapter. Each module will expose a Spring Boot auto-configuration and delegate that implements `VectorDatabaseService`.

| Module | Primary Dependency | Notes | Target Stories |
| --- | --- | --- | --- |
| `ai-infrastructure-vector-milvus` | `io.milvus:milvus-sdk-java` | Add automated smoke tests and CI profile once a Milvus container/cluster is available. | Testcontainers profile, sample dataset + documentation, monitoring hooks. |

### Implementation Checklist (per module)

1. Create Maven module (`ai-infrastructure-vector-<provider>`), add to parent `<modules>` list and dependency management.
2. Implement `<Provider>VectorDatabaseService` with full CRUD/search parity.
3. Add `<Provider>VectorAutoConfiguration` that exposes delegate plus `SearchableEntityVectorDatabaseService` wrapper.
4. Provide configuration section in `CONFIGURATION_REFERENCE.md` and sample YAML in `integration-tests` resources.
5. Extend integration test matrix with provider-specific profile and smoke test (can be profile-disabled until CI infra is available).
6. Document environment prerequisites (container image, env vars) in this roadmap and `INTEGRATION_TEST_CHANGES.md`.

### Open Questions
- **Testing Strategy:** Decide whether to spin up containers in CI (Testcontainers) or rely on contract tests against vendor mocks.
- **Schema Management:** Determine common abstraction for schema bootstrapping (e.g., vector dimension, metadata fields) to avoid duplication across modules.
- **Resilience:** Evaluate retry/backoff defaults per provider; some SDKs already expose resilient clients.

Capturing this roadmap here keeps the remaining work visible until the modules are implemented. Each bullet can be promoted to a tracked story as prioritization solidifies.
