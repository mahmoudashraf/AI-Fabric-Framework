# Shopify AI Assistant — Max Mode Redesign (v3)

## The Concept: Chat as the Cockpit

The chat conversation stays **centered** — that's the user's primary interaction. But the surrounding space becomes a **live, contextual Shopify dashboard** that reacts to the conversation. 

Think of a cockpit: the pilot looks forward (chat), but instruments surround them (product panels, cart, collections, insights). The instruments update based on what the pilot is doing.

**The chat is the brain. The surroundings are the nervous system.**

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                            SMART HEADER                                      │
├──────────────┬───────────────────────────────────┬───────────────────────────┤
│              │                                   │                           │
│   LEFT       │         CENTERED CHAT             │        RIGHT              │
│   RAIL       │         CONVERSATION              │        RAIL               │
│              │                                   │                           │
│  (context    │   (messages, input, AI replies)    │   (live product          │
│   panels)    │                                   │    spotlight)             │
│              │                                   │                           │
├──────────────┴───────────────────────────────────┴───────────────────────────┤
│                          BOTTOM DOCK                                         │
└──────────────────────────────────────────────────────────────────────────────┘
```

The rails are NOT static sidebars. They're **contextual panels** that morph based on what's happening in the chat. Idle? They show discovery content. Browsing products? They show filters and comparisons. Checking out? They show cart and shipping.

---

## Visual Design System

### Color Palette

| Role | Color | Usage |
|---|---|---|
| App background | `#f8f9fb` | Full page base, subtle cool gray |
| Chat column bg | `#ffffff` | Center chat area |
| Rail bg | `transparent` | Rails float over app background |
| Rail card bg | `#ffffff` | Cards in rails |
| Rail card border | `#e8eaed` | Subtle card edges |
| Rail card hover | `#fafbfc` bg + `#6366f1` border | Hover state |
| Header bg | `#ffffff` | Top bar |
| AI bubble bg | `#f3f4f6` | AI message bubbles |
| User bubble bg | `#1a1a2e` | User message, dark navy |
| User bubble text | `#ffffff` | White on dark |
| Primary | `#6366f1` | Indigo — CTAs, active states, accents |
| Primary light | `#eef2ff` | Indigo tint for active rail cards |
| Success | `#10b981` | In stock, positive metrics |
| Warning | `#f59e0b` | Low stock, attention |
| Danger | `#ef4444` | Out of stock, sale badges |
| Muted text | `#6b7280` | Secondary info |
| Dim text | `#9ca3af` | Tertiary info, timestamps |
| Divider | `#f0f1f3` | Section separators |
| Spotlight glow | `rgba(99,102,241,0.06)` | Subtle glow behind active product |

### Typography

| Element | Size | Weight | Color |
|---|---|---|---|
| Header store name | 16px | 600 | `#111827` |
| Rail section title | 11px | 700 | `#6b7280`, uppercase, tracking 0.08em |
| Rail card title | 13px | 600 | `#111827` |
| Rail card subtitle | 12px | 400 | `#6b7280` |
| Rail metric number | 22px | 700 | `#111827` |
| Rail metric label | 11px | 500 | `#9ca3af` |
| Chat message | 14px | 400 | `#374151` |
| Product name (chat) | 14px | 600 | `#111827` |
| Price | 15px | 700 | `#111827` |
| Compare price | 13px | 400 | `#9ca3af`, strikethrough |
| Badge text | 11px | 600 | varies |
| Input placeholder | 14px | 400 | `#9ca3af` |
| Dock label | 11px | 500 | `#6b7280` |

### Spacing & Dimensions

| Element | Value |
|---|---|
| Header height | 56px |
| Left rail width | 260px |
| Right rail width | 300px |
| Chat column | fluid (fills between rails), min 400px, max 640px |
| Rail card padding | 14px |
| Rail card radius | 10px |
| Rail card gap | 10px |
| Rail section gap | 20px |
| Chat bubble radius | 16px |
| Bottom dock height | 72px |
| Product card image | 1:1 aspect in rails, 3:4 in spotlight |

---

## Layout: The Full App

### Idle State (No Conversation Yet)

