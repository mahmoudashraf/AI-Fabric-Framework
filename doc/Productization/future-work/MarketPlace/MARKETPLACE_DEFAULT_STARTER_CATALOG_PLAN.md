# Marketplace Default Starter Catalog Plan

Status: implementation-oriented product seed plan (2026-04-14)

This document defines the default first-party plugins that should be available to operators in a new marketplace-enabled platform environment.

It is not a runtime/framework plan.
It is a product seed plan for the catalog content that should ship by default.

Related docs:

- `doc/Productization/future-work/MarketPlace/README.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`

---

## 1) Goals

The default starter catalog should:

- make the marketplace feel immediately useful on day 1
- demonstrate each shipped first-class plugin type
- show the three supported pricing models:
  - `FREE`
  - `ONE_OFF`
  - `SUBSCRIPTION`
- cover more than one vertical so the marketplace does not look commerce-only
- stay small and opinionated instead of becoming a noisy sample gallery

Recommended rule:

- ship a tight first-party starter set first
- add broader partner or third-party listings later

---

## 2) Recommended Default Starter Set

Recommended default catalog size:

- `6` first-party plugins in the initial visible starter set

These should be:

1. `mkp-template-commerce-shell`
2. `mkp-action-shopify-admin`
3. `mkp-data-commerce-catalog`
4. `mkp-template-support-desk-shell`
5. `mkp-data-help-center`
6. `mkp-action-notifications`

Why this set:

- it keeps the existing commerce path strong
- it adds a second obvious business workflow: support operations
- it adds one cross-domain utility plugin that many deployments can reuse

---

## 3) Concrete Plugin Definitions

### 3.1 `mkp-template-commerce-shell`

- display name: `Loom Commerce Shell`
- type: `TEMPLATE`
- pricing: `FREE`
- status:
  - already seeded
- purpose:
  - bootstrap a commerce-oriented deployment with strong shell defaults
- baseline contributions:
  - `template.curatedModuleId = commerce`
  - shell modules:
    - `docs`
    - `products`
    - `ai-search`
    - `actions`
  - default conversation mode:
    - `guided-commerce`
  - recommended plugin ids:
    - `mkp-action-shopify-admin`
    - `mkp-data-commerce-catalog`
- capability profiles:
  - `SURFACE`

Recommended product stance:

- keep all first-party shell templates free
- templates are the primary time-to-value path, so pricing them creates unnecessary friction

### 3.2 `mkp-action-shopify-admin`

- display name: `Shopify Admin Actions`
- type: `ACTION`
- pricing:
  - `ONE_OFF`
  - default amount: `49 USD`
- status:
  - already seeded
- purpose:
  - add real storefront/admin system actions to a commerce deployment
- baseline contributions:
  - `shopify-order-read`
  - `shopify-order-cancel`
- install form:
  - `store` text
  - `apiKey` secret ref
- capability profiles:
  - `SURFACE`
  - `POLICY_LOGIC`

Recommended product stance:

- action plugins that primarily use operator-owned credentials should default to `ONE_OFF`
- this avoids making the platform look like it rents access to the user's own integration

### 3.3 `mkp-data-commerce-catalog`

- display name: `Commerce Catalog Data`
- type: `DATA`
- pricing:
  - `SUBSCRIPTION`
  - default amount: `29 USD / month`
  - default trial: `7 days`
- status:
  - already seeded
- purpose:
  - provide shared commerce catalog and policy retrieval
- baseline contributions:
  - shared index source:
    - `commerce-catalog`
  - shell modules:
    - `docs`
    - `products`
    - `ai-search`
- install form:
  - `scope` select:
    - `refund-policy`
    - `catalog`
    - `all`
- capability profiles:
  - `SURFACE`

Recommended product stance:

- shared first-party data that Loom operates and refreshes should default to `SUBSCRIPTION`
- that aligns pricing with real ongoing hosting and maintenance cost

### 3.4 `mkp-template-support-desk-shell`

- display name: `Loom Support Desk Shell`
- type: `TEMPLATE`
- pricing: `FREE`
- status:
  - recommended next first-party seed
- purpose:
  - bootstrap an internal support or ticket-resolution assistant
- baseline contributions:
  - `template.curatedModuleId = support`
  - shell modules:
    - `docs`
    - `actions`
    - `ai-search`
    - `tickets`
  - default conversation mode:
    - `guided-support`
  - recommended plugin ids:
    - `mkp-data-help-center`
    - `mkp-action-notifications`
- capability profiles:
  - `SURFACE`

Recommended install target:

- internal operator-facing deployments
- support team copilots
- customer support assistant deployments

### 3.5 `mkp-data-help-center`

- display name: `Help Center Data`
- type: `DATA`
- pricing:
  - `FREE`
- status:
  - recommended next first-party seed
- purpose:
  - provide reusable help-center, FAQ, and policy retrieval for support-oriented assistants
- baseline contributions:
  - shared index source:
    - `help-center`
  - shell modules:
    - `docs`
    - `ai-search`
- install form:
  - `scope` select:
    - `faq`
    - `policy`
    - `all`
- capability profiles:
  - `SURFACE`

Recommended product stance:

- keep one high-quality shared support knowledge plugin free by default
- this gives non-technical users a strong zero-cost way to see data plugins working

### 3.6 `mkp-action-notifications`

- display name: `Notifications Actions`
- type: `ACTION`
- pricing:
  - `ONE_OFF`
  - default amount: `19 USD`
- status:
  - recommended next first-party seed
- purpose:
  - provide reusable outbound notification actions across multiple verticals
- baseline contributions:
  - `send-email`
  - `send-sms`
  - `send-slack-message`
