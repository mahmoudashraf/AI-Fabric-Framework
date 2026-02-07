# Actions Execution (Local + Connector + Relay) — Architecture & Developer Guide

This document extends the V5 actions model described in:
- `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`

It adds a **language-agnostic** execution path for actions via a **Customer Connector API** (optionally implemented using an **AI Fabric Relay**).

> This is an architecture + productization guide for the **Actions** solution only.
> It does **not** define commerce entities, domain logic, or “built-in” domain actions.

---

## 0) Goal (What we’re shipping)

AI Fabric provides:
- A **single action model** (metadata + safety semantics + confirmations + conversation flows)
- A **single orchestration UX contract** (missing params → clarification; confirmation → pending action; yes/no → correct execution)
- Pluggable **action execution backends**

Customers provide:
- Their **action catalog** (definitions / contracts)
- Their **action implementations** (HTTP endpoints in any language)

Curated packs / licensing provide:
- **Modes + prompt optimizations + routing defaults**
- They do **not** hardcode business logic and do **not** require domain-specific entities.

---

## 1) Concepts (What runs where)

### 1.1 Action definition vs action execution

**Action definition** is what the orchestrator and the LLM need to know:
- `name`, `description`, `category`
- `accessMode`: `READ | READ_WRITE | WRITE_ONLY`
- parameter contract (required fields + validation hints)
- `requiresConfirmation` and optional confirmation message template

**Action execution** is how the action actually runs:
- **Local (in-process)**: Java `@AIAction` handlers executed in the same JVM as AI Fabric
- **Connector (out-of-process)**: AI Fabric calls a customer HTTP API and receives an `ActionResult`

The orchestrator should not care *where* the action runs. It only cares that:
- the action is defined
- the action is allowed
- missing parameters are handled deterministically
- confirmation rules are respected
- an `ActionResult` is returned in the framework contract

### 1.2 Customer Connector API

The **Customer Connector API** is a small, stable HTTP contract implemented by the customer (or by your integrator team).

AI Fabric (hosted or self-hosted) calls **one** connector base URL, typically:
- `POST /actions/execute` (actions)
- (optional) `POST /retrieval/search` (documents-only retrieval for RAG)

### 1.3 Relay (customer-side agent)

A **Relay** is an official AI Fabric component that implements the Customer Connector API.

Customers deploy the Relay inside their environment (VPC/on‑prem). The Relay then:
- receives `actionId + params` from AI Fabric
- calls internal services safely (private network)
- returns an `ActionResult` back to AI Fabric

This avoids forcing customers to expose internal systems publicly.

---

## 2) Unifying actions (no conflict, one model)

There is no fundamental conflict between:
- annotation-based actions (`@AIAction`) and
- connector-executed actions (HTTP)

**Unify the contract and orchestration semantics.**
Keep execution as an implementation detail.

### 2.1 Canonical action model (single truth)

Regardless of backend, every action should have a canonical model that includes:
- name + description + category
- access mode (`READ`, `READ_WRITE`, `WRITE_ONLY`)
- parameter schema (required + validation)
- requiresConfirmation + confirmation message strategy

### 2.2 Execution backends (two implementations)

**A) Local backend**
- Source of truth: Java `@AIAction` + `@Param` metadata (see V5 guide)
- Execution: in-process via `AIActionRegistry` + `AnnotatedAIActionHandler`

**B) Connector backend**
- Source of truth: config-defined action contract (file first; DB later)
- Execution: HTTP call to the Customer Connector API

### 2.3 Collision handling (fail fast)

If an action name is registered twice (local + connector, or connector + connector):
- **Fail fast** at registration/startup.

No silent overrides.

---

## 3) Action catalog (how actions are defined)

### 3.1 Local actions (Java annotations)

Use the V5 guide:
- `@AIAction` + `@ActionExecute` (+ optional `@ActionAllowed`, `@ActionConfirmation`, `@ActionFacts`)
- Typed `ActionResult.data` via `ActionResultContracts.object(...)` or `ActionResultContracts.list(...)`

### 3.2 Connector actions (file-based contract — first release)

For hosted + language-agnostic adoption, start with a file-based action contract loaded at boot.

**Recommended file:** `ai-actions.yml`

Example:

```yaml
ai:
  actions:
    sources:
      - type: file
        path: classpath:ai-actions.yml

actions:
  - name: create_purchase_order
    description: "Create a purchase order for a product"
    category: "commerce" # examples only; framework is domain-agnostic
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Create purchase order for {{quantity}} × {{sku}}?"
    params:
      - name: sku
        type: string
        description: "Product SKU"
        required: true
        pattern: "^[A-Z0-9_-]{3,64}$"
      - name: quantity
        type: integer
        description: "Quantity"
        required: true
        min: 1
        max: 100
      - name: shippingAddress
        type: string
        description: "Shipping address"
        required: true
        sensitive: true
      - name: email
        type: string
        description: "Customer email"
        required: true
        pattern: "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"
        sensitive: true

  - name: cancel_purchase_order
    description: "Cancel an existing purchase order"
    category: "commerce"
    accessMode: WRITE_ONLY
    requiresConfirmation: true
    confirmationMessage: "Cancel order {{orderRef}}?"
    params:
      - name: orderRef
        type: string
        description: "Order reference"
        required: true
```

