# 010.44 Customer System Integration Boundary Correction

- Created: 2026-10-18.
- Status: implementation in progress from 2026-10-19; parallel source work and local verification, not live-verified.
- Priority: blocks the current 010.36 simulated-dealership customer offer and its hosted apply/promotion.
- Baseline: private Platform branch `Platform-V11`, commit `cab320545cc3dc1900c313ed1e6b40da1e7d0628`.
- Current implementation evidence: [010.44 execution record](010_44_CUSTOMER_SYSTEM_BOUNDARY_EXECUTION_RECORD.md).
- Related: [010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md), [execution record](010_36_PRODUCTIZATION_EXECUTION_RECORD.md), [ownership audit](010_44_CUSTOMER_SYSTEM_OWNERSHIP_BOUNDARY_AUDIT.md), [010.26 dealership demo](010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md).

## Execution Checkpoint: 2026-10-19

- Owner authorized implementation and continuation of independent work, with a
  checkpoint for work depending on this correction.
- In progress, parent: reviewed artifact ownership policy at publication,
  admission and worker boundaries; independent negative architecture tests.
- In progress, Raman: immutable AI-only customer template, external DATA/ACTION
  binding configuration, corrected package/offer helpers and focused tests.
- Source implemented and parent-verified: generic Railway/vector adoption and
  cleanup ownership safety, with local provider-fake and real PostgreSQL
  regressions. Ampere/Hume capacity failures did not discard their partial work.
- Source implemented, Hooke: provider-authoritative action target validation,
  independent demo FK migration and actual sync/indexing readiness;117 focused
  tests pass, including real PostgreSQL. Hosted demo upgrade is still pending.
- Source implemented and locally verified, Einstein: typed customer-provided
  AI-state PostgreSQL and immutable new-candidate credential generations/V200,
  frozen release/recovery scope and sequential generation replacement guards.
  Focused245 regressions and two actual Spring/H2 profile cases pass; hosted
  SQL generation replacement is a separate acceptance gate.
- Parent/Hooke actual PostgreSQL customer workflow and SQL wrapper persistence
  run passes19 cases throughV200. Real offer/invitation/acceptance/bootstrap,
  S3 binding, version publication and isolated target/candidate credentials
  succeed. This is not hosted apply/promotion or real-provider acceptance.
- In progress, Planck: C4 Coolify receipt-checked application/database
  reconciliation and lifecycle actions; no name-based adoption or conflict
  deletion. Parent is integrating final source and verifying independent paths.
- Separate preserved work: cancellation-only HTTP error handling and its tests
  passed20 cases and is committed locally as `00f3852c5`; no push, deployment or
  valid live cancellation is implied.
- Dependency checkpoint: 010.36 hosted dealership apply/promotion and its old
  v12 publisher remain on hold until the corrected package, ownership guards,
  chosen AI operational-storage posture and new capacity evidence pass.
  UI extraction stays last; unrelated dirty UI/site/media edits are preserved.
- Owner confirmed on 2026-10-19 that LoomAI may optionally host AI-state
  PostgreSQL. Make this an explicit new-deployment choice, alongside genuine
  customer-provided AI-state PostgreSQL support. No customer business database
  is provisioned in either posture. Unsupported/unconfigured AI durability
  remains visibly blocked, not silently converted to H2 or vector storage.
- No new live provider mutations, model calls, reservation cancellation or
  storage provisioning is authorized by this checkpoint alone. Existing
  retained resources, stopped workloads, cumulative grant and open Hetzner
  access remain preserved.

## 1. Owner Decision And Required Outcome

LoomAI delivers an AI enablement solution. It does not provision or own its
customers' inventory, CRM, booking, lead-management, commerce or other business
systems as part of an AI deployment. Those systems are integrations.

The simulated dealership website is an example customer application. The Auto
Trader simulator is an example external provider. Neither becomes customer AI
deployment infrastructure merely because LoomAI hosts the demonstration.

An Auto Trader DATA plugin defines advertiser-scoped authentication, stock/media
retrieval, notifications, reconciliation and indexing. ACTION plugins define
authorized external reads/writes. The same integration contract must support an
explicitly selected simulator or real provider; switching must not replace the
AI product with a bundled dealership application.

