# Platform UI Redesign Direction

Status: planning document (2026-04-21)

This document evaluates the current platform UI, identifies what does not work, and defines the new design direction for the LoomAI Labs control plane.

---

## 1) Current UI — What Is Wrong

### 1.1 Navigation is a flat wall

17 nav items in one flat list. No grouping, no sections, no hierarchy. A new user sees: Deployments, Overview, Actions, Approvals, Customers, Knowledge, POC, Prompts, Providers, Security, Verification, Vectorization, Revisions, Diagnostics, Notifications, Platform Diagnostics, User Access — all at the same visual level.

Nobody knows where to start. Nobody knows what matters.

### 1.2 Identity is confusing

The sidebar says "AI Enablement Control Plane" with a "Phase 21 Public API" chip. The top bar says "Configurable AI Enablement Platform" with a subtitle about immutable runtimes.

This is internal engineering language, not product language. A merchant or partner seeing this would not know what they are looking at.

### 1.3 Pages are monolithic

ProvidersPage.tsx is 3,399 lines. DeploymentsPage.tsx is 3,282 lines. These are not pages — they are entire applications crammed into single components. The form state in ProvidersPage alone has 80+ fields — every LLM provider, every vector database, every configuration option, all on one page.

No progressive disclosure. Everything is visible at once.

### 1.4 The theme is safe but flat

- Primary blue #3b82c4 — functional but forgettable
- Background #eef3f8 — washed-out blue-gray
- Cards have a barely-visible white gradient that blends into the background
- The dark sidebar gradient feels disconnected from the light content area
- Secondary color #d46c4a is defined but barely used
- Overall impression: generic enterprise dashboard from a template

### 1.5 Information density without hierarchy

MUI cards, chips, alerts, dividers, grids — all used correctly but without visual priority. Everything is equally prominent. Status chips, role badges, auth mode indicators, sign-out button — all in a row in the top bar, all the same visual weight.

### 1.6 No personality

Nothing about this UI says "AI product lab." It could be any admin panel for any SaaS. There is no brand expression, no visual signature, nothing memorable.

---

## 2) Brand Foundation

Name: LoomAI Labs

Domain: loomai.pro

Metaphor: the loom — threads woven into fabric, configuration woven into products

Personality: focused builder, clean workshop, precise tools

---

## 3) Color System

### 3.1 Background Scale

```
Deep:       #0B0F1A     near-black with blue undertone — primary background
Surface:    #141B2D     card and panel background
Elevated:   #1C2540     hover states, active panels
Border:     #2A3352     subtle separation lines
```

### 3.2 Accent Colors

```
Primary:    #6C8EEF     calm blue — interactive elements, links, active states
Glow:       #8BABFF     hover states, focus rings
Subtle:     rgba(108, 142, 239, 0.12)     selected backgrounds
```

### 3.3 Status Colors

```
Healthy:    #4ADE80     green — synced, active, connected
Warning:    #FBBF24     amber — needs attention
Critical:   #F87171     red — errors, disconnected
Neutral:    #94A3B8     gray — inactive, disabled
```

### 3.4 Text Colors

```
Primary:    #E2E8F0     high contrast on dark
Secondary:  #94A3B8     descriptions, labels
Muted:      #64748B     timestamps, metadata
On-accent:  #FFFFFF     text on primary buttons
```

### 3.5 Why dark-first

This is a builder's tool. Dark mode reduces eye strain for long sessions. It also makes status colors pop — green means healthy, red means broken, you see it instantly.

---

## 4) Typography

```
Headings:    Inter (weight 600-700)
Body:        Inter (weight 400-500)
Mono:        JetBrains Mono (config values, IDs, endpoints, model names)
```

No decorative fonts. Inter is clean, readable at all sizes, and free. JetBrains Mono for anything that looks like code or configuration.

---

## 5) Navigation Redesign

### 5.1 Before (17 flat items)

```
Deployments
Overview
Actions
Approvals
Customers
Knowledge
POC
Prompts
Providers
Security
Verification
Vectorization
Revisions
Diagnostics
Notifications
Platform Diagnostics
User Access
```

### 5.2 After (grouped into 4 sections)

```
WORKSPACE
  Dashboard              merged Overview + high-level stats
  Deployments            the main entity

CONFIGURE
  Providers              LLM, embedding, vector
  Prompts                prompt management
  Actions                action catalog
  Knowledge              knowledge sources
  Security               policies, secrets

OPERATE
  Approvals              confirmation interception
  Diagnostics            runtime health
  Vectorization          index status
  Verification           test and validate
  Revisions              change history

ADMIN                    collapsed by default, visible to admins only
  User Access
  Customers
  Platform Health
  Notifications
```

4 groups with clear labels. Operators find what they need in 2 seconds, not 10.

---

