# Private Configuration Authoring (010.37)

Runnable dedicated inference profile; disabled unless `loomai.authoring.enabled=true`
(`LOOMAI_AUTHORING_ENABLED=true`). No save client, mutation credentials, tools,
retrieval, embedding, action execution, publication or deployment in this path.
Consumes released AI Fabric 0.8.46; no framework changes or deployment. Local
real-provider qualification and actual ledger usage are recorded under
`verification/authoring/results/` (fictional inputs only).

## Platform Contract

`POST /api/private/configuration-authoring/generate`, JSON, private backend only.
Use the existing trusted-backend API key and signed private assertion headers.
Require `configuration:author`; chat scopes are neither necessary nor sufficient.
Issuer/audience must match the runtime's explicit existing auth allowlists.

Required request fields:

- `contractVersion`: `LOOMAI_CONFIGURATION_AUTHORING_V1`.
- `sessionId`, `turnId`: 1-128 ASCII letters, digits, `_` or `-` each.
- `resourceKind`: `DEPLOYMENT_DRAFT`, `PRIVATE_DATA_PLUGIN`, `ENGAGE_APP`.
- `task`: `ACTIONS`, `CONNECTIONS`, `DATA_PLUGIN`, `KNOWLEDGE`, `BEHAVIOUR`,
  `MODELS`, `ENGAGE`, `HUMAN_SUPPORT`, `SPECIALISTS`, `KIT`.
- `query`: nonblank, at most 8,000 characters.
- `currentConfiguration`, `catalogue`: JSON values, including dynamic maps/arrays.
- `sources`: up to 16 `{id,title,content}` objects, distinct IDs, limits
  128/512/32,000 characters respectively, nonblank.
- `priorTurns`: up to 24 `{role: USER|ASSISTANT, content}` objects,
  nonblank content at most 8,000 characters each. Sent as native chat messages.

The verified assertion `sessionId` must equal
`authoring:{sessionId}:{turnId}`. Only if there is no session claim may `subjectId`
supply that exact correlation. A matching subject cannot excuse a conflicting
session. Existing private assertions expose no independent request-ID claim;
`turnId` is bound through this full correlation, not an invented claim/replay store.

The assertion's customer/deployment/tenant identify the **dedicated shared
authoring service allocation**, not the resource being edited. All three must
exactly match the runtime budget configuration. Main owns the edited resource's
tenant authorization, allowed fields, revision checks, cancellation/supersession
and actual saves through existing resource services. These inputs cannot redirect
a runtime save, because this runtime never saves. Do not send mutation credentials
or actual secret values in catalogue/current config/imports.

## Responses And Failures

HTTP 200 preserves the model's execution envelope: `contractVersion`, `status`
(`READY|NEEDS_INPUT|EXPLAIN|UNSUPPORTED`), `message`, `changes`, `questions`,
`assumptions`, `sourceReferences`. No status is inferred from text. `message`
is at most 8,000 characters, matching Platform storage and history limits.

`changes[]` contains `path` (RFC 6901 pointer relative to current configuration),
`operation` (`SET|REMOVE|UPSERT|REMOVE_ITEM`), operation-dependent `key`, optional
arbitrary JSON `value`. SET/UPSERT require a present value, including explicit
null; UPSERT/REMOVE_ITEM require a nonblank string key (at most 512 characters).
SET/REMOVE do not use `key`: any supplied unused value, including null, is
preserved unchanged as non-authority, not validated or used to rewrite the
operation. Guidance asks the model to omit unused keys; consumers must dispatch
by operation, not key presence. Whole-response/tree bounds still apply.
System guidance describes
Platform's SET merge identities: `actions`/`params` use `name`, `sources` use `id`,
`knowledgeSources` use `sourceKey`, `datasets` use `datasetId`. Use explicit
REMOVE/REMOVE_ITEM for deletions, not omission from SET. Main enforces the actual
resource/task/path authority before saving. Questions preserve
`id`, `question`, optional `field`. Useful partial changes with NEEDS_INPUT remain
intact; no semantic quality gate.

Advisory metadata follows the fixed **REPORT_AND_PRESERVE** policy. It is not
mutation authority or an execution prerequisite. Missing/non-array `questions`,
`assumptions` or `sourceReferences` become safe empty arrays with visible
`diagnostics`. Valid items survive; malformed items and non-text question fields
are retained verbatim in diagnostics instead of crashing consumers. Guidance-only
item counts/lengths are not hard gates; the whole raw response/tree bounds remain.
No message, status, change, meaning or value is reconstructed or retried.

