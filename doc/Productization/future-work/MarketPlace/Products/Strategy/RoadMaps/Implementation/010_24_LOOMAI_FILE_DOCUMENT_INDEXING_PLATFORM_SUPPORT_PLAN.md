# 010.24 LoomAI File Document Indexing Platform Support Plan

Status: `IMPLEMENTED_HOSTED_PROVEN`; document-specific staging and controlled-production gates are green; global Platform market-readiness remains qualified by unrelated deferred Shopify failures

Created: 2026-09-22

Current supported baseline:

- AI Fabric `0.8.4` only
- private LoomAI runtime and Platform `Platform-V11`
- V04 deployment lifecycle
- current-only greenfield policy; no older framework/runtime compatibility path

Related architecture and framework evidence:

- `010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md`
- `010_22_AI_FABRIC_0_7_0_LOOMAI_PLATFORM_PRODUCTIZATION_IMPLEMENTATION_PLAN.md`
- `010_23_LOOMAI_DEPLOYMENT_BEHAVIOR_MARKET_READINESS_EXECUTION_PLAN.md`
- AI Fabric `docs/planning/0010-document-indexing-core-support-plan.md`
- AI Fabric `docs/release-notes/0.8.0.md`
- AI Fabric `docs/Framework-Dev-Guides/retrieval-vectorization/LOOMAI_AI_FABRIC_0_8_DOCUMENT_INDEXING_MIGRATION_RUNBOOK.md`
- AI Fabric `examples/real-apps/document-ingestion-workbench`

## 1. Executive Decision

LoomAI will productize AI Fabric's governed document-indexing core as a reusable
Platform capability named **Document Knowledge Operations**.

It is:

- a capability pack that can be attached to a compatible deployment;
- represented by the existing Marketplace `DATA` contribution type;
- selected by existing Marketplace `TEMPLATE` plugins;
- compiled through the existing deployment draft and V04 version lifecycle;
- executed inside each deployment's private runtime;
- connected to customer-provided source storage through a versioned DATA
  plugin contract;
- backed by the deployment's existing embedding and vector provider choices;
- verified through a dedicated reusable verification pack; and
- operated from the existing deployment workspace in Platform UI.

It is not:

- a fourth deployment behavior;
- a new Marketplace plugin type;
- a second deployment lifecycle;
- a Platform-central document-processing service;
- a LoomAI-managed object-storage product;
- a replacement for structured Data Sync;
- a replacement for external retrieval connectors;
- a generic document-management system;
- a crawler, OCR service, or PDF processing claim; or
- evidence that every file format is supported.

The production source of truth is customer-provided object storage. An
operator-provided folder may be mounted into a deployment for small datasets,
internal canaries, and demos, but it is not a managed customer storage feature
or a horizontally scalable production claim.

The first supported release is intentionally bounded to trusted text and JSON
files. Markdown may be accepted later as plain text only after explicit
fixtures prove the behavior. HTML, PDF/Tika, Office formats, OCR, images,
arbitrary URLs, and remote crawling are outside the first release.

## 2. Product Position

Document Knowledge Operations lets a Platform user add approved file knowledge
to a self-contained LoomAI deployment, preview how it will be represented,
index it safely, retrieve it with source evidence, replace it without losing the
working version, and delete its exact indexed chunks.

The customer keeps ownership of the original file and its backup, retention,
encryption, residency, and source deletion policy. LoomAI stores only the
connector binding, source reference/version evidence, content-free manifests,
work status, and indexed vector projection needed to provide the capability.

This is a reusable capability, not a task-specific customer product. Customer
products can use it from any compatible behavior:

| Deployment behavior | Document capability use |
| --- | --- |
| `CONVERSATIONAL` | Ground answers in approved files and cite source/version evidence. |
| `AGENTIC_SPECIALIST_TEAM` | Give approved read-only specialists access to deployment-owned document evidence. |
| `SMART_BRAIN` | Let approved event/scheduled analysis read active document evidence without centralizing document traffic. |

The first hosted canary attaches it to `CONVERSATIONAL`. Agentic and Smart Brain
compatibility may be claimed only after their own retrieval and isolation gates
pass. The capability must never widen a behavior's activation, execution, or
write authority.

## 3. Current Baseline And Gap

### 3.1 Framework capability already available

AI Fabric `0.8.4` includes the document-indexing core introduced in `0.8.0`:

- Spring AI `DocumentReader`, transformer, and token splitting integration;
- trusted file/classpath/in-memory resource policy;
- text and JSON readers;
- bounded document/chunk/content/metadata preparation;
- `DocumentIngestionPlan`;
- content-free `DocumentIngestionManifest`;
- deterministic source, version, chunk, entity, and fingerprint identity;
- protected and allowlisted metadata;
- canonical `AIIndexDocument` projection;
- existing durable indexing queue submission;
- `IndexingWorkQuery` reconciliation;
- exact manifest-based deletion; and
- retrieval source/version/chunk evidence.

The framework intentionally does not own source storage, connector credentials,
operator state, upload APIs, lifecycle persistence, approval, quotas, billing,
retention, or Platform UI.

### 3.2 LoomAI capability already available

LoomAI already has the primitives needed around the framework core:

- Marketplace `TEMPLATE`, `DATA`, `ACTION`, `INFERENCE_PROFILE`, and governed
  `SPECIALIST` contribution lifecycles;
- deployment template bootstrap and exact installed plugin versions;
- V04 draft, validation, immutable version, release, apply, verification,
  promotion, rollback, export, and import;
- deployment-local private runtime and verified auth context;
- deployment-local PostgreSQL for durable runtime state;
- managed embedding/vector profiles and deployment resource handles;
- Data Sync and indexing work-status administration;
- knowledge-source and entity configuration;
- runtime source-capability manifests and exact image/source readback;
- Platform release verification suites and deployment workspace UI; and
- provider secret binding and target-profile placement.

### 3.3 Implemented LoomAI productization

The private runtime and Platform now implement the first bounded release:

- direct AI Fabric document-indexing dependencies and configuration;
- deployment-local S3-compatible and mounted-folder source connectors;
- source, version, content-free manifest, exact chunk, work, and idempotency
  persistence;
- connector status, discover/register, preview, index, refresh, reconcile,
  retrieval-proof, exact-delete-index, and internal-retention APIs;
- active-version filtering on deployment-private vector retrieval;
- Marketplace `EXTERNAL_DOCUMENT_STORAGE` DATA contracts and a compatible
  Conversational TEMPLATE;
- target-scoped customer-storage bindings and demo-folder mount reconciliation;
- document capabilities, endpoint classes, migration, and verification pack in
  runtime capability readback;
- deployment workspace operations and Platform UI; and
- the `document-knowledge-operations-v1` reusable verification suite.

This is source-complete and locally verified. It is not yet a hosted-production
claim: external S3-compatible staging and controlled-production canaries,
isolation/restart/failure/lifecycle evidence, and the full live Platform release
gate remain mandatory.

