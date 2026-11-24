# 010.74 Shared Console, Workspace Membership And Staff UI Migration

Date: 23 November 2026.
Status: queued change plan requested by the owner; no implementation or release
has started. Execute after the pending 010.73 work, not alongside it.

## 1. Outcome And Order

Make the accepted customer Console the shared workspace experience for customers,
LoomAI staff and platform administrators. Retire the old staff interface page by
page rather than maintaining two permanent product interfaces.

Customers can give an existing eligible account access to their workspace,
including a person who already owns another customer workspace. The account
keeps its identity, sign-in and original workspace. Staff do not impersonate the
customer. Every operation records who actually acted and for which workspace.

Delivery order:

1. Finish or explicitly record an owner-approved deferral of the remaining
   [010.73 retrieval and reranking work](010_73_HYBRID_RETRIEVAL_RERANKING_AND_AI_LED_KNOWLEDGE_OPERATIONS_PLAN.md).
2. Inventory staff functionality and implement the shared authorization/context
   foundation before exposing the customer shell to staff.
3. Give admins and delegated staff a complete shared workspace journey.
4. Extend delegation to existing customer accounts, with Viewer and Editor access.
5. Migrate and remove duplicate staff screens in verified increments.
6. Modernize the remaining genuinely global administration area last.

The first working milestone is an administrator selecting an existing workspace
and completing Kit -> Smart Brain -> Build with AI -> Engage -> Updates entirely
through the shared Console, without changing ownership or the administrator role.

## 2. Reranking Dependency And Current Status

010.73 remains in progress, with local candidate code rather than a completed
release. Outstanding packages include provider/generation qualification, shared
multi-space retrieval, Ragas and reviewed comparisons, bulk knowledge operations,
reranker accounting/secret/UI integration, and staged/live release qualification.
Use its current plan and verification record at execution time; this list does
not turn every candidate provider into a mandatory infrastructure purchase.

The owner subsequently supplied a Cohere key, stored in ignored private auth
material. Authentication-only verification succeeded. This is not reranking
qualification, production activation or approval of a Cohere spending cap.
Paid qualification still needs an explicit cap. No credential belongs here.

Do not start this plan because an individual reranking unit test passes or a key
exists. Record the prior work's released versions, remaining explicitly deferred
items, live checks and current configuration before beginning the access change.
Keep separate commits/releases and acceptance evidence for the two workstreams.

## 3. Validated Current Limitations

These are source-validated constraints, not proposed new capabilities:

- `App.tsx` chooses the customer shell and many customer routes using
  `session.role === 'CUSTOMER_ADMIN'`. The Smart Brain workspace is already
  shared in part, but the Home/Kits/Engage/account journeys still diverge.
- `CustomerSessionSupport` and `PlatformCustomerAccessService.currentScope`
  require a customer identity tied to one `platform_users.customer_id`.
  Selecting a different customer in the browser does not change that boundary.
- `CustomerStaffGrantService` only accepts active `PLATFORM_OPERATOR` accounts
  with no customer binding. Customer accounts cannot receive that grant.
- `CustomerStaffAccessPolicy` is deliberately operator-specific. Its table is
  not a general-purpose multi-workspace membership system.
- Platform admins have global authority in several services, including early
  deployment-access checks. A new customer-looking shell alone would not scope
  those reads or writes to the selected workspace.
- Existing customer Team UI creates local Console administrators, resets legacy
  Console passwords and disables accounts. Those operations must not be reused
  as membership management for a person who belongs to multiple workspaces.
- Kit entitlements and self-service creation have customer/owner checks in
  addition to deployment access. Staff Kit endpoints already exist. Reuse their
  service contracts; changing a shell does not make the complete journey work.
- Brain Keys & secrets already uses the shared secret store and deployment
  authorization. An authorized delegated operator can manage eligible Brain
  secrets; the old collaboration copy is narrower than this actual contract.
  Global/platform-managed and target-managed credentials remain separate.
- Signup reports `LINK_REQUIRED` for any matching local email. Customer recovery
  only accepts an active customer administrator. A staff-email collision can
  consequently lead to an inappropriate recovery page and misleading link error.

Read-only production checks on 23 November found the reported corporate email
to be an active platform administrator without a customer binding, and the
reported Gmail account to be an active linked customer administrator. Neither
account, its permissions, password or workspace was changed. This finding does
not authorize conversion, linking or use of either person's session for testing.

