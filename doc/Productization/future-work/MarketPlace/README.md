# Marketplace Planning Docs

Status: implementation index with inference-profile support shipped (2026-04-15)

Current supported public marketplace plugin types:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

Current state:

- marketplace runtime/framework support baseline is implemented for runtime-backed contracts
- marketplace control-plane baseline is implemented for:
  - catalog and install records
  - template plugins
  - action plugins
  - data plugins
  - data-plugin dataset lifecycle:
    - apply-time seeding or sync
    - plugin-scoped tenant-shared dataset handles
    - SQL and folder-backed sync modes
    - differential cleanup and scheduled external resync
  - inference-profile plugins that compile into deployment `providerConfig`
  - billing and entitlements
  - first external publisher workflow
- unsupported public surfaces have been removed from the active marketplace baseline
- remaining marketplace work is now:
  - payout and revenue-share business workflow
  - broader public data-plugin and open-ecosystem expansion

Required interpretation:

- public marketplace plugin types currently supported are `TEMPLATE`, `ACTION`, `DATA`, and `INFERENCE_PROFILE`
- marketplace remains a control-plane composition layer, not a runtime plugin-loading system
- installs compile into deployment drafts and published versions
- publish and apply remain required before live behavior changes
- shell and runtime do not load arbitrary third-party code
- platform should only productize marketplace features that have a runtime-backed contract

This folder contains both marketplace vision documents and stricter implementation-baseline documents.

If two documents differ, the implementation-baseline documents should win.

---

## 1) Vision Documents

These are useful for product direction and UX framing:

- `doc/Productization/MARKETPLACE_HIGH_LEVEL_DESIGN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_DESIGN.md`

These documents are broader and more conceptual.
They should not override stricter platform, auth, release-governance, or runtime-support boundaries.

---

## 2) Implementation-Baseline Documents

These documents should be treated as the current implementation baseline:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_INFERENCE_PROFILE_PRODUCTIZATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_DATA_PLUGIN_DATASET_PRODUCTIZATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_DEFAULT_STARTER_CATALOG_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_RUNTIME_AND_FRAMEWORK_SUPPORT_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_RUNTIME_AND_FRAMEWORK_SUPPORT_CHECKLIST.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/CONFIG_DRIVEN_MARKETPLACE_VS_MICROFRONTEND_PLUGIN_ARCHITECTURE_PLAN.md`

---

## 3) Recommended Reading Order

Recommended sequence:

1. `doc/Productization/MARKETPLACE_HIGH_LEVEL_DESIGN.md`
2. `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE.md`
3. `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_DESIGN.md`
4. `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
5. `doc/Productization/future-work/MarketPlace/MARKETPLACE_INFERENCE_PROFILE_PRODUCTIZATION_PLAN.md`
6. `doc/Productization/future-work/MarketPlace/MARKETPLACE_DEFAULT_STARTER_CATALOG_PLAN.md`
7. `doc/Productization/future-work/MarketPlace/MARKETPLACE_DATA_PLUGIN_DATASET_PRODUCTIZATION_PLAN.md`
8. `doc/Productization/future-work/MarketPlace/MARKETPLACE_RUNTIME_AND_FRAMEWORK_SUPPORT_IMPLEMENTATION_PLAN.md`
9. `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
10. `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`
11. `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
12. `doc/Productization/future-work/MarketPlace/CONFIG_DRIVEN_MARKETPLACE_VS_MICROFRONTEND_PLUGIN_ARCHITECTURE_PLAN.md`

---

## 4) Conflict Rule

If a conceptual marketplace or shell document conflicts with:

- deployment draft and version governance
- publish and apply behavior
- secret boundaries
- auth sequencing
- tenant and customer isolation
- runtime-supported marketplace capability boundaries

follow the implementation-baseline documents and the underlying platform and auth plans.
