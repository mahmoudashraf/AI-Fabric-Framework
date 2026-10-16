# LoomAI Workspace One-Script Installation Guide

**Contract:** `loomai-ai-workspace-installation-v1`
**Audience:** customer website developers and implementation partners

LoomAI Platform can install the generic AI Workspace, its reviewed experience
pack, and its current assigned deployment connection from one public script.
The host page contains an opaque installation ID only.

This guide covers **embedded** delivery. For the full-page LoomAI Engage app,
domain routing, and entry/action-time login, use
[LOOMAI_ENGAGE_STANDALONE_AI_WORKSPACE_DELIVERY_GUIDE.md](../../Final_Documentation/External_User_Guides/LOOMAI_ENGAGE_STANDALONE_AI_WORKSPACE_DELIVERY_GUIDE.md).
Both delivery forms use the same installation, pack, assignment, and connection
contracts; standalone delivery does not require a customer page script.

## Configure In LoomAI Platform

An authorized operator prepares the installation before a website developer
adds the script:

1. Open Platform Console **AI Workspaces** and select the customer.
2. Choose **New workspace** and select the active consumer whose current
   verified deployment should serve the experience.
3. Select the reviewed connection mode and profile. Use direct anonymous
   runtime only for intentionally public, bounded capabilities; authenticated
   and private modes require their reviewed broker or adapter.
4. Add every exact HTTPS website origin that may install the Workspace. Do not
   use wildcard origins.
5. Complete the selected experience-pack configuration, then save the draft.
6. Resolve every blocking readiness check and activate the installation.
7. Select the active installation and copy the exact installation script shown
   by Platform.

An active installation is immutable through the edit form. To change its
public configuration, disable it, edit the same installation, re-run readiness
checks, and activate it again. Keep the same installation ID unless a separate
installation boundary is intentionally required.

## Install

Add the script supplied by the Platform **AI Workspaces** page:

```html
<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="awi_pub_0123456789abcdef0123456789abcdef"
></script>
```

Do not add a runtime URL, API key, provider credential, private assertion, or
customer token to the page. The installation ID is a public locator, not a
secret.

The script performs this bounded sequence:

```text
customer page
  -> Platform install.js
  -> origin-scoped installation manifest
  -> integrity-pinned generic workspace asset
  -> integrity-pinned reviewed experience pack
  -> assigned runtime or reviewed backend adapter
```

Platform serves configuration and assets only. Conversation, retrieval, and
action traffic does not pass through Platform after initialization.

## Connection Modes

| Mode | Browser data path | Host requirement |
| --- | --- | --- |
| `public-runtime-anonymous` | Directly to the assigned deployment after deployment-local anonymous bootstrap | Script only; intended for public-safe knowledge and bounded actions |
| `public-runtime-authenticated` | Host token broker, then directly to the assigned deployment | A reviewed server-side broker for the host's existing identity |
| `backend-mediated-private-runtime` | Reviewed backend adapter or bridge | A reviewed adapter that keeps private runtime assertions server-side |

The page uses the same script in every mode. Mode, routes, broker/adapter
profile, experience pack, and exact versions are Platform-owned installation
configuration. Customer JavaScript must not choose a deployment or elevate
scopes.

## Conversation Handover

When the assigned conversational deployment has the reviewed
`CONVERSATION_HANDOVER` extension, handover action, and
`CONVERSATION_CHANNEL` integration installed, the origin-scoped manifest
also advertises the generic handover capability and exact route templates.
The same one-script installation then provides:

- **Talk to a person**, structured optional intake, pending and human-active
  states, cancellation/retry, and explicit **Continue with AI** controls;
- canonical owner messages and an owner-authorized event stream, so AI is not
  called while a person owns the conversation;
- same-tab navigation and reload continuity for the conversation, messages,
  attachments, and anonymous owner proof; and
- the same behavior for a reviewed direct public runtime, authenticated
  runtime, or private backend-adapter route contract.

The website does not add provider credentials or operator logic. Platform
publishes only safe behavior configuration and these route templates:
`conversationMessageUrlTemplate`, `handoverRequestUrlTemplate`,
`handoverCancelUrlTemplate`, `handoverRetryUrlTemplate`,
`conversationControlUrlTemplate`, `conversationEventsUrlTemplate`, and
`aiResumeRequestUrlTemplate`. They are omitted when the extension is not
live, which keeps the UI hidden and the APIs unavailable.

