# Shopify Companion Launch Truth Enforcement

Status: implementation handoff (2026-04-25)

Owner mode: technical LLM implementation session

Roadmap phase: Phase 0 — Canonical Launch Truth

Priority: P0

---

## Strategic Handover

This is the first implementation task before Storefront Product Shell, Starter Launch Package, Partner Enablement, UI redesign, or any second-product work.

The goal is to make the code, billing, storefront gates, admin UI, partner catalog, docs, and launch copy agree on the same product truth.

Canonical decisions:

- Shopify Companion is the anchor product.
- Loom Companion is embedded store intelligence, not an AI chatbot.
- Current tier truth is `Free / Starter / Elite`.
- Historical `Growth / Pro` references must be removed from active copy or explicitly marked historical.
- Free is **AI search only**.
- Order lookup is **not Free**.
- Starter is full read-only embedded store intelligence.
- Elite is Starter plus verified governed actions only.
- Partners are implementation partners, not passive acquisition partners.

Why this goes first:

- later roadmaps depend on correct entitlement and positioning truth
- Free scope affects billing, storefront gates, App Store copy, partner catalog, merchant UI, and support docs
- Starter cannot be packaged honestly until Free and Elite boundaries are clean
- partner enablement cannot build a usable intelligence catalog until tier truth is stable

---

## Read First

