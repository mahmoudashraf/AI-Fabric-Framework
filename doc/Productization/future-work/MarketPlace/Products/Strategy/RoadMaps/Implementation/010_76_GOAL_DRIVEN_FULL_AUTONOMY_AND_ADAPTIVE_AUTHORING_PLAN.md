# 010.76 Goal-Driven Full Autonomy and Adaptive Authoring

Status: owner approved; implementation in progress, 24 November 2026.

## Owner Clarification, 25 November 2026

Build with AI is a user-invoked configuration helper, not a promised durable
background service. Prioritize autonomous planning, authorized draft writes,
validation repair and answer/configuration quality during the active request.
After a service interruption the user may trigger the request again against the
current draft. Preserve actual saved draft configuration; unsaved AI candidates
and exact in-flight reasoning recovery do not need to survive a restart.

Exact cross-restart continuation, same-container restart qualification and
long-lived recovery guarantees below are deferred, not production acceptance
prerequisites for this helper. Retain already-built generic SDK capabilities;
do not add more recovery infrastructure or remove working shared primitives
merely to simplify this helper's product promise. Keep ownership, source access,
cancellation, finite spending, atomic saves and honest effect receipts. Active
request autonomy must still be driven by the configured Brain/framework, not a
Platform inference loop. Do not turn this simplification into automatic repeated
business effects or permission bypasses.

The owner also approves qualifying the strongest suitable GPT model available to
the account within the existing USD50 combined testing ceiling. Do not blindly
select a model without supported transport, accurate cost accounting and actual
quality evidence. A read-only account catalogue check is not a paid model test.

The owner approved this full plan, including FW-G1/G2/G3, and very high testing
turn/token allowances within USD50 combined qualification spend. Do not ask for
the same testing approval again. This is not unlimited money or infrastructure
approval. Production autonomy is not qualified merely because this plan is approved.

Testing allocation applied through durable, additive native top-ups: staging
USD25, production USD20, with USD5 reserved for external Harbor checks. Each
native environment has 1,000,000 attempts and 100,000,000 tokens available under
its spending ceiling. Original grants, expiry and consumed usage were preserved.
Private approval/top-up evidence is under `~/.codex/private/loomai-autonomy-01076/`.

This follows 010.75. That migration delivered a real configured Smart Brain and
bounded READ/WRITE execution. It did not deliver a persistent, goal-owning agent
that automatically continues beyond one bounded authoring turn.

## 1. Product Decision

Implement Full Autonomy as **goal-driven, budget-governed draft assistance**:

- The user provides an outcome and relevant sources, not a recovery script.
- The configured Brain's LLM owns planning, task decomposition, tool/profile
  selection, recovery strategy and semantic completion/blocker decisions.
- The framework runs and checkpoints the work, executes governed capabilities,
  supplies observations and schedules continuation. Platform is not another AI
  planner or provider retry loop.
- Requested draft writes can execute without confirmation after every save.
  The user can review, manually edit or use the existing Undo afterward.
- Reaching a short execution segment's allowance yields the work; it does not
  fail the user's goal or require a new "try again" message.
- There is no small fixed total model-call or repair count for an autonomous
  goal. Every call still needs admission under existing finite funding, token,
  provider, authorization and cancellation boundaries.

"Unlimited calls" must not mean unlimited money, infinite error loops, unlimited
provider concurrency, permanent authorization or permission to publish. The
customer label can be **Autonomous**; internal documentation uses **goal mode**.

### Non-goals

Do not build a second Platform inference engine, browser-driving agent, general
code-execution sandbox, new account type, autonomous publisher or separate
customer console. Do not expand ordinary Support's read-only authority. Do not
restore the retired private generation endpoint or rejected 010.65 redesign.
No new paid infrastructure is part of this plan.

## 2. Evidence and Current Limitations

### 2.1 Actual Harbor qualification

The 24 November recording started with an empty Harbor configuration draft and
reused existing managed services. Two requests to prepare all five documented
API operations stopped with `WORKFLOW_CORRECTION_LIMIT`.

Each failed attempt recorded:

- One successful `authoring_get_context` READ.
- Four model calls in total, including three empty planning responses.
- Two consumed corrections and zero draft writes.
- No candidate-validation call or executed write to repair/replay.

