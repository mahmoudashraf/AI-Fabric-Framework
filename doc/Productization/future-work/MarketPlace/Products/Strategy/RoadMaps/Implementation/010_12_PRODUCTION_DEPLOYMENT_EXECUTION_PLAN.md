# 010.12 Production Deployment Execution Plan

Status: in execution, created 2026-05-28  
Primary target: Loom Companion / Platform-managed product services production rollout  
Production target profile: `dtp-coolify-production`  
Related plans: 010, 010.8, 010.10, 010.11, 007, 009.3

## Purpose

Define the exact production deployment sequence LoomAI should follow before moving Shopify Companion or external-customer managed deployments from staging to production.

This plan does not mark production as ready. It turns the remaining production gates into an executable checklist with evidence requirements, rollback expectations, and owner-safe boundaries.

## Release Principles

- Staging remains the default deployment target. Production must be explicit through `targetProfileId=dtp-coolify-production`.
- Production promotion must go through Platform-managed operations. Partner, merchant, and operator UIs must not call Coolify or provider APIs directly.
- No production secret is committed, printed, copied into public docs, or exposed to browser-side code.
- Production config must be reproducible from Platform-managed deployment records, Marketplace/plugin config, Platform secrets, and Coolify runtime env rows.
- Production failures must leave staging untouched.
- A release decision requires fresh evidence, not stale May 2026 gate history.
- Public App Store/self-service claims stay blocked until production promotion, rollback, support, Customer Account, Checkout, and packaging gates pass.

## Deployment Scope

Production deployment can include these service families:

- Platform backend and Platform UI.
- Partner UI and merchant launch workspace.
- Shopify Bridge production service.
- MCP Execution Gateway production service.
- Runtime, REST connector, and vectorization runner for the production deployment.
- Product-specific deployment records for Shopify Companion and external customers such as ProdUS.
- DNS, app URLs, Shopify app redirect URLs, webhook URLs, and customer-facing widget/script URLs.

Out of scope for the first production proof:

- Broad public Shopify App Store launch.
- Enabling unproven Checkout MCP or Customer Account MCP public claims.
- Moving unrelated staging stores/customers to production.
- Manual Coolify-only deployment paths that bypass Platform state.

## Shopify App Environment Boundary

Staging and production must be treated as separate Shopify app environments.

Controlled technical proof may temporarily use the existing development/test Shopify app when the goal is to prove Platform/Coolify production deployment mechanics. Real merchant production installs must use a dedicated production Shopify Partner app.

Production Shopify app requirements:

- App URL points only at the production Bridge domain, for example `https://shopify-bridge.loomai.pro`.
- OAuth redirect URLs point only at production callback URLs.
- App proxy URLs, webhook URLs, Customer Account redirect URLs, and Checkout redirect URLs point only at production domains.
- Production Shopify app client id, client secret, webhook secret, Customer Account credentials, and Checkout credentials live only in production Platform/Coolify secrets.
- Staging app credentials remain isolated from production and must not be reused for real merchant production.
- Protected customer data, public listing review, Customer Account access, and Checkout access are requested and documented against the production app claim set.

Reasoning:

- Shopify app URLs and redirect URLs are app-level configuration. Reusing one app for staging and production creates a direct risk that staging callback changes break production installs.
- Separate apps keep OAuth sessions, webhook registrations, Customer Account redirect posture, protected-data review, and App Store/private listing state auditable per environment.
- The production app boundary also keeps production secrets out of staging deploys and staging secrets out of production deploys.

Gate impact:

- Existing development/test Shopify app usage is acceptable for the controlled production proof already recorded in Gate 6/Gate 9.
- Public/private real merchant production launch is blocked until the production Shopify app exists, is configured with production domains, and its production secrets are installed in the production environment.

## Gate 0: Freeze Release Candidate

1. Select the exact Git branch and commit to promote.
   - Expected branch for current work: `Platform-V10`.
   - Record full commit SHA.
2. Confirm no uncommitted production-impacting work remains.
   - `git status --short`
   - `git diff --check`
3. Confirm GitHub CI is green for the release commit.
4. Record impacted components:
   - Platform backend/UI
   - Partner UI
   - Shopify Bridge
   - MCP Gateway
   - Runtime/provider modules
   - Connector/vectorization services
5. Create a production evidence directory:

```bash
RUN_DIR="/tmp/loomai-production-promotion-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$RUN_DIR"
```

Required evidence:

- release branch
- release commit
- CI status
- local verification command results
- list of service records and target profiles to promote

## Gate 1: Fresh Staging Release Proof

Production must not start from stale staging evidence.

Run or confirm a fresh hosted/full release gate on the current deployed staging branch:

```bash
bash scripts/verify-partner-enablement-live.sh
bash scripts/verify-shopify-companion.sh
bash scripts/verify-shopify-companion-answer-quality-repeats.sh
```

Also run the Platform-hosted full release suite from the operator surface or Platform API and record:

- suite run id
- status
- completion timestamp
- freshness expiry
- evidence bundle path
- failed/warning stages

Minimum pass criteria:

- Platform release gate reports `READY`.
- Shopify Companion verification passes.
- Partner Enablement live gate passes.
- Answer-quality repeat gate passes.
- Debug/RAG evidence still returns canonical `ragResponse.documents` when RAG is expected.
- Support/package posture matches storefront bootstrap flags.
- Current runtime branch and Coolify app source branch match the release commit/branch.

If ProdUS or another external customer is included in the production release, add:

- `/api/chat/me/query-once` smoke against staging runtime.
- transient file URL allowlist check.
- `documentUsage` proof for a real short-lived test file, or an explicit skip reason if no file analysis is in scope.

### Current Staging Gate Execution - 2026-05-28

Fresh hosted staging proof was rerun against the current staging services.

Passed checks:

