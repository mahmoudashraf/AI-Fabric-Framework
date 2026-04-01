# Platform V2 Features Guide

This guide consolidates the current Platform V2 feature set for the platform-first branch.

It describes what is already implemented across Wave 1, Wave 2, and Wave 3, with special focus on:

- deployment-centric operations
- authentication and authorization
- prompt and POC flows
- release safety and verification
- Railway-managed deployment control

It is meant to replace scattered mental models across multiple execution documents.

Related detailed guides:

- `RAILWAY_DEPLOYMENT_OPERATIONS_VIA_PLATFORM_GUIDE.md`
- `PLATFORM_PROVIDER_AND_VECTOR_DEPLOYMENT_GUIDE.md`
- `MANAGED_VECTOR_DATABASE_ADMINISTRATION_GUIDE.md`
- `PROMPT_MANAGEMENT_CURATED_MODULES_GUIDE.md`
- `RUNTIME_AUTHORIZATION_AND_ACCESS_CONTROL_GUIDE.md`
- `VERIFICATION_PLAYBOOK.md`

---

## 1. What Platform V2 Means

Platform V2 is the current enterprise-oriented control plane for AI deployment management.

The core shift from the older platform shape is:

- the UI is deployment-first, not page-first
- deployment state is treated as draft, published, release, and live
- role-aware operations are enforced in backend services, not only hinted in the UI
- prompt management, verification, diagnostics, and remediation are deployment-scoped
- Railway deployment outputs are generated from platform-managed configuration, artifacts, and secret references

Platform V2 is not only a UI refresh.

It is a control-plane model with:

- explicit deployment workspaces
- platform users and deployment assignments
- guarded apply and remediation flows
- configuration governance
- source-of-truth visibility
- live verification against runtime and REST connector services

---

## 2. V2 Feature Scope By Wave

### Wave 1: Deployment control-plane foundation

Wave 1 established the operational shell:

- unified deployment workspace context
- shared deployment header across pages
- deployment overview landing page
- deployment assignments and visibility controls
- platform user administration
- activity timeline and audit trail
- destructive operations with guardrails
- approval workflow foundation
- saved operator view persistence

The important result is that operators now work inside one selected deployment instead of manually re-selecting context per page.

### Wave 2: Fast operator iteration and proof-of-concept flow

Wave 2 shortened the path between config changes and customer validation:

- prompt workspace with managed prompt fields
- prompt diff and release preview
- prompt state clarity between editor, draft, published, and POC overlay
- deployment-scoped POC chat
- dataset import and guided migration intake
- import guardrails and run history
- scenario library and validation loops
- assistant staging and readiness summaries

The important result is that prompt and proof-of-concept work became deployment-scoped and auditable instead of ad hoc.

### Wave 3: Production operations and governed rollout

Wave 3 hardened the platform for live operations:

- apply versus draft versus live clarity
- release impact preview
- configuration diff center
- verification gate and grouped post-apply evidence
- service-by-service configuration model
- secrets and config separation
- auth, upstream, and CORS governance
- source-of-truth lineage and drift visibility
- diagnostics workspace
- provider and service navigation
- governed remediation actions
- production readiness scorecard

The important result is that the platform now behaves like a deployment operations console, not only a demo builder.

---

## 3. The Core Deployment Model

Platform V2 uses four distinct states. These should not be confused.

- `Editor state`: browser-local unsaved changes
- `Saved draft`: the latest deployment draft stored by the platform
- `Published version`: an immutable version compiled from the draft
- `Live / active version`: the version currently applied to the managed deployment

This distinction matters because:

- operators can edit safely without affecting live traffic
- publish freezes a concrete version
- apply drives provider rollout
- verification is tied to releases and active versions

Platform V2 intentionally separates these states in the workspace so operators can see:

- what they changed locally
- what is saved
- what is published
- what is live

---

## 4. Main V2 Workspaces

### Deployments

This is the deployment selection and fleet view.

