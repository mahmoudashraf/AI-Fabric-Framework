# 010.75 Build with AI Smart Brain Migration and Bounded Workflow Plan

Status: proposed for owner review, 24 November 2026. No migration or framework
feature implementation is authorized by this document. The owner requested a
full plan and review of required framework changes before implementation.

## 1. Decision Summary

Build with AI must be a real configured, versioned, Platform-managed Smart Brain.
It must use normal conversation/orchestration, declared capabilities, configured
prompts/models and genuine framework execution, not a custom Platform agent
calling `AICoreService.generateContent` through a private generation endpoint.

The owner's latest clarification is part of the target, not a deferred option:
**bounded iterative execution must see both READ and WRITE Platform actions and
may execute writes within the user's authorized request scope**. It must observe
their real results and choose subsequent steps. Existing action-specific
confirmation rules still apply. Authorized draft edits need no extra approval
after each step; a draft-edit request is not approval to publish or deploy.

Recommended implementation:

1. A dedicated internal Configuration Assistant Brain per environment, using the
   existing CONVERSATIONAL product contract and canonical conversation API.
2. A generic opt-in read/write workflow extension to the framework's existing
   bounded iterative execution. Preserve current read-only behavior by default.
3. Ordinary registered capabilities wrapping existing Platform resource services.
   Model planning and recovery live in framework execution; authorization,
   validation, persistence, idempotency and audit stay in Platform services.
4. Use an explicit configured page/task-to-specialist binding when the selected
   task has a known suitable specialist. Reserve a generalist/automatic manager
   for broad or ambiguous work. A native SpecialistChain is appropriate for
   multi-area goals, not a mandatory routing call on every message.
5. Preserve all current editing, preview, import, follow-up, repair, review and
   undo functionality. Explicitly review the incremental-write cancellation and
   accounting changes described below.

Three framework work packages require review: FW1 read/write iterative planning,
FW2 checkpointed effects and safe recovery, FW3 a shared execution budget and
consistent stop semantics. They are described in section 7. No existing release
or immutable tag is to be overwritten for this work.

## 2. Baseline and Confirmed Gaps

### 2.1 Current configuration assistant

Platform `ConfigurationAssistantService` owns context assembly, conversation
history, an initial generation plus up to two corrections, candidate validation
and the final draft save. It calls the dedicated runtime endpoint
`POST /api/private/configuration-authoring/generate`. The runtime makes a
structured generation call, not a normal configured Brain conversation turn.

Its sessions are actor/resource/task scoped, encrypted in
`platform_authoring_sessions`, limited to 30 stored turns, with up to eight
preceding turns passed to the model. Current live status on both environments
advertised automatic corrections enabled with a maximum of two on 24 November.
This is a useful existing product surface but not the required architecture.

Confirmed local edge case: `NEEDS_INPUT` plus partial changes is saved when
feedback is disabled; with feedback enabled it correctly waits without saving.
The future stop rule must not depend on whether automatic correction is enabled.
Neither changing a status label nor increasing repair rounds fixes this.

Saving an editable draft with ordinary validation findings after exhaustion is
separate, intentional behavior. Preserve it where the existing resource service
allows it. Never treat SAVED as publishReady, connected or high-quality.

### 2.2 Existing framework foundations

- Normal chat already orchestrates registered READ and WRITE actions, including
  writes without confirmation when their actual action policy permits them.
  `DefaultGovernedActionInvocationService` is the execution boundary to reuse.
- `ReadActionResolutionService` iterates only for INFORMATION intent and eligible
  READ actions. It is not a post-write observe/replan loop.
- `ExecutionStrategy.BOUNDED_ITERATIVE` currently requires an iterative READ
  mode in `SpecialistCapabilityResolver`; the enum name is broader than the
  actual implemented contract. This needs a real extension, not a renamed flag.
- `SpecialistWritePolicy.ACTION_POLICY` already follows each action's
  confirmation policy. Do not force all writes through manual confirmation.
- Native SpecialistChain supplies model-selected workers, sequential/independent
  parallel work, manager limits, checkpoints, cancellation and ASK_USER. Native
  fixed ExecutionPlans have predefined stages and are non-interactive; they are
  not an appropriate substitute for an adaptive authoring loop.
- Specialist chains permit bounded repeated target invocation when configured,
  but today's Support compiler fixes `maxInvocationsPerTarget` to one and worker
  writes to DISABLED. Do not globally change that Support configuration.
- Declarative chain worker input maps CHAIN_INPUT or MANAGER_OBJECTIVE, not
  arbitrary completed-result payloads. Objectives are bounded to 500 characters;
  they must not be used as a hidden transport for full draft JSON.
- Existing structured-output finalization retries schema/type validation. Its
  standard feedback does not carry the real Platform candidate-validation
  findings and a changed candidate through an application workflow.
- `SpecialistFinalOutputValidator` is a validation callback, not a generic
  read/write task loop. Application final validators run outside the finalizer's
  schema retry loop. Repeatedly throwing an exception is not the target design.
- One-level delegated specialists reject pending write proposals and child input
  waits. Do not route a confirmation-requiring workflow through that path and
  claim that confirmation continuation is already implemented there.

### 2.3 Existing Support is distinct

The deployed Support Brain and its five read-only specialists/plans are genuine
configured resources. Build with AI is the architectural deviation to migrate.
Do not grant Support chat draft-write access, reuse ticket diagnostic consent as
edit permission, or make every Support request run a configuration workflow.

Keep a dedicated authoring Brain for the capability and budget boundary. A
Support conversation may link to Build with AI with an explicitly selected owned
resource; it does not silently become an editing conversation. A future combined
experience can use the same generic capabilities with separately authorized
request scopes; it is not required to migrate the current authoring surface.

## 3. Target Architecture

