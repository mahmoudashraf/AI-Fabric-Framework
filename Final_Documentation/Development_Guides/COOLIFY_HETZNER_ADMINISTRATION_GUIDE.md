# Coolify And Hetzner Administration Guide

This guide covers operational access, staging-first deployment practice, and recovery workflows for the Coolify hosts running LoomAI platform services.

Do not put raw passwords, API tokens, or private key bodies in this tracked guide. Live secret values belong only in local secret files or the ignored private handoff:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`
- `/tmp/coolify_admin_credentials.env`
- `/tmp/coolify_api_tokens.env`
- `~/.ssh/loom_coolify_hetzner_ed25519`

## 1. Operating Policy

Current policy:

- develop and verify on Coolify staging first
- do not move Plan 009 or other new work to production until explicitly requested
- use production only for read-only health checks unless the owner gives direct approval for a production operation
- never print secret values in chat, command output, logs, PRs, or tracked docs

Staging is the active implementation target for Shopify MCP-first Plan 009.

## 2. Environments

### 2.1 Staging

- Coolify dashboard: `http://46.224.145.148:8000`
- SSH host: `46.224.145.148`
- SSH user: `loomops`
- Platform backend: `https://loomai-platform-backend.46.224.145.148.sslip.io`
- Platform UI: `https://loomai-platform-ui.46.224.145.148.sslip.io`
- Partner UI: `https://loomai-partner-ui.46.224.145.148.sslip.io`
- Shopify Bridge: `https://shopify-bridge-staging.46.224.145.148.sslip.io`
- Runtime: `https://loomai-runtime.46.224.145.148.sslip.io`
- Ecommerce app: `https://loomai-ecommerce-store.46.224.145.148.sslip.io`

### 2.2 Production

Production is not the current deployment target for new development.

- Coolify dashboard: `http://46.225.162.106:8000`
- SSH host: `46.225.162.106`
- SSH user: `loomops`
- Platform backend: `https://loomai-platform-backend.46.225.162.106.sslip.io`
- Platform UI: `https://loomai-platform-ui.46.225.162.106.sslip.io`
- Partner UI: `https://loomai-partner-ui.46.225.162.106.sslip.io`
- Shopify Bridge: `https://loomai-shopify-bridge-prod.46.225.162.106.sslip.io`
- Runtime: `https://loomai-runtime.46.225.162.106.sslip.io`
- Ecommerce app: `https://loomai-ecommerce-store.46.225.162.106.sslip.io`

### 2.3 Platform Target Profiles

Platform stores Coolify deployment targets in `deployment_target_profiles`.

Customer runtimes and Platform-managed product services use the same target-profile table, but product services are additionally gated by `platform_services_allowed`.

Current intended posture:

- `dtp-coolify-staging`: active, default for runtime, default for restartable services, and allowed for Platform-managed product services.
- `dtp-coolify-production`: active and allowed for explicit Platform-managed product-service placement, but not a default runtime or restartable-services target.

This keeps staging as the implicit managed-service target while allowing production records such as `mcp-execution-gateway-production` or `shopify-bridge-production` to be created with `targetProfileId=dtp-coolify-production` when production rollout is explicitly requested.

## 3. Local Access Material

### 3.1 SSH

Local SSH key paths:

- private key: `/Users/mahmoudashraf/.ssh/loom_coolify_hetzner_ed25519`
- public key: `/Users/mahmoudashraf/.ssh/loom_coolify_hetzner_ed25519.pub`

Expected private key mode:

```bash
chmod 600 ~/.ssh/loom_coolify_hetzner_ed25519
```

SSH commands:

```bash
ssh -i ~/.ssh/loom_coolify_hetzner_ed25519 loomops@46.224.145.148
ssh -i ~/.ssh/loom_coolify_hetzner_ed25519 loomops@46.225.162.106
```

The raw private key body is intentionally not duplicated in this tracked guide. If the local key is missing, use the ignored private handoff or rotate host access deliberately.

### 3.2 Coolify Web Login

Coolify admin login values live in:

```bash
/tmp/coolify_admin_credentials.env
```

Load them locally without printing values:

```bash
set -a
source /tmp/coolify_admin_credentials.env
set +a
```

Expected variables:

- `COOLIFY_STAGING_ADMIN_EMAIL`
- `COOLIFY_STAGING_ADMIN_PASSWORD`
- `COOLIFY_PRODUCTION_ADMIN_EMAIL`
- `COOLIFY_PRODUCTION_ADMIN_PASSWORD`

