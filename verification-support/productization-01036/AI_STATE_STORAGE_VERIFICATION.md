# 010.44 AI-State SQL Verification Handoff

## Source Scope

Completed the existing SQL binding patch in place. No framework, template
catalogue V196, generic ownership V197, external-integration V199, parent-owned
bootstrap/admission contracts or customer wrapper files were edited by this SQL
recovery task. No commits, hosted/provider operations or model calls were made.
Other dirty work was preserved.

The delegate and connection/probe/selection contracts are
`DeploymentAiStateStorage*`, `CoolifyRuntimeDatabaseBinding`, the private
`DeploymentAiStateStorageBindingController`, V198, V200 and the separate H2
`V120_25` mirror. V198 and the parent-owned `V120_24` mirror were not rewritten
for credential generations. At the parent's explicit request, the single test
profile Flyway target line advances from `120.24` to `120.25`. The owning apply/worker,
Coolify generation, release entity/recovery and retained-binding classification
carry the corresponding integration changes.

## Verified Boundaries

- Explicit `MANAGED_POSTGRES` / `CUSTOMER_POSTGRES` selection. External mode
  never calls managed PostgreSQL reconciliation and cannot use the old raw JDBC
  override path.
- Customer/tenant/deployment/target/published-version/config-hash scope, explicit
  migration consent, exact approved hosts, and HMAC-protected credentials.
  Summary, audit and provider-detail projections contain no connection secrets.
- Real PostgreSQL TLS encryption with trust and hostname verification. The
  negative hostname test intentionally logs a local certificate-name rejection;
  it is not a production server error.
- Direct and reachable `NOINHERIT` role privileges, business table access,
  database creation, global data roles, privileged memberships and executable
  security-definer functions fail closed.
- The repository's actual runtime migrations reach V19 in the runtime schema;
  the four connector migrations reach V4 in a distinct connector schema. Exact
  restart repeats execute no further migrations; the separate business fixture
  remains unchanged. No business inventory is created for the AI deployment.
- Non-secret release snapshots freeze storage identity, mode and the target
  profile's provider configuration, resource defaults, network policy and
  credential reference. Apply worker, provisioning and actual recovery recheck
  this identity before continuation. Completed-release reads are not treated as
  recovery operations.
- Runtime JDBC/Flyway/Hikari settings are pinned to the approved runtime schema.
  Connector generation uses its connector schema and has no role-creation or
  privileged bootstrap environment. Existing bootstrap variables are scrubbed.
- External SQL is a preserved binding: admission and provider-resource retirement
  do not treat it as a managed database. Even direct Coolify provider
  start/stop/restart/delete/status/log calls reject this binding kind before
  contacting the provider.
- Historical schema claims retain non-secret owner/tenant/deployment/target
  evidence after deployment metadata removal. SQL uniqueness prevents a second
  owner claiming the same retained schema; no automatic reassignment is allowed.
- Each new published target/version receives its own immutable credential
  generation, private HMAC key, connection secret references and approval.
  Staging never rewrites old binding metadata, claims, approvals, secrets or
  frozen release snapshots. Applied, queued, failed and unknown target/version
  history prevents staging a new generation; exact approved replay is read-only.
- Generation identity freezes the approved target profile before apply as well
  as the selected version/config hash, binding, consent and owner scope. Missing
  or tampered candidate credentials cannot fall back to a live generation.
- Real JPA/PostgreSQL row-lock contention proves competing candidate binds and
  apply acceptance serialize. An injected failure after staged vault writes
  rolls back the candidate's secrets, approval and generation together.
- A customer-prepared second restricted SQL login accesses the same already
  migrated AI schemas with real TLS/Flyway while the original login still works.
  The role preparation is test-fixture customer administration, never a Platform
  database-password mutation path.

## Test Execution

The isolated Maven harness is `/private/tmp/01044-sql-sidecar/pom.xml`; it compiles
the backend source without using the shared backend target directory. Its normal
profile runs SQL, Coolify-generation, execution/recovery, admission and retirement
tests. The SQL-only profile was used while a parent-owned contract was incomplete;
that limited profile was not substituted for the final integrated focused run.

The first integrated attempt encountered the in-progress parent
`externalIntegrationProfiles()` compile gap. A later run had fixture failures
(a missing environment name and a stale exact-latest-migration assertion); both
were corrected. No production check was weakened, no skipped Docker tests were
accepted, and no TLS/privilege policy was relaxed.

