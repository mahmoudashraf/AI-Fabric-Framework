# Marketplace Default Starter Catalog Plan

Status: implementation-oriented seed plan (2026-04-15)

This document defines the default first-party plugins that should be available in a new marketplace-enabled platform installation.

It is a product seed plan for the supported public marketplace types only:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

Unsupported public surfaces that are not backed by runtime contracts are intentionally excluded.

---

## 1) Goals

The starter catalog should:

- make the marketplace useful on day 1
- demonstrate every supported public plugin type
- show the supported pricing models:
  - `FREE`
  - `ONE_OFF`
  - `SUBSCRIPTION`
- cover more than one vertical
- stay small and opinionated

---

## 2) Recommended Default Starter Set

Recommended visible business-capability starter set:

1. `mkp-template-commerce-shell`
2. `mkp-action-shopify-admin`
3. `mkp-data-commerce-catalog`
4. `mkp-template-support-desk-shell`
5. `mkp-data-help-center`
6. `mkp-action-notifications`

Why this set:

- one strong commerce path
- one strong support path
- one cross-domain utility path
- real examples for all supported public plugin types

Recommended operator-facing inference starter set:

7. `mkp-inference-local-embeddings`
8. `mkp-inference-optimized-orchestration`
9. `mkp-inference-premium-hybrid`
10. `mkp-inference-byok-openai`

Why keep inference separate in presentation:

- they are real supported public plugin types
- they are deployment capability offers, not business-domain add-ons
- operators should see them, but they should not drown out the business-facing catalog

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

### 3.2 `mkp-action-shopify-admin`

- display name: `Shopify Admin Actions`
- type: `ACTION`
- pricing:
  - `ONE_OFF`
  - default amount: `49 USD`
- status:
  - already seeded
- purpose:
  - add real commerce/admin actions to a deployment
- baseline contributions:
  - `shopify-order-read`
  - `shopify-order-cancel`
- install form:
  - `store` text
  - `apiKey` secret ref

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
  - dataset mode:
    - plugin-owned logical dataset boundary
    - tenant-shared reuse for installs of the same plugin
- install form:
  - `scope` select:
    - `refund-policy`
    - `catalog`
    - `all`

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
    - `support`
  - default conversation mode:
    - `guided-support`
  - recommended plugin ids:
    - `mkp-data-help-center`
    - `mkp-action-notifications`

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
  - supported sync modes:
    - SQL sync connector
    - folder-of-files sync connector
  - shell modules:
    - `docs`
    - `ai-search`
    - `support`
- install form:
  - `scope` select:
    - `faq`
    - `policy`
    - `all`

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
- install form:
  - `provider` select:
    - `sendgrid`
    - `twilio`
    - `slack`
  - `credentialSecretRef` secret ref
  - `defaultSender` text optional

### 3.7 `mkp-inference-local-embeddings`

- display name: `Local Embeddings Profile`
- type: `INFERENCE_PROFILE`
- pricing:
  - `FREE`
- status:
  - already seeded
- purpose:
  - provide a zero-external-cost embedding baseline through the bundled ONNX endpoint profile
- baseline contributions:
  - `embedding.provider = onnx`
  - `embedding.endpointProfileRef = onnx-bundled`
  - `embedding.modelAlias = bge-small-en-v1.5`

### 3.8 `mkp-inference-optimized-orchestration`

- display name: `Optimized Orchestration Profile`
- type: `INFERENCE_PROFILE`
- pricing:
  - `SUBSCRIPTION`
  - default amount: `19 USD / month`
  - default trial: `7 days`
- status:
  - already seeded
- purpose:
  - use a managed orchestration/generation split with ONNX embeddings
- baseline contributions:
  - orchestration:
    - `provider = openai`
    - `endpointProfileRef = openai-cloud-orchestration`
    - `model = gpt-4.1-mini`
  - generation:
    - `provider = openai`
    - `endpointProfileRef = openai-cloud-default`
    - `model = gpt-4.1-mini`
  - embedding:
    - `provider = onnx`
    - `endpointProfileRef = onnx-bundled`

### 3.9 `mkp-inference-premium-hybrid`

- display name: `Premium Hybrid Response Profile`
- type: `INFERENCE_PROFILE`
- pricing:
  - `SUBSCRIPTION`
  - default amount: `49 USD / month`
  - default trial: `7 days`
- status:
  - already seeded
- purpose:
  - upgrade generation and embeddings to premium managed endpoints
- baseline contributions:
  - orchestration:
    - `provider = openai`
    - `endpointProfileRef = openai-cloud-orchestration`
  - generation:
    - `provider = openai`
    - `endpointProfileRef = openai-cloud-premium`
    - `model = gpt-4.1`
  - embedding:
    - `provider = openai`
    - `endpointProfileRef = openai-cloud-default`
    - `model = text-embedding-3-small`

### 3.10 `mkp-inference-byok-openai`

- display name: `Bring Your Own OpenAI Profile`
- type: `INFERENCE_PROFILE`
- pricing:
  - `FREE`