Only supplied source IDs enter the safe `sourceReferences` array. Unknown strings
are preserved as `AUTHORING_SOURCE_REFERENCE_UNVERIFIED` diagnostics with
`verified:false`; they must be shown as unverified plain text, never resolved,
linked as verified or used to authorize access. They do not abort useful changes.
Provider-supplied diagnostics are wrapped as unverified original metadata, not
accepted as runtime diagnostics. Main must preserve top-level `diagnostics` as
well as metadata and `runtime` through storage/API/UI.

Each diagnostic has `code`, JSON-pointer `field`, `message`,
`policy:"REPORT_AND_PRESERVE"`, and `originalValue` (including null/objects) or
`originalMissing:true`. Codes are `AUTHORING_METADATA_MISSING`,
`AUTHORING_METADATA_INVALID`, `AUTHORING_SOURCE_REFERENCE_UNVERIFIED`, and
`AUTHORING_PROVIDER_DIAGNOSTICS`. Runtime receipts also declare
`metadataRecoveryPolicy:"REPORT_AND_PRESERVE"`. Consumers render diagnostic data
as data, never HTML, authority, a source-link target or instructions.

Server-owned task instructions and output schema are selected before inference.
`schemaGuidedJsonObjectResponseParameters` intentionally chooses JSON-object
mode, retaining dynamic values. Imported/current/catalogue data are user input,
never system `context`. After whole-input validation, history is native USER/
ASSISTANT messages, followed by a USER `Authoring context data:` JSON message
without duplicated `query`/`priorTurns`. `.prompt(request.query())` supplies the
final USER message containing only the current query. This is prompt clarity,
not a framework ordering fix: released 0.8.46 preserves that order and requires
`.prompt()` or current `inputParts` alongside nonempty `.messages()`.
Whole raw provider output is parsed strictly before the
framework helper: no prose/fences, trailing JSON/text, duplicate keys or repair.
Exactly one `AICoreService.generateContent(..., GENERATION)` call maximum; helper
gets the already-validated response, `maxAttempts=1`, no call-error retry.

Server adds `runtime`: `requestId`, `modelCalls`, `deadlineAt`, `elapsedMs`,
`accountingPath=RUNTIME_PROVIDER_BUDGET`, `accountingReceipt` (actual ledger
ingress receipt), `model`, `providerRequestId`, `providerStatus`, `providerError`,
`usage`, `tokensUsed`, `providerProcessingTimeMs`, `metadataRecoveryPolicy`.
Usage/token fields are actual
provider values or null, never estimates or invented zeros. Financial truth
remains in the ledger.

Failures never return a fabricated model envelope. Generation failures return
HTTP 502 (`AUTHORING_PROVIDER_ERROR` / `AUTHORING_INVALID_OUTPUT`), 504
(`AUTHORING_TIMEOUT`), 503 (`AUTHORING_BUSY` / `AUTHORING_CANCELLED`), or 429
(`PROVIDER_BUDGET_*`), with `error: {code,message}` and the runtime receipt.
Provider exception text/raw output is not exposed. Auth/binding failures are
401/403; invalid request/input is 400; body above 262,144 bytes is 413. Responses
carrying proposals/receipts are `Cache-Control: no-store`.

Bounds protect spending/memory and the executable envelope, not semantic
confidence: total serialized request <=120,000 characters; JSON depth <=32,
nodes <=20,000, container children <=4,096; provider output <=96,000 characters;
changes <=128. Questions <=32 and assumptions/references <=64 are model guidance,
not hard acceptance requirements. The server schema
guides the model to Platform's narrower maximum of 30 changes. No silent truncation.
Each source content is <=32,000 characters even when Platform's imported prose
or context limit is 120,000. Platform must select/narrow bounded source excerpts
before inference; the runtime does not silently trim large source text. A source
over 32,000 gives HTTP 400 `AUTHORING_INVALID_REQUEST`; a combined serialized
request over 120,000 gives HTTP 400 `AUTHORING_INPUT_LIMIT`. Current config,
catalogue, history and JSON escaping also consume that combined request bound.
Deadline defaults to 45,000ms (100-120,000), output cap to 4,096 tokens (256-8,192),
via `loomai.authoring.deadline-ms` / `max-output-tokens` (corresponding
`LOOMAI_AUTHORING_DEADLINE_MS` / `LOOMAI_AUTHORING_MAX_OUTPUT_TOKENS`). Deadline
also cannot outlive the assertion. Four inference workers and eight queued
requests maximum. Budget scopes open/close on the actual worker, never on the
HTTP waiting thread. Timeout cancels the worker best-effort and suppresses late
proposals; a provider that ignores interruption can still spend until its own
transport timeout. Its existing captured ledger operation remains accountable;
unknown/ambiguous usage keeps the existing conservative hold. No refund/replay.