The original run log is `/private/tmp/01044-sql-focused-tests-final.log`; the XML and
text reports are under `/private/tmp/01044-sql-sidecar/target/surefire-reports`.
Original run, 2026-10-19 00:41:39 Europe/London: **167 tests passed, zero failures,
zero errors, zero skipped**, across nine test classes. This includes 34 binding
tests, 16 real PostgreSQL/TLS/migration cases, 14 Coolify SQL-generation and
lifecycle cases, plus execution/recovery, admission and retirement regressions.
`git diff --check` passed for the shared worktree at this checkpoint.
That original PostgreSQL run reached V199. It predates immutable credential
generations and is not evidence for V200 by itself. Its later 37-case binding
rerun overlaps those tests and must not be added to the total as distinct cases.

The first current generation-focused run passed **63 tests, zero failures/errors/
skips**. The expanded PostgreSQL run passed **88 tests, zero failures/errors/skips**,
completed 2026-10-19 01:45:47 Europe/London. Log:
`/private/tmp/01044-sql-generations-postgres.log`. It includes 17 real TLS/privilege/
migration cases, three real PostgreSQL/JPA transaction and lock-race tests,
49 binding cases and 19 Coolify SQL cases. These runs overlap and are not additive.
The full Platform migration chain through V200 succeeded in the isolated Docker
container, including V198 and the generation table's actual foreign key.

The final expanded integrated focused run passed **245 tests across 14 classes,
zero failures, zero errors, zero skipped**, completed 2026-10-19 01:50:01
Europe/London. Its log is `/private/tmp/01044-sql-generations-integrated.log`;
reports are under `/private/tmp/01044-sql-sidecar/target/surefire-reports`.
It compiled all 1,228 current backend main-source files, then ran 57 binding,
19 Coolify SQL, 17 real PostgreSQL/TLS/migration, three real PostgreSQL/JPA
transaction/race, 37 general Coolify provider, 26 apply execution, 18 recovery,
three topology, three replacement-checkpoint, three generation-service, seven
admission-readback, 20 retirement-inventory, 21 retirement-resource and 11
preserved-kind cases. Earlier run totals overlap this run and are not additive.

The promotion regression verifies that another target's release does not block
this target's first version approval. An unexplained deployment-wide active
pointer with no release history still fails closed; a normal staging-to-
production promotion is not mistaken for reauthorizing an already applied
production version.

This is full main-source compilation plus focused tests, **not the full Spring
Boot application-profile gate**. The PostgreSQL transaction fixture uses the
actual JPA repositories, secret service, V198/V200 DDL and PostgreSQL row locks;
its target-retirement gate and remote SQL privilege probe are mocked. Separate
real TLS/privilege/migration tests cover the SQL probe and an already populated
schema pair. No mocked provider-stop test is claimed as hosted stop evidence.
The parent remains responsible for its merged full Spring Boot profile,
customer-wrapper/API/UI and hosted acceptance gates.

### Actual H2 Application-Profile Verification

After the requested target bump, `DeploymentAiStateStorageH2ProfileTest` started
the real `PlatformBackendApplication` with the actual `test` profile, normal
repository/service wiring and `spring.jpa.hibernate.ddl-auto=validate`. Flyway
successfully applied all 147 H2-compatible migrations through `120.25`. Both
tests passed, **zero failures/errors/skips**, completed 2026-10-19 01:53:01
Europe/London. The second case confirms a generation cannot exist without its
immutable version approval; the first also checks release snapshot columns and
the binding service's actual transactional proxy.

Log: `/private/tmp/01044-sql-generations-h2-profile.log`. These two tests are
separate from the 245-case integrated run above. This closes the SQL H2
application-context/schema smoke gate, not every customer/authorization/UI or
hosted integration scenario. Coolify writes are frozen for the independent
application/database-ownership worker; no further SQL semantics or migration
changes are planned by this task.

## Explicit Open Gaps

1. **Emergency hot rotation and schema reassignment are not implemented.** The
   supported path is new never-applied candidate generation plus normal
   sequential apply, not mutation of an existing release. If old credentials
   were already revoked and its runtime cannot drain, normal replacement may
   block. Recover old connectivity or use a separately proved recovery flow;
   neither a browser quiescence flag nor destructive retirement is a bypass.
2. Parent-owned customer wrapper/API and entitlement/replay qualification are
   recorded separately in010.44. The final merged actual-module backend run now
   passes3,343 tests across350 classes without failures/errors/skips. Customer
   UI functional acceptance and every hosted canary remain open; this does not
   certify the whole product or hosted SQL replacement.
3. The previously reported managed Coolify exact-name adoption/creation-receipt
   issue belongs to the separate generic-ownership workstream. That source has
   changed concurrently; this SQL handoff does not independently close its gate.
4. No live customer PostgreSQL endpoint, hosted schema migration, datastore
   rotation or deployment retirement was exercised. Local Docker verification is
   not evidence of customer network reachability or hosted resource readiness.

For exact request/response and CA requirements, use
[AI-State Storage Contract](AI_STATE_STORAGE_CONTRACT.md).

