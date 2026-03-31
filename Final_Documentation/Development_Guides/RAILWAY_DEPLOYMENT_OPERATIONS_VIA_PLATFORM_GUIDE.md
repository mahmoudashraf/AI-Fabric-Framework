# Railway Deployment Operations Via Platform Guide

This guide explains how the current Wave 1, Wave 2, and Wave 3 platform features operate an existing Railway deployment made of:

- platform backend + UI
- runtime service
- REST connector service
- optional upstream/store service

It is written for the current platform-first branch and describes what is already operable now, not future roadmap behavior.

For one consolidated guide to Platform V2 capabilities, security model, deployment lifecycle, and workspace structure, see `PLATFORM_V2_FEATURES_GUIDE.md`.

## 1. Short Answer

Yes, the current Railway runtime and REST connector deployments can be operated through the new platform workspace, with an important distinction:

- most Wave 1, 2, and 3 features are control-plane features in the platform backend/UI
- they become active when the platform backend and UI are deployed from the current branch
- they do not require a new REST connector build for normal deployment operations
- they do not require a new runtime build for normal deployment operations
- the main exception is session-scoped prompt preview in the POC flow, which depends on the runtime prompt preview support added on this branch

So the operational answer is:

- current live runtime/connector deployments can be managed by the new platform
- current live runtime/connector deployments are not automatically changed by these waves unless you apply a new deployment version or run a governed remediation action

## 2. What These Waves Added For Railway Operations

### Wave 1

Wave 1 made the platform deployment-centric:

- one selected deployment context across the workspace
- deployment-scoped overview, access, approvals, activity, diagnostics, and versions
- deployment assignments and role enforcement
- bulk and destructive operations with guardrails and audit

### Wave 2

Wave 2 made iteration and proof-of-concept work faster:

- prompt baseline and release preview views
- prompt state clarity between draft, published, and session-only testing
- guided POC migration/import flow
- deployment-scoped POC chat and readiness staging

### Wave 3

Wave 3 made Railway operations materially stronger:

- explicit draft vs live vs published state
- release impact preview and configuration diff center
- verification gate and post-apply summary
- service-by-service configuration model
- secret usage and literal-credential detection
- auth, upstream, and CORS governance
- source-of-truth and branch/artifact visibility
- diagnostics with release failure analysis and recovery hints
- Railway project links, service links, public URLs, and Swagger links
- governed remediation actions
- production readiness scorecard

## 3. Compatibility Matrix

| Capability | Works with current live runtime/connector? | Extra requirement |
| --- | --- | --- |
| Deployment workspace header, selected deployment context, role-aware navigation | Yes | Platform backend/UI must run this branch |
| Overview state clarity, source-of-truth view, service config model, readiness scorecard | Yes | Deployment must exist in platform and have a saved draft |
| Versions page impact preview and diff center | Yes | At least one published version is needed for meaningful compare |
| Verification gate and post-apply summary | Yes | Active version and verification runs are needed for full value |
| Security page secret usage, auth, upstream, and CORS governance | Yes | Draft config must be managed through platform |
| Diagnostics page release timeline, failure reason, recovery hints, log pivots | Yes | Latest release data must exist in platform |
| Railway project link and service navigation | Partial | Latest release must record Railway project id and service metadata |
| Rerun verification | Yes | Operator role or higher, active version, no release currently running |
| Redeploy active version | Yes | Operator role or higher, active version, optional approval if required by deployment |
| Restart runtime service | Partial | Railway API mode, deployment admin role, completed Railway-managed release with runtime service id |
| Restart REST connector service | Partial | Railway API mode, deployment admin role, completed Railway-managed release with connector service id |
| Reset runtime vectors | Partial | Runtime base URL must exist and `APP_ADMIN_API_KEY` must be present in platform secrets |
| Session-scoped prompt preview and hot-apply in POC | Partial | Runtime must include the prompt preview support from this branch |
| Connector-specific new behavior from Waves 1-3 | No | These waves did not add connector-side feature changes for operations |

## 4. Required Setup

The new platform features operate best when the following are true.

### 4.1 Platform deployment

The platform backend and UI must be deployed from the current platform-first branch. That is what activates the Wave 1 to 3 workspace and APIs.

### 4.2 Deployment record quality

The platform deployment should have:

- a saved draft
- at least one published version
- an active version if the deployment is already live
- runtime and connector base URLs
- latest release history