LoomAI-managed vector storage is an optional service posture; customer-provided
vector storage must remain supported. Customer document/object storage remains
an external, read-only binding. Do not create a LoomAI-managed customer file
storage product as part of this correction.

## 2. Confirmed Defect And Current Impact

The 010.36 customer package reused an all-in-one demonstration dependency graph:

- External provider simulator, initialized and retired with the AI target.
- Dealership demo backend, renamed an engagement receiver.
- PostgreSQL for that backend, including dealership inventory and lead tables.
- Basic Inbox, plus the independently provisioned AI runtime/connector/worker
  and AI runtime PostgreSQL.

This is implemented provisioning/catalogue behavior, not just ambiguous prose.
The graph is loaded from
`Platfrom/backend/src/test/resources/managed-dependency/simulated-dealership-experience-v1-6.template.json`
by `verification-support/productization-01036/simulated-dealership-managed-spec.mjs`.
The receiver uses the dealership demo backend image and declares its own database.
The backend schema contains `dealership_vehicle`, `dealership_lead_request` and
demo sync tables. The latest 1.9 composition retains this graph.

The earlier live read-only audit on 2026-10-18 found zero managed-dependency
graphs on both control planes, and zero releases for `dep-6e29bab9`. Existing
Northfield demonstration services and its database are separate staging
fixtures; production has no corresponding dealership demo service. This bounds
the observed impact but is not a permanent absence guarantee. Re-audit exact
provider inventory before applying any remediation.

The wider audit also found unsafe generic resource-ownership assumptions in
Railway project cleanup and managed-vector adoption/cleanup. Those are separate
from the dealership topology and require their own fixes and verification, as
specified in workstream C. No live customer data loss was established.

Both control planes are verified on `cab320545`, migration 195, public health
UP. The current native-token runtime artifact remains `d2b1a9792` / AI Fabric
0.8.36. No new runtime image or framework release is justified by this topology
correction alone.

## 3. Explicit Ownership Categories

Use typed, explicitly reviewed resource-purpose and ownership contracts rather
than image-name heuristics to separate:

1. **Customer business system:** source of truth for stock, leads, orders,
   bookings and customer workflows. External binding only. No create, clone,
   seed, migrate, reset, stop or delete through the AI deployment lifecycle.
2. **External provider/test fixture:** Auto Trader, another provider, or the
   simulator. Connection/account scope is configured through plugins. Fixture
   hosting and fixture administration have an independent owner/lifecycle.
3. **Derived AI evidence:** vector indexes and any explicitly approved structured
   source projection. Rebuildable from the external source, never the business
   source of truth. Record freshness/provenance; no invented stock or lead data.
4. **AI operational state:** conversations, pending confirmations, handover,
   ingestion work, integration receipts, scheduler and provider-budget ledgers.
   The current runtime requires relational persistence for these capabilities.
   Enumerate that dependency and decide its hosting/ownership explicitly. This
   owner approved optional LoomAI hosting of this AI-state database on
   2026-10-19, explicitly selected during deployment creation. This does not
   authorize business databases or imply that all durable AI state can be kept
   in a vector store.
5. **Optional LoomAI product service:** for example Basic Inbox. This is AI
   conversation support, not a dealership CRM. Keep it optional and interchangeable
   with a configured conversation-channel provider; account for its actual state
   and lifecycle separately.

Do not silently delete operational durability. The reviewed hosting choices are
optional LoomAI-managed AI-state PostgreSQL or customer-provided AI-state
PostgreSQL. Selection must be explicit during new deployment creation and become
part of immutable deployment configuration. It is never a reason to retain the
dealership business DB or make LoomAI-managed SQL mandatory.

The Platform does not currently provide a complete typed external runtime-SQL
binding contract. `CoolifyDeploymentProvider` reconciles `COOLIFY_POSTGRES`, and
productized targets require that durable binding. A JDBC override applied after
managed database provisioning is not BYO storage support. If the owner selects
customer-provided operational SQL, implement the binding, validation and
migration permissions before claiming that posture works. File H2 defaults are
not a substitute for the current durable product contract.

## 4. Correct Target Composition

```text
Customer website / LoomAI Engage
            |
            v
Customer-assigned AI deployment
  runtime + connector + required AI workers/state
  DATA / ACTION / Knowledge / channel plugins
            |
            +--> advertiser-scoped Auto Trader API OR external simulator
            +--> dealership/CRM/booking API where needed
            +--> customer document storage (read only)
            +--> selected vector service (managed OR customer supplied)
            +--> selected human-conversation channel
```

