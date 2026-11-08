# 010.66 Plugin Library And Guided Data Plugin Authoring

Date: 8 November 2026
Status: Backend and UI implemented and deployed on staging/production; hosted authoring and installation passed. New-source indexing/answer/release canary remains unrun.
Source baseline: `75bedeb23`, after the owner-requested rollback of the subsequent plugin-authoring implementation.

## 1. Recommendation

Keep three jobs separate, but connected:

1. **Choose a plugin:** browse capabilities that can help this Smart Brain.
2. **Use a plugin:** provide this Brain's settings and authorized connections.
3. **Define a plugin:** create a reusable, private integration contract for the customer workspace.

Do not put a publisher console, manifest editor, credential form and Brain release controls on one page. A normal user should be able to connect an existing source without ever opening plugin authoring. A technical user can deliberately open a focused authoring workspace without changing the rest of Console.

The first guided creator should support **Data plugins**. Keep the underlying document/editor shell reusable, but do not begin with a builder for every plugin type.

**Terminology:** a plugin definition describes how an integration works; an installation configures its use in one Brain. A connection holds authorized access. None of these is a new customer inventory system or customer business database.

## 2. What The Code Actually Supports

The earlier statement that only a customer-friendly editor is missing was incomplete. The current backend supports a reviewed marketplace publisher workflow, not an already-finished private customer plugin lifecycle.

- `Platfrom/ui/src/brain/BrainCapabilitiesPage.tsx` combines configured actions/services/data in Connected, and lists DATA/ACTION/INTEGRATION catalogue entries in Available. The Data sources link is a filter over that catalogue.
- `Platfrom/ui/src/pages/MarketplacePage.tsx` has the existing generated installation form, technical configuration/reference JSON, shared Brain footer, and staff publisher/submission editor. Reuse these contracts; do not copy them into a second installer.
- `MarketplaceCatalogService.listPlugins()` lists ACTIVE plugins with PUBLISHED versions. `MarketplacePluginEntity` currently has publisher identity, but no customer-owner/visibility fields. Making a UI tab called Private would not make a plugin private.
- `MarketplacePublishingService.submitPublisherVersion()` requires an accessible verified publisher. Validation/publication are admin operations. This is useful for shared catalogue governance; it is not the desired ordinary private customer flow.
- `MarketplaceManifestService` understands Data datasets, entity contributions, knowledge sources, installation inputs, HTTP JSON/SQL/folder synchronization and document-storage connectors. Schema acceptance does not prove an arbitrary customer API can run.
- `DeploymentMarketplaceInstallService` stores plugin version, config and secret references, and recompiles the existing shared deployment draft. The install request is Brain-scoped, not a new per-environment installation API.

### Supported Does Not Mean Universally Plug-And-Play

- **Document storage:** reuse the existing hosted/BYO S3-compatible binding and indexing flow. Document discovery, preview, approval and indexing remain separate from authoring a plugin.
- **HTTP JSON:** start with the currently supported connector contract and its API-key or form-token-exchange authentication. Existing schema supports paths, structured mapping, pagination, schedules and optional notification/targeted reconciliation configuration. Qualify actual executable behavior before exposing each control as supported.
- **SQL:** the manifest supports SQL_QUERY, but implementation inspection found the current loader accepts only the Platform demo connection and executes through Platform's JDBC template. There is no qualified customer SQL binding. Retain imported source for editing, show the adapter gap, and do not create installable private SQL versions. A customer SQL adapter is a separate integration task, not a new plugin type.
- **Mounted folders / packaged seed:** internal/demo choices, not the default customer catalogue.
- **Other protocols:** explain the missing adapter and offer integration help. Do not present a successful generic connection form for an unsupported protocol.

No new AI Fabric release is assumed for this proposal. Raise a specific executable-contract gap if focused qualification proves one; do not preemptively redesign the framework.

## 3. Navigation Without More Menu Clutter

Retain the approved 010.64 Brain shell and the existing customer-wide pages. The rejected 010.65 redesign is out of scope.

### In A Brain

