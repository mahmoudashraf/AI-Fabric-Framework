# Suggestion Reliability - 9 November 2026

## Scope And Status

Owner-approved 010.63 suggestions #1 and #2 only. No framework edits/release,
provider/model switch, semantic text matching, hidden successful fallback,
automatic search-all or business writes. Existing model/infrastructure limits
and normal release/assignment mechanisms remain in force.

- [x] Trace released 0.8.46 suggestion ownership and current widget transport.
- [x] Observe a fresh four-query staging baseline.
- [x] Remove widget attachment timers and fixed client fallbacks.
- [x] Distinguish authored starters and generated next steps; hide old controls.
- [x] Implement/qualify the opt-in runtime post-result extension.
- [x] Clean up the independent attachment-ideas endpoint and focused tests.
- [x] Build images, apply to staging, compare live results and inspect the UI.
- [ ] Adopt qualified runtime in production and repeat focused verification.

Checked items above describe actual completed work, not future hosted adoption.

## Source Findings

Immutable framework tag `ai-fabric-framework-v0.8.46`, commit
`218730e5b18343576655fbd7108d63b4135f0cfc`, currently copies extracted
`nextStepRecommended` into READ/RAG results before sanitized projection.
`SmartSuggestionsStep` enriches the existing recommendation using another RAG
call; it does not reconsider relevance from the completed answer. Prompt
overlays alone cannot replace the structured recommendation after generation.
The public pipeline extension supports the needed bounded runtime behavior.

`AIActionRegistry` has no caller-filtered metadata-list method. Capability
catalogue helpers require a real resolved profile; the ad-hoc endpoint lacks
that context and therefore must not expose the global catalogue as available
actions. Ordinary execution authorization and confirmation are unchanged.

## Baseline

Real anonymous canonical-message routes for the existing staging Northfield
installation. Same OpenAI model, executor mode, landing position, sources,
managed Contextual next-step profile and assignment as before. Four READ-only
queries, successful transport, no business writes or suggestion-specific retries.

Manual relevance assessment:

- Shortlist: a comparison suggestion is a useful next step.
- Comparison: suggestion asks for the comparison just answered.
- Indexed facts: suggestion asks for the indexed lookup just completed.
- Stock plus policy: both suggestions repeat the two requested lookups despite
  both facts being present in the final answer.

This reproduces provisional-versus-final context loss. No lexical quality
scorer or deterministic relevance filter was used.

## Local Widget Verification

Typecheck, canonical next-step projection tests, compound projection smoke and
the new browser lifecycle smoke passed. The lifecycle check exercises authored
starter changes, advancing beyond the old ten-second timer, normal query
selection, a newer turn hiding old buttons, and human-control suppression.
The first harness assertion incorrectly required an exact accessible name
without the existing confidence label; corrected the assertion, not the UI.
The deferred-query fixture was separated into a direct hook regression after
its browser mock did not bind; production code was not changed to fit the
harness. The direct hook test proves a delayed response cannot reactivate
suggestions after reset or a newer user turn while preserving its answer.

## Runtime And Profile Verification

The runtime pass uses the existing public pipeline SPI at order 67. It receives
only bounded sanitized query/answer/evidence, with at most 12 eligible public
capability descriptions. The non-null effective profile and real handler
permission checks gate that catalogue; no profile means no catalogue, not a
global-registry fallback. Optional generation is not a startup dependency.

It makes at most one generation call, returns up to two suggestions, and starts
no new retrieval. The output cap is 1,024 tokens or the lower configured
standard generation limit, leaving room for reasoning-capable models; this is
a ceiling, not required output length. Extraction-time hints are cleared under
the explicitly selected result-aware policy. No lexical relevance filter,
completion padding or fallback recommendations are used.

Combined local runtime verification passed 104 tests: 19 new-step, 52 independent
endpoint and 33 existing controller/preview tests. The new pipeline test covers
MetadataBuilding, SmartSuggestions, ResponseSanitization and the canonical runtime
response, including diagnostics and no extra enrichment RAG. Ten Platform profile
tests passed, preserving the Off default and exposing the additional-call cost.
The final 1,024-token ceiling is rechecked before source publication.

## Private Evidence

Raw response/model data and current configuration snapshots remain outside git:
`~/.codex/private/loomai-suggestions-20261109/`. Existing evidence/earlier quality
records remain historical; current source, images, release and hosted results
will be recorded here after qualification.

