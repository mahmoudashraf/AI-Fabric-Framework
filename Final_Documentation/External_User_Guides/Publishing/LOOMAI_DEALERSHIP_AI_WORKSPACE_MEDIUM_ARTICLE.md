# Building a Dealership AI Workspace with Live Stock, Trusted Knowledge and Human Handover

**Subtitle:** A technical look at how LoomAI turns a dealership website into a richer buying experience using dedicated deployments, reusable experience packs and AI Fabric capabilities.

[Watch the live dealership demonstration on YouTube](https://youtu.be/rNl_bcMiS4Y)

A dealership visitor rarely has one isolated question. They may start with a broad search, compare two cars, ask whether a vehicle fits everyday use, check the delivery policy and then decide they want to speak with a person.

A conventional chatbot often treats those as disconnected messages. We wanted to demonstrate a different product shape: an AI workspace that stays connected to current stock, uses approved dealership knowledge, performs governed customer actions and carries the same conversation into a human interaction.

The public Northfield demonstration is built with fictional dealership data and an Auto Trader public-contract simulator. It does not use Auto Trader production credentials and does not represent an Auto Trader endorsement. The simulator lets us prove the integration boundary before partner onboarding.

## What the product demonstrates

The dealership experience combines five capabilities:

1. Current stock synchronization and indexing for discovery.
2. Live read actions for authoritative price, mileage, availability and vehicle details.
3. Approved dealership documents for delivery, warranty, reservations, test drives and customer care.
4. Confirmed callback and test-drive requests.
5. Temporary handover to dealership staff without losing the buyer's conversation.

The customer sees one branded AI Workspace. Underneath, each capability keeps its own authority boundary.

## A dedicated deployment per dealership

The browser does not send all customer traffic through one central chat service. LoomAI Platform resolves the current verified deployment assigned to the installation, and the workspace talks to that deployment.

```text
Dealership website
  -> Platform-hosted installation script
  -> current verified dealership assignment
  -> deployment-local chat and conversation APIs

Dedicated deployment
  -> dealer-scoped inventory connector
  -> indexed vehicle and document evidence
  -> governed dealership actions
  -> protected human-handover integration
```

Provider credentials, connector keys and private signing material stay outside the browser. A future production Auto Trader connection would use approved advertiser-scoped credentials for that dealership rather than a public search credential.

## The website integration is one script

The dealership website installs the workspace from LoomAI Platform:

```html
<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="awi_pub_0123456789abcdef0123456789abcdef"
></script>
```

The installation record selects the deployment, connection mode, immutable workspace assets and the public dealership experience configuration. The website does not need to expose a runtime URL or duplicate deployment configuration.

The same contract supports an anonymous public visitor, an authenticated public user or a backend-mediated private application. The connection profile is deployment configuration, not hard-coded dealership JavaScript.

## A reusable experience pack shapes the workspace

The generic AI Workspace does not know what a vehicle, stock ID or test drive is. The dealership experience pack injects the domain presentation:

```ts
maxMode.init({
  ...context.widgetConfig,
  theme: {
    primaryColor: config.theme?.primaryColor || '#123b35',
    borderRadius: config.theme?.borderRadius || '0.5rem',
    fontFamily: config.theme?.fontFamily || 'Inter, system-ui, sans-serif',
  },
  host: {
    assistantLabel: config.dealer.assistantLabel,
    toolGroups,
    toolRail,
    starterSuggestions: copy.starterSuggestions,
    actionPresentation: dealershipActionPresentationConfig(config.presentation),
    currentPageAttachment: {
      enabled: true,
      maxChars: config.page.maxChars || 1800,
      maxPages: config.page.maxPages || 3,
      invalidateOnNavigation: false,
    },
  },
})
```

This is how the same product template can be adapted to another dealership:

- change the assistant name, colour, copy and launcher;
- choose which buyer tools are visible;
- configure approved image hosts and vehicle detail links;
- enable or disable comparisons, callbacks and test drives; and
- map the deployment to that retailer's inventory, knowledge and actions.

The generic workspace remains reusable for other industries because dealership concepts stay inside the experience pack.

## Keeping indexed stock aligned with the source

The deployment owns a reviewed data-source contract. The current demonstration maps the simulator's Auto Trader-shaped stock response into the `dealer-vehicle` vector space:

```json
{
  "sourceId": "autotrader-dealership-stock-source",
  "path": "/stock",
  "method": "GET",
  "query": { "lifecycleState": "FORECOURT" },
  "vectorSpace": "dealer-vehicle",
  "entityType": "dealer-vehicle",
  "scheduleSeconds": 900
}
```

A scheduled full reconciliation provides a recovery baseline. Signed stock notifications can trigger a targeted current-record fetch by `stockId`, followed by an upsert or deletion. The notification is not trusted as the complete stock record; it tells the connector what current provider state to fetch.

The mapping includes provider IDs, make, model, derivative, year, fuel type, body type, transmission, mileage, price, lifecycle state, advert status, features and approved image references. Sold or deleted records become tombstones and leave searchable stock.

That indexed representation is useful for semantic discovery. Current price, mileage and availability can still come from a live action when the query needs authoritative current facts.

## Read actions and retrieval cooperate

The stock search action is declared as a typed, anonymous-safe read operation:

```yaml
- name: dealership_search_inventory
  accessMode: READ
  anonymousAllowed: true
  requiresConfirmation: false
  groundingEligible: true
  groundingVectorSpaces:
    - dealer-vehicle
  params:
    - name: fuelType
      type: STRING
      required: false
    - name: maxPriceGbp
      type: INTEGER
      required: false
      min: 1
```

The model decides which typed capability fits the request. The connector enforces the reviewed route, scoped provider binding, allowed filters, response projection and authorization. For a mixed question, such as asking for an electric family car and the dealership's delivery terms, orchestration can collect both vehicle evidence and approved document evidence instead of silently answering only one half.

Write actions have a tighter contract. Callback and test-drive requests require buyer-owned contact details and final confirmation. A successful UI receipt appears only after the application action returns success.

## Human help remains part of the same journey

The workspace can request human assistance through the deployment-owned `request_conversation_handover` action. Contact intake is validated before the conversation moves to `HUMAN_PENDING`. Dealership staff claim and reply through a protected staff view. AI responses remain blocked while human authority is active.

When staff explicitly return the conversation to AI, the customer can continue searching without starting over. The selected vehicle, previous evidence and ordered conversation remain available within the same bounded session.

This is valuable commercially because the customer reaches a person after building context and trust. The team receives more than a blank contact form: it receives the conversation, the buyer's current vehicle context and the confirmed contact channel.

## What is proven today, and what production access changes

The live demo proves the LoomAI deployment shape, workspace integration, stock synchronization mechanics, indexed retrieval, governed dealership actions and human handover.

It deliberately uses:

- a fictional dealership;
- synthetic stock records;
- a simulator implementing the expected provider contract; and
- demonstration callback and test-drive outcomes.

A production pilot would replace the simulator profile with approved Auto Trader endpoints and advertiser-scoped credentials, then bind confirmed commercial outcomes to the systems agreed with the retailer and Auto Trader. The AI never receives an unrestricted provider credential and never executes an arbitrary provider request.

## The outcome

The product is not a larger chatbot. It is a reusable AI buying workspace that can be installed with one script, branded for a retailer and extended through reviewed deployment capabilities.

For buyers, that means fewer dead ends and a clearer path from exploration to human help. For dealerships, it means a more informed conversation and a better-qualified next step without surrendering control of inventory, policy or customer outcomes.

- [Explore the live dealership demo](https://loomai.pro/demos/dealership-ai)
- [Learn about LoomAI Platform](https://loomai.pro/products/loomai-platform)
- [Watch the demonstration](https://youtu.be/rNl_bcMiS4Y)

**Tags:** Automotive Retail, AI Workspace, Retrieval Augmented Generation, Human in the Loop, Spring Boot, Customer Experience
