# Platform Execution Sequence and First Wave Plan

Status: execution-sequencing document (2026-03-31)

This document turns the current future-work plans into a concrete implementation sequence.

It uses the current product north star:

- configurable AI assistants that understand customer data
- grounded in live knowledge and live API/action results
- easy to configure and integrate into external systems
- safe to deploy and operate in enterprise environments

---

## 1) Sequencing Principles

The sequence should follow these rules:

- strengthen the deployment control plane before adding more advanced assistant behavior knobs
- improve time-to-value before expanding into large platform surfaces
- only add new capability layers when they fit the grounded-assistant product goal
- use Shopify and similar verticals as validation pressure, not as unrelated side work

---

## 2) Recommended Execution Sequence

### Wave 1: Deployment control-plane foundation

Primary plan:

- `ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md`

Goal:

- move from page-oriented platform UX to deployment-oriented workspace UX

Ordered scope inside this wave:

1. unified deployment workspace shell
2. deployments grid improvements and bulk operations foundation
3. access and assignment model foundation
4. destructive operations with guardrails and audit

### Wave 2: Faster iteration and proof-of-concept flow

Primary plans:

- `PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md`
- `DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md`

Goal:

- shorten the time between configuration and customer-visible proof

### Wave 3: Runtime answer quality and business behavior

Primary plans:

- `RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md`
- `CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md`

Goal:

- improve assistant correctness, usefulness, and business flow handling

### Wave 4: Larger enterprise expansion

Primary plans:

- `DATA_MIGRATION_PLATFORM_PLAN.md`
- `PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`
- `REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md`
- `MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md`

Goal:

- expand enterprise reach only after the core platform shell and assistant quality are stronger

---

## 3) First Wave Item To Build Now

The first implementation item should be:

- **Unified deployment workspace foundation**

This is deliberately narrower than the whole enterprise administration plan.

It includes:

- one persistent selected deployment context
- one deployment workspace header shared across deployment-scoped pages
- one backend workspace summary API for that header
- one deployment-scoped section navigation model
- removal of per-page deployment selector drift

It does not yet include:

- full user-management center
- deployment assignments
- delete/delete-all operations
- approval workflows
- multi-tenant access matrix

Those stay as later steps inside Wave 1.

---

## 4) Why This Is The First Item

This item should come first because it:

- directly fixes the biggest current UX fragmentation
- makes the platform feel deployment-centric instead of screen-centric
- creates the shell where prompt management, diagnostics, access, and later assistant operations can live
- reduces repeated selection logic in the current frontend
- gives a clean place to add quick actions and deployment-specific administration later

---

## 5) Completion Criteria For This First Item

The first item is complete when:

- deployment-scoped pages use one shared deployment context
- the selected deployment is visible at the top of the UI while moving across sections
- section navigation preserves the current deployment automatically
- backend exposes a dedicated workspace summary payload
- frontend no longer owns separate deployment-selection state per page
- backend tests and frontend build pass

---

## 6) Immediate Follow-up After This Item

After the workspace foundation is complete, the next items should be:

1. deployments grid enhancements and bulk operations shell
2. prompt management with hot apply
3. embedded POC chatbot and test-data onboarding

This keeps the roadmap focused on:

- stronger operator workflow
- faster assistant tuning
- faster customer proof-of-concept success

