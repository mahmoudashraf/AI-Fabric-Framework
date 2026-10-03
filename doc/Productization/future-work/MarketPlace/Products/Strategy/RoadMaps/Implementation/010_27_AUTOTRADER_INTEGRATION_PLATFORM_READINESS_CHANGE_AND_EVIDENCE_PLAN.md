# 010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan

- **Status:** Generic external-provider substrate is staging-hosted proven as
  `HOSTED_GENERIC_SUBSTRATE_VERIFIED`, and the separate fictional dealership
  composition passed `DEALERSHIP_DEMO_READY` on 2026-09-30. The generic
  targeted-record path, provider-backed action routing, public-contract
  simulator profile, Marketplace fixtures, and protected demo control are
  implemented in source on 2026-10-03; their replacement-deployment hosted
  canary is pending. Every
  partner-gated Auto Trader sandbox, data-rights, advertiser, package,
  certification, and production gate remains open. LoomAI is not yet entitled
  to claim Auto Trader integration readiness.
- **Date:** 2026-09-25
- **Last contract review:** 2026-10-03
- **Current LoomAI baseline:** AI Fabric `0.8.8`, Platform `Platform-V11`, V04
  deployment lifecycle
- **Product boundary:** one dealership, one deployment, one server-owned Auto
  Trader advertiser scope
- **Architecture decision:** Marketplace plugin-first and deployment-local; no
  standalone Auto Trader bridge and no central Platform data-plane proxy
- **2026-10-03 design refinement:** the Auto Trader `DATA` package owns provider
  semantics while the existing deployment-local Generic REST Connector owns
  reusable baseline, targeted-record reconciliation, webhook, mapping, and
  runtime Data Sync mechanics. The dealership website/backend remains an
  independent application and is not made dependent on Auto Trader.
- **2026-10-03 simulator refinement:** retain the two neutral simulator profiles
  for generic-substrate evidence, add a separately versioned public-document-
  informed Stock Sync fixture for contract rehearsal, and expose any meeting
  mutation control only through a protected demo-operator backend adapter. This
  fixture is not an Auto Trader sandbox, emulator, certification, or readiness
  claim.
- **Compatibility posture:** current-only greenfield contract; no legacy mode or
  parallel integration contract
- **Hosted evidence:** generic-substrate verifier source `79b23348f`, runtime
  source `86abb0320c5af2397231cf40077194ba0435efd2`, completed
  `2026-09-28T11:46:36Z`; dealership deployment `dep-f023c863`, runtime image
  source `9db6bc92bd06814c2b27b221dae2ac2e64dbe85a`, completed 2026-09-30

Related plans:

- [010.25 Auto Trader Connect LoomAI Capability Productization Analysis](010_25_AUTOTRADER_CONNECT_LOOMAI_CAPABILITY_PRODUCTIZATION_ANALYSIS.md)
- [010.26 Auto Trader Dealership First Release And Meeting Demo Plan](010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md)
- [010.24 LoomAI File Document Indexing Platform Support Plan](010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md)
- [Marketplace Plugin Manifest Reference](../../../../../../../../Final_Documentation/Development_Guides/MARKETPLACE_PLUGIN_MANIFEST_REFERENCE.md)
- [Generic REST API Connector Guide](../../../../../../../../Final_Documentation/Development_Guides/GENERIC_REST_API_CONNECTOR_GUIDE.md)

## 1. Executive Verdict

LoomAI now has a hosted-proven provider-neutral substrate for deployment-local
external HTTP integrations using complete-source reconciliation. The generic
targeted current-record refinement and mixed provider/dealership composition
are implemented and locally verified; replacement-deployment and browser
evidence are still pending. The Platform is not ready to claim an Auto Trader
integration.

Implemented foundations now include:

- Marketplace `TEMPLATE`, `DATA`, `ACTION`, and `INFERENCE_PROFILE` packages;
- the V04 draft, validate, version, release, apply, verify, export, import, and
  assignment lifecycle;
- one self-contained runtime and Generic REST Connector per deployment;
- deployment-local conversational orchestration, Data Sync, indexing, vector
  retrieval, structured action results, confirmation, and persistence;
- opt-in runtime-issued public anonymous browser tokens and direct secure
  `/api/chat/me/*` routes, plus the reusable LoomAI Max Mode/Companion chat
  application that consumes them;
- deployment and Marketplace secret-reference models;
- bounded API-key and form-token connection profiles with approved hosts,
  token caching/refresh, fair-usage controls, and typed provider failures;
- immutable protected-resource bindings and server-owned path/query/header/body
  injection;
- Marketplace `EXTERNAL_SYNC_HTTP`/`HTTP_JSON`, durable paged/cursor sync,
  tombstones, bounded current-record fetch, runtime Data Sync, and indexing-work
  reconciliation;
- raw-body HMAC webhook verification, deduplication, retry, replay, dead-letter,
  full-source or targeted latest-state convergence, and durable verified record
  keys;
- provider-profile action routing with protected-resource injection and
  fail-closed collection filtering before action results become model facts;
- connector-owned PostgreSQL schema/Flyway state and a restricted deployment-
  scoped role whose privileged bootstrap credentials are removed after setup;
- private connector/runtime networking and deployment-scoped service auth;
- opt-in customer-backend ingestion discovery, safe readiness/work projections,
  Platform operations APIs, and an Integrations workspace; and
- DRAFT-only provider package reservations that deliberately contain no
  executable or published Auto Trader contract.

The hosted generic gate passed on 2026-09-28 against a separately deployed
HTTPS simulator and two isolated staging deployments. It covered immutable
Platform apply, credential/resource isolation, sync, indexing, grounded
retrieval, updates, deletion/tombstones, scheduled repair, provider failures,
signed events, replay/dead-letter recovery, restart durability, immutable
rollback, destructive PostgreSQL backup/restore, convergence, and hard
decommission. This permits only `HOSTED_GENERIC_SUBSTRATE_VERIFIED`.

The remaining evidence/product work is material:

1. Auto Trader sandbox access is partner-provisioned and LoomAI does not yet
   have recorded sandbox credentials, grants, test advertiser, or test stock.
2. The fictional dealership dataset, ordinary customer demo application, and
   direct-anonymous Max Mode composition now pass hosted indexing, retrieval,
   read-action, confirmation, staff-inbox, restart, and browser gates against
   assigned deployment `dep-f023c863`. This proves only the bounded dealership
   demonstration, not an Auto Trader connection. Its source is being replaced
   by the versioned public-contract simulator composition; that replacement is
   not live-proven yet.
3. Public-document-informed DATA/ACTION/TEMPLATE fixtures now exercise the
   reviewed contract subset. Customer-facing Auto Trader package versions
   cannot be published responsibly until the granted sandbox routes, schemas,
   advertiser, webhook rules, data rights, and validation requirements are
   supplied.
4. No real Auto Trader sandbox or production canary has passed.
5. Public anonymous chat now has a deployment-local same-session renewal
   contract and the generic widget consumes it. Invalid/expired renewal still
   fails closed by clearing stale conversation and pending-confirmation state.

To avoid making partner onboarding the critical path for LoomAI engineering,
the hosted generic gate used a separately deployed, provider-neutral vehicle
inventory simulator. It proved the real deployment integration mechanics
against deterministic HTTPS APIs and signed events. It was not an Auto Trader
sandbox, substitute, emulator claim, or source of Auto Trader evidence.

Therefore:

> LoomAI may claim that an approved dealership demo is powered by LoomAI. It
> must not claim Auto Trader sandbox support, Auto Trader production support,
> or an Auto Trader-ready deployment template until the corresponding gates in
> this document pass.

## 2. Readiness Vocabulary

Readiness must be stated at the exact evidence level. Do not use the unqualified
phrase `Auto Trader ready`.

| State | Permitted claim | Required evidence |
| --- | --- | --- |
| `HOSTED_GENERIC_SUBSTRATE_VERIFIED` | LoomAI has proven its deployment-local external-provider mechanics against controlled hosted provider contracts | Separate HTTPS simulator, two materially different provider profiles, two isolated deployments, real Platform apply/index/restart/recovery/decommission evidence |
| `DEALERSHIP_DEMO_READY` | LoomAI can demonstrate dealership inventory indexing, retrieval, direct browser chat, comparison, and a confirmed dealership-owned lead action | Approved demonstration dataset, real LoomAI deployment, direct anonymous Max Mode/Companion proof, real model/embedding/vector providers, complete live demo gate |
| `AUTOTRADER_SANDBOX_ENABLED` | The exact deployment composition can connect to the granted Auto Trader sandbox | Partner-provisioned sandbox identity, exact grants, authorized test advertiser, generic substrate complete, authentication/preflight succeeds |
| `AUTOTRADER_SANDBOX_VERIFIED` | The named plugin/template version passed the named Auto Trader sandbox scenarios | Real baseline/read/webhook canaries, isolation, failures, work reconciliation, source evidence, Auto Trader validation evidence |
| `AUTOTRADER_PRODUCTION_READY` | The exact immutable composition is approved for the named production capabilities and advertiser | Separate production bindings, written rights, applicable go-live checks, production canary, LoomAI staging/production release gates |

One state never implies the next. In particular:

- an invented or dealership-owned demo dataset is not sandbox evidence;
- sandbox success is not production approval;
- one capability grant does not authorize another capability;
- one advertiser does not authorize another advertiser; and
- a healthy runtime does not prove source sync, indexing, retrieval, or data
  rights.

## 3. External P0 Access Gate

### 3.1 Official documentation finding

Auto Trader's official documentation explicitly refers to a sandbox and to
testing with provided credentials. It also states that production API access is
granted only after capability-specific tests, with validation through a
demonstration, API call logs, and sometimes Auto Trader database checks. An
Integration Manager participates in the testing/go-live process.

The reviewed public pages do not provide anonymous credentials or a
self-service sandbox enrollment flow. LoomAI must treat sandbox access as a
partner-onboarding dependency until Auto Trader provides a different written
process.

### 3.2 Required onboarding package

Before `AUTOTRADER_SANDBOX_ENABLED`, obtain:

- confirmed integration-partner and commercial ownership;
- sandbox authentication and API base URLs;
- provisioned sandbox client/API credentials;
- the integration identity associated with those credentials;
- exact enabled capability names and scopes;
- one authorized test `advertiserId` for the first dealership boundary;
- representative baseline stock and capability-specific test records or VRMs;
- webhook registration instructions, hash/signature material, retry semantics,
  and a supported test-event or replay mechanism;
- rate limits and fair-usage rules per granted service;
- contact and escalation route for data, token, webhook, and call-log issues;
  and
- the exact validation/go-live checklist Auto Trader will apply.

Record the non-secret grant metadata in Platform configuration and operational
evidence. Store credential values only through deployment secret bindings.

### 3.3 Sandbox limitations

Tests must not assume sandbox and production datasets are identical. Auto
Trader documents that:

- newer vehicles and recent registration-plate changes may be absent from
  sandbox; and
