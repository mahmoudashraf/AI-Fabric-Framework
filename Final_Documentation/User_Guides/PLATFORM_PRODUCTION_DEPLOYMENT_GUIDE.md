# Platform Production Deployment Guide

Status: current branch guide (2026-03-29)

This guide explains how to deploy the **AI Enablement Platform control plane** in a production-oriented setup, and how to run the platform locally in **live Railway provisioning mode** for realistic end-to-end testing.

This guide is about the **platform itself** in `Platfrom/`, not about customer runtime deployments only.

Primary audience:

- Platform Admin
- Platform Operator

Companion docs:

- `Final_Documentation/User_Guides/PLATFORM_ADMIN_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_CONFIG_AND_SECRETS_MANAGEMENT_GUIDE.md`
- `changes/Productization/PLATFORM_PHASE_18_PLUS_EXECUTION_PLAN.md`
- `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`

---

## 1) What “Production Deployment” Means Here

The platform is the **control plane**. In production it is responsible for:

- storing deployment configuration in Postgres
- compiling versioned artifacts
- provisioning Railway customer environments
- driving publish/apply flows
- verifying deployed runtime and REST connector instances
- exposing the platform UI and public provisioning API

The platform is **not** the same thing as the customer runtime deployment. It manages those deployments.

---

## 2) Recommended Production Topology

### 2.1 Platform components

Recommended minimum production topology:

- `Platfrom/backend`
- `Platfrom/ui`
- managed `Postgres`

The backend should be reachable on a **public HTTPS URL** because deployed Railway services must fetch config artifacts from it.

### 2.2 Why the backend needs a public URL

When the platform provisions a runtime and REST connector, it injects artifact URLs such as:

- `ai-actions.yml`
- `ai-entity-config.yml`
- `actions-routing.yml`
- `deployment-manifest.json`

These are consumed by the deployed services through:

- `AI_ACTIONS_CATALOG_PATH`
- `AI_CONFIG_DEFAULT_FILE`
- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION`

If `PLATFORM_PUBLIC_BASE_URL` points to `localhost` or another private-only address, Railway-hosted services cannot fetch those artifacts.

---

## 3) Current Production-Ready Capabilities

Implemented in the current branch:

- Postgres + Flyway persistence
- platform session login
- platform admin/operator separation
- platform secret management
- signed artifact delivery
- Railway provisioning support
- async apply/release tracking
- verification and diagnostics
- public provisioning API for machine clients

Current limitations to be aware of:

- customer-admin and customer-operator are product personas, not fully enforced backend roles yet
- public API clients are still configured statically rather than self-registered
- Shopify integration is not implemented yet

---

## 4) Required Backend Configuration

### 4.1 Core runtime config

Required:

- `PLATFORM_DB_URL`
- `PLATFORM_DB_USERNAME`
- `PLATFORM_DB_PASSWORD`
- `PLATFORM_PUBLIC_BASE_URL`
- `PLATFORM_PROVISIONING_MODE=RAILWAY_API`
- `RAILWAY_API_TOKEN`
- `RAILWAY_WORKSPACE_ID`
- `PLATFORM_DEPLOY_REPOSITORY`

Recommended:

- `PLATFORM_DEPLOY_BRANCH=main`
- `PLATFORM_PROVISIONING_ENVIRONMENT=dev`

### 4.2 Auth config

Recommended hosted config:

- `PLATFORM_AUTH_ENABLED=true`
- `PLATFORM_AUTH_API_KEY_ENABLED=false`
- `PLATFORM_AUTH_SESSION_ENABLED=true`
- `PLATFORM_AUTH_SESSION_COOKIE_SECURE=true`
- `PLATFORM_BOOTSTRAP_ADMIN_ENABLED=true` for first boot only
- `PLATFORM_BOOTSTRAP_ADMIN_EMAIL=<initial-admin>`
- `PLATFORM_BOOTSTRAP_ADMIN_PASSWORD=<initial-admin-password>`

After initial hosted bootstrap, keep bootstrap credentials under strict control.

If the UI and backend are on different origins, also set:

- `PLATFORM_AUTH_SESSION_COOKIE_SAME_SITE=None`

Why:

- the browser session cookie must be allowed on cross-site UI -> backend requests
- with separate Railway UI/backend service domains, `SameSite=Strict` will cause successful login responses but later authenticated API calls to return `401`

### 4.3 Browser UI CORS

The backend supports:

- `PLATFORM_CORS_ALLOWED_ORIGINS`
- `PLATFORM_CORS_ALLOWED_ORIGIN_PATTERNS`
- `PLATFORM_CORS_ALLOW_CREDENTIALS`

Use this if your UI is on a different origin than the backend.

Examples:

- same-origin UI/backend through one host or reverse proxy: simplest production setup
- separate UI domain: configure `PLATFORM_CORS_ALLOWED_ORIGINS`
- preview or wildcard-style browser origins: configure `PLATFORM_CORS_ALLOWED_ORIGIN_PATTERNS`

### 4.4 Public provisioning API

Only enable if you need vertical/backend consumers:

- `PLATFORM_PUBLIC_API_ENABLED=true`

Current branch note:

- public API clients are configured through platform properties
- for local smoke use, the provided local script supports one client id/secret pair

---

## 5) Required Platform Secrets

The platform secret layer currently supports:

- `OPENAI_API_KEY` (required)
- `CONNECTOR_API_KEY` (required)
- `ACTIONS_CONNECTOR_API_KEY` (required)
- `PLATFORM_ARTIFACT_SIGNING_KEY` (required)
- `APP_ADMIN_API_KEY` (optional but strongly recommended)

Meaning:

- `OPENAI_API_KEY`
  - used by platform-managed runtime deployments that target OpenAI
- `CONNECTOR_API_KEY`
  - protects deployed REST connector instances
- `ACTIONS_CONNECTOR_API_KEY`
  - shared key used by runtime when calling the REST connector
- `PLATFORM_ARTIFACT_SIGNING_KEY`
  - signs config artifact URLs served by the platform
- `APP_ADMIN_API_KEY`
  - protects runtime `/api/admin/*` endpoints

Recommendation:

- set all required secrets before using live Railway apply
- also set `APP_ADMIN_API_KEY` so runtime admin endpoints are not left open

---

## 6) Hosted Production Deployment Steps

### 6.1 Provision Postgres

Create a Postgres database for the platform.

Minimum needed by backend:

- connection URL
- username
- password

The backend will apply Flyway migrations automatically on boot.

### 6.2 Railway service setup

If you are deploying the platform itself on Railway, create two separate services in the same Railway project:

- `platform-backend`
- `platform-ui`

Use the repo root as the build context for both services.

Important:

- do not set the Railway root directory to `Platfrom/backend`
- do not set the Railway root directory to `Platfrom/ui`

These Dockerfiles intentionally copy files from the monorepo root, so the service must build from the repository root.

Backend Railway service settings:

- Dockerfile path: `Platfrom/backend/deploy/railway/Dockerfile`
- repo/build context: repository root
- exposed app port: Railway `PORT` -> backend `server.port`

UI Railway service settings:

- Dockerfile path: `Platfrom/ui/deploy/railway/Dockerfile`
- repo/build context: repository root
- exposed app port: Railway `PORT` -> UI server `3000`

Relevant files:

- [backend Railway Dockerfile](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/deploy/railway/Dockerfile)
- [ui Railway Dockerfile](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/deploy/railway/Dockerfile)
- [ui runtime server](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/deploy/railway/server.mjs)

### 6.3 Deploy Platform Backend

Deploy [backend](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend) with:

- public HTTPS hostname
- Postgres connectivity
- Railway provisioning env vars
- platform auth bootstrap env vars
- required deployment secrets

Minimum production-oriented env shape:

```env
PLATFORM_DB_URL=jdbc:postgresql://<host>:5432/ai_enablement_platform
PLATFORM_DB_USERNAME=<user>
PLATFORM_DB_PASSWORD=<password>

PLATFORM_PUBLIC_BASE_URL=https://platform-backend.example.com
PLATFORM_PROVISIONING_MODE=RAILWAY_API
RAILWAY_API_TOKEN=<workspace-token>
RAILWAY_WORKSPACE_ID=<workspace-id>
PLATFORM_DEPLOY_REPOSITORY=owner/repo
PLATFORM_DEPLOY_BRANCH=main

PLATFORM_AUTH_ENABLED=true
PLATFORM_AUTH_API_KEY_ENABLED=false
PLATFORM_AUTH_SESSION_ENABLED=true
PLATFORM_BOOTSTRAP_ADMIN_ENABLED=true
PLATFORM_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
PLATFORM_BOOTSTRAP_ADMIN_PASSWORD=<strong-password>
PLATFORM_AUTH_SESSION_COOKIE_SECURE=true
PLATFORM_AUTH_SESSION_COOKIE_SAME_SITE=None

PLATFORM_CORS_ALLOWED_ORIGINS=https://platform-ui.example.com
PLATFORM_CORS_ALLOW_CREDENTIALS=true

OPENAI_API_KEY=<secret>
CONNECTOR_API_KEY=<secret>
ACTIONS_CONNECTOR_API_KEY=<secret>
APP_ADMIN_API_KEY=<secret>
PLATFORM_ARTIFACT_SIGNING_KEY=<secret>
```

Railway note:

- Dockerfile path: `Platfrom/backend/deploy/railway/Dockerfile`
- keep the service build context at repo root

### 6.4 Deploy Platform UI

Deploy [ui](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui) with:

- `PLATFORM_UI_API_BASE_URL=https://platform-backend.example.com`

If using separate origins, ensure backend CORS allows the UI origin.

Why this variable:

- the UI is served by [server.mjs](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/deploy/railway/server.mjs), which injects runtime config through `/runtime-config.js`
- for hosted Railway deployment, `PLATFORM_UI_API_BASE_URL` is the preferred env var
- `VITE_PLATFORM_API_BASE_URL` still works as a fallback, but it is not the preferred runtime path

Railway note:

- Dockerfile path: `Platfrom/ui/deploy/railway/Dockerfile`
- keep the service build context at repo root

### 6.5 First Boot Verification

After backend and UI are up:

1. open the UI
2. sign in with the bootstrap admin
3. open `Security`
4. confirm Railway preflight is green
5. create a deployment
6. publish and apply a version
7. confirm `Diagnostics` shows successful provisioning and verification

---

## 7) Local “Prod Mode” Run

This means:

- backend runs on your machine
- provisioning mode is real `RAILWAY_API`
- Railway calls still target real Railway
- the backend is exposed through a public tunnel URL

This is useful for:

- end-to-end testing before hosted rollout
- verifying live Railway provisioning from the current branch

### 7.1 Requirements

You need:

- local Postgres
- either:
  - a public tunnel or reverse proxy URL, or
  - a locally installed tunnel client supported by the runner:
    - `cloudflared`
    - `ngrok`
- `RAILWAY_API_TOKEN`
- `RAILWAY_WORKSPACE_ID`
- `PLATFORM_DEPLOY_REPOSITORY`
- required platform secrets

### 7.2 Files created for this flow

Committed template files:

- [run-prod-local.example.sh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/run-prod-local.example.sh)
- [prod-local.env.example](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/prod-local.env.example)

Git-ignored local files:

- `Platfrom/backend/scripts/run-prod-local.local.sh`
- `Platfrom/backend/scripts/prod-local.env`

These ignored files are intended to hold your real local values without entering git.

### 7.3 Recommended local setup

1. Copy:
   - `prod-local.env.example` -> `prod-local.env`
   - `run-prod-local.example.sh` -> `run-prod-local.local.sh`
2. Fill `prod-local.env` with real values.
3. Choose one tunnel mode:
   - `PLATFORM_TUNNEL_MODE=manual`
     - set `PLATFORM_PUBLIC_BASE_URL` to a real public URL
   - `PLATFORM_TUNNEL_MODE=cloudflared`
     - install `cloudflared`
     - leave `PLATFORM_PUBLIC_BASE_URL` unset or ignore its placeholder
   - `PLATFORM_TUNNEL_MODE=ngrok`
     - install `ngrok`
     - make sure the local ngrok API is reachable, usually `http://127.0.0.1:4040`
4. Run:

```bash
bash Platfrom/backend/scripts/run-prod-local.local.sh
```

### 7.4 What the local prod-mode runner does

The script:

- loads your local `prod-local.env`
- validates required variables
- resolves `PLATFORM_PUBLIC_BASE_URL` from the selected tunnel mode
- blocks `localhost` as `PLATFORM_PUBLIC_BASE_URL`
- optionally rebuilds the backend jar
- starts the backend in:
  - `RAILWAY_API` mode
  - session auth enabled
  - browser CORS enabled for configured UI origins

### 7.4.1 Tunnel modes

Supported values for `PLATFORM_TUNNEL_MODE`:

- `manual`
  - use when you already have a stable public URL from a tunnel or reverse proxy
  - `PLATFORM_PUBLIC_BASE_URL` is required
- `cloudflared`
  - runner starts `cloudflared tunnel --url http://127.0.0.1:<port>`
  - runner extracts the `trycloudflare.com` URL and injects it into the backend
- `ngrok`
  - runner starts `ngrok http http://127.0.0.1:<port>`
  - runner reads the HTTPS public URL from the local ngrok API and injects it into the backend

The tunnel process is tied to the runner process. When the backend stops, the runner cleans up the tunnel process.

### 7.5 Example local env file

```env
PLATFORM_TUNNEL_MODE=manual
PLATFORM_PUBLIC_BASE_URL=https://your-tunnel.example
RAILWAY_API_TOKEN=<workspace-token>
RAILWAY_WORKSPACE_ID=<workspace-id>
PLATFORM_DEPLOY_REPOSITORY=mahmoudashraf/AI-Fabric-Framework
PLATFORM_DEPLOY_BRANCH=main

PLATFORM_DB_URL=jdbc:postgresql://localhost:15432/ai_enablement_platform
PLATFORM_DB_USERNAME=platform
PLATFORM_DB_PASSWORD=platform

PLATFORM_BOOTSTRAP_ADMIN_ENABLED=true
PLATFORM_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
PLATFORM_BOOTSTRAP_ADMIN_PASSWORD=<password>

PLATFORM_CORS_ALLOWED_ORIGINS=http://localhost:5173
PLATFORM_CORS_ALLOW_CREDENTIALS=true

OPENAI_API_KEY=<secret>
CONNECTOR_API_KEY=<secret>
ACTIONS_CONNECTOR_API_KEY=<secret>
APP_ADMIN_API_KEY=<secret>
PLATFORM_ARTIFACT_SIGNING_KEY=<secret>
```

Example using automatic `cloudflared` tunnel mode:

```env
PLATFORM_TUNNEL_MODE=cloudflared
RAILWAY_API_TOKEN=<workspace-token>
RAILWAY_WORKSPACE_ID=<workspace-id>
PLATFORM_DEPLOY_REPOSITORY=mahmoudashraf/AI-Fabric-Framework
PLATFORM_DEPLOY_BRANCH=main

PLATFORM_DB_URL=jdbc:postgresql://localhost:15432/ai_enablement_platform
PLATFORM_DB_USERNAME=platform
PLATFORM_DB_PASSWORD=platform

PLATFORM_BOOTSTRAP_ADMIN_ENABLED=true
PLATFORM_BOOTSTRAP_ADMIN_EMAIL=admin@example.com
PLATFORM_BOOTSTRAP_ADMIN_PASSWORD=<password>

PLATFORM_CORS_ALLOWED_ORIGINS=http://localhost:5173
PLATFORM_CORS_ALLOW_CREDENTIALS=true

OPENAI_API_KEY=<secret>
CONNECTOR_API_KEY=<secret>
ACTIONS_CONNECTOR_API_KEY=<secret>
APP_ADMIN_API_KEY=<secret>
PLATFORM_ARTIFACT_SIGNING_KEY=<secret>
```

### 7.6 Local prod-mode verification

Once the backend is running:

1. `GET /actuator/health`
2. sign in through the UI
3. open `Security`
4. confirm Railway preflight is ready
5. create a deployment
6. publish and apply
7. confirm the UI shows:
   - `Mode: RAILWAY_API`
   - real Railway-backed URLs
   - not `.placeholder.local`

---

## 8) Production Safety Rules

Do:

- keep `PLATFORM_PUBLIC_BASE_URL` public and stable
- protect runtime admin endpoints with `APP_ADMIN_API_KEY`
- use HTTPS for the platform backend
- keep provisioning secrets in the platform secret layer or protected env
- keep the platform database durable and backed up

Do not:

- run `RAILWAY_API` mode with `PLATFORM_PUBLIC_BASE_URL=http://localhost:...`
- rely on stub mode for real rollout expectations
- leave bootstrap admin credentials weak in hosted environments
- treat baked Docker config as the source of truth for customer deployments

---

## 9) Recommended Production Checks

### 9.1 Backend checks

- `GET /actuator/health`
- `GET /api/platform/overview`
- `GET /api/platform/provisioning/railway/preflight`

### 9.2 UI checks

- platform login works
- deployment creation works
- draft publish works
- apply starts and progresses
- diagnostics show release evidence

### 9.3 Public API checks

If enabled:

- `POST /api/public/deployments`
- `GET /api/public/deployments/{deploymentId}/status`
- `POST /api/public/deployments/{deploymentId}/apply`

---

## 10) Current Branch Caveats

Before calling the platform fully production-ready for customers, keep in mind:

- customer role isolation is not complete yet
- public API client registration is still static
- Shopify is still the next consumer phase, not implemented in this branch

So this guide describes a **production-oriented platform deployment path**, not a finished enterprise product surface.

---

## 11) Related Docs

- `Final_Documentation/User_Guides/PLATFORM_ADMIN_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`
- `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`
- `changes/Productization/PLATFORM_PHASE_18_PLUS_EXECUTION_PLAN.md`