## 4. Product Shape And Non Goals

### One Interface With An Explicit Context

```text
Verified account / legitimate staff sign-in
                  |
          Authorized workspaces
                  |
       Select workspace and access
                  |
   Shared Console: Kits / Brains / Engage / Team
                  |
    Existing services with checked workspace scope

Eligible staff also have a separate Platform administration destination.
There is no impersonated customer login or second configuration editor.
```

Workspace selection belongs in the existing header/account area. Show the active
workspace and effective access clearly, for example `Harbor / Editor` or
`Harbor / Platform administrator`. Customers with one workspace should not need
an extra selection step. Multiple-workspace users receive a searchable switcher.
An admin without a personal customer workspace can open an authorized existing
one or use the existing administrator workspace-creation flow.

Do not restore the rejected 010.65 customer-wide redesign. Preserve the accepted
010.64 Brain workspace, current customer layouts, responsive inspectors,
navigation return paths and developer entry behavior. This is a role/context
convergence and functional migration, not another visual overhaul.

Non goals:

- No new global account types, duplicated accounts, email-change workaround,
  customer impersonation or silent conversion of staff into customers.
- No identity-provider replacement, universal new IAM engine, per-resource
  custom role builder or mandatory invitation/email-delivery system.
- No automatic move of Kits, Brains, apps, documents, credentials or funding.
- No runtime/framework change expected. Raise any newly required framework
  change separately for owner review rather than expanding this plan silently.
- No infrastructure resize, new service, paid capacity or budget increase.
- No weakening of staged-only developer resource policy, confirmation,
  publication, document approval, indexing or customer quotas.
- No promise that removing a workspace grant revokes platform-wide admin powers.

## 5. Identity And Workspace Membership

### Keep Identity Separate From Access

Retain the existing global account, stable user ID, verified issuer/subject link
and staff role. Add one canonical workspace membership relation keyed by
`customer_id + platform_user_id`, with role, ACTIVE/REVOKED status and existing
audit-style grant/update/revocation fields. Enforce uniqueness in the database.
Reuse the Platform database and existing audit service; no separate access store.

Use three fixed workspace roles, without configurable permission combinations:

- **Owner:** existing workspace-administrator rights, including managing access.
  Preserve all legitimate existing administrators; do not invent a single-owner
  restriction or promote every invited collaborator to Owner.
- **Editor:** manage workspace products/configuration and perform existing
  operational actions allowed for that actor, resource and workspace. This is
  configuration access, not authority to execute every customer business action.
- **Viewer:** read permitted workspace configuration and state. No saves, AI
  authoring, uploads, indexing, publish/apply, activation, rotation or deletion.

Customers initially share Viewer or Editor access. Preserve the existing
owner/admin maintenance and last-active-administrator safeguards; new ownership
transfer or arbitrary role editing is not required for the first milestone.

Do not use the global `CUSTOMER_ADMIN` label as permission to administer every
workspace a person can see. Their effective rights come from the selected
membership. The existing user customer ID may remain a home/default during
migration, but is not reassigned to implement switching or cross-customer access.

### Existing Account Delegation

The Owner enters an exact email and selects Viewer or Editor. Resolve an active
existing account server-side. An existing customer recipient needs its active
verified Console identity; staff use the legitimate existing staff sign-in.
Do not infer account ownership from the entered email or browser metadata.
Do not strip email aliases or merge provider subjects by similar email strings.

The grant is idempotent, immediately available on the recipient's next authorized
workspace read, and audited. It does not alter their password, profile, global
role, home workspace, existing memberships or resource ownership. Use existing
notification channels when available; do not claim an invitation email was sent.

An unknown, inactive, unlinked or ineligible account gets a bounded, non-enumerating
eligibility error and no partial grant. Registration remains the existing sign-up
journey. Inviting an unregistered email, pending-email acceptance and granting an
unlinked Supabase-only identity are explicitly deferred, not silently implemented
by creating a replacement workspace or setting a password for the recipient.

Platform-admin accounts may appear in this model, but their independent global
authority cannot be removed or reduced by a customer membership. The UI must show
that distinction rather than claim a Viewer grant limits a global admin or that
revoking it blocks all admin access. Operators still require active customer
delegation. Adding a customer collaborator never creates a platform staff role.

