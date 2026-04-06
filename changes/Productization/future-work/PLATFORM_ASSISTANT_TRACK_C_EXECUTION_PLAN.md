# Platform Assistant Track C Execution Plan

Status: detailed execution plan (2026-04-06)

This document defines the concrete execution plan for Wave 4 Track C.

Sequencing clarification:

- the shared auth foundation should be built before Track C implementation starts
- the existing platform POC proxy should migrate onto that shared auth foundation before Track C reuses it as a reference interaction path
- Track C is expected to consume that completed auth foundation rather than define its own auth stack first
- assistant references to both auth modes in this document exist to keep Track C compatible with the auth work, not to make assistant delivery a prerequisite for auth delivery

Product-boundary clarification:

- Track C is the first first-party implementation of the assistant, but it should be built as a reference consumer of the platform rather than as a hidden privileged subsystem
- the assistant should remain architecturally compatible with being packaged later as a separate customer product that integrates with and uses the platform
- this means Track C must avoid platform-only shortcuts that would block later reuse of the same deployment, runtime, action, and auth contracts

It takes the broader direction from:

- `PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`
- `AI_ASSISTANT_PRODUCT_NORTH_STAR_AND_SCOPE.md`

and locks the first implementation around the current product reality and the latest execution decisions.

The goal is not to brainstorm every possible assistant surface.

The goal is to ship one real, platform-owned reference assistant path that:

- is created and healed as part of the platform itself
- uses the existing deployment model rather than a one-off backend
- routes assistant actions into the platform API
- appears as a first-party assistant chat page inside the platform UI
- remains bounded by the current authenticated user's permissions
- stays compatible with the same auth and deployment contracts that a separate customer-facing assistant product would use

---

## 1) Executive Summary

Track C should deliver a first-class platform assistant as a real deployment owned by the platform.

The first production shape should be:

- one platform-owned assistant deployment per environment
- bootstrap or reconcile semantics similar to the ecommerce demo deployment
- an assistant connector whose upstream system is the platform API
- an action-first assistant surface with bounded read and write platform actions
- a simple platform assistant chat page backed by the platform backend, not a browser-mounted external widget
- explicit current-user authorization proof for assistant actions
- phase-1 delivery in private platform-proxy auth mode
- explicit later support for public-runtime browser-token mode on the same auth foundation

Track C should therefore be read as:

- auth foundation first
- assistant implementation second

This is not a customer chatbot.

It is an operator and admin assistant for the platform itself in phase 1.

But it should be treated as:

- a first-party reference deployment
- a first-party reference UI
- a first-party reference consumer of the platform contracts

not as the only valid long-term assistant product shape.

---

## 2) Locked Decisions

The following points are locked for the first Track C execution pass.

### 2.1 Treat the assistant deployment as part of the platform

The assistant deployment is not optional sample data and not a user-created convenience deployment.

It should be treated like a platform-owned internal dependency, similar in operational posture to the ecommerce demo deployment:

- create it if it does not exist
- restore it if it is archived accidentally
- re-apply or recreate it if it is not up or running
- expose clear status in the platform UI and diagnostics

### 2.2 Use a dedicated assistant deployment path, not a hidden inline backend

Track C must use the normal deployment system:

- deployment template
- draft
- publish and apply
- release lifecycle
- verification
- diagnostics

The platform must dogfood its own deployment model.

This also keeps the implementation compatible with a later separately packaged assistant product, because the assistant remains a real deployment rather than a UI-only or backend-only exception.

### 2.3 The assistant is action-first in phase 1

The first Track C implementation should be:

- actions-first
- platform-API-backed
- read and write capable where the current user is allowed

This phase does not need to wait for a richer assistant retrieval corpus before shipping value.

The assistant can still answer grounded questions through platform API actions and deployment metadata, but the initial core is:

- ask a question
- route to bounded platform actions
- summarize the result in assistant form

