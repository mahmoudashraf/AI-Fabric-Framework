# Max Mode Widget Auth Modes and Customer Integration Plan

Status: detailed planning and review document (2026-04-06)

This document defines how `max-mode-widget` should evolve into the main customer-facing chat UI for storefront and website integrations.

It is intentionally focused on the widget itself:

- integration posture
- auth modes
- identity handling
- feature compatibility
- deployment expectations

It does not replace the broader auth architecture documents under:

- [../../changes/Productization/future-work/Auth/README.md](../../changes/Productization/future-work/Auth/README.md)

Instead, it translates those auth models into widget-specific product and implementation requirements.

---

## 1) Executive Summary

Yes, `max-mode-widget` can be the main initial chat UI for customer storefront integrations.

But it must be treated as a thin UI shell, not as the security boundary.

The widget should support multiple integration postures without forcing all customers into one auth model:

1. backend-mediated private runtime
2. public runtime authenticated
3. public runtime anonymous

The widget should never be the source of truth for:

- authenticated customer identity
- authorization decisions
- upstream store credentials

Current review result:

- the widget UX is strong enough to serve as the initial storefront interface
- the deployment workflow had repo-path and Pages-URL mistakes and needed correction
- the widget code had hardcoded `demo-user` / `demo-session` identifiers and needed cleanup
- the widget still needs a fuller auth-mode abstraction before it should be treated as production-ready across all customer integration patterns

---

## 2) What Was Reviewed

This review covered:

- widget code under `max-mode-widget/`
- current embed and usage docs
- `.github/workflows/deploy-widget.yml`
- script-tag and storefront examples

Key findings from the current repo state before this pass:

- the Pages deploy workflow incorrectly referenced `packages/max-mode-widget/` instead of `max-mode-widget/`
- public script-tag usage still pointed to `https://mahmoudashraf.github.io/aifabric/...` instead of this repository’s Pages path
- widget request hooks used hardcoded identity values such as `demo-user` and `demo-session`
- `apiConfig.headers` documentation claimed headers apply to all widget API calls, but the client only applied them to the chat base URL

Immediate corrections from this pass:

- workflow path fixed to `max-mode-widget/**`
- GitHub Pages script URL updated to this repo path
- widget identity now derives from verified runtime or backend auth context
- anonymous public mode keeps only a bootstrap `sessionId` hint
- shared headers now apply to both chat and CRUD requests, with optional `chatHeaders` and `crudHeaders`

---

## 3) Why the Widget Can Be the Main Customer UI

The widget already provides the right customer-facing primitives:

- floating launcher
- chat composer
- conversation history
- attachment context
- quick actions
- cart-side experience
- responsive mobile and desktop layouts

That means the product does not need a second chat UI track for the initial storefront release.

The main remaining work is not UX replacement.

The main remaining work is auth and integration hardening.

---

## 4) Locked Product Positioning

### 4.1 Widget is the UI layer

The widget should be treated as:

- the embeddable customer chat surface
- the initial storefront integration UI
- the host-site-compatible launcher and chat shell

It should not be treated as:

- the authorization decision point
- the customer identity issuer
- the holder of privileged static deployment credentials

### 4.2 Widget must support multiple auth modes

The widget cannot remain permanently tied to one simplistic auth contract such as:

- shared static headers

That is not enough for:

- anonymous public chatbot
- authenticated public runtime
- backend-mediated private runtime
- split auth between runtime and storefront CRUD routes

### 4.3 Connector must remain non-browser-facing in the secure modes

The widget may be the main UI, but the connector still should not become the normal browser ingress for customer deployments.

Preferred request shapes remain:

- browser -> storefront/app backend -> private runtime
- or browser -> public runtime

not:

- browser -> connector

If the connector is fully private, then customer-facing reads for:

- config
- data summaries
- status
- readiness
- logs
- diagnostics
- capabilities

must come from runtime or host-backend surfaces, not from direct connector APIs.

---

## 5) Supported Integration Modes

### 5.1 Mode A: Backend-mediated private runtime

This is the default and preferred production mode.

Flow:

- browser -> storefront/app backend
- storefront/app backend -> private runtime
- runtime -> private connector

Widget implications:

- widget talks only to the customer-controlled backend route
- widget may rely on same-site cookies or host-managed headers
- widget does not need to know the actual runtime location
- widget should not need to hold customer identity tokens directly
- widget should treat config/status/summary/log-style reads as host-backed or runtime-backed surfaces, never connector-backed
- `crudBaseUrl` is optional in this mode when the integration only needs chat and secure conversation history; cart/business CRUD UI must auto-disable unless a CRUD host is configured

Recommended auth posture:

- service-to-service auth between backend and runtime
- customer identity remains backend-side

### 5.2 Mode B: Public runtime authenticated

This is an opt-in easier integration mode.

