# 010.44 Customer-System Boundary Execution Record

## 2026-10-19 Start And Dependency Checkpoint

- Owner authorized implementation of [010.44](010_44_CUSTOMER_SYSTEM_INTEGRATION_BOUNDARY_CORRECTION_PLAN.md)
  and continuation of independent 010.36 work. Prior plan/audit-only restriction
  is superseded; provider changes still require their exact reviewed scope.
- Baseline backend source is `cab320545`, migration195, verified on both control
  planes in the previous checkpoint. Runtime remains `d2b1a9792` / AI Fabric0.8.36.
  No new source/image is claimed deployed by this record.
- Parent owns generic promoted-artifact ownership contracts and publication,
  admission, customer composition and worker rechecks. Raman owns AI-only
  catalogue/migration V196/helpers; Ampere owns Railway/vector safety/migration
  V197; Franklin owns independent demo provider-target and indexing-readiness
  correctness. These are disjoint source tasks, not new hosted deployments.
- Owner confirmed optional LoomAI-hosted AI-state PostgreSQL as a choice when
  creating a new dealership deployment. Implement explicit managed/customer-
  provided selection; neither permits business database provisioning. Newton
  owns the actual generic SQL binding/generation/retirement support and V198;
  parent owns reviewed bootstrap selection/configuration. No H2 workaround.

### Independent Work And Test Evidence

- Parent implemented source-level artifact contracts with a dedicated manifest
  schema. Missing ownership, external fixtures and customer business persistence
  cannot qualify for customer managed dependencies. The reviewed contract is
  tied to the promoted digest/commit and content hash, not an image-name heuristic.
- First sandboxed focused test attempt could compile but Mockito self-attachment
  was denied: 44 tests, 36 initialization errors, zero skips. This is not accepted
  test evidence and did not justify weakening the mocking configuration.
- First explicitly permitted local JVM/socket run: 88 tests, one assertion
  failure, zero errors/skips. Missing dependency-contract promotion failed
  closed but returned generic schema400 rather than the intended bounded409.
  Validation order was corrected in source; rerun is required.
- The same run exercised the previously uncommitted embedded cancellation HTTP
  regression. This work is independent of the business-system topology, but no
  live reservation cancellation or deployment of that fix is implied.

### Resume The Old 010.36 Rollout Only After

1. The corrected AI-only offer and external bindings are validated through the
   normal customer path, including the generic ownership and lifecycle gates.
2. AI operational storage ownership is selected and genuinely supported, without
   provisioning a managed database behind an external JDBC override.
3. New exact topology/capacity and model-budget evidence is reviewed. Old v11/v12
   admission for the bundled graph is not transferable approval.
4. Corrected source/image, backups and local integration gates pass before any
   hosted runtime apply. Never run prepared old-topology scripts unchanged.

Keep held reservation `f0e90dba-14f0-4787-b583-7dcf712bc4b2` intact until its
own fenced unused-cancellation flow is verified and explicitly executed. Preserve
Free-01, the approved production vector resource, all stopped workloads and the
same cumulative staging campaign. No allowance reset or production promotion is
authorized by this checkpoint. UI extraction remains last and independently
reversible. Preserve unrelated dirty UI/site/media files; keep Hetzner access
open as the owner instructed.

## Source Checkpoint: Catalogue Complete, Integration Continuing

- Raman completed V196, new external stock/discovery/engagement plugin versions,
  AI-only templates with optional managed Inbox, Inbox-only dependency fixture,
  corrected publishers and external integration guide. Reported local evidence:
  71 focused Node tests, 124 full support tests including opted-in synthetic
  Docker restore, and four isolated PostgreSQL/Flyway/parser tests, all passing
  without skips. Parent integration review remains required; no hosted claim.
- Offer publication deliberately remains blocked until actual scoped external
  DATA/ACTION binding enforcement and SQL-mode integration pass. This is a
  truthful dependency checkpoint, not a substitute successful publication.
- Owner-approved storage choice is implemented in source at customer bootstrap
  and immutable draft configuration. Definition includes separately reviewed
  per-mode topologies; admission selects actual demand from the published mode
  rather than charging managed SQL to BYO. Reviewed topology permits zero owned
  disk; limits still carry the reviewed maximum envelope.
- First Ampere/Newton workers hit selected-model capacity errors. Their partial
  source was retained and they were closed before transferring write ownership.
  Hume now owns Railway/vector/V197; Einstein owns AI-state SQL/V198, both with
  inherited parent model and max effort. No partial patch is accepted as verified.
- Raman continues the missing generic external-service binding API in a separate
  source scope/V199, coordinating typed profile definitions and wiring with parent.
  Franklin continues the provider-only lead identity migration in the independent
  demo database, with no substitute website inventory inserts.
- Parent verified local Docker24.0.2 is available and the existing frontend
  productization API regression passes11/11. These do not close backend integration,
  UI browser testing or hosted/provider verification.

## Integration Checkpoint: Explicit AI State And External Accounts

- Owner reconfirmed that optional managed AI-state PostgreSQL is a deliberate
  new-deployment choice, not an inventory database. Customer-provided PostgreSQL
  remains a separate typed binding; no secret JDBC override may silently create
  a managed database behind it.
- Parent isolated focused suite passed145 tests, zero failures/errors/skips.
  Follow-up customer SQL wrapper/API checks passed locally; broader integrated
  rerun is in progress after compiler/release wiring changes. Earlier compilation
  failure referenced the not-yet-added external profile accessor and is not
  counted as passing verification.
- Customer SQL API uses customer ownership, reviewed binding slots, active
  entitlement, strict HTTP types and private HMAC idempotency over write-only
  credentials. Raw credentials do not appear in result, command or audit records.
- External DATA/ACTION connections now compile target-independent aliases from
  immutable reviewed offer profiles using detached install copies. Provenance
  records the profile fingerprint; release snapshots and typed environment
  projections bind the actual target/account/credential revisions. Integrated
  tests and source review must pass before any publication checkpoint is removed.
- Einstein reports167 SQL-related tests passing with zero failures/skips,
  including PostgreSQL/TLS/migrations. Credential rotation is an explicit remaining
  gap and is being addressed; BYO production readiness is not yet claimed.
- Hooke completed the independent demo correction:117 tests passing with zero
  failures/errors/skips, including four real PostgreSQL tests; packaging passed.
  Provider-only stock identities no longer require a local website inventory row.
  No hosted demo migration or deployment is implied.
- Hume also hit a model-capacity error. Its partial C1/C2 source was preserved
  and the worker closed. Parent review and a fresh ownership/cleanup test run are
  still required before that patch is accepted. Hooke is independently reviewing
  the customer SQL wrapper while parent integrates external connection lifecycle.
- Canonical optional Inbox spec now lives under
  `product-services/basic-loomai-inbox-service/deployment/`, not test resources.
  The focused71 support tests and12 frontend API contract tests passed locally.
- No live apply, model call, provider deletion, commit or push occurred at this
  checkpoint. 010.36 hosted rollout remains dependent on corrected composition,
  fresh admission evidence and verified source. UI extraction remains last.

### Subsequent Cross-Boundary Verification

- Parent ownership/cleanup plus external compilation/runtime-env run:89 tests,
  zero failures/errors/skips, including real PostgreSQL ownership persistence,
  concurrency and rollback behavior. Historical ownership is never backfilled.
