# Building AvailableActions - Best Options & Strategies

## The Question
**"What are the best options to build AvailableActions?"**

= "How should we define what functions/actions the system can execute and expose them to the LLM?"

---

## Your Current State

### What You Have
1. **BehaviorType enum** - 50+ behavior types (tracking user actions)
2. **Service layer** - Multiple services executing business logic
3. **Adapters** - Services like UserAIService, OrderAIService, ProductAIService
4. **Spring framework** - Full dependency injection available

### What You Need
**AvailableActions** for the LLM to know:
- What functions can be called
- What parameters they need
- What they do
- Examples of when to use them

---

## Option 1: Annotation-Based Approach (RECOMMENDED FOR SPRING) ✅

### Concept
Use Spring annotations to mark methods as "available actions" that can be called by the LLM.

### Implementation

```java
// Step 1: Create annotation
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AIAction {
    String name();
    String description();
    String[] parameters();
    String[] parameterDescriptions();
    boolean requiresConfirmation() default false;
    String[] examples() default {};
}

// Step 2: Mark methods as AI-callable
@Service
public class UserService {
    
    @AIAction(
        name = "cancel_subscription",
        description = "Cancel user's subscription immediately",
        parameters = {"userId", "reason"},
        parameterDescriptions = {
            "The unique user ID",
            "Reason for cancellation (optional)"
        },
        requiresConfirmation = true,
        examples = {
            "Cancel my subscription",
            "I want to stop my membership",
            "Unsubscribe me"
        }
    )
    public void cancelSubscription(String userId, String reason) {
        // Implementation
    }
    
    @AIAction(
        name = "update_payment_method",
        description = "Update user's payment method",
        parameters = {"userId", "paymentType", "cardDetails"},
        parameterDescriptions = {
            "The unique user ID",
            "Type of payment (credit_card, debit_card, paypal)",
            "Payment details as JSON"
        },
        requiresConfirmation = true
    )
    public void updatePaymentMethod(String userId, String paymentType, String cardDetails) {
        // Implementation
    }
}

// Step 3: Auto-scan and build AvailableActions
@Service
public class AvailableActionsBuilder {
    
    @Autowired
    private ApplicationContext applicationContext;
    
    public List<ActionInfo> buildAvailableActions() {
        List<ActionInfo> actions = new ArrayList<>();
        
        // Scan all beans
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);
            Method[] methods = bean.getClass().getDeclaredMethods();
            
            for (Method method : methods) {
                AIAction annotation = method.getAnnotation(AIAction.class);
                
                if (annotation != null) {
                    // Convert annotation to ActionInfo
                    ActionInfo action = ActionInfo.builder()
                        .action(annotation.name())
                        .description(annotation.description())
                        .requiredParams(Arrays.asList(annotation.parameters()))
                        .confirmationRequired(annotation.requiresConfirmation())
                        .examples(Arrays.asList(annotation.examples()))
                        .build();
                    
                    actions.add(action);
                }
            }
        }
        
        return actions;
    }
}
```

### Advantages
✅ Declarative (define action with method)
✅ Spring-native (uses annotations)
✅ Easy to maintain (change = update annotation)
✅ Type-safe (method signature = contract)
✅ Auto-discovered (scan at startup)

### Disadvantages
❌ Tied to Spring
❌ Reflection overhead
❌ Limited flexibility

---

## Option 2: Configuration-Based Approach (RECOMMENDED FOR FLEXIBILITY) ✅

### Concept
Define available actions in a configuration file (YAML/JSON), then wire them to services.

### Implementation

