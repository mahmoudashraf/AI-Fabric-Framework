# Prompt Management Hot Apply Plan

Status: planning document (2026-03-31)

This document describes how to add prompt management as a first-class platform capability, with a focus on fast testing and hot apply for prompt behavior changes.

The goal is to let customers tune prompts without waiting for a full deployment cycle every time they want to test assistant behavior.

---

## 1) Executive Summary

Prompt management should become its own product area in the platform.

The user experience should be:

1. edit prompts in the platform
2. test changes immediately in a controlled chat/test console
3. hot apply prompt-only changes to a target environment when safe
4. publish stable prompt versions when ready

This should not replace normal versioned releases.

It should add a faster path specifically for prompt iteration and behavior testing.

---

## 2) Product Goal

The platform should support:

- prompt editing
- prompt organization
- prompt versioning
- prompt testing
- prompt rollback
- prompt-only hot apply

Target outcomes:

- customers can tune behavior faster
- implementation teams can validate prompt changes before full publish
- prompt changes are auditable and reversible
- runtime can refresh prompts without full runtime redeploy when allowed

---

## 3) Key Product Decision

Prompt changes should be modeled separately from:

- actions config
- entity config
- routing config
- secrets

Recommended model:

- deployment version continues to own the stable released configuration
- prompt management adds a prompt layer with both:
  - versioned prompt bundles
  - optional hot-applied preview overlays

This preserves release discipline while allowing fast prompt iteration.

---

## 4) Supported Prompt Types

Recommended prompt categories:

- system prompt
- intent extraction prompt
- action selection prompt
- clarification prompt
- answer generation prompt
- retrieval / grounding prompt
- mode-specific prompt overlays
- assistant / chatbot UI prompts

The platform should not treat prompts as one unstructured blob.

They should be grouped, named, and testable by purpose.

---

## 5) Product Model

Recommended entities:

- `PromptBundle`
- `PromptTemplate`
- `PromptOverlay`
- `PromptTestRun`
- `PromptHotApplySession`
- `PromptRevision`

Recommended relationships:

- one deployment can have one or more prompt bundles
- one bundle can have many revisions
- one environment can have one active prompt overlay
- one user can run many prompt tests

---

## 6) Hot Apply Modes

### 6.1 Preview only

Prompt changes apply only inside the platform test console.

Use for:

- individual experimentation
- safe tuning before broader rollout

### 6.2 Session hot apply

Prompt changes apply to a named test session or short-lived test environment.

Use for:

- internal UAT
- implementation review
- customer workshop testing

### 6.3 Environment hot apply

Prompt changes apply to a real deployment environment without full redeploy.

Use for:

- controlled dev/test environments
- advanced customers who need fast tuning

Production environments should support this only behind explicit policy.

---

## 7) Runtime Design Direction

### 7.1 Prompt source layering

Recommended prompt resolution order:

1. base prompt bundle from published deployment version
2. environment override
3. active hot-apply overlay
4. session preview overlay

### 7.2 Runtime refresh model

Recommended runtime capabilities:

- fetch prompt bundle by version
- optionally fetch prompt overlay by environment/session
- cache with revision id
- refresh on signal or short TTL

### 7.3 Safe hot apply boundary

Hot apply should be limited to:

- prompt text
- prompt parameters
- prompt selection / mode overlay rules

It should not silently change:

- action catalog
- security policy
- provider secrets
- deployment source / branch

---

## 8) Platform UX

### 8.1 Prompt management workspace

Add a `Prompts` section inside the deployment workspace.

Recommended sections:

- prompt catalog
- prompt editor
- test console
- revision history
- active hot apply state

### 8.2 Prompt editor

The editor should support:

- prompt name and purpose
- variables / placeholders
- preview of rendered prompt
- diff view
- rollback

### 8.3 Test console

The prompt test console should support:

- sample user queries
- current deployment context
- citations / actions chosen
- compare current vs edited prompt results
- save as revision

### 8.4 Hot apply controls

Show clear modes:

- `Preview only`
- `Hot apply to test session`
- `Hot apply to dev`
- `Publish into next release`

The user should always know whether a change is:

- local preview
- environment live
- versioned and published

---

## 9) Backend Changes

### 9.1 New APIs

Add APIs for:

- list prompt bundles
- get prompt revision
- save prompt draft
- run prompt test
- activate prompt overlay
- deactivate / rollback overlay

### 9.2 Storage model

Store:

- prompt structure in DB
- prompt revisions in DB
- test run summaries in DB
- published prompt artifact per version when needed

### 9.3 Audit

Track:

- who changed prompt text
- who hot-applied it
- scope of hot apply
- rollback events

---

## 10) Safety and Governance

### 10.1 Guardrails

Prompt hot apply should support:

- role checks
- environment restrictions
- expiry window for temporary overlays
- rollback to last stable prompt set

### 10.2 Production policy

Recommended production policy modes:

- `DISABLED`
- `APPROVAL_REQUIRED`
- `ALLOWED_FOR_PROMPTS_ONLY`

### 10.3 Observability

Track prompt behavior metrics:

- prompt revision id in requests
- action selection changes
- clarification rate
- success / fallback rate
- latency and token usage deltas

---

## 11) Recommended Delivery Phases

### Phase 1

- prompt bundle model
- prompt editor
- prompt test console
- revision history

### Phase 2

- preview-only prompt overlays
- runtime prompt refresh support
- prompt diff and compare mode

### Phase 3

- environment hot apply for dev/test
- rollback and overlay expiry
- prompt behavior analytics

### Phase 4

- approval-controlled production hot apply
- reusable prompt packs
- multi-deployment prompt propagation

---

## 12) Recommendation

The right direction is:

- treat prompts as a first-class configurable asset
- separate stable prompt releases from hot-apply overlays
- make the platform the prompt testing and behavior-tuning surface

That gives customers fast behavior iteration without weakening the main deployment release model.
