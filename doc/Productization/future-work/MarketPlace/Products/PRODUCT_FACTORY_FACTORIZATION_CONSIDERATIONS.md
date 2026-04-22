# Product Factory Factorization Considerations

Status: future-consideration document (2026-04-17)

This document captures how the current platform could evolve from:

- a strong product foundation

into:

- a repeatable product factory

It is intentionally not an active implementation plan.

The purpose is to prevent two opposite mistakes:

- claiming too early that the platform is already a high-throughput product factory
- delaying necessary product-factory extraction work until repeated product delivery becomes painful

---

## 1) Executive Summary

The current platform is good enough to produce:

- one strong product
- then a second product that reuses meaningful pieces

It is not yet a mature factory where product creation is cheap, standardized, and low-judgment.

The right interpretation is:

- we have a working product foundry
- we do not yet have a high-throughput product factory

This means the next priority should remain:

- ship real products on top of the platform

not:

- prematurely optimize every layer for generic product production

But it is worth naming the future factorization path now so repeated product work converges toward the same structure.

---

## 2) What "Product Factory" Means Here

In this platform, a product factory means the ability to repeatedly assemble product SKUs from the same base using:

- deployment templates
- action bundles
- data bundles
- inference profiles
- shell presentation defaults
- stable consumer-facing identity
- product-specific packaging and verification

With progressively less custom engineering per product.

A true factory would make it cheap to create:

- one opinionated standalone product
- one more configurable product tier
- one white-label or partner-distributed packaging of the same core

without building a second architecture each time.

---

## 3) Current Reality

### 3.1 What is already real

The current platform already provides the necessary base for bounded product production:

- marketplace-backed composition through `TEMPLATE`, `ACTION`, `DATA`, and `INFERENCE_PROFILE`
- deployment compilation through normal draft -> publish -> apply
- runtime-backed contracts rather than conceptual plugin-only surfaces
- deployment verification and live rollout discipline
- consumer-bound identity model for external-facing packaging
- real platform UI and admin surfaces for operating deployments

This is enough to produce a first serious product.

### 3.2 What is still missing for a true factory

The current system still requires too much manual product judgment and engineering assembly.

Missing or immature layers include:

- product SKU assembly conventions
- reusable merchant/admin app shell patterns
- standardized first-party product bundle definitions
- reusable onboarding/install/sync flows
- analytics and product attribution packaging
- repeatable review-readiness and launch-readiness checklists
- clearer boundaries between:
  - platform capability
  - product bundle
  - app shell
  - market/package tier

---

## 4) Who The Factory Users Are

The expected users of the future product factory are:

### 4.1 First: internal product builders

These are the immediate users.

They:

- define product posture
- choose deployment bundles
- select default plugins and shell behavior
- set verification rules
- package the resulting product for merchants or operators

### 4.2 Second: technical operators and implementation partners

These users come after the internal product path is proven.

They:

- install or bind products to customer environments
- manage rollout and verification
- adjust bounded configuration
- operate support and migration flows

### 4.3 Third: external product builders or advanced publishers

This is later-stage usage.

It should only happen after the product assembly model is reliable enough that outside parties can use it without deep platform knowledge.

End shoppers or ordinary merchants are not the factory users.
They are product users.

---

## 5) Factorization Goal

The future goal is not "more abstraction."

The goal is to factor the product stack into stable layers:

1. `Platform capability layer`
   - runtime contracts
   - control plane
   - deployment lifecycle
   - verification
2. `Product composition layer`
   - templates
   - bundles
   - product defaults
   - shell behavior
3. `Packaging layer`
   - standalone app
   - pro tier
   - white-label or enterprise package
4. `Launch and operations layer`
   - onboarding
   - sync/health
   - diagnostics
   - analytics
   - review/submission readiness where relevant

When these layers are clearer, product creation becomes repeatable.

---

## 6) The Right Product-Factory Maturity Model

### Stage 0: Foundation

Characteristics:

- platform capabilities exist
- runtime-backed contracts exist
- product thinking is still manual

Current status:

- achieved

### Stage 1: First product foundry

Characteristics:

- one serious product can be built on the platform
- product-specific packaging is still partially custom
- useful abstractions begin to emerge from actual shipping work

Recommended next target:

- Shopify Shopping Companion

### Stage 2: Reusable product bundle model

Characteristics:

- second product reuses meaningful bundle and packaging patterns
- first-party bundle definitions become standardized
- product shells and rollout checklists are reusable

This is the stage where factory factorization starts to pay off clearly.

### Stage 3: Controlled external product assembly

Characteristics:

- partners or advanced customers can create bounded product variants
- product composition is more declarative and less engineering-heavy
- support and verification posture is strong enough for non-core teams

### Stage 4: High-throughput product factory

Characteristics:

- new product packaging is low-friction
- verification and launch workflows are standardized
- product operations are repeatable and not reliant on platform experts every time

This stage should not be claimed before repeated delivery proves it.

---

## 7) What Should Be Factorized Later

When the time is right, the most useful factorization targets are:

### 7.1 Product bundle definition

We should eventually have a first-class definition for:

- product id
- target buyer and channel
- default template
- default plugin bundle
- default shell posture
- default inference posture
- default verification profile

This should sit above raw deployment config, not replace it.

### 7.2 App-shell packaging

We should eventually standardize:

- embedded admin shell
- storefront shell
- diagnostics shell
- onboarding shell

So the Shopify product is not a one-off example forever.

### 7.3 Product verification packs

We should eventually define reusable:

- install verification
- sync verification
- runtime verification
- rollout verification
- launch-readiness review checks

### 7.4 Product analytics and attribution

We should eventually package:

- core product usage metrics
- conversion or downstream outcome attribution
- merchant/operator success reporting

### 7.5 Launch artifacts

We should eventually standardize:

- demo environment shape
- review submission checklist
- test credentials package
- screencast/runbook package

---

## 8) What Should Not Be Factorized Too Early

Avoid early over-generalization of:

- every possible product category
- cross-vertical UI shells before two real products exist
- plugin types without runtime-backed contracts
- product taxonomy before real market feedback
- partner-facing builders before internal product packaging is stable

The right rule is:

- factor repeated pain, not hypothetical future flexibility

---

## 9) Triggers For Starting Real Factory Work

This future consideration should become an active plan only when at least two of these are true:

1. one strong product has shipped or reached real pilot use
2. a second product or product tier is underway and repeating similar packaging work
3. product onboarding, sync, diagnostics, and verification are obviously being rebuilt by hand
4. merchants, partners, or internal teams want bounded product customization on top of the same core
5. the packaging differences between standalone and white-label are becoming operationally expensive

If those triggers are not present, do not prioritize factory work over product proof.

---

## 10) Immediate Practical Guidance

For now, the correct move is:

- treat the current platform as sufficient to build the first serious products
- keep shipping on top of it
- observe what product work repeats
- extract only the stable repeated parts

In current roadmap terms:

- Shopify Shopping Companion should be the proving ground
- not the excuse to launch a major generic factory initiative

---

## 11) Recommendation

Keep this as a future-consideration document until:

- Shopify Companion or another first product proves real demand
- repeated product packaging work exposes stable pain worth extracting

Do not sell ourselves the story that the factory is already complete.

Do not dismiss the current platform as wasted effort either.

The more accurate statement is:

- the platform is now credible enough to produce products
- the factory should be factorized only after product repetition proves what deserves to become factory infrastructure