Logs confirm empty content at the planning boundary. They do not establish the
provider's finish reason or why that content was empty. A later read-only host
inspection found `LOOMAI_AUTHORING_MAX_OUTPUT_TOKENS=8192` and MEDIUM reasoning;
the compiler also declares a 4096-token specialist final-output limit. Neither
is proof of the effective failed planning call's cap. Trace the actual exchange
before attributing the failures to token exhaustion or changing model settings.

25 November qualification update: the new native provider diagnostics now prove
the effective cap on a fresh R3 attempt. Four planning calls ended with `length`,
4096 output/reasoning tokens and zero content. The controlling override is
`AI_PROVIDERS_GENERATION_MAX_TOKENS=4096`, not the specialist final-output limit:
the planner uses `LlmPurpose.GENERATION` without a request-specific override.
Correct the existing published generation-model token setting to 8192 rather
than introduce a duplicate workflow knob. Retain the existing financial boundary
and qualify actual calls. This is not a semantic validator or another retry
engine. Successful actual Harbor qualification is still required.

Manually narrowing the request produced two READ capabilities: product search
and delivery policy. Two new approved documents were uploaded/indexed. Harbor
v13 (`ver-1d693161`, release `rel-3465fd67`) then passed verification with
30 passed, zero failed and four skipped checks. All four Engage answer checks
passed. The three other API operations, including the enquiry WRITE, were not
configured/qualified in that walkthrough. These results do not prove full
autonomous API import.

Evidence: the private local recording/receipt directory is
`~/.codex/private/harbor-walkthrough-20261124/`. The shareable edited video is
`/Users/mahmoudashraf/Movies/LoomAI/Harbor-2026-11-24/harbor-build-with-ai-walkthrough.mp4`.
Keep raw provider/authentication material private; do not add it to Git.

### 2.2 Validated implementation constraints

- The live authoring policy is eight tool steps, three write steps, 16 model
  calls, two corrections and a 120-second workflow deadline per turn.
- `AuthoringFeedbackPolicy.MAX_CORRECTIONS` is two. The workflow compiler also
  rejects more than two corrections, 16 model calls, three writes or 120 seconds.
  Raising a UI number alone cannot enable the proposed behavior.
- `ConfigurationAssistantService` starts an in-memory `FutureTask` with a
  140-second session work deadline, then projects the native result as final
  for that turn. Sessions have 30 stored turns and a 1 MB encrypted payload
  limit; these are not a durable unlimited-goal journal.
- `BoundedWorkflowRunner` persists steps/effects, but allowance exhaustion becomes
  terminal `STOPPED`. Its restored usage counters belong to that bounded work.
- `RuntimeConfiguredWorkflowDispatcher` binds a ThreadLocal invocation to an
  owned canonical chat turn, a one-use conversation snapshot and a short-lived
  lease/assertion. It cannot simply be submitted to another thread unchanged.
- The SDK already provides `submit`, `find`, `resume`, `cancel`, durable
  execution records, checkpoints and governed action invocation. Reuse them.
- The inspected SDK SpecialistChain registry rejects write-enabled workers,
  restricts repeated target invocation and keeps managers free of actions.
  It is not the existing authoring WRITE loop. Do not pretend that attaching a
  chain manager enables write-capable goal delegation.
- Current source reads can return large source bodies, and ordinary draft
  patches can replace complete action/route collections. More retries alone do
  not make large candidate generation reliable.

## 3. Ownership of the Goal

Use a configured **goal owner**, implemented as an opt-in extension of the
existing BOUNDED_ITERATIVE execution, not a new inference subsystem.

```text
User outcome + selected resource/page + explicitly shared sources
    |
    v
Platform: authorize scope, sources and existing funding; create work authority
    |
    v
Existing canonical conversation message -> durable native goal execution
    |
    v
Configured goal-owner LLM
    | inspect current state and previous receipts
    | plan / choose scoped tool / select permitted specialist focus
    v
Existing governed READ/WRITE invocation -> normal Platform resource services
    |
    v
Actual receipt + current state + validation/provider findings
    |
    +----> goal owner replans, repairs, saves another part or requests real input
    |
    +----> segment boundary -> checkpoint / fair scheduling -> automatic continuation
    |
    +----> evidenced completion / real blocker / operational pause / cancellation
```

### Direct work versus specialist delegation

