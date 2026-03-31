# Configurable AI Enablement Platform Plan (Control Plane + Configurable Deployments) - V1

Status: draft (2026-03-28)

This document describes a realistic product plan for turning AI Fabric into a **fully configurable AI enablement platform** for customer domains, while keeping the core runtime stable and reusable across deployments.

It is written for the product direction agreed in recent implementation discussions:

- Runtime should stay **generic and unchanged** across customer deployments.
- The REST connector should act as the **public app-facing proxy** and integration bridge.
- The platform should allow customers and operators to configure:
  - actions
  - entities / vector spaces
  - providers
  - routing
  - authorization behavior
- V1 is allowed to use **redeploy-on-apply** instead of hot reload.
- Railway is the initial hosted deployment target.
- Shopify is an important future consumer of this platform, but not the platform boundary itself.

Related docs:

- `changes/Productization/PRODUCTIZATION_IMPLEMENTATION_PLAN.md`
- `changes/Productization/PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md`
- `changes/Productization/RUNTIME_AUTHORIZATION_AND_ACCESS_CONTROL_GUIDE.md`
- `changes/Productization/REMOTE_ACCESS_CONTROL_VIA_REST_CONNECTOR_PLAN.md`
- `changes/Productization/GENERIC_REST_API_CONNECTOR_GUIDE.md`
- `changes/Productization/GENERIC_REST_CONNECTOR_ADOPTION_ROADMAP.md`
- `changes/Productization/SHOPIFY_APP_IMPLEMENTATION_PLAN.md`
- `changes/Productization/SHOPIFY_ADMIN_APP_UI_PLAN.md`
- `changes/Productization/SHOPIFY_CHAT_WIDGET_V1_PLAN.md`

---

## 0) Executive Decision

### 0.1 The operating model

The platform should be built around a **control plane / data plane split**:

- **Control plane** is the product:
  - stores customer/domain configuration
  - stores deployment templates
  - versions configuration
  - provisions environments
  - applies releases
  - verifies deployments
  - provides UI and APIs for operators/customers

- **Data plane** is the deployed AI system:
  - AI Fabric runtime
  - REST connector
  - optional vector DB
  - optional customer-side integrations

### 0.2 Core V1 rule

The **runtime image** and **REST connector image** should remain the same across all customer deployments.

Customer/domain variation should come only from:

- configuration artifacts
- environment variables
- secrets
- deployment templates

### 0.3 V1 rollout model

V1 should use:

- **structured config editing in the platform**
- **publish a versioned config bundle**
- **redeploy-on-apply**

V1 should **not** depend on hot reload for core correctness.

This is simpler, safer, and better aligned with the current codebase.

---

## 1) Product Goal

Build a configurable AI enablement system that lets your company onboard customer domains without writing a new product implementation for each customer.

The platform should support:

- a standard runtime
- a standard connector
- customer/domain-specific action catalogs
- customer/domain-specific vector spaces and indexing schemas
- configurable provider combinations
- configurable routing and authorization
- consistent deployment and verification workflows

The result should be:

- reusable for multiple industries
- good enough for consultancy delivery
- mature enough to evolve into a product

---

## 2) Design Principles

### 2.1 Immutable runtime, mutable configuration

The platform should treat runtime and connector services as **immutable executables**.

Configuration changes should be applied through:

- new config versions
- controlled rollout
- verification
- rollback

### 2.2 Platform owns truth

The source of truth should be the **platform database**, not files mounted manually into the runtime or connector.

The platform may compile its internal structured config into YAML artifacts, but those YAML files are **deployment outputs**, not the primary product model.

### 2.3 Version everything

Every deployment should point to an explicit **published config version**.

No deployment should depend on:

- ad hoc file edits
- mutable manual Railway variables without release history
- hidden operator state

### 2.4 Separate safe change classes

Not every type of change is equally safe:

- action catalog changes are relatively lightweight
- connector route changes are moderate risk
- entity/vector-space changes are schema-like and may require reindex
- provider changes usually require redeploy and sometimes migration

The platform should reflect this in validation and rollout UX.

