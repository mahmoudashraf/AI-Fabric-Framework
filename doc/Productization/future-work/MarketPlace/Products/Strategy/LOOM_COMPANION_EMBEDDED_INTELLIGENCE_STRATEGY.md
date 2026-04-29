# Loom Companion: Beyond the Chatbot — Embedded Intelligence Strategy

Status: strategy document (2026-04-20)

Note:

- storefront-shape direction in this document is still valid
- pricing authority no longer lives here
- use [LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md](LOOM_COMPANION_PRICING_AND_TIER_STRATEGY.md) for tier decisions
- use [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md) for the resolved milestone order

---

## 1) What This Document Is

This document defines the product strategy for moving Loom Companion from a chatbot widget to an embedded intelligence system. The AI surfaces insights where shoppers already are — on product pages, in search results, in comparisons — without requiring them to open a chat window.

The chatbot remains as one of several interaction surfaces. It is no longer the primary interface.

---

## 2) The Problem with the Chatbot Shape

The current widget follows the industry-standard chatbot pattern: a bubble in the corner, a text input, message bubbles, a conversation thread. This shape has three structural problems:

**Shoppers ignore it.** A decade of bad chatbots trained shoppers to associate chat bubbles with unhelpful keyword bots. Fewer than 5% of store visitors click a chat widget. The other 95% never interact with the AI at all.

**It requires initiation.** The shopper must know what to ask. Most shoppers are browsing — they do not have a specific question. They have vague needs: "is this the right size?" "is this worth the price?" "what's the return situation?" These questions are latent, not formulated. A chat input box does not surface latent questions.

**It is a separate space.** Opening the chat widget takes the shopper's attention away from the product page. Every context switch is friction. The shopper views a product, opens the chat, asks a question, gets an answer, closes the chat, goes back to the product. Four transitions for one answer.

The fix is not a better chatbot. It is moving the AI's intelligence to where the shopper already is.

---

## 3) The Strategic Shift: From Chat to Embedded Intelligence

### Before

The AI lives in a chat bubble. Shoppers must come to it.

### After

The AI's intelligence appears as native page elements — inline blocks on product pages, an intelligent search bar, comparison tables, contextual policy snippets. Shoppers benefit without knowing they are interacting with AI.

The chat companion remains as a deeper interaction layer for shoppers who want a conversation. It is the fallback, not the entry point.

### Why this matters

Every visitor benefits from embedded intelligence. Only 5% would have clicked the chat. The value surface expands from 5% to 100% of traffic.

Merchants see this too. A chatbot is a feature they can skip. "Make every product page smarter" is a capability they evaluate differently — it touches their core business (product pages, search, conversion) rather than sitting in the periphery (a chat widget in the corner).

---

## 4) Six Intelligence Surfaces

### Surface 1: Product Insight Block

An inline block on the product page that shows AI-generated intelligence about the current product.

**What it shows:**
- Review synthesis: "Customers love the fit, but 23% say it runs small"
- Sizing guidance: "Based on reviews, order one size up if between sizes"
- Top use case: "Best for casual daily wear, not suited for running"
- Key concern: "Multiple reviews mention the sole wearing down after 6 months"

**Where it lives:** Shopify theme block. Merchant drags it into their product page template in the theme editor. Appears as a native section of the product page.

**How it works:** On page load, the block calls the runtime's RAG endpoint for the current product handle. The runtime searches the product vector space and the reviews vector space, synthesises the results, and returns structured insights. The block renders them as static-looking content — no chat UI, no loading spinner, no bot aesthetic.

**What it replaces:** Nothing. This content does not exist on most stores. It adds value that was not previously present.

### Surface 2: AI Search Bar

An enhanced search experience that understands natural language queries.

**What it shows:** When the shopper types "running shoes for wide feet under $100," the results page shows:
- An AI summary at the top: "Found 12 running shoes for wide feet under $100. The Brooks GTS is the most recommended by wide-foot reviewers."
- Filtered product results below, ranked by relevance to the query
- A refinement input: "Narrow these results further..."