The selected conversation-channel provider owns its operator login, queue,
claim, and reply experience. A configured operator-console link may open that
provider, but it is not exposed to anonymous website users.

## Origin And CSP Requirements

The exact website origin must be listed on the installation. Public runtime
modes must also allow it in runtime CORS and public-bootstrap policy. Wildcard
origins are not supported.

Allow these CSP sources:

- `script-src`: `https://api.loomai.pro`
- `connect-src`: `https://api.loomai.pro` plus the assigned public runtime, or
  the reviewed adapter origin for private mode
- `img-src`: only the image hosts approved for the selected experience pack

The manifest request sends no cookies. Authenticated brokers and private
adapters define their own reviewed cookie, CSRF, and origin behavior.

### Private Adapter Session Continuity

The installer includes a `browserSessionId` UUID in the
`loomai-workspace-private-adapter-request-v1` bootstrap request. It persists this
opaque correlation ID in `sessionStorage`, scoped to the installation,
assignment revision, adapter URL/profile, and host identity. The Shopify adapter
returns the same shopper-session header for that ID, so reload and same-tab
navigation can submit the restored conversation under the same anonymous owner.

This ID does not authenticate a customer. An authenticated adapter must still
derive authority from its verified server session. Runtime assertions, provider
tokens, and returned adapter headers are not persisted by this mechanism.
Assignment, adapter, or host-identity changes use a different session and
conversation namespace. Missing browser storage or corrupt session state stops
installation explicitly; it never silently sends an old conversation as a new
owner.

## Lifecycle Events

The installer emits browser events without breaking the host page:

```js
window.addEventListener("loomai:workspace-loading", ({ detail }) => {
  console.log("LoomAI Workspace loading", detail.installationId)
})

window.addEventListener("loomai:workspace-ready", ({ detail }) => {
  console.log("LoomAI Workspace ready", detail.connectionMode)
})

window.addEventListener("loomai:workspace-error", ({ detail }) => {
  console.error(detail.code, detail.message)
})
```

`window.LoomAIWorkspace.getController()` returns the selected experience
pack's optional host controller after `loomai:workspace-ready`.

`window.LoomAIWorkspace.refresh()` re-resolves the manifest and remounts the
experience. Use it only when an SPA or client-rendered page has materially
changed the visible page context after initial installation. Starting a new
conversation automatically revalidates the manifest; an assignment or
installation revision change reloads the page before creating the new session.
An active conversation is never replayed into another deployment.

## Optional Host Interaction

The one-script baseline needs no host bootstrap code. A reviewed experience
pack may expose optional methods after readiness, for example attaching the
currently selected domain item or sending a host-owned prompt. Wait for the
ready event and use only methods documented by that pack.

The generic widget also retains explicit programmatic initialization for
applications that are not managed as Platform installations. See
[EXTERNAL_DEVELOPER_GUIDE.md](EXTERNAL_DEVELOPER_GUIDE.md). Do not combine
explicit initialization and a Platform installation on the same page.

## Operational Checks

Before release, verify:

1. `install.js` returns JavaScript with short revalidation caching.
2. The allowed origin receives the installation manifest; another origin and
   a request without `Origin` receive the same unavailable response.
3. Workspace and pack scripts have `integrity`, `crossorigin="anonymous"`, and
   immutable caching.
4. Browser runtime traffic goes directly to the assigned deployment for
   public modes or only to the reviewed adapter for private mode.
5. Browser responses contain no provider credentials, Platform assignment
   credential, private runtime assertion, or signing material.
6. Disablement, reassignment, token expiry, and inaccessible conversations fail
   closed and offer a fresh session rather than replaying private history.
7. When handover is enabled, direct request, optional intake, pending,
   operator reply, reload/replay, and explicit return-to-AI all complete
   without an AI response during human ownership.
8. Private-adapter query, reload, and follow-up preserve the same conversation
   owner without replaying the query; reassignment and identity changes start
   separate conversations.

## Failure Handling

Installation errors are deliberately generic. Check, in order:

- installation status and exact allowed origin;
- consumer binding to an applied, verified release;
- selected connection profile readiness;
- runtime CORS and token policy for public modes;
- broker or adapter readiness for authenticated/private modes; and
- Platform asset-catalog health and SRI digests.

Do not work around an unavailable installation by publishing runtime URLs or
credentials in customer page source.
