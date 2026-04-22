# Shopify Companion Shell Mode Enablement Plan

Status: proposed implementation plan linked to builder-mode roadmap (2026-04-22)

This document defines how Shopify Companion should gain real storefront conversation-mode support without turning the Shopify shell into an ungoverned client-side switchboard.

It exists because the current code supports:

- shell variant selection: `legacy` vs `max-mode`
- a hardcoded Max Mode starter prompt posture of `mode: navigator`
- marketplace shell metadata such as `defaultConversationMode`

but it does **not** yet support real Shopify shell conversation modes such as:

- `shopify-companion`
- `guided-commerce`
- `guided-support`
- future advanced profiles such as `assistant` or `deep`

---

## 1) Code-Validated Current State

What is real today:

- the Shopify marketplace template already publishes `defaultConversationMode: "shopify-companion"` in the canonical marketplace bundle
- generic marketplace draft compilation already carries `shellConfig.defaultConversationMode` into compiled drafts and versions
- the Shopify storefront shell currently hardcodes starter prompts to `mode: "navigator"`
- the Shopify storefront bootstrap payload does not expose any conversation-mode field
- Shopify widget settings only support launcher label and welcome message
- Shopify storefront chat forwards shopper context, but not any explicit shell mode

What that means:

- Shopify does **not** currently support real storefront conversation modes as a product capability
- the deployment-side shell mode contract exists conceptually, but Shopify does not consume it
- the current shell behavior is effectively one hardcoded shopper posture

---

## 2) Product Decision

We should add Shopify shell mode support, but in a bounded order.

The rule is:

- support existing platform-backed shell conversation modes first
- do not invent merchant-visible `assistant` or `deep` modes before runtime semantics, pricing, and verification exist

Near-term canonical mode set:

- `shopify-companion`
  - default read-first Shopify shopper guidance posture
- `guided-commerce`
  - commerce-optimized posture when the storefront surface set and action posture justify it
- `guided-support`
  - support-oriented posture for Shopify support expansion, not the default shopping mode

Later, only if productized intentionally:

- `assistant`
  - possible more open-ended conversational posture
- `deep`
  - possible advanced reasoning / deeper guided investigation posture

Builder-mode rule:

- `assistant` and `deep` are not launch blockers
- they should not delay Milestones 1-4

---

## 3) Design Principles

### 3.1 Server-owned truth

Mode must be resolved by the platform and bridge, not by storefront JavaScript alone.

Do:

- derive the effective mode from deployment shell config, store policy, entitlement, and bounded override rules

Do not:

- trust an arbitrary query param, theme setting, or browser payload to set the active mode

### 3.2 Allowlist only

The Shopify shell must only accept known modes from a server-owned allowlist.

Unknown mode behavior:

- reject for admin mutation APIs
- fall back safely for shopper-facing bootstrap/runtime use

### 3.3 Product gating must be real

Mode availability must respect:

- shipped storefront surfaces
- current tier entitlements
- current store readiness
- current deployment shell capabilities

### 3.4 Auditable operations

Platform admins must be able to inspect:

- configured default mode
- effective active mode
- why a requested mode was downgraded or blocked
- who changed the default or override

---

## 4) Implementation Scope

### Phase 1: Contract Plumbing

Roadmap link:

- Milestone 2 — Embedded Intelligence Base

Must add:

- platform/bridge summary field for `defaultConversationMode`
- storefront bootstrap field for:
  - `defaultConversationMode`
  - `effectiveConversationMode`
  - `allowedConversationModes`
- Shopify shell consumption of bootstrap-provided mode
- request propagation from Shopify shell to bridge chat/suggestions
- safe fallback to `shopify-companion` when the configured mode is unsupported

Allowed modes in this phase:

- `shopify-companion`
- `guided-commerce`
- `guided-support`

Not included in this phase:

- public merchant editing of advanced modes
- `assistant`
- `deep`

### Phase 2: Admin and Merchant Visibility

Roadmap link:

- Milestone 3 — Starter Completion
- Milestone 4 — Launch-Ready Loom Companion

Must add:

- platform admin visibility of shell mode state on Shopify store pages
- merchant visibility of current mode and why it is active
- bounded admin override / preview path with audit logging
- entitlement-aware gating so the UI never promises a mode the store cannot actually use

Must not add:

- unbounded free-form mode text fields
- raw runtime prompt/profile editing from Shopify admin

### Phase 3: Productized Advanced Profiles

Roadmap link:

- Milestone 5 and later only

Only after the above is stable:

- define whether `assistant` is a real product mode or only a label
- define whether `deep` is:
  - an internal evaluation profile
  - a bounded Elite feature
  - or a later Deep Resolver-backed posture

This phase requires:

- explicit runtime semantics
- pricing/tier alignment
- verification coverage
- support/runbook coverage

Until then:

- `assistant` and `deep` should remain planned concepts, not exposed merchant switches

---

## 5) Security and Production Rules

Non-negotiable rules:

- mode must never be accepted as a raw shopper-controlled authority field
- shopper payload may request a mode, but server logic must validate and downgrade or reject it
- the bootstrap response must only expose bounded, non-secret mode metadata
- admin overrides must be audited
- merchant controls must be bounded to entitled, allowlisted values
- debug-only or operator-only modes must never leak into shopper-facing surfaces

Recommended implementation shape:

- platform-owned enum / allowlist for Shopify shell modes
- bridge normalization layer for shopper requests
- explicit `requestedConversationMode`, `effectiveConversationMode`, and `modeSource`
- structured downgrade reasons for admin diagnostics

---

## 6) Verification Requirements

This plan is not done unless verification is updated.

Must add to Shopify verification flow once implemented:

- bootstrap returns `defaultConversationMode`, `effectiveConversationMode`, and `allowedConversationModes`
- default mode matches deployment shell config when valid
- unsupported or disallowed mode falls back safely
- storefront chat requests reflect the effective mode contract
- platform admin view exposes mode summary and downgrade reason
- merchant/admin mutation paths reject unknown or disallowed modes

Recommended verification additions:

- `scripts/verify-shopify-companion.sh`
- Shopify GitHub workflow or platform-hosted Shopify suite replacement
- platform release verification suite once Shopify mode support becomes launch-critical

---

## 7) What We Should Not Do

Do not:

- pretend `assistant` already exists because the shell uses the label “Store assistant”
- expose `deep` as a merchant-facing option before its runtime semantics are real
- let theme settings silently override platform-owned shell behavior
- make mode selection a client-side-only convenience flag
- let mode support delay Milestones 1-4 launch-critical storefront completion

---

## 8) Relationship to Existing Docs

This plan is a child plan under the canonical builder-mode roadmap.

Read with:

- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](../Strategy/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md](SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md)

Interpretation rule:

- Max Mode convergence is the shell-host enabler
- shell mode enablement is the bounded behavior contract on top of that host
- neither one should be treated as “the product” on its own
