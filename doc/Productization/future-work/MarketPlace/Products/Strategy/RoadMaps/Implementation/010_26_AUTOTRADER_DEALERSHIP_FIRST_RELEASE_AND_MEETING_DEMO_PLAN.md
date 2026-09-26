# 010.26 Auto Trader Dealership First Release And Meeting Demo Plan

- **Status:** First-release and meeting-demo implementation plan; implementation has not started
- **Date:** 2026-09-25
- **Current LoomAI baseline:** AI Fabric `0.8.4`, Platform `Platform-V11`, V04 deployment lifecycle
- **Deployment boundary:** one dealership, one LoomAI deployment, one Auto Trader advertiser scope
- **Integration posture:** Marketplace plugin-first; no standalone Auto Trader bridge
- **Demo posture:** ordinary customer application using LoomAI deployment URLs; no AI Fabric dependency and no false claim of Auto Trader connectivity

Related plans and evidence:

- [010.25 Auto Trader Connect LoomAI Capability Productization Analysis](010_25_AUTOTRADER_CONNECT_LOOMAI_CAPABILITY_PRODUCTIZATION_ANALYSIS.md)
- [010.21 Consolidated LoomAI AI Enablement Product Profile And Deployment Architecture](010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md)
- [010.23 LoomAI Deployment Behavior Market Readiness Execution Plan](010_23_LOOMAI_DEPLOYMENT_BEHAVIOR_MARKET_READINESS_EXECUTION_PLAN.md)
- [010.24 LoomAI File Document Indexing Platform Support Plan](010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md)
- [010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan](010_27_AUTOTRADER_INTEGRATION_PLATFORM_READINESS_CHANGE_AND_EVIDENCE_PLAN.md)

Quality and verification references in the public framework repository:

- `examples/real-apps/ai-fabric-live-data-sync`
- `examples/real-apps/chat-capabilities-demo`

These examples are references for app completeness, visible readiness,
end-to-end scenarios, Docker packaging, and verification quality only. The
dealership demo does not consume AI Fabric libraries or copy their runtime code.

## 1. Executive Decision

The first automotive release will provide one self-contained LoomAI deployment
for one dealership. That deployment will:

1. bind to exactly one approved Auto Trader advertiser scope;
2. synchronize that dealership's approved stock baseline;
3. consume approved stock-change notifications;
4. maintain a deployment-local structured inventory projection;
5. index the permitted dealership stock into deployment-scoped semantic search;
6. index approved dealership-owned knowledge such as warranty, delivery, and
   contact policies;
7. answer buyer questions through grounded conversational retrieval and live
   Auto Trader read actions;
8. compare selected vehicles using typed facts;
9. maintain the selected vehicle across conversation turns; and
10. execute real dealership-owned callback or test-drive requests only after
    confirmation.

The release is composed from existing Platform primitives:

- one Marketplace `TEMPLATE`;
- one or more Marketplace `DATA` plugins;
- Marketplace `ACTION` plugins;
- an `INFERENCE_PROFILE`;
- V04 deployment/version/release lifecycle;
- the existing deployment-local Generic REST Connector;
- deployment-local runtime, persistence, vector storage, and endpoints; and
- the existing Conversational behavior with optional Human Review-compatible
  actions.

The first release does not require a new deployment behavior, plugin type,
product lifecycle, or provider-specific runtime service.

## 2. Two Connected Deliverables

This plan produces two related but independently honest deliverables.

### 2.1 First dealership release contract

This is the production-target composition. It is not production-ready until
Auto Trader grants the exact capabilities, the integration passes the applicable
go-live checks, and the full LoomAI release gates pass.

### 2.2 Meeting demonstration application

This is a polished, runnable dealership website and staff workspace modelled on
the quality and completeness of the AI Fabric real-app examples. It demonstrates
the customer experience and integration opportunities before or during the Auto
Trader meeting.

The customer demo application must use:

- plain customer-application HTTP integration with assigned LoomAI deployment
  URLs;
- no AI Fabric Maven/NPM dependency;
- no local LLM, embedding, vector, RAG, orchestration, or conversation engine;
- real indexing, retrieval, generation, and conversation persistence inside the
  LoomAI deployment;
- real structured action results;
- real confirmation behavior; and
- real persistence of dealership-owned lead/test-drive requests.

The assigned LoomAI deployment, not the customer demo application, must run the
current released AI Fabric runtime and real LLM/embedding provider.

The demo may use Auto Trader sandbox data only when credentials and usage rights
have been granted. Otherwise it uses a clearly labelled approved demonstration
dataset through a complete DATA plugin. It must never claim that demonstration
records came from Auto Trader.

## 3. Dealership Isolation Contract

### 3.1 Initial invariant

For the first release:

```text
one dealership
  = one Platform customer/consumer binding
  = one LoomAI deployment
  = one Auto Trader advertiserId
  = one structured stock projection
  = one deployment-scoped vector namespace or collection
  = one deployment-local conversation and action boundary
```

A dealer group with several advertiser IDs receives separate deployments. Group
reporting or cross-dealership search is outside the first release.

### 3.2 Credential qualification

Auto Trader may issue integration-partner credentials that can technically
reach several approved advertisers. LoomAI must still bind each deployment to
one server-owned `advertiserId`. Shared integration credentials do not create a
shared dealership data boundary.

