# LoomAI Engage Productization Execution Record

Date: 2026-10-16. Governing scope: section 10 of
[010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md).
Source baseline: `5206acd97423ee857b5a60c1fe370633bf3fd5a9`.

## Execution Gates

Latest hosted readback, 2026-10-18: backend source `8432ec735` and migration193
are verified on both control planes, with public healthUP and exact OCI revision/
RepoDigest. Backend digest:
`sha256:a668651d6daac04336f1fff71bd20008a97586914b46b677433b95421769661e`.
Rollouts: staging `ql6letv36y5212g4fnhdudzz`, production `gzilo152u7s2rcjx5g5tedfj`.
Fresh pre-fix backups both passed isolated local PostgreSQL18 restores through
their actual migration193; reports `...restore-l4lMMDmKgXts/report.json` and
`...restore-YGALPNohTkT1/report.json` under `/private/tmp/01036-local-control-plane-`.
These are control-plane backup proofs, not customer-runtime/topology recovery.

Runtime CI39164527840 succeeded. Exact image
`sha256:3809a6585ad19f25c0139d60cf1b9aa742b7831a6213fcec4bdfe6fb52e0b140`
was inspected in an unstarted network-disabled local container, which was then
removed. All20 framework0.8.36 jars match Central byte-for-byte; packaged manifest
equals committed source and includes native-token plus all provider-budget guards
and V19/private accounting libraries. Evidence:
`/private/tmp/01036-native-token-image-proof-S9qoQ6dRZFFj/evidence.json`.
Artifact `dsa-94841998` / normalized manifest hash
`abbe7561d843ef3c111bbb2702f012227e432cb6c8a2f8d6186200bd134982d6`
is registered/promoted on staging. This is not an applied customer runtime.
Offer v11/spec1.8 preparation passes27 synthetic tests; graph/resources/BasicInbox
images are unchanged. Spec identity changes because lookup is template-version
scoped, not because dependency code was rebuilt. No credit allocation/provider
writes or production customer promotion occurred. UI extraction remains last.

Staging spec1.8 and offer v11 are now published. The real CUSTOMER_ADMIN
configuration-only canary passed9 checks for `dep-6e29bab9` / `ver-3c0e3b88`,
config hash `c54fc56cfd5060c625ec1f0997df4059bf9a643e7a16311e0651062a658a75cc`.
Installation `awi_pub_10aefdad2b85e09d0a2bca3588298c4b` remains inactive; no
runtime exists. Configuration evidence `/private/tmp/01036-v11-configuration-evidence.json`.
Formal TOKEN binding to approved Free-01 was created and local scoped credentials
are READY. Six read-only checks prove anonymous/customer denial, exact staging
readiness, no production/unscoped reuse and unchanged zero grant allocation:
`/private/tmp/01036-v11-binding-access-and-scope-20261018.json`.

The exact bind replay now passes on the deployed precision fix, with the same
binding identity/fingerprint, credentials and persisted timestamp. The first
bind occurred before the fix; new-create precision is covered by89 service and3
real PostgreSQL tests. Hosted evidence:
`/private/tmp/01036-v11-free01-binding-evidence-20261018.json`. Nothing is
ignored, refreshed or rewritten to conceal the original mismatch.

Physical policy `01036-staging-v11-measured-20261018` and the one-candidate
customer quota are published on staging only. A fresh25-sample baseline and
the [attended operating review](../../../../../../../../verification-support/productization-01036/STAGING_V11_ATTENDED_CANARY_OPERATING_REVIEW.md)
support this bounded measurement, not production fit. Real customer reservation
`f0e90dba-14f0-4787-b583-7dcf712bc4b2` is RESERVED and exact replay passes.
Its whole envelope remains charged. Normal customer apply was correctly denied:
the immutable template requires `GOVERNED_RESOLVER`, which the runtime does not
advertise. Full template/runtime contract investigation is in progress; the gate
is not bypassed and ordinary confirmed actions are not treated as proof of the
distinct proposal/receipt extension. Read-only reconciliation proves zero
releases, apply commands, release bindings and provider allocations. Sole
financial grant remains0allocated/50requests/200ktokens/USD12remaining.
Evidence: `/private/tmp/01036-v11-staging-admission-20261018.json` and
`/private/tmp/01036-v11-staging-apply-20261018.json`.

Scoped managed-dependency credential projection is implemented locally and
parent-verified:185tests/0fail/0skip. It requires exact persisted version/target,
customer/tenant, reviewed spec, provisioned node revision and present managed
secret metadata; pending evidence stays MISSING and global-name collisions
cannot satisfy it. This is local credential-presence evidence, not provider
health or runtime initialization. No rollout yet; UI extraction stays last.

Local template correction: V194 publishes immutable1.9, changing only the
version and allowed/default execution-extension declarations. Governed Resolver
means separate proposal/confirm/receipt APIs; ordinary in-chat confirmed actions
do not implement it. Action/plugin contributions, protected-action behavior,
handover and the full dependency graph remain unchanged. Spec1.9/offer v12 keep
the exact inspected d2b1 runtime image/framework0.8.36; no rebuild or framework
release is needed. Publication support now rejects unsupported template
behavior/capabilities/extensions/channels/verification packs before metadata
effects. The real PostgreSQL migration gate passes3checks, including exact old
row preservation, full release-requirements comparison against the actual runtime
manifest, document/integration requirements and old1.8 rejection. Full support
gate87PASS/0skip includes the enabled synthetic Docker restore. One first guard
test expected the later of two fields sharing the same missing support array;
its error-order expectation was corrected without changing production behavior.
The correction is not deployed or published yet. A generic durable no-effect
reservation cancellation and its apply-race/binding-history proof are being
implemented separately; no direct database release or provider mutation.

Independent contract review requested full catalog-derived extension requirements
in the publication helper and exact pinned engagement-action assertions. The
PostgreSQL regression now resolves the template's actual engagement version,
proving protected test-drive confirmation/authentication and callback confirmation/
anonymous access;3PASS/0skip. Related composition/version/apply/manifest/behavior
regressions30PASS/0skip. Helper hardening is now parent-reviewed and verified:
mandatory backend behavior/extension catalogs supply complete capability,
endpoint, migration, verification-pack and specialist-bundle requirements.
No extension-name heuristic or synthetic requirements-derived artifact is used.
Final support103PASS/0skip, including the enabled synthetic Docker restore.
Read-only retained-stop verification again passes all30 original workloads.

The owner's dedicated operational chat reports creation of the separately
approved Frankfurt production Serverless cluster `in05-58b569513f0fc1c` in
`proj-807f74adeb59398bfc3d2b`, and owner-authorized removal of eight old
non-Free clusters from Default Project. It reports both intended clusters
RUNNING: that production cluster and existing staging Free-01. Private provider
receipts are recorded by that chat; no parent duplicate provider mutation was
performed. Parent read-only metadata and whitelisted vector-endpoint environment
audit found no references to those eight IDs across72 containers on both hosts;
this is not an exhaustive arbitrary-file/external-client audit. Evidence:
`/private/tmp/01036-old-zilliz-reference-audit-20261018.json`. An explicit future
apply of an old managed-mode configuration can still create a missing cluster;
that path has not been disabled, and paused auto-deploy is not a permanent
provider-level prohibition. Current external-existing canary does not use it.
No production model/embedding allowance or customer promotion follows from the
separate provider-resource approval. A requested cross-chat findings relay was
blocked by permission review; explicit sending permission is requested, with
unaffected implementation continuing.

- P0: source contract implemented and integrated; prerequisite acceptance is in
  progress. The customer-owned S3 canary passes exact bucket posture, explicit
  unversioned state, identity separation, prefix isolation, bounded object reads
  and write/delete denial. The staging S3 binding is persisted for the fresh v5
  customer lifecycle. SMTP authentication is live and the admission notice
  transport is explicitly configured on staging. Fresh guest measurements prove
  a prior capacity shortfall. Authorized retained workload stops have since
  cleared the staging RAM minimum. The measured staging policy is now
  published; workload and complete recovery acceptance remain open. Short-window baselines after the
  pauses show lower current CPU load on both hosts, but do not certify the
  unchanged topology under workload/startup/promotion overlap. Production fit
  remains unapproved. No policy or grant was fabricated to bypass that gate.
- P1-P5: source foundations are integrated. The immutable three-service managed
  dependency package is published and read back on both hosted control planes.
  Customer admission no longer accepts a manifest identity or capacity material:
  the backend derives an immutable manifest from the exact offer, version,
  target storage, runtime authority and reviewed managed dependency topology.
  Backend `8432ec735` is live on both control planes at migration193;
  native token binding and its exact replay pass. Template1.8 is seeded on both;
  spec1.8/offer v11 are published on staging only and pin inspected runtime
  `d2b1a9792`/framework0.8.36. Real customer capacity reservation now passes.
  Apply is denied before release creation because the template requests the
  unsupported distinct governed-resolver extension. Ordinary confirmed chat
  actions are not proof of that extension's APIs. The contract is being
  corrected without bypassing the artifact gate. The reservation remains
  charged until an explicit no-effect cancellation can prove safe release.
- P6: local UI implementation and fixture-based checks are in progress, but the
  extraction has not been committed or deployed. Hosted rollout remains gated
  on the verified functional baseline. Preserve the frozen STAFF-001 through
  STAFF-039 capability register and source snapshot; keep the UI-only rollback
  independent from backend and runtime corrections.
- P7: not started. Fresh production-customer lifecycle certification is required
  after the external capacity prerequisite is closed.

### Independent Work While Hosted Vector Integration Is Pending

Native-token integration and formal staging binding now pass; the template
contract mismatch above blocks indexing and hosted lifecycle acceptance. Owner permission
to use the existing broader key for Free-01 is resolved; do not ask again for
another credential or allocation approval. This dependency does not block
all implementation or verification. Continue independent tasks without weakening
their exit criteria or starting P6 early:

- Host baseline: reusable bounded read-only sampler committed `bdae9f3b7`.
  Four focused tests and the final combined 77-check support gate passed. Fresh
  25-sample/120-second staging and production observations passed at
  19:37-19:39 UTC. Raw private reports are
  `/private/tmp/01036-independent-staging-host-baseline-20261018.json` and
  `/private/tmp/01036-independent-production-host-baseline-20261018.json`.
  These are current-fleet baselines, not workload certification or approval.
- Control-plane backup recovery: committed `e4f8b6d53`; BOTH actual private
  pre-V192 archives restored on local isolated PostgreSQL18. Each passed
  120 public tables, 37 selected typed columns, counts across15core tables,
  191 successful migrations, constraint/index validity, unchanged archive hash
  and exact owned-fixture cleanup. Reports:
  `/private/tmp/01036-local-control-plane-restore-kCMCy4RB3Cya/report.json`
  (staging) and `/private/tmp/01036-local-control-plane-restore-HNJhpHp3jH1j/report.json`
  (production). Official image was cached separately at immutable digest;
  verifier remains no-pull/network-none/no-ports/no-backend/no-workers.
  This is not V192/current-head recovery, hosted recovery, key availability,
  owner/ACL parity or application bootstrap. An initial missing-image failure
  is preserved; the matching image prerequisite was resolved, not bypassed.
- P4 authentication: committed `143070ddb`; nine actual-module browser-fixture
  scenarios passed independently in the parent, zero skips, including delayed
  login cancellation, logout/account switching, renewal, expiry and no replay.
  Evidence `/private/var/folders/vb/pk83dvvx58v6dpw66mcsn9sr0000gn/T/01036-p4-auth-hj27hsdt4nxY/report.json`.
  Real runtime protected-capability selection/effects, real-provider cancellation
  and bearer revocation latency remain open. P4 is not labelled complete.
- Retained-index lifecycle: source review completed. Existing runtime readback
  can supply actual nonzero counts without clearing, but R3 COMPLETE/R7 release
  would incorrectly release the whole reservation. A distinct reviewed retained
  outcome, durable receipt and charged-retention accounting are required.
  Partial CPU/RAM/dependency reclamation needs its own ledger transition.
  No framework endpoint is structurally required. Existing blocker remains;
  this review is not authorization to invent new accounting semantics.

Final parent support gate with the isolated synthetic Docker test explicitly
enabled:77PASS/0fail/0error/0skip. Browser gate separately9PASS/0skip. Actual
private PostgreSQL18 archive restores are separate from the syntheticPG16 test.