Notes:
- This schema mirrors V5’s `@Param` capabilities: `required`, `pattern`, `allowedValues`, `min`, `max`.
- `type` is for documentation + UI hints; runtime should validate using the same rules as the Java binder.
- `sensitive: true` means “do not log / do not echo in confirmation by default”.
- The framework remains domain-agnostic: commerce names are illustrative only.

### 3.3 DB-backed registration (next)

After file-based adoption, support dynamic registration:
- Persist action definitions in DB
- Add a Web module controller:
  - register / deregister / list actions
  - return deterministic errors on duplicates (fail fast)
  - version actions (optional) to support safe updates

The runtime should treat file + DB sources uniformly (same canonical model).

---

## 4) Customer Connector API (execution contract)

AI Fabric should call a **single connector base URL**.
Do not let the model provide arbitrary URLs.

### 4.1 `POST /actions/execute`

Request (example):

```json
{
  "actionId": "create_purchase_order",
  "params": { "sku": "SKU-123", "quantity": 1, "email": "a@b.com", "shippingAddress": "..." },
  "idempotencyKey": "act_01H...",
  "trace": {
    "requestId": "req_...",
    "conversationId": "chat-...",
    "userId": "user-...",
    "sessionId": "sess-..."
  }
}
```

Response must match the framework action result contract:

```json
{
  "success": true,
  "message": "Purchase order created",
  "data": {
    "orderRef": "PO-123",
    "total": 249,
    "currency": "USD"
  }
}
```

#### Payload rules (important)

Align with V5 action payload contracts:
- `data` **MUST** be one of:
  - **object payload**: arbitrary key/value object (must not use reserved list keys)
  - **list payload**: must use reserved keys `_items`, `_count` (optionally `_totalCount`, `_cursor`)
- Reserved list keys are defined by `ActionResultContracts`:
  - `_items`, `_count`, `_totalCount`, `_cursor`

List payload example:

```json
{
  "success": true,
  "message": "Products",
  "data": {
    "_count": 2,
    "_items": [
      { "id": "p1", "title": "..." },
      { "id": "p2", "title": "..." }
    ]
  }
}
```

### 4.2 Idempotency + retries (hosted safety)

For `WRITE_ONLY` / `READ_WRITE` actions:
- AI Fabric must send an `idempotencyKey`
- Connector/Relay must implement idempotency (at least “exactly-once per key” best-effort)
- Retries are only safe when idempotency is supported

For `READ` actions:
- Retries are generally acceptable (still bounded)

### 4.3 Authentication

Supported patterns (choose one per customer):
- Static API key header
- HMAC-signed requests (recommended): include timestamp + nonce to prevent replay
- mTLS (best for enterprise later; optional early)

### 4.4 Timeouts + deterministic failures

Connector execution must be bounded:
- short connect + read timeouts
- deterministic error codes in `ActionResult.errorCode` for client/UI handling

---

## 5) Relay (recommended default for hosted deployments)

### 5.1 Who implements it?

AI Fabric implements and ships the Relay as an official component (Docker image / binary).
Customers deploy it and configure their internal routes/auth.

### 5.2 Do actions need to be defined in the Relay?

Not the full action contract (name/params/confirmation text).

But the Relay must know **where to send the call**, unless the customer uses a single internal dispatcher endpoint.

Two safe relay designs:

**A) Relay mapping: `actionId → internal endpoint` (most controlled)**
- Relay config file contains allowlisted actions and their internal routing
- Hosted AI Fabric never sends URLs

**B) Single internal dispatcher (least relay config)**
- Relay forwards everything to one internal endpoint like `POST /actions/execute`
- The customer service routes by `actionId` internally

Avoid:
- A relay that accepts arbitrary URLs from AI Fabric (SSRF risk)

### 5.3 Deployment patterns

- **Customer-side (recommended)**: Relay inside customer VPC/on‑prem, reachable from AI Fabric
- **Sidecar**: if AI Fabric is deployed into customer environment, run Relay next to it for consistent architecture

---

## 6) Confirmations and multi-step flows (unchanged)

All confirmation semantics remain as in V5:
- missing required params → `CLARIFICATION_REQUIRED`
- confirmation required → `CONFIRMATION_REQUIRED` with a pending action
- `yes/no` intents are resolved into execution by the confirmation pipeline step
- confirmation interceptors can implement flows like retention offers

**Key point:** interceptors operate on action names and pending stacks — they should not depend on the execution backend.

This means:
- a retention offer can chain two connector actions
- or chain local + connector actions
…with the same code.

---

## 7) Optional: documents-only retrieval via the connector

Customers may own retrieval but want AI Fabric to generate answers and orchestrate.

In that model:
- customer provides **documents only**
- AI Fabric does generation (and citations UX)

Suggested endpoint:
- `POST /retrieval/search`

Return:
- an ordered list of documents/chunks with `content`, `source`, `url`, `score`, and optional `vectorSpace`

This endpoint can be implemented:
- directly by the customer, or
- inside the Relay (which calls internal search/RAG systems)

---

## 8) What this enables (product outcomes)

With one unified action model + connector execution:
- Hosted AI Fabric can be **language-agnostic**
- Customers can adopt with their existing stack (Node/Python/Rails/etc.)
- Your curated packs remain “optimizations”, not domain lock-in
- Integrator/consulting work becomes repeatable: define actions + wire connector + ship

---

## References

- V5 actions + confirmations guide:
  - `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`
- Curated packs:
  - `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md`
- UI request contract (attachments + position/mode):
  - `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