The caller, browser, model, prompt, action parameter, or webhook payload may not
widen the deployment's advertiser scope.

### 3.3 Required fail-closed behavior

The deployment must reject or quarantine:

- a record for another advertiser;
- a caller-provided advertiser ID that differs from the deployment binding;
- a webhook whose advertiser ID does not match the deployment;
- a stock or vehicle target that cannot be resolved inside the deployment;
- a query when source readiness is unknown and a current answer is required;
- a write or lead request without the required identity, consent, or target; and
- any result that cannot preserve its source, target, and checked-at evidence.

## 4. First-Release Feature Set

### 4.1 P0 release features

| Feature | Customer value | Platform implementation | Release evidence |
| --- | --- | --- | --- |
| Dealership binding and preflight | Guarantees the deployment sees only its dealership | Deployment resource binding plus plugin-defined advertiser validation action | Wrong advertiser fails; exact advertiser succeeds |
| Stock baseline synchronization | Loads the dealership's current inventory | Auto Trader stock `DATA` plugin using generic paged HTTP sync | Source count, normalized count, and accepted count reconcile |
| Stock-change synchronization | Keeps local inventory aligned | Plugin-configured hash-authenticated webhook plus periodic reconciliation | Create, update, sold, unpublished, and delete converge |
| Structured inventory projection | Supports exact filtering, state, and operations | Deployment-local PostgreSQL projection | Stable IDs, versions, timestamps, filters, and deletion state verified |
| Semantic inventory indexing | Supports natural-language discovery | AI Fabric Data Sync/indexing into deployment-scoped vector storage | Nonzero vector count, metadata isolation, update replacement, exact deletion |
| Dealership knowledge indexing | Grounds warranty, delivery, location, and support answers | Customer-owned document/data source through a separate DATA plugin | Source/version evidence and exact delete verified |
| Natural-language vehicle search | Lets buyers describe needs instead of filling every filter | Conversational runtime plus semantic retrieval and typed Auto Trader read actions | Relevant dealer-only results with current evidence |
| Exact filters and live validation | Prevents semantic similarity from overriding price or availability | Structured filter plus live selected-vehicle re-read | Price, mileage, availability, and advertiser are current |
| Vehicle details and evidence | Answers practical questions about a selected car | Read-only ACTION plugins for stock, taxonomy, equipment, and approved MOT/history fields | Typed facts, checked-at time, and unsupported-field honesty |
| Vehicle comparison | Helps buyers understand tradeoffs | Typed comparison contract plus grounded generation | Same fields compared; absent fields remain absent |
| Multi-turn target continuity | Supports follow-ups such as "what about this one?" | Trusted conversation working target | Target survives follow-up but cannot cross conversation/deployment |
| Dealer policy answers | Connects a vehicle choice with dealership-specific warranty/support information | Hybrid vehicle facts plus dealership knowledge retrieval | Sources remain distinguishable and applicability is explicit |
| Callback or test-drive request | Converts useful assistance into a real dealership workflow | Dealership-owned confirmed ACTION and application database | Confirmation, persisted request, receipt, and staff visibility |
| Responsive dealership UI | Demonstrates a believable customer integration | Customer website, fixed composer, Max Mode, vehicle cards, compare and action surfaces | Desktop/mobile Playwright and live browser proof |
| Operations and readiness | Lets staff trust sync and AI state | Runtime-backed status plus dealership staff workspace | Source/index counts, freshness, failures, build and provider posture visible |

### 4.2 P1 features only when the exact grant allows them

- Search Finance with structured values and approved wording.
- Vehicle Check and report references with fair-usage enforcement.
- Richer MOT, vehicle history, charge-time, and equipment evidence.
- Auto Trader deep links and attribution required by the production agreement.
- Dealer CRM delivery in addition to the first deployment-local lead inbox.

P1 capability absence must not block the P0 product. The UI and assistant should
state that an unavailable field or service is unavailable rather than fabricate
an answer.

### 4.3 Explicit first-release exclusions

- Multiple dealerships or advertiser IDs in one deployment.
- Cross-dealer search, comparison, analytics, or shared vector spaces.
- Deal Sync, consumer message sync, finance applications, or part exchange.
- Price, stock, availability, media, description, reservation, or deal writes to
  Auto Trader.
- Agentic Specialist Team as a dependency.
- Smart Brain as a dependency.
- Autonomous external writes.
- Scraping dealership or Auto Trader websites.
- Claiming production Auto Trader access from sandbox or demo data.

These capabilities remain later independently granted and verified plugin packs.

## 5. Data Synchronization And Indexing Contract

### 5.1 Source-of-truth flow

```text
Auto Trader advertiser-scoped stock baseline
  -> generic paged HTTP DATA connector
  -> advertiser boundary validation
  -> normalized dealership vehicle projection
  -> AI Fabric indexing work
  -> deployment-scoped vector index

Auto Trader stock notification
  -> deployment-specific generic webhook endpoint
  -> hash verification
  -> advertiser boundary validation
  -> create/update/sold/unpublish/delete projection transition
  -> matching upsert/delete indexing work
  -> reconciliation status
```

