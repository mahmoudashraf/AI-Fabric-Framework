# LoomAI Labs Business Plan

Status: rewritten business plan (2026-05-09)

This replaces the older 2026-05-06 plan. It reflects the completed 009 MCP-first implementation series and the active 010 GTM / partner / merchant self-service launch roadmap.

Canonical execution references:

- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md`
- `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md`

---

## 1) Company Identity

**Name:** LoomAI Labs Ltd

**Registered:** United Kingdom

**Domain:** `loomai.pro`

**Founder:** Solo technical founder, Egyptian-British, based in the UK

**Public one-liner:**

```text
AI shopping assistant for Shopify stores.
```

**Expanded positioning:**

```text
LoomAI helps Shopify merchants add self-service AI shopping assistance to their storefronts: product discovery, product detail answers, policy answers, guided support, and governed commerce actions.
```

**Internal thesis:**

```text
LoomAI Labs is building the governed deployment layer for AI products. Shopify Companion is the first product. The platform, Marketplace, MCP execution, Coolify deployment, and partner/merchant launch portal are the operating engine behind it.
```

Do not lead merchant messaging with:

- AI Fabric platform
- generic MCP marketplace
- Coolify / Hetzner infrastructure
- autonomous agents
- developer ecosystem
- product factory

Those are strategic advantages, not the first customer-facing offer.

---

## 2) Strategic Thesis

The business must converge before it expands.

The first sellable business is:

```text
Loom Companion for Shopify
```

The current priority is not more platform optionality. The current priority is proving that real Shopify merchants and implementation partners can activate Loom Companion through a production-grade self-service path.

The company strategy is:

1. Sell and prove Shopify Companion first.
2. Use MCP and Marketplace internally as the capability engine.
3. Use partner and merchant portals to reduce founder intervention.
4. Use Coolify/Hetzner for repeatable tenant runtime economics.
5. Delay broad developer platform, WooCommerce, white-label, public partner directory, and generic MCP marketplace until Shopify evidence exists.

This is not a POC strategy. The first controlled launch must be mature from day one:

- self-service partner and merchant account access
- scoped merchant approval and revocation
- staging deployment before production
- merchant-approved `Go production`
- production deployment through Platform services
- evidence bundles
- support escalation
- rollback/deactivation
- package-aware Free / Starter / Elite tiers
- Elite as a complete self-service tier, not manual selected-partner assistance

---

## 3) What Is Sellable Now

### 3.1 Product

```text
Loom Companion for Shopify
```

Merchant-facing outcome:

- shoppers find products faster
- shoppers ask product and policy questions
- merchants get grounded answers from their store content
- partners can configure and verify store setup
- merchants can test in staging and approve production
- governed actions are controlled through package, permission, confirmation, audit, and evidence

### 3.2 Current Market Boundary

Sell now through a controlled production design-partner cohort:

- 5-10 Shopify stores
- 1-3 implementation partners
- self-service staging-to-production path
- no founder-only onboarding as the normal path
- weekly value review and evidence collection

Do not claim broad public Shopify App Store readiness until `010` returns a `MARKET_READY` recommendation.

### 3.3 Launch-Safe Capabilities

Launch-safe for the first cohort:

- storefront product discovery
- Shopify Storefront MCP catalog search
- product detail answers
- product FAQ
- policy and FAQ answers
- comparison
- contextual storefront surfaces
- read-only Max Mode / depth layer
- package-aware Free / Starter / Elite posture
- self-service staging deployment
- merchant-approved `Go production`
- partner-safe verification packs
- merchant-safe evidence bundles
- support escalation and rollback

Capabilities that require store-specific gates:

- customer-account/order-status surfaces require customer auth, protected-data posture where applicable, and live verification
- checkout surfaces require non-password-protected target store proof, correct Shopify auth/profile material, and live verification
- governed actions require package permission, confirmation, audit, rate limits, support posture, and evidence

Do not sell yet:

- autonomous checkout
- terminal checkout completion
- refunds automation
- returns automation
- broad protected-customer-data automation
- generic MCP marketplace for merchants

---

## 4) Product Tiers

Current tier truth:

```text
Free / Starter / Elite
```

Older `Growth` or `Pro` language is historical and must not appear in active launch material.

### 4.1 Free

Purpose:

- low-risk entry point
- lightweight store trial
- public App Store discovery later

Capabilities:

- AI search only
- limited catalog size or usage cap
- limited Knowledge Sync cadence
- mandatory `Powered by Loom Companion` badge
- docs/community support only

Free does not include:

- order lookup
- customer-account actions
- checkout actions
- governed write actions
- advanced analytics
- custom support workflow

### 4.2 Starter

Purpose:

- first serious paid product
- full read-only store intelligence
- primary design-partner package

Capabilities:

- all read-only embedded intelligence surfaces
- product insight block
- AI search
- product FAQ
- product comparison
- policy strip
- contextual pill
- read-only Max Mode / depth layer
- products, collections, pages, policies, and approved content sources
- Knowledge Sync status and readiness
- basic analytics and query review
- partner verification packs
- merchant evidence bundle
- support handoff configuration

Starter excludes:

- governed customer-account actions
- checkout actions
- refunds/returns automation
- terminal commerce operations

### 4.3 Elite

Purpose:

- complete self-service governed assistance tier
- not invite-only
- not founder-mediated
- not a custom consulting package

Elite must be implemented as a product tier that a merchant can select where billing/trial posture allows it.

Capabilities:

- everything in Starter
- governed assistance surfaces
- package-controlled Marketplace ACTION plugin bundle
- confirmation before governed actions
- audit and evidence for action attempts
- rate limits and support policy
- higher sync/verification posture
- advanced evidence and support escalation
- customer-account and checkout-related capabilities only when the store satisfies required Shopify gates

Important boundary:

Elite is complete and self-service as a tier, but individual governed capabilities can be blocked until the store satisfies package, auth, protected-data, live-verification, and supportability gates.

If a capability is blocked, the UI must show:

- what is blocked
- why it is blocked
- what the merchant or operator must do
- whether the blocker is Shopify auth, protected data, package, verification, or support posture

The fallback must not be founder intervention as the normal path.

---

## 5) Pricing Strategy

### 5.1 Principle

Use evidence-based pricing, not a long unpaid public free period.

The previous six-month free public model is no longer the default. It creates weak qualification, unbounded support, and delayed commercial signal.

### 5.2 Design-Partner Access

Recommended design-partner offer:

```text
60-90 day design-partner access to Loom Companion Starter or Elite.
```

Conditions:

- merchant agrees to feedback and query review
- merchant can test in staging before production
- merchant approves production activation
- merchant understands current limitations
- anonymized performance evidence is allowed unless declined
- paid conversion discussion starts after value evidence exists

### 5.3 Public Pricing Targets

Planning prices for public launch:

| Tier | Planning Price | Role |
|---|---:|---|
| Free | $0/month | low-risk entry, AI search only |
| Starter | $49/month | full read-only store intelligence |
| Elite | $249/month | governed assistance, advanced evidence/support |

These are planning prices, not immutable promises. Final public pricing should be confirmed after `010` evidence shows:

- support hours per store
- LLM cost per active store
- Knowledge Sync cost
- answer-quality pass rate
- merchant willingness to pay
- Elite action usage and support burden
- staging-to-production conversion rate

### 5.4 Trial Policy

Public trial target:

- 14-30 day trial after App Store/public readiness
- no default six-month public free period
- design-partner discounts are cohort-specific and not public pricing

### 5.5 Partner Commercials

Do not launch partner commissions, affiliate tracking, public partner directory, or white-label in the first GTM phase.

First partner model:

- implementation partners help merchants configure and verify stores
- partners use the portal to request access, run setup, export evidence, and escalate blockers
- commercial attribution can be added after the first cohort proves repeatability

---

## 6) Self-Service Operating Model

The operating model is part of the product.

LoomAI should not rely on founder handholding for normal merchant setup. The self-service cycle must be complete:

```text
lead
  -> qualified design partner
  -> intake
  -> Shopify install / store binding
  -> merchant portal account or magic-link access
  -> merchant approves scoped partner access
  -> staging deployment from Marketplace staging template
  -> partner and/or merchant configures through launch portal
  -> staging Knowledge Sync and widget activation
  -> verification pack
  -> evidence bundle
  -> merchant clicks Go production
  -> production deployment from Marketplace production template
  -> production release gate and storefront verification
  -> support path
  -> weekly value review
  -> MARKET_READY / ITERATE / NOT_READY decision
