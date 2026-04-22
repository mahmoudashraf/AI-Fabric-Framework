# Marketplace Post-Action Webhook Policy Implementation Plan

Status: implementation-baseline plan for the minimal post-action async policy slice (2026-04-17)

This plan defines the smallest production-worthy implementation for post-action async webhook delivery as a marketplace-compatible capability.

The scope is intentionally narrow:

- one post-action policy type: `webhook`
- attached to existing `ACTION` plugin contributions
- compiled into deployment config
- executed asynchronously after successful action execution

This plan is not a generic policy engine plan.
It is the minimum coherent slice needed to support Zapier-style outbound webhooks after actions.

---

## 1) First Decision: Should This Use The Connector?

Short answer:

- **Yes at the outbound execution substrate level, no at the lifecycle level.**

The correct architecture is:

- **one outbound execution substrate**
- **separate synchronous action vs asynchronous post-action lifecycles**

That means:

- the main action may still execute through the existing connector/outbound path
- the post-action webhook should reuse that outbound transport substrate where possible
- but the post-action webhook should still be executed by a separate runtime post-policy subsystem

### Why not fully unify the lifecycle

The connector path is designed for:

- primary action execution
- synchronous request/response behavior
- action-facing idempotency and retry semantics
- user-visible action outcomes

The webhook policy needs different behavior:

- it is not user-selected as an action
- it runs only after a successful action
- it must be asynchronous
- it needs its own retry queue and delivery status
- delivery failures must not rewrite the original action outcome
- it needs bounded secret-backed target configuration, not free-form action routing

If we force this into the connector abstraction, we conflate two separate concerns:

1. business action execution
2. post-action side-effect delivery

That would make retries, auditability, and operational visibility less coherent.

### What should be unified

We should unify the underlying outbound execution substrate:

- HTTP client and connection handling
- auth header or token injection
- request signing helpers
- JSON serialization
- upstream delivery execution
- optional relay or agent-aware delivery later

### What should remain separate

We should keep separate orchestration lifecycles:

- synchronous user-facing action execution
- asynchronous post-action webhook delivery

The webhook should therefore **not** appear as a normal user-visible action in the connector catalog, even if it reuses connector-style transport components underneath.

### What we should reuse from the connector/runtime stack

We should reuse shared infrastructure where useful:

- shared HTTP client factory
- outbound execution helpers
- secret resolution patterns
- signing helpers
- stable JSON serialization
- trace and deployment identifiers

This keeps transport code unified without collapsing delivery semantics.

---

## 2) Product Boundary

This capability should not introduce a new public plugin type.

Instead:

- existing `ACTION` plugins may contribute post-action webhook policy fragments
- platform compiles those fragments into deployment action config
- runtime executes them through a bounded post-policy engine

This stays aligned with the current marketplace boundary:

- marketplace is a control-plane composition layer
- runtime does not load arbitrary plugin code
- publish and apply remain required

---

## 3) Goal

After a successful action execution, the deployment may asynchronously send one or more webhook events to configured external targets.

Example:

- action: `cancel_order`
- post-policy: `webhook`
- target: Zapier catch hook
- event: `order.cancelled`

The user should receive the action result immediately.
Webhook delivery should happen in the background.

---

## 4) Explicit Non-Goals

Do not include these in v1:

- pre-action policies
- generic rule language
- arbitrary scripting
- arbitrary outbound HTTP payload templates
- multiple post-policy types
- workflow engine behavior
- marketplace plugin type expansion
- free-form public URLs inside action definitions

This slice should stay bounded and deterministic.

---

## 5) Minimal Contract Shape

### 5.1 Action-local post-policy

Extend deployment action config with per-action post-policies:

```yaml
actions:
  - name: cancel_order
    postPolicies:
      - type: webhook
        targetRef: zapier_order_events
        eventType: order.cancelled
```

### 5.2 Shared webhook target definitions

Add shared target declarations to the same resolved config:

```yaml
webhookTargets:
  - id: zapier_order_events
    urlSecretRef: ZAPIER_ORDER_EVENTS_URL
    signingSecretRef: ZAPIER_SIGNING_SECRET
    timeoutMs: 3000
    maxAttempts: 5
```

### 5.3 Why this shape

This shape is intentionally strict:

- action-local attachment is easy to reason about
- targets are reusable
- webhook URLs stay secret-backed
- validation remains bounded
- runtime can fail closed on bad references

---

## 6) Runtime/Framework Architecture

## 6.1 Where the lifecycle hook belongs

The post-policy hook should sit in the main action execution path in:

- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStep.java`

The current action result flow already converges here:

1. resolve action
2. execute handler
3. build `ActionResult`
4. optionally do read fallback
5. optionally do post-action generation
6. return orchestration result

The webhook enqueue hook should be inserted:

- **after successful `handler.executeAction(...)`**
- **before final response return**
- **isolated so enqueue failure cannot turn a successful action into a user-facing failure**

### 6.2 One outbound execution substrate

The runtime/framework should expose one reusable outbound execution substrate for:

- normal connector-backed actions
- asynchronous post-action webhook delivery

This substrate should centralize:

- transport
- auth and signing
- serialization
- timeout handling
- common response classification

But it should not erase the difference between:

- synchronous action execution
- asynchronous queued delivery

### 6.3 New runtime subsystem

Add a small dedicated post-policy subsystem:

- `ActionPostPolicyEngine`
- `ActionWebhookPolicyResolver`
- `ActionWebhookEnqueueService`
- `ActionWebhookDeliveryWorker`
- `OutboundExecutionService` or equivalent shared outbound substrate

Responsibilities:

- resolve applicable webhook policies for the executed action
- build deterministic payloads
- enqueue delivery jobs
- dispatch queued webhooks through the shared outbound execution substrate
- update delivery status

### 6.4 Catalog/config loading

Extend the action catalog/config loading path to load:

- `postPolicies`
- `webhookTargets`

Likely home:

- `ai-infrastructure-module/ai-infrastructure-actions-connector/.../ConnectorActionCatalogLoader.java`

This does not mean “turn the webhook into a normal synchronous action.”
It only means:

- the existing action config artifact loader remains the right place to parse deployment action config
- the existing outbound execution substrate should be reused instead of duplicating HTTP/auth plumbing

### 6.5 Minimal delivery payload

Use a fixed payload shape in v1:

```json
{
  "eventType": "order.cancelled",
  "timestamp": "2026-04-17T12:00:00Z",
  "deploymentId": "dep-123",
  "conversationId": "conv-123",
  "action": {
    "name": "cancel_order",
    "params": {
      "orderId": "O-1001"
    }
  },
  "result": {
    "success": true,
    "message": "Order cancelled",
    "data": {}
  }
}
```

Optional signing header:

- `X-AI-Fabric-Signature`

Do not introduce fully templated payloads in v1.

---

## 7) Platform Control-Plane Changes

### 7.1 Compiler

Marketplace `ACTION` installs should be allowed to contribute:

- action definitions
- routes
- post-policies
- webhook targets

Compiler responsibility:

- merge plugin webhook contributions into deployment action config
- reject invalid references during draft compilation

Primary platform touchpoint:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/DeploymentMarketplaceDraftCompilerService.java`

### 7.2 Draft validation

Extend deployment draft validation to check:

- only supported post-policy type is `webhook`
- `targetRef` exists
- target ids are unique
- secret refs are present and non-empty
- numeric limits like `timeoutMs` and `maxAttempts` are valid

Primary touchpoint:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDraftValidationService.java`

### 7.3 Published artifacts

The resolved webhook config must flow into the published deployment artifact path just like action config does today.

Primary touchpoint:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentConfigCompiler.java`

### 7.4 Release verification

Add parity checks so live runtime state can prove:

- expected action names with webhook post-policies are loaded
- expected webhook target ids are loaded
- counts and names match the published version

Primary touchpoint:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseVerificationService.java`

---

## 8) Delivery Storage And Worker

### 8.1 Minimal queue table

Add a single delivery table for queued webhook events.

Suggested columns:

- `id`
- `deployment_id`
- `action_name`
- `event_type`
- `target_ref`
- `payload_json`
- `status`
- `attempt_count`
- `next_attempt_at`
- `last_error`
- `created_at`
- `delivered_at`

### 8.2 Delivery states

Minimal state set:

- `PENDING`
- `IN_PROGRESS`
- `DELIVERED`
- `FAILED`

### 8.3 Worker behavior

Scheduled worker loop:

1. pick due `PENDING` deliveries
2. mark `IN_PROGRESS`
3. dispatch HTTP POST through the shared outbound execution substrate
4. on `2xx`, mark `DELIVERED`
5. on failure, either:
   - reschedule with backoff
   - or mark `FAILED` if attempts exhausted

### 8.4 Retry policy

Minimal retry model:

- bounded attempts
- exponential or stepped backoff
- no infinite retries

The retry policy should come from the target definition, not the action result path.

---

## 9) Security Model

This slice must stay fail-closed and bounded.

Rules:

- webhook target URL must come from secret-backed target configuration
- do not allow free-form runtime user input to choose target URLs
- do not allow plugin-defined arbitrary auth logic
- signing secret is optional but strongly recommended
- if target resolution fails, do not enqueue delivery
- if enqueue fails, log and surface internally, but do not mutate the already-successful action result

This is an outbound side-effect system.
It must be treated as such from day one.

---

## 10) Verification Plan

### 10.1 Local verification

Minimum local checks:

- config loader tests
- draft validation tests
- compiler merge tests
- delivery queue persistence tests
- worker delivery success and retry tests
- `git diff --check`

### 10.2 Release verification

Add parity assertions for:

- webhook post-policy counts
- webhook policy action names
- webhook target ids

### 10.3 Hosted/live verification

One real deployment proof is required:

1. publish/apply a deployment with one action webhook
2. execute the action successfully
3. verify a delivery row was created
4. verify the external endpoint received the event
5. force one failure and verify retry behavior

Recommended first live target:

- Zapier catch hook

---

## 11) First Seeded Example

Use an existing first-party `ACTION` plugin, not a new plugin type.

Recommended first example:

- extend a Shopify-style admin action plugin

Example behavior:

- `cancel_order`
- on success, emit `order.cancelled`
- deliver to `zapier_order_events`

This is product-legible and easy to validate live.

---

## 12) Recommended Implementation Waves

### Wave 0: Contract and validation

- add `postPolicies` and `webhookTargets` schema support
- add draft validation
- add catalog/config loader support

### Wave 1: Runtime enqueue path

- add runtime post-policy engine
- hook enqueue after successful action execution
- keep action result path non-blocking

### Wave 2: Delivery worker

- add delivery table
- add scheduled worker
- add retry and failure tracking

### Wave 3: Marketplace compiler

- allow `ACTION` plugins to contribute webhook policy config
- merge into deployment config
- publish artifact parity

### Wave 4: Verification and seeded example

- add release verification
- add live verification script step
- seed one first-party example

---

## 13) Final Recommendation

For the minimal production slice:

- **use the existing action config artifact**
- **do use one outbound execution substrate**
- **do keep synchronous action and asynchronous post-action lifecycles separate**
- **do use a dedicated async webhook delivery subsystem above that substrate**
- **do reuse shared HTTP, auth, signing, and secret-resolution infrastructure**

That gives the smallest architecture that is still coherent, secure, and marketplace-compatible.
