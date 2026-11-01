# Dealership Internal Knowledge Routing Correction

Date: 1 November 2026.
Status: generic configuration support deployed; staged recovery removes the
domain question but fails conversational answer quality. **Not promoted.**

## Root Cause

The buyer-facing sentence "Which knowledge base domain should I search?" is
framework-authored. It is not proof that the model chose to ask that question.
In the deployed 0.8.41 path, `InformationVectorSpaceRoutingSupport.route` returns
that literal when no valid intent/planner source is available and explicit
bounded recovery is not permitted. `vectorSpaceSelectionRequired=false` does
not select a source or suppress that terminal result.

A fresh four-question production API run used the same published installation,
anonymous bootstrap, canonical conversation-message endpoint, `executor` mode,
`landing` position and ordinary non-routing context. It preserved one
conversation and executed no business writes. Initial stock search passed;
the lowest-mileage follow-up reproduced the domain clarification. A separately
worded explicit comparison succeeded, so the failure is not universal.

The failed follow-up's extraction diagnostics show one valid model extraction,
no selected vector space, no repair, and the warning that
`requiresRetrieval=false` lacked a `directAnswer`. The earlier vector-resolution
step took 0ms, while `IntentHandling` produced the terminal clarification. The
immutable 0.8.41 handling code shows the intervening path: READ planning can
promote a no-retrieval decision to RAG, then route without re-entering the
extraction repair. On that route an absent source reaches the hardcoded
clarification. The failed public response discards the planner diagnostics;
the exact planner JSON is not claimed as captured evidence.

Contributing effective configuration:

- GPT-5.4-mini, not an old GPT-4o-mini assignment.
- Default curated module; `knowledgeBaseOverviewEnabled=false`.
- READ planning `SINGLE_PASS`, `RAG_IF_ACTIONS_INSUFFICIENT`, two-action limit.
- Authorized sources `dealer-vehicle` and `document`.
- Missing-source recovery `LLM_REPAIR_THEN_CLARIFY`; no explicit fan-out budget.
- The compiled system prompt already contains a configured-source catalogue and
  compound-clause guidance. It does not explicitly prohibit technical-domain
  questions. The separate READ planner in 0.8.41 does not receive the authorized
  catalogue added in 0.8.42.

Earlier dedicated prompt trials are recorded in
[the quality review](../autotrader-dealership-demo/2026-10-24_CUSTOMER_WORKSPACE_QUALITY_REVIEW.md)
and its overlay README. They were rejected experiments, not installed fixes.
Do not promote those historical candidates by their label or assume that the
optimized, subsequently deleted canary is the current production configuration.

## Bounded Change

No framework/runtime release, new validator on AI semantics, source-data edit,
new service, new database, keyword routing or response-text replacement.

1. Expose the framework's existing executor settings in Platform's normal
   versioned provider configuration: `knowledgeBaseOverviewEnabled`,
   `rag.fanoutEnabled` and `rag.maxSpaces`. Previously Platform exposed the
   recovery enum but could not publish the required fan-out budget fields.
2. For this demo explicitly select `BOUNDED_ALLOWED_FAN_OUT`, enable fan-out,
   set `maxSpaces=2` and expose the source overview. Preserve the generated
   two-source allowlist and all existing invalid-source/action/auth/tenant
   rules. Model-selected valid sources remain first; only missing selection
   can use the declared bounded recovery. This is not search-all per query.
3. Add reviewed prompt guidance: choose source entity types internally using
   the configured catalogue and conversation; return complete structured
   decisions; never ask buyers for a vector space, collection or technical
   knowledge domain. Clarify real business needs/item choices instead. No
   list of query phrases or car-name routing rules is introduced.
4. Preserve current model, UI mode/position, READ planning/cooperation and
   budgets, source bindings, images, actions, identity, Desk and app addresses.
   Qualify staging before applying the same configuration to production.

The generic adapter bounds `maxSpaces` to 1-12 and requires explicit true plus a
positive budget when bounded recovery is selected. This is an operator-owned
retrieval execution budget, not a semantic confidence threshold. Arbitrary
allowlist overrides remain rejected; actual retrieval authority is derived
from configured sources. Provisioning also rejects bounded recovery if that
generated allowlist is empty, so it cannot use an all-registered-space default.
Existing deployments without these overrides retain their defaults.

## Verification

- **PASS:** 117 focused Platform tests, zero failures/errors/skips, covering
  configuration types/bounds, allowlist protection, environment materialization
  and unchanged no-override behavior.
- **PASS:** focused diff/whitespace check.
- **PASS:** exact backend source/image rollout on both control planes for
  `00372a0fb1ec9d8e4810c48d6d82ff731ec64eb0`, with healthy native containers.
- **PASS, infrastructure only:** staging apply `rel-7b983289`, version
  `ver-dcdb7f61` (`v7`), completed `APPLIED_VERIFIED` / `PASSED`.
- **PARTIAL, semantic quality:** two fresh four-question staging conversations.
  Initial stock and explicit named comparison passed in both. The follow-up
  stopped asking for a domain but failed in both: it said car details were
  unavailable instead of answering Morrow C2 / 1,980 miles / hatchback. Both
  compound answers supplied stock but omitted the available delivery policy.
  No business write was executed. These are eight submissions, not a reliability
  benchmark; HTTP 200 / `success=true` did not count as an answer-quality pass.
