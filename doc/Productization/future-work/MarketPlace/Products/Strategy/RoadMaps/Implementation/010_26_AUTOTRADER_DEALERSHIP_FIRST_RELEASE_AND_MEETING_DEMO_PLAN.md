# 010.26 Auto Trader Dealership First Release And Meeting Demo Plan

- **Status:** `DEALERSHIP_DEMO_READY` passed on staging on 2026-09-30. The
  conversational quality closure passed on 2026-10-01 with deployment
  `dep-f023c863` version `v15`, AI Fabric `0.8.7`, six indexed fictional
  vehicles, and two consecutive strict `7/7` browser runs. Released
  Marketplace packaging and every real Auto Trader
  access/rights/certification gate remain open.
- **Date:** 2026-09-25
- **Last architecture review:** 2026-10-01
- **Last implementation checkpoint:** 2026-10-01
- **Current LoomAI baseline:** AI Fabric `0.8.7`, Platform `Platform-V11`, V04 deployment lifecycle
- **Deployment boundary:** one dealership, one LoomAI deployment, one Auto Trader advertiser scope
- **Integration posture:** Marketplace plugin-first; no standalone Auto Trader bridge
- **Demo posture:** ordinary customer application using its assigned LoomAI
  deployment directly for public chat; no AI Fabric dependency, no central
  chat proxy, and no false claim of Auto Trader connectivity
- **Demo UI residency:** native full-screen application route in
  `Platfrom/loomai-site`; the dealership backend remains a separately deployed
  ordinary application under `product-demos/autotrader-dealership-demo/backend`

Related plans and evidence:

- [010.25 Auto Trader Connect LoomAI Capability Productization Analysis](010_25_AUTOTRADER_CONNECT_LOOMAI_CAPABILITY_PRODUCTIZATION_ANALYSIS.md)
- [010.21 Consolidated LoomAI AI Enablement Product Profile And Deployment Architecture](010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md)
- [010.23 LoomAI Deployment Behavior Market Readiness Execution Plan](010_23_LOOMAI_DEPLOYMENT_BEHAVIOR_MARKET_READINESS_EXECUTION_PLAN.md)
- [010.24 LoomAI File Document Indexing Platform Support Plan](010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md)
- [010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan](010_27_AUTOTRADER_INTEGRATION_PLATFORM_READINESS_CHANGE_AND_EVIDENCE_PLAN.md)
- [2026-09-30 hosted dealership evidence](../../../../../../../../verification-support/autotrader-dealership-demo/evidence/2026-09-30-dealership-demo-live.json)
- [2026-10-01 dealership conversational-quality evidence](../../../../../../../../verification-support/autotrader-dealership-demo/evidence/2026-10-01-dealership-conversational-quality.json)

Quality and verification references in the public framework repository:

- `examples/real-apps/ai-fabric-live-data-sync`
- `examples/real-apps/chat-capabilities-demo`

These examples are references for app completeness, visible readiness,
end-to-end scenarios, Docker packaging, and verification quality only. The
dealership demo does not consume AI Fabric libraries or copy their runtime code.

Reusable LoomAI customer-chat implementation:

- `max-mode-widget` is the existing generic customer chat application shell;
- its Companion dock provides the persistent compact composer;
- its default Max Mode workspace provides the expanded chat experience; and
- its `public-runtime-anonymous` integration mode already performs runtime
  bootstrap, scoped bearer-token use, direct secure chat calls, fail-closed
  expiry/401 state invalidation, conversation handling, evidence rendering,
  action-result rendering, and confirmation turns. Same-session anonymous
  token renewal is implemented through the deployment-local
  `POST /api/public/chat/session/renew` contract and preserves the
  runtime-issued anonymous session identity.

The dealership demo must reuse this shell. The public AI Fabric
`examples/real-apps/chat-capabilities-demo` remains a behavioral and quality
reference for sessions, RAG, actions, confirmation, Data Sync, and readiness.
It is not the dealership UI, is not deployed inside the dealership composition,
and must not introduce a second runtime or embedded-framework application.

Implementation references:

- `Final_Documentation/Development_Guides/PUBLIC_RUNTIME_BROWSER_TOKEN_INTEGRATION_GUIDE.md`;
- `Final_Documentation/Development_Guides/PUBLIC_ANONYMOUS_ACTION_POLICY_GUIDE.md`;
- `max-mode-widget/docs/WIDGET_AUTH_MODES_AND_CUSTOMER_INTEGRATION_PLAN.md`;
- `Platfrom/loomai-site`;
- `Platfrom/ui/src/pages/PocPage.tsx`; and
- `Platfrom/partner-ui/src/components/PartnerMaxWidgetLiveTest.tsx`.

### Implementation and hosted checkpoint: 2026-09-30

