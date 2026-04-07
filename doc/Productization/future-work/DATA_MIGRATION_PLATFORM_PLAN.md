# Data Migration Platform Plan

Status: planning document (2026-03-30)

This document describes how to expand the platform from a deployment configuration system into a migration-enabled onboarding and activation platform.

The goal is to help customers move data into AI-enabled deployments through guided configuration, a **small number of generic source adapters**, and managed migration execution.

---

## 1) Executive Summary

The platform should support data migration as a first-class product capability, not just as an implementation project.

The ideal operator flow is:

1. choose a target deployment
2. open a migration wizard
3. connect to a source system
4. sample and map data
5. define migration behavior
6. run a dry run
7. execute a managed migration job
8. monitor results and reconcile failures

This should work for:

- one-time initial data import
- re-sync and incremental migration
- indexed knowledge import
- operational entity migration

Important scope rule:

- this plan should **not** assume a bespoke connector for every source system
- the product should prefer a generic migration engine with a few reusable source patterns

---

## 2) Product Goal

The platform should make data migration feel like:

- a guided onboarding workflow
- an operational job with observability
- a reusable asset for similar customers

not:

- a custom script per customer
- a one-off consulting-only tool
- manual database export/import work outside the platform

Target outcomes:

- migration plan stored in the platform
- migration secrets managed securely
- migration runs tracked like releases/jobs
- migration can target AI deployments managed by the platform

---

## 3) Key Product Decision

Migration should be modeled as:

- **configuration in the platform**
- executed by a **separate migration runtime / job**

Recommended model:

- keep runtime and REST connector focused on serving production AI traffic
- run migrations through a separate service type or job runner

This avoids:

- overloading runtime with long-running migration work
- mixing onboarding and online serving concerns
- creating scaling and stability risk for live traffic

---

## 4) Reuse of Existing AI Fabric Capabilities

The platform should reuse as much existing framework capability as possible.

High-value building blocks:

- migration module / migration pipeline
- data-sync ingestion APIs
- generic REST connector patterns
- entity configuration model
- indexing / embedding pipeline
- access control / metadata rules

Recommended product direction:

- reuse the migration module as the execution engine
- extend it with a few source adapters and platform orchestration
- use runtime data-sync APIs as the normalized ingestion boundary where appropriate

---

## 5) Target Migration Use Cases

### 5.1 Structured operational entities

Examples:

- products
- orders
- reviews
- policies
- CRM entities
- support tickets

### 5.2 Knowledge / content migration

Examples:

- PDFs
- help center articles
- CMS pages
- policy documents
- manuals
- knowledge-base exports

### 5.3 Incremental sync

Examples:

- nightly import
- delta sync by updated timestamp
- webhook-assisted refresh

### 5.4 Realistic source coverage

The migration platform should prioritize broad coverage through a few patterns:

- files
- generic REST APIs
- SQL sources

Optional curated source connectors should be added only when strategically justified for a target vertical.

---

## 6) Product Model

Recommended new entities:

- `MigrationTemplate`
- `MigrationPlan`
- `MigrationSourceConnection`
- `MigrationRun`
- `MigrationRunStep`
- `MigrationCheckpoint`
- `MigrationErrorRecord`

Recommended relationships:

- one deployment can have many migration plans
- one migration plan can have many runs
- one run can target one deployment version or one deployment environment

---

## 7) Migration Architecture

### 7.1 Control plane vs execution plane

Control plane responsibilities:

- wizard and configuration UI
- secrets management
- plan validation
- job orchestration
- run history and audit

Execution plane responsibilities:

- connect to source system
- read and normalize records
- transform and map entities
- call ingestion target
- checkpoint progress
- emit logs and metrics

### 7.2 Recommended execution target

Recommended first model:

- managed migration runner service / job

Possible deployment shapes:

- ephemeral job per run
- reusable migration service per environment
- later: worker pool

### 7.3 Recommended ingestion boundary

Preferred ingestion path for AI-enabled entities:

- migration runner -> runtime data-sync API

Benefits:

- consistent validation
- consistent embedding/indexing behavior
- avoids bypassing runtime invariants

For non-vector operational writes, a connector-based or target-specific writer may also be used where needed.

---

## 8) Connector Strategy

### 8.1 Source adapter strategy

Recommended first source categories:

- `FILE`
  - CSV
  - JSON
  - JSONL
