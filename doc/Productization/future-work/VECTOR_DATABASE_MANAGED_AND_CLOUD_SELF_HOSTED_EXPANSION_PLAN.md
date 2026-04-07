# Vector Database Managed And Cloud Self-Hosted Expansion Plan

Status: planning document (2026-04-01)

This document extends the managed vector strategy with a deeper rule:

- prefer formal vendor-managed services when they exist
- if no formal managed vendor service exists, then consider cloud-provider deployment operated by the platform

This plan is based on the vector backends currently supported by the framework and platform:

- `lucene`
- `memory`
- `qdrant`
- `pinecone`
- `weaviate`
- `milvus`

---

## 1) Strategy Rule

The platform should use a strict order of preference for vector infrastructure:

1. vendor-managed service with formal control plane
2. vendor-managed service with formal automation path such as official API, SDK, or Terraform provider
3. customer-owned existing deployment
4. platform-deployed self-hosted infrastructure on cloud provider

This rule matters because:

- vendor-managed services usually have the best operational path
- they reduce infrastructure burden on the platform
- they align better with enterprise support and lifecycle expectations
- cloud self-hosting should be a fallback, not the default

---

## 2) Current Vendor Landscape For Supported Backends

Based on current official vendor documentation:

- `qdrant`: Qdrant Managed Cloud exists, with a formal cloud API and management keys
- `pinecone`: Pinecone serverless managed service exists, with a formal control plane at `api.pinecone.io`
- `weaviate`: Weaviate Cloud exists as a fully managed offering, with serverless, enterprise cloud, and bring-your-own-cloud options
- `milvus`: Zilliz Cloud exists as the fully managed Milvus service
- `lucene` and `memory`: local runtime-managed only, not external vendor infrastructure

Sources:

- Qdrant Cloud API: https://qdrant.tech/documentation/qdrant-cloud-api/
- Qdrant Managed Cloud: https://qdrant.tech/documentation/cloud/
- Pinecone serverless docs: https://docs.pinecone.io/docs/create-an-index
- Weaviate Cloud docs: https://docs.weaviate.io/wcs
- Zilliz Cloud docs: https://docs.zilliz.com/

Implication:

- for the currently supported external vector vendors, vendor-managed service exists in all major cases
- AWS self-hosted deployment should therefore be a fallback and governance path, not the mainline path

---

## 3) Product Goal

The platform should support three operator-facing infrastructure choices:

### 3.1 Platform-managed vendor service

Examples:

- Qdrant Cloud
- Pinecone serverless
- future: Weaviate Cloud
- future: Zilliz Cloud

### 3.2 Bring your own existing deployment

Examples:

- existing Qdrant cluster
- existing Pinecone index
- existing Weaviate instance
- existing Milvus or Zilliz deployment

### 3.3 Platform-deployed self-hosted vector infrastructure

Examples:

- Qdrant on AWS
- Weaviate on AWS
- Milvus on AWS

This mode should only appear when:

- the vendor has no formal managed service, or
- the customer explicitly requires self-hosted infrastructure under their cloud control

---

## 4) Supported Backend Decision Matrix

### 4.1 Lucene / Memory

These stay:

- `LOCAL_MANAGED`

No external infrastructure orchestration is needed.

### 4.2 Qdrant

Preferred path:

- `PLATFORM_MANAGED` via Qdrant Managed Cloud

Secondary path:

- `EXTERNAL_EXISTING`

Fallback path:

- platform-deployed self-hosted Qdrant on AWS only when customer policy or infrastructure rules require it

### 4.3 Pinecone

Preferred path:

- `PLATFORM_MANAGED` via Pinecone serverless

Secondary path:

- `EXTERNAL_EXISTING`

Fallback path:

- none by default

Reason:

- Pinecone is fundamentally a managed vendor product, not a self-hosted OSS target the platform should try to impersonate

### 4.4 Weaviate

Preferred future path:

- `PLATFORM_MANAGED` via Weaviate Cloud

Current path:

- `EXTERNAL_EXISTING`

Fallback path:

- platform-deployed self-hosted Weaviate on AWS if formal Weaviate Cloud automation is not integrated yet or if the customer explicitly needs self-hosted deployment

### 4.5 Milvus

Preferred future path:

- `PLATFORM_MANAGED` via Zilliz Cloud

Current path:

- `EXTERNAL_EXISTING`

Fallback path:

- platform-deployed self-hosted Milvus on AWS for customers who require self-hosting or when managed Zilliz automation is not integrated yet

---

