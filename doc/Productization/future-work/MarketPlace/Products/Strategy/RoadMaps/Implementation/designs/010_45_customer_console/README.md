# 010.45 Customer Console Visual Proposal

Open [index.html](index.html) directly in a browser. It works from disk, without
a server or external network. The toolbar switches lifecycle fixtures and page
layouts; clicking resources opens the right inspector. Nothing calls Platform,
Coolify, an LLM, storage or a customer system. Enter no real credentials.

The prototype is separate from `Platfrom/ui` and has no production effect.
All resource names, metrics, tasks, health and events are fictional fixtures.
Human support metrics, Behaviour customer projections and combined activity
are design concepts requiring actual scoped readers/authority before delivery.

## Screens

- Home: first setup, staging ready and operating.
- Deployments: environment-aware resource canvas/list and inspector.
- Engage apps: app overview, warning-first inspector and configuration.
- Knowledge & data: live sources, documents and indexing details.
- Connections: external systems, storage, vector and binding errors.
- Human support: handover/channel, separate from action approval.
- Action reviews: enabled fixture and honest not-enabled state.
- Behaviours: optional conversational, Smart Brain and specialist profiles.
- Activity & alerts: scoped operations and lifecycle notices.
- Account & team: own business/account membership.
- Creation and production go-live overlays.

Desktop is first-class; mobile is basic navigation/critical details, not full
feature parity. Formal accessibility certification is outside this proposal;
basic native controls/labels, status text and Escape behavior are retained.
The admin/operator console is NOT redesigned.

## Regenerate And Verify

From the repository root, using the installed Platform UI development tools:

```sh
node doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/designs/010_45_customer_console/generate-icons.mjs
node doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/designs/010_45_customer_console/verify-preview.mjs
```

Icons are generated from the existing MUI icon library, not hand-drawn assets or
a remote CDN. Verification opens local files in Playwright, captures page
screenshots and records a scoped report. It sends no HTTP/API requests.

See [the screenshot gallery](gallery.html) after verification and
[the phased plan](../../010_45_CUSTOMER_RESOURCE_CONSOLE_PROGRESSIVE_UI_PLAN.md).
Preview tests are not proof of backend contracts or live customer acceptance.

## Actual-Component Implementation Reviews

`phase-u1` through `phase-u6` are retained build milestones of actual customer UI
components. Use the gallery links to review them over the temporary phone share.
Unlike the original standalone design prototype, module builds require HTTP
serving. They use a fixture-only transport that rejects mutations; no production
API or credentials are present. Enter no real credentials. Earlier milestones
are retained for comparison, not a claim that each contains all later changes.

From `Platfrom/ui`, build/test the latest source review:

```sh
node scripts/build-customer-console-preview.mjs u6
CUSTOMER_RESOURCE_PREVIEW_PHASE=u6 node scripts/customer-console-u2-smoke.mjs
node scripts/customer-ui-scope-regressions.mjs
node scripts/customer-layout-parity-smoke.mjs
npm run build
```

Do not silently rebuild earlier milestones and call them historical proof.
Hosted acceptance and publication are tracked in the
[execution record](../../010_45_CUSTOMER_RESOURCE_CONSOLE_EXECUTION_RECORD.md).
