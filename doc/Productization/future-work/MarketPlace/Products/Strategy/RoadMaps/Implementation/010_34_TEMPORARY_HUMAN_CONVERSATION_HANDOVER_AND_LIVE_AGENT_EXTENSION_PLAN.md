# 010.34 Temporary Human Conversation Handover And Live-Agent Extension Plan

- **Status:** Implemented and hosted-verified for the scoped first release.
  H0-H7 are complete; critical release gates passed on 2026-10-13. Broader
  unrelated Platform checklist items remain separately tracked and do not
  weaken this capability claim.
- **Created:** 2026-10-11
- **Scope:** AI Fabric conversation lifecycle, private runtime, deployment
integration connector, LoomAI Platform composition, AI Workspace, provider
channel contracts, and hosted verification
- **Framework release:** AI Fabric `0.8.32`, source `561659dc`, tag
  `ai-fabric-framework-v0.8.32`
- **Compatibility decision:** Current-only. LoomAI does not need parallel
support for older framework, runtime, Platform, or widget contracts.
- **Primary product name:** Conversation Handover
- **Platform execution extension:** `CONVERSATION_HANDOVER`
- **Primary decision:** Proceed, but do not implement handover as an ordinary
  external action or as another `HUMAN_REVIEW` source type.
- **Responsibility decision:** LoomAI owns conversation authority, continuity,
  and the customer-facing event stream. Installed conversation-channel
  providers own human operators, queues, assignment, presence, inboxes, and
  workforce operations.

Related plans:

- [010.23 Deployment Behavior Market Readiness Execution Plan](010_23_LOOMAI_DEPLOYMENT_BEHAVIOR_MARKET_READINESS_EXECUTION_PLAN.md)
- [010.29 Generic Max Mode Injectable Action UI And Dealership Experience Plan](010_29_GENERIC_MAX_MODE_INJECTABLE_ACTION_UI_AND_DEALERSHIP_EXPERIENCE_PLAN.md)
- [010.31 Platform-Hosted AI Workspace Installation Plan](010_31_PLATFORM_HOSTED_AI_WORKSPACE_INSTALLATION_AND_ASSIGNED_RUNTIME_DISCOVERY_PLAN.md)
- [010.33 LLM-Driven Semantic Decisions And Explicit Recovery Policy Plan](010_33_LLM_DRIVEN_SEMANTIC_DECISIONS_AND_EXPLICIT_RECOVERY_POLICY_PLAN.md)

Resolved architecture decisions:

- AI Fabric framework changes are required for generic conversation-control,
  participant-message, turn-lease, and pre-effect guard contracts. Framework
  code must contain no LoomAI, operator-product, or provider-specific logic.
- Anonymous conversations may transfer to a human while remaining bound to a
  verified deployment-issued pseudonymous owner.
- Token expiry, navigation, and reload do not invalidate a handover. Explicit
  close, security/consumer revocation, retention expiry, or loss of the
  anonymous owner proof does.
- The Platform adds a bounded `INTEGRATION` Marketplace plugin type. Its first
  supported contribution is `CONVERSATION_CHANNEL`; it is declarative and may
  select only reviewed, allowlisted adapters.
- The first release proves the contract through a Basic LoomAI Inbox packaged
  as the first reference `CONVERSATION_CHANNEL` provider. It receives no
  special runtime or widget path.
- Additional third-party channel plugins can follow without changing the
  runtime state machine, AI Workspace, or Marketplace contract.
- The first release and this plan are limited to `CONVERSATIONAL` deployments.

## 1. Verdict

This is a good and commercially valuable capability. It turns an AI Workspace
from an isolated assistant into a continuous customer-service surface where a
person can temporarily take ownership without losing the conversation,
attachments, selected resources, or deployment context.

It is especially useful for:

- a buyer asking to speak to a dealership representative;
- an AI assistant reaching a policy, confidence, sensitivity, or capability
boundary;
- a support conversation that requires empathy or non-automated judgement;
- a regulated workflow where a person must continue the dialogue, rather than
merely approve one action; and
- returning the same conversation to AI after the human part is complete.

The idea is viable only with the following architecture:

1. The deployment runtime, not an LLM or remote plugin, owns the durable
  conversation-control state.
2. The user button and model-selected action call the same idempotent handover
  command.
3. The model may request or propose handover, but it cannot choose credentials,
  an operator identity, a destination URL, or mutate state directly.
4. A channel integration routes queued messages to and from a human service;
  the provider owns operators and queue operations but does not own AI
  conversation authority.
5. While human ownership is pending or active, normal AI orchestration and
  governed AI actions are stopped for that conversation.
6. Return to AI is an explicit, authorized state transition, not a timeout or
  guessed text intent.

An ordinary plugin action alone is insufficient. It could open a ticket, but
it cannot atomically stop AI turns, preserve ownership, append human messages,
handle concurrent requests, resume AI safely, or recover after restart.

## 2. What LoomAI Has Today



### 2.1 Existing Human Review is real, but it is a different capability

The current `HUMAN_REVIEW` extension is implemented and intentionally narrow:

- it accepts an AI Fabric `ACTION_PROPOSAL` backed by a governed action
receipt;
- it creates a durable review task;
- an authenticated reviewer can approve or reject it;
- policy and action authority are revalidated before a domain effect; and
- the Platform provides a scoped Review Inbox over deployment-local runtime
APIs.

The runtime implementation calls `ReviewDecisionGateway.createActionReview`
and accepts only `APPROVE` or `REJECT`. The Platform behavior catalog describes
the extension as an action-proposal decision lifecycle. This is correct and
must not be generalized into live conversation ownership.

Human Review answers:

> May this proposed action proceed?

Conversation Handover answers:

> Who is currently responsible for replying in this conversation?

They may coexist, but neither substitutes for the other. A human operator in a
live conversation does not automatically gain permission to execute a domain
write. Any such write must still pass its normal action, authorization,
confirmation, review, and receipt controls.

### 2.2 Current conversation support has a genuine gap

The public AI Fabric chat-session module currently has only:

- `SessionStatus.ACTIVE`;
- `SessionStatus.CLOSED`;
- paired user/AI turns; and
- history, metadata, list, and delete operations.

It has no typed human participant, atomic ownership transition, pending
handover state, human message append path, AI-turn lease, or resume contract.

The private runtime currently exposes query, query-once, and owned
conversation history operations. It invokes orchestration without a durable
handover-state gate. The generic widget knows how to make a `LOCKED` or
`CLOSED` conversation read-only, but no current runtime contract drives a live
human session.

Anonymous runtime tokens currently default to query, suggestions, and
conversation scopes only. There are no owner-bound handover request, message,
status, event-stream, or resume scopes.

### 2.3 Marketplace gap

The current Marketplace compiler supports `TEMPLATE`, `ACTION`, `DATA`,
`INFERENCE_PROFILE`, and `SPECIALIST`. It has no implemented `INTEGRATION`
contribution for a bidirectional human conversation channel.

