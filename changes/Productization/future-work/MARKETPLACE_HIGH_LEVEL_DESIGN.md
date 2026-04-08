# Loom AI Marketplace: High-Level Design

Status: design document (2026-04-08)

---

## 1) What Is the Marketplace

The Loom AI Marketplace is the platform's plugin ecosystem. It allows deployment operators to browse, install, and configure pre-built capabilities — rather than building everything from scratch.

A deployment without marketplace plugins is a blank canvas: the operator must configure their own prompts, connect their own APIs, and source their own knowledge data. The marketplace changes this by offering ready-made building blocks that snap into any deployment.

The marketplace serves three purposes:

1. **Accelerate time-to-value** — a new customer can have a working AI assistant in minutes by cloning a template and installing a few plugins, instead of configuring everything manually.

2. **Unlock capabilities the operator cannot build alone** — shared data sources maintained by Loom AI (or third parties) give deployments access to knowledge they could not vectorize themselves.

3. **Create a revenue channel** — paid plugins generate recurring and one-off revenue independent of the core platform subscription.

---

## 2) Three Plugin Layers

The marketplace contains three distinct types of plugins. Each serves a different purpose and has a different installation behaviour.

### 2.1 Agent Templates (Clone and Customise)

An agent template is a complete, pre-configured deployment blueprint. It includes prompts, action definitions, knowledge source configuration, and recommended plugin add-ons — everything needed for a specific use case.

When a user installs a template, the platform creates a new deployment from the blueprint. The user then customises it: fills in their business name, connects their API, uploads their data. The template provides the starting structure; the user provides the content.

**Examples:**

- E-commerce store assistant (product search, order tracking, returns)
- Healthcare clinic assistant (appointment booking, symptom FAQ, patient intake)
- Ticket resolver (issue classification, escalation routing, status lookup)
- Car dealer assistant (vehicle search, test drive booking, finance enquiry)
- Restaurant assistant (menu enquiry, reservation, delivery tracking)

Templates should cover the most common verticals. They are the primary onboarding path for non-technical customers.

### 2.2 Action Plugins (Read and Write)

An action plugin adds one or more API-connected actions to an existing deployment. These are the individual capabilities that make an AI assistant useful beyond answering questions.

**Read actions** query external data sources on behalf of the user:
- Vehicle registration lookup
- Postcode and address resolution
- Weather and location data
- Flight or delivery tracking

**Write actions** execute operations on external systems:
- Book an appointment via a scheduling service
- Process a refund via a payment provider
- Send an email or SMS via a messaging service
- Create a support ticket in a helpdesk

Each action plugin defines what it connects to, what parameters it needs, what the user must configure (their own API credentials), and what policies should apply (confirmation before write operations, rate limits, etc.).

The plugin author controls the fixed configuration. The installing user provides their own credentials and any customisable settings.

### 2.3 Data Plugins (Shared Knowledge Sources)

A data plugin adds a shared, read-only vector data source to a deployment's RAG pipeline. When the AI assistant answers questions, it searches both the deployment's private knowledge and any installed data plugins.

The data itself is maintained centrally — either by Loom AI or by a third-party data provider. Individual deployments do not write to or manage the shared data. They only read from it.

**Examples:**

- UK vehicle listings from AutoTrader (synced periodically via their API)
- UK postcode and address database
- Product safety recalls database
- Public health condition information
- Common e-commerce shipping policies and FAQ

Data plugins solve a specific problem: many deployments need the same reference data, but each operator indexing it independently wastes storage, compute, and API quota. A single shared collection serves all subscribers.

---

## 3) Plugin Definition Model

Every plugin is defined by a declarative definition file. This file describes what the plugin is, what it provides, what the user must configure, and how it is priced.

The definition file is the single source of truth for a plugin. The platform reads it to:

- Display the plugin in the marketplace catalogue
- Present the correct configuration form to the installing user
- Inject the correct actions, data sources, or deployment blueprint on installation
- Enforce pricing and subscription status

**A definition file contains:**

- **Identity:** plugin ID, name, version, author, category, description, icon
- **Type:** whether this is a template, action plugin, or data plugin
- **Pricing:** free, one-off payment, or recurring subscription
- **What it provides:** the actions, data sources, or deployment blueprint it adds
- **Fixed configuration:** settings controlled by the plugin author, not editable by the installing user
- **User configuration:** settings the installing user must provide (API keys, business name, preferences)