## Platform-Owned Validation Feedback

The 10 November extension keeps this endpoint at **one accounted inference per
HTTP request**. Platform may explicitly request up to two correction attempts
after the first response, using its actual resource validator and one overall
140-second turn deadline. It supplies server-owned
`catalogue.validationFeedback` with `attempt`, `maxAttempts`, `deadlineAt`, and,
on correction, safe `previousFindings`/`previousChanges`. The current configuration
is the cumulative unsaved candidate; the final query remains the original user
intent. Runtime instructions ask the model to correct those findings without
inventing bindings, expanding authority or disabling checks. Imported content
and findings remain evidence, not privileged instructions.

Platform binds each attempt to its own turn correlation, clamps the private
assertion/HTTP timeout to remaining time, and retains every runtime receipt.
The only output-rejection correction category is typed
`AUTHORING_INVALID_OUTPUT`; rejected raw output is not returned or replayed.
Provider/network/budget failures, missing authority/information and explicit
model stop decisions do not cause a correction. Runtime structured parsing keeps
`maxAttempts=1`; do not add a nested retry loop. No framework upgrade, new
allocation, ledger reset, source indexing or business execution is involved.

The UI's page-specific welcome/document-first commands are a separate proposed
follow-up, not part of this endpoint extension. Current implementation and hosted
qualification are tracked in Platform's dated validation-feedback evidence.

## Dedicated Boot

Use the same executable JAR with `SPRING_PROFILES_ACTIVE=configuration-authoring`.
The main application selects explicit minimal imports rather than the normal
serving component scan. The normal profile retains its existing auto-configuration,
scheduling and property setup. The dedicated profile imports the released
`AICoreService`, `AIProviderManager`, real OpenAI Spring AI adapter, structured JSON
helpers, existing private auth, existing provider-budget wiring, JDBC/Flyway,
HTTP and health. No vector/embedding/search/index/action registry/orchestration,
JPA, Quartz, chat, documents, connector or business database service is started.
Only authoring, existing private budget administration, errors and health routes
are registered. No public bootstrap/chat or operational business routes.

JDBC is **internal service state**, not a customer business database. Reuse the
existing runtime Flyway migrations and budget allocation lifecycle. Their other
internal metadata tables remain unused; no framework business-entity table creator
or execution worker is loaded. One isolated schema on an existing internal-state
PostgreSQL database is sufficient for this shared service, not a DB per customer.
Keep that schema/identity across restarts/upgrades. Startup never issues an
approval, resets accounting, initializes an allocation or activates paid work.

Required environment for the dedicated shared service (example staging identity):

```properties
SPRING_PROFILES_ACTIVE=configuration-authoring
LOOMAI_AUTHORING_ENABLED=true
AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE=VERIFIED_CONTEXT_REQUIRED
AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=<private shared service ingress secret>
AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=<private assertion signing secret>
AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=platform-authoring
AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=configuration-authoring-staging
AI_FABRIC_RUNTIME_CUSTOMER_ID=loomai-internal
AI_FABRIC_RUNTIME_DEPLOYMENT_ID=configuration-authoring-staging
AI_FABRIC_RUNTIME_TENANT_ID=loomai-internal
LOOMAI_RUNTIME_TARGET_PROFILE_ID=configuration-authoring-staging
LOOMAI_PROVIDER_BUDGET_ENVIRONMENT=staging
LOOMAI_PROVIDER_BUDGET_CREDENTIAL_IDENTITY=<sha256 identity of actual provider credential>
LOOMAI_PROVIDER_BUDGET_RATE_CARD_REFERENCE=openai-gpt4omini-gpt54mini-embedding3small-20261024-v2
LOOMAI_PROVIDER_BUDGET_OUTPUT_CAP_MODE=MAX_COMPLETION_TOKENS
OPENAI_ENABLED=true
OPENAI_API_KEY=<matching provider credential>
OPENAI_MODEL=gpt-5.4-mini
SPRING_DATASOURCE_URL=jdbc:postgresql://state-postgres:5432/loomai_internal?currentSchema=loomai_authoring_staging
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
SPRING_DATASOURCE_USERNAME=<internal state schema owner>
SPRING_DATASOURCE_PASSWORD=<internal state schema password>
SPRING_FLYWAY_SCHEMAS=loomai_authoring_staging
SPRING_FLYWAY_DEFAULT_SCHEMA=loomai_authoring_staging
```

