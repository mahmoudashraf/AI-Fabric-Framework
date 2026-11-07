# Support Experience Host Adapters

Current `support@1.0.0` source contract. Console is one private-host integration,
not a dependency of the generic pack. This guide explains composition and APIs;
it does not claim a completed live installation or answer-quality qualification.

## Reuse The Pack, Not A Second Support UI

The pack supplies chat/form/My Requests/detail presentation, provider capability
checks, ticket lifecycle controls and links to canonical conversation episodes.
The existing Max Mode engine handles chat inside one integrity-checked isolated
frame. The host supplies identity and its reviewed transport/projections; it
does not replace generic ticket logic, copy transcripts or add a provider branch
to the pack.

Use the exports and types in [src/index.ts](../src/index.ts) and
[src/types.ts](../src/types.ts). The generated ESM bundle exports
`mountInstallation`, `createRequesterApi` and validators; generated declaration
files describe the public types.
The [pack README](../README.md) covers assets, configuration and script installation;
the [Desk requester contract](../../../product-services/loomai-desk-service/SUPPORT_TICKETS_API.md)
defines the server wire API. Generated bundles are build outputs, not evidence
that an installation is active.

## Docked Input And Max View

Trusted hosts can opt into the existing companion input, rather than embedding
an always-open Max view inside a small floating support page:

```ts
widgetConfig: { host: { companionDock: true } },
presentation: { mode: 'docked', container: viewportContainer },
```

`viewportContainer` is a connected, host-owned layout region covering the space
available to the workspace. The integrity-checked frame host must support
`setContentClipping`: it clips the collapsed/expanded input dock so transparent
iframe space does not intercept clicks on the host page. Max view and handover
dialogs use the full region. This is presentation isolation, not a new auth path.

Switch with `controller.setPresentation('page', pageContainer)` for the dedicated
support page, and back to `docked` for ordinary pages. Reuse one controller and
frame; do not remount on navigation or theme changes. The engine retains the
conversation, attachments and unsent chat draft. Default page/widget/standalone
mounts do not opt into companion input automatically.

Console uses the Clarity preset with a teal accent and the current Console color
scheme. Its dedicated Support page retains the assigned roster, request form,
My Requests, diagnostics and ticket lifecycle. Ordinary Console pages show only
the native input dock plus small support-page/move/minimize controls. Forms and
request navigation return to the Support page; they do not create a second chat.

## Choose An Explicit Identity/Transport Mode

For supported direct Supabase installations, the standard installer consumes
the activated manifest and mounts the pack without a customer backend:

- Original provider JWT authenticates Desk's registered requester facade.
- A distinct deployment-local broker token authenticates canonical chat.
- Start/link/episode/activity calls additionally send that runtime token as
  `X-LoomAI-Runtime-Authorization` to the registered Desk facade.
- The host never treats a runtime token, email, agent ID or service key as the
  requester's original provider identity.

For an authenticated private host, trusted application code injects a
`SupportHostAdapter`. The browser calls its BFF with the actual host session.
The BFF verifies that session, derives requester/resource ownership, and provides
registered short-lived requester assertions plus exact runtime proof server-side.
Select `runtimeCredentialMode:'host-mediated'` explicitly. A failed direct login
must never be retried automatically through the private adapter.

The Console's `platform-support-case-host` profile and `/api/support` routes are
one such host implementation. Other hosts implement their own reviewed adapter;
they do not adopt Console roles, account tables, diagnostic tools or fixed paths.
Arbitrary identity providers are not automatically supported by adding a token
callback: the server must have a matching registered requester identity contract.

## Trusted Mount Contract

`SupportMountContext` supplies the installed configuration, exact installation/
assignment, engine/frame, layout and credentials. Trusted code supplies
`supportHost`, or installer hosts use `LoomAIWorkspaceHost.extensions.support`.
Never deserialize executable adapters/callbacks from public configuration.

The following is a composition sketch. `loadOwnedWorkspace`, token/session
functions and `frame` belong to the host's authenticated, integrity-checked setup:

```ts
const lifetime = new AbortController()
const controller = await mountInstallation({
  signal: lifetime.signal,
  installationId,
  assignmentRevision,
  deliveryMode: 'embedded',
  connectionMode: 'backend-mediated-private-runtime',
  configuration,
  widgetConfig: {},
  hostSession: { namespace: initial.principalNamespace, accountKey: opaqueAccountKey },
  presentation: { mode: 'page', container },
  maxMode: frame.maxMode,
  workspaceFrame: frame,
  credentials: { getIdentityAccessToken, getIdentity },
  supportHost: {
    apiBaseUrl: 'https://host.example/api/support',
    runtimeCredentialMode: 'host-mediated',
    getIdentityAccessToken,
    fetchWorkspace: requestId => loadOwnedWorkspace(requestId),
    onRequestSelected: request => selectHostContext(request?.id ?? null),
  },
  refreshAssignment,
})
```

Register the host's unmount/account-change cleanup to call `lifetime.abort()`
**before awaiting the mount**. Cancellation synchronously disposes pack observers,
subscriptions and its owned frame even while session/bootstrap/history reads are
pending. The mount rejects with the abort reason; late responses cannot reattach
the old controller or start follow-up roster/bootstrap reads. Hosts should also
abort their own network adapter and keep their existing identity checks.
Do not destroy a mounting controller's frame independently without cancelling
its lifetime first. After a normal mount, explicit `controller.destroy()` remains
supported and idempotent; calls on a disposed controller remain errors.