```

### 6.1 Partner Portal

Target:

```text
partners.loomai.pro
```

Partner owns:

- signup/login
- empty workspace
- request access to installed stores
- view approved stores
- configure allowed product controls
- prepare staging setup
- run verification packs
- export evidence bundles
- run live widget smoke tests
- prepare production promotion when scoped permission allows it
- open support escalations
- add implementation notes

Partner must not own:

- provider secrets
- Coolify access
- deployment credentials
- raw vectorization/queue controls
- billing authority
- cross-tenant diagnostics
- production deployment internals

### 6.2 Merchant Launch Portal

Target:

```text
partners.loomai.pro/merchant/*
```

The merchant launch portal can share the same `Platfrom/partner-ui` codebase and backend API family, but must render a merchant-scoped account and store workspace.

Merchant access model:

- primary: direct email magic link when installed-store merchant email is known or provided
- secondary: Shopify embedded admin deep link
- fallback: operator-created invite only for exceptional cases
- avoid temporary user/password credentials as the normal path

Merchant owns:

- review connected store and package
- approve, deny, and revoke partner access
- view staging deployment/readiness state
- review staging preview and sample query evidence
- configure merchant-owned support handoff fields
- approve `Go production`
- view production deployment/readiness state
- view evidence bundles and limitations
- open/respond to merchant-visible support items
- request rollback/deactivation

### 6.3 Shopify Admin

Shopify admin is the trust and entry surface.

It should expose:

- package/tier summary
- setup checklist
- Knowledge Sync status
- theme app embed guidance
- partner access request approval/denial/revocation
- link to merchant launch portal
- support handoff
- privacy/data-use explanation
- uninstall/rollback guidance

It must not become an operator console.

---

## 7) Staging And Production Deployment Model

Merchants should test before going live.

Use two Marketplace deployment templates or variants:

```text
mkp-template-shopify-companion-staging
  -> default target profile: Coolify staging
  -> supports Free / Starter / Elite package profiles
  -> staging runtime domain pattern
  -> staging Bridge/Gateway bindings
  -> billing disabled or test-only
  -> staging verification pack required

mkp-template-shopify-companion-production
  -> default target profile: Coolify production
  -> supports Free / Starter / Elite package profiles
  -> production runtime domain pattern
  -> production Bridge/Gateway bindings
  -> billing/trial posture enabled when public billing is active
  -> stricter release, backup, rollback, and support gates
```

Rules:

- do not duplicate action definitions across staging and production templates
- both templates reference the same package profiles and Marketplace plugin bundles
- staging and production differ by target profile, domain strategy, secret posture, billing posture, verification requirements, and external Shopify auth/callback configuration
- `Go production` is a Platform operation, not a direct Coolify operation
- partner and merchant portals never call provider APIs directly
- production secrets resolve from Platform secret bindings
- failed production promotion leaves staging untouched
- production failure produces merchant-safe guidance and operator-safe diagnostics

---

## 8) Platform Advantage

The merchant buys Loom Companion. The business advantage is the platform behind it.

Platform capabilities:

- multi-provider LLM runtime
- RAG / Knowledge Sync pipeline
- Shopify MCP execution through MCP Gateway
- Marketplace ACTION plugin catalog
- package-profile-driven tier capability selection
- Bridge governance, auth/session binding, audit, rate limits, and support boundaries
- confirmation governance
- partner and merchant launch portals
- managed product services
- Coolify deployment provider on Hetzner for tenant runtimes
- Railway retained for Platform control-plane surfaces, billing, webhooks, partner UI/backend, and platform administration

Key architectural stance:

```text
Marketplace ACTION plugins define visible product capabilities.
Shopify MCP executes Shopify-facing tools.
Bridge governs and audits.
Platform provisions and verifies.
Partner/merchant portals operate the launch cycle.
```

The moat is governed, packaged, self-service AI product deployment.

---

## 9) Distribution Strategy

### 9.1 Phase 1: Controlled Production Design Partners

Target:

- 5-10 Shopify stores
- 1-3 implementation partners
- at least one partner-led activation
- at least one store completes self-service staging-to-production

Channels:

- direct founder outreach to qualified Shopify stores
- warm network
- selected Shopify agencies/integrators
- targeted LinkedIn outreach
- no paid ads yet
- no broad public partner recruitment yet

Qualification:

- live or near-live Shopify store
- merchant can approve access quickly
- enough catalog/policy content for answer quality
- merchant agrees to feedback
- merchant accepts current limitations
- merchant can test staging before production

### 9.2 Phase 2: Public Shopify App Store Candidate

Only after `010_MARKET_READY_RECOMMENDATION`.

Prepare:

- App Store listing
- screenshots
- demo video
- privacy/data-use copy
- support process
- billing/trial posture
- review tester instructions
- production routing decision
- public launch risk register

Do not submit publicly until:

- onboarding is not founder-only
- staging-to-production works
- answer quality is acceptable
- support load is manageable
- unsupported claims are zero
- App Store copy matches verified capability

### 9.3 Phase 3: Partner Scale

After repeatable cohort proof:

- expand implementation partner count gradually
- add partner commercial attribution
- add commission workflow only when merchant subscription flow is stable
- consider public partner directory later

### 9.4 Phase 4: Developer Platform

Developer platform remains the long-term attraction engine, not the current GTM priority.

Do not start large developer cohorts before Shopify Companion has real product and commercial signal.

---

## 10) Financial Plan

### 10.1 Unit Economics Assumptions

Planning assumptions:

- LLM and inference variable cost: $5-20 per active merchant/month depending on usage and tier
- vector/search/storage cost: low per merchant at early scale, but must be measured
- support time is the real early cost
- Elite support burden must be measured before aggressive public scaling

Target gross margin after support stabilizes:

- Starter: 70%+ gross margin
- Elite: 75%+ gross margin after governed-action support patterns stabilize

### 10.2 Revenue Scenarios

The old plan projected broad free adoption too early. The revised plan is evidence-gated.

#### First 90 Days

Goal:

- 5-10 design-partner stores
- 1-3 implementation partners
- at least one full staging-to-production flow
- minimal or discounted revenue

Expected MRR:

```text
$0-$1,500
```

Success is not revenue yet. Success is repeatable activation, answer quality, support load, and willingness-to-pay signal.

#### Months 3-6

If cohort evidence is positive:

```text
10-25 active stores
mix of Starter and Elite
target MRR: $1,500-$5,000
```

#### Months 6-12

If public launch is approved:

```text
40-100 active paid stores
target MRR: $5,000-$15,000
```

#### Months 12-18

If retention and support are healthy:

```text
100-250 active paid stores
target MRR: $15,000-$40,000
```

Only at this stage should the business seriously expand into WooCommerce, broader developer platform, public partner program, second product line, or larger Egypt operations.

---

## 11) Metrics And Gates

### 11.1 Activation Metrics

- time from intake to installed
- time from installed to staging active
- time from staging active to staging verified
- time from staging verified to production active
- production promotion success/failure rate
- founder-only interventions per store
- partner-only successful activations
- merchant-only successful approvals and `Go production` approvals

### 11.2 Quality Metrics

- answer-quality pass rate on query pack
- no-result rate
- unsupported-claim incidents
- source-gap count per store
- average and p95 assistant latency
- widget bootstrap success rate

### 11.3 Support Metrics

- support escalations per store
- support hours per store per week
- unresolved blockers
- rollback/deactivation incidents
- recurring setup questions

### 11.4 Commercial Metrics

- qualified lead to intro rate
- intro to install rate
- install to live rate
- staging-to-production conversion rate
- trial/design-partner to paid conversion signal
- merchant willingness to pay
- partner willingness to repeat implementation

### 11.5 Release Gates

Use the `010` decision model:

```text
010_PORTAL_READY
010_SELF_SERVICE_PRODUCTION_READY
010_DESIGN_PARTNER_ACTIVE
010_COHORT_READY
010_MARKET_READY_RECOMMENDATION
```

Final decision:

```text
MARKET_READY
ITERATE
NOT_READY
```

Do not proceed to broad public launch without this decision.

---

## 12) Risks

### 12.1 Shopify Builds Native AI

Probability: medium-high

Impact: high

Mitigation:

- focus on governed deployment, partner operations, evidence, and vertical workflows
- avoid generic chatbot positioning
- use Shopify MCP rather than fighting Shopify's platform direction
- make the value operational: setup, evidence, governance, support, and merchant-specific grounding

### 12.2 Self-Service Flow Is Too Complex

Probability: medium

Impact: high

Mitigation:

- start `010` with Slice 0 audit
- build only missing gaps
- require state, audit, notifications, and evidence for every launch-critical step
- keep provider internals out of partner/merchant surfaces

### 12.3 Answer Quality Fails Across Real Stores

Probability: medium

Impact: high

Mitigation:

- run real-store query packs
- measure no-result and unsupported-claim incidents
- improve Knowledge Sync and source preflight
- do not scale public launch before answer quality is repeatable

### 12.4 Elite Support Burden Is Higher Than Expected

Probability: medium

Impact: medium-high

Mitigation:

- keep Elite self-service as a tier
- gate individual capabilities by package, auth, protected data, live verification, and support posture
- show blockers/remediation in UI
- measure support hours before final public pricing

### 12.5 Founder Bottleneck

Probability: high

Impact: critical

Mitigation:

- no founder-only normal path
- self-service partner and merchant portals
- staging-to-production automation
- support escalation structure
- hire only when evidence shows repeated operational pain

---

## 13) Team And Hiring

Current operating model:

```text
Founder
  -> product/platform implementation
  -> first sales conversations
  -> Slice 0 / 010 execution oversight
  -> selected escalations
```

Do not hire before there is repeated operational pain.

Hiring triggers:

- 10+ active stores and support load is repeating
- 3+ partner-led implementations produce recurring questions
- founder spends more than 25-30% of week on support/onboarding
- public launch is approved and install volume starts increasing

First hire profile:

- partner/merchant success operator
- understands Shopify
- can run onboarding, evidence review, support triage, and documentation

Egypt operations remain a strategic advantage, but the large alumni/developer cohort should wait until Shopify Companion has product and commercial signal.

---

## 14) 12-Month Roadmap

### Now: 010 Slice 0

- audit partner portal
- audit merchant/admin approval flow
- audit merchant launch portal gap
- audit Marketplace staging/production template support
- audit `Go production` backend flow
- audit verification/evidence gates
- produce gap list before coding

### 0-30 Days

- close `010_PORTAL_READY` gaps
- close `010_SELF_SERVICE_PRODUCTION_READY` gaps
- implement merchant magic-link / deep-link access
- implement staging and production Marketplace template resolution
- implement or verify `Go production`
- verify evidence/support/rollback paths

### 30-90 Days

- onboard 5-10 design-partner stores
- activate 1-3 implementation partners
- complete at least one full staging-to-production flow
- run weekly value reviews
- measure support load and answer quality
- decide `MARKET_READY`, `ITERATE`, or `NOT_READY`

### 3-6 Months

If `MARKET_READY`:

- prepare and submit Shopify App Store package
- launch public trial/pricing
- scale to 10-25 active stores
- refine Starter and Elite pricing
- formalize first partner commercial model only if needed

If `ITERATE`:

- fix onboarding, answer quality, support, or pricing issues
- keep cohort controlled
- do not broaden GTM

### 6-12 Months

If public launch works:

- scale install/review loop
- mature Elite governed capabilities
- hire first support/success role
- start partner expansion carefully
- begin planning second product only after Shopify signal is durable

---

## 15) What Not To Do Yet

Do not start:

- WooCommerce
- BigCommerce
- broad developer marketplace
- public MCP marketplace
- broad partner recruitment
- partner commissions/affiliate tracking
- white-label
- public partner directory
- large Egypt developer cohort
- refunds/returns automation as a public claim
- terminal checkout automation

These can become valuable later, but they are distractions before Shopify Companion proves activation, value, supportability, and willingness to pay.

---

## 16) Summary

LoomAI Labs is not trying to sell a platform first.

It is selling one concrete product:

```text
Loom Companion for Shopify
```

The first market motion is controlled but production-grade:

- real merchants
- real partners
- self-service accounts
- merchant approval/revocation
- staging before production
- merchant-approved `Go production`
- evidence and support loops
- Free / Starter / Elite tiers
- Elite as complete self-service, with capability gates where Shopify/security/support require them

The company becomes a platform later by proving one product now.

The next operational truth is `010`:

```text
Make partner and merchant self-service launch readiness real, then decide MARKET_READY / ITERATE / NOT_READY.
```