Existing Marketplace and Shopify records called "documents" are already-shaped
Data Sync records. They do not prove raw file parsing, chunk manifests, file
version replacement, or exact chunk deletion.

## 4. Platform Primitive Mapping

The feature must reuse these primitives exactly:

| Concern | Existing primitive | Planned use |
| --- | --- | --- |
| Customer behavior | Deployment behavior catalogue | Attach document knowledge without defining a new behavior. |
| Starter composition | Marketplace `TEMPLATE` | Select compatible behavior, required DATA and inference plugins, runtime capability IDs, endpoint classes, migration IDs, and verification packs. |
| File knowledge declaration | Marketplace `DATA` | Add one mode-specific contribution that binds customer source storage to deployment-local indexing. |
| Embeddings | `INFERENCE_PROFILE` and managed inference profiles | Use the deployment's selected embedding provider and dimensions. |
| Vector persistence | Existing vector profile/resource | Use the selected AI Fabric vector provider; never create a Spring AI `VectorStore` path. |
| Searchable projection | V04 entity configuration | Define the `document` entity, searchable content, and protected metadata. |
| Retrieval exposure | Knowledge-source configuration | Bind active document evidence into existing RAG/specialist retrieval. |
| Source storage | DATA source connector plus external resource/secret binding | Bind customer-owned object storage; never provision it as a LoomAI storage product. |
| Demo source folder | Target-profile mount capability | Permit a bounded operator-mounted folder only for small datasets, canaries, and demos. |
| Secrets | Platform secret and deployment binding | Bind customer-provided, least-privilege storage credentials by reference. |
| Runtime state | Deployment-local PostgreSQL | Persist sources, manifests, exact chunk IDs, and work reconciliation. |
| Runtime contract | Source capability manifest | Attest document capabilities, endpoints, migration, and verification pack. |
| Immutable composition | V04 draft/version/release | Hash document policy, plugin version, source-connector contract, entity config, and provider bindings. |
| Operations | Deployment workspace | Show sources, active versions, work status, failures, retrieval proof, and safe commands. |
| Promotion/rollback | Existing release lifecycle | Promote config and binaries; bind production to an explicitly approved customer source. |
| Portability | Existing export/import | Export connector configuration and references; customer source objects remain outside the bundle. |
| Release confidence | Existing verification suites | Add a document-specific pack and include it when a template claims the capability. |

No `DOCUMENT` plugin type, document deployment table, or parallel release
controller will be introduced.

## 5. Marketplace Contract

### 5.1 DATA contribution extension

Extend the existing dataset union with these current values:

- `storageScope = CUSTOMER_MANAGED`
- `sharingScope = DEPLOYMENT_ONLY`
- `ingestionMode = EXTERNAL_DOCUMENT_STORAGE`
- `updateStrategy = VERSIONED_REPLACE`

Existing modes remain valid for their distinct current use cases:

- `PACKAGED_SEED`
- `EXTERNAL_SYNC_SQL`
- `EXTERNAL_SYNC_FOLDER`

`EXTERNAL_SYNC_FOLDER` must not be reinterpreted as file document indexing. It
prepares Platform-side records for Data Sync. `EXTERNAL_DOCUMENT_STORAGE`
keeps original bytes in customer storage and performs parsing/indexing in the
deployment data plane.

### 5.2 Mode-specific document policy

An `EXTERNAL_DOCUMENT_STORAGE` dataset must declare a `sourceConnector` and a
`documentPolicy`.

The source connector declares:

- `connectorType = S3_COMPATIBLE_OBJECT_STORAGE` for production; or
- `connectorType = MOUNTED_FOLDER` for bounded small-data/demo use;
- `storageOwnership = CUSTOMER_MANAGED`;
- a binding-ref field for endpoint/bucket/prefix/credential resolution;
- read/list authority only by default;
- whether provider object version IDs are available; and
- `deleteSourceOnRemoval = false`.

The document policy declares:

- exact allowed media types and extensions;
- maximum source bytes;
- maximum sources per deployment;
- maximum indexed sources and total indexed bytes per deployment;
- framework document/chunk/content bounds;
- approved metadata keys;
- visibility policy;
- retention policy for content-free manifests and operation evidence;
- preview character and chunk limits;
- whether operator confirmation is required before initial indexing;
- whether trusted application auto-indexing is allowed.

The first released policy allows only:

- `text/plain` using the Spring AI text reader; and
- an explicitly configured `application/json` content-key contract using the
  Spring AI JSON reader.

Client-supplied MIME type is advisory. Runtime validates extension, content
type, size, and reader selection using server-owned policy.

### 5.3 Illustrative DATA manifest fragment

```json
{
  "contributions": {
    "datasets": [
      {
        "datasetId": "company-file-knowledge",
        "entityType": "document",
        "storageScope": "CUSTOMER_MANAGED",
        "sharingScope": "DEPLOYMENT_ONLY",
        "ingestionMode": "EXTERNAL_DOCUMENT_STORAGE",
        "updateStrategy": "VERSIONED_REPLACE",
        "vectorizationProfile": "document-knowledge-default",
        "handleTemplate": "documents/{deploymentId}/company-file-knowledge",
        "sourceConnector": {
          "connectorType": "S3_COMPATIBLE_OBJECT_STORAGE",
          "storageOwnership": "CUSTOMER_MANAGED",
          "bindingRefField": "documentStorageBindingRef",
          "deleteSourceOnRemoval": false
        },
        "documentPolicy": {
          "allowedMediaTypes": ["text/plain", "application/json"],
          "maxSourceBytes": 1048576,
          "maxSources": 100,
          "maxTotalIndexedBytes": 104857600,
          "maxChunksPerSource": 100,
          "maxChunkCharacters": 8000,
          "maxTotalCharacters": 250000,
          "previewMaxChunks": 10,
          "previewMaxCharactersPerChunk": 500,
          "evidenceRetentionDays": 30,
          "commandRetentionDays": 30,
          "retentionBatchSize": 100,
          "allowedMetadataKeys": ["originalFilename", "locale", "sourceCategory"],
          "initialIndexRequiresConfirmation": true
        }
      }
    ]
  }
}
```

This is the target contract for implementation. The published manifest schema,
parser, database projection, draft compiler, and tests must agree before a
plugin is published.

### 5.4 Template composition

Publish one reusable starter template only after the DATA plugin is proven:

- behavior: `CONVERSATIONAL`;
- required DATA plugin: external document storage;
- required inference profile: generation plus embeddings;
- required vector profile;
- required runtime capabilities: `document-ingestion-core`,
  `document-source-connectors`, and `document-version-lifecycle`;
- required endpoint classes: connector status, discover/register/refresh,
  source list/detail, preview, index, delete-index, work status, and retrieval
  proof;
- required migration: `loomai-document-ingestion-v1`; and
- required verification pack: `document-knowledge-operations-v1`.

This template is a customer starting composition. It is not a LoomAI-owned
task product and does not add domain-specific prompts or text matching.

