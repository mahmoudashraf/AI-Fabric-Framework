# PLAN-008 — Vertical Product Factory Operating Model

Status: planning document (2026-04-27)

This document locks in the operating model that has emerged from building Loom Companion end-to-end on the platform: AI Fabric is a **vertical AI product company with a factory backbone**. It defines what "factory thinking" means in practice, which blocks are right, which are over-emphasized, which are missing, and the sequenced 5-phase roadmap that follows.

It complements PLAN-006 (pricing/licensing/positioning) and PLAN-007 (integration partner channel) by providing the long-running operating discipline.

---

## 1) Executive Summary

The factory thesis is **validated**. Loom Companion is a working end-to-end proof: platform + per-tenant deployment + plugin-based extensibility + Shopify bridge + partner portal + product on the App Store. Most "AI platform" companies have never closed this loop.

The operating discipline that follows from this validation:

- **Stay in builder + integrator mode** through ~$3M ARR. Ship vertical AI products yourself.
- **Open integration partner channel now** (PLAN-007). Do not open build-partner program.
- **Stop adding platform; start adding channel and product polish.**
- **Use the factory to ship vertical product #2** as the proof that the factory generalizes.
- **Defer build-partner program** until 3+ vertical products and a smoothed platform.

Recommended phasing:

1. Phase 1 (now → ~$1M ARR): direct + Loom GA
2. Phase 2 ($1–3M ARR): integration partner channel scaled
3. Phase 3 ($3–10M ARR): vertical product #2 ships on the factory
4. Phase 4 ($10M+ ARR): self-serve platform tier opens
5. Phase 5 (regulated verticals): enterprise + compliance pack, build partners eventually

---

## 2) Why This Plan Exists

The strategic mode has shifted from "I will be an AI integrator using my own platform" to "I am a builder who ships vertical AI products faster than anyone, with partners distributing them."

This shift is correct in spirit but can be misapplied. Common failure modes:

- premature opening of build-partner program before the factory is stable
- drifting into horizontal "AI control plane" positioning that competes with Bedrock and Vertex
- over-investing in platform features rather than channel + product polish
- losing customer-pain feedback by handing all customer relationships to partners

This plan defines the discipline that prevents these drifts.

---

## 3) Scope

In scope:

- the factory thesis: what is correct and what is fragile
- block assessment (built / built-but-wrong-emphasis / missing)
- the 5-phase sequenced roadmap
- vertical product #2 selection criteria
- operating discipline (build vs partner-led, factory leverage limits)

Out of scope:

- specific engineering work on platform features (covered in existing wave plans)
- pricing and licensing details (covered in PLAN-006)
- partner channel mechanics (covered in PLAN-007)
- vertical-specific product plans (will be written when product #2 is selected)

---

## 4) Factory Thesis — Validated, With Caveats

### 4.1 What is correct

- the plugin-based platform with strong conventions allows new capabilities to be added at a fraction of the cost competitors incur
- LLM-assisted authoring of new modes, actions, prompts, and connectors works *because the conventions exist*, not in spite of them
- Loom Companion is the proof: end-to-end loop closed, customers reachable, distribution validated
- the factory amortizes the boring 80% of vertical product development

### 4.2 Caveats to maintain humility

- the factory amortizes the boring 80%, **not the novel 20%**. Genuinely new capabilities (new modality, new compliance regime, new payment surface) still require real engineering
- Loom is one product on Shopify. The first non-Shopify vertical will reveal Shopify-shaped assumptions baked into "generic" abstractions. This always happens; it is not a defect, it is how factories get refined
- LLM-assisted authoring works for variations within an established capability space. It does not magically produce production-grade code outside the factory's existing patterns

### 4.3 The implication for hiring and roadmap

- favor hires who can extend the factory's conventions, not those who want to rebuild parts of it
- treat each new capability as either a "variation" (cheap, LLM-assisted) or a "novel" (engineered carefully); be honest about which
- new verticals reveal hidden Shopify-isms; budget engineering time for de-Shopify-fying when product #2 begins

---

## 5) Operating Mode Decisions

### 5.1 Builder + integrator mode through ~$3M ARR

Through Phases 1–2, the company:

- ships its own vertical products
- owns the customer relationship for direct sales
- uses partners as a distribution channel for finished products (PLAN-007)
- does not open a build-partner program

Rationale:

- the factory is real but not yet smoothed by anything other than its creator's own use
- customer-pain feedback flows directly only when you build and sell yourself
- partners follow proof, not pitches; you have one product as proof, not three

### 5.2 No build-partner program before 3 products + smoothed platform

A build-partner program (third-party ISVs building products on the factory) is deferred until:

- at least 3 vertical AI products have shipped on the factory (yours)
- the platform has been smoothed by integration-partner use (PLAN-007)
- the company has revenue to underwrite partner enablement (docs, certification, support, training, co-selling — real headcount)
- public APIs have settled enough that breaking changes are rare

Per the historical pattern: Salesforce, HubSpot, Shopify, Stripe all opened build-partner programs only after $200M–$500M ARR. We are nowhere near that scale, and there is no reason to defy the pattern.

### 5.3 Eat your own dog food forever

Even after build partners eventually exist, AI Fabric continues to ship vertical products itself. Shopify still operates merchants of record. Anthropic still ships Claude.ai. That direct surface keeps the factory honest.

---

## 6) Block Assessment

### 6.1 Right blocks already built — keep

| Block | Why it's right |
|---|---|
| Multi-tenant runtime (per-tenant deployments) | Required for B2B2B and partner channel; most competitors get this wrong |
| Plugin-based extensibility (4 types) | The factory mechanism; adding capabilities cheaply is the unfair advantage |
| Partner portal | Justified by integration-partner motion; rename to "Loom Operations Console for Agencies" |
| Shopify bridge service | Distribution channel = the moat at SMB scale |
| Loom Companion (end-to-end) | The wedge, the proof, the credibility for everything else |
| Actions + confirmation interceptors | Real differentiator; pays off in regulated verticals later |
| Open-core framework (Apache 2.0 core) | Credibility, hiring, ecosystem gravity; do not try to monetize directly |
| Governance/audit module (ADR-0003) | Banked optionality; worth real money in enterprise phase |
| Public API + RBAC for deployment lifecycle | Required for partner-installed accounts at scale |

### 6.2 Right blocks but wrong emphasis — adjust

| Block | Adjustment |
|---|---|
| Plugin marketplace as commercial channel | Built right, defer monetization. Free for v1 per PLAN-006 section 5.4 |
| Multi-cloud provisioning | Real but premature. One cloud + Railway is sufficient through $5M ARR |
| Phase 18+ execution plans | Plans aren't wrong; numbering signals waterfall and over-planning. Rename to milestone-named plans |
| "Pro Developer License" framework pricing | Wrong segment, wrong moment. Cut per PLAN-006 |
| MIT/Apache/proprietary license inconsistency | Confused. Fix per PLAN-006 |

### 6.3 Missing blocks — these are the next investments

These are the highest-leverage additions, most of which are channel/product polish, not platform engineering:

1. **Shared vector storage (Wave 4)** — gates SMB/B2B2B economics. Until this ships, partner channel margins are negative below ~50 merchants per deployment. **Highest-leverage remaining engineering.**
2. **Loom install flow under 30 minutes** — partners' time is money. Detailed in PLAN-007 section 6.
3. **Loom Operations Console for Agencies** — the partner portal pointed at a real user. Detailed in PLAN-007 section 5.
4. **Partner certification + playbook** — install checklist, ROI deck, troubleshooting guide. Detailed in PLAN-007 section 7.
5. **Tier-2 support runbook** — partner escalation discipline. Detailed in PLAN-007 section 10.
6. **Channel rules of engagement** — direct vs. partner conflict policy. Detailed in PLAN-007 section 9.
7. **Unified pricing page** — kill inconsistencies between Loom pricing doc, monetization doc, README. Detailed in PLAN-006 section 7.2.
8. **Vertical product #2 candidate list** — see section 8.

Notice: zero of items 2–8 are platform engineering. The discipline now is to **stop adding platform and start adding channel + polish**.

---

## 7) The 5-Phase Roadmap

### 7.1 Phase 1 — Direct + Loom GA (now → ~$1M ARR)

Goal: 100 paying Shopify merchants on Loom, direct sales.

Mode: integrator + builder.

Investments:

- shared vector storage acceleration (Wave 4 work)
- Loom install flow polish to under 30 minutes
- pricing/licensing/positioning consolidation (PLAN-006)
- Loom feature completeness for Elite tier
- App Store optimization and direct customer success

Deferred: partner channel scaling, vertical #2, multi-cloud, build partners.

Exit criteria: 100 paying merchants, $1M ARR run-rate, install flow target met, shared storage live.

### 7.2 Phase 2 — Integration Partner Channel Scaled ($1–3M ARR)

Goal: 5–10 hand-picked Shopify agencies actively installing Loom; 500+ partner-installed paying merchants.

Mode: builder + selective channel.

Investments:

- full execution of PLAN-007 (Waves A–E)
- Loom Operations Console v1
- partner certification program
- tier-2 support oncall in place
- one referenceable partner case study published

Deferred: vertical #2 begins toward end of phase, public Partner program, build-partner program.

Exit criteria: PLAN-007 acceptance criteria met (section 12 of that plan); $3M ARR run-rate; partner channel margin healthy.

### 7.3 Phase 3 — Vertical Product #2 ($3–10M ARR)

Goal: prove the factory works on a second vertical. Ship product #2 in a fraction of the time Loom took, on the factory.

Mode: builder.

Investments:

- vertical #2 candidate selection (section 8)
- ship vertical #2 to GA on the factory
- de-Shopify-fy any abstractions revealed during product #2 development
- second product on a non-Shopify distribution channel

Deferred: self-serve platform tier, build partners.

Exit criteria: vertical #2 has 50+ paying customers, factory leverage is measurable (e.g., product #2 reaches GA in <50% of the time and engineering cost of Loom).

### 7.4 Phase 4 — Self-Serve Platform Tier Opens ($10M+ ARR)

Goal: open Free/Team/Business platform tiers for self-serve mid-market ISVs. Begin selective build-partner pilot.

Mode: factory operator + builder.

Investments:

- developer documentation aimed at platform consumers (not framework consumers)
- self-serve onboarding flow for platform tiers
- plugin marketplace monetization (per PLAN-006 section 5.4 — open the 70/30 split)
- build-partner pilot with 2–3 hand-picked ISVs
- public API stability commitments (deprecation policy, semver discipline)

Deferred: full open build-partner program, enterprise compliance pack rollout.

Exit criteria: platform tier has paying customers; build-partner pilot delivers at least one third-party vertical product on the factory.

### 7.5 Phase 5 — Enterprise & Regulated Verticals (later)

Goal: enterprise sales motion in regulated verticals. Open build-partner program publicly.

Mode: factory operator + enterprise sales.

Investments:

- compliance pack (HIPAA, SOC 2, ISO 27001 certification)
- private runtime offering (customer-hosted control plane)
- enterprise sales team
- public build-partner program with certification, marketplace, and rev share
- multi-cloud (the deferred work from Phase 1) becomes relevant

Exit criteria: enterprise revenue exceeds product revenue; build partners ship more vertical products than AI Fabric does directly.

---

## 8) Vertical Product #2 Selection

### 8.1 Selection criteria

The second vertical product should:

- reuse the Shopify ecosystem **or** be different enough to genuinely test factory generality
- have a clear distribution channel (not "we'll figure out marketing")
- have an established competitor that is pre-LLM (so AI is a real differentiator)
- have a customer ARPU comparable to or higher than Loom Elite ($179/mo) to justify investment
- be one the founder has personal customer empathy for (avoid blind verticals)

### 8.2 Candidate types

Two categories worth considering:

**A. Shopify-adjacent verticals** (reuses distribution channel):

- B2B Shopify Plus assistant (wholesale ordering, account-tier pricing, large catalog navigation)
- Returns/RMA AI assistant for Shopify merchants
- Subscription management AI for Recharge merchants
- Wholesale supplier discovery and sourcing assistant

**B. Adjacent commerce platforms** (proves factory generality):

- WooCommerce equivalent of Loom
- BigCommerce equivalent
- A non-Shopify B2B commerce vertical (Salesforce Commerce Cloud, SAP Commerce)

**Recommendation**: pick from category A first. Lower distribution risk, higher factory leverage, clearer customer empathy. Reserve category B for Phase 4+ when factory generality is more important than speed.

### 8.3 Decision artifact

When Phase 3 begins, write a separate plan: `PLAN-009-VERTICAL_PRODUCT_TWO_SELECTION_AND_LAUNCH.md`. This plan does not pre-select; it locks the criteria.

---

## 9) Factory Leverage Discipline

The factory's leverage is real but conditional. Two failure modes to actively manage:

### 9.1 Variations vs novel work — be honest

For each new capability proposed:

- if it's a variation within an existing pattern (new mode, new prompt, new action of an existing type), use LLM-assisted authoring; estimate days
- if it's novel (new modality, new compliance regime, new integration paradigm), estimate weeks; engineer it carefully; expect the factory's conventions to need extension

The mistake is treating novel work as a variation and underestimating it. The factory amortizes the boring 80%, not the novel 20%.

### 9.2 De-Shopify-fication

When product #2 begins, expect to discover Shopify-isms in "generic" abstractions:

- action types that assume Shopify webhook semantics
- knowledge sources that assume catalog-shaped data
- confirmation patterns that assume e-commerce risk profiles
- UI shells that assume merchant-admin user models

Budget time during product #2 to extract these into clean abstractions. Do not push back the launch; do refactor as you go. Every Shopify-ism removed makes product #3 cheaper.

### 9.3 Customer signal preservation

Even with channel partners distributing products, AI Fabric maintains:

- direct customer support channels for at least 20% of accounts (founder office hours, direct email)
- monthly customer interview cadence with non-partner-installed merchants
- product analytics access independent of partner reporting

Losing direct customer signal is the silent killer of factory companies. The discipline must be enforced.

---

## 10) Acceptance Criteria

This plan is "in effect" when:

- the 5-phase roadmap is referenced in all subsequent product/engineering planning
- block assessment in section 6 is reflected in updated wave plans (premature blocks deprioritized, missing blocks added to backlog)
- vertical product #2 selection criteria (section 8) is captured for use when Phase 3 begins
- the operating discipline in section 9 is documented in CLAUDE.md or contributor docs

This plan does not "complete" — it is the operating model that runs continuously. Quarterly review should re-test whether the assumptions still hold (factory leverage, distribution channel reality, build-partner timing).

---

## 11) Dependencies

- PLAN-006 (pricing, licensing, positioning) provides the language and economics this plan references
- PLAN-007 (integration partner channel) is the channel motion called out in Phases 1–2
- existing wave plans (`PLATFORM_EXECUTION_SEQUENCE_WAVE4_PLAN.md` and `TENANT_SCOPED_SHARED_VECTOR_INFRASTRUCTURE_PLAN.md`) deliver the missing blocks identified in section 6.3 item 1

---

## 12) Risks

| Risk | Mitigation |
|---|---|
| Drift back to "horizontal AI control plane" positioning | Quarterly re-read of POSITIONING.md; explicit decision required to change |
| Premature opening of build-partner program | Section 5.2 gate criteria must all be met; require founder sign-off |
| Over-investment in platform vs channel | Engineering capacity allocation 60% product/channel, ≤40% platform during Phases 1–2 |
| Vertical #2 picked from gut, not criteria | Section 8.3 decision artifact is mandatory before Phase 3 begins |
| Factory leverage overestimated for novel work | Section 9.1 honest classification; estimates reviewed by second engineer |
| Customer signal lost to partner channel | Section 9.3 discipline enforced; quarterly review |

---

## 13) Estimated Effort

This plan is operating discipline, not a discrete project. The work is:

- ongoing review (quarterly, ~1 day each)
- selection of vertical #2 (when Phase 3 begins, separate plan)
- continuous reinforcement in roadmap reviews

Discrete deliverables under this plan are minimal; the value is in maintaining discipline and preventing drift.

---

## 14) Sequencing With Other Plans

- PLAN-006 and PLAN-007 are explicit dependencies
- existing wave plans should be re-reviewed against section 6.3 missing blocks; plans for premature blocks (multi-cloud, plugin monetization) should be re-tagged "deferred" with PLAN-008 reference
- a future PLAN-009 will cover vertical product #2 when Phase 3 is reached
- the build-partner program plan (currently nonexistent) is intentionally deferred; do not start drafting until Phase 4 entry conditions are met
