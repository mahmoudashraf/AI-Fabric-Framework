# Platform UI Persona Separation Plan

Status: planning document (updated 2026-04-25)

This document defines who sees what across the LoomAI Labs product surfaces. The current platform UI shows everything to everyone. That is wrong. Each persona needs a different experience designed for their needs, not a filtered version of the admin panel.

Current strategy note:

- Partners are **implementation partners**: agencies, integrators, consultants, and developers helping merchants add LoomAI intelligence pieces to existing stores.
- This is **not** a passive acquisition program.
- Public partner signup, partner directories, certification, and commercial attribution surfaces are out of scope until implementation partners prove repeatable deployment and support.

---

## 1) The Problem

The current platform UI has one layout, one sidebar, one experience. Whether you are a merchant checking sync status or the platform builder debugging a provider connection, you see the same 17 nav items, the same engineering language, the same level of complexity.

This creates three failures:

- merchants are overwhelmed by technical surfaces they do not need
- implementation partners cannot efficiently set up, verify, and support multiple client stores
- the operator view is cluttered with surfaces that should be contextual

---

## 2) Three Personas

### 2.1 Merchant

**Who:** Shopify store owner who installed Loom Companion

**Technical level:** low to medium. Knows Shopify admin. Does not know what vectorization or LLM providers mean.

**Goal:** make their store smarter with AI. Configure what the AI shows, how it looks, and verify it is working.

**Frequency:** daily during setup, weekly after launch.

**Enters from:** Shopify admin (embedded app) or direct link to app.loomai.pro.

### 2.2 Partner

**Who:** agency, consultant, or integrator managing Loom Companion for their clients

**Technical level:** medium to high. Understands deployment, configuration, and troubleshooting. Does not need to touch infrastructure.

**Goal:** implement, verify, and support Loom Companion across multiple client stores efficiently.

**Frequency:** daily. Managing multiple stores, onboarding new merchants, monitoring health.

**Enters from:** partners.loomai.pro or direct link to app.loomai.pro with partner session.

### 2.3 Operator

**Who:** the platform builder (you), or future platform team members

**Technical level:** high. Full understanding of the platform architecture, providers, runtime, and infrastructure.

**Goal:** manage the entire platform — deployments, providers, security, diagnostics, user access, everything.

**Frequency:** daily. Building, debugging, monitoring.

**Enters from:** app.loomai.pro with admin session, or admin.loomai.pro.

---

## 3) Subdomain Assignment

```
app.loomai.pro           Merchant dashboard
partners.loomai.pro      Implementation partner workspace
admin.loomai.pro         Operator control plane (full platform UI)
```

Each subdomain is a separate frontend application (or a single app with role-based routing). Each has its own navigation, layout, language, and complexity level.

---

## 4) Merchant Dashboard — app.loomai.pro

### 4.1 What the merchant sees

```
┌─────────────────────────────────────────────────────────┐
│  ◆ Loom Companion                    ● My Store   ▾     │
│                                                         │
│  HOME                                                   │
│  ◇ Dashboard                                            │
│                                                         │
│  MY STORE                                               │
│  ◇ Intelligence Surfaces                                │
│  ◇ Appearance                                           │
│  ◇ Knowledge                                            │
│                                                         │
│  SETTINGS                                               │
│  ◇ Plan and Billing                                     │
│  ◇ Help                                                 │
└─────────────────────────────────────────────────────────┘
```

Total: 6 items in 3 groups. That is it.

### 4.2 Merchant pages

**Dashboard**

What it shows:
- store connection status (connected / syncing / error)
- knowledge sync status (products indexed, pages indexed, last sync time)
- AI activity summary (queries this week, popular questions)
- quick actions: "Preview on storefront", "Customize appearance"

What it does NOT show:
- provider configuration
- vectorization details
- deployment internals
- security policies
- diagnostic logs

Design:
- large status cards with green/amber/red indicators
- simple numbers (847 products synced, 23 pages indexed)
- one primary CTA: "Preview your store"

---

**Intelligence Surfaces**

