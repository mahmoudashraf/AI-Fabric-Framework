# Shopify Storefront Chat Widget (V1) — Implementation Plan

This document describes the V1 plan for a **default storefront chat UI** shipped as a Shopify Theme App Extension / widget.

Principles:
- AI Fabric remains **headless-first** (API + contracts). The widget is a reference UI.
- Merchants can later **customize** or **bring their own UI** (SDK/API), without forking the runtime.
- Writes always follow **confirmation semantics** (V5) and never silently mutate state.

Related docs:
- Shopify integration architecture: `changes/Productization/SHOPIFY_APP_IMPLEMENTATION_PLAN.md`
- Admin UI plan: `changes/Productization/SHOPIFY_ADMIN_APP_UI_PLAN.md`
- Chat request contract: `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`
- Actions + confirmations: `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`
- Connector boundary (actions/retrieval): `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

---

## 0) Goals / Non-goals

### Goals (V1)
1. Drop-in widget that “just works” for most stores.
2. Supports the orchestration UX contract:
   - missing params → clarification prompts
   - write actions → confirmation card (Confirm/Reject)
   - yes/no follow-ups are correctly resolved (pending action + interceptors)
3. Trust-building UX:
   - show sources/citations when retrieval is used
   - avoid debug leakage in production
4. Minimal customization:
   - color, placement, greeting, show/hide sources
   - enable/disable certain actions
5. Strong performance:
   - lazy-loaded bundle
   - no Shopify theme breakage

### Non-goals (V1)
- Fully customizable UI builder.
- Full multi-language localization suite (support EN first).
- “Autonomous agent” behavior (keep it interactive and safe).

---

## 1) Packaging & Install Path (Shopify-native)

### 1.1 Preferred packaging
- **Theme App Extension** with:
  - App Embed block (for global enablement)
  - Optional Section block (for product page / cart page placement)

### 1.2 Alternate packaging (fallback)
- Script tag injection (legacy). Use only if extension adoption is blocked.

### 1.3 Admin-controlled enablement
- Widget is toggled from the Shopify embedded Admin app UI.
- Admin UI can deep-link merchant to Shopify theme editor to enable the embed.

### 1.4 Theme App Extension structure (recommended V1 layout)
Implement the widget as a Theme App Extension that renders:
- a root container element (where the widget mounts)
- a small inline config payload (shop + page context + theming settings)
- a deferred script bundle (lazy loads heavy UI)

Suggested extension structure (illustrative):
- `blocks/ai_fabric_widget_embed.liquid` (App Embed)
  - renders the root container + loads JS
  - contains the widget settings schema (colors, placement, greeting, enable toggle)
- `blocks/ai_fabric_widget_context.liquid` (optional App Block)
  - added to product templates to inject product/variant context more reliably
- `assets/ai-fabric-widget-loader.js` (tiny bootstrap)
  - reads Liquid-injected config, fetches backend config, then lazy-loads the UI bundle
- `assets/ai-fabric-widget-ui.js` (actual UI)
  - ships as a bundled build artifact

Important: keep all sensitive secrets off the storefront. Liquid should only emit safe identifiers.

---

## 2) Runtime Networking Model (No secrets in the browser)

### 2.1 Request path
Browser widget → Shopify App Backend → AI Fabric runtime

Recommended:
- Widget calls a Shopify **App Proxy** route (Shopify-signed request) OR calls your backend with a short-lived token issued by the backend.
- Backend calls AI Fabric runtime `POST /api/chat/me/query` for the verified caller contract.

### 2.2 Why this matters
- AI Fabric runtime keys stay server-side.
- You can enforce rate limits, bot protection, and abuse detection centrally.

### 2.3 Shopify App Proxy integration (recommended default)
Use Shopify’s **App Proxy** so the widget can call a same-origin path on the merchant’s storefront:
- avoids CORS headaches
- gives you a Shopify-signed request context (shop domain + HMAC/signature)
- keeps the widget “Shopify-native” and easier to support

Recommended proxy routes (illustrative):
- `GET  /apps/ai-fabric/widget/config` → returns widget config JSON (public/safe)
- `POST /apps/ai-fabric/widget/chat` → proxies chat to AI Fabric runtime (server-side auth)
- `POST /apps/ai-fabric/widget/event` → optional telemetry (bounded)

Backend requirements:
- verify App Proxy signature/HMAC on every request
- apply rate limits and abuse detection per `(shop, sessionId, ip)`

---

## 3) Identity, Session, and Conversation Model

### 3.1 Stable identifiers
Inputs to the orchestrator should be deterministic:
- `tenantId`: shop domain (`my-shop.myshopify.com`)
- `userId`:
  - if logged-in customer: Shopify customer id
  - else: anonymous id stored in a cookie/localStorage
- `sessionId`: per browser session (rotates)
- `conversationId`: per chat thread (persisted so the user can reopen)

### 3.2 Multi-tab behavior
- Use `conversationId` scoped to tab by default to avoid cross-tab confusion.
- Provide a “Continue previous chat” option later (V2).

### 3.3 Context injection (safe + bounded)
Widget can attach context via the chat request metadata/attachments:
- current page type: home / collection / product / cart / checkout
- current product handle/id (if on product page)
- current cart id (if available)

Do not attach full customer profiles or addresses by default.

### 3.4 Shopify context extraction (how the widget learns “where it is”)
Use **Liquid** (Theme App Extension blocks) to inject a small, safe config object into the DOM.

Recommended approach:
- The App Embed block renders a root element with `data-*` attributes and/or a small JSON script tag.
- The loader JS reads these values at boot time.

Fields to inject when available (illustrative; do not block rendering if missing):
- `shop` (permanent domain) → maps to `tenantId`
- `customerId` (if logged in; otherwise null/empty)
- `pageType` (`product`, `collection`, `cart`, `page`, etc.)
- product context (only on product pages):
  - `productId`, `productHandle`
  - selected variant id (if present) or a default variant id
- locale/currency hints:
  - `locale`, `currency`

Why Liquid injection matters:
- avoids brittle parsing of theme-specific global JS objects
- stays compatible across themes
- keeps the “context contract” deterministic and testable

### 3.5 Cart context (V1-safe approach)
Cart integration is tricky because storefront cart state is browser/session specific.

V1 recommendation:
- Read-only cart hints are OK:
  - call the store’s same-origin cart endpoint (e.g., `/cart.js`) to get item count and selected variant IDs
  - send only bounded, non-sensitive cart metadata to the backend (no addresses, no emails)
- For write flows, prefer **link-based actions** over direct cart mutation:
  - generate a “cart permalink” / checkout link from selected variant IDs
  - present it as a button (“Open cart” / “Go to checkout”)

This keeps the widget safe and reduces the need to unify browser cart state with server-side actions in V1.

---

## 4) UI Components (V1)

### 4.1 Core layout
- Floating launcher button (bubble)
- Slide-up panel (mobile-friendly)
- Header:
  - store logo (optional)
  - “AI Assistant” title
  - close/minimize
- Message list (scroll)
- Composer:
  - multiline input
  - send button
  - optional “attachments” chip row (selected products)

### 4.2 Message types
1. **User message**
2. **Assistant message** (markdown-lite)
3. **System message** (errors, rate-limited, offline)
4. **Sources panel** (expand/collapse)
5. **Action cards**
   - executed actions (“Action executed”)
   - pending confirmations (Confirm/Reject)
6. **Item cards**
   - products/collections/pages from retrieval results
   - pin/select for comparison

### 4.3 “Selected items” UX (matches your demo strengths)
Support:
- selecting 1..N items (chips)
- “Compare selected items”
- pass selected item IDs as attachments so the model can reason over known items (without re-searching)

---

## 5) Confirmation UX (Critical)

### 5.1 Confirmation card
When the orchestrator returns `CONFIRMATION_REQUIRED`:
- Show a card with:
  - summary text (safe, redacted)
  - Confirm button
  - Reject button
- Clicking Confirm sends a “confirm” intent (not freeform text) to the backend.

### 5.2 Follow-up confirmations (interceptors)
If the system returns a follow-up confirmation (e.g., retention offer):
- Render it as the next confirmation card.
- Keep a small “pending action” indicator so users understand the flow.

### 5.3 Safety rules
- Never auto-confirm.
- Never hide confirmations behind plain text (“type yes” is allowed, but buttons are primary).
- If there is a pending confirmation, disable unrelated write actions until resolved (or ask the user to resolve first).

---

## 6) Retrieval & Sources UX

### 6.1 When to show sources
- If the response includes documents/citations (RAG), show “Sources used” with:
  - document title (if present)
  - snippet preview
  - link (product/page URL)

### 6.2 When there are no sources
- Do not fabricate sources.
- Show a subtle “No sources” state and answer normally if allowed by pack/mode.

### 6.3 Vector spaces visibility
Optional: show small badges like “Products” / “Policies” if the response includes `vectorSpace` metadata.

---

## 7) Shopify-specific Action UX (V1)

Recommended actions to support in the widget:

READ (no confirmation):
- “Find products like…”
- “Compare these items”
- “Do you have a return policy for…?” (retrieval-driven, not action-driven)

WRITE (confirmation required):
- Create cart/checkout link (recommended V1 write pattern)
- Apply discount code (as part of checkout link creation when possible)
- (Optional, V2) Add/remove from cart (requires tighter cart/session handling)

Widget responsibilities:
- render action results in a friendly way (e.g., “Added to cart” with link to cart)
- for checkout link, render a prominent “Go to checkout” button

Shopify integration note:
- In V1, treat “write” actions as producing a **safe redirect** (URL) rather than trying to mutate the live cart session server-side.
- This aligns well with the confirmation UX and avoids theme-specific cart pitfalls.

---

## 8) Theming & Customization (V1 minimal)

### 8.1 Configurable options
- `enabled` (on/off)
- placement: bottom-right (default), bottom-left
- primary color + accent
- greeting text
- show/hide sources
- allowed actions (checkbox list)

### 8.2 Delivery of config
- Widget loads config from your backend:
  - `GET /widget/config?shop=<shop>`
- Cache config in the browser with short TTL (e.g., 5 minutes) to reduce load.

### 8.3 What is stored where (Shopify theme vs backend)
Split configuration into two layers:

**Theme-level settings (Shopify theme editor)**
- purely visual + placement options (colors, positioning, greeting text)
- stored in the theme configuration and injected via Liquid

**Backend config (your Shopify App Backend)**
- safety + capability toggles (allowed actions, show sources)
- environment targeting (Dev vs Prod)
- rate limits + abuse controls

This keeps merchants comfortable (they can theme in Shopify) while keeping safety centralized.

---

## 9) Performance & Reliability

### 9.1 Performance targets
- Widget script < 50–100KB gz (initial), lazy-load the panel bundle.
- First interaction < 500ms to open panel on a normal device.

### 9.2 Reliability features
- Retry on transient network failures (bounded).
- Offline state message.
- Rate-limit handling:
  - show “Too many requests” state with cool-down.

---

## 10) Abuse, Privacy, and Compliance

### 10.1 Abuse controls (backend)
- Rate limits per IP + per session.
- Bot signals (basic).
- Blocklist patterns.

### 10.2 Privacy defaults
- Do not store or display sensitive values in the widget by default.
- Avoid collecting addresses/emails via chat unless explicitly enabled and protected by confirmations.

---

## 11) Implementation Phases

### Phase 1 — Minimal widget (V1)
- bubble + panel + basic chat + confirmations
- sources panel
- selected items chips + compare flow

### Phase 2 — Shopify-native polish
- theme extension blocks
- better product cards
- cart/checkout deep links

### Phase 3 — Customization + monetization
- advanced theming (CSS variables + templates)
- white-label option
- per-page targeting (show only on product pages, etc.)

---

## 12) Acceptance Criteria (V1)

1. Widget installs via Theme App Extension and can be enabled from Admin UI.
2. Widget can call chat through backend proxy without exposing runtime keys.
3. Confirm/Reject flow works for write actions.
4. Sources show correctly when retrieval is used, and never appear when not provided.
5. Widget works on mobile and doesn’t break common Shopify themes.