A periodic advertiser-scoped baseline or reconciliation job repairs missed or
ambiguous notifications. Webhooks improve freshness but are not the only
recovery mechanism.

### 5.2 Structured and semantic indexes have different jobs

The deployment uses both:

1. **Structured inventory projection**

   Stores exact operational facts and supports deterministic filtering,
   reconciliation, and live-state comparison.

2. **Semantic vector index**

   Stores approved searchable descriptions and context so buyers can ask for a
   vehicle by intended use, preferences, or natural language.

The vector index is derived and rebuildable. It is not the authority for price,
availability, reservation, finance, provenance, or write decisions.

### 5.3 First vehicle entity

Proposed stable entity type:

```text
dealer-vehicle
```

Proposed stable identity:

```text
advertiserId + stockId
```

Searchable content may include only licensed and approved values such as:

- make, model, generation, derivative, and trim;
- body style, fuel, transmission, and colour;
- approved advert title and description;
- approved equipment/features;
- seat/door/body characteristics when present;
- approved EV range/charging descriptions; and
- dealership-authored descriptive tags.

Structured metadata should include:

- deployment and dealership scope;
- advertiser ID and stock ID;
- source version/fingerprint and source updated time;
- indexed version and indexed time;
- lifecycle and publication state;
- price, mileage, year, location, fuel, transmission, and body filters;
- derivative ID and other trusted target IDs; and
- evidence/source class.

Exact numeric and state fields may be metadata/filter inputs without being
embedded into prose. Sensitive or contractually restricted fields are excluded.

### 5.4 Dealer-owned knowledge entities

Separate dealership-owned sources may contribute:

- warranty policy;
- delivery and collection policy;
- test-drive requirements;
- finance contact and escalation wording;
- opening hours and locations;
- accessibility and customer-support information; and
- approved brand/service descriptions.

These records must use a separate entity/source identity from Auto Trader stock.
An answer must show whether a fact came from Auto Trader vehicle data or from a
dealership-owned policy.

### 5.5 Index lifecycle rules

- Baseline creates or replaces the current source revision.
- An unchanged fingerprint does not create duplicate vectors.
- A newer update supersedes older pending work.
- A sold, deleted, or no-longer-published vehicle is removed from customer
  retrieval according to the approved lifecycle mapping.
- Exact indexed IDs are retained long enough to perform deterministic deletion.
- Queue acceptance is not completion; work IDs are reconciled to a terminal
  status.
- A failed candidate update must not silently expose mismatched structured and
  vector versions.
- Reconciliation reports source count, active projection count, indexed count,
  failed count, stale count, and deleted count.
- Rebuild affects only the current deployment's dealership scope.

### 5.6 Query-time freshness rule

Local structured/vector data may find candidate vehicles. Before the assistant
states a selected vehicle's current price or availability, or starts an action,
the runtime must invoke a dealer-scoped live read action when that capability is
available.

The response should distinguish:

- `indexedAt`: when the semantic projection was produced;
- `sourceUpdatedAt`: the latest known source revision;
- `checkedAt`: when current operational state was re-read; and
- `source`: Auto Trader, dealership knowledge, or meeting demonstration data.

## 6. Marketplace Composition

The IDs below are proposed first-release IDs, not currently published plugins.

| Package | Type | First-release responsibility |
| --- | --- | --- |
| `mkp-template-autotrader-dealership-concierge-v1` | `TEMPLATE` | Select `CONVERSATIONAL`, required plugins, inference/vector profiles, shell surfaces, generic connector capabilities, and verification packs. |
| `mkp-data-autotrader-dealership-stock-v1` | `DATA` | Exact advertiser-scoped baseline, webhook mapping, normalized vehicle entity, Data Sync/indexing policy, freshness and deletion behavior. |
| `mkp-action-autotrader-dealership-discovery-v1` | `ACTION` | Advertiser preflight, live stock search/detail, taxonomy/equipment, and approved MOT/history reads. |
| `mkp-data-dealership-knowledge-v1` | `DATA` | Dealership-owned warranty, delivery, location, and support knowledge. |
| `mkp-action-dealership-lead-v1` | `ACTION` | Application-owned callback/test-drive request with confirmation and receipt. |
| Existing approved provider profile | `INFERENCE_PROFILE` | Real generation and embeddings with explicit model and dimensions. |

No `SPECIALIST` package is required for the first release. Adding specialists
before the core conversational data/action contract is proven would increase
complexity without improving the meeting proof.

### 6.1 Plugin relationship to the deployment

```text
published TEMPLATE version
  -> installs exact DATA/ACTION/INFERENCE_PROFILE dependencies
  -> Marketplace compiler resolves contributions into V04 draft
  -> V04 validation checks one advertiser binding and connector capabilities
  -> immutable deployment version pins plugin/config/source hashes
  -> release/apply starts the dealership deployment
  -> verification proves sync, index, chat, action, and isolation contracts
```

Marketplace plugins define the Auto Trader relationship. The Generic REST
Connector provides reusable deployment-local transport and must contain no
hard-coded Auto Trader behavior.

## 7. Runtime And Customer Architecture