## 6. Deployment-Local Architecture

```text
Platform TEMPLATE + DATA + INFERENCE_PROFILE selection
  -> existing draft compiler and V04 immutable version
  -> customer storage binding + target profile validation
  -> target profile provisions runtime DB and vector dependencies
  -> exact runtime image starts with document capability enabled

customer-owned S3-compatible storage / operator-mounted demo folder
  -> deployment-local source connector
  -> bounded temporary local resource when required
  -> trusted Spring Resource resolution
  -> Spring AI text/JSON reader and token splitter
  -> AI Fabric DocumentIngestionPlan
       |-> bounded preview DTO with no queue side effect
       |-> content-free manifest persisted in runtime PostgreSQL
       +-> DocumentIndexingQueueAdapter
             -> existing queue and worker
             -> selected embedding provider
             -> selected AI Fabric vector provider

existing chat / query-once / specialist retrieval
  -> active-version document evidence
  -> source/version/chunk citations
```

Steady-state file bytes must not pass through or be stored by the central
Platform backend. Platform installs/configures the DATA plugin, binds secret
references, verifies connector readiness, and reads bounded status. The exact
deployment reads approved objects directly from customer storage.

## 7. Customer Source Storage Model

### 7.1 Ownership boundary

The customer owns original source objects and their storage account, bucket,
backup, retention, encryption, residency, availability, and source deletion.
LoomAI does not sell, provision, or operate object storage as part of this
capability.

LoomAI owns only:

- validation of the configured connector contract;
- secure binding of customer-provided credentials;
- deployment-local read and temporary parsing;
- internal source/version mapping;
- content-free manifests and indexing work state;
- indexed vectors and exact vector deletion; and
- safe connector/indexing observability.

### 7.2 Source connector adapters

Define one private-runtime `DocumentSourceConnector` contract with two initial
adapters:

1. `S3_COMPATIBLE_OBJECT_STORAGE`
   - production-primary source connector;
   - customer supplies endpoint, bucket, allowed prefix and scoped credentials;
   - runtime lists/reads only the configured prefix;
   - provider version ID, ETag and content fingerprint become source evidence;
   - runtime materializes a bounded temporary local file when the reader needs
     a filesystem resource, then removes it.
2. `MOUNTED_FOLDER`
   - optional for small data, internal canaries and demos;
   - operator mounts a folder at a configured trusted root and the runtime
     connector exposes list/read behavior only;
   - runtime accepts only normalized paths below that root;
   - no horizontal-scale, managed backup, storage SLA, or customer storage
     product claim.

The framework reader sees only a trusted local `Resource`. The connector never
passes an arbitrary user-provided URL to Spring AI.

### 7.3 Physical data separation

| Data | Owner and location |
| --- | --- |
| Original source bytes | Customer-owned object storage or an operator-mounted demo folder. |
| Source lifecycle record | Deployment PostgreSQL. |
| Content-free framework manifest | Deployment PostgreSQL. |
| Exact chunk/entity IDs and fingerprints | Deployment PostgreSQL. |
| Queue work IDs and projected status | Deployment PostgreSQL plus existing AI Fabric queue. |
| Temporary parsing copy | Deployment-local bounded temp space, deleted after preparation. |
| Prepared chunk content | Request-scoped plan only, then canonical indexing work/vector payload. |
| Embeddings and searchable chunks | Existing selected vector provider and deployment namespace. |
| Bounded preview | Generated on request; not persisted as a second document copy. |
| Platform control-plane state | Plugin/config/external binding references and aggregate readiness only, never raw source bytes. |

## 8. Runtime Persistence Contract

Add deployment-local Flyway migration `loomai-document-ingestion-v1` with
tables equivalent to the following. These tables describe LoomAI's derived
indexing state; they do not turn the deployment database into source-file
storage.

### `loomai_document_source`

- source ID and stable logical source key;
- dataset ID;
- trusted tenant, customer, and deployment IDs;
- connector binding ID and normalized object locator;
- provider version ID when available;
- ETag, content length, last-modified evidence, and content fingerprint;
- validated media type and safe display name;
- current internal source version and nullable active source version;
- source lifecycle status and optimistic-lock version;
- bounded failure code/message;
- created/updated timestamps; and
- internal-record retention/deletion timestamps.

The normalized object locator is stored only in the deployment database where
it is needed to read the object. Platform projections expose a bounded display
label or digest, not an unrestricted object key. No credential is stored here.

### `loomai_document_manifest`

- framework manifest schema version;
- manifest ID and plan ID;
- source ID and internal source version;
- provider revision fingerprint used to create that version;
- entity type, tenant, and visibility;
- lifecycle state;
- accepted index/delete work counts;
- bounded warnings/failure evidence; and
- created/activated/superseded/deleted timestamps.

### `loomai_document_manifest_chunk`

- manifest ID;
- source document ID;
- chunk ID and index;
- exact canonical entity ID;
- content fingerprint; and
- no raw chunk content or embedding.

### `loomai_document_work`

- manifest ID;
- AI Fabric work ID;
- operation `INDEX` or `DELETE`;
- last projected work state;
- terminal success/failure marker;
- bounded failure evidence; and
- observed timestamps.

Do not persist a full `DocumentIngestionPlan`, parser exception, embedding,
temporary absolute path, storage credential, private assertion, or unbounded
file content in these tables.

## 9. Source Version Lifecycle

Document versions are revisions of one customer-owned object, not framework or
runtime release versions. They are required for safe replacement and exact
deletion and do not conflict with LoomAI's latest-only release policy.

The runtime maps external revision evidence to a monotonically increasing
`long` per stable source ID:

```text
customer object: handbook/employee.txt
  provider revision: VersionId=a1, or ETag/size/fingerprint tuple
  LoomAI source version: 1 -> ACTIVE

customer replaces that object
  provider revision: VersionId=a2, or new ETag/size/fingerprint tuple
  LoomAI source version: 2 -> candidate INDEXING
```

Use provider `VersionId` when available. Otherwise, use a bounded combination
of normalized locator, ETag, content length, last-modified evidence, and the
content fingerprint calculated during the trusted read. A unique constraint on
source ID plus revision fingerprint makes refresh idempotent.

Canonical manifest states:

- `DRAFT`
- `INDEX_SUBMITTED`
- `ACTIVE`
- `FAILED`
- `SUPERSEDED`
- `DELETE_SUBMITTED`
- `DELETED`

Canonical source states:

- `REGISTERED`
- `PREPARING`
- `INDEXING`
- `ACTIVE`
- `REPLACING`
- `DELETE_PENDING`
- `DELETING`
- `DELETED`
- `FAILED`

### Initial registration and indexing

1. Discover or accept an object reference below the connector's approved
   bucket/prefix or mounted-folder root.
2. Persist its normalized reference and external revision evidence.
3. Read the object directly from customer storage with hard byte/time limits.
4. Materialize a bounded temporary local resource only when the reader needs
   one, then calculate the content fingerprint.
