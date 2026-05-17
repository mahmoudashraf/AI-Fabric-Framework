# teams.prodops.com — Landing Page Design

## Overview

A developer-focused landing page that converts laid-off and freelancing developers into ProdOps team registrations. Dark, premium aesthetic. Emotionally resonant copy that acknowledges the current job market while positioning ProdOps as the structured alternative to chaotic freelancing.

**Target audience:** Developers who were laid off due to AI/cuts, currently freelancing or looking for structured income.

**Primary conversion:** Email signup for early access / team creation.

**Tone:** Direct, empathetic, no-bullshit. Not corporate. Speaks developer-to-developer.

---

## Visual Identity

### Color Palette

| Role | Color | Usage |
|---|---|---|
| Background | `#0a0e1a` | Page base |
| Elevated background | `#111827` | Alternating sections |
| Card surface | `#1a2035` | Cards, containers |
| Border | `rgba(255,255,255,0.08)` | Card edges, dividers |
| Primary text | `#f1f5f9` | Headlines, body |
| Muted text | `#94a3b8` | Descriptions, secondary content |
| Dim text | `#64748b` | Labels, captions |
| Primary accent | `#6366f1` (indigo) | CTAs, highlights, steps |
| Success accent | `#34d399` (emerald) | Positive outcomes, earnings, acceptance |
| Warning accent | `#fbbf24` (amber) | Ratings, attention items |
| Danger accent | `#ef4444` (red) | Pain points, "before" states |
| Info accent | `#60a5fa` (blue) | Secondary highlights |

### Typography

- **Font:** Inter (Google Fonts) — technical, clean, modern
- **Hero headline:** 56-60px, weight 800, letter-spacing -0.02em
- **Section titles:** 40-44px, weight 800
- **Card titles:** 18px, weight 700
- **Body text:** 14-15px, weight 400, line-height 1.7
- **Labels:** 13px, weight 600, uppercase, tracking 0.1em, primary accent color
- **Badges:** 13px, weight 500, pill-shaped containers

### Design Language

- Dark theme with subtle glow effects (radial gradients behind key sections)
- Cards with 1px borders, 16px radius, subtle hover elevation
- Gradient accents on card top borders (2-3px colored lines)
- Floating "notification" elements to show platform activity (animated gentle float)
- Numbered step counters (circles with numbers)
- Comparison layouts (red "before" vs green "after")
- Horizontal earning bars for visual income comparison
- No stock photos — abstract data visualizations and UI previews only

---

## Page Structure

### Section 1: Navigation (Fixed)

```
[ P logo ] ProdOps          [How It Works]  [Earnings]  [FAQ]  [Create Your Team ←CTA]
```

- Fixed header with blur backdrop
- Transparent until scroll, then subtle dark background
- Logo: Indigo-to-green gradient square with "P" letter mark
- CTA button: Filled indigo, stands out from text links
- Mobile: Hamburger collapses links, CTA always visible

---

### Section 2: Hero (Full viewport height)

**Layout:** Two columns — copy left, visual right

**Left column:**

- **Badge** (pill): "Now accepting teams" with pulsing green dot
- **Headline:** "Stop chasing gigs. Start delivering packages." — second line in gradient text (indigo → emerald)
- **Subtitle:** "ProdOps gives developer teams pre-scoped productization packages with defined milestones, evidence-based acceptance, and a verified reputation that earns better work over time."
- **CTAs:** Two buttons — "Create Your Team" (filled gradient) + "See How It Works" (outlined)

**Right column — Team profile card preview:**

A mock team profile card showing what a successful team looks like on the platform:

```
┌────────────────────────────────────────┐
│ ●●● (gradient top border)              │
│                                        │
│ [SK][RM][JL]  CloudStack Crew          │
│               ★★★★★ 4.9               │
│                                        │
│ ┌──────────┐ ┌──────────┐             │
│ │ 23       │ │ 96%      │             │
│ │ Packages │ │ Accept   │             │
│ └──────────┘ └──────────┘             │
│ ┌──────────┐ ┌──────────┐             │
│ │ $67K     │ │ 4.2d     │             │
│ │ Earned   │ │ Avg time │             │
│ └──────────┘ └──────────┘             │
│                                        │
│ [Node.js] [React] [AWS] [Docker]       │
│ [CI/CD] [Security]                     │
│                                        │
│ ✓ Matched to 2 new packages this week  │
└────────────────────────────────────────┘
```

**Floating elements (animated, offset from card):**

- Top-right: Notification card — "✓ Milestone accepted / Security Audit — verified"
- Bottom-left: Payment card — "Payment released / $4,200"

**Background:** Subtle radial glow (indigo top-right, emerald bottom-left)

---

### Section 3: Pain Points

**Section label:** "The problem"
**Headline:** "Freelancing after layoff shouldn't feel like this."
**Subtitle:** "You have production experience. But the freelance world makes you fight for every dollar with no structure, no protection, and no proof of quality."

