# Support Continuity And Specialists

## Ownership And History

Console Support uses the installed Support experience pack and its canonical
runtime conversation. Platform stores the requester/conversation reference,
selection time, selected diagnostic resources, consent and revision. It does not
store a second transcript. Opening Support resumes the last selected ordinary
conversation; New chat explicitly creates an empty scope. History is requester-
scoped, including customer, user, issuer and subject, not shared with every member
of the workspace. Membership is checked again before reads.

Ticket-linked conversations use the ticket's authoritative episode and lifecycle.
Adopting ordinary chat into a ticket removes it from ordinary history/resume.
An old ordinary route cannot bypass closure, cancellation or a newer episode.
Selection/adoption and consented diagnostic reads use the same requester/scope
locks. V244 adds metadata to the existing conversation-scope table only.

## Diagnostic Consent

A ticket is not required to inspect a selected owned resource. Configure
diagnostics in ordinary chat or a request, save the selected resources, then
grant permission. Changing resources revokes permission. Revocation stops later
private reads, not observations already shared in history. Merely visiting a
Brain, typing an ID or attaching an object does not authorize a resource lookup.

All four compound diagnostic tools retain the dedicated service-key authentication,
verified original requester, active membership, ownership, explicit resource
selection and consent. The exact connector routes are registered individually;
there is no wildcard anonymous diagnostics API. A normal customer token cannot
call the internal connector route directly. Child specialists preserve original
authority and cannot invent target IDs. Ticket reads also require the current
open authoritative episode.

- `support_get_case_summary`: current linked request metadata; ordinary chat can
  legitimately have no request.
- `support_get_deployment_status`: recorded states and bounded release records,
  including target, environment, version and verification. Exact document index
  observations require a selected connected app. `integrationConfiguration`
  contains bounded saved-draft capability metadata, not endpoints, credentials,
  raw configuration or business payloads. Neither projection executes business
  tools or proves live health/connectivity. Check setup remains the canonical
  compound connectivity workflow.
- `support_get_engage_readiness`: bounded local configuration checks. Not a
  delivery, provider connectivity or HTTPS probe. `handoverConfiguration` reads
  selected app visibility and its exact assigned verified release, not the draft.
  Agent presence, message delivery and ticket-provider health remain unprobed.
- `support_get_connection_sync_status`: stored synchronization observations, not
  a new upstream source check.

Missing selection/consent is returned as a safe structured requirement with no
resource observations. The model should explain the corresponding UI step, not
present it as a successful resource check. Source authorization, approved document
filters and release-specific runtime authority remain separate checks.

Support diagnostics project local-only `APP_DELIVERY` and `SUPPORT_SPACE` skipped
probes as `NOT_PROBED`, not a provider outage. `localReadinessPassed` covers the
actual configuration checks; the diagnostic remains INCOMPLETE and does not
establish full app readiness. Existing activation/readiness APIs retain their
full verification gates. No raw check exception, URL or credential is exposed.

## Specialists And Plans

The internal LoomAI overlay defines Workspace and channels, Brain and release
health, Knowledge and search, Tools and integrations, and Support and human
handover. Its five plans cover workspace/data investigation, knowledge freshness,
API/MCP setup, launch readiness and safe next-step guidance.

The reusable company Support template defines five company-neutral areas and
five corresponding plans. Its default READ-tool lists are empty and contain no
LoomAI diagnostic service bindings. Customers attach their own approved knowledge
and explicitly installed READ tools through the existing Support capabilities
editor. The LoomAI private overlay is an installation configuration, not a new
framework behavior type.
Fresh templates preconfigure the five areas and plans with assistance off until
the required sources/tools are connected. This preserves configuration-only
bootstrap when an owned document-storage binding does not exist yet. LoomAI's
already-connected production installation enables them through its normal draft
and release flow. The two areas of another company's custom installation are not
silently replaced by the new generic defaults.
The reusable template also retains its company-neutral system prompt: internal
Console diagnostic controls and Build with AI navigation are not assumed to
exist in another company's end-user support experience.

Plans use native SpecialistChain resources. The LLM manager chooses relevant
workers, order, sequential/independent parallel checks, clarification and
completion. Limits are six manager decisions, four worker invocations, two
parallel workers and 75 seconds. Each target can run once. Requested parallel
results remain all-required; failures and incomplete findings stay visible.
Simple questions may use direct approved retrieval without a specialist.
The generated manager output schema caps each target selection at the configured
parallel/worker budget, or one for sequential plans. The complete approved target
catalogue and supporting-result references remain available for later decisions;
this does not force a worker order or increase execution limits.

