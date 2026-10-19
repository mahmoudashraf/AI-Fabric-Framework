# 010.44 Customer-System Ownership Boundary Audit

- Date: 2026-10-18.
- Status: source and bounded live-metadata investigation; corrections not implemented.
- Baseline: `Platform-V11`, `cab320545cc3dc1900c313ed1e6b40da1e7d0628`.
- Implementation handoff: [010.44 correction plan](010_44_CUSTOMER_SYSTEM_INTEGRATION_BOUNDARY_CORRECTION_PLAN.md).
- Superseded assumptions: [010.36 customer productization](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md), especially the required simulator/receiver/business-database graph.

## 1. Verdict

The owner-identified mistake is real: the new customer dealership package
provisions demonstration business systems as if they were necessary AI
deployment infrastructure. That package must not be applied or promoted
unchanged. Existing independent demo applications do not need to be deleted;
they should remain external example customer/provider integrations.

The broader audit found two additional high-priority ownership defects:
Railway project-delete-first cleanup and managed-vector adoption without
durable ownership proof. It also found split inventory authority and weak
initialization evidence inside the bundled demo receiver.

No additional automatic customer-business-database provisioning path was found
outside the known dealership package in the inspected source. This is a scoped
finding, not proof that every historical deployment or external account is clear.
No live customer data loss was established, and no destructive operation was
performed during this audit.

## 2. Confirmed Findings

### F1. High: Customer Offer Bundles An External Provider And Business System

The current managed dependency fixture declares simulator, Inbox and receiver
slots. The receiver is the dealership demo backend image and declares its own
PostgreSQL database. Its schema includes inventory and lead records. The
customer template hardwires managed simulator and receiver references.

Evidence:

- [Managed specification loader](../../../../../../../../verification-support/productization-01036/simulated-dealership-managed-spec.mjs): lines 8 and 32 load the graph from a test fixture and select the dealership demo backend artifact.
- [Dependency fixture](../../../../../../../../Platfrom/backend/src/test/resources/managed-dependency/simulated-dealership-experience-v1-6.template.json): simulator at line 5, receiver at 276, backend image at 278, database at 457 and initialization contract at 472.
- [V174 customer package](../../../../../../../../Platfrom/backend/src/main/resources/db/migration/V174__simulated_dealership_customer_package.sql): receiver action binding at 461 and managed simulator/receiver defaults at 582-587.
- [Demo business schema](../../../../../../../../product-demos/autotrader-dealership-demo/backend/src/main/resources/db/migration/V1__dealership_demo_schema.sql): inventory and lead tables belong to the example dealership application, not AI orchestration.

Impact: future self-service customer apply would create and lifecycle-manage
business/provider fixtures alongside the AI solution. The error increases
capacity, recovery and retirement obligations and gives the wrong product
ownership contract. Renaming the backend an engagement receiver does not change
its responsibility.

Correction: an AI-only customer template, DATA/ACTION external bindings, optional
human-channel service, separately owned demo fixtures and independent architecture
tests. Preserve immutable published history; issue corrected definitions.

### F2. High: Railway Hard Delete Can Remove More Than The AI Deployment

Provisioning reuses a Railway project by name. Cleanup attempts to delete the
entire recorded project before obtaining its current inventory. Service-level
cleanup is only the fallback after project deletion fails. Therefore a reused
project or a project containing subsequently added external/customer resources
can lose unrelated resources when the AI deployment is hard-deleted.

Evidence:

- [Railway provisioning](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayApiProvisioningProvider.java): lines 189-204 reuse the named project and environment without proving exclusive ownership of the project.
- [Infrastructure cleanup](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentInfrastructureCleanupService.java): line 270 deletes the project first; lines 295-315 only later handle individual services and inventory.
- [Existing cleanup test](../../../../../../../../Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/DeploymentInfrastructureCleanupServiceTest.java): line 36 explicitly expects direct project deletion, so passing it would preserve the unsafe assumption.

Correction: owned-resource-only cleanup; whole-project deletion only with exact
exclusive ownership evidence and complete current inventory. Mixed projects,
foreign databases/environments and uncertain ownership must be preserved. No
live Railway project inspection/deletion was performed; actual exposure is not
established by this source finding.

### F3. High: Vector Compatibility Can Be Mistaken For Lifecycle Ownership

The existing managed provisioner accepts a same-name Pinecone index after
dimensions/metric validation and a same-name Qdrant cluster after provider/region
validation. Both can then be recorded as managed resources and become hard-delete
targets. Cleanup does not require the recorded resource to have been created by
this deployment. Pinecone cleanup can additionally disable deletion protection.

Evidence:

- [Managed vector provisioner](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentManagedVectorProvisioningService.java): Pinecone adoption at 239-254; Qdrant cluster adoption at 349-366; reused/created state is reported, but not an exclusive ownership receipt.
- [Managed resource registry](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentManagedVectorResourceService.java): lines 55-98 register desired resources; lines 292-315 admit the managed root modes without a created-only condition.
- [Cleanup dispatch](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentInfrastructureCleanupService.java): lines 365-394 select deletion by managed vendor/type; lines 407-410 disable Pinecone protection then delete; line 462 deletes the Qdrant cluster.

