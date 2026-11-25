# Autonomous Conversations

Implementation candidate, 25 November 2026. Hosted availability requires the
compatible Framework/Runtime rollout and live qualification recorded in 010.77.

## Choose Conversation Handling

Open your Smart Brain, then **Responses -> Conversation flow**. Standard keeps
the existing execution path. Autonomous lets the Brain use its enabled tools,
observe results, recover from supported READ failures and work toward a useful
answer within the configured request allowance.

Save the draft, review its changes, then publish and apply it normally. Existing
products do not switch automatically. The selected runtime must support this
capability; the Platform checks compatibility before applying it. Website widgets,
max-mode and standalone Engage apps use the same configured Brain.

Updates compares the saved handling mode and changed execution limits against
the applied configuration. A mode-only change links back to Conversation flow;
unchanged models, source permissions and stored keys are not replaced by it.

Engage follows the canonical conversation routes supplied by its installation
manifest automatically. A custom API client must use those existing conversation
and message routes after enabling this mode; it must not keep using a legacy
query-only route when canonical conversation control is enabled. The chat request
does not gain any private authoring scope. Framework-only applications can bind
the same engine through their existing trusted conversation host.

Advanced execution limits control elapsed time, per-segment tool/model use and
no-progress protection. Segments may continue within the request's deadline;
they do not create extra funding. Zero allowed writes makes the autonomous
catalogue read-only. Normal provider accounting and spending limits still apply.

## What Stays Protected

- Existing customer ownership, document/source access and business permissions.
- Required business inputs, sign-in and each action's confirmation policy.
- Selected action evidence, stored credentials and real write receipts.
- Separate publication and activation; a conversation cannot grant those rights.

If a write needs confirmation, the Brain pauses and uses the ordinary confirmation
flow. If a write does not require confirmation, it may execute within its existing
permissions. Unknown write outcomes are not automatically repeated. Approved
knowledge uses the existing indexing, search and optional reranking configuration.

## Practical Limits

This is assistance during an active conversation request, not a durable background
job. A lost connection or process restart does not promise automatic continuation.
Business effects already performed are not rolled back. Do not resubmit a write
blindly when its result is uncertain; inspect its receipt or status first.

Build with AI retains its separate configured native goal controls. Fixed Tasks
and explicitly bound specialist workflows keep their own execution contracts;
the ordinary conversation setting does not silently replace them.
