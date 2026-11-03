# Tickets UI Contract

Scope: the existing operator Desk, requiring the actual `SUPPORT` role on the
selected binding. `OWNER` and `REVIEWER` alone grant no ticket access.

The UI consumes the provider-neutral ticket shape published by the backend
agent, with `id`, `reference`, `providerBindingId`, `subject`, `details`, `topic`,
`status`, opaque string `version`, `createdAt`, `updatedAt`, `unread`,
`openingMessageRequired`, nullable `conversation`, `episodes`, `events` and
`capabilities: string[]`. No provider-specific UI or revision conversion.

- `GET /api/desk/v1/bindings/{bindingId}/tickets?limit=50&status=OPEN&cursor=...`:
  `{items: Ticket[], nextCursor: string | null}` with bounded page navigation.
- `GET /api/desk/v1/bindings/{bindingId}/tickets/{id}`: normalized ticket detail.
- `POST .../tickets/{id}/resolve`: `{ "expectedVersion": "opaque-version" }`,
  `Idempotency-Key` header, existing session cookie and session CSRF header;
  normalized updated ticket response. No automatic write retries.

Intake uses the normalized `details` and `topic`. `events` contains
`{type,actorId,createdAt}`; the UI displays event labels/timestamps without
reconstructing history or exposing raw actor identity. `upstreamStatus` is a safe
optional label. Episodes contain `episodeId`, `conversationId`, `number`,
`openingMessageRequired`, `delivery` and `createdAt`. Runtime URLs are not rendered.

Only explicit `AGENT_RESOLVE` capability plus `OPEN` state enables agent resolution.
The confirmation freezes the inspected version; a polled change requires
review and a new confirmation. 409 never automatically retries a mutation.
Uncertain identical manual retries reuse the command identity.

Ticket detail reads the existing authorized binding's `/conversations?limit=100`
queue and matches the exact `externalConversationRef` to an episode's
`conversationId`. Only that real queue's `handoverId` opens the existing
Conversations view. No runtime conversation/transcript endpoint, takeover,
claim, reply, return-to-AI or close is called by Tickets. Those commands stay in
the actual existing conversation controls after authorized handover. Resolving
a ticket changes no conversation authority or status. Older handovers outside
the latest 100 are not claimed available; neither are failed queue reads.

This document records the UI integration contract, not hosted qualification.
The backend contract remains authoritative; align these projections at
integration rather than adding compatibility aliases.