- sandbox valuations or metrics may differ from production because the
  environments use different datasets.

Use environment-specific fixtures and expected assertions. Do not weaken
identity, advertiser, schema, or security assertions to accommodate different
business values.

Current public guidance also presents different pause examples for `429` and
`503` responses across the Help Centre and developer collection. Keep these
durations as provider-package policy and record the Integration Manager's
capability-specific instruction as release evidence; never compile either
public example into generic connector defaults.

### 3.4 External blocker behavior

Lack of sandbox access must not block the LoomAI-only meeting demo. It does
block:

- live Auto Trader API development;
- provider authentication verification;
- advertiser membership verification;
- Stock Sync/webhook proof;
- Auto Trader call-log validation; and
- any Auto Trader readiness claim.

The customer-facing fallback is a clearly labelled dealership demonstration
dataset, not a mocked Auto Trader connection. Separately, engineering may use
the provider-neutral simulator defined below to close generic hosted mechanics.
Neither its service, UI, evidence, nor package labels may say that Auto Trader
is connected, simulated, certified, or verified.

### 3.5 Provider simulator policy

Create a small standalone `External Vehicle Provider Simulator` for internal
verification. It is an external test dependency, not a Platform data-plane
component, Marketplace product, customer bridge, or deployment runtime module.

The simulator must:

- run behind its own HTTPS hostname and independent state store;
- contain only fictitious, non-personal vehicle and dealership records;
- expose one form-token/page-size/query-bound provider profile;
- expose a materially different API-key/cursor/path- or header-bound profile;
- enforce one opaque account/resource per credential and deny cross-account
  reads;
- support deterministic create, update, delete, tombstone, pagination, cursor,
  token-expiry, and source-version scenarios;
- emit valid, duplicate, delayed, out-of-order, malformed, wrong-resource, and
  wrong-signature webhook scenarios;
- inject deterministic `401`, `403`, `429`, `503`, timeout, malformed-response,
  and partial-page failures;
- keep its mutation/failure-control API separate from provider data APIs and
  authorize it only to the verification runner/operator; and
- publish a fixture version and reset identifier in every evidence run.

It may reproduce generic mechanical shapes visible in public documentation,
but it must not use Auto Trader logos, real inventory, credentials, advertiser
identities, proprietary schemas, or undocumented behavior. Any assumption made
by the simulator is test-fixture policy, never an Auto Trader package fact.

Passing the simulator proves only `HOSTED_GENERIC_SUBSTRATE_VERIFIED`. It does
not satisfy `AUTOTRADER_SANDBOX_ENABLED`, provider call-log validation, data
rights, or any production gate.

### 3.6 Public-document contract fidelity and triggerable demo flow

The simulator has two different jobs, and their evidence must remain separate:

1. **Neutral substrate profiles A and B** prove that the connector is generic.
   Their invented routes and payloads deliberately do not claim provider wire
   compatibility.
2. **A versioned public-document-informed Stock Sync fixture** rehearses the
   provider contract visible in Auto Trader's current public Help Centre and
   official Partner Starter Collection. It remains synthetic and cannot replace
   the granted sandbox contract or Auto Trader validation.

The public material reviewed on 2026-10-03 establishes the following contract
targets. The current neutral profile A is similar in a few mechanics but is not
wire-compatible:

| Concern | Public Auto Trader contract target | Current neutral profile A | Required fixture treatment |
| --- | --- | --- | --- |
| Authentication | Form-encoded `POST /authenticate` with `key` and `secret`; response contains `access_token` and `expires_at`; reuse the short-lived bearer token rather than authenticating for every call | Form-token exchange exists under an invented prefixed route and includes simulator-owned fields | Mirror the published request/response names and token reuse/expiry behavior in an isolated fixture profile |
| Resource scope | API calls are performed for a server-owned `advertiserId` | Uses an invented query-bound `account` | Bind one synthetic advertiser identifier and deny caller/model override or cross-advertiser reads |
| Baseline stock | Advertiser-scoped paged Stock API read using `page` and `pageSize` | Paged `/vehicles` response with an invented projection | Mirror the published stock route, selectors, pagination envelope, and bounded fictional stock projection |
| Current-record reconciliation | Stock identity is the provider `stockId`; a notification is a signal to obtain current state | Generic detail route uses `vehicleId` | Extract one bounded `stockId`, fetch the current synthetic record through the same provider profile, and reuse the baseline mapper |
| Notification | HTTP `PUT`, `AutoTrader-Signature` containing timestamp/hash components, event type `STOCK_UPDATE`, advertiser identity and `data.metadata.stockId` | HTTP `POST`, `X-Simulator-A-Signature`, event type `vehicle.changed`, and top-level account/vehicle fields | Emit the published method, header grammar and documented bounded identity paths over raw signed bytes |
| Visibility/lifecycle | Publication and lifecycle state determine whether stock remains buyer-visible | Invented `active`, `reserved`, `sold`, and `deleted` state | Map documented publication/lifecycle examples into normalized active/tombstone behavior without inventing production policy |

Contract provenance is part of the fixture:

- every provider-shaped field, method, header, selector, and mapping records its
  official source URL, review date, fixture version, and status as `PUBLIC_DOC`
  or `PARTNER_CONFIRMED`;
- current Help Centre and non-deprecated Partner Starter Collection material is
  the preferred public source;
- any older official example explicitly labelled deprecated may inform a test
  hypothesis only, must be marked `PROVISIONAL`, and cannot become a package
  release fact without confirmation from the current collection or Integration
  Manager;
- the granted sandbox contract supersedes the documentation-informed fixture;
  drift is resolved by publishing a new immutable fixture/package version, not
  by silently changing old evidence; and
- no real credential, advertiser, registration, VIN, stock, logo, endorsement,
  or undocumented behavior enters the simulator.

For the meeting, the simulator may also be **triggerable** from a protected
staff/demo surface. This is operator tooling, not dealership inventory
integration:

```text
authenticated demo staff control
  -> dealership demo backend operator endpoint
  -> protected simulator upsert/delete control
  -> protected simulator signed-event control
  -> deployment-specific connector webhook
  -> verified record-key extraction
  -> current-record fetch (or current full-source reconcile until targeted mode exists)
  -> Data Sync/index work reconciliation
  -> buyer chat can discover the changed fictional stock
```

The browser, model, deployment runtime, and connector never receive the
simulator control key. The backend owns the fixed simulator profile/account,
allowed scenario IDs, deployment webhook destination, and secrets; request
input cannot select an arbitrary account, target URL, callback, or credential.
The endpoint requires staff authentication, CSRF protection, idempotency, rate
limits, and an explicit demo-only feature flag. It returns a sanitized staged
receipt for provider mutation, event delivery, connector acknowledgement,
terminal indexing, and searchability. It is disabled or absent from production
customer templates. The dealership backend's normal inventory API remains
independent and does not start sourcing its displayed catalogue from the
simulator.

## 4. Current LoomAI Implementation Audit

This audit was refreshed against the implementation and hosted evidence present
on 2026-10-03.

| Area | Current evidence | Verdict for Auto Trader |
| --- | --- | --- |
| Marketplace lifecycle | Existing install, compiler, V04 validation, immutable version/release/apply, verification, export/import | Reuse |
| Marketplace plugin types | `TEMPLATE`, `ACTION`, `DATA`, `INFERENCE_PROFILE`, and governed specialist support exist | Reuse; no `AUTOTRADER` plugin type |
| Plugin secret references | Install forms and install records support `secretRef` values | Reuse, then verify external-provider provisioning and redaction end to end |
| Connector routes | `RestRoutingConfig.ActionRoute` supports bounded method/path, query/body/header templates, timeout, response projection, and authz preflight | Reuse and harden |
| Connector upstream auth | Typed deployment-local profiles support API key and bounded form-token exchange, absolute/relative expiry, single-flight refresh, approved token host, and secret references | Implemented locally; exact Auto Trader profile remains package/external evidence |
| Connector state | Sync cursor/source version, record identity/fingerprint, indexing work, webhook dedupe/replay/dead-letter, and provider correlation are JDBC/Flyway-backed | Hosted restart and destructive restore evidence passed for the generic substrate |
| Connector fair usage | Per-profile concurrency, interval, retry status/backoff, and `429`/`503` pauses are bounded and package-configured | Implemented locally; exact provider policy pending written confirmation |
| Connector persistence | Deployment PostgreSQL is reused through connector-owned schema/role; every expected standard/preview bootstrap row must be found, deleted, and proven absent before connector restart on its restricted role | Hosted Coolify lifecycle and PostgreSQL backup/restore passed; ownership and ACLs are preserved for restricted roles |
| Marketplace DATA modes | `EXTERNAL_SYNC_HTTP` with `HTTP_JSON` is validated, compiled, hashed, exported/imported, provisioned, and capability-gated | Implemented locally |
| HTTP DATA execution | Page/size and cursor sources execute in the deployment connector, validate every record against the protected resource, and push only normalized operations to the colocated runtime | Hosted two-profile/two-deployment isolation, indexing, retrieval, mutation, and failure proof passed |
| Current dealership demo source | The live evidence still reflects six fictional rows previously pushed from the independent dealership backend. Current source removes that push path, enables provider-backed DATA sync, and keeps the backend independent for presentation and lead writes | Replacement deployment and hosted canary pending; no Auto Trader claim |
| External binding precedent | Document Knowledge Operations now provides target-scoped customer-storage bindings, secret references, safe readback, export/import boundaries, lifecycle cleanup, and operations UI | Reuse/generalize the lifecycle pattern; do not overload the document-storage-specific contract |
| Runtime Data Sync | Deployment runtime exposes batch/upsert/delete and vector-space contracts | Reuse as the normalized indexing boundary |
| Index work reconciliation | Runtime admin exposes per-work indexing status and vector overview | Reuse with customer-safe projection |
| Provider inbound webhook | Deployment connector exposes per-source signed ingress with raw-body verification, replay-window checks, durable dedupe, bounded reconciliation, retry, replay, and dead-letter state | Hosted TLS-originated signature, duplicate, ordering, rejection, replay, and dead-letter proof passed |
| Webhook reconciliation granularity | `FULL_SOURCE` and `FETCH_CURRENT_RECORD` are manifest-owned strategies. The targeted path extracts a bounded key only after signature/event/resource validation, persists it, fetches current state, reuses the baseline mapper, and submits one upsert/delete | Implemented and focused-test verified; hosted targeted canary pending |
| Existing runtime webhook code | Current runtime webhook tables/admin surface manage outbound action-result delivery | Do not misrepresent as inbound provider webhook support |
| Assignment endpoint catalog | Internal connector/runtime traffic stays private; templates may opt in to backend-only ingestion batch, work-status, and readiness URLs with exact operation flags | Implemented locally; browser exposure is prohibited |
| Protected resource binding | Typed immutable profile/resource/grant contracts compile server-owned values into provider requests; records and events must match the same binding | Implemented locally; Auto Trader's one-advertiser rule remains package-owned |
| Operations | Runtime proxy, Platform operations API, audit events, and Integrations UI expose bounded source/auth/index/webhook state plus controlled reconcile/replay; source record IDs and payload/resource fingerprints are omitted | Implemented locally |
| Rejected webhook retention | Invalid/unauthenticated attempts increment durable fixed-cardinality source/error counters without storing attacker-controlled event rows, payloads, identities, or hashes | Hosted rejection, recovery, and restart paths passed |
| Generated-secret cleanup | Hard delete clears deployment-generated connector service/database credentials after infrastructure cleanup succeeds | Hosted hard-decommission readback passed with no cleanup failures |
| Auto Trader packages | DRAFT catalog reservations exist, with no executable versions | Correctly blocked pending partner grants and schemas |
| Provider-backed action routing | Provider-profile routes resolve through `ProviderHttpClient` independently of the application upstream. Protected collection filters remove non-published records before response templates or model facts are built | Implemented and focused-test verified; hosted mixed-owner canary pending |
| Generic hosted evidence | Independent HTTPS simulator, two isolated deployments, exact immutable runtime artifact, full lifecycle matrix, and cleanup | `HOSTED_GENERIC_SUBSTRATE_VERIFIED` on staging; this is not named-provider evidence |
| Public-document simulator fidelity | A separate `autotrader` profile mirrors the reviewed public subset: form-encoded `POST /authenticate`, `access_token`/`expires_at`, bearer reuse, `GET /stock`, advertiser/page/pageSize/stockId/lifecycleState, stock envelope, `PUT` notification, `AutoTrader-Signature`, and documented event/response semantics | Implemented with synthetic records and provenance tests; hosted contract canary pending and never a sandbox/certification claim |
| Auto Trader hosted evidence | None | Blocking |

