# Customer Account Workspace

Owner-approved narrow follow-up to the Smart Brain outcome-led navigation, 7 November 2026.
This is not a restoration of the rejected customer-wide 010.65 redesign.

## Scope And Navigation

- `/account/team`: searchable workspace team, status filter, selected-member details and focused add/edit/password drawers.
- `/account/profile`: current identity, customer workspace and existing administrator access.
- `/account/loomai-access`: existing LoomAI operator grants and explicit revocation; grant entry is a drawer.
- `/account/security`: existing recovery/password and email-link sign-in flows, not a new identity provider.
- Customer `/users?deploymentId=...` redirects to the account workspace, retaining a safe Back to Smart Brain link. The Brain is navigation context, not the team permission scope.
- Staff `/users` remains the existing administration page. No staff role, assignment, customer ownership or authorization API changes.

The single resource sidebar, compact utility bar, restrained split work surface,
dark/light themes and basic mobile navigation follow the approved Smart Brain
frame. No draft/version/environment/publish controls appear in account settings.
Access changes are immediate and unrelated to runtime release publication.

## Existing Contracts Preserved

Team reads use the existing customer-filtered user access overview without a
deployment override. Create/update use the existing commands, fixed
`CUSTOMER_ADMIN` role and authenticated workspace ID. The backend still enforces
ownership and last-administrator rules. Mixed-owner/staff-role responses are not
rendered. Query keys and editor mounts isolate the current actor/customer/auth
mode; pending results cannot update a removed editor. Unsaved input and pending
commands protect navigation. The authenticated actor is an email, not a user row
ID; self-disable is unavailable.

Creating a team member creates an active **local Console account**, not an email
invitation or automatic Supabase registration. The existing account-linking
workflow remains necessary. Legacy team password resets only change local
Console credentials; own Supabase recovery remains separate. The UI says this
explicitly. These are preserved product constraints, not features added by this
redesign.

Collaboration retains exact existing operator email grants, revocation
confirmation, abort/generation guards, owner-checked receipts and immediate
access semantics. Global administrator powers remain outside this surface.

## Verification

Focused tests:

```sh
cd Platfrom/ui
node --test scripts/customer-account-workspace.test.mjs scripts/customer-staff-access.test.mjs
npm run build
```

The account suite covers canonical/legacy/safe return paths, owner/role isolation,
list filtering, exact fixed-role create/update bodies, authenticated actor
identity, existing API requests/errors, guarded editors, retained staff routes,
and collaboration lifecycle contracts. Existing Smart Brain/navigation,
Supabase-authentication and UI-profile regressions are also checked.

### Local Qualification

- Production UI build passed; existing runtime-config script and large-bundle warnings remain visible.
- 104 focused account, collaboration, Smart Brain, authentication, navigation and UI-profile checks passed.
- API-backed read-only localhost review passed: legacy redirect and Back to Smart Brain, actual owned team read, list filters/empty state, selected details/self-disable guard, add/edit/password drawers, Keep editing/Discard, dirty back-navigation guard, collaboration drawer, profile, own recovery route and fixed-email sign-in-link dialog.
- A name-save attempt was rejected by the local read-only proxy before reaching the production API; the actual form retained its input and became editable again. No account write occurred.
- Dark/light desktop and basic 390px navigation/security/team views checked; no horizontal page overflow. Desktop uses one viewport with independently scrollable work panes.
- Existing test identity signed out, temporary tabs closed and viewport reset.

Local screenshots are in [local](local/team-dark-1440.png). A stale native browser
confirmation interrupted an early check; native discard confirmations were
replaced with in-page dialogs and the checks were repeated after clean reload.
Development hot-reload context errors were cleared by a full reload; they are
not a claim about hosted production behavior.

### Hosted Qualification

- UI source: `f1045c731aad89d37f80e0897e6c7b728401ed98` on `Platform-V11`.
- [CI 42416848422](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42416848422) passed; backend image job skipped.
- Staging Coolify job `y7ubferxhzjar3es0wl1ykdy` finished; exact-source native UI container healthy.
- Production Coolify job `ce2za8bgmgsn0strvxadhit8` finished; exact-source native UI container healthy.
- [Production account](https://console.loomai.pro/account/team) and [developer staging account](https://developer.loomai.pro/account/team) verified with the authorized owned support fixtures, not the owner's personal Brain/account.
- Legacy customer `/users?deploymentId=...` redirects to the account workspace with safe return context. Actual team read, filters, own-member indicator/self-disable guard and add/edit drawers passed.
- Fresh signed-out `/account/team` entry returned to that exact account page after real customer password sign-in; no intermediate Kit selection was required. Final production screenshot was refreshed after the desktop viewport settled.
- Staging SPA back-navigation retained unsaved edits; Stay here and explicit discard passed. Collaboration drawer and own-identity email-link controls passed without granting access or sending email.
- Production Back to Smart Brain and account-avatar return preserved the selected target after the Brain loaded. My account, security, dark/light desktop and basic mobile navigation passed. No horizontal page overflow; desktop page fits one viewport.
- Staff direct `/users` still renders **Unified access administration**, with no customer account frame.
- Production verification tab captured no console errors. Local/staging/production customer fixtures and production staff were observed signed out; temporary tabs closed and viewport reset.
- Backend source remains `baf6ea3328a1ef72d83d01552ffbc02da1582105`, healthy on both hosts. Runtime config remains staging/developer and production/standard.

Proof: [production dark](live/production-team-dark.png), [production light](live/production-team-light.png),
[production security](live/production-security-dark.png), [production mobile](live/production-profile-mobile.png),
[staging dark](live/staging-team-dark.png), [staging light](live/staging-team-light.png),
[staging mobile](live/staging-profile-mobile.png).

Verification boundaries: an early production request still reached the previous
UI while Coolify was switching traffic; checks were repeated after the job
finished and the page reloaded. An account-avatar click during the Brain's
initial automatic environment resolution stayed on the Brain; the settled-page
return passed. Staff sidebar clicks did not navigate in this automation run;
the unchanged staff `/users` route was qualified by direct entry. These existing
navigation/timing surfaces were not rewritten as part of this account redesign.

Browser
verification does not change real user passwords, grant new staff access, disable
accounts or send sign-in emails. Such operations are checked with synthetic
command inputs and existing backend contracts instead.

Name/status edits reread the current owned member snapshot before updating to
avoid overwriting a status or name change already made by another administrator.
This is a UI preflight, not a new atomic server compare-and-swap API. Background
directory refresh no longer keeps a completed command busy. Account email-link
return destinations use the existing authentication-return mechanism with only
allowlisted local account routes; normal Desk and Kit return behavior is retained.

## Rollback

Revert only this account-workspace change set and redeploy the previous UI image.
No database, backend, runtime, framework, AI settings or customer configuration
migration is involved. Developer staging remains independently configured.
