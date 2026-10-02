# 010.29 Generic Max Mode Injectable Action UI And Dealership Experience Plan

**Status:** First-delivery scope fully implemented, deployed, and live verified;
explicit follow-ons remain deferred
**Created:** 2026-10-01
**Revised:** 2026-10-02
**Scope:** Generic Max Mode/Companion UI extension mechanics and the first
dealership-owned component package
**Related live baseline:** Dealership deployment `dep-f023c863`, version `v21` /
`ver-1459db9f`, release `rel-58af17bb`, AI Fabric `0.8.8`

## 1. Purpose

Make Max Mode a useful application workspace for structured action results
without adding dealership, vehicle, Auto Trader, Shopify, or other domain
matching to the generic `max-mode-widget`.

The first consumer is the dealership demonstration. Its inventory, vehicle,
comparison, test-drive, and callback results should appear as purposeful UI
instead of generic object cards. The same extension mechanism must support
future customer applications by injecting their own reviewed components.

This document primarily implements G7 and D4 from
`010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md`. It also
consumes the UI-relevant parts of G3-G5, G8-G9, and D1-D3 without turning this
into a second platform, connector, data-sync, or Auto Trader activation plan.

### 1.1 First delivery boundary

The first delivery includes:

- a provider-neutral renderer registry and explicit action-presentation mapping;
- a bounded safe presentation projection rather than raw provider payloads;
- deterministic inventory, vehicle-detail, and vehicle-comparison components;
- safe result selection and follow-up commands that continue through the
  assigned deployment;
- reuse of the existing generic clarification, confirmation, and receipt flow;
- generic fallback and renderer error isolation; and
- focused widget, browser, conversation, action, and screenshot verification.

The first delivery explicitly defers:

- AI Fabric automatic action-result promotion into the conversation working set
  from G3/D1;
- a new Marketplace `UI_EXTENSION` package type and complete Platform lifecycle
  management for independently distributed UI extensions;
- a new typed action-proposal endpoint while the existing chat,
  clarification, and confirmation route remains sufficient;
- custom replacements for the existing parameter and confirmation UI unless
  live evidence demonstrates a real usability gap;
- new dealership actions, real provider activation, inventory synchronization
  changes, and broader deployment operations;
- prompt/model changes and cost optimization; and
- production-generic current-page attachment semantics for arbitrary websites
  from G9.

Deferral does not mean the boundary may be bypassed. Until G3/D1 exists, the
UI must use explicit selected-result references and the deployment must
re-resolve any action target. Until G9 exists, the already implemented
current-page feature remains limited to its controlled first-party demo posture.

## 2. Product Decision

The generic chat application owns:

- conversation layout and state;
- authenticated or anonymous deployment communication;
- messages, attachments, suggestions, clarification, confirmation, and error
  states;
- a stable action-presentation extension contract;
- safe commands that injected components may request; and
- a generic fallback renderer for action results without a registered
  component.

The customer or Marketplace experience package owns:

- domain-specific visual components;
- mappings from reviewed action/result contracts to component IDs;
- domain labels, formatting, and navigation;
- domain-specific read and write action definitions; and
- customer-site composition and styling within the supported theme boundary.

The browser component must never call a dealership, Auto Trader, Shopify, or
other protected provider API directly. All live facts and writes continue
through the assigned LoomAI deployment and its governed connector boundary.

## 3. Required Runtime Path

```text
Customer page
  -> generic Max Mode/Companion widget
  -> assigned deployment chat endpoint
  -> AI Fabric orchestration, policy and confirmation
  -> deployment-local connector
  -> customer/provider backend
  -> normalized action result
  -> bounded presentation projection and selected-result references
  -> injected presentation component or generic fallback
```

The Platform control plane is not placed in normal customer chat or action
traffic.

### 3.1 AI and deterministic responsibilities

| Concern | Owner |
| --- | --- |
| Understand an open-ended buyer request | AI orchestration |
| Select an eligible action and extract buyer-owned parameters | AI orchestration under the action contract |
| Resolve protected vehicle IDs | Runtime/connector trusted resolution |
| Fetch inventory, availability, price, or a receipt | Connector and customer/provider backend |
| Produce safe presentation data and result references | Runtime/action projection contract, with a reviewed host adapter during the first delivery |
| Render returned vehicle facts | Deterministic injected component |
| Select two cards in the browser | Deterministic component state |
| Revalidate a selected target before an action | Runtime/connector; never the browser component |
| Explain suitability or trade-offs | AI generation grounded in selected action/RAG facts |
| Collect missing action fields | Schema-driven UI and runtime clarification contract |
| Authorize and confirm a write | Runtime policy and confirmation flow |
| Persist a lead or reservation | Customer backend through the connector |

An injected component may avoid an unnecessary language-model round trip for
purely local display interactions. It may not bypass LoomAI when retrieving
live data or proposing/executing an action.

