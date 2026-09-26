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
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_18_SHOPIFY_COMPANION_PRODUCTION_RELEASE_AND_APP_LISTING_READINESS_PLAN.md`
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
- 2026-06-02: External consumer runtime assignment discovery must use scoped consumer assignment credentials, not Platform admin/operator keys. The assignment endpoint is control-plane discovery for a bound consumer only; it must not expose runtime secrets, must not be callable from browser code, and must not grant credentials/status access outside the explicitly scoped consumer assignment path.
- 2026-06-05: Shopify Companion controlled/private production release is technically viable for the proof store after production domains, Bridge request-shape repair, MCP-only action artifact pruning, commerce read-action answer generation, supported go-live release `rel-4286bee2` / `ver-d6dd23c3`, and live storefront smoke. This does not mean public App Store/self-service launch is ready; public launch remains gated by production billing test-mode removal, reviewer/listing package, protected-data/order-scope posture, and final support/onboarding evidence.
- 2026-06-09: AI Fabric Framework source ownership moved out of the private product repo into public sibling repo `Loom-AI-Labs/ai-fabric-framework`; private products should consume framework artifacts through Maven/local install and must not reintroduce copied framework source to avoid product-code exposure or boundary drift.
- 2026-06-10: Curated runtime policy is a deployment contract, not an optional prompt detail. Platform curated modules that expect thinker/support/commerce behavior must emit the matching `AI_CURATED_PACK`; otherwise production runtimes can silently fall back to bare `DEFAULT` policy, bypassing intended retrieval/read-action generation behavior even when vectors are present.
- 2026-06-10: Repo-boundary correction: deployable `ai-fabric-runtime` and `ai-infrastructure-generic-rest-connector` are private LoomAI product services and must stay in TheBaseRepo at the current deployment paths. The public `ai-fabric-framework` repo should contain reusable framework libraries/packages, not hosted runtime/connector product service code.
- 2026-06-11: Deployment import/restore must rewrite tenant-scoped knowledge-source and vectorization metadata to the target deployment's current handles. Runtime shared-index filters should remain strict; stale imported handle drift must be fixed in the control plane, not by loosening retrieval isolation.
- 2026-06-28: Private Platform/product services now consume the public AI Fabric Framework release `0.3.1` through the `io.github.loom-ai-labs:ai-fabric-bom` instead of copied framework source. Private deployable runtime/connector services remain product code; Docker/CI should pin released framework tags for reproducible product builds unless explicitly testing unreleased framework changes.
- 2026-07-30: Private Platform/runtime consumers move one-way to AI Fabric
  `0.5.1` while preserving the completed V04 lifecycle and current runtime
  capabilities. Do not add backward-compatibility paths.
  `ai-fabric-execution` belongs only in the private runtime for the bounded
  `deployment-knowledge-specialist@1`; it must not replace existing chat.
  Durable per-work indexing reconciliation uses the public
  `IndexingWorkQuery` contract. Aggregate queue diagnostics remain a bounded
  internal dependency until the framework exposes a public summary contract.
  Immutable `0.5.1` publication and Central-only consumer gates are green:
  private infrastructure `206/206`, private product `32/32`, and Platform
  backend `728/728`. Do not deploy its default-enabled specialist:
  trusted-retrieval fix `7055dda` must ship in a new immutable framework patch
  and pass the two-tenant canary first.
- 2026-07-30: Specialist retrieval authority is an end-to-end framework
  contract. Released `0.5.0` lost trusted tenant, deployment, and scope values
  between `TrustedExecutionContext` and RAG metadata; an unpatched two-tenant
  canary leaked cross-tenant evidence. Framework correction `7055dda` and the
  LoomAI defense-in-depth filters pass locally, but specialist deployment is
  blocked until the correction is released as immutable `0.5.2` or later and
  the hosted two-tenant canary is repeated. Released `0.5.1` does not include
  the fix and must not be retagged. Do not compensate with a product-side
  duplicate gateway, relaxed filter, or request-owned identity.
- 2026-07-31: AI Fabric `0.5.2` is the current one-way private consumer
  target. Immutable release `ada4580` contains trusted-retrieval fix
  `7055dda`; BOM, core, and execution artifacts are public on Maven Central.
  Central-only private builds passed `967/967` tests, all resolved framework
  dependencies are `0.5.2`, and the runtime archive contains
  `ai-fabric-execution-0.5.2.jar`. Hosted two-tenant/two-deployment isolation,
  missing-boundary failures, canonical reindex convergence, and the full
  Platform release gate have passed. Production Platform and ProdUS proof have
  also passed; `0.5.2` is approved for the active Platform deployment path.
- 2026-07-31: Coolify release completion is deployment-bound, not
  application-status-bound. A previously healthy container must never satisfy
  the wait for a newly queued deployment UUID. Likewise, `PRE_APPLY`
  verification is not eligible evidence for late post-deploy release recovery.
  Poll the new deployment to terminal success and require `POST_APPLY` or
  `MANUAL_RERUN` proof before activation.
- 2026-07-31: The deployment-knowledge specialist uses a generic `document`
  evidence space in managed profiles. Product-specific spaces remain valid,
  but specialist enablement must not depend on a customer's domain taxonomy.
- 2026-07-31: A vectorization run must snapshot the active deployment
  version's indexed-output hash when it is queued. Read-only overview
  computation is insufficient because completion reconciliation must compare
  against a persisted target. Successful runs may be marked `IN_SYNC` only
  when current, last-success, and revision hashes agree.
- 2026-07-31: Optional legacy Qdrant verification may remain non-blocking
  `MIGRATION_REQUIRED` for an inactive V03 rollout. It must not be presented as
  a failure of the active `0.5.2` release path, and it must not weaken the
  required blocking gates.
- 2026-07-31: Managed product service environment scope is derived from the
  authoritative Coolify target profile during reconciliation. Changing a
  target profile while retaining a stale service scope is configuration drift
  and must fail release verification until reconciled.
- 2026-07-31: On the current four-core staging host, canonical Maven image
  builds run serially for release repair. Parallel product builds can saturate
  CPU and fail the remote build command without a Maven compilation error;
  that capacity failure must not be diagnosed as source failure.
- 2026-07-31: Short-lived Partner JWTs are release proof material, not durable
  application credentials. Refresh only the dedicated active gate fixture,
  store it through private Platform secret handling, and prove the standalone
  Partner suite before launching the full gate.
- 2026-08-01: Productize the AI Fabric `0.5.2` foundation through one shared
  LoomAI Product Kernel and versioned, opinionated Product Profiles rather
  than parallel bespoke runtimes. Framework examples prove primitives, not
  product readiness, and bounded specialists must not be marketed as
  unrestricted autonomous multi-agent systems. The behavior-first product
  taxonomy was finalized in the 2026-08-03 correction below.
- 2026-08-01: `010.21` is the canonical consolidated AI enablement product
  architecture. Product Profile is a product-intent layer above existing
  curated modules, Marketplace packs, inference/vector/package profiles,
  deployment topology templates, target profiles, verification packs, and
  the immutable V04 version/release/assignment lifecycle; none of those
  existing sources of truth should be replaced by a parallel template system.
  Existing outbound MCP Gateway capability and future inbound Platform
  authoring MCP are separate services and trust boundaries. Claude remains an
  optional provider/client, not a product dependency or security authority.
- 2026-08-03: Canonical LoomAI products are behavior templates, not vertical
  tasks: Conversational Assistant, Agentic Specialist Team, and Smart Brain.
  Product Profiles select exactly one behavior and compose approved solution,
  capability, extension, channel, provider, topology, and verification
  bindings. Shopify, ProdUS, support, incident, churn, and commerce are
  solution packs/reference deployments; RAG, MCP, vectorization, documents,
  privacy, relationship/behavior analysis, and UI are capabilities/services/
  channels. Resolver and Human Review are extensions. AI Fabric 0.5.2 permits
  durable exact-specialist read jobs and process-local fixed plans, not a
  durable multi-step autonomous graph or event/scheduled writes.
- 2026-08-04: Smart Brain follows LoomAI's deployment-local data-plane rule.
  Platform publishes control-plane assignment and endpoint metadata but does
  not proxy all event or result traffic. Customer/application adapters call the
  assigned deployment directly; that deployment owns ingress, durable jobs,
  status/results, cancellation, output delivery, and delivery recovery.
  Promotion routes new work through a revised assignment while the old runtime
  drains accepted operations and callbacks before decommissioning.
  The unified V1 wire contract is CloudEvents `1.0` structured JSON over HTTPS;
  direct polling is mandatory and deployment-owned signed HTTPS webhooks are
  the optional push result path. Non-HTTP broker adapters are optional,
  deployment-local Marketplace `INTEGRATION` contributions introduced only
  with an allowlisted implementation/compiler contract; existing `DATA` and
  `ACTION` types must not be overloaded. Managed schedules use deployment-local
  Quartz/PostgreSQL and enter the same activation contract, not a central
  scheduler or alternate execution path.
- 2026-09-15: AI Fabric `0.6.1` is the active supported fleet baseline.
  Staging marketplace/ecommerce/Shopify and production
  ProdUS/marketplace/ecommerce/Shopify use verified V04 `0.6.1` releases;
  Platform control-plane services are deployed from final private source
  `d9edc5816`. ProdUS strict tenant/deployment retrieval is live on
  `dep-f6abfa06` after an authority-injected metadata projection and `198/198`
  reindex. Do not weaken those filters or trust source-owned boundaries.
- 2026-09-15: Release evidence keeps aggregate truth separate from an owner
  exception. Canonical Platform, marketplace, ecommerce, Partner, and Thinker
  checks are green in staging and production. Full aggregate runs still fail
  only the deferred Shopify first-product retrieval-quality stage, so they
  must not be relabeled green even though the `0.6.1` version rollout is
  accepted. Optional historical Qdrant remains outside the supported path.
- 2026-09-15: Coolify custom-domain configuration does not compensate for
  incorrect authoritative DNS. Namecheap must point the LoomAI apex and
  production hostnames at the production IPv4/IPv6 addresses before public
  custom-domain TLS is considered operational.
- 2026-09-15: `010.22` is the implementation companion to canonical `010.21`
  for productizing AI Fabric `0.6.1`. Delivery proceeds through one Product
  Kernel and three behavior products rather than one product per framework
  module. Capability maturity must progress through framework availability,
  private runtime packaging, Platform selection, hosted proof, and market
  readiness. Start with a generic Conversational Assistant profile, then a
  bounded Agentic Specialist Team. Smart Brain remains subject to the existing
  Shopify commercial-signal gate or an explicit superseding strategy decision.
- 2026-09-18: The prior Product Kernel/Product Profile/three-behavior-product
  model is superseded. LoomAI is a generic AI-enablement Platform; Platform
  users create and own the domain products. `CONVERSATIONAL`,
  `AGENTIC_SPECIALIST_TEAM`, and `SMART_BRAIN` are built-in deployment
  behavior types describing activation and coordination, not LoomAI-owned
  customer products. Reusable solution starts use the existing published
  Marketplace `TEMPLATE` lifecycle; capabilities continue through existing
  `DATA`, `ACTION`, and `INFERENCE_PROFILE` plugin versions. Existing curated
  modules, runtime/inference/vector profiles, Deployment Templates, Target
  Profiles, managed services, source artifacts, verification, V04 drafts and
  versions, releases, assignments, promotion, and export/import remain the
  implementation spine. The immutable V04 Deployment Version owns exact
  composition provenance. Do not add `behavior_product_template`,
  `product_profile`, parallel publish/version tables, or a second release
  engine. Entitlements constrain choices but are neither customer-product
  identity nor runtime authority. Canonical `010.21` and implementation plan
  `010.22` were corrected accordingly; this was documentation/planning only.
- 2026-09-19: AI Fabric `0.7.0` is the current private source target. The
  immutable framework release is tag `ai-fabric-framework-v0.7.0`, release
  commit `5b075b66384dc5b756b3b3dd12efaf896ce9a50b`, with LoomAI guidance at
  documentation commit `7ac32985`. The source upgrade keeps specialist chains
  disabled and adds no chain table, secret, resource, or route. The last
  verified hosted fleet remains `0.6.1` until an immutable private commit is
  deployed and Gate A hosted evidence passes; source and fleet claims must not
  be conflated.
- 2026-09-19: Declarative-chain adoption is four explicit gates: Gate A base
  `0.7.0` with chains disabled; Gate B deployment-local chain migration while
  disabled; Gate C one-worker mechanics canary using the existing
  `deployment-knowledge-specialist@1`; and Gate D a real multi-specialist
  product only after a genuinely distinct second read-only worker exists. Stop
  after Gate A for evidence review. A one-worker canary is not a marketable
  Agentic Specialist Team.
- 2026-09-19: LoomAI will use the official `ai.fabric/v1` `SpecialistChain`
  resource, offline validator, shared registry/gateway, and runtime hashes. It
  will not create a private chain DSL or second engine. Reusable specialist
  teams should enter the existing Marketplace/V04 lifecycle through one
  planned governed `SPECIALIST` contribution carrying non-executable exact
  specialist/chain/schema/prompt resources. `TEMPLATE` references exact
  plugin versions; V04 remains immutable deployment truth. Java chain code is
  reserved for genuine application invariants the declarative contract cannot
  express.
- 2026-09-19: AI Fabric `0.7.0` Gate A is complete and supersedes the earlier
  same-day hosted-fleet-pending statement. Private commit
  `2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264` is live across the supported
  staging and production runtime families through verified V04 releases;
  live readback reports `0.7.0` with specialist chains disabled. Canonical,
  Partner, Thinker, direct-specialist, isolation, and ProdUS grounded
  retrieval checks passed. The full aggregate suites remain non-green only at
  the owner-deferred Shopify first-product answer-quality stage and must still
  be reported as failed. Gates B, C, and D remain unstarted; declarative chains
  are framework-available, not yet a selectable or market-ready LoomAI product.
- 2026-09-19: Human Review is a LoomAI execution extension, not a deployment
  behavior or customer product. LoomAI Platform publishes the versioned
  extension through V04; customers opt in and retain reviewer identity,
  roles/scopes, separation of duty, domain authorization, and system-of-record
  authority. The first productized profile is a Conversational governed action
  proposal using AI Fabric's released `ACTION_PROPOSAL` review source. Agentic
  reuse waits for a hosted-proven real team. Smart Brain remains read-only and
  may feed only a separate application-owned read-result review contract; it
  cannot call Resolver automatically. Never represent arbitrary model or chain
  results as framework-backed action review.
- 2026-09-19: Full behavior-aware Platform source implementation now exists for
  `CONVERSATIONAL`, `AGENTIC_SPECIALIST_TEAM`, and `SMART_BRAIN`, reusing the
  existing Marketplace/V04 lifecycle. The owner explicitly superseded the
  source-work hold, not the hosted evidence gates. `SPECIALIST` bundles are
  reviewed, source-attested, non-executable resources; immutable V04 versions
  own composition provenance; runtimes must match their capability manifest.
  Agentic uses the official gateway with exactly two distinct read-only workers.
  Smart Brain owns deployment-local CloudEvents, durable operations, schedules,
  results, and delivery and remains unable to write. Human Review accepts only
  governed `ACTION_PROPOSAL` sources and preserves customer-owned reviewer and
  domain authority. Durable chain/job/review state lives in the PostgreSQL
  resource attached to each deployment and is migrated by the private runtime,
  never in the central Platform database. Local runtime `174/174`, Platform
  backend `757/757`, PostgreSQL `V1..V133`, and UI build checks pass. Do not call
  these behaviors hosted-proven or market-ready until immutable V04 staging
  deployments pass their complete behavior-specific gates.
- 2026-09-20: AI Fabric `0.7.1` is the active compatible production patch and
  fixes PostgreSQL nullable lease-owner transitions for durable specialist and
  Human Review state. Private behavior-aware source is committed, pushed, and
  deployed. Exact Conversational, two-worker Agentic, and Smart Brain hosted
  canaries pass; production canary runtimes are healthy, Agentic completed a
  durable two-worker chain, and Smart Brain completed a typed durable
  operation. This advances the exact compositions to `HOSTED_PROVEN`, not
  `MARKET_READY`. Reusable Marketplace-template origin, complete lifecycle and
  recovery matrices, and hosted Human Review remain required. Canonical
  Marketplace/Ecommerce verification passes without repair, while the full
  aggregate gate remains honestly non-green on the owner-deferred protected
  Shopify path. Never weaken that expectation or modify ProdUS/Shopify
  assignments merely to manufacture a green aggregate result.
- 2026-09-20: The production custom-domain cutover is complete and supersedes
  the earlier DNS-pending operational note. Authoritative Namecheap DNS and
  independent public resolvers return `46.225.162.106` for the apex and the
  `api`, `console`, `partners`, and `shopify-bridge` hostnames. The apex and
  public UIs return HTTP `200`, `api` returns the expected unauthenticated
  `401`, and the apex certificate is valid. A client that still fails only on
  one Wi-Fi network should be investigated as local DNS cache, filtering, or
  transport behavior rather than treated as an incomplete Platform cutover.
- 2026-09-20: `010.23` is the focused market-readiness execution plan for the
  three deployment behaviors. Readiness belongs to an exact immutable
  Marketplace template/V04 composition with complete clean-room, behavior,
  isolation, lifecycle, recovery, operations, commercial, and controlled-
  production evidence; it never belongs to a behavior label or canary by
  implication. Generic Conversational is the first GA candidate, followed by
  hosted Conversational Human Review, bounded Agentic, and deployment-local
  read-only Smart Brain. Reuse the current Marketplace/V04 lifecycle and do
  not modify ProdUS or Shopify assignments to create generic evidence.
- 2026-09-21: Platform core services must be built off-host as immutable GHCR
  images. Source builds on the four-core production control-plane host can
  starve every public service. The published image pair at source
  `154f6552d98fb6b32f2ed3874534da65dedc18df` is staging-proven with terminal
  Coolify deployments, health, configuration, UI, and placement-preflight
  evidence.
- 2026-09-21: Blue/green production backend startup is also beyond the current
  host's spare capacity. Even a second JVM isolated to CPU `3` caused two
  canonical `502` liveness checks and was automatically stopped; canonical
  services recovered. Never retry a duplicate backend on that host. Use an
  approved maintenance cutover or owner-approved extra/expanded capacity.
  Stopped production image canaries and exact copied configuration are
  preparation evidence, not a completed production release.
- 2026-09-21: Recovery was held through nine consecutive production liveness
  and readiness passes before temporary access was closed. Staging rollout
  tokens were revoked, their session and local files were retired, and both
  production firewalls again contain no operator-IP rule. A healthy recovered
  canonical app must not be confused with the still-pending image cutover.
- 2026-09-22: AI Fabric `0.8.4` is the sole supported framework baseline. The
  matching LoomAI runtime and Platform contract are current-only: no legacy
  selector aliases, parallel old/new runtime matrix, or backward-compatibility
  product promise. Historical releases and stopped apps are recovery evidence,
  not supported execution targets.
- 2026-09-22: The production Platform backend image cutover is complete and
  supersedes the pending statement above. Canonical app
  `sbdv2pkkqrbsy9m59034hb5j` runs exact private source `3e20ccfae` after a
  single-app maintenance cutover; staging runs the same source. Production
  source builds and duplicate backend starts remain prohibited on the current
  host.
- 2026-09-22: Shopify uses one current mode contract. Shells publish
  `thinker_deep`; the Platform boundary translates it to runtime selector
  `thinker`. The retired `shopify-companion` selector is not accepted.
  Deployment `dep-8c3e7259` is active on `v26`, AI Fabric `0.8.4`, verified
  release `rel-6763c7e2`.
- 2026-09-22: Full release run `vsr-e8c40e4a` passed every blocking stage and
  the Platform release gate is `READY`. Partner Max, Thinker/Resolver, Shopify
  Companion, Shopify MCP, first-product quality, Marketplace, and Ecommerce
  are green. The old optional Qdrant fixture remains non-blocking and is not a
  reason to preserve an older supported contract.
- 2026-09-22: File document indexing is a planned reusable Platform
  capability named **Document Knowledge Operations**, not a deployment
  behavior or new plugin type. AI Fabric `0.8.4` supplies trusted text/JSON
  preparation, plans, content-free manifests, canonical queue work,
  versioned identity, reconciliation, and exact deletion. LoomAI must supply
  customer-storage connectors, runtime persistence/APIs, Marketplace `DATA`
  and `TEMPLATE` composition, V04 compilation, operations UI, and verification.
  Customer-provided S3-compatible storage is production-primary; a read-only
  mounted folder is allowed only for small data/demos. LoomAI does not
  provision, back up, bill for, or delete customer source storage. Source bytes
  flow from that storage directly to the exact deployment; Platform remains
  the deterministic control plane. The canonical plan is
  `010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md`.
- 2026-09-25: Auto Trader support remains a planned deployment-local
  Marketplace composition, not a released Platform capability. Use one
  dealership, one deployment, and one server-owned advertiser scope. Auto
  Trader semantics belong in DATA/ACTION/TEMPLATE packages executed by the
  deployment-local Generic REST Connector; do not add an Auto Trader plugin
  type, standalone bridge, or central Platform data proxy. The official
  material confirms a credentialed sandbox and capability-specific validation,
  but no public self-service credential flow was identified, so partner
  onboarding, exact grants, a test advertiser, and sandbox credentials are an
  external P0 gate. LoomAI may separately prove a clearly labelled dealership
  demo with approved demo data, but that is not Auto Trader evidence. Required
  generic gaps and claim gates are canonicalized in
  `010_27_AUTOTRADER_INTEGRATION_PLATFORM_READINESS_CHANGE_AND_EVIDENCE_PLAN.md`;
  only exact sandbox-verified or production-approved compositions may carry the
  corresponding claim.
- 2026-09-26: Document Knowledge Operations is now source-complete and locally
  verified on AI Fabric `0.8.4`, while its hosted claim remains deliberately
  open. Reuse Marketplace DATA/TEMPLATE, V04, deployment-local runtime state,
  existing inference/vector profiles, target-scoped external bindings, and the
  deployment workspace. Production source storage is customer-owned
  S3-compatible storage; Platform stores references and lifecycle evidence but
  never relays bytes, provisions the bucket, or deletes source objects. The
  mounted-folder connector is only an internal canary/demo option because the
  current Coolify storage API does not attest a read-only mount. Exact scopes,
  active-version filtering, exact indexed deletion, internal evidence
  retention, export/import boundaries, capability readback, and the
  `document-knowledge-operations-v1` pack are implemented. Local runtime,
  Platform, PostgreSQL migration, UI, syntax, and hygiene gates pass. Keep the
  capability below `HOSTED_PROVEN` until external-storage staging and controlled
  production evidence plus the full release gate pass; do not alter ProdUS or
  Shopify to manufacture that evidence.