### 4.1 Implementation checkpoint

The implementation checkpoint includes:

- generic code only: no Auto Trader route, field, signature header, retry
  duration, or business constant is compiled into Java;
- strict Marketplace submission, V04 draft, compiler, secret usage,
  capability-manifest, and provisioning validation;
- runtime integration-service auth and opt-in trusted customer-ingestion auth;
- connector JDBC migration plus real PostgreSQL Testcontainers proof;
- provider fixture coverage for form-token/page sync and API-key/cursor sync;
- public-contract simulator coverage for authentication, stock pagination,
  individual stock fetch, publication/lifecycle behavior, signed notification
  shape, and absence of simulator-only headers on the provider wire surface;
- targeted current-record mapping, zero/one/many response handling, persisted
  record key, duplicate identity, retry/dead-letter, and provider-action
  collection-filter coverage;
- boundary tests for host allowlists, capability grants, auth-header collisions,
  protected path placeholders, record/resource mismatch, malformed/oversized
  responses, cursor-version reset, tombstones, retry safety, webhook signatures,
  duplicates, replay, dead-letter behavior, bounded rejection aggregation,
  bootstrap readback, and generated-secret cleanup; and
- production UI compilation for the deployment Integrations workspace.

This source checkpoint is complemented by the hosted neutral evidence below.
Neither checkpoint is dealership-demo or Auto Trader evidence.

Final clean source gate on 2026-09-28:

- standalone external-provider simulator: `4` tests, zero failures/errors;
- Generic REST Connector: `37` tests, zero failures/errors;
- AI Fabric Runtime: `221` tests across `53` suites, zero failures/errors;
- Platform backend: `857` tests, zero failures/errors;
- Platform UI: production TypeScript/Vite build passed;
- hosted-canary Python syntax and command-line contract checks passed;
- all four verification-only Marketplace fixture documents parsed as JSON and
  passed the real Platform Marketplace manifest parser;
- runtime capability manifest JSON and whitespace checks passed; and
- generic Java source scan found no Auto Trader, advertiser, signature-header,
  or provider-correlation constants.

The local Docker Desktop engine could not complete an earlier simulator image
build because its overlay mount returned `invalid argument`. Maven packaging
and tests passed, and the independent clean Coolify build/deploy subsequently
closed the hosted image assertion. The local overlay error remains only a
workstation-engine caveat.

### 4.2 Hosted generic evidence checkpoint

The strict staging canary completed at `2026-09-28T11:46:36Z` with status
`PASSED` and claim `HOSTED_GENERIC_SUBSTRATE_VERIFIED`.

- Live staging Platform backend source:
  `a95a2114c57c653939e2232c460da4ebf5b1e686`.
- Hosted verifier source: `79b23348f` on `Platform-V11`.
- Runtime source artifact: `dsa-74191bb1`, commit
  `86abb0320c5af2397231cf40077194ba0435efd2`, image digest
  `sha256:a2966baee6d00fe383fbdf29593c2530dc58e772ac6a83223f15d4db3085db41`,
  capability hash
  `ccba8fcd82b356083bf13a1f89f3f746936e730118735fd2cdbc0c9a5d377807`.
- Simulator fixture version: `external-vehicle-provider-v1` at the separately
  deployed HTTPS simulator.
- Temporary deployments `dep-5ddb8a3c` and `dep-6a96add0` used different
  provider contracts, credentials, protected resources, and deployment-local
  runtime/connector/database resources.
- Profile A and B passed baseline, idempotency, completed upsert history,
  indexing, and deployment-local retrieval evidence. Cross-account reads were
  denied.
- Updates, absence deletion, field tombstones, scheduled missed-event repair,
  typed provider failures, TLS-originated signed events, duplicate/order
  handling, replay, and dead-letter recovery passed.
- Connector restart retained durable source/work state. An older immutable
  deployment version was reapplied and verified.
- The Coolify PostgreSQL backup was `542815` bytes. Coolify `4.1.1` has no
  native database-import API, so the canary used a private ephemeral PostgreSQL
  16 restore helper on the deployment network. Restore was transactional,
  marker-health-gated, and preserved schema ownership/ACLs required by the
  restricted runtime and connector roles. Post-restore reconciliation and
  source convergence passed.
- The scheduled reconciler advanced the restored source checkpoint before the
  readback, so `restoredCheckpointObserved` is `false`; the exact post-backup
  mutation checkpoint was absent and explicit convergence still passed.
- Both deployments, all provider resources, the backup configuration, restore
  helper, and all five scoped fixture credentials were removed. Cleanup
  reported no failures, and direct Coolify readback found no temporary apps,
  databases, restore helpers, or diagnostic probes.

Durable bounded evidence is committed at
[2026-09-28-hosted-generic-substrate.json](../../../../../../../../verification-support/external-vehicle-provider-simulator/evidence/2026-09-28-hosted-generic-substrate.json).
Its SHA-256 is
`69379965ae11eb8fa738f918db6dcbc2e2e1e6063f173fa3aa668705aeb9615b`.
It contains no credential values or provider payloads. This evidence proves
only the generic substrate; it does not prove Auto Trader access, schemas,
rights, certification, sandbox behavior, or production readiness.

## 5. Target Deployment-Local Architecture

Normal customer and Auto Trader traffic must bypass the central Platform data
plane.

```text
dealership browser
  -> ordinary dealership website and inventory APIs
  -> LoomAI Max Mode/Companion chat application
     -> assigned deployment public anonymous bootstrap
     -> assigned deployment secure /api/chat/me/* endpoints

dealership backend
  -> deployment-local Data Sync/work-status and safe-readiness endpoints
  -> protected remote authorization and lead/test-drive command endpoints

assigned deployment
  -> AI Fabric runtime
  -> exact-origin anonymous bootstrap and scoped public chat ingress
  -> fail-closed REMOTE_HTTP authorization for bounded public reads
  -> deployment-local Generic REST Connector
     -> Auto Trader sandbox or production APIs
  -> one deployment-owned PostgreSQL resource
     -> runtime-owned schema
     -> connector-owned integration schema and Flyway history
  -> private connector-to-runtime Data Sync/indexing channel
  -> deployment-scoped vector target

Auto Trader notification
  -> deployment-specific connector webhook URL
  -> signature/hash verification before parsing
  -> advertiser and event-schema validation
  -> deduplication plus bounded source-record-ID extraction
  -> durable targeted reconciliation work
  -> Auto Trader current-record read for that stock record
  -> shared mapping and Data Sync upsert/delete
  -> periodic full baseline remains the convergence fallback

LoomAI Platform
  -> install, compile, release, assign, observe, verify, promote, and retire
  -> never proxy routine Auto Trader calls, buyer chat, or source records
```

The Platform may poll bounded deployment admin projections. It must not receive
credential values, full inventory payloads, model prompts, or routine provider
events.

Public buyer chat is the one deliberate browser-to-deployment path. The runtime
creates the anonymous session and issues a short-lived scoped bearer token after
validating the exact allowed origin. The browser may receive the fixed public
deployment chat/bootstrap URLs, but never an assignment key, deployment service
credential, connector credential, Data Sync/admin URL, provider credential, or
caller-controlled tenant/deployment/advertiser identity.

The existing private LoomAI `max-mode-widget` is the customer chat application
for this path. Its Companion dock, default Max Mode workspace, anonymous
bootstrap client, conversations, source/action rendering, and confirmation flow
must be reused. The public AI Fabric `chat-capabilities-demo` is a behavioral
reference only and is not part of the customer composition or deployment.

The hosted neutral canary templates currently use `ALLOW_VERIFIED`, which
correctly denies `ANONYMOUS_SESSION`. The dealership template must not copy that
setting. It must opt into public bootstrap and exact CORS/origin, issuer,
audience, scope, signing-secret, and rate-limit configuration, and use
`REMOTE_HTTP` authorization that grants only the deployment's bounded public
dealership reads. Anonymous actions remain denied unless their manifest
metadata explicitly permits the reviewed public action.

The current anonymous client can obtain a new token after `401`, but a new
bootstrap also creates a new anonymous identity. Before external demo release,
the private runtime and Max Mode integration need origin-checked proactive
same-session renewal authenticated by the still-valid token. When renewal is no
longer possible, the widget must clear old conversation/pending state before a
new identity is bootstrapped. Cross-refresh continuity remains outside the first
release and no browser-supplied session ID is permitted.

The connector calls its colocated runtime through an internal service URL and a
generated deployment-scoped service credential limited to Data Sync writes and
work-status reads. That internal route is not discovered through the public
consumer assignment response and is never exposed to browser code.

The existing deployment PostgreSQL resource is reused, but ownership is
isolated. The connector owns an `integration_connector` schema, its own Flyway
history, and a connector-specific database role restricted to that schema. The
runtime does not read connector tables, and the connector does not read runtime
tables. Backup, restore, export/import evidence, rollback, and decommission
remain part of the deployment lifecycle.

### 5.1 Generic substrate versus Auto Trader package

The implementation has two explicit layers.

The **generic external-provider substrate** owns:

- bounded connection and token-exchange profiles;
- immutable trusted-resource bindings and server-owned value injection;
- deployment-local HTTP DATA sync and durable reconciliation;
- raw-body-authenticated inbound event ingress;
- typed mapping, error classification, rate policy, and correlation evidence;
- private connector-to-runtime service authentication;
- optional scoped customer-backend endpoint discovery; and
- schema-driven operations and verification surfaces.

