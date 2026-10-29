# 010.52 Engage Connection, Complete Preview And Custom Domains

Date: 2026-10-29.
Status: source implemented and pushed; hosted rollout/acceptance in progress.
Custom-domain acceptance awaits the owner's DNS records.
Predecessors: [010.50 delivery setup](010_50_ENGAGE_EMBEDDED_AND_STANDALONE_CUSTOMER_SETUP.md)
and [010.51 appearance preview](010_51_ENGAGE_CUSTOMER_APPEARANCE_AND_VISUAL_PREVIEW.md).

## 1. Customer Outcome

A customer can start from either a deployment or an Engage app, connect the two
without understanding consumers or entering assignment IDs, configure the
workspace while seeing its actual supported presentation, and publish at a
Platform address or a verified customer domain. Website embed and standalone
delivery can both use the same deployment.

Keep this a bounded Engage product improvement. Reuse the current consumer,
installation, experience-pack, verified-release, domain and publication models.
Do not introduce another assignment database, provisioning/versioning engine,
background recovery system, runtime service or framework release.

## 2. Code-Checked Starting Point

- `CustomerResourceSurface.tsx` has deployment/app inspectors and connected-app
  reads. `CustomerProductTasks.tsx` links to the technical assignment section.
  `CustomerDeploymentConsole.tsx` currently exposes manual Assignment ID,
  Create staging assignment and Use latest verified staging release controls.
- `AIWorkspacesPage.tsx` selects a consumer and requires a deployment, target
  and `boundReleaseId` before loading configurations. The shared command guard
  in `customerEngageCommands.ts` applies that release requirement to every app
  command. This makes pre-release draft setup unnecessarily difficult.
- `CustomerStagingConsumerActivationService.createAndActivate` already accepts
  a null release, creating a deployment/target-bound configuration assignment.
  `AIWorkspaceInstallationService` requires a deployment-bound active consumer,
  not an activated runtime for every draft. Activation separately checks actual
  readiness. A completely deployment-less persisted installation is not an
  existing contract and is not required by this plan.
- The staging-assignment endpoint is restricted to the bootstrap staging target;
  it must not be presented as an arbitrary production-binding API. Existing
  production activation and consumer-binding services remain authoritative.
  Cross-deployment staging activation is deliberately rejected; an app move
  must change its installation connection through the existing owned paths.
- The generic widget and dealership pack use the canonical object-shaped
  `toolGroups`: `initialScope`, `default`, `contextual`. The new appearance
  helper incorrectly reads an array with `scope`, and its fixture repeats that
  mistake. Valid configured tools can therefore disappear from the preview.
  The real widget and saved configuration retain their tools.
- The current preview only renders simplified default tool labels, capped at
  eight. It does not resolve pack defaults or mirror contextual tools, rail,
  action renderers, authentication, attachments and handover configuration.
  Its human-support sample can be selected independently of enabled features.
- Customer-domain ownership, routing and TLS services exist. Native production
  inspection found no configured standalone shell CNAME target, including in
  Spring application JSON. The shared Engage service is active at
  `https://loomai-engage-web.46.225.162.106.sslip.io`. The qualified Northfield
  hostname is a `PLATFORM_DEFAULT`, not customer-owned-domain proof.

These findings limit the earlier branding-preview completion claim: 010.51 is
closed for its recorded scope, not for a configuration-complete workspace or
customer-domain acceptance.

## 3. Shared Connect Deployment Panel

### Entry Points

- Deployment details: **Connect Engage app**, offering a new app or an existing
  app owned by the current customer. Preselect this deployment.
- Engage creation/details: **AI deployment** with **Connect deployment** or
  **Change deployment**. Show recognizable deployment names, environment and
  readiness instead of requiring a Consumer ID.
- Reuse one panel and selection/command implementation from both entry points.
  Keep connected-app lists and Open deployment/Open app navigation consistent.
  Keep technical assignment IDs and revisions in Advanced.
- Replace the current dead-end warning with a direct scoped setup action.
  Preserve appearance edits when opening or returning from connection setup.

### Connection Behaviour

1. Read current-customer deployments and their target releases. Display Ready,
   Not deployed or Verification needed honestly; do not select a different
   environment merely because the requested one is unavailable.
2. Resolve or create the existing assignment on explicit Connect. Generate its
   internal identifier once for the command; customers supply an app name, not
   a technical lookup key. Do not silently pick an ambiguous existing alias.
