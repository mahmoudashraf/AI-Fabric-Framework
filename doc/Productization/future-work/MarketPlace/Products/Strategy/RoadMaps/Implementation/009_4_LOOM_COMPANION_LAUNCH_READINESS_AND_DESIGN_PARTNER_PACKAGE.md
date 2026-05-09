# 009.4 Loom Companion Launch Readiness And Design-Partner Package

Status: implementation roadmap (created 2026-05-09)

Owner mode: product launch / platform operations LLM session

Roadmap phase: `009.4` - turn the 009 MCP-first implementation into a merchant-ready Loom Companion launch package.

Priority: P0 after `009.3` staging release gate. This is the next execution focus before more MCP infrastructure, new commerce verticals, or broad App Store launch.

Parent plans:

- [009 Shopify MCP-First Implementation Sequence](009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md)
- [009.1 Marketplace Config-Driven MCP Capability Architecture](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md)
- [009.2 MCP Execution Gateway Extraction Plan](009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md)
- [009.3 Shopify MCP Market Readiness And Release Gate](009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md)
- [008 Controlled Design-Partner Launch And Market Proof](008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md)

Related references:

- [Shopify Products Shipping Roadmap](../../SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md)
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_MCP_FIRST_AND_GATEWAY_DEVELOPMENT_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`

---

## Executive Decision

009 is technically release-gated on staging for the claim-safe Loom Companion product boundary.

009.4 must stop expanding the platform surface and package the product for real merchant use.

Sell now:

```text
Loom Companion for Shopify
AI shopping assistant for storefront product discovery, product detail help, policy answers, and governed commerce evidence.
```

Do not lead with:

- MCP marketplace
- Coolify/Hetzner infrastructure
- generic AI Fabric platform
- checkout automation
- broad autonomous customer-account actions

Those are technical advantages and future platform story. The merchant-facing first offer is the Shopify shopping companion.

---

## Accepted Starting State

Treat these as accepted from 009.3:

- Storefront MCP path is live-verified through Bridge -> MCP Gateway -> Shopify Storefront MCP.
- `shopify_search_catalog` is release-gated as normalized `MCP_TOOL_RESULT` evidence.
- Customer Account MCP read-only order-status boundary has staging bound-token proof.
- Checkout MCP credentials, agentic token exchange, UCP profile, direct `tools/call`, and `Shopify-Buyer-IP` semantics are verified.
- Managed Checkout MCP public proof is accepted only with a staging development-store limitation: the dev store still redirects `/api/ucp/mcp` to `/password`, and Bridge/Gateway intentionally do not use storefront password cookies.
- Full Platform release gate passed after 009.3 hardening.
- Production was not deployed or promoted by 009.3.

Claim-safe Checkout wording:

```text
Checkout MCP implementation is prepared and credential/header/profile verified on staging. Managed public Checkout MCP proof remains gated by Shopify dev-store password protection and must not be marketed as live checkout automation.
```

---

## Product Boundary For 009.4

Launch package name:

```text
Loom Companion for Shopify
```

Design-partner package:

```text
Loom Companion Starter Design Partner
```

Primary merchant outcome:

- shoppers can find products faster
- shoppers can ask policy and product-detail questions
- merchants get a controlled AI assistant with visible evidence and support paths
- setup is guided and reversible

Initial public-safe product surface:

- storefront chat / assistant widget
- product search and product detail help
- policy and FAQ answers
- package-aware Free / Starter / Elite posture in admin surfaces
- read-only Customer Account order-status proof only where customer auth is configured
- Bridge / Gateway / Platform release evidence for support and operations

Do not market yet:

- autonomous checkout
- terminal checkout completion
- refunds/returns automation
- broad protected-customer-data automation
- generic MCP server marketplace for merchants

---

## 009.4 Goals

1. Convert the technical release-gated state into a merchant-facing offer.
2. Make onboarding repeatable without founder-only manual intervention.
3. Create design-partner operating material and support paths.
4. Preserve strict claim hygiene around Customer Account and Checkout MCP.
5. Prepare a later non-dev Shopify store proof path for Checkout MCP without blocking design-partner launch.
6. Keep staging as the active environment unless production promotion is explicitly approved.

---

## Non-Goals

- Do not implement new MCP transports or generic MCP marketplace expansion.
- Do not add another product-service architecture layer.
- Do not deploy production by default.
- Do not claim Shopify App Store public readiness.
- Do not bypass Shopify storefront password protection with cookies or shared browser sessions.
- Do not introduce checkout terminal operations unless a separate gated test plan explicitly enables them.

---

## Workstream 1 - Merchant Offer And Packaging

Goal:

- make the product understandable in one sales conversation.

Deliverables:

- one-sentence positioning
- package comparison for Free / Starter / Elite
- design-partner offer page or PDF
- merchant FAQ
- install expectations
- data usage and privacy explanation
- support SLA for design partners
- clear exclusions for checkout, returns, refunds, and protected-data automation

Required copy:

- headline: `AI shopping assistant for Shopify stores`
- value props:
  - product discovery
  - product detail answers
  - policy answers
  - guided commerce with governed evidence
  - controlled setup and support
- technical proof hidden under "How it works", not front-page positioning

Exit gate:

- a merchant can understand the offer, package, expected setup time, and current limitations without reading architecture docs.

---

## Workstream 2 - Onboarding And Activation

Goal:

- make install and activation repeatable for 5-10 design-partner stores.

Deliverables:

- merchant onboarding checklist
- implementation partner checklist
- store preflight checklist
- admin app walkthrough
- theme app embed activation steps
- source sync / catalog readiness steps
- rollback/deactivation steps
- support escalation template

Minimum activation flow:

1. store intake form captured
2. package selected
3. product service/store binding verified
4. source preflight passed
5. widget enabled in merchant admin
6. theme app embed enabled
7. storefront bootstrap returns `200`
8. sample product queries pass
9. readiness audit evidence saved
10. merchant signs off on design-partner launch

Exit gate:

- one operator can onboard a new staging/design-partner store from the checklist and produce evidence without direct code edits.

---

## Workstream 3 - Design-Partner Evidence Pack

Goal:

- every design-partner install has a supportable evidence bundle.

Evidence required per store:

- store domain
- package/tier
- product-service ref
- deployment/service URLs used
- source preflight result
- widget/theme activation result
- sample query pack result
- Shopify MCP readiness result
- latest release-gate run id
- known limitations
- rollback steps

Query pack minimum:

- product discovery query
- product detail query
- policy/FAQ query
- out-of-scope query
- no-result query

Exit gate:

- evidence can be sent to a merchant or partner without exposing secrets.

---

## Workstream 4 - Claim Hygiene And Tier Boundaries

Goal:

- keep sales, docs, UI, and support material aligned with what is actually verified.

Rules:

- Storefront MCP is claim-safe.
- Customer Account MCP is claim-safe only for the read-only order-status boundary when customer auth is configured.
- Checkout MCP is implementation-prepared but not public-live-claim-safe until a non-password-protected store proves the managed Bridge/Gateway path.
- Terminal checkout operations are disabled by default.
- Elite must be described as governed assistance, not autonomous commerce.
- Public App Store launch remains pending until app-review packaging, public production routing, support, privacy, and self-serve onboarding are done.

Required audit:

- scan merchant-facing copy for unsupported claims
- scan UI labels and package cards for unsupported claims
- scan support docs for old Railway/public URL drift
- scan App Store draft copy before submission

Exit gate:

- no merchant-facing artifact claims checkout automation, terminal checkout, or broad protected-data automation before separate proof.

---

## Workstream 5 - Operational Readiness

Goal:

- design-partner support runs through Platform-managed operations, not ad hoc provider edits.

Required checks:

- Platform Product Services can reconcile, health-check, view logs/history, restart, scale, rotate secret, force recreate, and decommission the managed Bridge and MCP Gateway.
- Coolify/Hetzner staging guide is current.
- Platform managed-product-services auth guide is current.
- all active staging URLs are documented and stale Railway URLs are not used as source of truth.
- secret names are documented, values are only in private/operator material.
- release gate can be rerun by an operator.

Exit gate:

- an operator can recover Bridge or MCP Gateway staging service through Platform UI/API without manually editing Coolify except for provider-level incidents.

---

## Workstream 6 - Non-Dev Checkout MCP Proof Path

Goal:

- create a clean path to prove Checkout MCP later without blocking Loom Companion design-partner launch.

Options:

1. transfer the current dev store to a merchant account and remove password protection
2. switch the staging store to a paid/trial merchant store where password protection can be disabled
3. create a separate non-dev staging Shopify store for Checkout MCP proof

Required proof after one option is available:

- `https://<store>/api/ucp/mcp` no longer redirects to `/password`
- Gateway direct Checkout action returns a real UCP response through managed service
- Bridge `shopify_get_checkout` returns normalized `MCP_TOOL_RESULT` for a safe non-terminal invalid-id or test checkout case
- terminal operations stay disabled unless separately approved
- evidence is recorded in 009.3 and working context