External auth update, 2026-10-18: owner explicitly permits the existing broader
API key for this exact Free-01 synthetic staging campaign. Cluster-only provider
ACLs are not claimed, but another restricted key/password is not a campaign
prerequisite. Native SDK2.4.1 metadata authentication passed with zero collections,
zero writes and zero model calls. Public native-token code `218b7b48` and release
`0808b2d5` / tag `ai-fabric-framework-v0.8.36` are pushed; Central workflow
`39162425040` remains in progress at this checkpoint. Private binding/projection,
V193 immutable template 1.8 and pre-effect artifact gates are under integration
verification. No published image, actual binding or hosted runtime acceptance
is claimed. Never disguise a token as a password, use an implicit global-key
fallback, mutate provider users/roles/keys, or reset the sole allowance.

The first parent integrated run executed 413 tests and exposed three stale test
expectations: explicitly blank authentication modes must fail closed, not select
username/password. Corrected focused binding gate passed 61 tests, zero failures,
errors or skips (`/private/tmp/01036-native-token-blank-auth-gate-20261018.log`).
The original failed log is preserved. Independent review also found credential
readiness reading only the unchanged draft instead of the target-owned binding;
that fix and additional approval/admission ordering tests are being implemented
in parallel. The final integrated rerun remains required before rollout.

### Native Token Local Gate Completed

Framework0.8.36 Central publication39162425040 passed. Direct BOM/core/Milvus
HTTP readback returned200. Fresh isolated Maven repository
`/private/tmp/01036-native-token-central-0.8.36-m2` contains41 public artifact
files, all downloaded from Central and only at0.8.36. No public framework was
locally installed. Proof:
`/private/tmp/01036-private-central-artifact-proof-20261018.json`.

Final parent gates, all zero failures/errors/skips:

- Backend476, including real PostgreSQL V193 and API/admission ordering:
  `/private/tmp/01036-native-token-integrated-socket-enabled-gate-20261018.log`.
- Runtime425, provider accounting140, connector76 and vectorization32:
  `/private/tmp/01036-private-{runtime,budget,connector,runner}-central-0.8.36-gate-20261018.log`.
- Runtime/connector/runner packaging passed. Local runtime contains20
  framework0.8.36 jars including core/execution/Milvus and the native-token
  capability. Proof:
  `/private/tmp/01036-private-runtime-local-package-proof-20261018.json`.
  This is not CI-image or deployed-runtime proof.

Local readiness now validates binding integrity, exact version/target/refs and
credential fingerprint without provider probes. Live resolution retains those
probes. The scoped GET requires operator access, owned published version and
exact active target. No guessed scope, draft mutation or global-key fallback.
Unscoped reads and the generic unscoped scorecard remain intentionally not-ready;
selected-scope caller wiring is still required. Do not infer production readiness
from staging credentials. Final independent critical review found no concrete
P1/P2 issue in the reviewed delta.

Earlier failed runs remain recorded. The first full rerun had21 socket errors
and one skipped Docker test; it lacked local permissions and does not count as
green. The successful476 run used the required permissions without weakening
tests. Initial archive inspection assumed BOOT-INF/classes; exact listing found
the manifest at root META-INF/loomai and subsequent inspection passed.

Hosted control planes still select image58bf670de/migration192. No new runtime,
binding, apply/activation, provider mutation, model call, grant reset or production
promotion occurred. Next are exact CI images, a new immutable offer/spec, fresh
backups, V193 rollout, formal binding, measured admission and attended canary.
Retained-index completion and production fit remain open. UI extraction is last.

UI extraction remains last. No additional vector infrastructure, allowance
reset, paid provider traffic or stopped-workload restart is included here.

## Initial Readback

The initial Coolify inventory found 33 staging applications and five databases.
Later authenticated SSH and Coolify API access succeeded for both hosts. The
2026-10-16 staging snapshot reports 8 vCPUs, 16 GB RAM, about 3.58 GiB available
RAM and about 4.67 GiB swap in use. The reviewed dealership topology requires
3.75 vCPUs and 5.75 GiB RAM before fleet baseline and headroom. Production has
4 vCPUs, 16 GB RAM and about 7.87 GiB available RAM, but the topology plus the
previously measured production baseline requires about 4.22 vCPUs before
headroom. These facts block admission; they are not converted into permissive
policy values.

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
- Customer admission now has a customer-safe preparation/reservation/readback
  boundary backed by the existing durable R7 ledger. The customer supplies only
  reviewed target, version/release and operation identity. The server derives
  the manifest and demand from immutable reviewed records; provider placement,
  policy and prerequisite evidence remain staff-owned. Unknown manifest or
  capacity fields, foreign scope, unreviewed versions and staff-origin
  reservations fail closed, and ordinary apply/promotion commands still require
  the resulting admission evidence before provisioning.
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
- Customer plan/apply/activation now share one durable admission allocation and
  scoped authorization receipts. Accepted runtime chat, action and handover
  activity renews eligible staging allocations within the reviewed bounds;
  customer retention and extension commands are explicit and replay-safe.
- Generic customer mutations now require an active reviewed offer for their
  exact operation and target. Target-specific document bindings retain the
  logical reviewed slot while isolating the staging and production physical
  source. The productized dealership action set now keeps anonymous callback
  intake but requires authentication for test-drive creation.
- Coolify application and PostgreSQL capacity limits are projected from the
  reviewed resource envelope and checked against fresh provider readback.
  Productized runtime databases fail closed on missing or drifted admission
  evidence. The obsolete production-hosted staging profile is inactive for new
  placement while historical records remain readable.
- Admission lifecycle and release reconciliation are now separate opt-in
  workers. Both use valid ISO-8601 scheduling defaults and remain disabled
  unless the control-plane instance is explicitly assigned the work, avoiding
  duplicate or unintended lifecycle processing on passive instances.

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
- The live S3 verifier later passed with evidence level
  `LIVE_AWS_READ_AND_NON_MUTATING_PERMISSION_PROBES`. It observed the exact
  `eu-north-1` native endpoint, unversioned state, separate staging/production
  principals, three UTF-8 text objects per prefix, denied cross-prefix
  list/head/get, and denied put/delete with the sampled objects unchanged.
  Sanitized evidence is tracked at
  `verification-support/productization-01036/evidence/s3-canary-2026-10-15T12-51-55-413Z.json`.
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
  replaced. The latest combined verifier suite passed **17 tests** with no
  failures, including representational normalization of server-materialized
  optional `null` fields without ignoring real topology changes.
- Managed runtime binding projection fails closed if a target-owned secret is
  missing or no longer present. Services that require runtime callback hosts
  now wait for the runtime-bound reconciliation phase instead of trying to
  start during foundation provisioning with an empty host allowlist.
- The final integrated Platform backend gate passed **1,476 tests**, with zero
  failures, errors or skips. PostgreSQL contexts applied all production
  migrations through `V176`, including explicit source-artifact roles and the
  target-scoped Basic Inbox binding. The managed package verifier passed all
  **17** scenarios, and the exact three-service Java topology contract passed.
- After adding customer admission reservation and staff-only reviewed
  policy/grant/manifest publication APIs, the focused admission/authorization
  gate passed **63 tests**. A fresh complete Platform backend run then passed
  **1,485 tests**, with zero failures, errors or skips (`BUILD SUCCESS`,
  6m43s). No framework or UI change was required.
- The subsequent P0-P5 source-integration series added promotion-triggered
  staging retirement, exact provider-volume reclamation safeguards, enforced
  Coolify database limits, target-specific document binding retention,
  reviewed-offer authorization, protected dealership test-drive execution and
  durable allocation ownership across release lifecycle. The first broad run
  found one stale unit fixture that omitted the new required admission release
  coordinator; the production fail-closed invariant was preserved and the
  fixture was corrected. The clean final Platform backend run passed **1,551
  tests**, with zero failures, errors or skips (`BUILD SUCCESS`, 6m51s).
- The deployment-local runtime suite passed **319 tests**, with zero failures,
  errors or skips. Runtime Flyway applied through `V15.2`, including accepted
  admission activity. No public AI Fabric source or dependency version changed;
  all work remains on AI Fabric `0.8.33`.
- GitHub Actions run `38736048226` published the reviewed Platform backend from
  exact source `294f771ebadb60b5c8f43d0ea6abf63415b9efff` as
  `ghcr.io/mahmoudashraf/loomai-platform-backend:294f771ebadb60b5c8f43d0ea6abf63415b9efff`,
  digest
  `sha256:468f5fa69bad1b86ec1c9d9ceeaa6b3f15eaf1f974c4dee736dd3fae72863d1a`.
  The workflow's UI job was correctly skipped because this source did not
  change the UI.
- Staging Platform backend app `aa3dylvh4stfeg0ersfzdjjm` replaced the prior
  image with that exact tag through Coolify deployment
  `kmqjlhqspzdwe1nbbfq3ifvu`. The deployment finished, the application reports
  `running:healthy`, and both liveness and readiness return HTTP `200` / `UP`.
  An authenticated core-service readback still resolves the production backend
  and console identities as `RUNNING_HEALTHY`. The customer admission route
  rejects a Platform administrator with HTTP `403`; the staff manifest-review
  route reaches its controller and rejects an empty manifest with HTTP `412` /
  `PRECONDITION_FAILED`. No customer or reviewed admission record was created.
- Production Platform backend app `sbdv2pkkqrbsy9m59034hb5j` then replaced the
  prior image with the same immutable tag through Coolify deployment
  `kni9ojxnk1q3cv85mtfy6qg9`. The deployment finished and the app reports
  `running:healthy`. Canonical `api.loomai.pro` liveness and readiness return
  HTTP `200` / `UP`; authenticated core-service readback returns the exact
  image tag and `RUNNING_HEALTHY`. The same authorization probes return HTTP
  `403` for the customer route under Platform-admin authority and HTTP `412` /
  `PRECONDITION_FAILED` for an empty staff manifest. No business data changed.
- The production package publisher registered and production-promoted the exact
  reviewed simulator, receiver and Basic Inbox dependency images, published
  `mds-simulated-dealership-experience-v1-1` for template version
  `mkv-template-simulated-dealership-experience-v1-1`, and read back the same
  immutable topology. The resulting fingerprint is
  `c16a1ceacab41a4893b3f56e1f1df99d819d16c4d64415966e225fde440011df`.
  Sanitized evidence is recorded at
  `verification-support/productization-01036/evidence/managed-package-publication-2026-10-15T11-54-46-672Z.json`;
  no secret value is present in it.
- The convergent staging package publication/readback produced
  template `mkv-template-simulated-dealership-experience-v1-1`, topology
  `mds-simulated-dealership-experience-v1-1`, all three exact dependency image
  digests and all 14 runtime bindings. The staging topology fingerprint is
  `63c9878e1e4d29a5a65d198b7e6195f810d8a170811e401a233699349fcf9262`.
- Staging's operator readback initially used retired default UUIDs for Platform
  backend and console. Runtime environment bindings now select production
  resources `sbdv2pkkqrbsy9m59034hb5j` and `a6fz2ncuzzulg6lrzzwrjshu`; after
  restart, both resolve as `RUNNING_HEALTHY` with their exact current image
  tags. A later forced same-image deployment overlapped that completed restart
  and failed when its temporary container disappeared before health inspection;
  it did not replace or damage the healthy container and is not counted as a
  successful release.
- Production Coolify port `8000` again times out from the operator network after
  the temporary `/32` allowance was removed. Hetzner firewalls `10915120` and
  `10918233` exactly match their pre-run rule snapshots, with hashes
  `c687cf36b5b6b0a4cba4e3d0d888c71e4839a9064afd23baf9c061094f5c31fd`
  and `88f06be9d7d1127fce6cfda4e008480094d70b2f21e79289f0ccf2d1300bc648`;
  the operator CIDR has zero matches. Production Platform readback remains
  available through its authenticated API and reports backend and console
  `RUNNING_HEALTHY`.
- The final server-derived admission implementation passed the complete Platform
  backend suite: **1,751 tests**, zero failures, errors or skips. Focused tests
  prove deterministic materialization, strict customer request parsing, exact
  managed-dependency fingerprint verification, full-envelope demand checks,
  customer isolation and retention of the staff-managed non-offer path.
  Independent P0-P2 review found no remaining confirmed finding.
- GitHub Actions run `38822580070` published Platform backend source
  `fb68caac3d9305e211f8c41bda015012dbee32f9` as
  `ghcr.io/mahmoudashraf/loomai-platform-backend:fb68caac3d9305e211f8c41bda015012dbee32f9`,
  registry digest
  `sha256:6ffc62b5ddb4bad505e9dd0fb45aa301721d45fd9c6c11c4dd694d3fa9193446`.
  The unchanged UI image job was correctly skipped.
- Staging Coolify deployment `dawwwzz71c8s6v4kn797d9gy` and production
  deployment `tnz5ys408gfi6c9w6sfwjw7p` finished on that exact backend image.
  Both applications report `running:healthy`; aggregate health, liveness and
  readiness return HTTP `200`, and each host has exactly one Platform backend
  container. Staging retained the existing SMTP settings while explicitly
  enabling console notices with transport reference
  `loomai-admission-console-smtp-v1`; the complete production environment
  fingerprint was unchanged.
