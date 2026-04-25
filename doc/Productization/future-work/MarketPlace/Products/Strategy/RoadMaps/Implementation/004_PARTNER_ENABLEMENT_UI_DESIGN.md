# Partner Enablement UI Design

Status: validated design specification (2026-04-25)

Companion to: [004_PARTNER_ENABLEMENT_FOUNDATION.md](004_PARTNER_ENABLEMENT_FOUNDATION.md)

Audience: React/MUI engineer implementing `Platfrom/partner-ui` for `partners.loomai.pro`.

Stack constraint: React 18 · TypeScript · Vite · MUI v6 · Emotion · `@mui/icons-material` · React Router · TanStack Query · React Hook Form · Zod. No Tailwind, shadcn, Next.js, Chakra, Ant Design, custom SVG icon systems, or new design framework.

---

## Validation Result

This design is accepted as the Partner Enablement UI baseline after alignment with the Partner Enablement Foundation.

Non-negotiable constraints:

- partner UI is a product implementation workspace, not a deployment/admin console
- self-service signup creates an empty workspace by default
- no client-store data appears before merchant approval, approved install/claim flow, or operator assignment
- partner actions stay product-safe: request store access, apply templates, run verification, export evidence, open escalation
- deployment-level controls stay admin/operator-only: deployments, providers, environments, secrets, raw vectorization/replay, runtime controls, rollback, global audit
- partner UI may display plan/tier context, but paid-plan and billing changes remain merchant/operator flows
- support escalations are structured cases with governed reply visibility, not a loose chat inbox
- day-1 maturity means responsive layouts, accessible states, role/permission guards, redaction, signed evidence URLs, light/dark mode, and build/smoke verification from the first implementation slice

Implementation note:

- Keep the stack exactly aligned to the current Platform UI stack unless a functional dependency is explicitly required for auth or security.
- `@supabase/supabase-js` is expected for partner auth.
- Avoid new UI/design frameworks and avoid optional UI packages unless approved in the implementation handoff.

---

## 1. Overall Visual Direction

**Mood:** mature operations console, not a marketing surface. Closer to Linear / Vercel dashboard / GitHub Issues than to a SaaS landing page. Calm neutrals, decisive accents only on status, generous whitespace for scan-ability, but dense enough that an implementer working five client stores does not feel like the UI is wasting their time.

**Core principles**

- **Workspace-first.** After auth the user lands on `/` which is the dashboard, never a hero or onboarding splash. New partners land on the *empty workspace* (same route, different state).
- **Status is the visual language.** Color is reserved almost entirely for the eight status tokens (Ready / Needs setup / Blocked / Verification failed / Waiting on merchant / Waiting on operator / Revoked / Escalated). Everything else is neutral.
- **Lists over cards.** `Table` is the default; cards only appear when the unit of information is a single object (catalog entry, escalation header) and never nested.
- **Drawers over modals** for inspecting a row without losing list context. Modals are reserved for irreversible commits (create escalation, revoke, request access).
- **Operator vocabulary stays out.** No "deployment", "vector", "queue", "secret", "Railway", "provider" in partner-facing UI. Partner verbs are `Apply Starter template`, `Run verification pack`, `Export evidence`, `Escalate blocker`.
- **Light + dark from day one.** The audience is technical; dark mode is table stakes.

---

## 2. Navigation Structure

Two-level nav: a fixed left rail (icon + label) and a top app bar with the workspace switcher, access-state hint, escalation count, and account menu. No nested side-nav drawers.

**Left rail (in this order)**

| Section | Route | Icon (`@mui/icons-material`) |
|---|---|---|
| Dashboard | `/` | `SpaceDashboardOutlined` |
| Client stores | `/stores` | `StorefrontOutlined` |
| New implementation | `/implementations/new` | `AddTaskOutlined` (CTA-styled, slightly separated) |
| Intelligence catalog | `/catalog` | `AutoAwesomeOutlined` |
| Templates & playbooks | `/templates` | `CollectionsBookmarkOutlined` |
| Verification packs | `/verification` | `FactCheckOutlined` |
| Evidence bundles | `/evidence` | `FolderZipOutlined` |
| Support | `/support` | `SupportAgentOutlined` (badge with open-escalation count) |
| Documentation | `/docs` | `MenuBookOutlined` |

**Top bar (left → right):** Logo · workspace name + chevron (partner account switcher, hidden if only one) · global search (`/`) · `Help` link · escalation bell · avatar menu (Profile, Members, Sign out).

**Routing** (React Router v6, all under `<RequireAuth>`):

```
/login                          → public
/auth/callback                  → public
/                               → DashboardPage
/stores                         → ClientStoresPage
/stores/:storeId                → ClientStoreWorkspace (tabbed)
/stores/:storeId/access         → StoreAccessRequestPage
/implementations/new            → NewImplementationPage (stepper)
/implementations/:requestId     → ImplementationRequestDetail
/catalog                        → IntelligenceCatalogPage
/catalog/:surfaceId             → CatalogSurfaceDrawer (modal route)
/templates                      → TemplatesPage
/templates/:templateId          → TemplateDetailDrawer
/verification                   → VerificationPacksPage
/verification/:runId            → VerificationRunDetail
/evidence                       → EvidenceBundlesPage
/evidence/:bundleId             → EvidenceBundleDetail
/support                        → SupportCenterPage
/support/:escalationId          → EscalationDetailPage
/docs/*                         → DocumentationPage
/settings/members               → MembersPage
/settings/profile               → ProfilePage
```

---

## 3. MUI Theme Recommendation

A single theme factory in `src/theme/index.ts` returning light/dark variants from the same token base.

**Palette**

