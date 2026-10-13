# Conversation Handover Demo Videos

## AI Fabric Framework Proof

`AI_FABRIC_FRAMEWORK_CONVERSATION_HANDOVER_DEMO.mp4` is a live framework-only proof of:

- a natural-language request selecting the governed `request_conversation_handover` action;
- AI Fabric moving the conversation from AI control to human control;
- an operator claiming the conversation and replying;
- an explicit authorized return to AI; and
- the same conversation continuing after the return.

The recording targets the deployed `conversation-handover-demo` real app. Its evidence frames are in `AI_FABRIC_FRAMEWORK_CONVERSATION_HANDOVER_DEMO-evidence/`.

## LoomAI Dealership Proof

`LOOMAI_DEALERSHIP_CONVERSATION_HANDOVER_DEMO.mp4` is a live side-by-side proof of:

- grounded dealership inventory search;
- a customer asking AI to transfer the conversation to a person;
- the model selecting the deployment-owned handover action;
- staff claiming and replying through the protected dealership inbox;
- customer and operator messages sharing one durable conversation;
- staff explicitly returning control to AI;
- AI search working again after the return; and
- the customer starting a distinct second handover through the generic UI control.

Its evidence frames are in `LOOMAI_DEALERSHIP_CONVERSATION_HANDOVER_DEMO-evidence/`.

## Reproduction

The Playwright recorders live in `Platfrom/loomai-site/scripts/`:

```bash
node scripts/record-ai-fabric-handover-live-demo.mjs

DEALERSHIP_DEMO_STAFF_USERNAME=... \
DEALERSHIP_DEMO_STAFF_PASSWORD=... \
node scripts/record-dealership-handover-live-demo.mjs
```

The recorders fail when required live requests, committed state transitions, visible responses, or cleanup operations do not complete successfully.