### 2.5 Control plane first, Shopify second

Shopify should be treated as a **consumer of the platform**, not the platform itself.

That means:

- first build the platform deployment primitive
- then let Shopify request and bind deployments

This reduces rework and keeps the product generic.

---

## 3) V1 Product Boundaries

### 3.1 What the platform manages

The control plane should manage:

- deployment templates
- action definitions
- entity/vector-space definitions
- provider profiles
- connector route mappings
- authorization mode/settings
- secrets references
- release versions
- deployment history
- verification results

### 3.2 What the runtime does

The runtime remains responsible for:

- chat orchestration
- intent extraction
- confirmations
- retrieval/generation
- action dispatch
- managed indexing when enabled

### 3.3 What the REST connector does

The REST connector should serve as:

- the public app-facing proxy in hosted V1 setups
- the runtime-facing action executor bridge
- the runtime-facing authz proxy by default
- an optional runtime API alias for some deployments

The connector may run:

- beside the runtime in your hosted environment
- inside the customer environment later

### 3.4 What the platform does not do in V1

V1 should not try to support:

- arbitrary live config mutation in running services
- hot-reloaded entity schema changes
- arbitrary runtime environment-variable editing by customers
- full enterprise multi-tenant data-plane runtime
- custom Docker image builds per customer release

---

## 4) Stable Contracts

These contracts should remain stable and productized.

### 4.1 Public orchestration contract

- `POST /api/chat/query`

Used by:

- customer apps
- future Shopify app backend
- future widgets/backend proxies

### 4.2 Customer connector contract

- `POST /actions/execute`
- `POST /retrieval/search` (optional)

Used by runtime to reach customer/business logic and optional external retrieval.

### 4.3 Managed ingestion contract

- Data Sync push API

Used when AI Fabric owns indexing/vector search.

### 4.4 Platform deployment contract

The platform should define an internal deployment contract that fully describes a data-plane deployment.

This becomes the basis for:

- diff
- publish
- apply
- verify
- rollback

---

## 5) Deployment Topology (V1)

### 5.1 Railway default

For V1, each customer environment should be provisioned as:

- one Railway project per customer environment
- one `runtime` service
- one `rest-connector` service

Optional later:

- one managed vector DB per customer environment when required

### 5.2 Why one project per environment

This gives:

- clean isolation
- simpler deletion/deprovisioning
- simpler secret scoping
- simpler support and diagnostics
- simpler rollback reasoning

### 5.3 Dev deployment profile

The default dev template should optimize for fast provisioning:

- runtime
- rest connector
- Lucene vector DB in runtime, or another cheapest default
- standard verification endpoints enabled
- product authz configurable, dev defaults only when explicitly selected

### 5.4 Later deployment variants

Deployment templates should allow later variants such as:

- different LLM providers
- different embedding providers
- external retrieval instead of managed indexing
- different vector DB providers
- customer-hosted connector mode

---

## 6) Configuration Ownership Model

### 6.1 Platform DB is canonical

The platform should store structured configuration in the database.

Example config domains:

- actions
- routes
- entities
- provider profiles
- authz settings
- deployment template choices

### 6.2 Compiled artifacts are delivery format

The platform should compile database records into deployment artifacts:

- `ai-actions.yml`
- `ai-entity-config.yml`
- `actions-routing.yml`
- `deployment-manifest.json`

These are the deployment-consumable outputs for runtime and connector.

### 6.3 Advanced YAML should exist, but not as primary UX

Provide:

- import YAML
- export YAML
- advanced/raw editor

But the primary UI should be structured forms and tables.

This matters because a consultancy product needs:

- repeatable validation
- diff
- templating
- auditability

Those are much harder if raw YAML is the main data model.

---

## 7) What Should Be UI/API Configurable

### 7.1 Actions

Each action should be configurable with:

- `name`
- `description`
- `category`
- `accessMode`
- `requiresConfirmation`
- `confirmationMessage`
- parameters
- parameter schema
- enabled/disabled flag

### 7.2 Connector route mappings

Each action route should be configurable with:

- upstream method
- upstream path
- request templates
- response templates
- upstream auth mode/profile
- timeout profile
- retries/idempotency policy

### 7.3 Entities / vector spaces

Each entity type should be configurable with:

- entity type / vector-space name
- searchable fields
- embeddable fields
- metadata fields
- feature flags
- CRUD indexing behavior
- indexing enabled/disabled

### 7.4 Providers

Provider configuration should be managed through profiles:

- LLM provider
- chat model
- embedding provider/model
- embedding dimensions
- vector DB type/profile
- retrieval mode

### 7.5 Security and platform settings

Expose controlled settings for:

- admin auth enabled
- authz mode
- authz upstream base URL/profile
- CORS/browser policy
- connector inbound auth policy

Do not expose arbitrary runtime or connector env vars directly as a customer feature.

---

## 8) Deployment Version Lifecycle

The platform should implement a strict versioned workflow.

### 8.1 Draft

Customer/operator edits a **draft**.

The draft is mutable and not live.

### 8.2 Validate

Platform validates:

- schema correctness
- cross-reference correctness
- deployment compatibility
- safety rules

### 8.3 Publish

Platform creates an immutable **deployment version**.

A deployment version should contain:

- compiled config artifact references
- effective provider settings
- effective deployment template
- content hashes
- migration flags such as `reindexRequired`

### 8.4 Apply

Platform updates the target deployment to use that version and triggers redeploy.

### 8.5 Verify

Platform runs verification checks automatically.

### 8.6 Promote or rollback

If verification succeeds:

- mark the release active

If verification fails:

- show diagnostics
- optionally auto-rollback

---

## 9) Platform Data Model

The following entities are recommended for V1.

### 9.1 Tenant and project model

- `organization`
- `project`
- `environment`
- `deployment`

### 9.2 Config model

- `deployment_template`
- `provider_profile`
- `vector_db_profile`
- `secret_ref`
- `action_definition`
- `action_param_definition`
- `connector_route_definition`
- `entity_schema_definition`
- `searchable_field_definition`
- `embeddable_field_definition`
- `metadata_field_definition`
- `authz_policy_profile`

### 9.3 Release model

- `deployment_draft`
- `deployment_version`
- `deployment_release`
- `verification_run`
- `audit_event`

### 9.4 Suggested semantics

- A `deployment` is the live environment identity.
- A `deployment_draft` is the current editable work state.
- A `deployment_version` is immutable and publishable.
- A `deployment_release` links a deployment to one active version.

---

## 10) Platform API Blueprint

### 10.1 Deployment management

- `POST /deployments`
- `GET /deployments/{id}`
- `GET /deployments/{id}/status`
- `POST /deployments/{id}/apply/{versionId}`
- `POST /deployments/{id}/rollback`
- `GET /deployments/{id}/versions`

### 10.2 Draft editing

- `POST /deployments/{id}/drafts`
- `GET /deployment-drafts/{id}`
- `PUT /deployment-drafts/{id}/actions`
- `PUT /deployment-drafts/{id}/routes`
- `PUT /deployment-drafts/{id}/entities`
- `PUT /deployment-drafts/{id}/providers`
- `PUT /deployment-drafts/{id}/security`

### 10.3 Validation and publishing

- `POST /deployment-drafts/{id}/validate`
- `POST /deployment-drafts/{id}/publish`

### 10.4 Artifact and diagnostics APIs

- `GET /config-bundles/{deploymentId}/{versionId}/ai-actions.yml`
- `GET /config-bundles/{deploymentId}/{versionId}/ai-entity-config.yml`
- `GET /config-bundles/{deploymentId}/{versionId}/actions-routing.yml`
- `GET /deployments/{id}/verification`
- `GET /deployments/{id}/diff/{versionId}`

### 10.5 Secrets APIs

- `POST /secrets`
- `PUT /secrets/{id}`
- `GET /secrets/{id}/usage`

The UI should never display raw secret values after creation.

---

## 11) Validation Rules

The validator should run before publish.

### 11.1 Action validation

- action names must be unique
- normalized names must not collide
- route mappings must reference existing actions
- required params must be declared
- request templates must only reference declared params
- write actions should require confirmation by default

