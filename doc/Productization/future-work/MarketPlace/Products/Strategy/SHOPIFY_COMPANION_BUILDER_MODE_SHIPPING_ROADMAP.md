# Shopify Companion Builder-Mode Shipping Roadmap

Status: canonical Shopify builder-mode roadmap (2026-04-23)

This document is the current source of truth for how Shopify Companion should be finished, productized, priced, and sequenced in builder mode.

It exists because the strategy set is now rich enough to be useful, but also rich enough to contradict itself unless we resolve the conflicts explicitly.

Read this with:

- [PRODUCT_DIRECTION_DECISION_RECORD.md](PRODUCT_DIRECTION_DECISION_RECORD.md)
- [SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md](SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md)
- [LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)
- [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)
- [SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md](SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md)
- [../Companion/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md](../Companion/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md](../Companion/SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_APP_STORE_LISTING_PACKAGE.md](../Companion/SHOPIFY_COMPANION_APP_STORE_LISTING_PACKAGE.md)
- [Observability and Reliability Foundation Plan](../../../../../../doc/Operations/observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)

---

## 1) Executive Decision

The correct near-term Shopify roadmap is:

1. finish **Loom Companion** as a strong embedded-intelligence product
2. launch it with a real tier posture
3. expand into **Companion Pro / Elite** only after the read-first product is strong
4. expand to new products only after Shopify has real signal and the reliability gate is green

The product shape is now:

- embedded intelligence first
- chat as fallback/depth
- one app, not multiple apps
- Shopify as the anchor vertical, not the whole company identity

Builder-mode rule:

- ship one strong product deeply before multiplying surface area

---

## 2) Code-Validated Current State

The roadmap below is not aspirational-only. It is grounded in the current codebase.

### 2.1 What is already real

| Area | Code-backed reality | Assessment |
|---|---|---|
| Shopify app posture | `product-services/shopify-bridge-service/shopify.app.loom-companion.toml` is an embedded Shopify app with read-only scopes: `read_products`, `read_content`, `read_legal_policies`, `read_metaobjects`, and `read_metaobject_definitions`. | Good launch posture for read-first V1 with richer structured content depth. |
| Platform consumption | Shopify bootstrap/go-live flows in platform create a normal platform deployment, consumer binding, and marketplace bundle. | Correct architecture. |
| Merchant/admin surface | Embedded merchant app, storefront preview, sync, vectorization controls, billing summary, and verification flows exist. | Strong operator baseline. |
| Merchant legibility baseline | Merchant UI now exposes bounded store-intelligence health, live update freshness, shopper surface usage, and top shopper questions. Bridge admin APIs expose store usage and vectorization summaries for operator investigation. | The product is materially easier to operate and support without dropping into raw deployment internals. |
| Tier posture foundation | Bridge billing already models `Free / Starter / Elite`, allowed surfaces, chat fallback, powered-by requirements, and catalog caps. | Better than the old roadmap assumed; commercialization is now a productization problem, not a missing primitive. |
| Store sync baseline | Bridge sync/vectorization source services currently cover products, collections, pages, policies, and published articles. | Stronger Wave 1 foundation with real content-depth beyond static policies. |
| Live indexing foundation | Shopify vectorization trigger pipeline and bounded merchant/admin controls are now implemented. | Major foundation milestone completed. |
| Free-tier storefront wedge | The embedded AI search surface can now query bridge search/suggestions directly even when chat fallback is disabled. | Free is materially closer to a real product wedge. |
| Read-first action foundation | Shopify bridge action execution already supports `compare_products` and `find_similar_products` in addition to the baseline catalog/policy actions. | Better than the earlier strategy snapshot implied. |
| Verification | Live Shopify verification scripts and workflow entrypoints exist. Platform-hosted release verification now includes Shopify verification. | Shipping discipline is in place. |

### 2.2 What is still missing

