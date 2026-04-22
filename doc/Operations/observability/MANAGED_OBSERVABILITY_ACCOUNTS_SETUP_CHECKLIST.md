# Managed Observability Accounts Setup Checklist

Status: setup checklist (2026-04-22)

This document defines the smallest managed-observability account set needed to support:

- the observability starter plan
- the federated diagnostic control plane direction
- future admin and AI-assistant investigation workflows

It is intentionally biased toward:

- managed services over self-hosting
- least-privilege tokens
- account setup that is useful now, not a large procurement exercise

Read this together with:

- `./OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md`
- `./LOOMAI_OBSERVABILITY_STARTER_IMPLEMENTATION_PLAN.md`
- `../diagnostics/PLATFORM_FEDERATED_DIAGNOSTIC_CONTROL_PLANE_PLAN.md`

---

## 1. Decision

The default managed observability stack is:

1. `Grafana Cloud` for logs, metrics, and traces
2. `Sentry` for exception aggregation and release-linked error investigation
3. `Better Stack` for uptime probes and monitor-driven incident visibility

This is the current recommended baseline.

We are not standardizing on self-hosted observability services as the primary production path.

---

## 2. Required Accounts

### 2.1 Grafana Cloud

Create:

- one Grafana Cloud organization
- one stack for the current platform environment strategy

Required now:

- one `Cloud Access Policy`
- one token under that policy for telemetry ingestion

Recommended policy scope for now:

- `metrics:write`
- `logs:write`
- `traces:write`

Realm:

- stack-scoped, not organization-wide, unless there is a clear multi-stack need

Optional later:

- one Grafana `service account`
- one service account token for dashboard, alert, or API automation

Why:

- Cloud Access Policy tokens are for hosted telemetry services
- service account tokens are for the Grafana HTTP API

### 2.2 Sentry

Create:

- one Sentry organization
- at least one project now

Recommended first projects:

- `platform-backend`
- `platform-ui`

Add later as rollout expands:

- `shopify-bridge`
- `ai-fabric-runtime`

Required now:

- project `DSN` for ingestion
- one organizational auth token for API and release automation

Recommended token scopes:

- `org:read`
- `project:read`
- `event:read`
- `project:write`
- `project:releases`

Optional later:

- additional project-level DSNs as each service is onboarded

### 2.3 Better Stack

Create:

- one Better Stack account/team

Required now:

- one `Uptime API token`

Recommended immediate usage:

- probes for public platform surfaces
- probes for Shopify Bridge and other externally reachable service health endpoints

Not required now:

- Better Stack log source tokens

Reason:

- Grafana Cloud is the primary logs / metrics / traces backend in the current plan
- Better Stack is currently serving the uptime / monitor role

---

## 3. Account Creation Order

Use this order:

1. `Grafana Cloud`
2. `Sentry`
3. `Better Stack`

Why this order:

- Grafana Cloud is the largest telemetry dependency in the current observability plan
- Sentry is the next most useful because it gives high-signal failure grouping early
- Better Stack can be added after core signal ingestion is ready

---

## 4. Secrets To Prepare

Prefer storing secrets in Railway variables, local `.env` files outside source control, or another secrets manager.

Do not paste long-lived raw tokens into docs or source-controlled files.

Recommended variable names:

```env
GRAFANA_CLOUD_STACK_URL=
GRAFANA_CLOUD_ACCESS_TOKEN=
GRAFANA_GRAFANA_SA_TOKEN=

SENTRY_ORG_SLUG=
SENTRY_PROJECT_SLUG=
SENTRY_DSN=
SENTRY_AUTH_TOKEN=

BETTERSTACK_UPTIME_TOKEN=
```

Notes:

- `GRAFANA_GRAFANA_SA_TOKEN` is optional for now
- `SENTRY_PROJECT_SLUG` can point to the first project we onboard, such as `platform-backend`
- additional DSNs can be added later per project instead of overloading one shared Sentry project forever

---

## 5. Required Now Vs Later

### Required now

- Grafana Cloud account
- Grafana Cloud stack
- Grafana Cloud access policy token for telemetry ingestion
- Sentry organization
- first Sentry project
- Sentry DSN
- Sentry organizational auth token
- Better Stack account
- Better Stack uptime token

### Useful soon, but not blocking

- Grafana service account token
- additional Sentry projects
- Better Stack team structure or on-call routing

### Not needed yet

- PagerDuty
- Better Stack log ingestion tokens
- self-hosted observability infrastructure
- multi-region or multi-stack Grafana policy complexity

---

## 6. Practical Setup Guidance

### Grafana Cloud

Set up first:

- stack
- access policy
- token with write scopes

Defer until automation actually needs it:

- service account token
- dashboard provisioning automation
- alert-rule automation

### Sentry

Set up first:

- organization
- one or two core projects
- DSN
- auth token

Defer until frontend/release workflows are live:

- source map upload automation
- advanced release tracking across multiple projects

### Better Stack

Set up first:

- uptime token
- a very small monitor set

Start with:

- platform UI public route
- platform backend health route if externally reachable
- Shopify Bridge public health route when available

Defer:

- large monitor catalogs
- on-call complexity
- logs ingestion

---

## 7. Minimum Hand-Off Needed

To unblock implementation planning and later wiring, the minimum useful hand-off is:

1. `GRAFANA_CLOUD_STACK_URL`
2. `GRAFANA_CLOUD_ACCESS_TOKEN`
3. `SENTRY_ORG_SLUG`
4. `SENTRY_PROJECT_SLUG`
5. `SENTRY_DSN`
6. `SENTRY_AUTH_TOKEN`
7. `BETTERSTACK_UPTIME_TOKEN`

If only part of the stack is ready, the best order to hand off secrets is:

1. Grafana Cloud
2. Sentry
3. Better Stack

---

## 8. What This Checklist Deliberately Avoids

This checklist does not require:

- full dashboard design up front
- alert topology finalization up front
- on-call process maturity on day one
- self-hosted fallback planning before managed observability is even connected

The goal is to get the core managed services in place with the correct token boundaries so the platform and observability work can proceed cleanly.

---

## 9. Completion Criteria

This checklist is complete when:

1. all three accounts exist
2. the required tokens exist
3. the secrets are stored outside source control
4. the env var names are standardized
5. the platform can begin integrating logs, metrics, traces, errors, and uptime data without re-deciding vendors
