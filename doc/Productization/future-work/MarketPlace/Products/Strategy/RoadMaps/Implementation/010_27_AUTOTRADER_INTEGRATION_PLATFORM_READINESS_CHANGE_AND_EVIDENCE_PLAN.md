# 010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan

- **Status:** Required-change and evidence plan; LoomAI is not yet entitled to
  claim Auto Trader integration readiness
- **Date:** 2026-09-25
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

LoomAI has enough existing primitives to build an honest dealership experience,
but it is not yet ready to claim that the Platform supports an Auto Trader
integration.

Existing reusable foundations include:

- Marketplace `TEMPLATE`, `DATA`, `ACTION`, and `INFERENCE_PROFILE` packages;
- the V04 draft, validate, version, release, apply, verify, export, import, and
  assignment lifecycle;
- one self-contained runtime and Generic REST Connector per deployment;
- deployment-local conversational orchestration, Data Sync, indexing, vector
  retrieval, structured action results, confirmation, and persistence;
- deployment and Marketplace secret-reference models;
- runtime indexing-work status and administrative diagnostics; and
- customer/backend assignment discovery for current chat and behavior URLs.

The missing production substrate is material:

1. Auto Trader sandbox access is partner-provisioned and LoomAI does not yet
   have recorded sandbox credentials, grants, test advertiser, or test stock.
2. The Generic REST Connector has no OAuth2 client-credentials token lifecycle.
3. It has no explicit trusted deployment-binding injection for a protected
   advertiser ID.
4. Marketplace DATA ingestion does not support a deployment-local paged HTTP
   source.
5. There is no generic inbound provider-webhook contract.
6. Connector rate/fair-usage policy, structured mapping, and durable sync state
   are not sufficient for the provider contract.
7. Assignment discovery does not expose typed backend-only Data Sync,
   indexing-work, or safe-readiness endpoints.
8. Auto Trader-specific Marketplace packages and verification packs do not
   exist.
9. No real Auto Trader sandbox canary has passed.

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

## 4. Current LoomAI Source Audit

This audit reflects the source present on 2026-09-25.

| Area | Current evidence | Verdict for Auto Trader |
| --- | --- | --- |
| Marketplace lifecycle | Existing install, compiler, V04 validation, immutable version/release/apply, verification, export/import | Reuse |
| Marketplace plugin types | `TEMPLATE`, `ACTION`, `DATA`, `INFERENCE_PROFILE`, and governed specialist support exist | Reuse; no `AUTOTRADER` plugin type |
| Plugin secret references | Install forms and install records support `secretRef` values | Reuse, then verify external-provider provisioning and redaction end to end |
| Connector routes | `RestRoutingConfig.ActionRoute` supports bounded method/path, query/body/header templates, timeout, response projection, and authz preflight | Reuse and harden |
| Connector upstream auth | `RestRoutingConfig.UpstreamAuth.AuthType` contains only `NONE` and `API_KEY` | Blocking: add provider-neutral OAuth2 client credentials |
| Connector idempotency | Current store is in-memory | Suitable only for limited reads; blocking for durable external writes and stateful sync |
| Connector retry | Bounded retry status/backoff exists | Extend with provider/service pause and fair-usage policy |
| Marketplace DATA modes | `MarketplaceDatasetSyncService` supports `PACKAGED_SEED`, `EXTERNAL_SYNC_SQL`, and `EXTERNAL_SYNC_FOLDER` | Blocking for Auto Trader baseline pull |
| Existing DATA execution location | Current Marketplace dataset loading and tracking are Platform-backend driven | Must not become the normal Auto Trader data plane; add deployment-local HTTP sync execution |
| Runtime Data Sync | Deployment runtime exposes batch/upsert/delete and vector-space contracts | Reuse as the normalized indexing boundary |
| Index work reconciliation | Runtime admin exposes per-work indexing status and vector overview | Reuse with customer-safe projection |
| Provider inbound webhook | No generic deployment-local provider ingress exists | Blocking for Stock Sync notifications |
| Existing runtime webhook code | Current runtime webhook tables/admin surface manage outbound action-result delivery | Do not misrepresent as inbound provider webhook support |
| Assignment endpoint catalog | `PublicRuntimeEndpointsSummary` exposes chat, operational, agentic, Smart Brain, and review URLs | Extend with scoped backend Data Sync, indexing-work, and safe-readiness URLs |
| Advertiser resource binding | Generic deployment/install config exists, but no typed immutable one-advertiser binding is compiled for this provider | Blocking for safe first release |
| Auto Trader packages | No published DATA/ACTION/TEMPLATE packages | Blocking |
| Auto Trader hosted evidence | None | Blocking |

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
  -> deployment-owned PostgreSQL integration state
  -> runtime Data Sync/indexing
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

