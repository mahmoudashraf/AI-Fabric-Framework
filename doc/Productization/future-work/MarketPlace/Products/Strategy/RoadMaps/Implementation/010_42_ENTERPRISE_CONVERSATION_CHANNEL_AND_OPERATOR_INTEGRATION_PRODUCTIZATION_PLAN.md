# 010.42 Enterprise Conversation Channel And Operator Integration Productization Plan

- **Status:** Proposed. Document-only scope; not part of the Basic LoomAI Inbox release.
- **Created:** 2026-10-13
- **Depends on:** AI Fabric `0.8.32` conversation control and participant messages; LoomAI `CONVERSATION_HANDOVER`; Marketplace `INTEGRATION / CONVERSATION_CHANNEL`.
- **Primary goal:** Let an enterprise customer or certified support provider operate human conversations through its own identity, workforce, notification, and inbox systems without changing deployment-local AI/human authority.
- **Current baseline:** The Basic LoomAI Inbox proves the provider-neutral channel, callback, queue, claim, reply, close, and explicit return-to-AI lifecycle. It is a low-volume reference provider, not the enterprise workforce product.

## 1. Product Verdict

Enterprise conversation handover should be productized as a certified
**Conversation Channel integration**, not by expanding the deployment runtime
into an operator workforce system and not by exposing deployment credentials
to a browser.

The durable conversation and its current response authority remain in the
customer's assigned deployment. The enterprise operator system owns operator
identity, teams, assignment, presence, notifications, SLA workflows, and its
inbox UX. The deployment connector mediates the two systems through one
versioned provider-neutral contract.

This keeps the feature useful in three forms:

1. Basic LoomAI Inbox for demonstrations, evaluation, and very small teams.
2. A customer's own staff application using a protected server-side adapter.
3. A certified third-party contact-center or live-support provider installed
   as a Marketplace integration.

## 2. Non-Negotiable Ownership Boundaries

| Concern | Authoritative owner |
| --- | --- |
| AI versus human response authority | Deployment-local runtime and AI Fabric control state |
| Canonical safe transcript and owner event replay | Deployment-local runtime |
| Provider dispatch, callback verification, retry, and dead letters | Deployment integration connector |
| Operator identity, team membership, assignment, presence, and SLA | Customer IdP and operator provider |
| Customer application authorization and business writes | Customer application |
| Installation, reviewed adapter selection, secret references, and release | LoomAI Platform |
| Browser visitor identity | Deployment-issued anonymous owner proof or reviewed authenticated broker |

An operator reply does not grant business-action authority. A refund, booking,
reservation, account mutation, or other domain effect still crosses the
customer application's normal governed action and authorization boundary.

## 3. Enterprise Operator Authentication

### 3.1 Required production profiles

Support two explicit profiles:

- **Customer operator application:** OIDC Authorization Code with PKCE against
  the customer's IdP. The customer backend exchanges identity for a short-lived
  operator API token scoped to one customer, deployment binding, team, and set
  of conversation operations.
- **Third-party channel provider:** OAuth 2.0 client credentials or private-key
  JWT for server-to-server dispatch and callbacks. Secrets remain deployment
  bindings resolved by the connector; they never enter the workspace manifest.

### 3.2 Required claims and checks

Every operator command must resolve trusted values for:

- customer and tenant;
- deployment and channel binding;
- operator subject and display projection;
- team/queue membership;
- allowed operations such as `queue:read`, `conversation:claim`,
  `message:write`, `conversation:return-ai`, and `conversation:close`;
- short expiry, issuer, audience, token ID, and revocation posture.

The receiving boundary must reject missing scope, cross-binding access,
revoked operators, stale conversation versions, and replayed command IDs.
Static browser API keys are not an enterprise authentication mechanism.

## 4. Published Integration Contract

Publish one versioned contract package containing:

- OpenAPI for synchronous operator and provider commands;
- AsyncAPI or an equivalent event schema for push delivery;
- JSON Schemas for dispatches, participant messages, state events, receipts,
  typed failures, and callback acknowledgements;
- authentication profiles and required scopes;
- idempotency, ordering, timeout, retry, and replay rules;
- signature and key-rotation rules;
- compatibility and deprecation policy;
- a conformance test kit and reference adapter.

The first stable namespace should be `conversation-channel/v1`. Provider
objects use opaque LoomAI references and safe actor projections; they do not
receive runtime API keys, model credentials, raw private context, or authority
to select another deployment.

### 4.1 Required command surface

The contract must cover:

- accept/reject a handover dispatch;
- list and inspect provider-authorized conversations;
- claim or release a waiting conversation;
- append a human participant message;
- request explicit return to AI;
- close the conversation;
- acknowledge delivery and expose retryability;
- retrieve bounded replay after a cursor; and
- report provider health and binding identity.

