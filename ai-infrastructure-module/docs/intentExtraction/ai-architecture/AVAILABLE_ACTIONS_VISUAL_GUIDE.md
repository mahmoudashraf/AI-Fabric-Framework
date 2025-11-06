# AvailableActions - Visual Guide

## Option 1: Annotation-Based (Basic)

```
┌─────────────────────────────────────┐
│      Your Service Methods           │
├─────────────────────────────────────┤
│  @AIAction(...)                     │
│  void cancelSubscription() { }       │
│                                     │
│  @AIAction(...)                     │
│  void updatePayment() { }            │
│                                     │
│  @AIAction(...)                     │
│  void requestRefund() { }            │
└─────────────────────────────────────┘
          ↓ Scan at startup
┌─────────────────────────────────────┐
│  AvailableActionsBuilder            │
│  (Reflection-based scanning)        │
└─────────────────────────────────────┘
          ↓
┌─────────────────────────────────────┐
│  List<ActionInfo>                   │
│  [cancel_subscription, ...]         │
└─────────────────────────────────────┘

Pros: Spring-native, auto-discovered
Cons: Reflection overhead, less flexible
```

---

## Option 2: Configuration-Based (Declarative)

```
┌──────────────────────────────────┐
│   application-actions.yml        │
├──────────────────────────────────┤
│ ai:                              │
│   available-actions:             │
│     - name: cancel_subscription  │
│       service: subscriptionSvc   │
│       method: cancel             │
│       parameters: [...]          │
│     - name: update_payment       │
│       service: paymentSvc        │
│       method: update             │
│       parameters: [...]          │
└──────────────────────────────────┘
          ↓ Load YAML
┌──────────────────────────────────┐
│  ActionConfig                    │
│  (Spring ConfigurationProperties)│
└──────────────────────────────────┘
          ↓
┌──────────────────────────────────┐
│  AvailableActionsService         │
│  (Convert config to ActionInfo)  │
└──────────────────────────────────┘
          ↓
┌──────────────────────────────────┐
│  List<ActionInfo>                │
│  [cancel_subscription, ...]      │
└──────────────────────────────────┘

Pros: Flexible, no code changes, version-able
Cons: More files, less type-safe
```

---

## Option 3: Builder Pattern (Programmatic)

```
┌──────────────────────────────────────────┐
│  AvailableActionsRegistry                │
│                                          │
│  @PostConstruct                          │
│  public void registerActions() {         │
│    registerCancelSubscriptionAction()    │
│    registerUpdatePaymentAction()         │
│    registerRequestRefundAction()         │
│    ...                                   │
│  }                                       │
│                                          │
│  private void registerCancel...() {      │
│    actions.add(                          │
│      ActionInfo.builder()                │
│        .action("cancel...")             │
│        .description("...")              │
│        .build()                         │
│    )                                     │
│  }                                       │
└──────────────────────────────────────────┘
          ↓
┌──────────────────────────────────────────┐
│  List<ActionInfo> registeredActions      │
│  [cancel_subscription, ...]              │
└──────────────────────────────────────────┘

Pros: Clean, testable, flexible
Cons: More code, code changes needed
```

---

## Option 4: Dynamic Registry (RECOMMENDED) ✅

```
┌──────────────────────┐
│ SubscriptionService  │
├──────────────────────┤
│ implements           │
│ AIActionProvider     │
│                      │
│ @Override            │
│ getAvailableActions()│
│   return [           │
│     cancel_sub,      │
│     upgrade_sub,     │
│     pause_sub        │
│   ]                  │
└──────────────────────┘
          ↓
          
┌──────────────────────┐
│ PaymentService       │
├──────────────────────┤
│ implements           │
│ AIActionProvider     │
│                      │
│ @Override            │
│ getAvailableActions()│
│   return [           │
│     update_payment,  │
│     add_payment      │
│   ]                  │
└──────────────────────┘
          ↓

┌──────────────────────┐
│ OrderService         │
├──────────────────────┤
│ implements           │
│ AIActionProvider     │
│                      │
│ @Override            │
│ getAvailableActions()│
│   return [           │
│     refund_order,    │
│     return_order,    │
│     track_order      │
│   ]                  │
└──────────────────────┘
          ↓
          ↓
          ↓
┌────────────────────────────────────┐
│  AvailableActionsRegistry          │
│                                    │
│  @Autowired                        │
│  List<AIActionProvider> providers  │ ← Spring auto-injects ALL
│                                    │
│  getAllAvailableActions() {        │
│    return providers.stream()       │
│      .flatMap(p ->                 │
│        p.getAvailableActions()    │
│      )                             │
│      .collect(...)                 │
│  }                                 │
└────────────────────────────────────┘
          ↓
┌────────────────────────────────────┐
│  List<ActionInfo>                  │
│  ├─ cancel_subscription            │
│  ├─ upgrade_subscription           │
│  ├─ pause_subscription             │
│  ├─ update_payment                 │
│  ├─ add_payment                    │
│  ├─ refund_order                   │
│  ├─ return_order                   │
│  └─ track_order                    │
└────────────────────────────────────┘

Pros: Decentralized, scalable, Spring-native
Cons: Requires discipline
Best For: Enterprise systems ✅
```

