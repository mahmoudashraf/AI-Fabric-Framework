# Managed Vector Database Deployment Plan

Status: planning document (2026-03-31)

Recommended sequence position:

- **Wave 3.5**
- after Wave 3 platform operability
- before Wave 4 migration, assistant, remote-policy, and multi-cloud expansion

This document describes how to evolve the platform so a user can request a deployment with a **managed vector database** as part of the normal deployment flow instead of being forced to bring their own vector infrastructure first.

This is intentionally positioned as a **Wave 3.5** capability because it strengthens the core deployment path before the broader enterprise-expansion scope of Wave 4.

Provider-control-plane rule for this wave:

- if a vector vendor exposes a formal managed provider control plane, use that first
- prefer the vendor's official SDK or official API contract over reverse-engineered console behavior
- only fall back to AWS-native deployment automation when the vendor does not expose a formal managed provider path

The product goal is simple:

- default experience: `Platform-managed vector database`
- advanced experience: `Bring your own vector database`

This aligns with the platform’s core value:

- configure an AI assistant around customer data and APIs
- keep data retrieval live and current
- remove infrastructure friction from deployment setup

---

## 1) Executive Summary

Today the platform can do two useful things:

- run with local vector backends such as `lucene` and `memory`
- manage vector resources inside an already existing external vendor deployment:
  - Qdrant collections
  - Pinecone indexes

That is useful, but it still leaves a major onboarding gap:

- the user must separately create the vendor account, cluster, or project
- then come back and wire the endpoint into the platform

For the product to feel complete, the platform should support a higher-level request:

- `Create deployment with managed vector DB`

That should mean:

1. the operator chooses the vector mode
2. the platform provisions the needed vector target
3. the platform wires secrets and endpoints automatically
4. the platform provisions indexes/collections for the deployment schema
5. the operator continues with verification and rollout

This should be implemented in phases, not as one large jump.

---

## 2) Product Goal

The platform should let the operator choose between three vector deployment modes:

### 2.1 Local managed

Examples:

- `lucene`
- `memory`

Use case:

- fast dev
- demo
- low-friction proof of concept

### 2.2 External existing

Examples:

- existing Qdrant cluster
- existing Pinecone account/index
- existing Weaviate cluster
- existing Milvus deployment

Use case:

- customer already owns the vector infrastructure
- platform manages connection and optionally vendor-side collections/indexes

### 2.3 Platform-managed vendor deployment

Examples:

- platform creates a Qdrant Cloud cluster for the deployment
- platform issues a deployment-scoped Qdrant database API key for runtime use
- platform creates a Pinecone serverless index
- later: platform creates Weaviate Cloud or Zilliz/Milvus deployment targets

Use case:

- operator wants the easiest managed path
- platform should carry the infrastructure burden

The long-term product default should be:

- platform-managed where available
- bring-your-own when the customer needs control or already has infrastructure

---

## 3) Why This Matters

### 3.1 Better onboarding

The platform becomes easier to adopt when the operator does not need to understand vector vendor setup before a first deployment works.

### 3.2 Better product clarity

The UI should ask:

- `How do you want vector storage managed?`

not:

- `Paste cluster host, choose metric, figure out vendor setup separately`

### 3.3 Stronger product positioning

This moves the platform from:

- a deployment/configuration console

toward:

- a managed AI deployment product

### 3.4 Better enterprise path

It also gives a cleaner split:

- platform-managed for standard environments
- customer-managed for regulated/private/advanced environments

---

## 4) Current State

Current platform/vector state:

- provider/vector matrix is exposed in the `Providers` workspace
- platform validates non-secret provider/vector config
- secrets are managed in the `Secrets` workspace
- vendor probes exist
- release verification checks provider connectivity
- Qdrant managed collections are supported against an existing cluster
- Pinecone managed index reconciliation is supported against an existing account/project path

What is still missing:

- a first-class `vector provisioning mode`
- a first-class `managed vector DB requested` user choice at deployment creation
- vendor account/project/cluster creation by the platform
- lifecycle ownership of vendor vector infrastructure