- Parent actual Spring compiler/persistence run initially failed64 tests with23
  context errors because the H2 test schema lacked new AI-state release columns.
  Added the repository-pattern test-only schema mirror; production SQL unchanged.
  Rerun passed70 tests, zero failures/errors/skips, including full Spring context,
  marketplace compiler, customer bootstrap persistence, admission readback,
  typed connector environment and customer SQL wrapper regressions.
- Independent review identified missing transaction on customer SQL GET and
  missing reviewed-offer version validation before SQL binding. Both corrected
  in parent source; unit negative case now passes. Separate real-transaction
  customer SQL persistence/replay verification is still in progress.
- Admission now compares current external binding fingerprint references with
  the exact reviewed manifest set, rejecting changed, newly added or removed
  external authority before provisioning. A source test covers stale credential/
  account binding evidence. None of these local results implies hosted readiness.

### Real Template Bootstrap Follow-Up

- Generic external lifecycle/API/provenance run passed47 tests, zero failures/
  errors/skips, including production PostgreSQL migration through199.
- Symbolic install initialization, owner catalogue, bootstrap, compiler and SQL
  wrapper run passed99 tests, zero failures/errors/skips. Frontend shared API
  transport now passes14 contract checks and `tsc --noEmit`; no layout extraction.
- A new real Spring/PostgreSQL template-factory test then found an integration
  defect not covered by those narrower suites: unconfigured required DATA installs
  are disabled, but input-free discovery actions compiled immediately and failed
  with `ROUTE_SOURCE_PROJECTION_SOURCE_UNKNOWN` before offer association.
- Parent is correcting configuration-only bootstrap to defer an ACTION plugin
  only when its declared structured source reference belongs to a disabled,
  explicitly required DATA plugin. The deferred plugin remains in the visible
  unresolved-required-plugin set. Unknown unrelated sources and ordinary
  compilation/publication retain strict validation. This is dependency handling,
  not natural-language matching or a semantic fallback.
- The first rerun caught a local helper-overload compilation typo, corrected in
  source. Actual PostgreSQL bootstrap plus compiler regressions are rerunning.
  No success, publication, hosted apply or production readiness is claimed for
  that path yet.
- Corrected operative010.36 ownership/placement, template composition, P1 andR5
  wording: external stock/receiver/simulator systems are not provisioned or
  reclaimed by customer AI deployments. Historical records remain preserved.

- Real PostgreSQL bootstrap now passes after the dependency correction. The test
  verifies initial disabled/unbound stock and receiver plugins, explicit unresolved
  dependent actions, strict rejection by ordinary compilation, reviewed symbolic
  initialization and successful configuration-only compilation. It proves zero
  new provider handles, dependency graphs, releases or external account bindings,
  with all provider API clients untouched. It is not a hosted customer/grant proof.
- Combined rerun with the independent HTTP suite passed21 tests, zero failures/
  errors/skips. HTTP-only source and20 embedded cases committed separately as
  `00f3852c5` (`Fix scoped unused-reservation cancellation HTTP errors`). Not pushed
  or deployed. Live control planes remain at the previously verified `cab320545`
  checkpoint until a later explicitly verified rollout.

### Read-Only Server Capacity Snapshot

2026-10-19 around00:38UTC, authorized SSH remained reachable without firewall
changes. No service start/stop/delete/build or provider/model call was made.

- Staging:8 CPUs, load0.90/0.90/0.88,10501MiB available RAM of15608MiB;
  swap1095MiB used of8191MiB; root disk35% used,95GiB available. Container CPU
  snapshot was low. Used swap alone is not proof of active memory pressure.
- Production:4 CPUs, load0.16/0.25/0.29,12108MiB available RAM of15610MiB;
  no swap configured; root disk12% used,127GiB available. Container CPU snapshot
  was low. This is not a capacity/admission measurement or a load benchmark.
- Several running containers expose the host memory ceiling rather than a
  per-container cap. Preserve the reviewed resource envelopes and avoid parallel
  rebuild fleets; this observation alone does not authorize changing unrelated
  workloads or deleting customer/provider databases.

### Normal Customer Command Verification

- Extended the real PostgreSQL template test to use actual reviewed offer
  publication, staff invitation, customer acceptance and customer configuration
  bootstrap for both explicit AI-state storage modes. Provider clients remain
  mocked effect boundaries; no remote service readiness is implied.
- The first run failed both normal customer cases: template installation marks
  its editable draft `MODIFIED`, while initial storage selection accepted only
  `DRAFT`. Corrected the initializer to the existing editable states `DRAFT` or
  `MODIFIED`, retaining initial deployment/no-active-version, ownership and
  immutable-choice checks. Added a focused regression; rerun is in progress.
- Examining the next normal document-binding step exposed a probable missing
  required boolean in the new template's knowledge-install defaults. Catalogue
  worker is verifying an explicit `false` default, never inferred public consent.
  Source registration/public audience approval and initial-index consent must
  remain separate authorized actions. Full binding/publication/hosted guards
  are not cleared by configuration-only or mocked authorization tests.

- Follow-up ownership inspection found the same category in Coolify application
  and managed-SQL reconciliation: exact-name adoption, password updates without
  creation evidence, and deletion of a conflicting application after409. Added
  findingF6/workstreamC4 and assigned a disjoint provider reconciliation worker.
  No live resource was touched. This must not be hidden behind successful
  Railway/vector tests or treated as a reason to delete independent demo systems.
- The actual customer S3 binding and version publication steps now execute in
  the expanded PostgreSQL test. Its latest failure is a test-fixture vault setup
  error: the global signing key was submitted through the managed-name API.
  The test now uses the supported staff global-secret update API. No production
  secret-service permission was broadened to make the fixture pass.

### Actual Customer Workflow Through V200

- Parent merged PostgreSQL run passed19 cases, zero failures/errors/skips:
  three actual V196 template/customer workflow cases and16 customer AI-state
  transaction/persistence cases. All production migrations throughV200 apply.
- Normal staff offer publication, invitation, customer acceptance and bootstrap
  run with real authorization/services for both explicit SQL modes, not mocked
  customer verdicts. Configuration creation makes no provider-resource graph,
  runtime release or business database.
- The normal customer S3 binding enables the required knowledge install while
  preserving its explicit false installation-level public-content declaration.
  Actual source/public-audience approval and initial indexing consent remain
  separate. Real version publication/compiler provenance succeeds.
- Scoped external review/bind commands persist and replay credentials for two
  immutable versions. Missing bindings reject resolution. Production requires
  distinct bindings; candidate and production refs are disjoint from the
  original staging refs. Resolving the original version remains unchanged.
  Persisted customer command receipts contain none of the synthetic credentials.
- The SQL wrapper's16 cases prove actual writable GET transactions, reviewed
  ownership/entitlement/version checks before vault/probe access, persisted HMAC
  replay, concurrent command serialization and complete late-failure rollback,
  including V200 candidate isolation. Its remote SQL probe is mocked; separate
  low-level TLS/privilege/migration cases cover that boundary.
- These are local integration proofs, not hosted provider/network/capacity,
  full apply, generation replacement, runtime indexing or promotion evidence.
  C4 Coolify ownership source work and hosted acceptance remain open; publication
  helpers stay checkpointed until final source integration is accepted.

### Pre-Migration Backup Permission Checkpoint