What it shows:
- list of available intelligence surfaces (product insights, AI search, FAQ, comparison, policy strip, contextual pill)
- toggle each surface on/off
- preview of how each surface looks on the storefront
- placement instructions (which theme section to add the block)

What it does NOT show:
- RAG pipeline configuration
- embedding model details
- action routing internals

Design:
- card per surface with screenshot preview
- on/off toggle per card
- "How to add this" expandable instructions with Shopify theme editor screenshots

---

**Appearance**

What it shows:
- color picker for accent color
- font selection (match store theme)
- widget position and size
- branding toggle (show/hide "Powered by Loom Companion")
- live preview panel

What it does NOT show:
- CSS overrides
- theme extension code
- runtime configuration

Design:
- left panel: controls
- right panel: live preview (iframe or mock)
- changes apply in real time in preview

---

**Knowledge**

What it shows:
- knowledge sources with sync status:
  - Products: 847/847 synced, last sync 2 hours ago
  - Pages: 23/23 synced
  - Policies: 8/8 synced
  - Collections: 15/15 synced
- "Sync now" button
- sync history (last 10 syncs with status)

What it does NOT show:
- vector database details
- embedding dimensions
- indexing pipeline configuration

Design:
- simple table with progress bars
- green checkmarks for synced sources
- one button to trigger manual sync

---

**Plan and Billing**

What it shows:
- current plan (Free / Starter / Elite)
- what each plan includes
- upgrade/downgrade buttons
- billing history
- Shopify billing integration status

Design:
- plan comparison cards
- current plan highlighted
- clear upgrade CTA for free tier merchants

---

**Help**

What it shows:
- link to docs.loomai.pro
- common questions (FAQ accordion)
- contact support link
- status page link

---

### 4.3 Merchant language

Everything the merchant sees uses merchant language:

| Platform term | Merchant term |
|---|---|
| Deployment | My Store |
| Vectorization | Knowledge Sync |
| Intelligence surfaces | AI Features |
| RAG pipeline | — (hidden) |
| LLM provider | — (hidden) |
| Action catalog | — (hidden) |
| Confirmation interception | — (hidden) |
| Environment | — (hidden for single-store) |

The merchant never sees the word "deployment," "vectorization," "provider," or "runtime."

---

## 5) Implementation Partner Workspace — partners.loomai.pro

### 5.1 What the partner sees

```
┌─────────────────────────────────────────────────────────┐
│  ◆ LoomAI Partners                   ● Agency Name  ▾   │
│                                                         │
│  OVERVIEW                                               │
│  ◇ Dashboard                                            │
│  ◇ Client Stores                                        │
│                                                         │
│  IMPLEMENT                                              │
│  ◇ New Client Store                                     │
│  ◇ Intelligence Catalog                                 │
│  ◇ Templates                                            │
│  ◇ Verification Packs                                   │
│                                                         │
│  SUPPORT                                                │
│  ◇ Support Center                                       │
│  ◇ Escalations                                          │
│  ◇ Documentation                                        │
└─────────────────────────────────────────────────────────┘
```

Total: 9 items in 4 groups.

### 5.2 Partner pages

**Dashboard**

What it shows:
- assigned client stores
- implementations in progress
- stores needing attention
- verification failures
- support escalations
- recent activity across all client stores
- quick stats: stores launched, stores waiting on merchant action, surfaces enabled

Design:
- client-store health summary with green/amber/red counts
- implementation progress summary
- activity feed showing recent events across all stores

---

**Client Stores**

What it shows:
- table of assigned client stores:
  - store name
  - client owner
  - plan (Free / Starter / Elite)
  - implementation status (not started / in progress / ready / launched / needs attention)
  - Knowledge Sync health (healthy / warning / error)
  - active intelligence surfaces
  - last verification result
  - next required action
- click into any client store to see the implementation workspace
- filter by status, plan, health, surface, and blocker

Design:
- clean table with status dots
- click-through to client store workspace
- bulk actions: "Run verification", "Check Knowledge Sync", "Export launch packet"

