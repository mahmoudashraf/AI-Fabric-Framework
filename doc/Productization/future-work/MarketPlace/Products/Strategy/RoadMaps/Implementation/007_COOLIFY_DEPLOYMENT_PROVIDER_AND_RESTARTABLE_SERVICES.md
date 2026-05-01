# 007 Coolify Deployment Provider And Restartable Services

Status: implementation in progress (created 2026-04-29; Slice 0/1 complete; provider core implemented, strict staging Coolify smoke passed, and public Git-source parity path added 2026-05-01)

Owner mode: technical LLM implementation session

Roadmap phase: `007` - Coolify as a first-class deployment provider beside Railway

Priority: P1 infrastructure track. This is allowed in parallel with `006.x` only when it does not slow product-readiness, Thinker/Resolver, or design-partner learning.

Depends on:

- [005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md](005_SHOPIFY_COMPANION_FIRST_PRODUCT_READINESS_AUDIT.md)
- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md](../MultiCloud-plans/MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md)
- [Multicloud.md](../MultiCloud-plans/Multicloud.md)

Related code anchors:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentProvisioningProvider.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentProvisioningService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/PlatformProvisioningProperties.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayApiProvisioningProvider.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayStubProvisioningProvider.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/entity/DeploymentReleaseEntity.java`

External docs checked while drafting:

- Coolify API reference: `https://coolify.io/docs/api-reference/api/`
- Coolify Docker image application API: `https://coolify.io/docs/api-reference/api/operations/create-dockerimage-application`
- Coolify self-hosted installation: `https://coolify.io/docs/get-started/installation`
- Coolify GitHub Actions image deployment guide: `https://coolify.io/docs/applications/ci-cd/github/actions`
- Coolify environment variables: `https://coolify.io/docs/knowledge-base/environment-variables`
- Coolify backup and restore: `https://coolify.io/docs/knowledge-base/how-to/backup-restore-coolify`
- Hetzner API overview: `https://docs.hetzner.cloud/`
- Hetzner Cloud features, regions, firewalls, networks, and DNS: `https://www.hetzner.com/cloud/`
- Hetzner Cloud volumes: `https://docs.hetzner.com/cloud/volumes/overview/`
- Hetzner Cloud Terraform provider: `https://registry.terraform.io/providers/hetznercloud/hcloud/latest`
- Railway private registry support: `https://docs.railway.com/builds/private-registries`
- Railway services and Docker image sources: `https://docs.railway.com/services`
- Tailscale subnet routers: `https://tailscale.com/kb/1019/subnets`

---

## Strategic Handover

This roadmap replaces "hybrid hosting as an operational side note" with a mature implementation plan:

> Coolify becomes a first-class deployment provider beside Railway for tenant runtimes and restartable services. Platform control-plane surfaces remain on Railway.

This is not a POC and not a "cheap server experiment." The first implementation must include target profiles, credential isolation, API lifecycle, audit, verification, rollback, backup, restore, and operator UI from day one.

Strategic boundary:

- Railway remains the home for platform/control-plane services.
- Coolify is for tenant runtime deployments and restartable/internal services.
- AWS/Azure/GCP remain future provider types. They are not part of `007`.
- Coolify must use the same provider-neutral platform model that later AWS/Azure providers would use.
- Do not add a one-off `provisioner: railway | coolify` flag when a real `targetProfileId` model is needed.
- Do not move Postgres, billing, Shopify webhooks, partner auth, or Platform backend to Coolify.

Why this matters:

- Railway is still the right place for customer-facing reliability and control-plane trust.
- Coolify improves runtime economics for repeatable per-tenant deployments and restartable workloads.
- Hetzner Cloud is the selected first host provider for Coolify because it gives the right cost/performance, regions, API surface, firewalls, networks, volumes, DNS, and Terraform/hcloud automation.
- The current code still selects provisioning by global `platform.provisioning.mode`; that blocks a clean hybrid future.
- The mature fix is target profiles plus provider adapters, not another global mode.

---

## Non-Negotiable Decisions

### 1. Platform Stays On Railway

Keep these on Railway:

- Platform UI
- Platform backend/control-plane API
- Platform shared Postgres
- Partner UI and partner API surface
- Shopify bridge service
- Shopify webhook receivers
- billing and entitlement control
- readiness audit UI and release verification orchestration
- deployment target profile administration
- provider credential storage and audit trail

Reason:

- these are customer-facing, trust-bearing, billing-bearing, or webhook-critical
- moving them creates operational risk without fixing the current margin problem

### 2. Coolify Owns Restartable Runtime Surfaces

Coolify may host:

- tenant runtime deployments
- Loom Companion runtime instances
- ai-fabric embedding workers
- vectorization core/runner services when they are restart-tolerant
- product integration workers that can retry safely
- shared inference services where memory/GPU economics matter
- scheduled/batch services whose failure can be replayed

Coolify must not host:

- Platform Postgres
- Shopify bridge webhooks
- partner authentication authority
- billing/entitlement authority
- source-of-truth deployment records
- readiness audit decision storage

### 3. Coolify Is A Provider Type, Not A Special Case

Add provider-neutral abstractions first:

- `DeploymentTargetProfile`
- `DeploymentProviderType`
- `DeploymentSourceArtifact`
- `DeploymentProviderCredential`
- `DeploymentProviderResourceHandle`
- `ServiceDeploymentSpec`

Then implement:

- `RAILWAY_API`
- `RAILWAY_STUB`
- `COOLIFY`

### 4. Source Strategy: Git First For Public Repo, Image For Hardened Release

For the current public-repository phase:

- Coolify should provision tenant runtimes from the same Git source metadata used by Railway.
- Use the public Coolify application API with `build_pack=dockerfile`, repo-root build context, and the runtime Dockerfile path.
- Disable Coolify auto-deploy by default so Platform remains the deployment initiator.

For the hardened production release path:

- deploy prebuilt OCI images from GHCR
- use immutable release tags and store image digests
- avoid building from repository inside Coolify once private registry and image provenance are ready

For Railway:

- keep existing Git-based deployment working
- add image-source support as a compatibility path where useful
- do not break the current Railway release flow while introducing Coolify

### 5. Mature From Day One Means Guarded, Not Reckless

No POC framing. But do not equate maturity with moving every workload immediately.

Day-one maturity requires:

- schema and API compatibility
- idempotent provisioning
- secret handling
- release audit
- operator UI
- logs/status/restart controls
- backup and restore plan
- health checks
- staging and production profiles
- rollback path
- release-gate verification

Deployment progression still happens through controlled gates:

- provider model
- image pipeline
- staging Coolify validation
- one restartable internal service
- one tenant runtime
- default target switch for new eligible runtimes
- explicit migration of existing runtimes only

Those gates are production-hardening gates, not POC stages.

---

## Host Provider Decision: Hetzner Cloud First

Use Hetzner Cloud as the first Coolify host provider for `007`.

Rationale:

- strong cost/performance for container runtimes
- regions in Europe, USA, and Singapore
- generous traffic economics compared with hyperscalers
- API coverage for servers, firewalls, private networks, volumes, primary/floating IPs, load balancers, placement groups, DNS, and storage boxes
- official `hcloud` CLI and Terraform provider
- `user_data`/cloud-init support for bootstrapping servers
- simple path from one host to multiple hosts without Kubernetes
- Coolify documentation itself recommends Hetzner as a self-hosted server option

Default choice:

- `HETZNER_CLOUD` for staging and first production Coolify hosts.

Recommended server profiles:

| Environment | Hetzner type | Role | Notes |
|---|---|---|---|
| Dev/staging | `CPX32` | `coolify-staging-01` | 4 shared vCPU, 8 GB RAM, 160 GB SSD; enough for provider contract tests and disposable apps |
| Production initial | `CCX23` | `coolify-prod-01` | 4 dedicated vCPU, 16 GB RAM, 160 GB SSD; first real production host |
| Production growth | `CCX33` or second `CCX23` | `coolify-prod-02` or resized host | prefer a second host for isolation when operationally ready |
| Production heavy | `CCX43` or multiple `CCX33` hosts | larger runtime pool | only after metrics prove saturation |

Initial recommendation:

- create staging on `CPX32`
- create production on `CCX23`
- scale by adding another `CCX23` or moving to `CCX33`
- avoid `CX`/`CAX` for production tenant runtimes unless the workload is proven low-CPU and non-critical

Deferred choices:

- Hetzner dedicated server or Server Auction only after runtime density justifies less elastic but cheaper raw capacity.
- AWS/Azure/GCP only when enterprise/BYOC, regional compliance, procurement, or customer-owned cloud requirements exist.
- Kubernetes/k3s only when Coolify single-host or small multi-host operation stops being enough.

Do not start with Hetzner dedicated servers for `007` unless the operator explicitly accepts the extra automation and replacement complexity. Cloud instances are easier to create, destroy, rebuild, tag, and verify through API-first workflows.

### Automation Boundary

Hetzner automates the host and network layer.

Coolify automates the application layer.

Platform remains the source of truth for deployments, target profiles, provider handles, and audit.

```text
Platform / IaC
  -> Hetzner API or Terraform
     -> server, firewall, network, volume, DNS, IPs
     -> cloud-init/bootstrap
     -> Coolify install
  -> Coolify API
     -> projects/environments/applications/envs/deployments/logs/restarts
  -> Platform DB
     -> target profile, provider handle, artifact, audit, verification
```

The Platform backend does not need to provision Hetzner hosts in the first implementation slice. Host provisioning can be Terraform or `hcloud` automation first. Later, if useful, add a `HostProvider` abstraction for Hetzner host lifecycle.

### Hetzner Resources To Manage

Required for staging and production:

- project-scoped Hetzner Cloud API token
- SSH key resource
- firewall resource
- private network resource
- server resource
- optional volume resource for Coolify data and persistent app volumes
- primary IP or floating IP strategy
- DNS records for Coolify and runtime wildcard domains
- labels on all resources

Credential source:

- The Hetzner Cloud token exists in a private local document supplied by the operator.
- Implementation sessions may read that private document only to load the token into a local environment variable or secret file.
- Never print, paste, commit, log, or summarize the token value.
- Prefer `HCLOUD_TOKEN_FILE=/tmp/hetzner_cloud_token.secret` or equivalent local secret-file handling.
- Do not copy the token into Terraform files, docs, shell history, or committed `.env` files.
- If a token is missing, stop with a clear blocker instead of creating partial manual infrastructure.

Recommended labels:

```text
loom.component=coolify
loom.environment=staging|production
loom.hostRole=coolify-controller
loom.owner=platform
loom.managedBy=terraform|hcloud
```

### Hetzner API/IaC Setup Steps

Automate with Terraform or `hcloud`; Terraform is preferred for day-one repeatability.

1. Create project-scoped API token.
2. Register SSH key.
3. Create firewall:
   - allow `22/tcp` only from admin IPs, Tailscale, or controlled VPN
   - allow `80/tcp` and `443/tcp` publicly
   - restrict Coolify dashboard/API port access behind Cloudflare Access, Tailscale, or IP allowlist
4. Create private network for future multi-host expansion.
5. Create staging server.
6. Create production server.
7. Attach volume if `/data/coolify` or app volumes need separate storage.
8. Create or assign primary/floating IP strategy.
9. Create DNS records:
   - `coolify.ops.loomai.pro`
   - `*.runtime.loomai.pro`
   - `*.runtime-staging.loomai.pro`
10. Pass cloud-init `user_data` to:
    - harden SSH
    - install base packages
    - configure firewall agent rules where needed
    - install monitoring/log shipping bootstrap
    - run Coolify install with controlled environment variables
    - disable Coolify auto-update in production
    - write bootstrap status to a known file
11. Verify Coolify health and first admin/API setup.
12. Store resulting Coolify base URL, version, API token, project UUID, environment UUID, server UUID, destination UUID, and APP_KEY escrow location in Platform/operator secrets and docs.

### Execution Checklist

Run this sequence for the first implementation session:

1. Load Hetzner token from the private document into a local secret file or environment variable without printing it.
2. Verify `hcloud` or Terraform can authenticate against the Hetzner Cloud project.
3. Choose primary region:
   - Europe/UK default: `nbg1` or `fsn1`
   - US East default: `ash`
   - US West default: `hil`
   - APAC default: `sin`
4. Create Terraform module or `hcloud` scripts for:
   - SSH key
   - firewall
   - private network
   - staging `CPX32`
   - production `CCX23`
   - optional volume
   - DNS records
5. Add cloud-init template for:
   - SSH hardening
   - package update
   - base tools
   - Tailscale or Cloudflare Access bootstrap if selected
   - Coolify install
   - `AUTOUPDATE=false` for production
   - bootstrap status log
6. Apply staging first.
7. Verify SSH, firewall, DNS, HTTP/HTTPS, and Coolify install.
8. Apply production only after staging bootstrap is repeatable.
9. Protect Coolify dashboard/API before real workloads.
10. Create Coolify project/environment records:
    - `loom-staging` / `staging`
    - `loom-production` / `production`
11. Generate Coolify API token and store it in Platform/operator secret storage, not in docs.
12. Configure GHCR read access on Coolify host.
13. Configure backups for Coolify state, `/data/coolify`, APP_KEY, SSH keys, and any app volumes.
14. Rehearse staging restore before production default switch.
15. Only then proceed to Platform target profiles and provider registry work.

### What Hetzner Does Not Replace

Hetzner does not replace:

- Platform deployment records
- Platform release verification
- Coolify application lifecycle
- runtime auth
- Railway Postgres backups
- Coolify app-volume backup policy
- operator audit

Hetzner snapshots and server backups are useful, but they are not enough. Hetzner volume docs explicitly treat volumes as separate resources; volumes and app data need their own backup plan.

---

## Service Placement Matrix

| Surface | Target | Rule |
|---|---|---|
| Platform UI | Railway | Always Railway |
| Platform backend/control plane | Railway | Always Railway |
| Platform shared Postgres | Railway | Always managed Railway |
| Partner UI | Railway | Always Railway until a separate frontend hosting decision is made |
| Shopify bridge service | Railway | Always Railway because webhooks and merchant admin must stay reliable |
| Shopify webhooks | Railway | Never Coolify |
| billing and entitlements | Railway | Never Coolify |
| readiness audit UI | Railway | Never Coolify |
| tenant runtime | Coolify eligible | Default to Coolify after `007` acceptance |
| tenant runtime requiring enterprise isolation | Coolify dedicated host or future provider | No shared host if contractual isolation is required |
| embedding worker | Coolify eligible | Retry-safe, restartable |
| vectorization runner | Coolify eligible | Retry-safe, schedule/replay capable |
| vectorization core | Coolify eligible | Only if no source-of-truth state is local-only |
| shared inference/Ollama | Coolify eligible | Resource-heavy, isolated by target profile |
| product integration workers | Coolify eligible | Only if idempotent/retry-safe |

Any placement change requires updating this matrix and the target profile seed data.

---

## Current Code Reality

The current Platform has a useful provider foundation, but it is still globally selected.

Current behavior:

- `DeploymentProvisioningProvider.supports(String mode)` selects by string mode.
- `DeploymentProvisioningService` filters providers using `platform.provisioning.mode`.
- `PlatformProvisioningProperties` stores Railway-specific source and environment settings globally.
- `DeploymentReleaseEntity.provisioningTarget` records the selected target string, but not a target profile or provider resource handle.
- Railway log, remediation, recovery, and preflight services contain Railway-specific assumptions.

