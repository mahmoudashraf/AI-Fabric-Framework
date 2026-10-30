# LoomAI Desk Service

Shared human-support integration for multiple customers and deployment targets. One Spring Boot application (including its separately built UI) and one Desk PostgreSQL database are used per hosting environment, not per customer. This backend reuses the channel lifecycle/outbox implementation, adapting persistence and configuration to trusted, immutable binding scopes. The original standalone Basic Inbox module, active provisioner and three old application/database pairs have been retired after hosted dealership conversation and Action Review qualification.

## Ownership

- Desk stores registered operators, owner links, binding-specific credentials, support/reviewer assignments, safe transcript projections, callback outbox and audit entries.
- The deployment runtime still owns conversation authority and canonical safe transcripts. Customer systems own inventory and business effects.
- Review tasks and action continuation remain in the runtime/framework. Desk forwards to the existing Platform review bridge with the actual reviewer's Supabase access token and a binding-specific bridge key. No shared reviewer, global operator key or new review engine exists.
- Desk uses the published `ai-fabric-chat-session:0.8.41` contract and does not require a framework change. The separately owner-authorized runtime application-proposal adapter can originate genuine review tasks; it is not part of Desk and does not replace ordinary chat confirmation.

See [API_CONTRACT.md](API_CONTRACT.md) for exact HTTP inputs/outputs and Platform/UI coordination.

## Optional External Conversation Context

An existing binding may use a reviewed authenticated context provider to show an
external case reference, selected resources, diagnostic consent and observations
in the conversation inspector. An explicitly enabled resolve callback updates
that external system only; it neither closes the runtime conversation nor returns
it to AI. Current SUPPORT access is required on every read and command, and only
server-configured endpoints/credentials are used. Desk stores configuration, not
external cases or another transcript. See the [integration contract](EXTERNAL_CONVERSATION_CONTEXT.md).

## Configuration

Required server-only configuration:

```text
PORT=8086
DESK_JDBC_URL=jdbc:postgresql://<shared-desk-postgres>:5432/desk
DESK_JDBC_USERNAME=<desk-owned-role>
DESK_JDBC_PASSWORD=<secret>
LOOMAI_DESK_SERVICE_KEY=<distinct random secret, minimum 32 characters>
DESK_DATA_ENCRYPTION_SECRET=<distinct stable random secret, minimum 32 characters>
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_PUBLISHABLE_KEY=<public sb_publishable_... or anon JWT only>
SUPABASE_ISSUER=https://<project>.supabase.co/auth/v1
SUPABASE_JWKS_URI=https://<project>.supabase.co/auth/v1/.well-known/jwks.json
SUPABASE_AUDIENCES=authenticated
DESK_OIDC_CLIENT_ID=<registered first-party client>
DESK_OIDC_CLIENT_SECRET=<server-only confidential-client secret>
DESK_OIDC_SIGNING_ALGORITHM=ES256
JAVA_TOOL_OPTIONS=-Xms128m -Xmx512m
```

Each environment has a distinct OAuth client, service/encryption secrets, Desk database and registered bindings. Store these through the existing private deployment secret process; never embed secrets in the UI. `SUPABASE_PUBLISHABLE_KEY` is deliberately public; a service-role/secret key is rejected by the safe config projection. RS256 is also supported where explicitly configured for an actual RS256 OIDC provider. API JWTs accept asymmetric ES256/RS256 with exact configured issuer, nonempty subject, expiry and allowed audience.

Register OAuth client callback `/login/oauth2/code/supabase` on the exact Desk origin. Configure Supabase native OAuth server/central authorization UI separately without deleting existing project redirects. Desk uses Spring Security OIDC authorization code plus PKCE and `client_secret_basic`. UI deep links may pass only an opaque `bindingId`; the server retains it across login, then redirects to the same-origin root. It is not authorization.

The browser receives a host-only `__Host-LOOMAI_DESK_SESSION` Secure/HttpOnly/SameSite=Lax cookie. Access and refresh tokens are encrypted in PostgreSQL, not returned by `/session` or stored in frontend JavaScript. CSRF is required for session mutations; `/session` provides token/header name. Bearer-only direct APIs validate real signed Supabase tokens. A user-editable `user_metadata.email_verified` value is never proof of verification: when the signed top-level claim is absent/false, registration checks Supabase's authenticated `/auth/v1/user` response and `email_confirmed_at` for the same subject.

