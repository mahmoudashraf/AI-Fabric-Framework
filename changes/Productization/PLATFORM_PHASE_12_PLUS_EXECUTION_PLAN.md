# Platform Phase 12+ Execution Plan

Status: draft (2026-03-29)

This document is the follow-on execution plan for the new `Platfrom/` control plane implementation.

It starts from the **current implemented state**, not from the original greenfield product plan.

Related docs:

- `changes/Productization/CONFIGURABLE_AI_ENABLEMENT_PLATFORM_PLAN.md`
- `changes/Productization/PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md`
- `changes/Productization/VERIFICATION_PLAYBOOK.md`
- `changes/Productization/REMOTE_ACCESS_CONTROL_VIA_REST_CONNECTOR_PLAN.md`

---

## 1) Current Baseline

The current control-plane implementation already has these capabilities:

- deployment creation
- draft / version / release lifecycle
- config artifact compilation and delivery
- Railway plan generation
- Railway API provider implementation
- Railway preflight
- diagnostics and verification views
- structured draft editors for:
  - actions
  - connector routing / upstream / authz
  - providers
  - security
  - knowledge entities / vector-space structure

Current practical limitation:

- the platform has **not yet been proven end-to-end as the system that creates a live Railway deployment and carries it all the way through apply -> deploy -> verify -> active**

That missing loop is the next priority.

---

## 2) Main Goal Of The Next Phases

Move the platform from:

- configuration console with provisioning logic

to:

- real product control plane that can provision, apply, verify, and operate customer deployments on Railway

The target loop is:

1. create deployment
2. edit draft
3. publish version
4. apply version
5. provision/update Railway services
6. wait for deploy success
7. run post-deploy verification
8. show release state and evidence

---

## 3) Phase Ordering

Recommended next order:

1. Phase 12: Platform secrets + first real Railway apply
2. Phase 13: Apply progress and release-state UX
3. Phase 14: Stronger post-deploy verification
4. Phase 15: Docker/config cleanup and migration to platform-served config
5. Phase 16: Persistence hardening (Postgres + migrations)
6. Phase 17: Platform authentication and operator access control

This order is intentional:

- first prove that the platform can really deploy
- then make deployment execution observable
- then make verification deeper
- then remove baked deployment-config assumptions from Railway packaging
- then harden persistence
- then secure the platform itself

---

## 4) Phase 12: Platform Secrets + First Real Railway Apply

### 4.1 Objective

Make the platform able to perform a real deployment apply on Railway using the live Railway API flow already implemented.

### 4.2 Why this is next

Without this phase, the platform is still mostly a configuration and planning console.

The highest-value proof is:

- the platform itself provisions `runtime + rest-connector`
- the deployment receives valid config artifact URLs and required secrets
- the deployment reaches a verifiable active state

### 4.3 Scope

Implement a **bounded platform secrets model** and use it in real apply flows.

The minimum secrets for current deployment topology are:

- `OPENAI_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`

The platform also requires deployment environment values:

- `RAILWAY_API_TOKEN`
- `RAILWAY_WORKSPACE_ID`
- `PLATFORM_DEPLOY_REPOSITORY`
- `PLATFORM_DEPLOY_BRANCH`
- `PLATFORM_PUBLIC_BASE_URL`

### 4.4 Implementation tasks

- Add platform secret storage abstraction in backend.
- Start simple in V1:
  - store secret metadata in DB
  - store secret value encrypted at rest or use Railway-hosted platform envs as the first storage layer
- Add a bounded backend API for required platform deployment secrets.
- Add a simple UI screen or section for:
  - current secret presence
  - last updated time
  - edit / rotate
- Update Railway apply flow to read secrets from the platform secret layer instead of assuming local env only.
- Deploy the platform itself to Railway so:
  - `PLATFORM_PUBLIC_BASE_URL` is public and reachable
  - artifact URLs can be fetched by deployed runtime and connector services
- Run first real apply for one dev deployment.

### 4.5 Acceptance criteria

- Platform preflight reports `ready=true` in deployed environment.
- Platform can apply a published version and create or update:
  - Railway project
  - Railway environment
  - runtime service
  - rest-connector service
- Applied deployment receives public runtime and connector URLs.
- Diagnostics page shows non-stub provisioning evidence for the release.
- The release reaches at least `APPLIED` and does not stop in provisioning failure.