```text
Dealership website browser
  -> dealership demo/customer backend
     -> dealership application database (inventory presentation + leads)
     -> deployment-local chat/session URL
     -> deployment-local Data Sync/indexing URL

Assigned LoomAI dealership deployment
  -> AI Fabric runtime and conversation/session persistence
  -> deployment-local structured/vector evidence
  -> installed DATA/ACTION plugins
  -> Generic REST Connector
     -> Auto Trader APIs when sandbox/production access is configured
     -> dealership backend action URL for callback/test-drive persistence

Auto Trader notifications
  -> deployment-specific generic webhook endpoint
  -> normalized projection/index update

LoomAI Platform
  -> creates, configures, releases, assigns, verifies, and operates deployment
  -> does not proxy normal buyer chat, Auto Trader reads, or webhook traffic
```

The browser calls the dealership backend, not the private runtime or connector
with service credentials. The dealership backend supplies verified consumer,
session, and dealership context.

### 7.1 Hard application/deployment boundary

The meeting demo is an ordinary customer application. It must not embed AI
Fabric or recreate LoomAI behavior locally.

| Dealership demo/customer application owns | Assigned LoomAI deployment owns |
| --- | --- |
| Website UI and browser session | AI Fabric runtime and orchestration |
| Dealer inventory presentation/API | Data Sync acceptance and indexing work |
| Dealer-owned inventory source rows in demo mode | Structured retrieval projection and vector index |
| Comparison UI state | Semantic retrieval and grounded generation |
| Lead/test-drive database and staff inbox | Conversation state and trusted working targets |
| Backend HTTP clients for deployment URLs | Plugin execution, confirmation, and normalized action results |
| Safe projection of deployment readiness | Provider, embedding, vector, trace, and runtime health |

The demo application therefore has no AI Fabric Maven or NPM dependency, no
LLM or embedding provider key, no vector database client, and no local RAG or
intent/action pipeline.

### 7.2 Deployment URL communication contract

Each dealership application is configured with its own assigned deployment,
never a central Platform data-plane proxy. For the meeting deployment, the
backend may pin the exact deployment base URL. In a reusable customer setup it
may first resolve the assignment through the consumer assignment endpoint and
then cache and call the returned deployment URL directly.

Required server-to-server flows are:

1. **Chat and session:** dealership backend calls the deployment-local
   chat/query/session endpoints and returns the safe response projection to the
   browser.
2. **Demo inventory synchronization:** dealership backend sends normalized
   inventory upserts/deletes to the deployment-local Data Sync push API and
   reconciles indexing work to a terminal state.
3. **Auto Trader synchronization:** once approved, the deployment-local DATA
   plugin and Generic REST Connector communicate with Auto Trader directly;
   this replaces the demo source flow rather than adding AI code to the website.
4. **Dealership action execution:** after LoomAI obtains the required
   confirmation, the deployment-local ACTION plugin calls a protected dealership
   backend command URL. The backend validates the trusted stock target and
   idempotency key, persists the request, and returns a typed receipt.
5. **Readiness:** the customer backend may retrieve a non-secret deployment
   readiness projection for its staff screen. Runtime-admin credentials and raw
   traces never reach the browser.

Deployment URLs and credentials are backend configuration. A model, browser,
or user-provided field may not choose or override the deployment target.

## 8. Buyer Experience Contract

### 8.1 Search and discovery

The assistant should support questions such as:

- "Show me an electric family car under GBP 25,000."
- "I need an automatic SUV with five seats and a large boot."
- "Which cars are suitable for a long motorway commute?"
- "Show me the cheapest three and explain the tradeoffs."

The runtime should combine:

1. semantic candidate discovery over indexed dealership vehicles;
2. deterministic filters over structured metadata;
3. typed live read actions for current facts; and
4. post-action generation grounded in the resulting facts.

Unsupported filters must be identified explicitly. The model must not translate
"family car" into factual seat or boot claims unless the indexed/live fields
support those claims.

### 8.2 Comparison

The user may select two or three dealership vehicles. Comparison output should
use a fixed schema covering available values such as:

- price and mileage;
- body, fuel, transmission, and year;
- seats/doors when available;
- approved range/charging facts for EVs;
- equipment evidence;
- MOT/history evidence when granted;
- location and availability; and
- why each vehicle matches the expressed need.

Missing values remain `unknown`; the model may not fill them from general model
knowledge.

### 8.3 Vehicle and dealer knowledge

The assistant should answer a question such as "What warranty applies and does
this vehicle have a current MOT?" by combining two independently labelled
sources:

- vehicle/MOT evidence from the approved Auto Trader action; and
- warranty terms from the dealership-owned knowledge source.

If either source is absent, the answer should state the limitation.

### 8.4 Confirmed lead action

The first real write is dealership-owned, not an Auto Trader mutation:

- request a callback;
- request a test drive; or
- ask the dealership team about the selected vehicle.

The action requires:

- a trusted selected stock target;
- buyer-provided contact details;
- explicit confirmation of the displayed details;
- PII minimization and retention policy;
- an idempotency key;
- durable application persistence;
- a normalized action receipt; and
- visibility in an authenticated dealership staff inbox.

The live demo must persist and display the request. A button that only returns a
success message is not acceptable.

## 9. Meeting Demo Application

### 9.1 Purpose

