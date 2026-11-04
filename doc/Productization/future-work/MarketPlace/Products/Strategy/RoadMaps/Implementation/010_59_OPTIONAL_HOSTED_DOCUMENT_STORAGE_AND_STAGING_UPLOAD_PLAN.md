# 010.59 Optional Hosted Document Storage And Staging Upload Plan

Date: 4 November 2026

Status: IMPLEMENTING; not yet deployed or qualified. See the
[execution record](010_59_HOSTED_DOCUMENT_STORAGE_EXECUTION_RECORD.md).

Inspected source: `Platform-V11`, HEAD `22af1393b0dc60e1fabd3cf3cf604c4f7f8e5d37`

Scope: optional source-file storage, Console uploads, target bindings and the existing document-indexing/promotion journey.

## 1. Owner Decision And Policy Change

The owner now permits **optional LoomAI-provided S3 storage for knowledge files**.
This explicitly supersedes the customer-owned-storage-only product restriction
in [010.24](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md:30).
BYO S3-compatible storage remains supported. This is a deliberate product-policy
change, not a workaround or permission to reuse a private canary bucket.

The agreed product contract is:

- One bucket per **customer-owned knowledge storage connection**, not per
  deployment, runtime, release, target or uploaded file. Normally the customer
  starts with one connection and reuses it for compatible owned deployments.
- Customer staging and production use the **same bucket**. Purpose-based subpaths
  are configurable; separate environment buckets are not the default.
- The customer selects hosted storage or BYO during Kit/deployment staging setup
  and can upload supported files through Console when hosted storage is chosen.
- Runtime connectors receive scoped **read-only** credentials. Only the trusted
  Console backend has upload authority; AI chat, Engage and Desk do not acquire it.
- Reuse the existing DATA/knowledge/indexing flow. Source bytes remain in S3;
  parsing, chunking and indexing remain deployment-local.
- No per-deployment business database, permanent file folder or replacement
  document engine. Optional AI-state storage remains a separate choice.
- Promotion reuses source objects and reindexes into the production target's
  own evidence namespace. **No vector-record copy, transcript copy or bucket copy.**

Here "staging" means a customer's staging target, normally managed by production
Console. It does not mean that a developer staging control plane automatically
shares production customers' buckets, credentials or ownership records. Separate
control planes retain separate provider namespaces unless deliberately connected.

The canonical 010.24 plan and current operating guidance now record this policy
change and point here. During implementation, update customer-facing copy too.
Historical implementation evidence remains unchanged; it does not prove the new
hosting/upload capability is live.

## 2. Current Code: Reuse And Actual Gaps

### Target Binding Is Not A Shared Storage Connection

Current `DeploymentDocumentStorageBindingService` stores one
`EXTERNAL_DOCUMENT_STORAGE_BINDING` resource handle per deployment/target.
Its upsert lookup is `(deploymentId, targetProfileId, resourceKind)` and its
provider reference is `external-document-storage:<deployment>:<target>`.
S3 endpoint, region, bucket, prefix and credentials are materialized through
deployment/target-named secret references. No bucket is provisioned by this service.

`slot:document-storage` is a **logical composition slot**. The resolver requires
exactly one configured handle for the selected deployment and target. It checks
ownership and target equality, and all document datasets in a runtime must resolve
to that same handle. It is not a customer-global bucket ID or permission grant.

Keep this target resolution. Add the shared customer connection **above** it;
project the connection into ordinary stage/prod handles. Never attach a staging
handle ID directly to production or widen the resolver to ignore target identity.

Sources: [binding upsert](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentStorageBindingService.java:87),
[handle identity](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentStorageBindingService.java:174),
[slot resolution and single binding](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentBindingResolver.java:33).

### Existing Release Evidence Must Remain Target-Specific

The resolver freezes handle metadata and secret revision timestamps into the
release's `document_binding_snapshot_json` and fingerprint. Promotion requires
the staging binding to remain unchanged and separately snapshots the production
binding. Runtime source scope includes customer, tenant, deployment, target,
binding and physical endpoint/region/bucket/prefix identity.

Therefore, stage and prod may use the same physical bucket/prefix while retaining
different handles, secret references, runtime source IDs and indexing evidence.
Changing a bound prefix/credential scope requires the normal new-release path;
uploading a file does not itself change the binding.

Current document secrets use target names but `putManagedSecret` writes them via
the deployment-scoped secret API. For new hosted projections, use the already
available `upsertTargetManagedSecret` with real target metadata. Do not treat a
name containing the target as sufficient secret ownership.