For a known page, its configured specialist is the goal owner: the API profile
can complete an API-import goal without paying for an extra manager on every
step. "Manager" describes ownership of progress; it need not be a second model.

For multi-area Brain setup, the configured setup owner may select the existing
published API, knowledge, responses, models/search, workspace or team profile.
Add one-level native goal delegation where useful. A delegated profile receives
a typed subtask and authorized work/source references, not arbitrary authority
or full configuration stuffed into an objective string.

Parent and child share the root goal, funding, cancellation epoch and receipt
namespace. A child cannot grant scopes, open recursive agent trees or mint an
independent budget. Selecting a profile narrows the original scope; it never
expands it. Writes remain governed through the normal authoring adapters.

Ordinary SpecialistChain and Support defaults remain unchanged. Write-capable
goal delegation is an explicit new framework contract, not a global relaxation
of read-only chain restrictions.

## 4. Goal Contract and Real Completion

Persist the following logical information using the existing native durable
execution/checkpoint storage, with a small Platform session projection:

- Stable goal ID, original request and explicit user-request revision.
- Original actor/customer/resource, authorized paths/actions and source versions.
- Pinned assistant release/profile definitions and delegated-profile allowlist.
- Model-proposed work items, pending operations and compact progress summary.
- Executed operation IDs, actual receipts and current validation observations.
- Segment number, cumulative usage, recovery history and next eligible run time.
- Current lifecycle state, typed pause/blocker reason and resume reference.

The LLM may revise its work plan without asking permission to change tactics.
Plan items are not new authorization grants. Source text and model-created IDs
cannot become trusted resource selectors.

For an explicit "prepare all selected API operations" command, derive the
expected operation set from the existing parsed, versioned source catalogue.
Track each operation's actual saved definition, route, inputs, authentication
reference and relevant confirmation policy. This is a generic source-coverage
contract, not Harbor-specific matching or a forced minimum-write count.

A genuinely narrower user request changes the requested work set. Do not force
every documented endpoint into a request that only asks for one endpoint.
Do not use keyword matching to determine that semantic scope.

Before accepting COMPLETE, return normal execution/validation findings to the
model if required requested work has no supporting state or receipt. Preserve
existing valid no-op completion: a requested value may already be configured.
Check current state rather than requiring a new write merely to prove activity.

Distinguish these outcomes:

- Draft configuration saved.
- Configuration passes the relevant checks, with warnings still visible.
- A connection was tested, or explicitly remains not tested.
- A version was published/applied/verified, only through separate authorized work.
- A live conversation was qualified.

The agent must not report the last three because it only saved a draft.
Semantic quality is evaluated separately from structural completeness.

## 5. Lifecycle and Stop Decisions

Use existing execution lifecycle concepts, extended with a typed PAUSED state
and nonterminal segment yield. Do not create competing lifecycle engines in
Platform and Runtime.

### Model-owned semantic decisions

- **Complete:** requested outcome is achieved, supported by current state.
- **Waiting for input:** genuinely missing credentials/references, documentation,
  business facts or an explicit scope/consent decision prevents further work.
- **Unsupported/blocked:** remaining work cannot be achieved with the available
  authorized capabilities. Explain the evidence, attempted approaches and what
  would unblock it; do not declare impossibility after an arbitrary retry count.

The manager can complete independent permitted work before reporting a blocker,
provided it does not silently omit an explicitly denied resource or claim the
whole goal succeeded. Show exactly what was saved and what remains.

### System-owned operational decisions

- Segment step/correction/time allowance: checkpoint and requeue, not goal failure.
- Rate limit/transient outage: typed feedback, durable backoff honoring provider
  instructions, and configured fallback only where already authorized/funded.
- Repeated unavailable/empty planning: recovery with compact diagnostic context;
  pause visibly if no functioning planner is available. A nonresponding LLM
  cannot decide whether it should stop.
- Funding/token admission exhausted: pause and retain progress; no automatic top-up.
- Expired authority: pause for reauthorization. Revoked ownership/delegation or
  explicit forbidden access stops affected execution immediately.
- Uncertain WRITE outcome: reconcile its original operation ID; pause if the
  outcome cannot be established. Never execute a replacement write to discover it.
- User Pause/Cancel, source withdrawal, resource deletion or retirement: fence
  queued work and preserve actual already-committed effects.