The first delivery does not claim that arbitrary pronouns such as `those` or
`the cheaper one` are resolved from prior action results automatically. That
claim remains dependent on G3/D1. Explicit card selection can ship earlier
because the UI can preserve the selected safe labels/source references and the
deployment can resolve them again under the dealership scope.

## 4. Current State And Gap

The generic widget already supports:

- host-owned starter prompts and starter suggestions;
- runtime-generated suggestions;
- query, conversation, attachment, clarification, and confirmation flows;
- structured action-result rendering;
- a generic product-like renderer and generic object/list fallback;
- attach-to-chat behavior; and
- direct anonymous communication with an assigned deployment.

The dealership deployment already defines:

- `dealership_search_inventory`;
- `dealership_get_vehicle`;
- `dealership_resolve_vehicle`;
- `dealership_compare_vehicles`;
- `dealership_request_callback`; and
- `dealership_request_test_drive`.

The gap is presentation, not initial action coverage. Dealership results expose
fields such as `make`, `model`, `priceGbp`, `mileage`, and `fuelType`. The
current generic commerce heuristic primarily recognizes fields such as
`name`, `title`, and `price`, so vehicle results become verbose generic cards.
Adding vehicle field matching to the generic renderer would create domain
coupling and is explicitly rejected.

## 5. Generic Extension Contract

### 5.1 Stable presentation identity

Presentation selection must use an explicit, versioned identity. It must not
infer a component by searching result text or guessing from arbitrary field
names.

Preferred contract:

```json
{
  "actionName": "dealership_search_inventory",
  "presentation": {
    "rendererId": "loomai.vehicle-inventory.v1",
    "schemaVersion": "loomai.vehicle-list.v1",
    "data": {
      "items": []
    },
    "resultReferences": [
      {
        "key": "item-0",
        "label": "2024 Northstar S4",
        "scope": "dealer-vehicle"
      }
    ]
  }
}
```

Until action contracts can publish `presentation`, the host integration may
map an exact reviewed action name to a renderer ID and project an explicitly
allowlisted view model from the normalized action result. This compatibility
mapping belongs to the host/Marketplace package, not to generic widget source.
It must not pass the unrestricted connector/provider payload through as
`presentation.data`.

The generic widget may dispatch using the configured action name, but it must
not contain hardcoded knowledge of dealership action names or inspect business
fields, query text, generated answers, or labels to choose a component.

### 5.2 Renderer registration

The widget should expose a generic registry conceptually equivalent to:

```ts
interface ActionResultRendererRegistration {
  id: string;
  schemaVersions?: string[];
  render: ActionResultRenderer;
}

interface ActionPresentationMapping {
  actionName: string;
  rendererId: string;
}
```

For React consumers, `render` may be a typed React component. For the IIFE
bundle used by ordinary websites, registration must support a framework-neutral
adapter such as a registered custom element or a widget-owned renderer callback.
Both paths use the same safe properties and command surface.

Registration failure, an unknown renderer ID, an incompatible schema version,
or a renderer exception must fall back to the generic action-result renderer.
One custom component must never break the conversation surface.

### 5.3 Component input

An injected result component receives a bounded immutable view model:

```ts
interface ActionPresentationProps {
  actionName: string;
  rendererId: string;
  schemaVersion?: string;
  presentationData: Readonly<unknown>;
  resultReferences: readonly PresentationResultReference[];
  messageId: string;
  conversationId?: string;
  selectedResults: readonly PresentationResultReference[];
  context: Readonly<Record<string, unknown>>;
  commands: ActionPresentationCommands;
}

interface PresentationResultReference {
  key: string;
  label: string;
  scope?: string;
  sourceMessageId: string;
  sourceActionName: string;
}
```

`presentationData`, result references, and `context` must all be allowlisted and
bounded. They must not expose API keys, assertions, connector headers,
protected internal identifiers, unrestricted debug data, or raw provider
payloads. A result-reference key identifies an item in the rendered projection;
it is not proof that a browser-supplied action target is trusted.
The widget binds `sourceMessageId` and `sourceActionName` when it accepts the
runtime result; an injected component cannot choose or rewrite that provenance.

### 5.4 Safe command surface

Injected components receive capabilities, not backend clients:

```ts
interface ActionPresentationCommands {
  ask(input: {
    query: string;
    resultReferences?: PresentationResultReference[];
  }): Promise<void>;

  attachResult(reference: PresentationResultReference): void;
  detachResult(referenceKey: string): void;

  navigate(input: {
    url: string;
    target?: "same-window" | "new-window";
  }): void;
}
```

Rules:

1. `ask` uses the existing deployment chat route.
2. A test-drive/callback CTA uses `ask` with an explicit selected-result
   reference to start the existing intent, clarification, and confirmation
   flow. It does not call the connector or customer backend from the browser.
3. `attachResult` accepts only a reference issued with the currently rendered
   bounded presentation. It cannot accept an arbitrary raw object or caller
   constructed protected identifier.
4. A write action still requires runtime authorization, trusted-resource
   resolution, validation, and confirmation according to its manifest.