## 6. Effective Permissions And Sensitive Operations

Use one shared workspace-policy service from existing service boundaries, not
role checks copied into every screen. Intersect workspace role with existing
resource access, active status, applicable operation policy and authorization.
An explicit workspace mismatch is denied before global-admin shortcuts in the
workspace request path. Global administration retains its deliberate separate
authority and audit context.

Owner and Editor can use the existing product editors where authorized. Preserve
explicit lifecycle and permission checks for creating/removing resources,
checking setup, publishing, staging/production apply, promotion and activation.
No new approval ceremony is implied, and access is not proof of budget or consent.

Resource consumption and existing two-environment limits stay with the selected
customer workspace, not the collaborator's home account. Joining a workspace
does not add capacity, move a funding grant, reset usage, change the payer or
unlock production. Costs and any spending admission must identify the workspace
being served before an operation starts.

Keys & secrets requires particular care:

- Preserve the existing authorized delegated-operator Brain-secret workflow;
  do not remove it based on the narrower collaboration-panel wording.
- Viewer cannot create, rotate, bind or delete credentials. Editor operations
  must still satisfy the Brain's current administration/secret policy; do not
  grant platform-global secrets or provider-owned credentials through membership.
- A grant of ordinary configuration Editor access includes eligible
  workspace-owned Brain secret management only through that same checked
  contract. Explain this clearly when the Owner grants access. A future separate
  credential-manager role is not part of this simple first implementation.
- Never return stored secret values, pass them to Build with AI, reuse another
  workspace's references or copy managed credentials into customer drafts.

Global account administration is not workspace membership administration:

- `Remove access` revokes this membership only. It does not disable the person
  globally, reset a password, revoke unrelated memberships or unlink identity.
- Personal email/password/security changes remain with the account owner and
  existing provider recovery. A workspace Owner cannot reset an existing external
  collaborator's credentials or edit their global profile through Team UI.
- Keep legitimate legacy account-administration functions in their authorized
  location until migrated. Explicitly replace unsafe cross-workspace password or
  disable actions with membership actions; record this intentional security
  correction in the parity ledger, not as an unexplained missing button.
- Prevent removal/demotion of the last active workspace administrator. Separate
  removal from a workspace, global account suspension and ownership transfer.
- Customer grants cannot create staff accounts, global admin roles, global
  directory access, infrastructure entitlements or new paid allowances.

## 7. Request Context, Switching And Browser Isolation

Do not store one mutable active workspace on a shared login cookie: two browser
tabs would be able to change each other's write destination.

Use a single typed per-request workspace context. The concrete initial contract
is a `X-LOOMAI-WORKSPACE-ID` request header set by the shared API client for
workspace calls. Treat it as untrusted input and resolve effective access from
the real authenticated account on every request. Existing path customer IDs and
owned resource IDs must agree with it. Query/URL context is navigation only.

Preserve canonical `/smart-brains/:brainId/...`, Kit and app routes. A direct
resource link resolves its authoritative owner server-side, checks access and
sets the matching navigation context; it must not use a remembered unrelated
workspace or silently substitute a different resource. Keep one consistent
collection-route query convention, `workspaceId`, for reload/back/share behavior.

Return effective workspace ID/name/access/capabilities in trusted response
metadata or the session-context response, keeping real account role separate.
An explicit invalid, revoked or mismatched context never falls back to the home
workspace, all customers, an unscoped list or administrator bypass. Existing
single-workspace requests may retain a validated home default during rollout.

Cross-origin requests still enforce existing cookie/CSRF/origin rules. The new
header does not grant API-key, public client, MCP, Desk or Engage visitor sessions
Console access. Existing machine integrations keep their own explicit contracts.

On a successful workspace switch:

- Respect unsaved-input, busy-operation and destructive-confirmation guards.
- Cancel in-flight page reads and prevent stale responses populating the new UI.
- Key caches, drafts, inspector state, selected targets, app/Kit navigation,
  assistant sessions, usage views and local preferences by actor and workspace.
- Reset selected resources and Support diagnostics/consent. Never carry a grant,
  selected resource, transcript or authoring write target across workspace IDs.
