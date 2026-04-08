# Plugin Developer Extensibility: High-Level Design

Status: design document (2026-04-08)

---

## 1) What This Document Is

This document describes how third-party developers build, test, publish, and maintain plugins for the Loom AI platform. It defines the developer experience, the plugin contract, the extension points, and the governance model that keeps the ecosystem safe and reliable.

This sits on top of two existing documents:
- **Marketplace High-Level Design** — defines the three plugin layers (templates, actions, data) and the marketplace as a whole.
- **AI Application Shell Architecture** — defines the shell's component model and how plugins surface in the UI.

This document answers the question: "I'm a developer who wants to build a plugin for Loom AI. What can I extend, what are the rules, and how does my plugin get into customers' hands?"

This is a conceptual document. It does not contain implementation-level schemas or code.

---

## 2) Who Builds Plugins

### 2.1 First-Party (Loom AI)

At launch, all plugins are authored by Loom AI. This establishes the quality bar, proves the plugin model works end-to-end, and populates the marketplace with enough options to be useful on day one.

First-party plugins have no special runtime privileges. They follow the same definition format, same installation flow, and same review standards as third-party plugins will. This is deliberate — it forces the plugin system to be complete enough for external developers from the start.

### 2.2 Third-Party (External Developers)

When the marketplace opens to external developers, three types of publishers emerge:

**SaaS vendors** — companies that want their product accessible inside Loom AI deployments. A scheduling company publishes a booking plugin. A payment provider publishes a refund plugin. A CRM publishes a contact lookup plugin. Their motivation is distribution — every Loom AI deployment that installs their plugin is a new integration point for their service.

**Data providers** — companies or individuals that maintain valuable datasets and want to monetise them as knowledge sources. A vehicle data aggregator publishes a listings plugin. A regulatory body publishes a compliance database plugin. Their motivation is a new revenue channel for data they already maintain.

**Solution builders** — agencies, consultants, or freelancers that build vertical solutions for specific industries. They publish agent templates pre-configured for car dealerships, dental clinics, property agencies, or restaurants. Their motivation is productising their expertise — build once, sell to many.

### 2.3 Community Contributors

Open-source plugins published for free. No revenue expectation. Motivated by contribution, reputation, or because they built something useful for their own deployment and want to share it.

---

## 3) What Can Be Extended

The plugin system exposes four extension points. Each is a different surface of the platform that a plugin can add to.

### 3.1 Actions

The most common extension point. An action plugin defines one or more actions that the AI assistant can invoke during conversations.

**What the plugin defines:**

- Action identity: unique ID, display name, description for the AI
- API connection: which external API this action calls, what HTTP method, what endpoint path
- Parameters: what inputs the action needs (extracted from conversation or requested from user)
- Response mapping: how to transform the API response into something the shell can render
- Policies: what governance applies (confirmation required, rate limits, cost gates)
- Auth requirements: what credentials the installing operator must provide

**What the plugin does NOT define:**

- How the shell renders the result — that is the shell's responsibility based on response type
- How the AI decides to invoke this action — that is the orchestration layer's responsibility based on intent classification
- How credentials are stored — that is the platform's responsibility via its secret management

An action plugin is a declarative description of capability. It is not executable code.

### 3.2 Knowledge Sources

A data plugin defines a shared, read-only vector collection that any deployment can subscribe to.

**What the plugin defines:**

- Data source identity: name, description, category, update frequency
- Source origin: where the data comes from (API sync, file upload, web crawl) — described, not implemented
- Schema hints: what kind of documents are in the collection (product listings, articles, regulations, FAQ)
- Attribution: how answers sourced from this data should be credited ("According to AutoTrader listings...")
- Access model: who can subscribe (all deployments, specific tiers, geographic restrictions)

**What the plugin does NOT define:**

- Vectorization implementation — the platform's existing vectorization pipeline handles this
- Storage location — the platform decides where vectors live
- RAG retrieval logic — the existing pipeline handles search, ranking, and merging with private knowledge

### 3.3 Agent Templates

A template plugin defines a complete deployment blueprint.

**What the plugin defines:**

