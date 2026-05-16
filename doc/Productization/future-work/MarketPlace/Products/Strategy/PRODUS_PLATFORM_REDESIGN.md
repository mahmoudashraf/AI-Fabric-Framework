# ProdUS Platform - Full Redesign

## Current State Problems

The existing ProdUS owner dashboard puts everything on a single page (`/owner/project-cart`):

- Product creation, product list, package builder, team matching, workspace, and delivery tracking all in one scroll
- No dedicated routes for distinct workflows
- No clear entry points for primary actions (e.g., "Create New Product" has no standalone page)
- Information overload — owner sees everything regardless of what they came to do
- No progressive disclosure — new users see the same complexity as power users
- No visual hierarchy between active work and completed/archived items

The technical foundation is sound: Next.js App Router, MUI components, React Query, auth/notification providers, and a dashboard layout shell are all in place. The problem is purely information architecture and page composition.

---

## Design Principles

1. **One job per page** — Each route answers one question: "What are my products?" or "Create a new product" or "What's happening in this workspace?" Never all at once.

2. **Progressive disclosure** — Show summary first, detail on demand. The dashboard is a launchpad, not a warehouse.

3. **Action-oriented navigation** — Every page has a clear primary action. Product list → "Add Product." Package page → "Build Package." Team page → "Request Match."

4. **Evidence over opinion** — Status indicators show measured progress (4/7 criteria met), not subjective labels ("going well").

5. **Workspace isolation** — Once an owner is inside an active project, they're in a focused workspace. Global navigation remains accessible but doesn't compete.

6. **MUI-native patterns** — Every layout uses standard MUI components. No custom CSS frameworks. Consistency with Material Design 3 spacing, elevation, and interaction patterns.

---

## Information Architecture

### Route Structure

```
app/(dashboard)/
├── dashboard/page.tsx              → Home: summary cards, recent activity, quick actions
├── products/
│   ├── page.tsx                    → Product list (grid/table of all products)
│   ├── new/page.tsx                → Create product wizard (multi-step)
│   └── [id]/
│       ├── page.tsx                → Product detail (health, status, history)
│       ├── diagnosis/page.tsx      → AI diagnosis results and evidence
│       └── settings/page.tsx       → Product settings and integrations
├── packages/
│   ├── page.tsx                    → Package list (active, draft, completed)
│   ├── new/page.tsx                → Package builder wizard
│   └── [id]/
│       ├── page.tsx                → Package detail (milestones, deliverables)
│       └── review/page.tsx         → Milestone review with evidence
├── teams/
│   ├── page.tsx                    → Team discovery and recommendations
│   └── [id]/page.tsx              → Team profile and portfolio
├── workspaces/
│   ├── page.tsx                    → Active workspaces list
│   └── [id]/
│       ├── page.tsx                → Workspace overview (progress, activity)
│       ├── milestones/page.tsx     → Milestone tracking and submissions
│       ├── messages/page.tsx       → Communication thread
│       └── handoff/page.tsx        → Handoff readiness and checklist
├── billing/page.tsx                → Payment history, active subscriptions
└── settings/page.tsx               → Account settings, notifications, integrations
```

### Navigation Menu

**Primary (Sidebar)**
- Dashboard (home icon)
- Products (box icon)
- Packages (layers icon)
- Teams (people icon)
- Workspaces (folder icon)

**Secondary (bottom of sidebar)**
- Billing
- Settings
- Help

**Contextual (breadcrumb + tabs within pages)**
- Product detail: Overview | Diagnosis | Settings
- Package detail: Scope | Milestones | Deliverables
- Workspace: Overview | Milestones | Messages | Handoff

---

## Page-by-Page Design

### Dashboard Home (`/dashboard`)