- Persist only a navigation preference per tab. Revalidate it after login/reload;
  logout or account change clears account-scoped data and pending selections.
- Validate deep-link returns. No open redirect or ability to persist privilege
  through a client flag, query parameter or developer-entry preference.

Multiple-workspace access does not merge data. An authorized user may switch to
another workspace, but one workspace's screen/assistant response cannot silently
include information, attachments or search results from the other.
Document audience, active-version and end-user business-action authorization
remain separate checks. Resolve them before exposing content to an LLM or an
external reranker; Console membership is not blanket approval of all tenant data.

## 8. Sign In, Signup And Recovery

Keep staff authentication working during migration. Admins/operators sign in
through the existing staff flow, then select a workspace in the shared Console.
Opening the shared UI does not make customer email recovery a staff-login flow.
Supporting additional staff identity-provider login is a separate future scope.

After normal verified customer sign-in, resolve active memberships:

- One workspace: open it directly.
- Several: restore an authorized preference or show the workspace chooser.
- No membership: show the existing registration/access guidance, not an arbitrary
  empty customer workspace or fallback staff interface.
- An existing staff email entering customer onboarding: give correct staff
  sign-in guidance after verified identity, rather than suggesting customer
  workspace recovery that can never satisfy the staff-role checks.

Preserve provider-owned email proof, immutable issuer/subject linking, fresh
one-use recovery tokens, matching confirmed email, trusted origins and active
account checks. Never attach a staff identity to customer ownership based only
on email, change its role to make recovery succeed, reactivate a revoked link or
issue a replacement password without the legitimate recovery flow.

Separate the helpful UI states for no workspace, membership revoked, staff
sign-in required and expired/used recovery link. Detailed internal diagnostics
belong in protected logs, without tokens or provider bodies. Public unauthenticated
responses must not become an email/role enumeration endpoint. Review the
existing broad recovery error without weakening its mailbox-proof boundary.

## 9. Shared UI And Old Page Destinations

Use the accepted customer shell as a shared workspace shell, enabled by trusted
workspace context rather than a role-name substitution. Refactor customer-only
guards deliberately; do not broadly change `isCustomerSession` to return true for
admins and thereby bypass the existing customer authentication contract.

Migration groups and destinations:

- **Home and resource lists:** shared workspace Home, Brains and Engage lists,
  with current state, environment, readiness and leading blockers retained.
- **Kits and setup:** existing catalogue/create/detail flow, with authorized
  customer/owner attribution, entitlement preparation and template constraints.
  Staff preparation and original ownership-transfer tools retain their real actor.
- **Brain configuration:** existing Brain sidebar and one editor each for
  Actions, plugins, Knowledge, search, models, behavior, specialists, Tasks,
  secrets, testing and Updates. Operational/global tools are not copied into it.
- **Engage:** existing library/inspector and canonical connection, appearance,
  preview, website embed, address/domain, required-update and launch tools.
- **Account and team:** workspace membership/role/revocation in one Team & access
  experience. Keep own sign-in/profile separate from collaborator management.
  Fold LoomAI collaboration into that model; no second grants list or editor.
- **Support and notifications:** scope real requester identity, conversation and
  case access, selected resources and consent. Notification and continuation
  links retain the authorized workspace and do not default to another one.
- **Advanced workspace operations:** preserve every legitimate staff capability,
  diagnostic, access assignment, version/rollback and cleanup action in the
  correct workspace section or restricted administration destination.

Genuinely global functions stay under restricted Platform administration:
customer/workspace provisioning, platform-user/staff roles, marketplace publisher
validation/publishing, runtime artifacts, managed product/inference services,
fleet monitoring, global infrastructure, billing/allowance administration,
cross-workspace audit and global/provider-owned secrets. Existing deferred
Shopify, partner and specialist operations are classified individually, not
deleted or newly implemented as a side effect of this migration.

Administration is a destination within the same product/design system, not a
permanent duplicate customer product. Modernize its global pages after the
workspace migration. End state: no old staff workspace shell, duplicate Brain
editor, alternate Engage management list or ambiguous customer-selection form.

## 10. Feature Parity Ledger And Page Retirement

Before changing routes, create a checked-in ledger listing every current staff
route and visible operation. For each record, capture its outcome, important
fields/statuses, read/write APIs, role checks, confirmation, destination,
verification scenarios and retirement status. Include hidden/Advanced and
permission-dependent controls, not just default screenshots.

