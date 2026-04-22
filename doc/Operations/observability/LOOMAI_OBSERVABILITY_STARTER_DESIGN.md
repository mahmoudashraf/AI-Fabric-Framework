# LoomAI Observability Starter - Design Document

Status: design document (2026-04-22)

Purpose:

- make production-ready observability a one-line framework dependency instead of repeated per-service wiring
- standardize logging, metrics, tracing, and error enrichment across platform backend, runtime, and product services
- keep telemetry fast and low-overhead by default
- give the platform control plane a stable telemetry contract for verification, diagnostics, and assistant investigation

Related:

- `./OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md`
- `./CARDINALITY_GOVERNANCE_POLICY.md`
- `./LOOMAI_OBSERVABILITY_STARTER_IMPLEMENTATION_PLAN.md`
- `../runbooks/RUNBOOK_TEMPLATE.md`

## 1) Design Goals

1. **One dependency, production baseline included**: add the starter and get structured logging, Micrometer defaults, trace propagation hooks, optional Sentry enrichment, and sane health defaults.
2. **Collector-first, not vendor-first**: services emit locally; collectors handle batching, retry, memory protection, and remote transport.
3. **Fast by default**: observability must not add meaningful latency to customer request paths.
4. **Cardinality-safe by default**: low-cardinality metrics, rich logs and traces, explicit guardrails.
5. **Correlation-first**: emit stable identifiers the platform can join across release, verification, diagnostics, and managed-service flows.
6. **Stable architecture, phased adoption**: one durable module design, rolled out safely across services.

## 2) Module Location

The starter belongs in:

- `ai-infrastructure-module/loomai-observability-starter`

It does not belong under `Platfrom/` because it must be consumable by:

- `Platfrom/backend`
- `ai-infrastructure-module/ai-fabric-runtime`
- `product-services/*`

## 3) Module Layout

```text
ai-infrastructure-module/loomai-observability-starter/
|-- pom.xml
|-- src/main/java/com/ai/infrastructure/observability/
|   |-- LoomaiObservabilityAutoConfiguration.java
|   |-- properties/
|   |   `-- LoomaiObservabilityProperties.java
|   |-- logging/
|   |   |-- ObservabilityLogbackConfigurer.java
|   |   |-- RequestContextMdcFilter.java
|   |   `-- RedactionSupport.java
|   |-- metrics/
|   |   |-- MicrometerDefaultsConfig.java
|   |   |-- CardinalityMeterFilter.java
|   |   `-- CardinalityWhitelistLoader.java
|   |-- tracing/
|   |   |-- TraceContextConfig.java
|   |   `-- TenantBaggagePropagator.java
|   |-- errors/
|   |   |-- SentryConfig.java
|   |   `-- SentryScopeEnricher.java
|   `-- testing/
|       `-- ObservabilityStarterTestSupport.java
`-- src/main/resources/
    |-- META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    |-- logback-loomai.xml
    `-- cardinality-whitelist.yaml
```

## 4) Consumption Model

A consuming service adds the dependency and sets the two required identifiers:

```yaml
loomai:
  observability:
    product: platform-control-plane
    service: platform-backend