- **NOT DONE:** production runtime apply, production assignment change and
  buyer-UI acceptance. A failed staging answer must not be promoted as a fix.

Source commits: `c2e5b94a260364e953d8c45593ac75355b316901` (wiring) and
`00372a0fb1ec9d8e4810c48d6d82ff731ec64eb0` (non-empty source authority).
Image CI runs `41276363070` and `41277366160` both passed. The authority-guard
backend is native/source verified on staging and production. Exact latter
Coolify jobs: staging `rvsr64kjljhkoxpp47wxv0qt`, production
`sw9bopmw143q5ryl5ckv11m7`, both terminal success.
Version `ver-dcdb7f61` (`v7`, framework 0.8.41, no reindex required) passed draft
validation with zero errors/warnings and was normally published. The staging
apply reused the existing runtime/connector and source artifact `dsa-9302c94d`.
The untargeted preview endpoint returned 412 EXTERNAL_VECTOR_COMPLETE_SCOPE_REQUIRED;
the normal apply with the explicit target and current source artifact was
accepted. No second publication or scope-validation bypass was used.
Private receipts: `~/.codex/private/loomai-paul-rigby-01055/domain-routing/`.
The first harness attempt used the retired query route and correctly got 410;
it executed no AI query. The corrected run used the canonical message route.
This is a harness correction, not a product workaround.

## Why The Useful Follow-Up Still Fails

The first staging follow-up captured the actual recovery path:

- Extraction had conversation history (two messages) and model-requested target
  resolution recovered two prior action-result items.
- Extraction still chose no source and no direct answer. READ planning selected
  `ANSWER_FROM_CONTEXT`, proposed no read and emitted no preferred source.
- The 0.8.41 `ReadActionResolutionService.shouldUseRag` treats that decision as
  requiring RAG if there is no successful **current-turn** action evidence.
  Earlier conversational facts do not satisfy that check.
- Late routing therefore used `EXPLICIT_BOUNDED_FAN_OUT_RECOVERY`, exactly
  `dealer-vehicle` and `document`, budget 2. The source fallback is confirmed by
  metadata, not inferred from the absence of the old sentence.
- The model's optimized query was "Compare the two previously returned electric
  cars by mileage and body style". It contained no actual vehicle names. Neither
  target hints nor a self-contained target-aware retrieval query were applied.
- Retrieval returned zero documents. Generation used `RAG_NO_CONTEXT_CONCISE`.
  Source inspection confirms this prompt receives the query and managed prompt
  overlay, not the earlier conversation facts or resolved-target evidence. Its
  no-information answer is therefore unsurprising, but still a failed journey.

The repeated follow-up selected `dealership_compare_vehicles` with empty params
instead. It still produced no usable facts, entered bounded recovery and gave
the same no-information answer. This is model variability inside the same
incomplete evidence path, not evidence of a deterministic domain-name router.

Read-only target-scoped signed admin inspection succeeded with the configured
target issuer/audience and read scopes. Staging has 6 vehicle vectors and 9 document
vectors; production has 6 and 12 respectively. Scanned staging vehicle records
contain Aster E1/4,850/SUV and Morrow C2/1,980/Hatchback and the correct tenant,
deployment and configured source handle. The index is **not empty**. Different
document counts are not full environment parity proof. Provider scores and all
document visibility filters were not inspected here, so this does **not** prove
the weak query is the only cause of the zero-result search. The historical
dead-letter counters were recorded, not reset or investigated as a new task.

The 0.8.41 READ planner's prompt model contains the intent, optimized query,
direct answer, eligible actions and evidence from this turn's reads. It does
not receive the earlier conversation/target fact bundle. That is a generic
information-flow gap; instructing the top-level prompt to "use history" does
not supply history to a later separate LLM call. Earlier rejected direct-answer
prompt trials had already encountered the planner's promotion back to RAG.

## Next Bounded Framework Work, Not Implemented Here

1. Supply an authorized, bounded conversational/target evidence snapshot to the
   READ planner and downstream answer path. Preserve source provenance and
   freshness; earlier availability is not automatically current availability.
2. Define what `ANSWER_FROM_CONTEXT` means end to end. Do not equate "no new
   action result" with "no context", and do not discard a usable grounded
   contextual answer merely by promoting it to empty RAG.
3. If a later planner genuinely requires RAG, resolve its source internally
   using the configured LLM repair or explicit bounded fan-out. Keep that
   decision and any failure visible; do not ask a buyer for infrastructure names.
4. Guide LLM-produced retrieval queries to be self-contained when trusted
   conversational references are needed. Do not add phrase matching, a runtime
   query rewrite or a hard validator for optional optimization quality.

The already-published 0.8.42 source catalogue improvement is relevant, but does
not by itself add conversational evidence to these calls or repair the final
no-context path. No new framework release or runtime image was made here.
The retained runtime image is
`sha256:902a5d00e47d34078907ecfbc3aedb3df3f8c33a5134096ddfbbae765474598d`,
native OCI source `63888284e1fa36dfe9fe013578f026bd2d9e9ea0`; historical handoff
image-source labels are not substituted for this native readback.

Production remains on `ver-73bc5b03` / `rel-4163ea35`, consumer binding revision 12.
The existing app URL and public installation assignment were not switched.
Separate policy synthesis, sign-in and detail-link issues remain open. The
entire dealership demo is still **not qualified**, despite the staged routing
improvement and passing infrastructure gate.
