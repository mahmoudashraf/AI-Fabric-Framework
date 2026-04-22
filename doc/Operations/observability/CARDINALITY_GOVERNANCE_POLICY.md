# Cardinality Governance Policy

Status: binding policy for metric and log cardinality across all LoomAI services (2026-04-21)

Purpose:

- prevent cost blow-ups in the metrics pipeline as the product portfolio grows to four or more products
- keep dashboards and alerts useful by banning labels that create unbounded series
- make the "what can I tag" question answerable without asking an SRE
- enforce the rules in code so violations fail at merge time, not at bill time

Related:

- [OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md](../../Productization/future-work/MarketPlace/Products/Strategy/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)
- [LOOMAI_OBSERVABILITY_STARTER_DESIGN.md](./LOOMAI_OBSERVABILITY_STARTER_DESIGN.md)

## 1) Why Cardinality Matters

Every unique combination of tag values on a metric becomes a separate time series. A counter with `tenantId`, `endpoint`, `status`, and `method` labels, deployed across 500 merchants, 40 endpoints, 5 status classes, 4 HTTP methods, produces **400,000 series** for that single counter.

At Grafana Cloud pricing this is roughly $1k/month for one counter. Multiply by 20 counters across 4 products and the observability bill exceeds revenue in weeks. Worse: queries slow to unusable, alerts timeout, and the system that was supposed to save us in an incident becomes the incident.

This policy exists to prevent that.

## 2) The Three-Bucket Rule

Every metric label falls into exactly one bucket:

### Bucket A — Unconstrained (low cardinality by construction)

- `product` — enumerated: `shopify-companion`, `woocommerce-companion`, `loom-docs`, `loom-comply`, `loom-knowledge`
- `service` — enumerated by deployment (e.g. `shopify-bridge`, `platform-core`, `runtime`)
- `environment` — `prod`, `staging`, `dev`
- `region` — enumerated cloud region
- `status` — enumerated: `success`, `client_error`, `server_error`, `timeout`
- `method` — HTTP method
- `operation` — enumerated operation name, defined at compile time

**Rule**: freely usable on any metric. Total combinations bounded at <1000.

### Bucket B — Whitelisted (medium cardinality, explicit opt-in)

Tags that could grow unbounded but are required for specific operational questions.

- `tenantId` — allowed on exactly the following metrics:
  - `http_requests_total`
  - `http_request_errors_total`
  - `llm_calls_total`
  - `indexing_lag_seconds` (gauge)
  - any metric explicitly listed in `CardinalityWhitelist.java` (see 4.2)
- `merchantId` — alias for `tenantId` in Shopify context; same whitelist rules
- `endpoint` — allowed only on `http_requests_total` and `http_request_errors_total`; must be a route template (`/api/shops/:shopId/products`), **never** the raw URL

**Rule**: requires an entry in the whitelist with justification. Violations fail at merge time via the observability starter's annotation processor.

### Bucket C — Banned

Never permitted on any metric, under any circumstance:

- user-supplied strings (search queries, LLM prompts, product titles)
- URLs with path parameters expanded
- email addresses
- IP addresses
- session IDs, request IDs, trace IDs (these belong on logs and traces, not metrics)
- timestamps
- database primary keys that are not `tenantId`
- error messages (use `errorClass` in Bucket A instead)

**Rule**: no exceptions. If you need this information, it belongs in logs (with tenant context) or traces.

## 3) Logs and Traces — Different Rules

Logs and traces are not priced by cardinality the same way metrics are. The rules relax:

- **Logs**: any structured field is permitted. `tenantId`, `merchantId`, `userId`, `requestId`, `traceId` are **required** on every log line via MDC. User-supplied strings are permitted but must pass through the PII scrubber (see 5).
- **Traces**: any span attribute is permitted. Baggage propagates `tenantId` across services automatically. LLM prompts and responses are attached as span events but truncated at 2000 characters.

The asymmetry is intentional: logs and traces let you debug individual tenants; metrics let you understand the whole system.

## 4) Enforcement Mechanisms

### 4.1 Starter defaults

The `loomai-observability-starter` Spring Boot starter applies these rules by default:

- a `MeterFilter` rejects any metric with banned labels (Bucket C)
- a `MeterFilter` downcases and route-templates `endpoint` tags automatically
- a `MeterFilter` replaces any `tenantId` label with the string `"unlisted"` unless the metric name appears in the whitelist

### 4.2 Compile-time whitelist

A single file — `loomai-observability-starter/src/main/resources/cardinality-whitelist.yaml` — lists every metric permitted to carry a Bucket B label:

```yaml
tenantId:
  - http_requests_total
  - http_request_errors_total
  - llm_calls_total
  - indexing_lag_seconds
  - reason: "core observability signals requiring per-tenant debugging"
endpoint:
  - http_requests_total
  - http_request_errors_total
  - reason: "route-level traffic breakdown"
```

Changes to this file require a review from the platform control-plane owner. CI rejects PRs that modify it without that approval.

### 4.3 Runtime guards

Two Prometheus recording rules run continuously:

- `loomai_cardinality_per_product` — count of active series per `product` label
- `loomai_cardinality_per_metric` — count of active series per metric name

Alert rules:

- **warn** at 70% of budget: send to `#observability-health`
- **page** at 90% of budget: wake the platform on-call

Budgets (initial values, reviewed monthly):

| Scope | Series cap | Rationale |
|---|---|---|
| Per product | 100,000 | Accommodates ~1000 tenants × whitelisted metrics at expected ratios |
| Per metric | 50,000 | Any single metric exceeding this is almost certainly mis-tagged |
| Global | 500,000 | Hard ceiling before cost review |

## 5) PII in Logs

Cardinality governance prevents cost explosions. PII governance prevents legal explosions. They overlap enough to live in the same policy.

Required behavior of the shared Logback encoder:

- field names containing `email`, `phone`, `address`, `ssn`, `card` — values redacted to `[REDACTED]`
- free-text message bodies — scanned by regex for email / phone / credit-card patterns and redacted
- LLM prompt contents — logged only at DEBUG level; never at INFO or above
- Shopify access tokens, API keys, session secrets — never logged; starter rejects any log line containing `Bearer ` followed by non-redacted content

The same rules apply to Sentry breadcrumbs and trace span attributes.

## 6) Exceptions Process

If a product owner believes a metric legitimately needs a Bucket B or Bucket C tag that this policy forbids:

1. File an issue titled `[cardinality-exception] {metric} + {tag}`
2. Describe the operational question the tag is intended to answer
3. Show that logs or traces cannot answer the same question at comparable latency
4. Commit to a series-count ceiling and an expiry date for the exception

Exceptions are granted for 90 days max, with one renewal allowed.

## 7) Review Cadence

- **Weekly** during Phase A–D of the foundation plan: platform owner reviews cardinality alerts and growth trend
- **Monthly** steady-state: platform owner reviews total series count and budget utilization per product
- **Quarterly**: review the whitelist, prune unused entries, re-evaluate budget ceilings against revenue and scale

## 8) Non-Goals

- This policy does **not** cover log volume (that is a separate cost line)
- This policy does **not** cover trace sampling rates (see the starter design doc)
- This policy does **not** try to prevent badly-named metrics beyond the tag rules — name quality is a code-review concern
