# Platform Verification Restart Guide

Use this guide when a future LLM session needs to restart platform verification work from scratch.

This guide is the verification runbook for:

- flow
- script order
- credential classes
- required environment variables
- current live defaults
- operational recovery steps
- common traps that caused real failures in the current sessions

This file is safe to commit.
Do not put raw secrets here.
Use the private handoff for live values.

Related references:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_RESOURCES_MAP.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
- `Final_Documentation/Development_Guides/GITHUB_ACTIONS_VERIFICATION_SUITE_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_DESIGN_PARTNER_ROLLOUT_CHECKLIST.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_APP_REVIEW_GUIDE.md`

## 1. First Principles

Keep these verification layers separate:

1. local code regression
2. platform-owned release suite
3. canonical rollout readiness
4. direct managed-provider verification
5. platform admin regression
6. deployment-level hosted verification from repo scripts
7. full umbrella suite

These layers can disagree.

Examples:

- a release can be healthy while an older stored verification run still shows failure
- a deployment can pass direct repo verification while the canonical rollout inventory is blocked by stale release evidence
- a runner registration can be active while the live runner session is dead

Do not stop at the first green signal.
Close the live operational issue and then refresh verification evidence.

### 1.1 UI-first direction

The strategic direction is to move release verification into the platform control plane and away from GitHub Actions.

Current UI path:

- deployment-scoped verification:
  - `/verification`
- fleet and release-suite orchestration:
  - `/verification-ops`

Architecture reference:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_UI_RELEASE_VERIFICATION_ARCHITECTURE.md`

Use the platform release suite as the primary human-operated gate.
Use repo scripts for direct diagnosis and recovery.
Use GitHub Actions only as secondary confirmation while the broader CI estate is being retired.

## 2. Where Real Credentials Live

Use the private handoff file for current live values:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

Important sections in that private file:

- `6.1 Platform login`
- `6.2 Platform / deployment shared keys`
- `6.3 Railway / platform service env values`
- `6.5 Vendor keys`
- `7.4 Shopify API`

Do not copy raw secrets into committed docs.

## 3. Credential Classes You Need

### 3.1 Base platform auth

Required for almost every live script:

- `PLATFORM_BASE_URL`
- one auth mode:
  - `PLATFORM_API_KEY`
  - or `PLATFORM_LOGIN_EMAIL` and `PLATFORM_LOGIN_PASSWORD`

### 3.2 Deployment verification auth

Required for deployment verification wrappers:

- `APP_ADMIN_API_KEY`

Without this, `run-platform-state-verification-suite.sh` will stop before deployment checks.

### 3.3 UI verification

Required only for platform admin UI checks:

- `PLATFORM_UI_BASE_URL`

### 3.4 Managed provider verification

Required for direct vendor checks:

- `OPENAI_API_KEY`
- `PINECONE_API_KEY`
- `QDRANT_API_KEY`
- `QDRANT_CLOUD_MANAGEMENT_API_KEY`
- `ZILLIZ_CLOUD_API_KEY`
- `WEAVIATE_API_KEY`
- `WEAVIATE_HOST`

### 3.5 Railway and DB access

Needed only when debugging provider provisioning, Railway state, or platform internals directly:

- `RAILWAY_API_TOKEN`
- `RAILWAY_WORKSPACE_ID`
- `PLATFORM_DB_URL`
- `PLATFORM_DB_USERNAME`
- `PLATFORM_DB_PASSWORD`

### 3.6 Shopify verification extras

Required for Shopify live verification:

- `SHOPIFY_BRIDGE_BASE_URL`
- `SHOP_DOMAIN`

Optional for deeper Shopify verification coverage:

- `SHOPIFY_BRIDGE_ADMIN_API_KEY`
- `SHOPIFY_ADMIN_ACCESS_TOKEN`
- `SHOPIFY_MERCHANT_AUTHORIZATION`
- `SHOPIFY_EMBEDDED_HOST`

Operational notes:

- baseline non-destructive Shopify verification can run without the optional values
- uninstall verification is destructive and should only target a disposable shop mapping
- the private handoff is the source of truth for current live Shopify credentials and app values
- for GitHub Actions, keep non-secret Shopify config in variables and keep only keys, bearer tokens, and passwords in secrets

## 4. Current Live Defaults To Start From

These are the current known-good non-secret defaults as of `2026-04-21`.

- platform base URL:
  - `https://ai-fabric-framework-production-324f.up.railway.app`
