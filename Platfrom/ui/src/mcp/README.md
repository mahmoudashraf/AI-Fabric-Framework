# Customer MCP Connections

Customer and delegated-staff entry: Smart Brain -> AI capabilities -> **Connect
MCP** or **MCP connections**. The existing Brain target selector determines the
environment. There is no second environment picker or personal-account mode.

Routes reuse `/smart-brains/:brainId/capabilities`:

- `?view=mcp`: all owned connections, with their explicit selected-target binding.
- `?view=mcp&mcp=connect`: progressive public/business setup.
- `?view=mcp&mcp=:connectionId`: connection, authorization and tools.
- `mcpOAuth=connected|cancelled|failed`: backend callback notification only;
  the UI refreshes saved connection status and removes the notification. It does
  not infer authorization from the URL.

## Boundaries

- `api.ts` uses the existing authenticated `platformRequest` client and lossless
  JSON handling. It does not modify the large shared API module.
- Only the authorize command uses the shared `browser-bound` credential policy
  to retain Bearer authentication while accepting the backend's HttpOnly
  browser-binding cookie. Ordinary requests remain cookie-free with Bearer.
  Only discovery requests the extended 75-second timeout for bounded traversal.
- Public connections omit all upstream credentials. Business forms use saved
  Brain-owned references, company-owner OAuth or service credentials. No saved
  secret values are fetched or shown.
- List/snapshot reads are Platform metadata only. Discovery, OAuth start,
  disconnect, import and assistant attachment require explicit user actions.
- The backend validates OAuth state, grants and callback redirects. The browser
  handles only the authorization URL and nonsecret saved status. A provider
  popup has no opener; a blocked popup has an explicit same-tab continuation.
- Native tool schemas are read-only. Provider hints do not select action
  authority. Incompatible tools remain visible but cannot be selected.
- Refresh compares exact tool names/schema hashes with this connection's
  imported actions only. New/schema-changed/unchanged/not-returned counts and
  per-tool status are read-only. Missing or conflicting hashes need review;
  incomplete catalogues do not prove removal. Absent draft data is not treated
  as an empty baseline. No refresh deletes or updates an action automatically.
- New tools default to confirmation required, signed-in access, unverified
  READ_WRITE and no answer grounding. Existing policies come from the saved
  draft; only explicit field changes are sent during reimport.
- Incomplete/stale snapshots cannot be imported. Actual server receipts show
  created/updated counts and readable saved before/after changes (purpose,
  access, confirmation, answer evidence and schema changes), with exact JSON
  under Advanced snapshots. Undo this
  import uses the current receipt and the backend's actions-draft guard, not an
  assumed rollback. A rejected undo retains the real receipt and error. Receipts
  link to the existing Updates workflow. No automatic
  publish, apply, provider inference or tool execution is performed.
- Optional Help configure reuses the existing assistant panel and session. Only
  the server-owned snapshot handle and selected exact tool names are attached.
  MCP connection routes suppress the outer generic assistant opener so two
  panels cannot edit the same assistant session at once.

The existing Brain environment selector controls the reuse toggle. The UI lists
all Brain-owned connections so a staging-created connection is visible while
viewing production. `allowedTargetProfileIds` is the authority for its displayed
binding; the source `targetProfileId` is not inferred authorization. A confirmed
`target-bindings` command enables/revokes only the selected environment, without
copying tokens, changing the connection revision, importing duplicate actions or
publishing. Editing from another environment preserves the original source
target. A disconnected connection cannot be enabled through the toggle.

## Verification

From `Platfrom/ui`:

```sh
node --test src/mcp/mcp.test.mjs src/assistant/scope.test.mjs
node src/mcp/mcp.browser.mjs
node src/mcp/actionEditor.browser.mjs
npm run build
```

The browser runner starts and closes its own local fixture server, checks actual
components with mocked Platform responses, blocks external browser requests and
writes screenshots into a temporary directory (or `MCP_UI_EVIDENCE_DIR`). It
covers public/business setup, credential headers, machine/company-owner OAuth,
permission/error states, scope changes, policy preservation, precise schema
numbers, optional assistant attachment, and light/dark desktop/basic phone.
These tests are not hosted provider, OAuth-server or deployment qualification.

The action editor retains the existing HTTP route/forms unchanged. MCP actions
show exact tool/connection/server references, link to the existing connection
panel, and present native schemas read-only. Their local input collection,
descriptions, typed defaults and trusted resolvers remain editable. Advanced
local JSON excludes native schemas and execution/auth, and preserves the
imported parameter contract while allowing local collection metadata changes.
No business-tool execution preview is introduced.

## Hosted Qualification (Await MAIN Rollout)

`node src/mcp/hosted.browser.mjs` is dry-run only: no private auth read, browser,
network call or live change. After MAIN confirms deployment, use
`node src/mcp/hosted.browser.mjs --run-live` (optionally `--headed`). Do not use
the live flag before that confirmation.

The runner uses the ignored private `~/.codex/private/loomai-mcp-01069/api.mjs`
customer helper, the production customer canary `dep-3623c3f2`, a fresh isolated
browser context and only the synthetic public/business fixture service. It
never saves browser auth state, cookies, tokens or raw provider responses.

It qualifies real Console onboarding, company OAuth/browser binding,
discovery, actual saved review/guarded undo, explicit production reuse/revoke
and basic phone layout. Browser-side non-MCP writes are blocked except the
Console's resource-selection preferences; no publish/apply, infrastructure,
inference or business-tool execution is permitted by this UI runner. Runtime
execution/release qualification remains MAIN's separate path.

Cleanup attempts guarded undo for this run's imports, disconnects only this
run's synthetic connections and logs out its fresh session locally. It never
force-restores a concurrently changed draft. Disconnected connection metadata
remains because no deletion API is exposed. Reports contain only nonsecret
check names, API paths/statuses and owned synthetic resource IDs. Failure is
reported by phase without printing credentials or provider response bodies.
