# Product Direction Decision Record

Status: canonical product decision record (2026-04-17)

This document replaces ad hoc interpretation of:

- `doc/Productization/future-work/MarketPlace/NEW_DEPLOYMENT_TYPE_CONCEPTS_EVALUATION.md`
- `doc/Productization/future-work/MarketPlace/Companion_Plan.txt`

It extracts the parts that are valid under the current platform and marketplace architecture, and downgrades the parts that are only useful as product rhetoric.

---

## 1) Canonical Decisions

### 1.1 Shopify V1 product posture

The correct Shopify V1 posture is:

- `Shopping Companion`

Meaning:

- read-first
- evidence-backed
- grounded in product, policy, and review knowledge
- user-controlled purchase path
- no AI-initiated checkout or refund behavior in launch scope

This fits the current platform better than a write-heavy "cart assistant" product.

### 1.2 Deep Resolver

`Deep Resolver` is a valid product direction, but not a separate deployment family yet.

Current decision:

- treat it as a later orchestration-mode enhancement on the existing conversational runtime
- do not make it a Shopify V1 blocker
- do not treat it as proof that we need a new top-level deployment type now

It is valid because it extends the existing action/governance shell rather than replacing it.

It is not ready to be a shipping product posture until the iterative tool-use loop, budgets, and telemetry are explicitly designed.

### 1.3 Thinker

`Thinker` is valid only as:

- a marketplace template
- a prompt/reranker/knowledge-source packaging preset

Current decision:

- do not introduce `Thinker` as a new deployment type
- ship it later as a template if the knowledge-expert use case becomes commercially useful

### 1.4 Smart Brain

`Smart Brain` is valid as a separate product track.

Current decision:

- keep it outside the current conversational platform maturation stream
- treat it as a future worker/pipeline product family
- do not merge it into the current runtime or Shopify launch work

### 1.5 Shopify packaging

If Shopify ships as a public app first, it must be:

- a thin first-party consumer of the platform
- not a parallel Shopify-specific stack

That means:

- same control plane
- same deployment lifecycle
- same marketplace model
- same runtime contracts
- stable external identity through consumer binding, not deployment-id leakage

See:

- `doc/Productization/future-work/MarketPlace/CONSUMER_BOUND_DEPLOYMENT_RESOLUTION_PLAN.md`

---

## 2) What Is Valid From The Source Notes

### 2.1 Valid from `NEW_DEPLOYMENT_TYPE_CONCEPTS_EVALUATION.md`

The following claims are valid:

- `Deep Resolver` is a real market category and a valid platform extension
- the missing gap is iterative tool use, not a replacement runtime
- `Thinker` overlaps heavily with current retrieval and evidence capabilities
- `Thinker` should be a template, not a deployment type
- `Smart Brain` is a different execution model with different observability and trigger semantics
- `Smart Brain` should be handled on a separate track

### 2.2 Valid from `Companion_Plan.txt`

The following claims are valid:

- shoppers trust a decision-support companion more than an autonomous transaction bot
- a read-first companion lowers merchant anxiety and review risk
- products, reviews, policies, and comparison are the right early surfaces
- user-initiated cart behavior is better than AI-initiated purchase behavior
- Shopify V1 should stay read-heavy and avoid broad write scopes
- the same platform should power both the shopper-facing product and any later platform packaging

---

## 3) What Is Only Partially Valid

These suggestions contain useful signal, but should not be adopted literally.

### 3.1 "Deep Resolver should be built first"

Valid only relative to the other three concepts.

Not valid as:

- a reason to interrupt current platform hardening
- a reason to create a separate runtime family immediately

### 3.2 "Thinker requires zero runtime code"

Valid for a light template preset.

Not fully valid if the product requires:

- stronger reranking
- citation-walking
- cross-backend federation
- deeper evidence navigation

### 3.3 "Standalone Shopify app first, white-label second"

Valid as a go-to-market packaging rule.

Not valid if it turns into:

- a separate architecture
- a Shopify-specific fork of the runtime
- a hardcoded product stack that bypasses the platform

### 3.4 "Platform-V5 already perfectly fits shopping companion"

Directionally true.

Not literally true.

Missing or still-needed productization work includes:

- review-source ingestion
- product comparison UX
- merchant install and sync ergonomics
- analytics and attribution
- Shopify packaging and review readiness

---

## 4) What We Reject

The following interpretations should not drive roadmap or architecture:

- treating `Thinker` as a new public deployment type
- treating `Smart Brain` as part of the current conversational launch stream
- treating `Deep Resolver` as a reason to overcomplicate Shopify V1
- treating Shopify as the whole product identity
- treating a standalone Shopify app as permission to build a second stack
- relying on optimistic schedule claims as if they were implementation truth

---

## 5) Canonical Product Sequence

Recommended sequence:

1. Ship Shopify as the first strong reference vertical with a `Shopping Companion` posture.
2. Add productized comparison, review, and policy knowledge experiences.
3. Add Deep Resolver later as an orchestration-mode enhancement when multi-step read-first resolution is proven necessary.
4. Ship Thinker later as a marketplace template if a knowledge-expert SKU is still commercially useful.
5. Treat Smart Brain as a separate product line with its own runtime and operating model.

---

## 6) Product Boundary Rules

These rules stay in force:

- the platform remains the main product category
- Shopify remains the first strong vertical proof point
- marketplace stays a control-plane composition layer, not a code-loading system
- products must compile into deployment drafts and versions
- publish and apply stay mandatory for live behavior
- shopper-facing packaging must consume stable external identities, not hardcoded deployment ids

---

## 7) Practical Translation

For current planning, the product map should be read as:

- `Shopify Shopping Companion` = real near-term product
- `Deep Resolver` = later runtime/orchestration enhancement
- `Thinker` = later template/SKU packaging
- `Smart Brain` = separate future product track

This is the valid subset of the earlier suggestions.