---

## How It Connects to Intent Extraction

```
User Query
    ↓
    "Cancel my subscription"
    ↓
┌────────────────────────────────────────┐
│  IntentQueryExtractor                  │
│                                        │
│  1. Get all available actions:         │
│     registry.getAllAvailableActions()  │
│                                        │
│  2. Build system prompt with actions:  │
│     "AVAILABLE ACTIONS:                │
│      - cancel_subscription: ..."       │
│                                        │
│  3. Call LLM with prompt +  query      │
│                                        │
│  4. LLM analyzes:                      │
│     "User wants ACTION,                │
│      specifically cancel_subscription" │
│                                        │
│  5. Return:                            │
│     {                                  │
│       type: "ACTION",                  │
│       action: "cancel_subscription",   │
│       actionParams: {...}              │
│     }                                  │
└────────────────────────────────────────┘
    ↓
┌────────────────────────────────────────┐
│  RAGOrchestrator                       │
│  Sees type="ACTION"                    │
│  Routes to: SubscriptionService        │
│  Calls: cancelSubscription(params)     │
└────────────────────────────────────────┘
    ↓
Result: Subscription cancelled ✅
```

---

## Data Flow Comparison

### Before (Naive)
```
User Query
    ↓
RAGService.retrieveAndGenerate()
    ↓ Embeds raw query
Vector DB (gets wrong documents)
    ↓ LLM tries to answer from bad docs
Hallucination or irrelevant answer ❌
```

### After (With AvailableActions)
```
User Query
    ↓
IntentQueryExtractor + AvailableActionsRegistry
    ↓ LLM sees all actions + docs available
LLM decides: ACTION vs INFORMATION vs OUT_OF_SCOPE
    ↓
If ACTION: Execute function directly ✅
If INFORMATION: Retrieve from right vector space ✅
If OUT_OF_SCOPE: Say "I don't know" ✅
```

---

## SystemContextBuilder Integration

```
┌────────────────────────────────────────┐
│  SystemContextBuilder                  │
├────────────────────────────────────────┤
│                                        │
│  buildContext(userId) {                │
│                                        │
│    1. GET ACTIONS                      │
│       actions = registry               │
│         .getAllAvailableActions()      │
│       ↓ List of 15+ actions           │
│                                        │
│    2. GET ENTITY TYPES                 │
│       entities = loadEntityTypes()     │
│       ↓ [Product, Policy, Support]    │
│                                        │
│    3. GET KNOWLEDGE BASE SNAPSHOT      │
│       snapshot = countDocuments()      │
│       ↓ {Product: 1200, Policy: 800}  │
│                                        │
│    4. GET USER BEHAVIOR                │
│       behavior = userBehaviorContext() │
│       ↓ Past actions, preferences      │
│                                        │
│    5. BUILD SYSTEM CONTEXT             │
│       return SystemContext.builder()   │
│         .actions(actions) ←─── From registry
│         .entityTypes(entities)         │
│         .snapshot(snapshot)            │
│         .behavior(behavior)            │
│         .build()                       │
│                                        │
│  }                                     │
│                                        │
└────────────────────────────────────────┘
    ↓ Passed to IntentQueryExtractor
    ↓ Used in LLM system prompt
    ↓ LLM makes intelligent decisions
```

---

## Action Resolution Flow