Use these statuses: inventoried, destination implemented, locally verified,
staging verified, production verified, old implementation removed. Mark any
intentional security restriction explicitly with its reason. An attractive
summary without the original operation is not parity.

For each migrated page:

1. Reuse its existing service and editor in the canonical shared destination.
2. Verify customer, customer collaborator, operator and admin outcomes.
3. Translate the old URL once, preserving known resource, target, filters and
   valid return context. Ambiguous links ask for a workspace, never guess.
4. Remove its duplicate menu item and UI implementation after parity passes.
5. Keep only the necessary thin navigation adapter while existing links require
   it; assign its removal/maintenance explicitly. No second editable page.

Do not delete a staff page solely because customers do not see its controls.
Conversely, do not show every global admin control to customers merely to share
the component. Visibility follows authoritative capabilities; APIs enforce them.

## 11. Backend Changes And Data Migration

Implement in the existing Platform backend and database:

- Membership persistence, uniqueness, role/status checks and audit events.
- A shared workspace resolver/access policy with explicit request context.
- Workspace catalogue, current-context summary and Owner membership-management
  endpoints, using normal authenticated sessions and bounded email resolution.
- Adapt existing customer/staff service entry points to that policy; keep
  existing product, release, indexing, secret and funding implementations.
- Review all global-role bypasses, `currentScope`, `requireCustomerActor`,
  owner-entitlement checks and customer-only controllers before exposing a route.
- Bind creation to the selected workspace, actual actor and proper entitlement
  owner. The acting Editor/staff member does not become the owner or payer.
- Keep resource IDs, customer/tenant ownership, versions, runtime assignments,
  S3 prefixes, vector namespaces, external connections and billing unchanged.

Migration is additive first, then one authoritative access path:

1. Snapshot existing active/disabled customer accounts and staff grants through
   an approved protected inventory. Dry-run exact membership mappings/conflicts.
2. Backfill legitimate current customer administrators as Owners of their
   existing workspace. Disabled accounts remain disabled; no grant activates them.
3. Backfill active operator grants as Editor membership with their current
   allowed operation set. Preserve revoked records and original audit lineage;
   do not recreate them as active. Admin authority is not a forged membership.
4. Validate parity and row counts, then switch authorization to the canonical
   membership policy. Do not run permanent OR-based old/new grant engines.
5. Retain old rows read-only for audit or migrate their history; remove old
   writable staff-grant APIs/UI once their replacements are qualified.

Use migrations and existing authenticated services, not manual production SQL
role changes, account merges, untracked grants or identity-link replacement.
New memberships and revocations must survive application rollback. Once an
account has several workspaces, rolling back to the old one-customer model may
require a forward fix or controlled feature withdrawal; never restore old grants
over current revocations or pretend schema deletion is a safe rollback.

## 12. Revocation, Concurrent Work And Audit

Recheck active account, workspace, membership, resource ownership and operation
permission on every protected read/write, not only on switch or initial page load.
Workspace suspension removes access without silently transferring its resources.
Membership removal does not sign the person out of unrelated workspaces.

Enforce the same checks for uploads/downloads, document discovery/approval,
indexing/evaluation results, histories, exports, previews, conversation streams,
Support cases, authoring sessions, polling and subscriptions. Bound or disconnect
long-lived access when the membership is revoked; no indefinite cached grant.
Document the limits of revocation: already downloaded content cannot be recalled,
and already issued provider-signed links may remain usable until their bounded
expiry. Do not label these as instantly revoked. Reuse short-lived scoped links
or the existing authorized proxy where stronger read-time enforcement is needed.

Authoring preview/diff and save require the same workspace scope. Viewer cannot
invoke a paid or mutating assistant operation. Build with AI may modify only an
authorized editable draft and cannot auto-publish, apply or inherit credentials.
Explicit attachment content does not establish resource ownership.

Async operations retain immutable actor/workspace/resource IDs at submission;
changing tabs or workspace never retargets queued work. Recheck authorization
before new privileged dispatch/claim and define behavior after revocation using
the existing worker/operation lifecycle. Do not replay, undo or falsify already
executed writes; stop newly unauthorized steps and report the actual receipt.

