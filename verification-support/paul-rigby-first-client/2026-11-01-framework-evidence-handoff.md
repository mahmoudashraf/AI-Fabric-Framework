# Focused Framework Conversation Evidence Handoff

Date: 1 November 2026. Status: FOCUSED FIX VERIFIED LIVE; broader CI exception below.

## Scope

Fix conversational references losing their evidence between intent extraction,
READ planning and answer generation. Reuse approved, owner-bound history and
existing target references. History is a transcript, not a new fact ledger or
permission grant; fresh authorized evidence retains precedence. No storage,
schema, service, keyword routing, implicit search-all or hidden query rewrite.
Do not change the separate compound-delivery or policy-duration behavior here.

## Execution

- [x] Reproduce alternate-pair failure on the adopted 0.8.42 baseline in UI/API.
- [x] Isolate framework work from unrelated dirty indexing/execution changes.
- [x] Review existing native-message, target, provenance and scope contracts.
- [x] Pass bounded native history and existing target context to READ planning;
  carry optional model-authored self-contained retrieval query explicitly.
- [x] Pass bounded native history to generation without dropping it on fallback
  or upgrading historical claims to fresh/authorized system evidence.
- [x] Focused deterministic contracts and real-provider repeat conversations;
  retain failures and separate security/execution from semantic quality.
- [x] Publish immutable patch and verify Central consumption of current product.
- [x] Normal staging apply, live API/UI qualification, then same production app
  upgrade and repeat both original and alternate-pair journeys.

## Acceptance

Aster E1/Northstar S4 comparison followed by the lower-mileage/body-style question
must preserve that pair rather than answer from Morrow C2/Aster E2. Test a changed
pair, pronoun variants, missing history, bounded/truncated history and fresh-read
conflicts. Prior messages stay native roles; they are never spliced into the
embedding query by deterministic code. Model query changes remain diagnostic.
Scope/owner mismatches, NEVER persistence and caller-spoofed metadata must not
expose history or grant hidden-ID authority. No new AI semantic hard validator.

Framework worktree: `/private/tmp/ai-fabric-conversation-evidence-20261101`,
branch `codex/conversation-evidence-handoff-0842`, base published 0.8.42
`c21702ac`, merged with remote main `84207902`. Initial inspection found four
published-release commits missing from main; the isolated branch preserves both
the released core fixes and main's later demo/docs/test-fixture changes. Only
README conflicted; its newer capability layout and release links were retained.
The existing dirty framework checkout was left untouched.

## Deterministic Qualification

Framework 0.8.43, commit `46e1563b`: 1,001 passing checks
(895 Core, 93 chat-session, 13 default-curated), no failures/errors/skips.
Release-profile source/Javadoc packaging and repository release guards passed.
An independent review caught the action-only caller converting a generation
failure into raw-evidence success; that history-bound path now returns an
explicit generation error, covered through `IntentHandlingStep`.

Publication run `41318269974` PASSED; tag/main are `46e1563b`, Central artifacts
are available. Its full signed reactor recorded 2,224 checks, zero failures or
errors, with two existing `JdbcPostgresqlLeaseTransitionTest` checks skipped
because that runner lacked Docker. Those unrelated lease checks are not claimed
as passed; the affected 1,001-check local run had no skips.

Product source `783e91946` is pushed. A new empty Central-only cache passed 887
checks (181 provider-accounting, 88 connector, 582 runtime, 36 vectorization).
The runtime contains 20 public framework JARs, all 0.8.43; Core/execution/default-
curated bytes match Central. Platform focused checks: 85 PASS. Runtime image CI
`41319818622` and backend image CI`41319818607` passed; UI build was skipped.

Both control planes are native healthy on `783e91946`. Artifact `dsa-9f53d542`
has digest `sha256:e0799fe37b618f19a89baeb98dde503412504b5f6fb7d6b299d129ff1f489ef9`.
The unchanged Northfield draft published as v9/`ver-ae54cd59`, validation 0/0,
no reindex. Staging release `rel-fca6e53a` completed its runtime and connector
replacement. Native runtime digest and admin overview prove 0.8.43.
The production runtime was kept on 0.8.42 until staging semantic qualification
passed; the final production result is recorded below.

## Staging Semantic Evidence

The unchanged staging release reached `APPLIED_VERIFIED/PASSED`; its existing
Engage app passed all 16 checks and moved to binding revision 3. Nine canonical
API questions and two actual UI questions passed the focused reference cases:
Aster/Northstar comparison and lower-mileage answer, pronoun, changing to
Aster/Morrow, original electric search and comparison. Missing history requested
the vehicle reference rather than a knowledge domain. A second anonymous
identity received 404 on the first identity's conversation. No business writes.

