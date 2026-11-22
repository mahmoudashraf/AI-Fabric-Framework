# 010.71 Bounded Intent/Routing Repair And Final Context Answer

- Created: 2026-11-22.
- Status: owner-approved direction; implementation and live qualification requested.
- Baseline: framework 0.8.60; private image source `e5319e190`; Harbor v8 /
  `rel-4a4caa6a`. These are the previous task's verified identities, not a new
  health check or evidence that this plan is implemented.
- Related: 010.70 and
  `verification-support/context-attachments/2026-11-22.md`.

## 1. Owner Decision And Outcome

An end user must not be asked to choose an internal knowledge domain, namespace,
vector space, routing attribute or extraction field. Internal planning problems
belong to the assistant, not to the person asking for help.

For recoverable missing/invalid intent and routing decisions, keep asking the
model to repair its decision within configured rounds, time and spending limits.
If those repairs are exhausted, make an explicitly configured final model call
to answer the original user from available conversation context and chat history.
Do not return an internal routing error or domain-selection question to the user.

This final answer is a genuine model capability, not canned text, a raw-evidence
dump, a hidden retry or a phrase-based rewrite. It must be accounted and observable.
It may explain what information is unavailable, give permitted general guidance,
or ask a relevant business question. It cannot pretend a failed lookup succeeded.

The owner subsequently requested full implementation and live verification after
this plan. Publication/adoption evidence must be added separately; this planning
record does not claim release completion.

## 2. Verified Existing Implementation And Gaps

The public framework source of truth is the published framework repository, not
the private repository's runtime module. Current 0.8.60 source was inspected in
`~/.codex/worktrees/context-attachments-0860`.

- `InformationVectorSpaceRoutingSupport.route` directly constructs the internal
  domain question when no vector space is selected. Its fan-out-limit messages
  also expose internal routing concepts.
- `VectorSpaceResolutionStep` has a second domain-question constructor, including
  namespace options. It handles early missing/invalid source decisions.
- `IntentExtractionStep.terminalRecoveryDecision` can turn missing/invalid
  source attributes into a customer clarification after extraction recovery.
- `SemanticRecoveryPolicy` currently defaults missing vector space to
  `LLM_REPAIR_THEN_CLARIFY`. Merely changing a generation prompt cannot remove
  these deterministic paths.
- `ProgressiveIntentExtractionEngine` already supports compound extraction,
  repair, completion and multi-step extraction, with a maximum extraction-call
  budget. Defaults are one repair, one completion and five extraction calls.
  Reuse these primitives; do not build an independent autonomous-agent engine.
- `ReadActionResolutionService` already supports LLM-selected context answers,
  authorized READs, RAG and source-catalogue diagnostics. Its planning iterations
  are separately bounded. Missing source attributes need explicit feedback rather
  than an immediate customer question or repeated identical attempts.
- Existing generation supports native system/user/history message separation and
  generation without new retrieval. Reuse it for the final context answer.
- Compound handling collects individual evidence/results and performs final
  generation/aggregation. Recovery must preserve successful children, actual
  action receipts and pending confirmations; do not restart the whole request.
- Platform already stores recovery choices in the shared draft, validates them,
  generates runtime settings and exposes them in the existing Advanced editor.
  Extend that path; no new global recovery page or chat endpoint.

## 3. Intended Request Flow

```text
Authenticated/authorized request + permitted conversation context
                         |
             Normal extraction and planning
                         |
       Missing/invalid internal decision attributes?
                no       |        yes
                |        |         |
        Normal workflow  |   Model repair with actual diagnostics
                         |         |
                         |   Validate trust/execution contracts
                         |         |
                         |   Fixed? -> resume only the affected stage
                         |         |
                         |   More configured repair budget? -> repair again
                         |         |
                         |   Budget exhausted / repair cannot resolve
                         |         |
                         +--> One final context/history answer
                                   |
                         Normal user-facing model response
                         + private recovery diagnostics
```