---

**New Client Store**

What it shows:
- step-by-step implementation flow:
  1. enter Shopify store URL
  2. merchant authorizes the app
  3. choose configuration template
  4. trigger initial sync
  5. configure intelligence surfaces by tier
  6. verify on storefront
  7. export launch packet
- estimated time: 30-45 minutes
- each step has instructions and validation

What it does NOT show:
- provider selection (uses platform defaults)
- vector database configuration
- runtime settings

Design:
- wizard flow (step 1 of 7, step 2 of 7, etc.)
- progress bar at top
- each step validates before proceeding
- final step shows live storefront preview

---

**Intelligence Catalog**

What it shows:
- the reusable intelligence pieces partners can implement:
  - AI search
  - product insight block
  - product FAQ
  - comparison
  - policy strip
  - contextual pill
  - read-only depth layer
- plan availability:
  - Free: AI search only
  - Starter: full read-only embedded intelligence
  - Elite: verified governed actions only
- placement guidance
- source requirements
- expected merchant value
- setup and verification checklist per surface

What it does NOT show:
- provider configuration
- vector store internals
- raw runtime settings
- unverified Elite action claims

Design:
- card per intelligence piece
- plan badges
- setup checklist
- "Add to client setup" action

---

**Templates**

What it shows:
- pre-built configuration templates by store type:
  - Fashion / Apparel (high product count, visual, size/color variants)
  - Electronics (specs-heavy, comparison-oriented)
  - Health / Beauty (ingredient-focused, regulatory)
  - Home / Furniture (room/style categorization)
  - General Merchandise (balanced defaults)
- each template includes: intelligence surface defaults, appearance preset, knowledge source priorities
- "Create custom template" option

Design:
- card per template with description and preview
- one-click apply during client setup
- partners can save their own custom templates

---

**Verification Packs**

What it shows:
- reusable verification packs for launch and support:
  - Free AI search check
  - Starter embedded surfaces check
  - Knowledge Sync readiness check
  - App Store screenshot/readiness check
  - support handoff check
  - Elite governed-action check only when verified
- last run result
- failed checks
- remediation notes
- exportable launch/review packet

Design:
- checklist-first layout
- status dots and blockers
- export button for merchant/client handoff

---

**Support Center**

What it shows:
- client support notes
- common setup blockers
- install recovery steps
- Knowledge Sync issue guidance
- launch packet exports
- support evidence that can be escalated to the operator

---

**Escalations**

What it shows:
- open partner escalations
- affected client store
- blocker type
- evidence attached by the partner
- operator response/status
- resolution notes

Design:
- compact queue
- priority/status filters
- clear "what we need from you" messaging

---

### 5.3 Partner language

Partners see a mix of product language and light technical language:

| Platform term | Partner term |
|---|---|
| Deployment | Client Store Setup |
| Vectorization | Knowledge Sync |
| Intelligence surfaces | Intelligence Pieces |
| Provider | — (hidden, uses defaults) |
| Confirmation interception | Write Action Governance |
| Environment | Environment (dev/prod) |
| Action catalog | Action Catalog |
| Diagnostics | Health Check |

Partners understand setup, configuration, verification, and support evidence. They do not need provider selection, vectorization strategy, embedding dimensions, or commercial attribution tooling.

---

## 6) Operator Control Plane — admin.loomai.pro

### 6.1 What the operator sees

```
┌─────────────────────────────────────────────────────────┐
│  ◆ LoomAI Admin                      ● Operator    ▾    │
│                                                         │
│  ┌─────────────────────┐                                │
│  │ ▼ All deployments   │                                │
│  └─────────────────────┘                                │
│                                                         │
│  WORKSPACE                                              │
│  ◇ Dashboard                                            │
│  ◇ Deployments                                          │
│                                                         │
│  CONFIGURE                                              │
│  ◇ Providers                                            │
│  ◇ Prompts                                              │
│  ◇ Actions                                              │
│  ◇ Knowledge                                            │
│  ◇ Security                                             │
│                                                         │
│  OPERATE                                                │
│  ◇ Approvals                                            │
│  ◇ Diagnostics                                          │
│  ◇ Vectorization                                        │
│  ◇ Verification                                         │
│  ◇ Revisions                                            │
│                                                         │
│  PLATFORM                                               │
│  ◇ User Access                                          │
│  ◇ Customers                                            │
│  ◇ Platform Health                                      │
│  ◇ Notifications                                        │
│  ◇ Partner Management                                   │
│                                                         │
│  ─────────────────────────                              │
│  ◇ Settings                                             │
│  ◇ Sign out                                             │
└─────────────────────────────────────────────────────────┘
```

