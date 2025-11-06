# AvailableActions - Real Example for Your System

## Your Business Actions

Based on your system, here are the actions users can actually perform:

---

## Category 1: Subscription Management

### Action: Cancel Subscription
```json
{
  "action": "cancel_subscription",
  "description": "Permanently cancel user's subscription",
  "category": "subscription",
  "riskLevel": "high",
  "confirmationRequired": true,
  "requiredParams": ["subscriptionId"],
  "examples": [
    "Cancel my subscription",
    "Stop my membership",
    "Unsubscribe me",
    "I want to cancel my account",
    "Can I stop my subscription?"
  ],
  "parameters": [
    {
      "name": "subscriptionId",
      "type": "String",
      "description": "The unique subscription ID",
      "required": true
    },
    {
      "name": "reason",
      "type": "String",
      "description": "Reason for cancellation",
      "required": false,
      "enumValues": ["too_expensive", "not_using", "switching_provider", "poor_service", "other"]
    }
  ]
}
```

### Action: Upgrade Subscription
```json
{
  "action": "upgrade_subscription",
  "description": "Upgrade to a higher subscription tier",
  "category": "subscription",
  "riskLevel": "medium",
  "confirmationRequired": true,
  "requiredParams": ["subscriptionId", "newPlan"],
  "examples": [
    "Upgrade my subscription",
    "Switch to premium",
    "Can I get a better plan?",
    "I want the pro version",
    "Upgrade me to the best plan"
  ],
  "parameters": [
    {
      "name": "subscriptionId",
      "type": "String",
      "description": "Current subscription ID",
      "required": true
    },
    {
      "name": "newPlan",
      "type": "String",
      "description": "New plan tier",
      "required": true,
      "enumValues": ["basic", "premium", "professional", "enterprise"]
    },
    {
      "name": "billingCycle",
      "type": "String",
      "description": "Billing cycle (monthly/annual)",
      "required": false,
      "enumValues": ["monthly", "annual"]
    }
  ]
}
```

### Action: Pause Subscription
```json
{
  "action": "pause_subscription",
  "description": "Temporarily pause subscription",
  "category": "subscription",
  "riskLevel": "low",
  "confirmationRequired": false,
  "requiredParams": ["subscriptionId"],
  "examples": [
    "Pause my subscription",
    "Temporarily stop my subscription",
    "Freeze my account",
    "I need to pause for a month",
    "Can I pause my membership?"
  ],
  "parameters": [
    {
      "name": "subscriptionId",
      "type": "String",
      "description": "Subscription ID to pause",
      "required": true
    },
    {
      "name": "pauseDurationMonths",
      "type": "Integer",
      "description": "How long to pause (1-12 months)",
      "required": false,
      "defaultValue": "1"
    }
  ]
}
```

---

## Category 2: Payment Management

### Action: Update Payment Method
```json
{
  "action": "update_payment_method",
  "description": "Update or change payment method",
  "category": "payment",
  "riskLevel": "high",
  "confirmationRequired": true,
  "requiredParams": ["userId", "paymentType"],
  "examples": [
    "Update my payment method",
    "Change my credit card",
    "Switch to PayPal",
    "I need to update my payment info",
    "Can I use a different payment?"
  ],
  "parameters": [
    {
      "name": "userId",
      "type": "String",
      "description": "User ID",
      "required": true
    },
    {
      "name": "paymentType",
      "type": "String",
      "description": "Payment method type",
      "required": true,
      "enumValues": ["credit_card", "debit_card", "paypal", "apple_pay", "google_pay"]
    },
    {
      "name": "cardNumber",
      "type": "String",
      "description": "Credit card number (for card payments)",
      "required": false
    },
    {
      "name": "expiryDate",
      "type": "String",
      "description": "Card expiry (MM/YY)",
      "required": false
    },
    {
      "name": "cvv",
      "type": "String",
      "description": "CVV (for card payments)",
      "required": false
    }
  ]
}
```

