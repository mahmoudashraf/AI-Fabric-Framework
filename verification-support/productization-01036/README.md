# 010.36 customer-owned S3 canary

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

## Reviewed simulated-dealership dependency package

`publish-simulated-dealership-package.mjs` registers the exact reviewed GHCR
digests as `MANAGED_DEPENDENCY` source artifacts, promotes them through the
production review channel, and publishes one immutable dependency topology for
`mkv-template-simulated-dealership-experience-v1-2`. A production-reviewed
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
