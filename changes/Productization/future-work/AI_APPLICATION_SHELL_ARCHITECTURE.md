# AI Application Shell: Architecture and Vision

Status: design document (2026-04-08)

---

## 1) What This Document Is

This document redefines the Loom AI widget — not as a chatbot, but as an AI Application Shell. It describes the architectural vision, the component model, the deployment-specific hosting strategy, and how the shell becomes the foundation for next-generation AI-native applications across any vertical.

This is a conceptual document. It does not contain implementation-level configuration or code, to avoid constraining future development decisions.

---

## 2) The Core Insight: This Is Not a Chatbot Widget

A chatbot widget is a text input/output window bolted onto an existing application. The user types, the bot replies, and the host application remains the primary interface.

The Loom AI widget is fundamentally different. It is an AI-native application surface where:

- The user expresses intent in natural language, and the shell renders rich interactive components in response (product cards, confirmation dialogs, cart views, document previews).
- Side panel modules (Cart, Products, Actions, AI Search, Docs) function as application tabs — not chat features, but full interactive views driven by AI context.
- Write operations go through governance flows (confirmation, counter-offers, policy checks) before execution — the shell enforces business rules conversationally.
- Items from the host environment (products on the current page, documents, cart contents) can be attached as context, making the conversation aware of what the user is looking at.

This makes the shell an **AI Application Runtime** — a universal interface that renders the right UI components based on user intent, deployment configuration, and installed plugins.

---

## 3) Traditional Apps vs AI Application Shell

### Traditional application model

The developer builds fixed screens. Each screen has hardcoded UI components. Navigation is explicit (menus, tabs, links). Adding a new capability means building a new screen, wiring it into navigation, and deploying a new version.

### AI Application Shell model

The deployment operator configures capabilities (actions, knowledge sources, policies). The shell renders appropriate UI components dynamically based on:

- What the user asks for (intent)
- What actions are available (deployment config + installed plugins)
- What the user is allowed to do (policies and governance)
- What context is attached (products, documents, cart items)

There is no fixed navigation. The shell surfaces the right interface at the right moment. A product search renders product cards. An order cancellation renders a confirmation dialog. A knowledge query renders document cards. A retention flow renders a counter-offer with confirm/reject buttons.

The same shell, with different deployment configurations and plugins, becomes a completely different application.

---

## 4) Component Model

The shell renders a defined set of component types. Each component type has a specific purpose and is triggered by specific backend response types.

### 4.1 Conversation Components

The primary interface. Message bubbles, AI thinking indicators, and the input composer. This is the backbone — every interaction starts and ends here.

### 4.2 Rich Result Cards

Structured data rendered as interactive cards rather than plain text. Examples in the current implementation:

- Product cards (image, title, price, stock level, SKU, attach button)
- Action execution results (success/failure states)
- Search result cards with document previews

These are not decoration. They are interactive — the user can attach a product card to the conversation context, add it to cart, or use it as the subject of a follow-up query.

### 4.3 Governance Flows

The shell's sharpest differentiator. When a write operation requires confirmation, the shell renders a governance component:

- **Confirmation dialogs** — "Cancel order PO-xxx?" with Confirm/Reject buttons
- **Counter-offer flows** — "Apply a 10% discount to keep your order instead of cancelling?" with Confirm/Reject
- **Clarification requests** — multi-field forms when the AI needs additional parameters before executing

These are not modal interruptions. They are first-class conversation participants — they appear inline, carry context, and their outcomes feed back into the conversation.

### 4.4 Context Panels

Side panels (desktop) or bottom sheets (mobile) that provide deep views into specific domains:

- **Cart view** — full shopping cart with items, quantities, totals, and checkout
- **Product details view** — expanded product information with images, pricing, stock
- **Documents view** — RAG results, knowledge articles, attachable references
- **Action history** — previously executed actions and their outcomes

