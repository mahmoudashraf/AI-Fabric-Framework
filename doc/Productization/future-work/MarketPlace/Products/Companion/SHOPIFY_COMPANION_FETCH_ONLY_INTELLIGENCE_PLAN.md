# Shopify Companion Fetch-Only Intelligence Plan

Status: implementation-ready child plan linked to builder-mode roadmap (2026-04-24)

This document defines how Shopify Companion should remove rule-based storefront intelligence and converge on one product model:

- UI remains prompt-first and admin-flexible
- shopper-facing intelligence is LLM-mediated
- Shopify bridge tools become fetch-only retrieval/control primitives

Read with:

- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](../Strategy/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md](SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md)
- [SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md](SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md)
- [SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md](SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md)

---

## 1) Code-Validated Current State

What is real today:

- many Shopify storefront surfaces already behave as LLM wrappers over `bridgeQueryUrl`
- the shopper UI is already comfortable with free-text responses, sources, and optional follow-up actions
- the Shopify bridge still contains manual heuristic storefront intelligence paths for:
  - `find_similar_products`
  - `compare_products`
  - policy keyword matching
- the storefront still has a dedicated read-action path used by the structured comparison surface

What that means:

- the product currently mixes two intelligence models
  - LLM wrapper surfaces
  - heuristic Shopify-bridge reasoning
- that is no longer the target architecture

---

## 2) Product Decision

The canonical product model is:

- embedded intelligence wrappers stay
- UI-originated prompts stay
- admin prompt flexibility stays
- rule-based storefront intelligence goes away
- bridge/runtime reasoning becomes LLM-led
- bridge tools stay fetch-only

Interpretation rule:

- `fetch-only` means retrieval, factual lookup, and deterministic control primitives
- `not fetch-only` means heuristic product ranking, handcrafted comparison reasoning, or keyword-based answer selection posing as product intelligence

The shopper product should feel like:

- one AI product entered from many embedded surfaces

not:

- a hybrid of AI wrappers plus Shopify-side heuristic widgets

---

## 3) Architecture Rule

Use this split:

- `Frontend`
  - prompt origin
  - rendering
  - card attach controls
  - local interaction state

- `Shopify bridge`
  - readiness, entitlement, safety, and routing policy
  - fetch-only Shopify tools
  - bounded deterministic controls

- `Runtime / LLM path`
  - actual shopper-facing reasoning
  - tool selection
  - grounded answer synthesis

Important clarification:

- UI-originated prompts are acceptable and should be treated like normal shopper/admin utterances
- prompt text does not need to move out of the UI
- backend policy remains authoritative for tool selection, action policy, tier gating, surface meaning, final response shaping, and safety/grounding
- free-text, text-first shopper rendering remains acceptable in the current implementation phase

Do not use the Shopify bridge as the final reasoning layer for:

- similar product ranking
- comparison summary generation
- policy interpretation

---

## 4) What Must Be Removed

Remove heuristic intelligence from the Shopify bridge/action layer.

Primary removals:

- `find_similar_products`
- `compare_products`
- candidate scoring helpers
- handcrafted comparison-difference generation
- policy keyword/token matching
- storefront read-action endpoint and related comparison-only storefront path

Code areas to retire or replace:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/action/service/ShopifyBridgeActionExecutionService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontReadActionService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/model/ShopifyStorefrontReadActionRequest.java`
- comparison read-action route in `ShopifyStorefrontController`
- `bridgeReadActionUrl` from storefront bootstrap
- structured comparison read-action path in `companion-embedded-surfaces.js`

---

## 5) What Must Stay

Keep deterministic behavior where determinism is the point.

Keep:

- `search_products`
- `get_product_details`
- `check_availability`
- raw product/catalog fetch primitives
- raw policy fetch/list primitives
- order lookup
- governed cart actions
- auth/readiness/tier gating
- event recording and analytics

Rule:

- deterministic actions and deterministic controls stay
- deterministic shopper reasoning does not

---

## 6) Target Tool Layer

The bridge tool layer should become:

- factual
- bounded
- reusable by the LLM path
- non-opinionated in final shopper interpretation

Examples:

- product search
- product details by handle/id/sku
- availability by sku or variant
- policies list/fetch
- article/page/metaobject fetch where applicable

Tool rule:

- tools return evidence
- the LLM returns the shopper-facing explanation

---

## 7) Storefront Surface Behavior

All shopper intelligence surfaces should converge on the same model:

- surface provides prompt plus page context plus optional attached targets
- backend/runtime chooses tools
- tools fetch facts
- LLM produces grounded answer
- UI renders text, sources, and optional follow-ups

This applies to:

- AI search
- product insight
- policy strip
- product FAQ
- contextual pill
- comparison

Comparison-specific rule:

- comparison remains a product surface
- comparison stops being a special heuristic read-action subsystem

---

## 8) Implementation Waves

### Phase 1: Canonical Contract

Must add:

- one explicit decision in docs that shopper surfaces are LLM wrappers over fetch-only tools
- one explicit deprecation note for heuristic comparison/policy paths
- one explicit ownership split between bridge tools and LLM reasoning

### Phase 2: Read-Action Path Removal

Must add:

- remove storefront read-action endpoint and bootstrap contract
- remove structured comparison dependency on `bridgeReadActionUrl`
- route comparison back through the same query-wrapper path as other surfaces

### Phase 3: Bridge Tool Cleanup

Must add:

- remove heuristic compare/similar/policy interpretation logic
- keep only fetch-oriented primitives
- rename policy tooling if needed so it no longer implies heuristic matching

### Phase 4: Verification

Must add:

- tests proving shopper surfaces still work through the query-wrapper path
- tests proving bridge tool layer remains fetch-only
- verification coverage that no shopper surface depends on removed read-action routes

---

## 9) Acceptance Criteria

- no shopper surface depends on heuristic ranking or handcrafted comparison reasoning
- no storefront bootstrap contract exposes `bridgeReadActionUrl`
- no dedicated storefront read-action controller/service remains
- comparison is implemented as an LLM wrapper surface, not a special heuristic subsystem
- policy answers are grounded through fetched policy evidence, not keyword matching
- deterministic cart/order/auth logic remains intact

---

## 10) What We Should Not Do

Do not:

- remove the embedded wrapper model
- force prompts out of the UI just because the backend owns policy
- replace LLM reasoning with new rule packs
- keep heuristic compare/similar code “temporarily” as a shadow product architecture
- confuse fetch-only tooling with no-tool reasoning

The target is:

- `LLM reasoning over fetch-only tools`

not:

- `raw unguided LLM with no grounding`
