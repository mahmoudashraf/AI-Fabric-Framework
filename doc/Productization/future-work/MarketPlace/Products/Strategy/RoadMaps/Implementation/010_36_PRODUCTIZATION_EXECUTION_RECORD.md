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
  Source `54c2a36522b9dbcd2a44193cf43e19bd77703b7e` is live on both Platform
  backends. The hosted legal customer request reaches the measured-policy gate;
  an old client-supplied `manifestId` is rejected as an invalid contract.
  End-to-end provisioning remains intentionally unexecuted until sufficient
  customer capacity is measured and approved.
- P6: not started. No editor extraction or customer layout change before the
  verified functional baseline. Preserve the frozen STAFF-001 through STAFF-039
  capability register and source snapshot.
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
- `Platfrom/ui` remains unchanged. P6 editor extraction and customer-layout
  composition have not started.
- Frozen staff baseline snapshot remains unchanged. Source navigation comparison
  is not browser parity certification. `git diff --check` passed.

Logs reside under `/private/tmp/01036-*`; copy only final sanitized evidence to
the verification-support directory. Source baseline started at
`5206acd97423ee857b5a60c1fe370633bf3fd5a9`; the latest locally and hosted
verified source is committed on `Platform-V11` through
`54c2a36522b9dbcd2a44193cf43e19bd77703b7e`. The reviewed package metadata is
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
hosted real-provider Engage action-time auth, accessibility and capacity gates.
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
