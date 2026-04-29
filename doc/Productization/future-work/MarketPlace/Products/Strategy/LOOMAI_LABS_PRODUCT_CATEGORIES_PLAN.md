# LoomAI Labs: Product Categories and Expansion Plan

Status: strategy document (2026-04-20)

---

## 1) What This Document Is

This document maps every product category that the AI Fabric platform can produce, given the current architecture. Each category is a separate product line that LoomAI Labs can ship — using the same platform, same RAG pipeline, same action framework, same governance layer.

The purpose is to show that Loom Companion for Shopify is product #1 from a factory that can produce many. This document defines what "many" looks like.

---

## 2) The Factory Model

The platform produces products. Every product follows the same pattern:

```
Platform (shared)         Product (unique per vertical)
─────────────────         ──────────────────────────────
RAG pipeline        →     Domain-specific knowledge sources
Action framework    →     Domain-specific actions
Governance layer    →     Domain-specific policies
LLM routing         →     Domain-specific prompts
Widget shell        →     Domain-specific components
Data sync pipeline  →     Domain-specific webhook adapters
```

A new product requires:
- A new bridge service (`product-services/<name>-bridge-service/`)
- Domain-specific action configurations
- Domain-specific entity schemas for vector indexing
- Domain-specific prompt tuning
- A distribution channel integration (app store, plugin directory, direct embed)

The platform, runtime, vectorization, and shell are shared across all products.

---

## 3) Product Category Map

### Category 1: Commerce Companions

**What it is:** AI shopping intelligence embedded in online stores. The flagship category.

**Products:**