- Knowledge -> **Add knowledge** opens a Data-filtered plugin picker.
- AI capabilities -> **Add capability** opens the same picker with the relevant capability categories.
- Connected remains the home for installation settings. Knowledge remains the home for indexed source status, document approval and sync activity.
- A secondary **My plugins** link and **Create a data plugin** command open the private library/creator. They are not permanent extra Brain sidebar entries.

### Workspace Library

Proposed routes:

```text
/library/plugins
/library/plugins/new/data
/library/plugins/:pluginId/edit
/library/plugins/:pluginId/versions/:version
```

Authoring belongs to the customer workspace, not to one Brain/environment. Keep an explicit return path to the originating Brain, preserving its authorized target/release context. The library has a workspace label and Back to Smart Brain; it must not show a misleading Staging/Production switch for a reusable definition.

The old customer `/plugins?deploymentId=...` installer stays a navigation adapter to the canonical Brain surface, not a second library. Staff `/marketplace` remains the shared publisher/review interface. Staff acting through assigned customer access use the same library/editor and ownership rules as that customer; no new authentication role is needed.

Keep this in the same customer account. Ordinary use is selecting/configuring an integration; authoring is an intentional advanced task for a workspace admin or an authorized technical collaborator. Map authoring permission to the existing account/team model and explicitly assigned staff access. An Advanced-view preference changes presentation only, not permission. Brain edit permission alone must not imply authority to change every reusable workspace definition.

## 4. Screen A: Choose What Your AI Can Learn

**Outcome:** find an appropriate source without knowing internal marketplace identifiers.

Layout:

- Existing Brain breadcrumb, sidebar and environment strip.
- Heading **Add knowledge**. Supporting line: **Connect information your AI can use to answer customers.**
- One search input; origin filters **Recommended / My plugins / All**. Category filters appear only when entering from the broader capability picker.
- A compact list of clearly named options, with purpose, source kind, publisher/owner and compatibility state. Use the same list component, not another catalogue store.
- No connection form opens automatically. Selecting a row shows a restrained detail drawer with outcome, information accessed, required connection and **Set up for this Brain**.
- **Create a data plugin** is a clear secondary command, not a form embedded below the list.
- Demo/internal packages are hidden by explicit catalogue metadata by default; **Show demo sources** is a deliberate filter. Never classify them by matching words in their names.

Customer-facing families:

- **Documents from storage:** hosted/BYO are connection choices, not six near-identical catalogue rows. Keep actual manifest/version identity visible in technical details.
- **Live data from an API:** distinguish named qualified adapters from the generic supported HTTP contract. Do not label it Auto Trader approved.
- **Private customer integrations:** only that customer's saved usable versions.

Family grouping is presentation, not silent migration or substitution of an installed plugin. Existing installations keep their exact definition/version.

**Empty states:** My plugins offers Create a data plugin; a filtered zero result offers Clear search. Loading, permission failure, incompatible version and unavailable connection must not be shown as a successful empty catalogue.

## 5. Screen B: Configure This Brain

**Outcome:** attach an existing integration without exposing authoring complexity.

Use one bounded workspace, not a long narrow form alongside a very wide catalogue. Left task rail, center focused editor, optional compact summary on wide screens. Keep the existing shared Brain footer as the only save command.

### Step 1: Connection

- Display the selected plugin and exact reusable version; version changes are deliberate, not automatic upgrades.
- Choose an existing authorized named connection, or configure the inputs that this plugin genuinely needs.
- Show credential-reference pickers, not raw API keys or secret strings in a manifest field.
- Display where a value is stored: **Brain setting** or **Staging connection**, for example. Do not imply all plugin settings are environment-scoped: current install config is Brain-scoped.
- Use existing target-bound document/provider bindings where available. If a generic named HTTP connection picker is missing, implement its backing contract or show the real declared inputs; do not add a decorative selector.

### Step 2: Information And Access

- Show allowed customer account/resource, collection and selected content fields.
- Explain the outcome: **Your AI can answer from these records. Your source system remains the source of truth.**
- Read-only ingestion remains separate from optional Action plugins. Adding a Data plugin cannot implicitly enable bookings, leads or other writes.
- Surface plugin-specific installation inputs without exposing the entire definition.

