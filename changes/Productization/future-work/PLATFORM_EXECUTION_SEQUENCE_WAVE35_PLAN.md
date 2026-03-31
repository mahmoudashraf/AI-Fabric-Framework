# Platform Execution Sequence Wave 3.5 Plan

Status: execution-sequencing document (2026-03-31)

Wave 3.5 sits between:

- Wave 3 platform operability
- Wave 4 migration, assistant, remote-policy, and multi-cloud expansion

Its purpose is narrow and high-value:

- make managed vector storage an explicit deployment request
- distinguish local, bring-your-own, and provider-managed vector paths
- turn supported vendor control planes into first-class platform capabilities
- keep the deployment experience simple for operators without hiding security boundaries

---

## 1) Sequencing Principles

Wave 3.5 should follow these rules:

- prefer official provider control planes over home-grown infrastructure workarounds
- keep provider runtime credentials separate from platform vendor-integration credentials
- make operator intent explicit before platform automation starts
- never present a provider mode as live if the platform cannot actually provision it
- bind managed vector outputs back into deployment config automatically
- keep verification and readiness evidence aligned with the real managed resource state

---

## 2) Formal Provider Understanding

Wave 3.5 should treat these providers differently:

### Qdrant Cloud

- formal cloud control plane exists
- REST/JSON and gRPC are available
- official generated SDKs exist
- management keys are distinct from collection or cluster data-plane keys
- serverless collection creation is formally supported
- cluster management is also formally supported

Recommended Wave 3.5 posture:

- first implement `PLATFORM_MANAGED` via Qdrant Cloud serverless collections
- later add dedicated cluster lifecycle for customers who need isolated infrastructure

### Pinecone

- formal control plane exists
- official SDKs and API support serverless index creation
- cloud and region are explicit provider-side choices
- the index itself is the managed vector resource

Recommended Wave 3.5 posture:

- implement `PLATFORM_MANAGED` via Pinecone serverless index creation and binding

### Weaviate / Milvus

- keep as `EXTERNAL_EXISTING` first
- do not promise provider-managed automation until formal managed-provider control-plane work is proven

### AWS fallback

- only use AWS fallback when a desired vendor path has no formal provider control plane
- do not use AWS fallback to bypass a stable vendor-native managed API

---

## 3) Recommended Wave 3.5 Execution Sequence

### Track A: Product model and operator UX

43. vector provisioning mode foundation: add explicit `LOCAL_MANAGED`, `EXTERNAL_EXISTING`, and `PLATFORM_MANAGED` deployment/provider modeling across templates, create flow, providers, source-of-truth, readiness, and verification
44. managed vendor capability gating: show which vendors support real platform-managed provisioning now, which remain bring-your-own only, and why

### Track B: Managed resource tracking

45. managed vector resource registry: persist one governed managed-resource record per deployment-owned vector target, with vendor, status, resource id, endpoint, secret references, ownership mode, and timestamps
46. operator visibility and audit: show managed vector state in overview/diagnostics, attach audit events, and make remediation/readiness aware of the managed resource record

### Track C: Formal provider control planes

47. Qdrant Cloud serverless provisioning: use the formal cloud API to discover regions, create or resolve a serverless collection, create a deployment-scoped collection API key, and bind the resulting endpoint/secret back into the deployment
48. Pinecone serverless provisioning hardening: make Pinecone serverless index provisioning explicit as a platform-managed mode instead of an implicit template behavior, and track ownership and runtime binding through the managed resource registry

### Track D: Governance and lifecycle

49. managed vector drift and destructive controls: detect when runtime/provider config diverges from the managed resource record, and gate destructive actions behind approvals where required
50. detach, rotate, recreate, and cleanup flows: make managed vector lifecycle operations explicit and auditable for enterprise operators

---

## 4) Scope Notes

Wave 3.5 should explicitly include:

- explicit vector provisioning mode selection
- provider-managed capability surfaces
- managed resource persistence
- formal Qdrant Cloud and Pinecone automation where supported
- deployment-to-resource binding and verification

Wave 3.5 should explicitly not attempt to finish:

- all vector vendors at once
- migration/import orchestration
- assistant runtime behavior work
- generic multi-cloud deployment targets
- AWS-hosted custom vector infrastructure before vendor-native paths are used

---

## 5) Completion Criteria

Wave 3.5 is complete when:

- operators can request managed vector storage explicitly during deployment creation
- the platform distinguishes local, external existing, and provider-managed vector paths consistently
- managed vector resources are persisted as governed platform resources instead of loose JSON intent
- Qdrant Cloud and Pinecone managed targets are bound back into deployment config automatically where supported
- readiness, verification, diagnostics, and remediation all reason about the same managed resource state
- backend tests and frontend build pass for every completed item

---

## 6) Execution Progress

Completed on this branch:

- provider and vector matrix expansion groundwork
- vendor connectivity probes
- Pinecone managed index reconciliation against connected vendor accounts
- Qdrant managed collection reconciliation against connected clusters
- 43. vector provisioning mode foundation

Next in sequence:

- 44. managed vendor capability gating