### Action: Add Payment Method
```json
{
  "action": "add_payment_method",
  "description": "Add a new payment method to account",
  "category": "payment",
  "riskLevel": "high",
  "confirmationRequired": true,
  "requiredParams": ["userId", "paymentType"],
  "examples": [
    "Add a payment method",
    "Add a credit card",
    "Link my PayPal account",
    "Can I add another way to pay?"
  ],
  "parameters": [
    {
      "name": "userId",
      "type": "String",
      "description": "User ID",
      "required": true
    },
    {
      "name": "paymentType",
      "type": "String",
      "description": "Payment method type",
      "required": true,
      "enumValues": ["credit_card", "debit_card", "paypal"]
    }
  ]
}
```

---

## Category 3: Order Management

### Action: Request Refund
```json
{
  "action": "request_refund",
  "description": "Request refund for an order",
  "category": "order",
  "riskLevel": "high",
  "confirmationRequired": true,
  "requiredParams": ["orderId"],
  "examples": [
    "I want a refund",
    "Get money back for my order",
    "Can I return this order?",
    "Request refund for order",
    "I'd like a refund"
  ],
  "parameters": [
    {
      "name": "orderId",
      "type": "String",
      "description": "Order ID",
      "required": true
    },
    {
      "name": "reason",
      "type": "String",
      "description": "Reason for refund",
      "required": true,
      "enumValues": ["defective_item", "wrong_item_received", "not_as_described", "changed_mind", "better_price_found", "no_longer_needed"]
    }
  ]
}
```

### Action: Request Return
```json
{
  "action": "request_return",
  "description": "Request to return an item from order",
  "category": "order",
  "riskLevel": "medium",
  "confirmationRequired": true,
  "requiredParams": ["orderId"],
  "examples": [
    "Return my order",
    "I want to send it back",
    "Can I return this?",
    "Request return"
  ],
  "parameters": [
    {
      "name": "orderId",
      "type": "String",
      "description": "Order ID",
      "required": true
    },
    {
      "name": "itemIds",
      "type": "List<String>",
      "description": "Which items to return (optional, all if not specified)",
      "required": false
    }
  ]
}
```

### Action: Track Order
```json
{
  "action": "track_order",
  "description": "Get order tracking information",
  "category": "order",
  "riskLevel": "low",
  "confirmationRequired": false,
  "requiredParams": ["orderId"],
  "examples": [
    "Where is my order?",
    "Track my package",
    "Has my order shipped?",
    "When will my order arrive?",
    "Track order"
  ],
  "parameters": [
    {
      "name": "orderId",
      "type": "String",
      "description": "Order ID",
      "required": true
    }
  ]
}
```

### Action: Cancel Order
```json
{
  "action": "cancel_order",
  "description": "Cancel an order (if not yet shipped)",
  "category": "order",
  "riskLevel": "medium",
  "confirmationRequired": true,
  "requiredParams": ["orderId"],
  "examples": [
    "Cancel my order",
    "Cancel order #12345",
    "I want to cancel this purchase",
    "Can I cancel my order?"
  ],
  "parameters": [
    {
      "name": "orderId",
      "type": "String",
      "description": "Order ID",
      "required": true
    }
  ]
}
```

---

## Category 4: Account Management

### Action: Update Shipping Address
```json
{
  "action": "update_shipping_address",
  "description": "Update default shipping address",
  "category": "account",
  "riskLevel": "medium",
  "confirmationRequired": true,
  "requiredParams": ["userId", "address"],
  "examples": [
    "Update my shipping address",
    "Change my delivery address",
    "Update my address",
    "I moved to a new address"
  ],
  "parameters": [
    {
      "name": "userId",
      "type": "String",
      "description": "User ID",
      "required": true
    },
    {
      "name": "address",
      "type": "String",
      "description": "Street address",
      "required": true
    },
    {
      "name": "city",
      "type": "String",
      "description": "City",
      "required": true
    },
    {
      "name": "state",
      "type": "String",
      "description": "State/Province code",
      "required": true
    },
    {
      "name": "zipCode",
      "type": "String",
      "description": "Zip/Postal code",
      "required": true
    },
    {
      "name": "country",
      "type": "String",
      "description": "Country",
      "required": false
    }
  ]
}
```