## 6) Sidebar Layout

```
┌─────────────────────────────┐
│  ◆ LoomAI                   │
│    loomai.pro                │
│                              │
│  ┌─────────────────────┐    │
│  │ ▼ acme-store  prod  │    │
│  └─────────────────────┘    │
│                              │
│  WORKSPACE                   │
│  ◇ Dashboard                 │
│  ◆ Deployments               │
│                              │
│  CONFIGURE                   │
│  ◇ Providers                 │
│  ◇ Prompts                   │
│  ◇ Actions                   │
│  ◇ Knowledge                 │
│  ◇ Security                  │
│                              │
│  OPERATE                     │
│  ◇ Approvals                 │
│  ◇ Diagnostics               │
│  ◇ Vectorization             │
│  ◇ Verification              │
│  ◇ Revisions                 │
│                              │
│  ▶ ADMIN                     │
│                              │
│  ─────────────────────────   │
│  ◇ Settings                  │
│  ◇ Sign out                  │
└─────────────────────────────┘
```

Key changes:

- deployment/store selector at the top so context is always visible
- section labels in muted uppercase
- active item has accent left-border and subtle background
- admin section is collapsible
- sign out moved to bottom, out of the top bar

---

## 7) Top Bar Redesign

### 7.1 Before

"Configurable AI Enablement Platform" + role chip + name chip + auth chip + sign out button — all in one row.

### 7.2 After

```
┌──────────────────────────────────────────────────────────┐
│  Dashboard                              ● Mahmoud   ▾    │
│  Store health, deployment status                         │
└──────────────────────────────────────────────────────────┘
```

- page title (dynamic, matches current page)
- page subtitle (one-line description)
- user avatar/name in top-right with dropdown for role info and sign out
- no chips, no engineering jargon, no "Phase 21"

---

## 8) Dashboard Page (New)

Replace the current Overview page with a real dashboard.

```
┌────────────────────────────────────────────────────────┐
│                                                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐│
│  │ 3 Active │  │ 12.4K    │  │ 847      │  │ 99.2%  ││
│  │ Deploys  │  │ Queries  │  │ Actions   │  │ Uptime ││
│  │          │  │ this week│  │ this week │  │        ││
│  └──────────┘  └──────────┘  └──────────┘  └────────┘│
│                                                        │
│  ┌─────────────────────────┐  ┌───────────────────────┐│
│  │ Deployment Health       │  │ Recent Activity       ││
│  │                         │  │                       ││
│  │ ● acme-prod    healthy  │  │ 10:32  Query spike    ││
│  │ ● acme-dev     syncing  │  │ 10:15  Sync complete  ││
│  │ ● demo-store   healthy  │  │ 09:48  Config updated ││
│  │                         │  │                       ││
│  └─────────────────────────┘  └───────────────────────┘│
│                                                        │
│  ┌─────────────────────────────────────────────────────┐│
│  │ Knowledge Sources                                   ││
│  │                                                     ││
│  │ Products    847/847 indexed    ● synced   2h ago    ││
│  │ Pages       23/23 indexed      ● synced   2h ago    ││
│  │ Policies    8/8 indexed        ● synced   2h ago    ││
│  │ Collections 15/15 indexed      ● synced   2h ago    ││
│  └─────────────────────────────────────────────────────┘│
│                                                        │
└────────────────────────────────────────────────────────┘
```

Glanceable. Open the dashboard and in 3 seconds you know: everything is healthy, queries are flowing, knowledge is synced.

---

## 9) Providers Page Redesign

### 9.1 Before

80+ form fields all visible on one page. Every LLM provider, every vector database, every setting exposed simultaneously.

### 9.2 After

Progressive disclosure with tabs and sections.

```
┌────────────────────────────────────────────────────────┐
│  Providers                                             │
│                                                        │
│  ┌──────────────────────────────────────────┐          │
│  │ LLM Provider          [OpenAI ▾]        │          │
│  │ Embedding Provider     [OpenAI ▾]        │          │
│  │ Vector Storage         [Qdrant ▾]        │          │
│  └──────────────────────────────────────────┘          │
│                                                        │
│  ┌────────┐┌──────────┐┌────────┐┌─────────┐          │
│  │ OpenAI ││ Anthropic ││ Azure  ││ Cohere  │          │
│  └────────┘└──────────┘└────────┘└─────────┘          │
│                                                        │
│  ┌──────────────────────────────────────────┐          │
│  │ OpenAI Configuration                     │          │
│  │                                          │          │
│  │ Model         gpt-4o-mini                │          │
│  │ Embedding     text-embedding-3-small     │          │
│  │ Dimensions    1536                       │          │
│  │                                          │          │
│  │ ▶ Advanced (timeout, priority, base URL) │          │
│  └──────────────────────────────────────────┘          │
│                                                        │
│  ┌──────────────────────────────────────────┐          │
│  │ ● Connection healthy   Last check: 2m   │          │
│  └──────────────────────────────────────────┘          │
│                                                        │
│  [Save Configuration]                                  │
│                                                        │
└────────────────────────────────────────────────────────┘
```

