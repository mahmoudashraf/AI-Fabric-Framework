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

## Screen Flow Architecture

### Core Navigation Model: Product-Scoped Workspace

The platform uses a **product-scoped workspace** pattern. When an owner clicks a product, the entire navigation context switches to that product's workspace. The sidebar, breadcrumbs, and available actions all scope to the selected product.

```
┌──────────────────────────────────────────────────────────────────────┐
│  ProdUS                                                              │
│  ─────────                                                           │
│                                                                      │
│  GLOBAL                      PRODUCT WORKSPACE                       │
│  ┌──────────────┐            ┌──────────────────────────────────┐   │
│  │              │            │                                  │   │
│  │  Dashboard   │──click──→ │  Product: Auth Refactor          │   │
│  │  Products    │  product   │  ─────────────────────           │   │
│  │  Catalog     │            │                                  │   │
│  │  Settings    │            │  ◉ Overview    (product state)   │   │
│  │              │            │  ◎ Diagnosis   (scanner + AI)    │   │
│  │              │   ←back    │  □ Services    (selected)        │   │
│  │              │──to home── │  ◇ Package     (built)           │   │
│  │              │            │  ◎ Team        (matched)         │   │
│  │              │            │  ▣ Workspace   (delivery)        │   │
│  │              │            │  ↗ Share       (public links)    │   │
│  │              │            │                                  │   │
│  └──────────────┘            └──────────────────────────────────┘   │
│                                                                      │
│  "Go back to master/home page to change to new product"             │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Navigation behavior:**
- **Global level:** Dashboard shows all products, "Create Product" CTA, recent activity
- **Product level:** Clicking a product enters its workspace with product-scoped left menu
- **Back button:** Returns to global dashboard / product list (switch products)
- **Left menu in workspace:** Shows product's current state and next steps — each menu item reflects what's been done and what's pending
- **Menu items enable progressively:** Diagnosis unlocks after creation, Services after diagnosis, Package after service selection, Team after package, Workspace after team acceptance

### Product States & Next Steps

The product workspace menu shows the owner exactly where they are in the productization journey:

```
┌──────────────────────────────────────┐
│  ← All Products                      │
│                                      │
│  Auth Refactor                       │
│  Stage: Built, not production-ready  │
│                                      │
│  ─────────────────────               │
│                                      │
│  ✓ Overview            Created       │
│  ✓ Diagnosis           Complete      │
│  ✓ Services            3 selected    │
│  ◷ Package             In review     │  ← current step
│  ○ Team                Not started   │
│  ○ Workspace           —             │
│  ○ Share               —             │
│                                      │
│  ─────────────────────               │
│  NEXT STEP:                          │
│  Review your package and             │
│  confirm to start team matching.     │
│  [Review Package →]                  │
│                                      │
└──────────────────────────────────────┘
```

| State Icon | Meaning |
|---|---|
| ✓ | Complete — step done, can revisit |
| ◷ | In progress — current active step |
| ○ | Not started — locked or waiting on previous step |
| ⚠ | Needs attention — action required from owner |

---

### Three Entry Points Into Product Creation

Owners can start a product from three different places. All three converge into the same product workspace.

```
ENTRY POINT A                ENTRY POINT B              ENTRY POINT C
Dashboard / Products List    Services Catalog            AI-Assisted
"+ Create Product"           Browse → Select → Convert   "Let AI decide"
         │                           │                          │
         ▼                           ▼                          ▼
┌────────────────┐          ┌────────────────┐         ┌────────────────┐
│ Step 1: Input  │          │ Catalog Page   │         │ Step 1: Input  │
│ Name, URL,     │          │ Select services│         │ Name, URL,     │
│ repo, docs     │          │ and templates  │         │ repo, docs     │
└───────┬────────┘          └───────┬────────┘         └───────┬────────┘
        │                           │                          │
        ▼                           ▼                          ▼