The demo should let Auto Trader and dealership stakeholders experience the
customer outcome rather than only reviewing architecture slides. It should also
make the integration boundary visible enough to discuss capability grants,
webhooks, identifiers, data rights, and future actions.

### 9.2 Code residency

Proposed private repository location:

```text
product-demos/autotrader-dealership-demo
```

The app belongs in the private LoomAI product repository because it demonstrates
a LoomAI product composition and prospective partner integration. It should
follow the completeness, health/readiness, Docker, testing, and deployment
standards of the public AI Fabric `examples/real-apps`, but Auto Trader-specific
product/plugin code should not be added to AI Fabric core. Those examples are
quality references only; their embedded-framework application architecture is
not the architecture of this demo.

### 9.3 Application shape

The demo consists of:

1. **Dealership website frontend**
   - React/TypeScript;
   - realistic dealership inventory browsing;
   - vehicle detail and comparison views;
   - fixed full-width bottom LoomAI composer;
   - Max Mode for deeper conversation/comparison;
   - confirmation and action-receipt surfaces; and
   - responsive mobile/desktop behavior.

2. **Dealership application backend**
   - Spring Boot;
   - ordinary application code with no AI Fabric dependency;
   - browser session and customer context;
   - inventory presentation API;
   - lead/test-drive persistence and staff inbox;
   - deployment assignment/runtime HTTP client;
   - deployment-local Data Sync push/reconciliation HTTP client;
   - protected dealership action endpoints called by the deployment connector;
   - no local generation, embeddings, retrieval, vector storage, or AI
     orchestration;
   - no model/provider key in the application;
   - no deployment service key in the browser; and
   - PostgreSQL in hosted mode.

3. **LoomAI dealership deployment**
   - Conversational behavior;
   - exact first-release plugins;
   - real OpenAI generation and embeddings for the meeting deployment;
   - deployment-local structured/vector data;
   - Generic REST Connector; and
   - runtime-backed health, sync, index, and action readback.

### 9.4 Website surfaces

The first screen is the usable dealership experience, not a marketing landing
page. Required surfaces are:

- dealership header and location identity;
- prominent inventory search and filter controls;
- current featured/available vehicle results;
- vehicle detail page with factual sections and evidence timestamps;
- compare tray and comparison view;
- persistent bottom chat composer across inventory/detail pages;
- Max Mode overlay/page for richer conversation;
- confirmation surface for callback/test-drive request;
- completion receipt with reference ID;
- authenticated staff lead inbox; and
- authenticated integration/readiness view.

The UI must not show future capabilities as working controls. Integration
opportunities that are not implemented belong in the meeting narrative or
document, not fake buttons.

### 9.5 Demo data modes

#### Mode A: Auto Trader sandbox

Use only after Auto Trader Connect partner onboarding provisions sandbox
credentials, endpoint details, exact capability grants, and an authorized test
advertiser, and the exact data usage is approved before the meeting. The
reviewed official documentation describes credentialed sandbox testing and
Integration Manager validation; it does not expose anonymous credentials or a
public self-service sandbox flow.

- Install the Auto Trader stock DATA and discovery ACTION plugins.
- Bind sandbox credential references in deployment secrets; never place values
  in plugin manifests, exports, browser state, screenshots, or demo logs.
- Show the exact advertiser binding and sandbox source label.
- Exercise real baseline, reads, and any available webhook test.
- Account for documented sandbox limits: newer vehicles and recent plate
  changes may be absent, while metrics and valuations may use datasets that
  differ from production.
- Make clear that sandbox is not production certification.

#### Mode B: approved dealership demonstration dataset

Use when Auto Trader access is not yet available.

- Keep the invented dealership inventory in the ordinary demo application
  database and expose it through the application's inventory API.
- Install a complete demo DATA plugin using the same normalized
  `dealer-vehicle` contract, field policy, and deletion rules as the planned
  Auto Trader source.
- Have the demo backend push normalized inventory upserts/deletes to that
  deployment's Data Sync URL and reconcile returned work IDs.
- Use invented vehicle identities and no real registration, VIN, consumer, or
  dealer-confidential data unless explicitly approved.
- Label every result source as `Dealership demonstration inventory`.
- Have the LoomAI deployment perform real AI Fabric indexing, vector retrieval,
  OpenAI generation, conversation state, plugin execution, and confirmation.
- Have the deployment call the demo backend's protected action URL so confirmed
  lead/test-drive requests are genuinely persisted by the application.
- Do not expose an Auto Trader connection status of `CONNECTED`.
- Do not emulate a successful Auto Trader webhook or API call.

Mode B is not a stub: it is a complete supported demo data source exercising the
same entity, retrieval, UI, and application-action contracts. It does not count
as proof of Auto Trader transport or production readiness.

### 9.6 Real behavior required in both modes

- Inventory is genuinely indexed inside the assigned LoomAI deployment.
- Retrieval from that deployment returns source documents and metadata.
- Updating a customer-app vehicle and synchronizing it replaces its deployment
  index version.
- Removing a customer-app vehicle and synchronizing it removes it from
  deployment retrieval.
- The deployment LLM uses retrieved/action facts and exposes provider failures.
- Deployment conversation target continuity is real.
- Lead/test-drive actions require deployment-managed confirmation and persist in
  the dealership application database through the protected action URL.