- The fresh v5 staging customer lifecycle owns deployment `dep-ec6468c8`, target
  `dtp-coolify-staging-behavior`, storage binding `dsh-480e94bb-23e` and
  published version `ver-2dfa400e`. Runtime authority candidate generation is
  deterministic and includes the reviewed AI Workspace origin. Apply currently
  returns `PREREQUISITE_EVIDENCE_REQUIRED` because no admission allocation was
  approved. Direct hosted admission probes reject caller field `manifestId`
  with HTTP `400` / `INVALID_CONTRACT`; the legal server-derived request reaches
  HTTP `412` / `MEASURED_POLICY_REQUIRED`. Neither probe created a reservation or
  provider resource.
- The live AWS verifier reran after the owner confirmed
  `s3:GetBucketVersioning`. It passed both identities, all six expected text
  objects, explicit `UNVERSIONED` state, cross-prefix denial and non-mutating
  write/delete denial. Sanitized ignored evidence is
  `verification-support/productization-01036/evidence/s3-canary-2026-10-15T23-26-47-209Z.json`
  with SHA-256
  `5437c7018d18d458acc7d359d2c11ecfd57971552250556919909f57ab87612e`.
- The authoritative production Shopify deployment was upgraded through its
  normal release lifecycle to version `ver-6fe713d2` (`v30`, AI Fabric
  `0.8.33`) and release `rel-28acbf96`. Release status is
  `APPLIED_VERIFIED`, provisioning is `ACTIVE`, verification is `PASSED`, and
  the bound runtime, connector and vector runner each run immutable runtime
  artifact `dsa-3d324415` from source `980fc9d75bfea761d07808f86af9e0386e90ab66`.
  Consumer `shopify-shopping-companion-test` was atomically rebound to that
  release at revision `1`; the stale staging control-plane record was not
  treated as deployment truth.
- Production AI Workspace installation
  `awi_pub_fad735b62c2c3f7c958282e6ef2c8d1a` is active for the exact Shopify
  origin and bound to release `rel-28acbf96` through the reviewed
  `shopify-storefront-bridge` private-adapter profile. Assignment, assets,
  experience pack, profile, exact origin, HTTPS endpoint and private assertion
  readiness checks all pass.
- The first real browser canary found a contract defect that direct HTTP
  bootstrap checks could not reveal: Platform emitted `credentials: include`
  for every private adapter, while the Shopify bridge uses an opaque header and
  intentionally does not enable credentialed wildcard CORS. The browser blocked
  bootstrap. Source `54c2a36522b9dbcd2a44193cf43e19bd77703b7e` makes browser
  credential mode an explicit connection-profile property; Shopify declares
  `omit`, while a future cookie-backed profile must explicitly declare
  `include`. No wildcard credential policy, hostname matching or permissive
  fallback was added. Focused manifest and installation tests pass.
- GitHub Actions run `38831295518` published the corrected immutable Platform
  backend image. Production Coolify deployment
  `md0geh2v9amhhv0k1yk0n3rt` and staging deployment
  `z1023zouk0trodc50w5js6fo` both finished on exact source
  `54c2a36522b9dbcd2a44193cf43e19bd77703b7e`; each control plane has one healthy
  backend container and aggregate, liveness and readiness return HTTP `200`.
  Production Shopify Bridge reconciliation completed as deployment
  `w1c9dsrh5n4ldth2fbywhdl6`, with one healthy exact-source container, no drift,
  and HTTP `200` on both its managed and canonical health routes.
- The complete Platform backend Maven suite passed on the exact corrected
  source: **1,753 tests**, zero failures, zero errors and zero skipped. A fresh
  post-deployment readback also confirmed exact source
  `54c2a36522b9dbcd2a44193cf43e19bd77703b7e` in the single healthy staging and
  production Platform backend containers and the production Shopify Bridge
  container. Platform aggregate, liveness and readiness routes and both Bridge
  health routes returned HTTP `200` with status `UP`.
- The post-fix production browser canary loaded Platform `install.js` from the
  exact `shopping-companion-test.myshopify.com` origin, mounted the hosted
  workspace and queried through the production bridge/runtime. Reload preserved
  the generated browser owner and conversation, issued no replay, and a
  follow-up continued the same conversation. A separate browser context
  received a different owner and conversation. All three live responses were
  successful `INFORMATION_PROVIDED` results. The canary used controlled page
  HTML at the exact approved origin; it proves browser transport, persistence
  and isolation, not Shopify theme-extension publication. Sanitized evidence is
  tracked at
  `verification-support/productization-01036/evidence/shopify-private-adapter-browser-canary-2026-10-16.json`.
- The supported accessibility/mobile acceptance is closed on hosted staging.
  Generic workspace fixes give the send control an accessible name, preserve
  one installation-specific `h1`, meet compact-label contrast, and expose the
  embedded workspace as a named region without taking ownership of the host
  page's single main landmark. The widget TypeScript/build, viewport and native
  modal/focus/safe-area/keyboard smoke contracts pass; the standalone shell
  passes its server test and all **26 browser scenarios**. Fresh Chromium and
  axe-core scans against both the dealership and generic installations at
  `1440x1000` and `390x844` report **zero violations** across four scans.
  Staging and production now run backend source
  `1ac809d70e095c3d3c4a73cfdf9b9b84f88cff8a`; both shared standalone services
  run source `aebe8dc8135c912458d8249484ca54ae64b2282b`. All four Coolify apps read
  back their exact immutable tags as `running:healthy`; Platform aggregate,
  liveness and readiness are HTTP `200` / `UP` in both environments, and the
  production shared workspace health route returns HTTP `200`. Sanitized
  evidence is tracked at
  `verification-support/productization-01036/evidence/ai-workspace-accessibility-hosted-canary-2026-10-16.json`.
- `Platfrom/ui` remains unchanged. P6 editor extraction and customer-layout
  composition have not started.
- Frozen staff baseline snapshot remains unchanged. Source navigation comparison
  is not browser parity certification. `git diff --check` passed.

Logs reside under `/private/tmp/01036-*`; copy only final sanitized evidence to
the verification-support directory. Source baseline started at
`5206acd97423ee857b5a60c1fe370633bf3fd5a9`; the latest locally and hosted
verified source is committed on `Platform-V11` through
`1ac809d70e095c3d3c4a73cfdf9b9b84f88cff8a`. The reviewed package metadata is
hosted, but the customer offer has not passed the remaining live capacity and
lifecycle gates and is not externally admissible.
No customer deployment, provider resource or consumer assignment was created or
changed by the package-publication check.

### Remaining Before Admission

The staging prefix is bound to the fresh v5 deployment, but indexing cannot run
until its complete runtime topology is admitted. After capacity is added,
publish a fresh measured fleet policy and customer grant, reserve admission,
provision the staging target, and prove indexing, citation retrieval,
refresh/removal and cross-target denial without adding a source upload or write
path. Bind the production prefix only as part of the later production candidate.
Verify immutable dependency artifacts, notification/recovery evidence, repeated
same-target runtime replacement, restart recovery and S3 indexing. Complete the
hosted real-provider Engage action-time authentication and capacity gates.
The Shopify private-adapter browser transport, reload continuity and
cross-browser isolation gate is closed. The local typed-contract, cancellation,
revocation and no-replay acceptance is closed. The hosted Platform backend and
dependency package publication are closed; customer runtime deployment and exact
live version/digest readback remain open on external capacity. P6 extraction and P7
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
could then call the read-only bucket configuration operation
`s3:GetBucketVersioning`. The verifier therefore cannot independently prove
that versioning remains disabled before its non-mutating write/delete denial
probes. The identities were subsequently granted only
`s3:GetBucketVersioning` on the exact bucket ARN while retaining the existing
prefix/object restrictions. No object or bucket mutation occurred during the
failed run.

The reviewed native AWS canary contract now uses a private bucket in
`eu-north-1`, endpoint `https://s3.eu-north-1.amazonaws.com`, exact prefixes
`dealership/staging/` and `dealership/production/`, separate prefix-limited
identities, Block Public Access, SSE-S3, HTTPS-only access, and explicitly
disabled versioning. Each prefix contains three fictional UTF-8 policy files.
The non-secret bucket identifier is captured in the verifier contract; all
credential values remain outside this tracked ledger.

Earlier on 2026-10-15, four canary credential values were consumed without
printing or copying them into tracked files. The live verifier failed at the
bucket-versioning read described above. Sanitized evidence is recorded at
`verification-support/productization-01036/evidence/s3-canary-2026-10-15T01-46-39-446Z.json`.
The owner-only private auth handoff contains the corrected separate identities.
The rerun passed at `2026-10-15T12:51:55.413Z`; its tracked sanitized evidence
is the passing record linked above. Credential values remain private and were
not copied into this ledger, process output, or a deployment-readable public
manifest.

`verification-support/productization-01036/verify-s3-canary.mjs` validates the
exact non-secret contract, actual unversioned state, separate principals,
bounded list/head/get access, UTF-8 content fingerprints, cross-prefix denial,
and non-mutating write/delete denial probes. Generated evidence is ignored and
contains no principals, keys, object names, object contents, ETags or raw AWS
errors. Fixture tests prove pass and fail-closed behavior but are not live AWS
evidence. Secret values belong only in an ignored private source and managed
write-only bindings, never this ledger.

### 2026-10-18 Resumed Implementation Checkpoint

- Resumed the two pending code workers and the owner's separate recovery-plan
  documentation task. The latter owns deferred plan 010.43; it does not expand
  010.36 or authorize infrastructure spending.
- Runtime and connector source is committed and pushed as `bf7208084`:
  AI Fabric `0.8.35`, canonical conversation-state migration/fencing, exact
  tenant/customer/deployment/target/release document ownership, generation-aware
  retrieval, and durable runtime/connector administration receipts. Product
  verification passed 436 tests (runtime 360, connector 76), with zero failures,
  errors or skips, including PostgreSQL receipt/concurrency and restart tests.
  Runtime image publication is still in progress at this checkpoint; this is
  not a claim that any customer runtime has been upgraded.
- Basic Inbox's `0.8.35` dependency update passed all 11 tests and is committed
  separately as `2035dc360`. Only the affected immutable dependency image is
  scheduled for rebuilding.
- The previous complete Platform backend run finished with 1,861 tests and
  zero failures/errors/skips. Subsequent focused readiness/admission tests pass.
  The first restricted-sandbox invocation could not attach Mockito's JVM test
  agent; the same tests passed with the required local test permissions.
- Corrected the customer console's admission sequence: each reserve and owning
  command now share the exact operation key; production plan/apply/activation
  and rollback explicitly authorize the existing target allocation. The helper
  preserves its initial allocation choice after response loss. Helper/API
  tests pass (14), and the actual-button browser smoke passes through staging,
  assignment, production planning/apply and activation using stateful API
  fixtures. This browser result is not a hosted customer certification.
- Readiness now resolves capacity through the exact release's original apply
  receipt. It accepts a valid allocation initially created during promotion
  planning and rejects an unrelated current allocation. Required admission
  checks were not weakened.
- SSH currently succeeds to both hosts with the existing key and strict host
  verification. The owner explicitly allows the current source-scoped operator
  access to remain open until full plan implementation/verification completes;
  do not cycle firewall rules or close them independently during that work.
- Fresh read-only capacity: staging has 15,608 MiB physical RAM, approximately
  2,566 MiB available and 4,092 MiB swap occupied; production has approximately
  8,239 MiB available. Staging currently contains 33 applications and 5
  databases. The reviewed dedicated offer reserves 5,888 MiB, 3,750 milliCPU,
  two databases and two indexes per target, before operating headroom. A fresh
  staging admission is therefore not yet supported by this measurement. Swap
  does not count as physical RAM headroom; paid upgrades remain declined.
- No workload was stopped or deleted during this readback. Old-looking
  `dep-7706fafb` still has a staging consumer assignment. Actual customer/bridge
  usage must be proved before any scoped reversible reclamation. The external
  capacity gate is tracked in `Required for production`; code corrections and
  source verification continue while that gate remains open.
- UI changes remain a separate uncommitted workstream. The configuration-only
  assignment needed to create an Engage origin before initial publication is
  being completed. P6 hosted rollout, exact app lifecycle certification,
  repeated-generation hosted proof and P7 remain open.

### 2026-10-18 Parallel Review And Regression Repairs

- Both immutable images finished publication: runtime `bf7208084`, digest
  `sha256:d7cc5b3a7d72f65da0f77c02ae50a27d836b8599908566b109b5a07bd5e6f16f`,
  and Basic Inbox `2035dc360`, digest
  `sha256:d74871880950f09674e0558cdfb09bdadcce050e89ae7b33312dad7069ac3759`.
  Runtime artifact identities are registered for staging review on both control
  planes. Registration is not customer deployment or production certification.
