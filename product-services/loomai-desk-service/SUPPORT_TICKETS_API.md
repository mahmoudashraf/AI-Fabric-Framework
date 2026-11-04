# Support Tickets Backend Contract

Source contract: `src/main/java/com/loomai/desk/ticket/TicketContracts.java`.
The native contract is deployed and its core journeys are qualified; see
[the dated acceptance](../../verification-support/support-kit-01057/2026-11-04-production-acceptance.md)
for exact sources and limits. No history import,
compatibility writer, transcript copy, or AI engine is introduced.

## Provisioning

Existing service-admin authentication remains
`X-LoomAI-Desk-Service-Key`. Call
`PUT /api/desk/admin/v1/bindings/{bindingId}/support-space`:

```json
{
  "spaceId": "space-example",
  "expectedVersion": null,
  "name": "Support",
  "enabled": true,
  "humanHandoverEnabled": true,
  "providerId": "desk",
  "providerBindingId": "tpb-example",
  "runtimeBaseUrl": "https://registered-runtime.example",
  "runtimeTrustedKeyHeader": "X-AIFABRIC-RUNTIME-API-KEY",
  "conversation": {"mode": "executor"},
  "identity": {
    "mode": "TRUSTED_PRIVATE_HOST",
    "issuer": "https://registered-console.example",
    "jwksUri": "https://registered-console.example/api/support/jwks",
    "audience": "loomai-desk-requester",
    "requesterNamespace": "console-users",
    "privateServerKey": "write-only-distinct-secret-at-least-32-characters"
  },
  "connectorKey": "write-only-distinct-connector-secret-at-least-32-characters",
  "allowedOrigins": ["https://console.example"],
  "topics": ["setup", "knowledge"],
  "closedRetentionDays": 30,
  "serverConnector": null
}
```

Create uses null `expectedVersion`; updates require the returned opaque string
version. Full PUT includes secrets, encrypted through Desk's existing encryption
helper. GET at the same path returns `SpaceView`, never keys or JWKS material.
Customer/tenant/deployment/target come from the immutable existing binding.
The space ID and identity mode/issuer/namespace cannot be reassigned. Disabling
the space gates all ticket CRUD and exposes empty ticket capability sets, without
deleting stored requests. SESSION/TEAM/photo remain available when human handover
is enabled, independently of tickets. A disabled Desk binding denies all requester access.

`conversation` is typed, server-owned opening-message configuration, included in
the safe space view. Omitting the object or its mode explicitly defaults to
`executor`, matching the Support experience pack; Desk never relies on the
runtime's omitted-mode default. Mode and optional position are bounded strings,
not Desk enums: 1-64 ASCII characters, starting with a letter or number and
continuing with letters, numbers, `.`, `_` or `-`. Values are forwarded unchanged;
Desk validates syntax/size only, not membership in an active runtime catalogue.
For example `thinker`, `support_assistant` and a future registered custom mode
are accepted without rewriting them to executor. Explicit blank/malformed values
are rejected rather than defaulted. Omitted position stays absent. The runtime
owns active-code acceptance and any configured routing/rejection policy.
Both Platform's direct Supabase and private Console support-space provisioning deliberately send
`{"mode":"executor"}` without pinning a position. The Support pack chooses its
page-context position independently. A caller providing another mode must align
its workspace configuration deliberately; Desk does not infer it from ticket text.

For a form-first `/start`, Desk sends the saved intake plus these registered
`mode`/optional `position` fields to the canonical runtime messages endpoint.
Requesters cannot override them through ticket create/start inputs. This selects
the existing orchestration policy, not additional permissions: runtime identity,
action authorization/confirmation and scoped idempotency remain unchanged.
Linked conversations and continuation episodes never resend intake. Delivery
observation does not automatically repeat an uncertain POST. V5 adds only a JSON
configuration field on support spaces, with the explicit executor default; no
ticket data rewrite or migration is needed.

Runtime, issuer, JWKS and browser origins are registered by
the authenticated administrator. Ticket onboarding does not require a per-project
environment origin allowlist. `TicketEndpointPolicy` reuses the canonical HTTPS
URL and connection-time public-DNS checks; redirects and automatic retries are
disabled. The separate conversation-context callback policy and its global
allowlist remain unchanged (`loomai.desk.conversation-context-allowed-origins` /
`DESK_CONTEXT_ALLOWED_ORIGINS`). Outbound requests use the existing
DNS policy, bounded timeouts and no redirects. Loopback HTTP is test-only with
the existing explicit loopback switch. No token-supplied discovery URL is used.

## Requester Identity

