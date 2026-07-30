# 010.10 Shopify Admin To Partner Portal Transition Plan

Status: implemented, deployed, and staging live-verified, created 2026-05-26

Parent plans:

- [010 GTM And Partner Portal Launch Readiness](010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md)
- [010.8 Shopify Companion Next Urgent Steps Readiness Plan](010_8_SHOPIFY_COMPANION_NEXT_URGENT_STEPS_READINESS_PLAN.md)
- [010.9 Shopify Specific Max Mode Shopping Widget Implementation Plan](010_9_SHOPIFY_SPECIFIC_MAX_MODE_SHOPPING_WIDGET_IMPLEMENTATION_PLAN.md)

Code references reviewed:

- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyBridgeAdminController.java`
- `Platfrom/partner-ui/src/pages/StoreWorkspacePage.tsx`
- `Platfrom/partner-ui/src/api/productControls.ts`
- `Platfrom/partner-ui/src/api/schemas.ts`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/web/PartnerEnablementController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/web/MerchantPartnerAccessController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/web/ShopifyAdminController.java`

## 2026-05-26 Implemented Transition Slice: Partner-Owned Shopify Operations

The transition slice is implemented and locally verified. The Partner Portal now has a partner-safe Shopify operations surface for source sync, vectorization, live update policy, storefront activation guidance, billing/usage posture, provisioning/support readiness, webhook posture, and recent governed action audit. This moves the highest-risk daily Shopify operations out of the Shopify embedded admin app and into Platform-authorized Partner Portal APIs after merchant approval.

Implemented backend surface:

- `GET /api/partners/stores/{storeId}/shopify-operations`
- `POST /api/partners/stores/{storeId}/shopify-operations/knowledge/reconcile`
- `POST /api/partners/stores/{storeId}/shopify-operations/knowledge/index-all`
- `POST /api/partners/stores/{storeId}/shopify-operations/knowledge/reindex-all`
- `POST /api/partners/stores/{storeId}/shopify-operations/knowledge/reindex-selected`
- `PUT /api/partners/stores/{storeId}/shopify-operations/knowledge/policy`
- `POST /api/partners/stores/{storeId}/shopify-operations/knowledge/events/{eventId}/replay`
- `POST /api/partners/stores/{storeId}/shopify-operations/knowledge/retry-last-failed-auto-run`
- `POST /api/partners/stores/{storeId}/shopify-operations/source-preflight`

Implemented Partner Portal surface:

- new `Shopify operations` workspace tab
- install/widget/knowledge/readiness/billing state tiles
- vectorization runner and plan readiness summary
- blocking reasons and reconciliation-required warnings
- source/category posture and installed DATA plugin visibility
- storefront activation, theme app embed, and merchant-owned Shopify action handoff guidance
- usage and package value signals
- provisioning timeline and remediation posture
- support readiness and support handoff posture
- webhook subscription posture
- recent governed action audit timeline
- full index, full reindex, selected entity reindex, reconcile, failed-run retry, and event replay actions
- source preflight run action
- live update policy editor for auto-indexing, create/update/delete triggers, selected-field update mode, debounce, and minimum run interval
- Shopify embedded admin duplicate operations are disabled after approved/active Partner Portal management exists. Merchant-owned Shopify operations remain available there: install/reinstall/session recovery, billing approval, theme activation links, and partner access approve/deny/revoke/invite.

Security and ownership decisions:

- Partner UI calls Platform APIs only.
- The new backend DTOs intentionally omit raw deployment IDs, vectorization runner IDs, source connection IDs, provider IDs, webhook IDs, plan IDs, and secret/provider internals.
- Mutations are gated by existing partner assignment capabilities: `KNOWLEDGE_SYNC_TRIGGER` for sync/reindex/replay and `KNOWLEDGE_SOURCE_CONTROL` for reconcile/policy updates.
- `updateProductSupportProfile` now requires `PARTNER_WRITE_ACCESS`; it was a write operation and should not be authorized as read access.
- The Partner operations summary runs outside the caller transaction and safe-reads optional diagnostics so missing vectorization/provisioning diagnostics cannot mark a read-only store workspace request rollback-only.

