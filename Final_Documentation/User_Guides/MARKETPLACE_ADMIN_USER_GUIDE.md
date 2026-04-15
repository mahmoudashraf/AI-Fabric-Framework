# Marketplace Admin User Guide

Status: current branch guide (2026-04-15)

This guide is for the Platform Admin or high-trust Platform Operator who manages marketplace usage in the AI Enablement Platform.

Companion guides:

- `Final_Documentation/User_Guides/PLATFORM_ADMIN_USER_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`
- `Final_Documentation/Development_Guides/MARKETPLACE_PLUGIN_VERIFICATION_AND_TROUBLESHOOTING_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_CONFIG_AND_SECRETS_MANAGEMENT_GUIDE.md`

---

## 1) What Marketplace Admin Owns

Marketplace administration is a control-plane workflow.

Marketplace Admin is responsible for:

- browsing the marketplace catalog
- bootstrapping new deployments from `TEMPLATE` plugins
- installing `ACTION`, `DATA`, and `INFERENCE_PROFILE` plugins onto a selected deployment
- managing plugin entitlement state
- resolving plugin installs into the active deployment draft
- validating, publishing, and applying the resulting deployment version
- reviewing live verification after rollout
- managing publisher and submission workflow for first-party or approved third-party plugins

Important boundary:

- plugin installation does not change live behavior immediately
- live behavior changes only after `draft -> validate -> publish -> apply`

---

## 2) Supported Public Plugin Types

Current shipped public plugin types are:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

How each type is used:

- `TEMPLATE`
  - use the Marketplace bootstrap flow to create a new deployment
  - do not use deployment install APIs for template plugins
- `ACTION`
  - install onto an existing deployment
  - compiles into deployment `actionsConfig`
- `DATA`
  - install onto an existing deployment
  - compiles into deployment `knowledgeSourceConfig`, shell fragments, and plugin-managed dataset config
- `INFERENCE_PROFILE`
  - install onto an existing deployment
  - compiles into deployment `providerConfig`

Not supported:

- arbitrary runtime code plugins
- arbitrary frontend code plugins
- live hot-install without publish/apply

---

## 3) Main Screens You Will Use

### 3.1 Marketplace

Marketplace is a global screen, not a deployment-workspace tab.

Use it to:

- browse plugins by category
- inspect plugin metadata and contribution summary
- choose the target deployment for install or subscription work
- bootstrap template plugins into new deployments
- manage install status and entitlement state
- manage publisher submissions

### 3.2 Deployments

Use this to:

- confirm the target deployment exists
- open the selected deployment after template bootstrap
- review overall deployment health

### 3.3 Verification

Use this to:

- confirm the resolved draft is publish-ready
- review validation errors and warnings before publish

### 3.4 Revisions

Use this to:

- publish the active draft after plugin resolution
- apply the published version
- inspect release history

### 3.5 Diagnostics

Use this to:

- inspect release progress
- inspect verification evidence
- inspect marketplace-related release failures

---

## 4) Standard Admin Workflows

### 4.1 Bootstrap A Deployment From A Template Plugin

Use this for `TEMPLATE` plugins only.

1. Open `Marketplace`.
2. Select a template plugin such as `mkp-template-support-desk-shell`.
3. Enter:
   - deployment name
   - environment
   - optional deployment template
   - optional vector provisioning mode
   - optional customer or tenant ids
4. Run bootstrap.
5. Open the created deployment.
6. Continue with validation, publish, and apply.

Result:

- the template creates a normal deployment draft
- later changes still go through normal deployment lifecycle

### 4.2 Install A Plugin Onto An Existing Deployment

Use this for `ACTION`, `DATA`, and `INFERENCE_PROFILE`.

1. Open `Marketplace`.
2. Select the target deployment in the global install panel.
3. Choose the plugin and version.
4. Fill:
   - `config`
   - `secretRefs`
5. Create the install.
6. Review the install summary and impact preview.
7. If needed, update entitlement state to `ACTIVE`.
8. Resolve the install so the platform recompiles the deployment draft.

Result:

- install state exists in marketplace records
- resolved contribution is written into the deployment draft

### 4.3 Validate, Publish, And Apply

