# Platform UI Entry Points

Document preparation and RAG evaluation (010.72) belong to the selected source
under Brain Knowledge -> Documents, using Prepare / Index / Evaluate views and
the existing shared assistant. Read DOCUMENT_INTELLIGENCE_IMPLEMENTATION.md for
the source-scoped task, explicit evaluation allowance and release qualification.
Do not infer that a documented candidate feature is deployed.

The customer-wide 010.65 design expansion was rejected and rolled back on
2026-11-07. Keep the earlier approved customer pages and the 010.64 Smart Brain
workspace. Do not restore the removed customer shell/task redesign or its design
guide without a new owner request.

Brain-scoped operations have one canonical home under `/smart-brains/:brainId`:
Knowledge (documents/live data/storage), Human support and Action reviews. Do not
re-add their customer global-menu selectors or Manage wrappers. Legacy links are
navigation adapters, not parallel editors. Staff-wide administration stays
separate; sharing the workspace never grants customer review authority. See the
010.64 scoped Knowledge/Connections/Reviews proposal and its verification record.

Brain configuration also has one canonical navigation home: AI models in the
Brain sidebar, embedding/vector settings in Knowledge -> Search settings, and
runtime/provider/recovery/diagnostic/JSON tools under pinned Advanced children.
Action access and webhooks stay with AI capabilities, not empty Settings pages.
Old `/settings/...` URLs are redirects only. Reuse the existing provider/action
editors and draft-save contracts; never mount a second copy to add an entry point.
See `verification-support/brain-settings-navigation/README.md`.

Queued planning only, 23 November 2026: 010.74 proposes moving staff/admins into
these same workspace screens and extending delegation to existing customer
accounts. Implement only after the pending 010.73 work closes or receives an
explicit owner deferral. It does not make staff a customer session, authorize a
UI-only role bypass, restore 010.65 or change current access behavior. Read
`doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_74_SHARED_CONSOLE_WORKSPACE_MEMBERSHIP_AND_STAFF_UI_MIGRATION_PLAN.md`
for workspace scope, membership, page parity and retirement requirements.

Search settings > Answers & sources is the single owner for tool/knowledge
cooperation, bounded multi-source search and Advanced read-planning budgets.
Keep inheritance visible, preserve unknown/current values with focused draft
merges, and retain optional Advanced JSON. Do not add an editable
`ragRequiredWithReadActions`: that is a derived diagnostic. See
`SMART_BRAIN_CONFIGURATION_FORM_COVERAGE.md` for field coverage and limits.

Customer-owned credentials have one canonical home at
`/smart-brains/:brainId/secrets` (Keys & secrets), with links from AI models and
Search settings. Reuse the same page for customer-delegated staff; do not expose
the global Platform Security catalogue to customers. Brain administrator access
and current operator delegation protect the write API. Stored values are never
read back or sent to configuration assistance; only scoped references are used.
Target-specific storage/vector bindings remain with their existing workflows.
See `verification-support/customer-secrets/README.md`.

Build with AI is available across the Smart Brain workspace and opens on entry.
Keep its close control and persistent reopen button. Respect dismissal while
moving between the same Brain's pages; a new visit/Brain opens it again. Editing
pages retain their existing task scopes. Operational pages offer guidance only,
with no new mutation authority, automatic inference or source sharing. MCP's
explicit Help configure uses that same panel, not a parallel assistant drawer.

The shared Build with AI entry is a prominent contained button in the Brain
workspace and selected-Brain resource inspector. Its short entry animation stops
after three cycles and respects reduced-motion preferences. The inspector entry
opens the existing Brain workspace/assistant; it does not create a second
assistant or submit an AI request.

## AI-First Brain Workspace - Owner Approval, 15 November 2026

The owner approved the two AI-first mockups for the Brain library, its guided
inspector, and the contextual configuration assistant/capability workspace.
This is scoped approval, not restoration of the rejected 010.65 global redesign.
Home, Kits, Engage configuration, account pages and the global customer shell
keep their existing layouts and all existing routes/permissions.

