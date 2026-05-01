# Thinker Resolver Operator Guide

Status: operator guide for the 006 Thinker/Resolver implementation (2026-05-01)

This guide is for Platform Admin and Platform Operator users running governed issue resolution for Shopify Companion Elite stores and future product services.

Related guides:

- [Thinker Resolver User Guide](./THINKER_RESOLVER_USER_GUIDE.md)
- [Platform Admin User Guide](./PLATFORM_ADMIN_USER_GUIDE.md)
- [Platform Operator User Guide](./PLATFORM_OPERATOR_USER_GUIDE.md)
- [Shopify Companion Merchant Launch And Support Guide](./SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md)
- [Thinker Resolver Developer Guide](../Development_Guides/THINKER_RESOLVER_DEVELOPER_GUIDE.md)

---

## 1) Capability Boundary

Thinker/Resolver is a governed issue-resolution assistant. It does not grant autonomous unrestricted write access.

Current implemented boundaries:

- Thinker deep diagnosis is read-first and records issue sessions, evidence, plans, audit, and export packets.
- Shopify Companion Thinker is Elite-gated.
- Runtime/Thinker owns final answer generation from RAG and eligible read-action evidence.
- Shopify Bridge must pass through runtime evidence and diagnostics; it must not invent semantic fallback answers for successful action evidence.
- Resolver proposals are persisted and policy checked before dry-run.
- Dry-run is non-mutating.
- Governed execution currently supports one low-risk family: `SUPPORT_ESCALATION`.
- Execution requires policy allow, completed dry-run, explicit confirmation text, idempotency key, and per-deployment execution enablement.
- Product action-family kill switches can stop execution without redeploying.
- Storefront chat cannot directly execute support escalation. Escalation execution belongs in Platform or partner-governed flows.

---

## 2) Operator UI

Open Platform UI and use the `Thinker Resolver` navigation item.

Main route:

- `/thinker-resolver`

Use this page to:

- review Thinker readiness across enabled deployments
- enable or disable Thinker per deployment
- enable or disable Resolver preview and governed execution
- configure disabled action families
- inspect Thinker sessions, evidence, plans, and audit trails
- export a session support packet
- create low-risk Resolver proposals
- run dry-run simulation
- execute confirmed support escalations
- inspect policy and execution ledgers

The same records back the operator and partner surfaces. Operators see full evidence, raw references, controls, and execution ledgers. Partners see assigned-store, redacted records only.

---

## 3) Operator API

All operator routes require normal Platform operator/admin authentication.

Core routes:

- `GET /api/operator/thinker/readiness`
- `GET /api/operator/thinker/sessions`
- `POST /api/operator/thinker/sessions`
- `GET /api/operator/thinker/sessions/{sessionId}`
- `GET /api/operator/thinker/sessions/{sessionId}/evidence`
- `GET /api/operator/thinker/sessions/{sessionId}/export`
- `GET /api/operator/thinker/deployments/{deploymentId}/control`
- `PUT /api/operator/thinker/deployments/{deploymentId}/control`
- `GET /api/operator/resolver/proposals`
- `POST /api/operator/resolver/proposals`
- `POST /api/operator/resolver/proposals/{proposalId}/dry-run`
- `POST /api/operator/resolver/proposals/{proposalId}/execute`
- `GET /api/operator/resolver/policy-decisions`
- `GET /api/operator/resolver/executions`

Recommended control posture before first proof:

- `thinkerEnabled=true`
- `resolverPreviewEnabled=true`
- `governedExecutionEnabled=false`
- `disabledActionFamilies=[]`

Enable governed execution only for sandbox/design-partner proof when the low-risk action family is understood and rollback is clear.

The current execution family is intentionally narrow:

- action family: `SUPPORT_ESCALATION`
- action id: `create_support_escalation`
- confirmation text: `CREATE SUPPORT ESCALATION`
- write destination: Partner Enablement support escalation and evidence bundle records
- Shopify store mutation: none

---

## 4) Shopify Merchant Health Surface

The Shopify Bridge merchant session now includes Thinker health from Platform.

Merchant-admin route:

- embedded Shopify admin app, `Partner access` section, `Thinker deep diagnosis` card

The health card shows:

- whether Thinker is enabled
- status and next action
- linked deployment
- recent session count
- blocked session count

The card must show `UNAVAILABLE` if Platform health cannot be reached. It must not pretend that Thinker is ready when the backend check failed.

---

## 5) Safe Operating Procedure

Use this sequence for a store:

1. Confirm Shopify Companion is installed and linked to a Platform deployment.
2. Confirm billing tier is `ELITE` and active.
3. Open `Thinker Resolver` in Platform UI.
4. Select the deployment and enable Thinker.
5. Leave governed execution disabled unless support escalation execution is intentionally being tested.
6. Create or inspect a Thinker session.
7. Confirm every answer has evidence and an explainable plan.
8. For support escalation, create a Resolver proposal, run dry-run, then execute only with confirmation `CREATE SUPPORT ESCALATION`.
9. Export the session packet for support or design-partner proof.

---

## 6) Storefront Widget Checks

Use storefront widget checks to validate answer quality and mode behavior. Do not use storefront chat as the direct write-execution surface.

Recommended queries:

- `Show me products related to student laptops.`
- `Need to see more details about high performance laptops for gaming.`
- `Compare AtlasBook 14 Laptop, Harbor Student 15 Laptop, and Aurora 2-in-1 14 Laptop based on the product details you have.`
- `Compare available snowboards under $800 and explain what evidence is missing before claiming one is safest.`
- `Create support escalation for this unresolved shopper issue.`

Expected proof:

- product questions return RAG and read-action grounded answers
- vague prompts with attachments keep the attached products in context
- selected widget mode is visible in runtime metadata when debug tooling is enabled
- support escalation requests are diagnosed or handed off, not executed directly from chat
- generic widget errors are investigated from the bridge/platform HTTP response instead of hidden with canned fallback text

---

## 7) Incident Controls

Immediate disable options:

- set `thinkerEnabled=false` to stop new Thinker issue sessions
- set `resolverPreviewEnabled=false` to stop new Resolver proposals
- set `governedExecutionEnabled=false` to stop execution
- add `SUPPORT_ESCALATION` to `disabledActionFamilies` to kill the current write family

Do not delete evidence, policy, dry-run, or execution rows during an incident. Preserve the audit trail and export the affected sessions.
