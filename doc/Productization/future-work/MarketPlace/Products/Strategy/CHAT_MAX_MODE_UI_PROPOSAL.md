# Chat Max Mode — UI Redesign Proposal

## Problem Statement

The current maximized chat view stretches a narrow chatbot widget to full viewport width, creating an empty, lifeless experience. The conversation bubble sits centered in a sea of white space. Suggestion chips are duplicated (top bar + bottom panel). The user sees a blank page with one welcome message and thinks: "why did I maximize this?"

**Core insight:** Max mode shouldn't be "bigger chat." It should be a **workspace** — a split-screen experience where conversation drives a living canvas of visual content.

---

## Design Philosophy

**The Chat + Canvas Pattern**

Max mode transforms from a chat window into a two-panel workspace:
- **Left panel:** Focused conversation thread (fixed width, ~400px)
- **Right panel:** Dynamic canvas that renders rich content based on conversation context

The canvas is NOT static. It responds to what the AI is discussing — showing product cards when browsing, comparison tables when comparing, policy documents when asking about shipping, and an intelligent welcome dashboard when idle.

**Mental model:** Think of it as a knowledgeable shop assistant (left) standing next to a dynamic display wall (right) that shows whatever they're talking about.

---

## Visual Design

### Color Palette (inherits from parent app, enhanced for max mode)

| Role | Color | Usage |
|---|---|---|
| Left panel bg | `#ffffff` | Chat conversation area |
| Right panel bg | `#f8fafc` | Canvas area (subtle contrast) |
| Panel divider | `#e2e8f0` | 1px vertical separator |
| Card surface | `#ffffff` | Product cards, content cards on canvas |
| Card border | `#e2e8f0` | Card edges |
| Card hover | `#f1f5f9` | Hover state background |
| Primary accent | `#6366f1` | AI highlights, active states |
| Product tag | `#dbeafe` bg / `#2563eb` text | Category badges |
| Price text | `#111827` | Bold, prominent |
| Sale price | `#dc2626` | Discount/sale callout |
| Success | `#10b981` | In stock, available |
| Warning | `#f59e0b` | Low stock, limited |
| Rating star | `#f59e0b` | Filled stars |
| Muted text | `#64748b` | Secondary descriptions |
| AI bubble bg | `#f0f0ff` | Slight indigo tint for AI messages |
| User bubble bg | `#6366f1` | Indigo, white text |

### Typography

- **Canvas title:** 24px, weight 700
- **Card product name:** 15px, weight 600
- **Card price:** 16px, weight 700
- **Card description:** 13px, weight 400, muted color
- **Chat messages:** 14px, weight 400, line-height 1.6
- **Section labels:** 12px, weight 600, uppercase, tracking 0.05em
- **Badge text:** 12px, weight 500

---

## Layout Architecture

### Overall Structure

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [←  Minimize]  Shopping Assistant                      [⚙] [⊞ Layout] [✕]  │
├────────────────────┬─────────────────────────────────────────────────────────┤
│                    │                                                         │
│   CHAT PANEL       │              CANVAS PANEL                              │
│   (400px fixed)    │              (fluid, fills remaining)                  │
│                    │                                                         │
│                    │   Content changes dynamically based on                 │
│   Conversation     │   conversation context                                │
│   thread           │                                                         │
│                    │   States:                                              │
│                    │   • Welcome Dashboard (idle)                           │
│                    │   • Product Gallery (browsing)                         │
│                    │   • Product Detail (focused)                           │
│                    │   • Comparison Table (comparing)                       │
│                    │   • Policy/Info View (questions)                       │
│                    │   • Cart Summary (buying)                              │
│                    │                                                         │
│                    │                                                         │
├────────────────────┤                                                         │
│ [AI Suggestions ▾] │                                                         │
│ ┌────────────────┐ │                                                         │
│ │ Ask anything.. │ │                                                         │
│ └────────────────┘ │                                                         │
└────────────────────┴─────────────────────────────────────────────────────────┘
```

### Header Bar

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [← ▪▪]  🛍 Shopping Assistant              [🔍 Search] [⊞ Layout] [✕]      │
│          ● Online · Knows 2,847 products                                    │
└──────────────────────────────────────────────────────────────────────────────┘
```