These panels are not separate pages. They coexist with the conversation and can be opened, closed, and referenced within the conversational flow.

### 4.5 Module Buttons

The side rail of action buttons (Actions, Cart, Products, AI Search, Docs, and the expand/collapse toggle). Each button activates a context panel or triggers a specific interaction mode. Which buttons appear is determined by deployment configuration and installed plugins.

### 4.6 Future Component Types

The component model is extensible. New component types can be introduced as the platform and marketplace grow:

- **Form components** — structured input for complex operations (booking, registration, configuration)
- **Chart/data components** — visualisations rendered from action results
- **Media components** — video, audio, or interactive content from plugins
- **Map/location components** — for verticals like real estate, logistics, local services
- **Payment components** — inline checkout, payment confirmation, subscription management

Each new component type follows the same pattern: the backend response includes a type identifier, and the shell renders the appropriate component.

---

## 5) Deployment-Specific Widget Hosting

Every customer deployment gets its own configured instance of the shell. But this does not mean building or hosting a separate widget per customer.

### 5.1 One Bundle, Infinite Configurations

The shell is a single universal JavaScript bundle. It contains all component types, all rendering logic, and all interaction patterns. What makes each deployment unique is the configuration it receives at initialisation time.

The bundle is built once and hosted centrally. It does not change per customer. The deployment-specific behaviour comes entirely from configuration resolved at runtime.

### 5.2 The Resolve Flow

When the shell loads on a customer's site:

1. The embed code includes a deployment handle (a human-readable identifier for this specific deployment).
2. The shell calls the platform's resolve endpoint with the deployment handle.
3. The platform returns: the runtime URL for this deployment, a short-lived session token, and the widget configuration (branding, enabled features, module buttons, greeting, behaviour settings).
4. The shell connects directly to the runtime instance for all subsequent interactions (chat, actions, cart, search). The platform is not in the path after resolution.

This is the resolve-once-connect-direct pattern. The platform handles identity and configuration. The runtime handles conversation and actions. The shell handles rendering.

### 5.3 Widget Configuration Scope

The configuration returned on resolve controls everything the customer can customise:

**Branding and appearance** — primary colour, logo, widget title, font, border radius, light/dark mode, position on page. The shell applies these as theme variables, transforming its visual identity without rebuilding.

**Enabled modules** — which side-rail buttons appear. A deployment with no cart integration hides the Cart button. A deployment with no document knowledge hides the Docs button. A deployment focused purely on search might show only AI Search.

**Feature toggles** — conversation history on/off, quick actions on/off, debug inspector on/off, attachment capability on/off.

**Greeting and behaviour** — initial message, input placeholder text, auto-open rules, suggested first queries.

**Action definitions** — which actions are available, their confirmation requirements, their display metadata. This comes from the deployment's action configuration (including marketplace plugins).

### 5.4 Hosting Strategy

The universal bundle is hosted on a CDN — globally cached, fast to load, no per-customer infrastructure cost. The recommended approach:

- **CDN-hosted loader script** — a lightweight loader that bootstraps the full shell. Customers embed a single script tag.
- **Versioned bundles** — the loader can pin to a specific major version for stability, or track latest for automatic updates.
- **No customer-specific builds** — the bundle is identical for every deployment. All differentiation is configuration.

For customers who want deeper integration (embedding the shell as a React component within their own application), the shell is also available as an npm package with the same configuration-driven behaviour.

---

## 6) Integration Modes

The shell supports multiple integration patterns depending on the customer's technical sophistication and security requirements.

### 6.1 Script Tag Embed (Primary)

The simplest integration. The customer adds a script tag and a custom element to their page. The deployment handle in the element attribute tells the shell which configuration to resolve.

This mode uses Shadow DOM isolation to prevent CSS conflicts with the host page. The shell floats above the host content with its own styling context.

### 6.2 React Component Embed

