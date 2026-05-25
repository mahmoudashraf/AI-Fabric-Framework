# 010.8 Shopify Companion Next Urgent Steps Readiness Plan

Status: active release-readiness plan, created 2026-05-23

Parent plans:

- [009 Shopify MCP First Implementation Sequence](009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md)
- [009.3 Shopify MCP Market Readiness And Release Gate](009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md)
- [010 GTM And Partner Portal Launch Readiness](010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md)
- [010.1 Shopify Companion UI Launch Readiness](010_1_SHOPIFY_COMPANION_UI_LAUNCH_READINESS.md)
- [010.2 Shopify Companion Two-Mode Surface Simplification](010_2_SHOPIFY_COMPANION_TWO_MODE_SURFACE_SIMPLIFICATION.md)
- [010.3 Shopify Companion Query Speed, Accuracy, And Reliability Optimization Plan](010_3_SHOPIFY_COMPANION_QUERY_SPEED_ACCURACY_RELIABILITY_OPTIMIZATION_PLAN.md)
- [010.4 Shopify Companion Indexing Architecture Cleanup Plan](010_4_SHOPIFY_COMPANION_INDEXING_ARCHITECTURE_CLEANUP_PLAN.md)
- [010.5 LoomAI Canonical Runtime Bridge Contract Standardization Plan](010_5_LOOMAI_CANONICAL_RUNTIME_BRIDGE_CONTRACT_STANDARDIZATION_PLAN.md)
- [010.6 Private Runtime Asymmetric Assertion Auth Plan](010_6_PRIVATE_RUNTIME_ASYMMETRIC_ASSERTION_AUTH_PLAN.md)
- [010.7 Runtime Query Once Endpoint Contract Plan](010_7_RUNTIME_QUERY_ONCE_ENDPOINT_CONTRACT_PLAN.md)

## Purpose

This plan compresses the current Shopify Companion readiness state into the next urgent execution sequence.

The goal is not to reopen product strategy. The goal is to decide what must happen next so Loom Companion can be released safely with the product we have now, while keeping public App Store and self-service production claims blocked until their evidence exists.

## Current Release Posture

Controlled design-partner/private launch: passed for the current staged branch after the 2026-05-25 hosted release gate. This evidence remains valid only inside the gate freshness window recorded below.

Public self-service Shopify App Store launch: not ready.

Production self-service launch through Platform-managed promotion: not ready.

Current sellable posture:

- Loom Companion for Shopify as a private/design-partner storefront assistant.
- Shopper-facing product discovery, product guidance, policy answers, comparison help, and governed cart assistance.
- Merchant-facing setup and support through Platform/partner/merchant surfaces.
- Elite capability posture for the staging launch store; Free remains retained but disabled.

Current non-claim posture:

- Do not claim full public self-service onboarding.
- Do not claim broad production promotion readiness.
- Do not claim Customer Account MCP order, return, refund, or store-credit features beyond tools that have fresh live proof.
- Do not claim Checkout MCP automation as live until checkout-specific credentials, storefront access, and managed tools/call proof are recorded.
- Do not sell MCP, Coolify, Hetzner, or AI Fabric internals to merchants.

## Consolidated Next Urgent Steps From Latest Readiness Review

These are the active release-gating points that must stay visible in this plan.

1. Run a fresh hosted/full Shopify release gate on the current deployed branch. The May 8 gate is expired and cannot be used for a new release decision.
2. Execute controlled production-promotion proof through `dtp-coolify-production`: real `Go production` mutation, production verification, rollback/deactivation proof, and a failed-promotion proof that staging stays untouched.
3. Finish Customer Account and Checkout public-claim gates: durable Customer Account OAuth across Bridge redeploy/recreate, successful owned-resource `tools/call` proof, and Checkout MCP proof without storefront password redirects.
4. Fix or re-verify the support-readiness mismatch: Platform package posture must match storefront bootstrap flags, so the storefront never advertises a capability that Platform marks unsupported.
5. Implement durable owned-resource refs/cart-handle persistence if we want reliable "my cart", "my latest order", and "return last order" behavior across turns and redeploys.
6. Complete merchant-facing production packaging: pricing, onboarding, support, private/App Store listing copy, escalation path, and limitations copy.
7. Implement 010.6 asymmetric private-runtime assertions before external-customer production use. This is not the top Shopify blocker, but it is required for ProdUS-style production integrations.