- **Left:** Minimize button (returns to widget mode), assistant name with icon
- **Subtitle:** Status indicator + product knowledge count ("Knows 2,847 products")
- **Right actions:**
  - Search icon — opens quick product search overlay
  - Layout toggle — switch between Chat+Canvas / Full Chat / Full Canvas
  - Close button
- Background: white with bottom border
- Height: 56px

---

## Canvas States

### State 1: Welcome Dashboard (Default / Idle)

When no conversation has started or during idle, the canvas shows an intelligent welcome dashboard — NOT empty space.

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Good afternoon, welcome back 👋                                │
│                                                                 │
│  ┌─ TRENDING NOW ──────────────────────────────────────────┐    │
│  │                                                          │    │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────┐  │    │
│  │  │ 📸       │  │ 📸       │  │ 📸       │  │ 📸     │  │    │
│  │  │          │  │          │  │          │  │        │  │    │
│  │  │ Product  │  │ Product  │  │ Product  │  │Product │  │    │
│  │  │ Name     │  │ Name     │  │ Name     │  │Name    │  │    │
│  │  │ $49.99   │  │ $89.00   │  │ $124.00  │  │$35.00  │  │    │
│  │  │ ★★★★★    │  │ ★★★★☆    │  │ ★★★★★    │  │★★★★☆   │  │    │
│  │  └──────────┘  └──────────┘  └──────────┘  └────────┘  │    │
│  │                                                    ──→  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ QUICK EXPLORE ─────────────────────────────────────────┐    │
│  │                                                          │    │
│  │  ┌────────────────┐  ┌────────────────┐                  │    │
│  │  │ 🏷             │  │ 📦             │                  │    │
│  │  │ Collections    │  │ New Arrivals   │                  │    │
│  │  │ 12 categories  │  │ 24 this week   │                  │    │
│  │  └────────────────┘  └────────────────┘                  │    │
│  │  ┌────────────────┐  ┌────────────────┐                  │    │
│  │  │ 🔥             │  │ 💰             │                  │    │
│  │  │ Best Sellers   │  │ On Sale        │                  │    │
│  │  │ Top 20         │  │ 8 deals live   │                  │    │
│  │  └────────────────┘  └────────────────┘                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ POPULAR QUESTIONS ─────────────────────────────────────┐    │
│  │                                                          │    │
│  │  → "What's your return policy?"                          │    │
│  │  → "Show me gifts under $50"                             │    │
│  │  → "Compare your wireless headphones"                    │    │
│  │  → "What's trending this month?"                         │    │
│  │                                                          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Behavior:**
- Trending products are clickable — clicking sends "Tell me about [product]" to chat
- Quick Explore cards are clickable — clicking sends "Show me [category]" to chat
- Popular questions are clickable — clicking sends that question to chat
- Everything on the canvas is an entry point INTO the conversation — the canvas feeds the chat

---

### State 2: Product Gallery (Browsing Mode)

