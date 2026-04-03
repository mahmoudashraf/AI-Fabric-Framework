# Onboarding Vectorization Layer Plan

Status: planning document (2026-04-04)

This document replaces the broader "data migration" framing for Wave 4 Track B.

The goal is narrower and more product-accurate:

- index current customer data into the deployment's configured AI entities
- write that indexed output into the deployment's selected or provisioned vector database
- support the onboarding phase, where bulk vectorization matters most

This is not a broad ETL platform plan.

---

## 1) Product Goal

The platform should support **onboarding vectorization** as a first-class capability.

That means:

- selecting a deployment
- connecting to the customer's source data
- mapping source data into the deployment's configured AI entities
- bulk-indexing into the deployment's active vectorization path

This capability is most important:

- at the beginning of onboarding
- when the customer needs initial indexed knowledge or entity data loaded

It is less important later, because after onboarding the product should rely more on:

- live writes
- runtime indexing
- ongoing application-driven updates

---

## 2) Scope Definition

Track B should be scoped as:

- **indexing customer data into AI Fabric deployments**

It should not be scoped as:

- broad enterprise ETL
- operational-system replication
- generalized migration of every downstream application state
- a replacement for customer integration platforms

The default target is:

- the deployment's configured AI entity model
- the deployment's selected or provisioned vector database

So if a deployment is configured for:

- `product`
- `policy`
- `review`

then vectorization should map source data into exactly those entities and index through the deployment's existing runtime indexing path.

Bootstrap indexing should also be supported when:

- the configured vector spaces do not exist yet
- the deployment has no indexed state yet
- the deployment is newly provisioned and needs its first searchable dataset

---

## 3) Multi-Tenancy And Deployment Rules

Vectorization must respect the deployment model already in place.

Rules:

- one vectorization plan targets one deployment
- the deployment's current `Customer -> Tenant -> Deployment` binding is authoritative
- if the deployment uses tenant-scoped shared vector infrastructure, vectorization must respect that automatically
- if the deployment uses dedicated infrastructure, vectorization must write only to that dedicated target
- the vectorization layer must not invent a second tenancy model

The deployment's current provider and storage posture should determine the effective target automatically.

When deployment configuration changes in a way that affects indexed output, the customer should be able to choose whether to reindex.

Examples:

- entity configuration changes
- vector-content composition changes
- extraction or mapping changes
- embedding or vectorization-affecting configuration changes

The customer choice should be explicit:

- no reindex
- reindex impacted entities only
- full deployment reindex

---

## 4) Runner Model

The execution model should use a **runner per deployment**.

Why:

- source connectivity is customer-specific
- auth and network posture are customer-specific
- onboarding indexing is bursty and temporary
- we want the runner to be provisioned with customer connectivity much like the connector is provisioned for customer integration

Recommended model:

- provision a vectorization runner for the deployment when onboarding indexing is needed
- let that runner pull work from the platform
- delete the runner after indexing is completed
- recreate it later if another indexing pass is needed

The same runner model should support:

- initial bootstrap indexing when vectors are absent
- explicit reindex runs after relevant config changes

This makes the runner:

- deployment-scoped
- customer-connectivity aware
- ephemeral by default

It should not be treated as permanently shared platform infrastructure by default.

---

## 5) Lifecycle Model

The platform remains the control plane and source of truth.

The runner remains the execution worker.

Platform owns:

- plan creation
- plan revisioning
- run creation
- start
- pause
- resume
- cancel
- retry
- status authority

Runner owns:

- polling and claiming work
- heartbeats
- source reads
- indexing execution
- coarse checkpoint reporting
- technical outcome reporting

Important network rule:

- runners pull from the platform
- the platform does not directly call runners

---

## 6) Provisioning Model

The runner should be treated as part of the provisioning layer.

Recommended posture:

- runtime and connector remain the serving plane
- vectorization runner is a temporary execution-plane component
- provisioning should be able to create:
  - runtime
  - connector
  - optional vectorization runner