Sources: [snapshot/freeze](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentBindingResolver.java:57),
[promotion candidate](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentPromotionCandidateService.java:33),
[runtime scope](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/documents/DocumentSourceScope.java:19),
[current document secret writer](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentStorageBindingService.java:395),
[target secret API](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/secret/service/PlatformSecretService.java:147).

### Indexing Exists; Upload And Hosting Do Not

Runtime S3 supports list/head/get, provider revision evidence, bounded temporary
materialization, preview, registration, index/refresh/reconcile, active versions
and exact index removal. Platform already authorizes/proxies those operations.
The connector has no source write/delete operations. Preserve that contract.

`DocumentKnowledgePage` has the BYO S3 form, binding list, discovery, approval,
preview, index/work state and retrieval proof. It has **no file upload control**.
Its source-binding form can be used before a release, but document operations
require an explicitly provisioned target/release. The customer-focused knowledge
page mainly selects deployed targets and opens this existing editor in a panel.
There is no inspected document bucket provisioner or upload API in Platform.

Sources: [read-only S3 connector](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/documents/S3DocumentSourceConnector.java:55),
[existing operations](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentKnowledgeOperationsService.java:93),
[existing editor](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/pages/DocumentKnowledgePage.tsx:580),
[customer knowledge wrapper](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/pages/CustomerKnowledgeConnectionsPage.tsx:1).

## 3. Proposed Resource Model

### Shared Connection

Add a small Platform metadata resource, proposed name `DocumentStorageConnection`,
owned by the current customer/workspace. Use the existing control-plane database,
not a new database/service. Fields should include:

- ID, customer/tenant ownership, display name, revision and status.
- `mode`: `LOOMAI_HOSTED_S3` or `CUSTOMER_S3_COMPATIBLE`.
- Provider reference, approved region/endpoint, bucket identity/expected account
  owner, allowed root and named purpose-prefix mappings.
- Provider receipt/tags, credential references, verified security/versioning
  posture and created/updated actor/time. Never return secret values.

A connection is reusable only by explicitly authorized deployments of that same
customer. Reusing source bytes does not enable cross-deployment vector retrieval.
For BYO, existing target-specific bindings continue to work; optional adoption
into a shared connection is explicit, not a forced storage migration.

### Target Projection

Reuse the existing resource handle and `slot:document-storage`. Add safe metadata:
`storageConnectionId`, `storageMode`, selected purpose collection/effective prefix,
and the projection's own revision. Bind the selected connection to each target
using normal owned service calls and read-only secret materialization.

Do not place mutable connection-wide `updatedAt`, upload counts or an unrelated
purpose's revision in the release fingerprint. Freeze the **selected physical
scope and credential generation**. Uploading one file or adding an unused purpose
must not invalidate all customer releases. Preparing production must not rotate
the already-verified staging credential as a side effect.
An unchanged prepare/bind replay must read back the existing projection, not
call today's unconditional upsert and increment binding/secret timestamps.

### Upload Receipt, Not Another Ingestion Manifest

Persist a small upload/operation record in Platform: customer/connection/purpose,
operation key and request fingerprint, generated object key, original safe name,
length/media type/checksum, S3 version/ETag, state and timestamps. Target indexing
receipts can reference this record and the existing runtime source/work IDs.
Do not duplicate chunks, extracted content, queue state or canonical manifests.

Use a unique object key for each accepted new upload. An example layout is:

```text
One bucket: loomai-docs-<opaque-connection-id>
  knowledge/customer-help/policies/<upload-id>/delivery.txt
  knowledge/customer-help/operations/<upload-id>/contact-process.txt
  knowledge/staff-only/runbooks/<upload-id>/internal-checks.txt

Staging binding:    same bucket + knowledge/customer-help/
Production binding: same bucket + knowledge/customer-help/
```

The bucket is not duplicated by environment. `policies`, `operations` and other
subpaths represent purposes. Display names may be editable; authority comes from
server-owned connection/purpose mappings, not a browser-supplied raw object key.
Prefix changes use canonical normalization and bounded relative paths.

**Current limitation:** one effective connector prefix per runtime, shared by all
its document datasets. For the first patch, select one purpose collection with a
common approved root; use subfolders/allowed metadata for categories. Do not
promise arbitrary unrelated bucket/prefix connections per dataset. Staff-only
content must not sit under the public binding's read scope. Purpose labels do
not replace existing public-approval metadata or knowledge-source audience rules.

