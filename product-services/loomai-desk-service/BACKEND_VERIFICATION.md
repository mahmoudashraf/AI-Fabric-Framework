# 010.48 Backend Foundation Verification

## 010.57 Bounded Ticket Email Notifications (2026-11-04)

Desk-owned source, contracts, configuration and tests only. No commit, push,
deployment, framework change, external SMTP change or Console/pack/UI edit was
made by this task. Main owns hosted rollout and personal-agent qualification.

Final local verification passed **20 tests, zero failures/errors/skips**, with
executable Spring Boot JAR packaging. The database cases used H2 and a dedicated
PostgreSQL **16.15** container, not an H2-only PostgreSQL compatibility claim.
The preceding affected regression run passed **72 tests, zero failures/errors/
skips**, covering existing requester HTTP, ticket lifecycle, migrations, endpoint
policy and channel HTTP flows. That regression preceded the final isolated SMTP
scheduling adjustment; the final 20-test run verifies the final notification tree
including its new scheduler check and visible-failure precedence.

```sh
mvn -q \
  -Ddesk.test.postgres.url=jdbc:postgresql://127.0.0.1:<test-port>/desk \
  -Ddesk.test.postgres.required=true \
  -Dtest=SupportTicketNotificationsTest,SupportTicketNotificationHttpTest,SupportTicketNotificationSmtpTest \
  verify
```

- `SupportTicketNotificationsTest`: 15 cases. Future-only explicit consent,
  encrypted recipient, idempotent preference replay, requester/space isolation,
  disable cancellation, all four status transitions, exact native ticket and
  binding/conversation/principal correlation, bounded batch/backlog, atomic
  overlapping workers, four-attempt retry/dead-lease handling, current binding/
  space/origin checks, exact ticket purge and retained ticket state on failure.
  A real local HTTP callback peer returns 503 then a normal acknowledged
  `controlVersion`; no human email is queued before normal worker completion.
  The H2 scheduling case blocks SMTP while 100 poll invocations return promptly,
  proving one isolated mail job and no shared callback-scheduler blockage.
- `SupportTicketNotificationHttpTest`: four H2/PostgreSQL cases using signed
  JWTs and real HTTP. Private NOTIFICATIONS permission and signed top-level email
  proof; forged user metadata, arbitrary recipient fields and unverified email
  rejected. Direct Supabase notifications use the exact configured project and
  original token at `/auth/v1/user`; mismatched subject/anonymous/unverified user
  denied. Unsupported email issuers retain normal ticket-session access.
- `SupportTicketNotificationSmtpTest`: a real local SMTP capture returns 451 on
  the first DATA and 250 on retry. Both attempts have the same explicit
  `<desk-ticket-<durable-id>@loomai.pro>` Message-ID, fixed sender, exact verified
  recipient, `Support request update: <actual receipt reference>` subject and
  protected page link. No intake, transcript or auth token is present. A further
  poll sends nothing after SENT.

The first SMTP test correctly exposed Spring's preservation of a pre-existing
generated Message-ID; setting the intended stable ID explicitly before send
fixed it and the real transport test passed. Initial direct-identity HTTP tests
also exposed the Boot HTTP-converter/Jackson-node mismatch; identity response
parsing now uses Desk's existing Jackson mapper over a String body. Neither was
hidden behind a permissive recipient fallback.

