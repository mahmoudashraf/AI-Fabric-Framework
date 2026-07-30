# Gate B Evidence - AI Fabric 0.5.0 Publication

Status: **PASSED**

Last checked: 2026-07-30 14:36 BST
Published version: `0.5.0`
Published tag: `ai-fabric-framework-v0.5.0`
Release commit: `a49138c6bff39c66bf48c3885cb911e8d7b78d84`

This gate proves that LoomAI Platform consumes a real public framework release.
A local framework checkout or `mvn install` is not acceptable evidence.

## 1. Required Evidence

| Check | Status | Pass condition |
| --- | --- | --- |
| Local release-profile reactor | Passed | Clean release-profile build completes successfully |
| Integration-suite test compilation | Passed | Integration test reactors compile against the release source |
| Minimal external consumer | Passed | Minimal consumer compiles against packaged artifacts |
| Standalone agentic consumer | Passed | Standalone consumer tests packaged artifacts |
| Real-app reactor | Passed | All real apps build and test |
| Packaged real-app boot smoke | Passed | All selected packaged apps start |
| Vector contracts | Passed | Portable and real-container lifecycle contracts pass |
| Ecommerce-to-chat Data Sync smoke | Passed | Created product is projected, indexed, retrieved, deleted, and absent afterward |
| Release commit | Passed | Release commit is committed and pushed |
| Git tag | Passed | Remote tag `ai-fabric-framework-v0.5.0` resolves |
| GitHub release | Passed | Published release identifies the exact tag/commit and release notes |
| Maven Central BOM | Passed | `io.github.loom-ai-labs:ai-fabric-bom:0.5.0:pom` resolves from a fresh repository |
| Maven Central execution JAR | Passed | `io.github.loom-ai-labs:ai-fabric-execution:0.5.0:jar` resolves from the same fresh repository |
| Standalone consumer | Passed | `agentic-execution-consumer` compiles and passes using only those public artifacts |
| Dependency convergence | Passed | Consumer resolves one AI Fabric version, `0.5.0` |
| Source independence | Passed | No framework reactor, checkout, or local install participates |

## 2. Final Publication Evidence

GitHub publication:

```text
release:
  https://github.com/Loom-AI-Labs/ai-fabric-framework/releases/tag/ai-fabric-framework-v0.5.0
workflow:
  https://github.com/Loom-AI-Labs/ai-fabric-framework/actions/runs/30545989017
workflow conclusion: success
tag commit: a49138c6bff39c66bf48c3885cb911e8d7b78d84
```

Both public coordinates returned HTTP 200 and resolved into a new empty Maven
repository:

```text
io.github.loom-ai-labs:ai-fabric-bom:0.5.0:pom
io.github.loom-ai-labs:ai-fabric-execution:0.5.0:jar
```

The Maven Resolver `_remote.repositories` files recorded `central` as the
origin for both artifacts. A copy of `agentic-execution-consumer` was placed
outside the framework checkout and run with that same fresh repository:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Its AI Fabric dependency tree was:

```text
io.github.loom-ai-labs:ai-fabric-execution:jar:0.5.0
io.github.loom-ai-labs:ai-fabric-core:jar:0.5.0
io.github.loom-ai-labs:ai-fabric-curated-default:jar:0.5.0
```

Verified SHA-256 values:

```text
638bf49e9dca816c74cc8de080ef1a851f045e6dc890777d64f583a352101a82  ai-fabric-bom-0.5.0.pom
abdef28299a4da658b35abfda7f702e55ee50f5ed4086cb2bb921a7380b5f7cf  ai-fabric-execution-0.5.0.jar
```

## 3. Initial Prepublication Framework Observation

Observed framework identity:

```text
branch: main
commit: 13a37860bc9a0e4e11928be89f356d7ee185bc56
remote state: main matched origin/main at observation time
```

The worktree had 87 modified or untracked release-preparation paths, including
POM version changes, documentation updates, example version updates, and a new
`docs/release-notes/0.5.0.md`. These changes belong to the framework release
process and were left untouched.

During observation, the release process ran:

```bash
mvn -B -V --no-transfer-progress \
  -f ai-infrastructure-module/pom.xml \
  -Dai.vector-db.lucene.cleanup-on-close=true \
  -Prelease \
  -pl '<reactor excluding integration test applications>' \
  clean install
```

It then ran integration-suite test compilation:

```bash
mvn -B -V --no-transfer-progress \
  -f ai-infrastructure-module/pom.xml \
  -pl '<integration test applications>' \
  -am test-compile
```

Both processes finished and local `0.5.0` artifacts were produced. Their
terminal output and exit status belong to the release session and must be
included in that session's release evidence. This migration does not infer a
pass merely from local artifact presence.

Additional release-session logs showed:

```text
minimal consumer: BUILD SUCCESS
standalone agentic consumer: 2 tests, 0 failures, BUILD SUCCESS
real-app reactor: 23/23 modules SUCCESS, BUILD SUCCESS
packaged boot smoke: 11/11 selected apps started
```

The first ecommerce-to-chat Data Sync smoke failed:

```text
PROJECTION_REJECTED
SEARCHABLE_FIELDS_REQUIRED
runtime vector search did not return the created product within 45 seconds
```

The source request successfully created the ecommerce product, but the chat
runtime rejected its Data Sync upsert before vector indexing.

Root cause:

```text
examples/real-apps/chat-capabilities-demo/src/main/resources/
  ai-entity-config.yml
```