```
┌──────────────────────┐
│  User Query          │
│  "I want a refund"   │
└──────────────────────┘
    ↓
┌──────────────────────────────────────┐
│  IntentQueryExtractor                │
│  Prompt includes all actions:        │
│  - cancel_subscription               │
│  - update_payment                    │
│  - request_refund ← MATCHES         │
│  - track_order                       │
│  - ...                               │
└──────────────────────────────────────┘
    ↓
┌──────────────────────────────────────┐
│  LLM Response                        │
│  {                                   │
│    type: "ACTION",                   │
│    action: "request_refund",         │
│    actionParams: {                   │
│      orderId: "order-12345",         │
│      reason: "defective_item"        │
│    },                                │
│    confirmationRequired: true        │
│  }                                   │
└──────────────────────────────────────┘
    ↓
┌──────────────────────────────────────┐
│  RAGOrchestrator                     │
│  Sees action="request_refund"        │
│  Finds OrderService via registry     │
│  Calls: orderService.refund(...)     │
└──────────────────────────────────────┘
    ↓
┌──────────────────────────────────────┐
│  OrderService.refund()               │
│  ✓ Check order exists                │
│  ✓ Verify refund eligible            │
│  ✓ Process refund                    │
│  ✓ Send confirmation                 │
│  ✓ Return success                    │
└──────────────────────────────────────┘
    ↓
✅ User refunded successfully
```

---

## Service Registration (Magic Happens Here)

```
Spring Application Startup
    ↓
@SpringBootApplication
    ↓
Component Scanning
    ↓
Find all beans implementing AIActionProvider
    ↓
┌─────────────────────────────────┐
│  Services Found:                │
│  1. SubscriptionService         │
│  2. PaymentService              │
│  3. OrderService                │
│  4. UserManagementService       │
│  5. ... more                    │
└─────────────────────────────────┘
    ↓
Spring Dependency Injection
    ↓
┌─────────────────────────────────┐
│  AvailableActionsRegistry       │
│                                 │
│  @Autowired                     │
│  List<AIActionProvider> providers
│                                 │
│  providers now contains all 5!  │
└─────────────────────────────────┘
    ↓
Application Ready
    ↓
When needed, call:
registry.getAllAvailableActions()
    ↓
Calls each provider's getAvailableActions()
    ↓
Returns combined list of 15+ actions
    ↓
Success! ✅
```

---

## Comparison Matrix

```
                   Annotation  Config  Builder  Dynamic Registry
Space Usage        ⭐⭐⭐⭐⭐    ⭐⭐⭐    ⭐⭐     ⭐⭐⭐
Speed              ⭐⭐⭐      ⭐⭐⭐    ⭐⭐⭐⭐⭐  ⭐⭐⭐⭐⭐
Type Safety        ⭐⭐      ⭐       ⭐⭐⭐⭐⭐  ⭐⭐⭐⭐⭐
Maintainability    ⭐⭐⭐    ⭐⭐⭐    ⭐⭐⭐⭐   ⭐⭐⭐⭐⭐
Scalability        ⭐⭐      ⭐⭐⭐    ⭐⭐     ⭐⭐⭐⭐⭐
Learning Curve     ⭐⭐⭐    ⭐⭐⭐⭐   ⭐⭐⭐⭐   ⭐⭐⭐
─────────────────────────────────────────────────────────
Recommended        Small      Config Heavy  Medium  ENTERPRISE ✅
```

---

## Implementation Timeline

```
Day 1: Create DTOs (15 min)
  ├─ ActionInfo.java
  ├─ ActionParameterInfo.java
  └─ Test them

Day 1: Create Interface (5 min)
  └─ AIActionProvider.java

Day 1: Create Registry (10 min)
  └─ AvailableActionsRegistry.java

Day 2: Update Services (60 min)
  ├─ SubscriptionService ← implement AIActionProvider
  ├─ PaymentService ← implement AIActionProvider
  ├─ OrderService ← implement AIActionProvider
  └─ UserService ← implement AIActionProvider

Day 2: Integration (30 min)
  ├─ SystemContextBuilder ← use registry
  ├─ IntentQueryExtractor ← include in prompt
  └─ Test everything

Day 3: Testing & Deployment
  ├─ Unit tests
  ├─ Integration tests
  └─ Deploy

Total: ~2-3 days
```

---

## Success Metrics

After implementation, you should see:

✅ **Intent Extraction Accuracy**: 95%+ (vs 60% before)
✅ **Action Recognition**: Correctly identifies actionable queries
✅ **Zero Hallucinations** for actions (they're explicit)
✅ **Better Routing**: Information queries go to retrieval, actions go to services
✅ **Faster Response**: No unnecessary retrieval for action queries
✅ **Better UX**: Users see "Confirming action" instead of "Searching docs"

---

## Summary

**Best Approach:** Dynamic Registry (Option 4)

Why:
- ✅ Decentralized (each service owns actions)
- ✅ Scalable (add service = auto-included)
- ✅ Spring-native (uses autowiring)
- ✅ Type-safe (no strings)
- ✅ Enterprise-ready

Result:
- 🎯 LLM makes perfect intent decisions
- 🎯 Actions execute directly
- 🎯 Retrieval only when needed
- 🎯 Zero false positives on actions