```text
Existing Build with AI panel
  | user request, selected page/resource, documentation, preview/correction options
  v
Platform authoring facade
  | verifies original actor, resource, task scope and allowed operation classes
  | creates exact scoped work reference; resolves managed Brain's applied release
  v
Configured Configuration Assistant Smart Brain
  | canonical conversation and ordinary runtime orchestration
  | published instructions, mode, models, action catalogue, workflow limits
  v
Framework bounded iterative planner
  | sees authorized READ + WRITE actions together
  | chooses next action(s), observes receipts/findings, replans or asks/stops
  v
Governed action invocation -> existing connector / scoped Platform API adapters
  | READ current context, contracts, source samples, checks, operation receipts
  | WRITE requested draft changes, follow-up corrections or explicit undo
  v
Existing Platform ownership / resource service / validation / audit transaction
  | actual receipt and updated configuration projection
  +-----------------------------------------------------> planner observation

UI reads actual work status, diffs and receipts; editor reloads saved state.
Publish / apply / index / activate remain separately authorized operations.
```

The Platform facade is transport, context projection and authority, not the AI
planner. It may load a safe initial snapshot without asking the model to discover
the current page, and reject a stale or unauthorized resource. It must not choose
the semantic action sequence, call an inference helper to generate patches, or
loop until model output looks acceptable.

Use the existing canonical runtime route when conversation control is enabled:
`POST /api/chat/me/conversations/{conversationId}/messages`, with the existing
idempotency, history and control APIs. `POST /api/chat/me/query` is the existing
non-controlled route only when appropriate to that runtime's configured contract;
it must not be used to bypass enabled conversation control.

No new model-generation endpoint is required. Platform session/turn routes may
remain UI-compatible facades during migration, but their implementation must
forward real Brain work and return actual receipts, not retain the old engine.

### What proves this is a real Smart Brain

- It has an ordinary deployment identity, draft, immutable published version,
  applied release and verification evidence.
- Its model, prompts, capabilities, scopes and finite workflow policy are managed
  through normal configuration, not hardcoded authoring prompts in Java.
- Ordinary framework traces show actual tool selection, READ/WRITE execution,
  feedback, stop decisions, native model usage and save receipts.
- Changing a published instruction or disabling a capability changes behavior
  without an application code change; denied actions remain denied.
- The dedicated authoring generation endpoint is not an undisclosed fallback.
- A reusable template contains generic authoring contracts. LoomAI-specific API
  bindings are an installation overlay, not a framework platform-admin backdoor.

## 4. The Managed Brain and Its Configuration

Create a first-party Configuration Assistant template using existing Brain
primitives. The resource under edit remains separate from the Brain performing
the edits; do not let an ordinary customer configure the assistant's own secrets,
policy, runtime or scope issuer by supplying its ID as a target.

The published authoring configuration owns:

- Instructions for API/action preparation, supported data mappings, response
  profiles, providers, knowledge, Engage and specialist-team configuration.
- A versioned catalogue of supported schemas and field semantics. Some catalogue
  facts come from the selected resource service at execution time; those are
  data/contracts, not a second system prompt or a grant of permission.
- Model/purpose choices and existing provider settings, including explicit finite
  token limits. Do not silently apply the edited customer's model settings to the
  assistant or inherit a customer's credentials.
- Workflow action names, access/effect metadata, request-scope requirements,
  confirmation policies and registered adapter bindings.
- Optional specialist instructions, approved capabilities and native chain limits.
- Bounded workflow policy, remaining-budget presentation and stop instructions.

Expose these through existing Brain configuration/Advanced interfaces for its
authorized internal operators. Add only missing workflow controls; do not build a
second prompt editor or reapply the rejected customer-wide UI redesign.

### Proposed configuration shape

This is a design sketch, not a currently accepted schema. FW1/FW3 must define
the exact typed names and validation before the compiler/UI emits them. Existing
Specialist execution strategy/write-policy names are retained; new workflow
fields are explicitly additive and default off.

```yaml
# Proposed mode configuration on the managed authoring Brain.
# Reuse the same policy when a Specialist requests BOUNDED_ITERATIVE.
workflow:
  enabled: true
  planning: READ_AND_WRITE
  scopePolicy: VERIFIED_REQUEST_SCOPE
  maxActionSteps: 8
  maxWriteSteps: 3
  maxModelCalls: 16
  maxCorrections: 2
  deadlineSeconds: 140  # ceiling; actual transport/authority may narrow it
  parallelReads: true
  parallelWrites: false

# Existing Specialist concepts, if a worker profile is selected.
execution:
  strategy: BOUNDED_ITERATIVE
  writePolicy: ACTION_POLICY
capabilities:
  actions:
    visible:
      - authoring_get_context
      - authoring_check_candidate
      - authoring_update_draft
    requestableReads:
      - authoring_get_context
      - authoring_check_candidate
    proposableWrites:
      - authoring_update_draft
```

These declarative capability lists cannot create access. The effective catalogue
is their intersection with mode, deployment and current caller/request authority.
The normal conversation pipeline and Specialist gateway must use the same runner
and shared root policy, not separate implementations with independent limits.

Adopt the existing managed assistant service capacity where compatible, rather
than assuming a new server or deployment slot. Inventory ownership, AI-state
storage and funding before adoption. Any additional or resized infrastructure,
capacity or protection change requires separate owner approval with cost impact.

## 5. Platform Capabilities Visible to the Brain

Names below are proposed capability identities, not claims of current endpoints.
Reuse existing resource APIs through narrow registered adapters. No arbitrary
HTTP proxy, SQL tool, JavaScript execution or universal admin API is exposed.

### READ capabilities

- `authoring_get_context`: current authorized resource/task, redacted configuration,
  supported fields, existing references and scoped revision/content fingerprint.
