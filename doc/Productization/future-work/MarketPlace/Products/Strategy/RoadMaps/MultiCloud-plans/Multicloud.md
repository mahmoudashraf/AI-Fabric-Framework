# PLAN-009 — Hybrid Deployment Topology: Railway and Coolify

Status: planning document (2026-04-29)

This document captures the decision to operate the platform on a **hybrid hosting topology**: customer-facing and revenue-critical surfaces on Railway (managed reliability), with per-tenant runtimes and internal services on self-hosted Coolify (cost-optimized). It defines the service placement matrix, networking topology, provisioner implementation, migration order, and operational discipline required to make hybrid actually pay off.

It complements PLAN-008 (vertical product factory operating model) by addressing the runtime-cost line item that PLAN-008 §6.3 identifies as the second-largest infrastructure lever (after Wave 4 shared vector storage).

---

## 1) Executive Summary

Operate two hosting paradigms with a deliberate placement matrix:

- **Railway**: Platform UI, platform backend (control plane API), shared Postgres, partner portal, Shopify bridge service. Stable, managed, customer-facing.
- **Coolify on Hetzner**: Per-tenant runtime deployments (Loom Companion instances), embedding worker, vectorization core/runner, shared inference services, other internal workers. Cost-optimized, self-hosted.

A new `CoolifyDeploymentProvisioner` mirrors the existing Railway provisioner so the platform's deployment lifecycle code is unchanged — only the target shifts. Network the two paradigms via Tailscale. Build images once via GitHub Actions to GHCR; both Railway and Coolify pull the same artifact.

Outcome: ~60–70% reduction in per-tenant runtime cost without sacrificing reliability on the surfaces that matter for customer experience and partner trust.

---

## 2) Why This Plan Exists

Per PLAN-008 §5.1, the company stays in builder + integrator mode through ~$3M ARR. The pricing math in PLAN-006 §5.2 (Loom Starter $29 → Shopify 15% → partner 25% → ~$18.50 net) only works if per-tenant runtime cost stays well below $5/mo. On Railway today, that ceiling is hard to hit reliably as merchants scale.

The hybrid approach was chosen over three alternatives:

- **All-Railway**: simplest, but per-tenant runtime cost compounds against margin once paying merchants exceed ~50.
- **All-Coolify**: cheapest, but a single self-hosted host becomes a SPOF for the entire platform including customer-facing UI and Postgres. Unacceptable risk pre-revenue.
- **Multi-tenant shared JVM** (discussed in earlier strategy): the right long-term answer (5–10× density), but requires 4–8 weeks of runtime refactoring. Deferred to a later plan; this plan delivers cost reduction without touching runtime code.

The hybrid splits the trade-off correctly: managed reliability where customers see it, self-hosted economics where volume scales.

---

## 3) Scope

In scope:

- service placement matrix (Railway vs Coolify)
- `CoolifyDeploymentProvisioner` implementation behind the existing provisioner abstraction
- networking between Railway and Coolify (Tailscale)
- CI/CD pipeline: build once, deploy either target
- Postgres connectivity from Coolify to Railway
- service discovery from platform → tenant runtimes
- migration order from current all-Railway to hybrid
- operational runbook (backups, host loss, monitoring)
- rollback path if hybrid proves operationally painful

Out of scope:

- multi-tenant JVM refactor (separate, larger plan when the time comes)
- Wave 4 shared vector storage (existing plan: `TENANT_SCOPED_SHARED_VECTOR_INFRASTRUCTURE_PLAN.md`)
- multi-region replication
- HA for the Coolify control plane itself (deferred until 50+ paying merchants)
- Kubernetes / k3s migration (not needed at this scale)

---

## 4) Service Placement Matrix

| Service | Host | Rationale |
|---|---|---|
| Platform UI | Railway | Customer + partner facing; needs CDN, uptime, managed TLS |
| Platform backend (control plane API) | Railway | Partner-facing API; SLA-relevant; calls all other services |
| Postgres (platform shared DB) | Railway | Managed backups, PITR; do not self-operate pre-revenue |
| Partner portal / store | Railway | Partner-facing |
| Shopify bridge service | Railway | Webhook receiver; Shopify retry windows are limited; downtime risks data loss |
| ai-fabric-embedding-worker | Coolify | High volume, batch-tolerant, cost-sensitive |
| ai-fabric-vectorization-core | Coolify | Internal, restartable |
| ai-fabric-vectorization-runner | Coolify | Scheduled work, idle most of the time |
| ai-fabric-shared-ollama-service | Coolify | RAM/GPU-hungry; expensive on managed PaaS |
| ai-fabric-product-integration-core | Coolify | Internal orchestration; restart-tolerant |
| Tenant runtime deployments (Loom merchant instances) | Coolify | Largest cost line item; scales linearly with merchants |