Release status from this review:

- Controlled design-partner launch: current staging gate passed on 2026-05-25 and is viable for a private/design-partner launch while the evidence remains fresh.
- Public self-service Shopify production/App Store launch: not ready.
- Current product architecture is real, but production evidence is missing.
- Do not claim broad public readiness, terminal checkout automation, refund/return automation, or protected customer-data automation until the relevant gates above pass.

## 2026-05-25 Fresh Hosted Staging Release Gate Execution

Run directory: `/tmp/shopify-release-gate-20260525T021738Z`

Branch/commit: `Platform-V9` at `09024adc4` (`Fix Shopify release gate billing expectations`).

The full six-point staging gate was executed against the hosted staging environment. Result: design-partner staging gate passed; public self-service production/App Store remains blocked by production-promotion and public-claim proof gates.

### 1. Staging Deploy Reconciliation

Status: passed.

Evidence:

- Platform backend redeployed through Coolify staging as deployment `jdh3149u7nufk5iy28n986cn` and health returned `UP`.
- Current Shopify staging dependencies returned healthy: Platform backend, Shopify Bridge, Runtime, MCP Gateway, Shopify runtime deployment `dep-8c3e7259`, and vectorization runner `vectorization-runner-dep-8c3e7259`.
- Coolify reconciliation artifact: `/tmp/shopify-release-gate-20260525T021738Z/coolify-staging-deployment-reconciliation.json`.
- Stale non-current runtime deployments on older branches still exist on the host, but the active Shopify staging deployment used by the gate is on `Platform-V9`.

Implementation fix discovered during the hosted gate:

- The hosted release suite hard-coded Shopify Companion expectations as `STARTER/ACTIVE`, which downgraded order-lookup readiness during the suite even though the staging launch store is `ELITE/ACTIVE`.
- Commit `09024adc4` added billing tier/status expectation overrides to the Platform verification-suite dispatch model and script context.
- Local focused tests passed: `mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformVerificationSuiteServiceTest,PlatformVerificationSuiteScriptContextServiceTest,PlatformVerificationSuiteExecutionServiceTest test`.

### 2. Fresh Hosted Full Shopify Release Gate

Status: passed.

Evidence:

- Hosted full suite run: `vsr-e9e4ea6f`.
- Result: `PASSED`, completed `2026-05-25T04:02:44.671947Z`.
- `/api/verification-suites/release-gate` returned `ready=true`, `status=READY`, expiring `2026-05-25T16:02:44.671947Z`.
- Final gate artifact: `/tmp/shopify-release-gate-20260525T021738Z/full-platform-release-gate-final.json`.

Passed stages:

- shared inference health
- platform admin live regression
- canonical rollout inventory
- managed vector provider verification
- Coolify provider verification
- Marketplace install flow
- Shopify Companion verification
- Shopify MCP Gateway verification
- Shopify first-product readiness audit
- Partner Enablement verification
- Thinker Resolver readiness
- Marketplace hosted verification: 42 passes, 2 warnings
- Ecommerce hosted verification: 43 passes, 2 warnings
- Qdrant hosted verification: 25 passes, 2 warnings

Operator remediation during the run:

- The first hosted rerun failed at Partner Enablement because the stored short-lived `PARTNER_SUPABASE_JWT` was expired/stale.
- A fresh Supabase email test-user JWT was generated from private operator material, stored only in `/tmp/partner_supabase_jwt.secret`, and written back to Platform secret `PARTNER_SUPABASE_JWT` without printing token material.
- Standalone strict Partner Enablement verification then passed before the final full hosted gate rerun.

