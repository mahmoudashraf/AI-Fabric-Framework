# Shopify Companion Storefront Product Shell

Status: implementation handoff (2026-04-25)

Owner mode: technical LLM implementation session

Roadmap phase: Phase 1 — Storefront Product Shell

Priority: P0

Depends on:

- [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)

---

## Strategic Handover

Launch Truth Enforcement is complete.

Accepted state:

- Free allowed surface is `ai-search` only.
- `order-lookup` is gated to Elite.
- active tiers are `Free / Starter / Elite`.
- pushed commits: `08174962`, `d908c499`.
- full Shopify Bridge and Platform backend suites passed.
- live Shopify Companion verification passed.

This Phase 1 handoff starts the next roadmap: Storefront Product Shell.

The goal is to make Shopify Companion visibly feel like embedded store intelligence, not a chat widget with extra blocks.

Canonical product shape:

- embedded surfaces are the default value layer
- chat is the depth layer
- Max Mode is the long-term shopper shell
- Shopify bridge fetches grounded evidence
- runtime/LLM reasoning creates shopper-facing output
- page context and attached targets are distinct
- every surface respects Launch Truth entitlement gates

Why this goes next:

- the storefront product must become demoable before Starter can be packaged and sold
- Max Mode convergence prevents duplicated Shopify-only chat behavior
- page context and attachment handoff make embedded surfaces feel connected to the depth layer
- fetch-only intelligence removes brittle Shopify-specific heuristic reasoning
- Starter launch assets depend on real, merchant-placeable embedded surfaces

---

## Read First

Read these before editing code:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
4. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](../SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
5. [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](../SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
6. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](../SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)

Required companion implementation docs:

- [SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md](../../../Companion/SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md)
- [SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md](../../../Companion/SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md)
- [SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](../../../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)
- [SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md](../../../Companion/SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md)

Optional supporting docs:

- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](../SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](../../LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)
- [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](../SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)

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

---

## Implementation Brief

Task:

- implement the Storefront Product Shell convergence for Shopify Companion

Primary outcomes:

- Max Mode is the only long-term shopper shell path
- embedded intelligence surfaces hand off to Max Mode for depth
- shell conversation-mode contract is explicit and entitlement-aware
- page context and attached targets are separate and working
- shopper-facing comparison, similar-product, and policy behavior use fetch-only evidence plus runtime reasoning
- Free remains AI search only
- Starter-only surfaces remain gated
- no operator/debug language leaks into shopper surfaces

Do not:

- reopen Launch Truth decisions
- add order lookup back to Free or Starter
- start Starter launch packaging beyond shell readiness
- start partner portal implementation
- start UI redesign
- start WooCommerce
- expand Elite claims
- add unverified governed actions
- replace Shopify bridge storefront routes with direct runtime calls
- expose POC/operator debug controls on storefront

---

## Build Order

### Step 1: Freeze Contracts

Close the contracts before broad code edits:

- Shopify bootstrap payload shape
- Max Mode init/config shape
- `defaultConversationMode`
- `effectiveConversationMode`
- `allowedConversationModes`
- page context payload
- attached-target payload
- surface entitlement matrix

Exit:

- there is one implementation-ready contract for shell, modes, page context, attachments, and surfaces

### Step 2: Converge Host

Close:

- Max Mode as the long-term Shopify shopper shell
- duplicate-host prevention
- app embed and app blocks boot agreement
- legacy chat removal plan
- Shopify wrapper responsibility boundaries

Exit:

- every Companion surface can open the same Max Mode depth path

### Step 3: Convert Read-First Intelligence

Close:

- fetch-only product evidence path
- fetch-only policy evidence path
- fetch-only collection/search evidence path
- comparison through shared read-first runtime path
- similar-product guidance through shared read-first runtime path
- policy strip through retrieved policy evidence, not keyword matching

Exit:

- no active shopper-facing surface depends on brittle keyword or rule-only intelligence

