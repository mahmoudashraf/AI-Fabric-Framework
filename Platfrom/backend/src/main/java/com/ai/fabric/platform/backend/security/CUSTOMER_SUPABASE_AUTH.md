# Customer Console And Desk Authentication (010.48)

This is the auth slice only. No runtime/framework edits, hosted configuration,
credentials, commits or deployments are part of this work.

## Configuration Contract

Customer authentication is disabled until explicitly configured. Staff password
sessions/API keys and Partner auth keep their separate existing configuration.
Supply public Supabase configuration to each Platform backend. Do not put OAuth
client secrets, service-role keys or the Supabase management token here.

Exact Spring properties, also usable through `SPRING_APPLICATION_JSON`:

```json
{
  "platform": {
    "auth": {
      "customer-supabase": {
        "enabled": true,
        "project-url": "https://xazkenhomhtpejjjqtsy.supabase.co",
        "public-key": "<publishable-or-legacy-anon-key>",
        "issuer": "https://xazkenhomhtpejjjqtsy.supabase.co/auth/v1",
        "jwks-uri": "https://xazkenhomhtpejjjqtsy.supabase.co/auth/v1/.well-known/jwks.json",
        "audience": "authenticated",
        "link-origins": [
          "https://console.loomai.pro",
          "https://loomai-platform-ui.46.224.145.148.sslip.io"
        ],
        "desk-clients": [
          {"client-id": "73dca9dc-718d-4c39-974b-3492f6e1fa48", "redirect-uris": ["https://desk.loomai.pro/login/oauth2/code/supabase"]},
          {"client-id": "1b8255b8-7057-4e67-ae33-6acf21d703f9", "redirect-uris": ["https://loomai-desk.46.224.145.148.sslip.io/login/oauth2/code/supabase"]}
        ]
      }
    }
  }
}
```

Merge configuration with existing application JSON; do not replace unrelated
configuration. The Console authorization UI serving both environments needs
both exact Desk client/callback entries. The entries above are the approved
registered staging/production clients; do not append arbitrary third-party apps.
`SPRING_APPLICATION_JSON` is the deployment environment variable for this
contract; keep the nested property names and indexed client lists above. Only
`public-key` requires substitution from the approved public SDK configuration.
The browser obtains these values from `customer-config`; it needs no separate
build-time Supabase environment variables or confidential client secret.
`platform.auth.customer-supabase.link-origins` is a dedicated exact Console
origin allowlist, not the global CORS list. Configure production SSLIP Console
only if it is actually used for migration. An empty list disables account
linking; no global origin, wildcard pattern, Referer or cookie SameSite setting
substitutes for this list. Cross-origin Console requests still need the existing
CORS policy to allow credentials; CORS permission alone never permits linking.

Supabase OAuth Server setup (main/operations owner): enable OAuth Server, set
Site URL `https://console.loomai.pro`, Authorization Path
`/auth/desk/authorize`, register separate first-party Desk clients with exact
callbacks and PKCE, and preserve all previous redirects, signing keys and SMTP.
OIDC scopes are `openid email profile`; there is no custom SSO/token exchange.
Console-to-Desk navigation starts the standard Desk OAuth flow, not a URL that
contains a user access token. Native verification returns to
`https://console.loomai.pro/auth/callback`; recovery returns to
`https://console.loomai.pro/auth/recovery`. Add those exact redirect URLs without
removing earlier integrations' redirects. Verification returns to the saved
central Desk authorization request or one-time customer linking flow, never an
arbitrary external `next` URL. Direct staging Console signup/recovery uses the
same paths on its own origin and requires those exact URLs if exercised there.

## HTTP And Identity Contracts

- `GET /api/platform/auth/customer-config`: public, safe SDK configuration and
  exact Desk client/callback allowlist; disabled configuration emits no keys.
- `POST /api/platform/auth/customer-link` with `{"accessToken":"<native Supabase user access token>"}`:
  requires a browser `Origin` exactly in the dedicated `link-origins` allowlist,
  the existing local CUSTOMER_ADMIN cookie session plus independently verified
  Supabase identity/email. Missing, opaque or untrusted origins return 403 before
  identity verification or mutation, including public/customer app origins
  allowed by global CORS. It uniquely links `(issuer, sub)` to the
  existing Platform user ID. No email lookup, password import or reassignment.
- `GET /api/platform/auth/session` with native Supabase bearer: unchanged
  `PlatformAuthSessionSummary`, `authenticationMode=SUPABASE`, same customer
  permissions/resource ownership. Unlinked agents cannot access Console APIs.
- `DELETE /api/platform/auth/customer-links/{platformUserId}`: Platform admin
  revokes the link; uniqueness/history remain and native customer passwords are
  not reenabled. This is an administrative containment operation, not relinking.
- Existing local login for a linked customer returns 401 with
  `errorCode=CUSTOMER_USE_SUPABASE` and `useSupabase=true`. Existing local customer
  sessions stop authenticating immediately after linking. Staff remains local.

Migration V214 owns the identity-link table. V120.28 is only the H2 test mirror.
V120.29 mirrors the Desk binding table from V215, without marketplace data
updates; the H2 test migration target is 120.29. Production V215 is unchanged.
The mapping is to immutable Platform user ID; Supabase email/metadata never
selects the customer account. Role, user status and customer status are checked
on every Console request. Supabase anonymous/service tokens are not users.