### 3. Storefront Answer-Quality Repeat Gate

Status: passed.

Evidence:

- Repeat gate command: `scripts/verify-shopify-companion-answer-quality-repeats.sh`.
- Repeat count: 3.
- Result: 20/20 passed on each repeat.
- Summary JSON: `/tmp/shopify-release-gate-20260525T021738Z/shopify-answer-quality-20260525T022158Z-repeats/repeat-summary.json`.
- Summary Markdown: `/tmp/shopify-release-gate-20260525T021738Z/shopify-answer-quality-20260525T022158Z-repeats/repeat-summary.md`.
- First-product readiness audit also passed with `Readiness decision: DESIGN_PARTNER_READY`.

### 4. Debug/RAG Inspector Proof

Status: passed.

Evidence:

- Direct API proof for `summarize high performance laptops for gaming` returned `metadata.responseGenerationPath=RAG_ANSWER`.
- Response included `ragResponse.documents` count `5`, `sources` count `5`, and `readActionResolution.finalDecision=EXECUTE_READ_ACTIONS_AND_RAG`.
- Executed read action: `shopify_search_catalog`.
- Source metadata included product image URLs and Shopify identifiers.
- API proof artifact: `/tmp/shopify-release-gate-20260525T021738Z/debug-rag-response.json`.
- Browser widget proof captured a canonical chat response with `hasRagResponse=true`, `ragDocumentCount=5`, `sourceCount=5`, `debugButtonCount=6`, and live Shopify CDN image URLs on rendered cards.
- Browser proof artifact: `/tmp/shopify-release-gate-20260525T021738Z/debug-rag-widget-ui-proof.json`.
- Screenshot: `/tmp/shopify-release-gate-20260525T021738Z/debug-rag-widget-final-proof.png`.

### 5. Support/Package Readiness Mismatch

Status: passed for current staging posture.

Evidence:

- Platform support readiness and Bridge support readiness both reported `READY`.
- Storefront bootstrap reported `billingTier=ELITE`, `billingStatus=ACTIVE`, `orderLookupEnabled=true`.
- Enabled/configured surfaces were aligned: `contextual-pill,product-insight,policy-strip,product-faq,comparison,order-lookup`.
- Allowed conversation modes were aligned: `thinker_deep,executor`.
- Artifacts:
  - `/tmp/shopify-release-gate-20260525T021738Z/platform-support-readiness.json`
  - `/tmp/shopify-release-gate-20260525T021738Z/bridge-support-readiness.json`
  - `/tmp/shopify-release-gate-20260525T021738Z/storefront-bootstrap-proof.json`

### 6. Design-Partner Launch Material And Status

Status: passed for current controlled staging/design-partner launch posture; public packaging remains blocked.

Evidence:

- Partner UI build and smoke passed.
- Strict Partner Enablement live gate passed after JWT refresh, including merchant approval deep-link workspace, product controls, evidence bundle, support-profile write/restore, rollback/deactivation request, and revoked-access proof.
- Hosted full release gate also passed the Partner Enablement stage.
- This plan and the working context now record the exact run IDs, evidence locations, and remaining public-launch blockers.

Remaining release boundaries:

- Controlled staging/design-partner release is supported only while the 2026-05-25 full gate remains fresh or after rerunning it.
- Public self-service Shopify/App Store launch still requires controlled production-promotion proof, production rollback/deactivation proof, failed-promotion staging isolation proof, public Customer Account/Checkout claim proof, durable owned-resource/customer auth posture, and complete public support/App Store packaging.

## What Must Be Done If We Release What We Have Now

This section is the minimum path for a controlled design-partner/private release using the current product.

### 1. Deploy And Reconcile Current Staging

Required:

- Platform backend is deployed on the current branch and commit.
- Shopify Bridge staging is deployed on the same release branch and includes the canonical request/response contract.
- Max Mode widget assets are rebuilt, copied, and served by the Shopify theme extension and Platform static copies.
- MCP Gateway is healthy and reachable.
- Managed vectorization/indexing is healthy for the staging shop.
- Staging shop package posture is `ELITE` and `ACTIVE`.

Do not start merchant-facing demos while any deployed service still points to a stale branch, stale widget bundle, or stale Coolify Git source.

### 2. Run A Fresh Hosted Full Release Gate

The May 8 and May 9 release gates are expired. A release decision needs a current hosted gate.

Required evidence:

- Platform `full-platform-release-readiness` passes.
- `/api/verification-suites/release-gate` reports `READY`.
- Shopify Companion stage passes on the current deployed branch.
- MCP Gateway stage passes on the current deployed branch.
- Partner enablement stage passes or is explicitly scoped out only for a private non-partner launch.
- Any provider connectivity stage uses fresh Qdrant, OpenAI, Supabase, Coolify, and Bridge credentials.

This is a hard gate for design-partner release. It remains a hard gate for public release.

### 3. Run The Storefront Answer-Quality Gate

Required:

- Run the deterministic Shopify first-product readiness audit.
- Run the repeat answer-quality gate with at least three repeats after the latest deploy.
- Manually inspect representative storefront browser behavior, not only API responses.

The release must prove:

- product search answers are grounded in indexed store data or Shopify action evidence
- policy answers use store policy/indexed evidence when available
- comparisons do not expose internal vector, MCP, runtime, provider, or deployment wording
- action confirmations show shopper-safe copy
- failed or missing action evidence produces merchant-safe guidance, not generic success
- RAG documents appear in the context/debug panel when retrieval runs
- debug inspector shows request, normalized request, response, action/RAG diagnostics, and raw payload without secrets

### 4. Verify Canonical Storefront Payload Contract

Required:

- Storefront UI sends `context`, not legacy top-level `storefrontContext`.
- Bridge rejects unsupported legacy request fields where strict mode is enabled.
- Bridge returns canonical top-level shopper fields.
- Bridge returns optional `ragResponse` for debug/context panels when RAG evidence exists.
- Bridge returns optional `debug` only when widget/store config enables debug mode.
- Debug output is redacted, depth-bounded, and safe for merchant/operator diagnostics.

This is urgent because earlier UI debug behavior depended on legacy payload shape.

### 5. Verify Merchant-Facing Setup And Support Surfaces

Required:

- Partner/merchant portal shows the store, package, launch status, evidence, and support state.
- Merchant approval/deep-link flow works in the deployed environment.
- Merchant-facing pages do not expose provider internals, secret names, deployment template IDs, Coolify internals, or MCP wording.
- Support readiness is consistent between Platform support state and Shopify Bridge storefront bootstrap state.
- Storefront bootstrap does not advertise a support/order capability that the backend gate marks unsupported.

### 6. Package Claim-Safe Launch Material

Required before design-partner outreach:

- one-page design-partner package
- merchant onboarding checklist
- pricing/private-package copy
- support and escalation terms
- rollback/deactivation path
- limitations and gated capabilities
- App Store/private listing readiness notes, even if public submission is deferred

Merchant-facing copy must sell Loom Companion for Shopify, not MCP or infrastructure.

### 7. Record Release Evidence

Every release decision needs a short evidence bundle:

- deployed commit SHA
- Coolify deployment IDs for Platform, Bridge, MCP Gateway, and related product services
- hosted release-gate run ID
- answer-quality audit output directory
- repeat answer-quality output directory
- vectorization/reindex run ID
- browser smoke notes for `https://shop-staging.loomai.pro/?country=US`
- known blockers and intentionally deferred items

## Urgent Engineering Items

### P0: Required Before Design-Partner Release