### 4.6 Out of scope

- full customer-managed secret vault integrations
- arbitrary env var editing
- multi-provider secret templates

### 4.7 Risks

- `PLATFORM_PUBLIC_BASE_URL` not reachable from Railway services
- secrets only present locally instead of in deployed platform environment
- Railway API staging/commit behavior differing slightly from assumptions

---

## 5) Phase 13: Apply Progress + Release-State UX

### 5.1 Objective

Make apply execution observable and operator-friendly.

### 5.2 Why this matters

A real deployment product cannot look “stuck” during provisioning.

Operators need to see:

- where the apply is
- whether it is waiting on Railway deploys
- whether verification has started
- why a release failed

### 5.3 Scope

Add explicit release/apply states to backend and UI.

Recommended state model:

- `DRAFT`
- `VERSION_PUBLISHED`
- `APPLY_REQUESTED`
- `PROVISIONING`
- `DEPLOYING`
- `VERIFYING`
- `ACTIVE`
- `FAILED`
- `ROLLED_BACK`

### 5.4 Implementation tasks

- Extend release entity/status transitions.
- Persist step-level apply progress.
- Add apply polling in the UI.
- Show Railway resource ids and deployment ids in diagnostics:
  - project id
  - environment id
  - runtime service id
  - connector service id
  - deployment ids
  - generated domains
- Distinguish:
  - provisioning failure
  - deployment failure
  - verification failure

### 5.5 Acceptance criteria

- Apply action moves through visible states in UI.
- Failures no longer appear as one generic provisioning error.
- Diagnostics page can show the release’s exact Railway execution trail.

---

## 6) Phase 14: Stronger Post-Deploy Verification

### 6.1 Objective

Upgrade verification from basic health checks to real deployment correctness checks.

### 6.2 Why this matters

A deployment can be “up” but still be wrong:

- actions not loaded
- routing config not loaded
- entity config not loaded
- connector admin surface misconfigured
- authz path not available

### 6.3 Scope

Extend verification to probe actual deployment readiness.

Recommended checks:

- runtime health
- connector health
- runtime config overview
- runtime actions overview
- runtime vector/entity overview
- connector admin overview
- connector authz status when enabled

If APIs are protected, verification should use deployment-aware credentials.

### 6.4 Implementation tasks

- Add or standardize admin/introspection endpoints as needed.
- Extend `DeploymentReleaseVerificationService`.
- Persist per-check details in release evidence.
- Surface the checks clearly in diagnostics and verification screens.

### 6.5 Acceptance criteria

- Verification can distinguish “service is alive” from “deployment is correct”.
- Failed config load appears as verification failure, not only as manual diagnosis.
- Release evidence includes detailed check payloads.

### 6.6 Out of scope

- synthetic end-user chat tests driven by LLM
- load/performance verification

---

## 7) Phase 15: Docker/Config Cleanup And Migration To Platform-Served Config

### 7.1 Objective

Make the Railway Dockerfiles and deployment packaging neutral, so customer deployment behavior comes from the platform-generated config artifacts rather than baked demo config inside the image.

### 7.2 Why this matters

The current Railway Dockerfiles are acceptable for V1 provisioning because they already work on Railway, but they still create architectural confusion:

- Dockerfiles look like they carry deployment config
- platform-generated config is the real source of truth
- demo fallback config can drift from platform-generated config

For the product model, the intended contract is:

- Dockerfile = packaging and startup
- platform artifacts = deployment config
- env vars = binding to one selected config version

That contract should be explicit in the codebase.

### 7.3 Scope

- clean Railway Dockerfiles so they are packaging-first, not demo-config-first
- remove or isolate baked config assumptions that are only there for demo deployments
- make platform-provided artifact URLs the clear and primary config path
- preserve a small, explicit local/dev fallback only where necessary

### 7.4 Implementation tasks

- audit both Railway Dockerfiles:
  - `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile`
  - `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile`
- identify which copied config files are truly required for bootstrap versus which are demo defaults
- remove product/domain-specific baked config from the Railway packaging path where platform envs already supply it
- keep startup behavior deterministic if required env vars are missing:
  - fail fast for platform-managed deployments
  - optionally retain clearly marked demo defaults in separate demo packaging or profile-specific paths