- Read-only SSH container inspection reconfirmed both running control-plane
  images at `cab320545`; no service was started, stopped, deleted or changed.
- Prepared a temporary, exact-resource backup script for only the two Platform
  databases. The permission reviewer rejected execution because the complete
  archives may contain user/session records and stored credentials and need
  explicit approval for that payload/local destination. The script did not run;
  no fresh archive is claimed. No alternative export or indirect retry occurred.
- Requested owner approval for mode0600 archives under
  `/private/tmp/01044-control-plane-pre-200-20261019/` and isolated local restores.
  Source/test work continues independently. Hosted migration remains pending
  that backup authorization and the corrected source verification gates.
- Support regression now passes136 tests including its explicitly opted-in
  synthetic Docker restore case, zero failures/skips. Shared UI API contracts
  pass14 tests. These are not the requested fresh production archive restores.
- Owner then explicitly approved storing the archives and doing what is needed
  for the plan. Resubmitted the same exact-resource script with that approval;
  export/restore evidence is pending. This does not reset financial ceilings,
  authorize new paid resources or permit deleting customer source data.

### Approved Fresh Recovery Evidence

- Exact approved export completed for both Platform databases into the private
  directory above, with a mode0600 manifest and custom-format archives. Staging
  archive:8,653,072bytes, SHA256
  `476027c7bf79f956686ffd6cb5b7e97d247ff3e55a4dc78bcc0c77cdb1bdd93d`.
  Production archive:7,636,211bytes, SHA256
  `bce407cfb355059b8da927e9a5f51269d7a99ac1dd68f38b6e8cd7df7a06b363`.
  Coolify, customer business and runtime databases were not exported.
- Both actual archives passed the isolated PostgreSQL18 restore verifier with
  expected migration195 and exact cached official image digest
  `sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873`.
  Both temporary owned fixtures were removed. Private reports:
  `/private/tmp/01036-local-control-plane-restore-J5bHQsTyKYiU/report.json`
  and`/private/tmp/01036-local-control-plane-restore-NtDif0Wd1BvC/report.json`.
  This is current control-plane database recovery evidence, not runtime/index/
  object-store recovery or evidence thatV200 has been deployed.
- Current actual Platform UI production build passes. It retains the existing
  large-chunk warning (approximately1.93MB JS/479kB gzip), not a rendering or
  staff-parity proof. No extracted UI or frontend image was deployed.
- Read-only staging host baseline:25 samples/24 five-second intervals,8 cores,
  observed CPU busy p95 approximately761mCPU, minimum available memory
  10,907,451,392bytes, Docker filesystem available101,559,857,152bytes.
  Swap is excluded. No candidate workload was included and no admission policy
  or provider budget was created/approved by this baseline.

### Continued Independent Verification And Publishing Boundary

- Production's read-only baseline also completed:25 samples across24 five-second
  intervals, four cores, CPU busy p95 approximately364mCPU, minimum available RAM
  12,617,818,112bytes and available Docker filesystem136,058,945,536bytes. No swap
  was configured. Like staging, this is an idle-host baseline, not candidate
  capacity, admission or load evidence.
- SQL worker completed245 focused regression cases and two actual Spring/H2
  profile cases, zero failures/errors/skips. Demo worker completed146 cases,
  including16 PostgreSQL cases and barrier-forced concurrent idempotency checks;
  parent is rerunning that frozen demo source. These counts overlap prior runs
  and must not be added into a claimed unique release total.
- Authenticated read-only GitHub metadata shows current origin
  `mahmoudashraf/AI-Fabric-Framework` is public. Requested an explicit owner
  destination decision before publishing private Platform/runtime/connector
  changes. No source push, image publication or repository visibility change
  was performed. Source integration, local verification and private recovery
  work continue independently of that decision.

### Independent Demo Commit And Additional Lifecycle Review

- Parent reran the frozen independent demo:146 tests, zero failures/errors/skips,
  including16 PostgreSQL cases. Committed only those14 demo files locally as
  `8b11c4217`. This removes the website-inventory identity dependency, preserves
  legacy receipts and adds scoped provider stock validation and truthful indexing
  readiness. No push or hosted demo migration occurred.
- Actual Platform backend packaging passed before the subsequent lifecycle
  follow-up. Independent code review then identified two gaps: a committed
  binding revocation could race between a worker check and provider effects,
  and release recovery checked SQL identity but not generic external bindings.
- Parent adds an explicit persisted-release interlock: binding revocation and
  offer revocation/re-invitation cannot claim success while an exact unfinished
  external release owns the effect interval. Deployment and entitlement locks
  serialize snapshot creation and revocation; no long provisioning transaction
  or hidden cancellation is introduced. Recovery/queued redispatch revalidate
  current grants, bindings and frozen snapshots. Focused and actual transaction
  regressions are in progress; this checkpoint is not passing evidence yet.
- An additional opt-in rehearsal restores the actual archives without backend
  workers and validates/applies the five new migrations. Its first fixture used
  an unavailable Testcontainers helper (corrected), then Docker internal-network
  port suppression prevented startup before restore. The loopback-only private
  bridge fixture restores successfully, but explicit pre-migration Flyway
  validation rejects both copies. Diagnosing bounded version/error codes; no
  validation bypass, repair, migration-history edit or hosted change was made.

### Migration Rehearsal And Ownership Continuation

- Bounded Flyway diagnostics showed exactlyV196-V200 pending, with no historical
  checksum mismatch. The rehearsal now asserts that exact pending set, uses
  normal `migrate()` validation and runs post-migration validation. No ignore
  policy, repair or migration-history edit was introduced.
- Both actual archived databases then migrated195 to200 successfully, all200
  migrations validated, zero failed/skipped cases. SHA256 aggregate comparisons
  preserve all original fields in21 customer, identity, credential, resource,
  offer and reservation tables. V198's two new release columns remain null for
  historical releases; the V197 ownership table stays empty. An initial whole-
  row comparison correctly detected the schema's added null fields, not changed
  historical values, and was refined to assert both conditions explicitly.
- The actual migration fixture uses a new local Docker bridge and loopback-only
  PostgreSQL, not an internal Docker network (which suppresses published ports).
  It starts no Spring application or worker. An additional local-Unix-socket
  guard and explicit matching Docker CLI host are added for the final rerun.
  The earlier network-disabled restore evidence remains independently valid.
- Parent's45 focused external-binding/recovery/entitlement cases pass, zero
  failures/errors/skips. Additional actual transaction/concurrency tests are
  still running independently; these are not hosted revocation or live apply.
- C4 worker's191-case checkpoint passes, including107 new provider-fake cases
  with the real ownership ledger and four PostgreSQL receipt cases. It explicitly
  does not close nine old positive fixtures or all cleanup paths. Worker is
  continuing those fixtures, deletion journaling and the internal retirement SPI.
- Parent corrects Coolify's automatic retry loop: creation and other mutations
  are not repeated after ambiguous429/5xx responses; GET-based lifecycle actions
  are explicitly non-retryable too. Safe read retries remain. Local embedded
  HTTP tests verify request counts; no provider call was made.
- Target retirement now requires the same creation proof before provider/volume
  mutation and journals deletion around actual physical absence checks, through
  an internal ownership SPI. Parent owns that adapter and wiring tests; the C4
  worker owns the real provider/receipt implementation.
