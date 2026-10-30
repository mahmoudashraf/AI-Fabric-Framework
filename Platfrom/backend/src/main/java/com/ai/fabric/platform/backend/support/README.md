# Internal support registry and diagnostic facade (010.54)

This package is Platform's own support-case integration, not a ticket database
automatically installed into customer AI Support Workspace deployments.
It uses existing Platform PostgreSQL. Runtime owns canonical chat/control;
Desk owns human-channel delivery. No transcript, AI response, credentials, raw
configuration or provider payload is stored in this registry.

## Integration Status And Scope

Implemented independently: registry, requester authorization, lifecycle,
consent, runtime-episode correlation, diagnostic service authentication,
safe resource projections, Desk external context/resolve callback with actual
SUPPORT authorization and a minimal staff intake list. See
[Desk integration contract](DESK_EXTERNAL_CONTEXT.md). Security-chain
registration, runtime adapter, UI, configuration rollout, Desk provider
registration and hosted proof belong to the main integration stream.

Migration: `db/migration/V218__internal_support_case_registry.sql`.
Three tables: `platform_support_cases`, `platform_support_case_episodes`,
`platform_support_case_events`. The original form body/key and requester are
immutable; mutable context/status/consent use case-row locks and version checks.
Intake operation keys are unique per customer and original Platform user. The
existing user row serializes simultaneous duplicate intake, without a second
admission/capacity system.

## Frozen Private Runtime Adapter Contract

`SupportCaseService.requireOwnedCase(String caseId)` returns
`SupportContracts.OwnedCase`, with these accessors:

```text
caseId, reference, deploymentId, targetProfileId, customerId, tenantId,
principalId, conversationId, episodeId, episodeNumber, conversationTerminal,
initialMessageKey, initialMessageBody, initialMessageDelivery, status,
assertionIssuer, assertionAudience
```

`customerId`/`tenantId` are the fixed internal support runtime owner/boundary,
NOT the requesting customer's account. `principalId` is a random case-specific
opaque owner, stable across that case's episodes. Never serialize this server
DTO into the workspace manifest, including its delivery key or private assertion.
The adapter receives only `caseId` from the customer, and derives destination,
principal and conversation from this DTO. Resolve target endpoint and signing
material through the existing target-scoped runtime authority services.

Every call uses the current authenticated `PlatformSecurityContext`, an active
CUSTOMER_ADMIN local session or Supabase-linked identity. Original Platform user
ID/customer and captured issuer/subject must still match; email is not identity.
Other customer administrators, Platform API keys and global Platform admin do
not impersonate the requester. Supabase link revocation and removed/inactive
membership deny subsequent reads. An originally local identity can subsequently
link Supabase without losing its original Platform-user ownership.

Adapter hooks:

- `initialMessageForDelivery(caseId)` returns `InitialMessage(caseId, episodeId,
  conversationId, body, idempotencyKey, observation)`. Use the exact body/key for
  the canonical message route; no retry with an edited body or a new key.
- `observeInitialDelivery(caseId, episodeId, DeliveryStatus)` records actual
  response/readback evidence only. PENDING is initial state; UNKNOWN is uncertain
  delivery; USER_MESSAGE_PERSISTED is not AI completion. COMPLETED/FAILED are
  terminal observations. No response or transcript is saved here.
- `observeConversationTerminal(caseId, conversationId)` records only an
  authoritative runtime terminal observation, never a Desk badge guess.
- `requireConversationAccess(caseId, conversationId)` authorizes a pinned prior
  episode for read/history use. The browser must not freely select a foreign
  episode for mutation.

The adapter must distinguish history access from mutating message/handover
requests: only OPEN case + nonterminal current episode may accept new activity.
Resolve/cancel does not claim the runtime was closed. Explicit reopen reuses a
nonterminal conversation, or creates a new pinned episode if terminal was observed.
Episode >1 must **not** resend the original intake (the delivery hook refuses it).
Returning to AI or ending a human episode never resolves the case.

## Customer API

All routes under `/api/support/cases` use existing CUSTOMER_ADMIN authentication
and service-level immutable requester checks. No caller-supplied account,
requester, principal or runtime destination field is accepted.

- `POST /` with `Idempotency-Key`, `CreateCase` summary/details/topic/resources/
  diagnosticConsent. Summary required; resources optional (maximum eight).