This matters because the existing architectural rule is correct: `ACTION` and
`DATA` plugins must not be overloaded to represent arbitrary integrations. An
action is appropriate for the model-visible request to start handover. A
long-lived, bidirectional human channel needs its own allowlisted contract.

## 3. Product Boundary And Terminology

Use **Human in the Loop** as the broad product family, with two distinct
features:


| Feature               | Unit of control                          | Current/new | Result                                         |
| --------------------- | ---------------------------------------- | ----------- | ---------------------------------------------- |
| Human Review          | One governed action proposal             | Existing    | Approve or reject the proposal                 |
| Conversation Handover | A live conversation for a bounded period | New         | A human temporarily becomes the response owner |


Customer-facing terms:

- **Talk to a person** for the user command;
- **Connecting to a person** while handover is pending;
- **Human support active** while a person owns replies; and
- **Continue with AI** for an explicit return.

Internal terms:

- `CONVERSATION_HANDOVER` for the execution extension;
- `ConversationControlState` for current ownership mode;
- `ConversationHandover` for one durable handover lifecycle; and
- `CONVERSATION_CHANNEL` for a bidirectional integration contribution.

Do not rename or broaden `HUMAN_REVIEW`.

## 4. First Release Scope

The first marketable release supports:

- `CONVERSATIONAL` deployments only;
- anonymous and authenticated AI Workspace users;
- a generic user-controlled handover button;
- a generic provider-configured intake form with anonymous continuation and
  bounded optional contact fields;
- a governed, structured `request_conversation_handover` action available to
the LLM;
- explicit confirmation when the model proposes escalation without an
explicit user request;
- deployment-local durable state and message history;
- one active human-channel ownership binding at a time, with provider-authored
  messages carrying only opaque external actor references;
- text messages and safe references to already accepted attachments;
- the Basic LoomAI Inbox as the first certified `CONVERSATION_CHANNEL`
  reference provider;
- direct deployment-local browser APIs and an owner-authorized SSE event
  stream;
- explicit user or operator return to AI;
- idempotent dispatch, callback, restart, and replay behavior; and
- Marketplace/template composition and immutable release evidence.

Deferred from the first release:

- voice or video transfer;
- advanced inbox functions, workforce management, staffing forecasts, and
  complex skill routing;
- multiple active human-channel bindings for one conversation;
- autonomous model-initiated handover without user confirmation;
- arbitrary third-party adapter code uploaded by customers;
- a duplicate Platform transcript store;
- human operators receiving implicit domain-write authority;
- AI-generated operator replies sent without human approval.

The adapter contract is generic from day one. The Basic LoomAI Inbox is a
small provider service used for protocol proof, hosted demos, and customers
with very low conversation volume. Further provider plugins can follow after
the contract is proven. The basic inbox must not grow into runtime-owned
operator management or receive private integration behavior.

## 5. Required Architecture

```text
Customer browser
  -> Platform-hosted install manifest and assigned deployment discovery
  -> assigned deployment runtime
       -> Conversation Control Service (authoritative state)
       -> AI orchestration, only while AI_ACTIVE
       -> durable messages, handovers, outbox and audit
       -> customer owner SSE and durable replay
       -> deployment integration connector
            -> selected CONVERSATION_CHANNEL adapter
            -> Basic LoomAI Inbox or third-party human-support service
                 -> provider-authenticated human operator

LLM request path
  -> structured action selection
  -> governed request_conversation_handover action
  -> same Conversation Control Service

Direct button path
  -> owner-bound handover command
  -> same Conversation Control Service
```

The central Platform remains the control plane for template composition,
plugin installation, secret references, release, assignment discovery,
channel health, and runtime administration. Customer message traffic and
durable handover state remain in the assigned deployment. Operator traffic,
queueing, presence, and assignment remain in the selected provider. The
Platform must not become a mandatory shared chat proxy or duplicate operator
system.

### 5.1 Responsibility boundary

| Component | Authoritative responsibilities |
| --- | --- |
| AI Fabric framework | Generic control state, participant messages, atomic commands, turn leases, orchestration/effect guards, channel SPI, and reusable tests |
| Deployment runtime | AI/human authority, canonical safe transcript, event journal, owner commands, message routing, callback admission, SSE/replay, retention, and audit |
| Deployment integration connector | Adapter execution, deployment-bound secret resolution, outbound delivery, signed callback verification, retries, dead-letter handling, and provider-event normalization |
| LoomAI Platform | Marketplace composition, channel installation, compatibility validation, immutable release/apply, health/readiness, and assigned-deployment discovery |
| Generic AI Workspace | Handover controls, state presentation, canonical message submission, owner-scoped SSE/replay, and explicit return controls |
| Human-channel provider | Operator identity, queue and claim behavior, presence/availability, inbox UX, notifications, and any advanced workforce or SLA functions it offers |

The provider may retain its channel copy under the configured data-processing
and retention policy. The deployment retains the canonical safe transcript
needed for continuity, audit, and authorized return to AI. Platform may retain
minimal installation and health projections, never a duplicate transcript.

## 6. Conversation State Model

Do not overload the existing session lifecycle with every operational detail.
Keep two related dimensions:

1. `SessionStatus`: `ACTIVE` or `CLOSED`.
2. `ConversationControlState`: who may produce the next response.



### 6.1 Control states


| State               | Meaning                                                           | AI orchestration | User messages                    |
| ------------------- | ----------------------------------------------------------------- | ---------------- | -------------------------------- |
| `AI_ACTIVE`         | AI owns responses                                                 | Allowed          | Routed to AI                     |
| `HUMAN_PENDING`     | Transition to human requested; dispatch/claim pending             | Blocked          | Persisted and queued for human   |
| `HUMAN_ACTIVE`      | The bound provider has confirmed human ownership                  | Blocked          | Persisted and delivered through the provider |
| `AI_RESUME_PENDING` | Human ownership is ending and transcript state is being finalized | Blocked          | Persisted; no AI call yet        |
| `CLOSED`            | Conversation ended                                                | Blocked          | Rejected with typed status       |


Handover delivery has a separate status so adapter failures do not corrupt the
ownership model:

`REQUESTED -> DISPATCH_PENDING -> DISPATCHED -> CLAIMED -> COMPLETED`

Terminal alternatives are `CANCELLED`, `DECLINED`, `EXPIRED`, and `FAILED`.
A failed dispatch must not silently return control to AI. The user receives a
clear failure and can explicitly retry or select **Continue with AI**.

`HANDOVER_INTAKE_REQUIRED` is a typed pre-transition command outcome, not a
`ConversationControlState` and not a successful handover. While intake is
incomplete, control remains `AI_ACTIVE`, no channel dispatch is created, and
the user may continue speaking to AI or cancel the request.

### 6.2 Allowed transitions


