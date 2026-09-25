# 010.25 Auto Trader Connect LoomAI Capability Productization Analysis

- **Status:** Product and integration analysis; proposed packages and generic connector extensions are not implemented or hosted-proven
- **Date:** 2026-09-25
- **Decision scope:** UK automotive retail integrations using Auto Trader Connect
- **Current LoomAI baseline:** AI Fabric `0.8.4`, Platform `Platform-V11`, V04 deployment lifecycle
- **Compatibility posture:** current-only greenfield design; no legacy runtime or plugin compatibility path

Related LoomAI plans:

- [010.21 Consolidated LoomAI AI Enablement Product Profile And Deployment Architecture](010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md)
- [010.23 LoomAI Deployment Behavior Market Readiness Execution Plan](010_23_LOOMAI_DEPLOYMENT_BEHAVIOR_MARKET_READINESS_EXECUTION_PLAN.md)
- [010.24 LoomAI File Document Indexing Platform Support Plan](010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md)
- [Marketplace Plugin Manifest Reference](../../../../../../../../Final_Documentation/Development_Guides/MARKETPLACE_PLUGIN_MANIFEST_REFERENCE.md)
- [Generic REST API Connector Guide](../../../../../../../../Final_Documentation/Development_Guides/GENERIC_REST_API_CONNECTOR_GUIDE.md)

## 1. Executive Decision

The Auto Trader opportunity is credible and materially larger than replacing a
weak website chat widget. Auto Trader Connect can provide live vehicle,
inventory, taxonomy, finance, provenance, market, deal, message, and operational
capabilities. LoomAI can turn approved subsets of those capabilities into:

1. a live buyer concierge;
2. a bounded dealer sales copilot;
3. an event-driven forecourt intelligence deployment; and
4. a governed listing and deal operations workbench.

These are reusable automotive solution templates built from the existing LoomAI
platform primitives. They are not new Platform deployment behavior types.

The recommended first product is a **read-first Automotive Buyer Concierge** for
one approved dealer. It should search and validate live stock, compare vehicles,
explain equipment and electric-vehicle characteristics, retrieve approved MOT or
vehicle-check evidence, and hand a selected vehicle to a dealer-owned lead or
booking action. It must not rely on a periodically embedded copy of stock as the
authority for price or availability.

The key architectural decision is:

> Auto Trader is a plugin-defined integration executed through the existing
> deployment-local Generic REST Connector. The central LoomAI Platform remains
> the lifecycle control plane and is not the runtime data proxy.

The first implementation must not create a standalone Auto Trader bridge. Auto
Trader semantics belong in versioned Marketplace contributions. The Generic
REST Connector and generic DATA/event infrastructure may be extended only with
provider-neutral capabilities that are also reusable for other REST providers.

The integration should therefore reuse:

- Marketplace `TEMPLATE`, `DATA`, `ACTION`, `SPECIALIST`, and
  `INFERENCE_PROFILE` contributions;
- the existing V04 draft, validation, immutable version, release, apply,
  verification, promotion, rollback, export, and import lifecycle;
- `CONVERSATIONAL`, `AGENTIC_SPECIALIST_TEAM`, and `SMART_BRAIN` behavior
  contracts;
- governed read actions and post-action generation from normalized facts;
- Human Review for sensitive proposed writes;
- the existing deployment-local Generic REST Connector for outbound actions;
- generic HTTP data synchronization and webhook-to-CloudEvent handling;
- deployment-local CloudEvents ingress and typed result delivery; and
- deployment-local persistence, secrets, traces, limits, and audit receipts.

No `AUTOTRADER` plugin type, parallel deployment lifecycle, central Auto Trader
traffic gateway, standalone Auto Trader service, or provider-specific domain
logic in AI Fabric core or the Generic REST Connector should be introduced.

## 2. What The Dealer Assistant Evidence Shows

The reviewed dealer-site conversations show two different maturity levels:

- one assistant mainly returned generic text and redirected the user to buttons
  or a human even for inventory, warranty, MOT, and vehicle questions;
- another assistant could search live-looking inventory, aggregate models and
  attributes, narrow a result set, and show a detailed vehicle record;
- even the stronger assistant had visible contract gaps, including unsupported
  family-car attributes, insurance-category filtering, and an unanswered
  warranty/MOT follow-up;
- neither example proves governed transaction execution, reliable cross-turn
  target selection, source provenance, freshness guarantees, or dealer isolation.

The product lesson is not simply to use a stronger model. A trustworthy
automotive assistant needs:

1. live and explicitly scoped dealer data;
2. typed search and evidence actions;
3. stable vehicle and dealer identifiers;
4. response grounding from action facts;
5. conversational target continuity;
6. deterministic policy around finance, provenance, and writes;
7. freshness and availability revalidation; and
8. a governed handoff into the dealer's real operational systems.

The transcript is evaluation evidence only. It must not be copied into a
template, prompt, fixture, or dataset with customer or user identifiers.

## 3. Product Boundaries

### 3.1 What LoomAI should provide

- A repeatable deployment composition for approved Auto Trader capabilities.
- Typed Marketplace template, data, action, and specialist contracts containing
  the Auto Trader-specific schemas, routes, mappings, policies, and UI metadata.
- Provider-neutral OAuth token, HTTP synchronization, webhook, response-mapping,
  rate-control, and trusted-binding support in existing deployment services.
- Conversational, specialist, and event-driven orchestration over those
  contracts.
- Grounded answers, citations or evidence labels, and stable structured UI data.
- Confirmation, review, authorization revalidation, idempotency, and receipts
  for writes.
- Tenant/deployment isolation, observability, quotas, verification, and support
  evidence.

### 3.2 What LoomAI should not claim

- That every API visible in Auto Trader documentation is included in our grant.
- That sandbox access implies production capability approval.
- That an embedded stock copy is the current source of truth.
- That an LLM may infer a vehicle registration, stock ID, advertiser ID, deal ID,
  price, availability, finance result, or provenance status.
- That LoomAI provides financial advice, vehicle inspection, provenance
  certification, or a warranty.
- That a dealer can change prices, publish adverts, reserve stock, or mutate
  deals autonomously in the first release.
- That Auto Trader data may be embedded, retained, transformed, or shown to an
  LLM without confirming the applicable licence and capability terms.
- That LoomAI replaces the dealer management system, CRM, finance platform, or
  Auto Trader Portal.
