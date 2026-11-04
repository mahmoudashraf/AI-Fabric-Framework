# 010.59 Hosted Document Storage Execution Record

Started: 4 November 2026. Status: SOURCE DEPLOYED; live staging storage/upload
qualified. Reader permissions, indexing and production promotion NOT CLOSED.

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
- [x] Final exact-source CI and shared backend/Console rollout to both hosts.
- [x] Staging provider configuration and actual private bucket/upload/isolation proof.
- [ ] Production provider enablement after scoped reader access is qualified.
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

The initial no-mutation checkpoint above is superseded by the dated hosted proof
below. The two-target production qualification can reuse Northfield rather than
creating an unnecessary deployment/database. New public starter offer IDs also
need explicit operator default configuration after the provider is qualified;
new rows alone do not make an old enrollment allowlist select them.

## Hosted Checkpoint: 4 November 2026

- Source `353cccce8bceb5dc703713aebe768af1ce6a4672` committed/pushed. Focused CI
  `41827861594` and immutable core-image CI `41827861503` both SUCCESS.
- Shared backend and Console on BOTH hosts have native immutable source/image
  verification and healthy containers. Exact Coolify jobs: staging backend
  `k1378cl5d8iieetgph1c7c2n`, staging Console `q6l38ot0tkgg5b033if7ok1u`,
  production backend `ewu3ga8kholibgambo9wgerd`, production Console
  `oifugt5cmr6va4l7h4kye741`. Migrations 230/231/232 succeeded on both databases.
- Backend-only supplied credentials installed with exact STS identity check.
  Runtime-only flags were read back; automatically mirrored PR-preview settings
  were removed. No provisioner credential was installed on a customer runtime.
  Hosting is enabled on developer staging only. Production hosting is DISABLED;
  its existing BYO bindings and customer release identities remain unchanged.
- Actual Acme CUSTOMER_ADMIN used the Kit page to create/prepare a connection
  and upload `01-project-and-connector-setup.txt`. Native S3 checks confirmed all
  four public-access blocks, BucketOwnerEnforced, SSE-S3, TLS-only policy,
  versioning, exact ownership tags and uploaded size/checksum/version.
- Real anonymous API access 401, other-customer deployment access 404, foreign
  connection under that caller's own deployment 404, unsigned S3 object read 403.
- Live desktop and 390x844 screenshots recorded; no mobile horizontal overflow.
  UI truth is "Uploaded; no indexing receipt", not a fabricated indexing pass.
- No reader key created, no uploaded source indexed, no AI answer or production
  promotion claimed. The owner must apply the bounded reader delegation and
  approved boundary (including GetObjectVersion); Required for production has
  the exact account-scoped files and asks for the actual boundary ARN.
- Remaining sequence: configure confirmed boundary; bind/read-only denial
  canary; publish/apply mode-neutral DATA through the normal draft/version path;
  preview/approve/index/reconcile; answer proof; same connection production
  selection/indexing/activation/isolation. Then select new public bootstrap
  defaults `public-dealership-simulator-v3` and `public-knowledge-workspace-v2`.

Evidence: [dated live acceptance](../../../../../../../../verification-support/hosted-document-storage-01059/2026-11-04/acceptance.md).

## Explicit Bounded Limits

TXT/JSON source limit is 1 MiB, not PDF/OCR support. Initial hosted operation
limits are three connections per customer/tenant and 100 upload receipts per
connection, configurable by the operator; these protect paid storage operations,
not AI semantics. Abandoned provider preparation/upload and uncertain IAM key
issuance require operator readback/reconciliation, not a new recovery engine.
Shared source retirement/export and reader-key lifecycle assistance remain
operator responsibilities; no source-delete authority is granted to runtimes.

## Production Qualification: 4 November 2026, 22:10 UTC

This checkpoint supersedes the production-pending status above, not the retained
evidence of the earlier failed attempt.

- Existing Support deployment dep-79af290c promoted version ver-51e6c811 and
  artifact dsa-d3f03019 through the normal release/promotion APIs. Production
  rel-8822aff7 is APPLIED_VERIFIED/PASSED; vrf-e449aa2e has30 passed, four skipped,
  zero failed. Runtime/connector keep source83fd17427 and AI Fabric0.8.45.
- All13 selected support guides are ACTIVE, activeVersion1, independently indexed
  from approved original files in the same customer-owned hosted connection.
  Target reader identities remain separate. No vectors or business data copied.
- Original live consumer kit-ca617a82-bfb6-411e-89fe-94ccabf8713e moved directly
  production rel-09c92f90 -> rel-8822aff7, revision4 ->5. Existing Engage
  awi_pub_fc4f82f55cb6b83355cd807f0a21adfa retained identity, branding and URL.
  Temporary qualification consumer is unbound, owns zero apps/infrastructure and
  is DISABLED; audit foreign-key references retained rather than force-purged.
- Both Platform backends now095f6071, both Console UIs0bf44e602, both shared
  Engage shells c2cc54384; native source/image/health verified. Desk b2fb6a74 is
  unchanged. Narrow UI/readiness corrections retain ownership, exact candidate,
  target, configuration, artifact and verification checks.
- Final owning-customer readiness activationReady=true,
  postActivationComplete=true, blockers=[]. Actual production Console refresh
  showed Production active and all mandatory checks passed. No assignment was
  changed by refresh. Actual Support AI answer used the new upload/indexing guide
  and correctly distinguished upload from ACTIVE indexing and independent
  production indexing from vector copying. One observed answer, not a broad
  semantic quality claim.
- Verified Desk bindings required the normal owner status API read to reconcile
  APPLYING -> ENABLED; team endpoint then200 with two agents and no UI warning.
  Automatic post-verification reconciliation remains a recorded follow-up.
- Public enrollment defaults public-dealership-simulator-v3 and
  public-knowledge-workspace-v2 were NOT switched by this work. Fresh customer
  onboarding with those defaults is still a separate unqualified journey.

Evidence: [logout and production promotion qualification](../../../../../../../../verification-support/customer-journey-audit/2026-11-04/logout-correction.md).
Canonical private operation journal:
`loomai-document-storage-01059/receipts/promotion-qualified-20261104.private.json`;
final readback `promotion-final-readiness.private.json`. Older production-journey
receipts/helpers are historical; do not blindly repeat their apply sequence.
