# Platform Config And Secrets Management Guide

Status: current branch guide (2026-03-30)

This guide explains how to separate **platform-managed configuration** from **platform-managed secrets** in the AI Enablement Platform.

Primary audience:

- Platform Admin
- Platform Operator

Companion guides:

- `Final_Documentation/User_Guides/PLATFORM_ADMIN_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_PRODUCTION_DEPLOYMENT_GUIDE.md`

---

## 1) Core Rule

Use this rule consistently:

- **Config** = safe, non-secret deployment behavior and product settings
- **Secrets** = credentials, tokens, private keys, signing keys, and sensitive shared secrets

The platform should store both, but it should **not** treat them the same way.

---

## 2) What Belongs In Config

Config is the part of the deployment that defines how the system behaves.

Examples:

- action catalog definitions
- action routing definitions
- upstream base URLs
- authz mode
- authz base URL
- entity/vector-space definitions
- searchable fields
- embeddable fields
- metadata fields
- provider selection
- model names
- embedding dimensions
- vector DB type
- CORS settings
- feature flags
- deployment source overrides such as repo/branch

Config is expected to be:

- versioned
- diffable
- publishable
- reviewable
- safe to show in admin/operator UI

In the current platform flow, config is compiled into deployment artifacts such as:

- `ai-actions.yml`
- `ai-entity-config.yml`
- `actions-routing.yml`
- `deployment-manifest.json`

---

## 3) What Belongs In Secrets

Secrets are values that must not live in draft JSON, published config artifacts, committed files, or general UI forms.

Examples:

- `OPENAI_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`
- `PLATFORM_ARTIFACT_SIGNING_KEY`
- public API client secrets
- any future provider API key
- any future JWT signing secret or webhook secret

Secrets should be:

- stored in the platform secret layer
- injected into Railway env only at apply time
- masked in logs and UI
- rotated deliberately
- limited to Platform Admin mutation

---

## 4) Simple Decision Test

Ask:

- If this value is leaked, does it create direct security or billing risk?
  - If yes, it is a **secret**.
- If this value is something an operator should review in a diff or publish as part of a deployment version, it is **config**.

Examples:

- `llmProvider=openai` -> config
- `embeddingModel=text-embedding-3-small` -> config
- `OPENAI_API_KEY=...` -> secret
- `AUTHZ_UPSTREAM_BASE_URL=https://customer-app.example.com` -> config
- `X-AIFABRIC-API-KEY actual value` -> secret
- `X-ADMIN-API-KEY actual value` -> secret

---

## 5) Current Platform Mapping

### 5.1 Config owned by deployment drafts and versions

These belong in the deployment draft/version model:

- `actionsConfig`
- `entityConfig`
- `providerConfig`
- `securityConfig`
- deployment source override

This means they:

- are edited in platform screens
- are validated before publish
- become part of immutable published versions

### 5.2 Secrets owned by platform secret management

These belong in the platform secret store:

- `OPENAI_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`
- `PLATFORM_ARTIFACT_SIGNING_KEY`

Role of each secret:

- `CONNECTOR_API_KEY`
  - protects inbound REST connector endpoints such as `/actions/execute`
- `ACTIONS_CONNECTOR_API_KEY`
  - lets runtime call the REST connector for action execution
- `APP_ADMIN_API_KEY`
  - protects runtime `/api/admin/*` endpoints
  - protects REST connector `/api/admin/*` endpoints in platform-managed deployments
  - should also be used by the connector runtime-proxy when it forwards runtime admin requests

This means they:

- are not part of published draft JSON
- are not included in config bundle artifacts
- are resolved during Railway provisioning

---

## 6) Why This Separation Matters

### 6.1 Security

If secrets are mixed into normal config:

- they can leak in draft exports
- they can leak in artifact URLs or manifests
- they can leak in support screenshots
- they can leak through git or copied deployment files

### 6.2 Product quality

If non-secret config is hidden inside secret/env management:

- it becomes hard to diff
- it becomes hard to review
- it becomes hard to version
- operators lose visibility into real deployment behavior