- That a provider-specific service is required before the plugin-driven path has
  been implemented and proven insufficient.

### 3.3 Production access is capability-specific

Auto Trader Connect documents many capabilities, but production access and
go-live checks are capability-specific. Every LoomAI template must compile from
the exact capabilities approved for that integration. Missing capability scope
must fail preflight and must not silently degrade to invented or stale data.

## 4. Auto Trader Capability Map

The following is a product map, not a claim that LoomAI currently has access to
each capability.

| Auto Trader capability family | Useful customer outcome | LoomAI primitive | Preferred data posture | Initial release posture |
| --- | --- | --- | --- | --- |
| Advertisers and integration fundamentals | Validate which dealer sites and advertiser IDs belong to the integration | Plugin-defined read action, generic connector preflight, and deployment resource binding | Live API and bounded configuration cache | Required foundation |
| Search and Search Adverts | Find real vehicles by make, model, body, fuel, price, mileage, location, and supported advert fields | Read-only `ACTION` plugin | Live query; short bounded response cache only | First release |
| Search Finance | Return approved finance options associated with matching vehicles | Read-only `ACTION` plugin plus structured UI contract | Live query; do not improvise calculations | Optional first release after compliance review |
| Stock Sync | Establish baseline dealer stock and reconcile event-driven changes | `DATA` plugin plus generic HTTP sync and webhook ingress | Baseline pull plus webhook-driven reconciliation | Next freshness slice after the live-read canary |
| Vehicle Taxonomy | Resolve stable make/model/generation/derivative identity and technical data | Read-only `ACTION`; optional reference projection | Live lookup with bounded taxonomy cache | First release |
| Vehicle Equipment | Explain possible and factory-fitted feature evidence where available | Read-only `ACTION` | Live lookup; distinguish possible from confirmed equipment | First release |
| MOT, vehicle history, charge times | Answer vehicle-specific evidence questions | Read-only evidence `ACTION` | On-demand live lookup with evidence timestamp | First release when granted |
| Vehicle Check | Return approved provenance checks and report references | Restricted read-only evidence `ACTION` | On-demand and fair-usage-aware; no repeated polling | Optional first release when licensed |
| Current, historical, future, or trended valuations | Support dealer appraisal and pricing analysis | Read-only `ACTION` or internal `SPECIALIST` input | On-demand or approved scheduled projection | Later dealer-operations release |
| Vehicle, retail-rating, response, and competitor metrics | Explain market position, demand, views, leads, and expected days to sell | Read-only `ACTION`, `DATA`, and `SMART_BRAIN` | Respect each metric's documented freshness and usage limits | Later intelligence release |
| Stock updates | Create or change approved inventory records and advert fields | Write `ACTION` plus Human Review | Live command with revalidation and receipt | Later controlled release |
| Price updates | Propose and apply explicitly approved price changes | `SMART_BRAIN` proposal plus write `ACTION` and Human Review | Never autonomous in initial product | Later controlled release |
| Availability updates | Keep sold, reserved, available, or unavailable posture aligned | Write `ACTION` plus reconciliation | Event/command with current-state check | Later controlled release |
| Media updates and image ordering | Improve approved advert media and ordering | Write `ACTION`; optional Smart Brain recommendation | Review before mutation | Later controlled release |
| Co-Driver descriptions and smart imagery | Generate or consume Auto Trader-supported listing assistance | Read or write `ACTION` according to granted contract | Preserve attribution and approved output | Later, only after exact rights review |
| Deal Sync | Receive and inspect consumer-originated deals and components | `DATA` plus generic deployment-local webhook ingress | Baseline plus webhook, durable deduplication | Dealer copilot release |
| Deal Updates | Complete, cancel, reserve, or remove approved components from an existing deal | Write `ACTION` plus Human Review | Current deal re-read before command | Dealer copilot release |
| Messages, finance, part exchange, and delivery components | Give dealer staff a complete deal view and governed follow-up | Restricted `DATA`/read `ACTION`; approved writes where exposed | PII-minimized, role-gated, auditable | Dealer copilot release |
| Calls | Expose approved call events or evidence to staff workflows | Restricted `DATA` or read `ACTION` | Purpose-limited and retention-controlled | Later, if licensed |
| Advertiser, stock, and deal notifications | Trigger reconciliation or approved Smart Brain analysis | Plugin-configured generic webhook ingress to canonical CloudEvents | Hash-authenticated event, deduplicated and ordered | Required where capability provides webhooks |

## 5. LoomAI Primitive Mapping

| Concern | Existing LoomAI primitive | Auto Trader use |
| --- | --- | --- |
| Customer solution baseline | Marketplace `TEMPLATE` | Select one behavior, exact required plugins, generic connector capabilities, providers, UI surfaces, and verification packs. |
| Live reads and writes | Marketplace `ACTION` | Publish bounded Auto Trader actions with schemas, read/write posture, confirmation policy, generic connector route, and normalized response mapping. |
| Baseline and event-fed data | Marketplace `DATA` | Declare stock/deal projections, entities, HTTP sync source, webhook event mapping, update strategy, and freshness. |
| Specialist definitions | Governed Marketplace `SPECIALIST` | Package exact read-only vehicle-match, evidence, market, or deal-analysis workers when genuinely distinct. |
| Model and embedding selection | `INFERENCE_PROFILE` | Reuse provider profiles; no Auto Trader-specific inference type. |
| Runtime/connector topology | Existing Deployment Template | Reuse the deployment-local Generic REST Connector; do not add an Auto Trader service. |
| Immutable release truth | V04 Deployment Version | Pin behavior, plugin versions, generic connector/runtime source, required connector capabilities, capability grants, schema hashes, policies, and verification IDs. |
| Credentials | Deployment secret/resource bindings | Store Auto Trader credentials and webhook material by reference, never in plugin bodies or exports. |
| Customer identity and authority | Customer backend assertion plus runtime policy | Bind verified tenant, user, roles, site, and permitted advertiser IDs. |
| Review | Human Review execution extension | Approve sensitive inventory, price, media, reservation, or deal changes. |
| Proactive analysis | `SMART_BRAIN` | Consume trusted stock/deal/metric events or schedules and emit typed results or review proposals. |
| Multi-specialist work | `AGENTIC_SPECIALIST_TEAM` | Coordinate exact read-only specialists for complex staff analysis. |
| Browser experience | Existing docked composer, Max Mode, inline cards, and customer UI contracts | Render vehicle lists, comparisons, evidence, and explicit action confirmation. |
| Operations | Existing deployment workspace | Show connector health, grants, sync lag, webhook posture, action receipts, failures, and verification. |
| Portability | Existing export/import | Export configuration and references; omit credentials, tokens, PII, transient deal state, and licensed cached data unless explicitly permitted. |