```ts
// Neutral, slightly cool. Brand accent stays restrained.
const tokens = {
  brand:    { main: '#5B5BD6' },        // indigo, used sparingly (logo, primary CTA)
  surface:  { canvas: '#F7F8FA', paper: '#FFFFFF', elevated: '#FFFFFF' },
  text:     { primary: '#0F172A', secondary: '#475569', disabled: '#94A3B8' },
  border:   { default: '#E2E8F0', strong: '#CBD5E1' },

  // Status palette — these are the ONLY colors used for state.
  status: {
    ready:           { main: '#16A34A', bg: '#ECFDF5', fg: '#166534' },
    needsSetup:      { main: '#2563EB', bg: '#EFF6FF', fg: '#1E40AF' },
    blocked:         { main: '#DC2626', bg: '#FEF2F2', fg: '#991B1B' },
    verifyFailed:    { main: '#EA580C', bg: '#FFF7ED', fg: '#9A3412' },
    waitingMerchant: { main: '#A16207', bg: '#FEFCE8', fg: '#854D0E' },
    waitingOperator: { main: '#7C3AED', bg: '#F5F3FF', fg: '#5B21B6' },
    revoked:         { main: '#475569', bg: '#F1F5F9', fg: '#334155' },
    escalated:       { main: '#BE123C', bg: '#FFF1F2', fg: '#9F1239' },
  },
};
```

Dark mode flips canvas (`#0B0F17`), paper (`#111827`), border (`#1F2937`), keeps status hues but raises `bg` opacity to ~0.16 of `main`.

**Typography**

- Family: `'Inter', -apple-system, system-ui, sans-serif` (loaded via `<link>` in `index.html`, not via JS).
- Mono family for IDs/codes/JSON: `'JetBrains Mono', ui-monospace, SFMono-Regular, monospace`.
- Scale (rem): `h1 1.75 / 600`, `h2 1.375 / 600`, `h3 1.125 / 600`, `h4 1 / 600`, `subtitle1 0.875 / 600`, `body1 0.875 / 400`, `body2 0.8125 / 400`, `caption 0.75 / 500`, `overline 0.6875 / 600 / uppercase / letterSpacing 0.06em`.
- Default body: `0.875rem` — operations density.

**Shape & spacing**

- `shape.borderRadius: 8`. Buttons, chips, inputs all radius `8`. Drawers/sheets `12`. Pills/chips for status `999`.
- `spacing(1) = 8px`. Page gutter `spacing(3)`, section gap `spacing(4)`, dense table cell padding `spacing(1, 1.5)`.

**Component defaults** (`createTheme({ components: { ... } })`)

- `MuiButton`: `disableElevation: true`, `size: 'small'` default. Variants: `contained` for primary action only (max one per view), `outlined` for secondary, `text` for tertiary/destructive-confirm-dialog cancel.
- `MuiTextField`: `size: 'small'`, `variant: 'outlined'`.
- `MuiTable`: `size: 'small'`, sticky header by default.
- `MuiTableCell`: `borderBottom: 1px solid border.default`, no zebra.
- `MuiChip`: `size: 'small'`, custom `variant: 'status'` (see §5).
- `MuiPaper`: `elevation: 0` default, `1px solid border.default`. Elevation reserved for menus/popovers.
- `MuiTabs`: indicator `2px`, no all-caps, font-weight 600 on selected.
- `MuiDialog`: `maxWidth: 'sm'` default, `PaperProps={{ sx: { borderRadius: 12 } }}`.
- `MuiDrawer`: `anchor: 'right'`, width `min(560px, 100vw)`.
- `MuiAlert`: `variant: 'outlined'`, `iconMapping` per status.
- `MuiTooltip`: `arrow: true`, `enterDelay: 400`.
- `MuiLinearProgress`: thickness `2`.

---

## 4. Page-by-Page Layout

For every page: page header with `<PageHeader title subtitle actions breadcrumbs />`, then content. No page has a hero. Tables use TanStack Query for data, RHF + Zod for any form.

### 4.1 Login / Signup (`src/pages/LoginPage.tsx`)

Centered card, `maxWidth 400`, single column. No marketing copy beyond a one-line value statement.

```
┌──────────────────────────────────────┐
│  [logo]  LoomAI Partners             │
│                                      │
│  Sign in to your partner workspace   │
│                                      │
│  [ G  Continue with Google      ]    │  ← contained button
│  [    Continue with Apple       ]    │  ← outlined
│  [ in Continue with LinkedIn    ]    │  ← outlined
│                                      │
│  ──────────  or  ──────────          │
│                                      │
│  Email                               │
│  [                              ]    │
│  [ Send sign-in link            ]    │
│                                      │
│  By continuing you agree to the      │
│  Partner Terms.                      │
└──────────────────────────────────────┘
```

- No separate signup screen — Supabase social login is the signup. First successful login that doesn't match a `PartnerMember` triggers an inline "Set up your workspace" panel (one input: workspace name; one button: Create).
- `/auth/callback`: full-bleed centered `CircularProgress` + "Completing sign-in…" with a 5s fallback `Alert` linking back to `/login`.
- Loading state: buttons go `disabled` with `<CircularProgress size={16}>` adornment.
- Error state: top-of-card `<Alert severity="error" variant="outlined">` with sign-in-method-specific message and a "Try again" link.

### 4.2 Empty Partner Workspace (Dashboard, no assignments)

Same `/` route as Dashboard — render conditionally on `session.assignedStoreCount === 0 && implementations.length === 0`.

