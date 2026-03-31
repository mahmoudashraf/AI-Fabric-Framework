# Platform Phase 18+ Execution Plan

Status: Phase 18, Phase 19, Phase 20, and Phase 21 implemented and verified locally from API + UI flow (2026-03-29)

This document is the next execution plan for the `Platfrom/` control plane after completion of:

- `changes/Productization/PLATFORM_PHASE_12_PLUS_EXECUTION_PLAN.md`

It focuses on the next set of productization gaps between:

- an internal/operator-ready control plane

and:

- a customer-facing AI enablement platform that can safely expose deployment creation, lifecycle operations, and first vertical consumers such as Shopify.

Related docs:

- `changes/Productization/CONFIGURABLE_AI_ENABLEMENT_PLATFORM_PLAN.md`
- `changes/Productization/PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md`
- `changes/Productization/SHOPIFY_APP_IMPLEMENTATION_PLAN.md`
- `changes/Productization/SHOPIFY_ADMIN_APP_UI_PLAN.md`
- `changes/Productization/SHOPIFY_CHAT_WIDGET_V1_PLAN.md`
- `changes/Productization/REMOTE_ACCESS_CONTROL_VIA_REST_CONNECTOR_PLAN.md`
- `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`

---

## 1) Current Baseline

The platform now already has:

- deployment creation
- draft / version / release lifecycle
- structured draft editors
- config compilation into versioned artifacts
- Railway API provisioning
- Railway preflight
- secret management for deployment provisioning
- async apply execution with release progress tracking
- deep runtime + connector verification
- neutral Docker packaging for runtime and REST connector
- Postgres + Flyway-managed persistence
- platform API-key auth
- operator/admin role separation
- audit trail for privileged actions
- signed artifact delivery for runtime/connector config bundles
- UI smoke verification for signed artifact delivery
- platform-managed user/session identity
- browser session login/logout flow

The current proven loop is:

1. create deployment
2. edit draft
3. publish version
4. apply version
5. provision/update Railway resources
6. wait for deployment success
7. run verification
8. inspect diagnostics and release evidence

Current practical limitations:

- deployment artifact URLs are intentionally public so runtime and connector can fetch config bundles
- platform operator auth is still API-key based, not identity-provider based
- the platform is still operator-centric rather than customer-self-serve
- the public provisioning API still uses statically configured machine clients rather than self-service client registration
- Shopify remains planned, not integrated

---

## 2) Main Goal Of This Follow-On Plan

Move the platform from:

- operator-ready internal control plane

to:

- secure customer-facing control plane with a stable provisioning API and first vertical consumer integration

The target product loop becomes:

1. operator or customer creates deployment from platform UI/API
2. platform secures artifact delivery to runtime and connector
3. platform authenticates operators through real identity
4. customer can observe deployment lifecycle without internal-only tooling
5. external product consumers can request deployments programmatically
6. Shopify can become the first real distribution wrapper around the platform

---

## 3) Recommended Phase Ordering

Recommended next order:

1. Phase 18: Artifact Delivery Hardening
2. Phase 19: Platform Identity, SSO, And User Model
3. Phase 20: Customer-Facing Deployment Lifecycle UX
4. Phase 21: Public Provisioning API For Vertical Consumers
5. Phase 22: Shopify As First Vertical Consumer

This order is intentional:

- first close the biggest remaining security gap
- then replace internal API-key operator access with identity-backed access
- then expose lifecycle workflows to real customers
- then stabilize the programmatic API that future verticals use
- then build Shopify on top of the stable platform contracts

---

## 4) Phase 18: Artifact Delivery Hardening

### 4.1 Objective

Protect deployment config artifact delivery without breaking runtime and connector startup.

### 4.2 Why this is next

The platform currently leaves artifact endpoints readable without operator auth because deployed runtime and connector instances fetch:

- `ai-actions.yml`
- `ai-entity-config.yml`
- `actions-routing.yml`
- `deployment-manifest.json`

This is acceptable as an implementation bridge, but it should not remain the long-term product model once the platform becomes customer-facing.

### 4.3 Recommended design

Do **not** reuse operator auth for deployment artifact delivery.

Use a separate machine-to-machine access model:

- preferred V1.1: signed artifact URLs with expiry
- acceptable V1.1 alternative: per-deployment artifact access token appended in provisioning URLs