5. Prepare a side-effect-free plan and bounded preview.
6. Allocate internal version 1 and persist the content-free manifest.
7. Submit canonical indexing work.
8. Reconcile every work ID through `IndexingWorkQuery`.
9. Mark version 1 active only after every work item succeeds.
10. Remove the temporary copy on success or failure.

### Refresh and replacement

1. Read the registered object's current external revision evidence.
2. Return `UNCHANGED` when it maps to an already known source version.
3. Keep the current active version searchable while preparing a new revision.
4. Allocate the next internal version only after a complete bounded read and
   valid plan.
5. Submit candidate indexing work.
6. If candidate work fails, retain the previous active version and clean up
   only proven candidate entity IDs.
7. If every candidate work succeeds, atomically mark the candidate active.
8. Mark the previous manifest superseded and submit exact deletion work for
   its entity IDs.
9. Mark the previous version deleted only after every delete succeeds.

Queue acceptance is not successful indexing. A source may not become `ACTIVE`
from submission alone.

### Indexed-source removal

Removing a source from LoomAI means deleting its indexed vectors, manifests,
work state according to retention policy, and connector registration. It does
not delete, overwrite, move, or change the customer-owned object. The API and
UI must state this explicitly.

### Active-version retrieval

The first market claim requires user-facing retrieval to expose only active
versions. During implementation, confirm that the existing generic retrieval
filter contract can post-filter by trusted source ID/version metadata against
the deployment-local active-manifest registry.

If no suitable generic hook exists, raise a focused AI Fabric contract blocker
before adding private duplicated retrieval logic. Do not use query text,
document names, or product-domain matching. The framework's documented short
old/new overlap is acceptable only for the internal mechanics canary, not for
the final market-ready gate.

The first UI does not need a general document-version manager. It shows the
active version, pending candidate, provider revision evidence, last result, and
bounded history needed for operations and audit.

## 10. Deployment-Local API

Expose runtime APIs on each deployment, not on a shared `api.loomai.pro`
document data plane.

Target endpoint classes:

| Method | Deployment-local path | Purpose |
| --- | --- | --- |
| `GET` | `/api/documents/source-connector/status` | Return bounded readiness and connector type for the verified deployment. |
| `POST` | `/api/documents/sources/discover` | List a bounded page of eligible objects below the configured prefix/root. |
| `POST` | `/api/documents/sources` | Register one approved object reference without uploading its bytes. |
| `GET` | `/api/documents/sources` | List bounded source summaries for the verified deployment/tenant. |
| `GET` | `/api/documents/sources/{sourceId}` | Return lifecycle, active version, manifest, and work summary. |
| `POST` | `/api/documents/sources/{sourceId}/refresh` | Detect and prepare a changed external revision idempotently. |
| `GET` | `/api/documents/sources/{sourceId}/preview` | Return a bounded, side-effect-free preview of the candidate revision. |
| `POST` | `/api/documents/sources/{sourceId}/index` | Submit initial or replacement work idempotently. |
| `POST` | `/api/documents/sources/{sourceId}/reconcile` | Reconcile accepted work and advance valid lifecycle transitions. |
| `DELETE` | `/api/documents/sources/{sourceId}` | Delete exact indexed evidence and registration, never the customer object. |
| `POST` | `/api/documents/retrieval-proof` | Return tenant-scoped active evidence for verification, not an invented answer. |

The normal chat, query-once, and specialist endpoints remain the customer
answer surfaces. Do not add a second chat API to the document capability.

### API rules

- Derive tenant, customer, deployment, and allowed dataset from verified
  runtime context; reject client attempts to select authority.
- Require exact scopes such as `documents:read`, `documents:register`,
  `documents:index`, and `documents:delete-index`.
- Use idempotency keys for register, refresh, index, reconcile, and delete.
- Accept only a connector-relative object reference. Reject arbitrary URLs,
  endpoints, buckets, prefixes, absolute paths, and credential material.
- Enforce configured prefix/root boundaries after normalization.
- For mounted-folder mode, reject traversal, unsafe symlinks, and any path
  outside the read-only trusted root.
- Check object metadata before reading and enforce hard byte/time ceilings
  while streaming; never buffer unbounded content.
- Validate extension, media type, malformed JSON, and reader policy.
- Never return credentials, absolute paths, full plans, full source content,
  embeddings, or parser stack traces.
- Return only bounded preview excerpts and allowlisted metadata.
- Distinguish `ACCEPTED`, `INDEXING`, `ACTIVE`, `FAILED`, and deletion states.
- Record correlation IDs and safe audit evidence.

### Platform and storage traffic path

The Platform backend configures the connector binding and may call these
deployment-local APIs with a scoped runtime assertion to display status or
perform an authorized operator command. It must not receive or relay source
bytes.

```text
customer object storage -> exact deployment runtime -> temporary reader input
Platform UI/backend      -> connector status and lifecycle commands only
```

There is no customer-facing raw-file upload endpoint in the production
contract. A user places files in their own storage with their own storage
tools, then discovers/registers them in LoomAI. In mounted-folder demo mode,
an operator places files in the mounted folder outside the document API.

## 11. Runtime Integration

### 11.1 Dependencies

Declare these directly in `ai-fabric-runtime` even if currently present
transitively:

```xml
<dependency>
  <groupId>io.github.loom-ai-labs</groupId>
  <artifactId>ai-fabric-indexing</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-commons</artifactId>
</dependency>
```

Do not add a Spring AI `VectorStore`. Continue through AI Fabric's canonical
indexing queue and selected vector provider. Add only the source-storage client
needed by the private `DocumentSourceConnector` adapter.

### 11.2 Configuration

Compile explicit current configuration rather than depending on auto-config
defaults:

```yaml
ai:
  indexing:
    enabled: true
    documents:
      enabled: true
      max-documents-per-plan: 20
      max-chunks-per-plan: 100
      max-content-length-per-chunk: 8000
      max-total-content-length: 250000
      max-metadata-entries-per-chunk: 24
      max-metadata-value-length: 256
      default-splitter:
        enabled: true
        chunk-size: 800
        min-chunk-size-chars: 200
        min-chunk-length-to-embed: 5
      metadata:
        allowed-application-keys:
          - originalFilename
          - locale
          - sourceCategory
        warn-on-drop: true
```

Deployment-level environment/config also carries:

- capability enabled flag;
- source connector type and external binding reference;
- approved bucket/prefix or read-only mounted demo root;
- bounded deployment-local temporary root;
- indexed source/count/byte ceilings;
- preview bounds;
- internal manifest/work-evidence retention periods;
- reconciliation schedule and batch ceilings; and
- whether trusted auto-indexing is allowed.

Endpoint URL, bucket, prefix, and non-secret connector options are compiled
from the immutable DATA contribution and target binding. Credentials remain in
the existing secret system and are injected at runtime by reference. They are
never plugin manifest values.

