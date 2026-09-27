# 010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan

- **Status:** Generic external-provider substrate implemented and locally
  verified; hosted neutral proof, the dealership demo, and all partner-gated
  Auto Trader evidence remain open. LoomAI is not yet entitled to claim Auto
  Trader integration readiness.
- **Date:** 2026-09-25
- **Last contract review:** 2026-09-27
- **Current LoomAI baseline:** AI Fabric `0.8.4`, Platform `Platform-V11`, V04
  deployment lifecycle
- **Product boundary:** one dealership, one deployment, one server-owned Auto
  Trader advertiser scope
- **Architecture decision:** Marketplace plugin-first and deployment-local; no
  standalone Auto Trader bridge and no central Platform data-plane proxy
- **Compatibility posture:** current-only greenfield contract; no legacy mode or
  parallel integration contract

Related plans:

- [010.25 Auto Trader Connect LoomAI Capability Productization Analysis](010_25_AUTOTRADER_CONNECT_LOOMAI_CAPABILITY_PRODUCTIZATION_ANALYSIS.md)
- [010.26 Auto Trader Dealership First Release And Meeting Demo Plan](010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md)
- [010.24 LoomAI File Document Indexing Platform Support Plan](010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md)
- [Marketplace Plugin Manifest Reference](../../../../../../../../Final_Documentation/Development_Guides/MARKETPLACE_PLUGIN_MANIFEST_REFERENCE.md)
- [Generic REST API Connector Guide](../../../../../../../../Final_Documentation/Development_Guides/GENERIC_REST_API_CONNECTOR_GUIDE.md)

## 1. Executive Verdict

LoomAI now has a source-complete, provider-neutral substrate for deployment-local
external HTTP integrations. It is still not ready to claim that the Platform
supports an Auto Trader integration.

Implemented foundations now include:

- Marketplace `TEMPLATE`, `DATA`, `ACTION`, and `INFERENCE_PROFILE` packages;
- the V04 draft, validate, version, release, apply, verify, export, import, and
  assignment lifecycle;
- one self-contained runtime and Generic REST Connector per deployment;
- deployment-local conversational orchestration, Data Sync, indexing, vector
  retrieval, structured action results, confirmation, and persistence;
- deployment and Marketplace secret-reference models;
- bounded API-key and form-token connection profiles with approved hosts,
  token caching/refresh, fair-usage controls, and typed provider failures;
- immutable protected-resource bindings and server-owned path/query/header/body
  injection;
- Marketplace `EXTERNAL_SYNC_HTTP`/`HTTP_JSON`, durable paged/cursor sync,
  tombstones, runtime Data Sync, and indexing-work reconciliation;
- raw-body HMAC webhook verification, deduplication, retry, replay, dead-letter,
  and baseline convergence;
- connector-owned PostgreSQL schema/Flyway state and a restricted deployment-
  scoped role whose privileged bootstrap credentials are removed after setup;
- private connector/runtime networking and deployment-scoped service auth;
- opt-in customer-backend ingestion discovery, safe readiness/work projections,
  Platform operations APIs, and an Integrations workspace; and
- DRAFT-only provider package reservations that deliberately contain no
  executable or published Auto Trader contract.

The remaining evidence/product work is material:

1. Auto Trader sandbox access is partner-provisioned and LoomAI does not yet
   have recorded sandbox credentials, grants, test advertiser, or test stock.
2. A hosted neutral deployment has not yet passed the complete restart,
   two-deployment isolation, webhook, recovery, rollback, and decommission
   evidence pack.
3. The approved dealership dataset, ordinary customer demo application, and
   hosted meeting deployment do not yet exist.
4. Exact Auto Trader DATA/ACTION/TEMPLATE versions cannot be authored or
   published responsibly until the granted routes, schemas, advertiser,
   webhook rules, data rights, and validation requirements are supplied.
5. No real Auto Trader sandbox or production canary has passed.

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
| `DEALERSHIP_DEMO_READY` | LoomAI can demonstrate dealership inventory indexing, retrieval, chat, comparison, and a confirmed dealership-owned lead action | Approved demonstration dataset, real LoomAI deployment, real model/embedding/vector providers, complete live demo gate |
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

The fallback is a clearly labelled dealership demonstration dataset, not a
mocked Auto Trader connection.

## 4. Current LoomAI Implementation Audit

This audit was refreshed against the implementation present on 2026-09-27.