No new Marketplace plugin type is required for the first implementation.

## 6. Deployment-Local Architecture

### 6.1 Interactive request path

```text
buyer or dealer staff UI
  -> customer/dealer backend
  -> assigned deployment-local LoomAI runtime endpoint
  -> governed Auto Trader ACTION
  -> existing deployment-local Generic REST Connector
     (route/auth/mapping compiled from the installed ACTION plugin)
  -> Auto Trader Connect API
  -> normalized facts and evidence
  -> post-action LLM generation
  -> structured response to customer/dealer backend
```

The Platform assignment service may tell the customer backend which deployment
is active. It must not proxy each conversation or Auto Trader API call.

### 6.2 Data and event path

```text
Auto Trader baseline API
  -> plugin-configured generic HTTP DATA sync
  -> deployment-local normalized read model / Data Sync projection

Auto Trader webhook
  -> deployment-specific generic webhook ingress
  -> plugin-configured hash authentication + event mapping
  -> deduplication + ordering
  -> baseline/detail re-read when the event is only a notification
  -> projection reconciliation
  -> optional canonical CloudEvent to deployment-local Smart Brain ingress
  -> typed operation result
  -> direct polling and/or deployment-owned signed result webhook
```

Every deployment has its own runtime, connector, data, event, and result
endpoints. No Auto Trader action, event, or result should need to travel through
`api.loomai.pro` during normal runtime operation.

### 6.3 Write path

```text
user request or Smart Brain recommendation
  -> typed proposal with trusted target IDs and current evidence
  -> policy and authorization evaluation
  -> explicit user confirmation or Human Review where required
  -> current Auto Trader state re-read
  -> idempotent plugin-defined Generic REST Connector command
  -> Auto Trader Connect
  -> normalized receipt
  -> reconciliation read/event
  -> final audited result
```

An event-triggered Smart Brain deployment is read-only under the current
market-ready contract. It may produce a recommendation or review request. It may
not execute an Auto Trader write directly.

## 7. Plugin-Driven Generic Integration Architecture

The Auto Trader integration should be a set of reviewed Marketplace
contributions executed by existing deployment services. A plugin is a versioned
declaration and compilation input, not a process that receives webhooks or
stores access tokens. The Generic REST Connector and deployment data/event
services provide that execution substrate.

This decision does not mean the complete integration can be installed through
today's manifests without source changes. It means the missing source changes
must improve the reusable Platform integration substrate rather than create an
Auto Trader-specific runtime service.

### 7.1 Marketplace plugin responsibilities

The Auto Trader plugins own all provider-specific semantics:

- stable LoomAI action IDs and descriptions;
- input and output schemas;
- Auto Trader route, method, query, header, and body mappings;
- bounded structured response and error mappings;
- read/write, confirmation, role, and Human Review policy;
- trusted advertiser, stock, vehicle, derivative, and deal target rules;
- stock/deal dataset and entity definitions;
- baseline pagination and update strategy;
- webhook event-type mapping and reconciliation instruction;
- freshness, attribution, fair-usage, and retention declarations;
- shell cards and structured UI result metadata; and
- required Auto Trader capability and LoomAI verification IDs.

Plugins reference deployment secret and resource bindings. They never contain
credential values, access tokens, arbitrary executable scripts, or customer
data.

### 7.2 Generic connector and data/event responsibilities

The existing deployment services own only reusable transport and operational
mechanics:

- the stable Customer Connector API used by the runtime;
- outbound allowlisted HTTP execution;
- provider-neutral OAuth2/client-credential token acquisition, cache, expiry,
  refresh, and failure handling;
- API-key and bearer-token auth where selected;
- trusted server-owned parameter injection from deployment bindings;
- structured request construction and bounded JSON response mapping;
- HTTP pagination, timeout, retry, provider/service rate limiting, circuit
  breaking, and idempotency;
- generic paged REST baseline synchronization for DATA plugins;
- deployment-specific generic webhook endpoints;
- configurable hash/HMAC verification before payload parsing;
- schema validation, event mapping, deduplication, ordering, replay protection,
  and canonical CloudEvent forwarding;
- bounded persistence for token metadata, cursors, deduplication,
  reconciliation, and audit linkage; and
- secret-free health, metrics, traces, and runtime-backed admin readback.

The implementation code and built-in defaults of these generic services may not
hard-code Auto Trader route names, field names, advertiser rules, prompts, or
business decisions. Installed plugin configuration is expected to carry those
reviewed provider-specific values.

### 7.3 Current reusable capability and gaps

`HTTP_JSON` and `PAGED_REST` below are proposed contract names, not currently
supported manifest values.

| Capability | Current state | Required change |
| --- | --- | --- |
| `ACTION` with `connector-http` | Supported | Reuse |
| Route/request templating | Supported | Keep bounded and validate compiled routes |
| Static API-key/bearer auth | Supported | Reuse where applicable |
| ActionResult normalization | Supported at MVP level | Add structured JSON-path/list/warning/cursor/evidence mapping |
| Idempotency, timeout, bounded retry | Supported at MVP level | Make durable/provider-scoped where the production contract requires it |
| OAuth2 client credentials | Not implemented in the Generic REST Connector | Add provider-neutral auth profiles with token reuse and expiry |
| Server-owned trusted parameters | Partial through trace/config | Add explicit deployment-binding injection that callers and models cannot override |
| HTTP DATA synchronization | Not supported; DATA currently supports `SQL_QUERY` and `FILE_FOLDER` | Add bounded `HTTP_JSON`/`PAGED_REST` sync connector mode |
| Inbound provider webhooks | Not supported | Add generic plugin-configured hash-authenticated webhook ingress |
| Webhook-to-CloudEvent mapping | Not supported | Add schema-bound declarative transformation and forwarding |
| Provider/service fair-usage control | Partial HTTP retry only | Add shared provider/service rate and pause policy |

These are Platform product gaps. They should be implemented once in the generic
integration substrate and then selected by Auto Trader and future REST-provider
plugins.

