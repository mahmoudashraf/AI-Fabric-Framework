# Plan: Shopify Control and Execution Plane Separation

Status: proposed implementation plan (2026-04-23)

This document defines how Shopify action execution should evolve so the framework keeps a **single authoritative Shopify control boundary** without making one deployable service instance the scalability bottleneck or single point of overload risk.

This is a **framework and product-service architecture plan**, not a storefront-only note.

---

## 1) Executive Decision

We should keep Shopify behind a **shared logical bridge boundary**, but split responsibilities into:

- a **Shopify control plane**
- a **Shopify action execution plane**
- a **Shopify sync / background worker plane**

The correct target is:

- **one logical Shopify boundary**
- **multiple scalable deployable components behind that boundary when needed**

We should **not** move to:

- runtime calling Shopify Admin API directly
- platform backend calling Shopify Admin API directly for normal store actions
- deployment-scoped connectors holding Shopify credentials and bypassing shared policy
- one bridge clone per AI deployment by default

### Critical correction

Deployment-scoped action configuration is good.

Deployment-scoped direct Shopify API execution is not.

The right split is:

- **deployment-scoped connector / action config** = which Shopify capabilities a deployment may request
- **Shopify control plane** = authority for store binding, auth, entitlement, policy, audit, rate limits, and execution approval
- **Shopify execution plane** = horizontally scaled stateless workers that actually perform approved Shopify API calls

This keeps the framework aligned with the product and platform philosophy:

- strong product boundary
- centralized governance
- scalable execution
- fail-closed security

---

## 2) Problem Statement

The current Shopify product shape is directionally correct, but it risks being misunderstood in two opposite ways:

1. **“Everything should stay in one bridge service forever.”**
   - This creates a real overload and operational concentration risk as usage grows.

2. **“Connectors should just call Shopify directly.”**
   - This removes the clean Shopify boundary and duplicates the most sensitive logic across the platform.

The architecture needs to solve both:

- keep Shopify-specific control in one authoritative boundary
- avoid making one deployable service instance the only execution bottleneck

---

## 3) Code-Validated Current State

The current codebase already establishes Shopify as a **shared managed product service**, not a per-deployment connector toy.

### 3.1 What is already real

#### Store mappings bind to a Shopify managed product service

Platform store mappings require a Shopify managed product service and carry a `productServiceRef` / product-service binding through:

- `ShopifyStoreConnectionService`
- `UpsertShopifyStoreConnectionRequest`
- `ShopifyStoreConnectionSummary`

In `ShopifyStoreConnectionService`, the platform rejects non-Shopify product services for store mappings and persists a shared managed-service binding.

#### Platform already talks to Shopify through the bridge boundary

Platform-to-Shopify admin/sync/vectorization interactions already go through bridge clients such as:

- `ShopifyBridgeAdminClient`
- `PlatformManagedProductAdminService`

These clients resolve the managed service, fetch the bridge admin secret, and call bridge-owned admin endpoints instead of speaking Shopify directly.

#### Bridge already owns Shopify action execution

Bridge action execution is already explicitly hosted inside the Shopify bridge service through:

- `ShopifyBridgeActionsController`
- `ShopifyBridgeActionExecutionService`

Today the action endpoint is:

- `POST /api/admin/stores/{shopDomain}/actions/execute`

That is already the correct logical execution boundary.

#### Bridge already owns Shopify-specific concerns beyond actions

The bridge already owns or strongly participates in:

- install lifecycle
- store lifecycle
- billing / entitlements
- sync and vectorization source exposure
- merchant UI / storefront bootstrap
- Shopify Admin GraphQL calls

Representative code:

- `ShopifyBridgeStoreLifecycleService`
- `ShopifyBridgeStoreAdminService`
- `ShopifyBridgeStoreSyncService`
- `ShopifyBridgeBillingService`
- `ShopifyAdminGraphqlClient`

#### The documented live product shape is one shared bridge service

The internal deployment guide documents a single shared production bridge:

- base URL `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app`
- canonical managed service ref `shopify-bridge-prod`

That aligns with the current platform model.

### 3.2 What is not yet separated enough

The current architecture still tends to collapse these concerns into one deployable bridge runtime:

- policy authority
- approval logic
- Shopify API execution
- background sync work
- webhook handling

That is acceptable for early production, but it is not the best long-term scaling posture.

---

