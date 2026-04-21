# LoomAI Pro Subdomain and Web Infrastructure Plan

Status: planning document (2026-04-21)

This document defines the subdomain structure for loomai.pro, what each subdomain serves, who it serves, and what the experience should look like.

---

## 1) Domain Map

```
loomai.pro                         Company homepage
├── app.loomai.pro                 Merchant / operator dashboard
├── api.loomai.pro                 Backend services and runtime
├── docs.loomai.pro                Documentation hub
├── partners.loomai.pro            Partner portal and dashboard
├── demo.loomai.pro                Live demo store (Shopify)
├── status.loomai.pro              Service health and uptime
├── blog.loomai.pro                Content, updates, and stories
└── cdn.loomai.pro                 Widget and asset delivery
```

---

## 2) Subdomain Definitions

### 2.1 loomai.pro — Company Homepage

**Purpose:** first impression, company identity, product catalog entry point

**Who visits:** merchants evaluating the product, partners considering the program, investors, press

**What it shows:**

- hero section with one clear sentence and one CTA
- Loom Companion as the featured first product with a live preview or animation
- how it works (3 steps: install, sync, live)
- product catalog teaser (other products coming from the same platform)
- partner program CTA section
- trust signals (tech stack, uptime, data handling)
- footer with links to all subdomains

**Design direction:**

- single-page or minimal scroll
- dark background with accent highlights
- large typography, generous whitespace
- no stock photos — use product screenshots, subtle geometric patterns, or woven texture motifs
- the page should feel like a product lab, not a corporate site
- mobile-first responsive

**Tech stack:** static site — Next.js (static export) or Framer

**Priority:** must have before Shopify App Store launch

---

### 2.2 app.loomai.pro — Merchant and Operator Dashboard

**Purpose:** the control plane where merchants and operators manage their AI deployments

**Who visits:** merchants who installed the app, operators managing deployments, partners managing client stores

**What it shows:**

- deployment overview (store connection, sync health, environment toggle)
- intelligence surface configuration (which blocks are active, appearance settings)
- knowledge source status (products synced, pages indexed, policies loaded)
- action catalog and confirmation settings
- prompt management
- billing and plan management
- diagnostics and logs

**Design direction:**

- clean, focused workspace — not a dense enterprise admin panel
- left sidebar navigation with grouped sections (not 17 flat items)
- light mode default with optional dark mode
- card-based content areas with clear hierarchy
- status indicators that are glanceable (green/amber/red, not paragraph descriptions)
- progressive disclosure — show summary first, expand for detail
- the dashboard should feel calm and in control, not overwhelming

**Tech stack:** React + design system (see UI redesign document)

**Priority:** must have — this is the existing platform UI, redesigned

**Notes:**

- this replaces the current platform UI at its current URL
- the Shopify admin embedded experience is separate (inside Shopify admin) but shares the same backend
- partners managing multiple stores should see a store switcher or multi-store view

---

### 2.3 api.loomai.pro — Backend Services

**Purpose:** serves all API endpoints, OAuth callbacks, webhook receivers, runtime queries

**Who visits:** no humans — consumed by the dashboard, storefront widgets, Shopify webhooks, partner integrations

**What it exposes:**

- platform API (deployment management, configuration, knowledge)
- runtime API (query, action execution, confirmation flow)
- Shopify OAuth callbacks (install, token exchange)
- Shopify webhook receiver (products/update, orders/create, app/uninstalled)
- widget configuration endpoint (theme extension blocks fetch config from here)
- partner API (referral tracking, store management)

**Design direction:** no visual interface — returns JSON. Should return a clean JSON info response on root path:

```json
{
  "service": "LoomAI Platform API",
  "status": "operational",
  "docs": "https://docs.loomai.pro/api"
}
```

**Tech stack:** existing Spring Boot backend, reverse proxy via Cloudflare or nginx

**Priority:** must have — Shopify OAuth and webhooks require a stable public URL

---

### 2.4 docs.loomai.pro — Documentation Hub

**Purpose:** all documentation for merchants, partners, and developers

**Who visits:** merchants setting up the product, partners deploying to clients, developers integrating via API

