# Confirmation Interception Productization Plan

Status: partially implemented (updated 2026-04-13)

Implementation state:

- DONE: runtime config-driven confirmation interceptor loading and resolution
- DONE: stack-aware pending action support for configured interception rules
- DONE: platform draft validation for `confirmationInterceptors`
- DONE: artifact/compiler path support through `actionsConfig -> ai-actions.yml`
- DONE: ecommerce bootstrap/sample config support
- DONE: runtime admin visibility and platform release-verification alignment for interceptor metadata
- DONE: ecommerce deployment verifier coverage for retention-flow runtime/admin alignment
- PENDING: structured Platform UI editor for confirmation policies

This document explains:

- where the old “intercept action A and route to action B” capability existed
- why it was effectively lost in the productized `runtime + rest-connector` architecture
- how to restore it in a configuration-driven way for the platform

This specifically covers conversation flows such as:

- user asks to cancel an order
- runtime asks for confirmation
- user confirms
- instead of immediately executing `cancel_purchase_order`, the system offers `offer_order_discount`
- if user accepts, execute the discount action
- if user rejects, execute the original cancel action

---

## 1) Investigation Summary

### 1.1 Where the capability existed before

The old capability was **not** implemented as normal action metadata or routing.

It existed as **confirmation interception / intent resolver logic** in the chat-session layer.

Concrete example:

- [CancellationRetentionOfferResolver.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Real_Apps/chat-capabilities-demo/src/main/java/com/ai/fabric/realapps/chat/orders/resolver/CancellationRetentionOfferResolver.java)

That resolver:

- watches a pending action `cancel_purchase_order`
- intercepts positive confirmation
- prompts `offer_order_discount`
- on accept, executes `offer_order_discount`
- on reject, executes the original `cancel_purchase_order`

### 1.2 What still exists in the framework

The framework still supports this capability:

- confirmation pipeline step:
  - [ConfirmationResolutionStep.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/pipeline/ConfirmationResolutionStep.java)
- resolver auto-discovery:
  - [ChatSessionBaseConfiguration.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/config/ChatSessionBaseConfiguration.java)
- annotation-based interceptor support:
  - `@AIConfirmationInterceptors`
  - `@OnPendingActionConfirmation`
- interception helper context:
  - [ConfirmationInterceptionContext.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/interception/ConfirmationInterceptionContext.java)

### 1.3 What changed in productization

The productized runtime/connector model became configuration-driven:

- action metadata comes from `ai-actions.yml`
- action execution comes through the generic REST connector
- routing comes from `actions-routing.yml`

Current ecommerce productized config includes both actions:

- `cancel_purchase_order`
- `offer_order_discount`

Files:

- [ai-actions.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Real_Apps/ecommerce-store/deploy/runtime/config/ai-actions.yml)
- [actions-routing.yml](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Real_Apps/ecommerce-store/deploy/rest-connector/actions-routing.yml)

But there is no configuration that expresses:

- “when confirming action X, prompt action Y instead”

### 1.4 Root cause

The capability was lost at the **application packaging / config boundary**.

Specifically:

- the runtime still supports confirmation interception
- the rest connector is not the right layer for it
- the productized configuration model never gained a declarative equivalent of app resolver beans

So the missing feature is:

- **declarative confirmation interception policy configuration**

---

## 2) What Layer Should Own This

### 2.1 Not the REST connector

This logic should **not** live in the REST connector.

Why:

- the REST connector is stateless request routing
- it does not own pending confirmation state
- it does not own conversation stack semantics
- it executes actions after the runtime has already decided what to do

### 2.2 It belongs in the runtime

This logic belongs in the runtime because the runtime owns:

- chat sessions
- pending actions
- confirmation resolution
- final action choice before execution

### 2.3 It should be platform-configurable

For the productized system, the platform should manage this capability as configuration, not custom code.

That means:

- runtime loads interception rules from config
- platform stores/edits/versions those rules
- deployment apply publishes them with the rest of the runtime config

---

## 3) Recommended Product Model

### 3.1 Do not put this inside routing config

Routing config should remain transport-focused:

- upstream URL
- HTTP method
- request templates
- response templates
- auth

Interception rules are conversation/orchestration policy, not transport routing.

### 3.2 Best place: extend the action config artifact

Recommended model:

- keep action metadata in `ai-actions.yml`
- add a top-level section for confirmation interception policy

Recommended shape:

```yaml
actions:
  - name: cancel_purchase_order
    ...

  - name: offer_order_discount
    ...

confirmationInterceptors:
  - name: cancel_to_retention_offer
    trigger:
      pendingActions: [cancel_purchase_order]
      confirmation: CONFIRMATION_POSITIVE
      onceParam: _retentionOfferOffered
    decision:
      type: PROMPT_ACTION
      action: offer_order_discount
      params:
        orderNumber: "{{pending.actionParams.orderNumber}}"
        orderId: "{{pending.actionParams.orderId}}"
        discountPercent: 10

  - name: accept_retention_offer
    trigger:
      pendingActions: [offer_order_discount]
      confirmation: CONFIRMATION_POSITIVE
    stack:
      popCurrent: true
      popPreviousIfActionIn: [cancel_purchase_order]
    decision:
      type: EXECUTE_ACTION
      action: offer_order_discount
      params:
        orderNumber: "{{pending.actionParams.orderNumber}}"
        orderId: "{{pending.actionParams.orderId}}"
        discountPercent: "{{pending.actionParams.discountPercent|10}}"

  - name: reject_retention_offer
    trigger:
      pendingActions: [offer_order_discount]
      confirmation: CONFIRMATION_NEGATIVE
    stack:
      popCurrent: true
      popPreviousIfActionIn: [cancel_purchase_order]
    decision:
      type: EXECUTE_ACTION
      action: cancel_purchase_order
      params:
        orderNumber: "{{stack.previous.actionParams.orderNumber}}"
        orderId: "{{stack.previous.actionParams.orderId}}"
```

### 3.3 Why top-level is better than per-action fields

This is better than putting it inside one action because interception rules describe:

- transitions between actions
- confirmation intent branching
- pending stack behavior

That is not single-action metadata.

---

## 4) Minimal V1 Schema

Recommended V1 schema:

### 4.1 Top-level section

```yaml
confirmationInterceptors:
  - ...
```

### 4.2 Trigger block

```yaml
trigger:
  pendingActions: [cancel_purchase_order]
  confirmation: CONFIRMATION_POSITIVE
  onceParam: _retentionOfferOffered
```

Fields:

- `pendingActions`
  - required
  - one or more action names
- `confirmation`
  - required
  - `CONFIRMATION_POSITIVE`
  - `CONFIRMATION_NEGATIVE`
- `onceParam`
  - optional
  - same semantics as annotation-based `onceParam`

### 4.3 Decision block

```yaml
decision:
  type: PROMPT_ACTION
  action: offer_order_discount
  params:
    discountPercent: 10
```

Supported decision types:

- `PROMPT_ACTION`
  - create a new pending action requiring normal confirmation
- `EXECUTE_ACTION`
  - create an already-confirmed action to execute now
- `REPLY`
  - direct informational reply, no action execution

For `PROMPT_ACTION` and `EXECUTE_ACTION`:

- `action` is required
- `params` optional

For `REPLY`:

- `message` is required

### 4.4 Stack block

```yaml
stack:
  popCurrent: true
  popPreviousIfActionIn: [cancel_purchase_order]
```

Supported V1 fields:

- `popCurrent`
  - optional boolean
- `popPreviousIfActionIn`
  - optional action list

This keeps V1 practical without exposing full low-level stack scripting.

### 4.5 Param templates

Allow the same simple template engine style already used in routing config.

Supported contexts:

- `pending.actionParams.*`
- `stack.previous.actionParams.*`
- constants / literals
- simple fallback syntax such as:
  - `"{{pending.actionParams.discountPercent|10}}"`

Do not build a full expression language in V1.

---

## 5) Runtime Implementation Plan

### 5.1 Keep existing resolver pipeline

Do not replace:

- `ConfirmationResolutionStep`
- `IntentResolver`
- annotation-based interceptors

Instead, add a new resolver:

- `ConfiguredConfirmationInterceptorsResolver`

This resolver should:

- read the loaded confirmation interceptor config
- inspect current pending action + confirmation intent
- apply the first matching rule
- return the same `InterceptionDecision` objects used today

### 5.2 Add config loading

Recommended approach:

- extend the action catalog loading path so `ai-actions.yml` can carry:
  - `actions`
  - `confirmationInterceptors`

This avoids adding another artifact URL in V1.

Status: DONE

Needed classes:

- `ConfiguredConfirmationInterceptorRule`
- `ConfiguredConfirmationInterceptorTrigger`
- `ConfiguredConfirmationInterceptorDecision`
- `ConfiguredConfirmationInterceptorStackPolicy`

### 5.3 Add runtime bean

Add:

- `ConfiguredConfirmationInterceptorsResolver`

Status: DONE

Behavior:

1. only run on confirmation intents
2. inspect top pending action
3. match rules by:
   - pending action name
   - confirmation type
4. apply `onceParam` guard
5. mutate stack according to policy
6. build `InterceptionDecision`

### 5.4 Preserve annotation-based custom apps

Do not remove:

- `@AIConfirmationInterceptors`

Custom app runtimes should still be able to use code-based resolvers.

Product runtime should simply gain:

- config-based interception as an additional resolver source

### 5.5 Add observability

Runtime admin overview should expose:

- count of configured confirmation interceptors
- source config location
- maybe list of rule names

This is useful for platform verification.

Status: DONE

---

## 6) Platform Backend Plan