| Strategy expectation | Current code reality | Gap |
|---|---|---|
| Multiple embedded intelligence surfaces | Theme app extension now ships the launcher app embed plus merchant-placeable `AI search`, `Contextual pill`, `Product insight`, `Policy strip`, `Product FAQ`, and `Comparison` app blocks. | Real progress toward the embedded-intelligence product shape, but still missing richer source coverage, stronger merchandising polish, and App Store packaging maturity. |
| Embedded intelligence product shape | Storefront no longer loads only a launcher shell. The app embed now layers embedded intelligence surfaces on top of the shell, and AI search can now operate as a direct bridge-backed surface even with chat fallback disabled. | Product identity is improving, but the delivery model still depends on a fixed app-embed host rather than a mature theme-surface system. |
| Max Mode storefront convergence | The Shopify embed loader already supports `legacy` and `max-mode`, and there is already a Shopify-specific Max Mode wrapper. | This is a real convergence track, but it is only partially complete and should not be treated as a standalone product milestone. |
| Real shell conversation modes | Shopify now persists `shellModeProfile`, exposes it in bootstrap/admin surfaces, and forwards it into bridge/runtime request context. | This is now a bounded mode-profile system, but not yet full multi-mode runtime semantics like true `assistant` / `deep` Shopify modes. |
| Richer Shopify data coverage | Code-backed coverage now exists for published blog/article content, bounded shopper-relevant product metafield enrichment, and opt-in metaobject ingestion in addition to catalog/content/policy sources. Judge.me-compatible review and rating metafields now flow into product sync, vectorization content, and read-first shopper actions when present. | Wave 1 data-expansion work is now materially stronger; the remaining gap is broader review-provider depth and richer merchandising polish, not total structured-content absence. |
| Read-first action breadth from strategy docs | Current read action bundle already includes `compare_products` and `find_similar_products` alongside the baseline catalog/policy actions. | The remaining gap is richer generated rendering, size/fit guidance, and surface-specific product UX rather than total action absence. |
| Tiered commercial model | Bridge billing already models `Free / Starter / Elite`, allowed surfaces, chat fallback, product caps, and explicit Elite governance posture (`confirmation`, `audit`, `action packages`). Merchant and platform admin UIs now surface those details directly. Governed action grants, audit history, and shopper-safe cart action surfaces are now materially real in the bridge/theme-extension stack. | The remaining gap is fully aligned commercial rollout, live Elite-plan rollout, and launch-safe packaging, not missing technical foundations. |
| Free-tier distribution wedge | Pricing strategy says Free is AI search only. | AI search is now real in both the app embed and a merchant-placeable theme block. The remaining gap is proving the commercial/App Store story cleanly. |
| Elite posture | Pricing strategy says Elite is read+write with governance. | Shopify now has bounded governed action capability for `add to cart`, `cart update`, and `variant guidance` with explicit confirmation, signed grants, audit trail, and platform-admin investigation visibility. The remaining gap is commercial rollout and broader action depth, not total absence of Elite execution support. |

### 2.3 What this means

Three conclusions are now clear:

1. **The platform and control-plane foundation are ahead of the storefront product surface.**
2. **Pricing strategy is ahead of billing/entitlement implementation.**
3. **GTM messaging is ahead of the actual embedded surface inventory.**

That is not a failure. It just means the next roadmap must prioritize product-shell completion, not more abstract platform work.

---

## 3) Strategy Tensions We Must Resolve

### 3.1 Embedded-intelligence rollout vs pricing rollout

The embedded-intelligence strategy stages AI search later.

The pricing strategy says:

- Free = AI search only

Those two cannot both be true operationally.

Resolved rule:

- if Free is the real distribution wedge, **AI search must move earlier**
- otherwise the Free tier is not a product, only a pricing idea

### 3.2 Older launch-plan pricing vs new pricing strategy

Older launch docs still use:

- `Free / Growth / Pro`

Current pricing authority is:

- `Free / Starter / Elite`

Resolved rule:

- use the new pricing strategy for all roadmap, billing, GTM, and launch decisions

### 3.3 Builder mode vs portfolio expansion

The portfolio docs are useful, but they are not a command to start five products now.

Resolved rule:

- Shopify Companion must become a strong, legible, launchable product first
- WooCommerce is the only justified near-term second product, and only after Shopify reaches launchable quality

---

## 4) Canonical Shopify Product Ladder

The product ladder remains:

1. **Loom Companion**
2. **Loom Companion Pro / Elite posture**
3. **Loom AI Platform for Shopify**

Interpretation:

- `Loom Companion` is the read-first embedded-intelligence product
- `Companion Pro / Elite` is the action-capable commercial expansion of the same app
- `Loom AI Platform for Shopify` is the later platform packaging layer after the opinionated app proves demand

Deferred:

- Deep Resolver as a blocker for the current roadmap
- Smart Brain as part of the current Shopify shipping stream

---

## 5) Missing Companion Elements

These are the missing elements that must be completed before we can claim the full Companion product is done.

### 5.1 Storefront product shell

Missing:

- stronger comparison merchandising polish on top of the now-real comparison surface
- clean host contract between Shopify theme extension and shared Max Mode shell
- fuller platform-backed shell conversation modes for Shopify
- full removal of long-term dual-shell maintenance as a product dependency

Already materially real:

- AI search is now a real app-embed surface with direct bridge-backed query/suggestions handling even when `chatFallbackEnabled=false`
- AI search is now also a merchant-placeable Shopify app block
- contextual pill is now available as a merchant-placeable Shopify collection/product block with inline grounded answers
- product insight is now available as a merchant-placeable Shopify product-page block
- policy strip is now available as a merchant-placeable Shopify product-page block
- product FAQ is now available as a merchant-placeable Shopify product-page block with inline grounded answers

Important rule:

- the Max Mode refactor is required here, but only as an enabling convergence track
- it should make new storefront intelligence surfaces cheaper and safer to build
- it should not be run as an isolated cleanup milestone while the actual product surfaces are still missing
- shell mode enablement should sit on top of that converged host contract
- see [SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)

### 5.2 Data coverage

Now materially real:

- published Shopify blog/article coverage through sync, vectorization, source toggles, and webhook-triggered refresh
- Judge.me-compatible review and rating metafield ingestion on Shopify products
- opt-in metaobject coverage through source toggles, source preflight, sync, vectorization paging, and indexed-field catalog support
- merchant-visible source-preflight signals now expose detected review-provider coverage and top metaobject types instead of forcing operators to infer source depth from raw counts alone

Still missing:

- broader review-provider depth beyond product-metafield ingestion
- metaobject-backed richer structured commerce content

### 5.3 Read-first capability depth

Missing:

- richer comparison rendering built on grounded compare action evidence
- richer similar-product rendering built on existing `find_similar_products`
- richer size/fit guidance
- surface-specific rendering contracts instead of generic chat-only rendering

### 5.4 Commercialization

Missing:

- tier-aware storefront gating
- Shopify billing mapping aligned to the pricing strategy
- Elite commercial activation and packaging that only advertises governed action depth once the live plan rollout is active

Now materially real:

- plan-aware merchant UI with a visible tier ladder, allowed surfaces, chat-fallback posture, and activation affordances
- tier-aware placement guidance in the merchant UI
- merchant-visible store intelligence readiness summary
- merchant-visible launch and App Review readiness summary
- bounded surface usage and top-question analytics for shopper traffic
- bounded ROI evidence in the merchant app and exported launch/support materials based on live shopper-assist, decision-support, and governed-commerce signals
- governed shopper action grants, audit history, and platform-admin investigation visibility for the Elite guided-commerce package

### 5.5 Merchant legibility

Missing:

- feature/tier explanations that match the actual product
- no debug leakage in shopper-facing surfaces

