# Repair, Evidence Planning And Answer Consistency

## Scope And Current Status

The owner approved the recommended order: repair preservation, READ evidence
planning, then final-answer consistency and measured qualification. Keep the
existing routing model, inference/repair budgets, source restrictions, authority and
confirmation rules. No generic critic loop or answer replacement is authorized.

Implemented, published and adopted by the existing public Account Resolver demo
as0.8.57. Native artifacts/settings, live API conversation and desktop UI journey
are verified. The fixed live answer sample is11/12, not perfect. This work does
not roll out Platform or Support. See the remaining quality limits below.

Public worktree: `~/.codex/worktrees/planning-answer-evidence-0857`, branch
`codex/planning-answer-evidence-0857`, based on immutable f0169061. Preserve the
unrelated dirty public checkout and private repository changes.

## Confirmed Causes And Changes

1. Recovery preserved an entire original intent when a repair changed an already
   validated source. That also restored rejected null execution decisions over
   the model's valid completed candidate. Old post-preservation diagnostics made
   this look like repeated model omissions. Commit467ce649 accepts a completed
   Boolean only for its original typed missing-field issue, preserves validated
   choices and runs final validation. No invented default or added repair call.
2. READ planning saw an extractor-authored tentative answer beside actual
   evidence. Commit9cca4ecf excludes that draft and supplies per-iteration counts
   of completed/grounding-usable READs, conversation messages and target context.
   The model still chooses READ, RAG or context-only continuation within policy.
3. Forced direct READ generation had facts and an instruction to answer the
   request, but not the current question. Commit5ffb9c78 passes the processed
   question into that existing call; non-forced write narration is unchanged.
4. The generation path could receive correct facts and still produce a
   contradictory or unsupported conclusion. Shared guidance and the Account
   overlay distinguish current blockers, satisfied conditions and unknown checks.
   Commit52449585 strengthens mandatory-prerequisite and separate-check guidance
   and puts the example's outcome rules before its evidence. No text matcher,
   second generation call, critic loop or business-state computation was added.

Account extraction overlays also explicitly require the existing execution
decisions and distinguish checking eligibility from placing an unavailable order.
The source catalogue and two-source recovery ceiling remain unchanged.

## Verification Before Final Candidate

- Real-validator repair regression: 10 cases, nine failed before the fix; the
  no-model-repair control passed. Focused repair suite50 passed afterwards.
- Focused planner/source/authority suites95 passed. An initial fixture left RAG
  enabled while expecting NONE; fixture corrected, failing log retained.
- Question-handoff regression failed only its forced READ case before the fix;
  unchanged write control passed. Related generation suites73 passed afterwards.
- Full post-handoff release reactor30 modules/core1,165 passed. The initial
  reactor's two prompt-overlay fixtures needed the new summary placeholder;
  assertions were not weakened. All logs retained.
- Local Account53 plus smoke13, execution consumer5, vector18 unit plus8 actual
  container tests, minimal consumer and integration compilation passed.
- Initial native provider corpus24/24 passed with gpt-5.4-mini, including public
  policy paraphrases, no-tool greeting, authentication, confirmation, rejection,
  no-confirmation write, history and indexed retrieval. No scenario was skipped.
- Release guards passed after the public skill's mechanical version alignment.

## Failed Samples Retained

The fixed 12-case corpus was declared before calls: ready, missing-payment and
missing-address accounts each receive three eligibility questions; the ready
account receives three questions for unprovided statuses. Every question gets a
fresh conversation. These are read-only checks, with actual readiness retained.
Manual semantic review is separate from HTTP/routing assertions.

- Public 0.8.56 baseline: 8/12. Ready-why invented an address blocker; payment-can
  ignored missing payment; address-can confused eligibility with order placement;
  identity returned unrelated profile fields instead of an evidence limitation.
- First 0.8.57 candidate: 9/12. Payment-can and address-can had positive openings
  followed by required blockers. Identity invented a status from payment/address
  checks. The seven-turn payment journey passed routing, confirmation and receipt,
  but its final why-answer was still contradictory. Not qualified for publication.
- A single diagnostic provider-proxy turn captured the exact generation request
  and raw response: the correct question, current profile, policies and updated
  overlay reached OpenAI, and the contradiction came back from the provider. It
  was not introduced by the sanitizer. No authorization headers were recorded.
  The first trace harness session raced data seeding and failed before any AI
  call; after the seed summary, one turn produced the trace. No passing retry
  replaced a failed quality sample.

