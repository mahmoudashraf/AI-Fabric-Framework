# Thinker Resolver User Guide

Status: full user guide for the current 006 Thinker/Resolver implementation (2026-05-01)

This guide explains how Platform admins, operators, implementation partners, and support users should use Thinker/Resolver today.

Related guides:

- [Thinker Resolver Operator Guide](./THINKER_RESOLVER_OPERATOR_GUIDE.md)
- [Thinker Resolver Partner Guide](./THINKER_RESOLVER_PARTNER_GUIDE.md)
- [Shopify Companion Merchant Launch And Support Guide](./SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md)
- [Thinker Resolver Developer Guide](../Development_Guides/THINKER_RESOLVER_DEVELOPER_GUIDE.md)

---

## 1) What Thinker/Resolver Is

Thinker/Resolver is a governed issue-resolution workflow.

It has two connected parts:

- Thinker: read-first diagnosis that records the user question, answer, evidence, resolution plan, and audit trail.
- Resolver: a governed proposal, policy, dry-run, and execution layer for approved low-risk follow-up actions.

The current implementation is not an unrestricted autonomous store operator. It is a bounded system for diagnosing issues with evidence and escalating approved support work.

---

## 2) Current Implemented Scope

Implemented now:

- Thinker issue sessions persisted in Platform.
- Evidence items, resolution plans, and audit events.
- Operator UI for readiness, controls, sessions, evidence, exports, policy, dry-runs, and execution ledger.
- Partner UI for assigned-store Thinker sessions and redacted support handoffs.
- Shopify admin health card for Thinker deep diagnosis readiness.
- Shopify Companion Elite gating for Thinker diagnosis posture.
- Resolver proposal and policy ledger.
- Non-mutating dry-run for support escalation.
- Governed execution for `SUPPORT_ESCALATION` only.
- Real Partner Enablement support escalation and evidence bundle records on execution.

Not implemented:

- broad write automation across Shopify catalog, pricing, inventory, refunds, orders, billing, or theme settings
- arbitrary action execution from storefront chat
- bypassing policy, dry-run, confirmation, idempotency, or assignment checks
- Bridge-generated semantic fallback answers that replace runtime evidence

---

## 3) Core Terms

Thinker session:

The persisted diagnostic record. It includes deployment, shop, mode, user question, safe answer, status, recommendation, evidence count, plan count, and audit history.

Evidence:

The source material used to explain the answer or issue. Evidence can come from read actions, RAG, runtime metadata, verification packs, or operator-created diagnostic records. Evidence is stored so support can inspect why the system reached a conclusion.

Resolution plan:

The recommended next steps for the issue. A plan can be read-only, require merchant or partner support, or require an operator-governed Resolver proposal.

Resolver proposal:

A proposed follow-up action connected to a Thinker session and evidence. A proposal is not execution.

Policy decision:

The platform decision for whether a proposal is allowed. Current low-risk support escalation proposals are allowed only when the deployment, assignment, action family, and evidence posture pass checks.

Dry-run:

A non-mutating simulation. It validates parameters, explains expected state transition, lists expected side effects, confirms rollback posture, and records the result. It creates no partner escalation and changes no Shopify data.

Execution:

The real governed write after policy and dry-run. Current execution only creates Partner Enablement support escalation records and evidence bundles. It requires exact confirmation text and an idempotency key.

---

## 4) Where To Use It

Platform UI:

- route: `/thinker-resolver`
- audience: Platform Admin and Platform Operator
- use it to manage deployment controls, inspect sessions, export evidence, create proposals, run dry-runs, and execute approved support escalations

Partner UI:

- route: `/thinker`
- store workspace tab: `/stores/{storeId}` then `Thinker`
- audience: assigned implementation partners
- use it to inspect redacted Thinker sessions and create partner support handoffs when the assignment allows support management

Shopify admin:

- embedded app, `Partner access` section, `Thinker deep diagnosis` card
- audience: merchant store admin and internal support
- use it to confirm whether Thinker is enabled and healthy for the linked deployment

Storefront widget:

- shopper-facing chat and embedded surfaces
- mode choice should be respected by the runtime request metadata
- Thinker deep mode is intended for deeper diagnosis and evidence-backed answers

---

## 5) Standard Operator Workflow

