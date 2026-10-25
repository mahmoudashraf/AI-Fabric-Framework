# Optional Release-Scoped Runtime Drain

This LoomAI capability uses AI Fabric 0.8.41 without framework changes. It is
disabled by default, including when target/release metadata is present. Normal
cold apply does not require a drain, operations-service availability or generation
adoption. Core's confirmed native stop is the cross-release single-writer boundary.

## Enablement And Storage

A reviewed immutable version may select `providerConfigJson.operationalDrainEnabled=true`.
The Coolify renderer requires a pinned artifact advertising
`runtime-release-scoped-drain-v1` and migration ID
`runtime-release-scoped-retirement-v24`, exact frozen authority, and a dedicated
target datasource/schema. It emits `LOOMAI_RUNTIME_TARGET_RETIREMENT_ENABLED=true`,
the exact deployment/tenant/target/release identity and an immutable
`LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V2` contract plus its UTF-8 SHA-256 digest.
Specialist execution uses JDBC state with existing frozen encryption/fingerprint
secrets. Absent/false opt-in leaves ordinary cold configuration unchanged.

V24 creates release-scoped gate, command, snapshot, turn/worker-permit and index
receipt tables. Every active lookup uses tenant + deployment + target + release.
Each new release starts ACTIVE at epoch zero, even when the same artifact is
cold-applied against retained AI storage after an older release was draining or
fenced. Old gates, command history and unsettled permits remain intact but are
not adopted, reset or counted as the new release's lifecycle attempts.

During V24 only, a fully identified legacy singleton and its unsettled permits
are copied to that exact release tuple. A same-release artifact restart retains
its state, epoch and fence; incompatible topology fails closed. No legacy gate
is copied into a different release, and the old tables remain unchanged.

AI state remains target-owned. Existing conversations, document catalogs,
manifests, indexing state and other data are not reset or moved. Actual outstanding
target-owned work still blocks drain. Old singleton/generation tables remain
audit history and are never read or written by the active gate implementation.

## Private Protocol

POST routes under `/api/private/runtime-target-retirement`:

- `/status`: exact target; current release-local state and epoch.
- `/commands`: binding, expected epoch, command, frozen deadline and optional core evidence reference.
- `/snapshots`: binding; fresh persisted observations and remote obligations.
- `/snapshots/{snapshotId}/readback`: same current binding and release; saved evidence.
- `/index-retirements` and `/index-retirements/readback`: separately authorized, exact fenced index scope; never implicit cold/STOP cleanup.

`/generation-transitions` and `/cold-maintenance-generation-transitions` are
retired. They still authenticate before returning HTTP 410; they cannot adopt or
restore a generation. The manifest no longer advertises generation adoption.

Use the existing private API key and signed assertion. Deployment/tenant,
issuer/audience/expiry, trusted backend caller and read/write scopes remain
mandatory. Responses are no-store. Named errors are a sanitized `{ "code": ... }`;
`DRAIN_NOT_PROVED_COMPLETE` means reobserve, not success.

BEGIN_DRAIN blocks new roots and allows existing owned continuations. Accepted
turns remain valid during that drain; cancellation advances the fence and cannot
restore old permits. Commands use exact payload replay and expected-epoch CAS.
BEGIN/FENCE and other non-cancel mutations cannot execute after the frozen
deadline. Expiry does not erase work, authorize disposal or silently select cold.

CANCEL is allowed only before irreversible authority/reclaim flags. Once
AUTHORITY_REVOKED, restarting the same release does not restore authority. A new
cold-applied release has its own gate; there is no same-release resume shortcut.

## Actual Worker Coverage

Runtime-owned ingress, store/control and worker adapters remain owner-safe.
Indexing adapters cover queue dispatch/claim, synchronous after-commit processing,
scheduled lease/process/ack and cleanup boundaries. The injected
`aiFabricExecutionTaskExecutor` decorates actual specialist/parallel-chain tasks.
Durable attempts remain visible until executing code exits, even when a framework
row or future has already been cancelled/expired. They do not hold one exclusive
database lock while a parent waits for parallel children.

Complete inventory and installed boundary coverage are both required. Unsupported
custom/in-memory stores, enabled review recovery and Quartz scheduling retain
explicit blockers. Missing tables or workers never become zero evidence. Crashed
attempts are not silently expired into success.

## Remote Obligations

Local drain is not remote quiescence. Snapshots retain exact CONNECTOR,
VECTORIZATION_RUNNER and HUMAN_INBOX obligations from the frozen topology.
The core gateway exposes their actual kinds in `Result.remoteDependencies()`.
It returns local readiness from OBSERVE_DRAIN, but FENCE cannot proceed without
a real core-owned journal reference when obligations exist.

The parent owns supported native worker/Inbox stop and authoritative host readback,
with durable intent and exact ownership. Runtime FENCE captures a fresh snapshot
and rechecks local work after that step. A journal reference is not supplied by
the operations-service job. Unknown external providers remain blocked. Physical
stop evidence must not be described as proof that remote in-flight work completed
gracefully. No business-system, external vector service or customer S3 deletion is
implicit in this protocol.

The smallest fully local graceful canary uses Runtime-owned documents/indexing,
JDBC specialists and no remote dependencies. Hosted qualification remains the
parent's responsibility; capability advertisement is not a hosted success claim.