- Health/readiness distinguishes customer-app build state from actual deployment
  provider, source, projection, vector, and runtime state.
- No static prewritten response may satisfy a live AI scenario.

## 10. Meeting Demonstration Script

Target duration: 10 to 15 minutes.

### 10.1 Before attendees join

1. Open the health/readiness view.
2. Confirm current commit, build time, AI Fabric version, model, embedding model,
   source mode, dealership/advertiser binding, source count, vector count, and
   last successful synchronization.
3. Run one private retrieval canary and one lead-action cleanup.
4. Keep a tested backup browser session available.

### 10.2 Customer journey

1. Open the dealership inventory page.
2. Ask: "Find me a family electric car under GBP 25,000."
3. Show that results come only from this dealership and display their source and
   freshness.
4. Add two vehicles to comparison.
5. Ask: "Which is better for motorway driving and why?"
6. Follow with: "What warranty applies to this one and does it have a current
   MOT?"
7. Point out separate vehicle and dealership-policy evidence.
8. Ask to book a test drive or request a callback.
9. Review the selected vehicle and contact details, then confirm.
10. Open the staff inbox and show the persisted request and action receipt.

### 10.3 Integration proof

1. Show the deployment's exact installed TEMPLATE, DATA, ACTION, and inference
   profile versions.
2. Show source/projection/vector counts and one current vector record safely.
3. Update one demonstration record or use an approved sandbox update.
4. Show durable indexing work completion and the replacement version in search.
5. Remove or unpublish one record and show that it disappears from retrieval.
6. Explain the production swap from the labelled demo DATA plugin to the Auto
   Trader DATA/ACTION plugins without changing the customer experience or
   deployment behavior.

### 10.4 Opportunity discussion after the proof

Use the working first release to discuss, without pretending they are already
implemented:

- Search Finance;
- Vehicle Check and richer provenance;
- Deal Sync and messaging;
- dealer staff copilot;
- valuations and vehicle/response metrics;
- event-driven Forecourt Intelligence through Smart Brain;
- listing-quality recommendations; and
- reviewed stock, price, media, availability, and deal operations.

## 11. Demo And Product Communication Surfaces

The exact endpoint paths must use the deployment's published APIs rather than
inventing duplicate AI APIs in the demo. The integration needs these typed
equivalents:

| Owner/direction | Surface | Required contract |
| --- | --- | --- |
| Demo app, browser-facing | Public app health | App status, version, commit, build time, and non-secret integration posture |
| Demo app, browser-facing | Inventory API | Dealership-scoped list/detail/filter values used by the normal website UI |
| Demo app, browser-facing | Chat facade | Session-bound pass-through to the configured deployment chat/session URLs; no local AI behavior |
| Demo app, browser-facing | Compare UI/API | Stable selected stock IDs and typed fields, with AI explanation obtained from the deployment |
| Demo app, deployment-facing | Inventory sync worker | Normalized upsert/delete batches sent to the deployment-local Data Sync URL with work reconciliation |
| Demo app, called by deployment | Lead command | Protected, idempotent create after confirmed deployment action; stable receipt returned |
| Demo app, staff-facing | Staff inbox | Authenticated dealership-scoped lead list/detail/status |
| LoomAI deployment | Chat/session | Grounded query, conversation, confirmation, and normalized result contract |
| LoomAI deployment | Data Sync/indexing | Batch acceptance, work ID/status, counts, failures, and deletion semantics |
| LoomAI deployment | Safe readiness | Generation/embedding posture, source/projection/vector counts, last sync, and retrieval proof |

Debug or admin APIs must not be exposed as public browser controls.

## 12. Security And Privacy

- The demo and release each use one fixed dealership scope.
- The Auto Trader advertiser ID is server-owned.
- Service credentials remain backend/deployment secrets.
- The browser never receives Auto Trader, runtime-admin, connector, provider, or
  Platform-admin credentials.
- Guest conversation identity is bounded to a secure dealership session.
- Lead contact details are collected only at action time.
- PII is excluded from vector content and redacted from traces/support exports.
- Staff inbox access requires a distinct authenticated staff role.
- CORS permits only the deployed dealership website origin.
- Rate limits cover chat, search, sync, and lead submission.
- Every lead write is idempotent and auditable.
- App reset deletes only demo-app sessions, leads, and invented inventory rows.
- Deployment reset/reindex is a separate backend/operator operation against that
  deployment's scoped admin APIs; a coordinated reset runbook may invoke both,
  but the app does not directly own projections or vectors.

## 13. Implementation Workstreams

### Workstream A: generic Platform integration capabilities

- Provider-neutral short-lived access-token profile in the Generic REST
  Connector.
- Trusted deployment-binding parameter injection.
- Bounded structured response mapping.
- Provider/service rate and pause policy.
- Paged HTTP DATA synchronization.
- Generic hash-authenticated webhook ingress.
- Schema-bound webhook-to-CloudEvent mapping.
- V04 capability validation and source attestation.
- Runtime-backed connector/sync/webhook operational readback.

These changes must contain no hard-coded Auto Trader domain behavior.

### Workstream B: first-release Marketplace packages