Without these, the workspace still loads, but some sections will remain informational instead of actionable.

### 4.3 Railway provider prerequisites

Railway-specific actions need the platform to be configured for Railway operations. In practice:

- the platform must know the Railway workspace
- the platform must have a valid Railway API token if provider-side actions are expected
- the latest release must record Railway provisioning details if you want direct project links and service restarts

Provider-side restart actions do not work from draft data alone. They depend on a completed Railway-managed release that stored service ids.

### 4.4 Secret prerequisites

Use platform secrets for credentials. Do not keep live secrets as literal deployment config values.

Common examples:

- `APP_ADMIN_API_KEY`
- `CONNECTOR_API_KEY`
- `ACTIONS_CONNECTOR_API_KEY`
- `OPENAI_API_KEY`

For admin operations, the important distinction is:

- admin APIs should be protected by the shared admin secret path
- connector business ingress should stay protected by connector API key configuration

### 4.5 Runtime and connector auth expectations

Current platform governance assumes:

- runtime and connector admin surfaces are protected by `APP_ADMIN_API_KEY`
- connector ingress for runtime-to-connector and other controlled calls is protected separately
- connector ingress should normally use `X-AIFABRIC-API-KEY`
- admin surfaces should stay on the admin key path, not on public unauthenticated access

## 5. What You Can Operate From The Platform

### 5.1 Deployments page

Use this as the entry point for selecting the deployment you want to operate.

It now supports:

- persistent selected deployment context
- operator-focused filtering
- role-aware quick actions
- deployment health and assignment visibility

Once a deployment is selected, the rest of the workspace stays pinned to that deployment.

### 5.2 Overview page

Use the Overview page as the primary Railway operations summary.

It now gives you:

- current deployment state and health
- saved draft vs live posture
- service-by-service config model
- Railway project link when available
- runtime and connector public URLs
- runtime and connector Swagger links
- upstream/store target visibility
- source-of-truth lineage for template, branch, artifacts, and generated config
- production readiness scorecard

For normal daily operations, this is the first page to check before making changes.

### 5.3 Versions page

Use Versions when you need to understand what an apply will change.

It now provides:

- saved draft versus published comparison
- release impact preview
- config diff center across draft, published, live, and source/template inputs

This is the main safety page before applying a new version to Railway.

### 5.4 Security page

Use Security before production apply and during security review.

It now validates:

- missing required secrets
- literal credential risks in deployment config
- runtime and connector admin protection
- connector ingress auth posture
- upstream URL and upstream auth posture
- delegated authz URL posture
- CORS allowlist breadth and credential safety

This page is intentionally opinionated. If it shows blocked items, treat them as pre-production work, not as optional warnings.

### 5.5 Verification page

Use Verification to decide whether a release is ready to trust.

It now shows:

- required verification checks
- pre-apply readiness
- post-apply grouped verification state
- service-by-service outcomes after rollout

This page is the release-confidence page. It should be reviewed after every apply.

### 5.6 Diagnostics page

Use Diagnostics when rollout or live health needs investigation.

It now shows:

- release timeline
- failed step visibility
- extracted failure reason
- known recovery hints
- direct log pivots
- governed remediation actions

This is the page to use first when Railway shows a failed rollout or the deployment becomes unhealthy.

### 5.7 Access, Approvals, Activity, and Users

These pages complete the enterprise operations model.

Use them for:

- assigning deployment admins, operators, editors, and viewers
- requiring approval for guarded actions where needed
- reviewing deployment activity and audit history
- managing platform users separately from deployment-specific assignment

Production readiness will stay weak or blocked if operational ownership is missing.

### 5.8 Prompts and POC

These pages are primarily for safe operator iteration and demo validation.

They support:

- prompt draft vs published comparison
- release preview of prompt changes
- session-scoped prompt testing
- POC dataset import and bounded validation loops

Important limitation:

- prompt preview and hot-apply in the POC flow require the newer runtime support from this branch
- if the runtime is still on an older build, the rest of the platform workspace still works, but that specific preview loop may not

## 6. Standard Railway Operator Flows

### 6.1 Review a deployment before applying changes

1. Open the deployment in `Deployments`.
2. Review `Overview` for health, role, live URLs, source-of-truth, and readiness scorecard.
3. Review `Security` for blocked findings, missing secrets, auth posture, upstream issues, and CORS issues.
4. Open `Versions` to inspect impact preview and diff center.
5. If the draft is acceptable, publish the version.
6. Apply the published version.
7. Track rollout in `Verification` and `Diagnostics`.

