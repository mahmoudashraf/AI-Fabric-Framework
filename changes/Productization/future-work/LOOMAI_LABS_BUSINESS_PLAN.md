# LoomAI Labs Business Plan

Status: business plan (2026-05-06)

---

## 1) Company Identity

**Name:** LoomAI Labs Ltd

**Registered:** United Kingdom

**Domain:** loomai.pro

**Founder:** Solo technical founder, Egyptian-British, based in UK

**One sentence:** We are your AI lab for building smarter apps.

---

## 2) Origin Story

AI Fabric Framework started five months ago as a Java framework. The idea was simple: a Java developer adds annotations and configuration, and their app gets AI capabilities — semantic search, embeddings, RAG, behavioral analytics.

Five months and 195,000+ lines later, the framework evolved into a full deployment platform. The annotations became deployment configuration. The single-app model became multi-tenant bridge services. The framework became a factory.

The factory now produces AI products through configuration, not code. Each product is a bridge service that connects the platform's runtime (RAG pipeline, action framework, confirmation governance, multi-provider LLM routing) to a specific business domain.

The first product is Loom Companion for Shopify. The platform can produce hundreds more.

---

## 3) What The Platform Does

The AI Fabric platform provides:

- **Multi-provider LLM runtime** — Azure, OpenAI, Anthropic, Cohere, Gemini, Ollama, ONNX. Switch providers by configuration.
- **RAG pipeline** — live data indexing, vector search, multi-source retrieval with attribution.
- **Action framework** — read and write actions with typed schemas, routed through bridge services.
- **Confirmation governance** — write actions require user confirmation. Counter-offer interception proposes alternatives before execution.
- **Multi-tenant deployment** — each business gets an isolated deployment with its own configuration, knowledge, and actions.
- **MCP integration** — discover and consume external MCP servers as governed action sources. Expose platform actions as MCP tools.
- **Marketplace** — plugin discovery, manifest compilation, deployment drafts.
- **Managed provisioning** — Coolify-based deployment to self-hosted infrastructure on Hetzner.
- **Embedded UI** — theme extensions, widgets, and embedded intelligence surfaces for storefronts and apps.

A developer does not build AI infrastructure. They describe what they want through configuration, and the platform produces it.

---

## 4) The Two-Engine Model

The business runs on two parallel engines that feed each other.

### 4.1 Engine 1: Money — Shopify Products

Revenue-generating products sold to merchants through app stores and partner channels.

**What it produces:**

- Loom Companion (shopping companion with embedded intelligence surfaces)
- Loom Resolver (support agent with write actions and confirmation governance)
- Future commerce products as the platform expands

**Who pays:**

- Shopify merchants pay product subscriptions

**Distribution:**

- Shopify App Store (self-serve)
- Partner portal (agencies and integrators deploy to their clients)
- Direct outreach to store owners
- "Powered by" badge virality on free tier stores

**Revenue timeline:** now. First dollar within weeks of App Store approval.

### 4.2 Engine 2: Attraction — Developer Platform

Developer-facing platform that attracts technical talent, builds the ecosystem, and creates future revenue.

**What it provides:**

- Free development environment for building AI agents
- MCP integration (connect any MCP server, platform adds governance)
- Marketplace for discovering and deploying AI products
- Operator portal for managing deployments
- Specialized agents with router agent architecture
- Live data syncing and knowledge grounding

**Who joins:**

- Developers looking for AI integration experience
- Laid-off developers seeking new income channels
- CS graduates (Egypt and globally) entering the AI market
- Freelancers and agencies wanting to offer AI products

**Distribution:**

- Educational alumni program (internship cohorts)
- Landing page of available products
- Developer documentation and tutorials
- MCP ecosystem visibility

**Revenue timeline:** 12-18 months. Revenue share on developer-built products reaching production.

### 4.3 How the engines feed each other

```
Money Engine (Shopify)                Attraction Engine (Developers)
─────────────────────                 ──────────────────────────────
Revenue funds platform dev      →     Platform gets better
Products prove the factory      →     Developers see it works
Merchants need more products    →     Developers build them
Partner network grows           →     Developers become partners
                                →     Developers deploy to more merchants
                                →     More merchants = more revenue
```