### 11.3 Capability manifest

Add exact current declarations to runtime capability readback:

- capabilities:
  - `document-ingestion-core`
  - `document-source-connectors`
  - `document-version-lifecycle`
- endpoint classes for connector status, discovery, register, refresh,
  preview, index, reconcile, delete-index, and retrieval proof;
- migration ID `loomai-document-ingestion-v1`; and
- verification pack `document-knowledge-operations-v1`.

V04 apply must fail closed if a selected template claims these capabilities
but the exact runtime artifact does not attest them.

## 12. Platform Backend Work

Extend existing services rather than introducing a document data plane:

1. `MarketplaceManifestService`
   - accept and validate `EXTERNAL_DOCUMENT_STORAGE`;
   - require bounded `sourceConnector` and `documentPolicy` fields;
   - allow only supported connector types and ownership values;
   - reject `syncConnector` for this mode;
   - keep current structured modes unchanged.
2. Marketplace dataset persistence
   - persist normalized connector requirements and policy JSON;
   - include both in the dataset hash;
   - avoid a second document-plugin table.
3. `DeploymentMarketplaceDraftCompilerService`
   - compile DATA policy into existing marketplace dataset, entity, knowledge
     source, inference/vector, security, and external-binding requirements;
   - add immutable composition provenance.
4. `DeploymentDraftValidationService`
   - require embeddings, vector storage, document entity config, runtime DB,
     exact capability IDs, migration, verification pack, and either a valid
     customer storage binding or an explicitly permitted demo mount;
   - reject unsupported formats, missing limits, connector configurations that
     request write/delete operations, and a mounted-folder production/HA claim.
5. `DeploymentConfigCompiler`
   - emit exact runtime bounds, connector type, binding reference, approved
     prefix/root, endpoint policy, entity metadata, and capability expectations;
   - never emit secret values into immutable config or logs.
6. `MarketplaceTemplateBootstrapService`
   - install the exact DATA and inference plugin versions selected by the
     starter template.
7. Release execution
   - provision/reconcile only LoomAI-owned runtime dependencies such as the
     deployment database and current vector profile;
   - resolve and inject the pre-existing customer storage binding;
   - preflight list/read access from the exact runtime network;
   - never create a customer bucket, copy their objects, or delete their
     objects;
   - do not invoke `MarketplaceDatasetSyncService` to read or send document
     bytes for this ingestion mode.
8. Release verification
   - include the document verification pack whenever the immutable
     composition claims the capability.
9. Runtime administration client
   - call bounded connector/source/status/proof endpoints on the exact assigned
     deployment using scoped assertions;
   - never proxy the file body.
10. Audit
   - record plugin/template selection, binding changes, discover/register,
     refresh/index/delete-index commands, terminal reconciliation, and internal
     retention cleanup without source content or credentials.

Platform may cache aggregate counts and connector readiness for display, but
the deployment database remains authoritative for individual source lifecycle.

## 13. Customer Storage Binding And Target-Profile Work

### 13.1 External resource representation

Reuse `DeploymentProviderResourceHandleEntity` as a safe external binding
reference rather than creating provider-specific document tables. Add a
current resource kind such as:

- `EXTERNAL_DOCUMENT_STORAGE_BINDING`

The handle represents a customer-supplied resource; it does not mean LoomAI
owns or provisioned that resource. Safe metadata may include connector type,
endpoint host, bucket alias, allowed-prefix digest, region, object-versioning
availability, and last preflight status. It must not contain credentials or
unrestricted object keys.

The secret binding contains the least-privilege customer credential. For the
first S3-compatible contract, the normal policy is list/read on one bucket
prefix. Write and delete permission are neither required nor accepted as part
of the product contract.

`MOUNTED_FOLDER` is represented by target-profile mount configuration, not an
object-storage resource handle. The runtime connector is list/read-only and is
limited to an operator-controlled folder. Coolify's current application-storage
API does not expose a read-only mount flag, so host filesystem permissions are
an operator responsibility and this mode remains demo/internal-canary only.

### 13.2 Target profile requirements

A document-enabled template must require:

- deployment-local PostgreSQL for LoomAI lifecycle state;
- one valid customer storage binding or an explicitly allowed demo mount;
- selected embedding provider;
- selected vector provider;
- sufficient CPU/memory and bounded ephemeral parsing space;
- network egress from the runtime to the configured storage endpoint; and
- direct deployment URL/auth suitable for administration and proof.

Coolify and Railway adapters validate network reachability, bind existing
secrets/config, configure the optional demo mount, deploy, and poll terminal
status through their current paths. They do not provision object storage,
manage its backup, calculate storage billing, or delete source objects.

### 13.3 Scale and availability rules

S3-compatible customer storage is the production-primary mode and can serve
multiple runtime replicas when the rest of the deployment topology supports
it. Connector readiness is still deployment-specific and must be proven from
every runtime topology being marketed.

Mounted-folder mode is limited to a single-instance, small-data demo or
internal canary. LoomAI makes no HA, automatic backup, cross-host portability,
or managed-storage promise for that mode. Moving beyond that boundary requires
the customer to bind shared external storage, not LoomAI to turn the folder
into a managed storage product.

## 14. Platform UI

Add **Document Knowledge** to the existing deployment workspace when the
active immutable version claims the capability.

Required views and controls:

- connector type, bounded binding identity, readiness, and last successful
  read preflight;
- a clear ownership message: **Files remain in your storage**;
- discover/browse control restricted to the configured bucket/prefix or demo
  root;
- register control for an eligible discovered object;
- source list with status, type, size, active version, provider revision,
  update time, and bounded failure indicator;
- bounded preview before indexing;
- explicit Index command for the first release;
- Refresh command that detects and prepares a new external object revision;
- Remove from LoomAI command with confirmation that the customer file will not
  be deleted;
- work progress distinguishing queue acceptance from vector completion;
- active/pending/superseded version summary;
- indexed source/count/byte limits and internal retention posture;
- retrieval-proof tool with source/version/chunk evidence; and
- deployment/template/plugin/provider/connector provenance.

Mounted-folder mode must carry a visible **Demo / small data only** status. It
must not be presented as production managed storage.

Do not expose:

- secrets;
- unrestricted object keys or local absolute paths;
- raw manifests;
- embeddings;
- unbounded file content;
- parser stack traces;
- source-storage delete controls;
- a LoomAI storage quota, backup, or durability promise; or
- controls for unsupported formats.

The UI should reuse deployment selectors, operation feedback, verification
evidence, audit navigation, external binding controls, and capability
visibility already used elsewhere. It must not create a separate document
application shell or general-purpose storage browser.

## 15. Security And Governance

Release-blocking rules:

1. Runtime derives identity and authority from verified context.
2. The connector accepts only configured endpoints, buckets, and prefixes;
   client input cannot select a new authority boundary.
3. Customer credentials use existing secret references and least privilege.
   The released S3-compatible contract requires list/read, not write/delete.