- `bash -n scripts/verify-partner-enablement-live.sh`
- `bash -n scripts/verify-shopify-companion.sh`
- `bash -n scripts/verify-shopify-companion-answer-quality-repeats.sh`
- `scripts/verify-partner-enablement-live.sh` against the hosted Platform/Partner UI/Shopify staging stack
- `scripts/verify-shopify-companion.sh` against `shopping-companion-test.myshopify.com`
- `scripts/verify-shopify-companion-answer-quality-repeats.sh` with `ANSWER_QUALITY_REPEAT_COUNT=3`

Evidence:

- Answer-quality repeat bundle: `/tmp/shopify-answer-quality-20260528T090801Z-repeats`
- Repeat summary JSON: `/tmp/shopify-answer-quality-20260528T090801Z-repeats/repeat-summary.json`
- Repeat summary Markdown: `/tmp/shopify-answer-quality-20260528T090801Z-repeats/repeat-summary.md`
- Repeat decision: `PASS`
- Repeat runs: `3/3`
- Query pass rate: `20/20` in every repeat

Execution notes:

- The partner live gate passed, but intentionally skipped the actual production promotion mutation because `PARTNER_LIVE_PRODUCTION_PROMOTION_PROOF=true` was not set. That proof belongs to Gate 6.
- The Shopify Companion verification passed using the currently intended storefront surface set. `ai-search` is disabled on this staging store by product configuration, so it is not included in the expected enabled surface list.
- The live Elite package currently reports chat fallback enabled. That matches the current Max/chat storefront product direction and should not be treated as a stale May 2026 expectation failure.
- Customer Account owned-resource answers still correctly fail closed when no bound customer auth session is present.

## Gate 2: Production Target Preflight

Verify `dtp-coolify-production` is active, non-default, and explicitly allowed for production product-service placement.

Required checks:

- Platform target profile preflight passes.
- Coolify production API is reachable from Platform.
- Production Coolify project/environment exists.
- Production host has enough CPU, memory, disk, and Docker capacity.
- Production DNS/certificates are ready or an explicit temporary hostname is approved for the proof.
- Private Git/source access works for the production branch.
- Registry auth works if private images are used.
- Production database and vector-provider credentials are present as Platform secrets/Coolify runtime env values.
- No staging-only domains, callbacks, webhooks, or allowlists are present in production records.

Recommended Platform API checks:

```bash
curl -fsS "$PLATFORM_BASE_URL/api/deployment-provider/target-profiles/dtp-coolify-production/preflight" \
  -H "Authorization: Bearer $PLATFORM_ADMIN_TOKEN" \
  | tee "$RUN_DIR/dtp-coolify-production-preflight.json"
```

Do not proceed unless the preflight reports pass/ready.

## Gate 3: Hetzner Production Server Readiness

The production target profile preflight is necessary but not sufficient. Before any public production release, the underlying Hetzner/Coolify production host must be production-ready as an infrastructure target.

Production host baseline:

- production host: `46.225.162.106`
- SSH user: `loomops`
- Coolify dashboard/API: `http://46.225.162.106:8000`
- expected target profile: `dtp-coolify-production`
- production profile must remain non-default until the controlled promotion proof passes

Required host checks:

1. SSH access works for `loomops` using the managed operator key.
2. Root SSH login, password auth, and keyboard-interactive auth are disabled.
3. Host firewall and Hetzner Cloud firewall allow only required traffic:
   - public `80/tcp` and `443/tcp` for apps/proxy
   - restricted `22/tcp` for SSH
   - restricted `8000/tcp` for Coolify dashboard/API
   - no broad public production Coolify API exposure
4. Docker, Coolify, Coolify database, Redis, realtime, and proxy containers are healthy.
5. Coolify can validate the production server/destination/private key as reachable and usable.
6. Disk, Docker build cache, volume, CPU, and memory headroom are recorded before deployment.
7. Production DNS plan is explicit:
   - real production domains are preferred
   - `sslip.io` may be used only for a controlled proof, not public launch positioning
   - wildcard/runtime/app DNS records are documented
8. TLS certificate issuance is verified for every production-facing hostname.
9. Backups are configured before workloads:
   - Coolify dashboard database
   - `/data/coolify`
   - Coolify SSH keys
   - app volumes, if any
   - Platform/product databases
   - vector provider/index backup/export strategy where applicable
10. A backup restore rehearsal exists and is recent enough for the release decision.
11. Monitoring and alerting exist for:
   - host health
   - disk usage at 70/80/90 percent
   - Docker/Coolify service health
   - app health endpoints
   - backup failures
   - provider timeout/error spikes
12. Registry/private-source readiness is complete if any production service builds from private source or private images.
13. No temporary deployment files, dump files, migration env files, or raw secret artifacts remain on the host after migration/deploy operations.

Suggested read-only checks:

```bash
ssh -i ~/.ssh/loom_coolify_hetzner_ed25519 loomops@46.225.162.106 'hostname; uptime; df -h; free -h; sudo docker ps --format "table {{.Names}}\t{{.Status}}"'

curl -fsS -H "Authorization: Bearer $COOLIFY_PRODUCTION_API_TOKEN" \
  "$COOLIFY_PRODUCTION_BASE_URL/api/v1/version" \
  | tee "$RUN_DIR/coolify-production-version.json"
```

Suggested firewall/readiness evidence:

```bash
ssh -i ~/.ssh/loom_coolify_hetzner_ed25519 loomops@46.225.162.106 'sudo ufw status verbose'
```

Required evidence:

- SSH/hardening proof
- firewall proof
- Coolify API version proof
- Docker/Coolify container health proof
- disk/capacity proof
- DNS/TLS proof or approved temporary-domain exception
- backup configuration proof
- restore rehearsal proof
- monitoring/alerting proof
- private registry/source proof or explicit not-in-scope note

Production is not fully ready if any of these are missing:

- production Coolify API is broadly exposed to the internet
- no current backup/restore rehearsal exists
- no disk/capacity evidence exists
- production DNS/TLS is not decided
- private-source/registry auth is required but not configured
- app deployment requires manual SSH mutation outside Platform/Coolify records

### Current Production Infrastructure Snapshot - 2026-05-28

This snapshot records the current production Hetzner/Coolify state found during readiness inspection. It proves that a production target exists, but it does not mark production release-ready.

