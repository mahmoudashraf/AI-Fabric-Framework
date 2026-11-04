# Console Support Host Adapter (010.57)

This package adapts the signed-in LoomAI Console to the reusable Support
Experience Kit. It is **not a second ticket system**. This guide describes current
source contracts, not a completed hosted rollout or live answer-quality result.

```text
Console Support pack -> /api/support -> Desk requester facade -> TicketProvider
                                   -> scoped canonical runtime APIs
Console resource selection/consent -> minimal Platform integration context
Desk agent -> optional read-only Console context callback
```

## Ownership And Storage

- Desk's `TicketProvider` is the business authority for ticket intake, reference,
  status, opaque version, requester reads and lifecycle transitions. The native
  implementation is `DeskTicketProvider` (`id: desk`) in the shared Desk service.
  An external adapter must implement and qualify that contract; Platform never
  mirrors its payload or falls back to a local ticket on provider failure.
- Desk's ticket facade coordinates provider receipts, conversation links,
  episodes and opening delivery. Platform forwards commands; it does not keep
  another delivery queue or replay the original intake itself.
- The assigned deployment runtime owns canonical messages, completed turns and
  AI/human control. Ticket status and human conversation state are independent.
- `SupportIntegrationContextStore` stores only `(support_space_id, ticket_id)`,
  immutable Console requester identity, the verified runtime principal, selected
  resource references, consent/version, revision and timestamps. No subject,
  details, ticket status, transcript, AI response or provider response is stored.
- `SupportConversationScopeService` stores the private requester/principal/
  conversation linkage before a user chooses to save a ticket. This contains no
  messages, ticket lifecycle or diagnostic consent.
- Existing document storage connections, indexed evidence and deployment AI
  state remain separate. This package does not copy their contents into Console.

[V226](../../../../../../../resources/db/migration/V226__console_support_integration_context.sql)
creates the two minimal authority tables above.
[V227](../../../../../../../resources/db/migration/V227__retire_console_support_ticket_storage.sql)
drops `platform_support_desk_operations`, `platform_support_case_events`,
`platform_support_case_episodes` and `platform_support_cases`. This is the
authorized greenfield retirement of disposable Console test tickets: **no legacy
ticket migration, compatibility writer or dual read/write path**. Historical
migrations remain in place; document connections and AI state are not dropped.

## Console Requester API

These routes require `CUSTOMER_ADMIN` plus `SupportRequesterService`'s current,
active Console requester checks. Console membership is specific to this host;
the generic Support pack and direct Desk requester API do not require it.
Identity is customer/user plus the captured linked issuer/subject, never email.
Another administrator in the same customer is not automatically the requester.
Revoked links, inactive membership and API-key impersonation are denied.

`SupportTicketController` exposes:

- `GET /api/support/session`: authenticated provider session/capabilities.
- `GET /api/support/requests?status=OPEN&cursor=...&limit=20`: own-request page;
  `status` and cursor are optional, limit is 1-50.
- `POST /api/support/requests`: `{subject, details, topic?, existingConversationId?}`
  with `Idempotency-Key`. A supplied conversation is resolved from the current
  requester's server-owned scope and verified before it can be linked.
- `GET /api/support/requests/{id}`: provider-owned Ticket projection.
- `POST /api/support/requests/{id}/resolve`, `/cancel`, `/reopen`:
  `{expectedVersion}` with `Idempotency-Key`.
- `PUT /api/support/requests/{id}/seen`:
  `{expectedVersion, seenThrough}` with `Idempotency-Key`.

Ticket `version` and command `expectedVersion` are **opaque strings**. Forward
the provider value unchanged, for example `{"expectedVersion":"vendor-etag"}`.
The provider owns idempotency and transition rules. Provider failure/uncertainty
is visible; it does not create a substitute local ticket or imply AI completion.
Save does not require a live AI response. Start/delivery is a separate operation.

## Selected Resources And Diagnostic Consent

Console-only resource authority is separate from generic ticket intake:

- `GET /api/support/requests/{id}/context` returns
  `{ticketId, resources, consent, revision}`.
- `PUT /api/support/requests/{id}/context` accepts
  `{resources:[{type,id}], expectedRevision}`. At most eight resources, of type
  `DEPLOYMENT`, `ENGAGE_APP` or `CONNECTION`; current ownership is checked.
- `PUT /api/support/requests/{id}/context/consent` accepts
  `{granted, expectedVersion}`. Here `expectedVersion` is the **numeric consent
  version**, not the Ticket's opaque version.

