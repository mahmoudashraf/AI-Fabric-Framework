# ProdUS Network — UI Design Document

## Design System

### Visual Foundation

The PRD specifies: "Apple-like calm UI: white canvas, clear cards, subdued depth, pastel service identity, focused actions, no noisy social mechanics."

This is a **light, professional workspace** — not the dark marketing aesthetic of the landing page.

### Color System

| Token | Value | Usage |
|---|---|---|
| `--canvas` | `#ffffff` | Page background, card surfaces |
| `--surface` | `#f8fafc` | Secondary background, sidebar, input fields |
| `--surface-raised` | `#ffffff` | Cards, elevated panels |
| `--border` | `#e2e8f0` | Card borders, dividers |
| `--border-focus` | `#6366f1` | Active inputs, selected states |
| `--text-primary` | `#0f172a` | Headlines, primary body |
| `--text-secondary` | `#475569` | Descriptions, secondary info |
| `--text-muted` | `#94a3b8` | Captions, timestamps, labels |
| `--primary` | `#6366f1` | Primary actions, active nav, badges |
| `--primary-soft` | `#eef2ff` | Primary action backgrounds, pills |
| `--success` | `#059669` | Verified, accepted, available |
| `--success-soft` | `#ecfdf5` | Success backgrounds |
| `--warning` | `#d97706` | Pending, attention, needs info |
| `--warning-soft` | `#fffbeb` | Warning backgrounds |
| `--error` | `#dc2626` | Declined, blocked, urgent |
| `--error-soft` | `#fef2f2` | Error backgrounds |

### Service Category Colors (Pastels)

Each service category has a distinct pastel identity used for chips, channel icons, and category badges:

| Category | Color | Soft BG |
|---|---|---|
| Security | `#7c3aed` | `#f5f3ff` |
| CI/CD | `#2563eb` | `#eff6ff` |
| Cloud Infrastructure | `#0891b2` | `#ecfeff` |
| Monitoring | `#059669` | `#ecfdf5` |
| Frontend | `#e11d48` | `#fff1f2` |
| Backend | `#ea580c` | `#fff7ed` |
| Documentation | `#65a30d` | `#f7fee7` |
| Testing | `#7c3aed` | `#faf5ff` |

### Typography

| Role | Size | Weight | Line-height |
|---|---|---|---|
| Page title | 24px | 700 | 1.2 |
| Section title | 18px | 600 | 1.3 |
| Card title | 16px | 600 | 1.4 |
| Body | 14px | 400 | 1.6 |
| Caption/label | 12px | 500 | 1.4 |
| Badge text | 11px | 600 | 1 |

Font: Inter (matches teams landing page for brand consistency)

### Spacing Scale

- `4px` — tight internal gaps
- `8px` — chip padding, small gaps
- `12px` — card internal spacing
- `16px` — standard gap between elements
- `20px` — card padding
- `24px` — section gap
- `32px` — page section spacing
- `48px` — major section breaks

### Card Style

```
background: #ffffff
border: 1px solid #e2e8f0
border-radius: 8px
padding: 20px
box-shadow: 0 1px 3px rgba(0,0,0,0.04)
hover: box-shadow 0 4px 12px rgba(0,0,0,0.06), translateY(-1px)
```

### Button Styles

| Variant | Background | Text | Border |
|---|---|---|---|
| Primary | `#6366f1` | `#ffffff` | none |
| Secondary | `#ffffff` | `#0f172a` | `1px solid #e2e8f0` |
| Ghost | transparent | `#6366f1` | none |
| Danger | `#fef2f2` | `#dc2626` | `1px solid #fecaca` |
| Success | `#ecfdf5` | `#059669` | `1px solid #a7f3d0` |

All buttons: `border-radius: 6px`, `padding: 8px 16px`, `font-weight: 500`, `font-size: 14px`

---

## Global Shell

### Network Layout Structure

```
┌──────────────────────────────────────────────────────────────────────┐
│  HEADER BAR                                                          │
│  ┌──────┐                                   ┌───┐ ┌───┐ ┌────────┐ │
│  │ProdUS│  Network  Studio  ···             │ 🔔│ │ ✉ │ │ Avatar │ │
│  └──────┘                                   └───┘ └───┘ └────────┘ │
├────────────────┬─────────────────────────────────────────────────────┤
│  SIDEBAR       │  CONTENT AREA                                       │
│                │                                                     │
│  Home          │                                                     │
│  Directory     │                                                     │
│  Formation     │  (Page content renders here)                        │
│  Messages (3)  │                                                     │
│  Channels      │                                                     │
│  Trials        │                                                     │
│  ─────────     │                                                     │
│  My Profile    │                                                     │
│  My Team       │                                                     │
│  Requests (2)  │                                                     │
│  ─────────     │                                                     │
│  Settings      │                                                     │
│                │                                                     │
├────────────────┴─────────────────────────────────────────────────────┤
│  (Footer only on public/marketing pages)                             │
└──────────────────────────────────────────────────────────────────────┘
```

### Header Bar

```
┌──────────────────────────────────────────────────────────────────────┐
│                                                                      │
│  ┌────────────────┐                                                  │
│  │ ◆ ProdUS       │    [ Network ]  [ Studio ]                       │
│  └────────────────┘     ─────────                                    │
│                         (active underline)                            │
│                                                                      │
│                              Search experts, teams, channels...       │
│                              ┌────────────────────────────────┐      │
│                              │ 🔍                              │      │
│                              └────────────────────────────────┘      │
│                                                                      │
│                                         🔔        ✉ (3)     [MA ▾]  │
│                                       (bell)   (messages)  (avatar)  │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

- **Product switcher:** "Network" and "Studio" tabs — active one has a bottom accent line
- **Global search:** Centered, searches experts, teams, channels, posts
- **Notification bell:** Badge count for unread notifications
- **Messages shortcut:** Badge count for unread threads
- **Avatar dropdown:** Profile, settings, logout

### Sidebar Navigation

```
┌──────────────────┐
│                  │
│  HOME            │  ← Role-aware command center
│                  │
│  DISCOVER        │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄  │
│  Directory       │
│  Formation Board │
│                  │
│  COMMUNICATE     │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄  │
│  Messages    (3) │  ← Unread badge
│  Channels        │
│                  │
│  COLLABORATE     │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄  │
│  Join Requests(2)│
│  Trials      (1) │
│                  │
│  ───────────     │
│                  │
│  MY SPACE        │
│  ┄┄┄┄┄┄┄┄┄┄┄┄┄  │
│  Expert Profile  │
│  Team Profile    │
│  Settings        │
│                  │
└──────────────────┘
```

- Width: 240px (collapsed: 64px icon-only on tablet)
- Background: `#f8fafc`
- Active item: `#eef2ff` background, `#6366f1` text, 2px left accent
- Section labels: 11px, uppercase, `#94a3b8`, letter-spacing 0.08em
- Badge: Indigo pill with white text, right-aligned

---

## Page Designs

### 1. Network Home (Command Center)

**Route:** `network.produs.com/`

**Purpose:** Role-aware dashboard. Answer "what needs my attention?" immediately.

