# 010.70 LLM Context Routing And Selected-Result Reliability

- Status: implemented and released. Framework 0.8.60 and the updated widget are live in Harbor; primary desktop/phone item journeys pass. Broader compound/source-choice model quality remains explicitly qualified below.
- Created: 2026-11-22.
- Revised: 2026-11-22, explicit UI attachment approves the selected item's full data as AI context.
- Authority: the owner approved implementation after the explicit-attachment clarification on 2026-11-22. Scope remains the three fixes below and their bounded release/Harbor verification; no unrelated runtime upgrade or infrastructure change.
- Baseline: Harbor v7, `rel-2d36dfcf`, framework 0.8.59, private runtime source `e01eadd2866398562985e04a6226a3ba3173421d`.
- Evidence: [nine-case live investigation](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/verification-support/automatic-action-evidence/2026-11-21-selected-results-followup-investigation.md). Version and health statements describe that investigation, not a new live check.

## 1. Intended Customer Outcome

Customers can browse results, select one or more records, and ask natural short
follow-ups without supplying an internal knowledge-domain name or repeating the
full question. Explicitly attached results retain the full selected item data,
not just an action's automatic evidence projection. Two different records remain
selectable even when neither has a SKU.

The LLM chooses whether to answer from available context, call an authorized
READ tool, search knowledge, or ask a meaningful clarification. The framework
enforces ownership, permissions, necessary execution fields, confirmation and
budgets; it must not force RAG simply because selected records are present.

Grounded answers do not always require vector search. Reusing earlier approved
facts does not make them current, authorize an action, or prove current stock.
The LLM can still request a fresh authorized READ when the question needs one.

Owner clarification: clicking Attach explicitly approves that item's full
UI-provided data for AI use. Automatic/Selected controls unsolicited action-result
inclusion, not a second field restriction on content the user explicitly shares.
This is permission to read the attached content, not business-action authority
or proof that client-supplied facts are independently verified or current.

## 2. Scope And Component Ownership

1. **Framework: respect a valid context-only plan.** A completed
   `ANSWER_FROM_CONTEXT` plan with `requiresRetrieval=false`, no proposed actions
   and no further planning steps currently becomes RAG when targets/attachments
   exist. Correct that override rather than masking its resulting domain question.
2. **Widget: attach the full explicitly selected item.** The generic
   card serializer drops fields such as `priceGbp`, `stock` and
   `adjustableHeight`. This is a widget handoff defect, not a failure of Automatic
   evidence preparation. Serialize the selected UI item generically as explicit
   user-provided context. Do not require a server-owned evidence resolver just to
   share it; assess only any necessary existing runtime normalization changes.
3. **Widget only: correct record identity.** Missing SKUs compare equal and
   incorrectly reject the second product. Use one scoped identity rule across
   selection checks, attachment, removal and reattachment.

No new plugin type, result database, AI agent, queue, public API or configuration
screen is planned. Reuse current chat, conversation, evidence and widget paths.
If discovery demonstrates a necessary contract change beyond this scope, record
the smallest proposed change for review instead of silently expanding the work.

## 3. Framework Routing Changes

Primary locations in the published public framework source:

- `ReadActionResolutionService`: context-only predicate and RAG cooperation.
- `InformationVectorSpaceRoutingSupport`: downstream routing and diagnostics.
- `ReadActionResolutionServiceTest`: existing target/attachment expectations.

Implementation steps:

1. Add regression tests reproducing the valid completed context-only decision
   with history, resolved READ targets, pinned context and current attachments.
2. Remove their blanket exclusion from the context-only path. Attachment or
   target presence alone must not imply a new retrieval requirement. Keep
   authorization and the distinction between user input and trusted evidence.
3. Honor the consistent completed plan without requiring newly executed READ
   evidence. Preserve existing handling of actual retrieval requests, incomplete
   plans, failed reads and necessary execution fields; do not broaden those paths
   as an incidental change.
4. Keep source selection LLM-driven. Do not choose a default domain, force fan-out,
   add question keywords, or replace the clarification with a canned answer.
5. Record the planner decision, effective retrieval requirement, context origin
   and actual branch taken in existing diagnostics. Do not log secret payloads.

There is no new customer-facing force-retrieval setting. Inventory any consumers
of existing explicitly enabled retrieval flags before proposing their removal or
migration; do not silently change their policy in this patch. Both relevant flags
were false in Harbor, so they are not its root cause. Existing source restrictions
and action-declared requirements remain enforced when retrieval actually runs.

No stronger model, extra generation call, larger planning budget, hidden retry,
or domain-specific prompt is needed for the confirmed override.

## 4. Explicit Attachment Content Handoff

### 4.1 Confirm The Existing Contract First