There is no dealership backend or business database inside the AI deployment.
Keep per-message and event traffic deployment-local; do not route it through a
new shared central chat gateway or introduce a standalone bridge by default.
The customer can install the workspace script without implementing a new
backend. This does not remove the need for an existing provider/business API
when a capability must access business records or execute business effects.

### 4.1 Demo And Simulator Remain External

- Retain the existing independent dealership demo application and database as
  a simulated customer system. Its website can keep its own display inventory.
- Bind the AI's Auto Trader DATA plugin directly to the external simulator;
  do not make the dealership website database the authoritative AI stock source.
- Configure advertiser identity, credentials, trusted endpoints and notification
  verification through the existing reviewed plugin/secret/binding primitives.
- Never silently select a simulator when real-provider access fails.
- Do not use the independent website's seeded SQL inventory to validate stock
  selected from the provider. The current demo lead receiver does this and can
  disagree with provider add/update/delete events. Its replacement external
  action contract must resolve and validate the provider's canonical identity;
  the independent website inventory may still remain separate for display.
- A shared fixture requires genuinely isolated advertiser accounts, credentials,
  data and mutation scope. The current configured single-advertiser simulator
  is not automatically a multi-customer service. Add fixture-side account support
  or a separately operated bounded demo fixture; do not bundle a new fixture
  inside every customer AI deployment to avoid solving account isolation.
- Demo controls can add/change fictional stock and trigger the documented signed
  notification path. Such controls belong to the external demo operator surface,
  not a generally enabled customer runtime administration capability.

### 4.2 Data, Reads And Actions

- DATA owns sync configuration, not the provider's stock system. Notifications
  trigger scoped reconciliation; periodic baseline sync is recovery. Indexes and
  approved projections are derived and retain exact owner/source provenance.
- Read actions call the authorized provider or an explicitly configured derived
  projection. A cached projection must identify freshness and must not be
  presented as a fresh authoritative provider read when it is not one.
- Business writes go only to supported, authorized Auto Trader or dealership/CRM
  APIs. Availability and other critical facts must be validated as required by
  the chosen external contract before a write.
- Preserve confirmation and protected-capability authentication. Do not turn
  unavailable actions into apparent successful local lead/booking inserts.
- If an endpoint or entitlement is missing, show that the capability is not
  configured/ready. Do not silently deploy a substitute dealership receiver.

## 5. Parallel Execution Workstreams

### A. Catalogue And Template Split

Own immutable template/plugin migrations and
`verification-support/productization-01036/{simulated-dealership-managed-spec,simulated-dealership-offer,publish-simulated-dealership-package,publish-simulated-dealership-offer}.mjs`.

- First prevent new admission/publication/apply of the invalid customer offer
  through the existing offer lifecycle or a reviewed fail-closed boundary.
  Do not claim an operational pause is a durable Platform policy.
- Publish a corrected AI-only customer template/offer. Preserve immutable history;
  do not edit already applied Flyway migrations or rewrite published versions.
- Remove required simulator, demo receiver and business PostgreSQL from customer
  topology. Keep a separately identified demo fixture composition if useful.
- Replace `MANAGED_SIMULATOR_*` / `MANAGED_RECEIVER_*` dependencies in customer
  defaults with reviewed external plugin binding slots, not fixed Northfield URLs.
- Correct the operative 010.36 sections 10.1-10.4, P1 and R5, plus their customer
  guides. Preserve historical evidence as history rather than leaving contradictory
  mandatory-business-service requirements beneath a new summary paragraph.
- Remove mandatory fixture seeding/reset/retirement prerequisites. Customer
  readiness verifies integration access, not ownership of external applications.
- Recalculate the real topology, resource envelope, admission policy inputs,
  recovery obligations and commercial description. Do not merely decrement a
  displayed database count while the old provisioner still creates it.

### B. Plugin And External Binding Contract

Own plugin manifest definitions/compiler use, connection profiles and focused
connector tests. Agree typed slot contracts with workstream A first.
Allocate distinct new migration versions/files before parallel edits; A owns
template/offer changes and B owns DATA/ACTION manifest changes. Do not edit the
same seed migration or shared compiler contract concurrently.

