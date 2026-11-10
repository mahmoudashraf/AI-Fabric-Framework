# Bounded Configuration Validation Feedback - 10 November 2026

## Status

Implemented, committed, pushed and qualified on staging and production. No
framework change or release: AI Fabric remains 0.8.46. Existing customer runtime
images were not replaced for this shared authoring extension.

Final native serving identities on both hosts:

- Backend source `546da001a0586a087ea5ea96289f6ddc41571cb8`, image digest
  `sha256:884b3736472dede90925034f7c0f6ccbed7248df177bc091cd66441a31a075bc`.
- Shared private authoring source `546da001a0586a087ea5ea96289f6ddc41571cb8`,
  digest `sha256:3ba106bb0e17b434a272ccc7c194c6c677bad7f112355b5b9073cfaa51b4bff0`.
- Console source `44c5aee15d112f1978e42c8ee4b43eb5c9fb9473`, digest
  `sha256:551c5461f3dad77c9463a95ebdd68978813159de86e2b8c998b13eabc534fa8f`.

CI `42754742947` (core), `42754743131` (runtime verification/publication) and
`42757028157` (UI-only follow-up) passed. Normal image-only Coolify updates were
used; exact native container health, repository digest and OCI source were checked.

## Contract

- Explicit per-turn enabled policy and one/two correction limit; missing or off
  policy performs one inference. Operator ceiling and availability are enforced.
- One 140-second overall turn deadline. The private HTTP/assertion deadline is
  clamped to remaining time. Each attempt has a distinct accounting correlation.
- Feed actual configuration errors or a typed whole-JSON rejection back to the
  LLM with the original query and cumulative in-memory candidate. No lexical
  correction, canned values, provider substitution or warning-triggered retry.
- Stop on model questions/explanation/unsupported result, scope/authority failure,
  cancellation, deadline, provider/network failure or interrupted budget.
- Record attempts, findings, proposed changes and real provider receipts. Save
  final scoped changes once through existing services; preview remains unsaved.
  NEEDS_INPUT under enabled feedback never saves partial proposals. Invalid drafts
  remain visibly invalid where the normal save contract permits them.
- Manual editor stays paused for the turn; review/edit/undo follows. No automatic
  publish, apply, provision, indexing, assignment or business call.

## Local Verification

Final serialized backend command:

```sh
cd Platfrom/backend
mvn -o -q -Dtest='Authoring*Test,ConfigurationAssistantIntegrationTest' test
```

Result: **230 tests, zero failures/errors/skips**. Covers actual formal findings,
candidate accumulation, one save/undo, off/omitted policy, warning-only output,
bounded exhaustion, preview/idempotency, typed JSON correction versus budget
failure, unauthorized paths, NEEDS_INPUT (including partial changes), cancellation
and receipt retention. Runtime/client deadline and assertion boundaries are
included in the focused client tests.

An earlier overlapping backend invocation failed with no matching tests during
shared-target compilation. It was not retried with weakened Surefire settings;
backend builds were serialized and the focused suite rerun normally.

The runtime slice passed 69 focused tests. Final UI verification after natural-
language correction-stop labels replaced raw internal reason codes passed:
45 unit tests, 30 assistant browser groups, 13 actual-Support fixture groups and
the full TypeScript/Vite build. Existing build warnings remain unchanged.
These are controlled fixtures, not live model quality or a full release matrix.

## Review Finding Resolved

Independent read-only review found a paid-invalid-output receipt race: a
cancellation or scope change could throw before the guarded attempt transaction
persisted the original receipt. The worker now retains that receipt before the
guard and passes it to failure recording, without retry/save authority. A focused
regression proves CANCELLED state, retained receipt, one inference and no save.
The reviewer confirmed the correction by inspection; the final suite passed it.

A live staging resize also exposed panel-local input state being reset when the
desktop pane became a mobile Drawer. The scoped owner now retains unsent text,
selected documents/operations, preview/explanation and correction preferences
across that remount and dismissal, resetting on formal resource/task changes.
Three focused browser groups cover retention, scope reset and no turn on resize.
This is a narrow UI follow-up, not a new shared state/persistence service.