The money engine funds the attraction engine. The attraction engine scales the money engine.

---

## 5) Products

### 5.1 Loom Companion (Ship First)

AI shopping intelligence for Shopify stores. Embedded intelligence surfaces on product pages, search, and storefront.

Six intelligence surfaces:

- Product insights block
- AI-powered search
- Product FAQ
- Product comparison
- Policy strip
- Contextual pill

Plus conversational companion chat as secondary surface.

Status: bridge service built (20,000+ lines in Platform V5/V6), ready for App Store submission after merge and deployment.

### 5.2 Loom Resolver (Ship Second)

AI support agent with write actions and confirmation governance.

Capabilities:

- Order status lookup and tracking
- Return and exchange initiation with confirmation
- Cart assistance
- Support ticket creation and escalation
- Counter-offer governance (retention offers before cancellation)

Status: action framework and confirmation governance built in platform. Resolver-specific Shopify actions need bridge service configuration.

### 5.3 Future Products (Developer-Built)

As the attraction engine grows, developers build products for new verticals:

- WooCommerce companion (same product, new bridge service)
- BigCommerce companion
- Document intelligence (RAG over documentation)
- Universal FAQ agent (any website)
- WhatsApp commerce agent

The platform produces them. Developers configure and deploy them. The marketplace lists them.

---

## 6) Pricing Strategy

### 6.1 Merchant Pricing (Money Engine)

**The core principle: Pay After Value.**

Merchants do not pay before they see results. The product proves itself first.

| | Free | Starter | Elite |
|---|---|---|---|
| **Price** | $0/month | $29/month | $179/month |
| **First 6 months** | Free forever | Free | Free |
| **After 6 months** | Free forever | $29/month | $179/month |

**Free tier:**

- AI search surface only
- 50 products maximum
- Daily knowledge sync
- "Powered by Loom Companion" badge (mandatory)
- Docs support only

**Starter tier ($29/month after 6-month free period):**

- All read-only intelligence surfaces
- Unlimited products
- Knowledge sync every 2 hours
- Products, pages, policies, collections indexed
- Basic analytics
- Optional badge removal
- Custom accent color
- Email support (48-hour)

**Elite tier ($179/month after 6-month free period):**

- Everything in Starter
- Write actions with confirmation governance
- Order status, returns, cart assistance, ticket creation
- Counter-offer interception
- Deep Resolver for complex multi-step flows
- Abandoned cart recovery
- Discount and coupon application
- Real-time knowledge sync
- Advanced analytics with export
- Full appearance customization
- No badge
- Custom knowledge sources
- Priority support (24-hour)

### 6.2 Why Pay After Value Works

Traditional SaaS: merchant pays $179/month, hopes it works, cancels if it does not.

LoomAI model: merchant pays $0 for 6 months, sees the data, sees the conversations, sees the value, then pays.

**What happens after 6 months:**

- Merchant has 6 months of conversation data proving customer engagement
- Merchant has dependency (customers expect the AI to be there)
- Merchant has analytics showing support deflection or product discovery
- Switching cost is high (lose all trained knowledge, lose customer habit)
- Churn after free period: estimated 15-20% (vs. 30-40% for traditional trials)

**What it costs to offer:**

```
LLM cost per merchant: ~$5-15/month depending on tier
50 free merchants × $10 average × 6 months = $3,000 total
100 free merchants × $10 average × 6 months = $6,000 total
```

Survivable on modest savings. The cost is infrastructure, not staff.

### 6.3 Premium Onboarding and Support

For Elite tier merchants and partner deployments:

- Free onboarding (guided setup, no charge)
- Free server hosting for 6 months (platform covers infrastructure)
- Free production licence during free period
- Premium support included in Elite price after free period

### 6.4 Developer Pricing (Attraction Engine)

**Development:** free forever. No cost to build, test, and iterate.

```
Dev environment:     Free (unlimited)
Dev deployments:     Free (test stores, sandboxes)
Documentation:       Free
MCP integration:     Free
Community support:   Free
```

**Production:** revenue share model.