When the user first opens max mode, the entire screen is an **AI-powered store front**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🛍 Snow Devil Store        [🔍 Search products...]        [Cart (0)]  [✕]  │
├──────────────┬───────────────────────────────────┬───────────────────────────┤
│              │                                   │                           │
│  DISCOVER    │                                   │   SPOTLIGHT               │
│              │                                   │                           │
│  ┌────────┐  │       🛍                           │   ┌─────────────────┐    │
│  │🏂      │  │                                   │   │                 │    │
│  │Snow-   │  │   Welcome to Snow Devil           │   │                 │    │
│  │boards  │  │                                   │   │   HERO PRODUCT  │    │
│  │8 items │  │   I know 24 products across       │   │   IMAGE         │    │
│  └────────┘  │   5 collections. Ask me            │   │                 │    │
│  ┌────────┐  │   anything or explore around.      │   │   (featured/    │    │
│  │🧴      │  │                                   │   │    trending)    │    │
│  │Wax &   │  │                                   │   │                 │    │
│  │Care    │  │                                   │   └─────────────────┘    │
│  │4 items │  │                                   │                           │
│  └────────┘  │                                   │   Compare at Price        │
│  ┌────────┐  │                                   │   Snowboard               │
│  │👕      │  │                                   │                           │
│  │Apparel │  │                                   │   ~~$885.95~~             │
│  │6 items │  │                                   │   $785.95  [SAVE $100]    │
│  └────────┘  │                                   │                           │
│  ┌────────┐  │                                   │   ★★★★☆ 4.2 · ● In stock │
│  │🎿      │  │                                   │                           │
│  │Access- │  │                                   │   [View in Store →]       │
│  │ories   │  │                                   │                           │
│  │12 items│  │                                   │   ─────────────────────   │
│  └────────┘  │                                   │                           │
│              │                                   │   TRENDING                │
│  ─────────   │                                   │                           │
│              │                                   │   ┌──────┐ ┌──────┐      │
│  QUICK ASK   │                                   │   │ IMG  │ │ IMG  │      │
│              │                                   │   │Wax   │ │Board │      │
│  ┌────────┐  │                                   │   │$9.95 │ │$729  │      │
│  │💰 Deals│  │                                   │   └──────┘ └──────┘      │
│  │today   │  │                                   │   ┌──────┐ ┌──────┐      │
│  └────────┘  │                                   │   │ IMG  │ │ IMG  │      │
│  ┌────────┐  │                                   │   │Gift  │ │Multi │      │
│  │📦 Ship-│  │                                   │   │$25   │ │$45   │      │
│  │ping    │  │                                   │   └──────┘ └──────┘      │
│  └────────┘  │                                   │                           │
│  ┌────────┐  │                                   │                           │
│  │↩️ Rtrns│  │                                   │                           │
│  └────────┘  │                                   │                           │
│              │                                   │                           │
├──────────────┼───────────────────────────────────┼───────────────────────────┤
│              │  ┌─────────────────────────────┐  │                           │
│              │  │ Ask about anything...    ➤  │  │                           │
│              │  └─────────────────────────────┘  │                           │
└──────────────┴───────────────────────────────────┴───────────────────────────┘
```

**Left Rail — "Discover":**
- Collection cards with emoji icon, name, item count
- Quick Ask shortcuts (Deals, Shipping, Returns)
- Clicking any card sends a message to chat: "Show me Snowboards"
- These are generated from Shopify collection data

**Center — Chat:**
- Centered conversation with welcome message
- Clean, focused, no clutter
- Input at bottom

**Right Rail — "Spotlight":**
- Hero product: large image, name, price, sale badge, stock, rating
- Auto-rotates every 15s (or based on store's featured product)
- "View in Store" links to Shopify product page
- Below: 2x2 trending product thumbnails
- Clicking any product sends "Tell me about [product]" to chat

**Bottom Dock:**
- Just the input bar, centered within chat column
- Clean, minimal

---

### Browsing State (User Asked About Products)

When the user asks "show me your best sellers" — the rails transform:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🛍 Snow Devil Store        [🔍 Search products...]     [Cart (0)]    [✕]   │
├──────────────┬───────────────────────────────────┬───────────────────────────┤
│              │                                   │                           │
│  FILTERS     │  ┌─────────────────────────────┐  │   PRODUCT SPOTLIGHT      │
│              │  │ 🛍 Here are the top          │  │                           │
│  ┌────────┐  │  │ products:                    │  │   ┌─────────────────┐    │
│  │▣ All   │  │  │                              │  │   │                 │    │
│  └────────┘  │  │ ┌────────┐┌────────┐┌─────┐ │  │   │                 │    │
│  ┌────────┐  │  │ │  IMG   ││  IMG   ││ IMG │ │  │   │   (whichever    │    │
│  │□ Boards│  │  │ │Board   ││Multi   ││ Wax │ │  │   │    product is   │    │
│  └────────┘  │  │ │$785.95 ││$729.95 ││$9.95│ │  │   │    hovered in   │    │
│  ┌────────┐  │  │ │[SALE]  ││        ││3 var│ │  │   │    chat)        │    │
│  │□ Wax   │  │  │ └────────┘└────────┘└─────┘ │  │   │                 │    │
│  └────────┘  │  │                              │  │   └─────────────────┘    │
│  ┌────────┐  │  │ The Snowboard has $100 off.  │  │                           │
│  │□ Gear  │  │  │                              │  │   Compare at Price        │
│  └────────┘  │  │ [Compare] [Deals only]       │  │   Snowboard               │
│              │  │                        12:31 │  │                           │
│  ─────────   │  └─────────────────────────────┘  │   ~~$885.95~~             │
│              │                                   │   $785.95                  │
│  SORT BY     │         ┌─────────────────────┐   │                           │
│              │         │ Which one is best    │   │   ● In stock              │
│  ● Popular   │         │ for beginners?       │   │   Ships in 1-2 days      │
│  ○ Price ↑   │         │               12:32 │   │                           │
│  ○ Price ↓   │         └─────────────────────┘   │   ── Variants ────────    │
│  ○ Newest    │                                   │   [S] [M] [● L] [XL]     │
│              │  ┌─────────────────────────────┐  │                           │
│  ─────────   │  │ For beginners, I'd          │  │   ── Quick Info ──────    │
│              │  │ recommend the Multi-loc      │  │   Type: Snowboard        │
│  PRICE       │  │ Snowboard — it's the most   │  │   Vendor: Snow Devil     │
│              │  │ forgiving board and $56      │  │                           │
│  [$0]──[$900]│  │ cheaper than the Compare     │  │   ┌───────────────────┐  │
│  ●━━━━━━━━●  │  │ at Price model.              │  │   │  🛒 View in Store │  │
│              │  │                              │  │   └───────────────────┘  │
│  ─────────   │  │ [Tell me more] [Compare]     │  │                           │
│              │  │                        12:32 │  │   ┌───────────────────┐  │
│  STOCK       │  └─────────────────────────────┘  │   │  ⚖ Compare        │  │
│              │                                   │   └───────────────────┘  │
│  ● In stock  │  [Tell me more] [Any deals?]      │                           │
│  ○ All       │                                   │                           │
│              │                                   │                           │
├──────────────┼───────────────────────────────────┼───────────────────────────┤
│              │  ┌─────────────────────────────┐  │                           │
│              │  │ Ask about anything...    ➤  │  │                           │
│              │  └─────────────────────────────┘  │                           │
└──────────────┴───────────────────────────────────┴───────────────────────────┘
```