Verification completed:

- `mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementServiceAuthorizationTest,PartnerEnablementIntegrationTest test`
- `mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyStoreVectorizationServiceTest test`
- `mvn -f Platfrom/backend/pom.xml -q test`
- `npm --prefix Platfrom/partner-ui run build`
- `npm --prefix Platfrom/partner-ui run smoke`
- `npm --prefix product-services/shopify-bridge-service/ui run build`
- `git diff --check`

Staging rollout status:

- Commit `9b7bbfbc9` was pushed to `origin/Platform-V10`.
- Coolify staging deployments finished successfully:
  - Platform backend: `jbbed0negxmjurd6f0tvoasb`
  - Partner UI: `ieqhlnni9ek8i92eesiacs33`
  - Shopify Bridge staging: `vx90tkepschlm0dvisjv74x4`
- Live health checks passed:
  - Platform backend `/actuator/health` returned `UP`
  - Partner UI returned HTTP `200`
  - Shopify Bridge staging `/actuator/health` returned `UP`
- The staging merchant approval flow was re-approved for `shopping-companion-test.myshopify.com`, leaving partner assignment `psa-fbe3b4f7-5cda-4747-8773-82dd45bb0e93` active so Partner Portal is the active management surface.
- Live Partner API proof passed:
  - `GET /api/partners/stores/{storeId}/shopify-operations` returned HTTP `200`
  - `POST /api/partners/stores/{storeId}/shopify-operations/source-preflight` returned HTTP `200`
  - response included activation, billing, usage, provisioning, support readiness, webhook posture, vectorization posture, capabilities, and recent action summary fields
  - response was checked for secret/provider terms and did not expose raw credentials or provider internals
- Deployed UI bundle proof passed:
  - Partner UI bundle contains the `Shopify operations` tab, source preflight, merchant-owned Shopify action guidance, and knowledge sync controls
  - Shopify embedded admin bundle contains the Partner Portal management banner and disabled duplicate-operation guard
- `scripts/verify-partner-enablement-live.sh` passed in non-strict live mode against the active staging assignment. It verified Partner UI assets, partner store detail, product controls, Partner Max widget, support-profile write/restore, activity feed, verification/evidence bundles, launch readiness, templates, notes, members, and support escalation.
- The live verifier was stabilized so approval proof comes from the active approved store detail, while the activity feed assertion checks workflow events produced by the current run. This avoids treating an old paginated approval audit event as a release blocker after later verification/support events push it out of the recent feed.
- A final hosted non-strict run on the current staging deployment passed on 2026-05-26. Proof IDs from that run: verification run `pvr-a42711f6-5e8b-46bc-847d-40cc7a1732c8`, launch evidence bundle `peb-54e98feb-9126-4cdd-965a-62aea06361fc`.
- Production promotion was intentionally not run in this transition slice. The controlled production-promotion proof remains part of the broader Shopify production release gate.

`010_ADMIN_TO_PARTNER_TRANSITION_READY`: passed for staging. Public production readiness still depends on the separate production-promotion, rollback/deactivation, and public claim gates tracked in `010.8`.

## Purpose

The Partner Portal should become the main operating surface for everything related to Loom Companion on Shopify after the merchant approves scoped partner access.

This plan separates:

- what should move from the Shopify embedded app into the Partner Portal
- what should remain merchant-owned inside Shopify Admin or Shopify-native settings
- what should remain Platform operator-only
- what new Partner Portal surfaces are needed so partners can manage stores without direct access to Shopify Bridge internals, Coolify, secrets, or provider APIs

The goal is not to remove Shopify Admin entirely. Shopify Admin remains the merchant consent and Shopify-native configuration surface. The Partner Portal becomes the daily operating surface for approved implementation partners and internal launch operators.

## Current Shopify Embedded App Capability Map

The current Shopify embedded admin app exposes the following merchant/admin capabilities through `ShopifyMerchantController` and the Polaris UI:

| Area | Current Shopify embedded app capability | Current endpoint family |
| --- | --- | --- |
| Merchant session | Resolve Shopify merchant session, install recovery, scope grant prompts | `GET /api/app/session` |
| Store connection | Connect store, bootstrap Platform deployment, run source preflight | `/api/app/store/connect`, `/bootstrap`, `/source-preflight` |
| Source settings | Enable products, collections, pages, policies, articles, metaobjects | `POST /api/app/store/source-settings` |
| Storefront activation | Preview theme embed readiness, activation steps, theme editor links, storefront links | `GET /api/app/store/storefront-preview` |
| Widget settings | Launcher label, welcome message, shell profile, color scheme, debug mode, dock, legacy launcher, surfaces, modes, page routing | `POST /api/app/store/widget-settings` |
| Knowledge operations | Reconcile vectorization support, refresh knowledge, full reindex, selected reindex | `/api/app/store/vectorization/*` |
| Live update policy | Configure source policy, triggers, selected fields, retry failed live updates | `PUT /api/app/store/vectorization/policy` |
| Live update events | View recent events, replay a vectorization event | `/api/app/store/vectorization/events/*` |
| Provisioning | View product package and latest provisioning job state | `GET /api/app/store/provisioning` |
| Billing | View plan posture, allowed surfaces, current tier, request Shopify billing approval | `/api/app/store/billing-summary`, `/billing/approval` |
| Usage and audit | View usage summary and recent governed action audit | `/usage-summary`, `/actions/recent` |
| Partner access | Merchant approves, denies, revokes partner access and sends invite | `/partner-access/requests/*` |
| Support readiness | View support readiness, scope grant state, install recovery, support profile | `/support-readiness`, `/support-profile` |
| Webhooks | View webhook subscription status | `/webhook-subscriptions` |
| Playground | Merchant-side query playground and suggestions | `/playground/query`, `/playground/suggestions` |
| Go live | Request store go-live/apply from the embedded merchant app | `POST /api/app/store/go-live` |

This page is currently too broad for the long-term product direction. It mixes merchant consent, daily operations, partner implementation controls, advanced diagnostics, and operator-style vectorization controls in one Shopify Admin surface.

## Current Partner Portal Capability Map

The Partner Portal already exposes a strong subset of Shopify operations through Platform APIs only:

| Area | Current Partner Portal capability | Current Platform API |
| --- | --- | --- |
| Partner store workspace | Store assignment, launch status, approved access, notes, evidence, support | `/api/partners/stores/{storeId}` and related workspace APIs |
| Product controls | Widget status, source status, readiness state, capabilities | `GET /api/partners/stores/{storeId}/product-controls` |
| Widget settings | Launcher label, welcome message, shell profile, color scheme, debug, dock, legacy launcher, surfaces, modes, page mappings | `POST /product-controls/widget-settings` |
| Source settings | Enable/disable Shopify source categories | `POST /product-controls/source-settings` |
| Support profile | Merchant handoff email, URLs, order lookup page, policy note | `POST /product-controls/support-profile` |
| Package trials | Activate/deactivate package trial within partner-approved scope | `/package-trials` |
| Launch readiness | View staging, evidence, production promotion eligibility | `/launch-readiness` |
| Production promotion request | Partner requests production promotion through Platform service | `/production-promotions` |
| Verification and evidence | Run verification packs, complete manual checks, create/export evidence bundles | `/verification-*`, `/evidence-bundles` |
| Partner Max widget live test | Query through Platform partner-safe store widget path | `/max-widget/chat/me/*` |
| Support/escalation | Store escalations, replies, notes, support thread | `/support/escalations`, `/stores/{storeId}/escalations` |

Current Partner Portal gaps compared with Shopify embedded admin:

- no partner-safe view for full vectorization summary, runner state, blocking reasons, recent events, event replay, failed auto-run retry, or live update policy
- no partner-safe action to run source preflight, refresh knowledge, full reindex, or selected reindex
- no storefront activation guidance panel with theme editor links, app embed status, block placement guidance, and storefront preview
- no partner-safe usage summary and governed action audit timeline equivalent to the Shopify embedded app
- no partner-visible webhook subscription status
- no billing approval handoff or merchant billing-state visibility beyond package trial/product control state
- no partner-visible provisioning job timeline with remediation guidance
- no merchant-side approval/revoke status panel inside Partner Portal beyond assignment state
- no explicit capability matrix explaining which controls are merchant-owned, partner-owned, or operator-owned

