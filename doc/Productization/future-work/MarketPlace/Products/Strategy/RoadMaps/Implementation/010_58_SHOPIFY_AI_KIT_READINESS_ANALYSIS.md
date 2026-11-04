# 010.58 Shopify AI Kit Readiness Analysis

Date: 4 November 2026

Status: analysis and proposed scope only; implementation not started

Source inspected: `Platform-V11`, HEAD `22af1393b0dc60e1fabd3cf3cf604c4f7f8e5d37`

Scope: current Shopify code, Kit/Engage/Desk primitives, checked-in evidence and current official Shopify guidance.

## 1. Verdict

**Yes: Shopify is a sensible next industry Kit. No: it is not currently a qualified, customer-selectable Shopify AI Kit.**

There is substantial reusable Shopify implementation: merchant installation,
credentials, theme delivery, public shopping, indexed sources, governed commerce
actions, customer-account authentication, merchant configuration and verification
scripts. The missing work is not another AI framework or another commerce backend.
It is joining those pieces to the current Kit/customer journey, correcting the
specific integration gaps below and collecting fresh end-to-end proof.

Recommended first product: **Shopify AI Shopping Experience**, with approved
catalogue/policy knowledge, live product reads, comparison, a branded website
workspace and optional human support. Offer standalone Engage from the same
deployment, but qualify its cart and sign-in behavior independently. Checkout
completion, refunds, order edits and broad account automation are not first-pilot
requirements. Do not advertise them because action definitions exist.

The first commercial decision is a named merchant pilot under an appropriate
Shopify distribution arrangement, not a claim that public App Store launch is
ready. A public multi-merchant listing is a separate release boundary.

The owner subsequently approved optional hosted document storage/uploads
([010.59](010_59_OPTIONAL_HOSTED_DOCUMENT_STORAGE_AND_STAGING_UPLOAD_PLAN.md)).
Once implemented and qualified, a Shopify Kit can reuse that shared-bucket,
purpose-prefix flow for merchant policy/operation files, alongside BYO storage.
This does not replace Shopify catalogue/webhook integration or make the proposed
upload capability an already-delivered Shopify feature.

## 2. Evidence Rules And Current Observations

Labels used throughout:

- **CODE:** implementation inspected in the current worktree, not a fresh test pass.
- **RECORDED:** dated, retained verification evidence; valid only for its recorded scope and version.
- **LIVE:** a read-only HTTP observation during this analysis, not inferred from history.
- **GAP:** missing implementation or a specific inspected risk needing correction/qualification.

No code, services, credentials, Shopify subscriptions, customer data or deployments
were changed. No AI query, write action, install or release-gate script was run.
Some existing gates can repair billing state, index data or execute cart changes;
they are not safe to treat as read-only analysis commands.

### Live Snapshot

Read-only probes on 4 November, approximately 00:57 UTC:

- Production `https://shopify-bridge.loomai.pro/actuator/health`: HTTP 200, `UP`.
- Staging `https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io/actuator/health`: HTTP 503, `no available server`.
- GET of production `/api/storefront/shops/shopping-companion-test.myshopify.com/workspace/bootstrap`: HTTP 405. Source declares POST for this adapter route; this is a method mismatch, not a failed valid bootstrap test. No POST/session creation was attempted.

These observations do **not** establish the production runtime version, active
store assignment, Shopify grants, indexed freshness, merchant billing posture or
working storefront chat. The staging failure needs inventory/route inspection;
it is not proof that data was lost or code is defective.

The latest inspected cleanup entry records Shopify/ProdUS as previously stopped
on 1 November. Older June controlled-production and May quality successes are
historical, not today's release acceptance. See
[working context](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md:11818)
and [historical Shopify release plan](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_18_SHOPIFY_COMPANION_PRODUCTION_RELEASE_AND_APP_LISTING_READINESS_PLAN.md:1).

## 3. What Already Exists

### Merchant Installation And Integration Service

**CODE:** `shopify-bridge-service` is an existing Shopify-specific trusted adapter,
not a new service proposed by this plan. It has a Spring backend, embedded Polaris
merchant UI, Shopify CLI configuration, theme app extension, install callback,
webhook subscription reconciliation and merchant-session validation. It integrates
with Platform-owned customer/deployment/consumer resources.