3. Reuse an existing assignment only when customer, selected deployment and
   selected environment match the user's intent. Adding the other delivery
   channel may intentionally share the existing connection.
4. For an app-only move, do not rebind a shared consumer and silently move other
   apps. Use a destination assignment and the existing installation update.
   Show the affected app(s) before confirmation. Ordinary production promotion
   retains its existing explicit shared-assignment behaviour.
5. Preserve pack, delivery, authentication, domains, branding, tools and
   integrations when connecting an existing app. Report incompatibility as a
   concrete readiness issue; never downgrade private/sign-in behaviour.
6. Existing ACTIVE installations retain the explicit disable/edit/verify/
   activate lifecycle. Do not stop or replace a runtime to connect an app.

### Draft Versus Activation

- Customers can edit appearance and inspect a sample before connecting.
  Persist a new draft once an owned deployment/target configuration assignment
  exists; a verified release is not a prerequisite for that draft save.
- Split the existing UI command guard by operation. Configuration commands
  still require a fresh current actor, ownership, active assignment,
  deployment/target and revision. Activation/runtime access additionally
  require the exact applied, verified release and normal readiness.
- Resolve starter or existing pack configuration without making a release
  necessary for preview or draft authoring. Templates remain bootstrap defaults,
  not lifetime restrictions on owned configurations.
- Reuse customer staging creation/configuration, production activation,
  installation update and security-projection services. If a target-aware
  composition is missing, add only a thin application-service operation using
  these services. Do not duplicate authorization or deployment versioning.
- Keep normal idempotency and stale-account/revision checks. If assignment
  creation succeeds but app save fails, expose the existing pending connection
  for continuation; do not secretly create another alias or invent a recovery
  state machine. No background resource provisioning occurs here.

## 4. Configuration-Complete Supported Preview

### Resolve The Same Presentation As The Live Pack

Fix the canonical tool-group reader and its inaccurate tests first. Then share
pure presentation/default resolution with the actual registered experience
pack, so preview and live mounting do not maintain different defaults. Reuse
the widget's tool groups, rail, composer and registered presentation components.
Only introduce a small resolver where it removes this demonstrated duplication.

The generic widget must remain domain-neutral. Dealership names, sample
vehicles, comparison cards and receipts belong to the dealership pack's preview
fixtures/renderers, not generic Console or widget logic. The same contract must
work for generic knowledge and future Shopify packs.

### Coverage Required

- All configured default and contextual tools: labels, icons, order, active
  scope, context label and attachment-dependent availability. Do not silently
  truncate the tool set; use the actual wrapping/scrolling behaviour.
- Tool rail: configured items, icons, tones, collapsed state and supported
  open-tools, open-documents and prompt affordances.
- Pack defaults when configuration omits optional tool groups/copy; explicit
  empty or disabled configuration must not accidentally re-enable tools.
- Launcher, dock, full MAX and standalone presentation; branding, supported
  typography/radius and copy, suggestions, sources, history and enabled
  attachment/current-page controls.
- Authentication visibility from the selected interaction/auth profile:
  anonymous, login-required and public-with-protected-capabilities. Simulate
  signed-in/out states without invoking Supabase or carrying its config.
- Handover controls only when enabled by the resolved deployment/installation
  contract. Offer AI, pending and human sample states, with disabled/safe
  transitions. A sample must never enable actual handover or Human Review.
- Registered action-result renderers and the real generic fallback. Provide
  bounded static fixtures for supported result kinds, confirmation and receipt
  states. Known pack renderers use their actual components, not a second mock
  card implementation. Customer actions/queries are never executed.

"Complete" means all presentation fields and registered components supported
by the current contract, not arbitrary third-party JavaScript or proof of an
integration's execution. Unsupported renderers/settings must be visible as a
preview limitation, not silently replaced with an apparently working feature.
Record new extension types requiring a fixture instead of expanding this work
into a general plugin execution sandbox.

### Isolation Remains

- Keep the existing sandbox and no-connect/form policy. Parent-to-preview data
  is allowlisted presentation, capability visibility, approved renderer IDs and
  synthetic state only. Never forward credentials, tokens, private assertions,
  real conversation data, action queries/endpoints or integration configuration.
- Use bundled, trusted pack renderers; do not load customer-supplied scripts.
  Public images can load under the existing policy and use a visible/familiar
  fallback if unavailable.
- Sending, sign-in, confirmation and handover gestures show local preview
  feedback or move between samples. They issue no real operation.
