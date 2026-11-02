# Support Quality Profile Qualification

## Latest: 2026-11-02 Improvement Cycle

Approved compiler, evidence-projection, clarification and prompt fixes are
implemented. AI Fabric 0.8.44 is published and the existing staging Support
installation now uses `ver-704e28d5` / `rel-abaecf27`, binding revision 12.
Its exact immutable image and verification are in the
[execution record](2026-11-02-improvement-cycle.md).

The identical nine-turn suite improved from **6 pass / 2 partial / 1 fail** to
**7 / 1 / 1**. Observed median time fell from 11,984 to 5,370 ms, but this is a
small stochastic sample, not a performance guarantee. Six supplementary turns
were **2 / 2 / 2**: wrong-issue follow-ups remain and one specialist asserted a
negative policy finding without inspecting the actual answer/storage content.
Two additional diagnostic turns were useful, but selected direct READs rather
than a completed worker chain. No successful hosted worker-chain projection is
claimed from that probe.

**Production Support is now upgraded in place after explicit owner acceptance
of the remaining semantic limitations.** It uses `ver-23e915cb` /
`rel-b25e08d3`, `APPLIED_VERIFIED/PASSED`, with the same installation at binding
revision 3. The production nine-turn check was **7 pass / 1 partial / 1 fail**;
the wrong-subject follow-up remains unresolved. Both documentation-only
specialist executions completed successfully with structured findings. Actual
Desk claim/reply, visitor delivery, no AI during human control and explicit AI
return passed. The first handover runner's non-monotonic presentation-count
assumption failed; original evidence is retained and the corrected message-ID
check passed in a fresh run. No runtime change was needed for that test fix.

Recommended global template defaults remain unchanged. New generic `1.0.3` /
internal `1.0.2` settings are reserved as source-only candidates, not published
defaults. No lexical correction, hidden answer replacement or silent search-all
was introduced. Production verification cases were resolved and diagnostic
consent revoked. This is acceptance of the existing Support deployment, not an
all-green semantic result or a fleet/template-default promotion.

See the [primary review](2026-11-02-staging-review.json) and
[paraphrase review and remaining recommendations](2026-11-02-paraphrase-review.json).
The [production review](2026-11-02-production-review.json) records the fresh
production sample after the owner's acceptance.
Earlier failed runs and original reviews below remain historical evidence.

## 2026-11-01 Decision

The tuning cycle is complete, but **the candidates are not accepted as defaults
for all new production Support deployments**. Staging improved modestly;
production did not establish sufficiently reliable compound/follow-up behavior.
Keep current production customer TEMPLATE `1.0.1` and internal TEMPLATE `1.0.0`.
The candidate settings are reserved in versioned source and the developer-staging
catalogue, not discarded or silently promoted. See `production-review.json`.

The existing production LoomAI Support deployment has been upgraded in place to
the tuned configuration and the already-qualified 0.8.43 runtime. Its installation
and identity are preserved. Successful infrastructure/readiness and Desk checks
must not be mistaken for an all-green answer-quality result. No global defaults
were changed and no new framework release was made.

## Purpose

Qualify the reusable conversational settings previously tuned for the dealership
on LoomAI customer support. The internal support service is not the retired Acme
demo. Preserve company-specific sources, specialists, diagnostic consent,
authentication, handover and existing deployment/app identities.

## Execution Checklist

- [x] Read the actual production support binding and current deployment versions.
- [x] Capture a nine-turn, four-conversation staging baseline using normal
  authenticated support-case routes and the existing 13-guide library.
- [x] Publish a bounded staging candidate using the existing qualified AI Fabric
  0.8.43 runtime image. Preserve the original model, embeddings and vectors.
- [x] Apply/verify the first candidate and reconnect the existing installation.
- [x] Repeat identical quality questions and manually review evidence, uncertainty,
  consent and follow-up continuity as well as latency.