Brain library rows are compact, with plain-language saved/live state, separate
Engage connection information, search and All/Drafts/Live filtering. Missing or
failed release/profile reads are unknown, not zero or a verified live state.
Publication alone is never deployment verification; old passing release history
must not conceal a newer failed target. An app's exact assigned release remains
its authority. Original template/offer targets are bootstrap defaults, not limits
on identifying later production upgrades. Keep the environment allowance compact
here while preserving its full explanation and infrastructure list on expansion.

The selected-Brain inspector starts with Build with AI and clear setup choices:
documents, tools and answers, then testing, Engage and publishing. Existing
configuration facts, release history, notices and operational actions stay in
Technical details. Setup links reuse canonical Brain pages; they do not create
another configuration layer. Do not claim a live Brain lacks knowledge when its
source configuration has not been read.

Following a connected Engage app from the Brain inspector must retain a visible
Back to Smart Brain control in the app inspector. Restore the owned Brain and
its explicitly selected release without closing the inspector or losing library
filters. Direct app-inspector links may return to that app's owned linked Brain;
never choose an unrelated Brain when the parent is unavailable. This is panel
navigation only, not reassignment or a configuration change.

### Engage Setup Follow-up - Owner Approval, 15 November 2026

Engage discovery and app inspectors now follow the same compact, guided pattern.
Keep All / Setup needed / Active filters, customer-facing delivery labels and
one next-step action. Active is the recorded installation state, not a runtime
health probe. Show actual remaining readiness messages beside Connect your Brain,
Brand your workspace, Website/address setup or Check and activate, using declared
check codes. Unknown checks stay visible under launch checks. Never hide blockers,
auto-activate, change assignments or start AI merely because a setup view opens.
Keep all prior operational controls and raw facts under Technical details.

Setup steps reuse the existing connection dialog, appearance preview, delivery
instructions and launch controls; there is no duplicate app editor. Preserve
Kit/Brain origin identifiers through those routes. Back to Kit is offered only
after the owned Kit, app and Brain relationship has been loaded and matched;
never trust an arbitrary return URL. Retain existing close/discard protection.
Setup entered directly from Engage uses Back to Engage apps, restoring the exact
selected app rather than stranding the customer in the legacy setup view.

Customers have one Engage library and inspector, not a second management page.
Legacy `/ai-workspaces?view=manage` and `view=setup` links adapt to the requested
tool on that same library. Connection, appearance and creation reuse the existing
dialogs in tools-only presentation; delivery and launch use details-only
presentation in the existing configuration drawer. Neither mounts the old app
table or duplicate page heading. The default staff administration table remains
unchanged. Cancel/close restores the exact selected app and library filters;
successful creation selects the saved new app. Preserve validated Kit/Brain
origin when it still matches the result, nested manual form buffers and all
existing busy/close protections. Opening these tools never saves or invokes AI.

Keep operational context visible in Engage records: app name/state, delivery and
address or website count, connected Brain, assigned environment, readiness and
the actual leading blocker. Additional issues are counted, not silently dropped;
the inspector retains every original message. Environment comes from the exact
customer/connection/Brain/release assignment, never the Brain's initial environment
or another app. Missing or failed reads remain unavailable. A draft with no
release has no assigned runtime environment; readiness is recorded checks, not a
live health guarantee. Responsive rows wrap this information instead of hiding it.

Engage redesign parity: connection/reassignment stays in Connect your Brain;
branding, preview and existing AI-assisted editing stay in their canonical
editors; website script/copy, origins and domain verification stay in delivery;
readiness refresh, required deployment update and activation stay in launch
checks. App controls exposes the existing Advanced editor for AI/edit, disable
and inactive-app deletion, retaining authorization and destructive confirmation.
Technical details retains connection mode, visitor access, allowed websites,
connection/profile/version and release references. Standalone apps must retain
their allowed websites too. Do not replace this functionality with an inert
summary or restore the duplicate management page to surface these facts.

Kit product headings reveal and focus their inspector even on repeated selection
and narrow screens. The workspace card's Configure workspace action opens the
existing editor instead of re-selecting an already open panel. Preview and both
delivery-channel links retain the exact Kit ID. The Kit page, Home and global
shell are not otherwise redesigned by this follow-up; 010.65 stays rejected.

The assistant is page-aware and shows selected reference documents, real draft
save results and inspectable diffs. Review draft opens the existing Updates
workflow with the selected target preserved; it never publishes or applies.
Source import alone does not invoke AI or index customer knowledge. Actual
validation warnings/failures remain visible even if a candidate was saved.
Keep model text intact; human status labels use exact protocol states only.