**Solo Expert View:**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Good morning, Sarah                                             │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ PROFILE COMPLETENESS                              85% ████░ ││
│  │ Add portfolio links and cover photo to complete your profile ││
│  │                                    [Complete Profile →]      ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │
│  │ Available ✓   │ │ Looking for   │ │ Messages      │         │
│  │               │ │ team ✓        │ │               │         │
│  │ Status: Open  │ │ Posted 3d ago │ │ 3 unread      │         │
│  │               │ │               │ │               │         │
│  │ [Update]      │ │ [View Post]   │ │ [View →]      │         │
│  └───────────────┘ └───────────────┘ └───────────────┘         │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ RECOMMENDED FOR YOU                           [View All →]  ││
│  │                                                             ││
│  │ ┌─────────────────────────────┐ ┌────────────────────────┐ ││
│  │ │ Team Opening                │ │ Complementary Expert   │ ││
│  │ │                             │ │                        │ ││
│  │ │ CloudStack Crew             │ │ Jordan Lee             │ ││
│  │ │ "Frontend Specialist"       │ │ React · Next.js · UI  │ ││
│  │ │                             │ │                        │ ││
│  │ │ Needs: React, Next.js       │ │ Looking for:           │ ││
│  │ │ Match: 92% service fit      │ │ "Security + infra      │ ││
│  │ │                             │ │  partner"              │ ││
│  │ │ [View Opening] [Apply]      │ │                        │ ││
│  │ │                             │ │ [View Profile][Message]│ ││
│  │ └─────────────────────────────┘ └────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ RECENT ACTIVITY                                             ││
│  │                                                             ││
│  │  ● 2h   Reply in #security: "JWT rotation pattern..."      ││
│  │  ● 1d   Your post marked helpful in #ci-cd                 ││
│  │  ○ 2d   CloudStack Crew viewed your profile                ││
│  │  ○ 3d   New opening matches your services                  ││
│  │                                                             ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ YOUR CHANNELS                               [Browse All →]  ││
│  │                                                             ││
│  │  🟣 #security         3 new posts                           ││
│  │  🔵 #ci-cd            1 new post                            ││
│  │  🟢 #cloud-infra      5 new posts                           ││
│  │                                                             ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Team Lead View:**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Good morning, Marcus                    Team: CloudStack Crew   │
│                                                                  │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │
│  │ Open Roles    │ │ Join Requests │ │ Active Trials │         │
│  │               │ │               │ │               │         │
│  │     2         │ │     4         │ │     1         │         │
│  │               │ │ 2 new today   │ │ In progress   │         │
│  │ [Manage]      │ │ [Review →]    │ │ [View →]      │         │
│  └───────────────┘ └───────────────┘ └───────────────┘         │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ PENDING REQUESTS                              [View All →]  ││
│  │                                                             ││
│  │ ┌─────────────────────────────────────────────────────────┐ ││
│  │ │ ┌──┐ Jordan Lee          Applied for: Frontend Role     │ ││
│  │ │ │JL│ React · Next.js     Submitted: 4h ago              │ ││
│  │ │ └──┘ 5 milestones · 100% acceptance                    │ ││
│  │ │                                                         │ ││
│  │ │ "I specialize in production React apps with a focus     │ ││
│  │ │  on performance and accessibility..."                   │ ││
│  │ │                                                         │ ││
│  │ │ [Review Request]  [View Profile]  [Message]             │ ││
│  │ └─────────────────────────────────────────────────────────┘ ││
│  │                                                             ││
│  │ ┌─────────────────────────────────────────────────────────┐ ││
│  │ │ ┌──┐ Alex Rivera         Applied for: Frontend Role     │ ││
│  │ │ │AR│ Vue · React          Submitted: 1d ago             │ ││
│  │ │ └──┘ 2 milestones · 100% acceptance                    │ ││
│  │ │                                           [Review →]    │ ││
│  │ └─────────────────────────────────────────────────────────┘ ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ AI SUGGESTIONS                                              ││
│  │                                                             ││
│  │ 💡 "Based on recent package demand, adding a Documentation  ││
│  │    specialist would let your team take full productization  ││
│  │    packages. 3 matched packages need this capability."      ││
│  │                                                             ││
│  │    [Find Documentation Experts →]                           ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 2. Expert Profile

**Route:** `network.produs.com/experts/{id}`

**Public view (viewed by another expert or team lead):**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │
│  │░░░░░░░░░░░░░░░░ COVER PHOTO AREA ░░░░░░░░░░░░░░░░░░░░░░│   │
│  │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ┌────┐                                                  │   │
│  │  │    │  Sarah Kim                                       │   │
│  │  │ SK │  Full-stack Security Engineer                    │   │
│  │  │    │  London, UK · GMT · Available now                │   │
│  │  └────┘                                                  │   │
│  │         ● Available        ● Looking for team            │   │
│  │                                                          │   │
│  │                           [Message]  [Invite to Team]    │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ ABOUT                                                    │   │
│  │                                                          │   │
│  │ 8 years building production security infrastructure.     │   │
│  │ Previously: Senior Security Engineer at Monzo, Platform  │   │
│  │ Engineer at Deliveroo. I focus on making real systems    │   │
│  │ secure without slowing teams down.                       │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌────────────────────────────┐ ┌───────────────────────────┐   │
│  │ SERVICES                   │ │ VERIFIED DELIVERY         │   │
│  │                            │ │                           │   │
│  │ ┌────────────────────────┐ │ │  ┌─────┐  ┌─────┐       │   │
│  │ │ 🟣 Security Hardening  │ │ │  │  7  │  │100% │       │   │
│  │ │    Verified ✓          │ │ │  │mile-│  │acce-│       │   │
│  │ └────────────────────────┘ │ │  │stones│  │ptance│      │   │
│  │ ┌────────────────────────┐ │ │  └─────┘  └─────┘       │   │
│  │ │ 🔵 CI/CD Pipeline      │ │ │                           │   │
│  │ │    Verified ✓          │ │ │  ┌─────┐  ┌─────┐       │   │
│  │ └────────────────────────┘ │ │  │ 3.8d│  │  4  │       │   │
│  │ ┌────────────────────────┐ │ │  │ avg │  │teams│       │   │
│  │ │ 🌊 Cloud Deploy        │ │ │  │deliv│  │worked│      │   │
│  │ │    Verified ✓          │ │ │  └─────┘  └─────┘       │   │
│  │ └────────────────────────┘ │ │                           │   │
│  │                            │ │ Joined: March 2026        │   │
│  └────────────────────────────┘ └───────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ TECH STACK                                               │   │
│  │                                                          │   │
│  │ [Node.js] [Go] [TypeScript] [AWS] [Terraform]           │   │
│  │ [Docker] [Kubernetes] [GitHub Actions] [PostgreSQL]      │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ LOOKING FOR TEAM                                         │   │
│  │                                                          │   │
│  │ "Looking for a frontend specialist to form a full-stack  │   │
│  │  productization team. I handle infra, security, and      │   │
│  │  DevOps — need someone strong in React/Next.js for the   │   │
│  │  UI layer of delivery packages. Async-first, long-term   │   │
│  │  partnership preferred."                                 │   │
│  │                                                          │   │
│  │  Needs: [Frontend] [Documentation]                       │   │
│  │  Style: Async · GMT/GMT+1 · Long-term                    │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ COMMUNITY ACTIVITY                                       │   │
│  │                                                          │   │
│  │  Active in: #security (34 posts) · #ci-cd (12 posts)    │   │
│  │  Helpfulness: High (18 helpful marks received)           │   │
│  │  Member since: March 2026                                │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ PORTFOLIO & LINKS                                        │   │
│  │                                                          │   │
│  │  🔗 github.com/sarahkim                                  │   │
│  │  🔗 sarahkim.dev                                         │   │
│  │  🔗 linkedin.com/in/sarahkim                             │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Profile Edit Mode (own profile):**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Edit Expert Profile                          [Preview] [Save]   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ COVER PHOTO                                              │   │
│  │ ┌────────────────────────────────────────────────────┐   │   │
│  │ │         [Upload Cover Photo]                       │   │   │
│  │ │         1200×300 recommended                       │   │   │
│  │ └────────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ BASIC INFO                                               │   │
│  │                                                          │   │
│  │  Profile photo         Display name                      │   │
│  │  ┌────┐               ┌──────────────────────────┐      │   │
│  │  │    │ [Change]      │ Sarah Kim                 │      │   │
│  │  └────┘               └──────────────────────────┘      │   │
│  │                                                          │   │
│  │  Headline                                                │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ Full-stack Security Engineer                      │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  Bio                                                     │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ 8 years building production security             │   │   │
│  │  │ infrastructure. Previously: Senior Security...   │   │   │
│  │  │                                                  │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  Location              Timezone                          │   │
│  │  ┌─────────────────┐  ┌─────────────────┐              │   │
│  │  │ London, UK      │  │ GMT (UTC+0)   ▾ │              │   │
│  │  └─────────────────┘  └─────────────────┘              │   │
│  │  ☐ Hide location (show timezone only)                   │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ AVAILABILITY & INTENT                                    │   │
│  │                                                          │   │
│  │  Availability                                            │   │
│  │  ◉ Available now   ○ Available soon   ○ Not available    │   │
│  │                                                          │   │
│  │  Looking for team                                        │   │
│  │  [Toggle: ON ●━━━]                                       │   │
│  │                                                          │   │
│  │  Looking-for-team statement                              │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ Looking for a frontend specialist to form a...   │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  Preferred project size                                  │   │
│  │  ○ Small ($1-3K)  ◉ Medium ($3-8K)  ○ Large ($8K+)     │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ SERVICES                                    [+ Add Service]  │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 🟣 Security Hardening                    ✓ Verified│ │   │
│  │  │    Proficiency: Expert                    [Remove] │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 🔵 CI/CD Pipeline Setup                  ✓ Verified│ │   │
│  │  │    Proficiency: Expert                    [Remove] │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │  ┌────────────────────────────────────────────────────┐ │   │
│  │  │ 🌊 Cloud Deployment                      ✓ Verified│ │   │
│  │  │    Proficiency: Advanced                  [Remove] │ │   │
│  │  └────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ TECH STACK                                               │   │
│  │                                                          │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ Node.js × | Go × | TypeScript × | AWS × | +      │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │  (Autocomplete input with removable chips)              │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│                                             [Cancel] [Save Profile]│
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 3. Expert Directory

