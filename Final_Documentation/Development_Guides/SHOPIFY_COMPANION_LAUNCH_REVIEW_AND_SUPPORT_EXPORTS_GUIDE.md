# Shopify Companion Launch, Review, And Support Exports Guide

Status: developer and operator guide for the live-generated Shopify Companion export tooling (2026-04-23)

Purpose:

- document the merchant-app export surfaces added during the builder-roadmap hardening work
- show which live APIs and summaries feed each export
- define the safety rules for launch, review, lifecycle, and support packaging

Read this with:

- [Shopify Companion Developer And Store Admin Guide](./SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md)
- [Shopify Internal Development And Full Deployment Guide](./SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md)
- [Shopify Companion Merchant Launch And Support Guide](../User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md)

---

## 1) What Shipped

The merchant app now generates live, store-specific exports for:

- support bundle
- launch dossier
- App Store listing package
- design-partner rollout packet
- App Review guide
- review screencast script
- support runbook
- lifecycle and subscription packet

These exports are generated from the current live merchant session, not from a static marketing template.

The goal is:

- claim-safe launch packaging
- reviewer-safe product posture
- support-safe handoff guidance
- lifecycle and subscription visibility without raw platform debugging

---

## 2) Main Code Paths

Merchant export assembly currently lives in:

- `product-services/shopify-bridge-service/ui/src/App.tsx`

The major export builders are:

- `buildSupportBundle(...)`
- `buildLaunchDossier(...)`
- `buildAppStoreListingPackage(...)`
- `buildDesignPartnerRolloutPacket(...)`
- `buildAppReviewGuide(...)`
- `buildReviewScreencastScript(...)`
- `buildSupportRunbook(...)`
- `buildLifecycleSubscriptionPacket(...)`

The merchant app wires them into copy/download controls from the embedded admin UI.

Partner launch readiness and production-promotion review surfaces live in:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/service/PartnerEnablementService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/web/PartnerEnablementController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/web/MerchantPartnerAccessController.java`
- `Platfrom/partner-ui/src/pages/StoreWorkspacePage.tsx`
- `Platfrom/partner-ui/src/pages/MerchantApprovalPage.tsx`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`

These surfaces are not export-only. They gate whether a partner can prepare production promotion from the approved store workspace without exposing provider internals.

---

## 3) Live Data Sources Behind The Exports

### 3.1 Merchant store state

Primary store and lifecycle posture comes from:

- `ShopifyBridgeMerchantSessionResponse`
- `ShopifyBridgeStoreSummary`

This is where the exports get:

- install state
- onboarding state
- deployment and release state
- source toggles
- sync detail
- webhook detail
- widget settings

### 3.2 Storefront posture

Storefront placement and grounding posture comes from:

- `ShopifyStorefrontPreviewResponse`
- `ShopifyStorefrontPreviewService`

This is where the exports get:

- surface placement inventory
- theme editor activation URL
- grounding signals
- supported review-provider signals

### 3.3 Billing and tier posture

Commercial posture comes from:

- `ShopifyBridgeBillingSummary`

This is where the exports get:

- Free / Starter / Elite posture
- allowed surfaces
- product cap
- sync cadence
- badge requirements
- governed action packages
- confirmation and audit expectations

### 3.4 Webhook and live-update posture

Operational posture comes from:

- `ShopifyWebhookSubscriptionStatusSummary`
- `ShopifyBridgeStoreVectorizationSummary`

This is where the exports get:

- source preflight posture
- vectorization health
- live-update automation state
- recent Shopify live-update events
- webhook readiness
- subscription webhook posture
- indexing readiness

### 3.5 Partner launch readiness posture

Partner production-prep posture comes from Platform partner enablement APIs:

- `GET /api/partners/stores/{storeId}/launch-readiness`
- `POST /api/partners/stores/{storeId}/production-promotions`
- `POST /api/partners/client-implementations/{requestId}/merchant-invites`
- `GET /api/merchant/partner-access/{approvalCode}/workspace`
- `POST /api/merchant/partner-access/{approvalCode}/approve`
- `POST /api/merchant/partner-access/{approvalCode}/deny`
- `POST /api/merchant/partner-access/{approvalCode}/revoke`
- `POST /api/merchant/partner-access/{approvalCode}/production-promotions`
- `POST /api/merchant/partner-access/{approvalCode}/rollback-requests`

The launch-readiness response combines:

- active merchant-approved partner assignment
- store install, source, sync, and widget posture
- latest verification result
- latest evidence-bundle state
- go-live eligibility from the Shopify store readiness state
- merchant-safe blockers and next actions

Blocked and failed promotion attempts are written to partner audit evidence. The UI should show the merchant-safe reason and keep operator diagnostics behind Platform audit/support access.

Merchant approval links now resolve to a merchant-scoped launch workspace. The workspace can approve, deny, revoke, request production promotion, and request rollback/deactivation without exposing Coolify, deployment, provider, or secret internals.

