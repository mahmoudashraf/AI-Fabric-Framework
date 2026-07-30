# Framework Release Strategy and Open-Core Plan

Status: planning document (2026-03-31)

This document defines the recommended release strategy for AI Fabric:

- what should be public as the developer framework
- what should remain private as product/platform capability
- how to structure the boundary so the public framework is useful without giving away the enterprise product moat

Execution plan:

- `doc/Productization/future-work/AI_FABRIC_FRAMEWORK_PUBLIC_REPO_SEPARATION_AND_RELEASE_PLAN.md`

---

## 1) Executive Summary

Recommended strategy:

- **public**: the developer framework
- **private**: the product and platform

The public framework should include:

- core orchestration/runtime primitives
- actions
- retrieval / RAG abstractions
- indexing and data-sync
- migration building blocks
- connector contracts
- provider integrations needed for developer adoption

The private product should include:

- platform backend and UI
- deployment control plane
- provisioning and release lifecycle
- governance and enterprise administration
- prompt management UI and hot apply workflow
- diagnostics and verification product workflows
- platform assistant
- commercial vertical packaging and operator experiences

This is the cleanest open-core split.

---

## 2) Strategic Goal

The goal is to achieve both:

- adoption through a credible public developer framework
- monetization through a differentiated enterprise platform

This avoids two bad outcomes:

- keeping everything private and slowing ecosystem adoption
- open-sourcing so much that the product loses its moat

---

## 3) Recommended Product Boundary

### 3.1 Public side

The public side should answer:

- “How do developers build AI-powered applications with AI Fabric?”

### 3.2 Private side

The private side should answer:

- “How do teams configure, deploy, govern, test, and operate those applications at scale?”

This gives a clean category split:

- **Framework** = build with AI Fabric
- **Platform** = operate AI deployments with AI Fabric

---

## 4) What Should Be Public

Recommended public scope:

### 4.1 Core runtime and orchestration

- runtime core abstractions
- orchestration pipeline primitives
- intent/action framework
- confirmation handling primitives
- extension points and SPIs

### 4.2 Actions framework

- action metadata and contracts
- action handler interfaces
- confirmation-aware action behavior
- action connector contracts

### 4.3 Retrieval and indexing

- retrieval interfaces
- vector DB abstraction layer
- indexing/data-sync APIs
- entity configuration model where it supports framework usage

### 4.4 Migration building blocks

- migration module/runtime building blocks
- migration connector interfaces
- migration execution primitives

### 4.5 Provider integrations

- OpenAI and other provider integrations that are part of the developer experience
- provider abstractions

### 4.6 Generic connectors

- generic REST connector foundations
- connector contracts
- examples/templates

### 4.7 Docs and examples

- developer guides
- architecture docs for framework usage
- sample apps
- extension examples

---

## 5) What Should Stay Private

Recommended private scope:

### 5.1 Platform backend and UI

- deployment management backend
- platform admin UI
- deployment-centric operator workflows

### 5.2 Deployment control plane

- deployment drafts and version lifecycle
- publish/apply/release model
- provisioning orchestration
- environment and target-profile management

### 5.3 Governance and enterprise administration

- users, roles, assignments
- approvals
- audit console
- archive/delete workflows
- bulk administration

### 5.4 Productized prompt management

- prompt workspace
- prompt testing console
- hot apply orchestration
- prompt overlay lifecycle

### 5.5 Verification and diagnostics product flows

- release verification workflows
- deployment diagnostics UI
- log integration workflows
- evidence views and summaries

### 5.6 Migration control plane

- migration wizard
- migration plan storage
- managed migration runs
- onboarding/operator UX

### 5.7 Embedded POC and operator experiences

- POC deployment mode
- embedded chatbot in the platform
- platform assistant

### 5.8 Commercial vertical packaging

- vertical-specific admin/operator products
- guided solution packs
- commercial onboarding experiences

---

## 6) Why This Split Is Good