- [x] Verify handover/return separately; do not confuse AI specialists with agents.
- [x] Capture candidate settings in versioned reusable/internal TEMPLATEs; publish
  on developer staging and qualify actual fresh bootstrap and original reset.
- [x] Upgrade production in place and verify normal Console support routes and
  actual Desk handover/return.
- [ ] Production default promotion: **NOT ACCEPTED** on current semantic evidence.
  This is a deliberate quality hold, not a request for storage permissions or
  another capacity/recovery project.

## Candidate

- Existing GPT-5.4-mini selection unchanged.
- Existing executor `ITERATIVE`, `maxIterations=2` and
  `RAG_IF_ACTIONS_INSUFFICIENT` retained.
- READ planner context: 12,000 characters.
- READ evidence: 8,000 characters and at most 10 items per action.
- RAG generation context: at most eight documents and 8,000 characters.
- Clarification prompt guides the model not to ask customers for internal
  knowledge-domain/vector-space identifiers.
- Similarity threshold, source grants, specialist topology and consent unchanged.
- No dealership actions, stock identifiers or two-domain fan-out copied.
- No lexical semantic routing, silent fan-out or fabricated fallback answers.

These are candidate defaults, not a promise of identical semantic quality for
every future customer. Source quality, model, retrieval provider and integrations
still matter. Template settings remain editable bootstrap defaults.

## Runner

```sh
node verification-support/support-quality-01054/run-quality.mjs \
  staging /absolute/new/output-directory /absolute/private/adapter.mjs
```

The private adapter exports `request(environment, route, options)`,
`selectedResources(environment)` and `attest(environment)`. It supplies the
existing signed-in qualification identity, never a saved bearer token in this
repository. The runner creates its own marked support cases, uses normal
`start`/conversation-message routes and resolves its cases to revoke consent.
Do not rerun a directory with an uncertain request. Inspect existing case and
conversation receipts first. Each run has a fresh directory and idempotency keys.

`scenarios.json` contains human review criteria, not text-matching pass/fail
validators. Read original responses, source evidence, action receipts and
conversation state. A successful HTTP request is not a semantic pass.

An optional fourth argument selects explicit scenario IDs, for example
`installation,compound-investigation`. Use a new output directory, predeclare
the fixed repetition and retain the original results. This is a measurement
tool, not an automatic retry/fallback in the customer product.

An optional fifth argument supplies an absolute scenario JSON path. The fixed
`followup-paraphrases.json` and `specialist-contract-probe.json` supplement, never
replace, the original suite. Their judgments require reading the full actual
response and action/plan evidence; no query-term pass/fail grader is provided.

## 2026-11-01 Baseline

Staging original: `dep-a2706601`, `ver-516b99fa`, `rel-e3b9b047`, AI Fabric
0.8.41. Production is still unchanged at this checkpoint.

Nine real turns completed and all four qualification cases were resolved.
The baseline reproduced an internal-domain clarification on the compound
custom-domain/older-policy question. Its follow-up did not reliably retain the
specific second issue. A separate no-consent specialist run reported
`CHAIN_RESULT_PROJECTION_INVALID`; the outer response disclosed the failed
specialist and used approved document guidance instead. Do not relabel that
specialist error as a success just because the outer response was useful.

Raw receipts: `/private/tmp/loomai-support-quality-baseline-20261101/report.json`.
Deployment snapshots/intents: `/private/tmp/loomai-support-quality-ops-20261101/`.

An initial published-but-unapplied candidate changed the model alias to a dated
snapshot and received `reindexRequired=true`: the current comparison considers
provider changes, not just embedding changes. It was never applied. The final
candidate preserves the original model, publishes as `ver-b7aa98ff` and has
`reindexRequired=false`. No vector deletion, reindex, flag override or compiler
change was used to continue.

## First Candidate And Bounded Refinement