**Purpose:** Launchpad. Answer "what needs my attention?" in 5 seconds.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  Welcome back, [Name]                    [+ New Product] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
│  │ Products │  │ Active  │  │ Pending │  │ Spend   │  │
│  │    3     │  │ Packages│  │ Reviews │  │ This Mo │  │
│  │          │  │    2    │  │    1    │  │  $2,400 │  │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘  │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Needs Attention                                  │   │
│  │ • Milestone 2 submitted for "Auth Refactor"     │   │
│  │ • Team match ready for "Mobile App"             │   │
│  │ • Diagnosis complete for "API Gateway"          │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
│  ┌─────────────────────────────────────────────────┐   │
│  │ Recent Activity                                  │   │
│  │ 2h ago   Team DevCraft submitted milestone 2    │   │
│  │ 1d ago   Package "Security Hardening" approved  │   │
│  │ 2d ago   New product "API Gateway" created      │   │
│  └─────────────────────────────────────────────────┘   │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**MUI Components:**
- `Grid` container with 4 summary `Card` components (elevation 1, colored top border per type)
- `List` with `ListItem`, `ListItemIcon`, `ListItemText` for activity
- `Alert` severity variants for attention items
- `Button` variant="contained" for primary CTA
- `Typography` variant="h4" for greeting, "h6" for section titles

---

### Products List (`/products`)

**Purpose:** See all products, their health status, and take action.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  Products                                [+ New Product] │
├─────────────────────────────────────────────────────────┤
│  [All] [Active] [Needs Work] [Completed]    [Search...] │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌──────────────────────────┐ ┌──────────────────────┐ │
│  │ Auth Refactor            │ │ Mobile App           │ │
│  │ ●━━━━━━━━━━━━━━━○ 68%   │ │ ●━━━━━○ 30%         │ │
│  │                          │ │                      │ │
│  │ Stack: Node, React       │ │ Stack: React Native  │ │
│  │ Package: In Progress     │ │ Package: Matching    │ │
│  │ Team: DevCraft           │ │ Team: Pending        │ │
│  │                          │ │                      │ │
│  │ [View] [Workspace →]    │ │ [View] [Find Team]   │ │
│  └──────────────────────────┘ └──────────────────────┘ │
│                                                         │
│  ┌──────────────────────────┐ ┌──────────────────────┐ │
│  │ API Gateway              │ │ + Add New Product    │ │
│  │ ○ Not started            │ │                      │ │
│  │                          │ │ Connect a repo, URL, │ │
│  │ Stack: Go, gRPC          │ │ or describe your     │ │
│  │ Package: Not created     │ │ product to begin.    │ │
│  │ Team: —                  │ │                      │ │
│  │                          │ │ [+ Create]           │ │
│  │ [View] [Run Diagnosis]   │ │                      │ │
│  └──────────────────────────┘ └──────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**MUI Components:**
- `Tabs` for filter states (All, Active, Needs Work, Completed)
- `TextField` with search icon for filtering
- `Card` grid (responsive: 1 col mobile, 2 col tablet, 3 col desktop)
- `LinearProgress` with value label for health score
- `Chip` for stack tags and status
- `CardActions` with `Button` for contextual actions
- Empty state card with dashed border and CTA

---

### Create Product Wizard (`/products/new`)

**Purpose:** Guided product creation in clear steps. Replace the "dump everything in one form" pattern.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  ← Back to Products          Create New Product         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │  ① Connect  →  ② Describe  →  ③ Review  →  Done  │ │
│  │  ●━━━━━━━━━━━━●━━━━━━━━━━━━○━━━━━━━━━━━━○        │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │                                                   │ │
│  │  Step 2: Describe your product                    │ │
│  │                                                   │ │
│  │  Product name                                     │ │
│  │  ┌─────────────────────────────────────────────┐ │ │
│  │  │ My SaaS Platform                            │ │ │
│  │  └─────────────────────────────────────────────┘ │ │
│  │                                                   │ │
│  │  What stage is it in?                             │ │
│  │  ○ Idea / Pre-build                              │ │
│  │  ● Built but not production-ready                │ │
│  │  ○ Running in production                         │ │
│  │  ○ Production but needs improvement              │ │
│  │                                                   │ │
│  │  What's your primary goal?                        │ │
│  │  ┌─────────────────────────────────────────────┐ │ │
│  │  │ Get to production safely                    │ │ │
│  │  └─────────────────────────────────────────────┘ │ │
│  │                                                   │ │
│  │                          [Back]  [Continue →]     │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Wizard Steps:**
1. **Connect** — Repo URL, live URL, or "describe manually"
2. **Describe** — Name, stage, primary goal, tech stack (auto-detected if repo connected)
3. **Review** — Summary of what was entered, option to run AI diagnosis immediately
4. **Done** — Confirmation with next steps (run diagnosis, browse services, or return to list)