Reason codes must distinguish recoverable pause, user input, unsupported work,
security denial and infrastructure failure. Do not turn all of them into FAILED
or ask the user for internal planner/domain fields.

## 6. Calls, Repairs and Spending

There is no Infinity sentinel and no resetting usage when a segment resumes.

Retain finite admission for every provider call, original ratecards, recorded
usage and configured service funding. Root/child/repair calls all consume the
same applicable ledger. A goal budget may narrow available funding, never
increase it. Status reads, scheduling and opening a panel do not invoke a model.

Suggested initial segment settings for qualification are the existing eight
tool steps, three saves, 16 model calls and 120 seconds, with four corrections
instead of two. These are proposed scheduling bounds, not a claim that every
setting is already configurable or qualified. Compiler/deployment ceilings must
be explicit and no higher than the supported framework contract.

When a boundary is approached, yield before another operation is admitted.
Continue automatically under the root authority and remaining funds. Preserve
cumulative usage separately from a fresh segment allowance so restored work
does not immediately fail against the previous segment's consumed counters.

No arbitrary small total-call limit should end a healthy goal. The existing
finite financial/token/authorization limits remain real. Repeated no-progress
work supplies feedback and eventually an operational pause; it must not burn
the remaining allowance indefinitely.

Current historical qualification approvals include a USD50 combined owner ceiling
and separately preserved native environment caps. Read actual grants and usage
before running tests. The subsequent explicit owner approval permits the recorded
010.76 testing top-ups above. It does not permit resetting usage, exceeding the
USD50 combined ceiling or authorizing a new provider recipient.

## 7. Adaptive Recovery and Context Management

### Diagnose the actual failure

Carry safe typed provider outcomes through the existing generation response and
structured-call failure contracts: call purpose, model, effective output cap,
provider request ID, finish reason, available input/output/reasoning token counts,
returned-content size and transport/error classification. Missing provider data
stays unknown. Never log or request private chain-of-thought or credentials.

Keep distinct: empty response, truncated response, invalid JSON, invalid required
execution fields, candidate-validation findings, authorization failure, provider
unavailability and unknown write effect. Generic "return JSON" feedback is not
enough for all these cases.

### Let the model change approach

Published goal/profile prompts should support smaller batches, selective source
reads, incremental saves, a focused specialist and a shorter next decision.
When a functioning planner is available, it chooses the appropriate tactic.
Do not hardcode an endpoint count, product domain or keyword-based repair.

For repeated empty/oversized decisions, offer a compact recovery planning context
with goal progress, pending work references and typed findings, rather than
resending every full source/result. Inspect actual provider metadata before
changing completion limits. Any alternate model/provider and output allowance
must be configured, admitted and visible in diagnostics, not a hidden extra call.

Use exact execution/error identities and state changes to detect structural
non-progress and feed it back. Do not classify success/failure from phrases in
the model's explanation. Do not require the model to invent a new strategy when
the missing prerequisite is a real owned credential or unavailable service.

### Bound context without losing work

Keep the complete durable journal separate from the prompt window. Supply the
original goal, current request revision, compact plan, unfinished items, relevant
recent findings and receipt references. Load details through scoped tools.

Summaries may aid navigation but cannot replace authoritative saved state,
operation receipts, permissions, source versions or unresolved WRITE effects.
Context compaction cannot silently erase a remaining endpoint or blocker.

## 8. Incremental Authoring Capabilities

Reuse the current resource services, compilers and action editor. Add narrowly
scoped tool contracts where current whole-document operations are inefficient:

1. Enumerate selected source operations with stable source/version/operation IDs.
2. Read selected operation schemas or source ranges with explicit truncation,
   pagination and bounded payload sizes.
3. Read individual current action/route definitions and relevant saved state.
4. Upsert one capability, or a bounded batch, with its route and source provenance.
5. Check requested-goal coverage/current findings and read operation receipts.

The upsert adapter performs a structured merge by stable identity using the same
save path as existing forms. It preserves unrelated definitions and unknown
fields. It is not a new plugin type, a second action editor or an inference tool
that silently invents mappings.

Retain `authoring_update_draft` for supported general edits. Avoid repeatedly
requiring the model to re-emit an entire growing action collection to add one
endpoint. Update and upsert share the existing authority, validation and audit.

Automatic action evidence remains the new-action default; Selected fields stays
an explicit advanced option. Required input types, approved URL/credential
references and action confirmation policies remain necessary execution contracts.
Saving an API capability does not invoke that business action.

