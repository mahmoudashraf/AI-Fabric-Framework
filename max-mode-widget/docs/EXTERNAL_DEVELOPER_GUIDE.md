# LoomAI Max Mode Chat Application External Developer Guide

**Applies to:** `@loom-ai-labs/max-mode-widget` 1.0.0

**Audience:** customer application developers, implementation partners, and
reviewed experience-pack authors.

This guide explains how to add the generic LoomAI Companion and Max Mode chat
application to an existing website or web application. For the full component
API and internal development reference, see [GUIDE.md](GUIDE.md). For the
security rationale behind the supported authentication modes, see
[WIDGET_AUTH_MODES_AND_CUSTOMER_INTEGRATION_PLAN.md](WIDGET_AUTH_MODES_AND_CUSTOMER_INTEGRATION_PLAN.md).

## 1. Product Boundary

The Max Mode chat application owns:

- the Companion dock and full-screen Max Mode UI;
- messages, conversation history, suggestions, and attachments;
- `default` and `contextual` host-owned tool groups;
- current-page text capture when explicitly enabled;
- bounded action-result presentation extensions;
- browser-side anonymous-session continuity; and
- responsive, accessible interaction behavior.

It does not own:

- customer identity or authorization;
- connector or provider credentials;
- business data, indexing, or synchronization;
- action selection, validation, confirmation, or execution policy;
- the application database or business side effects; or
- deployment provisioning.

Those remain responsibilities of the customer application and its assigned
LoomAI deployment. The browser must never call a private connector directly.

## 2. Choose an Integration Mode

| Mode | Browser talks to | Use when | Browser credential |
| --- | --- | --- | --- |
| `backend-mediated-private-runtime` | Customer backend | Recommended when the runtime is private or the application already has a trusted backend session | Same-site session or another browser-safe host credential |
| `public-runtime-authenticated` | Assigned deployment runtime | The user already has an application-issued, short-lived bearer token accepted by the runtime | Short-lived token supplied by the host |
| `public-runtime-anonymous` | Assigned deployment runtime | A low-privilege public assistant is intentionally enabled | Short-lived runtime-issued anonymous token |

Use `backend-mediated-private-runtime` unless direct browser-to-runtime access
is an intentional deployment decision. The widget verifies the effective
runtime auth context for public modes and fails closed on identity changes or
conversation access denial.

## 3. Prerequisites

Before adding the script, obtain:

1. A reviewed widget bundle and its SHA-256 integrity value.
2. A customer backend adapter URL or assigned runtime URL.
3. The exact chat, suggestions, auth-context, shell-config, and conversation
   routes exposed for this deployment.
4. The selected integration mode and its token/bootstrap contract.
5. The browser origins allowed by the backend or runtime.
6. The capabilities, modes, tools, and action presentations approved for this
   customer.

The source packages are currently private reviewed artifacts. Do not assume
that `npm install @loom-ai-labs/max-mode-widget` is publicly available. Use the
bundle or package supplied through the approved LoomAI delivery channel.

## 4. Load a Reviewed Bundle

### 4.1 Script-tag distribution

Pin the content-addressed file and integrity value approved for the customer:

```html
<script
  src="https://assets.example.com/loomai/max-mode-widget.<sha16>.iife.js"
  integrity="sha256-<base64-sha256>"
  crossorigin="anonymous"
></script>
```

The IIFE includes its UI dependencies and exposes `window.MaxMode`. Do not load
an unversioned third-party copy in production. Resolve a new manifest during a
reviewed upgrade, pin the selected artifact, verify its SHA-256, and then roll
it out.

The expected manifest shape is:

```json
{
  "schemaVersion": "loomai-widget-bundle-v1",
  "file": "max-mode-widget.<sha16>.iife.js",
  "sha256": "<64-lowercase-hex-characters>"
}
```

For customers approved to use LoomAI-hosted browser assets, the current
manifest endpoint is:

```text
https://loomai.pro/vendor/max-mode-widget-manifest.json
```

Treat it as release metadata: select and verify the returned content-addressed
file during rollout rather than silently changing production code on every
page load.

### 4.2 ESM or React distribution

When LoomAI provides the reviewed package through a package registry or source
bundle, import the component and stylesheet from that exact version:

```tsx
import { MaxModeWidget, useMaxMode } from "@loom-ai-labs/max-mode-widget";
import "@loom-ai-labs/max-mode-widget/styles.css";
```

The rest of this guide uses the script API because it works in framework-free
sites and keeps host integration small.

## 5. Recommended Backend-Mediated Setup

In this mode the browser calls a customer-owned adapter. The adapter derives
the current user and tenant from its trusted server-side session, then calls
the private assigned runtime using backend-only credentials.

```html
<script
  src="/assets/loomai/max-mode-widget.<sha16>.iife.js"
  integrity="sha256-<base64-sha256>"
></script>
<script>
  MaxMode.init({
    integrationMode: "backend-mediated-private-runtime",
    apiConfig: {
      chatBaseUrl: "https://app.example.com/loomai",
      fetchCredentials: "include"
    },
    features: {
      cart: false,
      debug: false,
      conversations: true,
      quickActions: true
    },
    theme: {
      primaryColor: "#155eef",
      borderRadius: "0.5rem",
      darkMode: "auto"
    },
    host: {
      assistantLabel: "Example AI",
      welcomeMessage: "How can I help?",
      defaultConversationMode: "executor",
      effectiveConversationMode: "executor",
      allowedConversationModes: ["executor"],
      showUtilityPanel: false,
      companionDock: true
    }
  });
</script>
```

Do not put a service API key, runtime assertion, provider token, or connector
credential in `defaultHeaders` or page source. When a backend adapter requires
a browser credential, it must be an intentionally browser-safe, scoped
credential or normal application session.

## 6. Public Runtime Anonymous Setup

Anonymous mode is suitable only for deployment capabilities explicitly allowed
to an anonymous session. The runtime, not the browser, creates the identity.

```html
<script src="/assets/loomai/max-mode-widget.<sha16>.iife.js"></script>
<script>
  MaxMode.init({
    integrationMode: "public-runtime-anonymous",
    apiConfig: {
      chatBaseUrl: "https://deployment.example.com",
      runtimeRoutes: {
        chatQueryUrl: "/api/chat/me/query",
        suggestionsUrl: "/api/chat/me/suggestions",
        authContextUrl: "/api/chat/me/auth-context",
        shellConfigUrl: "/api/chat/me/shell-config",
        conversationsUrl: "/api/chat/me/conversations",
        conversationItemUrlTemplate:
          "/api/chat/me/conversations/{conversationId}"
      },
      runtimeAuth: {
        bootstrapUrl: "https://deployment.example.com/api/public/chat/session",
        renewUrl:
          "https://deployment.example.com/api/public/chat/session/renew",
        authContextUrl:
          "https://deployment.example.com/api/chat/me/auth-context",
        probeAuthContextOnOpen: true
      },
      probeShellConfigOnOpen: true
    },
    features: { cart: false, conversations: true },
    host: {
      assistantLabel: "Example AI",
      companionDock: true,
      showUtilityPanel: false
    }
  });
</script>
```

The bootstrap and renewal endpoints use `POST`. Bootstrap must return at least:

```json
{
  "token": "<short-lived-token>",
  "tokenType": "Bearer",
  "authMode": "PUBLIC_RUNTIME_ANONYMOUS",
  "subjectType": "ANONYMOUS_SESSION",
  "sessionId": "<runtime-issued-session-id>",
  "expiresAt": "2026-10-04T12:00:00Z"
}
```

The widget stores the runtime-issued short-lived credential only in
tab-scoped `sessionStorage` so same-tab navigation can retain the conversation.
It rejects a renewal that returns a different session ID. Expiry, runtime
change, HTTP 401/403, identity change, or typed conversation access denial
clears conversation-bound state and never replays the denied request.