**Left Rail transforms → "Filters":**
- Collection filter checkboxes (filter without typing)
- Sort options (radio buttons)
- Price range slider
- Stock filter
- Clicking a filter sends: "Show me snowboards under $500 in stock" to chat
- Rail title changes from "DISCOVER" to "FILTERS"

**Right Rail transforms → "Product Spotlight":**
- Shows the product the user is hovering over or the AI is recommending
- Large image (3:4 aspect)
- Full details: price, compare-at, stock, shipping, variants
- "View in Store" button → opens Shopify product page
- "Compare" button → adds to comparison
- This panel updates LIVE as the user mouses over different products in chat
- If nothing is hovered, shows the AI's top recommendation

**Hover-to-Spotlight behavior:**
```
User hovers "Multi-loc Snowboard" card in chat
  → Right rail smoothly cross-fades (200ms) to show that product
  → Product card in chat gets subtle indigo left border
  → User moves mouse away → rail returns to last AI recommendation
```

---

### Comparison State

When user says "Compare the snowboards":

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🛍 Snow Devil Store        [🔍 Search products...]     [Cart (0)]    [✕]   │
├──────────────┬───────────────────────────────────┬───────────────────────────┤
│              │                                   │                           │
│  COMPARING   │                                   │   VS.                     │
│  2 products  │  ┌─────────────────────────────┐  │                           │
│              │  │                              │  │   ┌───────────────────┐  │
│  ┌────────┐  │  │ Here's how they compare:     │  │   │ ┌──────┐┌──────┐ │  │
│  │ ┌────┐ │  │  │                              │  │   │ │ IMG  ││ IMG  │ │  │
│  │ │IMG │ │  │  │ [comparison table in chat]   │  │   │ │Board ││Multi │ │  │
│  │ └────┘ │  │  │                              │  │   │ └──────┘└──────┘ │  │
│  │Board   │  │  │                        12:33 │  │   │                   │  │
│  │$785.95 │  │  └─────────────────────────────┘  │   │ ─────────────────│  │
│  │ ✓ pick │  │                                   │   │ Price             │  │
│  └────────┘  │                                   │   │ $785 vs $729     │  │
│  ┌────────┐  │                                   │   │ 🏆 Multi wins     │  │
│  │ ┌────┐ │  │                                   │   │                   │  │
│  │ │IMG │ │  │                                   │   │ Savings           │  │
│  │ └────┘ │  │                                   │   │ $100 vs —        │  │
│  │Multi   │  │                                   │   │ 🏆 Board wins     │  │
│  │$729.95 │  │                                   │   │                   │  │
│  │ ✓ pick │  │                                   │   │ Variants          │  │
│  └────────┘  │                                   │   │ 4 vs 2           │  │
│              │                                   │   │ 🏆 Board wins     │  │
│  ─────────   │                                   │   │                   │  │
│              │                                   │   │ ─────────────────│  │
│  [+ Add      │                                   │   │                   │  │
│   product]   │                                   │   │ 💡 VERDICT        │  │
│              │                                   │   │ Board: better     │  │
│  ─────────   │                                   │   │ value with $100  │  │
│              │                                   │   │ savings + more   │  │
│  AI PICK     │                                   │   │ variant options.  │  │
│  ┌────────┐  │                                   │   │                   │  │
│  │ 🏆     │  │                                   │   │ Multi: $56       │  │
│  │ Board  │  │                                   │   │ cheaper base     │  │
│  │ Best   │  │                                   │   │ price, good for  │  │
│  │ value  │  │                                   │   │ budget buyers.   │  │
│  └────────┘  │                                   │   │                   │  │
│              │                                   │   └───────────────────┘  │
│              │                                   │                           │
├──────────────┼───────────────────────────────────┼───────────────────────────┤
│              │  ┌─────────────────────────────┐  │                           │
│              │  │ Ask about anything...    ➤  │  │                           │
│              │  └─────────────────────────────┘  │                           │
└──────────────┴───────────────────────────────────┴───────────────────────────┘
```

**Left Rail → "Comparing":**
- Stacked product cards being compared (with checkmarks)
- "+ Add product" button to add a third item
- "AI Pick" badge on the winner
- Clicking a product in the rail highlights it in the comparison

**Right Rail → "VS." Comparison Card:**
- Side-by-side images at top
- Row-by-row attribute comparison with trophy emoji on winner
- AI Verdict box at bottom with nuanced recommendation
- This is a persistent summary that stays visible while the user scrolls the chat

---

### Single Product Focus State

When the user asks "Tell me more about the Snowboard":

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🛍 Snow Devil Store        [🔍 Search products...]     [Cart (0)]    [✕]   │
├──────────────┬───────────────────────────────────┬───────────────────────────┤
│              │                                   │                           │
│  ABOUT THIS  │                                   │   ┌─────────────────────┐│
│  PRODUCT     │  ┌─────────────────────────────┐  │   │                     ││
│              │  │                              │  │   │                     ││
│  ┌────────┐  │  │ The Compare at Price         │  │   │                     ││
│  │        │  │  │ Snowboard is an all-mountain │  │   │    LARGE PRODUCT   ││
│  │  IMG   │  │  │ board perfect for            │  │   │    IMAGE           ││
│  │  (sm)  │  │  │ intermediate to advanced     │  │   │    (high-res)      ││
│  │        │  │  │ riders...                    │  │   │                     ││
│  └────────┘  │  │                              │  │   │                     ││
│              │  │ [Size guide] [Compare]        │  │   │                     ││
│  ~~$885.95~~ │  │                        12:34 │  │   │                     ││
│  $785.95     │  └─────────────────────────────┘  │   └─────────────────────┘│
│  [SAVE $100] │                                   │   ○ ○ ● ○  (image dots)  │
│              │                                   │                           │
│  ─────────   │                                   │   ── DETAILS ──────────  │
│              │                                   │                           │
│  VARIANTS    │                                   │   Type: Snowboard        │
│              │                                   │   Vendor: Snow Devil     │
│  Size:       │                                   │   SKU: SNOW-CMP-001     │
│  [S] [M]     │                                   │   Weight: 3.2kg         │
│  [● L] [XL]  │                                   │                           │
│              │                                   │   ── SHIPPING ─────────  │
│  Color:      │                                   │                           │
│  [⚫] [⚪]   │                                   │   📦 Free standard       │
│  [🔴]        │                                   │   🚀 Express $9.99       │
│              │                                   │   ⚡ Next day $19.99      │
│  ─────────   │                                   │                           │
│              │                                   │   ─────────────────────  │
│  ● In stock  │                                   │                           │
│  Ships 1-2d  │                                   │   ┌───────────────────┐  │
│              │                                   │   │                   │  │
│  ─────────   │                                   │   │  🛒 View in Store │  │
│              │                                   │   │                   │  │
│  ┌────────┐  │                                   │   └───────────────────┘  │
│  │🛒 View │  │                                   │                           │
│  │in Store│  │                                   │                           │
│  └────────┘  │                                   │                           │
│              │                                   │                           │
├──────────────┼───────────────────────────────────┼───────────────────────────┤
│              │  ┌─────────────────────────────┐  │                           │
│              │  │ Ask about anything...    ➤  │  │                           │
│              │  └─────────────────────────────┘  │                           │
└──────────────┴───────────────────────────────────┴───────────────────────────┘
```

