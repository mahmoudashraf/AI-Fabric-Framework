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
- Thinker/Resolver is the next product line after the first-product readiness gate: governed issue resolution with evidence, policy, confirmation, audited read/write actions, and escalation. It now follows the `006.x` sequence after `005` reached `DESIGN_PARTNER_READY`.
- Platform already supports the Thinker-side primitive: bounded LLM read-action resolution with eligible read actions, iterative `thinker` mode, evidence collection, optional RAG cooperation, and final generation. 006 should build on this instead of re-planning it from scratch.
- Coolify is a first-class deployment provider track beside Railway for tenant runtimes and restartable services. Platform/control-plane services stay on Railway.
- Hetzner Cloud is the selected first host provider for Coolify; use API/Terraform/`hcloud` automation for host, firewall, network, DNS, volume, and cloud-init setup. Dedicated Hetzner servers are deferred until runtime density justifies them.
- Partner support is an early platform capability for developers, integrators, and agencies using LoomAI as an AI enablement layer.
- Partner enablement means self-service signup, empty partner workspace by default, sandbox/demo access, intelligence-piece catalog, deployment templates, verification packs, merchant-approved scoped store access, support escalation, and implementation playbooks.
- Partners are implementation partners, not passive acquisition partners.
- Client-store access from signup alone, partner directories, certification, commercial attribution surfaces, white-label, partner APIs, and partner-led custom product assembly are out of current scope until implementation partners prove repeatable deployment and support.
- Long launch dossiers, App Review guides, support runbooks, design-partner packets, raw support bundles, webhook/vectorization diagnostics, and partner implementation evidence belong in partner/operator surfaces, not merchant Shopify admin.
- Merchant Shopify admin should remain action-oriented and merchant-safe: setup, storefront surfaces, knowledge sync, billing, support handoff, usage/value, and clear blockers.
- Shopify indexing must use the platform vectorization capability. Shopify Bridge supplies Shopify-specific source data and orchestration, but vectorization lifecycle, runners, policies, queues, retries, and provider/vector-store internals belong to the platform/operator surface.
- Runtime/Thinker owns final answer generation from RAG and read-action evidence. Shopify Bridge must not invent semantic fallback answers, suppress valid action evidence, or judge shopper-safe answer quality during development/optimization; it should pass through runtime output or expose diagnostics/errors.
- Read-action planning must remain LLM/tool-contract driven. Do not add product-domain text matching or hard-coded business heuristics in core/framework/action/connector modules; Shopify-specific behavior belongs in Shopify deployment prompts/config, and commerce-curated modules must stay generic enough for other commerce platforms.
- `relationship_query` is disabled for Shopify Companion until it can cooperate correctly with RAG/attachments and not bypass retrieval quality. Prefer bounded read actions such as product search/details/availability/policy plus RAG cooperation.
- Max widget shopper responses should render structured action evidence as UX components/cards where possible; raw JSON action envelopes are a diagnostic/debug concern, not the default shopper chat presentation.
- Git push is not enough to make Shopify theme-extension widget changes live. Shopify-hosted storefront asset changes require Shopify app/theme extension deploy/release, then storefront/browser proof.
- `008` is the active next strategic gate: controlled design-partner launch and market proof. Do not start WooCommerce, broad partner recruitment, white-label, or another platform expansion before `008` evidence exists.
- `009` is the queued Shopify MCP-first Marketplace tier/action-catalog alignment: use existing Marketplace `ACTION` plugins for runtime action visibility, make Shopify package profiles resolve tier-specific plugin bundles, and execute customer-facing Shopify actions through Shopify MCP. Bridge remains the governance/auth/session/audit/MCP-adapter boundary, not the owner of custom Shopify customer-action logic.

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
- `Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md`

## Latest Strategic Roadmap

Active sequence:

1. **Canonical Launch Truth**: launch truth created; Free is AI search only; align product, pricing, billing, gating, and launch copy.
2. **Storefront Product Shell**: make embedded intelligence visibly real; converge on Max Mode and retire legacy chat as a long-term shell.
3. **Starter Launch Package**: make full read-only store intelligence sellable and App Store-ready.
4. **First Product Readiness Audit**: run the final technical/product/commercial-readiness gate for Shopify Companion Starter before design-partner, partner-scale, or public-market activity.
5. **Thinker/Resolver `006.x` Product Line**: implement governed issue resolution in order: `006.1` read-only Thinker diagnosis, `006.2` Resolver dry-run, `006.3` governed low-risk writes, then `006.4` productized readiness and rollout.
6. **Coolify Deployment Provider `007`**: add Coolify as a provider type through target profiles, immutable image artifacts, provider handles, operator controls, audit, backup/restore, and release verification; keep Platform UI/backend/Postgres/partner UI/Shopify bridge on Railway.
7. **Partner Enablement Foundation**: support self-managed implementation partners with Supabase signup, empty workspace, sandbox, intelligence catalog, templates, verification packs, merchant-approved scoped access, and escalation.
8. **Controlled Design-Partner Launch `008`**: lock launch path, DNS/auth redirects, Shopify release, partner/merchant onboarding, real-store answer-quality audit, support/escalation evidence, and 5-10 store cohort proof.
9. **Shopify MCP-First Tier Action Catalog Alignment `009`**: align Shopify tiers/package profiles with MCP-backed Marketplace plugin bundles so Starter compiles Shopify Storefront/UCP MCP read actions and Elite compiles governed cart/customer-account MCP action plugins; Bridge governs execution but does not implement custom customer-facing Shopify action logic.
10. **Public Launch Push**: only after `008` returns `MARKET_READY`; earn the first install/review loop and target 40-50 installs or clear rejection signal.
11. **Elite Activation**: launch governed actions only after Starter demand, action-intent signal, and the Shopify control/execution boundary are coherent.
12. **Second Product Gate**: compare Thinker/Resolver governed resolution against WooCommerce as the next product move after Shopify signal and reliability gates are green.

Current P0 cleanup items:

- Remove or clearly mark old `Growth / Pro` terminology in active launch and partner materials.
- Remove order lookup from any active Free-tier copy, entitlement, partner catalog, or App Store claim.
- Keep Elite claims bounded to verified live governed-action surfaces.
- Finish storefront shell convergence before opening new product tracks.
- Build partner enablement early enough to support integrators, but keep broad partner scale, commercial attribution surfaces, white-label, and partner APIs out of current scope.
- Keep merchant/admin boundary clean so partner/operator packets do not render inside the Shopify merchant app.
- Merchant-facing indexing language is `Knowledge Sync`; raw index/reindex/replay/vectorization controls belong in the merged partner/operator dashboard.
- Run `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md` before treating Shopify Companion Starter as design-partner-ready or public-market-ready.
- Treat query-to-answer quality as part of first-product readiness, using Shopify Companion as the first concrete instance of a future platform-level Product Generation Audit Framework.
- Product readiness audit UI is a platform/operator console first, not Shopify merchant admin or partner-first UI; partners may later run scoped client-store audits after canonical thresholds exist.
- Treat `006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md` as the parent blueprint; implementation starts at `006.1` and must not skip to dry-run or writes.
- For 006, the new product risk is governed write-capable Resolver behavior; multi-read-action Thinker planning is an existing platform capability.
- Treat `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md` as the Coolify implementation source of truth. Coolify must be implemented as `ProviderType.COOLIFY` behind deployment target profiles, not as another global provisioning mode or one-off flag.
- Railway remains mandatory for Platform UI/backend/Postgres/partner UI/Shopify bridge, billing, webhooks, readiness audit, and provider administration.
- Start `007` with the Hetzner host automation baseline if reproducible Coolify infrastructure is missing; host provisioning is Hetzner API/Terraform/`hcloud`, while application lifecycle is Coolify API.
- The Hetzner Cloud token is in a private local document. Implementation sessions may load it into a local secret file/env var only; never print, commit, paste, or copy it into docs.
- Treat `008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md` as the active next roadmap after `007`: prove real-store onboarding, partner implementation, merchant setup, answer quality, support load, metrics, and launch decision before wider GTM.
- Treat `009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md` as the Plan 009 execution order. The source strategy drafts are `Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md` for Shopify capability mapping and `Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md` for generic MCP architecture: existing `ACTION` plugins remain the Marketplace-compatible action packaging layer, package profiles resolve plugin bundles, and Bridge remains final governance/permission/MCP-adapter authority while Shopify MCP owns customer-facing action implementation.

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
- 2026-04-25: Revised Partner Enablement Foundation to target a mature private partner operating layer, not only a document kit: partner identity/access, scoped store assignment, partner workspace, client portfolio/workspace, intelligence catalog, verification/evidence packs, support escalations, templates/playbooks, audit/security, and rollout gates; public partner signup, commissions, white-label, and public partner APIs remain out of current scope.
- 2026-04-25: Partner Enablement auth decision: use Supabase Auth for partner login with Google, Apple, and LinkedIn OIDC; keep Platform backend as the authorization source for partner roles, invitations, scoped store assignment, revocation, audit, and Shopify Bridge access; partner UI should live in a separate `Platfrom/partner-ui` project for `partners.loomai.pro`.
- 2026-04-25: Partner signup decision superseded: self-service partner signup is allowed and should create an empty workspace by default; client-store access requires merchant approval, signed approval link/code, approved install/claim flow, or operator assignment. Keep commissions, referral tracking, white-label, public partner API, directories, and certification out of current scope.
- 2026-04-25: Added First Product Readiness Audit gate after Starter Launch Package; Shopify Companion Starter is a technically ready candidate, but design-partner readiness and public-market readiness require a formal evidence audit before scaling outreach or partner activity.
- 2026-04-25: Query-to-answer quality belongs inside `005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md`; implement it with reusable platform audit primitives from day one so future products and partner-run client-store audits can reuse the query pack shape, scoring rubric, evidence output, forbidden-claim checks, and pass/fail semantics.
- 2026-04-25: Added Product Readiness Audit UI requirement to `005`: build an operator console for overview, checklist, query pack, answer results, evidence, and final decision; do not mark design-partner-ready from private scripts/chat history alone.
- 2026-04-25: Created `006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md` as the next product archetype candidate: Thinker diagnoses with evidence/read actions; Resolver executes approved registered write actions only through policy, confirmation, dry-run where possible, audit, recovery, and escalation. This is blueprint-only until `005` is complete.
- 2026-04-25: Corrected 006 foundation: read-action resolution is already implemented through `ReadActionResolutionService` with single-pass `resolver_assistant`, iterative `thinker`, eligible read-action allowlists, bounded action counts, RAG cooperation, diagnostics, and final generation from action evidence. Do not treat multi-read-action Thinker planning as missing.
- 2026-04-29: Promoted Thinker/Resolver into one `006.x` product line after `005` reached `DESIGN_PARTNER_READY`: `006` remains the parent blueprint; former `007` is now `006.1` Thinker read-only issue diagnosis; `006.2` is Resolver dry-run, `006.3` is governed low-risk writes, and `006.4` is productized readiness/rollout. Shopify Companion Elite is the first reference vertical, not a separate product.
- 2026-04-29: Created `007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md`; Coolify is a mature infrastructure provider track for tenant runtimes and restartable services, while Platform/control-plane surfaces remain on Railway. Implementation must start with target profiles/provider registry and keep Railway compatibility before adding Coolify API lifecycle calls.
- 2026-05-01: Updated `007` to select Hetzner Cloud as the first Coolify host provider. Hetzner automates host/network/DNS/firewall/volume/cloud-init setup; Coolify automates application lifecycle; Platform remains deployment source of truth. Do not start with dedicated Hetzner servers unless density economics justify the extra replacement complexity.
- 2026-05-01: Updated `007` with concrete Hetzner execution setup: staging `CPX32`, initial production `CCX23`, token loaded only from private/local secret handling, and Slice 0 execution checklist before Platform target profile/provider registry work.
- 2026-05-01: Thinker/RAG/action architecture tightened: Runtime/Thinker must generate final answers from read-action evidence and RAG; Bridge must not replace action evidence with canned semantic fallback answers; LLM action parameter extraction should drive read-action inputs, not text-matching workarounds in core/framework modules.
- 2026-05-01: Disabled Shopify Companion `relationship_query` from the shopper action path because it bypassed RAG/attachment quality and produced poor comparison behavior. Re-enable only after it is a proper RAG-cooperating read action with no domain coupling in generic framework modules.
- 2026-05-01: Max widget action results should render Shopify product/search action payloads as cards inside chat; default shopper chat should not expose raw `Data: { ... }` envelopes when structured product results are available.
- 2026-05-03: Created `008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md` as the active next gate after `007`: controlled 5-10 store design-partner proof, launch path/DNS/auth cleanup, Shopify release proof, partner/merchant onboarding, real-store answer-quality, support evidence, weekly metrics, and final `DESIGN_PARTNER_ACTIVE` / `MARKET_READY` / `ITERATE` / `NOT_READY` decision.
- 2026-05-03: Created then rewrote the original 009 Shopify capability draft around the cleaner Marketplace-compatible fix: tiers resolve package profiles, package profiles resolve required/disabled plugin bundles, Marketplace `ACTION` plugins compile runtime action catalogs, and Bridge code remains the Shopify API executor/final authority. Separate execution workers and GraphQL-in-config are deferred/non-goals. This Bridge-code-backed decision was superseded on 2026-05-04.
- 2026-05-04: Superseded the Bridge-code-backed Shopify customer-action execution decision. `009` is now MCP-first and greenfield: Shopify customer-facing actions should use Marketplace `ACTION` plugins with `adapterType = mcp-tool`; Bridge owns governance, auth/session binding, audit, rate limits, Customer Accounts OAuth/PKCE, and MCP transport, but should not implement custom product/cart/order/return Shopify action behavior when Shopify MCP exposes the capability.