The **Auto Trader Marketplace composition** owns:

- `/authenticate`, form fields `key` and `secret`, `access_token`, and
  `expires_at` mappings;
- approved sandbox/production hosts and exact capability grants;
- resource type `autotrader-advertiser`, the `advertiserId` injection rule, and
  the one-advertiser-per-deployment template constraint;
- stock routes, page/pageSize mapping, normalized vehicle projection, and Auto
  Trader lifecycle/tombstone mappings;
- `AutoTrader-Signature` parsing and its timestamped raw-body HMAC-SHA256
  verification profile;
- Auto Trader error-body and correlation-header mappings;
- provider-specific labels, data rights, attribution, verification, and claim
  gates; and
- all Auto Trader DATA/ACTION/TEMPLATE package identifiers.

No Auto Trader route, field, status, signature header, retry duration, or
business label may appear in generic connector, Platform, or AI Fabric code.

No additional provider-specific service or bridge is introduced. The existing
per-deployment Generic REST Connector becomes the host for generic external
integration actions, sync workers, and inbound events. Its product/module name
may be updated in the same current-only change to reflect that wider role, but
there is still exactly one generic connector process per deployment.

### 5.2 Hosted simulator topology

```text
verification runner/operator
  -> protected simulator control API (seed, mutate, fail, emit event)

External Vehicle Provider Simulator (separate HTTPS service)
  -> provider profile A: form token + page/size + query-bound account
  -> provider profile B: API key + cursor + path/header-bound account

deployment A connector -> profile A/account A -> deployment A runtime/index
deployment B connector -> profile B/account B -> deployment B runtime/index

LoomAI Platform -> install/apply/observe/verify/backup/restore/decommission
```

Provider data still travels directly between each deployment connector and the
simulator. The central Platform receives only bounded operational evidence.
The raw simulator control API and control key are never exposed to a model,
browser, deployment connector, or ordinary customer application. A meeting-only
dealership backend may implement the bounded operator adapter in Section 3.6;
that adapter is verification tooling and is not an inventory-source dependency
or production integration contract.

### 5.3 Canonical source ownership and migration state

The architecture has three deliberately different states. They must not be
collapsed into one claim.

| State | Inventory used by LoomAI chat | Read-action destination | Dealership website/backend relationship | Permitted claim |
| --- | --- | --- | --- | --- |
| Current live meeting demo | Six fictional rows seeded in the dealership backend and pushed by that backend to deployment Data Sync | Dealership backend | Website and backend own their fictional catalogue and lead records | `DEALERSHIP_DEMO_READY` |
| Next neutral provider-backed canary | External Vehicle Provider Simulator pulled and reconciled by the deployment connector | Simulator for provider-style reads; dealership backend for lead writes | Website/backend remains independent; matching fixture `stockId` values link page context to provider records | Provider-neutral architecture proof only |
| Auto Trader sandbox/production target | Approved advertiser-scoped Auto Trader stock pulled and reconciled by the deployment connector | Auto Trader for granted provider reads; dealership backend for dealer-owned writes | Website/backend may use its own inventory platform and is not required to consume LoomAI's provider feed | Exact named gate only after sandbox/production evidence |

The currently running immutable version of `dep-f023c863` is therefore not
silently reclassified as provider-backed. Its applied `EXTERNAL_SYNC_HTTP`
declaration keeps `httpSource.enabled=false`; the dealership backend performed
the six-record push. The replacement source profile enables the provider path,
but it changes this state only after a new immutable version is applied and its
hosted canary passes.
The provider simulator used for the hosted generic substrate was exercised by
separate temporary deployments and is not the source for the dealership demo.

The next canary changes only the LoomAI deployment composition. It must not
rewrite the dealership website backend to obtain its displayed vehicles from
the simulator or Auto Trader. This preserves the real customer boundary: a
dealership may already have its own website, DMS, CRM, and stock presentation,
while LoomAI independently obtains the provider data it is licensed to use.

### 5.4 Stable identity across independent systems

The provider, LoomAI deployment, and dealership application need a shared
business reference without sharing databases or authority.

- `providerVehicleId` is the provider's internal immutable identifier and is
  used only where the provider contract requires it.
- `stockId` is the dealership-facing stock reference and is the canonical
  cross-system target carried by approved page context and action results.
- The normalized LoomAI entity identity remains `advertiserId + stockId` unless
  the granted contract proves a different stable identity is required.
- Provider reads resolve the trusted `stockId` to the provider record inside the
  installed package/connector boundary. The browser or model never supplies an
  advertiser or connection profile.
- Dealership-owned writes send the trusted `stockId` to the dealership backend;
  that backend independently validates that the vehicle is valid for the
  dealership before storing a callback or test-drive request.

For the neutral meeting canary, simulator records and dealership page fixtures
must deliberately share the relevant `stockId` values. This is test-fixture
alignment, not a runtime dependency. If a page target cannot be resolved in the
provider source, LoomAI fails closed or asks the user to select another vehicle;
it must not guess from model text.

### 5.5 Baseline plus targeted-record reconciliation

The durable freshness model is:

```text
install/recovery/schedule
  -> advertiser-scoped complete baseline
  -> shared mapper
  -> batched runtime Data Sync upsert/delete
  -> reconcile every indexing work item to terminal state

verified provider notification
  -> extract bounded sourceRecordId from the verified event
  -> coalesce work by deployment + source + sourceRecordId
  -> fetch the current provider record using the immutable resource binding
  -> validate advertiser/resource ownership
  -> shared mapper
  -> one runtime Data Sync upsert, or delete when the current provider state
     and package lifecycle policy require it
  -> reconcile indexing work to terminal state
```

The event is a change signal, not the indexing authority. Delayed or
out-of-order events still fetch current provider state, so an old payload cannot
overwrite a newer record. Duplicate events are deduplicated, and several events
for the same record may be coalesced into one current-state read. Raw webhook
payloads are not retained; retry state stores only bounded internal identifiers,
event metadata, fingerprints, attempts, and safe error classes.

The generic connector must support two explicit reconciliation strategies:

- `FULL_SOURCE`: the existing complete-source reconcile, retained for providers
  without a safe record lookup and for controlled recovery; and
- `FETCH_CURRENT_RECORD`: the preferred stock-notification strategy, using a
  verified event record ID and a package-declared current-record read.

The proposed provider-neutral package contract is conceptually:

```yaml
eventReconciliation:
  strategy: FETCH_CURRENT_RECORD
  sourceRecordIdJsonPointer: /provider/event/recordId
  recordFetch:
    method: GET
    path: /stock/{sourceRecordId}
    sourceRecordIdPlacement: PATH
    recordJsonPointer: ""
    notFoundPolicy: DELETE
```

The exact paths and selectors above are illustrative package data. Generic Java
knows only `sourceRecordId`, placement, mapping, and lifecycle policy. The Auto
Trader DATA package supplies its actual stock ID selector, path, response shape,
not-found semantics, and advertiser rules after sandbox confirmation.

Implementation ownership is fixed:

1. `ProviderWebhookService` verifies the request, validates the protected
   resource, extracts the bounded record key through a configured JSON Pointer,
   and enqueues durable reconciliation work.
2. A provider-neutral `HttpRecordReconciliationService` uses the existing
   `ProviderHttpClient`, trusted-resource binding, capability grants, rate
   policy, and error classification to fetch current state.
3. Mapping/identity/boundary logic is extracted from `HttpDataSyncService` into
   one reusable mapper so baseline and targeted reads cannot produce different
   entities for the same source record.
4. `RuntimeDataSyncClient` submits the normalized `UPSERT` or `DELETE` and waits
   for terminal indexing status.
5. Periodic full baseline remains enabled as the recovery mechanism for missed
   notifications, registration gaps, ambiguous provider responses, and drift.

### 5.6 Notification delivery and registration

Each deployment exposes its own callback:

```text
{deploymentConnectorPublicBaseUrl}/integrations/webhooks/{sourceId}
```

The Platform compiles and displays that URL and tracks
`NOT_REGISTERED`, `PENDING_VERIFICATION`, `ACTIVE`, or `DISABLED`. It does not
receive or proxy notification traffic. For the first Auto Trader release,
callback registration and notification-secret issuance are onboarding actions
performed with Auto Trader or the assigned Integration Manager unless a
documented, granted registration API is later supplied. The Platform must not
pretend that exposing the receiver automatically subscribes it.

After registration, normal freshness is provider notification delivery plus one
current-record fetch and indexing completion. No exact freshness SLA is claimed
until sandbox evidence measures provider delivery and indexing latency. A missed
event is bounded by the configured periodic baseline interval plus provider and
indexing time; that interval is package policy constrained by provider fair-use
rules.

### 5.7 Action ownership and failure behavior

The deployment template installs two separate action packages because the
systems have different authority.

| Action class | Owner and destination | Examples |
| --- | --- | --- |
| Granted provider reads | Auto Trader ACTION package through the deployment connector | current stock detail, search, equipment, taxonomy, approved vehicle facts |
| Dealership-owned commands | Dealership ACTION package through the same connector process but a distinct protected backend route | callback, test-drive, local CRM/lead request |
| Model composition | Runtime orchestration over typed read facts | comparison or recommendation when no provider comparison endpoint exists |

Route ownership is selected by the immutable template and capability grants,
not by trying Auto Trader first and falling back to the dealership on any error.
A provider read failure may use an indexed snapshot only when deployment policy
permits it and the answer clearly reports its freshness. A write never silently
falls back after an ambiguous provider/backend response because that can produce
duplicate side effects. Every write keeps confirmation, trusted target,
idempotency, and application validation.

The generic connector must also support a provider-backed action route without
requiring `connector.upstream.base-url`. A route with `connectionProfileRef`
resolves through its immutable profile first; the global upstream remains only
for ordinary application routes such as the dealership backend.

### 5.8 Marketplace package and install contract

No new plugin type is required. The product composition is:

- a dedicated Auto Trader `DATA` plugin for connection profile, secret refs,
  advertiser binding, baseline, targeted event reconciliation, shared vehicle
  mapping, lifecycle/deletion, attribution, data rights, and rate policy;
- a dedicated Auto Trader `ACTION` plugin for only the provider capabilities
  granted to the integration;
- a separate dealership `ACTION` plugin for dealer-owned confirmed writes;
- optional dealership-owned knowledge `DATA` plugins; and
- one `TEMPLATE` that pins the exact behavior, packages, inference/vector
  profiles, chat surfaces, install fields, and verification packs.

The decision is therefore **yes to a dedicated Auto Trader DATA plugin, but no
to arbitrary Auto Trader executable code inside that plugin**. The plugin owns
the provider contract declaratively; the connector owns execution. This keeps
credential handling, advertiser enforcement, retries, durable work, redaction,
mapping, Data Sync, observability, and lifecycle behavior uniform across
providers. If the granted Auto Trader contract exposes a mechanical shape the
connector cannot express safely, add the smallest reusable typed connector
primitive and prove it with a second neutral fixture. Do not hide provider logic
in a script, custom container, or central bridge.