5. Until G3/D1 is implemented, selected-result context helps ground the next
   turn but does not make its target authoritative. The deployment/connector
   re-resolves the buyer-facing reference inside the active dealership scope.
6. `navigate` accepts only reviewed `https` URLs or same-site relative paths.
7. Components do not receive authorization headers, runtime assertion material,
   connector credentials, or a generic authenticated HTTP client.

An optional future `proposeAction` command may be added only after a typed
runtime contract exists. The first delivery deliberately reuses the current
chat query, clarification, and confirmation flow and must not invent a
browser-to-connector route as a shortcut.

Current-page attachments are not created through this component command API.
They remain owned by the widget's existing page-attachment control. Under the
controlled interim implementation they are untrusted answer context and must
never supply an executable action target. Production-generic support remains
deferred until AI Fabric exposes G9's typed `TRANSIENT_CONTEXT`, `UNTRUSTED`,
turn-scoped, non-action-eligible contract.

### 5.5 Extension slots

The complete design recognizes these generic slots:

| Slot | Purpose |
| --- | --- |
| `action-result` | Render list, detail, comparison, or receipt results inside a message/workspace |
| `action-parameters` | Render a schema-driven or custom missing-parameter form |
| `action-confirmation` | Render the final safe confirmation preview while retaining the standard confirm/reject controller |
| `context-workspace` | Optional larger Max Mode surface synchronized with the selected message/result |

The first delivery implements `action-result` and, where needed for the large
Max Mode canvas, `context-workspace`. It reuses the current generic
`action-parameters` and `action-confirmation` surfaces. Custom replacements are
deferred unless the hosted canary demonstrates that the existing flow cannot
complete the dealership journey safely and clearly.

The core widget always retains confirmation state and transition ownership. A
future custom confirmation component may improve presentation but cannot
declare a write confirmed by itself.

## 6. Dealership Component Package

Dealership components should live with the dealership customer experience in
`Platfrom/loomai-site`, or in a later reusable private Marketplace UI package.
They must not be added to `max-mode-widget` core.

### 6.1 Vehicle inventory results

Renderer ID: `loomai.vehicle-inventory.v1`

Uses `dealership_search_inventory` results to provide:

- an applied-filter and freshness summary;
- responsive vehicle cards with image, make/model, derivative, price, mileage,
  fuel type, transmission, location, and availability/lifecycle state;
- attach or ask-about-this-vehicle controls;
- multi-select for comparison;
- a focused vehicle-detail navigation command; and
- test-drive and callback CTAs that begin the existing governed conversation
  flow for the selected vehicle.

Selecting cards is local UI state. Pressing compare invokes
`dealership_compare_vehicles` through LoomAI with explicit selected-result
references. Until G3/D1 exists, the dealership action boundary re-resolves those
references; the browser selection itself is not trusted authority.

### 6.2 Vehicle detail result

Renderer ID: `loomai.vehicle-detail.v1`

Uses `dealership_get_vehicle` results to provide:

- an inspectable specification layout;
- source label and freshness;
- standout-feature and everyday-suitability questions;
- attach/select for comparison;
- request-test-drive and callback CTAs through the existing chat flow; and
- navigation to the ordinary dealership vehicle page when available.

Facts render deterministically. Suitability and trade-off explanations are AI
queries grounded in the structured vehicle attachment and approved indexed
dealership knowledge.

### 6.3 Vehicle comparison result

Renderer ID: `loomai.vehicle-comparison.v1`

Uses `dealership_compare_vehicles` results to provide:

- two-to-four-column comparison;
- stable rows for price, mileage, powertrain, body type, transmission, range,
  seats, location, and source freshness;
- visible unavailable/unknown values instead of fabricated replacements;
- a select-this-vehicle control; and
- grounded follow-up prompts such as value, practicality, or running-cost
  trade-offs.

The deterministic table does not name a winner. An AI explanation may recommend
a vehicle only after the buyer provides priorities and the answer cites the
facts used.

### 6.4 Existing lead flow and optional receipt presentation

The first delivery reuses the generic clarification and confirmation components
already used by `dealership_request_test_drive` and
`dealership_request_callback`. It must:

- display the selected vehicle safe label;
- collect only missing buyer-owned parameters;
- use existing typed field validation;
- never display or request internal `vehicleId`;
- show the existing final safe confirmation preview;
- submit through the existing confirmation flow; and
- render the returned receipt without exposing connector internals.

A dedicated `loomai.action-receipt.v1` renderer may be added in the first
delivery if the current generic receipt is materially unclear. A custom
`loomai.action-form.v1` or confirmation renderer is deferred until hosted
evidence demonstrates a gap. Name, email, phone, preferred date, message, and
explicit contact consent remain buyer-owned values. Trusted vehicle IDs remain
server-resolved.

## 7. Max Mode Experience Composition

The current top starter prompts remain useful:

- Summarize this car;
- Everyday suitability; and
- Explain trade-offs.

