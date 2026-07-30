# Platform-V11 AI Fabric 0.5.0 Migration

Status: **GATES A/B PASSED; LOCAL PATCHED SPECIALIST CANARY PASSED; HOSTED
SPECIALIST DEPLOYMENT BLOCKED**

This folder is the working evidence set for the one-way migration of LoomAI
Platform-V11 private framework consumers to published AI Fabric `0.5.0`.
Platform already owns the V04 lifecycle contract. The current migration moves
the base runtime, embedding worker, and deployment metadata to `0.5.0` without
adding a compatibility path. The next bounded phase adds
`ai-fabric-execution` only to the private runtime and proves
`deployment-knowledge-specialist@1` as required by the adoption prompt.

## Documents

| Document | Purpose |
| --- | --- |
| `AI_FABRIC_0_5_0_MIGRATION_PLAN.md` | Controlling phased migration, deployment, canary, and rollback plan |
| `00_CURRENT_STATE_EVIDENCE.md` | Immutable pre-change repository and architecture audit |
| `01_GATE_A_0_4_BASELINE_EVIDENCE.md` | File-level `0.4.0` implementation and proof checklist |
| `02_GATE_B_0_5_PUBLICATION_EVIDENCE.md` | Public tag, Maven Central, and standalone-consumer gate |
| `03_CONFIGURATION_MATRIX_REDACTED.md` | Property ownership, sensitivity, restart, reindex, and live-readback matrix |
| `04_BUILD_AND_TEST_EVIDENCE.md` | Gate A source, build, dependency, and test results |
| `05_PACKAGED_RUNTIME_EVIDENCE.md` | Packaged `0.4.0` runtime lifecycle and failure canaries |
| `06_CANARY_AND_TENANT_EVIDENCE.md` | Retrieval, tenant-isolation, and auth proof |
| `07_DEPLOYMENT_AND_ROLLBACK_EVIDENCE.md` | Snapshot/restore drill and external deployment gate |
| `08_SPECIALIST_SECURITY_CANARY_AND_FRAMEWORK_BLOCKER.md` | Specialist implementation, two-tenant canary, provider failure, and framework release blocker |

## Gate Order

```text
Completed V04 entity lifecycle source contract
        |
        v
Published AI Fabric 0.5.0 artifacts and clean-consumer proof
        |
        v
Private base runtime and embedding worker resolve only 0.5.0
        |
        v
Add and package deployment-knowledge-specialist@1
        |
        v
Deploy in isolation, canary, observe, and retain operational rollback
```

Do not add dual readers, compatibility shims, or runtime version fallbacks.
Historical immutable deployment records remain evidence; they are not an
active compatibility path. The specialist is additive and must not change the
existing chat surfaces.

AI Fabric `0.5.0` does not expose the stable per-work status and queue-summary
query API needed to remove Platform's private indexing facade imports. That is
a recorded framework blocker for decoupling the facade, not a blocker for the
read-only specialist. Do not remove the existing behavior or invent a second
queue contract while waiting for the framework follow-up.

Separately, released `0.5.0` loses trusted tenant/deployment/scope context
before framework RAG authorization. This is a hard blocker for hosted
specialist activation. Framework correction `7055dda` must be merged and
published as immutable `0.5.2` or later before the hosted canary proceeds.