`CustomerSupabaseIdentityService.verifyIdentity(token)` verifies the signed
issuer/audience/expiry/session user contract and provider-owned verified email.
It does not require Console membership: it can be reused by Desk's separately
authenticated review bridge. OAuth `client_id`, if present, must match the exact
Desk allowlist. `authenticate(token)` additionally rejects delegated OAuth
tokens and resolves a live linked Console customer. OAuth identity scopes are
not Console administration scopes. Account linking also requires a native token.

The shared decoder factory supports ES256/RS256. Verified email comes from
Supabase's `/auth/v1/user`, never writable `user_metadata.email_verified`.
Provider verification uses a public key, no management credential. No network
failure falls back to email, a local cookie or an API key. JWT signature checks
alone do not promise instant global Supabase logout; the token expiration and
provider's own session behavior still apply. Local link/user revocation is
checked on every request. Configure an appropriate Supabase access-token TTL.

## Auth Filter And Service Boundaries

The customer filter precedes local session/API-key authentication. An invalid
or unlinked bearer on a Console API stops authentication rather than selecting
another browser principal. It excludes `/api/desk/**`, `/api/partners/**`,
`/api/public/**`, `/api/merchant/**`, `/api/vectorization/runner/**`, non-API
machine/health routes, and native login/logout/config/link endpoints.
Exclusion grants no access: those routes retain their own authentication.

`DeskReviewBridgeAuthenticationFilter` is wired before AnonymousAuthenticationFilter.
Main owns its per-binding service-key, actual verified identity, assignment
recheck, distinct DeskReviewPrincipal and DESK_REVIEW_INTEGRATION authority.
`/api/deployments/{id}/desk` remains a normal customer-owned API, not excluded.

## Frontend Contract

The SDK is pinned to Partner's installed 2.104.1. Only the configured first-party
Platform request helper receives its access token. Bearer requests omit cookies
and API keys; auth refresh reads the SDK session on every request. API-key mode
disconnects the bearer provider. Principal transitions clear customer caches.
Native linking sends the Supabase proof in the JSON body alongside only the
existing local cookie. Logout uses the SDK and the existing local logout path.

The authorization page lives outside the Console membership gate. Desk-only
agents may register/verify/sign in there without becoming customer admins.
The page reuses an existing SDK session, loads getAuthorizationDetails, checks
the exact client/callback and identity scopes, and automatically approves only
the configured first-party Desk flow initiated by Open Desk. A per-request
attempt journal and shared pending promise prevent duplicate approvals across
Strict Mode remounts/reloads; failed attempts are not automatically retried.
Cancel after a failed approved-client flow calls denyAuthorization. Previously
consented responses are followed only to an allowlisted exact Desk callback.
Callback redirects are SDK/server-produced and checked;
arbitrary next/redirect query strings are not used.

Customer-only guards now use `isCustomerSession` or
`isCustomerAuthenticationMode`. Captured command scopes retain their actual
authentication mode. Ownership/revision/target checks were not removed.
CustomerKnowledgeConnectionsPage remains main-owned and was not edited here.

## Verification

- Focused Spring HTTP tests cover dual proof, immutable subject collisions,
  exact-email non-membership, cross-customer access, local-session retirement,
  revoked links/users and unchanged staff login.
- Real signed-JWT/JWKS tests cover ES256, RS256, issuer, audience, expiration,
  foreign keys, delegated client allowlisting and provider-owned verification.
- Browser smoke uses actual auth/provider/consent components and the SDK with
  synthetic local HTTP responses: login, refresh/logout, key isolation, linking,
  Desk-only consent, denial, unapproved client and returning consent.
- Hosted OAuth enablement, real emails, customer linking and Desk BFF sessions
  still need main's live verification. Offline success is not hosted evidence.

### Scoped Checkpoint (2026-10-26)

- Frontend: 87 focused tests passed, 11 actual-component/SDK browser scenarios
  passed, and the production UI build passed.
- Browser scenarios additionally exercise agent signup/PKCE verification back to
  the same Desk authorization request, recovery, cancelled login, and an existing
  Console owner's signup/verification/explicit dual-proof identity linking.
- Backend: the final bounded auth rerun passed all 40 tests with no failures,
  errors or skips: six real-JWKS/configuration tests, one fail-closed controller
  test, and 33 HTTP tests: `CustomerSupabaseAuthIntegrationTest` (11),
  `PlatformSessionAuthIntegrationTest` (4), `PlatformSecurityIntegrationTest` (14),
  and `CustomerAdminAccessIntegrationTest` (4). Flyway applied V120.29 and
  Hibernate schema validation passed; no validation bypass was used. The earlier
  22 customer authorization unit tests also passed.
- Origin tests prove that missing, unknown, opaque, public-site, partner,
  ecommerce and wildcard-matched customer app origins cannot link an identity.
  Globally CORS-allowed public/app requests reach the controller and are rejected
  before the decoder/provider verifier is called; no identity link is created.
  Exact dedicated Console origin requests still require both identity proofs.
- Final auth command (from `Platfrom/backend`):
  `mvn -B -Dtest=CustomerSupabaseAuthControllerTest,CustomerSupabaseConfigurationTest,CustomerSupabaseAuthIntegrationTest,PlatformSessionAuthIntegrationTest,PlatformSecurityIntegrationTest,CustomerAdminAccessIntegrationTest test`.
  Completed with `BUILD SUCCESS` on 2026-10-26 at 03:32:02 UTC (139 seconds).
- No live signup, email, identity creation, configuration mutation, deployment or
  commit was performed in this auth slice. The prepared agent signup fixture is
  intentionally untouched; operations owns the single native verification email.

Official contract references:
https://supabase.com/docs/guides/auth/oauth-server/getting-started
https://supabase.com/docs/guides/auth/sessions
