# Tenant-Scoped Shared Vector Infrastructure Plan

Status: planning document (2026-04-03)

This document defines the correct enterprise implementation path for Wave 4 Track A.

It exists to make one thing explicit:

- shared vector infrastructure must be modeled as a first-class customer, tenant, and resource problem in the platform control plane
- not as runtime-side deployment-id tagging and post-filtering

This is the concrete execution plan behind:

- `PLATFORM_EXECUTION_SEQUENCE_WAVE4_PLAN.md`
- Track A: customer, tenant, and shared resource foundation

---

## 1) Executive Summary

The correct enterprise path for shared vector infrastructure is:

1. introduce a stable `Customer -> Tenant -> Deployment` identity model in the platform
2. model shared vector infrastructure at the provider and resource level
3. use provider-native isolation primitives where they exist
4. pass the runtime a resolved scoped resource handle
5. keep shared mode disabled where safe provider-native isolation does not exist

The runtime should not be the primary tenant isolation boundary.

The storage provider should be.

That means:

- no generic deployment-id prefixing as the main model
- no application-side post-filtering as the main model
- no broad shared credentials without scoped resource guarantees

The platform must own the resource model, lifecycle, verification, and audit.

---

## 2) Why The Runtime-Filtering Approach Is Not The Right Primary Design

Runtime-side tagging and filtering is not strong enough as the enterprise primary path because:

- isolation lives in application code rather than at the storage boundary
- post-filtering can corrupt retrieval quality because foreign results can consume top-k before filtering
- shared credentials remain too broad
- lifecycle, quota, backup, legal delete, and audit are weaker
- entity-id prefixing is an application convention, not a hard storage boundary

This does not mean runtime helpers are always useless.

It means they should not be the platform's primary enterprise isolation model for shared storage.

---

## 3) Enterprise Rules For Shared Vector Infrastructure

The platform should follow these rules:

1. customer identity and tenant identity are durable and outlive deployment replacement
2. one deployment belongs to exactly one tenant
3. a customer may own multiple tenants and multiple tenant-bound deployments
4. shared storage is allowed only when the provider exposes a real isolation primitive the platform can model and verify
5. shared storage must not cross customer boundaries
6. the runtime receives a resolved provider scope, not just a tenant id string
7. provider-scoped lifecycle, reconciliation, cleanup, and verification must exist before shared mode is declared supported
8. where provider-native isolation is not strong enough, the supported posture remains dedicated storage

These rules should be enforced by the platform, not left to operator convention.

---

## 4) Track A Scope

Track A should deliver three things:

### 4.1 Stable customer and tenant identity

The platform needs a durable hierarchy independent of deployment id churn.

Recommended concepts:

- `Customer`
  - separate account boundary
  - owns users, deployments, tenants, and audit scope
- `Tenant`
  - durable business and data isolation identity within one customer
  - configured and administered by the owning customer
- `DeploymentTenantBinding`
  - links one deployment to exactly one tenant

The important point is:

- customers remain stable as the top-level ownership boundary
- tenants remain stable as the data and storage isolation boundary
- deployments may be recreated, re-applied, replaced, archived, or promoted
- one deployment belongs to exactly one tenant at a time
- multiple tenants may share vector infrastructure only within the same customer boundary
- customer and tenant identity must remain stable through those operations

### 4.2 Provider-native shared resource model

The platform needs a resource model for shared vector infrastructure, not just deployment config.

Recommended concepts:

- `SharedVectorProviderProfile`
  - provider capabilities and governance rules
- `TenantVectorScope`
  - provider-scoped tenant resource binding inside one customer boundary
- `SharedVectorResource`
  - cluster, index, database, collection, class, or equivalent root resource
- `TenantVectorHandle`
  - the resolved runtime-facing resource scope for a tenant

### 4.3 Tenant-scoped lifecycle and verification

The platform must own:

- create
- reuse
- reconcile
- rotate credentials
- verify isolation
- backup and restore posture
- legal delete and cleanup
- dedicated-to-shared and shared-to-dedicated migration compatibility

---

## 5) Provider Capability Matrix

Shared mode should be declared supported only per provider and only when the following isolation primitive is implemented and verified.

### 5.1 Pinecone

Recommended shared model:

- shared cluster or index where appropriate
- namespace per tenant or tenant-environment

Runtime should receive:

- `index`
- `namespace`
- scoped runtime credential if vendor posture allows it