## Transition Principle

Partner Portal should own operational configuration after merchant approval.

Shopify Admin should own Shopify-native consent and merchant-only decisions.

Platform operator surfaces should own secrets, provider internals, deployment internals, and emergency remediation.

Partner Portal and merchant portal must never call Coolify, Bridge admin APIs, Shopify Admin APIs, MCP gateways, vector providers, or secret stores directly. They must call Platform APIs only. Platform decides whether to call Bridge, runtime, vectorization runner, Shopify, or deployment providers.

## Ownership Model

| Owner | Allowed to do | Not allowed to do |
| --- | --- | --- |
| Merchant in Shopify Admin | Install/reinstall app, approve scopes, approve Shopify billing, enable theme app embeds/blocks, approve/deny/revoke partner access, manage native Shopify content | See provider credentials, Coolify internals, runtime secrets, partner-only notes, operator diagnostics |
| Merchant in merchant portal/deep link | Review partner request, approve/deny/revoke, request production promotion or rollback/deactivation, view launch/evidence/support status | Mutate provider internals, bypass Shopify billing/scope consent, expose secrets |
| Approved partner in Partner Portal | Configure Loom Companion product controls, source categories, support handoff, theme guidance, knowledge refresh/reindex, live update policy if granted, verification, evidence, support notes, production promotion request | Install app, approve Shopify scopes, approve Shopify billing charges, edit native Shopify products/policies directly, view secrets/provider internals |
| Platform operator | Manage credentials, deployment profiles, Coolify/provider resources, production target profiles, forced remediation, secret rotation | Use operator-only paths as merchant/partner UX, bypass merchant approval for launch claims |

## Move To Partner Portal

These capabilities should move to Partner Portal as first-class, approval-scoped product operations.

### 1. Storefront Widget And Max Mode Controls

Already mostly present in Partner Portal.

Keep and polish:

- launcher label
- welcome message
- shell profile
- color scheme/theme
- debug inspector
- companion dock enabled
- legacy Ask Assistant launcher toggle
- surfaces
- allowed modes
- page mode mappings

Required changes:

- group the controls by merchant-readable concepts, not runtime names
- keep raw runtime mode controls behind an advanced/operator affordance
- show "requires merchant approval" or "requires tier" beside disabled surfaces
- write an audit entry for every partner change with before/after summaries

### 2. Source And Knowledge Controls

Move into a Partner Portal `Knowledge` or `Content Sync` tab:

- source category enablement
- source preflight run
- Shopify-backed knowledge refresh
- full reindex
- selected entity reindex
- vectorization summary
- runner/plan status
- source connection status
- last successful refresh
- live update backlog
- blocking reasons

Partner-safe mutation boundary:

- partners may trigger refresh/reindex only for merchant-approved source categories
- Platform validates assignment capability and store install status
- Platform owns Bridge/vectorization calls and returns merchant-safe result summaries

### 3. Live Update Policy

Move into Partner Portal only if the partner assignment includes a high-trust capability such as `KNOWLEDGE_POLICY_MANAGE`.

Controls:

- auto indexing enabled per source family
- create/update/delete trigger toggles
- update trigger mode
- selected indexed fields
- debounce/minimum run interval, if safe to expose
- retry failed live update
- replay individual event, if safe and audited

Default posture:

- read-only diagnostics for normal partners
- mutation enabled only for implementation partners or internal launch operators

### 4. Storefront Activation Guidance

Move a partner-safe version into Partner Portal:

- theme embed readiness
- app embed/block activation steps
- theme editor deep links
- storefront preview link
- suggested block placements by tier
- current configured surfaces vs plan-allowed surfaces
- grounding signals/review provider summary

Keep Shopify Admin as the actual place where merchants activate theme embeds/blocks unless Shopify API support and permission posture prove automated activation is safe.

### 5. Usage, Shopper Evidence, And Action Audit

Move into Partner Portal:

- usage summary
- shopper surface usage
- top shopper questions
- recent governed action audit
- answer-quality proof links
- RAG/debug inspector evidence summaries

