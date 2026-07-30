# AI Fabric 0.5.0 Runtime Configuration Matrix

Status: **TEMPLATE PREPARED - LIVE VALUES NOT YET READ BACK**

This matrix records ownership, sensitivity, restart/data impact, and proof for
the first LoomAI runtime specialist. It intentionally contains no credential
values.

## 1. Environment Value Sources

| Environment | Allowed value source | Current verification |
| --- | --- | --- |
| Local development | Developer environment or ignored local file | Pending after implementation |
| Automated tests | Test-scoped properties and deterministic fixtures | Pending after implementation |
| Staging | Coolify environment and secret storage | Live readback pending |
| Isolated canary | Separate Coolify application/environment | Provisioning pending |
| Production | Coolify environment and secret storage | Live readback pending |

Rules:

- secret values never enter this document, Git, manifests, image layers,
  build arguments, health output, logs, or test reports;
- a value reported by source defaults is not assumed to be the live value;
- staging, canary, and production values require provider-side readback;
- a secret is verified by presence/fingerprint or a successful bounded probe,
  never by printing it;
- unknown production properties fail the release gate instead of silently
  inheriting a development default.

## 2. Artifact And Contract Configuration

| Configuration | Owner | Current/default | Target | Secret | Restart | Data impact | Proof |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AI Fabric BOM/version property | Product Maven reactor | `0.3.1` | `0.4.0` for Gate A, then `0.5.0` | No | Rebuild | Contract migration | Dependency tree and build metadata |
| Entity artifact file | Runtime | `ai.config.default-file=ai-entity-config.yml` | Immutable release artifact, one canonical property spelling | No | Yes | Reindex if projection changes | Startup bind plus artifact hash |
| Entity contract version | Platform deployment compiler | Not explicit | Explicit `0.4` | No | Release | Draft migration and new version | Manifest/readback |
| Framework version in release manifest | Platform deployment compiler | Not explicit | Exact resolved release | No | Release | None | Manifest/readback |
| Specialist manifest locations | Private runtime | Not present | `classpath*:ai-specialists/*.{yml,yaml,json}` | No | Yes | None | Fail-fast manifest diagnostics |
| Specialist ID | Private runtime code | Not present | `deployment-knowledge-specialist@1` | No | Rebuild | None | Endpoint response/metrics |

`AI_CONFIG_DEFAULT_FILE` is named by the adoption guide, while the current
source hard-codes `ai.config.default-file`. Implementation must select and test
one canonical external property before deployment.

## 3. Runtime Identity And Authorization

| Configuration | Owner | Current/default | Target rule | Secret | Restart | Proof |
| --- | --- | --- | --- | --- | --- | --- |
| `AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE` | Runtime | `VERIFIED_CONTEXT_REQUIRED` | Keep fail closed | No | Yes | Missing/invalid context denied |
| `AI_FABRIC_RUNTIME_REJECT_CONFLICTING_REQUEST_IDENTITY` | Runtime | `true` | Keep `true` | No | Yes | Conflicting identity test |
| `AI_FABRIC_RUNTIME_REJECT_REQUEST_IDENTITY_WHEN_VERIFIED_CONTEXT_PRESENT` | Runtime | `true` | Keep `true` | No | Yes | Body/header confusion test |
| Accepted issuers/audiences | Runtime | Environment-owned | Exact deployment-approved values | No | Yes | Valid/invalid assertion tests |
| Trusted backend API key | Runtime secret manager | Empty source default | Existing private ingress only | Yes | Yes | Presence plus authenticated probe |
| Private assertion signing key | Runtime secret manager | Empty source default | Existing rotated private key | Yes | Yes | Valid, tampered, expired assertions |
| Public token signing key | Runtime secret manager | Empty source default | No specialist scope for v1 bootstrap | Yes | Yes | Public token denied specialist |
| Public bootstrap enabled | Runtime | `false` | Keep disabled for first canary | No | Yes | Anonymous request denied |
| Runtime authz mode | Runtime | `REMOTE_HTTP` | Preserve reviewed live mode; never allow-all | No | Yes | Authorized and denied subject proof |
| Authz outbound credential | Runtime secret manager | Inherits action API key by default | Exact backend-owned credential | Yes | Yes | Bounded authz probe |
| Specialist scope | Assertion/token issuer | Not present | `specialist:deployment-knowledge-specialist@1` | No | Issuer deploy | Exact scope acceptance/denial |
| Vector scope | Assertion/token issuer | Existing context-dependent | `vector:document` only | No | Issuer deploy | Other-space denial |

The request body must never supply identity, tenant, deployment, customer,
subject, scopes, specialist, provider, model, or vector space.

## 4. Provider, Vector, And Retrieval Configuration

