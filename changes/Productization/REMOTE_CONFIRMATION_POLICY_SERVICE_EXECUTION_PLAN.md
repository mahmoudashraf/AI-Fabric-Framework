# Remote Confirmation Policy Service Execution Plan

Status: execution plan (2026-03-30)

This document turns the remote policy-service idea into a concrete implementation plan for the AI Fabric platform.

It builds on:

- [CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md)
- [REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/changes/Productization/REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md)

---

## 1) Decision

Recommended extensibility ladder:

1. config-driven confirmation interceptors in the runtime
2. remote confirmation policy service for advanced customer logic
3. optional in-process Java plugin SPI only later and only for trusted/self-hosted cases

This means:

- the runtime remains the orchestration engine
- the REST connector remains a stateless action-routing layer
- customer-specific business logic can live in a separate repo/container

---

## 2) Why This Has Product Value

This is valuable because it gives the platform two strong modes of customization:

- product-friendly configuration for common business rules
- deep enterprise extensibility without forking the runtime

Concrete value:

- consultants can implement retention/escalation/eligibility logic in separate services
- customers can own sensitive business logic in their own infrastructure
- runtime upgrades stay easier because customer code is not loaded into the runtime JVM
- the platform can support multiple languages and stacks, not only Java

Examples:

- cancel order -> offer discount only for VIP customers
- refund request -> offer store credit if return reason matches a rule
- cancellation -> escalate to human support if shipment already entered a non-cancellable state
- downgrade/retention flows driven by CRM, loyalty, or churn-model signals

---

## 3) What We Are Building

We will add an optional remote decision point into confirmation resolution.

Runtime flow:

1. user confirms or rejects a pending action
2. runtime evaluates built-in and config-driven interception logic
3. runtime optionally calls a remote policy service
4. remote service returns a normalized decision
5. runtime translates that decision into an `InterceptionDecision`
6. runtime continues normal chat flow

This is not a generic “run arbitrary code” feature.

It is a bounded contract for:

- inspecting pending-action confirmation context
- returning one of a small number of decision types
- optionally selecting an alternative action or reply

---

## 4) Non-Goals

Do not build these in the first version:

- arbitrary remote code execution
- loading customer jars into the runtime JVM
- running remote policy on every user message
- allowing remote service to mutate runtime internals directly
- unbounded response formats
- multi-step workflow orchestration in the remote service

The remote service should only decide confirmation interception behavior.

---

## 5) Runtime Architecture

### 5.1 New runtime component

Add:

- `RemoteConfirmationPolicyResolver`

Location:

- `ai-infrastructure-module/ai-infrastructure-chat-session/...`

Responsibilities:

- detect whether remote policy is enabled for the active deployment
- build a bounded request DTO from current confirmation context
- call the remote policy endpoint
- translate the remote response into framework interception behavior
- fail safely when remote service is unavailable or invalid

### 5.2 Resolver order

Recommended order:

1. framework/system safety resolvers
2. config-driven confirmation interceptors
3. remote confirmation policy resolver
4. default confirmation behavior

This gives:

- fast local rules first
- external logic only for advanced decisions
- stable default fallback

### 5.3 Failure semantics

Default behavior:

- fail open

Meaning:

- if remote service times out, errors, or returns invalid JSON
- runtime logs the problem
- runtime continues with normal confirmation flow

Optional later mode:

- `STRICT`

Meaning:

- remote failure blocks the confirmation action

Do not implement `STRICT` in the first iteration unless a real customer requires it.

---

## 6) Runtime Configuration Model

Add deployment-level config for remote policy.

Recommended config block:

```yaml
ai:
  chat:
    confirmation-policy:
      remote:
        enabled: true
        mode: FALLBACK
        base-url: https://policy-service.example.com
        path: /api/confirmation-policy/evaluate
        timeout-ms: 1500
        auth:
          type: API_KEY
          header: X-POLICY-KEY
          secret-ref: CONFIRMATION_POLICY_API_KEY
        include:
          conversation-metadata: false
          pending-stack: true
          raw-user-message: true
```

Recommended enums:

- `mode`
  - `DISABLED`
  - `FALLBACK`
  - `OVERRIDE`
- `auth.type`
  - `NONE`
  - `API_KEY`
  - `BEARER_TOKEN`

Notes:

- `base-url` and `path` stay in normal config
- credentials must come from secrets, not published config
- `include.*` lets the deployment limit how much context is sent

---

## 7) Request Contract

### 7.1 Endpoint

Recommended remote endpoint:

- `POST /api/confirmation-policy/evaluate`

### 7.2 Headers

Runtime sends:

- `Content-Type: application/json`
- `X-AI-Fabric-Deployment-Id`
- `X-AI-Fabric-Deployment-Version`
- `X-AI-Fabric-Trace-Id`
- optional auth header depending on config

