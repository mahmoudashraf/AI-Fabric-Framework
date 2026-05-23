# Shopify Chat Max Mode — UI Redesign Proposal (v2)

## Problem Analysis (From Current Screenshots)

### What's broken right now:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ [Show me best sellers] [Shipping policy?] [Compare categories] [Travel?]    │
│                                                                              │
│              ┌──────────────────────────────────────────┐                     │
│              │ Shopping Assistant is ready. Ask about   │   ← Plain text,    │
│              │ products, policies, or collections.      │     no personality │
│              └──────────────────────────────────────────┘                     │
│                                                                              │
│                                           ┌─────────────────────────┐        │
│                                           │ Show me your best       │        │
│                                           │ sellers              ■  │        │
│                                           └─────────────────────────┘        │
│                                                                              │
│              ┌──────────────────────────────────────────┐                     │
│              │ Here are some available products from    │   ← Wall of text,  │
│              │ this store:                              │     no product     │
│              │ Selling Plans Ski Wax: Available in      │     cards, no      │
│              │ three variants, priced from $9.95 to     │     images, no     │
│              │ $49.95.                                  │     visual         │
│              │ The Compare at Price Snowboard:          │     hierarchy      │
│              │ Available, priced at $785.95...          │                    │
│              │ The Out of Stock Snowboard is not        │                    │
│              │ currently available.                 [D] │                    │
│              └──────────────────────────────────────────┘                     │
│                                                                              │
│                                           ┌─────────────────────────┐        │
│                                           │ What should I buy for   │        │
│                                           │ travel?              ■  │        │
│                                           └─────────────────────────┘        │
│                                                                              │
│              ┌───────────────────────────────────────────┐                    │
│              │ 🤖 AI is thinking...                      │  ← Processing     │
│              │                                           │    card is good   │
│              │ ◎ PROCESSING                              │    but could be   │
│              │   "What should I buy for travel?"         │    cleaner        │
│              │ ████████████░░░░░░░░░░░░░░░░░░░           │                    │
│              │                                           │                    │
│              │ • Processing order request                │                    │
│              │ • Reviewing previous request              │                    │
│              │ • Processing database query               │                    │
│              │ • Generating response                     │                    │
│              └───────────────────────────────────────────┘                    │
│                                                                              │
│                        (massive empty space)                                 │
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐     │
│  │ Ask me anything...                          [Shopping] [landing] 😊│     │
│  └──────────────────────────────────────────────────────────────────────┘     │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 7 specific problems:

| # | Problem | Why it hurts |
|---|---|---|
| 1 | **Products listed as plain text** | "Selling Plans Ski Wax: Available in three variants, priced from $9.95 to $49.95" is a wall of text. No images, no cards, no prices formatted. Shopify HAS this data — images, variants, prices — but the chat throws it away. |
| 2 | **Static suggestion chips** | Same 4 chips at the top forever. They don't change based on conversation context. After browsing products, "Show me your best sellers" is irrelevant — you already saw them. |
| 3 | **Empty welcome state** | "Shopping Assistant is ready" is functional but lifeless. 90% of the viewport is empty. Zero reason to stay on this screen. |
| 4 | **No follow-up affordance** | After showing products, there's no "Compare these" or "Add to cart" or "Tell me more" — the user has to know what to type next. |
| 5 | **Processing card takes full width** | The thinking indicator is oversized for what it communicates. A subtle inline indicator would suffice. |
| 6 | **Suggestion chips duplicated** | Top bar AND bottom panel show the same suggestions. Wasted space and cognitive clutter. |
| 7 | **No Shopify integration visible** | This is embedded in Shopify — but nothing about the UI uses Shopify's data richness: product images, collection thumbnails, variant swatches, inventory status. |

---

## Revised Design Philosophy

**Center-aligned rich conversation with inline content blocks.**

NOT a split panel. The chat stays centered (max-width 720px) and the AI renders **rich interactive blocks** inline — product cards, comparison grids, collection showcases — as part of the conversation flow. The content IS the conversation.

**Mental model:** Like iMessage or WhatsApp — centered bubbles — but AI messages can expand into full-width product displays, carousels, and interactive components within the chat stream.

---

## Visual Design System

### Color Palette (Shopify-aligned)

| Role | Token | Color | Usage |
|---|---|---|---|
| Page bg | `--bg` | `#f6f6f7` | Full-page background behind chat |
| Chat column bg | `--chat-bg` | `#ffffff` | Center column backdrop |
| AI bubble | `--ai-bubble` | `#f3f4f6` | Light gray, rounded |
| User bubble | `--user-bubble` | `#1a1a2e` | Dark navy, near-black |
| User bubble text | `--user-text` | `#ffffff` | White on dark bubble |
| Product card bg | `--card-bg` | `#ffffff` | Product cards |
| Card border | `--card-border` | `#e5e7eb` | 1px card edges |
| Card hover border | `--card-hover` | `#6366f1` | Indigo on hover |
| Price | `--price` | `#111827` | Bold product price |
| Compare price | `--compare-price` | `#9ca3af` | Strikethrough original |
| Sale badge | `--sale` | `#ef4444` bg, `#fff` text | Sale/discount pill |
| In stock | `--stock-yes` | `#10b981` | Green dot |
| Out of stock | `--stock-no` | `#ef4444` | Red dot / muted card |
| Low stock | `--stock-low` | `#f59e0b` | Amber warning |
| Primary action | `--primary` | `#6366f1` | Buttons, links, active |
| Primary hover | `--primary-hover` | `#4f46e5` | Darker on hover |
| Secondary action | `--secondary` | `#f3f4f6` bg, `#374151` text | Outlined buttons |
| Muted text | `--muted` | `#6b7280` | Descriptions, meta |
| Tag bg | `--tag-bg` | `#eff6ff` | Product type badges |
| Tag text | `--tag-text` | `#2563eb` | Badge text |
| Thinking accent | `--thinking` | `#6366f1` | Progress, processing |
| Rating star | `--star` | `#f59e0b` | Star ratings |
| Separator | `--separator` | `#f3f4f6` | Thin horizontal rules |

