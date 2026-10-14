# 010.40 Small Persistent Lucene Deployment Profile Implementation Plan

- Status: planned; production profile adoption pending measurement and verification.
- Created: 2026-10-13.
- Execution priority: 6; measurement starts early in parallel with `010.35` and `010.36`.
- Source review: Platform `db58acd9`; framework `561659dc`; dependency AI Fabric `0.8.32`.
- Owners: Platform managed profile, private runtime/service packaging, and operational verification.
- Related: [010.19 vector strategy](010_19_QDRANT_DEMOTION_AND_MANAGED_VECTOR_MIGRATION_PLAN.md), [010.24 source documents](010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md), completed [010.34](010_34_TEMPORARY_HUMAN_CONVERSATION_HANDOVER_AND_LIVE_AGENT_EXTENSION_PLAN.md), [010.41](010_41_CUSTOMER_HOSTED_DEPLOYMENT_BUNDLE_IMPLEMENTATION_PLAN.md).

## 1. Outcome And Verdict

Offer a named, supported small-deployment profile using the existing embedded Lucene index and durable local state where appropriate. The aim is fewer required services and a measured resource envelope for dedicated deployments.

Use persistent Lucene files. OS caching and provider internals may use memory, but a volatile in-memory index is not the durable production profile. This plan does not build another vector provider.

## 2. Current Code Evidence

- `ai-infrastructure-module/ai-fabric-runtime/src/main/resources/application.yml`: file-backed H2 default, Lucene default, dimension-specific index directory, Data Sync, and document indexing configuration.
- `Platfrom/backend/.../deployment/service/ManagedDeploymentProfileCatalog.java`: Lucene/memory strategies, local-managed provisioning, embedded storage posture, and dimension bounds.
- `.../deployment/service/CoolifyDeploymentProvider.java`: `/app/data` reconciliation for a local Lucene runtime.
- `.../deployment/service/DeploymentDraftValidationService.java`: existing vector/storage/behavior validation to extend.
- `ai-infrastructure-module/ai-fabric-runtime/docker-entrypoint.sh`: mounted data/config preparation and non-root runtime execution.
- `.../ai-infrastructure-generic-rest-connector/src/main/resources/application.yml`: connector persistence/bootstrap JDBC and pool configuration.
- `product-services/basic-loomai-inbox-service/src/main/resources/application.yml`: separate Basic Inbox file-backed H2 default.

Local Lucene persistence is already implemented. A tested capacity profile, explicit service/state matrix, resource measurements, and supported operational limits are the work remaining. Do not infer small total memory from choosing an embedded vector store.

## 3. Profile Composition

Use existing provider/vector/runtime profile concepts and a TEMPLATE recommending the reviewed settings. Do not create a new deployment behavior or special dealership runtime.

The initial profile is one runtime instance with one writer for its local Lucene index. Required services derive from installed capabilities:

- runtime and its durable data volume;
- connector only when a selected source/action/channel requires it;
- worker/runner only when ingestion configuration requires that role;
- customer-selected document source storage;
- Basic Inbox only for deployments explicitly selecting that channel provider;
- persistent JDBC store for each enabled capability's operational state.

Embedded operational state is evaluated per service. Do not merge Basic Inbox, connector, and runtime files or mount a single writable index across independent runtimes. Do not silently disable handover, review, queueing, recovery, or isolation to meet a memory target.

Customer document originals stay in customer-provided approved storage. Persistent derived vectors and operational DB files are not a LoomAI managed object-storage offer. Optional source-directory mounts retain `010.24`'s explicit demo/small-data restrictions.

## 4. Measurement Contract

Create a repeatable benchmark under `verification-support/small-lucene-profile/` with exact source/image identity, selected capabilities, JVM settings, CPU/RAM limits, volume type, provider settings, and workload counts.

Proposed exploratory workloads, not advertised capacity:

1. 100, 1,000, and 5,000 structured records with dimension/metadata size recorded.
2. A document corpus with recorded file sizes and resulting chunk counts.
3. Warm queries at concurrency 1, 5, and 10 with real tenant filters.
4. Initial indexing, mixed update/query load, deletion, and full rebuild.
5. A conversation-only case and the same case with review/handover enabled.