Compiler guidance separates worker-tool input requirements from conversation
attachment targeting. Parameterless diagnostic tools resolve selected resources
through the authenticated backend; they do not need an invented attachment or
customer-supplied internal ID. Tools that genuinely require identifiers retain
their declared inputs. The manager chooses its next check from the question and
approved targets; internal preparation status is not a worker observation, and
an empty initial result list is not evidence of denied access. These are prompt
instructions, not forced worker sequences or a new semantic rejection gate.

### Manager Planning Prompt

The owner-approved 0.8.65 correction gives existing chain-directive contracts a
planning-specific system prompt. Server-owned specialist instructions and output
format stay in the system role; application input and grounding stay in the user
role as untrusted data. Empty completed results mean investigation has not yet
run, not that evidence access was denied. Preparation readiness is not a business
observation. The manager can choose relevant approved workers, reassess their
results, and complete when useful investigation is done or genuinely unavailable.

This adds no mandatory worker count, fixed sequence, authority, tool, write,
repair round or budget. Ordinary specialist finalization remains unchanged.
Release and live activation evidence belongs in the dated verification record.

Console exposes each area's instructions and each resolution plan's objective,
optional manager instructions (4000 characters), exact approved areas and parallel
setting in the existing Specialists editor. Shared execution limits remain in
Advanced, not duplicate per-plan budgets. The canonical Specialists page includes
that depth switch. Advanced reads coordination prompts and effective limits from
the exact published resource references; it never substitutes the current draft
for a missing published profile. The framework's generic planning system prompt
is inherited from the released runtime, not represented as an editable draft field.

Manager instructions use the existing behaviorConfig draft API, compile into its
SpecialistPromptProfile, and follow explicit publish/apply. Omitted or empty
instructions leave existing compiled resources unchanged. Build with AI's existing
SPECIALISTS task can edit this field and must preserve unrelated instructions.
Customer admins and normal Platform admin/operator sessions reuse this editor.
Staff access comes from the existing owned-resource/delegation workspace API, not
from displaying the form. Save rechecks actor, role, account, resource ownership,
edit permission and draft revision; revocation never grants a UI-only bypass.

### Build With AI Draft Checks

Navigation: open the selected Smart Brain -> Human support -> Specialists.
Under Configuration, edit a resolution plan to change Manager instructions;
save the draft, then use Updates for explicit publish/apply. Advanced exposes
the exact published coordination prompt and effective execution limits. It is
a read-only runtime preview, not a second prompt editor.

The SPECIALISTS catalogue maps the UI's Manager instructions label to each
existing resolution's exact `instructions` JSON Pointer. Prefer that narrow
field edit and preserve unrelated plans, limits, enablement and existing unknown
metadata. New undeclared fields are not writable through the AI task, even when
assistance is off. This protects the editor's supported configuration contract,
not a semantic confidence threshold. Invalid additions use the existing bounded
feedback loop; exhausted unsupported mutations cannot be saved. Explicit Undo
can restore its recorded historical shape through the existing owned-resource
and complete draft-validation path. Manual JSON configuration is unchanged.

The initial validation findings remain visible. For this task only, an identical
pre-existing `MARKETPLACE_REQUIRED_PLUGIN_CONFIGURATION_REQUIRED` finding does
not trigger another AI generation when its Marketplace requirements are unchanged.
It remains an error and still blocks publication. New/changed prerequisites,
unknown checks, authority failures and other execution errors are not excluded.
The UI labels this outcome separately from passed publication checks. No source
is invented, validation result erased, repair budget raised or automatic publish
performed. Other assistant tasks retain their existing correction policy.

### Typed Manager Directive Repair

The owner-approved 0.8.66 correction distinguishes a completed schema-bound
invocation's typed-output conversion failure from provider, authorization and
invocation failures. JSON Schema can accept fields whose combination violates
the existing executable directive contract. The typed client reports only a
safe binding-failure type and invocation ID, without raw output or a parser
exception cause that could contain customer data.

Only this manager binding failure enters the existing directive-correction loop.
Its feedback restates the executable field combinations, current eligible
targets, completed-result references and remaining budget. The LLM chooses the
replacement directive. The existing configured correction count (default one),
deadline, model admission, tokens, funding, target eligibility and consent checks
are unchanged. Disabling correction disables this repair too.