- An independent profile-contract review found credential field leakage,
  noncanonical theme/auth pin risks, and a missing `artifactRole` in the offer
  generator's artifact fingerprint. Connection profiles now admit only their
  supported fields. Supabase configuration accepts public publishable keys or
  legacy `anon` keys, never privileged secret/service-role credentials. Reviewed
  profiles reject nested themes and configuration that would normalize to
  different installed hashes. The artifact fingerprint now matches the backend
  field set. These are structural security checks, not semantic query matching.
- The complete backend run executed 1,917 tests: one assertion failure and 17
  errors, with no skips. The assertion incorrectly expected template 1.5.0 to
  retain the older Inbox plugin. V186 already publishes the correct immutable
  1.5.0 template; the redundant proposed V189 was removed before deployment.
  The 17 errors shared one sliced-test context missing the newly injected
  profile validator. After correcting that test context, all 38 affected
  profile, public-manifest, bootstrap-persistence and PostgreSQL migration tests
  pass. A complete rerun remains required after the allocation transition work.
- Existing-target configuration replacement remains an explicit implementation
  and verification gate. The console must allow a different published version
  only after the existing release settles; the ledger must preserve reviewed
  capacity, exact command receipts, generation fencing and retirement interlocks
  across that transition. Merely enabling the button is not completion.
- Fresh Platform database archives exist for both hosts, with matching-host
  `pg_restore --list` validation. These temporary operator-local archives do not
  constitute full Coolify disaster recovery or a tested restore. Deferred plan
  010.43 remains separate.
- Reviewed public profiles now support the first Engage app without an existing
  installation or a bound runtime release. This is configuration-only; runtime
  activation still requires verified release authority. The customer form keeps
  its original operation key when retrying the unchanged save after response
  loss. A newly identified backend create-receipt gap is being corrected before
  release, so an accepted embedded-app creation cannot be duplicated by retry.
- Current local UI verification passes: production build; 31 profile/admission/
  API tests; 13 reviewed-profile browser scenarios including a pre-runtime first
  app and lost-response retry; and the stateful customer staging/assignment/
  production-promotion browser flow. Independent review confirms no remaining
  actionable findings in the scoped public-profile safety corrections. These
  results use local fixture APIs, not a fresh hosted customer.
- Final owning-service review found and corrected two more edge cases: completed
  create replay after the original slug was renamed/reused, and inverse
  allocation/binding lock order between replacement planning and release bind.
  Slug availability is checked only for mutations, while completed replay still
  reauthorizes the current owner/consumer/offer. Both admission paths now lock
  allocation before binding. A latch-based concurrency regression verifies the
  binding stays available through `NOWAIT` and the waiting request rejects a
  lifecycle changed by a concurrent real bind.
- The final focused set passed 132 tests with zero failures/errors/skips:
  38 PostgreSQL admission, 38 H2 admission, 12 admission-service, 40 PostgreSQL
  create-receipt, and four public-authentication registry tests. The final full
  clean suite is running from the integrated source; its log is
  `/private/tmp/01036-full-backend-release-20261018.log`. The earlier clean
  snapshot run was deliberately stopped to include the lock-order correction.
- Desktop/mobile screenshots were inspected after animations settled. Mobile
  navigation now uses the existing drawer as a temporary panel, and the main
  flex column is width-constrained. Overflow, role-scoped navigation and
  Escape-to-close checks pass alongside the 13 reviewed-profile scenarios.
  The existing staff workspace browser smoke and customer promotion smoke pass
  on the final local UI build. No hosted UI extraction has been deployed.

### 2026-10-18 Backend Source Verification And Publication

- Backend and reviewed deployment-package implementation is committed/pushed as
  `8c35eb3bc419cc61cb42a19941bb1238c3decac0`. UI extraction remains outside this
  commit. No unrelated publishing assets, videos or local artifacts were staged.
- The complete clean backend run passed 2,030 tests with zero failures/errors/
  skips. A final explicit-assignment guard was then added: reviewed customer
  consumers cannot use deployment-wide runtime discovery before activation, or
  resolve a verified release under a mismatched target. The 33 affected-path
  tests pass against that final source, covering general discovery, bridge chat,
  workspace manifests and binding concurrency. Non-offer discovery behavior is
  unchanged. This is resource authorization, not semantic query routing.
- The final integrated lifecycle set also passes 132 focused tests. Deployment
  package/S3 verifier fixtures pass 25 tests; local UI helper/API tests pass 31.
  All stated test counts exclude no skipped cases.
- Backend-only image publication is running in GitHub Actions run `39113959720`.
  Publication and hosted deployment are separate gates. Staging-first rollout
  must verify the running image source/digest, migration 189 and HTTP health
  before production rollout. No customer runtime or customer data-plane
  activation is implied by backend publication.

### 2026-10-18 Hosted Backend Rollout Readback

- GitHub Actions [run 39113959720](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/39113959720)
  passed and published only the affected backend image. Immutable digest:
  `sha256:49d9fc4d7a71a4021adcd6574cb3c64b37f2364b0f36d1988c93a443a2df8abe`.
- Staging rollout `ugf34j2460bweq6decvlkn47` and then production rollout
  `nnmyru7iweop9q55xoziexff` finished. SSH readback verified the actual running
  image OCI revision `8c35eb3bc419cc61cb42a19941bb1238c3decac0` and digest on
  each host, not only the configured image tag. Both databases reached migration
  189; both HTTP health endpoints returned `200 UP` and Coolify marked both
  backends `running:healthy`. Staging's initial unhealthy flag cleared after
  startup; no check was disabled.
- Immutable dependency package publication and exact readback passed on both
  control planes for `mkv-template-simulated-dealership-experience-v1-5`.
  Spec fingerprints: staging
  `50e87074c95c47391bff867b8a9e7272476384b72d5169c83023cde2d9fd3ec6`,
  production `e27393b400d0b2a8d98cd987d4f1cf27d71109adb0a488dcf80dd792b6b1f0d2`.
  The environment-local artifact IDs are intentionally distinct.
- Reviewed offer `offer-loomai-engage-simulated-dealership-early-access-v6`
  publication passed real existing profile validators and artifact/target pin
  validation on each backend. Offer fingerprints: staging
  `70212b4793eec9ca2bfcb924efdc785bd5f201ba75e2aaffed40d03eb4de48ae`,
  production `9d0a116a9805efb0734be7f989c449b882bb29574cedafe53e1df61ebece37cb`.
  Publication grants no customer entitlement or capacity and deploys no runtime.
- Fresh private database archives were retained separately at
  `/private/tmp/01036-control-plane-preproduction-20261018/`; checksums and
  matching-host archive catalogs were validated for both hosts. Catalog listing
  can close stdin before reading the complete custom archive; verification
  accepted that specific `EPIPE` only alongside process exit zero and a valid
  catalog. No archive contents were restored, no backup was uploaded, and no
  full Coolify disaster-recovery claim is made.
- Post-rollout read-only snapshot: staging 15,608 MiB physical RAM, 2,535 MiB
  available, 4,268 MiB swap occupied, load averages 1.08/1.47/1.48; production
  15,610 MiB physical RAM, 8,222 MiB available, no swap occupied, load averages
  0.77/0.85/0.70. Staging remains below the 6,912 MiB admission-plus-reserve
  threshold. CPU load is not a substitute for reviewed CPU capacity accounting.
- Only the two Platform backend applications were redeployed. No customer or
  demo workload was paused/deleted, no database was removed, no customer runtime
  or data-plane assignment was changed, no capacity policy was weakened, and no
  paid resource was added. Hetzner operator access remains open as instructed.
- Remaining gate: owner decision in `Required for production` on the exact
  temporary public-demo outage or another authorized capacity path. The seven
  demos alone are insufficient; older-container dependencies must independently
  clear before any pause. Hosted repeated-generation, customer-owned S3 indexing,
  real-provider protected-action, fresh-customer lifecycle and P6/P7 certification
  remain open. UI extraction source stays separate and undeployed.

### 2026-10-18 Live Configuration-Only Customer Follow-Up

- The existing dedicated staging canary owner accepted reviewed offer v6 using
  the normal authenticated customer flow. This is a later metadata-only check,
  not an implicit side effect of the preceding backend rollout or publication.
  Its isolated draft is `dep-64759f9f`, consumer `01036-v6-config-canary`, first
  app `awi_pub_eb4ed68061ae02d4d2b15c5f6222a232`, and immutable version
  `ver-a789620e` with configuration hash
  `09ccd35eb6608b10214ab85d85aa3e0615482a02309d4d00313cbb60ff7283ef`.
- Real CUSTOMER_ADMIN checks passed bootstrap, configuration-only consumer and
  first-app replay; exact reviewed profile use; app-origin CORS projection;
  and pre-activation runtime-discovery/app-activation denial. The app remains
  DRAFT and not ready. No runtime release, provider service or database was
  created. This is not a first production customer app.
- Target-scoped customer-owned S3 binding passed with the staging identity,
  read-only metadata, secret-free response, stable command replay and no source
  object modification. Immutable version publication/replay passed. Separate
  direct HTTP requests for another customer's draft, admission reservations and
  installations returned 404/403/403. No hidden privileged customer role was
  introduced to obtain these results.
- The legal `APPLY` admission request returned HTTP 412 with
  `MEASURED_POLICY_REQUIRED`. Reservations were empty before and after; releases
  remained empty. The verifier never called apply. An initial verification
  script used the invalid operation enum `APPLY_STAGING_VERSION`; a GET-only
  state check proved zero reservations before the corrected contract was used.
  This script correction was not a product or admission-policy change.
- Reusable `verify-customer-configuration.mjs` now preserves the nine live
  control-plane checks without accepting staff keys or approving policy. Its
  optional missing-policy probe fails on any different denial, records an
  unexpected successful reservation for inspection, and never applies or
  cleans it up. Initial fixture/support suite: 38 passed. The reusable live
  run at 09:47 UTC passed all nine checks using the same operation keys and
  configuration, without creating another canary. Evidence is private at
  `/private/tmp/01036-v6-reusable-configuration-evidence.json`.
- Live readback also exposed a real catalogue usability gap: missing/expired
  placement evidence removes otherwise reviewed targets from discovery, so the
  customer cannot select a target to recheck placement. A focused correction is
  in progress; it must preserve explicit blocked readiness and keep fresh
  evidence mandatory for mutations. Do not mark that source correction live
  until its own tests, image and readback complete.
- Resumed parallel audits found no confirmed loss of STAFF001..039 source
  capabilities, but full role/hosted parity and a compatible UI-only rollback
  artifact remain unproved. Two resource-scope UI defects are being corrected:
  customer deep-link/selection consistency and one-time Inbox credential display
  across target changes. UI source remains separate and undeployed.
- The old-six dependency audit disproves blanket fixture cleanup: ProdUS has
  active staging discovery and real fallback dependencies; old Shopify services
  retain owned release/mutual references. Two expired-session runners are only
  conditional pause candidates, about 488 MiB combined, not approved disposable
  resources. A stale Shopify runner handle identifies a different app UUID;
  any future operation must use exact physical ownership readback. No pause,
  delete, rebind, paid upgrade or firewall cycling occurred. The earlier seven-
  demo outage proposal is on hold pending a complete viable owner decision.

### 2026-10-18 Placement Discovery Correction And Rollout

- Focused backend commit `c41c02ace594a094f5afb446685904fa8382534d` retains
  an entitled, composition/profile-validated target when its placement evidence
  is missing or expired. Its readiness is `PLACEMENT_RECHECK_REQUIRED` with the
  exact blocker. Untrusted placement, profile drift, missing profiles and other
  denials still hide the target; mutation authorization is unchanged. This is
  explicit typed recovery of infrastructure evidence, not text matching or an
  override of an LLM decision.
- The 86 focused backend tests pass, zero failures/errors/skips, including
  catalogue discovery, existing authorization, reviewed composition and trusted
  placement. The earlier complete 2,030-test baseline remains distinct from
  this scoped delta; it was not relabelled as a full rerun of this commit.
- GitHub Actions run `39117062370` succeeded. Only the backend image was built;
  the UI job was skipped. Immutable digest:
  `sha256:15f75d4aeae3add02927691af8097ad79fdfa4186aa0923e736a6e685a4acf6c`.
  Staging rollout `l79muxbk8jcgx0abnluit9i4` and production rollout
  `w6pz6jpodigau1yhti3wbjqe` finished in that order. Actual running OCI source
  and digest, migration 189, Coolify health and public `200 UP` were verified
  on each host. No active apply existed in either release inventory before the
  backend restarts.
