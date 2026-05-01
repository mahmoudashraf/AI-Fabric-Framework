# Shopify Companion Readiness Audit Operator Guide

Status: operator-facing guide for the live Shopify Companion first-product readiness gate (2026-04-29)

This guide is for:

- platform admins
- platform operators
- internal launch reviewers
- implementation leads preparing design-partner evidence

This is not a merchant-facing guide. Merchants should use the Shopify admin app, setup/readiness panels, Knowledge Sync wording, billing, support, and launch exports. The readiness audit is an operator review surface for release governance and design-partner proof.

Related guides:

- [Shopify Companion Merchant Launch And Support Guide](./SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md)
- [Shopify Companion Customer Capabilities Guide](./SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md)
- [Shopify Companion Readiness Audit Developer Guide](../Development_Guides/SHOPIFY_COMPANION_READINESS_AUDIT_DEVELOPER_GUIDE.md)
- [Shopify Companion Launch, Review, And Support Exports Guide](../Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md)
- [Platform Hosted Deployment Verification Guide](../Development_Guides/PLATFORM_HOSTED_DEPLOYMENT_VERIFICATION_GUIDE.md)

---

## 1) What The Readiness Audit Does

The Shopify Companion readiness audit answers one release question:

`Can Shopify Companion Starter be treated as the first product ready for controlled design-partner and market-readiness activity?`

It checks:

- product truth and tier claims
- Free, Starter, and Elite entitlement boundaries
- storefront product experience
- merchant admin readiness
- query-to-answer quality
- live bridge verification
- support and App Review collateral
- install and recovery posture
- design-partner evidence readiness

The current completed readiness state is `DESIGN_PARTNER_READY`, not `MARKET_PROVEN`.

Market proof still requires real merchant/design-partner outcomes from 5-10 stores.

---

## 2) Open The Operator Screen

Open the Platform UI route:

```text
/shopify-readiness-audit
```

Required access:

- `PLATFORM_ADMIN` can view and run the readiness suite.
- platform operator access can review the audit if enabled, but suite dispatch remains admin-only.

The page reads:

- `GET /api/shopify/readiness-audit/latest`
- `GET /api/shopify/readiness-audit/definition`
- verification suite run state from the Platform control plane

---

## 3) How To Read The Page

The top cards show:

- `Decision`: current readiness decision.
- `Latest run`: latest evidence-producing suite run.
- `Evidence`: freshness state.
- `Answer pack`: canonical answer-query pass count.

The main sections show:

- `Checklist`: readiness gate by category.
- `Query pack`: canonical shopper query categories and expected behavior.
- `Answer results`: per-query pass/fail, HTTP status, failure category, and redacted answer preview.
- `Evidence and decision`: artifact references and decision options.

Important evidence behavior:

- The page can use a standalone `shopify-first-product-readiness-audit` run.
- It can also use the Shopify readiness stage from `full-platform-release-readiness`.
- This means the operator page remains valid when the latest proof came from the full release gate.

---

## 4) Readiness Decisions

`DESIGN_PARTNER_READY`

- Technical readiness passed.
- Evidence is clear enough for non-founder review.
- One verified demo/test store can be used for controlled design-partner proof.
- Proceed with 5-10 store design-partner validation.

`TECHNICAL_READY`

- Product gates passed, but evidence still needs review or packaging before design-partner activity.
- Do not start broad design-partner outreach until the evidence is understandable without chat history.

`PARTIAL`

- Product mostly works, but bounded blockers remain.
- Resolve blockers and rerun the audit before design-partner activity.

`NOT_READY`

- A blocking truth, entitlement, live verification, answer-quality, storefront, support, or evidence issue exists.
- Do not use the product for design-partner proof.

Do not use `MARKET_PROVEN` for this audit. That decision requires real merchant outcomes.

---

## 5) When To Run The Audit

Run the readiness audit:

- before controlled design-partner outreach
- after Shopify Companion entitlement, billing, storefront, bridge, runtime, prompt, or support-collateral changes
- after changing the canonical query pack
- after fixing a readiness blocker
- before relying on old readiness proof if the page says evidence is stale

Run the full release gate instead when:

- the change affects multiple products or platform-wide release posture
- Partner Enablement, hosted verification, managed vector providers, or marketplace flows are also in scope
- release approval needs one end-to-end proof packet

---

## 6) How To Run It

From the UI:

1. Open `/shopify-readiness-audit`.
2. Confirm the target store and tier profile are correct.
3. Click `Run readiness audit`.
4. Wait for the run to complete.
5. Confirm the decision, blockers, checklist, and answer results.

From Verification Ops:

- run `shopify-first-product-readiness-audit` for standalone readiness proof
- run `full-platform-release-readiness` for end-to-end release proof

Expected live proof for a passing design-partner state:

- decision: `DESIGN_PARTNER_READY`
- evidence: `FRESH`
- blockers: `0`
- checklist: all blocking items passed
- answer results: all canonical P0 queries passed
- latest stage: `shopify-first-product-readiness-audit` passed

---

## 7) Checklist Meaning

Product truth and tier claims:

- Confirms Free is AI search only.
- Confirms Starter is read-only embedded store intelligence.
- Confirms Elite owns governed actions.
- Rejects active copy that positions the product as only a generic chatbot.

Code and entitlement gates:

- Confirms Free/Starter do not expose order lookup or governed actions.
- Confirms storefront and bridge routes enforce tier boundaries.

Storefront product experience:

- Confirms hosted surfaces render and hand off to Max Mode safely.
- Confirms no broken mobile or desktop state blocks first-product proof.

Merchant admin readiness:

- Confirms the merchant app shows setup, Knowledge Sync, billing, support, usage/value, and blockers without raw internals.

Query-to-answer quality:

- Confirms the canonical query pack passes deterministic checks for groundedness, tier safety, merchant safety, and forbidden language.

Platform operator audit UI:

- Confirms readiness is reviewable from Platform UI, not trapped in chat history or temporary files.

Live bridge verification:

- Confirms live bridge and admin checks pass when the required secret is configured.

App review and support collateral:

- Confirms support, launch, App Review, and design-partner exports match shipped product truth.

Install, uninstall, and recovery posture:

- Confirms lifecycle behavior is documented and non-destructive checks are verified.

Design-partner readiness:

- Confirms one verified test store and evidence packet are ready for non-founder review.

---

## 8) Evidence Artifacts

The readiness runner writes a compact packet under:

```text
/tmp/shopify-first-product-readiness-audit
```

The packet normally includes:

- `summary.md`
- `commands.txt`
- `live-verification-summary.txt`
- `browser-proof-summary.md`
- `product-truth-scan.txt`
- `answer-quality-query-pack.json`
- `answer-quality-results.json`
- `answer-quality-audit.md`
- `audit-ui-proof.md`
- `readiness-matrix.md`

Operator rule:

- Use artifact references and summaries.
- Do not paste raw secrets, cookies, tokens, private headers, or noisy logs into docs or tickets.
- If evidence is stale, rerun the suite before using the decision for release or design-partner proof.

---

## 9) Handling Blockers

If the page shows blockers:

1. Read the blocker text and the failed checklist category.
2. Check whether the latest evidence came from standalone readiness or full release gate.
3. Open the relevant evidence artifact.
4. Fix the underlying product, entitlement, runtime, bridge, documentation, or query-pack issue.
5. Rerun the readiness suite.

Common blocker meanings:

- `No readiness audit run has been recorded`: run standalone readiness or full release gate.
- `Evidence is stale`: rerun the suite.
- `Answer query ... failed`: inspect `answer-quality-audit.md` and the redacted answer preview.
- `Latest readiness audit did not pass`: inspect the suite stage log and fix the failing gate.
- live verifier failed: check bridge health, Platform health, store billing posture, and required secret wiring.

---

## 10) Design-Partner Handoff

Before outreach, confirm:

- decision is `DESIGN_PARTNER_READY`
- evidence is `FRESH`
- blockers are empty
- one test store can be demonstrated cleanly
- merchant/support exports are ready for a non-founder reader
- the design-partner validation questions are prepared

Do not claim:

- public market proof
- broad merchant validation
- Elite governed action readiness unless Elite-specific gates have passed
- revenue lift or ROI beyond bounded usage/value signals

The next step after this audit is controlled design-partner proof across 5-10 real stores.