| Area | Current evidence | Verdict for Auto Trader |
| --- | --- | --- |
| Marketplace lifecycle | Existing install, compiler, V04 validation, immutable version/release/apply, verification, export/import | Reuse |
| Marketplace plugin types | `TEMPLATE`, `ACTION`, `DATA`, `INFERENCE_PROFILE`, and governed specialist support exist | Reuse; no `AUTOTRADER` plugin type |
| Plugin secret references | Install forms and install records support `secretRef` values | Reuse, then verify external-provider provisioning and redaction end to end |
| Connector routes | `RestRoutingConfig.ActionRoute` supports bounded method/path, query/body/header templates, timeout, response projection, and authz preflight | Reuse and harden |
| Connector upstream auth | Typed deployment-local profiles support API key and bounded form-token exchange, absolute/relative expiry, single-flight refresh, approved token host, and secret references | Implemented locally; exact Auto Trader profile remains package/external evidence |
| Connector state | Sync cursor/source version, record identity/fingerprint, indexing work, webhook dedupe/replay/dead-letter, and provider correlation are JDBC/Flyway-backed | Implemented locally; hosted restart/restore evidence pending |
| Connector fair usage | Per-profile concurrency, interval, retry status/backoff, and `429`/`503` pauses are bounded and package-configured | Implemented locally; exact provider policy pending written confirmation |
| Connector persistence | Deployment PostgreSQL is reused through connector-owned schema/role; every expected standard/preview bootstrap row must be found, deleted, and proven absent before connector restart on its restricted role | Implemented for Coolify; hosted lifecycle/backup/restore evidence pending |
| Marketplace DATA modes | `EXTERNAL_SYNC_HTTP` with `HTTP_JSON` is validated, compiled, hashed, exported/imported, provisioned, and capability-gated | Implemented locally |
| HTTP DATA execution | Page/size and cursor sources execute in the deployment connector, validate every record against the protected resource, and push only normalized operations to the colocated runtime | Implemented locally; hosted two-deployment proof pending |
| External binding precedent | Document Knowledge Operations now provides target-scoped customer-storage bindings, secret references, safe readback, export/import boundaries, lifecycle cleanup, and operations UI | Reuse/generalize the lifecycle pattern; do not overload the document-storage-specific contract |
| Runtime Data Sync | Deployment runtime exposes batch/upsert/delete and vector-space contracts | Reuse as the normalized indexing boundary |
| Index work reconciliation | Runtime admin exposes per-work indexing status and vector overview | Reuse with customer-safe projection |
| Provider inbound webhook | Deployment connector exposes per-source signed ingress with raw-body verification, replay-window checks, durable dedupe, bounded reconciliation, retry, replay, and dead-letter state | Implemented locally; hosted signed-event proof pending |
| Existing runtime webhook code | Current runtime webhook tables/admin surface manage outbound action-result delivery | Do not misrepresent as inbound provider webhook support |
| Assignment endpoint catalog | Internal connector/runtime traffic stays private; templates may opt in to backend-only ingestion batch, work-status, and readiness URLs with exact operation flags | Implemented locally; browser exposure is prohibited |
| Protected resource binding | Typed immutable profile/resource/grant contracts compile server-owned values into provider requests; records and events must match the same binding | Implemented locally; Auto Trader's one-advertiser rule remains package-owned |
| Operations | Runtime proxy, Platform operations API, audit events, and Integrations UI expose bounded source/auth/index/webhook state plus controlled reconcile/replay; source record IDs and payload/resource fingerprints are omitted | Implemented locally |
| Rejected webhook retention | Invalid/unauthenticated attempts increment durable fixed-cardinality source/error counters without storing attacker-controlled event rows, payloads, identities, or hashes | Implemented locally; hosted abuse/restart proof pending |
| Generated-secret cleanup | Hard delete clears deployment-generated connector service/database credentials after infrastructure cleanup succeeds | Implemented locally; hosted decommission proof pending |
| Auto Trader packages | DRAFT catalog reservations exist, with no executable versions | Correctly blocked pending partner grants and schemas |
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
- boundary tests for host allowlists, capability grants, auth-header collisions,
  protected path placeholders, record/resource mismatch, malformed/oversized
  responses, cursor-version reset, tombstones, retry safety, webhook signatures,
  duplicates, replay, dead-letter behavior, bounded rejection aggregation,
  bootstrap readback, and generated-secret cleanup; and
- production UI compilation for the deployment Integrations workspace.

This is implementation and local verification evidence. It is not hosted
neutral evidence, dealership demo evidence, or Auto Trader evidence.

Final clean local gate on 2026-09-27:

