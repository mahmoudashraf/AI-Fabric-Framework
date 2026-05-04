# PLAN-007 — Integration Partner Channel Launch

Status: planning document (2026-04-27)

This document defines how to open an **integration partner channel** for Loom Companion (and future vertical products) using Shopify agencies as the first partner cohort. It is distinct from a build-partner program (which is deferred per PLAN-008 section 7).

The thesis: integration partners distribute and operate finished products on behalf of merchant clients. They do not build new products on the factory. This is the Klaviyo / HubSpot Solutions Partner motion, not the Salesforce ISV motion.

---

## 1) Executive Summary

Open a hand-picked integration partner pilot with 5–10 Shopify agencies. They install Loom Companion for their merchant clients and earn recurring revenue share. The partner portal already built is repurposed as a **Loom Operations Console for Agencies**.

This plan delivers:

- a partner cohort of 5–10 Shopify agencies actively installing Loom on merchant clients
- a partner-installable Loom configuration flow under 30 minutes per merchant
- a Loom Operations Console where agencies manage their merchant accounts
- a partner certification program ensuring quality of installs
- channel rules of engagement preventing direct/partner conflict
- a tier-2 support runbook for partner escalations
- channel economics validated against the Loom Pro SKU defined in PLAN-006

---

## 2) Why Now (Not Later, Not Never)

Integration partners are a much earlier and lower-risk play than build partners because:

- they distribute a finished product; the factory's internal stability is not exposed
- their feedback is product feedback (merchants want feature X) not platform feedback (your SDK has bugs)
- the Shopify agency ecosystem is well-mapped and addressable today
- the partner portal asset already exists and finally has the right purpose
- channel acceleration is the highest-leverage investment after Loom GA

Why not later:

- without channel, Loom growth is bounded by direct App Store discovery, which is slow and crowded
- agencies that adopt Loom early become referenceable case studies
- the Klaviyo precedent is well-documented; the playbook is not novel research

Why not never:

- direct Loom sales has a revenue ceiling at SMB economics
- agency-installed accounts retain better (agencies babysit configuration)
- channel revenue compounds without proportional headcount

---

## 3) Scope

In scope:

- partner cohort selection criteria and outreach
- repurposing partner portal as Loom Operations Console for Agencies
- partner certification program (lightweight, 2-hour cert + checklist)
- channel rules of engagement
- tier-2 support runbook for partner escalations
- channel economics validation and the Loom Pro SKU rollout
- partner enablement materials (pitch deck, ROI calculator, install playbook)

Out of scope:

- public Shopify Partners program enrollment (deferred until pilot proves the motion)
- build-partner program (covered in PLAN-008 phase 4)
- co-marketing campaigns and PR (separate marketing track)
- white-label / reseller licensing (deferred)

---

## 4) Partner Selection Criteria

Hand-pick 5–10 Shopify agencies meeting all of:

- 10+ active Shopify merchant clients under management
- aggregate merchant GMV $5M–$50M (sweet spot for Pro/Elite tiers, not pure SMB)
- existing reputation as Klaviyo / Recharge / Gorgias install partner (knows the motion)
- located in a tier-1 ecommerce market (US, UK, AU, DE, NL)
- direct relationship with founder/principal (not enterprise procurement gatekeeper)
- willingness to sign a 90-day pilot agreement with shared learning commitments

Do not enroll through the public Shopify Partners program in this plan. That's a firehose of low-quality leads. The pilot's purpose is to discover the playbook, not to scale it.

---

## 5) Loom Operations Console for Agencies

The existing partner portal is repurposed. Every screen is designed for a Shopify Plus agency managing 30 merchant accounts on Loom.

### 5.1 Required capabilities (v1)

- merchant account list with status (active, paused, install-in-progress, churned)
- per-merchant: MRR, current tier, install date, last activity, support tickets
- one-click "install Loom on new merchant" flow that completes in <10 minutes
- per-merchant configuration: knowledge sources, action allowlist, confirmation policies, branding
- shared agency-level templates ("our standard Shopify Plus config") that pre-fill new installs
- aggregated reporting: merchants by tier, MRR, conversion from trial to paid, churn
- per-merchant audit log access (read-only)
- support ticket creation routed to tier-2 support

### 5.2 Out of scope for v1

- direct billing access (Shopify handles)
- merchant-side admin override (merchants still own their store)
- white-label rebrand (Business tier only, deferred)
- multi-agency hierarchy (deferred)

### 5.3 Engineering load

- the partner portal exists; gap is mostly UX and the install flow integration
- estimated 4–6 weeks of focused frontend + 2 weeks of backend work for the install flow
- depends on the <30-minute install requirement (section 6)

---

## 6) Loom Install Flow Polish