## 4. Console Journey And Upload Contract

### Customer Journey

1. Create/select a compatible Kit or staging deployment. Kit creation stays
   configuration-only; it must not provision AWS inside its existing transaction.
2. In **Your business information / Documents**, choose **Use LoomAI storage**
   or **Connect my S3-compatible storage**, or reuse an owned connection.
3. Explicitly choose **Prepare storage**. Console provisions or validates the
   connection and projects the staging binding. Show bucket/purpose summary,
   actual status and an actionable failure, not AWS keys or provider topology.
4. Upload supported files, including before the first runtime is running. Show
   **Uploaded; not indexed** until a target registration/index receipt exists.
5. After deployment, select files and their audience/category, preview and choose
   **Index selected**. Reuse existing registration/index/reconcile/retrieval proof.
6. Test answers on staging. Review the exact document selection for production,
   prepare the production binding, apply/index/verify and explicitly activate.

Reuse [Kit overview document entry](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/components/kits/ExperienceKitOverview.tsx:82),
[deployment setup](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/components/customer/CustomerDeploymentConsole.tsx:1050),
and `DocumentKnowledgePage`. Extract only the shared storage picker/upload section
needed in draft and live views; do not duplicate the document editor or redesign
the Console navigation. Keep admin/operator functionality intact.

### Minimal Upload Transport

First release: a bounded authenticated Console-backend upload endpoint using the
normal customer session/Supabase authorization and CSRF/CORS conventions. Stream
the small supported object to S3; do not parse, embed or index it in the control
plane. Respect the selected document plugin's actual source-size/type limits
(current generic/Support defaults: 1 MiB, `.txt` and `.json`). No PDF/OCR/Office
claim is added by an upload button.

Proposed API scope (names are new, not existing routes):

```text
GET  /api/document-storage/connections
POST /api/document-storage/connections             # record owned configuration
POST /api/document-storage/connections/{id}/prepare
POST /api/document-storage/connections/{id}/bind-target
GET  /api/document-storage/connections/{id}/uploads
POST /api/document-storage/connections/{id}/uploads # bounded multipart/stream
POST /api/deployments/{dep}/document-knowledge/prepare-selection
```

Mutations use existing idempotency conventions and expected revisions. Upload
authority requires connection ownership and deployment/purpose attachment;
knowing a connection ID is not enough. A `targetProfileId` in a body must be
validated against the owned deployment's actual allowed target, not trusted.
Return safe object/receipt metadata, never credentials or an executable S3 URL.

Console backend holds write authority. Customer chat/LLM, runtime, connector,
Engage bootstrap, Desk and public scripts never receive it. A failed/oversized or
uncertain upload must not become indexable based solely on browser success.
Use generated keys, checksum/HEAD readback and conditional creation; a retry
reconciles the same operation/key instead of creating duplicate files.

BYO remains read-only by default. Do not reuse its reader key for upload or infer
write permission. The first patch can leave BYO source uploads in the customer's
storage tool; separately authorized BYO Console writes are an explicit later
extension, not a requirement to keep BYO indexing working.

Direct presigned browser upload is unnecessary for these small files and is
deferred. If added later, it must be one exact key with short expiry and bounded
length/checksum plus completion readback; a presigned URL is reusable bearer
authority, not automatically single-use. It must not broaden runtime authority.
[AWS presigned URL behavior](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html).

### Upload Is Not Publication Or Deletion

Uploading creates source bytes only. It does not index automatically, publish a
release, grant `PUBLIC_APPROVED`, alter production selection or activate an app.
The existing templates default trusted auto-indexing OFF; preserve that during
qualification. Public approval remains an explicit authorized choice.

Use append-only generated keys initially. A replacement is a new upload selected
and indexed before the old indexed source is removed. A different key is a new
runtime source, **not** the old source's numeric version. Existing same-key BYO
refresh/versioned replacement remains unchanged. Do not build a second document
version engine or silently overwrite a production-used source in this patch.

Existing **Remove from AI knowledge** still deletes only exact indexed chunks.
It must never become S3 `DeleteObject`. Bucket/source deletion is separate from
deployment removal and must check references from all targets/deployments. First
release can retain source objects and expose explicit staff-assisted connection
retirement; document retention/ownership honestly instead of automatic bucket
emptying. Runtime temporary files remain bounded scratch, not permanent originals.

## 5. Plugin Contract Delta Without New Plugin Types

Reuse these actual first-party DATA contributions and their existing knowledge
source declarations:

- `mkp-data-document-knowledge-s3`: dataset `document-knowledge`, source
  `document-knowledge`, generic `deployment-private-vector` retrieval.
- `mkp-data-dealership-knowledge-v1`: dataset `document-knowledge`, source
  `dealership-public-document-knowledge`, `PUBLIC_APPROVED` filter.
- `mkp-data-ai-support-documents`: dataset `support-documents`, source
  `approved-support-documents`, `PUBLIC_APPROVED` filter.
- `mkp-data-document-knowledge-mounted-demo` and dealership mounted-demo variant
  remain distinct optional small/demo-folder connectors; hosted S3 is not a mount.

No `STORAGE` or `DOCUMENT` plugin type and no new vector store adapter are needed.
Source: [generic DATA definition](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/db/migration/V148__document_knowledge_marketplace_products.sql:128),
[dealership definition](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/db/migration/V152__dealership_public_document_knowledge_packages.sql:117),
[Support definition](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-templates/ai-support-workspace/manifests/support-documents.manifest.json:38).

There is a real code constraint: manifest and draft validators currently require
`storageScope=CUSTOMER_MANAGED` and source `storageOwnership=CUSTOMER_MANAGED`.
The binding service also hardcodes that owner label. Simply attaching a hosted
bucket under those labels would misrepresent the new product.

Recommended small semantic extension for new DATA revisions:

```text
storageScope = CUSTOMER_SCOPED
sourceConnector.storageOwnership = BOUND_CONNECTION
binding safeMetadata.storageOwnership = CUSTOMER_MANAGED | LOOMAI_MANAGED
```

`CUSTOMER_SCOPED` describes data ownership/isolation; `BOUND_CONNECTION` defers
physical operator identity to the verified binding. The resolved operator mode
must be frozen in release metadata and shown in UI. Keep the same S3 connector,
`EXTERNAL_DOCUMENT_STORAGE`, `DEPLOYMENT_ONLY`, `VERSIONED_REPLACE`,
`allowedOperations=[LIST,READ,HEAD]` and `deleteSourceOnRemoval=false`.
Existing immutable BYO-only revisions retain their true contract; publish new
revisions for the hosted-or-BYO choice and update current template defaults
through the normal version lifecycle. This is not an old-runtime compatibility
path or permission to rewrite published releases.

Affected validation/compiler points:
[manifest validator](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceManifestService.java:3904),
[draft validator](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDraftValidationService.java:831),
[source connector compilation](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/DeploymentMarketplaceDraftCompilerService.java:2606),
[customer binding authorization](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/service/CustomerSelfServiceOperationBoundary.java:126).

Implementation inspection correction: `CUSTOMER_S3` identifies the existing
bootstrap/BYO slot; current ownership authorization does not make it a lifetime
restriction on later bindings. The new owned connection API and UI provide the
mode-neutral hosted-or-BYO choice and project into the same logical slot, without
inventing a second plugin/binding authorization system. Existing BYO slot
configuration remains supported. Actual DATA declarations and frozen binding
metadata identify storage ownership accurately. Do not use a staff credential to
bypass customer ownership or treat an old offer's wording as a permanent limit.

## 6. Promotion: Same Bucket, Fresh Target Evidence

Keep the existing practical promotion mechanism. It applies the verified source
version/image to production and switches the consumer only after verification.
Add a small document-selection preparation step around the existing commands:

1. Resolve the owned shared connection and explicit production target. Create a
   production binding/reader projection if missing; reuse the same bucket.
2. Freeze the production prefix/credential scope and the approved upload/source
   selection in the promotion candidate. Same bucket does not require the same
   purpose prefix if the customer deliberately chooses another approved scope.
3. Apply the normal production release. Register the selected original object
   references in that production runtime, preview/index and reconcile the real
   work IDs to ACTIVE. A missing release before apply is not missing storage.
4. Run existing release verification and document retrieval proof against actual
   production source/version/target evidence. Then explicitly activate/rebind.

The inspected promotion/verification code does **not** automatically discover,
register and index missing source files; it checks existing ACTIVE sources.
Do not claim same-bucket binding alone closes promotion. Implement the small
`prepare-selection` orchestration with stored operation receipts, reusing
`DeploymentDocumentKnowledgeOperationsService`. It selects an exact reviewed
list, not an unbounded automatic discover/index-all. Existing partial work is
reconciled; do not blindly resubmit uncertain indexing writes.