- Generic REST Connector: `35` tests across `10` suites, zero failures/errors;
- AI Fabric Runtime: `221` tests across `53` suites, zero failures/errors;
- Platform backend: `856` tests across `153` suites, zero failures/errors;
- Platform UI: production TypeScript/Vite build passed;
- runtime capability manifest JSON and whitespace checks passed; and
- generic Java source scan found no Auto Trader, advertiser, signature-header,
  or provider-correlation constants.

## 5. Target Deployment-Local Architecture

Normal customer and Auto Trader traffic must bypass the central Platform data
plane.

```text
dealership browser
  -> dealership backend
  -> assigned deployment chat/session endpoints

assigned deployment
  -> AI Fabric runtime
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
  -> deduplication and durable reconciliation work
  -> Auto Trader detail/baseline read when required
  -> structured projection and Data Sync update/delete

LoomAI Platform
  -> install, compile, release, assign, observe, verify, promote, and retire
  -> never proxy routine Auto Trader calls, buyer chat, or source records
```

The Platform may poll bounded deployment admin projections. It must not receive
credential values, full inventory payloads, model prompts, or routine provider
events.

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
- safe operational status and manual replay where provider rules allow; and
- optional canonical CloudEvent forwarding only after validation.

Webhook registration is lifecycle state, even when the provider registers it
manually. Platform must display `NOT_REGISTERED`, `PENDING_VERIFICATION`,
`ACTIVE`, or `DISABLED`, retain no signature secret value, and block a provider
readiness claim until a signed canary reaches the exact deployment URL. URL or
secret rotation requires drain, re-registration, verification, and rollback
evidence.

The Auto Trader package declares HTTP `PUT`, `AutoTrader-Signature`, its `t` and
`v1` parser, timestamp plus `.` plus raw-body HMAC-SHA256 construction,
resource extraction, event types, and response mapping. A notification may be
a change signal rather than a complete record, so that package also declares
whether to perform a detail read or baseline reconciliation before changing the
projection/index.

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

After the generic substrate passes neutral tests, publish exact packages:

| Proposed package | Type | Responsibility |
| --- | --- | --- |
| `mkp-data-autotrader-dealership-stock-v1` | `DATA` | Advertiser-scoped baseline, mapping, event reconciliation, normalized vehicle entity, indexing/freshness/delete policy |
| `mkp-action-autotrader-dealership-discovery-v1` | `ACTION` | Advertiser preflight, live stock search/detail, and only the granted evidence reads |
| `mkp-data-dealership-knowledge-v1` | `DATA` | Dealer-owned warranty, delivery, support, and location knowledge with distinct attribution |
| `mkp-action-dealership-lead-v1` | `ACTION` | Confirmed callback/test-drive command to the dealership-owned backend |
| `mkp-template-autotrader-dealership-concierge-v1` | `TEMPLATE` | Exact behavior, plugins, provider/vector profiles, bindings, endpoints, UI modules, and verification packs |

The package names do not become official until published versions and hosted
evidence exist.

Connection profiles, protected-resource bindings, route mappings, webhook
profiles, and provider labels are contributions inside these existing package
types. They do not introduce a `CONNECTION`, `WEBHOOK`, or `AUTOTRADER`
Marketplace plugin type.

### 6.10 Workstream J: dealership demo application

Build the separate ordinary customer application described in `010.26`. It:

- consumes deployment URLs over HTTP;
- contains no AI Fabric dependency or local AI implementation;
- uses approved demo data until sandbox access exists;
- exercises real LoomAI indexing, retrieval, chat, confirmation, and action
  execution;
- identifies the source mode visibly; and
- never reports `Auto Trader connected` while running the demonstration source.