### 2.4 Add a new curated module named `support`

The platform catalog currently only exposes these curated modules:

- `default`
- `commerce`

The repo also contains a filesystem path at:

- `ai-infrastructure-module/curated/ai-curated-support`

but that path is not currently wired into the platform curated catalog and is not yet a real checked-in curated pack in the same shape as the existing curated modules.

Track C should add:

- curated module id: `support`

This should become the assistant deployment baseline for prompt preset and runtime curated-pack metadata.

Track C must explicitly:

- restore or add real checked-in curated module content under `ai-curated-support`
- wire curated module id `support` into `DeploymentCuratedModuleCatalogService`
- align the runtime curated-pack metadata with the wired `ai-curated-support` implementation

### 2.5 Ship a simple platform assistant page first

The first Track C UI should be a simple first-party assistant page inside the platform, using the same practical send-and-receive posture already proven in the deployment POC console.

That reuse is about:

- interaction shape
- platform-backed proxy posture
- operator workflow

It must not copy the legacy POC synthetic runtime identity contract.

Track C should therefore assume the POC path has already been migrated onto the shared auth foundation before its chat proxy patterns are reused.

The first release should not depend on:

- external widget scripts
- browser-side connector credentials
- duplicate shell-mount logic

Recommended first surface:

- `Platfrom/ui/src/pages/AssistantPage.tsx`

Recommended shape:

- a plain chat thread
- suggestions and conversation reset
- optional deployment context when launched from a deployment workspace
- explicit permission-denied and approval-required responses

### 2.6 The browser should talk only to the platform backend

The browser must not call the assistant connector directly.

The safer first model is:

- browser -> platform backend assistant endpoints
- platform backend -> assistant runtime and connector
- assistant connector -> platform API upstream for action execution

This keeps connector ingress credentials and any assistant transport credentials server-side.

Track C should therefore reuse the operational pattern of `DeploymentPocChatService`, but harden it beyond the POC identity model for authorization-sensitive assistant actions.

More precisely:

- reuse the platform-backed proxy posture
- do not reuse synthetic runtime-facing `userId`, `ownerId`, or fixed session derivation
- rely on the shared auth foundation and the already-migrated POC path instead

This is the correct phase-1 posture for the first-party platform assistant.

It should not be interpreted as meaning the assistant product can only ever exist as an internal platform page.

### 2.7 The assistant must act as the current authenticated user

The assistant must never run as an invisible super-admin.

That means:

- UI-to-platform CRUD calls should use the current user session
- assistant actions that hit the platform API must execute with current-user authorization
- secret values must never be returned
- permission denial must be explained cleanly
- passed `userId`, `role`, or `deploymentId` fields from the chat payload must never be treated as authoritative by themselves

### 2.8 The assistant connector upstream should be the platform API

The assistant connector should use the platform API as its upstream system for actions.

This is different from the ecommerce demo pattern, where the connector points at the ecommerce store.

For Track C:

- connector upstream base URL should be the platform API base URL
- action routing should map assistant action ids to bounded platform API routes
- this assistant should be considered an actions-only assistant in the first pass

### 2.9 Add an explicit platform assistant authorization endpoint

Track C should add a dedicated platform assistant authorization check endpoint.

Recommended endpoint family:

- `POST /api/platform/assistant/authz/check`

This endpoint should be called before privileged assistant action execution and must determine:

- who the actor really is
- what platform role and deployment access they currently have
- whether the requested assistant action is allowed
- whether confirmation or approval is still required
- whether the requested deployment context is valid for that actor

### 2.10 Harden connector-side action execution

The assistant connector should not authorize platform actions based only on:

- connector API key possession
- action payload user fields
- optimistic UI role assumptions

Instead, Track C should require:

- a short-lived platform-issued assistant context token
- connector-side preflight to `POST /api/platform/assistant/authz/check`
- rejection of privileged execution when the signed context token is missing, expired, or invalid
- assistant-specific platform action routes that validate the same signed token again at execution time