Existing production target:

- Host: `coolify-prod-01`
- Public IPv4: `46.225.162.106`
- SSH operator user: `loomops`
- Coolify dashboard/API: `http://46.225.162.106:8000`
- Private handover: `Final_Documentation/Development_Guides/LLM-guides/PRODUCTION_HETZNER_COOLIFY_HANDOFF_PRIVATE.md` is gitignored and mode `600`.
- Platform target profile: `dtp-coolify-production`
- Platform credential reference: `dpc-coolify-production`
- Coolify project/environment/server/destination are configured in the Platform target profile.
- Target profile is active, production-scoped, and non-default.
- `platformServicesAllowed=true`, so Platform-managed product services can be placed there only when production is explicitly selected.

Observed host state:

- SSH access with the managed Hetzner operator key works.
- Docker and Coolify core services are running.
- `coolify`, `coolify-db`, `coolify-redis`, `coolify-realtime`, `coolify-proxy`, and `coolify-sentinel` are healthy.
- Disk headroom is strong: root volume is about `150G`, with about `12G` used and `133G` available.
- Memory headroom is acceptable: about `15Gi` total with about `11Gi` available.
- SSH hardening is in place: root login disabled, password auth disabled, keyboard-interactive auth disabled, public-key auth enabled.
- UFW is active with public `80/tcp` and `443/tcp`, restricted `22/tcp`, and restricted `8000/tcp`.
- Existing Coolify backup/restore rehearsal artifacts exist under `/var/backups/loom-coolify`, but the observed backup sets are from `2026-05-01` and must be refreshed before release.

Observed production apps:

- `loomai-platform-backend` is deployed and `GET /actuator/health` returns `UP`.
- `loomai-platform-ui` is deployed and `GET /health` returns `UP`.
- `loomai-partner-ui` is deployed and `GET /health` returns `UP`.
- `loomai-shopify-bridge-prod` is deployed and `GET /actuator/health` returns `UP`.
- `loomai-runtime` is deployed and `GET /actuator/health` returns `UP`.
- `loomai-ecommerce-store` is deployed and `GET /actuator/health` returns `UP`.
- The old hostname `shopify-bridge-prod.46.225.162.106.sslip.io` returns `404`; current production references should use `loomai-shopify-bridge-prod.46.225.162.106.sslip.io`.

Resolved during 2026-05-28 gate execution:

- Platform staging backend preflight for `dtp-coolify-production` now passes after tightening the production Coolify API allowlists.
- Hetzner Cloud firewall `loom-coolify-production-platform-api-firewall` now allows `8000/tcp` only from `46.224.145.148/32` and `46.225.162.106/32`.
- Production host UFW now allows `8000/tcp` from `46.224.145.148` and `46.225.162.106`.
- The stale `52.52.45.183` Coolify API UFW rule was removed.
- Production Coolify backup/restore rehearsal was refreshed successfully at `/var/backups/loom-coolify/production-20260528T093631Z`.
- Release branch provider-neutral service defaults were repaired:
  - Runtime default Dockerfile path now points to `ai-infrastructure-module/ai-fabric-runtime/Dockerfile`.
  - REST connector default Dockerfile path now points to `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/Dockerfile`.
  - Vectorization runner, embedding worker, and shared Ollama service now have provider-neutral container Dockerfiles under `deploy/container`.
  - Flyway migration `V118__provider_neutral_coolify_runtime_defaults.sql` updates existing Coolify staging/production target profile runtime defaults away from `deploy/railway/Dockerfile`.
  - Commit `a46b0b36c` was pushed to `Platform-V10`.
  - Staging Platform backend redeploy `h7fs17pdlc2q4jbene72bbcy` finished successfully.
  - Live Platform target profile readback now shows both `dtp-coolify-staging` and `dtp-coolify-production` contain the provider-neutral runtime Dockerfile and do not contain `deploy/railway` in managed runtime defaults.
  - Production target preflight still passes after V118.
- Production core app source records were moved from `Platform-V8` to `Platform-V10` and provider-neutral Dockerfile paths where applicable.
- Production core app redeploys were triggered through Coolify API and finished successfully:
  - `loomai-platform-backend`: deployment `kw5k9p2s9umbkis9w9jjsqfn`
  - `loomai-platform-ui`: deployment `vgblizcy0c27dda7lt8535af`
  - `loomai-partner-ui`: deployment `j67mfpmyk14soqpml7rcs1em`
  - `loomai-runtime`: deployment `qsc8e27ktjrqimgf5kh8ekit`
  - `loomai-shopify-bridge-prod`: deployment `lxj6cj4nbkbmm2q883mggamq`
  - `loomai-ecommerce-store`: deployment `skudb39rx880pe8bd2pzbd6k`
- Production endpoint health after the `Platform-V10` redeploy returned HTTP `200`/`UP` for Platform backend, Platform UI, Partner UI, Runtime, Shopify Bridge production, and Ecommerce Store.
- Production target preflight still passes after the `Platform-V10` production redeploy.

Read-only evidence captured during 2026-05-28 gate execution:

- Evidence directory: `/tmp/loomai-production-readiness-20260528T091943Z`
- Platform target preflight: `/tmp/loomai-production-readiness-20260528T091943Z/dtp-coolify-production-preflight.json`
- Target profile snapshot: `/tmp/loomai-production-readiness-20260528T091943Z/target-profiles.json`
- Host capacity: `/tmp/loomai-production-readiness-20260528T091943Z/prod-host-capacity.txt`
- SSH hardening: `/tmp/loomai-production-readiness-20260528T091943Z/prod-ssh-hardening.txt`
- UFW firewall status: `/tmp/loomai-production-readiness-20260528T091943Z/prod-ufw-status.txt`
- Hetzner Cloud firewall status: `/tmp/loomai-production-readiness-20260528T091943Z/hetzner-production-platform-api-firewall.json`
- Docker/Coolify service status: `/tmp/loomai-production-readiness-20260528T091943Z/prod-docker-services.txt`
- Coolify API health from staging control plane: `/tmp/loomai-production-readiness-20260528T091943Z/prod-coolify-health-from-staging-host.txt`
- Production endpoint health: `/tmp/loomai-production-readiness-20260528T091943Z/prod-endpoint-health.txt`
- Existing backup snapshot: `/tmp/loomai-production-readiness-20260528T091943Z/prod-backup-snapshot.txt`
- Fresh backup/restore rehearsal: `/tmp/loomai-production-readiness-20260528T091943Z/prod-coolify-backup-restore-rehearsal.txt`
- Redacted Platform secret metadata: `/tmp/loomai-production-readiness-20260528T091943Z/platform-secret-summary.json`
- Redacted Platform secret counts: `/tmp/loomai-production-readiness-20260528T091943Z/platform-secret-summary-redacted-counts.json`
- Sanitized production Coolify app readback after source update: `/tmp/loomai-production-readiness-20260528T091943Z/prod-apps-readback-after-v10-redeploy-sanitized.json`
- Production deployment status evidence: `/tmp/loomai-production-readiness-20260528T091943Z/prod-deployment-*.json`
- Production health after `Platform-V10` redeploy: `/tmp/loomai-production-readiness-20260528T091943Z/prod-health-after-platform-v10-redeploy.txt`
- Production preflight after `Platform-V10` redeploy: `/tmp/loomai-production-readiness-20260528T091943Z/dtp-coolify-production-preflight-after-prod-v10-redeploy.json`

Secret/config metadata check:

- Platform secret catalog returned 41 secret definitions.
- 36 are present.
- No required Platform secret is currently missing.
- Secret values were not printed or exported as part of this evidence bundle.

Observed blockers before controlled production promotion:

- Production public endpoints still use `sslip.io`; this is acceptable for controlled proof only, not public launch positioning.
- Some Coolify app records report `running:unknown` even though their HTTP health endpoints are `UP`; configure or document Coolify health checks before final release.
- The controlled production promotion proof had not run at this point in the gate sequence. It was executed later in Gate 6 and Gate 9 below.

Next required infrastructure actions:

1. Decide production DNS/TLS posture: real production domains for launch, `sslip.io` only for proof.
2. Configure or explicitly document Coolify health checks for apps that report `running:unknown` despite healthy HTTP endpoints.
3. Keep Gate 6 and Gate 9 evidence fresh for the intended production release target.
4. Decide production DNS/TLS posture for public launch.
5. Configure or explicitly document Coolify health checks for apps that report `running:unknown` despite healthy HTTP endpoints.

Verification run for the provider-neutral default fix:

- `git diff --check`
- `mvn -f Platfrom/backend/pom.xml -q -Dtest=DeploymentTargetProfileMigrationTest test`
- `mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformManagedInferenceProvisioningServiceTest,RailwayProvisioningPlanServiceTest,CoolifyDeploymentProviderTest test`
- `mvn -f ai-fabric-product/pom.xml -q -pl ai-fabric-vectorization-runner,ai-fabric-embedding-worker -am -DskipTests package`

Live evidence after applying V118:

- Staging Platform backend redeploy trigger: `/tmp/loomai-production-readiness-20260528T091943Z/staging-platform-backend-redeploy-trigger.json`
- Staging Platform backend deployment readback: `/tmp/loomai-production-readiness-20260528T091943Z/staging-platform-backend-deployment-h7fs17pdlc2q4jbene72bbcy.json`
- Staging Platform backend health after V118: `/tmp/loomai-production-readiness-20260528T091943Z/staging-platform-backend-health-after-v118.json`
- Target profile readback after V118: `/tmp/loomai-production-readiness-20260528T091943Z/target-profiles-after-v118.json`
- Production preflight after V118: `/tmp/loomai-production-readiness-20260528T091943Z/dtp-coolify-production-preflight-after-v118.json`
- Staging core service health after V118: `/tmp/loomai-production-readiness-20260528T091943Z/staging-core-health-after-v118.txt`

Live evidence after moving production apps to `Platform-V10`:

- Sanitized source readback confirms all six production apps use `Platform-V10`.
- Production app source paths are provider-neutral for Platform backend, Platform UI, Partner UI, Runtime, and Shopify Bridge production.
- Deployment ids:
  - `loomai-platform-backend`: `kw5k9p2s9umbkis9w9jjsqfn`
  - `loomai-platform-ui`: `vgblizcy0c27dda7lt8535af`
  - `loomai-partner-ui`: `j67mfpmyk14soqpml7rcs1em`
  - `loomai-runtime`: `qsc8e27ktjrqimgf5kh8ekit`
  - `loomai-shopify-bridge-prod`: `lxj6cj4nbkbmm2q883mggamq`
  - `loomai-ecommerce-store`: `skudb39rx880pe8bd2pzbd6k`
- Health endpoints after redeploy:
  - `https://loomai-platform-backend.46.225.162.106.sslip.io/actuator/health` -> `UP`
  - `https://loomai-platform-ui.46.225.162.106.sslip.io/health` -> `UP`
  - `https://loomai-partner-ui.46.225.162.106.sslip.io/health` -> `UP`
  - `https://loomai-runtime.46.225.162.106.sslip.io/actuator/health` -> `UP`
  - `https://loomai-shopify-bridge-prod.46.225.162.106.sslip.io/actuator/health` -> `UP`
  - `https://loomai-ecommerce-store.46.225.162.106.sslip.io/actuator/health` -> `UP`
- Platform preflight for `dtp-coolify-production` after redeploy: `PASSED`.

## Gate 4: Production Secret And Config Readiness

Production service configuration must be complete before promotion.

Check these categories:

- Platform internal service keys.
- Dedicated production Shopify app client id/secret and webhook secret for production app URLs.
- Shopify Admin tokens only where the app/service legitimately requires them.
- Customer Account MCP credentials and redirect URI registrations, if claimed.
- Checkout MCP credentials and redirect URI registrations, if claimed.
- MCP Gateway admin/service keys.
- Runtime provider keys.
- Vector provider keys and production index/collection config.
- SMTP/support notification credentials.
- ProdUS or external-customer private-runtime assertion material, if included.
- Transient file URL allowed hosts for external customer runtimes.

