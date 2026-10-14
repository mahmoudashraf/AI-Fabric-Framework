# 010.38 Deployment Owned Workspace Startup Context Implementation Plan

- Status: planned; implementation not started.
- Created: 2026-10-13.
- Execution priority: 4; shared by embedded and standalone AI Workspace.
- Source review: Platform `db58acd9`; framework `561659dc`; dependency AI Fabric `0.8.32`.
- Owners: Platform composition, private runtime initialization, and generic workspace presentation.
- Related: [010.36 standalone workspace](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md), [010.33 semantic decisions](010_33_LLM_DRIVEN_SEMANTIC_DECISIONS_AND_EXPLICIT_RECOVERY_POLICY_PLAN.md), completed [010.34](010_34_TEMPORARY_HUMAN_CONVERSATION_HANDOVER_AND_LIVE_AGENT_EXTENSION_PLAN.md).

## 1. Outcome And Verdict

Let a deployment prepare a small, authorized set of current facts when a user opens the workspace. Examples are approved item details, account state, or business information. The first question can then use those facts with explicit provenance and freshness.

This is opt-in context initialization, not an invisible user query and not permanent conversational memory. A configured fixed read plan represents application intent. Later natural-language interpretation remains model-driven.

## 2. Current Code Evidence

- `max-mode-widget/src/config.ts`: welcome message, initial attachments, host request context, and context provider exist.
- `max-mode-widget/src/api/client.ts`: identity bootstrap and same-owner renewal exist.
- `max-mode-widget/src/installer/types.ts`: assigned-runtime routes and connection profiles exist.
- `ai-infrastructure-module/ai-fabric-runtime/.../web/PublicRuntimeSessionController.java`: public bootstrap establishes identity; it is not a data-prefetch API.
- `.../web/ChatRuntimeController.java`: user query/attachment context assembly and conversation-control integration exist.
- `.../handover/RuntimeConversationHandoverService.java`: durable ownership, transcript, leases, and handover gates exist.
- Public `ai-fabric-execution/.../gateway/AIExecutionGateway.java`: execute/submit/find/cancel generic execution contracts exist.
- Public `ai-fabric-core/.../intent/action/AIActionHandler.java`: explicitly an internal orchestrator handler contract.

No inspected contract exposes a deployment-configured, owner-bound startup read plan and reusable seed-evidence lifecycle. Do not infer that browser `initialAttachments` or an internal handler call supplies trusted server initialization.

## 3. Product Contract And Composition

Add a reviewed startup capability to the deployment's compiled configuration, contributed by a TEMPLATE selecting installed read ACTION capabilities. Do not create a new Marketplace plugin type or a second plan language.

Proposed configuration responsibilities:

- a stable startup profile/read-plan reference;
- enabled invocation contexts, such as authenticated account or anonymous public workspace;
- reviewed read operations and existing structured input mappings;
- timeout, output-size, allowed-data, and concurrency limits;
- freshness/expiry and explicit refresh rules;
- behavior when individual reads fail;
- verification pack and supported deployment behavior.

Only `CONVERSATIONAL` workspace initialization is in the initial scope. It does not add handover to Smart Brain or change its write authority.

## 4. Deployment Local API And State

Proposed, not currently implemented, runtime operations:

```text
POST /api/chat/me/conversations/{conversationId}/startup-context
GET  /api/chat/me/conversations/{conversationId}/startup-context/{initializationId}
POST /api/chat/me/conversations/{conversationId}/startup-context/{initializationId}/refresh
```

The final route naming must align with the owned-message API before implementation. Private backends use the equivalent reviewed trusted-identity path. These routes live at the assigned deployment, not at a central public Platform chat gateway.

Identity, tenant, deployment, conversation owner, and approved resource references come from validated backend context. A client page ID or attachment may identify an advisory subject only; hidden targets require trusted resolution.

Proposed initialization record:

```text
initializationId, tenantId, deploymentId, ownerRef, conversationId,
profileHash, trustedInputHash, controlVersion,
status, evidenceRefs, sourceTimestamps, expiresAt,
idempotencyKey, createdAt, completedAt, failures
```

Statuses: `PENDING`, `RUNNING`, `READY`, `PARTIAL`, `FAILED`, `EXPIRED`, `CANCELLED`. `PARTIAL` names unavailable reads explicitly; it must not imply full grounding. Exact retries return the existing record; a changed request with the same key conflicts.

