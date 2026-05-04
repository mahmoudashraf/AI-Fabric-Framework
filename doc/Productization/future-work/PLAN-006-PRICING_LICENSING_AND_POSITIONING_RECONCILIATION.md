# PLAN-006 — Pricing, Licensing, and Positioning Reconciliation

Status: planning document (2026-04-27)

This document consolidates the pricing model, licensing posture, and positioning narrative across the four monetizable surfaces (products, platform, deployments, plugins) and resolves the inconsistencies currently present in `LICENSE`, `README.md`, and the existing strategy docs.

It is preparatory work that unlocks PLAN-007 (integration partner channel) and PLAN-008 (vertical product factory operating model). Without it, partner conversations, public pricing, and license disclosures are inconsistent across documents.

---

## 1) Executive Summary

Lock in three decisions and propagate them everywhere:

1. **Licensing**: Apache 2.0 for the framework; BSL 1.1 (with 3-year Apache 2.0 conversion) for the platform; commercial EULA for hosted SaaS; Apache 2.0 / CC-BY for plugin manifests.
2. **Pricing**: surface-specific pricing models, with the platform sold on **deployments + tenants** as the unit, not seats; products sold per end-customer subscription; deployments bundled into platform tiers with metered overage; plugins free for v1.
3. **Positioning**: vertical AI products with an operationally serious backbone; Shopify is the first proof; the platform is the engine, not the car.

Output of this plan:

- one consolidated public pricing page
- one reconciled `LICENSE` and `README.md`
- one canonical positioning document referenced by all marketing and sales surfaces
- removal of conflicting language from existing docs (Pro Developer License, MIT badge, "proprietary placeholder")

---

## 2) Why This Plan Exists

Current state has measurable conflicts:

- `LICENSE` declares dual licensing (Apache 2.0 community + proprietary enterprise)
- `README.md` shows an MIT badge and offers a "Pro Developer License" with a 50% lifetime discount
- `FRAMEWORK_RELEASE_STRATEGY_AND_OPEN_CORE_PLAN.md` describes platform licensing as a "proprietary placeholder" without naming a license
- `MULTI_TENANT_RUNTIME_STRATEGY_AND_MARKET_OPPORTUNITY.md` prices platform at £100–£200/tenant/mo or £15K/mo flat
- `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md` defines $0 / $29 / $179 product tiers
- An `ai-infrastructure-module/MONETIZATION_STRATEGY.md` lists five framework tiers including a "Pro Developer" tier

These cannot all be true at once. Partners, prospects, and contributors need one consistent answer.

---

## 3) Scope

In scope:

- license selection and reconciliation across the public repo
- per-surface pricing model (products, platform, deployments, plugins)
- positioning lock-in (one-line, audience priority, anti-positioning)
- doc cleanup and removal of conflicting language

Out of scope:

- contract templates, EULAs, or partner agreement drafting (legal work, separate)
- payment infrastructure, billing system implementation (engineering, later)
- per-tenant entitlement enforcement runtime (covered in PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md)

---

## 4) Licensing Decisions

### 4.1 Framework — Apache 2.0

The framework (`ai-infrastructure-module`, `ai-fabric-product`, public connectors, public action interfaces) ships under **Apache 2.0**.

Rationale:

- maximum adoption, no friction for enterprise consumers
- partner-friendly, hiring magnet
- the framework is not the revenue surface; restricting it costs more than it earns
- aligns with industry expectations for an "AI core"

### 4.2 Platform — BSL 1.1 → Apache 2.0 (3-year conversion)

The platform (control plane, admin UI, deployment lifecycle, governance module, marketplace runtime, partner portal) ships under **Business Source License 1.1**, converting to Apache 2.0 after 3 years.

Rationale:

- protects against hyperscaler-style "host your platform as a competing managed service" commercial substitution
- allows customer self-hosting and source inspection
- well-precedented (Sentry, MariaDB, CockroachDB, HashiCorp pre-2023, Couchbase)
- avoids AGPL adoption friction

The "Additional Use Grant" must explicitly allow:

- internal use by any organization
- self-hosting for own customers
- consulting and implementation services
- forbid: offering AI Fabric Platform as a commercial managed service

### 4.3 Hosted SaaS — Commercial EULA

The hosted control plane and managed runtimes operate under a separate commercial EULA covering:

- service-level commitments
- data processing terms (GDPR/CCPA)
- entitlement enforcement
- audit and compliance certifications

### 4.4 Plugins — Apache 2.0 or CC-BY for manifests