Implication:

- adding Coolify by another global mode would let the whole process run in Coolify mode
- it would not let deployment A use Railway and deployment B use Coolify correctly
- it would create operational drift and make later AWS/Azure providers harder

Required fix:

- target profile selection must move onto deployment/template/release records
- provider dispatch must use `providerType`
- provider-specific config must move out of global properties and into target profile/credential records

---

## Target Architecture

### Control Plane

Railway-hosted Platform backend owns:

- target profile CRUD
- provider credentials
- artifact registry records
- deployment lifecycle
- provider dispatch
- audit log
- release verification
- rollout gating
- operator UI

### Provider Layer

Provider adapters implement one lifecycle:

```text
preflight(targetProfile)
compileSpec(deployment, version, targetProfile)
provision(spec)
deploy(handle, artifact)
start(handle)
stop(handle)
restart(handle)
delete(handle, retentionPolicy)
status(handle)
logs(handle, lineCount)
```

`RailwayApiProvisioningProvider` becomes the Railway adapter behind this lifecycle.

`CoolifyDeploymentProvider` implements the same lifecycle through the Coolify API.

### Runtime Layer

Coolify-hosted workloads are treated as remote runtime resources:

- Platform stores the Coolify UUID and FQDN.
- Platform does not trust Coolify as a source of truth.
- Platform can restart/redeploy/delete through provider handles.
- Runtime calls Platform over public HTTPS with deployment-scoped JWT.
- Tenant runtimes do not receive broad Platform DB credentials.

---

## Data Model

### `deployment_target_profiles`

Purpose: operator-controlled placement profiles.

Fields:

- `id`
- `name`
- `provider_type`: `RAILWAY_API`, `RAILWAY_STUB`, `COOLIFY`
- `environment_name`: `prod`, `staging`, `dev`
- `region`
- `active`
- `default_for_runtime`
- `default_for_restartable_services`
- `platform_services_allowed`: false for Coolify
- `source_strategy`: `GIT_SOURCE`, `IMAGE_SOURCE`
- `credential_ref_id`
- `provider_config_json`
- `network_policy_json`
- `resource_defaults_json`
- `created_at`
- `updated_at`

Coolify `provider_config_json`:

```json
{
  "baseUrl": "https://coolify.ops.loomai.pro",
  "projectUuid": "coolify-project-uuid",
  "environmentName": "production",
  "environmentUuid": "coolify-environment-uuid",
  "serverUuid": "coolify-server-uuid",
  "destinationUuid": "coolify-destination-uuid",
  "defaultPublicDomainSuffix": "runtime.loomai.pro",
  "apiVersionPinned": "4.0.0-beta.xxx",
  "deploymentPollIntervalSeconds": 5,
  "deploymentTimeoutSeconds": 600
}
```

### `deployment_provider_credentials`

Purpose: credential reference metadata only. Secret values stay in Platform secret storage.

Fields:

- `id`
- `name`
- `provider_type`
- `secret_ref`
- `created_at`
- `updated_at`
- `rotated_at`
- `status`

Secret payload names:

- `COOLIFY_API_TOKEN`
- `COOLIFY_BASE_URL`
- `COOLIFY_REGISTRY_USERNAME`
- `COOLIFY_REGISTRY_TOKEN`
- `COOLIFY_WEBHOOK_SECRET`

### `deployment_source_artifacts`

Purpose: immutable images produced by CI.

Fields:

- `id`
- `service_name`
- `artifact_type`: `OCI_IMAGE`
- `image_repository`
- `image_tag`
- `image_digest`
- `git_commit_sha`
- `build_run_id`
- `sbom_ref`
- `created_at`
- `promoted_at`
- `promotion_channel`: `staging`, `production`

Rules:

- deploy by digest where supported
- store tag and digest
- never let `latest` be a production source of truth

### `deployment_provider_resource_handles`

Purpose: immutable-ish mapping between Platform releases and provider resources.

Fields:

- `id`
- `deployment_id`
- `release_id`
- `target_profile_id`
- `provider_type`
- `resource_kind`: `APPLICATION`, `SERVICE`, `JOB`
- `provider_resource_uuid`
- `provider_project_uuid`
- `provider_environment_uuid`
- `provider_server_uuid`
- `fqdn`
- `status`
- `last_observed_status`
- `last_observed_at`
- `metadata_json`
- `created_at`
- `updated_at`

### Existing Release Record Changes

Extend `platform_deployment_releases`:

- `target_profile_id`
- `provider_type`
- `source_artifact_id`
- `provider_resource_handle_id`

Keep `provisioningTarget` during migration for backward compatibility, but stop using it as the dispatch source once target profiles are live.

---

## Provider Contract

Replace the current provider interface gradually.

Current:

```java
boolean supports(String mode);

ProvisioningResult provision(
    DeploymentEntity deployment,
    DeploymentVersionEntity version,
    DeploymentReleaseEntity release,
    ProvisioningProgressTracker progressTracker
);
```

Target:

```java
ProviderType providerType();

ProviderPreflightResult preflight(DeploymentTargetProfile profile);

ServiceDeploymentPlan compilePlan(
    DeploymentEntity deployment,
    DeploymentVersionEntity version,
    DeploymentReleaseEntity release,
    DeploymentTargetProfile profile,
    DeploymentSourceArtifact artifact
);

ProvisioningResult provision(
    ServiceDeploymentPlan plan,
    ProvisioningProgressTracker progressTracker
);

ProviderActionResult start(ProviderResourceHandle handle);
ProviderActionResult stop(ProviderResourceHandle handle, StopPolicy policy);
ProviderActionResult restart(ProviderResourceHandle handle, RestartPolicy policy);
ProviderActionResult delete(ProviderResourceHandle handle, DeletePolicy policy);

ProviderStatusSnapshot status(ProviderResourceHandle handle);
ProviderLogsSnapshot logs(ProviderResourceHandle handle, int lines);
```

Transition:

- keep the old interface temporarily as an adapter
- wrap Railway in the new provider registry first
- add Coolify only after the registry and target profile path are in place

---

## Coolify Provider API Surface

Coolify must be called through a dedicated client:

- `CoolifyApiClient`
- `CoolifyDeploymentProvider`
- `CoolifyTargetProfileValidator`
- `CoolifyProvisioningDetailsMapper`

Official API operations used:

| Operation | Endpoint | Use |
|---|---|---|
| health | `GET /health` | unauthenticated basic reachability |
| version | `GET /version` | authenticated version pin check |
| list applications | `GET /applications?tag=...` | idempotent lookup and drift checks |
| create image app | `POST /applications/dockerimage` | create runtime/worker from prebuilt image |
| get app | `GET /applications/{uuid}` | status/FQDN/readback |
| update envs bulk | `PATCH /applications/{uuid}/envs/bulk` | inject runtime env/secrets |
| deploy | `GET or POST /deploy` | redeploy by UUID/tag |
| start app | `GET or POST /applications/{uuid}/start` | start/deploy resource |
| stop app | `GET or POST /applications/{uuid}/stop` | stop resource |
| restart app | `GET or POST /applications/{uuid}/restart` | restart resource |
| app logs | `GET /applications/{uuid}/logs?lines=...` | diagnostics |
| delete app | `DELETE /applications/{uuid}` | explicit delete/cleanup |

Do not call Coolify directly from controllers. Controllers call Platform services; Platform services call the provider registry.

---

## Coolify Resource Naming And Tags

Names must be deterministic and short enough for dashboards:

```text
tenant-runtime-{deploymentShortId}
worker-{serviceName}-{environment}
runner-{deploymentShortId}-{purpose}
```

Tags:

```text
loom
provider:coolify
env:prod
kind:tenant-runtime
deployment:{deploymentId}
release:{releaseId}
service:{serviceName}
```

Rules:

- every Coolify application gets tags
- every Platform provider handle stores Coolify UUID
- if Platform has no handle but Coolify has matching tags, provider must report drift and require operator decision before adopting/deleting
- never delete an unknown Coolify resource because a name matches

---

## Image Pipeline

### Build Artifacts

GitHub Actions builds OCI images for:

- `ai-fabric-runtime`
- `ai-infrastructure-generic-rest-connector`
- `ai-fabric-embedding-worker`
- `ai-fabric-vectorization-core`
- `ai-fabric-vectorization-runner`
- `ai-fabric-product-integration-core`
- shared inference services as needed

Tag format:

```text
ghcr.io/loom-ai-labs/{service}:2026.04.29-{shortSha}
ghcr.io/loom-ai-labs/{service}:release-{releaseId}
```

Required metadata:

- git commit SHA
- build timestamp
- service name
- image digest
- optional SBOM reference

### Promotion

Promotion is explicit:

- build on merge
- promote to staging
- verify staging
- promote same digest to production

Do not rebuild for production after staging passes.

### Registry Auth

Coolify host:

```bash
docker login ghcr.io -u <github-user-or-bot> --password-stdin
```

Railway:

- private GHCR images require registry credentials on Railway Pro plan
- keep Git-based Railway deployment working until image-source Railway support is verified for this repo

---

## Coolify Infrastructure Configuration

### Server Baseline

Production host:

- Hetzner Cloud dedicated-vCPU server sized for real runtime density
- Ubuntu LTS
- SSH key only
- password login disabled
- root login disabled after bootstrap
- `ufw` enabled
- only required inbound ports open:
  - `22` from admin IP or VPN only
  - `80` and `443` public for tenant runtime FQDNs
  - Coolify dashboard/API behind VPN, Cloudflare Access, or restricted allowlist
- Docker installed by Coolify install flow
- monitoring agent installed
- log shipping configured
- backups configured before production workloads

Staging host:

- separate smaller Hetzner Cloud host
- same Coolify version track or one controlled upgrade ahead
- used for provider contract tests and upgrade rehearsal

Dedicated-server track:

- defer Hetzner dedicated servers or Server Auction until production runtime density proves that fixed larger hosts beat Cloud elasticity
- if introduced later, model them as a distinct host profile, not as a silent replacement for Cloud
- dedicated hosts must still expose the same Coolify provider contract and Platform target profile behavior

### Install Coolify

Follow the official Coolify installation flow and pin the version used for production.

For automated Hetzner bootstrap:

- pass cloud-init through Hetzner server `user_data`
- run the Coolify install script on a fresh Ubuntu LTS host
- set production `AUTOUPDATE=false`
- use install environment variables for initial root user only if the value source is secret-managed and not committed
- configure Docker address pool deliberately if Tailscale/private networks may overlap
- immediately protect the dashboard/API before real workloads are deployed

Required records after install:

- Coolify base URL
- Coolify version
- API token
- project UUID
- environment UUID/name
- server UUID
- destination UUID
- default domain suffix
- backup location
- APP_KEY escrow location

### Coolify Projects

Create:

```text
Project: loom-production
Environment: production
Server: coolify-prod-01
Destination: coolify-prod-docker-network
```

Create staging equivalent:

```text
Project: loom-staging
Environment: staging
Server: coolify-staging-01
Destination: coolify-staging-docker-network
```

### Domains

DNS:

```text
*.runtime.loomai.pro -> Coolify production host
*.runtime-staging.loomai.pro -> Coolify staging host
coolify.ops.loomai.pro -> Coolify dashboard/API through protected access
```

Tenant runtime FQDN:

```text
tenant-{deploymentId}.runtime.loomai.pro
```

Internal restartable services:

- no public FQDN unless explicitly needed
- use private network, localhost-to-host, or provider-managed internal naming
- if public exposure is needed for a health probe, require auth and IP restrictions

---

## Network And Security Model

### Platform To Coolify API

Preferred:

- Coolify API exposed through Cloudflare Access service token or Tailscale-accessible endpoint
- Platform backend stores access token in Platform secret storage
- no human dashboard credentials in deployment code

Allowed fallback:

- public HTTPS endpoint with strong bearer token and additional edge access policy

Not allowed:

- exposing Coolify dashboard/API to the public internet with only password login
- embedding Coolify API tokens in env vars visible to tenant runtimes

### Runtime To Platform

Tenant runtime calls Platform backend:

- public HTTPS
- deployment-scoped JWT
- short-lived or rotatable credentials
- Platform validates deployment id, tenant/store scope, tier, and status

### Platform To Runtime

Platform calls tenant runtime:

- public HTTPS using runtime FQDN
- deployment-scoped auth header or JWT
- rotate per-deployment secret on release
- rate limit and audit operator-triggered calls

### Coolify Services To Railway Postgres

Default rule:

- tenant runtime should not connect directly to Platform Postgres

If a restartable internal service needs Postgres:

- use role-scoped credentials
- require TLS
- put PgBouncer on Coolify host or use managed pooling
- limit schema access
- log connection count and slow queries
- prefer Platform API calls for control-plane decisions

### Tailscale

Use Tailscale when private cross-environment access is required:

- Coolify host joins tailnet as a tagged server.
- subnet routing may be used for controlled private routes.
- ACLs must restrict which Railway/ops services can reach Coolify internals.
- key expiry must be disabled or operationally managed for server nodes.

Do not make Tailscale a hidden dependency without health checks and a documented fallback.

---

## Service Deployment Spec

Every provider receives a provider-neutral spec:

```json
{
  "serviceName": "ai-fabric-runtime",
  "kind": "TENANT_RUNTIME",
  "image": {
    "repository": "ghcr.io/loom-ai-labs/ai-fabric-runtime",
    "tag": "release-r123",
    "digest": "sha256:..."
  },
  "network": {
    "public": true,
    "fqdn": "tenant-dep123.runtime.loomai.pro",
    "port": 8080,
    "forceHttps": true
  },
  "health": {
    "enabled": true,
    "path": "/actuator/health",
    "method": "GET",
    "expectedStatus": 200,
    "intervalSeconds": 10,
    "timeoutSeconds": 5,
    "retries": 6,
    "startPeriodSeconds": 45
  },
  "resources": {
    "memory": "1g",
    "cpus": "1.0"
  },
  "env": {
    "DEPLOYMENT_ID": "...",
    "PLATFORM_BASE_URL": "https://..."
  },
  "secretRefs": [
    "runtime-deployment-jwt",
    "openai-api-key"
  ],
  "volumes": [],
  "restartPolicy": "PROVIDER_MANAGED",
  "labels": {
    "loom.kind": "tenant-runtime",
    "loom.deploymentId": "..."
  }
}
```

Provider adapters translate this into Railway or Coolify requests.

---

## Resource Sizing

Initial sizing defaults:

| Tier/workload | Memory | CPU | Notes |
|---|---:|---:|---|
| Free tenant runtime | 512m | 0.5 | only if runtime is actually deployed for Free |
| Starter tenant runtime | 1g | 1.0 | default paid runtime |
| Elite tenant runtime | 2g | 1.5 | before governed writes |
| Elite Thinker/Resolver runtime | 3g | 2.0 | only after `006.x` needs it |
| embedding worker | 1g-2g | 1.0 | tune by queue latency |
| vectorization runner | 1g | 0.5 | scheduled/retry-safe |
| shared inference | host-specific | host-specific | isolated profile |

