# LoomAI Engage Productization Execution Record

Date: 2026-10-16. Governing scope: section 10 of
[010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md).
Source baseline: `5206acd97423ee857b5a60c1fe370633bf3fd5a9`.

## Execution Gates

- P0: source contract implemented and integrated; prerequisite acceptance is in
  progress. The customer-owned S3 canary passes exact bucket posture, explicit
  unversioned state, identity separation, prefix isolation, bounded object reads
  and write/delete denial. The staging S3 binding is persisted for the fresh v5
  customer lifecycle. SMTP authentication is live and the admission notice
  transport is explicitly configured on staging. Fresh guest measurements prove
  that neither current host has an admissible complete topology envelope with
  headroom: staging lacks RAM and production lacks CPU. No policy or grant was
  fabricated to bypass that result.
- P1-P5: source foundations are integrated. The immutable three-service managed
  dependency package is published and read back on both hosted control planes.
  Customer admission no longer accepts a manifest identity or capacity material:
  the backend derives an immutable manifest from the exact offer, version,
  target storage, runtime authority and reviewed managed dependency topology.
  Source `c41c02ace594a094f5afb446685904fa8382534d` is live on both Platform
  backends, with template 1.5.0 and reviewed offer v6 published. The hosted legal
  customer request previously reached the measured-policy gate;
  an old client-supplied `manifestId` is rejected as an invalid contract.
  End-to-end provisioning remains intentionally unexecuted until sufficient
  customer capacity is measured and approved.
- P6: local UI implementation and fixture-based checks are in progress, but the
  extraction has not been committed or deployed. Hosted rollout remains gated
  on the verified functional baseline. Preserve the frozen STAFF-001 through
  STAFF-039 capability register and source snapshot; keep the UI-only rollback
  independent from backend and runtime corrections.
- P7: not started. Fresh production-customer lifecycle certification is required
  after the external capacity prerequisite is closed.

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
