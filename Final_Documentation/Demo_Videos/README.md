# Conversation Handover Demo Videos

## LoomAI Desk Shared-Service Proof

[Watch the current Desk demo](LOOMAI_DESK_DEALERSHIP_CUSTOMER_AGENT_DEMO.mp4)
shows the actual public dealership workspace and the assigned agent's shared
LoomAI Desk side by side. It is 66 seconds, H.264, 2560 x 1160, with no audio.

The live flow covers inventory search, an AI-selected human handover, agent
claim/reply, a visitor reply, explicit return to AI, a second button-requested
handover and final close. The initial close request is labelled pending; a
separate, labelled read-only capture shows **Closed** after the deployment's
callback acknowledgement. This cut is not a restart-latency measurement.

Test-account display labels are redacted in the recording only. API requests,
model answers, human replies and state transitions were not replaced or mocked.
No new account, grant, deployment, business booking or framework release was
created for this recording. Raw reports and authenticated browser state remain
private. The inventory itself is fictional simulator data, not live Auto Trader
customer data.

**Scope:** this is a technical handover proof, not a broad answer-quality or
outage-recovery pass. The post-return compound delivery-policy query still
reports missing policy evidence; that known limitation is retained visibly.
Action Review is qualified separately and is not shown in this clip.

See the [customer and agent guide](../External_User_Guides/LOOMAI_DESK_CUSTOMER_AND_AGENT_GUIDE.md)
for the current Console activation, Supabase sign-in and deployment-scoped agent
assignment flow. The redacted
[poster](../../verification-support/loomai-desk-01048/desk-customer-agent-demo-poster.png)
and [verification metadata](../../verification-support/loomai-desk-01048/shareable-demo-20261026.json)
identify the exact recording. Old dealership Inbox recorders below do not
reproduce the new Desk architecture.

## AI Fabric Framework Proof

`AI_FABRIC_FRAMEWORK_CONVERSATION_HANDOVER_DEMO.mp4` is a live framework-only proof of:

- a natural-language request selecting the governed `request_conversation_handover` action;
- AI Fabric moving the conversation from AI control to human control;
- an operator claiming the conversation and replying;
- an explicit authorized return to AI; and
- the same conversation continuing after the return.

The recording targets the deployed `conversation-handover-demo` real app. Its evidence frames are in `AI_FABRIC_FRAMEWORK_CONVERSATION_HANDOVER_DEMO-evidence/`.

## AI Fabric Required-Email Intake Proof

`AI_FABRIC_FRAMEWORK_REQUIRED_EMAIL_HANDOVER_DEMO.mp4` keeps the original
framework video intact and adds a live proof of:

- a natural-language handover request returning typed
  `HANDOVER_INTAKE_REQUIRED` while control remains `AI_ACTIVE`;
- required email and consent, with optional name and phone fields;
- a separate consent requirement when the optional phone is supplied;
- the validated contact projection reaching the application-owned operator
  view before claim; and
- the complete human claim, reply and explicit return-to-AI lifecycle.

Its evidence frames are in
`AI_FABRIC_FRAMEWORK_REQUIRED_EMAIL_HANDOVER_DEMO-evidence/`.

## Historical Dealership Inbox Proof

This earlier recording uses the now-retired dealership/operator Inbox. It is
retained as historical evidence, not a supported deployment or authentication
path. The current formal human-support service is shared LoomAI Desk above.

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

The earlier framework and historical dealership Playwright recorders live in
`Platfrom/loomai-site/scripts/`. They are not the current Desk recorder:

```bash
node scripts/record-ai-fabric-handover-live-demo.mjs

APP_DEMO_OPERATOR_ACCESS_CODE=... \
node scripts/record-ai-fabric-handover-intake-live-demo.mjs

DEALERSHIP_DEMO_STAFF_USERNAME=... \
DEALERSHIP_DEMO_STAFF_PASSWORD=... \
node scripts/record-dealership-handover-live-demo.mjs
```

The recorders fail when required live requests, committed state transitions, visible responses, or cleanup operations do not complete successfully.