**Left Rail → "About This Product":**
- Small product image
- Price with sale badge
- Variant selectors (interactive — selecting updates the right rail image)
- Stock + shipping estimate
- "View in Store" button

**Right Rail → Full Product Gallery:**
- Large hero image (high-res, 3:4 or 1:1)
- Swipeable image dots (if multiple images)
- Product details: specs, shipping info
- "View in Store" primary CTA
- Changes image when variant is selected on left rail

**The chat stays conversational** — the AI describes the product, answers questions. The rails provide the **visual/interactive** layer so the chat doesn't need to carry product images and specs inline.

---

### Policy / Info State

When user asks "What's your shipping policy?":

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🛍 Snow Devil Store        [🔍 Search products...]     [Cart (0)]    [✕]   │
├──────────────┬───────────────────────────────────┬───────────────────────────┤
│              │                                   │                           │
│  STORE INFO  │                                   │   📋 POLICY DETAILS      │
│              │  ┌─────────────────────────────┐  │                           │
│  ┌────────┐  │  │ Here's our shipping policy:  │  │   ┌───────────────────┐  │
│  │ 📦     │  │  │                              │  │   │                   │  │
│  │Shipping│  │  │ Standard shipping is free    │  │   │  SHIPPING         │  │
│  │● active│  │  │ on all orders...             │  │   │  ─────────────    │  │
│  └────────┘  │  │                              │  │   │                   │  │
│  ┌────────┐  │  │ [Returns?] [Track order?]    │  │   │  Standard         │  │
│  │ ↩️     │  │  │                        12:35 │  │   │  FREE · 5-7 days  │  │
│  │Returns │  │  └─────────────────────────────┘  │   │                   │  │
│  └────────┘  │                                   │   │  ─ ─ ─ ─ ─ ─ ─   │  │
│  ┌────────┐  │                                   │   │                   │  │
│  │ 💳     │  │                                   │   │  Express           │  │
│  │Payment │  │                                   │   │  $9.99 · 2-3 days │  │
│  └────────┘  │                                   │   │                   │  │
│  ┌────────┐  │                                   │   │  ─ ─ ─ ─ ─ ─ ─   │  │
│  │ ❓     │  │                                   │   │                   │  │
│  │FAQ     │  │                                   │   │  Next Day          │  │
│  └────────┘  │                                   │   │  $19.99            │  │
│              │                                   │   │  Order by 2pm EST │  │
│              │                                   │   │                   │  │
│              │                                   │   │  ─────────────    │  │
│              │                                   │   │                   │  │
│              │                                   │   │  ✓ All orders     │  │
│              │                                   │   │    include        │  │
│              │                                   │   │    tracking       │  │
│              │                                   │   │                   │  │
│              │                                   │   │  ✓ International  │  │
│              │                                   │   │    available      │  │
│              │                                   │   │                   │  │
│              │                                   │   │  ✓ Free returns   │  │
│              │                                   │   │    within 30 days │  │
│              │                                   │   │                   │  │
│              │                                   │   └───────────────────┘  │
│              │                                   │                           │
├──────────────┼───────────────────────────────────┼───────────────────────────┤
│              │  ┌─────────────────────────────┐  │                           │
│              │  │ Ask about anything...    ➤  │  │                           │
│              │  └─────────────────────────────┘  │                           │
└──────────────┴───────────────────────────────────┴───────────────────────────┘
```

**Left Rail → "Store Info" navigation:**
- Policy topic buttons (Shipping, Returns, Payment, FAQ)
- Active topic highlighted with indigo left border
- Clicking switches the right rail content AND sends a question to chat

**Right Rail → "Policy Details" card:**
- Clean, structured policy card
- Not a wall of text — formatted with icons, dividers, checkmarks
- Persistent while user scrolls chat
- Acts as a **reference card** — the user can read the full policy on the right while asking follow-up questions in chat

---

### Shopping / Cart State

When the conversation reaches buying intent or user says "I'll take the Snowboard":

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  🛍 Snow Devil Store        [🔍 Search products...]     [Cart (1)] ●  [✕]   │
├──────────────┬───────────────────────────────────┬───────────────────────────┤
│              │                                   │                           │
│  YOUR PICKS  │                                   │   🛒 CART SUMMARY        │
│              │  ┌─────────────────────────────┐  │                           │
│  ┌────────┐  │  │ Great choice! The Compare    │  │   ┌───────────────────┐  │
│  │ ┌────┐ │  │  │ at Price Snowboard in L/     │  │   │                   │  │
│  │ │IMG │ │  │  │ Black is ready.              │  │   │  ┌─────┐          │  │
│  │ └────┘ │  │  │                              │  │   │  │ IMG │ Board    │  │
│  │Board L │  │  │ [View in Store →]            │  │   │  └─────┘          │  │
│  │$785.95 │  │  │                        12:36 │  │   │  Size: L          │  │
│  │ ✓      │  │  └─────────────────────────────┘  │   │  Color: Black     │  │
│  └────────┘  │                                   │   │  $785.95     1x   │  │
│              │                                   │   │                   │  │
│  ─────────   │                                   │   │  ─────────────    │  │
│              │                                   │   │                   │  │
│  YOU MIGHT   │                                   │   │  Subtotal $785.95 │  │
│  ALSO LIKE   │                                   │   │  Shipping   FREE  │  │
│              │                                   │   │  ─────────────    │  │
│  ┌────────┐  │                                   │   │  Total   $785.95  │  │
│  │ ┌────┐ │  │                                   │   │                   │  │
│  │ │IMG │ │  │                                   │   │ ┌───────────────┐ │  │
│  │ └────┘ │  │                                   │   │ │ 🛒 Checkout → │ │  │
│  │Wax     │  │                                   │   │ └───────────────┘ │  │
│  │$9.95   │  │                                   │   │                   │  │
│  │[+ Add] │  │                                   │   └───────────────────┘  │
│  └────────┘  │                                   │                           │
│  ┌────────┐  │                                   │   ── ALSO BOUGHT ──────  │
│  │ ┌────┐ │  │                                   │                           │
│  │ │IMG │ │  │                                   │   ┌──────┐ ┌──────┐      │
│  │ └────┘ │  │                                   │   │ IMG  │ │ IMG  │      │
│  │Binding │  │                                   │   │Wax   │ │Bind  │      │
│  │$149    │  │                                   │   │$9.95 │ │$149  │      │
│  │[+ Add] │  │                                   │   └──────┘ └──────┘      │
│  └────────┘  │                                   │                           │
│              │                                   │                           │
├──────────────┼───────────────────────────────────┼───────────────────────────┤
│              │  ┌─────────────────────────────┐  │                           │
│              │  │ Ask about anything...    ➤  │  │                           │
│              │  └─────────────────────────────┘  │                           │
└──────────────┴───────────────────────────────────┴───────────────────────────┘
```