- Deployment profile: name, description, target vertical, target audience
- Prompt configuration: system prompt, persona, tone, guardrails
- Action set: which actions (custom-defined or references to marketplace action plugins) are included
- Knowledge strategy: what types of knowledge the operator should provide (product catalog, FAQ, policies)
- Widget configuration: recommended branding, enabled modules, greeting message, quick actions
- Recommended add-ons: which marketplace plugins complement this template

**What the plugin does NOT define:**

- The operator's actual data — they provide their own products, documents, API credentials
- Fixed business logic — the operator customises everything after cloning

### 3.4 Shell Modules (Future)

A future extension point allowing plugins to define new side-panel modules for the shell. A CRM plugin might add a "Contacts" module button. An analytics plugin might add a "Reports" module.

This is not available at launch. It requires the shell component model to stabilise first. But the architecture anticipates it: the shell's module buttons are already configuration-driven, so adding plugin-defined modules is a natural extension.

---

## 4) The Plugin Definition Contract

Every plugin is described by a single declarative definition. This definition is the contract between the plugin developer and the platform.

### 4.1 What the Definition Contains

**Identity block** — plugin ID (globally unique, immutable after publication), display name, version, author, category tags, short description, long description, icon.

**Type declaration** — one of: action, data, template. Determines which extension points apply.

**Capability block** — what the plugin provides. For action plugins: action definitions. For data plugins: data source description. For templates: the deployment blueprint. This is the functional core.

**Configuration block** — split into fixed configuration (set by the plugin author, invisible to the operator) and user configuration (presented as a form to the installing operator). Each user configuration field declares its type, label, help text, whether it is required, and validation rules.

**Pricing block** — free, one-off, or subscription. Price amount and currency. Trial period if applicable.

**Compatibility block** — minimum platform version, required platform features, incompatible plugins (if any).

**Metadata block** — documentation URL, support contact, changelog, screenshots, demo deployment link.

### 4.2 What the Definition Does NOT Contain

- **Executable code** — plugins are declarative. They describe what to connect to and how, not how to execute. The platform interprets the definition and handles all execution.
- **Direct database access** — plugins cannot read or write to the platform's internal data stores.
- **Shell rendering logic** — plugins describe what data to surface. The shell decides how to render it.

This is the fundamental security boundary. Plugins are configuration, not code. A malicious plugin definition cannot execute arbitrary logic on the platform or in the shell.

### 4.3 Versioning Rules

- Plugin IDs are permanent. Once registered, a plugin ID cannot be reused.
- Versions follow semantic versioning: MAJOR.MINOR.PATCH.
- **Patch**: bug fixes, description changes, metadata updates. Auto-applied to all installations.
- **Minor**: new optional capabilities, new user configuration fields with defaults. Auto-applied — existing installations gain new capabilities without breaking.
- **Major**: breaking changes to user configuration (removed fields, changed semantics), changed action signatures. Existing installations stay on the previous major version. Operators must explicitly upgrade and may need to reconfigure.

---

## 5) Developer Workflow

### 5.1 Create

The developer starts with the plugin definition format. The platform provides:

- **Definition reference** — complete documentation of every field, type, and constraint
- **Starter templates** — pre-filled definitions for common plugin patterns (REST action, data source, e-commerce template)
- **CLI validation tool** — validates a definition file offline before submission, catching errors early

The developer writes their definition file, specifying what their plugin connects to, what configuration it needs, and how it should appear in the marketplace.

### 5.2 Test

Before publishing, the developer must verify their plugin works correctly. The platform supports:

- **Sandbox deployment** — a private deployment where the developer can install their own plugin and test it end-to-end. Not visible to other users.
- **Definition dry-run** — submit the definition to a validation endpoint that checks syntax, resolves references, and reports errors without publishing.
- **Mock installation** — simulate the operator installation flow to verify user configuration fields render correctly and validation rules work.

Testing is local to the developer's own account. They cannot test against other users' deployments.

### 5.3 Submit

When the plugin is ready, the developer submits it for review:

1. Upload the definition file through the developer portal
2. Provide marketplace listing content (screenshots, description, documentation link)
3. Set pricing
4. Submit for review

The submission enters a review queue. The developer can track status through their dashboard.

### 5.4 Review

Every plugin submission goes through review before appearing in the marketplace. Review has two stages:

**Automated validation:**
- Definition schema compliance (all required fields present, correct types)
- API endpoint reachability (can the platform reach the configured endpoints?)
- Naming and content policy (no trademark violations, no misleading descriptions)
- Compatibility check (declared platform features actually exist)

**Manual review (for paid plugins and new publishers):**
- Does the plugin do what it claims?
- Is the user configuration clear and complete?
- Are policies appropriate for the action types (write actions require confirmation)?
- Is the pricing reasonable for what is offered?

Free plugins from established publishers may graduate to automated-only review after a track record is established.

### 5.5 Publish

Approved plugins appear in the marketplace catalogue. The developer can:

- View installation metrics (installs, active installations, uninstalls)
- View revenue reports (for paid plugins)
- Respond to operator feedback
- Submit updates (minor and patch auto-publish, major goes through review)

### 5.6 Maintain

Published plugins carry ongoing responsibility:

- If the external API the plugin connects to changes, the plugin definition must be updated
- If operators report issues, the developer is expected to respond
- If the platform evolves (new features, deprecated fields), the developer may need to update for compatibility
- Inactive plugins (no updates for an extended period, unresponsive developer) may be flagged or delisted

---

## 6) Security and Trust Model

### 6.1 No Code Execution

This is the single most important architectural decision. Plugins are declarative definitions, not executable code. The platform interprets definitions and handles all execution internally.

This means:
- A plugin cannot run arbitrary code on the platform
- A plugin cannot access other deployments' data
- A plugin cannot modify the platform's behaviour beyond its declared capabilities
- A plugin cannot exfiltrate data through custom logic

The attack surface is limited to what the definition format can express, which is bounded by design.

### 6.2 Credential Isolation

When an operator installs a plugin and provides API credentials, those credentials are:

- Encrypted at rest using the platform's existing secret management
- Scoped to that specific deployment — not accessible by the plugin developer or other deployments
- Used only for the specific API calls defined in the plugin
- Revocable by the operator at any time (uninstalling the plugin deletes credentials)

The plugin developer never sees operator credentials. They define where credentials are used (which API endpoint), not what the credentials are.

### 6.3 Rate Limiting and Abuse Prevention

The platform enforces rate limits on plugin-provided actions regardless of what the plugin definition specifies:

- Per-deployment rate limits prevent a single installation from overwhelming the external API
- Platform-wide rate limits prevent a malicious plugin from generating excessive outbound traffic
- Cost gates on paid API calls prevent runaway costs

These limits are enforced by the platform, not by the plugin. The plugin definition can declare its own limits (which the platform respects), but the platform's limits are the floor.

### 6.4 Publisher Identity

Plugin publishers must verify their identity:

- Email verification (minimum)
- Organization verification for paid plugins (business registration, domain verification)
- API ownership verification for action plugins that connect to the publisher's own API

This creates accountability. If a plugin behaves badly, there is a verified entity responsible.

### 6.5 Revocation

The platform can revoke a published plugin at any time if:

- It violates the content or security policy
- The external API it connects to becomes unreachable for an extended period
- The publisher is unresponsive to critical issues
- Operator complaints indicate the plugin is misleading or harmful

Revocation disables new installations and optionally disables existing installations (with operator notification).

---

## 7) Plugin Interactions and Conflicts

### 7.1 Action ID Namespacing

Every action defined by a plugin is namespaced to the plugin ID. A scheduling plugin with ID `calendly-booking` defines actions like `calendly-booking:create-event`, `calendly-booking:list-slots`. This prevents collisions between plugins and between plugins and custom actions.

Custom actions (defined directly by the operator) have no namespace prefix. If a custom action ID collides with a plugin action ID, the custom action takes precedence.

### 7.2 Plugin-to-Plugin Dependencies

A plugin can declare that it recommends or requires another plugin. For example, an e-commerce template might recommend the Stripe refunds action plugin.

- **Recommends**: shown as a suggestion during installation. Operator can ignore.
- **Requires**: installation blocked unless the dependency is already installed. Used sparingly — only when one plugin literally cannot function without another.

Circular dependencies are not allowed. The platform validates this at submission.

### 7.3 Data Source Priority

When multiple data plugins provide overlapping knowledge (e.g. two vehicle listing sources), the platform's RAG pipeline handles this through its existing ranking and deduplication. The operator can set priority order for their installed data sources.