```
PageHeader: "Welcome to your workspace, {firstName}"
            subtitle: "You don't have any client stores yet. Here's how to get started."

Two-column grid (md=8 / md=4):

LEFT (md=8): Numbered ActionList — three rows, each a Paper with icon + title + body + button
  1. Browse the intelligence catalog          → "Open catalog"   (outlined)
  2. Try the sandbox / demo store              → "Open sandbox"   (outlined)
  3. Start a client implementation request     → "New implementation" (contained)

RIGHT (md=4): "Resources" Paper
  • Implementation guide          (link)
  • Verification pack reference   (link)
  • Vertical playbooks (4 chips: Fashion · Electronics · Health/Beauty · Home)
  • Support: configured partner support channel

Bottom strip: "Pending approvals (0)" — collapsed Accordion summary so partner sees where merchant approvals will appear later.
```

No empty illustrations. No confetti. The page is still useful: catalog, sandbox, templates are all reachable.

### 4.3 Dashboard (with assignments) — `/`

Three rows of operational signal, no chrome.

```
Row 1 — KPI strip (4 small Paper tiles, one line each):
  Stores assigned: 12   |   Open escalations: 3   |   Verifications passed (7d): 18   |   Pending merchant approvals: 2

Row 2 — "Needs your attention" Table (default sort: severity desc, then dueDate asc)
  Columns: Store | Status chip | Blocker | Owner | Due | →
  Empty state: "Nothing on fire. Stores will appear here when they need action."

Row 3 — Two-column:
  Left (md=7): "Recent activity" Timeline (last 10 events: store assigned, verification ran, escalation reply, merchant approved access)
  Right (md=5): "Quick actions" stacked outlined buttons: New implementation · Run verification · Open escalation · Browse catalog
```

### 4.4 Client Stores — `/stores`

Operational table, full width.

```
PageHeader: "Client stores" · action: [+ New implementation]

Filter bar (sticky under header):
  [Status ▾] [Plan ▾] [Vertical ▾] [Owner ▾]  ────  [Search shop domain ___]   [⊟ Density]  [↧ Export CSV]

Table columns (TanStack Query, server pagination):
  ☐ | Shop domain ↓ | Merchant | Plan | Surfaces enabled | Knowledge Sync | Readiness | Last activity | Owner | Actions(⋮)
```

- Row click opens `/stores/:storeId` (full page, not drawer — too much detail).
- Bulk actions appear above the table when rows are selected (Run verification, Export evidence, Reassign owner).
- Empty state (no stores assigned): centered `<EmptyState>` with the same three-step guidance from §4.2 in compact form.

### 4.5 New Client Implementation — `/implementations/new`

`Stepper` (vertical on mobile, horizontal on md+). One step per page-section, advance disabled until valid (RHF + Zod).

**Steps**

1. **Client context** — client name, contact email, vertical (Select), expected go-live date, notes.
2. **Store** — shop domain (`*.myshopify.com` validated by Zod regex), requested package/tier context (Free/Starter/Elite helper text only until the store is approved), known integrations (multi-select chips). Partners can request or describe package intent, but cannot approve Shopify billing or change a paid plan.
3. **Surfaces requested** — checklist generated from catalog, filtered by tier rules (Free auto-restricts to AI search; helper alert if user crosses the gate).
4. **Approval method** — three radio cards:
   - "Send merchant approval link" (default) — backend generates code, partner gets shareable URL + 7-day expiry chip.
   - "Approved app install / claim flow" — show install URL preview.
   - "Operator assignment" — disabled with tooltip "Only operators can use this path; request via support."
5. **Review & submit** — read-only summary, primary `Create implementation request` button.

After submit, navigate to `/implementations/:requestId` (see §4.6).

### 4.6 Store Access Request / Merchant Approval Status — `/implementations/:id`

Two-column layout.

```
LEFT (md=8): Approval status Paper (the centerpiece)
  Big status chip: "Waiting on merchant"
  Approval link: <CodeBlock> with copy button + "QR" button (opens dialog)
  Sent to: merchant@example.com  · Sent 2d ago  · Expires in 5d
  [Resend link] [Revoke link] [Use different method]

  Timeline below:
   • Created       2026-04-23 14:02   you
   • Link sent     2026-04-23 14:02   system
   • Link opened   2026-04-24 09:11   merchant (IP region: UK)
   • —waiting—

RIGHT (md=4): Request summary
  Client · Vertical · Plan · Surfaces (chips) · Owner
  "Edit request" link (only while no approval has occurred)
```

State variants for the big status chip:

- `Waiting on merchant` (yellow) — the default after submit.
- `Approved` (green) — Timeline gains "Approved by {name}", primary button changes to `Open store workspace`.
- `Revoked` (slate) — link expired or merchant rejected; CTA `Start a new request`.
- `Blocked` (red) — backend validation failure (e.g. shop domain conflict); details in `<Alert severity="error">`.

### 4.7 Client Store Workspace — `/stores/:storeId`

The most complex page. Header with breadcrumbs + tabs.

```
Breadcrumbs: Stores / acme-fashion.myshopify.com
Header: shop domain (h2)  +  status chip + plan chip + owner avatar
Header right: [Run verification] [Export evidence] [Open escalation] (⋮ More)

Tabs: Overview · Setup · Surfaces · Knowledge Sync · Verification · Evidence · Escalations · Notes
```

**Overview tab** — three Paper sections in a 2-col grid:

- Readiness summary (status, top blocker, next action — large, single-purpose)
- Knowledge Sync summary (last sync, source readiness counts)
- Recent activity (last 8 timeline items)

**Setup tab** — `<Stepper orientation="vertical" nonLinear>` mapping the implementation checklist (client identified → Companion installed → plan confirmed → theme app embed → blocks placed → Knowledge Sync healthy → surfaces visible → Max Mode handoff → analytics → support handoff → screenshots). Each step has: status chip, body, "Mark complete" / "Mark blocked" buttons, evidence attachment list.