## 6. Required Platform And Product Changes

### 6.1 Workstream A: provider-neutral connection profiles

Extend the deployment/Marketplace contract with a typed connection profile
selected by a plugin, not an arbitrary URL or free-form environment map.

Required profile fields:

- stable profile ID and environment label;
- approved API host allowlist;
- approved token host and relative token path;
- auth type `OAUTH2_CLIENT_CREDENTIALS`;
- client ID and client secret references;
- optional audience/scope fields only when declared by the provider contract;
- token expiry/skew, timeout, and bounded retry policy;
- provider/service rate policy;
- capability grant metadata; and
- environment-specific advertiser/resource bindings.

Security requirements:

- the model, browser, caller, action parameters, and webhook payload cannot
  select a connection profile, host, credential, or advertiser;
- secrets are resolved only in the deployment during apply/startup;
- token values are never returned through action facts, traces, exports, admin
  readback, or support bundles;
- token cache is bounded by profile and deployment;
- authentication failure stops affected provider calls until a controlled token
  refresh succeeds; and
- sandbox and production use distinct bindings even if Auto Trader decides to
  issue related integration identities.

### 6.2 Workstream B: OAuth2 client-credentials execution

Add reusable token acquisition to the Generic REST Connector:

1. request a token with the configured profile;
2. cache it until expiry minus safety skew;
3. reuse it rather than authenticating every API call;
4. perform one controlled refresh after an upstream authentication rejection;
5. stop and surface an actionable classified failure if refresh fails;
6. expose only secret-free token posture such as `READY`, `EXPIRING`, or
   `AUTH_FAILED`; and
7. verify concurrent requests do not trigger a token stampede.

This code must have no Auto Trader route, field, or advertiser constant.

### 6.3 Workstream C: trusted deployment bindings

Compile a typed immutable resource binding into each release:

```text
integrationProfileRef
environment
advertiserId
grantedCapabilities[]
sourcePolicyRef
```

The connector injects the advertiser ID and other protected values from this
binding. A plugin may declare where the trusted value is placed, but a caller
or model cannot supply or override it.

Validation must fail when:

- the advertiser ID is missing or ambiguous;
- an action exposes a server-owned field as user input;
- the connection environment and endpoint host disagree;
- a plugin requires a capability absent from the recorded grant;
- a secret reference is unresolved; or
- two advertiser scopes are selected for the first-release template.

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

- perform advertiser-scoped baseline pulls;
- support bounded page/pageSize or approved cursor pagination;
- map records with structured selectors, not scripts;
- validate required identity and advertiser fields;
- persist cursor, source fingerprint/version, last-success time, and failure
  state in deployment-owned PostgreSQL;
- normalize records into the plugin-declared entity contract;
- call the same deployment's runtime Data Sync API;
- reconcile every returned work ID to a terminal state;
- apply update replacement and exact delete/unpublish/sold behavior;
- run periodic baseline reconciliation to repair missed events; and
- expose secret-free counts and lag.

Auto Trader provider data must not be loaded by the central
`MarketplaceDatasetSyncService`. That service can continue to own its current
Platform-side dataset modes, but the new HTTP provider source executes inside
the exact deployment.

### 6.5 Workstream E: generic inbound provider webhooks

Add a deployment-specific generic webhook ingress owned by the deployment-local
connector.

Required contract:

- opaque source ID resolved from immutable deployment configuration;
- provider-declared hash/HMAC verification performed against raw bytes before
  parsing;
- strict content type, body size, timeout, and schema limits;
- event type allowlist;
- trusted advertiser equality check;
- durable event ID/deduplication key;
- replay-window and ordering policy;
- quick provider-appropriate acknowledgement;
- durable reconciliation work and retry/dead-letter state;
- safe operational status and manual replay where provider rules allow; and
- optional canonical CloudEvent forwarding only after validation.

An Auto Trader notification may be a change signal rather than a complete
record. The plugin must declare whether the connector performs a detail read or
baseline reconciliation before changing the projection/index.

### 6.6 Workstream F: bounded mapping and fair usage

Extend connector configuration with reusable typed behavior for:

- selected JSON object/list roots;
- field rename and safe scalar/list projection;
- provider error/warning extraction;
- page/cursor extraction;
- source/check timestamp projection;
- response and collection-size limits;
- per-provider and per-service concurrency/rate budgets;
- `429` pause and retry-after behavior;
- `503` service pause behavior;
- non-retryable `400`, capability `403`, and advertiser-membership `403`
  classification; and