Provider-specific rule:

- Qdrant Cloud should be treated as a managed cluster product
- Pinecone should be treated as a managed serverless index product
- AWS fallback should only appear where a vendor does not expose a stable formal control plane

---

## 5) Recommended Product Model

### 5.1 New deployment concept: vector provisioning mode

Introduce a deployment-level concept:

- `vectorProvisioningMode`

Recommended values:

- `LOCAL_MANAGED`
- `EXTERNAL_EXISTING`
- `PLATFORM_MANAGED`

This should be stored as deployment/provider metadata and reflected in:

- templates
- draft config
- service config model
- readiness
- verification
- source of truth

### 5.2 Separate vendor choice from provisioning mode

Do not treat vector backend and provisioning mode as the same thing.

Keep:

- `vectorStrategy`
  - `lucene`
  - `memory`
  - `qdrant`
  - `pinecone`
  - `weaviate`
  - `milvus`

Separate from:

- `vectorProvisioningMode`
  - local
  - external existing
  - platform managed

That allows clean combinations:

- `lucene + LOCAL_MANAGED`
- `qdrant + EXTERNAL_EXISTING`
- `qdrant + PLATFORM_MANAGED`
- `pinecone + EXTERNAL_EXISTING`
- `pinecone + PLATFORM_MANAGED`

### 5.3 Separate deployment config from infrastructure lease

The deployment should still own:

- entity schema
- vector dimensions
- provider stack
- retrieval behavior

But the platform should introduce a separate infrastructure object such as:

- `ManagedVectorDeployment`

Suggested fields:

- `id`
- `deploymentId`
- `vendor`
- `provisioningMode`
- `resourceStatus`
- `resourceReference`
- `endpoint`
- `secretReferenceNames`
- `ownershipMode`
- `createdAt`
- `updatedAt`

This lets the platform track vector infrastructure as a governed managed resource instead of burying everything in provider config JSON.

---

## 6) User Experience Direction

### 6.1 Create deployment flow

The deployment create flow should add a vector management step.

Recommended UX:

1. choose deployment template
2. choose curated module
3. choose vector management mode
4. name deployment/environment

For vector management mode:

- `Platform-managed (recommended)`
- `Bring your own`
- `Local runtime-managed` where applicable

### 6.2 Providers page behavior

The `Providers` page should adapt based on vector mode.

If `EXTERNAL_EXISTING`:

- show host/index/cluster settings
- show vendor probes
- show onboarding guidance

If `PLATFORM_MANAGED`:

- show minimal vendor settings
- hide unnecessary host wiring when the platform will create the resource
- show managed resource plan:
  - vendor
  - region
  - resource naming
  - estimated outputs

### 6.3 Overview/diagnostics behavior

The deployment overview should show:

- vector mode
- vendor
- managed resource status
- endpoint
- collection/index status
- ownership model

Diagnostics should show:

- vendor provisioning attempts
- cluster/index/collection creation events
- drift between platform-managed vector resource and deployment config

---

## 7) Vendor Strategy

### 7.1 Qdrant first

Qdrant should be the first serious `PLATFORM_MANAGED` vector vendor target because:

- the current platform already supports collection management against Qdrant
- Qdrant Cloud has a clean cluster endpoint model
- it matches the current product need well

Official managed-provider understanding:

- Qdrant Cloud publishes a formal Cloud API with:
  - REST/JSON and gRPC endpoints
  - generated SDKs for Go, Python, and TypeScript
  - management-key authentication
  - cloud-provider and region discovery
  - cluster management operations
  - database API key issuance
- This means Qdrant qualifies as a real managed-provider target for the platform.

Planned Qdrant progression:

1. current:
   - existing cluster
   - managed collections
2. next:
   - platform stores Qdrant Cloud management credentials separately from runtime secrets
   - platform creates or resolves a Qdrant Cloud managed cluster through the formal Cloud API
   - platform creates a deployment-scoped database API key and binds it back into runtime secrets automatically