### 7.4 Configuration safety

The new declarative contracts must use typed fields and structured parsers. They
must not permit arbitrary JavaScript, shell commands, expression-language method
calls, unbounded JSON traversal, caller-selected URLs, caller-selected auth
profiles, or secret interpolation into model-visible results.

The compiler must fail publication or V04 validation when:

- a route host is not approved by the selected connection profile;
- an auth, secret, advertiser, or capability binding is missing;
- a caller can override a server-owned field;
- a write lacks required authority and review policy;
- a webhook verification or event schema is missing;
- response size, pagination, timeout, or rate policy is unbounded; or
- the selected deployment source does not attest the required generic connector
  capabilities.

### 7.5 Dedicated-adapter exception

A provider-specific service may be reconsidered only when a production-approved
Auto Trader capability demonstrably cannot be expressed safely through the
typed generic contracts, for example because it requires a non-HTTP protocol,
provider SDK with non-reproducible protocol behavior, binary streaming, or a
stateful transaction that cannot be bounded declaratively.

Complexity alone is not enough. The blocker, rejected generic alternative,
security boundary, ownership, lifecycle, and verification evidence must be
recorded before approving such an exception. The currently documented REST,
short-lived-token, paged baseline, and hash-authenticated webhook contracts do
not justify a standalone bridge.

### 7.6 Smallest implementation slices

The generic additions do not all have to block the first interactive proof:

1. A read-only Search/Vehicle `ACTION` plugin needs short-lived token support,
   trusted advertiser binding, bounded mapping, and rate control.
2. Stock or deal baseline support then adds paged HTTP `DATA` synchronization.
3. Near-real-time stock/deal consistency then adds verified generic webhook
   ingress and reconciliation.
4. Smart Brain consumes the same canonical events only after the event path is
   durable and verified.
5. External writes are added last through reviewed `ACTION` contributions.

This allows a real plugin-defined buyer-concierge canary without prematurely
building the complete dealer-operations surface.

## 8. Proposed Marketplace Packages

The following IDs are proposals. They become official only after manifest,
runtime, verification, documentation, and hosted evidence are complete.

| Proposed package | Type | Contribution |
| --- | --- | --- |
| `mkp-data-autotrader-stock` | `DATA` | Approved dealer stock baseline, event reconciliation, entity configuration, and freshness contract. |
| `mkp-data-autotrader-deals` | `DATA` | Restricted deal/component sync for authorized dealer staff deployments. |
| `mkp-action-autotrader-discovery` | `ACTION` | Search, advert, vehicle detail, taxonomy, equipment, MOT/history, and supported EV facts. |
| `mkp-action-autotrader-finance` | `ACTION` | Structured Search Finance reads with compliance and display boundaries. |
| `mkp-action-autotrader-vehicle-check` | `ACTION` | Restricted provenance checks and report references with fair-usage control. |
| `mkp-action-autotrader-market-intelligence` | `ACTION` | Approved valuations, vehicle metrics, retail rating, response metrics, and competitor reads. |
| `mkp-action-autotrader-deal-operations` | `ACTION` | Approved deal status, reservation, and component commands with review and receipts. |
| `mkp-action-autotrader-stock-operations` | `ACTION` | Approved stock, price, availability, media, and description commands with review. |
| `mkp-specialist-autotrader-buyer-team` | `SPECIALIST` | Exact read-only vehicle-match, evidence, and finance-explanation specialists. |
| `mkp-specialist-autotrader-forecourt-team` | `SPECIALIST` | Exact read-only stock-health, market, and listing-quality specialists. |
| `mkp-template-automotive-buyer-concierge` | `TEMPLATE` | First bounded `CONVERSATIONAL` customer-facing composition. |
| `mkp-template-dealer-sales-copilot` | `TEMPLATE` | Staff-facing conversational or genuinely multi-specialist composition. |
| `mkp-template-forecourt-intelligence` | `TEMPLATE` | Event/scheduled `SMART_BRAIN` analysis with typed results and review proposals. |
| `mkp-template-listing-operations-workbench` | `TEMPLATE` | Staff-facing governed listing improvements and approved writes. |

Packages must be capability-granular. A customer with Search approval must not
implicitly receive Deal Updates, Vehicle Check, valuations, or stock-write
authority.

## 9. Proposed Action Contract

These are stable LoomAI integration codes, not names of official Auto Trader
HTTP endpoints.

### 9.1 Read actions

| Proposed action code | Purpose | Confirmation | Important output |
| --- | --- | --- | --- |
| `autotrader_search_stock` | Search the approved dealer inventory | No | Stable stock/advert ID, vehicle summary, price, location, availability evidence, result cursor |
| `autotrader_get_stock_item` | Re-read one selected vehicle | No | Current detail, timestamp, supported attributes, deep link |
| `autotrader_compare_stock` | Fetch comparable selected records for deterministic comparison | No | Per-field values and missing-data markers; comparison prose remains LLM-generated |
| `autotrader_get_taxonomy` | Resolve make/model/generation/derivative data | No | Stable derivative identity and technical evidence |
| `autotrader_get_equipment` | Retrieve possible or factory-fitted equipment evidence | No | Explicit evidence class so possible equipment is not claimed as installed |
| `autotrader_get_vehicle_history` | Retrieve supported MOT, history, or charge-time data | No, but role/scope may be required | Source timestamp and exact evidence fields |
| `autotrader_get_vehicle_check` | Retrieve a licensed provenance check | No, but restricted and rate-limited | Check evidence, warnings, report reference, checked-at time |
| `autotrader_search_finance` | Retrieve approved finance results | No | Provider-returned structured values and required display metadata |
| `autotrader_get_valuation` | Retrieve an approved valuation | No, staff only | Valuation type, inputs, value/range, timestamp, evidence |
| `autotrader_get_vehicle_metrics` | Retrieve market, retail-rating, response, or competitor metrics | No, staff only | Metric definitions, values, scope, freshness |
| `autotrader_get_deal` | Retrieve an authorized deal and selected components | No, staff only | Trusted deal ID, status, component presence, update timestamp |

### 9.2 Write actions