| Area | Current state | Evidence / remaining gate |
| --- | --- | --- |
| Dealership-owned backend | Hosted and healthy | Spring Boot service under `product-demos/autotrader-dealership-demo/backend`; twelve HTTP and sync-contract tests pass; public status reports six fictional vehicles and the assigned runtime |
| Fictional inventory source | Implemented | Six clearly labelled vehicle records, structured filters/facets, stable IDs, source versions, and real attributed imagery |
| Customer application UI | Implemented | Native `/demos/dealership-ai` route with responsive inventory, detail, comparison, Companion dock, and Max Mode |
| Staff workspace | Implemented | Protected session login, sync posture, lead inbox/detail/status, CSRF-protected writes and true server-side logout |
| Browser runtime integration | Hosted and verified | Direct `public-runtime-anonymous` bootstrap, same-session renewal, `/api/chat/me/*`, Companion and Max Mode passed against `dep-f023c863`; invalid renewal still clears stale state without replay |
| Inventory Data Sync client | Hosted and verified | Private signed deployment-local sync completed `6/6`; lifecycle canary proved insert/update/delete, stale-version supersession, and return to six vectors |
| Authorization and actions | Hosted and verified | Indexed inventory read action and grounded answer passed; confirmed test-drive action produced receipt `NFM-33B974CC`, persisted once, and appeared as `NEW` in the protected staff inbox |
| Generic REST Connector routing | Hosted and verified | Deployment connector is healthy, exposes the expected five-action contract, and passed immutable Platform release verification |
| Public catalogue | Live | Experiment entry, screenshot, sitemap, content/static smoke and responsive accessibility browser coverage pass; production public site serves the 2026-10-01 verification record |
| Build and supply-chain posture | Implemented locally | Backend/site production images build; status exposes version/commit/build time; site and widget production dependency audits report zero findings; widget package/artifact ownership is LoomAI-labelled and locally bundled |
| Auto Trader source | Not activated | No credential, advertiser grant, sandbox fixture or production data is claimed; meeting composition remains fictional |

### Hosted closure: 2026-09-30

The staging deployment is `dep-f023c863`, version `ver-a3de38cc`, release
`rel-6b9f8943`. It is `APPLIED_VERIFIED` on AI Fabric `0.8.5`. Fresh Platform
verification `vrf-614ca9d9` passed 25 applicable checks with zero failures and
five intentionally absent runner/document-source checks skipped.

The backend-owned sync run `17a01f69-39a9-4d76-97a4-5349fa07407e` completed
six of six operations. The runtime reported six `dealer-vehicle` vectors after
sync, lifecycle mutation/deletion, and stop-first restart. The final mobile
browser gate against `https://loomai.pro/demos/dealership-ai` passed direct
anonymous bootstrap and renewal, indexed retrieval, deployment-owned inventory
search, confirmed lead execution, contextual suggestions, and protected staff
readback without a browser or runtime failure.

The bounded evidence artifact contains only safe IDs, counts, versions, request
IDs, and statuses. It contains no customer contact value, credential, token,
raw prompt payload, or provider data. The global Platform gate remains honestly
non-green on the separately deferred Shopify `shopify-companion` runtime mode;
that does not change this isolated dealership-demo verdict and must not be
misreported as a global release pass.

Security review during implementation corrected two staff-session defects before
hosted use: the initial login route is now the only unauthenticated staff route,
and logout now sends the required CSRF token so the server session is actually
invalidated.

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

The customer-facing UI is published as a native application route in the
existing LoomAI public site at `https://loomai.pro/demos/dealership-ai`. It is
not a separate frontend service, iframe, or marketing-page simulation. The
ordinary dealership backend remains independently deployed so its persistence,
health, authorization, ingestion, and command boundaries remain representative
of a real customer application.

The customer demo application must use:

- plain customer-application HTTP integration with assigned LoomAI deployment
  URLs;
- the existing LoomAI `max-mode-widget` Companion dock and Max Mode shell in
  `public-runtime-anonymous` mode for buyer chat;
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

### 2.3 Existing generic chat application decision

In this plan, the **generic chat application** means the reusable private
LoomAI `max-mode-widget`, not the framework `chat-capabilities-demo` backend.
The dealership frontend configures and themes the generic shell rather than
building another chat transport, authentication client, conversation client,
or confirmation flow.

The existing Platform POC page and Partner Max Mode live-test component are
integration references for loading, configuring, resetting, and probing this
same widget. They remain operator/test surfaces and are not inserted into the
customer traffic path.

The approved reuse boundary is:

- reuse anonymous runtime bootstrap and secure `/api/chat/me/*` clients;
- reuse the Companion dock, full Max Mode, messages, sources, conversation,
  action-result, clarification, and confirmation behavior;
- disable unrelated commerce/cart CRUD unless a dealership-owned equivalent is
  deliberately implemented;
- add dealership labels and vehicle projections through host configuration and
  bounded UI adapters, without embedding provider or orchestration logic; and
- bundle the reviewed widget artifact with the demo application rather than
  depending on an old personal GitHub Pages URL or unpinned remote asset.

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
  -> https://loomai.pro/demos/dealership-ai
     -> dealership backend inventory, comparison, lead, and staff APIs
  -> LoomAI generic chat application (Companion dock + Max Mode)
     -> POST assigned deployment /api/public/chat/session
     <- short-lived runtime-issued anonymous bearer token
     -> assigned deployment /api/chat/me/query, suggestions, and conversations

Dealership demo/customer backend
  -> dealership application database (inventory presentation + leads)
  -> deployment-local Data Sync/indexing and work-status URLs
  -> deployment safe-readiness URL for the authenticated staff screen
  -> protected authorization and lead/test-drive command endpoints

