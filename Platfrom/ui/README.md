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
workspace**. Their offer IDs are `public-dealership-simulator-v1` and
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
```

The browser regression suite uses synthetic identities and mocked HTTP boundaries.
It is not proof of native email delivery, hosted template publication or deployment.

Local API base URL:

- `VITE_PLATFORM_API_BASE_URL=http://localhost:8088`

