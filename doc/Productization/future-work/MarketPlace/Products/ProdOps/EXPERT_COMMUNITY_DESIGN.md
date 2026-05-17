# ProdOps Expert Community — Feature Design

## Purpose

A structured community space within ProdOps where solo experts discover each other, build trust through visible activity, and form teams organically. Not a generic chat tool — a professional networking layer where every interaction contributes to platform-visible credibility.

The community exists to solve one problem: **solo experts don't know who to team up with, and have no way to evaluate each other before committing.**

---

## Design Principles

1. **Activity builds trust** — Every contribution (answering a question, sharing knowledge, completing a trial collaboration) becomes a visible signal. Not gamified badges — real credibility indicators.

2. **Team formation is the goal** — The community isn't social for the sake of social. Every feature nudges toward "find your people, form a team, take bigger packages."

3. **Lightweight by default** — No notification overload, no infinite scroll feeds, no "engagement" tricks. Experts are busy. Respect their time. Quality over volume.

4. **Platform-integrated** — Community activity feeds into matching intelligence. An expert who's consistently helpful in the Security channel signals expertise the AI can use when recommending team compositions.

5. **Professional tone** — This is a workspace, not a social network. No likes, no follower counts, no viral mechanics. Substance over performance.

---

## Core Features

### 1. Expert Profiles (Public Cards)

Every registered expert has a profile card visible to other experts on the platform.

**Profile card contents:**

```
┌────────────────────────────────────────────────┐
│                                                │
│  ┌──┐  Sarah Kim                              │
│  │SK│  Full-stack Security Engineer            │
│  └──┘  London, UK · Available now             │
│                                                │
│  ─────────────────────────────────────────     │
│                                                │
│  Stack                                         │
│  [Node.js] [Go] [AWS] [Terraform] [Docker]    │
│                                                │
│  Services                                      │
│  [Security Hardening] [CI/CD] [Cloud Deploy]   │
│                                                │
│  ─────────────────────────────────────────     │
│                                                │
│  Verified delivery                             │
│  7 milestones delivered · 100% acceptance      │
│  Avg delivery: 3.8 days                        │
│                                                │
│  Community activity                            │
│  Active in: Security, DevOps                   │
│  Contributions: 34 posts                       │
│  Helpfulness: High (peer-assessed)             │
│                                                │
│  ─────────────────────────────────────────     │
│                                                │
│  Looking for                                   │
│  "Frontend specialist to form full-stack       │
│   productization team. I handle infra +        │
│   security, need someone for UI/UX delivery."  │
│                                                │
│  [Message]  [Invite to Team]                   │
│                                                │
└────────────────────────────────────────────────┘
```

**Key sections:**

| Section | Source | Purpose |
|---|---|---|
| Identity | Self-provided (name, title, location, avatar) | Human recognition |
| Stack | Verified via GitHub + self-declared | Technical matching |
| Services | Selected from platform taxonomy | What they can deliver |
| Verified delivery | Platform data (milestones completed) | Earned trust |
| Community activity | Automated from channel participation | Engagement signal |
| Looking for | Free-text + structured tags | Team formation intent |

**Visibility controls:**
- Profile is visible to all registered experts by default
- Experts can hide "looking for" section if they're already in a team
- Delivery stats are always visible (earned, not optional)
- Location can be hidden (timezone shown instead)

---

### 2. Skill Channels

Topic-based discussion spaces organized by service category. Experts join channels relevant to their skills and interests.

**Channel structure:**

```
GENERAL
├── #introductions          — New experts introduce themselves
├── #team-formation         — "Looking for" posts and team invites
└── #platform-updates       — ProdOps announcements

SERVICE CHANNELS
├── #security               — Security hardening, audits, pentesting
├── #ci-cd                  — Pipelines, automation, deployment
├── #cloud-infrastructure   — AWS, GCP, Azure, Terraform, Kubernetes
├── #monitoring             — APM, alerting, observability
├── #frontend               — UI/UX, React, performance
├── #backend                — APIs, databases, architecture
├── #documentation          — Technical writing, runbooks, onboarding
└── #testing                — QA, automation, load testing

COMMUNITY
├── #wins                   — Celebrate completed packages, milestones
├── #help                   — Ask the community for advice on active work
└── #off-topic              — Non-work conversation (opt-in)
```