- `authoring_get_contracts`: supported action, routing, plugin, provider, selected
  experience-pack and specialist schemas. No unrelated pack or customer metadata.
- `authoring_read_selected_sources`: bounded documents/operations the user selected
  or explicitly reused, including formal OpenAPI and MCP metadata where applicable.
- `authoring_check_candidate`: apply proposed changes to an isolated in-memory
  candidate and return structured validation findings; no live draft save, model
  call, business execution, indexing or network probe disguised as validation.
- `authoring_get_work_status`: receipts, observed state and prior step references
  for this exact authorized request. Unavailable is not success or zero usage.
- `authoring_read_evaluation`: actual authorized comparison findings and excerpts.
  It does not initiate a paid evaluation.

### WRITE capabilities

- `authoring_update_draft`: save requested bounded changes through the existing
  Brain/plugin/app/document-setting service, with a real operation receipt.
- `authoring_undo_request`: apply the existing inverse-diff semantics to eligible
  saved changes from this request. Check current state; do not silently overwrite
  later unrelated edits or promise rollback of external effects.

These are first-class WRITE tools in the same planner catalogue as the READ tools.
Do not expose a draft writer as READ, mark it grounding-only, or conceal all
mutation behind a read-only `consult_specialist` action. A successful write can
be followed by a fresh read, validation, another allowed write and verification.

Writes use normal action policy. For an authorized draft configuration request,
`requiresConfirmation:false` is supported and appropriate; do not add an
approval click for each correction. A WRITE action that actually requires
confirmation must stop/pause at the existing governed confirmation boundary.

### Separately approved operational capabilities

Safe connection checks, paid evaluation, document indexing, publication, apply,
activation, granting access and destructive operations are not granted by a
normal draft-edit request. Their capabilities may be enabled only with their
actual operation-specific authority, approval and finite allowance. Reuse their
existing workflows; do not infer approval from API documentation or model prose.

Reranking recipient consent and the aggregate USD10 qualification allowance are
not general authoring permission or ongoing production funding.

## 6. Bounded Workflow Autonomy

### 6.1 Verdict

Yes, bounded workflow autonomy is needed for the requested target: the Brain
should accomplish a scoped configuration goal, observe the effects and correct
itself without requiring a precise instruction for each action. It is not a
requirement for autonomous business operations or an unrestricted coding agent.

Do not implement autonomy by raising today's two correction rounds, running a
fixed Platform sequence, or adding a manager that merely calls the old private
generation endpoint. The semantic next step must be the configured Brain's
decision and execution must remain in the framework's governed loop.

### 6.2 A valid model-selected sequence

```text
User: Use this API document to add catalogue search and a confirmed enquiry.
Brain -> READ authoring_get_context
Brain -> READ authoring_read_selected_sources
Brain -> READ authoring_check_candidate(changes)
       <- structured findings: one response mapping needs correction
Brain -> WRITE authoring_update_draft(corrected changes)
       <- actual draft save receipt and content fingerprint
Brain -> READ authoring_get_context (fresh after the write)
Brain -> READ authoring_check_candidate(current saved configuration)
Brain -> WRITE authoring_update_draft(further requested-scope correction), if useful
Brain -> COMPLETE with real changed areas, findings and receipts
```

This is an example, not a forced sequence. The model may skip unnecessary checks,
complete immediately for an explanation, ask for a genuinely missing credential
reference, or stop with remaining findings. No keyword routing or hidden second
assistant generates the proposed values.

### 6.3 Scope and the meaning of within the request

Platform issues a server-owned scope binding to the original actor, workspace,
selected resource(s), task/allowed paths, source selection, request identity,
operation classes, applied assistant release and finite lifetime. The model may
choose permitted actions; it cannot mint or widen that scope.

Page-aware requests default to that page's current task. A whole-Brain request
can authorize several supported configuration areas of the same owned Brain;
the UI must show the affected areas and preserve unrelated settings. Related
Kit/app/plugin resources require verified relationships and normal edit access,
not IDs copied from an attachment or generated by the model.

These enforceable limits do not mathematically prove that every edit matches the
meaning of a natural-language request. Semantic faithfulness remains a measured
model-quality requirement. Keep diffs and undo visible; do not introduce lexical
validators or pretend a model-written justification is authorization.

### 6.4 Limits proposed for review

Proposed initial workflow ceilings: eight action steps, at most three draft-write
steps, up to two typed validation/parameter corrections, one overall deadline no
greater than the current 140-second Platform ceiling, and an explicit aggregate
model-call ceiling (proposed 16, to be qualified from actual traces).

These are proposals, not current settings or spending approvals. All routing,
planner, schema-repair, worker, manager and narration calls count toward the same
root allowance; do not multiply independent nested retry budgets. Every native
provider operation remains subject to existing financial/token enforcement.

The effective deadline is the narrowest of root policy, runtime transport,
conversation lease, signed authority and funding lifetime. Current normal runtime
transport and Support bridge use 120-second bounds, so full 140-second authoring
parity must not be assumed. Qualify a shorter effective inline run first; if
long-running execution is necessary, reuse native async execution handles and
explicit status/continuation contracts, not an unreviewed background loop or a
larger HTTP timeout. No infrastructure change follows from this proposal.

Independent safe reads may be parallel. Draft writes are sequential by default.
Do not parallelize writes to the same draft or allow dependent actions to use
parameters guessed before the previous result arrived.

### 6.5 Stop and interruption behavior

- NEEDS_INPUT/ASK_USER stops further execution regardless of repair preference.
  A later user answer is a new authorized turn with actual prior work references.
- Preview/explanation permits no target draft writes, even if the model asks for
  them. Validation can use an isolated candidate; that is not a real save.
- Actual authorization denial, revoked delegation, insufficient budget, missing
  consent and unavailable providers do not enter a make-it-pass repair loop.