Assigned LoomAI dealership deployment
  -> AI Fabric runtime and conversation/session persistence
  -> exact-origin public anonymous bootstrap and scoped chat ingress
  -> REMOTE_HTTP authorization for public dealership evidence
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

The browser calls the assigned deployment's deliberately public runtime chat
surface directly. It never calls the connector, Data Sync/admin endpoints,
provider, or Platform data plane. The runtime validates the exact allowed
origin and issues its own short-lived anonymous session identity; the browser
does not choose a session, user, dealership, tenant, deployment, or advertiser
identity.

The dealership backend remains authoritative for inventory presentation,
server-side assignment resolution when needed, ingestion, readiness, remote
authorization decisions, and dealership-owned writes. It is not a chat proxy.

### 7.1 Hard application/deployment boundary

The meeting demo is an ordinary customer application. It must not embed AI
Fabric or recreate LoomAI behavior locally.

| Dealership demo/customer application owns | Assigned LoomAI deployment owns |
| --- | --- |
| Native `loomai-site` demo route and Max Mode host configuration | Runtime-issued anonymous chat identity and token validation |
| Dealer inventory presentation/API | Data Sync acceptance and indexing work |
| Dealer-owned inventory source rows in demo mode | Structured retrieval projection and vector index |
| Comparison UI state | Semantic retrieval and grounded generation |
| Lead/test-drive database and staff inbox | Conversation state and trusted working targets |
| Backend HTTP clients for ingestion/readiness plus protected authz/write endpoints | Plugin execution, confirmation, and normalized action results |
| Safe projection of deployment readiness | Provider, embedding, vector, trace, and runtime health |

The demo application therefore has no AI Fabric Maven or NPM dependency, no
LLM or embedding provider key, no vector database client, and no local RAG or
intent/action pipeline.

### 7.2 Deployment URL communication contract

Each dealership application is configured with its own assigned deployment,
never a central Platform data-plane proxy. For the meeting deployment, the
public-site demo configuration pins the exact public deployment base URL. In a
reusable customer setup, the dealership backend may resolve assignment using
its backend-only scoped assignment credential and project only the non-secret
public runtime descriptor to the frontend. The assignment key is never sent to
the browser.

Required browser-direct and server-to-server flows are:

1. **Anonymous browser bootstrap:** Max Mode sends an empty request to
   `POST /api/public/chat/session` with the browser `Origin`. The runtime creates
   the anonymous session and returns a short-lived, scoped, no-store bearer
   token. Client-provided identity/session fields are prohibited.
2. **Direct chat and session:** Max Mode uses that token with deployment-local
   `/api/chat/me/query`, suggestions, conversation, auth-context, and shell-
   configuration routes. A token expiry/`401` causes one fresh bootstrap and
   retry; no service credential is involved.
3. **Demo inventory synchronization:** dealership backend sends normalized
   inventory upserts/deletes to the deployment-local Data Sync push API and
   reconciles indexing work to a terminal state.
4. **Auto Trader synchronization:** once approved, the deployment-local DATA
   plugin and Generic REST Connector communicate with Auto Trader directly;
   this replaces the demo source flow rather than adding AI code to the website.
5. **Dealership action execution:** after LoomAI obtains the required
   confirmation, the deployment-local ACTION plugin calls a protected dealership
   backend command URL. The backend validates the trusted stock target and
   idempotency key, persists the request, and returns a typed receipt.
6. **Readiness:** the customer backend may retrieve a non-secret deployment
   readiness projection for its staff screen. Runtime-admin credentials and raw
   traces never reach the browser.

The public deployment chat URL is configuration, not a credential. Deployment
service credentials, assignment credentials, provider credentials, and admin
URLs remain backend/deployment-only. A model, prompt, browser field, or action
parameter may not choose or override the deployment or advertiser target.

### 7.3 Public anonymous chat security contract

The first dealership template must explicitly configure all of the following:

- `publicRuntimeBootstrapEnabled=true`;
- one exact HTTPS dealership website origin for anonymous bootstrap and CORS;
- a deployment-unique public-token signing secret plus explicit issuer and
  accepted/default audience;
- bounded anonymous scopes for chat query, suggestions, and conversations;
- `REMOTE_HTTP` authorization whose policy grants only public dealership
  inventory/policy reads inside the fixed deployment scope;
- local runtime bootstrap/query rate limits plus edge abuse and cost controls;
  and
- anonymous action metadata that defaults to denied.

The current neutral vehicle canary template uses `ALLOW_VERIFIED`, which
intentionally rejects `ANONYMOUS_SESSION`; it cannot be copied unchanged for
the dealership browser. For the meeting demo, the ordinary dealership backend
provides the protected remote authorization decision endpoint. It permits only
the fixed public dealership read scope and fails closed for missing, malformed,
cross-deployment, private, staff, ingestion, or admin targets.

Inventory search/detail/compare actions may set `anonymousAllowed=true` only
after their public read boundary is verified. A callback or test-drive action
may allow an anonymous proposer only when it uses a server-owned dealership and
trusted vehicle target, explicit confirmation, validated contact input,
idempotency, abuse controls, and a protected application command endpoint.
Private account, staff, provider, ingestion, and admin actions remain denied.

The widget keeps its public token in memory. A full page refresh therefore
starts a new anonymous session in the first release. Cross-refresh anonymous
continuity is a later explicit security feature and must use a runtime-issued
secure mechanism, not a caller-selected session ID.

