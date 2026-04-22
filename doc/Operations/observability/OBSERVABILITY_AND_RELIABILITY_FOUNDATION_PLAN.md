# Observability and Reliability Foundation Plan

Status: strategic foundation plan (2026-04-22)

Purpose:

- establish the observability and reliability baseline required to operate multiple LoomAI products in parallel
- make the platform the trusted operator investigation surface without turning the company into an observability vendor
- keep observability fast, async, and low-overhead so it strengthens the product instead of slowing it down
- define the gate between product expansion and operational readiness

Read this with:

- `../../Productization/future-work/MarketPlace/Products/Strategy/SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md`
- `../../Productization/future-work/MarketPlace/Products/Strategy/PRODUCT_DIRECTION_DECISION_RECORD.md`
- `../../Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md`
- `../../../ai-infrastructure-module/docs/ARCH_REFACTORING/MONITORING_SERVICES_DEEP_ANALYSIS.md`
- `./LOOMAI_OBSERVABILITY_STARTER_IMPLEMENTATION_PLAN.md`
- `../diagnostics/PLATFORM_FEDERATED_DIAGNOSTIC_CONTROL_PLANE_PLAN.md`

## 1) Why This Plan Exists Now

At current shipping velocity, the constraint is no longer code generation. The constraint is operational truth, safe launches, and incident response.

Without this foundation:

- every new product multiplies support surface faster than support capacity grows
- merchant-facing incidents are discovered by tickets and reviews instead of signals
- release and verification pages stay strong, but they do not yet sit on top of a standardized telemetry substrate
- the assistant can summarize docs, but it cannot investigate live systems with confidence

The window to build this correctly is now, while the platform already has meaningful verification and diagnostics surfaces but before the product portfolio gets materially wider.

## 2) Current State Honest Assessment

Baseline derived from the repo and current branch:

- Logging exists, but not yet as one shared structured, correlation-safe baseline.
- Metrics exist in pieces, but not yet as one governed Micrometer baseline across the core services.
- Tracing is not yet standardized across platform backend, runtime, and bridge.
- Error aggregation is not yet part of the common service baseline.
- The platform already has real release and diagnostics surfaces:
  - `/verification`
  - `/verification-ops`
  - `/diagnostics`
  - `/platform-diagnostics`
- Managed services already expose useful health, activity, and action surfaces, but the adapter shape is not yet fully normalized.

Honest summary: the control-plane UX is ahead of the telemetry foundation underneath it.

## 3) Guiding Principles

1. **Observe before you ship**: any new product must emit structured logs, metrics, traces, and error context from day one.
2. **Per-tenant context matters, but cardinality must stay bounded**: logs and traces may carry rich context; metrics may only carry tightly controlled low-cardinality tags.
3. **Offload telemetry fast**: applications emit locally and return to product work quickly; collectors handle batching, retry, memory limits, and transport.
4. **The platform owns the diagnostic experience, not raw telemetry storage**: raw logs, traces, metrics, and errors stay in managed backends.
5. **Async beats inline for observability plumbing**: summaries, projections, and correlations should be materialized out of band wherever possible.
6. **Runbooks are code artifacts**: versioned, linked from alerts, exercised during incidents.
7. **Error budgets beat uptime slogans**: speed is allowed when the signal says it is safe and must pause when the signal says it is not.
8. **Buy before build**: use managed Grafana Cloud, Sentry, and Better Stack until scale or cost creates a real reason to revisit.

## 4) The Three Pillars

### 4.1 Observability Stack

Target architecture: one shared pipeline, many products.

- **Logs**: structured JSON via Logback plus async local dispatch -> stdout/file or OTLP/filelog receiver -> Grafana Alloy or equivalent collector -> Grafana Loki.
- **Metrics**: Micrometer Observation API plus Spring Actuator exposure -> collector scrape or OTLP pipeline -> Grafana Cloud Prometheus / Mimir.
- **Tracing**: OpenTelemetry Java agent and/or SDK instrumentation -> OTLP -> local collector -> Grafana Tempo.
- **Errors**: Sentry SDK with release tagging, shared scope enrichment, and redaction parity with logging.
- **Uptime**: Better Stack black-box probes on public and operator-critical surfaces.
- **Platform eye**: the platform becomes the all-in-one investigation surface by consuming async summaries of release state, verification state, drift, alerts, errors, and managed-service health.

Required structured fields:

- `timestamp`
- `level`
- `service`
- `product`
- `environment`
- `deploymentId`
- `releaseId`
- `verificationRunId`
- `tenantId`
- `merchantId`
- `requestId`
- `traceId`
- `spanId`
- `errorClass`

Performance rules:

- applications must not call managed observability vendors on customer request paths
- remote shipping belongs to collectors, not to hot code paths
- expensive caller data is off by default
- low-value telemetry is dropped before customer traffic is blocked
- audit-critical lifecycle records stay separate from lossy operational telemetry

Cardinality rules:

- `tenantId` and `merchantId` are allowed freely in logs and traces
- tenant or merchant tags in metrics are allowed only on a narrow whitelist
- expanded URLs, prompts, queries, and arbitrary strings never become meter tags
- high-cardinality observation data belongs in traces and logs, not default metric dimensions

### 4.2 Runbook Discipline

Every service that can page a human must have a runbook that answers:

1. What is this service?
2. What does healthy look like?
3. What are the common failure modes?
4. What is the escalation path?
5. What should operator and customer communication look like?
6. What must be captured before resolving the incident?

