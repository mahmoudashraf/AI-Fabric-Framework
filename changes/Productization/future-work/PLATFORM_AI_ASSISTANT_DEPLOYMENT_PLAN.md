# Platform AI Assistant Deployment Plan

Status: planning document (2026-03-30)

Execution note (2026-04-06):

- the concrete Wave 4 Track C execution baseline now lives in `PLATFORM_ASSISTANT_TRACK_C_EXECUTION_PLAN.md`
- that execution plan locks the first implementation around a platform-owned assistant deployment, a new `support` curated module, platform-API-backed assistant actions, and a shell-level floating widget mounted once from `AppShell`
- where this broader planning document and the Track C execution plan differ on first-release product shape, the Track C execution plan should win

This document describes how the platform should use its own deployment model to create and operate an AI assistant for the platform itself.

The goal is to let the product become both:

- the control plane for customer AI deployments
- and a deployment source for its own platform assistant experience

---

## 1) Executive Summary

The platform should be able to create a dedicated AI assistant deployment that helps users:

- understand the platform
- search platform documentation
- inspect deployments
- explain failures
- suggest fixes
- safely execute approved platform actions

This should not be a hardcoded chatbot bolted onto the UI.

It should be:

- a real platform-managed deployment
- backed by platform-managed configuration
- powered by the same runtime / action / security model

This is important for dogfooding and for proving the product’s core value.

---

## 2) Product Goal

The platform assistant should help users with:

- navigation
- troubleshooting
- documentation retrieval
- deployment understanding
- administrative assistance

The first productized form should be explicitly:

- an operator-facing assistant surface inside the platform
- not an end-customer chatbot
- not a hardcoded unmanaged support widget detached from the deployment model

Examples:

- “Why did my latest release fail?”
- “Show me deployments assigned to me.”
- “What secret change requires re-apply only?”
- “Explain this runtime verification failure.”
- “Find the deployment that points to the old branch.”

Later, with approvals:

- “Publish this draft.”
- “Apply version v8 to dev.”
- “Assign John as deployment viewer.”

---

## 3) Why This Matters

This has value in three ways:

### 3.1 Product dogfooding

The platform proves its own deployment, retrieval, and action model against a real internal use case.

### 3.2 Better UX

A complex enterprise control plane benefits from conversational guidance.

### 3.3 Future product surface

This can later become:

- an operator copilot
- a tenant support assistant
- an embedded assistant in customer-admin flows

---

## 4) Product Model

The platform assistant should be modeled as:

- a special deployment template
- with curated sources
- curated actions
- stricter permissions

Recommended template:

- `Platform Assistant`

Recommended deployment types:

- `platform-assistant-dev`
- `platform-assistant-prod`

---

## 5) Assistant Capabilities

### 5.1 Retrieval sources

The assistant should retrieve from:

- platform user guides
- productization plans and runbooks
- deployment metadata
- release history
- verification results
- diagnostics summaries
- audit summaries

### 5.2 Action capabilities

The assistant should call bounded actions such as:

- list deployments
- fetch deployment summary
- fetch latest release details
- fetch diagnostics / logs summary
- fetch effective permissions
- fetch templates
- fetch secret metadata

Later, with approvals:

- save draft
- publish version
- apply version
- archive deployment
- assign user to deployment

### 5.3 Recommended action modes

Split actions into:

- read-only assistant actions
- approval-required administrative actions

---

## 6) New UI Direction

### 6.1 Dedicated assistant UI

The platform should have a first-class assistant UI, not only a floating widget.

Recommended UI sections:

- `Assistant`
- `Conversations`
- `Sources`
- `Actions`
- `Policies`

These should be treated as required product surfaces for the first real assistant pass, not as optional UX polish.

### 6.2 In-context assistant panel

Within deployment pages/workspaces, show an assistant side panel scoped to the current deployment.

Scoped prompts:

- explain latest failure
- summarize draft changes
- show required next step
- compare current draft vs published version

### 6.3 Response structure

Assistant responses should support:

- citations
- related deployments
- proposed next actions
- approval cards
- action result summaries

---

## 7) Source Configuration Model

The platform should let admins configure what the assistant can see.

Recommended source types:

- documents
- deployment metadata
- release records
- diagnostics
- logs summaries
- templates
- audit records

Recommended source scope controls:

- all platform sources
- team sources
- assigned deployment sources
- explicit deployment set

---

## 8) Execution and Security Model

### 8.1 The assistant must respect platform authorization

The assistant should never see or execute beyond the user’s effective permissions.

Meaning:

- retrieval scope is filtered by user access
- actions are filtered by user access
- assistant answers should explain permission denial cleanly

### 8.2 Approval model

Administrative assistant actions should support:

- preview
- confirm
- execute
- audit

### 8.3 Secret handling

The assistant should never expose secret values.

It may only reference:

- secret presence
- last updated metadata
- whether a change requires apply

---

## 9) Platform Backend Changes

### 9.1 New assistant template and bootstrap path

The backend should support creating a platform assistant deployment template with sane defaults for:

- actions
- sources
- security
- retrieval

### 9.2 Assistant source providers

Add source-provider abstractions for:

- guides/docs
- deployment repository
- release/verification repository
- diagnostics/log summaries
- audit events

### 9.3 Assistant action layer

Add a bounded action layer that maps assistant actions to platform APIs with:

- authorization checks
- audit logging
- response normalization

---

## 10) Frontend Changes

### 10.1 New assistant experience

Add:

- assistant landing page
- deployment-scoped assistant panel
- action approval cards
- citation/source drawer

### 10.2 Admin configuration UI

Add admin controls for:

- assistant deployment selection
- source scope selection
- enabled action set
- approval policy
- assistant branding / prompt policy

### 10.3 Conversation memory strategy

Recommended memory layers:

- short-term conversation state
- deployment context memory
- user-specific recent actions

Do not mix this with unrestricted permanent memory.

---

## 11) Suggested Assistant Deployment Design

### 11.1 Runtime

Use the normal platform-managed runtime deployment as the assistant brain.

### 11.2 Sources

Sources should include:

- docs corpus
- deployment and release metadata connectors
- diagnostics summaries

### 11.3 Actions

Actions should call platform backend APIs through bounded assistant-specific action definitions.

### 11.4 Security

Use the same platform user identity and session.

The assistant should operate as:

- the current authenticated user
- never as a hidden super-admin
- never beyond the user's effective permissions
- never exposing raw secret values

---

## 12) Enterprise Extensions

Later enterprise features:

- team-specific assistants
- tenant-specific assistants
- approval routing to human admins
- per-environment assistant policies
- audit and analytics for assistant actions

---

## 13) Recommended Delivery Phases

### Phase 1

- assistant deployment template
- read-only platform assistant
- docs + deployment metadata retrieval
- deployment-scoped assistant panel

### Phase 2

- diagnostics and release explanation
- citations and source drawer
- user-scoped access filtering
- read-only operational actions

### Phase 3

- approval-required administrative actions
- assignment-aware assistant behavior
- conversation history and saved threads

### Phase 4

- advanced operator copilot features
- proactive recommendations
- multi-deployment analysis
- tenant / team assistants

---

## 14) Recommendation

The right strategic move is:

- build the platform assistant as a real deployment on the platform
- give it curated sources and bounded actions
- keep it permission-aware and approval-driven

That makes the platform both more usable and more credible as an enterprise AI deployment product.