┌────────────────┐          ┌────────────────┐         ┌────────────────┐
│ Step 2: Choose │          │ "Create Product│         │ AI analyzes    │
│ Template OR    │          │  with these    │         │ inputs, repo,  │
│ manual services│          │  services"     │         │ docs           │
│ OR let AI      │          │ → pre-fills    │         │ → recommends   │
│ recommend      │          │   new product  │         │   services     │
└───────┬────────┘          │   page with    │         │ → owner edits  │
        │                   │   selections   │         └───────┬────────┘
        │                   └───────┬────────┘                 │
        │                           │                          │
        └───────────────┬───────────┘──────────────────────────┘
                        │
                        ▼
               ┌────────────────┐
               │ Product Created│
               │ → Workspace    │
               │   with scoped  │
               │   navigation   │
               └────────────────┘
```

**Path A: Manual (Dashboard → Create)** — Owner knows what they want
- Step 1: Describe product (name, URL, repo, documents)
- Step 2: Choose services manually from catalog OR select a template
- Step 3: Review and create
- Product workspace opens with selected services

**Path B: Catalog-First** — Owner explores services before committing
- Browse the Services Catalog page independently
- Select services/templates that look relevant
- Click "Create Product with These Services"
- Redirects to new product page pre-filled with selections
- Owner adds product inputs (name, URL, repo) for analysis
- Product workspace opens with pre-selected services

**Path C: AI-Assisted (Primary/Recommended)** — AI drives the decisions
- Step 1: Describe product (name, URL, repo, documents)
- Step 2: Choose "Let AI analyze and recommend"
- AI runs diagnosis on inputs, repo, documents
- AI returns recommended services, owner reviews and edits
- Product workspace opens with AI-recommended services

---

### Screen: Dashboard Home (`/dashboard`) — Revised

**Purpose:** Global launchpad. Shows all products and their states. Primary action: create new product or enter a product workspace.

```
┌──────────────────────────────────────────────────────────────────────┐
│  Welcome back, Mahmoud                               [+ New Product] │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐            │
│  │ Products │  │ Active   │  │ Pending  │  │ Credits  │            │
│  │    3     │  │ Packages │  │ Reviews  │  │   1,200  │            │
│  │          │  │    2     │  │    1     │  │  balance  │            │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘            │
│                                                                      │
│  YOUR PRODUCTS                                                       │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────┐ │
│  │ Auth Refactor        │ │ Mobile App           │ │ API Gateway  │ │
│  │ ████████░░ 68%       │ │ ███░░░░░░ 30%        │ │ ○ Not started│ │
│  │                      │ │                      │ │              │ │
│  │ Next: Review M2      │ │ Next: Find team      │ │ Next: Run    │ │
│  │ submission           │ │                      │ │ diagnosis    │ │
│  │                      │ │                      │ │              │ │
│  │ [Open Workspace →]   │ │ [Open Workspace →]   │ │ [Start →]    │ │
│  └──────────────────────┘ └──────────────────────┘ └──────────────┘ │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ NEEDS ATTENTION                                               │   │
│  │                                                               │   │
│  │  ⚠ Milestone 2 submitted for "Auth Refactor" — Review now   │   │
│  │  ● Team match ready for "Mobile App" — View teams            │   │
│  │  ✓ Diagnosis complete for "API Gateway" — See results        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ RECENT ACTIVITY                                               │   │
│  │                                                               │   │
│  │  2h ago   Team DevCraft submitted milestone 2                │   │
│  │  1d ago   Package "Security Hardening" approved              │   │
│  │  2d ago   New product "API Gateway" created                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Key change from original:** Products are shown as cards directly on the dashboard with their current state and **next step**. Each card tells the owner exactly what to do next. Clicking opens the product workspace.

---

### Screen: New Product Creation (`/products/new`) — Revised

**Purpose:** Guided creation with three paths: manual, template, or AI-assisted.