- status:
  - already seeded
- purpose:
  - let the operator bind their own OpenAI-compatible endpoint and key into deployment `providerConfig`
- baseline contributions:
  - generation:
    - `provider = openai`
    - `baseUrl` from install form
    - `apiKeySecretRef` from install form
    - `model` from install form or default
  - embedding:
    - `provider = openai`
    - `apiKeySecretRef` from install form
    - `model = text-embedding-3-small`
- install form:
  - `baseUrl` text
  - `apiKey` secret ref
  - `generationModel` text
  - `embeddingModel` text

---

## 4) Pricing Defaults

Recommended starter-catalog pricing defaults:

- `TEMPLATE`
  - default: `FREE`
- `ACTION`
  - default:
    - `ONE_OFF` when the operator supplies their own external credentials
  - exception:
    - `SUBSCRIPTION` only when Loom or the publisher absorbs recurring proxy or infrastructure cost
- `DATA`
  - default:
    - `SUBSCRIPTION` for Loom-maintained premium shared datasets
    - `FREE` for foundational public or low-cost shared datasets
- `INFERENCE_PROFILE`
  - default:
    - `FREE` for bundled or bring-your-own profiles
    - `SUBSCRIPTION` for Loom-managed optimized or premium inference profiles

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
  - `mkp-inference-optimized-orchestration`
  - `mkp-inference-premium-hybrid`

---

## 5) Why These Examples

This set demonstrates the product clearly:

- template plugins:
  - deployment bootstrap and shell defaults
- action plugins:
  - external read and write integrations
- data plugins:
  - shared retrieval with attribution
  - real dataset lifecycle and sync responsibility
- inference-profile plugins:
  - deployment provider composition
  - purpose-specific endpoint-profile selection
  - managed vs customer-supplied credential models
- multi-plugin composition:
  - templates recommend action and data add-ons
- cross-domain utility:
  - notifications are useful beyond commerce

This keeps the marketplace inside the platform boundary:

- no arbitrary frontend code
- no arbitrary runtime code
- no unsupported public automation engine
- no bypass of `draft -> publish -> apply`

---

## 6) Seed Order

Recommended implementation order:

### Seed A: Current shipped baseline

- `mkp-template-commerce-shell`
- `mkp-action-shopify-admin`
- `mkp-data-commerce-catalog`

### Seed B: Immediate first-party defaults

- `mkp-template-support-desk-shell`
- `mkp-data-help-center`
- `mkp-action-notifications`

Seed B is now implemented in the catalog seed set.

### Seed C: Inference profile baseline

- `mkp-inference-local-embeddings`
- `mkp-inference-optimized-orchestration`
- `mkp-inference-premium-hybrid`
- `mkp-inference-byok-openai`

Seed C is now implemented in the catalog seed set.

---

## 7) Real Backing And Rollout Dependencies

Default starter plugins should not be demo-only metadata.

Required rollout rule:

- every default starter plugin that depends on backing infrastructure must have that infrastructure represented in the canonical rollout path

For starter data plugins, this means:

- the plugin remains a business-facing `DATA` plugin
- the backing corpus lives on a real shared vector backend
- the canonical marketplace rollout provisions that shared-storage-capable backend as part of rollout instead of assuming an operator created it manually

Recommended platform stance:

- use a rollout-owned shared-storage-capable vector backend for the canonical marketplace deployment
- use hosted verification to seed or refresh a small verification corpus when the platform is fresh
- keep the catalog business-facing; do not expose the vector provider itself as the marketplace product

Concrete implication for the default data plugins:

- `mkp-data-commerce-catalog`
  - should resolve into a real shared index or shared vector handle
  - should be validated against a rollout-owned shared backend
- `mkp-data-help-center`
  - should follow the same model with a real seeded starter corpus

This makes a new platform installation reproducible:

- platform admin can recreate the canonical marketplace rollout
- the rollout provisions the backing vector installation needed for the plugin proof
- hosted verification then proves the shared data plugin end to end

---

## 8) Suggested Manifest Shapes

These are compact examples, not final migration payloads.

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
  contributesShellPresentation: true
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
        - support
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
  contributesShellPresentation: true
  requiresSharedDatasetAccess: true
installForm:
  - id: scope
    type: select
    required: true
    options:
      - faq
      - policy
      - all
contributions:
  datasets:
    - datasetId: help-center-seed
      entityType: faq-article
      storageScope: PLUGIN_SCOPED
      sharingScope: TENANT_SHARED
      ingestionMode: PACKAGED_SEED
      updateStrategy: UPSERT_BY_ID
      seedDatasetRef: classpath:marketplace/datasets/help-center/help-center.jsonl
  knowledgeSources:
    - sourceType: shared-index
      sourceKey: help-center
      datasetRef: help-center-seed
      attributionLabel: Help center marketplace data
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
  contributesShellPresentation: true
  requiresExternalHttpExecution: true
  requiresDeploymentSecrets: true
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
```
