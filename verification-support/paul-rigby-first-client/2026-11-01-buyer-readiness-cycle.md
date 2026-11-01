# First-Customer Buyer Readiness: Implementation Cycle

Date: 1 November 2026
Status: FIRST DELIVERY CYCLE VERIFIED LIVE; full demo/client readiness remains open.

## Scope And Baseline

This cycle continues 010.55 against the existing Northfield deployment, not a
new client or permanent canary stack. Both target assignments remain on v9 /
AI Fabric 0.8.43. The latest comparison/history fix is not being reopened.
No framework release, new database, business system or recovery redesign is
part of this cycle.

`run-buyer-readiness.mjs` sent eight read-only questions to each target through
the published Engage installation and its canonical conversation-message route.
The runner verifies assignment/deployment identity, uses separate bounded
sessions and saves no bearer/provider tokens. It stops on provider allowance
interruption or an unexpected successful non-READ action. Model answers require
manual review; there is no lexical semantic grading.

## Observed Answers

- **PASS on both:** stock followed by delivery policy; reverse clause order;
  selected named vehicle facts plus warranty/delivery; explicit test-drive
  duration versus advance-notice question. Correct Aster/Morrow prices were
  returned. The duration question correctly distinguished 30 minutes of driving
  from an unspecified notice period.
- **FAIL on both:** local delivery cost plus handover requirements returned zero
  evidence. The price is absent from current content, but photo-ID/payment/
  preparation facts exist and were missed. Do not relabel this entire answer
  as an acceptable missing-source case.
- **FAIL on staging:** the age/licence-history question correctly reported that
  those requirements are absent, but incorrectly added a 30-minute advance-
  booking requirement. Production correctly kept the duration wording. This is
  an observed interpretation mistake, not a reason for a hard semantic validator.
- **SOURCE/CONTEXT GAPS:** exact 40-mile charge, age/licence-history rules and
  complete request fields are not established by the three current policy
  files. The request-input answers also did not explain the separately declared
  action form. Do not claim that a truthful no-evidence answer completes the
  intended buyer feature, or infer a new framework defect without tracing the
  action-schema/generation contract.

Index inspection found nine rows containing three distinct short fixture files,
with historical release/source registrations. Those are not nine distinct
policies or proof that every historical registration is currently eligible.
The richer repository examples were not the hosted source content. They also
use different fictional terms, so indexing them together would be incorrect.

Raw token-free runner receipts are in `/private/tmp/loomai-buyer-readiness-20261101-{staging,production}-1/`.
They are local qualification artifacts, not public customer data or production
readiness claims.

## Implemented Changes

- Commit `1d1c4bc61`: dealership detail controls use explicit reference-bound
  query commands on standalone apps; approved website navigation remains
  separate. Public fields are retained in result projections. Generic source
  summaries/detail panes use structured rendering rather than raw JSON; original
  evidence and attachment data are unchanged.
- Commit `5fb905b04`: customer resource summaries distinguish draft configuration
  from active app assignments. Overview warnings follow exact assigned releases;
  older failed releases remain in Advanced history. Missing assignments do not
  silently select another healthy release. Installed sign-in regression coverage
  clicks the actual widget header rather than calling a stub callback.
- Prepared a versioned fictional [customer-storage policy pack](../../product-demos/autotrader-dealership-demo/backend/deployment/customer-storage-policy-pack/README.md).
  It is not uploaded or indexed. Owner upload or a separately scoped uploader
  credential is requested; runtime document credentials remain read-only.

## Verification So Far

- Dealership projection/detail/browser tests: 18 PASS, including 390px/1440px
  layouts and no-query/no-navigation checks for missing/foreign references.
- Customer product/resource models: 13 PASS. Console TypeScript/build PASS.
- Source-presentation tests: 3 PASS. Widget typecheck and embedded builds PASS.
  Existing action-presentation and semantic-boundary smokes PASS.
- Installed sign-in: four new real-installer tests PASS; existing 26 browser
  tests PASS. Production desktop/390px and staging390px live dialog entry/cancel
  work in fresh checks. The earlier nonresponsive entry was not reproduced;
  no auth production-code change was made. Real provider sign-in/protected-action
  acceptance remains separate.

