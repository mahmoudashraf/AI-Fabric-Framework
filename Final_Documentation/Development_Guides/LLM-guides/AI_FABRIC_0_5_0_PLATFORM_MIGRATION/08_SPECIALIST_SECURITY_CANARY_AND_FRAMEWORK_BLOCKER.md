# Specialist Security Canary And Framework Blocker

Status: **LOCAL PATCHED CANARY PASSED; HOSTED DEPLOYMENT BLOCKED**

Evidence date: 2026-07-30
Branch: `Platform-V11`
Specialist: `deployment-knowledge-specialist@1`
Framework baseline: AI Fabric `0.5.0`

No production secret, raw provider payload, or sensitive prompt is recorded in
this report.

## 1. Gate Decision

The private LoomAI specialist implementation, product isolation controls, and
local packaged runtime canary are green only when tested with the framework
security correction at:

```text
repository: Loom-AI-Labs/ai-fabric-framework
branch: codex/specialist-trusted-retrieval-context
review commit: 7055dda
base: ai-fabric-framework-v0.5.1 / 4c9221b
```

Released AI Fabric `0.5.0` does not propagate the complete trusted execution
boundary into the orchestration metadata consumed by framework RAG. This is a
hard release blocker for the specialist:

- do not deploy the specialist to staging or production on unpatched `0.5.0`;
- do not add a LoomAI-side duplicate execution gateway or identity workaround;
- merge the framework correction;
- publish it in a new immutable framework release;
- rebuild LoomAI from that published artifact; and
- repeat the packaged two-tenant and provider-failure canary before promotion.

The existing non-specialist Gate A baseline remains historical green evidence.
This blocker applies to activation of the new specialist path.

## 2. Framework Contract Defect

Expected framework contract:

> A `TrustedExecutionContext` supplied to `SpecialistClient` must remain the
> authority source for tenant, deployment, subject, caller, auth mode, and
> granted scopes throughout retrieval and output generation.

Observed `0.5.0` behavior:

1. LoomAI supplied a complete `TrustedExecutionContext`.
2. `DefaultAIExecutionGateway.bindContext` did not project the trusted tenant,
   deployment, and scope values into `OrchestrationContext.metadata`.
3. `InformationRagExecutionSupport` built retrieval auth from that metadata.
4. A product `SearchSource` therefore received an incomplete trusted boundary.
5. An unpatched real-provider canary attached a Tenant B document to a Tenant A
   answer.

The defect belongs to `ai-fabric-execution`, not to a LoomAI HTTP endpoint.
No missing public endpoint is required for the fix.

The framework correction:

- removes reserved/spoofable auth keys from adapter-provided metadata;
- projects canonical auth fields from `TrustedExecutionContext`;
- preserves unrelated application metadata; and
- adds a regression proving spoofed adapter metadata cannot replace trusted
  tenant, deployment, subject, caller, auth mode, issuer, audience, expiry, or
  scopes.

Framework verification:

| Reactor | Tests | Result |
| --- | ---: | --- |
| Curated dependencies | 5 | Passed |
| Core | 680 | Passed |
| Chat session | 59 | Passed |
| Execution | 312 | Passed |
| Total | 1,056 | Zero failures/errors |

## 3. LoomAI Product Boundary

LoomAI retains defense in depth around the framework contract:

- exact scope constants are centralized in `RuntimeScopeCatalog`;
- the specialist requires both
  `specialist:deployment-knowledge-specialist@1` and `vector:document`;
- tenant ID and deployment ID come only from verified private runtime auth;
- the subject must identify the same deployment;
- auth mode and caller type must be the trusted application/service posture;
- only metadata-filter-capable vector providers are accepted;
- provider-side retrieval receives exact tenant and deployment filters;
- configured source-boundary conflicts fail closed;
- shared indexes are excluded from this deployment-private specialist; and
- every returned hit is post-filtered against the same tenant/deployment
  boundary.

Deployment POC import also overwrites caller-supplied tenant, deployment, and
customer metadata with server-owned target values before persistence.

## 4. Product Verification

The specialist/product changes passed:

| Verification | Result |
| --- | --- |
| Search registry and specialist focused tests | 19 passed |
| Search registry tenant/filter tests | 15 passed |
| Specialist manifest tests | 4 passed |
| Deployment POC import tests | 4 passed |
| Full private runtime reactor | 159 passed |
| Full Platform backend suite | 727 passed |

The mixed-tenant registry test deliberately returns Tenant A and Tenant B hits
from a provider and proves the LoomAI post-filter retains only Tenant A.

## 5. Packaged Runtime Proof

The patched framework execution artifact was installed into an isolated Maven
repository and used to package the real private runtime. The normal local Maven
cache was restored afterward.

Artifact verification:

```text
published 0.5.0 ai-fabric-execution SHA-256:
0df0fbc6...

patched 0.5.0 canary ai-fabric-execution SHA-256:
9ed55f3d...

runtime nested ai-fabric-execution SHA-256:
9ed55f3d...
```