1. Fresh hosted full release gate

Run against the current deployed branch. Expired gate evidence cannot be used for a new release decision.

2. Staging storefront live smoke

Use the real storefront and verify the current widget, canonical request shape, RAG panel, debug inspector, product search, policy answer, comparison, add-to-cart confirmation, and safe unsupported-action behavior.

3. Answer-quality repeat gate

Run the repeat gate after deploy. Require all repeats to pass. Do not treat a single green query run as release proof.

4. Support readiness consistency

Resolve or explicitly gate any mismatch where Platform says a support capability is unavailable but storefront bootstrap exposes it.

5. Indexed data freshness

Confirm staging shop reindex completed and retrieval returns real product/policy evidence. A healthy chat API without indexed evidence is not enough for release.

6. Debug and RAG evidence panel verification

The debug inspector must align with the current canonical payload and must show enough evidence to diagnose RAG/action behavior without leaking secrets.

### P0: Required Before Public Self-Service Or App Store Launch

1. Controlled production-promotion proof

Prove actual `Go production` mutation through `dtp-coolify-production`, production provisioning verification, production verification after provisioning, rollback/deactivation proof, and a failed promotion proving staging remains untouched.

2. Public support packaging

Complete merchant-facing onboarding, support, escalation, refund/support disclaimers, and App Store/private listing material.

3. Customer Account MCP public-claim proof

For each claimed customer-owned feature, record fresh customer OAuth, bound-token `tools/call`, safe response mapping, and failure handling. Do not claim order lookup, returns, store credit, refunds, or customer-owned data features without this proof.

4. Checkout MCP public-claim proof

Prove checkout-specific credentials, storefront access, managed live call path, and safe checkout UX. Keep checkout claims gated until proven.

5. Durable customer auth posture

Persist Customer Account OAuth sessions or explicitly gate customer-owned features as beta/reauthorization-required. This can be deferred for a small private pilot, but not for public launch.

6. Durable owned-resource reference posture

Persist short-lived, scoped owned-resource references such as cart handles, selected order references, and return-intent context when they are needed across turns. This must remain tenant/session scoped, time bounded, and redacted from debug output. Without this proof, "my cart", "my latest order", and "return my last order" must stay gated or re-ask safely for missing context.

7. 010.6 asymmetric private-runtime assertion auth

Implement asymmetric assertion verification before external-customer production integrations rely on direct/private runtime access. HMAC-based staging integrations can remain for current controlled testing, but production external integrations should not depend on shared symmetric assertion secrets.

## Recommended Next Execution Sequence

### Slice A: Staging Deploy Reconciliation

Outcome:

- all staging services run the current branch and expected commit
- widget asset is current on the storefront
- bridge health, platform health, and MCP gateway health pass

Verification:

```bash
bash -n scripts/verify-shopify-companion.sh
bash -n scripts/verify-shopify-companion-max-widget-live.sh
bash -n scripts/verify-shopify-mcp-gateway.sh
```

### Slice B: Fresh Hosted Release Gate

Outcome:

- `full-platform-release-readiness` passes
- `/api/verification-suites/release-gate` returns `READY`

Verification:

```bash
bash scripts/verify-shopify-mcp-gateway.sh
bash scripts/verify-shopify-companion.sh
```

The hosted Platform release suite must also be dispatched and recorded through the Platform verification-suite API/operator workflow.

### Slice C: Storefront Quality And Debug Proof

Outcome:

- answer-quality audit passes
- repeat gate passes
- RAG/debug inspector behavior is browser-verified

Verification:

```bash
SHOPIFY_BRIDGE_BASE_URL=https://shopify-bridge-staging.46.224.145.148.sslip.io \
SHOP_DOMAIN=shopping-companion-test.myshopify.com \
scripts/verify-shopify-companion-answer-quality-repeats.sh
```

Optional broader audit:

```bash
PLATFORM_BASE_URL=<platform-staging-url> \
SHOPIFY_BRIDGE_BASE_URL=https://shopify-bridge-staging.46.224.145.148.sslip.io \
SHOP_DOMAIN=shopping-companion-test.myshopify.com \
scripts/verify-shopify-first-product-readiness-audit.sh
```

### Slice D: Merchant/Partner Readiness Smoke

Outcome:

- merchant launch/admin surfaces are usable
- no dummy success states remain on launch-critical pages
- merchant approval/revocation behavior is current
- support/evidence/rollback surfaces are understandable

Verification:

```bash
npm --prefix Platfrom/partner-ui run build
npm --prefix Platfrom/partner-ui run smoke
bash -n scripts/verify-partner-enablement-live.sh
```

Run the live partner script when staging auth and target data are available:

```bash
bash scripts/verify-partner-enablement-live.sh
```

### Slice E: Private Design-Partner Launch Package

Outcome:

- a merchant can understand what Loom Companion does, what is enabled, what is beta, and how to get support
- launch copy does not claim unproven Customer Account, Checkout, refund, cancel, or order-edit automation
- private install/design-partner package is ready

Artifacts:

- merchant onboarding checklist
- design-partner package summary
- support and escalation terms
- release evidence bundle
- known limitations list

### Slice F: Controlled Production Promotion Proof

Outcome:

- public self-service production gate can move from blocked to evidence-backed

Required proof:

- actual Go production mutation through `dtp-coolify-production`
- production provisioning verification
- production storefront/bridge verification
- rollback/deactivation proof
- failed promotion leaves staging untouched

This slice is not required for a strictly staging/private design-partner pilot, but it is required before public self-service claims.

## Must Not Do During Current Release

- Do not bypass Shopify auth, storefront password, protected customer data, or OAuth requirements.
- Do not turn text matching into action routing logic.
- Do not couple generic runtime logic to Shopify domain semantics.
- Do not expose raw MCP, Coolify, Hetzner, Qdrant, OpenAI, Supabase, deployment, or secret details to merchants or shoppers.
- Do not claim customer-owned order/return/refund/store-credit capabilities unless that exact tool path has fresh live proof.
- Do not treat a successful API health check as storefront release readiness.
- Do not release with stale widget assets even if backend APIs pass.

## Acceptance Gates

### 010.8 Design-Partner Ready

Pass only when:

- current deployed staging branch is reconciled
- hosted full release gate is fresh and green
- answer-quality repeat gate passes after deploy
- storefront browser smoke passes on `shop-staging.loomai.pro`
- indexed data is fresh and retrieval evidence appears when expected
- debug/RAG panels are aligned with canonical payload
- merchant launch/support material is claim-safe
- known unproven features are visibly gated or excluded from copy

### 010.8 Public Self-Service Ready

Pass only when all design-partner gates pass plus:

- production promotion proof is complete
- rollback/deactivation proof is complete
- failed promotion isolation proof is complete
- Customer Account MCP public claims are proven feature-by-feature
- Checkout MCP public claims are proven feature-by-feature
- durable owned-resource refs are implemented or customer-owned flows remain explicitly gated
- 010.6 asymmetric private-runtime assertion auth is implemented for external-customer production integrations
- App Store/private listing, support, pricing, and merchant onboarding are finalized
- production support runbook and incident response are ready

## Immediate Recommendation

Release posture should be:

1. Ship a controlled design-partner/private Shopify Companion launch after the fresh staging release gate and answer-quality repeat gate pass.
2. Keep public App Store/self-service production launch blocked until production promotion, rollback, support packaging, and customer-owned capability proof are complete.
3. Treat Customer Account MCP and Checkout MCP as gated beta capabilities, not headline launch promises.
4. Use the debug/RAG inspector and answer-quality repeat gate as mandatory evidence for every Shopify deploy that affects storefront chat, indexing, actions, prompts, Bridge response shape, or widget behavior.