- platform login email:
  - `admin@gmail.com`
- platform UI base URL:
  - `https://platform-ui-production-00e3.up.railway.app`
- Shopify bridge base URL:
  - `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app`
- Shopify companion shop domain:
  - `shopping-companion-test.myshopify.com`
- Shopify product service ref:
  - `shopify-bridge-prod`
- Shopify disposable uninstall shop domain:
  - empty by default; set explicitly only for destructive uninstall verification
- Shopify embedded host:
  - empty by default; set only when merchant-session verification is needed
- current Weaviate host:
  - `weaviate-external-verify-dev.up.railway.app`

Current canonical deployment ids were:

- ecommerce:
  - `dep-0c725f3e`
- marketplace runtime:
  - `dep-6d13b01c`
- qdrant:
  - `dep-7786c409`
- pinecone:
  - `dep-a85f815f`
- milvus:
  - `dep-11c2fdce`
- weaviate:
  - `dep-713bb33e`

Treat those ids as reference only.
Do not hardcode them into new logic.
Resolve them live through rollout inventory first.

## 5. Minimal Shell Bootstrap

Use this as the starting shell shape:

```bash
export PLATFORM_BASE_URL="https://ai-fabric-framework-production-324f.up.railway.app"
export PLATFORM_UI_BASE_URL="https://platform-ui-production-00e3.up.railway.app"
export PLATFORM_LOGIN_EMAIL="..."
export PLATFORM_LOGIN_PASSWORD="..."
export APP_ADMIN_API_KEY="..."

export OPENAI_API_KEY="..."
export PINECONE_API_KEY="..."
export QDRANT_API_KEY="..."
export QDRANT_CLOUD_MANAGEMENT_API_KEY="..."
export ZILLIZ_CLOUD_API_KEY="..."
export WEAVIATE_API_KEY="..."
export WEAVIATE_HOST="weaviate-external-verify-dev.up.railway.app"
```

If platform API-key auth is enabled for the target environment, you can replace login envs with `PLATFORM_API_KEY`.

## 6. Script Map

### 6.0 Platform-owned release suite

Control-plane suite keys:

- `full-platform-release-readiness`
- `canonical-release-readiness`
- `platform-admin-live-regression`
- `managed-vector-provider-verification`
- `marketplace-install-flow`
- `shopify-companion-verification`

Primary release gate:

- `full-platform-release-readiness`

Release-gate summary:

- `/verification-ops` now also exposes a fresh-pass summary for the full suite
- use it as the top-level operator signal before treating the platform as release-ready
- expected statuses:
  - `READY`
  - `RUNNING`
  - `FAILED`
  - `STALE`
  - `MISSING`

Current ordered stages:

1. shared inference service health
2. platform admin live regression
3. canonical rollout inventory
4. managed vector provider verification
5. marketplace install flow
6. Shopify Companion verification
7. marketplace hosted verification
8. ecommerce hosted verification
9. qdrant hosted verification
10. pinecone hosted verification
11. milvus hosted verification
12. weaviate hosted verification

Important runtime behavior:

- with `allowControlPlaneRepair=true`, the suite may perform bounded repair only:
  - shared inference reconcile
  - canonical rollout recreation or refresh before hosted verification
  - governed vectorization bootstrap or reindex before hosted verification when a canonical deployment is only blocked by sync drift

### 6.1 Rollout resolution

Script:

- `scripts/resolve-verification-rollouts.sh`

Purpose:

- resolve canonical deployment ids
- tell you whether canonical rollouts are actually verification-ready
- optionally recreate missing or unready rollouts

Use this first.

### 6.2 Managed provider verification

Script:

- `scripts/verify-managed-vector-providers.sh`

Purpose:

- verify Pinecone, Qdrant Cloud, Zilliz Cloud, and Weaviate directly
- prove vendor access separately from deployment/runtime status

This script creates temporary provider-side resources for some providers and cleans them up.

### 6.3 Platform admin regression

Script:

- `scripts/verify-platform-admin-regression.sh`

Purpose:

- platform auth/session checks
- user directory checks
- deployment assignment checks
- async deletion flow
- deployment override flow
- consumer resolution flow
- inference-service UI and admin checks

This script creates temporary platform objects and cleans them up.

### 6.4 Deployment wrapper

Script:

- `scripts/run-platform-deployment-verification.sh`

Purpose:

- fetch hosted verification context from the platform
- run the correct deployment script for a specific deployment/profile

Profiles:

- `ecommerce`
- `marketplace-runtime`
- `vector`

### 6.5 Underlying deployment scripts

Scripts:

- `scripts/verify-ecommerce-deployment.sh`
- `scripts/verify-vector-deployment.sh`

Purpose:

- runtime health
- connector admin overview
- runtime-backed operational checks
- platform-side source-of-truth checks
- vectorization checks
- release evidence checks
- provider connectivity checks

### 6.6 Marketplace install-flow proof

Script:

- `scripts/verify-marketplace-install-flow.sh`

Purpose:

- create a fresh marketplace validation deployment
- install template, action, data, and inference plugins
- publish and apply
- prove live multi-source retrieval

This script creates a temporary deployment and cleans it up unless `KEEP_DEPLOYMENT=true`.

### 6.7 Full umbrella suite

Script:

- `scripts/run-platform-state-verification-suite.sh`

Purpose:

- local code checks
- marketplace install-flow
- admin regression
- deployment verification
- managed provider verification

This is the closest thing to a one-command full-state run.

### 6.8 Shopify verification

Scripts:

- `scripts/verify-shopify-companion.sh`
- `scripts/verify-shopify-companion-uninstall.sh`
- `scripts/run-shopify-companion-rollout.sh`

GitHub Actions workflow:

- `.github/workflows/shopify-companion-verification.yml`

Purpose:

- verify Shopify Companion live operator surfaces
- progress a Shopify store through bootstrap / preflight / go-live
- verify uninstall cleanup on a disposable store mapping
- verify Shopify indexing and live-update operator surfaces without mutating live state

Primary supporting docs:

- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUPPORT_RUNBOOK.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_DESIGN_PARTNER_ROLLOUT_CHECKLIST.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_APP_REVIEW_GUIDE.md`

Safety rules:

- use workflow/script mode `verify` first
- use `rollout` only when intentionally advancing store state
- use `uninstall_verify` only with an explicitly disposable shop and explicit destructive confirmation

Current read-only checks in `scripts/verify-shopify-companion.sh`:

- platform store vectorization summary
- live-update trigger policy visibility
- effective indexed field visibility
- automation queue / dead-letter visibility
- recent vectorization event visibility
- bridge admin vectorization source-page reachability when bridge admin auth is configured

## 7. Recommended Order

### 7.1 If you did not change code

Use this order:

1. run `full-platform-release-readiness` from `/verification-ops`
2. if it fails, use direct repo scripts to isolate the failing stage
3. rerun the full platform suite after the live repair is complete

### 7.2 If you changed code

Use this order:

1. targeted local tests for the touched code
2. `git diff --check`
3. resolve canonical rollouts if you need direct script diagnosis
4. direct live scripts for the changed surface
5. `full-platform-release-readiness` last

### 7.3 Canonical commands

Resolve rollouts:

```bash
env \
  PLATFORM_BASE_URL="$PLATFORM_BASE_URL" \
  PLATFORM_LOGIN_EMAIL="$PLATFORM_LOGIN_EMAIL" \
  PLATFORM_LOGIN_PASSWORD="$PLATFORM_LOGIN_PASSWORD" \
  REQUIRED_ROLLOUT_KEYS="ecommerce,marketplace,qdrant,pinecone,milvus,weaviate" \
  ALLOW_ROLLOUT_MUTATION="false" \
  WAIT_FOR_VERIFICATION_READY="true" \
  bash scripts/resolve-verification-rollouts.sh