Audit grants, role updates, revocations, workspace selection/admin entry and
privileged operations with the real actor, selected customer, access origin,
resource and operation/correlation ID. Never record raw keys, session/recovery
tokens, unnecessary email directories or complete private request bodies.

## 13. Implementation Phases And Exit Gates

### P0 Inventory And Security Contract

- Confirm 010.73 completion/approved deferrals and deployed baseline.
- Produce the parity ledger and exhaustive route/API ownership classification.
- Trace current owner, operator, global-admin, deployment-assignment and secret
  permissions; name every boundary being changed. Record intentional differences.
- Fix the membership roles, account-lifecycle distinction and concrete request
  context before shell access changes.
- Exit: source-validated design, migration dry-run and negative-test matrix; no
  staff/customer permission bypass disguised as a presentation change.

### P1 Shared Context And Staff First Milestone

- Add membership backfill, shared policy/context and trusted capabilities.
- Add workspace selection and shared shell entry for existing staff sign-ins.
- Complete Home, Brain configuration/assistant, Kit setup and Engage lifecycle
  for admin and already delegated operator, preserving all existing owner flows.
- Maintain explicitly unmigrated global/staff tools until parity is reached.
- Exit: exact workspace end-to-end success, cross-workspace denials, audit and
  revocation checks on staging before production. No broad UI-only enablement.

### P2 Customer To Customer Delegation

- Extend Owner Team & access to an existing verified customer or staff account,
  with Viewer/Editor selection and immediate membership removal.
- Unify the former LoomAI collaboration list with workspace memberships.
- Finish multi-workspace sign-in/chooser, staff-email onboarding guidance and
  safe member removal without global disable/password reset.
- Exit: account A keeps its original workspace while accessing B as Editor or
  Viewer; revoking B immediately denies B without affecting A or its credentials.

### P3 Incremental Staff Page Retirement

- Migrate remaining workspace routes in parity-ledger order; reuse canonical
  editors and remove each duplicate implementation after live verification.
- Include knowledge/uploads/evaluation, human support/reviews, access assignments,
  notifications, operational diagnostics, release/version and cleanup workflows.
- Exit per page: important data and every authorized action remain available;
  old links lead to the exact shared destination without loops or lost context.

### P4 Global Administration And Closure

- Reuse the design system for genuinely global internal pages and an explicit
  Administration navigation destination; preserve global authority boundaries.
- Remove obsolete staff shell/routing, redundant selectors and writable grant
  implementations after their remaining dependencies are migrated.
- Update operating guides, ownership/security documentation and user help.
- Exit: one shared workspace experience, one restricted administration area,
  no duplicate product editors and no unexplained missing functionality.

## 14. Verification And Release Requirements

Use isolated, consented fixtures with two known-real customer workspaces, an
Owner in each, a delegated operator, an admin and a Viewer. Establish positive
controls before testing foreign IDs. Do not use nonexistent IDs as the only
isolation evidence or borrow the owner's live session to bypass sign-in.

Required automated and live scenarios:

1. Existing customer login, mailbox recovery, logout and resource navigation
   still work; used/revoked links and account suspension remain denied.
2. Staff-email customer onboarding shows correct guidance without changing role,
   password, identity link or customer ownership. Unauthenticated email probing
   does not reveal account role or workspace membership.
3. Admin and delegated operator use the shared journey; operator without a grant
   cannot list/open/write the customer. Global admin mode remains explicit.
4. Owner A grants Editor/Viewer to existing customer B; B's original workspace,
   credentials and Owner access remain unchanged. Duplicate grant is idempotent.
5. Viewer GETs succeed for allowed resources while direct mutation, assistant,
   publication, secret, upload, indexing and activation requests are denied.
6. A configuration Editor can perform the promised authorized Brain secret
   operations without value readback, but cannot reach global/foreign secrets,
   grant access, change staff roles or reset another account's password.
7. Known foreign Brain/app/Kit/source/connection/secret/history IDs, forged
   request scope, mixed-resource requests and edited URL/header combinations are
   denied, including when sent by an admin in a different selected workspace.
8. Direct links, reload, back, two concurrent tabs, pending reads and unsaved
   forms preserve exact workspace/resource ownership and do not leak stale data.