**Grid:** 3x2 cards, each with:
- Red gradient top border (2px)
- Emoji icon (large)
- Bold title
- 1-2 sentence description in muted text
- Hover: Red-tinted border, slight lift

**Cards:**

| # | Title | Description |
|---|---|---|
| 1 | Vague briefs, endless scope creep | Client says "build me a website" and 3 months later you're debugging their legacy auth system for the original fee. |
| 2 | Race to the bottom on price | Competing with thousands of devs on price alone. Your 10 years of experience means nothing when someone bids $15/hr. |
| 3 | "Is it done?" arguments | You deliver good work. Client disagrees. No objective criteria. Disputes drag on. Payment held. |
| 4 | Portfolio is self-reported | You SAY you did great security work. But there's no verification. Every new client starts from zero trust. |
| 5 | Solo means small jobs | Can't take the $10K+ packages because you're one person. Bigger work goes to agencies charging 4x your rate. |
| 6 | AI is the new threat | Clients think AI can do your job. You know it can't ship production code. But you have no way to prove the difference. |

---

### Section 4: Solution (Elevated background)

**Section label:** "The ProdOps way"
**Headline:** "Structured packages. Clear scope. Evidence-based delivery."
**Subtitle:** "Every package has defined milestones, acceptance criteria, and automated verification. You know exactly what to deliver and get paid when evidence confirms it's done."

**Grid:** 4 cards, each with:
- Unique colored top border (indigo, green, amber, blue)
- Matching colored icon background (48px rounded square)
- Title + description

**Cards:**

| # | Color | Title | Description |
|---|---|---|---|
| 1 | Indigo | Pre-scoped packages | AI diagnoses the owner's product first. By the time you see it, the work is defined: services needed, dependencies mapped, timeline estimated. No more guessing. |
| 2 | Green | Evidence-based acceptance | Milestones have measurable criteria. "CI pipeline passing" not "looks good." Automated checks verify your delivery. No more subjective disputes. |
| 3 | Amber | Verified reputation | Every completed milestone adds to your team's verified track record. Not reviews — evidence. Stack verified, delivery speed measured, acceptance rate tracked. |
| 4 | Blue | Team up, earn more | Form a team of 2-4 specialists. Take bigger packages (security + CI/CD + monitoring). Split delivery, multiply earnings. Platform handles coordination. |

---

### Section 5: How It Works (Steps)

**Section label:** "Getting started"
**Headline:** "From signup to first payment in days, not months."

**Grid:** 6 numbered step cards

Each card has:
- Numbered circle badge (indigo, positioned top-left, overlapping card border)
- Bold title
- 1-2 sentence description

**Steps:**

| # | Title | Description |
|---|---|---|
| 1 | Create your team | Sign up, connect your GitHub, verify your stack. Invite teammates or go solo initially. |
| 2 | Define your services | Select what you deliver: CI/CD, security, cloud deployment, monitoring, documentation. Set your capacity. |
| 3 | Get matched | AI matches you to packages based on stack fit, service expertise, and availability. Only qualified, well-scoped work. |
| 4 | Accept a package | Review the diagnosis, milestones, and budget. Accept if it fits. Decline with no penalty. |
| 5 | Deliver milestones | Work through milestones in your workspace. Submit evidence when done. Automated checks verify deliverables. |
| 6 | Get paid on acceptance | Owner reviews evidence, platform verifies criteria met, payment releases. Done means paid. |

---

### Section 6: Earnings Comparison (Elevated background)

**Section label:** "Earnings potential"
**Headline:** "Teams earn more because packages pay more than gigs."
**Subtitle:** "Structured productization packages command premium rates because the scope is clear, quality is governed, and both sides are protected."

**Layout:** Two columns

**Left column — Before/After comparison rows:**

| Metric | Before (red) | After (green) |
|---|---|---|
| Income | $50-80/hr freelance hourly | $3K-12K per package |
| Finding work | 2-3 weeks finding clients | AI-matched, packages come to you |
| Payment | Net 30-60 invoice | On milestone acceptance |

Each row: Red card → arrow → Green card

**Right column — Earnings bar chart:**

```
Monthly earnings potential:

Solo gigs        [████░░░░░░░░░░] $3-5K
Team (2-3)       [█████████░░░░░] $8-15K
ProdOps team     [████████████░░] $12-25K / team
```

- Bars are horizontal, with gradient fills
- Solo: gray
- Team generic: indigo
- ProdOps team: indigo-to-green gradient (longest bar)

---

### Section 7: Social Proof / Urgency

**Section label:** "Early access program"
**Headline:** "First 100 teams get priority matching."
**Subtitle:** "Teams who join during beta will be first in line when owners start posting packages."

**Stats grid (4 boxes, centered):**

