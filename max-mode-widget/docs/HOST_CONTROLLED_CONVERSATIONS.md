# Host-Controlled AI Workspace Conversations

Use this integration when a trusted application creates or selects the conversation,
and the workspace must display that exact conversation without submitting intake again.
The contract is domain-neutral: no ticket, vehicle or business-system types are required.

## Script API

```js
MaxMode.init({
  apiConfig: {
    chatBaseUrl: '/api/your-reviewed-adapter',
    runtimeRoutes: {
      conversationItemUrlTemplate: '/conversations/{conversationId}',
      conversationMessageUrlTemplate: '/conversations/{conversationId}/messages',
      // Supply the existing handover/control/events routes when enabled.
    },
    fetchCredentials: 'same-origin',
    probeShellConfigOnOpen: false,
    runtimeAuth: {
      probeAuthContextOnOpen: false,
      getBearerToken: async () => getCurrentBrowserAccessToken(),
    },
  },
  integrationMode: 'backend-mediated-private-runtime',
  storageNamespace: 'my-workspace',
  hostSession: { namespace: 'selected-thread-opaque-key', accountKey: 'opaque-account-key' },
  features: { cart: false },
});

const unsubscribe = MaxMode.onStateChange(state => {
  // Readiness is UI/controller readiness, not a claim of authorization or runtime health.
  console.log(state.status, state.conversationId, state.controlState);
});
await MaxMode.whenReady();
// The backend has already delivered initial intake through the canonical idempotent API.
await MaxMode.openConversation(existingConversationId); // GET only; opens the overlay
await MaxMode.refreshConversation();                   // GET only; preserves attachments
unsubscribe();
MaxMode.destroy();
```

`openConversation(id, { open: false })` selects/hydrates without opening the overlay.
It does not close an already open workspace. `refreshConversation()` requires a selected
conversation; it does not create one. Promises reject on authorization/network errors,
superseded selection, busy workspace, unmount or a missing conversation. Readiness waits
are bounded to ten seconds; history GET requests have a thirty-second deadline.
Runtime GET failures are not converted to successful empty
conversations. Retry a read explicitly; never POST to reconstruct old cards.

`getState()` returns a detached snapshot:

```ts
{
  status: 'initializing' | 'ready' | 'loading' | 'error' | 'destroyed';
  ready: boolean;
  isOpen: boolean;
  busy: boolean;
  conversationId: string | null;
  controlState: 'AI_ACTIVE' | 'HUMAN_PENDING' | 'HUMAN_ACTIVE' | 'AI_RESUME_PENDING' | 'CLOSED' | null;
  controlVersion: number | null;
  error: { code: string; message: string } | null;
}
```

`onStateChange(listener)` immediately emits current state and returns an unsubscribe
function. State contains no transcript, credentials, debug payloads or private auth
assertions. A null control state means it has not been observed, not assumed AI ownership.
Control/events use the existing handover implementation and continue to be server-enforced.

## Namespace and Account Lifecycle

- `hostSession` opts into explicit host selection. It requires both nonempty opaque keys.
- The host namespace and account key partition browser persistence, including runtime-session
  cache keys. They are not authorization claims. The adapter/runtime must independently
  authorize every history, message, control and SSE request.
- Host-controlled mode does not restore cached transcripts, select a recent conversation,
  consume queued prompts, manufacture a welcome turn or send an initial message.
- `MaxMode.sendMessage()` deliberately rejects queued programmatic intake in this mode.
  Ordinary subsequent user messages still use the existing composer/canonical message path.
- Before a conversation is successfully opened, the composer is read-only. A denied selection
  clears the previous conversation rather than leaving another thread visible.
- Switching conversations clears draft input, attachments, confirmations, sources and
  queued host prompts. Refreshing the same conversation retains current draft attachments.
- On account or case/workspace namespace changes, destroy and mount with the new scope and
  reviewed adapter configuration. Do not mutate identity fields on a live mount. Old async
  history/control responses cannot populate the new mount. Do not reuse service credentials
  as namespace keys; use opaque non-secret identifiers.