| Proposed action code | Purpose | Initial governance |
| --- | --- | --- |
| `autotrader_update_deal_status` | Complete or cancel an existing deal | Human Review, current deal re-read, trusted IDs, idempotency, receipt |
| `autotrader_reserve_deal_vehicle` | Reserve an eligible vehicle against an existing deal | Explicit consumer/dealer approval, availability re-read, Human Review |
| `autotrader_remove_deal_component` | Remove approved finance or part-exchange component | Explicit confirmation, role check, current deal re-read |
| `autotrader_update_stock` | Create or update permitted stock fields | Dealer staff role, Human Review, schema validation, reconciliation |
| `autotrader_update_price` | Apply an approved price | Human Review, current price/stock re-read, bounded delta policy, receipt |
| `autotrader_update_availability` | Change approved availability state | Human Review, current state re-read, reconciliation |
| `autotrader_update_media` | Add, remove, or reorder approved media | Human Review and media ownership validation |
| `autotrader_update_description` | Apply an approved advert description | Human Review, content policy, provenance and length validation |

Dealer CRM actions such as requesting a callback, booking a test drive, or
creating a dealer-owned lead are separate customer-system `ACTION` plugins.
They must not be falsely represented as Auto Trader capabilities.

## 10. Grounding And Conversation Behavior

Auto Trader live reads are grounding-eligible actions. The runtime should:

1. identify the user's intent and missing constraints;
2. call the typed read action instead of answering from model memory;
3. pass the complete bounded normalized result as LLM facts;
4. require post-action generation grounded in those facts;
5. return structured vehicle/result data beside the generated answer; and
6. use a short deterministic summary if generation fails, never a raw JSON dump.

No product-domain text matching belongs in AI Fabric core or the generic runtime.
The action schema, action description, template prompt, entity configuration,
and response contract carry automotive semantics.

Selected targets must be carried as typed working targets:

- `advertiserId` is server-owned and resolved from verified deployment/user
  scope;
- `stockId`, `advertId`, `vehicleId`, `derivativeId`, and `dealId` come from a
  prior trusted response or a fresh validated lookup;
- a phrase such as "this car" may resolve to a trusted conversation target;
- a model-extracted registration or ID is untrusted until a plugin-defined read
  action validates it against the authoritative API; and
- any write re-reads the target before execution.

## 11. Data, Search, And Vectorization Strategy

### 11.1 Live authority

Live Search/Stock/Vehicle APIs are authoritative for:

- current availability;
- current price;
- current advert/stock state;
- current finance results;
- current deal state; and
- vehicle-specific evidence requested at decision time.

A user-visible answer must state when data was checked and must not call stale
projection data current.

### 11.2 Deployment-local projection

A normalized deployment-local projection is useful for:

- webhook reconciliation;
- efficient aggregate summaries;
- change detection;
- Smart Brain stock and performance analysis;
- idempotency and recovery; and
- bounded operational history.

The projection is derived and rebuildable. It is not the dealer or Auto Trader
system of record.

### 11.3 Vector use

Do not vectorize the complete stock feed by default. Exact filters, identifiers,
prices, mileage, availability, finance, and provenance belong in typed search and
read actions.

Optional vectorization may be useful for approved descriptive fields such as:

- advert descriptions;
- non-sensitive feature text;
- model/derivative descriptive text; and
- dealer-authored buying guides.

Before embedding any Auto Trader-derived content, confirm that the applicable
contract permits LLM processing, derivative storage, caching, retention, and
vector indexing. Dealer-owned warranty, delivery, location, and support documents
should use the separate Document Knowledge Operations capability and customer-
owned source storage.

### 11.4 Hybrid retrieval

A useful buyer query may combine three explicit evidence paths:

```text
"Which family EV under GBP 25,000 has good boot space and what warranty applies?"

typed Auto Trader stock search
  + typed vehicle/equipment facts
  + dealer-owned warranty document retrieval
  -> grounded comparison answer
```

Evidence from each source must remain distinguishable. The model must not merge a
generic dealer warranty document into a claim about a particular vehicle unless
the document and vehicle applicability rules support it.

## 12. Ready-Deployment Template Concepts

### 12.1 Automotive Buyer Concierge

- Behavior: `CONVERSATIONAL`.
- Audience: dealership website visitors.
- Core value: live stock discovery, comparison, vehicle evidence, and a clean
  dealer handoff.
- First capabilities: search, vehicle detail, taxonomy/equipment, supported
  EV/MOT evidence, dealer documents, and CRM lead/test-drive action.
- UI: docked composer or embedded chat, Max Mode for comparisons, vehicle cards,
  evidence labels, and explicit confirmation surfaces.
- Boundary: no autonomous price, stock, reservation, or deal mutations.

### 12.2 Dealer Sales Copilot

- Behavior: `CONVERSATIONAL` initially; use `AGENTIC_SPECIALIST_TEAM` only when
  at least two genuinely distinct read-only workers are justified.
- Audience: authenticated dealer sales staff.
- Core value: inspect a deal, vehicle, messages, part exchange, finance, and
  matching stock in one governed workflow.
- Candidate specialists: vehicle match, vehicle evidence, finance explanation,
  and deal summary.
- Boundary: writes use separate actions and Human Review; specialists remain
  read-only under the current contract.

### 12.3 Forecourt Intelligence

- Behavior: `SMART_BRAIN`.
- Audience: stock/pricing/operations staff.
- Core value: detect stale stock, weak listings, availability drift, anomalous
  price posture, underperforming adverts, and follow-up opportunities.
- Activation: approved stock/deal webhooks and bounded schedules.
- Output: typed finding, evidence, severity, recommendation, and optional Human
  Review task.
- Boundary: the Smart Brain does not change price, stock, media, availability,
  or deal state directly.

### 12.4 Listing Operations Workbench

- Behavior: `CONVERSATIONAL` with Human Review; Smart Brain may propose work.
- Audience: authorized listing and merchandising staff.
- Core value: review listing completeness, descriptions, images, derivative
  identity, price/metrics evidence, and proposed corrections.
- Boundary: every external mutation has current-state validation and a receipt.

## 13. Security, Privacy, And Tenant Isolation

### 13.1 Trusted scope

- The deployment owns an allowlist of integration and advertiser IDs.
- Caller-provided advertiser IDs are hints, never authority.
- Multi-site groups require an explicit advertiser-to-tenant/site mapping.
- Every action and event stores tenant, deployment, integration, advertiser,
  user/service principal, and correlation identity.
- A missing or ambiguous tenant, deployment, user, advertiser, target, role, or
  capability grant fails closed.

### 13.2 Credentials