## 7. Public Runtime Authenticated Setup

The customer application supplies a fresh short-lived token. Identity and
authorization still come from the runtime's verification of that token.

```js
MaxMode.init({
  integrationMode: "public-runtime-authenticated",
  apiConfig: {
    chatBaseUrl: "https://deployment.example.com",
    runtimeRoutes: {
      chatQueryUrl: "/api/chat/me/query",
      suggestionsUrl: "/api/chat/me/suggestions",
      authContextUrl: "/api/chat/me/auth-context",
      shellConfigUrl: "/api/chat/me/shell-config",
      conversationsUrl: "/api/chat/me/conversations",
      conversationItemUrlTemplate:
        "/api/chat/me/conversations/{conversationId}"
    },
    runtimeAuth: {
      getBearerToken: async () => applicationAuth.getCurrentAccessToken(),
      probeAuthContextOnOpen: true
    }
  },
  features: { cart: false }
});
```

Do not use a long-lived deployment or connector key as this token.

## 8. Host-Owned Tool Groups

The generic widget understands only two scopes: `default` and `contextual`.
The host or experience pack supplies all labels, icons, queries, modes, and
positions.

```js
host: {
  toolGroups: {
    initialScope: "default",
    default: {
      label: "Browse",
      icon: "search",
      tools: [
        {
          label: "Search records",
          query: "Help me search the current records.",
          position: "search",
          mode: "executor",
          icon: "search"
        }
      ]
    },
    contextual: {
      label: "Current record",
      icon: "details",
      contextLabel: "Current record",
      availableWithoutAttachments: false,
      tools: [
        {
          label: "Explain details",
          query: "Explain the current record using approved facts.",
          position: "search",
          mode: "executor",
          icon: "details"
        }
      ]
    }
  }
}
```

Attaching context automatically selects `contextual`. The user can switch back
to `default` without removing attachments. Removing the final attachment
returns to `default`. On a detail page, set
`availableWithoutAttachments: true` only when the page itself is trusted
context.

Tool-group selectors and their quick actions are available in desktop and
mobile Max Mode only. The Companion dock keeps the same conversation and
attachments but does not render this tool surface, preserving its limited
vertical space for messages and the composer.

### Mobile tool rail

The optional `host.toolRail` is a JSON-safe command surface for mobile Max
Mode. It does not call customer APIs directly. It can open one configured tool
scope, open retrieved sources, or send a reviewed prompt through chat.

```js
host: {
  toolRail: {
    items: [
      { id: "browse", label: "Browse", icon: "search", action: "open-tools", scope: "default" },
      { id: "context", label: "Context", icon: "details", action: "open-tools", scope: "contextual" },
      { id: "sources", label: "Sources", icon: "documents", action: "open-documents" }
    ]
  }
}
```

At most six entries are accepted. Allowed actions, icons and tones are bounded;
arbitrary callbacks and CSS are not. Contextual commands disable when context
is unavailable, and Sources hides until evidence exists. Omitting the contract
derives neutral tool/sources entries. The generic rail has no commerce tools;
storefront controls belong to a dedicated experience configuration.

## 9. Current-Page Attachments

Page capture is opt-in:

```js
host: {
  currentPageAttachment: {
    enabled: true,
    rootSelector: "#main-content",
    excludeSelectors: ["nav", "footer", "[data-private]"],
    maxChars: 4000,
    maxPages: 3,
    maxTotalChars: 10000,
    invalidateOnNavigation: false
  }
}
```

Rules:

- Select a content root that excludes navigation, account controls, hidden
  forms, and private data.
- Use `contentProvider` for SPAs or structured pages that need explicit text.
- URLs are sanitized before transmission, but the host must still avoid
  placing secrets in URLs.
