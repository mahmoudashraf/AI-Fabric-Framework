# LoomAI Dealership Experience Pack External Developer Guide

**Applies to:** `@loom-ai-labs/dealership-experience-pack` 1.0.0

**Audience:** dealership website developers, automotive implementation
partners, and LoomAI deployment operators.

The Dealership Experience Pack configures the generic LoomAI Max Mode chat
application for automotive retail. It supplies reviewed dealership tools,
vehicle context helpers, and rich inventory, detail, and comparison result
renderers without putting dealership logic into the generic chat application.

Read `docs/EXTERNAL_DEVELOPER_GUIDE.md` from the reviewed
`@loom-ai-labs/max-mode-widget` package before changing authentication,
runtime routes, page attachments, or generic widget behavior.

## 1. What the Pack Owns

The pack owns:

- `Browse stock` and `This vehicle` tool defaults;
- capability-aware comparison, test-drive, and callback controls;
- runtime descriptor discovery from the dealership backend;
- public anonymous Max Mode initialization;
- vehicle context attachments;
- bounded action-result projections;
- inventory, vehicle-detail, and comparison custom elements; and
- safe follow-up commands that continue through the assigned deployment.

The pack does not own:

- the dealership website or its normal inventory pages;
- provider API credentials or an Auto Trader client;
- source data synchronization or indexing;
- runtime, connector, or deployment provisioning;
- customer identity, server-side authorization, or consent records;
- test-drive or callback persistence; or
- the dealership system of record.

The pack is provider-neutral. Auto Trader, another stock provider, or a
dealership-owned backend may supply the data as long as the assigned deployment
exposes the reviewed action contracts described below.

## 2. Request Architecture

The customer-facing path is:

```text
dealership page
  -> dealership experience pack
  -> generic Max Mode chat application
  -> assigned deployment runtime
  -> deployment-local action connector
  -> provider API or dealership backend
```

The browser receives only public configuration and public runtime routes. It
does not receive connector credentials, provider credentials, private runtime
assertions, or backend service keys.

Vehicle indexing follows a separate deployment-local data path. Installing the
pack does not create a data plugin, synchronize stock, or index a vehicle.

## 3. Expected Integration Effort

When the assigned deployment and actions already satisfy this guide:

| Work | Typical effort |
| --- | --- |
| Script and bootstrap installation | 2 to 4 hours |
| Branding, page roots, routes, media allowlist, and capability selection | 0.5 to 1 day |
| Browser, mobile, action, confirmation, and production checks | 1 to 2 days |

Creating a new deployment, provider DATA integration, indexing flow, action
connector mappings, authorization rules, and write persistence is separate
platform onboarding work. Do not hide that work behind the UI estimate.

## 4. Prerequisites

Obtain all of the following before enabling the pack:

1. A reviewed, content-addressed dealership pack IIFE and integrity value.
2. A reviewed Max Mode widget manifest.
3. A dealership backend endpoint that publishes the runtime descriptor.
4. An assigned deployment with public anonymous chat enabled.
5. A deployment vector space containing this dealership's approved vehicle
   evidence.
6. Installed and verified read actions for inventory and vehicle details.
7. Any optional compare or write actions advertised by capability flags.
8. Browser-origin, CORS, CSP, and media-host approval.

The source package is currently private and `UNLICENSED`. Do not assume the npm
name is publicly installable. Use the reviewed IIFE or ESM artifact supplied by
LoomAI for the customer.

For customers approved to use LoomAI-hosted browser assets, current release
metadata is available at:

```text
https://loomai.pro/vendor/dealership-experience-manifest.json
https://loomai.pro/vendor/max-mode-widget-manifest.json
```

Resolve and verify the pack manifest during a reviewed rollout, then pin its
content-addressed script and SRI. The pack may consume the widget manifest at
runtime because it validates the schema, content-addressed filename, and
SHA-256 before loading that layer.

The expected pack manifest shape is:

```json
{
  "schemaVersion": "loomai-dealership-experience-bundle-v1",
  "file": "dealership-experience.<sha16>.iife.js",
  "sha256": "<64-lowercase-hex-characters>"
}
```

## 5. One-Script Installation

### 5.1 Add the reviewed script

```html
<script
  src="https://assets.example.com/loomai/dealership-experience.<sha16>.iife.js"
  integrity="sha256-<base64-sha256>"
  crossorigin="anonymous"
  data-bootstrap-url="https://dealer.example.com/loomai/dealership-experience.json"
></script>
```