- correlation evidence such as bounded provider call IDs or `CF-Ray` values.

Never expose arbitrary expression evaluation, JavaScript, shell, caller-chosen
URLs, or unbounded JSON traversal.

### 6.7 Workstream G: assignment and customer-backend discovery

Extend assignment discovery with a backend-only endpoint group, protected by
the existing scoped consumer assignment key and private runtime assertion
model. It should include typed URLs for:

- Data Sync batch/upsert/delete;
- indexing work status;
- source/index safe readiness; and
- existing chat/session surfaces.

Do not return runtime-admin credentials or raw admin routes. The customer
backend may receive only URLs it is authorized to call and a safe readiness
projection. Browser code receives neither service credentials nor private
ingestion URLs.

### 6.8 Workstream H: operations and support surfaces

The deployment workspace must show:

- integration environment and exact advertiser binding;
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

### 6.10 Workstream J: dealership demo application

Build the separate ordinary customer application described in `010.26`. It:

- consumes deployment URLs over HTTP;
- contains no AI Fabric dependency or local AI implementation;
- uses approved demo data until sandbox access exists;
- exercises real LoomAI indexing, retrieval, chat, confirmation, and action
  execution;
- identifies the source mode visibly; and
- never reports `Auto Trader connected` while running the demonstration source.

This work can proceed in parallel with partner onboarding after the generic
deployment Data Sync URL and safe-readiness contracts are stable.

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

## 8. Concrete Source Change Map

The exact class split may change during implementation, but ownership must
remain as follows.

| Area | Primary current source | Required direction |
| --- | --- | --- |
| Connector config | `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/.../RestRoutingConfig.java` | Add typed connection/auth/rate/data/webhook contracts |
| Connector validation | `.../RestConnectorStartupValidator.java` | Fail closed on hosts, bindings, secrets, OAuth, pagination, mapping, and webhook policy |
| Connector execution | `.../RestActionExecutionService.java` | Apply token service, trusted bindings, classified provider errors, bounded mapping |
| OAuth lifecycle | New provider-neutral connector service | Acquire, cache, refresh, redact, and expose safe posture |
| Durable integration state | New connector-owned entities/migrations in deployment PostgreSQL | Tokens metadata only, cursors, events, dedupe, reconciliation, dead letters |
| DATA manifest validation | `Platfrom/backend/.../MarketplaceManifestService.java` | Validate `EXTERNAL_SYNC_HTTP` and typed HTTP connector contribution |
| DATA compilation | `.../DeploymentMarketplaceDraftCompilerService.java` | Compile source, mapping, binding, and capability refs into V04 artifacts |
| DATA execution | New deployment-local connector sync worker | Pull provider data and push to local runtime Data Sync |
| Existing Platform dataset sync | `.../MarketplaceDatasetSyncService.java` | Do not route provider data through it; retain current modes only |
| Draft validation | `Platfrom/backend/.../DeploymentDraftValidationService.java` | Validate immutable advertiser/capability/connection bindings |
| Secret/resource lifecycle | Marketplace install and deployment provider secret services | Provision references into exact deployment without export/log leakage |
| Assignment response | `PublicRuntimeEndpointsSummary.java` and `DeploymentAssignmentService.java` | Add scoped backend ingestion/work/readiness endpoint group |
| Runtime indexing | Existing `/api/ai/data-sync/*` and `/api/admin/indexing/work/{workId}` | Reuse; add only safe customer projection where needed |
| Platform operations UI | Deployment workspace integration/data surfaces | Render real runtime/connector state and recovery actions |
| Verification | Platform backend suites and release-readiness scripts | Add neutral substrate, sandbox, isolation, lifecycle, and production gates |

No provider-specific route or field may be added to AI Fabric framework core or
to generic connector Java defaults. Auto Trader details live in Marketplace
package data.

## 9. Implementation Sequence

### Phase 0: freeze contracts and obtain access

- Confirm integration-partner ownership and first design-partner dealership.
- Request the complete sandbox onboarding package.
- Freeze one-advertiser-per-deployment and normalized `dealer-vehicle` identity.
- Confirm data/LLM/vector rights.
- Freeze the current canonical `EXTERNAL_SYNC_HTTP` contract.

Exit: reviewed contracts plus provisioned sandbox identity or a clearly recorded
external blocker. Demo-only work may continue when access is blocked.

### Phase 1: neutral connector foundation

- Implement typed connection profiles and OAuth2 client credentials.
- Implement trusted binding injection.
- Add structured response mapping and fair-usage policy.
- Test against a neutral local provider fixture with no Auto Trader names.

