# AI Fabric 0.5.0 Migration Current-State Evidence

Status: **BASELINE CAPTURED - GATE A BLOCKED, GATE B PASSED**

Evidence date: 2026-07-30
Timezone: Europe/London
Target product branch: `Platform-V11`

This report records the state observed before changing LoomAI Platform code for
the AI Fabric `0.5.0` adoption. It contains no secret values.

## 1. Repository Identity

| Repository | Branch | Commit |
| --- | --- | --- |
| LoomAI Platform product repository | `Platform-V11` | `a4563cda56e2bf6bb3955c711254a7c68e458b3e` |
| Public AI Fabric framework repository | `main` | `13a37860bc9a0e4e11928be89f356d7ee185bc56` |

At the initial capture, the public framework worktree contained externally
created release-preparation changes. The migration did not modify, discard,
stage, or commit those changes.

Publication update:

| Item | Published value |
| --- | --- |
| Release | AI Fabric `0.5.0` |
| Tag | `ai-fabric-framework-v0.5.0` |
| Commit | `a49138c6bff39c66bf48c3885cb911e8d7b78d84` |
| GitHub Release | `https://github.com/Loom-AI-Labs/ai-fabric-framework/releases/tag/ai-fabric-framework-v0.5.0` |
| Maven Central proof | BOM and execution JAR resolved from a fresh repository; out-of-tree consumer passed 2/2 tests |

The Platform worktree contained the untracked controlling adoption prompt
before this migration evidence folder was added:

```text
Final_Documentation/Development_Guides/LLM-guides/
  LOOMAI_PLATFORM_AI_FABRIC_ADOPTION_CODING_ASSISTANT_PROMPT.md
```

## 2. Current Framework Consumption

| Consumer reactor | Property | Observed value |
| --- | --- | --- |
| `ai-infrastructure-module/pom.xml` | `ai-fabric.framework.version` | `0.3.1` |
| `ai-fabric-product/pom.xml` | `ai-fabric.version` | `0.3.1` |
| `ai-infrastructure-module/ai-fabric-runtime/pom.xml` | framework comment | `AI Fabric 0.3.1 framework modules` |

The runtime does not yet consume the `ai-fabric-execution` aggregate required
for the first `0.5.0` specialist.

Audit command:

```bash
rg -n -C 2 \
  '<ai-fabric.version>|<ai-fabric.framework.version>|AI Fabric 0\.3\.1' \
  ai-fabric-product/pom.xml \
  ai-infrastructure-module/pom.xml \
  ai-infrastructure-module/ai-fabric-runtime/pom.xml
```

## 3. Source Entity-Contract Audit

The following source-controlled files contain removed `0.3` lifecycle or field
projection properties and must be migrated to the explicit `0.4` contract:

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

Observed legacy properties include:

```text
auto-process
enable-search
auto-embedding
indexable
crud-operations
include-in-rag
weight
type
```

The similarly named settings in general application configuration are not
automatically entity-contract fields. Each occurrence must be classified by
its owning configuration type before removal.

No active Java use of the removed entity lifecycle annotations or APIs was
found in the audited Platform product source. The principal migration surface
is therefore configuration, persisted state, compilation, validation, hashing,
and runtime packaging.

## 4. Persisted Contract Surface

The initial Platform schema persists entity contracts in:

```text
platform_deployment_drafts.entity_config_json
platform_deployment_versions.entity_config_json
platform_deployment_versions.entity_artifact_yaml
```

Relevant owners include:

```text
DeploymentDraftEntity
DeploymentVersionEntity
DeploymentConfigCompiler
DeploymentDraftValidationService
DeploymentService
DeploymentReleaseVerificationService
```

No explicit entity-contract version or AI Fabric framework version is present
on the initial draft/version records. Existing immutable `0.3` versions must
remain identifiable and reproducible; they must not be silently rewritten as
`0.4`.

## 5. Platform Contract-Owner Gaps

| Area | Current observation | Migration requirement |
| --- | --- | --- |
| Knowledge editor | `KnowledgePage.tsx` represents the old simplified model | Author the complete typed `0.4` projection and lifecycle contract |
| Draft validation | `validateEntities` performs shallow shape checks | Reject unknown legacy keys and validate the complete `0.4` schema |
| Artifact compiler | Stored entity JSON is serialized directly to YAML | Compile a normalized versioned contract deterministically |
| Immutable manifest | Framework/entity contract version is not recorded | Include both versions and hashes in release evidence |
| Indexed-output hash | Includes removed field properties such as weight/type | Hash only normalized output-affecting `0.4` projection semantics |
| Data Sync writer | Throws on HTTP status before preserving bounded response facts | Retain safe framework failure code/details for operators |

## 6. Product Image Reproducibility Audit

The following product-image families still clone framework source and install
it inside Docker builds:

```text
ai-infrastructure-module/ai-fabric-runtime/Dockerfile
ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile
ai-infrastructure-module/ai-infrastructure-generic-rest-connector/Dockerfile
ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile
ai-infrastructure-module/ai-fabric-relay/Dockerfile
ai-fabric-product/ai-fabric-embedding-worker/deploy/container/Dockerfile
ai-fabric-product/ai-fabric-embedding-worker/deploy/railway/Dockerfile
```

Runtime, connector, and relay paths default to framework `main`.
Embedding-worker paths default to `ai-fabric-framework-v0.3.1`.

This prevents a product image from proving that it uses only published Maven
artifacts. The migration must remove framework checkout/install steps from
normal product Docker builds. `-DskipTests` may remain in a packaging-only
stage, but that stage cannot count as verification evidence.

## 7. Gate Classification

### Gate A - Stable `0.4.0` baseline

Status: **BLOCKED**

Blocking evidence:

- both product reactors resolve `0.3.1`;
- source entity contracts retain removed lifecycle properties;
- persisted drafts and immutable versions have no explicit contract version;
- Platform editor/compiler/validator/hash paths do not own the `0.4` model;
- product Docker builds still compile framework source.

### Gate B - Published `0.5.0` artifacts

Status: **PASSED**

Passing evidence:

- the remote `ai-fabric-framework-v0.5.0` tag resolves to the immutable release
  commit;
- the GitHub Release and Maven Central workflow completed successfully;
- the BOM and execution JAR resolve from a new Maven repository with `central`
  recorded as their origin; and
- an out-of-tree standalone consumer passed 2/2 tests using only AI Fabric
  `0.5.0`.

## 8. Baseline Decision

Do not introduce the `0.5.0` specialist into the current `0.3.1` Platform
runtime. Complete and prove the `0.4.0` entity lifecycle cutover first. Adopt
`0.5.0` only from publicly resolvable Maven artifacts and keep the initial
specialist read-only, synchronous, tenant-bound, document-scoped, and separate
from existing chat endpoints.
