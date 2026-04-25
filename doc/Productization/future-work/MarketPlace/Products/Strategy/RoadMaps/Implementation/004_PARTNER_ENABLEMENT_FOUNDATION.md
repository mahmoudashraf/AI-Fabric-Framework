# Partner Enablement Foundation

Status: implementation handoff (2026-04-25)

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

The first implementation milestone is a founding partner enablement kit and repeatable verification flow, not a full partner portal. The goal is to make one serious implementation partner able to understand what can be deployed, set it up on a test/client store, verify it, and escalate with useful evidence without needing a live walkthrough every time.

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

- implement the first Partner Enablement Foundation slice for founding implementation partners

Primary outcome:

- one founding implementation partner can understand the Shopify Companion Starter package, deploy the verified intelligence surfaces to a client/test store, verify each surface, and escalate issues with evidence without live platform-operator explanation

This handoff should first produce a partner enablement kit. It may be documentation-only or a lightweight UI/export pass if the existing platform/Shopify admin surfaces make that cheap. Do not start a large partner portal or identity model unless the implementation session finds a small existing seam that can be safely reused.

Required kit contents:

- partner positioning and boundaries
- demo/sandbox store brief
- intelligence-piece catalog
- per-surface setup requirements
- per-surface verification checks
- deployment checklist
- launch verification pack
- support escalation template
- implementation playbooks for 2-3 merchant verticals
- partner-safe launch/support packet reuse guidance
- private founding-partner operating flow

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
- build partner API
- build broad custom product assembly
- expose secrets, tokens, provider credentials, deployment internals, raw vectorization controls, runtime controls, or queue/replay internals to partners
- push partner/operator packet content back into the merchant Shopify admin as long inline text
- start UI redesign as a prerequisite
- start WooCommerce or second-product work
- loosen Free/Starter/Elite tier truth
- add order lookup to Free or Starter

---

## Build Order

### Step 0: Merchant Boundary Audit

Close:

- confirm merchant admin remains focused on setup, surfaces, Knowledge Sync, billing, support handoff, usage/value, and blockers
- confirm long partner/operator packet text is not rendered inline for merchants
- confirm partner-only enablement language does not leak into shopper surfaces

Exit:

- merchant admin remains merchant-safe while partner kit work proceeds

### Step 1: Founding Partner Kit

Close:

- partner positioning
- non-goals and boundaries
- partner operating flow
- demo/sandbox store brief
- partner agreement/scope notes as a practical checklist, not legal boilerplate
- private partner communication channel recommendation

Exit:

- a founding partner understands the relationship, scope, and what they are allowed to promise

### Step 2: Intelligence Catalog

Close:

- catalog entry for each verified Shopify Companion surface
- tier availability
- required Shopify source data
- required Shopify scopes or merchant actions
- placement instructions
- setup blockers
- verification checks
- known limitations
- launch-safe claim text

Exit:

- a partner can choose a surface, know whether the client store is ready, and know how to verify it

### Step 3: Deployment And Verification Pack

Close:

- install checklist
- theme app embed/block placement checklist
- Knowledge Sync readiness checklist
- billing/tier verification checklist
- storefront surface verification checklist
- Max Mode/depth-layer handoff verification
- analytics/value proof checklist
- App Store/App Review material reuse guidance where relevant
- evidence capture template

Exit:

- partner can perform and document a repeatable setup without reconstructing steps from code or chat

### Step 4: Support Escalation Template

Close:

- escalation title
- client/store identity
- current plan
- enabled surfaces
- blocker category
- reproduction steps
- expected vs actual behavior
- verification already run
- screenshots/evidence links
- owner
- status
- next action
- due date
- resolution notes

Exit:

- support escalations arrive with enough context for the platform builder/operator to act

### Step 5: Vertical Playbooks

Close:

- 2-3 vertical playbooks using the verified Starter package
- recommended surfaces by vertical
- source readiness needs
- screenshot/demo targets
- client-facing value story
- common blockers
- support handoff posture

Exit:

- partner has repeatable starting points for the first client conversations

### Step 6: Optional Lightweight Surface

Only if low risk and aligned with existing code:

- add a partner-kit export/download section to an operator/admin support area
- add a static partner enablement docs page
- add compact copy/download buttons for partner catalog, verification pack, and escalation template

Do not add a full partner portal in this handoff unless explicitly chosen after discovery.

Exit:

- partner enablement artifacts are easy to retrieve without cluttering merchant admin

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

### Likely Documentation Targets

Prefer creating or updating docs before building new UI:

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

If adding a lightweight platform/operator retrieval surface, inspect first:

- `Platfrom/ui/src/App.tsx`
- `Platfrom/ui/src/pages/ProductServicesPage.tsx`
- `Platfrom/ui/src/pages/ShopifyStoresPage.tsx`
- `Platfrom/ui/src/api.ts`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/productservice/web/ProductServiceController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/web/ShopifyAdminController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/PlatformVerificationSuiteController.java`

Do not introduce a new backend partner domain unless the implementation session deliberately scopes and tests it. For this handoff, docs plus reusable exports are preferred over schema/auth churn.

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

## Acceptance Criteria

This handoff is complete when:

- founding partner positioning is explicit and does not read like affiliate/referral copy
- partner kit exists in durable docs and/or lightweight export surface
- intelligence catalog covers the verified Shopify Companion Starter surfaces
- each catalog entry has tier, source, setup, verification, limitations, and claim-safe copy
- deployment checklist is complete enough for a partner to follow without a live walkthrough
- verification pack can prove Free AI-search-only and Starter no-order-lookup boundaries
- escalation template captures owner, status, next action, and evidence
- 2-3 vertical playbooks exist
- merchant admin remains merchant-safe and not cluttered with partner/operator long-form content
- no public partner signup, commissions, white-label, partner API, or certification is introduced
- `CODEX_WORKING_CONTEXT.md` has compact completion status

---

## Verification

Always run:

```bash
git diff --check
```

If docs only:

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
