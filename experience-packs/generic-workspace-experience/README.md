# Generic AI Workspace Experience

Domain-neutral LoomAI Engage experience pack. It supplies reviewed default knowledge tools, optional current-page context, branding, and host copy without embedding any dealership, commerce, or customer-specific behavior in the shared workspace.

## Host-Controlled Conversations

The controller returned by `mountInstallation(context)` exposes `whenReady()`,
`getState()`, `onStateChange(listener)`, `openConversation(id, { open? })` and
`refreshConversation()`, in addition to `installationId` and `destroy()`.
Calls delegate to the shared widget; they do not create messages or add a second
conversation engine. A replaced controller cannot operate or destroy a new mount.

Provide `context.hostSession = { namespace, accountKey }` when the trusted host
owns conversation selection. Both keys must be opaque and non-secret. This disables
recent-conversation auto-selection, queued host intake and cached transcript restore.
The host's reviewed backend delivers any initial form message once; after that,
call `await controller.openConversation(authorizedConversationId)` to read/render it.
Normal user follow-ups and human handover keep using the widget's existing routes.
On account/scope change, destroy and mount with the new session and adapter settings.

See the [widget host integration guide](../../max-mode-widget/docs/HOST_CONTROLLED_CONVERSATIONS.md)
for the full script/React API, state/error behavior, canonical completed-turn DTO,
security boundaries and focused test command. A host session never grants access;
the adapter/runtime must authorize every request and event reconnect independently.
