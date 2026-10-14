# From AI Chat to Governed Human Handover in a Spring Boot Application

**Subtitle:** A framework-only demonstration of model-selected escalation, typed contact intake and durable conversation authority with AI Fabric 0.8.32.

> Publishing note: upload `Final_Documentation/Demo_Videos/AI_FABRIC_FRAMEWORK_REQUIRED_EMAIL_HANDOVER_DEMO.mp4` and place the public video link here before publication.

AI assistants are useful while they can answer a question. A production application becomes more interesting when the assistant must stop answering, transfer responsibility to a person and later resume without losing the conversation.

That is not just a user-interface feature. It is a control problem.

We built a standalone Spring Boot demonstration to prove that this lifecycle can be explicit and framework-backed. The demo uses AI Fabric directly. It does not depend on LoomAI Platform, a LoomAI runtime or a hosted operator service.

The result is one conversation that can move through this authority lifecycle:

```text
AI_ACTIVE
  -> HUMAN_PENDING
  -> HUMAN_ACTIVE
  -> AI_RESUME_PENDING
  -> AI_ACTIVE
```

The model may select the handover action, but it does not own the transition. Application policy, typed intake validation and the conversation-control state machine decide whether the transition is allowed.

## What the demonstration proves

The live application places the customer view and a small application-owned operator view side by side. It demonstrates:

- a natural-language request selecting a governed `request_conversation_handover` action;
- required email and explicit consent before an anonymous visitor enters the operator queue;
- optional name and phone fields, with separate consent when a phone number is supplied;
- durable, versioned and idempotent authority transitions;
- AI responses being blocked while a human owns the conversation;
- ordered `USER`, `AI`, `HUMAN` and `SYSTEM` messages; and
- an explicit return to AI followed by a normal AI response in the same conversation.

There is no phrase parser that looks for words such as "human" or "operator" and secretly changes the state. The language model selects a typed action through the normal AI Fabric action mechanism. Deterministic application and framework controls handle everything after that selection.

## 1. Describe handover as an AI Fabric action

The host application exposes handover as an ordinary `@AIAction`:

```java
@AIAction(
    name = "request_conversation_handover",
    description = "Begin a governed transfer of this conversation to a human operator...",
    category = "conversation-control",
    accessMode = ActionAccessMode.WRITE_ONLY,
    requiresConfirmation = true,
    anonymousAllowed = true,
    terminal = true
)
class RequestConversationHandoverAction {

    @ActionAllowed
    public boolean allowed(ActionContext context) {
        return context != null
            && context.hasConversation()
            && service.canRequestHuman(context.sessionId(), context.conversationId());
    }

    @ActionExecute
    public ActionResult execute(
        @Param(value = "requestProvenance", required = false)
        HandoverRequestProvenance requestProvenance,
        ActionContext context
    ) {
        var effectiveProvenance = requestProvenance == null
            ? HandoverRequestProvenance.MODEL_RECOMMENDATION
            : requestProvenance;
        var admission = service.requestHuman(
            context.sessionId(),
            context.conversationId(),
            context.requestId(),
            effectiveProvenance
        );
        return ActionResult.builder()
            .success(true)
            .message(admission.admission().message())
            .data(/* typed admission projection */)
            .build();
    }
}
```

`anonymousAllowed = true` means an anonymous visitor may ask for help. It does not mean the transfer is unconditional. The action returns a typed admission result and the conversation remains `AI_ACTIVE` until the application validates the required intake.

The confirmation policy is also contextual. An explicit user request does not need a redundant "Are you sure?" prompt. A model-recommended escalation still requires confirmation.

## 2. Return a typed intake contract

The demo defines the customer information that its operator workflow needs:

```java
new HandoverIntakeSchema(
    "DEMO_HANDOVER_INTAKE_V1",
    false,
    List.of(
        new HandoverIntakeField(
            "email", "Email", HandoverIntakeFieldType.EMAIL,
            true, 254,
            "Required so the operator can preserve contact continuity.",
            true, List.of()
        ),
        new HandoverIntakeField(
            "name", "Name", HandoverIntakeFieldType.TEXT,
            false, 120, "Optional display name for the operator.",
            false, List.of()
        ),
        new HandoverIntakeField(
            "phone", "Phone", HandoverIntakeFieldType.PHONE,
            false, 40, "Optional phone follow-up.",
            true, List.of()
        )
    )
);
```

The schema is presentation-neutral. A browser, mobile client or another channel can render the same reviewed contract. The host still owns validation, storage, encryption, retention and the legal wording appropriate to its use case.

In this demo, an invalid or missing email fails before the authority state changes. Supplying a phone number without phone consent also fails. The model cannot invent either field or bypass that policy.

## 3. Commit transitions through the conversation-control gateway

After valid intake, the host submits an explicit command with the expected state version and an idempotency key:

```java
gateway.transition(new ConversationControlCommand(
    scope,
    ConversationControlCommandType.REQUEST_HUMAN,
    request.expectedVersion(),
    request.idempotencyKey(),
    ownerId,
    "VISITOR_REQUEST",
    handoverId,
    channelId
));
```

The operator uses different authorized commands to claim the conversation, append a human message and request return to AI. Returning to AI is also explicit:

```java
var pending = transition(
    scope,
    ConversationControlCommandType.REQUEST_AI_RESUME,
    request,
    operatorId,
    "OPERATOR_RETURN",
    null,
    null
);

transition(
    scope,
    ConversationControlCommandType.COMPLETE_AI_RESUME,
    new TransitionRequest(
        pending.snapshot().version(),
        request.idempotencyKey() + "-complete"
    ),
    "system",
    "AI_RESUME_COMPLETE",
    null,
    null
);
```

The expected version prevents two actors from silently winning the same transition. The idempotency key makes retries safe. Conversation scope keeps tenant, deployment, conversation and owner identity together at every operation.

## 4. Block stale AI turns

AI Fabric also supplies an AI turn lease. If a handover begins while a model response is still being generated, the lease is no longer valid when that response tries to append. The stale AI response is rejected instead of appearing after a human has taken responsibility.

This is a small detail with a large product consequence: the interface cannot show the customer two competing authorities.

## Framework responsibility versus application responsibility

AI Fabric provides:

- the conversation-control state machine;
- typed handover admission and intake contracts;
- JDBC persistence contracts;
- scoped, versioned and idempotent transitions;
- AI turn leases;
- participant-role gates; and
- ordered conversation messages.

The application provides:

- the model and action configuration;
- customer intake policy and validation;
- customer and operator authentication;
- HTTP endpoints and UI;
- dispatch to an operator system;
- protected contact storage and retention; and
- the business decision about when human help is available.

That boundary is intentional. A framework can make authority transitions reliable without becoming a contact-centre product.

## How we verified it

The repository includes both deterministic integration tests and a real-provider demonstration.

The HTTP test exercises the complete AI-to-human-to-AI lifecycle. It verifies invalid intake, owner isolation, operator isolation, blocked AI responses, ordered messages and explicit state transitions. The hosted demo adds the proof that a real model can select the handover action from a natural-language request.

The demo source is available in the AI Fabric repository:

[Conversation Handover Demo on GitHub](https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/conversation-handover-demo)

## The outcome

The useful result is not a "talk to a human" button. It is a reusable way for an AI-enabled application to know who may answer, prove how that authority changed and preserve one coherent conversation across the transition.

That gives product teams room to use their own operator UI, CRM or workforce platform while keeping the AI boundary explicit.

**Tags:** Java, Spring Boot, AI Agents, Human in the Loop, Generative AI, Open Source
