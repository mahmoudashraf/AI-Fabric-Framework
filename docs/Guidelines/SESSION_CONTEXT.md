# AI Fabric Session Context

**Purpose**

Use this document as the first file to load in a new chat session when the work is about AI Fabric productization, the customer connector model, the runtime/relay contract, or the commerce connector demo in this repo.

This file is a consolidation layer, not the contract of record. When exact behavior matters, use the linked source files below.

## 1. What We Are Building

The current work is centered on productizing **AI Fabric** as:

- a domain-agnostic orchestration runtime
- with a language-agnostic **Customer Connector API** for actions and optional retrieval
- with curated packs for modes/prompt behavior
- while keeping business/domain state inside customer-owned systems or customer-side connector implementations

In this repo, the main runnable reference slice for that model is:

- **AI Fabric Runtime**: `Real_Apps/chat-capabilities-connector-demo` runtime config on port `8097`
- **Customer Connector**: `Real_Apps/chat-capabilities-connector-demo` app on port `8096`

The best single architecture walkthrough is:

- [`../../Real_Apps/chat-capabilities-connector-demo/docs/RUNNING_FLOW_AND_ARCHITECTURE.md`](../../Real_Apps/chat-capabilities-connector-demo/docs/RUNNING_FLOW_AND_ARCHITECTURE.md)

## 2. Architecture Snapshot

The runtime is responsible for:

- chat orchestration
- intent extraction
- confirmation flows
- RAG retrieval and generation
- action planning
- calling the connector for action execution
- exposing the Data Sync ingestion API

The connector is responsible for:

- implementing the customer-side action execution contract
- owning domain logic and operational state
- persisting domain state such as products, carts, orders, addresses, tickets, and reviews
- optionally pushing index updates into runtime Data Sync

The productization direction is:

- AI Fabric stays generic
- action definitions/config, vector spaces, and curated modes/prompts shape assistant behavior
- customer-side systems own domain semantics and side effects

## 3. Source Of Truth Files By Concern

### A) Action catalog

This is the concrete action contract for the commerce demo runtime:

- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml)

It defines:

- action names
- descriptions/categories
- access mode (`READ`, `WRITE_ONLY`)
- confirmation requirements
- confirmation messages
- param schemas
- sensitive params

### B) Entity and vector-space config

This is the concrete searchable/indexable entity config for the demo runtime:

- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml)

Current indexed/searchable vector spaces:

- `product`
- `policy`
- `review`

Important boundary:

- orders/customers/carts are operational state, not vector spaces by default in this demo

### C) Action semantics and confirmations

Core framework guide:

- [`../../Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`](../../Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md)

This defines:

- the V5 action model
- access modes
- confirmation interceptors
- clarification-before-confirmation behavior
- post-action facts
- fail-closed action authorization patterns

### D) Connector and relay architecture

Main productization guide:

- [`../../changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`](../../changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md)

Implementation guides:

- [`../../changes/Productization/CUSTOMER_CONNECTOR_IMPLEMENTATION_GUIDE.md`](../../changes/Productization/CUSTOMER_CONNECTOR_IMPLEMENTATION_GUIDE.md)
- [`../../changes/Productization/RELAY_IMPLEMENTATION_AND_DEPLOYMENT_GUIDE.md`](../../changes/Productization/RELAY_IMPLEMENTATION_AND_DEPLOYMENT_GUIDE.md)

Contract of record:

- [`../../changes/Productization/customer-connector-api.openapi.yml`](../../changes/Productization/customer-connector-api.openapi.yml)

These define:

- `POST /actions/execute`
- optional `POST /retrieval/search`
- request/response shapes
- idempotency expectations
- auth patterns
- rate limiting
- trace/user context forwarding
- SSRF-safe routing requirements

### E) Curated packs, modes, and prompts

Framework guide:

- [`../../Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md`](../../Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md)

Demo runtime activation:

- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/application.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/application.yml)

Commerce curated pack:

- [`../../ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml`](../../ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml)

Prompt bundle behavior check:

- [`../../ai-infrastructure-module/curated/ai-curated-commerce/src/test/java/com/ai/curated/commerce/CommerceCuratedPackTest.java`](../../ai-infrastructure-module/curated/ai-curated-commerce/src/test/java/com/ai/curated/commerce/CommerceCuratedPackTest.java)

Current important fact:

- the demo sets `ai.curated.pack: commerce`
- the `commerce` pack uses the default prompt bundle base version `v1`
- there are no active prompt overlay bundles for the commerce pack in the current implementation

### F) UI routing and request-shaping expectations

Main UI/runtime integration guide:

- [`../../Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`](../../Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md)

This is important for future sessions because it explains:

- `position` routing
- when to send `mode`
- when to send `attachments`
- how conversation ids and user ids are expected to flow

### G) Shipping and deployment shape

Shipping/distribution:

- [`../../changes/release/SHIPPING_PROCESS_AND_DISTRIBUTION_OPTIONS.md`](../../changes/release/SHIPPING_PROCESS_AND_DISTRIBUTION_OPTIONS.md)

Connector deploy guide:

- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`](../../Real_Apps/chat-capabilities-connector-demo/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md)

## 4. Demo-Specific Working Context

### Active runtime config

The demo runtime config is mounted from:

- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/application.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/application.yml)
- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml)
- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml)

Important runtime choices:

- chat sessions enabled
- curated pack set to `commerce`
- Lucene vector DB for dev/runtime storage
- OpenAI used for generation and embeddings when env vars are set
- actions executed through connector base URL configured by `ACTIONS_CONNECTOR_BASE_URL`

### Current action surface

The commerce demo action surface includes:

- catalog/product discovery
- cart view/add/remove/apply coupon/checkout
- direct purchase order creation and cancellation
- order listing/details/address change/retention discount offer
- account and address lookup
- support ticket creation
- review submission
- shipment tracking
- return request creation

This is defined in:

- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml)

### Current vector/search surface

The runtime currently indexes:

- products
- policies
- reviews

This is defined in:

- [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml)

Event-based indexing planning is documented here:

- [`../../Real_Apps/chat-capabilities-connector-demo/docs/EVENT_BASED_INDEXING_IMPLEMENTATION_PLAN.md`](../../Real_Apps/chat-capabilities-connector-demo/docs/EVENT_BASED_INDEXING_IMPLEMENTATION_PLAN.md)

Important note:

- the plan text starts by saying "products only", but the target-state section also mentions `policy` and `review`
- treat the actual runtime config and connector implementation as authoritative over plan wording

### Current UI mode routing

The commerce pack currently maps:

- `landing` -> `navigator`
- `catalog` -> `navigator`
- `search` -> `navigator`
- `cart` -> `cart_assistant`

Relevant files:

- [`../../ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml`](../../ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml)
- [`../../Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`](../../Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md)

## 5. Domain State Contract For The Demo

This repo does **not** currently have a single dedicated top-level "App State Contract" document for the commerce demo.

The authoritative behavior is split between the runtime/connector architecture doc and the connector implementation code.

### Cart

Operational meaning in the demo:

- a per-user active cart is created lazily if one does not exist
- cart status can be `ACTIVE`, `CHECKED_OUT`, or `ABANDONED`
- cart stores coupon code, subtotal, discount, total, currency, and cart items
- checkout turns the cart into an order and marks the cart as `CHECKED_OUT`

Primary sources:

- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/cart/domain/Cart.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/cart/domain/Cart.java)
- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/cart/service/CartService.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/cart/service/CartService.java)

### Order

Operational meaning in the demo:

- purchase orders are owned by a `userId`
- order status can be `CREATED`, `CANCELLED`, or `FULFILLED`
- orders store shipping address and email as operational data
- direct order creation decrements product stock
- order cancellation restores stock
- checkout-from-cart creates a purchase order plus payment and shipment records

Primary sources:

- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/orders/domain/PurchaseOrder.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/orders/domain/PurchaseOrder.java)
- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/orders/service/PurchaseOrderService.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/orders/service/PurchaseOrderService.java)

### Customer / user scope

Operational meaning in the demo:

- the runtime/connector request carries `trace.userId`
- connector actions require and re-scope work to that `userId`
- account, address, ticket, review, shipment, return, cart, and order access all hang off that user identity

Primary sources:

- [`../../changes/Productization/customer-connector-api.openapi.yml`](../../changes/Productization/customer-connector-api.openapi.yml)
- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/web/ActionExecuteController.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/web/ActionExecuteController.java)
- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/service/ConnectorActionDispatcher.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/service/ConnectorActionDispatcher.java)

## 6. Known Important Nuances

### Contract naming drift

Some older demo docs still say `actionName` for connector execution.

The current connector contract of record is:

- `actionId`

Use these as authoritative:

- [`../../changes/Productization/customer-connector-api.openapi.yml`](../../changes/Productization/customer-connector-api.openapi.yml)
- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/web/ActionExecuteController.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/web/ActionExecuteController.java)

### Prompt pack nuance

The runtime activates the `commerce` curated pack, but the current commerce pack intentionally relies on the default prompt bundle version rather than custom overlays.

Do not assume "commerce pack" means a separate prompt overlay is active.

### Domain contract gap

There is still no single first-class repo document that fully formalizes:

- what "cart", "order", and "customer" mean
- which runtime writes that state
- which parts are indexed versus action-only

For now, this document plus the linked connector implementation files are the closest thing to that contract.

## 7. Minimal File Set For A New Chat Session

If a future session needs a compact but sufficient context load, start with these:

1. This file:
   - [`SESSION_CONTEXT.md`](./SESSION_CONTEXT.md)
2. Runtime behavior/config:
   - [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/application.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/application.yml)
   - [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-actions.yml)
   - [`../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml`](../../Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/ai-entity-config.yml)
3. Action/connector contract:
   - [`../../changes/Productization/customer-connector-api.openapi.yml`](../../changes/Productization/customer-connector-api.openapi.yml)
   - [`../../changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`](../../changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md)
4. Demo architecture and UI integration:
   - [`../../Real_Apps/chat-capabilities-connector-demo/docs/RUNNING_FLOW_AND_ARCHITECTURE.md`](../../Real_Apps/chat-capabilities-connector-demo/docs/RUNNING_FLOW_AND_ARCHITECTURE.md)
   - [`../../Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`](../../Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md)
5. Curated pack behavior:
   - [`../../ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml`](../../ai-infrastructure-module/curated/ai-curated-commerce/src/main/resources/ai-curated/packs/commerce.yml)

If the task touches domain state semantics or action behavior in detail, also load:

- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/service/ConnectorActionDispatcher.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/connector/service/ConnectorActionDispatcher.java)
- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/cart/service/CartService.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/cart/service/CartService.java)
- [`../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/orders/service/PurchaseOrderService.java`](../../Real_Apps/chat-capabilities-connector-demo/src/main/java/com/ai/fabric/realapps/chat/orders/service/PurchaseOrderService.java)

## 8. Recommended Next Improvement

To reduce future context reconstruction work even further, add a dedicated first-class doc such as:

- `docs/Guidelines/DOMAIN_STATE_CONTRACT.md`

That document should explicitly define:

- indexed vs non-indexed entities
- operational state ownership
- cart/order/customer semantics
- write paths and read paths
- PII boundaries
- which files are contract-of-record versus illustrative
