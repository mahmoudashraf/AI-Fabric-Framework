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
  - This fix is branch-local until Platform backend is redeployed and the migration is applied to the live Platform database.

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

Secret/config metadata check:

- Platform secret catalog returned 41 secret definitions.
- 36 are present.
- No required Platform secret is currently missing.
- Secret values were not printed or exported as part of this evidence bundle.

Observed blockers before release:

- Production apps are still configured on source branch `Platform-V8`, not the current release branch `Platform-V10`.
- Production public endpoints still use `sslip.io`; this is acceptable for controlled proof only, not public launch positioning.
- Some Coolify app records report `running:unknown` even though their HTTP health endpoints are `UP`; configure or document Coolify health checks before final release.
- Live Platform target profile resource defaults still need the release migration applied before production promotion.

Next required infrastructure actions:

1. Move production app source branches to the release branch.
2. Deploy Platform backend so `V118__provider_neutral_coolify_runtime_defaults.sql` applies to live staging/production target profile defaults.
3. Re-run `dtp-coolify-production` preflight and inspect the target profile snapshot to confirm no managed runtime default references `deploy/railway`.
4. Move production app records to provider-neutral Dockerfile paths where the app itself is still configured with Railway-specific paths.
5. Decide production DNS/TLS posture: real production domains for launch, `sslip.io` only for proof.
6. Rerun Platform target profile preflight and record the evidence before any production promotion.

Verification run for the provider-neutral default fix:

- `git diff --check`
- `mvn -f Platfrom/backend/pom.xml -q -Dtest=DeploymentTargetProfileMigrationTest test`
- `mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformManagedInferenceProvisioningServiceTest,RailwayProvisioningPlanServiceTest,CoolifyDeploymentProviderTest test`
- `mvn -f ai-fabric-product/pom.xml -q -pl ai-fabric-vectorization-runner,ai-fabric-embedding-worker -am -DskipTests package`

## Gate 4: Production Secret And Config Readiness

Production service configuration must be complete before promotion.

Check these categories:

- Platform internal service keys.
- Shopify app client id/secret and webhook secret for production app URLs.
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

ProdUS/external-customer checks, if included:

- private-runtime assertion verification works
- `/api/chat/me/query-once` works
- transient file URL allowlist is production host scoped
- `documentUsage` is returned for temporary file inputs
- unsupported provider/file combinations return `NOT_USED`

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

## Completion Criteria

This production deployment plan is complete only when:

- all gates above are run against the intended production target,
- production deployment is live and verified,
- rollback/deactivation is live-proven,
- failed promotion leaves staging untouched,
- evidence is attached to the release record,
- launch claims match only what was proven.

Until then, production remains blocked for public/self-service launch and limited to controlled proof execution.
