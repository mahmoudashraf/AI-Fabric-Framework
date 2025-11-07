# Action Handling & Delegation - Summary

## Your Question
**"How will we handle actions in handleSingleIntent and handleCompoundIntents? Should we delegate to library user?"**

---

## The Answer: YES - Use Hybrid Delegation Pattern

### Library Handles
```
✅ Intent extraction
✅ Orchestration logic
✅ Sequential/parallel execution
✅ Routing to ActionHandler
✅ Error propagation
```

### User Implements
```
✅ Action execution (actual business logic)
✅ Permission validation
✅ Error handling
✅ Result merging
✅ Confirmation messages
```

---

## Architecture

```
RAGOrchestrator (Library)
    ↓
    ├─ Extract intents
    ├─ Route by type
    │
    ├─ ACTION intent?
    │   └─ Call actionHandler.executeAction()  ← DELEGATION
    │       └─ User's code executes
    │           └─ Returns ActionResult
    │
    └─ INFORMATION intent?
        └─ Call RAGService
            └─ Returns generated answer
```

---

## 3 Key Delegation Points

### 1️⃣ Single Action Handler
```
handleSingleIntent(intent)
    ├─ if ACTION type:
    │   ├─ actionHandler.validateActionAllowed()
    │   ├─ actionHandler.getConfirmationMessage()
    │   └─ actionHandler.executeAction()
    └─ if INFORMATION type:
        └─ ragService.retrieveAndGenerate()
```

### 2️⃣ Compound Actions Handler
```
handleCompoundIntents(intents)
    ├─ For each intent:
    │   └─ handleSingleIntent()  (calls user's code)
    │
    └─ actionHandler.mergeResults()  ← USER MERGES RESULTS
```

### 3️⃣ Error Handler
```
executeAction() throws exception
    ├─ catch block
    └─ actionHandler.handleError()  ← USER HANDLES ERROR
```

---

## Implementation Steps

### Step 1: Create Interface (20 min)
```java
public interface ActionHandler {
    ActionResult executeAction(...);        // User implements
    ActionResult mergeResults(...);         // User implements
    ActionResult handleError(...);          // User implements
    boolean validateActionAllowed(...);     // User implements
    String getConfirmationMessage(...);     // User implements
}
```

### Step 2: Create DTOs (40 min)
```java
ActionResult {
    ActionStatus status;
    String message;
    Map<String, Object> data;
    String errorCode;
}

OrchestrationResult {
    String type;
    String message;
    boolean success;
    // ... more fields
}
```

### Step 3: Update Orchestrator (60 min)
```java
RAGOrchestrator {
    private ActionHandler actionHandler;  // ← Injected
    
    private handleSingleIntent(intent) {
        if (ACTION) {
            actionHandler.validateActionAllowed();  // ← Call user
            actionHandler.executeAction();          // ← Call user
        }
    }
    
    private handleCompoundIntents(intents) {
        actionHandler.mergeResults();  // ← Call user
    }
}
```

### Step 4: User Implements (60 min)
```java
@Component
public class ActionHandlerImpl implements ActionHandler {
    @Autowired
    private SubscriptionService subscriptionService;
    
    @Override
    public ActionResult executeAction(...) {
        switch(actionName) {
            case "cancel_subscription":
                subscriptionService.cancel(...);
                return ActionResult.success(...);
        }
    }
}
```

---

## Flow Diagram

```
User: "Cancel my subscription"
    ↓
orchestrator.orchestrate()
    ├─ Extract intents
    │   └─ Intent: type=ACTION, action=cancel_subscription
    │
    ├─ handleSingleIntent()
    │   ├─ actionHandler.validateActionAllowed()
    │   │   └─ User checks: does user own subscription?
    │   │
    │   ├─ actionHandler.getConfirmationMessage()
    │   │   └─ User returns: "Are you sure?"
    │   │
    │   └─ actionHandler.executeAction()
    │       └─ User code:
    │           ├─ subscriptionService.cancel()
    │           └─ return ActionResult.success()
    │
    └─ return response to user
        └─ "Subscription cancelled"
```

---

## Key Benefits

### For Library Users
✅ **Simple** - Just implement 5 methods
✅ **Flexible** - Customize all behavior
✅ **Testable** - Mock ActionHandler easily
✅ **Clear** - Obvious what to implement
✅ **Type-safe** - Use DTOs

### For Library Developers
✅ **Clean** - Separation of concerns
✅ **Decoupled** - No knowledge of user's services
✅ **Extensible** - Can add methods later
✅ **Reusable** - Same pattern for all users

---

## Example: Single Action

### User Query
```
"Cancel my subscription"
```