### 2.11 Track C must explicitly support both assistant auth modes

Track C should not bake the assistant into one permanent auth posture.

The assistant architecture should explicitly recognize two supported auth modes:

- `PLATFORM_PROXY_SESSION`
- `PUBLIC_RUNTIME_BROWSER_TOKEN`

Meaning:

- `PLATFORM_PROXY_SESSION`
  - browser -> platform backend -> assistant runtime and connector
  - current authenticated platform session is the source identity
  - this is the required phase-1 mode
- `PUBLIC_RUNTIME_BROWSER_TOKEN`
  - browser -> public assistant runtime
  - browser uses short-lived bearer tokens
  - anonymous public chat uses a short-lived anonymous token issued by the runtime bootstrap endpoint by default
  - authenticated public chat uses a short-lived signed end-user token from a trusted backend or site identity provider
  - this is a later opt-in mode for customer-facing assistant surfaces, embeds, or widgets

Track C phase 1 does not need to ship the public mode itself.

But it must define the assistant auth and action contracts so the public mode can be added later without replacing:

- runtime auth context
- action authorization preflight
- connector trust boundaries
- conversation ownership rules

This is the compatibility bridge to the broader assistant-product direction:

- first-party platform assistant now
- separately packaged or customer-facing assistant product later
- same auth foundation and same execution contracts underneath

### 2.12 The assistant should be product-separable

Track C should intentionally preserve the ability to separate the assistant as its own product surface later.

That means the assistant should be built around:

- platform APIs
- deployment contracts
- supported auth modes
- explicit authorization contracts

and not around:

- hidden internal service shortcuts
- unscoped platform super-admin execution
- UI-only state that cannot be reproduced by another consumer
- platform-exclusive auth assumptions

---

## 3) Scope

Track C phase 1 should include:

- a platform-owned assistant deployment bootstrap and reconcile flow
- a dedicated assistant deployment template
- a new `support` curated module
- assistant action routing into the platform API
- a bounded read or write action catalog
- a simple first-party assistant chat page
- a platform-backed chat proxy surface
- a signed current-user assistant authorization contract
- assistant readiness and verification visibility
- local and live regression coverage for the assistant path
- explicit compatibility with the shared auth modes so later separate-product packaging does not require an auth redesign

Track C phase 1 should not require:

- an external widget or script-based embed
- direct browser-to-connector calls
- arbitrary document crawling across the full repo
- unrestricted admin writes
- secret-value access
- customer-facing white-label assistant surfaces

---

## 4) Product Shape

### 4.1 Deployment identity

The platform should own one assistant deployment per environment, for example:

- `Platform Assistant`
- environment `dev`

Recommended template direction:

- a dedicated assistant template id such as `platform-assistant-openai`

It is acceptable for the first implementation to reuse existing OpenAI or Lucene defaults internally, but the assistant should still have a dedicated template identity in the platform so it is not visually or operationally confused with customer deployments.

### 4.2 Curated module

Track C should add:

- `support`

Recommended meaning:

- support and operator guidance baseline
- platform-focused tone
- actions-first instructions
- safe explanation and confirmation behavior

### 4.3 Assistant UI surface

The first shipped assistant UI should be:

- a dedicated platform `Assistant` page
- implemented with a simple chat flow similar to the current POC console
- session-authenticated through the platform backend
- aware of the current deployment context when launched from a deployment workspace

That is the first UI.

It should still be designed as a consumer of generic assistant endpoints and contracts so the same assistant can later appear in:

- a separate product shell
- customer-facing integrations
- other first-party surfaces

Optional later additions:

- deployment-scoped side panels
- shell-level entry affordances
- richer conversation history views

Those later additions must not block the first Track C delivery.

---

## 5) Deployment Bootstrap and Reconciliation Model