## 9. Persistence, Authority and Exactly-Once Effects

### Runtime reuse

Extend existing durable execution/checkpoint storage for goal progress, scheduling
and segment continuation. Add migrations only for needed root/segment/state fields.
Reuse leases, compare-and-set ownership, execution handles and cancellation fencing.
Do not introduce another queue, workflow database or infrastructure service.

Replace the ThreadLocal-only host binding for asynchronous goal work with a
persisted, verified work-authority reference. On claim, reconstruct the trusted
execution context and canonical conversation binding from server-owned records.
Never serialize browser credentials or accept an LLM-supplied principal.

Keep the same root operation namespace across segments. Step numbers do not reset.
An observed EXECUTED receipt remains executed after a restart, narration failure,
new segment or delegated profile. Reconcile a DISPATCHING/unknown step before any
new side-effect attempt. Per-operation idempotency is not a draft editing lock.

### Platform projection and receipts

Keep the existing assistant session as the UI/source container and link it to the
native goal. Normalize long-running operation receipts if necessary into an
authoring-operations table; configuration save and receipt must share one short
transaction. Store a bounded summary/reference in the encrypted session, not an
unbounded execution history inside its 30-turn/1 MB payload.

The Platform authority record contains original actor, customer, owned resources,
allowed tasks/paths/actions, selected sources, release/profile identity, validity
and revocation state. It is issued by normal authenticated services, not by AI.
Each READ/WRITE rechecks current ownership, delegation, user status and scope.

Short-lived runtime assertions may be renewed by the trusted host only while
that original work authority remains valid. Goal continuation is not permission
to revive an expired login or revoked delegation. Explicit logout pauses/revokes
continued authoring authority; resumption requires authenticated reauthorization.
Closing a panel or navigating away does not, by itself, cancel the goal.

Pin the assistant definition for the active goal. Release retirement/unavailable
definitions pause safely; do not silently move work onto a broader newer profile.

### Single-user editing remains simple

Manual controls and navigation remain usable during AI work. Requested AI edits
may replace manual values in the authorized area; users normally refine after AI.
No AI/manual mutex, draft-revision approval gate or parallel-editing redesign.

Keep short atomic saves, actual resource lifecycle checks and preservation of
unrelated fields. If an app becomes ACTIVE or a resource is archived/deleted,
the goal must respect that real lifecycle boundary. It cannot disable/publish a
resource simply to recover its editing permission.

## 10. Conversation and API Integration

Keep the existing configured Brain and canonical message entry. Do not add a
second intelligence/generation endpoint.

An authorized goal-mode message starts/resumes native durable work and returns
an acknowledgement/handle rather than holding an HTTP request open until the
whole goal finishes. The existing Platform session/turn API can retain its
immediate session response and project goal progress additively.

Extend the canonical conversation controller with owner-scoped work-status/control
adapters over the SDK handles. Proposed route contract for review:

```text
POST /api/chat/me/conversations/{conversationId}/messages       existing entry
GET  /api/chat/me/conversations/{conversationId}/work/{workId}  progress/receipts
POST /api/chat/me/conversations/{conversationId}/work/{workId}/control
     { command: PAUSE | RESUME | CANCEL }                      authorized control
```

These are work-management surfaces, not another query endpoint. Reuse existing
conversation events for progress and exactly one final conversational outcome.
Segment retries do not create fake user turns or exhaust session turn capacity.
Unchanged ordinary customer chat endpoints keep their current behavior.
Conversation turn count and provider-ledger admission are different: retain the
existing provider/chat-attempt accounting for every applicable inference,
including repairs. Fewer transcript messages cannot make paid calls unmetered.

Bind each goal to one canonical conversation/control epoch. Integrate existing
`INTERRUPT_AI` cancellation, protected final-message capacity and history erasure.
Persist progress independently of a single HTTP thread/turn lease; renew or yield
operational leases without losing the original cancellation authority.
Pause releases the current execution lease and records progress without emitting
a false final assistant answer; the root retains protected completion capacity.
Resume obtains a new authorized execution lease, not a fabricated user message.
Cancel is terminal for that goal and records any actual partial effects. Existing
interrupt behavior needs this explicit integration, not blind reuse that marks a
paused goal permanently cancelled or emits a second final answer.