**Route:** `network.produs.com/directory`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Expert Directory                                                │
│  Find experts and teams to collaborate with                      │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 🔍 Search by name, skill, or service...                  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ FILTERS                                                  │   │
│  │                                                          │   │
│  │ Service:    [Security ×] [CI/CD ×]        [+ Add]        │   │
│  │ Skills:     [Node.js ×]                   [+ Add]        │   │
│  │ Available:  [Now ▾]   Timezone: [Any ▾]   Team: [Any ▾] │   │
│  │                                                          │   │
│  │ [Clear Filters]                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 💡 SUGGESTED FOR YOU                         [Dismiss]   │   │
│  │                                                          │   │
│  │ "Based on your Security + CI/CD skills, these experts    │   │
│  │  would complement your team with Frontend and Docs:"     │   │
│  │                                                          │   │
│  │ ┌──────────────────┐ ┌──────────────────┐               │   │
│  │ │ Jordan Lee       │ │ Priya Sharma     │               │   │
│  │ │ Frontend · React │ │ Docs · Technical │               │   │
│  │ │ 92% complement   │ │ 87% complement   │               │   │
│  │ │ [View] [Message] │ │ [View] [Message] │               │   │
│  │ └──────────────────┘ └──────────────────┘               │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  12 experts match your filters         Sort: [Best Match ▾]     │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ┌────┐  Sarah Kim                      ● Available     │   │
│  │  │ SK │  Full-stack Security Engineer                    │   │
│  │  └────┘  London · GMT                                    │   │
│  │                                                          │   │
│  │  Services: [🟣 Security] [🔵 CI/CD] [🌊 Cloud]          │   │
│  │  Stack:    Node.js · Go · AWS · Terraform                │   │
│  │                                                          │   │
│  │  7 milestones · 100% acceptance · 3.8d avg               │   │
│  │                                                          │   │
│  │  ★ Looking for team: "Frontend specialist to form..."    │   │
│  │                                                          │   │
│  │                    [View Profile]  [Message]  [Invite]    │   │
│  │                                                          │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │                                                          │   │
│  │  ┌────┐  Marcus Chen                    ● Available     │   │
│  │  │ MC │  DevOps & Cloud Architect                        │   │
│  │  └────┘  Berlin · CET                                    │   │
│  │                                                          │   │
│  │  Services: [🌊 Cloud] [🟢 Monitoring] [🔵 CI/CD]        │   │
│  │  Stack:    AWS · GCP · Kubernetes · Terraform            │   │
│  │                                                          │   │
│  │  12 milestones · 97% acceptance · 4.1d avg               │   │
│  │                                                          │   │
│  │  ★ Looking for team: "Security and frontend partners..." │   │
│  │                                                          │   │
│  │                    [View Profile]  [Message]  [Invite]    │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  [Load More]                                                     │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Profile preview drawer (opens from right when clicking "View Profile"):**

```
                              ┌────────────────────────────────────┐
                              │ ← Sarah Kim              [Full →]  │
                              │                                    │
                              │ ┌──┐ Full-stack Security Engineer  │
                              │ │SK│ London · GMT · Available      │
                              │ └──┘                               │
                              │                                    │
                              │ SERVICES                           │
                              │ 🟣 Security Hardening ✓            │
                              │ 🔵 CI/CD Pipeline ✓                │
                              │ 🌊 Cloud Deploy ✓                  │
                              │                                    │
                              │ DELIVERY                           │
                              │ 7 milestones · 100% · 3.8d avg    │
                              │                                    │
                              │ STACK                              │
                              │ Node · Go · AWS · Terraform        │
                              │                                    │
                              │ LOOKING FOR                        │
                              │ "Frontend specialist to form..."   │
                              │                                    │
                              │ ─────────────────────────────────  │
                              │                                    │
                              │ [Message]  [Invite to Team]        │
                              │                                    │
                              └────────────────────────────────────┘
```

---

### 4. Formation Board

