# Shopify Companion Storefront Product Shell Roadmap

Status: Phase 1 detailed roadmap (2026-04-24)

This roadmap details the `Storefront Product Shell` phase from the active Shopify Companion strategy.

It should be read with:

- [SHOPIFY_COMPANION_LAUNCH_TRUTH.md](SHOPIFY_COMPANION_LAUNCH_TRUTH.md)
- [SHOPIFY_COMPANION_FINDINGS_ROADMAP.md](SHOPIFY_COMPANION_FINDINGS_ROADMAP.md)
- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [../LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md](../LOOM_COMPANION_EMBEDDED_INTELLIGENCE_STRATEGY.md)

---

## 1) Purpose

Phase 1 makes the shopper-facing product visibly real.

The goal is not to make a cleaner chat widget. The goal is to make Shopify Companion feel like embedded store intelligence across product pages, search, policy moments, comparison decisions, and deeper shopper questions.

Canonical product shape:

- embedded surfaces are the default value layer
- chat is the depth layer
- Max Mode is the long-term shopper shell
- Shopify bridge logic fetches evidence; the runtime reasons over it
- merchant-placeable blocks are first-class product surfaces

---

## 2) Phase Boundary

Phase 1 owns:

- shopper-facing host convergence
- embedded intelligence surface architecture
- Max Mode shell contract
- conversation mode semantics
- page context and attached-target behavior
- read-first retrieval and rendering quality
- merchant-safe controls needed to place and verify surfaces

Phase 1 does not own:

- Elite governed actions
- order lookup
- support ticket creation
- return/exchange flows
- public partner portal
- WooCommerce
- broad product-factory abstraction
- passive acquisition or white-label partner programs

Free-tier rule still applies:

- Free exposes AI search only.
- No order lookup, chat, product FAQ, comparison, product insight, policy strip, or contextual pill in Free.

---

## 3) Non-Negotiable Decisions

### 3.1 Max Mode Is The Long-Term Shell

The storefront should not keep two long-term shopper shells.

Target:

- Max Mode is the only supported long-term shopper shell.
- Legacy chat UI is removed as a long-term product surface.
- Any fallback is temporary, bounded, and invisible in launch positioning.

### 3.2 Chat Is The Depth Layer

The product should not ask shoppers to begin with a blank chat input.

Target:

- product insight, FAQ, search, comparison, policy, and contextual pill create value before chat
- each surface can hand off to Max Mode for depth
- chat receives page context and explicit attachments when relevant

### 3.3 Fetch Evidence, Then Reason

Shopify-specific heuristic intelligence should be retired.

Target:

- bridge tools fetch product, policy, collection, review, article, metafield, and metaobject evidence
- runtime reasoning produces shopper-facing answers
- rule-based comparison, similarity scoring, and policy keyword matching are removed from the shopper intelligence path

### 3.4 Page Context Is Not Attachment

Automatic page context and explicit shopper-selected targets are different concepts.

Target:

- page context follows the page automatically
- attached targets are explicit objects the shopper or Companion-owned cards add to Max Mode
- theme-native instrumentation is optional and bounded

### 3.5 No Operator Language On Storefront

Shopper and merchant surfaces should not leak internal platform terms.

Avoid storefront language such as:

- vectorization
- deployment
- provider
- runner
- reindex job
- raw diagnostics
- runtime failure

Use shopper-safe language:

- syncing store knowledge
- using product details
- using reviews
- using store policies
- checking current page context

---

## 4) Workstreams

### 4.1 Host Convergence

Deliverables:

- one Shopify storefront host path for Max Mode
- no long-term `legacy` shopper shell dependency
- app embed and app blocks boot through the same product contract
- shell loads only what each surface needs
- graceful disabled/unauthorized states
- no duplicate shell instances on one page

Implementation notes:

- keep the full shell for depth
- keep lightweight block rendering for inline surfaces
- make the shell bootstrap contract explicit before deleting legacy paths
- treat performance, mobile layout, and theme editor compatibility as part of the host work

Acceptance checks:

- product page with multiple Companion blocks renders once, without duplicate boot
- opening depth from any Companion surface uses the same Max Mode path
- mobile and desktop placements are stable
- theme editor preview does not break the surface contract

### 4.2 Shell Conversation-Mode Contract

Deliverables:

- `defaultConversationMode`
- `effectiveConversationMode`
- `allowedConversationModes`
- entitlement-aware mode filtering
- explicit shopper enablement for deeper modes
- page-aware mode routing controlled by merchant/admin settings

Implementation notes:

- `defaultConversationMode` is the configured starting mode
- `effectiveConversationMode` is the runtime-selected mode after context, entitlement, and safety checks
- `allowedConversationModes` is the bounded set the shopper can intentionally select
- page-aware routing can suggest defaults such as product pages, search pages, and account/support pages
- mode strings must be platform-backed values, not theme-only arbitrary labels

Acceptance checks:

- bootstrap, bridge request context, runtime request context, and Max widget state agree
- a Starter store cannot expose governed-action-only behavior
- advanced modes are visible only when verified and entitled
- page-aware routing can be tested without editing theme code

### 4.3 Embedded Surface Architecture

Canonical surfaces:

- AI search
- product insight block
- product FAQ
- comparison
- policy strip
- contextual pill
- Companion chat/depth layer

Deliverables:

- shared surface bootstrap
- shared loading, empty, error, and unauthorized states
- shared source/grounding cues
- shared handoff to Max Mode
- surface entitlement gates matching Launch Truth
- merchant-placeable app blocks where relevant

Surface rules:

- AI search is the only Free surface.
- Starter gets the full read-only surface set.
- Elite may add governed actions only where verified.
- Every surface must remain useful without requiring a shopper to type first.