**Surfaces tab** — table of catalog surfaces × this store: surface, tier-allowed, placement, status (Ready/Needs setup/Verification failed), last verified, action. Read-only when partner does not have a write scope.

**Knowledge Sync tab** — partner-safe summary only: source categories with `<Chip>` per category (Healthy / Stale / Missing), last sync time, "What is Knowledge Sync?" inline help. **No raw vector / queue / replay controls.** A single partner-safe action: `Request operator action` (opens prefilled escalation form).

**Verification tab** — past runs table + `Run verification pack` button. Run rows expand into the per-step results.

**Evidence tab** — bundles list (see §4.11).

**Escalations tab** — filtered list of escalations scoped to this store + inline `New escalation` button.

**Notes tab** — partner-internal notes only. RHF textarea, list below sorted desc.

### 4.8 Intelligence Catalog — `/catalog`

Two-pane layout: left = filterable list, right = detail drawer (or page on mobile).

```
LEFT (md=4): List of surface cards (single-column, dense)
  Filters at top: [Tier ▾] [Surface type ▾] [Search]
  Each card: icon · name · tier chip · 1-line shopper problem

RIGHT (md=8): Catalog surface detail
  H2 surface name + tier chip
  Sections (Accordions, first 3 expanded by default):
    • What it does (shopper problem, launch-safe claim)
    • Where it appears (placement diagram - simple labeled box, NOT a marketing render)
    • Required source data
    • Required merchant setup
    • Verification steps (numbered)
    • Healthy result vs. failure signs (two-column)
    • Known limitations
    • Escalation evidence to capture (checklist)
  Footer actions: [Add to implementation] [Open sandbox example] [Copy claim text]
```

Tier badges throughout: `Free` (slate outline), `Starter` (indigo outline), `Elite` (gold outline + "Verified governed actions only" tooltip).

### 4.9 Templates / Playbooks — `/templates`

Tabbed: `Vertical playbooks` · `Implementation templates` · `Support handoff templates` · `Launch checklists` · `Troubleshooting`.

Each tab: dense list (Paper rows, not big cards) with: name, vertical chip, surface coverage chips, "last updated", `Use template` button. Detail opens in right drawer with markdown body, "duplicate to my workspace" action, and a "What this assumes" expandable note (tier, scopes, vertical assumptions) — important so partners don't apply Elite templates to Starter stores.

### 4.10 Verification Packs — `/verification`

Two surfaces:

- **Pack library** (top, collapsible): the canonical packs (Starter Read-Only, Free AI Search, Tier Gate, Knowledge Sync Readiness…) — each a row with `Run on store ▾` selector.
- **Run history table**: store, pack, started, duration, result chip (Pass / Partial / Fail / Blocked), steps passed, "View report".

`/verification/:runId` is a read-only report page — sticky summary header (overall status + counts), then a vertical step list with check name, status chip, evidence attached, partner-safe failure reason (no stack traces). Footer actions: `Open escalation from this run` (prefills an escalation with the failed step IDs as evidence references), `Download report (PDF)`, `Copy summary`.

### 4.11 Evidence Bundles — `/evidence`

Table: bundle name, store, kind (`Launch packet` / `Verification pack` / `Support bundle` / `Lifecycle packet`), generated, generated by, size, attachments count, status (`Ready` / `Generating` / `Expired`).

Bundle detail page: read-only summary fields (no editing — bundles are immutable snapshots), attachment list with redaction badges, copy-permalink, download as zip.

Important: **screenshots/videos render thumbnails only after a click** ("Show preview" button) — to keep the partner UI from accidentally inlining merchant data on a shared screen.

### 4.12 Support Center — `/support`

Default view: **Escalations table** (this is the primary reason to visit this page). Tabs at top: `My escalations` · `Workspace escalations` · `Resolved`.

```
Filter bar: [Status ▾] [Severity ▾] [Store ▾] [Owner ▾]  ──  [Search]  [+ New escalation]

Columns: ID | Title | Store | Severity | Status | Owner | Next action | Due | Updated
Row click → /support/:escalationId
```

Empty state: action-oriented: "No open escalations. Most blockers are caught by the verification pack — try running it before opening one."

Secondary: a small `Resources` strip below — links to Documentation, support email, response-time policy. Not a contact form (escalations are the contact form).

### 4.13 Escalation Detail With Reply Thread — `/support/:escalationId`

See §7 for the thread itself. Full layout:

```
PageHeader (sticky):
  Breadcrumbs: Support / ESC-1042
  H2: "Knowledge Sync stale on collection X"
  Right: status chip · severity chip · [Change status ▾] [Assign ▾]

Two-column layout (md=8 / md=4):

LEFT — main content:
  Summary panel (Paper): description, reproduction steps, expected vs actual, impact
  Evidence panel: attached bundle + per-attachment list with thumbnails (deferred render)
  Verifications referenced: chips linking to verification runs
  Reply thread (see §7) — anchored mid-page, fills remaining height
  Composer (sticky-bottom inside the thread panel)

RIGHT — metadata sidebar (sticky):
  Status chip (large)
  Severity (P1/P2/P3/P4 with color)
  Owner (Avatar + name + "Reassign")
  Next action (editable text)
  Due date (native date input or approved date component)
  Created · Updated
  Store (link)
  Verification run (link)
  Visibility key (small legend explaining PARTNER_VISIBLE / OPERATOR_VISIBLE)
  Timeline (vertical, condensed: status changes, assignments, resolutions)
```

`Resolved` and `Closed` states: composer collapses to a one-line "Reopen escalation" button, sidebar shows `Resolution summary` Paper with the partner-visible resolution text.