9. Revocation during an open editor, stream, assistant operation or queued task
   prevents newly unauthorized work; completed writes retain truthful receipts.
   Last Owner cannot be removed; membership removal does not disable the account.
10. Kit creation, standalone Brain creation, managed/BYO services, document
    upload/index/evaluation and the qualified 010.73 retrieval controls still use
    their actual owner, destination, budgets and existing approval workflow.
11. Engage binding, branding/preview, embed, domains, readiness, explicit apply,
    activation and inspector return paths work with unchanged app identities.
12. Support history/cases/diagnostic consent, notifications and Build with AI
    scenarios remain isolated and functional. Access does not auto-grant Support
    diagnostic consent, end-user action permission or Desk agent assignment.
13. Developer entry retains developer cards and staging-only policy. Choosing a
    workspace or staff access cannot silently unlock production or paid services.
14. Migration preserves old grants/revocations/owners; retries are idempotent,
    concurrent grant/revoke does not lose state, and old routes have real parity.

Run database-backed authorization/migration tests, API tests, normal-session
browser journeys, desktop/mobile and light/dark screenshots, keyboard/focus and
overflow checks. Test direct endpoints, not just hidden buttons. Keep ambiguous
and failed runs in the evidence; no claim of security based on LLM refusal.

Deploy each complete phase to physical staging, then production through normal
CI/release mechanisms. Verify source/image/schema identity and the same seeded
policy on both; keep environment-specific URLs/keys and real document inventories
distinct. Publication is not runtime qualification. Do not spend the owner's
manual Harbor turns, increase provider caps or create paid infrastructure as a
side effect of UI verification. Obtain bounded test allowance when necessary.

## 15. Source Map For Implementation

Re-read current versions before editing; files and deployed revisions may change
while 010.73 is completed.

- Presentation/routing: `Platfrom/ui/src/App.tsx`,
  `layout/CustomerConsoleShell.tsx`, `layout/AppShell.tsx`, Brain/account shells,
  `auth/PlatformAuthProvider.tsx`, `auth/customerSession.ts`, the shared API client
  and `workspace/DeploymentWorkspaceContext.tsx`.
- Team/auth: `pages/CustomerOnboardingPage.tsx`,
  `pages/CustomerAccountRecoveryPage.tsx`, `pages/CustomerAccountPage.tsx`,
  `account/CustomerTeamWorkspace.tsx`,
  `components/customer/CustomerStaffAccessPanel.tsx`.
- Backend identity/context: security `PlatformPrincipal`, `CustomerSessionSupport`,
  `PlatformAuthSessionSummary`, `PlatformAuthController`,
  `PlatformCustomerAccessService`, `CustomerStaffGrantService`,
  `CustomerStaffAccessPolicy`, `CustomerOnboardingService`,
  `CustomerAccountRecoveryService`, identity-link/user/session repositories.
- Resource/operation authority: `DeploymentAccessService`,
  `CustomerOfferEntitlementService`, `StaffCustomerAccessService`,
  `StaffExperienceKitController`, `ExperienceKitService`, self-service controllers,
  `CustomerDeploymentSecretService`, authoring, document/storage, Engage, Support,
  notification and asynchronous operation services.
- Extend existing security suites including `CustomerStaffGrantServiceTest`,
  `CustomerStaffAccessPolicyTest`, PostgreSQL grant/auth tests, onboarding/recovery
  tests and shared customer/Brain/Engage browser scenarios rather than inventing
  a second verification system.

## 16. Definition Of Done And Owner Decisions

The work is complete only when existing eligible accounts can receive scoped
workspace access; staff/admins can use the shared product journey as themselves;
customer configuration, ownership and sign-in remain intact; Viewer/Editor and
revocation hold at API boundaries; important staff functions have verified homes;
and obsolete duplicate workspace UI is removed.

Before execution, confirm the proposed simple role semantics, including Editor
access to eligible workspace-owned Brain credentials and existing explicitly
authorized release operations. This plan does not authorize implementation now,
new spending, infrastructure changes or automatic takeover of a customer.

Owner inputs during execution are limited to any policy change from these
defaults, consented live test identities/workspaces and any separately necessary
test allowance. No alternate email, password sharing or account conversion is
required to make an administrator use the new interface.

Current delivery: planning/documentation only. No account, membership, role,
session policy, UI route, backend behavior, runtime or production state changed.
