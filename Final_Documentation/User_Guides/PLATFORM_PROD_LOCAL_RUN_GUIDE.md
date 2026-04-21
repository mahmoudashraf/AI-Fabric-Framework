# Platform Prod-Local Run Guide

Status: current branch guide (2026-03-30)

This guide explains how to run the **AI Enablement Platform** locally in the same **prod-like Railway provisioning mode** we are currently using for realistic end-to-end testing.

This is not a plain local dev run.

It is a **prod-local run** because:

- the backend runs with `PLATFORM_PROVISIONING_MODE=RAILWAY_API`
- the platform uses a real Railway workspace/token
- the backend exposes a public artifact base URL through a tunnel
- the platform provisions real Railway customer deployments
- the UI runs locally against that backend

This is the closest local workflow to hosted production without deploying the platform itself first.

---

## 1) What This Mode Is For

Use prod-local mode when you want to:

- run the platform backend on your machine
- run the platform UI on your machine
- expose the backend publicly so Railway can fetch deployment artifacts
- use the platform to create and apply real Railway runtime/connector deployments
- verify end-to-end provisioning before hosting the platform itself

Do not use this mode as the final hosted production topology.

For hosted deployment, use:

- [PLATFORM_PRODUCTION_DEPLOYMENT_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/User_Guides/PLATFORM_PRODUCTION_DEPLOYMENT_GUIDE.md)

---

## 2) Current Local Topology

The current prod-local setup is:

- local platform backend on `http://localhost:8088`
- local platform UI on `http://localhost:3000`
- local Postgres on `localhost:15432`
- public tunnel to the backend
- Railway workspace used as the real provisioning target

The backend process is started through:

- [run-prod-local.local.sh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/run-prod-local.local.sh)

which delegates to:

- [run-prod-local.example.sh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/run-prod-local.example.sh)

and reads local ignored config from:

- `Platfrom/backend/scripts/prod-local.env`

The committed template is:

- [prod-local.env.example](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/prod-local.env.example)

---

## 3) Prerequisites

You need:

- Java 21
- Maven
- Node.js / npm
- local Postgres
- `cloudflared` or `ngrok` if you want the runner to create the tunnel automatically
- a real Railway workspace token
- a real Railway workspace id

Optional but recommended:

- `jq`
- `curl`

---

## 4) Required Local Services

### 4.1 Postgres

The current prod-local run expects Postgres at:

- host: `localhost`
- port: `15432`
- database: `ai_enablement_platform`
- username: `platform`
- password: `platform`

Equivalent JDBC URL:

```env
PLATFORM_DB_URL=jdbc:postgresql://localhost:15432/ai_enablement_platform
```

### 4.2 Tunnel

The platform backend must be reachable from Railway.

You can run the tunnel in one of three ways:

- `PLATFORM_TUNNEL_MODE=manual`
- `PLATFORM_TUNNEL_MODE=cloudflared`
- `PLATFORM_TUNNEL_MODE=ngrok`

Current recommended local mode:

- `PLATFORM_TUNNEL_MODE=cloudflared`

---

## 5) Local Env File

Copy:

- [prod-local.env.example](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/prod-local.env.example)

to:

- `Platfrom/backend/scripts/prod-local.env`

That local file is intentionally git-ignored.

### 5.1 Minimum required values

You must set:

```env
PLATFORM_TUNNEL_MODE=cloudflared

RAILWAY_API_TOKEN=<workspace-token>
RAILWAY_WORKSPACE_ID=<workspace-id>
PLATFORM_DEPLOY_REPOSITORY=mahmoudashraf/AI-Fabric-Framework
PLATFORM_DEPLOY_BRANCH=Platform-V5
PLATFORM_PROVISIONING_ENVIRONMENT=dev

PLATFORM_DB_URL=jdbc:postgresql://localhost:15432/ai_enablement_platform
PLATFORM_DB_USERNAME=platform
PLATFORM_DB_PASSWORD=platform

PLATFORM_BOOTSTRAP_ADMIN_ENABLED=true
PLATFORM_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
PLATFORM_BOOTSTRAP_ADMIN_PASSWORD=AdminPass123!
PLATFORM_BOOTSTRAP_ADMIN_DISPLAY_NAME=Platform Admin

PLATFORM_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
PLATFORM_CORS_ALLOW_CREDENTIALS=true

OPENAI_API_KEY=<real-openai-key>
CONNECTOR_API_KEY=test
ACTIONS_CONNECTOR_API_KEY=test
APP_ADMIN_API_KEY=test
PLATFORM_ARTIFACT_SIGNING_KEY=<strong-random-value>

PLATFORM_PUBLIC_API_ENABLED=true
PUBLIC_API_CLIENT_ID=shopify-dev
PUBLIC_API_CLIENT_SECRET=shopify-secret

PLATFORM_BOOTSTRAP_SAMPLE_ENABLED=false
PLATFORM_SERVER_PORT=8088
PLATFORM_REBUILD_JAR=true
```

### 5.2 Notes