## 4) Why Direct Connector-to-Shopify Execution Is the Wrong Default

The framework already supports configurable actions.

That does **not** mean Shopify execution should bypass the Shopify boundary.

### 4.1 Auth and install state are Shopify-product concerns

Shopify requires:

- installed-store awareness
- shop-scoped token ownership
- token rotation / invalidation handling
- uninstall awareness
- app-review-safe request posture

These are not generic connector details. They are product-specific control-plane concerns.

### 4.2 Entitlements and safety need one authority

The Shopify product already has real tiering and surface entitlements.

Write/read decisions also need one place for:

- tier gating
- confirmation policy
- safe defaults
- action allowlists
- audit identity

If direct connectors speak Shopify, these rules fragment.

### 4.3 Webhook and sync causality needs one boundary

Shopify writes and reads affect:

- sync state
- vectorization state
- merchant readiness
- analytics and investigation

If actions bypass the bridge, the causal story becomes fragmented:

- webhook state says one thing
- runtime action path says another
- sync/vectorization path has to infer what happened

### 4.4 Normalized results are part of the product contract

The product should expose normalized capabilities to the framework.

It should not force every deployment or runtime path to understand raw Shopify response shapes.

### 4.5 Rate-limit and retry discipline must stay centralized

Shopify rate-limit handling, backoff, retry posture, and error normalization should be controlled in one product boundary, not copied into many execution paths.

### 4.6 Auditability becomes weak if execution fragments

For Shopify actions we need one reliable answer to:

- who requested the action
- under which deployment
- against which store
- with which entitlement
- under which approval / confirmation posture
- what Shopify result came back

That is much harder if direct connector execution is allowed to fan out arbitrarily.

---

## 5) Target Architecture

### 5.1 Core principle

Keep **one logical Shopify control boundary**, but split deployable responsibilities.

### 5.2 Recommended component model

#### Shopify Control Plane

The Shopify control plane remains authoritative for:

- store binding and deployment-to-store authorization
- install state and token ownership
- entitlement / billing evaluation
- read vs write policy
- confirmation / approval rules
- per-shop concurrency budgets
- rate-limit budgeting and backpressure decisions
- audit, idempotency, and execution records
- webhook intake ownership
- issuance of execution requests / grants to workers

#### Shopify Action Execution Plane

The Shopify action execution plane should be:

- stateless
- horizontally scalable
- safe to run as multiple replicas
- focused on bounded Shopify API execution

It should perform:

- approved read actions
- approved write actions
- normalized result shaping
- bounded retries / transport recovery

It should **not** become the authority for:

- long-lived token ownership rules
- entitlement evaluation
- store binding decisions
- confirmation approval policy

#### Shopify Sync / Background Worker Plane

The background worker plane should own:

- bulk sync
- vectorization-source refresh
- webhook-driven repair flows
- long-running reconciliation
- analytics materialization

This keeps heavy background work away from the interactive action path.

### 5.3 Deployment-scoped connector model

Deployment-scoped connectors are still useful, but only for:

- deployment-specific action catalog exposure
- deployment-specific allowlists
- deployment-specific prompt / mode behavior
- deployment-specific parameter defaults and UX

They should **not** own:

- Shopify credentials
- direct Shopify Admin API access
- store-level entitlement authority
- cross-store policy

The connector should request a Shopify capability from the Shopify boundary, not perform the raw Shopify call itself.

---

## 6) Request Flows

### 6.1 Read action flow

1. Runtime resolves that a Shopify read action is relevant.
2. Deployment-scoped connector builds the requested Shopify capability call.
3. Shopify control plane validates:
   - deployment may access this store
   - action is allowed
   - tier allows it
   - request fits policy and budgets
4. Control plane dispatches execution to the action execution plane.
5. Execution plane calls Shopify and returns a normalized result.
6. Control plane records audit / execution facts.
7. Normalized result returns to runtime / LLM.

### 6.2 Write action flow

1. Runtime resolves a Shopify write action.
2. Deployment-scoped connector submits the request.
3. Shopify control plane validates:
   - deployment/store binding
   - write-action entitlement
   - confirmation / approval state
   - idempotency key
   - budget and safety posture
4. Control plane dispatches the approved write request.
5. Execution plane performs the write.
6. Control plane records execution and emits downstream state repair or refresh triggers as needed.
7. Result returns in normalized form.

### 6.3 Webhook / sync interaction flow