### 7.3 Request body

```json
{
  "deployment": {
    "id": "dep-123",
    "version": "ver-abc123",
    "environment": "dev"
  },
  "conversation": {
    "id": "conv-42",
    "channel": "chat"
  },
  "actor": {
    "userId": "user-17"
  },
  "confirmation": {
    "type": "CONFIRMATION_POSITIVE",
    "rawUserMessage": "yes do it"
  },
  "pending": {
    "action": "cancel_purchase_order",
    "actionDescription": "Cancel this order?",
    "actionParams": {
      "orderNumber": "PO-1001",
      "orderId": 1001
    }
  },
  "stack": [
    {
      "action": "cancel_purchase_order",
      "actionParams": {
        "orderNumber": "PO-1001",
        "orderId": 1001
      }
    }
  ],
  "context": {
    "customerTier": "gold"
  }
}
```

### 7.4 Request rules

Do:

- keep request bounded and predictable
- include only what the remote service needs
- cap stack size and serialized param size
- send deployment/version for auditability

Do not:

- send full chat history by default
- send raw secrets
- send internal framework-only objects

---

## 8) Response Contract

### 8.1 Supported decision types

- `NO_MATCH`
- `PROMPT_ACTION`
- `EXECUTE_ACTION`
- `REPLY`

### 8.2 NO_MATCH

```json
{
  "decision": "NO_MATCH"
}
```

Meaning:

- runtime continues with its next resolver or default behavior

### 8.3 PROMPT_ACTION

```json
{
  "decision": "PROMPT_ACTION",
  "action": "offer_order_discount",
  "actionParams": {
    "orderNumber": "PO-1001",
    "discountPercent": 10
  },
  "stack": {
    "popCurrent": false,
    "removePendingActions": []
  },
  "reason": "Eligible for retention offer"
}
```

Meaning:

- runtime should prompt/queue another action before the original one completes

### 8.4 EXECUTE_ACTION

```json
{
  "decision": "EXECUTE_ACTION",
  "action": "cancel_purchase_order",
  "actionParams": {
    "orderNumber": "PO-1001"
  },
  "stack": {
    "popCurrent": true,
    "removePendingActions": ["offer_order_discount"]
  },
  "reason": "Customer rejected retention offer"
}
```

Meaning:

- runtime should execute or continue with a specific action now

### 8.5 REPLY

```json
{
  "decision": "REPLY",
  "message": "I can connect you with support before we cancel the order if you prefer.",
  "stack": {
    "popCurrent": false,
    "removePendingActions": []
  }
}
```

Meaning:

- runtime responds conversationally without scheduling another action

### 8.6 Response validation

Runtime must reject invalid responses when:

- `decision` is unknown
- required fields for a decision are missing
- action name is blank
- action params are not a JSON object
- stack operations are malformed

On invalid response:

- log warning
- fail open to normal flow

---

## 9) Mapping To Framework Behavior

`RemoteConfirmationPolicyResolver` should translate:

- `NO_MATCH`
  - no interception decision
- `PROMPT_ACTION`
  - create interception that queues/prompts the target action
- `EXECUTE_ACTION`
  - create interception that executes/replaces pending behavior
- `REPLY`
  - create reply-only interception decision

Important:

- the remote contract must not bypass runtime validation
- target actions must still exist in the runtime action registry
- target action params must still satisfy action definitions as much as possible

If remote returns an unknown action:

- treat as invalid response
- fail open

---

## 10) Platform Backend Changes

### 10.1 Draft model

Add a new top-level deployment config object:

- `conversationPolicyConfig`

Suggested shape:

```json
{
  "remoteConfirmationPolicy": {
    "enabled": true,
    "mode": "FALLBACK",
    "baseUrl": "https://policy-service.example.com",
    "path": "/api/confirmation-policy/evaluate",
    "timeoutMs": 1500,
    "authType": "API_KEY",
    "authHeader": "X-POLICY-KEY",
    "apiKeySecretRef": "CONFIRMATION_POLICY_API_KEY",
    "includeConversationMetadata": false,
    "includePendingStack": true,
    "includeRawUserMessage": true
  }
}
```

### 10.2 Validation rules

Validate:

- `baseUrl` is required when enabled
- `path` is normalized and starts with `/`
- `timeoutMs` is within sane bounds
- `authHeader` required for `API_KEY`
- `apiKeySecretRef` required for `API_KEY` or `BEARER_TOKEN`
- `mode` is valid
- `baseUrl` uses `https` in production environments

### 10.3 Compiler output

Published config artifacts must include the remote policy config, but never raw secret values.

Secrets flow:

- deployment config references `CONFIRMATION_POLICY_API_KEY`
- platform apply injects actual env values
- runtime resolves them at boot