- Preview-only state never persists as deployment configuration, activates an
  installation or changes the host website's theme.

## 5. Customer-Owned Domains

### Platform Prerequisite

Select a stable canonical hostname for the existing shared Engage frontend;
`engage.loomai.pro` is a proposed name, not a claim that it is configured.
Verify its DNS, native route and HTTPS, then set
`PLATFORM_AI_WORKSPACE_STANDALONE_SHELL_BASE_URL` to that exact HTTPS URL.
Preserve `apps.loomai.pro` and unrelated shared-service routes. No new frontend
or runtime is deployed per customer domain.

Use authorized DNS access where available. If a DNS write needs the owner,
request the exact record and continue independent work; do not bypass ownership
verification or mark the domain complete. The existing sslip service address
remains a service endpoint, not the intended customer-facing canonical target.

### Customer Flow

1. On a standalone app, add an exact hostname such as `ai.dealership.com`.
2. Show the actual generated TXT ownership record and stable CNAME target with
   copy controls. Do not show a guessed token or an empty routing target as
   usable instructions. Explain whether the missing step belongs to the
   customer (DNS) or Platform (shared frontend configuration).
3. Customer creates both DNS records. Keep DNS pending, ownership pending,
   route/TLS pending, verified and actionable error states understandable.
4. **Verify domain** uses the existing ownership verifier, leased route worker
   and pinned HTTPS checks. A queued route is not a verified domain.
5. Project the exact new origin and required identity settings into the normal
   deployment draft. Publish/apply only if runtime configuration needs changing;
   no new framework/runtime code is expected.
6. For email/OAuth redirects, the customer configures the identity provider's
   allowed redirect URLs separately. Email/password does not justify claiming
   OAuth or magic-link redirects are already verified.
7. Refresh readiness and explicitly activate. An existing active app must be
   disabled before changing its domain configuration under the current lifecycle.
8. Confirm the custom hostname serves the correct installation and assignment,
   not merely a successful shared `/health` response. Keep the default address
   unless the customer explicitly removes it through a supported operation.

Website embedding on an existing customer website does not require CNAME/TXT
changes. It uses the script, exact allowed origin, runtime CORS and applicable
identity redirects. Do not confuse embedded origin configuration with standalone
custom-domain hosting.

## 6. Phases And Acceptance

### C1: Connection Journey

- [ ] Shared panel from deployment and app inspectors and from app creation.
- [ ] Ready deployment -> new app without manual assignment ID.
- [ ] New deployment -> configuration assignment -> saved app draft before a
      verified release; readiness prevents premature activation.
- [ ] Existing app -> another owned deployment with settings preserved; other
      apps sharing the original assignment remain untouched.
- [ ] Same-deployment embed + standalone coexistence and normal promotion.
- [ ] Fresh-account/revision and known-real foreign-resource denials retained.

### P1: Preview Parity

- [ ] Canonical object-shaped groups and accurate regression fixtures.
- [ ] Default/contextual groups, rail, pack defaults, explicit disablement and
      context/attachment switches represented without hidden truncation.
- [ ] Conditional auth, history, attachments, sources and handover controls.
- [ ] Registered generic/dealership renderers and generic fallback samples.
- [ ] No runtime/provider/auth/action requests or persistent visitor state;
      unsaved preview cannot modify active installations.
- [ ] Desktop and basic mobile navigation; both appearance modes and delivery
      channels. Record unsupported extensions explicitly.

### D1: Domain Qualification

- [ ] Canonical shared hostname, public DNS/TLS, configured CNAME target and
      preservation of existing default apps verified.
- [ ] Customer UI exposes exact records and correct pending/verified states.
- [ ] One controlled `CUSTOMER_CUSTOM` hostname passes actual TXT -> routing ->
      TLS -> correct app/manifest -> readiness -> activation on hosted services.
      Wildcard `PLATFORM_DEFAULT` success is not a substitute.
- [ ] Custom origin works with configured anonymous/authenticated access;
      required runtime projection and applicable redirect settings verified.
- [ ] Wrong/missing TXT and unknown/foreign/inactive host access remain denied.
- [ ] Remove only the exact temporary qualification app/domain/records and
      verify existing customer apps still work. Retain normal audit history.

### R1: Release And Closeout

- [ ] Separate reviewable commits for connection, preview and domain changes.
- [ ] Focused backend/UI/pack tests and production builds for touched code;
      no unrelated framework matrix, capacity benchmark or recovery expansion.