- New dependency worker Jason owns the managed-dependency provider region and
  tests. Its pending-create recovery also selected by name/description; this must
  fail closed and use exact durable graph/customer/target creation receipts.
  Inbox/operator/lifecycle/volume/retirement paths are included. No broad cleanup
  closure is claimed while this source work remains.

### Merged Actual-Module Checkpoint

- The actual `Platfrom/backend/pom.xml` run passes108 cases, zero failures,
  errors or skips: two actual-archive migration rehearsals,98 provider-budget
  cases (49 unit and49 PostgreSQL), two admission administration HTTP cases,
  four runtime-authority persistence cases and two deletion/retention cases.
  This reruns the local Unix-socket safeguard as well as migrations195 to200.
  Expected denial of unowned legacy cleanup remains visible; it is not changed
  into success or a missing-provider-configuration fallback.
- The managed-dependency worker froze its corrected source after109 passing
  cases, including eight actual PostgreSQL cases, with zero failures/errors/
  skips. The parent reviewed its receipt, graph, placement, storage, operator
  and retirement integration. Creation does not recover by resource name or
  description; an uncertain creation remains blocked for explicit reconciliation.
- Parent's embedded Coolify HTTP and retirement-adapter run passes57 cases,
  zero failures/errors/skips. Final qualification of the provider implementation
  and exact persisted-handle exclusion remains separate and in progress.
- The independent external-binding PostgreSQL concurrency run initially found
  nine assertion failures caused by a duplicated error-code prefix, not missing
  blocking. Parent corrected the prefix. Its40-case rerun is still pending;
  do not report it as green before the fresh result.
- The earlier shared-platform-service provisioning region is not the new
  customer-owned managed-dependency graph. It still warrants a separate bounded
  review for name/FQDN recovery and lifecycle authority. No blanket safety claim
  or destructive live reproduction is made for that region.
- The full actual backend regression suite and local support contract suite
  are running. No hosted upgrade, customer offer publication, external fixture
  migration, reservation change, model call or UI extraction happened in this
  checkpoint. Private-source publishing still needs the owner's repository
  destination decision; local commits are permitted and remain unpublished.

### Shared-Service Follow-Up Accepted Into Scope

- The bounded independent review confirms additional ownership defects in the
  older shared-platform-service region: first name/FQDN match adoption, force-
  recreation deleting a discovered different application, retained source UUID
  during retargeting, restart before readback and deletion without proved absence.
  Legacy Railway refresh also rebinds metadata and may create a domain on reads.
  These are staff-authorized paths, not a demonstrated customer authorization
  bypass or evidence that a particular live resource is foreign.
- Added workstreamC5. Jason now owns that earlier shared-service region, a real
  service-scoped ownership journal/V201 and focused tests. The frozen109-case
  customer managed-dependency region remains separate. Parent owns the actual-
  archive migration rehearsal update after the new migration exists.
- This expands local source qualification; it does not authorize adopting,
  deleting or rewriting historical shared services. No hosted mutation occurred.
- The support suite rerun with both explicit Docker opt-in and local socket
  passes136 cases, zero failures/skips. The prior opt-in command lacked the
  required socket argument and failed before creating the synthetic fixture;
  no restore guard was relaxed.
- Posting the repository-destination prerequisite to the existing `Required for
  production` task was denied by the permission reviewer. No retry, indirect
  message or new-task workaround was attempted. The owner can answer in the
  main task; publishing stays on hold and independent local work continues.

### Frozen C1-C4 Foundation

- Final C4 qualification is green:316 cases, zero failures/errors/skips,
  including37 legacy provider cases,167 C4 cases and eight actual PostgreSQL
  cases. All357 existing test sources compiled. The verified six-file source
  manifest matches the frozen worktree. This includes the real retirement SPI,
  exact persisted-handle exclusion and deletion-state recovery. Log:
  `/private/tmp/01044-c4-completion-tests/c4-bounded-green.log`.
- External binding/entitlement concurrency rerun is green:40 cases, zero
  failures/errors/skips. All conflict assertions passed unchanged. Before/after
  manifests differ only in the separately owned dependency helper, not external,
  entitlement, recovery or test source. Log:
  `/private/tmp/01044-external-revoke-pg-reviewer/final-run.log`.
- The parent staged only backend/catalogue/support/C1-C4 correction documents as
  a local source checkpoint. UI, website, publishing media and unrelated changes
  stay outside it. A bounded staged-file credential-pattern scan found no AWS
  access-key IDs, GitHub tokens or private-key PEM blocks; this is not a blanket
  historical secret audit. The full regression run is still pending and C5 will
  have its own source change and verification path.

### V201 Actual-Archive Rehearsal

- Parent expanded the opt-in local test to the exact six pending migrations
  V196-V201 and an explicit zero-row assertion for the shared-service ledger.
  Both actual approved archives pass, two cases with zero failures/errors/skips.
  Normal Flyway validates all201 migrations; all original fields in the21
  preserved tables remain unchanged, historical release SQL columns stay null,
  and both ownership ledgers stay empty. No repair, ignored checksum, backfill,
  hosted call, Spring worker or provider/model operation is introduced.
- Log: `/private/tmp/01044-archive-migration-201.log`. Parent session1334 completed;
  its separate build target is free. The shared-service source implementation
  still needs its own lifecycle qualification and merged-source verification.
- Backend foundation is committed locally as `5cef0210a`. Earlier local commits
  remain `00f3852c5` and `8b11c4217`; none has been pushed by this work.

### Full Foundation Regression Result

- Actual-module backend suite completed3,263 tests across348 test classes:
  one failure, one error, zero skips. Both are the old owned-dependency physical-
  reclamation cases in `PlatformManagedProductProvisioningServiceTest`; their
  fixtures omit the newly required real ownership helper/graph/receipts and fail
  at the earlier ownership guard. No other failure was reported.
- This is not green release evidence. Jason owns correcting those two fixtures
  alongside the C5 shared-service tests, preserving real positive ownership proof
  and the original physical-absence assertions rather than weakening production
  checks. Parent will rerun the merged suite. Session9644 is complete; the actual
  backend build target is free.
- Parent's review of in-progress C5 also requires denial of an old UUID that
  reappears after its receipt is `DELETED`, matching the completed C4 boundary.
  C5 must prove real Spring transaction suspension/local durable intent and keep
  provider HTTP outside database locks. These are final qualification criteria,
  not claims that the unfinished shared-service source already passes.

### Shared Admin Caller Verification

- Parent adds the shared-service authority check to Product Admin secret
  rotation before vault/registry changes. Its focused actual-module run passes
  19 tests, zero failures/errors/skips, including ordered authorization-before-
  write and denial-with-no-local-side-effects cases. It preserves the existing
  contract: this command updates desired credentials, not an automatic deploy.
- Pure desired Shopify billing/release settings remain editable before first
  provisioning. They neither grant resource ownership nor trigger remote
  mutations; adding a live-resource requirement there would obstruct legitimate
  initial configuration. Separate managed-inference provisioning is not silently
  included in the C5 safety claim.
- Parent requires exact deployment/application readback for shared start/restart;
  an already-healthy app cannot hide a null or mismatched provider response.
  C5 qualification also covers deleted-UUID reappearance and retained credentials
  whose lifecycle authority is unproven. V201 is evolving to include explicit
  mutation-pending state, so its final frozen checksum must be rehearsed again
  against both actual archives before that source is qualified.