Embedded merchant authentication already exchanges Shopify session/ID tokens for
expiring offline credentials and supports refresh/rotation. Platform secret
references hold Admin credentials; install metadata is separate. This should be
reused, not described as missing. The older authorization-code callback is an
exception needing alignment, described in section 5.

Sources: [Bridge workspace](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/README.md:1),
[token exchange](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/install/service/ShopifyTokenExchangeService.java:37),
[credential acquisition/refresh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/install/service/ShopifyBridgeInstallCredentialService.java:50),
[Platform credential storage](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreCredentialService.java:51).

### Indexed Store Knowledge And Change Events

**CODE:** the Bridge exposes paginated and single-record source reads for products,
collections, pages, articles, policies and metaobjects. Product records include
images, variants, prices, availability and selected metadata. Article publication
filtering exists. Source selection and package/product caps also exist.

Platform installs real Data plugins, resolves compiled marketplace datasets,
configures vectorization, keeps fingerprints/ledger records, accepts source-change
events and schedules debounced/coalesced refresh runs. Existing webhook handling
verifies HMAC and carries shop, topic, object ID, version and delivery identity
into this path. Do not say live sync must be invented from scratch.

However, this is substantially a **Platform-scheduled Shopify indexing path**;
the presence of a single-record source endpoint does not prove the webhook
scheduler performs targeted one-record updates. Its current run enqueuer passes
entity/category scope and event IDs, not selected source object IDs. There is
also a second Bridge document-sync path; see section 5.

Sources: [source reader](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeVectorizationSourceService.java:324),
[Data-plugin configuration](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationService.java:114),
[event ledger](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationEventService.java:47),
[scheduler](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationQueueSchedulerService.java:60),
[refresh request](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationRunEnqueuer.java:23).

### Shopping UI, Reads And Cart Actions

**CODE:** the generic chat engine contains `ShopifyShoppingWorkspace`: discovery,
product cards/images, product detail, comparisons, policy views and cart UI.
The Shopify theme extension supplies an app embed and search/product/policy/order
blocks. Existing Liquid/JavaScript loads a packaged widget and Shopify shell.

Action plugins declare catalogue search, product detail, policy search and cart
get/create/update, using MCP/UCP routes. Cart writes declare confirmation and
trusted variant/cart resolution. A generic MCP execution gateway and Bridge
governed-action grants/audits already exist. These are useful source foundations,
not proof the current upstream tools or every exposed action work live.

The browser also has direct Shopify Ajax-cart helpers. Their same-origin cart
session is not automatically the same cart as a server-side MCP/UCP action.

Sources: [shopping workspace](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/max-mode-widget/src/components/ShopifyShoppingWorkspace.tsx:145),
[browser Shopify integration](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/max-mode-widget/src/integrations/shopify.ts:155),
[theme bootstrap](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/extensions/companion-theme-app-extension/snippets/companion-widget-bootstrap.liquid:1),
[commerce action manifests](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/db/migration/V131__shopify_storefront_current_ucp_actions.sql:1),
[MCP gateway](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/mcp-execution-gateway-service/src/main/java/com/ai/fabric/product/mcp/gateway/service/McpGatewayExecutionService.java:286).

### Authentication And Protected Account Features

**CODE:** Shopify shopper Customer Account OAuth, endpoint discovery, encrypted
session tokens, refresh, one-time claim handling and a runtime-bound token broker
exist. They are different from merchant Admin credentials and different again
from a Console/Desk Supabase identity. There is also a separate order-number plus
checkout-email lookup route using Admin API access.

Do not expose that legacy lookup as equivalent to authenticated customer-account
authorization. Keep account/order capabilities disabled in the first public
shopping profile unless the exact identity, scope, privacy and abuse controls
are qualified. An order reference/email supplied in chat is not a general grant
to use the merchant's Admin token for customer-specific reads or writes.

