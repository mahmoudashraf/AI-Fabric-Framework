# 008 Controlled Design-Partner Launch And Market Proof

Status: implementation roadmap (created 2026-05-03)

Owner mode: technical/product launch LLM session

Roadmap phase: `008` - move from technically ready to real-store proof

Priority: P0 after `007` completion. This is the next strategic gate before WooCommerce, broad partner recruitment, white-label, public marketplace expansion, or another infrastructure/product line.

Depends on:

- [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
- [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
- [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)
- [004_PARTNER_ENABLEMENT_FOUNDATION.md](004_PARTNER_ENABLEMENT_FOUNDATION.md)
- [005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md](005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md)
- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md](007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md)

Related guides:

- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md`
- `Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`

---

## Strategic Handover

The platform has enough technical maturity to stop adding broad capability and start proving real usage.

Current accepted state:

- Shopify Companion is the first reference product.
- `005` reached `DESIGN_PARTNER_READY`.
- `006.x` Thinker/Resolver exists as the governed resolution product line.
- `007` Coolify provider and staging alignment are treated as complete for the purpose of entering controlled launch work.
- Partner Enablement exists for implementation partners.
- Current user decision: use staging only for now until public routing/cutover is deliberately chosen.

This roadmap answers:

> Can real merchants and implementation partners understand, install, use, support, and get value from Loom Companion without founder-only handholding?

This is not a marketing campaign plan. It is an evidence gate.

If `008` passes, the company may proceed to controlled public launch and wider partner activity.

If `008` fails, the product should iterate against real blockers before any second product or broad GTM expansion.

---

## Core Decision

The next risk is market and operating proof, not engineering throughput.

Do not start:

- WooCommerce
- broad partner recruitment
- public partner directory
- white-label
- marketplace product expansion
- new cloud providers
- new product archetypes
- broad Elite action marketing

until `008` evidence exists.

Allowed during `008`:

- fixes required by real-store onboarding
- answer-quality improvements grounded in real queries
- partner/merchant onboarding hardening
- DNS/auth/cutover cleanup
- support, audit, evidence, and rollback improvements
- packaging and demo collateral needed to run the controlled launch

---

## Launch Scope

### Target cohort

Controlled design-partner cohort:

- 5-10 real Shopify stores
- 1-3 founding implementation partners
- mix of simple catalogs and moderately complex stores
- at least one store with enough product/policy data to test answer quality
- at least one partner-led implementation, not founder-led only

### Product package

Primary package:

- Starter: full read-only embedded store intelligence

Secondary controlled package:

- Elite: only where governed action/readiness evidence is explicitly required
- do not sell Elite as broad autonomous resolution
- keep Elite positioned as controlled governed assistance with audit and escalation

### Active environment rule

Use staging-only until public traffic routing is intentionally approved.

Before any real merchant store points at production-like public URLs:

- real DNS must replace `sslip.io`
- Supabase redirect URLs must match the selected public Partner UI
- Shopify app URLs must match the selected Bridge URL
- Shopify extension/app config must be released
- full live verification must pass
- rollback path must be documented

---

## Readiness Outcomes

`008` must end with one of these decisions.

### `DESIGN_PARTNER_ACTIVE`

Use when:

- 5-10 stores are onboarded or actively scheduled
- partner and merchant onboarding flows work
- answer-quality audit passes on real-store query packs
- support path works
- product can remain in controlled rollout
- not enough evidence yet for wider launch

### `MARKET_READY`

Use only when:

- controlled cohort produces repeatable evidence
- install/setup time is acceptable
- answer quality is good enough across stores
- partner implementation is repeatable
- support load is manageable
- pricing/trial signal is not obviously wrong
- public launch risks are bounded

### `ITERATE`

Use when:

- product works technically
- real usage exposes bounded issues
- design partners can continue
- wider launch should wait

### `NOT_READY`

Use when:

- onboarding requires too much manual founder intervention
- answer quality fails in real store contexts
- partner workflow fails
- support burden is too high
- product truth/pricing/tier claims confuse merchants
- infrastructure/auth/DNS reliability blocks real usage

Do not mark `MARKET_PROVEN` from `008`. Market proof requires a longer revenue and retention window.

---

## Workstream 1 - Environment And Public Routing

Goal:

- choose exactly what environment real design partners use and make it coherent.

Required decisions:

- staging-only controlled trial
- production public cutover
- hybrid: staging for partner sandbox, production for real merchants

Minimum work:

- audit all live URLs for Platform UI, Partner UI, Shopify Bridge, Ecommerce Store, Runtime, and customer runtime/connector apps
- remove stale `.up.railway.app`, old production IP, and `railway-*` host references from active staging path
- decide real DNS names:
  - `platform.loomai.pro`
  - `partners.loomai.pro`
  - `shopify-bridge.loomai.pro`
  - `runtime.loomai.pro`
  - `staging.platform.loomai.pro`
  - `staging.partners.loomai.pro`
  - `staging-shopify-bridge.loomai.pro`
- configure DNS
- configure TLS
- configure CORS origins
- configure runtime config endpoints
- update environment variables through provider APIs without printing values
- run full health and release-gate verification after every routing change

Exit gate:

- one selected public path has no stale host references and passes live verification.

---

## Workstream 2 - Auth And Redirect Cleanup

Goal:

- partner and merchant login/approval paths work from real public URLs.

Required checks:

- Supabase Site URL matches selected Partner UI
- Supabase Additional Redirect URLs include staging and production callback URLs
- SMTP sender domain is authorized and production-safe
- magic-link flow succeeds
- resend cooldown is readable in UI
- Partner UI login returns to the correct environment
- merchant approval paths use installed-store-first approval, not typed shop authority
- partner assignment/revocation remains auditable

Security rule:

- rotate any Supabase Management API token, Brevo SMTP key, Shopify token, Railway token, Coolify token, or other credential shared in chat or copied into temporary local files during implementation sessions.

Exit gate:

- partner signup, login, empty workspace, merchant approval request, approval/denial, and revocation work against the selected public path.

---

## Workstream 3 - Shopify App And Storefront Release

Goal:

- the Shopify-facing app config, extension assets, and Bridge URLs match the selected launch path.

Required checks:

- `shopify.app.toml` points at selected Bridge URL
- companion app config points at selected Bridge URL
- theme extension defaults use selected Bridge URL
- Shopify app/theme extension has been deployed and released
- storefront browser proof confirms the hosted extension assets are live
- Max widget opens correctly from embedded surfaces
- widget mode, attachments, and page context are observable in response metadata
- structured product/search results render as shopper-facing cards, not raw JSON
- no internal runtime/provider/Coolify/Railway language appears in shopper UI

Exit gate:

- desktop and mobile storefront proof passes on the selected store.

---

## Workstream 4 - Demo Store And Collateral

Goal:

- partners and merchants can understand the product without a long founder explanation.

Required assets:

- demo store with real-looking product catalog
- product-page embedded surfaces
- AI search demo
- product FAQ demo
- policy/context demo
- comparison demo
- Max Mode/depth-layer demo
- Starter package explanation
- Elite governed-assistance explanation with clear boundaries
- 3-5 short screen recordings
- one merchant-facing setup guide
- one partner-facing implementation guide
- support escalation guide

Collateral rules:

- lead with embedded store intelligence, not chatbot language
- do not claim Free includes order lookup
- do not claim autonomous issue resolution
- do not claim broad write actions unless verified in the exact live surface
- keep operator/provider internals out of merchant collateral

Exit gate:

- a partner can watch/read the materials and run a store setup rehearsal.

---

## Workstream 5 - Partner Onboarding

Goal:

- implementation partners can onboard and support stores without direct database or internal operator access.

Partner flow:

1. partner signs up
2. partner lands in empty workspace
3. partner views intelligence catalog
4. partner selects an installed merchant store
5. merchant approves or denies inside connected merchant/admin flow
6. partner sees scoped store workspace
7. partner runs verification pack
8. partner exports evidence/support packet
9. partner escalates with evidence when needed

Must prove:

- empty workspace is understandable
- installed-store-first approval works
- scoped access is enforced
- denied/revoked stores disappear
- evidence packet is useful
- partner cannot see operator-only internals
- partner cannot change canonical product thresholds

Exit gate:

- one non-founder partner can complete a rehearsal on a test or design-partner store.

---

## Workstream 6 - Merchant Onboarding

Goal:

- merchants can activate and understand the app without seeing platform internals.

Merchant flow:

1. install/connect app
2. confirm Knowledge Sync/readiness
3. place embedded surfaces
4. verify product page experience
5. understand Starter capabilities
6. understand what Elite is and is not
7. view support/status
8. approve partner access if needed
9. provide feedback

Must prove:

- setup language is merchant-safe
- billing/tier state is correct
- Knowledge Sync does not expose vectorization/provider internals
- no stale Free/Starter order-lookup claim appears
- support path is visible
- partner approval is clear and revocable

Exit gate:

- at least 3 merchants can complete setup with bounded support.

---

## Workstream 7 - Real-Store Answer Quality

Goal:

- prove query-to-answer behavior under real catalog/policy/store data.

For each design-partner store, create a query pack:

- product search
- product detail
- comparison
- policy
- source gap
- out-of-scope
- tier boundary
- action intent
- support/escalation
- store-specific merchant questions

Scoring rubric:

- grounded
- helpful
- honest about missing data
- tier-safe
- merchant-safe
- no internals
- uses attachments/page context when relevant
- avoids raw JSON
- produces useful next step

Evidence:

- query pack JSON
- raw response metadata with secrets removed
- answer-quality summary
- screenshots where relevant
- merchant/partner notes

Exit gate:

- real-store query packs pass or produce bounded issues with tracked fixes.

---

## Workstream 8 - Support And Escalation

Goal:

- prove the product is supportable under real usage.

Support paths:

- merchant support request
- partner support escalation
- answer-quality issue
- storefront widget issue
- billing/tier mismatch
- Knowledge Sync/indexing issue
- runtime/Coolify deployment issue
- Shopify Bridge issue
- Thinker/Resolver issue

Required support evidence:

- issue type
- store
- partner
- product package
- screenshot/log summary
- affected surface
- severity
- root cause
- fix/decision
- time to resolution
- whether docs/UI need improvement

Exit gate:

- support issues can be triaged without direct ad hoc founder memory.

---

## Workstream 9 - Metrics

Track these weekly during `008`.

Activation:

- partner signups
- partner workspaces activated
- stores approved
- stores installed
- stores with surfaces placed
- stores with Knowledge Sync ready
- stores with successful browser proof

Usage:

- shopper queries
- embedded surface interactions
- Max Mode opens
- answer-quality pass rate
- unanswered/source-gap rate
- action-intent rate
- support-escalation count

Commercial:

- trial starts
- trial activations
- Starter interest
- Elite interest
- partner time-to-first-store
- merchant time-to-live
- partner implementation hours
- support minutes per store

Infrastructure:

- Coolify runtime health
- restart count
- deploy failure count
- host CPU/RAM/disk
- backup success
- restore drill status
- runtime cost per active store

Exit gate:

- metrics are visible in a weekly decision packet.

---

## Evidence Packet

Store evidence under:

```text
/tmp/loomai-008-design-partner-proof/
```

Required artifacts:

- `summary.md`
- `cohort.md`
- `public-routing-proof.md`
- `auth-redirect-proof.md`
- `shopify-release-proof.md`
- `partner-onboarding-proof.md`
- `merchant-onboarding-proof.md`
- `answer-quality-results/`
- `support-escalations.md`
- `metrics-weekly.md`
- `known-issues.md`
- `decision.md`

Do not commit `/tmp` evidence.

Commit only durable roadmap, guide, script, UI, or code changes.

---

## Implementation Slices

### Slice 1 - Launch Path Lock

Goal:

- define and verify the environment real design partners use.

Work:

- choose staging-only, production, or hybrid launch path
- configure real DNS or explicitly document temporary `sslip.io` limitation
- align runtime configs, CORS, Bridge URLs, Partner UI URLs, and Supabase redirects
- verify health and auth from the selected public path

Done when:

- no stale host references exist in the selected path
- partner login and merchant approval work
- full verification passes

### Slice 2 - Shopify Release Proof

Goal:

- make the storefront experience real and current.

Work:

- deploy Shopify app/theme extension config
- verify hosted extension release
- browser-test desktop/mobile
- validate widget/card rendering
- confirm no internal language leaks

Done when:

- one real store passes desktop/mobile storefront proof.

### Slice 3 - Demo And Collateral

Goal:

- make the offer understandable.

Work:

- create demo store script
- create partner setup guide
- create merchant setup guide
- create short demo recordings checklist
- create support/escalation guide

Done when:

- one partner can use the collateral for a rehearsal.

### Slice 4 - Partner Rehearsal

Goal:

- prove non-founder partner setup.

Work:

- invite partner
- run empty workspace flow
- run installed-store approval
- run verification pack
- export evidence
- escalate one seeded issue

Done when:

- partner completes setup and support rehearsal.

### Slice 5 - Merchant Cohort

Goal:

- onboard 5-10 stores.

Work:

- select stores
- capture baseline details
- activate appropriate package
- place surfaces
- run readiness proof
- collect feedback

Done when:

- at least 5 stores are live or actively completing onboarding.

### Slice 6 - Real Query Audit

Goal:

- prove shopper answer quality with real data.

Work:

- build per-store query packs
- run answer-quality audit
- fix bounded issues
- record unfixable product gaps

Done when:

- query packs pass or produce a tracked iteration plan.

### Slice 7 - Launch Decision

Goal:

- decide next strategic move from evidence.

Work:

- summarize metrics
- summarize blockers
- summarize support load
- decide `DESIGN_PARTNER_ACTIVE`, `MARKET_READY`, `ITERATE`, or `NOT_READY`
- update strategic context
- update public launch or iteration roadmap

Done when:

- decision packet exists and the next roadmap is clear.

---

## Stop Conditions

Pause wider launch if:

- Supabase/Shopify auth redirects are unstable
- selected public routing still depends on accidental/stale URLs
- Shopify hosted extension is not actually live
- answer-quality fails on common real-store questions
- merchant setup requires repeated manual founder intervention
- partner cannot complete the flow with scoped permissions
- support issues cannot be reproduced from evidence packets
- Coolify/staging reliability interrupts merchant experience
- Free/Starter/Elite claims drift again
- old Railway/production URL references leak into the active staging path

---

## Acceptance Criteria

`008` is complete when:

- selected public launch path is coherent and verified
- Shopify app/extension release is live and browser-proven
- partner signup/login/approval/store workspace works
- merchant onboarding works
- at least 5 real stores are onboarded or actively completing onboarding
- real-store query packs are run
- answer-quality evidence exists
- support/escalation evidence exists
- weekly metrics packet exists
- final decision is recorded as `DESIGN_PARTNER_ACTIVE`, `MARKET_READY`, `ITERATE`, or `NOT_READY`
- strategic context is updated

---

## First LLM Session Prompt

Use this prompt to start implementation:

```text
Read first:
- Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md
- Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md
- doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md

Task:
Start 008 Controlled Design-Partner Launch And Market Proof.

Context:
- 005 is design-partner-ready.
- 006 Thinker/Resolver is implemented.
- 007 Coolify provider/staging alignment is treated as complete.
- User confirmed staging is the active environment for now.
- The next risk is market and operating proof, not more platform capability.

Start with Slice 1: Launch Path Lock.

Implement:
1. audit active staging URLs across Platform UI, Partner UI, Shopify Bridge, Ecommerce Store, Runtime, customer runtime/connector, Supabase redirects, Shopify app config, and storefront extension defaults
2. identify stale Railway/prod/sslip.io risks
3. propose or implement real DNS/auth redirect cleanup if credentials exist
4. verify partner login and merchant approval path against selected public path
5. run health and browser proof where possible
6. produce `/tmp/loomai-008-design-partner-proof/public-routing-proof.md`
7. update CODEX_WORKING_CONTEXT.md compactly

Rules:
- do not start WooCommerce, white-label, broad partner recruitment, or another product line
- do not move beyond controlled design partners without an evidence packet
- do not print or commit secrets
- keep merchant surfaces merchant-safe and operator/provider internals out
```