This work can proceed in parallel with partner onboarding after the opt-in
customer-backend Data Sync URL and safe-readiness contracts are stable. The
demo backend receives those URLs and matching scopes; the browser does not.

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
| Token lifecycle | New provider-neutral connector service | Execute the implemented bounded auth strategies; first release covers API key and form token exchange, including cache, refresh, redaction, and safe posture |
| Durable integration state | Connector persistence package and `db/migration/integration/V1__integration_connector_state.sql` in schema `integration_connector` | Cursors, source fingerprints, work, events, dedupe, reconciliation, and dead letters; never token/credential values |
| Connector database provisioning | `RailwayProvisioningPlanService`, `CoolifyDeploymentProvider`, provider secret/resource services | Reuse the deployment PostgreSQL resource; create a restricted connector role/schema, inject JDBC config, verify readback, and include lifecycle cleanup |
| DATA manifest validation | `Platfrom/backend/.../MarketplaceManifestService.java` | Validates `EXTERNAL_SYNC_HTTP` and typed HTTP connector contribution |
| DATA compilation | `.../DeploymentMarketplaceDraftCompilerService.java` and `DeploymentConfigCompiler.java` | Compile immutable source, mapping, binding, capability, secret, and webhook refs into V04 artifacts |
| DATA execution | `HttpDataSyncService` and `RuntimeDataSyncClient` | Pull provider data, normalize it, and push to private local runtime Data Sync |
| Existing Platform dataset sync | `.../MarketplaceDatasetSyncService.java` | Do not route provider data through it; retain current modes only |
| Draft validation | `Platfrom/backend/.../DeploymentDraftValidationService.java` | Validate immutable protected-resource/capability/connection bindings and package-specific cardinality |
| Secret/resource lifecycle | Marketplace install, provider secret services, and the document-storage binding lifecycle precedent | Generalize target-scoped reference/readback/rotation/export/decommission patterns without coupling to document storage |
| Internal service auth | Provisioning plans plus runtime ingress authorization | Inject private runtime URL and generated connector identity scoped only to Data Sync writes/deletes and work reads |
| Optional assignment response | `PublicCustomerBackendIngestionSummary.java` and `PublicProvisioningApiService.java` | Scoped backend ingestion/work/readiness group only when enabled by immutable dataset contract; never internal connector discovery |
| Runtime indexing | `RuntimeIntegrationDataSyncController`, `RuntimeIntegrationIndexingController`, and `RuntimeCustomerIngestionController` | Private connector boundary plus opt-in customer-safe projection |
| Platform operations UI | `DeploymentIntegrationOperationsService`, controller, and `IntegrationsPage.tsx` | Real runtime/connector state, reconcile, webhook events, and controlled replay |
| Verification | Platform backend suites and release-readiness scripts | Add neutral substrate, sandbox, isolation, lifecycle, and production gates |

No provider-specific route or field may be added to AI Fabric framework core,
generic Platform schema names, or generic connector Java defaults. Auto Trader
details live in immutable Marketplace package data and provider-specific
verification fixtures.

## 9. Implementation Sequence

Current execution status:

| Phase | Status | Honest checkpoint |
| --- | --- | --- |
| 0 | `PARTIAL_EXTERNAL_BLOCKER` | Generic contracts are frozen; partner identity, grants, test advertiser, credentials, webhook details, and data rights are still required |
| 1 | `IMPLEMENTED_LOCAL_VERIFIED` | Generic profiles, auth, protected resources, mapping, fair usage, JDBC state, fail-closed bootstrap scrub, generated-secret cleanup, and neutral fixtures pass locally; hosted lifecycle proof remains |
| 2 | `IMPLEMENTED_LOCAL_VERIFIED` | Marketplace compile/provisioning, private Data Sync, cursor/version handling, tombstones, and work reconciliation pass locally; hosted isolation remains |
| 3 | `IMPLEMENTED_LOCAL_VERIFIED` | Signed ingress, dedupe, replay, retry, dead-letter, and baseline convergence pass locally; hosted ingress evidence remains |
| 4 | `IMPLEMENTED_LOCAL_VERIFIED` | Scoped discovery, runtime/Platform operations APIs, audit, public webhook projection, and Integrations UI compile and pass locally |
| 5 | `NOT_STARTED` | Requires an approved demo dataset, dealership-owned lead boundary, ordinary customer demo app, and hosted deployment |
| 6 | `BLOCKED_EXTERNAL` | Requires Auto Trader partner sandbox identity, exact grants, advertiser, schemas, events, and validation support |
| 7 | `BLOCKED_EXTERNAL` | Requires production approval, rights, bindings, go-live validation, and controlled production proof |

`IMPLEMENTED_LOCAL_VERIFIED` does not satisfy a phase exit that explicitly
requires hosted evidence. The remaining phases must not be collapsed into a
paper pass.

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
in one hosted neutral canary.

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
- Pass the `DEALERSHIP_DEMO_READY` gate.

Exit: live HTTPS demo with real LoomAI behavior and no false Auto Trader claim.

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
- two neutral package fixtures with different auth, pagination, protected-value
  placement, and error mappings compile and run without Java changes; and
- a source scan finds no Auto Trader names or wire constants outside package
  fixtures and provider-specific verification code.

### 10.2 DATA sync and indexing

