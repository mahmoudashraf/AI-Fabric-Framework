# Observability and Reliability Foundation Plan

Status: strategic plan for the observability, runbook, and error-budget foundation that must land before LoomAI ships a third parallel product (2026-04-21)

Purpose:

- establish the reliability foundation required to operate multiple products in parallel at Claude + Codex shipping velocity
- convert the current scattered in-memory monitoring into a coherent production observability stack
- define the runbook and error-budget discipline that prevents support debt from compounding faster than we can pay it down
- make the "quality gate" between product #2 and product #3 concrete and enforceable

This document should be read with:

- [SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md](../../Productization/future-work/MarketPlace/Products/Strategy/SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md)
- [PRODUCT_DIRECTION_DECISION_RECORD.md](../../Productization/future-work/MarketPlace/Products/Strategy/PRODUCT_DIRECTION_DECISION_RECORD.md)
- [SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md](../../Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md)
- [MONITORING_SERVICES_DEEP_ANALYSIS.md](../../../ai-infrastructure-module/docs/ARCH_REFACTORING/MONITORING_SERVICES_DEEP_ANALYSIS.md)

## 1) Why This Plan Exists Now

At current shipping velocity (50k–100k LOC per 2-week cycle, multiple products spun up in parallel), **the bottleneck is no longer code — it is operational capacity**. Without a reliability foundation:

- every new product multiplies support surface faster than support capability grows
- merchant-facing incidents go unnoticed until a review is left or a ticket arrives
- there is no shared language for "healthy" vs "unhealthy" across products
- the first real outage will consume weeks of trust that took months to build
- Codex-shipped code outpaces our ability to verify it in production

The company has a once-in-a-product-line window to build the foundation correctly: exactly two products are live or imminent (Shopify Companion, WooCommerce Companion). Before Loom Docs — the third product — ships, this plan must be executed.

## 2) Current State Honest Assessment

Baseline derived from the repo survey:

- **Logging**: SLF4J + Logback defaults. Plain text, stdout only. No JSON structuring, no aggregation, no per-tenant context propagation. No log shipping configured.
- **Metrics**: `AIMetricsService` and `AIHealthService` exist but are in-memory only (ConcurrentHashMap counters, rolling windows). Metrics evaporate on restart. No Micrometer wiring despite Spring Boot Actuator being present. Only `/health` and `/info` exposed.
- **Tracing**: None. No OpenTelemetry, Zipkin, Jaeger, or APM. Cross-service failures (Shopify Bridge → Platform → Runtime) cannot be reconstructed from logs.
- **Error tracking**: None. Exceptions are logged locally; no centralized aggregation, no grouping, no regression detection.
- **Alerting**: None. No PagerDuty, no Slack hooks, no rule definitions. Humans discover incidents by chance or by ticket.
- **Runbooks**: One exists for Shopify Companion (248 lines, manual triage). No platform-wide incident playbook, no on-call rotation, no escalation matrix.
- **Multi-tenancy observability**: Zero. No `shopId` / `merchantId` tagging in logs or metrics. Cannot answer "is this one merchant broken, or everyone?"

Honest summary: we have intentions (health services exist) but no production observability. Shipping four products onto this foundation is a reliability bet we will lose.

## 3) Guiding Principles

1. **Observe before you ship** — any new product must emit structured logs, metrics, and traces from day one. This is a merge gate, not a polish pass.
2. **Per-merchant cardinality is a first-class concern** — every signal must carry product, tenant, and surface tags, but cardinality must be bounded to prevent metric explosions.
3. **Runbooks are code artifacts** — they live next to the service, are versioned, are linked from alerts, and are tested during incidents.
4. **Error budgets beat uptime slogans** — an explicit budget creates permission to move fast *and* permission to slow down.
5. **Manual triage does not scale to four products** — every alert must route to an owner, not a shared inbox.
6. **Buy before build** — we are not an observability company. Use managed services (Grafana Cloud, Sentry, BetterStack) until cost forces a rethink.
7. **Silence is the worst signal** — no-event dashboards are useless. Every surface must emit a heartbeat.

## 4) The Three Pillars

### 4.1 Observability Stack

Target architecture — one shared pipeline, many products:

- **Logs**: structured JSON via Logback encoder → shipped via Grafana Alloy (or Vector) → Grafana Loki. Required fields: `timestamp`, `level`, `service`, `product`, `tenantId`, `merchantId`, `surfaceId`, `traceId`, `spanId`, `message`, `errorClass` (when applicable).
- **Metrics**: Micrometer → Prometheus remote write → Grafana Cloud Prometheus. Standard tag set: `product`, `service`, `tenantId` (bucketed if cardinality risk), `operation`, `status`. Use summary/histogram for latency, counter for events, gauge for queues.
- **Tracing**: OpenTelemetry Java agent on every JVM service → OTLP → Grafana Tempo. Auto-instrumentation for HTTP, JDBC, Shopify Admin API client, LLM provider calls. Baggage carries `tenantId` across service boundaries.
- **Errors**: Sentry (Java SDK) as the centralized exception sink. PII scrubbed at the SDK boundary. Release tagging tied to git SHA. Merchant/tenant context attached via scope.
- **Uptime**: BetterStack (or equivalent) black-box probes on every public surface — Shopify Bridge, theme extension manifest, Docs assistant, Comply API.
- **Dashboards**: one "product health" dashboard per product, one "platform health" dashboard shared, one "tenant debugging" dashboard templated by `tenantId`.