Sources: [Customer Account OAuth](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/customeraccount/service/ShopifyCustomerAccountOAuthService.java:145),
[token broker controller](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyCustomerAccountTokenBrokerController.java:1),
[separate order lookup](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontOrderLookupService.java:82).

### Kit, Engage And Desk Foundations

**CODE/RECORDED:** ordinary Kits already link canonical deployments, consumers
and one or both Engage installations; creation is configuration-only. Customer
staging, publish/apply/verify, production promotion, branding, preview, domains
and normal upgrades are reusable capabilities. Templates bootstrap defaults;
they do not permanently restrict later model/runtime/plugin choices.

The Platform already registers `shopify-storefront-bridge` as a private connection
profile. This is useful plumbing, but **not** a registered Shopify experience pack
or a complete Shopify kit. The pack registry currently recognizes only
`generic-workspace`, `dealership` and `support`; Kit catalogue migrations seed
general, dealership and Support experiences. Kit appearance shaping also has an
explicit supported-pack switch.

**RECORDED:** Support Kit 010.57 qualified native tickets, canonical conversations,
requester isolation, real form/action receipts, assigned agents, human replies
and return to AI. This is reusable product evidence, not Shopify-specific proof.
No Shopify-to-Desk handover wiring was found in the inspected Bridge/shopping UI.

Sources: [Kit resource composition](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/experiencekit/ExperienceKitService.java:90),
[Kit appearance handling](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/experiencekit/ExperienceKitService.java:237),
[pack registry](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceExperiencePackRegistry.java:15),
[Shopify connection profile](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceConnectionProfileRegistry.java:108),
[Support acceptance](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/verification-support/support-kit-01057/2026-11-04-production-acceptance.md:1).

## 4. Proposed Product Composition And Ownership

One merchant selects the Shopify Kit, connects an authorized store, configures
sources/branding, tests customer staging and explicitly activates/publishes.
Normal production promotion and later upgrades retain the merchant's deployment
and app identity. A Shopify install must connect to that owned configuration,
not silently create a second customer/deployment through the older bootstrap.

Suggested composition, **proposed identifiers rather than existing registrations**:

- Kit `shopify-ai-experience`: business-facing catalogue/setup metadata.
- Experience pack `shopify-shopping`: Shopify-specific presentation, configurable
  browse/context tools, product/variant renderers, cart and optional handover.
- Existing/refined Shopify template: conversational deployment with approved
  Data, Knowledge, Action and experience contributions using existing plugin types.
- Data plugins: merchant-approved public catalogue/policies; optional other
  approved content. Customer-provided S3 documents reuse document indexing.
- Action plugins: read-only live Shopify capabilities; explicitly enabled,
  confirmed cart operations; account operations separate and optional.
- Engage: website widget/Max Mode, standalone or both from the same consumer.
- Desk: optional existing shared multi-customer service and deployment-scoped
  agents; no per-store inbox service. Tickets are not mandatory for shopping.

### Data And Request Paths

```text
Merchant -> Console Kit -> owned deployment + consumer + Engage installation(s)
Merchant -> Shopify authorization -> existing trusted Bridge -> secret references

Shopify Admin public-content reads -> approved Data plugin/runner
  -> deployment-owned indexing state -> selected vector service
Shopify signed webhook -> verified durable event -> bounded refresh/reconcile
  -> fetch current authorized record(s) -> upsert/remove derived evidence

Visitor -> Shopify theme embed OR standalone Engage
  -> configured Shopify private adapter -> assigned runtime
  -> authorized retrieval / governed Shopify read or cart action
  -> product cards + sources + real receipt / visible uncertainty

Visitor -> optional handover -> shared Desk -> assigned agent
  -> canonical runtime conversation -> visitor; explicit return to AI
```

The diagram describes the intended product contract. The inspected Shopify
private adapter currently routes queries through the Bridge and Platform
consumer bridge. It is **not** today's anonymous direct-runtime route. Do not
claim no shared backend is involved. Keeping that existing trusted integration
adapter for a pilot is different from creating a new commerce system or forcing
the merchant to build a backend. If deployment-local direct traffic is made a
requirement, scope and qualify that adapter change explicitly; do not make it a
silent prerequisite for every Shopify capability.