| From                 | To                  | Authorized causes                                                         |
| -------------------- | ------------------- | ------------------------------------------------------------------------- |
| `AI_ACTIVE`          | `HUMAN_PENDING`     | Accepted owner command, governed model-selected action, or authorized server policy after admission checks |
| `HUMAN_PENDING`      | `HUMAN_ACTIVE`      | Valid accepted/claimed callback from the bound provider                    |
| `HUMAN_PENDING`      | `AI_ACTIVE`         | Owner cancellation, explicit owner recovery, or recorded pre-authorization |
| `HUMAN_ACTIVE`       | `AI_RESUME_PENDING` | Owner request or valid provider release callback                          |
| `AI_RESUME_PENDING`  | `AI_ACTIVE`         | Durable finalization and adapter acknowledgement                          |
| Any non-closed state | `CLOSED`            | Existing authorized close policy                                          |


Every transition requires:

- verified tenant, deployment, conversation owner, and actor context;
- expected state/version compare-and-set;
- an idempotency key;
- a reason/source code;
- an append-only audit event; and
- outbox publication when a channel must be notified.

### 6.3 Anonymous ownership and invalidation

Anonymous handover is a first-release capability. The runtime binds the
conversation to a stable, deployment-issued pseudonymous subject. A browser
cannot claim ownership by supplying a conversation ID, tenant ID, external
agent ID, or channel reference.

The following rules are mandatory:

- short-lived browser-token expiry suspends access until safe renewal; it does
  not close the conversation, cancel handover, or return control to AI;
- token rotation, page navigation, reload, and SSE reconnect preserve the same
  owner and conversation;
- a new anonymous subject cannot recover an old conversation using only its
  identifier;
- clearing the browser-held owner proof may make the old conversation
  inaccessible to that browser, but does not silently mutate server state;
- explicit close, consumer/install revocation, security revocation, and
  retention expiry invalidate access under a named audited policy;
- an active handover remains pinned to the deployment where it started and
  must be drained or explicitly migrated before that deployment is removed;
  and
- provider-side operator identity remains an opaque, signed external reference
  and never becomes the anonymous user's application identity.

Anonymous handover itself does not require a name, email address, or phone
number when the installed channel permits anonymous continuation. Optional
contact details are collected only through the structured handover intake
contract below, not inferred from conversation text by the model.

### 6.4 Structured handover intake

The installed `CONVERSATION_CHANNEL` may declare a small, bounded intake
schema. Its purpose is to help the operator address the user and, with explicit
consent, preserve a follow-up route if the browser disconnects. Typical fields
are display name, email address, phone number, handover reason, and a
`contactIfDisconnected` consent choice.

The first release follows these rules:

- the schema, required fields, purposes, limits, and verification modes are
  compiled from reviewed server-owned configuration;
- **Continue anonymously** remains available whenever `anonymousAllowed` is
  true;
- the runtime returns `HANDOVER_INTAKE_REQUIRED` with a safe public form schema
  when necessary information has not yet been supplied;
- the AI Workspace renders and submits the form directly; the LLM cannot
  authoritatively populate contact details or consent from free text;
- the server validates field type, length, consent, and configured verification
  requirements before moving from `AI_ACTIVE` to `HUMAN_PENDING`;
- contact details are encrypted separately, projected only to the bound
  provider for their declared purpose, and removed under a short explicit
  retention policy; and
- an entered email address or phone number is never labelled verified unless
  the configured verification step actually succeeded.

The deployment-issued pseudonymous owner remains the authority for the active
browser conversation. Contact details improve operator continuity; they do not
replace owner proof or grant conversation access.

### 6.5 Timeout and inactivity policy

Time passing alone must not silently return a conversation to AI.

- a pending queue timeout changes delivery to `EXPIRED` and presents **Retry**
  and **Continue with AI**;
- a stale provider claim may be reported or released back to `HUMAN_PENDING`
  under an audited provider policy, but does not activate AI;
- an active human conversation remains human-controlled until the owner or a
  valid provider callback requests return or close; and
- retention expiry closes the conversation under policy rather than resuming
  AI.

A future policy may support automatic return only when the user explicitly
pre-authorized a bounded rule such as, "If nobody responds within five
minutes, continue with AI." The runtime must record that authorization and
revalidate state, delivery, and in-flight messages before transition. This is
deferred from the first release.



### 6.6 In-flight AI turn race

A handover cannot be considered correct if an already-running AI turn can
execute an action after human ownership begins.

Add a bounded conversation turn lease:

1. An AI query acquires a lease only in `AI_ACTIVE` at a known control version.
2. A handover transition prevents new leases immediately.
3. The transition reports `HUMAN_PENDING` while an existing lease drains.
4. Governed action execution revalidates the lease/control epoch before a
  side effect.
5. The model-facing handover action is terminal and exclusive in its
  orchestration cycle.

Do not hold a database transaction open for an entire LLM call. Use a durable
epoch/lease record and short atomic transitions.

## 7. Trigger Paths



### 7.1 Direct user button

The generic AI Workspace exposes a configurable **Talk to a person** command.
It calls the deployment runtime directly and does not convert the click into a
natural-language prompt. The runtime derives conversation ownership from the
verified anonymous or authenticated token.

The direct command:

- works without an LLM call;
- is idempotent across double clicks and retries;
- creates a conversation first when the user has no current conversation;
- displays availability before or as part of the request;
- returns `HANDOVER_INTAKE_REQUIRED` when the installed channel needs a
  structured intake decision;
- keeps the same conversation ID, messages, and attachments; and
- after an accepted transition, changes the composer route from AI
  orchestration to human messaging.

### 7.2 Structured intake submission

The direct button and model-selected action use the same runtime intake
contract. When intake is required, the AI Workspace renders the server-supplied
fields, lets the user submit them, continue anonymously when allowed, or cancel,
and then retries the same idempotent handover command with a validated intake
payload.

The conversation remains `AI_ACTIVE` until that submission is accepted. A
model-produced action can request handover and provide a bounded reason code,
but cannot assert a person's name, contact details, verification status, or
consent. Those values come only from the user's structured submission or from
verified private-auth profile claims explicitly permitted by deployment
policy.


### 7.3 LLM-selected handover action

An `ACTION` plugin contributes `request_conversation_handover` with an
allowlisted built-in execution adapter. The model may select it through normal
structured action planning. There must be no keyword, regex, phrase, or
sentiment-matching fallback that secretly switches ownership.

Trusted internal parameters:

- tenant ID;
- deployment ID;
- conversation ID;
- verified owner/subject;
- control version; and
- selected channel binding.

The model may supply only bounded, non-authoritative fields such as a reason
code from an allowlist and a short user-facing summary. It cannot supply an
operator, endpoint, credential, tenant, conversation owner, or channel secret.

Default confirmation policy:


| Situation                                                                           | Behavior                                                                     |
| ----------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| User explicitly asks for a person and the structured action records that provenance | Execute without asking the same question again when policy permits           |
| Model recommends human help without an explicit user request                        | Return a confirmable proposal such as **Talk to a person?**                  |
| Server-owned mandatory escalation policy applies                                    | Execute only under that named policy, with visible reason and audit evidence |
| Model output is invalid, unavailable, or not authorized                             | Do not hand over; return typed clarification/failure                         |


