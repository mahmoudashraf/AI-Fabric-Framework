# Marketplace Planning Docs

Status: documentation index (2026-04-14)

Current state:

- marketplace runtime/framework support baseline is implemented
- marketplace control-plane baseline is implemented through:
  - Phase 0 catalog and install-record foundation
  - Phase 1 template plugins
  - Phase 2 action plugins
  - Phase 3B automation plugins
  - Phase 3 data plugins
  - Phase 4 billing and entitlements
  - Phase 5 first external publisher workflow
- remaining marketplace work is now:
  - payout and revenue-share business workflow
  - broader public data-plugin and open-ecosystem expansion
  - deeper capability-profile expansion for `SURFACE`, `POLICY_LOGIC`, and `ANALYTICS_EVENT`

Default-mode plugin taxonomy:

- current shipped first-class plugin types:
  - `TEMPLATE`
  - `ACTION`
  - `DATA`
  - `AUTOMATION`
- default-mode capability families that should remain capability profiles, not separate arbitrary-code plugin types:
  - `SURFACE`
  - `POLICY_LOGIC`
  - `ANALYTICS_EVENT`

Recommended interpretation:

- `TEMPLATE`, `ACTION`, `DATA`, and `AUTOMATION` are the public marketplace plugin types operators browse, install, and govern directly
- `SURFACE`, `POLICY_LOGIC`, and `ANALYTICS_EVENT` describe bounded contribution capabilities layered onto those public types
- this matches the real-world extension mix seen in ecosystems such as Shopify:
  - multiple UI surfaces
  - workflow automation
  - server-side logic
  - analytics and event capture
- but preserves this platform boundary:
  - no arbitrary runtime code loading
  - no arbitrary frontend code loading
  - no bypass of draft -> publish -> apply

This folder contains both marketplace vision documents and marketplace implementation-baseline documents.

They are not all at the same level of specificity.

If two documents differ, the implementation-baseline documents should win over the higher-level vision documents.

---

## 1) Vision Documents

These are useful for product direction, positioning, and UX framing:

- `doc/Productization/MARKETPLACE_HIGH_LEVEL_DESIGN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_DESIGN.md`

These documents are intentionally broader and more conceptual.
They should not be treated as the final implementation boundary when they conflict with stricter platform, auth, or release-governance rules.

---

## 2) Implementation-Baseline Documents

These documents should be treated as the current safer implementation baseline:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_DEFAULT_STARTER_CATALOG_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_RUNTIME_AND_FRAMEWORK_SUPPORT_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_RUNTIME_AND_FRAMEWORK_SUPPORT_CHECKLIST.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/CONFIG_DRIVEN_MARKETPLACE_VS_MICROFRONTEND_PLUGIN_ARCHITECTURE_PLAN.md`

These documents make the following boundaries explicit:

- marketplace is a control-plane composition layer, not a runtime plugin-loading system
- installs compile into deployment drafts and published versions
- publish and apply remain required before live behavior changes
- plugin versions are pinned and operator-governed
- shell and runtime do not load arbitrary third-party code
- auth must align with the shared auth foundation

---

## 3) Recommended Reading Order

Recommended sequence:

1. `doc/Productization/MARKETPLACE_HIGH_LEVEL_DESIGN.md`
2. `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE.md`
3. `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_DESIGN.md`
4. `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
5. `doc/Productization/future-work/MarketPlace/MARKETPLACE_DEFAULT_STARTER_CATALOG_PLAN.md`
6. `doc/Productization/future-work/MarketPlace/MARKETPLACE_RUNTIME_AND_FRAMEWORK_SUPPORT_IMPLEMENTATION_PLAN.md`
7. `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
8. `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`
9. `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
10. `doc/Productization/future-work/MarketPlace/CONFIG_DRIVEN_MARKETPLACE_VS_MICROFRONTEND_PLUGIN_ARCHITECTURE_PLAN.md`

---

## 4) Conflict Rule

If a conceptual marketplace or shell document conflicts with:

- deployment draft and version governance
- publish and apply behavior
- secret boundaries
- auth sequencing
- tenant and customer isolation

follow the implementation-baseline documents and the underlying platform and auth plans.
