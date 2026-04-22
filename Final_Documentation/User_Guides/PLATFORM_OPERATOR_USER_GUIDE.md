# Platform Operator User Guide

Status: current branch guide (2026-03-29)

This guide is for the **Platform Operator** user type in the AI Enablement Platform.

The Platform Operator is the main day-to-day user of the platform. This is the enablement consultant, implementation engineer, or operations user who creates deployments, edits drafts, publishes versions, applies changes, and verifies platform-managed environments.

Companion guide:

- `Final_Documentation/User_Guides/PLATFORM_USER_TYPES_GUIDE.md`
- `Final_Documentation/User_Guides/MARKETPLACE_ADMIN_USER_GUIDE.md`

---

## 1) What The Platform Operator Owns

Platform Operator responsibilities:

- create deployments
- edit deployment drafts
- validate and publish versions
- apply versions
- inspect release state and verification
- review diagnostics and audit trails

Platform Operator does **not** own:

- platform secret mutation
- privileged platform bootstrap changes

Those remain Platform Admin responsibilities.

---

## 2) How Operator Access Works

Current branch access model:

- browser session login is the main path
- API-key fallback still exists
- operator auth can access deployment and diagnostics APIs
- operator auth can list platform secrets, but cannot update or clear them

Useful backend routes:

- `GET /api/platform/overview`
- `GET /api/deployment-templates`
- `GET /api/deployments`
- `POST /api/deployments`
- `GET /api/deployments/{deploymentId}/draft`
- `PUT /api/deployment-drafts/{draftId}`
- `POST /api/deployment-drafts/{draftId}/validate`
- `POST /api/deployment-drafts/{draftId}/publish`
- `POST /api/deployments/{deploymentId}/apply/{versionId}`
- `GET /api/deployments/{deploymentId}/releases`
- `GET /api/deployments/{deploymentId}/verification-runs`

---

## 3) Main Screens You Will Use

### 3.1 Deployments

Use this for:

- creating a deployment
- understanding active deployment health
- selecting the deployment you will work on

### 3.2 Actions

Use this for:

- action definitions
- route mapping and upstream behavior

### 3.3 Marketplace

Use this global screen to:

- browse available marketplace plugins
- select the target deployment for install work
- inspect install impact
- bootstrap new deployments from template plugins

If a workflow requires publisher administration or secret escalation, involve Platform Admin.

### 3.4 Knowledge

Use this for:

- entity and vector-space-related draft editing

### 3.5 Providers

Use this for:

- provider/model configuration in the active draft

### 3.6 Security

Use this for:

- deployment security draft values
- viewing secret presence/readiness
- Railway preflight checks

If a required secret is missing, escalate to Platform Admin.

### 3.7 Verification

Use this before publish to confirm:

- the draft is publish-ready
- warnings are understood
- errors are fixed

### 3.8 Revisions

Use this to:

- publish a draft
- apply a version
- inspect release history

### 3.9 Diagnostics

Use this to:

- inspect verification evidence
- read release step progress
- inspect audit history

---

## 4) Standard Operator Workflow

### 4.1 Create Or Select A Deployment

1. Open `Deployments`.
2. Create a deployment or choose an existing one.
3. Confirm you are editing the correct environment and template.

### 4.2 Edit The Draft

1. Update the draft in:
   - `Marketplace` when plugin-backed changes are needed
   - `Actions`
   - `Knowledge`
   - `Providers`
   - `Security`
2. Save changes after each section.

### 4.3 Validate

1. Open `Verification`.
2. Review validation findings.
3. Fix blocking errors.

### 4.4 Publish

1. Open `Revisions`.
2. Publish the active draft.
3. Confirm a new version label is created.

### 4.5 Apply

1. Apply the desired version.
2. Watch the release transition through queued/provisioning/verifying states.
3. Open `Diagnostics` if progress stalls or verification fails.

---

## 5) When To Escalate To Platform Admin

Escalate if:

- preflight fails because required secrets are missing
- you need to rotate or clear platform secrets
- a provisioning failure points to platform-global configuration
- you need to change privileged environment bootstrap behavior

As operator, stay focused on deployment and config lifecycle work, not privileged platform state changes.

---

## 6) What Good Looks Like

An operator-managed deployment is healthy when:

- a version has been published
- apply has completed
- runtime and connector URLs are present
- verification has passed
- the deployment health summary is positive

For stub/local flows, placeholder runtime and connector URLs are acceptable if verification passed as expected.

---

## 7) Safe Operating Rules

Do:

- keep changes versioned through drafts and publish/apply
- validate before publish
- use Diagnostics instead of guessing
- use archive only with explicit intent

Do not:

- ask Platform Admin to change secrets for every routine config change
- make direct ad hoc deployment changes outside the platform
- treat UI draft edits as live until they are published and applied

---

## 8) Operator Troubleshooting

If validation fails:

- fix the section identified by the issue path/code
- re-run validation

If apply replays unexpectedly:

- check whether the same version already has an in-flight or completed release
- inspect release status in `Revisions` or `Diagnostics`

If deployment health is unclear:

- use `Deployments` overview first
- then open `Diagnostics`
- then open audit events if the cause is still unclear

---

## 9) Related Docs

- `Final_Documentation/User_Guides/PLATFORM_ADMIN_USER_GUIDE.md`
- `Final_Documentation/User_Guides/CUSTOMER_ADMIN_USER_GUIDE.md`
- `changes/Productization/PLATFORM_PHASE_18_PLUS_EXECUTION_PLAN.md`
