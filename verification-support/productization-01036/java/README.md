# Local Budget Store Export/Restore

This opt-in standalone test lives outside runtime image watch paths. It neither
builds an image nor changes the framework, runtime, CI configuration or a live
database. It is not part of the ordinary runtime Maven test discovery.

From the repository root, with Node.js, JDK 21, Maven and local Docker available:

```sh
export JAVA_HOME=/usr/local/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
RUN_01036_LOCAL_BUDGET_RESTORE=true \
  node verification-support/productization-01036/run-local-budget-restore.mjs
```

Both exact images must already be cached. Pulling is disabled:

- Runtime source `d2b1a9792dc10ec801d3ce30c170763f1f2ab301`, image
  `ghcr.io/mahmoudashraf/ai-fabric-runtime@sha256:3809a6585ad19f25c0139d60cf1b9aa742b7831a6213fcec4bdfe6fb52e0b140`,
  inspected local Linux/amd64 image ID
  `sha256:15a68ee33fe2bcc9d9234b76f2fea0ad10e6d3799fcad1e07dfd232eb000f564`.
- PostgreSQL 16 image digest
  `sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea`,
  inspected local Linux/amd64 image ID
  `sha256:81bd698b4594e751a3269e4dcd3e03a4a0ec0daf7b72e7aa1abd43cce9887542`.

The test also checks the previously inspected local image IDs, so another
architecture's variant fails rather than silently substituting an image.
The runtime's `APP_BUILD_COMMIT` and OCI revision must both match the source.
The inspected Docker context is the local Unix socket
`unix:///Users/mahmoudashraf/.docker/run/docker.sock`; remote contexts are rejected.
Maven uses offline `maven-dependency-plugin:3.10.0:build-classpath` only, against a
private, parentless tooling POM for cached AssertJ 3.27.7, JUnit API 6.0.3 and
Testcontainers 2.0.5. Production classes and all production libraries come from
the exact image's `BOOT-INF/classes` and `BOOT-INF/lib`, not local framework
packages. The plugin and test-tooling dependencies must already be cached.
Tooling transitive Byte Buddy and Jackson annotations are excluded from Maven:
the pinned image supplies versions 1.18.10 and 2.21 respectively, checked before
compilation. No alternate dependency versions are downloaded or substituted.
Missing cached dependencies fail the run; no install, package, public framework
release, source compilation or shared `target` modification is performed by
Maven. `javac` compiles only this test into a fresh private temporary directory,
using production classes extracted from the exact cached runtime image.

The repository Java fixture remains unchanged, SHA256
`9e70c06e0ba626852889a78cccde7376ba07e7fe2e5a8ad8864b03bb7c8992f6`.
It still records the historical `600f136bf0becb9a473ff1e085bbc9896b4ef696` /
`d3f744e6c68677b49d128a37a99c31b0717ebf87b1b6d8dd45adbe14030baa28` image.
The runner checks that exact fixture hash and compiles a private copy replacing
only its runtime digest and exact local image-ID literals, once each. No test
assertion, database, migration, API call or isolation rule is changed. The report
records old/new pins, runner hash and original/adapted Java hashes. Any template
drift or test/API incompatibility fails; it does not silently weaken assertions.

## What Runs

An image-reader container is created but never started, then removed. One
disposable PostgreSQL component and a short-lived Java client from the exact
runtime image run on a Docker **internal** network with no published ports or
host bind mounts. This is not `--network none`: JDBC uses the internal alias
`budget-postgres`. The client executes owning runtime/ledger classes directly,
not the full Spring application. No HTTP/model/provider client is constructed.
Only synthetic identities, fake keys and disposable databases are used.

PostgreSQL is limited to one CPU, 768 MiB RAM and equal RAM+swap limit; the
client to one CPU, 512 MiB and equal RAM+swap limit. The host Java orchestrator
uses one processor and a 512 MiB maximum heap. The runner bounds commands and
the Java rehearsal, labels every owned resource, and verifies cleanup on both
success and failure. It never removes unlabelled resources.

The real image migrations run through V19. The owning services initialize and
activate one synthetic approval, then persist ingress, RESERVED, AMBIGUOUS and
SETTLED attempts. `pg_dump` creates a custom archive; the source database is
dropped before `pg_restore --exit-on-error` restores a different database.
Verification uses typed `StoreSnapshot`/`Approval` equality and exact `Instant`
seconds+nanos, not floating-time tolerance or string normalization. It checks
the store UUID, ACTIVE state, approval/fingerprint/caps/expiry, full unknown
holds, replay denial, remaining-cap denial and six exact scope denials.