Trace generic cards, custom action renderers and desktop/mobile Sources selection
through `attachments.ts`, `useAttachmentsController`, chat submission and the
runtime conversation adapter. Include confirmation and clarification continuations.

Confirm that each selection control retains the full selected item before
normalization; several current paths summarize or rename fields. Use the existing
attachment payload and chat endpoint, with stable local source/item identity
where available. Do not assume timestamp message IDs are server IDs.

The widget's existing `action-result-context` references can remain where needed
for actual server-owned lookup or execution identity. They are not a prerequisite
for including explicitly shared item data. Do not add a result store, canonical
turn persistence or a resolver solely to authorize ordinary attachment content.

### 4.2 Two Separate Ways Data Reaches The AI

1. **Automatic action evidence:** keep existing Automatic/Selected behavior.
   Empty Selected stays empty. No user selection means no raw-result fallback;
   merely displaying a card does not approve additional AI context.
2. **Explicit user attachment:** include the full business-data object associated
   with the selected UI item, including fields omitted by the automatic Selected
   projection. Do not apply `llmFacts` as a second filter to this user input.
   Reattachment uses the same rule. A selected source card shares that card's
   available content; it does not fetch hidden backend fields.

The explicit scope is the selected item, not the whole API response, neighboring
records, browser state or execution envelope. Preserve original field names,
nested values and meaningful `0`, `false` and `null` values. Do not add Harbor-only
field aliases or silently reduce data to familiar commerce fields.

### 4.3 Boundaries That Remain

- Enforce existing chat/conversation ownership, anonymous-session access and
  attachment-enabled policy. Attaching an item grants no access to other records.
- Treat content as user-provided data, not system instructions or verified live
  business facts. Client flags such as `groundingUsable`, `trust` or
  `actionEligible` cannot grant authority or change action permissions.
- Keep platform/provider credentials, HTTP authorization headers and private
  execution context outside attachable item data. Retain existing credential
  exclusions and size/depth limits with visible truncation. This is not a new
  classifier for all confidential business fields or secrets embedded in prose.
- Preserve actual action success/failure. Sharing an error record may provide
  diagnostic context; it does not turn a failed action into successful evidence.
- If a control really resolves a server-owned reference, keep its existing
  authorization checks. Do not fetch missing or foreign data, or replay a write,
  to reconstruct ordinary attachment content.

Applications must not rely on `llmFacts` to hide confidential fields from users:
the attachable UI data itself must be appropriate for that user's access. Test
automatic Selected restrictions and explicit full-content sharing separately in
clean conversations, without assuming old history is retroactively erased.

## 5. Consistent Multi-Selection Identity

- Compare IDs or SKUs only when both compared values are nonempty.
- Scope identity to the actual source/result and record type where necessary;
  equal IDs from unrelated sources must not collapse accidentally.
- When business identifiers are absent, reuse a stable originating-result/item
  reference. Do not generate a new random identity for every click.
- Share the rule across `isItemAttached`, attach and reattach. The button state,
  selected chips and submitted attachments must agree.
- Keep removal independent: removing one selected record must not remove another.

This fixes structural identity only. No semantic duplicate detector is proposed.

## 6. Implementation Order

1. Capture current source/release identities and add failing regression tests.
   Trace the full selected-item payload and current runtime normalization.
2. Make the bounded framework routing fix and widget identity fix independently.
   Keep their ownership and tests separate for review.
3. Implement full explicit attachment content through existing contracts. Review
   any necessary runtime normalization delta before treating it as widget-only.
4. Run focused unit/integration and existing affected regression suites, including
   provider-backed checks where needed, before publishing a candidate.
5. After approval and passing pre-release tests, publish a new immutable framework
   patch, verify Maven availability and build pinned runtime/widget artifacts.
   Qualify Harbor on its existing staging target using the same question wording
   and UI controls that failed. Verify its exact assigned release and report
   source, image and release identities.
6. Roll out the qualified software changes through existing release mechanisms,
   with the same app identity, storage and credentials. No unrelated deployment
   upgrade, infrastructure change or automatic resource provisioning is included.

## 7. Required Tests And Live Acceptance

### Framework And Boundary Tests

1. Completed context-only plans with one/two resolved targets, current attachments,
   full source attachments and pinned context do not acquire forced RAG.
2. Real READ and RAG decisions still execute their configured paths, with current
   source/owner restrictions. Explicit payloads remain user input; they do not
   authorize server lookups. A genuine missing product can prompt clarification.
3. Incomplete plans, failed READs and explicit retrieval requests retain their
   intended handling. New routing does not suppress an actual requested lookup.
4. Unauthorized conversation access and foreign server-result lookups are denied.
   Tampered client flags grant no execution authority. Missing references do not
   fetch other results or replay writes; ordinary attached content needs no lookup.