### 4.14 Documentation — `/docs`

Two-pane: left TOC (sticky, 240px), right markdown content. Markdown rendered with `@mui/material` typography (`<MuiMarkdown>` wrapper that maps `<h2>` → `<Typography variant="h3">`, etc.). Code blocks use the mono font from §3 with a copy button. **No external doc tool, no iframe.** Search input above TOC filters headings client-side. Keep this thin — it links out to the canonical docs in `Final_Documentation/` rather than duplicating.

---

## 5. Key Components and Their Props/States

A small, owned component library in `src/components/` — each component has a clear contract.

### `<StatusChip status size variant />`

Single source of truth for the eight status tokens. Wraps `MuiChip`.

```ts
type PartnerStatus =
  | 'ready' | 'needsSetup' | 'blocked' | 'verifyFailed'
  | 'waitingMerchant' | 'waitingOperator' | 'revoked' | 'escalated';

interface StatusChipProps {
  status: PartnerStatus;
  size?: 'small' | 'medium';
  variant?: 'filled' | 'outlined';   // default 'outlined'
  withIcon?: boolean;                 // default true
  label?: string;                     // override default copy
}
```

Maps to status palette from §3. Icon mapping: `CheckCircleOutline`, `BuildOutlined`, `BlockOutlined`, `ErrorOutline`, `HourglassEmptyOutlined`, `ShieldOutlined`, `CancelOutlined`, `ReportProblemOutlined`.

### `<PageHeader title subtitle breadcrumbs actions />`

Standard top-of-page block. `actions` slot is right-aligned Stack of buttons (max 3). Sticks to top with shadow on scroll (use `useScrollTrigger`).

### `<DataTable<T> columns rows query rowKey onRowClick />`

Thin wrapper around MUI `Table` + TanStack `useQuery`. Owns: sticky header, dense default, loading skeletons (10 rows × n cols), empty state slot, pagination footer, and an `onRowClick` that navigates. Don't reach for `DataGrid` initially — `Table` is enough and ships less JS.

### `<EmptyState icon title body action />`

Single component for every empty surface. Centered, max-width 480px, no illustrations.

### `<DetailDrawer open onClose title actions>{children}</DetailDrawer>`

Right-anchored drawer used for catalog detail, template detail, escalation quick-view from list. Width `min(560, 100vw)`, sticky footer for actions.

### `<ConfirmDialog open onConfirm onCancel title destructive />`

Used for revoke, resend approval link, mark blocked, etc. `destructive` flips primary button to `color="error"` and requires the user to type a confirmation phrase for revoke.

### `<AccessGuard requires={Permission}>{children}</AccessGuard>`

Renders children only if the partner principal has the required scope. Falls back to `<UnauthorizedState />` (see §6).

### `<EscalationThread escalationId />`

See §7.

### `<EvidenceAttachment attachment />`

Renders a row: icon (by mime type), filename, redaction badge if `redacted: true`, "Show preview" button (no auto-load), download link. Never auto-renders images.

### `<VerificationStepRow step />`

Status chip · step name · checked-at timestamp · evidence count · expand for details. Inside expansion: failure reason (partner-safe), suggested fix, "Open escalation from this step" link.

### `<ApprovalLinkCard request />`

The centerpiece of §4.6. Big status chip, copy-to-clipboard URL, QR dialog, expires-in countdown using `Intl.RelativeTimeFormat`.

### `<TierBadge tier />`

Compact inline badge for `Free` / `Starter` / `Elite`, used inside catalog and surface tables.

### Form primitives (RHF + Zod)

- `<TextFieldRHF name control rules ... />`
- `<SelectRHF name control options />`
- `<DateInputRHF />` using a plain `<input type="date">` styled through MUI. Do not add `@mui/x-date-pickers` in the first slice unless explicitly approved.
- `<FormSection title description>{children}</FormSection>` — visual grouping inside steppers.

---

## 6. Empty / Loading / Error / Revoked / Unauthorized States

A single, predictable hierarchy. Every page implements these in this order before rendering content:

1. **Auth check** — if no Supabase session, redirect to `/login`.
2. **Authorization check** — if backend says the partner lacks scope for the route, render `<UnauthorizedState />`.
3. **Loading** — render skeleton matching the eventual layout (table skeleton, form skeleton, detail skeleton). Use `Skeleton` with `variant="rounded"` for blocks and `variant="text"` for lines. Avoid global spinners except on `/auth/callback`.
4. **Error** — render `<ErrorState error retry />` with `<Alert severity="error" variant="outlined">` and a `Try again` button calling `query.refetch()`.
5. **Empty** — render `<EmptyState>` with action-oriented copy and a primary CTA.
6. **Content** — the real page.

**Concrete copy table**

| Surface | Empty | Loading | Error | Revoked | Unauthorized |
|---|---|---|---|---|---|
| Stores list | "No client stores yet. Start a client implementation to request merchant approval." [+ New implementation] | 10-row skeleton | "We couldn't load your stores." [Try again] | Row-level: `<StatusChip status="revoked">`, action menu disabled | "Your account no longer has access to client stores. Contact your partner admin." |
| Store workspace | n/a (route only valid with a store) | Tab-shaped skeleton | "Couldn't load this store." | Full-page `<UnauthorizedState>` "Access to this store was revoked on {date} by {actor}." with `Back to stores` link | Same as revoked |
| Catalog | "No surfaces match your filters." [Clear filters] | List skeleton + empty drawer skeleton | inline alert in list pane | n/a | n/a (catalog is always partner-visible) |
| Verification packs | "No verification runs yet. Run a pack from a store workspace to see results here." [Browse stores] | Table skeleton | inline alert | n/a | n/a |
| Support | "No open escalations. Most blockers are caught by verification — try a pack first." [Open verification] | Table skeleton | inline alert | n/a | "You don't have permission to view escalations." |
| Escalation detail | n/a | Skeleton matching layout | "Couldn't load this escalation." | "This escalation belongs to a store whose access was revoked." | "You don't have permission to view this escalation." |