- Reuse DATA/ACTION/Knowledge/channel plugins and secret references. Do not add
  an Auto Trader-only plugin type or a new custom bridge without a proved gap.
- Make simulator/real-provider selection explicit, advertiser-scoped and bounded
  by trusted server-side configuration. Browser context and LLM output cannot
  choose credentials, endpoints or authorization scope.
- Keep stock sync, media, targeted notifications, baseline recovery, search/detail/
  comparison and typed action presentations working.
- Configure external action receivers where provider capabilities do not exist.
  Preserve visible missing-configuration states and correct result receipts.
- Record any actual generic contract gap before changing runtime/connector code;
  no framework release is presumed necessary.
- Replace receiver-seed initialization evidence with integration evidence:
  scoped source/binding identity, completed reconciliation/indexing work,
  expected records, failures, freshness and provider revision where supported.
  A non-null status body or a fresh timestamp on persisted lifecycle state is
  not proof of successful indexing. Do not introduce a second indexing source
  merely to make a fixture readiness check pass.

### C. Ownership And Lifecycle Enforcement

Own generic managed-dependency validation, pre-effect planning, retirement,
promotion, export/import and capacity accounting; do not modify provider adapters
to special-case dealership image names.

- Adopt a reviewed role/ownership model if current declarations cannot distinguish
  AI resources from external integrations and demonstration fixtures.
- A publisher-supplied purpose label is not proof: pin and review the actual
  artifact's contract, persistence purpose, permissions and lifecycle authority.
  Neither an LLM nor a browser selects the infrastructure ownership category.
- Production customer publication/apply must reject managed customer-business
  resources and implicit fixture hosting. An external binding must not become a
  create/delete target merely because it appears in a dependency graph.
- Validate the boundary before financial allocation or remote effects and again
  against the frozen release plan, including imported/cloned configurations.
- Keep export bundles AI-only: integration configuration and safe references,
  not customer database backups, business services or real provider credentials.
- Retain scoped index/metadata cleanup, customer source preservation, same-tenant
  binding rules and conservative uncertain-outcome accounting. No blind cleanup
  of old databases, collections, volumes or abandoned reservations.
- Keep optional AI services supported. An arbitrary-container allowlist is not
  sufficient evidence of resource purpose/ownership or authority to provision it.

#### C1. Railway Project Ownership Safety

- `cleanupRailwayResources` currently attempts project deletion before reading
  the current project inventory. Provisioning can reuse a project by name.
  A recorded project ID therefore does not prove exclusive lifecycle ownership.
- Replace project-delete-first behavior with ownership-checked resource cleanup.
  Delete only exact resources with durable creation/ownership evidence. Preserve
  external/customer resources and resources referenced by other active targets.
- Whole-project deletion requires explicit exclusive ownership evidence and a
  complete current inventory proving that no unrelated resources/environments
  remain. Unknown inventory or uncertain ownership blocks deletion, not safety.
- Add mixed-project, additional-environment, foreign database, retry, uncertain
  provider outcome and historical-record tests. An admin's authorization to
  delete the AI deployment is not proof of ownership of every project member.

#### C2. Vector Resource Adoption And Retention Safety

- Existing Pinecone indexes and Qdrant clusters can be reused by name and then
  recorded as managed resources without a creation-ownership check. Hard delete
  later dispatches root deletion; Pinecone cleanup can disable delete protection.
- Extend the same ownership check to collection-level reuse and every managed
  vendor path. Existing dimensions, region, credentials or a matching name prove
  compatibility/access, not authority to own or delete that resource.
- Reconcile an existing owned resource only against a durable, exact provider
  creation receipt and its customer/tenant/deployment/resource scope. An
  interrupted create requires explicit reconciliation of that receipt; do not
  claim ownership from name collision after a crash.
- An external resource must use an explicit retained-external binding, or the
  operation must fail before remote effects. Do not automatically convert it
  into a managed root, rotate its keys or change its deletion protection.
- Root and child ownership are independent: owning a scoped collection does
  not imply authority to delete its shared cluster. Check cross-target references
  and retention policy before every delete and secret/key retirement.
- Preserve the newer retained-external native-token path; do not silently
  reclassify the approved Free-01 or production cluster into an old managed mode.