```
┌──────────────────────────────────────────────────────────────────────┐
│  ← Back to Products              Create New Product                  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  ① Describe  →  ② Services  →  ③ Review  →  Created          │  │
│  │  ●━━━━━━━━━━━━━○━━━━━━━━━━━━━○━━━━━━━━━━━○                   │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  Step 1: Describe your product                                 │  │
│  │                                                                │  │
│  │  Product name *                                                │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │ My SaaS Platform                                        │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  Describe what your product does (natural language)            │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │ A customer support platform with ticketing, knowledge   │  │  │
│  │  │ base, and live chat for SaaS companies...               │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  Product URL (optional)                                        │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │ https://myapp.com                                       │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  Repository URL (optional)                                     │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │ https://github.com/myorg/myapp                          │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  Upload documents (optional)                                   │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  📎 Drop files here or click to upload                  │  │  │
│  │  │     PRD, architecture docs, requirements — AI will use  │  │  │
│  │  │     selected files to analyze your product.             │  │  │
│  │  │                                                         │  │  │
│  │  │  ✓ architecture.pdf (340KB)                [✕]         │  │  │
│  │  │  ✓ requirements.md (28KB)                  [✕]         │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  What stage is your product in?                                │  │
│  │  ○ Idea / Pre-build                                           │  │
│  │  ● Built but not production-ready                             │  │
│  │  ○ Running in production                                      │  │
│  │  ○ Production but needs improvement                           │  │
│  │                                                                │  │
│  │                                         [Continue →]           │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Step 2: Choose your service path**

```
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  Step 2: How do you want to select services?                   │  │
│  │                                                                │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  🤖 LET AI ANALYZE & RECOMMEND                (Primary) │  │  │
│  │  │                                                          │  │  │
│  │  │  AI will analyze your description, repo, and documents   │  │  │
│  │  │  to recommend the right services for your product.       │  │  │
│  │  │  You can review and edit everything before creating.     │  │  │
│  │  │                                                          │  │  │
│  │  │  [Choose This Path →]                                    │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  📋 START FROM A TEMPLATE                                │  │  │
│  │  │                                                          │  │  │
│  │  │  Pick a pre-configured service package:                  │  │  │
│  │  │                                                          │  │  │
│  │  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │  │  │
│  │  │  │ Full         │ │ Security     │ │ MVP Launch   │     │  │  │
│  │  │  │ Production   │ │ Focus        │ │              │     │  │  │
│  │  │  │ 8 services   │ │ 3 services   │ │ 5 services   │     │  │  │
│  │  │  │ [Select]     │ │ [Select]     │ │ [Select]     │     │  │  │
│  │  │  └──────────────┘ └──────────────┘ └──────────────┘     │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  🔧 CHOOSE SERVICES MANUALLY                             │  │  │
│  │  │                                                          │  │  │
│  │  │  Browse the full service catalog and pick exactly        │  │  │
│  │  │  what you need.                                          │  │  │
│  │  │                                                          │  │  │
│  │  │  [Open Catalog →]                                        │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │                                   [← Back]                     │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
```

**Step 2 (AI path): AI analysis results**

```
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  Step 2: AI Recommendations                                    │  │
│  │                                                                │  │
│  │  ┌──────────────────────────────────────────────────────────┐  │  │
│  │  │  🤖 Based on your inputs, repo, and documents, here's   │  │  │
│  │  │  what your product needs for production readiness:       │  │  │
│  │  └──────────────────────────────────────────────────────────┘  │  │
│  │                                                                │  │
│  │  RECOMMENDED (from analysis):                                  │  │
│  │                                                                │  │
│  │  ☑ Security Hardening                        ★ Recommended    │  │
│  │    3 critical findings in repo scan                            │  │
│  │                                                                │  │
│  │  ☑ CI/CD Pipeline Setup                      ★ Recommended    │  │
│  │    No CI configuration detected                                │  │
│  │                                                                │  │
│  │  ☑ Database Readiness                        ★ Recommended    │  │
│  │    No migration strategy found                                 │  │
│  │                                                                │  │
│  │  OPTIONAL (may benefit):                                       │  │
│  │                                                                │  │
│  │  ☐ Monitoring & Alerting                                      │  │
│  │    No APM or error tracking detected                           │  │
│  │                                                                │  │
│  │  ☐ AI Integration                                             │  │
│  │    Score: 78/100 — strong fit for support assistant             │  │
│  │                                                                │  │
│  │  ──────────────────────────────────────────────────────────    │  │
│  │                                                                │  │
│  │  ☐ Add more from catalog                    [Browse Catalog]   │  │
│  │                                                                │  │
│  │  3 services selected                  [← Back]  [Continue →]   │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
```

**AI recommendations show WHY each service is recommended** — linked to real evidence from the repo scan, not generic suggestions. Owner can check/uncheck any service, and add more from the catalog.

---

### Screen: Services Catalog (`/catalog`) — New Page

**Purpose:** Standalone browsable catalog. Entry point B — users can explore services independently, then convert selections into a product.

```
┌──────────────────────────────────────────────────────────────────────┐
│  Service Catalog                                        [Search...] │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  [All] [Security] [CI/CD] [Cloud] [Database] [Testing]              │
│  [Performance] [Operations] [AI Integration] [Launch]               │
│                                                                      │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────┐ │
│  │ 🔒 Security          │ │ ⚙ CI/CD Pipeline     │ │ ☁ Cloud      │ │
│  │    Hardening          │ │   Setup              │ │   Deployment │ │
│  │                      │ │                      │ │              │ │
│  │ Dependency audit,    │ │ Build, test, deploy  │ │ Infrastructure│ │
│  │ auth review, secrets │ │ automation with      │ │ setup, Docker,│ │
│  │ management, pentest  │ │ quality gates        │ │ Kubernetes   │ │
│  │                      │ │                      │ │              │ │
│  │ Includes:            │ │ Includes:            │ │ Includes:    │ │
│  │ • Vulnerability scan │ │ • Pipeline config    │ │ • IaC setup  │ │
│  │ • Auth hardening     │ │ • Automated tests    │ │ • Container  │ │
│  │ • Secrets vault      │ │ • Deploy stages      │ │ • Monitoring │ │
│  │                      │ │                      │ │              │ │
│  │ [☐ Select]           │ │ [☑ Selected]         │ │ [☐ Select]   │ │
│  └──────────────────────┘ └──────────────────────┘ └──────────────┘ │
│                                                                      │
│  ┌──────────────────────┐ ┌──────────────────────┐ ┌──────────────┐ │
│  │ 🗄 Database           │ │ 🧪 Testing &         │ │ 🤖 AI        │ │
│  │   Readiness           │ │   Quality            │ │   Integration│ │
│  │   ...                 │ │   ...                │ │   ...        │ │
│  │ [☐ Select]           │ │ [☐ Select]           │ │ [☐ Select]   │ │
│  └──────────────────────┘ └──────────────────────┘ └──────────────┘ │
│                                                                      │
│  ──────────────────────────────────────────────────────────────────  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  1 service selected: CI/CD Pipeline Setup                      │  │
│  │                                                                │  │
│  │  [Create New Product with Selected Services →]                 │  │
│  │                                                                │  │
│  │  Already have a product? [Add to Existing Product ▼]           │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Catalog behavior:**
- Category filter tabs at top
- Each service is a card with description, included items, and select checkbox
- Sticky bottom bar shows selection count and two actions:
  - "Create New Product with Selected Services" → redirects to `/products/new` with services pre-selected (skips Step 2 path selection)
  - "Add to Existing Product" → dropdown of owner's products, adds services to that product's workspace
