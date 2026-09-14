# Platform AI Fabric 0.6.1 Migration

Status: **SOURCE AND LOCAL RELEASE GATES PASSED; HOSTED STAGING IN PROGRESS**

This is the one-way direct Platform consumer migration from AI Fabric `0.5.2`
to `0.6.1`. No `0.5.3` or `0.6.0` deployment is performed between them, and
no compatibility reader, duplicate framework contract, or version fallback is
introduced.

## Base Upgrade Scope

- consume only `io.github.loom-ai-labs:ai-fabric-bom:0.6.1`
- retain Java 21 and Spring Boot 4.1.x in framework-consuming services
- preserve chat, RAG, Data Sync, indexing, actions, confirmation, memory,
  direct specialist, fixed-plan, and deployment-knowledge behavior
- set `ai.execution.output-finalization.max-attempts=1`
- set `ai.execution.specialist-chains.enabled=false`
- regress corrected tenant/deployment retrieval and exact MCP binding
- resolve artifacts from Maven Central in an empty dependency cache
- deploy and verify staging before any production rollout

No chain table or chain secret is required for this base upgrade.

## Separate Optional Scope

Bounded multi-specialist chains are available in AI Fabric `0.6.1`, but are not
enabled by this migration. A later adoption must define one exact-version,
read-only LoomAI canary chain; provision reviewed JDBC state and stable private
encryption/fingerprint secrets; and pass the full chain security, replay,
restart, cancellation, retention, and attribution matrix.

Existing chat, RAG, fixed plans, direct specialists, actions, receipts, reviews,
and Smart Brain operations must not be rebuilt as chains merely because the
new contract exists.

## Gates

| Gate | Status | Evidence |
| --- | --- | --- |
| Immutable framework publication | Passed | Tag dereferences to `bf6d19eed5ed0a8d8085db7cc02e0505e9973e65`; BOM, core, and execution resolve from Maven Central |
| Direct source migration | Passed | Active private source/config/tests target `0.6.1`; no intermediate release path was added |
| First-rollout settings | Passed | Runtime defaults, customer template, and generated deployment env force one output attempt and keep chains disabled |
| Empty-cache private builds | Passed | Infrastructure `209/209`, product `32/32`, Platform backend `733/733`; combined `974/974` |
| Dependency convergence | Passed | Framework-consuming dependency trees resolve only `0.6.1` |
| Packaged runtime identity | Passed | Runtime contains `ai-fabric-execution-0.6.1.jar` and all packaged AI Fabric libraries are `0.6.1` |
| Coolify-equivalent container builds | Passed | Platform backend, runtime, and vectorization-runner Dockerfiles build successfully from the repository root |
| Public framework page | Passed | Node 22 Astro check/build/content/static/browser smoke completed |
| Staging protected-work baseline | Passed | Canonical runtimes use in-memory async work with receipts/reviews/waits/plans/managers disabled; no chain state is enabled |
| Staging Platform deployment | In progress | Deploy immutable private source and verify public health/source identity |
| Canonical staging reapply | Pending | Publish/apply ecommerce and marketplace versions on `0.6.1` with chains off |
| Hosted existing-capability canaries | Pending | Indexing, Data Sync, RAG, chat, actions, confirmation, exact specialist, tenant/deployment denial |
| Full staging release gate | Pending | Fresh `full-platform-release-readiness` run must be `PASSED` and gate `READY` |
| Production rollout | Not started | Requires the completed staging evidence and explicit production execution |
| Optional chain adoption | Out of base scope | Separate migration, secrets, canary, and owner approval |

See `01_SOURCE_AND_LOCAL_RELEASE_EVIDENCE.md` for the completed source and
local verification record. Hosted identifiers and outcomes are added only
after each deployment actually completes.