Rules:

- Secrets live in PlatformSecretService or Coolify runtime envs, not code.
- Browser UIs receive only public runtime config.
- Any secret update must have an operator-safe audit entry.
- Duplicate stale Coolify env rows must be cleaned or proven preview-only.
- Production envs must not reference staging-only domains such as `shop-staging.loomai.pro` unless the proof explicitly uses a staging-equivalent production target.
- Real merchant production envs must not reference the staging Shopify app credentials.

### Current Gate 4 Execution - 2026-05-28

Gate 4 was executed against the existing production Coolify applications and Platform secret catalog.

Results:

- Platform secret catalog check returned 41 secret definitions, 36 present, and no required Platform secrets missing.
- Production Coolify env audit initially found stale/drifted values:
  - `loomai-platform-backend` normal/preview env had `PLATFORM_DEPLOY_BRANCH=Platform-V8`.
  - `loomai-ecommerce-store` normal/preview env had `CONNECTOR_INDEXING_ENABLED=true`.
  - `loomai-ecommerce-store` normal/preview env had `CONNECTOR_INDEXING_RUNTIME_BASE_URL` pointing at the old Railway runtime URL.
- Cleanup was applied through Coolify API:
  - backend normal/preview `PLATFORM_DEPLOY_BRANCH=Platform-V10`;
  - ecommerce normal/preview `CONNECTOR_INDEXING_ENABLED=false`;
  - ecommerce normal/preview `CONNECTOR_INDEXING_RUNTIME_BASE_URL=` blank.
- Backend and ecommerce production apps were redeployed after env cleanup:
  - backend deployment `x12bvu1lmorp1prodvc0gil9`;
  - ecommerce deployment `lkb7h8z1m3rjs4v1g95jvxzp`.
- Post-cleanup production env audit found zero suspicious staging/Railway/V8 references across the six production apps inspected.
- Production health after env cleanup returned HTTP `200`/`UP` for Platform backend, Platform UI, Partner UI, Runtime, Shopify Bridge production, and Ecommerce Store.

Evidence:

- Redacted secret catalog: `/tmp/loomai-production-readiness-20260528T091943Z/platform-secret-summary.json`
- Redacted secret counts: `/tmp/loomai-production-readiness-20260528T091943Z/platform-secret-summary-redacted-counts.json`
- Sanitized env audit after cleanup: `/tmp/loomai-production-readiness-20260528T091943Z/prod-coolify-env-key-audit-after-cleanup-sanitized.json`
- Health after cleanup redeploy: `/tmp/loomai-production-readiness-20260528T091943Z/prod-health-after-env-cleanup-redeploy.txt`

Gate 4 conclusion:

- `PASS` for controlled proof readiness.
- Public launch still requires final production DNS/TLS, dedicated production Shopify app credentials/URLs, and App Store/support packaging decisions.

## Gate 5: Production Data And Migration Readiness

Before mutating production:

1. Back up production Platform database.
2. Record current Flyway migration status.
3. Confirm migration checksums match the release branch.
4. Confirm no pending migration performs irreversible commercial/data mutation without rollback notes.
5. Confirm production vector indexes/collections exist or can be created idempotently.
6. Confirm reindex jobs can run without duplicating unsafe/private data.

Required command examples:

```bash
mvn -f Platfrom/backend/pom.xml -q test
mvn -f ai-infrastructure-module/pom.xml -q -pl ai-infrastructure-core,ai-fabric-runtime -am test
```

Use broader test commands when the release touches providers, Bridge, Marketplace, or deployment code.

### Current Gate 5 Execution - 2026-05-28

Gate 5 was executed for the current production Platform database and release branch.

Results:

- Production Coolify PostgreSQL database exists and is healthy:
  - name `loomai-platform-postgres-production`;
  - uuid `nkti6x5r7ovw1xx8q0ykhweq`;
  - status `running:healthy`.
- Fresh production Platform database backup was created on the production host:
  - `/var/backups/loom-platform-db/platform-production-20260528T135304Z.sql.gz`;
  - file mode `600`;
  - approximate size `2.5M`.
- Production Flyway history tail was recorded. The latest applied migration is:
  - version `118`;
  - description `provider neutral coolify runtime defaults`;
  - success `true`;
  - checksum `121501073`.
- Full Platform backend regression suite passed:
  - `mvn -f Platfrom/backend/pom.xml -q test`
- Runtime/core regression suite passed:
  - `mvn -f ai-infrastructure-module/pom.xml -q -pl ai-infrastructure-core,ai-fabric-runtime -am test`

Evidence:

- Production DB backup proof: `/tmp/loomai-production-readiness-20260528T091943Z/platform-production-db-backup-20260528.txt`
- Production Flyway history tail: `/tmp/loomai-production-readiness-20260528T091943Z/platform-production-flyway-history-tail.tsv`

Gate 5 conclusion:

- `PASS` for controlled proof readiness.
- No irreversible production data mutation was performed by Gate 5.
- Production vector/RAG proof remains part of Gate 8 after a controlled production promotion target exists.

### Runtime Chat Database Profile Update - 2026-05-29

Production runtime chat/session storage is now profile-driven:

- `dtp-coolify-staging` keeps the runtime Docker default H2 file database for cheap, disposable staging previews.
- `dtp-coolify-production` adds `runtimeDatabaseMode=COOLIFY_POSTGRES` through migration `V119__coolify_production_runtime_postgres.sql`.
- The Coolify provider creates or reuses one managed PostgreSQL database per deployment/profile, stores the generated DB password in Platform secrets, injects Spring datasource env vars into the runtime app, and persists a `RUNTIME_POSTGRES_DATABASE` provider resource handle for lifecycle actions.
- Runtime DB create uses `instant_deploy=false`; Platform explicitly starts the database so promotion evidence can show the DB lifecycle step.
- The runtime JDBC host is derived from Coolify's returned internal database URL when present. Live staging smoke showed Coolify uses the database UUID as the internal hostname, not the display name.

