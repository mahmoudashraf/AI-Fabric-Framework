# 010.36 Productization Verification

## Native Token Checkpoint

Backend476, runtime425, provider accounting140, connector76 and vectorization32
checks pass with zero skips against public AI Fabric0.8.36 from Central. Local
packaging passes; this is not CI-image, hosted runtime or capacity acceptance.
The published v10 offer below still pins the older immutable runtime/template1.7;
it is not token-capable proof. A new reviewed offer/spec must pin template1.8 and
the inspected new CI image before apply.

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

## Guarded runtime and financial allowances

The current offer publisher prepares a new immutable v10 staging measurement
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

## Reviewed simulated-dealership dependency package

`publish-simulated-dealership-package.mjs` registers the exact reviewed GHCR
digests as `MANAGED_DEPENDENCY` source artifacts, promotes them through the
production review channel, and publishes one immutable dependency topology for
`mkv-template-simulated-dealership-experience-v1-7`. It retains the exact 1.6
dependency graph and requires a separately approved retained external-vector
binding before apply. This does not approve production fit or promotion. A production-reviewed
artifact is valid in customer staging and production; a staging-only artifact
is never accepted for production.

The topology owns one simulator, one engagement receiver with PostgreSQL, and
one Basic Inbox with persistent deployment-local storage per customer target.
It emits the 14 exact
server-owned connector bindings used by the template. The target authority
callback-signing secret remains distinct from the Inbox-owned webhook-signing
secret. The script does not accept or emit dependency credentials.

Run only against a Platform backend that includes the managed-artifact role and
readback contract:

```bash
PLATFORM_BASE_URL=https://api.loomai.pro \
PLATFORM_ADMIN_API_KEY='<private>' \
MANAGED_DEPENDENCY_REVIEW_REFERENCE='<review-ticket-or-commit>' \
node verification-support/productization-01036/publish-simulated-dealership-package.mjs
```

The command is convergent: it reuses only exact digest/commit/role artifacts,
promotes them to `production`, and refuses to replace a different active
topology. Sanitized evidence is written under the ignored `evidence/` folder.

## Reviewed customer offer

`publish-simulated-dealership-offer.mjs` publishes the immutable v7 customer
offer only after reading both target profiles and confirming the complete
reviewed runtime envelope: runtime, connector, vectorization runner, runtime
PostgreSQL capacity and distinct CPU assignments. Missing or drifted target
settings fail before offer publication.

Target pins share one canonical identity with placement and admission, including
the provider, environment, credential reference and complete configuration.
The v7 offer is a new immutable publication; it does not rewrite earlier offer,
placement or canary records and does not accept earlier fingerprint recipes.
Target configuration numbers in this shared identity must be exact JavaScript-
safe integers. Decimal resource quantities remain explicit strings (for example,
CPU `"0.5"`); fractional/unsafe JSON numbers fail instead of being rounded or
producing different hashes. This restriction is local to target identity, not
other evidence or workspace-profile hashing.

The runtime is pinned to the CI-published AI Fabric `0.8.35` image, and the
managed dependency specification pins the matching Basic Inbox image. The
offer carries reviewed public workspace profile payloads as well as their
canonical setting hashes. An entitled new customer can therefore configure
its first Engage app without copying a previous customer's installation.
Private credentials remain write-only bindings and are never profile payloads.

The offer stores two separate capacity contracts. `topology` is the exact
minimum full-deployment demand that every admission manifest must cover;
`limits` is the reviewed maximum ceiling. Both include the platform-managed
services, two databases, two vector indexes and persistent storage. Customer
commands cannot supply or reduce either contract.

```bash
PLATFORM_BASE_URL=https://api.loomai.pro \
PLATFORM_ADMIN_API_KEY='<private>' \
ENGAGE_SUPABASE_PROJECT_URL='<reviewed-project-url>' \
ENGAGE_SUPABASE_PUBLISHABLE_KEY='<public-anon-key>' \
CUSTOMER_OFFER_REVIEW_REFERENCE='<review-ticket-or-commit>' \
node verification-support/productization-01036/publish-simulated-dealership-offer.mjs
```

The script probes current staging and production placement, pins the exact
runtime artifact and Marketplace versions, and writes only sanitized evidence.