**MUI Components:**
- `Stepper` (horizontal, with `StepLabel`)
- `TextField`, `RadioGroup`, `Autocomplete` for inputs
- `Paper` elevation 2 for the step content area
- `Button` group for navigation (Back / Continue)
- `Alert` for validation messages
- `Skeleton` loading states during AI detection

---

### Product Detail (`/products/[id]`)

**Purpose:** Everything about one product. Health, history, connected packages, assigned teams.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  ← Products    Auth Refactor                [Settings ⚙]│
├─────────────────────────────────────────────────────────┤
│  [Overview]  [Diagnosis]  [Packages]  [Activity]        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌─────────────────────┐  ┌──────────────────────────┐ │
│  │ Health Score         │  │ Quick Info               │ │
│  │                      │  │                          │ │
│  │   ████████░░ 72/100  │  │ Stack: Node, React, AWS │ │
│  │                      │  │ Stage: Production        │ │
│  │ ✓ CI/CD configured   │  │ Created: 12 Mar 2026    │ │
│  │ ✓ Monitoring active  │  │ Last diagnosis: 2d ago  │ │
│  │ ✗ No load testing    │  │ Team: DevCraft          │ │
│  │ ✗ Docs incomplete    │  │ Package: In Progress    │ │
│  │                      │  │                          │ │
│  │ [Run New Diagnosis]  │  │ [Go to Workspace →]     │ │
│  └─────────────────────┘  └──────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Active Package                                    │ │
│  │                                                   │ │
│  │ "Security Hardening + CI/CD"    $4,200 / 6 weeks  │ │
│  │                                                   │ │
│  │ Milestone 1: CI Pipeline        ✓ Complete        │ │
│  │ Milestone 2: Security Audit     ◷ In Review       │ │
│  │ Milestone 3: Hardening          ○ Not Started     │ │
│  │ Milestone 4: Documentation      ○ Not Started     │ │
│  │                                                   │ │
│  │ [View Full Package →]                             │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**MUI Components:**
- `Tabs` for sub-navigation (Overview, Diagnosis, Packages, Activity)
- `CircularProgress` or custom gauge for health score
- `List` with `ListItemIcon` (check/cross) for criteria
- `Card` with `CardHeader` and `CardContent` for sections
- `Chip` for stack tags
- `Timeline` (MUI Lab) for activity tab
- `Stepper` (vertical) for milestone progress

---

### Package Builder (`/packages/new`)

**Purpose:** Build a service package with milestones, budget, and acceptance criteria.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  ← Packages              Build Package                  │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ ① Services → ② Milestones → ③ Budget → ④ Review  │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Step 1: Select Services                           │ │
│  │                                                   │ │
│  │ Based on your diagnosis, we recommend:            │ │
│  │                                                   │ │
│  │ ┌─────────────────────────────────────────────┐  │ │
│  │ │ ☑ CI/CD Pipeline Setup         ★ Recommended│  │ │
│  │ │   Automated build, test, deploy pipeline    │  │ │
│  │ │   Depends on: — (none)                      │  │ │
│  │ └─────────────────────────────────────────────┘  │ │
│  │ ┌─────────────────────────────────────────────┐  │ │
│  │ │ ☑ Security Hardening           ★ Recommended│  │ │
│  │ │   Dependency audit, auth review, secrets    │  │ │
│  │ │   Depends on: CI/CD (for automated scans)   │  │ │
│  │ └─────────────────────────────────────────────┘  │ │
│  │ ┌─────────────────────────────────────────────┐  │ │
│  │ │ ☐ Monitoring & Alerting                     │  │ │
│  │ │   APM, error tracking, uptime monitoring    │  │ │
│  │ │   Depends on: Deployment (already met)      │  │ │
│  │ └─────────────────────────────────────────────┘  │ │
│  │                                                   │ │
│  │ ⚠ Removing CI/CD will break the dependency for   │ │
│  │   Security Hardening (automated scans).           │ │
│  │                                                   │ │
│  │                           [Back]  [Continue →]    │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**Wizard Steps:**
1. **Services** — Select from recommended + full catalog, dependency warnings shown inline
2. **Milestones** — Auto-generated from service templates, owner can adjust order and criteria
3. **Budget** — AI-suggested range based on scope, owner sets budget and timeline preference
4. **Review** — Full package summary with governance check results before confirming