Acceptance checks:

- every Starter surface can render in a real Shopify theme
- disabled surfaces explain plan or setup state in merchant-safe language
- no Free store can access Starter-only surfaces through direct storefront paths
- surface copy does not lead with chatbot language

### 4.4 Fetch-Only Intelligence Conversion

Deliverables:

- fetch-only product evidence tools
- fetch-only policy evidence tools
- fetch-only collection/search evidence tools
- comparison generated through the shared read-first runtime path
- similar-product guidance generated through the shared read-first runtime path
- policy snippets generated from retrieved policy evidence, not keyword rules

Retire:

- rule-based `compare_products`
- rule-based `find_similar_products`
- keyword-only policy matching
- dedicated storefront read-action paths that bypass the common read-first model

Acceptance checks:

- comparison results cite product evidence instead of hard-coded similarity rules
- policy strip updates when policy content changes and sync completes
- similar-product guidance can explain why results were chosen
- missing evidence produces honest fallback copy

### 4.5 Page Context And Attachments

Deliverables:

- explicit page context payload contract
- explicit attached-target payload contract
- Companion-owned card attach controls
- Max Mode attachment reuse from surface cards
- optional bounded path for theme-native card instrumentation

Definitions:

- page context: current product, collection, cart, search, policy page, article, or store context
- attached target: shopper-selected product, article, policy, comparison set, or result item

Acceptance checks:

- opening Max Mode from a product page includes product page context
- attaching a product from a Companion card adds a visible selected target
- changing pages changes page context without preserving stale context
- removing an attachment does not remove page context

### 4.6 Read-First Merchandising Polish

Deliverables:

- richer comparison rendering
- richer similar-product presentation
- size/fit guidance where source data exists
- review, policy, buying-guide, and structured-content grounding cues
- useful follow-up prompts tied to current surface context
- compact rendering that works inside product pages without feeling like a dashboard

Acceptance checks:

- comparison shows differences, strengths, concerns, and shopper-fit guidance
- product insight explains the evidence behind its summary
- size guidance is omitted when data is weak rather than invented
- follow-up prompts open the same Max Mode depth path

### 4.7 Merchant Controls

Deliverables:

- surface placement guidance
- surface availability by plan
- Knowledge Sync status in merchant-safe language
- page-aware mode defaults
- setup blockers and next actions
- plan upgrade affordances where a surface is unavailable

Acceptance checks:

- merchant can tell which surfaces are active
- merchant can see why a surface is blocked
- merchant can place and test surfaces without reading operator docs
- merchant UI does not expose raw vectorization or deployment internals

### 4.8 Verification

Required checks:

- Free, Starter, and Elite entitlement paths
- Shopify theme editor preview
- product page, collection/search page, cart page, and policy/page contexts where available
- desktop and mobile storefront layouts
- multiple Companion blocks on one page
- shell open/close and attachment state
- no debug/operator language leakage
- no direct URL bypass for gated surfaces
- reasonable loading and failure states

---

## 5) Build Order

### Step 1: Freeze Contracts

Close:

- shell bootstrap contract
- conversation-mode contract
- page context contract
- attachment contract
- surface entitlement matrix

Exit:

- implementation teams and future Codex turns have one contract to follow

### Step 2: Converge Host

Close:

- Max Mode as one long-term shell path
- legacy shell removal plan
- duplicate-host prevention
- app embed and block boot agreement

Exit:

- every surface can hand off to Max Mode consistently

### Step 3: Convert Read-First Intelligence

Close:

- fetch-only tool path
- comparison and similar-product conversion
- policy strip retrieval conversion
- honest fallback behavior

Exit:

- no active shopper-facing surface depends on brittle keyword intelligence

### Step 4: Add Context And Attach Depth

Close:

- page context propagation
- attached-target controls
- Max attachment reuse
- page-aware mode routing

Exit:

- a shopper can move from inline intelligence to focused depth without losing context

### Step 5: Polish Starter-Grade Surfaces

Close:

- comparison composition
- product insight grounding
- product FAQ rendering
- size/fit guidance
- surface-level empty/error states

Exit:

- surfaces look and behave like product features, not technical demos

---

## 6) Exit Gate

Phase 1 is complete when:

- Max Mode is the only long-term shopper shell
- embedded intelligence surfaces are visibly real and merchant-placeable
- chat is clearly a depth layer
- page context and attached targets are distinct and working
- comparison, similar-product, and policy outputs use the shared read-first model
- Free exposes AI search only
- Starter-only surfaces are gated correctly
- shopper surfaces contain no operator/debug language
- the storefront product can be demoed without explaining internal architecture

---

## 7) Risks

| Risk | Why It Matters | Mitigation |
|---|---|---|
| Max Mode becomes a cleanup project | It can consume time without improving the product story | Tie every shell task to embedded surface delivery |
| Dual shells remain | Creates product, QA, and support confusion | Set a removal gate for legacy chat UI |
| Mode semantics drift | Advanced modes can create unsafe or confusing behavior | Keep `default/effective/allowed` contract platform-backed |
| Surface-specific logic grows again | Recreates brittle Shopify-only intelligence | Keep bridge tools fetch-only |
| Free scope leaks | Weakens paid conversion and contradicts Launch Truth | Gate every surface through plan-aware entitlements |
| Too much operator UI leaks into merchant/shopper surfaces | Makes the product feel unfinished | Use merchant-safe and shopper-safe language rules |

---

## 8) Handoff To Phase 2

Phase 2 can start when Phase 1 produces:

- a stable Max Mode shell path
- gated embedded surfaces
- read-first comparison and policy behavior
- context/attachment handoff
- merchant-safe setup and verification signals

Phase 2 should then package those surfaces into a sellable Starter launch.