Cardinality controls:

- `tenantId` is permitted in logs and traces unconditionally
- `tenantId` on metrics is only permitted on a small whitelist (request count, error count) and explicitly banned elsewhere
- unbounded strings (URLs, search queries, LLM prompts) never become tag values
- a cardinality budget (<100k active series per product) is enforced by Prometheus recording rules and alerts

### 4.2 Runbook Discipline

Every service that can page a human must have a runbook that answers:

1. **What is this service?** — one paragraph, no jargon
2. **What does "healthy" look like?** — the three metrics to check first, with expected ranges
3. **Common failure modes** — at least five, ranked by frequency, with signal → diagnosis → remediation
4. **Escalation path** — who to involve at 15 / 60 / 240 minute marks
5. **Communication templates** — merchant-facing status-page copy, support reply skeleton
6. **Post-incident checklist** — what to capture before resolving

Storage rule: runbooks live in `doc/Operations/runbooks/{service}.md`, are linked from every alert, and are referenced by filename in every incident retrospective.

Coverage gate before product #3:

- Shopify Bridge runbook: upgraded from the current manual-triage baseline to the format above
- Platform control-plane runbook: new, covering vectorization runs, deployment state, indexing pipeline
- Storefront embed runbook: new, covering theme extension installation and widget delivery
- WooCommerce bridge runbook: new, mirrors Shopify Bridge structure

### 4.3 Error Budgets

We define SLOs narrow enough to be measurable, wide enough to be honest:

| Surface | Indicator | Target (30-day) | Burn-rate alert threshold |
|---|---|---|---|
| Shopify Bridge API | HTTP 5xx rate on merchant-facing endpoints | <0.5% | 14.4× for 1h OR 6× for 6h |
| Storefront widget manifest | Availability (black-box probe) | 99.9% | 2 consecutive probe failures |
| LLM-backed answer | P95 latency | <8s | 2× budget consumed in 1h |
| Indexing pipeline | Event-to-indexed lag | <5 minutes P95 | lag >15 min sustained 10 min |
| Install flow | Install completion rate | >95% | 3 consecutive failures within 5 min |

Error-budget policy:

- when a product is **within budget**, shipping speed is unconstrained
- when a product is **at 50% budget burn**, no new experiments — only reliability work
- when a product is **out of budget**, all non-reliability merges are blocked until burn rate recovers
- budgets reset on a rolling 30-day window, not calendar month

This policy is the quid pro quo for Codex-level shipping velocity: move fast when the signal says it's safe, stop when the signal says it isn't.

## 5) Ownership Model

Matches the ownership model already established in the Shopify execution roadmap.

### 5.1 Platform control-plane owner

Owns:

- shared logging/metrics/tracing libraries and defaults
- cardinality governance and the `tenantId` tag whitelist
- Grafana/Sentry/BetterStack account administration
- platform-wide dashboards and alert routing configuration
- vectorization, deployment, and indexing SLOs

Does not own:

- per-product runbooks beyond the template
- per-surface alert thresholds once the product owner has tuned them

### 5.2 Per-product owner (Shopify, WooCommerce, Docs, Comply)

Owns:

- their product's runbooks
- their product's SLO definitions and burn-rate alert thresholds
- their product's on-call rotation
- keeping their dashboards meaningful (pruning dead panels)

Does not own:

- the observability stack itself
- cross-product alerting decisions

### 5.3 On-call

Initial model — single on-call rotation across all products while the portfolio is small. Split when any one product independently justifies a dedicated rotation (rough threshold: >5 pages/week sustained).

## 6) Phased Execution

Aligned with the revised Claude + Codex roadmap.

### Phase A — Week 1: Foundation Sprint

Shipping alongside the already-planned foundation sprint. Code-bound work, so compressible.

- Wire Micrometer + Prometheus remote write into every Spring Boot service
- Replace default Logback config with JSON encoder; add MDC filter that propagates `tenantId` / `merchantId` / `traceId`
- Install OpenTelemetry Java agent on Shopify Bridge, Platform core, Runtime
- Stand up Grafana Cloud account; provision Loki, Prometheus, Tempo workspaces
- Integrate Sentry into all JVM services and the theme extension frontend
- Add BetterStack probes on Shopify Bridge `/health`, storefront manifest, admin dashboard

Exit gate: every service emits structured logs, metrics, and traces visible in Grafana Cloud within 60 seconds of emission.

### Phase B — Week 2: Shopify V1 Launch Gate

Launch of Shopify Companion is conditional on these being live:

- "Shopify Companion health" dashboard with at least: request rate, error rate, P95 latency, active install count, indexing lag, widget probe status
- Sentry issue routing to a `#incidents-shopify` Slack channel
- Upgraded Shopify Bridge runbook in the standard format
- PagerDuty (or BetterStack on-call) rotation assigned — even if the rotation is a single person
- Error-budget SLOs defined for the five indicators in Section 4.3, with burn-rate alerts active