Required platform ownership:

- namespace naming and reconciliation rules
- namespace lifecycle and cleanup
- namespace-scoped verification

### 5.2 Qdrant

Recommended shared model:

- collection per tenant preferred

Allowed later only if proven safe:

- shared collection with server-side payload filtering plus restricted credentials

Runtime should receive:

- `collection`
- optional scoped credential if available

Required platform ownership:

- collection lifecycle
- collection delete and legal delete workflow
- collection-scoped verification

### 5.3 Weaviate

Recommended shared model:

- native multi-tenancy tenant per tenant where supported

Fallback if the vendor capability is insufficient:

- dedicated class or collection boundary

Runtime should receive:

- `class` or collection
- `tenant` where native multi-tenancy is used

Required platform ownership:

- tenant enablement verification
- class and tenant lifecycle
- tenant-scoped verification

### 5.4 Milvus or Zilliz

Recommended shared model:

- database or collection per tenant

Use partition only if:

- vendor semantics are proven safe
- operational lifecycle is clearly modeled
- verification proves isolation and cleanup are reliable

Runtime should receive:

- `database`
- `collection`
- scoped runtime credentials where available

Required platform ownership:

- database or collection lifecycle
- credential rotation and cleanup
- tenant-scoped verification

### 5.5 Lucene and memory

These are not part of enterprise shared multi-tenant storage.

Supported posture:

- embedded only

They remain useful for:

- development
- local verification
- small-scale or temporary environments

---

## 6) What The Runtime Should Receive

The runtime should not be handed only:

- `tenantId`

It should receive a resolved provider scope, for example:

- Pinecone: `index + namespace`
- Qdrant: `collection`
- Weaviate: `class + tenant`
- Milvus or Zilliz: `database + collection`

Optional but preferred:

- tenant-scoped or resource-scoped credentials where the vendor supports them

The runtime contract should therefore be:

- provider type
- dedicated versus shared posture
- resolved provider resource handle
- resolved credential alias

Not:

- a generic app-layer filtering instruction

---

## 7) What The Platform Must Own

The platform control plane must own:

- customer-to-tenant ownership boundaries
- tenancy capability matrix per provider
- provisioning and reconciliation of scoped resources
- resource registry at the right granularity
- deletion and cleanup at that same granularity
- verification that cross-tenant access is impossible
- verification that shared storage never crosses customer boundaries
- audit trail for tenant resource creation, reassignment, rotation, and deletion
- migration compatibility between dedicated and shared modes

This is what makes the design enterprise-ready instead of convention-based.

---

## 8) Required Platform UI And Configuration Model

Track A is not complete without first-class UI and configuration support.

This should not appear as a loose:

- `multiTenant = true`

toggle.

The UI should model tenant-scoped shared infrastructure explicitly.

### 8.1 Customer and tenant administration UI

The platform needs a dedicated administration surface for:

- customer creation and lifecycle
- customer ownership and account metadata
- tenant creation and lifecycle
- tenant metadata and ownership within a customer
- deployment-to-tenant bindings
- tenant audit visibility

### 8.2 Deployment configuration UI

Each deployment should be configurable for:

- tenant binding
- exactly-one-tenant deployment ownership
- storage posture:
  - `Embedded`
  - `Dedicated`
  - `Shared`
- customer-bound shared-storage eligibility
- provider eligibility for shared mode
- effective credential source

Shared should be selectable only when:

- the selected provider supports verified native isolation
- the platform can resolve the tenant-scoped resource model for that provider
- the resolved shared resource stays within the deployment's owning customer boundary

### 8.3 Providers workspace visibility

The `Providers` workspace should show the effective runtime-facing scope without exposing secrets.

Examples:

- Pinecone:
  - `Index`
  - `Namespace`
- Qdrant:
  - `Cluster`
  - `Collection`
- Weaviate:
  - `Class`
  - `Tenant`
- Milvus or Zilliz:
  - `Database`
  - `Collection`

The operator should be able to see:

- shared versus dedicated posture
- resolved provider scope handle
- effective credential source
- whether the scope was platform-managed, reused, or externally referenced

### 8.4 Verification and diagnostics UI

Verification and diagnostics must expose tenant-scoped isolation evidence.

The operator should be able to see:

- owning customer
- tenant binding for the deployment
- resolved shared-resource handle
- provider-native isolation verification status
- customer-boundary verification status
- tenant-scoped cleanup and legal-delete readiness
- dedicated-to-shared and shared-to-dedicated migration readiness