**Revoked state details:** the status chip becomes the visual cue everywhere — slate gray with a `CancelOutlined` icon. Row-level revocations gray the row text to `text.disabled` and disable interactive elements. Full-page revocation is its own component:

```tsx
<UnauthorizedState
  icon={<ShieldOutlined />}
  title="Access revoked"
  body="Access to acme-fashion.myshopify.com was revoked on Apr 23, 2026."
  primaryAction={{ label: 'Back to stores', to: '/stores' }}
  secondaryAction={{ label: 'Open support', to: '/support' }}
/>
```

**Unauthorized vs Forbidden:** if the API returns 401, force a re-login; if 403, render `<UnauthorizedState>` and **never** retry — looping on 403 is a known cause of audit-log noise.

---

## 7. Escalation Reply Thread Design

The thread is **not** a chat. It's a structured, append-only conversation log with explicit visibility per entry. Partner UI **only** ever fetches and renders entries with `visibility === 'PARTNER_VISIBLE'`. Backend must enforce; frontend treats it as defense-in-depth.

### Visual structure

```
┌─ Reply thread ──────────────────────────────────────────────────────┐
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ [Avatar]  Mahmoud A. · Partner · 2d ago        [PARTNER_VISIBLE]│ │
│  │ Knowledge Sync hasn't refreshed since Tuesday on the           │  │
│  │ "summer-sale" collection. Re-running the sync from the store   │  │
│  │ workspace did not change last-sync timestamp.                  │  │
│  │                                                                 │  │
│  │ 📎 verification-run-7821.pdf   📎 sync-screenshot.png          │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ [Avatar]  LoomAI Support · Operator · 1d ago  [PARTNER_VISIBLE]│  │
│  │ Thanks — we've identified the source. We'll publish a fix and │  │
│  │ re-sync this collection. ETA today end-of-day UTC.            │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │ ⓘ  Status changed to "Waiting on operator" by LoomAI Support   │  │
│  │    1d ago                                                      │  │
│  └───────────────────────────────────────────────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘

Composer (sticky-bottom, only shown when status is not Resolved/Closed):

  [Avatar]  Reply as Mahmoud A. (Partner)
  ┌─────────────────────────────────────────────────────────────────┐
  │ Markdown allowed. Don't include screenshots of customer data.   │
  │                                                                 │
  └─────────────────────────────────────────────────────────────────┘
  [Attach evidence ▾]              ❗ Replies are visible to LoomAI Support.
                                   [Cancel]  [Send reply]
```

### Component contract

```ts
// PartnerSupportReply (PARTNER_VISIBLE only — backend filters)
interface ReplyEntry {
  id: string;
  authorName: string;
  authorAvatarUrl?: string;
  authorRole: 'PARTNER_ADMIN' | 'PARTNER_IMPLEMENTER' | 'PARTNER_DEVELOPER'
            | 'PARTNER_SUPPORT' | 'OPERATOR';   // partner UI never sees finer-grained operator roles
  visibility: 'PARTNER_VISIBLE';                 // type-narrowed; other values are 4xx in this UI
  createdAt: string;
  bodyMarkdown: string;
  attachments: EvidenceAttachment[];
}

interface TimelineEntry {
  id: string;
  kind: 'STATUS_CHANGE' | 'ASSIGNMENT' | 'RESOLUTION';
  actorName: string;
  actorRole: string;
  createdAt: string;
  payload: Record<string, unknown>;  // rendered by per-kind component
}

type ThreadItem =
  | { type: 'reply'; entry: ReplyEntry }
  | { type: 'timeline'; entry: TimelineEntry };
```

### Behavior rules

- **Visibility legend** is always present in the right sidebar, explaining: "You see partner-visible replies. Operators may keep internal notes that we don't show here."
- **Composer disabled** when escalation is `Resolved` or `Closed` — replaced by `Reopen escalation` button.
- **Optimistic insert** on send (TanStack Query mutation) with retry on failure. Failed replies show a per-bubble error banner with `Retry / Discard`.
- **Keyboard:** `Cmd/Ctrl + Enter` sends. `Esc` blurs.
- **Markdown** rendered via the same `<MuiMarkdown>` wrapper used in `/docs`. Start with a strict markdown subset and no raw HTML. If the renderer emits HTML, add sanitizer support explicitly and document the dependency.
- **Attachments** use `<EvidenceAttachment>` and obey the deferred-preview rule from §4.11.
- **Polling**: TanStack Query `refetchInterval: 30_000` while the page is focused, paused otherwise. No websocket needed for v1.
- **Unsafe content guard**: a static client-side check warns the partner if their reply text contains anything that looks like a token (`sk_...`, `ghp_...`, `xoxb-...`) or an email domain matching a customer pattern, and asks them to confirm.

### Resolution display

When status flips to `Resolved`, prepend a permanent banner above the thread:

```
┌─ ✅ Resolved by LoomAI Support · Apr 24, 2026 ────────────────────┐
│ Sync has been re-baselined for the affected collection. Verified │
│ via verification-run-7902. No partner action required.           │
└──────────────────────────────────────────────────────────────────┘
```

This is the **resolution summary** field — separate from the last reply, written cleanly for merchant export.

---

## 8. Mobile / Responsive Behavior