`ver-b7aa98ff` / `rel-d2927815` passed normal staging verification and the existing
installation was connected at binding revision 10. The same nine questions were
run against the same source library. This was not accepted for production:

- The compound request no longer stalled on a top-level internal-domain question,
  but its specialist returned a missing-item clarification and the answer lacked
  useful current readiness/index observations.
- A previously successful refresh-procedure follow-up became `OUT_OF_SCOPE`.
  The trace explicitly records a valid model-selected `OUT_OF_SCOPE`; a hard
  validator did not override the model into that result.
- A no-consent specialist still reported `CHAIN_RESULT_PROJECTION_INVALID`.
  Documentation help remained available and no private observation was claimed.
- The slowest complex answer grew to 36,378 ms. This is not a latency win.

The second candidate `ver-8426cead` / `rel-7b8c8e5f` adds narrowly scoped prompt
guidance: documented how-to help does not require a corresponding write action;
straightforward documentation need not invoke private diagnostics; follow the
actual inputs of installed READ tools rather than inventing target parameters.
Internal specialist instructions clarify that the existing parameterless
case-scoped tools resolve authorized selected resources on the backend.

It also enables the configured knowledge overview and **explicit** missing-source
recovery bounded to the single authorized `document` domain. This is not silent
search-all, never grants a new source, and never overrides a valid selected source.
Thresholds, consent, specialist topology and all side-effect boundaries remain
unchanged. This second candidate is not yet quality-accepted at this checkpoint.

Raw first-candidate receipts:
`/private/tmp/loomai-support-quality-candidate-20261101/report.json`.

## Refined Staging Result

The refined release passed normal verification and was assigned to the unchanged
installation at binding revision 11. Manual review of the identical nine-turn
set: **6 pass / 2 partial / 1 fail**, compared with **5 / 3 / 1** at baseline.
Median end-to-end time was 11,984 ms versus 12,169 ms; the slowest/p95 observation
was 20,501 ms versus 23,332 ms. This is a modest quality improvement and a small
sample, not a broad performance claim. See `baseline-review.json` and
`refined-review.json` for every judgment.

The internal-domain stall and the first candidate's out-of-scope how-to regression
did not recur in this refined set. Remaining issues are explicit:

- The READ planner resolves the ordinal follow-up to the wrong preceding issue,
  despite receiving two untruncated native history messages. Its diagnostic
  retrieval query and specialist question are model-authored; no text-matching
  correction was inserted.
- An explicitly requested no-consent resolution still has a failed specialist
  projection. A second invocation succeeds and documented help is provided. This
  does not mean the failed invocation or its empty findings were repaired.

The actual assigned Desk agent claimed the new test conversation, replied through
Desk, and the reply appeared in the visitor conversation. A visitor message during
human control created no AI turn. Explicit return then produced a useful AI
follow-up. Its case was resolved and consent remained revoked. The first harness
attempt wrongly required HTTP 200 for the documented asynchronous 202 handover
admission. That accepted request was read back, cancelled and resolved without
replaying it; the corrected full run passed. No product authentication or handover
implementation changed.

Raw refined and handover receipts:
`/private/tmp/loomai-support-quality-refined-20261101/report.json` and
`/private/tmp/loomai-support-quality-handover-staging-20261101-r2/report.json`.

## Template Implementation

Developer-staging candidates are customer TEMPLATE `1.0.2` and internal LoomAI
TEMPLATE `1.0.1`. Both carry the same non-secret quality settings from
`product-templates/ai-support-workspace/fixtures/balanced-support-quality.json`.
The internal template alone declares the diagnostic ACTION dependency and areas;
assistance starts disabled in both until the owning installation is configured.

The narrow control-plane patch accepts declared existing runtime setting leaves
and standard support prompt defaults at TEMPLATE bootstrap. It reuses the current
draft value contracts, merges only declared leaves, and captures the initial
defaults normally. Subsequent template synchronization preserves customer edits.
No new runtime service, schema migration, framework release or semantic validator
was introduced. Non-secret initial model selection is a default, never a funding
grant or permanent pin.

