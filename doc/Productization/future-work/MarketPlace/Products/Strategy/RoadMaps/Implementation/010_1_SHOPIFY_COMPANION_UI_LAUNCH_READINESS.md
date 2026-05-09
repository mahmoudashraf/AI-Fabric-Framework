# 010.1 Shopify Companion UI Launch Readiness

Status: implementation in progress (created 2026-05-09; local UI/build verification passed, staging deployment/live gate pending)

Owner mode: product UI / GTM readiness / launch operations LLM session

Roadmap phase: `010.1` - turn the Plan 010 launch kit into product UI surfaces for merchants, partners, and operators.

Priority: P0 follow-on to the first Plan 010 staging-verified slice. This plan does not replace 010. It closes the UI readiness gap discovered after staging proved the merchant approval, evidence, and rollback request flow.

Parent plan:

- [010 GTM And Partner Portal Launch Readiness](010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md)

Reference plans:

- [009.3 Shopify MCP Market Readiness And Release Gate](009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md)
- [009.4 Loom Companion Launch Readiness And Design-Partner Package - Merged Into 010](009_4_LOOM_COMPANION_LAUNCH_READINESS_AND_DESIGN_PARTNER_PACKAGE_MERGED_INTO_010.md)
- [004 Partner Enablement Foundation](004_PARTNER_ENABLEMENT_FOUNDATION.md)
- [Partner Enablement UI Design](004_PARTNER_ENABLEMENT_UI_DESIGN.md)

Related code:

- `Platfrom/partner-ui`
- `Platfrom/ui`
- `product-services/shopify-bridge-service/ui`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge`

---

## Executive Decision

The launch kit must be implemented primarily in UI, not only in markdown.

Docs remain useful as canonical copy, review checklists, and handoff material, but a merchant or partner should not need to read internal documentation to launch Loom Companion.

The product UI must own:

- onboarding
- pricing/package explanation
- support path
- design-partner intake and package
- App Store/private listing readiness
- launch evidence
- release gate status
- production-promotion readiness
- rollback/deactivation request path

This plan keeps the merchant-facing product language focused on Loom Companion for Shopify. Do not expose MCP, Coolify, Hetzner, provider handles, secrets, runtime internals, deployment internals, or AI Fabric platform architecture in merchant or partner launch surfaces.

---

## Required Next Priority Order

The next 010/010.1 priority order is:

1. Run the hosted/full release gate on staging after the latest deployment.
2. Do a controlled production-promotion proof:
   - actual Go production mutation through `dtp-coolify-production`
   - production provisioning verification
   - rollback/deactivation proof
   - failed promotion proves staging remains untouched
3. Package merchant-facing launch material:
   - onboarding/pricing/support copy
   - design-partner package
   - App Store/private listing readiness
4. Decide launch posture:
   - design-partner launch now
   - public App Store launch later after production proof and support packaging

The next engineering step after this plan is:

```text
run full release gate, then schedule and execute the controlled production promotion proof
```

Production mutation must remain behind an intentional operator-controlled proof gate until the owner explicitly chooses the proof window.

---

## UI Ownership Map

### Merchant Shopify Admin UI

The embedded Shopify admin UI must expose:

- onboarding checklist
- current package/tier and trial status
- what Loom Companion can access
- what partner/operator access means
- approval, denial, and revocation controls
- support request and rollback/deactivation request
- staging readiness
- production readiness
- evidence bundle/export links
- safe limitations and blocked-capability remediation

Merchant UI must not expose:

- Coolify
- Hetzner
- provider profile ids
- secret names or secret values
- deployment resource handles
- MCP server/tool implementation details
- raw runtime or gateway diagnostics

### Partner UI

The Partner UI must expose:

- design-partner intake
- implementation request status
- package recommendation and package explanation
- merchant approval invite
- onboarding checklist for assigned stores
- launch readiness
- production promotion readiness/request
- evidence bundle/export
- support/escalation tracking
- rollback/deactivation coordination
- weekly value-review prompts and outcomes

Partner UI may show implementation-level diagnostics only when they are partner-safe and do not reveal provider or secret internals.

### Platform/Admin UI

The Platform/Admin UI must expose:

- pricing/package definition review
- package/profile/template mapping
- App Store/private listing readiness checklist
- protected customer data posture
- Customer Account MCP and Checkout MCP external gate status
- production-promotion proof status
- release-gate evidence
- operator diagnostics
- provider-level failure details
- manual rollback/deactivation execution

Provider internals belong here, not in merchant or partner UI.

### Docs

Docs are backing material, not the primary launch interface.

Docs should store:

- canonical pricing/package copy
- design-partner offer copy
- App Store listing draft
- privacy/data-access/support language
- internal release checklist
- operator handoff

---

## Required UI Launch Kit Surfaces

### 1. Onboarding / Pricing / Support

Implement in merchant and partner UI:

- package cards for Free, Starter, and Elite
- clear feature and limitation rows
- support expectations per package
- trial/design-partner terms
- upgrade/downgrade state
- protected-data and checkout capability gates
- support request CTA
- revoke/pause access CTA

Acceptance:

- merchant can understand current package and next setup step without reading docs
- partner can explain package posture from UI only
- blocked gated features show a remediation path instead of silent omission

### 2. Design-Partner Package

Implement in Partner UI and Platform/Admin UI:

- design-partner intake form/status
- target merchant profile
- offer/timeline
- what merchant provides
- what LoomAI provides
- success metrics
- feedback cadence
- exit/continue terms
- cohort status

Acceptance:

- a partner/operator can move a merchant from intake to active design-partner status from UI
- merchant-facing copy remains about outcomes, not platform architecture
- cohort state can support 5-10 qualified stores and weekly value review

### 3. App Store / Private Listing Readiness

Implement in Platform/Admin UI first, with merchant-safe excerpts where relevant:

- app name and listing status
- short and long description draft status
- screenshot checklist
- demo store URL
- support contact status
- privacy/data-use explanation status
- protected customer data status
- Customer Account MCP gate status
- Checkout MCP gate status
- production proof status
- release-gate status
- known blockers before public listing

Acceptance:

- operator can see whether Loom Companion is private-listing-ready or public-App-Store-ready
- public launch remains blocked until production proof, support packaging, and protected-data claims are evidence-backed

---

## Implementation Slices

### Slice 0 - UI Current-State Audit

Before coding, map current surfaces:

- Merchant Shopify Admin UI onboarding/package/support/readiness surfaces
- Partner UI launch/design-partner/package/evidence/support surfaces
- Platform/Admin UI package/listing/release-gate/operator surfaces
- API gaps needed by those screens

Deliverable:

- bounded gap list split by Merchant UI, Partner UI, Platform/Admin UI, and backend API

#### Slice 0 audit result - 2026-05-09

Merchant Shopify Admin UI already had the strongest launch-kit base:

- onboarding, source setup, storefront activation, billing/tier posture, support profile, partner access, Go live, and support-tool tabs
- generated launch dossier, App Store listing package, design-partner rollout packet, App Review guide, screencast script, support runbook, support bundle, and lifecycle/subscription packet
- gap found: launch package copy was mostly in support tools/exports, so the Go live tab needed explicit onboarding/pricing/support/design-partner/rollback framing

Partner UI already had:

- implementation request intake
- merchant invite/deep-link approval state
- assigned-store workspace
- launch readiness
- production-promotion request
- evidence bundle export
- verification, support, notes, Thinker, and package-trial controls
- gap found: partner dashboard/store workspace did not explicitly show the design-partner package, package explanation, weekly value review prompts, or App Store/private listing posture

Platform/Admin UI already had:

- Shopify package profiles
- Shopify stores
- Shopify readiness audit
- verification ops and release-gate state
- product-service operations
- gap found: there was no single operator page for App Store/private listing readiness, protected-data gates, Customer Account MCP / Checkout MCP gate status, controlled production proof, release-gate evidence, and 010_SELF_SERVICE_PRODUCTION_READY blockers

Backend/API gap result:

- no new backend API was needed for this slice
- existing Partner, Platform verification, Shopify package profile, Shopify readiness audit, and Bridge merchant session APIs carried the required data

### Slice 1 - Merchant Launch Kit UI

Build or harden the Shopify Admin UI surfaces for:

- package/tier explanation
- onboarding checklist
- access explanation
- support and rollback/deactivation request
- evidence and readiness links

### Slice 2 - Partner Design-Partner And Package UI

Build or harden Partner UI surfaces for:

- design-partner intake/status
- package recommendation/explanation
- merchant invite and launch status
- weekly value-review prompts
- evidence and support tracking

### Slice 3 - Platform/Admin Listing Readiness UI

Build Platform/Admin UI surfaces for:

- App Store/private listing readiness
- protected-data and external Shopify gate status
- release-gate and production-promotion proof status
- operator-only diagnostics and remediation

### Slice 4 - Controlled Production Proof UI/Evidence

Expose production proof state without running production mutation by default:

- production proof required
- proof scheduled
- proof running
- proof passed
- proof failed
- staging untouched proof
- rollback/deactivation proof

The mutation itself must require an intentional operator-controlled gate.

### 2026-05-09 implementation record

Implemented code surfaces:

- Merchant Shopify Admin UI: added an explicit Go live `Launch package` card covering onboarding, package/tier posture, support path, evidence, design-partner posture, App Store/private listing claim safety, controlled production proof, and rollback/deactivation guidance.
- Partner UI: added a dashboard launch-readiness kit and store workspace launch package with Free/Starter/Elite explanation, design-partner package terms, weekly value review prompts, App Store/private listing posture, and production proof gating language.
- Platform/Admin UI: added `/shopify-launch-readiness` and navigation entry `Shopify Launch` with App Store/private listing readiness, protected-data gates, Customer Account MCP gate status, Checkout MCP gate status, controlled production proof state, release-gate evidence, recent verification runs, and 010_SELF_SERVICE_PRODUCTION_READY blockers.
- Live verifier: extended `scripts/verify-partner-enablement-live.sh` so deployed Partner UI assets must include the 010.1 launch surfaces and deployed Platform UI assets must include the Shopify launch-readiness route/surfaces.
- Docs: updated the launch/review/support exports guide so the UI ownership and launch safety rules match the implemented surfaces.

Local verification passed:

- `npm --prefix Platfrom/partner-ui run build`
- `npm --prefix Platfrom/partner-ui run smoke`
- `npm --prefix Platfrom/ui run build`
- `npm --prefix product-services/shopify-bridge-service/ui run build`
- `bash -n scripts/verify-partner-enablement-live.sh`
- `git diff --check`

Release-gate state after this implementation:

- `010_1_UI_READY`: locally verified; staging deployment/live asset proof pending.
- `010_SELF_SERVICE_PRODUCTION_READY`: still intentionally blocked until hosted/full staging release gate, controlled production-promotion proof, production provisioning verification, rollback/deactivation proof, and failed-promotion staging-isolation proof are recorded.

---

## Verification Gates

`010_1_UI_READY` passes only when:

- Merchant Shopify Admin UI can explain onboarding, package, support, evidence, readiness, and access controls without docs
- Partner UI can handle design-partner intake, package explanation, launch status, evidence, support, and value-review surfaces
- Platform/Admin UI can show App Store/private listing readiness, protected-data gates, release-gate status, production proof status, and operator diagnostics
- no merchant/partner UI exposes provider internals, secrets, or deployment resource handles
- UI builds pass
- targeted backend tests pass for any API additions
- live staging browser/API proof passes after deployment

`010_SELF_SERVICE_PRODUCTION_READY` remains blocked until:

- hosted/full staging release gate passes after the latest deployment
- controlled production-promotion proof is run intentionally
- production provisioning verification passes
- rollback/deactivation proof is recorded
- failed production-promotion proof confirms staging remains untouched

---

## Verification Commands

Expected local commands, adjusted to touched code:

```bash
npm --prefix Platfrom/partner-ui run build
npm --prefix Platfrom/partner-ui run smoke
npm --prefix Platfrom/ui run build
npm --prefix product-services/shopify-bridge-service/ui run build
mvn -f Platfrom/backend/pom.xml -q -Dtest=PartnerEnablementIntegrationTest test
mvn -f Platfrom/backend/pom.xml -q test
mvn -f product-services/shopify-bridge-service/pom.xml -q test
bash -n scripts/verify-partner-enablement-live.sh
bash scripts/verify-partner-enablement-live.sh
```

Live production mutation proof must not run unless explicitly intended:

```bash
PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true bash scripts/verify-partner-enablement-live.sh
```

---

## Non-Goals

- Do not add a new platform layer.
- Do not make docs the primary merchant launch surface.
- Do not expose MCP, Coolify, Hetzner, secret names, provider handles, or deployment internals in merchant/partner UI.
- Do not run production mutation as part of normal staging verification.
- Do not market broad public App Store readiness until production proof and support packaging are evidence-backed.
