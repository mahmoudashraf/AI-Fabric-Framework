# Customer Support Kit Host

The existing Console customer shell hosts the real
`experience-packs/support-experience` pack. React owns account authentication,
page routing, dock position/minimization and resource-check consent only. The
pack owns the request form, My Requests, detail, provider capabilities, opaque
ticket versions, command identities and lifecycle behavior. The existing chat
engine remains the only transcript/composer/handover implementation.

## Routes And Persistence

- `/support` shows the current support experience without resetting its view.
- `/support/requests` delegates to `controller.showRequests()`.
- `/support/:caseId` delegates the opaque route ID to `controller.openRequest()`;
  the route parameter name is internal, not an old case API or data reader.
- `?new=1` opens the pack's request form, not a second Console form.

One controller and one generic isolated workspace frame live beneath the
customer shell, independently of route components. Page/dock, left/right,
minimize, diagnostics dialogs and light/dark changes never replace that frame.
The pack's `setVisible` and `setTheme` APIs preserve its draft and attachments.
Only side, minimization and the selected request ID are stored in a new
customer/actor/Supabase-subject-scoped UI preference key. No old preference or
ticket-history migration is provided.

Account changes key/unmount the provider, destroy the prior controller/frame,
and revoke portrait object URLs. Async bootstrap and token reads verify the
same customer, actor and Supabase subject before accepting results. Tokens stay
in memory and are freshly resolved from existing Console authentication. No
private runtime assertion or service key reaches the browser.

## Exact BFF Contract

Prefix: `/api/support`. `SupportRequesterApi` and request DTOs are imported from
the reusable pack rather than independently defined by Console.

- `GET /session`: normalized requester `SupportSession` with effective space,
  provider binding and capability names, without server secrets.
- `GET /requests?status=OPEN&cursor=...&limit=20`: `{items,nextCursor}`.
- `POST /requests`: `{subject,details,topic,existingConversationId?}` and
  `Idempotency-Key`. Console adds no resources or implicit diagnostic consent.
- `GET /requests/{id}`: provider-neutral ticket (`subject`, opaque string
  `version`, `conversation`, `episodes`, events and capabilities).
- `POST /requests/{id}/start|resolve|cancel|reopen`:
  `{expectedVersion: string}` and `Idempotency-Key`; updated ticket response.
- `PUT /requests/{id}/seen`: the pack's last-seen observation with
  `Idempotency-Key`; the returned ticket/version is consumed before later commands.

The injected API uses the pack's requester transport directly. Only `team()` is
adapted for Console's existing safe response:
`{agents:[{agentReference,displayName,photoAvailable}],...availabilityFlags}`.
It maps `agentReference` to `profileRef` and loads optional JPEG portraits from
the current authenticated `/team/{reference}/photo` bridge into owned blob URLs.
There is no external image credential, private Desk avatar URL or invented
presence. Missing portraits use the pack's initials.

### Workspace Bootstrap

`GET /workspace` supplies an owner-scoped conversation-first bootstrap with no
ticket creation. `GET /requests/{id}/workspace` supplies its authorized linked
episode. Both return:

```ts
{
  apiBaseUrl: string
  conversationId: string
  principalNamespace: string
  installationId: string
  assignmentRevision: string
  configuration: Record<string, unknown>
  workspaceAssetUrl: string
  workspaceIntegrity: string
  experienceModuleUrl: string
  experienceIntegrity: string
  conversationHandover?: { enabled: boolean }
}
```

The real support pack is bundled from its shared source into the Console build.
The generic chat engine is loaded through the shared
`createIsolatedWorkspaceHost` helper, with the exact Platform-origin
`/api/public/ai-workspace/` asset and SHA-384 SRI. The host also validates the
declared experience asset reference. Runtime targets are exactly
`/api/support/conversations/{serverGeneratedConversationId}/runtime/api` for chat-first or
`/api/support/requests/{encodedId}/runtime/api` for a selected request. Canonical
conversation routes remain `/chat/me/conversations/{conversationId}` plus
messages/control/events and existing handover operations.

Installation and assignment remain stable during a mounted session. Changes are
visible failures requiring an explicit reload, not a silent rebind. The server's
principal namespace changes on explicit ticket/episode selection, resetting
cross-ticket chat state rather than retaining attachments or current targets.
Layout-only changes preserve that state. Ticket creation/start happens only through the pack's explicit
submit/delivery commands. Navigation, detail reads and reload do not replay them.

## Resource Permission

The host-specific **Configure diagnostics** dialog is available on a selected
request, never as implicit generic intake authority.

- `GET /requests/{id}/context` returns
  `{ticketId,resources,consent:{granted,version,changedAt,revokedAt},revision}`.
- `PUT /requests/{id}/context` sends `{resources,expectedRevision}`.
- `PUT /requests/{id}/context/consent` sends `{granted,expectedVersion}`.

Context/consent revisions are independent numeric values, not ticket versions.
Resource editing disables consent until the resource write succeeds. Resource
changes revoke permission; granting permission is a separate explicit action.
Errors refetch current context but never replay a write or regrant permission.
The server remains authoritative for owned resource IDs and active membership.
No ticket subject, business status or transcript is stored in this host context.

## Local Verification

Build the current generic widget asset first, then run from `Platfrom/ui`:

```sh
npx tsc -p tsconfig.app.json --noEmit --incremental false
npx vite build
node --test scripts/customer-support-pack-host.test.mjs
npm run smoke:customer-support
npm run smoke:support-team-intake
```

The repository-root Railway image build includes the support pack source and
the complete widget source tree (including the isolated installer helper):

```sh
docker build -f Platfrom/ui/deploy/railway/Dockerfile -t loomai-console-support-ui:local .
```

The customer smoke uses the real pack source and built chat engine with explicitly
synthetic BFF responses/portraits. It covers chat-first without tickets, pack
creation and opaque versioning, one-frame draft/attachment retention, dock/SPA
and theme changes, resource ownership choices, independent consent, My Requests,
deep links, no start replay, current bearer use, uncertain-create identity,
mobile geometry, logout cleanup and staff-shell isolation. Evidence defaults to
`/private/tmp/loomai-support-ui-01057`; override with `SUPPORT_UI_OUTPUT`.

The former `/support/team-intake` route now links to the existing trusted Desk
sign-in destination using the shared identity-navigation policy. There is no
Console staff ticket list or `/api/support/staff/intake` caller. Desk owns agent
authentication and ticket authorization; Console membership never grants it.

Local fixtures do not prove hosted token validation, real S3 knowledge,
provider delivery, Console cutover or AI answer quality. Main owns those checks
and release/deployment.
