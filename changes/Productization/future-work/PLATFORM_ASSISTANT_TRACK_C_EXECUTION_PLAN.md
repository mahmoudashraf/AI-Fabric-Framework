# Platform Assistant Track C Execution Plan

Status: detailed execution plan (2026-04-06)

This document defines the concrete execution plan for Wave 4 Track C.

It takes the broader direction from:

- `PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`
- `AI_ASSISTANT_PRODUCT_NORTH_STAR_AND_SCOPE.md`

and locks the first implementation around the current product reality and the latest execution decisions.

The goal is not to brainstorm every possible assistant surface.

The goal is to ship one real, platform-owned assistant path that:

- is created and healed as part of the platform itself
- uses the existing deployment model rather than a one-off backend
- routes assistant actions into the platform API
- appears as a floating assistant entry point across the platform UI
- remains bounded by the current authenticated user's permissions

---

## 1) Executive Summary

Track C should deliver a first-class platform assistant as a real deployment owned by the platform.

The first production shape should be:

- one platform-owned assistant deployment per environment
- bootstrap or reconcile semantics similar to the ecommerce demo deployment
- an assistant connector whose upstream system is the platform API
- an action-first assistant surface with bounded read and write platform actions
- a globally mounted floating assistant widget that appears across the platform UI without duplicate mounting

This is not a customer chatbot.

It is an operator and admin assistant for the platform itself.

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

The current code only exposes these curated modules:

- `default`
- `commerce`

There is no `support` curated module yet.

Track C should add:

- curated module id: `support`

This should become the assistant deployment baseline for prompt preset and runtime curated-pack metadata.

### 2.5 Use a floating assistant widget mounted once at shell level

The assistant should appear as a floating icon and chat entry point across the platform UI.

The correct generic integration point is the shell, not page-by-page duplication.

The implementation should mount the widget once from:

- `Platfrom/ui/src/layout/AppShell.tsx`

This should avoid:

- duplicate script tags
- per-page widget initialization
- multiple widget instances on route changes

### 2.6 Use the provided MaxMode widget integration contract

The first UI integration should follow this shape:

```html
<script src="https://mahmoudashraf.github.io/aifabric/max-mode-widget.iife.js"></script>
<script>
  MaxMode.init({
    apiConfig: {
      chatBaseUrl: "https://<assistant-connector>/api",
      crudBaseUrl: "https://<platform-base-url>/api",
      headers: { "X-AIFABRIC-API-KEY": "<assistant-connector-api-key>" },
    },
  });
</script>
```

The real platform implementation must not hardcode these values in the page source.

They should be resolved from platform state and injected once through a typed UI host component.

### 2.7 The assistant must act as the current authenticated user

The assistant must never run as an invisible super-admin.

That means:

- UI-to-platform CRUD calls should use the current user session
- assistant actions that hit the platform API must execute with current-user authorization
- secret values must never be returned
- permission denial must be explained cleanly

### 2.8 The assistant connector upstream should be the platform API

The assistant connector should use the platform API as its upstream system for actions.

This is different from the ecommerce demo pattern, where the connector points at the ecommerce store.

For Track C:

- connector upstream base URL should be the platform API base URL
- action routing should map assistant action ids to bounded platform API routes
- this assistant should be considered an actions-only assistant in the first pass

---

## 3) Scope

Track C phase 1 should include:

- a platform-owned assistant deployment bootstrap and reconcile flow
- a dedicated assistant deployment template
- a new `support` curated module
- assistant action routing into the platform API
- a bounded read or write action catalog
- a generic shell-level floating widget host
- assistant readiness and verification visibility
- local and live regression coverage for the assistant path

Track C phase 1 should not require:

- a full standalone Assistant page before launch
- a second duplicate embedded assistant implementation
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

- a floating widget icon visible across all pages
- mounted once from the shell
- aware of the current deployment context when the user is inside a deployment workspace

Optional later additions:

- a dedicated `Assistant` page
- deployment-scoped side panels
- conversation history views

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

Track C must add one of these safe models:

- short-lived user-scoped upstream token minted by the platform UI and forwarded through the assistant connector
- another equivalent signed current-user action credential