When the user asks "show me your best sellers" or browses a category, the canvas switches to a rich product gallery.

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Best Sellers                                    [Grid ⊞] [List ☰]│
│  24 products · sorted by popularity                             │
│                                                                 │
│  ┌─ Filters ──────────────────────────────────────────────┐     │
│  │ [All] [Under $50] [Under $100] [$100+]  Price ▾  Rating ▾│   │
│  └────────────────────────────────────────────────────────┘     │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │                   │  │                   │  │              │  │
│  │    ┌─────────┐    │  │    ┌─────────┐    │  │  ┌────────┐ │  │
│  │    │  IMAGE  │    │  │    │  IMAGE  │    │  │  │ IMAGE  │ │  │
│  │    │         │    │  │    │         │    │  │  │        │ │  │
│  │    └─────────┘    │  │    └─────────┘    │  │  └────────┘ │  │
│  │                   │  │                   │  │              │  │
│  │  Wireless Pro     │  │  Travel Pack XL   │  │ USB-C Hub   │  │
│  │  Headphones       │  │                   │  │ Pro          │  │
│  │  ★★★★★ (142)      │  │  ★★★★☆ (89)       │  │ ★★★★★ (201) │  │
│  │                   │  │                   │  │              │  │
│  │  $129.00          │  │  $79.99  $59.99   │  │ $49.00      │  │
│  │  [● In stock]     │  │  [🔴 25% OFF]     │  │ [● In stock]│  │
│  │                   │  │                   │  │              │  │
│  │  [Ask about this] │  │  [Ask about this] │  │ [Ask about] │  │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────┐  │
│  │    ┌─────────┐    │  │    ┌─────────┐    │  │  ┌────────┐ │  │
│  │    │  IMAGE  │    │  │    │  IMAGE  │    │  │  │ IMAGE  │ │  │
│  │    │         │    │  │    │         │    │  │  │        │ │  │
│  │    └─────────┘    │  │    └─────────┘    │  │  └────────┘ │  │
│  │  ...              │  │  ...              │  │ ...         │  │
│  └──────────────────┘  └──────────────────┘  └──────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Behavior:**
- Filter chips at top allow quick refinement WITHOUT typing
- Clicking a filter sends "Show me best sellers under $50" to chat automatically
- "Ask about this" button on each card sends "Tell me more about [product name]" to chat
- Grid/List view toggle
- Hovering a product card highlights it with a subtle indigo left-border
- If user clicks a product card directly, canvas transitions to Product Detail state

---

### State 3: Product Detail (Focus Mode)

When the user asks about a specific product or clicks one from the gallery.