A user follow-up while running is an explicit goal directive. Within existing
scope it can revise priorities/requirements at the next safe checkpoint. Record
a new immutable request revision, fence obsolete queued work and observe any
in-flight committed effect. Scope expansion needs explicit authenticated approval;
changing pages or attaching untrusted text cannot expand the grant.

Removing a shared source cancels dependent work and clears its protected body
from goal/checkpoint/history stores according to existing erasure rules. Retain
necessary financial/audit receipts, not removed source content in summaries.

## 11. UI and Configuration

Use the existing closable Build with AI panel and canonical Brain pages.
No new global menu or parallel configuration editor.

- Provide Quick edit and Autonomous execution modes on explicit Send/Prepare.
  Autonomous is the intended default for multi-operation preparation/Brain setup;
  simple requests may finish in one segment without an extra manager call.
- Keep current page awareness, blank Prepare, selected source/operation controls,
  optional hint, preview, follow-up, review/diff and Undo.
- Show the current goal, concise progress, saved changes and outstanding items.
  Progress counts come from actual scope/receipts, not optimistic model prose.
- Collapse intermediate tool details under activity. Show short explanations and
  typed findings, never private reasoning transcripts.
- Offer Pause, Resume and Cancel, using familiar icons with accessible names.
  Restore the same goal after refresh; a closed panel can reopen its progress.
- Show budget/authentication/service pauses distinctly with the relevant next
  action. Do not require a repeated prompt when continuation is already permitted.
- Keep the original target visible when navigating. Never retarget running work
  because another Brain or page is selected.
- Show draft-only scope and real save/validation state. Publish/apply/activate,
  indexing, funding and destructive operations remain separate unless explicitly
  added to an approved future goal capability contract.

Authorized operators configure published goal-owner/profile instructions,
delegated profile bindings, segment bounds, recovery settings, provider/output
policy and pause behavior through the managed Brain's existing settings/Advanced
surface. Add missing forms with inheritance/current values preserved. Customer
request options can narrow limits, not override operator ceilings or funding.

Do not restart a model call merely because the panel opens, a page changes or a
status poll runs. Old bounded/legacy transcripts remain readable; do not silently
copy their sources/history or rewrite their recorded engine/state.

## 12. Required Framework Work for Review

### FW-G1: Goal lifecycle and durable continuation

Extend WorkflowPolicy/Allowance/Checkpoint/Runner and the execution gateway's
durable dispatcher with opt-in goal execution, nonterminal yield, cumulative
usage, typed pause, safe resume and scheduling. Separate segment from goal
lifetime. Preserve existing bounded execution by default.

Add safe host-authority restoration and canonical control/history integration;
do not enqueue the current ThreadLocal dispatcher unchanged. Verify lease expiry,
cancellation and release retirement with actual storage. Exact restart recovery
is deferred for this helper under the owner clarification above.

### FW-G2: Failure observations and adaptive recovery

Extend existing generation/structured-call diagnostics with provider-safe outcome
metadata. Preserve EMPTY versus TRUNCATED versus transport/validation errors.
Feed compact recovery observations into the same configured LLM loop. Expose
configured recovery/fallback options without hidden inference or answer rewriting.

A segment correction boundary yields in goal mode. Repeated no-progress/provider
failure can pause with diagnostics; it is not evidence of semantic impossibility.

### FW-G3: Scoped goal profile delegation and completion

Support optional one-level, published-profile delegation within the same goal
authority, ledger and cancellation scope. Direct page specialists remain valid
goal owners. Carry typed work references and receipts; do not make the existing
read-only SpecialistChain writable or allow recursive child agents.

Expose completion/coverage findings through the existing completion-validation
hook. Keep semantic choices LLM-led and require only real execution/authority
contracts. Test partial saves, no-op completion and non-complete results from
workers without automatically terminating the parent goal.

### Platform/Runtime work, not additional generic framework semantics

- Replace hardcoded two-repair/short-turn authoring assumptions with the new
  published goal policy and durable authority/progress projection.
- Add paginated source/current-definition reads and transactional incremental
  capability upserts through existing resource services.
- Add the native asynchronous work-status/control adapter and goal cancellation.
- Publish domain-neutral manager/profile prompts and expose missing controls.
- Extend the current panel, audit, retention and verification; preserve existing
  Support/Engage/ordinary chat behavior and all source-access boundaries.

