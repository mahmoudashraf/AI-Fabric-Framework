# 010 GTM And Partner Portal Launch Readiness

Status: implementation roadmap (created 2026-05-09)

Owner mode: GTM / product operations / portal hardening LLM session

Roadmap phase: `010` - convert the 009 MCP-first product and the 009.4 merged launch package into a production-grade, self-service design-partner launch motion with partner/merchant portal readiness and automated staging-to-production promotion.

Priority: P0 after the `009.4` launch-package work was merged into this roadmap. This is the next roadmap before broad Shopify App Store launch, broad partner recruitment, developer marketplace expansion, WooCommerce, white-label, or another product line.

Parent plans:

- [008 Controlled Design-Partner Launch And Market Proof](008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md)
- [009 Shopify MCP-First Implementation Sequence](009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md)
- [009.1 Marketplace Config-Driven MCP Capability Architecture](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md)
- [009.2 MCP Execution Gateway Extraction Plan](009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md)
- [009.3 Shopify MCP Market Readiness And Release Gate](009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md)
- [009.4 Loom Companion Launch Readiness And Design-Partner Package - Merged Into 010](009_4_LOOM_COMPANION_LAUNCH_READINESS_AND_DESIGN_PARTNER_PACKAGE_MERGED_INTO_010.md)
- [004 Partner Enablement Foundation](004_PARTNER_ENABLEMENT_FOUNDATION.md)
- [Partner Enablement UI Design](004_PARTNER_ENABLEMENT_UI_DESIGN.md)

Related references:

- `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_MCP_FIRST_AND_GATEWAY_DEVELOPMENT_GUIDE.md`
- `Platfrom/partner-ui`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner`
- `product-services/shopify-bridge-service/ui`

---

## Executive Decision

The 009 series changes the business posture.

Before 009, Loom Companion was a strong product concept with too much platform optionality. After 009, the first sellable boundary is concrete:

```text
Loom Companion Starter Design Partner
Shopify MCP-first AI shopping assistant for product discovery, product detail help, policy answers, and governed commerce evidence.
```

010 must not add another platform layer. It must make the path from prospect to live production store repeatable and self-service-capable:

```text
lead
  -> qualified design partner
  -> intake
  -> Shopify install / store binding
  -> merchant portal account / magic-link access
  -> merchant approves scoped partner access
  -> staging deployment created from Marketplace staging template
  -> partner and/or merchant configures through the launch portal
  -> staging Knowledge Sync and widget activation
  -> verification pack
  -> evidence bundle
  -> merchant clicks Go production
  -> production deployment created from Marketplace production template
  -> production release gate and storefront verification
  -> support path
  -> weekly value review
  -> MARKET_READY / ITERATE decision