### Typography

| Element | Size | Weight | Line-height | Notes |
|---|---|---|---|---|
| Welcome heading | 28px | 700 | 1.2 | Centered, dark |
| Section label | 11px | 600 | 1 | Uppercase, tracking 0.08em, muted |
| AI message text | 14px | 400 | 1.6 | Dark gray |
| User message text | 14px | 400 | 1.5 | White |
| Product name | 14px | 600 | 1.3 | On cards |
| Product price | 16px | 700 | 1 | Prominent |
| Compare-at price | 13px | 400 | 1 | Strikethrough, muted |
| Variant label | 12px | 500 | 1 | In variant selectors |
| Badge text | 11px | 600 | 1 | Pill badges |
| Button text | 13px | 600 | 1 | Action buttons |
| Input placeholder | 14px | 400 | 1 | Muted |
| Timestamp | 11px | 400 | 1 | Muted, right-aligned |
| Chip text | 13px | 500 | 1 | Suggestion chips |

### Spacing & Sizing

| Element | Value |
|---|---|
| Chat column max-width | 720px |
| Chat column padding | 24px sides |
| Message gap | 16px between messages |
| Message group gap | 24px between user→AI or AI→user |
| Bubble padding | 14px 18px |
| Bubble radius | 18px (with flat corner on sender side) |
| Product card radius | 12px |
| Product card padding | 0 (image flush) + 14px (text area) |
| Product image aspect | 1:1 (square) |
| Button radius | 8px |
| Button padding | 10px 16px |
| Chip radius | 20px (full pill) |
| Chip padding | 8px 14px |
| Input height | 48px |
| Input radius | 24px (pill) |
| Header height | 56px |

---

## Page Layout

### Overall Structure

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         HEADER BAR (56px)                                    │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─ bg: #f6f6f7 ─────────────────────────────────────────────────────────┐  │
│  │                                                                        │  │
│  │                  ┌── CHAT COLUMN (720px, centered) ──┐                 │  │
│  │                  │                                    │                 │  │
│  │                  │  Messages scroll here              │                 │  │
│  │                  │  (rich content blocks inline)      │                 │  │
│  │                  │                                    │                 │  │
│  │                  └────────────────────────────────────┘                 │  │
│  │                                                                        │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│                         INPUT BAR (sticky bottom)                            │
└──────────────────────────────────────────────────────────────────────────────┘
```

- Chat column sits centered, white bg, with soft shadow on left/right edges
- Outer area is light gray (`#f6f6f7`) — gives the column definition without harsh borders
- Scroll is on the chat column only
- Input bar sticks to bottom, full width, white bg with top border

---

## Component Designs

### 1. Header Bar

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  [← ■■]    🛍 Shopping Assistant                              [🔍]    [✕]   │
│             ● Online · Snow Devil Store                                      │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

| Element | Spec |
|---|---|
| Minimize button | `← ■■` icon, returns to widget mode. Muted color, 32px tap target |
| Assistant icon | Store's favicon or default shopping bag icon, 28px |
| Title | "Shopping Assistant" — 15px, weight 600 |
| Subtitle | Green dot (8px, `#10b981`) + "Online" + store name — 12px, muted |
| Search | Magnifying glass icon, opens quick product search overlay |
| Close | ✕ to close entirely, muted color |
| Background | White, bottom border 1px `#e5e7eb` |
| Height | 56px |

---

### 2. Welcome State (Replaces Empty Screen)

This is what the user sees on first open in max mode — NOT empty space.

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  [← ■■]    🛍 Shopping Assistant                              [🔍]    [✕]   │
│             ● Online · Snow Devil Store                                      │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                                                                              │
│                     ┌────────────────────────────────┐                        │
│                     │                                │                        │
│                     │         🛍                      │                        │
│                     │                                │                        │
│                     │   Hi! I'm your shopping        │                        │
│                     │   assistant for Snow Devil.    │                        │
│                     │                                │                        │
│                     │   I know your 24 products      │                        │
│                     │   across 5 collections.        │                        │
│                     │   Ask me anything.             │                        │
│                     │                                │                        │
│                     └────────────────────────────────┘                        │
│                                                                              │
│                     ┌─ TRY ASKING ─────────────────────┐                     │
│                     │                                   │                     │
│                     │  ┌─────────────────────────────┐  │                     │
│                     │  │ 🏷  Browse collections       │  │                     │
│                     │  │    See all 5 collections     │  │                     │
│                     │  └─────────────────────────────┘  │                     │
│                     │  ┌─────────────────────────────┐  │                     │
│                     │  │ 🔥 What's popular?           │  │                     │
│                     │  │    Top sellers right now     │  │                     │
│                     │  └─────────────────────────────┘  │                     │
│                     │  ┌─────────────────────────────┐  │                     │
│                     │  │ 💰 Find deals               │  │                     │
│                     │  │    Products on sale          │  │                     │
│                     │  └─────────────────────────────┘  │                     │
│                     │  ┌─────────────────────────────┐  │                     │
│                     │  │ 📦 Shipping & returns        │  │                     │
│                     │  │    Policies and info         │  │                     │
│                     │  └─────────────────────────────┘  │                     │
│                     │                                   │                     │
│                     └───────────────────────────────────┘                     │
│                                                                              │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│          ┌──────────────────────────────────────────────┐                     │
│          │  Ask me anything...                      ➤  │                     │
│          └──────────────────────────────────────────────┘                     │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