### 3.3 Coolify API Tokens

Coolify API token values live in:

```bash
/tmp/coolify_api_tokens.env
```

Load them locally without printing values:

```bash
set -a
source /tmp/coolify_api_tokens.env
set +a
```

Expected variables:

- `COOLIFY_STAGING_BASE_URL`
- `COOLIFY_STAGING_API_TOKEN`
- `COOLIFY_PRODUCTION_BASE_URL`
- `COOLIFY_PRODUCTION_API_TOKEN`

Token values contain shell-special characters, so the local env file must quote them.

## 4. Read-Only Health Checks

Staging:

```bash
curl -fsS https://loomai-platform-backend.46.224.145.148.sslip.io/actuator/health
curl -fsS https://loomai-platform-ui.46.224.145.148.sslip.io/health
curl -fsS https://loomai-partner-ui.46.224.145.148.sslip.io/health
curl -fsS https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io/actuator/health
curl -fsS https://loomai-runtime.46.224.145.148.sslip.io/actuator/health
curl -fsS https://loomai-ecommerce-store.46.224.145.148.sslip.io/health
```

Production read-only checks:

```bash
curl -fsS https://loomai-platform-backend.46.225.162.106.sslip.io/actuator/health
curl -fsS https://loomai-platform-ui.46.225.162.106.sslip.io/health
curl -fsS https://loomai-partner-ui.46.225.162.106.sslip.io/health
curl -fsS https://loomai-shopify-bridge-prod.46.225.162.106.sslip.io/actuator/health
```

## 5. Coolify API Verification

Verify API tokens without printing them:

```bash
set -a
source /tmp/coolify_api_tokens.env
set +a

curl -fsS -H "Authorization: Bearer $COOLIFY_STAGING_API_TOKEN" \
  "$COOLIFY_STAGING_BASE_URL/api/v1/version"

curl -fsS -H "Authorization: Bearer $COOLIFY_PRODUCTION_API_TOKEN" \
  "$COOLIFY_PRODUCTION_BASE_URL/api/v1/version"
```

Run the provider verification script:

```bash
bash scripts/verify-coolify-provider.sh
```

By default this performs non-destructive API checks. Only set these flags when intentional:

- `COOLIFY_STRICT_APPLICATION_SMOKE=true`
- `COOLIFY_PUBLIC_GIT_SMOKE=true`
- `COOLIFY_KEEP_SMOKE_APP=true`

## 6. Staging Deployment Workflow

Use this workflow for Plan 009 and other active development:

1. Commit and push the branch that staging tracks.
2. Redeploy only staging Coolify apps that need the change.
3. Wait for Coolify deployment success.
4. Check public health endpoints.
5. Run product-specific live verification against staging URLs.
6. Inspect Coolify app env/readback only through the API or dashboard; never paste secret values.
7. Record outcomes in `CODEX_WORKING_CONTEXT.md`.

For Plan 009 Shopify MCP-first live verification, staging needs:

- Platform backend redeployed so the latest Flyway migration and Marketplace compiler behavior are live
- Shopify Bridge staging redeployed so the MCP client/adapter code is live
- Shopify staging store MCP endpoint reachable at `https://shopping-companion-test.myshopify.com/api/mcp`
- Bridge action call verified through the deployed staging Bridge, not only through local unit tests

Production must not be redeployed for this workflow unless explicitly requested.

### 6.1 Provider-Neutral Product Service Images

Platform-managed product services must use provider-neutral Dockerfile paths for new deployments:

- Shopify Bridge: `product-services/shopify-bridge-service/deploy/container/Dockerfile`
- MCP Execution Gateway: `product-services/mcp-execution-gateway-service/deploy/container/Dockerfile`

Coolify application `dockerfile_location` values should point to these paths with a leading slash because Coolify builds from the monorepo root. Railway can use the same paths. The old `deploy/railway/Dockerfile` paths are compatibility paths for existing Railway projects only.

Do not configure product-service secrets as build-time variables. Shopify credentials, webhook secrets, Platform service keys, MCP gateway keys, and downstream MCP credentials must be runtime environment variables in Coolify or Railway.

## 7. Common Coolify Operations

### 7.1 List Applications

