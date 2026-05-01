# Products Strategy Map

Status: strategy index and source-of-truth map (2026-04-24)

This file reorganizes the `Strategy` folder by **focus**, **authority**, and **current relevance**.

The goal is simple:

- make the folder usable again
- separate canonical product direction from supporting launch/GTM material
- mark discussion notes as discussion notes
- stop stale tiering or portfolio assumptions from quietly driving execution

---

## 1) Source-Of-Truth Rules

When multiple documents overlap, use this order of authority.

### 1.1 Shopify product direction

Use these first:

1. [PRODUCT_DIRECTION_DECISION_RECORD.md](PRODUCT_DIRECTION_DECISION_RECORD.md)
2. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](RoadMaps/SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
3. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
4. [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
5. [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
6. [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
7. [SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md](SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md)

### 1.2 Shopify storefront shape

Use:

- [LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)

Interpretation rule:

- embedded intelligence is the target product shape
- chat is the depth layer, not the primary product identity

### 1.3 Shopify tiers and pricing

Use:

- [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)

This is the current tier authority.

Do **not** use older `Free / Growth / Pro` references in older launch or GTM docs as billing truth.

### 1.4 Shopify execution waves

Use:

- [SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md](SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md)
- [SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md](SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md)

These define the broader enablement ladder after Companion becomes a strong Shopify product.

### 1.5 Reliability and shipping gate

Use:

- [Observability and Reliability Foundation Plan](../../../../../../doc/Operations/observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)

This is no longer stored in `Products/Strategy`.

---

## 2) Recommended Reading Order

If you are restarting planning from scratch, read in this order:

1. [PRODUCT_DIRECTION_DECISION_RECORD.md](PRODUCT_DIRECTION_DECISION_RECORD.md)
2. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](RoadMaps/SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
3. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
4. [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
5. [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
6. [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
7. [SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md](SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md)
8. [LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)
9. [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)
10. [SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md](SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md)
11. [Observability and Reliability Foundation Plan](../../../../../../doc/Operations/observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)

Then read the Companion implementation docs under:

- [../Companion](../Companion)
- especially:
  - [../Companion/SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md](../Companion/SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md)
  - [../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)

---

## 3) Strategy Docs By Focus

### 3.1 Canonical Shopify product and roadmap docs

- [PRODUCT_DIRECTION_DECISION_RECORD.md](PRODUCT_DIRECTION_DECISION_RECORD.md)
  - Canonical product boundary rules.
  - Decides Shopify first, Deep Resolver later, Thinker as template, Smart Brain separate.

- [RoadMaps/SHOPIFY_COMPANION_LAUNCH_TRUTH.md](RoadMaps/SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
  - Canonical launch truth for positioning, tiers, and surface gating.
  - Free is AI search only; order lookup is not Free.

- [RoadMaps/SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
  - Canonical execution roadmap from the current strategy review.
  - Defines the active phase sequence from launch truth through second-product gate.

- [RoadMaps/SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
  - Detailed Phase 1 roadmap.
  - Owns Max Mode convergence, embedded surfaces, fetch-only reasoning, page context, and attachments.

- [RoadMaps/SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
  - Detailed Phase 2 roadmap.
  - Owns Starter entitlement truth, read-only surface readiness, merchant activation, analytics, App Store assets, and support runbooks.

- [RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
  - Canonical current roadmap for Shopify in builder mode.
  - Resolves conflicts between pricing, embedded intelligence, launch, and portfolio docs.

- [SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md](SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md)
  - Canonical Shopify product ladder.
  - Interpret older action-tier wording through current `Companion Free -> Companion Starter -> Companion Elite -> Loom AI Platform for Shopify` launch truth.

- [LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)
  - Canonical storefront product-shape direction.
  - Strongly influences what should ship next on Shopify.

- [../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)
  - Canonical bounded plan for bringing real shell conversation modes into Shopify.
  - Use this with the builder-mode roadmap, not instead of it.

- [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)
  - Canonical tiering and pricing direction.
  - Current target tiers: `Free`, `Starter`, `Elite`.

- [SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md](SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md)
  - Category-expansion plan after Companion is strong.

- [SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md](SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md)
  - Release-wave translation of the enablement expansion plan.

### 3.2 Launch, GTM, distribution, and partner docs

- [LOOM_COMPANION_SHOPIFY_LAUNCH_PLAN.md](LOOM_COMPANION_SHOPIFY_LAUNCH_PLAN.md)
  - Useful for launch sequencing and one-app packaging.
  - Contains historical pricing/plan naming and must not override the pricing strategy doc.

- [LOOM_COMPANION_GO_TO_MARKET_PLAYBOOK.md](LOOM_COMPANION_GO_TO_MARKET_PLAYBOOK.md)
  - Tactical merchant acquisition playbook.
  - Useful once the embedded surfaces actually exist.

- [LOOM_COMPANION_OUTREACH_AND_CONTENT_STRATEGY.md](LOOM_COMPANION_OUTREACH_AND_CONTENT_STRATEGY.md)
  - Messaging and content system.
  - Needs a final pass before launch so tier messaging matches the latest pricing doc.

- [LOOM_COMPANION_PARTNER_PROGRAM_STRATEGY.md](LOOM_COMPANION_PARTNER_PROGRAM_STRATEGY.md)
  - Partner/distribution channel model.
  - Useful after launch posture and billing are finalized.

### 3.3 Portfolio and product-factory expansion docs

- [LOOMAI_LABS_PRODUCT_CATEGORIES_PLAN.md](LOOMAI_LABS_PRODUCT_CATEGORIES_PLAN.md)
  - Useful for long-range product-factory thinking.
  - Not the canonical near-term Shopify shipping plan.

- [LOOMAI_PORTFOLIO_ROADMAP_16_WEEKS.md](LOOMAI_PORTFOLIO_ROADMAP_16_WEEKS.md)
  - Aggressive portfolio scenario.
  - Treat as a stretch portfolio model, not the canonical builder-mode Shopify schedule.

- [PRODUCT_FACTORY_FACTORIZATION_CONSIDERATIONS.md](PRODUCT_FACTORY_FACTORIZATION_CONSIDERATIONS.md)
  - Useful guardrail against premature product-factory abstraction.

### 3.4 Platform UX, brand, and web-surface strategy

- [PLATFORM_UI_PERSONA_SEPARATION_PLAN.md](PLATFORM_UI_PERSONA_SEPARATION_PLAN.md)
  - Persona separation for merchant / partner / operator experiences.

- [PLATFORM_UI_REDESIGN_DIRECTION.md](PLATFORM_UI_REDESIGN_DIRECTION.md)
  - Productized control-plane design direction.

- [LOOMAI_PRO_SUBDOMAIN_AND_WEB_INFRASTRUCTURE_PLAN.md](LOOMAI_PRO_SUBDOMAIN_AND_WEB_INFRASTRUCTURE_PLAN.md)
  - LoomAI web presence and subdomain plan.

### 3.5 Discussion and exploratory material

- [Next_strategy.md](Next_strategy.md)
  - Valuable reasoning and product challenge material.
  - Treat as a discussion memo, not an authoritative roadmap.

- [RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md](RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md)
  - Technical integration guide.
  - Not part of the canonical Shopify product roadmap.

---

## 4) Current Conflict Resolutions

These points are now explicit and should not be re-litigated every session.

### 4.1 Pricing authority moved

Current pricing authority is:

- [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)

Older `Free / Growth / Pro` references are historical.

### 4.2 Embedded intelligence changed the storefront roadmap

The storefront is no longer just:

- one chat widget in a corner

The target is:

- embedded store intelligence, with chat as fallback/depth

### 4.3 Builder mode overrides portfolio fantasy

The next priority is:

- finish one strong Shopify Companion product

not:

- start shipping multiple new products because the platform theoretically can

### 4.4 Reliability is a gate, not a later polish pass

The reliability gate is real and lives in:

- [Observability and Reliability Foundation Plan](../../../../../../doc/Operations/observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)

---

## 5) Current Code-Backed Reality Check

The roadmap must stay grounded in current code status.

As of 2026-04-24:

- Shopify is a real platform-backed product, not a parallel stack.
- The Shopify Bridge service, embedded app, bootstrap/go-live flow, verification scripts, and live vectorization trigger pipeline are real.
- The storefront surface set is materially real, including AI search and multiple merchant-placeable embedded intelligence blocks.
- The remaining product-shell gap is Max Mode convergence, explicit mode contract closure, page context versus attached-target behavior, and removal of legacy chat as the long-term shell.
- The remaining read-first gap is fetch-only conversion for comparison, similar-product, and policy intelligence plus richer merchandising polish.
- Current launch truth is `Free / Starter / Elite`; Free is AI search only and order lookup is not Free.
- The remaining Starter gap is commercial launch packaging: entitlement alignment, merchant activation, analytics, App Store assets, support runbooks, and design-partner repetition.

See the detailed roadmaps for the validated gap map:

- [RoadMaps/SHOPIFY_COMPANION_LAUNCH_TRUTH.md](RoadMaps/SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
- [RoadMaps/SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
- [RoadMaps/SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
- [RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)

---

## 6) Folder Intent

Use this folder for:

- product direction
- roadmap decisions
- launch and distribution strategy
- portfolio sequencing

Do not use this folder as the home for:

- service runbooks
- operational reliability implementation details
- low-level technical implementation plans that already belong under `Companion` or `Operations`