**Channel rules:**

- Anyone can read any channel
- Posting requires platform registration (verified email + GitHub connected)
- No anonymous posting
- Channels are text-only (no voice, no video — keep it async)
- Posts can include code snippets (syntax highlighted)
- No file uploads in channels (link to repos/gists instead)

**Channel post format:**

```
┌────────────────────────────────────────────────┐
│  #security                                     │
│                                                │
│  ┌──┐ Sarah Kim · 2h ago                      │
│  │SK│                                          │
│  └──┘ Has anyone dealt with JWT rotation in    │
│       a microservices setup where services     │
│       hold long-lived connections? I'm on a    │
│       package where the owner's auth is...     │
│                                                │
│       [Read more]                              │
│                                                │
│       3 replies · Last: 45m ago                │
│                                                │
│  ─────────────────────────────────────────     │
│                                                │
│  ┌──┐ Marcus Chen · 5h ago                    │
│  │MC│                                          │
│  └──┘ Quick tip for anyone doing dependency    │
│       audits: npm audit signatures now checks  │
│       provenance. Use it in your CI stage...   │
│                                                │
│       1 reply                                  │
│                                                │
└────────────────────────────────────────────────┘
```

**What channels are NOT:**
- Not real-time chat (async-first, like a forum with fast replies)
- Not a support channel for platform issues (separate support exists)
- Not a place to solicit work outside the platform
- Not a content feed (no algorithms, no "trending," chronological only)

---

### 3. Team Formation Board

A structured, searchable board specifically for team-building. Unlike free-form channel posts, board entries follow a template.

**Two post types:**

**"Looking for" (solo expert seeking teammates):**

```
┌────────────────────────────────────────────────┐
│  LOOKING FOR                                   │
│                                                │
│  ┌──┐ Sarah Kim · Security + DevOps           │
│  │SK│ London · Available full-time             │
│  └──┘                                          │
│                                                │
│  I bring:                                      │
│  [Security Hardening] [CI/CD] [Cloud Deploy]   │
│  7 milestones · 100% acceptance                │
│                                                │
│  I need:                                       │
│  [Frontend] [Documentation]                    │
│                                                │
│  Team vision:                                  │
│  "Full-stack productization team that can      │
│   handle complete owner packages end-to-end.   │
│   Looking for 1-2 people who want consistent   │
│   work, not one-off gigs."                     │
│                                                │
│  Preferred working style:                      │
│  Async · Timezone: GMT/GMT+1 · Long-term       │
│                                                │
│  [Message Sarah]  [View Profile]               │
│                                                │
└────────────────────────────────────────────────┘
```

**"Team recruiting" (existing team with an open slot):**

```
┌────────────────────────────────────────────────┐
│  TEAM RECRUITING                               │
│                                                │
│  CloudStack Crew (2 members)                   │
│  ┌──┐┌──┐                                     │
│  │SK││MC│ Sarah + Marcus                       │
│  └──┘└──┘                                     │
│                                                │
│  We have:                                      │
│  [Security] [CI/CD] [Backend] [Cloud]          │
│  12 packages completed together · $34K earned  │
│                                                │
│  We need:                                      │
│  [Frontend] — React/Next.js specialist         │
│                                                │
│  Why join us:                                  │
│  "Consistent package flow, great acceptance    │
│   rate, async-first. We handle infra, you      │
│   handle the UI layer of productization        │
│   packages. Even split."                       │
│                                                │
│  [Apply to Join]  [View Team Profile]          │
│                                                │
└────────────────────────────────────────────────┘
```

**Board features:**

- Filter by: skills needed, timezone, availability, team size
- Sort by: newest, most active, verified delivery score
- Expiry: Posts auto-archive after 30 days unless refreshed
- Match suggestions: AI highlights board posts that complement your profile ("You do Frontend — Sarah is looking for a Frontend person")

---

### 4. Direct Messages

Private 1:1 messaging between experts for team formation conversations.