Key capabilities:

- persistent selected deployment
- deployment filtering and operator-specific views
- role-aware quick actions
- assignment and health visibility

### Overview

This is the operational home page for one deployment.

Key capabilities:

- draft versus live state summary
- service-by-service configuration model
- source-of-truth summary
- generated service URLs
- provider navigation links
- readiness scorecard

### Revisions / Versions

This is the release safety page.

Key capabilities:

- draft versus published compare
- release impact preview
- config diff center
- selected version review before apply

### Security

This is the deployment security posture page.

Key capabilities:

- missing secret detection
- literal credential risk detection
- runtime and connector admin exposure checks
- connector ingress auth posture checks
- remote authz posture checks
- CORS guidance

### Verification

This is the release-confidence page.

Key capabilities:

- pre-apply readiness
- post-apply grouped checks
- service-specific verification evidence
- release-linked verification history
- admin-only platform-hosted read-only verification reruns
- manual GitHub Actions workflow_dispatch for CI/CD reruns

### Diagnostics

This is the failure and recovery page.

Key capabilities:

- release timeline
- failed-step summary
- extracted recovery hints
- provider drift visibility
- governed remediation actions

### Activity, Approvals, Access, Users

These pages complete the enterprise operating model.

Key capabilities:

- audit and activity visibility
- approval request and approval action flow
- deployment assignment model
- platform user administration

### Prompts and POC

These pages support controlled iteration.

Key capabilities:

- deployment prompt bundle editing
- prompt baseline compare and release preview
- session-scoped POC prompt override
- embedded deployment POC chat
- dataset import and migration intake
- scenario-driven validation loops

---

## 5. Authentication And Authorization Model

This is the most important V2 security concept.

Platform V2 has multiple auth layers. They must not be mixed together.

### 5.1 Platform authentication

Platform backend authentication is controlled by `platform.auth.*` in [application.yml](../Platfrom/backend/src/main/resources/application.yml).

Supported platform auth methods:

- session auth
- platform API key auth
- public API client auth for `/api/public/*`

When `platform.auth.enabled=true`, almost all platform APIs require authentication. The only public platform endpoints are:

- `/actuator/health`
- `/api/platform/auth/session`
- `/api/platform/auth/login`
- `/api/platform/auth/logout`
- signed deployment artifact URLs for:
  - `ai-actions.yml`
  - `ai-entity-config.yml`
  - `actions-routing.yml`
  - `ai-prompt-config.json`
  - `deployment-manifest.json`

This is enforced in [PlatformSecurityConfiguration.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/PlatformSecurityConfiguration.java).

### 5.2 Session authentication

Session auth is the main browser login path.

It works through:

- `POST /api/platform/auth/login`
- `GET /api/platform/auth/session`
- `POST /api/platform/auth/logout`

The platform issues an HTTP-only session cookie. Cookie behavior is controlled by:

- `PLATFORM_AUTH_SESSION_COOKIE_NAME`
- `PLATFORM_AUTH_SESSION_COOKIE_SECURE`
- `PLATFORM_AUTH_SESSION_COOKIE_SAME_SITE`
- `PLATFORM_AUTH_SESSION_TTL`

Implementation is in [PlatformAuthController.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/web/PlatformAuthController.java).

### 5.3 Platform API key authentication

Platform API key auth is intended for automation and headless operations.

Header:

- default header name: `X-PLATFORM-API-KEY`

Possible keys:

- `PLATFORM_OPERATOR_API_KEY`
- `PLATFORM_ADMIN_API_KEY`

This mode is useful for scripts and automated control-plane clients.

### 5.4 Public API client authentication

This is a separate auth path for `/api/public/*`.

Headers:

- client id header: `X-PLATFORM-CLIENT-ID`
- API key header: `X-PLATFORM-PUBLIC-API-KEY`

This flow produces a `PUBLIC_API_CLIENT` principal, not an operator principal.