- Auto Trader client material, access tokens, webhook secrets, and customer
  system credentials remain deployment secrets.
- Tokens are never returned to a model, browser, trace, export, or support
  bundle.
- Secret rotation and token expiry must not require a new Marketplace plugin
  version.
- Export/import carries secret references and required bindings, not values.

### 13.3 Personal and financial data

- Deal, message, call, finance, delivery, and part-exchange data is restricted to
  the minimum approved staff roles and use purpose.
- Prompt input, model output, traces, support bundles, and retention rules must
  use field-level minimization and redaction.
- Consumer chat must not expose staff-only metrics or another consumer's deal.
- Finance output must preserve the provider's structured values and approved
  wording. The model must not invent eligibility, approval, affordability, APR,
  deposit, or monthly-payment calculations.

### 13.4 Write controls

- Target identity must come from trusted state.
- Authorization is evaluated again immediately before execution.
- Current external state is re-read where conflicts matter.
- Every write uses a stable idempotency key and produces a normalized receipt.
- Partial or ambiguous upstream outcomes enter reconciliation, not optimistic
  success.
- Price, stock, media, availability, reservation, and deal writes are reviewed
  in the first release that supports them.

## 14. Licensing And Commercial Gates

Before code implementation, obtain written answers for each approved capability:

1. May the response be supplied to a third-party LLM for the stated purpose?
2. Which fields may be shown to consumers, dealer staff, or both?
3. May fields be cached, and for how long?
4. May any fields be embedded or stored as derived vectors?
5. May data be combined with dealer-owned or third-party sources?
6. What attribution, links, branding, wording, or ordering is required?
7. Which valuation, metrics, finance, provenance, and competitor fields have
   special display or redistribution restrictions?
8. What retention and deletion rules apply to deals, messages, calls, finance,
   part exchange, and consumer identifiers?
9. Are generated descriptions or modified Auto Trader data allowed to be stored
   and republished?
10. What evidence and go-live checks are required for each capability?

The [Auto Trader Connect terms](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)
must be reviewed with the exact production grant. Public documentation is not a
licence to reuse or transform data.

## 15. Integration Meeting Questions

### 15.1 Commercial and access

- Which capabilities can LoomAI receive in sandbox and production for the first
  dealer?
- Is LoomAI the integration partner, is the dealer the contracting customer, or
  is a website/DMS provider expected to sponsor the integration?
- How is each advertiser added to and removed from the integration?
- Can one integration cover a dealer group with several advertiser IDs and
  brands?
- Which capability go-live checks and review lead times apply?

### 15.2 Authentication and networking

- Confirm token endpoint, credential type, token lifetime, rotation, and
  revocation behavior.
- Confirm whether production requires IP allowlisting, mTLS, or additional
  network controls.
- Confirm per-integration and per-capability rate limits and retry guidance.
- Confirm sandbox data realism and any differences from production.

### 15.3 Search and vehicle evidence

- Which Search/Search Advert filters are available for dealer-scoped website
  experiences?
- What is the canonical deep link and stable identifier for a search result?
- Which fields support seats, boot capacity, warranty, insurance category,
  Cat N/Cat S, MOT, EV range, charge time, equipment, and factory-fitted status?
- Which fields are absent versus present-but-unknown, and how should each be
  displayed?
- Which calls are restricted by fair usage, especially Vehicle Check?

### 15.4 Stock and webhooks

- Which baseline stock endpoint and notification events are approved?
- Are webhook payloads complete resources or change notifications requiring a
  follow-up read?
- What signature/authenticity mechanism, replay window, ordering key, retry
  schedule, and timeout apply?
- How are missed events replayed or reconciled?
- What are the expected deletion, sold, reserved, and unavailable semantics?

### 15.5 Deals and writes

- Which deal components may be read and which may be updated?
- Which reservation, completion, cancellation, finance, and part-exchange
  transitions are valid?
- What idempotency and conflict behavior does each write endpoint provide?
- Which actions require proof of consumer consent?
- Can Auto Trader send or receive dealer-originated chat/messages, or should
  website leads always use the dealer CRM integration?

### 15.6 AI and data rights

- Is sending approved fields to a hosted LLM permitted?
- Is semantic indexing of approved descriptions permitted?
- Are generated summaries, comparisons, recommendations, and descriptions
  considered modified or derivative Auto Trader data?
- What attribution and source freshness must be shown in an AI response?
- May metrics or valuations be used as inputs to an AI recommendation, and what
  exact output may be shown?

### 15.7 Testing and support

- Can Auto Trader supply one realistic test advertiser with stock, deal,
  webhook, vehicle-check, finance, and error-path fixtures?
- Can call-log validation be automated from a LoomAI verification pack?
- What support and incident route exists for token, rate-limit, data-quality,
  and webhook failures?
- How are breaking changes communicated and tested before production rollout?

## 16. Implementation Sequence

### Phase 0: Contract and capability approval

- Select one design-partner dealer and exact advertiser IDs.
- Obtain sandbox credentials and written capability scope.
- Resolve LLM, cache, vector, display, retention, attribution, and data-sharing
  rights.
- Map Auto Trader go-live checks to LoomAI verification IDs.
- Freeze the first release scope; do not implement speculative APIs.

Exit: approved capability matrix, data classification, and test advertiser.

### Phase 1: Generic outbound action substrate

- Extend the existing Generic REST Connector with provider-neutral OAuth2
  client-credential profiles, token reuse/expiry, trusted deployment bindings,
  structured response mapping, and provider/service rate controls.
- Extend the Marketplace compiler, V04 validation, capability attestation,
  export/import, and runtime-backed operations readback for those outbound
  connector capabilities.
- Add generic connector tests that use neutral fixture providers, not Auto
  Trader names or response fields.
- Prove restart, token expiry/refresh, rate-limit, timeout, mapping, and
  fail-closed behavior independently of the automotive solution.

Exit: a neutral test ACTION plugin can authenticate to a REST API, reuse and
refresh a short-lived token, inject trusted deployment bindings, and normalize a
bounded read without provider-specific service code.

### Phase 2: Read-only Automotive Buyer Concierge

- Publish the discovery ACTION package and first exact `CONVERSATIONAL`
  template. Do not make stock replication a prerequisite for live search.
- Express Auto Trader routes, schemas, response mappings, advertiser bindings,
  and capability requirements entirely in the reviewed plugins.