The pack fetches the bootstrap JSON, fetches the dealership runtime descriptor,
loads the exact Max Mode bundle selected by its manifest, verifies that bundle
with SRI, and mounts the Companion.

Pin the content-addressed pack script during a reviewed rollout. Do not replace
it silently through a mutable CDN URL.

Automatic mounting is asynchronous and is best for configuration-only hosts.
Use explicit initialization when page code must call `attachVehicle` or
`sendMessage` during startup, and wait for the returned mount promise first.
An automatic-mount failure dispatches
`loomai:dealership-experience-error` on `window`.

### 5.2 Publish secret-free bootstrap JSON

`GET /loomai/dealership-experience.json`:

```json
{
  "backendBaseUrl": "https://dealer.example.com",
  "runtimeDescriptorPath": "/api/public/runtime-descriptor",
  "widget": {
    "manifestUrl": "https://assets.example.com/loomai/max-mode-widget-manifest.json"
  },
  "dealer": {
    "id": "dealer-123",
    "assistantLabel": "Dealer AI",
    "sourceMode": "DEALERSHIP_INVENTORY"
  },
  "page": {
    "kind": "inventory",
    "rootSelector": "#main-content",
    "contextLabel": "Current dealership inventory",
    "maxChars": 4000,
    "maxPages": 3,
    "maxTotalChars": 10000
  },
  "capabilities": {
    "comparison": true,
    "testDrive": true,
    "callback": true
  },
  "copy": {
    "welcomeMessage": "I can search and compare current stock using approved dealership facts.",
    "placeholder": "Ask about a vehicle, feature, budget or comparison...",
    "emptyMessage": "Ask about current vehicles or start an available dealership request.",
    "companionModeLabel": "Vehicle assistant",
    "starterSuggestions": [
      "Compare electric cars",
      "What is under GBP 30,000?",
      "Which car has the best luggage space?"
    ]
  },
  "presentation": {
    "detailBasePath": "/vehicles/",
    "imageHostAllowlist": [
      "images.dealer.example.com",
      "m.atcdn.co.uk"
    ]
  },
  "theme": {
    "primaryColor": "#155eef",
    "borderRadius": "0.5rem",
    "fontFamily": "Inter, system-ui, sans-serif",
    "darkMode": "auto"
  }
}
```

This response is public configuration. It must never contain:

- connector or provider API keys;
- runtime assertions or signing keys;
- deployment administration credentials;
- private backend headers;
- customer records; or
- hidden trusted resource identifiers.

Use one bootstrap response per page kind when inventory and detail pages need
different page roots, subject labels, or copy. A server-rendered endpoint may
safely derive those public values from the current route.

## 6. Explicit Initialization

Use explicit initialization when the host needs callbacks or constructs config
in application code:

```html
<script
  src="/assets/loomai/dealership-experience.<sha16>.iife.js"
  integrity="sha256-<base64-sha256>"
></script>
<script>
  (async function () {
    const controller = await LoomAIDealershipExperience.mount({
      backendBaseUrl: "https://dealer.example.com",
      runtimeDescriptorPath: "/api/public/runtime-descriptor",
      widget: {
        manifestUrl: "/assets/loomai/max-mode-widget-manifest.json"
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
        testDrive: false,
        callback: true
      },
      presentation: {
        detailBasePath: "/vehicles/",
        imageHostAllowlist: ["images.dealer.example.com"]
      },
      onRuntimeState(state, title, detail) {
        console.info("LoomAI runtime", { state, title, detail });
      },
      onEvent(event) {
        applicationTelemetry.record("loomai_dealership_event", {
          type: event.type,
          timestamp: event.timestamp
        });
      }
    });

    window.dealershipAssistant = controller;
  })();
</script>
```

Do not call `MaxMode.init()` separately. The pack owns generic widget
initialization and cleanup for this page.

## 7. Runtime Descriptor Contract

The pack requests the descriptor from:

```text
{backendBaseUrl}{runtimeDescriptorPath}
```

The default path is `/api/public/runtime-descriptor`. The endpoint must return
public routing metadata only:

```json
{
  "success": true,
  "ready": true,
  "integrationMode": "public-runtime-anonymous",
  "chatBaseUrl": "https://deployment.example.com",
  "inventoryVectorSpace": "dealer-vehicle",
  "retrievalVectorSpaces": ["dealer-vehicle", "document"],
  "runtimeRoutes": {
    "bootstrapUrl": "/api/public/chat/session",
    "renewUrl": "/api/public/chat/session/renew",
    "queryUrl": "/api/chat/me/query",
    "suggestionsUrl": "/api/chat/me/suggestions",
    "authContextUrl": "/api/chat/me/auth-context",
    "shellConfigUrl": "/api/chat/me/shell-config",
    "conversationsUrl": "/api/chat/me/conversations",
    "conversationItemUrlTemplate": "/api/chat/me/conversations/{conversationId}"
  }
}
```

Validation is fail closed:

- `success` and `ready` must both be `true`;
- `integrationMode` must be `public-runtime-anonymous`;
- `chatBaseUrl`, `inventoryVectorSpace`, at least one
  `retrievalVectorSpaces` entry, and every route must be present; and
- all URLs must use HTTP or HTTPS.

The browser must not discover the runtime through the central Platform on each
request. The dealership backend publishes its already assigned deployment.
`dealer.id` is browser-visible request context, not an authorization boundary.
`inventoryVectorSpace` scopes trusted vehicle attachments. The pack sends the
complete `retrievalVectorSpaces` list as a preference so an informational turn
can retrieve both inventory and approved dealership documents without pinning
the whole conversation to one entity type.
The runtime and connector must derive the permitted dealership account from
their trusted deployment and protected-resource bindings.

## 8. Page Configuration

### 8.1 Inventory page

```json
{
  "kind": "inventory",
  "rootSelector": "#inventory-main",
  "contextLabel": "Current dealership inventory"
}
```

The pack starts in `Browse stock`. `This vehicle` remains visible but disabled
until a vehicle is attached. These scoped tools render in Max Mode; the compact
Companion dock remains conversation-only.

### 8.2 Vehicle detail page

```json
{
  "kind": "vehicle-detail",
  "rootSelector": "#vehicle-main",
  "contextLabel": "Viewing 2025 Example E1",
  "subjectLabel": "2025 Example E1"
}
```

The pack starts in `This vehicle`, keeps that group available from trusted page
context, and still lets the user switch to `Browse stock` in Max Mode. The
Companion dock retains the same vehicle context without rendering either tool
group.

Choose `rootSelector` carefully. Current-page capture uses text from this root.
Do not include account menus, finance applications, hidden forms, analytics
payloads, or private customer details.

## 9. Host Page Integration

The dealership site remains free to render its normal inventory and vehicle
pages from its own backend. It can add a displayed vehicle to chat context:

```js
LoomAIDealershipExperience.attachVehicle({
  id: "vehicle-public-123",
  stockId: "stock-123",
  make: "Example",
  model: "E1",
  derivative: "Long Range",
  registrationYear: 2025,
  priceGbp: 31995,
  mileage: 8400,
  fuelType: "Electric",
  bodyType: "SUV",
  lifecycleState: "FORECOURT",
  sourceLabel: "Current dealership stock",
  sourceUpdatedAt: "2026-10-04T10:00:00Z"
});
```

It can send a reviewed command:

```js
LoomAIDealershipExperience.sendMessage(
  "Explain the important trade-offs for this vehicle using current dealership facts.",
  { pageIntent: "vehicle-tradeoffs" }
);
```

These calls send context to the assigned deployment. They do not authorize or
execute a dealership write directly.

Browser API:

| Method | Purpose |
| --- | --- |
| `mount(config)` | Validate config, discover the runtime, and initialize Max Mode |
| `attachVehicle(vehicle)` | Add bounded vehicle context and select `This vehicle` |
| `sendMessage(message, context?)` | Send an executor/search chat request |
| `destroy()` | Destroy the pack-owned widget instance |

## 10. Default Tools

### Browse stock

- Search stock
- Electric cars
- Family options
- Compare cars, when `comparison` is enabled

### This vehicle

- Live details
- Everyday use
- Trade-offs
- Location
- Test drive, when `testDrive` is enabled
- Callback, when `callback` is enabled

Capability flags control both visible tools and rich-result CTAs. Enable a flag
only when the matching deployment action and backend workflow are installed,
authorized, and verified.

Use `toolGroups` to replace these defaults. Tool overrides remain prompts sent
through chat; they are not direct API calls.

