# Managed Services Federated Diagnostic Adapter Contract

Status: planning document (2026-04-22)

Purpose:

- define the minimum and preferred diagnostic integration points managed services must expose to the platform
- make the federated diagnostic control plane implementable without requiring every service to become its own observability product
- keep investigation fast by favoring bounded summaries, event feeds, and pivots over raw synchronous fan-out

Read this with:

- `PLATFORM_FEDERATED_DIAGNOSTIC_CONTROL_PLANE_PLAN.md`
- `../../Productization/future-work/PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`
- `../observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md`
- `../observability/LOOMAI_OBSERVABILITY_STARTER_IMPLEMENTATION_PLAN.md`

## 1) Executive Summary

Managed services do not need to expose every raw telemetry primitive directly to the platform.

They do need to expose a small, stable adapter contract that gives the platform:

- identity
- health and drift posture
- recent activity or event feed
- bounded logs or log pivots
- dependency and blast-radius visibility
- diagnostics summary
- governed repair actions
- correlation identifiers

The platform then:

- normalizes
- correlates
- permissions
- materializes summaries
- and navigates

This is the right shape for both:

- admin all-in-one investigation
- later assistant investigation

## 2) Core Rule

The platform needs **diagnostic adapters**, not bespoke per-service investigation logic.

The contract should be:

- thin
- bounded
- correlation-friendly
- safe under load
- compatible with async summary materialization

## 3) Contract Sections

### 3.1 Identity contract

Every managed service should expose:

- `serviceRef`
- `displayName`
- `serviceKind`
- `deploymentMode`
- `environmentScope`
- `deploymentId` when applicable
- provider-native identifiers when applicable
- `baseUrl`
- `privateNetworkUrl`
- `healthPath`
- `status`

### 3.2 Health contract

Every managed service should expose:

- current `status`
- `lastHealthyAt`
- `lastProbeAt`
- `lastSuccessfulProbeAt`
- `lastFailedProbeAt`
- `lastProbeStatus`
- `lastProbeMessage`
- one or more probe summaries with:
  - `key`
  - `label`
  - `status`
  - `endpoint`
  - `method`
  - `message`
  - `checkedAt`

### 3.3 Drift and reconcile contract

Every managed service should expose:

- `driftStatus`
- `driftMessage`
- `lastReconciledAt`
- `lastReconcileStatus`
- `lastReconcileMessage`
- `lastVerifiedOperation`
- `lastVerifiedAt`
- `lastVerifiedStatus`
- `lastVerifiedMessage`

### 3.4 Activity and event contract

Every managed service should expose a recent activity or event feed with:

- timestamp
- event type
- summary message
- actor or source when available
- correlation identifiers where known

This feed should be suitable for async projection into the platform eye.

### 3.5 Bounded logs contract

Every managed service should expose either:

- bounded recent logs directly

or

- a safe logs summary plus a deep-link or pivot to raw logs

Required query controls:

- `limit`
- `filter`
- `startDate`
- `endDate`
- optional release or deployment scoping

### 3.6 Dependency and blast-radius contract

Every managed service should expose:

- dependent deployment count or dependent store count
- active dependent count
- dependents list where safe and useful
- basic usage classification if available

### 3.7 Diagnostics summary contract

Every managed service should expose a normalized diagnostics summary with:

- `status`
- `summaryMessage`
- current risk posture
- key signals
- key dependency context
- recommended next action when one exists
- deep links or pivots where useful

This summary is optimized for operator investigation and assistant summarization, not raw telemetry storage.

### 3.8 Correlation contract

Every adapter payload should carry as many of the following as are known:

- `deploymentId`
- `releaseId`
- `verificationRunId`
- `serviceRef`
- `tenantId`
- `merchantId`
- `requestId`
- `traceId`
- provider deployment id when relevant

### 3.9 Governed actions contract

Every managed service should expose only actions that are explicitly safe and supported.

Examples:

- `reconcile`
- `restart`
- `force-recreate`
- `scale`
- `rotate-secret`
- `decommission`

Each action remains:

- permission-checked
- auditable
- confirmation-aware where required

### 3.10 Async materialization rule

The adapter should support fast platform investigation by allowing:

- event-based refreshes when service state changes
- scheduled refreshes for summary views where events are not available
- summary reads without forcing synchronous fan-out to every provider on every page render

The platform should be able to materialize a read model from adapter outputs instead of rebuilding every summary live.

## 4) Recommended Endpoint Families

### 4.1 Required read endpoints