- `OPENAI_API_KEY` must be a real key if you want embeddings/indexing to work.
- `CONNECTOR_API_KEY`, `ACTIONS_CONNECTOR_API_KEY`, and `APP_ADMIN_API_KEY` can be simple demo values locally, but not in real production.
- `PLATFORM_DEPLOY_BRANCH` must point to a pushed GitHub branch that matches the current platform release track, not only local code.

---

## 6) Start The Backend

From repo root:

```bash
bash Platfrom/backend/scripts/run-prod-local.local.sh
```

What the runner does:

1. loads `prod-local.env`
2. starts a public tunnel if configured
3. rebuilds the backend jar if `PLATFORM_REBUILD_JAR=true`
4. launches the platform backend with `RAILWAY_API` mode

Expected startup output includes:

- tunnel mode
- public base URL
- Railway workspace id
- deployment repository and branch
- backend port

If `PLATFORM_TUNNEL_MODE=cloudflared`, the runner auto-discovers a URL like:

- `https://<random>.trycloudflare.com`

That URL becomes:

- `PLATFORM_PUBLIC_BASE_URL`

and is used in deployment artifact URLs.

---

## 7) Start The UI

From repo root:

```bash
cd Platfrom/ui
npm install
npm run dev
```

Default local UI URL:

- `http://localhost:5173`

If you use the local prod-style UI server path instead, the current local setup may also expose:

- `http://localhost:3000`

For the normal Vite workflow, `5173` is the expected dev URL.

---

## 8) Default Local Login

With bootstrap admin enabled, the current local login is:

- email: `admin@example.com`
- password: `AdminPass123!`

These come from the local env file.

If you change them in `prod-local.env`, restart the backend.

---

## 9) Health Checks

Backend:

```bash
curl http://localhost:8088/actuator/health
```

UI:

```bash
curl http://localhost:3000/health
```

or for Vite dev:

```bash
curl -I http://localhost:5173
```

Railway preflight:

```bash
curl -b /tmp/platform-cookies.txt http://localhost:8088/api/platform/provisioning/railway/preflight
```

The backend must be healthy and the preflight should be `ready=true` before doing a real apply.

---

## 10) What “Ready” Looks Like

A good prod-local run has:

- backend health `UP`
- UI reachable
- public tunnel URL available
- Railway preflight `ready=true`
- required platform secrets present
- login works

At that point you can:

- create or bootstrap a deployment
- publish a draft
- apply the version to Railway

---

## 11) Current Known Behavior

### 11.1 Draft changes vs live deployment

Saving in the UI only updates the draft.

To affect the real Railway deployment you must:

1. save draft changes
2. validate
3. publish
4. apply

This matters for values like:

- `corsAllowedOrigins`
- `corsAllowedOriginPatterns`
- `corsAllowCredentials`

### 11.2 Secrets

Secret-only changes do not require publish, but they do require:

- re-apply

because Railway env vars are refreshed during apply.

### 11.3 Tunnel lifetime

If the tunnel URL changes:

- restart the backend
- publish/apply again if the deployed services need new artifact URLs

This is why prod-local is useful for testing, but not ideal as a permanent hosted setup.

---

## 12) Typical Prod-Local Workflow

1. Start Postgres.
2. Start backend with:

```bash
bash Platfrom/backend/scripts/run-prod-local.local.sh
```

3. Start UI.
4. Sign in.
5. Confirm Railway preflight is ready.
6. Edit draft config.
7. Publish the draft.
8. Apply the version.
9. Verify deployment from Diagnostics/Revisions.
10. Run external verification if needed.

For the ecommerce demo verifier:

```bash
bash scripts/verify-ecommerce-deployment.sh
```

---

## 13) Troubleshooting

### 13.1 Railway apply fails with artifact/config URL problems

Usually means:

- tunnel is dead
- backend restarted and tunnel URL changed
- `PLATFORM_PUBLIC_BASE_URL` is stale

Fix:

- restart prod-local backend
- confirm the new tunnel URL
- apply again

### 13.2 Railway deployment does not reflect a recent backend code change

Usually means:

- your local backend was not restarted after rebuilding
- or Railway is still building an older pushed branch

Fix:

- restart prod-local backend
- confirm `PLATFORM_DEPLOY_BRANCH`
- push the branch used by Railway
- publish/apply again

### 13.3 Indexing fails with `EMBEDDING_FAILED`

Usually means:

- `OPENAI_API_KEY` is wrong
- or the platform secret store still has a placeholder value

Fix:

- correct `OPENAI_API_KEY`
- re-apply

### 13.4 CORS settings do not appear in Railway env

Usually means:

- they are only saved in the draft
- or the backend that performed the apply was stale

Fix:

- restart backend if needed
- publish
- apply

---

## 14) Related Files

- [run-prod-local.example.sh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/run-prod-local.example.sh)
- [prod-local.env.example](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/prod-local.env.example)
- [PLATFORM_PRODUCTION_DEPLOYMENT_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/User_Guides/PLATFORM_PRODUCTION_DEPLOYMENT_GUIDE.md)
- [PLATFORM_ADMIN_USER_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/User_Guides/PLATFORM_ADMIN_USER_GUIDE.md)