- document the new contract:
  - platform-managed deployment path
  - demo/manual deployment path
  - fallback behavior
- verify that Railway provisioning still succeeds after cleanup using platform artifact URLs for:
  - `AI_ACTIONS_CATALOG_PATH`
  - `AI_CONFIG_DEFAULT_FILE`
  - `REST_CONNECTOR_ROUTING_CONFIG_LOCATION`

### 7.5 Acceptance criteria

- Railway Dockerfiles no longer appear to be the source of runtime/connector config for platform-managed deployments
- a platform-provisioned deployment still builds and boots successfully on Railway
- runtime and connector clearly load config from platform artifact URLs in logs and diagnostics
- demo-specific config, if retained, is explicitly separated from platform-managed deployment behavior

### 7.6 Out of scope

- redesigning the overall deployment topology
- introducing hot reload for config
- replacing Railway Dockerfiles with a different packaging model

### 7.7 Notes

This phase is cleanup and architectural alignment, not a blocker for proving live provisioning. It should happen after live apply and verification are proven, because it reduces drift and confusion without changing the core control-plane contract.

---

## 8) Phase 16: Persistence Hardening

### 8.1 Objective

Move the platform backend from local H2-style persistence to durable product-grade persistence.

### 8.2 Why this matters

The current implementation is good for local development, but not for a real control plane.

You need durable storage for:

- deployments
- drafts
- versions
- releases
- verification runs
- secret metadata
- audit history

### 8.3 Scope

- move platform backend to Postgres
- add schema migrations
- make local/dev bootstrap explicit and controlled

### 8.4 Implementation tasks

- introduce Flyway or Liquibase
- define first stable schema
- move H2 to dev-only profile if still needed
- test upgrade path on existing local data if useful

### 8.5 Acceptance criteria

- backend runs cleanly on Postgres
- schema is migration-managed
- no runtime dependency on local file-based H2 in hosted environments

---

## 9) Phase 17: Platform Authentication And Operator Access Control

### 9.1 Objective

Protect the platform itself.

### 9.2 Why this matters

Once the platform can provision real deployments and manage secrets, it becomes a privileged system.

It cannot remain effectively unauthenticated.

### 9.3 Scope

Introduce platform auth and simple operator authorization.

Recommended V1 role model:

- `platform-admin`
- `platform-operator`
- optional `customer-operator`

### 9.4 Implementation tasks

- choose platform auth model:
  - simplest V1: session-based login or API key for internal operators
  - preferred next step: OIDC/SSO-compatible auth
- restrict:
  - secret editing
  - deployment apply / rollback
  - diagnostics access
  - verification reruns
- add audit trail for privileged actions

### 9.5 Acceptance criteria

- unauthenticated access to platform management APIs is blocked
- privileged actions are role-restricted
- secret updates and apply operations are auditable

---

## 10) Suggested “Definition Of Done” For The Platform MVP

The platform MVP should be considered operationally complete only when all of the following are true:

- a new deployment can be created from the platform UI
- draft config can be edited through structured forms
- a version can be published
- the platform can apply that version to Railway
- runtime and connector are created or updated successfully
- verification proves the deployment is configured correctly
- diagnostics show release evidence and Railway details
- release history is durable
- the platform itself is authenticated

If any of those are missing, the platform is still in implementation mode rather than operational product mode.

---

## 11) Recommended Immediate Next Step

Start **Phase 12** now.

Concrete first milestone:

1. add platform secret storage and management
2. deploy the platform itself to Railway with:
   - `RAILWAY_API_TOKEN`
   - `RAILWAY_WORKSPACE_ID`
   - `PLATFORM_DEPLOY_REPOSITORY`
   - `PLATFORM_DEPLOY_BRANCH`
   - `PLATFORM_PUBLIC_BASE_URL`
3. run the first real apply from the platform
4. capture the result in diagnostics

That is the highest-value proof point for the current product direction.

---

## 12) What Not To Do Next

Avoid spending the next phase on:

- more editor polish without real deployment apply
- hot-reload config work
- manual duplication of baked Railway config into the platform as a parallel path
- Shopify-specific implementation before platform apply is proven
- broad secret templating for every provider before the first real apply works
- generic plugin ecosystems or advanced tenanting

The bottleneck is not more configuration UX.

The bottleneck is proving that the platform can provision and operate real deployments end to end.