```yaml
# application-actions.yml
ai:
  available-actions:
    - name: cancel_subscription
      description: "Cancel user's subscription"
      service: userService
      method: cancelSubscription
      parameters:
        - name: userId
          type: String
          description: "The unique user ID"
        - name: reason
          type: String
          description: "Reason for cancellation"
          required: false
      confirmation-required: true
      examples:
        - "Cancel my subscription"
        - "I want to stop my membership"
    
    - name: update_shipping_address
      description: "Update user's shipping address"
      service: userService
      method: updateShippingAddress
      parameters:
        - name: userId
          type: String
          description: "The unique user ID"
        - name: address
          type: String
          description: "New shipping address"
        - name: city
          type: String
          description: "City"
        - name: state
          type: String
          description: "State code"
        - name: zip
          type: String
          description: "Zip code"
      confirmation-required: true
      examples:
        - "Update my shipping address"
        - "Change my delivery address"
    
    - name: request_refund
      description: "Request refund for an order"
      service: orderService
      method: requestRefund
      parameters:
        - name: orderId
          type: String
          description: "The order ID"
        - name: reason
          type: String
          description: "Reason for refund"
      confirmation-required: true
      examples:
        - "I want a refund"
        - "Return my order"
```

```java
// ActionConfig.java
@Configuration
@ConfigurationProperties(prefix = "ai.available-actions")
public class ActionConfig {
    
    private List<ActionDefinition> actions;
    
    // Getters/setters
    public static class ActionDefinition {
        private String name;
        private String description;
        private String service;
        private String method;
        private List<ParameterDefinition> parameters;
        private boolean confirmationRequired;
        private List<String> examples;
        
        // Getters/setters
    }
    
    public static class ParameterDefinition {
        private String name;
        private String type;
        private String description;
        private boolean required;
        
        // Getters/setters
    }
}

// AvailableActionsService.java
@Service
public class AvailableActionsService {
    
    @Autowired
    private ActionConfig actionConfig;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    public List<ActionInfo> getAvailableActions() {
        List<ActionInfo> actions = new ArrayList<>();
        
        for (ActionConfig.ActionDefinition def : actionConfig.getActions()) {
            // Convert config to ActionInfo
            List<ActionParameterInfo> params = def.getParameters().stream()
                .map(p -> ActionParameterInfo.builder()
                    .name(p.getName())
                    .type(p.getType())
                    .description(p.getDescription())
                    .required(p.isRequired())
                    .build())
                .collect(Collectors.toList());
            
            ActionInfo action = ActionInfo.builder()
                .action(def.getName())
                .description(def.getDescription())
                .requiredParams(params.stream()
                    .filter(ActionParameterInfo::isRequired)
                    .map(ActionParameterInfo::getName)
                    .collect(Collectors.toList()))
                .confirmationRequired(def.isConfirmationRequired())
                .examples(def.getExamples())
                .parameters(params)
                .build();
            
            actions.add(action);
        }
        
        return actions;
    }
    
    // Execute action by name
    public Object executeAction(String actionName, Map<String, Object> parameters) {
        ActionConfig.ActionDefinition def = actionConfig.getActions().stream()
            .filter(a -> a.getName().equals(actionName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionName));
        
        Object service = applicationContext.getBean(def.getService());
        Method method = service.getClass().getMethod(def.getMethod(), getParameterTypes(def));
        
        Object[] args = buildArguments(def, parameters);
        return method.invoke(service, args);
    }
}
```

### Advantages
✅ Decoupled from code
✅ Easy to add/remove actions
✅ Non-technical people can configure
✅ Hot-reload friendly (no code changes)
✅ Version-able in git

### Disadvantages
❌ More files to maintain
❌ Strings everywhere (less type-safe)
❌ Harder to evolve

---

## Option 3: Builder Pattern (MOST FLEXIBLE) ✅

### Concept
Programmatically build available actions using a fluent API.

### Implementation