```
Production deployment:  25% revenue share to platform
Developer keeps:        75% of subscription revenue
Minimum fee:            $0 (if developer earns nothing, they pay nothing)
Production licence:     Included (no separate fee)
```

**Support subscription (optional):**

```
Community support:      Free
Standard support:       $49/month (email, 48-hour response)
Premium support:        $149/month (priority, 12-hour response, Slack)
```

Support subscription is optional. Most developers start with community support and upgrade when they have paying customers.

### 6.5 Partner Commission

Partners (agencies, integrators, deployers) earn commission on merchant subscriptions:

```
Starter referral:   $5.80/month (20% of $29)
Elite referral:     $35.80/month (20% of $179)
Commission period:  As long as merchant remains active
Attribution window: 90 days from first click
```

Partner tiers:

```
Starter Partner (0-9 merchants):    20% commission
Growth Partner (10-24 merchants):   20% commission + priority support + early access
Premium Partner (25+ merchants):    25% commission + dedicated support + white-label option
```

---

## 7) Distribution Channels

### 7.1 Shopify App Store

Primary channel for merchant acquisition.

- Self-serve install
- Free tier drives volume and reviews
- "Powered by" badge on free stores drives organic discovery
- App Store SEO for "AI shopping assistant," "product search," "shopping companion"

### 7.2 Partner Network

Agencies and integrators deploy Loom Companion to their merchant clients.

- 20-50 partners within 12 months
- Each partner deploys to 5-15 merchants
- Partners earn recurring commission
- Partners handle merchant onboarding and first-line support

### 7.3 Direct Outreach

Email and community outreach to store owners.

- Shopify Community forums
- Reddit (r/shopify, r/ecommerce)
- LinkedIn (Shopify agencies, e-commerce consultants)
- Cold email to target verticals (fashion, electronics, health/beauty)

### 7.4 Educational Program

Internship alumni become deployers and partners.

- Each cohort of 30 developers learns the platform
- 5-8 best performers become active deployers
- Each deploys to 3-5 merchants during the program
- Alumni network grows the ecosystem organically

### 7.5 MCP Ecosystem

As MCP adoption grows, the platform becomes discoverable through MCP directories and developer communities.

- Platform-as-MCP-server means any MCP client can connect
- MCP developers discover the platform through protocol compatibility
- The governance layer differentiates from raw MCP servers

---

## 8) Developer Attraction Strategy

### 8.1 The Value Proposition

"AI is approaching your normal work zone. Companies are laying off developers who cannot work with AI. We give you the tools and experience to build AI products — for free."

### 8.2 What Developers Get

- Free development environment with production-grade AI infrastructure
- Real projects deploying real AI products to real businesses
- MCP integration experience (the emerging standard)
- Understanding of RAG, actions, governance, multi-provider LLM routing
- Portfolio of deployed AI products
- Income path through partner commission or production revenue share
- Reference from a UK AI company

### 8.3 Educational Alumni Program

**Name:** LoomAI Labs AI Integration Program

**Location:** Remote, targeting Egyptian CS graduates initially

**Cohort size:** 30 developers per cohort

**Duration:** 3 months per cohort

**Cost to developer:** Free

**Structure:**

Month 1 — Learn the platform:
- Deploy Loom Companion to a test Shopify store
- Understand RAG pipeline, action framework, knowledge sync
- Configure intelligence surfaces
- Build a simple MCP server

Month 2 — Deploy to real stores:
- Each developer deploys to 2-3 real Shopify merchants (free tier)
- Handle configuration, troubleshooting, merchant communication
- Document what they learn
- Contribute to platform documentation

Month 3 — Independent projects:
- Build a new vertical product or MCP integration
- Or focus on merchant deployment and partner track
- Final project presentation

**Graduation outcomes:**

- Top 5-8: offered paid roles (Egypt, $500-700/month) or premium partner status
- Next 10-15: become active deployment partners with commission
- Remaining: community members who know the platform and spread the word

**Cohort frequency:** every 3-4 months

**12-month projection:** 3-4 cohorts, 90-120 developers exposed, 15-25 active deployers

### 8.4 Developer Landing Page

Products page on loomai.pro showing:

- All available products developers can deploy
- Revenue potential per product per merchant
- "Start building" CTA leading to free dev environment
- Success stories from alumni program
- MCP integration quickstart

