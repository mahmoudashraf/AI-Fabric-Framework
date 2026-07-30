# Gate A Evidence - Stable AI Fabric 0.4.0 Baseline

Status: **BLOCKED - IMPLEMENTATION NOT STARTED**

Target: a live, reproducible Platform baseline using AI Fabric `0.4.0` entity
lifecycle semantics before any `0.5.0` specialist is enabled.

This document is both the file-level execution map and the evidence checklist.
Items change to passed only when their commands and results are recorded.

## 1. Source Contract Migration

Files:

```text
ai-infrastructure-module/ai-fabric-runtime/src/main/resources/
  ai-entity-config.yml
ai-infrastructure-module/ai-fabric-runtime/src/test/resources/
  test-runtime-entity-config.yml
ai-infrastructure-module/ai-fabric-runtime/deploy/docker/customer-template/
  config/ai-entity-config.yml
Platfrom/backend/src/main/resources/bootstrap/ecommerce-demo/runtime/config/
  ai-entity-config.yml
```

Required work:

- replace removed implicit lifecycle flags with explicit `lifecycle` policy;
- express each indexed field with `destinations`;
- preserve only supported transform/projection semantics;
- explicitly configure Data Sync where trusted ingestion requires it;
- reject removed keys rather than retaining them in an extension map;
- classify similarly named non-entity application settings before editing.

Required focused proof:

- production runtime config binds;
- runtime test config binds;
- customer template binds;
- ecommerce bootstrap config binds;
- each removed key fails with a precise validation error;
- no removed entity-contract property remains in supported source YAML.

## 2. Typed Platform Contract Ownership

Primary owners:

```text
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/
  service/DeploymentDraftValidationService.java
  service/DeploymentConfigCompiler.java
  entity/DeploymentDraftEntity.java
  entity/DeploymentVersionEntity.java
Platfrom/ui/src/pages/KnowledgePage.tsx
```

Required model:

- entity contract version;
- lifecycle policy for ingest, analysis, and retrieval;
- searchable field name;
- field destinations;
- transform configuration where supported;
- validation diagnostics;
- deterministic normalized serialization.

Required behavior:

- Platform is the typed owner of the supported framework contract;
- editor, request DTOs, persisted JSON, validator, compiler, and generated YAML
  agree on one schema;
- unknown or removed lifecycle properties fail closed;
- normalized output is deterministic;
- immutable release artifacts record the framework and entity-contract
  versions used to compile them.

Existing focused tests to extend:

```text
Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/
  service/DeploymentDraftValidationServiceTest.java
  service/DeploymentConfigCompilerTest.java
```

New focused tests required:

- complete `0.4` happy-path contract;
- unknown-key rejection;
- every removed `0.3` key rejection;
- destination validation;
- lifecycle dependency/order validation;
- deterministic normalization;
- manifest version metadata;
- old immutable version remains unchanged.

## 3. Persisted Draft And Version Migration

Persisted columns:

```text
platform_deployment_drafts.entity_config_json
platform_deployment_versions.entity_config_json
platform_deployment_versions.entity_artifact_yaml
```

Required database work:

- add explicit entity-contract version metadata;
- add framework version metadata where release reproducibility requires it;
- implement a deterministic `0.3` draft-to-`0.4` migration service;
- migrate editable drafts through the typed model;
- retain immutable historical release payloads and label their source contract;
- create a new `0.4` version on publish instead of mutating an old version;
- make migration idempotent and auditable;
- provide a dry-run/count report before writes.

Next Flyway version must be selected from the actual branch state at
implementation time. The current highest observed migration is `V126`; do not
pre-claim `V127` if another concurrent change takes it first.

Required migration proof:

- blank/new draft is authored as `0.4`;
- valid `0.3` draft migrates deterministically;
- repeated migration is a no-op;
- invalid/ambiguous legacy data is quarantined with a bounded diagnostic;
- immutable `0.3` version bytes do not change;
- publishing a migrated draft creates a separately versioned `0.4` artifact;
- export/import preserves contract metadata and artifact bytes.

## 4. Indexed-Output Hash Semantics

Primary owner:

```text
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/
  service/VectorizationIndexedOutputHashService.java
```

Required work:

- remove legacy `embeddable-fields`, field `weight`, and metadata `type` from
  the normalized hash input;