### Step 4: Add Context And Attach Depth

Close:

- automatic page context propagation
- explicit attached-target controls on Companion-owned cards
- Max attachment reuse
- page-aware mode routing
- attach-and-ask behavior where appropriate

Exit:

- shopper can move from inline intelligence to Max depth without losing context

### Step 5: Polish Starter-Grade Surfaces

Close:

- richer comparison composition
- product insight grounding cues
- product FAQ rendering
- size/fit guidance where source data exists
- surface-level loading, empty, setup-blocked, and error states

Exit:

- surfaces look and behave like product features, not technical demos

---

## Technical Handover

### Session Startup Checklist

- Run `git status --short` and identify unrelated dirty files before editing.
- Read working context, strategic context, 001 completion summary, and all required docs above.
- Search before changing so wrapper, bridge, widget, theme extension, UI, and tests move together.
- Keep Launch Truth untouched unless a regression is found.
- Stage only files touched for Storefront Product Shell.
- Keep chat updates short and put compact implementation state in `CODEX_WORKING_CONTEXT.md`.

Suggested first search:

```bash
rg -n "legacy|max-mode|Max Mode|shellModeProfile|defaultConversationMode|effectiveConversationMode|allowedConversationModes|conversationMode|pageContext|storefrontContext|attached|attachment|compare_products|find_similar_products|policy strip|contextual pill|enabledSurfaces" \
  product-services/shopify-bridge-service \
  max-mode-widget \
  doc/Productization/future-work/MarketPlace/Products/Companion
```

### Architecture To Preserve

- Browser storefront traffic continues to use Shopify bridge storefront routes.
- Shopify bridge remains the storefront mediation layer for readiness, entitlement, query/suggestion routes, event recording, and store guardrails.
- Max Mode owns the long-term launcher, panel, composer, conversation state, suggestions, message rendering, attachment state, and shared UI.
- Shopify wrapper owns only Shopify-specific host concerns: Liquid data extraction, bridge bootstrap, page context normalization, Max init mapping, event recording, and block/surface handoff.
- Shopify Bridge uses platform-backed vectorization. Do not create a Shopify-only indexing/vectorization path.
- Merchant and shopper surfaces use safe language. Raw vectorization, provider, runtime, replay, deployment, and debug wording belongs in partner/operator surfaces.

### Primary Code Map

Theme extension and storefront shell:

- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.css`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.css`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-legacy-shell.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-max-mode-shell.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/max-mode-widget.iife.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/snippets/companion-widget-bootstrap.liquid`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/blocks/*.liquid`

Shared Max Mode widget:

- `max-mode-widget/src/entries/iife.ts`
- `max-mode-widget/src/mount.ts`
- `max-mode-widget/src/**`
- `product-services/shopify-bridge-service/deploy/shopify/sync-max-mode-widget.sh`
- `product-services/shopify-bridge-service/package.json`

Bridge storefront contracts:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontBootstrapService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontChatService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontPreviewService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyStorefrontController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/model/ShopifyBridgeStoreWidgetSettingsSummary.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/model/ShopifyBridgeUpdateWidgetSettingsRequest.java`

Merchant/admin controls:

- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`

Fetch-only/read-first paths:

- search for existing `compare_products`, `find_similar_products`, policy keyword matching, storefront read-action paths, and surface-specific heuristic logic before editing
- prefer bridge evidence retrieval plus shared runtime/LLM reasoning over Shopify-only rules

### Targeted Test Map

Use available tests around touched areas:

- `ShopifyStorefrontBootstrapServiceTest`
- `ShopifyStorefrontChatServiceTest`
- `ShopifyStorefrontPreviewServiceTest`
- `ShopifyStorefrontControllerTest`
- `ShopifyBridgeMerchantStoreServiceTest`
- `ShopifyMerchantControllerTest`
- `ShopifyBridgeAdminControllerTest`
- `ShopifyBridgeBillingServiceTest`
- `ShopifyBridgeSupportReadinessServiceTest`
- Max Mode widget tests if package has an available test command