- Templates are shown as a separate section: "Quick Start Templates" with pre-configured service bundles

---

### Screen: Product Workspace Overview (`/products/[id]`) — Revised

**Purpose:** Product-scoped home page. Shows current state, next steps, and all related data for one product. This is where the owner spends most of their time.

```
┌──────────────────────────────────────────────────────────────────────┐
│  ← All Products          Auth Refactor              [Settings] [⋮]  │
├────────────────────┬─────────────────────────────────────────────────┤
│                    │                                                 │
│  AUTH REFACTOR     │  PRODUCT OVERVIEW                              │
│                    │                                                 │
│  ─────────────     │  ┌──────────────────┐ ┌──────────────────────┐ │
│                    │  │ Health Score      │ │ Quick Info           │ │
│  ✓ Overview        │  │                  │ │                      │ │
│  ✓ Diagnosis       │  │  ████████░░ 72   │ │ Stack: Node, React   │ │
│  ✓ Services (3)    │  │                  │ │ Stage: Built         │ │
│  ◷ Package         │  │ ✓ CI/CD          │ │ Created: 12 Mar 2026 │ │
│  ○ Team            │  │ ✓ Monitoring     │ │ Team: —              │ │
│  ○ Workspace       │  │ ✗ Load testing   │ │ Package: Draft       │ │
│  ○ Share           │  │ ✗ Docs           │ │                      │ │
│                    │  └──────────────────┘ └──────────────────────┘ │
│  ─────────────     │                                                 │
│                    │  ┌────────────────────────────────────────────┐ │
│  NEXT STEP:        │  │ AI OPPORTUNITIES                    78/100│ │
│  Review package    │  │                                            │ │
│  and confirm to    │  │ 💬 Customer Support Assistant   Fit: 92   │ │
│  start matching.   │  │ 📊 Internal Data Analyst       Fit: 71   │ │
│  [Review →]        │  │                                            │ │
│                    │  │ [View AI Opportunity Report →]             │ │
│  ─────────────     │  └────────────────────────────────────────────┘ │
│                    │                                                 │
│  🔍 Diagnosis      │  ┌────────────────────────────────────────────┐ │
│  📦 Package        │  │ SELECTED SERVICES                          │ │
│  👥 Team           │  │                                            │ │
│  📊 Evidence       │  │ ☑ Security Hardening       ★ Recommended  │ │
│  📋 Activity       │  │ ☑ CI/CD Pipeline Setup     ★ Recommended  │ │
│                    │  │ ☑ Database Readiness       ★ Recommended  │ │
│                    │  │                                            │ │
│                    │  │ [Edit Services] [Build Package →]          │ │
│                    │  └────────────────────────────────────────────┘ │
│                    │                                                 │
│                    │  ┌────────────────────────────────────────────┐ │
│                    │  │ RECENT ACTIVITY                            │ │
│                    │  │                                            │ │
│                    │  │ 2h ago   Diagnosis completed               │ │
│                    │  │ 1d ago   3 services selected               │ │
│                    │  │ 2d ago   Product created                   │ │
│                    │  └────────────────────────────────────────────┘ │
│                    │                                                 │
├────────────────────┴─────────────────────────────────────────────────┤
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Key design decisions:**
- Left sidebar shows product-scoped navigation with state indicators (✓ ◷ ○)
- "Next Step" card in the sidebar tells the owner exactly what to do — never guessing
- AI Opportunities section appears after diagnosis (if score > threshold)
- Selected services shown with "Edit" and "Build Package" actions
- The workspace overview IS the product detail page — not a separate concept

---

### Screen: Public Shared Product Page (`/share/[linkId]`) — New Page

**Purpose:** Granular shareable view of a product. Owner controls what each link exposes. Used for team matching, investor updates, and expert recruitment.

**Share link management (owner view in workspace):**

```
┌──────────────────────────────────────────────────────────────────────┐
│  Share: Auth Refactor                                  [+ New Link]  │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  🔗 "For Team Matching"                                        │  │
│  │  Created: 2 days ago · Expires: Never · Views: 12              │  │
│  │                                                                │  │
│  │  SHARES:                                                       │  │
│  │  ✓ General description    ✓ Selected services                 │  │
│  │  ✓ Product status         ✗ Scanner findings                  │  │
│  │  ✗ Milestones             ✗ Team info                         │  │
│  │                                                                │  │
│  │  ACCESS LEVELS:                                                │  │
│  │  • Anyone with link: sees description + services              │  │
│  │  • Logged-in users: sees description + services + can apply   │  │
│  │  • Marketplace experts: sees catalog match, redirect to login │  │
│  │                                                                │  │
│  │  [Copy Link]  [Edit Permissions]  [View Analytics]  [Disable]  │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  🔗 "For Investor Update"                                      │  │
│  │  Created: 5 days ago · Expires: 7 days · Views: 4             │  │
│  │                                                                │  │
│  │  SHARES:                                                       │  │
│  │  ✓ General description    ✓ Selected services                 │  │
│  │  ✓ Product status         ✓ Milestone progress                │  │
│  │  ✗ Scanner findings       ✗ Team info                         │  │
│  │                                                                │  │
│  │  [Copy Link]  [Edit Permissions]  [View Analytics]  [Disable]  │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Public shared page (what external viewers see):**

