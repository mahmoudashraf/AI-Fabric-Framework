# Shopify Companion Builder-Mode Shipping Roadmap

Status: canonical Shopify builder-mode roadmap (2026-04-24)

Validation basis: updated against the current implementation snapshot validated from PR `#154` over `de525c49c2f73d9bad7cf21585e48d3a4c1778c3..2e84e43d989f2175363557535550460efe0674b3`.

This document is the current source of truth for how Shopify Companion should be finished, productized, priced, and sequenced in builder mode.

It exists because the strategy set is now rich enough to be useful, but also rich enough to contradict itself unless we resolve the conflicts explicitly.

Read this with:

- [PRODUCT_DIRECTION_DECISION_RECORD.md](PRODUCT_DIRECTION_DECISION_RECORD.md)
- [SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md](SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md)
- [LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)
- [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)
- [SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md](SHOPIFY_AI_ENABLEMENT_EXECUTION_ROADMAP.md)
- [../Companion/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md](../Companion/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md](../Companion/SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md](../Companion/SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)
- [../Companion/SHOPIFY_COMPANION_APP_STORE_LISTING_PACKAGE.md](../Companion/SHOPIFY_COMPANION_APP_STORE_LISTING_PACKAGE.md)
- [Observability and Reliability Foundation Plan](../../../../../../doc/Operations/observability/OBSERVABILITY_AND_RELIABILITY_FOUNDATION_PLAN.md)

---

## 1) Executive Decision

The correct near-term Shopify roadmap is:

1. finish **Loom Companion** as a strong embedded-intelligence product
2. launch it with a real tier posture
3. expand into **Companion Elite** only after the read-first product is strong and governed actions are verified
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
| Read-first wrapper foundation | Shopify storefront surfaces already work well as prompt-first LLM wrappers, and the bridge already has bounded catalog/policy retrieval and control primitives. The remaining heuristic `compare_products` / `find_similar_products` paths are implementation debt, not the target product model. | Strong direction, but the bridge must converge to fetch-only tools so shopper reasoning is consistently LLM-led. |
| Verification | Live Shopify verification scripts and workflow entrypoints exist. Platform-hosted release verification now includes Shopify verification. | Shipping discipline is in place. |

### 2.2 What is still missing

| Strategy expectation | Current code reality | Gap |
|---|---|---|
| Multiple embedded intelligence surfaces | Theme app extension now ships the launcher app embed plus merchant-placeable `AI search`, `Contextual pill`, `Product insight`, `Policy strip`, `Product FAQ`, and `Comparison` app blocks. | Real progress toward the embedded-intelligence product shape, but still missing richer source coverage, stronger merchandising polish, and App Store packaging maturity. |
| Embedded intelligence product shape | Storefront no longer loads only a launcher shell. The app embed now layers embedded intelligence surfaces on top of the shell, and AI search can now operate as a direct bridge-backed surface even with chat fallback disabled. | Product identity is improving, but the delivery model still depends on a fixed app-embed host rather than a mature theme-surface system. |
| Max Mode storefront convergence | The Shopify embed loader already supports `legacy` and `max-mode`, and there is already a Shopify-specific Max Mode wrapper. | This is a real convergence track, but it is only partially complete until the legacy chat UI is removed and Max Mode is the only long-term storefront shell. |
| Real shell conversation modes | Shopify now persists `shellModeProfile`, exposes it in bootstrap/admin surfaces, and forwards it into bridge/runtime request context. | This is now a bounded mode-profile system, but it is still missing the fuller bootstrap/runtime contract for `defaultConversationMode`, `effectiveConversationMode`, and `allowedConversationModes`, plus explicit user-enabled advanced modes and page-aware mode routing. |
| Storefront context and Max attachments | Shopify already extracts safe page/product/collection context into wrapper/runtime requests, and the Max widget already supports attached items in widget state. | The remaining gap is an explicit `page context + attached target` contract, attach controls on Companion-owned cards, and a bounded optional instrumentation path for theme-native cards. |
| Richer Shopify data coverage | Code-backed coverage now exists for published blog/article content, bounded shopper-relevant product metafield enrichment, and opt-in metaobject ingestion in addition to catalog/content/policy sources. Judge.me-compatible review and rating metafields now flow into product sync, vectorization content, and read-first shopper actions when present. | Wave 1 data-expansion work is now materially stronger; the remaining gap is broader review-provider depth and richer merchandising polish, not total structured-content absence. |
| Read-first reasoning model | Many storefront surfaces already behave as prompt-first LLM wrappers over bridge/runtime query handling, but Shopify still keeps heuristic comparison/similar-product/policy interpretation paths. | Remove rule-based storefront intelligence, converge on fetch-only tools plus LLM reasoning, then polish rendering and size/fit depth on top of that single model. |
| Tiered commercial model | Bridge billing already models `Free / Starter / Elite`, allowed surfaces, chat fallback, product caps, and explicit Elite governance posture (`confirmation`, `audit`, `action packages`). Merchant and platform admin UIs now surface those details directly. Governed action grants, audit history, and shopper-safe cart action surfaces are now materially real in the bridge/theme-extension stack. | The remaining gap is fully aligned commercial rollout, live Starter/Elite rollout by default, and launch-safe packaging, not missing technical foundations. |
| Free-tier distribution wedge | Pricing strategy says Free is AI search only, and the launch implementation now enforces Free as AI search only. | Free billing, storefront bootstrap, theme surfaces, merchant UI, and App Store/support copy must keep order lookup out of Free and Starter. |
| Elite posture | Pricing strategy says Elite is read+write with governance. | Shopify now has bounded governed action capability for `add to cart` and `cart update` with explicit confirmation, signed grants, audit trail, and platform-admin investigation visibility. `Variant guidance` is still a guided chat continuation rather than a governed action, and support actions remain outside launch packaging until there is a matching governed support-action layer. The remaining gap is broader action/support depth and commercial rollout, not total absence of Elite execution support. |