Flow:

- browser -> public runtime
- runtime -> private connector

Widget implications:

- widget sends short-lived bearer token to runtime
- widget may need token refresh hooks
- widget must not trust caller-supplied `userId` as the real identity source
- widget should use runtime-backed public read surfaces for status/capabilities rather than connector endpoints
- `crudBaseUrl` remains optional for chat-only integrations and must not silently fall back to `chatBaseUrl` for cart/business CRUD

Recommended auth posture:

- trusted backend or site auth service issues token
- widget presents token
- runtime validates token and derives subject

### 5.3 Mode C: Public runtime anonymous

This is the easiest low-friction public chatbot mode.

Flow:

- browser -> runtime bootstrap endpoint
- runtime issues short-lived anonymous session token
- browser -> public runtime using anonymous token

Widget implications:

- widget needs anonymous bootstrap support
- widget needs stable anonymous `sessionId`
- widget must treat anonymous mode as low-privilege

Recommended auth posture:

- runtime bootstrap endpoint issues token by default
- optional alternate issuer: trusted site or app backend
- never browser self-issued

## 6) Immediate Widget Identity Model

After this pass, the widget now has an explicit secure integration-mode model.

Supported modes:

- `backend-mediated-private-runtime`
- `public-runtime-authenticated`
- `public-runtime-anonymous`

In the secure modes, the widget does not send browser-supplied request identity fields such as:

- `sessionId`

Instead:

- backend-mediated mode relies on backend/runtime-owned identity
- authenticated public mode relies on bearer-token-derived identity
- anonymous public mode relies on runtime-issued anonymous session identity

In addition, the secure modes should verify the effective runtime posture on open by probing the runtime auth-context surface:

- default probe target: `/api/chat/me/auth-context`
- preferred override: `apiConfig.runtimeRoutes.authContextUrl`
- compatibility override: `runtimeAuth.authContextUrl`
- default behavior: enabled for secure modes unless explicitly disabled

When the host already has route-level provisioning metadata, the widget should use those explicit routes instead of inferring secure paths from `chatBaseUrl`.

Preferred mapping:

- `preferredChatQueryUrl` -> `apiConfig.runtimeRoutes.chatQueryUrl`
- `preferredSuggestionsUrl` -> `apiConfig.runtimeRoutes.suggestionsUrl`
- `preferredConversationsUrl` -> `apiConfig.runtimeRoutes.conversationsUrl`
- `preferredConversationItemUrlTemplate` -> `apiConfig.runtimeRoutes.conversationItemUrlTemplate`

Expected runtime auth modes:

- `backend-mediated-private-runtime` -> `PRIVATE_RUNTIME_BACKEND_MEDIATED`
- `public-runtime-authenticated` -> `PUBLIC_RUNTIME_AUTHENTICATED`
- `public-runtime-anonymous` -> `PUBLIC_RUNTIME_ANONYMOUS`

If the runtime reports a mismatched auth mode, the widget should surface an immediate error rather than silently continue under a weaker posture.

Why this matters:

- conversation history needs a stable owner
- anonymous chat needs a stable session owner
- cart and conversation APIs must rely on verified auth context instead of browser-supplied identity

This is the active greenfield auth model.

---

## 7) Immediate Config Surface

The widget should currently expose:

```ts
apiConfig: {
  chatBaseUrl: string;
  crudBaseUrl?: string;
  runtimeRoutes?: {
    chatQueryUrl?: string;
    suggestionsUrl?: string;
    conversationsUrl?: string;
    conversationItemUrlTemplate?: string;
    authContextUrl?: string;
  };
  headers?: Record<string, string>;
  chatHeaders?: Record<string, string>;
  crudHeaders?: Record<string, string>;
}
integrationMode?:
  | "backend-mediated-private-runtime"
  | "public-runtime-authenticated"
  | "public-runtime-anonymous";
sessionId?: string;
```

Interpretation:

- `headers`: shared auth/transport headers for both surfaces
- `chatHeaders`: runtime/chat-only headers
- `crudHeaders`: storefront CRUD-only headers
- `runtimeRoutes`: explicit preferred runtime URLs when the host already knows the secure route contract
- `integrationMode`: selects which secure runtime auth posture the widget expects
- `sessionId`: explicit anonymous bootstrap hint for the public-anonymous mode

This is enough for the initial secure productized integration API.

Important constraint:

- `crudBaseUrl` must not be interpreted as a connector URL in the secure modes
- if present, this surface should resolve to host-backed business CRUD, not connector-admin or connector-read endpoints
- if absent, the widget should stay chat-capable and disable cart/business CRUD entry points

---

## 8) Required Next-Step Widget Auth Abstraction

The widget should evolve toward a first-class auth model like:

```ts
auth: {
  mode:
    | "backend_mediated"
    | "public_runtime_authenticated"
    | "public_runtime_anonymous";
  transport?: "cookie" | "bearer" | "headers";
  bootstrapAnonymous?: () => Promise<BootstrapResult>;
  getAccessToken?: () => Promise<string | null>;
  getChatHeaders?: () => Promise<Record<string, string>>;
  getCrudHeaders?: () => Promise<Record<string, string>>;
  onUnauthorized?: (details: UnauthorizedEvent) => void;
}
```

This abstraction is needed because:

- some customers will use cookies
- some will use bearer tokens
- some will use backend proxy routes
- some will need anonymous bootstrap
- some will have different auth for runtime vs CRUD routes
- some will need runtime or host-backed operational reads because the connector is fully private

---

## 9) Feature Compatibility by Auth Mode

### 9.1 Chat query

Supported in all modes.

### 9.2 Conversation history

Should be:

- enabled for authenticated users
- enabled for anonymous users only when stable session ownership is available
- disabled or hidden if the host integration does not provide a stable owner/session identity

### 9.3 Cart

Should be:

- enabled only when the integration supports a stable cart owner or a host-managed cart route
- hidden when anonymous/public mode is FAQ-only

### 9.4 Quick actions

Should be mode-aware.

Examples:

- anonymous public mode: search/browse/help only
- authenticated mode: broader actions allowed
- backend-mediated mode: host can allow storefront-specific actions

### 9.5 Debug inspector

Should stay development-only regardless of auth mode.

---

## 10) Gaps Still Remaining After This Pass

The current widget is improved, but still not fully auth-mode-complete.

The main remaining gaps are:

1. no proactive authenticated-token refresh contract yet beyond `getBearerToken`
2. no host-visible unauthorized/expired-token recovery callback yet
3. no feature gating derived automatically from auth mode
4. no runtime-specific identity derivation contract yet beyond secure omission of legacy request identity
5. storefront examples still include static-header/demo-style integrations that should be clearly marked as non-production
6. no explicit runtime-first or host-first replacement contract yet for connector-adjacent config/status/summary/logs reads when the connector is private

---

## 11) Deployment Workflow Review

The corrected deployment workflow should now assume:

- widget source path is `max-mode-widget/`
- GitHub Pages deploys from this repository, not from the old `aifabric` repo path

Expected public script URL for this repo:

```html
<script src="https://mahmoudashraf.github.io/AI-Fabric-Framework/max-mode-widget.iife.js"></script>
```

The workflow should:

- install from `max-mode-widget/package-lock.json`
- build from `max-mode-widget`
- publish `max-mode-widget.iife.js`, `max-mode-widget.esm.js`, `max-mode-widget.cjs.js`, and `style.css`

That has now been corrected locally in `.github/workflows/deploy-widget.yml`.

---

## 12) Storefront Guidance

### 12.1 What the widget should do

The widget should:

- render the UI
- hold short-lived interaction state
- send chat and CRUD requests to the configured host surfaces
- surface auth failures cleanly
- assume secure-mode operational reads come from runtime or host-backend surfaces, not connector URLs

### 12.2 What the host integration should do

The host integration should:

- decide which auth mode is in use
- issue or forward tokens when needed
- own backend-mediated auth when using the private-runtime posture
- decide whether anonymous conversations and carts are allowed

### 12.3 What the widget must not do

The widget must not:

- invent customer identity
- self-issue anonymous tokens
- hold long-lived privileged secrets
- decide business authorization rules

---

## 13) Recommended Implementation Sequence

1. Keep the widget as the main storefront UI.
2. Preserve the immediate fixes from this pass:
   - anonymous bootstrap `sessionId`
   - verified runtime/backend-owned conversation identity
   - split shared/chat/CRUD headers
   - no hardcoded demo identities
3. Add first-class auth mode config.
4. Add anonymous bootstrap callback support.
5. Add token provider / refresh callback support.
6. Add a runtime-first or host-backend-first operational surface contract for config/status/summary/log-style reads so the widget does not rely on direct connector APIs in secure modes.
7. Add feature gating derived from auth mode.
8. Update Shopify and generic storefront examples to use secure production patterns by default.
9. Add regression coverage for:
   - anonymous continuity
   - authenticated continuity
   - split chat/CRUD headers
   - no direct connector dependency for operational reads in secure modes
   - no hardcoded identity leaks

---

## 14) Completion Criteria

This widget integration track is complete when:

- the widget can act as the main customer-facing chat UI
- the widget supports backend-mediated private runtime integrations cleanly
- the widget supports authenticated public runtime integrations cleanly
- the widget supports anonymous public runtime integrations cleanly
- the widget no longer relies on simplistic hardcoded identity assumptions
- the widget does not depend on direct connector APIs for customer-facing operational reads in secure modes
- docs and deployment paths match this repository and current product architecture