This path must comply with `010.33`: improve structured prompts and schemas,
validate against policy, optionally run bounded model repair, and expose
failure honestly. Do not add deterministic text matching to make a handover
test phrase pass.

### 7.4 Return to AI

The LLM cannot return itself to control while it is not the active responder.
Return is initiated by:

- the conversation owner selecting **Continue with AI**; or
- an authorized operator selecting **Return to AI** in the provider service,
  delivered through the bound adapter as a signed provider event.

The runtime closes the handover, records actor and reason, flushes pending
messages, appends an optional human-authored continuation note, advances the
control epoch, and only then allows a new AI turn. The AI receives the
authorized conversation history and typed handover events; it must not invent
what the human agreed.

## 8. Marketplace And Template Composition



### 8.1 New execution extension

Add `CONVERSATION_HANDOVER` to the Platform behavior catalog with:

- compatible behavior: `CONVERSATIONAL` for the first release;
- required runtime capabilities: conversation control, participant messages,
turn leases, durable handover, transactional outbox, and event streaming;
- required endpoint classes: owner handover, canonical messaging, event
stream/replay, normalized channel events, callback admission, and explicit
resume;
- required migrations and verification pack IDs; and
- maturity that cannot exceed stored source and hosted evidence.

Templates opt into the extension explicitly. It is not enabled globally and
is not inferred from the presence of an action.

### 8.2 ACTION plugin responsibility

Publish a generic first-party action plugin that contributes the model-visible
handover request:

```yaml
pluginType: ACTION
contributions:
  actions:
    - id: request_conversation_handover
      effect: CONTROL
      adapterType: conversation-control
      operation: REQUEST_HANDOVER
      terminal: true
```

`conversation-control` is a reserved, allowlisted runtime adapter. It invokes
the internal command service; it is not arbitrary HTTP, MCP, or customer code.
The manifest validator and compiler reject unknown control operations and any
caller-configured destination or trusted parameter.

### 8.3 CONVERSATION_CHANNEL integration responsibility

Add the Marketplace `INTEGRATION` type with a deliberately bounded,
allowlisted implementation/compiler contract. Its only first-release
contribution kind is `CONVERSATION_CHANNEL`.

It describes:

- adapter implementation ID and version;
- supported capabilities such as dispatch, claim, inbound message, outbound
message, presence, resume, and close;
- secret references and authentication mode;
- bounded intake fields, anonymous-continuation policy, verification modes,
  consent purposes, and safe provider projection;
- signed callback contract;
- allowed safe transcript/attachment projection;
- retry, timeout, expiry, and dead-letter policy; and
- health/readiness checks.

An integration manifest is declarative. It cannot upload executable code,
choose an arbitrary callback destination, or bypass the reviewed deployment
connector. It selects an adapter implementation already approved and packaged
for the deployment connector.

The first hosted proof uses the Basic LoomAI Inbox and its reviewed adapter as
the first reference provider. The inbox uses exactly this contract and receives
no private runtime, Platform, or AI Workspace path. Additional providers can
implement the same contract without changing the AI Workspace, runtime state
machine, or framework.

Do not represent the channel as a `DATA` plugin. Do not overload the handover
`ACTION` plugin with long-lived provider callbacks, secret ownership, or
message transport.

### 8.4 Compiled deployment configuration

The V04 draft/version compiler should produce server-owned configuration
similar to:

```yaml
behavior:
  type: CONVERSATIONAL
  executionExtensions:
    - CONVERSATION_HANDOVER
conversationHandover:
  enabled: true
  actionId: request_conversation_handover
  channelBindingId: loomai-basic-inbox
  modelInitiatedPolicy: REQUIRE_USER_CONFIRMATION
  userCanCancelPending: true
  userCanResumeAi: true
  queueTimeoutSeconds: 900
  queueTimeoutOutcome: EXPIRED_REQUIRES_EXPLICIT_RECOVERY
  activeIdleAutoResume: false
  transcriptProjection: SAFE_TEXT_AND_ATTACHMENT_REFS
  intake:
    anonymousAllowed: true
    fields:
      - key: displayName
        type: TEXT
        required: false
      - key: email
        type: EMAIL
        required: false
        purpose: DISCONNECT_FOLLOW_UP
        consentRequired: true
```

Secrets remain deployment-bound references. No secret value is written into a
Marketplace manifest, V04 public projection, installation manifest, browser
configuration, or audit payload.

## 9. AI Fabric Framework Change Request

Implement reusable mechanics in the public framework; keep LoomAI product and
provider details private.

### 9.1 Generic contracts

Add framework-owned types and services for:

- `ConversationControlState`;
- typed participant roles: `USER`, `AI`, `HUMAN`, and `SYSTEM`;
- append-only `ConversationMessage`/event records rather than assuming every
history item is a user/AI pair;
- `ConversationControlCommand` with expected version and idempotency key;
- a typed handover-admission result, including
  `HANDOVER_INTAKE_REQUIRED`, and bounded provider-neutral intake field
  contracts;
- `ConversationControlGateway` for transition, human activation, release, and
  status;
- an AI-turn lease/control epoch contract;
- safe conversation snapshots containing control state and participant
messages; and
- extension hooks for dispatch without including any LoomAI or vendor adapter.



### 9.2 Persistence

Provide JDBC/JPA-neutral domain contracts and a reference JDBC implementation
for:

- conversation control state/version;
- participant messages;
- handover lifecycle, channel binding, and external conversation reference;
- idempotency records; and
- dispatch outbox/recovery state.

LoomAI still owns reviewed deployment migrations and runtime wiring. The
framework must not assume a central Platform database.

### 9.3 Orchestration guard

Add a generic pre-orchestration and pre-effect guard:

- AI orchestration is admitted only under a valid AI-active lease;
- a control transition can stop new turns;
- an action revalidates the control epoch before execution; and
- a terminal conversation-control action prevents sibling actions or
generation from continuing after handover.

The framework must not detect handover intent from text. It receives a valid,
structured command from the host/action layer.

### 9.4 Framework acceptance tests

Prove:

- atomic state transitions and optimistic conflict behavior;
- duplicate command idempotency;
- intake-required outcomes do not change control state, create dispatch, or
  accept unvalidated contact/consent data;
- one active human-channel ownership binding;
- an in-flight AI turn cannot write after the control epoch changes;
- participant ordering across user, AI, human, and system messages;
- restart/replay and outbox recovery;
- tenant/owner metadata is not caller-overridable; and
- closed or human-controlled sessions cannot enter AI orchestration.

Publish the capability in one immutable AI Fabric release before Platform
templates claim it.

## 10. Private Runtime Work



### 10.1 Deployment-local schema

Add reviewed Flyway migrations for at least:

- `ai_conversation_control`;
- `ai_conversation_message`;
- `ai_conversation_handover`;
- `ai_conversation_handover_intake`, or an equivalently isolated encrypted
  store with field purpose, consent, verification, and expiry metadata;