Recommended direction:

- generate per-version signed URLs for artifact access
- embed those signed URLs into the Railway deployment env vars
- validate signature, deployment id, version id, artifact name, and expiry on access
- keep operator auth completely separate from artifact delivery

### 4.4 Scope

- add signed artifact URL generation in platform backend
- add artifact signature validation filter/controller support
- update provisioning plan to inject signed artifact URLs instead of plain public URLs
- keep a bounded expiry model that survives deployment startup and restarts
- update verification to assert signed artifact URLs are used in active deployment config

### 4.5 Implementation tasks

- define artifact token/signature model:
  - `deploymentId`
  - `versionId`
  - `artifactName`
  - `expiresAt`
  - signature
- add platform secret for artifact signing key
- add artifact URL builder that emits signed URLs
- update deployment manifest and Railway plan generation
- add validation/audit for rejected artifact access attempts
- decide how redeploys refresh expiring URLs:
  - long enough expiry for dev
  - rotation on apply for managed deployments

### 4.6 Acceptance criteria

- plain unsigned artifact URLs are no longer the default delivery path
- runtime and connector boot successfully from signed artifact URLs
- verification passes against a signed-artifact deployment
- rejected or expired artifact access is observable in logs/audit

### 4.7 Implementation status

Completed in the current branch.

Delivered:

- per-version signed artifact URLs with expiry and HMAC validation
- platform signing secret support through platform secret management
- Railway plan generation updated to emit signed artifact URLs into runtime and REST connector env
- runtime/connector artifact access remains separate from operator auth
- audit trail for rejected artifact access
- UI visibility in Revisions and Diagnostics for signed artifact strategy and artifact URLs
- browser smoke script:
  - `Platfrom/ui/scripts/phase18-ui-smoke.mjs`

Local verification completed:

- backend test suite passed
- signed URLs verified in Railway provisioning plan output
- UI flow verified against local backend + Vite UI:
  - Revisions page shows signed artifact bundle URLs
  - Diagnostics page shows `SIGNED_REMOTE_CONFIG_BUNDLES`
  - artifact URLs shown in UI include `expires=` and `sig=`

### 4.8 Out of scope

- general CDN layer
- customer BYO object storage
- end-user document download access control

---

## 5) Phase 19: Platform Identity, SSO, And User Model

### 5.1 Objective

Move platform operator access from static API keys to real user identity.

### 5.2 Why this matters

API-key auth is acceptable for internal/operator bootstrap, but not the right long-term operator model for:

- consultants
- internal teams
- customer admins
- support staff

You need user identity, session handling, and auditable actor attribution.

### 5.3 Recommended design

Preferred direction:

- OIDC-compatible login
- platform-managed user/session model
- role mapping at platform layer

Recommended initial role set:

- `platform-admin`
- `platform-operator`
- `customer-admin`
- `customer-operator`
- optional `support-readonly`

### 5.4 Scope

- add identity provider integration layer
- add user/session model
- add org/project/deployment ownership mapping
- preserve API-key mode only as explicit local/dev fallback
- keep service-to-service provisioning auth separate from user auth

### 5.5 Implementation tasks

- choose auth implementation path:
  - external IdP via OIDC
  - or managed auth provider for faster startup execution
- add backend session/JWT validation
- add frontend login/logout/session refresh flow
- map identity claims to platform roles
- extend audit trail to store real user identifiers
- restrict deployment list/detail visibility by org/project ownership

### 5.6 Acceptance criteria

- platform login no longer depends on manual API-key entry in normal hosted usage
- role-based access still works for:
  - deployment operations
  - secret mutation
  - diagnostics
  - audit review
- audit events show real user identity, not only static API-key actor ids

### 5.7 Implementation status

Implemented in the current branch as a platform-managed identity/session layer.

Delivered:

- persistent `platform_users` and `platform_user_sessions` tables via Flyway
- bootstrap admin user support for local/dev and controlled hosted bootstrap
- password-based login endpoint with cookie session issuance
- session cookie authentication filter on backend requests
- browser login/logout/session refresh flow in the platform UI
- API-key auth retained as optional fallback instead of the primary browser path
- audit coverage for bootstrap admin creation, successful login, and logout
- browser smoke script:
  - `Platfrom/ui/scripts/phase19-ui-auth-smoke.mjs`

Local verification completed:

- backend test suite passed with session-auth integration coverage
- UI build passed
- UI flow verified against local auth-enabled backend + Vite UI:
  - sign-in with email/password
  - authenticated operator shell rendering
  - sign-out back to login screen
- Phase 18 signed-artifact smoke still passed after the identity changes

Known follow-up still open within the broader identity theme:

- external OIDC/SSO provider integration
- org/project ownership mapping
- customer-admin / customer-operator role expansion

### 5.8 Out of scope

- advanced SCIM / enterprise provisioning
- fine-grained per-field ACLs
- customer end-user auth inside runtime conversations

---

## 6) Phase 20: Customer-Facing Deployment Lifecycle UX

### 6.1 Objective

Turn the current operator console into a customer-usable deployment product.

### 6.2 Why this matters

The platform can already provision deployments, but the current UX is still optimized for implementation and verification rather than customer operation.

Customers need:

- cleaner deployment creation flow
- clearer deployment states
- safer rollback/delete actions
- less “internal implementation detail” in the default UI

### 6.3 Scope

- customer-oriented deployment creation flow
- deployment overview screens
- release history / rollback workflow
- environment lifecycle actions
- safer destructive-operation UX
- clearer verification summaries

### 6.4 Implementation tasks

- add deployment wizard:
  - template selection
  - provider profile selection
  - environment naming
  - default config bootstrap
- add deployment summary page:
  - runtime URL
  - connector URL
  - current version
  - current release status
  - verification summary
- add rollback and redeploy workflows
- add delete/archive deployment workflow
- add customer-safe diagnostics mode:
  - hide low-level internals by default
  - keep advanced/operator diagnostics available behind role gate

### 6.5 Acceptance criteria

- a non-technical operator can create a deployment from the UI
- a customer operator can understand whether deployment is healthy
- rollback/reapply is visible and safe
- destructive operations require explicit confirmation and are audited

### 6.6 Out of scope

- billing
- multi-environment promotion pipelines
- complex incident-management workflows

### 6.7 Implementation status

Completed in the current branch.

Delivered:

- customer-oriented deployments overview cards with lifecycle health summaries
- default deployment creation flow focused on template choice plus environment naming
- explicit navigation from deployment overview into:
  - Revisions
  - Diagnostics
- archive deployment workflow with:
  - explicit name confirmation
  - audit event recording
  - active-list removal
  - archived-list visibility
- archived deployments excluded from default active deployment selectors
- Revisions and Diagnostics now prefer a deployment with meaningful lifecycle data when no deployment is explicitly requested
- browser smoke script:
  - `Platfrom/ui/scripts/phase20-ui-lifecycle-smoke.mjs`

Local verification completed:

- backend test suite passed
- UI production build passed
- browser smokes passed against the live local stack:
  - Phase 18 signed artifact flow
  - Phase 19 session login/logout flow
  - Phase 20 deployment lifecycle flow:
    - create deployment
    - navigate to revisions
    - archive with explicit confirmation
    - verify active-list removal
    - verify archived-list visibility

---

## 7) Phase 21: Public Provisioning API For Vertical Consumers

### 7.1 Objective

Expose the platform as a stable backend API that external product consumers can use to request and manage deployments.

### 7.2 Why this matters

Shopify, future admin apps, and any domain-specific onboarding flow should not provision Railway resources directly.

They should call your platform.

This is the boundary that turns the control plane into a reusable product rather than a one-off internal console.

### 7.3 Scope

- define stable external deployment API
- separate internal operator endpoints from external consumer endpoints
- add machine-client auth for external consumers
- add idempotent create/apply flows
- document request/response contracts

### 7.4 Recommended API shape

Examples:

- `POST /api/public/deployments`
- `GET /api/public/deployments/{id}`
- `POST /api/public/deployments/{id}/apply`
- `GET /api/public/deployments/{id}/status`
- `GET /api/public/deployments/{id}/credentials`

Recommended payload model:

- deployment template
- provider profile id
- environment name
- optional curated pack / domain preset
- optional callback metadata for consumer system

### 7.5 Implementation tasks

- define separate auth model for public API consumers
- make deployment creation idempotent
- decide what the public API can and cannot mutate
- add async operation ids if long-running applies are exposed directly
- add documentation and example integration flow

### 7.6 Acceptance criteria