Approved bounded source fan-out can remain a configured recovery option. It must
not be silently enabled, expanded to foreign sources, or treated as a replacement
for the requested repair/context-answer policy.

## 4. Recovery Contract

### 4.1 Typed Eligibility, Not Text Matching

Use existing validation issue codes and routing reason codes to identify missing
or invalid internal planning attributes. Do not inspect a message for words such
as domain, clarify or failed. Retire the affected framework-generated questions
at their constructors; do not strip their text at the widget boundary.

Eligible cases include missing intent requirements, incomplete structured intent
output, missing registered source selection and invalid model-selected source
identifiers. Required fields must still be supplied before actual execution.
Optional hints must not become new hard validation gates.

A disallowed proposed action/source is never executed. A bounded repair may
select a permitted alternative, but an actual access denial must not become
evidence, trigger a wider search or be presented as successful access.

### 4.2 Repair Feedback

Each repair receives the latest candidate and actual missing/invalid field codes,
the original request, applicable current attachments/history, and only the
registered/authorized capability/source catalogue available to that request.
Keep instructions in system messages and customer documents/content in user-data
messages. Do not expose credentials, other tenants' catalogues or private errors.

Ask the model to supply/correct the missing decision attributes. Do not inject a
guessed namespace or force the request into RAG when context/READ is appropriate.
Every new attempt must use the latest candidate and findings, not repeat the
original broken candidate unchanged. Record unchanged attempts honestly; do not
invent semantic progress detection or phrase classifiers.

Resume the smallest affected planning/execution stage after successful repair.
Never restart completed business writes, recreate tickets or replay confirmations.
Repairing an action plan still requires valid parameters, trusted identity and
the normal confirmation/no-confirmation policy before any write.

### 4.3 Finite Configuration

Expose one understandable recovery policy through the existing Advanced workflow:
repair internal decisions, then answer from context. Reuse existing repair and
planning limits wherever their scopes already fit. Introduce only missing shared
recovery limits; do not add contradictory per-stage knobs.

Proposed starting managed defaults, subject to qualification:

- Up to three correction rounds after the original decision.
- A finite repair-admission window, with a separate final-answer allowance.
- One final context-answer attempt, enabled for conversational chat.
- Existing provider/account funding, token limits and action/retrieval budgets
  remain effective. More configured rounds do not override those ceilings.

Make the effective round/call/time limits explicit in runtime diagnostics and
Advanced. Count extraction, completion and routing repairs honestly; compound
children must not each obtain a fresh top-level recovery allowance. Do not create
an unbounded nested loop. Stop repairs early enough to leave the reserved final
answer attempt; if funding/deadline cannot permit it, do not make an illegal call.

Implemented settings under `ai.orchestration.decision-recovery` are
`enabled=true`, `max-repair-rounds=3`, `repair-admission-timeout-ms=30000` and
`final-context-answer-enabled=true`. The admission window stops new repairs,
not an in-flight provider HTTP call; that call retains its provider timeout.
This is not a hard overall wall-clock cancellation. The final answer remains
subject to actual provider/funding limits. Platform's Advanced form exposes
the round/window limits in `providerConfig.decisionRecovery`; generated runtime
environment keys are verified through real Spring environment binding.

## 5. Final Context-Answer Contract

When recoverable internal planning cannot be repaired within the configured
limits, invoke the normal generation abstraction directly with a purpose-built,
domain-neutral system instruction. Do not recursively call the chat/query
endpoint, re-enter intent extraction or run the tools through this final prompt.

Available context means only what the current caller may already use:

- The original user request and permitted conversation history.
- Current explicitly supplied attachments, with 010.70's full-item semantics and
  existing content bounds; user input is not verified live business evidence.
- Already authorized, successfully retrieved and prepared evidence.
- Actual completed action receipts and truthful pending/failed operation state.

Do not add raw API responses omitted by Selected evidence, empty Selected
fallbacks, foreign conversation records, restricted documents, transport headers,
provider credentials or private execution context.

The instruction must tell the model to:

1. Address the original user's goal using the available context/history.
2. Never ask for an internal domain, namespace, routing field or planning repair.
3. Separate known facts from general advice and explicitly acknowledge missing
   current information. Retain the configured tone and answer-breadth profile.
4. Never claim a lookup or write happened without its actual successful receipt.
5. Never invent product facts, policy, stock, prices, appointment slots or a
   customer's authorization. General advice is not unapproved web access.
6. If useful, ask only a genuine business question, such as which subscription
   the customer means, not which database to search.
7. Perform no additional action, retrieval, background retry or specialist chain.

Preserve the generated response rather than replacing it with canned assistance.
Mark fallback use internally as a context-only recovery, not fresh grounding or a
successful repair. Preserve actual compound results and receipts independently of
answer generation. The widget can show the ordinary assistant answer; it needs
no special domain/error screen for this recoverable case.

## 6. Boundaries And Honest Limits

"No routing error to the user" applies to recoverable internal decision failures.
It is not permission to conceal an access denial, provider outage, exhausted
credits, explicit cancellation, spending ceiling or an uncompleted business write.
Those are not missing model attributes and must not be retried as semantic repair.

If the final provider call itself is unavailable or fails, do not manufacture an
answer or mark success. Retain the existing honest service-status behavior. A
real model-generated answer cannot be guaranteed during a total provider outage.
Keep this unavoidable exception explicit rather than promising impossible uptime.

Final context answering does not satisfy an explicit fresh-retrieval requirement:
it can explain the limitation, not assert the requested live fact. Keep security,
authentication, tenant/document ownership, confirmation, action schema and budgets.
For strict Managed Tasks/structured-output callers, preserve their execution and
output-schema contracts; do not silently replace required JSON with chat prose.

No phrase matching, semantic output classifier, arbitrary model switch,
unconfigured model retry, infrastructure resize or wider source access is added.

## 7. Full-Stack Implementation Work

1. Framework: add focused regressions for all three known internal-clarification
   entry points and single/compound requests. Reuse existing extraction, completion
   and READ-planning mechanisms for typed feedback and bounded continuation.
2. Framework: implement the explicit final context-generation path and shared
   budget/deadline handling; retain safe message roles, accounting and receipts.
3. Framework: remove missing-source customer questions from the affected recovery
   policies, including invalid selections and source-budget clarifications. Return
   internal typed state to the coordinator rather than leaking namespace options.
4. Runtime: expose validated settings, preserve trusted request context, existing
   chat/Managed Task contracts and query diagnostics. Advertise actual support in
   the runtime capability manifest; reject unsupported requested configuration.
5. Platform: round-trip the policy/limits through the existing draft, validation,
   generated runtime environment, publish/apply, import/export and template paths.
   Preserve unknown/unrelated fields when editing. Do not create a second editor.
6. Console: put plain-language controls and cost/latency implications in existing
   Smart Brain Advanced recovery settings. Show effective saved/live distinctions;
   saving never applies automatically. Developer/staff delegation stays unchanged.
7. Defaults/migration: set qualified conversational template/Brain defaults through
   existing configuration generation. Migrate affected obsolete domain-clarify
   choices explicitly, with immutable release history and known old-runtime
   compatibility. No silent widening of fan-out or mandatory retrieval policy.
8. Verification: publish a new immutable framework patch only after focused and
   release tests; build pinned CI images; qualify Harbor on its existing target
   and app binding before promoting the runtime package. No replacement Kit or
   new infrastructure is required.

## 8. Required Tests

- Missing extraction attributes repair on a later configured round; the latest
  candidate/findings are fed back, then normal handling resumes.
- Missing/invalid routing selections repair to an allowed source or context/READ
  decision without asking for an internal domain.
- Exhausted repairs produce exactly one accounted final context generation, with
  available history, two distinct full attachments and already prepared evidence.
- No available facts: the final model discloses limitations or asks a relevant
  business question instead of inventing answers or source identifiers.
- Single and compound cases share their effective allowance; configured limits,
  deadlines, provider funding and cancellation stop correctly without recursion.