Read these before editing code:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](../SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
4. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](../SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
5. [RoadMaps_BackLog.md](../RoadMaps_BackLog.md)

Optional supporting docs:

- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](../SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](../SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
- [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](../SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
- [../LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](../../LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)
- [../PARTNER_DASHBOARD_STRATEGY_PLAN.md](../../PARTNER_DASHBOARD_STRATEGY_PLAN.md)

---

## Working Rule

The technical LLM session must keep this file updated:

- [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)

Append compact notes for:

- decisions made during implementation
- files changed
- tests run
- blockers
- skipped checks
- handoff state

Do not paste long logs, diffs, or noisy reasoning into the working context.

---

## Technical Handover

### Session Startup Checklist

- Confirm branch and unrelated local changes with `git status --short`.
- Read the working context, strategic context, launch truth, findings roadmap, and this handoff before editing.
- Search before changing so entitlement, storefront, UI, generated copy, and tests move together.
- Stage only files touched for Launch Truth Enforcement; leave unrelated `.DS_Store`, `log.txt`, and independent roadmap edits alone.
- Keep chat feedback short and put only compact decisions/status in `CODEX_WORKING_CONTEXT.md`.

Suggested first search:

```bash
rg -n "Growth|Pro|Free / Growth / Pro|order lookup|order-lookup|ORDER_LOOKUP|allowed surfaces|allowedSurfaces|Starter|Elite" \
  product-services/shopify-bridge-service \
  doc/Productization/future-work/MarketPlace/Products/Strategy
```

### Architecture To Know

- `product-services/shopify-bridge-service` is the Shopify Companion product edge: billing summaries, merchant admin, storefront preview/bootstrap, theme extension assets, and support/order lookup routes live here.
- Platform services remain the shared capability layer. Shopify Bridge should consume platform indexing/vectorization capability; do not create a separate Shopify-only vectorization path while enforcing launch truth.
- Merchant Shopify Admin should use merchant language such as `Knowledge Sync`, `Setup`, `Insights`, `Billing`, and `Support`. Raw runbooks, packet dumps, replay/index controls, and deep diagnostics belong in the merged partner/operator dashboard.
- Order lookup remains a product capability, but launch truth says it is not Free. Free must stay AI-search-only across visible UI and direct API/storefront access.

### Primary Code Map

Billing and entitlement:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/billing/service/ShopifyBridgeBillingService.java`
- `product-services/shopify-bridge-service/src/test/java/com/ai/fabric/product/shopify/bridge/billing/service/ShopifyBridgeBillingServiceTest.java`
- Check tier/surface models used by billing summaries before changing tests.

Merchant embedded admin:

- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`
- Watch generated launch/App Store/support packet builders in `App.tsx`; these often repeat tier and Free-scope truth.

Storefront and theme extension gates:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontBootstrapService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontPreviewService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontOrderLookupService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyStorefrontController.java`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/blocks/companion-order-lookup.liquid`

Platform and support integration:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/client/platform/PlatformShopifyStoreClient.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyBridgeAdminController.java`
- Support/readiness services and generated support runbooks may restate Free, Starter, Elite, order lookup, or partner/operator responsibilities.

Targeted test map:

- `ShopifyBridgeBillingServiceTest`
- `ShopifyStorefrontBootstrapServiceTest`
- `ShopifyStorefrontPreviewServiceTest`
- `ShopifyStorefrontOrderLookupServiceTest`
- `ShopifyMerchantControllerTest`
- `ShopifyBridgeAdminControllerTest`
- `ShopifyBridgeSupportReadinessServiceTest`
- `PlatformShopifyStoreClientTest` if platform payload contracts change

### Implementation Guidance

- Remove `order-lookup` from Free allowed surfaces at the entitlement source first, then update callers and tests to match.
- Verify direct route denial, not just UI hiding. A Free shop must not be able to call order lookup through storefront/API paths.
- Keep order lookup available for the correct paid/read-only tier if existing product behavior supports it; do not delete the capability.
- Remove active `Growth`/`Pro` copy or mark it historical where retention is necessary.
- Keep Elite copy limited to verified governed actions. Do not promise new write actions or automation not already implemented.
- Update merchant UI copy, generated launch packets, support runbooks, and partner-facing copy in the same pass so product truth stays consistent.

### Verification Plan

Run the narrowest reliable checks for touched code, then record results in `CODEX_WORKING_CONTEXT.md`.

Baseline for any implementation:

```bash
git diff --check
npm --prefix product-services/shopify-bridge-service/ui run build
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyBridgeBillingServiceTest,ShopifyStorefrontBootstrapServiceTest,ShopifyStorefrontPreviewServiceTest,ShopifyStorefrontOrderLookupServiceTest,ShopifyMerchantControllerTest,ShopifyBridgeAdminControllerTest,ShopifyBridgeSupportReadinessServiceTest \
  test
```

If theme extension JavaScript changes:

```bash
node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js
```

If only docs changed:

```bash
git diff --check
```

### Live Verification Guidance

- Local code changes are not production-visible until committed, pushed, and deployed.
- Do not repeatedly run full live verification when Railway/API returns HTTP 429 or code 1015; record the rate-limit blocker and retry later.
- Minimum post-deploy smoke is the Shopify bridge shell:

```bash
curl -fsS https://shopify-bridge-shopify-bridge-pr-production.up.railway.app/api/app/shell
```

- Authenticated `/api/app/store/*` checks require a valid Shopify embedded admin session; do not treat unauthenticated failures as product regressions.
- If full live verification is requested, start from `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_RESOURCES_MAP.md`, run only the relevant Shopify/platform checks, and write the run ID/status/blockers into `CODEX_WORKING_CONTEXT.md`.

### Required Handoff Output

Before ending the implementation session, update `CODEX_WORKING_CONTEXT.md` with:

- changed files
- new decisions
- tests/builds run and pass/fail
- live verification status or reason skipped
- blockers
- next concrete handoff

---

## Implementation Brief

Task:

- enforce Shopify Companion launch truth across active code and docs

Implement:

- find every active reference to `Free / Growth / Pro`
- replace active tier truth with `Free / Starter / Elite`
- mark historical references explicitly when they must remain
- find every place order lookup is exposed as a Free capability
- remove order lookup from Free entitlements
- remove order lookup from Free storefront gating
- remove order lookup from Free merchant UI
- remove order lookup from Free partner catalog/copy
- remove order lookup from Free App Store/support/launch copy
- confirm AI search remains the only Free surface
- confirm Starter owns full read-only embedded intelligence
- confirm Elite is gated to verified governed actions only

Do not:

- start Max Mode refactor
- start Storefront Product Shell work beyond launch-truth gates
- start Starter package polish beyond truth alignment
- start partner portal implementation
- start UI redesign
- start WooCommerce
- expand Elite claims
- add new product surfaces

---

## Expected Search Areas

Search broadly before changing:

- Shopify bridge billing and entitlement code
- storefront/theme extension gating
- merchant embedded app UI
- launch packet or App Store copy exports
- support/runbook export code
- partner/intelligence catalog docs or code
- pricing docs and active roadmap docs
- tests and fixtures that encode tier expectations

Suggested search terms:

```text
Growth
Pro
Free / Growth / Pro
order lookup
order-lookup
ORDER_LOOKUP
allowed surfaces
allowedSurfaces
Free
Starter
Elite
```

---

## Acceptance Criteria

Product truth:

- active product tier names are `Free / Starter / Elite`
- `Growth / Pro` does not appear in active launch, billing, merchant, partner, or App Store copy unless marked historical
- Free includes AI search only
- order lookup is not included in Free anywhere
- Starter is read-only embedded store intelligence
- Elite claims are limited to verified governed actions

Behavior:

- Free cannot access order lookup through visible UI
- Free cannot access order lookup through direct storefront/API route
- Starter cannot access write/governed action surfaces
- Elite-only surfaces are gated and claim-safe

Docs/copy:

- launch truth, pricing, active roadmap docs, merchant UI copy, partner catalog, and support copy agree
- partner wording stays implementation-partner-first
- no passive acquisition/affiliate framing is introduced

---

## Verification Requirements

Run the narrowest reliable checks for touched code.

Minimum expected verification:

- `git diff --check`
- targeted tests for billing/entitlement changes
- targeted tests for storefront gating changes
- targeted tests for merchant UI copy/gating changes when available
- build command for any frontend touched

If live checks are needed:

- avoid repeated full-suite spam if Railway/API returns HTTP 429/code 1015
- document rate-limit blockers in `CODEX_WORKING_CONTEXT.md`

---

## Exit Gate

This task is complete only when:

- code and docs agree on launch truth
- Free cannot access order lookup by UI or direct route
- AI search remains the only Free storefront surface
- active tier naming is `Free / Starter / Elite`
- old tier names are removed or clearly historical
- Starter and Elite boundaries are not expanded
- `CODEX_WORKING_CONTEXT.md` has final status, changed files, tests, blockers, and next handoff

---

## Implementation And Verification Summary

Completion date: 2026-04-25

Pushed commits:

- `08174962 Enforce Shopify Companion launch truth`
- `d908c499 Align Shopify Companion readiness gates with launch truth`

Implementation summary:

- Billing truth now uses `Free / Starter / Elite`; Free allowed surfaces are AI search only, Starter excludes order lookup, and Elite is the only tier with `order-lookup`.
- Storefront bootstrap, storefront preview, direct order lookup routes, theme extension rendering, and merchant UI copy now enforce the same launch truth.
- Support readiness no longer blocks Free or Starter on order lookup scope/webhook posture; Elite keeps the strict order lookup support gates.
- Platform Shopify readiness and go-live gates now treat non-Elite support readiness as ready when the product service returns `READY`, while preserving Elite order lookup requirements.
- Generated launch, App Store, support, partner, and review copy no longer claims Free or Starter order lookup access.
- Verification script assertions were updated so Free and Starter must not include `order-lookup`, support fallback is merchant handoff, and direct bridge admin verification can run when the deployed bridge shared secret is supplied as `SHOPIFY_BRIDGE_ADMIN_API_KEY`.

Build and test proof:

- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js` passed.
- `npm --prefix product-services/shopify-bridge-service/ui run build` passed.
- Shopify Bridge targeted Maven tests passed for billing, storefront bootstrap, storefront preview, order lookup, merchant controller, admin controller, support readiness, governed actions, merchant store service, and storefront controller coverage.
- Full Shopify Bridge suite passed with `mvn -f product-services/shopify-bridge-service/pom.xml -q test`.
- Platform backend targeted tests passed with `mvn -f Platfrom/backend/pom.xml -q -Dtest=ShopifyStoreConnectionServiceTest,ShopifyStoreGoLiveServiceTest,PlatformManagedProductAdminServiceTest test`.
- Full Platform backend suite passed with `mvn -f Platfrom/backend/pom.xml -q test`.
- `bash -n scripts/verify-shopify-companion.sh` passed.
- `git diff --check` passed.

Live verification proof:

- Full live Shopify Companion verification passed against:
  - platform: `https://ai-fabric-framework-production-324f.up.railway.app`
  - bridge: `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app`
  - shop: `shopping-companion-test.myshopify.com`
- Expected live launch truth was Free tier, active billing, enabled surfaces `ai-search`, order lookup unsupported, governed actions unavailable, chat fallback disabled, powered-by badge required, and catalog product cap `50`.
- Direct bridge admin checks passed after resolving the deployed bridge `SHOPIFY_BRIDGE_SHARED_SECRET` from Railway and supplying it only as the process-local `SHOPIFY_BRIDGE_ADMIN_API_KEY` with header `X-BRIDGE-API-KEY`.
- Verified direct admin endpoints included `/api/admin/overview`, `/api/admin/stores/{shop}/billing-summary`, `/api/admin/stores/{shop}/webhook-subscriptions`, `/api/admin/stores/{shop}/support-readiness`, `/api/admin/stores/{shop}/usage-summary`, `/api/admin/stores/{shop}/vectorization`, `/api/admin/stores/{shop}/actions/recent`, and `/api/admin/stores/{shop}/vectorization-source/{entityType}`.
- Final verifier proof line:

```text
PASS: storefront standalone AI search contract
Shopify Companion verification passed for shopping-companion-test.myshopify.com
```

Secret handling note:

- Do not paste the bridge admin key into chat, docs, commits, or logs.
- The verification script variable is `SHOPIFY_BRIDGE_ADMIN_API_KEY`; for the deployed bridge it must equal the Railway `SHOPIFY_BRIDGE_SHARED_SECRET`.
- A missing key skips direct bridge admin checks; a wrong key returns HTTP 401; an unconfigured bridge admin key returns HTTP 503 for `/api/admin/*`.

---

## Handoff Template

Append a compact note to `CODEX_WORKING_CONTEXT.md` using this shape:

```md
- Launch Truth Enforcement status: <complete/partial/blocked>.
- Changed files: <compact list>.
- Decisions: <only new decisions>.
- Verification: <commands run and pass/fail>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```