- hash output-affecting field destinations, transforms, and projection order;
- avoid hash changes for non-output metadata;
- document when migration requires reindexing.

Required proof:

- output-affecting destination change changes the hash;
- output-affecting transform change changes the hash;
- non-output description/UI change does not change the hash;
- same normalized contract in different JSON key order yields the same hash;
- old/new hash transition is visible in deployment evidence.

## 5. Data Sync Failure Semantics

Primary owner:

```text
ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/
  vectorization/runner/service/ConnectorDataSyncTargetWriter.java
```

Existing focused test:

```text
ai-fabric-product/ai-fabric-vectorization-runner/src/test/java/com/ai/fabric/
  vectorization/runner/service/ConnectorDataSyncTargetWriterTest.java
```

Required work:

- parse bounded framework error content before throwing for non-2xx responses;
- preserve stable safe error code, request ID, retryability, and bounded detail;
- never expose credentials, raw provider secrets, or unbounded bodies;
- distinguish validation, authorization, embedding, vector-store, and transient
  transport failures where the framework contract supports them.

Required proof:

- representative `4xx` contract error is classified;
- representative `5xx` transient error is classified;
- oversized body is bounded;
- secret-like fields are not propagated;
- successful writes retain existing behavior.

## 6. Reproducible Maven Consumption And Images

Product parent POMs:

```text
ai-infrastructure-module/pom.xml
ai-fabric-product/pom.xml
```

Docker families to migrate:

```text
ai-infrastructure-module/ai-fabric-runtime/Dockerfile
ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile
ai-infrastructure-module/ai-infrastructure-generic-rest-connector/Dockerfile
ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile
ai-infrastructure-module/ai-fabric-relay/Dockerfile
ai-fabric-product/ai-fabric-embedding-worker/deploy/container/Dockerfile
ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile
```

Required work:

- resolve framework `0.4.0` from Maven repositories through one version
  property/BOM;
- remove framework Git clone and source install steps;
- preserve Maven credentials as BuildKit/CI secrets where required;
- keep Docker packaging independent of a sibling framework checkout;
- embed source commit/build metadata for live readback.

Required proof:

- clean Maven repository resolves one AI Fabric version;
- dependency tree contains no mixed `0.3.x`/`0.4.x`;
- product reactors compile and test without framework source present;
- Docker builds succeed with framework repository directory unavailable;
- image filesystem contains no framework source checkout;
- live health/build info reports the expected product commit and framework
  version.

## 7. Safe Live Cutover

The baseline deployment procedure must:

1. inventory affected deployments and current version IDs;
2. export the target deployment configuration and release artifacts;
3. record database rows and vector namespace/collection identifiers;
4. dry-run draft migration and compare counts;
5. publish a new `0.4` version without mutating the previous release;
6. deploy an isolated canary;
7. run Data Sync and verify nonzero per-space counts;
8. run retrieval and chat canaries;
9. verify tenant isolation and auth failure behavior;
10. promote only after rollback evidence is complete.

Rollback assets:

- previous immutable deployment version;
- exported deployment configuration;
- previous product image digest;
- database backup or row-level restore procedure;
- vector collection/namespace backup or rebuild procedure;
- assignment mapping before cutover.

Rollback is not merely changing a JAR version. Entity artifacts, persisted
draft/version metadata, vector state, and deployment assignment must return to
a mutually compatible point.

## 8. Gate A Completion Table

| Check | Status |
| --- | --- |
| Source entity YAML uses only the explicit `0.4` contract | Pending |
| Removed-key rejection tests pass | Pending |
| Platform owns a typed versioned entity model | Pending |
| Draft migration is deterministic and idempotent | Pending |
| Immutable old releases are unchanged | Pending |
| Compiler emits deterministic versioned artifacts | Pending |
| Indexed-output hash follows `0.4` output semantics | Pending |
| Data Sync exposes safe classified failures | Pending |
| Both product reactors resolve only `0.4.0` | Pending |
| Product Docker builds use published artifacts, not source clones | Pending |
| Clean build and focused integration tests pass | Pending |
| Isolated live baseline canary passes | Pending |
| Backup and rollback drill are evidenced | Pending |

Gate A remains **BLOCKED** until every row is passed with command output,
artifact versions, timestamps, and safe live identifiers recorded here or in
the later build/deployment evidence files.