- Typed parameter/schema or ordinary candidate-validation findings may enter the
  configured repair allowance. Warnings alone and model prose do not trigger it.
- Exhausted work reports partial completion and actual effects. Do not fabricate
  success, erase findings or claim the saved state was unchanged after writes.
- Cancellation prevents new dispatch and late commits. Already committed draft
  edits remain visible and undoable; cancellation is not transaction rollback.
- A successful write followed by model/narration failure stays executed. Its
  receipt must survive and it must not be run again to obtain a better answer.
- A timeout with an uncertain write outcome requires receipt lookup/reconciliation.
  It is not permission to try the same write under another operation key.

Missing internal planning attributes use typed bounded repair, never a request
for the user to select a knowledge domain or write internal JSON. If repairs are
exhausted, a final answer may use already approved context/receipts only when an
explicit remaining model-call allowance permits it; no new tool call or write
replay. During total provider failure the UI can show truthful structured work
status, but must not claim that it obtained an AI-generated answer.

## 7. Required Framework Changes for Review

### FW1 Scoped read and write iterative execution

Confirmed gap: current iterative service only handles INFORMATION/READ planning;
ordinary ACTION handling executes extracted single/compound intents without an
observe/replan cycle after each write. Compound intents alone do not solve result
dependencies or validation-feedback correction.

Proposal: extend existing BOUNDED_ITERATIVE execution with an explicit opt-in
workflow action policy. Retain the existing action registry, effective capability
intersection, mode policy, structured-call executor, parameter resolution,
governed action invocation and normal orchestration endpoint.

The workflow planner receives the original request, scoped READ and WRITE
metadata, current approved context, real prior observations and remaining budget.
It emits a typed next-action decision, completion, input request or unsupported
result. The model sees side-effect/confirmation metadata for both kinds of tool.
Every dispatch revalidates capabilities and calls the same governed boundary.
Build the mixed catalogue from effective visible/executable capabilities, not
the existing READ-only eligibility flag; otherwise valid writers would disappear
from the planner. Metadata visibility is still not permission to execute.

Implementation boundaries:

- Core exposes the generic workflow runner/policy/checkpoint SPI; it must not
  depend on the higher-level execution module or know Platform field names.
- Execution integrates this posture with specialist profiles and existing durable
  execution infrastructure. Runtime wires persistence, auth and accounting.
- `SpecialistWritePolicy.DISABLED` retains read-only behavior. ACTION_POLICY
  permits declared unconfirmed writes; confirmation-required actions retain their
  existing pending-action/proposal lifecycle and resume revalidation.
- Do not relax the existing one-pending-write-proposal contract to execute a
  whole batch of confirmation-required actions implicitly.
- A post-write read is fresh. Existing READ deduplication must not hide a new
  resource state by reusing pre-write observations.
- Validation errors are typed observations for replanning, not a generic provider
  error or falsely successful save. Raw secret-bearing exceptions stay private.
- The new path is disabled by default; ordinary existing chat, Tasks, read planning
  and Support chains retain their established behavior.

Required tests: model selects READ then WRITE then fresh READ; corrected second
write; no-confirmation write; confirmed write pauses/resumes correctly; denied
action/path/scope; preview blocks writes; compound dependencies; no vector-source
requirement for a tool-only workflow; ordinary existing modes unchanged.

### FW2 Durable tool-step receipts and replay-safe recovery

Confirmed gap: existing chain checkpoints track manager/worker steps, not a
general read/write action loop. Existing governed invocation reports EXECUTED or
unknown outcomes, but that alone is not a durable per-step idempotency protocol
for a new workflow. Durable specialist execution is not proof of tool-step resume.

Proposal: extend the existing execution persistence and protected checkpoint
adapters for workflow steps, rather than create a second general job engine.
Core uses the SPI; JDBC/codec integration stays in the execution/runtime layer.

Before a mutating dispatch, persist an operation identity and scoped parameter
fingerprint. Record actual EXECUTED/REJECTED/CONFIRMATION_REQUIRED/OUTCOME_UNKNOWN
state, safe result references and resource fingerprint after dispatch. Reconcile
uncertain writes through the application's existing receipt adapter before any
repeat. Platform must atomically persist an idempotency receipt with its own
draft mutation; the framework cannot guarantee exactly-once external effects
without that receiver contract.

Different steps may legitimately call the same writer with different changes.
Do not deduplicate all writes by action name or permanently by equal arguments.
HTTP retry, workflow recovery and a genuinely new user request are distinct.

Result envelopes retain executed step references even when final generation,
structured binding, cancellation or budget admission later fails. Model text
cannot overwrite or manufacture these receipts. Reauthorization, scope/version
checks and remaining deadlines apply at resume; no persisted user bearer tokens.

Required tests: process interruption before dispatch, after commit before reply,
after receipt before narration; lost response; repeated root key; changed payload
under same key; revoked actor; stale target; duplicate write prevention; legitimate
corrective second write; failed final answer with truthful completed effects.

### FW3 Shared execution budget and stop semantics

Confirmed gap: current policies bound READ iterations, specialist calls, manager
decisions and structured generation separately. These are not one aggregate
workflow-call/mutation/repair budget. The current authoring three-attempt limit
must not turn into three attempts at every nested level.

Proposal: one root execution allowance inherited by the workflow and any child
specialist. Count all admitted model calls, action steps, mutating steps and typed
repairs; apply one deadline and cancellation token. Reuse current guard and
provider-accounting propagation; do not invent a new financial ledger.

The configured Brain defines ceilings; per-request preferences can only narrow
them. Stopping for missing information is independent of correction settings.
Provider/network/funding failures stop, while actual earlier writes retain their
receipts. Optional final narration needs an explicitly reserved remaining call;
if unavailable, show actual structured work status, not a canned substitute AI
answer or another unconfigured model retry.