| Configuration | Current/default | Target rule | Secret | Restart | Reindex impact | Proof |
| --- | --- | --- | --- | --- | --- | --- |
| `AI_PROVIDERS_LLM_PROVIDER` | `openai` | Installed/enabled reviewed provider | No | Yes | None | Safe provider health/readback |
| `AI_PROVIDERS_EMBEDDING_PROVIDER` | `openai` | Must match indexed contract | No | Yes | Yes if changed | Embedding purpose/provider proof |
| `OPENAI_ENABLED` | `false` | `true` only where real provider is required | No | Yes | None | Provider smoke |
| `OPENAI_API_KEY` | Empty | Secret manager only | Yes | Yes | None | Presence plus bounded provider call |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Backend-owned reviewed endpoint | No | Yes | None | Safe endpoint/provider diagnostic |
| `OPENAI_MODEL` | `gpt-4o-mini` | Backend-owned reviewed model | No | Yes | None | Generation diagnostic |
| `OPENAI_TIMEOUT` | `60` seconds | Backend-owned bounded provider timeout | No | Yes | None | Timeout/failure visibility test |
| `OPENAI_EMBEDDING_MODEL` | `text-embedding-3-small` | Match index lifecycle | No | Yes | Yes if changed | Embedding/index evidence |
| `OPENAI_EMBEDDING_DIMENSIONS` | `512` | Match vector index exactly | No | Yes | New index when changed | Dimension health/readback |
| Service generation feature | Follows `OPENAI_ENABLED` | Enabled for real specialist | No | Yes | None | Capability health |
| Service embedding feature | Follows `OPENAI_ENABLED` | Enabled for Data Sync/proof | No | Yes | None | Capability health |
| Search/RAG features | `true` | Keep enabled | No | Yes | None | Retrieval canary |
| `AI_VECTOR_DB_TYPE` | `lucene` | Keep current provider for first canary | No | Yes | Provider change requires migration | Vector health/readback |
| Lucene index path | Dimension-specific local path | Persistent volume, dimension-specific | No | Yes | New path when dimension changes | Restart and retrieval proof |
| Lucene vector dimension | Embedding dimension | Exact embedding dimension | No | Yes | New index when changed | Startup validation |
| Data Sync enabled | Source `true` | Required | No | Yes | Populates derived index | Create/update/delete proof |
| Retrieval connector enabled | `false` | Keep disabled for first specialist | No | Yes | None | Capability inventory |
| Registered execution vector spaces | Not present | `document` only | No | Yes | None | Startup registry readback |
| Mode retrieval allowlist | Not present | `document` only | No | Yes | None | Other-space denial |
| Similarity threshold | Not present | Start at reviewed `0.45`, tune with evidence | No | Yes | None | Relevant/irrelevant query set |
| Top K/context bounds | Existing RAG path | `4` docs, `6000` context chars for v1 | No | Yes | None | Boundary tests |

The first canary must not combine a vector-provider migration with specialist
adoption.

## 5. Execution-Layer Configuration

| Configuration | Target v1 | Secret | Restart | Proof |
| --- | --- | --- | --- | --- |
| `ai.execution.enabled` | `true` | No | Yes | Capability health |
| `ai.execution.manifests.enabled` | `true` | No | Yes | Registry readback |
| `ai.execution.manifests.fail-fast` | `true` | No | Yes | Invalid manifest prevents startup |
| Manifest/resource byte limits | `65536` | No | Yes | Oversize rejection |
| Allowed actions | Empty | No | Yes | Action authority denied |
| Async repository | `IN_MEMORY` but synchronous API only | No | Yes | No submit path used |
| Receipts | `false` | No | Yes | Capability readback |
| Reviews | `false` | No | Yes | Capability readback |
| Input waits | `false` | No | Yes | Capability readback |
| Plans/parallel plans | `false` | No | Yes | Capability readback |
| Conversation managers | `false` | No | Yes | Capability readback |
| Specialist conversation binding | Disabled | No | Manifest restart | No chat-session state created |
| Specialist write policy | Disabled | No | Manifest restart | Write/action denial |
| Specialist timeout | `PT30S` | No | Manifest restart | Deadline test |
| Input/output/token limits | Manifest-bounded | No | Manifest restart | Boundary tests |

Do not add JDBC execution state, receipts, human review, plans, managers,
conversation binding, actions, or asynchronous execution to the first proof.

## 6. Operational Configuration

| Area | Current observation | Target rule | Secret | Proof |
| --- | --- | --- | --- | --- |
| Runtime datasource | H2 file source default | Preserve current deployment choice for v1 | Credentials may be secret | Restart/readback |
| JPA schema mode | Source default `update` | Production remains app/migration-owned | No | Effective config |
| CORS origins | Empty source default | Exact browser origins only when needed | No | Allowed/denied origin |
| CORS credentials | `false` | No wildcard with credentials | No | Preflight test |
| Liveness/readiness | Actuator probes enabled | Safe execution/provider/vector diagnostics | No | Health gates |
| Logging | Runtime/framework INFO | No keys, PII, embeddings, prompts, provider payloads | No | Redaction scan |
| Build commit/time | Deployment environment dependent | Exact product source readback | No | `/actuator/info` or safe health |
| Framework version | Not currently first-class live evidence | Report exact resolved version | No | Safe health/build endpoint |
| Entity artifact hash | Platform release evidence | Report exact active hash | No | Admin/release readback |

## 7. Required Live Readback

Before canary deployment, populate a private operator record with:

```text
environment
Coolify application UUID/name
image digest
product commit
AI Fabric version
entity contract version and artifact hash
specialist manifest fingerprint
provider/model identifiers without credentials
embedding model and dimensions
vector provider and safe namespace/path fingerprint
auth ingress mode and issuer/audience identifiers
enabled execution capabilities
restart/reindex impact
last successful validation timestamp
```

Only non-secret values suitable for durable handoff may be copied back into
this tracked redacted matrix.
