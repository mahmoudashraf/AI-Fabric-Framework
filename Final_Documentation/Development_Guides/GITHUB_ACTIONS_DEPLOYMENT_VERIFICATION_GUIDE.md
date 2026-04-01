# GitHub Actions Deployment Verification Guide

This guide describes the **single-deployment manual CI/CD** path for deployment verification.

For the full multi-workflow GitHub suite, use:

- `Final_Documentation/Development_Guides/GITHUB_ACTIONS_VERIFICATION_SUITE_GUIDE.md`

It is intentionally separate from the admin-only platform UI runner:

- platform UI uses a **platform-hosted background job**
- GitHub Actions is a **manual `workflow_dispatch`** option for CI/CD and repository-side diagnostics

Both paths use the same verified shell scripts:

- `scripts/verify-vector-deployment.sh`
- `scripts/verify-ecommerce-deployment.sh`

Both paths also force:

- `VERIFY_WRITE=false`

So the current model is read-only verification only.

## 1. Workflow

The repository workflow for one deployment is:

- `.github/workflows/deployment-verification.yml`

It is triggered manually from the GitHub Actions UI with:

- `deployment_id`
- `verification_profile`
- optional `platform_base_url`

The workflow then:

1. resolves platform auth from GitHub secrets
2. calls the platform context endpoint
3. exports the returned environment
4. runs the matching verification script in read-only mode

## 2. Required GitHub Secrets

Store these in the repository or organization GitHub Actions secrets.

Required:

- `PLATFORM_BASE_URL`
  - for example `https://ai-fabric-framework-production-324f.up.railway.app`

One of these auth options is required:

- `PLATFORM_API_KEY`
  - preferred when platform API-key auth is enabled
- or `PLATFORM_LOGIN_EMAIL`
- and `PLATFORM_LOGIN_PASSWORD`

Usually required for the scripts:

- `CONNECTOR_API_KEY`
- `APP_ADMIN_API_KEY`

Notes:

- the workflow does **not** depend on a platform-stored `GITHUB_ACTIONS_TOKEN`
- the workflow does **not** need connector/runtime/platform keys typed into workflow inputs

## 3. Required Platform Endpoint

The workflow calls:

- `GET /api/deployments/{deploymentId}/hosted-verification-context?profile=...`

This endpoint is admin-only, so the GitHub secret you use must authenticate as a platform admin.

## 4. Manual Run Flow

1. Open GitHub Actions.
2. Choose `Manual Deployment Verification`.
3. Click `Run workflow`.
4. Enter:
   - `deployment_id`
   - `verification_profile`
   - optional `platform_base_url` if not already stored as a secret/variable
5. Run the workflow.

## 5. Security Model

This workflow is intentionally read-only.

Controls:

- it fetches deployment context from the platform instead of asking for raw deployment secrets in workflow inputs
- it uses GitHub Actions secrets for platform authentication
- it forces `VERIFY_WRITE=false`
- it reuses the same scripts used by the platform-hosted admin runner

## 6. What This Is For

Use the GitHub workflow when you want:

- a manual CI/CD verification run
- repository-visible logs
- repeatable headless verification outside the platform web process

Do not treat it as the primary product operations path. The primary product path is the platform-hosted admin runner.

If you want the current full platform state instead of one deployment, use:

- `Platform State Verification Suite`