4. Endpoint validation and network policy prevent arbitrary URL/SSRF behavior.
5. Mounted-folder mode rejects traversal, out-of-root paths, and unsafe
   symlinks. The runtime never writes through the source connector. Because the
   current Coolify storage API cannot attest a read-only mount, the mode is not
   a production storage claim.
6. Object/file type and size are validated before and during streaming.
7. Tenant/deployment/source ownership is checked on every operation.
8. Metadata is allowlist-only; object or parser metadata cannot override
   tenant, deployment, source identity, version, visibility, or lifecycle keys.
9. Preview is bounded and side-effect free.
10. A source is not active until every index work item succeeds.
11. Index deletion is not complete until every exact chunk deletion succeeds.
12. Raw files are not written to logs, audit events, Platform DB, bundle JSON,
    observability labels, or durable temporary storage.
13. Temporary parsing copies are bounded and removed on success, failure,
    timeout, restart cleanup, and decommission.
14. Customer source backup, retention, encryption, residency, scanning, and
    deletion remain customer responsibilities. LoomAI documents the accepted
    text/JSON safety boundary and does not claim public arbitrary-file upload.
15. Internal retention applies to manifests, queue evidence, vectors, and temp
    cleanup. It never implies deletion of a customer source object.
16. Failed indexing or cleanup remains visible and retryable; no fallback
    reports success.

## 16. Observability And Operations

Expose bounded deployment-local and Platform-projected evidence for:

- connector type and readiness;
- safe binding/prefix identity and last successful list/read preflight;
- discovered, registered, active, replacing, failed, and deleted-index counts;
- active and candidate internal versions;
- indexed source count and indexed-byte policy use;
- external revision drift or unavailable-object state;
- prepared chunk counts;
- accepted/in-progress/succeeded/failed index work;
- accepted/in-progress/succeeded/failed delete work;
- oldest unreconciled work and stale candidate manifests;
- temporary-file cleanup failures;
- runtime DB, embedding, and vector provider readiness;
- retrieval proof with source/version/chunk IDs; and
- last successful reconciliation and internal-retention sweep.

Do not claim customer storage capacity, backup health, durability, or retention
status unless a future connector can report a specifically approved bounded
fact. Those remain external customer responsibilities.

Metrics and logs use IDs and bounded codes, not source content, credentials, or
filenames that may contain personal information.

Required operator commands:

- preflight the configured connector;
- discover/register or refresh one source;
- reconcile one source;
- retry failed candidate index work safely;
- clean a failed candidate by exact indexed IDs;
- retry exact index deletion;
- verify retrieval for the active version;
- run internal manifest/work retention cleanup;
- run temporary-file cleanup; and
- decommission LoomAI document state without modifying customer objects.

## 17. Export, Import, Promotion, Recovery, And Decommission

### 17.1 V04 promotion

Promote the immutable plugin/template/config/runtime composition through the
existing staging-to-production flow. Do not copy staging document bytes into
production. Production binds an explicitly approved customer storage resource
and registers its own source objects.

Staging and production may use different buckets, prefixes, credentials, and
object revisions while sharing the same reviewed DATA/TEMPLATE composition.
Promotion must show those target-scoped binding differences before apply.

### 17.2 Config export/import

Reuse current config-only and sealed export/import:

- export exact TEMPLATE/DATA/INFERENCE_PROFILE versions;
- export connector type, document policy, entity config, knowledge-source
  binding, external resource reference, capability requirements, and
  verification pack;
- rewrite target-scoped resource/secret references during import;
- require the destination operator to supply or map a valid customer storage
  binding;
- never place raw files, chunk text, embeddings, absolute paths, object keys,
  or live credentials in bundle JSON; and
- report indexed document state as an explicit external/stateful impact.

Sealed export may include encrypted secret material according to the existing
contract, but it is not a source-object backup and does not transfer storage
ownership.

### 17.3 Recovery responsibilities

The customer restores and operates original source storage. LoomAI does not
back up that bucket or folder as part of Document Knowledge Operations.

Before market-ready status, prove recovery for the parts LoomAI does own:

- deployment PostgreSQL source/manifest/work state under the existing target
  profile's runtime-state policy;
- vector state, or deterministic governed reindex from still-available
  customer objects;
- connector binding and secret-reference restoration; and
- restored active-version retrieval evidence.

After restoring runtime state, compare every active source's recorded external
revision with the current customer object. If the original revision is no
longer available, fail visibly and require an authorized refresh; never label a
different object revision as the restored active version.

Mounted-folder demo mode has no LoomAI source backup promise. The operator is
responsible for preserving or repopulating the mounted folder.

### 17.4 Decommission

Deployment decommission must:

1. stop new discovery, refresh, and index work;
2. drain or cancel accepted work safely;
3. issue exact deletes for active and candidate indexed manifests;
4. reconcile terminal vector deletion;
5. remove deployment-local source registrations, manifests, work state, and
   temporary files according to policy;
6. remove customer storage secret/resource bindings from the deployment;
7. retain only bounded audit evidence allowed by policy; and
8. explicitly report that customer bucket, folder, and objects were not
   deleted.

The Platform must never call an object-delete or bucket-delete operation during
source removal, rollback, import cleanup, or deployment decommission.

## 18. Verification Strategy

### 18.1 Local unit/component tests

Runtime tests must cover:

- S3-compatible list/head/read behavior under one configured prefix;
- external `VersionId` and ETag/fingerprint mapping to internal versions;
- missing object, changed object, revoked credential, endpoint timeout, partial
  read, and oversized stream behavior;
- arbitrary endpoint/bucket/prefix, traversal, remote URL, unsafe symlink,
  MIME, extension, malformed JSON, and size rejection;
- mounted-folder read-only root boundaries;
- text and approved JSON reading;
- direct dependency and auto-configuration conditions;
- metadata protection and allowlist behavior;
- deterministic source/version/chunk IDs;
- bounded preview with zero queue side effects;
- manifest persistence without content or credentials;
- idempotent register/refresh/submit/reconcile/delete-index;
- partial queue acceptance and worker failures;
- failed replacement preserving the active version;
- successful new-first replacement and exact old-index deletion;
- active-version retrieval filtering;
- tenant and deployment isolation;
- restart recovery and stale-temp cleanup;
- internal manifest/work retention; and
- proof that source removal and decommission leave the customer object
  unchanged.

Platform tests must cover:

- new DATA manifest values and mode-specific schema;
- invalid combinations and connector write/delete operation modes rejected;
- exact plugin persistence/hash;
- template bootstrap and install version pinning;
- draft compiler and validator requirements;
- V04 config/provenance hashing;
- external binding and target-profile validation without storage provisioning;
- source capability manifest enforcement;
- release execution branching away from Platform-side dataset sync;
- safe runtime status/proof calls without byte relay;
- export/import resource-reference rewriting;
- rollback/decommission planning that never deletes source objects;
- Platform API authorization; and
- UI typecheck/build and interaction states.