```
┌─────────────────────────────────────────────────────────────────┐
│  [← Back to results]                                           │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                                                         │    │
│  │   ┌───────────────────┐    Wireless Pro Headphones      │    │
│  │   │                   │                                 │    │
│  │   │                   │    ★★★★★ 4.8 (142 reviews)      │    │
│  │   │     PRODUCT       │                                 │    │
│  │   │     IMAGE         │    $129.00                      │    │
│  │   │                   │    ● In stock · Ships in 1 day  │    │
│  │   │                   │                                 │    │
│  │   │                   │    [Electronics] [Audio] [Best] │    │
│  │   └───────────────────┘                                 │    │
│  │   ○ ○ ● ○  (image dots)                                │    │
│  │                                                         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ DETAILS ──────────────────────────────────────────────┐     │
│  │                                                         │     │
│  │  Premium noise-cancelling wireless headphones with      │     │
│  │  40-hour battery life. Bluetooth 5.3, USB-C charging,   │     │
│  │  and multipoint connection for seamless device           │     │
│  │  switching.                                              │     │
│  │                                                         │     │
│  │  Key Features:                                          │     │
│  │  ✓ Active noise cancellation                            │     │
│  │  ✓ 40-hour battery                                      │     │
│  │  ✓ Bluetooth 5.3                                        │     │
│  │  ✓ Foldable design                                      │     │
│  │                                                         │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                 │
│  ┌─ QUICK ACTIONS ────────────────────────────────────────┐     │
│  │                                                         │     │
│  │  [💬 Ask a question]  [⚖ Compare with...]  [📋 Specs]  │     │
│  │                                                         │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                 │
│  ┌─ SIMILAR PRODUCTS ─────────────────────────────────────┐     │
│  │  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐               │     │
│  │  │ IMG  │  │ IMG  │  │ IMG  │  │ IMG  │               │     │
│  │  │$89   │  │$199  │  │$79   │  │$149  │               │     │
│  │  └──────┘  └──────┘  └──────┘  └──────┘               │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Quick Actions behavior:**
- "Ask a question" — focuses the chat input with "About the Wireless Pro Headphones, "
- "Compare with..." — opens a mini-selector of similar products, then triggers comparison state
- "Specs" — expands a specifications table inline

---

### State 4: Comparison Table (Comparing Mode)

When the user asks "compare your top headphones" or clicks compare from product detail.

```
┌─────────────────────────────────────────────────────────────────┐
│  [← Back]   Comparing 3 products                               │
│                                                                 │
│  ┌──────────────┬──────────────┬──────────────┬────────────┐    │
│  │              │  Wireless    │  Studio      │  Budget    │    │
│  │              │  Pro         │  Max         │  Buds      │    │
│  │              │  ┌──────┐   │  ┌──────┐   │  ┌──────┐ │    │
│  │              │  │ IMG  │   │  │ IMG  │   │  │ IMG  │ │    │
│  │              │  └──────┘   │  └──────┘   │  └──────┘ │    │
│  ├──────────────┼──────────────┼──────────────┼────────────┤    │
│  │ Price        │ $129.00      │ $199.00      │ $49.00     │    │
│  │              │              │              │ ✓ Best     │    │
│  ├──────────────┼──────────────┼──────────────┼────────────┤    │
│  │ Rating       │ ★★★★★ 4.8   │ ★★★★☆ 4.3   │ ★★★★☆ 4.1 │    │
│  │              │ ✓ Best       │              │            │    │
│  ├──────────────┼──────────────┼──────────────┼────────────┤    │
│  │ Battery      │ 40 hrs       │ 30 hrs       │ 8 hrs      │    │
│  │              │ ✓ Best       │              │            │    │
│  ├──────────────┼──────────────┼──────────────┼────────────┤    │
│  │ Noise Cancel │ ● Yes        │ ● Yes        │ ○ No       │    │
│  ├──────────────┼──────────────┼──────────────┼────────────┤    │
│  │ Weight       │ 250g         │ 320g         │ 45g        │    │
│  │              │              │              │ ✓ Best     │    │
│  ├──────────────┼──────────────┼──────────────┼────────────┤    │
│  │ Type         │ Over-ear     │ Over-ear     │ In-ear     │    │
│  ├──────────────┼──────────────┼──────────────┼────────────┤    │
│  │              │ [Ask about]  │ [Ask about]  │[Ask about] │    │
│  └──────────────┴──────────────┴──────────────┴────────────┘    │
│                                                                 │
│  ┌─ AI VERDICT ───────────────────────────────────────────┐     │
│  │  Based on price-to-feature ratio, the Wireless Pro     │     │
│  │  offers the best overall value. The Studio Max is       │     │
│  │  premium with top sound quality. Budget Buds are        │     │
│  │  great for portability.                                 │     │
│  │                                                    ──→  │     │
│  │  [💬 "Which one is best for travel?"]                   │     │
│  │  [💬 "Tell me more about the winner"]                   │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Behavior:**
- "✓ Best" badges auto-calculated per row
- AI Verdict appears as a summary card below the table
- Suggested follow-up questions are clickable
- Column headers are clickable for product detail
- Columns can be removed (✕ in header) or added ("+ Add product" column)

---

### State 5: Policy / Info View (Knowledge Mode)

When the user asks about shipping, returns, or any informational topic.

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  📋 Shipping Policy                                             │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                                                         │    │
│  │  Standard Shipping                          FREE        │    │
│  │  5-7 business days                                      │    │
│  │  ─────────────────────────────────────────────────       │    │
│  │  Express Shipping                           $9.99       │    │
│  │  2-3 business days                                      │    │
│  │  ─────────────────────────────────────────────────       │    │
│  │  Next Day                                   $19.99      │    │
│  │  Order by 2pm EST                                       │    │
│  │                                                         │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ RELATED ──────────────────────────────────────────────┐     │
│  │                                                         │     │
│  │  [📦 Return Policy]  [🌍 International]  [📍 Tracking]  │     │
│  │                                                         │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                 │
│  ┌─ HAVE MORE QUESTIONS? ─────────────────────────────────┐     │
│  │                                                         │     │
│  │  → "Do you ship to Canada?"                             │     │
│  │  → "Can I change my shipping after ordering?"           │     │
│  │  → "What if my package is lost?"                        │     │
│  │                                                         │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**Behavior:**
- Policy info rendered as clean, structured cards (not chat bubbles)
- Related topics as quick-action chips
- Follow-up questions clickable
- Content sourced from knowledge base, rendered in a readable document format (not chat)

