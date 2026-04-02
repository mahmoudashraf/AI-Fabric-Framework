# Platform Credentials And Secret Boundaries Guide

This guide defines where each credential belongs in Platform V2.

The most important rule is:

- Railway service env vars configure the platform service itself
- platform `Secrets` workspace stores deployment-facing and vendor-facing secrets
- platform API auth keys authenticate callers to the platform API
- deployment draft JSON must not contain live secret values

## 1. Credential Classes

### 1.1 Platform service environment variables

These are set on the Railway deployment for the platform backend itself.

Examples:

- `PLATFORM_DB_URL`
- `PLATFORM_DB_USERNAME`
- `PLATFORM_DB_PASSWORD`
- `RAILWAY_API_TOKEN`
- `RAILWAY_WORKSPACE_ID`
- `PLATFORM_DEPLOY_REPOSITORY`
- `PLATFORM_DEPLOY_BRANCH`
- `PLATFORM_PUBLIC_BASE_URL`
- `PLATFORM_PROVISIONING_MODE`

These do not belong in the platform `Secrets` workspace.

Why:

- `PLATFORM_DB_*` configures the platform application datasource
- `RAILWAY_API_TOKEN` and `RAILWAY_WORKSPACE_ID` configure the platform provisioning provider
- repository, branch, and public base URL are platform runtime settings, not deployment secrets

### 1.2 Platform API authentication keys

These authenticate a caller to the platform API itself.

Examples:

- `PLATFORM_OPERATOR_API_KEY`
- `PLATFORM_ADMIN_API_KEY`

These are not vendor keys and not deployment runtime keys.

They are used only when:

- `PLATFORM_AUTH_API_KEY_ENABLED=true`
- a caller sends `X-PLATFORM-API-KEY: <value>`

Recommended use:

- headless automation
- CI/CD control-plane calls
- approved scripts

Role separation:

- `PLATFORM_OPERATOR_API_KEY` grants `PLATFORM_OPERATOR`
- `PLATFORM_ADMIN_API_KEY` grants `PLATFORM_ADMIN`

Storage:

- may be provided through Railway env
- may also be stored in the platform `Secrets` workspace

### 1.3 Platform operational secrets

These are used by the platform control plane during delivery or signing.

Examples:

- `PLATFORM_ARTIFACT_SIGNING_KEY`

Storage:

- may be provided through Railway env
- may also be stored in the platform `Secrets` workspace

This is a platform secret, not a deployment draft field.

### 1.4 Deployment runtime and connector auth secrets

These protect deployed runtime and connector surfaces.

Examples:

- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`

Storage:

- platform `Secrets` workspace

Do not store these in draft JSON.

Meaning:

- `CONNECTOR_API_KEY`
  - protects inbound connector business ingress
- `ACTIONS_CONNECTOR_API_KEY`
  - runtime-to-connector shared key
- `APP_ADMIN_API_KEY`
  - protects runtime and connector admin endpoints

### 1.5 Vendor control-plane credentials

These are used by the platform when it provisions or manages vendor resources.

Examples:

- `QDRANT_CLOUD_MANAGEMENT_API_KEY`
- `PINECONE_API_KEY`
- `ZILLIZ_CLOUD_API_KEY`

Storage:

- platform `Secrets` workspace

These are used by:

- rollout apply
- managed vector provisioning
- provider verification
- vendor cleanup/remediation

### 1.6 Vendor runtime or data-plane credentials

These are used by the deployed runtime against the actual vector service after apply.

Examples:

- `QDRANT_API_KEY`
- `WEAVIATE_API_KEY`
- `MILVUS_USERNAME`
- `MILVUS_PASSWORD`

Storage:

- platform `Secrets` workspace

These are not platform API auth keys.

### 1.7 Platform-managed generated secrets

Some secrets are created by the platform during managed provisioning and stored as managed DB secrets.

Examples:

- deployment-scoped Pinecone runtime secret names
- deployment-scoped Qdrant database API keys
- deployment-scoped Zilliz runtime username/password material

These are internal managed secrets and are not entered manually by operators in most cases.

## 2. What Goes Where

### 2.1 Railway env on the platform backend service

Put these in Railway env for the platform backend:

- `PLATFORM_DB_URL`
- `PLATFORM_DB_USERNAME`
- `PLATFORM_DB_PASSWORD`
- `RAILWAY_API_TOKEN`
- `RAILWAY_WORKSPACE_ID`
- `PLATFORM_DEPLOY_REPOSITORY`
- `PLATFORM_DEPLOY_BRANCH`
- `PLATFORM_PUBLIC_BASE_URL`
- `PLATFORM_PROVISIONING_MODE`
- optional platform auth env values

Do not put them in deployment drafts.

### 2.2 Platform Secrets workspace

Put these in the platform `Secrets` workspace:

- `OPENAI_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`
- `PLATFORM_ARTIFACT_SIGNING_KEY`
- `QDRANT_API_KEY`
- `QDRANT_CLOUD_MANAGEMENT_API_KEY`
- `PINECONE_API_KEY`
- `WEAVIATE_API_KEY`
- `ZILLIZ_CLOUD_API_KEY`
- `MILVUS_USERNAME`
- `MILVUS_PASSWORD`
- optional `PLATFORM_OPERATOR_API_KEY`
- optional `PLATFORM_ADMIN_API_KEY`

### 2.3 Do not put these in draft config JSON

Never place live secret values in:

- `providerConfig`
- `routingConfig`
- `securityConfig`
- `promptConfig`
- `entityConfig`
- `actionsConfig`

Drafts should contain references, non-secret settings, and posture choices only.

## 3. Canonical Verification Rollout Requirements

### 3.1 Common rollout secret set

All canonical verification rollouts require:

- `OPENAI_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`
- `PLATFORM_ARTIFACT_SIGNING_KEY`

### 3.2 Provider-specific rollout secrets

- `Ecommerce Verification`
  - no extra vendor secret
- `OpenAI Qdrant Verification`
  - `QDRANT_CLOUD_MANAGEMENT_API_KEY`
- `OpenAI Pinecone Verification`
  - `PINECONE_API_KEY`
- `OpenAI Milvus Verification`
  - `ZILLIZ_CLOUD_API_KEY`
- `OpenAI Weaviate Verification`
  - `WEAVIATE_API_KEY`

Optional by scenario:

- `QDRANT_API_KEY`
  - only when the target Qdrant cluster requires authenticated data-plane access
- `MILVUS_USERNAME`
- `MILVUS_PASSWORD`
  - only when direct Milvus authentication is required

## 4. Audit And Visibility

### 4.1 What `Present / DATABASE` means

If the Secrets page shows:

- `Present`
- `DATABASE`

that means the platform resolved a non-empty DB-backed value for that secret name.

It does not expose the value itself.

### 4.2 What audit shows

Secret mutation is recorded in the platform audit trail with actions such as:

- `SECRET_UPDATED`
- `SECRET_CLEARED`
- `MANAGED_SECRET_UPDATED`
- `MANAGED_SECRET_CLEARED`

The Secrets page should be used to see:

- who changed a secret
- what action occurred
- when it happened

### 4.3 What audit does not show

Audit proves a secret slot was changed through the platform, but it does not reveal the secret value.

## 5. Common Misunderstandings

### 5.1 `RAILWAY_API_TOKEN`

This is not a deployment secret.

It is a platform provisioning env var and belongs on the platform backend Railway service.

### 5.2 `PLATFORM_DB_PASSWORD`

This is not a platform `Secrets` workspace value.

It is the platform service datasource password and belongs on the platform backend Railway service.

### 5.3 `PLATFORM_OPERATOR_API_KEY`

This is not a vendor key and not a deployment runtime key.

It is a headless authentication key for the platform API.

### 5.4 `APP_ADMIN_API_KEY`

This is not platform login.

It protects runtime and connector admin endpoints after deployment.

### 5.5 `QDRANT_CLOUD_MANAGEMENT_API_KEY` vs `QDRANT_API_KEY`

- `QDRANT_CLOUD_MANAGEMENT_API_KEY`
  - cloud control-plane key for account, cluster, and database API key management
- `QDRANT_API_KEY`
  - cluster data-plane key used against the Qdrant endpoint

They are not interchangeable.

## 6. Recommended Operator Practice

Use this split consistently:

- Railway service env for platform runtime configuration
- platform `Secrets` workspace for deployment and vendor credentials
- platform API auth keys only for approved automation
- no literal live secrets in deployment draft JSON

If a key is needed by the platform to manage infrastructure, store it as a platform secret or platform env based on whether it is:

- platform runtime configuration
- or reusable secret material

When in doubt:

- platform runtime setting -> Railway env on the platform backend
- reusable secret material -> platform `Secrets` workspace