### 18.2 `document-knowledge-operations-v1` verification pack

The reusable pack must prove against the exact deployment:

1. capability manifest, migration, endpoint, and framework version match;
2. runtime database, embeddings, and vector provider are ready;
3. the exact connector binding passes bounded list/read preflight;
4. discover and register a pre-seeded bounded text object;
5. preview returns bounded content and creates no queue work;
6. index submission returns durable work IDs;
7. every work item reaches successful terminal state;
8. retrieval returns the source/version/chunk evidence for Tenant A;
9. Tenant B and a second deployment cannot read or mutate it;
10. an invalid external revision fails while version 1 remains retrievable;
11. a valid changed object becomes version 2 before version 1 exact deletion;
12. end-user retrieval exposes only the active version;
13. removing the source from LoomAI removes retrieval evidence while the
    customer object still exists unchanged;
14. restart preserves registration, manifest, operation, and retrieval state;
15. unsupported, oversized, out-of-prefix, and untrusted inputs fail closed;
16. revoked storage credentials make the connector fail closed without losing
    the current active index; and
17. logs, API responses, bundle data, and Platform projections contain no
    forbidden data.

Repeat the lifecycle for the approved JSON reader contract. Run the mounted
folder variant as a separate demo-mode test; passing it does not substitute for
the production S3-compatible connector gate.

### 18.3 Hosted gates

1. Internal local deterministic smoke.
2. Staging single-deployment canary using an external test bucket controlled by
   the canary operator, plus real embeddings/vector provider.
3. Two-tenant and two-deployment isolation canary.
4. Runtime restart/redeploy persistence and deterministic reindex canary.
5. Failed indexing, unavailable object, revoked credential, and failed delete
   recovery canary.
6. V04 release, post-apply verification, promotion, export/import, rollback,
   recovery, and decommission canary.
7. Customer-object-preservation proof after source removal and decommission.
8. Production controlled canary using non-sensitive external test files.
9. Full Platform release gate.

A framework demo, local test, dependency presence, mounted-folder proof, or one
successful index is not market-ready evidence.

## 19. Implementation Work Packages

### WP0: Contract confirmation and baseline

Status: `IMPLEMENTED_HOSTED_PROVEN`

- Confirm active-version filtering can use an existing generic retrieval hook.
- Confirm the exact AI Fabric `0.8.4` reader, manifest, queue, reconciliation,
  and deletion contracts.
- Confirm the approved S3-compatible client, object-version evidence, and
  temporary-resource handoff to Spring AI readers.
- Confirm current external secret/resource binding support on Coolify and
  Railway and the mounted-folder demo target-profile boundary.
- Freeze DATA mode, connector type, policy, capability, and endpoint names.
- Record exact current runtime/Platform baseline and clean-tree state.

Exit: no framework, connector, or target-profile contract ambiguity remains
for the first slice.

### WP1: Private runtime document core

Status: `IMPLEMENTED_HOSTED_PROVEN`

- Add direct dependencies and explicit configuration.
- Add the `DocumentSourceConnector` contract.
- Add the production S3-compatible read/list adapter.
- Add the read-only mounted-folder demo adapter.
- Add bounded temporary materialization and restart cleanup.
- Add deployment-local migration/entities/repositories.
- Add discover/register, preview, submit, reconcile, refresh, replace, and
  exact-delete-index services.
- Add deployment-local APIs, authorization scopes, and safe DTOs.
- Add active-version retrieval enforcement.
- Add capability manifest entries and local tests.

Exit: the complete text lifecycle passes locally, survives runtime restart,
and never mutates the source object.

### WP2: Marketplace and V04 composition

Status: `IMPLEMENTED_HOSTED_PROVEN`

- Extend DATA manifest validation and persistence.
- Compile entity, knowledge-source, inference/vector, source connector,
  external binding, security, and document policy into the existing draft.
- Extend validation, config hashing, and source capability checks.
- Publish one reviewed DATA plugin version.
- Publish one compatible Conversational starter template version.

Exit: a clean deployment created from the template has no hidden manual
configuration and fails closed when any required capability or binding is
missing.

### WP3: Customer storage binding and deployment lifecycle

Status: `IMPLEMENTED_HOSTED_PROVEN`

- Add the external document storage binding kind and target requirements.
- Bind customer endpoint/bucket/prefix and secret references through current
  provider paths; never provision the bucket.
- Add runtime-network connector preflight and terminal deployment polling.
- Add optional demo-folder mount configuration with a list/read-only runtime
  connector and explicit provider mount limitation.
- Add runtime-state recovery/reindex, rollback, and decommission operations.
- Prove source objects remain customer-owned and unchanged across all paths.

Exit: connector config and LoomAI runtime state survive redeploy/rebinding,
reindex is proven, and source objects remain outside LoomAI lifecycle control.

### WP4: Platform operations and UI

Status: `IMPLEMENTED_HOSTED_PROVEN`

- Add bounded runtime connector/status/proof client without relaying bytes.
- Add Document Knowledge to the deployment workspace.
- Add binding readiness, discovery, register, version/status, preview, index,
  refresh, remove-index, and retrieval-proof flows.
- Add audit, indexed-limit, internal-retention, and failure visibility.
- Add clear customer-ownership and mounted-demo boundary language.

Exit: an operator can complete and understand the lifecycle without raw API,
database, source credential, or LoomAI file-upload access.

### WP5: Verification pack and release integration

Status: `IMPLEMENTED_HOSTED_PROVEN`

- Register `document-knowledge-operations-v1`.
- Add deterministic and real-provider checks.
- Add connector, isolation, restart, replacement, source-preservation,
  cleanup, export/import, rollback, recovery, and decommission coverage.
- Make the pack blocking only for versions that claim document capability.

Exit: an immutable composition cannot be released as verified without the full
claimed document evidence.

### WP6: Hosted staging and controlled production proof

Status: `IMPLEMENTED_HOSTED_PROVEN`

- Create a new generic deployment from the released template.
- Bind an operator-controlled external test bucket that Platform did not
  provision.
- Do not modify Shopify or ProdUS assignments for this proof.
- Pass staging mechanics and real-provider gates.
- Promote the same reviewed composition through the existing V04 flow while
  explicitly binding a production test source.
- Run controlled production proof and the full release gate.
- Record exact versions, commits, resource handles, connector evidence, and
  verification IDs.

Exit: the capability reaches `HOSTED_PROVEN`. `MARKET_READY` additionally
requires runtime-state recovery, decommission, support, indexed limits,
internal retention, security, and commercial policies. It does not require or
imply a LoomAI-managed customer storage service.

### WP7: Demand-gated expansion

Status: `DEFERRED`

- Azure Blob and Google Cloud Storage customer connectors;
- Markdown/HTML reader evaluation;
- PDF/Tika hardening;
- Office formats;
- OCR and multimodal extraction;
- connector polling/event schedules;
- malware/DLP integration hooks;
- quality scorecards and golden-answer evaluation; and
- bulk-provider optimization.