Track C should add a dedicated bootstrap service, parallel to the ecommerce demo bootstrap pattern.

Recommended service:

- `PlatformAssistantBootstrapService`

Recommended responsibilities:

1. Resolve the assistant deployment by fixed platform-owned identity.
2. Create it if it does not exist.
3. Restore it if it is archived.
4. Reconcile its draft config to the assistant baseline.
5. Publish and apply if the live version is missing or stale.
6. Recreate or re-apply when the deployment is not up or the latest release is not healthy.

Recommended bootstrap properties:

- `platform.bootstrap.assistant.enabled`
- `platform.bootstrap.assistant.auto-apply`
- `platform.bootstrap.assistant.name`
- `platform.bootstrap.assistant.environment`

This should extend the existing bootstrap properties model rather than creating a disconnected configuration system.

---

## 6) Assistant Connector and Platform API Upstream

### 6.1 Upstream model

The assistant connector should point to the platform API as its upstream.

Equivalent of the ecommerce demo pattern:

- ecommerce demo connector upstream -> ecommerce store API
- assistant connector upstream -> platform API

### 6.2 Action categories

The first assistant action catalog should include bounded platform actions such as:

- list deployments
- get deployment workspace summary
- get latest release status
- get diagnostics summary
- get verification summary
- get readiness summary
- list user assignments for a deployment
- list notifications relevant to the current user
- rerun verification where allowed
- archive or restore deployment where allowed

The write set should stay narrow and auditable.

### 6.3 Read/write posture

The assistant is allowed to be read or write capable in phase 1, but writes must obey the same governance rules as normal UI operations:

- role checks
- approval checks
- confirmation semantics
- audit trail

The assistant must never bypass platform governance.

### 6.4 Authentication model

The assistant deployment cannot use a hidden all-powerful platform key for end-user actions.

The browser must not be the source of truth for actor identity or role.

Track C should define one assistant auth architecture with two supported modes.

Phase-1 required mode:

- `PLATFORM_PROXY_SESSION`
  - browser authenticates only to the platform backend with the normal platform session
  - platform backend proxies assistant chat requests to the runtime and connector
  - platform backend holds assistant transport credentials server-side
  - platform backend mints a short-lived signed assistant context token bound to:
    - current authenticated actor
    - current platform role
    - authentication mode
    - optional deployment context
    - expiration time
  - connector and platform assistant action routes validate that signed token before allowing execution

Later opt-in mode:

- `PUBLIC_RUNTIME_BROWSER_TOKEN`
  - browser talks to a public assistant runtime directly
  - browser uses only short-lived bearer tokens
  - anonymous public mode uses a runtime-issued anonymous session token by default
  - authenticated public mode uses a trusted externally issued signed end-user token
  - connector still stays private
  - assistant action execution still depends on explicit signed context and assistant action preflight

Shared requirements in both modes:

- connector ingress credential is only a transport credential, not a user authorization credential
- passed request fields such as `userId`, `role`, or `deploymentId` are advisory only until validated
- assistant actions still need explicit authorization preflight and governed execution
- conversation ownership must derive from verified auth context, not raw payload identity

The required contract is:

- connector ingress API key is only a transport credential, not a user authorization credential
- connector upstream calls into the platform API must still be scoped to the real current user
- passed request fields such as `userId`, `role`, or `deploymentId` are advisory only until validated by the platform

This is a hard requirement.

### 6.5 Explicit assistant action authorization preflight

Before privileged assistant action execution, the connector should call:

- `POST /api/platform/assistant/authz/check`

Recommended request shape:

- signed assistant context token
- requested action id
- normalized params preview
- optional deployment context

Recommended response shape:

- `allowed`
- resolved actor summary
- resolved deployment access summary
- `requiresConfirmation`
- `requiresApproval`
- denial reason when blocked

This is the policy decision point for assistant actions.

### 6.6 Assistant-specific execution routes