Migration V6 adds subscription/outbox FKs in the existing shared Desk database.
The API/response, safe failure codes, SMTP settings and limits are documented in
[SUPPORT_TICKETS_API.md](SUPPORT_TICKETS_API.md#optional-requester-email-notifications)
and [README.md](README.md#optional-support-ticket-email). No additional per-space
notification configuration is required beyond registered allowed origins and
explicit requester opt-in. SMTP delivery remains at least once across a process
crash after mail acceptance; a stable Message-ID is not an exactly-once promise.

Hosted SMTP acceptance, actual inbox arrival and the live runtime's callback
`owner_ref` to ticket `runtime_principal` mapping are **not** claimed by these
local protocol tests. Main will qualify those after its deployment. The temporary
PostgreSQL container and random test schemas are removed after verification.

## 010.57 Opening Mode Consistency Fix (2026-11-03)

Local-only bounded fix: support spaces carry typed `conversation` settings with
bounded string codes, not a Desk-owned enum. Syntax/size checks permit 1-64 ASCII
characters matching `[A-Za-z0-9][A-Za-z0-9._-]*`; values are forwarded unchanged.
The runtime owns the active catalogue; unknown codes are not defaulted or repaired.
Omitted mode is explicitly `executor`; position is optional. Desk sends those
registered settings with a saved form opening. Direct Supabase and private
Console Platform provisioning both deliberately register executor with no fixed
position, consistent with the Support experience pack. No text inference,
permission changes, automatic retries, framework changes or remote operations.

Focused qualification passed:

- Desk: **66 tests, zero failures/errors/skips**, including real PostgreSQL
  16.15 plus H2 persistence and V4-to-V5 migration. The migration changes only
  space configuration; existing ticket rows and space revision are unchanged.
- Platform: **43 tests, zero failures/errors/skips** for
  `DeskSupportSpaceServiceTest,SupportSpaceProvisioningServiceTest`.
- HTTP contract cases cover direct/private identities, omitted/configured modes,
  optional position, exact runtime payload, credential forwarding and one opening
  POST after idempotent replay. `thinker`, `support_assistant`, future custom mode
  and position codes are accepted and preserved through HTTP and persistence.
  DTO cases cover missing/null defaults, unknown-code preservation and only
  syntax/size rejection, not semantic validation of user text. Breakdown:
  contract 27, HTTP 21, lifecycle/persistence 14, migrations 4.

Commands, run in the corresponding Maven module (supply a disposable local
PostgreSQL URL for Desk):

```sh
mvn -B -ntp -Ddesk.test.postgres.url=jdbc:postgresql://127.0.0.1:<port>/desk \
  -Ddesk.test.postgres.required=true \
  -Dtest=SupportSpaceConversationContractTest,SupportTicketHttpTest,SupportTicketsTest,SupportTicketMigrationTest test
mvn -B -ntp -Dtest=DeskSupportSpaceServiceTest,SupportSpaceProvisioningServiceTest test
```

The first Desk attempt exposed an ambiguous AssertJ generic assertion in the new
test, corrected before an initial 46-test successful run. Main review then found
that its closed enum incorrectly copied widget modes and omitted valid runtime
codes. It was replaced with bounded strings and requalified with the 66-test
Desk run plus the 43-test Platform rerun. The dedicated local PostgreSQL
container was removed afterward. No commit, push or hosted qualification is
claimed; UI origin and oversized runtime presentation fixes are separate work.

---

Date: 2026-10-26. Scope: `product-services/loomai-desk-service` backend, build, contracts and tests only. No commits made. No Platform, runtime, framework, original Basic Inbox, or UI source changes made by the backend implementation task.

## Qualified

```sh
mvn -B -ntp \
  -Ddesk.test.postgres.url=jdbc:postgresql://127.0.0.1:64260/desk \
  -Ddesk.test.postgres.required=true verify
```

Final full run completed 02:32:53 UTC: **11 tests, zero failures, zero errors, zero skips; BUILD SUCCESS**, including executable Spring Boot jar packaging. Six cases used H2; five used an actual dedicated PostgreSQL 16.15 container. Random test schemas were removed after each PostgreSQL case. The dedicated disposable PostgreSQL container is removed after qualification; recreate it and supply its port to rerun.

- `DeskIsolationTest`: six cases, H2 + PostgreSQL. Binding-scoped colliding handover, conversation, dispatch, projected-message and callback IDs; correct per-binding callback URL/signature; current claim/reply/return/close lifecycle; idempotency conflicts/replay; FIFO dead/retry/lease handling; owner/support/reviewer and staging/production isolation; immediate assignment revocation.
- `DeskChannelHttpFlowTest`: two cases, H2 + PostgreSQL. Real HTTP provider dispatches/replay and wrong-binding keys, scoped conversation detail, pre-claim denial, claim/reply/idempotent replay, signed-protocol callback processing and return-to-AI without changing the other customer's colliding handover.
- `DeskHttpSecurityTest`: three cases. Real signed RSA/ES256 JWT/JWKS HTTP admission on H2/PostgreSQL, issuer/audience/expiry/unverified-email rejection, no access from user-controlled email-verification metadata, safe service-key administration, exact registered-agent assignments, actual reviewer JWT + per-binding key forwarded to the configured full review-list URL, independent authorization recheck/revocation endpoint, PKCE/state/nonce/host-only secure cookies. BFF test also exercises encrypted access/refresh persistence, CSRF protection, logout/client removal, ES256 ID-token verification, preserved binding navigation and rejected external redirect hints.

The OIDC BFF CSRF/session unit uses Spring Security test authentication with real signed access-token validation; it does **not** claim a real hosted Supabase authorization-code exchange. Callback and review HTTP peers are bounded local protocol fixtures; runtime business-action continuation and the hosted dealership remain main-rollout verification responsibilities.

## Build Integration

Dockerfile: `deploy/container/Dockerfile`, build context **repository root**. It builds the separately owned UI using its lockfile, builds the backend with published `ai-fabric-chat-session:0.8.41`, and bundles UI static assets in the same application image. Default application port is **8086**. No per-customer application/database deployment is created by this code.

Local `docker build --progress=plain -f product-services/loomai-desk-service/deploy/container/Dockerfile -t loomai-desk:backend-foundation-local .` was attempted but stalled in Docker Hub base-image metadata retrieval, before source compilation. It was explicitly cancelled. **No successful local container-image build is claimed.** Main owns CI publication and hosted image qualification.

## Integration Contracts Ready

- `/api/desk/v1/config` exposes safe public Supabase settings.
- `/api/desk/v1/session` supplies authenticated identity, current binding roles and CSRF token/header.
- Registered agent, owner assignment, conversation and review routes are implemented in [API_CONTRACT.md](API_CONTRACT.md).
- Service-key protected workspace/binding PUT and safe binding GET are implemented.
- Service-key protected `/api/desk/admin/v1/bindings/{id}/authorizations` verifies an actual issuer/subject's explicit SUPPORT/REVIEWER assignment for Platform's bridge.
- `reviewBridgeUrl` is the **full** configured `/api/desk/bridge/v1/bindings/{id}/reviews` endpoint, not a base requiring another `/reviews` suffix.
- OIDC registration ID is `supabase`; callback is `/login/oauth2/code/supabase`. `client_secret_basic`, PKCE S256 and ES256 are configured. Deep links use only same-origin `/?bindingId=<opaque-id>` navigation.

## Modified Backend Paths

- `pom.xml`, `deploy/container/Dockerfile`.
- `src/main/java/com/loomai/desk/LoomAiDeskApplication.java`.
- `src/main/java/com/loomai/desk/channel/{config,model,persistence,service}/`: reused Basic Inbox lifecycle/contracts/signing/outbox, adapted to immutable binding-scoped SQL/composite keys and per-binding delivery configuration.
- `src/main/java/com/loomai/desk/config/`: properties, standard Spring OAuth/resource-server/CSRF/service-admin security, safe post-login navigation.
- `src/main/java/com/loomai/desk/model/DeskContracts.java`.
- `src/main/java/com/loomai/desk/service/`: identity verification, shared directory/assignments, encrypted OAuth client persistence, channel factory/scheduler and transparent review adapter.
- `src/main/java/com/loomai/desk/web/`: administration, Desk user APIs, existing channel dispatch protocol and safe HTTP error responses.
- `src/main/resources/application.yml`, `src/main/resources/db/desk-migration/V1__shared_desk.sql`.
- `src/test/java/com/loomai/desk/`: database fixtures and the three focused test classes above.
- `README.md`, `API_CONTRACT.md`, this verification record.

UI source, central authorization UI, Platform bridge/activation, CI workflow, hosted configuration, customer migration and old Inbox retirement are separately owned. Original per-deployment Inbox resources must not be deleted based solely on this backend qualification.

## 010.54 Optional External Context Verification

Date: 2026-10-30. Scope: this Desk service only, including its existing UI. No
commit, deployment, Platform/runtime/framework change or new auth flow was made.
The internal Platform support provider is a separate integration responsibility;
local HTTP peers here validate the published callback contract, not a hosted case
integration.

Qualified locally:

- Full `mvn verify` with required H2 + disposable PostgreSQL 16: **18 tests,
  zero failures/errors/skips**, executable jar packaged. After the final transport
  cancellation refinement, the five new context/endpoint tests were rerun with
  required PostgreSQL through `verify` and passed, with packaging.
- `npm run build`: TypeScript and production Vite build passed.
- Full `npm test`: **48 passed**, including five context tests and existing
  human-channel, agent authorization, review and conversation-layout regressions.
- `git diff --check`: passed. Light/dark desktop and narrow-screen context
  screenshots were inspected; no markup execution or horizontal overflow.

The context integration tests prove absent configuration, trusted administration,
encrypted credentials, actual signed agent JWT propagation, OWNER/REVIEWER denial,
foreign binding/unknown handover/expired token denial before provider lookup,
server-derived conversation/owner scope, exact response scope correlation,
allowlisted projection without private extra fields, per-binding read-only mode,
idempotent resolve forwarding and unchanged conversation/outbox control. Invalid,
oversized, missing-version, redirected and unavailable responses remain visible;
revocation during lookup prevents late disclosure. Removing this configuration
leaves the existing human channel usable.

Endpoint tests cover exact origins, HTTPS, URL/path restrictions, metadata/private
and mixed DNS answers, IPv4/IPv6 and explicit test-only loopback. The HTTP client
validates DNS again at the actual connection resolver, retains TLS verification,
and does not follow redirects, discover proxies or automatically retry writes.
Projection failure cancels the request rather than draining an unbounded body.

UI tests prove lazy context lookup, consent/observations, explicit resolve
confirmation, stable idempotency on uncertain retry, frozen confirmation version,
no case resolution through human-control buttons, plain-text rendering, read-only
providers and visible context outage without blocking chat replies. The external
system remains responsible for authoritative case permission, version checks and
mutation receipts, as specified in [the callback contract](EXTERNAL_CONVERSATION_CONTEXT.md).

Reproduction (the PostgreSQL port is supplied by a disposable local instance):

```sh
mvn -B -ntp -Ddesk.test.postgres.url=jdbc:postgresql://127.0.0.1:<test-port>/desk \
  -Ddesk.test.postgres.required=true verify
```

From `ui/`, run `npm run build` and `npm test`. The dedicated local test database
container is removed after qualification. No hosted verification is claimed.

### Changed Path Inventory

Paths below are relative to `product-services/loomai-desk-service/`; no prior
dirty file outside this directory was changed by this task.

```text
API_CONTRACT.md
BACKEND_VERIFICATION.md
EXTERNAL_CONVERSATION_CONTEXT.md
README.md
pom.xml
src/main/java/com/loomai/desk/config/DeskProperties.java
src/main/java/com/loomai/desk/model/ConversationContextContracts.java
src/main/java/com/loomai/desk/service/ConversationContextClient.java
src/main/java/com/loomai/desk/service/ConversationContextEndpointPolicy.java
src/main/java/com/loomai/desk/service/DeskConversationContextService.java
src/main/java/com/loomai/desk/web/ConversationContextController.java
src/main/resources/application.yml
src/main/resources/db/desk-migration/V2__external_conversation_context.sql
src/test/java/com/loomai/desk/DeskConversationContextTest.java
src/test/java/com/loomai/desk/service/ConversationContextEndpointPolicyTest.java
ui/src/ConversationExternalContext.tsx
ui/src/Conversations.tsx
ui/src/styles.css
ui/tests/conversation-context.spec.ts
ui/tests/fixture.ts
```

## 010.57 Support Tickets Backend Verification

Date: 2026-11-03. Desk backend source/contracts, plus the separately authorized
Console support tests and unused DTO cleanup described below. No UI,
provisioning, framework, S3 connection, deployment or commit changes by this task.

Final local `mvn verify` passed **80 tests, zero failures/errors/skips**, including
jar packaging. PostgreSQL was required, using an isolated PostgreSQL 16.15 Docker
container; all test schemas were removed and the container was stopped/removed
after qualification. H2 and real PostgreSQL exercised the same Flyway V4 schema.
The 36 new cases cover native lifecycle, independent fake-upstream provider
replacement, current authorization, private/direct HTTP identity and V3 upgrade.

```sh
mvn -q -Ddesk.test.postgres.url=jdbc:postgresql://127.0.0.1:55457/desk \
  -Ddesk.test.postgres.password=desk \
  -Ddesk.test.postgres.required=true verify
```

Verified: scoped requester ownership and pagination, opaque expectedVersion,
same-input replay/changed-input conflict, concurrent create, status/seen without
runtime calls, explicit terminal episode creation, encrypted intake/receipts,
stable initial-message keys, uncertain delivery, canonical saved-user-message
observation after an AI failure, linked/action origin with no duplicate send,
SUPPORT versus OWNER/REVIEWER and revocation, exact closed-ticket purge and
pending-delivery denial, provider provenance after selection changes, unsupported
external mutations and no fallback/mirrored native payload during upstream outage.

Private-host action CREATE is qualified with signed runtimePrincipal and
runtimeConversationId, verified runtime ownership before provider writes,
independent stable requester identity, immutable/unique private principal mapping,
same-auth replay and changed-binding rejection. Foreign owners, wrong actual
runtime subjects, missing proof, connector-key-only and forged trace-only access
are denied. Form-first retains support-ticket:<id>. Current canonical conversation
detail uses id and participantMessages; private runtime requests use the private
assertion header and registered trusted-key header, never public Authorization.

Final additions qualify the explicit five-field SERVER_CONNECTOR profile and
typed runtime-trace CREATE authority without a control-plane callback. Missing,
duplicate, expired or wrong key/issuer/audience/mode/caller/subject type/scope/
binding headers and browser Origin are rejected. Anonymous callers are rejected;
foreign requester GET cannot read another owner's ticket. Model conversationId
is ignored, verified trace conversation is adopted, changed trace replay is
rejected, original Supabase requester ownership is preserved, and these server
commands make no runtime/AI request. This is a registered trusted-runtime key
boundary, not cryptographic validation of arbitrary untrusted trace headers.

`continuationInitializesEmptyCanonicalConversationWithoutRepeatingIntake` passes
four H2/PostgreSQL x direct-Supabase/private-host combinations. The canonical
fixture returns 404 before control initialization. Tests assert authenticated
control-before-item order, proper public versus private header selection, empty
NOT_REQUIRED continuation, retained old episode, and no original intake resend.
The opening inference deadline test outlasts the short read timeout without
automatic replay. GET-only private assertions cannot authorize POST activity.

Requester roster/JPEG is authenticated and scoped to the current binding and
SUPPORT assignment. Tickets disabled + human enabled returns session/team/photo
with empty ticket capabilities; ticket create/list/get are disabled. Turning
human off or removing the current roster assignment denies photo access.
TicketEndpointPolicy permits admin-registered runtime/Supabase projects without
per-project environment allowlisting, rejects malformed/credential-bearing URLs,
and retains connection-time private/metadata/mapped-IPv6 DNS denial. The existing
conversation-context global endpoint policy is unchanged.

Contract: [SUPPORT_TICKETS_API.md](SUPPORT_TICKETS_API.md). Changed backend paths:

- `src/main/java/com/loomai/desk/ticket/`: contracts, provider SPI/native provider,
  support-space directory, requester identity/security, canonical runtime client,
  ticket facade and requester/agent/admin/integration controllers.
- `src/main/java/com/loomai/desk/config/DeskSecurityConfiguration.java`: explicit
  operator chain order after the independent requester chain.
- `src/main/java/com/loomai/desk/web/DeskErrorHandler.java`: typed ticket errors.
- `src/main/resources/db/desk-migration/V4__support_tickets.sql`.
- `src/test/java/com/loomai/desk/SupportTicketsTest.java`,
  `SupportTicketHttpTest.java`, `SupportTicketMigrationTest.java`,
  `TicketProviderContractTest.java`, `TicketEndpointPolicyTest.java`.
- `API_CONTRACT.md`, `SUPPORT_TICKETS_API.md`, this verification record.

No missing canonical runtime/framework contract was found. Main still owns
Console's verified-trace-to-requester-assertion bridge, action installation,
provisioning and hosted customer-journey qualification. Local peers are protocol
fixtures, not live runtime/provider/SSO proof. Optional notifications are not
enabled. Native unread/activity is refreshed from explicit verified canonical
activity observation; it does not claim immediate notification of every turn.

### Console Test Followup

`Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/support/` now uses
a mocked remote TicketClient and real minimal scoped context/requester/resource
authority instead of a local ticket store. Obsolete Case CRUD and Desk mutation
tests were replaced with provider delegation, opaque-version, trusted connector,
membership/consent/resource, safe read-only Desk context, private-header and
PostgreSQL mapping/lock/migration-retirement coverage. Diagnostic/document/team
trust coverage remains. Main's SupportRequesterAssertionServiceTest is unchanged.
The explicitly requested SupportContracts cleanup removes only unused legacy
records, keeping OwnedCase, CaseStatus and DeliveryStatus.

Latest complete Console slice: **114 executed, 113 passed, one error, zero skips**.
All seven SupportSpaceProvisioningServiceTest cases and ten workspace-service
tests pass, including ordinary chat without Desk session RPC, actual support pack
validation, safe empty control initialization, foreign-ID rejection, topic/actual
assignment/key registration, human-only readiness and read-only validation.
The retained regression `linkedTicketPrincipalUsesCurrentEpisodeInsteadOfOriginalCasualScope`
reproduces a main-owned production issue: SupportTicketCommandController chooses
the original casual scope before a linked ticket's current episode and rejects
the adopted principal after continuation. No Console Maven process remains.
This is local qualification, not a live rollout or complete main integration.