---

## 9) Egypt Operations

### 9.1 Why Egypt

- Founder is Egyptian with existing network
- CS graduate talent pool is large and affordable
- Salaries are 5-10x lower than UK/US
- 2-hour timezone difference from UK (minimal friction)
- Arabic + English bilingual workforce

### 9.2 Team Structure

```
Founder (UK)
├── 90% building platform and products
├── 10% technical decisions and partner calls
│
├── Egypt Team (2-3 people, Month 3+)
│   ├── Partner Success Associate ($400-600/month)
│   │   └── Partner communication, onboarding, commission tracking
│   ├── Deployment Support Specialist ($500-700/month)
│   │   └── Merchant deployments, configuration, troubleshooting
│   └── Outreach and Content ($300-500/month)
│       └── Partner recruitment, social media, documentation
│
├── Alumni Network (30+ developers per cohort)
│   ├── Active deployers (5-8 per cohort)
│   └── Community members
│
└── Partner Network (20-50 partners)
    └── Agencies and integrators deploying to merchants
```

### 9.3 Hiring Timeline

Do not hire before there is pain:

```
Ship Companion           → founder handles everything
5-10 installs            → still manageable
First 3 partners sign    → support starts eating build time
20+ installs             → hire first person (partner success + deployment combined)
50+ installs             → hire second person (split roles)
3+ cohorts completed     → hire third person (outreach + content)
```

---

## 10) Technology Stack

### 10.1 Platform

- **Runtime:** Java, Spring Boot
- **Database:** PostgreSQL with Flyway migrations
- **Vector databases:** Qdrant, Pinecone, Weaviate, Milvus, Lucene
- **LLM providers:** Azure OpenAI, OpenAI, Anthropic, Cohere, Gemini, Ollama, ONNX
- **Deployment:** Coolify on Hetzner (self-hosted, cost-efficient)
- **CI/CD:** GitHub Actions

### 10.2 Products

- **Shopify Bridge Service:** Java, Spring Boot, Shopify API (OAuth, webhooks, billing)
- **Admin UI:** React, TypeScript, MUI (migrating to Tailwind + dark theme)
- **Storefront Widget:** React, Tailwind, Framer Motion, embeddable via script tag
- **Theme Extension:** Liquid templates for Shopify theme editor blocks

### 10.3 Infrastructure Costs

```
Hetzner VPS (platform + DB):     ~$40-60/month
Qdrant Cloud (vector DB):        ~$25-50/month (scales with tenants)
LLM API costs:                   ~$5-15/month per active merchant
Domain and DNS:                  ~$15/year
Total fixed:                     ~$80-120/month
Per-merchant variable:           ~$5-15/month
```

At 100 merchants: ~$80 fixed + ~$1,000 variable = ~$1,080/month infrastructure cost.

---

## 11) Revenue Projections

### 11.1 Money Engine (Shopify Products)

**Month 1-6 (Free Period for Early Merchants)**

```
Installs: 50-100 (free tier + Starter/Elite on free period)
Revenue: $0 (Pay After Value model)
Cost: ~$3,000-6,000 total infrastructure for free merchants
Focus: proving value, collecting data, building reviews
```

**Month 7-12 (Free Period Ends, Merchants Convert)**

```
Retained from free period:  60-80 merchants (60-80% retention)
New installs:               50-100 additional
Paid breakdown:
  Free tier:                100 stores × $0    = $0
  Starter:                  80 stores × $29    = $2,320/month
  Elite:                    25 stores × $179   = $4,475/month
  
Total MRR:                  $6,795
Partner commission (20%):   -$1,359
Net MRR:                    $5,436
```

**Month 12-18 (Scaling)**

```
Total installs:             400-600
Paid breakdown:
  Free tier:                200 stores × $0    = $0
  Starter:                  200 stores × $29   = $5,800/month
  Elite:                    60 stores × $179   = $10,740/month

Total MRR:                  $16,540
Partner commission (20%):   -$3,308
Egypt team:                 -$1,500/month
Infrastructure:             -$2,500/month
Net MRR:                    $9,232
ARR:                        ~$110,784
```