For customers building React applications, the shell is available as a React component. The customer provides configuration as props and can programmatically control open/close state, listen for events, and pass authentication tokens.

### 6.3 Host Environment Integration

The shell can detect and integrate with the host environment:

- On a Shopify store, it detects products on the current page and offers to attach them as context.
- On a custom e-commerce site, the host can push product data to the shell via a JavaScript API.
- The host application can pass authentication tokens to the shell, enabling the runtime to identify the end user.

Each integration type (Shopify, WooCommerce, custom) provides adapter logic that translates between the host environment and the shell's internal context model.

---

## 7) Responsive Shell Architecture

The shell adapts its rendering to the available space, not just screen size.

### 7.1 Mobile Experience

On mobile devices, the shell takes a full-screen or near-full-screen layout. Context panels appear as bottom sheets with drag-to-dismiss gestures. Action buttons appear as a floating menu. The conversation occupies the full width.

### 7.2 Desktop Experience

On desktop, the shell can render with a side panel for context (cart, product details, documents) alongside the conversation. This allows the user to browse products in the side panel while chatting, or review their cart while asking questions about an order.

### 7.3 Embedded Experience (Future)

For customers who want the shell to be a primary interface rather than an overlay, the shell can render inline within a page section. This transforms it from a floating widget into an embedded AI application — the main content of the page, not an add-on.

---

## 8) Marketplace Integration with the Shell

The marketplace (described in the separate Marketplace High-Level Design document) plugs directly into the shell's component model.

### 8.1 Action Plugins Add Capabilities

When an operator installs an action plugin, the shell gains new capabilities:

- New actions become available in the conversation (the AI can invoke them).
- If the plugin defines a module button, it appears in the side rail.
- If the plugin defines confirmation requirements, the shell renders governance flows for those actions.

The shell does not need to know about specific plugins at build time. The deployment configuration (resolved at init) includes the full action catalog, including plugin-provided actions.

### 8.2 Data Plugins Add Knowledge

When an operator installs a data plugin, the shell's AI Search and document views gain access to new knowledge sources. The user experiences this as the AI knowing more — answering questions it couldn't before, surfacing documents from the plugin's vectorised data.

### 8.3 Agent Templates Configure Everything

When an operator clones an agent template, the resulting deployment comes pre-configured with specific module buttons, actions, knowledge sources, and branding. The shell renders exactly what the template defines, giving the operator a working AI application from the moment of installation.

---

## 9) Multi-Vertical Potential

The same shell architecture serves any vertical where users need to search, ask questions, and execute governed actions.

### 9.1 E-Commerce (Current)

Product cards, cart management, order actions (cancel, return, track), confirmation safety on write operations, retention counter-offers (discount to prevent cancellation).

### 9.2 Healthcare

Appointment cards (date, doctor, location), booking confirmation flows, symptom triage conversations, medical record document views, prescription action with multi-step governance.

### 9.3 Real Estate

Property cards (images, price, bedrooms, location), viewing booking with confirmation, mortgage calculator as an action, neighbourhood document knowledge, saved properties panel.

### 9.4 Financial Services

Transaction cards (amount, merchant, date), dispute initiation with confirmation, spending summary visualisations, policy document knowledge, transfer actions with amount confirmation and fraud checks.

### 9.5 HR and Internal Tools

Leave request forms, onboarding task cards, policy Q&A from knowledge base, ticket creation with approval workflows, employee directory search.

### 9.6 What Stays the Same Across Verticals

The shell architecture, the component model, the governance flows, the resolve-once-connect-direct pattern, the configuration-driven rendering, and the marketplace plugin integration. These are universal.

What changes per vertical is the deployment configuration: which actions are defined, which knowledge sources are connected, which component types are most frequently rendered, and what the branding looks like.

---

## 10) Session and State Model

### 10.1 Conversation Persistence