### Action: Update Email
```json
{
  "action": "update_email",
  "description": "Update account email address",
  "category": "account",
  "riskLevel": "high",
  "confirmationRequired": true,
  "requiredParams": ["userId", "newEmail"],
  "examples": [
    "Update my email",
    "Change my email address",
    "I need a new email",
    "Can I update my email?"
  ],
  "parameters": [
    {
      "name": "userId",
      "type": "String",
      "description": "User ID",
      "required": true
    },
    {
      "name": "newEmail",
      "type": "String",
      "description": "New email address",
      "required": true
    }
  ]
}
```

---

## Category 5: Information Retrieval (NOT ACTIONS)

These DON'T use ACTION intents - they use INFORMATION intents with retrieval:

```
- "What's your return policy?" → INFORMATION intent → retrieve from docs
- "How much does premium cost?" → INFORMATION intent → retrieve from docs
- "Do you offer international shipping?" → INFORMATION intent → retrieve from docs
- "What payment methods do you accept?" → INFORMATION intent → retrieve from docs
```

---

## Spring Service Implementation

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService implements AIActionProvider {
    
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentService paymentService;
    private final NotificationService notificationService;
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            buildCancelSubscriptionAction(),
            buildUpgradeSubscriptionAction(),
            buildPauseSubscriptionAction()
        );
    }
    
    private ActionInfo buildCancelSubscriptionAction() {
        return ActionInfo.builder()
            .action("cancel_subscription")
            .description("Permanently cancel user's subscription")
            .category("subscription")
            .riskLevel("high")
            .confirmationRequired(true)
            .requiredParams(List.of("subscriptionId"))
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
                    .enumValues(new String[]{"too_expensive", "not_using", "switching_provider"})
                    .build()
            ))
            .build();
    }
    
    // Execute the action
    public void cancelSubscription(String subscriptionId, String reason) {
        log.info("Cancelling subscription: {} with reason: {}", subscriptionId, reason);
        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
        
        // Perform cancellation logic
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancellationReason(reason);
        subscription.setCancelledAt(LocalDateTime.now());
        
        subscriptionRepository.save(subscription);
        
        // Send confirmation notification
        notificationService.sendCancellationConfirmation(subscription.getUser());
        
        log.info("Subscription cancelled successfully");
    }
}

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService implements AIActionProvider {
    
    private final OrderRepository orderRepository;
    private final RefundService refundService;
    private final TrackingService trackingService;
    
    @Override
    public List<ActionInfo> getAvailableActions() {
        return List.of(
            buildRequestRefundAction(),
            buildRequestReturnAction(),
            buildTrackOrderAction(),
            buildCancelOrderAction()
        );
    }
    
    private ActionInfo buildRequestRefundAction() {
        return ActionInfo.builder()
            .action("request_refund")
            .description("Request refund for an order")
            .category("order")
            .riskLevel("high")
            .confirmationRequired(true)
            .requiredParams(List.of("orderId"))
            .examples(List.of(
                "I want a refund",
                "Can I get money back?",
                "Request refund"
            ))
            .parameters(List.of(
                ActionParameterInfo.builder()
                    .name("orderId")
                    .type("String")
                    .description("Order ID")
                    .required(true)
                    .build(),
                ActionParameterInfo.builder()
                    .name("reason")
                    .type("String")
                    .description("Reason for refund")
                    .required(true)
                    .enumValues(new String[]{"defective", "wrong_item", "not_as_described"})
                    .build()
            ))
            .build();
    }
    
    // Execute the action
    public RefundResult requestRefund(String orderId, String reason) {
        log.info("Processing refund request for order: {} with reason: {}", orderId, reason);
        
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        
        return refundService.initiateRefund(order, reason);
    }
}
```

---

## In the SystemContextBuilder

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemContextBuilder {
    
    private final AvailableActionsRegistry actionsRegistry;
    
    public SystemContext buildContext(String userId) {
        // Get all available actions
        List<ActionInfo> actions = actionsRegistry.getAllAvailableActions();
        
        // Format for LLM
        String actionsJson = formatActionsForLLM(actions);
        
        return SystemContext.builder()
            .availableActions(actions)
            .availableActionsJson(actionsJson)  // For the prompt
            .knowledgeBaseOverview(buildKnowledgeBaseOverview())
            .build();
    }
    
    private String formatActionsForLLM(List<ActionInfo> actions) {
        return actions.stream()
            .map(action -> String.format(
                """
                AVAILABLE ACTION: %s
                Description: %s
                Category: %s
                Risk Level: %s
                Requires Confirmation: %s
                Examples: %s
                """,
                action.getAction(),
                action.getDescription(),
                action.getCategory(),
                action.getRiskLevel(),
                action.getConfirmationRequired(),
                String.join(", ", action.getExamples())
            ))
            .collect(Collectors.joining("\n\n"));
    }
}
```

