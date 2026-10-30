# Platform's Desk Support Context Integration

Implements the service-provider side of
`product-services/loomai-desk-service/EXTERNAL_CONVERSATION_CONTEXT.md`.
Only Platform's internal support cases use this registry. This is not a
requirement that third-party support deployments adopt a LoomAI ticket database.

## Endpoints And Root Security Registration

Exact callback: **POST `/api/integrations/support/desk/context`**. Both LOOKUP
and RESOLVE use this single endpoint and the official schemaVersion=1 envelope.
No GET lookup alias, generic action executor or customer-chosen callback URL.

Minimal staff registry view: **GET `/api/support/staff/intake?page=0&size=25`**,
maximum 50 entries per page. It shows OPEN requests whose first AI response has
not completed: PENDING/UNKNOWN/USER_MESSAGE_PERSISTED/FAILED. These are intake
records, not a second human messenger or a promise of staff notification.

For the main integration stream, register only these exact method/path pairs:

- Let POST `/api/integrations/support/desk/context` reach its own service-key
  **and actual Supabase agent JWT** checks. Exempt only that server callback
  from browser CSRF; never exempt `/api/support/**` wholesale.
- Let GET `/api/support/staff/intake` reach its own actual Supabase agent JWT
  and current SUPPORT checks. It is a read and needs no CSRF exemption.
- The ordinary Console customer bearer filter must not first require a Desk
  agent to have a linked CUSTOMER_ADMIN Console account on these two routes.
  Do not disable JWT validation: `SupportDeskAuthorization` explicitly uses
  `CustomerSupabaseIdentityService.verifyIdentity`, not Console membership
  authentication, and verifies expected issuer/audience/expiry/actor.
- These handlers must not rely on a Platform admin key, customer cookie,
  arbitrary `PlatformPrincipal`, Desk OWNER or REVIEWER role. Their independent
  authorization runs even if global auth already accepted a request.
- Existing customer `/api/support/cases` and private `support.runtime` routes
  retain their existing customer/requester authentication and mutation rules.

Success projections have `Cache-Control: no-store`. Main registered these exact
route/filter exceptions in the shared security configuration. This package's
`SupportDeskSecurityFilterTest` verifies them with the real filter chain; it does
not modify shared security or introduce an all-internal-path exemption. Existing
global CSRF policy was not changed by this package.

## Fixed Source Authority And Settings

Existing `platform.support` customer/tenant/deployment/target/assertion settings
remain the source of authority. They must match the canonical deployment row;
the target must exist. Unresolved placeholders, a customer-selected tenant or
foreign deployment ownership are not usable support configuration. None of this
adds a live runtime-health requirement to case intake.

Additional server settings:

```properties
platform.support.desk.binding-id=<exact-internal-support-desk-binding>
platform.support.desk.context-service-key-secret-name=<dedicated-server-secret-reference>
platform.support.desk.resolve-enabled=true
```

Resolve defaults to false. Register the provider in Desk with the same reviewed
resolve setting and exact callback URL. The context credential must be 32-4096
characters and distinct in reference **and value** from the diagnostic credential.
It also cannot be Desk's global administration credential. Resolve it through
the existing Platform secret service, never through a browser or case field.

The configured `DeskBindingEntity` must match that fixed internal support
customer, tenant, deployment and target. Local binding state must be ENABLED or
APPLYING; the remote current-role introspection additionally requires Desk's
actual enabled binding and registered SUPPORT assignment. Target environment
selects the existing staging/production `DeskServiceClient` connection. No
request URL/environment or browser secret selects the upstream Desk service.

## Callback Authentication And Correlation

Required headers:

```http
Authorization: Bearer <actual Desk agent Supabase access token>
X-LoomAI-Desk-Service-Key: <dedicated context credential>
X-LoomAI-Desk-Binding-Id: <fixed configured binding>
```

Missing, duplicate or mismatched credentials are rejected. Validate the actual
token through the existing identity verifier and match JWT issuer/subject to
the envelope's `actor`. Check the configured audience and current expiry.
`agentId` is bounded, Desk-service-authenticated correlation metadata, not an
authorization claim. Current existing introspection returns only a boolean,
so the provider's authoritative staff audit identity is verified issuer/subject;
it does not pretend to independently resolve `agentId` from that boolean.