- `GET /?page=0&size=25` and `GET /{caseId}`: requester-only views; no private DTO.
- `PATCH /{caseId}` with topic/resources/expectedRevision. Intake body stays
  unchanged; changing the resource set revokes the old consent grant.
- `PUT /{caseId}/consent` with granted/expectedVersion. Regrant requires OPEN and
  current resource ownership. Revoke takes effect before the command completes.
- `POST /{caseId}/resolve`, `/cancel`, `/reopen` with expectedRevision.

Cancel is the supported removal from active work, not data erasure. No hard-delete
endpoint is offered without the plan's cross-store retention/deletion policy;
deleting a registry row would not erase runtime/Desk/provider records.
Assistance/control badges are intentionally absent: read them from runtime/Desk.

## Configuration Owned By Main Integration

Default: disabled. These server properties are read via Spring Environment,
without edits to existing shared configuration classes:

```properties
platform.support.enabled=true
platform.support.customer-id=<internal-support-owner>
platform.support.tenant-id=<internal-support-tenant>
platform.support.deployment-id=<support-deployment>
platform.support.target-profile-id=<exact-target>
platform.support.assertion-issuer=<exact-private-assertion-issuer>
platform.support.assertion-audience=<exact-private-assertion-audience>
platform.support.diagnostics.service-key-secret-name=<dedicated-existing-secret-reference>
```

The configured deployment must exist, be unarchived and have that exact customer/
tenant; the target must exist. No live-health probe is required to persist intake.
New episodes capture the complete binding. Reconfiguration does not silently
reassign prior conversations: mismatched bindings are rejected explicitly.
No environment/global API-key fallback. The secret value is resolved only by
the existing `PlatformSecretService`, is server-only and must be provisioned
separately from runtime/admin/assertion credentials.

## Diagnostic Action Contract

Connector upstream:
`POST /api/internal/support/diagnostics/{action}`. No request body, customer or
resource parameters are required; only the case's selected resources are read.
Exact actions:

- `support_get_case_summary`
- `support_get_deployment_status`
- `support_get_engage_readiness`
- `support_get_connection_sync_status`

Dedicated credential: `X-LOOMAI-SUPPORT-SERVICE-KEY`. Forward the existing verified
connector headers, not browser-supplied lookalikes:

```text
X-AIFABRIC-AUTH-SUBJECT-ID = stored case principal
X-AIFABRIC-CONVERSATION-ID = stored current episode conversation, when present
X-AIFABRIC-AUTH-SUBJECT-TYPE = END_USER
X-AIFABRIC-AUTH-MODE = PRIVATE_RUNTIME_BACKEND_MEDIATED
X-AIFABRIC-AUTH-CALLER-TYPE = TRUSTED_BACKEND
X-AIFABRIC-AUTH-DEPLOYMENT-ID / CUSTOMER-ID / TENANT-ID = fixed support binding
X-AIFABRIC-AUTH-ISSUER / AUDIENCES = exact support assertion policy
X-AIFABRIC-AUTH-EXPIRES-AT = future ISO instant
X-AIFABRIC-AUTH-SCOPES includes support:diagnostics:read
```

Missing required, duplicate, expired or mismatched fields deny access, even if a valid
customer/admin session is also present. This is a trust boundary, not an AI
semantic validator. Main must register only this exact service path in the
security/CSRF chain; do not exempt all internal APIs or remove its own credential
verification. The connector possessing this credential is trusted to forward
runtime-verified context. Never put this credential into the browser or allow a
client header to replace the connector's trusted value.

After service authentication, the facade resolves the registered principal against
the registry's fixed binding, requires the current nonterminal OPEN episode,
rechecks original membership and *all* selected resources, and requires current
consent. Case lock serializes projection with consent/lifecycle changes. No LLM
parameter or requesting customer's tenant substitutes the fixed support boundary.

### Conversation-Isolated Nested Diagnostic Reads

Official conversation-disabled declarative specialists preserve the original
verified principal/customer/tenant/issuer/audiences through
`TrustedExecutionContext.callerIdentity`, but intentionally have no conversation
binding. Their connector trace can therefore omit `X-AIFABRIC-CONVERSATION-ID`.
On this **dedicated authenticated service path only**, an absent conversation ID
resolves the exact current episode from the opaque registered case principal.
The case, principal and latest episode are not chosen by model parameters.
If a conversation ID is supplied, it must equal that exact registered active
episode. Blank/duplicate IDs are not absence. Fixed support binding, original
requester membership, OPEN/nonterminal episode, selected-resource ownership,
expiry, diagnostic scope and current consent are identical on both paths.

