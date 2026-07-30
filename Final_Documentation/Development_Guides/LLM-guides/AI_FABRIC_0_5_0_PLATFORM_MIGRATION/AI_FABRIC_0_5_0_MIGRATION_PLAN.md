# LoomAI Platform-V11 AI Fabric 0.5.0 Migration Plan

- **Status:** Active - Gate B passed; Gate A platform migration pending
- **Platform branch:** `Platform-V11`
- **Platform baseline commit:** `a4563cda56e2bf6bb3955c711254a7c68e458b3e`
- **Framework release tag:** `ai-fabric-framework-v0.5.0`
- **Framework release commit:** `a49138c6bff39c66bf48c3885cb911e8d7b78d84`
- **Current platform AI Fabric version:** `0.3.1`
- **Required lifecycle baseline:** `0.4.0`
- **Target release:** `0.5.0`
- **Prepared:** 2026-07-30
- **Classification:** Private platform engineering document

## 1. Objective

Migrate LoomAI Platform and its deployable runtime services to the published AI
Fabric `0.5.0` release without mixing framework versions, consuming mutable
framework source in production images, rewriting immutable deployment versions,
or deploying a runtime over an incompatible entity artifact, indexing queue, or
vector projection.

The migration has two mandatory boundaries:

1. Complete and prove the AI Fabric `0.4.0` entity lifecycle, persistence,
   indexing, Data Sync, and vector cutover.
2. Revalidate published `0.5.0` from Maven Central in a clean consumer
   repository, then move the runtime process to `0.5.0` and adopt the optional
   execution module through one bounded read-only specialist.

The first `0.5.0` proof is:

```text
deployment-knowledge-specialist@1
```

It is additive. Existing chat endpoints remain unchanged.

## 2. Controlling Documents

Use these sources in order:

1. Public APIs, configuration classes, and tests in the published `0.5.0` tag.
2. Published `0.5.0` release notes and migration guides.
3. [Platform adoption prompt](../LOOMAI_PLATFORM_AI_FABRIC_ADOPTION_CODING_ASSISTANT_PROMPT.md).
4. [AI Fabric 0.4 migration runbook](../LOOMAI_PLATFORM_AI_FABRIC_0_4_MIGRATION_RUNBOOK.md).
5. [Platform operating context](../PLATFORM_LLM_SESSION_OPERATING_CONTEXT.md).
6. Older comments, examples, generated files, and cached artifacts.

Never copy an API from framework `main` into Platform merely because it compiles
against a locally installed candidate.

## 3. Non-Negotiable Boundaries

- Use one AI Fabric BOM version in each runtime process.
- Run tests normally. `-DskipTests` is never verification evidence.
- Do not use local `mvn install` as published-consumer proof.
- Do not copy public framework source into this private repository.
- Do not create a second orchestration, action, receipt, review, chat-memory, or
  vector lifecycle.
- Preserve existing `/api/chat/me/query` and `/api/chat/me/query-once` behavior.
- Add `ai-fabric-execution` only to `ai-fabric-runtime` in the first `0.5.0`
  adoption slice.
- Build identity, tenant, deployment, subject, and scopes only from verified
  backend state.
- Keep exact specialist and vector scopes. Production wildcards are prohibited.
- Keep manifests immutable, exact-versioned, bounded, and fail-fast.
- Do not hide provider, retrieval, validation, authorization, persistence, or
  policy failure behind a successful fallback.
- Published `0.3` deployment versions remain immutable.
- Mutable drafts require previewable, deterministic, audited migration.
- Production schema changes are application-owned Flyway/Liquibase migrations.
- No live deployment cutover starts without verified backup and rollback steps.
- No vector clear is allowed until resource or namespace ownership is proven.

## 4. Current-State Evidence

Audit timestamp: 2026-07-30, Europe/London.

### 4.1 Repository identity

| Item | Verified value |
| --- | --- |
| Platform branch | `Platform-V11` |
| Platform commit | `a4563cda56e2bf6bb3955c711254a7c68e458b3e` |
| Platform worktree | Clean except the untracked controlling adoption prompt |
| Framework branch | `main` |
| Framework release tag | `ai-fabric-framework-v0.5.0` |
| Framework release commit | `a49138c6bff39c66bf48c3885cb911e8d7b78d84` |