Runtime suggestions remain conversational follow-ups. They should not be the
only interaction model.

For a structured result, Max Mode should use the available workspace as follows:

```text
Header and host starter prompts
------------------------------------------------------------
Grounded generated explanation
------------------------------------------------------------
Injected inventory/detail/comparison workspace
------------------------------------------------------------
Context-aware suggestions
------------------------------------------------------------
Attachments and composer
```

The Companion dock continues to show a compact subset. Opening Max Mode reveals
the full injected workspace without creating another conversation or changing
the deployment position/mode.

Suggestions should be grouped conceptually as:

- **Understand:** features, suitability, policy, warranty;
- **Compare:** mileage, price, range, practicality;
- **Act:** test drive, callback, reservation or part exchange when installed.

Visible UI copy does not need to explain this taxonomy.

## 8. Existing Actions Before New Actions

The first UI increment must use and prove the five model-visible dealership
actions before expanding the action catalogue. Vehicle target resolution is a
server-owned helper, not another action exposed to the model.

| Existing action | UI behavior |
| --- | --- |
| `dealership_search_inventory` | Inventory workspace |
| `dealership_get_vehicle` | Vehicle detail workspace |
| `dealership_compare_vehicles` | Comparison workspace |
| `dealership_request_callback` | Lead form, confirmation and receipt |
| `dealership_request_test_drive` | Test-drive form, confirmation and receipt |

The backend's lower-level vehicle resolver remains private to the trusted
connector/action path. It converts a buyer-facing stock reference into the
current internal target only after scope and lifecycle validation. Publishing
that helper as a sixth model action would let orchestration stop after a
technically successful lookup without completing the requested detail or write
operation.

Questions about features, suitability, warranty, dealership policy, or general
trade-offs should use grounded generation/RAG. They are not new actions merely
because the UI displays a button.

## 9. Candidate New Dealership Actions

This section is a non-blocking backlog. None of these actions is required to
implement or release the first injectable Max Mode UI.

Add an action only after an authorized backend/provider capability exists and
the Marketplace action contract defines its scope.

| Candidate action | Mode | Customer value | Required boundary |
| --- | --- | --- | --- |
| `dealership_check_vehicle_availability` | READ | Confirm current sale/test-drive availability | Live source; no claim from stale index alone |
| `dealership_calculate_finance_example` | READ/calculation | Illustrative payment scenarios | Clear assumptions and regulatory wording; not financial advice or approval |
| `dealership_get_vehicle_history` | READ | MOT/history/provenance when licensed | Provider grant, attribution and retention rules |
| `dealership_find_location` | READ | Opening hours, directions and vehicle location | Approved dealership location source |
| `dealership_request_part_exchange` | WRITE | Capture a trade-in request | Confirmation, PII minimization and customer-owned persistence |
| `dealership_reserve_vehicle` | WRITE | Place a bounded reservation request | Authoritative availability recheck, idempotency and confirmation |
| `dealership_book_appointment` | WRITE | Book a sales/service appointment | Slot validation, idempotency and confirmation |

The UI package may omit components for uninstalled actions. It must not show a
control for a capability absent from the deployment shell/action contract.

## 10. Platform And Marketplace Configuration

### 10.1 First delivery configuration

To keep the first implementation focused, the reviewed dealership components
are built with `Platfrom/loomai-site` and supplied to the generic widget through
host configuration. That configuration declares:

- exact existing action-to-renderer mappings;
- supported presentation schema versions;
- allowlisted projection fields and result-reference fields;
- renderer-specific safe follow-up commands;
- approved same-site navigation targets; and
- the existing theme, labels, mode, position, and deployment routes.

The first delivery does not add a new Marketplace package type, Platform editor,
remote component loader, or independently hosted renderer bundle. This keeps
the security and deployment surface bounded while proving the generic widget
contract with a real customer application.

The host build and live canary must validate that:

1. every mapped action exists in the current dealership action catalogue;
2. every configured renderer is registered and supports the declared schema;
3. the projection allowlist excludes protected/internal fields;
4. unknown renderers and schema mismatches use the generic fallback;
5. write CTAs still enter the existing clarification and confirmation flow; and
6. the browser receives no provider or connector credential.

### 10.2 Deferred Platform productization

After the host-bundled canary is green and a second customer/domain demonstrates
reuse, the reusable deployment template may declare:

- required action plugin IDs and versions;
- action-to-renderer mappings;
- renderer package/version and integrity metadata;
- safe attachment projections;
- safe command/action allowlists per renderer;
- approved host origins;
- theme and host labels;
- expected result schema versions; and
- post-apply UI/action verification scenarios.

At that point Platform publishing should validate that:

1. every mapped action exists in the resolved deployment action catalogue;
2. every renderer ID resolves to an approved package/version;
3. schema versions are compatible;
4. proposed actions are a subset of installed actions;
5. write actions retain confirmation and authorization requirements;
6. no secret or protected binding is projected to the browser; and
7. export/import preserves mappings and package integrity metadata without
   exporting secret values.