### Step 3: Review

- Summarize definition version, non-secret inputs, connection scope, intended indexed collection and changes to this Brain's draft.
- Distinguish **Configuration checked** from **Connection tested**, **Sync completed** and **Searchable evidence verified**. Only show real results with scope/time.
- The shared footer command is **Add to Brain draft** or **Save connection**. It calls the existing install/update/compile path, not both install-save and a redundant draft-save request.
- After save, offer **Review Brain changes**. Existing Updates -> publish/apply/verify remains the only deployment lifecycle.

Saving does not test a provider, index files, apply to production or grant permission. Each side effect remains explicit.

## 6. Screen C: Guided Data Plugin Creator

**Outcome:** a technical customer or integrator defines a reusable source once, without editing the surrounding Console.

Layout:

- Workspace breadcrumb **My plugins / New data plugin**; title **Teach your AI from a new source**.
- Six compact steps in a left task rail. The center shows only one step. A slim right-side **What this enables** preview is optional on wide desktops, omitted on small screens.
- One durable draft buffer, explicit save status and Back/Continue footer. Navigation must retain values; invalid Source edits must never be silently replaced by form defaults.
- A secondary **Source** view offers the complete manifest for advanced users. Import/export live there, not in the primary toolbar alongside every field.
- Explicit **Private to your workspace** ownership label. The mockup uses fictional data and does not call Platform.

### Step 1: Name And Purpose

Fields: display name, one-sentence purpose and content category. Example: **Vehicle catalogue** / **Help customers compare our current stock**. Generate a technical identifier once, with advanced editing before first version creation; do not constantly rewrite it from a changing display name.

Preview: a human-readable option as it will appear in Add knowledge. The purpose describes the source; it is not an instruction to bypass retrieval access or to invent answers.

### Step 2: Source Contract

Choose **HTTP JSON** or **Document storage**. Show SQL as an unavailable advanced contract pending a qualified customer connection adapter. Render source-specific controls, not one universal endpoint form.

For HTTP: supported authentication strategy, relative read route, collection JSON pointer, pagination and required account/resource input. Define the expected connection inputs; actual customer credentials and endpoints are supplied during installation. Offer adapter presets only for contracts that actually execute.

For documents: source binding requirement and existing file/media policy. Do not recreate S3 provisioning, upload or document approval in the creator.

### Step 3: Records And Searchable Content

Use a mapping grid: **Source field / Meaning / Use**. Typical roles are stable record ID, title, searchable description, metadata, citation URL and media URL. Show a synthetic record preview and the intended indexed content separately.

Use a structured mapping contract, not arbitrary JavaScript or semantic keyword rules. Distinguish missing source values from absent mappings. Tenant/customer/deployment authority is stamped from trusted installation context, not editable source data.

### Step 4: Freshness And Boundaries

For qualified HTTP sources: baseline sync interval, pagination completeness, deletion/tombstone behavior and optional verified notification contract. Distinguish baseline recovery from targeted record reconciliation. The user cannot enable webhook support merely by entering a callback URL.

For document sources: the existing preview/approval/replacement workflow and configured retention. Do not offer unsupported timer/event options.

Separately declare who may use the resulting knowledge: public visitors with approved content, signed-in customers, or internal staff. Map this to the existing knowledge-source authentication modes and content/ownership policy, not just a visual badge. Source-system credentials are always server-side. A private definition means private authoring/catalogue visibility; it does not establish the audience of every indexed record. Public document access still requires the existing explicit content-approval rules.

Keep advanced retry/rate policies in a disclosure. No new capacity, draining or infrastructure lifecycle subsystem is part of this work.

### Step 5: Reusable Setup Inputs

Define the small form future installers complete: labels, required status, supported types, safe defaults and help text. Example: **Provider connection**, **Authorized retailer account**, optional **Catalogue category**.