- baseline pagination terminates and count reconciliation passes;
- repeated baseline is idempotent;
- update replaces searchable content;
- configured tombstone/absence policy removes retrieval exactly;
- every accepted Data Sync work ID reaches and records terminal status;
- restart resumes from durable state without duplicate source records;
- scheduled reconciliation repairs a deliberately missed event;
- vector metadata preserves tenant/deployment and opaque protected-binding
  identity; and
- a second deployment/resource binding cannot read or mutate the first.

### 10.3 Webhooks

- each supported verifier profile passes valid raw-body authentication;
- missing/invalid authentication rejected before JSON parsing;
- oversized, malformed, unknown, old, duplicate, and out-of-order events handled
  deterministically;
- response acknowledgement meets provider timing;
- retry and dead-letter state survive restart;
- replay cannot duplicate indexing or business effects; and
- an event for a different protected resource is rejected and alerted.

### 10.4 Auto Trader conversation and actions

- live/structured search facts and semantic evidence remain distinguishable;
- current price and availability are revalidated when required;
- unsupported fields are not invented;
- follow-up targets remain inside the conversation and deployment;
- post-action generation uses normalized facts;
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

### 11.1 `DEALERSHIP_DEMO_READY`

- approved source is visibly labelled as demonstration data;
- real runtime, generation, embeddings, vector store, indexing, retrieval, and
  confirmed dealership action are healthy;
- source/update/delete/indexing canaries pass;
- mobile/desktop UI and staff readiness surfaces pass; and
- no Auto Trader connection or data claim appears.

### 11.2 `AUTOTRADER_SANDBOX_VERIFIED`

- every Phase 0 through Phase 6 exit criterion passes;
- exact credential/grant/advertiser metadata is recorded safely;
- sandbox data limitations are reflected in assertions;
- Auto Trader-required demonstration/call-log checks pass or are explicitly
  recorded as pending; and
- evidence names the immutable plugin/template/deployment versions.

### 11.3 `AUTOTRADER_PRODUCTION_READY`

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
- No production Auto Trader writes in the first release.
- No multi-dealership search in one deployment.
- No silent fallback from failed Auto Trader access to demonstration data.
- No claim based only on a good chat transcript.

## 13. Definition Of Done

LoomAI may claim production Auto Trader integration readiness only when:

1. partner onboarding and exact production capability grants are complete;
2. all required generic substrate changes are implemented and neutral-canary
   proven with two materially different provider fixtures and no Auto Trader
   constants in generic source;
3. Auto Trader packages are published through the existing Marketplace/V04
   lifecycle;
4. one clean deployment can be created without manual config repair;
5. one deployment is bound to exactly one authorized advertiser;
6. provider traffic, state, indexing, and events remain deployment-local;
7. connector state is isolated in its owned schema and its private runtime
   scopes deny cross-deployment or broader admin access;
8. baseline, webhook, update, delete, restart, and reconciliation pass;
9. grounded conversation and confirmed dealership action pass;
10. data rights, attribution, retention, and offboarding are enforced;
11. Auto Trader capability-specific go-live checks pass;
12. LoomAI staging and production release gates pass; and
13. the exact evidence-backed claim is published without implying ungranted
    capabilities.

Until then, the correct status is:

```text
Auto Trader integration planned; LoomAI dealership demonstration can proceed
with clearly labelled approved demo data while partner sandbox access and the
generic deployment-local integration substrate are completed.
```

## 14. Official Auto Trader Evidence

- [Auto Trader Connect Developer API](https://developers.autotrader.co.uk/api)
- [Auto Trader Connect Capabilities Collection](https://www.postman.com/auto-trader-tam/partner-starter-collections/documentation/60cnu90/new-at-connect-capabilities-collection)
- [Integration Fundamentals](https://help.autotrader.co.uk/hc/en-gb/articles/21791620456221-Integration-Fundamentals)
- [Integration Fundamentals Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22645899163933-Go-Live-checks-for-Integration-Fundamentals)
- [Vehicle Check Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22676578750237-Go-Live-checks-for-Vehicle-Check)
- [Vehicle Metrics Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673426185501-Go-Live-checks-for-Vehicle-Metrics)
- [Response Metrics](https://help.autotrader.co.uk/hc/en-gb/articles/21871963006237-Introduction-to-Response-Metrics)
- [Stock Sync](https://help.autotrader.co.uk/hc/en-gb/articles/21846314775453-Introduction-to-Stock-Sync)
- [Stock Sync Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673947111325-Go-Live-checks-for-Stock-Sync)
- [Auto Trader Connect Terms](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)