### 6.2 Recover a failed Railway rollout

1. Open `Diagnostics`.
2. Read the current step, failure reason, and recovery hint.
3. Use the Railway project link or service links from `Overview` if provider-side inspection is needed.
4. Choose the smallest governed remediation that fits the failure.
5. Re-run verification after recovery.
6. Review `Activity` to confirm the remediation and resulting release history.

### 6.3 Inspect live surfaces and provider navigation

1. Open `Overview`.
2. Use the Railway project link if present.
3. Use the runtime public URL and runtime Swagger link.
4. Use the REST connector public URL and connector Swagger link.
5. Review the relationship map to understand how browser, runtime, connector, artifacts, and upstream are connected.

### 6.4 Prepare for a customer demo or production handoff

1. Confirm `Overview` readiness scorecard is not blocked.
2. Confirm `Verification` shows a trustworthy latest run.
3. Confirm `Security` has no blocked findings.
4. Confirm `Access` has at least one admin and one operator assigned.
5. Use `POC` only for final validation, not as the sole proof that production posture is safe.

## 7. Governed Remediation Actions

The current workspace supports governed remediation instead of raw infrastructure access from the UI.

Available actions depend on deployment state, role, and provider metadata.

The current set includes:

- rerun verification
- redeploy active version
- restart runtime service
- restart REST connector service
- reset runtime vectors
- archive deployment
- restore deployment
- delete deployment

These actions are intentionally bounded:

- role checks are enforced
- confirmations are required where appropriate
- approval ids can be attached when the deployment requires approval
- audit events are written for execution

## 8. Save Draft vs Publish vs Apply vs Reverify vs Redeploy

These states matter for Railway operations.

### Save draft

Saving a draft stores configuration changes in the platform. It does not change Railway.

### Publish version

Publishing turns the current draft into an immutable version and artifact set. It still does not change Railway.

### Apply version

Apply is the operation that rolls the selected published version out to Railway-managed services.

### Rerun verification

Rerun verification does not change deployment configuration. It re-checks the currently live deployment.

### Redeploy active version

Redeploy triggers a fresh rollout of the currently active published version. It is useful when live services need a new deployment cycle without changing draft content.

## 9. When A Feature Will Look Missing

If a feature is not visible or is disabled, the reason is usually one of these:

- the platform backend/UI are not yet deployed from the current branch
- the deployment has no active version
- the deployment has no published version
- the deployment has no latest release record
- runtime or connector base URLs are missing
- Railway project id or service ids were never recorded in release provisioning details
- the deployment role is too weak for the requested action
- `APP_ADMIN_API_KEY` is missing from platform secrets
- Railway API mode is not active for provider-side restart actions

This is expected behavior, not random UI drift. The workspace is intentionally state-aware and role-aware.

## 10. What The Platform Does Not Do

The platform can operate deployments, but it does not replace source-code delivery.

It does not:

- patch runtime or connector source code directly
- make new connector code features appear without deploying new connector code
- make runtime-only features appear without deploying new runtime code
- expose raw provider secrets to operators in normal config editing flows
- turn every Railway deployment into a fully provider-managed target if Railway metadata was never recorded for it

The platform is the control plane. It is not a source-code deployment replacement by itself.

## 11. Recommended Production Posture

For production Railway deployments operated through this platform:

- keep admin APIs protected with `APP_ADMIN_API_KEY`
- keep connector ingress authenticated
- store credentials in platform secrets, not literal draft values
- keep CORS origin lists narrow and environment-appropriate
- require clear deployment ownership
- use approvals for production apply where the deployment risk justifies it
- treat blocked security or readiness findings as release blockers
- use `Versions`, `Verification`, and `Diagnostics` together before and after every production apply

## 12. Practical Bottom Line

If the platform backend and UI are deployed from this branch, the current runtime and REST connector deployments can already be operated through the new Wave 1 to 3 platform workspace.

For most operations, no new connector deployment is required.

For most operations, no new runtime deployment is required.

The main runtime-specific exception is operator prompt preview in the POC flow.

Everything else in these waves is primarily about making the Railway deployment easier to understand, safer to change, and faster to recover from inside one governed platform workspace.
