# Platform Federated Diagnostic Control Plane Plan

Status: planning document (2026-04-22)

Purpose:

- define how the platform becomes the single operator entry point for diagnosis across deployments, managed services, and external observability tools
- keep the platform as the all-in-one investigation surface without turning it into a custom raw telemetry backend
- create one normalized investigation substrate for both admins and the platform assistant

Read this with:

- `../../Productization/future-work/PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`
- `../../Productization/future-work/PLATFORM_ASSISTANT_TRACK_C_EXECUTION_PLAN.md`
- `../observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md`
- `../observability/LOOMAI_OBSERVABILITY_STARTER_IMPLEMENTATION_PLAN.md`
- `../observability/CARDINALITY_GOVERNANCE_POLICY.md`

## 1) Executive Summary

The platform should become the **single point of diagnosis and navigation** for operator workflows.

It should not become:

- a custom log backend
- a custom trace backend
- a custom metrics warehouse
- a custom alerting platform

The correct model is:

- raw telemetry stays in managed backends
- the platform owns normalized operational truth
- the platform materializes async summaries and correlation views
- operators and the assistant investigate from the platform first
- raw evidence stays available through pivots and deep links

This is the right "all-in-one platform eye" direction because it gives one trusted operator surface without forcing LoomAI to become an observability company.

## 2) Why This Matters Now

The current branch already has meaningful surfaces for:

- deployment verification
- verification operations
- deployment diagnostics
- platform diagnostics
- managed-service health, activity, and logs in selected flows

So the problem is no longer "should we have diagnostics pages?"

The problem is:

- can these pages become one coherent investigation system
- can they correlate platform-native records with external telemetry and managed-service evidence
- can the assistant use the same investigation model instead of inventing a second one

## 3) Core Principles

1. **Platform first**: operators should start investigations in the platform.
2. **Federated evidence**: raw telemetry remains in external systems.
3. **Summary first, raw second**: the platform should load fast from normalized summaries, then pivot to deep evidence on demand.
4. **Deterministic evidence remains first-class**: release, verification, drift, remediation, and audit records stay visible and do not get replaced by assistant prose.
5. **Permissions are part of diagnosis**: access scope and tenant visibility must be enforced in summaries, excerpts, and pivots.
6. **Correlation is the product**: the main value is joining deployment, release, verification, service, tenant, and request context across systems.
7. **Async materialization over live fan-out**: operator pages should not depend on synchronous calls to every backend on first render.

## 4) Federation Model

### 4.1 Layer A: Platform-native operational truth

The platform should own and persist:

- deployments
- versions
- releases
- verification runs
- drift findings
- remediation actions
- audit records
- approval records
- managed-service inventory and readiness

This is the durable control-plane model.

### 4.2 Layer B: Async summary and event projections

The platform should materialize normalized projections for:

- release health
- verification status
- recent diagnostics posture
- current alert summary
- current error summary
- recent probe failures
- managed-service health summary
- dependency and blast-radius summary

These projections should be updated asynchronously from:

- platform-native lifecycle events
- managed-service adapter data
- alert or error feeds
- scheduled summary refreshes where needed

This is what powers the all-in-one platform eye.

### 4.3 Layer C: Deep raw evidence

Raw evidence remains primarily in:

- Grafana Loki or equivalent for logs
- Grafana Tempo or equivalent for traces
- Grafana Cloud metrics / Mimir / Prometheus for metrics
- Sentry or equivalent for grouped exceptions
- provider-native consoles where appropriate

The platform should pivot or deep-link to these systems instead of copying everything into its own database.

## 5) Ownership Modes and Diagnostic Depth

### 5.1 Platform-owned services

Examples:

- platform backend
- assistant deployment
- runtime paths the platform provisions directly

Diagnostic promise:

- strong platform-native summary
- recent operational signals
- governed actions
- raw-evidence pivots

### 5.2 Platform-managed external services

Examples:

- managed inference services
- managed product services
- managed vector roots

Diagnostic promise:

- lifecycle and drift summary
- bounded logs or pivots
- blast-radius visibility
- governed repair actions

### 5.3 External unmanaged systems

Examples:

- customer-owned downstream integrations
- third-party systems outside platform control

Diagnostic promise:

- connectivity and contract state
- recent known failures
- guidance and evidence pointers

The platform should not pretend it has full end-to-end raw observability over systems it does not control.

## 6) Canonical Correlation Contract

Preferred identifiers:

- `deploymentId`
- `releaseId`
- `verificationRunId`
- `remediationActionId`
- `serviceRef`
- `tenantId`
- `merchantId`
- `requestId`
- `traceId`
- `alertId`
- `incidentId` if incident records are added later

Rules:

- every normalized summary carries as many of these as the platform knows
- assistant answers surface them when useful
- deep links preserve them where practical
- observability starter work emits compatible identifiers

## 7) UI and API Shape

### 7.1 Primary operator surfaces

The main platform surfaces remain:

- `/verification`
- `/verification-ops`
- `/diagnostics`
- `/platform-diagnostics`

The control plane should deepen and connect these surfaces rather than create a disconnected second diagnostics application.

### 7.2 Platform eye behavior

The all-in-one platform eye should provide:

- a fast investigation summary
- current status and risk posture
- correlated recent release and verification signals
- recent alerts and top errors
- managed-service dependency and blast-radius summary
- links to raw evidence
- governed next actions

### 7.3 API families

The platform should move toward explicit API families for:

- normalized diagnostic summaries
- correlated recent events
- alerts summary
- errors summary
- managed-service diagnostics summary
- investigation links and pivots
- assistant-facing evidence bundles

These APIs should read mostly from platform-native records and async projections, not from live multi-provider fan-out per request.

## 8) Relationship to Observability

Observability foundation provides:

- logs
- metrics
- traces
- error events
- probes
- correlation identifiers

The federated diagnostic control plane provides:

- summary and interpretation
- normalized operational truth
- permission-aware navigation
- governed remediation
- assistant-ready evidence bundles

The platform depends on observability, but it is not the same thing as the telemetry backend.

## 9) Relationship to the Assistant

The assistant should use the federated diagnostic control plane as its investigation substrate.

That means:

- ask platform investigation APIs first
- summarize normalized evidence first
- cite and deep-link to deterministic platform surfaces
- pivot into raw telemetry only when needed and permitted

The assistant should not:

- invent a second diagnostics model
- answer live operational questions from planning docs when live state exists
- become the only way to investigate incidents

## 10) Recommended Rollout

### Phase 1: Normalize current truth

- standardize current verification and diagnostics payloads around canonical identifiers
- define the investigation summary contract
- define deep-link patterns between verification, diagnostics, and platform diagnostics

### Phase 2: Land the telemetry substrate

- land the shared observability starter
- standardize correlation fields across backend, runtime, and bridge
- begin alert and error summary ingestion

### Phase 3: Materialize the platform eye

- add async summary projections
- add correlated alert and error cards
- add managed-service blast-radius summaries
- add raw-evidence pivots

### Phase 4: Assistant-ready investigation APIs

- expose normalized evidence bundles
- teach the assistant to summarize, cite, deep-link, and propose governed actions

## 11) Completion Criteria

This direction is complete when:

- operators start investigations in the platform by default
- the platform can summarize health, release, verification, drift, remediation, alerts, and recent errors from one place
- managed-service investigation no longer requires opening several disconnected tools first
- raw telemetry remains available through governed pivots
- the assistant investigates from the same normalized platform model
- the platform remains a federated control plane rather than an accidental custom observability backend

## 12) Recommendation

The right move is:

- make the platform the federated diagnostic control plane
- make it the all-in-one platform eye for admins and later the assistant
- keep summaries async and fast
- keep raw telemetry storage managed and external