---

## Complete Prompt Example

```
SYSTEM PROMPT TO LLM:
=====================

You are a helpful customer service AI for EasyLuxury.

AVAILABLE INFORMATION (retrieve from knowledge base):
- Product information
- Pricing and plans
- Shipping policies
- Return policies
- Troubleshooting guides

AVAILABLE ACTIONS (user can trigger these):

AVAILABLE ACTION: cancel_subscription
Description: Permanently cancel user's subscription
Category: subscription
Risk Level: high
Requires Confirmation: true
Examples: Cancel my subscription, Stop my membership, Unsubscribe me

AVAILABLE ACTION: update_payment_method
Description: Update or change payment method
Category: payment
Risk Level: high
Requires Confirmation: true
Examples: Update my payment method, Change my credit card, Switch to PayPal

AVAILABLE ACTION: request_refund
Description: Request refund for an order
Category: order
Risk Level: high
Requires Confirmation: true
Examples: I want a refund, Get money back for my order, Can I return this order?

AVAILABLE ACTION: track_order
Description: Get order tracking information
Category: order
Risk Level: low
Requires Confirmation: false
Examples: Where is my order?, Track my package, Has my order shipped?

--- more actions ---

When responding to user queries:
1. If they ask about information (policies, pricing, features):
   - Respond with type: "INFORMATION"
   - Use retrieval to get accurate data
   
2. If they want to perform an action (cancel, refund, update):
   - Respond with type: "ACTION"
   - Include the action name and required parameters
   - Ask for confirmation if needed
   
3. If request is out of scope:
   - Respond with type: "OUT_OF_SCOPE"
   - Explain what you can help with

4. If user asks multiple things:
   - Respond with type: "COMPOUND"
   - List all sub-intents
   - Suggest orchestration strategy
```

---

## Summary

Your system has ~15-20 available actions across 4-5 categories:

✅ **Subscription:** cancel, upgrade, pause
✅ **Payment:** update, add payment method
✅ **Order:** refund, return, track, cancel
✅ **Account:** update address, email, preferences
✅ **Information:** retrieve from knowledge base (NOT actions)

Each action is exposed to the LLM with:
- Description
- Parameters
- Examples
- Confirmation requirements
- Risk level

The LLM then decides:
- Should I retrieve information? → INFORMATION intent
- Should I execute an action? → ACTION intent
- Should I admit I don't know? → OUT_OF_SCOPE intent
- Is this multiple questions? → COMPOUND intent

That's it! 🎯