Merchant approval invites can be requested from the partner implementation request flow or from Shopify Admin through the Bridge. Email delivery is controlled by:

- `PLATFORM_MERCHANT_EMAIL_ENABLED`
- `PLATFORM_MERCHANT_EMAIL_DRY_RUN`
- `PLATFORM_MERCHANT_EMAIL_FROM`
- `PLATFORM_MERCHANT_EMAIL_REPLY_TO`
- `PLATFORM_MERCHANT_EMAIL_SUBJECT_PREFIX`

When email delivery is disabled or dry-run is active, Platform records the notification status on the access request and emits audit evidence instead of pretending that delivery happened.

`scripts/verify-partner-enablement-live.sh` now checks that deployed Partner UI assets contain the Launch tab and production-promotion API wiring, proves the merchant approval-code workspace path, proves invite state, and proves merchant rollback/deactivation request recording. Set `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true` only when the run should intentionally request a real production promotion.

### 3.6 010.1 UI launch readiness surfaces

Plan `010.1` moves launch packaging from docs into product UI:

- Merchant Shopify Admin UI shows launch package, onboarding, package/tier posture, support path, evidence exports, design-partner posture, and rollback/deactivation guidance.
- Partner UI shows the 010.1 launch package, Free/Starter/Elite package explanation, design-partner terms, weekly value review prompts, promotion readiness, evidence, support, and escalation surfaces.
- Platform/Admin UI exposes `/shopify-launch-readiness` for App Store/private listing readiness, protected-data gates, Customer Account MCP and Checkout MCP gate status, hosted release-gate evidence, controlled production proof status, and operator diagnostics.

Merchant and partner surfaces remain provider-safe. They must not expose Coolify, Hetzner, provider handles, deployment internals, secret names, or secret values. Provider diagnostics belong in Platform/Admin UI only.

Public App Store readiness remains blocked until controlled production promotion proof, production provisioning verification, rollback/deactivation proof, protected-data claim proof, and support packaging are evidence-backed. Private/design-partner launch can proceed when the staging release gate and merchant-safe launch evidence are current.

### 3.7 Shopper-signal and ROI posture

Merchant value evidence comes from:

- `ShopifyBridgeUsageService`
- `ShopifyBridgeUsageSummary`
- `ShopifyBridgeUsageRoiSummary`

This is where the exports get:

- top shopper questions
- per-surface usage
- shopper journeys
- ROI status
- strongest surfaces
- rollout recommendations

---

## 4) Export Safety Rules

### 4.1 Claim-safe launch posture

Launch and review exports must stay aligned with the actual live store posture.

Do not generate language that implies:

- autonomous checkout
- universal review-provider coverage
- customer-safe order lookup
- order modification
- refund execution
- full support desk replacement

### 4.2 Elite posture rule

Only describe governed commerce when the live billing posture actually exposes it.

That means:

- action-capable tier is active or intentionally in scope
- confirmation is explicit
- audit trail is available
- the export language stays bounded to the live action families

### 4.3 Support and return boundary

Current support exports intentionally state the live governed support posture, including:

- whether customer-safe order lookup is actually enabled for the store
- whether the store is limited to recent orders or also has broader historical order scope
- which merchant handoff channels are configured
- which lifecycle stage and next actions still block a fully clean support rollout

The exports must still keep the read-only boundary explicit:

- refunds stay merchant-handled
- cancellations stay merchant-handled
- order edits stay merchant-handled
- address changes stay merchant-handled
- payment or account details stay outside Companion

---

## 5) Related Roadmap Items Covered By These Exports

This export tooling materially closes productization gaps around:

- merchant ROI visibility
- App Review packaging
- design-partner rollout packaging
- support runbook consistency
- lifecycle and subscription packaging

It does not close the deeper Milestone 7 execution gaps around:

- broader support integration repetition
- deeper lifecycle automation beyond the current computed contract
- deeper subscription integration beyond the current active-subscription and billing posture contract

Those still require deeper product and scope work.

---

## 6) Verification Flow

For export-related changes:

1. run `git diff --check`
2. run `npm -C product-services/shopify-bridge-service/ui run build`
3. if the export logic touches bridge summaries, run focused bridge tests
4. run `scripts/verify-shopify-companion.sh` after deploy when the change affects live posture or launch packaging

Current live verifier does not parse every export body directly.

Instead it confirms the live source data feeding the exports:

- store summary
- source coverage
- billing posture
- webhook diagnostics
- vectorization posture
- storefront bootstrap and query path

---

## 7) When To Change These Exports

Change the merchant exports when:

- a new live storefront surface is added
- a new source category becomes launch-safe
- billing posture changes
- support boundaries change
- Elite governed action posture changes
- App Review or design-partner packaging must reflect a new live truth

Do not change them just to make the product sound bigger than it is.

These exports exist to keep the live product story honest.
