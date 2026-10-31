# Platform UI

The Console includes a public product entrance, Supabase customer onboarding,
the customer resource console, and the existing staff administration interface.

Chosen V1 stack:

- React
- TypeScript
- Vite
- Material UI
- @tanstack/react-query
- react-hook-form
- zod

## Product Entrance And Registration

- `/` and `/products`: Platform, Engage and Desk; no upper navigation menu.
- `/products/platform`, `/products/engage`, `/products/desk`: short product
  pages with Back to products, authentication and demo/setup actions.
- `/sign-up?product=platform` and `/sign-in?product=platform`: the existing
  Supabase identity provider. Product values are `platform`, `engage`, `desk`.
- `/auth/callback`: provider PKCE exchange, returning only to a known local
  product/setup or Desk authorization path. External return URLs are not accepted.
- Verified users without Console membership explicitly create their workspace.
  No existing customer can be claimed by email. Existing accounts retain the
  dual-proof account-linking flow at `/staff/sign-in?link=customer`.
- `/start?product=...`: continue to existing deployment setup, Engage app
  configuration, or deployment-scoped human support. Engage and Desk require an
  AI deployment first. No infrastructure or provider spending occurs at signup.
- `/staff/sign-in`: existing local staff credentials and API-key access.

### Email Sign-In And Account Recovery

The shared identity form offers password sign-in, email sign-in links, password
reset requests and signup-verification resend. Email-only flows never require a
password. Sign-in links use existing accounts only; registration remains explicit.
Provider errors and cooldowns are shown, and a successful request is not described
as proof of inbox delivery. Already verified accounts do not need another activation.

Desk agents use `/auth/desk/sign-in?method=email` (also `recovery`, `verify`, or
`register`) without needing a Console customer workspace. With one Supabase OAuth
server, both Desk environments use its single configured Console identity origin.
`/auth/desk/sign-in/{clientId}` preserves the destination using only an exact
registered Desk client, not an arbitrary return URL. Requesting email on one
Console origin and completing OAuth consent on another must not be qualified as
the same identity flow. Email callbacks preserve
the original local Desk authorization when available, otherwise start a fresh Desk
sign-in. The trusted environment-specific root is configured on the backend as
`platform.auth.customer-supabase.desk-sign-in-url`; it must match a registered Desk
OAuth callback origin. No user-provided `next` URL is accepted.

Supabase email redirects must allow both `/auth/callback` and `/auth/recovery`,
including the exact `?flow=desk` variants, on each deployed Console origin.
The existing `ConfirmationURL` email templates and SDK PKCE exchange are retained.
Open the newest link in the browser where it was requested; expired/failed links
show an explicit error and a route to request another. Console Account & team also
provides Change password for signed-in Supabase users. Password changes do not
claim to revoke every existing Desk/OIDC session.

Delivery qualification must include mailbox placement, not just an HTTP 200 from
Supabase. An authenticated LoomAI-domain SMTP sender is needed to qualify reliable
inbox delivery; changing UI controls alone cannot correct spam placement.

Self-service template availability is configured on the **backend** with
`PLATFORM_CUSTOMER_ONBOARDING_TEMPLATE_OFFER_IDS` (comma-separated existing
published bootstrap offer IDs). The default is empty, not every private offer.
Only explicitly selected, appropriate public templates belong in this list;
never publish customer-specific secrets, canary funding or private bindings.
The backend's V217 migration adds the enrollment receipt command to the existing
database constraint; no new database or tables are created.
Normal credentials, ownership, release/apply verification and funding contracts
still govern later deployment. Workspace creation alone grants none of these.
Explicitly revoked enrollments are not restored by reopening setup.

The owner-selected public starters are **Dealership simulator** and **Knowledge
workspace**. Their offer IDs are `public-dealership-simulator-v2` and
`public-knowledge-workspace-v1`. These are clean bootstrap packages, not invitations
to the existing Northfield demo. Publication helpers and focused contract checks
are in `verification-support/productization-01049/`.
Customer-owned S3 documents, vector/AI-provider credentials and any external
simulator/action accounts must be connected separately. Public document answers
require explicit per-file owner approval; no sample customer bucket, identity
provider, funded allowance or business database is bundled into registration.

`PLATFORM_CUSTOMER_ONBOARDING_ENABLED=false` closes new workspace creation but
does not block existing accounts. Console origins must be included in existing
`platform.auth.customer-supabase.link-origins`; provider email callback/recovery
URLs remain the existing `/auth/callback` and `/auth/recovery` routes.

Public product discovery remains readable during a backend outage. Setup and
authentication do not bypass the outage or invent working state.

## Verification

```sh
npm run build
node --test scripts/customer-supabase-auth.test.mjs
node scripts/customer-supabase-auth-ui-smoke.mjs
node scripts/customer-ui-scope-regressions.mjs
node --test scripts/document-knowledge-audience.test.mjs scripts/production-server.test.mjs
```

The browser regression suite uses synthetic identities and mocked HTTP boundaries.
It is not proof of native email delivery, hosted template publication or deployment.

Local API base URL:

- `VITE_PLATFORM_API_BASE_URL=http://localhost:8088`

