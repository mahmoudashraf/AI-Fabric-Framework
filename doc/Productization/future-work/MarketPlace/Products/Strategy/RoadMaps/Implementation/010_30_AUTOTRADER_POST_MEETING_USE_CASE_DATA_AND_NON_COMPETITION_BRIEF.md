# 010.30 Auto Trader Post-Meeting Use Case, Data And Non-Competition Brief

- **Status:** Proposed post-meeting alignment; no Auto Trader sandbox,
  production access, endorsement, or integration-readiness claim
- **Date:** 2026-10-07
- **Meeting reflected:** 2026-10-06 Auto Trader discussion
- **Audience:** Auto Trader partnership, product, data-governance and Connect
  integration teams; LoomAI leadership and engineering
- **Immediate objective:** Agree one bounded read-mostly use case, the correct
  Search/Stock capabilities, one customer-confirmed Auto Trader Deal creation
  action, the permitted AI-processing boundary, and a named pilot retailer
- **Commercial position:** LoomAI should complement an Auto Trader advertiser
  package, not create a competing marketplace, stock authority, lead platform,
  or dealer relationship

Related plans:

- [010.25 Auto Trader Connect LoomAI Capability Productization Analysis](010_25_AUTOTRADER_CONNECT_LOOMAI_CAPABILITY_PRODUCTIZATION_ANALYSIS.md)
- [010.26 Auto Trader Dealership First Release And Meeting Demo Plan](010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md)
- [010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan](010_27_AUTOTRADER_INTEGRATION_PLATFORM_READINESS_CHANGE_AND_EVIDENCE_PLAN.md)
- [010.28 Auto Trader Partnership Position And Discovery Questions](010_28_AUTOTRADER_PARTNERSHIP_POSITION_AND_DISCOVERY_QUESTIONS.md)

Official references reviewed for this brief:

- [Auto Trader Connect developer API](https://developers.autotrader.co.uk/api)
- [Auto Trader API authentication](https://developers.autotrader.co.uk/api#authentication)
- [Auto Trader Stock API](https://developers.autotrader.co.uk/api#stock-api)
- [Auto Trader Search API](https://developers.autotrader.co.uk/api#search-api)
- [Auto Trader notification webhook](https://developers.autotrader.co.uk/api#notifications-webhook)
- [Auto Trader create-a-deal contract](https://developers.autotrader.co.uk/api#create-a-deal)
- [Auto Trader 19 November 2025 API release note](https://developers.autotrader.co.uk/api#19th-november-2025)
- [Auto Trader Connect advertiser business rules](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)
- [Add a retailer to an Auto Trader Connect integration](https://www.autotrader.co.uk/partners/retailer/platform/autotrader-connect/add-to-an-integration)

---

## 1. Executive Answer

The meeting feedback is reasonable and useful. Auto Trader did not merely ask
whether LoomAI can call an API. It asked whether the proposed use is permitted,
commercially complementary, technically controlled, and attached to a real
advertiser.

LoomAI should answer with a smaller and clearer first proposition:

> Use one participating retailer's permitted Auto Trader stock data to power a
> natural-language vehicle discovery and comparison experience on that
> retailer's own website. After the customer selects a vehicle and explicitly
> confirms their details, the same governed deployment creates an externally
> originated Auto Trader Deal. Auto Trader remains the data, product,
> advertiser-package and deal-system authority. LoomAI creates no parallel lead
> database and performs no other stock, deal-state, reservation, finance or
> message write.

This is materially different from asking Auto Trader to let an autonomous
agent browse its APIs:

- the model never receives a key, secret, token, API host or advertiser scope;
- the model cannot construct an arbitrary URL or choose another retailer;
- a fixed deployment-local connector owns authentication and API execution;
- server-owned policy limits the connector to reviewed routes and fields;
- returned facts may reach the model only through an approved projection; and
- Auto Trader must still expressly approve any LLM processing, embedding,
  retention and display of its licensed data.

Governed execution answers the API-authority concern. It does **not** replace
the need for data-use permission. Both controls are required.

---

## 2. Direct Response To The Meeting Questions

| Auto Trader question or concern | LoomAI answer |
| --- | --- |
| What exact use case needs the API? | Dealer-scoped, consumer-facing stock discovery and comparison on one participating retailer's own site. A customer describes needs, receives a current shortlist from that retailer's permitted stock, and sees evidence-backed explanations. |
| What did the simulator call? | A synthetic public-document-informed rehearsal of `POST /authenticate`, paginated `GET /stock`, signed stock-notification `PUT`, targeted `GET /stock?...&stockId=...`, and provider-hosted image references. It did not call Auto Trader and is not a sandbox. |
| What authentication was simulated? | Form-encoded `key` and `secret` exchange for a short-lived bearer token, followed by `Authorization: Bearer <token>`. Webhooks use the documented timestamp plus raw-body HMAC signature shape. All values are synthetic. |
| What data is needed? | A minimal allowlist: stock identity, freshness/lifecycle, published status, make/model/derivative/year, fuel/body/transmission, mileage, displayed price, selected features, and approved image references. Advertiser identity is used only to enforce scope. |
| What will LoomAI do with it? | Filter and rank one dealer's stock, explain a bounded shortlist, cite source/freshness, and remove sold/deleted/unpublished records. Long-lived indexing is optional and must be separately approved; a query-time mode can avoid a persistent Auto Trader vector copy. |
| Is LoomAI creating or managing leads? | The first pilot creates exactly one externally originated Auto Trader Deal after explicit customer confirmation. Auto Trader remains the system of record. The current fictional dealership callback/test-drive inbox is demo-only and must be removed from the real pilot composition rather than operated in parallel. |
| Does AI call Auto Trader directly? | No. The model may produce a typed intent or propose an allowlisted action. Deterministic code validates it and a fixed connector executes the exact granted operation using server-owned scope and credentials. |
| Which dealer is participating? | None yet. Northfield is fictional and Paul Rigby is not represented as a LoomAI customer or pilot. LoomAI is at pre-design-partner stage and needs either an Auto Trader-nominated retailer or a retailer authorization route. |

---

## 3. The Proposed First Use Case

### 3.1 Use-case statement

**Auto Trader-authorized guided stock discovery for one retailer website**

A visitor to a participating retailer's website can ask questions such as:

- "Show me this dealer's electric family cars under GBP 35,000."
- "Which of these two vehicles better fits frequent motorway travel?"
- "Explain the trade-offs using current vehicle facts."
- "Is this vehicle still published and what is its current displayed price?"

The experience may combine two clearly attributed sources:

1. Auto Trader-authorized stock facts for that retailer; and
2. retailer-owned documents such as warranty, test-drive and showroom policy.

### 3.2 First-pilot boundaries

The first pilot should include:

- one named retailer and one authorized advertiser identifier;
- one isolated LoomAI deployment;
- read-only Search and/or Stock Sync capabilities selected by Auto Trader;
- the separately granted `Deal Updates` capability for `POST /deals`;
- natural-language requirements converted to validated structured filters;
- bounded comparison and explanation of returned vehicles;
- source and freshness evidence;
- retailer-owned policy documents as a separate knowledge source;
- one customer-confirmed Auto Trader Deal creation with an Auto Trader
  `dealId` receipt; and
- objective quality, isolation, freshness and latency gates.

The first pilot should exclude:

- a LoomAI or dealership-owned parallel lead inbox;
- callback, test-drive or CRM writes outside the approved Auto Trader Deal
  handoff;
- Auto Trader Deal reservation, completion, cancellation, component or message
  updates after creation;
- stock, price, advert, reservation or availability writes;
- messages, finance applications, part exchange or valuations;
- whole-market or cross-retailer search;
- model training on Auto Trader data;
- autonomous web/API browsing; and
- persistent vectorization unless Auto Trader approves it in writing.

This scope is intentionally valuable but non-competitive. It improves the
retailer's on-site use of approved Auto Trader products and hands the converted
customer back into Auto Trader's deal boundary without recreating Auto Trader's
marketplace, lead database or downstream conversion stack.

---

## 4. What The Current Simulator Actually Does

### 4.1 Evidence boundary

The simulator is a LoomAI verification dependency implemented under:

- `verification-support/external-vehicle-provider-simulator`
- `verification-support/external-vehicle-provider-simulator/fixtures/marketplace/autotrader-contract-data.json`
- `product-demos/autotrader-dealership-demo/backend/deployment/platform/staging-profile.json`

It uses fictional vehicles and credentials. It was built from public Auto
Trader documentation to test LoomAI's generic connector mechanics. It is not:

- an Auto Trader environment;
- an exact complete emulator;
- evidence of granted capabilities;
- evidence of data rights;
- certification; or
- a production dependency.

### 4.2 Simulated request sequence

| Step | Synthetic request | Authority and purpose | Current response use |
| --- | --- | --- | --- |
| 1. Authenticate | `POST /authenticate` with form fields `key` and `secret` | Deployment-owned connector only; obtain a bearer token | Read `access_token` and `expires_at`; cache and refresh with expiry skew |
| 2. Baseline | `GET /stock?advertiserId=<fixed>&lifecycleState=FORECOURT&page=1&pageSize=200` | Fixed protected advertiser; reconcile the complete currently published source every 900 seconds | Page through results, validate advertiser, map allowlisted fields, upsert current records, delete absent records |
| 3. Receive event | HTTPS `PUT` to the deployment connector with `AutoTrader-Signature: t=<epoch>,v1=<hmac>` | Verify raw-body HMAC, replay window, event identity, advertiser and event type | Treat event as a change signal, not as trusted index content |
| 4. Refresh one record | `GET /stock?advertiserId=<fixed>&stockId=<event stockId>&page=1&pageSize=1` | Re-read latest provider state after a valid notification | Upsert current published record or delete sold/deleted/unpublished record |
| 5. Resolve media | Use the first approved `media.images[].href` | Present a provider-hosted synthetic image | Store only approved image identity/reference metadata |

The simulator's protected `/internal/control/**` endpoints mutate fictional
state and emit test events. They use `X-Simulator-Control-Key` and are operator
test controls only. They are not Auto Trader API requests and would not exist in
a production integration.

### 4.3 Current simulator response surface

The synthetic `/stock` response contains a reduced public-document-shaped
record with these sections:

- `vehicle`: ownership condition, synthetic registration and VIN, make, model,
  derivative, vehicle type, body type, fuel type, transmission, mileage, first
  registration date, year, and standard taxonomy values;
- `advertiser`: synthetic `advertiserId`;
- `adverts`: reservation state, supplied/total price, attention grabber,
  description, and publication status;
- `metadata`: `stockId`, `searchId`, timestamps, version, lifecycle and
  forecourt date;
- `features`: two synthetic feature entries; and
- `media`: one synthetic image identity/reference and empty video/spin values.

The connector does not persist or index that entire response. It currently
selects the following projection:

| Category | Persisted/indexed field | Purpose |
| --- | --- | --- |
| Scope | `advertiserId` | Validate the fixed retailer boundary; not model-selected |
| Identity | `stockId`, `searchId` | Stable record identity and current-record reconciliation |
| Freshness | `lastUpdated`, `versionNumber`, `lifecycleState`, published advert status | Convergence, staleness and removal decisions |
| Vehicle | make, model, derivative, year, fuel type, body type, transmission, mileage | Search, comparison and grounded explanation |
| Commercial | displayed total price in GBP | Current shortlist/display fact |
| Descriptive | selected features | Bounded suitability explanation |
| Media | primary `imageId` and HTTPS `href` | Safe vehicle presentation through an allowlisted media host |

The current projection deliberately drops synthetic registration, VIN,
supplied price, free-text advert description, attention grabber, reservation
state and other response values. It does not request or map customer identity,
deals, messages, finance applications, part-exchange data, valuations, response
metrics or cross-retailer data.

This exact table describes the demonstration, not a requested entitlement. A
real package must be reduced to the field allowlist Auto Trader approves.

---

## 5. Important API-Fit Correction

The current simulator rehearses `/stock` because the engineering goal was to
prove full baseline plus notification-driven current-record reconciliation.
Auto Trader's current documentation describes:

- **Stock Sync** as access to stock information and real-time Stock
  Notifications; and
- **Search** as interactive list, filter and sort of adverts for consumer-facing
  applications.

Therefore LoomAI must not assume that Stock Sync alone is the correct grant for
the customer-facing assistant. Auto Trader should decide which of these shapes
is valid:

### Option A: Search-first, query-time pilot

1. The model converts the customer's words into a typed search request.
2. Deterministic policy validates the allowed filters.
3. The connector calls the granted Search API for the fixed advertiser.
4. A small approved response projection is used transiently to rank and explain
   results.
5. Only retailer-owned policy documents are persistently vectorized.

This is the lowest-risk response to concerns about copying licensed stock into
an AI index.

### Option B: Approved synchronized projection

1. Stock Sync plus notifications maintains one retailer's approved stock
   projection.
2. Only a written field allowlist is persisted and, if permitted, embedded.
3. Sold, deleted and unpublished records are removed promptly.
4. A periodic full reconciliation repairs missed events.
5. Current decision facts are re-read through the granted API when required.

This supports richer semantic discovery but needs explicit agreement on
embedding, model processing, retention, deletion, branding and audit.

### Recommended ask

Ask Auto Trader to approve Option A for the first pilot and evaluate Option B
as a separately documented data-rights extension. Do not silently treat a
Search grant as a Stock Sync grant, or vice versa.

---

## 6. What Happens To The Data

### 6.1 Proposed processing purpose

The approved fields are used only to:

- search and filter the participating retailer's published stock;
- create a temporary shortlist for the current visitor;
- compare selected vehicles;
- explain why a vehicle may fit stated preferences;
- present current source and freshness evidence; and
- remove or suppress records that are no longer available or permitted.

They are not used to:

- train a general or customer-specific foundation model;
- build a cross-retailer dataset;
- benchmark or price retailers against each other;
- resell data;
- create an independent vehicle marketplace;
- make autonomous finance, eligibility or regulated decisions; or
- infer hidden personal characteristics.

### 6.2 Processing modes that need separate decisions

| Processing operation | First-pilot default | Auto Trader decision required |
| --- | --- | --- |
| Server-side API call for one fixed advertiser | Required | Correct capability and advertiser authorization |
| Transient facts in an LLM prompt | Proposed, bounded | Approved fields, model provider, region, retention/no-training terms |
| Persistent typed stock projection | Off in Search-first mode | Whether permitted, fields, encryption, retention, deletion and audit |
| Vector embeddings of stock facts | Off by default | Explicit written permission and approved embedding provider/model |
| Retailer-owned policy-document embeddings | Allowed by retailer contract, separate source | No Auto Trader data may be mixed into this source without approval |
| Consumer conversation history | Minimized and deployment-local | Privacy roles, notice, consent, retention and deletion |
| Derived AI explanation | Display only with attribution and qualification | Branding and modified/derived-data presentation rules |

### 6.3 Deployment isolation

Each participating retailer receives a separate LoomAI deployment. The
advertiser identifier, credentials, route definitions and field projection are
server-owned configuration. Customer traffic goes directly to that deployment,
not through one shared LoomAI chat gateway.

This architecture reduces blast radius and cross-retailer exposure, but it does
not change the licence. Technical isolation is a control, not permission.

---

## 7. Why The Model Does Not Have API Authority

The intended execution path is:

```text
Customer request
  -> typed intent or allowlisted action proposal
  -> deterministic schema and policy validation
  -> fixed dealer-scoped connector route
  -> server-owned token and advertiser injection
  -> Auto Trader API
  -> approved response projection
  -> evidence-backed answer
```

The model is permitted to reason about customer language. It is not permitted
to own transport authority.

The following values are never supplied by the model or browser:

- API host;
- endpoint path;
- HTTP method;
- key, secret or bearer token;
- advertiser identifier;
- webhook secret;
- capability grant; or
- unrestricted request/response fields.

The connector additionally enforces advertiser scope, route allowlists,
timeouts, retry/rate policy, response limits and source projection. Read actions
can execute only inside that boundary. The first-pilot Deal creation uses its
own separately granted route, typed payload, application authorization,
duplicate-submission protection and explicit customer confirmation.

This means the proposal is **AI-assisted selection over governed API
capabilities**, not direct agent access to Auto Trader.

---

## 8. Lead And Deal Non-Competition Boundary

### 8.1 Honest description of the current demo

The fictional Northfield demo contains `dealership_request_callback` and
`dealership_request_test_drive`. After confirmation, they write encrypted
contact details to a fictional dealership-owned review inbox. They do not call
Auto Trader.

That proves LoomAI's generic governed-action mechanics. It must not be described
to Auto Trader as the proposed lead architecture.

### 8.2 First pilot decision

The first pilot must use Auto Trader Deal creation as its only durable customer
handoff:

```http
POST /deals?advertiserId=<trusted-advertiser-id>
Authorization: Bearer <server-owned-integration-token>
Content-Type: application/json

{
  "consumer": {
    "firstName": "<confirmed-first-name>",
    "lastName": "<confirmed-last-name>",
    "email": "<confirmed-email>"
  },
  "stockId": "<trusted-selected-stock-id>",
  "advertiserId": "<trusted-advertiser-id>"
}
```

The expected provider receipt is the returned Auto Trader `dealId`. LoomAI
stores only bounded execution/audit evidence and that external identifier. It
does not create a second lead record or staff inbox.

The customer journey is:

1. Search, compare and select one current vehicle.
2. Revalidate that the trusted stock target still belongs to the deployment's
   advertiser and is eligible for the handoff.
3. Collect first name, last name and email through typed fields.
4. Show the exact vehicle, customer details, destination and privacy wording.
5. Require an explicit `Confirm and create Auto Trader Deal` decision.
6. Execute the fixed server-side action using the deployment's protected
   advertiser and `Deal Updates` grant.
7. Return a safe receipt containing the Auto Trader `dealId` and next step.

The model may collect and structure user input, but it cannot supply the API
host, method, advertiser, stock authority, credentials or arbitrary payload.

### 8.3 Deal-creation constraints and blockers

Deal creation is part of the pilot product definition, but remains blocked
until Auto Trader grants and validates it. The pilot cannot be marked ready
until all of these are resolved:

- `Deal Updates` is granted for the named integration and advertiser;
- Auto Trader confirms that an AI-assisted retailer-site expression of interest
  is an acceptable externally originated Deal;
- required consent, privacy notice, attribution and retention language is
  approved;
- retry/idempotency guidance is known, because the public create contract does
  not document an idempotency key;
- an ambiguous timeout cannot cause an automatic duplicate Deal;
- the exact sandbox and production go-live evidence is known; and
- the separately granted read capability still proves current stock ownership
  immediately before submission.

The public create contract does not accept a general message component and does
not document phone number, preferred test-drive date or free-form enquiry notes
in the create request. The first pilot must not hide those values in another
field. They stay out of the provider request unless Auto Trader supplies an
approved companion contract.

Reservation, completion, cancellation, part exchange, finance, message and
post-creation lifecycle management remain outside the first pilot. Auto Trader
and the retailer own the created Deal after the receipt is returned.

---

## 9. The Retailer Question

Auto Trader was right to ask which retailer is involved. Its public integration
onboarding asks for a Dealer ID, retailer name and retailer authorization.

The accurate answer is:

> LoomAI does not yet represent a participating Auto Trader retailer. Northfield
> is a fictional demonstration. We built it to validate the technical and user
> experience before asking a real retailer to authorize data access. We are now
> seeking the correct route to a design partner and will not represent any
> retailer without written authorization.

Three acceptable next paths are:

1. Auto Trader nominates a suitable pilot retailer.
2. LoomAI recruits a retailer, obtains written authorization and provides the
   Dealer ID through the normal integration process.
3. Auto Trader permits a technical evaluation against an Auto Trader-owned test
   advertiser before a retailer pilot is selected.

LoomAI should not use Paul Rigby, or any other real dealer, as a claimed design
partner until that dealer has agreed in writing.

---

## 10. Partnership And Commercial Position

The strongest long-term product is not a separate LoomAI lead product. It is an
optional **Auto Trader advertiser AI experience, powered by LoomAI**.

### Auto Trader remains responsible for

- advertiser eligibility and retailer relationship;
- capability grants and data rights;
- API and product authority;
- branding and display requirements;
- approved customer-journey and lead/deal destination;
- package design and commercial rules; and
- production validation.

### LoomAI provides

- one isolated deployment per retailer;
- governed natural-language orchestration;
- reusable integration, template and UI packages;
- retailer-policy knowledge as a separately attributed source;
- field-level projection and source freshness evidence;
- fixed API execution boundaries;
- deployment verification, observability and rollback; and
- a configurable embedded assistant/Max Mode experience.

### Retailer provides

- explicit authorization;
- retailer-owned policy and operational content;
- customer-facing privacy/consent notices;
- business authorization and staff ownership; and
- the final human service and sale.

This shape can later be distributed through an Auto Trader advertiser package,
co-sell arrangement or white-label experience. Auto Trader keeps the automotive
product and data relationship; LoomAI supplies the governed AI enablement and
deployment layer.

---

## 11. Decisions Requested From Auto Trader

The next conversation should ask for explicit decisions, not broad API access:

1. Is dealer-site guided stock discovery an acceptable Auto Trader Connect use
   case?
2. Is the correct first capability Search, Stock Sync, both, or another product?
3. May a bounded allowlist of returned stock facts be sent transiently to an
   approved LLM for ranking and explanation?
4. If yes, what model-provider, region, no-training, retention and audit terms
   apply?
5. May any selected stock fields be persisted or embedded for dealer-scoped
   semantic retrieval? If yes, which fields and for how long?
6. What deletion/freshness obligations apply to sold, unpublished, deleted or
   de-authorized stock?
7. What attribution, branding and derived-answer language is required?
8. Will Auto Trader grant `Deal Updates` and approve `POST /deals` as the first
   pilot's customer-confirmed expression-of-interest handoff?
9. Can Auto Trader provide a test advertiser and sandbox credentials before a
   named retailer, or is retailer authorization a prerequisite?
10. Can Auto Trader nominate a design-partner retailer, or should LoomAI bring
    one through the published add-to-integration flow?
11. What duplicate-prevention and ambiguous-timeout procedure is required for
    Deal creation?
12. What call-log, security, demonstration and go-live evidence will be needed?
13. Who owns the product, commercial, Connect integration and data-governance
    decisions?

---

## 12. Recommended Follow-Up Message

**Subject: LoomAI x Auto Trader - bounded use case, data flow and proposed next step**

> Thank you for the direct questions in our meeting. They helped us tighten the
> proposition.
>
> We are not asking to give an AI model direct access to Auto Trader APIs, and
> we are not proposing a competing lead-management product. Our proposed first
> use case is one participating retailer, one authorized advertiser and one
> governed LoomAI deployment on that retailer's own website. A customer can
> describe what they need, search and compare that retailer's permitted stock,
> receive an explanation grounded in an approved subset of current vehicle
> facts, and explicitly confirm creation of an externally originated Auto
> Trader Deal for the selected vehicle.
>
> The model never receives Auto Trader credentials, host, advertiser scope or
> arbitrary HTTP access. A deterministic connector owns authentication, fixed
> routes, advertiser binding, field projection and policy. We recognize that
> this security boundary does not itself grant data-processing rights, so we
> would agree separately which fields may be used transiently by an approved
> model and whether any dealer-scoped persistence or embedding is permitted.
>
> Our current demonstration uses only fictional records and a simulator built
> from the public authentication, Stock API and stock-notification shapes. It
> rehearses token exchange, paginated stock reads, signed change events and
> targeted record refresh. It is not an Auto Trader sandbox or production
> connection. We also recognize that your Search API is described for
> consumer-facing applications, so we would like your guidance on whether the
> pilot should be Search-first rather than based on Stock Sync.
>
> To avoid overlap with your customer journeys, the pilot will not operate a
> LoomAI lead inbox. Its only durable handoff will be the documented
> `POST /deals` operation under a separately granted Deal Updates capability,
> after explicit customer confirmation. Auto Trader will remain the system of
> record and LoomAI will retain only the returned external identifier and
> bounded audit evidence.
>
> We do not yet represent a live dealer. Northfield is fictional. We would be
> happy either to bring a retailer through your normal authorization process or
> work with a design partner you nominate.
>
> Could we schedule a short architecture and data-rights session to decide the
> correct API capability, permitted fields/processing, pilot retailer route and
> preferred handoff boundary?

---

## 13. Internal Changes Before The Next Auto Trader Demonstration

These changes improve accuracy of the proposition. They are not permission to
implement against Auto Trader without a grant.

### P0: messaging and scope

- Present Northfield as fictional on every relevant screen and artifact.
- State that no real retailer relationship or Auto Trader environment exists.
- Remove callback/test-drive lead management from the Auto Trader pilot story,
  and label it unambiguously as a separate fictional demo capability.
- Lead with guided discovery plus the single confirmed Auto Trader Deal handoff,
  not with generic platform breadth.
- Stop using "live revalidation" as an unqualified claim. The current action
  reads use a synchronized local source projection with a 900-second baseline
  and a 1,800-second staleness ceiling; event reconciliation can refresh an
  individual record sooner.

### P0: technical disclosure pack

- Produce one redacted trace showing authentication, baseline request,
  notification verification, targeted fetch, mapped fields and record removal.
- Mark `/internal/control/**` as simulator-only in that trace.
- Publish the exact current field allowlist and dropped fields.
- Document model and embedding provider, region, retention/no-training mode,
  logs, encryption, deletion and subprocessor boundary before requesting data
  rights.

### P0 external gate: capability alignment

- Do not rename the simulator to Search API or add guessed Search behavior.
- Ask Auto Trader whether Search, Stock Sync or both are correct for the pilot.
- Add no guessed Deal behavior to the simulator until Auto Trader confirms the
  sandbox request, response, failure and duplicate-handling contract.
- After that decision, create a separately versioned simulator/profile and
  immutable package matching only the granted contract.
- Keep Search-first/no-persistent-index and approved-index modes separate.

### P0 external gate: commercial alignment

- Recruit one retailer only after the use case is accepted, or ask Auto Trader
  to nominate one.
- Draft a one-page data processing schedule and pilot success scorecard.
- Make Auto Trader Deal creation the preferred and only first-pilot durable
  handoff.
- Obtain `Deal Updates`, privacy/consent, retry/idempotency and go-live approval
  before implementing or claiming that action.

---

## 14. Decision To Preserve

LoomAI should not try to win the partnership by proving that it can reproduce
Auto Trader's data and lead products.

It should prove this narrower value:

> Auto Trader defines the permitted data, advertiser product and customer
> journey. LoomAI makes those approved capabilities usable through a governed,
> dealer-isolated AI experience without exposing API authority to the model or
> creating a competing marketplace or lead system.
