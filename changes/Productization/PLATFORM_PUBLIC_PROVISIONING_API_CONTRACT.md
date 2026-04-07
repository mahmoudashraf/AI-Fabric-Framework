# Platform Public Provisioning API Contract

Status: Initial Phase 21 contract for vertical consumers such as Shopify (2026-03-29)

This document defines the stable public provisioning boundary exposed by the platform control plane.

The purpose of this API is to let vertical consumers:

- create a deployment
- inspect deployment status
- request apply/re-apply
- fetch runtime and connector base URLs

This API is intentionally narrower than the internal operator API.

It does **not** expose:

- draft editors
- raw secret mutation
- arbitrary Railway passthrough
- internal verification internals

---

## 1) Authentication Model

The public API uses a machine-client API key model separate from:

- platform operator session auth
- platform operator API-key fallback
- deployment artifact delivery auth

Current request headers:

- `X-PLATFORM-CLIENT-ID`
- `X-PLATFORM-PUBLIC-API-KEY`

These names are configurable through:

- `PLATFORM_PUBLIC_API_CLIENT_ID_HEADER`
- `PLATFORM_PUBLIC_API_KEY_HEADER`

The API is disabled by default and enabled through:

- `PLATFORM_PUBLIC_API_ENABLED=true`

Configured clients are currently supplied through platform configuration. In the current branch they are intended for controlled platform-managed setup rather than customer self-registration.

---

## 2) Base Paths

Public provisioning base path:

- `POST /api/public/deployments`
- `GET /api/public/deployments/{deploymentId}`
- `GET /api/public/deployments/{deploymentId}/status`
- `POST /api/public/deployments/{deploymentId}/apply`
- `GET /api/public/deployments/{deploymentId}/credentials`

---

## 3) Create Deployment

Endpoint:

- `POST /api/public/deployments`

Purpose:

- create a new deployment bound to an external consumer key
- publish the initial draft to `v1`
- optionally trigger apply

Request body:

```json
{
  "externalDeploymentKey": "shop-123",
  "name": "Shopify Merchant Dev",
  "environment": "dev",
  "templateId": "dev-openai-lucene",
  "autoApply": false,
  "callbackMetadata": {
    "shopDomain": "merchant-dev.myshopify.com"
  }
}
```

Fields:

- `externalDeploymentKey`
  - required
  - must be unique per public client
  - used for idempotent create replay
- `name`
  - required
- `environment`
  - required
- `templateId`
  - required
- `autoApply`
  - optional
  - when `true`, the platform immediately queues apply for the initial published version
- `callbackMetadata`
  - optional JSON object for consumer-side metadata

Response:

- `201 Created` on first successful creation
- `200 OK` on idempotent replay of the same request

Response shape:

```json
{
  "clientId": "shopify-dev",
  "externalDeploymentKey": "shop-123",
  "deploymentId": "dep-95f8ba89",
  "created": true,
  "name": "Shopify Merchant Dev",
  "environment": "dev",
  "templateId": "dev-openai-lucene",
  "status": "DRAFT",
  "activeVersion": null,
  "latestPublishedVersionId": "ver-0c9d1d70",
  "latestPublishedVersionLabel": "v1",
  "runtimeBaseUrl": null,
  "connectorBaseUrl": null,
  "latestRelease": null,
  "latestVerification": null,
  "createdAt": "2026-03-29T16:45:00Z",
  "updatedAt": "2026-03-29T16:45:00Z"
}
```

Idempotency rules:

- if the same client reuses the same `externalDeploymentKey` with the same request shape, the platform returns the existing deployment
- if the same client reuses the same `externalDeploymentKey` with different `name`, `environment`, or `templateId`, the platform returns `409 Conflict`

---

## 4) Get Deployment

Endpoint:

- `GET /api/public/deployments/{deploymentId}`

Purpose:

- fetch the current external-facing deployment summary

Response:

- `200 OK`
- same model as create response

Security boundary:

- a client can only access deployments bound to that same client id

---

## 5) Get Deployment Status

Endpoint:

- `GET /api/public/deployments/{deploymentId}/status`

Purpose:

- fetch the operational state of the deployment without exposing internal editor surfaces

Response shape:

```json
{
  "clientId": "shopify-dev",
  "externalDeploymentKey": "shop-123",
  "deploymentId": "dep-95f8ba89",
  "status": "APPLIED",
  "healthStatus": "HEALTHY",
  "healthSummary": "Verification passed",
  "activeVersion": "v1",
  "latestPublishedVersionId": "ver-0c9d1d70",
  "latestPublishedVersionLabel": "v1",
  "runtimeBaseUrl": "https://runtime-dep-95f8ba89-dev.up.railway.app",
  "connectorBaseUrl": "https://rest-connector-dep-95f8ba89-dev.up.railway.app",
  "latestRelease": {
    "id": "rel-24e4bfc9"
  },
  "latestVerification": {
    "status": "PASSED"
  },
  "createdAt": "2026-03-29T16:45:00Z",
  "updatedAt": "2026-03-29T16:50:10Z"
}
```