**Where it lives:** Shopify theme block replacing or augmenting the store's native search bar. Alternatively, a standalone search page accessible from the navigation.

**How it works:** The search query goes to the runtime's RAG endpoint with a search-mode flag. The runtime performs semantic vector search across products, generates a natural language summary, and returns ranked results with explanation snippets. The block renders the summary and products as a search results page.

**What it replaces:** Shopify's native keyword search, which cannot understand "for wide feet" or "under $100 for daily wear."

### Surface 3: Product FAQ Block

Auto-generated frequently asked questions specific to each product.

**What it shows:**
- 3-5 questions that shoppers commonly ask about this product or product category
- Answers grounded in product data, reviews, and policies
- Source attribution: "Based on 47 reviews" or "From our returns policy"
- An input to ask additional questions

**Where it lives:** Shopify theme block on the product page, typically below the description or reviews section.

**How it works:** FAQ content is pre-generated during data sync (when products are indexed) or generated on first request and cached. The runtime analyses the product's reviews and category to identify common question patterns, then generates grounded answers using RAG.

**What it replaces:** Static FAQ pages that are generic and not product-specific. Most stores have a single FAQ page for the entire store. This puts relevant answers on the product page where they are needed.

### Surface 4: Comparison Component

AI-powered product comparison that helps shoppers decide between options.

**What it shows:**
- Side-by-side comparison table with key attributes
- Review-based insights per product ("reviewers say X is best for Y")
- An AI verdict: "For your stated need, Product A is the better choice because..."
- Differences highlighted, not just listed

**Where it lives:** Triggered from product pages ("Compare with similar"), from search results ("Compare these"), or from the companion chat ("compare these two jackets").

**How it works:** The runtime receives 2-4 product handles, retrieves full product data and review summaries from the vector store, and generates a structured comparison. The component renders it as a table with narrative insights.

**What it replaces:** The shopper manually opening multiple tabs and comparing products by memory. No Shopify store currently offers AI-powered comparison.

### Surface 5: Contextual Policy Strip

Relevant policy information shown in context on the page where it matters.

**What it shows:**
- On product pages: "Free returns within 30 days for this category · Ships in 2-3 days"
- On cart page: "Free shipping — you qualify · 30-day returns on all items"
- On collection pages: "All items in this collection include free sizing exchanges"

**Where it lives:** Shopify theme block, placed by the merchant near the add-to-cart button or at the top of the cart page.

**How it works:** The block calls the runtime with the current page context (product handle, cart contents, collection handle). The runtime searches the policy vector space for relevant policies and returns a brief, contextual summary. Content updates automatically when policies change and are re-synced.

**What it replaces:** Fine-print footer links to the returns page. Shoppers do not read footer links. Contextual policy strips put the information where the buying decision happens.

### Surface 6: Companion Chat (existing, repositioned)

The conversational companion for deeper interactions.

**What it shows:** A natural language chat interface where shoppers can ask open-ended questions, get detailed product guidance, and access all capabilities not covered by inline blocks.

**Where it lives:** Expandable from any inline block ("Ask more"), from the contextual pill, or from a minimised chat icon.

**How it repositions:** The chat is no longer the only AI surface. It is the depth layer for shoppers who want a conversation. The inline blocks handle the high-frequency, shallow interactions (sizing, reviews, policies). The chat handles the low-frequency, deep interactions (complex comparisons, multi-step research, edge-case policy questions).

**Why this repositioning matters:** The chat serves 5% of traffic deeply instead of trying to serve 100% of traffic from a bubble nobody clicks.

---

## 5) The Contextual Pill

A persistent, non-intrusive element that replaces the traditional chat bubble.

**What it is:** A horizontal bar or pill at the bottom of the page showing 2-3 key insights about the current context. Always visible, not a floating bubble.

**What it shows (varies by page):**
- Product page: "Runs small · Free returns · 247 reviews"
- Collection page: "12 items on sale · 3 new this week"
- Cart page: "Add $12 more for free shipping · All items returnable"
- Homepage: "Ask about products, policies, or compare options"