Total: 16 items in 4 groups (same count as before, but grouped and labeled).

### 6.2 Operator-only pages

Everything from the UI redesign document, plus:

**Partner Management** (new)

What it shows:
- all registered partners
- per-partner: assigned client stores, implementation activity, health, escalations
- partner approval/rejection
- scoped store assignment
- sandbox/demo access
- implementation status
- verification history
- partner communication log

**Platform Health** (replaces Platform Diagnostics)

What it shows:
- all services health (API, runtime, sync, CDN)
- error rates and latency
- resource usage
- recent incidents

### 6.3 Operator language

Full technical language. Deployments, vectorization, providers, embedding dimensions, runtime profiles — everything uses the real platform terms.

---

## 7) Access Control Model

### 7.1 Role definitions

```
MERCHANT        can manage their own store only
PARTNER         can manage assigned client stores within scoped implementation permissions + own partner workspace
OPERATOR        can manage everything
```

### 7.2 How roles are assigned

- Merchant: created automatically when a store installs from Shopify App Store
- Partner: created when partner application is approved
- Operator: manually assigned, platform admin only

### 7.3 What each role can access

| Surface | Merchant | Partner | Operator |
|---|---|---|---|
| app.loomai.pro | full access (own store) | — | — |
| partners.loomai.pro | — | full access | full access |
| admin.loomai.pro | — | — | full access |
| docs.loomai.pro | full access | full access | full access |
| API (own store) | read + configure | read/configure assigned client stores within scope | full access |
| API (all stores) | — | — | full access |
| Provider config | — | — | full access |
| Billing (own) | full access | view assigned client store plan/status where authorized | full access |
| Partner assignments | — | assigned stores only | full access |

### 7.4 Cross-portal access

- Operator can impersonate a merchant view (see what the merchant sees for debugging)
- Operator can impersonate a partner view (see what the partner sees)
- Partner can view and support assigned client stores within scoped permissions
- Merchant cannot access partner or operator views

---

## 8) Shared vs. Separate Codebase

### 8.1 Option A: Three separate frontend apps

```
app.loomai.pro           → merchant-dashboard/ (React + Tailwind)
partners.loomai.pro      → partner-portal/ (React + Tailwind)
admin.loomai.pro         → admin-ui/ (React + Tailwind, current platform UI evolved)
```

Pros:
- cleanest separation
- each app is focused and small
- independent deployment
- no risk of leaking admin UI to merchants

Cons:
- three apps to maintain
- shared components need a design system package
- more build infrastructure

### 8.2 Option B: One app with role-based routing

```
app.loomai.pro           → single React app
                            /merchant/*    (merchant routes)
                            /partner/*     (partner routes)
                            /admin/*       (operator routes)
```

Pros:
- one codebase
- shared components by default
- simpler deployment

Cons:
- risk of complexity bleed between personas
- larger bundle for merchants who only need 6 pages
- harder to maintain strict separation

### 8.3 Recommendation

**Option A: three separate apps sharing a design system package.**

The audiences are different enough that forcing them into one app creates the same problem we have now — one UI trying to serve everyone.

The shared design system package provides:
- color tokens
- typography
- button, card, input, table components
- status indicators
- layout primitives (sidebar, top bar, content area)

Each app imports the design system and builds its own pages. The merchant dashboard stays light. The operator control plane stays deep.

---

## 9) Shared Design System Package

### 9.1 Package structure