- Test preexisting unowned resources, shared roots/collections, interrupted
  creation, repeated apply, missing receipts and deliberate deletion denial.
  Use local fakes/test fixtures, never customer resources, for destructive tests.

#### C3. Explicit AI Operational Storage Contract

- Inventory the actual runtime, connector, worker and optional Inbox state needs.
  Support the owner's approved optional hosted AI-state database and the
  customer-provided alternative as explicit creation choices. Do not infer
  customer business-data authority from either choice.
- For customer-supplied SQL, implement typed connection/secret references,
  target and tenant isolation, a dedicated database/schema boundary, safe
  connectivity checks and explicit schema-migration permission. Authority to
  migrate the approved AI schema is not authority to create/drop the server,
  database, customer schemas or business tables.
- Generation, promotion, retirement, recovery, admission/capacity and UI readiness
  must agree on that choice. Never provision a managed PostgreSQL instance first
  and then call a JDBC override an external binding.
- Keep an unconfigured or unsupported durability posture visibly blocked. Do not
  move persistent state into transient files or vector documents as a workaround.

#### C4. Coolify Application And Managed SQL Ownership Safety

- Follow-up source review found exact-name application/database adoption and a
  conflict-recovery path that can delete a conflicting application outside the
  intended scope. These are ownership defects, not useful retry behavior.
- Require durable creation evidence and exact provider location plus customer,
  tenant, deployment and target identity before reuse, update or deletion.
  A historical handle, a matching name, compatible SQL settings or a409 response
  does not establish ownership. Never change a foreign database's password.
- An uncertain create or mismatched live UUID/location requires explicit
  reconciliation. It may not be repaired by deleting a candidate or silently
  adopting another resource. Preserve existing retained-external SQL/vector
  bindings and independent demo/provider infrastructure.
- Verify new creation, exact owned retry, foreign/historical handles, same-name
  collisions, missing readback and ambiguous provider outcomes with local
  provider fakes. Review cleanup/retirement evidence separately before rollout.
  Source implementation is assigned independently; hosted acceptance remains open.

#### C5. Shared Platform Product Services Have Separate Ownership

- The older shared-service path in `PlatformManagedProductProvisioningService`
  is separate from the new per-deployment dependency graph. Review confirmed
  name/FQDN adoption, retargeting with a retained source UUID, destructive
  recreation/conversion and metadata-only lifecycle authority in that path too.
  Staff authorization does not establish resource ownership.
- Use a service-scoped creation/deletion journal keyed by actual service registry
  identity, target and live provider placement. Do not fabricate a customer or
  deployment scope to fit the per-deployment ledger. Future managed creations
  must have durable intent before remote effects and exact live readback.
- Historical metadata stays intact but is not backfilled into creation proof.
  Unproven bindings, collisions, uncertain creation/deletion and bound-resource
  retargeting fail visibly before effects. Never delete a discovered application
  to recover from a missing UUID or convert its build type.
- Shared lifecycle/reconcile/scale/recreate/decommission must use the same
  ownership checks. Summary/history/log reads must not rebind Railway resources
  or create a domain. Unsupported legacy Railway mutations remain blocked;
  provider errors are not permission for name-based discovery or project deletion.
- Check foreign registry, deployment graph, handle, release and vector references
  before mutation/deletion. Exclude only the exact owning registry row. Preserve
  existing running fixtures, secrets and rows, and add positive future-owned
  creation/lifecycle plus negative collision, stale identity, retargeting and
  uncertain-outcome tests. No live destructive reproduction is required.

### D. Verification And Product Configuration

Own source/HTTP/browser/live verification and functional configuration changes.
Reuse the existing customer/admin editors; shared-editor extraction remains last
and independently reversible. Do not mix UI extraction into this correction.

- Build an approved external fixture account and bindings independently, then
  create the AI customer deployment using the normal customer path.
- Demonstrate script/standalone bootstrap, anonymous and protected actions,
  inventory search/detail/compare, images, policy RAG, multi-page attachments,
  handover, cancellation and documented outage behavior.
- Prove add/update/delete provider notifications reach the derived index without
  changing the customer website database. Provider outage must not fall back to
  seeded inventory or an invented successful write.
- Test two customer scopes, forged advertiser/endpoint/context and stale/wrong
  binding versions. Do not reuse Northfield's identity as a default for all users.
