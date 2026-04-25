# Shopify Companion Starter Launch Package

Status: implementation handoff (2026-04-25)

Owner mode: technical LLM implementation session

Roadmap phase: Phase 2 - Starter Launch Package

Priority: P0

Depends on:

- [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
- [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)

---

## Strategic Handover

Launch Truth Enforcement is complete.

Accepted state:

- Free allowed surface is `ai-search` only.
- `order-lookup` is gated to Elite.
- active tiers are `Free / Starter / Elite`.
- pushed commits for launch truth: `08174962`, `d908c499`.
- full Shopify Bridge and Platform backend suites passed.
- live Shopify Companion verification passed with bridge admin checks enabled.

Storefront Product Shell is complete.

Accepted state:

- Shopify hosted extension deploy is complete.
- browser proof is complete.
- bridge admin verification is complete.
- full live verifier passed with admin checks enabled.
- docs/context committed and pushed through `e50e46d2`.
- no pending handoff items were reported.

This Phase 2 handoff starts the Starter Launch Package roadmap.

The goal is to make Starter sellable as a mature paid product from day one, not to build a thin V1 package. The owner is a solo developer using LLM coding sessions, so the product must reduce future support load through clear setup, honest claims, reusable launch packets, and repeatable verification.

Canonical product shape:

- Shopify Companion is embedded store intelligence, not a chatbot.
- chat and Max Mode are the read-only depth layer.
- embedded surfaces are the product identity.
- Starter is full read-only embedded store intelligence.
- Free remains AI search only.
- Elite is Starter plus verified governed actions only.
- order lookup is not part of Free or Starter.

Why this goes next:

- Launch Truth and Storefront Product Shell are now verified enough to package.
- Starter is the first serious paid product and needs merchant-safe activation, analytics, App Store material, support posture, and design-partner readiness.
- Partner enablement depends on a repeatable Starter package that integrators can deploy for client stores.
- The next implementation session should tighten the launch package around the shipped product, not expand into partner portal, UI redesign, WooCommerce, or Elite actions.

---

## Read First

Read these before editing code:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
4. [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
5. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](../SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
6. [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](../SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
7. [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](../SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
8. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](../SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)

Optional supporting docs:

- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](../SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](../../LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)
- [LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](../../LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)
- [PARTNER_DASHBOARD_STRATEGY_PLAN.md](../../PARTNER_DASHBOARD_STRATEGY_PLAN.md)

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

Do not paste long logs, diffs, secrets, or noisy reasoning into the working context.

Use this compact template:

```text
- Starter Launch Package status: <complete/partial/blocked>.
- Changed files: <compact list>.
- Decisions: <only new decisions>.
- Verification: <commands run and pass/fail>.
- Live verification: <passed/skipped/blocker>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```

---

## Implementation Brief

Task:

- implement the Starter Launch Package for Shopify Companion

Primary outcomes:

- Starter can be sold honestly as full read-only embedded store intelligence.
- Free remains AI search only across product, billing, docs, App Store, and partner-facing material.
- Starter does not include order lookup, write actions, return/exchange initiation, support ticket creation, discount application, or governed actions.
- Starter surfaces are live, gated, merchant-placeable, and visually mature enough for screenshots and demos.
- merchant setup, Knowledge Sync, launch readiness, and support handoff are understandable without operator explanation.
- basic analytics show product usage, unanswered questions, surface engagement, and future Elite demand signals without claiming Elite capabilities.
- App Store copy, App Review guide, screencast script, support runbook, launch packet, and design-partner checklist match the shipped product.

Starter included surfaces:

- product insight block
- AI search
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only Companion chat/depth layer

Starter included posture:

- unlimited products, if billing contract already supports it
- products, collections, policies, pages, articles/blogs, reviews, metafields, metaobjects where supported
- basic analytics
- optional `Powered by Loom Companion` badge
- custom accent color
- email support posture

Do not:

- reopen Launch Truth decisions
- add order lookup to Free or Starter
- start Elite governed action expansion
- start partner portal implementation
- start UI persona separation or UI redesign
- start WooCommerce
- start generic product factory expansion
- make chatbot-first copy
- expose vectorization, provider, queue, replay, runtime, or debug wording in merchant-facing launch material
- ship App Store or support claims that are more mature than the verified product

---

## Build Order

### Step 1: Align Commercial Truth

Close:

- active `Free / Starter / Elite` names across code, merchant UI, generated copy, launch docs, and support docs
- old `Growth / Pro` terminology in current launch material
- Free AI-search-only gate
- Starter allowed surface set
- Starter unlimited-product posture, if still product truth
- Starter sync/support posture
- optional powered-by badge posture
- custom accent color posture

Exit:

- every active product, billing, and copy surface tells the same tier story

### Step 2: Close Starter Surface Quality

Close:

- product insight launch readiness
- AI search launch readiness
- product FAQ launch readiness
- comparison launch readiness
- policy strip launch readiness
- contextual pill launch readiness
- read-only depth handoff
- shared loading, empty, setup-blocked, and error states
- mobile and desktop visual composition

Exit:

- a real store can demonstrate Starter without caveats about missing core surfaces

### Step 3: Finish Merchant Activation

Close:

- install and onboarding path
- theme activation guidance
- surface placement checklist
- live preview or verification flow
- plan ladder with current plan and available surfaces
- launch readiness checklist
- merchant-safe support handoff
- Knowledge Sync healthy, unhealthy, stale, and missing-source states

Exit:

- a merchant can activate Starter without reading engineering docs

### Step 4: Finish Analytics And Value Evidence

Close:

- query volume
- top shopper questions
- surface usage
- unanswered questions
- action-intent questions
- shopper journey by surface where available
- Starter-to-Elite demand signals without selling unavailable actions as live

Exit:

- a merchant can see that Starter is working and where store knowledge needs improvement

### Step 5: Capture App Store And Demo Assets

Close:

- final claim-safe App Store listing copy
- product-page screenshots
- AI search screenshots
- product FAQ screenshots
- comparison screenshots
- policy strip/contextual pill screenshots
- short demo clips or clear capture checklist
- App Review guide
- screencast script
- launch dossier export
- support runbook

Exit:

- App Store package is honest, legible, and ready for review

### Step 6: Rehearse With Design Partners

Close:

- design-partner checklist
- install support flow
- setup flow
- first usage review flow
- feedback capture template
- review ask criteria

Exit:

- the first design-partner loop can run repeatedly without custom operator improvisation

---

## Technical Handover

### Session Startup Checklist

- Run `git status --short` and identify unrelated dirty files before editing.
- Read working context, strategic context, 001 completion, 002 completion, and all required docs above.
- Search before changing so billing, storefront gates, merchant UI, theme extension, generated assets, docs, tests, and verifier move together.
- Keep Launch Truth untouched unless a regression is found.
- Keep Storefront Product Shell contracts intact unless a regression is found.
- Stage only files touched for the Starter Launch Package.
- Keep chat updates short and put compact implementation state in `CODEX_WORKING_CONTEXT.md`.

Suggested first search:

```bash
rg -n "Starter|starter|STARTER|Free / Starter / Elite|Growth|Pro|order lookup|order-lookup|App Store|App Review|screencast|design partner|Knowledge Sync|usage-summary|top questions|surface usage|action-intent|go-live|launch packet|support runbook|enabledSurfaces|surfacePlacements|billing-summary|poweredByBadgeRequired" \
  product-services/shopify-bridge-service \
  scripts/verify-shopify-companion.sh \
  doc/Productization/future-work/MarketPlace/Products/Strategy
```

### Architecture To Preserve

- Browser storefront traffic continues to use Shopify bridge storefront routes.
- Shopify bridge remains the storefront mediation layer for readiness, entitlement, query/suggestion routes, event recording, billing, setup, support readiness, and store guardrails.
- Max Mode remains the read-only depth layer for Starter.
- Shopify wrapper owns Shopify-specific host concerns: Liquid data extraction, bridge bootstrap, page context normalization, Max init mapping, event recording, and block/surface handoff.
- Shopify Bridge uses platform-backed vectorization. Do not create a Shopify-only indexing/vectorization path.
- Merchant UI says `Knowledge Sync`.
- Partner/operator surfaces may show vectorization, queues, providers, retries, and diagnostics.
- Shopper and merchant copy stays safe, concrete, and claim-matched to verified behavior.

### Primary Code Map

Billing, plans, and entitlement truth:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/billing/config/ShopifyBridgeBillingProperties.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/billing/service/ShopifyBridgeBillingService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/billing/model/ShopifyBridgeBillingSummary.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/billing/model/ShopifyBridgeBillingPlanSummary.java`

Merchant setup, support, readiness, and usage:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeMerchantStoreService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeSupportReadinessService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/analytics/service/ShopifyBridgeUsageService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyBridgeAdminController.java`

Merchant admin UI and generated launch material:

- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`

Theme extension and storefront surfaces:

- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-max-mode-shell.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/blocks/*.liquid`

Verification:

- `scripts/verify-shopify-companion.sh`

### Known Starting Points

The current Shopify merchant UI already contains Starter launch package hooks. Audit and harden them instead of duplicating:

- plan ladder and active plan checks
- Starter commercial availability copy
- launch packet/App Store package generation
- App Review guide generation
- screencast script generation
- support runbook generation
- design-partner package copy
- product shape/readiness checks
- storefront preview surface placement table
- `Knowledge Sync` merchant language
- claim-safe Free/Starter/Elite copy

Known claim-safe lines to preserve or strengthen:

- `Free: AI search only.`
- `Starter: full read-only store intelligence.`
- `Order lookup is not part of the Free or Starter launch package.`
- `Do not claim customer-safe order lookup for Free or Starter.`

---

## Acceptance Criteria

This handoff is complete when:

- Starter can be sold honestly as full read-only embedded store intelligence.
- Free is AI search only across billing, storefront, merchant UI, docs, App Store copy, and generated support material.
- Starter does not expose or claim `order-lookup` or governed write actions.
- Starter surfaces are live, gated, merchant-placeable, and visually suitable for App Store screenshots.
- merchant setup and Knowledge Sync states are understandable without operator explanation.
- blocked setup states show one clear next action.
- basic analytics/value proof is visible and merchant-readable.
- action-intent analytics do not imply Starter can take action.
- App Store copy/screenshots/demo guidance match the shipped product.
- App Review guide, screencast script, launch packet, support runbook, and design-partner checklist match current entitlement truth.
- no Elite action claims leak into Starter launch material.
- no raw operator/debug terminology leaks into merchant-facing launch material.

---

## Verification

Always run:

```bash
git diff --check
bash -n scripts/verify-shopify-companion.sh
```

If merchant UI changes:

```bash
npm --prefix product-services/shopify-bridge-service/ui run build
```

If theme extension JavaScript changes:

```bash
node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.js
node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js
node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-max-mode-shell.js
```

Run targeted backend tests when bridge code changes:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyBridgeBillingServiceTest,ShopifyStorefrontBootstrapServiceTest,ShopifyStorefrontPreviewServiceTest,ShopifyMerchantControllerTest,ShopifyBridgeAdminControllerTest,ShopifyBridgeSupportReadinessServiceTest,ShopifyBridgeUsageServiceTest,ShopifyBridgeMerchantStoreServiceTest \
  test
```

Run the full bridge suite if changes cross billing, storefront bootstrap, merchant admin, support readiness, analytics, and verifier contracts:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q test
```

Run live verification if deploy, entitlement, generated launch material, storefront surface, or App Review readiness behavior changes:

```bash
scripts/verify-shopify-companion.sh
```

For live admin checks:

- `SHOPIFY_BRIDGE_ADMIN_API_KEY` must match the deployed `SHOPIFY_BRIDGE_SHARED_SECRET`.
- Do not print, paste, commit, or log the secret.
- Use secret files or environment variables only.

---

## Completion Section For Implementing LLM

Append a compact completion update here before ending the implementation session.

Required completion fields:

- implementation summary
- changed files
- decisions made
- tests/builds run
- live verification status
- pushed commit refs, if pushed
- blockers or no pending handoff items

Do not include secrets, long logs, or raw diffs.
