# Provider Budget Ledger

Java 21, product-root `1.0.0`. Standard JDBC and `DataSource` only at runtime; no Spring, framework semantics, transport, token estimation, pricing, or billing-platform dependencies.

This module provides accounting primitives. It does **not** prove an adapter's token/cost bounds, enforce a provider's response cap, install live enforcement, or authorize provider calls by itself.

## Public API

Package: `com.ai.fabric.budget`. Records and interface: `ProviderBudgetLedger`; implementation: `JdbcProviderBudgetLedger(DataSource)` or `(DataSource, Clock)`.

```java
Scope(customerId, deploymentId, targetProfileId, environment, credentialIdentity, rateCardReference)
Limits(chatAttempts, totalTokens, costNanoUsd)
Approval(id, scope, limits, expiresAt)
Usage(inputTokens, outputTokens, costNanoUsd)

Snapshot approve(Approval approval)
ChatReceipt recordChatIngress(String approvalId, Scope scope, UUID serverAttemptId)
ReservationReceipt reserveAttempt(String approvalId, Scope scope, UUID attemptId, Usage upperBound)
Attempt settle(String approvalId, Scope scope, UUID attemptId, Usage settledUsage, String evidenceReference)
Attempt markAmbiguous(String approvalId, Scope scope, UUID attemptId, String reason)
Snapshot stop(String approvalId, Scope scope, String reason)
Snapshot read(String approvalId, Scope scope)
Instant effectiveExpiresAt(String approvalId, Scope scope)
ValidityExtensionReceipt extendValidity(Approval original, ValidityExtensionCommand command)
Optional<ValidityExtensionReceipt> readValidityExtension(String approvalId, Scope scope, String extensionId)
```

`BudgetException.code()` and `.reason()` are typed/sanitized failures, not fallback instructions. The parent can combine a current capacity denial with scoped `read()` for a safe usage/native-event response without treating the entire approval as stopped. Approval creation and all ledger methods are trusted-server operations: the owning application must authorize them. Never bind approval/scope/ceilings from an anonymous caller. Scope includes a first-class required `targetProfileId`, persisted as `target_profile_id`, separately from deployment/environment/credential identity. Approval replay requires exact scope, all limits, and expiration; it cannot transfer an approval to a different target, reset counters or increase caps. A newly minted approval ID is a new authorization, not an automatic retry/restart mechanism.

## Receipt And Hold Rules

- Allocate a fresh **server-generated** UUID for every HTTP chat ingress, including client retries. Replaying the same internal ledger call is idempotent; accepting a caller's UUID as the server ID would allow ingress-count evasion. `ChatReceipt` exposes `serverAttemptId`, `ordinal`, `newlyCounted`.
- Allocate a fresh provider attempt UUID for each possible actual send/retry. Only `ReservationReceipt.newlyReserved() == true` corresponds to newly reserved capacity. An exact replay returns `false` in every state, including after restart/expiry/stop; it never permits retransmission. Changed bounds under the same ID fail. A returned receipt is not a security token and cannot stop a faulty caller reusing it; the transport adapter must enforce single-use sending.
- `Usage` keeps input/output bounds separate and checks their sum without overflow. Tokens and nanoUSD are nonnegative signed 64-bit integers. The aggregate token ceiling covers both directions. The module has no canary constants or implicit rate math.
- `Snapshot.accountedUsage` is settled usage plus the **full** upper bounds of RESERVED/AMBIGUOUS attempts. `providerAttempts` and ingress counts never decrease. `Attempt` exposes `id`, `upperBound`, `state`, `settledUsage`, `evidenceReference`, `ambiguityReason`.
- Settlement is exact-once: identical usage and evidence replay safely; any conflicting replay fails. Each settled component must be no greater than its original bound. Cost is a conservative **accounted upper bound**, not a claim about invoice-exact charges; a transport may use native token totals multiplied by approved uncached upper rates. Evidence references must describe trustworthy native usage/rate-card provenance, not invented estimates.
- Over-bound settlement commits `SETTLEMENT_BOUND_EXCEEDED`, marks the attempt AMBIGUOUS and retains its full hold **before** throwing `SETTLEMENT_CONFLICT`. It cannot silently enable further calls or refund the hold. Report this externally as a broken bound; accounted usage is not proof the provider's real spend stayed within it.
- Timeouts, process crashes, lost replies, and missing usage never release holds. There is no refund/TTL/reaper/reset API. Settlement can reconcile a trustworthy result after expiry/stop; it does not clear the stop. Calling `markAmbiguous` after settlement or changing its existing reason fails.
- Before each new applicable operation, expiry is checked with exact `Instant` precision. Replays/readback/reconciliation are allowed after expiry but provide no new send capacity. Explicit stop is irreversible. Ordinary non-fit returns `LIMIT_EXCEEDED` with `CHAT_LIMIT`, `TOKEN_LIMIT`, or `COST_LIMIT`, and commits no new attempt, counter change, or stop state. Existing holds remain; trustworthy settlement may make a later **fresh** attempt fit. There is no automatic retry, refund or cap increase. An operation that would exceed a ceiling is never inserted. Reaching the ingress count cap prevents another ingress, not completion of an already admitted turn. Arithmetic integrity overflow and over-bound trusted usage durably stop before throwing; scope conflicts deny without mutating another scope's authority.