## 13. Ordered Implementation and Release Gates

1. **Baseline and diagnostics:** reproduce Harbor bulk failure with a sanitized
   fixture; capture actual provider finish reasons/caps/usage and current grants.
   Keep original failures. Do not infer token exhaustion from EMPTY_RESPONSE.
2. **Framework contracts:** implement FW-G1/G2/G3 behind opt-in policy, with unit,
   database-backed checkpoint, failure-injection and source-authorization tests.
3. **Platform tools and authority:** implement durable work binding/projection,
   normalized receipts, paginated sources and capability upsert; remove hardcoded
   product ceilings only through explicit supported policy validation.
4. **Runtime/managed Brain:** integrate asynchronous canonical work and published
   goal/profile instructions. No private generation shortcut or Support WRITE grant.
5. **UI:** add mode/progress/pause/resume/directives using the existing assistant.
   Preserve manual editor access, previews, source reuse and prior history.
6. **Release qualification:** pass full framework/core CI and affected Runtime,
   Platform, storage/quota, UI and security regressions. Publish a new immutable
   framework version; verify Central availability/checksums before adoption.
7. **Staging acceptance:** use real configured authoring Brain, real services and
   fault injection. Reuse existing managed resources; no new infra without approval.
8. **Production acceptance:** rollout the qualified original authoring service,
   preserve identities/keys/ledgers, enable goal mode and repeat real customer UI
   qualification. Exercise explicit draft publish/Engage separately as below.
9. **Documentation/evidence:** update 010.75's follow-up, this plan's status,
   operating/UI guides and CODEX_WORKING_CONTEXT with actual sources, versions,
   limits, traces, remaining failures and rollback instructions. Record a new
   honest walkthrough after acceptance; do not relabel the old video as a pass.

Each implementation gate needs an actual result. Plan completion, local tests
or increased budgets are not proof of production autonomous behavior.

## 14. Acceptance Tests

### Goal and recovery behavior

- A request needs more than two corrections, three saves and 16 model calls:
  it crosses segments automatically, keeps one goal, and retains cumulative usage.
- Inject empty/truncated/invalid decisions: typed feedback and real automatic
  recovery; no user "try again" prompt and no substituted canned AI answer.
- Large API document/candidate: the model selects bounded batches/source reads
  and saves incrementally; unrelated fields and selected evidence rules survive.
- Validation rejects an uncommitted candidate: model corrects it. A successful
  save followed by narration failure remains executed and is not repeated.
- Lost callback/timeout around WRITE: reconcile the original operation receipt.
  Never replay a potentially committed mutation under a new operation ID.
- Deferred durability qualification: kill/restart during planning, after save
  and during delegation. This is not a helper release gate; saved drafts,
  financial records and protection against repeated effects remain required.
- Same malformed plan repeats: manager receives no-progress evidence and changes
  tactic or the system pauses. No uncontrolled paid spin.
- Real missing credential/host/unsupported connector: no guessed secret, host,
  authentication downgrade or wasted repair loop. Report actual remaining work.
- COMPLETE while a requested operation is missing: return coverage findings.
  Already-correct configuration completes without a forced save.

### Trust, accounting and UX

- Known foreign customer/Brain/source/goal/operation: deny without inference or
  state changes; delegated staff revocation takes effect before further actions.
- Source injection tries to grant publish/admin/secret access or select another
  resource/profile: original authority remains unchanged.
- Profile delegation cannot widen root paths/actions or reset its budget/counters.
- Exhausted funding/token grant: pause before unapproved calls; authorized top-up
  and Resume retain original usage. Scheduling/polls consume no inference.
- Pause/Cancel/logout/source erasure fence future work; in-flight writes retain
  real outcomes. Navigate/close/reopen restores work without an editing lock.
- Follow-up modifies the active goal without replaying previous writes or
  silently expanding scope. Manual edits remain possible and reviewable.
- Long goal does not depend on 30 fabricated chat turns or overflow session
  payloads. Compaction retains unfinished items and receipt/source references.
- Ordinary Support stays read-only; ordinary chat, all nine Build with AI task
  profiles, source reuse, preview, Undo and existing forms retain functionality.

### Harbor end-to-end release proof