The runtime and widget now implement the required origin-checked same-session
renewal operation. Renewal is authenticated by the still-valid anonymous token
and must return the same runtime-issued session ID. If renewal cannot occur,
the widget clears the old conversation, pending confirmation, and persisted
anonymous state before starting a new runtime-issued session. It never replays
an old conversation ID under a new anonymous identity.

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

### 9.2 Code residency and hosting decision

The demo UI is part of the existing LoomAI public-site application, while the
ordinary dealership backend remains a separate private product-demo service:

```text
Platfrom/loomai-site/
  src/layouts/DemoApplicationLayout.astro
  src/pages/demos/dealership-ai/
    index.astro
    staff.astro
  src/features/dealership-demo/
    api/
    components/
    config/
    types/
  public/assets/demos/dealership-ai/

product-demos/autotrader-dealership-demo/
  backend/
```

The public URL is:

```text
https://loomai.pro/demos/dealership-ai
```

This follows the same discoverable-demo principle used by the AI Fabric main
site without copying the framework demo architecture. The UI is a native Astro
application route with bounded client-side TypeScript and the reviewed
`max-mode-widget` browser bundle. It is not hosted in an iframe and does not
require a second frontend application, Coolify service, or domain.

`DemoApplicationLayout.astro` should preserve metadata and accessibility while
omitting the normal marketing footer and minimizing site chrome so the
persistent Companion composer cannot obscure content. It should retain a small,
clear route back to Loom AI Labs.

The dealership backend remains under
`product-demos/autotrader-dealership-demo/backend` because it owns application
data, staff authentication, ingestion, remote authorization, and confirmed
writes. It is deployed and operated independently from the static public site.
Hosting the UI on `loomai.pro` does not turn the public site into an AI, data, or
action proxy.

This is a meeting-demo hosting decision, not the customer production hosting
model. A production dealership embeds the generic Companion/Max Mode shell into
its own website and configures that origin against its assigned deployment. It
does not depend on the LoomAI public site for buyer traffic.

The operational tradeoff is deliberate: a demo UI change redeploys
`loomai-public-site`. The feature must therefore remain isolated, fail locally
when its backend is unavailable, and extend the existing whole-site static,
browser, accessibility, and screenshot gates. A demo backend outage must not
break the home, product, experiment, or research routes.

The public experiment catalogue should contain a Dealership AI Experience entry
whose launch link points to the native demo route. Until written brand approval
exists, the public route and labels use **Dealership AI Experience**, identify
the inventory as demonstration data, and do not present the experience as an
official Auto Trader product.

Both parts remain in the private LoomAI product repository because they
demonstrate a LoomAI product composition and prospective partner integration.
They should follow the completeness, readiness, Docker, test, and verification
standards of the public AI Fabric `examples/real-apps`, but Auto Trader-specific
product/plugin code must not be added to AI Fabric core. Those examples are
quality references only.

### 9.3 Application shape

The demo consists of:

1. **Dealership demo UI inside `Platfrom/loomai-site`**
   - native Astro route with client-side TypeScript only where interaction is
     required;
   - a dedicated full-screen demo layout rather than the standard marketing
     page composition;
   - no iframe and no separately deployed frontend;
   - realistic dealership inventory browsing;
   - vehicle detail and comparison views;
   - the existing `max-mode-widget` generic chat application bundled at a
     reviewed commit/build;
   - `integrationMode="public-runtime-anonymous"` with explicit secure runtime
     routes and no static authorization header;
   - Companion dock configured as the fixed full-width bottom LoomAI composer;
   - the generic/default Max Mode workspace for deeper conversation/comparison;
   - cart/business CRUD disabled unless a deliberate dealership-owned adapter
     is added;
   - dealership wording and bounded vehicle/evidence projections layered over
     the existing message, document, action, and confirmation contracts;
   - responsive mobile/desktop behavior;
   - explicit unavailable states when the backend or assigned deployment cannot
     be reached; and
   - public build-time configuration containing only a dealership backend URL
     and an exact deployment URL or safe runtime-descriptor URL.

2. **Dealership application backend**
   - Spring Boot;
   - ordinary application code with no AI Fabric dependency;
   - inventory presentation API;
   - lead/test-drive persistence and staff inbox;
   - optional backend-only assignment discovery and a safe public runtime-
     descriptor projection for the frontend;
   - deployment-local Data Sync push/reconciliation HTTP client;
   - protected remote authorization endpoint used by the assigned deployment;
   - protected dealership action endpoints called by the deployment connector;
   - exact-origin CORS for `https://loomai.pro` and explicitly approved preview
     origins;
   - no buyer-chat facade or response rewriting;
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

- a full-screen native `/demos/dealership-ai` route with a restrained return to
  Loom AI Labs and no footer collision with the Companion composer;
- dealership header and location identity;
- prominent inventory search and filter controls;
- current featured/available vehicle results;
- vehicle detail page with factual sections and evidence timestamps;
- compare tray and comparison view;
- the reused Companion dock as the persistent bottom chat composer across
  inventory/detail pages;
- the reused generic Max Mode overlay/page for richer conversation;
- confirmation surface for callback/test-drive request;
- completion receipt with reference ID;
- authenticated staff lead inbox; and
- authenticated integration/readiness view.