Each expansion needs representative fixtures, security limits, lifecycle
tests, exact index-deletion proof, customer-object-preservation proof, and an
honest separate capability claim. It does not change the no-managed-storage
decision unless a future product decision explicitly replaces this plan.

## 20. Implementation Order

1. Complete WP0 contract checks.
2. Implement source connectors, persistence, and lifecycle behind a disabled
   capability configuration.
3. Pass runtime unit/component and deterministic packaged tests.
4. Add capability manifest and exact current runtime image evidence.
5. Extend Marketplace DATA and V04 compilation.
6. Add external binding validation and target-profile checks without storage
   provisioning.
7. Add Platform operations/UI.
8. Register the verification pack.
9. Publish exact plugin/template versions.
10. Create a clean staging deployment and pass all document gates.
11. Prove export/import configuration behavior, restart, reindex/recovery,
    rollback, source-preserving removal, and decommission.
12. Promote a controlled production canary and run the full release gate.
13. Update user guides and market claims only from the resulting evidence.

## 21. Definition Of Done

Document Knowledge Operations is complete only when:

- LoomAI uses AI Fabric `0.8.4` document contracts directly;
- no parallel reader, splitter, vector store, queue or deployment lifecycle is
  introduced;
- a current Marketplace DATA plugin and TEMPLATE compose the capability;
- V04 draft/version/release/apply/verification owns immutable configuration;
- production source files remain in customer-provided object storage;
- mounted-folder mode is optional and visibly restricted to small data/demos;
- LoomAI neither provisions nor claims to manage, back up, bill for, or delete
  customer source storage;
- every source and operation is deployment/tenant scoped;
- connector binding and runtime state survive redeploy or fail visibly;
- preview is bounded and side-effect free;
- queue acceptance and completed indexing are visibly distinct;
- internal source versions support safe failed and successful replacement;
- only active versions ground customer-visible retrieval;
- exact index deletion removes every manifest chunk while leaving the customer
  object unchanged;
- export/import, runtime-state recovery, rollback, and decommission have
  explicit truthful behavior;
- local, two-tenant, two-deployment, restart, failure, source-preservation, and
  real-provider gates pass;
- a clean Marketplace-template staging-to-production lifecycle passes;
- the document-specific hosted verification pack is green, and a global
  Platform market-readiness claim additionally requires either a green full
  release gate or an explicit owner disposition for failures proven unrelated
  to document indexing; and
- documentation does not claim unsupported formats, crawling, OCR, public
  upload security, or LoomAI-managed source storage.

## 22. Post-Implementation Follow-Up

The bounded first release is implemented and hosted-proven. Keep the claim
scoped to trusted `.txt` and configured `.json` objects in customer-provided
S3-compatible storage. Do not expand the format, connector, managed-storage, or
public-upload claim without completing the corresponding WP7 gates. Resolve or
explicitly disposition the unrelated Shopify release-gate findings before a
global Platform `MARKET_READY` announcement. Do not reindex or alter Shopify,
ProdUS, or existing structured Marketplace datasets; their current Data Sync
and retrieval paths remain separate supported paths.

## 23. Local Implementation Evidence

Recorded on 2026-09-26:

- private product-services reactor `mvn clean verify`: passed;
- generic REST connector: `12` tests passed;
- private runtime: `207` tests passed;
- relay: `35` tests passed;
- Platform backend `mvn clean verify`: `825` tests passed;
- fresh PostgreSQL 16 Marketplace migration proof: passed through `V148`;
- Platform UI production build: passed;
- verification script shell syntax: passed;
- repository whitespace validation: `git diff --check` passed; and
- common committed-secret pattern scan: no match.

These local results were the prerequisite for, and do not replace, the hosted
evidence in section 24.

## 24. Hosted Implementation And Release Evidence

Recorded on 2026-09-26 for private source
`ceca664c88fe79559f216f5fdf99c1af535090d3` and AI Fabric `0.8.4`:

- GitHub Actions run `36238988182` built the immutable Platform backend and UI
  images successfully. The final backend suite passed `832` tests across `152`
  suites, including the Coolify provisioning-heartbeat regression.
- Staging release `rel-a0c27d77` and verification `vrf-f2375e15` passed on
  runtime artifact `dsa-e428702d`. Reapply release `rel-ecbae14b` and
  verification `vrf-04b6880f` proved that a long Coolify provisioning step
  remains alive beyond the former stale-step threshold.
- Hosted staging pack `document-knowledge-operations-v1` run `vsr-77312f59`
  passed. The canary proved trusted TXT and configured JSON discovery,
  registration, preview, indexing, active-version retrieval, replacement,
  restart durability, deterministic recovery/reindex, exact indexed deletion,
  retention, two-tenant and two-deployment isolation, source outage and invalid
  input handling, and export/import configuration behavior.
- Export `dexp-f47491ef` / bundle `dxb-f6032265` and import `dimp-ea6d9bc9`
  preserved immutable connector policy without exporting target credentials.
- Controlled production release `rel-b5227257` and verification
  `vrf-9365e4fe` were `APPLIED_VERIFIED` on runtime artifact `dsa-6e70fab7`.
  Hosted document run `vsr-e39c0216` and durability run `vsr-e74bf17d` both
  passed against a real Pinecone index and operator-controlled S3-compatible
  source objects.
- Production restart checks returned two pieces of evidence for both bounded
  fixture queries with the correct top sources and active source version `2`.
  Exact index deletion then returned zero evidence while the source objects
  remained discoverable and unchanged.
- Hard decommission removed the temporary Platform bindings, managed vector
  resource, managed secret, Coolify application, runtime database, and Pinecone
  index. Staging canaries and their provider resources were also removed. The
  source-preservation check still listed `verification/catalog.json` and
  `verification/handbook.txt` before the disposable operator fixture itself was
  retired.
- All twelve framework demonstration applications were restored to
  `running:healthy`; their public health endpoints returned HTTP `200`. Eleven
  report AI Fabric `0.8.4`, and the MCP reference demo reports the same source
  commit but has no version field.
- No ProdUS deployment, Shopify assignment, or customer source dataset was
  changed for this proof. Temporary rollout tokens were revoked, temporary
  local secret copies were retired, and the two Hetzner firewall rule sets were
  restored to their exact pre-proof snapshots.

Document Knowledge Operations therefore reaches `IMPLEMENTED_HOSTED_PROVEN`
for the bounded first-release contract. It is not yet an unconditional global
Platform `MARKET_READY` claim: staging full run `vsr-87bc4b58` and standalone
Partner run `vsr-c2c5fac1` retain the known unsupported retired Shopify mode,
while production full run `vsr-aba87784` passed all earlier stages but failed
one of eleven first-product answer-quality checks because Shopify output exposed
internal terminology. Those findings are unrelated to document storage,
indexing, retrieval, isolation, durability, or lifecycle behavior and were not
hidden, skipped, or weakened for this proof.