The template install form requires provider environment, credential secret
references, notification secret reference, one advertiser/account binding,
dealership backend URL/key for dealer-owned actions, allowed website origins,
inference/vector profiles, full-baseline schedule, and lifecycle/retention
policy. A clean install must compile these values into one immutable deployment
version without manual JSON or environment repair.

The simulator uses separate neutral verification package fixtures. Those
fixtures prove generic mechanics and the target composition while access is
blocked, but they are never published or labelled as Auto Trader packages.

### 5.9 Alternatives considered and final verdict

| Considered option | Verdict | Reason |
| --- | --- | --- |
| Central standalone Auto Trader bridge | Rejected | Creates a shared data-plane bottleneck and failure/security boundary outside each self-contained deployment |
| Make the dealership backend consume Auto Trader/simulator stock | Rejected | Couples LoomAI adoption to rewriting an existing customer application and gives the backend provider credentials it does not need |
| Keep dealership backend push as the production provider source | Demo only | Valid for the current fictional proof, but it does not prove deployment-local provider sync or notification freshness |
| Dedicated Auto Trader DATA plugin with custom executable code | Rejected | Duplicates auth, persistence, redaction, retry, mapping, Data Sync and operations mechanics per provider |
| Dedicated declarative Auto Trader DATA plugin executed by the generic connector | Selected | Preserves provider-specific semantics while reusing governed deployment-local mechanics and existing Marketplace lifecycle |
| Reconcile the full stock source after every notification | Supported fallback | Correct and already proven, but inefficient as the primary stock-update path |
| Fetch current state for the verified changed record | Selected primary | Efficient, robust to sparse/delayed events, and still converges through periodic full baseline |
| Index webhook payload directly | Rejected | Event payload may be partial, stale, out of order, or intended only as a notification signal |
| Add a new Marketplace plugin type | Rejected | Existing `DATA`, `ACTION`, `TEMPLATE`, and profile primitives express the product boundary |
| Automatically register callbacks without a documented provider API | Rejected assumption | The Platform exposes and tracks the deployment callback; registration remains an onboarding step until a granted API proves otherwise |

## 6. Platform And Product Change Contract

### 6.1 Workstream A: provider-neutral connection profiles

Extend the deployment/Marketplace contract with a typed connection profile
selected by a plugin, not an arbitrary URL or free-form environment map.

Required profile fields:

- stable profile ID and environment label;
- approved API host allowlist;
- approved token host and relative token path;
- an allowlisted auth-strategy identifier; the first release implements
  `API_KEY` and `FORM_TOKEN_EXCHANGE`;
- named credential parameters whose values come only from secret references;
- token request encoding, approved static parameters, and bounded response
  projection;
- token value path plus either an absolute-expiry or relative-expiry path;
- authorization scheme/header, expiry skew, timeout, and bounded retry policy;
- provider/service rate policy;
- capability grant metadata; and
- environment-specific trusted-resource binding references.

Security requirements:

- the model, browser, caller, action parameters, and webhook payload cannot
  select a connection profile, host, credential, or protected resource;
- secrets are resolved only in the deployment during apply/startup;
- token request field names and response paths come from a reviewed immutable
  package, not caller input;
- token values are never returned through action facts, traces, exports, admin
  readback, or support bundles;
- token cache is bounded by profile and deployment;
- authentication failure stops affected provider calls until a controlled token
  refresh succeeds; and
- sandbox and production use distinct connection and resource bindings.

### 6.2 Workstream B: bounded credential-token-exchange execution

Add reusable token acquisition to the Generic REST Connector:

1. construct only the reviewed request method, encoding, static parameters, and
   secret-backed credential fields declared by the configured profile;
2. request a token only from the approved token host/path;
3. extract the token and absolute or relative expiry through bounded selectors;
4. cache it until expiry minus safety skew;
5. reuse it rather than authenticating every API call;
6. perform one controlled refresh after a classified upstream authentication
   rejection;
7. stop and surface an actionable classified failure if refresh fails;
8. expose only secret-free token posture such as `READY`, `EXPIRING`, or
   `AUTH_FAILED`; and
9. verify concurrent requests do not trigger a token stampede.

The generic contract may add another reviewed strategy, such as OAuth2 client
credentials, when a real provider package requires it. It must not expose an
arbitrary authentication-request DSL, and OAuth2 is not an Auto Trader or P0
requirement. For Auto Trader, the immutable package profile is:

```text
strategy = FORM_TOKEN_EXCHANGE
method = POST
path = /authenticate
contentType = application/x-www-form-urlencoded
credentialFields = key:<api-key-secret-ref>, secret:<api-secret-ref>
tokenJsonPointer = /access_token
absoluteExpiryJsonPointer = /expires_at
authorizationScheme = Bearer
```

These field names are Auto Trader package data, not Java constants or
Platform-wide schema fields.

### 6.3 Workstream C: trusted deployment bindings

Compile a typed immutable resource binding into each release:

```text
bindingId
connectionProfileRef
environment
resourceType
resourceId
capabilityGrants[]
policyRef
```

The connector injects protected values from this binding. A reviewed plugin may
declare a bounded placement rule for the resource value, such as a named query,
path, header, or body field. A caller or model cannot supply or override it, and
the action/input schema must not expose the protected field.

Validation must fail when:

- a required resource binding is missing or ambiguous;
- its resource type does not match the package contract;
- an action exposes a server-owned field as user input;
- the connection environment and endpoint host disagree;
- a plugin requires a capability absent from the recorded grant;
- a secret reference is unresolved; or
- a placement rule targets an undeclared or caller-selectable field.

The Auto Trader package declares `resourceType=autotrader-advertiser`, maps
`resourceId` to `advertiserId`, and requires exactly one such binding in its
first-release template. Those are package constraints, not generic Platform
constraints.

### 6.4 Workstream D: deployment-local HTTP DATA source

Add one current canonical DATA ingestion contract. Recommended shape:

```text
ingestionMode = EXTERNAL_SYNC_HTTP
syncConnector.connectorType = HTTP_JSON
```

Pagination, response selection, record identity, content/metadata mapping,
deletion policy, and reconciliation are typed subcontracts. Do not add separate
top-level modes for every pagination style.

The deployment-local sync engine must:

- perform trusted-resource-bound baseline pulls;
- support a typed bounded pagination strategy such as page/size or cursor;
- map records with structured selectors, not scripts;
- validate plugin-declared record identity and trusted-resource equality;
- persist cursor, source fingerprint/version, last-success time, and failure
  state in the connector-owned PostgreSQL schema;
- normalize records into the plugin-declared entity contract;
- use one reusable record mapper and boundary validator for both complete-source
  and single-record fetches;
- call the same deployment's runtime Data Sync API through the private scoped
  service channel;
- reconcile every returned work ID to a terminal state;
- apply a typed update strategy and exact tombstone/absence policy;
- run periodic baseline reconciliation to repair missed events; and
- expose secret-free counts and lag.

The Auto Trader DATA package configures page/pageSize, stock identity,
advertiser equality, normalized vehicle fields, and mappings for sold,
unpublished, `WASTEBIN`, and deleted records. These values are not generic
engine behavior.

External provider data must not be loaded by the central
`MarketplaceDatasetSyncService`. That service can continue to own its current
Platform-side dataset modes, but the new HTTP provider source executes inside
the exact deployment.

### 6.5 Workstream E: generic inbound provider webhooks

Add a deployment-specific generic webhook ingress owned by the deployment-local
connector.

Required contract:

- opaque source ID resolved from immutable deployment configuration;
- stable deployment-specific HTTPS route and explicit registration state;
- reviewed verification strategy, secret reference, signature-header parser,
  and signed-payload construction;
- verification against raw bytes before parsing whenever required by the
  strategy;
- strict content type, body size, timeout, and schema limits;
- event type allowlist;
- plugin-declared extraction and equality check against the trusted-resource
  binding;
- durable event ID/deduplication key;
- replay-window and ordering policy;
- quick provider-appropriate acknowledgement;
- durable reconciliation work and retry/dead-letter state;
- an explicit `FULL_SOURCE` or `FETCH_CURRENT_RECORD` strategy;
- for targeted mode, a bounded source-record selector, immutable current-record
  request, and declared not-found/delete policy;
- safe operational status and manual replay where provider rules allow; and
- optional canonical CloudEvent forwarding only after validation.

Webhook registration is lifecycle state, even when the provider registers it
manually. Platform must display `NOT_REGISTERED`, `PENDING_VERIFICATION`,
`ACTIVE`, or `DISABLED`, retain no signature secret value, and block a provider
readiness claim until a signed canary reaches the exact deployment URL. URL or
secret rotation requires drain, re-registration, verification, and rollback
evidence.

The currently implemented generic webhook path performs a complete-source
reconcile after every accepted event. That remains valid as `FULL_SOURCE`, but
it is not the selected Auto Trader stock-update design. Before publishing the
Auto Trader DATA package, add `FETCH_CURRENT_RECORD` so an accepted event can
extract one bounded internal source-record key, retrieve current provider state,
and upsert/delete only that entity while the scheduled baseline remains the
recovery path.

The Auto Trader package declares HTTP `PUT`, `AutoTrader-Signature`, its `t` and
`v1` parser, timestamp plus `.` plus raw-body HMAC-SHA256 construction,
resource extraction, event types, record-key selector, current-record fetch,
not-found/delete policy, and response mapping. A notification is treated as a
change signal rather than trusted complete record content.

### 6.6 Workstream F: bounded mapping and fair usage

Extend connector configuration with reusable typed behavior for:

- selected JSON object/list roots;
- field rename and safe scalar/list projection;
- provider error/warning extraction;
- page/cursor extraction;
- source/check timestamp projection;
- response and collection-size limits;
- per-provider and per-service concurrency/rate budgets;
- configurable `429` global/provider pause and retry behavior;
- configurable `503` service-level pause behavior;
- generic error classes `BAD_REQUEST`, `AUTHENTICATION_REQUIRED`,
  `RESOURCE_ACCESS_DENIED`, `CAPABILITY_DENIED`, `RATE_LIMITED`,
  `SERVICE_UNAVAILABLE`, `TIMEOUT`, and `MALFORMED_RESPONSE`; and
- an allowlist of bounded correlation-response headers.

The Auto Trader package maps its two `403` meanings, retry/pause durations, and
`CF-Ray` header into those generic contracts. Do not hard-code current public
documentation values: provider guidance differs by page and the exact policy
must be confirmed during capability onboarding.

Never expose arbitrary expression evaluation, JavaScript, shell, caller-chosen
URLs, or unbounded JSON traversal.

### 6.7 Workstream G: internal service discovery and optional customer discovery

These are two different trust boundaries.

For **deployment-internal connector calls**, Platform compiles:

- the private runtime base URL;
- a generated deployment-scoped service identity;
- only Data Sync write/delete and indexing-work read scopes; and
- exact tenant/deployment assertions.