The lowercase and uppercase framework paths resolve to the same local worktree
on this filesystem.

### 4.2 Current dependency versions

| File | Current state |
| --- | --- |
| `ai-infrastructure-module/pom.xml` | `ai-fabric.framework.version=0.3.1` |
| `ai-fabric-product/pom.xml` | `ai-fabric.version=0.3.1` |
| `ai-fabric-runtime/pom.xml` | Comment still identifies `0.3.1` modules |
| `ai-fabric-runtime/pom.xml` | Does not yet consume `ai-fabric-execution` |

No active LoomAI Java use of the removed annotation/entity lifecycle APIs was
found in the audited source paths.

### 4.3 Stale source-controlled entity contracts

The following remain on the removed `0.3` entity shape:

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

Detected stale properties include `auto-process`, `enable-search`,
`auto-embedding`, `indexable`, `crud-operations`, `include-in-rag`, and
`weight`.

Application-level properties with similar names must be classified rather than
deleted by text search.

### 4.4 Persisted configuration

Entity configuration is stored in:

```text
platform_deployment_drafts.entity_config_json
platform_deployment_versions.entity_config_json
platform_deployment_versions.entity_artifact_yaml
```

Current ownership code includes:

```text
DeploymentDraftEntity
DeploymentVersionEntity
DeploymentConfigCompiler
DeploymentDraftValidationService
DeploymentService
DeploymentReleaseVerificationService
```

The initial database schema does not yet record an explicit entity-contract
version or AI Fabric framework version on these records.

### 4.5 Platform authoring and projection gaps

- `KnowledgePage.tsx` still represents the old simplified field model.
- `DeploymentDraftValidationService.validateEntities` only verifies a shallow
  entity object/optional field shape.
- `DeploymentConfigCompiler` emits the stored entity JSON directly as YAML and
  does not record the framework/entity contract in the immutable manifest.
- `VectorizationIndexedOutputHashService` still hashes `embeddable-fields`,
  searchable `weight`, and metadata `type`.
- `ConnectorDataSyncTargetWriter` throws on non-2xx status before preserving the
  framework's bounded failure body.

### 4.6 Mutable framework source in product images

The runtime, generic connector, relay, and embedding-worker Docker paths still
clone framework source and run Maven installation during product assembly.
Several use `main`; embedding-worker paths remain pinned to
`ai-fabric-framework-v0.3.1`.

Packaging commands that use `-DskipTests` may remain packaging commands, but
they cannot be cited as test evidence.

## 5. Gate Status

| Gate | Status | Current evidence | Required transition |
| --- | --- | --- | --- |
| A - `0.4.0` lifecycle cutover | **BLOCKED** | Platform still resolves `0.3.1`; stale entity contracts and mutable framework Docker builds remain | Complete Phases A through F and establish a stable `0.4.0` deployment baseline |
| B - `0.5.0` publication | **PASSED** | Immutable tag/release, Central BOM and execution JAR, and out-of-tree consumer proof all passed | Revalidate from a fresh repository when adoption begins |
| C - Platform `0.5.0` compile/adoption | **NOT STARTED** | Gate A must pass first; Gate B is open | One-version dependency tree, specialist compile, focused tests |
| D - Packaged runtime | **NOT STARTED** | Prohibited until C passes | Docker image built without framework clone; startup and auth proof |
| E - Isolated deployment/canary | **NOT STARTED** | Prohibited until D passes | Real provider, Data Sync, retrieval, tenant, failure, and rollback evidence |

### 5.1 Maven Central evidence

The initial 2026-07-30 11:56 BST probe failed before publication. After the
release workflow completed, a second new Maven repository resolved:

```bash
io.github.loom-ai-labs:ai-fabric-bom:0.5.0:pom
io.github.loom-ai-labs:ai-fabric-execution:0.5.0:jar
```

Maven Resolver recorded `central` as the origin for both artifacts. An
out-of-tree standalone consumer then passed:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Completed Gate B evidence:

```text
ai-fabric-framework-v0.5.0 tag
GitHub release and release notes identifying the commit
ai-fabric-bom:0.5.0 from Maven Central
ai-fabric-execution:0.5.0 from Maven Central
standalone agentic-execution-consumer tests using the same empty repository
no framework reactor install or source checkout in the consumer path
```

See
[`02_GATE_B_0_5_PUBLICATION_EVIDENCE.md`](02_GATE_B_0_5_PUBLICATION_EVIDENCE.md)
for the release URL, workflow, dependency tree, and artifact checksums.

## 6. Migration Phases

Only one phase may be in progress at a time.

### Phase A - Make the `0.4` source contract valid

Status: **PENDING**

1. Add behavior-preserving tests around current entity validation, compilation,
   hashing, Data Sync failure handling, and packaging assumptions.
2. Align `ai-infrastructure-module` and `ai-fabric-product` to the released
   `0.4.0` BOM.
3. Update the runtime dependency comment.
4. Convert all four source-controlled entity YAML files to the explicit `0.4`
   indexing, analysis, searchable-field, and metadata-field shape.
5. Reject removed lifecycle properties rather than preserving them in an
   extension map.
6. Verify runtime vector-space diagnostics from the migrated test entity
   projection.
7. Prove no mixed `0.3.x` artifact exists in either Maven reactor.

Exit evidence:

```text
stale-contract scan with classified non-entity matches
effective POM and dependency-tree evidence
runtime entity-config tests
vector-space endpoint tests
both Maven reactors compile against 0.4.0
```

### Phase B - Make Platform the typed `0.4` contract owner

Status: **PENDING**

1. Create one Platform-owned typed entity projection model shared by request
   handling, validation, compilation, hashing, and migration preview.
2. Represent indexing, analysis, searchable destinations, preprocessing,
   limits, priority, required state, metadata type/destinations, and PII policy.
3. Upgrade `DeploymentDraftValidationService` with stable issue codes and JSON
   paths for every `0.4` rule and every removed property.
4. Update `DeploymentConfigCompiler` to validate, serialize, parse back, validate
   the round trip, and hash the validated representation.
5. Record AI Fabric version and entity-contract version in the immutable
   manifest.
6. Update the Knowledge editor to typed repeated controls.
7. Ensure raw/advanced editing uses the same backend validator.

Exit evidence:

```text
backend validation matrix
compiler round-trip tests
legacy-key rejection tests
UI build and focused browser/editor tests
generated artifact contains no 0.3 lifecycle key
```

### Phase C - Migrate persisted drafts without rewriting releases

Status: **PENDING**

1. Add application-owned schema fields for draft/version entity-contract
   version, published framework version, and migration audit evidence.
2. Backfill existing rows as `AI_ENTITY_CONFIG_V0_3`.
3. Implement deterministic preview and migration services.
4. Preserve removed weight and embeddable-field information as warnings, not
   fabricated `0.4` semantics.
5. Block unknown metadata types and any enabled entity left without semantic
   search.
6. Prove idempotency: a second migration makes no semantic change.
7. Convert only mutable drafts after backup and accepted preview.
8. Keep published `V0_3` versions and artifacts immutable.
9. Publish a new `V0_4` version from an approved migrated draft.
10. Add runtime/artifact compatibility checks before provisioning.

Exit evidence:

```text
database migration version
before/after hashes and migration report
published-version immutability tests
runtime/artifact compatibility tests
new immutable V0_4 artifact and manifest
```

### Phase D - Make vector drift and Data Sync failure operational

Status: **PENDING**

1. Replace the old indexed-output hash projection with the complete canonical
   `0.4` behavior-affecting model.
2. Prove ordering normalization and behavior/cosmetic change boundaries.
3. Parse Data Sync response bodies before classifying HTTP status.
4. Preserve error code, work ID, indexing status, retry disposition,
   correlation ID, and durable-handoff evidence.
5. Reconcile accepted durable work instead of blindly resubmitting it.
6. Add private diagnostics for framework version, artifact, entity types,
   projection hashes, queue state, vector counts, provider, and dimensions.
7. Keep diagnostics sanitized and private.

Exit evidence:

```text
hash drift test matrix
typed Data Sync result tests
retry/work reconciliation tests
private diagnostics and release-verification tests
```

### Phase E - Make product images reproducible