No customer/deployment permission is inferred from email. Platform provisions owner links using trusted issuer/subject. A new agent must register using a verified Supabase identity before assignment. Assignment lookup accepts a unique exact verified registered email or the agent reference; there is no global user directory. Owner, SUPPORT and REVIEWER are distinct. Owners can explicitly assign themselves; owning a customer does not implicitly authorize conversation reads or action approval. Permissions are checked on every operation and revoked assignments stop subsequent access.

## Build And Verify

From this directory:

```sh
mvn -B -ntp test
mvn -B -ntp -DskipTests package
```

The default suite runs H2 tests and explicitly skips PostgreSQL cases when no dedicated test URL is supplied. For required real PostgreSQL qualification, use a disposable database role with schema creation rights (tests create/drop random schemas only):

```sh
mvn -B -ntp \
  -Ddesk.test.postgres.url=jdbc:postgresql://127.0.0.1:<test-port>/desk \
  -Ddesk.test.postgres.user=desk \
  -Ddesk.test.postgres.password=<test-only-password> \
  -Ddesk.test.postgres.required=true verify
```

Focused tests cover two customers and staging/production assignments, colliding handover/dispatch/message/callback IDs, PostgreSQL migration, provider authentication, actual signed JWT/JWKS admission, separate support/review roles, revocation, safe administration, real HTTP channel operations, signed callback destinations, FIFO retries/leases/idempotency, server-side OAuth token encryption, session CSRF/logout, ES256 ID-token validation, PKCE and safe login navigation. These tests do not claim hosted Supabase/Platform/dealership qualification; main rollout owns that evidence.

Build the single application image **from repository root**:

```sh
docker build -f product-services/loomai-desk-service/deploy/container/Dockerfile \
  -t loomai-desk:local .
```

The Dockerfile builds `ui/` with its own lockfile and copies the resulting static assets into the Maven artifact. It does not change UI source or package definitions. Local Maven alone builds the backend; UI bundling is performed by the Docker build. The public probe is `/actuator/health`; no administration endpoints or health details are exposed.

## Minimal Operational Notes

- Flyway owns `db/desk-migration`; PostgreSQL is required in application images. H2 is test-only.
- Each channel binding has its own provider API key, callback destination and callback signing secret. Keys/intake/outbox tokens are encrypted at rest; safe transcript projections are readable only through authorized APIs.
- Dispatch/callback keys and foreign keys include the binding. Replaying a local ID from another customer cannot read, overwrite or deduplicate another binding's state.
- Outbox delivery uses the existing timestamp/nonce/body HMAC protocol, leased attempts, bounded retries and per-conversation FIFO. Dead/pending delivery remains visible; an outage never silently returns authority to AI.
- Defaults: callback poll 1 second, request timeout 10 seconds, eight attempts, retry delay 5 seconds, retention 30 days. Retention also covers disabled bindings. Binding disable refuses unfinished conversations/outbox work rather than silently abandoning it.
- Revoking an active agent does not implicitly transfer their claim. Use the runtime's existing explicit return/close workflow or deliberately restore an authorized assignment. No new takeover/draining/recovery engine is introduced.
- BFF sessions are local to the single service instance; restarting it requires sign-in again. Persisted OAuth tokens do not themselves restore a browser session. Multiple replicas/sticky/session infrastructure are not part of this baseline.
- Supabase-issued access-token revocation follows the provider's signed-token expiry semantics. Assignment revocation is immediate on subsequent server authorization checks; there is no claim that JWT signatures provide instant provider logout revocation.
- Managed Desk's connector plan explicitly opts HTTP 404 into bounded transport retries: Coolify removes its route while the application is stopped. Other providers retain their original default (5xx/429); 401/403 are never included by this opt-in. Runtime outbox attempt limits, backoff, ordering and visible final failure remain unchanged. This is not an AI fallback or an automatic return to AI. Hosted qualification of this additional outage case is tracked separately from the already passed normal journey.

See the [customer/agent guide](../../Final_Documentation/External_User_Guides/LOOMAI_DESK_CUSTOMER_AND_AGENT_GUIDE.md) for deployment assignment, service authentication and owner/agent responsibilities. Current hosted evidence and remaining external gates are recorded in [010.48](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_48_LOOMAI_DESK_MANAGED_HUMAN_SUPPORT_AND_REVIEW_SERVICE_IMPLEMENTATION_PLAN.md); source or a healthy probe alone does not prove the complete journey.
