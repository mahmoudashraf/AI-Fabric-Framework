# LoomAI Dealership Experience Pack External Developer Guide

**Pack:** `dealership@1.0.0`
**Manifest contract:** `loomai-ai-workspace-installation-v1`

The Dealership Experience Pack turns the generic LoomAI Workspace into a
reviewed automotive-retail experience. It contributes labels, tools, page
context, and rich result presentations. Runtime discovery, authentication,
retrieval, action policy, and provider access remain outside the pack.

## 1. Architecture

```text
dealership website + one Platform script
  -> origin-scoped AI Workspace installation manifest
  -> generic LoomAI Workspace + dealership experience pack
  -> assigned deployment runtime
  -> deployment-local retrieval and governed actions
  -> approved provider API or dealership-owned service
```

Vehicle synchronization/indexing is a separate deployment-local DATA path.
Installing this pack does not copy stock or create a provider connection.

## 2. Prerequisites

Before activation, LoomAI Platform must have:

1. an active consumer owned by the dealership customer;
2. an explicit binding to an applied, verified conversational release;
3. the `dealership@1.0.0` packaged asset and valid digest catalog;
4. an approved connection profile;
5. exact website origins;
6. `dealer-vehicle` plus any approved dealership-document vector spaces;
7. installed inventory/detail actions and any advertised optional actions; and
8. runtime CORS/token readiness for direct public modes, or a ready reviewed
   broker/adapter for authenticated/private modes.

## 3. One-Script Website Integration

Copy the exact snippet from Platform **AI Workspaces**:

```html
<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="awi_pub_0123456789abcdef0123456789abcdef"
></script>
```

That is the complete baseline installation. Do not add a dealership runtime
descriptor, bundle manifest, `data-bootstrap-url`, runtime URL, or credential.

For CSP and lifecycle events, use the generic
[one-script guide](../../../max-mode-widget/docs/AI_WORKSPACE_ONE_SCRIPT_INSTALLATION_GUIDE.md).

## 4. Reviewed Public Configuration

Platform stores and validates the pack configuration. A representative shape
is:

```json
{
  "dealer": {
    "id": "dealer-123",
    "assistantLabel": "Dealer AI",
    "sourceMode": "DEALERSHIP_INVENTORY"
  },
  "page": {
    "kind": "auto",
    "rootSelector": "#main-content",
    "contextLabel": "Current dealership inventory",
    "maxChars": 1800,
    "maxPages": 3,
    "maxTotalChars": 10000
  },
  "knowledge": {
    "inventoryVectorSpace": "dealer-vehicle",
    "retrievalVectorSpaces": ["dealer-vehicle", "dealership-document"]
  },
  "capabilities": {
    "comparison": true,
    "testDrive": true,
    "callback": true
  },
  "presentation": {
    "detailBasePath": "/vehicles/",
    "imageHostAllowlist": ["images.dealer.example"],
    "detailSlugs": {
      "provider-stock-42": "vehicle-forty-two"
    }
  },
  "theme": {
    "primaryColor": "#155eef",
    "borderRadius": "0.5rem",
    "darkMode": false
  }
}
```

`detailSlugs` is the host-owned mapping from a provider's stable stock ID to a
website page slug. Use it when provider records intentionally do not contain
website routing fields. The experience pack keeps `View details` disabled for
an unmapped record instead of guessing a route.

The configuration is public. It must not contain credentials, arbitrary script
URLs, request headers, private customer data, private runtime routes, or hidden
trusted resource IDs.

`page.kind: "auto"` detects inventory/detail pages. For client-rendered detail
pages, call `LoomAIWorkspace.refresh()` after the visible title and content are
settled so the pack can re-read contextual labels and selectors.

## 5. Default Experience

The pack provides two switchable Max Mode groups:

- **Browse stock:** search stock, electric cars, family options, compare cars.
- **This vehicle:** live details, everyday use, trade-offs, location, test
  drive, callback.

Current context opens automatically when a vehicle or detail page is active.
Browse remains available without removing context. Companion mode stays compact
and does not render the Max Mode tool panel.

The pack also registers safe presentations for inventory results, vehicle
details, comparison results, and confirmed dealership request receipts. An
unknown result falls back to the generic action renderer.

## 6. Optional Host Methods

Wait for `loomai:workspace-ready` before calling pack methods:

```js
window.addEventListener("loomai:workspace-ready", () => {
  LoomAIDealershipExperience.attachVehicle({
    id: "visible-page-record-id",
    stockId: "STOCK-123",
    registrationYear: 2025,
    make: "Example",
    model: "E1",
    summary: "Visible customer-safe page summary"
  })
})
```

Optional methods are `attachVehicle`, `sendMessage`, and `destroy`. Attached
browser data is context, not authorization. Read/write actions must resolve and
revalidate stock/dealership scope server-side.

## 7. Data And Action Boundaries

- Search/detail commercial facts should come from provider/dealership read
  actions when freshness matters.
- Indexed `dealer-vehicle` evidence supports semantic discovery and grounding.
- Approved policy and operating documents belong in dealership-owned document
  sources and their configured vector spaces.
- Test-drive/callback controls express governed intents. The runtime owns
  required parameters and confirmation; the connector owns execution.
- Provider credentials and advertiser binding stay in deployment-local
  connection profiles.

## 8. Release Verification

Verify on desktop and mobile:

1. one Platform installer script and no host runtime descriptor;
2. accepted/rejected origin behavior and immutable SRI assets;
3. anonymous/authenticated/private identity behavior selected by the profile;
4. inventory search, detail, comparison, and grounded document answers;
5. contextual/default tool switching and multi-page attachments;
6. clarification, reject, confirm, receipt, and duplicate-execution safety for
   every enabled write;
7. safe media hosts and detail navigation;
8. new-conversation assignment revalidation and inaccessible-conversation
   reset; and
9. browser traces with no connector/provider/control-plane secrets.

If the workspace does not mount, inspect Platform installation readiness first.
Do not restore the retired dealership-backend descriptor path as a fallback.
