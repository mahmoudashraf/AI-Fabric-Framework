# LinkedIn Post: AI Fabric Conversation Handover Demo

We have added a framework-only proof for one of the less visible problems in production AI: who is allowed to answer when a conversation moves between AI and a person?

The new AI Fabric 0.8.32 demo runs as a standalone Spring Boot application. A user can ask naturally to speak with a human, and the model selects a governed action:

```java
@AIAction(
    name = "request_conversation_handover",
    accessMode = WRITE_ONLY,
    anonymousAllowed = true,
    terminal = true
)
```

Selection is only the beginning. The action returns a typed `HandoverIntakeSchema`. The demo requires a valid email and explicit consent, supports optional name and phone fields, and keeps the conversation in `AI_ACTIVE` until application policy accepts the intake.

AI Fabric then governs the durable lifecycle:

```text
AI_ACTIVE -> HUMAN_PENDING -> HUMAN_ACTIVE
          -> AI_RESUME_PENDING -> AI_ACTIVE
```

Every transition is scoped, versioned and idempotent. AI turn leases prevent a stale model response from appearing after a human takes control. Operator claim, reply and return-to-AI operations remain explicit authorized commands.

There is no keyword matcher that secretly performs the handover. The model selects the typed action; deterministic application and framework controls decide whether anything changes.

AI Fabric owns the state machine, role gates, message ordering and persistence contracts. The host application still owns customer intake, operator authentication, contact storage, retention and UI.

That is the boundary we wanted to prove: reliable human-in-the-loop mechanics without turning the framework into an operator platform.

Source: https://github.com/Loom-AI-Labs/ai-fabric-framework/tree/main/examples/real-apps/conversation-handover-demo

Demo video: add the public URL for `AI_FABRIC_FRAMEWORK_REQUIRED_EMAIL_HANDOVER_DEMO.mp4` before publishing.

#Java #SpringBoot #GenerativeAI #HumanInTheLoop #OpenSource