---

## Implementation Guidance

### Host Convergence

- Do not run Max Mode refactor as an isolated cleanup.
- Every Max Mode change must improve embedded surface handoff, shopper depth, or shell consistency.
- Keep the existing live storefront path until Max-backed storefront is proven.
- Remove legacy chat UI only after the Max-backed path passes targeted and live checks.
- Avoid duplicate widget instances when multiple Companion blocks are present.

### Conversation Mode Contract

- Make `defaultConversationMode`, `effectiveConversationMode`, and `allowedConversationModes` explicit across bridge bootstrap, wrapper, Max init, and request context.
- Resolve effective mode server-side using store config, entitlement, readiness, and allowlist.
- Do not trust arbitrary query params or theme-only mode strings.
- Do not expose advanced modes unless runtime semantics, entitlement, and verification are real.

### Page Context And Attached Targets

- Page context is automatic wrapper grounding.
- Attached targets are explicit shopper-selected objects.
- Product page context should not appear as a removable attachment unless intentionally designed later.
- Companion-owned product/article/policy cards may expose `Add to Max` or `Add to Max and Ask`.
- Reuse existing Max attachment state and public host APIs such as `attachProduct(...)`; add or use generic `attachItem(...)` where needed.
- Do not invent a second Shopify-only attachment store.

### Embedded Surface Architecture

Canonical surfaces:

- `ai-search`
- `product-insight`
- `product-faq`
- `comparison`
- `policy-strip`
- `contextual-pill`
- chat/depth layer

Rules:

- Free exposes `ai-search` only.
- Starter gets the full read-only embedded surface set.
- Elite adds only verified governed actions.
- Disabled surfaces must explain plan/setup state in merchant-safe language.
- Shopper-facing copy should not lead with chatbot language.

### Fetch-Only Intelligence

- Convert brittle comparison, similar-product, and policy behavior to fetch evidence first, then let the runtime reason.
- Missing or weak evidence should produce honest fallback copy.
- Do not create new per-surface Shopify reasoning engines.
- Keep outputs grounded with source/grounding cues where available.

---

## Acceptance Criteria

Product shell:

- Max Mode is the only long-term shopper shell path.
- legacy chat UI is removed or explicitly isolated behind a temporary fallback.
- multiple Companion blocks on one page do not create duplicate shell instances.
- every embedded surface can hand off to Max Mode for depth.

Mode contract:

- bootstrap, bridge request context, runtime request context, and Max widget agree on mode metadata.
- `defaultConversationMode`, `effectiveConversationMode`, and `allowedConversationModes` are present and bounded.
- Starter cannot expose governed-action-only modes.

Context and attachments:

- product page handoff includes page context.
- attaching a product/document from a Companion-owned card creates a visible attached target.
- changing pages changes page context without keeping stale context.
- removing an attachment does not remove page context.

Entitlements:

- Free remains `ai-search` only.
- Starter-only surfaces are gated from Free by UI and direct route.
- order lookup remains Elite-gated.

Read-first behavior:

- comparison and policy outputs are based on fetched evidence.
- similar-product guidance can explain why results were chosen.
- weak source data is not invented.

Surface quality:

- surfaces render on desktop and mobile without layout conflicts.
- loading, empty, blocked, and error states are reasonable.
- no debug/operator language appears on shopper surfaces.

---

## Verification Requirements

Run the narrowest reliable checks for touched code.

Minimum expected verification:

- `git diff --check`
- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.js`
- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js`
- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-max-mode-shell.js`
- `npm --prefix product-services/shopify-bridge-service/ui run build` if merchant UI changes
- targeted Shopify Bridge Maven tests for touched services/controllers
- Max Mode package build/test command if `max-mode-widget` changes

Suggested Shopify Bridge targeted test baseline:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyStorefrontBootstrapServiceTest,ShopifyStorefrontChatServiceTest,ShopifyStorefrontPreviewServiceTest,ShopifyStorefrontControllerTest,ShopifyBridgeMerchantStoreServiceTest,ShopifyMerchantControllerTest,ShopifyBridgeAdminControllerTest,ShopifyBridgeBillingServiceTest \
  test
```

If theme extension packaging changes:

```bash
npm --prefix product-services/shopify-bridge-service run shopify:widget:build
npm --prefix product-services/shopify-bridge-service run shopify:widget:sync
```

Only run `shopify:widget:sync` if the session is intentionally updating the checked-in extension bundle.

---

## Live Verification Guidance

- Local code changes are not production-visible until committed, pushed, and deployed.
- Avoid repeated full-suite live verification when Railway/API returns HTTP 429 or code 1015.
- Authenticated bridge admin checks require `SHOPIFY_BRIDGE_ADMIN_API_KEY`; it must match deployed `SHOPIFY_BRIDGE_SHARED_SECRET`. Do not expose this key in chat, docs, commits, or logs.
- Minimum live storefront proof should include:
  - bridge shell availability
  - storefront bootstrap
  - Free store shows `ai-search` only
  - multiple embedded surfaces do not duplicate the shell
  - Max Mode opens from an embedded surface
  - page context is present in a depth handoff
  - attached target behavior works on a Companion-owned card if implemented

Record live verification status or blocker in `CODEX_WORKING_CONTEXT.md`.

---

## Exit Gate

This task is complete only when:

- Max Mode is the long-term Shopify shopper shell
- embedded intelligence surfaces are visibly real and merchant-placeable
- chat is clearly a depth layer, not the product identity
- page context and attached targets are distinct and working
- comparison, similar-product, and policy behavior use the shared read-first model
- Free remains AI search only
- Starter-only surfaces are gated correctly
- no operator/debug language leaks into shopper surfaces
- the storefront product can be demoed without explaining internal architecture
- `CODEX_WORKING_CONTEXT.md` has final status, changed files, tests, blockers, and next handoff

---

## Implementation And Verification Summary

Implementation status: code implementation is complete and pushed in commit `a3fdab98`; bridge/runtime live proof passed; Shopify-hosted theme extension deploy and browser proof passed. Only direct bridge admin live verification remains blocked until the deployed `SHOPIFY_BRIDGE_SHARED_SECRET` is available as `SHOPIFY_BRIDGE_ADMIN_API_KEY`.

Implemented:

- Removed the theme extension legacy shell path and made `max-mode` the only storefront shell path.
- Kept Shopify wrapper ownership narrow: Liquid context extraction, bridge bootstrap, Max Mode init, event wiring, and embedded surface handoff.
- Added entitlement-aware storefront chat/suggestion direct-route checks so Free stores cannot bypass UI gating for Starter-only surfaces.
- Added server-side conversation-mode filtering: Free exposes only `navigator`; non-actionable tiers cannot expose governed-action modes; action-capable tiers can expose configured action modes.
- Normalized Shopify page context whether sent as nested `storefrontContext` or top-level Max request context.
- Preserved automatic page context as hidden grounding and kept explicit card attachments separate from page context.
- Added Companion-owned result card `Add to Max` and `Ask in Max` handoff behavior for product/source cards.
- Hid Max Mode debug controls unless the host explicitly enables `features.debug`; the Shopify wrapper keeps debug disabled.
- Rebuilt and synced `max-mode-widget.iife.js` into the Shopify theme extension bundle.

Local verification passed:

- `git diff --check`
- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.js`
- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js`
- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-max-mode-shell.js`
- `node --check product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/max-mode-widget.iife.js`
- `bash -n scripts/verify-shopify-companion.sh`
- `npm --prefix max-mode-widget run typecheck`
- `npm --prefix max-mode-widget run build`
- `npm --prefix product-services/shopify-bridge-service run shopify:widget:sync`
- targeted Shopify Bridge Maven suite from this handoff
- full `mvn -f product-services/shopify-bridge-service/pom.xml -q test`

Skipped:

- `npm --prefix product-services/shopify-bridge-service/ui run build`, because no merchant UI files changed.

Live verification passed:

- Pushed implementation commit: `a3fdab98`.
- Ran `scripts/verify-shopify-companion.sh` against:
  - Platform: `https://ai-fabric-framework-production-324f.up.railway.app`
  - Shopify Bridge: `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app`
  - Shop: `shopping-companion-test.myshopify.com`
- Non-admin live verification passed end-to-end for platform health, bridge health, platform product-service checks, store binding, billing posture, support readiness, webhook/vectorization/governed-action diagnostics, bridge shell, embedded app shell/assets, storefront bootstrap, storefront suggestions, storefront AI-search query, storefront event, and post-bootstrap store summary.
- Storefront bootstrap live proof returned:
  - `billingTier`: `FREE`
  - `enabledSurfaces`: `ai-search`
  - `defaultConversationMode`: `navigator`
  - `effectiveConversationMode`: `navigator`
  - `allowedConversationModes`: `navigator`
  - `chatFallbackEnabled`: `false`
  - `actionCapability.available`: `false`
- Direct live entitlement bypass checks passed:
  - nested `storefrontContext.shopifySurfaceEntry=comparison` returned HTTP `403`
  - legacy top-level `shopifySurfaceEntry=comparison` returned HTTP `403`
  - response message: `Companion surface 'comparison' is not available for this store's current plan.`
- Shopify CLI non-interactive deploy was unblocked with the private handoff CLI token and a temp deploy env that preserved the full checked-in scope set.
- `npm --prefix product-services/shopify-bridge-service run shopify:app:info` passed without device-code login and resolved app owner context:
  - App: `Loom Companion`
  - Service account: `Loom AI Labs Ltd`
  - Dev store: `https://shopping-companion-test.myshopify.com`
  - Shopify CLI: `3.93.2`
- `npm --prefix product-services/shopify-bridge-service run shopify:app:deploy` passed without device-code login and released Shopify app/theme extension version `loom-companion-22`.
- Browser proof passed with screenshots under `/tmp/shopify-verify/`:
  - product page: `https://shopping-companion-test.myshopify.com/products/selling-plans-ski-wax`
  - Shopify CDN scripts loaded from `loom-companion-22`
  - desktop rendered Companion root `ready` with two surface cards
  - desktop AI-search embedded surface submitted a query and `Continue in assistant` opened Max Mode from the embedded surface
  - mobile rendered two Companion surface cards and opened Max Mode from the launcher
  - summary file: `/tmp/shopify-verify/verification-summary.json`
- Post-deploy non-admin `scripts/verify-shopify-companion.sh` passed again for `shopping-companion-test.myshopify.com`.

Live verification blockers:

- Bridge admin live checks could not pass with the locally available admin key material. A run with the configured local `SHOPIFY_BRIDGE_ADMIN_API_KEY` returned HTTP `401` from `/api/admin/overview`, which means the available value does not match deployed `SHOPIFY_BRIDGE_SHARED_SECRET`. A second run intentionally unset the admin key and passed all non-admin live checks. Do not record or expose the key in docs, chat, commits, or logs.
- Direct bridge admin live verification is still blocked because the deployed bridge shared secret was not available in local env or private handoff. Platform product-service proxy checks pass, but they do not expose the raw deployed secret required for direct `/api/admin/*` calls.

---

## Next-Session Unblock Pack

Use this section to finish the pending live proof without rediscovering the blocker.

### Required Secret Inputs

Do not paste these values into chat, docs, commits, screenshots, or logs.