Required tests: nested workers cannot reset limits; schema repair and planner
repair count; cancellation before write and after write; NEEDS_INPUT with repairs
both off/on; no hidden fallback inference; every provider charge remains accounted.

### Changes not currently justified

- No new top-level deployment behavior type, unrestricted agent platform, coding
  sandbox, Python agent subsystem or external autonomous-agent framework.
- No need to make existing fixed ExecutionPlans adaptive or enable writes for
  every Support worker.
- Do not add a new finalizer-specific Platform validation retry engine when
  normal scoped validation actions can return structured feedback to FW1.
- A richer completed-result mapping source for declarative chains is optional,
  not assumed necessary: workers may read an owned bounded work-state reference.
  Propose that extension separately only if qualification demonstrates the need.
- Do not move account/tenant authorization or edit permission into an LLM prompt.

## 8. Platform and Runtime Work

### 8.1 Authority and API adapters

Issue an exact authoring work scope after normal customer/staff delegation checks.
Bind it to the original caller and selected resource/task; the shared assistant's
internal deployment identity is never authority to edit every customer.

Use the existing private service authentication and signed requester-context
patterns, with purpose-specific audience and scopes. Model/browser-supplied
context is data, not a signed grant. Reject swapped work, actor, source, resource,
target and release IDs before returning observations or changing state. Recheck
active membership/delegation on every API read/write and on continuation.

Adapters call `AuthoringResourceService` and existing Brain/plugin/app/document
services. Preserve supported schemas, field-delta merge, unknown values, current
manual-edit authority, active-app preview rules and original source ownership.
Never forward raw secrets, access cookies, signing keys or full unredacted drafts.

### 8.2 Draft concurrency and idempotency

Read fresh configuration before each save. Rebase the requested field changes
through existing merge behavior and revalidate under the normal short transaction.
Manual values within the user's requested change scope may be replaced, as already
approved; preserve unrelated edits. Do not hold a DB transaction open across LLM
calls or introduce a universal writer-version framework.

Plugin/app/source revisions retain their existing contracts. Brain draft
`revisionNumber` is not a per-edit content version: record a bounded configuration
fingerprint/actual save receipt for work observations, not a fictional revision.
Trust-relevant changes to credentials, ownership, task scope or bindings require
fresh authorization, not an automatic merge through a stale grant.

Add a narrowly scoped operation receipt only where the current resource mutation
does not already supply atomic idempotency. It belongs in Platform's existing
database/transaction, not a customer business database. Every retry uses the
original operation identity until its outcome is known.

### 8.3 Runtime integration

Resolve the authoring Brain through its actual verified release and authority;
do not keep a fixed generation URL outside the deployment model. Reuse canonical
conversation control and registered actions. Add only generic runtime wiring
needed by FW1-FW3 and the declared Platform adapter bindings.

Preserve the currently approved provider allowance and ledger history. Ordinary
chat ingress and dedicated authoring ingress require different scopes today;
explicitly migrate the binding/identity to the verified managed Brain using the
existing activation protocol. Do not copy/reset usage, treat chat scopes as
authoring permission, create another USD5 grant, or replay previous top-ups.
Model-call accounting must include planner, tool-related generation and repair.

Reranking/embedding/evaluation remain separately funded where required. No API
key, recipient consent or budget is created just by configuring the Brain.

## 9. UI and Current Functionality Preservation

Keep the same dock/panel, page-aware welcome, prominent Build with AI entry,
selected documentation, optional user hint, preparation commands, manual forms
and Advanced JSON. Do not add another assistant or duplicate resource editor.

Required parity:

- API import accepts supported pasted/text/file/public-link inputs and formal
  OpenAPI operation selection. Public/no-auth needs no invented credential.
- All selected supported operations are handled; parameter schemas, URL/path
  routing, mapping and action evidence settings remain editable.
- MCP discovery/import stays mechanical; assistant edits retain native bindings,
  observed schemas, account identity and existing security semantics.
- Data-plugin definitions and installation inputs stay distinct; preserve required
  fields, defaults/hints, mapping, pagination and declared synchronization behavior.
- Knowledge/storage/model/provider/response-profile editing uses the same forms
  and safe references, without implicit provisioning, indexing or provider access.
- Engage uses only the selected pack's contract and keeps active apps preview-only.
- Specialist/manager configuration preserves installed authority, unrelated plans,
  existing required-plugin findings and the actual publication blockers.
- Local 010.73 document preparation, SEARCH, comparison-context and reviewed-label
  safeguards are preserved as pending additions, not falsely described as live.
- Follow-ups retain the current resource and explicit source selection. No silent
  attachment from another Brain/task, and no user requirement to write JSON.
- Preview/explain, read-only, cancel, retry-readback, saved diffs and undo remain.
- Lost HTTP responses read back the same work; editor refresh failure is not a
  reason to generate or execute the changes again.

### Deliberate behavior changes requiring review

An iterative workflow can commit more than one draft write. Display each actual
save and aggregate the request's diff/undo group. A cancelled or failed request
may already have changed the draft; replace old no-change promises with actual
receipts and an eligible Undo action. Do not silently auto-undo prior work.

Automatic-correction controls govern corrections, not permission to ignore an
input request. Zero corrections still allows the separately authorized planned
steps, but does not authorize semantic retry after a typed failure. Scope, action,
model-call and mutation limits remain finite.

Show current activity as real states such as reading, checking, updating draft,
waiting for input and complete with findings. Do not animate an invented tool
sequence or use model prose as a save/verification receipt.

## 10. Storage and Conversation Migration

- Canonical conversation text moves to the Brain runtime's existing conversation
  storage and owner identity. Do not keep two active transcripts.
- Platform retains owned work/resource references, selected-source metadata,
  scope/approval records, per-action idempotency receipts, diffs and audit in its
  existing database. Full private source bodies keep current encryption/access
  and retention, rather than being indexed globally for convenience.
