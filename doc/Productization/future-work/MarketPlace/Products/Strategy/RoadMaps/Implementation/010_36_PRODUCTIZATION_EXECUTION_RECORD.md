# LoomAI Engage Productization Execution Record

Date: 2026-10-15. Governing scope: section 10 of
[010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md).
Source baseline: `5206acd97423ee857b5a60c1fe370633bf3fd5a9`.

## Execution Gates

- P0: source contract implemented and integrated; prerequisite acceptance is in
  progress. The owner supplied the non-secret customer-owned S3 canary contract,
  and separate staging/production credentials are present in the ignored private
  handoff. The live verifier was rerun, but AWS denied the bucket-versioning
  read required to prove the storage posture. The prefix-isolation/read-only
  live gate therefore still needs a passing sanitized evidence record. Do not
  substitute managed storage or claim the storage gate passed from fixture
  tests.
- P1-P5: source foundations are integrated but not hosted-certified. Dependent
  customer provisioning remains gated on live storage, identity, notification,
  immutable dependency artifacts, measured capacity and fresh-customer
  lifecycle evidence.
- P6: not started. No editor extraction or customer layout change before the
  verified functional baseline. Preserve the frozen STAFF-001 through STAFF-039
  capability register and source snapshot.
- P7: not started. Fresh production-customer lifecycle certification is required.

## Initial Readback

Staging Coolify readback found 33 applications and five databases. SSH to
staging and the direct production Coolify API were blocked from this network.
Existing shell-only capacity evidence is not admission certification for the
complete dealership topology. No resources were removed or redeployed.

## Safety Prerequisites

- Customer scope must resolve an active persisted owner and active persisted
  customer, or return `403`. A missing customer scope cannot become a global
  customer-list grant. Cross-customer and blank-owner deployment access remain
  denied.
- Existing-runtime administration must use explicit deployment, target and
  release identity. A missing or inconsistent provider URL/resource record is a
  `409`, never a deployment-wide URL fallback.
- Consumer binding mutations need database concurrency control and a persisted
  operation identity. Direct rebind, promotion and rollback share that contract;
  no wizard-only guard.

## Verification Ledger

### Source Safety Progress

- Active persisted customer-owner checks deny absent, disabled and foreign scope.
- Explicit target/release administration validates candidate membership, actual
  runtime application/server evidence and release-owned endpoints. Document
  operations no longer route through the deployment's mutable latest URL.
- Consumer assignment commands now have a row-locked binding revision,
  expected-revision requirement, persisted operation fingerprint/result, and
  complete before/after release/target history. Shopify internal callers and
  verification clients use the same concurrency contract.
- The reviewed `slot:document-storage` resolves exactly one owned configured
  binding per selected target. Releases freeze the safe configuration and
  credential revisions; drift, missing evidence and cross-target references
  block administration/verification. A staging lineage can configure production
  S3 without weakening mounted-production denial.
- Practical promotion selects the consumer's exact bound staging release, not
  latest verified. Its candidate includes immutable image/config identity,
  verification identity and target/storage fingerprints. A configured evidence
  age is mandatory. Whole-command replay preserves the original result without
  repeating lifecycle updates. No client source-artifact override is accepted.
- Shared vector provisioning derives distinct customer/tenant/deployment/target
  scopes. Registry updates no longer detach another target's active scope.
- R3 target-only retirement, R5 dedicated dependency/operator lifecycle and R7
  atomic admission/expiry ledgers exist as integrated primitives. The apply path
  now derives the release-bound runtime topology, coordinates connector/vector
  runner and managed-dependency quiescence, and fails closed when required
  readback evidence is absent. Hosted initialization, notice delivery, capacity
  and recovery evidence remain required; source integration is not live proof.
- Apply, release-bound consumer rebind and document-binding mutation now join
  the durable target-retirement fence. Existing staff UI has only functional
  contract wiring; no P6 layout/editor extraction has been performed.
