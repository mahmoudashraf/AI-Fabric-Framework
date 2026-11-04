# Support Experience Pack 1.0.0

Provider-neutral support shell, configuration schema
`loomai-support-experience-config-v1`, catalogue code `support`. Chat is the
current Max Mode engine in one persistent, integrity-checked isolated frame.
There is no support-specific chat client, transcript store or agent routing.

Start with [Installation and operations](docs/INSTALLATION_AND_OPERATIONS.md)
for the customer, host, agent, storage and outage workflows, or the
[private host adapter contract](docs/CONSOLE_HOST_ADAPTER.md) for Console-style
integrations.

## Assets And Exports

Run `npm ci && npm test` after building the sibling widget with
`npm --prefix ../../max-mode-widget run build:embedded`. `test:unit` needs no
browser asset; `test:browser` uses the repository's existing Playwright install.
Browser assets (generated, not committed):

- `dist/support-experience.iife.js` registers `support@1.0.0` with `isolation: 'frame'`.
- `dist/support-experience.esm.js` exports the mount, validators, API client,
  presentation resolver and configuration-aware fixture factory.
- `dist/index.d.ts` and adjacent type declarations describe the public contract.

The installer owns SRI checks for both pack and engine assets. Neither bootstrap
HTML nor public configuration contains keys, assertions, executable callbacks
or customer intake. The frame is style isolation for trusted code, not an
untrusted-code sandbox. The bridge uses same-origin object references; it does
not accept cross-window messages or serialize credential callbacks.

## Configuration

```json
{
  "schemaVersion": "loomai-support-experience-config-v1",
  "app": { "assistantLabel": "Example Support", "title": "Help and support" },
  "support": {
    "spaceId": "space-example",
    "ticketProviderBindingId": "binding-example",
    "requesterOrigin": "https://desk.example.test",
    "topics": [{ "code": "knowledge", "label": "Knowledge" }],
    "teamVisible": true
  },
  "capabilities": { "tickets": true, "handover": false, "attachments": true, "currentPage": false }
}
```

Bindings are managed installation output, not owner-supplied authority.
`validateConfig` validates executable installed configuration; preview calls
`validateConfig(config, { allowUnbound: true })`. Requested ticketing can therefore
be previewed before activation, without contacting a service or claiming enablement.
With both ticketing and handover disabled, no requester endpoint or binding is
needed. Handover-only installations still load the authenticated requester
session and, when `teamVisible` and effective `humanHandoverEnabled` are true,
the assigned roster. Ticket controls and commands stay disabled independently.
Effective request capabilities
come only from the authenticated session and individual request. No provider ID
selects UI branches. Theme, tools, copy and request context reuse generic leaves.

## Installation And Layout

```html
<div id="company-support" style="height: min(760px, calc(100dvh - 48px))"></div>
<script async src="https://platform.example.test/api/public/ai-workspace/install.js"
  data-installation-id="awi_pub_0123456789abcdef0123456789abcdef"
  data-container="#company-support" data-presentation="page"></script>
```

Only `widget`, `page` and `standalone` are accepted. `data-container` accepts a
single bounded element ID; a missing/invalid page target fails without opening
an overlay. Page containers supply a definite height, including a bounded flex
pane with `min-height: 0`; list/detail scroll inside that pane. Keep the installer
once in the persistent SPA layout. On navigation,
the shell can attach to the replacement element with that same ID. Its frame
never moves in the DOM. A missing SPA target hides the presentation, not the
engine. `controller.setPresentation(mode, element?)` changes layout without
reinitializing chat; changing request/conversation is a separate explicit command.
Full-document navigation restores durable chat through existing engine contracts;
unsent drafts are not ticket storage or cross-origin synchronization.

## Private Host / Console

Use the exported `SupportMountContext` and `SupportHostAdapter` types. Trusted
mount code supplies `supportHost` (installer hosts may instead provide
`LoomAIWorkspaceHost.extensions.support`). No host adapter is read from config.

```ts
const controller = await mountInstallation({
  installationId, assignmentRevision, connectionMode: 'backend-mediated-private-runtime',
  deliveryMode: 'embedded', configuration, widgetConfig: {},
  hostSession: { namespace: 'support-owner', accountKey: opaqueAccountKey },
  presentation: { mode: 'page', container }, maxMode: frame.maxMode, workspaceFrame: frame,
  credentials: { getIdentityAccessToken, getIdentity },
  supportHost: {
    apiBaseUrl: '/api/support', runtimeCredentialMode: 'host-mediated', getIdentityAccessToken,
    fetchWorkspace: requestId => fetchAuthorizedWorkspace(requestId),
    onRequestSelected: request => selectDiagnosticContext(request?.id ?? null),
    intakeExtension: mountResourceAndConsentFields,
  },
  refreshAssignment,
})
```

