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
- [ ] Build images, apply to staging, compare live results and inspect the UI.
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
authentication weakening or CI bypass was introduced; the complete runtime
test suite is being rerun before publication.