The definition file does not contain implementation code. It is purely declarative. The platform interprets it.

### Plugin Identity and Versioning

Each plugin has a unique ID that never changes. Versions follow semantic versioning. When a plugin is updated, existing installations can continue on the previous version or opt into the upgrade.

The platform must handle version compatibility: a plugin installed at version 1.0 should not break if the definition is updated to version 1.1. Breaking changes require a new major version and explicit migration.

---

## 4) Installation Behaviour

### Installing a Template

1. User browses marketplace, selects a template (e.g. "E-Commerce Store Assistant")
2. Platform shows a preview of what the template includes (prompts, actions, knowledge sources)
3. User clicks "Clone" — platform creates a new deployment from the template blueprint
4. User is presented with the configuration form (store name, API URL, credentials)
5. User fills in their settings — saved as deployment configuration and secrets
6. Platform triggers initial knowledge sync (crawl store URL, index uploaded documents)
7. Deployment is ready in draft state — user can test, then publish and go live
8. Platform shows recommended add-on plugins (e.g. "Add Calendly for appointment booking")

### Installing an Action Plugin

1. User browses marketplace from within an existing deployment's settings
2. Selects an action plugin (e.g. "Calendly Booking")
3. If paid: payment flow (one-off or subscription start)
4. User presented with configuration form (API token, default settings)
5. User fills in their credentials — saved as deployment secrets
6. Plugin's actions are merged into the deployment's available actions
7. The AI assistant can now invoke those actions during conversations

### Installing a Data Plugin

1. User browses marketplace from within an existing deployment's settings
2. Selects a data plugin (e.g. "AutoTrader UK Vehicles")
3. If paid: subscription start
4. User fills in any required configuration (dealer ID, region filter)
5. The shared data collection is linked to the deployment's RAG pipeline
6. The AI assistant now searches both private knowledge and the plugin's shared data when answering questions
7. Answers sourced from plugin data are attributed (e.g. "According to AutoTrader listings...")

### Uninstalling

