# Conversation Handover And Basic Inbox Operations

## Purpose

Conversation Handover temporarily changes who may answer one LoomAI
conversation. It is separate from Human Review: Human Review approves or
rejects one governed action proposal, while Conversation Handover transfers
reply ownership between AI and a human channel.

The first release supports `CONVERSATIONAL` deployments only. It works with
anonymous direct-runtime, authenticated direct-runtime, backend-mediated
private-runtime, and Shopify-hosted AI Workspace connection profiles when the
profile supplies the canonical handover routes and scopes.

## Required Release Composition

Use one immutable deployment version containing all of the following:

1. AI Fabric `0.8.32` conversation-control mechanics.
2. Runtime migration `V10__conversation_handover.sql`.
3. Runtime capability `conversation-handover-v1` and
   `LOOMAI_CONVERSATION_HANDOVER_ENABLED=true`.
4. The canonical `request_conversation_handover` action plugin.
5. One reviewed `INTEGRATION` / `CONVERSATION_CHANNEL` plugin.
6. The generic REST connector conversation-channel worker.
7. A provider instance, initially Basic LoomAI Inbox.
8. An AI Workspace manifest that advertises the canonical message, request,
   cancel, retry, control, event, and AI-resume routes.

Do not enable the public capability if any migration, route, connector,
binding, or provider readiness check is absent.

## Data And Authority Flow

```text
Browser AI Workspace
  -> assigned deployment runtime canonical message API
  -> deployment-local control/message/outbox tables
  -> deployment integration connector
  -> dedicated Basic LoomAI Inbox instance
  -> authenticated human operator
  -> signed normalized callback through connector
  -> deployment-local canonical transcript and owner SSE replay
```

The Platform composes and releases the installation. It does not proxy normal
customer messages and does not retain a duplicate transcript. The runtime owns
AI/human authority. The provider owns operator authentication, queue, claim,
presence, and inbox behavior. A human participant gains no domain-action
authority from the handover.

## Runtime Configuration

Server-owned deployment values:

```text
LOOMAI_CONVERSATION_HANDOVER_ENABLED=true
LOOMAI_CONVERSATION_HANDOVER_CHANNEL_BINDING_ID=<reviewed binding id>
LOOMAI_CONVERSATION_HANDOVER_ANONYMOUS_ALLOWED=true|false
LOOMAI_CONVERSATION_HANDOVER_INTAKE_SCHEMA_JSON=<compiled bounded schema>
LOOMAI_CONVERSATION_HANDOVER_REQUIRED_INTAKE_FIELDS=<optional list>
```

Secret references resolved at deployment time:

```text
LOOMAI_CONVERSATION_HANDOVER_ENCRYPTION_SECRET
LOOMAI_CONVERSATION_HANDOVER_CALLBACK_SIGNING_SECRET
```

Signing and encryption secrets must be distinct and at least 32 characters.
They never enter public manifests or browser configuration. Production uses the
deployment's durable PostgreSQL database; framework schema auto-creation stays
disabled.

## Connector Configuration

```text
REST_CONNECTOR_CONVERSATION_CHANNEL_ENABLED=true
REST_CONNECTOR_CONVERSATION_CHANNEL_BINDING_ID=<same reviewed binding id>
REST_CONNECTOR_CONVERSATION_CHANNEL_RUNTIME_BASE_URL=<private runtime URL>
REST_CONNECTOR_CONVERSATION_CHANNEL_RUNTIME_API_KEY=<secret reference>
REST_CONNECTOR_CONVERSATION_CHANNEL_RUNTIME_CALLBACK_SIGNING_SECRET=<runtime signing secret>
REST_CONNECTOR_CONVERSATION_CHANNEL_PROVIDER_BASE_URL=<provider service URL>
REST_CONNECTOR_CONVERSATION_CHANNEL_PROVIDER_API_KEY=<provider secret reference>
REST_CONNECTOR_CONVERSATION_CHANNEL_PROVIDER_WEBHOOK_SIGNING_SECRET=<provider callback secret>
```

The connector validates provider webhooks, then calls the normalized private
runtime API. It retries committed outbox records in order and exposes dead
dispatch recovery. It does not own conversation state or operator identity.

## Basic Inbox Configuration

Run one Basic Inbox instance per tenant/deployment. Required values are listed
in the service [README](../../product-services/basic-loomai-inbox-service/README.md).
The trusted `BASIC_INBOX_TENANT_ID`, `BASIC_INBOX_DEPLOYMENT_ID`, and binding ID
must match the released deployment. Cross-scope dispatches fail closed.

Use PostgreSQL in production. The default H2 file store is for local proof
only. Give each instance separate provider, callback-signing, encryption, and
operator credentials through the hosting secret manager.

## AI Workspace Behavior

The Workspace shows **Talk to a person** only when the active manifest declares
handover support. Optional name, email, and phone fields come from the bounded
runtime schema; users may continue anonymously when policy allows. The LLM is
not used to collect identity or infer consent.

All messages use:

```text
POST /api/chat/me/conversations/{conversationId}/messages
```

During `AI_ACTIVE`, the runtime admits one leased AI turn. During
`HUMAN_PENDING` or `HUMAN_ACTIVE`, the same endpoint appends and dispatches the
user message without calling AI. During `AI_RESUME_PENDING` or `CLOSED`, it
returns a typed non-acceptance result.

Conversation list/detail endpoints project the canonical participant timeline,
so navigation and reload restore user, AI, human, and system messages. Public
projections omit tenant/owner scope, raw provider actor references, provider
conversation references, and internal model/request metadata.

## Rollout Gate

Before enabling a customer template, prove:

- direct button and model-selected structured action reach the same command;
- model-recommended transfer requires confirmation;
- optional contact consent and anonymous continuation;
- duplicate requests and provider callbacks are idempotent;
- pending and active user messages remain FIFO;
- a pending human callback prevents premature return to AI;
- browser navigation/reload and SSE replay restore the same transcript;
- runtime/provider restart recovers durable state and outboxes;
- adapter outage is visible and never silently resumes AI;
- forged owner, tenant, deployment, callback signature, timestamp, and nonce
  fail closed;
- two tenant/deployment instances cannot cross dispatches or transcripts; and
- existing Human Review action approval/rejection still passes.

Rollback stops new admission but keeps the runtime, connector, and provider
available for active handovers until each is explicitly returned to AI or
closed. Never use a timeout to silently hand control back to AI.