### Mobile Max Mode rail

The default dealership rail is injected by this experience pack, not by the
generic widget:

| Rail item | Generic command | Result |
| --- | --- | --- |
| Stock | `open-tools`, `default` | Opens Browse stock tools |
| Vehicle | `open-tools`, `contextual` | Opens This vehicle tools; disabled without context |
| Sources | `open-documents` | Opens retrieved evidence; hidden until evidence exists |

Override `toolRail` with up to six JSON-safe items when needed. A configured
`prompt` item still sends a normal chat request and does not bypass action
authorization or confirmation. Do not put provider credentials, direct dealer
API calls, or browser-authorized writes in rail configuration.

## 11. Default Action Names

| Pack capability | Default action | Access |
| --- | --- | --- |
| Inventory search | `dealership_search_inventory` | Read |
| Vehicle detail | `dealership_get_vehicle` | Read |
| Vehicle comparison | `dealership_compare_vehicles` | Read, optional |
| Callback request | `dealership_request_callback` | Confirmed write, optional |
| Test-drive request | `dealership_request_test_drive` | Confirmed write, optional |

Override names when a reviewed deployment uses different stable codes:

```json
{
  "presentation": {
    "actionNames": {
      "searchInventory": "customer_search_stock",
      "getVehicle": "customer_get_stock_item",
      "compareVehicles": "customer_compare_stock"
    }
  }
}
```

The override changes presentation mapping. The deployment action catalog and
selection prompt must independently advertise the same names.

## 12. Rich Result Contracts

The pack never hands an unbounded raw action payload to a renderer. It maps an
exact action name and schema version to allowlisted fields.

### 12.1 Inventory list: `loomai.vehicle-list.v1`

Minimum action result shape:

```json
{
  "total": 1,
  "dataNotice": "Current stock returned by the approved provider integration.",
  "source": {
    "label": "Approved stock source",
    "refreshedAt": "2026-10-04T10:00:00Z"
  },
  "appliedFilters": {
    "make": "Example",
    "fuelType": "Electric",
    "maxPriceGbp": 35000
  },
  "results": [
    {
      "stockId": "stock-123",
      "lifecycleState": "FORECOURT",
      "lastUpdated": "2026-10-04T10:00:00Z",
      "vehicle": {
        "make": "Example",
        "model": "E1",
        "derivative": "Long Range",
        "yearOfManufacture": 2025,
        "odometerReadingMiles": 8400,
        "fuelType": "Electric",
        "transmissionType": "Automatic",
        "bodyType": "SUV"
      },
      "adverts": {
        "retailAdverts": {
          "totalPrice": { "amountGBP": 31995 }
        }
      },
      "features": [
        { "name": "Adaptive cruise control", "type": "Standard" }
      ],
      "media": {
        "images": [
          {
            "imageId": "image-1",
            "href": "https://images.dealer.example.com/stock-123.webp"
          }
        ]
      }
    }
  ]
}
```

The pack projects at most 12 inventory items for rich rendering.

### 12.2 Vehicle detail: `loomai.vehicle-detail.v1`

Return one provider-shaped record under `vehicleRecord` plus optional source
and notice fields:

```json
{
  "vehicleRecord": {
    "stockId": "stock-123",
    "lifecycleState": "FORECOURT",
    "lastUpdated": "2026-10-04T10:00:00Z",
    "vehicle": {
      "make": "Example",
      "model": "E1",
      "derivative": "Long Range",
      "yearOfManufacture": 2025,
      "odometerReadingMiles": 8400,
      "fuelType": "Electric",
      "transmissionType": "Automatic",
      "bodyType": "SUV"
    },
    "adverts": {
      "retailAdverts": {
        "totalPrice": { "amountGBP": 31995 }
      }
    },
    "features": [],
    "media": { "images": [] }
  },
  "source": {
    "label": "Approved stock source",
    "refreshedAt": "2026-10-04T10:00:00Z"
  },
  "dataNotice": "Current provider stock record."
}
```

### 12.3 Vehicle comparison: `loomai.vehicle-comparison.v1`

Return two to four normalized public vehicle records under `_items`:

```json
{
  "_count": 2,
  "_items": [
    {
      "id": "vehicle-public-123",
      "stockId": "stock-123",
      "slug": "example-e1-long-range",
      "make": "Example",
      "model": "E1",
      "derivative": "Long Range",
      "registrationYear": 2025,
      "priceGbp": 31995,
      "priceFormatted": "GBP 31,995",
      "mileage": 8400,
      "fuelType": "Electric",
      "transmission": "Automatic",
      "bodyType": "SUV",
      "location": "Example Riverside",
      "lifecycleState": "FORECOURT",
      "summary": "Current approved public stock summary.",
      "features": [],
      "imagePath": "https://images.dealer.example.com/stock-123.webp",
      "sourceLabel": "Approved stock source",
      "sourceUpdatedAt": "2026-10-04T10:00:00Z"
    }
  ],
  "source": {
    "label": "Approved stock source",
    "refreshedAt": "2026-10-04T10:00:00Z"
  }
}
```

The connector and runtime may carry additional internal fields, but the pack
does not project them into its custom elements.

### 12.4 Confirmed request receipt: `loomai.dealership-request-receipt.v1`

When `capabilities.testDrive` or `capabilities.callback` is enabled, the pack
maps the matching confirmed write action to a compact request receipt. The
projection accepts the safe receipt fields either directly in the normalized
action result or under a standard nested `data` envelope:

```json
{
  "data": {
    "receiptCode": "DEALER-REQUEST-001",
    "actionType": "dealership_request_test_drive",
    "status": "NEW",
    "createdAt": "2026-10-05T10:35:14Z",
    "vehicle": "2025 Example E1",
    "message": "Your request is in the dealership review inbox."
  },
  "message": "Your request is in the dealership review inbox.",
  "success": true
}
```

Only `receiptCode`, `actionType`, `status`, `createdAt`, `vehicle`, `message`,
and `success` are projected. The renderer does not receive contact details,
provider credentials, internal targets, or the unbounded connector payload.
If the result does not match this reviewed mapping, the generic widget fallback
still renders a responsive bounded representation.

## 13. Write Actions

The Test drive and Callback buttons do not call a dealership endpoint. They
send a conversational request through the runtime. The runtime must:

1. Resolve the current vehicle to a trusted active target.
2. Collect only the required customer-owned parameters.
3. Apply action and resource authorization.
4. Present the final confirmation when policy requires it.
5. Execute through the private connector using idempotency.
6. Persist the request in the dealership-owned system of record.
7. Return a safe receipt.

Recommended callback fields are vehicle, name, phone, optional email, optional
message, and explicit consent. Recommended test-drive fields are vehicle, name,
email, phone, optional preferred date, and optional message. Customer contact
details are sensitive and must not be written into browser bootstrap config,
telemetry, or indexed vehicle evidence.

If the customer has not installed a verified write flow, set the corresponding
capability to `false`.

## 14. Media and Navigation

Set `presentation.imageHostAllowlist` to the exact reviewed media hosts. An
action result image outside that list is not trusted for rich rendering.

`presentation.detailBasePath` supplies the customer route prefix used by
vehicle links. `detailSlugs` and `imageFallbacks` are optional host-owned maps
for bounded migrations or demonstrations; they are not provider lookup logic.
Production integrations should return stable public stock references and media
URLs from their approved backend contracts.

The dealership site owns navigation and normal catalogue rendering. The pack
owns only chat-result navigation commands and context attachments.

## 15. CORS and CSP

The customer site must permit:

- the content-addressed pack script;
- the Max Mode manifest and selected bundle;
- browser connections to the assigned runtime; and
- images from the approved media allowlist.

Example CSP additions:

```text
script-src 'self' https://assets.example.com
connect-src 'self' https://deployment.example.com
img-src 'self' data: https://images.dealer.example.com https://m.atcdn.co.uk
```

The runtime must allow the exact dealership website origin for bootstrap,
renewal, chat, suggestions, auth-context, shell-config, and conversation
routes. Do not expose the private connector origin in browser CORS.

## 16. Security Checklist

- [ ] Bootstrap JSON is public and secret-free.
- [ ] Runtime descriptor contains routes but no credential.
- [ ] Browser traffic goes to the assigned runtime, never a connector.
- [ ] Anonymous capabilities are explicitly allowlisted.
- [ ] `dealer.id`, `inventoryVectorSpace`, and `retrievalVectorSpaces` are
      scoped to the intended deployment.
- [ ] Runtime and connector authorization does not trust browser-supplied
      `dealer.id`, stock IDs, or result references as authority.