## 5) Cloud Self-Hosted Mode Should Be Explicit

Do not hide self-hosted infrastructure under the same label as vendor-managed service.

Recommended provisioning mode model:

- `LOCAL_MANAGED`
- `EXTERNAL_EXISTING`
- `PLATFORM_MANAGED_VENDOR`
- `PLATFORM_MANAGED_CLOUD`

Or, if keeping current names:

- keep `PLATFORM_MANAGED`
- add an explicit `managedVectorProviderType`
  - `VENDOR_MANAGED`
  - `CLOUD_SELF_HOSTED`

This distinction is critical because operator expectations differ for:

- lifecycle
- support
- cost
- backups
- upgrades
- HA posture
- security boundaries

---

## 6) AWS Self-Hosted Architecture Principles

When cloud self-hosted vector deployment is needed, the platform should start with AWS first.

Recommended principles:

- use one standardized deployment pattern per engine
- prefer managed cloud primitives around the engine, not hand-built servers
- keep runtime and vector DB in separate service boundaries
- treat backups, snapshots, and credentials as first-class managed resources
- keep operator choices small and opinionated

### 6.1 Qdrant on AWS

Possible shapes:

- ECS/Fargate for simple single-tenant deployments
- EKS for more advanced HA posture
- EBS/EFS/S3-backed backup flows depending on the operational model

Use only if:

- customer requires self-hosting
- vendor-managed Qdrant Cloud is not acceptable

### 6.2 Weaviate on AWS

Possible shapes:

- EKS-first posture
- managed object storage, snapshot, and secret integration around it

Use only if:

- Weaviate Cloud is not the chosen path

### 6.3 Milvus on AWS

Milvus is operationally heavier than Qdrant or Pinecone.

If self-hosted AWS support is pursued, it should be treated as:

- advanced
- enterprise-only
- explicitly higher-ops-cost

It should not become the default Milvus path if Zilliz Cloud automation is available.

---

## 7) Administration Model

Vendor-managed and cloud-self-hosted modes should have different admin behavior.

### 7.1 Vendor-managed

The platform should manage:

- vendor account integration
- resource creation
- resource binding
- lifecycle controls
- drift checks against vendor state

### 7.2 Cloud self-hosted

The platform should manage:

- cloud environment target
- deployment package
- infrastructure state references
- app/engine credentials
- backup posture
- upgrade posture
- infrastructure drift against cloud resources

This is not just another vector vendor integration. It is a separate infrastructure class.

---

## 8) Recommended Sequencing

### Phase 1: finish vendor-managed supported backends

1. keep Qdrant Cloud and Pinecone as the current production-grade vendor-managed paths
2. add Weaviate Cloud research and control-plane readiness review
3. add Zilliz Cloud research and control-plane readiness review

### Phase 2: expand vendor-managed automation

4. implement Weaviate Cloud as `PLATFORM_MANAGED_VENDOR` if formal automation is strong enough
5. implement Zilliz Cloud as `PLATFORM_MANAGED_VENDOR` if formal automation is strong enough

### Phase 3: define cloud-self-hosted fallback

6. add explicit infrastructure type distinction in platform models
7. add AWS self-hosted Qdrant plan
8. add AWS self-hosted Weaviate plan
9. treat AWS self-hosted Milvus as advanced/enterprise-only

### Phase 4: governance and economics

10. add cost model visibility
11. add backup and recovery posture views
12. add upgrade/lifecycle policies
13. add provider-specific readiness and drift scoring

---

## 9) Recommended Operator Experience

The platform should not ask low-level infrastructure questions first.

Instead, it should ask:

1. `How should vector storage be managed?`
   - local
   - bring your own
   - platform-managed

2. if platform-managed:
   - `Use vendor-managed service`
   - `Use cloud self-hosted infrastructure`

3. then ask only the minimum provider-specific inputs needed

This keeps the product simple while preserving flexibility.

---

## 10) Guardrails

The platform should not:

- present self-hosted AWS as equal to vendor-managed SaaS without explaining the tradeoff
- route Pinecone into self-hosted logic
- claim automation for Weaviate or Zilliz before formal provider integration is proven
- let cloud-self-hosted paths bypass backup, drift, or secret governance

---

## 11) Completion Criteria

This future work area is mature when:

- every supported external vector backend has a clear decision path
- vendor-managed service is used first where it exists
- cloud-self-hosted fallback is explicit and governed
- the platform distinguishes vendor-managed and cloud-self-hosted state in source of truth and remediation
- operator UX stays simple despite the deeper infrastructure model