- Author exact plugin manifests and schemas.
- Add install forms for secret/resource references and advertiser binding.
- Add capability/grant prerequisites.
- Add stable normalized vehicle and action-result contracts.
- Add source/freshness/attribution metadata.
- Add verification-pack references.
- Publish exact versions only after source tests pass.

### Workstream C: meeting demo app

- Build the dealership frontend and backend.
- Add the approved meeting dataset to the application database and inventory
  API.
- Add plain HTTP clients for the exact deployment chat/session, Data Sync work,
  and safe-readiness URLs.
- Add application-to-deployment inventory sync and work reconciliation.
- Add assigned-runtime conversation facade.
- Add protected, idempotent dealership action command endpoints.
- Add vehicle list/detail/compare UI.
- Add fixed composer and Max Mode.
- Add confirmed lead/test-drive persistence and staff inbox.
- Add Dockerfile, health checks, build identity, and deployment configuration.

### Workstream D: hosted dealership deployment

- Create one staging customer/consumer and deployment.
- Bind persistent PostgreSQL and vector storage.
- Bind a real inference profile.
- Install the exact template, demo DATA plugin, and dealership ACTION plugin.
- Configure the dealership action plugin to call the protected demo-backend
  command URL.
- Run baseline/index/retrieval/action verification.
- Deploy the dealership demo app to staging Coolify.
- Bind a stable meeting URL and HTTPS.
- Record sanitized evidence and recovery instructions.

### Workstream E: Auto Trader activation

- Complete partner onboarding and obtain separate sandbox endpoint,
  credential, capability, advertiser, stock-fixture, and webhook-test details.
- Replace the meeting DATA source only after the exact sandbox grant.
- Bind the approved credential secret and exact advertiser ID.
- Run advertiser preflight.
- Run baseline and reconcile counts.
- Register and verify the deployment-specific webhook URL when granted.
- Execute the applicable Auto Trader go-live checks.
- Obtain separate production credentials, advertiser membership, rights, and
  approval before replacing the sandbox bindings or making a production claim.
- Preserve the demo source as a separate non-production composition, never as a
  silent fallback for an Auto Trader deployment.

## 14. Verification Matrix

### 14.1 Source and build

- Demo dependency and source scans prove there is no AI Fabric module,
  `io.github.loom-ai-labs` framework dependency, embedded runtime, copied
  framework source, model client, embedding client, or vector database client.
- The separate LoomAI deployment image is independently verified to contain the
  current released AI Fabric `0.8.4` artifacts and no locally substituted
  framework build.
- Backend tests pass without skips.
- Frontend typecheck/build/tests pass.
- Docker image builds from a clean context.
- Health reports exact commit and build time.
- No secrets or real PII exist in source, image layers, fixtures, or logs.

### 14.2 Data and indexing

- Baseline accepts records only for the bound dealership/advertiser.
- Source, active projection, and index counts reconcile.
- Stable IDs prevent duplicates on repeated baseline.
- Update replaces old searchable content and version.
- Sold/unpublished/deleted record leaves buyer retrieval.
- Failed or superseded work is visible and recoverable.
- Vector metadata contains deployment/dealership scope.
- A second deployment cannot retrieve the first deployment's vehicles.
- Dealership documents remain distinct from vehicle records.

### 14.3 Retrieval and conversation

- Natural-language query returns relevant dealership vehicles.
- Exact budget/fuel/body/transmission filters are respected.
- Zero-result answer is honest and offers bounded alternatives.
- Unsupported fields are not invented.
- Follow-up references resolve only trusted selected targets.
- Comparison uses current typed facts.
- Warranty/MOT answer preserves source separation.
- Generation failure is visible or returns an approved deterministic summary,
  not raw JSON or invented prose.

### 14.4 Actions

- Lead/test-drive request cannot execute without a selected vehicle.
- Confirmation shows vehicle and submitted contact details.
- Cancel performs no write.
- Confirm persists one request and returns one receipt.
- Repeated confirm with the same idempotency key does not duplicate it.
- Staff inbox sees the request only inside the dealership scope.
- Unauthenticated staff access fails.

### 14.5 UI

- Desktop and mobile Playwright screenshots pass.
- Fixed composer does not obscure page content.
- Max Mode opens, preserves conversation, and closes cleanly.
- Vehicle cards and comparison remain readable at supported widths.
- Loading, empty, stale, failed-provider, failed-sync, and confirmation states
  are coherent.
- No control implies unavailable Auto Trader functionality.

### 14.6 Hosted and lifecycle

- Staging deployment is template-backed and immutable.
- Release/apply and assignment pass.
- Restart preserves source, projection, vectors, conversations, and leads as
  designed.
- Export/import omits secrets, PII, transient sessions, and restricted source
  data.
- Rollback and decommission are verified.
- Full Platform release-readiness passes for the exact composition.

## 15. Meeting Demo Acceptance Gate

The demo is ready for the meeting only when:

1. the public meeting URL is HTTPS and healthy;
2. the UI is visually complete on mobile and desktop;
3. a real LLM and embedding provider are required and healthy;
4. source mode is prominently and accurately identified;
5. source, projection, and vector counts are nonzero and aligned;
6. update and delete indexing canaries pass;
7. the complete customer script passes twice from a clean session;
8. the confirmed lead action persists and appears in the staff inbox;
9. provider or retrieval failure does not produce a fake answer;
10. no cross-session/deployment data leakage is observed;
11. build commit/version are visible; and
12. a recovery/reset runbook has been exercised.