Use the same separate fictional Harbor API and actual uploaded OpenAPI contract.
Keep the API service, managed S3 bucket, database/vector services and Engage URL.
Prepare an empty action/route draft through normal owned-resource operations;
preserve the previous immutable live release until explicit publication.

From one normal UI request/blank Prepare, autonomously prepare all five operations:

1. Product search, preserving optional category/maxPrice/inStock filters.
2. Product details with documented product ID.
3. Delivery policy.
4. Enquiry WRITE using the existing owned secret reference and required confirmation.
5. Enquiry receipt READ with its documented reference and business authorization.

The assistant must not require a human to say "prepare one endpoint at a time".
Inspect actual source-operation coverage, saved definitions/routes and receipts.
Repeat in three fresh staging runs and three production authoring runs, with
bounded spend and all failures retained. Add a larger synthetic contract to prove
that success is not specific to five Harbor operations.

Use an accepted candidate for explicit normal publish/apply/verification and
Engage assignment/activation. Reuse the approved documents and verify active
source versions; indexing is an explicit separate operation if required.

Run real Engage product/details/delivery/returns/care questions and a synthetic
enquiry with explicit confirmation, then read its actual receipt. Verify cancel
before confirmation causes no enquiry. Do not send real email, reserve stock or
perform a real customer transaction. A configured capability is not a successful
live invocation, and the confirmation test must exercise the current UI contract.

## 15. Rollback, Retention and Current Source Map

Rollback disables new goal admission and returns new requests to the existing
configured-Brain bounded mode, not the retired generator. Drain/pause active
goals with receipts intact. Do not run the old worker on an active goal record or
silently restart it under a new release/operation namespace. Use additive database
migrations and forward-compatible stored-state handling before software rollback.
No resource recreation, vector copying, wallet reset or automatic draft rollback.

Inspect these current files before implementation:

- Platform `assistant/ConfigurationAssistantService.java`: turn lifetime, queue,
  status projection, cancellation/source retention.
- `assistant/AuthoringFeedbackPolicy.java`: two-correction product ceiling.
- `assistant/AuthoringDraftOperations.java`: binding, task/source scope, normal
  actor revalidation, transactional saves and per-turn receipt limits.
- `assistant/ConfiguredAuthoringRuntimeClient.java` and
  `AuthoringBrainBindingService.java`: canonical transport and published authority.
- `deployment/specialist/WorkflowAssistanceCompiler.java`: current fixed ceilings,
  profile compilation and output limits.
- Runtime `workflow/RuntimeConfiguredWorkflowDispatcher.java`: canonical lease,
  ThreadLocal binding, durable checkpoint and governed authority hooks.
- Runtime `workflow/WorkflowAssistanceBundle.java`, `budget/` and the existing
  conversation-control/retention components.
- Framework `BoundedWorkflowRunner`, `WorkflowAllowance`, `WorkflowCheckpoint`,
  `BoundedWorkflowStep`, `AIExecutionGateway`, durable execution dispatcher,
  `DefaultStructuredJsonCallExecutor` and generation response metadata.
- Framework `DefaultSpecialistChainRegistry`: read-only/noninteractive limits;
  do not assume the new direct READ/WRITE loop removed those restrictions.
- UI shared configuration assistant, source picker and existing Brain/Advanced
  editors. Keep `PLATFORM_UI_PROFILES.md` and optional JSON preservation rules.

Framework source inspected for this assessment is the 010.75 worktree at
`/private/tmp/loomai-framework-01075`, commit `9e172766`. Runtime/Platform source
identities and live releases must be re-read before implementation; a historical
entry or this plan is not proof of the next deployed version.

## 16. Owner Review Checklist

- [ ] Goal-driven continuation within finite existing authorization/funding,
  without a small fixed total-call/repair count ending the goal.
- [ ] FW-G1/G2/G3, including durable host authority and one-level scoped delegation.
- [ ] Model-led recovery/semantic completion, system-enforced trust and typed pauses.
- [ ] Incremental draft writes and source-operation coverage; manual edits stay unlocked.
- [ ] Existing panel/API entry, additive progress/control and published policy forms.
- [ ] Full Harbor five-operation proof plus regression/security/accounting gates;
  exact restart recovery deferred by owner.
- [ ] No autonomous publication, new paid infrastructure or increased spending caps.

The owner has now approved implementation. Completion still requires the live
evidence above, not simply replacing two repairs with a larger constant.