The requester security chain is stateless and separate from operator OIDC.
Bearer identity is always required. A runtime token, app ID, service key,
email or agent role is not requester authentication.

The explicit server connector below is a different, registered server route;
it is never a fallback on requester routes.

### Registered Server Connector

For a SUPABASE space, admin PUT may register this optional additional input:

```json
{
  "serverConnector": {
    "authMode": "PUBLIC_RUNTIME_AUTHENTICATED",
    "callerType": "PUBLIC_BROWSER",
    "issuer": "https://published-runtime-issuer.example",
    "audience": "published-runtime-audience",
    "requiredScopes": ["chat:conversations", "chat:messages"]
  }
}
```

This profile is encrypted at rest, never included in requester/session/Ticket
projections. `requiredScopes` must include `chat:conversations`. The caller must
supply the existing per-space `X-LoomAI-Ticket-Connector-Key`, no Origin, and one
value for each generated `X-AIFABRIC-AUTH-` header: SUBJECT-ID, SUBJECT-TYPE
(`END_USER`), MODE, CALLER-TYPE, ISSUER, AUDIENCES, SCOPES, EXPIRES-AT,
CUSTOMER-ID, TENANT-ID and DEPLOYMENT-ID. The registered exact profile and binding
must match; audiences/scopes are comma-separated, expiry is a future UTC instant.
`X-AIFABRIC-CONVERSATION-ID` is additionally required for CREATE. Model JSON
cannot select owner, principal, issuer, URL or credentials.

`POST /api/desk/integrations/v1/spaces/{spaceId}/server-ticket-commands`
accepts the existing `CommandInput`, supporting CREATE, LIST and GET.
CREATE requires a valid `Idempotency-Key`; GET/LIST are read-only and do not.
These use the registered original Supabase subject/namespace,
without any control-plane callback, original JWT forwarding, or runtime token
minting. CREATE ignores body `conversationId` and takes only the authenticated
runtime-generated conversation header. A package-private `VerifiedRuntimeTrace`
proof is minted by the security filter after exact profile admission and consumed
only by the connector create operation. It authorizes adoption of the current
runtime-controlled owner turn, not arbitrary conversation lookup/linking.

Trust requirement: the registered runtime connector holds the dedicated space
key and forwards immutable `TraceContextSupport.forwardHeaders` from an already
owner-authorized conversation execution. Generated headers override configured
route templates; duplicate identity headers are rejected. A compromised trusted
connector key is an authority compromise, not a browser identity. Missing
conversation metadata never falls back to a latest ticket or body field.
Replaying a key under different trace identity/conversation is rejected.
Requester UI link/start still use original bearer + canonical runtime ownership
verification. No server-connector LINK fallback or synthetic RuntimeCredential
exists. Old callback/credentialUrl/credentialKey contracts are not supported.

`SUPABASE`: original provider JWT, exact registered issuer/audience/JWKS,
RS256/ES256, valid issued-at/expiry, non-anonymous subject. Ownership uses
space + registered namespace + verified issuer + immutable subject, not email.
Contact email is neither retained nor claimed verified. The existing direct
runtime broker maps that exact provider subject to runtime subject.

`TRUSTED_PRIVATE_HOST`: host verifies its own session/CSRF and sends these
server-to-server headers; never expose either credential to the browser:

```http
Authorization: Bearer <RS256-or-ES256-requester-assertion>
X-LoomAI-Requester-Server-Key: <identity.privateServerKey>
```

```json
{
  "iss": "https://registered-console.example",
  "aud": "loomai-desk-requester",
  "sub": "stable-immutable-console-user",
  "iat": 1793700000,
  "exp": 1793700300,
  "jti": "unique-assertion-id",
  "spaceId": "space-example",
  "operations": ["SESSION", "TEAM", "CREATE", "LIST", "GET", "START", "LINK_CONVERSATION", "RESOLVE", "CANCEL", "REOPEN", "SEEN", "TICKET_COMMAND"]
}
```

Maximum assertion lifetime is 300 seconds. JTI is required, not a one-use nonce:
bounded token replay follows ordinary bearer semantics; mutations separately
enforce durable scoped command idempotency. Private routes reject an Origin
header. Direct browser requests require an allowed origin when present; no
cookies are used and all responses are private/no-store.

### Exact Runtime Principal Mapping

Private requester identity stays stable per Console user, independently of
runtime ownership. Form-first runtime principal is **`support-ticket:<requestId>`**.
For private chat-first/action conversion, the trusted host adds signed claims
`runtimePrincipal` and `runtimeConversationId` to the requester assertion. The
latter must equal existingConversationId. Neither is an AI action ownership
parameter. The host derives them from its verified chat-authority row and
runtime-to-connector caller, not user form fields or arbitrary trace headers.