`fetchWorkspace(undefined)` is conversation-first `/api/support/workspace`:
it must not create a ticket. A request ID selects `/requests/{id}/workspace`.
Its trusted JSON is `{ apiBaseUrl, conversationId?, principalNamespace?,
conversationHandover?, configuration? }`. Asset metadata may accompany it but
is not executed by this helper. Principal namespaces partition ticket episodes:
an explicit episode switch clears current targets, drafts, attachments and old
read observations through the engine's existing reset. Layout/theme/minimize
changes preserve all that state. Replacing the account requires destroying the old
controller/frame and creating a fresh mount. `getRuntimeBootstrap(request)` is
an alternative callback returning `{ widgetConfig, hostSession?, conversationId? }`
for hosts with a custom reviewed route map.
Historical episodes are always read-only, even while the ticket is OPEN or an
archived control state says AI_ACTIVE. Selecting the latest episode restores
editability only when the request status is OPEN. Bootstrap remains ticket-scoped.

`createIsolatedWorkspaceHost<SupportChatApi>(asset, { layer })` supports the
application's existing stacking order. Choose a layer above the persistent
dock but below application modals. Installer hosts can supply the same option
as `LoomAIWorkspaceHost.presentation.layer`. The default is suitable for the
floating public widget. `setTheme` and `setVisible` never replace the engine.

Intake extension `read()` returns an untrusted `hostContext` object, never merged
into generic ownership/scope fields. The BFF must authorize its resources and
consent. `onRequestSelected` lets the host mount diagnostics outside this pack.
An injected `api: SupportRequesterApi` can replace HTTP transport entirely.

## Requester Wire Contract

Direct mode uses the exact reviewed requester origin and
`/api/desk/requester/v1/spaces/{spaceId}`. A trusted `apiBaseUrl` is used verbatim
as the collection root, so `/api/support` never acquires a Desk path prefix.

Session follows Desk `TicketContracts.Session`: `{ authenticated, requesterId,
space: {spaceId, providerBindingId, humanHandoverEnabled, ...}, capabilities: [...] }`.
Capabilities are exact enums `CREATE`, `LIST`, `GET`, `RESOLVE`, `CANCEL`, `REOPEN`,
`SEEN`, `LINK_CONVERSATION`, `OBSERVE_ACTIVITY`; unsupported operations are disabled, not emulated.
List uses `{items,nextCursor}` and bounded `limit=20` (client maximum 50).
Requests carry opaque string `version` and commands send string `expectedVersion`.
Create is `{subject,details,topic?,existingConversationId?,hostContext?}`.
Start sends only `{expectedVersion}`; server state supplies opening text/key.
Mutations, including `seen`, use stable idempotency keys. Seen consumes the
returned ticket/version and never moves a marker beyond trusted `updatedAt`.
An uncertain create locks the original
input/key for explicit retry. There is no automatic write retry or provider fallback.
Ordinary HTTP operations are bounded at 15 seconds; opening-message start has
its own 130-second deadline for the existing real-provider execution window.

Direct linking/start/episode/activity additionally sends `X-LoomAI-Runtime-Authorization` from a
runtime credential callback. Explicit `host-mediated` mode delegates that proof
to the BFF; it does not retry failed direct authentication as a private request.
Canonical message routing follows the manifest's `conversationMessageUrlTemplate`,
independently of the human handover UI flag. A tickets-only runtime can expose
canonical message/control routes without exposing human handover commands.
All authorization remains server-owned. In-flight requester account changes
reject stale reads. Credentials stay in memory and requests use no-store and
redirect-error. Configure server CORS and CSRF for the actual identity profile.

Ticket save precedes runtime start. Linked-only episodes never resend intake.
Delivery observations remain separate from ticket status and AI/human control.
Resolve/cancel/reopen never synthesize chat messages or transfer conversation
authority. `newEpisode?(request, key)` posts `{expectedVersion}` to
`/requests/{id}/episodes`. Only an OPEN request with its actual current/latest
chat control CLOSED offers explicit continuation. The new episode must be
initialized, empty, `openingMessageRequired: false`, delivery `NOT_REQUIRED`.
The pack refreshes bootstrap and opens it without `/start`, retaining previous
episode links. Before returning a new empty episode, the provider must use the
authorized existing canonical `/control` route to initialize its owner scope,
then verify its canonical item can be read. Item GET alone returns 404 before
initialization. Desk owns this sequence in `TicketRuntimeClient.initialize`;
the pack does not invent a message or silently repair a missing provider step.
The strict browser fixture enforces that 404 boundary, tests direct requester
credentials and ordering, and verifies visible failure if initialization is
omitted. Checking an uncertain opening performs a ticket readback only.
New forms never offer to relink a conversation already known to belong to a
request; an unticketed owner conversation may be explicitly linked.

`activity?(request, key)` posts `{expectedVersion}` to `/requests/{id}/activity`.
The engine's `canonicalRevision` advances for successful history reads and
accepted runtime/control updates, not presentation renders. Updates coalesce
with a 10-second minimum observation interval; failed observations are not
timer-replayed. Activity uses the returned provider version. A seen marker is
sent only after the current/latest conversation is actually visible, never
from metadata polling, minimized chat or a mobile list hiding history.

Generic team photos use the authenticated scoped `/team/{profileRef}/photo`
endpoint, never public staff IDs or emails. The client returns image blob URLs
and disposes them on account invalidation or `destroy()`. Hosts overriding
`team()` remain responsible for their own photo lifecycle.
Team names denote assignments, never online availability. Fixture
previews are explicitly synthetic and never contact live services.