Only the active provider's settings are visible. Advanced settings are collapsed. Connection health is shown inline. The page goes from 80 visible fields to 5-8.

---

## 10) Card Design

### 10.1 Before

White cards with barely-visible gradient on light gray background. Cards blend into the page.

### 10.2 After

```css
background: #141B2D;
border: 1px solid #2A3352;
border-radius: 12px;
box-shadow: 0 0 0 1px rgba(108, 142, 239, 0.08) inset;
```

Cards are distinct from the background. Border gives structure. Hover adds a subtle accent glow. No gradients — solid colors with clear boundaries.

---

## 11) Status Indicators

### 11.1 Before

Chips and text labels with inconsistent coloring.

### 11.2 After

Colored dots with one-word status:

```
● healthy     green dot + text
● syncing     amber dot + text, subtle pulse animation
● error       red dot + text
○ inactive    gray ring + text
```

Visible from across the room. No reading required.

---

## 12) Component Patterns

### 12.1 Buttons

- Primary: solid accent blue (#6C8EEF), white text, 8px border-radius
- Secondary: transparent with accent border
- Destructive: transparent with red border, red text
- No gradients, no shadows, no rounded-pill shapes

### 12.2 Inputs

- Dark background (#1C2540)
- Subtle border (#2A3352)
- Focus: accent border glow
- Labels above, not inside
- Mono font for values that look like config (URLs, model names, IDs)

### 12.3 Tables

- No zebra striping
- Subtle row borders (#2A3352)
- Header row in muted text (#64748B)
- Hover highlights with accent background at 8% opacity

### 12.4 Modals and Dialogs

- Centered overlay on darkened backdrop
- Same card styling (dark surface, border)
- Clear title, content, action buttons
- Always closable with Escape and backdrop click

---

## 13) Micro-Interactions

Keep these minimal but present:

- page transitions: subtle fade (150ms)
- status changes: color transitions (300ms)
- syncing indicator: gentle pulse on the amber dot
- save confirmation: brief green flash on the save button
- sidebar active item: accent border slides in (200ms)

No bouncing, no spinning, no complex animations. This is a control plane, not a marketing site.

---

## 14) The Overall Feel

### 14.1 Before

"Enterprise admin dashboard template with Material UI defaults."

### 14.2 After

"A builder's workshop — dark, focused, every tool in its place, nothing wasted."

Think:

- Linear (issue tracker) — clean, dark, keyboard-first
- Vercel dashboard — minimal, status-driven
- Raycast — focused, fast, no clutter

Not:

- Salesforce — cluttered, overwhelming
- AWS Console — dense, confusing
- Generic MUI template — forgettable

---

## 15) Migration Approach

Do not rewrite everything at once. Migrate in layers.

### Step 1: Theme

Switch to dark palette, update typography, update colors. This changes the entire feel without touching component structure.

Estimated effort: 1-2 days.

### Step 2: Navigation

Group the sidebar, add deployment selector, clean up the top bar. This fixes the biggest UX problem.

Estimated effort: 2-3 days.

### Step 3: Dashboard

Build the new Dashboard page to replace Overview. This gives an immediate impact.

Estimated effort: 2-3 days.

### Step 4: Progressive Disclosure

Refactor ProvidersPage and DeploymentsPage to use tabs, collapsible sections, and contextual visibility. This is the most work but the biggest usability improvement.

Estimated effort: 5-7 days.

### Step 5: Component Refinement

Standardize cards, buttons, inputs, tables across all pages.

Estimated effort: 3-4 days.

Each step ships independently. Each step makes the product better.

---

## 16) Technology Decision

### 16.1 Option A: Stay with MUI, retheme heavily

Pros:

- no migration cost
- MUI supports dark themes natively
- existing components keep working

Cons:

- MUI defaults are hard to fully escape
- heavy dependency (large bundle)
- custom styling fights the framework

### 16.2 Option B: Migrate to Tailwind + headless components

Pros:

- full design control
- lighter bundle
- matches the max-mode-widget approach (already uses Tailwind)
- consistent tech across platform UI and widget

Cons:

- migration cost for existing pages
- need to rebuild or adopt headless components (Radix, Headless UI)

### 16.3 Recommendation

Start with Option A (retheme MUI) for Steps 1-3. This gets the visual impact fast.

Evaluate Option B for Steps 4-5 once the design direction is validated. If the product grows and the widget and platform UI need to share a design system, Tailwind becomes the right long-term choice.

Do not let the technology decision block the visual improvement.