Resource edits require an OPEN provider ticket and current context revision.
Changing the resource set revokes prior consent. Granting consent rechecks OPEN
status and all resource ownership; revocation is serialized by the context lock.
A successful requester lifecycle transition also revokes existing consent; a
failed provider mutation does not report a local success. Reopen does not
silently regrant access. Ordinary knowledge help needs no diagnostic consent.

## Private Identity And Provisioning

`SupportTicketClient` calls only the server-selected target environment's Desk
requester root, `/api/desk/requester/v1/spaces/{configuredBindingId}`. The browser
does not choose its origin, space, requester, private runtime principal or keys.

`SupportRequesterAssertionService` issues an RS256 requester assertion lasting
120 seconds, with `iss`, `aud`, opaque stable `sub`, `iat`, `exp`, `jti`, `spaceId`
and permitted operations. Its subject is derived from Console customer/user IDs,
not email. Verified chat-first linking additionally carries the exact server-owned
`runtimePrincipal` and `runtimeConversationId` claims.

Server-to-server Desk calls use:

```http
Authorization: Bearer <short-lived-requester-assertion>
X-LoomAI-Requester-Server-Key: <distinct-per-binding-server-key>
```

These are not the private runtime credentials. When start/link/episode/activity
needs runtime proof, the BFF obtains exact target authority and additionally
sends `X-LoomAI-Runtime-Authorization` and `X-LoomAI-Runtime-Api-Key` to Desk.
Neither assertion nor trusted runtime key enters the browser or manifest.

`POST /api/platform/support/prepare` is an explicit administrator/operator
provisioning operation. `SupportSpaceProvisioningService` checks the configured
owned installation and assignment, prepares distinct signing/server/connector
keys, and registers the native Desk space with `TRUSTED_PRIVATE_HOST` identity,
the actual runtime endpoint and configured topic codes. It uses the existing
remote opaque space version on updates. Public keys alone are exposed by
`GET /api/public/support/requester-jwks`; GET does not prepare secrets.

The private profile is `platform-support-case-host@1.0.0` with
`BACKEND_MEDIATED_PRIVATE_RUNTIME`. The profile code is an implementation
identifier, not evidence of a legacy ticket store.

Server configuration, disabled unless explicitly enabled:

```properties
platform.support.enabled=true
platform.support.customer-id=<support-deployment-owner>
platform.support.tenant-id=<support-deployment-tenant>
platform.support.deployment-id=<owned-support-deployment>
platform.support.target-profile-id=<exact-target>
platform.support.assertion-issuer=<private-runtime-issuer>
platform.support.assertion-audience=<private-runtime-audience>
platform.support.installation-id=<owned-support-installation>
platform.support.desk.binding-id=<exact-target-desk-binding>
platform.support.diagnostics.service-key-secret-name=<dedicated-diagnostic-secret-reference>
platform.support.desk.context-service-key-secret-name=<distinct-context-secret-reference>
LOOMAI_DESK_REVIEW_BRIDGE_BASE_URL=https://<registered-platform-api-origin>
```

The support deployment owner/tenant are the fixed internal runtime boundary,
not the requesting customer's account. Exact assignment, immutable release
authority and target credentials are checked before runtime access. No global
key or caller-selected environment substitutes for them.

## Canonical Chat And Ticket Delivery

`SupportRuntimeAccessService` returns a trusted bootstrap containing
`conversationId`, `principalNamespace`, `apiBaseUrl`, installation/assignment
references, integrity-checked asset references, safe configuration and handover
projection. It never serializes private access headers.

- `GET /api/support/workspace` allocates an owned pre-ticket conversation scope;
  no ticket, opening message or AI query is created.
- `GET /api/support/requests/{id}/workspace` selects that owned provider Ticket's
  current episode; reading bootstrap does not resend intake.
- `POST /api/support/requests/{id}/start`, `/episodes`, `/activity` accept opaque
  `{expectedVersion}` plus `Idempotency-Key` and delegate to Desk.

Use the returned `apiBaseUrl`, not an invented direct runtime URL. It is either
`/api/support/conversations/{scopeId}/runtime/api` or
`/api/support/requests/{id}/runtime/api`. Beneath it the host allows only:

- GET `chat/me/auth-context`, `chat/me/shell-config`,
  `chat/me/conversations/{conversationId}` and its `/control` read.
- POST `chat/me/suggestions` and the bound conversation's `/messages`,
  `/handover-requests`, `/ai-resume-requests`, and explicit handover cancel/retry.
- The bound conversation's GET `/events` uses the dedicated streaming handler.

Messages and control mutations retain their idempotency key; suggestions do not
require a message key. Message input is limited to query/mode/position,
attachments/context and action draft submission, with the conversation ID fixed
server-side. This is not a general proxy or a legacy query/execute endpoint.
Human operations still require the runtime's configured handover contract.