- The staging live regression retained the expired production target in the
  catalogue, then returned `409 PLACEMENT_EVIDENCE_STALE` for a publish attempt.
  Published version count stayed one. No placement evidence was modified and
  no runtime apply was called. All nine configuration-only customer checks then
  passed again after the backend restart using their original keys, proving
  their persisted receipts remained usable.
- Verification-only commit `c6aa7de31` contains the reusable canary and guide.
  After independent review, it validates session/entitlement customer scope
  before writes, requires complete scoped immutable-version receipts, and checks
  redaction/read-only metadata on both binding responses. Twenty canary fixture
  tests plus the prior 25 support tests pass: 45 total, no failures/skips. The
  final independent readback found no remaining serious issue in that scope.
- Fresh private Platform database archives were taken before this correction
  under `/private/tmp/01036-control-plane-pre-catalogue-fix-20261018/`.
  Checksums and matching-host catalogs pass for both. No restore, Coolify
  backup, managed source-object storage or new infrastructure was introduced.
- Latest pre-rollout read-only operating snapshot: staging 2,528 MiB available
  physical RAM, 4,264 MiB swap used, load 3.33/2.12/1.76 on eight cores;
  production 8,194 MiB available, no swap used, load 1.12/0.57/0.51 on four
  cores. Root disk use was 35%/12%; app/database counts remain 33/5 staging and
  17/3 production. These are observations, not an approved admission envelope.
  The new customer runtime still has not been admitted.

### 2026-10-18 Local Functional UI Corrections

- Customer deployment controls now resolve their selection through the shared
  workspace URL context and existing persisted preferences. Explicit unknown
  deployment IDs remain unresolved rather than silently selecting another owned
  deployment. Sidebar links, browser history, reload and the selected resource
  stay aligned; preference-save failure exposes a retry.
- One-time Basic Inbox credentials use a generic scope-token hook covering
  actor, customer, auth mode, role, deployment, target and release. Every scope
  transition invalidates old completions, including A-to-B-to-A navigation.
  Stale handlers retain their original rendered token; delivery from an old
  request cannot become visible under a newer target or account.
- The placement selector recognizes the explicit recheck state. A successful
  probe refreshes the catalogue and cannot publish its local evidence into a
  different selected target. Bootstrap remains blocked on fresh evidence.
- Eighteen browser scenarios pass using local fake APIs/credentials. The final
  parent UI production build and 31 helper/API tests also pass. Existing
  runtime-config and large-bundle build warnings remain. These checks are not
  hosted UI parity or a real protected-capability sign-in journey.
- UI files remain uncommitted and undeployed, preserving the final extraction
  gate. The rollback-split audit separates mandatory API/auth/profile/lifecycle
  adapters from AppShell presentation hunks; reverting all dirty UI files would
  remove functional compatibility work. A verified compatible functional UI
  artifact and its hosted rollback proof still must be produced. Frozen
  STAFF001..039 inventory stays unchanged, and full role/behavior parity is open.

### 2026-10-18 Revised External Capacity Decision

- The complete replacement proposal uses only twelve advertised framework demo
  backends: Account Resolver, Agentic Action Resolver, AI Shopping Experience,
  Behavior Signals, Conversation Handover, Deployment Knowledge Guard, Document
  Knowledge Operations, Incident Investigation, Live Data Sync, MCP Operations,
  Privacy Shield and Tenant Guard. All legacy/customer services, the dealership
  chain, shared Engage, control planes, databases and MCP Reference Server remain
  outside the proposal. This supersedes the earlier seven-demo/old-six option.
- Two fresh physical-memory samples support a conservative estimate of
  2,800 MiB available plus 4,747 MiB reclaimable anonymous resident memory,
  excluding swap/cache/shared-RSS credit. After the 5,888 MiB topology, projected
  remainder is 1,659 MiB: 1,024 MiB operating reserve plus 635 MiB extra. This is
  a RAM option, not approved admission; actual post-outage memory, CPU, disk,
  startup and operating-policy evidence must all pass independently.
- Owner approval must explicitly cover all twelve public-demo outages, possible
  session/in-memory database resets, retained exact containers/layers/volumes,
  and return to service. Some file-backed demos lack mounted volumes. No drain,
  state backup or stop/start/restore test was performed. The temporary customer
  allocation cannot coexist with all twelve restarted demos at this estimate;
  separately authorized canary retirement or other real capacity is required
  before full restoration. No permanent public availability is claimed.
- Exact UUIDs, links, reference checks, per-demo persistence qualifications and
  sample math are in
  `/private/tmp/01036-framework-only-outage-option-20261018.md`. Sent to the
  dedicated `Required for production` owner thread for a decision. No approval
  is inferred, no workload was stopped/deleted, no policy weakened, and no paid
  resource or firewall change occurred. All bounded parallel workers completed
  and were closed; the external owner decision remains pending.

### 2026-10-18 Authorized Temporary Workload Stops

- The owner's informed approval in `Required for production` supersedes the
  earlier pending decision: the twelve disclosed framework demo backends may
  remain stopped while dealership productization is the priority, including the
  disclosed transient demo/session resets. The owner additionally authorized
  temporary ProdUS and Shopify stops directly in the implementation thread.
  These instructions do not authorize deletion, recreation, durable-data loss,
  new paid capacity or bypassing build/deployment verification.
- All twelve framework demo backends and eighteen audited ProdUS/Shopify
  application services are now stopped. Application stops used graceful
  `SIGTERM` with no forced-kill timeout. All eighteen latter exits were 0 or 143;
  no OOM stop occurred. Readback verifies the same container IDs, image IDs,
  writable filesystem layers and mounts. No database, volume, image, deployment
  configuration, customer assignment or release history was removed.
- Two Shopify bridges remain running pending actual datasource verification:
  their source defaults to an in-memory database. The authorization for demo
  resets is not treated as authorization to discard Shopify business state.
  This entry-specific check does not delay the other eighteen stops.
- Fresh post-stop physical memory samples show staging 10,561 MiB available
  on eight cores and production 12,095 MiB on four cores. Swap is excluded.
  Root disk use is 35% and 13%, respectively. Short host CPU samples were
  297-934 milliCPU staging and 170-277 milliCPU production; these are observed
  samples, not a sustained-load certification or reviewed admission allowance.
  In particular, production's four cores must still cover the 3,750 milliCPU
  topology plus real baseline and operating reserve; free RAM alone is not
  sufficient to authorize production promotion.
- All five staging and three production audited databases remain running and
  healthy. Dealership and shared control-plane services were not stopped.
  Private before-state and sanitized retention evidence are held in
  `/private/tmp/01036-authorized-stops-20261018/` and
  `/private/tmp/01036-produs-shopify-stops-20261018/`; post-stop readback is
  `/private/tmp/01036-post-produs-shopify-stop-readback-20261018.json`.
  Hetzner access stays open per the owner's instruction until full completion.

### 2026-10-18 Shared Target Identity And Fresh Customer Canary

- Found a real contradiction: customer composition/placement and admission
  hashed different field names, and only admission included the environment.
  No honest policy payload could satisfy both. Commit `65b522a2d` replaces
  those local recipes with one private deployment-domain identity, retaining
  all eight security fields. Active/provider/source/physical-server/intent and
  freshness checks remain independent and fail closed.
- Java and the offer publisher share a nested golden hash. Numeric
  normalization is local to this identity and accepts only exact safe integers;
  resource decimals remain strings. Malformed configuration, unsafe/fractional
  numbers, old environment-omitting pins and every tested security-field drift
  fail closed. Global evidence hashing, semantic decisions and the public
  framework were not changed. No alternative legacy hash is accepted.
- Focused backend verification: 146 tests passed, including 13 real composition
  to placement to admission bridge cases using the H2 admission ledger. All 48
  support tests passed. Independent scoped review found no actionable security
  issue under the documented numeric contract. The initial extreme-exponent
  assertion was corrected to distinguish parser-invalid input from a parsed
  unsupported number; both remain denied rather than rounded or accepted.
- CI `39125180102` passed with only the backend image selected and the UI image
  job skipped. Both running backends are verified on full source
  `65b522a2dc5167c5770d198642921448a0009a12`, digest
  `sha256:9adbf77f53a79afd951f15e656629b33e9ccdabdc404f9725446fef10fc06ee8`,
  migration 189 and public `200 UP`. Staging rollout
  `s11jabc65u86e3vd1zltw6ui` completed before production
  `h93t53ryvz06wcn8iz3tdx9z`. No active apply was found before restarting them.
  The pending UI extraction source was not committed or deployed.
- Fresh pre-update private control-plane archives are in
  `/private/tmp/01036-control-plane-pre-identity-fix-20261018/`. Checksums and
  matching-host PostgreSQL catalogs passed for both. This is not restore proof
  or the deferred Coolify disaster-recovery implementation.
- Published a new immutable offer
  `offer-loomai-engage-simulated-dealership-early-access-v7` on both control
  planes. The publisher now verifies each freshly recorded placement identity
  against its exact reviewed pin before publication. Fingerprints are staging
  `9ec0b49744ebcb59ac5770419fc82b3279bab508d6f4ba18c01d82c79452bcc4`
  and production
  `1e7244834a17a7cd27951827f40957a8a90490f590a0e1a3e29009aa031441c9`.
  Existing v6 and earlier records were not rewritten or repinned.
- The real customer-owner configuration canary passed all nine checks on v7:
  deployment `dep-51739855`, immutable version `ver-e18d9063`, consumer
  `01036-v7-config-canary`, draft app
  `awi_pub_9a9e273f598766fb280af69b2775a835`. Bootstrap/app/consumer/binding/
  version replay, read-only staging S3 redaction, foreign-resource denials and
  early activation denial passed. Legal admission still returned the exact
  missing-measured-policy denial, with no reservation or release. No runtime
  apply, source-object mutation or provider call occurred.
- Post-rollout readback still confirms all eighteen ProdUS/Shopify apps stopped,
  all eight audited databases healthy and the two memory bridges retained.
  Available RAM was 10,621/12,156 MiB staging/production in the later snapshot;
  root disk use remained 35%/13%. Short CPU samples are not a production
  capacity approval. Framework remains 0.8.35, with the earlier pinned runtime
  artifact unchanged.

### 2026-10-18 Provider Allowance Enforcement Work

- The owner approved a bounded, synthetic staging provider test and the existing
  owner test mailbox/attended escalation contact through `Required for
  production`. Exact private approval is in the ignored production handoff.
  It does not grant new infrastructure, production promotion, a counter reset
  or an SLA. No test provider call or lifecycle email has been sent yet.
- Read-only inspection found that current framework diagnostics and runtime
  metrics do not enforce aggregate allowance before every paid send. Native
  retries, embedding usage and a separate embedding worker require coverage;
  no successful metrics label is treated as enforcement. A shared private
  durable reservation ledger and guarded provider transport are being built.
  No public framework release is currently required by the available hooks.
- The first token ceiling is lower than this model's documented full context.
  A conservative pre-send full-context reservation would correctly deny every
  chat call at that ceiling. Only the token-ceiling adjustment was sent back
  for a specific owner decision; the existing ceiling remains binding until
  explicitly changed. No guessed character/token estimate, silent API/model
  switch or hidden fallback is being used to make the canary appear green.
- Execution tracking and the source-only assessments are under
  `/private/tmp/01036-provider-budget-execution-track-20261018.md`,
  `/private/tmp/01036-provider-budget-enforcement-assessment-20261018.md` and
  `/private/tmp/01036-operating-proof-path-20261018.md`. Genuine hosted budget,
  restore/rebuild, lifecycle delivery and customer-runtime/promotion checks
  remain open. This is continued implementation, not full 010.36 completion.

### 2026-10-18 Retained Stops And Guard Integration Checkpoint

- Paused auto-deploy for exactly the thirty already-stopped applications:
  twelve framework demos on staging and eighteen ProdUS/Shopify components
  across both hosts. Only that flag changed; actual settings and exact
  container/image/writable-layer/mount readbacks passed. Private prior settings
  and restoration evidence are under
  `/private/tmp/01036-stopped-auto-deploy-preservation-20261018/`.
  Restore them only with the productization return-to-service/capacity plan.
- The two Shopify bridges remain running to preserve their actual memory-only
  business records. Their source watch path is
  `product-services/shopify-bridge-service/**`, outside the pending changes.
  No bridge, database, dealership or shared-service deletion/recreation occurred.
- Fresh post-stop readback passed: all eight audited databases and all running
  health-checked dealership/shared services are healthy. Available physical RAM
  was 10,608 MiB staging and 12,014 MiB production; swap is excluded. Production
  still has four CPUs. Neither these samples nor spare RAM approve promotion.