Shopify remains system of record for products, inventory, orders and checkout.
LoomAI stores only integration metadata, authorized secrets, AI/conversation
state and derived evidence. Vectors may be managed or customer-provided.
Optional AI-state storage is not a merchant inventory database. Desk owns its
own shared support data when enabled. Do not create a per-deployment Shopify
business database or copy orders/customers into a public knowledge index.

## 5. Missing, Finalize And Cleanup Work

### G1. Correct The Webhook Event Envelope Before Claiming Live Sync

**Inspected defect, not yet reproduced by an execution test:** Bridge content
events pass a nullable `deliveryAttempt` (currently null) to Platform. Event
ingestion constructs `Map.of` with that value; Java rejects null map values.
Other optional envelope fields can fail similarly. The call chain contains no
normalization that makes this field non-null before ingestion. This can prevent
the durable event write and cause Shopify retries instead of indexing.

Fix optional-field serialization, not by inventing delivery attempts or weakening
HMAC/ownership. Add an HTTP-to-real-database regression using the actual Bridge
envelope, absent optional version fields, duplicate IDs and delete payloads.
Keep successful acknowledgment dependent on durable acceptance.

Evidence: [Bridge content event](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/webhook/service/ShopifyWebhookService.java:142),
[unchanged request forwarding](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreWebhookService.java:95),
[nullable Map.of construction](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationEventService.java:85).

### G2. Enforce Public Content Eligibility Before Indexing

**Inspected boundary gap:** product page/single-node Admin queries do not request
or enforce product status/channel publication. Pages also lack an equivalent
publication check; articles have one. Admin-readable content is not necessarily
approved for anonymous storefront retrieval. Product metafields/metaobjects also
need explicit merchant-approved fields/types, rather than broad Admin visibility.

