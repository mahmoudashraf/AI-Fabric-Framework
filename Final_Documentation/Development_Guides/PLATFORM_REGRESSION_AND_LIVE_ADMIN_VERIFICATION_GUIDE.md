# Platform Regression And Live Admin Verification Guide

This guide describes the platform regression model for the current product.

The regression system is split into two tracks:

- **code regression**
- **live admin/API regression**

The primary goal is to protect real platform behavior, not only compile-time correctness.

## 1. What Runs Where

### 1.1 Code regression

Workflow files:

- [.github/workflows/platform-code-regression.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/.github/workflows/platform-code-regression.yml)
- [.github/workflows/platform-code-regression-gate.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/.github/workflows/platform-code-regression-gate.yml)

Use this for:

- pull requests
- pushes to key platform branches
- manual repo-side regression runs

It runs:

- platform backend tests
- `ai-fabric-product` tests
- targeted `ai-infrastructure-module` tests used by the platform product path
- platform UI build
- hosted verification shell syntax checks

This is the fast merge gate.

### 1.2 Live admin/API regression

Workflow file:

- [.github/workflows/platform-admin-live-regression.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/.github/workflows/platform-admin-live-regression.yml)

Use this for:

- nightly live verification
- release readiness checks
- manual full-platform regression against Railway

It uses:

- real platform admin authentication
- live deployment verification
- write-backed verification where configured
- tenant-shared isolation proof
- managed provider verification

This is the real platform behavior gate.

## 2. Authentication Model

The live regression workflow supports:

- `PLATFORM_API_KEY`
- or `PLATFORM_LOGIN_EMAIL`
- and `PLATFORM_LOGIN_PASSWORD`

That means the workflow can verify the live platform using:

- admin API key mode
- or real admin user login/session mode

This is intentional. The platform regression suite is meant to verify real operator behavior.

## 3. Required Repository Variables

The live workflow resolves deployment ids from repository variables unless manual inputs override them.

Recommended repository variables:

- `PLATFORM_BASE_URL`
- `REGRESSION_ECOMMERCE_DEPLOYMENT_ID`
- `REGRESSION_QDRANT_DEPLOYMENT_ID`
- `REGRESSION_PINECONE_DEPLOYMENT_ID`
- `REGRESSION_MILVUS_DEPLOYMENT_ID`
- `REGRESSION_WEAVIATE_DEPLOYMENT_ID`
- `REGRESSION_TENANT_PRIMARY_DEPLOYMENT_ID`
- `REGRESSION_TENANT_COUNTERPART_DEPLOYMENT_ID`

This avoids hardcoding a stale regression fleet inside the workflow file.

## 4. Required Secrets

For live admin/API regression, store these GitHub Secrets:

- `PLATFORM_API_KEY`
- or `PLATFORM_LOGIN_EMAIL`
- and `PLATFORM_LOGIN_PASSWORD`
- `CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`

For managed provider verification, add:

- `PINECONE_API_KEY`
- `QDRANT_CLOUD_MANAGEMENT_API_KEY`
- `QDRANT_API_KEY`
- `ZILLIZ_CLOUD_API_KEY`
- `WEAVIATE_API_KEY`

## 5. What The Live Suite Verifies

The live suite can run all of the following:

- ecommerce deployment verification with write-backed checks
- vector deployment verification with write-backed checks
- tenant-shared isolation proof using a deployment pair
- managed vector provider verification

The suite reuses:

- [deployment-verification.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/.github/workflows/deployment-verification.yml)
- [verify-vector-deployment.sh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/verify-vector-deployment.sh)
- [verify-ecommerce-deployment.sh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/verify-ecommerce-deployment.sh)
- [managed-vector-provider-verification.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/.github/workflows/managed-vector-provider-verification.yml)

So the live regression suite is an orchestrator, not a second implementation.

## 6. Recommended Run Modes

### 6.1 Pull request and push

Rely on:

- `Platform Code Regression Gate`

This should be the required branch-protection workflow for platform/product changes.

### 6.2 Nightly

Rely on:

- `Platform Admin Live Regression`

This should verify the current live Railway regression fleet using stored repository variables and secrets.

### 6.3 Before release or major rollout

Run both:

1. `Platform Code Regression`
2. `Platform Admin Live Regression`

Do not treat a release as healthy if only one layer passed.

## 7. Manual Run Procedure

### 7.1 Run code regression

1. Open GitHub Actions.
2. Choose `Platform Code Regression`.
3. Click `Run workflow`.
4. Leave all checks enabled unless you are intentionally narrowing the run.

### 7.2 Run live admin/API regression

1. Open GitHub Actions.
2. Choose `Platform Admin Live Regression`.
3. Click `Run workflow`.
4. Optionally override:
   - `platform_base_url`
   - specific deployment ids
   - which live verification surfaces to run
5. Run the workflow.

If no overrides are given, the workflow should use the repository variables that define the current regression fleet.

## 8. Failure Triage

### 8.1 Code regression failure

Treat this as a repository-side regression.

Common causes:

- backend API contract changed
- product runner/vectorization behavior changed
- targeted framework ingestion behavior changed
- shell verification scripts drifted

### 8.2 Live regression failure

Treat this as a live-platform regression until proven otherwise.

Common causes:

- deployment drift
- expired or missing platform auth
- vectorization runner not healthy
- tenant-shared isolation break
- managed provider control-plane breakage
- stale repository regression deployment ids

## 9. What This Replaces

This guide does not replace:

- [GITHUB_ACTIONS_DEPLOYMENT_VERIFICATION_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/Development_Guides/GITHUB_ACTIONS_DEPLOYMENT_VERIFICATION_GUIDE.md)

That guide is still the right reference for single-deployment verification.

This guide also does not replace:

- [PLATFORM_VECTORIZATION_AND_TENANT_VERIFICATION_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/Development_Guides/PLATFORM_VECTORIZATION_AND_TENANT_VERIFICATION_GUIDE.md)

That guide is still the right reference for platform-admin verification from the product UI.

This guide defines the **regression operating model** for the repository and live platform together.
