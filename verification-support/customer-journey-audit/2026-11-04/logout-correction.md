# Engage Logout Correction And Promotion Follow-Up

Date: 4 November 2026. Status: LOGOUT FIX LIVE AND VERIFIED ON BOTH HOSTS;
SUPPORT PRODUCTION PROMOTION AND ORIGINAL-APP READINESS VERIFIED.

## Scope

Owner requested the confirmed logout correction and completion of meaningful
promotion work. Quota/deletion changes are a separate design decision, not
silently included in this authentication patch. No framework release, runtime
revocation ledger, global sign-out or capacity/draining redesign.

## Logout Implementation

- Original provider evidence: 19:43:59 UTC Acme logout failed with403 bad_jwt,
  token expired. Local authority was cleared, but provider revocation failed.
- Shared Engage shell now refreshes an expired/expiring session before local
  provider revocation. The refresh must retain the same verified subject; it
  cannot restore or persist identity after sign-out starts.
- Local credential, private conversation state and workspace authority are
  cleared immediately. The temporary credential exists only for revocation.
- In-flight normal refresh is shared rather than rotated twice. Its late result
  cannot reinstall state or reload the page before logout completes. Repeated
  sign-out joins one operation. Refresh and logout each have five-second bounds.
- Failed/mismatched refresh, provider failure or timeout remain visible as
  unconfirmed provider revocation; no blanket401/403-success interpretation.
- The documented JWT lifetime contract is unchanged. This does not claim instant
  invalidation of copied access JWTs or already-issued bounded runtime tokens.

## Local Verification

- TypeScript typecheck and production build passed.
- Server security/config test: one passed, zero skipped.
- Complete shell browser suite:32 passed, zero failures/errors/skips.
- Added six cases: idle expired-token logout; rejected, foreign-owner and
  unavailable refresh; concurrent refresh/logout; repeated sign-out.
- Existing refresh timeout, owner isolation, login cancellation, magic-link
  nonce/replay and logout503/timeout checks remain passing.
- These are local fixtures, not proof of hosted Supabase session revocation.
  Hosted checks below provide the separate provider/browser evidence.

## Hosted Logout Acceptance

- Source `c2cc543845bea8f432d722cd420ceeb2fcf8cdfe`; image workflow
  [41915229631](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/41915229631)
  passed verification and immutable publication.
- Shared Engage shell native source/image/running/health confirmed on BOTH
  servers. Staging Coolify receipt `hox43q75xauyjin2pdjydz67`; production
  `paxvi8ldv3musc0kdvn54xu0`. No customer runtime, backend or framework upgrade
  was required for this authentication patch.
- Actual provider protocol at21:22:47 UTC: saved Acme fixture access token was
  expired; same-subject refresh200 -> local logout204 -> reuse of refresh token
  denied400 `refresh_token_not_found`. This is real Supabase evidence, not a
  network fixture. It is separate from a hosted idle UI expiry rehearsal.
- Actual staging Acme UI: sign in through the updated shell; authenticated
  support workspace visible; Sign out -> entry-required login gate, no private
  iframe and no browser errors. Supabase independently recorded logout204 at
  21:26:32 UTC, request `01a1a874-da24-7642-b4e3-0214ede0671c`.
- Actual production Northfield UI: normal sign-in -> authenticated workspace ->
  Sign out -> anonymous workspace/Sign in button, zero browser errors. Supabase
  independently recorded logout204 at21:28:54 UTC, request
  `01a1a877-0399-7061-b1c6-123d9dacaf2b`.
- The expiry/race branches were exercised by the actual shell in local browser
  regressions. Hosted UI sign-out used fresh sessions; an hour-long physical
  idle-token UI test is not claimed. No private browser storage injection or
  provider token extraction was used for the live UI checks.
- Evidence: `acme-logout-entry-gate.png`, `production-logout-anonymous.png`.
  Private provider/session and Coolify receipts remain outside the repository.

## Promotion

The initial read-only review found an invalid staging assignment, not a reason
to reapply staging or weaken the promotion checks. At that checkpoint, Support
staging rel-2ab687cf was APPLIED_VERIFIED/PASSED while the original live consumer
still referenced production rel-09c92f90. The completed promotion and a separate
post-activation readiness defect discovered afterward are recorded below.
Northfield already had separate staging/production assignments on one version.

A narrow Console gap permitted reviewing a production candidate with a connection
still assigned to production; the backend correctly rejected it. Console source
`0bf44e602440f12096d4a89e79e369cfba039425` now disables that invalid review and
explains choosing a staging assignment, without rebinding anything.81 related
UI/API tests, TypeScript and production build passed. Core image CI41917425613
and storage regression CI41917425643 passed; backend image correctly skipped.
Both Console hosts are source/image/health verified. Staging Coolify receipt
`mcbi29sj10z8he1dev7y76ua`; production `c1vis50gjn1oe1z215guf4nz`. Actual
customer Production step showed the staging-assignment explanation and disabled
Review production candidate for the production-connected app.

### Support Production Promotion

- Existing deployment `dep-79af290c`, version `ver-51e6c811`, artifact
  `dsa-d3f03019` promoted through the normal APIs. New production release
  `rel-8822aff7` is APPLIED_VERIFIED/PASSED. Runtime and connector keep immutable
  source `83fd17427b28814fb737093571f46615a598e2c0`, AI Fabric0.8.45.
- The existing production runtime and connector were cold-replaced; this was
  explicit maintenance, not a second traffic-isolated production environment.
  No new business database, simulator account or duplicate deployment created.
