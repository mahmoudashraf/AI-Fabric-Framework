# Console's Read-Only Desk Context Adapter (010.57)

This is optional Console resource/consent enrichment beside Desk's native Ticket
and canonical conversation views. It is **not ticket storage, a ticket resolver,
an operator inbox or a chat transport**. This document describes source behavior;
provider registration and hosted qualification are separate work.

The broader
[external-context protocol](../../../../../../../../../../../product-services/loomai-desk-service/EXTERNAL_CONVERSATION_CONTEXT.md)
can support other providers' resolve operations. **This Console adapter supports
only `LOOKUP`**, with `command:null`. Do not advertise its resolve capability.
`SupportDeskContextService` rejects RESOLVE before provider mutation, always
returns `resolveAction:null`, and has no local ticket-resolution receipt store.

## Exact Callback And Authentication

```http
POST /api/integrations/support/desk/context
Authorization: Bearer <actual-agent-Supabase-access-token>
X-LoomAI-Desk-Service-Key: <dedicated-context-credential>
X-LoomAI-Desk-Binding-Id: <configured-support-binding>
Content-Type: application/json
```

The body is the existing schema-version-1 context envelope: `operation:"LOOKUP"`,
the Desk-generated `scope`, verified `actor`, and `command:null`.
There is no GET callback alias or current Platform staff-intake route. Agents
use Desk's own authorized ticket list/detail and conversation UI instead.

The root security chain lets this exact callback reach its own authentication.
The ordinary customer Supabase filter does not first require an agent to have a
Console customer account. This exception does **not** open neighboring internal
or customer routes. Preserve the existing host/CSRF policy; do not exempt all
support routes or accept an admin session as callback identity.

`SupportDeskAuthorization.callback` requires all of the following:

- One matching context credential and binding header. The context key must be
  32-4096 characters and distinct in reference and value from the diagnostic
  credential, and cannot be the selected Desk service's administration key.
- A fixed configured support binding whose customer/tenant/deployment/target
  match the callback scope. Its local state must be ENABLED or APPLYING and its
  target active; the target selects the server-side Desk connection.
- The actual agent JWT verified through `CustomerSupabaseIdentityService`, with
  expected issuer/audience, unexpired lifetime and immutable subject matching the
  asserted actor. `agentId` is authenticated correlation metadata, not a role or
  permission claim.
- A fresh existing Desk administration-side authorization lookup:
  `GET /api/desk/admin/v1/bindings/{bindingId}/authorizations?issuer=...&subject=...&permission=SUPPORT`.
  Only `authorized:true` grants access. OWNER/REVIEWER, agent registration,
  service keys alone and customer/admin cookies do not substitute for SUPPORT.

Desk's administration credential stays server-side. No model field, browser URL
or request environment selects the Desk service or support runtime.

## Correlation And Data Ownership

`SupportIntegrationContextStore` maps the exact configured space and verified
runtime principal (`scope.ownerRef`) to a provider ticket and original Console
requester. It stores resource authority/consent only. It does not keep ticket
subject/details/status, conversation episodes or a transcript.

For each authorized lookup, the service:

1. Finds that principal's unique integration context in the configured space.
2. Fetches the actual Ticket through `SupportTicketService`/the configured
   provider, rechecking the original requester's active membership.
3. Requires the callback conversation to equal the Ticket's **current** linked
   conversation. No principal mapping or a different conversation returns
   `NOT_LINKED` with null context and the unchanged scope, never another ticket.
4. Rechecks ownership of all selected resource references.
5. Returns safe context based on the current provider Ticket and local consent.
   Only OPEN tickets with current consent receive recorded resource observations.

The returned context contains provider reference/subject/status, account label,
selected-resource labels, consent state/timestamp, coarse observations and
`resolveAction:null`. Its numeric `version` is the **integration-context revision**,
not the TicketProvider's opaque Ticket version and not a resolution token.
No raw logs, configuration, credentials or runtime assertion is included.

Declined/revoked consent still permits basic ticket context to an authorized
SUPPORT agent; it does not permit resource diagnostic observations. Authorized
observations explicitly state that they are recorded status and that upstream
services have not been probed. LOOKUP does not change ticket status, consent,
runtime AI/human control or the customer's infrastructure. Responses are no-store.

## Resolution, Roster And Conversation Control Are Separate

Native ticket resolution uses Desk's existing
`POST /api/desk/v1/bindings/{bindingId}/tickets/{id}/resolve`, with the actual
agent session/current SUPPORT, `Idempotency-Key` and opaque `{expectedVersion}`.
Requester resolve/cancel/reopen uses the provider-neutral requester routes
described in [README.md](README.md). There is no Console context RESOLVE path or
second local resolution transaction.

Ticket resolution does not return a conversation to AI, end a human session or
close canonical history. Those remain explicit authorized runtime/channel
operations. Ticket history and state come from Desk's selected TicketProvider;
messages/control come from the assigned deployment runtime.

The requester-facing assigned-agent roster is independently served by
`GET /api/support/team` and its authenticated photo route. It uses current
target-scoped SUPPORT assignments, not context LOOKUP or ticket consent. A name
or photo denotes an assigned agent, not online availability or response time.

## Configuration And Retirement

Use the fixed owner/tenant/deployment/target/assertion/installation settings in
[README.md](README.md), plus:

```properties
platform.support.desk.binding-id=<exact-support-binding>
platform.support.desk.context-service-key-secret-name=<dedicated-context-key-reference>
```

Register the optional context provider in Desk through its existing service-admin
API using the exact callback URL and **`resolveEnabled:false`**. Its origin must
pass Desk's existing context-origin policy. This is distinct from preparing the
native Support space, which uses `POST /api/platform/support/prepare` and its own
requester identity/server/connector credentials. Do not place any of these keys
in agent or requester browser configuration.

V227 removes the old Console ticket/episode/event/Desk-operation tables without
migrating their disposable test records. V226 retains only resource-consent and
pre-ticket conversation authority mappings. The callback has no compatibility
ticket reader or local status writer after this cutover.

## Source Coverage

- `SupportDeskContextServiceTest`: actual provider lookup, missing/foreign
  correlation, current requester/resource ownership and consent; rejects RESOLVE
  without mutating the provider or local consent.
- `SupportDeskSecurityFilterTest`: real root-filter boundaries, Desk-only agent
  identity without Console membership, dedicated credential/current SUPPORT,
  expired/invalid tokens and no admin-cookie/API-key substitution.
- `SupportContextPostgresTest`: minimal authority/consent serialization and
  removal of disposable legacy tables without deleting knowledge or AI state.
- `SupportTeamSecurityTest` and `SupportTeamControllerTest`: independently
  protected assigned-agent/portrait projection.

These source tests do not prove live provider registration, staffed handover or
hosted response quality. No previous test count or rollout status is carried
forward as current qualification evidence.