Exit: connector authentication, refresh, advertiser-style binding, errors, and
rate behavior pass locally and in one hosted neutral canary.

### Phase 2: deployment-local HTTP DATA sync

- Add manifest/compiler/validation contract.
- Add deployment-local paged sync worker and durable state.
- Push into runtime Data Sync and reconcile work.
- Prove create/update/delete/restart/reconcile and two-deployment isolation.

Exit: neutral HTTP DATA plugin passes complete hosted indexing lifecycle.

### Phase 3: generic inbound provider events

- Add raw-body verification, schema mapping, dedupe, ordering, replay, and
  reconciliation.
- Add operations status and recovery.
- Prove missed/duplicate/out-of-order/invalid events converge through baseline.

Exit: neutral event-fed source passes hosted lifecycle and failure tests.

### Phase 4: discovery and operator UX

- Add backend-only deployment endpoint discovery.
- Add safe readiness and source/index/work projection.
- Add Platform forms for connection refs, grants, and advertiser binding.
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

- token acquisition, cache reuse, expiry skew, refresh, invalid credentials,
  revocation, concurrent refresh, timeout, and restart;
- host allowlist and caller-selected URL denial;
- trusted advertiser injection and override denial;
- bounded request/response mapping and log redaction;
- `400`, membership/capability `403`, `401`, `429`, `503`, timeout, and malformed
  response classification; and
- provider/service concurrency and pause recovery.

### 10.2 DATA sync and indexing

- baseline pagination terminates and count reconciliation passes;
- repeated baseline is idempotent;
- update replaces searchable content;
- sold/unpublished/delete removes buyer retrieval;
- every accepted Data Sync work ID reaches and records terminal status;
- restart resumes from durable state without duplicate source records;
- scheduled reconciliation repairs a deliberately missed event;
- vector metadata preserves tenant/deployment/advertiser identity; and
- second deployment/advertiser cannot read or mutate the first.

### 10.3 Webhooks

- valid signature/hash accepted;
- missing/invalid authentication rejected before JSON parsing;
- oversized, malformed, unknown, old, duplicate, and out-of-order events handled
  deterministically;
- response acknowledgement meets provider timing;
- retry and dead-letter state survive restart;
- replay cannot duplicate indexing or business effects; and
- event for a different advertiser is rejected and alerted.

### 10.4 Conversation and actions

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
- source/projection/index deletion proof; and
- exact Platform staging and production release gates.

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
- No Auto Trader domain constants in AI Fabric or generic connector code.
- No website scraping.
- No production Auto Trader writes in the first release.
- No multi-dealership search in one deployment.
- No silent fallback from failed Auto Trader access to demonstration data.
- No claim based only on a good chat transcript.

## 13. Definition Of Done

LoomAI may claim production Auto Trader integration readiness only when:

1. partner onboarding and exact production capability grants are complete;
2. all required generic substrate changes are implemented and neutral-canary
   proven;
3. Auto Trader packages are published through the existing Marketplace/V04
   lifecycle;
4. one clean deployment can be created without manual config repair;
5. one deployment is bound to exactly one authorized advertiser;
6. provider traffic, state, indexing, and events remain deployment-local;
7. baseline, webhook, update, delete, restart, and reconciliation pass;
8. grounded conversation and confirmed dealership action pass;
9. data rights, attribution, retention, and offboarding are enforced;
10. Auto Trader capability-specific go-live checks pass;
11. LoomAI staging and production release gates pass; and
12. the exact evidence-backed claim is published without implying ungranted
    capabilities.

Until then, the correct status is:

```text
Auto Trader integration planned; LoomAI dealership demonstration can proceed
with clearly labelled approved demo data while partner sandbox access and the
generic deployment-local integration substrate are completed.
```

## 14. Official Auto Trader Evidence

- [Integration Fundamentals](https://help.autotrader.co.uk/hc/en-gb/articles/21791620456221-Integration-Fundamentals)
- [Integration Fundamentals Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22645899163933-Go-Live-checks-for-Integration-Fundamentals)
- [Vehicle Check Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22676578750237-Go-Live-checks-for-Vehicle-Check)
- [Vehicle Metrics Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673426185501-Go-Live-checks-for-Vehicle-Metrics)
- [Response Metrics](https://help.autotrader.co.uk/hc/en-gb/articles/21871963006237-Introduction-to-Response-Metrics)
- [Stock Sync](https://help.autotrader.co.uk/hc/en-gb/articles/21846314775453-Introduction-to-Stock-Sync)
- [Stock Sync Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673947111325-Go-Live-checks-for-Stock-Sync)
- [Auto Trader Connect Terms](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)