Status: **PENDING**

1. Remove framework `main` cloning and local installation from normal production
   images for runtime, generic connector, relay, and embedding worker.
2. Consume immutable Maven Central artifacts through one BOM version.
3. Keep a source-candidate image only as an explicitly named non-production lab.
4. Report LoomAI commit, AI Fabric version, entity artifact version/hash, image
   build time, and source commit accurately.
5. Update Docker packaging assertions.

Exit evidence:

```text
Dockerfiles contain no mutable framework production clone
image dependency tree resolves one version
image health/build metadata matches source and Maven artifacts
source-candidate path is isolated and clearly named
```

### Phase F - Establish the stable `0.4.0` baseline

Status: **PENDING**

Run:

```bash
mvn -B -V --no-transfer-progress -f ai-fabric-product/pom.xml clean verify
mvn -B -V --no-transfer-progress -f ai-infrastructure-module/pom.xml clean verify
mvn -B -V --no-transfer-progress -f Platfrom/backend/pom.xml clean verify
npm ci --prefix Platfrom/ui
npm run build --prefix Platfrom/ui
```

Then prove:

1. Packaged runtime startup from immutable `0.4.0` artifacts.
2. Exact expected vector spaces.
3. Data Sync create, update, delete, and durable retry behavior.
4. RAG from updated evidence and absence of deleted evidence.
5. Tenant filtering and PII policy.
6. Queue/source/vector count reconciliation.
7. Runtime restart preservation.
8. Platform UI draft, validation, preview, publish, deploy, vectorization, query,
   and delete flow.
9. Real-provider canary with visible failures.
10. Rehearsed rollback.

Gate A passes only after a stable `0.4.0` deployment baseline is recorded.

### Phase G - Prove the published `0.5.0` release

Status: **BLOCKED ON FRAMEWORK PUBLICATION**

1. Verify release tag and GitHub release.
2. Create a new empty Maven repository.
3. Resolve `ai-fabric-bom:0.5.0`.
4. Resolve `ai-fabric-execution:0.5.0`.
5. Run `examples/agentic-execution-consumer` against that same repository.
6. Inspect the published JAR schema and public APIs.
7. Record published commit, checksums, dependency tree, and test results.

Do not substitute a locally installed candidate.

### Phase H - Align Platform runtime dependencies to `0.5.0`

Status: **BLOCKED ON PHASES F AND G**

1. Move every AI Fabric dependency in the runtime process to `0.5.0`.
2. Add `ai-fabric-execution` only to `ai-fabric-runtime`.
3. Keep `Platfrom/backend` as control plane, not the first execution consumer.
4. Re-run effective POM and dependency trees.
5. Run existing chat, conversation, Data Sync, auth, and admin regression tests
   before adding specialist behavior.

Exit evidence:

```text
one-version dependency tree
existing runtime behavior unchanged
no execution module in Platform backend
```

### Phase I - Add `deployment-knowledge-specialist@1`

Status: **BLOCKED ON PHASE H**

1. Package an exact-version manifest in `ai-fabric-runtime`.
2. Validate its syntax against the schema in the published `0.5.0` JAR.
3. Add a strict retrieval-only `deployment_knowledge` mode.
4. Allow only the `document` vector space.
5. Disable actions, writes, conversation binding, receipts, review, waits,
   plans, managers, parallel execution, and JDBC execution state.
6. Use synchronous execution for the first proof.
7. Require approved evidence and citations.
8. Return schema-bound `ANSWERED` or `INSUFFICIENT_EVIDENCE`; expose no-evidence
   and provider failures explicitly.

Exit evidence:

```text
startup manifest compilation
exact specialist and vector scopes
zero visible/requestable/proposable actions
bounded input/output/evidence/timing
no hidden fallback
```

### Phase J - Integrate trusted context and typed endpoint

Status: **BLOCKED ON PHASE I**

1. Resolve request identity through existing runtime auth before constructing
   execution context.
2. Map verified actor, tenant, deployment, subject, correlation ID,
   authentication time, and exact scopes into the published execution types.
3. Never bind trusted execution context from public JSON.
4. Add a separate typed endpoint:

```text
POST /api/specialists/deployment-knowledge/query
```

