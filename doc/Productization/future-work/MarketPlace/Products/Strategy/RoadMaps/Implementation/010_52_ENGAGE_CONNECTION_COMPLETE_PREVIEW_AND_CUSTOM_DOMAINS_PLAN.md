# 010.52 Engage Connection, Complete Preview And Custom Domains

Date: 2026-10-29.
Status: connection and supported preview LIVE on staging and production.
Custom-domain setup UI is live; positive domain qualification is pending the
owner's DNS records and the subsequent Platform/TLS/runtime-origin steps.
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

- [x] Shared panel from deployment and app inspectors and from app creation.
- [x] Ready deployment -> new app without manual assignment ID.
- [x] New deployment -> configuration assignment -> saved app draft before a
      verified release; readiness prevents premature activation.
- [x] Existing app -> another owned deployment with settings preserved; other
      apps sharing the original assignment remain untouched.
- [x] Same-deployment embed + standalone coexistence; existing promotion path
      retained, not replaced or re-executed by this change.
- [x] Fresh-account/revision and known-real foreign-resource denials retained.

### P1: Preview Parity

- [x] Canonical object-shaped groups and accurate regression fixtures.
- [x] Default/contextual groups, rail, pack defaults, explicit disablement and
      context/attachment switches represented without hidden truncation.
- [x] Conditional auth, history, attachments, sources and handover controls.
- [x] Registered generic/dealership renderers and generic fallback samples.
- [x] No runtime/provider/auth/action requests or persistent visitor state;
      unsaved preview cannot modify active installations.
- [x] Desktop and basic mobile navigation; both appearance modes and delivery
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

- [x] Reviewable backend connection, pack/widget preview, Console integration,
      target-selection and documentation commits.
- [x] Focused backend/UI/pack tests and production builds for touched code;
      no unrelated framework matrix, capacity benchmark or recovery expansion.
- [x] Publish only affected artifacts. Qualify on staging, then production.
      Domain routing may briefly restart the shared frontend; make that explicit.
- [ ] Hosted customer journey: deployment -> app -> preview/save -> domain ->
      activate -> open app; reverse connection management also demonstrated.
- [x] Update customer/developer guides, 010.51 follow-up status and working
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
- [x] C1 implemented and verified.
- [x] P1 implemented and verified for the supported presentation contract.
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

### Hosted Acceptance: 2026-10-29

- CI `40964601088` passed for backend and Console. UI-only follow-up
  `d30c81f5aa6eaf35e546a6860c6d608cba0730c4` preserves an existing target or the
  deployment's explicit bootstrap target. It never substitutes another
  environment when that intent is unavailable. CI `40966252353` passed,
  backend skipped. Final focused Console set: **62 passed**.
- Both backend environments run source
  `afabb4ce954e2c85cb513f817aa08de0e6688e26`, OCI
  `sha256:31348b8252c6d78ce00a841f5738492f52fdde909429c48ebb9b71cd8d099bb7`.
  Staging native job `f8pddo8ls4vudp0mkcqilxbm`; production
  `teks6tnqea27ep7hr2k34b6o`. Healthy, schema217 unchanged, zero restarts;
  environment, routes, placement and resource settings preserved.
- Both Consoles run source `d30c81f5aa6eaf35e546a6860c6d608cba0730c4`, OCI
  `sha256:be1960607c8ad81fdf86c960201a4267d01eaca3382c9cbd5d531b6a24a3b9f6`.
  Staging job `kciu6g0iyf50iafnzr8bq4qu`; production
  `u7eo9x1ba52uus0ax6v9z6g5`. Public routes and source verified; zero restarts
  and existing settings preserved.
- Real Supabase `CUSTOMER_ADMIN` API checks passed on both environments using
  the existing public-signup customer's configuration-only deployments:
  create/replay, changed-command conflict, null-release capability projection,
  two app drafts, early-activation denial, app-only cross-deployment move with
  branding/auth/tools/origins preserved, sibling unchanged, known-real foreign
  deployment denial, then exact app/connection cleanup. No provider deployment,
  business database, vector migration or AI query was triggered.
- CUA staging: empty apps -> name/branding -> Connect deployment -> knowledge
  configuration and staging target -> embedded draft save. Unsaved branding
  survived connection setup. Reloaded saved branding was correct; activation
  stayed unavailable without a release. Exact UI fixture
  `awi_pub_c33514d59e528c7739a4f09420da91b5` and its new connection were removed.