This does not create a child conversation, change source/auth identity or bypass
framework conversation policy. Parent execution checks and atomic final response
acceptance remain the runtime adapter's responsibility. A previously authorized
read may finish; its result must not publish a stale AI answer after human takeover.

Responses include action, observedAt and explicit CURRENT/INCOMPLETE/UNAVAILABLE/
NOT_APPLICABLE. They contain only whitelisted resource IDs, coarse statuses and
timestamps. An absent selected resource is NOT_APPLICABLE, not healthy. Deployment
release attempts remain distinct and never imply the latest attempt is serving.
Engage uses existing **local** readiness: no route provisioning/provider probe,
no raw check messages, and delivery readiness remains INCOMPLETE until explicitly
probed through its normal workflow. Connection support currently covers existing
vectorization source connections/plans/run observations, not every external
integration kind. Unknown status values are disclosed as UNKNOWN, not guessed.

No text matching, intent/source classifier, model repair or AI fallback is added.

## Verification And File Inventory

Verified 2026-10-30, from `Platfrom/backend`:

```sh
mvn -q \
  -Dtest=SupportCaseServiceTest,SupportDiagnosticServiceTest,SupportResourceServiceTest,SupportCaseControllerTest,SupportCasePostgresTest,SupportDeskContextServiceTest,SupportDeskContextControllerTest,SupportDeskPostgresTest,SupportDeskSecurityFilterTest \
  test
```

58 tests passed, zero failures/errors/skips: registry/lifecycle/identity (11),
diagnostic authority including conversation-isolated reads (20), safe resource
projections (3), HTTP intake/lifecycle (2), PostgreSQL migration/concurrent intake/
consent serialization (1), Desk context/authority/receipt handling (14), Desk
HTTP context/resolve/staff intake (1), concurrent PostgreSQL Desk resolve (1),
real Platform root security/filter integration (5).
H2 exercises the same migrations for unit fixtures; PostgreSQL tests use
ephemeral PostgreSQL 16 containers. Configured runtime
FAILED with no URL still persists OPEN/PENDING intake without a runtime probe.
No hosted rollout or private runtime/Desk integration proof is claimed here.

The real-filter fixture imports `PlatformSecurityConfiguration`, uses its actual
customer bearer/session/API-key filters and the real support controller,
authorization and Supabase identity-verification services. Only external JWT
decoding/user verification and Desk permission lookup are mocked. It proves
Desk-only access without Console membership, denial without actual JWT/current
SUPPORT, no admin-cookie/API-key fallback, and protection of adjacent paths.
Main owns the exact global route/filter registration; this package did not edit it.

Do not treat `Support*Test` as this bounded suite: it also selects main-owned
`support.runtime` work. The final bounded rerun on 2026-10-30 passed the explicit
58-test selection above. Main coordinates the broader integrated suite and its
runtime fixes; those results are not substituted for this package's proof.

New main package files:

```text
support/SupportContracts.java
support/SupportPolicy.java
support/SupportSettings.java
support/SupportRequesterService.java
support/SupportResourceService.java
support/SupportCaseStore.java
support/SupportCaseService.java
support/SupportDiagnosticAuthentication.java
support/SupportDiagnosticService.java
support/SupportCaseController.java
support/SupportDiagnosticController.java
support/SupportDeskContracts.java
support/SupportDeskAuthorization.java
support/SupportDeskContextService.java
support/SupportDeskContextController.java
support/SupportDeskStore.java
support/DESK_EXTERNAL_CONTEXT.md
support/README.md
```

New test package files:

```text
support/SupportFixture.java
support/SupportCaseServiceTest.java
support/SupportDiagnosticServiceTest.java
support/SupportResourceServiceTest.java
support/SupportCaseControllerTest.java
support/SupportCasePostgresTest.java
support/SupportDeskFixture.java
support/SupportDeskContextServiceTest.java
support/SupportDeskContextControllerTest.java
support/SupportDeskPostgresTest.java
support/SupportDeskSecurityFilterTest.java
```

Migrations: `V218__internal_support_case_registry.sql` and
`V219__support_desk_context_receipts.sql`. No existing
security/configuration/controller/service, UI or shared compiler file was edited
by this implementation. The separate `support.runtime` package is reserved for
the main integration stream's private adapter.