5. Accept only the untrusted `question`.
6. Select the specialist server-side.
7. Return only typed status, answer, exact specialist version, correlation ID,
   safe evidence references, and bounded failure code.
8. Preserve existing chat routes and behavior.

Exit evidence:

```text
request identity-field rejection
exact authority intersection
tenant/deployment mismatch denial
cross-tenant evidence denial
existing chat regression suite
```

### Phase K - Package and test the `0.5.0` runtime

Status: **BLOCKED ON PHASE J**

Run the complete build matrix without skipped tests, then build the actual
runtime Docker image.

Required proofs:

1. Manifest compilation and unknown-field failure.
2. Schema-bound input/output.
3. Exact specialist/version and vector authority.
4. No action inventory.
5. Safe evidence projection.
6. No-grounding and insufficient-evidence behavior.
7. Provider, retrieval, malformed output, schema, timeout, and persistence
   failure visibility.
8. Two-tenant retrieval, update, and delete lifecycle.
9. Framework version and Platform commit in health/build metadata.
10. Docker image resolves published `0.5.0`, not local source.
11. Existing chat, conversation, Data Sync, and admin surfaces start.

### Phase L - Isolated deployment and canary

Status: **BLOCKED ON PHASE K**

Rollout order:

```text
local deterministic tests
-> packaged runtime
-> isolated development deployment
-> internal tenant
-> small canary
-> measured expansion
```

Canary probes:

- grounded deployment answer;
- insufficient evidence;
- no hidden v1 conversation memory;
- hostile evidence/prompt injection;
- cross-tenant request;
- provider disabled/failure;
- Data Sync update/delete;
- existing chat path;
- health/build identity.

No secret or raw provider payload may enter logs or evidence.

## 7. Live `0.4` State Cutover Procedure

Execute per deployment, never as an unreviewed bulk operation:

1. Inventory deployment/version/artifact/runtime/database/vector/source state.
2. Confirm no release or vectorization run is active.
3. Record the rollback image, artifact, vector state, and restoration commands.
4. Pause vectorization and Data Sync ingress.
5. Stop old runtime/indexing workers.
6. Back up the Platform database, selected runtime database, vector
   namespace/index, immutable artifact, and count report.
7. Rehearse restore for the first canary.
8. Replace incompatible runtime queue/ordering state only in the selected
   runtime database.
9. Clear or replace generated vectors only after proving dedicated ownership or
   applying a tenant-safe filtered strategy.
10. Deploy the new immutable `V0_4` artifact and `0.4.0` runtime.
11. Verify private diagnostics and exact vector spaces.
12. Backfill from authoritative source records.
13. Wait for durable queue completion and reconcile source/queue/vector counts.
14. Prove update, delete, retrieval, RAG, retry, and tenant behavior.
15. Resume writers only after reconciliation.
16. Record the observation window before promotion.

## 8. Rollback

### 8.1 Before specialist activation

- Keep the previous immutable runtime image.
- Keep the unchanged published `V0_3` deployment version and entity artifact.
- Keep database and vector backups with tested restoration instructions.
- Do not run a `0.3` worker on a `0.4` queue or vector projection.

### 8.2 `0.4` cutover rollback

1. Stop the `0.4` runtime, vectorization, and Data Sync ingress.
2. Restore the pre-cutover runtime database.
3. Restore the prior vector snapshot or rebuild using the exact `0.3` source
   contract.
4. Reactivate the unchanged `V0_3` version and artifact.
5. Deploy the exact previous LoomAI image and AI Fabric `0.3.1`.
6. Verify versions, vectors, counts, update/delete, tenant behavior, and RAG.
7. Resume writers only after verification.

### 8.3 `0.5.0` specialist rollback

1. Disable the specialist endpoint or feature flag.
2. Disable `ai.execution.enabled` if necessary.
3. Preserve the existing chat endpoint.
4. Roll back to the previous immutable runtime image.
5. Do not mutate or delete system-of-record data.

The first specialist creates no new durable execution, receipt, or review state.

## 9. Configuration And Secret Inventory Deliverable

Before deployment, add a redacted environment matrix under this folder. Record:

```text
property/environment name
owning service
required or optional
safe default
environment-specific source
secret or non-secret
restart required
data migration or reindex impact
validation/health proof
```

Cover local, test, staging, canary, and production for:

- entity artifact and contract version;
- runtime ingress and identity conflict policy;
- assertion/public/private auth;
- authorization;
- LLM and embedding provider/model/dimensions;
- vector provider and index path/namespace;
- Data Sync;
- retrieval/action connectors;
- chat/session behavior;
- prompt bundles;
- PII;
- datasource and migrations;
- CORS;
- health/build metadata;
- execution manifests and exact scopes.

Do not store secret values in this plan.

## 10. Required Evidence Files

Create and update these as phases execute:

```text
00_CURRENT_STATE_EVIDENCE.md
01_GATE_A_0_4_BASELINE_EVIDENCE.md
02_GATE_B_0_5_PUBLICATION_EVIDENCE.md
03_CONFIGURATION_MATRIX_REDACTED.md
04_BUILD_AND_TEST_EVIDENCE.md
05_PACKAGED_RUNTIME_EVIDENCE.md
06_CANARY_AND_TENANT_EVIDENCE.md
07_DEPLOYMENT_AND_ROLLBACK_EVIDENCE.md
```

Each report records exact commands, timestamps, exit status, test counts,
artifact versions, safe identifiers, limitations, rollback point, and next
gate. Never mark a skipped or blocked check as passed.

## 11. Status Log

### 2026-07-30 - Initial analysis

- Confirmed the guide's reviewed Platform and framework commits.
- Confirmed Platform remains on AI Fabric `0.3.1`.
- Confirmed no active removed Java entity-lifecycle API usage.
- Confirmed stale source-controlled entity contracts.
- Confirmed persisted draft/version/artifact implications.
- Confirmed Platform validation, compiler, editor, hash, and Data Sync gaps.
- Confirmed mutable framework builds remain in product Dockerfiles.
- Confirmed framework release POM preparation is external work and left it
  untouched.
- Confirmed no `0.5.0` tag or GitHub release exists yet.
- Confirmed fresh Maven Central BOM resolution currently fails.
- Set Gate A and Gate B to **BLOCKED**.

### 2026-07-30 - Framework release observation

- Observed the external framework release process run its release-profile
  reactor and integration-suite test compilation.
- Confirmed local `0.5.0` artifacts were produced.
- Confirmed minimal and standalone agentic consumers passed against local
  candidate artifacts.
- Confirmed the real-app reactor passed and 11 packaged applications started.
- Found the subsequent P0 ecommerce-to-chat Data Sync smoke failed with
  `PROJECTION_REJECTED / SEARCHABLE_FIELDS_REQUIRED`.
- Traced the failure to `chat-capabilities-demo` enabling YAML-only entity
  indexing without explicit searchable field projections.
- Did not treat local Maven installation as public release proof.
- Confirmed the framework release preparation remained uncommitted and
  untagged after those commands finished.
- Left the externally modified framework worktree untouched.
- Kept Gate B **BLOCKED** pending release commit, remote tag, Maven Central
  artifacts, and standalone clean-repository consumer proof.

### 2026-07-30 - Data Sync smoke diagnosis

- Confirmed the release session added explicit searchable and metadata
  projections for `product`, `policy`, and `review`.
- Confirmed the rebuilt `chat-capabilities-demo` JAR contains those projection
  rules, so the clean rerun did not use stale packaging.
- Reproduced a Data Sync upsert reporting `indexingStatus=COMPLETED`.
- Confirmed readiness reports one `product` vector and an exact scan finds the
  submitted entity.
- Confirmed vector search returns the entity with similarity `1.0` when the
  query uses the canonical projected text.
- Identified the remaining failed smoke as an assertion defect: the offline
  hash-based embedding cannot semantically match a JSON source serialization
  to a differently formatted canonical projection.
- Confirmed the release session corrected the smoke to use canonical
  projection text.
- Re-ran the corrected packaged P0 flow and confirmed create, Data Sync,
  retrieval, delete, and absence-after-delete all passed.
- Confirmed 18 portable vector contract tests and 8 real-container vector
  contract tests passed with zero failures.
- Made no changes to the concurrent framework release worktree.

