# LoomAI Dealership Experience Pack

Provider-neutral automotive-retail configuration and action-result UI for the
generic LoomAI Max Mode widget. The pack contains no Auto Trader credentials,
provider client, dealership-specific inventory, or protected backend access.

Customer and partner implementations should start with the
[External Developer Guide](docs/EXTERNAL_DEVELOPER_GUIDE.md).

Each dealership supplies:

- its public runtime descriptor URL;
- dealer identity and branding;
- the current page kind and visible context;
- installed capabilities;
- approved image hosts and detail routes; and
- optional safe host copy or tool overrides.

Normal browser traffic goes directly to the dealership's assigned LoomAI
deployment. Inventory and write operations still pass through deployment-local
actions, policy, validation, and confirmation.

## One-script bootstrap

Host a public JSON configuration endpoint, then install the pack with:

```html
<script
  src="https://cdn.example.com/dealership-experience.iife.js"
  data-bootstrap-url="https://dealer.example.com/loomai/experience.json"
  crossorigin="anonymous"
></script>
```

The bootstrap response must conform to `DealershipExperienceConfig`. It must
not contain API keys, runtime assertions, connector credentials, or provider
credentials.

## Explicit initialization

```html
<script src="https://cdn.example.com/dealership-experience.iife.js"></script>
<script>
  LoomAIDealershipExperience.mount({
    backendBaseUrl: "https://dealer.example.com",
    widget: {
      manifestUrl: "https://cdn.example.com/max-mode-widget-manifest.json"
    },
    dealer: {
      id: "dealer-123",
      assistantLabel: "Dealer AI",
      sourceMode: "DEALERSHIP_INVENTORY"
    },
    page: {
      kind: "inventory",
      rootSelector: "#main-content",
      contextLabel: "Current dealership inventory"
    },
    capabilities: {
      comparison: true,
      testDrive: true,
      callback: true
    },
    presentation: {
      detailBasePath: "/vehicles/",
      imageHostAllowlist: ["images.example.com"]
    }
  });
</script>
```

Use `attachVehicle`, `sendMessage`, and `destroy` on the exported browser API
for host-page interactions. The generic widget remains unaware of vehicles,
dealership actions, and provider payload fields.