**Rules:**
- Only registered, verified experts can message each other
- Messages are private (platform doesn't read content for AI training)
- No group DMs (use a team workspace for that — see below)
- Rate limited: max 10 new conversations per day (prevents spam/recruiting abuse)
- Recipient can block/report

**Message thread layout:**

```
┌────────────────────────────────────────────────┐
│  ← Back     Sarah Kim                    ●     │
│             Security Engineer · Online         │
├────────────────────────────────────────────────┤
│                                                │
│  You · 2:15pm                                  │
│  Hi Sarah, saw your post on the team board.    │
│  I'm a React/Next.js specialist with 4        │
│  completed frontend milestones. Would love     │
│  to chat about joining your team.              │
│                                                │
│  Sarah · 2:22pm                                │
│  Hey! Looked at your profile — your delivery   │
│  stats are solid. We're doing a lot of full    │
│  productization packages and always need       │
│  frontend. Want to try a package together      │
│  first before formalizing?                     │
│                                                │
│  You · 2:24pm                                  │
│  That makes sense. Trial collaboration first.  │
│  I'm available from next week.                 │
│                                                │
│  Sarah · 2:25pm                                │
│  Great. I'll send you a trial invite when      │
│  our next matched package includes frontend.   │
│                                                │
├────────────────────────────────────────────────┤
│  [Type a message...]              [Send]       │
└────────────────────────────────────────────────┘
```

**Conversation starters (templates for first message):**

When messaging from a board post or profile, the platform pre-fills context:

- "Hi [name], I saw your team board post. I specialize in [your services] and I'm interested in [their stated need]."
- "Hi [name], we're looking for a [skill] specialist. Your profile shows [relevant stat]. Would you be open to a trial package?"

These are editable suggestions — not mandatory.

---

### 5. Trial Collaboration

Before committing to a permanent team, two or more experts can "trial" working together on a single package.

**How it works:**

1. Expert A is matched to (or accepts) a package that needs skills they don't have alone
2. Expert A invites Expert B to collaborate on this specific package
3. Both work as a temporary unit — shared workspace, milestone assignments, split payment
4. After delivery, both rate the collaboration privately:
   - "Would you work together again?" (Yes / Maybe / No)
   - "Communication quality" (1-5)
   - "Delivery reliability" (1-5)
5. If both say "Yes" → platform suggests formalizing as a team
6. Trial data is private between the participants (not shown publicly)

**Trial workspace:**

```
┌────────────────────────────────────────────────┐
│  Trial: Sarah + Jordan                         │
│  Package: "Full-Stack Productization — E-comm" │
├────────────────────────────────────────────────┤
│                                                │
│  Milestone assignment:                         │
│                                                │
│  Sarah:                                        │
│  ✓ M1: Security audit                         │
│  ○ M3: CI/CD pipeline                         │
│                                                │
│  Jordan:                                       │
│  ◷ M2: Frontend rebuild                       │
│  ○ M4: Documentation                          │
│                                                │
│  Shared:                                       │
│  ○ M5: Integration testing + handoff           │
│                                                │
│  ─────────────────────────────────────────     │
│                                                │
│  Payment split: 55% Sarah / 45% Jordan         │
│  (agreed before starting)                      │
│                                                │
│  [Team Chat]  [View Package]                   │
│                                                │
└────────────────────────────────────────────────┘
```

**After trial completes:**

```
┌────────────────────────────────────────────────┐
│  Trial complete                                │
│                                                │
│  You and Sarah delivered successfully.         │
│  Package accepted. Payment released.           │
│                                                │
│  Both rated: "Would work together again" ✓     │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │ Form a team together?                    │ │
│  │                                          │ │
│  │ Based on this trial:                     │ │
│  │ • Delivery: On time                      │ │
│  │ • Acceptance: First submission           │ │
│  │ • Communication: Both rated 5/5          │ │
│  │                                          │ │
│  │ [Create Team]  [Maybe Later]             │ │
│  └──────────────────────────────────────────┘ │
│                                                │
└────────────────────────────────────────────────┘
```

---

### 6. Expert Directory (Browse/Search)

A searchable directory of all experts on the platform. Not a social feed — a professional lookup tool.

**Search and filter:**

```
┌────────────────────────────────────────────────┐
│  Expert Directory                              │
│                                                │
│  Search: [security specialist AWS_________]    │
│                                                │
│  Filters:                                      │
│  Skills: [Security ×] [AWS ×]  [+ Add]        │
│  Availability: [Available now ▼]               │
│  Timezone: [GMT-5 to GMT+1 ▼]                  │
│  Looking for team: [Yes ▼]                     │
│  Min deliveries: [3+ ▼]                        │
│                                                │
├────────────────────────────────────────────────┤
│                                                │
│  12 experts match                              │
│                                                │
│  ┌──────────────────┐ ┌──────────────────┐    │
│  │ Sarah Kim        │ │ Raj Patel        │    │
│  │ Security + DevOps│ │ Cloud + Security │    │
│  │ 7 delivered      │ │ 12 delivered     │    │
│  │ Looking for team │ │ Looking for team │    │
│  │ [View] [Message] │ │ [View] [Message] │    │
│  └──────────────────┘ └──────────────────┘    │
│                                                │
│  ┌──────────────────┐ ┌──────────────────┐    │
│  │ ...              │ │ ...              │    │
│  └──────────────────┘ └──────────────────┘    │
│                                                │
└────────────────────────────────────────────────┘
```

**AI-powered suggestions:**

When an expert views the directory, the platform shows:

- "Experts who complement your skills" — people whose services fill your gaps
- "Active in your channels" — people you've interacted with in discussions
- "Similar timezone" — practical collaboration match

These are subtle suggestions, not aggressive "You should connect!" prompts.

---

### 7. #introductions Channel (Onboarding ritual)

Every new expert is prompted to introduce themselves in #introductions when they join. This serves as the community entry point.

**Introduction template (suggested, not required):**

```
Hey everyone 👋

I'm [name], a [role] based in [location].

**Stack:** [technologies]
**Services I deliver:** [from platform taxonomy]
**Previously:** [brief background — company, years, notable work]
**Looking for:** [what kind of team/collaboration]
**Fun fact:** [optional, human touch]

Happy to connect with anyone doing [relevant area].
```

**Why this matters:**
- Experts see real humans, not just profile cards
- Creates a sense of community arrival
- Others can reply, welcome, and flag potential team fits
- Builds the first interaction that can lead to DMs and team formation

---

## Trust & Credibility System

### What builds visible credibility:

| Signal | Source | Visibility |
|---|---|---|
| Milestones delivered | Platform data (automatic) | Public on profile |
| Acceptance rate | Platform data (automatic) | Public on profile |
| Average delivery time | Platform data (automatic) | Public on profile |
| Channel contributions | Post count + replies (automatic) | Public on profile |
| Helpfulness | Peer-assessed (other experts mark posts as helpful) | Public on profile |
| Trial success | Trial collaboration outcomes | Private (only shown to potential teammates) |
| Team longevity | How long team has stayed together | Public on team profile |
| Stack verification | GitHub analysis (automatic) | Public badge on profile |

### What does NOT build credibility:

- Number of DMs sent (prevents spam incentive)
- Number of connections/followers (no social graph)
- Posting frequency alone (quality over volume)
- Self-reported endorsements
- Paid boosts or featured profiles

### Credibility tiers (earned, not purchased):

| Tier | Criteria | Benefit |
|---|---|---|
| New | Just registered | Can post, message, browse |
| Verified | GitHub connected + 1 milestone delivered | Profile badge, higher in directory |
| Established | 5+ milestones, 90%+ acceptance | Priority matching, can create team board posts |
| Expert | 15+ milestones, consistent high performance | Mentor tag, trial invite priority, platform advisory |

---

## AI Integration Points

### Community activity feeds into matching:

- Expert who's consistently helpful in #security gets weighted higher for security packages
- Expert who answers cloud questions frequently signals cloud depth beyond just "AWS" tag
- Expert who completes successful trials shows collaboration readiness

### AI-suggested team compositions:

When an expert posts on the team board, AI can suggest:

> "Based on your skills (Security, CI/CD) and the package types that match you, a team with a Frontend specialist and a Documentation writer would let you take full productization packages worth $8-15K."

### Conversation context for matching:

When matching a package to teams, AI can reference:

> "Sarah has discussed JWT rotation patterns 12 times in #security and delivered 3 auth-related milestones. High confidence match for this auth refactor package."

---

## Moderation

### Automated:
- Spam detection (repeated messages, external links, recruitment for non-platform work)
- Rate limiting (max posts per hour, max new DM conversations per day)
- Content filter (no harassment, no solicitation of work outside platform)

### Community-driven:
- Any expert can flag a post
- 3 flags from different experts = auto-hide pending review
- Flagged experts get a warning, then temporary mute, then removal

### Platform rules (displayed at registration):
1. No soliciting work outside ProdOps (this protects the community)
2. No spam, self-promotion, or recruitment for external companies
3. Be helpful — this is a professional community
4. No sharing confidential package/owner information in public channels
5. Respect async — no pressure to respond immediately

---

## Notification Strategy

### Default (new expert):
- DM received: Push + email
- Reply to your channel post: In-app only
- Team board match suggestion: In-app + email (weekly digest)
- Trial invite: Push + email
- Channel activity: None (opt-in)

### Expert controls:
- Per-channel mute
- DM mute (still receive, no notification)
- Digest frequency: real-time / daily / weekly
- "Focus mode" — mute everything except DMs and trial invites

### Platform never sends:
- "You haven't posted in 3 days!" engagement nudges
- "5 experts viewed your profile!" vanity notifications
- "Trending in #security!" algorithmic bait

---

## Mobile Experience

The community works on mobile as a native-feeling experience within the ProdOps app:

- **Bottom tabs:** Directory | Channels | Board | Messages | Profile
- **Channels:** Scrollable list, tap to open, reply inline
- **Directory:** Card grid with quick-filter chips at top
- **Messages:** Standard messaging interface
- **Board:** Swipeable cards (swipe right = message, left = skip)

---

## Metrics That Matter

### Health metrics (track these):
| Metric | Target | Why |
|---|---|---|
| Intro posts per week | Growing | New experts engaging |
| DMs that lead to trial | >15% of conversations | Community drives team formation |
| Trials that become teams | >40% | Trials are effective filter |
| Time from registration to first team | <21 days | Community accelerates formation |
| Channel posts per active expert | 2-4 per week | Healthy participation without burnout |
| Expert retention at 90 days | >70% | Community provides enough value to stay |

### Vanity metrics (ignore these):
- Total messages sent
- Daily active users
- Time spent in app
- Number of connections

---

## Launch Sequence

### Phase 1: Foundation (Week 1-2)
- Expert profiles live
- #introductions channel open
- Expert directory with search/filter
- Direct messages enabled
- Invite first 20-30 experts from waitlist

### Phase 2: Channels (Week 3-4)
- Service channels open (#security, #ci-cd, #cloud, etc.)
- Helpfulness marking enabled
- Community activity shows on profiles
- Moderation tools active

### Phase 3: Team Formation (Week 5-6)
- Team formation board launches
- "Looking for" section added to profiles
- AI match suggestions on board posts
- Trial collaboration flow enabled

### Phase 4: Intelligence (Week 7-8)
- Community activity feeds into package matching
- AI-suggested team compositions
- Trial success data influences recommendations
- Directory suggestions based on complementary skills

---

## What This Is NOT

| This is NOT | Why not |
|---|---|
| A social network | No followers, no feeds, no virality mechanics |
| Slack/Discord clone | Async-first, no real-time pressure, no 500-channel overwhelm |
| A job board | No one posts "hiring" — team formation is collaborative, not transactional |
| A content platform | No blogs, no articles, no thought leadership. Short posts and replies only |
| A rating system | No stars on people. Credibility is earned through verified delivery, not votes |

---

## Summary

The expert community is a **team formation engine disguised as a professional space.** Every feature exists to help solo experts:

1. **Discover** each other through profiles, directory, and channels
2. **Evaluate** each other through verified delivery data and community contributions
3. **Connect** through DMs initiated from real context (board posts, channel activity)
4. **Trial** working together on a real package with real stakes
5. **Formalize** into a team when the trial proves compatibility

The community isn't the product — team formation is. The community is the trust-building layer that makes team formation feel safe instead of risky.
