# Support Quality Profile Qualification

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
- [ ] If improved, capture settings in reusable and internal support TEMPLATEs,
  qualify actual new-deployment bootstrap and publish new immutable versions.
- [ ] Upgrade production in place and verify normal Console support routes.

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

The source catalogue now selects customer TEMPLATE `1.0.2` and internal LoomAI
TEMPLATE `1.0.1`. Both carry the same non-secret quality defaults from
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
verification-pack tests; 11 Node composition/library/default-isolation checks
passed. Hosted new-template bootstrap and production closure remain below the
unchecked gates until their receipts are recorded.
