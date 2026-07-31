# Platform AI Fabric 0.5.2 Migration

Status: **LOCAL CONSUMER AND RELEASE-WAIT GATES PASSED; HOSTED GATES ACTIVE**

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
| Coolify deployment completion | Passed locally | Poll the new deployment UUID; never trust the old healthy container |
| Hosted specialist canary | Running | First isolated runtime is on `0.5.2`; isolation and negatives remain |
| Full Platform release gate | Pending | Fresh successful hosted run and `READY` result |

See `01_BUILD_AND_RELEASE_GATE_EVIDENCE.md` for commands and final results.