1. Shopify emits webhook.
2. Shopify control plane ingests and validates it.
3. Control plane decides:
   - immediate state update
   - background sync enqueue
   - vectorization enqueue
4. Background worker plane performs the heavy work.

This preserves one Shopify source of truth for store state changes.

---

## 7) Security Model

### 7.1 Credentials

Long-lived Shopify credentials should remain owned by the Shopify boundary.

They should not be copied into:

- runtime deployments
- generic connector configs
- browser clients

### 7.2 Authorization

The control plane should evaluate:

- which deployment is asking
- which store is targeted
- whether the deployment is authorized for that store
- whether the current plan allows that action class

### 7.3 Idempotency and replay safety

Every write-class execution request should support:

- idempotency keys
- execution status tracking
- safe retry semantics

### 7.4 Fail-closed behavior

If the control plane cannot validate:

- store binding
- entitlement
- secret availability
- approval state

the request must fail closed before reaching Shopify.

---

## 8) Reliability and Overload Mitigation

### 8.1 The real risk

The risk is not the existence of a Shopify boundary.

The risk is letting one deployable bridge process own:

- interactive requests
- webhook intake
- background sync
- all execution

### 8.2 Required mitigations

To avoid overload:

- keep action execution stateless
- scale execution replicas horizontally
- isolate background sync from interactive execution
- enforce per-shop concurrency limits
- use queues for heavy or bursty work
- keep read latency budgets separate from sync budgets
- implement circuit breakers and backpressure for Shopify degradation

### 8.3 Recommended execution classes

Split execution into at least three classes:

- `interactive-read`
- `interactive-write`
- `background-sync`

This avoids one noisy lane starving the others.

---

## 9) Non-Goals and Anti-Patterns

We should explicitly avoid:

- one bridge clone per AI deployment by default
- direct generic connector access to Shopify Admin API
- browser-side Shopify Admin API execution
- connector-managed Shopify token refresh
- hiding Shopify execution policy inside prompt-only behavior
- letting the LLM decide whether a write is allowed

### Important nuance

This plan does **not** say every Shopify-related HTTP endpoint must be served by one runtime forever.

It says the **authority** must stay logically centralized even if deployable responsibilities split.

---

## 10) Optional Future Variant: Delegated Execution Grants

If we later need looser coupling between control and execution, the safe advanced option is:

- control plane issues short-lived signed execution grants
- execution plane uses that grant for one bounded Shopify action
- grant encodes:
  - store
  - deployment
  - action name
  - access class
  - expiry
  - idempotency key

This can reduce synchronous coupling while keeping control centralized.

Even in that model:

- long-lived Shopify credentials should still remain under Shopify boundary ownership
- execution grants should be bounded and short-lived

---

## 11) Recommended Rollout Phases

### Phase 1 — Keep current boundary, formalize the rule

- Declare Shopify bridge as the only supported Shopify execution boundary.
- Keep current shared bridge path for actions.
- Prevent new direct-to-Shopify connector shortcuts.

### Phase 2 — Separate interactive execution from background work

- Split background sync/vectorization workers from the interactive action runtime.
- Keep one shared control plane.

### Phase 3 — Introduce execution-plane scaling

- Move approved action execution to horizontally scaled stateless workers.
- Keep control-plane approval and audit centralized.

### Phase 4 — Add stronger execution governance

- add execution records
- add idempotency contracts
- add per-shop rate budgets
- add circuit-breaking / degraded-mode behavior

### Phase 5 — Optional delegated execution grants

- only if needed for scale or topology flexibility
- not before the simpler split is proven

---

## 12) Relationship to Framework Action Planning

This plan is complementary to:

- `PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md`

That document defines how the framework should plan and use read-only actions.

This document defines how Shopify action execution should be hosted safely and scalably once a Shopify action is selected.

The important boundary is:

- **framework / LLM planner** may choose a Shopify capability
- **Shopify control plane** decides whether and how that capability is actually executed

---

## 13) Final Recommendation

The framework should adopt this as the standard Shopify posture:

- **shared Shopify control plane**
- **scalable Shopify execution plane**
- **separate background worker plane**
- **deployment-scoped action configuration, not deployment-scoped raw Shopify execution**

That gives us:

- clean product boundaries
- strong governance
- scalable execution
- better failure isolation
- no need to duplicate Shopify auth/policy logic across the framework

This is the enterprise-ready path.