---

### State 6: Cart / Checkout Summary

When the conversation reaches purchasing intent.

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  🛒 Your Conversation Picks                                     │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  ┌──────┐   Wireless Pro Headphones                     │    │
│  │  │ IMG  │   $129.00  · 1x                               │    │
│  │  └──────┘   [Remove]  [Ask about this]                  │    │
│  │  ─────────────────────────────────────────────────       │    │
│  │  ┌──────┐   USB-C Hub Pro                               │    │
│  │  │ IMG  │   $49.00  · 1x                                │    │
│  │  └──────┘   [Remove]  [Ask about this]                  │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Subtotal                                   $178.00     │    │
│  │  Shipping (Standard)                        FREE        │    │
│  │  ───────────────────────────────────────────────         │    │
│  │  Estimated Total                            $178.00     │    │
│  │                                                         │    │
│  │  [  Proceed to Checkout →  ]                            │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                 │
│  ┌─ YOU MIGHT ALSO LIKE ──────────────────────────────────┐     │
│  │  ┌──────┐  ┌──────┐  ┌──────┐                          │     │
│  │  │ IMG  │  │ IMG  │  │ IMG  │                          │     │
│  │  │$29   │  │$39   │  │$19   │                          │     │
│  │  └──────┘  └──────┘  └──────┘                          │     │
│  └─────────────────────────────────────────────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Chat Panel Design (Left Side)

The chat panel itself also gets improvements in max mode.

### Message Types

**AI text message:**
```
┌─────────────────────┐
│ 🤖                   │
│ Here are our top 5   │
│ wireless headphones  │
│ ranked by customer   │
│ reviews.             │
│                      │
│ I've loaded them on  │
│ the right →          │
│           12:34 PM   │
└─────────────────────┘
```

**AI rich card message (inline for widget mode, canvas in max mode):**
```
┌─────────────────────┐
│ 🤖                   │
│ Great choice! Here   │
│ are the details →    │
│                      │
│ ┌─ PINNED ────────┐ │
│ │ Wireless Pro     │ │
│ │ $129 · ★★★★★    │ │
│ │ [View on canvas] │ │
│ └─────────────────┘ │
│           12:35 PM   │
└─────────────────────┘
```

**User message:**
```
              ┌─────────────────────┐
              │ Show me your best   │
              │ sellers under $100  │
              │           12:34 PM  │
              └─────────────────────┘
```

### Suggestion Chips (Improved)

Remove the duplicate top bar. In max mode, suggestions appear ONLY in the expandable panel above the input:

```
┌───────────────────────────┐
│  ▾ Suggestions            │
│                           │
│  Based on your browsing:  │
│  [Compare these two]      │
│  [What about battery?]    │
│  [Any deals right now?]   │
│                           │
│  Popular:                 │
│  [Show me best sellers]   │
│  [Shipping policy]        │
└───────────────────────────┘
┌───────────────────────────┐
│ Ask me anything...     ➤  │
│ 🛍 Shopping · landing pg  │
└───────────────────────────┘
```

**Key change:** Suggestions are contextual — they change based on what was just discussed, not static defaults.

---

## Interaction Patterns

### Canvas ↔ Chat Sync

The canvas and chat are connected by a **context bridge**:

1. **Chat drives canvas:** When AI discusses products, canvas updates to show them
2. **Canvas drives chat:** Clicking items on canvas sends messages to chat
3. **Hover sync:** Hovering a product in chat highlights it on canvas (subtle glow)
4. **Scroll sync:** As user scrolls past product mentions in chat, canvas scrolls to match

### Transition Animations

| From → To | Animation |
|---|---|
| Welcome → Gallery | Cards slide up from bottom, staggered 50ms |
| Gallery → Detail | Selected card expands, others fade out |
| Detail → Back | Card shrinks back, gallery fades in |
| Any → Comparison | Table slides in from right |
| Any → Policy | Cross-fade (200ms) |