Collection-level reuse requires the same correction: Qdrant reconciliation at
lines 630-647 of the provisioner accepts an existing entity-type collection;
registry lines 348-383 track it, and cleanup lines 414-424 delete it. Owning or
having access to a shared cluster is not permission to delete a preexisting
collection.

The existing managed Zilliz path has a stronger prerequisite: it requires stored
deployment runtime credentials to reuse a cluster (provisioner lines 534-560).
That is not itself a durable creation-ownership receipt; include this path in
the same safety contract and tests rather than claiming all vendor paths are
equally unguarded. Its cleanup can delete a tracked cluster at lines 465-478.

Correction: exact durable creation/ownership receipts, explicit retained-external
bindings, separate root/child authority, cross-target references and policy-checked
deletion. A matching name, valid credentials, compatible dimensions or a managed
record alone must not grant ownership. Do not silently adopt external resources,
rotate their keys or disable their deletion protection.

This concerns existing managed strategies. It does not establish a defect in
the newer explicitly retained-external native-token binding path, nor prove that
the approved Free-01 or production cluster was deleted/adopted by these paths.

### F4. Medium: Inventory Discovery And Lead Validation Have Different Authorities

The receiver unconditionally seeds six vehicles when its SQL inventory is empty.
Its lead service resolves the selected vehicle against that local inventory,
while AI stock discovery uses the provider DATA integration. Initial fixture
identifiers can match and hide divergence after provider stock changes.

Evidence:

- [Inventory seeder](../../../../../../../../product-demos/autotrader-dealership-demo/backend/src/main/java/com/loomai/demo/dealership/inventory/DemoInventorySeeder.java): line 23 seeds when the local repository is empty.
- [Lead service](../../../../../../../../product-demos/autotrader-dealership-demo/backend/src/main/java/com/loomai/demo/dealership/lead/LeadService.java): lines 61-71 resolve against local vehicles; lines 97-102 insert a local business lead.

This is a second business authority, not merely an unused cache. However, the
audited initialization path does **not** prove duplicate vector-indexing
pipelines: the receiver calls reconcile on the same configured DATA source and
does not send its SQL vehicles in that request.

Correction: the website may keep separate display inventory, as the owner
requested. The AI integration and its external action receiver must use an
explicit provider identity/validation contract; do not validate new provider stock
against an unrelated fixture database or pretend a local insert is an external
provider deal/lead creation.

### F5. Medium: Dependency READY Does Not Prove Indexing Readiness

Receiver initialization accepts at least one local SQL vehicle plus a non-null
connector status response. It does not inspect completed work, errors, freshness
or indexed counts. Successful HTTP can therefore return semantically pending or
failed status without this code rejecting it. Network/HTTP exceptions still fail.

Its subsequent challenge readback attaches a current timestamp to persisted
lifecycle state without rechecking connector sync. Platform validates that scoped
receipt and marks the dependency READY. This proves a scoped lifecycle response,
not successful inventory/index reconciliation. It does not by itself establish
that the full customer go-live scorecard passes.

Evidence:

- [Receiver lifecycle](../../../../../../../../product-demos/autotrader-dealership-demo/backend/src/main/java/com/loomai/demo/dealership/runtime/ManagedDependencyLifecycleService.java): lines 59-65 accept initialization; lines 85-101 read persisted state.
- [Connector operations client](../../../../../../../../product-demos/autotrader-dealership-demo/backend/src/main/java/com/loomai/demo/dealership/runtime/RuntimeConnectorOperationsClient.java): line 43 calls the configured source reconciliation endpoint, not a second stock upload.
- [Platform lifecycle](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/dependency/ManagedDependencyLifecycleService.java): lines 537-539 accept initialization verification and transition to READY.

Correction: use source/binding-scoped reconciliation and indexing-work evidence,
with bounded current readback and failure/freshness criteria. Never infer data
readiness from seeded rows or a new receipt timestamp.

### F6. High: Coolify Name/Conflict Recovery Can Imply Ownership

Follow-up source inspection on2026-10-19 found the analogous risk in the
Coolify adapter. `reconcilePostgresDatabase` can update a same-name database
without a creation receipt, including its configured credentials.
`reconcileApplication` can recover another UUID by name, and
`createWithConflictCleanup` can delete a same-name conflicting application
outside the desired scope after409. Existing handles alone are not a durable
creation-ownership proof.

This is source evidence, not proof that a live foreign database or application
was changed. No destructive reproduction was performed. WorkstreamC4 adds
receipt-checked reconciliation and fail-closed conflicts; independent source
verification and cleanup/retirement review are required before rollout.

## 3. Important Gap: AI Operational SQL Is Not Customer Inventory

Runtime conversations, confirmations, handover, ingestion work and operational
ledgers require durable AI state. Connector sync receipts, fingerprints, webhook
deduplication and approved source projections are also AI integration state.
These are different from inventory/CRM/booking source-of-truth tables.