**MUI Components:**
- `Stepper` horizontal
- `Card` with `Checkbox` for service selection
- `Chip` label="Recommended" color="success"
- `Alert` severity="warning" for dependency conflicts
- `Accordion` for milestone editing (expand to see deliverables and acceptance criteria)
- `Slider` or `TextField` for budget range
- `Divider` between sections

---

### Team Discovery (`/teams`)

**Purpose:** Find and compare teams matched to a specific package.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  Teams                         For: Security Hardening ▼ │
├─────────────────────────────────────────────────────────┤
│  Sort by: [Best Match ▼]  Filter: [Available Now ▼]     │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ #1 Match: 94%                                     │ │
│  │                                                   │ │
│  │ ┌──┐  DevCraft Solutions                         │ │
│  │ │DC│  ★★★★★ (4.9)  •  23 completed packages     │ │
│  │ └──┘                                              │ │
│  │                                                   │ │
│  │ Why matched:                                      │ │
│  │ • 8 security packages completed (highest)         │ │
│  │ • Node + React expertise verified                 │ │
│  │ • Available within 1 week                         │ │
│  │ • Budget aligned ($3,800-4,500 typical)           │ │
│  │                                                   │ │
│  │ Stack: Node  React  AWS  Docker  Terraform        │ │
│  │                                                   │ │
│  │ [View Profile]          [Request This Team]       │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ #2 Match: 87%                                     │ │
│  │                                                   │ │
│  │ ┌──┐  SecureStack Labs                           │ │
│  │ │SL│  ★★★★☆ (4.6)  •  15 completed packages     │ │
│  │ └──┘                                              │ │
│  │                                                   │ │
│  │ Why matched:                                      │ │
│  │ • Security specialist (12 of 15 packages)         │ │
│  │ • No React experience (partial stack match)       │ │
│  │ • Available in 2 weeks                            │ │
│  │ • Budget aligned ($3,200-4,000 typical)           │ │
│  │                                                   │ │
│  │ [View Profile]          [Request This Team]       │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**MUI Components:**
- `Select` for package context and sort/filter
- `Card` with `Avatar`, `Rating`, `Chip` tags
- `List` for "Why matched" reasoning
- `Button` variant="contained" for primary action, "outlined" for secondary
- `Badge` or percentage indicator for match score
- `Tooltip` on match factors for detail

---

### Workspace (`/workspaces/[id]`)