Before any provider write Desk verifies the actual runtime subject and owned
conversation against those claims. It pins the principal immutably in the
ticket's minimal integration mapping and returns it as `Ticket.runtimePrincipal`.
That value is routing metadata, not authentication. New episodes keep the same
pinned principal; existing canonical conversations are never re-owned. A private
principal is unique per space/ticket, so a host should use one unique principal
per casual chat, not one per Console user. Signed principal/conversation binding
also participates in create idempotency; changing it on replay returns 409.

Diagnostic consumers must resolve this exact principal and recheck requester
membership, resources and consent. If conversationId is absent, zero/multiple
matches fail explicitly; never select the latest ticket. No transcript or ticket
summary/status copy is needed in Console's authority mapping.

Start/link/episode creation additionally send:

```http
X-LoomAI-Runtime-Authorization: Bearer <short-lived-runtime-credential>
X-LoomAI-Runtime-Api-Key: <existing-private-runtime-trusted-backend-key>
```

For private spaces Desk forwards the two headers respectively as
`X-AIFABRIC-RUNTIME-AUTHORIZATION` and the registered `runtimeTrustedKeyHeader`
(default `X-API-KEY`; also supports `X-AIFABRIC-RUNTIME-API-KEY`). Direct
Supabase spaces send only the authorization header; Desk forwards it as
`Authorization`. Runtime credentials are never persisted/logged.

Desk checks `/api/chat/me/auth-context`: exact subject, END_USER subject type,
customer, tenant and deployment, then the existing owner-scoped conversation
GET when linking. A target-specific registered runtime URL pins the target.
No runtime calls are made for ordinary list/detail/status/seen.

## Requester Routes

Prefix `/api/desk/requester/v1/spaces/{spaceId}`. Every write requires
`Idempotency-Key` (1-180 characters); unchanged replays return the original safe
result, changed replays return 409. Versions are opaque strings, never JSON
numbers. Unknown/foreign IDs return 404, expiry 401, unsupported operations 422,
dependencies 503. Conflicts may include an authorized safe `current` projection.

- GET `/session`: `{authenticated,requesterId,space,capabilities}`.
- GET `/team`: existing binding-scoped safe support-team projection.
- GET `/team/{profileRef}/photo`: authenticated requester JPEG bytes, binding-scoped
  opaque profile ref, `private, no-store` and `nosniff`. Current roster and enabled
  state are rechecked; no operator identity, raw staff ID or admin URL is needed.
- POST `/requests`: `{subject,details,topic,existingConversationId}`; 201.
- GET `/requests?status=OPEN&cursor=...&limit=20`: `{items,nextCursor}`, max 50.
- GET `/requests/{id}`: Ticket below.
- POST `/requests/{id}/start`: `{expectedVersion}`.
- POST `/requests/{id}/link`: `{expectedVersion,conversationId}`.
- POST `/requests/{id}/episodes`: `{expectedVersion}`; open request plus a
  proven terminal previous runtime conversation creates an empty continuation:
  `openingMessageRequired:false`, `delivery:NOT_REQUIRED`. It never repeats the
  original subject/details. Desk initializes the new canonical control row and
  verifies owner history readback without AI before linking it. The caller reads
  canonical history directly; do not call `/start` for intake.
- POST `/requests/{id}/resolve`, `/cancel`, `/reopen`: `{expectedVersion}`.
- PUT `/requests/{id}/seen`: `{expectedVersion,seenThrough}`; trusted activity
  timestamp at or before current updatedAt, never ticket status.
- POST `/requests/{id}/activity`: `{expectedVersion}`, matching runtime
  credentials and ACTIVITY assertion permission; capability OBSERVE_ACTIVITY. Reads
  the owned canonical conversation's lastInteractionAt, not a browser timestamp,
  to refresh unread/activity without executing AI. Ordinary ticket reads remain
  runtime-independent. Until observed, unread describes known ticket activity,
  not immediate notification for every canonical chat turn.

Subject max 240, details max 12000; optional topic must be configured. Form save
precedes runtime delivery. Existing-conversation conversion requires runtime
ownership and never queues another opening message. The same applies to action
origin. `/start` on linked-only or delivered episodes does not call the runtime.
Private chat-first/action conversion requires the signed runtime binding above;
missing/mismatched claims fail before provider writes. Direct Supabase conversion
uses the verified provider subject. Both return NOT_REQUIRED without another
opening message. Form-first tickets retain their derived per-ticket principal.
The saved initial message key is stable across every retry/command key. Runtime
timeout remains UNKNOWN and cannot manufacture a successful AI receipt.