- Action plugins: actions removed from deployment, user credentials deleted
- Data plugins: shared collection unlinked from RAG pipeline, no data deleted (it's shared)
- Templates: the deployment itself was created from the template — uninstalling the template does not delete the deployment

---

## 5) Pricing and Subscription Model

The marketplace supports three pricing models:

### Free

No payment required. Used for:
- All agent templates (templates drive adoption — keep them free)
- Community-contributed action plugins
- Public data sources (postcodes, public health data)

### One-Off Payment

Single payment, permanent access. Used for:
- Action plugins where the user provides their own API credentials (Calendly, SendGrid)
- The ongoing cost is borne by the user's own API subscription, not by Loom AI

### Recurring Subscription

Monthly payment, access revoked if subscription lapses. Used for:
- Data plugins where Loom AI maintains the data (AutoTrader, DVLA)
- Premium action plugins where Loom AI proxies the API and absorbs the per-call cost
- Third-party plugins where the publisher charges a recurring fee

### Subscription Lifecycle

- Active: plugin is installed and functional
- Past due: payment failed, grace period (7 days), plugin still functional
- Suspended: grace period expired, plugin disabled but not uninstalled, data preserved
- Cancelled: user explicitly cancelled, plugin uninstalled at end of billing period

### Revenue for Third-Party Publishers

When Loom AI opens the marketplace to third-party plugin authors:
- Publisher sets the price
- Loom AI takes a platform fee (percentage of sale)
- Publisher receives the remainder
- Loom AI handles billing, invoicing, and payout

This is a future capability. At launch, all plugins are first-party (published by Loom AI).

---

## 6) Plugin Configuration: Fixed vs User-Configurable

Every plugin has two layers of configuration:

### Fixed Configuration

Set by the plugin author. Not visible or editable by the installing user. Controls:
- API endpoints the plugin connects to
- Timeout and retry behaviour
- Data refresh intervals
- Rate limits and safety policies
- Internal parameter defaults

The plugin author is responsible for these choices. The installing user trusts them.

### User Configuration

Set by the installing user. Presented as a form in the platform UI. Includes:
- Their API credentials (secrets, stored encrypted)
- Business-specific settings (store name, region, language)
- Preferences (default values, optional feature toggles)

Each user configuration field has a type (text, secret, URL, select, boolean), a label, help text, and whether it is required or optional.

**The separation is deliberate.** The plugin author ensures the plugin works correctly. The user ensures it works for their business. Neither can break the other's responsibility.

---

## 7) How Plugins Interact with Deployments

### Actions from Plugins Merge with Custom Actions

A deployment can have:
- Custom actions defined directly in the deployment's action configuration
- Plugin actions installed from the marketplace

Both appear in the same action pool. The AI assistant can invoke any of them based on user intent. There is no distinction at runtime between a custom action and a plugin action — they behave identically.

If a custom action and a plugin action have the same ID, the custom action takes precedence (the operator's configuration overrides the plugin).

### Data from Plugins Merges with Private Knowledge

A deployment's RAG pipeline searches:
- The deployment's private vector collections (operator's own data)
- Any installed data plugin collections (shared, read-only)

Results from both sources are merged, ranked, and presented to the LLM for answer generation. The source of each result is tracked so the answer can attribute where the information came from.

### Templates Create Deployments

A template does not merge into an existing deployment. It creates a new one. The resulting deployment is fully independent — the operator owns it and can modify everything. The template is the starting point, not an ongoing dependency.

---

## 8) Marketplace Catalogue and Discovery

The marketplace UI should make it easy for operators to find relevant plugins:

- **Browse by category:** scheduling, payments, data, messaging, CRM, e-commerce, automotive, healthcare
- **Browse by type:** templates, read actions, write actions, data sources
- **Filter by pricing:** free, one-off, subscription
- **Search by keyword**
- **Recommended plugins:** shown contextually based on what the deployment already has (e.g. "You have an e-commerce template — consider adding Stripe refunds")
- **Popular and trending:** based on installation count

Each plugin listing shows: name, description, author, category, pricing, install count, version, and a configuration preview.

---

## 9) Third-Party Plugin Publishing (Future)

At launch, all plugins are authored by Loom AI. In future, the marketplace opens to third-party publishers:

1. Publisher creates a plugin definition following the specification
2. Publisher submits for review (automated validation + manual review)
3. Approved plugins appear in the marketplace
4. Publisher sets pricing, receives revenue share
5. Publisher is responsible for maintaining the plugin and responding to issues

This creates a plugin ecosystem similar to Shopify App Store or WordPress Plugin Directory — but for AI assistant capabilities rather than website features.

The plugin definition format must be designed with this future in mind: it should be simple enough for third-party developers to author, strict enough to prevent misconfiguration, and expressive enough to cover a wide range of use cases.

---

## 10) Relation to Platform Architecture

The marketplace builds on top of existing platform capabilities:

| Platform Capability | Marketplace Uses It For |
|---|---|
| Deployment lifecycle (Draft → Live) | Templates create deployments that follow the standard lifecycle |
| Action configuration and routing | Action plugins inject into the existing action framework |
| Vectorization pipeline | Data plugins are additional vector sources in the existing pipeline |
| Deployment-scoped secrets | User-configured API credentials stored via existing secret management |
| Per-action auth | Plugin actions use the same auth mechanisms as custom actions |
| Pre/post action policies | Plugin actions can include policies (confirmation, webhooks) |
| Tenant and customer isolation | Plugin installations scoped to deployment, secrets isolated per tenant |

The marketplace does not replace or modify any of these systems. It composes them. A plugin is a pre-packaged configuration that uses the platform's existing capabilities — it is not a new runtime or execution model.

---

## 11) Success Metrics

The marketplace succeeds when:

- A new customer can go from signup to working AI assistant in under 30 minutes using a template
- Action plugins eliminate the need for customers to read API documentation or write action configs manually
- Data plugins provide knowledge that individual operators could not source themselves
- Paid plugins generate meaningful recurring revenue
- Third-party developers want to publish plugins because the installed base makes it worthwhile

---

## 12) What This Document Does Not Cover

This document intentionally omits:

- Specific configuration file formats and schemas (to be defined during implementation)
- Database table designs
- API endpoint specifications
- UI wireframes and component designs
- Implementation sequence and code-level architecture

These belong in implementation-level documents authored during the build phase. This document provides the conceptual foundation and design intent that implementation should follow.