- `ai_conversation_handover_dispatch`/outbox; and
- idempotency/audit fields required by the framework contract.

Each row carries trusted tenant/deployment ownership, opaque subject/agent
references, channel binding and external conversation references, timestamps,
version, and retention metadata. Do not store raw credentials, provider
workforce data, or unnecessary personal data.

### 10.2 Canonical owner APIs

Prefer one control-aware message endpoint so the browser cannot accidentally
bypass handover state:

```text
POST /api/chat/me/conversations/{conversationId}/messages
POST /api/chat/me/conversations/{conversationId}/handover-requests
POST /api/chat/me/conversations/{conversationId}/handover-requests/{handoverId}/cancel
POST /api/chat/me/conversations/{conversationId}/ai-resume-requests
GET  /api/chat/me/conversations/{conversationId}
GET  /api/chat/me/conversations/{conversationId}/events
```

The message endpoint routes internally:

- `AI_ACTIVE` -> AI orchestration;
- `HUMAN_PENDING` or `HUMAN_ACTIVE` -> append and dispatch to the human
channel; and
- `AI_RESUME_PENDING` or `CLOSED` -> typed non-acceptance/status response.

Migrate the current widget and all current clients to this contract. Because
the product supports only the latest runtime, remove or hard-gate any old query
route that can bypass conversation control after migration.

Every mutation requires `Idempotency-Key`. Every response returns current
control state, control version, accepted message/event ID, and a typed outcome.
The handover request response may include a public intake schema and
`HANDOVER_INTAKE_REQUIRED`; it must never return previously entered contact
values in a general conversation projection.

### 10.3 Deployment integration connector

The deployment integration connector, not the chat runtime core, executes the
selected `CONVERSATION_CHANNEL` adapter. It:

- consumes committed handover/message outbox records;
- resolves deployment-bound provider secret references;
- creates or associates the external provider conversation;
- sends safe projected user messages, attachment references, and only the
  consented intake fields declared by the binding;
- applies bounded retry, timeout, and dead-letter policies;
- verifies provider webhook signatures and replay controls; and
- normalizes provider events into the runtime's private channel contract.

The connector does not own conversation authority or the canonical transcript.
It cannot activate AI, invent an operator identity, or execute application
domain actions. Adapter implementations are reviewed and packaged; Marketplace
installation cannot upload arbitrary executable code.

### 10.4 Normalized channel callback API

Expose a deployment-local private route keyed by an opaque channel binding ID,
for example:

```text
POST /api/private/conversation-channel-bindings/{bindingId}/events
```

First-release normalized event types are bounded to:

- `HANDOVER_ACCEPTED` or `HANDOVER_CLAIMED`;
- `HUMAN_MESSAGE`;
- `HANDOVER_RELEASED` or `HANDOVER_CLOSED`; and
- `DELIVERY_FAILED`.

The runtime validates connector service identity, timestamp, nonce, event ID,
expected external conversation reference, tenant/deployment binding, current
control version, and allowed event type before mutation. Human messages carry
only a provider-verified opaque actor reference and safe display projection.

Provider callbacks never call the public owner endpoint and never carry a
Platform admin credential. The deployment connector validates the external
provider callback first, then invokes this normalized internal contract.

### 10.5 Event delivery

Use an owner-authorized SSE stream for live status and messages:

- publish only events whose message/control transaction has committed;
- support `Last-Event-ID` replay;
- use bounded heartbeat and connection limits;
- refresh/renew anonymous browser tokens safely;
- never expose another owner's event;
- reconnect from durable events rather than browser-only state; and
- provide bounded conditional polling only as a fallback.

The deployment event journal is authoritative. PostgreSQL `LISTEN/NOTIFY` may
wake connected runtime instances, but carries only an event reference and is
never the durable message store. The human-channel provider owns its operator
stream and notifications; LoomAI does not replicate that operator stream.



## 11. Platform Backend And UI Work



### 11.1 Authoring and release

Add Platform support for:

- the `CONVERSATION_HANDOVER` behavior extension;
- `INTEGRATION` / `CONVERSATION_CHANNEL` Marketplace manifests;
- compatibility and capability validation;
- install forms for channel references, safe non-secret settings, and secret
references;
- V04 compilation and composition hashing;
- runtime capability, endpoint, migration, and health gating;
- immutable release/apply and rollback; and
- assignment metadata that advertises only active handover endpoints.



### 11.2 Channel integration operations

The Platform's normal deployment-management surface provides control-plane
operations, not an operator inbox.
Required first-release views are:

- install and configure a reviewed conversation-channel plugin;
- bind deployment-scoped provider secret references;
- show adapter capabilities and compatibility;
- show dispatch, callback, and health readiness without transcript content;
- expose bounded retry/dead-letter recovery to authorized administrators;
- link an administrator to the provider's own operator console when available;
  and
- show an audit timeline for configuration, release, health, and recovery
  events.

The Basic LoomAI Inbox is deployed as a separate reference provider and
operator application. Platform may link to it, but must not absorb its queue,
operator session, or transcript into the control-plane database.

### 11.3 Roles and scopes

Define separate scopes, for example:

- `conversation:handover:configure`;
- `conversation:handover:health:view`;
- `conversation:handover:recovery`; and
- `conversation:handover:audit:view`.

Provider-side operator scopes remain provider-owned. A Platform administrator
credential is never forwarded to the provider or deployment callback API.

### 11.4 Basic LoomAI Inbox reference provider

The first release includes a small LoomAI-owned provider for demos, testing,
and low-volume customers. It proves that the generic channel boundary is
usable without turning the deployment runtime into an operator product.

The Basic LoomAI Inbox supports only:

- provider-local operator authentication and deployment membership;
- one simple queue per installed deployment binding;
- availability, claim, one active operator owner, and release;
- safe text replies and references to permitted attachments;
- the consented intake projection needed to address or reconnect with the
  user;
- return to AI and close commands; and
- basic unread, delivery, and connection status.

It deliberately excludes skills-based routing, advanced assignment rules,
workforce management, SLA automation, CRM ownership, omnichannel messaging,
AI-authored operator replies, and application-domain write authority. Its
adapter consumes the same safe outbox projection and emits the same signed,
normalized channel events required of any third-party provider. Replacing it
with another provider therefore changes the installed integration binding, not
the runtime state machine or AI Workspace.

The deployment remains the canonical source for conversation control and its
safe transcript. The inbox may retain a bounded provider-side copy only under
its declared retention policy and must not become the recovery authority for
the deployment conversation.

## 12. Generic AI Workspace Work

The generic widget/application receives only capability and presentation
configuration. It contains no dealership, Shopify, support-provider, or vendor
logic.

Add generic configuration such as:

```ts
humanHandover: {
  enabled: true,
  requestLabel: "Talk to a person",
  cancelLabel: "Cancel request",
  resumeAiLabel: "Continue with AI",
  showAvailability: true,
  showAnonymousContinuation: true
}
```