- `invalidateOnNavigation: false` allows deliberate multi-page collection in
  the same tab. It does not make attachments durable server records.
- Page text is request context, not automatically indexed knowledge.

## 10. Attach Host Context and Send Commands

The IIFE exposes:

```js
MaxMode.attachItem({
  type: "record",
  contextLabel: "Order 1042",
  data: {
    id: "order-1042",
    name: "Order 1042",
    status: "Processing"
  }
});

MaxMode.sendMessage("Explain this order status.", {
  open: true,
  mode: "executor",
  position: "search",
  requestContext: { workflow: "order-support" }
});
```

Browser API:

| Method | Purpose |
| --- | --- |
| `init(config)` | Mount one widget instance |
| `open()` / `close()` / `toggle()` | Control visibility |
| `attachItem(item)` | Add generic host-owned context |
| `attachProduct(product)` | Convenience product attachment |
| `attachCurrentPage()` | Capture the page when enabled |
| `sendMessage(text, options)` | Send a reviewed host command |
| `destroy()` | Remove the widget and listeners |

Only one IIFE widget instance is supported per page. Call `destroy()` before
mounting a different configuration.

## 11. Safe Action-Result Presentations

The generic UI can render reviewed React components or custom elements for
specific action-result schemas. The host config maps an exact action name and
schema version to a renderer and an allowlisted projection.

Register a custom element before calling `MaxMode.init()`. If its registration,
action name, schema, or projection does not match, the widget intentionally
uses its generic safe fallback.

```js
host: {
  actionPresentation: {
    renderers: [{
      id: "example.record-list.v1",
      kind: "custom-element",
      elementName: "example-record-list",
      schemaVersions: ["example.record-list.v1"]
    }],
    mappings: [{
      actionName: "example_search_records",
      rendererId: "example.record-list.v1",
      schemaVersion: "example.record-list.v1",
      projection: {
        collections: [{
          sourcePath: "results",
          target: "items",
          includeFields: ["id", "title", "status"],
          maxItems: 12,
          reference: {
            lookupField: "id",
            labelFields: ["title"],
            scope: "example-record"
          }
        }]
      }
    }]
  }
}
```

Raw action payloads are not passed directly to custom renderers. The widget
projects only configured fields, bounds collection sizes and strings, and
removes sensitive renderer-context keys. A presentation reference is
buyer-facing context only; the server must re-resolve and authorize trusted
resource identifiers before any action.

Use an experience pack when a reusable domain needs several coordinated
tools, projections, and renderers. Do not add domain vocabulary to the generic
widget.

## 12. Events

Use `onEvent` for host analytics and operational UI:

```js
onEvent(event) {
  applicationTelemetry.record("loomai_widget_event", {
    type: event.type,
    timestamp: event.timestamp
  });
}
```

Current event types include `widget:opened`, `widget:closed`,
`conversation:reset`, `message:sent`, `message:received`, action-presentation
events, cart events, `product:view`, and `error`. Treat event data as
operational telemetry, not an authorization signal. Do not forward raw event
data to analytics until the customer has reviewed and redacted its fields.

## 13. API Routes

Unless overridden, the widget resolves these paths relative to
`apiConfig.chatBaseUrl`:

| Method | Default path | Purpose |
| --- | --- | --- |
| `POST` | `/chat/me/query` | Submit a chat request |
| `POST` | `/chat/me/suggestions` | Request suggestions |
| `GET` | `/chat/me/auth-context` | Verify effective runtime identity |
| `GET` | `/chat/me/shell-config` | Read deployment-owned shell configuration |
| `GET` | `/chat/me/conversations` | List the current subject's conversations |
| `GET` / `DELETE` | `/chat/me/conversations/{conversationId}` | Read or delete one owned conversation |
| `POST` | Configured public bootstrap URL | Create an anonymous session |
| `POST` | Configured public renewal URL | Renew the same anonymous session |

