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
- Starter is the first serious paid product: full read-only store intelligence.
- Elite should stay gated until governed action surfaces, audit, confirmation, support behavior, and plan rollout are coherent.
- The platform is a credible product foundry, not yet a high-throughput product factory.
- Do not start WooCommerce, Docs, Comply, Slack, Smart Brain, or broad white-label work before Shopify Companion has real product and commercial signal.
- Partner support is a separate product surface. Long launch dossiers, App Review guides, support runbooks, design-partner packets, raw support bundles, webhook/vectorization diagnostics, and referral/commission views belong in a partner/operator dashboard, not merchant Shopify admin.
- Merchant Shopify admin should remain action-oriented and merchant-safe: setup, storefront surfaces, knowledge sync, billing, support handoff, usage/value, and clear blockers.
- Shopify indexing must use the platform vectorization capability. Shopify Bridge supplies Shopify-specific source data and orchestration, but vectorization lifecycle, runners, policies, queues, retries, and provider/vector-store internals belong to the platform/operator surface.

## Canonical Strategy Files

- `doc/Productization/future-work/MarketPlace/Products/Strategy/README.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PRODUCT_DIRECTION_DECISION_RECORD.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PARTNER_DASHBOARD_STRATEGY_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md`

## Latest Strategic Roadmap

Active sequence:

1. **Canonical Launch Truth**: align product, pricing, billing, gating, and launch copy.
2. **Storefront Product Shell**: make embedded intelligence visibly real; converge on Max Mode and retire legacy chat as a long-term shell.
3. **Starter Launch Package**: make full read-only store intelligence sellable and App Store-ready.
4. **Design-Partner Proof**: test with 5-10 real stores before scaling outreach or partner motion.
5. **Public Launch Push**: earn the first install/review loop; target 40-50 installs or clear rejection signal.
6. **Elite Activation**: launch governed actions only after Starter demand and action-intent signal exist.
7. **Partner Dashboard Gate**: move partner/operator packets, diagnostics, portfolio support, referrals, and commissions out of merchant admin.
8. **Second Product Gate**: consider WooCommerce only after Shopify signal and reliability gates are green.

Current P0 decisions still open:

- Decide whether Free is `AI search only` or `AI search + customer-safe order lookup`.
- Remove or clearly mark old `Growth / Pro` terminology in active launch and partner materials.
- Keep Elite claims bounded to verified live governed-action surfaces.
- Finish storefront shell convergence before opening new product tracks.
- Build partner dashboard only after the merchant/admin boundary is clean enough that partner/operator packets no longer need to render inside the Shopify merchant app.
- Merchant-facing indexing language is `Knowledge Sync`; raw index/reindex/replay/vectorization controls belong in the merged partner/operator dashboard.

## Strategic Decision Log

- 2026-04-24: Strategic direction is convergence over expansion: finish one strong Shopify Companion product before portfolio work.
- 2026-04-24: Product positioning should lead with embedded store intelligence; chatbot language should not lead App Store or GTM copy.
- 2026-04-24: Starter is the first commercial launch target; Elite remains gated by real governed actions and supportability.
- 2026-04-24: WooCommerce is the preferred second product only after Shopify install, review, paid-conversion, support, and reliability gates are green.
- 2026-04-24: Created `PARTNER_DASHBOARD_STRATEGY_PLAN.md`; partner dashboard owns multi-store management, launch/review/support packets, raw support exports, diagnostics, referrals, commissions, and partner playbooks.
- 2026-04-24: Decided Shopify indexing is platform-backed vectorization, not a separate Shopify indexing stack; merchant UI should expose only Knowledge Sync while partner/operator handles internals.