Required behavior in docked and max modes:

- show the request control only when the runtime manifest declares support;
- render the bounded runtime-supplied intake form, validation errors, consent,
  and anonymous continuation without asking the LLM to collect those fields;
- retain the same conversation and attachments during every transition;
- render `HUMAN_PENDING`, `HUMAN_ACTIVE`, `AI_RESUME_PENDING`, failure, and
closed states clearly;
- attribute human and AI messages distinctly without fabricating identity;
- disable AI-only suggestions/tools while human control is pending or active;
- route composer submissions through the canonical message endpoint;
- reconnect to SSE and replay missed events after page navigation/reload;
- expose cancel/resume only when permitted by current state and policy;
- support anonymous and private-auth connection profiles; and
- remain usable and accessible on mobile.

The Platform-hosted installation manifest supplies endpoint templates,
capabilities, labels, and connection profile. It never supplies secrets or an
operator identity.

## 13. Security And Governance Rules

1. Conversation ownership always derives from verified runtime identity.
2. The browser cannot select tenant, deployment, owner, operator, adapter URL,
  or channel credential.
3. The model can request a transition only through a registered, allowed,
  terminal control action.
4. There is no keyword-based hidden handover or hidden return-to-AI fallback.
5. Human control does not grant action or system-of-record authority.
6. External channel plugins receive only the configured safe projection.
7. Attachment content is shared only when its type, ownership, policy, and
  projection permit it; otherwise send an opaque safe reference or omit it.
8. Callback and SSE paths are rate-limited, origin/auth bound, replay-safe,
  and auditable.
9. Transcript retention, deletion, export, and external-provider retention are
  explicit deployment policies.
10. A channel outage is visible. It cannot be masked as successful handover or
  silently resume AI.
11. Every state change records original source, effective policy, actor,
  previous/new version, and timestamp without storing secrets.
12. The current Human Review authorization and separation-of-duty boundary is
  unchanged.
13. Provider authentication of an operator proves only channel authorship; it
  grants no application identity, domain-action authority, or Platform role.
14. Provider credentials are deployment-bound secret references resolved only
  by the integration connector.
15. Intake values are purpose-bound, minimized, encrypted separately, omitted
  from ordinary transcript projections, and deleted on their configured
  retention schedule.
16. The Basic LoomAI Inbox receives no broader callback, identity, transcript,
  or domain-action authority than a third-party `CONVERSATION_CHANNEL`.



## 14. Observability And Operations

Expose deployment-local health/readiness for:

- conversation-control persistence;
- AI-turn lease support;
- channel adapter readiness;
- dispatch outbox backlog and oldest age;
- callback verification failures;
- SSE connections/reconnects;
- pending queue age;
- provider acceptance and first-human-message latency when the adapter exposes
  those events;
- handover/resume success and failure totals; and
- conversations stuck in transitional states.

Useful metrics must avoid user message content and direct personal data.

Operational commands need bounded recovery for:

- retrying failed dispatch;
- expiring an abandoned pending request;
- reconciling a stale provider ownership reference without activating AI;
- rebuilding an adapter projection from durable deployment events; and
- returning a conversation to AI only through an explicit audited command.



## 15. Implementation Work Packages

Local implementation status recorded on 2026-10-13:

- **H0 complete:** terminology, authority boundaries, state model, trigger
  policy, and provider responsibilities are codified.
- **H1 complete and published:** AI Fabric `0.8.32` passed the clean 30-module
  release reactor with 2,100 tests and zero failures/errors/skips, plus focused
  conversation-control, terminal-action, vector-contract, real-app, packaging,
  and restart checks. The immutable tag, GitHub release, BOM, core, execution,
  and chat-session artifacts are published.
- **H2-H6 complete and locally green:** runtime, connector, Platform,
  Basic LoomAI Inbox, AI Workspace, migrations, UI, and external guides are
  present. Private critical gates passed 351 product-service tests, 937
  Platform-backend tests, Basic Inbox verification, Platform UI build/smoke,
  and widget type/build/smoke checks.
- **H7 complete for controlled release:** staging release `rel-49b2eb38` is
  `APPLIED_VERIFIED` / `PASSED`. Direct and LLM-selected handover, anonymous
  ownership, consented intake, duplicate idempotency, Basic Inbox claim/reply,
  SSE replay, explicit return to AI, real-provider execution, restart,
  provider outage recovery, forged callback rejection, and owner isolation are
  recorded in Section 19.



### H0: Contract approval and terminology

- approve the separation between Human Review and Conversation Handover;
- approve first-release states, transitions, trigger policy, and compatibility;
- define public/private scopes and safe transcript projection; and
- create the public AI Fabric change request.

**Exit:** no endpoint, manifest, or UI uses Human Review task semantics for
conversation ownership.

### H1: AI Fabric conversation-control release

- implement generic state, participant-message, lease, idempotency, and
persistence contracts;
- implement orchestration/effect guards and terminal control-action support;
- add migration and framework tests;
- publish an immutable framework release; and
- verify from an empty Maven cache.

**Exit:** a standalone host application can hand a conversation to a human,
append human messages, resume AI, survive restart, and reject races.

### H2: Runtime data plane

- add deployment migrations and framework wiring;
- add canonical owner message/control APIs;
- implement typed intake admission, validation, encrypted purpose-bound
  persistence, and expiry;
- add the normalized private channel-event API;
- add transactional outbox and recovery;
- add SSE/replay;
- add scopes and anonymous-token policy; and
- expose capability/health metadata.

**Exit:** focused runtime tests prove direct button and structured action paths
converge on one durable command service.

### H3: Marketplace and behavior composition

- add `CONVERSATION_HANDOVER` extension contract;
- add reserved `conversation-control` action adapter validation;
- add `INTEGRATION` / `CONVERSATION_CHANNEL` manifest and compiler support;
- publish the first-party handover action plugin and the reviewed Basic LoomAI
  Inbox channel integration plugin;
- compile bounded intake schema, consent, anonymous-continuation, projection,
  and retention policy into server-owned deployment configuration;
- update compatible conversational templates; and
- gate apply on exact runtime capability evidence.

**Exit:** a deployment gets handover only by installing compatible reviewed
plugins and publishing a new immutable version.

### H4: Platform channel integration operations

- add channel-plugin installation and compatibility UI;
- add deployment-bound secret-reference configuration;
- add capability, health, dispatch, callback, and dead-letter status;
- add bounded administrative recovery and audit UI;
- link to the provider-owned operator console when configured; and
- test two-customer and two-deployment isolation without storing transcript
  content in Platform.

**Exit:** an authorized customer administrator can install, release, inspect,
and recover the channel integration while all operator workflows remain in the
provider service.

### H5: Basic LoomAI Inbox reference provider

- build the separate provider service and operator UI;
- add provider-local operator authentication and deployment membership;
- implement the bounded queue, availability, claim, reply, release, return,
  close, and basic unread/delivery behavior;
