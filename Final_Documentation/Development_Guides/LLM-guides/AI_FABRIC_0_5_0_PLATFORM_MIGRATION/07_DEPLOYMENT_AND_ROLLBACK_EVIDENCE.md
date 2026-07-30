# Gate A Deployment And Rollback Evidence

Status: **PLATFORM DEPLOYMENT PASSED; CANONICAL V04 CUTOVER AND EXTERNAL ROLLBACK PENDING**

Evidence date: 2026-07-30

## 1. Packaged Runtime Restore Drill

The primary canary was stopped before snapshotting so H2 and Lucene were
quiescent. The named volume was archived, the primary container restarted, and
the archive restored into a new named volume.

```text
Pinned image:
sha256:aec3c8daeb50312299283ce8e38b4612d0224cfac434997938255123029b4391

Snapshot SHA-256:
a168bd582d7048eda2d592f2b71487cca4a46363befc292d7ff9fd7616249777

Snapshot bytes:
27129
```

The restored container passed:

```text
liveness: healthy
document vectors: 2
completed lifecycle work: 5
pending/processing/dead-letter work: 0/0/0
retrieval path: RAG_ANSWER
source count: 1
source ID: gate-a-aurora
source tenant: ten-gate-a
```

Restore verification request ID:

```text
rag-f6c90f48-2992-4f6b-8ed3-b207ac481821
```

The restored answer retained the current penetration-test and security-owner
approval policy. The restore container, restore volume, and temporary archive
were removed after proof.

## 2. External Staging Attempt 1

```text
Application: loomai-platform-backend
Coolify application UUID: lnlgausj8hzrim7f5fa8pmpx
Source branch: Platform-V11
Source commit: 624e6c8bd67a9143e4fd02f840cf0e6a0e8771d2
Deployment UUID: urpsy513d95hpxkibgx31cv4
```

The build resolved the pinned commit and reached Flyway successfully. Runtime
startup then failed strict V04 validation for the customer-installed
`mkp-data-produs-safe-knowledge@0.1.0` manifest. Coolify automatically rolled
back; the prior Platform-V10 backend remained `running:healthy`. No assignment
or production deployment was changed.

Root cause:

- `V128` upgraded five known first-party marketplace plugin IDs;
- persisted customer-installed and historical plugin IDs were outside that
  hardcoded set; and
- startup validation correctly failed closed instead of accepting their legacy
  `entity-type`, `description`, and `features` keys.

Correction prepared for attempt 2:

- `V129` migrates legacy entries under every persisted marketplace
  `ai-entities` object while preserving dynamic entity-type keys;
- a real PostgreSQL 16 regression proves dynamic conversion, valid-V04
  preservation, and idempotence;
- the complete backend suite passes 717 tests with zero failures/errors;
- the staging database password is now runtime-only rather than a Docker build
  argument, with its value preserved; and
- the deployment branch metadata is aligned to `Platform-V11`.

Private pre-change application/environment snapshots are stored outside the
repository with mode `0600`. Durable evidence records only their checksums:

```text
backend application snapshot:
77eb8e6d138c33f6faadaa9ed61dd736d7093ce056fc342f5327f65eebdbafdf

backend environment snapshot:
c6ad71f2448763ebf63087122cafa8606abebd0f9f660ccecb71e578795ea77e
```

## 3. External Staging Attempt 2

```text
Source commit: 196aaf921c0dfbe7d7f0468b53fae1c2abacacf0
Backend deployment UUID: whzq2ue17viziprepjsq88cx
UI deployment UUID: jarlejdco743ggsj1w0mgk9a
```

Both Coolify applications resolved the exact immutable commit and finished
successfully. Backend and UI health probes passed. Live Marketplace readback
proved that customer plugin `mkp-data-produs-safe-knowledge@0.1.0` remained
published and that all ten dynamic entity entries use the V04 shape:

```text
legacyEntryCount: 0
v04EntryCount: 10
```

No assignment, customer runtime, or production Coolify application was changed
by this Platform deployment.

## 4. Canonical Configuration Backup

Before mutating either canonical rollout, the Platform created config-only
exports with no secret values:

```text
Marketplace deployment:
deployment: dep-d99b3252
active version before cutover: v6
export: dexp-eea9f25f
bundle: dxb-6f686d71
bundle hash: sha256:gkvbap2mRV4AsomqNWjKUw_Gp-epNEEC3907x6ptQ-g
manifest hash: sha256:DG6enK4bNDBLuHFLH4EDVp5Jpr9KrNBixPt4a83FvDg

Ecommerce deployment:
deployment: dep-c5b5fe23
active version before cutover: v5
export: dexp-304585b4
bundle: dxb-3db0a27c
bundle hash: sha256:5VpxMLnnjy_Zv-9uAl_o1elTwwQa4KpwK8Z8oKgr3L4
manifest hash: sha256:XS2Fj59QQGic3D77SKSz9YqQc32q4JijfKlx3D-ud_8
```

Both exports report `READY`, `CONFIG_ONLY`, and
`includedSecretValues: 0`. Private draft/version snapshots are retained outside
the repository with mode `0600`; only their checksums are recorded here:

```text
Marketplace draft: d8214a67af607580104ac77ead54f53cd98ff4da8b8e89f0c46f4fd02c8ebc2f
Marketplace versions: 8ee45f69ebb69412f7be839d0b5d240441d0407fc557b896be9eb179d6265fd0
Marketplace config export: 5419308fc764e7278e9443a78de8148d947fd062882b7583a12a2bb9490c00d5
Ecommerce draft: 48fa795ba38694d767df10eb5fa87382727a3e833adb06e837192adc5fda1adc
Ecommerce versions: e85301db5812be6b49634e402a2cd3cbf683334b2ca40fefd3246fbcc7cd0464
Ecommerce config export: 091e678a52bcb58a7fd5ddf4fcdf8f890e7f031fa4564a996b08150b187bf1c3
```

## 5. Fresh Release-Gate Finding

The fresh full release gate was dispatched with repair disabled:

```text
run: vsr-807a8010
status: FAILED
failed stage: platform-admin-live-regression
```

The gate stopped before executing its live regression script because both
preserved canonical active versions still declare
`AI_ENTITY_CONFIG_V0_3`. The V04 indexed-output hasher correctly rejected that
legacy contract. Shared inference passed; later stages did not run.

This was a Platform migration-state defect, not a missing AI Fabric endpoint.
The correction:

- represents a legacy active version as `MIGRATION_REQUIRED` instead of trying
  to compute a V04-only indexed-output hash;
- marks canonical rollout readiness as repairable with reason
  `ENTITY_CONFIG_CONTRACT_MIGRATION_REQUIRED`;
- gives every canonical shared-vector entity explicit required `tenantId`
  vector metadata; and
- makes canonical rollout repair apply the existing audited draft migration
  before validation, publication, and apply.

Historical V03 versions remain immutable. Only a new V04 version may become
active.

## 6. Canonical Repair Sequencing Check

The first deployed repair implementation attempted to write canonical entity
config through the generic draft editor before advancing the V03 contract
label. Ecommerce repair returned HTTP 400:

```text
Migrate this draft to AI_ENTITY_CONFIG_V0_4 before editing entity configuration.
```

This was the expected fail-closed editor behavior. Live readback after the
attempt confirmed:

```text
Ecommerce: draft drf-74d30047 remains V03; 5 published versions; latest v5
Marketplace: draft drf-fa0bfdbf remains V03; 6 published versions; latest v6
```

The follow-up repair is atomic inside the migration service: it validates the
Platform-owned canonical entity config against the target provider posture,
records old/new config and semantic hashes, advances only the active mutable
draft to V04, and then allows the normal rollout update/validate/publish/apply
sequence. Invalid, non-V04, or tenant-unsafe canonical config remains blocked
and audited.

## 7. External Deployment Gate

The following evidence is still required before Gate A is complete:

1. commit and deploy the migration-aware release-gate correction;
2. repair the two backed-up canonical drafts through the audited migration
   flow and publish/apply new V04 versions;
3. rerun the complete full-platform release gate to `PASSED` and fresh
   `READY`;
4. build all affected image families from published Maven artifacts;
5. read back product commit, framework `0.4.0`, and entity contract `0.4`;
6. run the lifecycle, tenant, auth, and provider-failure canaries externally;
7. prove deployment-level rollback to the prior immutable image/config;
8. restore the repaired V04 baseline after the rollback drill; and
9. leave customer and production assignment mappings unchanged until the gate
   is green.

The local restore drill proves the package and state backup mechanism. It does
not claim that Coolify, DNS, assignment mapping, or production rollback has
already been exercised.