- All13 reviewed source documents are ACTIVE, activeVersion1. They were read
  from the shared approved S3 source and indexed independently in production.
  Retrieval proof returned5 records. Verification `vrf-e449aa2e`:30 passed,
  four skipped, zero failed. Skipped checks are not presented as passes.
- A temporary configuration-only consumer scoped the staging promotion. The
  original consumer `kit-ca617a82-bfb6-411e-89fe-94ccabf8713e` moved directly
  `rel-09c92f90` -> `rel-8822aff7`, binding revision4 ->5. Exact history confirms
  it never pointed to staging. Existing Engage app remained ACTIVE/ready and
  preserved identity, branding and URL.
- The temporary consumer `support-hosted-01059-20261104-qualification` is unbound
  and owns zero apps/infrastructure. Normal deletion returned409 because two
  promotion command rows retain its audit identity through
  `platform_deployment_promotion_commands_consumer_entity_id_fkey`. It is
  now DISABLED through the normal owning-customer API, retained as metadata and
  excluded from active assignments. It was not force-purged. Zero apps and no
  deployment/release binding were checked before disabling it.

### Follow-Up Readiness Correction

The successful original-app switch exposed a real projection defect: go-live
readiness compared the original consumer with the different temporary consumer
in the frozen promotion command, returning `Frozen candidate consumerId changed`.
That frozen identity scopes the original promotion; it is not a permanent pin
on every later owned app connection to an already-verified production release.

Backend source `095f6071b8be27f8d8aaa538268bdd439acc3734` fixes that narrow case.
A different consumer must ALREADY have the exact same owned deployment, verified
production release and target binding. Pre-activation consumer/revision checks,
customer ownership, immutable candidate hash/config/artifact/target checks and
fresh verification remain enforced. The frozen command is not rewritten.
Focused readiness/controller/promotion43 tests passed, zero failures or skips;
core backend image CI41920412836 passed and correctly skipped the UI image.
Both backends are native source/image/health verified at095f6071. Staging Coolify
receipt `dmnc2yzusuvnkcnwqkdds21f`; production `fxwxpxc15nzvy97pwn6sxgmb`.
Final owning-customer readiness API: activationReady=true,
postActivationComplete=true, blockers=[], original binding revision5. Actual
production Console Refresh production readiness showed Production active and
all mandatory checks passed; optional operations/cleanup were not applicable.
It explicitly reported that no app assignment changed. Screenshot:
`production-readiness-passed.png`. Test account signed out and temporary tab
closed afterward; user-owned tabs were not closed.

### Existing Desk Status Reconciliation And Actual UI Answer

After apply, Desk metadata still said APPLYING although the exact releases had
passed verification. This made the Support team endpoint return503. The existing
authorized owner Desk-status API reconciled both bindings to ENABLED from the
actual verified releases; the team endpoint then returned200 with two agents.
No direct SQL, permission grant, provider retry or release reapply was used.
This is an existing operational reconciliation step, NOT a newly implemented
automatic post-verification hook. That automation gap is recorded rather than
hidden by a roster fallback or unconditional success.

Actual Console Support UI then loaded the assigned-agent row without warning.
Question: explain hosted document upload, staging testing, production use and
whether staging vectors should be copied; make no configuration changes.
The AI accurately distinguished stored versus indexed/ACTIVE files, described
review/preview/index/reconcile/verify, and explicitly said NOT to copy vectors:
index approved original files independently in production. Evidence included
`07-document-storage-and-indexing.txt`, reviewed4Nov, release `rel-8822aff7`.
This one UI answer passed; it is not a claim of universal semantic correctness.
Screenshot: `promoted-support-storage-answer.png`. API/native evidence and private
before/intents/receipts are retained separately from UI evidence.

The canonical private production record is now
`loomai-document-storage-01059/receipts/promotion-qualified-20261104.private.json`,
with final readiness in `promotion-final-readiness.private.json`. The older
production-journey receipt and qualification helper describe earlier attempts;
do not repeat their stale apply or promotion sequence. No public starter-offer
default was switched here and a new-customer-from-zero Kit journey is not claimed.

## Proposed Two-Environment Quota (Not Implemented)

The useful unit is customer WORKSPACE, not individual login: team members must
not bypass the allowance by using another account in the same workspace.

- Default allowance: two provisioned deployment environments, normally one
  customer staging and one production. A logical deployment with both counts2.
- Creating a configuration/Kit draft is not starting infrastructure. Additional
  Engage apps sharing existing runtimes do not consume environment slots.
- Reserve a slot atomically when apply starts, so simultaneous requests cannot
  exceed2. An upgrade/replacement of the same environment keeps its existing
  slot; it does not require a third slot for temporary rollout overlap.
- Stopped/failed resources still occupy a slot while owned infrastructure
  remains. Successfully released/removed infrastructure frees it. Otherwise a
  stop/create loop can leave unlimited databases, disks or vector services.
- Archived audit-only records do not occupy a slot after their owned
  infrastructure is released. Do not turn immutable historical evidence into
  a requirement to purge records before a customer can deploy again.
- Show used2/limit2 and links to manage existing environments. Retain a normal
  owned-resource removal path and an integrator override for agreed larger
  customers. Do not rebuild the retired capacity/recovery system for this.

This quota reduces new resource sprawl, but does not repair old stuck deletion
records or make an irreversible deletion confirmation unnecessary.
