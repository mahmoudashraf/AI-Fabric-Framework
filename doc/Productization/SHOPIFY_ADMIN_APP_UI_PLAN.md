# Shopify Admin App UI Plan (Embedded) — V1

This document describes the V1 UI plan for the **Shopify Embedded App** (merchant-facing Admin UI) that configures and operates the AI Fabric Shopify integration.

This UI is a core part of the “sells itself” story:
- Merchants can self-serve setup (dev → prod) without talking to sales.
- Merchants can see sync health, action health, and the widget behavior.
- Merchants can upgrade to prod (dedicated Qdrant) and understand what changes.

Related docs:
- Shopify integration architecture: `changes/Productization/SHOPIFY_APP_IMPLEMENTATION_PLAN.md`
- Productization baseline: `changes/Productization/PRODUCTIZATION_IMPLEMENTATION_PLAN.md`
- Actions contract + confirmations: `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`
- Connector boundary (actions/retrieval): `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

---

## 0) Goals / Non-goals

### Goals (V1)
1. **Fast onboarding**: install → dev env ready → first sync → try in playground.
2. **Two environments** per shop:
   - **Dev**: Lucene vector DB (zero external deps).
   - **Prod**: **dedicated Qdrant per merchant** + full re-sync.
3. **Self-serve storefront enablement**: install widget and configure behavior safely.
4. **Operational visibility**: sync progress, webhook health, action errors, retrieval counts.
5. **Safety defaults**: confirmations for writes, no debug leakage, redaction for sensitive values.

### Non-goals (V1)
- Full enterprise RBAC (keep it simple: Admin-only settings).
- Complex vertical “builder UI” for action schemas (start with curated defaults + optional file upload).
- Advanced analytics suite (ship minimal usage + health stats).

---

## 1) UX Principles

1. **Wizard-first, dashboard-second**: V1 optimizes for “getting it working,” not browsing settings.
2. **Fail-closed**: if prod provisioning fails, don’t partially enable prod; keep dev intact.
3. **Progressive disclosure**: hide advanced knobs until the merchant switches to “Advanced mode”.
4. **Explain what’s happening**: dev vs prod, what data gets indexed, what gets sent to LLMs, what requires confirmation.
5. **Support-friendly**: every screen should show a “Copy diagnostic bundle” button (redacted).

---

## 2) Information Architecture (Navigation)

V1 navigation (left-side):
1. **Overview**
2. **Playground**
3. **Sync**
4. **Storefront Widget**
5. **Actions**
6. **Knowledge (Vector Spaces)**
7. **Environments**
8. **Billing**
9. **Diagnostics**

Notes:
- Keep “Settings” distributed across relevant pages to avoid a giant settings dump.
- “Diagnostics” exists to reduce support load (logs, health, last errors, export bundle).

---

## 3) Core Screens (What each page does)

### 3.1 Overview
Purpose: one-page status for merchants.

Must show:
- Current plan (Dev-only vs Prod).
- Dev env status: `READY | SYNCING | ERROR | PAUSED`.
- Prod env status (if enabled): `PROVISIONING | READY | SYNCING | ERROR`.
- Widget status: `ENABLED | DISABLED`.
- Last sync: timestamp + items indexed counts (by vectorSpace).
- Webhook health: last webhook received + queue lag.
- “Next recommended step” CTA (wizard-driven).

Primary CTAs:
- “Run initial sync” / “Resync”
- “Enable widget”
- “Upgrade to Prod”

### 3.2 Playground (Admin-only chat)
Purpose: validate behavior without touching storefront.

Features:
- Chat UI (same semantics as widget, but in Admin).
- Optional “context picker”:
  - select a product from Shopify
  - attach it as an active item/attachment
- Toggle between Dev and Prod environment (if prod exists), but default to Dev.
- Show sources/citations (if retrieval enabled), never show internal debug unless an explicit “Support mode” is enabled.

### 3.3 Sync
Purpose: data control and visibility.

Sections:
- **Data sources toggles** (V1 default on: Products, Collections, Pages/Policies):
  - Products
  - Collections
  - Pages + Policies
  - (Off by default) Orders (requires advanced + PII warnings)
- **Initial sync** status + progress bar:
  - total items discovered
  - processed / failed / retrying
  - ETA (best-effort)
- **Webhook status**:
  - subscribed webhooks list
  - last event time
  - error rate
  - retry queue length
- **Actions**:
  - “Start sync”
  - “Pause”
  - “Resync full”
  - “Rebuild index (Dev only)”

Failure UX:
- show *what failed* (resource type + Shopify gid) and a retry button
- show rate-limit/backoff messaging clearly

### 3.4 Storefront Widget
Purpose: enable and configure the widget safely.

Must show:
- Installation method:
  - Theme App Extension (recommended)
  - App Embed toggle instructions
- Status checks:
  - “Widget installed” detection (best-effort)
  - “Widget enabled”
- Configuration:
  - placement (bottom-right default)
  - theme color / accent
  - greeting text
  - “show sources” toggle
  - allowed actions in storefront (safe defaults)
- Test:
  - “Open storefront preview with widget”
  - “Send test message”

### 3.5 Actions
Purpose: transparency + safety controls, not complex authoring.

V1 default:
- Show the **default Shopify action catalog** (read + write).
- For each action:
  - name, description, accessMode (READ / WRITE_ONLY)
  - requiresConfirmation flag
  - parameters (required + optional)
  - enabled toggle (per environment)
- “Advanced” panel:
  - upload custom action contract (file-based)
  - configure forwarding connector (optional)

Guardrails:
- If merchant disables confirmations on write actions → disallow (or require explicit “unsafe mode” + warnings).
- If merchant uploads actions that collide with default Shopify actions → reject upload (fail fast).

### 3.6 Knowledge (Vector Spaces)
Purpose: control retrieval scope and build trust.

Must show:
- Vector spaces enabled (default list):
  - `product`, `collection`, `page`, `policy`
- Per vectorSpace stats:
  - indexed items count
  - last updated timestamp
- Optional filters:
  - exclude tags/collections from indexing
  - exclude certain pages/policies

### 3.7 Environments
Purpose: make dev/prod concrete and debuggable.

Dev environment section:
- Runtime status (UP/DOWN)
- Lucene index path (not editable)
- “Reset dev index” (dev-only)

Prod environment section:
- Status + “Provisioned Qdrant” info:
  - Qdrant endpoint (masked)
  - region
  - size/tier
- “Promote to prod” flow:
  - provisions Qdrant
  - deploys prod runtime config
  - runs full sync
- Rollback:
  - “Disable prod and go back to dev-only” (keeps dev data intact)

### 3.8 Billing
Purpose: monetize and gate prod.

V1: Shopify Billing integration.

Must show:
- current plan
- included allowances (messages/month, indexed items, vector storage tier)
- current usage (best-effort; do not block requests on minor reporting gaps)
- upgrade/downgrade CTA

### 3.9 Diagnostics
Purpose: reduce support and improve reliability.

Must show:
- Connector/action execution health:
  - last 20 action calls (redacted)
  - p95 latency
  - error rate
- Retrieval health:
  - vector DB health checks
  - indexing queue lag
- Webhook receiver health:
  - signature verification status
  - last failures + reasons
- “Download diagnostics bundle”:
  - config snapshot (redacted)
  - last N errors
  - environment IDs
  - sync job IDs

---

## 4) Onboarding Wizard (Critical Path)

Wizard steps:
1. **Welcome**
   - explain dev vs prod
   - confirm what data will be indexed in V1 (products/pages/policies)
2. **Create Dev Environment**
   - “Dev environment ready” check (runtime health)
3. **Run Initial Sync**
   - show progress, allow leaving page
4. **Try Playground**
   - suggested prompts (product discovery, compare products)
   - show “actions + confirmations” demo flow
5. **Enable Storefront Widget**
   - guide through theme extension install
   - preview link
6. **Upgrade to Prod (optional)**
   - plan selection (billing)
   - provision dedicated Qdrant
   - full re-sync to prod

---

## 5) Backend APIs Needed to Power the UI (High-level)

The UI should only call your Shopify App Backend. The backend calls AI Fabric runtime and Shopify APIs.

Suggested UI-facing backend routes (illustrative):
- `GET /app/status` (dev/prod/widget status summary)
- `POST /app/onboarding/step` (advance wizard state)
- `POST /sync/start` / `POST /sync/resync` / `POST /sync/pause`
- `GET /sync/status`
- `GET /actions/catalog` / `PUT /actions/enabled`
- `POST /actions/catalog/upload` (advanced)
- `GET /knowledge/stats` (vectorSpace counts + last updated)
- `POST /widget/config` / `GET /widget/config`
- `POST /prod/upgrade` (starts provisioning dedicated Qdrant + prod runtime)
- `GET /prod/status`
- `GET /diagnostics/bundle`

Constraints:
- UI must never talk directly to AI Fabric runtime using long-lived secrets.
- Storefront widget must route via App Proxy / backend tokenization.

---

## 6) Implementation Notes (High-level stack)

Recommended Shopify app stack:
- Shopify CLI app
- Embedded app framework: Remix (Shopify default) or Next.js
- UI: Shopify Polaris + App Bridge
- Backend jobs: queue (BullMQ/SQS) for sync + webhooks
- DB: Postgres (shop install records + sync state + widget config + action toggles)

---

## 7) Acceptance Criteria (V1)

1. Fresh install reaches “first working chat” in < 10 minutes for a typical store.
2. Dev sync completes and shows vectorSpace counts.
3. Widget can be enabled, themed, and tested from Admin UI.
4. Prod upgrade provisions **dedicated Qdrant** and runs full re-sync.
5. Uninstall triggers cleanup (tokens + vectors + sessions) and UI displays “Uninstalled” state.