- install form:
  - `provider` select:
    - `sendgrid`
    - `twilio`
    - `slack`
  - `credentialSecretRef` secret ref
  - `defaultSender` text optional
- capability profiles:
  - `SURFACE`

Recommended product stance:

- this is the cross-domain utility plugin in the starter set
- it should work with platform-approved outbound adapters only

---

## 4) Pricing Defaults

Recommended starter-catalog pricing defaults:

- `TEMPLATE`
  - default: `FREE`
- `ACTION`
  - default:
    - `ONE_OFF` when the operator supplies their own external credentials
  - exception:
    - `SUBSCRIPTION` only when Loom or the publisher is absorbing real recurring infrastructure or proxy cost
- `DATA`
  - default:
    - `SUBSCRIPTION` for Loom-maintained premium shared datasets
    - `FREE` for foundational public or low-cost shared datasets

Recommended first-party pricing mix for the starter set:

- free:
  - `mkp-template-commerce-shell`
  - `mkp-template-support-desk-shell`
  - `mkp-data-help-center`
- one-off:
  - `mkp-action-shopify-admin`
  - `mkp-action-notifications`
- subscription:
  - `mkp-data-commerce-catalog`

This gives the marketplace a visible range of business models without overcomplicating day-1 operator choice.

---

## 5) Why These Examples

This set demonstrates the product clearly:

- template plugin:
  - deployment bootstrap and shell defaults
- action plugin:
  - external read and write integrations
- data plugin:
  - shared retrieval with attribution
- multi-plugin composition:
  - template recommends action and data add-ons
- cross-domain utility:
  - notifications are useful beyond commerce

This also matches the real-world extension mix seen in systems like Shopify:

- solution templates
- integration actions
- shared knowledge or catalog data
- later workflow automation

But it stays inside this platform's stricter boundary:

- no arbitrary frontend code
- no arbitrary runtime code
- no bypass of draft -> publish -> apply

---

## 6) Seed Order

Recommended implementation order:

### Seed A: Current shipped baseline

- `mkp-template-commerce-shell`
- `mkp-action-shopify-admin`
- `mkp-data-commerce-catalog`

### Seed B: Immediate next first-party defaults

- `mkp-template-support-desk-shell`
- `mkp-data-help-center`
- `mkp-action-notifications`

Recommended release rule:

- Seed B should ship only after the manifests, catalog listing copy, and basic install-flow verification are all in place

---

## 7) Suggested Manifest Shapes

These are intentionally compact examples, not final migration payloads.

### 7.1 `mkp-template-support-desk-shell`

```yaml
schemaVersion: 1
pluginId: mkp-template-support-desk-shell
version: 1.0.0
pluginType: TEMPLATE
displayName: Loom Support Desk Shell
pricing:
  pricingModel: FREE
permissions:
  contributesTemplate: true
  contributesSurfaceCapabilities: true
capabilityProfiles:
  - SURFACE
contributions:
  template:
    curatedModuleId: support
    recommendedPluginIds:
      - mkp-data-help-center
      - mkp-action-notifications
    shell:
      enabledModuleIds:
        - docs
        - ai-search
        - actions
        - tickets
      defaultConversationMode: guided-support
```

### 7.2 `mkp-data-help-center`

```yaml
schemaVersion: 1
pluginId: mkp-data-help-center
version: 1.0.0
pluginType: DATA
displayName: Help Center Data
pricing:
  pricingModel: FREE
permissions:
  contributesKnowledgeSources: true
  contributesSurfaceCapabilities: true
capabilityProfiles:
  - SURFACE
installForm:
  - id: scope
    type: select
    required: true
    options:
      - faq
      - policy
      - all
contributions:
  knowledgeSources:
    - sourceType: shared-index
      sourceKey: help-center
      attributionLabel: Help center marketplace data
  shell:
    moduleRefs:
      - docs
      - ai-search
```

### 7.3 `mkp-action-notifications`

```yaml
schemaVersion: 1
pluginId: mkp-action-notifications
version: 1.0.0
pluginType: ACTION
displayName: Notifications Actions
pricing:
  pricingModel: ONE_OFF
  amount: 19.00
  currency: USD
permissions:
  contributesActions: true
  contributesSurfaceCapabilities: true
  requiresExternalHttpExecution: true
  requiresDeploymentSecrets: true
capabilityProfiles:
  - SURFACE
installForm:
  - id: provider
    type: select
    required: true
    options:
      - sendgrid
      - twilio
      - slack
  - id: credentialSecretRef
    type: secretRef
    required: true
  - id: defaultSender
    type: text
    required: false
contributions:
  actions:
    - actionId: send-email
      readOnly: false
      confirmationRequired: true
      adapterType: connector-http
    - actionId: send-sms
      readOnly: false
      confirmationRequired: true
      adapterType: connector-http
    - actionId: send-slack-message
      readOnly: false
      confirmationRequired: false
      adapterType: connector-http
  shell:
    moduleRefs:
      - actions
```

---

## 8) What Should Wait

Do not make these part of the default visible starter catalog yet:

- payments plugins
- third-party analytics or pixel plugins
- unrestricted storefront or theme code plugins
- policy-heavy plugins that require new function or rule execution surfaces
- automation plugins before automation support is implemented as a first-class control-plane type

Those are valid later categories, but they should not dilute the first starter catalog.

---

## 9) Acceptance Criteria

The starter catalog is ready when:

- operators can see a small high-quality first-party set immediately after enabling marketplace
- at least one free template, one paid action, and one subscription data plugin are visible
- each plugin type has a clear example with install-ready copy and configuration form
- the starter plugins compose cleanly into deployment drafts and impact previews
- the catalog looks intentional rather than like a raw developer demo