- [ ] Publish only affected artifacts. Qualify on staging, then production.
      Domain routing may briefly restart the shared frontend; make that explicit.
- [ ] Hosted customer journey: deployment -> app -> preview/save -> domain ->
      activate -> open app; reverse connection management also demonstrated.
- [ ] Update customer/developer guides, 010.51 follow-up status and working
      context with source/image identities, screenshots and precise limitations.

Each phase reports source completion separately from hosted acceptance. Do not
close custom domains for DNS prerequisites alone, or full preview for branding
and one simplified tool list alone. Keep unresolved hard extension cases in this
plan instead of broadening the basic product journey.

## 7. Implementation Locations

- Console: `Platfrom/ui/src/components/customer/CustomerResourceSurface.tsx`,
  `CustomerProductTasks.tsx`, `customerEngageCommands.ts`,
  `Platfrom/ui/src/pages/AIWorkspacesPage.tsx`, `aiWorkspaceAppearance.ts`,
  `EngageAppearancePreview.tsx` and their focused tests.
- Assignment reuse: Platform backend `tenant/service/PlatformCustomerConsumerService.java`,
  `customerselfservice/service/CustomerStagingConsumerActivationService.java`,
  `CustomerStagingConsumerConfigurationService.java`, existing production
  activation services and `aiworkspace/service/AIWorkspaceInstallationService.java`.
- Presentation: `max-mode-widget/src/entries/preview.tsx`, existing widget
  tool-group/rail/message/presentation components and `experience-packs/` pure
  configuration resolution plus pack-owned fixtures/renderers.
- Domains: backend `aiworkspace/service/AIWorkspaceDomainService.java`,
  `AIWorkspaceDomainRouteService.java`, `AIWorkspaceDomainVerifier.java`,
  current Console domain UI and managed shared Engage service configuration.

## 8. Progress

- [x] Consolidate agreed UX and code-check the actual reuse points/gaps.
- [ ] C1 implemented and verified.
- [ ] P1 implemented and verified.
- [ ] D1 implemented and verified.
- [ ] R1 hosted journey, exact cleanup and documentation complete.

No application, deployment, DNS, assignment or authentication setting was
changed when creating this plan.

### Execution Checkpoint: 2026-10-29

- Backend source `51e036467`: atomic owned connection composition reuses the
  existing consumer/binding history and exact target authorization. Null release
  supports configuration-only connections; a supplied release still requires
  normal verified binding. A read-only capability projection shares the public
  handover projection, including intake and allowed user transitions.
- Preview source `16009ccc3`: canonical object groups, shared live pack
  presentation resolvers, all supported tool groups/rail, registered renderers
  and pack-owned fixtures, auth/history/sources/attachment/handover samples.
  No live controller, credentials, requests, storage or customer scripts inside
  the opaque preview frame. Page attachment remains dock-only, matching MAX.
- Console integration source `afabb4ce9`: shared connection picker from both
  resource sides, draft/activation command split, owned destination connection
  rather than shared consumer rebind, appearance preservation, Advanced IDs,
  complete supported preview props and copyable domain instructions.
- Focused verification: 183 backend tests and 61 Console tests passed. Widget,
  both packs and Console production builds passed. Existing size/runtime-config
  build warnings remain; no new framework release or broad matrix was run.
- CUA local acceptance: deployment-first picker, exact environment/release,
  configuration-only connection plus draft save, default/contextual switching,
  more than eight configured tools, retained attachment while browsing and
  conditional support samples. Synthetic fixture is
  `verification-support/engage-01052/local-fixture.mjs`.
- CI `40964601088` publishes only affected backend/Console images from
  `afabb4ce954e2c85cb513f817aa08de0e6688e26`; its final result and hosted
  acceptance are not yet recorded here.
- Separate inactive production domain-qualification app created:
  `awi_pub_be622d27062f1320bfb872757d8bd5d9`, custom host
  `engage-canary.loomai.pro`. The original Northfield apps/assignments were not
  changed. Requested exact A/CNAME/TXT records from owner; no ownership, TLS,
  runtime-origin or activation success is claimed until those checks pass.
- Preview limitations remain explicit: automatic page inference uses a safe
  synthetic page; private adapter responses and custom scripts are not invoked;
  unsupported renderer/mapping extensions need bundled fixtures. Runtime
  handover configuration is authoritative, not an ignored pack-only flag.