### 2026-07-30 - AI Fabric 0.5.0 publication

- Published GitHub Release `ai-fabric-framework-v0.5.0`.
- Confirmed the release points to
  `a49138c6bff39c66bf48c3885cb911e8d7b78d84`.
- Confirmed the Maven Central release workflow completed successfully.
- Resolved the `0.5.0` BOM and execution JAR from Central into an empty Maven
  repository.
- Ran an out-of-tree standalone consumer against those public artifacts:
  2 tests, zero failures, errors, or skips.
- Confirmed its AI Fabric dependency tree contains only `0.5.0`.
- Marked Gate B **PASSED**. Gate A remains the active platform blocker.

### 2026-07-30 - Gate A implementation and packaged canary

- Migrated the Platform-owned entity contract, persisted draft/version model,
  compiler, validator, editor, manifests, and indexed-output hash to the
  explicit `0.4` baseline.
- Added deterministic and auditable draft migration while preserving immutable
  historical release bytes.
- Added bounded Data Sync failure classification and durable-work
  reconciliation.
- Removed framework source cloning from product Docker builds and resolved the
  `0.4.0` framework from Maven artifacts.
- Passed the product, infrastructure, backend, frontend, focused runtime, and
  focused durable-retry checks recorded in `04_BUILD_AND_TEST_EVIDENCE.md`.
- Built packaged image digest
  `sha256:aec3c8daeb50312299283ce8e38b4612d0224cfac434997938255123029b4391`.
- Fixed two Platform runtime integration defects found by the package canary:
  the Boot 4 fallback mapper did not register Java time modules, and the
  search-source adapter did not normalize Lucene's serialized JSON metadata
  before its fail-closed source filter.
- Proved create, update, persistence across restart, retrieval, delete,
  projection rejection, auth failures, retryable provider failure, dead-letter
  termination, tenant isolation, and snapshot restoration.
- Kept Gate A **BLOCKED** on the isolated external staging deployment and
  deployment-level rollback proof. Local packaged-runtime evidence is not
  represented as a Coolify deployment.

### 2026-07-30 - Gate A Platform staging and canonical migration finding

- Deployed Platform backend and UI successfully on staging from immutable
  commit `196aaf921c0dfbe7d7f0468b53fae1c2abacacf0`.
- Proved dynamic customer Marketplace entity manifests migrated to V04 without
  removing the ProdUS safe-knowledge plugin or its ten entity types.
- Captured config-only, zero-secret exports and private draft/version snapshots
  for the canonical Marketplace and Ecommerce deployments before mutation.
- Ran a fresh full release gate with repair disabled. Run `vsr-807a8010`
  stopped before its first live script because preserved canonical active
  versions still declared V03 and the strict V04 indexed-output hash rejected
  them.
- Kept historical V03 releases immutable and added migration-aware read state:
  legacy active versions now report `MIGRATION_REQUIRED`, never a false
  `IN_SYNC` result.
- Added explicit required `tenantId` vector metadata to the canonical
  shared-vector entity config.
- Connected canonical rollout repair to the existing audited draft migration
  service before validation/publication/apply. This internal repair applies
  only to Platform-owned canonical verification rollouts; customer draft
  migration remains explicit and admin-controlled.
- Passed the final clean Platform backend verification with 721 tests and zero
  failures, errors, or skips.
- No missing AI Fabric endpoint or framework defect was found. Gate A remains
  **BLOCKED** until the correction is deployed, new canonical V04 versions are
  applied, a fresh full release gate passes, and external canary/rollback
  evidence is complete.

## 12. Immediate Next Actions

1. Deploy the migration-aware canonical repair correction.
2. Publish/apply backed-up Marketplace and Ecommerce canonical drafts as new
   V04 versions through the audited repair path.
3. Run the fresh full release gate to green, then complete external runtime
   canary and rollback proof.
4. Revalidate Gate B from a new Maven repository immediately before dependency
   edits.
5. Move the complete runtime process to one AI Fabric version, `0.5.0`.
6. Add `ai-fabric-execution` only to `ai-fabric-runtime`.
7. Implement and prove `deployment-knowledge-specialist@1` through the gated
   phases in this plan.