- Export, promote, retire and retry the AI target while proving external fixture,
  customer source files, business records and externally owned services survive.
- Reconcile provider inventory: no dealership backend/business DB/simulator is
  created by the customer AI apply, even after retries, import or promotion.
- Add an independent negative architecture gate, not another test that derives
  both expected and actual topology from the same all-in-one demo fixture.
  Assert provider create calls for business systems remain absent, while explicitly
  selected AI-internal services still work. Verify reviewed resource-purpose
  contracts, not service/image-name keywords.

## 6. Acceptance Gates And Commit Boundaries

1. **Containment and ownership contracts:** invalid offer blocked; corrected
   categories/slots reviewed; tests fail for the former bundled graph.
2. **External integration and AI-only composition:** plugin/configuration tests,
   real PostgreSQL migrations and generated-plan assertions pass. Normal AI
   persistence and optional services are not accidentally removed.
3. **Lifecycle, import and promotion:** external-resource survival and no-business-
   provisioning tests pass; no unowned resource becomes a deletion candidate.
   Railway mixed-project and vector adoption/retention regressions pass in
   separate commits; uncertain historical ownership remains retained/blocked.
4. **Hosted external-fixture canary:** exact immutable source/images/bindings,
   actual chat/actions/sync/HITL and provider inventory evidence. Only this gate
   can claim working integration; passing a seed/reset canary is insufficient.
5. **Customer UI functional support:** existing views expose the required minimum
   bindings and readiness without duplicates. UI extraction still follows the
   complete non-extraction gate in a separate reversible commit.

Use separate commits for the contracts/catalogue, binding/compiler changes,
Railway cleanup, vector ownership, the selected operational-storage contract,
generic lifecycle guards and verification/docs. Catalogue workstream A, plugin
workstream B and lifecycle workstream C can run in parallel after agreeing the
typed contracts; coordinate the shared cleanup-service edits within C rather
than letting Railway/vector tasks overwrite one another. Scale testing to affected Platform and
connector contracts. Do not run an unrelated framework release matrix or publish
another AI Fabric release for a private-template correction.

## 7. Handoff Safety And Current Open Work

- Unrelated dirty UI/site/media files must not be staged, reset or overwritten.
- A cancellation-only HTTP parsing/status fix remains locally frozen in
  `AdmissionUnusedReservationCancellationController.java` and
  `AdmissionUnusedReservationCancellationHttpTest.java`. Its sidecar 61 tests
  passed, including 20 embedded HTTP cases; it is not committed or deployed.
  Keep that separate from this architecture correction.
- No valid cancellation was sent. The v11 physical reservation remains held;
  no runtime release or model allocation was created. Never release it through
  ad hoc SQL or repurpose its reviewed topology for a different manifest.
- Prepared `/private/tmp/01036-publish-v12-staging-candidate.mjs` and
  `/private/tmp/01036-run-v12-customer-configuration.mjs` target the invalid graph.
  Do not run them unchanged. Old topology reviews and admission measurements do
  not certify the corrected package.
- Existing staging model campaign remains cumulative and unchanged: USD12 total,
  200,000 tokens, 50 interactive requests, including embeddings. Do not reset it
  or assume it authorizes new production/provider resource changes.
- Preserve Free-01, the separately approved production vector resource, existing
  external indexes, source data and all retained stopped workloads. No cluster,
  user/key/role, bucket, database or firewall mutation is authorized by this plan.
- Keep Hetzner access as instructed by the owner until the complete implementation
  is finished. Consult ignored private material without copying secrets here.
- The correction is not complete until generated plans and live inventories prove
  the boundary. Renaming the receiver, changing documentation or moving business
  tables into another LoomAI-owned database is not an acceptable fix.

## 8. Effort And Completion Claims

Initial topology-only estimate was roughly 2-4 focused engineering days plus
hosted verification. The audit adds separate generic Railway/vector safety work,
and a complete BYO operational-SQL contract would add another implementation
scope if selected. Do not represent the initial estimate as the schedule for
all audit findings. Agree storage ownership and size each workstream first.
Reuse AI runtime, widget/packs, authorization, budget accounting, indexing,
generic connector and managed-service machinery where their boundaries are sound.

Real Auto Trader production access/entitlements are external prerequisites and
are not established by simulator evidence. Do not describe the corrected demo
as a verified real-provider production integration until separately proven.