Remove old `Pro` terminology from this infrastructure track. Current product tier truth is `Free / Starter / Elite`.

---

## Coolify Provisioning Flow

### 1. Resolve Target Profile

Input:

- deployment
- template
- requested target profile override
- default target profile

Rules:

- Platform services cannot resolve to Coolify profiles.
- tenant runtimes may resolve to Coolify when the profile is active.
- restartable internal services may resolve to Coolify when `default_for_restartable_services=true`.
- existing Railway deployments remain Railway until explicitly migrated.

### 2. Resolve Artifact

Provider requires:

- image repository
- tag
- digest
- service name
- health contract

If no immutable artifact exists, block release.

### 3. Build Provider-Neutral Spec

Compile:

- env
- secret refs
- FQDN
- resource limits
- health checks
- labels/tags
- volume policy
- public exposure policy

### 4. Preflight Coolify

Check:

- Coolify `/health`
- authenticated `/version`
- version matches allowed range
- project/environment/server/destination UUIDs exist
- GHCR auth works on host
- DNS wildcard resolves to host
- dashboard/API access policy is active
- backup status is configured
- staging profile exists

### 5. Idempotent Create Or Reconcile

Provider behavior:

- if handle exists, read Coolify app by UUID
- if handle missing, list applications by tags/name
- if exact owned resource exists, adopt only with explicit metadata match
- otherwise create `POST /applications/dockerimage`
- bulk patch env vars
- trigger deploy/start
- poll status and health
- store provider handle

### 6. Verify Runtime

Checks:

- Coolify app status is running
- FQDN resolves and serves HTTPS
- app `/actuator/health` returns expected status
- Platform can call runtime with deployment auth
- runtime can call Platform with deployment JWT
- logs can be fetched and sanitized

### 7. Record Release Evidence

Store:

- target profile id
- provider type
- Coolify app UUID
- FQDN
- image tag/digest
- health result
- logs sample metadata
- deployment UUID from Coolify start/deploy request if returned
- verification run id

---

## Restartable Services

Restartable services are Platform-managed resources that can be stopped/restarted/redeployed without losing source-of-truth state.

### Eligibility Checklist

A service is Coolify-eligible only if:

- it has an OCI image artifact
- it can tolerate container restart
- it has a health check
- it does not own irreplaceable local state
- it can recover from queue/database state
- it emits structured logs
- it has bounded resource limits
- it has a documented rollback path

### Day-One Service Registry

Create a service registry in Platform:

```text
service_key
display_name
kind
default_target_profile_id
image_artifact_selector
restart_allowed
stop_allowed
delete_allowed
public_exposure_allowed
required_secret_refs
health_path
resource_defaults
owner_surface
```

Initial restartable candidates:

| Service | Coolify action | Notes |
|---|---|---|
| `ai-fabric-embedding-worker` | deploy/restart/logs | high-value first internal service |
| `ai-fabric-vectorization-runner` | deploy/restart/logs | scheduled/retry-safe |
| `ai-fabric-vectorization-core` | deploy/restart/logs | only if state is externalized |
| tenant runtime | deploy/restart/logs/delete | core goal |
| shared inference service | deploy/restart/logs | isolate by profile/host |

### Restart API

Add operator endpoints:

```text
POST /api/platform/deployment-resources/{handleId}/restart
POST /api/platform/deployment-resources/{handleId}/stop
POST /api/platform/deployment-resources/{handleId}/start
GET  /api/platform/deployment-resources/{handleId}/status
GET  /api/platform/deployment-resources/{handleId}/logs?lines=200
```

All endpoints require:

- operator/admin role
- audit reason
- provider handle exists
- provider action allowed for service kind
- rate limit
- audit event

Partners do not get raw restart controls.

---

## Operator UI

Add Platform UI surfaces.

### Target Profiles

Route:

```text
/operator/deployment-targets
```

Views:

- list target profiles
- active/inactive state
- provider type
- environment
- profile health
- default flags
- last preflight
- edit non-secret config
- rotate credential reference

### Provider Credentials

Route:

```text
/operator/provider-credentials
```

Rules:

- never show secret values
- show status, age, rotation time, last validation
- allow rotate by secret ref

### Source Artifacts

Route:

```text
/operator/source-artifacts
```

Show:

- service
- image tag
- digest
- commit
- build run
- promotion channel
- verification status

### Deployment Release Detail

Add:

- target profile
- provider type
- provider resource UUID
- FQDN
- image digest
- Coolify status
- logs
- restart/start/stop controls when allowed
- rollback action

### Service Placement Guard

If an operator tries to select Coolify for a Platform service:

- block the selection
- show the placement rule
- link to this roadmap

---

## Verification Suites

Add a standalone suite:

```text
coolify-provider-verification
```

Add it to full release readiness once production Coolify is active.

Stages:

1. target profile schema and seed validation
2. provider credential validation
3. Coolify health/version preflight
4. GHCR artifact availability
5. staging app create/reconcile
6. bulk env update
7. deploy/start/status polling
8. public FQDN health check for tenant runtime
9. restart action verification
10. logs retrieval and sanitization
11. stop/start verification for eligible service
12. delete cleanup for disposable staging resource
13. backup configuration proof
14. operator UI smoke

Evidence root:

```text
/tmp/coolify-provider-readiness/
```

Required artifacts:

- `summary.md`
- `commands.txt`
- `coolify-preflight.json`
- `target-profile-readback.json`
- `artifact-readback.json`
- `staging-provision-result.json`
- `runtime-health.txt`
- `restart-proof.txt`
- `logs-sanitization-proof.txt`
- `backup-restore-readiness.md`
- `operator-ui-proof.md`

Do not commit `/tmp` evidence.

---

## Tests To Add

Backend unit/integration:

- `DeploymentTargetProfileServiceTest`
- `DeploymentTargetProfileControllerTest`
- `DeploymentProviderCredentialServiceTest`
- `DeploymentSourceArtifactServiceTest`
- `DeploymentProviderRegistryTest`
- `CoolifyApiClientTest`
- `CoolifyDeploymentProviderTest`
- `CoolifyProvisioningIdempotencyTest`
- `CoolifyProviderPreflightServiceTest`
- `DeploymentReleaseTargetProfileSelectionTest`
- `DeploymentResourceActionServiceTest`
- `DeploymentResourceActionAuditTest`
- Railway backward-compatibility tests

Frontend:

- target profile list/edit smoke
- credential status smoke
- artifact list smoke
- release detail provider panel smoke
- restart/log controls visibility tests

Scripts:

- `scripts/verify-coolify-provider.sh`
- add suite catalog entry for `coolify-provider-verification`
- optional strict mode for real staging Coolify

---

## Security Requirements

### Secrets

- Coolify API token lives only in Platform secret storage.
- GHCR read token lives only in Platform secret storage and Coolify host registry config.
- Runtime secrets are injected as runtime variables, not build variables.
- Do not print env values in logs, provider details, or verification artifacts.
- Logs endpoint must redact known secret patterns.

### Authorization

Only operator/admin can:

- manage target profiles
- rotate provider credentials
- trigger provider actions
- view provider logs
- delete provider resources

Partners can see high-level deployment state only when needed for implementation support.

Merchants do not see Coolify, Railway, provider UUIDs, runtime logs, or restart controls.

### Audit

Every provider action writes:

- actor
- timestamp
- action
- reason
- deployment id
- provider handle id
- target profile id
- before/after status
- result
- error summary if failed

### Kill Switches

Global:

```text
platform.deployment.providers.coolify.enabled=false
```

Profile-level:

```text
deployment_target_profiles.active=false
```

Service-level:

```text
service_registry.restart_allowed=false
```

Release-level:

```text
deployment.release.targetProfileOverride=railway-prod
```

---