- CUA production: deployment-first panel -> production target/release ->
  existing connection -> new branded embedded app -> ready draft -> explicit
  activation. Manifest200 returned that exact installation and branding.
  Temporary `awi_pub_3fc6604b83a41436aa534a67908c1845` was then disabled, saved
  back to a draft and removed through normal APIs. Its shared consumer was
  neither rebound nor deleted.
- Production preview: actual dealership default/context groups and six context
  tools, registered inventory/detail/comparison/receipt components, conditional
  sign-in and human-support visibility, shared support dialog and synthetic
  request/cancel transitions. Desktop/dark and basic390px mobile Settings/
  Preview navigation/phone rail checked. Sample gestures performed no live
  action or handover. Existing active installations were not edited.
- Public regression: both original app manifests200, foreign origin403,
  inactive custom-domain installation403 and unknown hostname403. Hashed
  widget/pack assets matched their declared SRI. Original Northfield standalone
  and embedded apps remain ACTIVE/ready with unchanged row versions, assignment,
  branding, tools, origins and authentication.
- Domain UI correctly shows the generated copyable TXT value, DNS pending,
  missing Platform CNAME-target warning and disabled activation. Missing TXT
  verification returns PENDING with an ownership error; it is not accepted as a
  routed/verified domain. A successful positive CNAME/TLS/app/origin proof is
  **not** claimed.
- Exact native receipts are under
  `~/.codex/private/loomai-onboarding-01049/{staging,production}/engage-01052-*`.
  Screenshots: `output/engage-01052/`. Local5187 fixture and temporary browser
  tabs stopped/closed. Unrelated worktree changes were preserved.

### Remaining D1 / R1 Work After DNS

The owner has been asked for these Namecheap records on `loomai.pro`:

```text
A      engage                         46.225.162.106
CNAME  engage-canary                  engage.loomai.pro
TXT    _loomai-workspace.engage-canary <exact token from the open owner request / domain panel>
```

1. Confirm actual public DNS. Back up the current shared Engage native
   configuration, preserve unrelated routes, add `engage.loomai.pro` and verify
   its HTTPS health. Shared native app: `rtdzfytxrf80qrc92tm5u2xa`; managed
   service `psv-81bf3c7a` / `loomai-engage-web`.
2. Set production backend
   `PLATFORM_AI_WORKSPACE_STANDALONE_SHELL_BASE_URL=https://engage.loomai.pro`
   through normal Coolify environment configuration, preserving all other
   settings. No runtime/framework code change is needed. Do not pretend the
   target is already configured: it remains missing at this checkpoint.
3. Verify the controlled CUSTOMER_CUSTOM hostname through the existing domain
   worker. Check ownership, native route completion, TLS and the exact app
   identity, not only shared `/health`.
4. The inactive domain canary currently reports missing runtime CORS origins
   for its default and custom addresses. Use the normal deployment draft,
   publish/apply and assignment path for exact origins; verify applicable
   identity redirects separately. Then readiness -> explicit activation ->
   correct app/manifest and anonymous/authenticated access.
5. Remove only the exact qualification app, native managed route and owner DNS
   records after recording proof; preserve the canonical shared hostname and
   original customer apps. Do not globally clean up old customer fixtures.

The domain canary remains DRAFT/inaccessible for this continuation. No DNS,
canonical shared route, backend CNAME-target environment or runtime-origin
change was made before the DNS prerequisite. UI/source work is delivered, but
010.52 as a whole is intentionally not marked fully closed.

### Customer Environment Selector Follow-Up: 2026-10-29

- Customer Connect deployment now groups authorized active target profiles by
  environment: **Staging** and **Production**, rather than displaying separate
  Git-build and immutable-image profile names as extra environments. Provider
  target administration is unchanged.
- Existing connections retain their exact target/release. Release choices cover
  all authorized profiles in the selected environment; selecting one retains its
  exact target ID. Drafts use the configured bootstrap target or a uniquely
  compatible provider/source strategy in the requested environment. Ambiguous
  configuration is visible, never an arbitrary first-profile selection or a
  silent cross-environment substitution. A previous deployment's assignment is
  not reused when choosing a different deployment.
- Source `af2964611576a289dcfce2a7db8fba9faddf1140` committed/pushed.
  Focused Console tests **68 passed**; TypeScript/Vite production build passed.
  Local browser fixture with four technical profiles showed exactly two
  environment choices; production release and pre-release draft commands both
  submitted the exact expected image target. These were synthetic local
  commands, not customer assignment changes. Hosted rollout pending at this
  checkpoint. Custom-domain qualification remains separately pending DNS.