## Initial Staging Hosted Result

The real customer UI run `staging-1794284798937` passed document import, three
documented anonymous READ actions/routes saved with zero errors, visible attempt
history, corrections off (one inference), actual UI undo, preview cancellation,
390px document width, cleanup and sign-out. The enabled query passed on its first
attempt; it did not need a real-provider correction. Deterministic multi-attempt
correction/exhaustion is proven by the controlled integration/browser tests, not
misrepresented as an observed provider mistake in this sample.

The run made three real provider attempts, including the cancelled preview:
26,763 reported tokens / USD 0.025221000 ledger delta. Both store/allocation
identity and earlier counters survived upgrade. Its desktop capture shows actual
in-progress status, not a claimed final saved screen; the production capture
must wait for visible terminal UI state. The resize finding above is addressed
by the UI-only follow-up and final hosted control check below.

## Final Hosted Qualification

- Final staging control run `staging-1794285650100` passed imported-source,
  unsent-message, preview and off/one-correction preference retention across
  desktop/mobile/desktop. No turn was created; the actual ledger remained at
  26 attempts / 154,240 tokens / USD 0.147258750 before and after this check.
- Production real customer UI run `production-1794285808495` passed the same
  retention check, source import, three documented anonymous READ actions/routes
  with zero errors, visible terminal SAVED state and attempt history, corrections
  off/single attempt, UI undo, preview cancellation and 390px width checks.
  The enabled production query also succeeded on its first attempt. Do not claim
  that the live sample needed or exercised a second model correction.
- Each target used three real provider attempts (two completed configuration
  turns plus the cancelled preview). Actual runtime/accounting evidence is
  retained separately; cancellation is not a claim of zero provider usage.
- Incremental task usage: staging 26,763 tokens / USD 0.025221000; production
  27,051 tokens / USD 0.026742000. Combined six attempts / 53,814 reported tokens /
  USD 0.051963000. Final cumulative service counters are staging 26 attempts /
  154,240 tokens / USD 0.147258750 and production 13 attempts / 109,828 tokens /
  USD 0.106176000. These are actual attempts/usage, not semantic pass totals.
- Actual saved fields were inspected, and unrelated entity/provider/security/
  prompt/knowledge/shell/marketplace/behaviour fields were unchanged. Zero versions
  or releases were created; no business API, provision, index or assignment ran.
- Three exact test sessions were deleted with 404 readback and three
  configuration-only Brains were archived. Both customer UI sessions signed out.
  Desktop/mobile screenshots were visually inspected; raw data stays private in
  `~/.codex/private/loomai-feedback-loop-20261110/`.
- Shared authoring store, approval, limits, expiry and accounting identity survived
  both upgrades unchanged. Neither initialize/activate nor a ledger reset ran.

Both hosts' final Coolify jobs: staging authoring `p8fwx2v4t1hdjihh158krf8d`,
backend `eklbjxhj25ryatwn1o5fee14`, Console `aciwomc95v0y52wl689bcumw`;
production authoring `qhl59huhbj8gw1z2e9inkt1f`, backend
`jarwhv6dh6c7lvfolm7rb5vo`, Console `d13ew27gxfd2h6tetaekvcjt`.

This qualifies the bounded supported authoring path, not universal semantic
correctness or real business connectivity. Existing save-contract limitations
remain: some configurations lack pre-save checks; mandatory save rejections stay
visible. A cumulative proposal exceeding the existing 30-change bound stops
visibly, rather than being silently compacted. Page-aware document-first UX
remains the separately proposed next enhancement.

## Next UX Is Separate

010.37 section 19 proposes page-specific welcome text and document-first commands
with an optional hint. Existing task/configuration/catalogue/source context is
already supplied, but the UI still requires a typed request. That follow-up is
not implemented by the validation-feedback extension, and navigation/import must
not start a paid inference. The recorded assistant video predates this extension
and correctly demonstrates explicit user follow-up rather than automatic repair.