**What it shows:**

Organized into sections:

1. **Getting Started**
   - install from Shopify App Store
   - connect your store
   - configure intelligence surfaces
   - go live

2. **Merchant Guide**
   - understanding intelligence surfaces (what each block does)
   - customizing appearance
   - managing knowledge sources
   - billing and plans
   - troubleshooting

3. **Partner Guide**
   - partner program overview
   - deployment checklist
   - configuration templates by store type
   - multi-store management
   - referral tracking and commissions

4. **API Reference**
   - authentication
   - endpoints
   - webhooks
   - rate limits

5. **Platform Guide** (for operators / advanced)
   - deployment configuration
   - provider setup
   - action catalog
   - confirmation policies
   - prompt management

**Design direction:**

- clean sidebar navigation with search
- code examples with copy buttons
- light mode with syntax highlighting
- fast page loads, static generation
- breadcrumbs for deep pages

**Tech stack:** Mintlify, GitBook, or Docusaurus — do not build custom

**Priority:** must have before partner recruitment

---

### 2.5 partners.loomai.pro — Partner Portal

**Purpose:** where partners apply, onboard, track referrals, and access resources

**Who visits:** prospective partners (pre-signup), active partners (daily/weekly)

**What it shows:**

**Pre-login (landing page):**

- what the partner program offers
- commission structure (20% recurring, tiered)
- who it is for (agencies, consultants, integrators)
- how it works (3 steps: apply, deploy, earn)
- application form

**Post-login (dashboard):**

- referred merchants list with status (active, trial, churned)
- commission balance and payout history
- referral link and code
- merchant health overview (sync status, usage, plan)
- deployment guides and templates (links to docs)
- resource downloads (logos, one-pagers, pitch deck)
- support channel link

**Design direction:**

- landing page matches loomai.pro visual language
- dashboard is clean and focused — partner sees their portfolio at a glance
- commission numbers should be prominent — this is what motivates partners
- merchant cards should show health status with clear indicators
- mobile responsive — partners check this on the go

**Tech stack:** can be a section of app.loomai.pro behind partner auth, or a separate lightweight app

**Priority:** should have — needed when first partners sign up (Month 1-2)

---

### 2.6 demo.loomai.pro — Live Demo Store

**Purpose:** a working Shopify store with Loom Companion fully installed and configured

**Who visits:** prospective merchants, prospective partners, anyone evaluating the product

**What it shows:**

- a real Shopify storefront (fashion or general merchandise theme)
- all intelligence surfaces active and working:
  - product insights block on product pages
  - AI search on collection and search pages
  - product FAQ block
  - comparison tool
  - policy strip on relevant pages
  - contextual pill throughout
- the companion chat available as secondary surface
- real product data (synced, indexed, queryable)
- a banner or badge: "This store is powered by Loom Companion — try the AI features"

**Design direction:**

- looks like a real store, not a tech demo
- the AI surfaces should feel native to the theme
- visitors should be able to interact with every intelligence surface
- a floating "Learn more about Loom Companion" CTA that links to loomai.pro

**Tech stack:** Shopify development store with a clean free theme + Loom Companion installed

**Priority:** should have — strongest sales tool for both merchants and partners

---

### 2.7 status.loomai.pro — Service Status

**Purpose:** public uptime and incident reporting

**Who visits:** merchants checking if an issue is on their end, partners during deployment

**What it shows:**

- service status for each component:
  - Platform API
  - Runtime (query processing)
  - Shopify sync
  - Widget CDN
  - Dashboard
- uptime percentage (90-day rolling)
- incident history with resolution notes
- subscribe to updates (email or webhook)

**Design direction:**

- minimal, clean, fast-loading
- green/amber/red status indicators
- no unnecessary branding — this is a utility page
- matches loomai.pro color palette

**Tech stack:** Betterstack (betteruptime.com), Instatus, or Atlassian Statuspage — do not build custom

**Priority:** should have — set up in 30 minutes, builds trust immediately

---

### 2.8 blog.loomai.pro — Content Hub

**Purpose:** product updates, merchant stories, AI commerce insights, SEO content

