# Gate A Build And Test Evidence

Status: **PASSED FOR SOURCE AND PACKAGED BUILD**

Evidence date: 2026-07-30
Timezone: Europe/London
Branch: `Platform-V11`

No secret values are recorded in this report.

## 1. Implemented Baseline

- Both product reactors consume AI Fabric `0.4.0`.
- All source-controlled entity YAML uses the explicit `0.4` projection and
  lifecycle contract.
- Platform owns a typed, versioned entity-contract model shared by persistence,
  validation, compilation, API responses, and the UI editor.
- Flyway `V127` adds contract/version and migration-audit ownership.
- Flyway `V128` converts the five production marketplace DATA manifests to the
  explicit `0.4` contract.
- Flyway `V129` dynamically converts legacy entity entries for every persisted
  marketplace plugin version, including customer-installed plugin IDs.
- Draft migration is deterministic, idempotent, dry-runnable, and auditable.
- Immutable historical version payloads remain unchanged.
- Export/import preserves entity contract, framework version, artifact bytes,
  and hashes.
- Indexed-output hashing uses normalized output-affecting `0.4` semantics.
- Data Sync errors retain bounded safe codes, request IDs, and retry
  disposition.
- Durable Data Sync work is reconciled before source replay.
- Production Dockerfiles no longer clone or install framework source.

## 2. Framework Contract Check

The immutable public tags were inspected from:

```text
/Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework
```

Both `ai-fabric-framework-v0.4.0` and `ai-fabric-framework-v0.5.0` expose the
expected Data Sync and search contracts. The Lucene provider returns stored
metadata as serialized JSON in `AISearchResponse` result maps. Platform's
source adapter now normalizes that supported provider shape before applying its
second fail-closed tenant/source filter.

No missing framework endpoint or framework publication blocker was found
during Gate A. The two package-canary defects were in Platform integration
code, not the immutable framework release.

## 3. Verification Results

| Verification | Result |
| --- | --- |
| AI Fabric product reactor | 31 tests, 0 failures/errors |
| AI Fabric infrastructure reactor | 187 tests, 0 failures/errors |
| Platform backend clean verify | 722 tests, 0 failures/errors/skips |
| Platform UI production build | Passed |
| Runtime search-source regression | 10 tests, 0 failures/errors |
| Vectorization durable-work/failure tests | 18 tests, 0 failures/errors |
| Platform retry/checkpoint tests | 3 tests, 0 failures/errors |
| Both dependency trees | AI Fabric `0.4.0` only |
| PostgreSQL 16 Flyway `V128` validation | Passed |
| PostgreSQL 16 dynamic marketplace `V129` migration | 1 test, 0 failures/errors; included in the 722-test backend total |
| Legacy active-version and canonical audited-repair regressions | 4 focused tests added; complete focused service set 26 tests, 0 failures/errors |

Focused commands:

```bash
mvn -B --no-transfer-progress \
  -f ai-infrastructure-module/pom.xml \
  -pl ai-fabric-runtime -am \
  -Dtest=RuntimeDeploymentSearchSourceRegistryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -B --no-transfer-progress \
  -f ai-fabric-product/pom.xml \
  -pl ai-fabric-vectorization-runner -am \
  -Dtest=ConnectorDataSyncTargetWriterTest,VectorizationRunExecutorTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -B --no-transfer-progress \
  -f Platfrom/backend/pom.xml \
  -Dtest=VectorizationServiceTest test

mvn -B --no-transfer-progress \
  -f Platfrom/backend/pom.xml \
  -Dtest=MarketplaceLegacyEntityContractMigrationPostgresTest test

mvn -B --no-transfer-progress \
  -f Platfrom/backend/pom.xml \
  clean verify
```

## 4. Staging Startup Defect Closed In Source

The first external backend deployment reached Flyway but failed strict V04
startup validation for customer-installed plugin
`mkp-data-produs-safe-knowledge@0.1.0`. Its dynamic entity types were not among
the five first-party IDs hardcoded in `V128`.

`V129` removes that closed-world assumption. It discovers the
`contributions.entityConfig.ai-entities` object in every persisted marketplace
version, rewrites only entries containing legacy keys, preserves each dynamic
entity-type key, leaves already-valid V04 entries byte-equivalent at the JSON
tree level, and produces the same result when run again.

## 5. Release-Gate Migration-State Correction

Fresh run `vsr-807a8010` proved that the canonical active versions were still
V03. The release gate attempted to compute a strict V04 indexed-output hash
and stopped before its first live script.

The corrected source:

- keeps the V04 hasher strict;
- reports a legacy active version as `MIGRATION_REQUIRED`;
- marks canonical rollout readiness as repairable instead of throwing;
- writes required tenant vector metadata into the authoritative canonical
  entity config; and
- invokes the existing audited migration service to validate and atomically
  adopt the authoritative canonical config before the generic draft update,
  validation, publication, and apply.

The first deployed repair sequence tried the generic draft update before
migration. The API correctly rejected that edit with HTTP 400, and no new
version was published. The corrected operation supplies the source-controlled
entity and target provider configs directly to the migration service. It:

- validates V04 under the target shared/dedicated vector posture;
- records the preserved V03 config and semantic hash as `before` evidence;
- records the normalized canonical V04 config and hash as `after` evidence;
- converts resolved source blockers into audit warnings rather than hiding
  them;
- writes the mutable draft and contract label atomically; and
- still rejects a non-V04 or tenant-unsafe canonical config without mutation.

The internal canonical repair does not require a request-scoped user because
only the Platform-owned rollout service can call its package-private method.
Public customer migration remains access-controlled.

## 6. Residual Build Notes

- The UI build reports six npm audit findings and a roughly 1.668 MB main
  chunk. Neither changed the Gate A runtime contract, but both remain release
  quality work.
- Java test output warns that Mockito's dynamic agent attachment will be
  restricted by a future JDK. Current tests pass.
- Remaining product image-family builds are repeated in the external
  deployment gate; the runtime image itself was built from the repository root
  without a sibling framework checkout.
