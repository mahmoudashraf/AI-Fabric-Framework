# Marketplace Shared Inference Service Admin UI Plan

Status: implementation-baseline plan (2026-04-16)

This document defines the production-grade admin and UI management surface for platform-managed inference services used by marketplace `INFERENCE_PROFILE` plugins.

It sits on top of:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_SHARED_INFERENCE_SERVICE_PLATFORM_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_INFERENCE_PROFILE_PRODUCTIZATION_PLAN.md`

This plan is about operator control and production operations, not plugin contract changes.

The marketplace plugin contract stays the same:

- public plugin type remains `INFERENCE_PROFILE`
- installs still compile into deployment `providerConfig`
- runtime still consumes resolved provider configuration only

The problem this plan solves is operational:

- when shared inference services fail, drift, scale badly, or need secret changes, admins need a complete UI and API surface to manage them safely in production

---

## 1) Executive Summary

The current platform already supports:

- listing managed inference services
- reconciling them
- scaling desired replicas
- using them through marketplace installs

That is enough for engineering-operated validation and early controlled rollout.

It is not enough for production operations because the UI still lacks:

- force recreate
- secret rotation workflows
- dependency-aware safety controls
- health, drift, and recent activity visibility
- deployment impact visibility
- clear failure-recovery flows

Required outcome:

- platform admins can operate shared inference services from the UI without code changes or direct Railway intervention for normal production incidents

This does not require full arbitrary service authoring on day 1.

For the first production slice, the priority is:

- robust operations UI for seeded first-party services
- not open-ended service-definition editing

---

## 2) Scope

This plan covers:

- `SHARED_EMBEDDING_SERVICE`
- `SHARED_OLLAMA_SERVICE`
- `BUNDLED_ONNX_SERVICE` visibility where relevant
- deployment-dedicated embedding workers where they surface as managed services

This plan does not require:

- arbitrary third-party model-server authoring by admins
- marketplace plugin taxonomy changes
- runtime plugin loading
- new non-marketplace infrastructure surfaces

---

## 3) Current State

### 3.1 What exists now

Current UI and API support:

- list services
- inspect summary state
- reconcile
- scale desired replicas
- open service base URL

Current service summary already exposes useful fields:

- `serviceRef`
- `displayName`
- `serviceKind`
- `deploymentMode`
- `providerType`
- `modelId`
- `desiredReplicas`
- `actualReplicas`
- `minReplicas`
- `maxReplicas`
- `autoscalingMode`
- `baseUrl`
- `privateNetworkUrl`
- `healthPath`
- `secretName`
- `status`
- endpoint summaries

### 3.2 What is missing

The current UI is not sufficient for production operations because it cannot:

- force a clean recreate
- show dependent deployments
- show last healthy time
- show last reconcile result
- show failure reason or drift detail
- show recent deployment or probe history
- rotate service secrets
- validate secret presence before reconcile
- show logs and Railway linkage cleanly
- distinguish safe restart vs destructive recreate
- protect unsafe actions when active deployments depend on the service

---

## 4) Product Goal

The production admin surface must let an operator answer these questions quickly:

1. What services exist and which ones are unhealthy?
2. Which deployments depend on a service?
3. Is the service actually serving the intended endpoint and model?
4. Did the last reconcile succeed or fail?
5. Is the issue configuration drift, Railway drift, secret drift, or runtime readiness?
6. Can I restart, scale, rotate, or recreate this service safely from the UI?
7. What operator action happened last, and who did it?

If the UI cannot answer those questions, it is not production-ready.

---

## 5) Recommended UI Shape

### 5.1 Separate admin surface

Do not keep production service operations buried only inside the general marketplace install page.

Add a dedicated top-level admin page:

- `Inference Services`

The existing marketplace page may keep a compact summary card, but the full operational workflow should move to a dedicated page.

### 5.2 Main screens

Required screens:

1. `Inference Services List`
2. `Inference Service Detail`
3. `Inference Service Activity`
4. `Dependent Deployments`
5. `Service Recovery Dialogs`
6. `Secret Rotation Dialog`

### 5.3 Service list page

Each row or card should show:

- display name
- service ref
- service kind
- environment or tier scope
- status
- desired replicas
- actual replicas
- last healthy timestamp
- last reconcile timestamp
- number of dependent deployments
- primary endpoint profile refs
- primary model id

List controls:

- filter by status
- filter by environment
- filter by kind
- filter by provider
- sort by health, dependents, or recent activity
- quick actions:
  - reconcile
  - scale
  - open details

### 5.4 Service detail page

This is the main production operations view.

It should show:

- identity:
  - service ref
  - display name
  - service kind
  - deployment mode
  - environment scope
  - tier scope
- current runtime state:
  - status
  - base URL
  - private network URL
  - health path
  - provider type
  - protocol type
  - model id
  - embedding dimensions where relevant
- Railway linkage:
  - project id
  - environment id
  - service id
  - last deployment id
  - direct Railway links
- scale state:
  - desired replicas
  - actual replicas
  - min replicas
  - max replicas
  - autoscaling mode
- endpoint profiles exposed by the service
- dependency summary:
  - dependent deployments
  - dependent published versions
  - active marketplace installs using the service
- recent probes:
  - health probe
  - authenticated inference probe
  - last successful probe time
  - last failed probe time
  - last error message
- secret state:
  - expected secret name
  - secret configured yes/no
  - last rotated time

### 5.5 Recovery and lifecycle actions

Service detail must expose explicit actions:

- `Reconcile`
- `Restart deployment`
- `Scale`
- `Rotate secret`
- `Force recreate`
- `Suspend` or `Disable` if needed later

These actions must not all behave the same.

Definitions:

- `Reconcile`
  - reapply desired state to existing Railway resources
- `Restart deployment`
  - trigger a fresh deploy without clearing resource linkage
- `Force recreate`
  - destroy and recreate Railway service linkage cleanly
  - keep service ref stable
  - update endpoint bindings after recreation

### 5.6 Guardrails in UI

Before destructive or risky actions, show:

- active dependent deployments count
- current health state
- whether the action will change endpoint base URL
- whether secret rotation is also required
- whether deployments will need re-probe or re-verify

Required confirmations:

- `Force recreate` requires typed confirmation
- destructive actions should explain blast radius in plain language

---

## 6) API And Backend Additions

### 6.1 Existing APIs to keep

Keep:

- `GET /api/marketplace/inference-services`
- `GET /api/marketplace/inference-services/{serviceRef}`
- `POST /api/marketplace/inference-services/{serviceRef}/reconcile`
- `PUT /api/marketplace/inference-services/{serviceRef}/scale`

### 6.2 Required new APIs

Add:

- `GET /api/marketplace/inference-services/{serviceRef}/dependents`
- `GET /api/marketplace/inference-services/{serviceRef}/activity`
- `GET /api/marketplace/inference-services/{serviceRef}/health`
- `POST /api/marketplace/inference-services/{serviceRef}/restart`
- `POST /api/marketplace/inference-services/{serviceRef}/force-recreate`
- `POST /api/marketplace/inference-services/{serviceRef}/rotate-secret`
- `GET /api/marketplace/inference-services/{serviceRef}/railway-links`

Optional later:

- `POST /api/marketplace/inference-services/{serviceRef}/disable`
- `POST /api/marketplace/inference-services/{serviceRef}/enable`

### 6.3 Required backend summary additions

The service summary and detail models should include:

- `lastHealthyAt`
- `lastProbeAt`
- `lastSuccessfulProbeAt`
- `lastFailedProbeAt`
- `lastReconciledAt`
- `lastReconcileStatus`
- `lastReconcileMessage`
- `lastDeploymentId`
- `dependentDeploymentsCount`
- `dependentActiveDeploymentsCount`
- `secretConfigured`
- `secretRotatedAt`
- `driftStatus`
- `driftMessage`
- `railwayDashboardUrl`

---

## 7) Health, Drift, And Verification

### 7.1 Health model

The UI must distinguish at least four different health layers:

1. `Control-plane state`
   - platform believes service is active
2. `Railway deployment state`
   - Railway deployment succeeded
3. `Endpoint health`
   - health endpoint responds correctly
4. `Authenticated inference readiness`
   - real inference probe succeeds with current auth and model

These should not collapse into one status badge.

### 7.2 Drift model

The platform must explicitly surface drift categories:

- `NO_DRIFT`
- `CONFIG_DRIFT`
- `SECRET_DRIFT`
- `RAILWAY_RESOURCE_DRIFT`
- `ENDPOINT_DRIFT`
- `SCALE_DRIFT`

Examples:

- platform expects one replica but Railway shows zero
- service secret exists in platform but not in Railway env
- endpoint base URL no longer matches current domain
- expected model id differs from live probe metadata

### 7.3 Verification panel

Service detail should show:

- last health probe result
- last authenticated LLM probe result
- last authenticated embedding probe result
- last hosted verification run linked to the service

For shared services, production should not rely on manual curl checks.

---

## 8) Dependency Awareness

### 8.1 Why this is required

Operators cannot safely restart or recreate a shared service without seeing who depends on it.

### 8.2 Required dependency views

Each managed service should show:

- deployments using it in current draft
- deployments using it in published version
- deployments currently active against it
- marketplace installs that resolved to this service

Each dependency record should show:

- deployment id
- deployment name
- environment
- active version
- active release status
- purpose:
  - orchestration
  - generation
  - embedding

### 8.3 Action safety

If active dependencies exist, `Force recreate` should require:

- warning banner
- blast-radius summary
- confirmation text

Optional later:

- maintenance mode flow
- batched deployment re-verification after service recovery

---

## 9) Secret Management

### 9.1 Required operator capabilities

Admins need to know:

- whether the expected secret exists
- whether the service env and endpoint probe are using a working secret
- when it was last rotated

### 9.2 Secret rotation flow

Recommended flow:

1. open `Rotate secret`
2. create or update the platform-managed secret value
3. reconcile the managed service
4. run an authenticated probe immediately
5. persist rotation metadata and audit trail

The UI should not expose raw secret values after submission.

### 9.3 Secret failure states

The detail page should explicitly surface:

- secret missing
- secret configured but inference probe unauthorized
- secret drift between platform and Railway env

---

## 10) Recovery Model

### 10.1 Reconcile

Use when:

- config drift exists
- env vars need to be reapplied
- Railway linkage exists but is stale
- replicas need to be corrected

### 10.2 Restart deployment

Use when:

- service resource definition is correct
- latest deploy is unhealthy or stuck
- no clean rebuild of identity is needed

### 10.3 Force recreate

Use when:

- Railway service or project is corrupted
- domain or service linkage is broken
- repeated reconcile fails
- operator intentionally wants a clean rebuild

Expected behavior:

- delete or detach old Railway resources
- clear stale linkage safely
- recreate project, service, env, domain, env vars
- update platform records
- rerun authenticated probe

### 10.4 Post-recovery verification

After any recovery action, the platform should automatically run:

- health endpoint probe
- authenticated inference probe
- dependency-impact summary refresh

Implemented baseline:

- `reconcile`
- `scale`
- `restart`
- `force recreate`
- `rotate secret`

all trigger an automatic post-action verification pass and persist the latest verified action, verification timestamp, and verification status for the service summary and detail view.

---

## 11) Scaling And Capacity UX

### 11.1 Minimum production scale controls

The existing desired-replica input is not enough by itself.

The UI should show:

- desired replicas
- actual replicas
- last scale action
- scale actor
- recommended scale guidance
- service saturation hint if available later

### 11.2 Scaling guardrails

The platform should enforce:

- positive integer validation
- min/max bounds
- clear error on Railway refusal
- drift warning if actual replicas do not converge

### 11.3 Later capacity enhancements

Safe later work:

- request-rate metrics
- queue depth metrics
- autoscaling policy editor

Not required for first production slice.

---

## 12) Audit And Activity History

### 12.1 Required audit events

Track at minimum:

- reconciled
- scaled
- restarted
- force recreated
- secret rotated
- probe failed
- probe recovered

### 12.2 Activity view

The UI should show:

- timestamp
- actor
- action type
- target service
- result
- concise message
- linked Railway deployment id when applicable

This is required for serious operations and incident review.

---

## 13) Create And Edit Authoring

### 13.1 Recommendation

Do not make full arbitrary service authoring the first production blocker.

For the first production slice, keep:

- service definitions mostly code- or seed-driven
- UI focused on operational control

### 13.2 When to add authoring

Add create and edit authoring only when:

- first-party service model is stable
- multiple environment variants are common
- admins genuinely need self-serve service creation

### 13.3 Later authoring scope

When authoring is added, support:

- display name
- model id
- service kind
- environment scope
- tier scope
- replica defaults
- health path
- endpoint purpose mappings
- secret name

Do not expose arbitrary build-root or Dockerfile editing in UI.

Those remain engineering-controlled.

---

## 14) Recommended Delivery Waves

### Wave 0: Visibility baseline

Deliver:

- dedicated `Inference Services` admin page
- richer list view
- detail view
- last healthy / last reconcile / last failure
- direct Railway links
- dependency counts

Acceptance:

- an operator can identify unhealthy services and dependent deployments without leaving the platform

### Wave 1: Recovery controls

Deliver:

- restart
- force recreate
- richer reconcile result view
- guarded destructive actions

Acceptance:

- an operator can recover a failed shared service from UI without direct Railway usage for normal incidents

### Wave 2: Secret management

Deliver:

- secret presence status
- rotate secret flow
- post-rotation authenticated probe
- secret drift detection

Acceptance:

- a stale or missing service secret is visible and recoverable from UI

### Wave 3: Health and drift operations

Deliver:

- explicit drift categories
- health panel
- recent probe history
- activity timeline

Acceptance:

- operators can distinguish config drift, Railway drift, auth drift, and service readiness failures

### Wave 4: Dependency-safe production operations

Deliver:

- dependent deployments table
- blast-radius previews
- post-recovery dependency refresh
- optional batch re-verification triggers

Acceptance:

- shared service actions are safe and understandable when many deployments depend on one service

### Wave 5: Full authoring if still needed

Deliver only if justified:

- create new service
- edit service metadata
- edit endpoint purpose mappings

Acceptance:

- admins can define new service instances without code changes while still respecting engineering guardrails

---

## 15) Production Readiness Criteria

The admin UI should be considered production-ready only when all of the following are true:

- unhealthy services are visible without inspecting Railway directly
- operators can reconcile, restart, scale, and force recreate from UI
- operators can see dependent deployments before risky actions
- operators can rotate secrets from UI
- authenticated probe status is visible and trustworthy
- activity history exists for operational actions
- Railway and platform drift is visible and categorized

If those are not true, shared inference services are still engineering-operated, not admin-operated.

---

## 16) Recommended Immediate Sequence

The next implementation order should be:

1. dedicated admin page and richer detail model
2. dependency and health APIs
3. restart and force-recreate flows
4. secret rotation flow
5. activity history and drift model
6. optional full service authoring later

This sequence gets the platform to real production operations with the smallest amount of risky UI surface area.