3. later:
   - platform hardens cluster lifecycle operations such as rotation, detach, recreate, and cleanup

### 7.2 Pinecone second

Pinecone should be the second `PLATFORM_MANAGED` target because:

- the platform already supports managed Pinecone index reconciliation
- Pinecone is strong for managed SaaS-style onboarding

Official managed-provider understanding:

- Pinecone exposes an official control-plane API and official SDK support for index creation
- The control plane supports serverless index creation with explicit cloud and region selection
- For Pinecone, the serverless index is the primary managed vector resource, so full deployment value can be delivered without inventing extra infrastructure layers

Planned Pinecone progression:

1. current:
   - existing account/project path
   - managed index reconciliation
2. next:
   - platform-managed serverless index provisioning in a connected Pinecone account
   - automatic endpoint binding back into runtime provider config
3. later:
   - deeper project/account automation where supported by vendor APIs and tenancy model

### 7.3 Weaviate and Milvus later

These should come after Qdrant and Pinecone because:

- current platform support is less mature operationally
- they fit enterprise/private deployments more than first-run simplicity

---

## 8) Security Model

### 8.1 Vendor integration credentials must be admin-only

There are two kinds of secrets here:

1. deployment runtime secrets
2. platform-to-vendor management credentials

Do not mix them.

Recommended model:

- deployment runtime secrets:
  - used by runtime/connector
  - visible in secret usage/readiness as appropriate
- vendor management integrations:
  - admin-only
  - used by platform control plane to create vendor resources
  - never exposed in normal deployment editing flows

Examples:

- `PINECONE_API_KEY` can remain a deployment-scoped runtime secret when Pinecone is only used as an external existing backend
- a future `PINECONE_MANAGEMENT_API_KEY` should be treated as a platform integration secret when the platform is provisioning Pinecone indexes on behalf of operators
- the Qdrant database key used by runtime should remain deployment/runtime-scoped
- the Qdrant Cloud management key and account context used to create managed clusters should be platform integration credentials only

### 8.2 Least privilege vendor credentials

Where possible:

- use scoped vendor API keys
- use per-workspace/per-project integrations
- avoid giving one global admin key broader access than necessary

### 8.3 Managed resource governance

Any platform-managed vector resource should support:

- ownership tracking
- audit events
- deletion guardrails
- approval rules for destructive actions

Examples:

- delete vector cluster
- rotate vendor secret
- recreate vendor resource
- detach managed vector resource from deployment

---

## 9) Provisioning Architecture

### 9.1 Introduce a vector provisioning provider layer

The platform should mirror the deployment provisioning architecture and add:

- `ManagedVectorProvisioningProvider`

Suggested implementations:

- `QdrantCloudClusterManagedVectorProvisioningProvider`
- `PineconeManagedVectorProvisioningProvider`
- later:
  - `WeaviateCloudManagedVectorProvisioningProvider`
  - `MilvusManagedVectorProvisioningProvider`

### 9.2 Split resource creation from schema creation

Provisioning should have two phases:

1. create or resolve the vector infrastructure target
2. create or reconcile deployment-specific schema resources

For Qdrant:

1. managed cluster target resolution/creation
2. database API key creation or cluster credential binding
3. deployment binding and readiness validation

For Pinecone:

1. index target resolution or creation
2. deployment binding and readiness validation

Provider fallback rule:

- if a vendor exposes a formal managed provider path, provision there first
- if a vendor does not expose a formal managed path, do not fake provider management through console scraping
- in those cases, the platform may later offer AWS-managed fallback infrastructure, but that is a separate deployment-target concern

### 9.3 Bind outputs back into deployment plan

Once the managed vector target exists, the platform should:

- save endpoint metadata
- save managed resource identifiers
- resolve the runtime env plan automatically
- avoid asking the operator to manually copy host values back into `Providers`

This is the key usability win.

---

## 10) Verification Model