```

Managed providers:

```bash
bash scripts/verify-managed-vector-providers.sh
```

Platform admin regression:

```bash
env \
  PLATFORM_BASE_URL="$PLATFORM_BASE_URL" \
  PLATFORM_UI_BASE_URL="$PLATFORM_UI_BASE_URL" \
  PLATFORM_LOGIN_EMAIL="$PLATFORM_LOGIN_EMAIL" \
  PLATFORM_LOGIN_PASSWORD="$PLATFORM_LOGIN_PASSWORD" \
  ADMIN_TARGET_DEPLOYMENT_ID="$ECOMMERCE_DEPLOYMENT_ID" \
  VERIFY_INFERENCE_SERVICE_UI="true" \
  VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION="false" \
  bash scripts/verify-platform-admin-regression.sh
```

Single deployment verification:

```bash
env \
  PLATFORM_BASE_URL="$PLATFORM_BASE_URL" \
  PLATFORM_LOGIN_EMAIL="$PLATFORM_LOGIN_EMAIL" \
  PLATFORM_LOGIN_PASSWORD="$PLATFORM_LOGIN_PASSWORD" \
  PLATFORM_DEPLOYMENT_ID="dep-xxxxxxxx" \
  VERIFICATION_PROFILE="ecommerce" \
  VERIFY_WRITE="false" \
  APP_ADMIN_API_KEY="$APP_ADMIN_API_KEY" \
  bash scripts/run-platform-deployment-verification.sh
```

Marketplace install-flow:

```bash
env \
  PLATFORM_BASE_URL="$PLATFORM_BASE_URL" \
  PLATFORM_LOGIN_EMAIL="$PLATFORM_LOGIN_EMAIL" \
  PLATFORM_LOGIN_PASSWORD="$PLATFORM_LOGIN_PASSWORD" \
  KEEP_DEPLOYMENT="false" \
  bash scripts/verify-marketplace-install-flow.sh
```

Full umbrella suite:

```bash
env \
  PLATFORM_BASE_URL="$PLATFORM_BASE_URL" \
  PLATFORM_UI_BASE_URL="$PLATFORM_UI_BASE_URL" \
  PLATFORM_LOGIN_EMAIL="$PLATFORM_LOGIN_EMAIL" \
  PLATFORM_LOGIN_PASSWORD="$PLATFORM_LOGIN_PASSWORD" \
  APP_ADMIN_API_KEY="$APP_ADMIN_API_KEY" \
  OPENAI_API_KEY="$OPENAI_API_KEY" \
  PINECONE_API_KEY="$PINECONE_API_KEY" \
  QDRANT_API_KEY="$QDRANT_API_KEY" \
  QDRANT_CLOUD_MANAGEMENT_API_KEY="$QDRANT_CLOUD_MANAGEMENT_API_KEY" \
  ZILLIZ_CLOUD_API_KEY="$ZILLIZ_CLOUD_API_KEY" \
  WEAVIATE_API_KEY="$WEAVIATE_API_KEY" \
  WEAVIATE_HOST="$WEAVIATE_HOST" \
  RUN_MARKETPLACE_INSTALL_FLOW_CHECKS="true" \
  VERIFY_WRITE="false" \
  bash scripts/run-platform-state-verification-suite.sh