- Target-owned authority generation, immutable release snapshots, credential
  revision checks and explicit-key assertion signing are consumed by the
  productized Coolify provisioning and runtime-generation administration path.
  Missing or drifted authority fails closed. This is still not hosted proof that
  every live target uses isolated current keys.
- Invited owners have an explicit `CONFIGURATION_ONLY` bootstrap command. It
  locks the active entitlement, validates reviewed composition/name settings,
  reuses the template factory, and commits the association/receipt atomically.
  Retries do not duplicate drafts. It does not seed automatic vectorization,
  publish a version, queue an apply or create provider resources.
- Runtime retirement has a concrete private command/status/snapshot API with
  real observations, owned continuation and durable ingress/worker fences. The
  unused competing proposed runtime endpoint DTO was removed. Unknown framework
  or remote-worker state continues to block reclamation.
- Document UI mutations capture their original deployment/target/release and
  invalidate only that scope after delayed completion. Target navigation does
  not pretend to cancel a server operation or display its result on a new scope.
- Customer self-service exposes only the reviewed S3 document-binding slot. It
  authorizes deployment/tenant/target ownership server-side, stores credentials
  through the existing write-only binding boundary, returns redacted binding
  evidence, provides idempotent bind/revoke commands and never deletes source
  objects.
- Same-target runtime replacement now uses an explicit private generation
  transition. It drains and fences the old release, requires authoritative
  remote-dependency and provider-stop readback, durably checkpoints the
  `FENCED -> STOP_CONFIRMED -> ADOPTED` phases, adopts the exact new release and
  resumes dependencies from the persisted prior-generation identity. The old
  release handle remains authoritative until adoption; provider UUID rebinding,
  missing prior identity and restart drift fail closed.
- Protected capabilities now cross the runtime/widget boundary through an exact
  typed contract. The runtime translates only the framework
  `ai-fabric-authentication-requirement-v1` record for an anonymous caller into
  `loomai-workspace-authentication-requirement-v1`; the widget rejects naked
  error codes and prose, emits the typed host event, opens mixed-mode sign-in
  only when the host advertises it, and never replays the blocked operation.
- The reviewed fictional dealership customer package is now represented by four
  published Marketplace versions: `mkp-data-simulated-dealership-stock-v1`,
  `mkp-action-simulated-dealership-discovery-v1`,
  `mkp-action-dealership-engagement-v1` and
  `mkp-template-simulated-dealership-experience-v1`, all at `1.0.0`. The
  template composes deployment-local synchronized stock, customer-owned S3
  documents, governed engagement writes, conversation handover and Basic Inbox.
  It deliberately leaves customer document storage unresolved. The reserved
  real Auto Trader package IDs remain unpublished.
- Managed dependency release planning can bind a reviewed deployment-target
  authority secret independently of service-owned credentials. Managed action
  routes compile reviewed service URL/path and secret-header bindings, validate
  HTTP methods and normalize bounded success-status contracts.

### Executed Checks

- Initial clean sequential backend suite: 1,025 tests passed, zero failures,
  errors or skips. This predates the expanded R3/R5/R6/R7 source.
- Consumer concurrency on PostgreSQL 16: seven tests passed.
- Whole-promotion-command H2/PostgreSQL concurrency/replay checks passed in the
  scoped run. Document-slot and promotion unit checks passed.
- First expanded backend run: 1,234 tests, one assertion failure and three
  errors. The failure was a stale two-argument vector-summary mock. The errors
  stemmed from verifying a transactional mock through its Spring proxy and
  contaminating subsequent Mockito state. The fixtures were corrected; the
  scoped repair run passed. The later clean full baselines below supersede this
  intermediate result.
- Independent agent checks: R3 34, R5 35, R7 68 tests passed, including
  PostgreSQL tests. These do not substitute for the integrated final suite.
- R6 independent authorization checks: 58 passed; authority checks: 30 passed.
  Customer-bootstrap checks: 43 passed (26 unit, 17 H2 persistence). Template
  creation is mocked in the latter; locks, ownership, pins, rollback, replay,
  associations and audit persistence are real.