- Private accounting implementation now includes the durable neutral ledger,
  captured native OpenAI exchange guard, CP lifetime allowance issuer, explicit
  output-cap mode, private two-phase initialization/activation, runtime turn
  and indexing integration, and a local-only bounded embedding-worker profile.
  No public framework source or release was changed. These changes are not yet
  deployed or evidence of a completed hosted canary.
- Verified local results so far: ledger 52 H2/PostgreSQL cases, native adapter
  88 fake-HTTP cases, embedding-worker four cases, CP issuer 79 cases and parent
  focused backend integration 158 cases. The full backend run found a genuine
  Spring constructor-selection regression in the new runtime client; explicit
  production-constructor injection is fixed in source and awaits its rerun.
  Do not label that initial full run green. Runtime integration and independent
  review remain in progress.
- Paid control-plane inference probes are explicitly deferred for customer
  offers; their result is `SKIPPED`, not provider success. Genuine inference
  verification must use the activated, metered deployment runtime. Queries and
  suggestions must share the approved interactive-request limit. Unknown paid
  attempts retain their full reservation; resource retirement does not refund
  financial credits or authorize a new runtime store.
- The original approved 50-request, 100,000-token, USD 2 staging allowance is
  still binding. The token-only 200,000 amendment remains unanswered. No paid
  provider request, lifecycle email, live financial grant or runtime apply has
  been made. Receipt-loss characterization in the current framework still
  needs proof against the actual enabled profile before any broad claim of
  effect-preserving error handling. UI extraction/deployment remains last.

### 2026-10-18 Provider Guard Combined Verification

- Full backend rerun passed 2,189 tests, and the full private runtime reactor
  passed 425 tests, with no failures, errors or skips. The earlier missing
  constructor annotation and test compilation failure were corrected, not
  waived. Logs are `/private/tmp/01036-provider-budget-full-backend-rerun.log`
  and `/private/tmp/01036-provider-budget-full-runtime-rerun.log`.
- Final runtime capability, action-receipt, suggestions and real PostgreSQL
  migration regressions passed after the packaged capability declarations were
  added: 43 passed. Final backend regressions for the subsequent immutable-
  artifact and image-pull corrections also passed: 173, with no failures,
  errors or skips. Logs end in `final-focused-runtime.log` and
  `final-focused-backend.log` under `/private/tmp/01036-provider-budget-`.
- Independent review found that the runtime provisioner validated an artifact
  digest but still requested its mutable image tag. It now requests Coolify's
  digest-specific image reference and checks the observed repository/digest
  setting before continuing. The guarded-offer gate also rejects old runtime
  artifacts before financial allocation; enabling an environment variable on
  an older image is not accepted as installing enforcement.
- Runtime normalization now keeps a later turn interruption separate from
  successful individual action receipts. Focused regressions preserve genuine
  receipts and report the interruption without inventing effects. Review of
  ordinary conversational execution found no confirmed provider exception
  escape requiring a public framework release. Synthetic broader pipeline
  limitations remain outside that bounded proof; framework stays 0.8.35.
- The ledger/native modules are in local commit `66d1ec2c3`; host integration
  is not yet committed or deployed at this checkpoint. Both live control-plane
  backends remain `65b522a2d` / migration 189. No paid call, financial grant,
  lifecycle email or new customer runtime apply occurred. UI extraction stays
  last and the original approved financial limits still apply.

### 2026-10-18 Guarded Images Published And Control Planes Upgraded

- Pushed `66d1ec2c3` and `600f136bf`. Runtime CI `39134142561` completed
  verification before publication. Backend CI `39134142560` passed and skipped
  its UI image job. No public framework release or stopped-workload rebuild was
  triggered by these image workflows.
- Staging rollout `wswm25do7whkrc1u0852ejlo`, then production rollout
  `yh59j4qzgu6sly0p7696ua5y`, both finished. Both running backends match source
  `600f136bf0becb9a473ff1e085bbc9896b4ef696` and digest
  `sha256:4cabac7a30bf5f26860e998c8e25962d8695df8674c56b67f80ccab8fc1c837c`;
  both databases report migration 190 and public health returns UP.
- Fresh private archives in
  `/private/tmp/01036-control-plane-pre-provider-budget-20261018/` passed
  SHA-256 and matching PostgreSQL-client catalog validation, 706 lines each.
  This was archive validation, not an actual restore or a customer-runtime
  recovery rehearsal.
- The cached runtime image digest is
  `sha256:d3f744e6c68677b49d128a37a99c31b0717ebf87b1b6d8dd45adbe14030baa28`.
  Never-started local image inspection verified OCI source, AI Fabric execution
  0.8.35, both private guard JARs, runtime V19 and complete packaged manifest.
  The temporary inspection container was removed. The first harness run used
  an incorrect nested META-INF path; actual root META-INF content matched the
  committed source, and all exact checks then passed.
- Exact artifacts are registered/promoted on staging `dsa-df79db5e` and
  production `dsa-c92fc189`, capability manifest hash
  `f14a2e45a46e9e10a3485143241b973162b723ac582b52ed258d65ea9e37de18`.
  Registration did not repin old offers, apply a runtime or initialize credits.
- HTTP budget readback passed anonymous denial, staff authentication and exact
  authenticated missing-grant handling on both hosts. Customer-owner denial is
  not yet proven: the test initially confused the session's email actor ID
  with the persisted user ID. Keep its initial failed result separate from
  the passing staff/anonymous checks.
- Real handoff review found a functional accounting gap before activation:
  customer admission manifests populate physical topology but zero financial
  dimensions. The first issuer implementation/tests incorrectly assumed those
  dimensions contained the approved financial allowance. A focused correction
  now uses the explicit staff-reviewed lifetime grant as the financial source,
  with whole-grant automatic allocation, exact replay and no refunds. Unsupported
  provider profiles fail before debit. Verification passed 80 H2/PostgreSQL
  cases and a 131-test parent integration gate. The real customer-manifest
  producer feeds the issuer in both databases; concurrent authorized releases,
  partial staff allocations, replay, retirement and recreation are covered.
  Initial fixture wiring errors were corrected without disabling constraints.
  The correction is not live merely because migration 190 is installed.
- Latest short readback retains the eighteen ProdUS/Shopify application stops,
  eight healthy databases and two running memory-backed Shopify bridges.
  Physical available RAM: staging 10,615 MiB, production 12,142 MiB. Swap is not
  capacity. A separate 15-minute production baseline completed with 180
  five-second intervals: median 160mCPU, p95 440mCPU, peak 720mCPU, no observed
  swap I/O, I/O wait or steal. It includes control-plane operational work but
  no candidate customer workload. The proposed 2,750mCPU full-feature topology
  is unmeasured, not an approved offer. Evidence is under
  `/private/tmp/01036-production-baseline-20261018/`.
  No paid call, grant, lifecycle email, customer runtime apply or UI extraction
  deployment has occurred. The token amendment and remaining acceptance gates
  remain open.

### 2026-10-18 Final Guarded-Offer Checkpoint

- Separate pushed commits: `ed122f1b7` corrects the real financial-authority
  handoff; `2bf8b3aab` prepares the fresh guarded offer. Final parent backend
  integration passed 131 tests, and offer-support fixtures passed 54. Runtime
  and public framework sources were not changed by this follow-up.
- Backend-only CI `39136211077` passed; the UI job was skipped and the runtime
  image workflow was not retriggered. Staging `x111yg1wyx30kfbi63fp64tv`, then
  production `lldgi2enq4hqqo0v3p1lbgpc`, finished and passed actual image,
  migration and health verification. Both now run
  `2bf8b3aaba2e9bb1b5a1a188938f4faf872ec0f4` at
  `sha256:8390c26a592c2bea070d5102f0ba04b8053cbb5ab08c621362a031b01e44314a`,
  migration 190, public UP. An early staging check correctly refused proof
  during the temporary two-container rolling overlap; it passed only after
  completion. No exact-container assertion was weakened.
- Fresh pre-correction private database archives and catalogs are in
  `/private/tmp/01036-control-plane-pre-whole-grant-fix-20261018/`; both checksum
  and matching-client catalog checks passed, 723 lines each. Actual restore
  was not performed.
- Immutable offer v8 is published on both control planes, with fingerprints
  staging `856085506838af34a923dc2fe3f00fba863be6ecb261f3c24e9a47a63bbc74ac`
  and production `2165f95b87a9cb590a9b6251618c43eee2f05a52869c56b9a0cbeaaa5162c41a`.
  Template 1.5.0 and the complete 3,750mCPU topology remain unchanged; the
  2,750mCPU candidate was not published as if it had been measured.
- The real staging owner passed all nine configuration-only v8 checks:
  deployment `dep-ae68ef7b`, version `ver-050b015a`, config hash
  `ea390514ea9d0839911ed4e21d3af417afd45a43ec815cf33e6bb079a9eea677`,
  consumer `01036-v8-config-canary`, draft app
  `awi_pub_b1c120b26ab7c988f76f7aebfaf28a91`. Evidence:
  `/private/tmp/01036-v8-configuration-evidence.json`; private continuation state:
  `/private/tmp/01036-v8-configuration-private/state.json`. Admission returned
  the expected missing-measured-policy denial, with no reservation or release.
  No runtime was applied or app activated.
- HTTP budget checks prove anonymous denial and authenticated staff handling
  on both hosts, plus actual staging customer-owner denial with HTTP 403 after
  exact source-bound email/session verification. Production owner coverage is
  still NOT_PROVEN, not silently passed; no production customer test session
  was created. Routine owner authentication only updated last-seen metadata.
- Exact-image offline bootstrap passed all eleven assertions in 59,698ms:
  liveness UP; readiness DOWN/NOT_INITIALIZED; private readback UNINITIALIZED;
  unauthenticated and wrong-scope denials; query/suggestions typed budget denial
  before AI orchestration; final state still UNINITIALIZED. It used synthetic
  keys, network NONE, loopback only, bounded local resources and disposable
  storage. Its only owned container was removed. Evidence:
  `/private/tmp/01036-offline-guarded-runtime-XGSjViAxk6g1/report.json`.
  Earlier harness failures (missing Docker stdin forwarding, required release
  ID and offline hostname resolution) remain recorded separately. No runtime
  guard, scheduler, lifecycle check or production setting was disabled to pass.
  This local negative-path proof is not hosted activation or paid inference.
- Final read-only preservation proof passed for all thirty stopped apps:
  21 staging, nine production, exact container/image/config/writable-layer/mount
  identities retained and auto-deploy still paused. Evidence:
  `/private/tmp/01036-final-retained-stop-readback-20261018.json`.
  The two memory-only business-state Shopify bridges remain running; eight
  audited databases and shared/dealership infrastructure were preserved.
- All parallel work for this checkpoint was collected and the agents closed.
  No paid provider test, live financial grant, customer runtime activation or
  lifecycle email occurred. The 200,000-token amendment still awaits the owner;
  50 requests / 100,000 tokens / USD 2 including embeddings remains binding.
  Complete hosted indexing, private bootstrap/activation, restart/recovery,
  lifecycle notices, production sizing and promotion acceptance remain open.
  Keep Hetzner access open as instructed; P6 UI extraction and P7 certification
  remain last and incomplete. Full 010.36 is NOT declared green.

### 2026-10-18 Verified Owner Allowance Amendment

- Verified the owner's direct reply in Required for production, turn
  `01a14f8a-55a4-7cf3-b62c-ee2c8f91b5d7`, and its private handoff record. The
  same attended synthetic staging campaign now has cumulative ceilings of
  50 interactive requests, 200,000 model tokens and USD 12 total provider spend,
  including embeddings. USD 12 replaces USD 2, not additional credit. No
  counter resets, expanded dataset, new provider account or production approval.
- Fresh read-only staging database counts: zero financial grants, zero
  allocations, no active measured policy, zero held reservations and zero
  in-flight releases. No paid call or financial grant was made during this
  readback. Keep durable pre-send enforcement and first-limit stopping mandatory.
- Reopened the existing three coding agents for bounded enforcement, admission
  and acceptance audits. Parent owns all hosted mutations and paid calls.
  A read-only 15-minute staging baseline sample is running; this is not a
  candidate workload result or approved operating policy. UI extraction stays
  last and the production capacity/promotion gates remain open.

### 2026-10-18 Dormant Grant And Pre-Debit Configuration Correction

- The staff-only financial API recorded the one approved staging campaign grant
  `pug-01036-dealership-staging-20261018`. Exact retry and quota readback passed:
  50 interactive requests, 200,000 tokens, USD 12 including embeddings, expiry
  2026-10-19 15:00 UTC, zero allocated. No provider calls or runtime activation.
  CP allocation is assignment of credits, not a claim of measured spending;
  the deployment-local ledger remains the authoritative paid-attempt counter.