### 2.3 What this means

Three conclusions are now clear:

1. **The storefront surface inventory has caught up materially, but host convergence and merchandising polish are still behind the platform/control-plane foundation.**
2. **Commercial contracts are ahead of final rollout discipline and roadmap/code alignment.**
3. **Launch packaging and GTM messaging are still behind the real surface set.**

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

Launch enforcement note:

- `Free` exposes AI search only
- customer-safe order lookup is kept out of Free and Starter and can only be claimed when Elite entitlement and support readiness are verified

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
2. **Loom Companion Elite posture**
3. **Loom AI Platform for Shopify**

Interpretation:

- `Loom Companion` is the read-first embedded-intelligence product
- `Companion Elite` is the action-capable commercial expansion of the same app
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
- fuller platform-backed shell conversation modes for Shopify, including explicit `defaultConversationMode`, `effectiveConversationMode`, and `allowedConversationModes`
- explicit `page context` versus `attached target` contract between Shopify wrapper, Max widget, and bridge chat
- intentional advanced-mode controls in the Max widget so users can opt into richer modes explicitly instead of only inheriting a store default
- page-aware mode mapping so admin can configure `page or surface -> preferred mode`, for example `landing -> navigator` and `account -> assistant or resolver`
- full removal of long-term dual-shell maintenance as a product dependency
- full removal of the legacy chat UI as a shopper-facing product surface

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
- reuse the existing Max widget attachment system instead of creating a second Shopify-only attachment path
- advanced modes in the Max widget must be intentional and legible, not hidden behind implicit shell behavior
- page-aware mode routing belongs to the storefront shell contract, not to ad hoc theme customization alone
- page context is automatic wrapper grounding; attached targets are explicit shopper-selected objects
- see [SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)
- see [SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md](../Companion/SHOPIFY_COMPANION_CONTEXT_AND_ATTACHMENT_PLAN.md)

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

