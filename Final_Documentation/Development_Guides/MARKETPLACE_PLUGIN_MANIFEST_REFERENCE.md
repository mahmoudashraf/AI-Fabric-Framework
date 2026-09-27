# Marketplace Plugin Manifest Reference

Status: strict current-branch reference (2026-09-27)

This document describes the marketplace manifest contract enforced by the current platform implementation.

Source of truth:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceManifestService.java`

---

## 1) Supported Public Plugin Types

Current supported public plugin types:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

Unsupported:

- `AUTOMATION`
- arbitrary shell/plugin code
- arbitrary runtime code
- `capabilityProfiles`

Important rule:

- `capabilityProfiles` is rejected by the current parser

---

## 2) Top-Level Fields

Required top-level fields:

- `schemaVersion`
- `pluginId`
- `version`
- `pluginType`
- `displayName`
- `compatibility`
- `pricing`
- `permissions`
- `contributions`

Current rules:

- `schemaVersion` must be `1`
- `pluginType` must match the catalog plugin type exactly
- `contributions` must be an object

Example skeleton:

```json
{
  "schemaVersion": 1,
  "pluginId": "mkp-action-example",
  "version": "1.0.0",
  "pluginType": "ACTION",
  "displayName": "Example Actions",
  "compatibility": {},
  "pricing": {
    "pricingModel": "FREE"
  },
  "permissions": {
    "contributesActions": true
  },
  "contributions": {
    "actions": []
  }
}
```

---

## 3) Compatibility Block

Supported fields:

- `minPlatformVersion`
- `maxPlatformVersion`
- `requiredCapabilities`
- `supportedDeploymentTargets`
- `supportedAuthModes`
- `supportedProviderModes`

### 3.1 `requiredCapabilities`

Supported values:

- `actions`
- `knowledgeSources`
- `shellConfig`
- `templates`
- `providers`

Normalization rule:

- parser normalizes hyphens and underscores

### 3.2 `supportedAuthModes`

Supported values:

- `PLATFORM_PROXY_SESSION`
- `PRIVATE_RUNTIME_BACKEND_MEDIATED`
- `PUBLIC_RUNTIME_AUTHENTICATED`
- `PUBLIC_RUNTIME_ANONYMOUS`

### 3.3 `supportedProviderModes`

Supported format:

- `key:value`

Supported keys:

- `llm`
- `embedding`
- `vector`
- `runtime`
- `connector`

Example:

```json
{
  "supportedProviderModes": [
    "llm:openai",
    "embedding:openai"
  ]
}
```

---

## 4) Pricing Block

Supported `pricingModel` values:

- `FREE`
- `ONE_OFF`
- `SUBSCRIPTION`

Rules:

- `FREE`
  - no amount or billing fields required
- `ONE_OFF`
  - requires positive `amount`
  - requires `currency`
  - does not allow `billingInterval` or `trialDays`
- `SUBSCRIPTION`
  - requires positive `amount`
  - requires `currency`
  - supports:
    - `billingInterval`: `MONTHLY` or `YEARLY`
    - `trialDays`: non-negative integer

Example:

```json
{
  "pricing": {
    "pricingModel": "SUBSCRIPTION",
    "amount": 29.0,
    "currency": "USD",
    "billingInterval": "MONTHLY",
    "trialDays": 7
  }
}
```

---

## 5) Install Form

`installForm` is optional and must be an array of objects.

Supported field types:

- `text`
- `url`
- `boolean`
- `select`
- `number`
- `secretRef`

Field contract:

- `id` required
- `label` optional
- `type` required
- `required` optional
- `description` optional
- `options` required for `select`

Example:

```json
{
  "installForm": [
    {
      "id": "provider",
      "label": "Notification provider",
      "type": "select",
      "required": true,
      "options": ["sendgrid", "twilio", "slack"]
    },
    {
      "id": "credentialSecretRef",
      "label": "Credential secret ref",
      "type": "secretRef",
      "required": true
    }
  ]
}
```

---

## 6) Permissions

Supported permission booleans:

- `contributesTemplate`
- `contributesActions`
- `contributesKnowledgeSources`
- `contributesProviders`
- `contributesShellPresentation`
- `requiresExternalHttpExecution`
- `requiresSharedDatasetAccess`
- `requiresDeploymentSecrets`

Validation rules:

- action contributions require `contributesActions = true`
- data contributions require `contributesKnowledgeSources = true`
- inference contributions require `contributesProviders = true`
- shell module or card contributions require `contributesShellPresentation = true`
- `secretRef` install-form fields require `requiresDeploymentSecrets = true`

---

## 7) `TEMPLATE` Contributions

Required block:

- `contributions.template`

Supported fields used by the current parser/compiler:

- `template.curatedModuleId`
- `template.security.authzMode`
- `template.recommendedPluginIds`
- `template.shell`

Current supported template security field:

- `authzMode`
  - must be supported by the managed deployment profile catalog

Shell fragment fields commonly used:

- `enabledModuleIds`
- `moduleRefs`
- `enabledCardIds`
- `cardRefs`
- greeting and starter prompt structures

Example:

```json
{
  "pluginType": "TEMPLATE",
  "permissions": {
    "contributesTemplate": true,
    "contributesShellPresentation": true
  },
  "contributions": {
    "template": {
      "curatedModuleId": "support",
      "recommendedPluginIds": ["mkp-data-help-center", "mkp-action-notifications"],
      "security": {
        "authzMode": "ALLOW_VERIFIED"
      },
      "shell": {
        "enabledModuleIds": ["docs", "ai-search", "actions", "support"]
      }
    }
  }
}
```

---

## 8) `ACTION` Contributions

Required block:

- `contributions.actions`

Validation rules:

- must be a non-empty array
- each action must declare `id` or `actionId`
- `route` may declare `url` or `path`, but not both
- if `route` is present, it must include at least one of `url` or `path`

Common action fields used by the current first-party manifests:

- `actionId`
- `displayName`
- `readOnly`
- `confirmationRequired`
- `adapterType`
- `route.method`
- `route.path`
- `route.url`

Example:

```json
{
  "pluginType": "ACTION",
  "permissions": {
    "contributesActions": true,
    "contributesShellPresentation": true,
    "requiresDeploymentSecrets": true
  },
  "contributions": {
    "actions": [
      {
        "actionId": "send-email",
        "displayName": "Send email",
        "readOnly": false,
        "confirmationRequired": true,
        "adapterType": "connector-http",
        "route": {
          "method": "POST",
          "path": "/actions/execute"
        }
      }
    ],
    "shell": {
      "moduleRefs": ["actions"]
    }
  }
}
```

---

## 9) `DATA` Contributions

Required blocks:

- `contributions.datasets`
- `contributions.knowledgeSources`

Optional blocks:

- `contributions.entityConfig`
- `contributions.shell`

### 9.1 Dataset contract

Each dataset must declare:

- `datasetId`
- `entityType`
- `storageScope`
- `sharingScope`
- `ingestionMode`
- `updateStrategy`

Current supported values:

- `storageScope`
  - `PLUGIN_SCOPED`
  - `CUSTOMER_MANAGED`
- `sharingScope`
  - `TENANT_SHARED`
  - `DEPLOYMENT_ONLY`
- `ingestionMode`
  - `PACKAGED_SEED`
  - `EXTERNAL_SYNC_SQL`
  - `EXTERNAL_SYNC_FOLDER`
  - `EXTERNAL_SYNC_HTTP`
  - `EXTERNAL_DOCUMENT_STORAGE`
- `updateStrategy`
  - `UPSERT_BY_ID`
  - `VERSIONED_REPLACE`

For `PACKAGED_SEED`:

- `seedDatasetRef` required

For `EXTERNAL_SYNC_SQL`, `EXTERNAL_SYNC_FOLDER`, and `EXTERNAL_SYNC_HTTP`:

- `syncConnector` required

Supported sync connector types:

- `SQL_QUERY`
- `FILE_FOLDER`
- `HTTP_JSON`

`EXTERNAL_SYNC_HTTP` requires `syncConnector.connectorType=HTTP_JSON`.
`EXTERNAL_DOCUMENT_STORAGE` uses its separate bounded `sourceConnector` and
`documentPolicy` contract described in the Document Knowledge Operations plan.

### 9.2 Knowledge source contract

Each knowledge source must declare:

- `id` or `sourceKey`
- `adapterType` or `sourceType`
- `datasetRef`

Rules:

- if only one dataset exists, `datasetRef` may be omitted and is inferred
- if multiple datasets exist, `datasetRef` is required
- every `datasetRef` must point to a declared dataset

Example:

```json
{
  "pluginType": "DATA",
  "permissions": {
    "contributesKnowledgeSources": true,
    "contributesShellPresentation": true,
    "requiresSharedDatasetAccess": true
  },
  "contributions": {
    "entityConfig": {
      "ai-entities": {
        "support-policy": {
          "entity-type": "support-policy",
          "auto-embedding": true,
          "indexable": true,
          "enable-search": true
        }
      }
    },
    "datasets": [
      {
        "datasetId": "policy-folder-pack",
        "entityType": "support-policy",
        "storageScope": "PLUGIN_SCOPED",
        "sharingScope": "TENANT_SHARED",
        "ingestionMode": "EXTERNAL_SYNC_FOLDER",
        "updateStrategy": "UPSERT_BY_ID",
        "syncConnector": {
          "connectorType": "FILE_FOLDER",
          "folderRef": "classpath*:marketplace/folders/policy-pack/*.md"
        }
      }
    ],
    "knowledgeSources": [
      {
        "sourceType": "shared-index",
        "sourceKey": "policy-folder",
        "datasetRef": "policy-folder-pack",
        "entityType": "support-policy",
        "attributionLabel": "Policy folder marketplace data"
      }
    ],
    "shell": {
      "moduleRefs": ["docs", "ai-search", "support"]
    }
  }
}
```

---

### 9.3 `EXTERNAL_SYNC_HTTP` contract

An HTTP dataset contains:

- `syncConnector.connectionProfile`
- `syncConnector.protectedResource`
- `syncConnector.httpSource`
- optional `syncConnector.webhook`
- optional dataset-level `customerBackendIngestion`

Connection profiles support `API_KEY` and `FORM_TOKEN_EXCHANGE`. They must
declare an HTTPS base URL, an allowlist containing every provider/token host,
capability grants, secret-reference fields, and bounded auth/rate/error policy.
Resolved secret values are deployment configuration and never manifest data.

The protected resource declares `bindingId`, environment, `resourceType`, an
install field containing the resource ID, and capability grants. The ID is
compiled into the deployment and inserted server-side. It is never accepted
from model/action input.

The HTTP source declares:

- stable `sourceId`, relative path, and `GET` or `POST`;
- at least one trusted resource placement;
- grants present on both profile and protected resource;
- `NONE`, `PAGE_SIZE`, or `CURSOR` pagination;
- record, identity, protected-resource, content, entity, and metadata JSON
  Pointer mappings;
- record/response/page bounds and schedule; and
- optional field/snapshot tombstone policy.

Every protected path placeholder must have a matching server-owned `PATH`
placement. Header placement cannot overwrite the provider auth header. Every
record must project the exact protected-resource ID or the entire sync fails
closed before indexing.

Optional provider webhooks support only the reviewed raw-body
`HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY` verifier in this release. They declare a
signature secret-reference field, event/resource JSON Pointers, allowed event
and content types, replay window, reconciliation attempts, retry delay,
registration expectation, and optional operator replay. Webhook processing
always reconciles the latest provider state; event payloads are not treated as
authoritative records.

`customerBackendIngestion`, when explicitly enabled by the immutable dataset,
may grant only these operations:

- `UPSERT`
- `DELETE`
- `WORK_STATUS`
- `READINESS`

This emits backend-only deployment URLs and exact operation flags in assignment
discovery. It does not expose the private connector/runtime channel and must not
be placed in browser configuration.

`UPSERT`, `DELETE`, and `WORK_STATUS` authorization is compiled per dataset
entity type. Enabling work-status lookup for one entity type does not authorize
inspection of work created for another entity type. `READINESS` is a bounded
deployment posture projection and does not return source records or provider
payloads.

The complete executable schema is enforced by `MarketplaceManifestService`;
tests use a provider-neutral manifest so provider-specific field and route names
cannot leak into generic code.

---

## 10) `INFERENCE_PROFILE` Contributions

Required block:

- `contributions.inferenceProfile`

Required fields:

- `profileId` or `id`
- at least one of:
  - `orchestration`
  - `generation`
  - `embedding`

Common section fields:

- `provider`
- `endpointProfileRef`
- `baseUrl`
- `baseUrlField`
- `apiKeySecretRef`
- `apiKeySecretRefField`
- `deploymentName`
- `deploymentNameField`
- `apiVersion`
- `apiVersionField`
- `model`
- `modelField`
- `maxTokens`
- `maxTokensField`
- `temperature`
- `temperatureField`
- `timeout`
- `timeoutField`
- embedding-only fields such as `dimensions`

Example:

```json
{
  "pluginType": "INFERENCE_PROFILE",
  "permissions": {
    "contributesProviders": true,
    "requiresDeploymentSecrets": true
  },
  "contributions": {
    "inferenceProfile": {
      "profileId": "customer-openai",
      "generation": {
        "provider": "openai",
        "baseUrlField": "baseUrl",
        "apiKeySecretRefField": "apiKey",
        "modelField": "generationModel",
        "model": "gpt-4.1-mini",
        "maxTokens": 1800,
        "temperature": 0.3,
        "timeout": 60
      },
      "embedding": {
        "provider": "openai",
        "baseUrlField": "baseUrl",
        "apiKeySecretRefField": "apiKey",
        "modelField": "embeddingModel",
        "model": "text-embedding-3-small",
        "dimensions": 1536
      }
    }
  }
}
```

---

## 11) Recommended Validation Flow

For every new manifest:

1. submit the plugin version
2. run submission validation
3. publish the version
4. install it onto a test deployment
5. resolve the install into the active draft
6. run deployment draft validation
7. publish and apply the deployment
8. verify the resulting runtime behavior

Use:

- `Final_Documentation/Development_Guides/MARKETPLACE_PLUGIN_VERIFICATION_AND_TROUBLESHOOTING_GUIDE.md`
