# Platform-V11 AI Fabric 0.5.0 Migration

Status: **ANALYSIS COMPLETE - GATE B PASSED; GATE A REMAINS ACTIVE**

This folder is the working evidence set for migrating LoomAI Platform-V11 from
AI Fabric `0.3.1` through the mandatory `0.4.0` lifecycle baseline and then to
the published `0.5.0` agentic execution release.

## Documents

| Document | Purpose |
| --- | --- |
| `AI_FABRIC_0_5_0_MIGRATION_PLAN.md` | Controlling phased migration, deployment, canary, and rollback plan |
| `00_CURRENT_STATE_EVIDENCE.md` | Immutable pre-change repository and architecture audit |
| `01_GATE_A_0_4_BASELINE_EVIDENCE.md` | File-level `0.4.0` implementation and proof checklist |
| `02_GATE_B_0_5_PUBLICATION_EVIDENCE.md` | Public tag, Maven Central, and standalone-consumer gate |
| `03_CONFIGURATION_MATRIX_REDACTED.md` | Property ownership, sensitivity, restart, reindex, and live-readback matrix |

The remaining numbered evidence files are created as their phases execute.

## Gate Order

```text
Current Platform 0.3.1
        |
        v
Gate A: complete and prove the 0.4.0 entity lifecycle cutover
        |
        v
Gate B: revalidate the already-published 0.5.0 artifacts from an empty
        Maven repository
        |
        v
Adopt one read-only deployment-knowledge specialist in the private runtime
        |
        v
Package, deploy in isolation, canary, observe, and retain rollback
```

Do not bypass Gate A with a compatibility shim, and do not satisfy Gate B with
a local framework checkout or `mvn install`.
