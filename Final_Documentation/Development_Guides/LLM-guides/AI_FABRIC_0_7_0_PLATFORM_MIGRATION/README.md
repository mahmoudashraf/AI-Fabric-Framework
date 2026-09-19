# Platform AI Fabric 0.7.0 Migration

Status: **GATE A COMPLETE; SUPPORTED FLEET UPGRADED; CANONICAL GATES PASSED; SHOPIFY QUALITY EXCEPTION OPEN**

This is the one-way LoomAI Platform consumer upgrade to the immutable AI
Fabric `0.7.0` release. Gate A changes the framework baseline only. It keeps
declarative specialist chains disabled and does not add a chain table, chain
resource, private chain secret, chain route, compatibility reader, or product
claim.

## Immutable Identities

- AI Fabric tag: `ai-fabric-framework-v0.7.0`
- AI Fabric release commit: `5b075b66384dc5b756b3b3dd12efaf896ce9a50b`
- framework guidance commit: `7ac32985`
- LoomAI private source: `2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264`
- Maven foundation: `io.github.loom-ai-labs:ai-fabric-bom:0.7.0`
- required live control: `AI_EXECUTION_SPECIALIST_CHAINS_ENABLED=false`

## Source And Package Evidence

- Product, connector, runtime, relay, and Platform backend builds passed
  without skipped normal tests. The focused totals were `10` connector, `164`
  runtime, `35` relay, and `737` Platform backend tests, plus all five product
  reactor modules.
- An empty-cache Maven Central build passed all `164` runtime tests.
- The packaged runtime contains `ai-fabric-execution-0.7.0.jar` and no
  `0.6.1` AI Fabric library.
- Runtime defaults and generated deployment environment keep specialist
  chains disabled.
- No Gate B schema, Gate C resource/secret/route, or Gate D product topology
  was added.

## Hosted Gate A Releases

Every release below is `APPLIED_VERIFIED`, has verification `PASSED`, has
provisioning `ACTIVE`, returns runtime health `UP`, and reached a terminal
Coolify `finished` deployment. Live readback reports AI Fabric `0.7.0`, chains
disabled, and private source commit
`2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264`.

| Environment | Runtime family | Version | Release |
| --- | --- | --- | --- |
| Staging | Marketplace | `ver-aeb99ac6` | `rel-c16e267e` |
| Staging | Ecommerce | `ver-7167bd27` | `rel-235185ed` |
| Staging | Shopify | `ver-97357cc1` | `rel-45ae6acf` |
| Production | Marketplace | `ver-d43d5ffb` | `rel-ae6b1d12` |
| Production | Ecommerce | `ver-6e9fde0c` | `rel-667353a7` |
| Production | ProdUS | `ver-470ba005` | `rel-ed852b3e` |
| Production | Shopify | `ver-f44f178d` | `rel-6ec95cf0` |

Config-only exports were captured before every apply. No platform-wide
Coolify backup or secret-bearing export was created.

## Hosted Behavior And Isolation Evidence

- Two staging deployments each retrieved only their own temporary specialist
  fact. Cross-deployment requests exposed no foreign evidence.
- Missing runtime authentication returned `401`.
- Missing trusted tenant or deployment claims returned `403`.
- Incorrect trusted tenant/deployment boundaries produced no evidence.
- Temporary canary documents were deleted after verification.
- ProdUS assignment resolves to `dep-f6abfa06` through both supported
  assignment hostnames.
- ProdUS Milvus readback reports `203` indexed documents across `14` entity
  types, including `90` service modules and `15` package templates.
- ProdUS request `rag-0152bfb3-840a-4231-a2b1-aa0ca4f19f84` returned
  `service-module:api-security-review` with exact tenant `ten-bf9f61fb` and
  deployment `dep-f6abfa06`; a wrong-tenant query returned no evidence.
- No ProdUS-side configuration change was required.

## Release Verification

| Verification | Staging | Production | Result |
| --- | --- | --- | --- |
| Canonical suite | `vsr-8df8715d` | `vsr-da3641f3` | Passed |
| Partner suite | `vsr-dd47edb2` | `vsr-19240cf6` | Passed |
| Thinker suite | `vsr-cbb44501` | `vsr-bc98e4d9` | Passed |
| Full aggregate | `vsr-8e8306d3` (`8/11`) | `vsr-c9bb743e` (`9/11`) | Failed only at owner-deferred Shopify first-product answer quality |

The canonical suites used controlled repair for an Ecommerce vectorization
bootstrap and then passed in sync. Their optional historical Qdrant stage
remains non-blocking and outside the supported path.

The full aggregate records remain `FAILED`; they must not be reported as
green. The user-approved Shopify answer-quality exception permits this
framework baseline upgrade, but it does not close the separate Shopify product
quality work.

## Productization Consequence

Gate A proves that existing LoomAI behavior is preserved on AI Fabric `0.7.0`.
It does not make declarative specialist chains Platform-selectable or
market-ready. Current maturity is:

- AI Fabric `SpecialistChain`: `FRAMEWORK_AVAILABLE`;
- private chain runtime: packaged but disabled;
- Marketplace `SPECIALIST` contribution: planned;
- Gate B deployment-local chain schema: not started;
- Gate C one-worker mechanics canary: not started;
- Gate D genuine multi-specialist product: not started.

Any continuation must begin with a separately reviewed Gate B decision. Gate C
may use the existing `deployment-knowledge-specialist@1` only to prove
mechanics. A reusable Agentic Specialist Team claim requires a genuinely
distinct second read-only worker and the full Gate D hosted evidence.
