# 010.28 Auto Trader Partnership Position And Discovery Questions

- **Status:** Meeting-ready partnership brief; no Auto Trader integration or
  production-readiness claim
- **Date:** 2026-09-26
- **Audience:** LoomAI leadership, Auto Trader partnership/product teams,
  Auto Trader Connect integration team, and an initial dealership design partner
- **Purpose:** Establish a credible partnership position, obtain the decisions
  and access needed for a bounded pilot, and avoid positioning LoomAI as another
  generic dealership chatbot
- **Architecture boundary:** One dealership, one LoomAI deployment, one
  server-owned Auto Trader advertiser scope; plugin-defined and deployment-local

Related plans:

- [010.25 Auto Trader Connect LoomAI Capability Productization Analysis](010_25_AUTOTRADER_CONNECT_LOOMAI_CAPABILITY_PRODUCTIZATION_ANALYSIS.md)
- [010.26 Auto Trader Dealership First Release And Meeting Demo Plan](010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md)
- [010.27 Auto Trader Integration Platform Readiness Change And Evidence Plan](010_27_AUTOTRADER_INTEGRATION_PLATFORM_READINESS_CHANGE_AND_EVIDENCE_PLAN.md)
- [010.24 LoomAI File Document Indexing Platform Support Plan](010_24_LOOMAI_FILE_DOCUMENT_INDEXING_PLATFORM_SUPPORT_PLAN.md)

Official research references:

- [Auto Trader Connect](https://www.autotrader.co.uk/partners/retailer/platform/autotrader-connect)
- [Auto Trader platform](https://www.autotrader.co.uk/partners/retailer/platform)
- [Auto Trader Dealer Websites](https://help.autotrader.co.uk/hc/en-gb/sections/14691471794461-Dealer-Websites)
- [Auto Trader Dealer Website chat support](https://help.autotrader.co.uk/hc/en-gb/articles/13233677505949-Can-I-put-chat-on-a-Dealer-Website)
- [Auto Trader Live Chat](https://help.autotrader.co.uk/hc/en-gb/articles/13145555341597-What-is-Live-Chat)
- [Auto Trader Chat Assist](https://help.autotrader.co.uk/hc/en-gb/articles/13234113287709-What-is-Chat-Assist)
- [Auto Trader Connect advertiser business rules](https://www.autotrader.co.uk/partners/retailer/terms-and-conditions/auto-trader-connect)
- [Auto Trader 2026 annual report](https://plc.autotrader.co.uk/investors/2026-annual-report/)

---

## 1. Executive Partnership Position

LoomAI should not approach Auto Trader as a website builder, stock-data
reseller, general chatbot provider, competing marketplace, or replacement for
Deal Builder, Buying Signals, Co-Driver, Portal, a dealership CRM, or a dealer
management system.

The proposed position is:

> LoomAI is a governed AI experience and orchestration partner for Auto Trader
> Connect. It turns each dealership's authorized live data and dealer-owned
> knowledge into a verified guided-buying experience on dealer-owned digital
> surfaces, then transfers a customer-approved decision context into Auto
> Trader and dealership workflows.

The intended outcome is not more chat. It is:

- better vehicle discovery from the dealer's actual stock;
- more confident and explainable comparisons;
- current facts revalidated before a decision or handoff;
- fewer generic or incorrect answers;
- a customer-controlled transition from online research to a person;
- better-qualified enquiries, appointments, and Deal Builder starts; and
- measurable trust, conversion, and operational evidence.

This position complements Auto Trader's data, marketplace, retailer platform,
Dealer Websites, Deal Builder, Buying Signals, and retailer relationships. It
does not ask Auto Trader to become a model-hosting company or send routine
consumer traffic through LoomAI's central control plane.

---

## 2. Why This Can Matter To Auto Trader

Auto Trader already owns assets that LoomAI must not pretend to recreate:

- the UK's primary automotive marketplace and buyer audience;
- vehicle, stock, valuation, retail, and buyer-interaction data;
- retailer identity, advertiser scope, products, and commercial relationships;
- Auto Trader Connect and its integration-partner model;
- Portal, Deal Builder, Buying Signals, Co-Driver, and Retailer Store surfaces;
- hosted Dealer Websites for applicable retailer packages; and
- an incumbent LivePerson relationship for chat and managed chat services.

The opportunity for LoomAI is the space between those assets:

1. dealer website visitors still need to convert an imprecise need into a
   suitable, current shortlist;
2. vehicle facts, dealer policies, and operational actions usually live in
   different systems;
3. ordinary chat often captures a lead without producing a reusable decision
   state;
4. online research context can be lost when the customer reaches a salesperson;
5. generative answers need explicit evidence, freshness, scope, and action
   controls; and
6. integration partners need a repeatable way to productize AI without placing
   provider-specific logic in every dealership website.

LoomAI can supply that governed composition layer while Auto Trader remains the
authority for its licensed data and products.

---

## 3. Clear Division Of Responsibility

| Party | Owns |
| --- | --- |
| Auto Trader | Licensed automotive data, advertiser membership, capability grants, marketplace and retailer products, API rules, go-live validation, attribution requirements, and applicable Deal Builder or enquiry contracts |
| LoomAI Platform | Deployment lifecycle, Marketplace packages, configuration, release, assignment, verification, observability, promotion, rollback, and retirement |
| Assigned LoomAI deployment | Dealer-scoped sync, structured projection, permitted semantic index, conversational orchestration, evidence retrieval, action governance, traces, and deployment-local integration state |
| Dealership | Authorization to access its advertiser scope, dealer-owned policies, buyer-facing experience, CRM/DMS ownership, staff identity, business authorization, and final customer service or sale |
| Customer | Their requirements, shortlist, consent, contact preferences, and decision to share context or start an action |

The source of truth remains explicit:

- Auto Trader is authoritative for the Auto Trader facts covered by the grant.
- The dealership is authoritative for dealer policy and operational effects.
- A vector index is derived, rebuildable discovery evidence, never the
  authority for price, availability, finance, reservation, or provenance.
- The LLM interprets and explains approved facts; it does not invent trusted
  identifiers or authorize business effects.

---

## 4. Proposed Product Position

### 4.1 Working product description

**Auto Trader-powered Guided Buying, enabled by LoomAI**

Each participating dealership receives an isolated LoomAI deployment that can:

1. synchronize only the approved dealership's permitted inventory;
2. let a customer describe practical needs in natural language;
3. create a visible and editable requirement set;
4. retrieve a relevant dealer-only shortlist;
5. compare vehicles using typed, consistently labelled facts;
6. answer vehicle and dealership-policy questions with separate attribution;
7. re-read current price, availability, and selected facts before presentation
   or handoff;
8. show what is confirmed, possible, unknown, or unavailable;
9. let the customer preview and approve the context to share; and
10. hand the approved context into a test-drive, callback, Deal Builder, or
    dealership workflow supported by the exact grant.

### 4.2 The customer promise

> Find the right vehicle from this dealership's real stock, understand why it
> fits, verify the important facts, and reach the right person without starting
> again.

### 4.3 The retailer promise

> Receive better-qualified customers with their needs, shortlist, questions,
> and consented context already organized, while your systems remain
> authoritative.

### 4.4 The Auto Trader promise

> Extend authorized Auto Trader data and journeys into dealer-owned websites
> through a controlled integration that drives measurable, higher-quality
> engagement rather than creating an ungoverned data copy or competing buying
> flow.

---

## 5. What LoomAI Is Not Proposing

The meeting position must explicitly exclude:

- scraping Auto Trader or dealership websites;
- pooling inventory across dealerships without a separately authorized product;
- using Auto Trader data to train general models;
- presenting a dealer-scoped assistant as an independent whole-market adviser;
- replacing Auto Trader search, Deal Builder, Buying Signals, or Co-Driver;
- replacing the dealership website, CRM, DMS, finance provider, or salesperson;
- publishing, pricing, reserving, financing, or modifying vehicles in the first
  release;
- treating semantic matches as verified vehicle specifications;
- giving financial advice or making autonomous regulated decisions;
- retaining, embedding, or sending fields to an LLM without written rights;
- routing normal dealer traffic through the central LoomAI Platform; or
- claiming Auto Trader readiness before capability-specific sandbox and
  production gates pass.

---

## 6. Recommended Partnership Path

### 6.1 Route A: Auto Trader Connect integration partner

**Recommendation: start here.**

LoomAI becomes an approved integration provider for a bounded consumer-facing
dealer website use case. The first integration is authorized per retailer and
per capability. It runs on the dealer's existing independent website or a
meeting demonstration site.

Why this route is credible:

- Auto Trader explicitly operates an integration-partner model;
- Connect is intended to make Auto Trader services available in third-party
  systems;
- retailer onboarding can identify a Dealer Website or Interactive Dealer
  Website application; and
- the design can be proven with one dealer before any native Auto Trader
  website commitment.

### 6.2 Route B: approved Auto Trader Dealer Website experience

After a successful Connect pilot, ask Auto Trader to approve LoomAI as an
embedded or complementary experience on Auto Trader Dealer Websites.

The current public help material says those websites support LivePerson chat.
LoomAI must therefore not assume that arbitrary scripts or an alternative chat
provider are permitted. This route requires a deliberate product and commercial
decision by Auto Trader.

LoomAI should be positioned as a guided-buying and decision surface, not merely
as another chat bubble. It may coexist with human chat if Auto Trader prefers.

### 6.3 Route C: co-sell or white-label

Only after the pilot establishes measurable value, explore:

- an Auto Trader-certified integration;
- a retailer package or optional add-on;
- co-selling to suitable dealer groups;
- an Auto Trader-branded experience powered by LoomAI; or
- an implementation pattern available to other approved technology partners.

### 6.4 Routes to avoid initially

- direct-to-consumer cross-dealer marketplace;
- broad data licensing or redistribution;
- replacing an entire dealer website;
- pitching a generic AI platform without an automotive outcome;
- requesting every documented capability before a narrow use case is proven;
  and
- making LivePerson replacement the condition for partnership.

---

## 7. First Meeting Objectives

The first meeting should produce decisions, owners, and evidence paths. It
should not become a general AI presentation.

Required outcomes:

1. confirm whether Auto Trader sees strategic value in an AI-guided experience
   on dealer-owned websites;
2. identify the correct partnership and Auto Trader Connect onboarding route;
3. clarify the current Dealer Website and LivePerson product boundaries;
4. agree the smallest useful first customer journey;
5. identify one authorized design-partner dealership or test advertiser;
6. obtain the sandbox onboarding process and exact capability-grant owner;
7. identify data-use, LLM-processing, indexing, retention, and attribution
   decisions that require written approval;
8. determine the preferred Deal Builder, lead, appointment, or human-handoff
   boundary;
9. agree pilot success measures; and
10. leave with named commercial, product, integration, security, and legal
    contacts plus dated next actions.

---

## 8. Priority Questions For The First Meeting

These are the questions that should be answered before a technical deep dive.

| Priority | Question | Decision unlocked |
| --- | --- | --- |
| P0 | Does Auto Trader want approved partners to build AI-guided buying experiences on retailer-owned websites using Auto Trader Connect? | Confirms strategic fit |
| P0 | Which team owns this opportunity: Connect partnerships, Dealer Websites, Deal Builder, retailer product, or another group? | Establishes accountable sponsor |
| P0 | Is the hosted Dealer Websites product actively sold to new retailers, and what is its strategic direction? | Determines whether native embedding is relevant |
| P0 | What does the current LivePerson package actually provide: retailer-operated chat, managed human chat, AI assistance, autonomous AI, or a configurable combination? | Establishes the incumbent capability and real gap |
| P0 | Is Auto Trader open to a complementary guided-buying surface alongside LivePerson, or to an approved alternative after a pilot? | Determines the product surface |
| P0 | What is the correct process for LoomAI to become an Auto Trader Connect integration partner? | Starts formal onboarding |
| P0 | Which exact sandbox capabilities can be granted for a one-dealer buyer-concierge pilot? | Bounds implementation |
| P0 | Can Auto Trader provide one authorized sandbox integration ID, advertiser ID, representative test stock, and an Integration Manager? | Enables real development and proof |
| P0 | May approved dealer stock fields be retained in a deployment-local structured projection? | Determines baseline sync design |
| P0 | May approved descriptions or fields be transformed into embeddings and stored in a dealer-scoped vector index? | Determines semantic discovery design |
| P0 | May approved fields and retrieved evidence be sent to the dealer-selected LLM provider for inference? | Determines generation design |
| P0 | Which fields may be displayed on a consumer-facing dealer website, and what attribution or deep links are required? | Determines UI contract |
| P0 | What is the preferred handoff into Deal Builder, an Auto Trader enquiry, or a dealer-owned CRM/appointment workflow? | Prevents competing workflow design |
| P0 | Which actions are explicitly out of scope for the pilot? | Establishes fail-closed action policy |
| P0 | What validation, call-log, security, demonstration, and go-live evidence will Auto Trader require? | Establishes release gates |
| P0 | Which dealership profile would make a useful design partner, and can Auto Trader introduce one? | Enables market validation |
| P1 | What commercial model would Auto Trader prefer for a pilot and later rollout? | Frames business case |
| P1 | Which outcomes would make Auto Trader consider certification, co-selling, or Dealer Website integration? | Defines progression criteria |

---

## 9. Full Partnership Discovery Questions

### 9.1 Strategy and product fit

1. Which buyer or retailer problem does Auto Trader most want an integration
   partner to solve during the next 12 to 18 months?
2. Where does Auto Trader see the greatest gap between marketplace research,
   dealer-owned websites, and the physical forecourt?
3. Does Auto Trader want AI partners to improve discovery, qualification,
   conversion, retailer productivity, or all four?
4. Which of those outcomes are already owned by an active Auto Trader roadmap?
5. What would Auto Trader regard as harmful duplication of Deal Builder,
   Buying Signals, Co-Driver, search, or Portal?
6. Is Auto Trader primarily looking for horizontal integration partners or
   automotive-specific products with defined customer journeys?
7. Would Auto Trader prefer LoomAI to sell to dealers, to integrate behind an
   Auto Trader offer, or to operate as a white-label technology provider?
8. What evidence would make the partnership strategically interesting rather
   than just another API consumer?

### 9.2 Dealer Websites, Retailer Stores, and LivePerson

1. Are Auto Trader Dealer Websites available to new retailers today?
2. How many active sites and which retailer segments use them?
3. What extension mechanisms are supported: scripts, widgets, APIs, approved
   components, redirects, or only centrally delivered features?
4. Can a dealer authorize an approved third-party interactive experience on an
   Auto Trader Dealer Website?
5. Is LivePerson contractually exclusive on those sites?
6. Does the current LivePerson package use automated AI for ordinary vehicle
   questions, or does it mainly provide retailer agents and managed operators?
7. Does LivePerson receive live Auto Trader stock and structured vehicle data,
   or only page/conversation context?
8. Can it perform dealer-inventory search, comparison, availability checks,
   Deal Builder handoff, and persistent buyer-context transfer today?
9. Which chat transcripts or outcome metrics are available to the retailer and
   Auto Trader?
10. Could LoomAI complement human chat by preparing a verified shortlist and
    handoff pack rather than replacing the conversation channel?
11. Are Retailer Store pages open to partner-powered interactive experiences,
    or must the first pilot remain on dealer-owned websites?

### 9.3 Auto Trader Connect onboarding and capabilities

1. What legal and technical agreements are required for an Integrator System
   Provider?
2. Who sponsors and approves the integration?
3. Which API environment, authentication mechanism, token lifetime, and base
   URLs apply to the granted sandbox?
4. Which capabilities are available for dealer-scoped stock baseline and live
   selected-vehicle reads?
5. Are search, stock synchronization, advert details, taxonomy, equipment,
   charge time, MOT/history, finance, enquiries, and Deal Builder independently
   granted?
6. Which capabilities are appropriate for a consumer-facing dealer website?
7. Is an integration credential shared across authorized dealers with
   advertiser membership checks, or provisioned per dealer?
8. What is the authoritative method for proving that an advertiser belongs to
   the integration and that the retailer approved access?
9. What rate, concurrency, pagination, timeout, and fair-usage rules apply to
   each capability?
10. Which error responses indicate missing grant, wrong advertiser, expired
    token, fair-use pause, unavailable provider, or invalid request?
11. Are there supported idempotency keys for any write or enquiry capability?
12. Which test records and expected call-log evidence are provided for sandbox
    certification?
13. How are capability changes or deprecations communicated to partners?

### 9.4 Stock synchronization and freshness

1. Which endpoint is the approved baseline for all active stock belonging to
   one advertiser?
2. Is a consumer-search endpoint suitable for synchronization, or should a
   dedicated stock capability be used?
3. Which lifecycle states must remove a vehicle from consumer retrieval?
4. Do stock notifications contain a complete record or only a change signal?
5. What authentication, hash/signature, acknowledgement time, retry, ordering,
   replay, and duplicate semantics apply to notifications?
6. Is periodic advertiser-scoped reconciliation expected in addition to
   notifications?
7. Which source timestamp or version should be used to reject stale events?
8. How quickly must sold, reserved, unpublished, or deleted stock disappear
   from a consumer-facing experience?
9. Are price and availability required to be re-read before display, action, or
   handoff?
10. What service-level expectations apply to data freshness?

### 9.5 Data rights, AI processing, and privacy

1. Which fields may LoomAI retrieve for the approved consumer journey?
2. Which fields may be persisted, and for how long?
3. Which fields may be cached only transiently?
4. Which fields may be transformed into normalized dealer-vehicle records?
5. Which fields may be embedded or indexed semantically?
6. May embeddings be generated by a third-party model provider?
7. May approved evidence be sent to a hosted LLM for a single inference call?
8. Are generated comparisons or summaries considered derivative Auto Trader
   data, and what licence applies?
9. Must vectors and generated outputs remain in the UK or a specified region?
10. What deletion proof is required when stock is removed, a dealer disconnects,
    or the partnership ends?
11. Which attribution, logo, source label, deep link, disclaimer, or checked-at
    timestamp must accompany consumer output?
12. Which retail metrics may never be exposed on a consumer-facing surface?
13. May Auto Trader data be combined with dealer-owned warranty and service
    knowledge if source attribution remains separate?
14. What telemetry may LoomAI retain for quality evaluation without retaining
    restricted payloads?
15. What explicit restrictions apply to model training, fine-tuning, prompt
    logging, or provider retention?
16. What consent is required before sharing buyer requirements or conversation
    context with the retailer?

### 9.6 Search, evidence, and customer experience

1. Which Auto Trader product semantics should govern a dealer-site search?
2. May the assistant use semantic retrieval to discover candidates and then use
   Auto Trader APIs for exact filtering and revalidation?
3. How should possible derivative equipment be distinguished from confirmed
   fitted equipment?
4. Which claims require a live check rather than indexed evidence?
5. What should happen when a natural-language need cannot be mapped to an
   approved field?
6. Should unavailable answers hand off to a person, link to Auto Trader, or
   state the limitation without generating a lead automatically?
7. Which Auto Trader links should accompany a selected vehicle?
8. Can a customer maintain a shortlist or comparison state across a dealer-site
   session?
9. Should Auto Trader or the dealer own that state?
10. What accessibility, disclosure, and AI-identification requirements apply?

### 9.7 Deal Builder, enquiries, and dealership actions

1. What is the approved method for starting Deal Builder from a dealer-owned
   website?
2. Can a structured requirement set and selected stock ID be passed into that
   journey?
3. Is there an API or deep-link contract for creating a contextual handoff?
4. Can LoomAI submit a qualified enquiry through Auto Trader, or should it call
   the dealer's CRM/application directly?
5. Which customer fields are required, optional, or prohibited?
6. How should consent and channel preference be represented?
7. Can appointment or test-drive availability be read and booked through an
   approved capability?
8. Which actions require explicit customer confirmation?
9. Which actions require staff review or must remain entirely outside LoomAI?
10. What receipt or correlation ID should be returned to the customer and
    retailer?
11. How are duplicate leads or repeated submissions identified?
12. Can Buying Signals enrich a resulting enquiry, and if so, where is that
    enrichment visible and under what rights?

### 9.8 Security and deployment architecture

1. Does Auto Trader accept one isolated deployment per dealership as the
   initial integration boundary?
2. May each deployment call Auto Trader directly, or must all partner traffic
   use a fixed registered egress or gateway?
3. What IP allowlisting, mTLS, key rotation, certificate, or network controls
   are required?
4. Can sandbox and production credentials be separately provisioned and
   rotated through secret references?
5. What incident-notification and credential-compromise process applies?
6. What audit evidence must identify integration, advertiser, capability,
   request, and result without logging restricted data?
7. What penetration testing, security questionnaire, DPIA, DPA, or assurance
   reports are required?
8. Are subprocessors and LLM/vector providers subject to prior approval?
9. What recovery-time, availability, and support obligations apply?
10. What is the required offboarding sequence for credentials, webhooks,
    projections, vectors, logs, and customer context?

### 9.9 Pilot, validation, and commercial model

1. Which retailer segment should the first pilot target?
2. Can Auto Trader nominate a dealership with sufficient stock complexity and
   willingness to compare the experience against its existing journey?
3. Should the pilot run on the dealer's current site, a private demonstration
   site, or an Auto Trader Dealer Website?
4. What is an acceptable pilot duration and traffic allocation?
5. Which baseline metrics can Auto Trader and the dealer legally share?
6. Which success measures matter most: shortlist completion, qualified
   enquiries, test-drive requests, Deal Builder starts, conversion, response
   time, or reduced repeated qualification?
7. What factual-accuracy and stale-stock thresholds are mandatory?
8. Who provides first-line retailer and buyer support?
9. Who owns implementation, API, data-quality, and incident triage?
10. Who pays Auto Trader licence fees, LoomAI deployment fees, LLM usage, vector
    storage, and implementation costs?
11. Does Auto Trader prefer referral, reseller, revenue-share, marketplace,
    certification, or direct retailer contracting?
12. What evidence is needed before expanding to more advertisers or considering
    co-selling?

---

## 10. Proposed Bounded Pilot

### 10.1 Pilot scope

- one approved dealership;
- one advertiser ID;
- one isolated LoomAI deployment;
- one dealer-owned customer website or approved pilot surface;
- read-first vehicle discovery and comparison;
- dealer-owned policy retrieval;
- live selected-vehicle validation where granted;
- one customer-confirmed callback or test-drive handoff;
- real model, embedding, vector, and deployment services;
- visible source, freshness, and unsupported-field behavior; and
- no Auto Trader write unless explicitly included in the pilot grant.

### 10.2 Pilot customer journey

1. The buyer describes a need in ordinary language.
2. LoomAI shows the interpreted requirements for correction.
3. The deployment returns matching dealer-only vehicles.
4. The buyer compares selected vehicles using consistent fields.
5. Important facts are labelled by source and verification state.
6. Current price and availability are checked before handoff when supported.
7. The buyer previews the shortlist and context to be shared.
8. After explicit approval, the selected dealership workflow receives the
   structured handoff.
9. The customer receives a durable receipt or clear next-step confirmation.
10. The dealer sees enough context to continue without repeating discovery.

### 10.3 Pilot exclusions

- cross-dealer search;
- autonomous pricing or advert changes;
- reservation or finance application execution;
- financial advice;
- unapproved vehicle-history or provenance claims;
- unrestricted CRM/DMS access;
- proactive marketing without separate consent;
- model training on Auto Trader or customer data; and
- any capability not listed in the signed pilot grant.

### 10.4 Pilot measures

| Measure | Why it matters |
| --- | --- |
| Useful-result rate | Proves that customer needs produce relevant dealer stock |
| Shortlist and comparison completion | Measures decision support rather than chat activity |
| Qualified handoff rate | Measures movement into a real dealership workflow |
| Deal Builder or appointment start rate | Measures alignment with an existing conversion journey |
| Handoff completeness | Measures whether the salesperson receives useful context |
| Repeated-qualification reduction | Measures continuity between digital and human stages |
| Fact-support rate | Measures how many claims have approved evidence |
| Stale or wrong-stock incident rate | Protects customer trust and retailer operations |
| Unsupported-question honesty | Measures refusal to invent unavailable facts |
| Customer consent completion and withdrawal | Measures control over shared context |
| Dealer response and appointment outcome | Connects the experience to operational value |
| Source deletion and advertiser-isolation proof | Protects Auto Trader and retailer data boundaries |

Chat count, message length, and time spent are diagnostic measures, not primary
success outcomes.

---

## 11. Proposed Technical Shape For Discussion

```text
customer browser
  -> dealership website/backend
  -> assigned dealership LoomAI deployment
     -> conversational runtime
     -> deployment-local structured inventory projection
     -> deployment-scoped permitted semantic index
     -> deployment-local Generic REST Connector
        -> granted Auto Trader Connect capabilities
     -> dealership-owned policy source
     -> confirmed dealership action endpoint

Auto Trader stock event, when granted
  -> deployment-specific authenticated webhook
  -> advertiser validation and durable reconciliation
  -> projection update/delete
  -> indexing work and completion verification

LoomAI Platform
  -> provision, install, configure, release, verify, observe, rollback, retire
  -> does not proxy normal buyer, stock, or webhook traffic
```

Marketplace composition:

| Package | Responsibility |
| --- | --- |
| Dealership concierge `TEMPLATE` | Pins behavior, required packages, providers, surfaces, bindings, and verification |
| Auto Trader dealership-stock `DATA` plugin | Advertiser-scoped sync, normalization, permitted indexing, freshness, and deletion |
| Auto Trader dealership-discovery `ACTION` plugin | Live preflight, search/detail, and only granted evidence reads |
| Dealership-knowledge `DATA` plugin | Dealer-owned warranty, delivery, service, location, and support knowledge |
| Dealership-lead `ACTION` plugin | Confirmed callback/test-drive command and receipt |
| Approved `INFERENCE_PROFILE` | Explicit generation and embedding providers and dimensions |

The proposed Auto Trader HTTP DATA sync and inbound webhook mechanics are not
currently complete. They remain platform-readiness work identified in 010.27,
not functionality to imply during the meeting.

---

## 12. Data And Trust Commitments To Offer

LoomAI should volunteer the following commitments rather than wait to be asked:

- one advertiser scope per initial deployment;
- deny requests that lack a trusted deployment, tenant, or advertiser boundary;
- no cross-dealer vector spaces or retrieval;
- no model training on Auto Trader data;
- only explicitly permitted fields are retained, embedded, or sent for
  inference;
- source records, derived projections, vectors, and traces have documented
  retention and deterministic deletion;
- selected operational facts are revalidated when required;
- absent or unsupported facts remain absent;
- every answer distinguishes Auto Trader evidence from dealer-owned knowledge;
- consumer-facing responses include required source/freshness attribution;
- customer context is shared only after an explicit preview and approval;
- actions require deterministic authorization, confirmation where applicable,
  idempotency, and receipts;
- secrets remain deployment-bound and are never exported in deployment
  packages; and
- the exact sandbox or production capability grant is visible in readiness and
  verification evidence without exposing secret values.

---

## 13. Likely Objections And Proposed Responses

| Auto Trader concern | LoomAI response |
| --- | --- |
| "We already use LivePerson." | LoomAI is not asking Auto Trader to remove human chat. The pilot tests a verified decision workspace, dealer-stock reasoning, and consented context handoff. It can complement human messaging. We first need to understand which AI and stock capabilities LivePerson already provides. |
| "We already have Co-Driver." | Co-Driver improves retailer merchandising and advert creation. LoomAI proposes a buyer-facing decision and orchestration layer over approved data, not another description generator. |
| "Deal Builder already connects online and offline." | LoomAI should feed better-understood requirements and selected stock into the approved Deal Builder boundary, not create a parallel finance or transaction journey. |
| "Buying Signals already explains buyer intent." | LoomAI captures explicit, customer-reviewed needs and conversation context on the dealer's own surface. Any combination with Buying Signals must remain Auto Trader-owned and separately authorized. |
| "We cannot allow our data into a vector database." | Semantic indexing is optional and must be licensed. LoomAI can restrict indexed fields, use short retention, or use structured/live API search if Auto Trader does not permit embeddings. No readiness claim depends on assumed rights. |
| "AI may hallucinate vehicle facts." | LoomAI separates semantic discovery from exact facts, uses typed action results, labels evidence, revalidates current values, and refuses unsupported claims. Accuracy and stale-stock incidents are release gates. |
| "This could leak one dealer's data to another." | The first contract is one dealership and one advertiser per isolated deployment, with server-owned bindings, deployment-scoped storage, explicit membership checks, and cross-deployment isolation tests. |
| "This adds another system for retailers." | The customer experience embeds in the existing dealer site, while the deployment is installed from a versioned template. The pilot uses existing Auto Trader and dealership workflows rather than asking staff to operate a parallel CRM. |
| "The commercial case is unclear." | The pilot measures qualified handoffs, Deal Builder or appointment starts, factual quality, and repeated-qualification reduction against the current experience before asking for rollout. |
| "We cannot support every dealership integration." | LoomAI uses one reusable deployment template and approved plugin contracts. Dealer differences are bindings and configuration, not copied provider logic. The first pilot intentionally remains narrow. |

---

## 14. Concrete Requests To Make Of Auto Trader

At the end of the meeting, request the following package:

1. a named partnership/product sponsor;
2. a named Auto Trader Connect Integration Manager;
3. the integrator onboarding and contracting steps;
4. sandbox authentication/base URLs and separately delivered credentials;
5. a written matrix of the exact granted capabilities;
6. one authorized test integration ID and advertiser ID;
7. representative sandbox stock and capability-specific test records;
8. webhook registration, verification, retry, and test-event instructions if
   stock notifications are included;
9. capability rate limits, fair-use rules, and expected error semantics;
10. the certification and production go-live checklist;
11. written guidance covering persistence, caching, embeddings, LLM inference,
    retention, deletion, attribution, and consumer display;
12. the supported Dealer Website or independent-site embedding route;
13. the preferred Deal Builder, enquiry, CRM, or appointment handoff contract;
14. introduction to one candidate design-partner dealership; and
15. agreement on a follow-up architecture and data-rights workshop.

Credentials must never be placed in meeting notes, email threads, plugin
manifests, source control, or exported deployment packages.

---

## 15. Suggested Meeting Language

### 15.1 Thirty-second position

> LoomAI is not proposing another generic dealership chatbot or a replacement
> for Auto Trader's buying products. We want to become an Auto Trader Connect
> integration partner that turns each dealer's authorized live stock into a
> verified guided-buying experience on the dealer's own website. The customer
> can find and compare suitable vehicles, verify important facts, and approve a
> structured handoff into Auto Trader or dealership workflows without starting
> again.

### 15.2 Two-minute position

> Auto Trader already owns the trusted automotive data, advertiser relationship,
> marketplace reach, and products such as Deal Builder, Buying Signals and
> Co-Driver. LoomAI's role is the governed experience between those assets and a
> dealer's customer-facing application. We deploy one isolated AI runtime per
> dealership, bind it to one authorized advertiser, synchronize and index only
> permitted data, and revalidate current facts through granted APIs. The AI can
> combine that evidence with dealer-owned warranty and service knowledge, but it
> keeps the sources distinct. When a customer is ready, they preview exactly
> what will be shared and approve a structured handoff to the workflow Auto
> Trader and the dealer choose. We propose proving this with one read-first
> dealership pilot and objective conversion, accuracy, freshness, and isolation
> gates before discussing wider distribution.

### 15.3 Single partnership ask

> We are asking Auto Trader to assess LoomAI for a bounded Connect integration
> pilot, provide the correct sandbox and data-rights path, and help select one
> dealership where a verified guided-buying experience can be measured against
> the current digital journey.

---

## 16. Suggested 60-Minute Agenda

| Time | Topic | Desired outcome |
| --- | --- | --- |
| 0-5 minutes | Introductions and meeting decision | Confirm owners and purpose |
| 5-12 minutes | Auto Trader priorities and current journey | Hear the problem in Auto Trader's terms |
| 12-20 minutes | LoomAI position and bounded customer journey | Confirm complement rather than competition |
| 20-30 minutes | Dealer Websites, LivePerson, Deal Builder, and Connect boundaries | Identify product surface and overlaps |
| 30-40 minutes | Capability grants, sandbox, data rights, and integration shape | Identify technical/legal path and blockers |
| 40-48 minutes | Pilot dealer, scope, exclusions, and measures | Define smallest useful proof |
| 48-55 minutes | Commercial and support model | Identify contracting and operational owners |
| 55-60 minutes | Decisions, artifacts, owners, and dates | Leave with a written next-step package |

Do not spend the first half of the meeting demonstrating generic chat. Start by
asking Auto Trader which outcomes and product boundaries matter, then show only
the parts of the demonstration that prove those points.

---

## 17. Internal Go/No-Go Criteria After The Meeting

Proceed to an Auto Trader sandbox implementation only when all of these are
true:

- Auto Trader identifies a valid integration-partner route;
- an accountable sponsor and Integration Manager exist;
- the exact sandbox grant is documented;
- an authorized advertiser and representative data are available;
- the proposed customer-facing use is permitted;
- persistence, LLM inference, indexing, retention, deletion, and attribution
  decisions are explicit;
- a handoff boundary is selected;
- the required generic LoomAI readiness work is accepted and scheduled;
- the pilot has a dealership owner and measurable outcomes; and
- the validation/go-live process is known.

Pause or reshape the proposal when:

- Auto Trader permits API reads but prohibits the intended consumer use;
- data rights do not allow any useful structured or semantic discovery path;
- the proposed journey directly conflicts with an Auto Trader-owned product;
- no dealer is willing to participate;
- success cannot be measured beyond chat volume; or
- the required solution would introduce a central traffic bridge, unbounded
  cross-dealer data access, scraping, or unsupported claims.

---

## 18. Decision To Preserve

The partnership proposition is not "LoomAI has a chatbot."

It is:

> Auto Trader supplies the trusted automotive platform and authorized data;
> LoomAI safely composes those capabilities into a dealer-specific guided
> decision experience; the dealership remains responsible for its customer,
> policy, people, and business effects.

That division gives Auto Trader a reason to approve LoomAI as an integration
partner while preserving the role and value of Auto Trader's existing products.