The connector should execute platform actions through assistant-specific platform API routes instead of trying to reuse a browser session.

Recommended endpoint family:

- `POST /api/platform/assistant/actions/{actionId}`

Those routes should:

- validate the same signed assistant context token again
- enforce role and deployment-access checks again
- preserve existing approval and audit semantics
- dispatch into the existing platform services rather than duplicating business logic

---

## 7) Simple Assistant Page and Platform Proxy

### 7.1 First UI surface

The first UI surface should be:

- `AssistantPage`

Recommended location:

- platform-level route such as `/assistant`

Recommended implementation pattern:

- reuse the proven POC page chat interaction model
- assume the POC proxy has already been migrated onto the shared auth foundation
- keep the UI intentionally simple
- prioritize correctness of authn/authz, action denial, and audit over shell polish

### 7.2 Platform-backed chat proxy

In phase 1, the page should call platform backend endpoints such as:

- `POST /api/platform/assistant/chat/query`
- `POST /api/platform/assistant/chat/suggestions`
- `GET /api/platform/assistant/chat/conversations/{conversationId}`
- `DELETE /api/platform/assistant/chat/conversations/{conversationId}`

These endpoints should:

- enforce the current platform session
- resolve the assistant deployment and readiness
- forward to assistant runtime or connector using server-held connector credentials
- attach the signed assistant context token for downstream action authorization

If the assistant later supports `PUBLIC_RUNTIME_BROWSER_TOKEN`, those browser-direct runtime endpoints should still reuse the same runtime auth context and action authorization model instead of introducing a second assistant-only security stack.

### 7.3 Deployment context awareness

When the user launches the assistant from a deployment-scoped workspace route, the platform chat proxy should pass the current deployment id as assistant context.

That allows prompts such as:

- explain this deployment failure
- summarize this deployment verification
- rerun verification for this deployment

without making the user re-specify the deployment every time.

---

## 8) New Backend Surfaces

Track C should add an assistant status and control-plane surface.

Recommended API families:

- `GET /api/platform/assistant/status`
- `POST /api/platform/assistant/reconcile`
- `POST /api/platform/assistant/chat/query`
- `POST /api/platform/assistant/chat/suggestions`
- `GET /api/platform/assistant/chat/conversations/{conversationId}`
- `DELETE /api/platform/assistant/chat/conversations/{conversationId}`
- `POST /api/platform/assistant/authz/check`
- `POST /api/platform/assistant/actions/{actionId}`

These endpoints should be designed as product-facing assistant contracts, not merely internal page helpers.

Recommended status payload:

- deployment existence
- archived status
- latest release status
- runtime or connector URLs
- assistant readiness
- safe chat-page configuration metadata
- whether deployment context is currently available
- whether the current actor can use assistant write actions

The reconcile path should remain platform-admin only.

The status path can be broader if it only returns safe non-secret metadata.

---

## 9) Assistant Prompt and Curated Content Model

Track C should add a `support` curated module with:

- prompt preset id: `support`
- runtime curated pack id aligned to `support`
- implementation source wired from `ai-infrastructure-module/curated/ai-curated-support`

Recommended prompt shape:

- operator-focused tone
- concise answers
- action-grounded summaries
- explicit permission-denied explanations
- explicit confirmation language for writes
- no secret-value exposure

The prompt bundle should be stored alongside the existing curated prompt resources.

---

## 10) Detailed Implementation Items

Track C should be executed in the following item order.

1. Turn `ai-infrastructure-module/curated/ai-curated-support` into a real checked-in curated module and wire curated module id `support` into the platform catalog.
2. Add a dedicated assistant deployment template and choose its default provider or vector posture.
3. Extend bootstrap properties with assistant settings.
4. Implement `PlatformAssistantBootstrapService` with create, restore, reconcile, publish, and apply behavior.
5. Add assistant deployment health and status resolution logic.
6. Add assistant connector routing config that targets the platform API upstream.
7. Define the initial bounded read or write assistant action catalog for platform operations.
8. Reuse the migrated POC interaction pattern only as a UI and proxy-shape reference, not as a legacy identity-contract reference.
9. Define a shared assistant auth mode abstraction that supports:
   - `PLATFORM_PROXY_SESSION`
   - `PUBLIC_RUNTIME_BROWSER_TOKEN`