Now materially real:

- bounded per-surface shopper analytics
- shopper-journey summaries now connect questions, interactions, comparison read-actions, and governed-commerce completions by surface instead of only showing raw counts
- clear freshness/indexing-health view for merchants
- bridge-admin investigation endpoints for store usage and vectorization state
- platform-admin investigation surface for recent governed commerce actions

### 5.6 Shipping package

Missing or still needing hardening:

- App Store screenshots and final product story aligned to embedded intelligence
- pricing copy aligned to actual product tiers
- design-partner onboarding loop
- support and launch playbooks fully consistent with the shipped product surface set

Now materially real:

- canonical App Store listing copy baseline exists
- App Review guide, screencast script, design-partner checklist, and support runbook are now aligned around the current embedded surface set and optional Elite posture
- the merchant app now exports a claim-safe App Store listing package, App Review guide, review screencast script, support runbook, and design-partner rollout packet directly from the current live store posture

---

## 6) Canonical Milestone Roadmap

### Milestone 0 — Platform-Backed Shopify Foundation

Status:

- complete enough to stop treating Shopify as speculative

Included:

- bridge install/auth
- embedded merchant admin
- platform bootstrap/go-live
- theme app embed delivery
- live vectorization trigger pipeline
- verification and operational visibility

This milestone is already real.

---

### Milestone 1 — Free-Tier Distribution Wedge

Goal:

- make the **Free** tier real

Why this milestone comes first:

- pricing strategy makes Free the distribution engine
- Free currently has no real implemented storefront wedge

Must ship:

- AI search surface as a real storefront entry point
- basic entitlement boundary for `Free`
- 50-product cap enforcement
- daily sync posture for Free
- required powered-by badge

Can coexist with:

- current chat fallback
- current single app-embed delivery while the multi-block model is being built

Progress note:

- the direct bridge-backed AI search surface is now real inside the Shopify app embed
- remaining Milestone 1 work is mostly around merchant placement flexibility, commercial clarity, and App Store legibility

Exit criteria:

- a merchant can install the app and get real value from Free without inventing a pricing story
- Free can be described honestly in App Store copy

---

### Milestone 2 — Embedded Intelligence Base

Goal:

- move the shopper experience away from widget-first and into embedded-intelligence-first

Must ship:

- contextual pill
- Max Mode convergence for Shopify host integration
- product insight block
- contextual policy strip
- bounded Shopify shell conversation-mode support based on platform-owned shell config

Required supporting work:

- storefront host contract cleanup
- lightweight block runtime or shared block host
- no operator/debug leakage in shopper surfaces
- bootstrap/chat plumbing for safe `defaultConversationMode` consumption in Shopify

Milestone rule:

- treat the Max Mode refactor as a required enabling track inside Milestone 2
- do not treat it as a standalone milestone that can be declared complete while embedded surfaces are still absent
- all new Shopify storefront intelligence surfaces should build on the converged host contract rather than creating another Shopify-only shell path
- do not expose fake `assistant` or `deep` mode switches before runtime semantics, entitlements, and verification are real

Exit criteria:

- the product is visibly no longer “just a chatbot”
- at least two embedded intelligence surfaces are real and merchant-placeable

---

### Milestone 3 — Starter Completion

Goal:

- make **Starter** real as the read-only workhorse tier

Must ship:

- product FAQ block
- broader read-first discovery depth
- plan-aware entitlements for Starter
- unlimited products for Starter
- 2-hour sync posture for Starter
- basic analytics:
  - query volume
  - top questions
  - surface usage

Progress note:

- query volume, top shopper questions, bounded shopper surface usage, and shopper-journey-by-surface summaries are now materially real in the merchant app
- published articles/blog content is now materially real in the Shopify source pipeline
- multi-provider review-aware metafield support is now materially real across Judge.me, Stamped, Okendo, Loox, Yotpo, and Shopify Product Reviews compatible Shopify metadata
- plan-aware merchant UI is now materially real in the bridge admin app
- explicit Elite governance posture is now visible to merchants and platform admins through the live billing contract
- platform-admin store investigation now has explicit source-depth and launch/commercial readiness surfaces instead of forcing operators to infer roadmap status from raw preflight and vectorization data
- storefront product-insight surfaces now expose shopper-visible grounding cues for reviews, policies, buying guides, and structured content instead of relying only on generic summary text
- merchant launch/readiness views now surface detected review-provider and metaobject-type evidence from live source preflight instead of only generic capability claims
- merchant ROI visibility is now materially real through bounded live value evidence instead of raw usage counts alone
- the main remaining Milestone 3 gap is broader live rollout repetition and higher-fidelity merchandising composition, not missing read-first source depth or tier legibility

Recommended supporting data work:

- broader review-provider depth beyond Judge.me-compatible metafield ingestion
- richer merchandising composition on top of the now-real metaobject/article source base

Exit criteria:

- Starter can be sold honestly as “full read-only store intelligence”
- the surface set in the pricing doc is substantially real

---

### Milestone 4 — Launch-Ready Loom Companion

Goal:

- make the product launchable, not just technically impressive

Must ship:

- final merchant onboarding flow
- plan-aware billing copy
- App Store assets and story
- design-partner checklist closure
- support/runbook consistency
- merchant-facing freshness/indexing health
- strong live verification discipline

Progress note:

- merchant-facing freshness/indexing health is now materially real
- live verification already checks the bridge admin investigation contract and storefront behavior end to end
- merchant-facing launch readiness now includes explicit commercial/governance checks instead of only surface/readiness prose
- the merchant app now exposes a claim-safe launch packet that turns the live surface set, source depth, and Elite governance posture into App Review-safe product packaging guidance
- the merchant app now exposes a concrete go-live checklist with direct actions for theme activation, vectorization reconcile, billing activation, install recovery, and dossier export
- merchants and operators can now export a launch dossier markdown packet instead of stitching App Review and design-partner notes together from raw diagnostics
- platform admins now get the same launch/commercial posture and source-depth view directly in the platform store investigation surface

Non-negotiable launch rule:

- all product claims in listing copy must match the surfaces that are actually shipped

Exit criteria:

- App Store submission package is honest, legible, and review-safe
- design-partner rollout can be repeated without operator improvisation

---

### Milestone 5 — Elite Foundation

Goal:

- make **Elite** commercially and technically credible

Must ship first:

- real entitlements for Elite
- action governance posture exposed to merchants/admins
- safe action auditability
- tier-aware UI and packaging

Progress note:

- the live billing contract now exposes Elite confirmation posture, audit availability, and packaged action families to both merchant and platform-admin surfaces
- governed `add-to-cart`, `cart update`, and `variant guidance` surfaces are now materially real with bridge-issued grants, signed completion, shopper confirmation, and audit history
- the remaining Milestone 5 gap is live commercial rollout repetition and merchant adoption evidence, not missing governance primitives or missing operator visibility

Elite should not launch as a promise bundle.

It must be backed by real action surfaces.

---

### Milestone 6 — Elite Actions and Guided Commerce

Goal:

- move from read-only intelligence into safe, bounded action-taking

Must ship:

- add-to-cart
- cart update
- variant guidance
- action allowlist
- audit trail
- confirmation interception

Only after that should Elite messaging include:

- “takes action”

Progress note:

- the current Shopify implementation now has bounded governed commerce for `add-to-cart`, `cart update`, and `variant guidance`
- the remaining Milestone 6 gap is broader action depth and live commercial rollout rather than zero action capability

Deep Resolver belongs here only if it materially improves Elite outcomes. It is not a prerequisite for Milestone 5.

---

### Milestone 7 — Commerce and Support Enablement

Goal:

- move from shopping companion to broader Shopify AI enablement

