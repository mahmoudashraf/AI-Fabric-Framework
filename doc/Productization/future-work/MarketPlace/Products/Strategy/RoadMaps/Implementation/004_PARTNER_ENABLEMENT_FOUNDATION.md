# Partner Enablement Foundation

Status: comprehensive implementation handoff (revised 2026-04-25)

Owner mode: technical LLM implementation session

Roadmap phase: Phase 3 - Partner Enablement Foundation

Priority: P0/P1

Depends on:

- [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
- [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
- [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)

---

## Strategic Handover

The first three Shopify Companion implementation phases are complete and verified.

Accepted state:

- Launch Truth Enforcement is complete.
- Storefront Product Shell is complete.
- Starter Launch Package is complete.
- current tiers are `Free / Starter / Elite`.
- Free is AI search only.
- Starter is full read-only embedded store intelligence.
- Starter excludes order lookup and governed actions.
- Elite is the only tier for verified governed actions.
- Shopify Companion is positioned as embedded store intelligence, not a chatbot.
- chat and Max Mode are the depth layer.
- `Knowledge Sync` is merchant-facing language.
- raw vectorization, provider, queue, replay, runtime, and debug language is operator-only.

This Phase 3 handoff starts Partner Enablement Foundation.

The partner strategy is implementation support for developers, integrators, agencies, and consultants who help client stores or current apps add LoomAI intelligence pieces. This is not an affiliate program, referral dashboard, passive acquisition surface, public partner signup, commission workflow, or white-label program.

The implementation target is now a mature partner enablement operating layer, not only a founding-partner document kit. The platform already has one working Shopify store and verified intelligent embedded surfaces, so partner enablement should be designed as a complete product capability from the start, shipped in controlled increments.

The first usable release can still start with founding partners, but the architecture and handoff must cover the complete path: partner access, client-store portfolio, intelligence catalog, client store workspace, setup and verification packs, support escalation, evidence exports, templates, auditing, and operator override. The goal is to make a serious implementation partner able to deploy and support multiple client stores without full operator access or live explanation every time.

Canonical partner offer:

- add LoomAI-powered intelligence surfaces to client stores and current apps without building the AI infrastructure from scratch
- start with Shopify Companion and the verified Starter surface catalog
- keep partner work bounded to setup, verification, support handoff, evidence, and escalation
- keep platform operator internals out of partner and merchant surfaces

Why this goes next:

- Starter is now sellable and verified enough to be used as the first partner-facing package.
- Partner enablement gives the solo developer leverage without promising public partner scale.
- The intelligence catalog, setup checklist, verification pack, and escalation template will also strengthen design-partner and launch workflows.
- Building partner materials now prevents future partner sessions from inventing product claims, tier rules, or support promises.
- A mature platform needs the partner operating layer before broad market activity, otherwise every implementation becomes bespoke support from the founder.

---

## Read First

Read these before editing code or docs:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
4. [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
5. [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)
6. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](../SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
7. [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](../SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
8. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](../SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
9. [RoadMaps_BackLog.md](../RoadMaps_BackLog.md)
10. [PARTNER_DASHBOARD_STRATEGY_PLAN.md](../../PARTNER_DASHBOARD_STRATEGY_PLAN.md)

Supporting persona and UI context:

- [PLATFORM_UI_PERSONA_SEPARATION_PLAN.md](../../PLATFORM_UI_PERSONA_SEPARATION_PLAN.md)
- [PLATFORM_UI_REDESIGN_DIRECTION.md](../../PLATFORM_UI_REDESIGN_DIRECTION.md)

Useful existing Shopify Companion docs:

- [SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md](../../../../../../../../Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md)
- [SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md](../../../../../../../../Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md)
- [SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md](../../../../../../../../Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md)
- [SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md](../../../../../../../../Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md)
- [SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md](../../../../../../../../Final_Documentation/Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md)

---

## Working Rule

The technical LLM session must keep this file updated:

- [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)

Append compact notes for:

- decisions made during implementation
- files changed
- tests run
- blockers
- skipped checks
- handoff state

Do not paste long logs, diffs, secrets, or noisy reasoning into the working context.

Use this compact template:

```text
- Partner Enablement Foundation status: <complete/partial/blocked>.
- Changed files: <compact list>.
- Decisions: <only new decisions>.
- Verification: <commands run and pass/fail>.
- Live verification: <passed/skipped/blocker/not needed>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```

---

## Implementation Brief

Task:

- implement the complete Partner Enablement Foundation for implementation partners

Primary outcome:

- implementation partners can understand the Shopify Companion Starter package, deploy verified LoomAI intelligence surfaces to assigned client/test stores, verify each surface, monitor health, support normal setup issues, and escalate with evidence without full operator access or live platform-operator explanation

This handoff should be treated as a mature platform implementation plan, delivered incrementally. Do not stop at a documentation kit if code-level partner capabilities are feasible. Also do not start with public partner scale. Build the private implementation-partner operating system first.

Complete product capabilities:

- partner identity and scoped access
- partner-member roles
- partner-store assignment and revocation
- partner home and client-store portfolio
- client store workspace
- intelligence-piece catalog
- sandbox/demo center
- setup/deployment checklist
- verification and launch center
- evidence/export packet generation
- support and escalation center
- vertical templates and implementation playbooks
- partner action audit trail
- operator override and revocation
- merchant-safe boundary

Initial intelligence catalog:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only chat/depth layer

Catalog rules:

- Free = AI search only.
- Starter = all read-only embedded intelligence surfaces.
- Starter excludes order lookup.
- Elite-only surfaces must be marked later/gated/verified-only.
- Do not present governed actions as current partner-deployable Starter work.

Vertical playbooks to start with:

- fashion/apparel: sizing, reviews, product fit, policies
- electronics: comparison-heavy buying, specs, compatibility
- health/beauty: ingredient/use-case questions, policy clarity

Optional fourth playbook if cheap:

- home/furniture: dimensions, materials, delivery/return policy context

Do not:

- build affiliate/referral/commission workflows
- build public partner signup
- build partner directory
- build certification
- build white-label packaging
- build public partner API
- build broad custom product assembly
- expose secrets, tokens, provider credentials, deployment internals, raw vectorization controls, runtime controls, or queue/replay internals to partners
- push partner/operator packet content back into the merchant Shopify admin as long inline text
- start broad platform UI redesign as a prerequisite
- start WooCommerce or second-product work
- loosen Free/Starter/Elite tier truth
- add order lookup to Free or Starter

---

## Build Order

### Step 0: Product Boundary And Current-State Inventory

Close:

- inventory current Shopify Companion surfaces, merchant admin exports, platform product-service views, Shopify store views, verification scripts, and live evidence sources
- confirm merchant admin remains focused on setup, surfaces, Knowledge Sync, billing, support handoff, usage/value, and blockers
- confirm long partner/operator packet text is not rendered inline for merchants
- confirm partner-only enablement language does not leak into shopper surfaces
- decide what is partner-facing, operator-only, merchant-facing, or shopper-facing
- record any old affiliate/referral/commission/public-signup language as deferred or retired

Exit:

- partner enablement has a clear product boundary and does not depend on later UI redesign

### Step 1: Partner Domain And Access Model

Close:

- partner account model
- partner member model
- partner roles:
  - partner admin
  - partner implementer
  - partner developer
  - partner support
- partner invitation/activation posture
- partner session and access boundary
- partner-store assignment model
- merchant/operator revocation model
- platform-operator override model
- audit events for partner access and actions

Recommended first implementation:

- start with private/operator-created partners
- no public signup
- partner can be linked to assigned stores only
- partner actions are read-mostly until explicit safe actions are defined
- all write actions require scoped permission and audit

Exit:

- one implementation partner can be represented in the platform with assigned stores and without full operator access

### Step 2: Partner Workspace Shell

Close:

- partner-specific navigation and information architecture
- partner home page
- client stores page
- intelligence catalog page
- verification packs page
- support/escalations page
- templates/playbooks page
- documentation entry point
- empty, loading, blocked, unauthorized, and revoked states
- clear separation from operator control plane and merchant Shopify admin

Recommended first UI:

- use a separate partner route or workspace inside the Platform UI if fastest
- keep labels partner-safe and implementation-focused
- do not expose deployments, providers, secrets, Railway, raw vectorization controls, or runtime internals

Exit:

- partner has a dedicated workspace surface that does not look like a filtered operator admin panel

### Step 3: Client Store Portfolio

Close:

- assigned store list
- shop domain and merchant name
- current plan
- install status
- enabled intelligence pieces
- storefront readiness
- Knowledge Sync status
- webhook/live update health
- top blocker
- last activity
- usage/value signal
- escalation state
- owner/contact
- quick links to client workspace, verification pack, support packet

Exit:

- partner can prioritize assigned stores and see which stores need action without using operator tools

### Step 4: Client Store Workspace

Close:

- setup checklist
- source readiness
- surface placement status
- widget/settings summary
- support handoff profile
- bounded billing visibility
- Knowledge Sync summary
- verification run history
- launch readiness summary
- usage/value summary
- blocked-state next action

Partner-safe allowed actions, if implemented:

- copy/download verification pack
- copy/download support packet
- mark manual verification step complete
- add partner note
- create escalation
- request operator action

Partner actions to defer until explicitly scoped:

- raw sync/retry
- billing changes
- credential changes
- install OAuth changes
- provider/runtime/deployment changes
- governed action configuration

Exit:

- partner can onboard or inspect a client store without seeing operator internals

### Step 5: Intelligence Catalog

Close:

- durable catalog entries for every verified Shopify Companion surface
- tier availability
- required source data
- required Shopify scopes or merchant actions
- storefront placement
- setup instructions
- verification checks
- healthy result
- failure signs
- known limitations
- launch-safe claim text
- escalation evidence to capture
- demo/sandbox link or screenshot target

Catalog entries required:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only chat/depth layer

Later/gated catalog entries:

- order lookup
- governed add-to-cart
- cart update
- support handoff
- advanced value reporting

Exit:

- partner can choose a surface, know whether a client store is ready, implement it, verify it, and explain it to a client

### Step 6: Sandbox And Demo Center

Close:

- demo store link and purpose
- sample product-page surfaces
- sample merchant admin flow
- sample launch packet
- sample verification pack
- sample support escalation
- before/after examples
- screenshot/demo clip checklist
- known demo limitations

Exit:

- partner can learn and sell the implementation workflow before touching a client store

### Step 7: Verification And Launch Center

Close:

- install checklist
- theme app embed checklist
- app block placement checklist
- Knowledge Sync readiness checklist
- billing/tier verification checklist
- storefront surface verification checklist
- Max Mode/depth-layer handoff verification
- analytics/value proof checklist
- Free AI-search-only gate
- Starter no-order-lookup gate
- Elite-only gated capability checks where relevant
- evidence capture checklist
- verification run status and history

Implementation rule:

- reuse existing live verifier outputs and Shopify Bridge/Platform readiness APIs where possible
- partner view should show pass/fail/blocked/next action, not raw logs by default

Exit:

- partner can run or follow a repeatable verification pack and produce evidence for launch/support

### Step 8: Evidence And Packet Generation

Close:

- shared packet generation boundary
- partner launch packet
- partner verification pack
- support bundle
- lifecycle/subscription packet
- design-partner rollout packet
- App Review support material where relevant
- compact summary cards plus copy/download actions
- no long packet walls inside merchant admin

Exit:

- partner and operator can retrieve consistent evidence without duplicating copy or drifting from live product truth

### Step 9: Support And Escalation Center

Close:

- escalation creation
- escalation list
- escalation status
- owner
- next action
- due date
- reproduction steps
- expected vs actual behavior
- attached evidence links
- verifier/manual checks already run
- client/store impact
- resolution notes
- operator-only internal notes separated from partner-visible notes

Exit:

- support escalations arrive with enough context for the platform builder/operator to act without reconstructing the issue from chat history

### Step 10: Templates And Vertical Playbooks

Close:

- vertical presets
- source presets
- surface presets
- support handoff templates
- launch checklist templates
- troubleshooting playbooks
- partner agreement/scope checklist
- private founding-partner operating flow

Initial vertical playbooks:

- fashion/apparel: sizing, reviews, product fit, policies
- electronics: comparison-heavy buying, specs, compatibility
- health/beauty: ingredient/use-case questions, policy clarity
- home/furniture: dimensions, materials, delivery/return policy context

Exit:

- partner has repeatable starting points for first client conversations and deployments

### Step 11: Audit, Security, And Governance

Close:

- partner action audit events
- partner-store access audit
- partner note/escalation audit
- revoked access behavior
- unauthorized access tests
- secret redaction
- operator-only data redaction
- merchant-safe data redaction
- least-privilege defaults
- internal support evidence boundary

Exit:

- partner enablement is supportable without expanding trust to full operator access

### Step 12: Release And Rollout Gates

Close:

- private founding partner readiness gate
- internal operator readiness gate
- documentation readiness gate
- verification readiness gate
- support readiness gate
- rollback/revocation procedure
- metrics dashboard or report

Private founding partner gate:

- one partner account exists
- one or more stores assigned
- catalog available
- client store workspace usable
- verification pack usable
- escalation template usable
- partner cannot access unassigned stores
- partner cannot see secrets or operator internals

Broad partner scale gate:

- several stores have been deployed or supported through the partner workflow
- repeated blockers are fixed or documented
- support load is measurable
- partner-store access is scoped and revocable
- templates are repeatable
- product reliability is strong enough for broader promises

Exit:

- platform can support founding implementation partners now and has clear gates before public scale

---

## Implementation Slices

Use these as discrete LLM work packages. Do not collapse all of them into one risky session.

### Slice A: Partner Enablement Data And Contracts

Deliver:

- partner account/member/role model
- partner-store assignment model
- access/revocation model
- audit model
- API summaries for partner home, portfolio, client workspace, catalog, verification, and escalations

Exit:

- contracts exist and can be tested without a polished UI

### Slice B: Partner Workspace Shell

Deliver:

- partner route/workspace
- partner navigation
- partner home
- client stores list
- empty/unauthorized/revoked states

Exit:

- one private partner can log in or be simulated and see only assigned stores

### Slice C: Intelligence Catalog And Demo Center

Deliver:

- catalog entries for verified Starter surfaces
- sandbox/demo center
- launch-safe claims
- setup and verification instructions
- limitations

Exit:

- catalog is usable by a partner without reading strategy docs

### Slice D: Client Store Workspace And Verification Pack

Deliver:

- client store workspace
- setup checklist
- Knowledge Sync/source readiness
- surface placement status
- verification checklist
- evidence capture

Exit:

- partner can verify a store from the workspace

### Slice E: Support And Escalation Center

Deliver:

- support bundle/packet access
- escalation creation
- escalation status/owner/next action/due date
- evidence attachment or links
- operator/partner note boundary

Exit:

- escalations are structured and actionable

### Slice F: Templates, Playbooks, And Rollout Gate

Deliver:

- vertical playbooks
- implementation templates
- private founding-partner operating flow
- rollout checklist
- metrics and acceptance proof

Exit:

- Partner Enablement Foundation is ready for a real founding implementation partner

---

## Data Model Targets

Use existing platform/customer/store entities where they fit, but keep partner concepts explicit.

Required concepts:

- `PartnerAccount`
- `PartnerMember`
- `PartnerRole`
- `PartnerStoreAssignment`
- `PartnerStoreAccessStatus`
- `PartnerActionAudit`
- `IntelligenceCatalogEntry`
- `PartnerVerificationPack`
- `PartnerVerificationRun`
- `PartnerVerificationStep`
- `PartnerSupportEscalation`
- `PartnerSupportNote`
- `PartnerEvidenceBundle`
- `PartnerTemplate`
- `PartnerPlaybook`

Relationship rules:

- partner account has many members
- partner account has many assigned stores
- store can start with zero or one primary partner
- assignment can be approved, active, suspended, or revoked
- partner can only see assigned stores
- operator can override or revoke
- merchant approval can be added later if not already available
- every partner action that changes state is audited

---

## API Surface Targets

Prefer partner-safe APIs over exposing operator APIs directly.

Required read APIs:

- partner session summary
- partner home summary
- partner client-store portfolio
- partner client-store workspace
- intelligence catalog
- sandbox/demo center summary
- verification pack summary
- verification run history
- support escalation list/detail
- templates/playbooks list/detail

Required write APIs:

- create/update partner note
- create/update escalation
- mark manual verification step
- request operator action
- download/copy evidence packet

Operator-only APIs:

- create partner
- invite partner member
- assign store
- revoke store assignment
- override partner access
- resolve escalations
- view internal evidence

Do not expose:

- raw secrets
- provider credentials
- Railway variables
- runtime admin controls
- raw vectorization/replay controls
- arbitrary sync/retry until scoped and audited
- unassigned store data

---

## UI Surface Targets

Partner workspace pages:

- Home
- Client Stores
- Client Store Workspace
- Intelligence Catalog
- Sandbox/Demo Center
- Verification Packs
- Support Center
- Escalations
- Templates And Playbooks
- Documentation

Partner UI should feel operational and efficient:

- dense but readable tables
- status filters
- clear blockers
- next actions
- copy/download actions
- verification state
- compact evidence summaries
- no decorative marketing hero pages

Status language:

- Ready
- Needs setup
- Blocked
- Verification failed
- Waiting on merchant
- Waiting on operator
- Revoked
- Escalated

Partner-safe language:

- `Knowledge Sync`
- `source readiness`
- `surface placement`
- `verification pack`
- `support handoff`
- `evidence bundle`

Operator-only language:

- vectorization internals
- runtime provider
- Railway deployment
- raw credentials
- replay queue
- debug logs
- infrastructure diagnostics

---

## Mature Platform Acceptance Criteria

This handoff is complete when:

- partner enablement is represented as a real private partner workspace or equivalent mature platform surface
- partner identity, roles, store assignment, revocation, and audit are implemented or explicitly stubbed with a safe migration path
- partner can see only assigned stores
- partner can inspect client-store readiness without operator internals
- intelligence catalog covers verified Shopify Companion Starter surfaces
- partner can run or follow a verification pack for each surface
- Free AI-search-only and Starter no-order-lookup gates are included in partner verification
- support escalation captures owner, status, next action, evidence, and resolution notes
- evidence packets reuse live product truth and do not drift from merchant/App Review/support exports
- vertical playbooks exist for at least 3 merchant types
- merchant admin remains merchant-safe and not cluttered with partner/operator long-form content
- operator can create/revoke partner access
- partner cannot access secrets, provider credentials, Railway/runtime internals, raw vectorization controls, or unassigned stores
- no public partner signup, commissions, referral tracking, white-label, public partner API, directory, or certification is introduced
- rollout gates are documented for founding partner, broad partner scale, white-label, and public APIs
- `CODEX_WORKING_CONTEXT.md` has compact completion status

Do not accept a docs-only outcome unless the implementation session proves code changes are blocked or intentionally deferred. The desired direction is mature platform capability, not only planning collateral.

---

## Technical Handover

### Session Startup Checklist

- Run `git status --short` and identify unrelated dirty files before editing.
- Read working context, strategic context, 001 completion, 002 completion, 003 completion, and required docs above.
- Search before changing so partner artifacts reuse current launch packet, support runbook, verification, billing, and storefront surface truth.
- Keep Launch Truth, Storefront Product Shell, and Starter Launch Package decisions intact.
- Stage only files touched for Partner Enablement Foundation.
- Keep chat updates short and put compact implementation state in `CODEX_WORKING_CONTEXT.md`.

Suggested first search:

```bash
rg -n "partner|Partner|implementation partner|affiliate|referral|commission|intelligence catalog|launch packet|support runbook|verification pack|design partner|Knowledge Sync|Free: AI search only|Starter remains read-only|order lookup|order-lookup|surfacePlacements|enabledSurfaces|usage-summary|App Review|screencast" \
  doc/Productization/future-work/MarketPlace/Products/Strategy \
  Final_Documentation \
  product-services/shopify-bridge-service \
  Platfrom
```

### Architecture To Preserve

- Shopify Companion remains the anchor/reference vertical.
- Partner enablement mirrors the verified Starter truth.
- Partner surfaces are implementation support surfaces, not merchant sales pages.
- Merchant admin must remain merchant-safe.
- Operator surfaces may retain diagnostics and internal language.
- Partner surfaces may show setup, verification, evidence, and bounded support context, but not raw platform internals.
- Platform/Shopify bridge remains the source for live readiness, billing, support, usage, and verification evidence.
- Generated packets should come from shared logic where possible, not duplicated static copy that can drift.

### Documentation Targets

Docs are still required because partners need durable operating material. They are not a substitute for the mature platform surface defined above.

Create or update:

- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_IMPLEMENTATION_PARTNER_ENABLEMENT_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_PARTNER_VERIFICATION_PACK_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PARTNER_DASHBOARD_STRATEGY_PLAN.md`

If adding generated partner packet logic, start from the existing Shopify merchant UI/export logic:

- `product-services/shopify-bridge-service/ui/src/App.tsx`
- `product-services/shopify-bridge-service/ui/src/api.ts`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyMerchantController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyBridgeAdminController.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeMerchantStoreService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeSupportReadinessService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/analytics/service/ShopifyBridgeUsageService.java`

If adding partner workspace or platform/operator retrieval surfaces, inspect first:

- `Platfrom/ui/src/App.tsx`
- `Platfrom/ui/src/pages/ProductServicesPage.tsx`
- `Platfrom/ui/src/pages/ShopifyStoresPage.tsx`
- `Platfrom/ui/src/api.ts`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/productservice/web/ProductServiceController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/web/ShopifyAdminController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/PlatformVerificationSuiteController.java`

Introduce a backend partner domain when implementing Slice A or later slices. Keep it private, scoped, audited, and testable. Avoid public partner signup, public partner APIs, and broad auth churn until founding implementation partners prove the workflow.

---

## Partner Kit Content Requirements

### Intelligence Catalog Entry Template

Each surface should include:

- surface name
- target shopper problem
- included tier
- storefront placement
- required source data
- required merchant setup
- verification steps
- healthy result
- failure signs
- known limitations
- launch-safe claim
- escalation evidence to capture

### Deployment Checklist

Must cover:

- client store identified
- Shopify Companion installed
- plan/tier confirmed
- theme app embed enabled
- target blocks placed
- Knowledge Sync healthy
- required source categories reachable
- storefront surfaces visible
- Max Mode/depth layer opens from embedded surfaces
- analytics/value signals available after use
- support handoff profile configured
- screenshots/evidence captured

### Verification Pack

Must cover:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- read-only depth layer
- billing/tier gates
- Free AI-search-only gate
- Starter no-order-lookup gate
- Elite-only order lookup, if present and verified for that store
- Knowledge Sync source readiness
- usage/value evidence

### Escalation Template

Must capture:

- partner name
- partner owner
- client/store
- plan
- enabled surfaces
- blocker category
- impact
- reproduction steps
- screenshots or video links
- verifier output or manual checks
- latest changed/deployed version when known
- next action owner
- due date
- resolution notes

---

## Minimum Slice Acceptance Criteria

Use this only for partial slice completion. The full handoff is complete only when the Mature Platform Acceptance Criteria above are met.

Minimum acceptable partial slice:

- implementation partner positioning is explicit and does not read like affiliate/referral copy
- partner domain and access assumptions are recorded
- intelligence catalog covers verified Shopify Companion Starter surfaces
- each catalog entry has tier, source, setup, verification, limitations, and claim-safe copy
- deployment checklist is complete enough for a partner to follow without a live walkthrough
- verification pack can prove Free AI-search-only and Starter no-order-lookup boundaries
- escalation template captures owner, status, next action, and evidence
- at least 3 vertical playbooks exist
- merchant admin remains merchant-safe and not cluttered with partner/operator long-form content
- no public partner signup, commissions, white-label, public partner API, or certification is introduced
- `CODEX_WORKING_CONTEXT.md` has compact slice status and next handoff

---

## Verification

Always run:

```bash
git diff --check
```

If a slice touches only docs:

```bash
rg -n "affiliate|referral|commission|white-label|partner API|public partner signup|order lookup.*Starter|Starter.*order lookup|Growth|Pro" \
  doc/Productization/future-work/MarketPlace/Products/Strategy \
  Final_Documentation/User_Guides \
  Final_Documentation/Development_Guides
```

Use search results to fix current-scope leaks or explicitly mark historical/deferred content.

If Shopify merchant UI/export code changes:

```bash
npm --prefix product-services/shopify-bridge-service/ui run build
bash -n scripts/verify-shopify-companion.sh
mvn -f product-services/shopify-bridge-service/pom.xml -q \
  -Dtest=ShopifyMerchantControllerTest,ShopifyBridgeAdminControllerTest,ShopifyBridgeSupportReadinessServiceTest,ShopifyBridgeUsageServiceTest,ShopifyBridgeMerchantStoreServiceTest \
  test
```

If Platform UI changes:

```bash
npm --prefix Platfrom/ui run build
```

If Platform backend changes:

```bash
mvn -f Platfrom/backend/pom.xml -q test
```

If live deployment or verifier behavior changes:

```bash
scripts/verify-shopify-companion.sh
```

For live bridge admin checks:

- `SHOPIFY_BRIDGE_ADMIN_API_KEY` must match the deployed `SHOPIFY_BRIDGE_SHARED_SECRET`.
- Do not print, paste, commit, or log the secret.
- Use secret files or environment variables only.

---

## Completion Section For Implementing LLM

Append a compact completion update here before ending the implementation session.

Required completion fields:

- implementation summary
- changed files
- decisions made
- tests/builds run
- live verification status
- pushed commit refs, if pushed
- blockers or no pending handoff items

Do not include secrets, long logs, or raw diffs.