### 11.2 Attraction Engine (Developer Platform)

Revenue begins when developers ship production products:

**Month 12-18:**

```
Active developers:          10-15 (from alumni program)
Developers with prod apps:  3-5
Average app revenue:        $500/month
Platform rev share (25%):   $125/month per app

Total platform revenue:     $375-625/month
```

**Month 18-24:**

```
Active developers:          30-50
Developers with prod apps:  10-15
Average app revenue:        $800/month
Platform rev share (25%):   $200/month per app

Total platform revenue:     $2,000-3,000/month
```

### 11.3 Combined Revenue Projection

```
Month 6:    ~$0 MRR (free period)
Month 9:    ~$3,500 MRR (early conversions)
Month 12:   ~$7,000 MRR (money engine)
Month 15:   ~$12,000 MRR (money + early attraction)
Month 18:   ~$19,000 MRR (both engines running)
Month 24:   ~$30,000+ MRR (compound growth)

24-month ARR target: ~$360,000
```

---

## 12) Competitive Positioning

### 12.1 Against Shopify AI competitors

| | Loom Companion | Rep AI | Manifest AI | Tidio |
|---|---|---|---|---|
| RAG quality | Multi-source + attribution | Basic search | GPT wrapper | Template |
| Write governance | Confirmation + counter-offer | Direct execution | Direct execution | N/A |
| Pricing model | Pay After Value | Pay upfront | Pay upfront | Pay upfront |
| Intelligence surfaces | 6 embedded blocks | Chat only | Chat only | Chat only |
| Entry price | Free (6 months) | $29/month | $99/month | $29/month |

### 12.2 Against developer platforms

| | LoomAI Platform | Botpress | Voiceflow | Chatbase |
|---|---|---|---|---|
| Governance | Built-in confirmation + counter-offer | None | None | None |
| MCP support | Native (consume + expose) | None | None | None |
| RAG | Production-grade, multi-source | Basic | Basic | Upload-only |
| Deployment | Managed, multi-tenant | Self-managed | Cloud only | Cloud only |
| Revenue model | 25% rev share | Seat-based | Seat-based | Usage-based |
| Developer cost | Free to start | $0-$150/month | $0-$600/month | $0-$500/month |

### 12.3 Moat

What competitors cannot easily replicate:

1. **Confirmation governance** — no competitor has write action interception with counter-offers
2. **Pay After Value** — no competitor offers 6 months free with no commitment
3. **MCP + governance** — nobody else wraps MCP tools with enterprise governance
4. **Platform-as-factory** — competitors build one product; the platform produces many
5. **Developer network + alumni program** — built-in talent pipeline from Egypt

---

## 13) Risk Assessment

### 13.1 Shopify builds native AI companion

**Probability:** medium-high
**Impact:** high
**Mitigation:** Loom Companion's governance, intelligence surfaces, and multi-provider flexibility go beyond what Shopify would build natively. Shopify's AI will be basic. Deep vertical intelligence is where third-party apps survive.

### 13.2 No merchants convert after free period

**Probability:** low (if the product works)
**Impact:** high
**Mitigation:** 6 months of data proves or disproves value. If merchants do not convert, the product needs improvement, not marketing. Kill the free period and pivot to paid-from-day-one if conversion is below 40%.

### 13.3 Developer platform does not attract developers

**Probability:** medium
**Impact:** low (money engine runs independently)
**Mitigation:** the alumni program in Egypt is a controlled recruitment channel. 30 developers per cohort is achievable through university and community outreach. If organic developer attraction fails, the alumni program still produces deployers.

### 13.4 Founder burns out

**Probability:** medium
**Impact:** critical
**Mitigation:** the entire model is designed to protect build time. Partners handle merchant relationships. Egypt team handles support. Alumni handle deployment. The founder builds. If any of these layers fail and the founder absorbs the work, the model breaks.

### 13.5 Infrastructure costs exceed revenue during free period

**Probability:** low
**Impact:** medium
**Mitigation:** LLM costs are $5-15 per merchant per month. 100 free merchants for 6 months costs $3,000-9,000 total. This is manageable with modest savings. Cap free tier installs if costs exceed budget.

