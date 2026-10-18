# R7 Admission And Staging Expiry Sidecar

Control-plane primitives for `010.36` / `01036-capacity-expiry-recovery`.
The ledger itself performs no remote provisioning or deletion. Explicit reviewed
controllers, adapters and lifecycle workers provide the integration boundaries.
No measured envelopes, resource limits, durations or customer grants are seeded.
CPU-only observations cannot approve a policy: measured RAM/disk remain required.

## Integration Contracts

- Staff calls `approvePolicy`, `approveCustomerGrant`, and `approveManifest`.
  Each resource dimension must be explicit, including replicas, DB/index/volume
  storage, dependencies, apply/build concurrency and API/token/spend allowances.
  Fleet/target baseline includes non-ledger services/build load; headroom protects
  promotion/recovery. Per-customer limits cover the fleet and each entitled target.
  Quantities are allowance allocations, not a usage-metering or billing service.
- Main must derive `Manifest` from the real reviewed immutable version/topology
  and resolved binding snapshot. It includes the complete R5 dependency footprint,
  not just runtime CPU. Published version/config hash is checked against existing
  records. The customer self-service API accepts only target/version/operation
  scope; it derives and immutably records the manifest before calling this sidecar.
  Offer-backed manifests include the exact reviewed managed-dependency fingerprint
  for the offer's template version and revalidate it against the full envelope.
  Staff-managed non-offer manifests retain their existing reviewed evidence path;
  an `offer-topology:` manifest never falls back when its offer association is absent.
  The sidecar's internal `Reserve(operationKey, manifestId)` contract remains a
  trusted server-to-server continuation and never accepts customer-supplied amounts.
- Implement exactly one `AdmissionEvidenceSource`. It must read actual Coolify
  placement and current target/config/manifest/binding-bound prerequisite evidence,
  including restore, privacy, verified owner contact and configured notification
  transport. A referenced document or green health check is not that readback.
- Staging is pinned to server `zf25hgk9694bt7q0zwb98ado`, destination
  `xjhfu65nacrr30xax5cp0ry7` (`46.224.145.148`). Production is pinned to server
  `kvufjk78dj4wyhjgp1mlxecr`, destination `r3thf2xmxcjn1tt2bclabebz`
  (`46.225.162.106`). `dtp-coolify-prod-staging` is forbidden; aliases cannot
  duplicate one server's capacity pool. There is no staging-to-production fallback.
- Call `reserve` and commit before effects. Identical keys/fingerprints replay the
  durable result; conflicting keys/payloads return 409. One live slot exists per
  deployment/target. A different manifest/key cannot silently allocate a second
  fleet: it requires main/R5 generation-safe reuse/reconciliation or proved release.
- Call `beginProvisioning` before effects, under authenticated staff/deployment
  permissions. Persist reservation ID/generation in every provider handle/marker.
  It revalidates policy/prerequisites and locks/rechecks local ownership/version/
  profile rows. Do not call this sidecar while holding an outer lifecycle DB lock;
  its short REQUIRES_NEW transaction must finish before remote/provider work.
- `markOutcomeUnknown` / `markCleanupFailed` retain the full envelope indefinitely
  until proved reconciliation. Expiry, stopped containers and missing metadata do
  not release quota. Apply/build/spend allocations are conservatively held with
  the full reservation; no automatic window reset or lease-based release exists.
- Implement `AdmissionResourceReadbackSource` against real handle inventories and
  R3 assignment/session/confirmation/inbox/background-work evidence. Include every
  owned resource generation, DB, index and volume; preserve customer sources,
  foreign/shared resources and production. `reconcile(false)` requires settled,
  complete current inventory. `reconcile(true)` additionally requires resources
  absent, ingress blocked, retention verified, verified zero assignments and
  actual zero work counts. Null counts, stale generation/revision/worker or stale
  readback fail closed. An HTTP delete acknowledgement is not absence evidence.