### 8.5 Guardrails

The UI should prevent invalid combinations such as:

- shared mode on unsupported providers
- shared mode without a tenant binding
- shared mode across customer boundaries
- shared mode without a resolvable provider-native scope
- unsafe tenant reassignment as a raw config edit

Tenant reassignment must be a governed migration flow, not a simple field edit.

---

## 9) Security And Governance Requirements

The enterprise shared-storage model must guarantee:

- no cross-tenant retrieval
- no cross-tenant delete
- no cross-customer shared storage boundary
- no broad shared credential where scoped credential or scoped resource control is required
- explicit audit for tenant resource lifecycle
- legal delete at tenant-resource scope
- verification evidence that isolation is real, not assumed

The platform should also keep an explicit provider capability matrix like:

- `SHARED_SUPPORTED`
- `DEDICATED_ONLY`
- `SHARED_SUPPORTED_WITH_LIMITS`

That matrix should be visible in provider diagnostics and planning docs.

---

## 10) Verification Requirements

Track A is not complete until the platform can verify, per provider:

1. tenant-scoped write lands only in the tenant's resource scope
2. tenant-scoped read cannot access another tenant's data
3. tenant-scoped delete removes only the tenant's data
4. shared-resource cleanup leaves unrelated tenant scopes untouched
5. no tenant scope is ever resolved into another customer's shared resource boundary
6. provider resource registry and runtime handle resolution stay aligned

Verification should exist at two levels:

- provider control-plane verification
- deployment E2E verification using tenant-scoped shared resources

---

## 11) Migration And Compatibility

Track A must include compatibility for:

- dedicated to shared
- shared to dedicated
- tenant reassignment only under governed migration flow

The platform should never treat tenant reassignment as a raw config flip.

It should be a governed migration operation with:

- preflight validation
- resource mapping checks
- verification
- rollback posture

---

## 12) Recommended Track A Execution Sequence

This plan maps directly to Wave 4 Track A:

### Item 53: customer and tenant identity foundation

Deliver:

- stable customer and tenant records
- deployment-to-tenant binding
- tenant-aware audit references
- customer ownership boundaries
- customer and tenant administration UI
- deployment tenant-binding UI and guardrails

### Item 54: provider-native shared vector isolation model

Deliver:

- provider capability matrix
- provider-native shared resource model
- resolved tenant vector handle contract
- provider-specific scoped resource verification
- provider workspace visibility for resolved scope handles
- shared-versus-dedicated storage posture UI

### Item 55: tenant-scoped shared-resource lifecycle, verification, and migration compatibility

Deliver:

- lifecycle and cleanup flows
- verification suite
- dedicated-to-shared compatibility
- tenant-scoped legal delete and resource reconciliation
- diagnostics and verification UI for tenant-scoped shared resources
- governed tenant reassignment and migration UX

---

## 13) What Track A Should Explicitly Not Do

Track A should not:

- use deployment id as the durable isolation boundary
- make runtime post-filtering the primary shared-storage design
- declare shared mode for a provider before native isolation exists
- allow shared storage to cross customer boundaries
- use Lucene or memory as enterprise shared multi-tenant storage
- conflate broad shared-runtime architecture with tenant-scoped shared storage

---

## 14) Success Criteria

Track A is successful when:

1. customer and tenant identity exist as durable control-plane models independent of deployment replacement
2. every deployment is bound to exactly one tenant
3. the platform can provision or reconcile provider-native tenant scopes for supported vector backends
4. the runtime receives resolved scoped resource handles rather than generic tenant-filter instructions
5. cross-tenant access is prevented by storage-boundary design, not only by app-layer convention
6. shared storage never crosses customer boundaries
7. shared mode is disabled for providers that do not meet the required isolation standard
8. operators can configure tenant binding and storage posture through the platform UI with proper guardrails
9. effective provider scope handles and tenant isolation status are visible in Providers, Verification, and Diagnostics
10. verification, cleanup, audit, and migration compatibility all operate at the tenant-resource boundary

---

## 15) Recommendation

Wave 4 Track A should be executed using this plan as the implementation guide.

The strategic multi-tenant document explains why this matters.

This document explains how it must be built correctly for enterprise production:

- stable customer and tenant identity
- provider-native isolation
- runtime receives resolved scoped resource handles
- platform owns lifecycle, verification, and audit

That is the right path.
