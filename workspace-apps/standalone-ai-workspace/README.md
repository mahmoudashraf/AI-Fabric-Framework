# LoomAI Engage Standalone AI Workspace

One shared, static frontend shell for verified LoomAI AI Workspace installations. The shell resolves its exact browser origin through the Platform, initializes the reviewed experience pack, and sends message traffic directly to the installation's assigned runtime.

Required runtime environment:

- `PORT` (default `8080`)
- `PLATFORM_API_BASE_URL` (for example `https://api.loomai.pro`)

Customer identity configuration is obtained from the installation bootstrap. Secret provider credentials are never placed in this image or in an experience pack.

## Authentication Lifetime Contract

Protected-capability sign-in consumes only the structured runtime authentication
requirement, never answer prose or generic access denials. Successful sign-in
starts a new identity-bound conversation. Cancelled/failed login does not execute
or replay the blocked operation. Refresh preserves state only for the same
verified subject; account switch and logout clear this installation's private
state without touching other installations or claiming anonymous history.

Authenticated deployment-local runtime tokens have a separate configurable
`AI_FABRIC_RUNTIME_AUTHENTICATED_TOKEN_TTL_SECONDS` (default and maximum 300
seconds). The broker sets expiry to the earlier of that TTL and the verified
provider access token's expiry. A valid provider JWT is not rejected merely for
being older than five minutes. Anonymous token lifetime remains a separate
policy. Authenticated runtime tokens require signed `iat` and `exp`; missing or
overlong authenticated lifetime claims fail closed.

There is no runtime user-session revision/revocation ledger. A copied issued
runtime token remains valid until its exact expiry, including after local logout;
it is not silently invalidated by browser storage cleanup. Supabase local logout
revokes the provider session's refresh tokens, not its already-issued access JWT.
The broker currently validates JWT signature, issuer, audience and expiry, not
online provider-session revocation. Consequently, a copied provider JWT can
still be exchanged while valid; each runtime token remains capped and can never
outlive that provider JWT. Immediate provider-session revocation requires a
reviewed online session check or a real deployment-local revocation ledger and
is not claimed here. See [Supabase sign-out](https://supabase.com/docs/guides/auth/signout).

Logout explicitly requests provider `scope=local`. A failed provider request
still destroys local authority/state but reports revocation as unconfirmed.
Expiry or rejected refresh never downgrades an in-flight authenticated request
to anonymous authority or replays it under a new account. Hosted acceptance must
measure these behaviors against the reviewed provider; fixture tests are not
provider revocation or production-readiness evidence.

## Live Verification

Run read-only chat, persistence, and responsive-layout checks on two registered
app origins with `WORKSPACE_LIVE_ORIGINS` and `npm run test:live`.

For a reviewed mixed-authentication installation, set `WORKSPACE_AUTH_ORIGIN`
and `WORKSPACE_AUTH_CREDENTIAL_FILE`, then run `npm run test:auth-live`. The
credential file belongs outside the repository and contains a synthetic user's
email/password; reports never contain provider or runtime tokens.

Run `node tests/standalone-private-live-gate.mjs` with
`WORKSPACE_PRIVATE_ORIGIN`, `WORKSPACE_OTHER_RUNTIME`, its registered
`WORKSPACE_OTHER_ORIGIN`, and the same private
credential file to verify entry-gated login, anonymous denial, deployment token
isolation, and logout. This is authorization evidence, not private knowledge
answer-quality evidence.

Run `node tests/standalone-handover-live-gate.mjs` with
`WORKSPACE_LIVE_ORIGINS` to verify UI transfer, human-pending message admission,
paused AI orchestration, and explicit owner cancellation. It creates synthetic
human-channel episodes, not bookings or leads. Use only reviewed test inboxes.