Restrictions:

- redact shopper PII by default
- expose only merchant-approved store data
- action audit must be immutable and traceable to store, deployment, action, and confirmation state

### 6. Support Readiness And Merchant Handoff

Already partly present.

Add:

- support readiness status and exact blockers
- scope grant/install recovery status as read-only partner guidance
- order lookup claim status
- support runbook/export links
- escalation path and current open support issues

Keep merchant profile fields editable only if assignment has `SUPPORT_MANAGE`.

### 7. Provisioning And Package State

Move a partner-safe view into Partner Portal:

- current package profile
- active tier/status
- latest provisioning job state
- vector reindex required flag
- package trial history
- remediation message

Do not expose:

- deployment provider ids
- Coolify ids
- secret refs
- raw provider logs
- deployment env values

### 8. Billing Posture

Move read-only billing posture and merchant handoff into Partner Portal:

- current tier
- allowed surfaces
- catalog cap
- sync cadence
- powered-by posture
- launch blocked reason
- package trial status

Keep Shopify billing approval in Shopify Admin because the merchant must approve charges in Shopify's billing flow.

Partner Portal may show a "Merchant billing approval required" status and send the merchant to the Shopify billing approval link, but the partner must not approve charges for the merchant.

### 9. Partner Access State

Move full visibility into Partner Portal:

- request status
- merchant approval/revocation status
- assigned scope
- granted capabilities
- invite sent/accepted/expired status
- revoke status

Keep approval/denial/revocation as merchant-owned actions through Shopify Admin and merchant deep-link portal.

### 10. Playground And Launch QA

Move the merchant playground concept into Partner Portal as a launch QA surface:

- query playground
- suggestions
- debug evidence view
- RAG source panel
- action/confirmation trace panel
- quality test preset runner

This should use canonical Platform chat/query APIs, not Bridge-specific payload assumptions.

## Keep In Shopify Admin

These should remain in Shopify Admin or Shopify-native settings, with the reason made explicit in product copy and docs.

| Capability | Keep location | Reason |
| --- | --- | --- |
| App install/reinstall/OAuth | Shopify Admin | Shopify controls merchant app installation, scopes, sessions, and embedded app auth. |
| Protected customer data access | Shopify Partner/Dev Dashboard and Shopify Admin reinstall flow | Shopify owns protected data review, scope approval, and merchant consent. |
| Shopify billing approval | Shopify Admin billing approval URL | Shopify requires merchant charge approval in Shopify billing. Partner must not approve merchant charges. |
| Theme app embed/block activation | Shopify Theme Editor | Shopify owns theme editing and app embed placement. Partner Portal can guide and deep-link, but merchant/theme owner controls activation. |
| Store domain and Customer Account domain | Shopify Admin domain/customer account settings | These affect storefront identity, customer auth, and Shopify-hosted account flows. |
| Native product, policy, collection, page, and article edits | Shopify Admin | Shopify remains source of truth for merchant content. LoomAI indexes and grounds from it; it should not become a CMS replacement. |
| Checkout/customer account extension installation and Shopify-specific eligibility | Shopify Admin and Partner Dashboard | Shopify controls checkout/customer account extension availability, review, and distribution constraints. |
| App uninstall | Shopify Admin | Merchant-owned removal action with Shopify lifecycle webhooks. |

## Keep Operator-Only In Platform

These should not move to Partner Portal or Shopify Admin merchant UX.

| Capability | Reason |
| --- | --- |
| Raw Shopify access tokens and secret material | Secret material must stay in Platform secret storage and Bridge credential stores. |
| Coolify app ids, project ids, deployment ids, provider urls, env vars | Provider internals are operational infrastructure, not product UX. |
| Deployment target profile mutation | Production target profiles affect infrastructure and release safety. |
| Forced deployment recreate, provider retry, provider log retrieval | High-blast-radius operations require operator authorization. |
| Platform secret rotation | Requires admin-only audit and recovery flow. |
| Internal MCP gateway server credentials | Not merchant/partner visible. |
| Runtime/vector provider credentials and collection internals | Not needed to operate the product and can leak infrastructure posture. |

