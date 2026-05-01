# Shopify Companion Findings Roadmap

Status: execution roadmap from strategy review (2026-04-24)

This document turns the strategy review findings into one practical roadmap.

It should be read with:

- [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
- [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
- [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [../PRODUCT_DIRECTION_DECISION_RECORD.md](../PRODUCT_DIRECTION_DECISION_RECORD.md)
- [../LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](../LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)
- [../LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](../LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md)
- [../SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md](../SHOPIFY_AI_ENABLEMENT_EXPANSION_PLAN.md)
- [../PRODUCT_FACTORY_FACTORIZATION_CONSIDERATIONS.md](../PRODUCT_FACTORY_FACTORIZATION_CONSIDERATIONS.md)

---

## 1) Point Of View

The strategy direction is strong, but the next phase should be convergence, not more ideas.

The strongest product thesis is:

- **Loom Companion is not a chatbot.**
- It is **embedded store intelligence**.
- Chat is the depth layer.
- AI search, product insights, FAQ, comparison, policy strips, and contextual pills are the product identity.

The strongest execution thesis is:

- finish one strong Shopify product
- make tier truth coherent
- launch with honest claims
- build the partner enablement foundation early enough to support serious integrators
- learn from real stores and founding implementation partners
- only then expand to broad partner recruitment, Elite, WooCommerce, or the broader product factory

---

## 2) Non-Negotiable Decisions

### 2.1 Shopify remains first

Shopify Companion is the reference vertical.

Do not treat Shopify as the whole company identity, but do not split focus before Shopify produces real install, review, paid-conversion, and reliability signal.

### 2.2 One app, not multiple apps

Keep Loom Companion as one Shopify app with tiered capabilities.

Do not split read-only, support, sales, or search into separate Shopify apps.

### 2.3 Embedded intelligence first

The App Store story should lead with:

1. smart product pages
2. AI search
3. product FAQ
4. comparison
5. contextual policy
6. contextual pill
7. chat as the deeper layer

Do not lead with "AI chatbot for Shopify."

### 2.4 Read-first launch posture

Starter should be the first serious paid product.

Elite should not be marketed as a live action-taking tier until governed action surfaces, audit, confirmation, support behavior, and plan rollout are coherent.

### 2.5 Product factory later

The platform is a credible product foundry, not yet a high-throughput product factory.

The next priority is not more abstraction. The next priority is proving one product commercially.

### 2.6 Partner enablement is early, partner scaling is gated

Partner support should exist early because the platform may be marketed as an AI enablement layer for developers, integrators, and agencies.

That means early work should support:

- sandbox/demo access
- intelligence-piece catalog
- deployment templates
- verification packs
- support and escalation flow
- scoped multi-store access
- implementation playbooks

This is different from a passive acquisition program.

Do not scale partner recruitment, public signup, commercial attribution surfaces, or white-label packaging until founding implementation partners prove the flow.

---

## 3) Roadmap Summary

| Phase | Name | Main Goal | Exit Gate |
|---|---|---|---|
| 0 | Canonical Launch Truth | One product, pricing, and launch story | All docs, billing, gating, and copy agree |
| 1 | Storefront Product Shell | Embedded intelligence becomes the visible product | Max Mode is the only long-term shell path |
| 2 | Starter Launch Package | Make read-only paid product sellable | Starter can be sold honestly |
| 3 | Partner Enablement Foundation | Support founding integrators without exposing operator internals | One partner can deploy/support test stores with scoped access |
| 4 | Design-Partner Proof | Learn from real stores before scaling | 5-10 stores tested, feedback captured |
| 5 | Public Launch Push | First install/review loop | 40-50 installs or clear rejection signal |
| 6 | Elite Activation | Add governed actions only after proof | Action tier is real, audited, and supportable |
| 7 | Second Product Gate | Decide whether WooCommerce is justified | Shopify signal and reliability gate are green |

---

## 4) Phase 0: Canonical Launch Truth

Goal:

- freeze the product truth before more implementation or launch work compounds contradictions

Priority: P0

Recommended window: 1-3 days

### Must Decide

1. **Free tier scope**
   - Decision: Free is AI search only.
   - Order lookup is not Free.
   - Make billing, storefront gating, docs, partner catalog, and App Store copy match it.

2. **Tier naming**
   - Current truth is `Free / Starter / Elite`.
   - `Free / Growth / Pro` is historical and should not appear in current launch or partner copy.

3. **Launch claim**
   - Allowed: `AI shopping assistant`, `AI product intelligence`, `embedded store intelligence`.
   - Not allowed yet: `full AI enablement layer`, `autonomous commerce agent`, broad support automation claims.

4. **Elite promise boundary**
   - Say only what governed actions actually support.
   - Do not advertise Deep Resolver, returns, ticket creation, or discount actions unless they are verified live surfaces.

### Exit Gate

Phase 0 is complete when:

- pricing doc, builder roadmap, billing contract, App Store copy, and merchant UI describe the same product
- old `Growth / Pro` terms are either removed from active docs or explicitly marked historical
- Free tier scope is settled
- Elite claims are launch-safe

---

## 5) Phase 1: Storefront Product Shell

Goal:

- make embedded intelligence the actual shopper-facing product, not just a strategy phrase

Priority: P0

Recommended window: 1-2 focused implementation cycles

### Must Ship

- Max Mode host convergence for Shopify
- full shell conversation-mode contract:
  - `defaultConversationMode`
  - `effectiveConversationMode`
  - `allowedConversationModes`
- removal of the legacy chat UI as a long-term shopper surface
- explicit page-context versus attached-target contract
- Max widget attachment reuse from Companion-owned cards
- fetch-only bridge tools for shopper evidence
- removal of rule-based comparison, similarity, and policy keyword matching paths
- richer rendering for comparison, similar products, and size/fit guidance

### Product Rule

Every shopper-facing surface should follow the same model:

1. fetch grounded evidence
2. let the runtime reason
3. render clear shopper-facing output
4. hand off to chat only for depth

Do not keep adding per-surface heuristic intelligence inside the Shopify bridge.

### Exit Gate

Phase 1 is complete when:

- embedded surfaces are visible and merchant-placeable
- Max Mode is the single long-term shopper shell
- chat is a depth layer, not the only meaningful experience
- comparison and product guidance are grounded through the same read-first model
- no debug or operator language leaks into shopper surfaces

---

## 6) Phase 2: Starter Launch Package

Goal:

- make Starter the first serious commercial product

Priority: P0

Recommended window: 1 focused launch-readiness cycle after Phase 1

### Must Ship

- Starter entitlements active and verified
- all read-only surfaces aligned to Starter:
  - product insights
  - AI search
  - product FAQ
  - comparison
  - policy strip
  - contextual pill
  - read-only chat
- merchant-facing setup flow for placing surfaces
- freshness/indexing-health view
- basic analytics:
  - query volume
  - top questions
  - surface usage
  - unanswered/action-intent questions
- App Store screenshots using real embedded surfaces
- App Store copy led by "make every product page smarter"
- support runbook and design-partner checklist aligned to the shipped product

### Exit Gate

Phase 2 is complete when:

- Starter can be sold honestly as full read-only store intelligence
- a merchant can understand the product without engineering translation
- App Store assets match the real storefront surfaces
- support can answer common setup and sync issues without improvised debugging

---

## 7) Phase 3: Partner Enablement Foundation

Goal:

- make LoomAI supportable as an AI enablement layer for founding developers, integrators, and agencies

Priority: P0/P1 in parallel with launch preparation

Recommended window: begin once Phase 0 launch truth is stable; mature alongside Phases 1-4

### Partner Positioning

The partner offer is:

- use LoomAI to add intelligence surfaces to client stores and current apps without building the AI infrastructure from scratch

Founding partners should be able to deploy:

- AI search
- product insight blocks
- product FAQ
- comparison
- policy strips
- contextual pills
- chat/depth layer
- governed actions later

### Must Ship

- partner-facing demo/sandbox store
- intelligence-piece catalog with clear tier and data requirements
- implementation playbooks for common merchant types
- deployment checklist and verification pack
- scoped partner account and store assignment model
- support escalation path with owner, status, next action, and evidence
- short launch/support packet exports
- merchant-safe boundary so partners do not need operator internals

### Not Yet

Do not prioritize:

- broad public partner signup
- commercial attribution surfaces
- white-label packaging
- partner API
- certification program
- partner-led custom product assembly

### Exit Gate

Phase 3 is complete when:

- one founding implementation partner can deploy and support multiple test stores with scoped access
- partner docs are enough to complete normal setup without platform-operator help
- support escalation is repeatable and not chat-history dependent
- partner feedback has improved the install, setup, and verification flow

---

## 8) Phase 4: Design-Partner Proof

Goal:

- get real merchant signal before scaling public outreach, partner recruitment, or the portfolio

Priority: P0

Recommended window: 2-4 weeks

### Target Stores

Prioritize stores where embedded intelligence has obvious value:

- fashion/apparel with sizing and reviews
- electronics with comparison-heavy buying
- health/beauty with ingredient and use-case questions
- home goods with larger catalogs

Avoid early:

- dropshipping stores
- single-product stores
- stores already deeply committed to Gorgias/Tidio
- low-traffic stores where shopper signal will be too weak

### Must Capture

- install friction
- time to first working storefront surface
- which blocks merchants actually place
- shopper engagement by surface
- top shopper questions
- unanswered/action-intent questions
- merchant willingness to pay for Starter
- merchant confusion in setup or product explanation

### Exit Gate

Phase 4 is complete when:

- 5-10 design-partner stores have tested the product
- setup and support friction is documented
- at least one repeatable demo store or case-study candidate exists
- the launch story has been adjusted based on real merchant feedback

---

## 9) Phase 5: Public Launch Push

Goal:

- earn the first real install and review loop

Priority: P0 after design-partner proof

Recommended window: first 90 days after public launch

### Motion

- personalized direct outreach
- Shopify Community answers
- Reddit/build-in-public posts with screenshots and short demos
- founder follow-up with every install
- review asks after real usage
- weekly review of install funnel and support issues

### Targets

| Metric | Target |
|---|---|
| Month 1 installs | 5-10 |
| Month 2 installs | 15-25 total |
| Month 3 installs | 40-50 total |
| Month 3 reviews | 8-10 |
| Rating | 4.5+ |
| Free to Starter signal | Measured, not forced |

### Exit Gate

Phase 5 is complete when one of these is true:

- the product reaches 40-50 installs with positive review/usage signal
- or the launch exposes a clear product-positioning or setup problem that must be fixed before scaling

---

## 10) Phase 6: Elite Activation

Goal:

- expand from read-only intelligence to governed action-taking only after the read-first product proves demand

Priority: P1 until Starter has real signal

### Start Conditions

Do not start full Elite launch until:

- Starter is live and used by real merchants
- at least 30 active Starter or equivalent validated merchants exist
- merchant analytics show repeated action-intent questions
- action governance, audit, confirmation, and disable controls are verified

### Must Ship

- governed add-to-cart and cart update
- variant guidance as a real governed or clearly bounded surface
- customer-safe order lookup if included in Elite
- support handoff or support ticket behavior only if fully implemented
- merchant controls for action classes
- action audit history visible to merchants/operators
- Elite-specific App Store and in-app copy that does not overclaim

### Exit Gate

Phase 6 is complete when:

- Elite is more than a promise bundle
- merchants can see what actions happened and why
- risky action classes are allowlisted, audited, and revocable
- support can handle action failures without engineering intervention

---

## 11) Phase 7: Second Product Gate

Goal:

- decide whether WooCommerce or another second product is justified

Priority: hold until Shopify proof exists

### Start Conditions

Only consider WooCommerce after:

- Shopify Companion has real install and paid-conversion signal
- support load is understood
- launch and onboarding are repeatable
- the reliability gate is green
- the repeated product packaging work is clear enough to reuse

### Recommended Second Product

If the gate is green, WooCommerce Companion is the correct second product because:

- it reuses the same commerce-companion product shape
- it tests the bridge-service factory model
- it expands distribution without changing the core buyer problem

### Not Yet

Do not start these before Shopify proof:

- Loom Docs
- Loom Comply
- Loom Knowledge for Slack
- Smart Brain / Loom Insights
- partner-facing product factory beyond bounded implementation enablement
- broad white-label platform packaging

---

## 12) What Should Not Happen Next

Do not:

- ship WooCommerce only because the platform can support it
- treat Deep Resolver as the next product
- sell Elite before action surfaces are coherent
- keep polishing platform abstractions while launch packaging is unfinished
- recruit a scaled public partner program before founding implementation partners prove deployment flow
- treat partner enablement as passive acquisition
- keep historical pricing terms in active launch copy
- market the product as full Shopify AI enablement before actions, integrations, governance, and ROI reporting are actually live

---

## 13) Success Metrics

### Product Metrics

- storefront surface activation rate
- product insight impressions
- AI search usage
- FAQ interaction rate
- comparison usage
- chat handoff rate from embedded surfaces
- unanswered question rate
- freshness lag

### Business Metrics

- installs
- activated stores
- Free to Starter conversion
- Starter retention
- review count and rating
- merchant-reported setup friction
- merchant willingness to pay

### Operational Metrics

- install success rate
- time to first working surface
- sync success rate
- live verification pass rate
- support requests per active store
- mean time to recover degraded stores

### Partner Enablement Metrics

- founding implementation partners onboarded
- partner setup time per store
- stores managed per partner
- partner escalations per store
- partner setup completion without operator help
- verification-pack pass rate
- repeated questions that require docs/playbook updates

### Future Elite Metrics

- action-intent questions on Starter
- governed action attempts
- action completion rate
- action failure rate
- confirmation accept/reject rate
- support handoff success

---

## 14) Operating Rule

The roadmap should advance only when gates are green.

If a phase slips, do not compensate by opening more product tracks. Close the failed gate first.

The current advantage is not shipping the most ideas. The advantage is turning a serious platform into one product that merchants understand, trust, install, review, and pay for.

Partner enablement supports that advantage when it helps capable integrators deploy intelligence pieces repeatably. It becomes a distraction when it turns into broad channel management before the implementation flow is proven.