Renderer code is not a DATA or ACTION plugin. A first-class versioned
`UI_EXTENSION` package type is a later product decision, not a prerequisite for
this plan. Do not misclassify UI code as an action connector merely to reuse an
existing package label.

## 11. Security And Trust Requirements

- No custom component receives provider credentials or a generic backend
  client.
- No browser-provided dealership, advertiser, tenant, deployment, or protected
  resource ID becomes trusted merely because a component submitted it.
- A selected result reference is scoped to its source message, action, and
  conversation. It is re-resolved for actions until G3/D1 supplies a
  framework-owned trusted working-set contract.
- Internal action parameters remain hidden and server-owned.
- Anonymous access remains limited to explicitly reviewed actions.
- Writes retain confirmation, application validation, idempotency, audit, and
  customer-owned persistence.
- Result projections are allowlisted and bounded.
- Raw connector/provider responses are never component properties.
- Current-page text remains untrusted, non-authoritative context and cannot be
  converted into an action target by a renderer.
- Generated prose never overrides structured price, availability, identifier,
  source, or receipt fields.
- Unknown or malformed renderer output fails back to safe generic rendering.
- Renderer packages use integrity/version controls and an allowlisted origin or
  are built into the reviewed host application.
- Custom components cannot inject HTML into assistant prose without
  sanitization.
- Telemetry records renderer ID, schema version, action name, render outcome,
  and safe timing only; it excludes PII and raw payloads.
- Prompt text does not implement renderer selection, authorization, target
  trust, confirmation, field validation, or result-shape detection.

## 12. Implementation Sequence

### Phase 0: preserve the verified baseline

1. Preserve the repeated `v15` quality reports as the pre-presentation behavior
   baseline; use the current immutable version for all new hosted evidence.
2. Capture the current generic-card, clarification, confirmation, receipt,
   mobile, and desktop behavior before changing the widget.
3. Do not change deployment mode/position, prompts, model policy, backend action
   semantics, or provider configuration as part of this UI work.

### Phase 1: minimal generic widget mechanics

1. Define the versioned renderer registry and safe component properties.
2. Add exact action/presentation mapping to host configuration.
3. Project bounded `presentationData` and selected-result references rather
   than passing a raw action/provider payload.
4. Resolve a custom renderer before the generic fallback.
5. Add an error boundary and fallback behavior.
6. Expose `ask`, `attachResult`, `detachResult`, and safe `navigate` commands.
   Continue to use the current chat flow for action intent and confirmation.
7. Implement the `action-result` slot and the minimum `context-workspace`
   synchronization needed by Max Mode.
8. Reuse the existing parameter, confirmation, suggestion, conversation,
   attachment, and receipt behavior.
9. Support React registration and the IIFE/custom-element path.
10. Preserve the current generic behavior when no extensions are supplied.

### Phase 2: dealership package using existing actions

1. Implement inventory, detail, and comparison components outside widget core.
2. Register mappings for the existing dealership actions.
3. Reuse the current `executor` mode, `search` position, conversation, and
   current-page attachment behavior.
4. Route test-drive and callback CTAs through the current chat,
   clarification, and confirmation flow.
5. Re-resolve selected targets at the deployment/connector boundary; do not
   trust browser-selected IDs.
6. Keep current deployment/backend/connector interfaces unchanged unless a
   missing safe presentation field such as applied filters or source freshness
   is demonstrated. Add only the normalized projection field that the UI
   actually needs.
7. Add a dedicated receipt renderer only if the generic receipt is unclear in
   the canary.

### Phase 3: focused hosted canary

1. Build and deploy the generic widget and host-bundled dealership components.
2. Exercise search, detail, comparison, grounded follow-up, test-drive
   clarification, reject, confirm, receipt, and staff readback in one session.
3. Repeat the read-only sequence and retain safe request IDs and screenshots.
4. Compare behavior against the preserved pre-presentation baseline.
5. Release only when generic fallback, anonymous auth, conversations,
   suggestions, current-page attachment, and debug behavior remain green.

### Phase 4: deferred follow-on work

The first release does not wait for these items:

1. Implement G3/D1 working-set promotion and then enable reliable pronoun-based
   follow-ups over prior action results.
2. Implement AI Fabric G9 before enabling current-page attachment on arbitrary
   production websites.
3. Productize renderer mappings, immutable UI packages, review/diff,
   export/import, and template-owned verification in Platform after reuse is
   proven.
4. Introduce a typed runtime `proposeAction` command only if it provides a clear
   benefit over the existing governed chat flow.
5. Add custom parameter/confirmation renderers only if live usability evidence
   requires them.
6. Add new dealership actions only after provider/customer grants and exact
   contracts exist.
7. Optimize prompts, models, latency, and cost only against the same immutable
   deployment and repeated quality corpus.

## 13. Verification Matrix

### 13.1 Generic widget tests

- registered renderer selected by exact renderer ID/action mapping;
- no hardcoded domain action-name, field-name, query-text, or answer-text
  matching in generic widget code;