```java
@Service
public class AvailableActionsRegistry {
    
    private final List<ActionInfo> registeredActions = new ArrayList<>();
    
    @PostConstruct
    public void registerActions() {
        // Register all available actions
        
        registerCancelSubscriptionAction();
        registerUpdatePaymentAction();
        registerRequestRefundAction();
        registerUpdateShippingAction();
    }
    
    private void registerCancelSubscriptionAction() {
        registeredActions.add(
            ActionInfo.builder()
                .action("cancel_subscription")
                .description("Cancel user's subscription")
                .requiredParams(List.of("subscriptionId"))
                .confirmationRequired(true)
                .examples(List.of(
                    "Cancel my subscription",
                    "Stop my membership",
                    "Unsubscribe me"
                ))
                .parameters(List.of(
                    ActionParameterInfo.builder()
                        .name("subscriptionId")
                        .type("String")
                        .description("The unique subscription ID")
                        .required(true)
                        .build(),
                    ActionParameterInfo.builder()
                        .name("reason")
                        .type("String")
                        .description("Reason for cancellation")
                        .required(false)
                        .build()
                ))
                .build()
        );
    }
    
    private void registerUpdatePaymentAction() {
        registeredActions.add(
            ActionInfo.builder()
                .action("update_payment_method")
                .description("Update user's payment method")
                .requiredParams(List.of("userId", "paymentType"))
                .confirmationRequired(true)
                .examples(List.of(
                    "Change my payment method",
                    "Update my credit card",
                    "Switch to PayPal"
                ))
                .parameters(List.of(
                    ActionParameterInfo.builder()
                        .name("userId")
                        .type("String")
                        .description("User ID")
                        .required(true)
                        .build(),
                    ActionParameterInfo.builder()
                        .name("paymentType")
                        .type("String")
                        .description("Type: credit_card, debit_card, paypal")
                        .required(true)
                        .build()
                ))
                .build()
        );
    }
    
    public List<ActionInfo> getAvailableActions() {
        return new ArrayList<>(registeredActions);
    }
}
```

### Advantages
✅ Maximum flexibility
✅ Easy to test (no reflection)
✅ Type-safe builder pattern
✅ Easy to add conditions
✅ Clean and readable

### Disadvantages
❌ Code-based (code changes to add action)
❌ More boilerplate
❌ Harder for non-devs

---

## Option 4: Dynamic Registry (MOST POWERFUL) ✅

### Concept
Allow services to self-register their actions at runtime.

### Implementation

```java
// Self-registration interface
public interface AIActionProvider {
    List<ActionInfo> getAvailableActions();
}

// Service implements and provides actions
@Service
public class SubscriptionService implements AIActionProvider {
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            ActionInfo.builder()
                .action("cancel_subscription")
                .description("Cancel user subscription")
                .requiredParams(List.of("subscriptionId"))
                .confirmationRequired(true)
                .build(),
            
            ActionInfo.builder()
                .action("upgrade_subscription")
                .description("Upgrade subscription plan")
                .requiredParams(List.of("subscriptionId", "newPlan"))
                .confirmationRequired(true)
                .build()
        );
    }
}

@Service
public class PaymentService implements AIActionProvider {
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            ActionInfo.builder()
                .action("update_payment_method")
                .description("Update payment method")
                .requiredParams(List.of("userId", "paymentType"))
                .confirmationRequired(true)
                .build(),
            
            ActionInfo.builder()
                .action("process_refund")
                .description("Process refund for order")
                .requiredParams(List.of("orderId"))
                .confirmationRequired(true)
                .build()
        );
    }
}

// Central registry collects all
@Service
public class AvailableActionsRegistry {
    
    private final List<AIActionProvider> providers;
    
    @Autowired
    public AvailableActionsRegistry(List<AIActionProvider> providers) {
        this.providers = providers;
    }
    
    public List<ActionInfo> getAvailableActions() {
        return providers.stream()
            .flatMap(provider -> provider.getAvailableActions().stream())
            .collect(Collectors.toList());
    }
}
```

### Advantages
✅ Decentralized (each service owns actions)
✅ Easy to scale (add new service = add new provider)
✅ Highly maintainable
✅ Clean separation of concerns
✅ Type-safe and flexible

### Disadvantages
❌ Requires discipline
❌ More complex setup

---

## Comparison: Which One to Choose?

| Option | Flexibility | Maintainability | Spring-Native | Scalability | Recommendation |
|--------|-------------|-----------------|---------------|-------------|-----------------|
| **Annotation** | ⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ | Good for small projects |
| **Config** | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | Good for complex projects |
| **Builder** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐ | Good for medium projects |
| **Dynamic Registry** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | **BEST FOR ENTERPRISE** ✅ |

---

## Recommendation for Your System

### BEST CHOICE: Dynamic Registry (Option 4)

**Why:**
1. You have multiple services (UserService, OrderService, PaymentService, etc.)
2. Each service knows what actions it can perform
3. You want to scale by adding new services
4. Perfect Spring ecosystem fit
5. Easy to test each service independently