Real selected model: `gpt-5.4-mini-2026-03-17`. Debug readback shows matching
planner/generator native history counts 2, 4, 8. The changed-pair follow-up had
zero fresh sources and answered correctly from the earlier exchange; it did
not explicitly label those facts historical. This is conversational reference
success, not freshly revalidated inventory or perfect freshness phrasing.

Production normal apply `rel-69662734` subsequently passed against the same
deployment and version. No new customer deployment/app or database was created.

## Production Closure

- Same deployment `dep-3c4dbe4f`, v9/`ver-ae54cd59`; both staging and production
  are `APPLIED_VERIFIED/PASSED`. Physical runtime image/source and protected admin
  overview confirm 0.8.43 and product source `783e91946` on both targets.
- Existing production consumer `northfield-console-live-20261024` moved through
  normal revision-checked binding update to revision 14/`rel-69662734`. Staging
  remains revision 3/`rel-fca6e53a`. No template restrictions or gates bypassed.
- Existing production standalone app remains ACTIVE at
  <https://northfield-console-live.apps.loomai.pro/> and passed all 16 readiness
  checks. Its manifest and the existing embedded installation on
  <https://loomai.pro/demos/dealership-ai> resolve the same new assignment hash
  and production runtime. No URL, installation identity, branding or auth change.
- Nine production API questions and two actual production UI questions repeat
  the successful staging results. Across both targets: 18 API questions, 4 UI
  questions, two additional denied cross-owner probes, zero business writes.
  Missing-history cases correctly clarify the referenced vehicle; they are not
  successful information answers and are not counted as factual answers.
- Aster/Northstar resolves to Aster 4,850/SUV; changing the pair to Aster/Morrow
  resolves to Morrow 1,980/Hatchback. Original electric search and named comparison
  retain the correct 2,870-mile difference. Neither tested follow-up asks the
  visitor to choose an internal knowledge domain.
- Real provider remains OpenAI `gpt-5.4-mini-2026-03-17`. The historical-only
  changed-pair answer is not a fresh-read claim; the freshness-attribution caveat
  above remains. No live source mutation/conflict drill was performed.
- No new semantic text matching, authority bypass, implicit search-all, schema
  migration, reindex or deployment-setting change. Separate compound-policy,
  policy-duration and raw-source/detail/auth UI observations remain outside this
  fix; this is not full pilot/product-launch or full provider-matrix qualification.

Local UI screenshots:
`output/evidence-handoff-0843/staging-follow-up.png` and
`output/evidence-handoff-0843/production-follow-up.png`. Full signed/native/API
receipts are kept under the ignored private operation directory
`~/.codex/private/loomai-paul-rigby-01055/domain-routing/evidence-handoff-0843/`.

## Deferred Incident Demo Compatibility Issue

Framework main CI `41319820326` failed 14 checks in the separate
`IncidentInvestigationIntegrationTest`: access denied by policy before planner
or generation. Provider Registry and Vector Provider Container Contracts passed.
This differs from the successful immutable publication reactor and dealership
checks; do not describe broad framework CI as green.

Read-only diagnosis confirmed an inherited demo incompatibility, not the new
history patch. Commit `0a6611e1`, published in 0.8.40, preserves the interactive
initiator in `authContext.subjectId` and keeps the processed incident separate.
The demo's `IncidentAccessControlConfiguration.java:48` still compares the caller
to `scenario.id()`; `IncidentActionScopeResolver.java:55` repeats that conflation.
Main's prior demo POM was still 0.8.38. Those Java/resolver sources are identical
in published 0.8.42 and 0.8.43; failure occurs before history projection.

Deferred, separately scoped fix: authorize the interactive owner via the
server-owned unexpired session-to-scenario binding; resolve the processed
incident independently from typed trusted subject/binding; require matching
incident, tenant, deployment and existing scopes. Do not replace caller IDs with
incident IDs, relabel interactive calls as application calls or trust caller/
model metadata. Cover real interactive success plus owner/incident mismatch,
expiry, tenant/deployment and missing scopes; run the existing 28-test incident
integration class and four action-support tests. No unrelated framework patch,
authorization weakening, hidden skip or provider matrix was performed here.

The editable GitHub release guidance also records this known exception. The
immutable release tag and published artifacts were not changed.