```
┌──────────────────────────────────────────────────────────────────────┐
│  ProdUS                                            [Sign In] [Join] │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │  Auth Refactor                                                 │  │
│  │  by Product Owner                                              │  │
│  │                                                                │  │
│  │  A customer support platform with ticketing, knowledge         │  │
│  │  base, and live chat for SaaS companies. Built with            │  │
│  │  Node.js and React. Currently pre-production.                  │  │
│  │                                                                │  │
│  │  Stage: Built, not production-ready                            │  │
│  │  Stack: [Node.js] [React] [PostgreSQL] [AWS]                  │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  SERVICES NEEDED                                               │  │
│  │                                                                │  │
│  │  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐  │  │
│  │  │ 🔒 Security     │ │ ⚙ CI/CD         │ │ 🗄 Database     │  │  │
│  │  │    Hardening     │ │   Pipeline      │ │   Readiness     │  │  │
│  │  │                 │ │                 │ │                 │  │  │
│  │  │ Audit, auth,    │ │ Build, test,    │ │ Migrations,     │  │  │
│  │  │ secrets         │ │ deploy          │ │ indexing         │  │  │
│  │  └─────────────────┘ └─────────────────┘ └─────────────────┘  │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  🔒 Scanner findings are private                               │  │
│  │  Sign in to see detailed findings (if shared by owner).        │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  Are you an expert or team that can deliver these services?    │  │
│  │                                                                │  │
│  │  [Sign In to Apply]              [Learn About ProdUS →]        │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  📋 Marketplace users: Browse our full service catalog         │  │
│  │  [View Service Catalog →]                                      │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Three-tier access on shared page:**

| Viewer | What They See | What They Can Do |
|---|---|---|
| **Anonymous** (no login) | Product description, stack, stage, selected services (names only) | View only. CTA: "Sign In to Apply" |
| **Logged-in user** (registered) | Everything above + scanner findings (if owner shared them) + detailed service descriptions | Apply to project, message owner, view full details |
| **Marketplace expert** (from Network) | Everything above + service catalog cross-reference | See how their skills match, apply through Network profile |

---

### Updated Route Structure

```
app/(dashboard)/
├── dashboard/page.tsx              → Home: product cards, activity, quick actions
├── products/
│   ├── page.tsx                    → Product list (grid/table of all products)
│   ├── new/page.tsx                → Create product (3-step: describe → services → review)
│   └── [id]/
│       ├── page.tsx                → Product workspace overview (scoped navigation)
│       ├── diagnosis/page.tsx      → AI diagnosis + scanner results + AI opportunities
│       ├── services/page.tsx       → Selected services (edit, add, remove)
│       ├── package/page.tsx        → Package builder (milestones, budget, review)
│       ├── team/page.tsx           → Team matching and selection
│       ├── workspace/page.tsx      → Active delivery (milestones, evidence, messages)
│       ├── share/page.tsx          → Share link management
│       └── settings/page.tsx       → Product settings
├── catalog/page.tsx                → Service catalog (standalone, browsable)     ← NEW
├── share/[linkId]/page.tsx         → Public shared product page (external)       ← NEW
├── teams/
│   ├── page.tsx                    → Team discovery (global browse)
│   └── [id]/page.tsx              → Team profile
├── billing/page.tsx                → Credits, payment history
└── settings/page.tsx               → Account settings
```

**Key changes from original:**
- Added `/catalog` as standalone catalog entry point
- Added `/share/[linkId]` for public shareable product pages
- Products now contain nested workspace routes (`/products/[id]/workspace/`) instead of separate `/workspaces/` top-level route — everything is product-scoped
- Added `/products/[id]/services/` for service management within workspace
- Added `/products/[id]/share/` for share link management

---

## Key User Flows

### Flow 1: New Owner — AI-Assisted Path (Primary)

```
Dashboard (empty state)
  → "Create Your First Product" CTA
  → /products/new Step 1: Describe (name, URL, repo, docs, stage)
  → Step 2: Choose "Let AI Analyze & Recommend"
  → AI processes inputs, repo, docs
  → Step 2 shows AI-recommended services (owner edits)
  → Step 3: Review summary
  → Product created → redirected to /products/[id]
  → Product workspace opens with scoped navigation
  → Left nav shows: ✓ Overview, ✓ Services, ◷ Diagnosis (running)
  → Diagnosis completes → ✓ Diagnosis, AI Opportunities shown
  → Owner clicks "Build Package →"
  → /products/[id]/package (pre-filled from services + diagnosis)
  → Package built → "Find Team" CTA
  → /products/[id]/team (matched teams shown)
  → Owner requests team → Workspace activates
  → /products/[id]/workspace (milestones, evidence, delivery)