### Final Ownership Follow-Ups And Frozen Migration

- The final focused actual-module run passes376 tests across nine classes,
  zero failures/errors/skips. Log:
  `/private/tmp/01044-ownership-final-intended-credential.log`. Counts overlap
  earlier C4/C5 runs and are not additive. The previous375-case fixture failure
  is superseded, not counted as passing.
- Shared Docker services require a full source commit and image digest, deploy
  by digest and verify repository/hash after update and terminal deployment.
  Malformed desired metadata fails before provider effects; live image drift
  cannot be hidden by an already-healthy application.
- Direct Coolify deletion requires explicitly empty attached storage. Mounted,
  unavailable or malformed storage must use the existing frozen target-retirement
  contract. App/database deletion does not request volume, network or broad
  Docker cleanup; app stop does not request broad cleanup either.
- Shared-service credential rotation requires its own creation claim, not just
  application ownership or a secret-name prefix. The caller captures one exact
  secret name; committed-state preflight validates that same name before the
  vault write. A real PostgreSQL transaction test proves an uncommitted foreign
  name cannot pass preflight and reach the vault. Supplied or historically
  unproven credentials remain retained and cannot be rotated by this command.
- Independent sol6.1/max read-only re-review closed all confirmed P1/P2 findings
  in these fixes. No framework changes, query-term routing, hidden recovery or
  provider mutation were introduced. Structural identity/URI checks are resource
  authorization, not LLM semantic decisions.
- Final actual-archive rehearsal passes2 tests, zero failures/errors/skips,
  using V201 SHA256
  `707b3c3e10a560784f9c2e8d31a2cd2e8717ace89be88693067c79e19a8b54ae`.
  Source and compiled migration hashes match. Both original Platform backups
  migrate195 to201 with normal validation,21 preserved tables unchanged,
  historical SQL release columns null and both ownership ledgers empty. Log:
  `/private/tmp/01044-final-archive-migration-201.log`.
- Full merged actual-module regression is running separately. C5 and these
  follow-ups are not yet committed/published at this checkpoint. Hosted control
  planes remain unchanged atcab320545/migration195. Publication remains held
  for the repository-destination decision; UI extraction remains last.

### Full Merged Backend Qualification

- Completed2026-10-19 04:15UTC using the actual `Platfrom/backend/pom.xml`,
  offline Maven and permitted local Docker/JVM access: **3,343 tests across350
  classes, zero failures, errors or skips**, process exit0. Log:
  `/private/tmp/01044-final-merged-backend.log`; fresh XML results under
  `Platfrom/backend/target/surefire-reports` were counted from this run only.
- All1,843 backend main/test source and resource files remained unchanged
  throughout the run. Their sorted path/content SHA256 manifest is
  `8a34bf788cfe27912ba2bcdc26611642bae613ec2742495b13cdaa62e9b2da20`.
  This is the final merged result, not a sum of overlapping focused runs.
- The two old reclamation cases remain present and now use real durable
  ownership/graph fixtures. All14 test slots in the rewritten shared-service
  test remain; expectations that previously authorized name adoption or deletion
  now require explicit rejection. Additional positive/negative ownership cases
  are in dedicated unit and PostgreSQL classes. Tests were not removed to hide
  the earlier failures.
- Final archive qualification is separate:2 passing actual-backup tests against
  the frozen V201 checksum above, not included in the3,343 normal test count.
  The semantic-decision source guard and `git diff --check` also pass.
- A bounded scan of the17 changed/new backend files found no AWS access-key ID,
  GitHub-token or private-key PEM patterns. It is not a historical secret audit.
  Local source publication still requires the owner's repository decision.
- Backend packaging is the next local check. No full hosted release gate,
  corrected offer activation, customer runtime, provider effect, framework
  release or UI extraction is claimed by this green local regression.

### Local Commit And Package Checkpoint

- Committed C5 and the parent ownership follow-ups locally as `12fb4e4b2`
  (`Guard shared-service lifecycle and credentials with durable ownership`).
  Exactly21 backend/verification/010.44 files were staged. The bounded credential-
  pattern scan and cached whitespace check passed. UI, website, media and
  unrelated documents were not included. Prior foundation commit is `5cef0210a`.
- After that source commit, offline Maven packaging with `-DskipTests package`
  and `Platfrom/backend/pom.xml` exited0. Log:
  `/private/tmp/01044-final-merged-backend-package.log`.
  Tests were deliberately not rerun during packaging; the unchanged source had
  already passed the full3,343-case run above.
- Every required local execution session is complete; independent coding/review
  agents are closed. No public push, image publication or live mutation occurred.
  Current origin is public and the owner-facing repository question is pending.
  Do not substitute a public publication or unreviewed deployment path.
- Resume at the hosted source/artifact and corrected-customer canary gates,
  preserving the existing budgets, reservation and external resources. Complete
  non-UI acceptance before advancing separately reversible UI extraction.

### Owner Visibility Clarification And Rollout Started

- Owner clarified that the repository was always public and directed continuation.
  The earlier publishing hold came from an incorrect interpretation of product-
  ownership wording, not an owner requirement to change repository visibility.
  Existing origin remains unchanged; private auth material and backups remain
  excluded. Commits through `ceaacf12e041a8991db76d42e8d3bcc69fe2a0a7` are pushed.
- Core-image CI39216761742 and affected-dependency CI39216761828 both pass.
  Backend index digest is
  `sha256:f59d045994b81cd2e8fb1208defaf50b1f50a2b16e8441fe393781a8b2020d11`.
  UI image job is skipped. Dependency CI verified/published the dealership demo
  and Inbox; it did not rebuild the unchanged simulator or framework runtime.
- Fresh private pre-rollout Platform-only backups are retained at
  `/private/tmp/01044-control-plane-pre-201-rollout-20261019/`. Both actual archives
  pass the frozen six-migration rehearsal, two tests/zero failures/errors/skips.
  Log: `/private/tmp/01044-pre201-rollout-archive-rehearsal.log`. Read-only live
  preflight found both planes at195, zero in-flight releases and zero managed
  graphs; the held customer has no release or provider allocation.
- Staging Coolify deployment `dh5xzilz7o1r5mu2ovehlu63` is queued against the exact
  digest. A private durable operation receipt records intent before mutation.
  Its first read-only verification correctly refuses unfinished deployment;
  it does not infer success from the old healthy app or choose one of two
  rollout containers. No deployment mutation was retried.
- Parent owns control-plane rollout; Carver owns the corrected attended offer
  publisher; Helmholtz owns read-only independent external-fixture preflight.
  They have disjoint source scopes. UI extraction still remains last.

### Control-Plane Rollout Verified; Reservation Preserved

- Both control planes now verify source `ceaacf12e`, digest `f59d0459...20d11`,
  normal migration201, public healthUP, all five V196 catalogue entries, zero
  fabricated customer/shared ownership rows and zero managed-dependency graphs.
  Staging deployment is `dh5xzilz7o1r5mu2ovehlu63`; production deployment is
  `j14dp61hc4mdc98yl786l3p6`. Exact application identity, terminal success,
  running-container source labels and RepoDigests are checked, not just health.
  Private receipts: `/private/tmp/01044-backend-{staging,production}-rollout.json`.