## Transaction And Schema Contract

### Explicit Validity Extensions

An authorized host may extend an existing approval's effective expiry without
changing its immutable original approval, fingerprint, limits, counters, stop
state or outstanding holds. This is not a top-up, replacement approval, refund
or retry mechanism. The host must first verify its ACTIVE store binding and the
exact approval, scope, store UUID and runtime fingerprint.

The command carries `schemaVersion=1`, `extensionId`, `approvalId`, `storeUuid`,
`runtimeFingerprint`, `expectedEffectiveExpiresAt`, `expiresAt` and `reason`.
The ledger locks the original approval, compares the expected effective expiry,
and inserts a separate extension receipt. The new expiry must advance the current
expiry, be in the future, and be no more than 24 hours from authorization. A
stopped approval cannot receive a new extension. Only a digest of the reason is
stored in the ledger receipt.

An exact duplicate returns the historical receipt without extending again;
changed duplicates or stale expected expiry fail. After a lost response, read
the same receipt ID. Absence is not permission for an automatic retransmission.
Original `Snapshot.approval().expiresAt()` remains unchanged; effective expiry is
queried separately and enforced for new ingress/provider reservations.

The LoomAI runtime owns Flyway V20 and exposes these operations only through
private `/api/admin/provider-budget/validity-extensions` administration. POST
requires `runtime:admin:provider-budget:write`; receipt GET requires
`runtime:admin:overview`. Anonymous chat cannot authorize an extension. The
library still executes no DDL and makes no provider calls.

### Separately Funded Top-Ups

Runtime Flyway V21 preserves the original approval limits in `original_*`
columns and keeps the existing database checks on the effective `max_*`
ceilings. A top-up appends one receipt and increases those ceilings in the same
approval-row transaction. It never resets requests, settled usage, reserved or
ambiguous holds, expiry, the store identity, credential scope or a stop flag.
The original approval and its fingerprint remain unchanged across restarts.

Private runtime POST `/api/admin/provider-budget/top-ups` requires
`runtime:admin:provider-budget:write`; GET `/top-ups/{topUpId}` requires
`runtime:admin:overview`. Commands fence the approval, store UUID, runtime
fingerprint and expected effective limits. Exact replay returns the historical
receipt; changed replay, stale limits, reused funding evidence, stopped or
expired approvals fail. Readback exposes original `limits` separately from
`effectiveLimits`. Token reservations use the effective ceilings.

Platform admins use `/api/deployments/provider-usage/top-ups/plan`, then send the
returned exact command to `/{topUpId}/execute`. A new owner-approval evidence
reference and payer reference authorize **additional funding**, not another draw
on the exhausted original grant. Each command is bounded to 10,000 interactive
attempts, 100 million tokens and USD 100; normal staff authorization and exact
release/credential/capability checks still apply. Reviews and INTENT/ACK/UNKNOWN
events are immutable. An uncertain send is never automatically repeated; use
GET status and POST `/{topUpId}/reconcile` for receipt-only observation.

Top-ups do not expand the approved model profile. A separately reviewed model
and rate-card binding is required for a new model; existing approvals are not
silently reinterpreted. Neither operation is a public customer billing API.

### Independent Transactions

Every method obtains its own connection, uses `SELECT ... FOR UPDATE` on the approval row, and commits before returning. No network work occurs inside these transactions. Approval-row-first ordering serializes ingress, reservations, settlement and stop across runtime/worker instances. Approval insertion races are resolved by uniqueness plus exact-request comparison, not an overwrite.

Supply a dedicated, non-transaction-enlisted `DataSource`. An already non-autocommit connection fails closed rather than joining caller work. Do not supply a transaction-aware proxy whose `commit` can be suppressed. The ledger cannot verify arbitrary malicious DataSource implementations. Independent transactions intentionally retain committed holds if caller application work rolls back. A commit-response loss returns a storage error, and replaying the same attempt can return only `newlyReserved=false`.

The library runs **no DDL**. The owning app must review and incorporate `src/main/resources/com/ai/fabric/budget/provider-budget-schema.sql` in its own Flyway migration, supply a persistent PostgreSQL authority, and control DB roles/approval authorization. Runtime and paid embedding-worker instances must share that authority and immutable scope. No schema bootstrap on missing tables, no in-memory production fallback. Database failure denies work. The schema does not contain credentials, prompts, payloads, or provider response bodies; credential/rate-card values are identity references only.

## Tests

Run this module alone, without changing parent module registration:

```sh
mvn -o -B --no-transfer-progress -f ai-fabric-product/ai-fabric-provider-budget/pom.xml test
```

The same contract runs on H2 2.2.224 (test-only baseline) and real PostgreSQL 16 through Testcontainers. Docker/PostgreSQL unavailability fails the PostgreSQL proof; it is not silently skipped. H2 2.4.240 was observed retaining a closed DDL session for an `IN` constraint; the test baseline is pinned rather than weakening the PostgreSQL schema constraint.

Transport interception, Responses API accounting, full-context bounds, model-specific output caps, provider retries, keys, runtime/worker wiring and paid canaries remain outside this phase. No positive adapter/production-readiness claim follows from these ledger tests alone.