### Flow
```
Step 1: Extract Intent
  └─ IntentQueryExtractor
      └─ LLM decides: ACTION, action=cancel_subscription

Step 2: Validate
  └─ actionHandler.validateActionAllowed()
      └─ User code: Check user owns subscription
          └─ Return: true/false

Step 3: Confirm
  └─ actionHandler.getConfirmationMessage()
      └─ User code: Get confirmation text
          └─ Return: "Are you sure?"

Step 4: Execute
  └─ actionHandler.executeAction()
      └─ User code:
          ├─ Call SubscriptionService.cancel()
          ├─ Log audit trail
          └─ Return: ActionResult.success()

Step 5: Return
  └─ Build response
      └─ Return to user: "Subscription cancelled"
```

---

## Example: Compound Actions

### User Query
```
"Cancel my subscription and process my refund"
```

### Flow
```
Step 1: Extract Intents
  └─ Two intents:
      ├─ ACTION: cancel_subscription
      └─ ACTION: request_refund

Step 2: Execute Sequential
  ├─ handleSingleIntent(cancel_subscription)
  │   └─ actionHandler.executeAction()
  │       └─ User executes: cancel subscription
  │           └─ Result 1: success
  │
  └─ handleSingleIntent(request_refund)
      └─ actionHandler.executeAction()
          └─ User executes: request refund
              └─ Result 2: success

Step 3: Merge Results
  └─ actionHandler.mergeResults()
      └─ User code: Combine both results
          └─ Return: Merged success message

Step 4: Return
  └─ Response: "Subscription cancelled and refund requested"
```

---

## Delegation Breakdown

| Phase | Library | User |
|-------|---------|------|
| Extract Intent | ✅ | |
| Validate Permission | | ✅ |
| Get Confirmation | | ✅ |
| Execute Action | | ✅ |
| Handle Error | | ✅ |
| Merge Results | | ✅ |
| Build Response | ✅ | |

---

## Testing Strategy

### Unit Test User Code
```java
@Test
void shouldExecuteCancelSubscription() {
    ActionResult result = actionHandler.executeAction(
        "cancel_subscription",
        Map.of("subscriptionId", "sub-123"),
        null,
        "user-123"
    );
    
    assertThat(result.getStatus()).isEqualTo(SUCCESS);
}
```

### Integration Test Orchestrator
```java
@Test
void shouldHandleActionIntent() {
    OrchestrationResult result = orchestrator.orchestrate(
        "Cancel my subscription",
        "user-123"
    );
    
    assertThat(result.isSuccess()).isTrue();
}
```

---

## Why This Pattern Works

### For Simple Queries
```
"Cancel subscription" → Execute action → Return result
✅ Fast and clean
```

### For Complex Queries
```
"Cancel + refund + update address" 
  → Extract 3 intents 
  → Execute each 
  → Merge results 
  → Return combined answer
✅ Still clean and maintainable
```

### For Error Cases
```
"Cancel subscription" (not owned by user)
  → Validate fails
  → Return: "You can't cancel this"
✅ User code handles all edge cases
```

---

## Configuration

```yaml
ai:
  orchestration:
    action:
      timeout-seconds: 30
      require-confirmation-for-high-risk: true
      high-risk-actions:
        - cancel_subscription
        - request_refund
    compound:
      strategy: sequential  # sequential, parallel, merged
      max-parallel: 4
```

---

## Files to Create

1. **ActionHandler.java** (20 min)
   - Interface users implement

2. **ActionResult.java** (20 min)
   - Result DTO

3. **OrchestrationResult.java** (20 min)
   - Orchestration result DTO

4. **RAGOrchestrator.java** (60 min)
   - Updated orchestrator with delegation

5. **ActionHandlerImpl.java** (60 min)
   - User implements their logic

---

## Total Implementation: 3 Hours

- Create interfaces & DTOs: 60 min
- Update orchestrator: 60 min
- User implementation: 60 min
- Testing & verification: Included

---

## Success Indicators

After implementation:

✅ Actions execute correctly
✅ Permissions validated
✅ Confirmation works
✅ Errors handled gracefully
✅ Compound intents merged properly
✅ All tests pass
✅ Production ready

---

## One More Thing

### Scalability

Adding new action is **trivial**:

1. User adds case in `executeAction()`
2. Implements business logic
3. Done! Library handles rest

No changes to library code needed.

---

## Comparison

### Without Delegation
```
❌ Library knows about all user services
❌ Hard to extend
❌ Tight coupling
❌ Complex to maintain
```

### With Delegation (Recommended)
```
✅ Library generic and reusable
✅ Easy to extend
✅ Loose coupling
✅ Simple to maintain
✅ Professional architecture
```

---

## Conclusion

**Hybrid Delegation Pattern:**
- Library: Handles orchestration
- User: Handles action execution
- Interface: Clear contract
- Result: Production-ready, extensible system

**This is enterprise-grade!** 🚀

---

## Next Steps

1. **Read:** `ACTION_HANDLING_DELEGATION_STRATEGY.md` (complete)
2. **Implement:** `ACTION_HANDLING_IMPLEMENTATION_GUIDE.md` (3 hours)
3. **Test:** Write comprehensive tests
4. **Deploy:** Go to production
5. **Monitor:** Track execution metrics

---

**Ready to build? Start with the implementation guide! 🚀**