- Implement live search, detail re-read, taxonomy/equipment, and approved
  MOT/vehicle evidence.
- Add structured vehicle list, detail, comparison, missing-data, and source-
  freshness response contracts.
- Enforce post-action generation from complete bounded action facts.
- Integrate a separate dealer CRM handoff action where available.

Exit: a newly created deployment passes live multi-turn search, compare,
evidence, stale-data, and two-dealer isolation canaries.

### Phase 3: Generic sync/event substrate and stock freshness

- Extend `DATA` contributions with bounded paged HTTP synchronization.
- Add generic deployment-local hash-authenticated webhook ingress and
  schema-bound CloudEvent mapping.
- Extend Marketplace compilation, V04 validation, source capability
  attestation, export/import, and runtime-backed operations for these data/event
  contracts.
- Prove the generic sync and webhook contracts with neutral fixture providers
  before adding Auto Trader mappings.
- Publish the stock DATA package and configure its baseline and
  deployment-specific webhook mapping.
- Add deduplication, ordering, replay, reconciliation, and lag operations.
- Build the deployment-local normalized projection.
- Prove baseline/event convergence, missed-event repair, delete/sold behavior,
  restart recovery, and bounded retention.

Exit: projection converges with the authoritative API and never overrides a
fresh live availability/price read.

### Phase 4: Dealer Sales Copilot

- Add restricted deal sync and component reads.
- Add exact staff roles, data minimization, and retention.
- Use `AGENTIC_SPECIALIST_TEAM` only if distinct workers improve a proven flow.
- Add Human Review for supported deal commands.
- Prove current-state revalidation, idempotency, ambiguous-outcome
  reconciliation, and audit receipts.

Exit: authorized staff can inspect and safely progress one approved deal flow;
consumer or cross-dealer access fails closed.

### Phase 5: Forecourt Intelligence

- Add approved metrics/valuation DATA and ACTION packages.
- Publish a bounded `SMART_BRAIN` template with exact triggers and schemas.
- Produce typed findings and Human Review proposals only.
- Add cost, frequency, quota, drift, and false-positive evaluation.

Exit: event and schedule canaries produce reproducible evidence-backed findings
without an external write.

### Phase 6: Controlled listing and inventory writes

- Add only the write capabilities granted and contractually approved.
- Implement stock, price, availability, media, or description commands one at a
  time.
- Require role/target/current-state checks, review, idempotency, receipt, and
  reconciliation.
- Pass the corresponding Auto Trader capability go-live checks.

Exit: each exact write action is independently production-approved and
verification-backed.

## 17. Verification And Release Gates

The Auto Trader verification pack must include:

### 17.1 Static and source gates

- exact Marketplace manifest and dependency validation;
- route and schema allowlist validation;
- no secret values in manifests, exports, images, logs, or support bundles;
- source/image attestation for runtime and Generic REST Connector;
- Central-only AI Fabric dependency verification; and
- no Auto Trader product semantics in AI Fabric core or generic deployment
  services.

### 17.2 Generic connector, sync, and webhook gates

- token reuse and refresh at expiry;
- invalid/revoked credential failure;
- advertiser membership and cross-advertiser denial;
- pagination and rate-limit compliance;
- timeout, retry, and non-retryable error classification;
- webhook authenticity, replay, duplicate, out-of-order, malformed, and unknown
  event handling;
- restart and reconciliation recovery; and
- bounded/log-safe response normalization.

### 17.3 Conversational gates

- live search with supported filters;
- zero, one, and many-result behavior;
- follow-up target resolution such as "this vehicle";
- detail and availability revalidation;
- unsupported-filter honesty;
- vehicle comparison with missing-field handling;
- evidence answer with checked-at/source metadata;
- post-action generation grounded only in returned facts;
- deterministic non-JSON fallback when generation fails; and
- no result from another tenant, deployment, advertiser, or conversation.

### 17.4 Write and review gates

- untrusted or invented IDs fail;
- unauthorized roles fail;
- expired review and stale state fail;
- duplicate confirmation does not duplicate the write;
- conflicting upstream state enters reconciliation;
- successful action returns an immutable normalized receipt; and
- cancellation, rejection, retry, replay, and restart preserve correct state.

### 17.5 Lifecycle gates

- clean-room template install and V04 release;
- staging to production promotion with explicit production resource binding;
- export/import without secrets, tokens, PII, or disallowed cached data;
- rollback, restart, scaling, draining, and decommission;
- token/secret rotation;
- dealer offboarding and data deletion; and
- full Platform release-readiness plus applicable Auto Trader go-live checks.

No template is `MARKET_READY` because a chat response looks good. The exact
immutable composition must pass all applicable gates with real providers and
approved Auto Trader capabilities.

## 18. Operational Model

The deployment workspace should expose safe, customer-appropriate operations:

- approved capability grants and their verification status;
- advertiser bindings without credential material;
- Generic REST Connector and runtime health;
- token-expiry/refresh posture without token values;
- baseline status, cursor, last successful reconciliation, and lag;
- webhook expected/received/rejected/duplicate/replayed counts;
- rate-limit and fair-usage posture;
- read/write action success, error, latency, and reconciliation counts;
- Smart Brain operation status and review queue;
- source freshness shown to users;
- retention/deletion status; and
- support export with secrets and personal data removed.

Alert conditions should include authentication failure, advertiser-scope drift,
webhook lag, reconciliation mismatch, sustained rate limiting, stale stock,
write ambiguity, dead-letter growth, and cross-boundary denial anomalies.

## 19. API Change Readiness

Auto Trader states that integrations must handle non-breaking evolution and that
breaking changes are communicated in advance. LoomAI should therefore:

- use tolerant structured deserialization for unknown response fields;
- keep required-field assertions narrow and contract-tested;
- preserve upstream warnings and partial-result evidence;
- maintain sandbox contract fixtures for each granted capability;
- run scheduled compatibility canaries before connector/template promotion;
- make capability/schema drift visible in deployment verification;
- fail closed when a changed response can alter identity, price, availability,
  finance, provenance, or write semantics; and
- subscribe an owned integration mailbox to Auto Trader change notices.

The prior documentation review identified a pending Vehicles API response-shape
transition and field/warning changes targeted for late 2026. The integration
team must confirm the exact current notice and effective date with Auto Trader
before implementation. Do not encode an unverified transitional dual-parser in
the product merely for compatibility; implement the current production contract
selected for go-live and test it explicitly.