| Product | Platform | Distribution | Addressable stores | Effort |
|---|---|---|---|---|
| Loom Companion for Shopify | Shopify | Shopify App Store | 4,600,000 | Built (PR #153) |
| Loom Companion for WooCommerce | WordPress | WordPress.org Plugin Directory | 6,000,000+ | 3-5 weeks |
| Loom Companion for BigCommerce | BigCommerce | BigCommerce App Marketplace | 60,000 | 3-4 weeks |
| Loom Companion for Wix | Wix | Wix App Market | 900,000 | 4-5 weeks |
| Loom Companion for Squarespace | Squarespace | Squarespace Extensions | 400,000 | 4-5 weeks |
| Loom Companion for PrestaShop | PrestaShop | PrestaShop Addons | 300,000 | 3-4 weeks |

**Total addressable:** ~12,000,000 online stores.

**Revenue model:** Free tier + $29/mo Starter + $179/mo Elite per store, using the current Shopify Companion launch truth.

**Why this category first:** Validated by Amazon Rufus. No competitor does proper RAG. Distribution channels (app stores) exist and are free. Revenue per unit is predictable.

**What's shared across all products in this category:** The same embedded intelligence surfaces (product insights, AI search, comparison, FAQ, policy strips). The same capability tiers (companion → support → sales). Each platform needs only a new bridge service for its specific OAuth, webhook, and API patterns.

---

### Category 2: Documentation Intelligence

**What it is:** AI-powered documentation assistants that SaaS companies embed on their docs sites. Answers technical questions with source-cited evidence from the company's documentation, API reference, changelogs, and community forums.

**Products:**

| Product | Integration | Distribution | Effort |
|---|---|---|---|
| Loom Docs (standalone) | Script tag embed on any docs site | Direct sales + content marketing | 4-6 weeks |
| Loom Docs for GitBook | GitBook | GitBook Integration Directory | 5-6 weeks |
| Loom Docs for ReadMe | ReadMe | ReadMe Marketplace | 5-6 weeks |
| Loom Docs for Notion | Notion | Notion Integrations | 6-7 weeks |
| Loom Docs for Confluence | Confluence | Atlassian Marketplace | 6-8 weeks |

**Revenue model:** $49-$199/month per site. SaaS companies pay more than merchants because documentation quality directly impacts support costs and developer adoption.

**Why this category fits the platform:**

- Multi-source RAG is the core value — docs live across multiple systems (guides, API reference, changelogs, community)
- Attribution is critical — technical answers must cite the specific doc page, not hallucinate
- Live sync matters — docs update frequently, the AI must stay current
- Embedded intelligence surfaces apply directly — inline answer blocks on doc pages, smart search, auto-generated FAQ per topic

**What the bridge service handles:**

- Authentication with the docs platform API (GitBook, ReadMe, Notion, Confluence)
- Webhook sync: doc pages created/updated/deleted → re-vectorize
- Page context detection: which doc page the user is on → scope answers
- Billing: per-site subscription via Stripe (not platform-native billing like Shopify)

**The embedded intelligence parallel:**

```
E-COMMERCE                    DOCUMENTATION
────────────                  ─────────────
Product insight block    →    Doc topic insight block
AI product search        →    AI docs search
Product FAQ              →    Topic FAQ
Product comparison       →    Feature/API comparison table
Policy strip             →    Version/deprecation notice strip
Companion chat           →    Docs assistant chat
```

Same shell component model. Different domain data.

---

### Category 3: Compliance and Regulatory Intelligence

**What it is:** AI that answers regulatory and policy questions with mandatory source citation. For industries where "I think the answer is..." is unacceptable — the AI must cite the specific regulation, clause, or policy.

**Products:**

| Product | Vertical | Distribution | Effort |
|---|---|---|---|
| Loom Comply (standalone) | Any regulated industry | Direct enterprise sales | 6-8 weeks |
| Loom Comply for Finance | Financial services | Direct + FCA/regulatory partnerships | 7-9 weeks |
| Loom Comply for Healthcare | Healthcare/pharma | Direct + NHS/regulatory partnerships | 7-9 weeks |
| Loom Comply for Construction | Construction/safety | Direct + HSE partnerships | 6-8 weeks |

**Revenue model:** $200-$2,000/month per organisation. Compliance budgets are large and non-negotiable.

**Why this category is high-value:**

Attribution is not a nice-to-have in compliance. It is a legal requirement. When a compliance officer asks "can we do X under current regulation?" the answer must cite the specific regulation and clause. If the AI cannot cite its source, the answer is worthless.

This is where the platform's attribution capability becomes a genuine requirement, not just a differentiator. Competitors (generic chatbots, basic RAG wrappers) cannot do proper multi-source attribution.

**What the platform provides:**

- Multi-source RAG across regulation databases, internal policies, case studies, and guidance notes
- Source attribution on every answer — specific document, section, clause
- Live sync when regulations update (webhook from regulation database providers)
- Read-only by design — the AI advises, it does not make compliance decisions
- Audit trail — every question and answer logged for compliance review

**What the bridge service handles:**

- Integration with regulation data sources (APIs, document feeds, RSS)
- Organisation-specific policy document ingestion
- User authentication (SSO, enterprise directory)
- Audit logging and export for compliance records
- Billing: enterprise subscription via Stripe

---

### Category 4: Internal Knowledge Assistants

**What it is:** AI that searches across a company's internal knowledge bases (Notion, Confluence, Google Drive, Slack) and answers employee questions with cited sources.

**Products:**

| Product | Integration | Distribution | Effort |
|---|---|---|---|
| Loom Knowledge for Slack | Slack | Slack App Directory | 6-8 weeks |
| Loom Knowledge for Teams | Microsoft Teams | Teams Marketplace | 7-9 weeks |
| Loom Knowledge for Web | Browser / intranet embed | Direct sales | 5-7 weeks |

**Revenue model:** $5-$15/user/month or $200-$2,000/company/month flat rate.

**Why this category fits:**

Every company with more than 20 employees has the same problem: knowledge is scattered across Notion, Confluence, Google Drive, Slack messages, email threads, and tribal knowledge. Employees waste hours searching or asking colleagues. An AI that searches everything and cites where it found the answer saves measurable time.

**What the platform provides:**

- Multi-source RAG across different knowledge backends (Notion API, Confluence API, Google Drive API, Slack API)
- Attribution: "Found in Q2 OKR document in Notion" or "From #engineering-decisions Slack channel"
- Live sync: webhooks from each source → re-vectorize on change
- Actions: create ticket, update doc, request access (with confirmation governance)
- Context awareness: knows which team/department the employee belongs to, scopes results accordingly

**What the bridge service handles:**

- Multi-platform OAuth (Notion, Confluence, Google, Slack)
- Source-specific webhook handlers and sync jobs
- User directory integration for team-scoped knowledge
- Slack/Teams bot interaction layer (slash commands, thread replies)
- Billing: per-company subscription

---

### Category 5: Conversational Form Replacement

**What it is:** AI that replaces complex multi-field forms with guided conversations. The AI collects the same data through natural dialogue, with higher completion rates.

**Products:**

| Product | Vertical | Distribution | Effort |
|---|---|---|---|
| Loom Forms (standalone) | Any website with complex forms | Direct sales + embed | 6-8 weeks |
| Loom Intake for Healthcare | Patient intake forms | Direct to clinics/hospitals | 7-9 weeks |
| Loom Quote for Insurance | Insurance quote forms | Direct to insurers/brokers | 7-9 weeks |
| Loom Apply for HR | Job application forms | Integration with ATS platforms | 7-9 weeks |

**Revenue model:** $49-$299/month per form or per site, based on submission volume.

**Why this category fits:**

Complex forms have 20-40% abandonment rates. Conversational intake has 60-80% completion rates. The platform's chat orchestration, action framework (submit form data), and governance (confirm collected data before submission) map directly to this use case.

**What the platform provides:**

- Chat orchestration guides the conversation through required fields
- Actions: submit collected data to the backend (with confirmation)
- Governance: show the user a summary of everything collected, confirm before submission
- Knowledge: RAG over FAQ about the form/process ("what does this field mean?")
- Context: the AI adapts follow-up questions based on previous answers

**What the bridge service handles:**

- Form schema definition (what fields to collect, validation rules, conditional logic)
- Backend integration for form submission (webhook, API, email)
- Embedding on the customer's site (script tag or platform-specific integration)
- Analytics: completion rates, drop-off points, time-to-complete
- Billing: per-form or per-site subscription

---

### Category 6: Real Estate and Property Intelligence

**What it is:** AI property finder and advisor embedded on estate agency websites and property portals.

**Products:**

| Product | Market | Distribution | Effort |
|---|---|---|---|
| Loom Property (standalone) | UK estate agencies | Direct sales | 6-8 weeks |
| Loom Property for Rightmove | UK property portal | Partnership / API | 8-10 weeks |
| Loom Property for Zoopla | UK property portal | Partnership / API | 8-10 weeks |

**Revenue model:** £99-£499/month per agency or per branch.

**Why this category fits:**

- Multi-source RAG: property listings + area data + transport links + school ratings + flood risk
- Rich cards: property cards with images, price, bedrooms, location (same component model as product cards)
- Actions: book viewing (with confirmation), request valuation, mortgage calculator
- Attribution: "Based on Land Registry data" or "According to Ofsted ratings"
- Live sync: listings update daily → re-vectorize

**The embedded intelligence parallel:**

```
E-COMMERCE                    PROPERTY
────────────                  ────────
Product insight block    →    Property insight block (area stats, price history)
AI product search        →    AI property search ("3-bed near good schools")
Product comparison       →    Property comparison (side-by-side with area data)
Policy strip             →    Stamp duty / mortgage estimate strip
Companion chat           →    Property advisor chat
```

---

### Category 7: Store Intelligence (Smart Brain — future)

**What it is:** Background batch analysis that runs on schedules without human-initiated conversations. Fundamentally different runtime model.

**Products:**

| Product | What it analyses | Distribution | Effort |
|---|---|---|---|
| Loom Insights for Shopify | Reviews, customer patterns, product performance | Shopify App Store | 8-12 weeks |
| Loom Insights for WooCommerce | Same | WordPress.org | 8-12 weeks |
| Loom Insights (standalone) | Any data source | Direct sales | 10-14 weeks |

**Revenue model:** $49-$199/month per store.

**Why this is a separate category:**

- Different runtime: batch/async, not request-response
- Different buyer: ops/analytics person, not CX/marketing
- Different UX: dashboard and reports, not chat or embedded blocks
- Requires Smart Brain runtime (not yet built)

**Gate:** Only build after Loom Companion proves the Shopify channel works and Smart Brain runtime is implemented.

---

## 4) Priority Sequence

```
PHASE 1 (Now)
└── Loom Companion for Shopify ← SHIPPING

PHASE 2 (Month 3-4)
└── Loom Companion for WooCommerce
    Same product. 6M more stores. Tests factory speed.

PHASE 3 (Month 5-6)
├── Loom Docs (standalone)
│   Different buyer (SaaS), higher price ($49-199), proves platform generality.
└── Loom Companion for BigCommerce/Wix
    Quick wins. Same bridge pattern.

PHASE 4 (Month 7-9)
└── Loom Comply (standalone)
    Highest price point ($200-2000). Compliance attribution is table stakes.
    Enters enterprise market for the first time.

PHASE 5 (Month 10-12)
├── Loom Knowledge for Slack
│   Enterprise internal tool. Per-user pricing. New distribution channel.
└── Loom Insights for Shopify (if Smart Brain runtime built)
    Second Shopify app. Different buyer. Dashboard, not chat.

PHASE 6 (Month 12+)
├── Loom Forms / Loom Intake
├── Loom Property
└── Additional platform companions (Squarespace, PrestaShop, Ecwid)
```

---

## 5) Revenue Projection by Category

Assuming modest adoption in each category after 12-18 months:

| Category | Product | Paying customers | Avg price | Monthly revenue |
|---|---|---|---|---|
| Commerce | Shopify Companion | 200 merchants | $35/mo | $7,000 |
| Commerce | WooCommerce Companion | 150 merchants | $35/mo | $5,250 |
| Commerce | BigCommerce + Wix | 50 merchants | $35/mo | $1,750 |
| Docs | Loom Docs | 30 sites | $99/mo | $2,970 |
| Compliance | Loom Comply | 10 orgs | $500/mo | $5,000 |
| Knowledge | Loom Knowledge | 15 companies | $400/mo | $6,000 |
| Intelligence | Loom Insights | 50 stores | $79/mo | $3,950 |
| **Total** | | | | **$31,920 MRR** |

Conservative estimate. No category requires more than 200 customers. Total across all categories: ~505 paying customers.

At a 10x SaaS valuation multiple on ARR, this represents ~$3.8M company valuation from modest adoption across seven product lines — all running on one platform, operated by one person with a small ops team.

---

## 6) Factory Discipline: Rules for Multi-Product Operation

### Rule 1: One product at a time

Never develop two products simultaneously. Ship one, stabilise it (30+ paying customers, <5% churn), then start the next. Parallel development splits focus and breaks the solo model.

### Rule 2: Three weeks or cut it

If a new product's bridge service takes more than three weeks beyond the estimated effort, something is wrong. Either the platform needs an extension (add it to the platform, benefit all products) or the product scope is too large (cut features). Never spend six weeks on what should take three.

### Rule 3: Platform first, product second

When a product needs a capability the platform does not have, add it to the platform — not to the product. Every platform improvement benefits every future product. Every product-specific hack weakens the factory.

### Rule 4: Each product must reach 30 paying customers before the next starts

This prevents the portfolio from becoming a collection of experiments. 30 paying customers proves the product works and the distribution channel functions. Below 30, the product is unvalidated.

### Rule 5: Shared ops, not per-product ops

One support system, one billing system, one monitoring dashboard across all products. Do not build separate ops infrastructure per product. The ops team (even if it is one contractor) handles all products through unified tooling.

### Rule 6: Kill underperformers

If a product does not reach 10 paying customers within 3 months of launch, either fix the distribution or kill it. Do not carry dead products. The platform is not lost — only the bridge service and distribution effort.

---

## 7) What Makes This Defensible

A competitor who wants to replicate this portfolio needs:

1. A multi-source RAG pipeline with live sync and attribution
2. An action framework with governance and confirmation interception
3. A multi-provider LLM routing layer
4. A widget/shell with embedded intelligence components (not just chat)
5. A marketplace control plane for plugin composition
6. Domain expertise across multiple verticals
7. Bridge services for multiple distribution platforms

Building any one of these takes months. Building all of them takes years. The time advantage is measured in years, not features.

Every product shipped on the platform extends the moat. A competitor must match the platform AND match every product simultaneously. The more products you ship, the harder it becomes to compete with the portfolio.

---

## 8) What This Document Does Not Cover

- Detailed implementation plans for each product (separate documents per product when they enter development)
- Specific bridge service architectures for each platform
- Marketing and content strategies per product category
- Hiring plan for scaling beyond solo operation
- Legal and compliance requirements per vertical (especially healthcare, finance)
- Partnership strategies with platform providers (GitBook, Atlassian, Slack)
- Pricing experiments and optimisation
- International expansion and localisation

---

## 9) Summary

The AI Fabric platform can produce at least seven distinct product categories — commerce companions, documentation intelligence, compliance Q&A, internal knowledge assistants, conversational forms, property intelligence, and store analytics — without new infrastructure. Each category requires only a new bridge service and domain-specific configuration.

The recommended sequence ships one product at a time, starting with the validated Shopify companion, expanding to WooCommerce, then diversifying into higher-priced categories (documentation, compliance) before returning to additional commerce platforms.

At modest adoption (505 total paying customers across all categories), the portfolio generates ~$32K MRR — a sustainable solo business with significant growth potential. Every additional product shipped strengthens the platform moat and compounds the portfolio's defensibility.