Plugin manifests, declarative configs, and shell contributions ship under Apache 2.0 (code) or CC-BY (content). Plugins remain free to author and free to install in v1. Future monetization is covered in section 5.4.

---

## 5) Pricing Decisions

### 5.1 Platform Tiers

Unit of pricing: **deployments + tenants under deployments**. Seats are noise; deployments are what cost to operate and what customers can count.

| Tier | Price | Deployments | Storage | Audit | Support | Brand |
|---|---|---|---|---|---|---|
| Free / Developer | $0 | 1 | shared | 7d | community | AI Fabric branding required |
| Team | ~$499/mo | 5 | shared | 30d | email, business hours | optional branding |
| Business | ~$2,500/mo | 25 | dedicated optional | 90d | email + chat, SLA | full white-label |
| Enterprise | custom (£15K+/mo or £100–£200/tenant for B2B2B) | unlimited | dedicated, multi-region | 1y+ | CSM, 24/7 | white-label + audit pack |

Enterprise tier matches the existing math in `MULTI_TENANT_RUNTIME_STRATEGY_AND_MARKET_OPPORTUNITY.md`. Public pricing exposes Free, Team, Business; Enterprise is "Contact us" only.

### 5.2 Products (Loom Companion)

Public pricing on Shopify App Store:

| Tier | Price | Audience |
|---|---|---|
| Free | $0 + AI Fabric branding | trial, low-volume merchants |
| Starter | $29/mo | SMB merchants self-serve |
| **Pro** | **$49–$69/mo** | **partner-installed accounts (new SKU)** |
| Elite | $179/mo | upmarket merchants, full feature set |

The new Pro tier is introduced specifically to make integration-partner channel economics work (see PLAN-007 section 6).

Existing Loom tier definitions in `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md` should be updated to add the Pro SKU.

### 5.3 Deployments

Deployments are not a standalone SKU. They are bundled into the platform tier (deployment count limits per tier). Overage:

- LLM token pass-through: cost + 15–25% margin
- Vector storage: GB-month metered, with 50GB free per Business tier deployment
- Outbound webhooks: 100K/mo free, $0.50 per additional 100K

Pricing language for deployments must always be "included with platform tier"; never a standalone line item until self-serve self-hosting is offered.

### 5.4 Plugins (Marketplace)

V1 commitment:

- all plugins free to author, free to install
- no transaction fee, no listing fee
- pre-commit publicly to a future paid model: 70/30 publisher-favorable revenue split when monetization opens; free tier always remains
- "Verified" certification is the only paid mechanism in v1: $500–$2,000 one-time per plugin per major version

Rationale: marketplace's job in v1 is to prove ecosystem extensibility, not extract rent. Stripe, Shopify, Vercel all gave plugins away for years before charging. Pre-committing to publisher-favorable economics removes uncertainty for early authors.

---

## 6) Positioning Decisions

### 6.1 One-line position

> AI Fabric ships production AI products for verticals where governance isn't optional. Starting with Shopify. Built on a control plane no one else needs to see.

### 6.2 What we are NOT (anti-positioning)

These statements should appear internally and inform every customer-facing surface:

- not a framework competing with LangChain, Vercel AI SDK, Mastra (we lose on DX)
- not a horizontal AI control plane competing with Bedrock, Vertex Agent Builder, Copilot Studio (we lose on distribution)
- not a day-1 prototyping tool (Vercel + frontier API wins, period)
- not a general-purpose enterprise AI platform yet (that's a Series B sales motion we cannot fund today)
- not an open-source business where the framework is the revenue product

### 6.3 Audience priority

Sell down the ladder over time:

| Phase | Audience | What they buy | When |
|---|---|---|---|
| 1 (now) | Shopify merchants | Loom Companion (a product, not a platform) | active |
| 2 (6–12mo) | Integration partners (Shopify agencies) | factory access via partner portal | after Loom GA + 100 paying merchants |
| 3 (12–24mo) | Mid-market ISVs | self-serve Team/Business platform tier | after 2–3 vertical products live |
| 4 (24–36mo) | Regulated enterprise | Enterprise tier + compliance pack | after referenceable case studies |

### 6.4 Category claim

External: "Vertical AI products, operationally serious by default"
Internal shorthand: "The AI product factory"

Avoid claiming "AI platform" or "AI framework" — categories already owned, we cannot win them.

---

## 7) Implementation Steps

### 7.1 Licensing reconciliation

1. update `/LICENSE` to state Apache 2.0 for `ai-infrastructure-module/**`, `ai-fabric-product/**`, and listed public connectors
2. add a `PLATFORM_LICENSE` file at `/Platfrom/LICENSE` containing the BSL 1.1 text with the Additional Use Grant defined in 4.2
3. remove the MIT badge from `README.md`; replace with Apache 2.0 + BSL 1.1 dual badge
4. remove the "Pro Developer License" subscription offer and the 50% lifetime discount language from `README.md`
5. update `FRAMEWORK_RELEASE_STRATEGY_AND_OPEN_CORE_PLAN.md` section 9 to name BSL 1.1 explicitly and remove "proprietary placeholder" language
6. add a one-paragraph license summary to `README.md` linking to both license files

### 7.2 Pricing consolidation

1. create `/doc/Productization/PRICING.md` as the canonical internal pricing source
2. update `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md` to add the Pro tier and reference PLAN-007
3. archive `ai-infrastructure-module/MONETIZATION_STRATEGY.md` under `changes-(old-tracking-only-Ignore)` — its five-tier framework model conflicts with the open-core decision
4. update `MULTI_TENANT_RUNTIME_STRATEGY_AND_MARKET_OPPORTUNITY.md` to align the per-tenant fee with the Enterprise tier defined in 5.1
5. produce a public pricing page draft showing: Loom tiers (full), Platform Free/Team/Business (full), Enterprise ("Contact us")

### 7.3 Positioning lock-in

1. create `/doc/Productization/POSITIONING.md` as the canonical positioning source containing 6.1–6.4
2. update `GO_TO_MARKET_POSITIONING_AND_GAP_ANALYSIS.md` to align with this positioning (not replace; align)
3. update `README.md` lead paragraph to match the one-line position
4. add a section to `CLAUDE.md` (if present) or contributor docs noting that any new doc claiming a different positioning must update POSITIONING.md first

### 7.4 Doc inventory and rename

1. inventory all references to "MIT", "Pro Developer", "proprietary", "framework subscription" across the repo
2. for each, decide: update to new language, archive, or delete
3. rename `Phase 18+` planning docs to milestone-named forms ("Loom GA Plan", "Vertical-2 Plan") to remove waterfall signaling
4. consolidate scattered pricing references into citations of `PRICING.md`

---

## 8) Acceptance Criteria

This plan is complete when:

- a single Apache 2.0 LICENSE file applies to the framework directories
- a single BSL 1.1 PLATFORM_LICENSE applies to platform directories with the Additional Use Grant text
- `README.md` shows Apache 2.0 + BSL 1.1 badges only (no MIT, no Pro Developer License)
- `/doc/Productization/PRICING.md` exists and is referenced by all other pricing docs
- `/doc/Productization/POSITIONING.md` exists and is referenced by all other positioning docs
- a grep for "MIT", "Pro Developer License", "proprietary placeholder" returns zero hits in active docs (only in archived/old folders)
- `LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md` includes the Pro tier
- a public pricing page draft is reviewable

---

## 9) Dependencies

- legal review of BSL 1.1 Additional Use Grant language (external)
- decision on whether existing private discussions with prospects need re-papering under new license (sales)
- alignment with PLAN-007 on the Loom Pro SKU price point ($49 vs $69)

No engineering dependencies. This is documentation, license, and positioning work.

---

## 10) Risks

| Risk | Mitigation |
|---|---|
| BSL adoption friction with enterprise legal teams | Provide a 1-page FAQ for procurement; emphasize self-host rights and the 3-year Apache conversion |
| Existing customers signed under prior implied terms | Honor existing terms via grandfathering; new contracts use new license |
| Pricing changes confuse early adopters or active prospects | Communicate proactively to known prospects within 7 days of public update |
| Loom Pro tier cannibalizes Starter | Position Pro as partner-installed only; do not list publicly as direct option |
| Positioning lock-in feels restrictive to product team | Reserve quarterly review of POSITIONING.md; changes require explicit decision, not drift |

---

## 11) Estimated Effort

- licensing reconciliation: 1–2 days (legal review on critical path)
- pricing consolidation: 2–3 days
- positioning lock-in and doc cleanup: 2–3 days
- public pricing page draft: 1 day

Total: ~1.5 weeks of focused work, single owner, with legal review running in parallel.

---

## 12) Sequencing With Other Plans

This plan is preparatory:

- must complete before PLAN-007 (integration partners need consistent pricing and licensing to sell against)
- runs in parallel with PLAN-008 (operating model) but PLAN-008 references decisions locked here
- does not block any current engineering work on Loom GA