Definition inputs describe slots. Installation values fill those slots. Secrets remain references, and trusted account identity still comes from an authorized binding, not an unverified user-entered display label.

### Step 6: Review And Save A Version

Show purpose, source contract, mapped record/content fields, freshness policy, connection inputs and the generated definition. Backend schema validation returns field-addressable issues; unsupported features remain visible and editable in Source rather than being deleted.

Commands: **Save draft** and **Save reusable version**. A private version that passes necessary schema/trust checks does not require marketplace publisher verification or a human admin approval. Sharing publicly remains the existing separate reviewed publisher process.

After saving a usable private version, offer **Use in this Brain** when a return scope is present, or **Choose a Smart Brain**. This leads to Screen B, not to an automatic install or deployment.

## 7. Screen D: My Plugins And Versions

**Outcome:** reuse work and understand the effect of an edit.

- List only the workspace's own plugin definitions with name, kind, draft/version state, usable version and usage count.
- Select one to open a detail drawer: purpose, current version, installations, **Edit draft**, **Create a version** and **Use in a Brain**.
- Show separate counts for saved installations and applied uses when the backend can prove each. Do not call a draft install a live deployment.
- Definition changes do not rewrite existing installations. Show **New version available**; an installation upgrade has a diff and uses the existing Brain draft/release flow.
- Archive removes an unused option from future selection; it must not silently break existing pinned uses. Do not allow destructive deletion of a version still referenced by installs or release artifacts.

Source/form guarantees apply to both definition documents and installation config/reference documents: preserve unknown supported fields, nested values, explicit nulls, array order, large integers and decimals across unrelated edits. Use a proven structured parser; do not introduce ad hoc JSON parsing or rely on ordinary JavaScript Number for lossless source editing. Unsupported numeric ranges require an explicit qualified policy, not silent rounding. Scope this to configuration fidelity, not semantic AI validation.

## 8. Functionality Gaps To Fill

1. **Private definitions and visibility:** add customer ownership and private/shared visibility to the existing marketplace model, plus mutable private draft/revision handling. Use the current Platform database and marketplace module, not a new service/database. Apply ownership to list/detail/version/install/clone/export and compiler resolution, not only the UI.
2. **Private lifecycle:** customer-authorized draft create/update/check/version operations must reuse manifest parsing and the existing compiler. Do not route private use through the verified-publisher/admin-publication requirement. Shared publication continues to use it.
3. **Catalogue presentation metadata:** add explicit source families, customer/demo audience and adapter capability metadata. Compatibility filtering must be server-aware; do not infer it from title text or hide unavailable services as if connected.
4. **Schema-driven guided forms:** implement supported Data contract adapters, source/form synchronization, field-addressable errors and complete Source access. Reuse the same installation editor and keep one save command per document.
5. **Connection scope clarity:** reuse existing secret and document/provider binding ownership. Qualify how HTTP install fields resolve for the selected target. The current install request has no targetProfileId; do not pretend separate Staging/Production connection values exist in that payload. If generic environment bindings need an extension, add a bounded shared connection-resolution contract first.
6. **Safe preview/test:** authoring can always preview synthetic or explicitly supplied sample data without a provider call. A real Test connection must be a separately authorized, bounded server-side read with target/host restrictions and redacted results. Reuse an applicable existing test operation; otherwise explicitly add one. Never report a mock/schema check as a successful live test.
7. **Concurrency and fidelity:** the existing installation UI fetches before saving, but this is not proof of an atomic compare-and-save. New authoring and reused installation saves need a consistent revision check to prevent cross-tab overwrites, plus focused source/form precision coverage.
8. **Usage and change impact:** expose exact definition-version/install relationships for the library and existing Updates preview. Reuse recorded installs/releases, not a second deployment inventory.

These are Platform gaps. Existing connectors, storage indexing, runtime authorities and release mechanisms should be reused and qualified rather than rewritten.

## 9. What Must Not Change