The vectorization runner should be provisioned only when needed:

- onboarding import
- major re-index wave
- customer-requested refresh
- bootstrap indexing when vectors are absent or empty

And should be removable afterwards.

---

## 7) Tracking Model

Vectorization tracking should be intentionally lighter in the first implementation.

We do **not** need full fine-grained per-artifact receipt tracking in Track B.

For the first product slice, we can track progress roughly through:

- source page numbers
- id ranges such as `0-1000`
- batch counters
- source cursors
- rough success and failure counts

This gives enough operational visibility for onboarding without turning Track B into a full historical reconciliation database.

So Track B should start with:

- coarse checkpointing
- coarse progress tracking
- coarse failure buckets
- coarse run reason tracking such as bootstrap or reindex

Not with:

- full artifact-level rollback receipts
- full previous-state reconstruction

---

## 8) Rollback Posture

Rollback should not be the primary design goal for this layer.

Reasons:

- indexing is expensive
- embedding and token costs matter
- deletes plus full re-index is often the operationally cleaner answer
- most onboarding issues should be handled by:
  - fix plan or mapping
  - rerun indexing
  - patch or update indexed entities if needed

So the platform should:

- avoid promising rich rollback in Track B
- support delete and rerun where necessary
- support targeted update or patch flows later when the product needs them

The preferred recovery posture is:

- cancel
- adjust plan
- rerun

not:

- sophisticated compensating rollback logic

---

## 9) Verification Posture

Verification is still important, but can be staged.

For Track B, later verification should compare:

- source data shape and rough counts
- indexed entity counts
- target vector spaces
- deployment entity coverage

Before deep verification, the platform should at least detect obvious bootstrap conditions:

- configured vector spaces missing
- indexed state absent or clearly empty

and offer a bootstrap vectorization action.

This verification step should come **after** the first working vectorization layer is in place.

So:

- basic execution and progress first
- indexing verification later

---

## 10) Recommended Product Model

Recommended new platform entities:

- `VectorizationPlan`
- `VectorizationPlanImpact`
- `VectorizationSourceConnection`
- `VectorizationRun`
- `VectorizationRunStep`
- `VectorizationCheckpoint`
- `VectorizationFailureBucket`
- optional later: `VectorizationVerificationRun`

Recommended relationships:

- one deployment can have many vectorization plans
- one plan can have many runs
- one run targets one deployment snapshot
- one run is executed by one claimed runner at a time
- one run should carry a reason such as:
  - `BOOTSTRAP`
  - `REINDEX`
  - `REFRESH`

---

## 11) Source Strategy

Recommended first source adapter categories:

- `FILE`
  - CSV
  - JSON
  - JSONL
- `REST_API`
  - generic endpoint + auth + pagination
- `SQL`
  - read-only query or view based extraction

The product should not assume:

- bespoke connector per source system

It should prefer:

- a small number of general-purpose source adapters

---

## 12) Ingestion Boundary

Preferred target path:

- vectorization runner -> runtime data-sync API

Benefits:

- same entity rules as the deployment
- same indexing behavior
- same vectorization path
- same selected/provider-backed vector database
- same multi-tenancy and storage posture

The vectorization layer should not bypass deployment invariants by writing directly to vector providers as the default path.

---

## 13) Track B Build Order

Track B should build in this order:

1. vectorization domain model in the platform
2. vectorization source connection model and secret references
3. bootstrap detection plus plan revisioning and preview workspace
4. deployment-scoped ephemeral runner provisioning
5. coarse checkpointing and lifecycle controls
6. config-change impact analysis and customer-selected reindex flow
7. later verification against source and indexed target state

---

## 14) Summary

The right Track B goal is:

- **Vectorization Layer**, not broad migration

The right operating posture is:

- onboarding-heavy
- deployment-scoped
- customer-connectivity aware
- pull-based runners
- temporary runner provisioning
- bootstrap indexing when vectors are absent
- explicit customer choice on reindex after config changes
- coarse tracking first
- verification later
- limited rollback ambitions