The UI must not show future capabilities as working controls. Integration
opportunities that are not implemented belong in the meeting narrative or
document, not fake buttons.

The public site build may receive only non-secret configuration such as:

```text
PUBLIC_DEALERSHIP_DEMO_API_BASE_URL
PUBLIC_DEALERSHIP_RUNTIME_BASE_URL
```

The runtime URL may instead come from the backend's safe public runtime
descriptor. Assignment keys, deployment service credentials, provider secrets,
Auto Trader credentials, backend staff credentials, and connector credentials
must never be compiled into the static site or returned to the browser.

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
| `loomai-site` demo route | Public UI | Full dealership experience, Companion dock, Max Mode host configuration, and safe client-side status projection |
| Dealership backend, browser-facing | Public app health | App status, version, commit, build time, and non-secret integration posture |
| Dealership backend, browser-facing | Inventory API | Dealership-scoped list/detail/filter values used by the normal website UI |
| Browser -> LoomAI deployment | Anonymous chat bootstrap | Empty request, exact allowed origin, runtime-issued short-lived scoped token, no caller identity |
| Browser -> LoomAI deployment | Direct Max Mode chat | Secure `/api/chat/me/*` routes for query, suggestions, conversations, shell config, evidence, actions, and confirmation |
| Dealership backend, browser-facing | Public runtime descriptor | Exact deployment chat/bootstrap URLs and non-secret shell options only; no assignment or service credential |
| `loomai-site` plus dealership backend | Compare UI/API | Stable selected stock IDs and typed fields, with AI explanation obtained from the deployment |
| Dealership backend, deployment-facing | Inventory sync worker | Normalized upsert/delete batches sent to the deployment-local Data Sync URL with work reconciliation |
| Dealership backend, called by deployment | Authorization decision | Fail-closed decision for anonymous public dealership reads; no browser access |
| Dealership backend, called by deployment | Lead command | Protected, idempotent create after confirmed deployment action; stable receipt returned |
| Dealership backend, staff-facing | Staff inbox | Authenticated dealership-scoped lead list/detail/status |
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
- The public deployment URL and runtime-issued scoped bearer token are not
  service credentials; the token remains in memory and is never logged or
  exported.
- Guest conversation identity is created by the runtime and bounded to the
  assigned dealership deployment; the browser cannot supply its own identity.
- Lead contact details are collected only at action time.
- PII is excluded from vector content and redacted from traces/support exports.
- Staff inbox access requires a distinct authenticated staff role.
- Anonymous bootstrap and CORS permit only the exact deployed dealership
  website origin. Origin filtering is not treated as bot authentication, so
  public actions remain low privilege and independently rate limited.
- Rate limits cover chat, search, sync, and lead submission.
- Every lead write is idempotent and auditable.
- App reset deletes only demo-app sessions, leads, and invented inventory rows.
- Deployment reset/reindex is a separate backend/operator operation against that
  deployment's scoped admin APIs; a coordinated reset runbook may invoke both,
  but the app does not directly own projections or vectors.

## 13. Implementation Workstreams

### Workstream A: generic Platform integration capabilities

- Reuse and verify the existing template/compiler support for opt-in public
  anonymous runtime bootstrap, exact allowed origins, issuer/audience, and
  scoped public chat metadata.
- Reuse and verify the existing safe provisioning output that exposes public
  deployment chat/bootstrap URLs without exposing assignment keys or service
  credentials.
- Operator-visible public-runtime posture and release checks for origin, token,
  CORS, authorization, and rate-limit configuration.
- Runtime-owned same-session anonymous token renewal using a still-valid token,
  with no caller-supplied subject/session field, plus safe new-session fallback.
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

- Build the full-screen dealership UI as a native route under
  `Platfrom/loomai-site/src/pages/demos/dealership-ai` and keep its browser
  behavior isolated under `src/scripts/dealership-demo.ts` and
  `src/scripts/dealership-staff.ts`.
- Add the demo to the public site's experiment catalogue, static smoke checks,
  sitemap, and desktop/mobile browser suite.
- Build only the dealership backend under
  `product-demos/autotrader-dealership-demo/backend`; do not create a second
  frontend project or frontend container.
- Add the approved meeting dataset to the application database and inventory
  API.
- Bundle the reviewed `max-mode-widget` generic chat application and configure
  its Companion dock and default Max Mode workspace for dealership use.
- Before external release, replace stale package/repository/CDN branding with a
  LoomAI-owned versioned artifact or pin and bundle the exact reviewed source
  commit; do not load an unpinned personal GitHub Pages asset.
- Configure direct `public-runtime-anonymous` bootstrap and `/api/chat/me/*`
  routes from a non-secret public runtime descriptor.
- Configure only runtime environment values `DEALERSHIP_DEMO_API_BASE_URL` and
  `DEALERSHIP_DEMO_RUNTIME_BASE_URL` on the public-site container. Serve the
  safe API URL through `/runtime-config/dealership-demo.json`, obtain exact chat
  routes from the backend runtime descriptor, and reject secrets during
  source/build scans.
- Configure exact-origin backend and runtime CORS for the production public-site
  origin and approved preview origin.
- Use the implemented proactive same-session token renewal and preserve the
  fail-closed clear-before-new-identity fallback.