**Left Rail → "Your Picks" + "You Might Also Like":**
- Products discussed/favorited during conversation
- Cross-sell recommendations with "+ Add" buttons
- Clicking "+ Add" adds to cart (right rail updates)

**Right Rail → "Cart Summary":**
- Product thumbnail + name + variant + price
- Subtotal, shipping, total
- "Checkout →" button → opens Shopify checkout
- "Also Bought" recommendations below cart
- Cart badge on header updates: `[Cart (1)] ●`

---

## Header Bar Design

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│  [← ■■]  🛍 Snow Devil Store    [🔍 Search products...]    🛒 Cart (0)  [✕] │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

| Element | Spec |
|---|---|
| Minimize | `← ■■` returns to chat widget mode. 32px tap target |
| Store icon | Store favicon or shopping bag, 24px |
| Store name | 16px, weight 600 |
| Search bar | Pill input, `#f3f4f6` bg, 🔍 icon left, 200px width, expands on focus |
| Cart | Shopping bag icon + count badge. Badge: indigo circle, white number |
| Close | ✕ icon, muted, 32px tap target |
| Background | White, bottom shadow `0 1px 0 #e8eaed` |
| Height | 56px |

**Search bar behavior:**
- Typing filters products in real-time (dropdown below)
- Selecting a product sends "Tell me about [product]" to chat
- Acts as a power-user shortcut — doesn't replace chat