### 10.4 Apply/provisioning

No new infrastructure provider is required.

The platform only needs to inject additional runtime env/config:

- remote policy enabled flag
- base URL
- path
- timeout
- auth type/header
- secret-backed credential env

---

## 11) UI Changes

### 11.1 New section

Add a new operator UI section:

- `Conversation Policies`

Do not bury this inside generic security settings.

### 11.2 Fields

Expose:

- enable remote confirmation policy
- mode
- base URL
- path
- timeout
- auth type
- auth header
- secret ref
- context inclusion toggles

### 11.3 Diagnostics

Diagnostics should show:

- remote policy enabled/disabled
- effective mode
- configured endpoint
- last call status if available
- source of last confirmation decision:
  - local config
  - remote policy
  - default flow

### 11.4 Operator guidance

UI should clearly explain:

- use config-driven rules for common behavior
- use remote policy for customer-specific logic
- remote service should be highly available but runtime fails open by default

---

## 12) Secrets Model

Remote policy credentials belong in platform secrets, not draft config.

Examples:

- `CONFIRMATION_POLICY_API_KEY`
- `CONFIRMATION_POLICY_BEARER_TOKEN`

Rules:

- deployment config references a secret ref
- platform stores real value in secret store
- apply injects it into runtime env
- published artifact never includes secret value

---

## 13) Sample Remote Policy Service

We should ship a starter template.

Recommended first template:

- Spring Boot
- Java
- one endpoint: `POST /api/confirmation-policy/evaluate`
- simple DTOs matching runtime contract
- sample rules:
  - cancel order -> offer discount
  - customer rejects discount -> execute cancel

Why:

- many customers/consultants in this ecosystem will already use Java
- fastest path to adoption
- proves contract design before multi-language examples

Later templates:

- Node.js/TypeScript
- Python

This is where “separate repo with Java business logic in its own container” becomes a strong supported story.

---

## 14) Comparison With In-Process Java Plugins

### 14.1 Remote service advantages

- strong isolation
- no classpath collisions
- no plugin lifecycle complexity in runtime
- easier runtime upgrades
- customer can deploy independently
- can use any language, not just Java

### 14.2 In-process plugin advantages

- lower latency
- easier access to runtime internals
- good for trusted self-hosted extensions

### 14.3 Recommendation

Support the remote service first.

Treat in-process Java plugin SPI as:

- later
- narrower
- enterprise/self-hosted only

This gives the product a clear story without coupling the runtime to arbitrary customer jars.

---

## 15) Security And Reliability

### 15.1 Security

- default to `https`
- use secret-backed auth
- bound request payload size
- do not send secrets or excessive user data
- log decision metadata, not raw sensitive payloads by default

### 15.2 Reliability

- short timeout, default `1500ms`
- fail-open default
- retries off by default for confirmation path
- circuit-breaker style metrics can come later

### 15.3 Auditability

Record:

- remote policy enabled/disabled
- remote endpoint used
- decision type returned
- reason if provided
- whether fallback occurred because of remote failure

---

## 16) Phased Implementation

### Phase 1: Config-driven interceptors

Complete first:

- declarative `confirmationInterceptors`
- runtime support
- platform backend/UI support

This covers the common product case.

### Phase 2: Remote resolver in runtime

Add:

- request/response DTOs
- `RemoteConfirmationPolicyResolver`
- config loading
- fail-open behavior
- tests for all decision types

### Phase 3: Platform config and secrets

Add:

- `conversationPolicyConfig`
- validation
- secret refs
- apply-time env/config generation
- UI editor

### Phase 4: Diagnostics and audit

Add:

- decision source visibility
- remote failure visibility
- operator diagnostics
- audit trail fields

### Phase 5: Sample service template

Ship:

- Java Spring Boot starter
- example ecommerce retention rule
- local integration test harness

### Phase 6: Optional later plugin SPI evaluation

Only after:

- remote service adoption proves insufficient
- there is concrete enterprise demand

---

## 17) Definition Of Done

This feature is done when:

- a deployment can enable a remote confirmation policy service through platform config
- runtime calls it only during confirmation resolution
- runtime safely handles `NO_MATCH`, `PROMPT_ACTION`, `EXECUTE_ACTION`, and `REPLY`
- remote auth credentials are secret-backed
- platform UI exposes the config cleanly
- diagnostics show whether the last decision came from remote or local logic
- an example policy-service repo/container demonstrates:
  - cancel order -> offer discount
  - reject discount -> proceed with cancel

---

## 18) Immediate Recommendation

Build in this order:

1. config-driven confirmation interceptors
2. remote confirmation policy service
3. Java sample policy-service template repo

Do not start with arbitrary in-process Java plugins.

That keeps the platform stable while still giving customers and consultants a real path to ship custom business logic in their own container.