- an external service can create and inspect a deployment without using the platform UI
- public API responses are stable and documented
- apply lifecycle is safe for retries
- audit trail differentiates:
  - platform user action
  - external machine client action

### 7.7 Out of scope

- exposing every internal draft editor directly
- arbitrary Railway passthrough APIs
- full customer self-hosting support

### 7.8 Implementation status

Completed in the current branch.

Delivered:

- machine-client auth for the public provisioning surface using:
  - client id header
  - public API key header
- dedicated public API role:
  - `PUBLIC_API_CLIENT`
- idempotent public deployment creation keyed by:
  - client id
  - `externalDeploymentKey`
- automatic initial publish to `v1` on first public deployment creation
- public deployment inspection endpoints:
  - summary
  - status
  - credentials
- public apply endpoint with idempotent replay semantics for the same target version
- audit events that distinguish public machine clients from operator users
- persistence for public deployment bindings via:
  - `platform_public_api_deployments`
- integration coverage for:
  - create
  - replayed create
  - apply
  - replayed apply
  - status
  - credentials
  - conflict on mismatched external key reuse
- contract doc:
  - `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`

Local verification completed:

- backend test suite passed with the new public provisioning integration tests
- existing platform UI browser smokes continued to pass after the backend/auth changes
- local public API smoke verified:
  - public create returns `201`
  - idempotent create replay returns `200`
  - public apply returns `201`
  - idempotent apply replay returns `200`
  - public status and credentials endpoints return the expected deployment context

---

## 8) Phase 22: Shopify As First Vertical Consumer

### 8.1 Objective

Use Shopify as the first real distribution wrapper around the platform.

### 8.2 Why this is the right first vertical

Shopify gives:

- a clear install/onboarding event
- a strong domain model
- a real customer journey
- a concrete retrieval/indexing story
- a practical path to recurring revenue

It also cleanly fits the architecture already defined in the existing Shopify docs.

### 8.3 Scope

The Shopify app backend should:

- authenticate merchants through Shopify OAuth
- call the platform public API to create/bind a deployment
- store `shop -> deployment` mapping
- sync Shopify data into the deployed runtime/indexing flow
- use the deployment for playground and storefront chat flows

The Shopify app should **not** own provisioning logic directly.

### 8.4 Implementation tasks

- build Shopify backend integration to platform public API
- create merchant-install flow:
  - install app
  - request deployment
  - wait for provisioning
  - bind shop to deployment
- reuse platform verification/state in Shopify admin UX
- connect Shopify sync jobs to the deployment runtime
- connect storefront/admin chat traffic through the bound deployment

### 8.5 Acceptance criteria

- installing the Shopify app can result in a provisioned deployment without manual operator intervention
- the Shopify admin app can show deployment status from the platform
- Shopify sync targets the correct deployment
- storefront/admin chat uses the deployment created by the platform

### 8.6 Out of scope

- full Shopify billing integration
- advanced merchant custom action marketplace
- multiple deployments per shop in V1

---

## 9) Suggested MVP Definition Of Done For This Follow-On Plan

This follow-on plan should be considered complete only when all of the following are true:

- deployment config artifacts are no longer exposed through plain unsigned public URLs by default
- hosted platform access uses identity-backed auth rather than static API-key entry
- customers can create and operate deployments from a cleaner deployment lifecycle UX
- an external product consumer can request deployments through a stable platform API
- Shopify can consume that API and bind a shop to a deployment

If any of those are missing, the platform is still operator-ready rather than customer-ready.

---

## 10) Recommended Immediate Next Step

Start **Phase 18** now.

Concrete first milestone:

1. choose signed artifact URLs vs deployment artifact token
2. implement artifact signing key management
3. update Railway plan generation to emit secured artifact URLs
4. prove runtime + connector still boot from secured artifact delivery

That is the highest-leverage next step because it closes the biggest remaining security gap without changing the proven deployment architecture.

---

## 11) What Not To Do Next

Avoid spending the next phase on:

- more UI polish before artifact delivery is hardened
- Shopify implementation before the public platform API exists
- advanced billing before customer deployment lifecycle exists
- hot-reload config work
- generic multi-cloud deployment support
- broad tenant abstractions before customer-facing access patterns are clear

The main bottleneck now is not more configuration power.

The bottleneck is converting the proven operator platform into a secure customer-facing product boundary.