The exact nested release/verification payloads follow the existing platform summary models and may grow with additive fields.

---

## 6) Apply Deployment

Endpoint:

- `POST /api/public/deployments/{deploymentId}/apply`

Purpose:

- request deployment apply for the latest published version
- or request apply for a specific published version

Request body:

```json
{
  "versionId": "ver-0c9d1d70"
}
```

`versionId` is optional.

Behavior:

- if `versionId` is omitted, the platform uses the latest published version
- if no version exists yet, the platform publishes the active draft first

Response:

- `201 Created` when a new apply request is queued
- `200 OK` when the request is treated as an idempotent replay

Response shape:

```json
{
  "clientId": "shopify-dev",
  "externalDeploymentKey": "shop-123",
  "deploymentId": "dep-95f8ba89",
  "versionId": "ver-0c9d1d70",
  "versionLabel": "v1",
  "idempotentReplay": false,
  "release": {
    "id": "rel-24e4bfc9",
    "status": "APPLY_REQUESTED"
  }
}
```

Idempotency rules:

- if the latest release for the target version is already in progress or already completed, replay returns the same release payload with `idempotentReplay=true`

Replayable release states currently include:

- `APPLY_REQUESTED`
- `PROVISIONING`
- `VERIFYING`
- `APPLIED_VERIFIED`
- `APPLIED_VERIFICATION_FAILED`

---

## 7) Get Deployment Credentials

Endpoint:

- `GET /api/public/deployments/{deploymentId}/credentials`

Purpose:

- return the public connection points the vertical consumer needs to use or store

Response shape:

```json
{
  "clientId": "shopify-dev",
  "externalDeploymentKey": "shop-123",
  "deploymentId": "dep-95f8ba89",
  "runtimeBaseUrl": "https://runtime-dep-95f8ba89-dev.up.railway.app",
  "connectorBaseUrl": null,
  "access": {
    "runtimeAuthMode": "PRIVATE_RUNTIME_TRUSTED_BACKEND"
  },
  "integration": {
    "preferredChatBaseUrl": "https://runtime-dep-95f8ba89-dev.up.railway.app",
    "preferredCrudBaseUrl": "https://runtime-dep-95f8ba89-dev.up.railway.app",
    "publicRuntimeBootstrapUrl": null,
    "publicRuntimeAuthorizationHeader": null,
    "publicRuntimeTokenScheme": null,
    "runtimeAuthMode": "PRIVATE_RUNTIME_TRUSTED_BACKEND",
    "hostBackedRuntimeRequired": true,
    "connectorInternalOnly": true,
    "guidance": "Runtime is configured for trusted-backend/private-runtime integration. Route customer traffic through your host or storefront backend; do not expose the connector directly."
  }
}
```

This response intentionally does not expose platform operator secrets or raw Railway metadata.

Important contract clarification:

- `runtimeBaseUrl` remains for compatibility and discovery
- `connectorBaseUrl` is intentionally withheld from public clients and should be treated as internal-only
- `integration` is the preferred consumer-facing contract for deciding:
  - where chat traffic should go
  - whether a host backend is required
  - whether public runtime bootstrap/token mode is available
  - which header/token scheme a public runtime expects

---

## 8) Error Model

Common error shape:

```json
{
  "success": false,
  "message": "Human-readable description.",
  "errorCode": "ERROR_CODE"
}
```

Common statuses:

- `401 Unauthorized`
  - missing or invalid public API credentials
- `403 Forbidden`
  - authenticated but not allowed to access the target resource
- `404 Not Found`
  - deployment or version not found for that client
- `409 Conflict`
  - idempotent create key reused with a different request
- `400 Bad Request`
  - invalid request payload or missing public API identity context

---

## 9) Audit Model

Public API actions are recorded distinctly from operator actions.

Current audit action names include:

- `PUBLIC_API_DEPLOYMENT_CREATED`
- `PUBLIC_API_DEPLOYMENT_CREATE_REPLAYED`
- `PUBLIC_API_APPLY_REQUESTED`
- `PUBLIC_API_APPLY_REPLAYED`

Actor role for public clients:

- `PUBLIC_API_CLIENT`

This lets the platform distinguish:

- browser operator activity
- internal platform admin/operator activity
- external vertical consumer activity

---

## 10) Intended Consumers

This API is designed for:

- Shopify app backend
- future vertical admin apps
- domain onboarding services that create deployments on behalf of customers

It is not designed for:

- direct browser use by end users
- arbitrary third-party Railway automation
- full deployment config editing

---

## 11) Current V1 Limitations

- public clients are configured statically at the platform layer
- provider profile selection is still template-driven rather than a separate public API field
- callback metadata is stored but not yet used for callbacks/webhooks
- there is not yet an async operation resource; apply replay returns the release summary instead

These are acceptable V1 constraints for Phase 21.