Source bytes stay in place. Source IDs, manifests, indexing work and vector
ownership remain production-local. If a shared source is uploaded later,
production is unchanged until an authorized indexing/selection update. No
new files should appear in production because staging happened to discover them.

Sources: [apply/activation](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentPracticalPromotionService.java:154),
[current ACTIVE-source check](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseVerificationService.java:1121),
[preserved storage resources](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/model/PreservedExternalBindingKinds.java:1).

## 7. Provider Configuration And Authority

Add an optional backend-only AWS S3 provider configuration: enabled flag,
approved provider/account reference, region allowlist/default, bucket naming/tag
namespace, storage root defaults, upload limit/deadline and credential references.
Default disabled until configured. No browser-selected AWS account/endpoint or
arbitrary provider-role/policy document. Use the existing provider/secret/audit
patterns; do not add a capacity service or dependency container for a bucket.

The provider must tag a bucket with control-plane, customer and connection IDs
and record its returned identity. A name collision never authorizes adopting an
unowned bucket. Prepare is idempotent and readbacks actual configuration. Missing
permissions produce a visible setup error; record partial provider resources and
allow bounded reconciliation/manual operator assistance, not a new recovery engine.

### Permission Separation

- **Provisioner, Console backend only:** create/configure buckets within the
  approved namespace; block public access, enforce bucket-owner ownership/ACLs
  disabled, configure encryption/versioning/tags/TLS policy and read back state.
  If it issues IAM reader identities, restrict identity names/policies to the
  document-reader namespace. No general IAM administrator or wildcard PassRole.
- **Uploader, Console backend only:** put and inspect exact owned connection
  keys, with generated paths/checksums. No bucket policy changes from an ordinary
  upload, and no source deletion permission in the basic upload path.
- **Connector reader:** bucket list limited by authorized prefix, object
  read/head in that scope; version reads when versioning is enabled. Include
  bucket versioning-state read only where the actual preflight uses it. No put,
  delete, ACL, policy, user/key management or access to another customer's bucket.
- **Browser/user:** normal Console API permission only; no AWS keys. Anonymous
  Engage, chat actors and Desk agents have no upload authority.

