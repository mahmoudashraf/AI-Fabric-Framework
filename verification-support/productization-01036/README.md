# 010.36 Productization Verification

## Current 010.44 Ownership Boundary

The bundled simulator/engagement-receiver/business-PostgreSQL composition is
superseded for new customer publication. Do not use the historical checkpoints
below as instructions to recreate it. V196 adds new immutable external-system
templates; it does not rewrite applied migrations, old offers or held capacity.
See [the external integration contract](EXTERNAL_DEALERSHIP_INTEGRATION.md).

The actual Spring customer/bootstrap/binding/compiler tests and full actual
backend **3,343-test, zero-skip** gate now pass. The obsolete unconditional local
bootstrap blocker is removed. The publisher supports a bounded attended
**MANAGED_POSTGRES + managed Basic Inbox** canary through existing staff APIs:
read-only `prepare`, digest-reviewed single `publish`, then separate entitled
customer `verify`. See [the exact inputs and commands](EXTERNAL_DEALERSHIP_INTEGRATION.md#attended-offer-publication).
It rechecks immutable manifests/artifacts, Inbox spec, target fingerprints and
placement; it never provisions a fixture business database/simulator or releases
any existing hold. Unknown POST outcomes stay unknown with no automatic retry.
There is no staff offer GET: full publication acceptance needs the supported
customer catalogue readback after a separately authorized invitation/acceptance.

`buildOfferProposal()` still represents both real SQL modes, their distinct
demands and scoped DATA/ACTION requirements without claiming live readiness.
The attended offer explicitly narrows its immutable storage choices to managed
SQL; it cannot advertise BYO-hosted replacement acceptance. Broader BYO and
external-Inbox publication remain separately gated. Staging activation/recovery
stay available behind existing authorization/admission. Existing promotion
operations remain declared for the same customer/deployment journey, but their
execution still requires all production target, binding, admission, financial
and readiness checks. Real provider binding, measured
capacity/admission, hosted persistence/indexing/actions/handover, production
promotion and final UI acceptance remain open. No success flags can bypass them.
Optional managed Basic Inbox is an AI-only service; ordinary external Inbox has
no managed spec and still needs its real target callback/operator contract.

Initial setup can visibly defer an
ACTION whose declared source projection depends on an unavailable explicitly
required DATA install; ordinary compilation remains strict. The post-association
initializer enables alias-only DATA/ACTION installs and resyncs. This creates no
external account binding and is not hosted provider-access proof. See the
integration contract for the exact unresolved-requirements path and API protocol.

## Read-Only External Fixture Preflight

`external-fixture-preflight.mjs --live` accepts private configuration on stdin.
It checks reviewed HTTPS hosts, scoped stock authentication/pagination/media,
the exact receiver commit, unauthenticated denial, authenticated405/Allow,
and explicitly supplied inspected provider/scope configuration. Credentials
never enter its report. Its only POST is the stock authentication exchange;
it never executes a business action, sends a notification, reconciles a source,
provisions a resource or calls a model. Use a private input file, not argv:

```sh
node verification-support/productization-01036/external-fixture-preflight.mjs --live \
  < /private/path/reviewed-external-fixtures.json > /private/path/preflight-report.json
```

Create input/output with private permissions and keep them outside Git. Optional
`index` input must identify the exact customer/tenant/deployment, source version,
connection profile and protected binding; current, complete source/work counts
must converge with the scoped provider inventory. Without it, the report says
`AI_CANARY_INDEX_NOT_OBSERVED`. A successful405 probe declares a protected POST
contract, not executed-action authorization. Inspected configuration is not
remote acceptance proof. This preflight cannot certify indexing, write actions,
signed notification delivery, real Auto Trader access or overall readiness.

## Historical Native Token Checkpoint

Local corrected candidate: offer v12/template1.9/spec1.9, retaining the exact
inspected native-token runtime. Its20 framework jars match released0.8.36 Central
bytes. Both control planes remain on V193/source8432ec735. V194/1.9 is not yet
deployed; it removes only an unsupported Governed Resolver API declaration,
not ordinary chat actions or their confirmations. Old publications are immutable.

The hosted v11 canary's TOKEN binding and real customer reservation/replay pass.
Apply correctly rejects its unsupported extension before creating a release or
allocating provider credits. The whole reservation remains charged. Generic
unused-reservation cancellation is under implementation; no direct database
release, clear/drop or guessed absence is allowed. Actual hosted workload
acceptance remains open. The spec's graph and service image pins are unchanged.
New publication guards consume the backend behavior/extension catalogs and
check every derived capability, endpoint, migration and verification pack,
including specialist-bundle identities, against the actual runtime declaration.
Real PostgreSQL migration/manifest regression3PASS and complete support103PASS/
0skip, including the opted-in synthetic Docker test. The exact pinned engagement
version preserves protected test-drive and anonymous callback confirmation.

Backend476, runtime425, provider accounting140, connector76 and vectorization32
checks pass with zero skips against public AI Fabric0.8.36 from Central. Local
packaging passes; this is not CI-image, hosted runtime or capacity acceptance.
The published v10 offer below still pins the older immutable runtime/template1.7;
it is not token-capable proof. The corrected new offer/spec must pin template1.9
and the same inspected CI image before apply. No framework or runtime rebuild
is needed for the template contract correction.

Explicit local credential readiness:
`GET /api/deployments/{deploymentId}/versions/{versionId}/targets/{targetProfileId}/secret-usage`.
Operator access, owned published version and exact active target are required.
This validates local binding/refs/fingerprint, not provider health. No Zilliz
probe or credential value is returned. Unscoped summaries remain not-ready for
external bindings; selected-scope caller wiring remains tracked work, not a
reason to infer production readiness from staging credentials.

This verifier proves the document-source boundary for the first dealership
offer without provisioning or modifying an AWS resource. It is intentionally
separate from runtime indexing and from the P6 UI extraction.

## Contract under test

- one private native AWS S3 bucket in `eu-north-1` at
  `https://s3.eu-north-1.amazonaws.com`;
- versioning is explicitly disabled;
- separate staging and production identities are restricted to
  `dealership/staging/` and `dealership/production/` respectively;
- each prefix exposes at least three non-empty UTF-8 `.txt` objects;
- each identity can list, inspect and read only its own prefix;
- cross-prefix list, head and get requests are denied;
- put and delete permissions are denied.

The verifier never records access keys, principal names, object keys, object
contents, ETags or raw AWS errors. Evidence contains only scope labels,
non-secret endpoint facts, byte counts and SHA-256 fingerprints.

This is verification support, not a second storage implementation. Its contract
matches the existing deployment binding primitive:

- connector type `S3_COMPATIBLE_OBJECT_STORAGE`;
- customer-managed, read-only source storage;
- exact target-specific endpoint, region, bucket and prefix;
- `pathStyleAccess=false` for native AWS S3;
- `objectVersioningAvailable=false` for this deliberately unversioned canary;
- removal of a binding never deletes source objects.

The Platform remains responsible for putting binding values into its write-only
managed-secret boundary and freezing the selected target binding in a release.
This verifier only produces independent, sanitized acceptance evidence for the
supplied customer-owned storage boundary.

## Run

AWS CLI v2 is required and must be available on `PATH`. Before contacting
AWS, the verifier checks that the installed CLI exposes conditional `If-Match`
inputs for both `put-object` and `delete-object`; without those preconditions,
the denial probes do not run. Copy `.env.example` to the ignored `.env`, add
the two credential pairs, set its mode to `0600`, then run:

```bash
chmod 600 verification-support/productization-01036/.env
node verification-support/productization-01036/verify-s3-canary.mjs \
  --env-file verification-support/productization-01036/.env
```

An existing ignored key-value document may also be passed with `--env-file`;
unrelated Markdown lines are ignored. Process environment values take
precedence over file values. Use `--output /absolute/path/report.json` to choose
the evidence destination. The default is an ignored timestamped file under
`evidence/`. On POSIX systems, an env file is rejected unless it is a regular,
non-symlink file with no group or other permissions. Credentials may instead be
supplied directly through the process environment, and are removed before every
AWS CLI child process except for the one exact staging or production identity
being verified. Inherited AWS profiles, role/container credential sources,
endpoint override variables and region overrides are scrubbed; remote calls use
the reviewed `eu-north-1` region and the exact S3 endpoint from this contract.
Every AWS CLI invocation also has a bounded 30-second process timeout.

The identities need these read operations for the canary:

- `s3:ListBucket`, conditioned to their exact prefix;
- `s3:GetObject` for HEAD and GET requests in their own prefix;
- `s3:GetBucketVersioning` so the no-versioning safety precondition can be
  verified before the non-mutating delete-denial probe.

The verifier also calls `sts:GetCallerIdentity` to prove that staging and
production resolve to different principals. AWS documents that operation as
requiring no explicit permission. No object key or principal identity is
written to evidence; only SHA-256 fingerprints are retained.

The write- and delete-denial probes target an existing object with a randomly
generated non-matching `If-Match` ETag. An allowed request therefore fails its
precondition rather than changing content. Both probes perform a follow-up head
check. Any result other than an explicit access denial fails the gate. The
verifier also independently requires S3 to report that versioning has never
been enabled, matching the runtime binding contract.

Object listing is non-paginating and bounded to one more than the 100-object
canary maximum, so an unexpectedly broad prefix fails closed without scanning
the bucket.

Run fixture tests without AWS access:

```bash
node --test verification-support/productization-01036/test/*.test.mjs
```

## Configuration-only customer canary

`verify-customer-configuration.mjs` uses a real, already-entitled customer
owner session. It checks reviewed-profile first-app creation before a runtime
exists, exact-command retries, origin projection, early activation denial,
target-scoped read-only S3 binding, immutable-version publication, and direct
foreign-customer denial. It never invites a customer, grants entitlements,
approves capacity, calls runtime apply, provisions a provider resource, or
modifies a source object. Run it only against a designated staging canary.

This **does write control-plane metadata** and persists the supplied staging
storage credentials through the Platform's write-only secret boundary. An
explicit `--allow-metadata-writes` flag is required. The accepted offer and
staging target must already exist. The foreign IDs must identify existing,
authorized-for-testing resources owned by another customer; invented IDs are
not evidence of cross-customer isolation. Keep the same run identity, names,
slug and payload when retrying. Do not choose another operation key to conceal
a conflict or a partial run. The resulting draft/app remain unactivated and
must not be mistaken for a live customer deployment.

Supply these values through the process environment or a private regular
`0600` env file:

```dotenv
ENGAGE_CANARY_PLATFORM_URL=https://your-staging-control-plane.example
ENGAGE_CANARY_EMAIL=<existing-canary-owner-email>
ENGAGE_CANARY_PASSWORD=<private-password>
ENGAGE_CANARY_CUSTOMER_ID=<existing-customer-id>
ENGAGE_CANARY_OFFER_ID=<reviewed-offer-id>
ENGAGE_CANARY_TARGET_PROFILE_ID=<customer-staging-target-id>
ENGAGE_CANARY_AI_STATE_STORAGE_MODE=<MANAGED_POSTGRES-or-CUSTOMER_POSTGRES>
ENGAGE_CANARY_OPERATION_PREFIX=<stable-canary-operation-prefix>
ENGAGE_CANARY_RUN_ID=<stable-run-id>
ENGAGE_CANARY_DEPLOYMENT_NAME=<stable-deployment-name>
ENGAGE_CANARY_CONSUMER_ID=<stable-consumer-id>
ENGAGE_CANARY_APP_NAME=<stable-app-name>
ENGAGE_CANARY_APP_SLUG=<stable-app-slug>
ENGAGE_CANARY_FOREIGN_DEPLOYMENT_ID=<existing-other-customer-deployment-id>
ENGAGE_CANARY_FOREIGN_CUSTOMER_ID=<existing-other-customer-id>
# Optional only when the reviewed offer publishes multiple profiles:
ENGAGE_CANARY_WORKSPACE_PROFILE_ID=<exact-reviewed-profile-id>
```

Use the S3 env contract above for the separate `--s3-env-file`. The customer
canary reads only its **staging** credential pair. Node.js 20.12 or later is
required for the built-in env-file parser. Example:

```bash
node verification-support/productization-01036/verify-customer-configuration.mjs \
  --env-file /private/path/customer-canary.env \
  --s3-env-file /private/path/s3-canary.env \
  --allow-metadata-writes \
  --output /private/path/customer-configuration-evidence.json
```

`--verify-admission-denial` is an additional, explicit probe for an environment
known to have **no approved measured policy**. It expects the exact `412` /
`MEASURED_POLICY_REQUIRED` response and zero reservations before and after.
Another denial is a failed test, not a substitute pass. If reservation
unexpectedly succeeds, the verifier records its ID, stops, and never calls
apply or cleanup. Inspect that reservation before retrying; this option is not
appropriate once measured admission has been approved.

Evidence is checkpointed after accepted stages and contains only identifiers,
configuration hashes and named checks, never cookies, passwords, credentials,
public auth configuration or source content. A partial run is explicitly
`FAIL`. Fixture tests cover drift, denied ownership, secret leakage, unexpected
reservation success, and transport-error sanitization. A passing canary proves
these control-plane contracts, **not** hosted indexing, runtime functionality,
capacity readiness, production promotion, or full 010.36 certification.

## Independent Host Baseline

Capacity preparation does not require vector serving credentials. Use the
read-only host sampler with an explicit SSH identity and a new private output:

```sh
node verification-support/productization-01036/measure-host-baseline.mjs \
  --environment staging --host 46.224.145.148 --user loomops \
  --identity "$HOME/.ssh/loom_coolify_hetzner_ed25519" \
  --samples 181 --interval 5 --output /private/tmp/new-staging-baseline.json
```

The sampler uses CPU-counter deltas, interval memory/swap observations and
filesystem statistics for `/var/lib/docker`. It rejects missing/reset counters,
topology drift, incomplete windows and interrupted cadence. SSH host-key checking
is mandatory, outputs are exclusive mode0600, and transport failures are redacted.
It starts no application and changes no remote state. Swap is never counted as
usable RAM. Every report explicitly says `candidateWorkloadIncluded=false` and
`policyApproval=false`: a current-fleet baseline cannot certify customer load,
startup/promotion overlap, vector quota or database capacity, and it never
publishes admission policy or allocates a grant.

## Local Authentication Browser Acceptance

```sh
node --test verification-support/productization-01036/auth-acceptance-browser.test.mjs
```

This separate nine-scenario gate builds the actual standalone shell, installer,
generic experience pack and widget into a temporary directory. It requires
existing workspace dependencies and Playwright Chromium; it does not install
them or overwrite production assets. Browser traffic outside its loopback
fixture server is blocked. The fixture provider/broker/runtime exercise typed
protected-capability challenges, cancelled/failed/delayed login, new authenticated
conversations without replay, logout/account switching, renewal, expiry and
broker denial. Foreign-installation state is checked for preservation.

Reports retain asset hashes, requests and explicit live gaps. Passing proves
actual frontend behavior against fixtures, not real-provider cancellation,
runtime authorization/effects or bearer revocation latency. P4 remains open
until that hosted evidence exists. The harness is outside image-build watch
paths and the lightweight `test/*.test.mjs` gate, so adding verification does
not publish replacement backend/runtime images.

## Local Control-Plane Restore Drill

```sh
RUN_01036_LOCAL_CONTROL_PLANE_RESTORE=true \
node verification-support/productization-01036/run-local-control-plane-restore.mjs \
  --dump /private/tmp/01036-control-plane-pre-retained-external-binding-20261018/staging.dump \
  --docker-host "unix://$HOME/.docker/run/docker.sock" \
  --postgres-major 18 --expected-migration 191 \
  --image postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873
```

Use a private custom-format archive and an already cached official matching-major
image. The verifier never pulls images or accepts remote Docker/database targets.
It creates new labelled local fixtures with network disabled, no published ports,
no backend/workers and no host bind mounts; raw restore diagnostics are discarded.
The official `pg_restore --list` validates source/tool versions before data restore.
Typed schema, migration chain, aggregate counts, constraint/index checks and an
unchanged source hash are required. Cleanup verifies exact fixture identity and
isolation before removing only those resources. Reports are mode0600.

The two actual pre-V192 staging/production archives passed on PostgreSQL18 with
120 public tables, 37 selected typed columns, counts across 15 core tables and
191 successful migrations. This proves local database recovery of those archives,
not V192/current-head recovery, owner/ACL parity, secret decryption-key availability,
application bootstrap, hosted recovery or runtime/index/object-store restoration.

To include the synthetic real-Docker regression in the support gate:

```sh
RUN_01036_LOCAL_CONTROL_PLANE_DOCKER_TEST=true \
CP_RESTORE_TEST_DOCKER_HOST="unix://$HOME/.docker/run/docker.sock" \
node --test verification-support/productization-01036/test/*.test.mjs
```

That regression uses a separate synthetic PostgreSQL16 fixture. It does not
substitute for the actual private PostgreSQL18 archive results. Without explicit
opt-in, it is visibly skipped, never silently counted as executed.

## Historical Runtime And Financial Checkpoint

The former offer publisher prepared a new immutable v10 staging measurement
candidate, using template/spec 1.7 and the same inspected guarded runtime image.
It does not rewrite old offers or provision a runtime. The candidate declares
an explicit integer `openaiMaxTokens=2048`, now accepted by the generic
Marketplace template contract, and retains every service, binding and RAM/disk
allocation. Its complete 2,750mCPU topology is a candidate to be measured, not
approved production capacity. The four
provider-budget capability, endpoint, migration and verification declarations
are required in addition to the exact reviewed image digest and source commit.
Publishing an offer is not approval of host capacity or provider spending.

Hosted checkpoint, 2026-10-18: both control planes run backend `58bf670de`,
migration 192, with exact-image and public-health verification. The v10 offer
and spec 1.7 were published on staging only; the nine-check real-owner
configuration canary and six additional read-only binding checks passed.
Free-01 allocation is approved, but its existing serving credentials are still
missing. No external binding, runtime apply, paid traffic or grant allocation
has occurred. See [the retained-vector checkpoint](EXISTING_VECTOR_CANARY_CHECKPOINT.md)
for exact rollout receipts, evidence and unresolved acceptance gates.

Financial allowance comes from an explicit staff-reviewed lifetime grant,
separately from the physical R7 admission manifest. Automatic preparation
allocates the complete grant to one deployment; exact retries reuse it, a
second deployment cannot reuse it, and retirement does not refund it. A partial
staff allocation cannot silently turn its remaining balance into a fresh
automatic allowance. Unsupported model/rate bindings must be rejected before
debit. Customers cannot submit credits through query context or public APIs.

The private runtime starts with paid calls denied. Provisioning initializes its
deployment-local ledger, commits the returned store UUID in the control plane,
and only then activates that exact allocation. Missing/replaced stores require
explicit reconciliation, not automatic credit initialization from environment
variables. Query/suggestion ingress and indexing embeddings share the durable
guard. Unknown native usage retains its full reservation. The separate bounded
embedding-worker profile permits verified local ONNX only, not unguarded paid
worker calls.

Verify archive/image provenance, private authorization, uninitialized readiness,
actual restart persistence and native pre-send bounds before an attended live
canary. Use only the real approved limits. A skipped provider probe, a healthy
control plane or successful offer publication is not evidence of paid runtime
verification. On 2026-10-18 the owner approved the pending 200,000-token ceiling
and revised the same staging campaign's total provider spending cap to USD 12,
including embeddings. The 50 interactive-request limit remains. Prior usage,
retries and reservations still count; this is not a new campaign or a reset.
These historical canary limits are not default customer entitlements or
authority for another run. The exact scope and current counters must be
verified before any provider call.

The prior template 1.6 requested a dedicated managed Zilliz Serverless cluster,
a separate effect not authorized by model-token approval. On 2026-10-18 the
owner explicitly approved the existing Free-01 for isolated synthetic staging
use at no additional vector cost, retaining both cluster and data. Template
1.7 instead requires an explicit retained external-vector binding, with no
seeded endpoint or credentials and no managed creation fallback. Existing
cluster metadata or an empty collection list alone never grants permission.
See [the execution checkpoint](EXISTING_VECTOR_CANARY_CHECKPOINT.md) for missing
serving credentials and the honest retained-index retirement blocker.

The staff-only API is `POST /api/deployments/{deploymentId}/targets/{targetId}/existing-vector-binding`.
It accepts an exact published `versionId`, `clusterId`, `projectId`, `regionId`,
`expectedDeploymentOption`, `databaseName=default`, existing serving `username`
and `password`, and an `approvalReference`. Read back the redacted summary with
GET on the same path. Changed replay is rejected, not treated as credential
rotation. Serving secrets are target-owned; the management key stays on the
control plane for GET-only identity readback. A missing, changed, foreign or
unbound scope must stop generation, with no global-key fallback. Do not store
credential-bearing request JSON in committed fixtures, logs or screenshots.

For an opt-in synthetic export/restore of the actual guarded image's budget
store, see [the local rehearsal guide](java/README.md). It uses cached exact
images and an internal network, without provider calls or live database access.
This proves only that subsystem, not hosted whole-topology recovery or safe
reactivation after restoring an older financial snapshot.

## AI-Only Package And Offer Helpers

`publish-simulated-dealership-package.mjs` keeps its historical filename, but
accepts only an explicit `MANAGED_INBOX=true|false` selection. False produces no
managed spec and makes no platform requests. True publishes only the new
Inbox-only spec, requiring an already registered, staff-promoted exact image,
commit and dedicated ownership-manifest hash. It never registers or promotes
artifacts implicitly and never publishes the former three-service graph.
`inboxArtifactRegistration()` produces the separate reviewed registration body.

`publish-simulated-dealership-offer.mjs` is checkpointed and makes no requests.
The local proposal helper preserves runtime digest/commit, native TOKEN,
provider-budget guards, catalog capabilities, full action set and exact target
fingerprints. It requires explicit dealer identity/media hosts, managed-Inbox
selection and AI-state storage mode. It does not manufacture binding authority,
document consent, physical policy approval or production readiness.

`aiStateStorageTopologies` declares exact per-mode demand. The offer `topology`
is the maximum reviewed envelope; admission must select actual demand from the
immutable version's `aiStateStorageMode`. Customer PostgreSQL is not charged as
a hosted database. The customer-business database always remains external.