Console Support's dock and Support page share canonical runtime history. The
host restores an owned conversation; do not add a second local transcript store.
History, explicit New chat and Configure diagnostics use the same controls in
both presentations. Ordinary chat can select resources and grant diagnostic
consent without a ticket; new chat never inherits either. Ticket adoption uses
the ticket's authoritative episode, not a parallel ordinary conversation.
Support specialists are read-only; requested configuration edits still use the
canonical Build with AI panel. Read SUPPORT_CONTINUITY_AND_SPECIALISTS.md before
changing these boundaries. This is not the rejected customer-wide redesign.
The canonical Specialists page uses the existing Configuration/Advanced depth
switch. Keep optional per-plan Manager instructions alongside its objective,
approved areas and parallel setting; shared limits and exact published manager
prompts belong in Advanced. Authorized staff use the same editor and server-side
workspace access checks, not a second prompt editor or browser-only permission.
Session management and raw references belong under session options/Advanced.
Manual action/plugin editors remain the single editable implementation, opened
explicitly from the outcome table rather than automatically mounting on entry.

Action evidence preparation belongs in that existing action editor's collapsed
Advanced section. Automatic is the new-action default; selected fields remains
an explicit, lossless advanced projection. Preserve saved selection rules when
switching modes and show an empty-selection warning rather than silently using
the complete result. Use the normal shared draft save and explicit Updates
workflow, not another editor, automatic publication or a new connection test.

Connection testing has one aggregate entry at Smart Brain -> Updates -> Check
setup, combining existing draft validation with safe service checks. Saving a
connection runs only changed/new checks and shows a compact shared summary;
Details and Retry failed checks replace per-field/per-action test buttons. A
safe GET path is optional connection metadata, not an AI business action.
Never execute writes, paid inference or guessed health routes in these checks.
Not tested must stay distinct from verified; saved-draft results are not live
release verification. See `External_User_Guides/SMART_BRAIN_SETUP_CHECKS.md`.

Managed intelligence Tasks have one Brain-scoped home at
`/smart-brains/:brainId/tasks`, using the same approved shell and shared draft.
Keep Outcome, Resources, Try and Use in your app as focused views of that same
definition; do not duplicate model/connection editors or create a global Task
dashboard. The initial synchronous contract inherits the Brain model and permits
only configured knowledge/READ capabilities, including input-only tasks without
vectors. Try identifies the selected environment's applied version; it is not an
unsaved-draft preview. Inference starts only on an explicit Run task command.
Publishing/applying stay in Updates. No async job, per-task model or external-agent
callback should be advertised until separately implemented and qualified. See
`External_User_Guides/SMART_BRAIN_MANAGED_TASKS.md` and the 010.68 execution record
for current verification/deployment status.

MCP connections have one home in Smart Brain -> AI capabilities, at
`/smart-brains/:brainId/capabilities?view=mcp`. Reuse the Brain header's selected
target and the existing action editor; do not add a global connection selector
or a parallel action editor. Public setup skips credentials. Business-owner
authorization belongs to the integration team, never each app visitor.
Discovery/import is mechanical and uses no inference; optional Help configure
attaches owned, nonsecret catalogue metadata to the existing assistant.
Import shows actual saved changes and guarded Undo. Target reuse is an explicit
connection switch, not credential copying or deployment. Native schemas remain
visible without irrelevant HTTP-route controls. See
`External_User_Guides/MCP_CAPABILITIES.md` and plan 010.69 for qualified scope.

Knowledge's All sources / Documents / Live data tabs are filtered views, not
parallel editors. Keep their source counts and empty-state wording specific;
unknown or failed reads are not zero. Search settings uses four focused tasks
over the same provider buffer: service, embeddings, connection and customer
separation. Keep file uploads separate, retain connection inputs across these
tasks and do not display provider defaults as a loaded customer configuration.
See `verification-support/knowledge-source-experience/README.md`.