Staging proof executed on the staging Coolify server before touching the production server:

- Staging Coolify project has both internal `staging` and `production` environments.
- A disposable PostgreSQL database was created in the staging server's internal `production` environment with `is_public=false`.
- The database reached `running:healthy`.
- The returned internal DB host matched the Coolify database UUID.
- The smoke database was deleted after verification; read-after-delete returned `404`.
- Sanitized evidence: `/tmp/loomai-coolify-postgres-smoke-sanitized-summary.json`.

Local verification:

```bash
mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyDeploymentProviderTest,CoolifyApiClientTest,DeploymentTargetProfileMigrationTest test
```

Important operational note:

- The live staging Platform record for `dtp-coolify-production` points at the real production Coolify server (`46.225.162.106`). Use the staging Coolify server's internal `production` environment for dry-run database smoke tests; do not use `dtp-coolify-production` for staging-only destructive tests unless the target profile is explicitly redirected to a staging-safe production-equivalent environment.

## Gate 6: Controlled Production Promotion Proof

The first production deployment must be a controlled proof, not a broad launch.

Promotion target:

- one approved merchant-equivalent Shopify store or one production-equivalent deployment
- one production target profile: `dtp-coolify-production`
- one release commit
- one rollback plan

Steps:

1. Verify merchant/operator approval exists.
2. Create or select the production product-service records.
3. Set `targetProfileId=dtp-coolify-production`.
4. Trigger the Platform `Go production` mutation from the approved Platform/merchant flow.
5. Capture the operation id and deployment records.
6. Observe Coolify production deployments through Platform status surfaces.
7. Verify the created production services point to the production app UUIDs/domains.
8. Run production health checks.
9. Run production smoke tests.
10. Record all evidence in `$RUN_DIR`.

This proof must demonstrate:

- real production mutation occurred
- production provisioning completed
- production verification passed
- staging deployment records and live staging services were unchanged

### Current Gate 6 Execution - 2026-05-28/29

Gate 6 was executed against Shopify Companion deployment `dep-8c3e7259` using production target profile `dtp-coolify-production`.

Precondition fix:

- The production target profile initially pointed Platform at the public Coolify API URL from inside the Platform container, which timed out through host hairpin networking.
- The production target profile provider config was corrected to use the internal production Docker network URL `http://coolify:8080` while keeping the external operator dashboard/API at `http://46.225.162.106:8000`.
- After this correction, target profile preflight returned `PASSED`.

Production promotion proof:

- Published version applied: `ver-1b77bfba` (`v10`).
- Production release: `rel-ec590e44`.
- Target profile: `dtp-coolify-production`.
- Final release state: `APPLIED_VERIFIED`, provisioning `ACTIVE`, verification `PASSED`.
- Platform consumer credentials now resolve the production runtime base URL `http://dep-8c3e7259.46.225.162.106.sslip.io` for `shopify-shopping-companion-test`.
- Production runtime health returned `UP`.
- Production Shopify Bridge bootstrap returned `available=true`, `deploymentId=dep-8c3e7259`, `consumerId=shopify-shopping-companion-test`, and `runtimeAuthMode=PRIVATE_RUNTIME_SIGNED_ASSERTION`.

Bridge resilience fix required during proof:

- Production Bridge surfaced stale/expired persisted Shopify credential refresh failures during bootstrap/action capability/chat paths.
- Commits `4c3e86b86`, `61ab231c0`, and `9421f96f4` made bootstrap, governed-action capability, and chat paths resilient to stale persisted credential refresh failures without printing secrets or bypassing valid credentials.
- Production Bridge redeploy `i2h4tqzs4xmx5q68twhnvjc7` finished successfully after the final chat resilience commit.
- Production Bridge health and bootstrap remained healthy after the redeploy.
- Production chat smoke returned HTTP `200` with canonical response fields.

Product service source-of-truth fix:

- Production Platform product service `shopify-bridge-prod` had stale staging metadata in the production database.
- The production record was corrected to `environment_scope=production`, production Bridge base URL `https://loomai-shopify-bridge-prod.46.225.162.106.sslip.io`, and production Coolify app UUID `wurlsp7d3bdsedy1lmn33sdc`.
- Production product-service API and health readback returned active/ready after correction.

Evidence:

- Evidence directory: `/tmp/loomai-production-readiness-20260528T174005Z`
- Target preflight after internal URL fix: `prod-target-profile-preflight-after-internal-url.json`
- Production apply response: `prod-deployment-apply-production-profile-response.json`
- Production release readback: `prod-deployment-releases-after-production-apply.json`
- Production consumer credentials readback: `prod-platform-shopify-consumer-credentials-after-production-apply.json`
- Production runtime health: `prod-runtime-health-after-production-apply.json`
- Production Bridge bootstrap: `prod-bridge-bootstrap-after-production-apply.json`
- Bridge resilience redeploy: `bridge-chat-resilience-deployment-i2h4tqzs4xmx5q68twhnvjc7.json`
- Post-resilience Bridge health/bootstrap: `prod-bridge-health-after-chat-resilience.json`, `prod-bridge-bootstrap-after-chat-resilience.json`
- Production chat smoke: `prod-bridge-chat-shipping-policy-after-chat-resilience-response.json`

Gate 6 conclusion:

- `PASS` for controlled production-promotion proof.
- This is a controlled production proof on temporary `sslip.io` hostnames. It does not by itself approve public App Store/self-service launch.

## Gate 7: Production Service Deployment Order

Use this order unless a release-specific dependency graph says otherwise:

1. Platform backend.
2. Platform UI and Partner UI.
3. MCP Execution Gateway.
4. Shopify Bridge production service.
5. Runtime service.
6. REST connector service.
7. Vectorization runner service.
8. Product/customer-specific data plugin installation.
9. Reindex/vectorization jobs.
10. Storefront widget/theme/script config.
11. Shopify app URLs, webhooks, and OAuth redirect verification.