Exit gate:

- Checkout MCP can move from "prepared and credential/header/profile verified" to "managed public path live-verified" without weakening security posture.

---

## Workstream 7 - App Store / Public Launch Preparation

Goal:

- prepare public launch material, but do not submit until design-partner evidence exists.

Deliverables:

- App Store listing draft
- screenshots and demo video outline
- privacy policy and data-use explanation
- support contact and response process
- install/uninstall behavior checklist
- protected customer data explanation for any customer-account surface
- pricing copy
- onboarding emails
- review test account/runbook

Execution rule:

- verify the latest Shopify app-review and protected-data requirements from official Shopify docs before submission.

Exit gate:

- public submission package is ready for review, but actual submission waits for explicit go/no-go.

---

## Release Gates

### 009.4 Design-Partner Ready

Required:

- full Platform release gate is `READY`
- focused Shopify MCP Gateway verifier passes
- merchant onboarding checklist exists and is dry-run once
- evidence pack template exists
- package/pricing copy is claim-safe
- support and rollback docs exist
- no unsupported Checkout MCP / terminal checkout claims

Decision:

```text
009.4_DESIGN_PARTNER_READY
```

### 009.4 Design-Partner Active

Required:

- at least one real or merchant-equivalent store onboarded through the checklist
- storefront assistant active
- sample query pack passed
- merchant/partner support path tested
- evidence pack recorded