## Hosted Closure Of The Focused Changes

- Core image CI `41333982816` and standalone verification/image CI `41333982720`
  completed successfully for source `5fb905b043bcad9c9bf88131dde6e81301f860e1`.
  The shell's production code did not change, so its existing shared frontend
  was not needlessly replaced for test-only coverage.
- All four affected existing services reached exact Coolify terminal success
  and native `running:healthy` with the expected image/source: staging backend
  `ur5xq6e3ad16b8khz2w8a3x2`, production backend `ovcgbhm96sxv7p1apwrinsa1`,
  staging UI `sddwqfh6uk7ttbilrrrr2cbp`, production UI `g8jxix540oncgc9u7dx0qgu1`.
- Installer, widget and both experience-pack bytes passed SHA-256/SRI readback
  on both control planes. Widget hash begins `1a385049ef961473`; dealership pack
  hash begins `817d810b5dd17a7e`. The existing staging, production and embedded
  installation manifests use these assets and retain their runtime assignments.
  Production-owned apps use production-hosted assets even when their runtime
  target is staging; a separate staging asset service is not their live source.
- Actual staging inventory -> first-vehicle detail and production inventory ->
  second-vehicle detail clicks PASS. They produced the correct rich Aster and
  Morrow detail views, prices/mileage and images, using the selected references.
  The former disabled/no-op detail path is fixed without guessed website URLs.
- Live JSON evidence previews and structured source-detail fields PASS. Plain
  vectorized source text remains literal evidence, including its machine-like
  field formatting; it was not rewritten with string matching. Missing provider
  facts such as location/range remain visibly unknown, not fabricated by UI.
- The current customer Console shows `App connected`, three active assignments
  and exactly the two assigned verified releases. Eighteen other releases remain
  available in collapsed Advanced history instead of becoming current overview
  failures. Draft configuration and app assignment are not runtime-health claims.
- Embedded website inventory/docked entry and opening configured Max Mode PASS.
  A fresh staging conversation and detail result survived reload within the
  valid guest session. An earlier long-idle reload started a new guest context;
  that existing expiry path is distinct from the bounded reload proof and was
  not weakened or declared a new asset-update defect.
- Nine actual browser AI submissions supplement the sixteen API questions.
  Staging AI-selected human transfer reached the pending state; cancelling that
  isolated handover returned to AI, whose delivery-policy answer then PASSed.
  Production test-drive intake asked for contact details, but the subsequent
  cancel request produced unsupported-flow wording rather than a useful
  no-submission acknowledgement. No business request was submitted; full
  confirmation/cancellation/receipt acceptance remains OPEN.
- Desk SSO opened the already signed-in owner's portal, which correctly denied
  conversation access without a Support assignment. No role was granted to
  manufacture a pass. This is not the assigned agent's full operator-UI proof.

No AI runtime/configuration change, framework release, reindex, new customer
service/database or business write was used to deliver these fixes. This is
not a green whole-demo gate or a claim of real Paul Rigby integration.

## Remaining Acceptance

- Finish full Desk operator UI, protected simulated request/cancellation/receipt,
  and complete basic phone journey checks. An AI-selected pending handover and
  cancellation alone do not close operator reply delivery. Inspect the action
  intake/cancellation trace before proposing any further code change.
- Upload/replace/index approved fictional policies on staging, inspect effective
  source versions and rerun affected questions before the corresponding
  production update. Do not copy staging vectors or wipe whole collections.
- Finish the existing external-systems template/profile and customer binding
  setup against approved client resources when supplied. This cycle does not
  claim Paul Rigby API access, rights, storage qualification or launch approval.

Operational receipts are retained under `/private/tmp/loomai-pilot-rollout-*-20261101/`,
`/private/tmp/loomai-pilot-{staging,production}-assets-20261101.json` and
`/private/tmp/loomai-pilot-current-manifests-20261101.json`. Native browser
screenshots were captured during the live detail/source and Console inspection.