---

## 8) Revenue and Economics

### 8.1 Revenue Share

For paid plugins:
- Publisher sets the price
- Loom AI takes a platform fee (percentage of each transaction)
- Publisher receives the remainder
- Payouts on a regular cycle (monthly)

The platform fee covers: billing infrastructure, marketplace hosting, review process, and the distribution value of the marketplace itself.

### 8.2 Pricing Guidance

The platform provides pricing guidance to help publishers set reasonable prices:

- Action plugins where the operator provides their own API key: one-off payment recommended (the ongoing cost is the operator's)
- Action plugins where the publisher absorbs API costs: subscription recommended (ongoing cost to cover)
- Data plugins: subscription recommended (ongoing maintenance and data freshness)
- Templates: free recommended (drives platform adoption, which benefits all publishers)

### 8.3 Free Tier Plugin Access

Operators on the platform's free tier should have access to:
- All free plugins (templates, community action plugins, public data)
- A limited number of paid plugin trials
- Paid plugins at full price if they choose to subscribe

The free tier does not exclude operators from the marketplace. It limits platform features (deployment count, conversation volume), not plugin access.

---

## 9) Developer Portal

The developer portal is the publisher's workspace. It provides:

### 9.1 Plugin Management

- Create, edit, and version plugin definitions
- View submission status and review feedback
- Monitor installations and active usage
- View revenue and payout history

### 9.2 Testing Tools

- Sandbox deployments for end-to-end testing
- Definition validator
- Mock installation simulator
- API endpoint health checker

### 9.3 Documentation and Guides

- Plugin definition reference
- Starter templates for common patterns
- Best practices for each plugin type
- Example plugins with walkthroughs

### 9.4 Analytics

- Install and uninstall counts over time
- Active installation count
- Common configuration patterns (anonymised)
- Error rates (how often the plugin's external API fails)

---

## 10) Evolution Path

### Phase 1: First-Party Only (Launch)

- Loom AI publishes 10-20 plugins covering common use cases
- Plugin definition format is validated in production
- Marketplace UI is built and refined
- No external publishers

### Phase 2: Invite-Only Third-Party (Post-Launch)

- Selected partners invited to publish plugins
- Publisher onboarding process refined
- Review process scaled and partially automated
- Revenue share model validated

### Phase 3: Open Marketplace

- Any verified developer can submit plugins
- Full automated review pipeline with manual escalation
- Publisher reputation and ratings system
- Plugin analytics and discovery algorithms
- Community contributions welcomed

### Phase 4: Shell Module Extensions (Future)

- Plugins can define new side-panel modules for the shell
- Custom card types for specialised rendering
- Richer interaction patterns beyond action execution and knowledge retrieval

---

## 11) What Makes This Different from App Stores

The Loom AI plugin model differs from traditional app stores (Shopify, WordPress) in one critical way: **plugins are not code**.

In Shopify or WordPress, a plugin is executable code that runs inside the platform. This creates an enormous security surface, requires sandboxing, and means every plugin can potentially break the platform or compromise data.

In Loom AI, a plugin is a declarative definition. It describes what to connect to, what configuration is needed, and what capabilities are added. The platform handles all execution. This means:

- No sandboxing required — there is no code to sandbox
- No performance risk — plugin logic does not compete with platform logic for resources
- No security escalation — the definition format is the security boundary
- Simpler authoring — developers write configuration, not code
- Faster review — validating a definition is orders of magnitude simpler than auditing code

The trade-off is expressiveness: plugins cannot do arbitrary things. They are limited to the extension points the platform defines (actions, data, templates, and eventually shell modules). But for the target use cases — connecting APIs, sharing data, and packaging deployment blueprints — this is sufficient.

---

## 12) What This Document Does Not Cover

- Specific definition file format (JSON, YAML, or other — to be decided during implementation)
- Database schema for plugin registry, installations, and subscriptions
- API endpoint specifications for the developer portal
- Exact platform fee percentage
- Legal terms for publishers (developer agreement, liability, IP)
- Detailed review criteria and rubrics
- Payout provider selection and financial compliance

These belong in implementation-level documents authored during the build phase.