### 6.3 Operational clarity

When config and secrets are separated:

- publish/apply flows are easier to reason about
- deployment failures are easier to debug
- platform ownership stays clear

---

## 7) Real Example From The Ecommerce Demo

Current lesson from the live ecommerce deployment:

- the runtime and connector could be provisioned correctly
- the data-sync proxy path could work
- but indexing still failed because `OPENAI_API_KEY` was set to `test`

This is exactly why provider selection and provider credentials must be separated:

- `providerConfig.llmProvider=openai` is valid config
- `OPENAI_API_KEY=test` is an invalid secret value

When indexing failed, the problem was not deployment config structure. It was the secret value used for embeddings.

Manual data-sync upsert proved this:

- runtime returned `EMBEDDING_FAILED`

That is a secret/provider problem, not an actions/entities config problem.

---

## 8) Where To Manage Each Type In The Platform

### 8.1 Config management

Use the normal platform screens:

- `Actions`
- `Knowledge`
- `Providers`
- `Security`
- `Revisions`
- `Verification`

These screens should manage the publishable deployment definition.

### 8.2 Secret management

Use the Platform Admin secret management area on `Security`.

Only Platform Admin should:

- set secret values
- rotate secret values
- clear secret overrides

Platform Operators may need to see readiness status, but should not generally mutate secrets.

---

## 9) How Secrets Flow At Apply Time

The intended flow is:

1. Platform draft/version defines config.
2. Platform secret store holds credentials and signing keys.
3. Railway apply resolves `${secret:...}` placeholders.
4. Runtime and REST connector receive concrete env vars in Railway.
5. Published config artifacts remain non-secret.

Examples:

- `AI_ACTIONS_CATALOG_PATH` -> config artifact URL
- `AI_CONFIG_DEFAULT_FILE` -> config artifact URL
- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION` -> config artifact URL
- `OPENAI_API_KEY` -> resolved secret
- `CONNECTOR_API_KEY` -> resolved secret
- `ACTIONS_CONNECTOR_API_KEY` -> resolved secret
- `APP_ADMIN_API_KEY` -> resolved secret for runtime admin protection and admin-proxying

Admin/runtime proxy note:

- direct browser calls to runtime `/api/admin/*` without `X-ADMIN-API-KEY` should return `401`
- in platform-managed deployments, the REST connector runtime proxy should use:
  - `REST_CONNECTOR_RUNTIME_PROXY_API_KEY=${secret:APP_ADMIN_API_KEY}`
  - `REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER=X-ADMIN-API-KEY`

---

## 10) What Should Never Happen

Do not:

- store `OPENAI_API_KEY` in `providerConfig`
- store connector API keys in routing JSON
- commit real secrets into repo config files
- treat Railway service env as the long-term source of truth
- copy secret values into deployment manifests
- use placeholder values like `test` and assume indexing/chat will still be a valid production verification

Railway env is the runtime delivery mechanism. The platform remains the source of truth.

---

## 11) Recommended Management Model

Use this model:

- deployment drafts/versions own non-secret behavior
- platform secret store owns sensitive values
- publish creates immutable config versions
- apply injects secrets into the target environment
- verification proves behavior using the resolved deployment

This is the clean product boundary for AI enablement as a control-plane product.

---

## 12) Practical Checklist

Before publish:

- actions, entities, providers, and security config are correct
- no secret values are embedded in config JSON

Before apply:

- required platform secrets are present
- Railway preflight is green
- provider secrets are real values, not placeholders

After apply:

- check connector and runtime health
- check runtime proxy endpoints
- check runtime indexing overview
- run end-to-end verification

If indexing fails:

- check provider secret validity first
- then check runtime/connector connectivity
- then check entity config and vector-space mapping

---

## 13) Short Rule To Remember

Use config for **behavior**.

Use secrets for **trust**.

If a value changes how the deployment behaves, it is usually config.

If a value grants access, signs requests, or spends money, it is a secret.
