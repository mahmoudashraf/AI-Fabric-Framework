# AI Behavior - Comprehensive Solution

**Version:** 1.0.0  
**Date:** 2025-11-14  
**Status:** Design Specification

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Core Principles](#core-principles)
4. [Implementation Review](#implementation-review)
5. [Decoupling Strategy](#decoupling-strategy)
6. [Module Structure](#module-structure)
7. [Core Interfaces](#core-interfaces)
8. [Data Models](#data-models)
9. [Implementation Components](#implementation-components)
10. [Integration Patterns](#integration-patterns)
11. [Embedding Strategy](#embedding-strategy)
12. [Configuration](#configuration)
13. [Usage Examples](#usage-examples)
14. [Performance Considerations](#performance-considerations)
15. [Monitoring & Observability](#monitoring--observability)
16. [Implementation Roadmap](#implementation-roadmap)

---

## Executive Summary

### Vision

Build a modern, scalable behavior tracking system that:
- **Separates concerns:** Behavior tracking as dedicated module, independent from ai-core
- **Performs efficiently:** Fast writes, pre-computed insights, proper indexing
- **Integrates flexibly:** Accept events from any source (web, mobile, external systems)
- **Uses AI wisely:** Embeddings only where they provide value (text content)
- **Scales seamlessly:** Async processing, pluggable storage, handles high volume

### Solution Overview

New `ai-behavior` module that:
- ✅ **Clean architecture:** Behavior tracking in dedicated module
- ✅ **Flexible storage:** Pluggable storage backends (DB, Kafka, Redis, S3)
- ✅ **Multi-source ingestion:** Accept events from web, mobile, external systems
- ✅ **Selective AI:** Embeddings only for text content (feedback, reviews, search queries)
- ✅ **Async processing:** Non-blocking analysis and pattern detection
- ✅ **Extensible:** Interface-driven design for easy customization

### Key Design Principles

| Principle | Implementation |
|-----------|----------------|
| **Simplicity** | 15 event types (not 115), 8 core fields (not 20+) |
| **Performance** | Async processing, selective AI, proper indexes |
| **Flexibility** | Pluggable storage, multiple data sources |
| **Cost-effective** | Embeddings only for text (95% cost reduction) |
| **Scalability** | Event-driven, partitioned storage, batch processing |

---

## Architecture Overview

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                       APPLICATION LAYER                          │
│  (E-commerce backend, Mobile app, External systems)             │
│                                                                  │
│  - Track events: POST /api/ai-behavior/ingest                  │
│  - Query insights: GET /api/ai-behavior/users/{id}/insights    │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ REST API / SDK
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                    AI-BEHAVIOR MODULE                            │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ INGESTION LAYER                                            │ │
│  │  - BehaviorIngestionService                               │ │
│  │  - Validation & Enrichment                                │ │
│  │  - Event Publishing                                       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ STORAGE LAYER (PLUGGABLE)                                 │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │ │
│  │  │   Database   │  │    Kafka     │  │    Redis     │   │ │
│  │  │    Sink      │  │    Sink      │  │    Sink      │   │ │
│  │  └──────────────┘  └──────────────┘  └──────────────┘   │ │
│  │          └─────────────┬──────────────┘                   │ │
│  │                   BehaviorSignalSink (Interface)            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ PROCESSING LAYER (ASYNC)                                  │ │
│  │  - Real-time Aggregation Worker                           │ │
│  │  - Pattern Detection Worker                               │ │
│  │  - Anomaly Detection Worker                               │ │
│  │  - Embedding Generation Worker (selective)                │ │
│  │  - User Segmentation Worker                               │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ ANALYSIS RESULTS                                          │ │
│  │  - BehaviorInsights (pre-computed)                        │ │
│  │  - BehaviorMetrics (aggregated)                           │ │
│  │  - BehaviorEmbeddings (selective, text only)              │ │
│  │  - BehaviorAlerts (anomalies)                             │ │
│  └────────────────────────────────────────────────────────────┘ │
│                            │                                     │
│                            ↓                                     │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ QUERY LAYER                                               │ │
│  │  - BehaviorDataProvider (Interface)                       │ │
│  │  - BehaviorInsightsService                                │ │
│  │  - BehaviorQueryService                                   │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
                            │
                            │ Uses for AI capabilities
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                      AI-CORE MODULE                              │
│  (Generic AI infrastructure - domain agnostic)                  │
│                                                                  │
│  - Embeddings Generation (OpenAI, local models)                 │
│  - Vector Search                                                │
│  - Pattern Detection (generic time-series)                      │
│  - Semantic Analysis                                            │
│  - RAG (Retrieval Augmented Generation)                         │
│                                                                  │
│  Internal: AI Operations Tracking (NOT exposed to behavior)     │
└─────────────────────────────────────────────────────────────────┘
```

### Module Dependencies

```
application
    ↓
ai-behavior (NEW)
    ↓
ai-core (existing - generic primitives only)
    ↓
Spring Boot, JPA, etc.
```

**Key principle:** ai-core knows NOTHING about behaviors. ai-behavior USES ai-core.

---

## Core Principles

### 1. Separation of Concerns
- **ai-core:** Generic AI primitives (embeddings, search, RAG)
- **ai-behavior:** Domain-specific behavior tracking
- **application:** Business logic and UI

### 2. Flexibility First
- Pluggable storage backends
- Multiple ingestion sources
- Extensible via interfaces
- Default implementations for quick start

### 3. Performance by Design
- Async processing (non-blocking)
- Selective AI (only where valuable)
- Proper indexing
- Efficient batch operations

### 4. AI Where It Matters
- NO embeddings for simple events (clicks, views)
- YES embeddings for text content (feedback, reviews)
- Pre-computed insights (fast reads)
- Background analysis (scheduled)

### 5. Integration-Friendly
- REST API for remote systems
- SDK for local integration
- Adapter pattern for legacy systems
- Batch import for external analytics

---

## Implementation Review

### Code Audit Summary (2025-11-18)

- This module is brand new (no downstream adopters yet), so we can perform a breaking, single-shot refactor.
- The current code separates ingestion, storage, processing, and API layers, but core models and analyzers embed e-commerce assumptions (event enums, purchase funnels, cart heuristics).
- AI-driven components depend on hard-coded metadata keys (`amount`, `category`, `price`, `durationSeconds`) that are neither validated nor documented outside the source files.
- The `com.ai.infrastructure.*` package bundles a legacy-style REST surface we do not intend to ship inside infrastructure modules.

### Domain Coupling Hotspots

1. **Rigid event taxonomy** – `com.ai.behavior.model.EventType` enumerates funnel actions like `ADD_TO_CART`, `PURCHASE`, and `WISHLIST`, and is referenced by ingestion (`BehaviorSignalValidator`), adapters (`LegacySystemAdapter`), and analytics services. Adding a new domain requires editing this enum and all downstream `switch` statements such as those in `BehaviorService`.
2. **Commerce-specific metrics schema** – `BehaviorMetrics` persists dedicated columns (`add_to_cart_count`, `purchase_count`, `total_revenue`, etc.), while `MetricProjectionWorker` increments those counters and parses `metadata.amount`. Other domains that care about different KPIs must fork these entities and migrations.
3. **Insight heuristics baked in** – `PatternAnalyzer` and `SegmentationAnalyzer` publish labels like `cart_abandoner`, `frequent_buyer`, `VIP`, and compute scores (`conversion_probability`, `lifetime_value_estimate`) based on shopping behavior. These values are saved in `BehaviorInsights`, so every consumer inherits commerce terminology even if irrelevant.
4. **Legacy REST surface bundled in** – The `com.ai.infrastructure.{controller,dto,service,entity}` stack mirrors the e-commerce backend API. Keeping those classes within the module forces every adopter to depend on application-specific DTOs.
5. **Implicit metadata contracts** – Workers and analyzers expect keys such as `amount`, `category`, `price`, and `durationSeconds` without schema enforcement, making it impossible to validate payloads generically.

### Impact

- Deploying the module into a non-commerce product would require code changes plus database migrations, negating the purpose of a reusable AI infrastructure layer.
- Even within commerce, evolving behavior definitions (e.g., adding loyalty events) touches entity fields, enum constants, workers, and analyzers simultaneously, increasing defect risk.

---

## Decoupling Strategy

### Guiding Goals

- Deliver a single comprehensive refactor (no phased rollout) because the module has no external adopters.
- Align authoring and implementation with `/docs/Guidelines` (clear extension points, neutral primitives, explicit configuration).
- Remove backend-specific code so `ai-infrastructure-behavior` ships as a standalone AI infrastructure artifact.
- Keep ai-core interactions generic (signals, embeddings, anomaly inputs) rather than commerce DTOs.

### One-Shot Refactor Blueprint

| Layer | Current State | Refactored State |
|-------|---------------|------------------|
| **Data Model** | `BehaviorSignal` + `EventType` enum hard-coded to commerce funnel. | Rename entity to `BehaviorSignal` with `schemaId`, `signalKey`, `attributes` (JSON). Event semantics come from configuration-driven `BehaviorSignalDefinition`. |
| **Schema Management** | No formal schema; metadata is ad-hoc maps. | Introduce `BehaviorSchemaRegistry` (YAML + Java builder) that defines required attributes, types, embedding policy, retention hints, and validation constraints. |
| **Ingestion** | `BehaviorIngestionService` + `BehaviorSignalValidator` reference enums and metadata size only. | New `BehaviorSignalIngestionService` uses schema registry, enforces contracts, and writes via `BehaviorSignalSink` (renamed interface). Publishes neutral `BehaviorSignalIngested` events. |
| **Storage** | Separate tables for events, metrics, embeddings; metrics table contains commerce-specific columns. | Consolidate to flexible tables: `behavior_signals` (tenant/user/session/signal), `behavior_signal_metrics` (key/value/attributes), `behavior_insights` (neutral payload). Embeddings/alerts remain optional tables referencing `schemaId`. |
| **Metrics** | `MetricProjectionWorker` increments counters tied to commerce actions. | Replace with `MetricProjectionWorker` that runs `BehaviorMetricProjector` SPI instances loaded from Spring context. Projections persist as key/value metrics with optional attributes. |
| **Analysis** | `PatternAnalyzer` & `SegmentationAnalyzer` encode commerce personas. | Introduce `BehaviorInsightStrategy` and `SegmentationStrategy` SPIs. Default strategies provide neutral engagement tiers; commerce-specific strategy lives in optional `ai-infrastructure-behavior-commerce` starter. |
| **API/DTOs** | Mix of generic (`com.ai.behavior.api`) and backend-specific (`com.ai.infrastructure.*`). | Remove `com.ai.infrastructure.*`. Controllers and DTOs operate on `schemaId`, `attributes`, and typed metadata descriptions. Add `/schemas` discovery endpoint. |
| **Config** | `BehaviorModuleProperties` assumes enum-based events. | Expand properties to declare schemas, signal sources, sink configs, projector list, and insight strategy order. Provide starter YAML aligned with `/docs/Guidelines/PROJECT_GUIDELINES.yaml`. |

### Detailed Refactor Steps

1. **Data model + migrations**
    - Rename `BehaviorEvent` → `BehaviorSignal`, drop `EventType`, add `schema_id`, `signal_key`, `attributes` JSONB, `version`.
    - Author Liquibase change sets (`db/changelog/db.changelog-master.yaml`) to create `behavior_signals`, `behavior_signal_metrics`, `behavior_insights`, and `behavior_embeddings`, removing commerce-specific columns.
    - Update repositories and query builders to reference `schemaId` and signal attributes.

2. **Schema registry + validation**
   - Add `BehaviorSignalDefinition`, `SignalFieldDefinition`, `SchemaValidationRule`.
   - Implement `BehaviorSchemaRegistry` that loads YAML definitions from `classpath:/behavior/schemas/*.yml` and exposes discovery APIs.
   - Replace `BehaviorSignalValidator` with `BehaviorSignalValidator` that enforces schema types, allowed enums, max metadata size, etc.

3. **Ingestion + sinks**
    - Rename `BehaviorEventSink` → `BehaviorSignalSink`. Provide default Postgres sink plus Kafka/S3 sinks.
   - Update ingestion service, controllers, DTOs to accept `schemaId` and `attributes`.
   - Emit standardized application events (`BehaviorSignalIngested`, `BehaviorSignalBatchIngested`) for workers to consume.

4. **Metrics projection**
    - Define `BehaviorMetricProjector` SPI with `supports(schemaId)` and `project(BehaviorSignal signal, MetricAccumulator accumulator)`.
    - Replace `RealTimeAggregationWorker` with `MetricProjectionWorker`, routing signals to the enabled projectors declared in `ai.behavior.processing.metrics.enabled-projectors`.
    - Persist results in `behavior_signal_metrics` as `{metric_key, metric_value, metric_type, attributes_json}` keyed by user/date/tenant.

5. **Insights + segmentation**
   - Create `BehaviorInsightStrategy` and `SegmentationStrategy` interfaces; re-implement default strategy to use neutral KPIs (engagement_score, interaction_density, recency_index).
   - Move commerce heuristics (cart abandoner, frequent buyer, VIP) to optional `ai-infrastructure-behavior-commerce` package that depends on the base module but is not packaged by default.
   - `BehaviorAnalysisService` resolves strategy beans via Spring ordering and merges their outputs.

6. **API + DTO cleanup**
   - Delete `com.ai.infrastructure.{controller,dto,service,entity}` packages.
   - Update `com.ai.behavior.api` DTOs to include `schemaId`, `attributes`, `signalKey`, and typed metadata map.
   - Add `/schemas` endpoints for discovery, `/signals` for ingestion, `/insights` for querying precomputed results.

7. **Configuration & docs**
   - Extend `BehaviorModuleProperties` to include schema loading paths, default sink, metric projector list, insight strategies, retention per schema.
   - Provide sample `behavior-schemas.yml` plus `application.yml` snippets under `docs/examples`.
   - Update README + development guide sections to describe schema registration workflow and SPI usage.

8. **Testing & observability**
   - Build unit + integration tests covering schema validation, projector execution, insight strategies, and API contracts.
   - Export Micrometer metrics: `behavior.signals.ingested`, `behavior.projector.execution`, `behavior.insights.latency`.

### Execution Checklist (Single Sprint)

- [ ] Remove backend-specific packages and DTOs.
- [ ] Introduce schema registry, definitions, and YAML loader.
- [ ] Rename entities/repositories/migrations to `BehaviorSignal`.
- [ ] Replace ingestion validator + sink to use schema metadata.
- [ ] Implement metric projector SPI and worker.
- [ ] Implement insight/segmentation strategy SPIs with neutral defaults.
- [ ] Update configuration properties, documentation, and OpenAPI.
- [ ] Add end-to-end tests covering ingestion → projection → insights.

---

## Detailed Design

### 1. Data Model & Persistence

| Artifact | Description | Notes |
|----------|-------------|-------|
| `BehaviorSignal` (entity) | Canonical record for an observed signal. Fields: `id (UUID)`, `tenantId`, `userId`, `sessionId`, `schemaId`, `signalKey`, `source`, `timestamp`, `ingestedAt`, `attributes (JSONB)`, `version`. | Replaces `BehaviorEvent`. `signalKey` supports idempotency and correlation. |
| `BehaviorSignalAttributes` | Type-safe map wrapper with helpers (`asString`, `asNumber`, `asBoolean`, `asList`). | Enforces schema-defined types and prevents unchecked casts. |
| `behavior_signals` | Primary table with composite indexes (`tenant_id,schema_id,timestamp DESC`, `user_id,schema_id,timestamp DESC`, `schema_id,signal_key`). | Managed via Liquibase (`db/changelog/db.changelog-master.yaml`). |
| `behavior_signal_metrics` | Flexible key/value store for projector output: `metric_key`, `metric_value`, `metric_type`, optional attributes JSON. | Replaces rigid `BehaviorMetrics` columns; also managed via Liquibase. |
| `behavior_insights` | Persists strategy output with neutral columns: `insight_type`, `scores`, `labels`, `segment`, `evidence`. | `insight_type` differentiates multiple strategy outputs per user. |

### 2. Schema Registry & Definitions

- `BehaviorSchemaRegistry` lives in `com.ai.behavior.schema` and loads YAML descriptors from `classpath:/behavior/schemas/*.yml` (overridable via configuration).
- Each `BehaviorSignalDefinition` contains:
  - `id`, `domain`, `summary`, `version`.
  - `attributes[]` with `name`, `type`, `required`, `validation` rules.
  - `embeddingPolicy` (enabled, minLength, adapters).
  - `metricHints` (keys, aggregation hints) used by default projectors.
  - `retentionDays`, `pii` classification for governance.
- Registry exposes discovery endpoints (`GET /api/ai-behavior/schemas`) and developer tooling (e.g., JSON Schema export).

Sample descriptor:

```
- id: content.view
  domain: media
  version: 1
  summary: User viewed a piece of content
  retentionDays: 180
  attributes:
    - name: contentId
      type: string
      required: true
      maxLength: 64
    - name: durationSeconds
      type: number
      required: false
      minimum: 0
    - name: device
      type: enum
      values: [web, ios, android, tv]
  embeddingPolicy:
    enabled: false
  metricHints:
    - key: engagement.view
      aggregation: count
```

> **Validation Tip:** install PyYAML (`python -m pip install pyyaml`) and run the snippet below to lint all schema files before committing:

```bash
python3 - <<'PY'
import json
from pathlib import Path
import yaml

errors = []
seen = set()
for schema_file in Path("ai-infrastructure-module/ai-infrastructure-behavior/src/main/resources/behavior/schemas").rglob("*.yml"):
    data = yaml.safe_load(schema_file.read_text())
    for entry in data:
        schema_id = entry.get("id")
        if not schema_id:
            errors.append(f"{schema_file}: missing id")
            continue
        if schema_id in seen:
            errors.append(f"duplicate schema id: {schema_id}")
        seen.add(schema_id)

if errors:
    print("\\n".join(errors))
    raise SystemExit(1)
print(f"Validated {len(seen)} schema definitions")
PY
```

### 3. Ingestion Flow

1. `POST /api/ai-behavior/signals` accepts `CreateBehaviorSignalRequest` with `schemaId`, `signalKey`, `userId/sessionId`, `timestamp`, and `attributes`.
2. `BehaviorSignalIngestionService` resolves schema, validates attributes via `BehaviorSignalValidator`, enriches metadata, and delegates to `BehaviorSignalSink`.
3. Persisted signals emit `BehaviorSignalIngested` events consumed by metrics, insight, and embedding workers.
4. Batch ingestion uses `POST /api/ai-behavior/signals/batch` with server-side validation against `ai.behavior.ingestion.maxBatchSize`.

### 4. Storage & Query

- `BehaviorSignalRepository` provides specification-based queries across schemaId, user, tenant, time range, and attribute predicates (`jsonb_path_query`).
- `BehaviorQueryService` evolves into `BehaviorSignalQueryService` that translates query DSL objects into repository criteria.
- `BehaviorDataProvider` stays interface-driven but works with `BehaviorSignal` objects and supports streaming for high-volume analytics.

### 5. Metrics Projection Layer

- SPI: `BehaviorMetricProjector`.
  - `boolean supports(String schemaId, BehaviorSignalDefinition definition)`
  - `void project(BehaviorSignal signal, MetricAccumulator accumulator)`
- `MetricProjectionWorker` listens to ingestion events, finds applicable projectors (configured via `ai.behavior.metrics.projectors`), and writes aggregates to `behavior_signal_metrics`.
- Default projector (`EngagementProjector`) tracks engagement_score inputs (views, interactions, recency, dwell time) using schema hints rather than hard-coded event types.
- Additional projectors can be packaged as Spring starters (e.g., `behavior-metrics-commerce`).

### 6. Insight & Segmentation Strategies

- SPI: `BehaviorInsightStrategy`.
  - Input: `BehaviorInsightContext` containing recent signals, metrics, schema definitions.
  - Output: `BehaviorInsightResult` with neutral KPIs (`engagement_score`, `interaction_density`, `recency_index`, `preferred_schemas`).
- `SegmentationStrategy` assigns segments such as `new`, `active`, `dormant`, `super_engaged` using configurable thresholds.
- Commerce-specific heuristics (cart abandoner, VIP) move to optional add-on modules so the base package remains domain-agnostic.

### 7. API Contract Updates

| Endpoint | Request Highlights | Response |
|----------|-------------------|----------|
| `POST /api/ai-behavior/signals` | `{ schemaId, signalKey?, userId?, sessionId?, attributes{} }` | Stored signal summary. |
| `POST /api/ai-behavior/signals/batch` | Array of request objects respecting `maxBatchSize`. | Count of stored signals. |
| `GET /api/ai-behavior/schemas` | Optional filters (`domain`, `version`). | List of definitions + JSON Schema refs. |
| `GET /api/ai-behavior/users/{id}/signals` | Query params: `schemaId`, `start`, `end`, attribute filter expressions. | Paginated `BehaviorSignal` records. |
| `GET /api/ai-behavior/users/{id}/metrics` | `metricKey`, `startDate`, `endDate`. | Aggregated metric values. |
| `GET /api/ai-behavior/users/{id}/insights` | `insightType` optional. | Latest `BehaviorInsightResult` objects. |

OpenAPI definitions must be regenerated to reflect `schemaId` + `attributes` payloads and the new discovery endpoint.

### 8. Configuration & Extensibility

```
ai:
  behavior:
    schemas:
      path: classpath:/behavior/schemas/*.yml
    ingestion:
      maxBatchSize: 500
      publishApplicationEvents: true
    sink:
      type: database
    metrics:
      projectors:
        - engagementProjector
    insights:
      strategies:
        - engagementInsightStrategy
        - segmentInsightStrategy
    retention:
      signalsDays: 180
      metricsDays: 365
      insightsDays: 90
```

- Additional sinks (Kafka, S3, Redis) are enabled via existing auto-configuration but now consume `BehaviorSignal`.
- Teams can package domain-specific schemas/projectors as separate starters to keep the base module slim.

### 9. Observability & Tooling

- Micrometer metrics:
  - `behavior.signals.ingested{schemaId}`
  - `behavior.signals.validation.failures{schemaId,reason}`
  - `behavior.metrics.projector.duration{projector}`
  - `behavior.insights.strategy.duration{strategy}`
- Structured logs include `schemaId`, `signalKey`, and `tenantId`.
- Utility scripts (optional):
  - `schema-doctor` – validates YAML descriptors and generates JSON Schema.
  - `signal-replay` – replays stored signals through projectors for backfill/testing.

### 10. Verification Plan

1. **Unit tests** for schema validation, attribute coercion, projector calculations, strategy outputs.
2. **Integration tests** ingesting multiple schema types (commerce-like, media, support) and verifying metrics/insights endpoints.
3. **Performance tests** for batch ingestion and projector throughput using Testcontainers.
4. **Documentation checks** ensuring `/schemas` endpoint matches YAML files and developer docs.

### 11. Rollout Notes

- Because no downstream services rely on the old APIs, merge everything in one release (e.g., `v1.0.0-beta`).
- Integration steps for adopter teams:
  1. Define schemas via YAML and include them on the classpath.
  2. Configure sinks/projectors/strategies via Spring (`application.yml`).
  3. Wire ingestion clients to the new schema-aware API and leverage `/schemas` for validation.

### 12. Migration Guidance for Existing Clones

Even though this release treats the module as greenfield, anyone who cloned the repository before the Liquibase move should:

1. **Drop legacy tables** – remove the old `behavior_events`, `behavior_metrics`, `behavior_insights`, `behavior_embeddings`, and `behavior_alerts` tables to avoid column mismatches.
2. **Remove Flyway configuration** – delete any `spring.flyway.*` properties or custom Flyway beans. The new starter no longer ships Flyway migrations.
3. **Enable Liquibase** – ensure the host application points to the bundled changelog by adding (or keeping) the default:

   ```yaml
   spring:
     liquibase:
       change-log: classpath:/db/changelog/db.changelog-master.yaml
       enabled: true
   ```

4. **Apply the new schema** – run `./mvnw -pl ai-infrastructure-behavior liquibase:update` (or let Spring Boot execute Liquibase on startup).
5. **Validate custom schemas** – run the YAML validation snippet described in “Detailed Design → Schema Registry & Definitions” (or an equivalent lint step) against every `behavior/schemas/*.yml` file before deployment.

After these steps the repository will align with the Liquibase-driven `behavior_signals`/`behavior_signal_metrics` schema and the new schema registry.

---

## Module Structure

### Directory Structure

```
ai-behavior-module/
├── pom.xml
├── README.md
│
├── src/main/java/com/ai/behavior/
│   │
│   ├── model/
│   │   ├── BehaviorSignal.java                   # canonical signal aggregate (schemaId + attributes)
│   │   ├── BehaviorQuery.java                    # query builder & attribute filters
│   │   ├   BehaviorMetrics.java                  # JSON-based metric snapshots
│   │   ├── BehaviorInsights.java                 # pre-computed insights/segments
│   │   ├── BehaviorEmbedding.java                # stored embeddings
│   │   └── BehaviorAlert.java                    # anomaly records
│   │
│   ├── schema/
│   │   ├── BehaviorSchemaRegistry.java
│   │   ├── BehaviorSignalDefinition.java
│   │   ├── EmbeddingPolicy.java
│   │   └── YamlBehaviorSchemaRegistry.java
│   │
│   ├── metrics/
│   │   ├── MetricProjectionWorker.java
│   │   ├── BehaviorMetricProjector.java
│   │   └── projector/
│   │       └── EngagementMetricProjector.java
│   │
│   ├── ingestion/
│   │   ├── BehaviorIngestionController.java       # `/api/ai-behavior/signals`
│   │   ├── BehaviorSignalSink.java                # storage SPI
│   │   ├── BehaviorSignalValidator.java           # schema-driven validation
│   │   ├── BehaviorIngestionService.java
│   │   └── impl/ (database, kafka, redis, S3, etc.)
│   │
│   ├── storage/
│   │   ├── BehaviorSignalRepository.java
│   │   ├── BehaviorMetricsRepository.java
│   │   ├── BehaviorInsightsRepository.java
│   │   ├── BehaviorDataProvider.java
│   │   └── impl/ (database/external/aggregated providers)
│   │
│   ├── processing/
│   │   ├── worker/ (metric projection, pattern, anomaly, embedding, segmentation)
│   │   └── analyzer/ (pattern/anomaly/segmentation strategies)
│   │
│   ├── api/
│   │   ├── BehaviorIngestionController
│   │   ├── BehaviorSchemaController               # `/api/ai-behavior/schemas`
│   │   ├── BehaviorQueryController
│   │   ├── BehaviorInsightsController
│   │   └── BehaviorMonitoringController
│   │
│   ├── config/ (auto-configuration + `BehaviorModuleProperties`)
│   └── exception/, service/, etc.
│
├── src/main/resources/
│   ├── behavior/schemas/                         # default YAML signal definitions
│   └── db/changelog/db.changelog-master.yaml     # Liquibase change log (Flyway removed)
│
└── src/test/java/com/ai/behavior/
    ├── integration/
    └── unit/
```

---

## Core Interfaces

### 1. BehaviorSignalSink (Write Interface)

**Purpose:** Define WHERE signals are stored (pluggable storage)**

```java
package com.ai.behavior.ingestion;

import com.ai.behavior.model.BehaviorSignal;
import java.util.List;

public interface BehaviorSignalSink {

    void accept(BehaviorSignal signal);

    void acceptBatch(List<BehaviorSignal> signals);

    default void flush() {
        // Default: no-op
    }

    String getSinkType();
}
```

### 2. BehaviorDataProvider (Read Interface)

**Purpose:** Define HOW to query behavior data (flexible sources)**

```java
package com.ai.behavior.storage;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import java.util.List;
import java.util.UUID;

public interface BehaviorDataProvider {

    List<BehaviorSignal> query(BehaviorQuery query);

    default List<BehaviorSignal> getRecentEvents(UUID userId, int limit) {
        return query(BehaviorQuery.forUser(userId).limit(limit));
    }

    default List<BehaviorSignal> getEntityEvents(String entityType, String entityId) {
        return query(BehaviorQuery.forEntity(entityType, entityId));
    }

    String getProviderType();
}
```

### 3. BehaviorSchemaRegistry (Schema Interface)

**Purpose:** Expose registered behavior signal definitions to validators, APIs, and analyzers**

```java
package com.ai.behavior.schema;

import java.util.Collection;
import java.util.Optional;

public interface BehaviorSchemaRegistry {

    Optional<BehaviorSignalDefinition> find(String schemaId);

    BehaviorSignalDefinition getRequired(String schemaId);

    Collection<BehaviorSignalDefinition> getAll();
}
```

### 4. BehaviorMetricProjector (Metrics SPI)

```java
package com.ai.behavior.metrics;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.BehaviorSignalDefinition;

public interface BehaviorMetricProjector {

    boolean supports(BehaviorSignal signal, BehaviorSignalDefinition definition);

    void project(BehaviorSignal signal,
                 BehaviorSignalDefinition definition,
                 MetricAccumulator accumulator);

    String getName();
}
```

### 3. BehaviorAnalyzer (Analysis Interface)

**Purpose:** Define HOW behavior is analyzed (extensible analyzers)

```java
package com.ai.behavior.processing.analyzer;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorInsights;
import java.util.List;
import java.util.UUID;

/**
 * Interface for behavior analysis.
 * Different analyzers can focus on:
 * - Pattern detection
 * - Anomaly detection
 * - User segmentation
 * - Churn prediction
 * - Conversion optimization
 */
public interface BehaviorAnalyzer {
    
    /**
     * Analyze behavior events and generate insights
     * 
     * @param userId the user ID
     * @param events the behavior events to analyze
     * @return behavior insights
     */
    BehaviorInsights analyze(UUID userId, List<BehaviorSignal> events);
    
    /**
     * Get the analyzer type
     */
    String getAnalyzerType();
    
    /**
     * Check if this analyzer supports the given event types
     */
    boolean supports(List<String> eventTypes);
}
```

---

## Data Models

### 1. BehaviorSignal (Core Event Model)

**Simplified, lightweight, domain-agnostic**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * BehaviorSignal - Core lightweight event model
 * 
 * Simplified from previous 20+ fields to essential data only.
 * Analysis results stored separately (not in event).
 * 
 * Design principles:
 * - Fast writes (minimal fields)
 * - Flexible metadata (JSON for domain-specific data)
 * - No AI fields here (separate table)
 * - Partitioned by date (automatic cleanup)
 */
@Entity
@Table(
    name = "behavior_events",
    indexes = {
        @Index(name = "idx_behavior_user_time", columnList = "user_id, timestamp DESC"),
        @Index(name = "idx_behavior_entity", columnList = "entity_type, entity_id"),
        @Index(name = "idx_behavior_type", columnList = "event_type"),
        @Index(name = "idx_behavior_session", columnList = "session_id"),
        @Index(name = "idx_behavior_timestamp", columnList = "timestamp DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorSignal {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * User who performed the action
     * NULL for anonymous users (use session_id)
     */
    @Column(name = "user_id")
    private UUID userId;
    
    /**
     * Type of behavior event
     * Simple enum: view, click, purchase, search, feedback, etc.
     * Keep under 15 types for simplicity
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    /**
     * Type of entity the event relates to
     * Examples: product, page, order, article, video
     */
    @Column(name = "entity_type", length = 50)
    private String entityType;
    
    /**
     * ID of the entity
     * Examples: product ID, page slug, order ID
     */
    @Column(name = "entity_id", length = 255)
    private String entityId;
    
    /**
     * Session ID for tracking user journeys
     * Used for anonymous users and session analysis
     */
    @Column(name = "session_id", length = 255)
    private String sessionId;
    
    /**
     * When the event occurred
     * Used for time-series analysis and partitioning
     */
    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;
    
    /**
     * Flexible metadata (JSON)
     * Store domain-specific data here:
     * - source: "web_app", "mobile_app", "pos_system"
     * - device: "iPhone 15", "Chrome on Windows"
     * - location: "US-CA-SF"
     * - price: 199.99
     * - quantity: 2
     * - Any custom fields
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    /**
     * When this event was ingested (system timestamp)
     * Different from 'timestamp' which is user action time
     */
    @CreationTimestamp
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;
    
    // Helper methods
    
    public String getMetadataValue(String key) {
        return metadata != null ? (String) metadata.get(key) : null;
    }
    
    public void setMetadataValue(String key, Object value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }
    
    public boolean isAnonymous() {
        return userId == null;
    }
    
    public String getUserIdentifier() {
        return userId != null ? userId.toString() : sessionId;
    }
}
```

### 2. BehaviorInsights (Analysis Results)

**Pre-computed insights for fast reads**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BehaviorInsights - Pre-computed analysis results
 * 
 * Stores results of async analysis for fast retrieval.
 * Updated periodically (every 5 min, hourly, daily depending on type)
 * 
 * Separation principle: Raw events are cheap to write,
 * insights are expensive to compute but cheap to read.
 */
@Entity
@Table(
    name = "behavior_insights",
    indexes = {
        @Index(name = "idx_insights_user", columnList = "user_id"),
        @Index(name = "idx_insights_valid", columnList = "valid_until")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorInsights {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    /**
     * Detected patterns
     * Examples: ["frequent_buyer", "evening_shopper", "mobile_first", "price_sensitive"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "patterns", columnDefinition = "jsonb")
    private List<String> patterns;
    
    /**
     * Behavioral scores
     * Examples:
     * - engagement_score: 0.85
     * - conversion_probability: 0.65
     * - churn_risk: 0.15
     * - lifetime_value_estimate: 1250.00
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", columnDefinition = "jsonb")
    private Map<String, Double> scores;
    
    /**
     * User segment
     * Examples: "VIP", "at_risk", "new_user", "dormant"
     */
    @Column(name = "segment", length = 100)
    private String segment;
    
    /**
     * Preferences detected from behavior
     * Examples:
     * - preferred_categories: ["watches", "jewelry"]
     * - price_range: "luxury"
     * - shopping_time: "evening"
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private Map<String, Object> preferences;
    
    /**
     * Recommended actions
     * Examples: ["send_promotion", "offer_vip_upgrade", "re_engagement_email"]
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    private List<String> recommendations;
    
    /**
     * When this insight was computed
     */
    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;
    
    /**
     * When this insight expires (cache TTL)
     * After expiration, re-analyze
     */
    @Column(name = "valid_until", nullable = false)
    private LocalDateTime validUntil;
    
    /**
     * Analysis version (for A/B testing different algorithms)
     */
    @Column(name = "analysis_version", length = 50)
    private String analysisVersion;
    
    // Helper methods
    
    public boolean isValid() {
        return validUntil.isAfter(LocalDateTime.now());
    }
    
    public boolean hasPattern(String pattern) {
        return patterns != null && patterns.contains(pattern);
    }
    
    public Double getScore(String scoreType) {
        return scores != null ? scores.get(scoreType) : null;
    }
}
```

### 3. BehaviorEmbedding (Selective Text Embeddings)

**Only for text content that needs semantic search**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BehaviorEmbedding - Selective text embeddings
 * 
 * ONLY created for events with text content:
 * - User feedback
 * - Product reviews
 * - Search queries
 * - Support messages
 * 
 * NOT created for:
 * - Clicks
 * - Views
 * - Purchases
 * - Simple events
 * 
 * Principle: Embeddings are expensive (API cost + storage).
 * Only generate when semantic search provides value.
 */
@Entity
@Table(
    name = "behavior_embeddings",
    indexes = {
    @Index(name = "idx_embedding_signal", columnList = "behavior_signal_id"),
        @Index(name = "idx_embedding_type", columnList = "embedding_type")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorEmbedding {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Reference to the behavior signal
     */
    @Column(name = "behavior_signal_id", nullable = false)
    private UUID behaviorSignalId;
    
    /**
     * Type of embedding
     * Examples: "feedback", "review", "search_query", "support_message"
     */
    @Column(name = "embedding_type", nullable = false, length = 50)
    private String embeddingType;
    
    /**
     * Original text that was embedded
     */
    @Column(name = "original_text", columnDefinition = "TEXT")
    private String originalText;
    
    /**
     * Vector embedding (1536 dimensions for text-embedding-3-small)
     * Stored as binary for efficiency
     */
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;
    
    /**
     * Model used for embedding
     */
    @Column(name = "model", length = 100)
    private String model;
    
    /**
     * When embedding was generated
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    /**
     * Optional: Category detected from embedding
     */
    @Column(name = "detected_category", length = 100)
    private String detectedCategory;
    
    /**
     * Optional: Sentiment score (-1 to 1)
     */
    @Column(name = "sentiment_score")
    private Double sentimentScore;
}
```

### 4. BehaviorMetrics (Aggregated Counters)

**Fast aggregated metrics**

```java
package com.ai.behavior.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * BehaviorMetrics - Pre-aggregated metrics
 * 
 * Updated in real-time by MetricProjectionWorker.
 * Provides fast counts without scanning raw events.
 * 
 * Partitioned by date for efficient queries.
 */
@Entity
@Table(
    name = "behavior_metrics",
    indexes = {
        @Index(name = "idx_metrics_user_date", columnList = "user_id, metric_date DESC"),
        @Index(name = "idx_metrics_date", columnList = "metric_date DESC")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;
    
    // Event counts
    @Column(name = "view_count")
    private Integer viewCount = 0;
    
    @Column(name = "click_count")
    private Integer clickCount = 0;
    
    @Column(name = "search_count")
    private Integer searchCount = 0;
    
    @Column(name = "add_to_cart_count")
    private Integer addToCartCount = 0;
    
    @Column(name = "purchase_count")
    private Integer purchaseCount = 0;
    
    @Column(name = "feedback_count")
    private Integer feedbackCount = 0;
    
    // Session metrics
    @Column(name = "session_count")
    private Integer sessionCount = 0;
    
    @Column(name = "avg_session_duration_seconds")
    private Integer avgSessionDurationSeconds = 0;
    
    // Conversion metrics
    @Column(name = "conversion_rate")
    private Double conversionRate = 0.0;
    
    @Column(name = "total_revenue")
    private Double totalRevenue = 0.0;
    
    // Helper methods
    
    public void incrementView() {
        this.viewCount++;
    }
    
    public void incrementPurchase(double amount) {
        this.purchaseCount++;
        this.totalRevenue += amount;
        updateConversionRate();
    }
    
    private void updateConversionRate() {
        if (viewCount > 0) {
            this.conversionRate = (double) purchaseCount / viewCount;
        }
    }
}
```



---

## Implementation Components

### 1. BehaviorIngestionService

**Main service for receiving events**

```java
package com.ai.behavior.ingestion;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.ingestion.validator.BehaviorSignalValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * BehaviorIngestionService
 * 
 * Main entry point for behavior event ingestion.
 * 
 * Flow:
 * 1. Validate event
 * 2. Enrich event (add system metadata)
 * 3. Store via sink (pluggable)
 * 4. Publish for async processing
 * 5. Return immediately (non-blocking)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorIngestionService {
    
    private final BehaviorSignalValidator validator;
    private final BehaviorSignalSink sink;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Ingest a single behavior event
     */
    @Transactional
    public void ingest(BehaviorSignal event) {
        log.debug("Ingesting behavior event: type={}, userId={}", 
            event.getEventType(), event.getUserId());
        
        try {
            // 1. Validate
            validator.validate(event);
            
            // 2. Enrich
            enrichEvent(event);
            
            // 3. Store (via pluggable sink)
            sink.accept(event);
            
            // 4. Publish for async processing
            eventPublisher.publishEvent(new BehaviorSignalIngested(event));
            
            log.debug("Successfully ingested event: {}", event.getId());
            
        } catch (Exception e) {
            log.error("Failed to ingest event: {}", event, e);
            throw new BehaviorIngestionException("Failed to ingest event", e);
        }
    }
    
    /**
     * Ingest multiple events in batch (more efficient)
     */
    @Transactional
    public void ingestBatch(List<BehaviorSignal> events) {
        log.info("Ingesting batch of {} events", events.size());
        
        try {
            // Validate all
            events.forEach(validator::validate);
            
            // Enrich all
            events.forEach(this::enrichEvent);
            
            // Store batch (more efficient)
            sink.acceptBatch(events);
            
            // Publish batch event
            eventPublisher.publishEvent(new BehaviorSignalBatchIngested(events));
            
            log.info("Successfully ingested batch of {} events", events.size());
            
        } catch (Exception e) {
            log.error("Failed to ingest batch", e);
            throw new BehaviorIngestionException("Failed to ingest batch", e);
        }
    }
    
    /**
     * Enrich event with system metadata
     */
    private void enrichEvent(BehaviorSignal event) {
        // Set ID if not present
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
        }
        
        // Set ingestion timestamp
        if (event.getIngestedAt() == null) {
            event.setIngestedAt(LocalDateTime.now());
        }
        
        // Set event timestamp if not present
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        
        // Add system metadata
        if (event.getMetadata() == null) {
            event.setMetadata(new java.util.HashMap<>());
        }
        event.getMetadata().put("_ingested_at", event.getIngestedAt().toString());
        event.getMetadata().put("_version", "1.0");
    }
}

/**
 * Event published after successful ingestion
 * Picked up by async workers
 */
class BehaviorSignalIngested {
    private final BehaviorSignal event;
    
    public BehaviorSignalIngested(BehaviorSignal event) {
        this.event = event;
    }
    
    public BehaviorSignal getEvent() {
        return event;
    }
}

class BehaviorSignalBatchIngested {
    private final List<BehaviorSignal> events;
    
    public BehaviorSignalBatchIngested(List<BehaviorSignal> events) {
        this.events = events;
    }
    
    public List<BehaviorSignal> getEvents() {
        return events;
    }
}
```

### 2. DatabaseEventSink (Default Implementation)

```java
package com.ai.behavior.ingestion.impl;

import com.ai.behavior.ingestion.BehaviorSignalSink;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.storage.BehaviorSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DatabaseEventSink - Default storage implementation
 * 
 * Stores events in PostgreSQL database.
 * Active by default unless user configures a different sink.
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "ai.behavior.sink.type",
    havingValue = "database",
    matchIfMissing = true  // Default
)
@RequiredArgsConstructor
public class DatabaseEventSink implements BehaviorSignalSink {
    
    private final BehaviorSignalRepository repository;
    
    @Override
    public void accept(BehaviorSignal event) {
        log.trace("Storing event in database: {}", event.getId());
        repository.save(event);
    }
    
    @Override
    public void acceptBatch(List<BehaviorSignal> events) {
        log.debug("Storing batch of {} events in database", events.size());
        repository.saveAll(events);
    }
    
    @Override
    public String getSinkType() {
        return "database";
    }
}
```

### 3. Async Processing Workers

#### MetricProjectionWorker

```java
package com.ai.behavior.processing.worker;

import com.ai.behavior.ingestion.BehaviorSignalIngested;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorMetrics;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * MetricProjectionWorker
 * 
 * Listens to ingested events and updates metrics in real-time.
 * Runs async, doesn't block ingestion.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricProjectionWorker {
    
    private final BehaviorMetricsRepository metricsRepository;
    
    @Async
    @EventListener
    public void onEventIngested(BehaviorSignalIngested event) {
        BehaviorSignal behaviorEvent = event.getEvent();
        
        // Skip anonymous events for user metrics
        if (behaviorEvent.getUserId() == null) {
            return;
        }
        
        try {
            updateMetrics(behaviorEvent);
        } catch (Exception e) {
            log.error("Failed to update metrics for event: {}", behaviorEvent.getId(), e);
            // Don't throw - metrics are non-critical
        }
    }
    
    private void updateMetrics(BehaviorSignal event) {
        LocalDate date = event.getTimestamp().toLocalDate();
        
        // Get or create metrics for user + date
        BehaviorMetrics metrics = metricsRepository
            .findByUserIdAndMetricDate(event.getUserId(), date)
            .orElseGet(() -> BehaviorMetrics.builder()
                .userId(event.getUserId())
                .metricDate(date)
                .build());
        
        // Update based on event type
        switch (event.getEventType()) {
            case "view":
                metrics.incrementView();
                break;
            case "click":
                metrics.setClickCount(metrics.getClickCount() + 1);
                break;
            case "search":
                metrics.setSearchCount(metrics.getSearchCount() + 1);
                break;
            case "add_to_cart":
                metrics.setAddToCartCount(metrics.getAddToCartCount() + 1);
                break;
            case "purchase":
                Double amount = (Double) event.getMetadata().get("amount");
                metrics.incrementPurchase(amount != null ? amount : 0.0);
                break;
        }
        
        metricsRepository.save(metrics);
    }
}
```

#### PatternDetectionWorker

```java
package com.ai.behavior.processing.worker;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.processing.analyzer.PatternAnalyzer;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.behavior.storage.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PatternDetectionWorker
 * 
 * Scheduled worker that detects patterns in user behavior.
 * Runs every 5 minutes for active users.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PatternDetectionWorker {
    
    private final BehaviorDataProvider dataProvider;
    private final BehaviorInsightsRepository insightsRepository;
    private final PatternAnalyzer patternAnalyzer;
    
    @Scheduled(fixedDelay = 300000) // Every 5 minutes
    public void detectPatterns() {
        log.info("Starting pattern detection worker");
        
        try {
            // Get users with recent activity (last 5 minutes)
            List<UUID> activeUsers = getRecentlyActiveUsers();
            
            log.info("Analyzing patterns for {} active users", activeUsers.size());
            
            // Analyze each user
            activeUsers.forEach(this::analyzeUserPatterns);
            
            log.info("Pattern detection completed");
            
        } catch (Exception e) {
            log.error("Pattern detection worker failed", e);
        }
    }
    
    private void analyzeUserPatterns(UUID userId) {
        try {
            // Get last 24 hours of events
            List<BehaviorSignal> events = dataProvider.getRecentEvents(userId, 1000);
            
            if (events.isEmpty()) {
                return;
            }
            
            // Analyze patterns using ai-core
            BehaviorInsights insights = patternAnalyzer.analyze(userId, events);
            
            // Set expiration (5 minutes from now)
            insights.setValidUntil(LocalDateTime.now().plusMinutes(5));
            
            // Save insights
            insightsRepository.save(insights);
            
        } catch (Exception e) {
            log.error("Failed to analyze patterns for user: {}", userId, e);
        }
    }
    
    private List<UUID> getRecentlyActiveUsers() {
        // Query for users with events in last 5 minutes
        // Implementation depends on your storage
        return List.of(); // Placeholder
    }
}
```

#### EmbeddingGenerationWorker

```java
package com.ai.behavior.processing.worker;

import com.ai.behavior.ingestion.BehaviorSignalIngested;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorEmbedding;
import com.ai.behavior.storage.BehaviorEmbeddingRepository;
import com.ai.infrastructure.core.AICoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * EmbeddingGenerationWorker
 * 
 * SELECTIVELY generates embeddings only for text content.
 * 
 * Generates embeddings for:
 * - feedback
 * - review
 * - search queries (complex ones)
 * 
 * Does NOT generate for:
 * - view, click, purchase (no semantic value)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingGenerationWorker {
    
    private final AICoreService aiCoreService;
    private final BehaviorEmbeddingRepository embeddingRepository;
    
    @Async
    @EventListener
    public void onEventIngested(BehaviorSignalIngested event) {
        BehaviorSignal behaviorEvent = event.getEvent();
        
        // Check if this event needs embedding
        if (!shouldGenerateEmbedding(behaviorEvent)) {
            return;
        }
        
        try {
            generateEmbedding(behaviorEvent);
        } catch (Exception e) {
            log.error("Failed to generate embedding for event: {}", behaviorEvent.getId(), e);
        }
    }
    
    private boolean shouldGenerateEmbedding(BehaviorSignal event) {
        // Only generate for events with text content
        switch (event.getEventType()) {
            case "feedback":
            case "review":
            case "search":
                return extractText(event) != null;
            default:
                return false;
        }
    }
    
    private void generateEmbedding(BehaviorSignal event) {
        String text = extractText(event);
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        
        log.debug("Generating embedding for event: {} (type: {})", 
            event.getId(), event.getEventType());
        
        // Generate embedding using ai-core
        float[] embedding = aiCoreService.generateEmbedding(text);
        
        // Store embedding
        BehaviorEmbedding behaviorEmbedding = BehaviorEmbedding.builder()
            .behaviorEventId(event.getId())
            .embeddingType(event.getEventType())
            .originalText(text)
            .embedding(embedding)
            .model("text-embedding-3-small")
            .build();
        
        embeddingRepository.save(behaviorEmbedding);
        
        log.debug("Successfully generated embedding for event: {}", event.getId());
    }
    
    private String extractText(BehaviorSignal event) {
        // Extract text from metadata based on event type
        if (event.getMetadata() == null) {
            return null;
        }
        
        switch (event.getEventType()) {
            case "feedback":
                return (String) event.getMetadata().get("feedback_text");
            case "review":
                return (String) event.getMetadata().get("review_text");
            case "search":
                return (String) event.getMetadata().get("query");
            default:
                return null;
        }
    }
}
```

---

## Integration Patterns

### 1. Web Application Integration

```javascript
// Frontend SDK: behavior-tracker.js

class BehaviorTracker {
  constructor(apiUrl, userId, sessionId) {
    this.apiUrl = apiUrl;
    this.userId = userId;
    this.sessionId = sessionId;
    this.queue = [];
    this.flushInterval = 5000; // Flush every 5 seconds
    this.startAutoFlush();
  }

  // Track a behavior event
  track(eventType, entityType, entityId, metadata = {}) {
    const event = {
      userId: this.userId,
      eventType: eventType,
      entityType: entityType,
      entityId: entityId,
      sessionId: this.sessionId,
      timestamp: new Date().toISOString(),
      metadata: {
        ...metadata,
        source: 'web_app',
        page: window.location.pathname,
        referrer: document.referrer,
        userAgent: navigator.userAgent
      }
    };

    this.queue.push(event);

    // Flush immediately for critical events
    if (this.isCriticalEvent(eventType)) {
      this.flush();
    }
  }

  // Convenience methods
  trackView(entityType, entityId) {
    this.track('view', entityType, entityId);
  }

  trackClick(elementId, metadata = {}) {
    this.track('click', 'ui_element', elementId, metadata);
  }

  trackPurchase(orderId, amount, items) {
    this.track('purchase', 'order', orderId, {
      amount: amount,
      items: items,
      currency: 'USD'
    });
  }

  trackSearch(query) {
    this.track('search', null, null, {
      query: query
    });
  }

  trackFeedback(feedbackText, category) {
    this.track('feedback', 'app', null, {
      feedback_text: feedbackText,
      category: category
    });
  }

  // Flush queue to server
  async flush() {
    if (this.queue.length === 0) {
      return;
    }

    const eventsToSend = [...this.queue];
    this.queue = [];

    try {
      await fetch(`${this.apiUrl}/api/ai-behavior/ingest/batch`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${this.getAuthToken()}`
        },
        body: JSON.stringify({ events: eventsToSend })
      });
    } catch (error) {
      console.error('Failed to send behavior events:', error);
      // Re-queue on failure
      this.queue.push(...eventsToSend);
    }
  }

  // Auto flush
  startAutoFlush() {
    setInterval(() => this.flush(), this.flushInterval);
  }

  isCriticalEvent(eventType) {
    return ['purchase', 'feedback'].includes(eventType);
  }

  getAuthToken() {
    // Get from your auth system
    return localStorage.getItem('auth_token');
  }
}

// Usage in React app
import { useEffect } from 'react';

const tracker = new BehaviorTracker(
  process.env.REACT_APP_API_URL,
  currentUser.id,
  generateSessionId()
);

// Track product view
function ProductPage({ product }) {
  useEffect(() => {
    tracker.trackView('product', product.id);
  }, [product.id]);

  const handleAddToCart = () => {
    tracker.track('add_to_cart', 'product', product.id, {
      price: product.price,
      quantity: 1
    });
    // ... rest of add to cart logic
  };

  return (
    <div>
      <h1>{product.name}</h1>
      <button onClick={handleAddToCart}>Add to Cart</button>
    </div>
  );
}
```

### 2. Mobile App Integration

```java
// Android SDK: BehaviorTracker.java

package com.ai.behavior.sdk;

import java.util.*;
import java.util.concurrent.*;

public class BehaviorTracker {
    private final String apiUrl;
    private final UUID userId;
    private final String sessionId;
    private final Queue<BehaviorSignal> queue;
    private final ScheduledExecutorService executor;
    
    public BehaviorTracker(String apiUrl, UUID userId) {
        this.apiUrl = apiUrl;
        this.userId = userId;
        this.sessionId = UUID.randomUUID().toString();
        this.queue = new ConcurrentLinkedQueue<>();
        this.executor = Executors.newSingleThreadScheduledExecutor();
        startAutoFlush();
    }
    
    public void track(String eventType, String entityType, String entityId, Map<String, Object> metadata) {
        BehaviorSignal event = BehaviorSignal.builder()
            .userId(userId)
            .eventType(eventType)
            .entityType(entityType)
            .entityId(entityId)
            .sessionId(sessionId)
            .timestamp(LocalDateTime.now())
            .metadata(enrichMetadata(metadata))
            .build();
        
        queue.add(event);
        
        if (isCriticalEvent(eventType)) {
            flush();
        }
    }
    
    // Convenience methods
    public void trackView(String entityType, String entityId) {
        track("view", entityType, entityId, Map.of());
    }
    
    public void trackPurchase(String orderId, double amount) {
        track("purchase", "order", orderId, Map.of(
            "amount", amount,
            "currency", "USD"
        ));
    }
    
    private Map<String, Object> enrichMetadata(Map<String, Object> metadata) {
        Map<String, Object> enriched = new HashMap<>(metadata);
        enriched.put("source", "mobile_app");
        enriched.put("device", Build.MODEL);
        enriched.put("os_version", Build.VERSION.RELEASE);
        enriched.put("app_version", BuildConfig.VERSION_NAME);
        return enriched;
    }
    
    private void flush() {
        if (queue.isEmpty()) {
            return;
        }
        
        List<BehaviorSignal> events = new ArrayList<>();
        while (!queue.isEmpty()) {
            events.add(queue.poll());
        }
        
        // Send to API (async)
        CompletableFuture.runAsync(() -> sendToAPI(events));
    }
    
    private void sendToAPI(List<BehaviorSignal> events) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "/api/ai-behavior/ingest/batch"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(events)))
                .build();
            
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            Log.e("BehaviorTracker", "Failed to send events", e);
            // Re-queue
            queue.addAll(events);
        }
    }
    
    private void startAutoFlush() {
        executor.scheduleAtFixedRate(this::flush, 5, 5, TimeUnit.SECONDS);
    }
}
```

### 3. External System Integration (Adapter)

```java
package com.ai.behavior.adapter;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorQuery;
import com.ai.behavior.storage.BehaviorDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter for integrating with external analytics (e.g., Mixpanel, Amplitude)
 * 
 * Allows querying behavior data from external system instead of local DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalAnalyticsAdapter implements BehaviorDataProvider {
    
    private final MixpanelClient mixpanel;  // External analytics client
    
    @Override
    public List<BehaviorSignal> query(BehaviorQuery query) {
        log.debug("Querying behaviors from Mixpanel for user: {}", query.getUserId());
        
        try {
            // Query external analytics
            List<MixpanelEvent> externalEvents = mixpanel.queryEvents(
                query.getUserId().toString(),
                query.getStartTime(),
                query.getEndTime()
            );
            
            // Convert to BehaviorSignal
            return externalEvents.stream()
                .map(this::convertToBehaviorSignal)
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to query Mixpanel", e);
            return List.of();
        }
    }
    
    private BehaviorSignal convertToBehaviorSignal(MixpanelEvent mixpanelEvent) {
        return BehaviorSignal.builder()
            .userId(UUID.fromString(mixpanelEvent.getUserId()))
            .eventType(mapEventType(mixpanelEvent.getEventName()))
            .entityType(mixpanelEvent.getProperties().get("entity_type"))
            .entityId(mixpanelEvent.getProperties().get("entity_id"))
            .timestamp(mixpanelEvent.getTimestamp())
            .metadata(mixpanelEvent.getProperties())
            .build();
    }
    
    private String mapEventType(String mixpanelEventName) {
        // Map external event names to your event types
        switch (mixpanelEventName) {
            case "Product Viewed": return "view";
            case "Item Added": return "add_to_cart";
            case "Order Completed": return "purchase";
            default: return "custom";
        }
    }
    
    @Override
    public String getProviderType() {
        return "mixpanel";
    }
}
```

---

## Embedding Strategy

### When to Generate Embeddings

```
┌─────────────────────────────────────────────────────────────┐
│                    EMBEDDING DECISION TREE                   │
└─────────────────────────────────────────────────────────────┘

Event Type?
    │
    ├─ Simple Event (view, click, purchase)
    │  └─> NO EMBEDDING
    │      Reason: No semantic value
    │      Cost: $0
    │
    ├─ User Feedback
    │  └─> YES EMBEDDING
    │      Reason: Need to find similar feedback
    │      Cost: ~$0.0001 per event
    │      Value: Categorization, sentiment, similar issue detection
    │
    ├─ Product Review
    │  └─> YES EMBEDDING
    │      Reason: Semantic search, sentiment analysis
    │      Cost: ~$0.0001 per event
    │      Value: Find similar reviews, detect patterns
    │
    ├─ Search Query (short: "watch")
    │  └─> NO EMBEDDING
    │      Reason: Simple keyword search sufficient
    │      Cost: $0
    │
    └─ Search Query (complex: "luxury watch for women under $500")
       └─> YES EMBEDDING
           Reason: Intent understanding needed
           Cost: ~$0.0001 per query
           Value: Better search results, personalization
```

### Cost Analysis

```
Scenario: E-commerce site, 10,000 active users/day

WITHOUT selective embedding (current approach):
- Events per day: 500,000 (50 events/user average)
- All events embedded: 500,000 embeddings/day
- Cost per embedding: $0.0001
- Daily cost: $50
- Monthly cost: $1,500
- Annual cost: $18,000

WITH selective embedding (proposed):
- Events per day: 500,000
- Text events (5%): 25,000
  - Feedback: 5,000
  - Reviews: 10,000
  - Complex searches: 10,000
- Embeddings per day: 25,000
- Cost per embedding: $0.0001
- Daily cost: $2.50
- Monthly cost: $75
- Annual cost: $900

SAVINGS: $17,100/year (95% reduction)
```

---

## Configuration

### Application Properties

```yaml
# application.yml

ai:
  behavior:
    # Module enabled
    enabled: true
    
    # Ingestion configuration
    ingestion:
      validation:
        enabled: true
        strict-mode: false  # Reject invalid events or log and continue
      batch-size: 1000
    
    # Storage configuration
    sink:
      type: database  # Options: database, kafka, redis, hybrid, custom
      # Custom sink class (if type=custom)
      # custom-class: com.mycompany.CustomEventSink
      
      # Database sink configuration
      database:
        batch-insert: true
        batch-size: 100
      
      # Kafka sink configuration (if type=kafka)
      kafka:
        topic: behavior-events
        compression: snappy
        
      # Redis sink configuration (if type=redis)
      redis:
        ttl-days: 7
        
      # Hybrid sink configuration (if type=hybrid)
      hybrid:
        hot-storage: redis     # Recent events
        hot-retention-days: 7
        cold-storage: database # Historical events
    
    # Processing configuration
    processing:
      # Real-time aggregation
      aggregation:
        enabled: true
        async: true
      
      # Pattern detection
      pattern-detection:
        enabled: true
        schedule: "*/5 * * * *"  # Every 5 minutes (cron)
        analysis-window-hours: 24
        min-events-for-analysis: 10
      
      # Anomaly detection
      anomaly-detection:
        enabled: true
        schedule: "* * * * *"  # Every minute
        sensitivity: 0.8  # 0.0 (low) to 1.0 (high)
      
      # Embedding generation
      embedding:
        enabled: true
        async: true
        event-types:  # Only generate for these
          - feedback
          - review
          - search
        min-text-length: 10  # Don't embed very short text
      
      # User segmentation
      segmentation:
        enabled: true
        schedule: "0 2 * * *"  # Daily at 2 AM
    
    # Insights configuration
    insights:
      cache-ttl-minutes: 5
      min-events-for-insights: 10
    
    # Retention configuration
    retention:
      events-days: 90         # Raw events kept for 90 days
      insights-days: 180      # Insights kept longer
      metrics-days: 365       # Metrics kept for 1 year
      embeddings-days: 90     # Same as events
    
    # Performance configuration
    performance:
      async-executor:
        core-pool-size: 4
        max-pool-size: 16
        queue-capacity: 1000
```

### Programmatic Configuration

```java
package com.ai.behavior.config;

import com.ai.behavior.ingestion.BehaviorSignalSink;
import com.ai.behavior.storage.BehaviorDataProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomBehaviorConfiguration {
    
    /**
     * Custom sink implementation
     * Will be used instead of default if defined
     */
    @Bean
    public BehaviorSignalSink customEventSink() {
        return new MyCustomSink();
    }
    
    /**
     * Custom data provider
     * Can define multiple providers for different sources
     */
    @Bean
    public BehaviorDataProvider legacySystemProvider() {
        return new LegacySystemBehaviorProvider();
    }
    
    /**
     * Custom analyzer
     */
    @Bean
    public BehaviorAnalyzer churnPredictionAnalyzer() {
        return new ChurnPredictionAnalyzer();
    }
}
```

---

## Usage Examples

### Example 1: E-commerce Product Tracking

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    private final ProductRepository productRepository;
    private final BehaviorIngestionService behaviorIngestion;
    
    public Product getProduct(UUID productId, UUID userId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
        
        // Track view (async, non-blocking)
        behaviorIngestion.ingest(BehaviorSignal.builder()
            .userId(userId)
            .eventType("view")
            .entityType("product")
            .entityId(productId.toString())
            .metadata(Map.of(
                "product_name", product.getName(),
                "price", product.getPrice(),
                "category", product.getCategory()
            ))
            .build());
        
        return product;
    }
    
    public void addToCart(UUID productId, UUID userId, int quantity) {
        // Business logic...
        
        // Track add to cart
        behaviorIngestion.ingest(BehaviorSignal.builder()
            .userId(userId)
            .eventType("add_to_cart")
            .entityType("product")
            .entityId(productId.toString())
            .metadata(Map.of(
                "quantity", quantity,
                "price", product.getPrice()
            ))
            .build());
    }
}
```

### Example 2: User Feedback with Embedding

```java
@Service
@RequiredArgsConstructor
public class FeedbackService {
    
    private final BehaviorIngestionService behaviorIngestion;
    
    public void submitFeedback(UUID userId, String feedbackText, String category) {
        // Track feedback event (will trigger embedding generation automatically)
        behaviorIngestion.ingest(BehaviorSignal.builder()
            .userId(userId)
            .eventType("feedback")
            .entityType("app")
            .entityId(null)
            .metadata(Map.of(
                "feedback_text", feedbackText,  // Will be embedded
                "category", category
            ))
            .build());
        
        // EmbeddingGenerationWorker will:
        // 1. Detect this is feedback (has text)
        // 2. Generate embedding asynchronously
        // 3. Store in behavior_embeddings table
        // 4. Enable semantic search for similar feedback
    }
    
    public List<BehaviorSignal> findSimilarFeedback(String feedbackText) {
        // Generate embedding for search query
        float[] queryEmbedding = aiCoreService.generateEmbedding(feedbackText);
        
        // Search for similar feedback
        return embeddingSearchService.findSimilar(
            queryEmbedding,
            "feedback",
            10  // Top 10 similar
        );
    }
}
```

### Example 3: Personalized Recommendations

```java
@Service
@RequiredArgsConstructor
public class RecommendationService {
    
    private final BehaviorInsightsService insightsService;
    private final BehaviorDataProvider behaviorProvider;
    private final ProductRepository productRepository;
    
    public List<Product> getPersonalizedRecommendations(UUID userId, int limit) {
        // Get pre-computed insights (fast!)
        BehaviorInsights insights = insightsService.getUserInsights(userId);
        
        if (insights == null || !insights.isValid()) {
            // Fallback to default recommendations
            return getDefaultRecommendations(limit);
        }
        
        // Use insights to personalize
        List<String> preferredCategories = (List<String>) 
            insights.getPreferences().get("preferred_categories");
        
        String priceRange = (String) 
            insights.getPreferences().get("price_range");
        
        // Query products based on preferences
        return productRepository.findByPreferences(
            preferredCategories,
            priceRange,
            limit
        );
    }
    
    private List<Product> getDefaultRecommendations(int limit) {
        return productRepository.findTopRated(limit);
    }
}
```

### Example 4: Anomaly Detection (Fraud)

```java
@Service
@RequiredArgsConstructor
public class FraudDetectionService {
    
    private final BehaviorDataProvider behaviorProvider;
    private final AlertService alertService;
    
    @Scheduled(fixedDelay = 60000) // Every minute
    public void detectSuspiciousActivity() {
        // Get last minute of purchase events
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        
        List<BehaviorSignal> recentPurchases = behaviorProvider.query(
            BehaviorQuery.builder()
                .eventType("purchase")
                .startTime(oneMinuteAgo)
                .build()
        );
        
        // Check for suspicious patterns
        recentPurchases.forEach(event -> {
            if (isSuspicious(event)) {
                alertService.sendAlert(new FraudAlert(
                    event.getUserId(),
                    "Suspicious purchase pattern detected",
                    event
                ));
            }
        });
    }
    
    private boolean isSuspicious(BehaviorSignal event) {
        // Multiple purchases in short time
        List<BehaviorSignal> userPurchases = behaviorProvider.query(
            BehaviorQuery.forUser(event.getUserId())
                .eventType("purchase")
                .lastMinutes(5)
        );
        
        if (userPurchases.size() > 5) {
            return true;  // 5+ purchases in 5 minutes
        }
        
        // High value purchase
        Double amount = (Double) event.getMetadata().get("amount");
        if (amount != null && amount > 10000) {
            return true;  // Purchase over $10k
        }
        
        return false;
    }
}
```

---

## Performance Considerations

### 1. Database Optimization

```sql
-- Partitioning by month (automatic old data deletion)
CREATE TABLE behavior_events (
    id UUID NOT NULL,
    user_id UUID,
    event_type VARCHAR(50) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    -- ... other columns
) PARTITION BY RANGE (timestamp);

CREATE TABLE behavior_events_2025_11 
    PARTITION OF behavior_events
    FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE behavior_events_2025_12 
    PARTITION OF behavior_events
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

-- Automatic cleanup: just drop old partitions
DROP TABLE behavior_events_2025_10;  -- Drops entire October instantly

-- Indexes on each partition automatically
CREATE INDEX idx_behavior_user_time_2025_11
    ON behavior_events_2025_11(user_id, timestamp DESC);
```

### 2. Query Optimization

```java
// BAD: Loading all events then filtering in app
List<BehaviorSignal> allEvents = repository.findByUserId(userId);
List<BehaviorSignal> purchases = allEvents.stream()
    .filter(e -> e.getEventType().equals("purchase"))
    .toList();

// GOOD: Filter in database
@Query("SELECT e FROM BehaviorSignal e WHERE e.userId = :userId AND e.eventType = :type ORDER BY e.timestamp DESC")
List<BehaviorSignal> findByUserIdAndEventType(UUID userId, String eventType);

// BETTER: Use pre-computed metrics
BehaviorMetrics metrics = metricsRepository.findByUserIdAndDate(userId, LocalDate.now());
int purchaseCount = metrics.getPurchaseCount();  // Instant!
```

### 3. Caching Strategy

```java
@Service
@RequiredArgsConstructor
public class BehaviorInsightsService {
    
    private final BehaviorInsightsRepository repository;
    private final PatternAnalyzer analyzer;
    
    @Cacheable(value = "behavior-insights", key = "#userId")
    public BehaviorInsights getUserInsights(UUID userId) {
        // Check cache first (Redis)
        // Cache TTL: 5 minutes
        
        return repository.findByUserIdAndValidUntilAfter(
            userId,
            LocalDateTime.now()
        ).orElseGet(() -> {
            // Not found or expired - recompute
            return analyzer.analyze(userId);
        });
    }
}
```

### 4. Batch Processing

```java
// BAD: Process events one by one
events.forEach(event -> {
    behaviorIngestion.ingest(event);  // N database writes
});

// GOOD: Batch insert
behaviorIngestion.ingestBatch(events);  // 1 database write

// BETTER: Batch with optimal size
Lists.partition(events, 1000).forEach(batch -> {
    behaviorIngestion.ingestBatch(batch);  // 1000 events per batch
});
```

### 5. Async Processing

```java
// BAD: Synchronous analysis (blocks user request)
@GetMapping("/recommendations")
public List<Product> getRecommendations(@RequestParam UUID userId) {
    BehaviorInsights insights = analyzer.analyze(userId);  // SLOW! 500ms+
    return recommendationEngine.generate(insights);
}

// GOOD: Use pre-computed insights
@GetMapping("/recommendations")
public List<Product> getRecommendations(@RequestParam UUID userId) {
    BehaviorInsights insights = insightsService.getUserInsights(userId);  // FAST! <10ms from cache
    return recommendationEngine.generate(insights);
}

// Background worker computes insights async every 5 minutes
@Scheduled(fixedDelay = 300000)
public void computeInsights() {
    List<UUID> activeUsers = getActiveUsers();
    activeUsers.forEach(userId -> {
        BehaviorInsights insights = analyzer.analyze(userId);
        repository.save(insights);
    });
}
```

### 6. Selective Embedding

```java
// BAD: Embed everything (expensive!)
events.forEach(event -> {
    float[] embedding = aiCore.generateEmbedding(event.toString());  // $$$$
    store(embedding);
});

// GOOD: Only embed text content
events.forEach(event -> {
    if (hasTextContent(event)) {  // Only feedback, reviews, search
        String text = extractText(event);
        if (text.length() >= 10) {  // Skip very short text
            float[] embedding = aiCore.generateEmbedding(text);
            store(embedding);
        }
    }
});
```

---

## Monitoring & Observability

### Metrics to Track

```java
@Component
@RequiredArgsConstructor
public class BehaviorMetrics {
    
    private final MeterRegistry meterRegistry;
    
    // Ingestion metrics
    public void recordEventIngested(String eventType) {
        Counter.builder("behavior.events.ingested")
            .tag("type", eventType)
            .register(meterRegistry)
            .increment();
    }
    
    // Processing metrics
    public void recordProcessingTime(String workerType, long durationMs) {
        Timer.builder("behavior.processing.duration")
            .tag("worker", workerType)
            .register(meterRegistry)
            .record(Duration.ofMillis(durationMs));
    }
    
    // Embedding metrics
    public void recordEmbeddingGenerated(String embeddingType, long durationMs, double cost) {
        Counter.builder("behavior.embeddings.generated")
            .tag("type", embeddingType)
            .register(meterRegistry)
            .increment();
        
        Counter.builder("behavior.embeddings.cost")
            .tag("type", embeddingType)
            .register(meterRegistry)
            .increment(cost);
    }
    
    // Error metrics
    public void recordError(String component, String errorType) {
        Counter.builder("behavior.errors")
            .tag("component", component)
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment();
    }
}
```

### Health Checks

```java
@Component
public class BehaviorHealthIndicator implements HealthIndicator {
    
    @Autowired
    private BehaviorSignalRepository repository;
    
    @Autowired
    private BehaviorSignalSink sink;
    
    @Override
    public Health health() {
        try {
            // Check database connectivity
            long eventCount = repository.count();
            
            // Check sink health
            String sinkType = sink.getSinkType();
            
            // Check recent ingestion (last 5 minutes)
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
            long recentEvents = repository.countByIngestedAtAfter(fiveMinutesAgo);
            
            if (recentEvents == 0) {
                return Health.down()
                    .withDetail("reason", "No events ingested in last 5 minutes")
                    .build();
            }
            
            return Health.up()
                .withDetail("total_events", eventCount)
                .withDetail("recent_events_5min", recentEvents)
                .withDetail("sink_type", sinkType)
                .build();
                
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

### Logging

```java
// Structured logging for debugging
log.info("Behavior event ingested", 
    kv("event_id", event.getId()),
    kv("user_id", event.getUserId()),
    kv("event_type", event.getEventType()),
    kv("entity_type", event.getEntityType()),
    kv("ingestion_time_ms", duration)
);

// Error logging with context
log.error("Failed to process behavior event",
    kv("event_id", event.getId()),
    kv("error", e.getMessage()),
    kv("worker", "PatternDetectionWorker"),
    e
);
```

---

## Code Cleanup Plan

### Overview

Before starting the new implementation, clean up the existing behavior tracking code to avoid confusion and conflicts. This section details what to remove and what to keep.

### Files to Remove

#### 1. Old Behavior Entities

```bash
# Remove duplicate behavior entities
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/entity/Behavior.java
rm -f backend/src/main/java/com/easyluxury/entity/UserBehavior.java

# Remove DTOs
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/BehaviorRequest.java
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/BehaviorResponse.java
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/BehaviorAnalysisResult.java
rm -f backend/src/main/java/com/easyluxury/ai/dto/UserBehaviorRequest.java
rm -f backend/src/main/java/com/easyluxury/ai/dto/UserBehaviorResponse.java
```

#### 2. Old Repositories

```bash
# Remove repositories
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/repository/BehaviorRepository.java
rm -f backend/src/main/java/com/easyluxury/repository/UserBehaviorRepository.java
```

#### 3. Old Services

```bash
# Remove behavior services
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/service/BehaviorService.java
rm -f backend/src/main/java/com/easyluxury/ai/service/BehaviorTrackingService.java

# Remove retention service (will be replaced in ai-behavior)
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/behavior/BehaviorRetentionService.java
```

#### 4. Old Controllers

```bash
# Remove behavior controllers
rm -f ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/controller/BehaviorController.java
rm -f backend/src/main/java/com/easyluxury/ai/controller/BehavioralAIController.java
```

#### 5. Old Adapters

```bash
# Remove adapters bridging old systems
rm -f backend/src/main/java/com/easyluxury/ai/adapter/UserBehaviorAdapter.java
rm -f backend/src/main/java/com/easyluxury/ai/adapter/ProductAIAdapter.java  # If behavior-specific
rm -f backend/src/main/java/com/easyluxury/ai/adapter/OrderAIAdapter.java    # If behavior-specific
```

#### 6. Old Tests

```bash
# Remove old behavior tests
rm -rf ai-infrastructure-module/integration-tests/src/test/java/com/ai/infrastructure/it/Behavior*
rm -f ai-infrastructure-module/ai-infrastructure-core/src/test/java/com/ai/infrastructure/behavior/BehaviorRetentionServiceTest.java
```

#### 7. Old Configuration

Remove behavior-related configuration from auto-configuration:

```bash
# Edit this file to remove behavior beans
vi ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java
```

Remove these bean definitions:
```java
// REMOVE these from AIInfrastructureAutoConfiguration:
@Bean
public BehaviorRetentionService behaviorRetentionService(...)

@Bean
public com.ai.infrastructure.service.BehaviorService behaviorService(...)
```

---

### Files to Update

#### 1. RecommendationEngine

**File:** `backend/src/main/java/com/easyluxury/ai/service/RecommendationEngine.java`

**Changes:**
```java
// REMOVE old imports
import com.easyluxury.repository.UserBehaviorRepository;
import com.easyluxury.entity.UserBehavior;

// ADD new imports
import com.ai.behavior.service.BehaviorInsightsService;
import com.ai.behavior.service.BehaviorQueryService;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.model.BehaviorQuery;

@Service
@RequiredArgsConstructor
public class RecommendationEngine {
    
    // REMOVE
    // private final UserBehaviorRepository userBehaviorRepository;
    
    // ADD
    private final BehaviorInsightsService insightsService;
    private final BehaviorQueryService queryService;
    
    public List<Product> generateProductRecommendations(UUID userId, int limit, ...) {
        // OLD CODE (remove):
        // List<UserBehavior> behaviors = userBehaviorRepository
        //     .findByUserIdOrderByCreatedAtDesc(userId)
        //     .stream()
        //     .limit(200)
        //     .toList();
        
        // NEW CODE:
        // Get pre-computed insights (fast!)
        BehaviorInsights insights = insightsService.getUserInsights(userId);
        
        if (insights == null || !insights.isValid()) {
            return getDefaultRecommendations(limit);
        }
        
        // Use insights for recommendations
        List<String> preferredCategories = (List<String>) 
            insights.getPreferences().get("preferred_categories");
        
        return productRepository.findByPreferences(
            preferredCategories,
            limit
        );
    }
}
```

#### 2. UIAdaptationService

**File:** `backend/src/main/java/com/easyluxury/ai/service/UIAdaptationService.java`

**Changes:**
```java
// REMOVE old imports
import com.easyluxury.repository.UserBehaviorRepository;

// ADD new imports
import com.ai.behavior.service.BehaviorInsightsService;

@Service
@RequiredArgsConstructor
public class UIAdaptationService {
    
    // REMOVE
    // private final UserBehaviorRepository userBehaviorRepository;
    
    // ADD
    private final BehaviorInsightsService insightsService;
    
    public Map<String, Object> generatePersonalizedUIConfig(UUID userId) {
        // OLD CODE (remove):
        // List<UserBehavior> behaviors = userBehaviorRepository
        //     .findByUserIdOrderByCreatedAtDesc(userId);
        
        // NEW CODE:
        BehaviorInsights insights = insightsService.getUserInsights(userId);
        
        return Map.of(
            "theme", insights.getPreferences().get("preferred_theme"),
            "layout", insights.getPreferences().get("preferred_layout"),
            "features", insights.getRecommendations()
        );
    }
}
```

#### 3. UserDataDeletionService

**File:** `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/deletion/UserDataDeletionService.java`

**Changes:**
```java
// REMOVE old imports
import com.ai.infrastructure.repository.BehaviorRepository;

// ADD new imports
import com.ai.behavior.service.BehaviorDeletionService;

@Service
@RequiredArgsConstructor
public class UserDataDeletionService {
    
    // REMOVE
    // private final BehaviorRepository behaviorRepository;
    
    // ADD
    private final BehaviorDeletionService behaviorDeletionService;
    
    @Transactional
    public UserDataDeletionResult deleteUserData(String userId) {
        UUID userUuid = UUID.fromString(userId);
        
        // OLD CODE (remove):
        // List<Behavior> behaviors = behaviorRepository
        //     .findByUserIdOrderByCreatedAtDesc(userUuid);
        // behaviorRepository.deleteAllInBatch(behaviors);
        
        // NEW CODE:
        behaviorDeletionService.deleteUserBehaviors(userUuid);
        
        // ... rest of deletion logic
    }
}
```

---

### Database Cleanup

#### Drop Old Tables

```sql
-- Drop old behavior tables
DROP TABLE IF EXISTS behaviors CASCADE;
DROP TABLE IF EXISTS user_behaviors CASCADE;

-- Drop old indexes (if they weren't cascade deleted)
DROP INDEX IF EXISTS idx_behavior_user_id;
DROP INDEX IF EXISTS idx_user_behavior_user_id;
```

#### Remove Old Migrations

```bash
# Archive old migration files (don't delete, for reference)
mkdir -p ai-infrastructure-module/ai-infrastructure-core/src/main/resources/db/migration/archived
mv ai-infrastructure-module/ai-infrastructure-core/src/main/resources/db/migration/*Behavior*.sql \
   ai-infrastructure-module/ai-infrastructure-core/src/main/resources/db/migration/archived/
```

---

### Dependency Cleanup

#### Update pom.xml Files

Remove any behavior-specific test dependencies:

```xml
<!-- REMOVE from backend/pom.xml if present -->
<!-- Old behavior test utils, mocks, etc. -->
```

---

### Configuration Cleanup

#### application.yml

Remove old behavior configuration:

```yaml
# REMOVE these sections from application.yml

# Old behavior tracking config (remove)
# ai:
#   infrastructure:
#     behavior:
#       retention-days: 90
#       auto-cleanup: true
```

---

### Test Configuration Cleanup

**File:** `backend/src/test/java/com/easyluxury/ai/config/TestAIConfiguration.java`

```java
// REMOVE old behavior mocks
@Bean
public BehaviorRepository behaviorRepository() {
    return Mockito.mock(BehaviorRepository.class);
}

@Bean
public BehaviorService behaviorService(...) {
    return new BehaviorService(...);
}
```

---

### Cleanup Verification Script

Create a script to verify cleanup:

```bash
#!/bin/bash
# cleanup-verification.sh

echo "Verifying behavior code cleanup..."

# Check for old entity references
echo "Checking for old Behavior entity references..."
grep -r "import.*\.entity\.Behavior" . --include="*.java" && echo "❌ Found old Behavior imports" || echo "✅ No old Behavior imports"

grep -r "import.*entity\.UserBehavior" . --include="*.java" && echo "❌ Found old UserBehavior imports" || echo "✅ No old UserBehavior imports"

# Check for old repository references
echo "Checking for old repository references..."
grep -r "BehaviorRepository" . --include="*.java" --exclude-dir=ai-behavior-module && echo "❌ Found old BehaviorRepository" || echo "✅ No old BehaviorRepository"

grep -r "UserBehaviorRepository" . --include="*.java" --exclude-dir=ai-behavior-module && echo "❌ Found old UserBehaviorRepository" || echo "✅ No old UserBehaviorRepository"

# Check for old service references
echo "Checking for old service references..."
grep -r "BehaviorService" . --include="*.java" --exclude-dir=ai-behavior-module && echo "❌ Found old BehaviorService" || echo "✅ No old BehaviorService"

grep -r "BehaviorTrackingService" . --include="*.java" --exclude-dir=ai-behavior-module && echo "❌ Found old BehaviorTrackingService" || echo "✅ No old BehaviorTrackingService"

# Check database
echo "Checking database for old tables..."
psql -U postgres -d your_database -c "SELECT tablename FROM pg_tables WHERE tablename IN ('behaviors', 'user_behaviors');" | grep -q "0 rows" && echo "✅ Old tables removed" || echo "❌ Old tables still exist"

echo "Cleanup verification complete!"
```

---

### Summary Checklist

- [ ] Remove old Behavior and UserBehavior entities
- [ ] Remove old repositories (BehaviorRepository, UserBehaviorRepository)
- [ ] Remove old services (BehaviorService, BehaviorTrackingService)
- [ ] Remove old controllers (BehaviorController, BehavioralAIController)
- [ ] Remove old adapters (UserBehaviorAdapter, etc.)
- [ ] Remove old tests
- [ ] Update RecommendationEngine to use new ai-behavior
- [ ] Update UIAdaptationService to use new ai-behavior
- [ ] Update UserDataDeletionService to use new ai-behavior
- [ ] Drop old database tables (behaviors, user_behaviors)
- [ ] Archive old migration files
- [ ] Clean up configuration files
- [ ] Clean up test configurations
- [ ] Run cleanup verification script
- [ ] Verify application compiles
- [ ] Run all tests to ensure nothing broken

---

## AI-Core Integration

### Overview

The `ai-behavior` module **uses** `ai-core` for AI capabilities but maintains clear separation. `ai-core` provides generic AI primitives (embeddings, search, pattern detection), while `ai-behavior` applies them to behavior tracking domain.

### Integration Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    AI-BEHAVIOR MODULE                       │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Domain-Specific Behavior Logic                      │ │
│  │  - BehaviorSignal models                              │ │
│  │  - BehaviorInsights                                  │ │
│  │  - Behavior-specific analysis                        │ │
│  └──────────────────────────────────────────────────────┘ │
│                          │                                  │
│                          │ Uses (via interfaces)            │
│                          ↓                                  │
│  ┌──────────────────────────────────────────────────────┐ │
│  │  Integration Layer                                   │ │
│  │  - BehaviorEmbeddingService (wraps AICoreService)   │ │
│  │  - BehaviorSearchService (wraps AISearchService)    │ │
│  │  - PatternAnalyzer (uses ai-core primitives)        │ │
│  └──────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
                          │
                          │ Calls
                          ↓
┌────────────────────────────────────────────────────────────┐
│                     AI-CORE MODULE                          │
│                (Generic AI Primitives)                      │
│                                                             │
│  - AICoreService (embeddings)                              │
│  - AISearchService (vector search)                         │
│  - RAGService (retrieval augmented generation)             │
│  - AIAnalysisService (generic pattern detection)           │
│  - AICapabilityService (entity processing)                 │
│                                                             │
│  Does NOT know about: behaviors, users, products          │
└────────────────────────────────────────────────────────────┘
```

### Key Principle: Dependency Direction

```
ai-behavior → ai-core  ✅ (ai-behavior depends on ai-core)
ai-core → ai-behavior  ❌ (ai-core never knows about ai-behavior)
```

---

### Integration Points

#### 1. Embedding Generation

**Scenario:** Generate embeddings for text content (feedback, reviews, search queries)

```java
package com.ai.behavior.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorEmbedding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * BehaviorEmbeddingService
 * 
 * Wraps ai-core embedding capabilities for behavior-specific use.
 * Knows WHEN to embed (selective), ai-core knows HOW to embed.
 */
@Service
@RequiredArgsConstructor
public class BehaviorEmbeddingService {
    
    private final AICoreService aiCoreService;  // From ai-core
    private final BehaviorEmbeddingRepository embeddingRepository;
    
    /**
     * Generate embedding for behavior event (if applicable)
     * 
     * ai-behavior decides: Should we embed this event?
     * ai-core provides: Embedding generation capability
     */
    public BehaviorEmbedding generateEmbedding(BehaviorSignal event) {
        // ai-behavior logic: check if embedding needed
        if (!shouldEmbed(event)) {
            return null;
        }
        
        String text = extractText(event);
        if (text == null || text.length() < 10) {
            return null;
        }
        
        // ai-core capability: generate embedding
        float[] embedding = aiCoreService.generateEmbedding(text);
        
        // ai-behavior logic: store with behavior context
        BehaviorEmbedding behaviorEmbedding = BehaviorEmbedding.builder()
            .behaviorEventId(event.getId())
            .embeddingType(event.getEventType())
            .originalText(text)
            .embedding(embedding)
            .model("text-embedding-3-small")
            .build();
        
        return embeddingRepository.save(behaviorEmbedding);
    }
    
    /**
     * ai-behavior decision logic
     */
    private boolean shouldEmbed(BehaviorSignal event) {
        // Only embed text-heavy events
        return List.of("feedback", "review", "search").contains(event.getEventType());
    }
    
    private String extractText(BehaviorSignal event) {
        if (event.getMetadata() == null) {
            return null;
        }
        
        // Extract text based on event type (behavior domain knowledge)
        switch (event.getEventType()) {
            case "feedback":
                return (String) event.getMetadata().get("feedback_text");
            case "review":
                return (String) event.getMetadata().get("review_text");
            case "search":
                return (String) event.getMetadata().get("query");
            default:
                return null;
        }
    }
}
```

---

#### 2. Semantic Search

**Scenario:** Find similar feedback/reviews using vector search

```java
package com.ai.behavior.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.rag.AISearchService;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorEmbedding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BehaviorSearchService
 * 
 * Wraps ai-core search capabilities for behavior-specific searches.
 */
@Service
@RequiredArgsConstructor
public class BehaviorSearchService {
    
    private final AICoreService aiCoreService;           // For embedding query
    private final AISearchService aiSearchService;        // For vector search
    private final BehaviorEmbeddingRepository embeddingRepo;
    private final BehaviorSignalRepository eventRepo;
    
    /**
     * Find similar feedback to given text
     * 
     * Example: User submits feedback "Checkout is confusing"
     * Find all similar past feedback to identify patterns
     */
    public List<BehaviorSignal> findSimilarFeedback(String feedbackText, int limit) {
        // Step 1: Generate embedding for search query (ai-core)
        float[] queryEmbedding = aiCoreService.generateEmbedding(feedbackText);
        
        // Step 2: Search for similar embeddings (ai-core)
        // Note: We'd need to integrate with vector database here
        // This is simplified - actual implementation depends on vector DB
        List<UUID> similarEmbeddingIds = aiSearchService.findSimilarVectors(
            queryEmbedding,
            "behavior_embeddings",
            limit
        );
        
        // Step 3: Get behavior events (ai-behavior domain)
        List<BehaviorEmbedding> embeddings = embeddingRepo.findAllById(similarEmbeddingIds);
        
        List<UUID> eventIds = embeddings.stream()
            .map(BehaviorEmbedding::getBehaviorSignalId)
            .collect(Collectors.toList());
        
        return eventRepo.findAllById(eventIds);
    }
    
    /**
     * Find users with similar search patterns
     */
    public List<UUID> findUsersWithSimilarSearches(UUID userId, int limit) {
        // Get user's search queries
        List<BehaviorSignal> userSearches = eventRepo.findByUserIdAndEventType(
            userId,
            "search"
        );
        
        if (userSearches.isEmpty()) {
            return List.of();
        }
        
        // Get embeddings for user's searches
        List<BehaviorEmbedding> userSearchEmbeddings = embeddingRepo
            .findByBehaviorSignalIdIn(
                userSearches.stream().map(BehaviorSignal::getId).collect(Collectors.toList())
            );
        
        // Calculate average embedding (user's search profile)
        float[] avgEmbedding = calculateAverageEmbedding(userSearchEmbeddings);
        
        // Find similar search patterns (ai-core)
        List<UUID> similarEmbeddingIds = aiSearchService.findSimilarVectors(
            avgEmbedding,
            "behavior_embeddings",
            limit
        );
        
        // Get users from those searches
        List<BehaviorEmbedding> similarEmbeddings = embeddingRepo.findAllById(similarEmbeddingIds);
        List<UUID> eventIds = similarEmbeddings.stream()
            .map(BehaviorEmbedding::getBehaviorSignalId)
            .collect(Collectors.toList());
        
        List<BehaviorSignal> events = eventRepo.findAllById(eventIds);
        
        return events.stream()
            .map(BehaviorSignal::getUserId)
            .distinct()
            .filter(id -> !id.equals(userId))  // Exclude original user
            .collect(Collectors.toList());
    }
    
    private float[] calculateAverageEmbedding(List<BehaviorEmbedding> embeddings) {
        // Simple average - could use more sophisticated methods
        if (embeddings.isEmpty()) {
            return new float[1536];  // text-embedding-3-small dimension
        }
        
        float[] sum = new float[1536];
        for (BehaviorEmbedding emb : embeddings) {
            float[] vec = emb.getEmbedding();
            for (int i = 0; i < vec.length; i++) {
                sum[i] += vec[i];
            }
        }
        
        for (int i = 0; i < sum.length; i++) {
            sum[i] /= embeddings.size();
        }
        
        return sum;
    }
}
```

---

#### 3. Pattern Detection

**Scenario:** Detect behavioral patterns using ai-core's analysis capabilities

```java
package com.ai.behavior.processing.analyzer;

import com.ai.infrastructure.analysis.AIAnalysisService;
import com.ai.infrastructure.dto.AnalysisRequest;
import com.ai.infrastructure.dto.AnalysisResult;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorInsights;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * PatternAnalyzer
 * 
 * Analyzes behavior patterns using ai-core's pattern detection.
 * ai-behavior provides domain context, ai-core provides pattern detection algorithms.
 */
@Component
@RequiredArgsConstructor
public class PatternAnalyzer implements BehaviorAnalyzer {
    
    private final AIAnalysisService aiAnalysisService;  // From ai-core
    
    @Override
    public BehaviorInsights analyze(UUID userId, List<BehaviorSignal> events) {
        if (events.isEmpty()) {
            return null;
        }
        
        // Step 1: Convert behavior events to time-series data (ai-behavior domain)
        List<TimeSeriesDataPoint> timeSeries = convertToTimeSeries(events);
        
        // Step 2: Detect patterns using ai-core (generic pattern detection)
        AnalysisRequest request = AnalysisRequest.builder()
            .dataPoints(timeSeries)
            .analysisType("pattern_detection")
            .parameters(Map.of(
                "window_size", 24,
                "min_support", 0.3
            ))
            .build();
        
        AnalysisResult result = aiAnalysisService.analyze(request);
        
        // Step 3: Interpret patterns in behavior context (ai-behavior domain)
        List<String> patterns = interpretPatterns(result.getPatterns(), events);
        
        // Step 4: Calculate behavior-specific scores (ai-behavior domain)
        Map<String, Double> scores = calculateBehaviorScores(events, patterns);
        
        // Step 5: Detect preferences (ai-behavior domain)
        Map<String, Object> preferences = detectPreferences(events);
        
        // Step 6: Generate recommendations (ai-behavior domain)
        List<String> recommendations = generateRecommendations(patterns, scores);
        
        // Step 7: Determine segment (ai-behavior domain)
        String segment = determineSegment(scores, patterns);
        
        return BehaviorInsights.builder()
            .userId(userId)
            .patterns(patterns)
            .scores(scores)
            .segment(segment)
            .preferences(preferences)
            .recommendations(recommendations)
            .analyzedAt(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusMinutes(5))
            .analysisVersion("1.0")
            .build();
    }
    
    /**
     * Convert behavior events to generic time-series format (ai-core compatible)
     */
    private List<TimeSeriesDataPoint> convertToTimeSeries(List<BehaviorSignal> events) {
        return events.stream()
            .map(event -> TimeSeriesDataPoint.builder()
                .timestamp(event.getTimestamp())
                .value(getEventWeight(event.getEventType()))
                .category(event.getEventType())
                .metadata(event.getMetadata())
                .build())
            .collect(Collectors.toList());
    }
    
    /**
     * Behavior domain knowledge: event importance
     */
    private double getEventWeight(String eventType) {
        switch (eventType) {
            case "purchase": return 10.0;
            case "add_to_cart": return 5.0;
            case "view": return 1.0;
            case "search": return 2.0;
            case "feedback": return 3.0;
            default: return 1.0;
        }
    }
    
    /**
     * Interpret generic patterns in behavior domain context
     */
    private List<String> interpretPatterns(List<String> genericPatterns, List<BehaviorSignal> events) {
        List<String> behaviorPatterns = new ArrayList<>();
        
        // Map generic patterns to behavior patterns
        for (String pattern : genericPatterns) {
            if (pattern.contains("high_frequency")) {
                // Check what's frequent
                Map<String, Long> eventCounts = events.stream()
                    .collect(Collectors.groupingBy(
                        BehaviorSignal::getEventType,
                        Collectors.counting()
                    ));
                
                if (eventCounts.getOrDefault("purchase", 0L) > 5) {
                    behaviorPatterns.add("frequent_buyer");
                }
                if (eventCounts.getOrDefault("view", 0L) > 50) {
                    behaviorPatterns.add("heavy_browser");
                }
            }
            
            if (pattern.contains("time_clustering")) {
                // Detect time-based patterns
                Map<Integer, Long> hourCounts = events.stream()
                    .collect(Collectors.groupingBy(
                        e -> e.getTimestamp().getHour(),
                        Collectors.counting()
                    ));
                
                long eveningCount = hourCounts.entrySet().stream()
                    .filter(e -> e.getKey() >= 18 && e.getKey() <= 23)
                    .mapToLong(Map.Entry::getValue)
                    .sum();
                
                if (eveningCount > events.size() * 0.6) {
                    behaviorPatterns.add("evening_shopper");
                }
            }
        }
        
        // Add domain-specific patterns
        detectCartAbandonment(events).ifPresent(behaviorPatterns::add);
        detectPriceChecker(events).ifPresent(behaviorPatterns::add);
        
        return behaviorPatterns;
    }
    
    /**
     * Behavior-specific pattern: cart abandonment
     */
    private Optional<String> detectCartAbandonment(List<BehaviorSignal> events) {
        long addToCartCount = events.stream()
            .filter(e -> e.getEventType().equals("add_to_cart"))
            .count();
        
        long purchaseCount = events.stream()
            .filter(e -> e.getEventType().equals("purchase"))
            .count();
        
        if (addToCartCount > 3 && purchaseCount == 0) {
            return Optional.of("cart_abandoner");
        }
        
        return Optional.empty();
    }
    
    /**
     * Behavior-specific pattern: price checker
     */
    private Optional<String> detectPriceChecker(List<BehaviorSignal> events) {
        // User views same product multiple times
        Map<String, Long> entityViews = events.stream()
            .filter(e -> e.getEventType().equals("view"))
            .filter(e -> "product".equals(e.getEntityType()))
            .collect(Collectors.groupingBy(
                BehaviorSignal::getEntityId,
                Collectors.counting()
            ));
        
        boolean multipleViews = entityViews.values().stream()
            .anyMatch(count -> count >= 3);
        
        if (multipleViews) {
            return Optional.of("price_checker");
        }
        
        return Optional.empty();
    }
    
    /**
     * Calculate behavior-specific scores
     */
    private Map<String, Double> calculateBehaviorScores(
            List<BehaviorSignal> events,
            List<String> patterns) {
        
        Map<String, Double> scores = new HashMap<>();
        
        // Engagement score (based on event frequency and diversity)
        double engagementScore = calculateEngagementScore(events);
        scores.put("engagement_score", engagementScore);
        
        // Conversion probability (based on patterns)
        double conversionProb = calculateConversionProbability(events, patterns);
        scores.put("conversion_probability", conversionProb);
        
        // Churn risk (based on activity decline)
        double churnRisk = calculateChurnRisk(events);
        scores.put("churn_risk", churnRisk);
        
        return scores;
    }
    
    private double calculateEngagementScore(List<BehaviorSignal> events) {
        // Frequency component
        long daysSinceFirst = ChronoUnit.DAYS.between(
            events.get(events.size() - 1).getTimestamp(),
            events.get(0).getTimestamp()
        );
        double frequency = events.size() / Math.max(1.0, daysSinceFirst);
        
        // Diversity component
        long uniqueEventTypes = events.stream()
            .map(BehaviorSignal::getEventType)
            .distinct()
            .count();
        double diversity = uniqueEventTypes / 10.0;  // Max 10 event types
        
        // Weighted average
        return Math.min(1.0, (frequency * 0.6 + diversity * 0.4));
    }
    
    private double calculateConversionProbability(
            List<BehaviorSignal> events,
            List<String> patterns) {
        
        double baseProbability = 0.1;  // 10% base
        
        // Increase based on patterns
        if (patterns.contains("frequent_buyer")) {
            baseProbability += 0.5;
        }
        if (patterns.contains("cart_abandoner")) {
            baseProbability -= 0.2;
        }
        if (patterns.contains("price_checker")) {
            baseProbability += 0.2;
        }
        
        // Increase based on recent activity
        long recentViews = events.stream()
            .filter(e -> e.getTimestamp().isAfter(LocalDateTime.now().minusDays(7)))
            .filter(e -> e.getEventType().equals("view"))
            .count();
        
        if (recentViews > 10) {
            baseProbability += 0.2;
        }
        
        return Math.min(1.0, Math.max(0.0, baseProbability));
    }
    
    private double calculateChurnRisk(List<BehaviorSignal> events) {
        // Check activity decline
        LocalDateTime now = LocalDateTime.now();
        
        long lastWeekEvents = events.stream()
            .filter(e -> e.getTimestamp().isAfter(now.minusWeeks(1)))
            .count();
        
        long previousWeekEvents = events.stream()
            .filter(e -> e.getTimestamp().isBefore(now.minusWeeks(1)))
            .filter(e -> e.getTimestamp().isAfter(now.minusWeeks(2)))
            .count();
        
        if (previousWeekEvents == 0) {
            return 0.0;  // Not enough data
        }
        
        double activityRatio = (double) lastWeekEvents / previousWeekEvents;
        
        if (activityRatio < 0.3) {
            return 0.8;  // High churn risk
        } else if (activityRatio < 0.7) {
            return 0.5;  // Medium churn risk
        } else {
            return 0.2;  // Low churn risk
        }
    }
    
    /**
     * Detect user preferences from behavior
     */
    private Map<String, Object> detectPreferences(List<BehaviorSignal> events) {
        Map<String, Object> preferences = new HashMap<>();
        
        // Preferred categories
        Map<String, Long> categories = events.stream()
            .filter(e -> e.getMetadata() != null)
            .filter(e -> e.getMetadata().containsKey("category"))
            .collect(Collectors.groupingBy(
                e -> (String) e.getMetadata().get("category"),
                Collectors.counting()
            ));
        
        List<String> topCategories = categories.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(3)
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
        
        preferences.put("preferred_categories", topCategories);
        
        // Price range
        List<Double> prices = events.stream()
            .filter(e -> e.getMetadata() != null)
            .filter(e -> e.getMetadata().containsKey("price"))
            .map(e -> ((Number) e.getMetadata().get("price")).doubleValue())
            .collect(Collectors.toList());
        
        if (!prices.isEmpty()) {
            double avgPrice = prices.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
            
            String priceRange;
            if (avgPrice > 1000) {
                priceRange = "luxury";
            } else if (avgPrice > 500) {
                priceRange = "premium";
            } else if (avgPrice > 100) {
                priceRange = "mid_range";
            } else {
                priceRange = "budget";
            }
            
            preferences.put("price_range", priceRange);
        }
        
        return preferences;
    }
    
    /**
     * Generate recommendations based on analysis
     */
    private List<String> generateRecommendations(
            List<String> patterns,
            Map<String, Double> scores) {
        
        List<String> recommendations = new ArrayList<>();
        
        if (patterns.contains("cart_abandoner")) {
            recommendations.add("send_cart_reminder");
            recommendations.add("offer_discount");
        }
        
        if (patterns.contains("frequent_buyer")) {
            recommendations.add("offer_vip_upgrade");
            recommendations.add("exclusive_early_access");
        }
        
        if (scores.get("churn_risk") > 0.7) {
            recommendations.add("re_engagement_campaign");
            recommendations.add("win_back_offer");
        }
        
        if (scores.get("engagement_score") > 0.8) {
            recommendations.add("request_review");
            recommendations.add("referral_program");
        }
        
        return recommendations;
    }
    
    /**
     * Determine user segment
     */
    private String determineSegment(Map<String, Double> scores, List<String> patterns) {
        if (patterns.contains("frequent_buyer") && scores.get("engagement_score") > 0.7) {
            return "VIP";
        }
        
        if (scores.get("churn_risk") > 0.7) {
            return "at_risk";
        }
        
        if (scores.get("engagement_score") < 0.3) {
            return "dormant";
        }
        
        if (patterns.contains("cart_abandoner")) {
            return "needs_nurturing";
        }
        
        return "active";
    }
    
    @Override
    public String getAnalyzerType() {
        return "pattern_detection";
    }
    
    @Override
    public boolean supports(List<String> eventTypes) {
        // Supports all event types
        return true;
    }
}
```

---

#### 4. RAG Integration

**Scenario:** Use behavior data in RAG context for AI-powered insights

```java
package com.ai.behavior.service;

import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorInsights;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BehaviorRAGService
 * 
 * Uses RAG (Retrieval Augmented Generation) to provide AI-powered
 * behavioral insights and recommendations.
 */
@Service
@RequiredArgsConstructor
public class BehaviorRAGService {
    
    private final RAGService ragService;  // From ai-core
    private final BehaviorDataProvider behaviorProvider;
    private final BehaviorInsightsService insightsService;
    
    /**
     * Get AI-powered explanation of user behavior
     * 
     * Example: "Why did this user abandon their cart?"
     */
    public String explainUserBehavior(UUID userId, String question) {
        // Get user behavior context
        List<BehaviorSignal> events = behaviorProvider.getRecentEvents(userId, 100);
        BehaviorInsights insights = insightsService.getUserInsights(userId);
        
        // Build context for RAG
        String context = buildBehaviorContext(events, insights);
        
        // Use ai-core RAG to generate explanation
        RAGRequest request = RAGRequest.builder()
            .query(question)
            .context(context)
            .maxTokens(500)
            .temperature(0.7)
            .build();
        
        RAGResponse response = ragService.generate(request);
        
        return response.getGeneratedText();
    }
    
    /**
     * Get personalized recommendations using RAG
     */
    public String getPersonalizedRecommendations(UUID userId) {
        List<BehaviorSignal> events = behaviorProvider.getRecentEvents(userId, 100);
        BehaviorInsights insights = insightsService.getUserInsights(userId);
        
        String context = buildBehaviorContext(events, insights);
        
        RAGRequest request = RAGRequest.builder()
            .query("Based on this user's behavior, what products should we recommend and why?")
            .context(context)
            .maxTokens(300)
            .temperature(0.7)
            .build();
        
        RAGResponse response = ragService.generate(request);
        
        return response.getGeneratedText();
    }
    
    /**
     * Build behavior context for RAG
     */
    private String buildBehaviorContext(List<BehaviorSignal> events, BehaviorInsights insights) {
        StringBuilder context = new StringBuilder();
        
        // Add user segment
        context.append("User Segment: ").append(insights.getSegment()).append("\n");
        
        // Add patterns
        context.append("Detected Patterns: ")
            .append(String.join(", ", insights.getPatterns()))
            .append("\n");
        
        // Add scores
        context.append("Engagement Score: ")
            .append(String.format("%.2f", insights.getScore("engagement_score")))
            .append("\n");
        context.append("Conversion Probability: ")
            .append(String.format("%.2f", insights.getScore("conversion_probability")))
            .append("\n");
        
        // Add preferences
        context.append("Preferred Categories: ")
            .append(insights.getPreferences().get("preferred_categories"))
            .append("\n");
        context.append("Price Range: ")
            .append(insights.getPreferences().get("price_range"))
            .append("\n");
        
        // Add recent behavior summary
        Map<String, Long> eventCounts = events.stream()
            .collect(Collectors.groupingBy(
                BehaviorSignal::getEventType,
                Collectors.counting()
            ));
        
        context.append("\nRecent Activity (last 100 events):\n");
        eventCounts.forEach((type, count) -> 
            context.append("- ").append(type).append(": ").append(count).append("\n")
        );
        
        return context.toString();
    }
}
```

---

### Integration Configuration

**pom.xml for ai-behavior module:**

```xml
<dependencies>
    <!-- AI-Core dependency -->
    <dependency>
        <groupId>com.ai.infrastructure</groupId>
        <artifactId>ai-infrastructure-core</artifactId>
        <version>${ai-core.version}</version>
    </dependency>
    
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- Other dependencies -->
</dependencies>
```

**Spring Configuration:**

```java
package com.ai.behavior.config;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.analysis.AIAnalysisService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Import ai-core services into ai-behavior context
 */
@Configuration
@Import({
    AICoreService.class,
    RAGService.class,
    AIAnalysisService.class
})
public class AIBehaviorConfiguration {
    // ai-core services now available for injection
}
```

---

### Summary: Clear Separation

| Responsibility | ai-behavior | ai-core |
|----------------|-------------|---------|
| **Domain Knowledge** | Behavior events, patterns, insights | Generic AI operations |
| **When to use AI** | Decides which events need embedding | Doesn't decide |
| **How to use AI** | Calls ai-core services | Provides services |
| **Pattern Interpretation** | Maps generic patterns to behavior context | Detects generic patterns |
| **Scoring** | Behavior-specific scores (engagement, churn) | Generic analysis scores |
| **Storage** | Behavior events, embeddings, insights | AI operation logs only |
| **API** | Behavior-specific endpoints | Generic AI endpoints |

**Key principle:** ai-behavior adds **domain intelligence** on top of ai-core's **generic capabilities**.

---

## Implementation Roadmap

### Phase 1: Core Module Setup (Week 1)

**Goals:**
- Create ai-behavior module structure
- Define core interfaces
- Implement basic models
- Database schema creation

**Tasks:**
```bash
# 1. Create module structure
mkdir -p ai-behavior-module/src/main/java/com/ai/behavior
mkdir -p ai-behavior-module/src/main/resources/db/migration
mkdir -p ai-behavior-module/src/test/java/com/ai/behavior

# 2. Create pom.xml with dependencies
# 3. Define core interfaces
# 4. Implement data models
# 5. Create database migrations
# 6. Add unit tests
```

**Key Deliverables:**
- ✅ Module structure created
- ✅ Core interfaces defined (BehaviorSignalSink, BehaviorDataProvider, BehaviorAnalyzer)
- ✅ Data models implemented (BehaviorSignal, BehaviorInsights, BehaviorMetrics, BehaviorEmbedding)
- ✅ Database schema created with proper indexes and partitioning
- ✅ Unit tests for models and interfaces

**Database Setup:**
```sql
-- Create main tables with partitioning
CREATE TABLE behavior_events (...) PARTITION BY RANGE (timestamp);
CREATE TABLE behavior_insights (...);
CREATE TABLE behavior_metrics (...);
CREATE TABLE behavior_embeddings (...);

-- Create indexes
CREATE INDEX idx_behavior_user_time ON behavior_events(user_id, timestamp DESC);
CREATE INDEX idx_behavior_entity ON behavior_events(entity_type, entity_id);

-- Create initial partitions (3 months)
CREATE TABLE behavior_events_2025_11 PARTITION OF behavior_events ...
CREATE TABLE behavior_events_2025_12 PARTITION OF behavior_events ...
CREATE TABLE behavior_events_2026_01 PARTITION OF behavior_events ...
```

---

### Phase 2: Storage Layer (Week 2)

**Goals:**
- Implement default storage (database sink)
- Implement query layer
- Add repository implementations

**Key Deliverables:**
- ✅ DatabaseEventSink implemented and tested
- ✅ BehaviorSignalRepository with custom queries
- ✅ BehaviorInsightsRepository, BehaviorMetricsRepository, BehaviorEmbeddingRepository
- ✅ DatabaseBehaviorProvider implemented
- ✅ Integration tests for storage layer
- ✅ Can successfully write and read events

**Implementation Focus:**
- Efficient batch inserts
- Optimized queries with proper index usage
- Transaction management
- Error handling and retry logic

---

### Phase 3: Ingestion Layer (Week 3)

**Goals:**
- Implement ingestion service
- Build REST API
- Create validation layer
- Event enrichment

**Key Deliverables:**
- ✅ BehaviorIngestionService implemented
- ✅ BehaviorSignalValidator with validation rules
- ✅ REST API controllers (ingestion, query, insights)
- ✅ Event publishing to application event bus
- ✅ API documentation (OpenAPI/Swagger)
- ✅ Integration tests for API endpoints

**API Endpoints:**
```
POST   /api/ai-behavior/signals        # Single event
POST   /api/ai-behavior/ingest/batch        # Batch events (up to 1000)
GET    /api/ai-behavior/events/{id}         # Get specific event
GET    /api/ai-behavior/users/{id}/events   # Get user events (paginated)
GET    /api/ai-behavior/users/{id}/insights # Get user insights
GET    /api/ai-behavior/users/{id}/metrics  # Get user metrics
GET    /api/ai-behavior/health              # Health check
```

---

### Phase 4: Client SDKs (Week 4)

**Goals:**
- JavaScript/TypeScript SDK for web
- Java/Kotlin SDK for mobile
- Python SDK for backend services
- SDKs handle batching, retry, offline queue

**Key Deliverables:**
- ✅ JavaScript SDK with auto-batching
- ✅ Android/Java SDK with auto-batching
- ✅ Python SDK for backend integration
- ✅ SDK documentation and examples
- ✅ Published to package registries (npm, Maven Central, PyPI)

**Features:**
- Automatic event batching
- Offline queue support
- Retry with exponential backoff
- Automatic metadata enrichment
- Type-safe APIs

---

### Phase 5: Async Processing Workers (Week 5)

**Goals:**
- Real-time metrics aggregation
- Pattern detection
- Selective embedding generation
- Anomaly detection

**Key Deliverables:**
- ✅ MetricProjectionWorker (updates metrics on every event)
- ✅ PatternDetectionWorker (scheduled every 5 minutes)
- ✅ EmbeddingGenerationWorker (selective, async)
- ✅ AnomalyDetectionWorker (fraud detection, scheduled)
- ✅ Worker monitoring and error handling
- ✅ All workers tested with load testing

**Worker Configuration:**
```yaml
processing:
  aggregation:
    enabled: true
    async: true
  
  pattern-detection:
    enabled: true
    schedule: "*/5 * * * *"  # Every 5 minutes
  
  embedding:
    enabled: true
    async: true
    event-types: [feedback, review, search]
  
  anomaly-detection:
    enabled: true
    schedule: "* * * * *"  # Every minute
```

---

### Phase 6: Analysis & Insights (Week 6)

**Goals:**
- Implement pattern analyzers
- Build insights service
- Query service for events
- Caching layer

**Key Deliverables:**
- ✅ PatternAnalyzer using ai-core primitives
- ✅ BehaviorInsightsService with caching
- ✅ BehaviorQueryService for flexible queries
- ✅ Redis caching for insights
- ✅ Integration with ai-core for pattern detection
- ✅ Performance optimized (<10ms for insights)

**Analysis Capabilities:**
- Pattern detection (frequent buyer, cart abandoner, etc.)
- User segmentation (VIP, at-risk, new, dormant)
- Churn prediction
- Conversion probability
- Preference detection

---

### Phase 7: Alternative Storage Backends (Week 7)

**Goals:**
- Implement Kafka sink
- Implement Redis sink
- Implement hybrid sink (hot/cold)
- Storage selection via configuration

**Key Deliverables:**
- ✅ KafkaEventSink for high-throughput scenarios
- ✅ RedisEventSink for ultra-fast recent data
- ✅ HybridEventSink (Redis hot + DB cold)
- ✅ S3EventSink for data lake integration
- ✅ Configuration-based sink selection
- ✅ Performance comparison documentation

**Use Cases:**
- **Database Sink:** Default, balanced performance
- **Kafka Sink:** Very high volume (millions/day), event streaming
- **Redis Sink:** Ultra-fast recent data (last 7 days)
- **Hybrid Sink:** Best of both (Redis for recent, DB for historical)
- **S3 Sink:** Data lake, long-term analytics

---

### Phase 8: External System Integration (Week 8)

**Goals:**
- Adapters for external analytics
- Batch import capabilities
- Export capabilities

**Key Deliverables:**
- ✅ MixpanelBehaviorProvider (read from Mixpanel)
- ✅ AmplitudeBehaviorProvider (read from Amplitude)
- ✅ GoogleAnalyticsBehaviorProvider
- ✅ Batch import service (CSV, JSON)
- ✅ Export service (data warehouse integration)
- ✅ Adapter documentation

**Integration Patterns:**
- **Real-time push:** External system pushes to our API
- **Scheduled pull:** We query external system periodically
- **Batch import:** Upload files for bulk import
- **Aggregated provider:** Combine multiple sources

---

### Phase 9: Monitoring & Observability (Week 9)

**Goals:**
- Metrics collection
- Health checks
- Alerting
- Dashboards

**Key Deliverables:**
- ✅ Prometheus metrics for all components
- ✅ Health indicators for storage, workers, API
- ✅ Grafana dashboards
- ✅ Alert rules (ingestion stopped, worker failures, high latency)
- ✅ Structured logging with trace IDs
- ✅ Cost tracking (embedding API costs)

**Key Metrics:**
```
# Ingestion
behavior.events.ingested (counter, by type)
behavior.ingestion.latency (histogram)
behavior.ingestion.errors (counter, by error type)

# Processing
behavior.processing.duration (histogram, by worker)
behavior.embeddings.generated (counter, by type)
behavior.embeddings.cost (counter)

# Storage
behavior.storage.writes (counter, by sink type)
behavior.storage.latency (histogram)

# Insights
behavior.insights.cache.hit_rate (gauge)
behavior.insights.generation.duration (histogram)
```

---

### Phase 10: Production Hardening (Week 10)

**Goals:**
- Load testing
- Security hardening
- Documentation
- Deployment automation

**Key Deliverables:**
- ✅ Load test results (10K events/sec sustained)
- ✅ Security audit (authentication, authorization, input validation)
- ✅ Rate limiting implemented
- ✅ Complete API documentation
- ✅ Architecture documentation
- ✅ Deployment guides (Docker, Kubernetes)
- ✅ CI/CD pipelines
- ✅ Runbooks for operations

**Load Testing Targets:**
- 10,000 events/second ingestion (sustained)
- <10ms p95 latency for insights queries
- <50ms p95 latency for event ingestion
- 99.9% uptime
- Handle 10x traffic spikes gracefully

---

### Phase 11: Advanced Features (Week 11+)

**Optional advanced features based on needs:**

**A/B Testing Integration:**
- Track experiment variants
- Analyze behavior by variant
- Statistical significance testing

**Real-time Personalization:**
- WebSocket API for real-time updates
- Real-time recommendation updates
- Live user segmentation

**Advanced Analytics:**
- Funnel analysis
- Cohort analysis
- Retention analysis
- Attribution modeling

**Machine Learning Integration:**
- Churn prediction models
- Next-best-action models
- Lifetime value prediction
- Anomaly detection with ML

---

### Summary Timeline

```
Week 1:  ✅ Core module setup
Week 2:  ✅ Storage layer
Week 3:  ✅ Ingestion layer
Week 4:  ✅ Client SDKs
Week 5:  ✅ Async processing
Week 6:  ✅ Analysis & insights
Week 7:  ✅ Alternative storage
Week 8:  ✅ External integrations
Week 9:  ✅ Monitoring & observability
Week 10: ✅ Production hardening
Week 11+: 🎯 Advanced features (optional)

Total: 10 weeks to production-ready
```

---

### Success Criteria

**Functional Requirements:**
- [ ] Events ingested from web, mobile, and external systems
- [ ] No blocking/synchronous AI processing
- [ ] Embeddings only for text content (<5% of events)
- [ ] Pre-computed insights available in <10ms
- [ ] Pattern detection working and accurate
- [ ] All storage backends functional

**Performance Requirements:**
- [ ] Sustain 10,000 events/second ingestion
- [ ] <10ms p95 latency for insights queries
- [ ] <50ms p95 latency for event ingestion
- [ ] 99.9% uptime SLA
- [ ] Handle 10x traffic spikes

**Cost Requirements:**
- [ ] 95% reduction in embedding costs (selective embedding)
- [ ] Efficient storage with automatic partitioning/cleanup
- [ ] Configurable retention policies

**Quality Requirements:**
- [ ] 95%+ test coverage
- [ ] Zero critical security vulnerabilities
- [ ] Complete API documentation
- [ ] Operational runbooks
- [ ] Monitoring dashboards

---

## Conclusion

This comprehensive solution provides a **modern, scalable, cost-effective behavior tracking system** built from the ground up with best practices:

### Key Features

1. **Clean Architecture**
   - Behavior tracking in dedicated module
   - Clear separation from ai-core
   - Interface-driven design
   - Highly extensible

2. **Performance & Scale**
   - Async processing (non-blocking)
   - Proper indexing and partitioning
   - Batch operations
   - Can handle 10K+ events/second

3. **Flexibility**
   - Pluggable storage (DB, Kafka, Redis, S3)
   - Multiple ingestion sources (web, mobile, external)
   - Configurable processing pipelines
   - Easy customization via interfaces

4. **AI Integration**
   - Selective embedding (only text, 95% cost savings)
   - Uses ai-core for pattern detection
   - Pre-computed insights
   - Semantic search for relevant content

5. **Production Ready**
   - Comprehensive monitoring
   - Health checks and alerting
   - Load tested and optimized
   - Complete documentation
   - Deployment automation

### Next Steps

1. **Review and approve** this design
2. **Allocate resources** for 10-week implementation
3. **Start Phase 1** (Core Module Setup)
4. **Weekly checkpoints** to track progress
5. **Iterate** based on feedback and requirements

**The system is designed to be production-ready in 10 weeks with a clear, incremental implementation path.**

---

**End of Document**