- Successful/failed READs retain actual state. Empty Selected never becomes raw
  output; credential and foreign-conversation canaries never enter either prompt.
- A completed write plus another failed planning obligation cannot replay the
  write. Successful receipts and pending confirmations remain visible and true.
- Unauthorized documents/actions remain denied. No repair/fallback broadens
  authority or treats attachments as permission to execute business operations.
- Final generation failure does not replay tools, fabricate success or trigger
  another unconfigured generation call.
- Managed Tasks preserve their JSON/schema and required execution guarantees.
- Platform form/JSON round-trip, draft validation, environment generation,
  compatibility checking and template bootstrap/reset preserve the policy.
- Existing generation integrity, 010.70 context/selection tests, authentication,
  action confirmation and conversation restoration regressions remain passing.

## 9. Live Qualification And Completion

Record every sample and attempt, including failures. Preserve model identities
and the original short questions so model changes cannot disguise a contract fix.

Use Harbor READ-only journeys: Browse, attach desk/chair, summarize/compare,
delivery, mixed-topic source follow-up, and the previously failing compound
delivery request. Qualify a controlled failure-injection fixture through the real
generation abstraction as well; live model randomness alone does not prove the
exhaustion path. Keep test hooks outside customer request control and production
configuration. No consequential business writes without separate authorization.

Acceptance requires:

1. Recoverable internal-domain clarifications are absent by typed construction,
   not by post-processing generated text.
2. Multi-round repair and final context answering are both exercised with exact
   call/round/deadline/accounting evidence.
3. Context answers are relevant and truthful; the same namespace prompt does not
   reappear inside a compound response or pending-clarification state.
4. Security/side-effect/structured-output negative tests pass.
5. Published source, actual Central artifacts, CI images, native loaded libraries,
   target release and Engage binding match the qualified patch.
6. Existing storage, credentials, app URL, capacity and unrelated bindings remain
   unchanged; no infrastructure/billing change or confirmation replay occurs.

Keep imperfect model source choice and missing indexed knowledge as separate
quality findings even when the final context response improves the user journey.
Do not describe successful graceful recovery as successful evidence retrieval.

## 10. Execution Record

### Implemented Scope, Pending Publication And Live Qualification

- Public framework patch prepared in the isolated `routing-recovery-0861`
  worktree, targeting immutable version 0.8.61. No published tag is moved.
- Request-shared server-only repair state spans progressive extraction and
  source routing. Feedback includes the latest invalid candidate, including
  malformed output, and actual validation findings. Invalid routes are never
  installed as executable selections.
- A single final generation step handles unresolved ordinary-chat planning.
  It uses trusted managed response guidance, native conversation messages,
  explicit attached content and bounded approved READ/retrieval evidence.
  Weak matches remain evidence with an explicit relevance qualification.
- Internal domain-question constructors are removed, including the weak-fanout
  path. Curated extraction instructions make source selection internal work.
  Genuine access/provider failures are retained; consumed planning markers
  cannot obscure them during normalization. Receipts and pending confirmations
  survive failed final generation and routing-provider failure.
- Platform reuses the Advanced -> Recovery policy editor and normal draft,
  validation, provisioning and publish/apply contracts. The runtime advertises
  `internal-decision-recovery-v1`; incompatible images are not allowed to apply
  requested settings. Managed bootstrap defaults include the bounded limits.
- Regression coverage includes strict specialist contracts, source ceilings,
  prepared versus raw evidence, operation state, provider failures, conversation
  recording, concurrent-field preservation and environment binding. Explicit
  real-provider qualification uses controlled routing failures, not production
  request-controlled test hooks.

This subsection records source scope, not deployed completion. Exact release,
Central, image, Harbor binding and live result evidence will be appended after
the verified rollout. Existing storage, credentials and infrastructure are not
being replaced or resized.

Plan created before implementation. No runtime changes, publication, deployment
or live query were performed in preparing this document. Append implementation,
exact effective settings, tests and live evidence as they are completed.