## Backup And Restore

Coolify instance backups are not application data backups.

Day-one backup requirements:

- Coolify dashboard database backup to S3-compatible storage
- secure escrow of Coolify `APP_KEY`
- backup Coolify SSH keys under `/data/coolify/ssh/keys`
- backup provider profile records in Platform DB through Railway Postgres backups
- backup persistent app volumes separately if any workload uses them
- document restore runbook
- rehearse restore on staging before production default switch

Tenant runtime rule:

- no irreplaceable tenant data should live only in Coolify volumes
- Lucene/index caches may be rebuilt or backed up as caches, but source-of-truth stays in Platform/product stores

Restore acceptance:

- fresh host installed with pinned Coolify version
- APP_KEY restored
- Coolify DB restored
- SSH keys restored
- one staging app restored or reprovisioned
- Platform handle reconciliation succeeds
- DNS cutover documented

---

## Observability

Minimum:

- Coolify host CPU/memory/disk alerts
- Docker container health alerts
- Coolify dashboard/API health monitor
- tenant runtime canary health monitor
- Coolify app status readback in Platform
- provider action failure alerts
- backup success/failure alerts
- disk usage alert at 70/80/90 percent

Logs:

- Coolify logs available from Platform release detail
- secrets redacted
- last 200/500/1000 line options
- logs are evidence, not source of truth

Dashboards:

- provider profile health
- tenant runtime count by target
- restart count by service
- failed deploy count
- average provision time
- host capacity and saturation
- cost estimate by target profile

---

## Rollback And Migration

### Existing Railway Deployments

Do not auto-migrate existing Railway deployments.

Migration requires:

- target Coolify profile active
- image artifact exists
- equivalent env/secret mapping
- staging verification
- tenant runtime health check
- explicit operator approval
- rollback Railway release still available

### New Deployments

After `007` acceptance:

- new eligible tenant runtimes default to Coolify
- Platform/control-plane services still default to Railway
- restartable internal services use Coolify only if service registry marks them eligible

### Rollback To Railway

Rollback steps:

1. switch deployment target profile override to Railway
2. apply release against Railway
3. verify runtime health and Platform calls
4. update deployment FQDN in Platform
5. stop Coolify runtime
6. keep Coolify app for retention window
7. delete after retention with audit

---

## Implementation Slices

These are production implementation slices, not POC phases.

### Slice 0 - Hetzner Host Automation Baseline

Goal:

- make the Coolify host layer reproducible before production workloads depend on it

Work:

- load the Hetzner Cloud token from the private local document into a local secret file or environment variable without exposing it
- create Terraform or `hcloud` automation for Hetzner Cloud staging and production hosts
- create staging as `CPX32` and initial production as `CCX23`
- create SSH key, firewall, private network, server, optional volume, and DNS records
- pass cloud-init user data for SSH hardening, package bootstrap, monitoring/log shipping bootstrap, and Coolify install
- disable production Coolify auto-update
- protect Coolify dashboard/API with Tailscale, Cloudflare Access, or explicit IP allowlist
- record Coolify host outputs for Platform target profile seeding
- document host rebuild procedure

Done when:

- Hetzner token handling is secret-file/env based and never committed
- staging Coolify host can be destroyed and recreated from automation
- production Coolify host can be created from the same module with production variables
- firewall and DNS are managed by automation
- Coolify install completes on a fresh host
- host outputs include Coolify base URL, version, server IP, and DNS names
- no app lifecycle work is done manually except first controlled Coolify API token/bootstrap where unavoidable

### Slice 1 - Target Profiles And Provider Registry

Goal:

- remove global provider mode as the dispatch source

Work:

- add target profile schema
- seed Railway profiles
- seed inactive Coolify staging/prod profiles
- add provider credentials metadata
- add source artifact table
- add provider resource handle table
- introduce provider registry by `ProviderType`
- adapt Railway provider into registry
- keep old `platform.provisioning.mode` as default profile fallback only

Done when:

- one deployment can resolve Railway through target profile
- current Railway apply path still works
- release summary shows target profile
- tests prove no behavior regression for Railway

### Slice 2 - Image Artifact Pipeline

Goal:

- produce immutable images for Coolify deployments

Work:

- add GitHub Actions image build workflow
- push images to GHCR
- store artifact metadata in Platform
- add artifact promotion concept
- support digest readback
- configure Coolify host GHCR auth

Done when:

- staging image artifact is visible in Platform
- image digest is recorded
- Coolify host can pull image manually and through API-created app

### Slice 3 - Coolify Provider Core

Goal:

- implement lifecycle operations

Work:

- implement `CoolifyApiClient`
- implement provider preflight
- implement create/reconcile app from Docker image
- implement env bulk update
- implement start/stop/restart/delete/status/logs
- implement provider details mapper
- implement idempotency and drift detection
- implement sanitized logs

Done when:

- mocked API tests pass
- staging Coolify contract test provisions and deletes disposable app
- provider action audit exists

### Slice 4 - Runtime Deployment Integration

Goal:

- deploy tenant runtime through Coolify target profile

Work:

- compile tenant runtime `ServiceDeploymentSpec`
- generate FQDN
- inject runtime env
- inject deployment-scoped auth
- poll Coolify status and runtime health
- store provider handle
- show in release UI

Done when:

- one staging tenant runtime deploys through Coolify
- Platform can call runtime
- runtime can call Platform
- logs/status/restart work from Platform

### Slice 5 - Restartable Service Registry

Goal:

- manage restartable services beside tenant runtimes

Work:

- add service registry
- register embedding worker/vectorization runner candidates
- add operator restart/log/status controls
- add service-level target profile default
- add audit and rate limits

Done when:

- one restartable internal service runs on Coolify staging
- operator can restart it from Platform
- failure and logs are visible

### Slice 6 - Operations Hardening

Goal:

- make Coolify production-operable

Work:

- backup automation
- restore drill
- host monitoring
- deployment canary
- DNS and TLS verification
- capacity dashboard
- rollback runbook
- staging upgrade rehearsal
- release-gate suite

Done when:

- restore drill is documented
- `coolify-provider-verification` passes
- production profile can be activated

### Slice 7 - Production Default Switch

Goal:

- make Coolify default for new eligible runtime deployments

Work:

- activate production Coolify target profile
- set `default_for_runtime=true`
- keep Railway default for Platform/control-plane services
- create first production tenant runtime through Coolify
- monitor for 7 days
- only then migrate selected existing Railway runtimes with explicit approval

Done when:

- new eligible runtime deployment defaults to Coolify
- no Platform service can select Coolify
- rollback to Railway has been tested

---

## Acceptance Criteria

`007` is complete only when:

- Hetzner Cloud is documented as the first Coolify host provider.
- Hetzner token is consumed only from private/local secret handling and is never committed, printed, or copied into docs.
- staging uses `CPX32` or a documented equivalent and initial production uses `CCX23` or a documented equivalent.
- Hetzner host creation is reproducible through Terraform or `hcloud` automation.
- Hetzner firewall, network, server, DNS, and optional volume setup are automated for staging and production.
- Railway remains the target for Platform UI/backend/Postgres/partner UI/Shopify bridge.
- Coolify exists as `ProviderType.COOLIFY`.
- target profiles replace global mode as the release dispatch source.
- Railway deployments still work through target profiles.
- Coolify target profiles support staging and production.
- Coolify credentials are secret-managed and rotatable.
- Platform builds or records immutable GHCR image artifacts.
- Coolify provider can create/reconcile/start/stop/restart/delete/status/logs.
- Coolify provider is idempotent and drift-aware.
- tenant runtime deploys successfully through Coolify.
- at least one restartable internal service deploys successfully through Coolify.
- operator UI shows profile, provider handle, logs, status, and restart controls.
- provider actions are audited.
- backup and restore drill is documented and rehearsed.
- `coolify-provider-verification` passes.
- full release readiness includes Coolify provider verification once production default is enabled.
- existing Railway runtimes are not auto-migrated.