**Purpose:** Focused view of an active project between owner and team.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  ← Workspaces    Auth Refactor × DevCraft     [Actions] │
├─────────────────────────────────────────────────────────┤
│  [Overview]  [Milestones]  [Messages]  [Handoff]        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌────────────────────────────┐ ┌────────────────────┐ │
│  │ Progress                   │ │ Budget              │ │
│  │ ████████████░░░░░ 50%      │ │ $2,100 / $4,200    │ │
│  │ 2 of 4 milestones done     │ │ 50% released       │ │
│  └────────────────────────────┘ └────────────────────┘ │
│                                                         │
│  ┌────────────────────────────┐ ┌────────────────────┐ │
│  │ Timeline                   │ │ Team               │ │
│  │ Started: 1 Apr 2026        │ │ DevCraft Solutions │ │
│  │ Due: 15 May 2026           │ │ Lead: Sarah K.     │ │
│  │ Status: On track           │ │ [Message Team]     │ │
│  └────────────────────────────┘ └────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Milestones                                        │ │
│  │                                                   │ │
│  │  ✓  M1: CI Pipeline Setup          Accepted      │ │
│  │  ◷  M2: Security Audit             In Review     │ │
│  │      → Team submitted 2 hours ago                 │ │
│  │      → [Review Submission]                        │ │
│  │  ○  M3: Hardening Implementation   Not Started   │ │
│  │  ○  M4: Documentation & Handoff    Not Started   │ │
│  │                                                   │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Recent Activity                                   │ │
│  │                                                   │ │
│  │  Today     Team submitted M2 for review           │ │
│  │  Yesterday Team pushed 3 commits to audit branch  │ │
│  │  3d ago    Owner approved M1                      │ │
│  │  1w ago    Project started                        │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**MUI Components:**
- `Tabs` for sub-views
- `Grid` with 4 summary `Card` components
- `Stepper` vertical for milestones (custom icons: check, clock, circle)
- `Timeline` (MUI Lab) for activity feed
- `Button` for "Review Submission" CTA
- `Dialog` for review flow (opens evidence + accept/reject)
- `LinearProgress` for progress bars

---

### Milestone Review (`/workspaces/[id]/milestones`)

**Purpose:** Evidence-based review of submitted milestone deliverables.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  ← Workspace    Review: M2 Security Audit               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Acceptance Criteria                    4/5 Met    │ │
│  │                                                   │ │
│  │ ✓ Dependency audit report generated               │ │
│  │   Evidence: audit-report.pdf attached             │ │
│  │                                                   │ │
│  │ ✓ All critical vulnerabilities patched            │ │
│  │   Evidence: 0 critical in latest scan             │ │
│  │                                                   │ │
│  │ ✓ Authentication reviewed and hardened            │ │
│  │   Evidence: JWT rotation implemented, tested      │ │
│  │                                                   │ │
│  │ ✓ Secrets moved to vault                          │ │
│  │   Evidence: No plaintext secrets in repo scan     │ │
│  │                                                   │ │
│  │ ✗ Penetration test report                         │ │
│  │   Missing: No pentest report uploaded             │ │
│  │                                                   │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ AI Verification Summary                           │ │
│  │                                                   │ │
│  │ Automated checks passed: 3/3                      │ │
│  │ • Repo scanned: no secrets detected               │ │
│  │ • CI pipeline: security stage passing             │ │
│  │ • Dependencies: 0 critical, 2 moderate            │ │
│  │                                                   │ │
│  │ Recommendation: Accept with note on pentest       │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Decision                                          │ │
│  │                                                   │ │
│  │ ○ Accept milestone (release payment)              │ │
│  │ ○ Accept with conditions                          │ │
│  │ ○ Request revision                                │ │
│  │                                                   │ │
│  │ Notes (optional):                                 │ │
│  │ ┌─────────────────────────────────────────────┐  │ │
│  │ │                                             │  │ │
│  │ └─────────────────────────────────────────────┘  │ │
│  │                                                   │ │
│  │                              [Submit Decision]    │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**MUI Components:**
- `List` with custom icons (check green, cross red) for criteria
- `Paper` with `Alert` for AI verification summary
- `RadioGroup` for decision
- `TextField` multiline for notes
- `Button` variant="contained" color="primary" for submit
- `Chip` showing "4/5 Met" with color coding

---

### Handoff (`/workspaces/[id]/handoff`)

**Purpose:** Structured handoff checklist ensuring nothing is missed before project closure.

