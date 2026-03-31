# Deployment Test Data Migration and POC Chatbot Plan

Status: planning document (2026-03-31)

This document describes how to support proof-of-concept deployments that combine:

- test data migration
- sample / sandbox data setup
- an integrated chatbot inside the platform for validation and demos

The goal is to make the platform useful not only for production deployment management, but also for rapid pre-sales, onboarding, and validation workflows.

---

## 1) Executive Summary

The platform should support a dedicated POC workflow where an operator can:

1. create a deployment for demo or proof-of-concept use
2. load test/sample/customer-safe data into it
3. open an integrated chatbot directly in the platform
4. validate behavior before external integration work begins

This is especially useful for:

- sales demos
- customer workshops
- solution design
- sandbox trials
- implementation validation

---

## 2) Product Goal

The platform should provide a fast path for:

- creating a deployment
- migrating or generating non-production data
- testing the assistant/chat experience immediately

Target outcomes:

- lower setup time for demos
- better customer workshop experience
- easier internal validation
- repeatable sandbox environments

---

## 3) Core Product Decision

POC support should combine three platform capabilities:

- deployment creation
- test data migration or generation
- embedded chatbot testing UI

This should be modeled as a dedicated operating mode, not as a pile of manual steps across different pages.

Recommended concept:

- `POC deployment mode`

---

## 4) Supported Data Paths

### 4.1 Test data import

Operators should be able to:

- upload CSV / JSON
- import sample content files
- load packaged demo data
- import sanitized customer extracts

### 4.2 Synthetic data generation

The platform should later support:

- generating sample products
- generating fake reviews / tickets / policies
- creating scenario-specific demo records

### 4.3 Guided migration

For more realistic POCs:

- connect to a source system
- sample a subset
- map into the deployment
- run a one-time test migration

This plan complements the broader migration plan in [DATA_MIGRATION_PLATFORM_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/DATA_MIGRATION_PLATFORM_PLAN.md).

---

## 5) Integrated Chatbot Goal

The platform should include an embedded chatbot for the selected deployment so the operator can test:

- retrieval behavior
- action execution
- permissions and clarification
- prompt behavior
- overall conversational quality

This should be available directly in the deployment workspace.

The operator should not need to build a separate frontend first just to validate the deployment.

---

## 6) Recommended Product Model

Add or strengthen these concepts:

- `PocDeploymentProfile`
- `TestDataSet`
- `TestDataMigrationRun`
- `EmbeddedChatSession`
- `DemoScenario`

Recommended relationships:

- a deployment may be marked `POC_ENABLED`
- a deployment can have many test datasets
- a deployment can have many POC chat sessions
- a deployment can have resettable test state

---

## 7) POC Deployment Workspace

Add a dedicated `POC` section inside the deployment workspace.

Recommended panels:

- `Test Data`
- `Migration`
- `Chatbot`
- `Scenarios`
- `Reset`

### 7.1 Test Data panel

Show:

- current dataset loaded
- last migration/import run
- sample entity counts
- reset / clear / reload controls

### 7.2 Migration panel

Show:

- upload/import controls
- source selection
- mapping summary
- dry run result
- execution logs

### 7.3 Chatbot panel

Show:

- embedded chat UI
- deployment context
- citations
- actions executed
- request/response trace summary

---

## 8) Embedded Chatbot UX

### 8.1 Minimal first version

Recommended first version:

- deployment-scoped chat input
- chat response rendering
- citations and action traces
- conversation reset

### 8.2 POC testing features

Recommended additions:

- suggested prompts
- scenario presets
- compare two deployment behaviors
- show which action executed
- show which vector space answered

### 8.3 Demo mode

Add a demo mode that hides operator complexity and shows:

- a clean chat surface
- selected sample scenarios
- deployment branding / title

This is useful for customer-facing workshops.

---

## 9) Data Migration Design Direction

### 9.1 Separate from production migration

POC migration should be optimized for:

- speed
- safety
- repeatability

It should not immediately require the full enterprise migration feature set.

### 9.2 Recommended POC data sources

First sources:

- local file upload
- platform-packaged demo datasets
- simple REST pull

### 9.3 Recommended execution path

Recommended flow:

- import source data
- normalize into entity shape
- ingest through runtime data-sync
- verify indexing
- open chatbot

---

## 10) Reset and Cleanup

POC workflows need easy cleanup.

Support:

- clear vectors
- reset demo/test data
- reload selected dataset
- archive POC deployment

This is critical for repeatable demos.

---

## 11) Backend Changes

### 11.1 Domain additions

Add support for:

- POC deployment metadata
- test datasets
- test migration runs
- embedded chat session records

### 11.2 APIs

Add APIs for:

- upload test data
- list available demo datasets
- trigger test migration
- get import/index summary
- create / reset embedded chat session

### 11.3 Reuse of existing modules

Reuse where possible:

- migration module for ingestion workflow
- runtime data-sync API
- chat session support
- diagnostics and verification paths

---

## 12) Frontend Changes

### 12.1 New deployment POC tab

Inside a deployment workspace, add:

- `POC`

Recommended subsections:

- `Dataset`
- `Import`
- `Chat`
- `Results`

### 12.2 Embedded test console

The chat console should surface:

- assistant reply
- action trace
- knowledge sources
- latency

### 12.3 POC scenario library

Operators should be able to save named scenarios such as:

- product discovery
- policy Q&A
- order lookup
- escalation flow

---

## 13) Enterprise and Sales Value

This capability helps both product and go-to-market.

Value areas:

- faster presales demos
- better onboarding
- lower implementation friction
- easier internal QA for customer-specific packs

It also gives a bridge between:

- configuration work
- migration work
- actual chatbot experience

---

## 14) Recommended Delivery Phases

### Phase 1

- POC deployment flag/profile
- packaged demo datasets
- simple upload/import
- embedded chat UI

### Phase 2

- guided test migration
- dataset reset/reload
- action and citation trace in chat
- sample scenarios

### Phase 3

- sanitized source import
- shareable demo mode
- test result reporting
- compare environments

### Phase 4

- synthetic data generation
- more connector types
- reusable POC templates by domain

---

## 15) Recommendation

The right product move is:

- make POC deployment a first-class operating mode
- attach test data migration directly to it
- give the deployment an embedded chatbot in the platform

That turns the platform into a complete sandbox, onboarding, and demonstration environment, not only a deployment admin tool.