- Staging's malformed cancellation request returns400 with
  `UNUSED_CANCELLATION_REQUEST_INVALID`, not401. Existing staff/customer API
  reads succeed after rollout. No valid cancellation is implied by that check.
- The unused-v11 preflight passes: no releases, release bindings, dependency
  graphs, provider allocations or accepted apply commands, and unchanged
  published version/draft/inactive app/credential fingerprints. Anonymous401,
  customer403 and omitted-fence-revision400 produce no state changes. Quota
  lifetime allocations remain0 attempts/0tokens/0cost.
- Actual cancellation execution was rejected by approval review BEFORE process
  execution because earlier owner directions preserve this exact hold. Do not
  retry directly or indirectly without the owner's specific answer. No valid
  cancellation request or reservation/consumer detachment occurred. Parent asked
  for approval in the main task and continues independent work.
- Corrected customer bootstrap creates a new tenant. Existing Free-01 is bound
  to the old unused tenant; current safe-reuse contract requires the same tenant
  and exact committed cancellation. Parent separately asked about an audited
  unused-binding handoff or another approved endpoint. Neither a broad key nor
  same customer is permission to remove this boundary. No handoff implemented.
- New25-sample/120-second read-only host baselines pass after rollout. Staging:
  8cores, busyCPU p95~918mCPU, minimum availableRAM11,052,855,296B, available
  Docker filesystem101,254,774,784B. Production:4cores, p95~366mCPU, minimum
  availableRAM12,722,995,200B, available filesystem135,754,674,176B. Swap is
  excluded. These do not include candidate load or approve admission. Private
  evidence: `/private/tmp/01044-{staging,production}-post201-host-baseline-20261019.json`.

### Attended Metadata Publication Review

This review permits an immutable staging preview offer and configuration-only
customer setup under the owner's continued implementation instruction. It does
not authorize overriding the retained reservation, transferring Free-01,
allocating a second financial grant, provisioning a business system, activating
an app, or claiming hosted workload readiness.

- `01044-ai-only-external-integration-ownership-20261019`: approve only the V196
  external-stock/external-business-action composition with an explicitly chosen
  managed AI-state PostgreSQL and optional managed Basic Inbox. The simulator
  and dealership application remain independently operated systems. No real
  Auto Trader production access is claimed.
- `01044-attended-staging-no-admission-20261019`: the implementing session is
  the attended operator for metadata preparation/readback. Runtime apply still
  requires all normal physical, binding, isolation and financial checks. Keep
  the existing promotion operations declared so this same immutable customer
  deployment can later complete that journey, but execute none until its exact
  production target/binding/admission/financial/readiness gates pass. Do not
  treat a named policy reference as an executed readiness gate.
- `01044-post201-host-observations-20261019`: refer to the two measured baseline
  files above. The proposed managed-Inbox/managed-SQL demand is2,000mCPU,
  3,584MiB memory,6,144MiB disk, one hosted AI database, two derived indexes
  and one managed AI dependency, not measured candidate consumption. Preserve
  host headroom and the existing charged reservation. Separate actual admission
  policy/grant/review remains required; this reference creates none.
- `01036-reviewed-supabase-public-protected-20261019`: use the existing reviewed
  Supabase public configuration (`xazkenhomhtpejjjqtsy.supabase.co`, health200),
  anonymous direct entry and explicit sign-in for protected actions. It does
  not prove a completed protected-action/cancelled-login browser canary.
- `01036-read-only-customer-s3-unversioned-20261019`: use only the owner's
  customer S3 storage and target-specific read-only prefix/credentials. Separate
  public-content approval and initial-index confirmation remain explicit. Do
  not index automatically merely because a binding exists.
- Exact reviewed stock endpoint/host:
  `https://external-vehicle-provider-simulator.46.224.145.148.sslip.io`, advertiser
  `990001`; stock/media use the simulator host. Business-action endpoint:
  `https://loomai-dealership-demo-api.46.224.145.148.sslip.io/api/internal/actions/execute`.
  Endpoint allowlisting is not proof that an account/scope is bound or authorized.
- The exact Inbox image `d7487188...3759` / source `2035dc36082fefccbfd3a757d38e493fa8f001fc`
  has successful CI39108639740 and an unstarted local image review. All five
  packaged migrations match that exact source and its management controller is
  present; no service was started during inspection. Private report:
  `/private/tmp/01044-inbox-image-review-8aTNeQ/report.json`.
- Staging registered/promoted artifact `dsa-509ba523` with the dedicated reviewed
  AI-operational ownership manifest, then published immutable Inbox-only spec
  `mds-dealership-ai-managed-inbox-v1-0`, fingerprint
  `9628e61095a25bf12d1d8f6412c2b91f0a80eac331f491fa25e8cb06457dd4f8`.
  Provider resources/graphs/new ownership receipts stay0; the old hold remains
  `RESERVED` and old canary releases0. No simulator or business database is in
  the new spec. This does not yet publish an offer or provision the Inbox.

### Attended Offer And Real Customer Configuration Verified

- Publisher/preflight committed and pushed as `81b9e5b6f`. Full support suite:
  175 tests, zero failures/cancellations/skips, including isolated local Docker
  restore. The promotion-permission fixture fingerprint was independently
  regenerated with the compiled production Java `CustomerOfferCodec`:
  `b2d7cffbf29f994403d837421c097d69f5e056430c72bbe185b34f582b527348`.
  Log: `/private/tmp/01044-attended-publisher-final-support-tests.log`.
- Published only staging metadata for
  `offer-loomai-engage-external-dealership-attended-v1`, immutable fingerprint
  `430d8b213209659fb47b59f12fecce166b960d90ebe2967e4c75fb6f28a1d595`.
  Existing owner `usr-7238e886` accepted invitation
  `cse-4732d3ab-3d44-412a-b286-a60e8200bd9c` under a real CUSTOMER_ADMIN session.
  Separate supported customer catalogue readback verifies its full projected
  contract. It still reports `liveReadinessProven=false`; no direct SQL write,
  synthetic entitlement or invented staff-read endpoint was used.
- Real configuration-only customer path passes all eight checks: owner session,
  reviewed profile/placement, bootstrap replay with no release, unactivated
  consumer replay, app replay/CORS/early-activation denial, read-only customer
  S3 binding/replay/redaction, immutable-version replay and direct foreign-scope
  denials. New deployment `dep-70b3d2c1`, tenant `ten-ad10424d`, consumer
  `01044-ai-only-canary`, installation `awi_pub_5d8a271e6f248a535a010b0531ca6573`,
  version `ver-df8d2843`, hash
  `321c6f902a2bafc87213150bb80a3f55d0eec797a6a1e2a51cedafb25df60f37`.
  Private evidence:
  `/private/tmp/01044-attended-{offer-verification,customer-configuration}-20261019.json`.
- Fresh read-only checks confirm the old reservation remains RESERVED/revision0,
  cancellation count0, new canary releases/graphs0, both ownership ledgers0,
  and lifetime model allocations0 attempts/0tokens/0cost. No database, simulator,
  business system, runtime or Inbox was provisioned by these metadata operations.
  S3 source objects were not changed and the app was not activated.