The separately approved account follow-up uses `/account/profile`,
`/account/team`, `/account/loomai-access` and `/account/security` in a single
resource workspace. Customer `/users` redirects there; staff `/users` remains
unchanged. Account permissions are workspace-wide, never scoped by a Brain
navigation query. See `verification-support/customer-account-workspace/README.md`
for the preserved authentication/team contracts and verification evidence.

The 010.66 private Data plugin library is a separate deliberate authoring task
under `/library/plugins`, entered from a Brain's existing Knowledge/Capabilities
picker. It is workspace-owned and has no environment selector. Keep definition
authoring separate from Brain installation; reuse the existing installer,
compiler and release flow. There is no new plugin type. Unsupported customer SQL
or filesystem adapters must not be presented as connected. Preserve source/form
fidelity and server-enforced ownership/revisions; advanced presentation is not
permission. See `verification-support/plugin-library-01066/README.md` for delivery
status, including the distinction between local tests and hosted qualification.

## Developer Entry - Owner Decision, 8 November 2026

The old staging-wide developer presentation is retired by 010.67. Do not
re-enable commercial/contact-hiding wrappers or treat staging as a developer
account type. Existing customer and staff pages retain their standard content
on both environments. The rejected 010.65 redesign remains out of scope.

There are two public entry pages over one shared production Console:

- `console.loomai.pro/`: ordinary business Kit discovery.
- `developer.loomai.pro/`: the developer landing page; `/developers` is its explicit
  preview route on the existing frontend.

Developer links use canonical Console authentication and the existing Kit
creation route with `entryPoint=DEVELOPER`. Keep this typed intent through sign-in,
onboarding and email callbacks. It is not a role, entitlement or permission.
The customer-experience and support Kits reuse their existing configuration and
editors. Ordinary business templates are not globally restricted by this entry.

Developer entry also preserves a Console discovery preference. General
sign-in/Open Console links and email callbacks carry `entryPoint=DEVELOPER`, even
without a selected Kit. The UI remembers the choice in this browser, scoped to
the signed-in actor and customer workspace; short-lived pending intent bridges
sign-in. This is presentation state, not an account type, analytics event or
authorization claim. It is not a server-side preference synchronized to every
device; email links carry their explicit entry intent across devices.

Home, My kits, discovery and empty Brain lists reuse the same two developer
starter cards. Existing Kits/resources remain accessible. A visible discovery
switch returns to business kits without changing account roles or existing
resource policy. New supported developer Kit links still carry typed entry into
the backend creation contract. Never infer or relax allowed environments from
this preference. Ordinary visitors without a remembered/explicit developer
choice retain business cards.

Owner follow-up, 9 November: a visitor entering through the developer URL must
keep developer presentation unless deliberately browsing business content. A
Secure/SameSite=Lax `loomai-website-experience` cookie on `loomai.pro` carries only
`DEVELOPER` or `CUSTOMER`, with a one-year lifetime and no identity/token data.
It selects the public homepage and signed-out Console discovery; signed-in
Console preferences remain actor/customer-scoped and are not inherited by an
unrelated account. The public site's `/` redirects remembered developer visitors
to `https://developer.loomai.pro/` before rendering business cards. Other paths
(products, demos, proposals, etc.) are deliberate browsing and are unchanged.
Homepage responses are private/no-store and vary by Cookie, avoiding a cached
personalized redirect. `/?entryPoint=CUSTOMER` or Console's explicit business
switch restores the business homepage. Fresh visitors keep the ordinary homepage.

The website cookie is not a developer session, authentication, entitlement or
production upgrade. The owner means a paid production launch by "upgrade";
browsing business content must never perform that transition. This presentation
follow-up neither implements payment nor relaxes the staging-only backend policy.

The business homepage offers Developers in its desktop/mobile navigation and a
Brain-image developer invitation beside the founder contact card. The invitation
links to the production developer entry; the founder card reuses the existing
contact/booking panel. Keep these as compact, adjacent light/dark links with actual
self-hosted portrait/Brain assets, not a flattened mockup or full-width invitation
band. The founder entry retains its original 34px portrait and compact typography;
the developer copy is `Developer?` / `Configure your Smart Brain`. Preserve their
side-by-side placement on narrow screens without overflowing text.

The backend stamps a persisted environment policy on developer-created Brains.
Use its `deploymentPolicy.allowedEnvironments` for target choices and show its
staging-only status. Do not infer policy from the hostname, browser preference,
free-form tags or a customer-editable prompt. Backend enforcement remains the
authority across deployment, provisioning, promotion and reassignment.