All mutations require an idempotency key and expected control/provider
version. Success returns a durable receipt; ambiguity never becomes implicit
success.

## 5. Push And Asynchronous Delivery

Polling may remain a development fallback, but enterprise delivery needs a
push path with durable replay.

Recommended model:

```text
deployment outbox
  -> connector adapter
  -> provider webhook or provider event API
  -> provider queue and operator notification

operator command
  -> provider/customer backend
  -> signed callback to deployment connector
  -> runtime admission and state transition
  -> owner event journal
  -> visitor SSE or WebSocket update
```

Requirements:

- transactional outbox before dispatch;
- at-least-once delivery with idempotent consumption;
- monotonic conversation sequence and replay cursor;
- signed callbacks with timestamp, nonce, event ID, and key ID;
- bounded exponential retry and visible dead-letter recovery;
- provider acknowledgement separate from runtime state commitment;
- SSE for the existing visitor contract, with WebSocket optional only when a
  provider genuinely needs bidirectional presence or typing events; and
- no shared Platform message proxy in the customer traffic path.

Email, mobile, Slack, Teams, or contact-center notifications are provider
features triggered from the provider queue. They are not deployment runtime
responsibilities.

## 6. Marketplace And Provisioning

Each certified adapter is an `INTEGRATION` plugin contributing exactly one
reviewed `CONVERSATION_CHANNEL` implementation. Installation captures only
typed configuration and secret references, for example:

- provider tenant/account reference;
- queue/team binding;
- OAuth client or signing-key secret references;
- callback host allowlist and expected audience;
- retention and transcript projection policy;
- enabled message/attachment capabilities; and
- health/readiness thresholds.

Template compilation must verify deployment behavior compatibility,
single-active-channel rules, adapter version, required secrets, callback
reachability, and scope. Release/apply emits immutable binding evidence and a
rollback-safe prior version.

For customer-built staff applications, LoomAI should supply an adapter SDK and
server-side BFF example. The public AI Workspace remains unchanged because it
consumes the generic owner handover and event-stream contract.

## 7. Data, Privacy, And Compliance

Before certification, define:

- which safe transcript projection leaves the deployment;
- purpose-bound consented intake fields;
- attachment reference policy and malware/content checks;
- provider and deployment retention independently;
- deletion propagation and legal-hold behavior;
- residency, encryption, audit export, and data-processor roles;
- redaction before provider dispatch where configured; and
- operator-visible versus customer-visible actor identity.

The Platform stores installation metadata and health evidence only. It must
not become a second transcript database.

## 8. Delivery Phases

### E0: Contract extraction

- Derive `conversation-channel/v1` from the hosted Basic Inbox proof.
- Remove service-specific naming from schemas.
- Publish OpenAPI, async events, auth profiles, and failure taxonomy.
- Build a provider conformance harness using deterministic replay fixtures.

### E1: Strong operator identity

- Add OIDC/OAuth profiles, scoped short-lived tokens, team binding, revocation,
  and auditable operator projections.
- Prove customer, tenant, deployment, queue, and conversation isolation.
- Add separation-of-duty tests for domain actions.

### E2: Push delivery and recovery

- Certify signed webhooks, durable outbox, replay cursors, retry, dead letters,
  key rotation, and reconnect behavior.
- Prove no message loss across connector, provider, or runtime restart.
- Add latency and sustained-load objectives.

### E3: Customer and provider SDKs

- Publish server SDKs, BFF example, typed client models, integration guide,
  and a certification test runner.
- Add one real customer-owned operator UI integration.
- Add one third-party provider adapter only after its contract passes E0-E2.

### E4: Marketplace certification

- Add reviewed installation forms, health probes, immutable release evidence,
  support ownership, and rollback.
- Publish capability and regional availability declarations.
- Run production canaries before general availability.

## 9. Release Gates

The enterprise claim is allowed only when all of the following pass:

- OIDC/OAuth issuance, expiry, revocation, audience, and scope tests;
- two-customer, two-tenant, two-deployment, and two-channel isolation;
- duplicate, delayed, reordered, and replayed event handling;
- provider outage, timeout, retry, and dead-letter recovery;
- runtime, connector, provider, and browser reconnect/restart continuity;
- in-flight AI invalidation before an effect or answer commits;
- explicit return-to-AI authorization;
- transcript projection, redaction, retention, and deletion verification;
- operator action audit with no browser-exposed provider credential;
- load and latency evidence for the declared service tier; and
- external conformance suite against the published contract.

## 10. Explicit Non-Goals

This plan does not turn LoomAI into a full contact-center suite. It does not
add workforce forecasting, telephony, omnichannel campaign management,
automatic operator-written domain effects, or a central Platform transcript
store. Those concerns remain provider/customer products unless a later market
decision establishes a distinct LoomAI product with its own ownership and
operational model.
