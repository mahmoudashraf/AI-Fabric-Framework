# LoomAI Engage Standalone AI Workspace

One shared, static frontend shell for verified LoomAI AI Workspace installations. The shell resolves its exact browser origin through the Platform, initializes the reviewed experience pack, and sends message traffic directly to the installation's assigned runtime.

Required runtime environment:

- `PORT` (default `8080`)
- `PLATFORM_API_BASE_URL` (for example `https://api.loomai.pro`)

Customer identity configuration is obtained from the installation bootstrap. Secret provider credentials are never placed in this image or in an experience pack.

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