- `recordActivity` is a trusted accepted-work hook, never browser polling/health.
  `extendStaging` records explicit bounded extensions; keeping staging requires
  current entitlement, quota and visible expiry. Neither can undo an expiry claim.
- Poll `dueStaging`, request durable two-channel `NoticeWork`, and deliver actual
  in-console plus verified-owner email notices. `AdmissionNoticeDeliverySource`
  reads durable transport receipts for each idempotent cycle/channel delivery key.
  Backoff/attempts/failure/dead-letter state is persisted; undelivered notices block
  automatic expiry. Delivery must precede reclamation by the full approved notice
  window even when delivery was late. Old-cycle receipts cannot authorize expiry.
- `claimExpiry` persists a worker token, drain/recovery deadlines and exact fence;
  `expiryWork` recovers that claim after restart. It authorizes R3 planning/draining,
  not deletion. Main must actually block ingress, drain/resolve human work, obtain
  actual assignments and execute target-scoped retirement. Acknowledge its durable
  operation with `acknowledgeRetirementHandoff`; capacity still remains reserved.
  Only generation-bound readback after the recovery window releases it. Retry an
  uncertain R3 operation through R3, not a second expiry claim or deployment delete.

## Cancel A Never-Used Reservation

`POST /api/platform/deployments/{deploymentId}/targets/{targetProfileId}/admission-reservations/cancel-unused`
is an explicit staff-only alternative to retirement when an apply was rejected
before any release or provider effect. It is not a cleanup shortcut for failed,
unknown, provisioned or retained-index workloads.

- Supply the exact reservation ID/generation/revision, manifest and binding
  fingerprints, published version/hash and original allocation authorization.
  The server verifies durable no-effect evidence; callers cannot provide counts
  or claim that external data is absent.
- Explicitly enumerate any never-activated consumer binding at revision 1 and
  its complete DRAFT workspace ID/revision set. Unknown, active, formerly active
  or release-backed assignments block cancellation. The owning consumer service
  records normal binding history; draft apps and published configuration remain.
- No-wait row locks reject competing apply/binding/lifecycle work with a conflict.
  The binding history, retained-handle disposition, receipt and physical-capacity
  release commit atomically. Any failure rolls back the whole operation.
- A retained external vector binding becomes `RETAINED_UNUSED`; its original
  metadata, credentials, collection prefix and external data are not removed.
  Same-tenant fresh binding requires the exact committed cancellation receipt,
  matching owner approval and a distinct prefix. Cross-tenant reuse remains
  prohibited. Customer-managed document storage remains untouched.
- Replay the identical command after an uncertain response. Changed expectations
  are not an idempotent replay. Successful cancellation reports
  `CANCELLED_UNUSED_NO_EFFECT` and `reclamationPerformed=false`: no provider
  deletion, credit refund, financial-allowance reset or zero-index claim occurs.

## Acceptance And Remaining Gates

`AdmissionStoreTest` and its PostgreSQL subclass cover complete dimensions,
headroom, concurrent quota checks, duplicate commands, restart/replay, policy
changes, physical placement, retained failed cleanup and generation/readback.
`StagingExpiryContractTest` covers two-channel notice evidence, late delivery,
dead-letter/backoff, extensions/entitlement, stale callbacks and persisted claims.
`DeploymentAdmissionServiceTest` covers active ownership, customer null scope,
operator access, missing adapters, published/config drift and ownership read races.
`AdmissionMigrationPostgresTest` checks V165 uniqueness/slot checks and empty gates.

Local verification: 68 test cases passed via isolated javac/JUnit, including
PostgreSQL 16 Testcontainers; no Maven/shared-target build was run. Parent still
owns sequential full integration builds and main/R3/R5 wiring. Published acceptance
is blocked by real reviewed CPU/RAM/disk/topology measurements, approved policies,
restore/privacy/contact/transport evidence, usage enforcement and actual provider/
assignment/session/human-work adapters. There is no fake-ready default adapter.