```json
{
  "id": "req-example",
  "reference": "SUP-EXAMPLE",
  "providerBindingId": "tpb-example",
  "runtimePrincipal": "support-ticket:req-example",
  "subject": "Guide not appearing",
  "details": "I connected our approved guide.",
  "topic": "knowledge",
  "status": "OPEN",
  "version": "0",
  "createdAt": "2026-11-03T12:00:00Z",
  "updatedAt": "2026-11-03T12:00:00Z",
  "upstreamStatus": null,
  "unread": true,
  "openingMessageRequired": true,
  "conversation": {"episodeId":"episode-example","conversationId":"support-example","delivery":"PENDING"},
  "episodes": [{"episodeId":"episode-example","conversationId":"support-example","number":1,"runtimeBaseUrl":"https://registered-runtime.example","openingMessageRequired":true,"delivery":"PENDING","createdAt":"2026-11-03T12:00:00Z"}],
  "events": [{"type":"CREATED","actorId":"requester-opaque","createdAt":"2026-11-03T12:00:00Z"}],
  "capabilities": ["CREATE","LIST","GET","RESOLVE","CANCEL","REOPEN","SEEN","LINK_CONVERSATION","OBSERVE_ACTIVITY","AGENT_LIST","AGENT_GET","AGENT_RESOLVE","PURGE"]
}
```

Status: OPEN/RESOLVED/CANCELLED. Delivery: PENDING/IN_FLIGHT/DELIVERED/UNKNOWN/
NOT_REQUIRED, independent from status and runtime human/AI authority. All
timestamps are ISO-8601. Nullable fields serialize as null. No transcript,
provider secret, agent email, diagnostic permission or invented presence.

## Agents And Purge

Existing operator authentication and session CSRF apply. Current SUPPORT is
required on the exact binding, not OWNER or REVIEWER alone:

- GET `/api/desk/v1/bindings/{bindingId}/tickets` (same query/page).
- GET `/api/desk/v1/bindings/{bindingId}/tickets/{id}`.
- POST `/api/desk/v1/bindings/{bindingId}/tickets/{id}/resolve`, `{expectedVersion}`.

Existing handover APIs remain the only human transcript/reply/control APIs.

Service-admin POST `/api/desk/admin/v1/bindings/{bindingId}/tickets/{id}/purge`
with Idempotency-Key and
`{expectedVersion,ownerIssuer,ownerSubject,testRecord,reason}`. The trusted
Console must already have performed its normal ownership gate; Desk also checks
that the named immutable identity is a current Desk OWNER. Exact closed test
records require explicit `testRecord:true` attestation; otherwise closed
retention must have elapsed. Pending/uncertain opening deliveries reject purge.
Private payload, events, episodes and old command receipts are transactionally
removed; a minimal ID/time tombstone and purge receipt remain. No runtime
conversation, shared binding, operator, knowledge source or S3 object is deleted.

## Integration Commands

POST `/api/desk/integrations/v1/spaces/{spaceId}/ticket-commands`, with distinct
`X-LoomAI-Ticket-Connector-Key`, original requester bearer authentication (plus
private server key for that profile). CREATE/LINK_CONVERSATION require a valid
Idempotency-Key; read-only GET/LIST do not. A key or arbitrary
trace header alone is never caller identity. Signed private assertions require
both `TICKET_COMMAND` and the requested operation in operations.

`CommandInput` exact fields: `{operation,requestId,expectedVersion,subject,details,topic,conversationId}`.
Supported operations CREATE, LIST, GET, LINK_CONVERSATION. CREATE requires an
already-persisted owned conversation; result is linked-only NOT_REQUIRED.
It requires the same runtime ownership credentials as chat-first conversion.
The governing action must enforce ordinary confirmed WRITE execution. No AI
resolve/cancel command is installed here. Trusted connector templates must not
expose caller credentials or scope as model parameters. Existing unsigned
TraceContext alone is not accepted as cryptographic requester proof.

## Provider Boundary

`TicketProvider` adapters enforce server Scope(spaceId,providerBindingId,
requesterId), upstream ownership, conditional writes and operation capabilities.
External versions retain actual opaque ETags. Unsupported operations fail;
uncertain upstream writes are not retried to another provider or mirrored into
native tables. External adapters must namespace IDs within a space across
provider bindings. Existing request mappings retain provider provenance after
selection changes. Only native Desk stores encrypted intake/status in Desk.
Vendor adapters and vendor live qualification remain deferred.