---

## Blockers And Stop Conditions

Stop implementation if:

- Hetzner host setup is manual-only with no reproducible Terraform or `hcloud` path
- target profile dispatch is skipped in favor of another global mode
- Coolify credentials would be exposed to tenant runtime env
- Platform services become selectable for Coolify
- image artifacts cannot be made immutable
- Coolify API/dashboard cannot be protected beyond password login
- backup/restore cannot be rehearsed
- logs expose secrets
- provider actions lack audit events
- runtime source-of-truth data would live only on a Coolify host

---

## First Technical Session Prompt

Start with Slice 0 if Hetzner/Coolify infrastructure does not already exist in a reproducible form. Then move to Slice 1.

Read:

1. this file
2. Hetzner Cloud API and Terraform provider docs
3. Coolify self-hosted installation docs
4. `DeploymentProvisioningProvider.java`
5. `DeploymentProvisioningService.java`
6. `PlatformProvisioningProperties.java`
7. `RailwayApiProvisioningProvider.java`
8. `DeploymentReleaseEntity.java`
9. Railway provisioning tests

Implement:

- secret-safe loading of the Hetzner Cloud token from the private local document
- Hetzner host automation baseline if missing
- target profile schema
- provider type enum
- provider registry
- Railway adapter compatibility
- release target profile fields
- tests proving current Railway behavior still works

Do not implement Coolify API calls before target profile dispatch exists.

Do not print, commit, or paste the Hetzner token. If the token cannot be loaded safely, stop and report the blocker.

Handoff template:

```text
- 007 Coolify provider status: <complete/partial/blocked>.
- Slice completed: <slice>.
- Changed files: <compact list>.
- Decisions: <new decisions only>.
- Verification: <commands and pass/fail>.
- Live verification: <passed/skipped/blocker>.
- Blockers: <none or compact blockers>.
- Next handoff: <next concrete step>.
```

---

## 2026-05-01 Slice 0 Local Baseline

Status: host automation implemented, applied to Hetzner, imported into local Terraform state, and validated with Terraform.

Created a Terraform-compatible Hetzner Cloud baseline under `infra/coolify/hetzner` for:

- `coolify-staging-01` on `cpx32`, Ubuntu 24.04
- `coolify-prod-01` on `ccx23`, Ubuntu 24.04
- `nbg1` default location, switchable to `fsn1`
- Hetzner SSH key, shared firewall, private network/subnet, server resources, optional volumes, labels, and DNS record outputs
- cloud-init SSH hardening, package updates, base tools, host firewall, Coolify install, production `AUTOUPDATE=false`, and bootstrap status/log files
- secret-file based token wrapper scripts so `HCLOUD_TOKEN` can be loaded from `/tmp/hetzner_cloud_token.secret` without committing it
- Hetzner API fallback runner for environments where Terraform/OpenTofu is unavailable locally

DNS is currently output-only because no DNS provider credential was available in local context. Add a provider-specific DNS module after Cloudflare/Hetzner DNS credentials are available through the same secret-safe handling.

Live resources created:

- staging: `coolify-staging-01`, `cpx32`, `nbg1`, IPv4 `46.224.145.148`, IPv6 `2a01:4f8:c2c:83e2::1`
- production: `coolify-prod-01`, `ccx23`, `nbg1`, IPv4 `46.225.162.106`, IPv6 `2a01:4f8:1c18:c04::1`
- shared Hetzner SSH key, firewall, and private network
- Coolify installed on both hosts; local HTTP checks returned `302`
- generated Coolify root users created through SSH tunnels; credentials are stored only in `/tmp/coolify_admin_credentials.env`
- Coolify API enabled on both hosts; API tokens are stored only in `/tmp/coolify_api_tokens.env`
- Coolify version readback returned `4.0.0` for staging and production
- Coolify projects/environments created:
  - staging project `loom-staging` UUID `id069t43frp519u5i3dg2jpr`, environment `staging` UUID `h1433m09ezg882q7xmf3ae0x`
  - production project `loom-production` UUID `t1400k32bg9yd764chyt1slm`, environment `production` UUID `rn5sbycbix789i973okr9ugm`
- Coolify built-in server records now use hardened user `loomops` and validate as reachable/usable:
  - staging server UUID `zf25hgk9694bt7q0zwb98ado`, destination UUID `xjhfu65nacrr30xax5cp0ry7`, private key UUID `n117g3g8n75p6x048drc11on`
  - production server UUID `kvufjk78dj4wyhjgp1mlxecr`, destination UUID `r3thf2xmxcjn1tt2bclabebz`, private key UUID `bmllhht0k5m0gfkuk0ovwisz`
- SSH root login and password login disabled; UFW active on both hosts
- current firewall allows SSH and Coolify port `8000` only from the operator public IP used during setup, and allows public HTTP/HTTPS
- host UFW/fail2ban allow the local Coolify Docker address pool for self-validation after root SSH was disabled
- Coolify proxy is running and healthy on both hosts
- Coolify SSH deployment user ACL access is configured for local `/data/coolify` resource directories so Docker-image app deployments can write generated compose files
- local ignored Terraform state now contains the live SSH key, firewall, network, subnet, staging server, and production server
- Terraform server resources ignore imported create-time fields (`network`, `public_net`, `ssh_keys`, `user_data`) to avoid replacing adopted hosts

Verification completed:

- shell syntax checks for `infra/coolify/hetzner/scripts/*.sh`, including `apply-hcloud-api-baseline.sh`
- shell syntax check for `infra/coolify/hetzner/cloud-init/coolify-bootstrap.sh.tftpl`
- Terraform `1.6.6` installed into `/tmp/codex-tools/bin` after clearing local Homebrew/npm/pip caches for disk space
- `terraform init -backend=false`
- `terraform fmt -check -recursive`
- `terraform validate`
- Terraform import completed for SSH key `111657146`, network `12181920`, subnet `12181920-10.44.0.0/24`, firewall `10915120`, staging server `128757995`, and production server `128758153`
- Terraform applied one saved in-place firewall convergence plan with `0 added, 1 changed, 0 destroyed`
- post-apply `terraform plan -detailed-exitcode` returned `0`
- `git diff --check`
- local secret scan over the new infra files for direct token assignments
- Hetzner API resource readback confirmed both servers running with the requested types/region
- SSH hardening readback confirmed `PermitRootLogin no`, `PasswordAuthentication no`, and `KbdInteractiveAuthentication no`
- `sudo docker ps` confirmed Coolify containers healthy on both hosts
- root-user registration forms are gone; login forms are present after generated root-user creation
- Coolify API `/api/v1/version` returned `4.0.0` on both hosts
- Coolify API server readback confirmed `user=loomops`, `is_reachable=true`, and `is_usable=true` on both hosts
- strict staging disposable Docker-image smoke passed after the proxy/ACL fix: app creation, deployment, `running:unknown` status, HTTP routing through `sslip.io`, and cleanup confirmation

Verification blocked:

- DNS records were not created because `loomai.pro` currently uses registrar nameservers, not Hetzner DNS, and no registrar/DNS provider API credential was available.

Slice 1 plan prepared:

- current code anchor: `DeploymentProvisioningService` still selects providers by `PlatformProvisioningProperties.mode()`
- current code anchor: `DeploymentProvisioningProvider` still exposes `supports(String mode)`
- current code anchor: `DeploymentReleaseEntity` stores `provisioningTarget` but no target profile/provider/artifact/handle references yet
- add `DeploymentProviderType` values `RAILWAY_API`, `RAILWAY_STUB`, and reserved `COOLIFY`
- add `deployment_target_profiles` with seeded Railway-compatible defaults and inactive Coolify staging/prod profiles
- add nullable release fields for target profile, provider type, source artifact, and provider handle while preserving `provisioningTarget`
- introduce provider registry dispatch by target profile provider type
- wrap current Railway API and stub providers through the registry without changing Railway behavior
- add tests proving current Railway provisioning still works with target profiles present
- do not add Coolify API calls in Slice 1

---

## 2026-05-01 Slice 1 Target Profiles And Provider Registry

Status: implemented after rebasing `Platform-V8` onto `origin/main`; no Coolify app lifecycle or Platform Coolify API calls were added.

Implemented:

- `DeploymentProviderType` with `RAILWAY_API`, `RAILWAY_STUB`, and reserved `COOLIFY` values.
- Provider-neutral persistence for target profiles, provider credentials, source artifacts, and provider resource handles.
- Flyway migration `V76__deployment_target_profiles_and_provider_handles.sql`, numbered after the rebased `main` migrations through `V75`.
- Seeded active Railway target profiles and inactive Coolify staging/production profiles using only non-secret Coolify URLs and UUID metadata.
- Release metadata fields for target profile, provider type, source artifact, and provider resource handle while preserving legacy `provisioningTarget`.
- `DeploymentTargetProfileService` and `DeploymentProviderRegistry` dispatch so current Railway providers are selected through target profiles.
- Railway API/stub adapters now expose provider type while retaining legacy `supports(String mode)` compatibility.
- Regression tests for target-profile dispatch, missing Coolify adapter behavior, migration seeds, and existing verification-suite property behavior.

Verification completed:

- `mvn -f Platfrom/backend/pom.xml -q -Dtest=DeploymentProvisioningServiceTargetProfileTest,DeploymentTargetProfileMigrationTest,PlatformVerificationSuitePropertiesTest,PlatformVerificationSuiteServiceTest,PlatformVerificationSuiteExecutionServiceTest,PlatformVerificationSuiteScriptContextServiceTest test`
- `PATH=/tmp/codex-tools/bin:$PATH terraform -chdir=infra/coolify/hetzner init -backend=false`
- `PATH=/tmp/codex-tools/bin:$PATH terraform -chdir=infra/coolify/hetzner fmt -check -recursive`
- `PATH=/tmp/codex-tools/bin:$PATH terraform -chdir=infra/coolify/hetzner validate`
- `bash -n` for the Hetzner helper scripts and Coolify bootstrap template

Blockers:

- DNS remains skipped by request and because `loomai.pro` is not delegated to an API-backed provider in this repo context.
- Coolify app lifecycle, GitHub/GHCR credential wiring, provider API calls, backups/restore rehearsal, and dashboard/API hardening beyond IP allowlisting remain future slices.

Next handoff:

- Slice 2 should add a Coolify provider adapter skeleton behind the registry, still without creating application resources until source artifact and credential contracts are finalized.

---

## 2026-05-01 Slice 2/3 Backend Provider Core

Status: backend provider core implemented and locally/live testable; full production tenant runtime rollout remains blocked by DNS/GHCR/backup-hardening gates.

Implemented:

- `CoolifyApiClient` for `/api/v1` health/version, Docker image application create/update/list/get, env bulk update, start/stop/restart/delete, status, and logs.
- `CoolifyDeploymentProvider` behind `DeploymentProviderRegistry`.
- `CoolifyTargetProfileResolver` with secret-managed token resolution from `COOLIFY_STAGING_API_TOKEN` and `COOLIFY_PRODUCTION_API_TOKEN`.
- Provider lifecycle defaults on `DeploymentProvisioningProvider` for preflight/start/stop/restart/delete/status/logs.
- Source artifact records and API for Docker image artifact create/list/promote.
- Provider resource handle action API for status/logs/start/stop/restart/delete.
- Apply endpoint optional query params:
  - `targetProfileId`
  - `sourceArtifactId`
- Release execution now captures `providerResourceHandleId` from provisioning details.
- GitHub Actions workflow `.github/workflows/coolify-image-artifacts.yml` builds/pushes runtime and REST connector images to GHCR and uploads metadata JSON.
- Verification suite key `coolify-provider-verification` runs `scripts/verify-coolify-provider.sh`.

Live validation:

- Non-strict verifier passed against staging and production Coolify hosts:
  - staging `version=4.0.0`, `health=OK`, `applications=0`
  - production `version=4.0.0`, `health=OK`, `applications=0`
- Strict disposable staging smoke now creates, starts, observes `running:unknown`, and deletes an app. Cleanup confirms staging returns to zero applications.
- The smoke unblock required starting `coolify-proxy` and granting the hardened Coolify SSH user ACL access to `/data/coolify` resource directories; both are now encoded in host bootstrap.
- Temporary app routing uses `sslip.io` until the planned `loomai.pro` runtime wildcard DNS records are automated.

Remaining blockers:

- Custom DNS was intentionally skipped; without runtime DNS/FQDN, tenant runtime post-apply verification cannot be considered production-ready.
- GHCR read credential and host registry auth are not configured in Coolify yet; this is no longer a blocker for the public Git-source path, but remains required for the hardened image-source path.
- Coolify target profiles remain inactive in seed data until GHCR auth, DNS replacement for temporary `sslip.io`, backup/restore, and dashboard/API hardening gates are complete.
- Backup/restore rehearsal for Coolify state, APP_KEY, SSH keys, and app volumes is still pending.
- Operator UI integration is API-ready but not implemented in the frontend.

---

## 2026-05-01 Public Git-Source Railway Parity

Status: implemented locally. This changes the near-term Coolify path to match Railway while the repository is public.

Implemented:

- Coolify provider now supports `GIT_SOURCE` target profiles using the same `RailwayProvisioningPlanService` source repo, branch, runtime Dockerfile path, and runtime environment variables.
- Added Coolify public application create/update support through `/applications/public`.
- Existing Docker-image app support remains available behind `IMAGE_SOURCE`.
- Seeded Coolify staging/production target profiles are updated by `V78__coolify_public_git_source_profiles.sql` to `GIT_SOURCE` with `buildPack=dockerfile`, repo-root base directory, and the runtime Railway Dockerfile.
- Public Git source normalizes GitHub slugs such as `owner/repo` to `https://github.com/owner/repo.git` for Coolify.
- Coolify auto-deploy is disabled for provider-created public Git apps; Platform still triggers deployment explicitly.
- `scripts/verify-coolify-provider.sh` has optional `COOLIFY_PUBLIC_GIT_SMOKE=true` coverage that creates and deletes a disposable staging public Git application without triggering a deployment.

Verification completed:

- `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile`
- `mvn -f Platfrom/backend/pom.xml -q -Dtest=CoolifyDeploymentProviderTest,DeploymentTargetProfileMigrationTest,CoolifyTargetProfileResolverTest,DeploymentProvisioningServiceTargetProfileTest test`
- `bash -n scripts/verify-coolify-provider.sh`
- `COOLIFY_PUBLIC_GIT_SMOKE=true scripts/verify-coolify-provider.sh` against staging/prod Coolify `4.0.0`; it created a disposable public Git app, confirmed the Git repository readback, and cleanup returned staging to zero apps.

Remaining gates:

- Push/merge the deployment source branch before live Coolify Git app deployment if Coolify must build code that only exists locally.
- Run a full public Git app build/start/health smoke through the Platform apply path after target-profile activation policy is set.
- Replace temporary `sslip.io` runtime domains with real DNS before production tenant acceptance.
- Keep GHCR/private registry auth as the hardened image-source follow-up, not as a blocker for public-repo Git-source testing.
