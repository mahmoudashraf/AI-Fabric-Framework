# 010.48 Backend Foundation Verification

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