```bash
set -a
source /tmp/coolify_api_tokens.env
set +a

curl -fsS -H "Authorization: Bearer $COOLIFY_STAGING_API_TOKEN" \
  "$COOLIFY_STAGING_BASE_URL/api/v1/applications"
```

Use `jq` locally for filtering, but avoid writing raw env payloads with secrets into tracked files.

### 7.2 Inspect One Application

```bash
curl -fsS -H "Authorization: Bearer $COOLIFY_STAGING_API_TOKEN" \
  "$COOLIFY_STAGING_BASE_URL/api/v1/applications/<application-uuid>"
```

### 7.3 Start Or Redeploy One Staging Application

Only do this for staging unless production was explicitly approved:

```bash
curl -fsS -H "Authorization: Bearer $COOLIFY_STAGING_API_TOKEN" \
  "$COOLIFY_STAGING_BASE_URL/api/v1/applications/<application-uuid>/start?force=true&instant_deploy=true"
```

### 7.4 Read Logs

```bash
curl -fsS -H "Authorization: Bearer $COOLIFY_STAGING_API_TOKEN" \
  "$COOLIFY_STAGING_BASE_URL/api/v1/applications/<application-uuid>/logs?lines=100"
```

Review logs for secret leakage before copying excerpts anywhere.

## 8. Password Rotation

Use password rotation when:

- a password was pasted into chat
- a token was exposed in command output
- a secret file was copied into an untrusted location
- a future session cannot recover a working login but still has host SSH

Rotation is performed on the host through the Coolify container, not through tracked code.

Secret-safe command shape:

```bash
sudo docker exec \
  -e COOLIFY_RESET_EMAIL=<admin-email> \
  -e COOLIFY_RESET_PASSWORD=<new-password> \
  coolify \
  php artisan tinker --execute='
    session(["currentTeam" => App\Models\Team::find(0)]);
    $user = App\Models\User::where("email", getenv("COOLIFY_RESET_EMAIL"))->firstOrFail();
    $user->password = Illuminate\Support\Facades\Hash::make(getenv("COOLIFY_RESET_PASSWORD"));
    $user->save();
    echo "reset-ok";
  '
```

After rotating:

1. Update `/tmp/coolify_admin_credentials.env`.
2. Set mode `600`.
3. Update the ignored private handoff.
4. Do not update this tracked guide with raw values.

## 9. API Token Rotation

Create fresh API tokens through the Coolify container:

```bash
sudo docker exec \
  -e COOLIFY_TOKEN_EMAIL=<admin-email> \
  coolify \
  php artisan tinker --execute='
    session(["currentTeam" => App\Models\Team::find(0)]);
    $user = App\Models\User::where("email", getenv("COOLIFY_TOKEN_EMAIL"))->firstOrFail();
    echo $user->createToken("loom-platform-operator-YYYYMMDD", ["root"])->plainTextToken;
  '
```

After rotating:

1. Update `/tmp/coolify_api_tokens.env`.
2. Quote token values in the env file because Coolify tokens contain `|`.
3. Set mode `600`.
4. Verify `/api/v1/version` for staging and production as appropriate.
5. Update the ignored private handoff.

## 10. Hetzner Host Notes

Coolify runs on Hetzner hosts. Host-level work should use SSH and be kept minimal:

- inspect Docker/container state
- inspect Coolify failed jobs
- repair host ACLs when Coolify cannot write generated resource directories
- check disk usage and prune Docker build cache when safe

Useful read-only commands:

```bash
ssh -i ~/.ssh/loom_coolify_hetzner_ed25519 loomops@46.224.145.148 'docker ps'
ssh -i ~/.ssh/loom_coolify_hetzner_ed25519 loomops@46.224.145.148 'df -h'
ssh -i ~/.ssh/loom_coolify_hetzner_ed25519 loomops@46.224.145.148 'sudo docker logs --tail=100 coolify'
```

Avoid direct container edits except for documented operational recovery. Prefer Coolify API/app env changes for application configuration.

## 11. Documentation Rules

Tracked docs may contain:

- public URLs
- host IPs
- SSH key paths
- env var names
- command shapes
- verification steps

Tracked docs must not contain:

- raw passwords
- raw API tokens
- private key bodies
- database connection strings with credentials
- signed artifact URLs with live query signatures

Ignored private docs may contain live credentials when the owner explicitly requests it, but they must remain gitignored and mode `600`.