**Who visits:** merchants, partners, search traffic, social media traffic

**Content categories:**

1. **Product Updates**
   - new features, improvements, fixes
   - quarterly roadmap updates

2. **Merchant Stories**
   - case studies from early adopters
   - before/after metrics
   - how they use intelligence surfaces

3. **Partner Spotlights**
   - featured partners and their approach
   - deployment stories

4. **AI Commerce Insights**
   - how AI changes shopping behavior
   - intelligence surface design principles
   - comparison with traditional chatbot approaches
   - SEO-targeted content

**Design direction:**

- clean reading experience
- featured image + title + date + category
- no sidebar clutter
- good typography, readable line lengths
- share buttons but not intrusive

**Tech stack:** Ghost, Hashnode, or blog section in Next.js site — whatever is fastest to launch

**Priority:** nice to have — start when there is content worth publishing (Month 3+)

---

### 2.9 cdn.loomai.pro — Asset Delivery

**Purpose:** serves storefront widget JavaScript, CSS, and static assets

**Who visits:** no humans — loaded by Shopify theme extension blocks on merchant storefronts

**What it serves:**

- intelligence surface JavaScript bundles
- CSS for embedded blocks
- icon assets
- configuration payloads

**Design direction:** no visual interface — pure CDN

**Tech stack:** Cloudflare CDN, AWS CloudFront, or Vercel Edge

**Priority:** must have — storefront blocks need a reliable, fast CDN

**Performance requirements:**

- global edge caching
- gzip/brotli compression
- cache-control headers (long TTL for versioned assets)
- total widget bundle under 50KB gzipped

---

## 3) DNS and SSL Setup

All subdomains should use:

- Cloudflare DNS (or similar) for easy management
- automatic SSL via Cloudflare or Let's Encrypt
- CNAME records pointing to respective hosting services

Recommended DNS records:

```
loomai.pro          A/CNAME    → static hosting (Vercel, Netlify, or Framer)
app.loomai.pro      CNAME      → dashboard hosting (Vercel or direct server)
api.loomai.pro      CNAME      → backend server (Railway, Render, or VPS)
docs.loomai.pro     CNAME      → Mintlify/GitBook hosted
partners.loomai.pro CNAME      → same as app or separate
demo.loomai.pro     CNAME      → Shopify store custom domain
status.loomai.pro   CNAME      → Betterstack/Instatus hosted
blog.loomai.pro     CNAME      → Ghost/blog hosting
cdn.loomai.pro      CNAME      → Cloudflare CDN / CloudFront
```

---

## 4) Implementation Priority

### Phase 1: Before Shopify App Store Launch

1. `api.loomai.pro` — point at backend (required for OAuth)
2. `app.loomai.pro` — point at merchant dashboard
3. `cdn.loomai.pro` — point at widget assets
4. `loomai.pro` — build and deploy company homepage

### Phase 2: First Month After Launch

5. `docs.loomai.pro` — deploy documentation
6. `demo.loomai.pro` — set up demo Shopify store
7. `status.loomai.pro` — set up status page (30 minutes)

### Phase 3: Partner Program Launch (Month 2-3)

8. `partners.loomai.pro` — deploy partner portal
9. `blog.loomai.pro` — start publishing content

---

## 5) Shared Visual Identity Across Subdomains

All public-facing subdomains should share:

- the same logo and wordmark
- the same color palette (defined in UI redesign document)
- the same typography (Inter or equivalent)
- consistent navigation: logo top-left links to loomai.pro, minimal top-right links
- consistent footer with links to all subdomains
- "LoomAI Labs" branding, not "AI Enablement Control Plane"

The goal is that a visitor moving between loomai.pro, docs.loomai.pro, and partners.loomai.pro feels like they are in the same product world.

---

## 6) Email Subdomains

In addition to web subdomains, set up email routing:

- `hello@loomai.pro` — general contact
- `partners@loomai.pro` — partner program inquiries
- `support@loomai.pro` — merchant support
- `status@loomai.pro` — automated status notifications

Use Google Workspace, Zoho Mail, or Cloudflare Email Routing.
