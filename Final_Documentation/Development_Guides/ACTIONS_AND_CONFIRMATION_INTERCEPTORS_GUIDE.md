# Actions + Confirmation Interceptors (V5) — Developer Guide

This guide explains how to:
- implement **actions** using the new annotation API (`@AIAction`, `@ActionExecute`, …)
- implement **confirmation interceptors** (intent resolvers) for conversation flows like retention offers

This is **greenfield**: the legacy `ActionHandler` API is removed and not supported.

---

## 1) Concepts (What runs where)

### Actions
An “action” is a Spring bean annotated with `@AIAction`. The framework discovers these beans at startup and:
- exposes their metadata to the LLM during intent extraction
- executes them via the orchestration pipeline when the LLM selects an action intent

Discovery and execution are backed by:
- `AIActionRegistry` (runtime discovery + lookup)
- `AnnotatedAIActionHandler` (invocation adapter)

### Confirmation interceptors (“intent resolvers”)
When chat sessions are enabled, a dedicated pipeline step resolves confirmation intents (e.g. *yes/no*) into the **correct action execution**.

This is implemented in:
- `ConfirmationResolutionStep` (runs before action execution in the pipeline)
- `IntentResolver` SPI (your interceptors)
- `ConfirmationResolverSupport` (helper base class for writing resolvers)

---

## 2) Enablement (Apps)

### Actions
Nothing special is required beyond normal Spring component scanning:
- `@AIAction` is meta-annotated with `@Component`, so the bean is auto-registered.
- Your app must compile with `-parameters` (recommended) or you must explicitly name parameters with `@Param("name")`.

### Chat confirmations + resolvers
To enable conversation-aware confirmation handling:
1. Include the chat-session module (the app uses it; the core framework remains usable without it).
2. Set:

```yaml
ai:
  chat:
    enabled: true
```

Resolvers are just Spring beans that implement `IntentResolver`.

---

## 3) Writing an Action (Recommended pattern)

### Minimal example

```java
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;

@AIAction(
    name = "add_to_cart",
    description = "Add a product SKU to the current cart",
    category = "commerce",
    requiresConfirmation = true
)
public class AddToCartAction {

    @ActionExecute
    public ActionResult execute(
        @Param(required = true, description = "Product SKU") String sku,
        @Param(required = true, description = "Quantity", min = 1, max = 100) Integer quantity,
        ActionContext ctx
    ) {
        // business logic…
        return ActionResult.builder()
            .success(true)
            .message("Added to cart")
            .data(java.util.Map.of("sku", sku, "quantity", quantity))
            .build();
    }
}
```

### Optional: custom confirmation message
If you omit `@ActionConfirmation`, the framework auto-generates a basic confirmation prompt.

```java
import com.ai.infrastructure.intent.action.annotation.ActionConfirmation;

@ActionConfirmation
public String confirm(
    @Param(required = true) String sku,
    @Param(required = true) Integer quantity
) {
    return "Add " + quantity + " × " + sku + " to your cart?";
}
```

### Optional: access control (fail-closed)
Use `@ActionAllowed` to guard actions. This runs **before confirmation and before execution**.

```java
import com.ai.infrastructure.intent.action.annotation.ActionAllowed;

@ActionAllowed
public boolean allowed(ActionContext ctx) {
    return ctx != null && ctx.userId() != null && !ctx.userId().isBlank();
}
```

If this returns `false`, the orchestrator returns `ACTION_DENIED`.

### Optional: “facts for LLM” after action execution
Use `@ActionFacts` to provide post-action structured facts for the LLM (used by post-action generation logic).

```java
import com.ai.infrastructure.intent.action.annotation.ActionFacts;

@ActionFacts
public java.util.Map<String, Object> facts(ActionResult result, ActionContext ctx) {
    return java.util.Map.of(
        "userId", ctx != null ? ctx.userId() : null,
        "success", result != null && result.isSuccess()
    );
}
```

---

## 4) Parameter binding & validation rules