| Stat | Label |
|---|---|
| 73 | Teams registered |
| 27 | Spots remaining |
| 8 | Service categories |
| $0 | Cost to join |

- Numbers in large gradient text (indigo → green)
- Labels in muted text below
- Cards with border, radius, subtle background

---

### Section 8: FAQ

**Section label:** "Questions"
**Headline:** "What teams ask before joining."

**Grid:** 2x3 cards

| Question | Answer |
|---|---|
| Do I need a team or can I join solo? | Start solo, take smaller packages. Form a team later for bigger work. Many find teammates on the platform after first delivery. |
| What's the platform fee? | Teams keep 85-92% depending on tier. No monthly sub. No fee until you earn. |
| What kind of work is it? | Productization packages: CI/CD, security, cloud deployment, monitoring, documentation, performance, testing. Real production infra work. |
| How is this different from Upwork/Toptal? | You never write a proposal. Work is pre-scoped by AI. Acceptance is evidence-based. Reputation is verified by data, not reviews. |
| What if the owner rejects unfairly? | Criteria defined upfront. Automated checks verify. Platform governs disputes with evidence. No "I don't like it" rejections. |
| Can I bring my own clients? | Yes. Owners can invite their existing dev as their team. You still get structured packages and verified portfolio growth. |

---

### Section 9: Final CTA (Full-width, glow background)

**Headline:** "Your skills are real. Now prove it with evidence."
**Subtitle:** "Create your team profile, verify your stack, and be first in line for matched productization packages. Free to join. First 100 teams get priority."

**Form:** Email input + "Join Early Access" button (inline on desktop, stacked on mobile)

**Note below form:** "Free to join. No credit card. We'll notify you when packages match your skills."

**Background:** Large radial indigo glow centered behind the content

---

### Section 10: Footer

```
ProdOps for Teams     [For Owners] [How It Works] [FAQ] [teams@prodops.com]     © 2026 ProdOps Ltd.
```

- Single row, border-top
- Minimal, links only
- Mobile: stacked center-aligned

---

## Interaction Details

### Hover States
- Cards: border color shifts toward accent, 2-3px vertical lift, subtle shadow increase
- Buttons: 2px lift, shadow intensity increases, slight scale
- Nav links: text color shifts from muted to white

### Animations
- Hero badge: Green dot pulses (opacity 1 → 0.4, 2s loop)
- Floating notifications: Gentle vertical float (8px, 3s ease-in-out)
- Stats numbers: Count-up animation on scroll into view
- Step numbers: Stagger-fade-in on scroll

### Mobile Adaptations
- Hero: Single column, hide visual card (copy + CTAs only)
- Pain grid: Single column stack
- Solution grid: Single column
- Steps: Single column
- Earnings: Stack columns vertically
- Stats: 2x2 grid → single column
- FAQ: Single column
- CTA form: Stack input and button vertically
- Nav: Hamburger menu, CTA button always visible

---

## Copy Strategy

### Key phrases to use:
- "Pre-scoped" (not vague)
- "Evidence-based" (not opinion-based)
- "Verified" (not self-reported)
- "Packages" (not gigs/jobs)
- "Milestones" (not deliverables/tasks)
- "Acceptance criteria" (not approval)
- "Your track record" (not your profile)

### Key phrases to avoid:
- "Gig economy" (feels cheap)
- "Freelance marketplace" (commodity positioning)
- "AI-powered" in developer-facing copy (they're sick of it — say "structured" and "evidence-based" instead)
- "Passive income" (not real, sounds scammy)
- "Be your own boss" (overused, hollow)
- "Disrupting" (eye-roll territory)

### Emotional arc of the page:
1. **Recognition** — "We see your situation" (badge: now accepting)
2. **Validation** — "The current options suck and it's not your fault" (pain section)
3. **Hope** — "There's a structured alternative" (solution section)
4. **Clarity** — "Here's exactly how it works" (steps)
5. **Desire** — "Here's what you could earn" (earnings)
6. **Urgency** — "Spots are limited" (social proof)
7. **Confidence** — "Here are answers to your concerns" (FAQ)
8. **Action** — "Join now, it's free" (CTA)

---

## SEO and Meta

- **Title:** "ProdOps for Teams | Structured work. Verified delivery. Real reputation."
- **Description:** "Join ProdOps as a developer team. Get pre-scoped productization packages, evidence-based delivery, and a verified track record that earns you more work."
- **URL:** teams.prodops.com
- **OG image:** Dark card with team profile preview + headline text
- **Keywords (organic):** developer teams, freelance alternative, structured delivery, verified developer portfolio, productization packages

---

## Performance Notes

- No JavaScript required for initial render (static HTML + CSS)
- Single font load (Inter, 400/500/600/700/800 weights)
- CSS animations only (no JS animation libraries)
- Images: none required (all UI elements are CSS/HTML)
- Target: <100KB total page weight, <1s paint on 3G
