# LoomAI Platform AI Fabric 0.4 Migration Runbook

- **Document status:** Required migration plan
- **Platform repository:** `TheBaseRepo`
- **Platform branch reviewed:** `Platform-V10`
- **Source AI Fabric version:** `0.3.1`
- **Target AI Fabric version:** `0.4.0`
- **Target framework tag:** `ai-fabric-framework-v0.4.0`
- **Prepared:** 2026-07-25
- **Classification:** Private platform engineering document

## Purpose

This runbook defines how to migrate LoomAI Platform, its product services, generated deployment
artifacts, and deployed customer runtimes from AI Fabric `0.3.1` to `0.4.0`.

AI Fabric `0.4.0` is a breaking entity-indexing lifecycle release. This is not a safe dependency-only
upgrade. LoomAI generates, stores, publishes, and executes AI entity configuration, so the platform
must migrate both its code and its persisted deployment state before a `0.4.0` runtime is allowed to
consume a deployment artifact.

The migration is complete only when all of the following agree on the `0.4` contract:

```text
Platform Knowledge editor
  -> Platform draft validation
  -> immutable deployment version and entity artifact
  -> runtime configuration loading
  -> trusted Data Sync projection
  -> durable indexing queue
  -> embedding and vector provider
  -> retrieval and RAG verification
```

Do not deploy a `0.4.0` runtime over a `0.3.1` entity artifact, queue schema, or generated vector
index.

## Authoritative Framework References

