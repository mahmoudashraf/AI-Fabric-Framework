# Support informational follow-up qualification

## Scope and reproduction

Owner request: fix the follow-up failure and test it live. The reported request
`SUP-D1224F11-96A` and its original conversation are read-only evidence. No
customer request, message or knowledge document is rewritten by this change.

A fresh normal-customer production conversation reproduced the defect on
framework `0.8.52`: an integration explanation offered a checklist, and
`yes please do` returned `There is no pending action to confirm.` The approved
conversation snapshot contained the preceding two messages. This is not a
proven history-loss defect. Extraction selected `CONFIRMATION_POSITIVE` without
a pending operation, then the existing confirmation handler returned its
no-pending response as a successful informational answer.

## Change

Released framework `0.8.53` supplies server-observed pending state at trusted
system priority, separately from user messages and retrieved evidence. An
unavailable observation is distinct from an observed empty store. No pending
parameters or private descriptions enter this prompt.

Generic model guidance resolves short continuations against the prior offer,
including its requested output and format. There is no phrase matcher, forced
INFORMATION classification, fabricated pending action or canned answer
replacement. Confirmation of an absent operation produces the typed execution
finding `CONFIRMATION_WITHOUT_PENDING_ACTION`.

The framework's new `confirmation-without-pending` recovery policy defaults to
`FAIL`. The private LoomAI runtime explicitly opts into `LLM_REPLAN_THEN_FAIL`,
using the existing progressive-extraction call cap and strategy switches. It
can be disabled with
`AI_ORCHESTRATION_SEMANTIC_RECOVERY_CONFIRMATION_WITHOUT_PENDING=FAIL`.
The model chooses the corrected intent. Failed correction remains a visible
error under its own policy, not generic clarification or success.

Valid sibling obligations, user/tenant ownership, authorization, parameter
contracts, genuine confirmation and configured no-confirmation writes remain
enforced. An observed empty pending-action store does not suppress unfinished
action drafts. No database migration, vector reindex or UI redesign is needed.

## Verification record

- Full release-profile framework reactor passed before final prompt wording;
  focused final core tests and release CI qualify the final wording separately.
- Focused recovery, policy, pipeline and generation-history regressions: 124 tests
  passed against the final framework candidate.
- Release guards passed. Independent read-only review found no remaining issues
  after four findings were corrected and covered by regressions.
- Standalone agentic consumer clean verification passed against local candidate
  artifacts. This is not evidence of public Maven Central availability.
- Real-provider candidate tests intentionally exposed follow-up clarification,
  instruction leakage and repetition; those attempts remain private evidence.
  Assertions were strengthened rather than accepting only a successful type.
- Final native OpenAI qualification passed all 14 scenarios / 19 entry calls,
  including four checklist continuations, genuine approval and rejection,
  private-read authentication and configured no-confirmation writes. The
  OpenAI/ONNX/Lucene core matrix passed. Business receivers are synthetic; the
  provider, extraction, planning, confirmation and generation are real.
- The synthetic public guide was expanded to contain actual documented steps
  for its offered checklist. A two-item bullet list is accepted as a valid short
  checklist; a plain repetition is not. Earlier minimal-guide format misses and
  the hallucinated free-trial/refund example remain recorded, not relabelled.
- Runtime opt-in/disable and packaged-version regressions passed (12 tests).
  Earlier broad runtime runs exposed only stale packaged-version assertions
  after the BOM bump; these were corrected. Full final CI is a separate gate.
- Broad Framework Build `44552651000` remains red in the unrelated real-app
  Incident Room suite: 14 failures, with exactly the same failing method names
  as the recorded 0.8.52 main baseline and no additions. The method-set comparison
  is saved privately. This is not an all-repository-green claim and is outside
  the approved bounded follow-up fix.

## Release identities

- Framework source/tag: `9b0188bd76e7627813aeaa3d5e633c97c32cec97`,
  `ai-fabric-framework-v0.8.53`; Central workflow `44552711283` passed.
- Backend configuration default source:
  `e14964e4c64fab343a6ef922bf3f290759faf549`.
- Runtime adoption source: `7804af5ee73574704f19efc46e2f913cd964a201`,
  runtime image CI `44556239040` passed, including all 818 runtime tests.
  Artifact `dsa-5482e5ea` has digest
  `sha256:2c09a78bcf49dfd1c5b713b004e4793bc3362d710dd0b5010b960f9df99f01f4`.
- Central publication completed successfully. An isolated Central-only consumer
  clean verify/package/boot passed. Packaged core bytes match the public JAR:
  `3550d03cc382bb199aa8621bab58f59d01220eae5b922a2268167565ed480d35`.
- Both backend environments are healthy on the configuration-default source
  above, schema241 and environment/settings preserved.
- Support version `ver-fb899e00` preserves all ten saved configuration sections;
  reindexRequired=false. Staging release `rel-f0539865` is
  APPLIED_VERIFIED/PASSED. Its runtime and connector are healthy on the exact
  image digest, with matching public core bytes and no mixed framework JARs.
