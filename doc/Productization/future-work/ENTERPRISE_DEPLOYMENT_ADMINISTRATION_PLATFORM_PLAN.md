# Enterprise Deployment Administration Platform Plan

Status: planning document (2026-03-30)

This document describes how to evolve the current platform from a feature-complete deployment tool into an enterprise-grade control plane for AI deployments.

It focuses on:

- deployment lifecycle administration
- unified operator UI
- user, role, and assignment management
- bulk and destructive operations with guardrails
- auditability, governance, and tenancy boundaries

---

## 1) Executive Summary

The current platform already supports:

- creating and editing deployment drafts
- publishing versions
- applying deployments
- tracking releases
- diagnostics, verification, and logs

The next product step is not only “add more pages”.

The next step is to turn the product into a real administration surface where an operator can manage:

- many deployments
- many users
- many environments
- many target profiles
- many operational events

from one coherent, deployment-centric workspace.

The key UI shift should be:

- not page-oriented
- but deployment-oriented

Meaning:

- the selected deployment becomes the main working context
- related configuration, releases, diagnostics, access, and activity all live under that context
- global grids and admin views sit above that context

---

## 2) Product Goal

The platform should support enterprise-style administration for:

- internal operator teams
- customer success / implementation teams
- customer tenant admins
- security / audit users
- deployment-scoped contributors

Target outcomes:

- one clear deployments grid
- one consistent deployment workspace
- clear privileges and assignments
- safe destructive operations
- strong audit trail
- clean separation between platform admin scope and deployment scope

---

## 3) Current Gaps

### 3.1 UI fragmentation

Today, the product is still largely page-oriented:

- Deployments
- Revisions
- Diagnostics
- Security
- Actions
- Knowledge

That works during buildout, but it becomes inefficient when operators manage many deployments.

Problems:

- context switching between pages is high
- deployment identity is not always the persistent top-level context
- operational workflows feel split across screens

### 3.2 Missing enterprise administration surface

Current gaps include:

- no full user-management center
- no deployment-assignment matrix
- no clear distinction between platform admin and deployment admin
- limited bulk administration
- destructive actions are not modeled as deliberate operations with approvals/retention

### 3.3 Missing lifecycle administration features

We need explicit support for:

- archive deployment
- delete deployment
- delete all releases for a deployment
- delete all deployments for an environment or tenant
- provider-side project cleanup
- stale-resource cleanup

These should be modeled as operational jobs, not just simple buttons.

---

## 4) Product Model

### 4.1 Core administrative entities

Introduce or strengthen these entities:

- `PlatformUser`
- `PlatformRole`
- `Team`
- `Deployment`
- `DeploymentAssignment`
- `DeploymentTemplate`
- `TargetProfile`
- `Release`
- `OperationalJob`
- `AuditEvent`

### 4.2 Scope levels

Model privileges at three layers:

- platform scope
- team / tenant scope
- deployment scope

### 4.3 Recommended role model

Platform roles:

- `PLATFORM_OWNER`
- `PLATFORM_ADMIN`
- `PLATFORM_OPERATOR`
- `PLATFORM_AUDITOR`
- `PLATFORM_VIEWER`

Deployment roles:

- `DEPLOYMENT_ADMIN`
- `DEPLOYMENT_EDITOR`
- `DEPLOYMENT_OPERATOR`
- `DEPLOYMENT_VIEWER`

Template roles:

- `TEMPLATE_ADMIN`
- `TEMPLATE_EDITOR`
- `TEMPLATE_VIEWER`

### 4.4 Assignment model

Users should be assignable to:

- all deployments
- deployments in a team / environment
- explicit deployment set

This enables:

- customer-specific access
- environment-specific operator groups
- implementation partners with bounded scope

---

## 5) Unified UI Direction

### 5.1 Main navigation direction

Recommended top-level information architecture:

- `Home`
- `Deployments`
- `Templates`
- `Operations`
- `Access`
- `Audit`
- `Settings`

### 5.2 Deployments grid as primary entry

The deployments grid should become the main operational home.

Columns should include:

- deployment name
- environment
- template
- target profile
- status
- latest release status
- last published version
- last applied timestamp
- runtime URL
- connector URL
- owner / assigned team
- verification state

Filtering should support:

- status
- environment
- template
- target profile
- assigned user / team
- release health
- search by name or id

Bulk actions should support:

- assign users
- archive
- apply published version
- re-run verification
- export metadata
- delete with confirmation workflow

### 5.3 Deployment workspace

After selecting a deployment, the user should stay in one workspace with a persistent deployment header.

Recommended workspace sections:

- `Overview`
- `Configuration`
- `Versions`
- `Diagnostics`
- `Access`
- `Activity`

Recommended persistent top bar:

- deployment name
- environment
- template
- target profile
- draft state
- published version
- latest release state
- quick actions:
  - save draft
  - publish
  - apply
  - verify
  - archive

