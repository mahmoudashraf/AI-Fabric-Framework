# Platform Next LLM Session Context Dump

Use this file as the committed, sanitized handoff for the next LLM session.

This is not a secret store.
Do not put raw credentials here.
If live access is required, use the private handoff file separately and keep it out of commits.

## 1. Current Repo State

- Repo root: `/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo`
- Branch: `Platform-V5`
- HEAD: `9ba2fd1b737c6bbac76ca1150d36bbc21137eced`
- Date captured: `2026-04-17`

Important branch note:

- the user explicitly reset the branch to `9ba2fd1b737c6bbac76ca1150d36bbc21137eced`
- all streaming/widget work discussed after that point must be treated as discarded
- do not assume any later widget or streaming commits exist on the active branch

Current local note:

- one unrelated untracked file may exist:
  - `doc/Productization/future-work/MarketPlace/PLATFORM_CONTRACT_FINALIZATION_AND_LAUNCH_PLAN.md`
- do not assume it is committed or authoritative unless the user asks to use it

## 2. Current Marketplace Baseline On This Branch

Authoritative index:

- `doc/Productization/future-work/MarketPlace/README.md`

Current supported public marketplace plugin types:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

Required interpretation:

- marketplace is a control-plane composition layer
- installs compile into deployment drafts and published versions
- publish and apply remain mandatory before live behavior changes
- runtime and shell do not load arbitrary third-party code
- only runtime-backed contracts should be productized through marketplace

## 3. Relevant Current Guides

Start here for current implementation context:

1. `doc/Productization/future-work/MarketPlace/README.md`
2. `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
3. `doc/Productization/future-work/MarketPlace/MARKETPLACE_INFERENCE_PROFILE_PRODUCTIZATION_PLAN.md`
4. `doc/Productization/future-work/MarketPlace/MARKETPLACE_SHARED_INFERENCE_SERVICE_PLATFORM_PLAN.md`
5. `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`

Only use the private handoff doc if live credentials are required:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_NEXT_LLM_SESSION_HANDOFF_PRIVATE.md`

That private file is operationally useful but intentionally not safe for normal committed handoff usage.

## 4. What Was Just Discussed In This Session

The active product discussion at the point of this handoff was not about streaming or widget UX.

The useful current topic is:

- minimal marketplace-compatible post-action async policy support

The user explicitly narrowed scope to:

- ignore pre-action policies
- keep only the smallest useful post-action async policy

## 5. Agreed Minimal Direction: Post-Action Async Webhook Policy

The clean minimal implementation direction is:

- support exactly one post-action async policy type:
  - `webhook`
- do not add a new public marketplace plugin type
- attach this capability to existing `ACTION` plugins
- compile it into deployment config
- execute it from runtime/framework config, not from third-party code

## 6. Minimal Config Direction

Use action-local post-policy declarations plus shared target definitions.

Conceptual shape:

```yaml
actions:
  - name: cancel_order
    postPolicies:
      - type: webhook
        targetRef: zapier_order_events
        eventType: order.cancelled

webhookTargets:
  - id: zapier_order_events
    urlSecretRef: ZAPIER_ORDER_EVENTS_URL
    signingSecretRef: ZAPIER_SIGNING_SECRET
    timeoutMs: 3000
    maxAttempts: 5
```

Rationale:

- per-action policy attachment is easy to reason about
- target reuse stays centralized
- target URL remains secret-backed
- validation remains bounded and deterministic

## 7. Minimal Runtime Behavior

Required behavior:

1. action executes successfully
2. matching webhook post-policy is resolved
3. delivery job is enqueued
4. user response returns immediately
5. background worker sends the webhook asynchronously

Do not block the user-facing action response on webhook delivery.

## 8. Minimal Delivery Payload

Conceptual payload shape:

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
    "data": {}
  }
}
```

Optional signing header:

- `X-AI-Fabric-Signature`

## 9. Minimal Platform Pieces To Build

### 9.1 Config And Validation

Add support for:

- `actions[].postPolicies[]`
- `webhookTargets[]`

Validate:

- policy type must be `webhook`
- referenced action exists
- `targetRef` resolves to a declared target
- secret refs are non-empty

### 9.2 Delivery Queue Table

Minimal table shape:

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

### 9.3 Enqueue Service

After successful action execution:

- create delivery rows for matching webhook post-policies

### 9.4 Background Sender

Scheduled worker:

- pick pending rows
- send HTTP POST
- mark delivered on `2xx`
- retry on failure until `maxAttempts`

## 10. Minimal Marketplace Integration

Do not add a new public plugin type.

Instead:

- extend `ACTION` plugin contributions so they can emit:
  - action definitions
  - action routes
  - post-action webhook policies
  - webhook target definitions

Compiler responsibility:

- merge plugin-provided webhook config into deployment config
- validate references before publish

## 11. Minimal Verification Target

Required proof:

1. publish and apply a deployment with one webhook post-policy
2. trigger the action successfully
3. confirm a delivery row is created
4. confirm the webhook endpoint receives the payload
5. force one failure and confirm retry behavior works

Good first real example:

- extend a first-party `ACTION` plugin such as a Shopify admin action plugin
- deliver post-action event to a Zapier catch hook

## 12. Recommended Immediate Next Sequence

If the next session starts implementation, use this order:

1. add config schema for `postPolicies` and `webhookTargets`
2. add validation
3. add delivery queue table
4. enqueue on successful action execution
5. add scheduled sender with retry
6. extend marketplace `ACTION` compiler support
7. seed one first-party example
8. add one live verification script or verification step

## 13. Explicit Non-Goals For The Next Session

Do not expand scope unless the user asks:

- no pre-action policies
- no generic workflow engine
- no arbitrary user scripting
- no arbitrary outbound HTTP execution without bounded target config
- no new public marketplace plugin type for webhook behavior

## 14. Session Safety Notes

- this file is safe to commit
- do not copy raw secrets into this file
- if live validation is needed, use the private handoff only as an operational credential source, not as a design reference