The profile sets `loomai.provider-budget.enabled=true`, disables fallback, uses
4,096 output tokens and a 40-second provider timeout within the 45-second host
deadline. `gpt-5.4-mini` is supported by the existing reviewed v2 rate card and is
the profile default; `OPENAI_MODEL=gpt-4o-mini` also works with that card. No model
or rate-card code was added. The existing native SDK has `maxRetries(0)`.
For complex authoring calls, coordinate the client/proxy timeout above 120s and
set `LOOMAI_AUTHORING_DEADLINE_MS=120000`,
`LOOMAI_AUTHORING_MAX_OUTPUT_TOKENS=8192`, and
`LOOMAI_AUTHORING_PROVIDER_TIMEOUT_SECONDS=115`. The provider's actual output cap
is `max_completion_tokens=8192`; the existing Spring AI warning about both cap
properties explicitly confirms that maxCompletionTokens takes precedence.
The resolver validates OpenAI embedding defaults (`text-embedding-3-small`) as
part of the existing profile contract, but no embedding bean or invocation exists.

Use `currentSchema` **in the JDBC URL**, not only Hikari's schema option: the
existing ledger opens its own independent committed connections to the same URL.
Flyway and both connections must resolve the same service schema. No new database
creation, accounting engine, recovery, auto-funding or per-customer infrastructure.
Main owns live state schema/app provisioning and normal allocation initialization.

Build/run from `ai-infrastructure-module` with the environment above:

```sh
mvn -o -pl ai-fabric-runtime -DskipTests package
java -jar ai-fabric-runtime/target/ai-fabric-runtime-0.1.0-preview.jar
```

For a local-only boot, omit the PostgreSQL/Flyway schema variables. The profile
defaults to persistent H2 internal state at `./data/configuration-authoring-state`
(driver `org.h2.Driver`, user `sa`), not a business database. Binding localhost
uses `--server.address=127.0.0.1 --server.port=18097`; select a free port.

`GET /actuator/health/liveness` is 200 UP before funding. Readiness and aggregate
health are 503 until an existing allocation is initialized and activated;
`GET /actuator/health/readiness` then becomes 200 UP. This is accounting/service
readiness, not a paid model quality/credential probe. Do not remove budget from
readiness to make an unfunded service green.
Container startup checks can use liveness while main initializes the allocation;
Platform must require readiness UP before admitting authoring traffic. This avoids
an aggregate-health startup deadlock without weakening accounting admission.

## Existing Initialize/Activate JSON

Both calls require the existing trusted-backend key plus a verified private
assertion bound to this service's customer/deployment/tenant, issuer and audience,
with **only the separate admin operation's** `runtime:admin:provider-budget:write`
scope. Authoring assertions keep `configuration:author` only. Admin readback
`GET /api/admin/provider-budget` requires `runtime:admin:overview`.

The packaged helper reads an **existing approved** `ProviderBudgetLedger.Approval`
JSON from stdin and prints the exact initialize body using
`RuntimeBudgetAllocation.fingerprint`. Its activate mode reads the actual
initialize response and prints the existing activation record. It does not start
Spring, issue an approval, choose limits, contact a service or change accounting.
No new funding or fingerprint implementation is involved:

```sh
java -Dloader.main=com.ai.fabric.runtime.authoring.ConfigurationAuthoringAllocationPayload \
  -cp ai-fabric-runtime/target/ai-fabric-runtime-0.1.0-preview.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher initialize < approval.json > initialize.json
java -Dloader.main=com.ai.fabric.runtime.authoring.ConfigurationAuthoringAllocationPayload \
  -cp ai-fabric-runtime/target/ai-fabric-runtime-0.1.0-preview.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher activate < initialize-response.json > activate.json
```

`POST /api/admin/provider-budget/initialize` uses this exact existing shape:

```json
{
  "approval": {
    "id": "<existing Platform prepaid approval ID>",
    "scope": {
      "customerId": "loomai-internal",
      "deploymentId": "configuration-authoring-staging",
      "targetProfileId": "configuration-authoring-staging",
      "environment": "staging",
      "credentialIdentity": "sha256:<64 lower-case hex digits for actual API credential>",
      "rateCardReference": "openai-gpt4omini-gpt54mini-embedding3small-20261024-v2"
    },
    "limits": {
      "chatAttempts": 20,
      "totalTokens": 1000000,
      "costNanoUsd": 5000000000
    },
    "expiresAt": "<existing Platform approval ISO-8601 expiry>"
  },
  "runtimeFingerprint": "<existing allocation compiler's 64-hex fingerprint>"
}
```