- consume only the safe dispatch projection through the reviewed adapter;
- emit only signed, idempotent normalized channel events;
- enforce consented intake projection and provider-side retention; and
- prove that no private runtime, Platform, or widget path is used.

**Exit:** the Basic LoomAI Inbox completes a conversation handover through the
same `CONVERSATION_CHANNEL` contract available to another provider and cannot
perform application-domain writes.

### H6: Generic AI Workspace

- add manifest capability and generic configuration;
- add request, structured intake, pending, human-active, return, failure, and
  reconnect UI;
- use canonical messaging and SSE;
- preserve navigation, messages, attachments, and context;
- verify docked/max and mobile/desktop layouts; and
- update external installation/auth guides.

**Exit:** the same widget package works for a dealership, Shopify, support, or
another customer without domain code in the generic component.

### H7: Hosted canary and market-readiness evidence

- deploy one non-production conversational canary with anonymous access;
- install the handover action and Basic LoomAI Inbox channel plugin;
- execute the full lifecycle with a real LLM;
- execute direct-button, optional-contact, and anonymous-continuation paths;
- run restart, adapter outage, duplicate, race, isolation, and recovery tests;
- promote only after stored source/runtime/template/plugin/widget evidence is
current; and
- update canonical status and operating-context documents with proven facts.

**Exit:** hosted evidence supports the exact release composition and no claim
exceeds its recorded maturity.

## 16. Verification Matrix

The release gate must include at least:


| Scenario                             | Required result                                                          |
| ------------------------------------ | ------------------------------------------------------------------------ |
| Owner clicks **Talk to a person**    | One idempotent request; AI stops; pending state shown                    |
| Explicit natural-language request    | Structured LLM action invokes the same command; no text matcher          |
| Model recommends escalation          | User receives a proposal/confirmation; no silent takeover                |
| Optional intake is shown              | Control stays `AI_ACTIVE`; no dispatch exists before accepted submission  |
| User continues anonymously            | Handover proceeds when allowed; no contact field is fabricated            |
| Contact follow-up is supplied         | Type, consent and verification policy are enforced; projection is bounded |
| Invalid or cancelled intake           | No control transition, dispatch, or retained unauthorized contact data    |
| Duplicate button/action request      | Same active handover returned; no duplicate dispatch                     |
| User messages while pending          | Persisted in order and delivered after/when channel is ready; no AI call |
| Duplicate/conflicting provider claim | Current binding/version accepts one transition and rejects stale events   |
| Human reply                          | Correct owner receives it through SSE and durable replay                 |
| Basic Inbox claim/reply/release       | Uses only the standard adapter and normalized event contract              |
| Browser navigation/reload            | Same conversation, state, messages, and attachments return               |
| User/provider returns to AI          | Durable close/finalization, then AI is admitted on a new epoch           |
| Handover during an AI turn           | No post-transition action side effect; race outcome is explicit          |
| Adapter unavailable                  | Visible failed/pending status; retries bounded; no silent AI fallback    |
| Runtime restart                      | State, assignment, messages, outbox, and event replay remain correct     |
| Forged conversation/tenant           | Fail closed without existence leakage                                    |
| Anonymous token from another session | Cannot view, message, cancel, or resume the conversation                 |
| Forged provider callback             | Rejected; no state/message mutation                                      |
| Human requests a domain action       | Normal governed action/review authority still applies                    |
| Extension absent                     | Runtime and widget do not advertise or accept handover                   |
| Human Review regression              | Existing action-proposal approve/reject lifecycle remains unchanged      |
| Two tenants/two deployments          | No binding, transcript, event, secret, or callback crossover             |


Quality evaluation should also include paraphrased and multilingual requests
for a person. Model accuracy may be imperfect and measurable; production code
must not patch the failures with phrase matching.

## 17. Release Sequence And Rollback

Release in this order:

1. AI Fabric framework release with handover disabled by default.
2. Private runtime packaging, migrations, APIs, and disabled capability.
3. Deployment integration-connector channel SPI and Basic LoomAI Inbox
   adapter.
4. Platform catalog/compiler/plugin support and channel operations UI.
5. Basic LoomAI Inbox provider service and operator UI.
6. Generic AI Workspace support hidden unless advertised.
7. One staging conversational deployment with `CONVERSATION_HANDOVER` and the
   Basic LoomAI Inbox binding enabled.
8. Hosted verification and explicit owner approval.
9. New template/plugin versions for controlled customer preview.

Rollback disables new handover admission but must not strand existing human
conversations. The runtime continues serving active handovers until they are
returned or closed, then the extension may be removed in a later release. A
rollback must never route an active human conversation back to AI silently.

## 18. Completion Definition

This plan is complete only when:

- Conversation Handover is distinct from action Human Review in code, API,
UI, documentation, and audit;
- the runtime owns a durable, race-safe state machine;
- both direct and LLM-selected paths call the same command;
- structured intake supports anonymous continuation and consented optional
  follow-up data without making the LLM an identity authority;
- AI cannot respond or execute after human control begins;
- human messages and return-to-AI survive restart and reconnect;
- Marketplace templates compose the extension, action, and channel through
reviewed primitives;
- operator queues, identity, assignment, presence, and inbox UX remain owned by
the selected human-channel provider;
- the Basic LoomAI Inbox implements the standard `CONVERSATION_CHANNEL`
  contract, has no private runtime path, and remains within its low-volume
  first-release boundary;
- Platform and the runtime do not duplicate a provider workforce product;
- anonymous and authenticated users remain owner-isolated;
- the generic AI Workspace has no customer-domain coupling;
- release gates pass with Basic LoomAI Inbox and two-tenant/two-deployment
proof;
and
- one hosted conversational deployment has current immutable evidence.

Those conditions are met for the current controlled first release on
`CONVERSATIONAL` deployments using reviewed channel integrations. LoomAI may
describe this scope as Conversation Handover with Basic LoomAI Inbox support.
It must not imply support for Smart Brain, specialist-team handover, arbitrary
provider adapters, or enterprise workforce routing until those separately
deferred scopes are implemented and certified.

## 19. Final Release And Hosted Evidence

Evidence recorded on 2026-10-13 supersedes the earlier active/pending status in
this document.

### 19.1 Immutable release identity

- Public framework source: `561659dc0a4e8872f83654ef8549ead3cc4acc32`.
- Immutable tag and release: `ai-fabric-framework-v0.8.32`.
- Framework verification: 30 selected modules and 2,100 tests passed with zero
  failures, errors, or skips. Container vector contracts, 26 real-app reactor
  entries, 11 packaged real-app starts, restart smoke, integration compilation,
  and external-consumer checks passed.
- Maven Central returns HTTP `200` for the `0.8.32` BOM, core, execution, and
  chat-session artifacts. The GitHub release URL also returns HTTP `200`.
- Private source implementing the final anonymous authorization boundary:
  `7b4665849b0f82d862ef898ec659228668d19de7` on `Platform-V11`.