Channel partners' time is money. If install takes 4 hours of fiddling per merchant, partners will not push Loom. Target: **install in under 30 minutes per merchant, including knowledge sync and first conversation test.**

### 6.1 Required improvements

- agency template pre-fill (action allowlist, confirmation rules, branding) reduces per-install configuration
- guided "Install in 6 steps" wizard replaces freeform deployment configuration
- automated knowledge sync from Shopify catalog with progress visibility
- one-click test conversation with pre-seeded merchant questions
- automated post-install verification checklist (knowledge indexed, actions wired, confirmations active, branding applied)

### 6.2 Acceptance test

A partner installer (not a developer) completes 5 fresh merchant installs in a single working day, end-to-end including merchant signoff. If this fails, the channel cannot scale.

---

## 7) Partner Certification Program

Lightweight, mandatory, designed to protect brand consistency.

### 7.1 Certification components

- 2-hour async certification (recorded videos + quiz) covering Loom positioning, install flow, configuration best practices, support handoff
- written install checklist (PDF + console-embedded version) covering: merchant data review, knowledge source configuration, action allowlist, confirmation policy selection, branding, post-install verification
- annual recertification on major Loom version bumps

### 7.2 Certification gates

- only certified installers can use the agency template feature in the console
- merchants installed by certified partners earn a "Loom Certified Install" badge in admin (signal to merchant)
- non-certified agencies still earn rev share but cannot install at scale (can install manually one-off)

### 7.3 Why this matters

A bad install reflects on Loom, not on the partner. The certification protects the brand without slowing partner onboarding (a single working day from outreach to certified).

---

## 8) Channel Economics

### 8.1 Margin math problem

Loom Starter at $29 → Shopify takes 15% on App Store transactions → $24.65 → 25% partner rev share → ~$18.50 → LLM/vector/hosting costs → margin too thin to be sustainable.

### 8.2 Solution: Loom Pro SKU

Per PLAN-006 section 5.2, introduce a partner-installed Pro tier at **$49–$69/mo**. Channel economics:

- Pro at $49 → Shopify 15% → $41.65 → partner 25% → $31.25 → costs (~$8) → ~$23 net per merchant per month
- Pro at $69 → Shopify 15% → $58.65 → partner 25% → $44 → costs (~$8) → ~$36 net per merchant per month

Decision needed in PLAN-006: $49 or $69 base. Recommendation: **$59** as a midpoint; positions Pro between Starter and Elite without crowding either.

### 8.3 Partner rev share schedule

| Tier | Partner share | Notes |
|---|---|---|
| Starter ($29) | 20% | not the target SKU; offered for partner-touch SMB |
| Pro ($59) | 25% | the partner SKU; lifetime recurring |
| Elite ($179) | 25% | the partner-installed upmarket SKU; lifetime recurring |

Lifetime recurring (not 12-month limited) is industry standard for Shopify ecosystem and required to attract the cohort.

### 8.4 Direct sale rule

Merchants who self-install Loom directly (no partner involvement) generate no partner share. Partners receive credit only for merchants they install through the console.

---

## 9) Channel Rules of Engagement

Write before disputes, not after.

### 9.1 Account protection

- a partner that installs Loom on a merchant has 18 months of "lead protection": AI Fabric will not directly upsell, downsell, or migrate that merchant without partner involvement
- protection lapses if the partner loses certification or the merchant churns from Loom
- protection does not block AI Fabric from selling unrelated products to the same merchant

### 9.2 Direct vs partner conflict

- if a merchant approaches AI Fabric directly while already a partner-installed account, AI Fabric routes the conversation to the partner unless the merchant explicitly requests otherwise in writing
- if a merchant approaches AI Fabric directly and is not yet a Loom customer, AI Fabric may sell direct or refer to a partner at AI Fabric's discretion; no partner is "owed" the lead

### 9.3 Pricing discipline

- partners may not discount Loom below published tier pricing without written approval
- partners may bundle Loom into their service offering at any price as long as merchant pays Loom's published tier price
- AI Fabric reserves the right to run direct promotional pricing; partners receive 7 days notice

### 9.4 Termination and orderly handoff

- either party may terminate with 90 days notice
- terminated partners retain rev share on installed accounts for 12 months (orderly transition)
- AI Fabric retains the right to immediately revoke certification on quality breach

---

## 10) Tier-2 Support Runbook

Currently undefined. Partners need a defined escalation path.

### 10.1 Support tiers

- **Tier 1**: partner handles merchant questions (configuration, usage, training)
- **Tier 2**: AI Fabric handles partner escalations (Loom bugs, platform issues, knowledge sync failures)
- **Tier 3**: AI Fabric engineering handles platform-level incidents

### 10.2 Tier 2 SLA (pilot)