The primary user is on a laptop, but the page must remain usable on a phone for triage (escalation reply, approval status check).

**Breakpoints** (MUI defaults: `xs 0 / sm 600 / md 900 / lg 1200 / xl 1536`).

**Layout shifts**

- Left rail collapses to a `<Drawer>` triggered by a hamburger in the top bar at `< md`.
- Two-column page layouts (e.g. escalation detail) stack vertically at `< md`. Sidebar moves above the main content; the composer becomes a sticky-bottom bar.
- Tables: at `< md`, `DataTable` renders one row per card (label: value rows) instead of a horizontal table. Use a `useMediaQuery(theme.breakpoints.down('md'))` hook in `<DataTable>` to switch rendering modes.
- Filter bars collapse into a single `<Filters>` button that opens a bottom sheet.
- Stepper switches to vertical orientation `< md`.
- Drawers go full-screen `< sm` (`PaperProps={{ sx: { width: '100vw' } }}`).

**Touch targets**: minimum 44px hit area. Status chips remain at 24px height but their row container (table cell, list item) gets `minHeight: 44` on touch devices.

**Top bar on mobile**: logo + escalation bell + avatar only. Workspace switcher moves into the avatar menu.

---

## 9. Accessibility

Targets WCAG 2.2 AA. Concrete rules:

- **Color**: status chip text and icon must clear 4.5:1 against their `bg`; verified for both light and dark in the palette in §3 (run an axe audit at theme switch).
- **Color is never sole signal**: every status uses chip text + icon + (in tables) a leading icon column.
- **Focus**: keep MUI's default focus ring, do not `outline: none`. Add a visible outline on `Paper` rows that act as buttons: `&:focus-visible { outline: 2px solid palette.primary.main; outline-offset: 2px; }`.
- **Skip link**: invisible-until-focused "Skip to content" anchor at top of `<AppShell>`.
- **Landmarks**: `<header>` (top bar), `<nav aria-label="Primary">` (left rail), `<main id="main">` (page content). Drawers use `role="dialog"` (MUI handles).
- **Tables**: caption via visually-hidden `<caption>`, `scope="col"` on headers, `aria-sort` on sortable columns.
- **Forms**: every field has a visible `<label>` (RHF + MUI handles); errors use `aria-describedby` on the input and `role="alert"` on the message.
- **Live regions**:
  - Reply thread: `aria-live="polite"` on the thread container so new operator replies announce.
  - Verification runs: progress announcement when state transitions.
- **Keyboard**:
  - Global shortcut `g s` (stores), `g e` (escalations), `g c` (catalog), `n` (new escalation when in support context), `?` (shortcut cheatsheet dialog). Implement with a small `useHotkeys` helper, document in the cheatsheet.
  - Tab order matches visual order on every page; explicitly verify on stepper pages.
- **Reduced motion**: respect `prefers-reduced-motion` — disable drawer slide animation and table row hover transitions when set.
- **Screen-reader only text**: a `<VisuallyHidden>` component for status icon descriptions ("Status: Waiting on merchant").
- **Internationalization**: dates via `Intl.DateTimeFormat`, relative times via `Intl.RelativeTimeFormat`, currency never shown in partner UI (no commerce surface). All strings in a single `i18n/en.ts` keyed object so a future locale swap is mechanical.

---

## 10. Implementation Notes for a React/MUI Engineer

### Backend companion boundary

This UI must be backed by the extraction-ready Partner Enablement module inside `Platfrom/backend`.

Expected backend package boundary:

```text
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/partner/
  config/
  security/
  entity/
  repository/
  service/
  web/
  model/
  audit/
  gateway/
```

UI integration rules:

- call only `/api/partners/*` and `/api/merchant/partner-access/*`
- never call operator/admin/deployment/provider/secret/vectorization endpoints
- expect partner-safe read models from the backend partner module
- treat Shopify/store readiness, evidence, catalog, and verification as partner-safe summaries, not raw Platform or Shopify internals
- preserve the ability for the backend partner module to be extracted later by depending only on API contracts, not frontend assumptions about internal services

### Project layout

```
Platfrom/partner-ui/
├─ src/
│  ├─ main.tsx                  # bootstrap: ThemeProvider, QueryClientProvider, Router, SupabaseProvider
│  ├─ AppShell.tsx              # left rail + top bar + <Outlet />
│  ├─ theme/
│  │  ├─ index.ts               # createTheme factory (light/dark)
│  │  ├─ tokens.ts              # palette, typography, spacing tokens
│  │  └─ statusTokens.ts        # the 8 status definitions, single source of truth
│  ├─ auth/
│  │  ├─ SupabaseProvider.tsx   # @supabase/supabase-js client + session context
│  │  ├─ RequireAuth.tsx        # route guard
│  │  ├─ AccessGuard.tsx        # permission-scoped guard
│  │  └─ apiClient.ts           # fetch wrapper that injects Bearer token
│  ├─ api/
│  │  ├─ session.ts             # GET /api/partners/session
│  │  ├─ stores.ts              # store list/detail
│  │  ├─ implementations.ts
│  │  ├─ catalog.ts
│  │  ├─ verification.ts
│  │  ├─ evidence.ts
│  │  ├─ escalations.ts
│  │  └─ schemas.ts             # Zod schemas — runtime + types both derive
│  ├─ components/
│  │  ├─ StatusChip.tsx
│  │  ├─ PageHeader.tsx
│  │  ├─ DataTable.tsx
│  │  ├─ EmptyState.tsx
│  │  ├─ DetailDrawer.tsx
│  │  ├─ ConfirmDialog.tsx
│  │  ├─ EscalationThread.tsx
│  │  ├─ EvidenceAttachment.tsx
│  │  ├─ VerificationStepRow.tsx
│  │  ├─ ApprovalLinkCard.tsx
│  │  ├─ TierBadge.tsx
│  │  ├─ MuiMarkdown.tsx
│  │  ├─ VisuallyHidden.tsx
│  │  └─ form/                  # TextFieldRHF, SelectRHF, FormSection
│  ├─ pages/                    # one folder per route (page + page-local components)
│  ├─ hooks/                    # useHotkeys, useDebouncedSearch, useStickyHeader
│  ├─ utils/                    # date formatters, redaction helpers, copy-to-clipboard
│  └─ i18n/en.ts
├─ public/
└─ index.html
```

