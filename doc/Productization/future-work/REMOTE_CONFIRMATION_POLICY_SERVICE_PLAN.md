# Remote Confirmation Policy Service Plan

Status: product architecture plan (2026-03-30)

This document describes an advanced extensibility model for business-rule interception during confirmation flows.

Goal:

- allow customers or consultants to implement custom interception/business logic in a **separate service/container**
- keep the core runtime generic
- avoid loading arbitrary customer Java directly into the runtime process

This is the recommended advanced extension model for use cases such as:

- cancel order -> offer retention discount
- cancel subscription -> offer downgrade instead
- refund request -> suggest store credit
- high-value order cancel -> escalate to support instead of cancelling directly

---

## 1) Executive Summary

### 1.1 Recommendation

Yes, this is valuable.

But the right first design is:

- **remote confirmation policy service**

not:

- loading arbitrary Java plugin jars into the runtime

### 1.2 Why this is valuable

This gives the platform a strong enterprise/customization story:

- platform handles the common case with config-driven rules
- advanced customers can bring custom business logic in their own service
- your runtime stays stable and upgradeable
- your consultancy can build custom policy services per customer/domain without forking core runtime

---

## 2) What Problem This Solves

The platform needs two levels of extensibility:

### Level 1: common product behavior

Use platform-managed config for:

- retention offers
- upsell prompts
- safe branching on confirmation

### Level 2: customer-specific policy logic

Use remote service when logic depends on:

- customer account status
- churn models
- CRM / billing / loyalty signals
- policy engines
- custom eligibility rules
- proprietary business calculations

Examples:

- “Offer discount only if customer LTV > 1000 and churn score > 0.8”
- “Do not allow cancellation if a shipping event is already in transit”
- “For VIP customers, escalate to a retention agent”

That logic is too specific to encode only as generic YAML rules.

---

## 3) Recommended Architecture

### 3.1 Do not load arbitrary Java into runtime first

Avoid this initial model:

- customer ships a jar/plugin
- runtime loads it into the same JVM

Why not:

- classpath/version conflicts
- security risk
- crash/isolation risk
- runtime upgrades become harder
- plugin lifecycle and dependency management become complex

### 3.2 Preferred model: remote service

Recommended model:

- runtime owns confirmation flow
- runtime calls an external **Confirmation Policy Service**
- service returns an interception decision

This service can be:

- customer-owned
- consultant-built
- deployed beside customer systems
- or hosted by you for a customer

This matches the rest of your product direction:

- runtime = productized orchestration engine
- connector/relay = integration seam
- external services = domain-specific logic

---

## 4) Where It Fits In The Current Runtime

Current flow:

- pending action exists
- user responds with confirmation
- `ConfirmationResolutionStep` runs
- `IntentResolver`s can intercept

Relevant framework pieces:

- [ConfirmationResolutionStep.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/pipeline/ConfirmationResolutionStep.java)
- [IntentResolver.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/spi/IntentResolver.java)
- [ConfirmationInterceptionContext.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/interception/ConfirmationInterceptionContext.java)

Recommended addition:

- `RemoteConfirmationPolicyResolver`

This resolver should:

1. run during confirmation resolution
2. build a request describing the current confirmation context
3. call the remote policy service
4. translate the response into an `InterceptionDecision`

---

## 5) Proposed Product Model

### 5.1 Three layers of confirmation behavior

The platform should support:

1. **No interception**
   - normal runtime confirmation behavior

2. **Configured interception**
   - declarative `confirmationInterceptors` rules

3. **Remote policy service**
   - runtime calls external policy logic

This creates a clear product ladder:

- standard
- advanced
- enterprise/custom

### 5.2 Evaluation order

Recommended order:

1. built-in safety/system resolvers
2. config-driven confirmation interceptors
3. remote confirmation policy resolver
4. default confirmation resolver behavior

This allows:

- fast local rules
- advanced external override only when needed

### 5.3 Optional composition

Allow a deployment to choose:

- config-only
- remote-only
- config-first-then-remote

---

## 6) Remote Service Contract

### 6.1 Request shape

The runtime sends:

- deployment id
- version id or config version
- conversation id
- user id / trace id
- current pending action
- confirmation intent
- pending action stack summary
- optional domain/user metadata

Example:

```json
{
  "deploymentId": "dep-123",
  "deploymentVersion": "v7",
  "conversationId": "conv-abc",
  "userId": "user-42",
  "confirmation": {
    "type": "CONFIRMATION_POSITIVE",
    "rawUserMessage": "yes do it"
  },
  "pending": {
    "action": "cancel_purchase_order",
    "actionParams": {
      "orderNumber": "PO-1001",
      "orderId": 1001
    },
    "description": "Cancel this order?"
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
    "channel": "chat",
    "customerTier": "gold"
  }
}
```

### 6.2 Response shape

The service returns one of:

- `NO_MATCH`
- `PROMPT_ACTION`
- `EXECUTE_ACTION`
- `REPLY`

