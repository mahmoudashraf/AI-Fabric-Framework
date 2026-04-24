# Roadmaps Backlog

Status: strategy idea backlog from `Products/Strategy` review (2026-04-24)

This document captures ideas found across the strategy folder and classifies what should be kept, included, deferred, rewritten, or retired.

Use this as the backlog feeding:

- [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
- [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
- [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
- [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [../PARTNER_DASHBOARD_STRATEGY_PLAN.md](../PARTNER_DASHBOARD_STRATEGY_PLAN.md)

Do not treat every item here as active scope. This file is an idea inventory with roadmap disposition.

---

## 1) Backlog Rules

- Keep canonical Shopify work focused on Loom Companion until Shopify has real install, review, paid-conversion, support, and reliability signal.
- Include ideas that strengthen Shopify Companion, partner enablement, launch readiness, or product truth.
- Defer ideas that require a second product, broad platform claim, white-label program, or new runtime until the relevant gates are green.
- Rewrite stale ideas instead of copying them forward when they use old pricing or chatbot-first language.
- Retire ideas that conflict with the current strategic direction.

---

## 2) Immediate Roadmap Inputs

These ideas should feed current Shopify Companion and Partner Enablement planning.

| Idea | Source Docs | Include Where | Disposition |
|---|---|---|---|
| Shopify Companion as anchor reference vertical | `README.md`, `PRODUCT_DIRECTION_DECISION_RECORD.md`, `SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md` | Findings roadmap, builder-mode roadmap | Keep |
| Read-first Shopping Companion posture | `PRODUCT_DIRECTION_DECISION_RECORD.md`, `SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md`, `LOOM_COMPANION_SHOPIFY_LAUNCH_PLAN.md` | Phase 0-2, Starter launch | Keep |
| Embedded intelligence product identity | `LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md`, `Next_strategy.md` | Storefront Product Shell, App Store story | Keep |
| Chat as depth layer, not product identity | `README.md`, `LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md`, `Next_strategy.md` | Phase 1, GTM copy | Keep |
| One Shopify app with tiered capabilities | `LOOM_COMPANION_SHOPIFY_LAUNCH_PLAN.md`, `PRODUCT_DIRECTION_DECISION_RECORD.md` | Product truth, pricing, launch | Keep |
| `Free / Starter / Elite` tier truth | `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md`, `README.md` | Phase 0, billing, App Store copy | Keep |
| Free tier scope alignment | `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md`, builder-mode roadmap, launch truth | Phase 0 P0 decision | Keep: Free is AI search only |
| Starter as first serious paid product | `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md`, findings roadmap | Phase 2 | Keep |
| Elite gated by governed actions | pricing strategy, launch plan, builder-mode roadmap | Phase 6 | Keep |
| Max Mode host convergence | builder-mode roadmap, Companion implementation docs referenced by it | Phase 1 | Include |
| Full shell conversation-mode contract | builder-mode roadmap | Phase 1 | Include |
| Page context versus attached target contract | builder-mode roadmap | Phase 1 | Include |
| Fetch-only bridge tools and LLM-led reasoning | builder-mode roadmap | Phase 1 | Include |
| Retire rule-based comparison/similarity/policy matching | builder-mode roadmap | Phase 1 | Include |
| Product insight block | embedded intelligence strategy, Next_strategy | Phase 1-2, Partner catalog | Keep |
| AI search | embedded intelligence strategy, pricing strategy, builder-mode roadmap | Free/Starter, Partner catalog | Keep |
| Product FAQ | embedded intelligence strategy | Starter, Partner catalog | Keep |
| Comparison surface | embedded intelligence strategy, builder-mode roadmap | Starter polish, Partner catalog | Keep |
| Policy strip | embedded intelligence strategy | Starter, Partner catalog | Keep |
| Contextual pill | embedded intelligence strategy, Next_strategy | Embedded identity, Partner catalog | Keep |
| Demo store | web infrastructure plan, GTM playbook, partner strategy | Launch infrastructure, partner sandbox | Include |
| App Store screenshots and short demos | GTM playbook, embedded intelligence strategy | Starter launch package | Include |
| Design-partner loop | builder-mode roadmap, findings roadmap | Phase 4 | Keep |
| First 40-50 install loop | GTM playbook, findings roadmap | Phase 5 | Keep |
| Partner Enablement Foundation | revised partner dashboard plan, latest strategic context | Phase 3 | Keep |
| Intelligence-piece catalog | partner dashboard plan | Partner Enablement | Include |
| Partner sandbox/demo center | partner dashboard plan, web infrastructure plan | Partner Enablement | Include |
| Verification packs | partner dashboard plan, product factory considerations | Partner Enablement, factory later | Include |
| Scoped partner access and store assignment | partner dashboard plan, persona separation plan | Partner Enablement | Include |
| Partner support/escalation notes | partner dashboard plan | Partner Enablement | Include |
| Merchant/admin/partner/operator persona separation | persona separation plan, partner dashboard plan | UI/product surfaces | Keep |
| Merchant admin language cleanup | persona separation plan | Merchant dashboard | Include |
| Launch and support packets moved out of merchant admin | partner dashboard plan | Partner Enablement, merchant boundary | Include |

---

## 3) Product And Storefront Backlog

### 3.1 Embedded Surfaces

Keep and include in the active roadmap:

- AI search as a real Free/Starter entry point.
- Product insight blocks with review, fit, use-case, concern, and source cues.
- Product FAQ blocks grounded in product data, reviews, policies, and content.
- Product comparison as a dedicated shopper surface, not only a chat message.
- Contextual policy strips near buying decisions.
- Contextual pill replacing the empty chat-bubble pattern.
- Chat/depth layer opened from surfaces with page context and attached targets.

Roadmap placement:

- Phase 1: shell convergence and surface architecture.
- Phase 2: Starter launch package.
- Phase 3: partner intelligence catalog.

### 3.2 Read-First Depth

Keep and include:

- Broader review-provider coverage.
- Blog/article ingestion.
- Metaobject and metafield ingestion.
- Richer comparison rendering.
- Similar-product rendering.
- Size/fit guidance where source data exists.
- Shopper-visible grounding cues.
- Smart attach controls on Companion-owned cards.

Roadmap placement:

- Phase 1 for architecture.
- Phase 2 for sellable Starter quality.
- Phase 6 only if tied to governed actions.

### 3.3 Storefront Interaction Ideas

Keep as future enrichment, not launch blockers:

- Store-wide returning-visitor intelligence dashboard.
- "What's new since your visit" shopper panel.
- Saved-item changes and review updates.
- Smart navigation components.

Disposition:

- Defer until the core embedded surfaces are launched and measured.

---

## 4) Pricing And Packaging Backlog

### 4.1 Keep

- Free as distribution wedge.
- Starter as full read-only store intelligence.
- Elite as read+write with governance.
- Read vs read+write as the clean tier line.
- Built-in Starter-to-Elite upsell through action-intent questions.
- Optional future Enterprise tier only after Shopify Plus or larger merchants request it.
- Optional future mid-tier only if data shows merchants want limited writes but reject Elite.

### 4.2 Decided P0 Scope

Free scope is settled:

- Free = AI search only.
- Order lookup is not Free.

Required alignment:

- billing contract
- storefront gating
- merchant UI
- App Store copy
- partner catalog
- support docs

### 4.3 Rewrite

Rewrite or mark historical:

- `Growth / Pro`
- `Pro at $79`
- old free conversation-count language
- any tier tables that do not match `Free / Starter / Elite`

---

## 5) Partner Enablement Backlog

### 5.1 Keep Immediately

Partner support should be an early platform capability because LoomAI may be marketed to developers, integrators, and agencies as an AI enablement layer.

Include:

- founding implementation partner flow
- partner sandbox/demo store
- intelligence-piece catalog
- deployment checklist
- per-surface verification checks
- support escalation template
- implementation playbooks
- scoped partner/store access
- partner-store portfolio
- client store workspace
- support and escalation center

### 5.2 Include After Foundation

- Multi-store health rollups.
- Templates by merchant vertical.
- Packet center for launch, App Review, support, lifecycle, and design-partner material.
- Deeper verification automation.
- Advanced support evidence exports.
- Client-app intelligence integration examples.

### 5.3 Defer

- Public partner signup.
- Partner API.
- Certification program.
- Partner directory.
- Broad white-label packaging.
- Partner-led custom product assembly.

Gate:

- only after founding partners repeatedly deploy and support stores without platform-operator intervention.

### 5.4 Rewrite

The partner program should not be framed as passive acquisition first.

Rewrite to:

- client intelligence enablement
- implementation partner workflow
- repeatable verification and support
- "powered by LoomAI" before white-label

---

## 6) GTM And Launch Backlog

### 6.1 Keep

- First installs come from direct effort, not organic.
- Target stores with strong use case:
  - fashion/apparel with sizing and reviews
  - electronics with comparison-heavy buying
  - health/beauty with ingredient questions
  - home/furniture or larger catalogs
- Avoid early:
  - dropshipping
  - single-product stores
  - very low-traffic stores
  - stores deeply committed to Gorgias/Tidio unless there is a clear wedge
- Outreach channels:
  - direct personalized email
  - Shopify Community
  - Reddit/build-in-public
  - Twitter/X demos
  - partner/founder network
- Follow-up sequence:
  - Day 1 install help
  - Day 3 usage check
  - Day 14 review ask if experience is positive

### 6.2 Include In Roadmaps

- App Store asset capture.
- Product-page screenshots.
- AI search screenshots.
- Comparison screenshots.
- Policy strip/contextual pill screenshots.
- 30-second demo clips.
- Demo store.
- Case-study candidate from design partners.

### 6.3 Rewrite

Copy should lead with:

- "Make every product page smarter."
- "AI product intelligence for Shopify."
- "Embedded store intelligence."

Do not lead with:

- "AI chatbot."
- "Sales agent."
- broad autonomous-commerce language.

---

## 7) Web, Docs, And Infrastructure Backlog

### 7.1 Include Before Or Near Launch

From `LOOMAI_PRO_SUBDOMAIN_AND_WEB_INFRASTRUCTURE_PLAN.md`:

- `api.loomai.pro`: stable backend/OAuth/webhook/runtime URL.
- `app.loomai.pro`: merchant dashboard or current app surface.
- `cdn.loomai.pro`: storefront widget/assets delivery.
- `loomai.pro`: company/product homepage.
- `docs.loomai.pro`: merchant, partner, and developer docs.
- `demo.loomai.pro`: live Shopify demo store.
- `status.loomai.pro`: public status page.

### 7.2 Defer

- `blog.loomai.pro` until there is real content, cases, and product updates worth publishing.
- Full partner portal polish until founding partner flow is validated.

### 7.3 Include In Partner Enablement

- partner docs
- demo/sandbox store
- partner resources
- setup playbooks
- support escalation route

---

## 8) Platform UI And Persona Backlog

### 8.1 Keep

- Separate surfaces:
  - merchant dashboard
  - partner enablement dashboard
  - operator control plane
- Merchant UI should avoid:
  - deployment
  - vectorization
  - provider
  - runtime
  - raw logs
  - raw support bundles
- Partner UI should expose:
  - store setup
  - surface placement
  - templates
  - health
  - verification
  - support evidence
  - bounded sync/retry controls
- Operator UI should expose:
  - providers
  - deployments
  - vectorization
  - diagnostics
  - security
  - actions
  - platform health

### 8.2 Include

- Grouped navigation.
- Progressive disclosure.
- Status-first dashboards.
- Clear top-level context selector.
- Merchant-safe language.
- Partner implementation language.
- Operator technical language.

### 8.3 Design Ideas To Keep

- LoomAI Labs brand foundation.
- "Focused builder / clean workshop / precise tools."
- Dark-first operator/control-plane style.
- Clear status colors.
- Inter and JetBrains Mono.
- Minimal micro-interactions.
- Retheme first, then refactor heavy pages.

### 8.4 Rewrite

Partner UI in `PLATFORM_UI_PERSONA_SEPARATION_PLAN.md` is now implementation-partner-first.

Keep partner UI centered around:

- intelligence catalog
- client store portfolio
- implementation workflows
- verification packs
- support/escalation
- templates/playbooks
- revenue later

---

## 9) Shopify AI Enablement Backlog

### 9.1 Keep As Future Ladder

Use the market-claim progression:

1. AI shopping assistant
2. AI knowledge enablement
3. AI commerce enablement
4. AI enablement layer

### 9.2 Include After Starter

- live freshness and indexing controls
- review-provider depth
- search augmentation
- metaobjects/metafields
- blogs/articles
- surface usage analytics
- merchant-visible source readiness

### 9.3 Include After Governed Actions Are Stable

- add-to-cart
- cart update
- variant guidance
- promotion awareness
- support handoff
- order lookup
- returns handoff
- support integration
- lifecycle integration
- subscription integration

### 9.4 Defer Until Enough Activation Exists

- conversion influence reporting
- revenue attribution
- support deflection claims
- AI enablement layer positioning
- reusable Shopify integration framework
- cross-surface orchestration

---

## 10) Product Factory And Portfolio Backlog

### 10.1 Keep

The platform can produce multiple product categories:

- Commerce Companion for WooCommerce
- Commerce Companion for BigCommerce/Wix/Squarespace/PrestaShop/Ecwid
- Loom Docs
- Loom Comply
- Loom Knowledge
- Loom Forms / Intake
- Loom Property
- Loom Insights / Smart Brain

### 10.2 Current Gate

Do not start portfolio expansion until Shopify has:

- real install signal
- review signal
- paid-conversion signal
- support load understood
- reliability gate green
- repeatable launch/onboarding
- partner enablement foundation validated

### 10.3 Preferred Future Sequence

If gates are green:

1. WooCommerce Companion
2. Loom Docs
3. Loom Comply
4. Loom Knowledge
5. Loom Forms / Intake
6. Loom Property
7. Loom Insights / Smart Brain

### 10.4 Keep As Product-Factorization Targets

- product bundle definitions
- app-shell packaging
- product verification packs
- product analytics/attribution packaging
- launch artifacts
- demo environment shape

### 10.5 Defer

- high-throughput product factory claim
- external product builders
- partner-led product assembly
- broad marketplace product publishing

---

## 11) Reliability And Operations Backlog

### 11.1 Keep

From the portfolio roadmap:

- reliability gate before product #3
- two products on shared observability for at least two weeks
- MTTD target
- MTTA target
- alert runbooks
- incident retrospectives
- cardinality budgets
- support escalation playbook

### 11.2 Include Now

- live verification discipline
- Shopify verification green before launch claims
- support runbook used in real support situations
- status page
- launch blockers surfaced clearly
- design-partner support evidence captured

### 11.3 Defer

- aggressive multi-product observability extraction until at least a second product is real or underway.

---

## 12) Technical Enablement Backlog

### 12.1 Relationship Query Orchestrator

Source: `RELATIONSHIP_QUERY_ORCHESTRATOR_INTEGRATION.md`

Idea:

- relationship-query module already has hybrid vector + relational search
- add orchestrator integration through existing `ActionHandler`
- support direct and orchestrated patterns
- use orchestrator for entity-type extraction, PII/access control, and behavior insights

Disposition:

- Keep as platform technical backlog.
- Do not make it a Shopify launch blocker.
- Include later if relationship-aware product intelligence, internal knowledge, compliance, or partner integrations need relational reasoning.

Potential roadmap placement:

- Platform capability backlog.
- Future Partner Developer / AI enablement layer.
- Future Loom Knowledge / Comply / internal systems.

---

## 13) Ideas To Rewrite Or Retire

### Rewrite

- `Growth / Pro` references -> `Starter / Elite` or explicitly historical.
- Chatbot-first App Store copy -> embedded intelligence copy.
- Partner program as passive acquisition -> implementation partner enablement.
- Partner dashboard acquisition-first design -> intelligence catalog and implementation support first.
- Portfolio timing -> gate-based sequence.
- UI docs that merge partner, merchant, and operator concerns -> persona-separated surfaces.

### Retire As Active Direction

- separate Shopify apps for read/support/sales layers
- Deep Resolver as Shopify V1 blocker
- Smart Brain inside Shopify Companion stream
- full AI enablement layer claim before governance/integrations/ROI are real
- white-label before brand/support ownership is proven
- broad partner program before founding implementation partners validate flow
- second-product build only because the platform can support it

---

## 14) Roadmap Integration Checklist

When updating active roadmaps, check whether the change supports one of these lanes:

1. Canonical launch truth
2. Storefront product shell
3. Starter launch package
4. Partner enablement foundation
5. Design-partner proof
6. Public launch push
7. Elite governed-action activation
8. Shopify AI enablement expansion
9. Second-product gate
10. Product factory factorization

If it does not fit one of these lanes, keep it in backlog until the strategic lane is clear.