The current verification model already checks:

- managed vector prerequisites
- vendor connectivity

For true platform-managed vector DB, add:

- `vector_resource_provisioned`
- `vector_resource_bound_to_deployment`
- `vector_schema_reconciled`
- `vector_runtime_env_matches_managed_target`

Verification should answer:

- did the platform create or resolve the vendor target?
- did the platform create the collections/indexes?
- is the runtime pointed at the managed target?

---

## 11) Phased Implementation Sequence

### Phase 1. Product-model foundation

Goal:

- make vector management mode explicit in platform data model and UI

Scope:

- add `vectorProvisioningMode`
- update templates
- update provider UI
- update service config/readiness/source-of-truth

### Phase 2. Managed resource tracking

Goal:

- make managed vector infrastructure a first-class tracked resource

Scope:

- add `ManagedVectorDeployment` entity
- add status tracking
- add overview/diagnostics surfaces
- add audit events

### Phase 3. Qdrant platform-managed target

Goal:

- let the platform create or resolve a Qdrant Cloud managed target for a deployment

Scope:

- vendor integration config
- account and region capability discovery
- Qdrant Cloud managed-cluster provisioning provider
- deployment-scoped database API key issuance
- endpoint/secret binding
- collection reconciliation where needed
- lifecycle hardening as a later extension, not a prerequisite for first managed support

### Phase 4. Pinecone platform-managed target

Goal:

- let the platform create or resolve a Pinecone managed target for a deployment

Scope:

- vendor integration config
- Pinecone provisioning provider
- index creation/reconciliation
- runtime binding

### Phase 5. Governance and lifecycle operations

Goal:

- make managed vector resources safe to operate in enterprise environments

Scope:

- approvals for destructive actions
- rotate/recreate flows
- detach/reattach flows
- drift detection

### Phase 6. Additional vendors

Goal:

- extend to Weaviate and Milvus after Qdrant/Pinecone are proven

---

## 12) Recommended Priority

Recommended order:

1. make `vectorProvisioningMode` a real concept
2. make Qdrant `PLATFORM_MANAGED` the first full vendor path
3. make Pinecone `PLATFORM_MANAGED` the second full vendor path
4. add governed lifecycle operations
5. extend to additional vendors

This is the right order because it gives the fastest path to:

- easier onboarding
- stronger product differentiation
- real operational proof

without trying to solve every vendor at once.

### 12.1 Relationship to Wave 4

This plan should execute **before** the current Wave 4 sequence.

Reason:

- it removes friction from the core deployment request flow
- it improves customer onboarding before broader migration tooling exists
- it is more central to deployment product value than assistant, remote policy, or multi-cloud breadth

The intended relationship is:

- Wave 3: strong operated deployment control plane
- **Wave 3.5: managed vector database request path**
- Wave 4: migration, assistant, remote policy service, and multi-cloud target profiles

---

## 13) Non-Goals For First Slice

Do not try to do these in the first implementation slice:

- every vector vendor at once
- full multi-cloud abstraction for vendor data plane creation
- cross-vendor migration automation between vector backends
- automatic cost optimization
- arbitrary customer-provided vector plugins

The first win is:

- `Platform-managed Qdrant Cloud cluster`

with a clean architecture that later supports Pinecone and others.

---

## 14) Product Positioning

This capability should be described to users as:

- `Managed vector storage`
- `Platform-managed retrieval infrastructure`

not:

- `vendor API helper`
- `Qdrant advanced mode`

The platform value is that the user asks for a deployment outcome, not a vendor-specific setup procedure.

---

## 15) Related Plans

- [PLATFORM_PROVIDER_AND_VECTOR_MATRIX_EXPANSION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/PLATFORM_PROVIDER_AND_VECTOR_MATRIX_EXPANSION_PLAN.md)
- [MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md)
- [IMPLEMENTATION_PRIORITIZATION_ROADMAP.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/future-work/IMPLEMENTATION_PRIORITIZATION_ROADMAP.md)