The connector never discovers this channel through the public consumer
assignment endpoint, and it receives no broad runtime-admin credential.

For **customer-backend ingestion**, extend assignment discovery only when the
immutable template explicitly enables an external-ingestion surface. Protect it
with the existing scoped consumer assignment key and private runtime assertion
model. The response may include typed Data Sync, indexing-work, and safe
readiness URLs only for granted scopes.

Browser code receives neither service credentials nor ingestion URLs. Auto
Trader's deployment-local connector does not require public Data Sync exposure.

### 6.8 Workstream H: operations and support surfaces

The deployment workspace must show:

- integration environment plus protected resource type and a policy-approved
  display value or fingerprint;
- capability grants and preflight state;
- credential presence/rotation status without values;
- token posture without token material;
- last baseline, cursor, source count, normalized count, indexed count, and lag;
- accepted/completed/failed indexing work;
- webhook expected, received, rejected, duplicate, replayed, and dead-letter
  counts;
- latest provider error class and bounded provider call ID;
- source freshness visible to chat/retrieval; and
- applicable verification-pack status.

Operators need explicit actions for baseline reconcile, failed-work retry,
webhook replay where permitted, credential rotation, and safe decommission.
Display labels are package metadata. The Auto Trader package may label the
protected resource as `Advertiser` and may display its exact advertiser ID when
the approved policy allows it; generic Platform code must not assume either.

### 6.9 Workstream I: Marketplace packages

After the generic substrate has passed and the exact partner grants, contracts,
and data rights exist, publish exact packages:

| Proposed package | Type | Responsibility |
| --- | --- | --- |
| `mkp-data-autotrader-dealership-stock-v1` | `DATA` | Advertiser-scoped baseline, targeted current-record reconciliation, shared mapping, normalized vehicle entity, indexing/freshness/delete policy |
| `mkp-action-autotrader-dealership-discovery-v1` | `ACTION` | Advertiser preflight, live stock search/detail, and only the granted evidence reads routed directly through the provider connection profile |
| `mkp-data-dealership-knowledge-v1` | `DATA` | Dealer-owned warranty, delivery, support, and location knowledge with distinct attribution |
| `mkp-action-dealership-lead-v1` | `ACTION` | Confirmed callback/test-drive command to the dealership-owned backend |
| `mkp-template-autotrader-dealership-concierge-v1` | `TEMPLATE` | Exact behavior, plugins, provider/vector profiles, bindings, endpoints, UI modules, and verification packs |

The package names do not become official until published versions and hosted
evidence exist.

Connection profiles, protected-resource bindings, route mappings, webhook
profiles, and provider labels are contributions inside these existing package
types. They do not introduce a `CONNECTION`, `WEBHOOK`, or `AUTOTRADER`
Marketplace plugin type.

The dealership website/backend is not a dependency of the Auto Trader DATA
plugin and does not receive provider credentials. It participates only through
its independently owned page targets and dealer-command routes. The only shared
business reference is the validated `stockId` contract described in Section
5.4.

### 6.10 Workstream J: dealership demo application

The separate ordinary customer application described in `010.26` is now source
implemented. It:

- consumes deployment URLs over HTTP;
- reuses the LoomAI Max Mode/Companion generic chat application in
  `public-runtime-anonymous` mode;
- pins a reviewed LoomAI-owned widget artifact/source build and removes stale
  package/repository/CDN branding before an external demo;
- renews short-lived anonymous access before expiry without changing the
  runtime-issued session, and safely clears stale conversation/pending state
  when a new anonymous identity is unavoidable;
- sends public buyer chat directly from the browser to the assigned deployment,
  without a Platform or dealership chat facade;
- contains no AI Fabric dependency or local AI implementation;
- uses approved demo data until sandbox access exists;
- exercises real LoomAI indexing, retrieval, chat, confirmation, and action
  execution;
- identifies the source mode visibly; and
- never reports `Auto Trader connected` while running the demonstration source.

This work can proceed in parallel with partner onboarding after the opt-in
customer-backend Data Sync URL and safe-readiness contracts are stable. The
demo backend receives those privileged URLs and matching scopes; the browser
does not. The browser receives only the non-secret public runtime descriptor
needed for bootstrap and secure chat routes.

The 2026-09-29 source-only checkpoint proved the native customer and staff UI,
dealership-owned inventory/lead backend, protected connector contracts, private
Data Sync client, build identity, and browser-safe runtime descriptor. The
subsequent 2026-09-30 hosted closure proved same-session anonymous renewal,
indexing, retrieval, live confirmation/action execution, restart durability,
and protected staff readback. The composition now passes
`DEALERSHIP_DEMO_READY`; this remains separate from every Auto Trader gate.

### 6.11 Workstream K: hosted external-provider simulator

The simulator described in Section 3.5 is implemented and deployed with:

- deterministic seed/reset and versioned fixture contracts;
- two neutral provider profiles and separate resource accounts;
- authenticated provider data/detail routes and signed event delivery;
- an operator-only control API for mutations and fault injection;
- health and fixture-version endpoints that contain no secrets;
- automated contract tests independent of LoomAI; and
- a hosted verification script that creates two LoomAI deployments, runs the
  complete matrix, captures bounded evidence, and hard-decommissions temporary
  resources.

The existing implementation satisfies the neutral profile work only. The
public-document-informed Stock Sync fixture, provenance matrix, and protected
meeting trigger described in Section 3.6 are planned additions and must not be
reported as implemented evidence until their contract and hosted tests pass.

The simulator is disposable verification infrastructure. It must not become a
runtime dependency of any customer deployment or a compatibility layer once
real provider access exists.

## 7. Data Rights And Indexing Gate

Technical access is not permission to index provider data. Before enabling the
Auto Trader DATA plugin, obtain written answers for:

- which stock fields may be retained and for how long;
- which fields may be embedded or transformed into vectors;
- whether approved fields may be supplied to the configured LLM provider;
- whether descriptions, equipment, metrics, valuations, finance, vehicle-check,
  and competitor data have different rights;
- required source attribution, deep links, wording, or freshness display;
- delete/offboarding timing;
- use of sandbox data in demonstrations; and
- whether derived summaries, comparisons, or recommendations may be retained.

The plugin must compile the approved field policy. Fields outside that policy
must never enter model context, vector content, metadata, traces, or support
exports.

## 8. Implemented Source Change Map

The source implementation uses the following ownership boundaries.

| Area | Primary current source | Required direction |
| --- | --- | --- |
| Connector config | `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/.../RestRoutingConfig.java` | Typed connection/auth/rate/data/webhook contracts |
| Connector validation | `.../RestConnectorStartupValidator.java` | Fail-closed hosts, bindings, secrets, token exchange, pagination, mapping, and webhook policy |
| Connector execution | `.../RestActionExecutionService.java` | Token service, trusted bindings, classified provider errors, bounded mapping |
| Provider action route resolution | `RestActionExecutionService.executeOnce(...)` currently resolves the ordinary global upstream before checking `connectionProfileRef` | Resolve provider-profile routes through `ProviderHttpClient` first so mixed provider/dealership packages do not require a fake global upstream |
| Token lifecycle | New provider-neutral connector service | Execute the implemented bounded auth strategies; first release covers API key and form token exchange, including cache, refresh, redaction, and safe posture |
| Durable integration state | Connector persistence package and `db/migration/integration/V1__integration_connector_state.sql` in schema `integration_connector` | Cursors, source fingerprints, work, events, dedupe, reconciliation, and dead letters; never token/credential values |
| Connector database provisioning | `RailwayProvisioningPlanService`, `CoolifyDeploymentProvider`, provider secret/resource services | Reuse the deployment PostgreSQL resource; create a restricted connector role/schema, inject JDBC config, verify readback, and include lifecycle cleanup |
| DATA manifest validation | `Platfrom/backend/.../MarketplaceManifestService.java` | Validates `EXTERNAL_SYNC_HTTP` and typed HTTP connector contribution |
| DATA compilation | `.../DeploymentMarketplaceDraftCompilerService.java` and `DeploymentConfigCompiler.java` | Compile immutable source, mapping, binding, capability, secret, and webhook refs into V04 artifacts |
| DATA execution | `HttpDataSyncService` and `RuntimeDataSyncClient` | Pull provider data, normalize it, and push to private local runtime Data Sync |
| Shared record projection | Mapping and boundary logic currently lives inside `HttpDataSyncService` | Extract one reusable provider-neutral mapper for complete snapshots and targeted record fetches |
| Targeted event reconciliation | `ProviderWebhookService` currently calls `HttpDataSyncService.reconcileAsync(...)` for the complete source | Add bounded verified-event record-key extraction, durable per-record work/coalescing, current-record fetch, and upsert/delete completion |
| Existing Platform dataset sync | `.../MarketplaceDatasetSyncService.java` | Do not route provider data through it; retain current modes only |
| Draft validation | `Platfrom/backend/.../DeploymentDraftValidationService.java` | Validate immutable protected-resource/capability/connection bindings and package-specific cardinality |
| Secret/resource lifecycle | Marketplace install, provider secret services, and the document-storage binding lifecycle precedent | Generalize target-scoped reference/readback/rotation/export/decommission patterns without coupling to document storage |
| Internal service auth | Provisioning plans plus runtime ingress authorization | Inject private runtime URL and generated connector identity scoped only to Data Sync writes/deletes and work reads |
| Optional assignment response | `PublicCustomerBackendIngestionSummary.java` and `PublicProvisioningApiService.java` | Scoped backend ingestion/work/readiness group only when enabled by immutable dataset contract; never internal connector discovery |
| Runtime indexing | `RuntimeIntegrationDataSyncController`, `RuntimeIntegrationIndexingController`, and `RuntimeCustomerIngestionController` | Private connector boundary plus opt-in customer-safe projection |
| Platform operations UI | `DeploymentIntegrationOperationsService`, controller, and `IntegrationsPage.tsx` | Real runtime/connector state, reconcile, webhook events, and controlled replay |
| Hosted provider fixture | `verification-support/external-vehicle-provider-simulator` outside the deployment and Platform data plane | Deployed and proven with deterministic two-profile HTTPS, event, mutation, and failure contracts without provider branding or data |
| Verification | Platform backend suites and `scripts/verify-external-http-provider-hosted-canary.py` | Simulator-backed isolation/lifecycle gate passed; real sandbox and production gates remain |

No provider-specific route or field may be added to AI Fabric framework core,
generic Platform schema names, or generic connector Java defaults. Auto Trader
details live in immutable Marketplace package data and provider-specific
verification fixtures.

## 9. Implementation Sequence

Current execution status:

| Phase | Status | Honest checkpoint |
| --- | --- | --- |
| 0 | `PARTIAL_EXTERNAL_BLOCKER` | Generic contracts are frozen; partner identity, grants, test advertiser, credentials, webhook details, and data rights are still required |
| 1 | `HOSTED_GENERIC_SUBSTRATE_VERIFIED` | Generic profiles, auth, protected resources, mapping, fair usage, JDBC state, fail-closed bootstrap scrub, generated-secret cleanup, restart, backup/restore, and decommission passed against the hosted neutral simulator |
| 2 | `HOSTED_GENERIC_SUBSTRATE_VERIFIED` | Marketplace compile/provisioning, private Data Sync, page/cursor handling, tombstones, work reconciliation, indexing, retrieval, and two-deployment isolation passed on staging |
| 3 | `HOSTED_GENERIC_SUBSTRATE_VERIFIED` | TLS-originated signed ingress, dedupe, ordering, rejection, replay, retry, dead-letter, and baseline convergence passed on staging |
| 3A | `LOCAL_IMPLEMENTED_HOSTED_PENDING` | Targeted current-record reconciliation, persisted verified record key, shared mapping, source locking, provider-only action routing, and response filtering are implemented and focused-test verified; focused hosted canary remains |
| 4 | `HOSTED_GENERIC_SUBSTRATE_VERIFIED` | Scoped operations/discovery, immutable apply/rollback, durable restart, destructive restore, and complete cleanup passed; UI remains source-build verified |
| 5 | `DEALERSHIP_DEMO_READY` | Approved fictional data, dealership-owned lead boundary, ordinary customer demo app, direct browser chat, indexing, retrieval, confirmation, restart durability, and staff readback passed on staging; the initial closure used AI Fabric `0.8.5` and the latest first-delivery closure uses `0.8.8` |
| 5A | `LOCAL_IMPLEMENTED_HOSTED_PENDING` | Public-contract simulator profile, Marketplace fixtures, provider-backed deployment profile, and protected staff trigger are implemented; replacement deployment and continuous hosted browser canary remain |
| 6 | `BLOCKED_EXTERNAL` | Requires Auto Trader partner sandbox identity, exact grants, advertiser, schemas, events, and validation support |
| 7 | `BLOCKED_EXTERNAL` | Requires production approval, rights, bindings, go-live validation, and controlled production proof |

`HOSTED_GENERIC_SUBSTRATE_VERIFIED` closes only the provider-neutral hosted
mechanics. `DEALERSHIP_DEMO_READY` separately closes only the fictional
dealership composition. Neither advances a named-provider gate, and the
remaining phases must not be collapsed into a paper pass.

### Immediate next execution

1. Preserve the passed generic evidence artifact and rerun the affected focused
   matrix after any change to external-sync, connector persistence, provider
   lifecycle, or webhook contracts.
2. Deploy the implemented targeted-reconciliation and provider-route source to
   staging, replace the dealership deployment from the current immutable
   profile, and prove add, update, sold/delete, duplicate, delayed event,
   missed-event baseline repair, restart, and cross-account denial.
3. Run the provider-backed dealership canary: indexed stock and provider reads
   come from the simulator; the independent backend continues to own only
   presentation and lead writes; the protected staff trigger demonstrates the
   public-contract notification path without exposing controls or credentials.
4. Complete Auto Trader partner onboarding and obtain the exact sandbox
   identity, grants, authorized advertiser, schemas, event contract, data
   rights, and validation checklist.
5. Preserve and rerun the passed dealership demo gate after any change to its
   deployment, runtime, browser-auth, inventory-indexing, or lead-action
   contracts.
6. Once grants exist, author exact Auto Trader DATA/ACTION/TEMPLATE versions
   through the existing Marketplace/V04 lifecycle; keep all provider wire
   constants in package data and provider verification.
7. Run the named sandbox matrix and record
   `AUTOTRADER_SANDBOX_VERIFIED` only if the exact immutable composition and
   provider-required evidence pass.

The dealership demo may remain available while partner onboarding proceeds,
but it must stay visibly fictional. Neither simulator nor demo success reduces
the named sandbox and production gates.

### Phase 0: freeze contracts and obtain access

- Confirm integration-partner ownership and first design-partner dealership.
- Request the complete sandbox onboarding package.
- Freeze generic connection, token-exchange, protected-resource, HTTP DATA,
  webhook-verifier, mapping, error, and rate-policy schemas.
- Freeze the Auto Trader one-advertiser-per-deployment package rule and
  normalized `dealer-vehicle` identity separately.
- Confirm data/LLM/vector rights.
- Confirm exact sandbox token, notification, pause, and error policies with the
  Integration Manager where public guidance differs.

Exit: reviewed contracts plus provisioned sandbox identity or a clearly recorded
external blocker. Demo-only work may continue when access is blocked.

### Phase 1: neutral connector foundation

- Build the standalone two-profile provider simulator and its independent
  contract suite.
- Add the connector-owned database schema, restricted role, Flyway history,
  provisioning, backup/restore, bootstrap-secret proof, and decommission
  behavior.
- Implement typed connection profiles and bounded token exchange, including
  absolute and relative expiry.
- Implement trusted binding injection.
- Add structured response mapping and fair-usage policy.
- Test against at least two neutral provider fixtures with no Auto Trader names:
  one form-token/page/query-bound fixture and one materially different
  auth/pagination/resource-placement fixture.

Exit: connector persistence, authentication, refresh, protected-resource
binding, errors, rate behavior, restart, rollback, and cleanup pass locally and
in hosted simulator canaries across two isolated deployments.

### Phase 2: deployment-local HTTP DATA sync

- Add manifest/compiler/validation contract.
- Add deployment-local paged sync worker and durable state.
- Add private scoped connector-to-runtime service authentication.
- Push into runtime Data Sync and reconcile work without public assignment.
- Prove create/update/delete/restart/reconcile and two-deployment isolation.

Exit: neutral HTTP DATA plugin passes complete hosted indexing lifecycle.

### Phase 3: generic inbound provider events

- Add raw-body verification, schema mapping, dedupe, ordering, replay, and
  reconciliation.
- Add operations status and recovery.
- Prove missed/duplicate/out-of-order/invalid events converge through baseline.

Exit: neutral event-fed source passes hosted lifecycle and failure tests.

### Phase 3A: targeted current-record reconciliation

**Source status:** implemented and focused-test verified on 2026-10-03; hosted
canary pending.

- Add provider-neutral `FULL_SOURCE` and `FETCH_CURRENT_RECORD` strategies.
- Extract a bounded record key only after signature, event, and resource
  validation; retain no raw provider payload.
- Fetch current record state with the source connection profile, trusted
  resource, capability grants, and provider rate/error policy.
- Reuse the exact baseline mapper and boundary validator.
- Coalesce duplicate work by deployment/source/record while preserving durable
  attempts and dead-letter recovery.
- Submit one runtime upsert/delete and reconcile its indexing work to terminal.
- Make provider-backed action routes independent of the ordinary global
  application upstream.
- Prove targeted add/update/delete, delayed/out-of-order convergence, restart,
  baseline repair, and two-account isolation against the hosted simulator.

Exit: the generic substrate supports efficient event-driven record freshness
without provider-domain code. The existing `HOSTED_GENERIC_SUBSTRATE_VERIFIED`
claim remains valid for its recorded full-source scope; new evidence must name
the targeted extension explicitly.

### Phase 4: discovery and operator UX

- Add opt-in backend-only ingestion endpoint discovery for templates that need
  an external customer backend.
- Add safe readiness and source/index/work projection.
- Add schema-driven Platform forms for connection refs, grants, and protected
  resource bindings; provider packages supply labels.
- Add deployment operations status and controlled recovery actions.

Exit: a clean tenant can configure and operate the neutral composition without
manual JSON or environment repair.

### Phase 5: dealership demo

- Publish the demo DATA and dealership lead ACTION packages.
- Create the exact meeting deployment and ordinary customer demo app.
- Configure exact-origin public anonymous bootstrap, `REMOTE_HTTP` bounded
  public-read authorization, and explicit anonymous action policy.
- Reuse Max Mode/Companion and prove direct browser-to-deployment chat without a
  static credential or chat proxy.
- Prove same-session token renewal and fail-closed new-session reset without
  stale conversation replay.
- Pass the `DEALERSHIP_DEMO_READY` gate.

Exit: live HTTPS demo with real LoomAI behavior and no false Auto Trader claim.

### Phase 5A: neutral provider-backed dealership composition

**Source status:** implemented on 2026-10-03; replacement deployment and
continuous hosted browser evidence pending.

- Keep the public dealership website/backend implementation and inventory
  presentation unchanged.
- Replace only the LoomAI deployment's demo backend-push DATA/read-action
  packages with neutral simulator-backed package fixtures.
- Align fictional `stockId` values only where page context must select the same
  provider record.
- Route simulator-supported reads to the simulator and confirmed callback/test-
  drive writes to the dealership backend with no blind fallback.
- Add an authenticated, demo-only staff scenario control that performs a
  server-owned simulator mutation followed by signed event delivery. Do not
  expose the simulator control key, account, webhook target, or arbitrary
  payload/URL selection to the browser.
- Display bounded progress through provider mutation, event acknowledgement,
  connector reconciliation, terminal indexing work, and buyer-search
  visibility. Keep the dealership website catalogue source unchanged.
- Prove initial baseline, targeted event freshness, semantic retrieval, live
  detail, comparison, confirmation, lead persistence, restart, and baseline
  recovery in one continuous browser/session scenario.

Exit: the intended provider/dealership split is live-proven without claiming
Auto Trader compatibility or changing the independent customer application.

### Phase 6: Auto Trader sandbox packages

- Author Auto Trader DATA/ACTION contributions for only granted capabilities.
- Bind sandbox connection and exact test advertiser.
- Pass authentication, advertiser, baseline, indexing, search/detail, webhook,
  failure, and isolation canaries.
- Collect Auto Trader call-log/demonstration evidence.

Exit: exact package/template versions marked
`AUTOTRADER_SANDBOX_VERIFIED`.

### Phase 7: production approval and release

- Bind production credentials and advertiser membership separately.
- Apply approved retention/indexing/attribution rules.
- Pass applicable Auto Trader go-live checks.
- Run production canary, LoomAI release-readiness, rollback, and offboarding.

Exit: owner-approved exact immutable composition marked
`AUTOTRADER_PRODUCTION_READY`.

## 10. Verification Requirements

### 10.1 Generic connector

- API-key and form-token-exchange profile validation without provider-specific
  code;
- token acquisition, bounded response projection, absolute/relative expiry,
  cache reuse, skew, refresh, invalid credentials, revocation, concurrent
  refresh, timeout, and restart;
- host allowlist and caller-selected URL denial;
- trusted-resource injection and caller/model override denial;
- bounded request/response mapping and log redaction;
- generic bad-request, authentication, resource-denied, capability-denied,
  rate-limited, unavailable, timeout, and malformed-response classification;
- configured correlation-header capture without leaking response headers; and
- provider/service concurrency and pause recovery;
- provider-backed action execution succeeds with only its declared connection
  profile and does not require the ordinary connector global upstream;
- two neutral package fixtures with different auth, pagination, protected-value
  placement, and error mappings compile and run without Java changes; and
- a source scan finds no Auto Trader names or wire constants outside package
  fixtures and provider-specific verification code.