**Route:** `network.produs.com/formation`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Formation Board                             [+ Create Post]     │
│  Find teammates or recruit for your team                         │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ [All]  [Looking for Team]  [Team Openings]               │   │
│  │                                                          │   │
│  │ Service: [Any ▾]  Timezone: [Any ▾]  Posted: [Recent ▾] │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 👤 LOOKING FOR TEAM                          Posted 3d   │   │
│  │                                                          │   │
│  │  ┌────┐  Sarah Kim                                       │   │
│  │  │ SK │  Security Engineer · London · GMT                 │   │
│  │  └────┘  7 milestones · 100% acceptance                   │   │
│  │                                                          │   │
│  │  "Full-stack team for ongoing productization packages"    │   │
│  │                                                          │   │
│  │  I bring:  [🟣 Security] [🔵 CI/CD] [🌊 Cloud]          │   │
│  │  I need:   [🔴 Frontend] [🟢 Documentation]              │   │
│  │                                                          │   │
│  │  Style: Async · GMT/GMT+1 · Long-term · $5-12K packages  │   │
│  │                                                          │   │
│  │  💡 92% match with your skills                           │   │
│  │                                                          │   │
│  │                           [View Profile]  [Message]       │   │
│  │                                                          │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ 👥 TEAM OPENING                             Posted 1d   │   │
│  │                                                          │   │
│  │  ┌──┐┌──┐  CloudStack Crew (3 members)                  │   │
│  │  │CS││··│   12 packages completed · $34K earned          │   │
│  │  └──┘└──┘                                                │   │
│  │                                                          │   │
│  │  Role: "Frontend Specialist"                              │   │
│  │                                                          │   │
│  │  Need:   [🔴 Frontend] — React/Next.js production        │   │
│  │  Offer:  Even revenue split on frontend milestones       │   │
│  │  Hours:  15-25/week flexible                              │   │
│  │  Timezone: GMT-2 to GMT+2                                 │   │
│  │                                                          │   │
│  │  "We have consistent package flow (2-3/month) and need   │   │
│  │   a frontend partner for the UI portions. We handle      │   │
│  │   infra, security, backend. You handle frontend."        │   │
│  │                                                          │   │
│  │  Application questions:                                   │   │
│  │  • Share a React production app you're proud of          │   │
│  │  • How do you handle component performance?              │   │
│  │                                                          │   │
│  │  💡 88% service fit                                      │   │
│  │                                                          │   │
│  │                    [View Team]  [Apply]  [Message]         │   │
│  │                                                          │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │ 👤 LOOKING FOR TEAM                          Posted 5d   │   │
│  │                                                          │   │
│  │  ┌────┐  Jordan Lee                                      │   │
│  │  │ JL │  Frontend Engineer · Austin · CST                 │   │
│  │  └────┘  5 milestones · 100% acceptance                   │   │
│  │                                                          │   │
│  │  "React/Next.js specialist seeking infra-focused team"    │   │
│  │                                                          │   │
│  │  I bring:  [🔴 Frontend] [🧪 Testing]                    │   │
│  │  I need:   [🟣 Security] [🌊 Cloud] [🔵 CI/CD]          │   │
│  │                                                          │   │
│  │                           [View Profile]  [Message]       │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Create Post Modal (Looking for Team):**