- Add backend-only HTTP clients for assignment discovery when needed, Data Sync
  work, and safe-readiness URLs.
- Add application-to-deployment inventory sync and work reconciliation.
- Add the fail-closed remote authorization endpoint for anonymous public
  dealership reads; do not add a buyer-chat facade.
- Add protected, idempotent dealership action command endpoints.
- Add vehicle list/detail/compare UI.
- Style and verify the reused Companion dock and Max Mode dealership surfaces.
- Add confirmed lead/test-drive persistence and staff inbox.
- Add backend Dockerfile, health checks, build identity, and deployment
  configuration; reuse the existing `loomai-site` build/deployment pipeline for
  the UI.

### Workstream D: hosted dealership deployment

- Create one staging customer/consumer and deployment.
- Bind persistent PostgreSQL and vector storage.
- Bind a real inference profile.
- Install the exact template, demo DATA plugin, and dealership ACTION plugin.
- Enable public runtime bootstrap with a deployment-unique signing secret,
  issuer/audience, exact website origin, CORS, and bounded chat scopes.
- Configure `REMOTE_HTTP` authorization for the meeting backend's protected
  public-read policy; do not use `ALLOW_VERIFIED` for anonymous buyers.
- Mark only verified public read actions as anonymous and keep all other
  actions denied unless the confirmed lead contract explicitly permits them.
- Configure the dealership action plugin to call the protected demo-backend
  command URL.
- Run baseline/index/retrieval/action verification.
- Deploy the dealership backend to staging Coolify with a stable HTTPS URL.
- Publish the UI through the existing production `loomai-public-site` service at
  `https://loomai.pro/demos/dealership-ai`; do not create a separate frontend
  Coolify application or frontend domain.
- Verify the public-site origin against backend CORS and deployment anonymous-
  bootstrap/CORS allowlists.
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
  The reviewed LoomAI `max-mode-widget` UI dependency/bundle is expected and is
  not an AI Fabric runtime dependency.
- The separate LoomAI deployment image is independently verified to contain the
  current released AI Fabric `0.8.5` artifacts and no locally substituted
  framework build.
- Backend tests pass without skips.
- `Platfrom/loomai-site` check, build, content/static smoke, and browser smoke
  pass with the native demo route included.
- The bundled chat shell reports an exact LoomAI-owned artifact/source version
  and contains no stale third-party package, repository, or CDN branding in the
  customer-visible integration contract.
- The independently deployed dealership backend Docker image builds from a clean
  context and its health reports exact commit and build time.
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

- Browser bootstrap with `{}` returns a no-store token and runtime-created
  anonymous session for the exact configured origin.
- Missing/disallowed origin, caller-supplied session identity, invalid token,
  expired token, wrong issuer/audience, and excess scope fail closed.
- Max Mode calls the assigned deployment directly; network evidence shows no
  Platform or dealership chat-facade hop.
- Proactive renewal preserves the same runtime-issued anonymous identity and
  active in-page conversation before access-token expiry.
- Hard expiry or failed renewal starts a new anonymous identity only after the
  widget clears the old conversation and pending confirmation; it never retries
  a stale conversation ID under the new identity.
- `ALLOW_VERIFIED` is proven to reject the anonymous path; the selected
  `REMOTE_HTTP` policy grants only the bounded public dealership read scope.
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

- Anonymous inventory search/detail/compare succeeds only when the action is
  explicitly `anonymousAllowed` and remote authorization grants the fixed
  dealership read scope.
- Anonymous access to private/staff/provider/ingestion/admin actions fails.
- Lead/test-drive request cannot execute without a selected vehicle.
- Confirmation shows vehicle and submitted contact details.
- Cancel performs no write.
- Confirm persists one request and returns one receipt.
- Repeated confirm with the same idempotency key does not duplicate it.
- Staff inbox sees the request only inside the dealership scope.
- Unauthenticated staff access fails.

### 14.5 UI

- `/demos/dealership-ai` renders as a native full-screen application route, not
  an iframe, marketing card, or redirect to another frontend deployment.
- Existing LoomAI public-site pages and navigation continue to pass their smoke
  and accessibility checks.
- Desktop and mobile Playwright screenshots pass.
- Reused Companion dock does not obscure page content.
- Reused generic Max Mode opens from the dock, shares the conversation,
  preserves in-page continuity, and closes cleanly.
- Sources/documents, normalized action results, clarification, confirmation,
  cancellation, failure, and receipt states render through the existing generic
  chat contracts.
- Vehicle cards and comparison remain readable at supported widths.
- Loading, empty, stale, failed-provider, failed-sync, and confirmation states
  are coherent.
- No control implies unavailable Auto Trader functionality.
- Backend or runtime unavailability produces a bounded explicit state and does
  not break the surrounding LoomAI public site.
- Browser network evidence contains no assignment key, service credential,
  provider secret, connector secret, or staff credential.

### 14.6 Hosted and lifecycle

- Staging deployment is template-backed and immutable.
- Release/apply and assignment pass.
- Restart preserves source, projection, vectors, conversations, and leads as
  designed.
- Export/import omits secrets, PII, transient sessions, and restricted source
  data.
- Rollback and decommission are verified.
- Dealership-specific immutable release verification and browser/action gates
  pass for the exact composition; global Platform release-gate state is
  recorded separately and no unrelated blocker is hidden.