The packaged runtime started with:

- AI Fabric version `0.5.0`;
- entity type `document`;
- Lucene local persistent vector storage;
- 512-dimensional OpenAI embeddings;
- metadata-filtered search support;
- private source `deployment-private-vector` in `READY` state; and
- verified private runtime auth required.

The real-provider container used the same fix before its rebase, at commit
`aeda7e1`. The review branch was subsequently rebased without conflict onto
released `0.5.1`; the equivalent reviewed patch is now `7055dda`, and the full
combined `0.5.1` execution dependency reactor passed.

The packaged `ai-entity-config.yml` SHA-256 was:

```text
aa4f26b68337a953673f5f4c396c117dc1a67b6cc14bf022d5c1a7c182d115ac
```

The local container did not receive `AI_ENTITY_CONFIG_HASH`, so admin readback
reported that optional field as `unknown`. The hash above was calculated from
the resource inside the executable JAR. The hosted canary must pass the hash
through deployment metadata so readback proves the running artifact directly.

## 6. Real OpenAI Two-Tenant Canary

Safe fixtures:

```text
Tenant A:
- deployment-vector-configuration
- deployment-runtime-status

Tenant B:
- other-tenant-distinctive-fact
```

Results:

| Case | Result | Correlation ID |
| --- | --- | --- |
| Grounded vector configuration | `ANSWERED`; correct Lucene, 512 dimensions, and embedding model; Tenant A evidence only | `exec-31fe0517-f161-4080-b385-ca8989fc88c0` |
| Direct Tenant B marker probe | `INSUFFICIENT_EVIDENCE`; marker absent; Tenant A evidence only | `exec-5975abb0-897c-440c-a265-0688cc4392be` |
| Data Sync update to 768 dimensions | 1/1 update succeeded; answer changed to 768 | `exec-5a610af4-108e-41b1-8239-986d14edbc52` |
| Data Sync delete | Delete succeeded; removed document absent; result became `INSUFFICIENT_EVIDENCE` | `exec-aa9e94fb-9d72-44d9-8ca8-045ae6f80971` |
| Hostile instruction in evidence | Answer reported the safe document status; hostile output marker was not followed | `exec-15ac7cb0-f9de-4e9d-9709-3c7fa13446ae` |
| Hidden-memory probe | HTTP 422, `GROUNDING_VALIDATION_FAILED`; no invented conversation memory | `exec-5e7295af-19d7-461e-80b4-d4af53cb5522` |

No Tenant B evidence ID or distinctive marker appeared in the patched Tenant A
responses.

## 7. Auth And Provider Failure Canaries

Live private-runtime negatives:

| Case | Result |
| --- | --- |
| Missing `vector:document` scope | HTTP 403 |
| Missing trusted tenant/deployment boundary | HTTP 403 |
| OpenAI provider disabled | HTTP 503, `FAILED / INTENT_PROVIDER_FAILED`, zero evidence |

Provider-disabled correlation ID:

```text
exec-f7dc10f8-a165-4f82-b432-59cbbe10302f
```

The provider-disabled runtime started with zero `AIProvider` beans and returned
a visible bounded failure. It did not return a deterministic or fabricated
success.

## 8. Configuration Semantics Finding

`ai.service.features.enable-generation=false` is not a provider kill switch in
AI Fabric `0.5.0`. Framework source shows that it skips LLM configuration
validation, while an enabled OpenAI provider bean remains callable. This was
confirmed when a canary with OpenAI enabled and only the generation feature
flag disabled still returned a generated answer.

The actual provider-disabled canary therefore used:

```text
OPENAI_ENABLED=false
AI_SERVICE_FEATURES_ENABLE_GENERATION=false
AI_SERVICE_FEATURES_ENABLE_EMBEDDINGS=false
```

This does not block the current production shape because the specialist
requires both generation and embeddings enabled. It is still a framework
contract/documentation issue: operators must not treat
`enable-generation=false` as an emergency generation kill switch until the
framework either enforces the flag or narrows its documented meaning.

## 9. Required Next Release Sequence

1. Review and merge framework commit `7055dda`.
2. Include the fix in immutable AI Fabric `0.5.2` or later. Released `0.5.1`
   does not contain it and must not be retagged.
3. Confirm Maven Central and the GitHub tag resolve to the same released
   source.
4. Update the private BOM consumer once, without a dual-version fallback.
5. Rebuild the private runtime from published Maven artifacts only.
6. Verify the nested execution JAR matches the published release.
7. Deploy an isolated hosted canary, not an existing customer runtime.
8. Repeat auth, provider failure, two-tenant retrieval, hostile evidence,
   update/delete, health, build metadata, and rollback checks.
9. Run existing chat, Data Sync, MCP/read-action, and release-gate
   regressions.
10. Promote only after every required stage is green.