## Runtime Configuration

### Connected Workspace Preview

The Brain's Try it page embeds the actual standalone Engage app only after an
explicit Load workspace command. It does not pass Console identity or permissions
to the customer app. Open app remains available as a separate-tab path.

Both servers must explicitly permit the preview. Console uses
`PLATFORM_UI_ENGAGE_FRAME_ORIGINS` (comma-separated HTTPS origins; a leading
subdomain wildcard is supported for the owned app-hostname zone). The shared
Engage frontend uses `CONSOLE_FRAME_ANCESTORS` (comma-separated exact HTTPS
Console origins, never a wildcard). Local loopback HTTP is accepted for tests.
An empty Engage list retains frame-ancestors none and X-Frame-Options DENY.
An explicitly configured list replaces only Engage's framing restriction with
the exact CSP ancestor list. Console itself remains non-embeddable. Never allow
all HTTPS parents or forward authentication tokens into the iframe.

Kit Brain-entry links retain the exact owned app-assigned release and target
when known, otherwise the Kit's saved bootstrap target. Branding saves close
their current revision's dialog before cache replacement can remount the form;
pending saves, revision conflicts and ownership checks must remain intact.
Response-preset composition must use the exact JSON serializer and parser on
both request and response: draft decimal values must never become wrapper
objects. Keep the underlying numeric execution contract unchanged.

The uncached `/runtime-config.js` still supplies the API base URL and actual
`environment` (`staging` or `production`). The removed `PLATFORM_UI_PROFILE` value
has no effect and is not emitted. Do not confuse a public entry page with the
server on which its customer's runtime is deployed.

The owner selected `developer.loomai.pro` for the production frontend; remove its
old staging route. Staging retains its existing sslip hostname. Normal
authentication, ownership, credentials, provider accounting and resource limits
apply regardless of entry page. Neither page is a resource-free simulation.

## Visual Contract

Keep the developer landing dark, using the approved silver/teal Brain bitmap,
eight interactive capability nodes and two product starting points. The Brain
image is media, while labels, links, previews and controls are real responsive
HTML. Do not ship a flattened screenshot as the page. Preview examples must not
claim live execution, configured specialists or successful provider calls.
Developer-page header/footer logos are noninteractive branding, not links.
Other product pages keep their existing logo navigation.

The eight capability nodes, Web/Mobile/Backend targets and four start steps open
read-only visual explorers. Keep provider icons named and self-hosted; distinguish
supported options from configured or active services. Starter actions use the
same two real Kit routes and preserve developer intent. Code examples follow the
existing Engage installation and published runtime routes, with placeholder
tokens/IDs, not provider credentials. Mobile delivery means the mobile-friendly
workspace, a web view or the customer's own native client, not a new native SDK.
These previews never provision, change settings or invoke AI. Keep dialog theme
tokens on the portal itself so the primary action stays readable.

The developer offer is free developer platform access and LoomAI training, with
no one-year offer limit. It is staging only, not unlimited hosting, unlimited
model credit or a quota/funding bypass. Existing
support provides the training enquiry entry; no duplicate ticket system is added.

## Verification

Run `node --test scripts/developer-entry.test.mjs
scripts/customer-supabase-auth.test.mjs scripts/ui-profile.test.mjs`, the developer
landing browser scenarios and the production UI build. Check desktop/mobile,
all capability previews, real Kit setup links and retained authentication intent.
Verify production denial for developer Brains through the API, staging allowed,
and ordinary business production unchanged. Check both public hostnames and
the standard staging presentation after rollout.

Run `node scripts/developer-console.browser.mjs` for general sign-in, remembered
cards, Home/My kits/discovery navigation, business switching, account isolation,
mobile layout and typed setup links. It uses controlled API/identity fixtures,
not real hosted sign-in or provisioning.

Evidence and rollback status:
The old `verification-support/developer-ui-profile/README.md` is historical
evidence only. Current work is tracked in 010.67, `verification-support/developer-entry/`
and the latest `LLM-guides/CODEX_WORKING_CONTEXT.md` entry. Source and plan text
are not proof of a completed hosted rollout.
