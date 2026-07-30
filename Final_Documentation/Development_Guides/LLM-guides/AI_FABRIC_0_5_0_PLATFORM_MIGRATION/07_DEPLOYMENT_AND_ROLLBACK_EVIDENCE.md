# Gate A Deployment And Rollback Evidence

Status: **LOCAL RESTORE DRILL PASSED; FIRST EXTERNAL ATTEMPT ROLLED BACK; RETRY PENDING**

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

## 3. External Deployment Gate

The following evidence is still required before Gate A is complete:

1. commit and push the reviewed `V129` correction;
2. pin Coolify to that new immutable source commit;
3. deploy the Platform backend and UI successfully;
4. build all affected image families from published Maven artifacts;
5. read back product commit, framework `0.4.0`, and entity contract `0.4`;
6. run the lifecycle, tenant, auth, and provider-failure canaries externally;
7. export deployment configuration and record pre-cutover assignment mapping;
8. prove deployment-level rollback to the prior immutable image/config;
9. leave the production assignment unchanged until the gate is green.

The local restore drill proves the package and state backup mechanism. It does
not claim that Coolify, DNS, assignment mapping, or production rollback has
already been exercised.
