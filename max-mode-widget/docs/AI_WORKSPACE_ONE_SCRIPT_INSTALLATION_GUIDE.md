# LoomAI Workspace One-Script Installation Guide

**Contract:** `loomai-ai-workspace-installation-v1`
**Audience:** customer website developers and implementation partners

LoomAI Platform can install the generic AI Workspace, its reviewed experience
pack, and its current assigned deployment connection from one public script.
The host page contains an opaque installation ID only.

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