Exit gate: a synthetic failure (kill the Shopify Bridge pod in staging) must produce a page, a Sentry issue, a dashboard red status, and a runbook link within 2 minutes.

### Phase C — Weeks 3–4: WooCommerce Launch + Observability Generalization

While WooCommerce is being built, harden the shared foundation:

- Extract `loomai-observability-starter` — a Spring Boot starter that any new service inherits to get logging, metrics, tracing, Sentry, and default SLO scaffolding for free
- Define the cardinality budget and enforce it with Prometheus recording rules + alerts
- Publish the runbook template and write the Platform control-plane runbook
- Add per-tenant debugging dashboards (parameterized by `tenantId`)
- First incident drill: run a tabletop exercise of "merchant reports assistant returning wrong product data" using only the observability stack

Exit gate: WooCommerce Companion inherits the starter and ships with logs, metrics, traces, a runbook, and defined SLOs on day one.

### Phase D — Weeks 5–6: Hardening Before Product #3

This is the explicit gate between product #2 and product #3 (Loom Docs).

Must be true to open the gate:

1. Two products have been running on the observability stack for ≥2 weeks
2. Mean time to detect (MTTD) for a synthetic incident is <5 minutes
3. Mean time to acknowledge (MTTA) in the on-call rotation is <15 minutes
4. Every alert fired in the prior two weeks has a resolution note linked to its runbook
5. At least one post-incident retrospective has been written and acted on
6. Cardinality budget has held — no product is over its series cap
7. Support escalation playbook is documented: when the on-call escalates to engineering, and what context is required

If any of these fails, Loom Docs is delayed by one week and the failing item becomes the only reliability priority. **No exceptions.**

## 7) Budget and Tooling Choices

Managed first, build later:

- **Grafana Cloud** for logs/metrics/traces — generous free tier covers early volume, pay-as-you-grow up to ~$300/month at projected scale
- **Sentry** Team plan — ~$26/month, scales with event volume
- **BetterStack** — ~$30/month for 10-probe uptime monitoring
- **PagerDuty** — free tier works for <5 users; upgrade when rotation splits

Estimated monthly cost at 4 products, 500 merchants: **~$400–800/month**. This is an order of magnitude less than a single engineer-day spent debugging an undiagnosed outage.

What we explicitly do **not** build in-house at this stage:

- our own log aggregator
- our own tracing backend
- our own alert-routing service
- our own APM

Revisit this decision at 5000+ tenants or if any managed service exceeds $5k/month.

## 8) Success Metrics

Reviewed monthly for the first two quarters, quarterly after:

1. **MTTD** (mean time to detect): trending toward <5 minutes
2. **MTTA** (mean time to acknowledge): <15 minutes for business hours, <30 off-hours
3. **MTTR** (mean time to resolve): product-specific, trending down quarter-over-quarter
4. **Error budget burn rate**: no product consistently over 50% month-over-month
5. **Alert volume per on-call shift**: <3 (otherwise alert quality work is prioritized)
6. **Runbook coverage**: 100% of services that can page a human
7. **Runbook freshness**: every runbook referenced in an incident in the last 90 days, or reviewed in the last 180

## 9) Risks and Mitigations

- **Risk: Codex-generated code emits noisy / wrong logs.** Mitigation: observability starter enforces log schema; lint rule rejects `System.out.println` and unstructured SLF4J calls in merged code.
- **Risk: Cardinality explosion kills the metrics bill.** Mitigation: per-product series cap with burn-rate alert; `tenantId` tag whitelist is code-enforced.
- **Risk: Alert fatigue within two weeks.** Mitigation: every alert must have a runbook link and an acknowledged owner; weekly review of alert-to-action ratio.
- **Risk: Single-person on-call burnout.** Mitigation: rotation is time-boxed; at two products a 1-week rotation with a backup is sufficient; split at the threshold in 5.3.
- **Risk: This plan becomes shelfware when launch pressure rises.** Mitigation: the Phase D gate is the explicit enforcement mechanism — product #3 cannot ship without it.

## 10) Explicit Non-Goals

To keep scope honest:

- We are not building 100% uptime. We are building *known* uptime with defined budgets.
- We are not building AIOps, anomaly detection, or ML-based alerting. Humans write the thresholds.
- We are not building a status page in Phase A–D. We are *preparing* for one — the black-box probes and SLOs are the inputs.
- We are not instrumenting every internal method. We instrument boundaries — public APIs, database calls, LLM calls, external integrations.
- We are not replacing the existing `AIMetricsService` / `AIHealthService`. We integrate them as sources behind Micrometer adapters; rewriting comes later if needed.

## 11) Review Cadence

- Phase gates A, B, C, D reviewed at the end of each phase — go/no-go decision for the next phase
- Monthly: SLO review, error-budget status, runbook-freshness audit
- Quarterly: observability-cost review, tooling choice review, on-call load review
- After every P1/P2 incident: retrospective within 5 business days, action items assigned with owners and due dates
