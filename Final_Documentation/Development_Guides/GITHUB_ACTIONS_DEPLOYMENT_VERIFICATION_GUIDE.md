# GitHub Actions Deployment Verification Guide

This guide describes the platform-managed GitHub Actions verification flow.

The goal is:

- run the same verification scripts already used locally
- keep deployment URLs and verification keys in the platform
- dispatch the workflow from the platform UI
- avoid typing long-lived secrets into GitHub Actions inputs

## 1. What This Flow Uses

The platform dispatches `.github/workflows/deployment-verification.yml`.

That workflow does **not** ask the operator for connector, runtime, or platform keys.

Instead, the platform builds a short-lived signed verification context and passes only:

- `deployment_id`
- `release_id`
- `verification_profile`
- `context_url`

The workflow downloads the context JSON, exports the environment, and runs one of:

- `scripts/verify-vector-deployment.sh`
- `scripts/verify-ecommerce-deployment.sh`

So GitHub Actions uses the same verification defaults and scripts already proven locally.

## 2. Required Platform Secrets

Store these in the platform `Secrets` workspace.

Always required:

- `GITHUB_ACTIONS_TOKEN`
  - GitHub token used by the platform backend to dispatch the workflow and list workflow runs
- `CONNECTOR_API_KEY`
  - used by the verification scripts for REST connector ingress

Usually required:

- `APP_ADMIN_API_KEY`
  - used by runtime and REST connector admin probes

Required when platform auth is enabled:

- `PLATFORM_OPERATOR_API_KEY` or `PLATFORM_ADMIN_API_KEY`
  - used by GitHub Actions to call protected platform verification endpoints

## 3. Required Platform Config Flags

If platform auth is enabled and you want GitHub Actions to run the full platform checks, platform API-key auth must also be enabled.

Required config:

- `PLATFORM_AUTH_ENABLED=true`
- `PLATFORM_AUTH_API_KEY_ENABLED=true`

The **value** of the key can stay in the platform Secrets workspace. The config flag only enables the auth mode.

If platform auth is disabled, the workflow can still run because platform verification endpoints are already open.

## 4. Required Deployment State

The deployment must already have:

- a live release
- live `runtimeBaseUrl`
- live `connectorBaseUrl`

For `ecommerce` profile, the deployment must also have:

- `connector.upstream.base-url`

For `vector` profile, the deployment must also have:

- at least one configured AI entity type

## 5. How Dispatch Works

From the `Verification` workspace, an operator selects:

- `Vector deployment`
- or `Ecommerce deployment`

Then the platform backend:

1. resolves the active deployment release and version
2. resolves the deployment source repository and branch
3. builds the verification environment from deployment config plus platform secrets
4. generates a short-lived signed context URL
5. calls the GitHub Actions `workflow_dispatch` API
6. polls recent runs and shows the latest indexed run in the UI

## 6. Security Model

This flow intentionally does **not** send long-lived secrets as GitHub Actions inputs.

Security controls:

- workflow inputs contain only ids and a short-lived signed context URL
- the context URL is signed with `PLATFORM_ARTIFACT_SIGNING_KEY`
- the context URL expires quickly
- the workflow masks values that look like keys, passwords, tokens, or cookies
- platform verification calls use a platform API key only when platform auth requires it

Important:

- the context URL is still sensitive until it expires
- the GitHub token used for dispatch should be scoped narrowly to repository Actions use
- rotate keys that were ever pasted manually outside the platform Secrets workspace

## 7. Workflow Profiles

### 7.1 Vector

Uses:

- `scripts/verify-vector-deployment.sh`

The platform context injects:

- `REST_CONNECTOR_BASE_URL`
- `RUNTIME_BASE_URL`
- `API_KEY`
- `RUNTIME_ADMIN_API_KEY`
- `CONNECTOR_ADMIN_API_KEY`
- `PLATFORM_BASE_URL`
- `PLATFORM_DEPLOYMENT_ID`
- `PLATFORM_API_KEY` when needed
- `PLATFORM_EXPECT_RELEASE_ID`
- `PLATFORM_EXPECT_VERSION_ID`
- `EXPECTED_VECTOR_SPACES`
- `EXPECTED_VECTOR_DB`
- `VERIFY_WRITE`

### 7.2 Ecommerce

Uses:

- `scripts/verify-ecommerce-deployment.sh`

The platform context injects:

- all shared runtime/connector/platform values above
- `STORE_BASE_URL`
- `PLATFORM_EXPECT_RELEASE_STATUS=APPLIED_VERIFIED`
- `PLATFORM_EXPECT_VERIFICATION_STATUS=PASSED`

## 8. GitHub Token Requirements

The GitHub token used in `GITHUB_ACTIONS_TOKEN` must be able to:

- dispatch workflows in the deployment source repository
- read workflow runs in that repository

In practice, use a fine-grained token with repository Actions permissions where possible.

## 9. Current Operator Flow

1. Configure deployment URLs and auth as usual.
2. Store verification keys in the platform `Secrets` workspace.
3. Ensure `PLATFORM_AUTH_API_KEY_ENABLED=true` if platform auth is on.
4. Open `Verification`.
5. Choose `Vector deployment` or `Ecommerce deployment`.
6. Click `Run in GitHub Actions`.
7. Watch recent runs in the same page.
8. Open the latest run if deeper GitHub-side logs are needed.

## 10. What This Does Not Replace

This does not replace platform-side release verification.

You still have:

- release verification in the platform backend
- readiness and diagnostics in the platform UI
- direct local script usage when needed

GitHub Actions verification adds:

- reproducible CI-hosted verification
- branch/repository traceability
- reusable headless execution without re-entering keys