```
┌──────────────────────────────────────────────────────────────┐
│                                                              │
│  Post: Looking for Team                           [×]        │
│                                                              │
│  Title                                                       │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Full-stack team for ongoing productization packages   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  What you bring (services)                                   │
│  [🟣 Security ×] [🔵 CI/CD ×] [🌊 Cloud ×]  [+ Add]       │
│                                                              │
│  What you need (services)                                    │
│  [🔴 Frontend ×] [🟢 Docs ×]                [+ Add]        │
│                                                              │
│  Collaboration statement                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Looking for a frontend specialist to form a           │   │
│  │ full-stack productization team...                     │   │
│  │                                                       │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  Working style                                               │
│  Communication: [Async ▾]                                    │
│  Timezone:      [GMT/GMT+1 ▾]                                │
│  Duration:      [Long-term ▾]                                │
│  Package size:  [Medium $3-8K ▾]                             │
│                                                              │
│  Expires in: [30 days ▾]                                     │
│                                                              │
│                                    [Cancel]  [Publish Post]  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

### 5. Join Request Detail

**Route:** `network.produs.com/join-requests/{id}`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Join Requests                                                 │
│                                                                  │
│  ┌────────────────────────────────────────┬─────────────────────┐│
│  │ REQUEST DETAIL                         │ CONVERSATION        ││
│  │                                        │                     ││
│  │ ┌──────────────────────────────────┐   │ System: Thread      ││
│  │ │ STATUS                           │   │ created when Jordan ││
│  │ │                                  │   │ applied. 4h ago     ││
│  │ │ ◉ Under Review                   │   │                     ││
│  │ │                                  │   │ ─────────────────── ││
│  │ │ Applied: 4h ago                  │   │                     ││
│  │ │ Opening: Frontend Specialist     │   │ ┌──┐ Jordan · 4h   ││
│  │ │ Team: CloudStack Crew            │   │ │JL│                ││
│  │ └──────────────────────────────────┘   │ └──┘ Hi! I'm a     ││
│  │                                        │ React specialist    ││
│  │ ┌──────────────────────────────────┐   │ with 5 completed   ││
│  │ │ APPLICANT                        │   │ milestones. I       ││
│  │ │                                  │   │ focus on production ││
│  │ │ ┌──┐ Jordan Lee                  │   │ performance and     ││
│  │ │ │JL│ Frontend Engineer           │   │ accessibility.      ││
│  │ │ └──┘ Austin · CST               │   │ Here's my latest    ││
│  │ │                                  │   │ project: [link]     ││
│  │ │ 5 milestones · 100% acceptance   │   │                     ││
│  │ │                                  │   │ ─────────────────── ││
│  │ │ [View Full Profile →]            │   │                     ││
│  │ └──────────────────────────────────┘   │ ┌──┐ Marcus · 2h   ││
│  │                                        │ │MC│                ││
│  │ ┌──────────────────────────────────┐   │ └──┘ Thanks Jordan!││
│  │ │ SERVICES OFFERED                 │   │ Your milestones     ││
│  │ │                                  │   │ look solid.         ││
│  │ │ [🔴 Frontend — Expert]           │   │ Question: how do    ││
│  │ │ [🧪 Testing — Advanced]          │   │ you handle state    ││
│  │ │                                  │   │ management in       ││
│  │ └──────────────────────────────────┘   │ large apps?         ││
│  │                                        │                     ││
│  │ ┌──────────────────────────────────┐   │ ─────────────────── ││
│  │ │ APPLICATION ANSWERS              │   │                     ││
│  │ │                                  │   │                     ││
│  │ │ Q: Share a React app you're      │   │                     ││
│  │ │    proud of                      │   │                     ││
│  │ │ A: "Built the merchant dashboard │   │                     ││
│  │ │    for [company], handling 50K   │   │                     ││
│  │ │    daily users with <100ms..."   │   │                     ││
│  │ │                                  │   │                     ││
│  │ │ Q: How do you handle component   │   │                     ││
│  │ │    performance?                  │   │                     ││
│  │ │ A: "React.memo selectively,      │   │                     ││
│  │ │    virtualization for lists..."  │   │                     ││
│  │ └──────────────────────────────────┘   │ ─────────────────── ││
│  │                                        │ ┌──────────────────┐││
│  │ ┌──────────────────────────────────┐   │ │ Type reply...    │││
│  │ │ TEAM LEAD ACTIONS                │   │ │                  │││
│  │ │                                  │   │ │          [Send]  │││
│  │ │ [Request More Info]              │   │ └──────────────────┘││
│  │ │ [Propose Trial]                  │   │                     ││
│  │ │ [Accept into Team]              │   │                     ││
│  │ │ [Decline]                        │   │                     ││
│  │ │                                  │   │                     ││
│  │ └──────────────────────────────────┘   │                     ││
│  │                                        │                     ││
│  └────────────────────────────────────────┴─────────────────────┘│
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 6. Messages Inbox

**Route:** `network.produs.com/messages`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Messages                                                        │
│                                                                  │
│  ┌──────────────────────┬───────────────────────────────────────┐│
│  │ THREADS              │ CONVERSATION                          ││
│  │                      │                                       ││
│  │ 🔍 Search threads... │ ┌─────────────────────────────────┐  ││
│  │                      │ │ Context: Team Opening            │  ││
│  │ ┌──────────────────┐ │ │ "Frontend Specialist" at         │  ││
│  │ │ ┌──┐ CloudStack  │ │ │ CloudStack Crew                  │  ││
│  │ │ │CS│ Re: Your     │ │ └─────────────────────────────────┘  ││
│  │ │ └──┘ application  │ │                                       ││
│  │ │ "Thanks! Your..." │ │                                       ││
│  │ │ 2h ago    ●       │ │ ┌──┐ You · Yesterday 3:15pm         ││
│  │ │ ⊟ Team Opening    │ │ │JL│                                 ││
│  │ └──────────────────┘ │ │ └──┘ Hi! I saw your team opening    ││
│  │                      │ │ for a Frontend Specialist. I have    ││
│  │ ┌──────────────────┐ │ │ 5 completed milestones and focus    ││
│  │ │ ┌──┐ Sarah Kim   │ │ │ on production React with Next.js.   ││
│  │ │ │SK│ "Would love  │ │ │                                     ││
│  │ │ └──┘  to chat..." │ │ │ My latest delivery was the         ││
│  │ │ 1d ago            │ │ │ merchant dashboard for [company].   ││
│  │ │ ⊟ Expert Profile  │ │ │                                     ││
│  │ └──────────────────┘ │ │ ─────────────────────────────────── ││
│  │                      │ │                                       ││
│  │ ┌──────────────────┐ │ │ ┌──┐ Marcus (CloudStack) · 2h ago  ││
│  │ │ ┌──┐ Priya S.    │ │ │ │MC│                                 ││
│  │ │ │PS│ "Thanks for  │ │ │ └──┘ Thanks! Your milestones look  ││
│  │ │ └──┘  the info"  │ │ │ solid. We've been doing 2-3         ││
│  │ │ 3d ago            │ │ │ packages/month and consistently     ││
│  │ │ ⊟ Formation Post  │ │ │ need frontend work.                 ││
│  │ └──────────────────┘ │ │                                       ││
│  │                      │ │ Question: how do you approach         ││
│  │ ┌──────────────────┐ │ │ component architecture for a new     ││
│  │ │ ┌──┐ Alex R.     │ │ │ codebase you've never seen?          ││
│  │ │ │AR│ "Sounds     │ │ │                                       ││
│  │ │ └──┘  great!"    │ │ │ ─────────────────────────────────── ││
│  │ │ 5d ago            │ │ │                                       ││
│  │ │ ⊟ Trial Collab    │ │ │ ✓✓ Read                              ││
│  │ └──────────────────┘ │ │                                       ││
│  │                      │ │ ┌─────────────────────────────────┐  ││
│  │                      │ │ │ Type a message...         [Send]│  ││
│  │                      │ │ └─────────────────────────────────┘  ││
│  │                      │ │                                       ││
│  │                      │ │ [Report] [Mute] [Block]              ││
│  └──────────────────────┴───────────────────────────────────────┘│
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Thread list item anatomy:**

- Avatar (32px)
- Sender name (bold if unread)
- Last message preview (truncated, muted color)
- Timestamp (right-aligned, muted)
- Unread dot (indigo, right side)
- Scope badge (small pill at bottom: "Team Opening", "Expert Profile", etc.)

---

### 7. Service Channels

**Route:** `network.produs.com/channels`

**Channel list view:**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Channels                                                        │
│  Professional discussion by service category                     │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ YOUR CHANNELS                                            │   │
│  │                                                          │   │
│  │  🟣 #security            3 new posts    Last: 1h ago     │   │
│  │  🔵 #ci-cd              1 new post     Last: 3h ago     │   │
│  │  🌊 #cloud-infra        5 new posts    Last: 30m ago    │   │
│  │  📢 #introductions      2 new posts    Last: 2h ago     │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ ALL CHANNELS                                             │   │
│  │                                                          │   │
│  │  📢 #introductions      New experts introduce themselves │   │
│  │  🤝 #team-formation     Team building and partnerships   │   │
│  │  🟣 #security           Security hardening & audits      │   │
│  │  🔵 #ci-cd              Pipelines & automation           │   │
│  │  🌊 #cloud-infra        AWS, GCP, Kubernetes             │   │
│  │  🟢 #monitoring         APM, alerting, observability     │   │
│  │  🔴 #frontend           UI/UX, React, performance        │   │
│  │  🟠 #backend            APIs, databases, architecture    │   │
│  │  📝 #documentation      Technical writing, runbooks       │   │
│  │  🧪 #testing            QA, automation, load testing      │   │
│  │  💡 #delivery-practices Tips from completed packages     │   │
│  │  📣 #platform-updates   ProdUS announcements             │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Channel detail view (inside a channel):**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Channels    🟣 #security                    [+ New Post]      │
│  Security hardening, audits, and vulnerability management        │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ┌────┐ Sarah Kim · 1h ago                               │   │
│  │  │ SK │                                                   │   │
│  │  └────┘ JWT Rotation in Microservices                     │   │
│  │                                                          │   │
│  │  Has anyone dealt with JWT rotation in a microservices    │   │
│  │  setup where services hold long-lived gRPC connections?   │   │
│  │                                                          │   │
│  │  The challenge is that when you rotate the signing key,   │   │
│  │  existing connections still hold tokens signed with the   │   │
│  │  old key. Immediate invalidation causes cascading         │   │
│  │  failures...                                              │   │
│  │                                                          │   │
│  │  [🟣 Security] [Backend]                                  │   │
│  │                                                          │   │
│  │  ♡ 4 helpful    💬 6 replies    Last reply: 12m ago       │   │
│  │                                                          │   │
│  │  [Read replies →]                                         │   │
│  │                                                          │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │                                                          │   │
│  │  ┌────┐ Marcus Chen · 5h ago                             │   │
│  │  │ MC │                                                   │   │
│  │  └────┘ npm audit signatures for CI                       │   │
│  │                                                          │   │
│  │  Quick tip: npm audit signatures now checks provenance.   │   │
│  │  Add this to your CI security stage for dependency        │   │
│  │  verification beyond just vulnerability scanning.         │   │
│  │                                                          │   │
│  │  ```                                                      │   │
│  │  npm audit signatures --registry=https://registry...      │   │
│  │  ```                                                      │   │
│  │                                                          │   │
│  │  [🟣 Security] [🔵 CI/CD]                                │   │
│  │                                                          │   │
│  │  ♡ 7 helpful    💬 2 replies                              │   │
│  │                                                          │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │                                                          │   │
│  │  ┌────┐ Priya Sharma · 8h ago                            │   │
│  │  │ PS │                                                   │   │
│  │  └────┘ Secrets scanning in monorepos                     │   │
│  │                                                          │   │
│  │  Working on a package where the owner has a monorepo      │   │
│  │  with 12 services. What's the best approach for...        │   │
│  │                                                          │   │
│  │  [Read more →]                                            │   │
│  │                                                          │   │
│  │  ♡ 2 helpful    💬 4 replies                              │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Post detail with replies (expanded):**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← #security                                                     │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ┌────┐ Sarah Kim · 1h ago                    [Report]   │   │
│  │  │ SK │ Full-stack Security Engineer                      │   │
│  │  └────┘                                                   │   │
│  │                                                          │   │
│  │  JWT Rotation in Microservices                            │   │
│  │                                                          │   │
│  │  Has anyone dealt with JWT rotation in a microservices    │   │
│  │  setup where services hold long-lived gRPC connections?   │   │
│  │                                                          │   │
│  │  The challenge is that when you rotate the signing key,   │   │
│  │  existing connections still hold tokens signed with the   │   │
│  │  old key. Immediate invalidation causes cascading         │   │
│  │  failures. Graceful rotation needs:                       │   │
│  │                                                          │   │
│  │  1. Old key remains valid for a window                    │   │
│  │  2. New tokens issued with new key                        │   │
│  │  3. Services gradually receive new tokens                 │   │
│  │  4. Old key revoked after window expires                  │   │
│  │                                                          │   │
│  │  But what about services that hold connection pools       │   │
│  │  and don't re-authenticate for hours?                     │   │
│  │                                                          │   │
│  │  [🟣 Security] [Backend]                                  │   │
│  │                                                          │   │
│  │  ♡ 4 helpful                                              │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  6 REPLIES                                                       │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  ┌──┐ Marcus Chen · 45m ago               [♡ Helpful]    │   │
│  │  │MC│                                                     │   │
│  │  └──┘ We hit this exact problem at scale. The solution    │   │
│  │  was a JWKS endpoint that serves both keys during the     │   │
│  │  rotation window. Each service polls JWKS every 5min...   │   │
│  │                                                          │   │
│  │                                              [Reply]      │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │  ┌──┐ Raj Patel · 30m ago                 [♡ Helpful]    │   │
│  │  │RP│                                                     │   │
│  │  └──┘ Adding to Marcus's point: for gRPC specifically,    │   │
│  │  look at token interceptors that refresh proactively      │   │
│  │  before expiry rather than waiting for rejection...       │   │
│  │                                                          │   │
│  │                                              [Reply]      │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Add a reply...                                            │   │
│  │ ┌────────────────────────────────────────────────────┐   │   │
│  │ │                                                    │   │   │
│  │ │                                          [Post]    │   │   │
│  │ └────────────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 8. Trial Collaboration

**Route:** `network.produs.com/trials/{id}`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ← Trials                                                        │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ TRIAL COLLABORATION                                      │   │
│  │                                                          │   │
│  │ "Full-Stack Productization — E-commerce Platform"        │   │
│  │                                                          │   │
│  │ Status: ◉ ACTIVE                    Started: 3 May 2026  │   │
│  │ Package: PKG-0042                   Due: 17 May 2026     │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ PARTICIPANTS                                             │   │
│  │                                                          │   │
│  │ ┌────────────────────────────┐ ┌────────────────────────┐│   │
│  │ │ ┌──┐ Sarah Kim            │ │ ┌──┐ Jordan Lee        ││   │
│  │ │ │SK│ Security + CI/CD     │ │ │JL│ Frontend + Testing ││   │
│  │ │ └──┘                      │ │ └──┘                    ││   │
│  │ │                           │ │                          ││   │
│  │ │ Split: 55%                │ │ Split: 45%              ││   │
│  │ │ Role: Infra Lead          │ │ Role: Frontend Lead     ││   │
│  │ │                           │ │                          ││   │
│  │ └────────────────────────────┘ └────────────────────────┘│   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ MILESTONE ASSIGNMENTS                                    │   │
│  │                                                          │   │
│  │ ┌──────────────────────────────────────────────────────┐ │   │
│  │ │ Sarah                                                │ │   │
│  │ │ ✓ M1: Security Audit           Accepted · 5 May      │ │   │
│  │ │ ◷ M3: CI/CD Pipeline           In Progress           │ │   │
│  │ └──────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │ ┌──────────────────────────────────────────────────────┐ │   │
│  │ │ Jordan                                               │ │   │
│  │ │ ◷ M2: Frontend Rebuild         In Progress           │ │   │
│  │ │ ○ M4: Documentation            Not Started           │ │   │
│  │ └──────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  │ ┌──────────────────────────────────────────────────────┐ │   │
│  │ │ Shared                                               │ │   │
│  │ │ ○ M5: Integration Testing       Not Started          │ │   │
│  │ └──────────────────────────────────────────────────────┘ │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ PROGRESS                                                 │   │
│  │                                                          │   │
│  │ ████████████░░░░░░░░░░░ 40%   2/5 milestones             │   │
│  │                                                          │   │
│  │ Budget: $4,200                                           │   │
│  │ Released: $1,680 (40%)                                   │   │
│  │ Pending: $2,520                                          │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ TRIAL THREAD                                [View Full]  │   │
│  │                                                          │   │
│  │ Sarah · Today 2pm                                        │   │
│  │ "M3 pipeline config is in PR. Can you check the          │   │
│  │  frontend build stage integrates properly?"              │   │
│  │                                                          │   │
│  │ Jordan · Today 2:30pm                                    │   │
│  │ "Checked — looks good. I'll add the Cypress stage        │   │
│  │  for E2E tests in that same PR."                        │   │
│  │                                                          │   │
│  │ ┌────────────────────────────────────────────────────┐  │   │
│  │ │ Reply...                                    [Send] │  │   │
│  │ └────────────────────────────────────────────────────┘  │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

**Trial Completion + Team Formation Prompt:**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  🎉 Trial Complete                                       │   │
│  │                                                          │   │
│  │  All milestones delivered and accepted.                   │   │
│  │  Package PKG-0042 complete.                               │   │
│  │                                                          │   │
│  │  ────────────────────────────────────────────────────    │   │
│  │                                                          │   │
│  │  COLLABORATION FEEDBACK                                   │   │
│  │                                                          │   │
│  │  Would you work with Jordan again?                        │   │
│  │  ◉ Yes   ○ Maybe   ○ No                                  │   │
│  │                                                          │   │
│  │  Communication quality          ★★★★★                    │   │
│  │  Delivery reliability           ★★★★★                    │   │
│  │  Scope clarity                  ★★★★☆                    │   │
│  │                                                          │   │
│  │  Private note (optional, not shared):                     │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ Great collaboration. Jordan communicated clearly  │   │   │
│  │  │ and delivered ahead of schedule.                  │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │                                     [Submit Feedback]     │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ✨ FORM A TEAM?                                         │   │
│  │                                                          │   │
│  │  Both participants rated "would work together again."     │   │
│  │                                                          │   │
│  │  Trial results:                                           │   │
│  │  • Delivery: On time                                      │   │
│  │  • Acceptance: All milestones first submission            │   │
│  │  • Communication: Both rated 5/5                          │   │
│  │  • Combined services: Security + CI/CD + Frontend + QA   │   │
│  │                                                          │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │                                                  │   │   │
│  │  │  Suggested team name: _________________________  │   │   │
│  │  │                                                  │   │   │
│  │  │  Services:                                       │   │   │
│  │  │  [🟣 Security ✓] [🔵 CI/CD ✓] [🔴 Frontend ✓]  │   │   │
│  │  │  [🧪 Testing ✓]                                 │   │   │
│  │  │                                                  │   │   │
│  │  │  Roles:                                          │   │   │
│  │  │  Sarah: Infra Lead (55%)                         │   │   │
│  │  │  Jordan: Frontend Lead (45%)                     │   │   │
│  │  │                                                  │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │              [Maybe Later]  [Create Team →]               │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 9. Team Profile

**Route:** `network.produs.com/teams/{id}`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │
│  │░░░░░░░░░░░░░░░░ TEAM COVER PHOTO ░░░░░░░░░░░░░░░░░░░░░░│   │
│  │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░│   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  ┌────┐  CloudStack Crew                                 │   │
│  │  │ CS │  Full-stack productization delivery team          │   │
│  │  └────┘  London + Berlin · GMT/CET · Available           │   │
│  │                                                          │   │
│  │  3 members · 23 packages · $67K earned                    │   │
│  │                                                          │   │
│  │                    [Message Team]  [View Openings (2)]    │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ ABOUT                                                    │   │
│  │                                                          │   │
│  │ We take products from "built" to "production-ready."     │   │
│  │ Security, infrastructure, CI/CD, frontend, and           │   │
│  │ documentation — we handle the full stack of              │   │
│  │ productization work as a coordinated team.               │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ MEMBERS                                                  │   │
│  │                                                          │   │
│  │ ┌──┐ Sarah Kim          Lead · Security + CI/CD          │   │
│  │ │SK│ 7 milestones · 100% acceptance                      │   │
│  │ └──┘                                        [Profile →]  │   │
│  │                                                          │   │
│  │ ┌──┐ Marcus Chen        Specialist · Cloud + Monitoring  │   │
│  │ │MC│ 12 milestones · 97% acceptance                      │   │
│  │ └──┘                                        [Profile →]  │   │
│  │                                                          │   │
│  │ ┌──┐ Jordan Lee         Specialist · Frontend + Testing  │   │
│  │ │JL│ 5 milestones · 100% acceptance                      │   │
│  │ └──┘                                        [Profile →]  │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌────────────────────────────┐ ┌───────────────────────────┐   │
│  │ SERVICES                   │ │ DELIVERY STATS            │   │
│  │                            │ │                           │   │
│  │ 🟣 Security Hardening      │ │  ┌─────┐  ┌─────┐       │   │
│  │ 🔵 CI/CD Pipeline          │ │  │  23 │  │ 96% │       │   │
│  │ 🌊 Cloud Deployment        │ │  │pack-│  │acce-│       │   │
│  │ 🟢 Monitoring Setup        │ │  │ages │  │ptance│      │   │
│  │ 🔴 Frontend Development    │ │  └─────┘  └─────┘       │   │
│  │ 🧪 Testing & QA            │ │                           │   │
│  │ 📝 Documentation           │ │  ┌─────┐  ┌─────┐       │   │
│  │                            │ │  │ 4.2d│  │$67K │       │   │
│  │                            │ │  │ avg │  │total│       │   │
│  │                            │ │  │deliv│  │earned│      │   │
│  │                            │ │  └─────┘  └─────┘       │   │
│  └────────────────────────────┘ └───────────────────────────┘   │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ OPEN ROLES                                               │   │
│  │                                                          │   │
│  │ ┌────────────────────────────────────────────────────┐  │   │
│  │ │ Documentation Specialist                            │  │   │
│  │ │ Need: Technical writing, runbooks, API docs         │  │   │
│  │ │ Posted: 2d ago · Expires: 28d                       │  │   │
│  │ │                                    [View] [Apply]   │  │   │
│  │ └────────────────────────────────────────────────────┘  │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 10. Admin Moderation

**Route:** `network.produs.com/admin/moderation`

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  Moderation                                                      │
│                                                                  │
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐         │
│  │ Open Reports  │ │ Flagged Users │ │ Auto-detected │         │
│  │     4         │ │     1         │ │     2         │         │
│  │ 2 high priority│ │               │ │ Spam signals  │         │
│  └───────────────┘ └───────────────┘ └───────────────┘         │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ [Open (4)]  [Reviewing]  [Resolved]  [Dismissed]         │   │
│  ├──────────────────────────────────────────────────────────┤   │
│  │                                                          │   │
│  │ ┌────────────────────────────────────────────────────┐  │   │
│  │ │ ⚠ HIGH    Report #127          Submitted: 2h ago   │  │   │
│  │ │                                                    │  │   │
│  │ │ Type: Message                                      │  │   │
│  │ │ Reported by: Sarah Kim                             │  │   │
│  │ │ Target: Alex Rivera (message in join request #45)  │  │   │
│  │ │ Reason: Off-platform solicitation                  │  │   │
│  │ │                                                    │  │   │
│  │ │ "Hey, let's skip the platform and work directly.   │  │   │
│  │ │  I'll charge you less. DM me on [external]..."    │  │   │
│  │ │                                                    │  │   │
│  │ │ [View Context] [Hide Message] [Warn User]          │  │   │
│  │ │ [Suspend User] [Dismiss]                           │  │   │
│  │ └────────────────────────────────────────────────────┘  │   │
│  │                                                          │   │
│  │ ┌────────────────────────────────────────────────────┐  │   │
│  │ │ ● NORMAL   Report #126         Submitted: 5h ago   │  │   │
│  │ │                                                    │  │   │
│  │ │ Type: Channel Post                                 │  │   │
│  │ │ Reported by: Marcus Chen                           │  │   │
│  │ │ Target: Post in #security by New_User_42           │  │   │
│  │ │ Reason: Spam / self-promotion                      │  │   │
│  │ │                                                    │  │   │
│  │ │ "Check out my amazing course on [link] only $49!"  │  │   │
│  │ │                                                    │  │   │
│  │ │ 🤖 AI assessment: 95% spam confidence              │  │   │
│  │ │                                                    │  │   │
│  │ │ [View Context] [Hide Post] [Warn User]             │  │   │
│  │ │ [Suspend User] [Dismiss]                           │  │   │
│  │ └────────────────────────────────────────────────────┘  │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

### 11. #introductions Channel (New Expert Onboarding)

**Route:** `network.produs.com/channels/introductions`

**New expert prompt (shown once on first visit):**

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                                                          │   │
│  │  👋 Introduce yourself to the community                  │   │
│  │                                                          │   │
│  │  This is how other experts discover you. A good intro    │   │
│  │  helps potential teammates understand your experience    │   │
│  │  and what you're looking for.                            │   │
│  │                                                          │   │
│  │  ┌──────────────────────────────────────────────────┐   │   │
│  │  │ Hey everyone 👋                                  │   │   │
│  │  │                                                  │   │   │
│  │  │ I'm [name], a [role] based in [location].        │   │   │
│  │  │                                                  │   │   │
│  │  │ Stack: [your technologies]                       │   │   │
│  │  │ Services: [what you deliver]                     │   │   │
│  │  │ Previously: [brief background]                   │   │   │
│  │  │ Looking for: [team type / collaboration style]   │   │   │
│  │  │                                                  │   │   │
│  │  │ Happy to connect with anyone doing [area].       │   │   │
│  │  │                                          [Post]  │   │   │
│  │  └──────────────────────────────────────────────────┘   │   │
│  │                                                          │   │
│  │  💡 Tip: Mention your services and what kind of team     │   │
│  │     you'd like to join — it helps others reach out.     │   │
│  │                                                          │   │
│  │                                      [Skip for Now]      │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Empty States

### No Messages

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│           ┌─────────────────┐                           │
│           │    ✉            │                           │
│           │   (envelope)     │                           │
│           └─────────────────┘                           │
│                                                          │
│           No messages yet                                │
│                                                          │
│           Start a conversation from an expert            │
│           profile, team opening, or formation            │
│           board post.                                    │
│                                                          │
│           [Browse Directory]  [View Board]               │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### No Join Requests (Team Lead)

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│           ┌─────────────────┐                           │
│           │    📋           │                           │
│           │  (clipboard)     │                           │
│           └─────────────────┘                           │
│                                                          │
│           No join requests yet                           │
│                                                          │
│           Publish a team opening to attract              │
│           qualified experts to your team.                │
│                                                          │
│           [Create Team Opening]                          │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### Incomplete Profile

```
┌──────────────────────────────────────────────────────────┐
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Complete your profile to get discovered           │   │
│  │                                                  │   │
│  │ ████████░░░░░░░░░░░░ 45%                         │   │
│  │                                                  │   │
│  │ Missing:                                         │   │
│  │ ○ Add a profile photo                            │   │
│  │ ○ Write your bio                                 │   │
│  │ ○ Add at least 2 services                        │   │
│  │ ○ Connect your GitHub                            │   │
│  │ ✓ Set availability                               │   │
│  │ ✓ Add tech stack                                 │   │
│  │                                                  │   │
│  │                          [Complete Profile →]    │   │
│  └──────────────────────────────────────────────────┘   │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## Mobile Layouts