5. Without explicit attachment, Automatic retains allowed fields and Selected/
   empty Selected remain restricted. With explicit attachment, the full selected
   UI item is included even when its price/stock fields were absent from Selected.
   Nested values, false, zero and null survive. Unselected neighboring records,
   credential canaries and execution envelopes remain excluded in both paths.
6. Confirmation, no-confirmation policy where explicitly configured, ownership,
   read/write permissions, execution budgets and successful-write receipts retain
   their existing contracts. This patch grants no new business capability.

### Widget And Customer Journeys

1. Browse furniture; select the desk; ask `summarize it`. The submitted evidence
   and answer retain its attached price, stock and height facts.
2. Select desk and chair with different IDs and no SKUs; ask to compare. Both
   chips, identities and full item content arrive. Repeated selection deduplicates
   only the same record; removal and reattachment remain independent.
3. Browse in a fresh conversation; ask `compare between them` without attaching
   anything. Available resolved results must not force an internal-domain question.
4. Select both full READ source cards and compare. Preserve each explicitly
   selected card's available content without silently attaching unrelated data.
5. Ask `delivery options` and the full delivery question. Check actual chosen
   READ/context paths and factual answers, not just the absence of an error.
6. Repeat attachment/continuation checks on desktop and phone, covering docked,
   max and standalone presentations that share this implementation. Record exactly
   which surfaces were tested; do not imply every theme was qualified.

Use repeated fresh and continuing conversations, recording every sampled outcome,
not only a successful retry. Capture decisions, selected references, actual tool
calls, evidence exposure and visible results. Keep raw private traces out of Git.
Prefer READ-only live tests; any consequential test write needs separate explicit
authorization, counter checks and a no-replay assertion.

## 8. Separate Findings And Completion Criteria

The baseline care question chose document RAG but returned zero documents. Audit
its indexed-source/filter/search path separately; do not claim this routing fix
solves it or infer an S3 permission problem from an empty search. Likewise, the
older cramped action table, Updates status discrepancy and unrelated broad-CI
failures are not silently included in this scope.

Completion requires all three confirmed defects fixed with regression coverage,
exposure/ownership negative tests passing, and the exact live follow-ups qualified.
Any remaining model-quality miss or unrelated CI failure stays explicit. A valid
context route alone is not proof that the final answer is accurate.

## 9. Execution Checkpoint, 2026-11-22

Framework source `09a0d7d1` implements the bounded context-routing correction
for 0.8.60. All 53 focused routing tests and the 30-module test reactor passed;
release-profile packaging/install also passed (2,568 tests, no failures/skips).
Public main, immutable tag and Maven Central publication are complete. All 30
module availability checks, fresh consumer resolution and release guards pass.
No model, prompt, retrieval allowlist or budget was changed.

Widget source now preserves full explicit item content within existing content
bounds, retains nested/zero/false/null values, excludes structured credentials,
and consistently identifies scoped selections across generic cards, reattachment,
persistence, React/script-tag APIs and transient chat inputs. Desktop/phone
browser tests exercise real cards and outgoing chat payloads. The complete widget
pre-publication suite, next-step/source-document regressions and Console build
passed. Initial test-harness dependency omissions were corrected, not bypassed.

Private final image source is `e5319e190eebc6a6cec312e5770e804f894236d7`.
Fresh Core/runtime CI passed against Central. Both Core backend/UI environments
run that source. Harbor's existing app is ACTIVE/ready on v8 / `rel-4a4caa6a`,
APPLIED_VERIFIED/PASSED. Native runtime and connector public JARs match Central
0.8.60 checksums. Both control planes' runtime packages are promoted to the
production channel; unrelated Brain deployments were not upgraded.

Fourteen READ-only live query samples are retained. Desk summary, delivery,
two-item comparison and history-only comparison pass; the phone comparison also
passes with two full attachments and no page overflow/browser errors. Two full
source-card payloads reach the server, but one mixed-topic source follow-up chose
delivery instead of those products. A care wrong-tool choice, compound delivery
domain clarifications and a fresh browse skipping its READ tool remain separate
model/planning findings. Do not describe the broader AI experience as fully green.

Storage, credentials, draft settings, capacity, app URL/identity and unrelated
bindings were preserved; enquiry count stayed 13. Both task-owned admin sessions
were revoked and their old cookies no longer authenticate. No paid infrastructure
change, business write, hidden model retry or automatic reindex was performed.

See [execution evidence](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/verification-support/context-attachments/2026-11-22.md)
for the latest publication, artifact and live-qualification status. Initial plan
preparation itself made no code or deployed changes; this later implementation
checkpoint supersedes its earlier not-started status.