---

## Thinking / Processing State (Redesigned)

The current processing card is too heavy. New design: a compact, elegant indicator within the AI bubble area:

**Simple query (fast response):**
```
│  ┌─────────────────────┐    │
│  │  ● ● ●              │    │
│  └─────────────────────┘    │
```
Three dots in an AI bubble, pulsing animation. That's it.

**Complex query (multi-step):**
```
│  ┌───────────────────────────────────┐    │
│  │                                   │    │
│  │  🔍 Searching products...         │    │
│  │                                   │    │
│  │  ✓ Scanned 24 products            │    │
│  │  ✓ Checked availability           │    │
│  │  ● Finding best matches           │    │
│  │  ○ Preparing response             │    │
│  │                                   │    │
│  └───────────────────────────────────┘    │
```

| Element | Spec |
|---|---|
| Container | Same style as AI bubble (`#f3f4f6`, left-aligned) |
| Width | Same max-width as AI bubbles — NOT full-width |
| Title | 14px, weight 600, with contextual icon |
| Steps | 13px, regular weight |
| ✓ done | Green `#10b981` |
| ● active | Indigo `#6366f1`, pulsing |
| ○ pending | Gray `#d1d5db` |
| Progress bar | REMOVED — the step list IS the progress indicator |

**Key improvement:** No more oversized blue card. The thinking state matches the chat bubble style, so the transition to the actual response is seamless.

---

## Rail Transition Map

The rails morph based on conversation context. Here's the complete state map:

| Chat Context | Left Rail | Right Rail |
|---|---|---|
| **Idle** (no messages) | Discover: collections + quick ask | Spotlight: featured product + trending grid |
| **Browsing products** | Filters: categories, sort, price, stock | Product Spotlight: hovered/recommended product |
| **Single product focus** | Product Info: image, price, variants, stock | Product Gallery: large image, details, shipping |
| **Comparing** | Comparing: stacked products, + add, AI pick | VS Card: side-by-side comparison + verdict |
| **Policy / info** | Store Info: topic navigation | Policy Details: structured info card |
| **Cart / buying** | Your Picks: discussed products + cross-sells | Cart Summary: items, total, checkout |
| **General chat** | Discover (default) | Spotlight (default) |

### Transition Animation

```
Rail content change:
  → Current content fades out (opacity 1→0, 150ms)
  → 50ms pause
  → New content fades in (opacity 0→1, 200ms, translateY 8px→0)
  → Total: ~400ms, feels smooth, not jarring
```

---

## Bottom Dock

The input area is clean and minimal:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                                                                              │
│              ┌─────────────────────────────────────────────┐                  │
│              │ 📎  Ask about products or policies...   ➤  │                  │
│              └─────────────────────────────────────────────┘                  │
│               Powered by AI · Snow Devil Store                               │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

| Element | Spec |
|---|---|
| Background | White, top border 1px `#f0f1f3` |
| Input | Centered, width matches chat column (max 640px), pill shape |
| Padding | 14px horizontal, 12px vertical inside bar |
| 📎 icon | Left inside input, muted, for image-based product search (optional) |
| Send ➤ | Right inside input, 32px indigo circle, white arrow |
| Footer text | "Powered by AI · [Store name]" — 11px, `#9ca3af`, centered |
| Height | 72px total |

**No suggestion chips in the dock.** Suggestions appear as follow-up buttons inside AI responses and as contextual options in the left rail. The input area stays clean.

---

## Contextual Follow-Ups (In-Chat)

After each AI response, context-aware follow-up buttons appear:

```
│  ┌─────────────────────────────────────────────┐  │
│  │                                             │  │
│  │  (AI response about products)               │  │
│  │                                             │  │
│  │  [⚖ Compare] [💰 Deals only] [📦 Shipping] │  │
│  │                                      12:31  │  │
│  └─────────────────────────────────────────────┘  │
```

| After context... | Show buttons... |
|---|---|
| Multiple products shown | "Compare these", "Sort by price", "Any on sale?" |
| Single product detail | "Check sizes", "Similar products", "View in store" |
| Policy info | Related policies, "Talk to human" |
| Comparison shown | "Which one for [use case]?", "View winner in store" |
| Cart updated | "Continue shopping", "Checkout", "Remove item" |
| No results | "Browse all", "Try different search" |

**Buttons spec:**
- Inside the AI bubble, below the content
- Pill shape, `#f3f4f6` bg, `#374151` text, 1px `#e5e7eb` border
- Hover: `#6366f1` bg, white text
- Click: sends button text as user message
- Only the LATEST AI message shows follow-ups — previous ones auto-hide

---

## Mobile Max Mode

On mobile (< 768px), the rails collapse. The experience becomes a **swipeable 3-panel layout**:

```
┌─────────────────────────────────────────────────────────────────┐
│  🛍 Snow Devil          [🔍]  [🛒]  [✕]                        │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│              ← swipe →                                          │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │              │  │              │  │              │          │
│  │  LEFT PANEL  │  │  CHAT        │  │  RIGHT PANEL │          │
│  │  (Discover/  │  │  (centered,  │  │  (Spotlight/  │          │
│  │   Filters)   │  │   default)   │  │   Details)   │          │
│  │              │  │              │  │              │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                 │
│                         ○  ●  ○                                 │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────┐  │
│  │  Ask about anything...                                ➤  │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

**Mobile behavior:**
- Chat is the default center panel
- Swipe left → reveals right rail (product spotlight / cart)
- Swipe right → reveals left rail (discover / filters)
- Dot indicators show current panel
- Input bar persists on all panels
- When AI recommends a product, a subtle "→" indicator pulses on the right edge, hinting to swipe

**Alternative: Bottom sheet approach for mobile:**
```
┌─────────────────────────┐
│ 🛍 Snow Devil     [🔍][✕]│
├─────────────────────────┤
│                         │
│  (Chat messages fill    │
│   the screen)           │
│                         │
│   ┌───────────────────┐ │
│   │ AI response...    │ │
│   │ [Compare] [More]  │ │
│   └───────────────────┘ │
│                         │
├─────────────────────────┤
│ ┌─ drag handle ───────┐ │
│ │ ≡ Product Details    │ │  ← bottom sheet, drag up
│ │                      │ │     to reveal product info
│ │  ┌─────┐ Board       │ │
│ │  │ IMG │ $785.95     │ │
│ │  └─────┘ ● In stock  │ │
│ │  [View in Store →]   │ │
│ └──────────────────────┘ │
├─────────────────────────┤
│ Ask anything...      ➤  │
└─────────────────────────┘
```

- Bottom sheet slides up when a product is being discussed
- Drag handle to expand/collapse
- Collapsed: shows product summary (name + price)
- Expanded: full details, variants, "View in Store"
- Sheet auto-appears when AI mentions a product, auto-hides when topic changes

---

## Product Card Rendering in Chat

When the AI's response includes products, they render as compact visual cards WITHIN the AI bubble:

### Grid Layout (1-3 products):

```
│  ┌────────────────────────────────────────────────────┐  │
│  │                                                    │  │
│  │  Here are the best sellers:                        │  │
│  │                                                    │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐           │  │
│  │  │ ┌──────┐ │ │ ┌──────┐ │ │ ┌──────┐ │           │  │
│  │  │ │ IMG  │ │ │ │ IMG  │ │ │ │ IMG  │ │           │  │
│  │  │ └──────┘ │ │ └──────┘ │ │ └──────┘ │           │  │
│  │  │ Board    │ │ Multi    │ │ Ski Wax  │           │  │
│  │  │ $785.95  │ │ $729.95  │ │ $9.95    │           │  │
│  │  │ [SALE]   │ │ ● stock  │ │ 3 types  │           │  │
│  │  └──────────┘ └──────────┘ └──────────┘           │  │
│  │                                                    │  │
│  │  The Board has $100 off right now.                 │  │
│  │  [⚖ Compare] [💬 Ask about one]                   │  │
│  │                                             12:31 │  │
│  └────────────────────────────────────────────────────┘  │
```

### Scroll Layout (4+ products):

```
│  ┌────────────────────────────────────────────────────┐  │
│  │                                                    │  │
│  │  All 8 snowboard products:                         │  │
│  │                                                    │  │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐  →   │  │
│  │  │  IMG   │ │  IMG   │ │  IMG   │ │  IMG   │      │  │
│  │  │ Board  │ │ Multi  │ │ Out/St │ │ Pro    │      │  │
│  │  │ $785   │ │ $729   │ │ $699   │ │ $999   │      │  │
│  │  └────────┘ └────────┘ └────────┘ └────────┘      │  │
│  │                                    ← scroll →      │  │
│  │                                                    │  │
│  │  Scroll to see all 8. The Board is on sale.        │  │
│  │  [Filter by price] [In stock only]                 │  │
│  │                                             12:32 │  │
│  └────────────────────────────────────────────────────┘  │
```

### Out-of-Stock Card:

```
┌──────────┐
│ ┌──────┐ │
│ │░░░░░░│ │  ← 40% white overlay
│ │░IMG░░│ │
│ └──────┘ │
│ Out/Stock │  ← muted text
│ Board     │
│ $699.95   │  ← muted price
│ 🔴 Sold   │
│ [🔔 Alert]│  ← "Notify me" instead of price emphasis
└──────────┘
```

---

## Empty & Edge States

### Store has no products

```
Left rail:     (empty — show "No collections yet")
Chat:          "This store doesn't have any products yet. Check back soon!"
Right rail:    (empty — show store info / branding)
```

### Search returns nothing

```
Chat bubble:
┌──────────────────────────────────────────┐
│                                          │
│  🔍 No products match "purple kayak"     │
│                                          │
│  I searched all 24 products and           │
│  5 collections. Try:                      │
│                                          │
│  [Browse all products]                   │
│  [See collections]                       │
│  [Ask differently]                       │
│                                          │
└──────────────────────────────────────────┘

Right rail: reverts to Spotlight (featured product)
Left rail: reverts to Discover
```

### AI can't answer

```
Chat bubble:
┌──────────────────────────────────────────┐
│                                          │
│  I can help with products, shipping,      │
│  returns, and store policies. For other   │
│  questions, reach out to the store:       │
│                                          │
│  [📧 Contact store]  [💬 Try another Q]  │
│                                          │
└──────────────────────────────────────────┘
```

---

## Interaction Patterns

### Hover-to-Spotlight

```
User hovers product card in chat
  → 100ms delay (prevents flicker on fast mouse movement)
  → Right rail smoothly transitions to show that product (200ms cross-fade)
  → Product card in chat gets subtle left border (2px indigo)
  → Mouse leaves → 300ms delay → rail returns to default/recommendation
```

### Click-to-Focus

```
User clicks product card in chat
  → Card pulse animation (100ms)
  → "Tell me about [product]" sent as user message
  → Both rails transition to Single Product Focus state
  → AI responds with detailed product info