Passing this gate means the LoomAI dealership experience is demonstrable. It
does not mean Auto Trader production integration is approved.

## 16. First Production Release Gate

The first production dealership release additionally requires:

1. written Auto Trader capability and data-use approval;
2. production credentials bound through secrets, independently of any sandbox
   credentials;
3. exact production advertiser membership proof;
4. successful advertiser-scoped baseline and reconciliation;
5. verified hash-authenticated stock notifications where granted;
6. applicable Auto Trader go-live checks;
7. source licence, cache, embedding, retention, and attribution approval;
8. real two-deployment isolation proof;
9. customer privacy, support, offboarding, and deletion runbooks;
10. cost and rate limits;
11. Platform staging and production release gates; and
12. owner approval of the exact immutable template and plugin versions.

Sandbox verification is required evidence for the provider integration, but it
does not satisfy any production credential, advertiser, licensing, or go-live
item in this gate.

## 17. Implementation Order

1. Freeze the normalized dealership/vehicle contracts and one-advertiser
   invariant.
2. Implement and verify the generic connector, DATA sync, and webhook gaps.
3. Build the ordinary dealership demo app and its deployment HTTP clients,
   inventory source API, lead command API, and approved demonstration dataset.
4. Publish and install the first dealership template and application-owned lead
   action plus the demo DATA plugin in a LoomAI deployment.
5. Create the staging deployment and prove indexing, retrieval, chat, and action
   behavior live.
6. Polish and rehearse the meeting script.
7. Use the working demo to agree Auto Trader capability scope and integration
   requirements.
8. Implement the Auto Trader DATA/ACTION plugins against sandbox.
9. Replace only the source/action packages in a new immutable deployment version.
10. Pass Auto Trader and LoomAI production gates before making a production
    claim.

## 18. Decisions To Preserve

- One dealership deployment is the product and security boundary.
- One advertiser ID per deployment is the initial rule.
- Dealership stock sync and indexing are required first-release capabilities.
- Structured data and vector data have separate responsibilities.
- Current price and availability require live validation when available.
- Auto Trader semantics live in Marketplace plugins.
- Generic deployment services contain reusable mechanics only.
- The central Platform is the control plane, not the buyer-traffic proxy.
- The dealership app uses deployment URLs and contains no AI Fabric runtime or
  local AI implementation.
- The meeting app demonstrates real LoomAI behavior without faking Auto Trader
  access.
- The first external write is dealership-owned and confirmed.
- Later Auto Trader capabilities are separate granted and verified plugin packs.

## 19. Official Auto Trader References

- [Auto Trader Connect Developer API](https://developers.autotrader.co.uk/api#introduction)
- [Integration Fundamentals](https://help.autotrader.co.uk/hc/en-gb/articles/21791620456221-Integration-Fundamentals)
- [Integration Fundamentals Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22645899163933-Go-Live-checks-for-Integration-Fundamentals)
- [Vehicle Check Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22676578750237-Go-Live-checks-for-Vehicle-Check)
- [Vehicle Metrics Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673426185501-Go-Live-checks-for-Vehicle-Metrics)
- [Response Metrics](https://help.autotrader.co.uk/hc/en-gb/articles/21871963006237-Introduction-to-Response-Metrics)
- [Stock Sync](https://help.autotrader.co.uk/hc/en-gb/articles/21846314775453-Introduction-to-Stock-Sync)
- [Stock Sync Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673947111325-Go-Live-checks-for-Stock-Sync)
- [Search](https://help.autotrader.co.uk/hc/en-gb/articles/21946045692445-Introduction-to-Search)
- [Search Adverts](https://help.autotrader.co.uk/hc/en-gb/articles/21945940067229-Introduction-to-Search-Adverts)
- [Vehicle Taxonomy](https://help.autotrader.co.uk/hc/en-gb/articles/21791924757789-Introduction-to-Vehicle-Taxonomy)
- [Vehicle Equipment](https://help.autotrader.co.uk/hc/en-gb/articles/21792637172125-Introduction-to-Vehicle-Equipment)
- [Vehicle Check](https://help.autotrader.co.uk/hc/en-gb/articles/21872506361757-Introduction-to-Vehicle-Check)
- [Capability Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/sections/22825511475741-Capability-Go-Live-checks)
- [Auto Trader Connect Terms](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)

## 20. Final First-Release Statement

The first LoomAI automotive product is a dealership-owned Conversational
deployment, not a generic Auto Trader chatbot.

It continuously synchronizes and indexes only that dealership's approved stock,
combines semantic discovery with exact and live facts, grounds dealership-policy
answers separately, and turns a selected vehicle into a confirmed real customer
request. Marketplace plugins define every Auto Trader relationship, while the
deployment remains self-contained and the Platform remains the deterministic
control plane.

The meeting demo should make that complete product shape tangible before asking
Auto Trader for the exact production capabilities needed to activate it. The
demo website remains a plain customer application throughout: all AI behavior
comes from its assigned LoomAI deployment URLs.
