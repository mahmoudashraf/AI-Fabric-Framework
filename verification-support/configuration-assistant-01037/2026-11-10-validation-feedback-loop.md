# Bounded Configuration Validation Feedback - 10 November 2026

## Status

Implemented locally; hosted rollout and actual-provider/UI qualification are
pending. No framework change or release. Source remains uncommitted at this
checkpoint; serving identities are still those in the earlier hosted closeout.

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
45 unit tests, 27 assistant browser groups, 13 actual-Support fixture groups and
the full TypeScript/Vite build. Existing build warnings remain unchanged.
These are controlled fixtures, not live model quality or a full release matrix.

## Review Finding Resolved

Independent read-only review found a paid-invalid-output receipt race: a
cancellation or scope change could throw before the guarded attempt transaction
persisted the original receipt. The worker now retains that receipt before the
guard and passes it to failure recording, without retry/save authority. A focused
regression proves CANCELLED state, retained receipt, one inference and no save.
The reviewer confirmed the correction by inspection; the final suite passed it.

## Hosted Qualification To Complete

- Commit/build immutable backend, Console and private authoring image identities.
- Upgrade staging first, retaining authoring allocation/store identity and ledger.
- Prove real provider turns and visible UI policy/attempt history; record actual
  attempt counts rather than claim that every query needs a correction.
- Use controlled integration/browser evidence for deterministic invalid-first,
  corrected-second execution; never inject a fabricated hosted provider success.
- Prove off-policy, review/undo and cancellation without publication/business calls.
- Roll out and repeat the bounded production canary, clean exact test resources
  and sign out. Existing customer serving runtimes are not upgraded for this work.

## Next UX Is Separate

010.37 section 19 proposes page-specific welcome text and document-first commands
with an optional hint. Existing task/configuration/catalogue/source context is
already supplied, but the UI still requires a typed request. That follow-up is
not implemented by the validation-feedback extension, and navigation/import must
not start a paid inference. The recorded assistant video predates this extension
and correctly demonstrates explicit user follow-up rather than automatic repair.