### 5.4 Configuration inside one workspace

Instead of separate major pages, configuration should live inside one workspace with sections:

- actions
- entities
- routing
- providers
- security
- CORS
- deployment source

This keeps the deployment context stable.

---

## 6) Destructive Operations and Lifecycle Administration

### 6.1 Administrative operations to add

Introduce explicit operations:

- `ArchiveDeployment`
- `RestoreDeployment`
- `DeleteDeployment`
- `DeleteDeploymentAndProviderResources`
- `DeleteAllDeploymentsInScope`
- `DeleteReleaseHistory`
- `CleanupStaleProviderProjects`

### 6.2 Guardrails

Destructive operations should require:

- role check
- typed confirmation
- optional second approver for bulk delete
- audit event
- preview of affected resources

### 6.3 Recommended delete semantics

Preferred order:

1. archive
2. soft-delete
3. retention window
4. hard delete

This avoids immediate irreversible loss.

### 6.4 Provider cleanup behavior

Deletion should distinguish:

- delete platform record only
- delete platform record and provider resources
- detach provider resources from platform management

This matters when external services still contain customer data or are shared.

---

## 7) Access and Privilege Administration

### 7.1 User administration center

Add a dedicated access administration UI for:

- creating users
- disabling users
- resetting access methods
- assigning global roles
- viewing deployment assignments

### 7.2 Deployment assignment matrix

Add a matrix/table view:

- rows: users or teams
- columns: deployments or environments
- cells: effective role

This becomes the clearest enterprise admin view for “who can touch what”.

### 7.3 Effective permission inspection

For each user and deployment, the platform should show:

- direct role
- inherited role
- reason / assignment path
- actions allowed

This is critical for supportability.

### 7.4 Future identity integrations

Future-compatible hooks should exist for:

- SSO / SAML / OIDC
- SCIM provisioning
- group-to-role mapping
- just-in-time access

---

## 8) Enterprise Operations Features

### 8.1 Audit

Every important operation should create an audit event:

- login
- secret change
- draft save
- publish
- apply
- delete
- assignment change
- provider cleanup

### 8.2 Operational jobs

Long-running admin actions should be modeled as jobs:

- bulk apply
- bulk verify
- delete all
- provider cleanup
- migration run

Each job should have:

- id
- actor
- target scope
- progress
- result
- logs

### 8.3 Approval workflow

Enterprise mode should later support:

- approval required for production apply
- approval required for bulk delete
- approval required for secret rotation on protected deployments

### 8.4 Drift detection

The platform should detect and surface drift:

- deployment config drift
- secret drift
- provider service drift
- disconnected services

---

## 9) Backend Changes

### 9.1 Domain model additions

Recommended additions:

- deployment archive / deletion state
- deployment assignment entity
- team entity
- operational job entity
- approval entity

### 9.2 API additions

Add APIs for:

- user list / create / disable
- team management
- deployment assignment management
- bulk administrative actions
- archive / restore / delete
- job status
- effective permission inspection

### 9.3 Policy layer

Add a central authorization policy layer so UI and API behavior stay aligned.

The policy layer should answer:

- can this user view this deployment
- can this user publish this deployment
- can this user delete this deployment
- can this user manage assignments

### 9.4 Audit/event model

Administrative actions should write structured audit events with:

- actor
- target
- action
- prior state
- new state
- request id

---

## 10) Frontend Changes

### 10.1 Layout

Move from page navigation to:

- persistent left navigation
- top deployment context selector
- tabbed / sectional deployment workspace

### 10.2 Unified deployment shell

Create a reusable deployment shell component that owns:

- selected deployment
- selected version / release context
- draft dirty state
- quick actions
- breadcrumbs

### 10.3 Grid-first workflow

The deployments grid should support:

- multi-select
- saved filters
- compact and detailed modes
- row expansion for release status and links

### 10.4 Access screens

Add:

- users grid
- teams grid
- assignments matrix
- effective access detail drawer

---

## 11) Recommended Delivery Phases

### Phase 1

- deployments grid improvements
- persistent deployment header
- deployment workspace shell
- archive / restore
- basic user list
- deployment assignment basics

### Phase 2

- bulk actions
- operational jobs
- access matrix
- effective permission inspection
- soft delete / retention

### Phase 3

- approval workflows
- team-based administration
- audit console improvements
- drift detection
- production governance mode

### Phase 4

- SSO / SCIM
- advanced enterprise tenancy
- delegated administration
- cross-workspace governance

---

## 12) Recommendation

The most important product decision is:

- make the deployment the persistent context
- and make administration role-aware

That will do more for enterprise readiness than adding more isolated pages.

The shortest high-value sequence is:

1. better deployments grid
2. unified deployment workspace
3. user + assignment administration
4. archive/delete/bulk jobs
5. audit + approval

That sequence turns the product from “configuration tool” into “enterprise AI deployment control plane”.