```

The next risk is no longer "can the platform execute Shopify MCP tools?" The next risk is whether a real merchant and implementation partner can understand, activate, verify, promote, support, and see value with founder intervention limited to exceptional escalation.

010 is not a V1/POC roadmap. The first controlled launch must be mature from day one:

- self-service account access for partners and merchants
- complete approval, activation, staging, production, evidence, support, and rollback cycles
- automated staging and production deployment creation through Platform services
- no direct merchant/partner access to Coolify, secrets, provider handles, or deployment internals
- clear state machines and evidence for every externally visible step

Implementation is ready to start, but it must start with Slice 0 current-state audit and gap mapping before feature work. The implementation session should not reopen product strategy unless it discovers a hard blocker in the codebase. Slice 0 must produce the concrete implementation map for partner portal, merchant launch portal, approval/revocation, staging/production templates, Go production, verification, and evidence gates; only then should code changes begin in bounded slices.

---

## Implementation Update - 2026-05-09

Slice 0 current-state audit was completed before code changes.

Current capability map:

- partner portal already covered signup, empty workspace, eligible stores, implementation requests, merchant-approved assignments, product controls, package trials, verification packs, evidence bundles, support/escalations, notes, and live widget tests
- merchant launch/admin approval already existed through Shopify admin Bridge and Platform endpoints for approve, deny, revoke, and merchant go-live
- Marketplace template support existed as one Shopify Companion template; it did not explicitly model staging and production template/profile resolution
- production promotion existed as a Platform go-live service, but it was not exposed as a partner-safe production-readiness surface and did not resolve the production target profile from the selected package/template posture

Implemented first 010 vertical slice:

- added partner-safe launch-readiness and production-promotion Platform APIs
- added a Partner UI `Launch` tab that calls Platform APIs only and does not expose Coolify/provider/secret internals
- added `mkp-template-shopify-companion-staging` and `mkp-template-shopify-companion-production` Marketplace template plugins without duplicating action definitions
- extended Shopify Companion package profiles with staging template, production template, and production target profile metadata
- changed Shopify provisioning to use the staging template by default and persist effective launch-template metadata in store package state
- changed Go production to resolve the package production target profile and call Platform deployment apply through Platform services
- made blocked/failed promotion attempts persist partner audit evidence even when the request returns a failure

Verification status for this slice:

- local backend integration/unit coverage passes for partner launch readiness, promotion blocking, package profile resolution, provisioning metadata, go-live target profile resolution, and Marketplace draft compilation
- the full Platform backend suite passes with the new V93 staging/production template migration included
- Partner UI build and smoke tests pass locally
- script syntax checks pass for the partner enablement and Shopify Companion verification scripts
- the partner enablement live verifier now gates deployed Partner UI assets for the `Launch` tab, launch-readiness API wiring, and production-promotion API wiring
- the partner enablement live verifier includes an explicit `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true` opt-in for a real Go production mutation; without that flag it verifies readiness/evidence and skips the mutation

Gate status after this slice:

- `010_PORTAL_READY`: locally implemented for the partner Launch surface and production-prep API path; still needs selected-environment live verification for merchant magic-link/deep-link access and the full partner/merchant browser flow
- `010_SELF_SERVICE_PRODUCTION_READY`: template/profile resolution and Platform operation path are implemented and tested; live production deployment proof still needs a staging environment run with production target profile credentials/secrets present and `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true`
- `010_DESIGN_PARTNER_ACTIVE`: still requires a real or merchant-equivalent store to complete lead/intake, merchant approval, staging verification, evidence, production promotion, support escalation, revocation, and rollback with recorded evidence

Explicit not-implemented / not-proven items discovered after the first 010 slice, before the merchant-owned remediation slice:

- direct merchant approval email invite from Shopify Admin is not implemented
- direct merchant approval email invite from the partner implementation request flow is not implemented
- merchant launch portal is only an approval page; it is not yet a merchant-scoped store workspace
- merchant launch portal cannot yet deny or revoke partner access from the approval-code flow
- merchant launch portal cannot yet show staging readiness, production readiness, evidence bundles, support/escalation items, or rollback/deactivation actions
- merchant-owned Go production approval is not implemented; the current self-service production action is partner-side production-promotion request
- production rollback/deactivation request from merchant-safe surfaces is not implemented or live-proven
- partner/merchant notification after approval, denial, revocation, production promotion, or rollback request is not implemented as a durable notification/email flow
- merchant magic-link/deep-link entry is not live-proven through a real browser/email path; the current verifier proves API approval only
- live production mutation is not proven because `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true` has not been run against a production-equivalent target
- production deployment creation/provisioning/verification through `dtp-coolify-production` is not live-proven
- failed production-promotion behavior that leaves staging untouched is modeled but not proven through a real failed promotion attempt
- the current production operation applies go-live through Platform services; it does not yet prove cloning approved non-secret staging configuration into a separate production draft/deployment record
- cohort tracker, 5-10 qualified stores, 1-3 implementation partners, and weekly value review are not implemented as a closing gate
- broad public Shopify App Store package is not release-ready until merchant-owned onboarding, production promotion, rollback, support, billing, and claim-safe collateral are proven

Remediation status added by the merchant-owned launch slice:

- direct merchant approval invite is implemented from both Shopify Admin and the partner implementation request flow
- merchant approval links now open a merchant-scoped launch workspace, not only a one-shot approval page
- merchant approval-code flow now supports approve, deny, revoke, production-promotion request, and rollback/deactivation request
- merchant workspace shows staging/production readiness, evidence bundles, support/escalation items, limitations, and available actions without provider internals
- merchant-owned Go production request is implemented through Platform services and remains blocked unless launch readiness says production promotion is ready
- rollback/deactivation request is implemented as a merchant-safe support escalation and does not mutate production state
- partner/merchant notifications are backed by durable request fields, audit evidence, and an SMTP-capable notification gateway; default configuration records/dry-runs email delivery until SMTP is explicitly enabled
- the live verifier now proves the merchant approval deep-link API workspace path and keeps the actual production mutation behind `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true`

Still not live-proven after this remediation unless the selected environment has the new build deployed and the opt-in proof is intentionally run:

- real browser/email receipt of the merchant invite
- live production deployment creation/provisioning/verification through `dtp-coolify-production`
- rollback/deactivation operator execution after the merchant request
- failed production-promotion behavior against a real failed production target while staging remains untouched
- separate cloned production deployment record proof, if that becomes a hard public-launch standard
- cohort tracker, 5-10 qualified stores, 1-3 implementation partners, weekly value review, and broad public Shopify App Store package readiness

Implementation requirement for the next slice:

- treat the remaining not-live-proven items above as explicit 010 release-gate blockers
- keep production mutation behind an intentional opt-in gate during verification
- do not add provider/Coolify/secrets/deployment internals to partner or merchant UI
- every merchant-visible action must have durable state, audit evidence, merchant-safe wording, and fail-closed behavior

Important implementation boundary:

- the current production operation applies the verified deployment version through the resolved production target profile; if the release standard requires a separate cloned production deployment record before public launch, that is the next bounded 010 slice
- production failures must continue to leave staging untouched and return merchant-safe guidance plus operator-safe diagnostics

---

## Reconsidered Analysis After 009

009 made the core architecture real enough to sell controlled design-partner access.

Accepted posture:

- Marketplace `ACTION` plugins compile runtime action catalogs.
- Bridge remains governance, auth/session, audit, package, and Shopify host boundary.
- MCP Gateway executes Shopify MCP tools and returns normalized action evidence.
- Storefront MCP search and evidence are claim-safe for the first product.
- Customer Account MCP is claim-safe only where read-only order-status auth is configured and verified.
- Checkout MCP is implementation-prepared, but not public-live-claim-safe while the managed public path is blocked by storefront password protection or missing non-dev proof.
- Production/public launch is still gated.

This means LoomAI can sell now through a controlled cohort while still building the product as complete self-service tiers:

- sell Starter first
- make Elite a complete self-service tier, not an invite-only or founder-mediated service
- do not market terminal checkout automation
- do not market refunds/returns automation
- do not market broad protected-customer-data automation
- do not lead with generic MCP marketplace language to merchants

---

## Assessment Of 009.4

`009.4` matched the launch-package need and has now been merged into this `010` execution roadmap.

What it gets right:

- It narrows the merchant-facing offer to Loom Companion for Shopify.
- It correctly hides MCP, Coolify, Hetzner, and generic platform language behind the merchant outcome.
- It defines claim-safe boundaries for Storefront, Customer Account, and Checkout MCP.
- It creates design-partner packaging, onboarding checklists, support paths, rollback, and evidence packs.
- It prevents broad App Store launch claims before production routing, app-review packaging, and self-serve onboarding are proven.
- It creates a non-dev Checkout MCP proof path without blocking design-partner launch.

What 009.4 did not fully own, and 010 now owns:

- outbound GTM funnel
- qualification criteria for the first stores
- partner portal production readiness as an end-to-end operating surface
- merchant launch portal and Shopify admin entry points as separate acceptance surfaces
- complete partner-to-merchant approval lifecycle
- staging-to-production self-service promotion
- cohort tracking and weekly value review
- metrics that decide whether to continue, price, pause, or go public
- public launch candidate packaging after design-partner evidence exists

Decision:

```text
Keep 009.4 as a historical/reference launch-package detail.
Use 010 as the active GTM, partner portal, merchant readiness, cohort, support, and evidence roadmap.
```

---

## Product Boundary For 010

Sell:

```text
Loom Companion for Shopify
AI shopping assistant for Shopify stores.
```

Launch-safe capabilities:

- storefront product discovery
- Shopify Storefront MCP catalog search
- product detail answers
- policy and FAQ answers
- read-only Max Mode / depth layer
- package-aware Free / Starter / Elite posture
- self-service Starter and Elite package selection where billing/trial posture allows it
- partner-safe verification packs
- merchant-safe evidence bundles
- self-service staging deployment for merchant review
- merchant-approved Go production flow
- support escalation and rollback path

Self-service Elite capabilities:

- governed assistance is a productized Elite capability, not a selected-design-partner exception
- Elite must compile the correct Marketplace ACTION plugin bundle from package profile truth
- Elite actions must stay governed by confirmation, permission, audit, rate limits, evidence, and support policy
- customer-account and checkout surfaces are enabled only when the store has the required Shopify auth, protected-data posture, and live verification
- if a store does not satisfy a required external Shopify gate, the Elite UI must show the blocked capability and remediation path instead of hiding the tier or requiring founder intervention

Do not sell yet:

- autonomous checkout
- terminal checkout completion
- refunds or returns automation
- protected customer data automation
- public generic MCP marketplace for merchants
- partner commissions, white-label, public directory, or affiliate workflows

---

## Target Cohort

Design-partner cohort:

- 5-10 Shopify stores
- 1-3 implementation partners
- at least 1 partner-led implementation
- at least 3 stores with enough catalog and policy content to test answer quality
- at least 2 verticals, preferably apparel/fashion and beauty/electronics

Merchant qualification:

- Shopify store is live or near-live
- store owner or operator can approve access quickly
- catalog has enough product data to make AI answers useful
- policies are published or can be supplied
- merchant agrees to weekly feedback during the design-partner period
- merchant accepts current limitations around checkout, returns, refunds, and protected customer data

Avoid in this cohort:

- stores that need complex checkout automation on day one
- stores with no usable product descriptions or policies
- highly regulated medical, financial, or legal claims
- merchants expecting full support-agent replacement
- merchants that cannot provide feedback or approve access quickly

---

## Design-Partner Offer

Recommended first offer:

```text
60-90 day design-partner access to Loom Companion Starter or Elite.
```

Terms:

- guided setup included
- no long six-month free period by default
- merchant commits to feedback and query review
- merchant allows anonymized performance evidence unless separately declined
- merchant can stop or roll back at any time
- merchant can test in staging before production
- production activation is merchant-approved through the launch portal
- paid conversion discussion starts after value evidence exists, not after an arbitrary long free window

Pricing posture:

- do not promise six months free as the default public model
- design-partner access can be free or discounted, but only inside the controlled cohort
- public Starter pricing should be decided after support load, answer quality, and conversion signal
- Elite pricing should be available as a product tier, but final public pricing can be adjusted after governed-action usage, support load, and conversion signal

---

## Persona Boundaries

### Partner Portal

Target surface:

```text
partners.loomai.pro
```

Audience:

- implementation partners
- integrators
- technical operators helping merchant stores

Meaning of production readiness:

- the portal is an end-to-end operating surface, not a document checklist
- partners can run their approved side of the lifecycle without founder-only steps
- every launch-critical action has a durable state, audit trail, notification, and recovery path
- partner actions call Platform APIs only; they never call provider, Coolify, secret, or deployment internals directly

Partner owns:

- request access to installed stores
- view approved client stores
- apply templates and playbooks
- configure allowed product controls
- prepare staging setup
- run verification packs
- export merchant-safe evidence
- run live widget smoke tests
- request or prepare production promotion when allowed
- open support escalations
- add implementation notes

Partner must not own:

- provider secrets
- deployment credentials
- raw vectorization or queue controls
- billing authority
- cross-tenant diagnostics
- production deployment changes
- protected customer data beyond explicit scoped approval

### Merchant Launch Portal

Target surface:

```text
partners.loomai.pro/merchant/*
```

This can share the same `Platfrom/partner-ui` codebase and backend API family, but it must render a merchant-scoped account and store workspace. The merchant should not see the partner's multi-client workspace, partner private notes, or operator internals.

Merchant access model:

- primary path: direct email magic link/invite when the installed-store merchant email is known or provided
- secondary path: Shopify embedded admin deep link to the merchant launch portal
- fallback path: operator-created invite only for exceptional cases
- avoid temporary user/password credentials as the normal path

Merchant portal owns:

- review the connected store and package
- approve, deny, and revoke partner access
- view staging deployment/readiness state
- review storefront widget preview and sample query evidence
- configure merchant-owned support handoff fields
- approve Go production
- view production deployment/readiness state
- view evidence bundles and limitations
- open or respond to support/escalation items visible to the merchant
- request rollback/deactivation

Merchant portal must not expose:

- partner private notes across other stores
- raw verification internals that are not merchant-safe
- provider names, Coolify UUIDs, API keys, secret bindings, queues, vector stores, or cross-tenant diagnostics

### Merchant Shopify Admin

Audience:

- merchant owner
- Shopify store admin

Shopify admin owns the trust and entry surface:

- install and uninstall
- package/tier awareness
- partner access approval, denial, and revocation
- theme app embed activation
- Knowledge Sync start/status language
- support handoff details
- visible limitations and privacy/data-use acceptance
- billing or trial acceptance when public billing is enabled
- direct link to the merchant launch portal
- clear prompt to open staging preview or continue production activation

Merchant must not see:

- Coolify, Railway, Hetzner, Qdrant, provider, secret, queue, or raw runtime language
- platform release gate internals
- cross-tenant evidence
- operator-only diagnostics

### Operator Console

Audience:

- founder/operator
- trusted platform admin

Operator owns:

- product-service deployment state
- release gates
- Bridge and MCP Gateway health
- secret rotation
- provider incidents
- partner override/revocation
- support escalation reply authority
- cohort decision evidence

---

## Workstream 1 - GTM Offer And Collateral

Goal:

- make the first offer easy to understand and easy to say yes or no to.

Deliverables:

- one-page Loom Companion Starter Design Partner offer
- merchant FAQ
- partner FAQ
- intake form
- qualification checklist
- 5-minute demo script
- outreach email and LinkedIn message sequence
- founder call script
- design-partner agreement outline
- pricing/trial posture note
- current limitations page
- screenshot checklist
- demo-store walkthrough

Required positioning:

```text
AI shopping assistant for Shopify stores.
Helps shoppers find products, understand product details, and get policy answers from your store content.
```

Do not lead with:

- MCP
- AI Fabric
- Coolify
- platform
- developer marketplace
- autonomous agents

Exit gate:

- a merchant can understand the offer, setup path, limitations, and design-partner ask without reading architecture docs.

---

## Workstream 2 - Partner Portal Production Readiness

Goal:

- make `Platfrom/partner-ui` ready to run the partner side of the full design-partner operating flow without founder-only steps.

Required portal paths:

- login and signup through Supabase
- empty partner workspace
- eligible installed-store selector
- implementation request creation
- implementation request status
- approved client-store portfolio
- store workspace overview
- product controls delegated to canonical store config
- setup / surfaces / Knowledge Sync view
- staging deployment/readiness view
- production promotion request/readiness view
- verification pack runner
- evidence bundle creation and download
- support escalation creation and reply view
- partner notes
- live Max widget smoke test
- member/profile settings

Production-readiness requirements:

- `partners.loomai.pro` or selected staging equivalent is configured
- Supabase redirect URLs match the selected Partner UI URL
- runtime config points at the selected Platform backend
- API client remains allowlisted to `/api/partners/*` and `/api/merchant/partner-access/*`
- no dummy rows, fake counts, placeholder request statuses, or static success states
- all sensitive values are redacted
- revoked store access fails closed
- mobile/tablet layouts are usable for partner field work
- every destructive or externally visible action has confirmation and clear result state
- every launch-critical step is backed by a real state and audit event
- partner can complete allowed implementation tasks without operator impersonation

Exit gate:

- one partner account can go from empty workspace to approved store workspace, complete staging setup, produce verification/evidence, and prepare production promotion without operator-only access.

---

## Workstream 3 - Merchant Account, Approval, And Admin Readiness

Goal:

- make the merchant side of the flow account-based, clear, safe, reversible, and self-service.

Required merchant surfaces across Shopify admin and merchant launch portal:

- merchant magic-link/invite acceptance
- merchant-scoped store workspace
- current package/tier summary
- setup checklist
- Knowledge Sync status and action wording
- storefront widget/app embed activation guidance
- partner access requests list
- approve partner access
- deny partner access
- revoke active partner access
- staging deployment/readiness state
- staging preview link and sample evidence
- Go production approval
- production deployment/readiness state
- support handoff configuration
- privacy/data-use explanation
- current limitations around checkout, returns, refunds, and protected customer data
- uninstall/rollback guidance

Merchant acceptance criteria:

- merchant can enter from a direct email magic link when email is known or provided
- merchant can enter from Shopify embedded admin deep link
- merchant can approve a partner request from the installed-store/admin or merchant portal flow
- merchant can revoke access and partner portal access fails closed
- merchant can activate the storefront assistant or understand what step is pending
- merchant can test staging before production
- merchant can approve Go production and see production readiness
- merchant can see what Loom Companion does and does not do in the current package
- merchant does not see operator/provider terminology

Exit gate:

- at least one merchant or merchant-equivalent tester can complete install, account access, partner approval, staging review, Go production, activation, revocation, and rollback from merchant-safe surfaces.

---

## Workstream 4 - Marketplace Staging And Production Templates

Goal:

- let merchants safely test before going live, then promote through an automated production path.

Template decision:

- create separate Marketplace deployment templates or template variants for staging and production
- do not duplicate action definitions between staging and production
- both templates must reference the same package profiles and Marketplace plugin bundles
- staging and production differ by target profile, domain strategy, secret posture, billing posture, verification requirements, and external Shopify auth/callback configuration

Required template/profile shape:

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

Go production flow:

1. merchant has staging deployment with passing verification
2. merchant reviews staging evidence and limitations
3. merchant clicks `Go production`
4. Platform clones approved non-secret staging configuration into a production draft
5. Platform resolves the production Marketplace template and Coolify production target profile
6. Platform provisions production runtime/service through provider-neutral deployment services
7. Platform resolves production secrets from secret bindings, not from partner/merchant input
8. production release gate and Shopify Companion checks run
9. merchant confirms final activation/switch
10. previous staging deployment remains available for rollback/reference until retention policy expires

Rules:

- `Go production` is a Platform operation, not a partner or merchant direct provider operation
- partner may prepare production promotion only when scoped permission allows it
- merchant must approve the production activation
- production must not rebuild from an unverified artifact when an immutable staging artifact/digest is available
- production template must not expose provider details to partner or merchant UI
- production failure must leave staging untouched and produce merchant-safe failure guidance plus operator-safe diagnostics
- Elite package activation must be self-service where package, auth, and external Shopify gates are satisfied

Exit gate:

- a staging store can be promoted to production through Platform services using production template/profile resolution, without manual Coolify edits.

---

## Workstream 5 - Lead-To-Live Cohort Operations

Goal:

- run the first cohort as a controlled operating system, not ad hoc outreach.

Cohort states:

```text
LEAD
QUALIFIED
INTRO_BOOKED
INTAKE_COMPLETE
INSTALL_READY
INSTALLED
PARTNER_REQUESTED
PARTNER_APPROVED
STAGING_DEPLOYMENT_READY
STAGING_SYNC_READY
STAGING_WIDGET_ACTIVE
STAGING_VERIFIED
PRODUCTION_REQUESTED
PRODUCTION_DEPLOYING
PRODUCTION_VERIFIED
LIVE
WEEKLY_REVIEW
CONVERSION_REVIEW
PAUSED
CHURNED
```

Minimum cohort tracker fields:

- merchant name
- shop domain
- vertical
- contact owner
- partner owner
- package
- current state
- blocker
- next action
- install date
- latest verification run
- latest evidence bundle
- staging deployment state
- production deployment state
- latest answer-quality score
- support hours
- merchant sentiment
- conversion signal

Channel plan:

- direct founder outreach to qualified Shopify stores
- small agency/integrator outreach for 1-3 implementation partners
- warm personal network outreach where available
- no public paid ads yet
- no broad partner recruitment yet

Exit gate:

- 5-10 qualified stores are onboarded or actively scheduled with clear owner, next action, and expected live date.

---

## Workstream 6 - Activation, Verification, And Production Promotion

Goal:

- make activation repeatable and evidence-backed.

Minimum lead-to-live flow:

1. qualify store
2. capture intake
3. install Shopify app or confirm installed-store record
4. choose package posture
5. create or assign partner workspace
6. partner requests store access
7. merchant approves access
8. partner reviews store workspace
9. staging deployment is created from staging template
10. source preflight passes
11. staging Knowledge Sync is ready
12. staging widget or embedded surfaces are activated
13. sample query pack passes on staging
14. verification pack is recorded
15. evidence bundle is generated
16. merchant reviews staging evidence
17. merchant clicks Go production
18. production deployment is created from production template
19. production release gate and storefront checks pass
20. merchant signs off on live design-partner launch
21. weekly review is scheduled

Verification minimum:

- product discovery query
- product detail query
- policy/FAQ query
- out-of-scope query
- no-result query
- widget bootstrap check
- support handoff check
- partner revocation check
- staging-to-production promotion check
- production rollback/deactivation check

Exit gate:

- one operator, partner, and merchant can activate a store from intake to staging evidence to production deployment without code edits or manual provider-console work.

---

## Workstream 7 - Quality, Value, And Support Loop

Goal:

- prove value and learn what blocks public launch.

Weekly review inputs:

- shopper questions
- answer-quality review
- failed/no-result queries
- source gaps
- merchant feedback
- partner setup notes
- support escalations
- widget health
- latency/error posture
- support time spent

Value signals:

- shoppers use the assistant without prompting
- product discovery queries return useful products
- policy answers reduce repeated merchant questions
- merchants can identify where store content needs improvement
- partners can resolve setup issues without founder intervention
- evidence bundles are clear enough for merchant review

Support SLA for design partners:

- P0 storefront outage: same day
- P1 install/activation blocker: next business day
- P2 answer-quality or content gap: weekly review or next planned iteration
- P3 product suggestion: backlog

Exit gate:

- support load is measured and does not exceed the founder's ability to run the cohort.

---

## Workstream 8 - Public Launch Candidate Preparation

Goal:

- prepare for public launch without prematurely claiming public readiness.

Deliverables:

- App Store listing draft
- app screenshots
- demo video outline
- support email and escalation process
- privacy policy/data-use page
- uninstall behavior proof
- billing/trial posture
- review tester instructions
- production routing decision packet
- public launch risk register
- final claim hygiene scan

Rules:

- verify current Shopify App Store and protected customer data requirements from official Shopify docs before submission
- do not submit until design-partner evidence supports the claim
- do not present Customer Account or Checkout MCP as public features unless their separate gates pass
- do not enable public self-serve install without support, billing, onboarding, and rollback paths

Exit gate:

- public launch package is ready for go/no-go review, but submission still requires explicit approval.

---

## Metrics

Activation metrics:

- time from intake to installed
- time from installed to staging active
- time from staging active to staging verified
- time from staging verified to production active
- production promotion success/failure rate
- number of founder-only interventions per store
- number of partner-only successful activations
- number of merchant-only successful approvals and Go production approvals

Quality metrics:

- answer-quality pass rate on query pack
- no-result rate
- unsupported-claim incidents
- source-gap count per store
- average and p95 assistant latency

Support metrics:

- support escalations per store
- support hours per store per week
- unresolved blockers
- rollback incidents

Commercial metrics:

- qualified lead to intro rate
- intro to install rate
- install to live rate
- live to continued-use signal
- merchant willingness to pay
- partner willingness to repeat implementation
- staging-to-production conversion rate

Minimum signal for `MARKET_READY` recommendation:

- at least 5 stores live or scheduled with clear dates
- at least 3 stores live long enough to generate usage/feedback
- at least 1 partner-led activation
- at least 1 store completes the self-service staging-to-production path
- answer-quality pass rate is acceptable across real-store query packs
- unsupported public claims are zero
- support load is bounded
- merchant setup and Go production do not require repeated founder-only explanation
- pricing/trial signal is not obviously wrong

---

## Release Gates

### `010_PORTAL_READY`

Required:

- Partner UI builds and smoke-checks successfully.
- Partner auth, empty workspace, implementation request, approved store workspace, staging setup, verification, evidence, support, production-prep, and revocation paths are verified against the selected environment.
- Merchant magic-link/deep-link access works for a merchant-scoped store workspace.
- Merchant admin or merchant portal can approve, deny, and revoke partner access.
- No dummy data or placeholder success states appear in launch-critical partner pages.
- Selected public/staging URLs and Supabase redirects are coherent.
- Marketplace staging and production template/profile resolution is available without duplicating action definitions.

### `010_SELF_SERVICE_PRODUCTION_READY`

Required:

- staging template can create or bind a staging deployment.
- production template can create or bind a production deployment.
- `Go production` creates a production draft/deployment through Platform services.
- production secrets resolve from secret bindings, not partner/merchant input.
- production verification runs after provisioning.
- failed production promotion leaves staging untouched and produces merchant-safe guidance.
- partner and merchant UIs show production state without exposing provider internals.

### `010_DESIGN_PARTNER_ACTIVE`

Required:

- at least one real or merchant-equivalent store is onboarded through the partner and merchant flows
- staging storefront assistant is active and verified
- production deployment is created through the self-service production path
- production storefront assistant is active or, if production traffic cutover is explicitly deferred, a production-equivalent target profile run has passed with a recorded reason
- sample query pack passes
- evidence bundle is generated
- support escalation path is tested
- rollback/revocation path is tested

### `010_COHORT_READY`

Required:

- 5-10 qualified stores onboarded or scheduled
- 1-3 implementation partners ready or active
- cohort tracker has owner/next-action/live-date for every store
- partner and merchant materials are claim-safe
- weekly review process is scheduled

### `010_MARKET_READY_RECOMMENDATION`

Required:

- design-partner cohort evidence supports repeatability
- onboarding is not founder-only
- staging-to-production promotion is not founder-only
- answer quality is good enough across stores
- partner implementation is repeatable
- support load is manageable
- public launch risks are bounded
- production routing and App Store package are ready for explicit go/no-go

Decision output:

```text
MARKET_READY
ITERATE
NOT_READY
```

Use the same decision language as 008 so this roadmap can close the controlled-launch loop.

---

## Non-Goals

- Do not create new MCP architecture.
- Do not start WooCommerce.
- Do not launch a public developer marketplace.
- Do not build partner commissions, affiliate tracking, public partner directory, or white-label.
- Do not market Checkout MCP, refunds, returns, or protected customer data automation as live public capability.
- Do not make partner or merchant portal a deployment/provider console.
- Do not move operator/provider internals into merchant Shopify admin.
- Do not run a large developer cohort before design-partner evidence exists.
- Do not duplicate action definitions across staging and production templates.
- Do not let partner or merchant UIs call Coolify or provider APIs directly.

---

## Implementation Sequence

Execution rule:

- start with Slice 0; do not jump directly into feature coding
- treat strategy and product boundary as settled for this roadmap
- produce a concrete gap list before changing partner, merchant, marketplace, or deployment code
- keep each implementation slice independently verifiable
- if Slice 0 discovers that an existing capability is already complete, mark it as verified and move to the next missing gap instead of rebuilding it

### Slice 0 - Current-State Audit

Read:

- `009_4_LOOM_COMPANION_LAUNCH_READINESS_AND_DESIGN_PARTNER_PACKAGE_MERGED_INTO_010.md`
- `004_PARTNER_ENABLEMENT_FOUNDATION.md`
- `004_PARTNER_ENABLEMENT_UI_DESIGN.md`
- `Platfrom/partner-ui`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner`
- `product-services/shopify-bridge-service/ui`

Produce:

- gap list for `010_PORTAL_READY`
- gap list for `010_SELF_SERVICE_PRODUCTION_READY`
- selected staging and production target profile decision
- current partner/merchant/deployment happy-path map
- confirmation of existing approval-cycle capabilities and missing self-service pieces

### Slice 1 - GTM Materials

Create or update:

- offer one-pager
- merchant FAQ
- partner FAQ
- outreach sequence
- intake form/checklist
- design-partner agreement outline
- claim-hygiene checklist

### Slice 2 - Unified Partner/Merchant Launch Portal Hardening

Implement or verify:

- production/staging runtime config
- Supabase redirects
- auth callback
- empty workspace
- merchant magic-link/deep-link access
- eligible store selector
- implementation request detail
- client store workspace
- merchant-scoped store workspace
- staging and production state views
- verification/evidence/support
- revocation failure behavior
- responsive portal smoke test

### Slice 3 - Merchant Approval Lifecycle Hardening

Implement or verify:

- direct email magic link when merchant email exists or is provided
- Shopify admin deep link into merchant launch portal
- merchant partner-access approval
- merchant denial and revocation
- notification to partner after approval/denial/revocation
- package/tier clarity
- Knowledge Sync language
- widget activation guidance
- limitations/privacy/support copy
- rollback/uninstall path

### Slice 4 - Marketplace Template And Promotion Automation

Implement or verify:

- `mkp-template-shopify-companion-staging`
- `mkp-template-shopify-companion-production`
- both templates reference the same package profiles and Marketplace plugin bundles
- staging template resolves Coolify staging target profile
- production template resolves Coolify production target profile
- production draft is cloned from approved staging config
- production secrets resolve from Platform secret bindings
- Go production operation calls Platform services, not provider APIs from UI
- production release gate and Shopify Companion verification run after provisioning

### Slice 5 - End-To-End Production Cycle Verification

Run:

- partner creates request
- merchant approves
- staging deployment is created
- partner configures store
- staging widget activates
- staging verification pack runs
- staging evidence bundle exports
- merchant clicks Go production
- production deployment is created
- production verification passes
- production widget activates or production-equivalent target proof is recorded
- support escalation opens
- merchant revokes
- partner access fails closed
- rollback/deactivation path is verified

### Slice 6 - Cohort Launch

Run:

- 50 targeted leads or smaller high-quality equivalent list
- 10-15 intro conversations
- 5-10 qualified design partners
- 1-3 implementation partners
- weekly review cycle

### Slice 7 - Market-Ready Decision

Produce:

- cohort evidence report
- launch risks
- pricing/trial recommendation
- staging-to-production automation evidence
- public launch package status
- `MARKET_READY`, `ITERATE`, or `NOT_READY` recommendation

---

## Verification Commands

Local:

```bash
git diff --check
npm --prefix Platfrom/partner-ui run build
npm --prefix Platfrom/partner-ui run smoke
mvn -f Platfrom/backend/pom.xml -Dtest=PartnerEnablementIntegrationTest test
bash -n scripts/verify-partner-enablement-live.sh
bash -n scripts/verify-shopify-mcp-gateway.sh
bash -n scripts/verify-shopify-companion.sh
```

Staging/live checks:

```bash
bash scripts/verify-partner-enablement-live.sh
bash scripts/verify-shopify-mcp-gateway.sh
bash scripts/verify-shopify-companion.sh
```

Use environment-specific secret files only. Never print, paste, commit, or document secret values.

---

## Final Output Of 010

010 is complete when Loom Companion is no longer only technically verified or launch-packaged.

It is complete when:

- the partner portal can run the design-partner implementation workflow without founder-only steps
- the merchant launch portal and Shopify admin entry points can approve, activate, understand, Go production, and revoke safely
- Marketplace staging and production templates exist without duplicated action definitions
- at least one real or merchant-equivalent store is live through the full staging-to-production flow
- the first cohort is tracked with owners and next actions
- support/evidence/quality loops are operating
- the business has a grounded go/no-go recommendation for public launch

Expected final state:

```text
Loom Companion is GTM-ready for controlled production design partners, with partner and merchant portals ready to support self-service staging, merchant-approved production promotion, cohort operations, and a MARKET_READY / ITERATE / NOT_READY decision.
```