## Superseded Hot-Rotation Investigation

2026-10-19: inspected the existing target gate/store/worker, runtime drain gateway,
replacement-generation checkpoint and provider action entry points read-only.
There was **no safe production maintenance lease reusable by the SQL delegate**:
retention precedes drain, successful drain advances toward destructive retirement,
cancellation restores ingress, retirement capture is staging-only, and replacement
stop evidence belongs to an in-progress release. Provider lifecycle actions also
need a genuine maintenance interlock. The current credential HMAC contributes
to immutable version/release identity, so a vault overwrite alone would break
that evidence.

That finding concerned replacing a serving version's credential. The owner then
selected a smaller design: append a generation for a different, never-applied
candidate, keep all serving credentials unchanged, then use existing ordinary
sequential replacement. A new maintenance coordinator is **not required for this
staging workflow**. The current contract replaces the earlier broad hook proposal.

Added three no-mutation regressions to `DeploymentAiStateStorageBindingServiceTest`:
username and password changes cannot rewrite vault contents, immutable approvals,
binding metadata or frozen release identity; an existing retirement fence cannot
be repurposed as implicit rotation authorization. Follow-up focused run:
**37 passed, zero failures/errors/skips**, completed 2026-10-19 00:46:51
Europe/London. Log: `/private/tmp/01044-sql-rotation-safety-tests.log`. This is a
focused rerun including the prior binding cases, not 37 additional distinct tests
on top of the accepted 167-test integrated run.

The initial no-overwrite tests remain relevant but now reject changes to an
already approved version with `AI_STATE_STORAGE_VERSION_CREDENTIALS_IMMUTABLE`
or `AI_STATE_STORAGE_APPROVAL_IMMUTABLE`, rather than prohibiting a new candidate.
No parent wrapper, UI, Coolify external-environment region, framework, provider
resource or hosted service was modified by this generation follow-up. Request
and Summary API types are unchanged. Emergency live-secret mutation remains out
of scope; ordinary version-based credential replacement is the implemented path.

## Control-Plane Archive Migration Rehearsal

`ControlPlaneArchiveMigrationIT` is an explicitly selected local test for the
current migration195-to201 upgrade, not a hosted deployment or runtime-state
restore. The normal unit-test pattern does not select this `IT` class.

Prepare the owner-approved custom PostgreSQL18 archives as `staging.dump` and
`production.dump`, each mode0600, in one mode0700 private local directory. No
archive or raw database output belongs in Git, CI artifacts or shared logs.
The exact official PostgreSQL image digest pinned by the test must already be
cached locally; PostgreSQL image pulls and remote Docker transports are forbidden.
The normal Testcontainers helper images must also be available for an offline run.

```sh
RUN_01044_ARCHIVE_MIGRATION=true \
CONTROL_PLANE_ARCHIVE_DIRECTORY=/absolute/private/backups \
mvn -o -f Platfrom/backend/pom.xml -Dtest=ControlPlaneArchiveMigrationIT test
```

The test creates temporary PostgreSQL instances on a fresh Docker bridge with
loopback-only ports. It starts no Spring application, scheduler or worker and
makes no provider/model calls. Restore diagnostics are discarded. It validates
the exact six pending migrations, runs normal Flyway migration validation,
applies196-201, then validates all201 migrations. It does not repair history or
ignore checksum mismatches.

Aggregate SHA256 comparisons cover all original fields in21 customer, identity,
credential, offer, reservation and resource tables. The two new V198 release
columns must remain null on historical rows, and both the customer-resource and
shared-service ownership ledgers must stay empty: migration must not fabricate
ownership for old handles or registry metadata. Containers and
their temporary data are closed through Testcontainers; source archives remain
private and unchanged. Later upgrades require a newly reviewed migration ceiling
and preservation assertions, not blindly reusing this fixed195-to201 acceptance.

The final frozen V201 rehearsal passed on2026-10-19 against both actual approved
archives: **two tests, zero failures/errors/skips**, six exact pending migrations,
201 validated migrations, unchanged preserved records and both new ownership
ledgers empty. Source and compiled V201 SHA256 both equal
`707b3c3e10a560784f9c2e8d31a2cd2e8717ace89be88693067c79e19a8b54ae`.
Log: `/private/tmp/01044-final-archive-migration-201.log`; XML reports are under
`/private/tmp/01044-parent-boundary-tests/target/surefire-reports`.
Earlier rehearsals used earlier migration contents and are historical evidence,
not substitutes for this frozen-source result.

This is migration evidence, not hosted activation or customer runtime-state
recovery. Customer workflow, concurrent revocation and ownership qualification
have their own results in010.44's execution record; hosted acceptance and UI
gates remain separate. Local rehearsal never proves the live control-plane version.