Example:

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
    "popPreviousIfActionIn": []
  },
  "reason": "Retention offer available for eligible customer"
}
```

Example reject-to-original-cancel:

```json
{
  "decision": "EXECUTE_ACTION",
  "action": "cancel_purchase_order",
  "actionParams": {
    "orderNumber": "PO-1001"
  },
  "stack": {
    "popCurrent": true,
    "popPreviousIfActionIn": ["cancel_purchase_order"]
  },
  "reason": "Customer rejected retention offer"
}
```

### 6.3 Reply response

```json
{
  "decision": "REPLY",
  "message": "I can connect you to a support agent before cancelling if you prefer."
}
```

### 6.4 No-match response

```json
{
  "decision": "NO_MATCH"
}
```

---

## 7) Runtime Implementation Plan

### 7.1 Add remote resolver bean

Add:

- `RemoteConfirmationPolicyResolver`

Responsibilities:

- determine whether remote policy is enabled
- build request payload
- call remote endpoint
- translate response to `InterceptionDecision`
- fail safely

### 7.2 Configuration

Recommended runtime config:

```yaml
ai:
  chat:
    confirmation-policy:
      remote:
        enabled: true
        base-url: https://policy-service.example.com
        path: /api/confirmation-policy/evaluate
        timeout-ms: 1500
        outbound-auth:
          type: API_KEY
          api-key-header: X-POLICY-KEY
          api-key-value: ${CONFIRMATION_POLICY_API_KEY:}
        mode: FALLBACK
```

Recommended modes:

- `DISABLED`
- `FALLBACK`
  - only if config-driven rules did not match
- `OVERRIDE`
  - remote can decide first

### 7.3 Failure semantics

Recommended default:

- fail open to normal confirmation flow

If remote policy service is down:

- do not break the entire chat flow
- log warning
- continue with normal resolution

Optionally later:

- strict mode for regulated customers

### 7.4 Security

Support outbound auth:

- `NONE`
- `API_KEY`
- possibly `BEARER_TOKEN`

Do not send excessive sensitive data by default.

Request data should be bounded and explicit.

---

## 8) Platform Model

### 8.1 Where config should live

This should be deployment-level config, not global platform process config.

Recommended place:

- `securityConfig` or a new `conversationPolicyConfig`

Best long-term choice:

- add new `conversationPolicyConfig`

because this is not security-only and not action metadata only.

### 8.2 Suggested platform fields

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
    "apiKeySecretRef": "CONFIRMATION_POLICY_API_KEY"
  }
}
```

### 8.3 Secrets

Remote policy auth secrets should use the platform secret store.

Example:

- `CONFIRMATION_POLICY_API_KEY`

Do not place them in published JSON config blobs as raw values.

---

## 9) UI Plan

### 9.1 New configuration section

Recommended UI location:

- `Actions` page or a new `Conversation Policies` section

Recommended fields:

- enable remote confirmation policy
- mode
- base URL
- path
- timeout
- auth type
- secret ref

### 9.2 Operator UX

Operator should be able to say:

- for this deployment, use platform-configured retention rules only
- or call the customer policy service
- or call policy service only after local rules do not match

### 9.3 Diagnostics

Diagnostics should show:

- remote policy enabled/disabled
- last policy service status
- recent policy decision metadata
- whether a decision came from config or remote service

---

## 10) Why This Is Better Than In-Process Java Plugins

### 10.1 Advantages

- strong isolation
- no classpath/plugin loading complexity
- runtime upgrades remain easier
- customer logic can live in any repo/language
- easier consultant delivery model
- works well for customer-owned infrastructure

### 10.2 Tradeoffs

- network hop
- another service to run
- request/response contract design required
- slightly more latency in confirmation flow

These are acceptable for advanced business-rule logic.

### 10.3 When in-process Java plugins still make sense

Possible later support for:

- fully self-hosted enterprise deployments
- trusted internal plugin ecosystems
- low-latency local-only customizations

But this should be a later, narrower option.

---

## 11) Business Value

This extension model is valuable because it lets you sell:

- a stable AI enablement platform
- plus customizable domain/business policy logic

Good consultancy positioning:

- “We do not need to fork your runtime.”
- “We can keep the product core stable.”
- “We can deploy your business rules as a separate policy service.”

This is a strong enterprise story because it separates:

- orchestration platform
- customer business policy

---

## 12) Recommended Phases

### Phase 1: Config-Driven Interceptors

First implement:

- config-based `confirmationInterceptors`

This covers common cases cheaply and safely.

### Phase 2: Remote Policy Resolver

Then add:

- `RemoteConfirmationPolicyResolver`
- runtime config
- platform config + secrets
- diagnostics

### Phase 3: Remote Policy Service SDK/Template

Provide:

- sample Spring Boot policy service
- request/response contract
- local test harness

This is important for customer adoption.

### Phase 4: Optional Hosted/Managed Policy Services

Offer:

- consultant-built remote policy services
- managed by you or by customer

### Phase 5: Evaluate In-Process Plugin SPI

Only later, if enterprise demand justifies it.

---

## 13) Recommendation

Recommended product strategy:

1. restore config-driven interception first
2. add remote policy service as the advanced extension mechanism
3. postpone direct Java plugin loading

This gives you:

- good default product UX
- strong enterprise extensibility
- low coupling to customer code

---

## 14) Follow-Up Document

If this direction is approved, the next concrete implementation doc should be:

- `changes/Productization/REMOTE_CONFIRMATION_POLICY_SERVICE_EXECUTION_PLAN.md`

That follow-up should define:

- exact runtime config keys
- exact request/response DTOs
- resolver order rules
- platform backend schema/API changes
- UI changes
- sample policy service template