### Mobile Navigation (Bottom tabs)

```
┌──────────────────────────────────────┐
│                                      │
│  (Page content)                      │
│                                      │
│                                      │
│                                      │
├──────────────────────────────────────┤
│                                      │
│  🏠      🔍     📋      ✉     👤    │
│  Home  Directory Board  Messages Me  │
│                                      │
└──────────────────────────────────────┘
```

### Mobile Expert Card (Directory)

```
┌──────────────────────────────────────┐
│                                      │
│  ┌──┐ Sarah Kim            ● Avail  │
│  │SK│ Security Engineer              │
│  └──┘ London · GMT                   │
│                                      │
│  [🟣 Security] [🔵 CI/CD] [🌊 Cloud]│
│                                      │
│  7 milestones · 100% · 3.8d         │
│                                      │
│  ★ Looking for team                  │
│                                      │
│  [View]  [Message]  [Invite]         │
│                                      │
└──────────────────────────────────────┘
```

### Mobile Message Thread

```
┌──────────────────────────────────────┐
│ ← CloudStack Crew                    │
│ Context: Team Opening                │
├──────────────────────────────────────┤
│                                      │
│ You · Yesterday                      │
│ ┌────────────────────────────────┐  │
│ │ Hi! I saw your opening for     │  │
│ │ Frontend. I have 5 milestones  │  │
│ │ and focus on production React. │  │
│ └────────────────────────────────┘  │
│                                      │
│              Marcus · 2h ago         │
│  ┌────────────────────────────────┐ │
│  │ Thanks! Your milestones look  │ │
│  │ solid. Question: how do you   │ │
│  │ approach component arch...    │ │
│  └────────────────────────────────┘ │
│                                      │
│                                      │
│                                      │
├──────────────────────────────────────┤
│ ┌────────────────────────────┐ [⬆] │
│ │ Type a message...          │      │
│ └────────────────────────────┘      │
└──────────────────────────────────────┘
```