### 11.2 Entity validation

- entity type names must be unique
- field names must be valid
- unknown field references are rejected
- prompt or action references to vector spaces must resolve

### 11.3 Provider validation

- selected LLM profile must be complete
- embedding dimensions must be compatible with the vector DB
- managed indexing requires compatible embedding/search configuration

### 11.4 Deployment validation

- all required secrets must be bound
- required connector base URLs and upstream profiles must exist
- changes that require reindex should be flagged
- disallowed combinations should fail fast

---

## 12) Config Delivery Strategy

### 12.1 Recommended V1 approach: immutable config bundle URLs

The platform should publish compiled config artifacts to immutable URLs.

Each deployment version should reference exact URLs for:

- `ai-actions.yml`
- `ai-entity-config.yml`
- `actions-routing.yml`

The deployment then points runtime/connector at those versioned locations.

### 12.2 Why this is better than mounted files for V1

- better fit for hosted Railway automation
- easier versioning
- easier rollback
- no custom image build per customer edit
- easier diagnostics and reproducibility

### 12.3 Why this is better than env-var-only config

- large YAML/config payloads do not fit cleanly into env vars
- artifacts are diffable and hashable
- avoids brittle string encoding problems

---

## 13) Railway Provisioning Model

### 13.1 One environment = one Railway project

On `POST /deployments`, the platform should:

- create a Railway project
- create `runtime` service
- create `rest-connector` service
- apply environment variables
- store resulting service URLs and metadata

### 13.2 Service configuration

The deployment worker should set:

- provider env vars
- connector base URL settings
- config artifact location settings
- authz settings
- admin auth settings
- CORS settings

### 13.3 Apply operation

Applying a version should:

- publish config artifacts
- update env vars for the target version
- trigger redeploy for runtime and connector
- wait for health
- run verification

### 13.4 Rollback operation

Rollback should:

- re-point the deployment to the last good config version
- redeploy
- re-run verification

### 13.5 Railway Public API feasibility

Railway is a valid V1 provisioning backend for this platform.

Railway's Public API is GraphQL-based and supports the main operations needed for this plan:

- create project
- create service
- create environment
- create/update variables
- trigger deployment / redeploy
- inspect deployment status

This means the control plane does not need to rely on manual dashboard operations for every deployment.

### 13.6 Recommended token model

For your hosted platform V1, the recommended Railway auth model is:

- use a **workspace token** in the platform backend
- provision customer environments inside your Railway workspace

This is the best fit because:

- the platform will create many customer projects
- the platform needs broad workspace-scoped automation
- the platform should not require each customer to own Railway directly in V1

Later options:

- use project tokens for narrower environment-scoped automation
- use OAuth only if customers later connect their own Railway accounts

### 13.7 Recommended provisioning sequence

When a user requests a new deployment from your platform, the platform backend should:

1. create a Railway project for the customer environment
2. create the `runtime` service
3. create the `rest-connector` service
4. optionally create additional resources later such as a managed vector DB service
5. publish immutable config artifacts for the target deployment version
6. upsert variables for runtime and connector
7. trigger or commit deployment changes in Railway
8. wait for deployment state transitions
9. run verification against the deployed endpoints
10. mark the deployment healthy or roll it back

### 13.8 Suggested initial Railway variable strategy

The platform worker should set variables in a structured way rather than exposing ad hoc free-form settings.

Examples:

- runtime config artifact location
- connector routing config artifact location
- action connector base URL
- provider credentials and model selections
- authz settings
- admin auth settings
- CORS/browser settings
- platform-managed config version identifier

This keeps deployments reproducible and easier to support.

### 13.9 Deployment state handling

The platform should explicitly model Railway deployment states during rollout.

At minimum:

- provisioning requested
- services created
- variables applied
- deploying
- active
- failed
- rolled back

The control plane should not mark a deployment as ready merely because Railway accepted the mutation.

It should only mark it healthy after:

- Railway reports the deployment as active
- verification checks pass

### 13.10 Why Railway is sufficient for V1

Railway is sufficient for the V1 deployment engine because it already provides the primitives needed for:

- project-per-customer-environment isolation
- multi-service deployment
- environment variable management
- redeploy/restart workflows
- deployment status inspection

This lets the platform focus its engineering effort on:

- configuration modeling
- validation
- release/versioning
- verification
- rollback logic

instead of building custom infrastructure orchestration too early.

---

## 14) Required Runtime and Connector Changes

The current codebase is already close, but a few changes are needed to productize this cleanly.

### 14.1 Runtime config source improvements

The runtime should support explicit config paths for:

- action catalog
- entity config

The current action catalog path is already config-driven via:

- `AI_ACTIONS_CATALOG_PATH`

The entity config loader should also be exposed as an explicit deployment-facing path, and should support remote artifact loading if the chosen artifact strategy uses `http(s)` URLs.

### 14.2 Connector routing config delivery improvements

The REST connector already loads routing config from `rest-connector.routingConfigLocation`.

For platform deployment use, it should support:

- file paths
- optionally remote `http(s)` URLs if that is the chosen bundle strategy

### 14.3 Config introspection endpoints

Add admin/read-only overview endpoints that expose:

- loaded config version
- loaded action source location
- loaded entity config location
- loaded routing config location
- effective provider summary

This is essential for support and verification.

### 14.4 Keep hot action refresh optional

The DB-backed action registry can remain as an optional feature for later.

It is useful, but it should not be the primary rollout mechanism in V1.

### 14.5 Do not implement hot entity reload in V1

Entity/vector-space changes are schema-like and should continue to require version publish + redeploy.

---

## 15) UI Product Scope

### 15.1 Core screens

- `Deployments`
- `Templates`
- `Actions`
- `Knowledge`
- `Providers`
- `Security`
- `Verification`
- `Revisions`
- `Diagnostics`

### 15.2 Suggested UX model

- structured editors as default
- diff view before publish
- publish/apply button
- verification status panel
- rollback button

### 15.3 Advanced mode

Advanced mode can provide:

- raw YAML import/export
- expert-only overrides

But this should not replace structured editing for the main product path.

### 15.4 Recommended V1 UI technology stack

For the platform UI, the recommended V1 stack is:

- `React`
- `TypeScript`
- `Vite`
- `Material UI`
- `@tanstack/react-query`
- `react-hook-form`
- `zod`
- `Monaco Editor` for advanced YAML/JSON editing only

### 15.5 Why this stack is recommended

This stack is the most pragmatic fit for the platform UI because the product is primarily an authenticated control-plane console, not a public marketing site.

The main UI needs are:

- dense tables
- configuration forms
- wizards
- status pages
- dialogs
- polling and async workflow state
- diff/revision flows

`Material UI` is well suited for this kind of internal/product console.

`@tanstack/react-query` is the correct data-fetching layer for:

- deployment lists
- deployment status polling
- verification results
- apply/rollback mutations
- cache invalidation after config changes

`react-hook-form` and `zod` are a good fit for large structured configuration forms with client-side validation.

### 15.6 Why Next.js is not required for V1

Next.js is optional, not required.

For this platform, a plain React SPA is enough because:

- the control-plane backend is already a separate service
- the UI is primarily an authenticated admin/product console
- the product does not depend on server-side rendering
- the main complexity is operational workflows, not public content rendering

Next.js can still be considered later if you want:

- a combined frontend/backend web app
- server-side auth/session handling in the UI layer
- marketing/docs and product console in a single app

For V1, `React + Vite + Material UI` is the simpler and lower-friction path.

---

## 16) Phased Implementation Plan

### Phase 0 - Freeze the deployment contract

Goal:

- define the deployment version model and what artifacts are required

Deliverables:

- deployment manifest schema
- artifact schema for actions/entities/routes
- release lifecycle states

Done when:

- one deployment version can be represented deterministically as data plus artifact references

### Phase 1 - Build the control-plane backend skeleton

Goal:

- create the platform service and core database model

Deliverables:

- DB schema
- deployment CRUD APIs
- draft/version/release tables
- secrets storage abstraction

Done when:

- a deployment draft can be created and stored with structured config