- components receive only bounded `presentationData`, never the raw provider
  payload;
- unknown renderer and incompatible schema use generic fallback;
- renderer exception is contained;
- no extension configuration preserves existing output;
- safe commands cannot access arbitrary HTTP or connector routes;
- action CTA enters clarification/confirmation rather than executing;
- arbitrary component-created objects/IDs cannot be attached as trusted result
  references;
- current-page text cannot become an action-eligible selected result;
- mobile, desktop, Companion, and Max Mode layouts do not overlap;
- long values and missing fields remain readable; and
- keyboard, focus, screen-reader labels, and reduced-motion behavior pass.

### 13.2 Dealership component tests

- search filters and freshness appear from structured facts;
- cards preserve exact price, mileage, fuel type, location, and lifecycle state;
- compare requires two to four explicit selected-result references and the
  deployment re-resolves their buyer-facing vehicle references;
- unavailable values remain explicitly unknown;
- detail and comparison follow-ups carry only the intended safe result
  projections/source references;
- test-drive and callback forms ask only for missing buyer-owned values;
- internal `vehicleId` is never displayed or accepted as user authority;
- reject/cancel produces no lead;
- confirm creates exactly one lead and renders a stable receipt; and
- staff inbox shows the same persisted request.

### 13.3 Hosted evidence

- use the immutable dealership deployment version under test;
- capture browser network evidence proving chat calls go directly to the
  assigned deployment and not to a provider API or Platform control plane;
- prove connector calls originate from the deployment boundary;
- run one continuous-session search, detail, compare, RAG follow-up, test-drive
  clarification, reject, confirm, receipt, and staff-readback scenario;
- use explicit selected vehicles in the first-release comparison/follow-up
  canary; pronoun-only working-set behavior is not a release claim before G3/D1;
- repeat the read-only sequence to detect stochastic regressions; and
- retain safe provider request IDs, deployment/release IDs, renderer/schema
  versions, screenshots, and gate output.

## 14. Release Gates

The UI improvement is ready only when:

1. generic widget tests are green with and without custom renderers;
2. the widget core contains no hardcoded dealership, vehicle, Auto Trader,
   domain action-name, business field-name, or response-text matching; generic
   host-configured action-to-renderer dispatch is allowed;
3. the dealership package uses the existing actions successfully;
4. browser traffic contains no direct protected dealership/provider calls;
5. structured facts and generated explanations remain visibly distinguishable;
6. test-drive and callback writes pass confirmation, idempotency, persistence,
   receipt, and staff-readback gates;
7. mobile and desktop screenshots show a useful workspace without overlap or
   empty unusable regions;
8. unknown/missing extensions degrade to the generic renderer; and
9. the existing conversation, suggestion, attachment, debug, and anonymous-auth
   behavior remains green;
10. selected browser results are re-resolved before an action and no current-page
    text is accepted as an action target; and
11. release evidence does not claim G3/D1 working-set automation, G9 arbitrary
    page support, new dealership actions, or Platform-managed UI packages.

## 15. Initial Delivery Recommendation

Implement the generic registry and three dealership presentations first:

1. vehicle inventory;
2. vehicle detail;
3. vehicle comparison.

Reuse the current generic parameter, confirmation, and receipt UI for test-drive
and callback actions. Add a dedicated receipt presentation only if the canary
shows that the generic result is materially unclear.

Do not add new backend actions merely to make the UI appear richer. The current
action catalogue already covers the most important meeting journey. Add
availability, finance, history, part exchange, reservation, and appointment
actions later only when their authoritative API, policy, and result contracts
are available.

This sequence turns Max Mode from a large conversational canvas into a grounded
dealership workspace while preserving the reusable chat application and the
deployment-owned security boundary.

## 16. Alignment With `010.26` Improvements

| `010.26` item | Treatment in this plan |
| --- | --- |
| G1 Coolify `429` lifecycle handling | Unrelated to the chat UI change; do not block this implementation |
| G2 template-owned verification packs | Defer Platform productization; run the focused widget/dealership canary directly for the first release |
| G3 action-result working set | Preserve its trust model, but defer framework automation; use explicit result selection and server re-resolution |
| G4 action/sufficiency diagnostics | Reuse existing safe debug output; add only renderer/projection diagnostics needed to prove this UI path |
| G5 repeatable conversation evaluation | Required for the first hosted UI canary and release gate |
| G6 inference cost evidence | Record existing timing where available; defer model or cost optimization |
| G7 structured action presentation | Primary generic widget deliverable in this plan |
| G8 prompt governance | Required invariant: no prompt-based rendering, authorization, confirmation, or shape detection |
| G9 transient page context | Preserve the controlled demo behavior; defer arbitrary-site production support to the framework contract |
| D1 dealership working-set projection | Defer with G3; do not claim pronoun-only continuity in this release |
| D2 filters and freshness | Consume safe normalized fields; make a narrowly scoped backend/connector projection change only if a required field is absent |
| D3 confirmed-write canary | Required using the existing clarification/confirmation UI, with deterministic cleanup or test-record handling |
| D4 structured vehicle/comparison UI | Primary dealership deliverable in this plan |
| D5 Marketplace packaging | Keep first components host-bundled; defer reusable Platform packaging until a second use case proves the abstraction |
| D6 sync/reconciliation | Preserve the current verified deployment; outside this UI implementation |
| D7 prompt/model baseline | Freeze during the UI canary so presentation changes are measured independently |
| D8 latency/cost budgets | Measure UI and end-to-end timings, but defer optimization |
| D9 Auto Trader activation | Outside scope; no new provider connectivity or endorsement claim |