- `REST_API`
  - generic endpoint + auth + pagination + mapping
- `SQL`
  - read-only query/view based import for databases such as PostgreSQL/MySQL

Recommended rule:

- build the migration engine around these few generic adapters first
- do **not** build a unique connector per source system by default

Later optional curated connectors:

- `SHOPIFY`
- `CMS / KB source`
- other high-value vertical integrations only after the generic engine is proven

### 8.2 Connector contract

All source adapters and curated connectors should expose a normalized contract:

- connection test
- schema/sample fetch
- list datasets / collections
- paged read
- incremental checkpoint support

### 8.3 Target connector types

Recommended target modes:

- runtime data-sync target
- application REST API target
- bulk file export target

### 8.4 Why this boundary matters

This keeps migration complexity under control:

- one migration engine
- a few reusable read adapters
- one normalized ingestion boundary

instead of:

- one custom connector per customer system
- one-off migration code for every onboarding project

---

## 9) Migration Wizard UX

### 9.1 Wizard steps

Recommended steps:

1. choose target deployment
2. choose migration template or start blank
3. configure source connection
4. discover source schema / sample data
5. map source objects to AI entities
6. configure transformation and rules
7. configure indexing / embedding behavior
8. run validation and dry run
9. execute migration

### 9.2 Mapping UI

Mapping should support:

- source field -> target field
- derived fields
- metadata field mapping
- vector source content composition
- default values
- ignore rules

### 9.3 Transformation rules

Add support for:

- field renaming
- normalization
- date / enum transforms
- text cleanup
- chunking for long content
- redaction / PII controls

---

## 10) Migration Run Experience

### 10.1 Run states

Recommended run states:

- `DRAFT`
- `VALIDATED`
- `DRY_RUN_SUCCEEDED`
- `RUNNING`
- `PAUSED`
- `FAILED`
- `COMPLETED`
- `COMPLETED_WITH_ERRORS`

### 10.2 Run dashboard

A migration run view should show:

- source records read
- target records written
- skipped records
- errored records
- checkpoint position
- throughput
- logs
- estimated completion

### 10.3 Reconciliation

Operators should be able to:

- retry failed records
- export error rows
- resume from checkpoint
- cancel safely

---

## 11) Platform Backend Changes

### 11.1 Domain and APIs

Add backend support for:

- migration plans
- migration templates
- migration runs
- migration connection test
- dry run
- run / resume / cancel

### 11.2 Secret management

Migration sources often need strong credentials.

Use secrets for:

- DB passwords
- API tokens
- OAuth refresh tokens
- object-store credentials

Do not store these in the migration plan config blob.

### 11.3 Execution orchestration

The backend should:

- create migration job requests
- hand off work to execution plane
- collect status
- store logs and summary metrics

---

## 12) Platform Frontend Changes

### 12.1 New migration section

Add a top-level `Migrations` area with:

- migration plans grid
- runs grid
- templates
- source connections

### 12.2 Deployment integration

Inside a deployment workspace, show:

- migration plans for this deployment
- last migration run
- indexing readiness
- data freshness

### 12.3 Guided templates

Templates should exist for common onboarding paths:

- ecommerce demo import
- product catalog import
- knowledge-base import
- policy library import

---

## 13) Enterprise Considerations

### 13.1 Safety

Migration must support:

- dry run before write
- row-level validation
- rate limiting
- target back-pressure handling
- idempotent re-run behavior

### 13.2 Audit

Track:

- who created the plan
- who ran the migration
- what source was connected
- what target deployment was affected
- what record counts changed

### 13.3 Data governance

Need controls for:

- PII handling
- retention of staged data
- source credential rotation
- log redaction

---

## 14) Recommended Delivery Phases

### Phase 1

- migration plan domain model
- basic migration runner
- runtime data-sync target
- CSV / REST source connectors
- run history and logs

### Phase 2

- wizard UI
- mapping UI
- dry run and validation report
- checkpoint/resume
- retry failed rows

### Phase 3

- database source connectors
- incremental sync
- scheduling
- reusable templates

### Phase 4

- advanced connectors
- multi-step transforms
- approval workflows
- customer self-service onboarding packs

---

## 15) Recommendation

The right product shape is:

- platform as migration control plane
- migration runner as execution service
- runtime data-sync as normalized ingestion boundary

That gives the platform a strong onboarding story and turns migration from services-only work into a reusable enterprise feature.