### Canvas Loading States

When AI is generating a response that will populate the canvas:

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│                                                                 │
│              ┌──────┐  ┌──────┐  ┌──────┐                      │
│              │ ░░░░ │  │ ░░░░ │  │ ░░░░ │                      │
│              │ ░░░░ │  │ ░░░░ │  │ ░░░░ │                      │
│              │ ░░   │  │ ░░   │  │ ░░   │                      │
│              │ ░░░  │  │ ░░░  │  │ ░░░  │                      │
│              └──────┘  └──────┘  └──────┘                      │
│                                                                 │
│              Finding the best matches for you...                │
│              ●○○                                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

- Skeleton cards with shimmer animation
- Progress text with animated dots
- Skeleton count matches expected result count if known

---

## Layout Modes

Users can toggle between three layout modes via the header button:

### Mode 1: Chat + Canvas (Default)
```
┌──────────┬──────────────────────┐
│  Chat    │  Canvas              │
│  (400px) │  (fluid)             │
└──────────┴──────────────────────┘
```

### Mode 2: Full Chat
```
┌────────────────────────────────┐
│  Chat (centered, max-w 680px) │
│  (like current but with rich  │
│   inline cards)               │
└────────────────────────────────┘
```

### Mode 3: Full Canvas
```
┌────────────────────────────────┐
│  Canvas (full width)           │
│  Chat minimized to floating   │
│  bubble bottom-right          │
└────────────────────────────────┘
```

---

## Mobile Max Mode

On mobile, max mode becomes a **tabbed full-screen experience**:

```
┌──────────────────────────┐
│ Shopping Assistant    [✕] │
├──────────────────────────┤
│                          │
│  [💬 Chat] [🔍 Browse]   │  ← tab bar
│                          │
│  (Active tab content     │
│   fills the screen)      │
│                          │
│                          │
│                          │
│                          │
├──────────────────────────┤
│ Ask me anything...    ➤  │  ← input always visible
└──────────────────────────┘
```

- **Chat tab:** Full conversation thread
- **Browse tab:** Canvas content (products, comparisons, info)
- When AI responds with product content, a subtle badge appears on Browse tab: "3 products loaded"
- Input bar persists across both tabs
- Swipe left/right to switch tabs

---

## Welcome State vs Current (Visual Comparison)

### Current Max Mode (the screenshot):

```
┌──────────────────────────────────────────────────────────────────┐
│ [chips] [chips] [chips] [chips]              Shopping Assistant ✕│
│                                                                  │
│         ┌──────────────────────────────────┐                     │
│         │ Shopping Assistant is ready.     │                     │
│         │ Ask about products, policies,   │                     │
│         │ or collections.                 │                     │
│         └──────────────────────────────────┘                     │
│                                                                  │
│                                                                  │
│                      (empty space)                               │
│                                                                  │
│                                                                  │
│                                                                  │
│                                                                  │
│                                                                  │
│                                                                  │
│  ┌─ AI Suggestions ──────────────────────────────────────────┐   │
│  │ [Show me best sellers]  [What is your shipping policy?]   │   │
│  └───────────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │ Ask me anything...                                       │    │
│  └──────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘

Problems:
✗ 90% dead white space
✗ Duplicated suggestion chips (top + bottom)
✗ Welcome message gives no value
✗ No visual content — just text
✗ No reason to use max mode over widget
✗ No discoverability of products
```