Use SSE-S3 and HTTPS as the simple initial hosted default; require Block Public
Access and bucket-owner-enforced Object Ownership. Enable S3 versioning for new
hosted buckets, accurately project its state and account for version-read
permissions. It is source protection, not a replacement for active AI document
versions. Do not change versioning on existing BYO/canary buckets silently.
Do not expire current/shared source objects with an automatic short lifecycle.
[AWS S3 security guidance](https://docs.aws.amazon.com/AmazonS3/latest/userguide/security-best-practices.html),
[S3 versioning](https://docs.aws.amazon.com/AmazonS3/latest/userguide/Versioning.html).

Prefer role/temporary credentials where an actual refresh path is available.
The current runtime constructs a **static** provider for supplied access/session
keys and the target binder requires keys. Injecting short-lived STS credentials
without refresh would break the runtime later. A narrowly scoped dedicated
reader key with explicit rotation through target bindings is a bounded initial
option; refreshing role credentials is a separate reviewed private-runtime
enhancement, not an assumed capability or required framework release.
[Current credential provider](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/documents/DocumentConnectorConfiguration.java:35).

IAM requirements above are a responsibility boundary, not a deployable policy.
During implementation, derive the policy from the actual SDK operations and
review exact bucket/prefix/reader-identity restrictions against AWS's operation
reference. No additional policy-generator dependency or service is required.

No account/region values or secrets were inspected in this analysis. Verify API
permission mappings from [AWS's operation reference](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-with-s3-policy-actions.html).
Conditional object creation is documented by
[AWS conditional writes](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-writes.html).

## 8. Minimal Next Code Patch And Phases

Implementation is in progress; use the execution record for phase evidence.
Keep changes scoped and sequential through one real
customer journey. No broad framework matrix, database-per-deployment expansion,
capacity/drain redesign or new object-storage microservice.

### P1. Shared Connection And Target Projection

- New bounded `backend/documentstorage` package, metadata migration for
  connections/uploads and provider interface with an AWS implementation; add
  the necessary AWS SDK v2 backend dependencies/configuration.
- Owned connection list/create/prepare/bind commands with operation/revision
  checks; maintain bucket identity and prefix mappings independent of targets.
- Extend `DeploymentDocumentStorageBindingService` for trusted connection
  projection, accurate ownership metadata and target-bound reader secrets.
- Update resolver snapshot inputs narrowly, marketplace validation/compiler,
  customer document-binding contracts/scope and current DATA/template revisions.
- Preserve existing BYO endpoint/private-network policy and demo mount behavior.

Acceptance: two targets resolve separate correct handles to one bucket; foreign
customer/target/secret references are denied; an ordinary upload or unused-purpose
edit does not drift a frozen release. BYO still compiles and binds. No provision
occurs on configuration-only Kit creation. Real PostgreSQL migration/ownership
and provider-client contract tests pass with no AWS operations in unit tests.

### P2. Draft Upload And Existing Indexing UX

- Implement the bounded upload/receipt path and exact-object readback.
- Add a reusable connection/purpose picker and upload list in Kit/deployment
  document setup; reuse the existing live document editor for preview/indexing.
- Expose distinct statuses: Storage ready, Uploaded, Not indexed, Indexing,
  Active on staging, Active on production, Failed. UI must derive them from
  actual receipts, not a successful upload alone.
- Implement selected-file preparation using existing register/preview/index/
  reconcile APIs. Preserve explicit approval for anonymous/public knowledge.

Acceptance: a customer can upload before the first release, then index after
apply; oversize/wrong-type/path traversal/foreign purpose fails before accepted
storage; duplicate/uncertain submission does not create extra files; runtime
credentials cannot upload/delete; real query retrieves the approved uploaded
document. No AI-semantic classifier/keyword validator is introduced.

### P3. Production Promotion And Live Proof

- Include exact selected source references and connection/projection revision
  in the existing promotion review and candidate evidence.
- Prepare production binding to the same bucket, apply, index selected original
  files, run real retrieval verification and explicitly switch the app.
- Exercise deployment retirement without deleting shared storage; keep a clear
  retained-source/connection summary. Update current guides and 010.24 policy.

Acceptance: a fresh Kit/deployment UI journey shows one physical bucket and
separate stage/prod handles/work/evidence. Production sees selected approved
files only. Staging-only uploads do not appear automatically. A wrong-purpose or
foreign-customer document is unavailable; shared source survives either target's
retirement. There is no vector copy, source-folder mount or extra business DB.
Record exact source/image/release/assignment/provider receipts and browser proof.

### First Patch Boundary

Start with P1 and the narrow upload API/P2 shared component for generic or Support
documents, then one end-to-end P3 Kit journey. Dealership and future Shopify use
the same capability after their template revisions are published. Keep existing
working S3 connections untouched. Do not create a special upload implementation
per Kit or hold the first proof for every possible object-store provider.

## 9. Evidence And Completion Checklist

- Existing suites to extend: `DeploymentDocumentStorageBindingServiceTest`,
  `DeploymentDocumentBindingResolverTest`, `CustomerDocumentStorageBindingServiceTest`,
  document marketplace/draft validation, target secret materialization,
  promotion candidate/service tests and real PostgreSQL migration tests.
- UI suites: `customer-document-storage-scope.test.mjs`,
  `customer-auth-document-authority-ui-smoke.mjs`,
  `document-knowledge-target-race-smoke.mjs`, Kit creation/edit and customer
  knowledge setup. Test both customer authentication modes already supported.
- Provider tests: same operation returns same connection/bucket; collision/foreign
  tags deny adoption; permission failure is explicit; security/versioning readback;
  upload checksum/length/type and retry behavior; no source delete in indexing.
- Hosted proof: actual isolated approved provider identity, one customer bucket,
  owned Console upload, real staging/production connector denials for write/foreign
  scope, ACTIVE work and grounded answer on both targets. Do not mistake local
  mocks or HTTP 200 alone for IAM/indexing proof.
- Operational notes: approved region, provider credentials, storage/cost terms,
  basic retention/export/retirement ownership and expected source limits. Obtain
  these before enabling the provider; do not inspect or alter existing private
  credentials as part of this documentation task.

**Done means:** hosted S3 is an optional truthful choice beside BYO, customer
uploads work from staging setup, shared source storage survives target lifecycle,
and the existing indexing/promotion path proves the selected knowledge on each
target. It does not mean a general-purpose file manager, antivirus/OCR platform,
unlimited storage plan, cross-customer source sharing or a new managed database.

## 10. Analysis Evidence And Non-Actions

This document is based on inspected source and official AWS documentation checked
4 November 2026. No AWS account lookup, secret query, bucket/IAM provisioning,
upload, code change, test execution, service rollout or framework modification
was performed. All proposed identifiers/endpoints/classes are labelled above;
they are not claims of existing functionality.