The placement is binary; do not "drift" services across the boundary opportunistically. Any change to placement requires updating this matrix first.

---

## 5) Networking Topology

### 5.1 Tailscale mesh

Use Tailscale as the private network spanning Railway and Coolify:

- install Tailscale on the Coolify host
- attach Railway services that need cross-cloud private connectivity via Tailscale's Railway integration (sidecar or wireguard-config pattern)
- internal services on Coolify (embedding-worker, vectorization-runner) reach Railway Postgres over Tailscale, never public internet
- platform backend (Railway) calls internal Coolify-hosted services over Tailscale

Free tier (Tailscale's "Personal" plan or "Starter") covers expected node count.

### 5.2 Public exposure

Two surfaces are intentionally public:

- Coolify-hosted **tenant runtimes** are exposed via Coolify-managed Traefik with auto-TLS at FQDNs like `tenant-{id}.runtime.loom.ai`. The platform calls them over public HTTPS with shared-secret header (mTLS upgrade is a future hardening).
- Coolify-hosted **Shopify bridge** stays on Railway (public there), so this is moot.

Internal services are never exposed publicly.

### 5.3 Postgres connectivity

- Railway Postgres has public networking enabled with strong credentials and SSL required
- Coolify-side services connect via Tailscale once Tailscale is in place; before that, public connection string with strong password and (where supported) IP allowlist
- **PgBouncer on the Coolify host** in front of all Coolify→Railway Postgres traffic, to drop connection count and reduce per-query overhead

### 5.4 Egress and latency

- Cross-cloud Postgres queries add 20–50ms vs co-located. Acceptable for chat workflows; problematic for chatty queries. Do not issue per-message metadata reads from runtimes against Railway Postgres without caching.
- Railway charges Postgres egress when traffic leaves its network. Monitor monthly; expected baseline at 50 tenants is small (<$10/mo) but watch the line.

---

## 6) `CoolifyDeploymentProvisioner` Implementation

### 6.1 Interface

The platform already has (or this plan introduces) a `DeploymentProvisioner` abstraction:

```
provision(spec) → handle
start(handle) | stop(handle) | delete(handle)
status(handle) | tailLogs(handle)
```

A `RailwayDeploymentProvisioner` exists. This plan adds a sibling `CoolifyDeploymentProvisioner` calling Coolify's REST API.

### 6.2 Coolify API surface used

```
POST   /api/v1/applications              create application
POST   /api/v1/applications/{uuid}/start
POST   /api/v1/applications/{uuid}/stop
POST   /api/v1/applications/{uuid}/restart
DELETE /api/v1/applications/{uuid}
GET    /api/v1/applications/{uuid}       status
POST   /api/v1/applications/{uuid}/envs  set env vars
GET    /api/v1/applications/{uuid}/logs  stream logs
```

Authentication via Bearer token. Pin to a Coolify minor version; upgrade deliberately.

### 6.3 Per-deployment selection

A field on the deployment record (`provisioner: railway | coolify`) selects target. New deployments default to Coolify. Existing Railway deployments continue under their original provisioner — never auto-migrate without explicit decision.

### 6.4 Tier-driven resource limits

Memory and CPU passed via spec, derived from tier:

- Free: 512MB, 0.5 CPU
- Starter: 1GB, 1 CPU
- Pro: 2GB, 1.5 CPU
- Elite: 4GB, 2 CPU
- Enterprise: dedicated host (separate provisioner path or higher Coolify limits)

---

## 7) CI/CD: Build Once, Deploy Either

```
GitHub Actions on tag
  ├─ build image (one Dockerfile per service)
  ├─ push to GHCR (ghcr.io/loom-ai-labs/{service}:vX.Y.Z)
  ├─ Railway pulls from GHCR via image-source service
  └─ Coolify pulls from GHCR via the provisioner
```

Both targets reference the same artifact. Single source of truth. GHCR is free for both public and private images, integrates with GitHub auth.

Image tags are immutable. Promotion to "latest" happens explicitly via release tag, never auto-overwritten.

---

## 8) Service Discovery and Inter-Service Calls

### 8.1 Platform → tenant runtime

- platform stores runtime FQDN in deployment record on provisioning
- runtime FQDN format: `tenant-{deployment-id}.runtime.loom.ai`
- platform calls runtime over public HTTPS with shared-secret header `X-AIFabric-Auth`
- runtime validates header against value injected at provision time

### 8.2 Platform → Coolify-hosted internal service

- internal services exposed only on Tailscale addresses (no public DNS)
- platform reaches them via Tailscale hostname (`embedding-worker.tail.ts.net` or similar)
- no shared secret required; Tailscale ACL provides authentication

### 8.3 Tenant runtime → platform backend

- runtime calls platform's Railway-hosted backend over public HTTPS with deployment-scoped JWT
- existing pattern unchanged

---

## 9) Operational Discipline

### 9.1 Backups

| Surface | Backup mechanism | RPO | RTO |
|---|---|---|---|
| Railway Postgres | Railway managed (daily snapshot + PITR) | 1 day | <1 hr |
| Coolify control state | nightly tar of `/data/coolify` to Backblaze B2 | 1 day | <2 hr (reinstall + restore) |
| Per-deployment volumes (Lucene indexes) | nightly rsync to B2 | 1 day | minutes per deployment |
| Image artifacts | GHCR retains tags indefinitely | n/a | n/a |

### 9.2 Monitoring

- Railway: built-in metrics and logs for Railway services
- Coolify: built-in logs UI; ship metrics to Grafana Cloud free tier (Prometheus remote-write)
- per-tenant Grafana dashboards with `tenant` label
- single alerting destination (PagerDuty free tier or email-to-Slack) for both sides

### 9.3 Host loss recovery (Coolify)

Documented runbook covers:

- detection (uptime monitor on Coolify and on a sample tenant runtime)
- spin up replacement Hetzner host
- install Coolify, restore `/data/coolify` from B2
- re-attach DNS, restore per-deployment volumes
- target RTO: 2 hours

### 9.4 Coolify version pinning

- Coolify pinned to specific version in production
- staging Coolify (separate cheap Hetzner host, $7/mo) tracks one version ahead
- upgrades only after staging soak

---

## 10) Implementation Steps

### 10.1 Wave A — POC and infrastructure (week 1)

1. provision Hetzner CCX23 ($48/mo) in Falkenstein
2. install Coolify, generate API token, harden host (SSH key only, fail2ban, ufw)
3. add Coolify host to Tailscale; add Tailscale on at least one Railway service that needs cross-cloud access
4. deploy one non-critical service manually via Coolify UI (e.g., `ai-fabric-embedding-worker`)
5. verify it reaches Railway Postgres over Tailscale and platform backend can call it

### 10.2 Wave B — CI/CD and provisioner (weeks 2–3)

6. set up GitHub Actions to build images and push to GHCR for the runtime image and one internal service
7. configure Coolify to pull from GHCR with credentials
8. implement `CoolifyDeploymentProvisioner` against the existing `DeploymentProvisioner` interface
9. wire per-deployment provisioner selection into platform code
10. unit + integration tests covering provision/start/stop/delete/status/logs

### 10.3 Wave C — Internal service migration (weeks 3–4)

11. migrate `ai-fabric-embedding-worker` from Railway to Coolify
12. migrate `ai-fabric-vectorization-core` and `ai-fabric-vectorization-runner`
13. validate platform end-to-end with Coolify-hosted internal services
14. Decommission corresponding Railway services after 7 days of stable operation

### 10.4 Wave D — Tenant runtime migration (weeks 4–6)

15. provision one new test tenant runtime on Coolify; validate Loom Companion end-to-end
16. switch new tenant runtime defaults to Coolify
17. leave existing Railway tenant runtimes alone; migrate opportunistically (on tier change, on plan upgrade, on customer request)

### 10.5 Wave E — Operations hardening (weeks 6–8)

18. document host loss recovery runbook; rehearse once
19. set up backup automation (nightly to B2)
20. set up uptime monitoring for Coolify and a canary tenant runtime
21. configure Grafana Cloud dashboards
22. write the placement matrix into CONTRIBUTING.md or platform docs as the source of truth

---

## 11) Acceptance Criteria

This plan is complete when:

- the service placement matrix in §4 is fully implemented in production
- all Coolify-targeted services run on the Hetzner host and have not been on Railway for at least 7 days
- `CoolifyDeploymentProvisioner` provisions, starts, stops, and deletes deployments via Coolify API in production
- CI/CD builds images once and deploys to either Railway or Coolify based on declarative config
- Tailscale connects Coolify to Railway and at least one cross-cloud service path is on Tailscale only
- backup automation runs nightly and a restore has been rehearsed at least once
- the runbook exists and a host loss drill has been completed
- per-tenant runtime cost on Coolify is measurable and 50%+ lower than on Railway for equivalent workload

---

## 12) Dependencies

- existing `DeploymentProvisioner` interface (introduce in week 2 if not yet present)
- one Hetzner account, one Tailscale account, one Backblaze B2 account
- GHCR access (already available via GitHub)
- Loom Companion runtime image building cleanly as a Docker image (verify in week 1)

No engineering dependencies on PLAN-006, PLAN-007, PLAN-008. This plan is operational and runs in parallel.

---

## 13) Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Coolify host fails (single SPOF for runtimes) | Medium | Backups + 2-hour RTO runbook; add standby host once 50+ paying merchants |
| Cross-cloud Postgres latency hurts a specific feature | Low | PgBouncer; cache hot reads; if specific feature suffers, move data closer |
| Postgres egress bill higher than expected | Low | Monitor monthly; mitigate with PgBouncer and query reduction |
| Coolify API breaks on upgrade | Medium | Pin version; staging Coolify one version ahead; canary upgrade |
| Operational fatigue from two paradigms | Medium | Strict placement matrix discipline; document, do not freelance |
| Tailscale free tier limits exceeded | Low | Migrate to paid ($6/user/mo) — still negligible cost |
| Image build inconsistency between Railway-pulled and Coolify-pulled artifacts | Low | Single GHCR source; both targets pull same immutable tag |
| Migration drift (some services half-on-Railway) | High during waves C–D | Wave acceptance criteria require Railway service shutdown after 7 days; do not skip |

---

## 14) Cost Analysis

### 14.1 Today (all-Railway, illustrative at 10 tenants)

- platform UI/backend/Postgres/partner: ~$30–50/mo
- 4 internal services on Railway: ~$30–60/mo
- 10 tenant runtimes on Railway: ~$50–150/mo
- **Total: ~$110–260/mo**

### 14.2 Hybrid (this plan, at 10 tenants)

- platform UI/backend/Postgres/partner on Railway (unchanged): ~$30–50/mo
- 4 internal services co-resident on Coolify host: ~$0 marginal
- 10 tenant runtimes co-resident on Coolify host: ~$0 marginal
- 1× Hetzner CCX23 + Coolify: $48/mo
- Tailscale free tier: $0
- B2 backups: ~$1/mo
- **Total: ~$80–100/mo**

### 14.3 At 50 tenants

- all-Railway projection: ~$300–800/mo for runtimes alone
- hybrid: 1–2 Hetzner hosts ($48–96/mo) + Railway customer-facing baseline (~$30–50/mo) = ~$80–150/mo total

The gap widens with scale because Railway costs scale linearly while Coolify costs scale step-wise per host.

---

## 15) Estimated Effort

- Wave A POC: 1 week (1 engineer)
- Wave B CI/CD + provisioner: 2 weeks (1 engineer)
- Wave C internal migration: 2 weeks (1 engineer)
- Wave D tenant runtime migration: 2 weeks (concurrent with Wave C end)
- Wave E hardening: 2 weeks (concurrent, partial-time)

Total elapsed time: ~6–8 weeks. Total person-effort: ~6–8 person-weeks.

---

## 16) Sequencing With Other Plans

- PLAN-006 (pricing/licensing/positioning): independent, runs in parallel.
- PLAN-007 (integration partner channel): this plan accelerates PLAN-007's margin economics; partner-installed accounts on cheap runtimes is what makes the channel work.
- PLAN-008 (vertical product factory operating model): this plan is referenced from PLAN-008 §6.3 as a missing-block addition; specifically, it precedes "Wave 4 shared vector storage" as the *first* runtime-cost reduction that does not require runtime code changes.
- Future multi-tenant JVM plan: deferred separately. When that lands, the per-tenant container model on Coolify migrates to a denser shared-JVM model on the same Coolify infrastructure. Coolify continues to manage the topology; the runtime architecture changes underneath.

---

## 17) Reversibility

If hybrid proves operationally painful within 6 months, fall back is straightforward:

- migrate Coolify-hosted internal services back to Railway one at a time (existing Dockerfile + Railway integration)
- migrate tenant runtimes back to Railway by switching deployment provisioner default
- keep `CoolifyDeploymentProvisioner` code in place; disabled but available
- Hetzner host can be retired with a single cancellation

The reversibility cost is bounded: ~1 week of work to fully roll back, no data loss (Postgres stayed on Railway throughout), no customer impact if done in waves.

This reversibility is itself a justification for the plan: the downside is recoverable, the upside is structural cost reduction.

---

Want me to commit this as `PLAN-009-HYBRID_DEPLOYMENT_TOPOLOGY_RAILWAY_AND_COOLIFY.md` on the branch and update the prioritization roadmap to reference it?