**"Try Asking" cards:**
- Full-width within the chat column
- Each is a clickable row with icon + title + subtitle
- Background: white, border: `#e5e7eb`, radius: 12px
- Hover: border shifts to `#6366f1`, subtle lift (2px translateY)
- Clicking sends the prompt text to the chat
- These cards DISAPPEAR once the first message is sent (they're a welcome state only)

**No duplicate chips.** The top bar suggestion chips from the current design are REMOVED entirely. Suggestions live ONLY in the welcome cards and contextual follow-ups.

---

### 3. AI Response with Product Cards (The Core Improvement)

This is the most critical change — when the AI mentions products, they render as rich visual cards, NOT plain text.

**Current (broken):**
```
Here are some available products from this store:
Selling Plans Ski Wax: Available in three variants, priced from $9.95 to $49.95.
The Compare at Price Snowboard: Available, priced at $785.95 (compare-at price $885.95).
```

**Proposed (rich cards inline):**

```
│                                                                    │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                                                          │      │
│  │  Here are the popular products from Snow Devil:          │      │
│  │                                                          │      │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │      │
│  │  │ ┌──────────┐ │ │ ┌──────────┐ │ │ ┌──────────┐ │     │      │
│  │  │ │          │ │ │ │          │ │ │ │          │ │     │      │
│  │  │ │  IMAGE   │ │ │ │  IMAGE   │ │ │ │  IMAGE   │ │     │      │
│  │  │ │  (1:1)   │ │ │ │  (1:1)   │ │ │ │  (1:1)   │ │     │      │
│  │  │ │          │ │ │ │          │ │ │ │          │ │     │      │
│  │  │ └──────────┘ │ │ └──────────┘ │ │ └──────────┘ │     │      │
│  │  │              │ │              │ │              │     │      │
│  │  │ Selling Plans│ │ Snowboard   │ │ Multi-loc    │     │      │
│  │  │ Ski Wax     │ │              │ │ Snowboard    │     │      │
│  │  │              │ │ ~~$885.95~~ │ │              │     │      │
│  │  │ From $9.95  │ │ $785.95     │ │ $729.95      │     │      │
│  │  │ 3 variants  │ │ [SALE]      │ │              │     │      │
│  │  │ ● In stock  │ │ ● In stock  │ │ ● In stock   │     │      │
│  │  │              │ │              │ │              │     │      │
│  │  └──────────────┘ └──────────────┘ └──────────────┘     │      │
│  │                                                          │      │
│  │  The Selling Plans Ski Wax is your most versatile        │      │
│  │  option with 3 variants starting at $9.95. The           │      │
│  │  Snowboard is marked down from $885.95.                  │      │
│  │                                                          │      │
│  │  ┌──────────────────┐ ┌──────────────────┐              │      │
│  │  │ ⚖ Compare these  │ │ 💬 Tell me more  │              │      │
│  │  └──────────────────┘ └──────────────────┘              │      │
│  │                                                   12:34 │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                    │
```

**Product card spec:**

```
┌──────────────────┐
│ ┌──────────────┐ │
│ │              │ │  Image: square (1:1), fills card width
│ │   PRODUCT    │ │  Radius: 8px top corners only
│ │   IMAGE      │ │  Fallback: light gray bg with product type icon
│ │              │ │
│ └──────────────┘ │
│                  │  Padding below image: 12px all sides
│  Product Name    │  14px, weight 600, max 2 lines, ellipsis
│                  │
│  ~~$885.95~~     │  Compare-at: 12px, strikethrough, muted
│  $785.95  [SALE] │  Price: 15px, weight 700. Sale badge: red pill
│                  │
│  3 variants      │  12px, muted (only if >1 variant)
│  ● In stock      │  12px, green dot + text
│                  │
└──────────────────┘

Card: white bg, 1px border #e5e7eb, radius 12px
Hover: border → #6366f1, shadow 0 4px 12px rgba(0,0,0,0.08)
Click: opens product detail in-chat (see Section 5)
Width: ~200px each in a 3-col row, responsive
Out-of-stock: image has 40% white overlay, muted text, no price emphasis
```

**Follow-up action buttons:**
- Appear below the AI's summary text
- Pill-shaped, secondary style (gray bg, dark text)
- Hover: primary color bg, white text
- Clicking sends the action as a message (e.g., "Compare the Ski Wax and Snowboard")

**Horizontal scroll for 4+ products:**
```
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ →       │
│  │   IMG    │ │   IMG    │ │   IMG    │ │   IMG    │          │
│  │ Product  │ │ Product  │ │ Product  │ │ Product  │  scroll  │
│  │ $49.95   │ │ $29.95   │ │ $19.95   │ │ $89.95   │  hint    │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘          │

- Horizontal scroll with snap points
- Fade gradient on right edge as scroll indicator
- Scroll bar hidden, swipe/drag to scroll
- 1-3 products: grid (no scroll)
- 4+ products: horizontal scroll
```

---

### 4. Single Product Detail (In-Chat Expansion)

When user clicks a product card or asks "tell me about the Snowboard":

```
│                                                                    │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                                                          │      │
│  │  ┌────────────────────────────────────────────────────┐  │      │
│  │  │                                                    │  │      │
│  │  │                                                    │  │      │
│  │  │              LARGE PRODUCT IMAGE                   │  │      │
│  │  │              (16:9 aspect ratio)                    │  │      │
│  │  │                                                    │  │      │
│  │  │                                                    │  │      │
│  │  │  ○  ○  ●  ○     ← image dots if multiple          │  │      │
│  │  └────────────────────────────────────────────────────┘  │      │
│  │                                                          │      │
│  │  The Compare at Price Snowboard                          │      │
│  │  ★★★★☆ 4.2 (18 reviews)                                 │      │
│  │                                                          │      │
│  │  ~~$885.95~~                                             │      │
│  │  $785.95                                    [SAVE $100]  │      │
│  │                                                          │      │
│  │  ● In stock · Usually ships in 1-2 days                  │      │
│  │                                                          │      │
│  │  ── Variants ──────────────────────────────────────────  │      │
│  │                                                          │      │
│  │  Size:    [S]  [M]  [◉ L]  [XL]                          │      │
│  │  Color:   [⚫ Black]  [⚪ White]  [🔴 Red]               │      │
│  │                                                          │      │
│  │  ── Details ───────────────────────────────────────────  │      │
│  │                                                          │      │
│  │  This all-mountain snowboard is perfect for              │      │
│  │  intermediate to advanced riders. Features a             │      │
│  │  directional twin shape with medium flex.                │      │
│  │                                                          │      │
│  │  • Type: Snowboard                                       │      │
│  │  • Vendor: Snow Devil                                    │      │
│  │  • SKU: SNOW-CMP-001                                     │      │
│  │                                                          │      │
│  │  ┌──────────────────────────────────────────────────┐    │      │
│  │  │           🛒  View in Store →                     │    │      │
│  │  └──────────────────────────────────────────────────┘    │      │
│  │                                                          │      │
│  │  ┌───────────────┐ ┌────────────────┐ ┌──────────────┐  │      │
│  │  │ ⚖ Compare     │ │ 📋 Size guide  │ │ 📦 Shipping  │  │      │
│  │  └───────────────┘ └────────────────┘ └──────────────┘  │      │
│  │                                                   12:35 │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                    │
```

**Product detail block spec:**

| Element | Spec |
|---|---|
| Image | 16:9 aspect, radius 12px, swipeable if multiple images |
| Image dots | Center-aligned, 6px circles, active = `#6366f1`, inactive = `#d1d5db` |
| Product name | 20px, weight 700 |
| Rating | Stars (filled `#f59e0b`, empty `#d1d5db`) + count |
| Compare-at price | 14px, strikethrough, muted |
| Sale price | 22px, weight 700, `#111827` |
| Save badge | Green pill "SAVE $100" |
| Stock status | Green dot + text, 13px |
| Variant selector | Pill buttons, active = filled dark, inactive = outlined |
| Description | 14px, regular weight, muted color |
| "View in Store" | Full-width button, primary color, opens Shopify product page |
| Quick actions | 3 pill buttons, secondary style |

**Variant selector behavior:**
- Selecting a variant updates the price, image, and stock status in real-time
- Active variant: dark bg (`#1a1a2e`), white text
- Inactive: white bg, dark border, dark text
- Out-of-stock variant: strikethrough text, disabled state

---

### 5. Comparison View (In-Chat Table)

When user says "Compare these" or "What's the difference between X and Y":

```
│                                                                    │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                                                          │      │
│  │  Here's a side-by-side comparison:                       │      │
│  │                                                          │      │
│  │  ┌─────────────┬─────────────┬─────────────┐            │      │
│  │  │             │ ┌─────────┐ │ ┌─────────┐ │            │      │
│  │  │             │ │  IMG    │ │ │  IMG    │ │            │      │
│  │  │             │ └─────────┘ │ └─────────┘ │            │      │
│  │  │             │ Snowboard   │ Multi-loc   │            │      │
│  │  │             │             │ Snowboard   │            │      │
│  │  ├─────────────┼─────────────┼─────────────┤            │      │
│  │  │ Price       │ $785.95     │ $729.95     │            │      │
│  │  │             │ was $885    │             │            │      │
│  │  ├─────────────┼─────────────┼─────────────┤            │      │
│  │  │ Savings     │ ✓ $100 off  │ —           │            │      │
│  │  ├─────────────┼─────────────┼─────────────┤            │      │
│  │  │ Variants    │ 4 options   │ 2 options   │            │      │
│  │  ├─────────────┼─────────────┼─────────────┤            │      │
│  │  │ Stock       │ ● Yes       │ ● Yes       │            │      │
│  │  ├─────────────┼─────────────┼─────────────┤            │      │
│  │  │ Type        │ Snowboard   │ Snowboard   │            │      │
│  │  └─────────────┴─────────────┴─────────────┘            │      │
│  │                                                          │      │
│  │  ┌────────────────────────────────────────────────────┐  │      │
│  │  │ 💡 The Compare at Price Snowboard gives you $100   │  │      │
│  │  │    in savings. The Multi-location Snowboard is     │  │      │
│  │  │    $56 less but no current discount.               │  │      │
│  │  └────────────────────────────────────────────────────┘  │      │
│  │                                                          │      │
│  │  ┌────────────────────┐ ┌─────────────────────┐         │      │
│  │  │ 🛒 View Snowboard  │ │ 🛒 View Multi-loc   │         │      │
│  │  └────────────────────┘ └─────────────────────┘         │      │
│  │                                                   12:36 │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                    │
```

**Comparison table spec:**
- Horizontal scroll if 3+ products (mobile)
- Header row: product image (small, 48px square) + name
- Data rows: alternate white/`#f9fafb` backgrounds
- "Best" values get a subtle green text or checkmark
- AI verdict card below: light blue bg (`#eff6ff`), left indigo border (3px)
- View buttons: one per product, secondary style

---

### 6. Collection Browse (In-Chat Grid)

When user says "Browse collections" or "What categories do you have?":

```
│                                                                    │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                                                          │      │
│  │  Here are all 5 collections:                             │      │
│  │                                                          │      │
│  │  ┌────────────────────────┐ ┌────────────────────────┐   │      │
│  │  │ ┌──────────────────┐   │ │ ┌──────────────────┐   │   │      │
│  │  │ │                  │   │ │ │                  │   │   │      │
│  │  │ │  COLLECTION      │   │ │ │  COLLECTION      │   │   │      │
│  │  │ │  IMAGE           │   │ │ │  IMAGE           │   │   │      │
│  │  │ │                  │   │ │ │                  │   │   │      │
│  │  │ └──────────────────┘   │ │ └──────────────────┘   │   │      │
│  │  │  Snowboards             │ │  Accessories           │   │      │
│  │  │  8 products             │ │  12 products           │   │      │
│  │  └────────────────────────┘ └────────────────────────┘   │      │
│  │  ┌────────────────────────┐ ┌────────────────────────┐   │      │
│  │  │ ┌──────────────────┐   │ │ ┌──────────────────┐   │   │      │
│  │  │ │  COLLECTION      │   │ │ │  COLLECTION      │   │   │      │
│  │  │ │  IMAGE           │   │ │ │  COLLECTION      │   │   │      │
│  │  │ └──────────────────┘   │ │ └──────────────────┘   │   │      │
│  │  │  Wax & Care             │ │  Apparel               │   │      │
│  │  │  4 products             │ │  6 products            │   │      │
│  │  └────────────────────────┘ └────────────────────────┘   │      │
│  │                                                          │      │
│  │  Tap any collection to explore it.                       │      │
│  │                                                   12:37 │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                    │
```

**Collection card spec:**
- 2-column grid (fixed, even on wider screens — keeps it scannable)
- Image: 16:9 aspect, collection hero image or first product image
- Title: 15px, weight 600
- Count: 13px, muted
- Hover: lift + indigo border
- Click: sends "Show me products in [Collection]" to chat

---

### 7. Processing / Thinking State (Redesigned)

The current processing card is too large. Redesign as a compact inline indicator:

**Simple thinking (short queries):**
```
│                                                                    │
│  ┌────────────────────────────────────────┐                        │
│  │  ●●●  Thinking...                     │                        │
│  └────────────────────────────────────────┘                        │
│                                                                    │
```
- Three animated dots (scale pulse, staggered)
- AI bubble style (gray bg)
- Compact — just one line

**Complex thinking (multi-step queries):**
```
│                                                                    │
│  ┌────────────────────────────────────────┐                        │
│  │                                        │                        │
│  │  🔍 Finding products for travel...     │                        │
│  │                                        │                        │
│  │  ✓ Checked 24 products                 │                        │
│  │  ✓ Reviewed your preferences           │                        │
│  │  ● Generating recommendations          │                        │
│  │  ━━━━━━━━━━━━━━━━━━━━━░░░░░░░          │                        │
│  │                                        │                        │
│  └────────────────────────────────────────┘                        │
│                                                                    │
```

| Element | Spec |
|---|---|
| Container | AI bubble style, same width as text bubbles |
| Status icon | ✓ completed (green), ● active (indigo pulse), ○ pending (gray) |
| Step text | 13px, regular weight |
| Progress bar | 4px height, indigo fill, rounded |
| Width | Matches bubble width, NOT full chat column |
| Title | 14px, weight 600, with contextual icon |

**Key difference from current:** The processing card is the SAME WIDTH as a normal AI bubble. It doesn't stretch to full width. This makes the transition to the actual response feel seamless.

---

### 8. User Message Bubbles

```
│                                                                    │
│                        ┌─────────────────────────────────┐         │
│                        │  Show me your best sellers      │         │
│                        │                          12:34  │         │
│                        └─────────────────────────────────┘         │
│                                                                    │
```

| Element | Spec |
|---|---|
| Alignment | Right-aligned |
| Background | `#1a1a2e` (dark navy) |
| Text color | `#ffffff` |
| Font | 14px, weight 400 |
| Padding | 12px 18px |
| Radius | 18px, with 4px bottom-right (flat corner on sender side) |
| Timestamp | 11px, `rgba(255,255,255,0.6)`, right-aligned below text |
| Max width | 70% of chat column |

---

### 9. AI Text-Only Bubbles

For non-product responses (policies, general info):

```
│                                                                    │
│  ┌────────────────────────────────────────────────────────┐        │
│  │                                                        │        │
│  │  Our shipping policy:                                  │        │
│  │                                                        │        │
│  │  📦 Standard Shipping — Free                           │        │
│  │     5-7 business days                                  │        │
│  │                                                        │        │
│  │  🚀 Express — $9.99                                    │        │
│  │     2-3 business days                                  │        │
│  │                                                        │        │
│  │  ⚡ Next Day — $19.99                                   │        │
│  │     Order by 2pm EST                                   │        │
│  │                                                        │        │
│  │  All orders include tracking. Free returns             │        │
│  │  within 30 days.                                       │        │
│  │                                                        │        │
│  │  ┌──────────────────┐ ┌──────────────────┐            │        │
│  │  │ 📋 Return policy │ │ 📍 Track order   │            │        │
│  │  └──────────────────┘ └──────────────────┘            │        │
│  │                                                 12:38 │        │
│  └────────────────────────────────────────────────────────┘        │
│                                                                    │
```

| Element | Spec |
|---|---|
| Alignment | Left-aligned |
| Background | `#f3f4f6` |
| Text color | `#374151` |
| Padding | 14px 18px |
| Radius | 18px, with 4px bottom-left (flat corner on sender side) |
| Max width | 85% of chat column (wider than user bubbles — AI needs more room) |
| Formatting | AI can use bold, lists, emoji for structure |
| Follow-up actions | Secondary pill buttons at bottom of bubble |

---

### 10. Contextual Follow-Up Suggestions

Instead of static chips that never change, suggestions appear AFTER each AI response, contextual to what was just discussed:

```
│                                                                    │
│  ┌──────────────────────────────────────────────────────┐          │
│  │  (AI response about snowboards)                      │          │
│  │                                               12:35  │          │
│  └──────────────────────────────────────────────────────┘          │
│                                                                    │
│    [⚖ Compare these]  [🔎 Any in stock?]  [💰 Under $500?]        │
│                                                                    │
```

**Suggestion chip spec:**
- Appear directly below the AI bubble, left-aligned
- Horizontal scroll if more than fit (max 4 visible)
- Pill shape: radius 20px, padding 8px 14px
- Border: 1px `#e5e7eb`, bg white
- Text: 13px, weight 500, `#374151`
- Icon: 14px, left of text
- Hover: bg `#f3f4f6`, border `#6366f1`
- Click: sends text as user message, chips disappear
- **Only the most recent AI response has suggestions.** Previous suggestions auto-hide when new messages arrive.

**Context rules:**
| After showing... | Suggest... |
|---|---|
| Multiple products | "Compare these", "Sort by price", "Any on sale?" |
| Single product | "Similar products", "Check availability", "View in store" |
| Collection list | "Show me [top collection]", "What's new?" |
| Policy info | Related policies, "Talk to a human" |
| Out-of-stock item | "Notify when available", "Show alternatives" |
| Comparison | "Which do you recommend?", "View [winner] in store" |

---

### 11. Input Bar (Redesigned)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│          ┌──────────────────────────────────────────────────────┐             │
│          │  ┌──┐                                           ┌──┐│             │
│          │  │📎│  Ask about products, shipping, or more...  │ ➤││             │
│          │  └──┘                                           └──┘│             │
│          └──────────────────────────────────────────────────────┘             │
│            🛍 Shopping · Snow Devil                                           │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

| Element | Spec |
|---|---|
| Container | Sticky bottom, white bg, top border 1px `#f3f4f6`, padding 12px 24px |
| Input field | Pill shape (radius 24px), border 1px `#e5e7eb`, height 48px |
| Placeholder | "Ask about products, shipping, or more..." — 14px, muted |
| Attachment icon | 📎 left inside input, 20px, muted, optional for image search |
| Send button | ➤ right inside input, 32px circle, `#6366f1` bg, white icon |
| Send disabled | Gray bg when input is empty |
| Context label | Below input: store icon + "Shopping · [Store name]" — 12px, muted |
| Focus state | Input border → `#6366f1`, subtle shadow |

**Remove:** The "AI Suggestions" collapsible panel above input. Suggestions are now contextual (Section 10), not in the input area.

**Remove:** The `[Shopping]` and `[landing page]` tag badges from inside the input. Move context label below.

---

### 12. Quick Product Search Overlay

Triggered by the 🔍 icon in the header:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  ┌──────────────────────────────────────────────────────────────────────┐    │
│  │                                                         ░░░░░░░░░░░░│    │
│  │  ┌───────────────────────────────────────────────────────┐  ░░░░░░░░│    │
│  │  │ 🔍 Search products...                             [✕] │  ░░░░░░░░│    │
│  │  └───────────────────────────────────────────────────────┘  ░░░░░░░░│    │
│  │                                                         ░░░░░░░░░░░░│    │
│  │  RECENT                                                 ░░░░░░░░░░░░│    │
│  │  ┌──────────────────────────────────────────────────┐   ░░░░░░░░░░░░│    │
│  │  │ 🕐 Snowboard                                     │   ░░░░░░░░░░░░│    │
│  │  │ 🕐 Ski Wax                                       │   ░░░░░░░░░░░░│    │
│  │  └──────────────────────────────────────────────────┘   ░░░░░░░░░░░░│    │
│  │                                                         ░░░░░░░░░░░░│    │
│  │  COLLECTIONS                                            ░░░░░░░░░░░░│    │
│  │  ┌──────────────────────────────────────────────────┐   ░░░░░░░░░░░░│    │
│  │  │ 🏷 Snowboards (8)                                │   ░░░░░░░░░░░░│    │
│  │  │ 🏷 Accessories (12)                              │   ░░░░░░░░░░░░│    │
│  │  │ 🏷 Wax & Care (4)                                │   ░░░░░░░░░░░░│    │
│  │  └──────────────────────────────────────────────────┘   ░░░░░░░░░░░░│    │
│  │                                                         ░░░░░░░░░░░░│    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

- Modal overlay with backdrop blur
- Auto-focus on search input
- Results appear as you type (instant, client-side filter)
- Clicking a result sends "Tell me about [product]" to chat and closes overlay
- Keyboard navigable (arrow keys + enter)
- ESC to close

---

### 13. Out-of-Stock Product Handling

When AI includes an out-of-stock product:

```
│  ┌──────────────┐                                            │
│  │ ┌──────────┐ │                                            │
│  │ │  IMAGE   │ │  ← 40% white overlay on image              │
│  │ │ ░░░░░░░░ │ │                                            │
│  │ │ ░░░░░░░░ │ │                                            │
│  │ └──────────┘ │                                            │
│  │              │                                            │
│  │ Out of Stock │  ← Title in muted text                     │
│  │ Snowboard    │                                            │
│  │              │                                            │
│  │ $699.95      │  ← Price shown but muted                   │
│  │ 🔴 Sold out  │  ← Red dot + "Sold out"                    │
│  │              │                                            │
│  │ [🔔 Notify]  │  ← "Notify me" button instead of "View"   │
│  │              │                                            │
│  └──────────────┘                                            │
```

---

## Full Conversation Flow Example

Here's a complete max-mode conversation showing how all components work together:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  [← ■■]    🛍 Shopping Assistant                              [🔍]    [✕]   │
│             ● Online · Snow Devil Store                                      │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│                          ┌─ CHAT COLUMN (720px) ─┐                           │
│                          │                        │                           │
│  ┌───────────────────────────────────────────────────────────┐                │
│  │                                                           │                │
│  │  🛍 Hi! I'm your shopping assistant for Snow Devil.       │                │
│  │     I know your 24 products across 5 collections.         │                │
│  │     How can I help?                                       │                │
│  │                                                    12:30  │                │
│  └───────────────────────────────────────────────────────────┘                │
│                                                                              │
│                               ┌──────────────────────────────────┐           │
│                               │  Show me your best sellers       │           │
│                               │                           12:31  │           │
│                               └──────────────────────────────────┘           │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────┐                │
│  │                                                           │                │
│  │  Here are the top products from Snow Devil:               │                │
│  │                                                           │                │
│  │  ┌────────────┐ ┌────────────┐ ┌────────────┐            │                │
│  │  │ ┌────────┐ │ │ ┌────────┐ │ │ ┌────────┐ │            │                │
│  │  │ │  IMG   │ │ │ │  IMG   │ │ │ │  IMG   │ │            │                │
│  │  │ │  🎿   │ │ │ │  🏂   │ │ │ │  🧴   │ │            │                │
│  │  │ └────────┘ │ │ └────────┘ │ │ └────────┘ │            │                │
│  │  │ Snowboard  │ │ Multi-loc  │ │ Ski Wax    │            │                │
│  │  │            │ │ Snowboard  │ │            │            │                │
│  │  │ ~~$885.95~~│ │            │ │ From       │            │                │
│  │  │ $785.95    │ │ $729.95    │ │ $9.95      │            │                │
│  │  │ [SALE]     │ │            │ │ 3 variants │            │                │
│  │  │ ● In stock │ │ ● In stock │ │ ● In stock │            │                │
│  │  └────────────┘ └────────────┘ └────────────┘            │                │
│  │                                                           │                │
│  │  The Snowboard is your best value — $100 off right now.   │                │
│  │  Ski Wax is most popular with 3 size options.             │                │
│  │                                                           │                │
│  │  ┌──────────────┐ ┌──────────────┐                       │                │
│  │  │ ⚖ Compare    │ │ 💬 Ask more  │                       │                │
│  │  └──────────────┘ └──────────────┘                       │                │
│  │                                                    12:31  │                │
│  └───────────────────────────────────────────────────────────┘                │
│                                                                              │
│    [⚖ Compare these]  [🔎 What's on sale?]  [📦 Shipping?]                   │
│                                                                              │
│                               ┌──────────────────────────────────┐           │
│                               │  What should I buy for travel?   │           │
│                               │                           12:32  │           │
│                               └──────────────────────────────────┘           │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────┐                │
│  │                                                           │                │
│  │  🔍 Finding travel-friendly products...                   │                │
│  │                                                           │                │
│  │  ✓ Checked 24 products                                    │                │
│  │  ● Matching for portability & travel                      │                │
│  │  ━━━━━━━━━━━━━━━━━░░░░░░░░░░                              │                │
│  │                                                           │                │
│  └───────────────────────────────────────────────────────────┘                │
│                                                                              │
│                          │                        │                           │
│                          └────────────────────────┘                           │
│                                                                              │
├──────────────────────────────────────────────────────────────────────────────┤
│          ┌──────────────────────────────────────────────────────┐             │
│          │  ┌──┐                                           ┌──┐│             │
│          │  │📎│  Ask about products, shipping, or more...  │ ➤││             │
│          │  └──┘                                           └──┘│             │
│          └──────────────────────────────────────────────────────┘             │
│            🛍 Shopping · Snow Devil Store                                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Mobile Max Mode

On mobile, max mode is full-screen with the same centered design but adapted for touch:

```
┌─────────────────────────┐
│ [←] 🛍 Shopping     [✕] │
│     ● Snow Devil Store  │
├─────────────────────────┤
│                         │
│ ┌─────────────────────┐ │
│ │ 🛍 Hi! I know your  │ │
│ │ 24 products. Ask    │ │
│ │ me anything.        │ │
│ └─────────────────────┘ │
│                         │
│   ┌───────────────────┐ │
│   │ Show me best      │ │
│   │ sellers        ■  │ │
│   └───────────────────┘ │
│                         │
│ ┌─────────────────────┐ │
│ │ Top products:       │ │
│ │                     │ │
│ │ ┌───────┐ ┌───────┐ │ │  ← 2-column grid
│ │ │ IMG   │ │ IMG   │ │ │    on mobile
│ │ │Board  │ │Multi  │ │ │
│ │ │$785   │ │$729   │ │ │
│ │ └───────┘ └───────┘ │ │
│ │ ┌───────┐           │ │
│ │ │ IMG   │           │ │
│ │ │Wax    │           │ │
│ │ │$9.95  │           │ │
│ │ └───────┘           │ │
│ │                     │ │
│ │ [Compare] [More]    │ │
│ └─────────────────────┘ │
│                         │
│ [Compare] [On sale?]    │
│                         │
├─────────────────────────┤
│ ┌─────────────────────┐ │
│ │ Ask anything...  ➤  │ │
│ └─────────────────────┘ │
└─────────────────────────┘
```

**Mobile adaptations:**
- Product cards: 2-column grid (not 3)
- Product detail: single column, full width
- Comparison table: horizontal scroll with frozen first column
- Collection grid: 2-column
- Suggestions: horizontal scroll chips
- Input: full width, no attachment icon (simplified)

---

## Interaction Patterns

### Product Card Tap

```
User taps product card
  → Card gets indigo border + subtle pulse (150ms)
  → Chat input auto-fills: "Tell me about [Product Name]"
  → Message sends automatically
  → AI responds with product detail block (Section 4)
  → Suggestions update to product-specific actions
```

### Suggestion Chip Tap

```
User taps suggestion chip
  → Chip fills with primary color briefly (100ms feedback)
  → Text appears as user message bubble
  → All suggestion chips from this response fade out
  → AI processes and responds
  → New contextual suggestions appear below new response
```

### Scroll Behavior

```
New AI message arrives
  → Chat auto-scrolls to show full response
  → If response is taller than viewport, scroll to top of response
  → If user has scrolled UP (reading history), DON'T auto-scroll
     → Instead, show "↓ New message" pill at bottom
```

### Image Gallery Swipe (Product Detail)

```
User swipes left on product image
  → Next image slides in (200ms ease)
  → Dot indicator updates
  → Swipe right for previous
  → Tap image: opens full-screen lightbox
```

---

## Animations & Transitions

| Animation | Spec |
|---|---|
| Message appear | Fade up (translateY 8px → 0, opacity 0 → 1, 200ms ease) |
| Product cards | Stagger appear: each card 50ms delay after previous |
| Thinking dots | 3 dots, scale 1→1.3→1, staggered 150ms, loop |
| Progress bar | Width 0→100%, continuous ease, 2-4 seconds |
| Card hover lift | translateY -2px + shadow increase, 150ms |
| Suggestion appear | Fade in (opacity 0→1, 150ms) after AI message completes |
| Suggestion disappear | Fade out (100ms) when new message sends |
| Search overlay | Backdrop blur 0→8px + panel slide down, 200ms |
| Welcome → Chat | Welcome cards fade out (200ms), first AI message fades in |

---

## Shopify Data Integration

The chat leverages Shopify's product data to render rich cards. Each AI response that mentions products should include structured metadata:

### Product Card Data

```
{
  "type": "product_cards",
  "products": [
    {
      "id": "gid://shopify/Product/123",
      "title": "Compare at Price Snowboard",
      "image": "https://cdn.shopify.com/.../snowboard.jpg",
      "price": "785.95",
      "compare_at_price": "885.95",
      "currency": "USD",
      "variants_count": 4,
      "available": true,
      "product_type": "Snowboard",
      "vendor": "Snow Devil",
      "handle": "compare-at-price-snowboard"
    }
  ]
}
```

### What the AI currently does vs. what it should do

| Current | Proposed |
|---|---|
| "Selling Plans Ski Wax: Available in three variants, priced from $9.95 to $49.95." | Renders a visual product card with image, name, price, variant count, and stock status |
| "The Out of Stock Snowboard is not currently available." | Renders a muted card with white overlay, "Sold out" badge, and "Notify me" button |
| "The store does not provide a specific best sellers list" | Shows all products in a browsable grid with sorting options |
| Plain text list of products | Horizontal-scroll carousel or 3-column grid with images |

---

## Empty States

### No products match

```
│  ┌───────────────────────────────────────────────┐  │
│  │                                               │  │
│  │         🔍                                    │  │
│  │                                               │  │
│  │   No products match "purple skateboard."      │  │
│  │                                               │  │
│  │   Try:                                        │  │
│  │   [Browse all products]  [See collections]    │  │
│  │                                               │  │
│  └───────────────────────────────────────────────┘  │
```

### Store has few products

If the store has < 10 products, the welcome state shows ALL products instead of "Try Asking" cards:

```
│  ┌───────────────────────────────────────────────┐  │
│  │                                               │  │
│  │  🛍 Welcome! Here's everything in the store:  │  │
│  │                                               │  │
│  │  ┌────────┐ ┌────────┐ ┌────────┐             │  │
│  │  │  IMG   │ │  IMG   │ │  IMG   │             │  │
│  │  │Board   │ │Multi   │ │Wax     │             │  │
│  │  │$785    │ │$729    │ │$9.95   │             │  │
│  │  └────────┘ └────────┘ └────────┘             │  │
│  │  ┌────────┐ ┌────────┐                        │  │
│  │  │  IMG   │ │  IMG   │                        │  │
│  │  │Gift    │ │Out/Stk │                        │  │
│  │  │$25     │ │$699    │                        │  │
│  │  └────────┘ └────────┘                        │  │
│  │                                               │  │
│  │  Tap any product to learn more, or ask me     │  │
│  │  a question below.                            │  │
│  │                                               │  │
│  └───────────────────────────────────────────────┘  │
```

---

## Design Tokens Summary

```
--radius-bubble: 18px
--radius-card: 12px
--radius-button: 8px
--radius-chip: 20px
--radius-input: 24px

--shadow-card: 0 1px 3px rgba(0,0,0,0.06)
--shadow-card-hover: 0 4px 12px rgba(0,0,0,0.08)
--shadow-chat-column: 0 0 24px rgba(0,0,0,0.04)

--transition-fast: 100ms ease
--transition-normal: 200ms ease
--transition-slow: 300ms ease

--chat-column-width: 720px
--chat-padding: 24px
--message-gap: 16px
--group-gap: 24px

--z-header: 100
--z-input-bar: 90
--z-search-overlay: 200
--z-lightbox: 300
```

---

## Implementation Priority

| Phase | What | Why first |
|---|---|---|
| **P0** | Rich product cards in AI responses | Biggest impact — transforms text walls into shoppable content |
| **P0** | Contextual follow-up suggestions | Replaces static chips, guides the conversation |
| **P1** | Welcome state with product grid or "Try Asking" | Eliminates empty-screen problem |
| **P1** | Redesigned processing indicator | Cleaner, less intrusive thinking state |
| **P2** | Product detail expansion | Deep product exploration without leaving chat |
| **P2** | Collection browse grid | Natural category navigation |
| **P3** | Comparison table | Side-by-side evaluation |
| **P3** | Quick search overlay | Power-user shortcut |
| **P4** | Out-of-stock handling, variant selectors | Polish and completeness |
| **P4** | Animations, transitions, lightbox | Delight layer |