The required contract is:

- connector ingress can use its own assistant API key
- connector upstream calls into the platform API must still be scoped to the current authenticated user

This is a hard requirement.

---

## 7) Floating Widget Integration

### 7.1 Single mount point

The widget should be hosted once from:

- `Platfrom/ui/src/layout/AppShell.tsx`

Recommended new shell component:

- `PlatformAssistantWidgetHost`

Responsibilities:

- load the external script once
- initialize MaxMode once
- tear down cleanly when needed
- reconfigure only when the active assistant deployment or platform base URL changes

### 7.2 Runtime configuration source

The widget config should be resolved from platform APIs, not hardcoded in source.

Recommended assistant-status payload should provide:

- assistant deployment id
- chat base URL
- platform CRUD base URL
- connector ingress header name
- connector ingress value or token reference
- readiness status
- deployment-scoped context support flag

### 7.3 Deployment context awareness

When the user is in a deployment-scoped workspace route, the widget host should pass the current deployment id as assistant context.

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

Recommended status payload:

- deployment existence
- archived status
- latest release status
- runtime or connector URLs
- assistant readiness
- widget configuration fields safe to return to the UI

The reconcile path should remain platform-admin only.

The status path can be broader if it only returns safe non-secret metadata.

---

## 9) Assistant Prompt and Curated Content Model

Track C should add a `support` curated module with:

- prompt preset id: `support`
- runtime curated pack id aligned to `support`

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

1. Add the new `support` curated module and prompt preset resources.
2. Add a dedicated assistant deployment template and choose its default provider or vector posture.
3. Extend bootstrap properties with assistant settings.
4. Implement `PlatformAssistantBootstrapService` with create, restore, reconcile, publish, and apply behavior.
5. Add assistant deployment health and status resolution logic.
6. Add assistant connector routing config that targets the platform API upstream.
7. Define the initial bounded read or write assistant action catalog for platform operations.
8. Implement current-user-scoped upstream auth for assistant actions.
9. Add assistant status APIs for the UI shell.
10. Add a global `PlatformAssistantWidgetHost` mounted once from `AppShell`.
11. Load and initialize the MaxMode widget from resolved assistant config.
12. Pass deployment context into the widget on workspace routes.
13. Add platform diagnostics or overview visibility for assistant deployment health.
14. Add local regression for bootstrap, routing, auth, and status surfaces.
15. Add live regression for assistant deployment readiness and one read path plus one governed write path.
16. Document assistant operations, failure modes, and recovery.

---

## 11) Verification and Regression Requirements

Track C should not be considered complete until these are covered.

### 11.1 Local regression

- bootstrap create-if-missing
- restore-if-archived
- reconcile-if-not-running
- assistant status API
- assistant connector routing to platform API
- current-user auth enforcement
- denial behavior for insufficient permission

### 11.2 Live regression

- assistant deployment exists and is healthy
- floating widget config resolves successfully
- at least one read-only assistant action works end to end
- at least one governed write action works end to end for an authorized user
- unauthorized user cannot exceed their permissions

### 11.3 UI verification

UI automation does not need to be deep, but the following must be proven:

- widget loads once across route changes
- widget does not duplicate itself
- widget can open from multiple platform pages
- deployment workspace context is passed when present

---

## 12) Completion Criteria

Track C is complete only when all of the following are true:

- the platform can ensure the assistant deployment exists and is running
- the assistant deployment is clearly identified as a platform-owned component
- the assistant connector routes actions into the platform API
- the assistant respects current-user authorization
- the `support` curated module exists and is used by the assistant baseline
- the floating widget appears across the platform from a single generic shell mount
- no duplicate widget initialization occurs on navigation
- local and live regression prove the assistant path end to end

---

## 13) Immediate Recommendation

The first implementation pass should start with these four items:

1. `support` curated module
2. assistant bootstrap service and template
3. assistant status API
4. global `AppShell` widget host

That gives the platform:

- a real assistant deployment
- a stable UI mount point
- a clear readiness model
- a concrete base for the later action catalog and auth passthrough work