## 5. Evidence, Memory, And Human Ownership

Keep seed evidence separate from user/assistant transcript messages. Runtime-generated evidence handles must be owner/deployment bound, tamper-resistant, and expire according to the profile. Persist the minimal safe facts or approved references needed for reuse, with source and fetch times. Revalidate before admitting evidence to a later turn.

The model receives current seed facts through a supported trusted context/evidence contract, with their scope and expiry visible in diagnostics. They do not force a vector space or suppress fresh reads/RAG. Do not automatically treat every read result as retrieval-grounding eligible; respect declared metadata and the actual user request.

Initialization and refresh participate in the existing conversation-control/effect guard. During `HANDOVER_PENDING`, `HUMAN_ACTIVE`, `AI_RESUME_PENDING`, or closed/revoked states, do not launch AI-owned initialization work. A concurrent ownership change must stop pending reads/results from entering an AI turn under stale authority. Existing human transcript and attachments remain intact.

## 6. Framework Contract Review

First prove how the released execution gateway, approved read adapter, trusted context, and conversational snapshot/evidence contract can express the flow. Do not directly call internal `AIActionHandler.executeAction` to bypass normal authorization, parameter provenance, or pre-effect guards.

If generic guarded read invocation or trusted seed-evidence admission is genuinely missing, create a narrow public framework change request with exact caller, intended types, owning module, isolation, cancellation, and effect tests. The private runtime still owns HTTP, persistence lifecycle, and product configuration. A product REST route alone is not a framework blocker.

## 7. Implementation Phases

### C0. Resolve The Public Contract And Profile

- [ ] Trace supported read execution, trusted input binding, and seed admission from released code.
- [ ] Select one reviewed startup plan with bounded read-only operations.
- [ ] Record any precise reusable framework gap before writing a substitute.
- [ ] Define profile schema and compilation through existing template/action primitives.

### C1. Implement Runtime Initialization

- [ ] Add deployment-owned records, migration, statuses, and exact request idempotency.
- [ ] Execute approved reads with budgets and conversation-control gates.
- [ ] Project owner-safe, timestamped evidence with explicit failures.
- [ ] Add expiry, refresh, cancellation, and restart behavior.
- [ ] Deduplicate simultaneous opens without sharing cached results across principals.

### C2. Admit Evidence To Normal Queries

- [ ] Resolve handles server-side and check owner/deployment/profile/freshness.
- [ ] Use supported trusted evidence/context contracts, preserving source provenance.
- [ ] Keep unavailable/expired evidence visible; normal configured query orchestration remains available.
- [ ] Preserve model source/action decisions and all declared recovery diagnostics.

### C3. Wire Workspace And Platform UI

- [ ] Add optional startup route/profile metadata to the installation connection.
- [ ] Trigger once after identity and owned conversation initialization, without fabricating a user message.
- [ ] Present loading, current evidence, source time, failure, and refresh states generically.
- [ ] Expose profile selection and limits in template/deployment configuration.
- [ ] Preserve the same behavior across standalone, embedded, anonymous, and reviewed private paths.

### C4. Verify And Release

- [ ] Prove first-turn use with a real provider and approved read facts.
- [ ] Prove owner isolation, trusted-target enforcement, stale expiry, exact replay, and concurrent-open deduplication.
- [ ] Prove denied reads, timeout, partial failure, and restart without static success.
- [ ] Prove human handover blocks initialization and return-to-AI resumes under current control.
- [ ] Verify no text matching or hidden synthetic query drives startup selection.
- [ ] Record live evidence and actual compiled profile identity.

## 8. Completion And Release Impact

Add startup-context unit/controller and JDBC restart/concurrency tests in the
runtime. Extend existing `ConversationHandoverControllerTest` and
`RuntimeConversationHandoverServiceTest` for initialization/control races, and
`DeploymentConfigCompilerTest` for profile/route compilation. If C0 requires
a public contract change, put its tests in the actual affected framework
module before adding product HTTP/UI coverage.

Complete when a first answer can use fresh authorized startup evidence, failures remain honest, and identity, conversation, effect, and lifecycle checks hold through replay/restart/handover. Optional startup failure may leave the existing workspace usable with an explicit failure state; it may not be relabeled ready.

Framework release is conditional on C0's evidenced contract gap. If needed, run affected core/execution/chat-session critical tests and clean released-consumer proof before private adoption. No unrelated framework-wide feature or alternative orchestration engine is part of this plan.