- Independent demo unsupported HTTP methods previously returned500. Narrow
  handler fix preserves Spring405 and the actual Allow header, without allowing
  GET execution or weakening authentication. Seven red regressions reproduced
  before the fix; focused15 and full161 tests/10classes, including16 PostgreSQL
  cases, pass with zero failures/errors/skips. Separate commit `f7b7bd58c` is
  pushed. Affected-only CI39221195575 passes, publishing the independent demo
  image; no framework, runtime or UI rebuild. Hosted verification follows below.
- Runtime apply still depends on the exact unused-reservation decision and safe
  Free-01 binding/handoff, plus normal scope, capacity, financial and readiness
  gates. No indirect retry of the rejected cancellation. Production self-service,
  real generation/index/actions/identity/handover, promotion and P7 are not yet
  certified. UI extraction stays last and separately reversible.

### Independent External Demo And Storage Read Preflight Verified

- Existing independent demo app `df2y27bjnisvi347ff23u2j1` is now verified healthy
  on exact source `f7b7bd58c9d20a6d7293a5e1207324eee071465e`, Coolify job
  `k1rn5gk96fskucbp7ahu4yeq`, running image
  `sha256:c455a0f983f51b94f9c7e3245d68e1494019c3efd959eccf6e1cb038679168eb`.
  Public status, exact deployment commit and live401/405/Allow POST checks pass.
  No business action was executed by verification.
- Its existing independent PostgreSQL is
  `s10ag41pp0ti4l93e5o0xq0f`, not a customer AI dependency. A consistent private
  custom archive/catalogue check preceded upgrade:34,651bytes, SHA256
  `fd3f814a332611109208c0286a562829eb620f14f3218c2512d8e7dfc325bcda`,
  under `/private/tmp/01044-independent-demo-pre-upgrade-20261019/`.
  Read-only discovery found actual migration1, not assumed3; the preflight
  stopped before effects and was corrected to that exact reviewed baseline.
  Normal migrations1-to4 then passed. All six website vehicles and73 request
  records retain their content fingerprints, excluding the two newly added
  nullable provider-identity columns. No website inventory reseeding or CRM
  database provisioning occurred.
- Added only the simulator's reviewed server-side provider-read configuration
  and accurate source stamp. The prior database/PII/internal keys, customer/
  tenant/deployment authority and Inbox/runtime configuration fingerprint are
  unchanged. Existing scope remains `dep-f023c863` / `cus-28e3b6f9` /
  `ten-80c0ae7c`; new customer authority was not silently added.
- CI also published registry image
  `sha256:8071777777591e5eb5c0bafa72ee7620bb8f11eed12c40b07b9d8387152cc040`.
  It is NOT the running image: Coolify rejected converting this existing
  source app to `dockerimage`, and its installed validator also rejects the
  newer `include_source_commit_in_build` update field. Both422s were reconciled
  read-only with no queued deployment. The installed validator was inspected,
  then its supported `git_commit_sha`-only update was used. One cached, non-force
  build deployed the existing app; no replacement app or validation bypass.
  Native source is now pinned to the above commit. Future source updates must
  deliberately advance it after verification, not assume branch push overrides it.
- Post-upgrade external preflight passes for the existing scope: scoped token
  exchange, six provider stock records, six reviewed media references, source/
  method/auth checks and inspected provider configuration. The new canary
  correctly remains blocked only by its different receiver authority. Stock
  count is not indexed count. No write, webhook, reconcile, model or provisioning
  request was sent. Private reports:
  `/private/tmp/01044-independent-demo-rollout-20261019.json` and
  `/private/tmp/01044-external-fixture-post-upgrade-20261019.json`.
- A20-second build observation on the8-core staging host measured busy CPU
  p95=4,400mCPU, maximum5,120mCPU, maximum I/O wait4%, steal0. The deployment
  completed during the window. Compare with the earlier idle/current-fleet
  baseline p95~918mCPU: source builds materially compete with serving capacity.
  This is one build observation, not causal proof of every prior incident or
  customer admission approval. Keep affected-only CI and prebuilt images for
  new services; avoid concurrent source builds on a serving host. Private data:
  `/private/tmp/01044-independent-demo-build-host-20261019.json`.
- Direct customer-S3 read preflight passes for both supplied identities:
  GetBucketVersioning succeeds/unversioned, three policy objects per prefix
  list/head/download with matching lengths and SSE-S3/AES256, and known foreign
  prefix HEAD returns403 in both directions. Private report:
  `/private/tmp/01044-s3-read-preflight-20261019/report.json`. Local verification
  copies are private; S3 source objects/policies remain unchanged. This deliberately
  used read APIs only, not the existing verifier's mutation-denial probes. It is
  not connector/indexing acceptance, write-denial proof or a bucket-policy audit.
  No S3 permission request is currently pending for these reads.
- The old reserved capacity, Free-01 ownership, production vector service,
  all30 stopped workloads and cumulative provider allowance remain retained.
  No new runtime apply or production promotion. Specific cancellation/handoff
  approval is still needed; continue through normal customer binding/admission
  and hosted acceptance after that, keeping UI extraction last.

### Owner-Approved Unused Reservation And Audited Vector Handoff

- Owner explicitly answered "Yes" to cancelling the unused `dep-6e29bab9`
  reservation and performing an audited Free-01 handoff to `dep-70b3d2c1`, with
  data and the existing allowance retained. Older pending-approval notes for
  these exact actions are superseded; no unrelated resource deletion, allowance
  reset or ownership transfer is authorized.
- Fresh private Platform-only backups and a fresh fenced zero-effect preflight
  pass. Approved-operation evidence uses new `01044-approved-unused-cancellation`
  files, preserving the older preflight. No business/Coolify database backup or
  data mutation is implied by the Platform archives.
- Implementation tracking: normal same-tenant binding remains unchanged. Add a
  separate staff-only, same-customer unused-binding handoff with exact committed
  cancellation/source identity, complete historical-source proof, immutable
  destination version/target and distinct collection prefix. Store an audited,
  idempotent receipt atomically with the new binding; preserve original handles,
  metadata, collections and secrets. Verify replay, forged/used/cross-customer
  negatives, rollback and PostgreSQL active-binding concurrency before rollout.
- Parent implements the core boundary; independent workers own the external
  binding API canary and real PostgreSQL handoff qualification. Hosted binding,
  admission, provider-budget and runtime gates still apply. UI extraction last.

### Approved Handoff Implementation And Qualification In Progress

- The exact cancellation attempt returned HTTP409. Independent read-only
  reconciliation confirms the reservation remains `RESERVED`, revision0, with
  no cancellation row or event. No blind retry, SQL ownership edit or provider
  deletion was used. A separate worker is diagnosing the original guard failure.
- Added a staff-admin-only handoff contract, additive V202 receipt ledger,
  complete-source locking, exact committed cancellation proof, immutable version
  and provider-scope verification, credential-bound replay and atomic audit.
  Ordinary same-tenant binding checks remain unchanged. No framework changes.
- Focused binding/authentication tests passed119 cases. The initial restricted
  JVM attempt could not attach Mockito; rerunning with normal local JVM
  permissions passed rather than suppressing the failing tests. New PostgreSQL
  qualification caught two real integrity gaps: a replay advanced a retirement
  epoch, and saved cancellation JSON was not compared to the actual row revision.
  Both are corrected in source and are undergoing repeat qualification.
