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

- `7` first-party plugins in the initial visible starter set

These should be:

1. `mkp-template-commerce-shell`
2. `mkp-action-shopify-admin`
3. `mkp-data-commerce-catalog`
4. `mkp-template-support-desk-shell`
5. `mkp-data-help-center`
6. `mkp-action-notifications`
7. `mkp-automation-order-retention`

Why this set:

- it keeps the existing commerce path strong
- it adds a second obvious business workflow: support operations
- it adds one cross-domain utility plugin that many deployments can reuse
- it includes a real automation example so every shipped first-class plugin type is visible by default

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
  - already seeded
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
  - already seeded
- purpose:
  - provide reusable help-center, FAQ, and policy retrieval for support-oriented assistants
- baseline contributions:
  - shared index source:
    - `help-center`
  - dataset ownership:
    - plugin-owned tenant-shared dataset handle
  - launch ingestion mode:
    - packaged seed dataset
  - follow-on supported ingestion modes:
    - SQL sync connector
    - folder-of-files sync connector
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
- the plugin should seed a real starter FAQ corpus on first activation so the install is immediately demonstrable

### 3.6 `mkp-action-notifications`

- display name: `Notifications Actions`
- type: `ACTION`
- pricing:
  - `ONE_OFF`
  - default amount: `19 USD`
- status:
  - already seeded
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

### 3.7 `mkp-automation-order-retention`

- display name: `Order Retention Automation`
- type: `AUTOMATION`
- pricing:
  - `FREE`
- status:
  - already seeded
- purpose:
  - provide a real workflow automation example that compiles into deployment automation config
- baseline contributions:
  - trigger:
    - `order-cancel-requested`
  - action:
    - `offer-retention-discount`
  - workflow:
    - `order-cancel-retention`
  - schedule:
    - `retention-follow-up`
- install form:
  - `discountPercent` number
  - `cooldownDays` number
- capability profiles:
  - `POLICY_LOGIC`

Recommended product stance:

- keep the first automation example free
- it proves workflow compilation and governance without adding pricing friction to the starter catalog

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
- `AUTOMATION`
  - default:
    - `FREE` for first-party workflow starters that exist to prove deployment-governed automation
  - later:
    - `ONE_OFF` or `SUBSCRIPTION` when automation packages include premium maintained workflow content or managed execution cost

Recommended first-party pricing mix for the starter set:

- free:
  - `mkp-template-commerce-shell`
  - `mkp-template-support-desk-shell`
  - `mkp-data-help-center`
  - `mkp-automation-order-retention`
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
- automation plugin:
  - deployment-governed workflow compilation with triggers, actions, workflows, and schedules
- multi-plugin composition:
  - templates recommend action, data, and automation add-ons
- cross-domain utility:
  - notifications are useful beyond commerce

This also matches the real-world extension mix seen in systems like Shopify:

- solution templates
- integration actions
- shared knowledge or catalog data
- workflow automation

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
- `mkp-automation-order-retention`

### Seed B: Immediate next first-party defaults

- `mkp-template-support-desk-shell`
- `mkp-data-help-center`
- `mkp-action-notifications`
- Seed B is now implemented in the catalog seed set

---

## 7) Real Backing And Rollout Dependencies

Default starter plugins should not be demo-only metadata.

Recommended rollout rule:

- every default starter plugin that depends on underlying infrastructure must have that infrastructure represented in the canonical rollout path

For starter data plugins, this means:

- the plugin remains a business-facing `DATA` plugin
- the backing shared corpus lives on a real shared vector backend
- the canonical marketplace rollout provisions that shared-storage-capable backend as part of rollout instead of assuming an operator created it manually beforehand

Recommended platform stance:

- use a rollout-owned shared-storage-capable vector backend for the canonical marketplace deployment
- use hosted verification to seed or refresh a small verification corpus when the platform is fresh
- keep the catalog business-facing; do not expose the vector provider itself as the marketplace product

Concrete implication for the default data plugins:

- `mkp-data-commerce-catalog`
  - should resolve into a real shared index or shared vector handle
  - should be validated against a rollout-owned shared backend
- `mkp-data-help-center`
  - should follow the same model once seeded

This makes a new platform installation reproducible:

- platform admin can recreate the canonical marketplace rollout
- the rollout provisions the backing vector installation needed for the plugin proof
- hosted verification then proves the shared data plugin end to end

---

## 8) Suggested Manifest Shapes

These are intentionally compact examples, not final migration payloads.

### 8.1 `mkp-template-support-desk-shell`

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

### 8.2 `mkp-data-help-center`

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

### 8.3 `mkp-action-notifications`

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

## 9) What Should Wait

Do not make these part of the default visible starter catalog yet:

- payments plugins
- third-party analytics or pixel plugins
- unrestricted storefront or theme code plugins
- policy-heavy plugins that require new function or rule execution surfaces

Those are valid later categories, but they should not dilute the first starter catalog.

---

## 10) Acceptance Criteria

The starter catalog is ready when:

- operators can see a small high-quality first-party set immediately after enabling marketplace
- every shipped first-class plugin type has at least one visible first-party example
- at least one free template, one paid action, one subscription data plugin, and one automation plugin are visible
- each plugin type has a clear example with install-ready copy and configuration form
- the starter plugins compose cleanly into deployment drafts and impact previews
- the catalog looks intentional rather than like a raw developer demo
