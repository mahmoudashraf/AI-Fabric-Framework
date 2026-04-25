# Shopify Companion First Product Readiness Audit

Status: implementation handoff (2026-04-25)

Owner mode: technical LLM implementation/audit session

Roadmap phase: First Product Readiness Gate

Priority: P0

Depends on:

- [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
- [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
- [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)

Related next work:

- [004_PARTNER_ENABLEMENT_FOUNDATION.md](004_PARTNER_ENABLEMENT_FOUNDATION.md)

---

## Strategic Handover

Shopify Companion Starter is the first product candidate.

Current accepted state:

- Launch Truth Enforcement is complete.
- Storefront Product Shell is complete.
- Starter Launch Package is complete.
- Shopify hosted extension deploy completed.
- browser proof completed.
- bridge admin verification completed.
- full live verifier passed with bridge admin checks enabled.
- Starter Launch Package has no pending handoff items.

Important distinction:

- **Technically product-ready** means the shipped product passes repeatable product, code, entitlement, storefront, merchant-admin, support, and live verification gates.
- **Market-proven** means real merchant/design-partner stores have installed, used, and generated enough signal to validate the product and support posture.

This audit determines whether Shopify Companion Starter is ready to be treated as the first product for design-partner and controlled market activity. It does not claim broad public-market proof by itself.

Canonical product truth:

- Shopify Companion is embedded store intelligence, not a chatbot.
- Free is AI search only.
- Starter is full read-only embedded store intelligence.
- Starter excludes order lookup and governed actions.
- Elite is the only tier for verified governed actions.
- `Knowledge Sync` is merchant-facing language.
- raw vectorization, provider, queue, replay, runtime, and debug language is operator-only.

The audit is a gate before:

- design-partner proof across 5-10 real stores
- public launch push
- broad partner/integrator activity
- Elite activation
- second product work

---

## Read First

Read these before running the audit:

1. [CODEX_WORKING_CONTEXT.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md)
2. [Codex_Strategic_Context.md](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md)
3. [001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md](001_SHOPIFY_COMPANION_LAUNCH_TRUTH_ENFORCEMENT.md)
4. [002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md](002_SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL.md)
5. [003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md](003_SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE.md)
6. [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](../SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
7. [SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md](../SHOPIFY_COMPANION_STOREFRONT_PRODUCT_SHELL_ROADMAP.md)
8. [SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md](../SHOPIFY_COMPANION_STARTER_LAUNCH_PACKAGE_ROADMAP.md)
9. [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](../SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)

Useful docs:

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

- audit result
- evidence produced
- commands run
- live verification status
- blockers
- readiness decision
- next handoff

Use this compact template:

```text
- First Product Readiness Audit status: <passed/partial/blocked/failed>.
- Evidence: <compact artifact paths or summary>.
- Verification: <commands run and pass/fail>.
- Live verification: <passed/skipped/blocker>.
- Readiness decision: <technical-ready/design-partner-ready/not-ready>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```

Do not paste long logs, secrets, raw diffs, private tokens, or noisy reasoning into working context.

---

## Audit Question

Answer this:

> Can Shopify Companion Starter be treated as the first product ready for controlled design-partner and market-readiness activity?

Acceptable answers:

- `TECHNICAL_READY`: product is technically ready; proceed to design-partner proof.
- `DESIGN_PARTNER_READY`: product is technically ready and has enough live evidence/support collateral for 5-10 real stores.
- `PARTIAL`: product mostly works but has bounded blockers before design-partner proof.
- `NOT_READY`: product has product-truth, entitlement, live verification, support, or merchant-readiness blockers.

Do not mark `MARKET_PROVEN` from this audit alone. Market proof requires real store outcomes.

---

## Audit Scope

In scope:

- product truth and tier claims
- Free/Starter/Elite entitlement gates
- storefront embedded surfaces
- Max Mode/depth-layer handoff
- merchant admin setup/readiness
- Knowledge Sync language and readiness
- billing/tier visibility
- analytics/value proof
- App Store/App Review/support/export material
- theme extension deploy state
- browser proof on desktop/mobile
- live bridge verification
- live direct bridge admin endpoint coverage
- support readiness
- secret-handling hygiene
- design-partner readiness

Out of scope:

- Partner Enablement implementation
- public partner portal
- affiliate/referral/commission workflows
- white-label packaging
- public partner APIs
- WooCommerce or second product work
- Elite governed actions beyond verifying they are gated/deferred correctly

---

## Required Evidence Output

Produce a compact audit packet.

Recommended local artifact root:

```text
/tmp/shopify-first-product-readiness-audit/
```

Required packet contents:

- `summary.md`: pass/fail decision, evidence list, blockers, next handoff
- `commands.txt`: commands run, no secrets
- `live-verification-summary.txt`: verifier outcomes, no raw secrets
- `browser-proof-summary.md`: desktop/mobile storefront observations and screenshot paths if taken
- `product-truth-scan.txt`: active-scope search findings and disposition
- `readiness-matrix.md`: checklist result by category

Do not commit `/tmp` artifacts. Commit only roadmap/context/doc updates unless the audit creates durable docs intentionally.

---

## Readiness Matrix

### 1. Product Truth

Pass criteria:

- active product docs say Free is AI search only
- active product docs say Starter is full read-only embedded store intelligence
- active product docs say Starter excludes order lookup
- active product docs say Elite is gated for verified governed actions only
- active App Store/support/partner material does not lead with chatbot positioning
- old `Growth / Pro` language is removed or clearly marked historical

Search:

```bash
rg -n "Growth|Pro|order lookup|order-lookup|Free.*order|Starter.*order|chatbot|chat bot|vectorization|runtime|provider|Railway|replay queue|raw vector" \
  doc/Productization/future-work/MarketPlace/Products/Strategy \
  Final_Documentation/User_Guides \
  Final_Documentation/Development_Guides \
  product-services/shopify-bridge-service/ui/src \
  product-services/shopify-bridge-service/shopify-extension \
  product-services/shopify-bridge-service/src/main/java
```

Disposition rule:

- fix active-scope leaks
- leave historical references only if clearly marked historical
- reject audit if active launch copy claims Free/Starter order lookup or governed actions

### 2. Code And Entitlement Gates

Pass criteria:

- Free entitlement exposes `ai-search` only
- Starter exposes read-only embedded surfaces
- Starter excludes order lookup
- direct order lookup routes deny Free/Starter
- governed actions remain Elite-only/gated
- direct bridge/storefront routes enforce surface entitlement
- billing/tier transitions do not silently expose Starter/Elite features

Required checks:

```bash
git diff --check
bash -n scripts/verify-shopify-companion.sh
bash -n scripts/run-shopify-companion-rollout.sh
bash -n scripts/build-shopify-companion-review-kit.sh
npm --prefix product-services/shopify-bridge-service/ui run build
mvn -f product-services/shopify-bridge-service/pom.xml -q test
mvn -f Platfrom/backend/pom.xml -q test
```

### 3. Storefront Product Experience

Pass criteria:

- Shopify hosted extension version is current or intentionally unchanged since last deploy
- product-page embedded surfaces render on desktop and mobile
- AI search surface works
- embedded surfaces open Max Mode/depth layer correctly
- page context and explicit attachments hand off correctly
- no dedicated order lookup block renders for Free/Starter
- no broken visual states on mobile

Recommended proof:

- browser screenshot desktop product page
- browser screenshot mobile product page
- browser proof of `Continue in assistant` from embedded surface to Max Mode
- record CDN/theme extension version if available

### 4. Merchant Admin Readiness

Pass criteria:

- merchant admin shows setup, surfaces, Knowledge Sync, billing, support handoff, usage/value, and blockers
- merchant admin does not show raw vectorization/provider/runtime/Railway/replay language
- Elite/order lookup setup is hidden or clearly gated outside entitled tiers
- generated support/App Review/runbook copy matches shipped product truth
- usage summary shows early value evidence without overclaiming ROI

Recommended checks:

```bash
npm --prefix product-services/shopify-bridge-service/ui run build
```

If a live admin URL is available, verify:

- dashboard loads
- Knowledge Sync wording appears
- Starter remains read-only wording appears
- usage/value signals appear after data exists

### 5. Live Verification

Pass criteria:

- `scripts/verify-shopify-companion.sh` passes against the target test store
- bridge admin checks pass when `SHOPIFY_BRIDGE_ADMIN_API_KEY` matches deployed `SHOPIFY_BRIDGE_SHARED_SECRET`
- direct admin endpoint coverage proves:
  - overview
  - billing
  - webhook diagnostics
  - support readiness
  - usage summary
  - Knowledge Sync/vectorization source readiness
  - governed action gate state

Command:

```bash
scripts/verify-shopify-companion.sh
```

If admin checks are enabled:

```bash
SHOPIFY_BRIDGE_ADMIN_API_KEY_FILE=/path/to/secret.file scripts/verify-shopify-companion.sh
```

Secret rule:

- never print, paste, commit, or log the bridge secret
- use env vars or secret files only
- audit output must say only whether admin checks were enabled and passed

### 6. Shopify App/Extension Deploy State

Pass criteria:

- Shopify CLI app info succeeds non-interactively if token/session is available
- app/theme extension deploy is current if extension assets changed since last deploy
- if no extension assets changed, record the last proven deployed version and skip deploy with reason

Optional commands when credentials are available:

```bash
npm --prefix product-services/shopify-bridge-service run shopify:preflight
npm --prefix product-services/shopify-bridge-service run shopify:app:info
```

Deploy command should be run only if needed and authorized by current handoff conditions.

### 7. App Store, App Review, And Support Collateral

Pass criteria:

- App Store listing/package copy matches Free/Starter/Elite truth
- App Review guide and screencast script match shipped behavior
- support runbook explains common setup, Knowledge Sync, billing, and storefront blockers
- launch packet/export material is consistent with merchant admin and live product truth
- design-partner packet is ready enough for controlled merchant proof

Check docs:

- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md`

### 8. Install/Uninstall And Recovery

Pass criteria:

- uninstall behavior is documented and verified in a safe target store or simulator
- reinstall/reconnect path is clear
- billing cancellation/plan state does not leave unsafe feature access
- no destructive live-store action is run without explicit safe target and reason

Optional safe check:

```bash
bash -n scripts/verify-shopify-companion-uninstall.sh
```

Run the uninstall verifier only if the target store is safe for destructive lifecycle testing.

### 9. Supportability

Pass criteria:

- common failure modes have clear user-facing blockers
- support readiness endpoint gives actionable status
- evidence bundle contains enough information to debug without reconstructing from chat
- no raw secrets or tokens appear in support/export material
- unanswered/source-gap questions are framed as merchant value evidence
- action-intent questions are framed as future Elite demand only

### 10. Design-Partner Readiness

Pass criteria for `DESIGN_PARTNER_READY`:

- one clean demo/test store is fully verified
- product truth and support packet are ready for a non-founder reader
- onboarding steps are repeatable without live explanation
- store setup can be completed from docs/admin UI
- expected first 5-10 store validation questions are written

Design-partner validation questions:

- Did the merchant understand what Free includes?
- Did the merchant understand what Starter adds?
- Did the merchant notice embedded product-page intelligence before chat?
- Did Knowledge Sync language make sense?
- Did setup require founder explanation?
- Did any support issue lack enough evidence?
- Did shoppers use AI search/product FAQ/comparison/policy surfaces?
- Did unanswered questions reveal source gaps or product demand?
- Did action-intent questions suggest future Elite demand?

---

## Final Decision Rules

Mark `TECHNICAL_READY` only if:

- local builds/tests pass
- entitlement gates pass
- live verifier passes
- browser proof passes or has a justified no-change skip
- active product/support/App Review copy matches shipped truth
- no secret-handling leak is found
- no P0 merchant-facing blocker exists

Mark `DESIGN_PARTNER_READY` only if all `TECHNICAL_READY` criteria pass and:

- support/export packet is ready
- design-partner checklist is ready
- one verified demo/test store can be shown confidently
- known gaps are acceptable and documented

Mark `PARTIAL` if:

- product works but one or more bounded, non-P0 blockers remain
- live checks are partially skipped for a concrete reason
- evidence is enough to continue internal work but not enough for design partners

Mark `NOT_READY` if:

- Free/Starter/Elite truth is inconsistent
- Free or Starter exposes order lookup/governed actions
- live verifier fails without a known environmental blocker
- storefront surfaces are broken
- merchant/admin copy leaks internals or misleads buyers
- support evidence is insufficient for first real stores

---

## Completion Section For Auditing LLM

Append a compact completion update here before ending the audit session.

Required completion fields:

- audit summary
- readiness decision
- evidence artifacts
- changed files
- verification commands and results
- live verification status
- blockers or no pending handoff items
- pushed commit refs, if pushed

Do not include secrets, long logs, or raw diffs.