```

Everything else should have safe defaults.

## 5) Component Design

### 5.1 Logging

The starter should provide:

- structured JSON logging
- a shared field schema
- MDC population for request and deployment context
- redaction for secrets and agreed PII classes
- async local dispatch via Logback `AsyncAppender`

Required fields:

- `timestamp`
- `level`
- `logger`
- `message`
- `product`
- `service`
- `environment`
- `deploymentId`
- `releaseId`
- `verificationRunId`
- `requestId`
- `tenantId`
- `merchantId`
- `traceId`
- `spanId`
- `errorClass`

Performance rules:

- no remote log shipping from the request path
- caller data off by default
- WARN and ERROR should survive pressure better than lower-value events
- queue policy must be bounded and explicit

### 5.2 Metrics

The starter should provide:

- Micrometer common tags:
  - `product`
  - `service`
  - `environment`
- Spring Boot Actuator-compatible Prometheus exposure
- meter filters for cardinality protection
- whitelist-driven exceptions for narrowly approved tenant-scoped metrics

Metric rules:

- low-cardinality dimensions belong in metrics
- high-cardinality context belongs in logs and traces
- route templates should be normalized before tagging
- prompts, expanded URLs, search queries, and other arbitrary strings are never meter tags

### 5.3 Tracing

The starter should provide:

- trace context propagation hooks
- service name alignment from properties
- propagation of:
  - `deploymentId`
  - `releaseId`
  - `verificationRunId`
  - `tenantId`
  - `merchantId` when applicable

Transport rules:

- target a local or host-level OTLP collector endpoint by default
- do not assume direct service-to-vendor export as the primary path
- let the collector own batching, retry, memory protection, and advanced sampling

### 5.4 Error Tracking

The starter should provide optional Sentry auto-configuration with:

- shared scope enrichment:
  - `product`
  - `service`
  - `tenantId`
  - `merchantId`
  - `traceId`
- release tagging
- redaction behavior aligned with logging

The design assumption is that error shipping should be non-blocking from the point of view of the request path.

### 5.5 Health and Local Wiring Checks

The starter should support:

- normal Spring Boot liveness/readiness health endpoints
- optional local observability self-checks that validate:
  - config loaded
  - MDC filter active
  - meter filter active
  - tracing hooks active

The starter should not attempt to prove that Grafana Cloud or Sentry already received data. End-to-end sink verification belongs in environment verification and deployment readiness checks.

### 5.6 Compile-Time Enforcement

A companion lint module is still a valid follow-on track, but it is not required for the architecture to be mature.

If implemented, it should focus on:

- rejecting `System.out.println`
- rejecting `printStackTrace`
- rejecting obviously unsafe metric tags
- pushing teams toward parameterized logging

## 6) Consumer Integration Points

The starter cannot infer everything. Consumers may provide:

- `TenantIdResolver`
- `MerchantIdResolver`
- request/deployment correlation hooks where the service already knows those values
- optional client instrumentation adapters for Shopify and LLM providers

The starter should never force business-logic rewrites just to fit observability.

## 7) Configuration Surface

```yaml
loomai:
  observability:
    product: platform-control-plane
    service: platform-backend
    environment: ${SPRING_PROFILES_ACTIVE:prod}

    logging:
      json: true
      async: true
      caller-data: false
      pii-redaction: true

    metrics:
      prometheus-exposure: true
      whitelist-overlay: classpath:cardinality-whitelist-overlay.yaml

    tracing:
      enabled: true
      otlp-endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4317}
      sample-ratio: 0.1

    errors:
      enabled: true
      sentry-dsn: ${SENTRY_DSN:}

    health:
      local-self-check-enabled: false
```

Secrets stay env-driven, not committed to source control.

## 8) Testing Strategy

The starter should support:

- unit tests without hosted sink dependencies
- integration tests for:
  - property binding
  - auto-configuration
  - MDC propagation
  - meter filter behavior
  - optional Sentry on/off behavior
- reusable smoke-test helpers for consumer services

Optional environment smoke tests against real managed backends are useful, but they should not be the only proof that the starter works.

## 9) What the Starter Does Not Do

- It does not define dashboards.
- It does not define alert rules.
- It does not replace the platform verification or diagnostics UI.
- It does not install the OpenTelemetry Java agent.
- It does not become a business-event or audit framework.
- It does not make live multi-provider calls just to render telemetry locally.

## 10) Risks and Mitigations

- **Risk: the starter becomes a second platform product.**  
  Mitigation: keep it focused on shared in-process observability concerns.

- **Risk: observability slows product requests.**  
  Mitigation: async logging, collector-first transport, bounded queues, and no request-path remote sink calls.

- **Risk: metric cardinality gets out of control.**  
  Mitigation: central meter filters, explicit whitelist, and low/high-cardinality split.

- **Risk: the design still assumes future rewrites.**  
  Mitigation: stable module boundary and stable property model from day one.

## 11) Open Questions

- When do we add compile-time lint, if at all?
- Do we want starter-provided wrappers for specific external SDKs, or should that stay per-service until repeated pain appears?
- What sampling policy should be fixed in the service and what should remain collector-side?
- Which local self-checks are worth shipping in the starter and which belong in platform-level readiness logic?