---

## 14) Milestones

### 14.1 Immediate (Week 1-4)

- Merge Platform V8 to main
- Deploy Loom Companion to production
- Submit to Shopify App Store
- Set up loomai.pro homepage
- Set up api.loomai.pro, app.loomai.pro, cdn.loomai.pro

### 14.2 Month 1-2

- Shopify App Store approval
- First 10-20 installs (free + Starter on free period)
- Set up docs.loomai.pro
- Set up demo.loomai.pro (live demo store)
- Begin partner outreach (target 3-5 founding partners)

### 14.3 Month 2-3

- 30-50 installs
- First alumni cohort begins (30 developers in Egypt)
- Partner portal MVP (partners.loomai.pro)
- Status page (status.loomai.pro)
- First App Store reviews (target 4.5+ stars)

### 14.4 Month 3-6

- 50-100 installs
- Alumni cohort 1 graduates
- 5-8 active deployers from cohort
- 10-15 partners signed
- MCP server adapter shipped (platform actions exposed as MCP tools)
- Loom Resolver (write actions) shipped as Elite tier feature
- Blog launched with first content

### 14.5 Month 6-9

- Free period ends for first merchants, conversions begin
- Revenue: target $3,000-5,000 MRR
- Alumni cohort 2 begins
- First Egypt team hire
- WooCommerce companion development begins
- Developer landing page and marketplace MVP

### 14.6 Month 9-12

- 200+ installs, 100+ paid merchants
- Revenue: target $7,000-10,000 MRR
- 20-30 active partners
- 2 alumni cohorts graduated (60 developers exposed)
- MCP client adapter shipped (consume external MCP servers)
- First developer-built product reaches production

### 14.7 Month 12-18

- 400+ installs, 250+ paid merchants
- Revenue: target $15,000-20,000 MRR
- Developer platform generating first revenue (rev share)
- 3-4 alumni cohorts (90-120 developers)
- 3-5 products in marketplace
- Innovator Founder visa application ready

---

## 15) Innovator Founder Visa Path

The UK Innovator Founder visa requires:

- Innovation: building something new that does not exist in the market
- Viability: evidence the business can sustain itself
- Scalability: evidence the business can grow significantly

**What LoomAI Labs demonstrates:**

- **Innovation:** confirmation governance for AI actions, embedded intelligence surfaces, MCP-first deployment platform — none of these exist in combination in any competitor
- **Viability:** paying merchants on Shopify App Store, partner network generating revenue, sustainable unit economics ($24+ margin per Starter, $164+ margin per Elite)
- **Scalability:** platform-as-factory model produces new products via configuration; developer network and alumni program scale deployment without scaling the team linearly

**Recommended application timing:** Month 9-12, when the business has:

- 100+ installs
- $5,000+ MRR
- Active partner network
- Graduated alumni cohort
- Multiple products deployed

---

## 16) Company Positioning

### 16.1 For merchants

"We are your AI partner for intelligent Shopify pieces. Make every product page smarter. Pay only after you see value."

### 16.2 For developers

"We are your AI lab for building smarter apps. Free development environment. Build real AI products. Earn revenue when you ship."

### 16.3 For partners

"Deploy AI products to your clients. We build the products. You deploy them. Earn 20% recurring commission on every merchant."

### 16.4 For investors / visa panel

"LoomAI Labs is an AI product studio. We built a platform that produces governed AI products through configuration, not code. Our first product serves Shopify merchants. Our developer network deploys products to businesses across verticals. We are building the deployment and governance layer for AI products."

---

## 17) Summary

LoomAI Labs runs two engines:

**Money engine:** Loom Companion on Shopify. Premium pricing with Pay After Value (6 months free). Distributed through App Store, partners, and direct outreach. Revenue from merchant subscriptions.

**Attraction engine:** developer platform with free dev environment, MCP integration, and marketplace. Distributed through alumni program (30 devs per cohort in Egypt), developer marketing, and MCP ecosystem. Revenue from production revenue share (25%).

The money engine funds the business today. The attraction engine scales it tomorrow. Both run on the same platform — the AI Fabric Framework that one engineer built in five months.

The machine is built. Now it needs its first customers.
