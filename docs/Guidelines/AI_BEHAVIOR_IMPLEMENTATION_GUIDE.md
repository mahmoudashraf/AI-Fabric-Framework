# AI Behavior Module Implementation Guide

## Overview

The `ai-behavior-module` isolates behavior tracking from `ai-infrastructure-core` and exposes a self-contained Spring Boot starter that can be dropped into any service.

- **Ingestion:** `POST /api/ai-behavior/ingest` (+ `/batch`) with JSON payloads validated by `BehaviorEventValidator`.
- **Storage:** JPA entities (`BehaviorEvent`, `BehaviorInsights`, `BehaviorMetrics`, `BehaviorEmbedding`) backed by Flyway migrations (`V1`–`V5`).
- **Processing:** Async workers update aggregates, generate embeddings via `AICoreService`, and schedule pattern detection.
- **Insights:** `BehaviorAnalysisService` orchestrates pluggable `BehaviorAnalyzer` implementations (default: `PatternAnalyzer`) and caches results through `BehaviorInsightsService`.
- **Query:** `BehaviorQueryService` wraps pluggable `BehaviorDataProvider` implementations; the default `DatabaseBehaviorProvider` uses JPA specifications.
- **Monitoring:** `BehaviorMonitoringController` returns live counters and active sink metadata.

## Configuration

All knobs live under `ai.behavior.*` (see `BehaviorProperties`):

```yaml
ai:
  behavior:
    enabled: true
    ingestion:
      validation-enabled: true
      strict-mode: false
    sink:
      type: database
    processing:
      aggregation:
        enabled: true
      pattern-detection:
        fixed-delay: PT5M
        analysis-window-hours: 24
      embedding:
        event-types: [feedback, review, search]
        min-text-length: 10
      async-executor:
        core-pool-size: 4
        max-pool-size: 16
        queue-capacity: 1000
    insights:
      cache-ttl: PT5M
      min-events-for-insights: 10
    retention:
      events-days: 90
      insights-days: 180
```

## Integration Steps

1. **Add Dependency**
   ```xml
   <dependency>
     <groupId>com.ai.infrastructure</groupId>
     <artifactId>ai-behavior-module</artifactId>
   </dependency>
   ```
2. **Expose API**
   `AIBehaviorAutoConfiguration` registers controllers automatically; no manual component scan needed.
3. **Publish Events Programmatically**
   Inject `BehaviorIngestionService` and call `ingest` / `ingestBatch` with populated `BehaviorEvent` instances.
4. **Query Insights**
   Use `BehaviorInsightsService#getUserInsights(UUID)` for <10 ms access to cached results or hit `/api/ai-behavior/users/{id}/insights`.
5. **Retain/Delete Data**
   `BehaviorDeletionService` removes events, embeddings, insights, and metrics for a user; wire it into existing privacy flows.
6. **Extend**
   - Implement additional `BehaviorAnalyzer` beans for churn, segmentation, etc.
   - Register custom `BehaviorEventSink` / `BehaviorDataProvider` beans for Kafka, Redis, S3, etc.

## Operational Notes

- All async workers share the `ai-behavior-` thread pool configured via `processing.async-executor`.
- Flyway migrations live inside the module; include the jar on the Flyway classpath or copy the SQL files when operating standalone.
- `BehaviorEvent` metadata is stored as JSONB and queried via `jsonb_extract_path_text`, so Postgres 14+ (or compatible) is recommended.
- Embedding generation costs are controlled by selective event types + minimum text length; the worker skips short/plain queries automatically.
- Monitoring endpoint: `GET /api/ai-behavior/monitoring/summary` ⇒ `{sinkType, events, insights, metrics}`.