Decision:

```text
009.4_DESIGN_PARTNER_ACTIVE
```

### 009.4 Public Launch Candidate

Required:

- 5-10 design-partner installs completed or scheduled
- repeatable onboarding evidence
- merchant-facing docs and support paths proven
- App Store listing package ready
- production routing decision made
- production release gate passed after explicit production deploy

Decision:

```text
009.4_PUBLIC_LAUNCH_CANDIDATE
```

---

## Verification Commands

Local:

```bash
git diff --check
bash -n scripts/verify-shopify-mcp-gateway.sh
bash -n scripts/verify-shopify-companion.sh
bash -n scripts/verify-shopify-first-product-readiness-audit.sh
```

Staging:

```bash
PLATFORM_BASE_URL="https://loomai-platform-backend.46.224.145.148.sslip.io" \
SHOPIFY_BRIDGE_BASE_URL="https://shopify-bridge-staging.46.224.145.148.sslip.io" \
SHOP_DOMAIN="shopping-companion-test.myshopify.com" \
PLATFORM_API_KEY_FILE="/tmp/platform_staging_admin_api_key.secret" \
MCP_GATEWAY_API_KEY_FILE="/tmp/mcp-gateway-admin-key.secret" \
SHOPIFY_BRIDGE_ADMIN_API_KEY_FILE="/tmp/shopify_bridge_staging_shared_secret.secret" \
bash scripts/verify-shopify-mcp-gateway.sh
```

Platform release gate:

```text
POST /api/verification-suites/full-platform-release-readiness/runs
GET  /api/verification-suites/release-gate
```

---

## Pending Items Checklist

- [ ] Create merchant-facing Loom Companion offer copy.
- [ ] Create package/pricing comparison for Free / Starter / Elite.
- [ ] Create design-partner onboarding checklist.
- [ ] Create implementation partner checklist.
- [ ] Create store evidence pack template.
- [ ] Create support/rollback runbook.
- [ ] Audit merchant-facing copy for unsupported Customer Account / Checkout claims.
- [ ] Dry-run onboarding against the staging store.
- [ ] Record latest release-gate evidence after the dry run.
- [ ] Decide production routing only after explicit go/no-go.
- [ ] Prepare non-dev Shopify store path for final Checkout MCP managed proof.

---

## Final Output Of 009.4

009.4 is complete when Loom Companion is no longer just technically verified, but packaged enough for a design partner to understand, install, test, and support with controlled claims and recorded evidence.

The expected result is not "public App Store launch" by default.

The expected result is:

```text
Loom Companion is design-partner launch ready, with a clear path to public launch after merchant proof.
```