## 20. First Release Recommendation

The smallest marketable, defensible release is:

```text
One UK dealership or dealer group
+ one exact production-approved Auto Trader integration
+ Auto Trader Marketplace plugins
+ existing deployment-local Generic REST Connector with attested generic capabilities
+ live dealer-scoped stock search
+ vehicle detail and supported taxonomy/equipment/EV/MOT evidence
+ dealer-owned warranty/support document retrieval
+ structured vehicle list/detail/comparison UI
+ governed dealer CRM callback or test-drive handoff
+ no Auto Trader writes
+ full tenant/deployment/advertiser isolation and go-live verification
```

This already solves the visible market problem: the assistant can discover what
the dealer actually has, answer from current evidence, maintain the selected
vehicle across turns, explain uncertainty, and hand a qualified request to the
right dealer workflow.

Valuations, market intelligence, Deal Sync, agentic staff workflows, Smart Brain
analysis, and reviewed writes should then be added as independently granted and
verified capability packs.

## 21. Definition Of Done

This initiative is complete only when:

1. Auto Trader approves the exact first production capabilities and data use.
2. The provider-neutral connector, HTTP sync, and webhook gaps are implemented,
   tested, source-attested, and operationally observable without Auto Trader
   domain code.
3. Exact Auto Trader Marketplace packages and the first template are published.
4. A clean tenant can create and release the deployment through V04 without
   manual configuration repair.
5. Browser/customer traffic and Auto Trader traffic do not depend on the central
   Platform request path.
6. Live reads return normalized evidence and grounded generated answers.
7. Two-tenant, two-deployment, and two-advertiser isolation tests pass.
8. Token, webhook, rate-limit, restart, replay, reconciliation, and stale-data
   tests pass.
9. All applicable Auto Trader capability go-live checks pass.
10. Platform staging and production release gates pass for the exact immutable
    composition.
11. Customer integration, operator, privacy, support, and offboarding guides are
    published.
12. Only the capabilities proven by evidence are marketed.

## 22. Official Research Sources

Primary sources used for this analysis:

- [Auto Trader Connect Developer API directory](https://developers.autotrader.co.uk/api#introduction)
- [Integration Fundamentals](https://help.autotrader.co.uk/hc/en-gb/articles/21791620456221-Integration-Fundamentals)
- [Integration Fundamentals Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22645899163933-Go-Live-checks-for-Integration-Fundamentals)
- [Search](https://help.autotrader.co.uk/hc/en-gb/articles/21946045692445-Introduction-to-Search)
- [Search Adverts](https://help.autotrader.co.uk/hc/en-gb/articles/21945940067229-Introduction-to-Search-Adverts)
- [Search Finance](https://help.autotrader.co.uk/hc/en-gb/articles/21946004293021-Introduction-to-Search-Finance)
- [Stock Sync](https://help.autotrader.co.uk/hc/en-gb/articles/21846314775453-Introduction-to-Stock-Sync)
- [Stock Sync Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673947111325-Go-Live-checks-for-Stock-Sync)
- [Stock Updates](https://help.autotrader.co.uk/hc/en-gb/articles/21847112099101-Introduction-to-Stock-Updates)
- [Availability Updates](https://help.autotrader.co.uk/hc/en-gb/articles/21846548407069-Introduction-to-Availability-Updates)
- [Price Updates](https://help.autotrader.co.uk/hc/en-gb/articles/21846785854237-Introduction-to-Price-Updates)
- [Media Updates](https://help.autotrader.co.uk/hc/en-gb/articles/21846827095325-Introduction-to-Media-Updates)
- [Vehicle Taxonomy](https://help.autotrader.co.uk/hc/en-gb/articles/21791924757789-Introduction-to-Vehicle-Taxonomy)
- [Vehicle Equipment](https://help.autotrader.co.uk/hc/en-gb/articles/21792637172125-Introduction-to-Vehicle-Equipment)
- [Vehicle Check](https://help.autotrader.co.uk/hc/en-gb/articles/21872506361757-Introduction-to-Vehicle-Check)
- [Current Valuations](https://help.autotrader.co.uk/hc/en-gb/articles/21923133513117-Introduction-to-Current-Valuations)
- [Vehicle Metrics](https://help.autotrader.co.uk/hc/en-gb/articles/21946149296029-Introduction-to-Vehicle-Metrics)
- [Retail Rating](https://help.autotrader.co.uk/hc/en-gb/articles/21945900805405-Introduction-to-Retail-Rating)
- [Response Metrics](https://help.autotrader.co.uk/hc/en-gb/articles/21871963006237-Introduction-to-Response-Metrics)
- [Deal Sync](https://help.autotrader.co.uk/hc/en-gb/articles/21944941459485-Introduction-to-Deal-Sync)
- [Deal Updates](https://help.autotrader.co.uk/hc/en-gb/articles/21945536021277-Introduction-to-Deal-Updates)
- [Message Updates](https://help.autotrader.co.uk/hc/en-gb/articles/21945712571165-Introduction-to-Message-Updates)
- [Part Exchange Updates](https://help.autotrader.co.uk/hc/en-gb/articles/21945772657437-Introduction-to-Part-Exchange-Updates)
- [Co-Driver Generated Descriptions](https://help.autotrader.co.uk/hc/en-gb/articles/23707472935581-Introduction-to-Co-Driver-Generated-Descriptions)
- [Co-Driver Smart Ordered Imagery](https://help.autotrader.co.uk/hc/en-gb/articles/23707360911901-Introduction-to-Co-Driver-Smart-Ordered-Imagery)
- [Capability Go-Live Checks](https://help.autotrader.co.uk/hc/en-gb/sections/22825511475741-Capability-Go-Live-checks)
- [Auto Trader Connect Terms](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)

## 23. Final Product Position

LoomAI should present this as an **AI enablement layer for automotive retail**,
not as another generic chatbot and not as a replacement for Auto Trader.

Auto Trader supplies approved authoritative automotive capabilities. The dealer
supplies identity, policy, documents, CRM/DMS workflows, and final business
authority. LoomAI composes those systems into deployment-local conversational,
specialist, and event-driven behavior with grounding, governance, review,
isolation, and operational proof. Auto Trader semantics remain in Marketplace
plugins; reusable deployment services execute their bounded generic transport
contracts.

That combination is the defensible product.