**Interaction:** Tapping the pill expands a compact Q&A panel — not a full chat window, but a focused question-and-answer interface for the current page context. The full companion chat is accessible from within the expanded panel.

**Why it replaces the chat bubble:** The chat bubble is a blue circle that communicates nothing. The pill communicates three relevant facts before the shopper interacts. It demonstrates value before asking for engagement.

---

## 6) Implementation Using Shopify Theme Extensions

The companion theme app extension currently contains one block (the chat embed). The strategy is to ship multiple blocks in the same extension:

```
companion-theme-app-extension/
├── blocks/
│   ├── companion-app-embed.liquid         (existing — chat widget)
│   ├── companion-product-insights.liquid   (Surface 1)
│   ├── companion-smart-search.liquid       (Surface 2)
│   ├── companion-product-faq.liquid        (Surface 3)
│   ├── companion-comparison.liquid         (Surface 4)
│   ├── companion-policy-strip.liquid       (Surface 5)
│   └── companion-context-pill.liquid       (replaces chat bubble)
├── assets/
│   ├── companion-max-mode-shell.js        (existing — full shell)
│   ├── companion-blocks.js                (new — lightweight block runtime)
│   └── companion-blocks.css               (new — block styling)
├── snippets/
│   └── companion-widget-bootstrap.liquid   (existing)
└── locales/
    └── en.default.json
```

Merchants install one app. They get seven blocks to place in their theme. The chat widget is one of seven surfaces. The other six show intelligence without any chat interaction required.

### Block Runtime

The inline blocks do not need the full shell runtime. A lightweight JavaScript module handles:
- Fetching insights from the runtime API for the current page context
- Rendering structured content in the block's container
- Expanding to the companion chat when the shopper wants deeper interaction
- Caching responses per product/page to avoid redundant API calls

This keeps the page performance impact minimal. The full shell loads only when the shopper explicitly opens the companion chat.

---

## 7) Architecture: Same Platform, New Shell Components

The platform does not change. The RAG pipeline, action framework, vector sync, and runtime API remain identical. What changes is the shell's component model and how it embeds on the page.

**Runtime API additions:**
- Product insights endpoint: given a product handle, return structured insights (review synthesis, sizing, use case)
- Page context endpoint: given a page context (product/collection/cart), return contextual policy snippets
- Comparison endpoint: given 2-4 product handles, return structured comparison

These are thin wrappers over the existing RAG search. The intelligence is already in the pipeline. These endpoints shape the output for specific component types.

**Shell component additions:**
- InsightBlock: renders structured product insights inline
- SearchResults: renders AI-augmented search results
- FAQBlock: renders auto-generated Q&A
- ComparisonView: renders product comparison table
- PolicyStrip: renders contextual policy snippet
- ContextPill: renders persistent insight bar

**Existing components unchanged:**
- Conversation, MessageBubble, ActionResult, GovernanceDialog, ContextPanel — all remain for the companion chat surface.

---

## 8) Pricing Alignment

The embedded intelligence surfaces integrate into the existing tier structure:

| Surface | Free | Starter ($29) | Elite ($179) |
|---|---|---|---|
| Product Insight Block | — | Included | Included |
| AI Search | Included | Included | Included |
| Product FAQ | — | Included | Included |
| Comparison | — | Included | Included |
| Policy Strip | — | Included | Included |
| Context Pill | — | Included | Included |
| Companion Chat | — | Read-only depth | Read + verified governed actions |

The free tier includes AI search only. Merchants who want the full embedded intelligence surface set upgrade to Starter; Elite is reserved for verified governed actions.

---

## 9) Competitive Differentiation

No Shopify AI app currently ships embedded intelligence blocks. Every competitor is a chat widget.