```
@loomai/design-system/
├── tokens/
│   ├── colors.ts
│   ├── typography.ts
│   ├── spacing.ts
│   └── shadows.ts
├── components/
│   ├── Button.tsx
│   ├── Card.tsx
│   ├── Input.tsx
│   ├── Table.tsx
│   ├── StatusDot.tsx
│   ├── Sidebar.tsx
│   ├── TopBar.tsx
│   ├── Modal.tsx
│   ├── Badge.tsx
│   └── Toggle.tsx
├── layouts/
│   ├── DashboardLayout.tsx
│   └── WizardLayout.tsx
└── index.ts
```

### 9.2 Tokens

All three apps use the same color palette, typography, and spacing from the UI redesign document. This ensures visual consistency across subdomains.

### 9.3 Component API

Each component is a styled Tailwind component with variant props:

```tsx
<Button variant="primary">Save</Button>
<Button variant="secondary">Cancel</Button>
<Button variant="destructive">Delete</Button>

<StatusDot status="healthy" label="Synced" />
<StatusDot status="warning" label="Syncing" />
<StatusDot status="error" label="Failed" />

<Card>
  <Card.Header>Knowledge Sources</Card.Header>
  <Card.Content>...</Card.Content>
</Card>
```

Same components, different pages, different apps, same visual language.

---

## 10) Shopify Embedded App Context

The merchant dashboard has a special case: it also runs inside the Shopify admin as an embedded app.

### 10.1 Two entry points for merchants

1. **Inside Shopify admin** — merchant clicks "Loom Companion" in their Shopify app list. The dashboard loads inside a Shopify admin iframe using Shopify App Bridge.

2. **Direct access** — merchant visits app.loomai.pro directly. Same dashboard, standalone mode.

### 10.2 What changes in embedded mode

- Shopify App Bridge provides the top bar (navigation, breadcrumbs)
- the sidebar may be hidden or collapsed (Shopify admin already has navigation)
- authentication uses Shopify session token instead of direct login
- "Plan and Billing" redirects to Shopify's billing approval flow

### 10.3 What stays the same

- all page content
- all intelligence surface configuration
- all knowledge management
- all appearance settings
- the design system components

### 10.4 Implementation

Use a layout flag:

```tsx
<DashboardLayout mode="embedded" />    // inside Shopify admin
<DashboardLayout mode="standalone" />  // direct access at app.loomai.pro
```

Embedded mode hides the sidebar and uses Shopify App Bridge for navigation. Standalone mode shows the full sidebar.

---

## 11) Implementation Priority

### Phase 1: Merchant Dashboard (Week 1-3)

Build app.loomai.pro with 6 pages:
- Dashboard
- Intelligence Surfaces
- Appearance
- Knowledge
- Plan and Billing
- Help

This is what merchants see after installing from the Shopify App Store. It must be ready before launch.

### Phase 2: Operator Control Plane Redesign (Week 2-4)

Apply the UI redesign document to the existing platform UI:
- dark theme
- grouped navigation
- new dashboard
- progressive disclosure on heavy pages

Move to admin.loomai.pro.

### Phase 3: Implementation Partner Workspace (after Starter package is coherent)

Build partners.loomai.pro around implementation support:
- Dashboard
- Client Stores
- New Client Store
- Intelligence Catalog
- Templates
- Verification Packs
- Support Center
- Escalations
- Documentation

This is needed when the first founding implementation partners are ready to deploy and support real client stores.

### Phase 4: Design System Extraction (Week 6-8)

Extract shared components from the merchant dashboard and operator control plane into @loomai/design-system. Apply to the implementation partner workspace.

---

## 12) Success Criteria

The persona separation is working when:

- a merchant can configure their AI features without ever seeing the word "vectorization"
- an implementation partner can deploy and verify a new client store in under 45 minutes using the wizard flow
- the operator can debug a provider connection without navigating through merchant-level screens
- moving between app.loomai.pro, partners.loomai.pro, and admin.loomai.pro feels like the same product family but each feels designed for its audience
