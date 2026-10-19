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