Form-first start uses Desk's stable opening once. Chat-first conversion adopts
the verified existing conversation without re-enqueueing it. A terminal latest
conversation can receive an explicit empty continuation through `/episodes`;
there is no synthetic user message. Empty casual/continuation history is
initialized through canonical control before item read. Historical item/control
reads are selected only from the owned provider episode list and are read-only;
new writes remain latest/OPEN and subject to actual runtime control. SSE transport
is bounded and rechecks requester membership while forwarding.

## Governed Ticket And Diagnostic Commands

`POST /api/integrations/support/ticket-commands` is the Console instance's
connector endpoint, not a customer/browser executor. It accepts `CREATE`, `LIST`
and `GET` with `Idempotency-Key`. Dedicated
`X-LoomAI-Ticket-Connector-Key` plus immutable runtime-generated caller headers
must match the fixed deployment/tenant/customer/issuer/audience/expiry policy.
Required scopes are `support:tickets:create` or `support:tickets:read`.
The controller derives the original requester and exact current conversation
from the verified principal/context; model fields cannot select either.
Create uses the same provider path and confirmation-capable ticket ACTION, never
a second ticket implementation. Only a real provider receipt establishes success.

`POST /api/internal/support/diagnostics/{action}` supports exactly:
`support_get_case_summary`, `support_get_deployment_status`,
`support_get_engage_readiness`, `support_get_connection_sync_status`.
These names are current registered tools; the summary name does not imply local
case persistence. The dedicated `X-LOOMAI-SUPPORT-SERVICE-KEY`, trusted caller
headers and `support:diagnostics:read` scope are required. No model-supplied
resource/owner parameters select authority.

Diagnostics require the linked provider Ticket to be OPEN, active original
membership, current ownership of every selected resource and current consent.
A supplied conversation must equal the current provider episode. For a
conversation-disabled specialist trace, an absent conversation ID may resolve
only through the unique verified principal mapping, never the latest ticket or
text matching. Results distinguish CURRENT/INCOMPLETE/UNAVAILABLE/NOT_APPLICABLE;
recorded status is not a successful live infrastructure probe. No credentials,
raw configuration or arbitrary provider payload is projected.

## Roster And Desk Context

`GET /api/support/team` and `GET /api/support/team/{agentReference}/photo` expose
only the configured target's current SUPPORT roster to an active Console
requester. Team JSON is `{agents:[{agentReference,displayName,photoAvailable}]}`;
photos are authenticated private JPEG responses. Assignment is not online
presence, response-time assurance or a ticket status. The Console UI adapts this
host-specific shape to the generic pack's `SupportAgent[]` and disposes photo
blob URLs on unmount/account change. Roster access grants no diagnostic consent.

The separate [Desk context callback](DESK_EXTERNAL_CONTEXT.md) supplies optional
read-only resource enrichment to authorized agents. Ticket resolution uses the
TicketProvider, not this callback. Shared pack/BFF integration is documented in
[Console host adapter guide](../../../../../../../../../../../experience-packs/support-experience/docs/CONSOLE_HOST_ADAPTER.md).
The authoritative requester/provider wire contract is
[Support Tickets API](../../../../../../../../../../../product-services/loomai-desk-service/SUPPORT_TICKETS_API.md).

## Source And Verification Map

Relevant tests in `src/test/java/com/ai/fabric/platform/backend/support`:

- `SupportTicketServiceTest`, `SupportTicketControllerTest`,
  `SupportTicketClientTest`: delegation, requester isolation, opaque versions,
  provider uncertainty and retirement of the old HTTP ticket routes.
- `SupportRequesterAssertionServiceTest`, `SupportTicketCommandControllerTest`:
  distinct private identity/proof, trusted chat-first conversion and no identity
  selection from command input.
- `SupportIntegrationContextStoreTest`, `SupportContextPostgresTest`:
  minimal storage, concurrency/consent serialization and V227 retirement without
  deleting document connections or AI state.
- `SupportDeskContextServiceTest`, `SupportDeskSecurityFilterTest`,
  `SupportTeamSecurityTest`: read-only agent enrichment, real root security,
  current SUPPORT and roster isolation.
- `runtime/SupportRuntimeAccessServiceTest`, `runtime/SupportWorkspaceServiceTest`:
  exact assignment, canonical routes, empty initialization, historical reads,
  latest-only writes and no intake replay.

These are current test locations and responsibilities, not a fresh test-run
count. Hosted lifecycle, direct requester, AI quality and Console cutover evidence
must be checked separately; this guide makes no live-completion claim.