- Parent Maven runtime verification: 85 passed, covering the 22 retirement tests
  and chat ownership/auth, prompt preview, suggestions and public sessions.
  No public framework source or dependency version was changed.
- Parent browser race smoke: ten delayed-response scenarios passed with the real
  Document Knowledge page/API client and local HTTP fixtures. This is not live
  provider verification. UI TypeScript/build checks and the current parent
  production build passed, with existing runtime-config/bundle-size warnings.
- The new real-template bootstrap test initially asserted the wrong display
  value (`null` instead of `draft`); persisted active-version identity was empty.
  Its first integrated placement also reused a shared H2 fixture and duplicated
  document-plugin seeds. It now uses a dedicated test database. The failed full
  run was stopped; the clean corrected full backend run passed 1,301 tests in
  211 suites, zero failures, errors or skips.
- After integrating authority snapshots, dependency evidence, document binding
  and runtime generation, two production-schema PostgreSQL tests exposed stale
  hard-coded Flyway target `167`. They now use the latest production migration,
  while ordinary H2 tests retain the bounded test-migration target `120.17`.
  The final clean backend run passed **1,353 tests**, with zero failures, errors
  or skips. PostgreSQL contexts applied all **169** production migrations.
- The deployment-local runtime run passed **300 tests**, with zero failures,
  errors or skips. Runtime migrations applied through `V13`, including the
  private generation-transition state.
- After adding the protected-capability bridge, the complete deployment-local
  runtime suite passed again: **305 tests**, zero failures, errors or skips. The
  widget passed TypeScript, all three production
  bundles and all five contract smokes. The standalone shell passed its unit
  check and **20 browser scenarios**, including unsolicited/replayed callback
  rejection, cancelled mixed-mode login with no request execution, owner-change
  fencing, bounded logout and revoked/expired provider-session cleanup.
- The built installer also passed a fresh browser-level private-adapter
  regression. It fetched the typed Shopify-style adapter contract with the host
  session cookie, mounted the selected pack, sent a query through the returned
  private route with only the bounded opaque adapter header, and exposed no
  runtime bearer token. This closes the local shared-installer regression; a
  hosted storefront query/action run is still required.
- The S3 verifier's offline suite passed **13 tests**, covering exact native-AWS
  endpoint policy, separate principals, prefix isolation, bounded UTF-8 fixture
  reads, unversioned state, non-mutating write/delete denial and sanitized
  evidence, including rejection of credential files with group/other access.
  These tests validate the verifier, not the live bucket identities.
- AI Fabric `0.8.33` was released from framework commit `b1a0ac2c`, tag
  `ai-fabric-framework-v0.8.33`. Its Maven Central workflow completed
  successfully and Central serves the BOM, core, execution, starter, source and
  Javadoc artifacts. A brand-new Maven repository resolved only Central
  artifacts: the minimal consumer passed **2 tests** and the agentic execution
  consumer passed **5 tests**.
- All active private consumers now select `0.8.33`: deployment runtime/
  connector/relay, vectorization/embedding products, Basic Inbox, Platform's
  advertised framework default and the runtime capability manifest. Clean
  packaging passed for every private reactor. Runtime/connector packaged JARs,
  Basic Inbox and the embedding worker contain only `0.8.33` AI Fabric
  libraries; no `0.8.30`, `0.8.31` or `0.8.32` framework JAR remains in those
  artifacts.
- The production runtime Dockerfile built successfully as local image
  `loomai-runtime-01036:0.8.33` (`sha256:4f2a23bec9af...`). Direct extraction
  from that image showed only `0.8.33` framework libraries in `runtime.jar` and
  `connector.jar`; the private vectorization runner remains a thin JAR and does
  not carry a second framework version. This local image is packaging evidence,
  not a hosted release or provider digest.