- Reopening after navigation/reload requires the host to resolve the authorized conversation
  and call `openConversation` again. No initial-message delivery happens in these APIs.

Only one live widget mount is supported by this bundle's existing singleton API/config.
These methods do not add concurrent independent widget instances.

The optional `runtimeAuth.getBearerToken` supplier is also honored for a reviewed
backend-mediated adapter. It is invoked on every request and SSE reconnect, so token
refresh/logout is not captured as a static mount-time bearer. Existing explicit request
or default authorization headers retain precedence; do not set one when using the
supplier for rotating identity. Cookie credentials retain their configured behavior.
Throw from the supplier after logout if the adapter requires a signed-in identity;
returning no token preserves the ordinary cookie/no-header path. Browser tokens must
belong to the host user, never a runtime assertion or server integration credential.
`host.readOnly: true` disables composing/handover transitions while retaining history
and control reads for an archived or closed host resource.

## React

```tsx
import { createWorkspaceHost, MaxModeWidget } from '@loom-ai-labs/max-mode-widget';

// Create one handle for this mounted namespace/account. Use a new handle on account switch.
const control = createWorkspaceHost();
// Render <MaxModeWidget workspaceHost={control} hostSession={scope} ... />.
// After mounting:
await control.whenReady();
await control.openConversation(existingConversationId);
await control.refreshConversation();
```

React's `isOpen` remains host-controlled; the open option does not change that prop.
Use the supplied handle for state subscriptions and cleanup the widget on account changes.

## Canonical GET and Stored Presentations

The existing owner-authorized conversation GET remains authoritative:

```json
{
  "id": "conversation-123",
  "controlState": "AI_ACTIVE",
  "controlVersion": 4,
  "participantMessages": [
    { "messageId": "user-1", "sequence": 1, "role": "USER", "content": "My original request", "createdAt": "2026-10-30T12:00:00Z" },
    { "messageId": "ai-1", "sequence": 2, "role": "AI", "content": "The canonical answer", "createdAt": "2026-10-30T12:00:01Z" }
  ],
  "completedTurns": [{
    "userMessageId": "user-1",
    "aiMessageId": "ai-1",
    "completedAt": "2026-10-30T12:00:01Z",
    "response": {
      "success": true,
      "type": "INFORMATION_PROVIDED",
      "answer": "The canonical answer",
      "sources": [{ "id": "guide-1", "title": "Setup guide", "content": "Approved public excerpt", "type": "document" }],
      "actions": []
    }
  }]
}
```

`completedTurns` is additive and optional. The receipt's `userMessageId` must exactly
match a canonical USER message, and `aiMessageId` binds the presentation to that exact
canonical AI message. Interleaved USER/AI writes from two tabs do not change pairing.
Missing IDs leave canonical text only. No nearest-user inference, text matching,
response-time guessing or synthetic message insertion is used. Canonical content
always wins over a differing receipt answer.
No presentation is attached to HUMAN/SYSTEM messages or another conversation.

The runtime owns the response allowlist: public sources, structured read results and
answer only. Never return metadata/debug/auth/private `result`, action proposals or write
payloads in this read projection. The widget uses the normal public response renderer,
does not restore authentication prompts or executable confirmations, and copies no raw
response into debug state. Missing/expired receipts retain canonical text only. Existing
text-only participant history and legacy `turns` display continue to render without cards.

## Focused Verification

`npm run smoke:backend-bearer` covers rotating backend-mediated browser tokens,
SSE reconnects, logout rejection and existing header/cookie precedence.

```sh
cd experience-packs/generic-workspace-experience
npm run typecheck
npm run build
cd ../../max-mode-widget
npm run typecheck
npm run build:iife
npm run smoke:workspace-host
npm run smoke:workspace-host-contract
npm run smoke:conversation-history
npm run smoke:conversation-restoration
```

The host smoke uses local fake authorized endpoints, not production services. It verifies
GET-only hydration/refresh, source/read cards, canonical text, control observations, denial,
reload, account replacement, obsolete response rejection and pack controller lifecycle.
Runtime authorization, receipt allowlisting and actual hosted support journeys require
their corresponding backend/integration verification.