For every request, using existing server-only administration trust:

```http
GET /api/desk/admin/v1/bindings/{fixedBindingId}/authorizations?issuer=...&subject=...&permission=SUPPORT
```

Only boolean `authorized:true` grants access. This implementation uses the
existing class named `DeskServiceClient` (there is no separate DeskAdminClient
in current Platform source). No global Desk administration key is returned to
Desk agents, customer providers, the workspace or the callback response.

The callback scope's binding/customer/tenant/deployment/target must all equal
fixed source authority. Resolve `ownerRef` using the registered opaque case
principal and require the exact registered `conversationId` episode/binding.
The handover/external reference are trusted Desk correlation fields, not case
selection, requester identity or runtime-control authority. An unlinked
principal/conversation returns NOT_LINKED with null context and the **same exact
scope**, never another case. No parsing of intake/contact fields or message text.

Recheck the original immutable requester's active membership and current
selected-resource ownership. With declined/revoked consent, basic case context
remains available to the authorized staff member, but no resource diagnostic
observations run. When consent is active, only the existing safe projections
are used. No raw logs/configuration/provider payload or runtime assertions enter
the projection. Oversized or unrepresentable context fails visibly rather than
being silently truncated.

Staff intake uses the same current SUPPORT gate and original requester/resource
checks, but requires **only the actual agent Bearer token**, not a browser-held
context secret. It never returns the case's runtime principal or initial-message
idempotency key. An authorization failure is explicit, not a silently omitted
request or a downgraded public support session.

## Resolve Is A Case Mutation Only

RESOLVE requires `command.idempotencyKey` and `command.expectedVersion`, an
enabled provider setting, current SUPPORT, exact scope and an OPEN case. Version
is the same revision supplied in the context projection. The case row is locked;
version check, consent revocation, RESOLVED status, lifecycle audit and receipt
are committed atomically. A failed projection or receipt write rolls all changes
back. It does not call Desk close, return-to-AI, runtime conversation control or
any customer infrastructure write.

Receipt identity is SHA-256 over the binding/conversation/verified actor/
operation/idempotency-key tuple. The full schema/scope/actor/command payload is
fingerprinted separately. An exact authorized retry returns its original typed
response; changed payload under the same key is CONFLICT. A changed actor cannot
reuse another actor's receipt. Live SUPPORT and original membership/resource
checks also run for a retry. Previously referenced resources are rechecked
before replay, so the receipt does not bypass later ownership revocation.

Migration `V219__support_desk_context_receipts.sql` adds
`platform_support_desk_operations` to existing Platform PostgreSQL. LOOKUP audits
contain only correlated case/binding/conversation/handover and actual staff
identity metadata, with no context payload. RESOLVE additionally stores the safe
typed response and resource references for idempotent replay. It stores neither
JWT/service credentials nor chat history. The existing lifecycle event uses no
fake Platform requester ID for a Desk agent; the new audit has its actual
verified issuer/subject and Desk-supplied agent reference.

## Verification

The explicit package test command in [README.md](README.md) passed **58 tests**,
no failures/errors/skips (37 registry/diagnostic tests plus 16 Desk tests and 5
real-filter security tests). Includes PostgreSQL 16
concurrent resolve: one receipt, one case transition, identical response for
both requests and no runtime conversation change. Other coverage includes
missing/duplicate credentials, JWT issuer/subject/audience/expiry mismatch,
revoked SUPPORT on receipt replay, foreign scope/binding, consent-aware reads,
placeholder configuration, stale-version/payload conflicts, full transaction
rollback and actual-Bearer staff intake. This is local verification, not hosted
Desk provider registration or end-to-end UI proof.

The five real-filter tests use the actual `PlatformSecurityConfiguration` and
`CustomerSupabaseAuthenticationFilter`, real support authorization/controller and
real Supabase identity-verification service. A Desk-only JWT works without a
Console identity link; invalid/expired JWTs, service keys/admin credentials alone
and revoked SUPPORT do not. Adjacent internal/customer routes remain protected.

The broad `Support*Test` selector also includes main-owned private runtime tests.
The final bounded rerun on 2026-10-30 passed all 58 tests listed above. Broader
integrated-suite verification and runtime fixes remain coordinated by main;
this package proof does not claim that every support/runtime release gate passed.