- The final clean private runtime reactor passed with **314 runtime tests** and
  **35 relay tests**; generic connector, runtime and relay modules all built.
  The product reactor passed its vectorization suites and all four product
  modules built. Dealership backend passed **32 tests**, Basic Inbox **7**, and
  the external vehicle-provider simulator **8**.
- The final clean Platform backend run passed **1,449 tests**, zero failures,
  errors or skips. Its first run exposed a stale test-only Flyway ceiling
  (`120.17`) below the current managed-dependency test schema (`120.21`); the
  integration fixture now applies the complete test migration set. Production
  migration/runtime behavior was not changed to make that test pass.
- The widget passed TypeScript, all three production bundles and all seven
  contract smokes, including structured authentication, credential broker and
  browser-level private adapter behavior. The standalone shell again passed
  its server test and all **20** browser scenarios after the framework upgrade.
- The latest immutable dependency-image workflow completed successfully from
  source commit `fda8085d0c345e62f750f9041b8fd0760598651c`. It published the simulator
  at digest `sha256:6db239c5c4663dee79719e9716ff6429e1f76fbcfd4efc662d5d39e577e52725`,
  dealership receiver at digest
  `sha256:84ab2be0c963671d67db1791dfb75187124f059adad1831f6e4985c20b46aa0d`,
  and Basic Inbox at digest
  `sha256:10f830dc1e4b1bb5e24722c663b40dac83b07793ab944f3978ac8a6c21e23788`.
  All three registry indexes were read back directly. Workflow evidence is
  GitHub Actions run `38723313627`. Reviewed Platform registration and hosted
  topology publication remain separate gates.
- The simulated dealership package migration was applied from an empty database
  through all **174 production migrations** on PostgreSQL 16. Its four manifests
  parsed successfully; discovery and engagement routes compiled with their exact
  deployment-local projection, managed URL/header and normalized success-status
  shapes. Focused Marketplace and managed-dependency tests and backend packaging
  passed. This is source and migration evidence, not hosted provisioning proof.
- Source artifacts now carry an explicit `RUNTIME` or `MANAGED_DEPENDENCY` role.
  Runtime releases and behavior-readiness checks reject dependency images;
  managed topology publication and hosted dependency prerequisites reject
  runtime images. Dependency images do not fabricate runtime capability
  manifests. A production-promoted dependency image is accepted in customer
  staging and production, while staging-only provenance remains blocked from
  production. Migration `V175` backfills all historical artifacts as runtime.
- The reviewed simulated-dealership topology is encoded in a convergent
  publication verifier. It pins the three dependency images above, PostgreSQL
  16 by immutable digest, per-target simulator/receiver/Inbox resources, the
  exact 14 connector bindings, including the target-scoped Inbox binding, and
  distinct target-authority versus Inbox-owned
  callback secrets. Existing publication is read back and compared rather than
  replaced. The combined verifier suite passed **16 tests** with no failures.
- Managed runtime binding projection fails closed if a target-owned secret is
  missing or no longer present. Services that require runtime callback hosts
  now wait for the runtime-bound reconciliation phase instead of trying to
  start during foundation provisioning with an empty host allowlist.
- The final integrated Platform backend gate passed **1,476 tests**, with zero
  failures, errors or skips. PostgreSQL contexts applied all production
  migrations through `V176`, including explicit source-artifact roles and the
  target-scoped Basic Inbox binding. The managed package verifier passed all
  **16** scenarios, and the exact three-service Java topology contract passed.
- `Platfrom/ui` remains unchanged. P6 editor extraction and customer-layout
  composition have not started.
- Frozen staff baseline snapshot remains unchanged. Source navigation comparison
  is not browser parity certification. `git diff --check` passed.

Logs reside under `/private/tmp/01036-*`; copy final sanitized evidence to the
verification-support directory after the integrated gate passes. Source baseline
started at `5206acd97423ee857b5a60c1fe370633bf3fd5a9`; verified source foundations
are committed and pushed on `Platform-V11` through `c9942fc85`. That branch is
not yet a hosted release and has not passed the remaining live safety gates. No
live deployment, provider resource or assignment was changed by these checks.