After bootstrap or install resolution:

1. Open the target deployment.
2. Run draft validation.
3. Fix blocking issues.
4. Publish the draft.
5. Apply the published version.
6. Wait for release verification.

This is required for every plugin-backed change.

### 4.4 Confirm The Plugin Is Live

Confirm in this order:

1. Marketplace install summary shows `LIVE` or expected live state.
2. Deployment version has been published and applied.
3. Deployment release reaches verified state.
4. Deployment verification runs pass.
5. Published artifacts contain the expected contribution:
   - `actionsConfig`
   - `knowledgeSourceConfig`
   - `shellConfig`
   - `providerConfig`

---

## 5) Entitlement Management

Entitlements control whether a plugin is allowed to compile into the deployment draft.

Current lifecycle states typically include:

- `PENDING`
- `ACTIVE`
- `PAST_DUE`
- `SUSPENDED`
- `CANCELLED`

Operational rule:

- keep entitlement aligned with the intended commercial state before publish/apply

If entitlement is not active, the plugin may remain installed in marketplace records without contributing to the effective deployment draft.

---

## 6) Publisher Workflow

Publisher workflow is separate from deployment install workflow.

Typical sequence:

1. Create a publisher.
2. Verify the publisher.
3. Submit a plugin manifest version.
4. Validate the submission.
5. Publish or reject the submission.

Useful routes:

- `GET /api/marketplace/publishers`
- `POST /api/marketplace/publishers`
- `PUT /api/marketplace/publishers/{publisherId}/verification`
- `POST /api/marketplace/publishers/{publisherId}/submissions`
- `POST /api/marketplace/submissions/{pluginVersionId}/validate`
- `POST /api/marketplace/submissions/{pluginVersionId}/publish`
- `POST /api/marketplace/submissions/{pluginVersionId}/reject`

Publisher workflow should be handled only by trusted users.

---

## 7) Safe Operating Rules

Do:

- use Marketplace as a global catalog and install surface
- explicitly choose the target deployment before install work
- keep secrets in platform secret management and secret refs only in plugin install data
- re-run draft validation after every install update or entitlement change
- inspect impact before publish

Do not:

- treat install creation as a live rollout
- copy raw secrets into plugin manifest JSON
- try to install template plugins into existing deployments
- enable more than one inference-profile plugin on the same deployment

---

## 8) Minimal Route Reference

Catalog and publisher:

- `GET /api/marketplace/plugins`
- `GET /api/marketplace/plugins/{pluginId}`
- `GET /api/marketplace/categories`
- `GET /api/marketplace/publishers`

Bootstrap and installs:

- `POST /api/marketplace/templates/{pluginId}/bootstrap`
- `GET /api/deployments/{deploymentId}/marketplace-installs`
- `POST /api/deployments/{deploymentId}/marketplace-installs`
- `PUT /api/deployments/{deploymentId}/marketplace-installs/{installId}`
- `PUT /api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement`
- `POST /api/deployments/{deploymentId}/marketplace-installs/{installId}/resolve`
- `DELETE /api/deployments/{deploymentId}/marketplace-installs/{installId}`

Deployment lifecycle:

- `GET /api/deployments/{deploymentId}/draft`
- `PUT /api/deployment-drafts/{draftId}`
- `POST /api/deployment-drafts/{draftId}/validate`
- `POST /api/deployment-drafts/{draftId}/publish`
- `POST /api/deployments/{deploymentId}/apply/{versionId}`
- `GET /api/deployments/{deploymentId}/releases`
- `GET /api/deployments/{deploymentId}/verification-runs`

---

## 9) Troubleshooting

If install exists but nothing changed in the draft:

- check entitlement state
- run install resolve
- re-open the deployment draft

If publish or apply fails:

- inspect validation output
- inspect release diagnostics
- inspect the published artifact payloads

If a shared `DATA` plugin fails after apply:

- check whether the deployment uses a shared-storage-capable vector backend
- check dataset sync status and verification evidence

For deeper debugging:

- `Final_Documentation/Development_Guides/MARKETPLACE_PLUGIN_VERIFICATION_AND_TROUBLESHOOTING_GUIDE.md`
