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
- `knowledge` documents/source/storage tasks -> KNOWLEDGE; `knowledge/search`
  -> SEARCH; `models` and `advanced/providers` -> MODELS. SEARCH uses the same
  existing provider draft service with a retrieval-specific supported catalogue.
  AI cannot create external-processing consent, provider secrets or allowances.
- `responses`, `responses/instructions`, `responses/advanced` -> BEHAVIOUR.
- `human-support` -> HUMAN_SUPPORT. `action-reviews` uses that same task in
  guide-only mode and cannot make live review decisions.
- `human-support/specialists` -> SPECIALISTS. The Shell renders the existing
  customer specialist form on this wildcard route; it does not duplicate it.
- Brain overview/configuration JSON -> KIT, guide-only. Other Brain pages,
  including Updates, Try it, Tasks, secrets and runtime/recovery/diagnostics,
  offer the same guide-only assistance without new editing authority. Secret
  values are never added to assistant context.
- MCP setup uses the shared ACTIONS panel. Explicit Help configure attaches
  only the selected owned catalogue metadata; there is no second assistant.
- Saved private plugin editor -> PRIVATE_DATA_PLUGIN / pluginId / DATA_PLUGIN.
  New unsaved definitions and immutable versions have no assistant entry.
- Saved Engage installation -> ENGAGE_APP / installationId / ENGAGE. Active
  installations are preview-only; the assistant never disables them.
- Saved customer Kit details -> DEPLOYMENT_DRAFT / kit.deploymentId / KIT,
  guide-only, with fixed links to actual Brain/Engage editors. This is the narrow
  fourth integration explicitly requested after the original three-file scope.

## Behavior

Build with AI opens on entry to a Smart Brain. It is closable and the header
button remains available to reopen it. Visibility belongs to the current
actor/workspace/Brain visit, so dismissal survives subpage/task changes. A new
Brain or a fresh visit opens it again. Assistant sessions, inputs and operation
state still use their existing resource/task identities; visibility does not
broaden authority or copy conversations between tasks. Other resource editors
retain their existing opening behavior. On narrower screens it uses the same
closable drawer, not a second mobile implementation.

The Brain library uses a compact list and guided inspector; technical facts and
existing manual links remain under Technical details. The root Brain assistant
links to the existing documents, capabilities and response editors. On the
capabilities page, the outcome table refreshes from the saved draft. Access and
confirmation labels come from declared policy fields; missing optional fields
are not a new execution gate. Opening the list does not mount a dirty action
editor or select a capability automatically.

Turns save editable drafts without per-turn approval. AI activity does not lock
manual editors, footer commands or page navigation. The intended workflow is
AI finishes, then the user reviews and edits before publishing; requested AI
changes may replace manual values without a conflict-approval step. Normal
manual-save pending controls and unsaved-navigation confirmation remain.
Successful SAVED receipts invalidate resource queries and reload
the selected editor, including unchanged draft IDs/revision counters. Brain page
local state can reset on that remount; route/environment and unrelated pages
are retained. A failed editor reload is visible and can be retried with Refresh;
it does not lock manual input or leave the assistant permanently busy. Undo
uses UNDONE/undoReceipt and the normal resource reload.

Only an actor/resource/task-scoped session ID is stored in sessionStorage.
Restoration GETs the session; it never replays inference or saves. Lost turn
responses offer retry using the original Idempotency-Key. Poll/cancel errors are
visible; old poll responses cannot supersede a newer command.

Earlier helper conversations are read-only after the configured-Brain cutover.
Start Build with AI opens the current configured session without deleting the
earlier one or automatically sharing its sources/history. Session options can
return to the earlier conversation after reload. Only its scoped session ID is
retained in sessionStorage; viewing old save receipts does not reload the manual
editor or replay a mutation.

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

Task guidance collapses after the first turn. Session controls and raw resource
identifiers are under Session options. Human-readable status labels use exact
server statuses, never model prose. A SAVED receipt exposes Not published and
an expandable structured diff, plus a Review draft link to the existing Updates
page. Errors, validation findings and the original model message remain visible.
The shared controlled conversation's optional submitContent and placeholder
props change presentation only; other consumers retain their existing defaults.

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

`hosted.journey.browser.mjs` is a separately opted-in real-provider UI proof. It
requires a fresh, owned, configuration-only Brain and a public GET-only OpenAPI
fixture. It signs in normally, imports the reference without inference, starts
an explicit preparation turn, checks the real draft receipt and outcome table,
inspects the diff and navigates to review without publishing or provisioning.
It also checks the narrow-screen assistant and close/reopen behavior. Set
`ALLOW_ASSISTANT_DRAFT_WRITE=1` and the documented environment names at the top
of that runner only for a disposable authorized fixture. Credentials, sources,
session IDs and evidence are kept outside the repository. Clean up that exact
fixture and its sessions afterward; this test is not an infrastructure or
runtime-chat qualification.

Where a staging control plane has no enabled customer starters, an explicit
`LIBRARY_BRAIN_ID` can qualify library entry on an existing owned Brain before
testing the isolated draft editor. Reports mark that split proof with
`singleBrainJourney:false`; it is not a substitute for the single-Brain customer
journey on the production control plane. Mobile evidence waits for the drawer
transition to settle rather than capturing a closing panel.
