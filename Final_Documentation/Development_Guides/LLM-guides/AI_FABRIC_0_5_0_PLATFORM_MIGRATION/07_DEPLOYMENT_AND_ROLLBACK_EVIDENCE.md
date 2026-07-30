# Gate A Deployment And Rollback Evidence

Status: **LOCAL RESTORE DRILL PASSED; EXTERNAL DEPLOYMENT PENDING**

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

## 2. External Deployment Gate

The following evidence is still required before Gate A is complete:

1. commit and push the reviewed Gate A source;
2. record the isolated Coolify staging application and immutable source commit;
3. build all affected image families from published Maven artifacts;
4. read back product commit, framework `0.4.0`, and entity contract `0.4`;
5. run the lifecycle, tenant, auth, and provider-failure canaries externally;
6. export deployment configuration and record pre-cutover assignment mapping;
7. prove deployment-level rollback to the prior immutable image/config;
8. leave the production assignment unchanged until the gate is green.

The local restore drill proves the package and state backup mechanism. It does
not claim that Coolify, DNS, assignment mapping, or production rollback has
already been exercised.