- Fresh private staging/production Platform archives restore successfully into
  isolated local PostgreSQL18 and migrate201-to202 with zero preserved-row
  changes and an empty handoff ledger:2 tests, zero failures/errors/skips.
  Source S3, vector data, business systems and hosted control planes are unchanged.
- External binding API canary/client has23 local passing tests. It exposed a
  customer summary READ slot-authorization mismatch; the owning worker is
  correcting that boundary with regression tests. This is not permission to
  bypass customer authorization or treat an unknown command as successful.

### V202 Hosted Verification And Approved Operations Complete

- Both control planes verify source `0c42606c2`, migration202, public healthUP,
  exact image `sha256:66a8db202cc72a3593c93a257c72b2d9b73a2bfc258202439cb8feb9a2317aa6`.
  The actual backend suite passes3,463 tests/354 classes with zero failures,
  errors or skips; packaging passes. Real PostgreSQL handoff qualification
  passes59 tests. Fresh private staging/production archives restore and migrate
  201-to202 with selected preserved rows unchanged. UI/runtime were not rebuilt.
- Approved unused cancellation verifies `CANCELLED_UNUSED_NO_EFFECT`, receipt
  `c2658c3e-443d-4fbe-be72-f5b70720622b`; reservation released, no provider
  reclamation. Consumer unbinding is audited; original app/version/data retained.
  The audited Free-01 handoff to `dep-70b3d2c1` also verifies PASS with receipt
  `uvh-dffe08c0-2c73-49cd-bbdf-58e80352c557`. Old ownership metadata/secrets
  remain immutable. No model allocation, indexed data or runtime apply yet.
- Client v2 verifies actual customer email-to-owner mapping, explicit exclusive
  staff SESSION/API_KEY authority and identity-fenced commands;31 tests pass.
  Normal customer placement refresh returned200 before new external DATA binding
  preparation. This is not binding/indexing or customer-runtime acceptance.
- Owner clarified that the already approvedUSD12 applies to the entire task,
  across staging and production. Earlier staging-only allowance descriptions are
  superseded. Maintain one cumulative ceiling and existing engineering token/query
  bounds, with no automatic recharge, duplicate credit, erased history or fake
  usage. The zero-allocation mis-scoped grant needs an audited unused lifecycle
  correction and bounded task portions, not a new spending approval.
- External receiver isolation source `1735a5f90` passes197 tests including17
  real-PG cases. It is not deployed yet. A fresh private actual pre-V5 backup
  retains6 website vehicles and73 business receipts. V203 retained-index quota
  and V204 unused-grant source work run independently; hosted acceptance and
  production promotion remain open. UI extraction stays last.

### Unused Allowance Scope Correction: Source Qualification

- Owner's whole-taskUSD12 clarification is implemented as a generic staff-only
  unused-approval lifecycle, not a second credit grant or payment/refund path.
  V204 preserves the original approval/counters, adds immutable void receipt and
  attributed audit history, and exposes zero allocatable availability for voided
  approvals. Any allocation row or usage history prevents voiding. Replacement
  funding remains an explicit, separately bounded normal approval.
- Focused qualification passes166 tests, including73 actual PostgreSQL tests,
  zero skipped. Cases include authorization, strict HTTP parsing, immutable
  history, real migration, rollback, replay and concurrent read/allocation/
  initialization/void locking. This is source evidence, not hosted execution.
  No grant has been voided or replaced live; provider usage remains unchanged.
- Customer DATA approval/binding and exact replay now pass on staging:
  `eia-e531bb5d-9f91-4de7-9ceb-4d70079e90cd` and
  `eib-22b9772b-8981-40eb-8219-f18cf2ce72cf`. All206 support tests pass without
  skips. This configures credentials locally; it is not stock indexing proof.
- Actual private independent-demo V4 backup restored into an isolated local
  PostgreSQL16 fixture; applying the real V5 SQL preserved all6 website vehicles
  and73 receipts with unchanged canonical content hashes. All seven added
  scope fields remainNULL on legacy receipts. The fixture was removed. This
  SQL rehearsal is distinct from live Flyway/source rollout.
- Receiver cutover remains held: old runtime/connector are physically running.
  Their normal audited stop calls hit
  `COOLIFY_MANAGED_RESOURCE_RECONCILIATION_REQUIRED`; no stop/deletion is
  certified. Investigate supported ownership reconciliation before attesting
  old callers retired. Do not bypass the guard, rotate old credentials, or
  silently retarget the receiver. Existing business data remains intact.

### Standalone Origin Denial Correction And Receiver Cutover Contract

- Source inspection of real backend error markers reproduced a standalone
  bootstrap defect: catching a nested transactional404 left the outer transaction
  rollback-only, replacing an expected unknown-origin denial with500. Both origin
  checks now use nonthrowing expected-absence lookups; strict manifest/domain
  resolution still returns404, while genuine database failures propagate.
  Focused qualification passes76 tests, including45 real Spring transaction-
  proxy/JPA cases, zero skipped. Source only; not yet deployed.
- Legacy stop reconciliation confirms the two old AI callers have no historical
  ownership receipts. Their409 guard runs before Coolify mutation. They remain
  running; no creation receipt, ownership transfer or direct-provider bypass is
  authorized or claimed by this correction.
- The independent business receiver can still undergo an explicit credential
  cutover. It accepts only the new customer/deployment-scoped key and rejects old
  keys. The owner-approved sequential migration accepts the old callers' action
  outage; it does not attest that their processes are retired. Existing capacity
  baseline continues to include them. Cleanup needs separately reviewed legacy
  maintenance, not an invented historical creation record.
- Private receiver tooling stopped once before any mutation because its full
  provider-response fence included two host-observation timestamps. Exact prior
  HMAC reconstruction proves every other application field unchanged. Only
  `/destination/server/updated_at` and `sentinel_updated_at` are normalized; host
  identity/configuration remain fenced. The unused original preparation remains
  preserved. The subsequent environment-only update generated Coolify's documented
  preview mirrors; all pairs require exact values/flags. Reconciliation continues
  without repeating that update or claiming a deployment before its receipt.

### Retained External Index Lifecycle: Source Qualification

- V203 adds a reviewed preservation contract and immutable retained-index receipt.
  It uses the existing deployment-local read-only index/runtime readback APIs,
  never clears external collections, and requires exact scope, drain, revocation,
  topology, worker fence and zero unresolved work. Used bindings never become
  `RETAINED_UNUSED` and cannot enter the unused-cluster handoff path.
- Successful retirement is explicitly `RETIRED_WITH_RETAINED_DATA`, not a claim
  that all resources disappeared. Only proven reclaimed owned compute/volumes
  release their physical slots. Residual indexes and the reviewed byte envelope
  stay charged, with original manifest and financial history unchanged. Unknown
  bytes are not zero; the envelope is an allowance, not provider-measured usage.
- Independent review caught and corrected an approval-expiry dead end and a
  concurrent changed-proof replay gap. `reviewAcceptBefore` limits first review
  acceptance; accepted preservation is durable. Both replay paths now compare
  the exact winning proof. Preserved/unknown volumes cannot receive false disk
  reclamation credit, and retained rows cannot confuse later active selection.
- Qualification passes29 actual PostgreSQL tests and380 scoped regressions,
  zero failures/errors/skips. The actual full backend module and fresh202-to204
  archive rehearsal remain separate parent integration gates. No hosted retention
  approval, capacity release, model funding, framework change or UI extraction.