- Hosted follow-up CLOSED: CI `40972913462` passed, backend skipped. Identical
  UI image `sha256:5358c21342d9f54c1a9539ff35f8bafbd10d683484a23615ee72f1a43e8a0d73`
  deployed to staging (`kjqbf5huen2jwqis02jpq0lm`) and production
  (`lj2bnjx9vcf68e9wj4vqd95j`). Native source/health/routes/settings verified,
  zero restarts. Live customer browser selectors on BOTH hosts showed exactly
  Production/Staging. Staging pre-release selection and production existing
  releases/return to the current production release checked, then cancelled
  without saving. Screenshots `output/engage-01052/*-environments.png`.
  No backend, runtime, framework, target-profile or customer-assignment changes.

### Deployment-First Engage Connection: 2026-10-30

- Supersedes exposing Environment/Runtime release as normal setup fields. The
  default customer panel selects only the deployment, then shows a read-only
  environment badge and runtime/draft status. **Change connection** explicitly
  opens the grouped environment and exact release controls for intentional
  staging/production changes or another release.
- Preserve the current app's exact assignment. For new apps, reuse the selected
  deployment's unambiguous active customer assignment, not its historical
  bootstrap target or the newest unrelated release. Aliases with the same target
  and release do not invent an environment ambiguity. Differing assignments or
  unassigned releases in multiple environments require an explicit choice.
- A fresh undeployed configuration uses its configured bootstrap target and
  remains a draft. Invalid/missing assigned releases are shown rather than
  silently downgraded to drafts. Selection is captured for the current app/
  deployment interaction; data refresh does not move it to a newer release.
  Promotion/activation and active-app disable-before-edit remain explicit.
- Source `c9207df7cc959c7755a1c41d002d99ad4706f970` committed/pushed, setup guide
  updated. Focused Console tests **76 passed**, TypeScript/Vite build passed.
  Local browser confirmed zero Environment/Runtime release selectors in the
  default panel, assigned production retained despite staging bootstrap,
  automatic null-release staging draft command, and deliberate alternate-release
  command using that release's exact Git target. Local fixture stopped; no
  hosted app/connection commands were executed. Hosted acceptance pending at
  this checkpoint; custom-domain DNS qualification remains a separate gate.
- Hosted acceptance CLOSED: CI `40977427601` passed, backend skipped. Identical
  Console image `sha256:fd5078e97b388de3f827f2e7fa9fcbfc129792f2fb11675b65550d08706af4ac`
  on staging job `ue3pdtyzy2scaoi5ounm520i` and production job
  `q390hhj91ym1zhqak6u33381`. Native source/health/routes/settings preserved,
  zero restarts. Actual staging new-app journey selected the knowledge
  deployment, derived Staging/Configuration draft, enabled connection and
  showed zero normal environment/release selectors. Advanced controls expanded
  explicitly. No staging connection/app was saved.
- Production Add standalone app reused the existing embedded app's production
  connection. The normal panel showed Production/Verified runtime, zero
  environment/release selectors; **Use this deployment** returned to the editor
  with the same deployment and configuration. Unsaved app cancelled; no new
  app/connection or runtime was created. Screenshots
  `output/engage-01052/{staging,production}-deployment-first.png`. Private native
  receipts `{staging,production}/engage-01052-deployment-first/` under the
  existing private onboarding evidence root. Backend/runtime/framework and
  customer assignments unchanged. No fresh AI or domain qualification claim.

### Domain Automation Follow-Up: 2026-10-30 In Progress

Approved customer journey: Add domain -> DNS -> Verify and prepare -> reviewed
deployment update when required -> explicit activation. Reuse the current app
assignment, existing draft/publish/apply/verify/binding services and shared
frontend; no framework release, new per-customer service or recovery engine.

- [x] Automatically finish owned ROUTING_PENDING domains after the native route
  is applied and TLS passes, without a second Verify click. Disabled/deleted
  installations must not be revived; explicit activation stays separate.
- [x] Review generated origins/identity and any unrelated draft changes, then
  publish the exact reviewed draft and apply using the assigned target/image.
  Connect the verified update only through the normal explicit binding command.
- [x] Finish canonical engage.loomai.pro shared route/HTTPS and Platform setting,
  preserving existing native routes. DNS A/CNAME/TXT are now visible.
- [x] Focused tests, staged rollout, actual custom-domain canary, origin/auth/
  activation evidence. No broad unrelated release matrix.
- [ ] Retire only the exact qualification app and its two temporary hostnames
  after review; it is deliberately retained live for the owner to inspect.

Implementation evidence before rollout:

- **131 focused backend tests passed**, zero failures/errors/skips; **84 focused
  Console tests passed** and TypeScript/Vite production build passed. Review
  guards bind approval to the exact draft, app and assignment; replay uses the
  normal existing published version. The original source draft is used for
  comparison, not compiled prompt artifacts. All apps sharing the runtime are
  disclosed, including apps on other customer connections to that deployment.
- Local browser exercised one Verify, one review, one publish, one apply and
  one explicit connection update. Domain became VERIFIED automatically; app
  remained DRAFT with Activate enabled. No automatic activation command.
  Screenshot `output/engage-01052/local-domain-automation-verified.png`.
- Canonical shared host operational setup passed: native job
  `uobdexflnxnuf30p0c9sxkko`, trusted HTTPS health UP, existing routes/image/
  resources preserved. Platform canonical-target setting saved in production
  Coolify but not active until the backend rollout. Native private receipts
  `production/engage-01052-canonical-host/` under the private onboarding root.
- Source does not release a framework/runtime upgrade, replace an incompatible
  runtime image, automatically select another environment, or activate an app.
  Customer-owned OAuth/email-link redirect settings remain explicit external
  work. Hosted canary/rollout still pending at this checkpoint.

### Hosted Domain Automation Acceptance: 2026-10-30 Passed

- Feature source `25f761030c603e2d7b2329a52bc3e9e179deb977` committed/pushed;
  image CI `40985735294` passed. Backend OCI
  `96e3cb667d5431fcabe49344e5c01f0eccbc760b59f337258da7f29b10bc02b5`
  and Console OCI
  `6d95ab523324a623cd0f14fafdf4701e6249faafafcf25054b160bc53c8860b3`
  are identical on both hosts. Backend jobs stage `d5icj7f69m9h2fzkrqusouu3` /
  production `yaxbgps8b0pi2v29kfsym9j0`; Console jobs stage
  `i20r19z3w4qvtgl3h8h8wx8z` / production `hreuxq6zskm3xewoicxcswqy`.
  Native source/health/settings PASS, zero restarts, schema remains217.
- Canonical target is active and reported in the actual customer domain panel.
  One **Verify and prepare** click verified both qualification addresses after
  native route job `vxetx8q0w6aycvqlbmrfbza1`, generation31/applied31. Audit
  records show two initial verifications and two automatic completion events;
  no second Verify click. The app remained DRAFT and its manifest returned403
  despite trusted custom-domain HTTPS health200.
- Actual customer review showed only Security changes: the two exact origins
  and managed-origin bookkeeping. No unrelated sections. The customer UI
  published/applied version `ver-73bc5b03`, release `rel-4163ea35`, through the
  existing mechanism. Release APPLIED_VERIFIED/PASSED. Original target
  `dtp-coolify-production-behavior` and source `dsa-9302c94d` retained; no runtime
  code/image or framework upgrade. Cold replacement took approximately
  13 minutes including connector restart and bootstrap-credential removal.
- **Use verified update** explicitly rebound the existing shared consumer;
  **Activate app** was then a separate click. Canary
  `awi_pub_be622d27062f1320bfb872757d8bd5d9` is ACTIVE/ready at
  `https://engage-canary.loomai.pro`. Both domains VERIFIED. Original standalone
  and embedded apps remain ACTIVE/ready with their full configuration,
  authentication, origins and row versions preserved; only their shared
  assignment advanced as reviewed. Native unrelated routes and canonical host
  are preserved.
- Custom-origin manifest200, runtime OPTIONS200 with exact allow-origin;
  foreign manifest/runtime-origin403, unknown app host403. Workspace and pack
  SRI digests matched. Actual browser anonymous inventory query returned Aster
  E1 and Morrow C2 with typed vehicle results. Supabase email/password sign-in,
  a separate authenticated inventory query and logout passed on the custom
  hostname. Only three read-only AI queries were sent; no business write.
- Separate observed quality issue: the delivery-policy question returned no
  current indexed policy evidence. This task does not claim a policy/RAG
  quality pass and makes no prompt, routing, indexing or framework workaround.
  OAuth/magic-link redirect configuration remains external-provider work if
  those methods are enabled; this installation uses email/password only.
- Screenshots `output/engage-01052/production-domain-{update-review,active-ready}.png`
  and `custom-domain-{anonymous,authenticated}-inventory.png`. Private receipts
  under `production/engage-01052-domain-automation-proof/`, staged backend/UI
  receipts `engage-01052-domain-auto-{backend,ui}/`. Local fixture is stopped;
  no broad resource cleanup. Canary and temporary DNS intentionally remain for
  owner review, not silently removed. Keep canonical engage.loomai.pro when
  retiring the exact canary later.