Measure resident/peak memory, heap/native/file-cache contribution where observable, CPU, index/DB disk size, indexing throughput, and query latency. Report runtime retrieval latency separately from external embedding/LLM latency and API cost. Include JVM, connector, workers, inbox, database, and required shared services when reporting deployment footprint.

Declare acceptance thresholds in the benchmark configuration before calling a profile supported. Select production limits from measured headroom and recovery behavior; do not publish an assumed RAM, price, or record-count promise.

## 5. Persistence, Backup, And Update Contract

Use verified persistent volumes with least-required permissions. Keep embedding model/dimension and index format in release metadata. A model/dimension change requires explicit new index/reindex readiness; never silently mix incompatible vectors.

The application database remains the authoritative operational store for conversation ownership, action/review/control state, work records, and source/version lifecycle. The vector index is derived and rebuildable. Original document retention stays under customer storage policy.

Backups must capture an application-consistent DB snapshot and either a supported consistent index snapshot or the precise rebuild source/config. Copying open database/index files without a consistency procedure is not a validated backup. Test restore on an isolated runtime and ensure old callback/dispatch work cannot replay duplicate effects.

## 6. Implementation Phases

### L0. Measure The Existing Baseline

- [ ] Run the declared workloads without source optimization first.
- [ ] Measure every required service and external dependency.
- [ ] Test restart, data-volume replacement, and cold retrieval.
- [ ] Record the real bottleneck and determine whether Lucene or other service/JVM choices dominate.

This phase may begin immediately, independent of implementation of the other ideas.

### L1. Define Supported Profile And Validation

- [ ] Select the measured topology, budgets, workload limits, and volume/JDBC requirements.
- [ ] Express those settings through existing managed profiles/templates.
- [ ] Validate unsupported shared-index writers, dimension mismatches, missing persistent stores, and incompatible capabilities.
- [ ] Show explicit limits and required services in provisioning preview and Platform UI.

### L2. Apply Evidence-Based Optimization

- [ ] Adjust JVM, pools, concurrency, ingestion batches, and optional service roles only where measurements justify them.
- [ ] Reuse provider-supported Lucene settings; do not hand-roll search/index logic.
- [ ] If image/module trimming is useful, verify startup inventories and all selected capabilities rather than deleting dependencies by guess.
- [ ] Preserve current semantic policy, authorization, read/RAG cooperation, and effect guards.

### L3. Prove Operational Recovery

- [ ] Verify committed vectors and operational DB survive process/container restart.
- [ ] Test active indexing and queries during controlled restart/recovery.
- [ ] Test backup/restore, index rebuild, disk-full detection, and capacity limits.
- [ ] Verify callback/queue recovery and completed handover continuity when enabled.
- [ ] Prove cross-tenant/deployment filters and ownership remain fail-closed.

### L4. Release The Profile

- [ ] Publish measured support limits and a staging canary profile/template.
- [ ] Provision through Platform and verify persisted storage/readiness readback.
- [ ] Run representative live chat, indexing, source, action, and recovery checks.
- [ ] Record image/config/profile identities, benchmark results, and source of cost/resource estimates.
- [ ] Adopt in a customer deployment only after its workload and required capabilities fit the proved profile.

## 7. Framework Impact And Completion

Extend existing `RuntimeVectorBackendStartupValidatorTest`,
`DeploymentDraftValidationServiceTest`, and `DeploymentConfigCompilerTest`
for profile compatibility. Keep load/restart/restore measurements in the
repeatable benchmark rather than encoding an unmeasured resource promise in
a unit test. Repeat enabled review/handover and ingestion persistence checks
against the actual limited-resource runtime.

No framework release is expected for measurement/profile work. A provider defect discovered under the actual workload requires a traced framework fix with focused Lucene/concurrency/isolation tests, followed by private adoption. Do not weaken invariants or remove existing capability checks as an optimization.

Complete when a named profile has repeatable capacity/restart/recovery evidence, explicit persistent-state ownership, honest total-resource limits, and managed provisioning validation. Scale beyond the supported single-runtime topology through another reviewed storage/profile option; do not imply local files provide shared multi-node storage.
