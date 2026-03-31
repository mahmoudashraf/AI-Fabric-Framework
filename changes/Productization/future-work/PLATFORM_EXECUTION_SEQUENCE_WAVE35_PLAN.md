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
- REST/JSON and gRPC are available for the data plane
- official generated SDKs exist for Go, Python, and TypeScript, while Java integration should use the formal public API directly
- management keys are distinct from collection or cluster data-plane keys
- cluster creation is the formal infrastructure unit
- deployment-scoped database API keys can be issued separately from the management key

Recommended Wave 3.5 posture:

- implement `PLATFORM_MANAGED` via Qdrant Cloud managed cluster provisioning first
- keep the management key in platform Secrets and issue a separate deployment-scoped runtime key for the live runtime
- harden lifecycle operations such as detach and cleanup through the formal control plane
- keep runtime-key rotation explicit but blocked until a staged cutover flow can retire the previous live key safely

### Pinecone

- formal control plane exists
- official SDKs and API support serverless index creation
- Java platform integration can use the formal public API directly without depending on console-only behavior
- cloud and region are explicit provider-side choices
- the index itself is the managed vector resource
- the control plane resolves the runtime API host after creation, so the platform should bind that host back into deployment config automatically
- current platform-managed runtime binding can safely use a deployment-owned secret reference even when the same vendor key material is reused underneath

Recommended Wave 3.5 posture:

- implement `PLATFORM_MANAGED` via Pinecone serverless index creation and binding
- keep the operator-facing experience minimal: index name, cloud, region, metric, and dimensions
- treat account/project selection as provider-side integration context, not as the main deployment UX
- support explicit destructive recreation and detached-resource cleanup through the formal control plane

### Weaviate / Milvus

- keep as `EXTERNAL_EXISTING` first
- do not promise provider-managed automation until formal managed-provider control-plane work is proven

### AWS fallback

- only use AWS fallback when a desired vendor path has no formal provider control plane
- do not use AWS fallback to bypass a stable vendor-native managed API
- if AWS fallback is ever needed, treat it as a separate provider path with its own lifecycle and cost/readiness model instead of pretending it is vendor-native

---

## 3) Recommended Wave 3.5 Execution Sequence

### Track A: Product model and operator UX

43. vector provisioning mode foundation: add explicit `LOCAL_MANAGED`, `EXTERNAL_EXISTING`, and `PLATFORM_MANAGED` deployment/provider modeling across templates, create flow, providers, source-of-truth, readiness, and verification
44. managed vendor capability gating: show which vendors support real platform-managed provisioning now, which remain bring-your-own only, and why

### Track B: Managed resource tracking

45. managed vector resource registry: persist one governed managed-resource record per deployment-owned vector target, with vendor, status, resource id, endpoint, secret references, ownership mode, and timestamps
46. operator visibility and audit: show managed vector state in overview/diagnostics, attach audit events, and make remediation/readiness aware of the managed resource record

### Track C: Formal provider control planes

47. Qdrant Cloud managed-cluster provisioning: use the formal cloud API to discover accounts, regions, and packages, create or resolve a managed cluster, create a deployment-scoped database API key, and bind the resulting endpoint/secret back into the deployment
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

Item status:

| Item | Status | Implementation anchor |
| --- | --- | --- |
| 43. vector provisioning mode foundation | Completed | `461c9931` |
| 44. managed vendor capability gating | Completed | `461c9931` |
| 45. managed vector resource registry | Completed | `645e16da` |
| 46. operator visibility and audit | Completed | `645e16da` |
| 47. Qdrant Cloud managed-cluster provisioning | Completed | `2a0474c3` |
| 48. Pinecone serverless provisioning hardening | Completed | `957757dd` |
| 49. managed vector drift and destructive controls | Completed | `5cb94537` |
| 50. detach, rotate, recreate, and cleanup flows | Completed | `db07c747` |

Supporting groundwork completed on this branch:

- provider and vector matrix expansion groundwork
- vendor connectivity probes
- Pinecone managed index reconciliation against connected vendor accounts
- Qdrant managed collection reconciliation against connected clusters
- 43. vector provisioning mode foundation
- 44. managed vendor capability gating
- 45. managed vector resource registry
- 46. operator visibility and audit
- 47. Qdrant Cloud managed-cluster provisioning
- 48. Pinecone serverless provisioning hardening
- 49. managed vector drift and destructive controls
- 50. detach, rotate, recreate, and cleanup flows

Wave 3.5 status:

- complete on this branch
