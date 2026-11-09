# Platform UI Entry Points

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

The backend stamps a persisted environment policy on developer-created Brains.
Use its `deploymentPolicy.allowedEnvironments` for target choices and show its
staging-only status. Do not infer policy from the hostname, browser preference,
free-form tags or a customer-editable prompt. Backend enforcement remains the
authority across deployment, provisioning, promotion and reassignment.

## Runtime Configuration

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

The developer offer is one year of developer platform access and LoomAI training,
not lifetime hosting, unlimited model credit or a quota/funding bypass. Existing
support provides the training enquiry entry; no duplicate ticket system is added.

## Verification

Run `node --test scripts/developer-entry.test.mjs
scripts/customer-supabase-auth.test.mjs scripts/ui-profile.test.mjs`, the developer
landing browser scenarios and the production UI build. Check desktop/mobile,
all capability previews, real Kit setup links and retained authentication intent.
Verify production denial for developer Brains through the API, staging allowed,
and ordinary business production unchanged. Check both public hostnames and
the standard staging presentation after rollout.

Evidence and rollback status:
The old `verification-support/developer-ui-profile/README.md` is historical
evidence only. Current work is tracked in 010.67, `verification-support/developer-entry/`
and the latest `LLM-guides/CODEX_WORKING_CONTEXT.md` entry. Source and plan text
are not proof of a completed hosted rollout.