```

### Rail Card Click

```
User clicks a collection card in the left rail
  → "Show me [collection name]" sent as user message
  → Chat receives response with product cards
  → Left rail transitions to Filters
  → Right rail shows first product in Spotlight
```

### Filter Interaction

```
User clicks "Under $500" filter in left rail
  → "Show me products under $500" sent as user message
  → Active filter highlighted with indigo bg
  → AI responds with filtered results
  → Right rail updates to show top match
```

### Cart Flow

```
User says "I'll take the snowboard in size L"
  → Cart badge in header updates: Cart (1) with pulse animation
  → Left rail transitions to "Your Picks" + cross-sells
  → Right rail transitions to "Cart Summary"
  → AI confirms with "View in Store" link (checkout happens on Shopify, not in chat)
```

---

## Animation Specifications

| Animation | Spec |
|---|---|
| Rail transition | Cross-fade: opacity swap over 200ms, content slides up 8px |
| Message appear | Fade up: translateY 8px→0, opacity 0→1, 200ms ease-out |
| Product cards stagger | Each card 60ms delay after previous |
| Thinking dots | 3 circles, scale 0.6→1→0.6, staggered 150ms, infinite |
| Thinking steps | Each step fades in as it activates, ✓ appears with scale-bounce |
| Card hover lift | translateY 0→-2px, shadow increase, 150ms ease |
| Spotlight change | Cross-fade 200ms, slight scale 0.98→1 on new content |
| Cart badge pulse | Scale 1→1.3→1 on update, 300ms |
| Scroll hint (→) | Opacity pulse 0.3→1→0.3, 2s loop |
| Welcome → Chat | Welcome cards fade out 200ms, first AI bubble fades in |
| Rail collapse (mobile) | Slide left/right 300ms ease-out |
| Bottom sheet (mobile) | Slide up with spring physics, backdrop fade |

---

## Design Tokens

```
/* Layout */
--rail-left-width: 260px;
--rail-right-width: 300px;
--chat-column-min: 400px;
--chat-column-max: 640px;
--header-height: 56px;
--dock-height: 72px;

/* Radius */
--radius-card: 10px;
--radius-bubble: 16px;
--radius-button: 8px;
--radius-chip: 20px;
--radius-input: 24px;
--radius-product-image: 8px;

/* Shadows */
--shadow-rail-card: 0 1px 2px rgba(0,0,0,0.04);
--shadow-rail-card-hover: 0 4px 12px rgba(0,0,0,0.06);
--shadow-chat-column: 0 0 1px rgba(0,0,0,0.08);
--shadow-header: 0 1px 0 #e8eaed;

/* Transitions */
--t-fast: 100ms ease;
--t-normal: 200ms ease-out;
--t-slow: 300ms ease-out;
--t-spring: 400ms cubic-bezier(0.34, 1.56, 0.64, 1);

/* Z-index */
--z-rails: 10;
--z-chat: 20;
--z-header: 100;
--z-dock: 90;
--z-search-overlay: 200;
--z-mobile-sheet: 150;
```

---

## Responsive Breakpoints

| Breakpoint | Layout |
|---|---|
| **≥ 1280px** | Full 3-column: left rail + chat + right rail |
| **1024–1279px** | 2-column: chat + right rail. Left rail becomes a slide-out drawer (hamburger) |
| **768–1023px** | Chat only + bottom sheet for product details. Rails accessible via tab icons in header |
| **< 768px** | Full-screen chat + swipeable panels or bottom sheet (see mobile section) |

### 1024–1279px (Tablet Landscape):

```
┌──────────────────────────────────────────────────────────────────┐
│  [☰]  🛍 Snow Devil        [🔍 Search...]     [🛒 Cart (0)] [✕] │
├──────────────────────────────────────┬───────────────────────────┤
│                                      │                           │
│        CENTERED CHAT                 │     RIGHT RAIL            │
│        (expanded)                    │     (always visible)      │
│                                      │                           │
├──────────────────────────────────────┴───────────────────────────┤
│  Ask about anything...                                       ➤  │
└──────────────────────────────────────────────────────────────────┘

[☰] opens left rail as slide-out drawer with backdrop overlay
```

---

## Implementation Priority

| Phase | What | Impact |
|---|---|---|
| **P0** | 3-column layout + rail framework | Eliminates empty space, establishes the app shell |
| **P0** | Idle state rails (Discover + Spotlight) | First impression — users see a living store, not a blank chat |
| **P0** | Product cards in AI responses (replacing text) | Single biggest UX improvement — products become visual |
| **P1** | Rail state transitions (Idle → Browse → Focus) | Rails become contextually useful, not static |
| **P1** | Redesigned thinking indicator | Cleaner, less intrusive processing state |
| **P1** | Contextual follow-up buttons | Guides conversation without static chips |
| **P2** | Hover-to-Spotlight behavior | Delightful — products come alive on hover |
| **P2** | Filter rail + sort/price/stock controls | Power browsing without typing |
| **P2** | Comparison rail state | Side-by-side evaluation with AI verdict |
| **P3** | Cart rail state | Shopping journey completion |
| **P3** | Mobile: swipeable panels or bottom sheet | Full mobile experience |
| **P3** | Search overlay | Power-user product search |
| **P4** | Variant selectors in left rail | Interactive product configuration |
| **P4** | Animations, transitions, polish | Delight layer |