**Layout:**
```
┌─────────────────────────────────────────────────────────┐
│  ← Workspace    Handoff Readiness                       │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Readiness: 5/7 items verified                     │ │
│  │ ████████████████████░░░░░░ 71%                    │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Deployment                                        │ │
│  │ ✓ Production deployment documented                │ │
│  │ ✓ CI/CD pipeline operational                      │ │
│  │ ✓ Environment variables documented                │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Operations                                        │ │
│  │ ✓ Monitoring configured and alerting              │ │
│  │ ✓ Backup strategy documented                      │ │
│  │ ✗ Runbook not found                               │ │
│  │   → Team needs to provide operational runbook     │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ Knowledge Transfer                                │ │
│  │ ✗ No recorded handoff session                     │ │
│  │   → Schedule walkthrough with team                │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
│  ┌───────────────────────────────────────────────────┐ │
│  │ [Approve Handoff] (disabled until 7/7)            │ │
│  │                                                   │ │
│  │ Or: [Request Support Package]                     │ │
│  │ Ongoing support available for items not yet met.  │ │
│  └───────────────────────────────────────────────────┘ │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

**MUI Components:**
- `LinearProgress` with label for overall readiness
- `Card` grouped by category (Deployment, Operations, Knowledge Transfer)
- `List` with check/cross icons
- `Button` disabled state with tooltip explaining why
- `Alert` severity="info" for support package upsell

---

## Component Patterns

### Summary Cards (reusable)

Used on: Dashboard, Workspace overview, Product detail

```
MUI: Card + CardContent + Typography + Box
- elevation: 1
- border-left: 4px solid [category color]
- Typography variant="h3" for the number
- Typography variant="body2" color="text.secondary" for label
- Click navigates to relevant list
```

**Category colors:**
- Products: `#2563eb` (blue)
- Packages: `#7c3aed` (purple)
- Teams: `#059669` (green)
- Budget/Billing: `#d97706` (amber)
- Alerts/Attention: `#dc2626` (red)

### Status Chips

```
MUI: Chip with custom colors
- Active/In Progress: color="primary"
- Completed/Accepted: color="success"
- Needs Attention: color="warning"
- Blocked/Failed: color="error"
- Not Started: color="default"
```

### Evidence Indicators

```
MUI: List + ListItemIcon + ListItemText
- Met: CheckCircleIcon color="success"
- Not Met: CancelIcon color="error"
- Pending: HourglassIcon color="warning"
- Secondary text shows evidence source
```

### Empty States

Every list page has an empty state:

```
MUI: Box + Typography + Button (centered)
- Illustration or icon (large, muted)
- Heading: "No [items] yet"
- Body: One sentence explaining what this page shows
- CTA Button: "Create your first [item]"
```

---

## Visual Design System

### Colors (MUI Theme)

```javascript
palette: {
  primary: { main: '#2563eb' },      // Action blue
  secondary: { main: '#7c3aed' },    // Package purple
  success: { main: '#059669' },      // Verified green
  warning: { main: '#d97706' },      // Attention amber
  error: { main: '#dc2626' },        // Blocked red
  background: {
    default: '#f8fafc',              // Page background (existing)
    paper: '#ffffff',                 // Card surfaces
  },
  text: {
    primary: '#1f2937',              // Existing
    secondary: '#6b7280',            // Muted text
  }
}
```

### Typography (MUI Theme)

```javascript
typography: {
  fontFamily: "'Roboto', sans-serif",  // Already configured
  h4: { fontWeight: 600, fontSize: '1.5rem' },     // Page titles
  h6: { fontWeight: 600, fontSize: '1.125rem' },   // Section titles
  body1: { fontSize: '0.9375rem' },                 // Body text
  body2: { fontSize: '0.8125rem', color: '#6b7280' }, // Secondary text
}
```

### Spacing

- Page padding: `theme.spacing(3)` (24px)
- Card gap: `theme.spacing(2.5)` (20px)
- Section gap: `theme.spacing(4)` (32px)
- Inner card padding: `theme.spacing(2.5)` (20px)

### Elevation

- Cards at rest: elevation 1
- Cards on hover: elevation 3
- Modals/Dialogs: elevation 8
- Sidebar: elevation 4

### Responsive Breakpoints

- Mobile (<600px): Single column, full-width cards, bottom navigation
- Tablet (600-960px): 2-column grid, sidebar collapses to drawer
- Desktop (>960px): 3-column grid, persistent sidebar, full layout

---

## Key User Flows

### Flow 1: New Owner Onboarding

