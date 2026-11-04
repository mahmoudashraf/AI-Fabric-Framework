# 010.59 Hosted Document Storage Execution Record

Started: 4 November 2026. Status: IMPLEMENTING, not deployed or qualified.

## Scope

Implement the owner's optional hosted S3 storage and Console uploads through the
existing document-indexing lifecycle. One owned storage connection shares its
bucket across customer staging/production; target credentials and indexes remain
separate. BYO S3-compatible sources remain available. No framework change,
per-deployment business database, vector copy or new storage service.

## Progress

- [x] Record supplied AWS identity/key pair in ignored mode-600 private auth material.
- [x] Verify STS identity matches the supplied customer-storage provisioning user.
- [x] P1 source: shared connection metadata, private provider, scoped reader projections,
  ownership checks and mode-neutral document contracts.
- [x] P2 source: authenticated bounded uploads, reusable Kit/deployment document setup,
  existing registration/approval/index reconciliation.
- [x] P3 source: explicit production file selection, same-bucket target binding,
  production-local indexing and normal release verification.
- [x] Focused local backend/UI tests, real PostgreSQL migration and full UI build.
- [ ] Final exact-source CI, hosted service rollout and enabled provider configuration.
- [ ] Obtain scoped IAM reader permissions and approved read-only boundary ARN.
- [ ] Hosted staging/production upload-to-answer and isolation proof.

## Current Evidence And Limits

The supplied key authenticates as the intended IAM principal. IAM policy-list
inspection returns AccessDenied; this is not proof of failed bucket permission.
No resource was created for the initial identity check. Provisioning permissions
will be tested by the actual qualified operation, without widening IAM or sending
the provisioner key to a customer runtime. Exact secret values remain private.

Parallel write scopes: AWS provider and focused provider tests; reusable frontend
storage setup and tests. Main owns metadata/API orchestration, binding/compiler
integration, selection/indexing, review and hosted verification.

The earlier Support email/mobile work is closed separately. Its successful
deployment is not evidence that this storage feature has shipped.

## Source Integration And Local Evidence

- Existing binding secrets now have actual target ownership, not just a target
  in their names. Repeating the same hosted binding does not rotate secrets or
  change frozen release evidence. Hosted ownership cannot be supplied through
  the ordinary browser BYO binding payload.
- Registration operates on exact owned upload IDs under the target prefix,
  using normal runtime authority. Public approval is explicit. Pending work
  reconciles existing runtime IDs; authorization failures are not hidden as
  successful preparation.
- Promotion freezes reviewed file IDs/checksums in the existing candidate.
  Activation requires production-local source receipts and current runtime
  Active state. The candidate shows document filenames in customer Console.
- DATA revisions use CUSTOMER_SCOPED/BOUND_CONNECTION while historical BYO
  declarations remain truthful. Structured Jackson/Flyway migrations publish
  new DATA/template/bootstrap snapshots without regex JSON rewriting.
- Final local focused backend package: 301 tests across 17 suites passed, zero
  failures/errors/skips, including PostgreSQL 16.15 migrations, source retention,
  target isolation and full application startup/access controls with hosted
  storage disabled. The H2 test schema now mirrors target-owned secrets and
  the three storage metadata tables.
- Frontend: production build passed; 15 scoped unit/API tests and 11 mocked
  browser scenarios passed. Browser fixtures are not hosted AWS/runtime proof.
- New focused CI workflow: platform-document-storage-regression.yml. No broad
  framework release matrix, framework changes or new runtime image is required.

## Current External Prerequisite

The concrete required iam:GetUser probe for the generated reader namespace was
denied. The provisioner also needs the reviewed namespaced reader-management
permissions and a preapproved read-only S3 permissions boundary; it must never
become a general IAM administrator. The reader boundary is being made an explicit
backend setting and checked before issuing keys. The owner-facing prerequisite
was sent to Required for production; main continues all independent work.

No S3/IAM mutation, new bucket, secret installation in hosted Platform, or hosted
feature activation has occurred at this checkpoint. Candidate live qualification
can reuse an existing two-target customer deployment rather than creating an
unnecessary deployment/database. New public starter offer IDs also need the
explicit operator default configuration after migration; new rows alone do not
make an old enrollment allowlist select them.

## Explicit Bounded Limits

TXT/JSON source limit is 1 MiB, not PDF/OCR support. Initial hosted operation
limits are three connections per customer/tenant and 100 upload receipts per
connection, configurable by the operator; these protect paid storage operations,
not AI semantics. Abandoned provider preparation/upload and uncertain IAM key
issuance require operator readback/reconciliation, not a new recovery engine.
Shared source retirement/export and reader-key lifecycle assistance remain
operator responsibilities; no source-delete authority is granted to runtimes.