- `GET /{service}/summary`
- `GET /{service}/health`
- `GET /{service}/activity`
- `GET /{service}/dependents`
- `GET /{service}/diagnostics-summary`

### 4.2 Strongly recommended read endpoints

- `GET /{service}/logs`
- `GET /{service}/overview`
- `GET /{service}/deployment-history`
- `GET /{service}/errors-summary`
- `GET /{service}/alerts-summary`
- `GET /{service}/metrics-summary`
- `GET /{service}/trace-pivots`

### 4.3 Required action endpoints

- `POST /{service}/reconcile`

### 4.4 Important optional action endpoints

- `POST /{service}/restart`
- `POST /{service}/force-recreate`
- `PUT /{service}/scale`
- `PUT /{service}/rotate-secret`
- `POST /{service}/decommission`

## 5) Preferred DTO Families

### 5.1 Summary DTO

Contains:

- identity block
- health block
- drift block
- dependency block
- correlation block

### 5.2 Diagnostics Summary DTO

Contains:

- `status`
- `summaryMessage`
- `keySignals`
- `recommendedActions`
- `deepLinks`
- `correlation`

### 5.3 Logs DTO

Contains:

- `available`
- `message`
- provider or service identifiers
- request parameters used
- `queriedAt`
- bounded log entries or pivots

### 5.4 Errors and alerts summary DTO

Contains:

- `available`
- `status`
- top issue groups or alerts
- counts
- last-seen timestamps
- deep links
- correlation identifiers where known

For early phases, these summaries may be generated by the platform over external sinks instead of being owned by each service.

## 6) Current Coverage Matrix

Legend:

- `Implemented`
- `Partial`
- `Missing`

| Capability | Managed inference services | Managed product services | Runtime-backed deployment diagnostics |
|---|---|---|---|
| Identity summary | Implemented | Implemented | Partial |
| Health summary | Implemented | Implemented | Partial |
| Drift and reconcile summary | Implemented | Implemented | Partial |
| Activity or event feed | Implemented | Implemented | Implemented |
| Dependents or blast radius | Implemented | Implemented | Partial |
| Bounded logs | Missing | Implemented | Implemented |
| Diagnostics summary | Partial | Partial | Partial |
| Deployment history | Missing | Implemented | Implemented via releases |
| Error summary | Missing | Missing | Missing |
| Alert / SLO summary | Missing | Missing | Missing |
| Metrics summary | Missing | Missing | Missing |
| Trace pivots | Missing | Missing | Partial |
| Governed actions | Implemented | Implemented | Implemented across related routes |

## 7) Interpretation of Current State

### 7.1 Managed inference services

Strong on:

- summary
- health
- dependents
- activity
- repair actions

Weak on:

- bounded logs
- diagnostics summary
- errors summary
- alert summary
- trace pivots

### 7.2 Managed product services

Strong on:

- summary
- health
- overview
- dependents
- activity
- deployment history
- bounded Railway logs
- governed actions

Weak on:

- normalized errors summary
- normalized alerts summary
- normalized metrics summary
- trace pivots

### 7.3 Runtime-backed deployment diagnostics

Strong on:

- releases
- verification runs
- hosted verification
- bounded logs
- source-of-truth and integration views
- repair and recheck flows

Weak on:

- one explicit adapter-shaped diagnostics summary endpoint
- normalized alerts summary
- normalized errors summary
- reusable trace pivots

## 8) Minimum Integration Points Needed

For each managed service family, the minimum useful contract is:

1. one summary endpoint
2. one health endpoint
3. one activity or event endpoint
4. one dependents endpoint
5. one diagnostics-summary endpoint
6. one bounded logs endpoint or logs-summary-plus-pivot endpoint
7. one reconcile endpoint
8. correlation fields in all of the above

That is enough for:

- platform admin investigation
- assistant evidence-grounded summarization

## 9) Recommended Next Steps

1. formalize reusable internal DTOs for summary, diagnostics summary, logs, errors summary, and alerts summary
2. add the missing diagnostics-summary shape for runtime-backed deployment diagnostics
3. add bounded logs and diagnostics-summary support for managed inference services
4. review correlation fields across all managed-service payloads
5. keep alerts and error summaries platform-generated where that avoids duplicating sink-specific logic in every service

## 10) Recommendation

The right move is:

- standardize this adapter contract now
- keep it small, stable, and async-friendly
- fill the missing logs and diagnostics-summary gaps first
- let the platform layer materialize the all-in-one investigation views on top of it
