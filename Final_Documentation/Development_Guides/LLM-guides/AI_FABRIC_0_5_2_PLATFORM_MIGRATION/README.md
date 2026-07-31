# Platform AI Fabric 0.5.2 Migration

Status: **PRODUCTION PLATFORM AND PRODUS VALIDATED; RELEASE GATE READY**

This is the current one-way Platform consumer migration. It preserves the V04
entity lifecycle and existing runtime behavior without compatibility readers,
duplicate framework contracts, or version fallbacks.

## Scope

- consume `io.github.loom-ai-labs:ai-fabric-bom:0.5.2`
- use `IndexingWorkQuery` for durable per-work indexing reconciliation
- include trusted tenant, deployment, subject, and scope propagation for
  specialist RAG
- keep LoomAI authentication, authorization, polling, and HTTP projection
- preserve aggregate queue diagnostics until a public summary contract exists
- build from published Maven Central artifacts only
- deploy and run isolation, fail-closed, regression, and full release gates

## Gates

| Gate | Status | Required evidence |
| --- | --- | --- |
| Immutable framework publication | Passed | Tag, release, CI, and Central resolve to `ada4580` |
| Security-fix ancestry | Passed | Release contains `7055dda` |
| Private source migration | Passed | Active defaults and tests target only `0.5.2` |
| Empty-cache private builds | Passed | 967 tests passed across infrastructure, product, and Platform |
| Dependency convergence | Passed | Runtime and worker resolve only `0.5.2` |
| Packaged execution JAR | Passed | Runtime contains `ai-fabric-execution-0.5.2.jar` |
| Coolify deployment completion | Passed | Both canonical deployments completed against their returned deployment UUIDs |
| Hosted specialist canary | Passed | Own-tenant retrieval, cross-tenant denial, and missing-boundary negatives passed |
| Vectorization convergence | Passed | Both canonical plans are `IN_SYNC` after successful reindex |
| ProdUS production deployment | Passed | `dep-f6abfa06` runs V04 version `ver-aaec416e` on AI Fabric `0.5.2`; verification and assignment passed |
| Managed product drift | Passed | MCP gateway is production-scoped, `READY`, and `NO_DRIFT` |
| Full Platform release gate | Passed | Production run `vsr-e18452e5` passed all 13 blocking stages; release gate is `READY` |

See `01_BUILD_AND_RELEASE_GATE_EVIDENCE.md` for commands and final results.