---

## Interaction Patterns

### Card hover

```
Default:  border: 1px solid #e2e8f0
          box-shadow: 0 1px 3px rgba(0,0,0,0.04)

Hover:    border: 1px solid #c7d2fe (soft indigo)
          box-shadow: 0 4px 12px rgba(0,0,0,0.06)
          transform: translateY(-1px)
          transition: all 0.15s ease
```

### Status transitions (join request, trial)

When status changes, show a brief toast notification and animate the status badge:

```
┌──────────────────────────────────────────────┐
│ ✓ Request accepted! Welcome to CloudStack.   │  ← Toast (top-right, 4s)
└──────────────────────────────────────────────┘
```

### Loading states

Use skeleton cards matching the card layout:

```
┌────────────────────────────────────────────────┐
│ ┌──┐ ░░░░░░░░░░░░░░░░         ░░░░░           │
│ │░░│ ░░░░░░░░░░░░░░░░░░░░░░                   │
│ └──┘ ░░░░░░░░░░░░                              │
│                                                │
│ [░░░░░░░] [░░░░░] [░░░░░░░░]                  │
│                                                │
│ ░░░░░░░░░░ · ░░░░ · ░░░░                      │
└────────────────────────────────────────────────┘
```

### Confirmation dialogs (destructive actions)