- Source audit found an availability/configuration defect, not a spending
  bypass: v8's published provider configuration lacks the explicit output cap
  required by the guarded runtime. It must not be applied as-is. Generic
  pre-debit validation now rejects missing/invalid output caps and unsupported
  effective provider/model configuration before credits are assigned. The
  focused H2/PostgreSQL contract passes 98 checks. No runtime guard, public
  framework behavior or staff split-allocation contract was weakened.
- A new immutable template 1.6/spec 1.6/offer v9 candidate is being prepared.
  It declares a 2048-token output cap and a complete 2,750mCPU topology while
  retaining every service, binding, RAM/disk allocation and image pin. This is
  a staging-first measurement candidate, not measured production capacity;
  old published templates/offers remain unchanged. The sole financial grant
  remains unallocated while this replacement composition is verified.
- The staging 15-minute baseline has 180 five-second observations: CPU p50
  400m, p95 640m and maximum 1040m across eight cores; ending MemAvailable
  11,102,191,616 bytes. Maximum runnable count 36 and brief swap-in 16KiB/s
  are retained honestly; no observed swap-out, I/O wait or steal. Memory was
  observed at endpoints, not measured as a candidate workload peak. Evidence:
  `/private/tmp/01036-staging-host-baseline-20261018/evidence.json`.
- Fresh pre-migration backups in
  `/private/tmp/01036-control-plane-pre-runtime-cap-and-rightsize-20261018/`
  pass checksum and matching-client catalog checks, 723 lines each. This is
  not restore proof. Separate synthetic runtime export/restore work is still
  in progress; its initial local internal-network/port harness failure is
  preserved rather than relabelled as a successful product check.

### 2026-10-18 Immutable Candidate And Vector Preservation

- Pushed separate scoped commits: `4b8c80e0a` validates effective runtime
  provider/output-cap configuration before allowance allocation; `97fa5f9f6`
  accepts explicit positive-integer output caps in the generic Marketplace
  template contract; `6de589ce2` adds immutable template/spec 1.6 and staging
  offer v9. The combined backend gate passed 272 checks with no failures,
  errors or skips, including real PostgreSQL V190-to-V191 migration and
  template compilation; support fixtures passed 56 checks. No public framework
  source, release or runtime image was changed.
- Both backends first passed exact-image, migration 191 and public-UP checks
  on `6de589ce2b6bbf095f2990e52cf1d4f4f916a4a7`, digest
  `sha256:d4d59933e1c29fb005124a10112df1033de8a19a713e15be9f5241374f2ada9c`.
  CI 39141112248 passed with UI deployment skipped. This supersedes the older
  V190 checkpoint above, without claiming hosted customer-runtime acceptance.
- The 1.6 candidate explicitly declares `openaiMaxTokens=2048` and all eight
  services at 2,750mCPU, 5,888MiB RAM and 16,384MiB disk. Only bounded CPU
  requests changed; services, features, bindings, RAM/disk and exact image pins
  remain. This is a full-feature measurement candidate, not measured production
  fit. The old published composition remains immutable.
- Published spec 1.6 and offer v9 on staging only, with fingerprints
  `45948e39d82f17196b3712941a2f70ef6255e30532bd839b1cf819a9577416c4`
  and `d0e9fa9d4e39b1db26a0b7a9364048f7fd025e3ec4d5241582f81d9f6bb84ea6`.
  Nine real-owner configuration-only checks passed for `dep-0d7fc676`, version
  `ver-66489d48`, config hash
  `5d27f48409fe095612c767431e855cd1ea6b9ec2702ef620bb0ee693a34b524e`.
  App `awi_pub_67237efb5086ffedcc2af2d58424b752` remains DRAFT. Actual immutable
  database readback proves the 2048 cap and Serverless plan. The expected
  `MEASURED_POLICY_REQUIRED` denial created no reservation or release.
- The template's managed Zilliz Serverless allocation is a separate external
  effect, not authorized by the USD 12 model/embedding budget. Read-only account
  checks distinguish eight Serverless clusters and one existing Free cluster
  using `deploymentOption`, not the misleading common `plan` label. Free-01
  has one database and no collections, and neither control plane has a matching
  resource handle. Those observations do not prove ownership or authorize reuse.
  Required for production is awaiting the owner's bounded Free-01 decision;
  safe scoped adoption, credentials and retain-not-owned cleanup would still
  need implementation and verification. No cluster or credential was changed.
- Fixed a discovered preservation defect in `6d32eb2be`: missing stored runtime
  credentials now return `MANAGED_ZILLIZ_RUNTIME_CREDENTIALS_REQUIRED` rather
  than implicitly deleting/recreating an existing cluster. All three missing-
  secret variants and restored-credential reuse pass the 26-test focused gate;
  independent critical review passed. CI 39142840789 published the exact backend
  digest `sha256:8c40c28449c1e27664c6e3b53ed6f38d996cffe7b4d36f051381199eabaa8b6c`.
- The reproducible local exact-image budget-store export/restore rehearsal is
  committed separately as `877b9fcb9`, outside image watch paths. The parent run
  passed at `/private/tmp/01036-local-budget-restore-hRJhles2QIpc/report.json`:
  actual V19 owning services, pg_dump, removal of the source database, restore
  into another database, exact typed identity/expiry/counter/hold checks and
  owned-resource cleanup. No provider client or live database was used. This
  is subsystem-local proof, not hosted whole-topology recovery or permission
  to reactivate a stale financial snapshot without usage reconciliation.
- Fresh preservation-fix backups at
  `/private/tmp/01036-control-plane-pre-vector-preservation-20261018/` passed
  checksum and matching-client catalog validation, 723 lines each. Actual
  control-plane restore remains unperformed. Read-only retained-stop verification
  at 16:02:55 UTC passed all 30 apps, unchanged with auto-deploy paused.
- Read-only allowance verification at 16:08:39 UTC still reports zero allocated
  across all three cumulative ceilings. No customer runtime was applied or
  activated, paid canary run, or lifecycle email sent in this continuation.
  Measured admission, safe vector binding, hosted acceptance, production fit,
  promotion, P6 UI extraction and P7 certification remain open. Keep Hetzner
  access open as instructed; do not restart retained stopped workloads.
- Final preservation-fix rollout verified on both control planes: source
  `6d32eb2beae23283d60a1615eb0430fd0708ecdb`, exact published digest above,
  migration 191 and public health UP. Staging deployment
  `hqxpt8e80xdwez4ibz44s8p8` and production deployment
  `ibj1syauyqq3z22q6yhzk34s` both finished. Verification required exactly one
  backend container and matching OCI source revision/repository digest; no
  overlap or health assertion was weakened. This is a control-plane rollout,
  not customer-runtime acceptance or vector-provider mutation.

### 2026-10-18 Approved Existing Free Cluster And Retained Binding

- Owner directly replied `Use Free-01` in main. Allocation permission is
  resolved for the existing cluster, isolated synthetic staging, zero additional
  vector cost, retaining both cluster and data. No create/delete/recreate,
  upgrade, paid fallback or production allocation is approved by that reply.
  The existing 50-request / 200,000-token / USD 12 cumulative campaign is
  unchanged, including prior use and holds; no new grant or reset was created.
- Backend source commit `2f5fb8132` implements the explicit retained external
  binding, using existing Platform provider modes, scoped prefixes, secret
  storage and resource-handle primitives. V192 seeds immutable template 1.7
  without endpoint/credentials or managed-cluster creation. Spec 1.7/offer v10
  reuse the exact 1.6 dependency graph, service images and resource envelope.
  This is not production-fit or actual hosted-runtime evidence.
- Binding pins the published version/hash, customer, tenant, deployment,
  target, provider cluster/project/region and deployment option. Metadata-only
  serving authentication verifies the default alias and collection scope
  before credits/dependency effects, then repeats during plan generation.
  Missing/changed credentials cannot fall back to global keys or reset the
  binding. A private target-owned HMAC key prevents publishing a guessable
  password digest; the organization key stays on the control plane.
- Integrated gate passed 351 checks with zero failures/errors/skips, including
  real PostgreSQL V192, admin-only method security, generated runtime contract,
  pre-debit denial, persisted retirement blocking and preservation regressions.
  Support fixtures passed 57 checks. Six physical-name golden vectors passed
  against the exact cached runtime's actual Milvus class without constructing a
  provider or making network calls. Source guard and diff check passed.
- Independent critical review cleared after correcting five findings: late
  validation before financial/provider effects, mutable override anchoring,
  idempotent replay despite lost secrets, integer code overflow, and double
  counting already materialized collections. No public framework release,
  runtime-image change or semantic query-matching workaround was introduced.
- Retained external data has a fail-closed INDEX retirement interlock before
  any runtime clear. The current contract cannot certify complete retained-
  index retirement; its blocker is explicit and capacity is not falsely freed.
  The pinned adapter also does not select arbitrary database names, so only
  `default` plus unique physical collection prefixes is accepted. This single
  synthetic allocation is not proof of provider-enforced tenant ACLs.
- Existing Free-01 serving credentials were not found in private docs or either
  vault; Required for production is gathering the existing pair securely. No
  unrelated service credentials, management-key-as-runtime-token, password
  reset, new provider user/key/role or permission change is being used.
- At this source checkpoint the changes are not yet deployed. Fresh private
  backups and catalogs are validated under
  `/private/tmp/01036-control-plane-pre-retained-external-binding-20261018/`;
  actual restore false. See the [bounded execution checkpoint](../../../../../../../../verification-support/productization-01036/EXISTING_VECTOR_CANARY_CHECKPOINT.md)
  for exact local test evidence and next steps. Actual vector binding, hosted
  apply/activation/indexing, provider spending, production sizing/promotion,
  full retained-index completion, P6 and P7 remain open. UI extraction is last.

### 2026-10-18 Final Retained-Binding Backend Rollout And v10 Configuration

- Committed inventory follow-up `58bf670de`: resolve the approved target binding
  before projecting release provider config and the shared vector registry.
  Required unbound scope is BLOCKED; native external isolation is accurately
  reported as COLLECTION_PREFIX. Focused gate107 passed, zero failures/skips.
- Backend-only CI39153198911 passed and published digest
  `sha256:9d12191c88cba18c9d38892ab34ef214944162549c5178891fdb9c86d8eb7a68`.
  Both staging `bklxnl0ydxp37vkpa3i0aaf0` and production
  `s35fsnwh41udc6stv1xb1juu` finished. Exact one-container/source/digest,
  migration192 and publicUP verification passed in both. UI publication skipped;
  runtime600f/framework0.8.35 unchanged.
- New dependent spec1.7 and offer v10 were published on staging only. Their
  service graph is unchanged from1.6, fingerprint
  `45948e39d82f17196b3712941a2f70ef6255e30532bd839b1cf819a9577416c4`.
  Template1.7 itself is seeded byV192 on both control planes. Publication is
  measurement preparation, not production-fit approval or customer activation.
- Nine real-owner configuration-only checks passed: `dep-ccd7da65`,
  `ver-529e85bc`, hash
  `b5658d34e44cef349cbdfcb0b0589f9284d9698f8f5910b9b77814bc8f23154a`.
  Ownership/replay, reviewed placement, S3 redaction, immutable configuration,
  foreign denial and expected measured-policy admission denial were exercised.
  Installation `awi_pub_69a2777be578a7460912b217dded3665` remains DRAFT/inactive.
  Evidence `/private/tmp/01036-v10-configuration-evidence.json`.
- Six read-only hosted checks additionally passed at18:29UTC: valid customer
  session/version hash; binding GET anonymous401/customer403/staff-unbound404;
  database readback of required EXTERNAL_EXISTING/SHARED with no endpoint; and
  unchanged sole grant with all allocation counters zero. Evidence
  `/private/tmp/01036-external-binding-hosted-readback-20261018.json`.
- Read-only preservation check passed all30paused workloads after rollout,
  unchanged containers/images/configuration/layers/mounts and auto-deploy off.
  No provider serving authentication/write, runtime apply, activation, paid
  call, allowance reset or lifecycle email occurred. Existing Free-01 serving
  credentials remain missing; private input is tracked in Required for
  production. Keep Hetzner access open. Actual provider/indexing acceptance,
  measured admission, retained-index completion, production fit/promotion and
  completeP6/P7 remain open. UI extraction remains last and untouched.

### 2026-10-18 Accurate Runtime Contract And Unused Admission Cancellation

- Backend source `8432ec735` is verified live on both control planes at migration
  193. The native-token runtime remains source `d2b1a9792` / AI Fabric `0.8.36`;
  the image's framework JARs match Maven Central. No additional framework release
  is required for the template correction or cancellation work described here.