It is enforced by [PlatformPublicApiAuthenticationFilter.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/PlatformPublicApiAuthenticationFilter.java).

### 5.5 Platform roles

Platform-level roles are:

- `PLATFORM_ADMIN`
- `PLATFORM_OPERATOR`
- `PUBLIC_API_CLIENT`

They are defined in [PlatformRole.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/PlatformRole.java).

High-level behavior:

- `PLATFORM_ADMIN`
  - full platform and deployment administration
  - user management
  - secret mutation
  - approval action
- `PLATFORM_OPERATOR`
  - deployment operations within authorized scope
  - can view secrets metadata, but cannot mutate secrets
  - cannot manage platform users
- `PUBLIC_API_CLIENT`
  - restricted to public API flows
  - cannot use privileged deployment operations

### 5.6 Deployment-level authorization

Platform role alone is not the full story.

For human users, deployment access is also limited by deployment assignment.

Assignment roles:

- `DEPLOYMENT_VIEWER`
- `DEPLOYMENT_OPERATOR`
- `DEPLOYMENT_EDITOR`
- `DEPLOYMENT_ADMIN`

These are enforced in [DeploymentAccessService.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentAccessService.java).

Practical meaning:

- `DEPLOYMENT_VIEWER`
  - view deployment information
- `DEPLOYMENT_OPERATOR`
  - operational actions
- `DEPLOYMENT_EDITOR`
  - draft editing
- `DEPLOYMENT_ADMIN`
  - highest deployment-scoped authority

Platform API-key principals with platform operator/admin role are treated as global platform access in the current model.

### 5.7 Secrets authorization

Secrets are intentionally separated from normal config editing.

Current secret controller behavior:

- list secret summaries: admin and operator
- update or clear secret values: admin only

This is enforced in [PlatformSecretController.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/secret/web/PlatformSecretController.java).

### 5.8 User management authorization

Platform user management is admin-only.

This is enforced in [PlatformUserAdminController.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/security/web/PlatformUserAdminController.java).

### 5.9 Approval model

Some deployment operations can require explicit approval before execution.

The pattern is:

- operator requests approval
- admin approves
- guarded action can proceed with the approved request id

This is part of the governed operations model, not a separate authentication system.

---

## 6. Runtime And REST Connector Security Model

Platform V2 also governs downstream service security posture. That is separate from platform login.

### 6.1 Runtime admin authentication

Runtime admin APIs are protected by:

- `APP_ADMIN_API_KEY`
- `APP_ADMIN_API_KEY_HEADER`

Default header:

- `X-ADMIN-API-KEY`

If `APP_ADMIN_API_KEY` is empty, runtime admin endpoints are effectively open. That is only acceptable for dev/test, not production.

Implementation is in [AdminAuth.java](../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/admin/AdminAuth.java).

### 6.2 REST connector inbound authentication

REST connector business ingress is protected separately from admin ingress.

Connector inbound API key settings live in routing config:

- `CONNECTOR_ALLOW_UNAUTHENTICATED`
- `CONNECTOR_API_KEY_ENABLED`
- `CONNECTOR_API_KEY_HEADER`
- `CONNECTOR_API_KEY`

Default connector header:

- `X-AIFABRIC-API-KEY`

These defaults are visible in [actions-routing.yml](../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/resources/actions-routing.yml).

### 6.3 REST connector admin authentication

REST connector `/api/admin/*` uses the admin key path when configured:

- `APP_ADMIN_API_KEY`
- `APP_ADMIN_API_KEY_HEADER`

So the current security model intentionally separates:

- connector business ingress: `X-AIFABRIC-API-KEY`
- runtime/connector admin ingress: `X-ADMIN-API-KEY`

This is enforced in [ApiKeyAuthFilter.java](../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/security/ApiKeyAuthFilter.java).

### 6.4 Runtime to connector authentication

When runtime calls the connector, it uses:

- `ACTIONS_CONNECTOR_BASE_URL`
- `ACTIONS_CONNECTOR_API_KEY`