10. Implement platform-backed assistant chat proxy endpoints modeled after the migrated POC console posture.
11. Implement a short-lived signed assistant context token model bound to the current authenticated user for `PLATFORM_PROXY_SESSION`.
12. Add `POST /api/platform/assistant/authz/check`.
13. Add assistant-specific platform action execution routes that validate the signed token and preserve approval and audit semantics.
14. Harden connector-side execution so it does not trust raw payload user or role fields and always preflights privileged actions through the platform authz endpoint.
15. Add a simple `AssistantPage` UI that calls the platform chat proxy.
16. Pass deployment context into the assistant page when launched from workspace routes.
17. Document the later `PUBLIC_RUNTIME_BROWSER_TOKEN` extension path, including anonymous runtime-issued token flow and authenticated browser token flow.
18. Ensure the assistant contracts remain usable by a separately packaged customer product without relying on platform-only auth shortcuts.
19. Add platform diagnostics or overview visibility for assistant deployment health and assistant auth posture.
20. Add local regression for bootstrap, routing, auth, token validation, authz preflight, and status surfaces.
21. Add live regression for assistant deployment readiness, one read path, one governed write path, and one insufficient-permission denial path.
22. Document assistant operations, failure modes, and recovery.

---

## 11) Verification and Regression Requirements

Track C should not be considered complete until these are covered.

### 11.1 Local regression

- bootstrap create-if-missing
- restore-if-archived
- reconcile-if-not-running
- `support` curated module catalog resolution
- assistant status API
- assistant chat proxy endpoints
- assistant connector routing to platform API
- signed assistant context token validation
- current-user auth enforcement
- assistant authz preflight endpoint
- connector rejection of forged or missing actor context
- denial behavior for insufficient permission

### 11.2 Live regression

- assistant deployment exists and is healthy
- assistant page can send and receive through the platform backend
- at least one read-only assistant action works end to end
- at least one governed write action works end to end for an authorized user
- unauthorized user cannot exceed their permissions
- browser traffic does not require exposing connector ingress credentials

### 11.3 UI verification

UI automation does not need to be deep, but the following must be proven:

- assistant page can send and receive a bounded query
- permission-denied responses are visible and understandable
- deployment workspace context is passed when present

---

## 12) Completion Criteria

Track C is complete only when all of the following are true:

- the platform can ensure the assistant deployment exists and is running
- the assistant deployment is clearly identified as a platform-owned component
- the assistant connector routes actions into the platform API
- the browser never needs direct connector credentials
- the assistant respects current-user authorization
- the connector does not trust caller-supplied role or user fields
- assistant actions are preflighted through a platform authorization endpoint before execution
- the assistant auth architecture explicitly distinguishes:
  - phase-1 `PLATFORM_PROXY_SESSION`
  - later `PUBLIC_RUNTIME_BROWSER_TOKEN`
- the assistant remains structurally compatible with later packaging as a separate customer product that consumes the same platform contracts
- the `support` curated module exists and is used by the assistant baseline
- the first-party assistant page works end to end
- local and live regression prove the assistant path end to end

---

## 13) Immediate Recommendation

The first implementation pass should start with these four items:

1. `support` curated module
2. assistant bootstrap service and template
3. platform-backed assistant chat proxy and status API
4. signed assistant auth plus `POST /api/platform/assistant/authz/check`

That gives the platform:

- a real assistant deployment
- a secure first-party chat surface
- a clear readiness model
- a concrete base for the later action catalog and richer UI work