- removal of rule-based storefront intelligence from the Shopify bridge
- fetch-only retrieval tools for product/policy/shopper evidence with LLM-led reasoning on top
- comparison and similar-product surfaces implemented through the same prompt-first query-wrapper path as the rest of Companion
- removal of the dedicated storefront read-action path and `bridgeReadActionUrl` dependency
- richer comparison rendering built on grounded fetched evidence
- richer similar-product rendering built on grounded fetched evidence
- richer size/fit guidance
- stronger text-first rendering, grounding cues, and follow-up affordances without forcing typed per-surface contracts in the first implementation wave
- smart attach controls on Companion-owned product/article/policy cards using the existing Max widget attachment model
- explicit theme-native card instrumentation path for attach controls instead of assuming card-level attach appears automatically everywhere

Architecture rule:

- shopper-facing reasoning belongs to the LLM/runtime path
- Shopify bridge tools stay fetch-only plus deterministic control
- UI-originated prompts remain valid and admin-flexible as long as backend policy stays authoritative
- text-first shopper rendering remains acceptable in the current phase
- heuristic comparison, similarity scoring, and policy keyword matching are legacy implementation debt to retire
- see [SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md](../Companion/SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md)

### 5.4 Commercialization

Missing:

- roadmap/code alignment for what `Free` actually includes
- live Starter/Elite commercial rollout active by default rather than only modeled in the billing contract
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
- first real design-partner feedback loop captured in practice
- support and launch playbooks fully consistent with the shipped product surface set
- explicit code/build regression gate paired with the stronger platform-owned live verification suite

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

Status:

- materially real, but not closed

Why this milestone comes first:

- pricing strategy makes Free the distribution engine
- Free still needs one coherent and launch-safe product truth even though the storefront wedge is now materially real

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
- AI search is now also a merchant-placeable Shopify app block
- customer-safe order lookup is not part of the Free or Starter launch package
- remaining Milestone 1 work is mostly around commercial clarity, tier-truth alignment, and App Store legibility

Exit criteria:

- a merchant can install the app and get real value from Free without inventing a pricing story
- Free can be described honestly in App Store copy
- roadmap tier truth, effective allowed surfaces, and billing copy all say the same thing about Free

---

### Milestone 2 — Embedded Intelligence Base

Goal:

- move the shopper experience away from widget-first and into embedded-intelligence-first

Status:

- materially real, but not closed

Must ship:

- contextual pill
- Max Mode convergence for Shopify host integration
- product insight block
- contextual policy strip
- bounded Shopify shell conversation-mode support based on platform-owned shell config
- page-context plus attached-target handoff contract for Max widget depth handoff
- intentional advanced modes in the Max widget that a shopper can explicitly enable when deeper behavior is desired
- page-aware storefront mode routing so admin can map `page or surface -> preferred mode`
- removal of the legacy chat UI as the long-term shopper shell

Required supporting work:

- storefront host contract cleanup
- lightweight block runtime or shared block host
- no operator/debug leakage in shopper surfaces
- bootstrap/chat plumbing for safe `defaultConversationMode` consumption in Shopify
- explicit wrapper reuse of the existing Max widget attachment system
- explicit mode affordances and copy so advanced modes are user-intentional rather than surprising
- admin controls for page-aware mode configuration with bounded allowed values per page or surface

Progress note:

- contextual pill, product insight, and policy strip are now materially real and merchant-placeable
- Shopify now persists `shellModeProfile` through admin/bootstrap surfaces
- storefront page context and Max attachment primitives are already real separately
- the main remaining Milestone 2 gap is full Max Mode host convergence, explicit page-context versus attached-target contract closure, legacy chat removal, intentional advanced-mode exposure, page-aware mode routing, and full conversation-mode contract closure, not missing embedded surface inventory

Milestone rule:

- treat the Max Mode refactor as a required enabling track inside Milestone 2
- do not treat it as a standalone milestone that can be declared complete while embedded surfaces are still absent
- all new Shopify storefront intelligence surfaces should build on the converged host contract rather than creating another Shopify-only shell path
- advanced modes are allowed only when runtime semantics, entitlements, and verification are real
- page-aware mode routing must stay bounded by platform-backed allowed modes rather than arbitrary theme-only strings
- product detail pages may carry automatic page context, but card-level attach behavior must be explicitly instrumented rather than assumed globally

Exit criteria:

- the product is visibly no longer “just a chatbot”
- at least two embedded intelligence surfaces are real and merchant-placeable
- Max Mode is the only long-term shopper shell and the legacy chat UI is removed
- advanced modes can be intentionally enabled in-widget and page-aware mode mapping is configurable in admin
- page context and attached targets are distinct, implemented, and reused consistently across wrapper handoff and Max attachment UX

---

### Milestone 3 — Starter Completion

Goal:

- make **Starter** real as the read-only workhorse tier

Status:

- materially real and close to target, but not closed

Must ship:

- product FAQ block
- broader read-first discovery depth
- smart attach controls on Companion-owned cards
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
- the main remaining Milestone 3 gap is broader live rollout repetition, higher-fidelity merchandising composition, richer read-first rendering polish, and attach controls on Companion-owned cards rather than missing source depth or tier legibility

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

Status:

- partially real

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
- the remaining Milestone 4 gap is actual asset capture, real design-partner repetition, and launch proof in practice rather than missing launch-packet machinery

Non-negotiable launch rule:

- all product claims in listing copy must match the surfaces that are actually shipped

Exit criteria:

- App Store submission package is honest, legible, and review-safe
- design-partner rollout can be repeated without operator improvisation

---

### Milestone 5 — Elite Foundation

Goal:

- make **Elite** commercially and technically credible

Status:

- partially real

Must ship first:

- real entitlements for Elite
- action governance posture exposed to merchants/admins
- safe action auditability
- tier-aware UI and packaging

Progress note:

- the live billing contract now exposes Elite confirmation posture, audit availability, and packaged action families to both merchant and platform-admin surfaces
- governed `add-to-cart` and `cart update` surfaces are now materially real with bridge-issued grants, signed completion, shopper confirmation, and audit history
- `variant guidance` currently exists as guided chat continuation rather than a governed action, and support-action packaging must stay out of launch claims until a matching governed support-action layer exists
- the remaining Milestone 5 gap is live commercial rollout repetition and merchant adoption evidence, not missing governance primitives or missing operator visibility

Elite should not launch as a promise bundle.

It must be backed by real action surfaces.

---

### Milestone 6 — Elite Actions and Guided Commerce

Goal:

- move from read-only intelligence into safe, bounded action-taking

Status:

- partially real

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

- the current Shopify implementation now has bounded governed commerce for `add-to-cart` and `cart update`
- `variant guidance` is still guided chat continuation rather than a governed action, and support actions remain future work until they are full governed support surfaces
- the remaining Milestone 6 gap is broader action/support depth and live commercial rollout rather than zero action capability

Deep Resolver belongs here only if it materially improves Elite outcomes. It is not a prerequisite for Milestone 5.

---

### Milestone 7 — Commerce and Support Enablement

Goal:

- move from shopping companion to broader Shopify AI enablement

Status:

- materially real, but still needs rollout repetition

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
- scope-grant recovery is now first-class through manifest-aligned `read_orders`, explicit scope-grant URLs, rollout gating, and go-live blocking when support readiness is not `READY`
- the remaining Milestone 7 gap is live shop reauthorization after scope deploy, broader support integration repetition, and a final decision on historical-order posture if `read_all_orders` matters

This milestone aligns with the existing AI enablement expansion plan.

---

### Milestone 8 — Second Product And Portfolio Expansion

Status:

- not open yet

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
| Elite | read + action with governance | verified cart actions, confirmation, audit, richer sync, advanced analytics; support actions only after a governed support layer exists |

Important rule:

- do not sell a tier before the tier’s core product truth is actually implemented

Current implementation drifts to keep out of launch claims:

- `Elite` currently has governed cart actions, but `variant guidance` and support actions are not yet a full governed-action surface

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
- repo-side code/build regression is green or intentionally waived alongside the platform-owned live suite
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

1. Free-tier truth alignment and paid rollout decision
2. Max Mode host convergence plus explicit shell conversation-mode contract
3. fetch-only tool conversion and rule-based storefront-intelligence removal
4. page-context plus attached-target contract and Max attachment reuse
5. intentional advanced modes in the Max widget plus page-aware admin mode routing
6. richer comparison, similar-product, size/fit, and Companion-card attach rendering
7. broader Elite action/support depth
8. App Store asset capture and final product story
9. design-partner loop and support/launch repetition
10. explicit code/build regression gate paired with the live release suite

