# V205 Unused External Vector Transfer Changeset

Production source checkpoint: frozen after the claim SQL placeholder, terminal archival-readback, and sanitized provider-error boundary fixes. No framework changes, hosted mutations, commits, or deployments are included.

## Exact Source Manifest

Modified existing files:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/admission/AdmissionUnusedReservationEvidence.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentExistingVectorBindingService.java`

New production files:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/model/UnusedVectorTransferContracts.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/ExternalVectorClusterLocks.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/ExternalVectorClusterReadback.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/UnusedVectorTransferEvidence.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/UnusedVectorTransferIdentity.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/UnusedVectorTransferJson.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/UnusedVectorTransferService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/UnusedVectorTransferStore.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/UnusedVectorTransferController.java`
- `Platfrom/backend/src/main/resources/db/migration/V205__unused_external_vector_transfers.sql`

New tests:

- `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/admission/UnusedVectorTransferHttpTest.java`
- `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/ExternalVectorClusterReadbackTest.java`
- `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/UnusedVectorTransferContractTest.java`
- `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/UnusedVectorTransferPostgresIT.java`

Modified existing test fixture only (no assertion changes):

- `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/deployment/service/DeploymentExistingVectorBindingServiceTest.java`

Documentation, including this manifest:

- `Final_Documentation/Development_Guides/UNUSED_EXTERNAL_VECTOR_CONTROL_PLANE_TRANSFER_GUIDE.md`
- `verification-support/productization-01036/V205_UNUSED_VECTOR_TRANSFER_CHANGESET.md`

Total: 12 production files (2 modified, 10 new), 5 test files (4 new, 1 fixture-only modification), 2 new documentation files.

## Qualification Checkpoint

`/private/tmp/01036-v205-focused-3.log`: **70 passed, 0 failed, 0 errors, 0 skipped**.

- Two actual independent PostgreSQL CP databases, production Flyway through V205: 19 tests.
- Real HTTP/authentication/parser boundary: 17 tests.
- Reviewed full-visibility default-database inventory: 14 tests.
- Strict local contracts and server identity: 20 tests.

The positive case performs actual original bind, actual cancellation service, actual V202 local handoff, genuine no-reservation source release, authenticated-readback enrollment, first destination claim, and exact replay. Only the provider read boundary is mocked; IDs and credentials are synthetic. Both Hume findings are exercised: the first claim commits, and archived terminal sources retain readback/replay while changed owner/version pins reject.

`/private/tmp/01036-v205-focused-4-regression.log`: 323 run, 320 passed, 3 fixture failures, 0 errors/skips. All 32 expanded transfer PG cases, 59 old handoff PG cases, 35 cancellation PG cases, and 49 gate/epoch cancellation PG cases passed. The three old bind-unit failures came from omitted deployment IDs in mock historical handles; the listed three-line fixture correction was applied before the final rerun, without changing assertions.

Final `/private/tmp/01036-v205-focused-5-final.log`: **328 passed, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS, exit 0**. Session 33150 has completed. Breakdown:

- New transfer real PostgreSQL/Flyway: 33.
- Transfer HTTP/authentication/strict parser and sanitized provider failure responses: 19.
- Inventory/readback including sanitized exception text and absent upstream causes: 16.
- Transfer contracts/server identity: 20.
- Unchanged local V202 handoff PostgreSQL regression: 59.
- Existing bind unit suite with corrected mock ownership: 92.
- Existing binding security: 5.
- Cancellation PostgreSQL: 35.
- Cancellation gate/epoch PostgreSQL: 49.

This final run includes actual normal source archival and terminal resource refresh/lifecycle rejection coverage. The parent owns actual-root full-module and fresh archive qualification. No hosted readiness claim is made by this source checkpoint.

## Parent Integration

Configure a stable, unique CP identity and canonical HTTPS origin on each participating CP, plus the exact reviewed peer map. Configuration is fail-closed when absent; no identity or ownership is seeded by V205. Review the coordinator trust contract and full-visibility inventory approval before enabling this operational path. Keep the operator no-writer window in force through the fresh destination claim. See the guide for the routes and evidence requirements.

Existing ordinary-bind local history restrictions and V202 same-customer handoff remain intact. Cluster serialization is shared by ordinary bind, local handoff, resolution, and transfer. Source tombstones and all transfer journals remain immutable. Provider refresh already skips preserved external references; no trigger exception is needed.

## Configuration-Only Consumer Follow-Up, 2026-10-19

Hosted investigation found that a reviewed, never-applied source can have an
initial consumer and DRAFT workspace without any admission reservation. Generic
unbinding correctly rejects that reviewed scope. The correction adds optional,
explicit `consumerDetachments` to the existing source-release request/receipt;
the owning consumer mutation and release commit or roll back together. No
generic assignment guard, no-effects check, provider or schema boundary is
relaxed. Draft/app/version rows remain unchanged. See the updated transfer guide.

Five production files change: the transfer contracts/evidence/service, shared
unused-consumer verifier and owning consumer service. Three existing test files
extend contract, real HTTP and two-control-plane PostgreSQL coverage. Empty
approval is omitted from serialization to preserve stored request/receipt
fingerprints; historical missing fields decode without rewriting old evidence.

Final parent focused qualification: **258 tests across eight classes PASS,
zero failures, errors or skips**. It includes60 real PostgreSQL16/Flyway206
transfer cases,28 existing PostgreSQL detachment cases, strict JSON/HTTP/auth,
rollback/replay/concurrency and historical-fingerprint checks. Earlier196 and118
runs overlap and must not be added to this total. The first sandbox run failed
JVM agent/Docker access and is retained as failed, not qualification.

Actual-module `mvn -B -ntp -o -DskipTests package` succeeds separately; this
packaging command does not claim another test run. All1,894 captured tracked
backend inputs are unchanged before/after. JAR SHA256:
`faf4cd23b43b2b93046251a4849a78234b191c29670650c2df1ba9b029cdf351`.
Proof/log root:`/private/tmp/01044-atomic-release-parent-qualification`.

This checkpoint is source/package qualification only. Hosted source release,
claim, AI-only apply/indexing, production promotion and external readiness still
require their normal live acceptance. No framework/runtime change or new
migration is part of this follow-up.