The revised candidate uses separate `candidate2` evidence and the unchanged
gpt-4o-mini model. It also scored9/12: all three ready-account answers invented
blockers or contradicted themselves, while all blocked/unknown cases passed.
Prompt changes shifted errors rather than qualifying this answer model.

The owner then explicitly approved qualifying `gpt-5.4-mini` for answer generation
only and deploying it only if quality improves. Candidate3 uses that existing
per-purpose configuration while keeping gpt-4o-mini routing and call budgets.
This is a model-plus-prompt comparison, not proof that prompts alone fix quality.
No small sample is a population reliability guarantee.

## Additional Catalogue Contract Finding

The second native run was22/24. Both failures executed the public READ but then
asked for a source in parallel-RAG mode. The model chose EXECUTE_READ_ACTIONS
without a source; the server policy required RAG anyway. Its catalogue had
advertised READ-only as an available choice even though execution adds RAG.

Commit9daffb51 aligns the advertised choices with the effective policy and guides
combined READ-plus-RAG selection in the same planning turn. The new real-policy
regression failed before the fix, then the focused78 checks passed. Legacy
decisions still follow the same execution policy; no source is invented and no
extra repair call or semantic rejection gate is added. Optional/no-index READ
and history-only paths remain. The failed source sample is retained.

## Verification Limits

The fixed live sample has one misleading affirmative opening for an unknown
identity-verification status; its following explanation correctly states the
status is not reported. All nine ready/blocked eligibility questions passed.
Some answers still expose technical Boolean names. No sample proves universal
model reliability, and no failed question was retried into a passing result.

The older Smart Suggestion card can repeat the profile read and show an empty
context result after a correct main answer. The retained0.8.56 baseline already
contains this same legacy suggestion result. It is not a failed account update;
that separate path was not rewritten in this bounded release.

Broader pre-existing Incident Room14 failures remain outside this scope and are
not relabelled as fixed or hidden by changing assertions. Exact baseline comparison
is recorded below. Mobile verification covers initial load, not the full journey.

Raw evidence and private helpers: `~/.codex/private/loomai-framework-0.8.57/`.
Do not put native environment snapshots, keys or authenticated payloads in Git.

## Final-Source Qualification

