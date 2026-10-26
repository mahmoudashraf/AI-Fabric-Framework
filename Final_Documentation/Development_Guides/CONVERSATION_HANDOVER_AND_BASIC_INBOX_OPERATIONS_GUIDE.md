# Conversation Handover Operations

The formal managed provider is now shared LoomAI Desk. The former per-deployment
Basic Inbox service and its provisioning path are retired. Use the
[Desk customer and agent guide](../External_User_Guides/LOOMAI_DESK_CUSTOMER_AND_AGENT_GUIDE.md)
for activation, Supabase authentication and scoped agent assignments.

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

1. AI Fabric `0.8.41` conversation-control mechanics.
2. Runtime migration `V10__conversation_handover.sql`.
3. Runtime capability `conversation-handover-v1` and
   `LOOMAI_CONVERSATION_HANDOVER_ENABLED=true`.
4. The canonical `request_conversation_handover` action plugin.
5. One reviewed `INTEGRATION` / `CONVERSATION_CHANNEL` plugin.
6. The generic REST connector conversation-channel worker.
7. An enabled provider binding; managed Desk uses the shared service for that environment.
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
  -> shared LoomAI Desk through the exact customer/deployment/target binding
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
LOOMAI_CONVERSATION_HANDOVER_DISPATCH_MAX_ATTEMPTS=5
```

The dispatch limit defaults to five and accepts only 1 through 8. Normal managed
Desk plans select eight so delivery can survive a brief provider restart; other
providers keep five. The setting is server-owned, not a chat request parameter.
It bounds completed retryable delivery failures, not AI semantics or lease-expiry
redelivery. Permanent failures do not consume extra retries. Backoff, stable
message identity and FIFO remain unchanged; exhaustion stays visible and never
authorizes an automatic return to AI. Exact hosted qualification is tracked in
010.48, separately from this configuration contract.

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

## Managed Desk Configuration

Use one shared Desk application and its own PostgreSQL store per hosting
environment, not per customer. Platform registers each deployment/target binding
and its distinct provider/callback credentials server-side. Supabase authenticates
people; explicit Support and Reviewer assignments authorize their binding access.
An owner is not implicitly a support agent or reviewer. See the service
[README](../../product-services/loomai-desk-service/README.md) for server-only
configuration. Do not recreate a retired per-deployment Inbox to restore an old
release or copy staging credentials/grants to production.

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