## 15. Meeting Demo Acceptance Gate

The demo is ready for the meeting only when:

1. `https://loomai.pro/demos/dealership-ai` is HTTPS and healthy, and the
   independently deployed dealership backend reports healthy;
2. the UI is visually complete on mobile and desktop;
3. direct anonymous browser bootstrap and Max Mode chat against the assigned
   deployment pass without a static/service credential or chat proxy;
4. same-session anonymous token renewal preserves an active conversation and
   failed renewal resets safely without replaying stale conversation state;
5. a real LLM and embedding provider are required and healthy;
6. source mode is prominently and accurately identified;
7. source, projection, and vector counts are nonzero and aligned;
8. update and delete indexing canaries pass;
9. the complete customer script passes twice from a clean session;
10. the confirmed lead action persists and appears in the staff inbox;
11. provider or retrieval failure does not produce a fake answer;
12. no cross-session/deployment data leakage is observed;
13. build commit/version are visible; and
14. a recovery/reset runbook has been exercised.

Passing this gate means the LoomAI dealership experience is demonstrable. It
does not mean Auto Trader production integration is approved.

**Result on 2026-09-30:** passed as `DEALERSHIP_DEMO_READY`. The claim is
bounded to the fictional Northfield dealership composition and the evidence
artifact linked above.

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
3. Build the dealership experience as a native `loomai-site` demo route, reuse
   the generic Max Mode/Companion shell for direct anonymous deployment chat,
   and build the separate ordinary dealership backend with only assignment,
   ingestion/readiness, authorization, inventory, and lead-command HTTP
   contracts.
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
- The meeting UI is a native full-screen route in `Platfrom/loomai-site`; there
  is no iframe, second frontend service, or separate frontend domain.
- This public-site residency applies to the LoomAI meeting demo only; customer
  dealership websites host their own UI integration and call their own assigned
  deployment directly.
- The dealership backend remains independently deployed from the static public
  site under `product-demos/autotrader-dealership-demo/backend`.
- The dealership browser uses the assigned deployment's explicit public chat
  surface directly through the existing generic Max Mode/Companion shell.
- The dealership backend is not a buyer-chat proxy; it owns application data,
  ingestion/readiness integration, remote authorization, and confirmed writes.
- The dealership app contains no AI Fabric runtime or local AI implementation.
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
demo website remains a plain customer application throughout: its UI is hosted
as a native full-screen route on the LoomAI public site, its ordinary backend is
independently deployed, and all AI behavior comes from its assigned LoomAI
deployment URLs. Its customer-facing chat surface reuses the existing LoomAI
Max Mode/Companion application.

## 21. Post-Verification Fixes And Improvements

This section records improvements discovered after the 2026-10-01 live quality
closure. They are not unresolved blockers for the current fictional demo.
Deployment `dep-f023c863` version `v15` / `ver-d8d76d70`, release
`rel-f899de18`, remains the immutable known-good baseline while these items are
implemented and canaried separately.

The two lists below deliberately separate reusable LoomAI or AI Fabric
mechanics from dealership behavior. Generic code must not contain dealership,
vehicle, Auto Trader, field-name, or answer-text matching. Dealership semantics
belong in the Marketplace composition, deployment configuration, connector
mapping, customer application, and bounded prompt overlay.

### 21.1 General Reusable Improvements

| Priority | Surface and owner | Improvement | Required behavior and evidence |
| --- | --- | --- | --- |
| G1 | Platform deployment lifecycle | Treat Coolify HTTP `429` as a transient control-plane condition | Honour `Retry-After` when present, use bounded backoff, and resume polling the same provider deployment and immutable LoomAI release. A poll-rate limit must not be reported as an application deployment failure. Unit tests and one hosted rate-limit canary must prove no duplicate release or deployment is created. |
| G2 | Platform template/release verification | Support template-owned post-apply verification packs | After a release is provisioned, execute bounded non-destructive sync, readiness, retrieval, read-action, and action-plus-RAG checks declared by the template. Keep the release unverified until required checks pass; record safe request IDs, counts, versions, and statuses as evidence. |
| G3 | AI Fabric chat-session/runtime contract | Promote eligible read-action result items into a bounded conversation working set | Extend the existing generic working-set mechanism so an action contract can declare item ID, vector space, and safe label projections. A later phrase such as `those` can then resolve trusted recent targets without inventing identifiers or passing the literal pronoun to an application action. Preserve attachment precedence, owner/session isolation, maximum item limits, and explicit target-resolution intent. |
| G4 | Runtime and connector observability | Expose sanitized action-input and sufficiency diagnostics in debug/evaluation output | Record allowlisted applied parameters, item count, grounding sufficiency, source freshness, truncation, action failure, and fallback path. Never expose secrets, protected IDs, raw headers, unrestricted payloads, or these diagnostics as ordinary end-user prose. |
| G5 | Platform quality gates | Make repeatable conversational evaluation a deployment-version gate | Run one continuous-session scenario set more than once, retain provider request IDs, and fail on scope loss, invented facts, confirmation/write leakage, or browser/transport errors. A stochastic first pass must not be the sole promotion criterion. |
| G6 | Platform inference operations | Record correctness, latency, token, and cost evidence per inference stage | Keep orchestration and generation model overrides independently configurable. Compare a cheaper model only against the same immutable deployment version and quality corpus; cost reduction cannot override a demonstrated behavioral regression. |
| G7 | Generic chat UI and action contracts | Render structured action facts alongside generated language | Let actions declare safe list, detail, comparison, and receipt projections that the generic UI can render without parsing prose. Generated text explains the result; authoritative prices, statuses, identifiers, and availability remain structured facts. |
| G8 | Prompt governance | Prefer typed contracts and policy over accumulating prompt instructions | Version prompt overlays, show their diff in deployment review, and rerun the bounded quality corpus for every change. Do not use prompts to implement authorization, trusted-target resolution, grounding sufficiency, confirmation, or application validation. |