- `SHOPIFY_BRIDGE_ADMIN_API_KEY`
  - Must equal the deployed Shopify Bridge `SHOPIFY_BRIDGE_SHARED_SECRET`.
  - Source it from the Railway service secret or the private handoff.
  - Verification header defaults to `X-BRIDGE-API-KEY`.
- `SHOPIFY_CLI_PARTNERS_TOKEN`
  - Must be a valid Partner Dashboard CLI token for the Shopify app owner context.
  - Required for non-interactive `shopify app deploy` from an LLM/CI session.
- Optional browser/session inputs:
  - `SHOPIFY_MERCHANT_AUTHORIZATION`
  - `SHOPIFY_EMBEDDED_HOST`
  - current storefront password or merchant browser session, if the test store is protected

### Bridge Admin Endpoint Unblock

First prove that the provided bridge admin key matches production:

```bash
curl -fsS \
  -H "X-BRIDGE-API-KEY: ${SHOPIFY_BRIDGE_ADMIN_API_KEY}" \
  "${SHOPIFY_BRIDGE_BASE_URL}/api/admin/overview" \
  >/tmp/shopify-bridge-admin-overview.json
```

Expected:

- HTTP `200`
- response contains the bridge service overview

Failure interpretation:

- HTTP `401`: supplied `SHOPIFY_BRIDGE_ADMIN_API_KEY` does not match deployed `SHOPIFY_BRIDGE_SHARED_SECRET`
- HTTP `503`: deployed bridge has no admin key configured
- missing local key: skip admin checks, but do not mark full live verification complete

Then rerun Shopify verification with admin coverage enabled:

```bash
scripts/verify-shopify-companion.sh
```

Record only pass/fail and endpoint class in `CODEX_WORKING_CONTEXT.md`; never record the key.

### Shopify Theme Extension Publish Unblock

From repo root, verify the non-interactive Shopify CLI credential is present:

```bash
test -n "${SHOPIFY_CLI_PARTNERS_TOKEN:-}"
npm --prefix product-services/shopify-bridge-service run shopify:preflight
npm --prefix product-services/shopify-bridge-service run shopify:app:info
```

Expected:

- commands complete without a device-code login prompt
- app owner/context resolves to the real Loom Companion Shopify app

Then publish the app/theme extension version:

```bash
npm --prefix product-services/shopify-bridge-service run shopify:app:deploy
```

Stop conditions:

- If Shopify CLI asks for interactive login, the token/session is missing or invalid for this app owner context.
- If app context is wrong, stop before deploy and fix the Partner token/app config.
- If deploy succeeds but storefront does not change, confirm the merchant store has enabled the app embed and is using the deployed app version.

### Final Browser Proof To Capture

After deploy, use Playwright or a real browser session:

- Open `https://shopping-companion-test.myshopify.com`.
- If storefront password is enabled, unlock with the current private handoff value.
- Open a product page or page where a Companion app block is placed.
- Verify an embedded Companion surface renders from the Shopify-hosted theme extension.
- Click the embedded surface action that opens Max Mode.
- Confirm Max Mode opens from the embedded surface and receives the page/surface context or attachment handoff.
- Repeat desktop and mobile viewport smoke.
- Save screenshots locally under `/tmp/shopify-verify/`.

Completion condition:

- Admin live checks pass with the matching bridge shared secret.
- Shopify app/theme extension deploy completes non-interactively.
- Browser proof shows Shopify-hosted embedded surface opening Max Mode.
- `CODEX_WORKING_CONTEXT.md` records the completed live proof, commands, screenshots path, and any remaining blockers.

---

## Handoff Template

Append a compact note to `CODEX_WORKING_CONTEXT.md` using this shape:

```md
- Storefront Product Shell status: <complete/partial/blocked>.
- Changed files: <compact list>.
- Decisions: <only new decisions>.
- Verification: <commands run and pass/fail>.
- Live verification: <passed/skipped/blocker>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```