The owner's vector-only managed-storage statement does not silently authorize
another managed SQL service, and removing the business DB does not remove the
AI durability requirement. This needs an explicit storage ownership decision.

Current Platform support is not a complete BYO operational-SQL contract:

- [Coolify runtime binding](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/CoolifyDeploymentProvider.java): lines 1765-1782 only reconcile the managed `COOLIFY_POSTGRES` mode; a JDBC override is read afterward at 1802.
- Productized deployments require durable target storage at lines 2743-2751.
- [Runtime defaults](../../../../../../../../ai-infrastructure-module/ai-fabric-runtime/src/main/resources/application.yml): line 11 uses file H2, which is not a replacement for the durable product contract.

If BYO SQL is chosen, implement a scoped external database/schema binding and
explicit AI-schema migration permission. Database access or migration permission
does not imply authority to provision, clone or delete customer infrastructure.
Do not claim this option already works end-to-end.

## 4. Examined Areas Not Showing The Same Mistake

- Customer document storage declares customer-managed, read-only ownership and
  prohibits source deletion. The inspected S3 connector lists/stats/downloads
  objects and materializes temporary local copies; it does not create customer
  buckets or mutate/delete source objects. Removing an AI index is separate.
- Shopify document cleanup removes derived runtime documents and local sync
  tracking in the examined flow, not merchant source records. No automatic
  merchant business-database provisioning was found in that inspected path.
- Connector `entity_data`/`content_text` projections are derived evidence with
  synchronization/freshness state. Their presence is not itself a second
  customer business system. Preserve their derived-only contract.
- Optional Basic Inbox is an AI conversation service, not automatically a CRM.
  It remains optional; hosting and its persistence need explicit selection.
- Generic neutral deployment drafts and disabled-by-default sample/ecommerce
  bootstrap are not mandatory customer-system provisioning by themselves.
- An external ProdUS HTTP/MCP binding is not itself a boundary violation.
  The full live ProdUS business-system ownership chain was not independently
  audited here, so it is not globally certified by this finding.

Supporting source: [document storage binding](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentStorageBindingService.java)
lines 106-121; [S3 source connector](../../../../../../../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/documents/S3DocumentSourceConnector.java)
lines 64, 95, 122 and 163; [Shopify derived-document cleanup](../../../../../../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreDocumentSyncService.java)
lines 167-183; [source projection](../../../../../../../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/SourceProjectionQueryService.java)
lines 45-47 and 69-85.

These statements are limited to inspected code paths. They are not an audit of
every published plugin definition, arbitrary volume or third-party account.

## 5. Bounded Live Impact Evidence

Read-only reconciliation at **2026-10-18 22:47:42 UTC** found:

- Both control planes: migration 195; zero tracked managed-dependency graphs.
- Current canary `dep-6e29bab9`: zero releases and zero provider-model allocations.
- Its staging capacity reservation remains `RESERVED`, revision 0; no valid
  cancellation was sent. This investigation did not release or reset it.
- Staging has a separately defined `loomai-dealership-demo-postgres`, demo API,
  external vehicle-provider simulator, Basic Inbox database and AI runtime
  database. These existing demonstration fixtures are not proof of the new
  per-customer package having been instantiated.
- Production's defined databases were the Platform database and two AI runtime
  databases; no dealership demo service matched the bounded service inventory.

The private audit receipt is
`/private/tmp/01044-resource-boundary-audit-20261018.json`. It records zero
provider writes and zero model calls. Coolify health/status fields in that
receipt are control-plane metadata, not independent container-health evidence.
Thirty previously stopped workloads remain preserved; do not restart or delete
them based on stale provider status metadata.

Zero tracked graphs do not prove that every historical/orphan resource is
absent. No live Railway/vector destructive reproduction was performed. No
arbitrary customer volumes or external databases were inspected.

## 6. Execution Handoff And Verification

Use the separate correction plan for implementation. Priorities are:

1. Hold the invalid customer offer and preserve existing data/resources.
2. Fix generic destructive ownership risks in isolated Railway/vector commits.
3. Publish the AI-only composition and external plugin bindings; resolve AI
   operational storage ownership explicitly.
4. Replace split-authority and weak initialization assumptions in the integration
   acceptance path. Keep independent demo application behavior clearly external.
5. Verify no business/provider infrastructure is created by AI customer apply,
   and no external resource is deleted by promotion, retries, export or retirement.
6. Prove real sync, policy retrieval, actions, workspace and handover against the
   externally operated fixture. UI extraction stays last and reversible.

Tests currently reinforce parts of the wrong graph. The offer test expects two
PostgreSQL databases and four managed dependencies, and the publisher requires
the simulator/Inbox/receiver slot set. Passing those tests cannot independently
certify the owner-approved architecture. Replace those expectations and add
negative provider-call assertions independent of the product fixture.

This audit ran no implementation test suite and made no application/framework
changes. Existing test evidence from earlier work is not acceptance of the new
architecture. No framework release is presumed necessary for the topology fix.

Two read-only parallel reviews checked the final plan/audit against their
catalogue/integration and generic lifecycle/storage slices, with no material
consistency findings. This is source-review evidence, not tested remediation.