Reasoning:

- Platform must own the product-service records and target-profile state before product services are reconciled.
- Gateway/Bridge should be ready before runtime action paths are exercised.
- Runtime and connector must be healthy before vectorization and RAG proof.
- Storefront/widget should switch only after backend services pass smoke checks.

## Gate 8: Production Verification

Run production smoke checks immediately after deployment.

Required checks:

- `/actuator/health` or equivalent health endpoints return `UP`.
- Platform service inventory shows production service records as active.
- Product runtime can answer a simple query.
- RAG query returns grounded evidence when indexed data exists.
- Governed action path returns confirmation for write actions.
- Rejected/unauthorized calls fail closed.
- Partner/merchant portal can view production readiness without provider internals.
- Production support/export bundle can be generated.
- Logs show no raw secrets, OAuth tokens, temporary file URLs, or private document content.

Shopify-specific checks:

- storefront bootstrap points to production-safe config
- widget loads on production storefront
- catalog/policy RAG works
- cart add flow is governed and confirms before write
- Customer Account/Checkout claims are hidden unless live-proven
- Shopify app redirect URLs and webhooks use production domains
- real merchant production installs use the dedicated production Shopify app, not the staging/development app

ProdUS/external-customer checks, if included:

- private-runtime assertion verification works
- `/api/chat/me/query-once` works
- transient file URL allowlist is production host scoped
- `documentUsage` is returned for temporary file inputs
- unsupported provider/file combinations return `NOT_USED`

### Current Gate 8 Execution - 2026-05-29

Gate 8 was executed after Gate 6 production promotion and after the Bridge resilience redeploy.

Production vectorization/RAG source-of-truth fix:

- Before reindex, the production deployment vectorization connection still referenced the old Railway Bridge URL.
- The production vectorization connection was updated through Platform API to use production Bridge base URL `https://loomai-shopify-bridge-prod.46.225.162.106.sslip.io`, admin header `X-BRIDGE-API-KEY`, and secret ref `MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY`.
- The active vectorization plan was refreshed to revision `9`, preserving Shopify product/policy vector spaces and product metadata fields including image URL, image alt text, price, availability, variant, vendor, and product URL fields.

Production Shopify source credential fix:

- Production Bridge vectorization-source initially failed with Shopify token refresh `401` because the persisted expiring offline credential had stale refresh material.
- The production store source credential was changed to use the dedicated non-expiring Admin API source token for `shopping-companion-test.myshopify.com`; the token value remains in private handoff/local secret storage only.
- Direct production Bridge vectorization-source proof returned HTTP `200`, product `totalCount=77`, and real product records with image URLs.

Managed production reindex proof:

- Managed reindex run `vrn-2d5921b5` completed successfully.
- Final status: `COMPLETED`, requested status `COMPLETED`.
- Processed records: `81`.
- Succeeded records: `81`.
- Failed records: `0`.
- Failure buckets: none.
- Vectorization preview/source counts after reindex: `product=77`, `support-policy=4`.
- Deployment vectorization overview reported current active runner and plan.

Production RAG smoke:

- Query: `summarize high performance laptops for gaming`.
- Production Bridge chat returned HTTP `200`.
- Response contained canonical `ragResponse.documents`, `sources`, and `providerRequestId`.
- Initial production RAG smoke returned `10` documents/sources.
- Post-rollback-forward RAG smoke returned `5` documents/sources and production bootstrap remained available.

Evidence:

- Production vectorization connection request/response: `prod-vectorization-connection-upsert-request.json`, `prod-vectorization-connection-upsert-response.json`
- Production vectorization plan request/response: `prod-vectorization-plan-upsert-request.json`, `prod-vectorization-plan-upsert-response.json`
- Direct production Bridge vectorization source proof: `prod-bridge-vectorization-source-product-direct-status-after-source-token-upsert.txt`, `prod-bridge-vectorization-source-product-direct-response-after-source-token-upsert.json`
- Managed reindex final proof: `prod-vectorization-reindex-run-vrn-2d5921b5-final.json`
- Production vectorization overview/preview after reindex: `prod-deployment-vectorization-overview-after-successful-reindex.json`, `prod-deployment-vectorization-preview-after-successful-reindex.json`
- Production RAG smoke after reindex: `prod-bridge-chat-rag-gaming-laptops-response.json`
- Production RAG smoke after rollback-forward: `prod-rag-smoke-after-forward-v10-response.json`

Gate 8 conclusion:

- `PASS` for controlled production runtime/Bridge/vectorization/RAG verification.
- Public claims for Customer Account, Checkout, refunds, returns, and terminal checkout automation remain blocked until their separate gates pass.

## Gate 9: Rollback And Deactivation Proof

A production release is not complete until rollback is proven.

Perform one controlled rollback/deactivation proof:

1. Request rollback/deactivation through the approved merchant/operator flow.
2. Confirm Platform records the request and operation id.
3. Confirm production service is stopped, reverted, or disabled according to the selected rollback mode.
4. Confirm staging remains untouched.
5. Confirm user-facing guidance is merchant-safe.
6. Confirm operator diagnostics contain enough detail without exposing secrets.
7. Restore the production proof target only if required for continued beta testing.

Also run a failed-promotion proof:

- intentionally block a non-destructive production precondition,
- submit a promotion attempt,
- verify production fails safely,
- verify staging remains unchanged,
- verify the user receives actionable merchant-safe guidance.

### Current Gate 9 Execution - 2026-05-29

Rollback-forward proof was executed through Platform release apply operations against `dtp-coolify-production`.

Rollback proof:

- Previous published version applied: `ver-1d4b7a13` (`v9`).
- Rollback release: `rel-baf3d84e`.
- Final release state: `APPLIED_VERIFIED`, provisioning `ACTIVE`, verification `PASSED`.
- Production Bridge bootstrap remained available after the rollback.

Forward restore proof:

- Current published version re-applied: `ver-1b77bfba` (`v10`).
- Forward release: `rel-9bfd761f`.
- Final release state: `APPLIED_VERIFIED`, provisioning `ACTIVE`, verification `PASSED`.
- Production Bridge bootstrap returned `available=true` after forward restore.
- Production runtime and Bridge health returned `UP`.
- Production RAG smoke returned canonical documents/sources after forward restore.

Staging isolation proof:

- Staging Bridge bootstrap was captured before rollback and after the rollback-forward sequence.
- The following staging bootstrap fields stayed unchanged: `deploymentId`, `consumerId`, `runtimeBaseUrl`, `runtimeAuthMode`, `billingTier`, and `billingStatus`.
- This proves the production rollback-forward operation did not mutate the live staging storefront bootstrap state.

Failed-promotion validation proof:

- A negative production apply was submitted with `targetProfileId=dtp-coolify-production` and a non-existent version id.
- The request failed with HTTP `404`.
- The latest production release remained `rel-9bfd761f` on `ver-1b77bfba`, status `APPLIED_VERIFIED`.
- Staging bootstrap fields stayed unchanged before/after the failed apply attempt.

Scope note:

- This proves rollback-by-reapply, forward restore, and validation-failure staging isolation.
- A provider-level failed deployment rehearsal still needs a first-class non-destructive failure harness before it should be claimed as fully live-proven. Do not create broken production app config just to force a provider failure.

Evidence:

- Rollback release final proof: `prod-rollback-v9-release-final.json`
- Forward restore release final proof: `prod-forward-v10-release-final.json`
- Production bootstrap after rollback: `prod-bootstrap-after-v9-rollback-proof.json`
- Production bootstrap after forward restore: `prod-bootstrap-after-v10-forward-proof.json`
- Staging bootstrap before/after rollback-forward: `staging-bootstrap-before-production-rollback-proof.json`, `staging-bootstrap-after-production-rollback-proof.json`
- Negative failed-promotion response/status: `prod-failed-promotion-invalid-version-response.json`, `prod-failed-promotion-invalid-version-http-status.txt`
- Staging bootstrap before/after negative apply: `staging-bootstrap-before-failed-promotion-validation-proof.json`, `staging-bootstrap-after-failed-promotion-validation-proof.json`
- Production release list after negative apply: `prod-releases-after-failed-promotion-validation-proof.json`

Gate 9 conclusion:

- `PASS` for rollback-by-reapply, forward restore, and validation-failure staging isolation.
- `PARTIAL` for provider-level failed-promotion proof until a safe provider failure harness exists.

## Gate 10: Release Decision

Only after Gates 0-9 pass can the release owner choose a launch posture.

Allowed launch posture after controlled proof:

- private/design-partner production beta for named stores/customers
- no public App Store claims beyond proven features

Blocked launch posture until additional evidence exists:

- public self-service Shopify App Store launch
- broad customer-account/order/return/refund automation claims
- terminal checkout automation claims
- unsupported provider document-analysis claims
- real merchant production installs using staging/development Shopify app credentials or URLs

Release notes must include:

- release commit
- production service URLs/domains
- active feature claims
- explicitly disabled/deferred claims
- rollback procedure
- support escalation path
- evidence bundle location

## Gate 11: Post-Release Monitoring

For the first 24-72 hours:

- monitor health endpoints and Coolify app status
- monitor Platform verification status
- monitor provider error rate, timeout rate, and fallback/fail-closed counts
- monitor vectorization job status and duplicate/drop counts
- monitor Shopify Bridge action errors
- monitor Customer Account/Checkout auth failures if enabled
- monitor support inbox/escalations
- review logs for accidental secret, token, temporary URL, or private document exposure

Escalation triggers:

- production health down
- high action execution failure rate
- RAG returning ungrounded/stale answers
- repeated provider timeout/fallback
- any secret/private URL in logs
- staging affected by production operations
- merchant-facing UI exposes provider internals

## Evidence Bundle Checklist

Store these under the production run directory:

- release commit and branch
- GitHub CI proof
- local test outputs
- staging release gate final JSON
- production target preflight JSON
- production product-service records before/after
- Coolify deployment ids and statuses
- health responses
- smoke query responses
- RAG/debug evidence
- governed action evidence
- reindex/vectorization evidence
- rollback/deactivation proof
- failed-promotion staging-isolation proof
- final release decision note

## Execution Status - 2026-05-29 Runtime Promotion Proof

Customer/runtime production promotion was executed against `dtp-coolify-production` for deployment `dep-8c3e7259`.

Evidence:

- Branch: `Platform-V10`
- Runtime fix commit: `5c9c0add8` (`Add PostgreSQL driver to runtime image`)
- Production release: `rel-9a8cc932`
- Production release status: `APPLIED_VERIFIED`
- Production verification status: `PASSED`
- Runtime app: `hygnmeoto42ip5lepsow86ek`, `running:healthy`
- Connector app: `sr6yva7wn46j8gvya9npjouh`, `running:healthy`
- Vectorization runner app: `aown07njyl1ev0yrwunwnzmb`, `running:healthy`
- Runtime Postgres DB: `f3eprropxb0gi9d89ytc3qiq`, private, `running:healthy`
- Public health checks passed for runtime, connector, and vectorization runner.

Important note:

- Initial production attempt `rel-64e7eec4` failed because the runtime image did not include `org.postgresql.Driver`.
- The failed attempt left an orphan runtime Postgres database. It was removed from Coolify after the successful retry.
- The current active production state has a single private runtime Postgres database for `dep-8c3e7259`.

Still outside this proof:

- Full public Shopify launch remains blocked until the broader release gates pass.
- Rollback/deactivation proof and failed-promotion staging-isolation proof still need explicit execution before a public/self-service launch decision.

## Completion Criteria

This production deployment plan is complete only when:

- all gates above are run against the intended production target,
- production deployment is live and verified,
- rollback/deactivation is live-proven,
- failed promotion leaves staging untouched,
- evidence is attached to the release record,
- launch claims match only what was proven.

Until then, production remains blocked for public/self-service launch and limited to controlled proof execution.