### 6.1 Keep storage in `actionsConfig`

The platform already stores draft/version JSON blobs:

- `actionsConfig`
- `entityConfig`
- `routingConfig`
- `providerConfig`
- `securityConfig`

Recommended V1:

- store `confirmationInterceptors` inside `actionsConfig`

Why:

- no new DB columns needed
- no new artifact needed
- simpler migration

### 6.2 Update draft validation

Extend:

- [DeploymentDraftValidationService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDraftValidationService.java)

Validation rules:

- every referenced action must exist in `actionsConfig.actions`
- `confirmation` must be supported enum
- `decision.type` must be supported enum
- `decision.action` required for action decisions
- `decision.message` required for reply decisions
- `onceParam` must be safe string
- `stack.popPreviousIfActionIn` actions must exist
- reject invalid template placeholders if possible

Status: DONE

### 6.3 Update artifact compiler

The compiler already emits:

- `ai-actions.yml`

It should now include:

- `confirmationInterceptors`

No new artifact is required for V1.

Status: DONE

### 6.4 Update bootstrap importer

The ecommerce demo bootstrap path should support injecting these rules so the restored demo can include the retention offer behavior automatically.

Status: DONE

---

## 7) UI Plan

### 7.1 Add editor under Actions

Best place in the current platform UI:

- `Actions` page

New section:

- `Confirmation policies`

Status: PENDING

### 7.2 Suggested UI model

Each rule should edit:

- rule name
- trigger action(s)
- confirmation type
- once-only guard
- decision type
- target action or reply
- parameter mapping
- stack behavior

### 7.3 Keep structured first

Use structured forms for:

- common retention / upsell flows

Keep a raw JSON/YAML advanced view only as fallback.

### 7.4 Good first UX

Provide templates like:

- `Cancel -> offer discount`
- `Cancel -> ask for support ticket first`
- `Reject offer -> continue original cancel`

This will make the feature understandable to operators.

---

## 8) Verification Plan

Add runtime verification checks for:

- configured interceptor count > 0 when expected
- runtime admin overview shows rule names or count

Status: DONE

Already implemented:

- runtime admin overview exposes confirmation interceptor count, names, and sources
- runtime actions overview exposes confirmation interceptor count, names, and sources
- platform release verification checks runtime confirmation interceptor count and rule-name alignment against the published `actionsConfig`
- `scripts/verify-ecommerce-deployment.sh` now verifies the expected interceptor rule names on runtime admin surfaces
- `scripts/verify-ecommerce-deployment.sh` can run an authenticated retention smoke that proves:
  - cancel -> confirmation required
  - confirm cancel -> retention offer confirmation
  - accept offer -> `offer_order_discount` executes
  - reject offer -> original `cancel_purchase_order` executes and the order becomes `CANCELLED`

---

## 9) Migration Plan For The Ecommerce Demo

To restore the old demo behavior in the productized system:

1. keep actions:
   - `cancel_purchase_order`
   - `offer_order_discount`
2. keep existing routing
3. add `confirmationInterceptors` rules to the ecommerce `ai-actions.yml`
4. update platform bootstrap/import path
5. publish/apply the deployment

That is enough to restore the business-rule behavior without custom app code inside the product runtime.

---

## 10) Phased Implementation

### Phase 1: Runtime Config-Driven Resolver

Build:

- schema
- config loader
- `ConfiguredConfirmationInterceptorsResolver`
- runtime admin visibility

Done when:

- runtime can perform cancel -> offer -> reject -> cancel using config only

Status: DONE

### Phase 2: Platform Backend Support

Build:

- draft validation
- artifact compiler support
- ecommerce bootstrap support

Done when:

- platform can store/publish/apply these rules

Status: DONE

### Phase 3: Platform UI

Build:

- structured confirmation policy editor in Actions page

Done when:

- operator can create retention flow rules without raw YAML edits

Status: PENDING

### Phase 4: Verification

Build:

- automated verification scenario for ecommerce retention flow

Done when:

- platform diagnostics can prove the configured interception behavior is active

Status: DONE

- DONE: config-level verification and runtime/admin alignment
- DONE: behavioral verification scenario that proves cancel -> offer -> accept/reject flows end to end

---

## 11) Recommendation

Recommended product direction:

- **restore the capability in the runtime**
- **express it declaratively in `actionsConfig`**
- **do not push it into REST connector routing**

This is the cleanest fit for the current architecture because:

- runtime owns confirmation state
- rest connector remains stateless
- platform can manage/version the behavior
- custom apps can still use annotation-based resolvers when needed

---

## 12) Follow-Up

After approval of this plan, the next implementation doc should be:

- `changes/Productization/CONFIGURED_CONFIRMATION_INTERCEPTORS_EXECUTION_PLAN.md`

That doc should define:

- exact YAML schema
- exact runtime classes to add
- validation rules
- UI screen changes
- ecommerce demo acceptance tests