## Transition Architecture

```text
Partner Portal
  -> Platform Partner APIs
     -> assignment/capability check
     -> store install/readiness check
     -> audit event
     -> Shopify Platform services
        -> Bridge merchant/admin APIs where needed
        -> vectorization runner where needed
        -> verification/evidence services where needed

Shopify Admin Embedded App
  -> merchant session only
  -> install/reconnect/scope/billing/theme guidance
  -> approve/deny/revoke partner access
  -> link to Partner Portal or merchant deep-link workspace for daily operations
```

The Bridge remains Shopify-specific and keeps Shopify auth/session/storefront boundary behavior. The Partner Portal should not learn Bridge implementation details. Platform owns the mapping from partner-safe operations to Bridge and deployment operations.

## Required Partner API Additions

Add these Platform Partner APIs before moving the UI:

| API | Purpose | Required authorization |
| --- | --- | --- |
| `GET /api/partners/stores/{storeId}/shopify-operations/activation` | Storefront/theme activation preview and guidance | active assignment + read |
| `GET /api/partners/stores/{storeId}/shopify-operations/billing` | Read-only billing posture and merchant approval requirement | active assignment + read |
| `GET /api/partners/stores/{storeId}/shopify-operations/usage` | Usage summary and top shopper signals | active assignment + read |
| `GET /api/partners/stores/{storeId}/shopify-operations/actions/recent` | Recent governed action audit | active assignment + read |
| `GET /api/partners/stores/{storeId}/shopify-operations/vectorization` | Vectorization summary, status, runner, blockers, events | active assignment + `KNOWLEDGE_SOURCE_CONTROL` or read-only capability |
| `POST /api/partners/stores/{storeId}/shopify-operations/source-preflight` | Run source preflight | `KNOWLEDGE_SOURCE_CONTROL` |
| `POST /api/partners/stores/{storeId}/shopify-operations/knowledge-refresh` | Queue Shopify-backed index-all refresh | `KNOWLEDGE_SOURCE_CONTROL` |
| `POST /api/partners/stores/{storeId}/shopify-operations/reindex-all` | Queue full reindex | `KNOWLEDGE_SOURCE_CONTROL` + launch/operator policy |
| `POST /api/partners/stores/{storeId}/shopify-operations/reindex-selected` | Queue bounded selected reindex | `KNOWLEDGE_SOURCE_CONTROL` |
| `PUT /api/partners/stores/{storeId}/shopify-operations/vectorization-policy` | Update live indexing policy | new `KNOWLEDGE_POLICY_MANAGE` capability |
| `POST /api/partners/stores/{storeId}/shopify-operations/vectorization-events/{eventId}/replay` | Replay event | new `KNOWLEDGE_POLICY_MANAGE` or operator-only |
| `GET /api/partners/stores/{storeId}/shopify-operations/webhooks` | Webhook subscription health | active assignment + read |
| `POST /api/partners/stores/{storeId}/shopify-operations/playground/query` | Partner QA query using canonical payload | active assignment + read |

All writes must:

- validate active assignment
- validate store belongs to assignment
- validate store install status
- validate capability
- never accept raw shop domain as authorization
- write audit with before/after or requested operation summary
- return merchant-safe errors and operator-safe diagnostic identifiers

## Shopify Embedded App Reduction Plan

After Partner Portal parity exists, reduce the embedded Shopify app to:

- session/install recovery
- app connection/bootstrap status
- protected-scope and billing approval guidance
- theme editor activation guidance
- merchant approval/deny/revoke partner access
- link to Partner Portal or merchant launch workspace
- emergency support link

The embedded app can keep read-only status cards for merchant confidence, but it should no longer be the main place to manage Loom Companion configuration.

## UI Plan

Partner Portal Store Workspace should become the main Shopify operations workspace.

Suggested tabs:

1. `Overview`
   - install, assignment, tier, readiness, top blocker
   - links to Shopify Admin for merchant-only actions
2. `Storefront`
   - widget theme/color/mode/surfaces
   - theme activation guidance
   - Max Mode preview/live test