Define the allowed publication/audience and field policy; enforce it for both
baseline and targeted reads, then remove prior vectors on unpublish/delete.
Test draft, archived, scheduled/unpublished, channel-restricted products and
private pages/metafields. Store/tenant isolation alone does not protect this
public/private boundary. Shopify exposes publication-aware product filtering;
use its API contracts, not title/query text matching.
[Official product query](https://shopify.dev/docs/api/admin-graphql/latest/queries/products).

Evidence: [products query](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeVectorizationSourceService.java:82),
[single-record path](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeVectorizationSourceService.java:407),
[page projection](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeVectorizationSourceService.java:720).

### G3. Converge On One Indexing Writer And Honest Freshness

The webhook handler records an event and then calls `syncFromWebhook`, which
loads enabled source categories and pushes documents to Platform. The event
scheduler independently enqueues a category/entity REFRESH. Calling this
"incremental" does not make it a targeted single-record update. Slow full-source
processing inside the webhook request also risks delivery timeouts.

Choose one authoritative indexing path for the Kit. Reuse the existing plugin,
runner and durable work reconciliation, with a baseline and bounded event
refresh. Retire the redundant document-sync trigger only after tracing its
remaining manual/uninstall callers. Keep a periodic or explicit reconciliation
path for missed events and sources without subscribed notifications. Targeted
record refresh is valuable as volume grows, but do not introduce a new queue
service just to ship a small merchant pilot.

Qualification must cover product create/update/delete/unpublish, policy changes,
missed/duplicate/out-of-order events, stale-source UI and index completion.
Do not assume inventory-only changes or every enabled content type have matching
subscriptions. Price/stock used for a purchase must be rechecked live; an index
timestamp is not a live stock guarantee.

Evidence: [webhook sync call](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/webhook/service/ShopifyWebhookService.java:258),
[full category loading](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/store/service/ShopifyBridgeStoreSyncService.java:193),
[current subscriptions](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/webhook/service/ShopifyWebhookSubscriptionService.java:76).

### G4. Register A Real Kit/Pack And Connect Store Ownership

Add catalogue definition, matching template/offer/workspace profile, pack schema,
integrity-checked assets, previews and customer setup. Wire the merchant's verified
Shopify shop to its existing owned Kit deployment/consumer. A typed shop domain
or public installation ID is not proof of merchant ownership.

Reuse the current Kit service and editors, rather than adding a second publishing
or offer engine. Make store connect/reconnect, source selection/index status,
theme enablement, preview/test and activation visible from the kit overview.
Keep merchant Admin UI only for Shopify-specific authentication/theme/billing
tasks; do not duplicate every Console editor inside it.

The Shopify legacy bootstrap can create a customer/deployment independently.
Resolve that overlap before a customer can create a Kit and then install the
Shopify app. Preserve one authoritative customer/deployment relationship.

Evidence: [legacy bootstrap resource creation](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreBootstrapService.java:133),
[current public Kit view](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/pages/ExperienceKitsPublicPage.tsx:20),
[asset publication](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/scripts/build-ai-workspace-assets.mjs:46).

### G5. Finish Conversation, Identity And Optional Desk Transport

The current Shopify private-adapter response declares conversations unsupported,
sets placeholder conversation URLs to the query path and returns `false`
capability flags. It supplies chat/query/suggestions, not the complete history,
canonical messaging/control and human handover contract used by newer packs.

Implement the missing adapter capabilities using the existing runtime/Desk
contracts. Qualify navigation, reload, multiple attached pages/items, separate
shopper identities, anonymous renewal and account login/logout. Preserve chat
on ordinary page navigation; do not copy transcripts into Desk or reopen a
closed episode implicitly. Account changes must not expose the prior identity's
history/cart/tickets.

When Desk is requested, use one shared service, actual deployment-scoped agent
assignments and the existing handover plugin. Prove requester/agent messages and
explicit return to AI. A Support Kit's successful Supabase flow does not prove
Shopify Customer Account identity mapping works with Desk. Reuse its adapter
contracts, not Console-private credentials or an automatic privileged fallback.

Evidence: [Shopify adapter capabilities](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyStorefrontController.java:74),
[Support identity/host contract](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/experience-packs/support-experience/README.md:88).

### G6. Qualify One Cart Contract Per Surface

Existing website helpers use relative `/cart*.js` calls. On a standalone Engage
hostname these point to Engage, not the merchant's Shopify store. Moving them
cross-origin is not a sufficient fix because storefront cookies/cart identity
and CORS differ. Existing UCP cart actions need current tool/schema/profile
qualification and a clear link to the cart the shopper eventually sees.

For the first website release, select one authoritative cart path and verify
actual Shopify readback after every confirmed change. Use locale-aware Shopify
URLs where Ajax is used. For standalone, qualify a server-side cart with a safe
merchant continuation/checkout URL or make purchase a clear handoff to the
merchant site. Never silently show a different cart or claim a write succeeded
because an action envelope returned HTTP 200.

Also review the shopping shell's browser product hydration/fallback and static
"Best sellers"/"Deals" tools. Product display is not an authoritative bestseller,
discount or live-stock claim without corresponding evidence.

### G7. Complete Auth And Privacy Across All Paths

Do not rebuild the working expiring-token exchange. Align the authorization-code
callback: it currently sends only client credentials/code and persists the result
as non-expiring with no refresh/expiry data. Also qualify concurrent token refresh
under the deployed topology, revocation, reinstall and denied/reduced scopes.
The inspected refresh path alone does not establish race-safe rotation.

Compliance handling is incomplete for a richer Kit: `customers/data_request`
and `customers/redact` currently record an event and return. The statement that
the Bridge has no local customer records does not settle data in Customer Account
sessions, canonical chats, usage/query records or Desk. Implement an auditable
cross-store inventory and appropriate export/redaction/retention workflow; it
may include documented operator fulfillment, but a receipt alone is not completion.
Keep shop uninstall, shop redaction and customer deletion distinct.

Evidence: [authorization-code callback](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/install/service/ShopifyInstallFlowService.java:94),
[code exchange](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/install/service/ShopifyInstallFlowService.java:234),
[current compliance handling](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/webhook/service/ShopifyWebhookService.java:65).

### G8. Requalify Current Defaults And Remove Obsolete Product Assumptions

The old Bridge uses Free/Starter/Elite packages, separate surface entitlements,
merchant widget settings and its own billing/launch posture. The Kit layer must
show one coherent included-capability and charging story. Retain real grants,
funding and commercial entitlements, but do not let old package/runtime pins
permanently restrict an owned deployment's normal upgrades.

Select current compatible runtime/connector artifacts and reviewed prompts;
do not inherit old manifests simply because historical verification passed.
Freshly discover the exact MCP tools, arguments and response shapes. Existing
source defaults use Admin/webhook version `2026-04`; that is not automatically
an outage, but needs supported-version/schema testing and an explicit update
decision. Never silently fall forward to a different schema.

Carry over only accepted, measured quality defaults from dealership/Support:
source catalogue descriptions, bounded read/RAG cooperation, trusted target
resolution and clear uncertainty. Do not copy dealership prompts/IDs, the
unaccepted Support 32k-context candidate or a particular model as a universal
solution. Keep LLM semantic choices; no keyword routing, lexical evidence ranking
or hidden search-all recovery. Explicit UI scope/presentation and diagnostic
test assertions are not semantic intent substitutes.

Sources: [Bridge defaults](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/resources/application.yml:36),
[storefront adapter/policy shaping](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontChatService.java:187),
[accepted Support limits](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/verification-support/support-kit-01057/2026-11-04-production-acceptance.md:1).

## 6. Current Shopify Requirements Checked

Official pages checked **4 November 2026**. These are external requirements;
their existence does not prove this app's grants, approval or hosted compliance.

- **Distribution:** Shopify distinguishes custom distribution for a particular
  merchant/eligible organization from public distribution for many merchants.
  Verify the existing app's selected distribution and permissible pilot route;
  do not label a multi-merchant install "private" to bypass review.
  [Distribution](https://shopify.dev/docs/apps/launch/distribution).
- **Admin API:** use GraphQL for new public-app integration. The API version page
  lists `2026-10` as latest stable at this review. Explicitly qualify the chosen
  supported version rather than equating "not latest" with "unsupported".
  [Versioning](https://shopify.dev/docs/api/usage/versioning),
  [REST legacy status](https://shopify.dev/docs/api/admin-rest).
- **Merchant auth:** embedded app ID-token exchange and offline-token refresh
  must be handled on the trusted server. Shopify's current public-app policy
  requires expiring offline tokens; existing non-expiring public apps face new
  install/listing restrictions from 1 January 2027 and GraphQL rejection from
  1 April 2027. The cited timeline excludes custom/merchant-created apps; it
  should not be applied indiscriminately. Fix the actual callback gap, not the
  already implemented exchange path.
  [Embedded authentication](https://shopify.dev/docs/apps/build/authentication-authorization/implement-token-exchange),
  [Expiring-token migration](https://shopify.dev/docs/apps/build/authentication-authorization/migrate-to-expiring-offline-access-tokens).
- **Webhook delivery:** validate the raw-body HMAC, persist delivery identity,
  tolerate duplicates and do not assume event order. HTTPS deliveries have a
  five-second total timeout; queue accepted work and reconcile missed changes.
  Existing event/worker infrastructure should be reused.
  [Verify deliveries](https://shopify.dev/docs/apps/build/webhooks/verify-deliveries),
  [Ordering](https://shopify.dev/docs/apps/build/webhooks).
- **Privacy/account data:** public-app compliance webhooks and protected-data
  requirements apply to the actual data processed. Keep requested personal/order
  access minimal. Customer Accounts MCP requires shopper OAuth; a merchant Admin
  token or Supabase login is not a substitute. Keep these features out of the
  first default profile if their approval and live proof are absent.
  [Privacy webhooks](https://shopify.dev/docs/apps/build/compliance/privacy-law-compliance),
  [Protected customer data](https://shopify.dev/docs/apps/launch/protected-customer-data),
  [Customer Accounts MCP](https://shopify.dev/docs/apps/build/customer-accounts-mcp).
- **Storefront delivery:** reuse theme app extensions/app embeds and merchant
  theme-editor enablement rather than editing merchant theme source. The current
  extension hosts widget assets on Shopify; the Platform-hosted installer can
  be the canonical Kit path, but replacing theme asset loading needs actual
  deployment/version proof. A Platform script is not installed in a theme merely
  because the merchant created a Kit.
  [Theme app extensions](https://shopify.dev/docs/apps/build/online-store/theme-app-extensions).
- **Cart/UCP:** Ajax Cart is a shopper storefront-session API and Shopify asks
  clients to use locale-aware URLs. Server-side UCP has separate agent identity,
  buyer-IP and tool contracts. Confirm the current profile/auth tier and schemas;
  do not assume an old successful MCP initialize proves today's cart write.
  [Ajax Cart](https://shopify.dev/docs/api/ajax/reference/cart),
  [UCP auth/rate limits](https://shopify.dev/docs/agents/profiles/auth-and-rate-limiting),
  [Cart MCP](https://shopify.dev/docs/agents/carts-and-checkout/cart-mcp).
- **Paid public release:** Shopify-provided billing is required for App Store
  apps. Current guidance makes Shopify App Pricing the default for new public
  apps; existing/manual Billing API integrations have their own supported cases.
  Decide this from the app's actual status, not an old test-mode environment
  value or Console funding contract.
  [Billing](https://shopify.dev/docs/apps/launch/billing).

## 7. Minimal Focused Implementation Phases

All phases below are proposed and **NOT STARTED** by this document. Keep one
main sequential merchant journey; parallel work only for independent bounded
pack/tests/docs tasks. No capacity/draining/recovery-service project is required.

### P0. Establish A Trustworthy Shopify Baseline

Inspect native stage/prod service inventory, deployed sources, actual store
ownership/grants and selected API/tool schemas. Correct G1/G2, align token
issuance and choose one indexing writer. Pin the pilot claim set and distribution.

Acceptance: focused Bridge/Platform tests pass with real PostgreSQL for durable
events; genuine signed product change enters work successfully; private/draft
content never enters public retrieval; baseline/update/delete converge;
token refresh and revoked-scope behavior are visible and safe. No framework
release should be assumed necessary. Report a real framework contract gap for
review before proposing one.

### P1. Compose The Kit And Merchant Setup

Register the Shopify pack/assets/template/profile/Kit using existing primitives;
connect an authorized store to the current customer's canonical resources.
Provide minimal guided UI: Connect store -> Choose public sources -> Brand and
preview -> Index and test -> Enable website/standalone -> Activate.

Acceptance: a normal customer creates from the catalogue, reloads and sees the
same resources; install/reconnect is idempotent; a foreign shop cannot be bound;
both channels share the intended consumer; no duplicate customer/deployment,
side-effecting creation or fake readiness. Reset-to-template edits the draft
only; normal upgrade retains app/deployment identity.

### P2. Complete The Shopping And Optional Human Journey

Adapt the existing shopping UI into the registered pack, keep generic tooling
generic, finish G5 and qualify G6. Use the shared Desk when selected. If ticketing
is added later, compose the provider-neutral support capability rather than
copying Console ticket storage into Shopify.

Acceptance: search -> product cards -> attach/select -> comparison/policy follow-up
-> actual confirmed cart change or explicit merchant handoff; reload/navigation
retain the right chat/context. Cancellation performs no write. Duplicate confirm
does not duplicate effects. Optional handover -> assigned agent reply -> visitor
reply -> explicit AI return works; disabled handover stays unavailable. No login,
cart, source, transcript or ticket crosses merchant/shopper boundaries.

### P3. Prove A Fresh Merchant Pilot In Staging And Production

Create a fresh kit through customer UI, not a repaired staff fixture. Use matching
template/plugin/configuration structure on customer staging and production;
environment credentials, callbacks, origins and source bindings remain separate.
Deploy/reindex from Shopify/approved documents on each target: **do not transfer
vector records or customer conversations during promotion**.

Acceptance: exact image/release/assignment evidence plus real browser proof;
desktop and actual narrow-screen storefront checks; public discovery, product
plus policy questions, comparisons, follow-ups, honest no-evidence cases, live
price/availability, cart cancellation/receipt, optional human loop. Test two
merchant boundaries, browser refresh, install/uninstall/reinstall and a basic
upstream failure. Score semantic quality separately from hard security and
execution success. Document difficult noncritical cases rather than expanding
this into an exhaustive platform reliability programme.

Pilot closure does not require account/order writes, autonomous checkout,
every theme, every locale or every external helpdesk. Do not claim those tested.

### P4. Public Distribution And Optional Protected Features

Only when commercial scope requires it: public listing/review, current billing,
privacy fulfillment, support collateral, approved Customer Account capabilities,
and real customer/order fixtures. Complete these before marketing the associated
features. An account tool returning "no orders" is not proof of correct real-order
authorization or a completed return/refund.

## 8. Verification Assets To Reuse

Existing source tests cover merchant sessions/install/token exchange, webhook
verification, subscriptions, source reading, chat shaping, cart grants, customer
OAuth/broker, vectorization policy/scheduling, store bootstrap/uninstall and Kit
creation. They were inspected, **not rerun in this documentation-only task**.

Useful starting points:

- [Bridge test tree](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/test/java/com/ai/fabric/product/shopify/bridge).
- [Platform Shopify tests](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/shopify/service).
- [Kit tests](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/experiencekit).
- [Live Shopify verifier](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/verify-shopify-companion.sh).
- [Max Mode browser verifier](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/verify-shopify-companion-max-widget-live.sh).
- [Uninstall verifier](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/verify-shopify-companion-uninstall.sh).
- [Quality query pack](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/verification/shopify-first-product-readiness/answer-quality-query-pack.json).
- [Quality runner](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/evaluate-shopify-companion-answers.py).

Extend these for the actual new Kit/pack, final assigned runtime and current
cart contract. Existing string-based answer assertions are diagnostics, not a
production semantic router and not sufficient evidence that an answer is good.
Keep raw/safe response evidence and review real answers. Add the actual nullable
webhook envelope regression and public-content visibility tests; mocks on either
side of that boundary cannot prove the integrated path.

## 9. Merchant And Owner Prerequisites

- A named merchant and authorized `*.myshopify.com` store, or an approved dev
  store for qualification; verify distribution suitability before installation.
- Authorized app owner/developer access, selected app identity, live callback and
  webhook origins, current credentials and correct grants. Store secrets only in
  approved private material/secret references, never the public Kit definition.
- Merchant approval of catalogue/content categories and public fields, policies,
  supported countries/currencies/languages and the claims the assistant may make.
- Theme-editor permission to enable the app embed and a test theme/storefront
  with realistic products, variants, sold-out items and policies.
- Chosen AI-state and vector posture. Additional document storage remains
  customer-owned unless the explicitly supported small/demo mount is chosen.
- Optional Desk agents with their own accounts and deployment assignments;
  confirmed contact/retention policies. Do not imply a staffed 24/7 service.
- Funding/model configuration and first-pilot commercial terms. Public app
  billing/listing and protected-data approval are separate explicit decisions.
- For later order features: new Customer Accounts setup, approved redirect URLs,
  necessary protected-data/scopes and real owned/foreign order test fixtures.

## 10. Decision Summary

**Reuse:** current AI runtime, plugin catalogue/Data/Action/Knowledge contracts,
Shopify trusted adapter and auth, shopping UI, Kit composition, Engage installer
and standalone shell, customer staging/promotion/editors, shared Desk and tests.

**Add:** a real Shopify Kit and registered experience pack; owned store connection
journey; complete conversation/optional Desk adapter; coherent per-surface cart
contract; current merchant-facing setup/readiness and fresh acceptance evidence.

**Fix/finalize first:** nullable webhook ingestion, public-content eligibility,
duplicate indexing writers/notification freshness, authorization-code token
lifecycle, privacy fulfillment and package/billing/defaults alignment.

**Do not add:** a merchant inventory/order database, a new generic bridge,
per-deployment inbox, second publishing engine, Shopify-specific intelligence
inside generic chat, hidden text-matching recovery or a speculative framework
release. Keep optional protected commerce features separate from the useful
read-first shopping pilot.

**Readiness today:** enough existing implementation to begin focused
productization, but insufficient current evidence to declare the Shopify AI Kit
launched, production-qualified or publicly self-service ready.