```
┌──────────────────────────────────────────────┐
│                                              │
│  Decline join request?                       │
│                                              │
│  Jordan Lee's request to join CloudStack     │
│  Crew will be declined. They will be         │
│  notified.                                   │
│                                              │
│  Reason (shared with applicant):             │
│  ┌────────────────────────────────────────┐ │
│  │                                        │ │
│  └────────────────────────────────────────┘ │
│                                              │
│                    [Cancel]  [Decline]        │
│                                              │
└──────────────────────────────────────────────┘
```

---

## Design Tokens Summary

```
RADIUS
  card: 8px
  button: 6px
  chip: 100px (pill)
  avatar: 50% (circle)
  input: 6px

ELEVATION
  card-rest: 0 1px 3px rgba(0,0,0,0.04)
  card-hover: 0 4px 12px rgba(0,0,0,0.06)
  dropdown: 0 8px 24px rgba(0,0,0,0.08)
  modal: 0 16px 48px rgba(0,0,0,0.12)
  toast: 0 4px 16px rgba(0,0,0,0.1)

MOTION
  fast: 0.1s ease
  normal: 0.15s ease
  slow: 0.25s ease
  page: 0.3s ease

BREAKPOINTS
  mobile: <640px
  tablet: 640-1024px
  desktop: >1024px

SIDEBAR
  width-expanded: 240px
  width-collapsed: 64px
  collapse-at: 1024px
  hide-at: 640px (use bottom nav)

AVATAR SIZES
  xs: 24px (inline mentions)
  sm: 32px (list items, thread list)
  md: 40px (cards, directory)
  lg: 64px (profile header)
  xl: 96px (profile page hero)
```