### Proposed Max Mode:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [← ▪▪]  🛍 Shopping Assistant              [🔍 Search] [⊞ Layout] [✕]      │
│          ● Online · Knows 2,847 products                                    │
├────────────────────┬─────────────────────────────────────────────────────────┤
│                    │                                                         │
│  🤖 Welcome!       │  Good afternoon 👋                                      │
│  I know 2,847      │                                                         │
│  products across   │  ┌─ TRENDING NOW ─────────────────────────────────┐    │
│  12 collections.   │  │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐          │    │
│  Ask me anything   │  │ │ IMG  │ │ IMG  │ │ IMG  │ │ IMG  │          │    │
│  or explore the    │  │ │$129  │ │$79   │ │$49   │ │$89   │          │    │
│  canvas →          │  │ │★★★★★ │ │★★★★☆ │ │★★★★★ │ │★★★★☆ │          │    │
│                    │  │ └──────┘ └──────┘ └──────┘ └──────┘          │    │
│                    │  └────────────────────────────────────────────────┘    │
│                    │                                                         │
│                    │  ┌─ QUICK EXPLORE ─────────────────────────────────┐   │
│                    │  │ ┌──────────┐ ┌──────────┐ ┌──────────┐         │   │
│                    │  │ │Collections│ │New Arrive│ │Best Sell │         │   │
│                    │  │ │12 cats   │ │24 new    │ │Top 20    │         │   │
│                    │  │ └──────────┘ └──────────┘ └──────────┘         │   │
│                    │  └────────────────────────────────────────────────┘    │
│                    │                                                         │
│                    │  ┌─ POPULAR QUESTIONS ──────────────────────────────┐  │
│                    │  │ → "Show me gifts under $50"                      │  │
│ ┌─ Suggestions ──┐│  │ → "Compare your wireless headphones"             │  │
│ │Based on context ││  │ → "What's your return policy?"                   │  │
│ │[Best sellers]   ││  └─────────────────────────────────────────────────┘  │
│ │[Shipping info]  ││                                                         │
│ └────────────────┘│                                                         │
│ ┌────────────────┐│                                                         │
│ │Ask anything.. ➤││                                                         │
│ └────────────────┘│                                                         │
├────────────────────┴─────────────────────────────────────────────────────────┤

Improvements:
✓ Every pixel has purpose — no dead space
✓ Products visible immediately — the store comes to you
✓ Canvas responds to conversation in real time
✓ Clear reason to use max mode (browse + chat simultaneously)
✓ Suggestions are contextual, not duplicated
✓ Everything clickable → feeds into conversation
✓ Feels like a store with a personal shopper, not a chatbot
```

---

## Key Design Principles

1. **Max mode = workspace, not bigger chat.** The extra screen real estate must deliver extra value, not extra whitespace.

2. **Canvas serves the conversation.** Everything shown on the canvas is driven by or feeds into the chat. No orphaned UI.

3. **Click-to-chat everywhere.** Every interactive element on the canvas translates to a chat message. The user never has to type what they can click.

4. **Contextual, not static.** Suggestions, quick actions, and canvas content adapt based on conversation history. No hard-coded "Show me your best sellers" chip that appears forever.

5. **Progressive disclosure.** Start with a rich welcome dashboard. As conversation deepens, canvas narrows focus: gallery → detail → comparison → cart. The journey mirrors a real shopping experience.

6. **Two entry points, one journey.** Users can start by chatting (left panel drives right) OR by browsing the canvas (right panel drives left). Both paths converge.

---

## Implementation Priority

| Phase | Scope | Impact |
|---|---|---|
| **P0** | Chat+Canvas split layout + Welcome Dashboard | Eliminates the empty-space problem immediately |
| **P1** | Product Gallery canvas state + click-to-chat | Makes max mode genuinely useful for browsing |
| **P2** | Product Detail + Comparison states | Enables deep product exploration |
| **P3** | Cart Summary + contextual suggestions | Completes the shopping journey |
| **P4** | Layout modes + mobile tabs + transition animations | Polish and flexibility |

---

## Technical Considerations

- Canvas state is derived from chat message metadata — AI responses include structured data (product IDs, type: "gallery"|"detail"|"comparison") that the canvas renderer interprets
- Canvas component is lazy-loaded — widget mode never loads it
- Product images require thumbnail URLs in the product data feed
- Comparison tables are dynamically generated from product attributes
- Layout preference persisted in localStorage
- Canvas ↔ Chat sync uses a shared React context (or equivalent state management) — no API calls for sync
- Skeleton loading states prevent layout shift during AI responses