The hosted form of this matrix must use the simulator's two materially
different profiles. In-process mocks alone do not satisfy the hosted exit.

### 10.2 DATA sync and indexing

- baseline pagination terminates and count reconciliation passes;
- repeated baseline is idempotent;
- baseline and targeted fetch map the same source record to byte-equivalent
  entity/content/metadata projections;
- update replaces searchable content;
- configured tombstone/absence policy removes retrieval exactly;
- every accepted Data Sync work ID reaches and records terminal status;
- restart resumes from durable state without duplicate source records;
- scheduled reconciliation repairs a deliberately missed event;
- one targeted create/update/sold/delete event changes only its identified
  source record and reaches terminal indexing status;
- vector metadata preserves tenant/deployment and opaque protected-binding
  identity; and
- a second deployment/resource binding cannot read or mutate the first.

### 10.3 Webhooks

- each supported verifier profile passes valid raw-body authentication;
- missing/invalid authentication rejected before JSON parsing;
- oversized, malformed, unknown, old, duplicate, and out-of-order events handled
  deterministically;
- a verified event extracts only the configured bounded record key, while a
  missing/oversized key fails closed and no raw event payload is retained;
- duplicate events for one record are deduplicated or coalesced and delayed
  events fetch current provider state rather than replaying stale payload data;
- response acknowledgement meets provider timing;
- retry and dead-letter state survive restart;
- replay cannot duplicate indexing or business effects; and
- an event for a different protected resource is rejected and alerted.

The hosted simulator must originate the signed requests over HTTPS. Calling
the connector service directly from an in-process test does not prove ingress,
TLS routing, deployment URL isolation, or provider acknowledgement behavior.

The documentation-informed fixture additionally proves the published Stock Sync
shape: `PUT`, the `AutoTrader-Signature` timestamp/hash grammar, `STOCK_UPDATE`,
advertiser binding, bounded `data.metadata.stockId` extraction, and current-
record fetch. These checks prove only fixture conformance to reviewed public
documentation, not access to or acceptance by Auto Trader.

### 10.3.1 Protected meeting trigger

- an unauthenticated buyer, anonymous chat session, model-selected action, and
  non-staff browser cannot call the operator endpoint;
- the browser never receives the simulator control key, provider credential,
  notification secret, deployment service token, or webhook destination;
- only allowlisted deterministic scenario IDs and validated fictional vehicle
  input are accepted;
- caller-supplied profile, advertiser/account, target URL, callback URL, secret,
  or arbitrary event body is rejected;
- repeated submission with the same idempotency key cannot create duplicate
  fixture records or business effects;
- the result reports sanitized mutation, event, acknowledgement, reconciliation,
  indexing-work, and searchability stages without returning provider payloads;
- add, update, sold/unpublished, and delete scenarios converge, while a missed
  event is repaired by periodic baseline; and
- disabling the demo feature removes the route or fails closed, while the
  public dealership catalogue and buyer experience remain healthy.

### 10.4 Auto Trader conversation and actions

- live/structured search facts and semantic evidence remain distinguishable;
- current price and availability are revalidated when required;
- unsupported fields are not invented;
- follow-up targets remain inside the conversation and deployment;
- post-action generation uses normalized facts;
- provider-supported reads route only through the provider ACTION package,
  dealership-owned writes route only through the dealership ACTION package, and
  unsupported capabilities fail explicitly without blind cross-system fallback;
- generation failure returns a bounded deterministic summary, not raw JSON;
- dealership-owned lead action requires confirmation and returns one receipt;
  and
- no Auto Trader write exists in the first release.

### 10.5 Lifecycle and operations

- clean install, compile, publish, release, apply, assign, and verify;
- export/import includes references and grant metadata but no secrets or
  restricted provider data;
- sandbox-to-production promotion requires explicit rebinding and revalidation;
- secret rotation, restart, rollback, drain, decommission, and advertiser
  offboarding;
- connector schema migration, backup/restore, credential rotation, role
  isolation, and exact schema cleanup;
- internal connector-to-runtime authorization denial outside its granted
  deployment and scopes;
- source/projection/index deletion proof; and
- exact Platform staging and production release gates.

### 10.6 Auto Trader protocol profile

- form `POST /authenticate` sends only package-declared `key` and `secret`
  values and never logs either;
- `access_token` and `expires_at` are projected, cached, refreshed after expiry
  or one `401`, and never exposed;
- advertiser ID is injected from the immutable protected-resource binding and
  cannot be accepted from model/action input;
- page/pageSize baselining, stock identity, lifecycle/tombstone mappings, and
  current-price/availability revalidation pass;
- `AutoTrader-Signature` HMAC-SHA256 validation uses the untouched raw body and
  rejects wrong timestamp/signature/resource before parsing or enqueueing;
- Auto Trader-specific `403`, `429`, `503`, and correlation-ID policies match
  the exact onboarding-approved contract; and
- all capability-specific demonstration, call-log, and database checks are
  recorded against immutable package/template versions.

## 11. Claim Gates

### 11.1 `HOSTED_GENERIC_SUBSTRATE_VERIFIED`

**Status:** Passed on staging on 2026-09-28. See the bounded
[hosted evidence artifact](../../../../../../../../verification-support/external-vehicle-provider-simulator/evidence/2026-09-28-hosted-generic-substrate.json).

- a separately deployed simulator with a recorded fixture version is healthy;
- two materially different profiles run without Java changes;
- two deployments use different credentials and protected resources;
- baseline/update/delete/indexing and signed-event convergence pass;
- authentication, rate, timeout, malformed, replay, and cross-resource
  failures remain fail-closed;
- restart, backup/restore, rollback, and hard decommission pass; and
- all temporary deployments and fixture credentials are removed afterward.

This claim contains no Auto Trader name or implication.

### 11.2 `DEALERSHIP_DEMO_READY`

**Status:** Passed on staging on 2026-09-30. See the bounded
[hosted dealership evidence](../../../../../../../../verification-support/autotrader-dealership-demo/evidence/2026-09-30-dealership-demo-live.json).

- approved source is visibly labelled as demonstration data;
- real runtime, generation, embeddings, vector store, indexing, retrieval, and
  confirmed dealership action are healthy;
- source/update/delete/indexing canaries pass;
- mobile/desktop UI and staff readiness surfaces pass; and
- no Auto Trader connection or data claim appears.

### 11.3 `AUTOTRADER_SANDBOX_VERIFIED`

- every required Phase 0 through Phase 6 exit criterion, including Phase 3A and
  the provider/dealership split proven in Phase 5A, passes;
- exact credential/grant/advertiser metadata is recorded safely;
- sandbox data limitations are reflected in assertions;
- Auto Trader-required demonstration/call-log checks pass or are explicitly
  recorded as pending; and
- evidence names the immutable plugin/template/deployment versions.

### 11.4 `AUTOTRADER_PRODUCTION_READY`

- written production grant and data-use rights exist;
- production credential and advertiser bindings pass preflight;
- production baseline, retrieval, and approved webhook canaries pass;
- all applicable provider go-live checks pass;
- two-deployment isolation, lifecycle, recovery, and offboarding pass;
- Platform full release-readiness is green; and
- owner approves the exact immutable release evidence.

## 12. Non-Goals

- No standalone Auto Trader bridge.
- No central Platform proxy for provider traffic or source payloads.
- No new Marketplace plugin type.
- No Auto Trader domain constants, labels, token fields, signature headers,
  lifecycle values, or retry durations in AI Fabric, Platform generic schemas,
  or generic connector code.
- No public ingestion endpoint merely to let the colocated connector reach its
  runtime.
- No website scraping.
- No requirement that a dealership website/backend replace its own stock source
  with Auto Trader or the LoomAI simulator.
- No direct indexing of an inbound notification payload when the event is only a
  change signal; current provider state is fetched first.
- No public claim that the neutral simulator is Auto Trader, an Auto Trader
  sandbox, or evidence of provider compatibility/certification.
- No simulator dependency in production customer deployments.
- No production Auto Trader writes in the first release.
- No multi-dealership search in one deployment.
- No silent fallback from failed Auto Trader access to demonstration data.
- No claim based only on a good chat transcript.

## 13. Definition Of Done

LoomAI may claim production Auto Trader integration readiness only when:

1. partner onboarding and exact production capability grants are complete;
2. all required generic substrate changes are implemented and neutral-canary
   proven with the separately hosted simulator's two materially different
   provider profiles and no Auto Trader constants in generic source;
3. Auto Trader packages are published through the existing Marketplace/V04
   lifecycle;
4. one clean deployment can be created without manual config repair;
5. one deployment is bound to exactly one authorized advertiser;
6. provider traffic, state, indexing, and events remain deployment-local;
7. connector state is isolated in its owned schema and its private runtime
   scopes deny cross-deployment or broader admin access;
8. baseline, targeted record fetch, webhook, update, delete, restart, missed-
   event repair, and indexing-work reconciliation pass;
9. the independent dealership website can attach a trusted `stockId` without
   receiving provider credentials or becoming the provider-data source;
10. provider reads and dealership writes route to their declared owners without
    blind fallback, and grounded conversation plus confirmed dealership action
    pass;
11. data rights, attribution, retention, and offboarding are enforced;
12. Auto Trader capability-specific go-live checks pass;
13. LoomAI staging and production release gates pass; and
14. the exact evidence-backed claim is published without implying ungranted
    capabilities.

Until then, the correct status is:

```text
Auto Trader integration planned; LoomAI dealership demonstration can proceed
with clearly labelled approved demo data. The generic deployment-local
integration substrate is staging-hosted verified and the fictional dealership
hosted deployment gate has passed. Partner sandbox, advertiser, data-rights,
package, certification, and production evidence remain separate open gates.
```

## 14. Official Auto Trader Evidence

- [Auto Trader Connect Developer API](https://developers.autotrader.co.uk/api)
- [Auto Trader Connect Capabilities Collection](https://www.postman.com/auto-trader-tam/partner-starter-collections/documentation/60cnu90/new-at-connect-capabilities-collection)
- [Auto Trader Partner Starter Collections](https://www.postman.com/auto-trader-tam/partner-starter-collections/overview)
- [Integration Fundamentals](https://help.autotrader.co.uk/hc/en-gb/articles/21791620456221-Integration-Fundamentals)
- [Integration Fundamentals Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22645899163933-Go-Live-checks-for-Integration-Fundamentals)
- [Vehicle Check Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22676578750237-Go-Live-checks-for-Vehicle-Check)
- [Vehicle Metrics Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673426185501-Go-Live-checks-for-Vehicle-Metrics)
- [Response Metrics](https://help.autotrader.co.uk/hc/en-gb/articles/21871963006237-Introduction-to-Response-Metrics)
- [Stock Sync](https://help.autotrader.co.uk/hc/en-gb/articles/21846314775453-Introduction-to-Stock-Sync)
- [Stock Sync Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673947111325-Go-Live-checks-for-Stock-Sync)
- [Auto Trader Connect Terms](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)
