# Support Experience Kit UI

The existing kit catalogue, public detail, setup, overview and edit dialog support
`ai-support-experience`, backed by `support@1.0.0`. Support is an explicit preview
kind, not the adaptive shopping example. Catalogue/setup illustrations use
`supportExperiencePreviewFixtures` from the shared pack. The common Engage
appearance iframe also bundles those fixtures. Neither preview calls AI, creates
requests, configures a service or authenticates a visitor.

## Preferences And Providers

Create/update/summary DTOs carry independent `ticketingEnabled`, nullable
`ticketProvider`, and `humanSupportEnabled`. Both toggles start off in new kit
setup. Provider options come only from `CatalogueEntry.ticketProviders` with
`{code,name,declaredOperations}`. The native option currently has code `desk`.
There is no inferred provider alias or automatic human takeover.

Human-only kits can choose a provider while keeping ticketing off. An existing
human-only kit with a null provider has an explicit Edit path to select one.
Unavailable providers remain visible as unavailable rather than silently being
replaced. Creating a kit saves desired settings only; the existing draft,
publish, apply, verify, assignment and app activation lifecycle is unchanged.

## Managed Support Setup

The overview's Support setup inspector reads/writes:

`/api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/support-space`

GET includes the paired `targetProfileId` and `releaseId`. PUT sends:

```ts
{
  installationRevision: number
  assignmentRevision: string
  expectedVersion: string | null
  enabled: boolean
  humanHandoverEnabled: boolean
  ticketProvider: string
  closedRetentionDays: null
  operationKey: string
  targetProfileId: string
  releaseId: string
}
```

The installation revision comes from its own workspace row. Assignment and
opaque provider versions come from the exact scoped GET; they are never parsed
or inferred. A retry with unchanged input retains the operation key. No mutation
automatically retries. Refresh reads state without replaying a write.

An assigned release is authoritative. Without one, an owner can explicitly
select a verified, active release of this deployment to prepare before app
promotion. This does not rebind the consumer or activate the app. Multiple
candidates do not silently select the latest release. Each delivery channel
uses its own installation identity and revision. Human setup additionally checks
the same target/release's actual Desk channel and links to existing human
support management; it never grants an agent role.

The service derives support space, binding, requester identity, connector
credentials and runtime authority. Console does not collect these values or
add a ticket engine, lifecycle store, connector callback or recovery subsystem.

## Verification

From `max-mode-widget`, build the shared preview and run its typecheck:

```sh
npm run build:preview
npx tsc --noEmit
```

From `Platfrom/ui`:

```sh
node --test scripts/experience-kits-ui.test.mjs scripts/kit-capability-preview.test.mjs scripts/kit-app-address-preview.test.mjs scripts/engage-preview-configuration.test.mjs scripts/ai-workspace-appearance.test.mjs
node scripts/support-kit-ui-smoke.mjs
npx tsc -p tsconfig.app.json --noEmit --incremental false
npm run build
```

The browser smoke uses real UI and the built shared preview with synthetic
owner BFF responses. Evidence defaults to `/private/tmp/loomai-support-kit-ui`;
override with `SUPPORT_KIT_UI_OUTPUT`. It is not proof of hosted authentication,
real support-space provisioning, runtime actions, AI quality or live cutover.
Main owns full backend integration and release qualification. No deployment or
commit is performed by these verification scripts.
