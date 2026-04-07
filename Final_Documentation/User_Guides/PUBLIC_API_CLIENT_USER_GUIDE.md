# Public API Client User Guide

Status: current branch guide (2026-03-29)

This guide is for the **Public API Client** user type.

A Public API Client is not a human user in the platform UI. It is a machine client such as:

- a future Shopify app backend
- a vertical onboarding service
- an internal backend that provisions deployments programmatically

This user type interacts with the platform through the **public provisioning API**.

Companion docs:

- `Final_Documentation/User_Guides/PLATFORM_USER_TYPES_GUIDE.md`
- `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`

---

## 1) What A Public API Client Does

Public API Client responsibilities:

- create deployments programmatically
- inspect deployment state
- request apply/re-apply
- fetch runtime and connector base URLs

Public API Client should **not**:

- edit drafts directly
- mutate platform secrets
- call Railway directly for provisioning
- use internal operator endpoints

The platform is the provisioning boundary.

---

## 2) Authentication Model

The public provisioning API uses a machine-client API key model.

Default request headers:

- `X-PLATFORM-CLIENT-ID`
- `X-PLATFORM-PUBLIC-API-KEY`

These header names are configurable through:

- `PLATFORM_PUBLIC_API_CLIENT_ID_HEADER`
- `PLATFORM_PUBLIC_API_KEY_HEADER`

The API itself is enabled through:

- `PLATFORM_PUBLIC_API_ENABLED=true`

In the current branch, public clients are configured centrally by the platform operator/admin team.

---

## 3) Public API Endpoints

Supported routes:

- `POST /api/public/deployments`
- `GET /api/public/deployments/{deploymentId}`
- `GET /api/public/deployments/{deploymentId}/status`
- `POST /api/public/deployments/{deploymentId}/apply`
- `GET /api/public/deployments/{deploymentId}/credentials`

These routes are intentionally narrower than the internal operator API.

---

## 4) Standard Integration Sequence

### 4.1 Create Deployment

Call:

- `POST /api/public/deployments`

Recommended request fields:

- `externalDeploymentKey`
- `name`
- `environment`
- `templateId`
- `autoApply`
- `callbackMetadata`

Use `externalDeploymentKey` as your idempotency key per client.

### 4.2 Inspect Returned Deployment Id

Persist:

- your own `externalDeploymentKey`
- returned `deploymentId`
- latest published version if you care about rollout tracking

### 4.3 Apply

If `autoApply=false`, call:

- `POST /api/public/deployments/{deploymentId}/apply`

You may provide:

- `versionId`

If omitted, the platform uses the latest published version.

### 4.4 Poll Status

Call:

- `GET /api/public/deployments/{deploymentId}/status`

Use this to determine:

- deployment status
- health status
- latest release state
- latest verification summary
- the runtime-facing integration posture and connection points when available

### 4.5 Fetch Credentials / Base URLs

Call:

- `GET /api/public/deployments/{deploymentId}/credentials`

Use this when your integration needs to store or bind the deployment connection points.

Preferred interpretation:

- use `integration.preferredIntegrationMode` as the primary integration decision
- use `integration.preferredChatBaseUrl` as the chat/runtime entrypoint
- use `integration.preferredCrudBaseUrl` for supported runtime-backed operational reads
- use `integration.runtimeAuthMode`, `integration.hostBackedRuntimeRequired`, and `integration.guidance` to decide whether your backend must proxy traffic
- if present, use `integration.publicRuntimeBootstrapUrl`, `integration.publicRuntimeAuthorizationHeader`, and `integration.publicRuntimeTokenScheme` for public-runtime token bootstrap flows
- if present, use `integration.publicRuntimeTokenIssuerHint` and `integration.publicRuntimeDefaultAudience` as the deployment-advertised public token hints

Do not treat `connectorBaseUrl` as a customer-facing entrypoint. The public API intentionally withholds the internal connector URL.

---

## 5) Idempotency Rules

### 5.1 Create

If the same client reuses the same `externalDeploymentKey` with the same request shape:

- the platform returns the existing deployment
- HTTP status returns `200`

If the same client reuses the same `externalDeploymentKey` with different key request fields:

- the platform returns `409 Conflict`

### 5.2 Apply

If the same target version is already:

- queued
- provisioning
- verifying
- already applied

then replay returns the same release summary with:

- `idempotentReplay=true`

This is what makes retry-safe backend integration possible.

---

## 6) What Your Backend Should Store

At minimum, store:

- your own external customer/shop/account id
- `externalDeploymentKey`
- returned `deploymentId`
- last known `integration.preferredChatBaseUrl`
- last known `integration.preferredCrudBaseUrl`
- last known `integration.preferredIntegrationMode`

Only store bootstrap/token transport details when your integration actually uses public-runtime mode:

- `integration.publicRuntimeBootstrapUrl`
- `integration.publicRuntimeAuthorizationHeader`
- `integration.publicRuntimeTokenScheme`
- `integration.publicRuntimeTokenIssuerHint`
- `integration.publicRuntimeDefaultAudience`
- `integration.runtimeAuthMode`
- `integration.hostBackedRuntimeRequired`

For richer automation, also store:

- latest release id
- latest published version id
- deployment health state

---

## 7) Recommended Retry Behavior

For create:

- retry safely using the same `externalDeploymentKey`

For apply:

- retry safely for the same `versionId`
- treat `200` replay as success, not failure

For status:

- poll until the deployment reaches the state your integration needs

For credentials:

- only persist connection points after apply/status indicates the deployment is ready

---

## 8) Error Handling

Common response codes:

- `401`
  - invalid or missing public API credentials
- `403`
  - authenticated but not permitted for that resource
- `404`
  - deployment/version not found for that client
- `409`
  - external deployment key reused with a conflicting request
- `400`
  - malformed or invalid request

Do not treat all non-`201` responses as failures. For idempotent operations:

- `200` can be the correct success result

---

## 9) Best Practices For Vertical Consumers

Do:

- treat the platform as the only provisioning system
- keep your own stable `externalDeploymentKey`
- persist `deploymentId` after first create
- use status/credentials instead of guessing deployment readiness

Do not:

- provision Railway resources directly
- depend on internal platform UI or internal APIs
- bypass the platform and hardcode runtime environments manually

This is especially important for Shopify and future vertical wrappers.

---

## 10) Shopify Mapping

For a future Shopify backend, the mapping should look like:

- Shopify shop/install id -> `externalDeploymentKey`
- Shopify backend -> public provisioning API
- platform -> creates deployment
- Shopify backend -> polls status and stores deployment urls

Shopify should consume this API, not reimplement provisioning itself.

---

## 11) Related Docs

- `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`
- `changes/Productization/SHOPIFY_APP_IMPLEMENTATION_PLAN.md`
- `changes/Productization/SHOPIFY_ADMIN_APP_UI_PLAN.md`