Use this sequence for Shopify Companion Elite validation:

1. Confirm the store is installed and linked to a Platform deployment.
2. Confirm billing is `ELITE` and active.
3. Open Platform UI `/thinker-resolver`.
4. Select the deployment.
5. Enable `Thinker diagnosis`.
6. Enable `Resolver preview` only if support proposal proof is needed.
7. Leave `Governed execution` off unless intentionally testing real support escalation creation.
8. Review or create a Thinker session.
9. Inspect evidence, plan, and audit.
10. Export the session packet when support proof is needed.
11. Create a support escalation proposal only when the evidence explains the issue.
12. Run dry-run and inspect expected side effects.
13. Execute only with confirmation `CREATE SUPPORT ESCALATION` and a unique idempotency key.

---

## 6) Standard Partner Workflow

Partners can use Thinker only for assigned stores.

Use this sequence:

1. Open Partner UI `/thinker`.
2. Select an assigned store.
3. Open a Thinker session.
4. Review redacted evidence and resolution plan.
5. Confirm the issue is support-worthy and not missing evidence.
6. Create a support handoff only when the partner assignment includes `SUPPORT_MANAGE`.

Partner views intentionally hide operator-only identifiers, raw evidence references, proposal parameters, secrets, and runtime credentials.

---

## 7) Storefront Testing Workflow

Use the storefront widget for answer-quality testing, not for operator-only execution.

Recommended live test queries:

- `Show me products related to student laptops.`
- `Need to see more details about high performance laptops for gaming.`
- `Compare AtlasBook 14 Laptop, Harbor Student 15 Laptop, and Aurora 2-in-1 14 Laptop based on the product details you have.`
- `Which laptop options are best for a student who needs portability and value?`
- `Compare available snowboards under $800 and explain what evidence is missing before claiming one is safest.`
- `Create support escalation for this unresolved shopper issue.`

Expected behavior:

- product and comparison questions should answer from RAG and eligible read actions
- vague prompts with attachments should respect the attached products as context
- the response metadata should expose the selected mode when debug tooling is enabled
- support escalation from storefront chat should not execute a support write directly
- real support escalation execution belongs in Platform or Partner governed flows

If the widget shows a generic error, inspect the network response first. A generic widget error usually means the bridge or platform returned a non-2xx response or malformed response payload. Do not hide that with a canned shopper answer during optimization.

---

## 8) Safety Rules

Follow these rules:

- Keep Thinker read-first.
- Let runtime/Thinker generate final answers from read-action and RAG evidence.
- Do not add Bridge semantic fallbacks that replace successful runtime evidence.
- Do not add domain text matching inside framework/core modules.
- Put Shopify-specific prompt behavior in Shopify deployment configuration.
- Keep commerce-curated modules generic enough for commerce platforms beyond Shopify.
- Add a new write family only with policy, dry-run, execution, post-action verification, UI, tests, and release-gate proof.

---

## 9) Troubleshooting

Thinker health is unavailable:

- confirm the store has a linked deployment
- confirm Platform backend is reachable
- confirm the deployment control exists
- open `/api/shopify/stores/{shopDomain}/thinker-health` as an operator-authenticated Platform check

Partner cannot see sessions:

- confirm the store assignment is active
- confirm the assignment includes `PRODUCT_CONFIG_READ`
- confirm the session shop matches the assigned store
- confirm the partner is calling `/api/partners/stores/{storeId}/thinker-sessions`

Dry-run cannot run:

- confirm the proposal exists
- confirm policy decision is `ALLOWED`
- confirm Resolver preview is enabled
- confirm the action family is not disabled

Execution cannot run:

- confirm governed execution is enabled for the deployment
- confirm dry-run is completed
- confirm confirmation text is exactly `CREATE SUPPORT ESCALATION`
- confirm the idempotency key is present and unique for the intended operation
- confirm `SUPPORT_ESCALATION` is not disabled

Storefront chat returns `Sorry, I encountered an error processing your request`:

- inspect the bridge response status and body
- confirm the platform runtime endpoint is healthy
- confirm the active deployment action catalog does not expose unregistered actions
- confirm the widget mode sent in the request matches the selected UI mode
- confirm the response includes a generated answer or structured action evidence that the widget can render
