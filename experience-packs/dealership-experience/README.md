# LoomAI Dealership Experience Pack

Provider-neutral automotive-retail presentation and interaction configuration
for the generic LoomAI Workspace. The pack contains no provider client,
provider credential, fixed dealership inventory, or protected backend access.

Start with the [external developer guide](docs/EXTERNAL_DEVELOPER_GUIDE.md).

## Installation

Create and activate a `dealership@1.1.0` AI Workspace installation in LoomAI
Platform, then add the generated script to the dealership website:

```html
<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="awi_pub_0123456789abcdef0123456789abcdef"
></script>
```

Platform resolves the installation's current verified deployment, exact
origin, connection profile, immutable widget/pack assets, and public pack
configuration. The dealership website does not publish a runtime descriptor or
write workspace bootstrap JavaScript.

Each installation configures public values such as dealer label, page-content
rules, tool groups, capabilities, approved media hosts, detail links, and
theme. Runtime, connector, provider, and signing credentials stay server-side.

Normal public-mode conversation traffic goes directly to the dealership's
assigned deployment. Inventory reads and writes still pass through
deployment-local retrieval/actions, policy, validation, and confirmation.

## Optional Host API

After `loomai:workspace-ready`, the pack exposes:

- `LoomAIDealershipExperience.attachVehicle(vehicle)`
- `LoomAIDealershipExperience.sendMessage(message, requestContext)`
- `LoomAIDealershipExperience.destroy()`

These methods improve a richer dealership host but are not required for the
script-only baseline. Browser-derived IDs remain untrusted hints; the
deployment or connector must resolve protected targets before execution.

The generic widget remains unaware of vehicles, dealerships, provider payloads,
and dealership action names.