### Implementation for Your System

```java
// Step 1: Each service implements AIActionProvider

@Service
public class UserManagementService implements AIActionProvider {
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            ActionInfo.builder()
                .action("cancel_subscription")
                .description("Cancel user's subscription")
                .requiredParams(List.of("userId"))
                .confirmationRequired(true)
                .examples(List.of("Cancel my subscription"))
                .build(),
            
            ActionInfo.builder()
                .action("update_payment_method")
                .description("Update payment method")
                .requiredParams(List.of("userId", "paymentType"))
                .confirmationRequired(true)
                .examples(List.of("Update my payment"))
                .build(),
            
            ActionInfo.builder()
                .action("update_shipping_address")
                .description("Update shipping address")
                .requiredParams(List.of("userId", "address", "city", "state", "zip"))
                .confirmationRequired(true)
                .examples(List.of("Change my shipping address"))
                .build()
        );
    }
}

@Service
public class OrderManagementService implements AIActionProvider {
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            ActionInfo.builder()
                .action("request_refund")
                .description("Request refund for order")
                .requiredParams(List.of("orderId"))
                .confirmationRequired(true)
                .examples(List.of("Get refund for my order", "I want a refund"))
                .build(),
            
            ActionInfo.builder()
                .action("track_order")
                .description("Get order tracking information")
                .requiredParams(List.of("orderId"))
                .confirmationRequired(false)
                .examples(List.of("Where is my order?", "Track my package"))
                .build()
        );
    }
}

// Step 2: Central registry (auto-wired)

@Service
public class AvailableActionsRegistry {
    
    private final List<AIActionProvider> providers;
    
    @Autowired
    public AvailableActionsRegistry(List<AIActionProvider> providers) {
        this.providers = providers;  // Spring auto-injects all AIActionProvider beans
    }
    
    public List<ActionInfo> getAllAvailableActions() {
        return providers.stream()
            .flatMap(provider -> provider.getAvailableActions().stream())
            .collect(Collectors.toList());
    }
}

// Step 3: Use in SystemContextBuilder

@Service
public class SystemContextBuilder {
    
    @Autowired
    private AvailableActionsRegistry actionsRegistry;
    
    public SystemContext buildContext(String userId) {
        return SystemContext.builder()
            .entityTypesSchema(buildEntityTypesSchema())
            .knowledgeBaseOverview(buildKnowledgeBaseOverview())
            .availableActions(actionsRegistry.getAllAvailableActions())  // ← Gets all actions
            .userBehaviorContext(buildUserBehaviorContext(userId))
            .indexStatistics(buildIndexStatistics())
            .systemCapabilities(buildSystemCapabilities())
            .build();
    }
}
```

---

## Real Example from Your System

Your system currently has:
- `UserService` - manages subscriptions, payments, addresses
- `OrderService` - handles refunds, tracking, returns
- `ProductService` - product operations
- etc.

**Each should implement `AIActionProvider`:**

```
UserService → cancel_subscription, update_payment, update_address
OrderService → request_refund, track_order, request_return
ProductService → (any product-specific actions)
```

Then central registry collects them all.

---

## Implementation Steps

1. **Create the interface:**
```java
public interface AIActionProvider {
    List<ActionInfo> getAvailableActions();
}
```

2. **Update each service:**
```java
@Service
public class MyService implements AIActionProvider {
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(...);  // Define actions
    }
}
```

3. **Create registry:**
```java
@Service
public class AvailableActionsRegistry {
    @Autowired
    private List<AIActionProvider> providers;
    
    public List<ActionInfo> getAllAvailableActions() {
        return providers.stream()
            .flatMap(p -> p.getAvailableActions().stream())
            .collect(Collectors.toList());
    }
}
```

4. **Use in context builder:**
```java
.availableActions(registry.getAllAvailableActions())
```

---

## Conclusion

**Use Dynamic Registry (Option 4)** because:
✅ Each service owns its actions
✅ Automatic discovery via Spring
✅ Easy to scale (add new service = auto-included)
✅ Clean and maintainable
✅ Perfect for enterprise

This is the "Spring Way" of solving the problem.