- [ ] Attached stock IDs are re-resolved and authorized server-side.
- [ ] Only active, dealership-owned stock may be returned by read actions.
- [ ] Optional controls are hidden when their server capability is absent.
- [ ] Write actions use confirmation, idempotency, and application-owned
      persistence.
- [ ] Customer contact data is excluded from indexing and browser telemetry.
- [ ] Page capture excludes forms and private account data.
- [ ] Media and navigation targets use reviewed allowlists.

## 17. Acceptance Checklist

### Installation

- [ ] The pack script filename and SRI match its release manifest.
- [ ] The pack loads the Max Mode bundle selected by the widget manifest.
- [ ] The runtime descriptor is `ready` and anonymous auth probes succeed.
- [ ] No provider, connector, or administration credential appears in the
      browser network log.

### Inventory page

- [ ] Max Mode starts with Browse stock selected.
- [ ] Max Mode shows This vehicle as unavailable before context exists.
- [ ] The Companion dock shows no scoped tools or quick-action toolbar.
- [ ] The mobile rail shows Stock and a disabled Vehicle command, with no Cart
      or Product controls.
- [ ] Search stock returns a rich inventory presentation.
- [ ] Filters and images reflect current approved action facts.
- [ ] Selecting a result can attach it and open contextual tools.

### Detail page

- [ ] Max Mode initially selects This vehicle with the correct visible label.
- [ ] Live details returns the authoritative current stock record.
- [ ] Browse stock remains available without dropping context.
- [ ] Current-page capture includes the intended visible vehicle text only.
- [ ] The mobile Vehicle rail command opens This vehicle tools and Sources
      appears only after retrieved evidence exists.

### Actions

- [ ] Comparison uses two to four explicitly selected active vehicles.
- [ ] Missing or empty action evidence can cooperate with indexed retrieval
      according to deployment policy.
- [ ] Callback and test-drive requests collect required details.
- [ ] No write executes before the configured confirmation and authorization.
- [ ] The dealership backend persists a durable receipt exactly once.

### Resilience

- [ ] Same-tab navigation retains the intended conversation and attachments.
- [ ] Expired or changed anonymous identity clears stale state without replay.
- [ ] Unsupported action results fall back safely.
- [ ] Desktop and mobile layouts have no overlap or horizontal overflow.

## 18. Troubleshooting

### `The assigned public runtime is not ready`

Check `success`, `ready`, `integrationMode`, `chatBaseUrl`,
`inventoryVectorSpace`, `retrievalVectorSpaces`, and all required descriptor
routes. Do not substitute central Platform URLs for a deployment-local runtime.

### `The LoomAI chat bundle manifest is invalid`

The manifest schema, content-addressed filename, and 64-character SHA-256 must
agree. Publish the manifest with `no-store` and the selected bundle as
immutable.

### Inventory renders as a generic result

Confirm the executed action name and expected rich schema. Then compare the
action result with the paths in Section 12. Missing `results`, `vehicleRecord`,
or `_items` causes the reviewed renderer to fall back safely.

### Images do not appear

Confirm the action returned `media.images[].href` or an approved normalized
`imagePath`, and add only the required hostname to `imageHostAllowlist` and the
site CSP.

### Test drive or Callback is missing

Confirm the corresponding capability is `true`. If it is true but the action
is not installed and verified, turn it off rather than advertising a broken
flow.

### A contextual tool asks which vehicle

Attach a vehicle with `attachVehicle`, return a trusted result reference, or
provide `subjectLabel` on a detail page. Do not ask the user for a hidden
provider stock identifier.

### The chat resets during navigation

Confirm all pages use the same runtime descriptor, origin, and browser tab.
Check anonymous renewal and session ID continuity. A genuine identity or
runtime change intentionally clears state.

## 19. Upgrade Procedure

1. Build and test the new pack and generic widget together.
2. Review action-name, schema, tool, capability, and bootstrap changes.
3. Publish content-addressed artifacts and `no-store` manifests.
4. Verify SHA-256 and SRI from a clean browser origin.
5. Canary one dealership and one reduced-capability dealership.
6. Exercise read results and one confirmed write in a non-production account.
7. Pin the approved versions for the customer.
8. Retain the previous version for rollback until acceptance completes.

Do not silently combine a UI pack upgrade with provider credential, indexing,
action-policy, or deployment changes. Verify each ownership boundary.