## Hosted Build And Auth Fixture Correction

Source `4e57f95d6` passed core image run `42712323180`. Both Platform backends
and UIs completed image-only deployment and report healthy containers at that
source. Runtime run `42712323175` was stopped by its normal test gate: 671 of
672 tests passed. The failing authenticated suggestion-endpoint test supplied
only a placeholder AI key and implicitly relied on the removed successful
fallback. No new runtime image was published from that failed run.

The auth integration fixture now supplies an explicit generated JSON response
and asserts its exact suggestion. A separate authenticated provider-failure
case asserts empty suggestions, unavailable status, no raw response and retained
caller identity. The focused 76-test recheck passed. No production fallback,
authentication weakening or CI bypass was introduced. The complete local runtime
suite then passed all 673 tests with zero skipped, including PostgreSQL-backed
tests. Corrective source `4dd738c75` is pushed; its normal runtime CI is
`42713707940`. The independent core images remain at `4e57f95d6` because the
correction changed only the runtime auth test fixture and this evidence record.

## Qualified Staging Observation

Runtime CI `42713707940` passed and published source
`4dd738c75eb9a4dff21ce6bc54724fec4f383311`, index digest
`sha256:dbce449f6e79c4755ff5450b835a12365858694bbf8302307f47d4d8f1416b66`.
Artifact `dsa-c812478a`, existing version `ver-689ff79c` and release
`rel-a5e3ff33` reached `APPLIED_VERIFIED` / `PASSED`. The existing staging app
and consumer were retained and rebound normally; native runtime source/digest
and the new pipeline step were observed. Existing connector credential scrubbing
adds another restart to the normal apply path; no deployment bypass was used.

The same four READ-only queries all succeeded, each reporting one result-aware
model call, a scoped capability catalogue and no enrichment retrieval. Durations
were 10.228 / 7.824 / 6.404 / 7.737 seconds, compared with baseline
8.490 / 5.778 / 4.780 / 5.796 seconds. These are single observations, not a
latency benchmark or an isolated measurement of model-call time.

Manual assessment, without semantic text filters or quality-specific retries:

- Shortlist: first suggestion usefully compares the two returned cars; the
  second asks for more matching stock despite an already complete shortlist.
- Comparison: suggestions move to missing family-use factors and a concrete
  use-case decision. The first may be unproductive because the answer already
  says some requested facts are unavailable; no new data capability is implied.
- Indexed facts: first suggestion compares against another stocked vehicle;
  the second mainly reformats facts already given.
- Stock plus policy: first suggestion advances to comparison/value; the second
  mainly repeats the delivery policy as a shorter summary.

This is an improvement in first-choice relevance, not a claim that every model
suggestion is useful. Redundant secondary reformatting remains an observed
quality limitation. No canned replacement, duplicate detector or repeated
generation was added to conceal it.

Real staging UI proof `staging-ui-1794265104487` passed a manually reviewed READ
suggestion click, ordinary query submission and same-conversation continuation.
Adding a vehicle to context followed by a 12-second wait made zero independent
suggestion requests. Only the latest completed turn exposed next-step controls.
Desktop/mobile screenshots were captured and inspected; no page errors or
document-level horizontal overflow were observed. Existing narrow mobile
suggestion labels still truncate; this task does not claim a mobile redesign.
The private harness initially kept its TTY open after successful completion;
EOF closed it and its stdin is now paused after selection. No UI/product fix
was made for that harness-only issue.

## Advanced Guidance Preservation

Before closing the rollout, review found that the new generation pass read the
managed opt-in but not the customer's custom next-step instructions. That would
let Advanced preferences influence provisional extraction but not the final
recommendations replacing it. The runtime now reads the existing managed
`aiBehaviour.nextSteps.instructions` and passes up to 4,000 characters as
deployment-authored preferences, subordinate to the output/trust contract.
Nothing is inferred from the wording, and no client-supplied context is promoted
to trusted instructions. Off and unmanaged configurations remain inactive.

Three regressions cover real configuration loading/custom guidance reaching
generation, Off/unmanaged isolation, and the size bound without semantic
interpretation. All 112 focused runtime/controller tests passed. This requires
another immutable runtime image and ordinary apply; it is not being hidden as
a prompt-only hosted change. The first production release `rel-b9a28355` at
`4dd738c75` already reached `APPLIED_VERIFIED` / `PASSED` and its existing app
binding was refreshed. Final closure will name the guidance-preserving source.