Completed worker findings remain in the checkpoint; repair does not rerun them
or replay an action. Exhaustion reports CHAIN_MANAGER_DIRECTIVE_BINDING_FAILED
rather than an opaque invocation error. Safe diagnostics identify typed-binding
phase, invocation ID, correction count and completed-result count. Ordinary
worker finalization has no new automatic retry. This describes the qualified
release contract; actual staging/production adoption is recorded separately.

### Target-Decision Recovery

The owner-approved framework 0.8.64 patch adds bounded repair when the model
marks a target as required but no attachment/working-set target is available.
The repair sees the selected visible action's declared public input contract,
not argument values or another capability profile's tools. It can correct only
the target-resolution flag or ask a genuine missing-choice question. It cannot
create IDs, change action parameters or expand execution authority.

Model-directed specialist workers share the existing decision-recovery round
and admission-time budget. Structured-only managers do not gain chat repair or
generic answer fallback. Exhausted specialist repair stays an execution failure;
ordinary chat retains its configured answer-only fallback. Required inputs,
trusted values, action authorization and confirmation remain governed downstream.
Supplied targets are resolved first. NEVER-persist child invocations do not gain
parent conversation history through this repair. Release/adoption status belongs
in the dated verification record, not this description of the approved change.

Worker writes are disabled. Parent conversation ticket/handover controls retain
their actual authority and confirmation requirements. Draft configuration edits
use canonical Build with AI; there is no second Support draft writer. Publish,
apply, activation and destructive operations remain explicit existing workflows.

V245/V246/V247 create additive catalogue snapshots. V246 corrects the distinction
between runtime extensions and template allowed/default extensions. Two unused
invalid generated snapshots retain their payloads and hashes as rejected audit
records; they cannot bootstrap a deployment. The corrective migration refuses
to reject either if an installation or offer references it. Startup excludes
unreviewed/rejected authoring payloads, while published definitions still require
the actual manifest parser. Explicit draft-template configuration bootstrap keeps
its existing parser and ownership checks; creating a draft is not deployment or
permission to execute its actions. No extra publication gate is added to that
authoring flow. The rejected malformed seed still fails parsing if selected.
V247 preserves the original diagnostic endpoint/credential field contract by
adding the new observations to the existing resource checks. It also makes fresh
template activation dependency-aware; it does not auto-enable unconnected sources.
Existing customer drafts,
immutable published versions, offer pins and app assignments are not silently
repinned. A newly reviewed offer can select the new template revision; the
presence of a catalogue revision is not proof that every existing starter offer
has adopted it. Shared model/provider/security defaults are preserved.

## Source And Verification

Source profiles are under `product-templates/ai-support-workspace/overlays`.
`scripts/build-specialist-defaults.mjs` regenerates diagnostic1.2.0 and the v2
bundled defaults. The original bundle used by applied V245/V246 is frozen; do not
rewrite historical migration inputs or the unqualified six-endpoint1.1.0 candidate.
Node consistency tests
prevent drift; compiler tests verify both profiles within the existing 65536-byte
runtime bundle limit. No framework limit increase follows from this feature.

Compiled bundles above 16384 UTF-8 bytes use a signed, version-specific
`specialist-assistance.json` artifact instead of nested base64 in provider shell
commands. This protects the provider's OS argument-size boundary. Apply requires
the selected runtime image's `signed-specialist-bundle-bootstrap-v1` capability
before provisioning; old images remain usable for smaller inline bundles.
The private runtime entrypoint downloads over HTTPS, enforces the existing
65536-byte limit, verifies the published SHA-256 digest and exports the existing
base64 setting locally. The unchanged framework loader verifies it again.
Download/hash failures stop startup without falling back to stale configuration.
Disabled specialists explicitly clear both transports and their hash. Signed
artifact access retains existing version ownership and signature rules; it is
not an unauthenticated configuration catalogue or a new AI execution engine.

Approved Support guides 10 and 12 describe the customer workflow. Source edits
are not live indexing evidence: upload, approval, preview and indexing must be
observed through the existing owned document workflow before retiring an older
indexed guide. Never delete upstream objects as a substitute for index removal.

Use the dated verification record and `LLM-guides/CODEX_WORKING_CONTEXT.md` for
actual deployed commits, assignments, query quality and incomplete checks. Keep
funding limits real; do not increase a Build with AI allowance merely to make a
regression run green. Retain failed attempts and distinguish model quality,
provider allowance exhaustion and application failures.