The owner separately approved prompt-role separation after a stronger answer
model alone also scored8/12. Commitce0ab223 sends static generation policy in the
system role, keeps questions/evidence in user content and history in native
messages, and retains the text helper's existing token cap/purpose routing and
single provider call. Dynamic system placeholders are rejected before a provider
request, protecting the instruction/data boundary. This is not an answer rewrite
or critic loop. Existing user-template/managed-preview content is not promoted.
The approach is consistent with [official OpenAI role guidance](https://developers.openai.com/api/docs/guides/prompt-engineering).
Model support was checked against the [official mini model page](https://developers.openai.com/api/docs/models/gpt-5.4-mini),
but documentation is not a guarantee of this application's measured quality.

Role regression2/2 failed before implementation. Full role-source reactor passed
30modules/core1,171; guards passed. A first unit run's7 older mock signatures and
one generic test marker collision were corrected without weakening output or
call-count assertions. The final native run passed24/24 and the available
OpenAI/ONNX/Lucene matrix passed. Prior failing native samples remain retained.

Both role-only models scored11/12:5.4 passed all9eligibility questions but invented
identity status;4o still contradicted ready-why while passing the unknown cases.
The stronger model's7turn payment journey had correct post-update conclusions.

App-only commit0f185ac0 then declares profile source coverage in its description
and facts. Identity status, fraud results and order history are not reported;
that declaration is not an actual failed or satisfied business check. A new
regression failed before this addition, then all55Account plus13smoke tests passed.
No framework code changed after the full role-source qualification. Candidate6
and7 compare both answer models with this final evidence format before selection.

## Selected Release

Candidate6's fixed12 passed, but its seven-turn opening hit TARGET_REQUIRED during
completion. Later turns succeeded. The structural harness had omitted an opening
success assertion; that gap was corrected, and this sample is explicitly failed,
not a full journey pass. App-only e4cca587 supplies the missing completion overlay
with current-account scope and existing execution decisions. Default/prod regressions
failed before the file existed, then all app tests passed. No new repair round.

Final same-source comparison: candidate8 answer5.4 scored12/12 and its stricter
seven-turn journey passed; candidate7 original answer4o scored11/12 and still
contradicted ready-why. The owner's conditional model-promotion requirement is met.
Routing stays4o; only the example's answer model is pinned to the observed tested
gpt-5.4-mini-2026-03-17 snapshot. App default/prod pin tests and the remaining
generation-routing integration adapter passed:57 app plus13 smoke,2 integration.
Some responses can still expose technical field names; no fixed sample proves
future model certainty. No Platform/Support model or runtime is changed.

Immutable release source c25a854d63e39fae7b52495085aa72c4cf5e813e, tag
ai-fabric-framework-v0.8.57. GitHub release453493860 triggered exactly one Central
workflow44876213643 at06:34UTC. Final guards passed. No tag movement, manual
parallel publish or duplicate upload. All local comparison JVMs are stopped.

## Published-Artifact Verification

Central became public at06:53UTC; publication44876213643 completed successfully.
A fresh isolated consumer cache excluded local0.8.57 artifacts. Its Account57
and smoke13 tests passed. All12 packaged framework jars have public-central-only
origins and recorded hashes; core sources and javadoc are also public.

The packaged default, without generation-model environment overrides, passed
all12 fixed questions and all7 journey turns. Manual review confirms correct
ready/blocked/unknown conclusions, typed form, confirmation, actual update
receipt and correct current-state follow-ups with retained native history. No
failed question was retried. Some Boolean field names remain in answers.

The consumer JVM was stopped. Public main then fast-forwarded normally from
f0169061 to c25a854d; no force push or tag movement. Account-only image CI was
requested through the existing pipeline; the completed hosted results follow.

## Hosted Adoption And Live Verification

- Image CI44877858058 passed. Only Account Resolver was explicitly selected;
  no serving-host build, duplicate deploy, new app or global timer pause.
- Existing updater requested deployment ssgi0bkkhufkvxnesmiyyoje at07:02UTC;
  the normal deployment finished and existing app prjnwmfsk6bva2r2lxnq6lwh is
  healthy. New container55bf93307f62, image digest
  `sha256:33bfc5f6fda319fd935a2371655a5952d4a0cbc1fa9018cd9fe39d58c6bd349e`.
- Final07:08UTC readback: updater receipt phaseDONE for that exact tag/deployment;
  health stillUP57/sourcec25a854d at07:08:29. No duplicate deployment.
- Native OCI revision is exact releasec25a854d. All12 embedded framework library
  hashes match the clean public-consumer artifacts, not just the version string.
  Core hash `3b2a9929e8ed22ad78effd385500aa009fb2e32cc748f32283f12bfe755c6e99`.
- Exact environment rows, provider key/routing model, database, JVM settings,
  CPU/memory/ports/restart policy, mounts, app identity and hostname preserved.
  No migration, vector copy/reindex or private Platform/Support upgrade.
- Health07:03:36UTC: UP/framework0.8.57/sourcec25a854d/branchmain. Actual live
  query metadata confirms routinggpt-4o-mini-2024-07-18 and packaged answer
  modelgpt-5.4-mini-2026-03-17, with no generation-model environment override.
- Fixed live12 questions:11pass/1fail by manual semantic review. All9
  ready/payment/address variants pass; fraud and order-status uncertainty pass.
  Identity answer starts with an ambiguous Yes, then correctly says unavailable;
  retained as a failure. No invented identity proof or unauthorized action occurs.
- Independent live API7turn payment journey PASS. Actual browser7turn journey
  also PASS with typed last4 form, required confirmation, executed receipt and
  correct post-update eligibility/profile/why. Native history counts8/10/12,
  approved policy source retained, no internal domain/attachment-target request.
- Desktop1440x1000 and mobile390x844 screenshots inspected. No JavaScript errors;
  phone viewport and scroll width both390. Phone initial load only. Desktop's
  pre-existing legacy suggestion limitation is described above, not hidden.
- Broad frameworkCI44877849609: provider registry and vector containers PASS;
  Maven libraries PASS, real-app suite fails on exactly the same14 Incident Room
  methods as baseline44629351707. Added0/removed0; method names AND summary lines
  match. This is not an all-green broad-CI claim or a new Incident Room fix.

Private proofs: public-consumer-proof, account-native-proof, quality-live-review,
journey-live-review, browser-live-review and incident-baseline-comparison under
the private evidence root above. All comparison/consumer JVMs and test browsers
were stopped. No keys or authenticated native snapshots are in this report.

Live demo: https://ai-fabric.dev/demos/ai-fabric-account-resolver