```

## 8. Read-Only Vs Write

Default posture:

- `VERIFY_WRITE=false`

Use write-backed verification only when intentional.

Important rules:

- platform-hosted verification is expected to be read-only by default
- GitHub Actions deployment verification is expected to be read-only by default
- marketplace runtime verification in normal CI should not do live write probes
- direct active write probes are acceptable only for deliberate non-prod proof flows

## 9. Important Operational Considerations

### 9.1 Resolve rollouts first

If rollout resolution is stuck, the problem is usually real, not a UI glitch.

Typical cause:

- latest canonical release is not `APPLIED_VERIFIED`
- or the rollout readiness check sees runner/provider drift

### 9.2 Prefer governed remediation

If a live deployment is unhealthy, prefer:

- `REDEPLOY_ACTIVE_VERSION`

before trying ad hoc service restarts.

### 9.3 Runner token rotation revokes sessions

This matters.

Rotating a vectorization runner token revokes existing sessions.
If the service does not reconnect with the new token, deployment verification will fail runner-session checks even though registration stays `ACTIVE`.

Operational fix:

- redeploy the active version so the managed runner service picks up the new managed secret and reconnects

### 9.4 Stored verification evidence can lag reality

It is possible for:

- runtime to be healthy
- direct repo verification to pass
- older release evidence to still show failure

When that happens:

- rerun verification evidence through the platform
- do not stop at “the deployment looks healthy”

### 9.5 The provider suite needs real vendor envs

`run-platform-state-verification-suite.sh` does not inject provider credentials for you.

If you omit them, the suite will pass earlier steps and then fail at the final managed-provider stage.

### 9.6 `APP_ADMIN_API_KEY` is still required

Even when using platform session login, the umbrella suite still expects `APP_ADMIN_API_KEY` for deployment checks.

### 9.7 Weaviate default host changed

The stale old host should not be reused.

Current default:

- `weaviate-external-verify-dev.up.railway.app`

## 10. Real Failure Patterns Seen In This Session

### 10.1 Marketplace runner provisioning smoke failed

Observed on:

- `dep-6d13b01c`

Shape:

- registration active
- token valid after rotation
- runner instance id present
- but `lastSessionExpiresAt` was stale and verification failed

Cause:

- earlier token rotation revoked runner sessions
- managed runner service had not reconnected yet

Fix:

- trigger platform remediation:
  - `REDEPLOY_ACTIVE_VERSION`
- wait for new release to reach `APPLIED_VERIFIED`
- confirm fresh runner heartbeat
- rerun deployment verification

### 10.2 Weaviate canonical rollout was blocked

Observed on:

- canonical Weaviate rollout

Cause:

- old external host was dead
- provider connectivity failed

Fix that was applied:

- move the canonical Weaviate host to the live Railway-hosted external Weaviate endpoint
- refresh rollout
- rerun verification

### 10.3 Umbrella suite failed only at the provider tail

Cause:

- missing provider envs in the shell invocation

Fix:

- rerun the suite with vendor envs
- or rerun only the managed-provider phase with those envs

## 11. What Good Looks Like

Before ending a verification session, confirm:

- rollout inventory shows all required canonical rollouts as ready
- managed provider suite passes
- platform admin regression passes
- canonical deployment wrapper passes for:
  - ecommerce
  - marketplace runtime
  - qdrant
  - pinecone
  - milvus
  - weaviate
- marketplace install-flow passes on a fresh temp deployment
- umbrella suite passes, or if one invocation failed due missing envs, the failed stage is rerun cleanly with the correct envs

## 12. Session Hand-Off Checklist

If another LLM session must continue verification work, leave these facts clearly:

1. which live scripts were run
2. which deployments were verified
3. which release ids were the final passing releases
4. whether any remediation action was triggered
5. whether any token rotation was performed
6. whether the worktree is clean
7. where the real secrets are stored:
   - the private handoff file

Do not leave the next session guessing whether a failure is still live or was already remediated.
