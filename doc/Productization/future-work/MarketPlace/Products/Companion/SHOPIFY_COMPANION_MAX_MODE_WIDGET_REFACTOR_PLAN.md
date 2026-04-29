# Shopify Companion Max Mode Widget Refactor Plan

Status: detailed follow-on refactor plan (2026-04-24)

This document defines the concrete refactor path for converging the Shopify storefront widget onto the shared `max-mode-widget` shell instead of continuing to maintain a separate custom Shopify chat UI.

It should be read with:

- `doc/Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md`

Relevant code surfaces today:

- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.css`
- `max-mode-widget/src/entries/iife.ts`
- `max-mode-widget/src/mount.ts`
- `Platfrom/ui/src/pages/PocPage.tsx`

---

## 1) Decision

The correct follow-on direction is:

- keep the current Shopify bridge storefront routes
- keep the current theme app extension delivery model
- replace the custom Shopify widget UI shell with a thin Shopify wrapper that calls `MaxMode.init(...)`

The future target is:

- `Shopify theme app extension -> Shopify wrapper bootstrap -> MaxMode.init(...) -> Shopify bridge storefront routes`

This is a convergence refactor, not a runtime/auth rewrite.

Session-aligned rules:

- remove the legacy Shopify chat UI once the Max-backed shell is stable
- keep shopper interaction prompt-first and text-first in the current phase
- let users intentionally enable advanced modes from the Max widget instead of hiding them in implicit shell defaults
- honor admin-configured page-aware mode defaults through the shell contract rather than ad hoc theme behavior
- reuse the existing Max attachment system for page-context handoff and explicit attached targets

---

## 2) Why We Are Refactoring

The current Shopify storefront widget works, but it duplicates behavior already present in `max-mode-widget`.

Current duplication:

- launcher rendering
- open/close lifecycle
- message list rendering
- suggestions rendering
- composer behavior
- reset behavior
- result card rendering
- browser session handling
- simple error/status rendering

This duplication creates three problems:

1. behavior drift between the platform POC surface and the Shopify storefront surface
2. duplicated bug fixing across two widget implementations
3. slower feature rollout because new shared widget capabilities do not automatically reach Shopify

The current custom Shopify widget was acceptable as the fastest V1 path because it gave us a bounded storefront surface quickly. It should not remain the long-term direction.

---

## 3) Current State

Today we have two separate chat surfaces:

### 3.1 Platform POC surface

The platform POC page loads the real Max Mode IIFE bundle and initializes it with deployment-scoped adapter routes.

Characteristics:

- React + Shadow DOM widget
- deployment/operator oriented
- POC adapter routes
- operator-oriented debug and inspection features

### 3.2 Shopify storefront surface

The Shopify storefront uses a custom plain-JS theme app extension asset.

Characteristics:

- custom launcher and panel DOM
- custom message and suggestion rendering
- bridge bootstrap call first
- Shopify storefront context extraction from Liquid/data attributes
- custom event recording to the bridge

### 3.3 Important invariant

The refactor must preserve the current live storefront contract:

- browser talks only to Shopify bridge storefront routes
- Shopify bridge continues to own bounded store readiness and storefront traffic mediation
- runtime/auth posture remains backend-mediated private runtime by default

---

## 4) Target Architecture

The target shape is:

1. Shopify theme app extension renders only a minimal bootstrap root plus storefront context data attributes.
2. A small Shopify-specific wrapper script reads those attributes.
3. The wrapper calls the existing Shopify bridge bootstrap endpoint.
4. The wrapper maps bridge/bootstrap output into `MaxMode.init(...)`.
5. Max Mode owns the launcher, panel, conversation UI, suggestions, message rendering, and internal widget state.
6. The wrapper remains responsible only for Shopify-specific host concerns.

### 4.1 Wrapper responsibilities

The Shopify wrapper should own only:

- extracting `shopDomain`
- extracting page/product/collection context from theme data attributes
- calling storefront bootstrap
- converting storefront context into shared widget attachments/initial context
- converting explicit card-level attach clicks into the existing Max widget attachment model
- wiring bridge event recording
- applying Shopify-specific launcher label / welcome message / defaults

### 4.2 Max Mode responsibilities

Max Mode should own:

- widget mounting
- launcher rendering
- chat panel rendering
- message list
- composer
- suggestions UI
- conversation state
- conversation history behavior
- action/result rendering
- shared error states
- shared styling and responsiveness

### 4.3 Bridge responsibilities

The Shopify bridge should continue to own:

- storefront bootstrap
- readiness gating
- shopper session propagation
- query and suggestions endpoints
- event recording
- merchant/store aware guardrails

---

## 5) Non-Goals

This refactor should not:

- replace Shopify bridge storefront routes with direct runtime calls
- merge Shopify merchant UI into `max-mode-widget`
- turn the storefront widget into the full operator POC console
- expose debug inspector or operator-only controls on the storefront
- introduce a second storefront auth model
- change the theme app extension delivery path

---

## 6) Concrete Mapping: Shopify Bootstrap To Max Mode

The wrapper should translate the current Shopify bootstrap/runtime contract into the Max Mode config contract.

### 6.1 Current Shopify inputs

From Liquid/data attributes:

- `bridgeBaseUrl`
- `shopDomain`
- `launcherLabel`
- `pageType`
- `pageTitle`
- optional product context
- optional collection context

From bridge bootstrap:

- `available`
- `message`
- `shopDomain`
- `bridgeQueryUrl`
- `bridgeSuggestionsUrl`
- `bridgeEventUrl`
- `launcherLabel`
- optional welcome-message-equivalent storefront defaults

### 6.2 Target Max Mode init contract

The wrapper should call `MaxMode.init(...)` roughly as:

```ts
MaxMode.init({
  apiConfig: {
    chatBaseUrl: bridgeBaseUrl,
    runtimeRoutes: {
      chatQueryUrl: payload.bridgeQueryUrl,
      suggestionsUrl: payload.bridgeSuggestionsUrl,
    },
  },
  integrationMode: "backend-mediated-private-runtime",
  launcher: true,
  position: "bottom-right",
  features: {
    cart: false,
    debug: false,
    conversations: true,
    quickActions: true,
  },
  onEvent: ...
})
```

### 6.3 Required host additions

To avoid reintroducing custom UI logic outside Max Mode, the shared widget should support:

- host-provided initial welcome message
- host-provided initial starter suggestions or suggestion fallback
- host-provided context attachment(s)
- host-visible open/close/send/error events for bridge event recording
- host-level launcher label override
- host-provided conversation mode metadata and advanced-mode affordances where the shell contract allows them

If one of these is missing in the current Max Mode public API, add it to Max Mode instead of rebuilding the same behavior in the Shopify wrapper.

---

## 7) Gaps We Must Close

### 7.1 Public API gap

The current `window.MaxMode` IIFE API is still too POC-centric.

Today it exposes:

- `init`
- `open`
- `close`
- `toggle`
- `attachProduct`
- `sendMessage` placeholder
- `destroy`

What Shopify needs is more explicit host-level configuration for:

- initial context attachments
- initial greeting override
- bounded starter prompts / suggestions
- storefront event callbacks
- generic host attachment insertion while preserving existing `attachProduct(...)` compatibility
- explicit `defaultConversationMode`, `effectiveConversationMode`, and `allowedConversationModes` support where shell mode enablement requires it
- user-visible advanced-mode affordances that can be enabled intentionally when allowed by the backend contract

### 7.2 Shopify context gap

The Shopify storefront context currently exists as:

- page/product/collection data in the custom Shopify script

This should be normalized into a host attachment/config shape that Max Mode can consume directly.

Important rule:

- page context is automatic host grounding
- explicit product/article/policy attachments are separate host-selected targets

### 7.3 Build/distribution gap

The Shopify extension package must ship the Max Mode storefront bundle cleanly.

That means:

- a deterministic build step from `max-mode-widget`
- a sync/copy step into theme extension assets
- no manual copying
- no hidden dependency on the platform UI `public/` copy of the IIFE

### 7.4 Styling gap

The current Shopify widget styling lives in:

- `companion-app-embed.css`

The target styling should live primarily in Max Mode, with only minimal Shopify host/reset styling left in the wrapper if needed.

### 7.5 Shell-mode product gap

The Max-backed Shopify shell must also absorb the storefront mode decisions already made in the roadmap.

That means:

- advanced modes are intentional widget affordances, not hidden runtime behavior
- admin-configured page-aware mode defaults must influence shell startup where the backend allows them
- the long-term target is one Max-backed shopper shell, not perpetual `legacy` plus `max-mode` coexistence
- these mode behaviors must work with the same prompt-first, text-first UI posture rather than requiring a second specialized shell

---

## 8) Refactor Waves

### Wave 1: Contract Extraction

Goal:

- define the shared storefront host contract without changing the live store behavior

Required outputs:

- `ShopifyStorefrontHostConfig` shape for:
  - launcher label
  - welcome message
  - initial context attachment(s)
  - explicit attached target shape
  - starter suggestions
  - page-aware preferred mode / allowed modes
  - bridge event hooks
- explicit mapping from Shopify bridge bootstrap response to Max Mode config
- explicit file ownership split:
  - Max Mode shared widget
  - Shopify wrapper
  - Shopify Liquid root bootstrap

Success criteria:

- no storefront behavior change yet
- contract documented in code comments and tests

### Wave 2: Max Mode API Extension

Goal:

- add the missing host configuration points to Max Mode

Required outputs:

- support for host-provided initial greeting
- support for host-provided initial attachments
- support for generic `attachItem(...)` style host insertion while preserving `attachProduct(...)`
- support for bounded host-provided starter suggestions
- support for reliable event callbacks needed by Shopify bridge analytics
- support for bounded host-provided conversation mode metadata and advanced-mode affordances
- remove any POC-only assumptions from the IIFE public surface where they block Shopify reuse

Primary code ownership:

- `max-mode-widget/src/config.ts`
- `max-mode-widget/src/mount.ts`
- `max-mode-widget/src/entries/iife.ts`
- relevant React components/hooks inside `max-mode-widget/src`

Success criteria:

- POC still works
- Shopify wrapper can configure Max Mode without custom DOM chat rendering

### Wave 3: Shopify Wrapper Introduction

Goal:

- replace the custom Shopify chat UI implementation with a thin bootstrap wrapper

Required outputs:

- new lightweight Shopify wrapper asset
- wrapper calls bridge bootstrap
- wrapper converts storefront context into shared widget attachment/config input
- wrapper reuses the Max attachment system for Companion-owned card attach controls instead of inventing a parallel Shopify-only store
- wrapper passes through page-aware mode defaults and allowed-mode metadata from bootstrap to Max Mode
- wrapper calls `MaxMode.init(...)`
- wrapper records storefront events through bridge callbacks

Primary code ownership:

- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/blocks/companion-app-embed.liquid`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/*`
- build/sync scripts for packaging the Max Mode bundle into the extension

Success criteria:

- storefront continues using the same bridge endpoints
- launcher and chat UI now come from Max Mode, not the custom Shopify script

### Wave 4: Controlled Dual-Run Rollout

Goal:

- de-risk the cutover

Required outputs:

- feature flag or extension setting to choose:
  - legacy custom Shopify widget
  - Max Mode-backed Shopify widget
- verification script coverage for both modes during rollout
- ability to fall back without redeploying platform/runtime contracts

Success criteria:

- safe rollout on dev store first
- no loss of current live capability set

### Wave 5: Legacy Removal

Goal:

- remove the duplicate Shopify UI code after the Max Mode storefront path is stable

Required outputs:

- delete legacy custom launcher/panel implementation
- remove duplicate CSS/JS no longer needed
- update docs and support runbook
- keep only the minimal Shopify wrapper + shared Max Mode bundle
- keep advanced-mode affordances and page-aware defaults working on the surviving Max-backed shell

Success criteria:

- one shared widget shell
- one Shopify-specific wrapper
- no duplicated chat UI stack

---

## 9) File-Level Target Hierarchy

### Shared widget

Own in `max-mode-widget/`:

- chat UI
- launcher UI
- panel UI
- conversation state
- shared rendering
- shared host config contract

### Shopify wrapper

Own in the theme extension assets:

- DOM bootstrap
- Liquid dataset parsing
- bridge bootstrap call
- Shopify-specific event relay
- Max Mode initialization only

### Bridge backend

Keep in `product-services/shopify-bridge-service/`:

- storefront readiness
- bootstrap response
- storefront query/suggestions/event APIs
- merchant/store-specific traffic policy

---

## 10) Acceptance Criteria

The refactor is complete only when all of these are true:

1. Shopify storefront no longer uses a custom chat panel implementation.
2. The storefront launcher and chat panel are rendered by Max Mode.
3. The storefront still talks only to Shopify bridge storefront routes.
4. Product, collection, and page context still influence responses.
5. Event recording for widget opens, resets, and suggestion clicks still works.
6. Merchant-configured launcher label and welcome behavior still work.
7. The platform POC widget and Shopify storefront widget share the same core UI shell.
8. The Shopify wrapper stays thin and does not grow into a second widget implementation.
9. Users can intentionally enable allowed advanced modes from the Max widget.
10. Admin-configured page-aware mode defaults influence shell startup through the backend-backed shell contract.
11. The legacy Shopify chat UI is removed rather than left as a permanent second shell.

---

## 11) Main Risks

### 11.1 Over-importing POC behavior

Risk:

- the storefront gets operator-only complexity or debug features

Mitigation:

- keep Shopify feature set explicitly bounded
- disable debug/operator features in the Shopify wrapper config

### 11.2 Theme packaging complexity

Risk:

- build/deploy friction if the extension bundle sync is not deterministic

Mitigation:

- add a single supported sync/build script
- make CI/package flow fail if the bundle is stale or missing

### 11.3 Hidden host assumptions in Max Mode

Risk:

- Max Mode still assumes POC-only adapter routes or host behaviors

Mitigation:

- treat Shopify as a first-class host in the Max Mode public config contract
- add targeted tests for the Shopify host config path

---

## 12) Recommendation

The next implementation step should be:

1. Wave 1 contract extraction
2. Wave 2 Max Mode API extension
3. Wave 3 wrapper replacement behind a rollout flag

Do not jump straight to deleting the current Shopify widget.

The working live storefront path is now valuable as a fallback and should be kept until the Max Mode-backed storefront path is proven on the dev store and passes the live verification scripts.