Focused qualification: 122 backend tests passed with no failures/errors/skips
across manifest, bootstrap, template sync, actual source manifests and specialist
verification-pack tests; 12 Node composition/library/default-isolation checks
passed. The subsequent actual bootstrap, production checks and quality hold are
recorded below.

## Hosted Closure And Remaining Quality

- Staging control-plane backend source `e6cbc35f834cf6f31d3f3d09876a3a2cab5c9ea4`
  passed exact Coolify job `u10xoa2jtt57wg8ul4wuivnt` and native health/source checks.
  Core image CI `41355355888` passed.
- The same functional backend source passed exact production Coolify job
  `uyajbzc8y317yk31tj4c3dpn` and native health/source checks. Console, Desk and
  unrelated runtime services were not rebuilt or redeployed for this patch.
- Developer-staging candidate versions: customer `mkv-df908fe5`, internal
  `mkv-d1cd604a`. Their served manifests match source exactly.
- Fresh customer candidate bootstrap created only draft metadata
  `dep-abb47160`, inherited the declared prompt/provider leaves, kept assistance
  disabled and did not install internal diagnostics. A deliberate prompt-budget
  edit followed by normal `template-reset` restored the recorded defaults. The
  draft was then archived. No service, vector index or database was deployed.
- Early harness requests used an unsupported single-deployment GET, an external
  mode for a Lucene base and an already-bound tenant. Those were caller mistakes,
  not product fixes. Native API rejection and no-created-record reconciliation
  were preserved. The successful request allowed the normal fresh tenant scope;
  no tenant binding, authentication or storage rule was weakened.
- Production release `rel-a1a27bce`, version `ver-92ffca64`, passed verification
  and was assigned to unchanged installation
  `awi_pub_bb57aa3822a176ee770d93cf2e8a31e7` at revision 2.
- The nine-turn production run was **6 pass / 1 partial / 2 fail**. A single fixed
  four-turn repetition of the affected conversations was **2 / 1 / 1**. Both
  original failures remain counted; the 44,100 ms compound tail is also retained.
  The DNS answer varied; the wrong ordinal subject recurred. These observations
  supersede the earlier tentative default-adoption decision.
- Actual assigned-agent Desk claim/reply, visitor-visible asynchronous reply,
  suppressed AI during human control and explicit AI return passed on production.
  All generated cases were resolved and diagnostic consent was revoked.

Production quality remains a separate, visible issue. Next work should investigate
the model-authored READ question/retrieval query and specialist result projection,
not insert lexical reference recovery, force every source, hardcode product
answers or start another infrastructure/recovery redesign. Any further framework
change needs its own bounded analysis/verification rather than being hidden in
this configuration comparison.

Final native readback confirms both support runtime/connector pairs and both
control-plane backends healthy, the exact support releases still verified, and
production starter versions unchanged. The new candidate versions return 404 on
the production catalogue because they were deliberately not published there.

One final integration-readiness check initially returned 503 for the agent
roster. The Desk binding was still awaiting its normal post-apply status
reconciliation. The existing customer-owned
`GET /api/deployments/{id}/desk?targetProfileId=...&releaseId=...` reconciled the
already verified release to `ENABLED`; both roster routes then returned 200.
No role, credential or database status was manually changed. API-driven upgrades
must include this existing readiness check, not just app readiness. Automatic
post-apply reconciliation should be considered separately; this run does not
claim that the product now does it without a status read.

Private receipts and helper snapshots are also preserved under
`~/.codex/private/loomai-support-01054/quality-20261101/` (original temporary
directory names retained inside). No credentials are committed here. The source
changes after functional commit `e6cbc35f8` record the measured quality hold and
verification tools/tests only; they do not require rebuilding unchanged product
binaries or releasing the framework.