### Stack wiring

- **TanStack Query**: one `QueryClient` in `main.tsx`, `defaultOptions: { queries: { staleTime: 30_000, retry: (count, err) => err.status !== 401 && err.status !== 403 && count < 2 } }`. Use query keys shaped `['stores', { filters }]`, `['store', storeId]`, `['escalation', id, 'thread']`.
- **Supabase**: a single client created once, passed via context. `onAuthStateChange` invalidates all queries.
- **API client**: `fetch`-based wrapper that pulls the current Supabase access token, sets `Authorization: Bearer …`, parses JSON, throws typed `ApiError` with status. Validates response with Zod schema and logs schema mismatch to console in dev.
- **Routing**: declare routes in a single `routes.tsx` so the left rail can introspect them for label/icon. Lazy-load page components with `React.lazy` + `Suspense` (keep the shell synchronous).
- **Forms**: every form is RHF + Zod via `zodResolver`. Schemas live next to the API request in `api/schemas.ts` so request validation is shared.
- **State**: do not introduce Redux/Zustand. URL is state for filters and pagination (`useSearchParams`); server cache is state for data; React state is local UI only.

### Build & verification

- The doc's verifier command works as-is: `npm --prefix Platfrom/partner-ui run build`.
- TypeScript `strict: true`, `noUncheckedIndexedAccess: true`. No `any` in components.
- Add a `tsx` smoke script at `scripts/partner-ui-smoke.mjs` mirroring the existing `phase19-ui-auth-smoke.mjs` pattern in `Platfrom/ui/scripts/` for the auth callback path.

### Platform Release Gate Requirements

The Partner UI is not release-ready just because the Vite build passes. Any UI change that affects auth routing, access guards, partner API calls, store visibility, evidence downloads, verification pack screens, escalation visibility, or deployed routing must be included in the Platform live release gate.

Minimum UI live gate coverage:

- deployed Partner UI shell loads from the target environment.
- `/login` renders with missing-config and configured Supabase states handled cleanly.
- `/auth/callback` handles success and failure without exposing tokens in URL, console, screenshots, or local storage.
- authenticated new partner lands on the empty workspace with zero stores.
- client store routes render unauthorized/revoked states without leaking store data.
- API client refuses non-partner endpoint paths client-side.
- invalid/expired session returns user to login; `403` renders `UnauthorizedState` and does not retry in a loop.
- catalog page shows correct tier truth: Free AI search only, Starter read-only surfaces, no Starter order lookup.
- verification/evidence pages do not show raw vectorization/runtime/provider/secret/operator terms.
- escalation thread shows only `PARTNER_VISIBLE` replies and drops any unexpected visibility value.
- desktop and mobile smoke pass for dashboard, stores, new implementation, catalog, verification, evidence, and support routes.
- axe or equivalent accessibility smoke passes for login, dashboard, store list, and escalation detail in light and dark mode.

If no deployed partner UI exists yet, the implementing session must add the live gate as a tracked release blocker or create the first deployment path before claiming release readiness. Docs-only changes may skip live UI verification only with an explicit `CODEX_WORKING_CONTEXT.md` note.

### Things to be careful about

- **Never call operator endpoints from this UI.** Even if the network allows it, the audit log will flag it. The API client should refuse any URL not under `/api/partners/*` or `/api/merchant/partner-access/*`.
- **Evidence URLs are short-lived signed URLs.** Don't cache them in localStorage. Re-request on render.
- **Approval link copy** must use `navigator.clipboard.writeText` with a fallback `textarea` selection for non-secure contexts.
- **`status` enum on the wire is `SCREAMING_SNAKE_CASE`** (matches the Java backend) — convert at the API boundary in `schemas.ts` to the camelCase tokens in `statusTokens.ts`. Don't sprinkle conversion through components.
- **Reply visibility** — the partner UI must request the thread endpoint with no visibility filter parameter and trust the backend filter. Do not implement client-side filtering as the only protection; do trust-but-verify by asserting with Zod that every returned reply has `visibility === 'PARTNER_VISIBLE'` and dropping (with a console warning) anything else.
- **No telemetry libraries that exfiltrate URLs.** If you add analytics, scrub `:storeId`, `:escalationId`, `:requestId` from paths first.

### Suggested build order (matches Slices C–F in the foundation doc)

1. AppShell + theme + auth + empty Dashboard route. Verify a logged-in partner with no assignments lands on §4.2.
2. `<StatusChip>`, `<PageHeader>`, `<DataTable>`, `<EmptyState>` — the four primitives most pages depend on.
3. Stores list (§4.4) + Store workspace shell (§4.7 Overview tab only).
4. New implementation stepper (§4.5) + Approval status (§4.6).
5. Catalog + Templates (§4.8, §4.9) — they're mostly read-only and let you exercise the layout system without backend writes.
6. Verification + Evidence (§4.10, §4.11).
7. Support center + Escalation detail with thread (§4.12, §4.13, §7) — last because it depends on every primitive above.
8. Documentation + Mobile pass + a11y audit.