Parameters are extracted from the LLM’s `actionParams` and converted to your method’s types.

### Supported “context injection”
You can declare any of these parameters without `@Param`:
- `ActionContext`
- `OrchestrationContext`
- `PipelineContext`

### `@Param` validation
Validation is enforced by the argument binder:
- `required = true` → missing/empty values are rejected
- `pattern = "..."` → regex validation (string form)
- `allowedValues = {...}` → enum-style validation (case-insensitive)
- `min` / `max` → numeric range validation

**Important:** required parameters should be declared on the `@ActionExecute` method so the framework can:
- show them in action metadata (for LLM selection)
- return `CLARIFICATION_REQUIRED` before asking for confirmation

---

## 5) Confirmation interceptors (IntentResolvers)

### When should you write a resolver?
Write an app resolver when you need conversation-specific control, for example:
- a retention offer (“instead of cancelling, apply 10% discount?”)
- multi-step confirmations across several actions
- special handling for compound confirmations (“yes and also …”)

### Resolver interface
Implement `IntentResolver`:
- `canResolve(...)` decides whether your resolver handles the current turn
- `resolve(...)` returns an updated `PipelineContext`

Resolvers are executed by `ConfirmationResolutionStep` in ascending priority (lower = earlier).

### Use `ConfirmationResolverSupport`
Extend `ConfirmationResolverSupport` to avoid boilerplate for:
- pending action stack ops (`peekPending`, `popPending`, `pushPending`)
- checking confirmation intents (`hasPositiveConfirmation`, `hasNegativeConfirmation`)
- marking an action confirmed for the current request (`markConfirmed`)

### Template: retention offer interceptor
High-level approach:
1. Detect a *confirmed* cancel intent.
2. Swap it into an “offer” action instead of executing cancel immediately.
3. If offer is rejected, execute the original cancellation (or re-prompt it) explicitly.

Skeleton:

```java
public class CancellationRetentionOfferResolver extends ConfirmationResolverSupport {

    public CancellationRetentionOfferResolver(PendingActionStore store) {
        super(store);
    }

    @Override
    public boolean canResolve(MultiIntentResponse response, Map<String, Object> sessionMetadata, PipelineContext ctx) {
        PendingAction pending = peekPending(ctx);
        return pending != null
            && "cancel_order".equalsIgnoreCase(pending.action())
            && hasPositiveConfirmation(response);
    }

    @Override
    public PipelineContext resolve(MultiIntentResponse response, Map<String, Object> sessionMetadata, PipelineContext ctx) {
        // Implementation detail depends on your UX:
        // - pop the cancel pending action
        // - push an offer action as the new pending action (or return an offer ACTION intent)
        // - ensure the original cancel action is still available if the offer is rejected
        return ctx;
    }

    @Override
    public int getPriority() {
        return 1; // run before the default confirmation resolvers
    }

    @Override
    public String getResolverName() {
        return "CancellationRetentionOfferResolver";
    }
}
```

See built-in resolvers for patterns:
- `CompoundConfirmationResolver`
- `SingleConfirmationPositiveResolver`
- `ExpiredConfirmationResolver`

---

## 6) Testing guidance

### Unit tests (actions)
- Validate your action’s business behavior by calling the `@ActionExecute` method directly.
- If you need registry/discovery coverage, use `AIActionRegistryTest` patterns (Spring context + `@AIAction` beans).

### Integration tests (resolvers + chat)
- Use `@SpringBootTest` with minimal component scanning.
- Avoid scanning the entire `com.ai.infrastructure.it` package; broad scanning can unintentionally pick up test actions and cause duplicate names at startup.

---

## References
- Change plan: `changes/DEVELOPER_EXPERIENCE_ACTIONS_AND_INTENT_RESOLVERS_CHANGE_PLAN.md`
- DX improvement plan: `ai-infrastructure-module/docs/DEVELOPER_EXPERIENCE_IMPROVEMENT_PLAN.md`