Use `runtimeRoutes` whenever provisioning supplies exact route metadata. Do
not infer unsupported endpoints from a different deployment type.

`crudBaseUrl` is separate and optional. Configure it only when the customer
application exposes reviewed browser-facing business APIs such as cart
operations. The widget must not silently use the runtime or connector as a
business CRUD service.

## 14. CORS and Content Security Policy

For cross-origin runtime access:

- Allow only reviewed customer origins, methods, and headers.
- Allow `Authorization` and `Content-Type` when public token modes are used.
- If credentials are included, return an exact allowed origin, never `*`.
- Keep connector, provider, and control-plane origins private.

Typical CSP additions are:

```text
script-src 'self' https://assets.example.com
connect-src 'self' https://deployment.example.com
img-src 'self' data: https://approved-media.example.com
```

Add only the origins used by the selected configuration and presentation
renderers.

## 15. Security Checklist

- [ ] No API key, runtime assertion, connector credential, or provider token is
      present in HTML, JavaScript, bootstrap JSON, or browser storage.
- [ ] The selected integration mode matches the runtime auth context.
- [ ] Anonymous capabilities are explicitly allowlisted and low privilege.
- [ ] Write actions remain server-authorized and use confirmation when policy
      requires it.
- [ ] Trusted resource IDs are resolved and authorized server-side.
- [ ] Page capture excludes private account and form content.
- [ ] Action renderers receive only bounded projected fields.
- [ ] CORS and CSP list only reviewed origins.
- [ ] Debug mode is disabled for customer production.

## 16. Acceptance Checklist

Test at desktop and mobile widths:

1. The content-addressed script passes SRI and exposes `window.MaxMode`.
2. The widget reaches `ready` using the expected integration mode.
3. Auth-context probing reports the intended subject type.
4. Chat, suggestions, and owned conversation history work.
5. A stale or foreign conversation fails closed and is not replayed.
6. Normal same-tab navigation retains the intended session and attachments.
7. Default/contextual tools transition without losing attachments.
8. Current-page capture respects count and character limits.
9. Unsupported action results use the generic fallback safely.
10. Supported action results expose only projected fields.
11. A write action requires the configured server-side policy and produces a
    durable application-owned result or receipt.
12. Keyboard, focus, mobile layout, and screen-reader labels are usable.

## 17. Troubleshooting

### The script loaded but no UI appears

Confirm `apiConfig.chatBaseUrl` is non-empty, `MaxMode.init()` runs after the
bundle, and `launcher` is not `false` unless the Companion dock or a host-owned
open button is present.

### The runtime returns 401 or 403

Confirm the integration mode, token issuer, token expiry, allowed origin, and
auth-context route. Do not work around the failure with a static browser key.

### A conversation returns access denied

The widget intentionally clears the stale conversation handle and does not
replay the request. Verify the runtime-issued session remains the same across
navigation and that the browser is not restoring state from another runtime.

### Context tools are disabled

Attach a host item with `contextLabel`, or set
`contextual.availableWithoutAttachments: true` on an authoritative detail
page. Do not fake an attachment merely to enable a tool.

### A rich action result falls back to generic rendering

Verify the exact action name, configured schema version, custom-element
registration, and projected source paths. A fallback is safer than passing an
unreviewed raw payload to a renderer.

### Page context is missing or noisy

Set an exact `rootSelector`, add `excludeSelectors`, or supply a deterministic
`contentProvider` for an SPA.

## 18. Experience Packs

An experience pack configures the generic chat application for a reusable
domain without changing widget core. It may provide:

- host-owned tool groups;
- action names and safe projections;
- custom result renderers;
- attachment helpers;
- runtime descriptor discovery; and
- bounded theme and copy defaults.

The first implementation is `@loom-ai-labs/dealership-experience-pack`. Its
reviewed package includes `docs/EXTERNAL_DEVELOPER_GUIDE.md`. When a pack owns
initialization, do not also call `MaxMode.init()` directly.