- The v11 configuration canary reached a real customer capacity reservation,
  then normal apply rejected an unsupported `GOVERNED_RESOLVER` declaration.
  Read-only reconciliation found no release, apply command, release binding or
  model allocation. The full physical reservation remains held until explicitly
  cancelled. This is a genuine contract defect, not an LLM routing failure.
- Commit `1cc4beaab` adds immutable template/spec 1.9 and offer v12. Only the
  unsupported extension declaration changes: existing in-chat confirmed actions,
  protected test-drive action, callback, handover, plugins, resource envelope and
  service images remain. The helper validates complete catalog-derived runtime
  requirements rather than extension labels alone. Backend CI 39169645144 passed;
  this source is not yet deployed at this checkpoint.
- Generic V195 cancellation work preserves published configuration, DRAFT apps,
  old consumer binding history, write-only secrets and external data. The staff
  command requires exact reservation/authorization fingerprints and explicit
  consumer/app revision expectations. Owning binding mutations, local no-effect
  recheck, durable receipt and physical-capacity release share one transaction.
  Concurrent apply or binding ownership returns a bounded conflict. Provisioned,
  uncertain, release-backed or active/formerly-active workspaces cannot qualify.
  `CANCELLED_UNUSED_NO_EFFECT` does not claim provider reclamation or reset the
  separate cumulative model campaign. Retained-index retirement remains blocked.
- Final parent integrated verification passed 282 tests across 12 classes with
  no failures/errors/skips,
  including PostgreSQL lock ordering, cancellation replay, JPA/JDBC rollback,
  native binding and INDEX preservation. A sandbox-only attempt could not access
  Docker and is not counted; the explicitly Docker-enabled run executed all
  cases. Full Spring startup, customer publishing/bootstrap and the actual 195-
  migration chain passed. Independent review closed with no open P1/P2 findings
  after adding cancellation-only strict fence JSON validation: omitted/null,
  overflow and coerced revisions cannot silently become revision zero. Shared
  fence deserialization remains unchanged. No live cancellation has been
  performed yet.
- Fresh pre-cancellation control-plane backups were actually restored in
  isolated local PostgreSQL 18 fixtures: staging report
  `/private/tmp/01036-local-control-plane-restore-XicZFNZm0a3N/report.json`,
  production report
  `/private/tmp/01036-local-control-plane-restore-SKy21x70E9yc/report.json`.
  These prove control-plane recovery, not deployed-runtime topology or financial
  accounting-store recovery. All 30 paused workloads were rechecked unchanged.
- Final fresh pre-rollout archives are in
  `/private/tmp/01036-control-plane-pre-195-final-20261018/`. Both were actually
  restored with the cached official PostgreSQL 18 digest, migration 193, and
  owned-fixture cleanup passed: staging `RW06yGzAXL25`, production `Dx7nNrLCTivQ`
  under `/private/tmp/01036-local-control-plane-restore-*/report.json`.
  A first no-pull invocation requested a missing local tag and failed before
  creating anything; it was not counted as successful restore evidence.
- Owner chose one production Serverless vector cluster per tenant/dealership,
  not a new cluster per release/version/user. The separately approved Frankfurt
  production cluster remains; staging keeps Free-01. The eight old Default
  Project clusters were removed by the dedicated prerequisite session under
  direct owner approval, not this implementation process. No duplicate deletion,
  new key/role, production model allowance or production promotion was performed.
  The same staging USD12/200,000-token/50-request campaign remains unchanged and
  has allocated zero so far.
- A replacement self-service bootstrap creates a new synthetic tenant. Reusing
  Free-01 for that tenant requires an explicit owner answer; current retained
  binding reuse remains SAME-TENANT only. UI extraction is still last and has
  not been advanced to hide these backend/live-readiness boundaries.

### 2026-10-18 Owner Boundary Correction And Parallel Handoff

- Owner rejected provisioning customer dealership inventory/lead systems inside
  the AI deployment. Investigation confirms the new package bundles a simulator
  and the dealership demo backend with business PostgreSQL. Existing independent
  demo fixtures may remain, but customer AI composition must use external DATA/
  ACTION integrations. Earlier package/gate success is not architecture acceptance.
- [010.44 correction plan](010_44_CUSTOMER_SYSTEM_INTEGRATION_BOUNDARY_CORRECTION_PLAN.md)
  prepares separate parallel workstreams and
  [ownership audit](010_44_CUSTOMER_SYSTEM_OWNERSHIP_BOUNDARY_AUDIT.md) records
  source evidence, limitations and required verification. No correction has been
  implemented by this documentation/audit task. Invalid customer apply/promotion
  is operationally paused; a durable offer/policy block still requires work.
- Wider source audit confirmed Railway project-delete-first and managed-vector
  resource/collection adoption-to-delete risks without sufficient ownership proof.
  Receiver initialization has split local/provider inventory authority and weak
  READY evidence, but no duplicate vector pipeline was proven. AI operational
  SQL hosting remains an explicit decision; current typed BYO SQL is incomplete.
- Backend `cab320545` / exact digest
  `sha256:ded22d9042b49e4551348ec797544accfd75d90470551b4ec6d24b753716f6bf`
  is verified on both control planes, migration195/publicUP. Runtime remains
  `d2b1a9792` / AI Fabric0.8.36. Strict HTTP verification found a malformed staff
  cancellation-body response incorrectly returning401; a scoped local fix plus
  embedded HTTP regression passed61/61 but remains uncommitted/undeployed.
- Read-only metadata receipt at22:47:42UTC,
  `/private/tmp/01044-resource-boundary-audit-20261018.json`, found zero managed
  graphs on both control planes and zero releases/provider allocations for
  `dep-6e29bab9`. Its exact revision0 reservation is still RESERVED. No valid
  cancellation, provider mutation, model call or customer apply occurred in this
  audit. No actual customer data loss was established.
- Retain all stopped workloads, external source data, Free-01 and approved
  production vector resource. Do not run old v12 topology publishers unchanged,
  reset allowance/reservation state, or close Hetzner access contrary to the
  owner's instruction. UI extraction remains last and independently reversible.

### 2026-10-19 AI-Only Ownership Correction Checkpoint

- Owner explicitly approved optional hosted AI-state PostgreSQL, with a genuine
  customer-provided PostgreSQL alternative. Neither posture authorizes customer
  inventory, CRM, lead or booking infrastructure. Customer S3 remains external
  and read-only; simulator and dealership business receiver are independent
  external integrations, not customer AI dependencies.
- Local commits: `00f3852c5` scoped cancellation HTTP errors; `8b11c4217`
  independent demo business-state/source-authority correction; `5cef0210a`
  C1-C4 backend/catalogue/storage/external-binding foundation. No push or hosted
  upgrade is implied. See the dedicated [010.44 execution record](010_44_CUSTOMER_SYSTEM_BOUNDARY_EXECUTION_RECORD.md)
  for exact source/test boundaries rather than adding overlapping test counts.
- C4 provider/retirement qualification passes316 cases including eight real
  PostgreSQL cases. External binding/entitlement concurrency passes40. Managed
  dependency ownership passes109 including eight PostgreSQL cases. Package
  support passes136 with its real local restore case enabled. All these runs
  have zero failures/errors/skips. Full actual-module regression remains running.
- Fresh approved private Platform backups were restored on isolated local
  PostgreSQL18. Both also pass migration195 through201, normal validation of all
  201 migrations, original-record preservation across21 tables and empty new
  customer/shared-service ownership ledgers. No historical adoption/backfill,
  Flyway repair or hosted database change occurred.
- Independent follow-up found the same ownership category in older shared-
  platform-service provisioning. C5 implements a separate service-registry
  journal and guarded lifecycle. That source follow-up is still in progress;
  metadata-only historical bindings must not grant mutation/delete authority.
- Authenticated metadata confirms current Git origin is public. Publishing
  private product source/images is held for the owner's repository destination
  decision; no public push, registry publication or visibility change occurred.
  Sending that prerequisite to `Required for production` was denied by permission
  review, so no indirect retry was made. The owner can answer in the main task.
- Hosted AI-only offer publication, fresh customer apply/index/actions/handover,
  target generation/promotion, capacity/load evidence and P7 certification remain
  open. Existing reservation, external vector bindings, source objects, stopped
  workloads and cumulative financial campaign remain preserved. UI extraction is
  last, after non-UI hosted acceptance, and is not advanced to hide these gates.

### 2026-10-19 Final Local Ownership Qualification

- The merged backend is green:3,343 tests across350 classes, zero failures,
  errors or skips, using the actual module/POM and Docker-backed cases. Source
  stayed fixed for the whole run. Offline backend packaging also passes. Logs:
  `/private/tmp/01044-final-merged-backend.log` and
  `/private/tmp/01044-final-merged-backend-package.log`.
- Final actual staging/production backup rehearsals separately pass2 cases,
  normal195-to201 migration/validation and preservation of21 tables. Final V201
  SHA256 is `707b3c3e10a560784f9c2e8d31a2cd2e8717ace89be88693067c79e19a8b54ae`.
  Ownership ledgers remain empty for historical resources. No hosted upgrade,
  ownership backfill or customer data mutation is implied.
- Local source commits are `00f3852c5` cancellation HTTP errors, `8b11c4217`
  independent demo correction, `5cef0210a` AI-only composition/storage/external
  binding foundation and `12fb4e4b2` shared-service/final ownership correction.
  Independent re-review closed the confirmed P1/P2 findings. AI Fabric remains
  0.8.36; no framework change or release was made.
- Customer AI resources are runtime/connector/workers, derived vector evidence,
  an explicitly selected managed or customer-provided AI-state PostgreSQL, and
  optional AI conversation Inbox. Simulator, dealership inventory and business
  action systems remain independent DATA/ACTION integrations. Customer S3 stays
  read-only and externally owned.
- Publication is still held: the only configured Git origin is public, while
  Platform product code is intended private. Owner must supply the private
  destination or explicitly approve public publication. The question was asked
  in the main task; no cross-task permission workaround was attempted.
- Hosted offer/apply/index/actions/handover, immutable target-generation
  replacement, capacity/load, production promotion and P7 remain open. Customer
  UI functional acceptance/extraction is not certified by these backend results;
  extraction stays last. All execution sessions/agents are closed, so no local
  background task is required to resume. Preserve the held reservation, same-
  tenant Free-01, approved production cluster, source data, cumulative grant,
  stopped workloads and owner-approved open Hetzner access.

### 2026-10-19 Hosted Ownership Correction And Customer Metadata Gate

- Owner confirmed the existing public repository is intentional. The earlier
  publication hold is superseded; auth material remains ignored/private.
- Both control planes verify source `ceaacf12e`, immutable backend digest
  `f59d0459...20d11`, migration201 and public healthUP after fresh verified
  Platform-only archives/rehearsal. No historical ownership backfill.
- Corrected AI-only offer metadata is published on staging, with actual staff
  review, exact artifact/spec/target pins, owner invitation/acceptance and
  independent customer catalogue readback. Optional managed AI-state SQL and
  Basic Inbox are distinct from external stock/business systems. No business
  database, dealership backend or simulator is in its dependency graph.
- Real customer `dep-70b3d2c1` / `ten-ad10424d` passes all eight configuration-only
  checks, including read-only S3, immutable version/app/consumer replay and
  foreign-customer denials. Its app remains draft and releases/graphs remain0.
  Support175 and independent demo161 tests pass with zero skips. Source pushed
  as `81b9e5b6f` and separately `f7b7bd58c`; only the affected demo image is rebuilt.
- The exact old reservation remains RESERVED/revision0 and Free-01 is not moved.
  Cancellation/handoff questions still need specific owner answers; no rejected
  operation was retried. Cumulative model allocations remain0 and no second
  financial grant was created. Runtime stays Framework0.8.36 with no new release.
- Non-UI hosted runtime apply/index/actions/handover, capacity/load, generation
  replacement, production self-service/promotion and P7 remain open. Customer UI
  extraction remains last, uncommitted/undeployed and independently reversible.
  Exact receipts and remaining gates are in the010.44 execution record.

- Follow-up independent fixture gate: demo API upgraded/verified at `f7b7bd58c`,
  normal database1-to4 migration, all six vehicles/73 prior receipts and original
  trust/secret fingerprint preserved. Live401/405/Allow POST and external scoped
  stock/media preflight pass. The replacement customer is not silently trusted.
  Source app stays commit-pinned; Coolify does not support its attempted in-place
  conversion/newer metadata field, so one cached exact-source build was used.
  No new app/database, business write, webhook, model call or framework release.
- Both provided S3 identities now pass direct unversioned read/three-document/
  SSE-S3 and cross-prefix403 checks. No source write/index, mutation-denial probe,
  second grant or customer-runtime readiness claim. Specific held-reservation/
  Free-01 decisions and actual runtime acceptance still gate further rollout.