- First staging seed hit a separate source-selection miss before any checklist
  offer: the model selected source ID `approved-support-documents` as a vector
  domain, then asked for a knowledge domain. This is retained as a quality
  finding, not a passing follow-up. No source permission or routing policy was
  weakened. Fresh natural Engage-setup conversations then passed four turns
  (Thinker and Executor), including both short continuations. They used an owned,
  read-only server-test identity, not a browser customer sign-in.
- Production release `rel-08026937` is APPLIED_VERIFIED/PASSED and assigned to
  the original Support consumer. Runtime and connector are healthy on the exact
  CI image, matching the public core JAR without mixed framework versions.
  Original installation identity and ACTIVE state are retained. Both applies
  used the existing COLD_REPLACE lifecycle, not a zero-downtime rollout.

## Production result: partially fixed, not closed

Fresh normal-customer HTTP/API conversations ran the same integration-guide
offer followed by four continuations. No staff/admin identity or synthetic AI
answer was used. All four first answers succeeded and offered a checklist.

- Thinker, `yes please do`: useful checklist; INFORMATION_PROVIDED, success=true.
- Thinker, `do that please`: CLARIFICATION_REQUIRED, success=false, asks which
  knowledge domain to search.
- Thinker, `please continue`: same knowledge-domain clarification.
- Executor, `go ahead with the checklist`: useful checklist; success=true.

The false-confirmation response did not recur. Both failed continuations had
the preceding two messages and an INFORMATION intent with requiresRetrieval=false
and requiresGeneration=true. Thus the remaining failure is not missing history
or false confirmation. These are six successful turns and two failed turns,
not an end-to-end passing qualification. No browser clickthrough was possible
because browser automation was unavailable in this session.

## Additional retrieval defect and candidate

`ReadActionResolutionService.shouldUseRag` unconditionally requests RAG under
PARALLEL_ACTIONS_AND_RAG, even for a completed ANSWER_FROM_CONTEXT plan with no
executed read. RAG_IF_ACTIONS_INSUFFICIENT also treats the absence of new action
evidence as a reason to search. A controlled regression reproduced this forcing.
The production responses omitted the read-planner diagnostics on the routing
terminal, so their exact planner decision cannot be asserted retrospectively.
The extraction decision is observed; the context-only planner path is proven
separately by tests. The earlier source-ID/domain selection miss is distinct.

An unpublished candidate is prepared in the isolated framework worktree
`~/.codex/worktrees/context-only-rag-0854`, branch `codex/context-only-rag-0854`,
local candidate commit `862eefd4` (not pushed, tagged or released):

- Honor a completed ANSWER_FROM_CONTEXT decision only with explicit no-retrieval,
  nonempty owned history, no current structured input/target, and no executed,
  rejected, deduplicated or unfinished read work to preserve.
- Do not mark historical messages as current grounding evidence. Retain actual
  retrieval authorization, source selection and failed-read recovery unchanged.
- Clarify the planner prompt: available indexed work must use a RAG decision,
  not implicit delegation through ANSWER_FROM_CONTEXT.
- Clarify extraction's flags for a historical transformation: previous use of
  knowledge does not itself require a new indexed search. Fresh-evidence needs
  still require retrieval; no hardcoded phrase classification is introduced.
- Retain planner diagnostics and safe read receipts on routing-terminal results,
  without changing their failure status or exposing private action parameters.

Candidate checks: the original controlled regression was red before the change;
79 focused tests and all 1,130 core tests pass after it. These are local candidate
tests, not hosted 0.8.54 proof. POM versions remain 0.8.53 in this uninstalled
worktree; released artifacts have not been overwritten. Additional real-provider
qualification initially passed three of four parallel-cooperation cases. In the
fourth, extraction set requiresRetrieval=true but the READ planner chose
ANSWER_FROM_CONTEXT, saying that no fresh evidence was needed. The guard correctly
preserved the explicit retrieval flag, so the turn remained a visible failure.
This attempt is retained; the subsequent clarification is prompt guidance, not
removal of the guard. A second run after that clarification also passed three
of four, but exposed another cooperation branch: the model selected a successful
EXECUTE_READ_ACTIONS plan with usable guide evidence and no indexed source;
PARALLEL_ACTIONS_AND_RAG then forced RAG and routing returned the domain question.
This is directly observed in the candidate's retained planner diagnostics, not
retrospective proof of the production planner's exact decisions.

Do not publish this candidate as qualified. The bounded context-only branch is
covered, but remaining cooperation/source selection needs finishing: explicitly
align the READ planner's source-selection contract with parallel policy, or
qualify an appropriate Support cooperation policy. Do not silently bypass fresh
retrieval, search unauthorized sources, replace the model's answer, or ask the
customer for an internal domain. The synthetic parallel fixture currently has
an authorized namespace but no indexed dataset; actual search must be qualified
separately from history-only generation. Owner approval for the additional
framework release is pending; no 0.8.54 release is live.

Do not infer deployment from source version fields. Production activation and
normal-customer query results have been observed, but the failures above prevent
closing the whole follow-up journey.
Private logs, customer auth and raw conversations are outside Git under
`~/.codex/private/loomai-framework-0.8.53/`.