Must ship:

- one support integration
- one lifecycle integration
- one subscription integration
- customer-safe order lookup
- return guidance / bounded support handoff
- better merchant ROI visibility

Progress note:

- merchant ROI visibility is now materially real through bounded shopper-assist, decision-support, and governed-commerce evidence in the merchant app and launch/support exports
- support/lifecycle/subscription packaging is now materially stronger through live-generated App Review and support playbooks plus bounded return/handoff guidance
- lifecycle and subscription posture are now exportable directly from live install, billing, webhook, sync, and release state in the merchant app
- customer-safe order lookup is now implemented as a bridge-governed, read-only support surface with exact order number plus checkout email verification, scope/webhook readiness checks, and merchant/platform support-readiness diagnostics
- merchant support handoff is now configurable as a first-class store profile and is surfaced through support readiness, launch/support exports, and lifecycle next actions
- active support subscriptions now surface as structured live subscription objects instead of name-only hints
- the remaining Milestone 7 gap is live rollout completion and broader support integration repetition, not missing support/lifecycle/subscription primitives or missing order-lookup implementation

This milestone aligns with the existing AI enablement expansion plan.

---

### Milestone 8 — Second Product And Portfolio Expansion

Only after:

- Milestones 1–4 are complete
- Shopify Companion has real install and paid-signal evidence
- the reliability gate is green

Then:

1. **WooCommerce Companion** is the correct second product.
2. **Loom Docs** is the correct third product after the reliability gate.
3. **Loom Comply** and other higher-value verticals follow after that.

This is the disciplined product-factory sequence.

---

## 7) Tier-Aligned Product Map

This is the target tier map that implementation should converge toward.

| Tier | Product truth | What must be real |
|---|---|---|
| Free | distribution engine | AI search, capped catalog, daily sync, powered-by badge |
| Starter | full read-only store intelligence | insights, policy strip, FAQ, comparison, chat fallback, basic analytics |
| Elite | read + action with governance | cart/support actions, confirmation, audit, richer sync, advanced analytics |

Important rule:

- do not sell a tier before the tier’s core product truth is actually implemented

---

## 8) Builder-Mode Shipping Gates

Builder mode does not mean “keep building forever.”

It means:

- ship deliberately
- ship honestly
- ship on reliable foundations

Before declaring Shopify Companion “done enough” to expand beyond it, these gates must be green.

### 8.1 Product gate

- Free tier is real
- Starter tier is real
- embedded intelligence is the visible product identity
- chat is a depth surface, not the only meaningful surface

### 8.2 Operational gate

- release verification passes live
- Shopify verification flows stay green
- support runbook and design-partner rollout are repeatable
- observability/reliability gate is on track for multi-product operation

### 8.3 Commercial gate

- App Store listing copy matches the product
- billing and entitlements are coherent
- first design-partner feedback loop exists
- product claims are legible to merchants without engineering translation

---

## 9) What Should Not Happen Next

Do not do these before Milestones 1–4 are complete:

- ship WooCommerce because the platform can support it
- treat Deep Resolver as the next product
- spin up Smart Brain work inside the Shopify stream
- run the Max Mode refactor as a standalone cleanup project disconnected from embedded surface delivery
- keep refining platform abstractions while the storefront product remains incomplete
- keep outdated `Free / Growth / Pro` docs as if they were current pricing truth

---

## 10) Practical Next Build Order

If work starts now, the correct near-term build order is:

1. AI search as the Free-tier wedge
2. contextual pill + Max Mode host convergence
3. product insight block + policy strip
4. review provider + richer source coverage
5. FAQ + comparison
6. tier entitlements + billing alignment
7. launch/App Store/design-partner hardening

This is the cleanest path that respects:

- the pricing strategy
- the embedded intelligence strategy
- the shipping roadmap
- the builder-mode reality that Shopify must become a strong product before portfolio sprawl
