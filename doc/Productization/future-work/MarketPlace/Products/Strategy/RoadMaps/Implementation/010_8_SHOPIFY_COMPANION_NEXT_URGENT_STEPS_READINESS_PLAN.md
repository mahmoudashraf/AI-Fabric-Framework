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
- [010.7 Runtime Query Once Endpoint Contract Plan](010_7_RUNTIME_QUERY_ONCE_ENDPOINT_CONTRACT_PLAN.md)

## Purpose

This plan compresses the current Shopify Companion readiness state into the next urgent execution sequence.

The goal is not to reopen product strategy. The goal is to decide what must happen next so Loom Companion can be released safely with the product we have now, while keeping public App Store and self-service production claims blocked until their evidence exists.

## Current Release Posture

Controlled design-partner/private launch: conditionally allowed after a fresh hosted staging release gate passes on the current deployed branch.

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
- App Store/private listing, support, pricing, and merchant onboarding are finalized
- production support runbook and incident response are ready

## Immediate Recommendation

Release posture should be:

1. Ship a controlled design-partner/private Shopify Companion launch after the fresh staging release gate and answer-quality repeat gate pass.
2. Keep public App Store/self-service production launch blocked until production promotion, rollback, support packaging, and customer-owned capability proof are complete.
3. Treat Customer Account MCP and Checkout MCP as gated beta capabilities, not headline launch promises.
4. Use the debug/RAG inspector and answer-quality repeat gate as mandatory evidence for every Shopify deploy that affects storefront chat, indexing, actions, prompts, Bridge response shape, or widget behavior.
