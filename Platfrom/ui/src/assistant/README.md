# Configuration Assistant UI (010.37)

Console owns the session, transport, source selection and resource refresh. The
controlled React conversation view is imported directly from the canonical
`max-mode-widget/src/controlledConversation.tsx` source, not a copied widget or
runtime controller. It has no draft, action, tenant, authentication or network
semantics. Vite dedupes React; the UI TypeScript paths and Docker's single-file
COPY support that source boundary in clean builds.

## Entry Points

- Smart Brain: `capabilities` / action identities -> ACTIONS; formal action
  connection view -> CONNECTIONS. `knowledge/connections` is the live-data
  source view and uses KNOWLEDGE, not outbound action routing.
- `knowledge` documents/source/storage tasks -> KNOWLEDGE; `knowledge/search`,
  `models` and `advanced/providers` -> MODELS.
- `responses`, `responses/instructions`, `responses/advanced` -> BEHAVIOUR.
- `human-support` -> HUMAN_SUPPORT. `action-reviews` uses that same task in
  guide-only mode and cannot make live review decisions.
- `human-support/specialists` -> SPECIALISTS. The Shell renders the existing
  customer specialist form on this wildcard route; it does not duplicate it.
- Brain overview/configuration JSON -> KIT, guide-only. No assistant entry on
  runtime/recovery/diagnostics, publication, Try it or capability installation.
- Saved private plugin editor -> PRIVATE_DATA_PLUGIN / pluginId / DATA_PLUGIN.
  New unsaved definitions and immutable versions have no assistant entry.
- Saved Engage installation -> ENGAGE_APP / installationId / ENGAGE. Active
  installations are preview-only; the assistant never disables them.
- Saved customer Kit details -> DEPLOYMENT_DRAFT / kit.deploymentId / KIT,
  guide-only, with fixed links to actual Brain/Engage editors. This is the narrow
  fourth integration explicitly requested after the original three-file scope.

## Behavior

Turns save editable drafts without per-turn approval. The selected editor and
footer are disabled/inert while a request runs; modal command buttons also use
the busy state. Successful SAVED receipts invalidate resource queries and reload
the selected editor, including unchanged draft IDs/revision counters. Brain page
local state can reset on that remount; route/environment and unrelated pages
are retained. A failed editor reload keeps manual input paused until Refresh
succeeds. Undo uses UNDONE/undoReceipt and the normal resource reload.

Only an actor/resource/task-scoped session ID is stored in sessionStorage.
Restoration GETs the session; it never replays inference or saves. Lost turn
responses offer retry using the original Idempotency-Key. Poll/cancel errors are
visible; old poll responses cannot supersede a newer command.

New imports and explicit same-resource reuse are selected by ID. Visible document
chips open an authenticated redacted-content review dialog on request; import
does not force another modal step. Every turn sends sourceIds
explicitly: [] shares no sources. operationIds:[] means whole selected sources.
Paste MIME is explicitly selected; no prose-based format or intent matching.
Source removal confirms that conversation history is also cleared, while saved
draft changes remain. All rich content is rendered as escaped React text.

`pagePresentation.ts` and typed `editorContext` register task guidance and
preparation commands. `PREPARE` sends an optional hint, including an empty query,
to the existing turn endpoint; Platform resolves its registered intent. Plain
messages retain the existing path. No navigation, opening, source import or
guidance expansion triggers inference. Same-task sections refresh their context
per turn without creating another assistant or granting new writable paths.
The transcript uses the server-retained command/context/hint for concise request
labels, not interpretation or rewriting of model output. Missing-information
answers use the ordinary message composer. Preview, guide-only, bounded feedback,
save and undo still use the same controller. Turn options disclose correction,
preview and explanation settings without crowding the default composer.

## Verification

From `Platfrom/ui`:

```sh
node --test src/assistant/*.test.mjs
node src/assistant/assistant.browser.mjs
npm run build
```

The browser suite uses actual Console components and controlled authenticated
API fixtures, not live inference. It covers desktop/dark, 320px/light, source
review/reuse/selection, preview/apply, receipts/runtime metadata, undo, cancel,
idempotent retry, same-revision real Brain editor refresh, footer/navigation
locking, plugin immutability, and actual Specialist/Kit route entry points.
Screenshot paths are printed by the suite; ASSISTANT_EVIDENCE_DIR can set their
destination. No hosted rollout, real-provider quality or cross-tenant backend
qualification is implied by these local UI tests.