This is the cleanest path that respects:

- the pricing strategy
- the embedded intelligence strategy
- the shipping roadmap
- the builder-mode reality that Shopify must become a strong product before portfolio sprawl

---

## 11) Remaining Tracking

Tracking snapshot as of `2026-04-24`.

| Track | Current state | What closes it | Priority | Milestone |
|---|---|---|---|---|
| Free-tier truth alignment | AI search is real, and launch implementation enforces Free as AI-search-only. | Keep billing copy, allowed surfaces, storefront gating, App Store copy, and partner/support language aligned to AI-search-only Free. | P0 | 1 |
| Max Mode host convergence | Shopify still carries `legacy` plus `max-mode` host behavior. | Converged host contract with no long-term dual-shell dependency. | P0 | 2 |
| Shell conversation-mode contract | `shellModeProfile` is real, but full `default/effective/allowed` mode semantics are not. | Land and verify the fuller bootstrap/runtime mode contract. | P0 | 2 |
| Fetch-only intelligence conversion | Shopify still carries heuristic `compare_products`, `find_similar_products`, policy keyword matching, and a dedicated storefront read-action path. | Remove rule-based storefront intelligence, converge on fetch-only bridge tools, and route comparison through the same LLM wrapper model as other surfaces. | P0 | 2 / 3 |
| Page context plus attached-target contract | Shopify page context is already real and Max attachments are already real, but the contract between them is not explicit. | Land one implementation-ready contract where page context is automatic wrapper grounding and attached targets are explicit Max attachments. | P0 | 2 |
| Intentional advanced modes in Max widget | Shopify mode handling exists, but users cannot yet intentionally enable richer modes from the Max widget in a bounded, legible way. | Advanced modes are explicit, entitlement-aware, verified, and user-enabled from the Max widget. | P0 | 2 |
| Legacy chat UI removal | Shopify still carries a long-term `legacy` chat shell path alongside Max Mode. | Legacy chat UI is removed and Max Mode is the only supported shopper shell. | P0 | 2 |
| Page-aware mode routing | Shopify has bounded shell mode plumbing, but admin cannot yet map `page or surface -> preferred mode`. | Admin can configure page-aware mode defaults such as `landing -> navigator` and `account -> assistant or resolver`, bounded by allowed modes. | P0 | 2 |
| Smart attach surface rollout | Max already supports attachments, but Companion-owned cards do not yet expose attach controls and theme-native cards are not instrumented. | Attach/Add-to-Max is live on Companion-owned cards first, and theme-native card instrumentation is explicitly bounded and optional. | P1 | 3 |
| Read-first merchandising polish | The surface set is real, but comparison/similar-product rendering is still thin after the fetch-only conversion. | Rich comparison, richer similar-product presentation, and size/fit guidance are real in shopper surfaces. | P1 | 3 |
| Paid rollout activation | Billing contracts are real, but Starter/Elite rollout is not yet fully live by default. | Live paid rollout is active, verified, and safe to advertise. | P0 | 4 / 5 |
| Elite action depth | Governed `add-to-cart` and `cart update` are real. | Variant guidance and support actions become real governed surfaces with audit and confirmation posture where required. | P1 | 5 / 6 |
| App Store asset capture | Launch/export packaging exists, but final screenshots and story are not closed. | Real App Store asset set and listing story match the shipped product exactly. | P0 | 4 |
| Design-partner loop | Design-partner packet export exists, but repeated live feedback loop proof is missing. | First real partner loop is completed, captured, and fed back into launch decisions. | P0 | 4 |
| Code/build regression gate | Platform-owned live suite is stronger than the current code/build gate posture. | Repo-side code/build regression is restored or explicitly made part of launch discipline next to the live suite. | P0 | 4 / 8 |
| Support scope reauth and historical-order posture | Recent-order lookup is real under `read_orders`, but reauth repetition and broader order posture are still open. | Live reauthorization is routine after scope changes, and historical-order needs are explicitly decided. | P1 | 7 |