- [AI Fabric 0.4.0 release notes](https://github.com/Loom-AI-Labs/ai-fabric-framework/blob/main/docs/release-notes/0.4.0.md)
- [AI entity lifecycle 0.4 migration guide](https://github.com/Loom-AI-Labs/ai-fabric-framework/blob/main/docs/Framework-Dev-Guides/retrieval-vectorization/ANNOTATION_LIFECYCLE_0_4_MIGRATION_GUIDE.md)
- [Data Sync Push API guide](https://github.com/Loom-AI-Labs/ai-fabric-framework/blob/main/docs/Framework-Dev-Guides/retrieval-vectorization/DATA_SYNC_PUSH_API_GUIDE.md)

This document is the LoomAI-specific execution layer above those framework documents.

## Executive Decision

LoomAI must use a staged cutover with no compatibility shim.

1. Make Platform configuration production and validation `0.4` aware.
2. Migrate mutable deployment drafts.
3. Preserve published `0.3` versions as immutable historical/rollback artifacts.
4. Publish a new `0.4` deployment version from each approved migrated draft.
5. Stop old indexing workers for the target deployment.
6. Replace old queue/state tables and generated vectors.
7. Deploy the `0.4` runtime and backfill from authoritative source records.
8. Verify update, delete, retrieval, RAG, retry, and tenant behavior.
9. Promote by deployment cohort only after a canary passes.

The platform must reject either incompatible pairing:

| Runtime | Entity artifact | Result |
| --- | --- | --- |
| `0.3.1` | `0.3` | Allowed only during rollback window |
| `0.3.1` | `0.4` | Reject |
| `0.4.0` | `0.3` | Reject |
| `0.4.0` | `0.4` | Required target |

## Current LoomAI Audit

### Dependency and build state

The following parent builds still select AI Fabric `0.3.1`:

- `ai-infrastructure-module/pom.xml:28`
  - `ai-fabric.framework.version=0.3.1`
- `ai-fabric-product/pom.xml:28`
  - `ai-fabric.version=0.3.1`

The runtime, generic connector, and relay Dockerfiles clone framework `main`, while their Maven
reactor still requests `0.3.1`. Installing the current framework source into the image build does not
upgrade a product whose BOM property still resolves `0.3.1`. This can make a build look current while
it consumes older Maven Central artifacts.

The embedding-worker Dockerfiles are explicitly pinned to
`ai-fabric-framework-v0.3.1`.

Affected Docker build surfaces include:

- `ai-infrastructure-module/ai-fabric-runtime/Dockerfile`
- `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/Dockerfile`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile`
- `ai-infrastructure-module/ai-fabric-relay/Dockerfile`
- `ai-fabric-product/ai-fabric-embedding-worker/deploy/container/Dockerfile`
- `ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile`

### Java API state

A source scan found no active LoomAI Java use of:

- `@AICapable`
- `@AISearchable`
- `@AIContext`
- `@AIIdentity`
- `@AIProcess`
- `AIEntityIndexingGateway`
- removed `0.3` lifecycle methods such as `processEntityForAI`

LoomAI's current migration is therefore primarily a YAML-only trusted Data Sync and operational
state migration. There is no current annotated LoomAI entity model to convert.

Re-run this scan immediately before implementation:

```bash
rg -n \
  '@(AICapable|AISearchable|AIContext|AIIdentity|AIProcess)|AIEntityIndexingGateway|processEntityForAI' \
  ai-infrastructure-module ai-fabric-product Platfrom product-services \
  --glob '!**/target/**' \
  --glob '!**/node_modules/**' \
  --glob '*.java'
```

Any new match must be migrated using the public lifecycle migration guide before proceeding.

### Stale source-controlled entity configuration

The following files still use removed `0.3` keys:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/resources/ai-entity-config.yml`
- `ai-infrastructure-module/ai-fabric-runtime/src/test/resources/test-runtime-entity-config.yml`
- `ai-infrastructure-module/ai-fabric-runtime/deploy/docker/customer-template/config/ai-entity-config.yml`
- `Platfrom/backend/src/main/resources/bootstrap/ecommerce-demo/runtime/config/ai-entity-config.yml`

Examples of removed keys currently present include:

- `features`
- `auto-process`
- `enable-search`
- `auto-embedding`
- `indexable`
- `crud-operations`
- `include-in-rag`
- `weight`
- metadata `type`

### Persisted platform configuration

Entity configuration is not limited to source YAML. LoomAI stores it in:

- `platform_deployment_drafts.entity_config_json`
  - represented by `DeploymentDraftEntity.entityConfigJson`
- `platform_deployment_versions.entity_config_json`
  - represented by `DeploymentVersionEntity.entityConfigJson`
- `platform_deployment_versions.entity_artifact_yaml`
  - represented by `DeploymentVersionEntity.entityArtifactYaml`

`DeploymentConfigCompiler` serializes a draft into the immutable entity artifact. Provisioning then
sets `AI_CONFIG_DEFAULT_FILE` to that artifact URL. Existing persisted `0.3` artifacts can therefore
reach a newly deployed runtime even after every source-controlled YAML file has been corrected.

Published versions are release evidence. Do not rewrite their JSON or YAML in place.

### Platform editing and validation gaps

`Platfrom/ui/src/pages/KnowledgePage.tsx` currently edits:

- searchable field names;
- embeddable field names;
- metadata in `name:type` form.

It does not require the `0.4` projection contract:

- `indexing.enabled`;
- `indexing.max-characters`;
- searchable destinations;
- preprocessing;
- priority;
- required fields;
- metadata `data-type`;
- metadata destinations;
- PII policy.

New searchable fields are currently written with only `name`, which `0.4` rejects because
destinations are mandatory.

`DeploymentDraftValidationService.validateEntities` currently verifies only:

- `ai-config.vector-dimensions`;
- the existence of `ai-entities`;
- optional `fields` array shape.

It neither rejects legacy keys nor validates the current searchable and metadata projection.

### Reindex detection gap

`VectorizationIndexedOutputHashService` currently hashes old properties:

- searchable `name` and `weight`;
- embeddable fields;
- metadata `name` and `type`.

It does not hash all `0.4` behavior-affecting properties:

- `indexing.enabled`;
- `indexing.max-characters`;
- searchable destinations, preprocessing, max length, priority, and required;
- metadata data type, format, description, destinations, priority, required, and PII sanitization;
- analysis policy where analysis output affects the indexed lifecycle.

A projection change can therefore fail to create the expected vectorization drift/reindex evidence.

### Data Sync error handling gap

`ConnectorDataSyncTargetWriter` throws a generic status-only exception before parsing a non-2xx
Data Sync response. AI Fabric `0.4` distinguishes:

- `PROJECTION_REJECTED`;
- `INDEXING_RETRYABLE`;
- `INDEXING_PERMANENT`;
- `INDEXING_SUBMISSION_FAILED`;
- access and vector-space failures.

The runner must preserve the bounded error code, indexing work ID, and retry disposition. It must not
turn durable retryable work into a false success or blindly resubmit it as if no work were accepted.

### Existing compatibility evidence

The following audit commands were already run:

```bash
mvn -f ai-fabric-product/pom.xml -Dai-fabric.version=0.4.0 test
```

Result: build success; 19 tests passed.

```bash
mvn -f ai-infrastructure-module/pom.xml \
  -pl ai-fabric-runtime -am \
  -Dai-fabric.framework.version=0.4.0 \
  test
```

Result: Java compilation succeeded, but 2 of 138 tests failed:

- `RuntimeDataSyncEndpointTest.vectorSpacesEndpointRequiresTrustedBackendAuth`
- `RuntimeDataSyncTrustedBackendEndpointTest.vectorSpacesEndpointAcceptsTrustedBackendApiKeyInVerifiedMode`

Both failures expected `vectorSpaces[0]`. Runtime diagnostics reported
`supportedEntityTypes=[]`. The old test entity configuration did not provide an enabled, typed
`0.4` projection.

This proves the product Java surface is close to compatible, but LoomAI is not configuration-ready.

## The Target 0.4 Entity Contract

### Supported fields

| Scope | Property | Required behavior |
| --- | --- | --- |
| Entity | `indexing.enabled` | Must be `true` for a Data Sync vector space |
| Entity | `indexing.max-characters` | Optional integer from 1 through 8000 |
| Entity | `analysis.enabled` | Keep `false` unless dependent analysis is intentionally configured |
| Entity | `analysis.after` | Required operations when analysis is enabled |
| Searchable field | `name` | Required and unique, case-insensitively |
| Searchable field | `destinations` | Non-empty; at least one field must include `SEMANTIC_SEARCH` |
| Searchable field | `preprocessing` | `NONE`, `NORMALIZE`, `CLEAN`, or `SANITIZE` |
| Searchable field | `max-length` | Optional `-1` or positive integer |
| Searchable field | `priority` | Optional integer from 0 through 100 |
| Searchable field | `required` | Optional boolean |
| Metadata field | `name` | Required and unique, case-insensitively |
| Metadata field | `data-type` | `AUTO`, `STRING`, `NUMBER`, `BOOLEAN`, `DATE`, `ENUM`, `ID`, or `JSON` |
| Metadata field | `format` | Optional type-compatible format |
| Metadata field | `description` | Optional, maximum 500 characters |
| Metadata field | `destinations` | One or more of `VECTOR_METADATA`, `LLM_CONTEXT`, `API_RESPONSE` |
| Metadata field | `priority` | Optional integer from 0 through 100 |
| Metadata field | `required` | Optional boolean |
| Metadata field | `sanitizePII` | Optional Java/JSON model property; requires the configured PII capability and fails closed |

Generate `sanitize-pii` in YAML and bind it to the `sanitizePII` model property. Lock that
serialization and binding behavior with a Platform compiler round-trip test and a packaged-runtime
smoke test before exposing the field in the editor.

### Removed concepts

Do not emit these properties in a `0.4` artifact:

```text
entity-type
features
auto-process
enable-search
enable-recommendations
auto-embedding
indexable
embeddable-fields
crud-operations
include-in-rag
enable-semantic-search
weight
include-in-search
metadata-fields[].type
```

### Conversion rules derived from 0.3.1

The converter must apply explicit, test-backed rules:

| `0.3.1` value | `0.4.0` result |
| --- | --- |
| `indexable: true` | `indexing.enabled: true` |
| `indexable: false` | `indexing.enabled: false` |
| missing `indexable` | `indexing.enabled: true`, matching the old default |
| searchable field with missing `enable-semantic-search` | Add `SEMANTIC_SEARCH`, matching the old default |
| `enable-semantic-search: true` | Add `SEMANTIC_SEARCH` |
| `enable-semantic-search: false` | Do not add `SEMANTIC_SEARCH` for that field |
| searchable field with missing `include-in-rag` | Add `RAG_CONTEXT`, matching the old default |
| `include-in-rag: true` | Add `RAG_CONTEXT` |
| `include-in-rag: false` | Do not add `RAG_CONTEXT` |
| searchable `weight` | Drop and emit `WEIGHT_REMOVED`; do not claim it maps to similarity ranking |
| searchable list order | Preserve and assign descending priorities only to preserve bounded projection order |
| `embeddable-fields` | Do not auto-merge; old push Data Sync ignored this list. Emit a manual-review warning |
| metadata `include-in-search: true` or missing | Add `VECTOR_METADATA` |
| metadata `include-in-search: false` | Drop unless an explicit new destination is approved |
| metadata `type: string` | `data-type: STRING` |
| metadata `type: number` | `data-type: NUMBER` |
| metadata `type: boolean` | `data-type: BOOLEAN` |
| metadata `type: date` | `data-type: DATE` |
| metadata `type: id` | `data-type: ID` |
| metadata `type: enum` | `data-type: ENUM` |
| metadata `type: json` | `data-type: JSON` |
| unknown metadata type | Block automatic migration and require an operator decision |
| CRUD work flags | Remove; trusted push operations already declare `UPSERT` or `DELETE` |
| old analysis flags | Do not infer hidden analysis; default `analysis.enabled: false` |

If conversion leaves an enabled entity with no `SEMANTIC_SEARCH` field, mark the conversion blocked.
Do not publish that draft.

`priority` is projection order and bounded retention. It is not a replacement for weighted
similarity.

### Generic document vector space

The runtime's generic document configuration must name the `content` key because direct Data Sync
content is exposed to projection under that name:

```yaml
ai-entities:
  document:
    indexing:
      enabled: true
      max-characters: 8000
    analysis:
      enabled: false
      after: []
    searchable-fields:
      - name: content
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: CLEAN
        priority: 100
        required: true
```

### Ecommerce bootstrap pattern

The migrated bootstrap should follow this shape:

```yaml
ai-config:
  vector-dimensions: 512

ai-entities:
  product:
    indexing:
      enabled: true
      max-characters: 8000
    analysis:
      enabled: false
      after: []
    searchable-fields:
      - name: name
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 100
        required: true
      - name: description
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: CLEAN
        priority: 90
      - name: category
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 80
      - name: tags
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 70
      - name: sku
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 60
        required: true
    metadata-fields:
      - name: sku
        data-type: ID
        destinations: [VECTOR_METADATA, API_RESPONSE]
        priority: 100
        required: true
      - name: category
        data-type: STRING
        destinations: [VECTOR_METADATA]
        priority: 80
      - name: price
        data-type: NUMBER
        destinations: [VECTOR_METADATA, API_RESPONSE]
        priority: 80
      - name: currency
        data-type: STRING
        destinations: [VECTOR_METADATA, API_RESPONSE]
        priority: 80
      - name: inStockQty
        data-type: NUMBER
        destinations: [VECTOR_METADATA]
        priority: 70

  policy:
    indexing:
      enabled: true
      max-characters: 8000
    analysis:
      enabled: false
      after: []
    searchable-fields:
      - name: title
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 100
        required: true
      - name: text
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: CLEAN
        priority: 90
        required: true
      - name: classification
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 70
    metadata-fields:
      - name: classification
        data-type: STRING
        destinations: [VECTOR_METADATA]
        priority: 80
      - name: title
        data-type: STRING
        destinations: [API_RESPONSE]
        priority: 70

  review:
    indexing:
      enabled: true
      max-characters: 8000
    analysis:
      enabled: false
      after: []
    searchable-fields:
      - name: text
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: CLEAN
        priority: 100
        required: true
      - name: sku
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 80
        required: true
      - name: rating
        destinations: [SEMANTIC_SEARCH, RAG_CONTEXT]
        preprocessing: NORMALIZE
        priority: 60
    metadata-fields:
      - name: sku
        data-type: ID
        destinations: [VECTOR_METADATA]
        priority: 100
        required: true
      - name: rating
        data-type: NUMBER
        destinations: [VECTOR_METADATA, API_RESPONSE]
        priority: 80
```

Add `tenantId` as required `VECTOR_METADATA` wherever a deployment shares a vector resource across
tenants. The value must come from authenticated application/platform context or approved source
mapping, not from an untrusted browser field.

## Required Platform Implementation

### Workstream 1: one immutable framework version

- [ ] Set `ai-fabric.framework.version` to `0.4.0` in
  `ai-infrastructure-module/pom.xml`.
- [ ] Set `ai-fabric.version` to `0.4.0` in `ai-fabric-product/pom.xml`.
- [ ] Update the runtime POM comment that still names `0.3.1`.
- [ ] Remove production Docker cloning of framework `main`, or pin the exact
  `ai-fabric-framework-v0.4.0` tag and make the product BOM resolve the same version.
- [ ] Update both embedding-worker Dockerfiles from the `0.3.1` tag.
- [ ] Update packaging tests that currently assert `AI_FABRIC_FRAMEWORK_REF=main`.
- [ ] Record the resolved framework version and product source commit in runtime build metadata.
- [ ] Add a release verification check that compares expected and live framework versions.

Preferred production model:

```text
LoomAI product source commit
  + ai-fabric-bom:0.4.0 from Maven Central
  -> reproducible product image
```

Keep source-built framework images only as an explicit pre-release verification target, never the
default production path.

Verify the selected versions:

```bash
mvn -q -f ai-infrastructure-module/pom.xml \
  help:evaluate \
  -Dexpression=ai-fabric.framework.version \
  -DforceStdout

mvn -q -f ai-fabric-product/pom.xml \
  help:evaluate \
  -Dexpression=ai-fabric.version \
  -DforceStdout

mvn -f ai-infrastructure-module/pom.xml \
  -pl ai-fabric-runtime \
  dependency:tree \
  -Dincludes=io.github.loom-ai-labs

mvn -f ai-fabric-product/pom.xml \
  dependency:tree \
  -Dincludes=io.github.loom-ai-labs
```

The output must contain `0.4.0` and no `0.3.x` AI Fabric artifact.

### Workstream 2: typed Platform entity model

Create one Platform-owned `0.4` entity configuration model used by:

- the REST request contract;
- the Knowledge editor;
- draft validation;
- artifact compilation;
- output hashing;
- migration preview.

Do not maintain separate editor, validator, compiler, and hashing interpretations.

The model must represent:

```text
EntityProjectionConfig
  indexing
  analysis
  searchableFields[]
  metadataFields[]
```

Use the released `ai-fabric-core:0.4.0` public DTO/enums where practical so enum values are not copied
as free-form strings. Platform validation still owns deployment concerns such as vector dimensions,
selected PII capability, tenant policy, and provider limits.

### Workstream 3: Knowledge editor

Replace the old comma-separated editor with typed repeated rows:

- entity indexing toggle;
- maximum projected characters;
- searchable field name;
- destination multi-select;
- preprocessing select;
- maximum length;
- priority;
- required toggle;
- metadata name;
- data type select;
- destination multi-select;
- optional format and description;
- priority;
- required and PII sanitization toggles.

Remove `embeddableFields` from `EntityEditorState`. In `0.4`, embedding input is the searchable
projection that includes `SEMANTIC_SEARCH`.

Required UI behavior:

- [ ] An enabled entity cannot be saved without a semantic-search field.
- [ ] Invalid priorities, data types, duplicate names, and empty destinations are visible inline.
- [ ] Selecting `LLM_CONTEXT` or `API_RESPONSE` shows that the value leaves vector-only storage.
- [ ] Selecting PII sanitization shows the required runtime capability.
- [ ] The generated YAML preview contains no legacy key.
- [ ] Advanced/raw editing, if retained, passes the same backend validator before saving.
- [ ] Saving never silently preserves a legacy property.

### Workstream 4: backend validation

Extend `DeploymentDraftValidationService.validateEntities` to:

1. reject every removed `0.3` key with a stable issue code;
2. validate the complete `0.4` model;
3. require `indexing.enabled=true` for vectorization/Data Sync entity scopes;
4. require at least one `SEMANTIC_SEARCH` destination;
5. validate enum values and numeric bounds;
6. reject duplicate field names case-insensitively;
7. ensure analysis operations exist when analysis is enabled;
8. ensure PII sanitization is compatible with the deployment's selected modules;
9. ensure tenant-filter fields are required metadata for shared vector resources;
10. return JSON paths suitable for direct UI field highlighting.

Add tests to `DeploymentDraftValidationServiceTest` for every rule and every legacy property.

Draft publishing must fail when the entity contract is invalid. Runtime startup is the final
authority, but it must not be the first validator.

### Workstream 5: deterministic stored-config migrator

Implement an idempotent service such as:

```text
EntityConfigV03ToV04Migrator
  preview(config) -> MigrationReport
  migrate(config) -> MigratedConfig + MigrationReport
```

The report must include:

- source and target contract versions;
- before and after hashes;
- converted entity types;
- dropped keys;
- warnings such as removed weights and embeddable fields;
- blocking unknown metadata types;
- entities left without semantic-search destinations;
- whether vector rebuild is required.

The migrator must be deterministic: running it twice produces the same output and second-run report
must say that no migration is required.

Recommended persistence additions:

- `entity_config_contract_version` on deployment drafts;
- `entity_config_contract_version` on deployment versions;
- `ai_fabric_framework_version` on deployment versions or their immutable manifest;
- an audit record containing before/after hashes and migration result.

Backfill existing records as `AI_ENTITY_CONFIG_V0_3`. New migrated drafts and newly published
versions use `AI_ENTITY_CONFIG_V0_4`.

Migration policy:

- Mutable drafts may be converted after backup and a successful preview.
- Published versions remain unchanged and retain `V0_3`.
- Create a new published version from the migrated draft.
- An active `V0_3` version remains attached to its `0.3.1` runtime until its own cutover.
- Provisioning rejects a runtime/artifact contract mismatch.

Do not use a database-only search-and-replace for nested JSON. The transformation requires defaults,
type mapping, warning generation, and semantic validation.

### Workstream 6: compiler and artifact guard

Update `DeploymentConfigCompiler` and provisioning so the immutable manifest records:

```json
{
  "aiFabricFrameworkVersion": "0.4.0",
  "entityConfigContractVersion": "AI_ENTITY_CONFIG_V0_4"
}
```

Before publishing:

- validate the typed config;
- generate YAML;
- parse the generated YAML back into the typed model;
- validate the round trip;
- hash the validated representation;
- reject all legacy keys.

Before provisioning:

- compare runtime framework version with artifact contract version;
- refuse an incompatible deployment;
- ensure `AI_CONFIG_DEFAULT_FILE` points to the newly published `V0_4` artifact;
- include the exact deployment version ID and config hash in release evidence.

### Workstream 7: indexed-output hash

Update `VectorizationIndexedOutputHashService` to include every property that can change the
canonical projected output:

```text
entity type
indexing.enabled
indexing.maxCharacters
analysis policy when applicable
searchable name
searchable destinations
searchable preprocessing
searchable maxLength
searchable priority
searchable required
metadata name
metadata dataType
metadata format
metadata description
metadata destinations
metadata priority
metadata required
metadata sanitizePII
ai-config values affecting embedding/vector shape
provider configuration affecting embeddings
```

Canonicalize maps, lists, and enum sets before hashing. Add a dedicated
`VectorizationIndexedOutputHashServiceTest` proving:

- property order does not change the hash;
- every behavior-affecting property does change it;
- display-only Platform metadata does not change it;
- old `weight` and `type` are rejected, not hashed.

### Workstream 8: Data Sync result classification

Parse the response body before handling the HTTP status in
`ConnectorDataSyncTargetWriter`.

Use a typed failure model containing:

```text
httpStatus
errorCode
message
vectorSpace
entityId
indexingWorkId
indexingStatus
retryDisposition
durableHandoffAccepted
```

Required handling:

| Result | Platform behavior |
| --- | --- |
| `PROJECTION_REJECTED` | Permanent configuration/data failure; do not retry unchanged input |
| `ACCESS_DENIED` | Permanent until identity/policy changes; surface policy evidence |
| `VECTOR_SPACE_NOT_FOUND` | Contract/deployment drift; stop the run |
| `VECTOR_SPACE_NOT_INDEXABLE` | Contract/deployment drift; stop the run |
| `INDEXING_SUBMISSION_FAILED` | No confirmed durable handoff; safe runner retry using stable identity/version |
| `INDEXING_RETRYABLE` | Runtime accepted durable work; do not report vector success and do not blindly duplicate submission |
| `INDEXING_PERMANENT` | Operator review/dead-letter path |
| Batch `200` with failed results | Classify every failed operation using its own `errorCode` |

For `INDEXING_RETRYABLE`, retain `indexingWorkId` and reconcile runtime queue completion before
advancing the source checkpoint or representing the vector as synchronized.

Add `ConnectorDataSyncTargetWriterTest` coverage for:

- non-2xx JSON body preservation;
- projection rejection;
- access denial;
- durable retryable outcome;
- permanent outcome;
- submission failure;
- mixed batch results;
- malformed or empty error bodies without leaking request content.

### Workstream 9: operational diagnostics

Expose the sanitized `aifabricEntities` diagnostics only through an intentional private operator
surface. Do not publish it broadly merely to make the migration easier.

Release verification must prove:

- live AI Fabric framework version;
- product source commit;
- entity artifact URL;
- deployment version ID;
- entity contract version;
- exact supported entity types;
- projection/descriptor hashes;
- queue readiness and counts;
- vector counts by entity type;
- provider identity and embedding dimensions.

The existing `RuntimeAdminOverviewController`, private assertion mechanism, and Platform release
verification service are the preferred boundary.

## Runtime State Cutover

AI Fabric `0.4` queue payloads and ordering state are not compatible with `0.3`.

### Inventory each deployment

Before touching a runtime, record:

- deployment ID and customer/tenant scope;
- active deployment version and entity artifact URL;
- live product commit and AI Fabric version;
- runtime database mode and resource ID;
- vector provider, collection/index/namespace, dimensions, and resource ownership;
- source connection and current vectorization checkpoint;
- source record counts by entity type;
- vector counts by entity type;
- outstanding vectorization runs;
- old queue counts by status;
- backup identifiers and restoration instructions.

No deployment enters cutover with a vectorization run or release already in progress.

### Stop writers

For the target deployment:

1. stop or pause its vectorization runner;
2. block new Data Sync ingress;
3. stop all old runtime instances and indexing workers;
4. prevent release/publish changes during the maintenance window;
5. capture a source high-water mark if the authoritative source cannot be paused.

Do not allow `0.3` and `0.4` workers to share one runtime database.

### Back up

Back up:

- Platform database;
- target runtime database;
- external vector collection/index/namespace where provider tooling permits;
- active `0.3` entity artifact and manifest;
- current Platform source and image identifiers;
- source/vector count report.

A backup is not accepted until a restore procedure is known and, for the first canary, rehearsed.

### Replace queue and ordering tables

With all runtime instances stopped, inspect and then replace:

```sql
DROP TABLE IF EXISTS ai_indexing_queue;
DROP TABLE IF EXISTS ai_indexing_entity_state;
```

Run this only against the selected runtime database after backup verification. Do not run it against
the Platform database.

Allow the reviewed application migration mechanism or the `0.4` runtime startup to create the new
schema. Startup must fail if an incompatible old table remains.

The runtime currently uses Hibernate `ddl-auto=update`. For managed production deployments, prefer a
reviewed runtime schema migration over relying permanently on automatic schema updates.

### Clear generated vectors

Generated vectors must be rebuilt from authoritative source records.

LoomAI already has a governed clear path:

```text
POST /api/deployments/{deploymentId}/poc/reset/runtime-vectors
```

It ultimately calls the runtime's private:

```text
POST /api/admin/migration/clear?confirm=true
```

Use the Platform operator/remediation path so confirmation, authorization, reason, and audit evidence
are retained.

Before clearing, prove the vector resource or namespace is dedicated to the selected deployment. A
global clear is prohibited for a shared customer or tenant resource. For shared resources, provision
a new namespace/index or perform a correctly filtered deletion.

### Deploy and backfill

1. Deploy the new `V0_4` artifact with the `0.4.0` runtime.
2. Verify health and private runtime diagnostics.
3. Verify the exact expected vector-space list.
4. Start a full vectorization/backfill from authoritative source records.
5. If source writes continued, run a high-water-mark catch-up.
6. Wait for runtime queue completion, including durable retries.
7. Verify source count, accepted count, completed count, dead letters, and vector count.
8. Resume incremental Data Sync only after reconciliation passes.

## Verification Gates

### Gate 1: stale-contract scan

Run:

```bash
rg -n \
  'features:|auto-process:|enable-search:|auto-embedding:|indexable:|crud-operations:|include-in-rag:|enable-semantic-search:|weight:|embeddable-fields:|include-in-search:' \
  ai-infrastructure-module ai-fabric-product Platfrom product-services \
  --glob '!**/target/**' \
  --glob '!**/node_modules/**' \
  --glob '*.{yml,yaml,json,java,ts,tsx}'
```

Classify non-entity uses rather than deleting similarly named provider/application settings. The
gate fails when any generated or runtime entity contract still uses a removed key.

Also scan metadata declarations for old `type` fields through a structured parser; a broad text scan
would produce unrelated action and marketplace matches.

### Gate 2: deterministic build and tests

Run tests normally:

```bash
mvn -f ai-fabric-product/pom.xml clean verify

mvn -f ai-infrastructure-module/pom.xml clean verify

mvn -f Platfrom/backend/pom.xml clean verify

npm ci --prefix Platfrom/ui
npm run build --prefix Platfrom/ui
```

Required focused coverage:

- runtime typed entity config and vector-space endpoint;
- trusted backend auth;
- Platform draft validation;
- configuration conversion and idempotency;
- compiler YAML round trip;
- runtime/artifact compatibility guard;
- indexed-output hash;
- Data Sync response classification;
- release verification;
- Docker packaging assertions.

No failed test may be explained away as a version migration caveat.

### Gate 3: packaged runtime

Build the runtime image from the same source state that passed tests:

```bash
docker build \
  -f ai-infrastructure-module/ai-fabric-runtime/Dockerfile \
  -t loomai/ai-fabric-runtime:0.4.0-candidate \
  .
```

Verify its dependency/build metadata reports:

```text
AI Fabric: 0.4.0
entity contract: AI_ENTITY_CONFIG_V0_4
LoomAI source commit: expected immutable commit
```

Test the packaged image, not only the IDE classpath.

### Gate 4: canary Data Sync lifecycle

Use a non-production canary deployment with real provider credentials stored only in the deployment
secret manager. Required secrets depend on the selected provider; for OpenAI:

```text
OPENAI_ENABLED=true
OPENAI_API_KEY=<secret manager value>
OPENAI_MODEL=<approved model>
OPENAI_EMBEDDING_MODEL=<approved embedding model>
OPENAI_EMBEDDING_DIMENSIONS=<matching vector dimensions>
```

Do not place provider keys in the runbook, source control, test output, or migration report.

The canary must prove:

1. exact expected vector spaces are returned;
2. an upsert creates one current vector;
3. an update replaces old evidence and preserves the latest source version;
4. a delete removes retrieval evidence;
5. RAG answers from updated evidence;
6. deleted evidence is not returned to the LLM;
7. metadata filters preserve tenant/deployment scope;
8. invalid required data returns `PROJECTION_REJECTED`;
9. provider failure is visible as retryable/permanent, never success;
10. retry completion is reconciled to the source checkpoint;
11. queue and vector counts agree after backfill;
12. runtime restart preserves queue and ordering state.

### Gate 5: Platform end-to-end

From the Platform UI:

1. open a canary deployment;
2. edit a typed searchable field;
3. save the draft;
4. validate it;
5. preview generated YAML;
6. publish a new immutable version;
7. observe `reindexRequired=true`;
8. deploy the version;
9. run vectorization;
10. inspect runtime/vector counts;
11. query through the actual chat/RAG path;
12. delete a source item and prove it disappears.

The UI and backend must show explicit failure when provider or projection work fails. No fallback may
hide a migration defect.

## Deployment Order

Use this order:

1. Platform backend schema additions and migrator in dormant/dry-run mode.
2. Platform validation and compiler support for both persisted version labels, while new publishing
   remains on `0.3`.
3. Platform UI typed editor.
4. Product service dependency and Docker reproducibility changes.
5. All deterministic and packaged tests.
6. Dry-run report for every draft and active version.
7. Canary draft conversion and new `V0_4` version publication.
8. Canary runtime state replacement and backfill.
9. Canary observation period.
10. Staging cohort rollout.
11. Production deployment cohorts, one vector resource at a time.
12. Remove `0.3` publishing only after every rollback window closes.

Do not bulk-convert live active versions before the canary proves the full sequence.

## Rollback

Rollback is a deployment/database/vector restoration, not a JAR downgrade.

### Roll back a canary or deployment

1. stop the `0.4` runtime, vectorization runner, and Data Sync ingress;
2. restore the pre-cutover runtime database backup;
3. restore the prior vector resource snapshot, or clear and rebuild it using the `0.3` source and
   contract;
4. reactivate the unchanged `V0_3` deployment version and entity artifact;
5. deploy the exact prior LoomAI image and AI Fabric `0.3.1`;
6. verify version, vector spaces, counts, update/delete, and RAG;
7. resume writers only after verification;
8. record the failed gate and retain `0.4` diagnostics.

Never run a `0.3` worker against a `0.4` queue or reuse a `0.4` generated vector set as proof of a
successful `0.3` rollback.

### Roll back Platform control-plane changes

Old published versions remain immutable, which provides the artifact rollback boundary. If the
Platform database schema or migrated drafts must also be reverted, restore the Platform backup or use
the audited before image from the migration record.

Do not reverse-convert a `0.4` config heuristically. Restore its exact prior `0.3` representation.

## Production Completion Criteria

The migration is complete only when:

- [ ] Both LoomAI Maven reactors resolve only AI Fabric `0.4.0`.
- [ ] Production Docker builds use one immutable framework source.
- [ ] No runtime/generated entity artifact contains a removed `0.3` property.
- [ ] Knowledge editor creates a complete typed `0.4` projection.
- [ ] Backend validation rejects every known stale form.
- [ ] Draft conversion is deterministic, idempotent, audited, and previewable.
- [ ] Published `0.3` versions remain immutable.
- [ ] Runtime/artifact compatibility is enforced before deployment.
- [ ] Indexed-output hashing covers the complete projected contract.
- [ ] Data Sync failures preserve error codes and durable work evidence.
- [ ] Runtime queue/state tables were replaced per migrated deployment.
- [ ] Generated vectors were rebuilt from authoritative sources.
- [ ] Source, queue, and vector counts reconcile.
- [ ] Update and delete behavior is proven.
- [ ] Tenant filtering and PII policy are proven where configured.
- [ ] Packaged and Docker tests pass.
- [ ] A real-provider canary passes without hidden fallback.
- [ ] Rollback was rehearsed and recorded.
- [ ] Live release evidence reports the exact product commit, framework version, contract version,
  deployment version, artifact hash, and provider dimensions.

## Suggested Delivery Slices

### Slice A: make invalid state impossible

- version pins;
- source-controlled YAML;
- typed backend model;
- validator;
- compiler round-trip;
- runtime/artifact guard.

### Slice B: migrate Platform ownership

- Knowledge editor;
- stored-draft migrator;
- contract/version metadata;
- immutable new deployment versions;
- indexed-output hash.

### Slice C: make failure operational

- typed Data Sync target failures;
- queue reconciliation;
- private diagnostics;
- release evidence and deployment inventory.

### Slice D: execute the cutover

- canary;
- state replacement;
- backfill;
- live verification;
- staged production cohorts;
- rollback-window closure.

Do not begin Slice D until Slices A through C have passed their deterministic and packaged gates.