- The existing approved customer Home, Kits, Engage, account, Support and Smart Brain layouts outside these entry points.
- Staging developer presentation remains independent; it grants no plugin authoring or provider access rights.
- Existing installed definitions, connection credentials, Brain identity and app assignment are never silently replaced by catalogue grouping.
- Source business data stays external. Only derived AI indexing and existing optional AI operational persistence remain deployment responsibilities.
- No unsupported custom executable code upload, universal OAuth wizard, universal SaaS adapter marketplace, public monetization system or AI auto-authoring is required for the first usable creator.
- No phrase matching for AI source decisions, hidden auto-fanout, or relaxed tenant/action boundaries. This proposal changes integration configuration, not AI semantic behavior.

## 10. Bounded Implementation Order After Approval

### P1: Private Contract And Fidelity

Customer-owned definitions/drafts/versions, private install resolution, source/schema round-trip, authorization and atomic save checks. No customer layout changes outside the proposed routes. Use synthetic manifest/compiler tests and two-customer isolation tests.

### P2: Connect And Author

Build one shared picker/installer integration, the six-step Data creator and the small workspace library. Start with S3-compatible documents and the qualified HTTP JSON contract. SQL stays unavailable until a customer-owned adapter exists; demo/seed paths remain out of normal customer selection.

### P3: Real User Journey

Customer creates a private Data plugin -> saves a usable version -> configures it in a Brain -> inspects the draft diff -> applies to staging -> syncs/indexes and checks an answer -> configures production's authorized binding -> publishes/applies normally. Definition upgrade and permission/conflict cases use focused tests; do not expand this into unrelated recovery/release machinery.

Acceptance: no leaked private definitions, no dropped source fields, no copied secrets, no live change from a definition save, no fake test success, one clear authoring/save scope, and a bounded desktop layout with basic mobile navigation. Existing generic and dealership installations must remain usable.

## 11. Review Assets

Clickable, local-only mockup:
`designs/010_66_plugin_library/index.html`.

The mockup shows source selection, all six authoring steps, a Brain installation flow and the private library. Its sample data and simulated success messages are labelled as a design preview. It is not Platform functionality or deployment evidence.

The owner subsequently approved full implementation of this design. No framework release is expected.

## 12. Execution Record

- P1 private contract, ownership, fidelity and concurrency: implemented and verified in the existing marketplace backend.
- P2 picker, three-step installer, six-step creator and library: implemented and browser-verified; no changes to unrelated customer layouts.
- P3 local full-stack qualification: passed real staff sign-in, create/check/version and persistence readback. The existing installer/compiler is covered by focused backend and browser tests. Hosted source sync/indexing/answer/promotion qualification has not run for this new private definition.
- Hosted authoring/installation qualification: passed on staging and production,
  including real persistence, compiled Brain draft readback and customer-scope
  denial. Exact-source healthy backend/UI containers run `ad02bbe55`; both
  databases are on migration 237. No running Brain was changed by these tests.
  All disposable configuration-only test Brains were deleted; definitions were
  archived. This does not close the source-indexing/answer/promotion part of P3.

The new authoring workflow continues to create existing `DATA` manifests. Private
ownership, drafts and versions are lifecycle metadata, not additional plugin types.
Provider checks, indexing and release application are separate operations and must
not be represented as completed by a definition or installation save.

Implementation discovery: private versions must not activate the current
Platform-demo SQL loader, server-folder readers or packaged-resource readers.
Those existing staff/demo contracts are not customer connection authorization.
The first private executable sources are HTTP JSON and bound S3 document storage.
Unsupported imported contracts remain editable but cannot be saved as usable
private versions. This protects Platform database/filesystem ownership; it is
not semantic AI validation or a new plugin type.

Verification and exact publication status: `verification-support/plugin-library-01066/README.md`.
User guide: `Final_Documentation/External_User_Guides/CUSTOMER_DATA_PLUGIN_AUTHORING_GUIDE.md`.

Read-only hosted preflight discovered staging already at an earlier implementation's
236 migration while production remains at 235. Preserve the original V236 bytes
and use V237 for this schema. Both baseline paths, draft retention and unsupported
old private ACTION archival passed real PostgreSQL tests. No live schema was
changed during this implementation session.