The file explicitly enabled indexing for `product`, `policy`, and `review`,
but did not declare `searchable-fields`. Data Sync intentionally uses
`AIConfiguredEntityProjectionService`, the fail-closed YAML projection path,
and requires at least one field with `SEMANTIC_SEARCH`.

The concurrent framework release session then added explicit searchable and
metadata projections for those three entity types and rebuilt the packaged
application. Inspection of the rebuilt JAR proved that the packaged
`ai-entity-config.yml` contained the new projection. A clean rerun accepted the
Data Sync request with HTTP 200 but the release script still reported zero
search results.

Focused manual reproduction isolated that second failure:

```text
Data Sync response:
  success=true
  indexingStatus=COMPLETED
  indexingStrategy=SYNC

Readiness after upsert:
  provider=memory
  product.vectorCount=1
  retrievalProof.found=true
  retrievalProof.sampleEntityId=P0-MANUAL-002

Search using the canonical projected text:
  returnedResults=1
  entityId=P0-MANUAL-002
  similarity=1.0000000000000002
```

This proves that projection, synchronous indexing, vector storage, scan, and
search are wired to the same in-memory vector service and work after the
configuration change.

The remaining failure is in the release smoke assertion. The smoke profile
uses an offline deterministic embedding provider that hashes the entire input
string. The script searched with a JSON serialization of the source entity,
while indexing embeds the canonical projection:

```text
name: ...
description: ...
sku: ...
category: ...
tags: ...
price: ...
currency: ...
inStockQty: ...
```

Those differently formatted strings do not have semantic similarity under the
hash-based smoke provider. Their cosine score can be negative and is then
removed by the endpoint's minimum threshold of `0.0`. Waiting longer cannot
make that query succeed.

The release session corrected the P0 script to query the receiver-owned
canonical projection. The corrected packaged-application smoke then passed:

```text
chat-capabilities-demo healthy
ecommerce-store healthy
ecommerce product created
runtime vector search found the product
ecommerce product deleted
runtime vector search no longer returned the product
P0 ecommerce -> chat data-sync smoke passed
```

The published release retained:

1. The explicit `product`, `policy`, and `review` projections.
2. The focused configuration/projection regression test.
3. The corrected canonical-projection P0 smoke assertion.
4. The rule that hash-based smoke embeddings are not used to claim free-text
   semantic relevance.

The container-backed vector contract gate also passed:

```text
VectorDatabaseServiceContractTest:
  tests=18, failures=0, errors=0, skipped=0

VectorDatabaseServiceContainerIT:
  tests=8, failures=0, errors=0, skipped=0
```

After the smoke investigation, the framework repository still had no new
commit or tag. The release-preparation worktree had modified/untracked paths,
including the projection fix and a focused projection configuration test. No
Platform migration code was changed while this worktree was active.

No local release tag was present:

```bash
git tag --list 'ai-fabric-framework-v0.5.0'
```

Observed output: empty.

No remote release tag was present:

```bash
git ls-remote --tags origin \
  'refs/tags/ai-fabric-framework-v0.5.0' \
  'refs/tags/ai-fabric-framework-v0.5.0^{}'
```

Observed output: empty.

The 12:57 BST continuation recheck was unchanged:

```text
origin/main:
  13a37860bc9a0e4e11928be89f356d7ee185bc56

remote ai-fabric-framework-v0.5.0 tag:
  absent

Maven Central ai-fabric-bom/0.5.0:
  HTTP 404
```

## 4. Initial Fresh Maven Central Probe

The BOM was requested with a newly created Maven repository:

```bash
mvn -B -V --no-transfer-progress \
  -Dmaven.repo.local="<fresh-temporary-repository>" \
  dependency:get \
  -Dartifact=io.github.loom-ai-labs:ai-fabric-bom:0.5.0:pom \
  -U
```

Observed result:

```text
BUILD FAILURE
```

The BOM was not available from Maven Central. Because this first artifact gate
failed, the execution JAR and standalone consumer checks were intentionally not
reported as passed.

## 5. Repeatable Gate Recheck Procedure

To revalidate the immutable release:

1. Resolve the remote tag and record its commit.
2. Confirm the GitHub release points to that tag and includes `0.5.0` notes.
3. Create a new empty Maven repository; do not reuse this failed probe cache.
4. Resolve the BOM from Maven Central.
5. Resolve `ai-fabric-execution` from Maven Central.
6. Copy the standalone consumer outside the framework reactor.
7. Run its clean tests with the same fresh repository.
8. Record the dependency tree and prove all AI Fabric artifacts are `0.5.0`.
9. Scan the consumer/build logs for local framework reactor or source-checkout
   participation.
10. Change this gate to **PASSED** only if every required check succeeds.

Suggested commands:

```bash
FRESH_REPO="$(mktemp -d)"

mvn -B -V --no-transfer-progress \
  -Dmaven.repo.local="$FRESH_REPO" \
  dependency:get \
  -Dartifact=io.github.loom-ai-labs:ai-fabric-bom:0.5.0:pom \
  -U

mvn -B -V --no-transfer-progress \
  -Dmaven.repo.local="$FRESH_REPO" \
  dependency:get \
  -Dartifact=io.github.loom-ai-labs:ai-fabric-execution:0.5.0:jar \
  -U
```

The consumer command must use a copied standalone project and the same
`FRESH_REPO`.

## 6. Current Decision

Gate B is open. LoomAI may begin the `0.5.0` dependency and specialist phase
only after Gate A establishes the required `0.4.0` lifecycle baseline. Use the
published artifacts above, keep one framework version per runtime process, and
do not substitute a locally installed framework build for the public release.