### Remaining Before Admission

Run the live customer-owned S3 isolation verifier after the private credentials
are supplied, then bind and index the exact staging and production prefixes.
Verify immutable dependency artifacts, actual staging/production placement,
resource envelopes, notification/recovery evidence, repeated same-target runtime
replacement, restart recovery, S3 indexing and cross-target denial. Complete the
hosted real-provider Engage action-time auth, Shopify private-adapter,
accessibility and capacity gates. The local typed-contract, cancellation,
revocation and no-replay acceptance is closed. Redeploy the hosted Platform and
customer runtime from the `0.8.33`-aligned private source and prove live version/
digest readback before counting that upgrade as hosted evidence. P6 extraction and P7
fresh-customer hosted certification remain later gates. The canonical 40-task
checklist stays open.

### Runtime Generation Source Resolution And Hosted Gate

The runtime drain singleton still correctly pins deployment/tenant/target/release
and topology; it is not cleared or overwritten from environment configuration.
The source implementation now performs an explicitly authorized generation
transition tied to the target authority snapshot, remote dependency quiescence,
provider stop/readback and a durable release checkpoint. A restarted worker must
refresh stop evidence before adoption, and the next generation must adopt an
epoch greater than the fenced old generation. Provisional resource discovery no
longer advances the durable old-release handle before this transition completes.

The former code blocker is therefore resolved. Before enabling the offer, prove
the same behavior on hosted Coolify with repeated same-target releases, a restart
between each checkpoint phase, preserved sessions/evidence, rejected old workers,
and authoritative connector/vector-runner/inbox readbacks. Enabled indexing,
recovery and Quartz workers remain part of that live topology proof; S3 is not
the only open gate.

### Owner S3 Inputs

The first live verifier run on 2026-10-14 failed closed before object
inspection with sanitized code `ACCESS_DENIED`. Neither prefix-scoped identity
can currently call the read-only bucket configuration operation
`s3:GetBucketVersioning`. The verifier therefore cannot independently prove
that versioning remains disabled before its non-mutating write/delete denial
probes. Do not weaken or bypass this gate. Grant both canary identities
`s3:GetBucketVersioning` on the exact bucket ARN only, retain all existing
prefix/object restrictions, and rerun the live verifier. No object or bucket
mutation occurred during the failed run.

The reviewed native AWS canary contract now uses a private bucket in
`eu-north-1`, endpoint `https://s3.eu-north-1.amazonaws.com`, exact prefixes
`dealership/staging/` and `dealership/production/`, separate prefix-limited
identities, Block Public Access, SSE-S3, HTTPS-only access, and explicitly
disabled versioning. Each prefix contains three fictional UTF-8 policy files.
The non-secret bucket identifier is captured in the verifier contract; all
credential values remain outside this tracked ledger.

As of 2026-10-15, the four canary credential values are present in the ignored
root private handoff and were consumed without printing or copying them into
tracked files. The live verifier was rerun and failed at the bucket-versioning
read described above. Sanitized evidence is recorded at
`verification-support/productization-01036/evidence/s3-canary-2026-10-15T01-46-39-446Z.json`.
The next run requires only the narrowly scoped bucket permission correction;
credential values must remain private and must never be copied into this ledger
or a deployment-readable public manifest.

`verification-support/productization-01036/verify-s3-canary.mjs` validates the
exact non-secret contract, actual unversioned state, separate principals,
bounded list/head/get access, UTF-8 content fingerprints, cross-prefix denial,
and non-mutating write/delete denial probes. Generated evidence is ignored and
contains no principals, keys, object names, object contents, ETags or raw AWS
errors. Fixture tests prove pass and fail-closed behavior but are not live AWS
evidence. Secret values belong only in an ignored private source and managed
write-only bindings, never this ledger.