- response within 4 business hours
- resolution or workaround within 2 business days for Sev 2
- defined escalation channel: partner-portal-embedded ticket form + dedicated Slack channel for the pilot cohort

### 10.3 Required artifacts

- partner-facing troubleshooting playbook (top 20 issues + resolution)
- internal tier-2 runbook (oncall responsibilities, escalation paths, known-issue tracker)
- monthly office hours for the partner cohort during pilot

---

## 11) Implementation Steps

### 11.1 Wave A — Foundations (weeks 1–4)

1. complete PLAN-006 sections 5.2 and 6 (Loom Pro SKU, positioning lock-in)
2. ship Loom install flow polish per section 6 above
3. write channel rules of engagement (section 9) and tier-2 support runbook (section 10)
4. produce partner enablement materials: pitch deck, ROI calculator, install checklist

### 11.2 Wave B — Console v1 (weeks 3–8)

5. design Loom Operations Console for Agencies per section 5
6. ship console v1 with required capabilities only (no out-of-scope features)
7. wire console into existing partner portal authentication

### 11.3 Wave C — Cohort onboarding (weeks 6–10)

8. identify 20 candidate Shopify agencies meeting section 4 criteria
9. outreach: founder-to-founder, not sales-to-procurement
10. enroll 5–10 in pilot under 90-day partner pilot agreement
11. run 2-hour certification with each enrolled partner
12. run cohort kickoff: shared Slack channel, monthly office hours, weekly sync during pilot

### 11.4 Wave D — Pilot operations (weeks 8–22)

13. each partner installs Loom on 3–5 merchant clients during pilot
14. weekly cohort syncs to surface issues, refine playbook
15. tier-2 support oncall in place
16. monthly review of margin economics, churn, support load

### 11.5 Wave E — Pilot review and decision (weeks 22–26)

17. measure pilot outcomes against acceptance criteria (section 12)
18. decide: open public Shopify Partners program enrollment, or refine and run a second pilot cohort
19. archive pilot learnings in a "Channel Playbook v1" doc

---

## 12) Acceptance Criteria

The pilot is successful and the channel is ready to scale when:

- 5–10 partners are actively installing Loom (not just enrolled)
- aggregate partner-installed paying merchants exceed 50 across the cohort
- average install time under 30 minutes (measured in console)
- gross margin per partner-installed merchant exceeds 50%
- partner retention through 90-day pilot exceeds 80% (4 of 5 partners renew)
- tier-2 support load remains under 5 escalations per partner per month
- no unresolved channel conflict disputes
- one published case study per top-performing partner

If any of these fail, the right move is to refine and run a second pilot, not open the public partner program.

---

## 13) Dependencies

- PLAN-006 must complete (consistent pricing and licensing for partner conversations)
- Loom Companion GA on Shopify App Store (this plan's customer-facing surface)
- shared vector storage acceleration (PLAN-008 section 9) for partner economics to hold at scale; pilot can run before but cohort expansion blocks on this
- engineering capacity for install flow polish and console v1 (~10 engineering weeks)

---

## 14) Risks

| Risk | Mitigation |
|---|---|
| Partners enrolled but not installing | Tight pilot agreement with 90-day expectations; hand-pick on willingness, not just capability |
| Install flow remains over 30 minutes | Block cohort onboarding until flow target met; do not weaken the bar |
| Margin compression from competitive partner discounting | Section 9.3 pricing discipline; enforce |
| Brand damage from low-quality installs | Section 7 certification gates; enforce |
| Channel conflict with direct sales | Section 9 rules written and signed before pilot launch |
| Tier-2 support overload | Cap pilot cohort size; hold capacity in reserve; weekly load review |
| Loom GA delays push the entire plan back | Plan is sequenced after Loom GA; do not start Wave C until Loom GA is stable |

---

## 15) Estimated Effort

- Wave A foundations: ~4 weeks (1 product + 1 PM + legal review)
- Wave B console v1: ~6 weeks (2 frontend + 1 backend)
- Wave C cohort onboarding: ~4 weeks (1 partner manager + founder time)
- Wave D pilot operations: ~14 weeks running concurrent (0.5 partner manager + tier-2 oncall rotation)
- Wave E review and decision: ~4 weeks (1 PM + founder review)

Total elapsed time from start to decision point: ~26 weeks (6 months). Total person-effort: ~12–15 person-weeks plus oncall.

---

## 16) Sequencing With Other Plans

- depends on PLAN-006 (pricing, licensing, positioning lock-in)
- runs in parallel with PLAN-008 phases 1–2 (operating model, vertical product factory)
- precedes any build-partner program (PLAN-008 phase 4) by 12+ months
- the partner cohort pilot's outcomes feed PLAN-008 phase 3 (vertical product #2 selection criteria)