| Capability | Rep AI | Manifest AI | Tidio | Gorgias | Loom Companion |
|---|---|---|---|---|---|
| Chat widget | Yes | Yes | Yes | Yes | Yes |
| Product page intelligence blocks | No | No | No | No | **Yes** |
| AI-powered search | No | No | No | No | **Yes** |
| Auto-generated product FAQ | No | No | No | No | **Yes** |
| AI product comparison | No | No | No | No | **Yes** |
| Contextual policy strips | No | No | No | No | **Yes** |
| Contextual pill (not bubble) | No | No | No | No | **Yes** |

This is not incremental improvement. It is a different product category. Competitors sell chatbots. Loom Companion sells store intelligence.

---

## 10) Phased Rollout

### Phase 1: Launch with chat + contextual pill (Month 1-2)

Replace the chat bubble with the contextual pill. Ship the companion chat as the depth layer accessible from the pill. This is the minimum viable shift from chatbot to embedded intelligence.

### Phase 2: Product insight block + policy strip (Month 3-4)

Ship the two blocks that provide the most immediate value without interaction. Every product page becomes smarter. Every policy question is answered in context.

### Phase 3: AI search + product FAQ (Month 5-6)

Ship the search replacement and auto-generated FAQ. These require the RAG pipeline to handle higher query volumes and FAQ pre-generation during sync.

### Phase 4: Comparison component (Month 7-8)

Ship the comparison feature. This is the most complex component (multiple products, structured output, narrative generation) and should ship after the simpler surfaces are proven.

---

## 11) Success Metrics

### Surface-level metrics

- **Insight block impression rate:** percentage of product page visitors who see the block (target: 100% of product page views)
- **Insight block expansion rate:** percentage who tap "Ask more" from a block (target: 5-10%)
- **Search usage:** AI search queries per store per day (target: 10+)
- **Comparison usage:** comparisons initiated per store per day (target: 2+)
- **Chat usage:** companion conversations per store per day (target: 3-5 — should decrease as blocks handle shallow queries)

### Business metrics

- **Install conversion improvement:** new installs should increase when the App Store listing shows embedded intelligence, not just a chatbot
- **Merchant retention improvement:** merchants who place 3+ blocks churn less than merchants who only use the chat
- **Upgrade rate improvement:** merchants who see block engagement data upgrade to Starter or, when verified action demand exists, Elite at higher rates

---

## 12) App Store Listing Repositioning

### Current positioning (chatbot)

"Add an AI shopping companion to your store."

### New positioning (embedded intelligence)

"Make every product page smarter. AI-powered product insights, intelligent search, and customer guidance — built into your store, not bolted on."

### Feature order in listing

1. Smart Product Pages — AI review insights and sizing on every product
2. Intelligent Search — shoppers describe what they want in plain language
3. Product Comparison — AI-powered side-by-side with review insights
4. Policy Transparency — relevant policies shown where buying decisions happen
5. Auto-Generated FAQ — product-specific Q&A, always current
6. Companion Chat — for shoppers who want a deeper conversation

The chat is listed last. The intelligence surfaces are listed first.

---

## 13) What This Document Does Not Cover

- Specific UI designs, wireframes, or visual mockups for each surface
- API endpoint specifications for new runtime endpoints
- Performance budgets and caching strategies for inline blocks
- A/B testing methodology for measuring block impact on conversion
- Mobile-specific layout and interaction patterns for each surface
- Accessibility compliance for embedded blocks
- Merchant onboarding flow for block placement in theme editor

These belong in implementation-level documents authored during the build phase.

---

## 14) Summary

The chatbot shape limits the AI's reach to the 5% of shoppers who click a chat bubble. Embedded intelligence surfaces — product insight blocks, AI search, FAQ, comparison tables, policy strips, and the contextual pill — bring the AI's value to 100% of traffic.

The platform does not change. The RAG pipeline, action framework, and runtime are the same. What changes is the shell: new component types that render AI intelligence as native page elements rather than chat messages.

This is the strategic shift from "AI chatbot for your store" to "AI that makes every page in your store smarter." It positions Loom Companion in a category no competitor currently occupies.