- Existing authoring transcripts remain readable as legacy history until their
  existing expiry/deletion rules. Never replay them as fresh user instructions.
  Starting a Brain-backed continuation must be explicit; selected source reuse
  is authorized and attributed, not an automatic cross-resource import.
- A work item records its execution backend/version and cannot switch backend
  halfway through a write. No dual-write shadow qualification.
- New job/session deletion and removing imported sources must account for both
  runtime history and Platform source/work stores. Do not claim erasure while
  retaining the same imported body in another active store; audit/usage receipts
  retain their lawful operational retention separately.
- Existing customer S3 guides, vectors, source/index identities, templates, Kits,
  original Harbor and owner practice resources are not reset or migrated as
  assistant implementation data.

## 11. Specialists and Resolution Plans

The workflow runner is useful without a specialist team. Use one configured
authoring Brain with task-specific specialist resources so a known single-area
edit need not pay for a manager-selection call. Initial configured profiles can
cover the already supported authoring areas:

1. API capabilities and routing.
2. Data plugins and knowledge mappings.
3. Responses, models and supported behavior settings.
4. Engage branding and workspace configuration.
5. Specialist-team configuration.
6. Document/search quality when its underlying operations are available.

### 11.1 Explicit selection from page and task

Recommendation: yes. Use the typed selected editor/task to resolve the configured
specialist directly when that task has an approved binding. This is application
selection of a declared execution profile, not keyword interpretation of the
user's message or another set of hardcoded prompts.

Proposed default bindings:

- ACTIONS and CONNECTIONS -> API capabilities/routing specialist, retaining each
  task's distinct write paths.
- KNOWLEDGE, LIVE_DATA, STORAGE and private DATA_PLUGIN -> data/knowledge
  specialist with the exact selected resource kind and supported source contract.
- DOCUMENT_PREPARATION and SEARCH -> document/search-quality specialist, with
  different source-setting versus Brain-provider permissions.
- BEHAVIOUR and MODELS -> behavior/model specialist, again with distinct task
  permissions and current model/provider contracts.
- ENGAGE and HUMAN_SUPPORT -> workspace/channel specialist, retaining the
  selected pack and active-app/installed Desk-binding restrictions.
- SPECIALISTS -> specialist-team author, able to edit only supported team/manager
  configuration, not grant itself worker authority.
- KIT, action-review explanations and navigation-only views -> an explicitly
  guide-only profile, not the writable version of the same specialist.
- Whole-Brain or genuinely multi-area setup -> automatic manager/generalist with
  only the approved targets needed for the request; not every worker by default.

Sharing a specialist's expertise does not share or union its tasks' permissions.
For example, choosing the same profile for SEARCH and DOCUMENT_PREPARATION must
not permit a document-setting request to change Brain providers or consent.

Routing precedence:

1. A user explicitly chooses an allowed specialist/profile, when that option is
   exposed. Validate it against the selected task and installed published version.
2. Otherwise use the managed Brain's explicit page/task binding.
3. Otherwise use its declared automatic route. If no authorized route exists,
   return a clear unavailable state with manual editing, not a hidden helper.

The mapping lives in published Brain/template configuration, not Java page-name
switches. Resolve its exact specialist version/hash at the start of the turn;
persist the actual selection in receipts. Template edits affect later turns,
not a running job. A page change creates a new typed task context for subsequent
turns; it does not widen or redirect an active writer.

The panel can show a compact label such as API specialist with an optional
Automatic/allowed-specialist menu. The user does not have to select a worker to
use the assistant. Administration uses a form mapping registered task IDs to
installed specialist profiles, with full JSON remaining optional in Advanced.

Inside a pinned specialist, the LLM still selects READ/WRITE actions and their
sequence. Pinning expertise does not force a tool script. If the request needs
another area, use an explicitly allowed escalation/automatic route with the
actual broader request scope and visible selected profile. Do not silently gain
another resource or reset budget/history by switching specialists.

### 11.2 Existing SDK support and Runtime wiring gap

Framework `AIExecutionRequest` already takes an exact `SpecialistId` plus trusted
context, conversation binding, deadline and idempotency key.
`AIInteractiveExecutionGateway` already coordinates that selected specialist's
backend-owned dialogue turn. No new core specialist-selector API is evidenced as
necessary merely to pin a known worker.

However, the current runtime `ChatQueryRequest` has no trusted specialist-selector
field; the existing Build with AI path does not wire this SDK. This requires a
scoped Platform/Runtime dispatcher binding the published page/task route to the
existing gateway. A value in user-controlled context, a prompt saying use worker
X, or another hand-built generation endpoint is not that integration.

Keep canonical conversation admission, actor identity and the selected Brain
release. Ensure exactly one component owns the interactive turn: the Runtime
conversation controller and `SharedInteractiveTurnCoordinator` must not acquire
independent nested turns or duplicate user/assistant messages. Prove approved
snapshot/lease handoff with integration tests. If existing interfaces cannot
support that binding without a core extension, return for review with a specific
additional framework patch; do not assume it works or bypass the turn guard.

This uses genuine configured specialist/framework execution. The canonical
message route remains the ingress, not the old private generation endpoint.

Each worker sees only its actual READ/WRITE capability subset and task scope.
Allow writes explicitly where requested; do not claim all specialists are
read-only while allowing mutations inside their tools. Reads may be parallel;
dependent writers use the same serialized root work scope and budget.

Use native SpecialistChain only for adaptive multi-area delegation. The manager
can choose useful workers/order, recheck findings, ask or finish; no fixed worker
count or canned sequence. Existing Support chains stay unchanged. Child pending
confirmation/input restrictions must be respected: route an approval wait to the
dialogue owner, or stop with a typed requirement. Do not quietly drop it.