The runner prints a private `/private/tmp/01036-local-budget-restore-*`
directory containing `report.json`, `restore.log` and isolated build logs.
Successful exit requires all assertions and verified resource cleanup.

## Latest Actual Qualification

The 2026-10-19 JDK 21.0.9 run completed with local status `PASS`, scope
`LATEST_RUNTIME_P5_SUBSYSTEM_ONLY`, and `p5Pass:false`:

- Report: `/private/tmp/01036-local-budget-restore-i4UlcjOnMBOJ/report.json`.
- Assertions/cleanup log: `/private/tmp/01036-local-budget-restore-i4UlcjOnMBOJ/restore.log`.
- Run ownership UUID: `d99605b2-36ca-45f4-8e91-f799d8660b44`.
- Actual archive SHA256:
  `75df591ad7453abf2dccde3fdbae7db416700b779942bce50c832e66925e0d6d`.
- Qualified runner SHA256:
  `5bac9b1997719ea6f1ccc33204517c450569ed287e4b52c8cfa35406101ea692`.
- Private pin-only Java copy SHA256:
  `d34f05a2cf8ec48a9b05531458403b09d57391c6b318f43712547b7c7098fd3f`.

The actual source database was dumped, dropped, then restored into the different
synthetic destination. Typed identity/approval/accounting, full unknown holds,
replay/cap denial and six scope denials passed against the latest pinned image,
using all 424 embedded runtime libraries. The report records schema 19 and zero
provider calls. Both completion markers are present; cleanup is
`OWNED_RESOURCES_REMOVED`. Independent post-run exact-label container and network
listings were empty. The private report/log files are 0600 in a 0700 directory.
The repository Java test remains unchanged; no hosted/customer/funding state,
backend, runtime/framework source, CI or UI was changed, committed or pushed.

## Evidence Boundaries

The first latest-image attempt is preserved at
`/private/tmp/01036-local-budget-restore-UVZQawgt68fy/report.json` (`FAIL`,
`LOCAL_COMMAND_FAILED`, cleanup `OWNED_RESOURCES_REMOVED`). Its
`maven-classpath.log` records the missing offline workspace framework BOM; Java
compilation and the restore test were not reached. The private tooling POM avoids
that workspace model dependency without fetching, installing or rebuilding any
framework/runtime package. An earlier shell launch omitted Node from PATH and
failed before the runner or Docker resources existed; the command above preserves
the inherited PATH. These failures are not passing restore evidence.

The next pre-compilation failure is also preserved at
`/private/tmp/01036-local-budget-restore-lzGyGcEkfaMr/report.json` (`FAIL`,
cleanup `OWNED_RESOURCES_REMOVED`). Its offline tooling resolution requested
uncached Byte Buddy 1.18.3 and Jackson annotations 2.20. The image already embeds
the runtime-managed versions above; excluding those two tooling transitives
retains the exact image libraries and changes no restore assertion.

The original passing execution is retained locally at
`/private/tmp/01036-budget-export-restore-typed.log`. Failed harness logs are
preserved, not rewritten:

- `/private/tmp/01036-budget-export-restore-test.log`: internal networking did
  not expose the host port required by the initial host-JDBC harness.
- `/private/tmp/01036-budget-export-restore-internal-client.log`: Docker refused
  test-class copying into a read-only client root filesystem before startup.
- `/private/tmp/01036-budget-export-restore-final.log` and
  `/private/tmp/01036-budget-export-restore-verified.log`: JSON numeric-node
  comparisons failed after real export/restore; typed record comparison replaced
  that comparator without weakening the underlying identity/hold assertions.

The latest-runtime report is labeled `LATEST_RUNTIME_P5_SUBSYSTEM_ONLY` and
retains `p5Pass:false`, even when its local subsystem status is `PASS`.
This proves a local budget-store subsystem restore. It does not prove hosted
whole-topology recovery, CP reconciliation after a stale backup, HTTP startup,
v9 provisioning/output-cap integration, provider transmission or usage billing.
Synthetic local approvals do not allocate or reset the real campaign grant.
Customer conversation/handover, Inbox volume/outbox, action-receipt recovery and
actual cross-target financial high-water reconciliation remain separate hosted
P5 gates; this local result does not discharge them or qualify the live R7 path.

Restoring an older backup can retain the store UUID while regressing usage.
Never resume paid work merely because the identity matches. Keep paid egress
and customer ingress blocked until all usage and outstanding holds since the
checkpoint are authoritatively reconciled; unknown outcomes do not release
credit. This rehearsal restores an immediate quiescent checkpoint with no
intervening provider traffic. It does not certify an automated stale-backup
reactivation protocol.
