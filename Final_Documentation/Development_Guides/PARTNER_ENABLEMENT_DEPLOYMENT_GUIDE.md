# Partner Enablement Deployment Guide

This guide deploys the Partner Enablement backend slice and the `Platfrom/partner-ui` app for `partners.loomai.pro`.

Use this for the current email/password test path only. Google, Apple, and LinkedIn social login are intentionally deferred until their provider details are supplied.

## Secret Source

Use the ignored private handoff file as the source of Supabase values:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`
- section `7.5 Subabse Partner UI`

Do not paste, print, commit, or log Supabase secret keys, service-role keys, database passwords, or partner JWTs.

The private section currently provides:

- Supabase project URL
- Supabase publishable/anon key for the Partner UI
- Supabase secret/admin key for one-time test account setup
- database password and connection string, if direct DB inspection is needed

## Backend Deployment Requirements

The Partner Enablement backend code lives inside `Platfrom/backend` and is deployed with the Platform backend.

The live Platform backend must be built from a branch that contains commit `032d5b53` or later. The current production Railway service is configured to build from `Platform_V1`; the partner implementation was committed on `Platform-V6`, so production will not serve partner routes until the deployed branch includes the partner slice.

Required backend env values:

```bash
PLATFORM_SUPABASE_AUTH_ENABLED=true
PLATFORM_SUPABASE_ISSUER=https://xazkenhomhtpejjjqtsy.supabase.co/auth/v1
PLATFORM_SUPABASE_JWKS_URI=https://xazkenhomhtpejjjqtsy.supabase.co/auth/v1/.well-known/jwks.json
PLATFORM_SUPABASE_AUDIENCE=authenticated
PLATFORM_SUPABASE_REQUIRE_EMAIL_VERIFIED=false
PLATFORM_PARTNER_APP_URL=https://partners.loomai.pro
PLATFORM_PARTNER_MERCHANT_APPROVAL_TTL=7d
```

Why `PLATFORM_SUPABASE_REQUIRE_EMAIL_VERIFIED=false` for this first test:

- the created email/password Supabase user is confirmed in Supabase admin data
- the issued Supabase access token for this project does not include a top-level `email_verified` claim
- keeping the Platform-side claim requirement enabled would reject valid email/password Supabase sessions with `401`

Keep email confirmation policy enforced in Supabase for real partner signups. Revisit the Platform-side claim gate when social providers are configured and their token shape is known.

## Partner UI Deployment Requirements

The Partner UI is a standalone Vite app:

```bash
npm --prefix Platfrom/partner-ui ci
npm --prefix Platfrom/partner-ui run build
npm --prefix Platfrom/partner-ui run smoke
```

For local build-time config, use:

```bash
cp Platfrom/partner-ui/.env.example Platfrom/partner-ui/.env.local
```

Then fill only local, non-committed values.

For Railway, use the included Dockerfile:

- build context: repository root
- Dockerfile: `Platfrom/partner-ui/deploy/railway/Dockerfile`
- temporary verification domain: Railway-generated `*.up.railway.app`
- production public domain: `partners.loomai.pro`

Set runtime env on the Partner UI Railway service:

```bash
PARTNER_UI_PLATFORM_API_BASE_URL=https://ai-fabric-framework-production-324f.up.railway.app
PARTNER_UI_SUPABASE_URL=https://xazkenhomhtpejjjqtsy.supabase.co
PARTNER_UI_SUPABASE_ANON_KEY=<supabase-publishable-or-anon-key>
```

The static server serves `/runtime-config.js`, so these values are injected at runtime rather than baked into the Vite bundle.

Health check:

```bash
curl -fsS "$PARTNER_UI_BASE_URL/health"
```

Expected:

```json
{"status":"UP"}
```

Runtime config check:

```bash
curl -fsS "$PARTNER_UI_BASE_URL/runtime-config.js"
```

The script must contain non-empty `platformApiBaseUrl`, `supabaseUrl`, and `supabaseAnonKey` values. If it returns empty strings, set the Partner UI Railway env vars above and redeploy.

## Email-Only Test Account

A non-social Supabase test user was created through the Supabase admin API on 2026-04-25.

Local temp files on this machine:

- `/tmp/partner_supabase.env`
- `/tmp/partner_supabase_test_account.env`
- `/tmp/partner_supabase_jwt.secret`

These files are intentionally not committed. The account is an email/password account using provider `email`; do not use social-provider flows until their details are configured.

To create another password test account without printing secrets, use the Supabase Admin API with the private-file admin key and store generated passwords/JWTs only under `/tmp`.

## Live Verification

Backend-only partial check:

```bash
PLATFORM_BASE_URL=https://ai-fabric-framework-production-324f.up.railway.app \
  scripts/verify-partner-enablement-live.sh
```

Strict release gate after backend and UI are deployed. Use the Railway-generated service URL until `partners.loomai.pro` is ready:

```bash
PARTNER_UI_BASE_URL=https://<partner-ui-service>.up.railway.app \
PARTNER_SUPABASE_JWT="$(cat /tmp/partner_supabase_jwt.secret)" \
PLATFORM_BASE_URL=https://ai-fabric-framework-production-324f.up.railway.app \
PARTNER_LIVE_STRICT=true \
  scripts/verify-partner-enablement-live.sh
```

Expected strict result:

- backend health reachable
- unauthenticated partner session rejected
- invalid partner JWT rejected
- partner UI route reachable
- valid email/password Supabase JWT accepted
- new partner sees an empty workspace
- provisioned partner catalog and store checks pass when using a provisioned test partner

## Current Blocker

As of 2026-04-25, production still returns `401` for:

```bash
POST /api/merchant/partner-access/not-a-real-code/approve
```

That means the deployed Platform backend does not yet include the partner routes. Do not interpret valid Supabase JWT `401` as a Supabase failure until the Platform backend deployment branch includes commit `032d5b53` or later and the backend env values above are configured.

## Completion Checklist

- Platform backend deployed from a branch containing Partner Enablement.
- Backend Supabase env configured.
- Partner UI deployed using `Platfrom/partner-ui/deploy/railway/Dockerfile`.
- `partners.loomai.pro` DNS points at the Partner UI service.
- Supabase email/password test account JWT is available in a temp file.
- Strict verifier passes.
