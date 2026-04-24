# Codex Strategic Context

Purpose: compact, append-only strategic context for this repo so future Codex turns can understand current product direction without reading long strategy threads.

Rules:
- Append only important strategic decisions, roadmap changes, product gates, and direction changes.
- Keep entries compact and clear. Do not paste full plans, long rationale, diffs, or chat transcripts.
- Prefer exact file paths for canonical docs and roadmap files.
- When a decision changes, append a new entry that supersedes the old one. Do not silently rewrite decision history.
- Treat this file as strategic memory, not implementation status. Use `CODEX_WORKING_CONTEXT.md` for session work, tests, blockers, and changed files.

## Current Strategic Direction

- Shopify Companion is the anchor product and first reference vertical.
- Loom Companion should be positioned as embedded store intelligence, not an AI chatbot.
- Chat is the depth layer; embedded surfaces are the product identity.
- Lead externally with smart product pages, AI search, product FAQ, comparison, contextual policy, contextual pill, and then chat.
- Use one Shopify app with tiered capabilities. Do not split search, support, sales, or read-only behavior into separate Shopify apps.
- Current tier truth is `Free / Starter / Elite`; older `Free / Growth / Pro` references are historical unless explicitly marked otherwise.
- Free tier is AI search only. Order lookup is not Free.
- Starter is the first serious paid product: full read-only store intelligence.
- Elite should stay gated until governed action surfaces, audit, confirmation, support behavior, and plan rollout are coherent.
- The platform is a credible product foundry, not yet a high-throughput product factory.
- Do not start WooCommerce, Docs, Comply, Slack, Smart Brain, or broad white-label work before Shopify Companion has real product and commercial signal.
- Partner support is an early platform capability for developers, integrators, and agencies using LoomAI as an AI enablement layer.
- Partner enablement means sandbox/demo access, intelligence-piece catalog, deployment templates, verification packs, scoped store access, support escalation, and implementation playbooks.
- Partners are implementation partners, not passive acquisition partners.
- Public partner signup, partner directories, certification, commercial attribution surfaces, white-label, partner APIs, and partner-led custom product assembly are out of current scope until founding implementation partners prove repeatable deployment and support.
- Long launch dossiers, App Review guides, support runbooks, design-partner packets, raw support bundles, webhook/vectorization diagnostics, and partner implementation evidence belong in partner/operator surfaces, not merchant Shopify admin.
- Merchant Shopify admin should remain action-oriented and merchant-safe: setup, storefront surfaces, knowledge sync, billing, support handoff, usage/value, and clear blockers.
- Shopify indexing must use the platform vectorization capability. Shopify Bridge supplies Shopify-specific source data and orchestration, but vectorization lifecycle, runners, policies, queues, retries, and provider/vector-store internals belong to the platform/operator surface.

## Canonical Strategy Files

- `doc/Productization/future-work/MarketPlace/Products/Strategy/README.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PRODUCT_DIRECTION_DECISION_RECORD.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PARTNER_DASHBOARD_STRATEGY_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PLATFORM_UI_PERSONA_SEPARATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PLATFORM_UI_REDESIGN_DIRECTION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/SHOPIFY_COMPANION_FINDINGS_ROADMAP.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/SHOPIFY_COMPANION_LAUNCH_TRUTH.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md`

## Latest Strategic Roadmap

Active sequence:

1. **Canonical Launch Truth**: launch truth created; Free is AI search only; align product, pricing, billing, gating, and launch copy.
2. **Storefront Product Shell**: make embedded intelligence visibly real; converge on Max Mode and retire legacy chat as a long-term shell.
3. **Starter Launch Package**: make full read-only store intelligence sellable and App Store-ready.
4. **Partner Enablement Foundation**: support founding implementation partners with sandbox, intelligence catalog, templates, verification packs, scoped access, and escalation.
5. **Design-Partner Proof**: test with 5-10 real stores before scaling public outreach or broad partner recruitment.
6. **Public Launch Push**: earn the first install/review loop; target 40-50 installs or clear rejection signal.
7. **Elite Activation**: launch governed actions only after Starter demand and action-intent signal exist.
8. **Second Product Gate**: consider WooCommerce only after Shopify signal and reliability gates are green.

Current P0 cleanup items:

- Remove or clearly mark old `Growth / Pro` terminology in active launch and partner materials.
- Remove order lookup from any active Free-tier copy, entitlement, partner catalog, or App Store claim.
- Keep Elite claims bounded to verified live governed-action surfaces.
- Finish storefront shell convergence before opening new product tracks.
- Build partner enablement early enough to support founding integrators, but keep public partner scale, commercial attribution surfaces, white-label, and partner APIs out of current scope.
- Keep merchant/admin boundary clean so partner/operator packets do not render inside the Shopify merchant app.
- Merchant-facing indexing language is `Knowledge Sync`; raw index/reindex/replay/vectorization controls belong in the merged partner/operator dashboard.

## Strategic Decision Log

- 2026-04-24: Strategic direction is convergence over expansion: finish one strong Shopify Companion product before portfolio work.
- 2026-04-24: Product positioning should lead with embedded store intelligence; chatbot language should not lead App Store or GTM copy.
- 2026-04-24: Starter is the first commercial launch target; Elite remains gated by real governed actions and supportability.
- 2026-04-24: WooCommerce is the preferred second product only after Shopify install, review, paid-conversion, support, and reliability gates are green.
- 2026-04-24: Created `PARTNER_DASHBOARD_STRATEGY_PLAN.md`; partner dashboard owns multi-store management, launch/review/support packets, raw support exports, diagnostics, referrals, commissions, and partner playbooks.
- 2026-04-24: Superseded simple partner-dashboard framing with Partner Enablement: support developers/integrators/agencies adding LoomAI intelligence pieces to client stores/apps; referrals and commissions are later modules, not the core first milestone.
- 2026-04-24: Updated `SHOPIFY_COMPANION_FINDINGS_ROADMAP.md` to add early Partner Enablement Foundation while keeping broad partner recruitment and white-label gated.
- 2026-04-24: Created `SHOPIFY_COMPANION_LAUNCH_TRUTH.md`; Free is AI search only, order lookup is not Free, tiers are `Free / Starter / Elite`, and launch story leads with embedded store intelligence.
- 2026-04-24: Decided Shopify indexing is platform-backed vectorization, not a separate Shopify indexing stack; merchant UI should expose only Knowledge Sync while partner/operator handles internals.
- 2026-04-24: Created detailed Phase 1 and Phase 2 roadmap docs. Phase 1 owns Storefront Product Shell convergence around Max Mode, embedded surfaces, fetch-only reasoning, and page context/attachments. Phase 2 owns Starter Launch Package readiness around read-only surfaces, entitlements, merchant activation, analytics, App Store assets, and support runbooks.
- 2026-04-25: Partner direction is implementation-partner-first, not passive acquisition. Updated UI and partner strategy docs to center client store setup, intelligence catalog, templates, verification packs, sandbox/demo access, support center, escalations, and scoped store access; commercial attribution surfaces are out of current scope.