This ordering intentionally delivers the highest-value visible improvement
without coupling widget core to the dealership domain or waiting for unrelated
framework, Platform, provider, or data-operation work.

## 17. Implementation Record

### 17.1 Implemented on 2026-10-02

- Added the provider-neutral action-presentation registry, exact action mapping,
  explicit bounded projection, schema compatibility check, safe result
  references, React/custom-element adapters, renderer isolation, telemetry, and
  generic fallback to `max-mode-widget`.
- Added deployment-backed `ask`, bounded result attach/detach, and safe
  navigation commands. Selected results are emitted as
  `action-result-context` with source-message/action provenance,
  `trust=REQUIRES_SERVER_RESOLUTION`, and `actionEligible=false`.
- Kept dealership action names, fields, prompts, components, and styling out of
  widget core. The host-owned package is implemented in
  `Platfrom/loomai-site/src/scripts/dealership-action-presentations.ts`.
- Added inventory, vehicle-detail, and desktop/mobile vehicle-comparison
  presentations using the five-action dealership catalogue installed on the
  deployment. Trusted vehicle resolution remains a server-owned helper rather
  than a model action. Write controls still enter the existing chat,
  clarification, confirmation, and receipt flow.
- Added normalized `appliedFilters`, result counts, source freshness, and data
  notice fields to the dealership backend/connector result projection. No new
  action or provider route was introduced.
- Fixed the generic IIFE attach-then-send race by carrying a bounded snapshot of
  queued host attachments with an immediately issued programmatic prompt. The
  normal merger deduplicates by public `id` or `sku`, and consumed queue entries
  are removed after widget state accepts them.

### 17.2 Local verification evidence

- Max Mode widget TypeScript check: green.
- Action-presentation contract smoke: green, including bounded collections,
  allowlisted fields, protected-context exclusion, provenance, unknown action,
  and incompatible-schema fallback.
- Widget ESM, CJS, declaration, and IIFE production builds: green.
- Dealership backend: `25` tests, `0` failures, `0` errors. This includes a
  real login, CSRF issuance, authenticated staff-state update, and final
  `CANCELLED` persistence check.
- Public-site release gate: Astro diagnostics reported `0` errors/warnings;
  production build, `27` static-route checks, content graph, accessibility, and
  Playwright browser smoke all passed.
- Browser evidence covers exact custom inventory/detail/comparison rendering,
  applied filters, attach-then-send delivery, bounded non-authoritative result
  context, two-result comparison, responsive stacked mobile comparison, and
  unchanged page-attachment/conversation behavior.

### 17.3 Hosted rollout and evidence

- The current runtime product source is
  `80acdad2693f058e938ae5e14141c3bfa8d85cec`. It contains AI Fabric `0.8.8`,
  whose generic structured read-action result contract preserves the bounded
  facts required by host renderers without dealership-specific matching.
- Deployment `dep-f023c863` published immutable version `v21` /
  `ver-1459db9f` with configuration hash
  `38f53d907e7134362bb194d85afe4ba18858745d045c029c94a02b60048c1aa0`.
  Release `rel-58af17bb` is `APPLIED_VERIFIED`, provisioning is `ACTIVE`, and
  verification run `vrf-6388d613` is `PASSED` with `25` passed, `0` failed and
  `5` intentionally skipped checks. It uses promoted source artifact
  `dsa-454597d0`, AI Fabric `0.8.8`, and required no reindex.
- Runtime liveness/readiness and connector health return HTTP `200` / `UP`.
  Runtime readback reports the exact framework version, product source,
  deployment version, five-action catalogue, entity configuration, and
  capability-manifest hash expected by the immutable version.
- Dealership backend commit
  `59caeffa3233305d45ab9c1ca0dfd7a0c31f322f` is live from staging Coolify
  deployment `kosm46d20v41s3pj8as7b2im`. Its status route reports `UP`, the
  exact commit and six fictional inventory records.
- Cross-site staff writes retain CSRF protection. The CSRF cookie now inherits
  the configured secure session posture (`Secure; SameSite=None`), allowing
  the public-site staff client to return the cookie with its CSRF header. A
  live authenticated status update and readback both returned `CANCELLED`.
