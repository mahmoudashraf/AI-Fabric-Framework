# Engage Logout Correction And Promotion Follow-Up

Date: 4 November 2026. Status: LOCAL LOGOUT FIX QUALIFIED; HOSTED ROLLOUT PENDING.

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
  Immutable source/health and real provider/browser checks follow deployment.

## Promotion

Read-only review found no backend promotion defect. Existing Support staging
release rel-2ab687cf is APPLIED_VERIFIED/PASSED; its consumer remains on production
rel-09c92f90. Reapplying staging would repeat work. Northfield already has separate
staging/production assignments and both releases use the same version.

A narrow Console gap permits reviewing a production candidate with a connection
still assigned to production; the backend then rejects it correctly. The UI
correction is being qualified separately. Actual Support production replacement
is maintenance of its existing runtime, not a traffic-isolated staging test;
any apply and live-assignment change must be recorded distinctly.

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
- Show used2/limit2 and links to manage existing environments. Retain a normal
  owned-resource removal path and an integrator override for agreed larger
  customers. Do not rebuild the retired capacity/recovery system for this.

This quota reduces new resource sprawl, but does not repair old stuck deletion
records or make an irreversible deletion confirmation unnecessary.