3. `Knowledge`
   - source categories
   - source preflight
   - refresh/reindex controls
   - live update health and policy
4. `Shopper Evidence`
   - usage summary
   - top questions
   - recent actions
   - debug/RAG evidence
5. `Support`
   - merchant handoff
   - support readiness
   - order/customer capability claim status
   - escalation thread
6. `Launch`
   - staging readiness
   - verification
   - evidence export
   - production promotion request
   - rollback/deactivation request
7. `Merchant Access`
   - approval/revocation state
   - granted capabilities
   - invite status
   - merchant approval link status

## Implementation Slices

### Slice 1 - Capability Matrix And Partner API Parity

Deliver:

- add explicit capability enum/constants for Shopify operations
- add read-only Partner APIs for activation, billing, usage, action audit, webhook, vectorization summary
- add tests for active assignment and revoked assignment fail-closed behavior

Verification:

- Partner API tests prove active assignment can read
- revoked/expired assignment returns forbidden/not found
- no provider ids, secret refs, or raw Bridge diagnostics leak

### Slice 2 - Knowledge Operations In Partner Portal

Deliver:

- Partner Portal `Knowledge` tab
- source preflight
- refresh knowledge
- selected reindex
- full reindex with stronger confirmation copy
- vectorization health and recent event view

Verification:

- partner live verifier can trigger a safe refresh/reindex in staging
- failed operation returns merchant-safe copy and diagnostic id
- event replay remains disabled unless capability is present

### Slice 3 - Storefront Activation And Widget Ownership

Deliver:

- Partner Portal `Storefront` tab
- theme activation guidance
- app embed/block deep links
- widget controls grouped by merchant-readable concepts
- plan/tier/surface mismatch warnings

Verification:

- Partner UI build and smoke pass
- live staging widget picks up Partner Portal changes
- Shopify Admin and Partner Portal show consistent widget state

### Slice 4 - Usage, Audit, And Launch QA

Deliver:

- Partner Portal `Shopper Evidence` tab
- usage summary
- recent governed actions
- canonical debug/RAG evidence view
- partner query playground using canonical request/response shape

Verification:

- live RAG query returns documents/sources in the evidence panel
- governed action execution appears in recent action audit
- no PII leaks in partner-visible views

### Slice 5 - Billing And Merchant-Owned Consent Guidance

Deliver:

- read-only billing posture in Partner Portal
- clear merchant-only billing approval handoff
- explicit explanation why billing approval stays in Shopify
- merchant portal/Shopify Admin link for approval

Verification:

- partner cannot activate paid Shopify billing directly
- partner can see exact blocker and send merchant to the correct owner action

### Slice 6 - Shopify Embedded App Simplification

Deliver:

- Shopify embedded app shifts to merchant consent/status/guidance
- daily operations link out to Partner Portal or merchant launch workspace
- advanced operational controls removed or hidden behind operator-only context

Verification:

- merchant can still approve/deny/revoke partner access from Shopify
- install/reinstall/scope/billing/theme guidance still works
- Partner Portal can perform all migrated operations after approval

## Acceptance Gates

`010_ADMIN_TO_PARTNER_TRANSITION_READY` passes only when:

- after merchant approval, Partner Portal can manage all Loom Companion product configuration that does not require Shopify-native merchant consent
- revoking merchant approval immediately blocks Partner Portal mutations and sensitive reads
- Partner Portal exposes no provider internals, secrets, Coolify handles, or raw Bridge credential material
- Shopify Admin clearly explains which actions remain there and why
- Shopify Admin remains sufficient for install/reinstall, scope grant, billing approval, theme embed/block activation guidance, and partner access approval/revocation
- Partner Portal can run source preflight, refresh, selected reindex, and view vectorization health in staging
- Partner Portal can show usage, RAG evidence, and governed action audit without PII leakage
- full Shopify release gate remains green after the transition

## Release Impact

This transition is not a blocker for the current controlled design-partner launch if Shopify Admin remains available.

It is a blocker for a scalable partner-led launch motion because partners should not need merchant Shopify Admin access to operate Loom Companion after approval.

Public App Store/self-service production launch should not claim full partner-led administration until this transition passes the acceptance gate.