```
Dashboard (empty state) 
  → "Add Your First Product" CTA
  → /products/new (wizard step 1: connect)
  → Step 2: describe
  → Step 3: review + "Run AI Diagnosis"
  → /products/[id]/diagnosis (results load)
  → "Build Package" CTA on diagnosis page
  → /packages/new (pre-filled with recommendations)
  → Package created → "Find Team" CTA
  → /teams (filtered for this package)
  → Request team → Workspace created
  → /workspaces/[id] (active project begins)
```

### Flow 2: Review Submitted Milestone

```
Dashboard "Needs Attention" card
  → Click notification
  → /workspaces/[id] (overview shows milestone pending)
  → Click "Review Submission"
  → /workspaces/[id]/milestones (review view)
  → See acceptance criteria status + AI verification
  → Make decision (accept/revise)
  → Confirmation → payment released (if accepted)
  → Return to workspace overview (updated progress)
```

### Flow 3: Handoff and Project Closure

```
Workspace shows "All milestones complete"
  → "Begin Handoff" CTA appears
  → /workspaces/[id]/handoff
  → Checklist shows what's verified, what's missing
  → Team completes remaining items
  → All items verified → "Approve Handoff" enabled
  → Owner approves → project marked complete
  → Optional: "Request Support Package" for ongoing needs
  → /products/[id] (product now shows "Independent" status)
```

---

## Sidebar Navigation Design

```
┌──────────────────────┐
│  ProdUS              │
│  ─────────────────── │
│                      │
│  ◉ Dashboard         │
│  □ Products     (3)  │
│  ◇ Packages    (2)  │
│  ◎ Teams             │
│  ▣ Workspaces  (1)  │
│                      │
│  ─────────────────── │
│                      │
│  $ Billing           │
│  ⚙ Settings          │
│  ? Help              │
│                      │
│  ─────────────────── │
│  ┌──┐                │
│  │MA│ Mahmoud A.     │
│  └──┘ owner@produs   │
└──────────────────────┘
```

**MUI Components:**
- `Drawer` variant="permanent" (desktop), "temporary" (mobile)
- `List` with `ListItemButton`, `ListItemIcon`, `ListItemText`
- `Badge` for counts on menu items
- `Divider` between sections
- `Avatar` + `Typography` for user info at bottom
- Active item: `selected` prop with primary color background tint

---

## Migration Strategy

### Phase 1: Route Splitting (No visual changes)

Move existing page sections into separate routes. The UI stays the same, but each section gets its own URL. Users can now bookmark and share specific views.

- Extract product list into `/products`
- Extract product creation form into `/products/new`
- Keep dashboard as summary only
- Update sidebar menu to show new routes

### Phase 2: Wizard Patterns

Replace single-page forms with multi-step wizards:

- Product creation → 3-step wizard with `Stepper`
- Package builder → 4-step wizard with dependency validation
- Both use the same wizard layout component

### Phase 3: Workspace and Team Views

Build the collaboration layer:

- Workspace with milestone tracking
- Team discovery with AI matching
- Milestone review with evidence display

### Phase 4: AI Integration Points

Wire LoomAI deployment capabilities into the UI:

- Diagnosis results display on product detail
- Service recommendations in package builder
- Team match scoring in discovery
- Automated checks in milestone review
- Handoff readiness verification

---

## What This Redesign Achieves

| Before | After |
|---|---|
| One page with everything | 15+ focused pages with clear purpose |
| No clear entry for "create product" | Dedicated wizard with guided steps |
| Owner must know what services exist | AI recommends based on diagnosis evidence |
| Team selection is manual browsing | AI-scored matching with explanations |
| Milestone review is subjective | Evidence-based with automated verification |
| Handoff is informal | Structured checklist with verification gates |
| New users overwhelmed immediately | Progressive disclosure, empty states guide |
| No mobile usability | Responsive grid, collapsible sidebar, touch targets |
| Status described in words | Visual progress indicators everywhere |
| No sense of workflow | Clear flow from product → diagnosis → package → team → workspace → handoff |