- Runtime image workflow `38391857383` and Platform Core workflow
  `38391857307` passed for that exact source.
- Runtime image digest:
  `sha256:2a11764b426f714b58d16210c7d2e0783361413a5534feeded41c7c11e6abff6`.
- Staging Platform backend deployment `p14hg50bjh1opncgtoqlo32s` completed from
  exact source `7b4665849b0f82d862ef898ec659228668d19de7`; app
  `lnlgausj8hzrim7f5fa8pmpx` is `running:healthy` and public health is `UP`.
  Coolify's application-level `git_commit_sha` field remained a stale branch
  annotation, so the immutable deployment record is the source-of-truth proof.

### 19.2 Platform composition and apply proof

- Canary deployment: `dep-ebf4c7c3` / customer `cus-28e3b6f9` / tenant
  `ten-09df34bf`.
- Published deployment version: `ver-627e433c`.
- Target profile: `dtp-coolify-staging-behavior`.
- Installed Marketplace resources: template `mpi-ce89cdfd`, handover action
  `mpi-b1f2947d`, and channel integration `mpi-2b9931a1`.
- Runtime artifact `dsa-f47248fa` is promoted on `staging` with the exact image
  digest and AI Fabric `0.8.32` capability manifest.
- Platform release `rel-49b2eb38` reached `APPLIED_VERIFIED`; verification is
  `PASSED`.
- Runtime app `yq1tqyv6c5hn379ik5g3yff3`, connector
  `p48zva29xjkg6h8kvvdz4fki`, runtime database
  `t4wex7lwd1x58e8hvzvii7vc`, Basic Inbox
  `g14p1jagvqwu4nbul0pyyeqt`, and Inbox database
  `h12u68e3oz0f90cb5w1dentt` are deployment-scoped resources.
- Effective runtime configuration contains
  `AI_FABRIC_RUNTIME_AUTHZ_ALLOW_ANONYMOUS_SESSIONS=true` only because this
  deployment explicitly enables signed public bootstrap under
  `ALLOW_VERIFIED`.

### 19.3 Live lifecycle proof

- Fresh signed anonymous session
  `anon-54862e1d-e8be-4f30-b370-3ab44bba84fb` was bound to the expected
  deployment/customer/tenant and received only the declared chat and handover
  scopes.
- Conversation
  `chat-handover-live-ef28820a-403a-49a7-965e-27f19c44581f` and handover
  `handover-2f636394d63dbab184b6fcbeb7da104e` completed:
  `AI_ACTIVE -> HUMAN_PENDING -> HUMAN_ACTIVE -> AI_ACTIVE`.
- Basic Inbox accepted the request as `WAITING`, queued one operator claim,
  delivered one human reply into the canonical deployment transcript, and
  returned the conversation explicitly as `RETURNED_TO_AI`.
- Runtime replay and SSE exposed durable `HANDOVER_REQUESTED`,
  `HANDOVER_CLAIMED`, `HUMAN_MESSAGE`, and `HANDOVER_RELEASED` events with
  stable event IDs. No browser-only message/state was required.
- A post-return and post-restart anonymous AI request returned HTTP `200`,
  `success=true`, and a provider request ID. Intent extraction used the live
  OpenAI provider; no deterministic production provider or text matcher was
  used.
- Natural-language request conversation
  `chat-handover-natural-4be28c9f-fd64-464d-a6b3-a89766bd0a5f` returned typed
  `HANDOVER_REQUESTED` through the registered LLM-selected terminal action.
  Its test handover was then explicitly cancelled.
- Missing contact consent returned `HANDOVER_INTAKE_CONSENT_REQUIRED` while
  control remained `AI_ACTIVE` with no handover or event. A consented request
  exposed only declared `name` and `email` fields to the Inbox.
- Repeating the exact consented request with one idempotency key returned
  `IDEMPOTENT_ACTIVE_HANDOVER` and the same handover ID; no duplicate dispatch
  was created.
- A second anonymous session received `404 NOT_FOUND` for the first owner's
  conversation. Existing cross-tenant/deployment and forged-callback tests also
  fail closed; the hosted forged callback returned
  `CHANNEL_CALLBACK_SIGNATURE_INVALID`.
- Controlled restart deployment `mrtq4f8d3agn9dfatvpl09mc` completed. The
  renewed anonymous token retained the same session ID, and the full transcript,
  event journal, `AI_ACTIVE` state, and subsequent real-provider turn survived.
- Provider-outage evidence proves visible `HUMAN_PENDING`, bounded retry, and
  eventual Inbox recovery. The final dispatch overview contains 23
  `DELIVERED`, one terminal `SKIPPED` obsolete-lifecycle record, and zero
  `PENDING`, `PROCESSING`, or `DEAD` records. `SKIPPED` is an intentional
  terminal outcome and is not a hidden success or retry backlog.

### 19.4 Anonymous authorization correction

The private runtime remains fail-closed by default. Anonymous entity access is
permitted only when the deployment explicitly enables it and every verified
claim is present: `PUBLIC_RUNTIME_ANONYMOUS` auth mode, `PUBLIC_BROWSER` caller,
matching subject/session, deployment, tenant, issuer, audience, unexpired
expiry, and `chat:query` scope. Platform emits the opt-in only when public
bootstrap, `ALLOW_VERIFIED`, and a public signing key are all configured.

Focused correction gates passed:

- `AllowVerifiedEntityAccessPolicyTest`: 6/6;
- `RailwayProvisioningPlanServiceTest`: 33/33;
- full private runtime suite: 266/266; and
- Platform provisioning plus release-verification suites: 58/58.

This is a typed verified-identity rule. It does not inspect query text, infer a
domain, broaden retrieval/action authority, or add a fallback that masks an LLM
decision.

### 19.5 Release posture and accepted nonblocking gaps

All capability-critical gates are green: immutable framework publication,
clean framework reactor, current-only private consumption, auth fail-closed
tests, exact image evidence, Platform apply verification, lifecycle,
idempotency, consent, callback rejection, owner isolation, durable replay,
restart, outage recovery, and real-provider action selection.

The optional local framework real-provider matrix was not made a deterministic
publication blocker; the hosted canary supplies the real OpenAI proof required
for this host composition. The unrelated aggregate
`full-platform-release-readiness` suite was not relabelled or required for this
bounded feature release. Known Shopify wording and Partner identity/Supabase
issues remain owned by their product gates. Per the release decision, those
full-checklist gaps are recorded but do not block Conversation Handover because
its critical tests and hosted evidence pass.

### 19.6 Operator cleanup

- Hetzner firewall `10915120` was restored exactly to its pre-run rules hash
  `71ee78c836904fbaffa01c6ff87ead74f6186cda554a9bbd5629e9ef7eca90bc`.
- The operator CIDR has zero matches and direct SSH is filtered. Staging port
  `8000` retains its normal baseline unauthenticated `401`; no temporary rule
  remains.
- Production firewall `10918233` was not changed during this release.