The platform provisions these values into runtime when connector auth is enabled.

### 6.5 Runtime authorization to customer data

Runtime entity-level authorization is currently driven by remote HTTP authz or deny-all mode.

Current supported deployment modes:

- `REMOTE_HTTP`
- `DENY_ALL`

Current runtime env mapping:

- `AI_FABRIC_RUNTIME_AUTHZ_MODE`
- `AUTHZ_BASE_URL`
- `AUTHZ_PATH`
- `AUTHZ_API_KEY_HEADER`
- `AUTHZ_API_KEY`

Runtime defaults to using the connector base URL as authz base URL when appropriate.

Implementation starts in [application.yml](../ai-infrastructure-module/ai-fabric-runtime/src/main/resources/application.yml) and [RemoteHttpEntityAccessPolicy.java](../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/authz/RemoteHttpEntityAccessPolicy.java).

### 6.6 REST connector authz proxy

The connector can proxy runtime authz requests to a customer authz service via:

- `POST /api/authz/check`

Its upstream authz behavior is configured under the `authz:` section in routing config.

Implementation is in [RestAuthzProxyService.java](../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/RestAuthzProxyService.java).

---

## 7. What The Platform Actually Drives Into Railway

This is where V2 becomes operational.

Platform V2 does not only store deployment drafts. It compiles and provisions specific outputs.

### 7.1 Artifacts

For each published version, the platform exposes signed artifact URLs for:

- actions
- entities
- routing
- prompts
- deployment manifest

### 7.2 Runtime env generated by platform

Current important runtime env values generated by the platform include:

- `AI_ACTIONS_CATALOG_PATH`
- `AI_CONFIG_DEFAULT_FILE`
- `AI_PROMPTS_DEPLOYMENT_CONFIG_FILE`
- `ACTIONS_CONNECTOR_BASE_URL`
- `AI_CURATED_PACK`
- `AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED`
- `AI_FABRIC_RUNTIME_AUTHZ_MODE`
- `APP_ADMIN_API_KEY`
- `APP_ADMIN_API_KEY_HEADER`
- `AUTHZ_BASE_URL`
- CORS env values

### 7.3 REST connector env generated by platform

Current important connector env values generated by the platform include:

- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION`
- `REST_CONNECTOR_RUNTIME_PROXY_BASE_URL`
- `REST_CONNECTOR_RUNTIME_PROXY_ENABLED`
- `REST_CONNECTOR_RUNTIME_PROXY_API_KEY`
- `REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER`
- `CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`
- `APP_ADMIN_API_KEY_HEADER`
- CORS env values

This generation logic is in [RailwayProvisioningPlanService.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayProvisioningPlanService.java).

### 7.4 Security profile normalization

The platform uses normalized deployment profiles rather than blindly passing raw user strings into Railway.

Examples:

- connector API key header is standardized to `X-AIFABRIC-API-KEY`
- admin header is standardized to `X-ADMIN-API-KEY`
- authz mode is normalized to `REMOTE_HTTP` or `DENY_ALL`

This normalization is defined in [ManagedDeploymentProfileCatalog.java](../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/ManagedDeploymentProfileCatalog.java).

---

## 8. Prompt Management In V2

Prompt management in V2 is deployment-owned and version-aware.

Current implemented behavior:

- curated module selection seeds the managed prompt bundle
- operators edit the deployment prompt bundle in the platform
- prompt config is stored in platform draft and published version records
- publish exposes `ai-prompt-config.json`
- apply wires prompt config into runtime through `AI_PROMPTS_DEPLOYMENT_CONFIG_FILE`
- runtime reports the loaded prompt config location in admin overview
- POC prompt preview can override deployed prompt values per key for a single request or session

The currently managed prompt fields are:

- `systemPrompt`
- `intentExtractionPrompt`
- `actionSelectionPrompt`
- `clarificationPrompt`
- `answerGenerationPrompt`
- `retrievalPrompt`
- `assistantUiPrompt`

V2 supports both:

- deployment-owned live prompt config
- session-scoped POC override for controlled testing

---

## 9. Verification, Diagnostics, And Remediation In V2

Platform V2 adds a real release verification model.

### Verification

Current verification covers:

- active version linkage
- runtime and connector health
- runtime and connector admin overview checks
- runtime and connector config expectations
- actions and entity expectations
- prompt artifact reachability
- runtime prompt config alignment
- Railway preflight evidence
- platform-hosted read-only reruns of `verify-vector-deployment.sh` and `verify-ecommerce-deployment.sh`
- manual GitHub Actions `workflow_dispatch` that fetches the same verification context from the platform for CI/CD use

Detailed setup for the platform-hosted admin path is in:

- `Final_Documentation/Development_Guides/PLATFORM_HOSTED_DEPLOYMENT_VERIFICATION_GUIDE.md`

Detailed setup for the manual GitHub Actions path is in:

- `Final_Documentation/Development_Guides/GITHUB_ACTIONS_DEPLOYMENT_VERIFICATION_GUIDE.md`

### Diagnostics

Diagnostics correlate:

- release state
- failed steps
- provider drift
- known recovery hints
- direct operational next steps

### Remediation

Current governed remediation actions include:

- rerun verification
- redeploy active version
- restart runtime service
- restart REST connector service
- reset runtime vectors
- archive deployment
- restore deployment
- delete deployment

These actions are role-aware and can be blocked by:

- release in progress
- missing active version
- missing admin secret
- provider drift
- deployment assignment role
- required approval flow

---

## 10. How To Operate A Deployment In V2

Recommended operator flow:

1. Select the deployment in the Deployments workspace.
2. Use Overview to confirm current state, source-of-truth, generated endpoints, and readiness posture.
3. Use Security to fix secrets, auth, upstream, and CORS issues before release.
4. Use Revisions to review diff and release impact before apply.
5. Use Verification after apply to confirm runtime and connector behavior.
6. Use Diagnostics if release or live health is unhealthy.
7. Use Remediation only through the governed actions, not by manually mutating provider state unless necessary.

For prompt and proof-of-concept work:

1. Edit prompts in the Prompts workspace.
2. Review baseline versus draft and release preview.
3. Use POC chat and session-scoped prompt overrides for safe validation.
4. Publish and apply when the prompt bundle is ready to become live behavior.

---

## 11. What V2 Still Does Not Try To Be

V2 is intentionally strong on deployment administration and controlled iteration.

It does not yet try to be:

- a full multi-cloud abstraction layer beyond the current Railway-first provisioning path
- a runtime-orchestration research surface
- a general-purpose infrastructure console
- a replacement for customer identity systems
- a replacement for the customer authz service itself

Its purpose is narrower and more valuable:

- manage AI deployment configuration
- safely publish and apply it
- verify and diagnose the resulting services
- let operators tune prompts and POC flows inside one governed control plane

---

## 12. Recommended Guide Map

Use this guide as the entry point.

Then use the more specific guides when needed:

- Railway operations: `RAILWAY_DEPLOYMENT_OPERATIONS_VIA_PLATFORM_GUIDE.md`
- provider and vector deployment behavior: `PLATFORM_PROVIDER_AND_VECTOR_DEPLOYMENT_GUIDE.md`
- managed vector database administration: `MANAGED_VECTOR_DATABASE_ADMINISTRATION_GUIDE.md`
- prompt behavior and deployment: `PROMPT_MANAGEMENT_CURATED_MODULES_GUIDE.md`
- runtime authz behavior: `RUNTIME_AUTHORIZATION_AND_ACCESS_CONTROL_GUIDE.md`
- live verification steps: `VERIFICATION_PLAYBOOK.md`

If a feature appears in the UI but not in this guide, treat that as a documentation gap and update this file. This guide should remain the consolidated V2 reference.