- The final meeting gate used one anonymous conversation for `18` query turns.
  It proved all eight host tools, contextual suggestions, anonymous renewal,
  inventory/detail/comparison presentation, a grounded follow-up with indexed
  evidence,
  governed rejection and confirmation, protected staff readback, current-page
  attach/remove, desktop and mobile layouts, and absence of direct protected
  browser calls.
- The inventory result selected renderer `loomai.vehicle-inventory.v1` with
  schema `loomai.vehicle-list.v1`, rendered three bounded cards with electric
  and GBP 40,000 filters, retained source action/message provenance, and
  projected no raw `content`, `errors`, or `warnings` transport fields. Detail
  and comparison renderers were exercised through the same live deployment.
- Rejecting a test drive created no action. Confirmed test-drive and callback
  journeys each created exactly one persisted receipt, appeared once in the
  protected staff inbox with the expected buyer/vehicle details, and were
  deterministically changed to `CANCELLED` after verification. A repeated CTA
  may enter confirmation directly when the same conversation already owns all
  required buyer parameters; this is valid state reuse, not a missing form.
- The strict seven-scenario quality gate separately passed in one conversation
  using bounded `ITERATIVE` planning (`maxIterations=2`) and
  `RAG_IF_ACTIONS_INSUFFICIENT`. It covered action retrieval, semantic RAG,
  comparison, empty-action RAG fallback, policy honesty and non-executing
  governed-write intent without confirming a write.

### 17.4 Completion verdict and boundaries

The first-delivery boundary in section 1.1 is fully implemented and live
verified. The generic widget contains no hardcoded dealership, vehicle, Auto
Trader, dealership action-name, business-field, query-text, or answer-text
matching. Domain components and mappings remain in the customer host package
and communicate only through the generic bounded projection and command
contracts.

This completion verdict does not include the explicitly deferred items in
Phase 4. In particular, it is not evidence of real Auto Trader access, rights,
endorsement or partnership; the live inventory is fictional. It also does not
claim automatic G3/D1 working-set promotion, arbitrary-site G9 page authority,
or Platform-managed `UI_EXTENSION` packaging.

### 17.5 Anonymous conversation ownership recovery (2026-10-02)

- Corrected a generic widget identity-continuity defect found through the live
  dealership surface. Conversation IDs and messages were persisted in
  `sessionStorage`, while the short-lived anonymous bearer token intentionally
  remained memory-only. A document reload could therefore restore a
  conversation owned by an earlier anonymous runtime identity.
- The widget now persists a non-secret `{ runtimeKey, sessionId }` binding and
  compares it with every fresh anonymous bootstrap. A changed runtime or
  runtime-issued session clears conversation history, attachments, pending
  prompts, confirmation state, debug state, and the denied conversation handle.
  Bearer tokens remain memory-only.
- Canonical chat HTTP 200 responses with `success=false`, `type=ERROR`, an
  existing request conversation ID, and machine code `ACCESS_DENIED` or
  `CONVERSATION_ACCESS_DENIED` activate the same recovery boundary. The raw
  denial is not rendered as an assistant answer, and the submitted request is
  never replayed automatically.
- Recovery emits the provider-neutral `conversation:reset` host event instead
  of an operational `error`, so a host does not mark a healthy deployment
  unavailable during an expected identity-boundary reset.
- Local production build and Playwright browser smoke passed. Regression proof
  covers both legacy typed-denial recovery and full document navigation with a
  changed anonymous session, including zero automatic replay and a clean next
  request without the stale conversation ID.

### 17.6 Generic scoped tool navigation (2026-10-02)

- Added the optional `host.toolGroups` contract with exactly two widget-owned
  scope identities: `default` and `contextual`. Widget core knows no dealership,
  vehicle, provider, action-name, response-text, or business-field rule. The
  host owns group labels, icon semantics, prompts, initial scope, page-context
  availability, and context labels.
- The same scope state is rendered by Companion, desktop Max Mode, and the
  mobile quick-actions sheet. Both selectors remain available whenever the
  host configures the contract. Adding an attachment selects `contextual`;
  manual selection of `default` preserves all attachments; removing the final
  attachment returns to `default`.
- A detail page may keep contextual tools available without an attachment via
  `availableWithoutAttachments=true`. Explicit attachment labels take
  precedence over the host page label; the host page label takes precedence
  over a heuristic attachment title. Existing hosts without `toolGroups`
  retain the flat `starterPrompts`/runtime-shell behavior.
- The dealership host now supplies `Browse stock` with Search stock, Electric
  cars, Family options and Compare cars, plus `This vehicle` with Live details,
  Everyday use, Trade-offs, Location, Test drive and Callback. These names and
  queries exist only in the public-site host package.
- Local release verification is green: widget TypeScript and production builds,
  Astro diagnostics/build, content/static gates, accessibility, and complete
  Playwright browser smoke. Browser regressions prove initial disabled context
  on inventory, auto-selection after attach, Browse switching without detach,
  detail-page context without attachments, final-detach reset, clear context
  labeling, and desktop/Companion/mobile rendering.