The limits above illustrate 20 turns / 1,000,000 tokens / USD 5, **not a new
approval**. Main supplies the actual existing approved prepaid limits and expiry.
Use the existing allocation compiler (runtime contract reference:
`RuntimeBudgetAllocation.fingerprint(approval)`); do not hash arbitrary JSON,
include tenantId in this scope, or invent a second funding/fingerprint algorithm.
Tenant identity is enforced separately in the runtime configuration/assertion.

Durably retain the initialize response, then send
`POST /api/admin/provider-budget/activate` using **those exact returned values**:

```json
{
  "approvalId": "<initializeResponse.approvalId>",
  "storeUuid": "<initializeResponse.storeUuid>",
  "runtimeFingerprint": "<initializeResponse.runtimeFingerprint>"
}
```

The real HTTP boot fixtures generate fully concrete, fingerprint-valid initialize
and activate requests plus responses and readiness receipts under
`target/configuration-authoring-boot-proof/h2/` and `.../postgres/`. Their fixture
credential is deliberately not a real provider key and their allocation allows
zero interactive turns; this proves boot/administration/denial without paid
inference. Do not deploy that fixture allocation or copy its store UUID to another
database. Live uses main's actual shared-service prepaid allocation.

## Local Verification

```sh
mvn -o -pl ai-fabric-runtime -Dtest='*Authoring*,RuntimeBudgetTurnScopesTest,RuntimeBudgetBindingServiceTest,RuntimeProviderBudgetSpringConfigurationTest,AIFabricRuntimeApplicationTest' test
mvn -o -pl ai-fabric-runtime clean package
```

Run from `ai-infrastructure-module`. The dedicated fixtures start a real server
and real released provider wiring without mocks, verify excluded bean/route types,
test existing private initialize/activate and HTTP health, and prove that a shared
PostgreSQL database retains unrelated state with all runtime writes isolated in
the service schema. They do not make a paid model call or prove hosted adoption.

2026-11-10 original profile verification: 88 focused tests passed, followed by
`clean package`: 723 tests, zero failures/errors/skips, executable JAR produced.
The subsequently added pure allocation helper passed 3/3 focused tests and a
successful package. Exact packaged-JAR local boot returned liveness200,
unfunded readiness503, then readiness200 after existing initialize/activate.

The metadata-policy follow-up passed 42 focused parser/service tests with zero
failures/errors/skips, then full `package`: **737 tests, zero failures/errors/skips**,
finished 2026-11-10T01:23:38Z. Eight offline Node fixture/accounting-evidence tests
also passed, reconciling all twenty real provider attempts and token totals.
Post-policy real-provider results are recorded in the qualification report.
No deployment.

## Bounded Real Qualification

`verification/authoring/qualify-real-provider.mjs` is an explicit operator-only
fixture, never a Maven/CI task. It imports the existing private operations helper,
reads only the approved internal **staging** runtime key into memory, generates
local auth secrets and starts the packaged JAR on localhost:18097 with persistent
internal H2 state. It never uses production credentials, calls fictional business
APIs or saves target drafts. No key, signed assertion or local auth secret is
written to evidence; runtime logs are redacted before persistence.

The initial phase initializes/activates an explicit current-task-owner-approved
service allocation through the existing admin contracts and pure payload helper:
20 turns, 2,000,000 tokens, USD5, six-hour expiry. This is not a claimed existing
customer allowance. The follow-up phase reuses the exact store/approval and
continues counters15..20; it neither resets accounting nor tops up funding.
Each phase/case records its intent before its single request. Existing result
directories prevent silent repetition or overwriting. There is no retry loop.

The initial 15 cases predate the metadata-policy correction; preserve those
results, including six visible errors. The owner-requested post-policy phase
contains five explicitly identified regression cases, not hidden repairs or
retroactive success claims. Whole responses, formal checks, actual usage,
deadlines and per-case ledger readback are retained under `verification/authoring/results/`.
See [Qualification Report](verification/authoring/QUALIFICATION.md) for the manual
semantic assessment and remaining limits. These are inference checks, not
Platform save/compiler acceptance, hosted adoption or deployment evidence.
