# Platform Admin User Guide

Status: current branch guide (2026-03-29)

This guide is for the **Platform Admin** user type in the AI Enablement Platform.

The Platform Admin is the highest-trust operator in the control plane. This user is responsible for platform setup, privileged changes, secret management, and escalation support.

Companion guide:

- `Final_Documentation/User_Guides/PLATFORM_USER_TYPES_GUIDE.md`
- `Final_Documentation/User_Guides/PLATFORM_CONFIG_AND_SECRETS_MANAGEMENT_GUIDE.md`
- `Final_Documentation/User_Guides/MARKETPLACE_ADMIN_USER_GUIDE.md`
- `Final_Documentation/User_Guides/THINKER_RESOLVER_USER_GUIDE.md`
- `Final_Documentation/User_Guides/THINKER_RESOLVER_OPERATOR_GUIDE.md`

---

## 1) What The Platform Admin Owns

Platform Admin responsibilities:

- create and manage deployments
- create customers and consumer bindings for external integrations
- edit and publish deployment drafts
- apply versions to target environments
- review verification and diagnostics
- manage platform deployment secrets
- review audit events
- support Platform Operators when provisioning or verification fails

In practice, this is the user who can safely operate the full control plane.

---

## 2) How Platform Admin Access Works

Current branch access model:

- session-based login is the normal browser path
- API-key auth still exists as fallback
- only Platform Admin can update or clear platform secrets

Important backend routes:

- `POST /api/platform/auth/login`
- `POST /api/platform/auth/logout`
- `GET /api/platform/auth/session`
- `GET /api/platform/overview`
- `GET /api/platform/secrets`
- `PUT /api/platform/secrets/{name}`
- `DELETE /api/platform/secrets/{name}`

Typical local UI:

- `http://localhost:5173`

Typical local backend:

- `http://localhost:8088`

---

## 3) Main Screens You Will Use

### 3.1 Deployments

Use this screen to:

- create a new deployment
- review current deployment health
- open Revisions or Diagnostics for a deployment
- archive deployments when needed

### 3.2 Actions

Use this screen to manage:

- action catalog entries
- connector route mappings
- upstream/action execution behavior

### 3.3 Marketplace

Use this global screen to:

- bootstrap deployments from template plugins
- install marketplace plugins onto a selected deployment
- manage entitlement state
- inspect plugin impact before publish/apply
- manage publisher and submission workflow

Marketplace is intentionally global, not a deployment-workspace page.

### 3.4 Customers

Use this screen to manage:

- customers and their tenant grouping
- external consumers owned by a customer
- consumer to deployment binding and rebinding
- consumer binding history for external routing changes

Consumers are the stable external lookup key for runtime discovery. External clients can use `consumerId` while the platform admin rebinds that consumer to a different deployment behind the scenes.

### 3.5 Knowledge

Use this screen to manage:

- entity definitions
- vector-space-related configuration
- searchable fields
- embeddable fields
- metadata fields

### 3.6 Providers

Use this screen to manage:

- LLM/provider settings
- embedding model settings
- vector strategy and related provider configuration

### 3.7 Thinker Resolver

Use this screen to manage governed issue resolution:

- per-deployment Thinker and Resolver controls
- Thinker issue sessions, evidence, plans, audit, and exports
- Resolver policy decisions, dry-runs, and low-risk support escalation executions
- product action-family kill switches

See `Final_Documentation/User_Guides/THINKER_RESOLVER_OPERATOR_GUIDE.md` for the full operating procedure.

### 3.7 Security

Use this screen to manage:

- deployment security draft settings
- platform deployment secrets
- Railway preflight readiness

This is the only screen where secret mutation is allowed for Platform Admin.

### 3.8 Verification

Use this screen to:

- validate draft readiness before publish
- review warnings/errors before rollout

### 3.9 Revisions

Use this screen to:

- inspect active draft
- list published versions
- publish the current draft
- apply a selected version

### 3.10 Diagnostics

Use this screen to:

- review release progress
- inspect verification results
- review audit events
- inspect provisioning plan details

---

## 4) Standard Admin Workflow

### 4.1 Create A Deployment

1. Open `Deployments`.
2. Create a deployment using a template such as `dev-openai-lucene`.
3. Confirm the deployment appears in the active list.

### 4.2 Configure The Draft

1. Open `Actions`, `Knowledge`, `Providers`, and `Security`.
2. Update the active draft for the selected deployment.
3. Save each section.

If the deployment uses marketplace plugins:

1. Open `Marketplace`.
2. Select the target deployment.
3. Install or update the plugin.
4. Resolve the install before returning to deployment draft editing.

### 4.3 Create Or Rebind A Consumer

If the deployment will be consumed by an external backend or frontend:

1. Open `Customers`.
2. Select the owning customer.
3. Create a consumer with a stable `consumerId`.
4. Bind that consumer to the target deployment.
5. If you need a cutover later, rebind the same consumer to a different deployment.

Use consumer rebinding when you need:

- blue/green cutover
- migration to a replacement deployment
- rollback without changing the external client identifier

### 4.4 Validate And Publish

1. Open `Verification`.
2. Run or review validation for the active draft.
3. Fix any errors.
4. Open `Revisions`.
5. Publish the draft to create a version.

### 4.5 Apply

1. In `Revisions`, apply the published version.
2. Watch release state progress.
3. Open `Diagnostics` to confirm provisioning and verification.

### 4.6 Verify Deployment State

Look for:

- release status moving to applied/verified
- runtime and connector base URLs populated
- verification checks passing
- no blocking audit or provisioning errors

---

## 5) Secret Management

Platform Admin is allowed to manage platform secrets.

Examples of platform-level secrets already used by provisioning:

- `OPENAI_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- artifact signing secret

Best practices:

- rotate secrets deliberately
- re-run Railway preflight after secret changes
- avoid editing secrets during an active apply unless required
- keep the number of admins small

---

## 6) Audit And Governance

Platform Admin should review audit events for:

- deployment creation
- publish/apply requests
- archive operations
- secret mutation
- public API client actions

Current audit view:

- `GET /api/platform/audit-events`

Use the Diagnostics screen for the same information in the UI.

---

## 7) What Platform Admin Should Not Do

Avoid:

- using the public provisioning API as a substitute for platform UI administration
- making direct Railway changes without reflecting them back through the platform release flow
- treating baked Docker config as source of truth
- applying versions before validation is clean unless you are intentionally testing failure paths

The platform, not manual env drift, should remain the source of truth.

---

## 8) Troubleshooting Checklist

If apply fails:

- check `Diagnostics` for release step and error message
- review `Verification` output
- review Railway preflight in the `Security` screen
- confirm required platform secrets are present
- confirm the selected version was actually published

If runtime or connector URLs are missing:

- confirm the apply has reached a terminal state
- inspect the release provisioning details
- confirm provisioning mode and plan are valid

If a public API client reports an issue:

- confirm the correct customer consumer is bound to the intended deployment
- check consumer binding history for unexpected rebinding
- verify the external client is using `consumerId` for runtime discovery and not a stale cached deployment URL

- review audit events for `PUBLIC_API_*`
- confirm the client id and external deployment key mapping
- inspect deployment status and latest release

---

## 9) Related Docs

- `Final_Documentation/User_Guides/PLATFORM_OPERATOR_USER_GUIDE.md`
- `changes/Productization/PLATFORM_PUBLIC_PROVISIONING_API_CONTRACT.md`
- `changes/Productization/CONFIGURABLE_AI_ENABLEMENT_PLATFORM_PLAN.md`