```

### Flow 2: Catalog-First Entry (Alternative)

```
Owner browses /catalog
  → Explores service categories, reads descriptions
  → Selects 2 services (CI/CD + Security)
  → Clicks "Create New Product with Selected Services"
  → /products/new Step 1: Describe (name, URL, repo, docs)
  → Step 2: Pre-filled with selected services + "Add more" option
  → Step 3: Review
  → Product created with pre-selected services
  → Workspace opens → owner runs diagnosis for evidence
```

### Flow 3: Existing Owner — Add Services to Product

```
Owner browses /catalog
  → Selects "AI Integration" service
  → Clicks "Add to Existing Product ▼"
  → Dropdown shows owner's products
  → Selects "Auth Refactor"
  → Service added to /products/[auth-refactor-id]/services
  → Owner navigates to product workspace
  → Left nav shows updated service count
```

### Flow 4: Share Product for Team Matching

```
Owner in /products/[id]/share
  → Clicks "+ New Link"
  → Configures: share services + status, hide findings + milestones
  → Names link "For Team Matching"
  → Copies link
  → Posts link on Network or sends to expert
  → Expert opens /share/[linkId]
  → Sees product description + services needed
  → Clicks "Sign In to Apply"
  → Logged in → sees full details (if shared)
  → Applies through Network profile
  → Owner sees application in /products/[id]/team
```

### Flow 5: Review Submitted Milestone

```
Dashboard "Needs Attention" card
  → Click notification
  → /products/[id]/workspace (milestone pending)
  → Click "Review Submission"
  → Review view: acceptance criteria + evidence + AI verification
  → Make decision (accept/revise)
  → Confirmation → payment released (if accepted)
  → Workspace progress updates
```

### Flow 6: Handoff and Project Closure

```
Workspace shows "All milestones complete"
  → "Begin Handoff" CTA appears
  → Handoff checklist within workspace
  → Items verified → "Approve Handoff" enabled
  → Owner approves → project marked complete
  → Optional: "Request Support Package"
  → Product shows "Production Ready" status
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