### Phase 2 - Build the config compiler

Goal:

- compile structured DB config into runtime/connector artifacts

Deliverables:

- compiler for `ai-actions.yml`
- compiler for `ai-entity-config.yml`
- compiler for `actions-routing.yml`
- config hash and diff support

Done when:

- a draft can be validated and published into immutable versioned artifacts

### Phase 3 - Make runtime and connector deployment-friendly

Goal:

- align runtime and connector config loading with platform-managed artifacts

Deliverables:

- explicit entity-config deployment path
- connector routing-config deployment path hardening
- config overview/admin endpoints
- optional remote artifact support if selected

Done when:

- runtime and connector can boot deterministically from platform-published versioned config sources

### Phase 4 - Build the Railway deployment worker

Goal:

- provision and update deployments automatically

Deliverables:

- Railway project creation
- service provisioning
- env var application
- deploy/apply/rollback job flow

Done when:

- `POST /deployments` produces a live environment with runtime and connector URLs

### Phase 5 - Build verification and release safety

Goal:

- make deployments supportable and auditable

Deliverables:

- verification runner
- health checks
- action overview verification
- vector-space verification
- connector route verification
- audit events

Done when:

- a release is only marked healthy after verification passes

### Phase 6 - Build the operator/customer UI

Goal:

- allow real users to configure deployments without direct ops work

Deliverables:

- deployments page
- actions editor
- knowledge editor
- provider profile editor
- revisions/diff/rollback UI
- verification UI

Done when:

- a non-developer operator can change config and safely apply a new version

### Phase 7 - Integrate Shopify as the first vertical consumer

Goal:

- prove the platform through a real industry integration

Deliverables:

- Shopify app backend calls the platform deployment API
- shop-to-deployment binding
- Admin UI points to verification and setup state
- storefront/backend flows use the assigned deployment

Done when:

- a Shopify merchant can obtain a deployment through the platform instead of through manual setup

---

## 17) Recommended V1 Sequence for a Startup

If the goal is to build a consultancy company with a productized delivery engine, the most practical order is:

1. control plane backend
2. config compiler
3. runtime/connector deployment alignment
4. Railway provisioning worker
5. verification and rollback
6. UI for operators
7. Shopify as first market-specific consumer

This sequence gives the highest leverage:

- first create repeatable delivery
- then create customer-friendly editing
- then attach vertical GTM

---

## 18) Risks and Mitigations

### 18.1 Risk: overbuilding hot config before release discipline

Mitigation:

- keep V1 redeploy-based
- add hot reload only later and only for safe classes like actions

### 18.2 Risk: raw YAML becomes the product

Mitigation:

- make YAML an advanced export/import format only
- keep structured DB config canonical

### 18.3 Risk: customer changes break runtime behavior silently

Mitigation:

- validate before publish
- verify after deploy
- support rollback
- expose config-version introspection

### 18.4 Risk: provider/entity changes create hidden migration issues

Mitigation:

- classify changes
- mark some changes `reindexRequired`
- require explicit operator confirmation for schema-like changes

---

## 19) MVP Acceptance Criteria

The V1 platform is successful when:

- a new deployment can be created from the platform without manual file editing
- actions can be configured through UI/API and applied through a versioned release
- entities/vector spaces can be configured through UI/API and applied through a versioned release
- provider profiles can be selected through UI/API and applied through a versioned release
- runtime and connector stay the same executable across customers
- each release is diffable, verifiable, and rollbackable
- Railway provisioning is repeatable for customer environments
- Shopify or another vertical consumer can request a deployment from the platform instead of hardcoding environment setup

---

## 20) Final Recommendation

Build this as a **versioned control-plane product** with **immutable deployments** and **redeploy-on-apply**.

That gives you:

- a clean consultancy delivery engine
- a serious productization foundation
- enough flexibility for customer domains
- low-regret evolution toward hot-reload or deeper automation later

The platform should sell:

- configurable AI enablement
- repeatable deployment
- repeatable integration
- repeatable governance

not:

- one-off custom runtime forks
- manual environment assembly
- hidden config drift