`apiBaseUrl` is the **collection root** used verbatim. It does not receive an
extra Desk prefix. `getIdentity()` returns authenticated status plus an opaque,
stable `subjectKey`; account change invalidates in-flight private reads.
When the account changes, destroy the controller/frame and recreate the mount.
Do not reuse another account's requests, draft, portrait blobs or subscriptions.

`fetchWorkspace(undefined)` starts owner-scoped chat without saving a ticket or
submitting a synthetic message. A request ID selects that owned ticket's current
canonical conversation. The trusted result has:

```ts
{
  apiBaseUrl,       // exact reviewed canonical API root; no token in the URL
  conversationId,
  principalNamespace,
  installationId,
  assignmentRevision,
  configuration,
  conversationHandover,
}
```

Asset URL/integrity metadata may accompany the response. The host must validate
the expected origin/route and unchanged installation/assignment before use;
the callback must not choose a destination from ticket text or user form fields.
`getRuntimeBootstrap(request)` is the alternative typed callback for hosts that
already supply a reviewed `widgetConfig`/route map. Neither route is a generic
proxy or authority grant.

## Provider-Neutral Ticket API

`createRequesterApi` handles authenticated no-store requests. A host with a
different transport can supply `supportHost.api: SupportRequesterApi` instead,
while retaining the same pack/store and native shapes:

- Session: `{authenticated, requesterId, space, capabilities}`.
- List: `{items, nextCursor}`; Ticket includes `id`, `reference`,
  `providerBindingId`, `subject`, `status` and opaque string `version`.
- Create: `{subject, details, topic?, existingConversationId?}` plus a retained
  idempotency key. The provider owns the saved reference and opening delivery.
- Start, resolve/cancel/reopen, seen, explicit continuation and activity forward
  the provider's unchanged string `expectedVersion`. Capabilities decide which
  operations are available; unsupported operations are not locally emulated.

Save happens before start. A real saved receipt remains visible if AI delivery
fails. Uncertain writes retain their original input/key for explicit inspection;
the transport must not auto-retry with a new key or fall back to another provider.
Linking an owned existing conversation does not enqueue intake again. Explicit
continuation of a terminal latest episode is empty and must be initialized by
the server before canonical history read; historical episodes remain read-only.

Ticket status, delivery observation and AI/human conversation control are three
different states. Resolving a ticket does not transfer control to AI or close
chat. A ticket-only installation can use canonical messages with human handover
disabled. The server must enforce ownership and runtime control, not trust UI
disabled buttons.

## Console Reference Mapping

Console uses the real pack in
[SupportWorkspace.tsx](../../../Platfrom/ui/src/components/customer/SupportWorkspace.tsx),
not a copied chat/form implementation. Its
[supportApi.ts](../../../Platfrom/ui/src/api/supportApi.ts) adapts transport and
the roster; its backend guide is
[Console Support host adapter](../../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/support/README.md).

- Request collection/session/lifecycle: `/api/support/session`,
  `/api/support/requests` and `/api/support/requests/{id}/...`.
- Conversation-first bootstrap: `GET /api/support/workspace`; ticket bootstrap:
  `GET /api/support/requests/{id}/workspace`.
- The returned API root scopes canonical message/control/history to that
  request or pre-ticket conversation; no runtime/private key reaches the pack.
- The generic pack's roster is `SupportAgent[]`. Console's endpoint returns
  `{agents:[{agentReference,displayName,photoAvailable}]}`; its host adapter maps
  `agentReference` to `profileRef` and fetches authenticated portrait blobs.
  Photo cleanup stays the overriding host's responsibility.
- Console resource selection and consent use separate `/requests/{id}/context`
  and `/context/consent` endpoints. Ticket version, context revision and consent
  version are different concurrency tokens. They are not form ownership fields.

The host may use `onRequestSelected` to show its own resource editor alongside
the pack. `intakeExtension(container)` can return `read/validate/destroy`; its
`read()` result is untrusted `hostContext`, not a generic ticket or identity field.
The BFF must explicitly consume and authorize any such extension. Console's
current integration edits resources/consent separately after ticket save; the
generic pack does not implicitly persist or grant access to those selections.

An assigned-agent row is separate from ticket/diagnostic permission. Names and
portraits show assignments, not presence. Human controls require configured
handover, effective provider capability and actual authorized agent assignment.

## Layout, Lifetime And Qualification

Keep one controller/frame per document in the persistent SPA layout. A contained
page needs a connected local element with bounded height and `min-height:0` in
flex layouts. Use `setPresentation`, `setVisible` and `setTheme` for layout/theme
changes; do not reinitialize chat on route navigation or minimization. Explicit
ticket/episode selection changes the conversation independently of layout.
Same-user token refresh or tab-focus sign-in events also keep the existing
controller; revalidate server permissions without treating them as an account
switch. Sign-out and a genuinely different subject must clear the prior scope.
The engine owns live conversation delivery. The shell polls request lists and
details, not chat on tab focus, and does not poll while editing an intake form.

The standard script supports `data-presentation="page"` with a bounded
`data-container="#support-root"`, or `widget`; standalone uses its normal
activated app address. The frame isolates styles for trusted code, not arbitrary
third-party scripts. Keep SRI and registered origins intact.

Check the chosen identity mode independently: own/foreign real ticket IDs,
expired/revoked identity, duplicate/uncertain writes, provider outage, canonical
start/history/empty continuation, human-disabled behavior, account switching,
roster/photo cleanup and page/widget/SPA navigation. The pack's `tests/core`,
`tests/browser` and `tests/installer` cover source fixtures; backend authorization,
actual provider connectivity and AI answer quality need separate live evidence.
Do not infer hosted closure from this guide or a local fixture pass.
