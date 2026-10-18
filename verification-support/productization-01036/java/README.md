# Local Budget Store Export/Restore

This opt-in standalone test lives outside runtime image watch paths. It neither
builds an image nor changes the framework, runtime, CI configuration or a live
database. It is not part of the ordinary runtime Maven test discovery.

From the repository root, with Node.js, JDK 21, Maven and local Docker available:

```sh
RUN_01036_LOCAL_BUDGET_RESTORE=true \
  node verification-support/productization-01036/run-local-budget-restore.mjs
```

Both exact images must already be cached. Pulling is disabled:

- Runtime source `600f136bf0becb9a473ff1e085bbc9896b4ef696`, image digest
  `sha256:d3f744e6c68677b49d128a37a99c31b0717ebf87b1b6d8dd45adbe14030baa28`.
- PostgreSQL 16 image digest
  `sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea`.

The test also checks the previously inspected local image IDs, so another
architecture's variant fails rather than silently substituting an image.
Maven uses offline `dependency:build-classpath` only. Its plugin, runtime test
dependencies and private budget modules must already be available locally.
Missing cached dependencies fail the run; no install, package, public framework
release, source compilation or shared `target` modification is performed by
Maven. `javac` compiles only this test into a fresh private temporary directory,
using production classes extracted from the exact cached runtime image.

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

## Evidence Boundaries

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

This proves a local budget-store subsystem restore. It does not prove hosted
whole-topology recovery, CP reconciliation after a stale backup, HTTP startup,
v9 provisioning/output-cap integration, provider transmission or usage billing.
Synthetic local approvals do not allocate or reset the real campaign grant.

Restoring an older backup can retain the store UUID while regressing usage.
Never resume paid work merely because the identity matches. Keep paid egress
and customer ingress blocked until all usage and outstanding holds since the
checkpoint are authoritatively reconciled; unknown outcomes do not release
credit. This rehearsal restores an immediate quiescent checkpoint with no
intervening provider traffic. It does not certify an automated stale-backup
reactivation protocol.