Storage rule:

- runbooks live in `doc/Operations/runbooks/`
- alerts link directly to runbooks
- retrospectives reference the runbook used

### 4.3 Error Budgets

We define SLOs that are narrow enough to be measurable and honest enough to guide delivery.

Suggested initial 30-day indicators:

| Surface | Indicator | Target | Burn alert |
|---|---|---|---|
| Shopify Bridge API | merchant-facing 5xx rate | <0.5% | fast burn and slow burn alerts |
| Storefront manifest | availability | 99.9% | 2 consecutive probe failures |
| LLM-backed answer | P95 latency | <8s | 2x budget in 1h |
| Indexing pipeline | event-to-index lag P95 | <5 min | >15 min for 10 min |
| Install flow | completion rate | >95% | repeated failures within 5 min |

Policy:

- within budget: shipping speed stays high
- at 50% burn: reliability work gets priority over experiments
- out of budget: non-reliability merges pause until the burn rate recovers

## 5) Ownership Model

### 5.1 Platform control-plane owner

Owns:

- starter defaults and instrumentation baseline
- collector and sink integration standards
- cardinality governance
- cross-product dashboards and alerting defaults
- platform-native diagnostic projections and correlation contracts

Does not own:

- every product-specific runbook
- product-specific thresholds once the baseline exists

### 5.2 Product owner

Owns:

- product runbooks
- product SLO tuning
- product-specific alert review
- keeping dashboards and summary panels meaningful

### 5.3 On-call

Initial model:

- one shared rotation while the portfolio is still small
- split when sustained page volume or business criticality justifies it

## 6) Phased Execution

### Phase A: Foundation sprint

- create managed accounts and tokens for Grafana Cloud, Sentry, and Better Stack
- stand up Grafana Alloy / collector as the telemetry hop
- replace default logging with structured JSON plus async local dispatch
- wire Micrometer and Actuator exposure into core services
- route traces through OTLP and the collector layer
- integrate Sentry with shared enrichment and redaction rules
- add Better Stack probes for the most important public and operator routes
- define the correlation contract for:
  - `deploymentId`
  - `releaseId`
  - `verificationRunId`
  - `requestId`
  - `traceId`

Exit gate:

- every core service emits logs, metrics, traces, and errors through the shared architecture
- telemetry is visible in managed backends within one minute
- no request path depends on direct vendor network calls

### Phase B: Shopify launch gate

- ship a product health dashboard for Shopify
- route Sentry issue visibility to the incident channel
- upgrade the Shopify runbook to the standard format
- assign on-call ownership
- turn on initial SLO and burn-rate alerts
- prove one synthetic failure drill end to end

Exit gate:

- a synthetic failure produces:
  - a visible dashboard degradation
  - an error or incident signal
  - a runbook-linked response path

### Phase C: Shared foundation extraction

- land `loomai-observability-starter`
- centralize correlation and cardinality governance
- normalize managed-service adapter contracts
- materialize async summary projections for the platform eye
- add per-tenant debugging pivots and read models
- run at least one tabletop exercise using only the platform plus approved raw-evidence pivots

Exit gate:

- platform backend, runtime, and Shopify Bridge share one observability baseline
- the platform can investigate from summary-first views instead of tool juggling

### Phase D: Product expansion gate

Before a wider product expansion:

1. at least two surfaces have run on the shared observability stack long enough to validate the model
2. MTTD and MTTA are acceptable for the current team size
3. runbooks exist for all paging services
4. alert-to-action quality is acceptable
5. cardinality budgets are holding
6. the platform eye can correlate release state, verification, diagnostics, alerts, and managed-service summaries

If these are not true, expansion pauses and reliability work becomes the priority.

## 7) Managed Tooling Choices

Managed first:

- **Grafana Cloud**: logs, metrics, traces
- **Sentry**: exception aggregation and release-linked debugging
- **Better Stack**: uptime probes and simple incident routing

What we are explicitly not building now:

- a custom log backend
- a custom trace backend
- a custom metrics warehouse
- a custom alerting platform

## 8) Success Metrics

Track at least:

1. MTTD
2. MTTA
3. MTTR
4. error-budget burn rate
5. alert volume per shift
6. runbook coverage
7. runbook freshness
8. percentage of incidents that can be investigated from the platform first

## 9) Risks and Mitigations

- **Risk: observability slows the product**  
  Mitigation: collector-first transport, async local logging, bounded queues, no vendor API calls on request paths.

- **Risk: metrics cardinality explodes**  
  Mitigation: central whitelist, meter filters, high-cardinality context only in logs and traces.

- **Risk: the platform eye becomes another slow fan-out layer**  
  Mitigation: use async summary projections and read models; deep-link to raw evidence instead of querying every provider on each page load.

- **Risk: managed-service diagnostics stay inconsistent**  
  Mitigation: formal adapter contract and checklist-driven rollout.

- **Risk: this stays shelfware**  
  Mitigation: tie it to launch gates and product-expansion gates.

## 10) Explicit Non-Goals

- We are not building AIOps or anomaly-detection-first automation.
- We are not trying to capture every internal method call.
- We are not replacing deterministic platform evidence with assistant prose.
- We are not making the platform the long-term storage backend for all raw telemetry.

## 11) Review Cadence

- phase gate review at the end of each execution phase
- monthly SLO and alert quality review
- monthly observability cost review
- quarterly tooling review
- retrospective within 5 business days of every major incident