The only possible framework-level item in this list is G3. AI Fabric already
provides conversation working-set target seeding for retrieved documents. The
remaining opportunity is a generic, declarative action-result projection into
that same bounded mechanism. It should be proposed to AI Fabric only with a
provider-neutral contract and focused evidence; the current v15 result does not
justify an urgent framework patch because configured RAG fallback recovers
safely.

### 21.2 Dealership-Specific Improvements

| Priority | Surface and owner | Improvement | Required behavior and evidence |
| --- | --- | --- | --- |
| D1 | Dealership action plugin and deployment | Project inventory action results into the generic working-set contract once G3 exists | Map only trusted active vehicle ID, stock ID, slug, make/model label, and `dealer-vehicle` scope. Prove that `those`, `the cheaper one`, and `compare the first two` resolve only the latest scoped result set and never cross conversation, tenant, deployment, or dealership boundaries. |
| D2 | Dealership backend and connector | Return and preserve validated applied inventory filters and source freshness | Search responses should expose safe `appliedFilters`, result count, source version/update time, and exact no-match status. Connector evidence should preserve those fields so tests can prove fuel, body type, budget, mileage, and make constraints were actually applied rather than inferred from prose. |
| D3 | Dealership release verification | Add a separate confirmed-write canary with cleanup | Submit one test callback or test-drive request through the real confirmation flow, verify trusted internal `vehicleId` resolution, idempotency, receipt, persistence, and staff-inbox visibility, then delete or mark the synthetic record through an authorized cleanup path. Keep this separate from the no-write conversational quality suite. |
| D4 | Dealership customer experience | Add structured vehicle and comparison presentation to Companion and Max Mode | Render vehicle cards and comparison tables from action facts while keeping the generated grounded explanation. Preserve the existing `executor` mode and `search` position; do not introduce browser-side mode selection or a second AI runtime. |
| D5 | Dealership Marketplace packaging | Remove demo constants from the reusable dealership template | Bind dealership ID, approved origins, application URLs, advertiser scope, plugin versions, vector profile, sync schedule, webhook verification, and secret references at installation. `dealer-demo-001` and fictional source URLs remain fixtures only, never product defaults. |
| D6 | Dealership data operations | Add automatic post-release inventory sync and reconciliation | Require source count, accepted count, indexed count, tombstones, work completion, and freshness to reconcile before the dealership deployment is marked ready. Production-sized customers provide an approved managed vector/object-storage service; local mounted/Lucene storage remains limited to demos or small explicitly accepted deployments. |
| D7 | Dealership prompts and model policy | Freeze the current v15 prompt/model baseline until new repeatable evidence fails | Keep `gpt-5.4-mini` for orchestration at temperature `0` and generation at `0.1`. Do not add more wording for `those`; solve that structurally through D1. Any later prompt change must target a named failed scenario and must not introduce text matching, fabricated facts, or Auto Trader claims. |
| D8 | Dealership performance and cost | Establish quality-preserving latency and cost budgets | The current strict run observed roughly three to nine seconds per turn, with the mixed semantic action-plus-RAG case the slowest. Measure p50/p95 by grounding path and canary parallel action/RAG only for broad mixed queries where measured quality and latency justify the extra retrieval/model cost. |
| D9 | Auto Trader activation | Keep real provider work behind the existing sandbox and production gates | Replace the fictional source only after credentials, advertiser grant, exact capabilities, data rights, retention/attribution rules, and go-live checks are available. None of G1-G8 or D1-D8 changes the current no-connectivity/no-endorsement claim. |

### 21.3 Recommended Execution Order

1. Preserve v15 and its two strict `7/7` reports as the comparison baseline.
2. Implement G1 and G2 in the Platform because they improve every deployment
   type without changing customer behavior.
3. Implement G4 and D2 so later quality decisions have stronger structured
   evidence.
4. Design G3 as a provider-neutral action working-set contract, then canary D1
   without dealership logic in the framework.
5. Add D3 as an isolated synthetic write test with deterministic cleanup.
6. Implement G7/D4 structured presentation without changing the existing
   browser mode or position.
7. Productize D5 and D6 before onboarding a real dealership.
8. Optimize models, prompts, latency, or parallel retrieval only after the
   repeated quality gate remains green.

Do not reopen the already-fixed empty-action grounding issue unless a future
run again reports explicit insufficient action evidence as usable grounding or
skips the configured fallback. Do not weaken confirmation, trusted target
resolution, tenant isolation, source attribution, or application-owned writes
to improve demo fluency.
