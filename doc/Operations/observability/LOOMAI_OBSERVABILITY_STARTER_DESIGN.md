# LoomAI Observability Starter — Design Document

Status: design for the shared Spring Boot starter that every LoomAI service depends on for production-ready observability (2026-04-21)

Purpose:

- make "production-ready observability" a one-line Maven dependency instead of 200 lines of per-service wiring
- enforce logging / metrics / tracing / error-tracking defaults that survive Codex-generated code
- encode the cardinality governance policy directly in the starter so violations fail at build time
- give every new product (Loom Docs, Loom Comply, Loom Knowledge) a zero-effort path to the observability bar

Related:

- [OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md](../../Productization/future-work/MarketPlace/Products/Strategy/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)
- [CARDINALITY_GOVERNANCE_POLICY.md](./CARDINALITY_GOVERNANCE_POLICY.md)
- [RUNBOOK_TEMPLATE.md](../runbooks/RUNBOOK_TEMPLATE.md)

## 1) Design Goals

1. **One dependency, everything wired** — adding the starter to a new service must produce structured logs, Micrometer metrics, OpenTelemetry traces, Sentry integration, and default health endpoints with zero additional code.
2. **Opinions, not options** — the starter chooses defaults that match the cardinality and PII policies. Products can override via properties, but the defaults are production-correct.
3. **Fail loud at build time** — policy violations (banned cardinality labels, missing MDC context, unstructured logging) fail at compile or startup, not in production.
4. **Test ergonomics preserved** — in test profiles, the starter auto-disables expensive integrations (OTLP exporter, Sentry) so unit tests stay fast.
5. **Upgrade in one place** — when the stack changes (new OTel version, new Grafana Cloud endpoint), one starter release propagates to every product.

## 2) Module Layout

Located at `platform/loomai-observability-starter/`:

```
loomai-observability-starter/
├── pom.xml
├── src/main/java/com/loomai/observability/
│   ├── LoomaiObservabilityAutoConfiguration.java
│   ├── logging/
│   │   ├── JsonLogEncoderConfig.java
│   │   ├── TenantMdcFilter.java
│   │   └── PiiScrubbingConverter.java
│   ├── metrics/
│   │   ├── MicrometerDefaultsConfig.java
│   │   ├── CardinalityMeterFilter.java
│   │   └── CardinalityWhitelistLoader.java
│   ├── tracing/
│   │   ├── OpenTelemetryConfig.java
│   │   └── TenantBaggagePropagator.java
│   ├── errors/
│   │   ├── SentryConfig.java
│   │   └── SentryTenantScopeEnricher.java
│   ├── health/
│   │   └── LoomaiHealthEndpointConfig.java
│   └── properties/
│       └── LoomaiObservabilityProperties.java
├── src/main/resources/
│   ├── META-INF/spring.factories
│   ├── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   ├── logback-loomai.xml
│   └── cardinality-whitelist.yaml
└── src/test/...
```

## 3) Consumption

A new service adds:

```xml
<dependency>
  <groupId>com.loomai.platform</groupId>
  <artifactId>loomai-observability-starter</artifactId>
  <version>${loomai.platform.version}</version>
</dependency>
```

And sets the two properties that cannot be inferred:

```yaml
loomai:
  observability:
    product: shopify-companion   # required — one of the enumerated product values
    service: shopify-bridge      # required — matches deployment name
```

Everything else has production-correct defaults.

## 4) Component Design

### 4.1 Structured Logging

- Logback configuration packaged as `logback-loomai.xml`; loaded automatically if the consumer has not provided their own `logback-spring.xml`
- JSON encoder based on `logstash-logback-encoder`
- Standard fields on every line: `@timestamp`, `level`, `logger`, `message`, `service`, `product`, `environment`, `traceId`, `spanId`, `tenantId`, `merchantId`, `requestId`, `errorClass`, `stackTrace` (when applicable)
- A Logback `Converter` scans messages and MDC values for PII patterns (email / phone / credit-card / access-token) and redacts before emission
- A `TenantMdcFilter` (Servlet + WebFlux variants) extracts `tenantId` from either (a) a JWT claim, (b) the `X-Tenant-Id` header, or (c) a resolver bean provided by the consumer, and pushes it into MDC for the request lifecycle

