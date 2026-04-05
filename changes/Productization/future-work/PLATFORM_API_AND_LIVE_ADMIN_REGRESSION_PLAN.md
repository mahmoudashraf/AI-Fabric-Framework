# Platform API And Live Admin Regression Plan

## Goal

Create a regression system that protects the platform against feature loss and behavioral drift while the product keeps expanding.

This plan is intentionally not tied to Wave sequencing. It is an operating-quality plan that should stay active as the platform evolves.

The regression focus is:

- real platform API behavior
- real product execution behavior
- real platform-admin verification against live Railway deployments

This plan is **not** centered on UI automation. The current UI should still build cleanly, but the primary regression investment should be in backend, product, and live admin/API verification.

## Why This Matters

The platform now spans:

- deployment lifecycle and approvals
- tenant/customer boundaries
- managed vector infrastructure
- vectorization and runner orchestration
- hosted verification
- canonical rollout administration
- destructive cleanup and deletion

At this size, targeted unit tests are not enough. We need a layered regression system that catches:

- API contract regressions
- security regressions
- lifecycle/state-machine regressions
- orchestration regressions between platform, runner, runtime, and Railway
- provider and tenant isolation regressions

## Regression Layers

### Layer 1: Code Regression

Runs on GitHub Actions for pull requests and branch pushes.

Purpose:

- catch backend and product regressions before merge
- keep feedback fast enough for daily development

Scope:

- platform backend integration and service tests
- `ai-fabric-product` tests
- targeted `ai-infrastructure-module` tests used by the platform product path
- shell syntax checks for hosted verification scripts
- platform UI build only

### Layer 2: Live Admin/API Regression

Runs on GitHub Actions manually and on a schedule against live Railway deployments.

Purpose:

- verify the actual deployed platform using real admin authentication
- verify real hosted verification and vectorization flows
- prove tenant isolation and provider integration on live systems

Scope:

- real platform admin login or admin API key
- live deployment verification workflows
- write-backed verification where safe and intentionally configured
- tenant-shared isolation smoke
- managed provider verification

### Layer 3: Release Readiness Regression

Runs before release or major rollout decisions.

Purpose:

- make sure the deployment set you rely on for production validation still behaves correctly

Scope:

- canonical rollout deployments
- ecommerce vectorization deployment
- vector provider verification deployments
- tenant-shared isolation deployment pair

## Required Feature Coverage

Every major platform capability should have all of the following:

1. local backend or product regression coverage
2. live admin/API verification path
3. named owner workflow or script that can be rerun later

The must-not-break feature list is:

1. deployment create, publish, apply, archive, restore, delete
2. approval-gated operations
3. customer and tenant binding
4. tenant-scoped shared vector infrastructure
5. managed vector provider lifecycle
6. vectorization source connection, discovery, sample run, reindex
7. vectorization runner registration and compatibility
8. hosted verification and admin-triggered verification
9. canonical rollout create and cleanup
10. admin security, user access, and assignment visibility
11. asynchronous deletion, cleanup completion, and failure notification

## Required Work Items

### Item 1: Dedicated Regression Plan And Ownership

- create a standalone regression plan and keep it current
- define the required deployment ids, counterpart pairs, and secret expectations
- define which workflow is authoritative for which verification surface

Completion criteria:

- this plan exists
- the workflow inventory is explicit
- the required live deployment set is named

### Item 2: Platform Code Regression Workflow

Create a dedicated GitHub Actions workflow for code regression.

Required behavior:

- runs on `pull_request`
- runs on pushes to key product branches
- runs platform backend tests
- runs `ai-fabric-product` tests
- runs targeted `ai-infrastructure-module` tests used by the platform product path
- runs platform UI build
- validates hosted verification script syntax

Completion criteria:

- one workflow exists for platform/product regression
- it can be required in branch protection
- it is not limited to manual execution

### Item 3: Live Admin/API Regression Workflow

Create a dedicated live regression workflow that uses real platform-admin authentication and live Railway deployments.

Required behavior:

- supports real admin login via `PLATFORM_LOGIN_EMAIL` and `PLATFORM_LOGIN_PASSWORD`
- can also use `PLATFORM_API_KEY` when enabled
- executes live hosted verification using current scripts
- supports safe write-backed verification mode
- supports tenant counterpart deployment ids for isolation smoke
- can be run manually
- can be scheduled

Completion criteria:

- live admin/API regression workflow exists
- it covers ecommerce vectorization, vector deployments, tenant isolation, and provider verification

### Item 4: Canonical Regression Inventory

Make the live regression suite explicitly target canonical deployment surfaces.

Required behavior:

- named ecommerce deployment
- named vector verification deployments
- named tenant-shared deployment pair
- named provider verification suite

Completion criteria:

- the workflow inputs and defaults clearly describe the regression fleet
- the live workflow can verify the complete fleet without ad hoc editing

### Item 5: Failure Visibility And Operator Reuse

Make failures easy to understand and easy to rerun.

Required behavior:

- workflow names and job names clearly identify the deployment and regression type
- verification scripts remain reusable by admins later
- failure output points to the correct deployment or provider surface

Completion criteria:

- a platform admin can rerun the same regression workflows later without reverse engineering them

### Item 6: Regression Operating Guide

Document how to use the regression system.

Required sections:

- what runs on PR
- what runs live
- which secrets are required
- how to trigger the live suite
- what to do when a job fails
- which suites are expected before release

Completion criteria:

- one guide exists in `Final_Documentation`
- it references the actual workflows and scripts

## Recommended Workflow Split

### Code Regression Workflow

Suggested name:

- `platform-code-regression.yml`

Purpose:

- fast and merge-gating

### Live Admin/API Regression Workflow

Suggested name:

- `platform-admin-live-regression.yml`

Purpose:

- live Railway verification using real admin access

### Existing Supporting Workflows

These remain useful and should be reused rather than replaced:

- `deployment-verification.yml`
- `managed-vector-provider-verification.yml`
- `platform-state-verification-suite.yml`

The new workflows should orchestrate and specialize them, not duplicate their core logic.

## Security Expectations

- live workflows must use GitHub Secrets, not plaintext credentials
- admin login credentials and API keys must always be masked
- write-backed verification must stay bounded and intentionally scoped
- tenant isolation checks must only use deployments intended for that purpose
- provider verification must continue to clean up ephemeral resources where supported

## Non-Goals

- large frontend UI test suites
- screenshot-based UI regression
- browser automation as the primary regression system
- replacing backend integration or hosted verification with UI-only tests

## Definition Of Done

This plan is complete when:

1. code regression runs automatically on PRs and pushes
2. live admin/API regression can be run manually and on a schedule
3. canonical deployment and tenant-isolation verification are part of the live suite
4. the operating guide exists
5. the workflows are reliable enough to become part of release readiness