### 6.1 It preserves a credible moat

The moat should not be:

- secret source code for basic orchestration

The moat should be:

- productized operations layer
- deployment lifecycle
- governance
- fast iteration and testing workflows
- enterprise administration

### 6.2 It improves adoption

A public framework helps:

- developer trust
- integration ecosystem
- self-serve adoption
- technical credibility

### 6.3 It makes the platform easier to sell

Customers can understand:

- framework = what developers use
- platform = what teams buy to operate it professionally

That is clearer than mixing both into one confusing offer.

---

## 7) What Not to Open

Do not open-source these product differentiators too early:

- deployment control plane internals
- release/apply UX and orchestration
- enterprise governance flows
- platform-owned diagnostics workflows
- prompt hot apply product workflows
- migration control-plane UX
- platform assistant UX
- curated commercial vertical packaging

If these become public too early, you risk:

- weakening monetization
- increasing support burden
- training the market to expect the full product for free

---

## 8) Repo and Module Strategy

### 8.1 Recommended high-level structure

Recommended long-term split:

- public framework repo
- private product repo

Alternative transitional model:

- one monorepo with clear public/private boundaries
- then split later

### 8.2 Recommended public repo contents

Public repo should contain:

- framework modules
- framework docs
- sample apps
- public CI
- public releases

### 8.3 Recommended private repo contents

Private repo should contain:

- platform backend
- platform UI
- private docs/runbooks
- commercial vertical/operator layers

### 8.4 Transitional rule

Before any repo split:

- remove platform-private dependencies from framework modules
- ensure framework modules do not depend on private classes or workflows
- make contracts clean and intentional

---

## 9) Licensing Direction

Recommended licensing shape:

### 9.1 Framework

Use a permissive or business-friendly OSS license for the developer framework.

The goal is:

- low friction for adoption
- ecosystem growth

### 9.2 Platform/product

Keep the platform proprietary / private.

### 9.3 Commercial add-ons

Optional later model:

- open framework
- paid enterprise product
- optional paid managed services / hosted control plane / vertical packs

---

## 10) Product Packaging Implications

Recommended offer structure:

### 10.1 Free/public offer

- use AI Fabric framework in your own applications
- run self-hosted basics
- build custom assistants/workflows

### 10.2 Paid product offer

- configure and operate deployments through the platform
- versioning and rollout
- governance and access control
- migration and onboarding workflows
- prompt management and testing workflows
- diagnostics, verification, and enterprise admin

This is a strong and understandable packaging model.

---

## 11) Migration Plan from Current State

### Phase 1

- classify modules as public vs private
- document the intended boundary
- remove accidental coupling

### Phase 2

- clean framework public APIs
- prepare framework docs and examples for public consumption
- keep platform modules private

### Phase 3

- move platform-only docs and modules into clearly private areas/repos
- create framework release process
- first selected release route: publish framework Maven artifacts to GitHub Packages and attach a framework-only source archive to a GitHub Release

### Phase 4

- launch public framework identity
- launch product/platform as the enterprise operating layer

---

## 12) Risks

### Risk 1: opening too much

Effect:

- weakens product moat
- raises support expectations

### Risk 2: opening too little

Effect:

- low adoption
- weaker ecosystem
- harder to earn trust

### Risk 3: unclear boundary

Effect:

- framework feels crippled
- platform feels arbitrary
- engineering complexity increases

### Risk 4: product logic leaking into framework

Effect:

- harder repo split later
- blurred licensing boundary
- confusing developer experience

---

## 13) Recommendation

The best move is:

- open the framework
- keep the platform private
- make the boundary explicit and intentional

In practical terms:

- release core actions, orchestration, RAG, indexing, migration, and connector foundations
- keep platform deployment control, governance, prompt-management product flows, diagnostics, and operator UX private

That gives the strongest combination of:

- adoption
- credibility
- ecosystem potential
- and commercial defensibility