Banned at runtime: plain `System.out.println` calls are not catchable by Logback, but an annotation processor (4.6) rejects them at compile time in any module that depends on the starter.

### 4.2 Metrics

- `MicrometerDefaultsConfig` wires a `PrometheusMeterRegistry` and a remote-write exporter pointed at the Grafana Cloud endpoint configured via properties
- `CardinalityMeterFilter` applies the rules from the cardinality governance policy:
  - drops any metric with Bucket C labels
  - replaces `tenantId` / `merchantId` values with `"unlisted"` for metric names not in the whitelist
  - route-templates `endpoint` labels using Spring's `HandlerMapping`
  - lowercases enum labels for consistency
- `CardinalityWhitelistLoader` reads `cardinality-whitelist.yaml` (from the starter's classpath, extendable by consumers via `loomai.observability.whitelist-overlay`)
- Standard meters auto-registered: JVM, system CPU, Tomcat / Netty, HikariCP, HTTP server, HTTP client, `@Scheduled` execution time
- Product-specific meters: consumers emit them via `MeterRegistry` injection; the filter takes care of governance

Default scrape interval: 15s to Grafana Cloud remote write. Override via `loomai.observability.metrics.export-interval`.

### 4.3 Tracing

- `OpenTelemetryConfig` wires the OTel SDK with an OTLP exporter to Grafana Tempo
- Auto-instrumentation: HTTP server, HTTP client, JDBC, R2DBC, Shopify Admin API client (instrumented explicitly — see 4.7), LLM provider clients (Anthropic, OpenAI — instrumented explicitly)
- `TenantBaggagePropagator` attaches `tenantId` to OTel `Baggage` so downstream services receive it without manual plumbing
- Default sampler: parent-based, 10% head sampling for root spans. Override via `loomai.observability.tracing.sample-ratio`. Errors and slow requests (>P99 latency) are always sampled via tail-sampling rules at the collector (not in the SDK)
- LLM call attributes captured: `llm.provider`, `llm.model`, `llm.tokens.input`, `llm.tokens.output`, `llm.latency_ms`, `llm.cost_usd_estimate`. Prompt and response text attached as span events, truncated to 2000 characters

### 4.4 Error Tracking

- `SentryConfig` enables the Sentry Java SDK, disabled in test profile
- `SentryTenantScopeEnricher` attaches `tenantId`, `merchantId`, `product`, `service`, `traceId` to every event's scope
- Release tagging: `sentry.release` set from `BuildProperties` (requires `spring-boot-maven-plugin` `build-info` goal; starter documents this requirement)
- PII scrubbing: the Sentry SDK's `beforeSend` hook runs the same scrubber as the Logback converter, so Sentry events cannot leak what logs cannot
- Grouping: errors are fingerprinted by `product` + `service` + `errorClass` + top stack frame — prevents one-off stack-trace noise from creating thousands of issue groups

### 4.5 Health Endpoints

- `/actuator/health` — standard Spring Boot health (liveness)
- `/actuator/health/liveness` — Kubernetes liveness probe; returns 200 if JVM is responsive
- `/actuator/health/readiness` — Kubernetes readiness; returns 200 only if dependencies (DB, vector store, LLM provider) are reachable
- `/loomai/observability/self-check` — a starter-provided endpoint that confirms logs are being shipped, metrics are being scraped, and at least one recent trace was exported. Used by BetterStack probes and the Phase B launch gate.

### 4.6 Compile-Time Enforcement

A companion annotation processor (`loomai-observability-lint`) rejects at compile time:

- `System.out.println`, `System.err.println`
- `printStackTrace()` invocations
- SLF4J calls with string concatenation (forces parameterized logging: `log.info("x={}", x)`)
- Micrometer `Tag.of("tenantId", ...)` calls outside whitelisted metric names
- Direct instantiation of `Logger` from `java.util.logging`

Consumers depend on the processor in the provided scope; it does not ship into production artifacts.

### 4.7 Integration Points Consumers Implement

The starter cannot know everything — consumers provide:

- `TenantIdResolver` bean — extracts tenant from the request; default implementation tries JWT claim then header
- `ShopifyClientInstrumentation` — for services that call the Shopify Admin API, a pre-written `WebClient.Builder` customization adds tracing spans and metrics per Shopify resource
- `LLMClientInstrumentation` — provided wrappers for the Anthropic and OpenAI Java SDKs that add `llm.*` span attributes and `llm_calls_total` counter

## 5) Configuration Surface

```yaml
loomai:
  observability:
    product: shopify-companion           # required
    service: shopify-bridge              # required
    environment: ${SPRING_PROFILES_ACTIVE:prod}

    logging:
      json: true                         # false only in local dev for human readability
      include-mdc-keys: "*"              # comma-separated or wildcard
      pii-scrubbing: true                # disable only for security-reviewed tests

    metrics:
      export-endpoint: ${GRAFANA_CLOUD_PROMETHEUS_URL}
      export-interval: 15s
      whitelist-overlay: classpath:cardinality-whitelist-overlay.yaml  # optional product-specific additions

    tracing:
      enabled: true
      otlp-endpoint: ${GRAFANA_CLOUD_TEMPO_URL}
      sample-ratio: 0.1

    errors:
      enabled: true
      sentry-dsn: ${SENTRY_DSN}
      release: ${spring.application.version:unknown}

    health:
      self-check-enabled: true
```

All secrets (`GRAFANA_CLOUD_*`, `SENTRY_DSN`) come from the environment, not configuration files.

## 6) Testing Strategy

- `@ObservabilityStarterTest` meta-annotation enables an in-memory meter registry, a span-capturing tracer, and a mock Sentry transport — tests can assert on emitted signals without network
- A starter integration test suite runs against a real Grafana Cloud staging account nightly; failures block the starter release
- Each consumer service gets a "observability smoke test" — a single integration test that hits a sample endpoint and asserts log, metric, and trace emission. Provided as a reusable JUnit extension.

## 7) Versioning and Rollout

- Starter follows semver; breaking changes require a migration note
- Published to the internal Maven repository on every merge to `main`
- Consumers pin via the `loomai.platform.version` BOM — single-line upgrade
- First release (`1.0.0`) must precede Shopify Companion V1 launch (Phase B of the foundation plan)
- `1.1.0` target: Week 3–4, adds the WooCommerce bridge instrumentation and the whitelist overlay feature
- `2.0.0` target: after product #3 launch — any breaking changes learned from 90 days of production use

## 8) What the Starter Does NOT Do

Explicit non-goals, to keep scope honest:

- **Does not provide dashboards** — dashboards live in Grafana with per-product definitions, versioned separately
- **Does not provide alert rules** — alert definitions live in the Prometheus / Alertmanager config repo, per product
- **Does not replace `AIMetricsService` / `AIHealthService`** — those continue to exist; the starter adds Micrometer adapters so their counters become real time series, but the in-memory APIs remain
- **Does not handle business-level events** — audit logs, security events, compliance trails remain in their existing pipelines
- **Does not implement sampling policy changes at runtime** — sample ratios change via deployment, not feature flag, to prevent accidental cost spikes

## 9) Risks and Mitigations

- **Risk: starter becomes a bottleneck for every product change.** Mitigation: consumers can override any default via properties; the starter is opinionated, not restrictive. Escape hatches are documented.
- **Risk: upgrading the starter breaks many services at once.** Mitigation: semver discipline; breaking changes require a migration note and a deprecation release; staging rollout before prod.
- **Risk: Codex generates code that defeats the lint rules.** Mitigation: rules run in CI, not just IDE; PR check blocks merge on violation.
- **Risk: PII scrubber false-negatives leak data.** Mitigation: scrubber is backed by a test corpus of known patterns; adding a new pattern is a one-line change; Sentry has a second pass.
- **Risk: cost of managed services outpaces budget.** Mitigation: cardinality governance policy enforces series caps; monthly cost review in the foundation plan's cadence.

## 10) Open Questions

Tracked for resolution before `1.0.0`:

- Does `TenantIdResolver` need to support async resolution (e.g. DB lookup)? Current design is synchronous; if not sufficient, add a `ReactiveTenantIdResolver` variant
- Should LLM span attributes include a cost estimate at emission time, or only tags that allow later aggregation? Leaning toward emission-time with a swappable pricing table
- Tail-sampling vs head-sampling: Grafana Tempo supports tail-sampling at the collector — worth the complexity for 1.0 or defer to 2.0?
- Do we need a "multi-region" mode for Loom Comply's regulated-vertical deployments, or does single-region suffice for year one?