## 12. Validation Evidence and Qualification Limits

Source baseline: private Platform HEAD56abfeaf11d55e872c0ea743aa4e79ca022089fe with
existing uncommitted010.73changes preserved; framework worktree source
f933227e2df814b8648bb1582bcead67f2e92f0f, candidate0.8.67. Last recorded deployed
Support uses0.8.66. This assessment is not deployment or Maven publication proof.

Existing framework contract suites were run during planning, using scripted
providers only:

- Execution:119testsPASS, no skips. Covers specialist manifests, chain manifests,
  specialist/chain gateways, fixed plans and structured output finalization.
- Core:81testsPASS, no skips. Covers governed action invocation, its architectural
  execution boundary and READ action resolution.
- Interactive selection:18testsPASS, no skips. Covers exact specialist interactive
  execution and conversation-manager gateway contracts. This does not prove the
  proposed Runtime dispatch or controller-to-gateway turn handoff is wired.

Total:218distinct existing contract tests passed, with no failures or skips.

These passing tests validate existing primitives; they do not prove the proposed
read/write iterative feature exists. FW1-FW3 require new tests and implementation.
No public source, production configuration, paid inference or deployment changed.

Retained logs:
`~/.codex/private/loomai-hybrid-retrieval-01073/authoring-migration-framework-contracts.log`
and sibling logs `authoring-migration-core-contracts.log` and
`authoring-migration-explicit-specialist-contracts.log`. The earlier local
stopping-rule probe uses actual session transactions with mocked resource/model
services; its final run confirms the disabled-feedback NEEDS_INPUT defect
without live writes.

## 13. Implementation Phases and Gates

### M0 Owner review and frozen parity baseline

Approve or amend FW1-FW3, request-scope semantics, incremental writes/cancellation,
root limits and migration boundaries. Capture representative existing UI/API
scenarios, schemas, encrypted-history behavior and actual native call costs.
Freeze a generic Harbor API/source fixture plus non-dealership cases. Do not
change current templates, assistance or ordinary Support to make a baseline pass.

### M1 Framework contracts and deterministic qualification

Implement reviewed FW1-FW3 in the public framework with default-off configuration,
failure-injection and regression tests. Reuse governed invocation and execution
storage; do not fork confirmation, tenancy, provider or chat engines. Add capability
attestation so Platform cannot offer the feature on an unsupported artifact.

Publish only a newly available immutable version after its release gates and
owner release approval. The processing0.8.67upload/tag is not to be replaced or
republished. Record actual consumption from Central, not locally installed jars.

### M2 Platform adapters and managed Brain configuration

Implement actor-bound work scopes, narrow action adapters, atomic receiver
receipts and published authoring templates/profiles. Add workflow controls and
validation to existing Brain editors. Bind the assistant through its normal
deployment/release path. Preserve funding history and current infrastructure.

### M3 UI adapter and legacy history

Connect the current panel to canonical Brain conversations and genuine work
events/receipts. Preserve all parity items, manual editing, preview and undo.
Label existing transcripts; never secretly rerun a legacy turn on the new Brain.
No provider/model call is started just by opening a page or importing a document.

### M4 Staging qualification

Use normal customer and delegated-staff identities. Run every scenario in section14
with actual native provider receipts within existing approved funding. Compare
quality, costs, latency and unnecessary clarifications against the frozen old
flow, not just HTTP200or an SDK test. Retain failures and partial writes.

### M5 Production migration and live proof

Deploy exact qualified artifacts, then verify the managed assistant release and
scope issuer before routing new jobs. Pin each job/conversation to one backend.
Run canary journeys, including Build with AI from a clean generic Brain, partial
failures, cancellation, delegation revocation and mobile. Existing Support,
Engage and unrelated deployments must retain their verified assignments.

New-path failure must not silently call the old generation helper. Manual forms
remain available. An explicit operator rollback may restore the prior qualified
release for new jobs, with its architectural limitation visible; it is not an
undocumented permanent alternate product path.

### M6 Retire the custom inference workflow

Remove Platform's model-generation/correction loop from
`ConfigurationAssistantService.run`, the direct `AuthoringRuntimeClient.generate`
integration, private runtime generation controller/service, and hardcoded task
prompt ownership once new jobs and retention dependencies have drained.

Keep reusable resource adapters, imports, merge/check/save/undo contracts and
necessary legacy read-only history support until scheduled expiry. Migrate
versioned instructions to the managed Brain. Revoke obsolete service credentials
only after confirming they are not shared or still required; retain funding and
audit history. Remove temporary feature routing after the rollback window.

## 14. Acceptance Scenarios

- A01: create actions from a generic OpenAPI document, all operations and an
  explicit subset; retain required inputs, public auth semantics and existing
  siblings. A follow-up corrects a route without reconstructing unrelated config.
- A02: actual READ -> WRITE -> READ -> corrected WRITE -> completion through the
  configured Brain. Trace every chosen tool; no private generation helper call.
- A03: preview/explanation cannot write; active Engage app remains preview-only.
- A04: edit data-plugin mapping from real sample fields, preserve installation
  boundaries, missing ownership/ID facts and required credential references.
- A05: configure models/response presets and specialist manager instructions;
  preserve unknown/unrelated values and visible publication prerequisites.
- A06: configure document processing/questions and SEARCH from actual evidence;
  no automatic indexing/evaluation/recipient consent or AI-invented human review.
- A07: no-confirmation draft writes execute; a genuinely confirmation-required
  action waits without execution and resumes through its authoritative receipt.
- A08: a caller cannot edit another customer's resource, swap a target/work ID,
  reuse revoked staff access or escalate through the assistant's internal identity.
- A09: prompt injection in API docs, imported text, attachments and tool results
  cannot grant actions, replace prompts/identity, expose secrets or widen scope.