The shell maintains conversation history so users can return to previous interactions. Conversations are stored on the runtime and fetched on demand. The shell caches the current conversation locally for resilience against connection interruptions.

### 10.2 Context Accumulation

As the user interacts, the shell builds context: attached products, referenced documents, cart contents, previous action results. This context travels with the conversation, making the AI progressively more aware of what the user cares about.

### 10.3 Cross-Session Identity

When the host environment provides user authentication, the shell maintains identity across sessions. A returning user sees their conversation history, their cart, and their previous interactions — the AI remembers them.

When no authentication is provided (anonymous users), the shell uses session tokens with configurable expiry. The operator chooses how long anonymous sessions persist.

---

## 11) Security Boundaries

### 11.1 Shadow DOM Isolation

In script tag mode, the shell runs inside a Shadow DOM boundary. The host page cannot read or modify the shell's internal state. The shell cannot accidentally affect the host page's styles or behaviour.

### 11.2 Token Scoping

Session tokens issued on resolve are scoped to a specific deployment and have a limited lifetime. They grant access only to the actions and knowledge sources configured for that deployment. A token for one deployment cannot be used against another.

### 11.3 Governance as Security

The confirmation and policy flows are not optional UI features — they are security boundaries. A write action that requires confirmation cannot be executed without the user explicitly approving it through the shell's governance component. The runtime enforces this, not just the shell.

---

## 12) How the Shell Differs from Competitors

| Dimension | Industry standard widget | Loom AI Application Shell |
|-----------|------------------------|---------------------------|
| **Core model** | Text input / text output | Intent in / rich component out |
| **UI rendering** | Markdown, maybe cards | Product cards, governance dialogs, context panels, module buttons |
| **Write operations** | Execute immediately or not at all | Confirmation interception, counter-offers, policy enforcement |
| **Side panels** | None or basic FAQ drawer | Full application views (cart, products, docs, search) |
| **Configuration** | API key + colour | Full deployment config: actions, modules, features, branding, policies |
| **Multi-vertical** | Requires rebuild | Same shell, different config |
| **Plugin extensibility** | None | Marketplace plugins add actions, knowledge, and component types |
| **Host integration** | Embed and forget | Detects host environment, accepts context, shares state |

---

## 13) Widget Versioning and Lifecycle

### 13.1 Semantic Versioning

The widget bundle follows semantic versioning. Major versions indicate breaking changes to the embed API (attributes, JavaScript API, events). Minor versions add new component types or features. Patch versions fix bugs.

### 13.2 Controlled Rollout

New widget versions are rolled out through the CDN. The loader script supports version pinning, so customers on production can stay on a known-good version while new versions are validated.

### 13.3 Backward Compatibility

The embed API (script tag attributes, JavaScript control methods, event names) is a permanent contract. Once published, it does not change within a major version. Internal rendering changes (new component types, improved layouts) ship freely without breaking existing integrations.

---

## 14) What This Document Does Not Cover

- Specific file structures, class names, or code organisation — those are implementation decisions.
- Exact configuration schema or JSON formats — the shape will be defined during development.
- CDN provider selection or CI/CD pipeline for widget publishing.
- Analytics and telemetry collection within the shell.
- Accessibility (a11y) compliance details — important but out of scope for this architectural document.
- Performance budgets and bundle size targets.
- Specific theme variable names or CSS custom property conventions.

---

## 15) Summary

The Loom AI widget is not a chatbot. It is an AI Application Shell — a universal, configuration-driven interface that renders rich interactive components based on user intent, deployment configuration, and installed marketplace plugins.

One bundle serves every customer. Configuration makes each deployment unique. The resolve-once-connect-direct pattern keeps the platform out of the conversation path. Governance flows enforce business rules conversationally. The marketplace extends the shell's capabilities without rebuilding.

This architecture positions the shell as the foundation for next-generation AI-native applications: not applications with AI bolted on, but applications where AI is the primary interface and the UI emerges from the conversation.