- A10: root and per-step retries do not duplicate writes or provider admission.
  Lost acknowledgements reconcile; distinct corrective writes remain possible.
- A11: final model failure after a save reports the actual save; cancellation
  stops later writes and retains prior edits/Undo rather than claiming rollback.
- A12: NEEDS_INPUT with nonempty proposed changes stops both with corrections off
  and on. Missing information is asked once; answering continues with fresh context.
- A13: provider/budget failure stops rather than invoking a fallback assistant or
  regenerating until green. Already executed effects and usage stay truthful.
- A14: restart recovery, stale schema/release, expired approval and uncertain
  writes fail safely without replay; actual latest observations are not cached
  from before the write.
- A15: grouped Undo respects later edits. Manual changes within requested scope
  remain replaceable as approved; unrelated fields are preserved.
- A16: old history remains readable; new transcript is canonical, requester-owned
  and deletable under its real retention rules. Cross-task source reuse is explicit.
- A17: fresh customer/delegated-staff desktop and360/390px light/dark flows pass,
  including editor reload failure and read-only/no-permission presentations.
- A18: changing the assistant's published prompt/capability configuration changes
  behavior through normal release/apply, proving configurability rather than a
  code-only helper. Capability removal makes its tool unavailable at execution.
- A19: current ordinary Support, its five specialists/plans, standard chat,
  managed Tasks and existing READ-only iterative profiles have no regression.
- A20: characterize costs/call counts and quality for simple edits, broad setup
  and repair cases; no live rollout merely because the new endpoint returns200.
- A21: the Actions page selects its configured API specialist without a manager
  selection call, while that specialist chooses scoped READ and WRITE actions.
- A22: wrong-task, unpublished, disabled or unauthorized specialist selection is
  rejected before inference/dispatch; guide-only selection cannot inherit writes.
- A23: page change, explicit allowed override, whole-Brain escalation and an
  unavailable worker retain truthful history, narrow scope and the root budget.
- A24: one canonical interactive turn owner, no double transcript or nested turn
  lock; exact selection and configuration hash survive retry and resume.
- A25: changing the published task-to-specialist binding changes future routing
  without application code changes or broadening installed action authority.

## 15. Definition of Done

The migration is complete only when Build with AI routes through a verified
configured Brain; its planner actually sees scoped READ and WRITE Platform
actions; real effects and findings drive bounded subsequent decisions; current
editing functionality is retained; failure/replay/security/quality journeys pass
on staging and production; and the custom direct-generation workflow is retired.

Remaining model uncertainty stays visible. No claim of deterministic reasoning,
perfect natural-language scope interpretation, exactly-once arbitrary external
writes or universal autonomous business operation follows from this migration.

## 16. Source Anchors

Paths are repository-relative unless prefixed by the framework worktree. Review
the named methods rather than relying on historical deployed-version notes.

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/assistant/ConfigurationAssistantService.java`:
  `turn`, `run`, `evaluateAttempt`, `input`, `complete`, cancellation and storage.
- `.../assistant/AuthoringFeedbackPolicy.java`: current correction ceiling and
  eligibility; `AuthoringResourceService.java`: supported resources/check/save/undo.
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/authoring/`:
  existing private controller, one-call inference, hardcoded instructions/schema.
- `.../runtime/web/ConversationHandoverController.java`: canonical message admission,
  idempotency and conversation-control boundary.
- `.../runtime/budget/RuntimeBudgetTurnScopes.java` and `RuntimeBudgetBindingService.java`:
  actual request admission, inherited task accounting and distinct auth scopes.
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/support/runtime/SupportRuntimeAccessService.java`:
  exact release/requester authority patterns; do not inherit ticket edit permission.
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/specialist/SpecialistAssistanceCompiler.java`:
  current read-only workers and one invocation per target.

Framework worktree:
`/Users/mahmoudashraf/.codex/worktrees/hybrid-retrieval-01073`.

- `ai-infrastructure-module/ai-fabric-core/src/main/java/ai/fabric/intent/orchestration/information/ReadActionResolutionService.java`:
  INFORMATION/READ-only loop and prior-observation planning.
- `.../orchestration/pipeline/steps/IntentHandlingStep.java`: single/compound
  ACTION handling and governed dispatch, not a general result-dependent workflow.
- `.../intent/action/invocation/DefaultGovernedActionInvocationService.java`:
  authorization, parameters, confirmation and actual/uncertain outcomes.
- `ai-infrastructure-module/ai-fabric-execution/src/main/java/ai/fabric/execution/gateway/SpecialistCapabilityResolver.java`:
  effective capability intersection and current BOUNDED_ITERATIVE restriction.
- `.../gateway/DefaultStructuredSpecialistOutputFinalizer.java` and
  `.../specialist/manifest/JsonSchemaSpecialistOutputAdapter.java`: schema retries
  versus external final validation; not the new workflow engine.
- `.../gateway/DefaultSpecialistChainGateway.java`, `.../chain/SpecialistChainLimits.java`
  and `.../chain/state/SpecialistChainCheckpoint.java`: existing native adaptive
  manager and durable checkpoint concepts to reuse.
- `.../gateway/AIExecutionRequest.java`, `AIInteractiveExecutionGateway.java`
  and `DefaultAIInteractiveExecutionGateway.java`: exact application-selected
  specialist and canonical interactive turn coordination.
- `.../chain/manifest/SpecialistChainMappingSource.java`: actual input mapping limits.
- `.../delegation/OneLevelSpecialistTransitionEngine.java`: pending confirmation/input
  restrictions, which must not be bypassed for writable workers.
- `.../plan/AIExecutionCoordinator.java`: fixed-plan semantics, not autonomous planning.

Related plans:010.37owner correction,010.72document preparation,010.73retrieval
optimization and010.74queued staff migration. This plan does not silently close
or implement any of those pending work packages.
