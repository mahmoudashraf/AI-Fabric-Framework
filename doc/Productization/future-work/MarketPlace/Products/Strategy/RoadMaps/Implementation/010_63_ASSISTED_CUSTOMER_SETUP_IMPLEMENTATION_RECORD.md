# 010.63 Simple Assisted Customer Setup

## Approved Scope

2026-11-06: implement only the simplified account/setup workflow from
[010.63](010_63_ASSISTED_CUSTOMER_SETUP_AI_BEHAVIOUR_AND_DEALERSHIP_OPTIMIZATION_PROPOSAL.md).
The AI behaviour, suggestions, public research and dealership-quality proposals
remain separate, unimplemented scope.

1. Create/select the intended customer workspace.
2. Prepare its real customer administrator account.
3. Staff configures a complete customer-owned Kit, Smart Brain and Engage app.
4. The customer signs in to the same workspace using existing authentication.

No temporary Gmail account, email rename, ownership transfer, impersonation,
multi-workspace role model or new infrastructure lifecycle is introduced.
Global LoomAI administration remains. The owner's follow-up request adds
customer-controlled operator collaboration grants without claiming to revoke
global administration.

## Progress

- Implemented: staff-authorized customer-scoped Kit catalogue, creation, list,
  read and update entry points over existing services. Underlying bootstrap and
  staging-consumer configuration accept explicit staff-authorized customer scope.
  Customer routes remain unchanged; the actual staff principal remains in audit.
- Implemented: public-template preparation reuses the existing entitlement
  ledger and configured public-offer list. It requires a real active customer
  administrator and never revives or reassigns an existing revoked entitlement.
- Complete: real-email account preparation reuses user administration with a
  generated, undisclosed local password. Same-customer active administrators are
  reused; foreign/staff/disabled/revoked/conflicting accounts are rejected.
  Existing signup/recovery/sign-in routes are presented for manual handover;
  no invitation email or new provider identity is created by staff preparation.
- Complete: Customers -> Set up kit; explicit customer selector, shared setup
  form, staff Kit list/configuration and Give customer access dialog. The
  existing Brain editor and exact customer/installation Engage editors are
  linked directly. Staff/customer transports and caches are separate; switching
  company clears the previous setup and suppresses late async responses.
- Complete: final focused backend, persistence, browser and production-build
  verification. See exact scope below.
- Deployed and hosted-qualified on staging and production, 2026-11-06. See
  the rollout and cleanup evidence below. Existing customer apps and runtime
  assignments were not changed.

## Authentication Boundary

Use existing account creation, verified Supabase signup/sign-in and the existing
one-use account-recovery flow. A locally prepared account is not an authenticated
Supabase membership until that established verification succeeds. The generated
local password is never displayed or handed to the recipient.

An existing active administrator of the same customer can be reused. An email
belonging to another customer, a staff account, a disabled user or a revoked/
conflicting identity link must not be silently reassigned or reactivated.
Preparing access is not the same as sending an invitation email.

## Resource Boundary

Kit creation remains configuration-only. Normal existing editors, versioning,
release apply/verification, consumer assignment, resource quotas and billing
boundaries still control going live. The target customer's connections and
credentials are used; no customer business database is created by this change.

## Initial Assisted Setup Verification

- Backend: **338 passed, 0 failed, 0 errors, 0 skipped**, across 16 suites:
  ExperienceKitServiceTest, SupportExperienceKitServiceTest,
  StaffExperienceKitServiceTest, StaffExperienceKitControllerTest,
  CustomerDeploymentBootstrapServiceTest, CustomerSelfServiceCatalogueServiceTest,
  CustomerOfferEntitlementServiceTest,
  CustomerStagingConsumerConfigurationControllerTest,
  CustomerStagingConsumerConfigurationPostgresTest,
  CustomerDeploymentBootstrapPersistenceIntegrationTest,
  ExperienceKitMigrationPostgresTest, StaffCustomerAccessServiceTest,
  StaffCustomerAccessControllerTest, CustomerAccountRecoveryControllerTest,
  CustomerAccountRecoveryPostgresTest and PlatformUserAdminIntegrationTest.
- Real JPA/transaction and PostgreSQL tests prove staff ownership/audit identity,
  customer access to the same resource, configuration binding, existing recovery
  and isolation. Provider email-token exchange is a controlled local test
  provider, not an actual Supabase email-delivery claim.
- UI unit/API tests: **9 passed** via `node --test scripts/staff-experience-kits.test.mjs src/components/customer/CustomerAccessDialog.test.mjs` from `Platfrom/ui`.
- Browser: **11 assisted Kit scenarios + 8 account-access scenarios passed** via
  `node scripts/staff-experience-kits.browser.mjs` and
  `node src/components/customer/CustomerAccessDialog.browser.mjs`.
  Covers create/update, customer switching, stale responses, account conflicts,
  access-preparation retry, preserved draft, role denial, customer-only flow,
  recipient instructions and basic mobile layout. Network responses are local
  fixtures, not hosted resources. Desktop/mobile screenshots were inspected.
- `npm run build` passed, including TypeScript and Engage preview generation.
  Existing large-chunk/runtime-config warnings remain; no new build failure.
- No framework, AI prompt, model, retrieval/action policy, runtime image, new
  database or schema migration changed. No hosted account, email, business write
  or customer infrastructure was created, deleted or moved.

The existing customer bootstrap request shape and malformed-request validation
order remain covered by regression tests. Kit creation still does not publish,
apply, spend provider allowance or activate an app.

## Use And Hosted Status

See the [assisted setup guide](../../../../../../../../Final_Documentation/External_User_Guides/LOOMAI_ASSISTED_CUSTOMER_KIT_SETUP_GUIDE.md).

Staff routes use `/customers/{customerId}/kits`, with `/new/{code}` and `/{id}`
for setup and configuration. The backend uses
`/api/staff/customers/{customerId}/experience-kits` (global administrators or
customer-granted operators) and `/api/staff/customers/{customerId}/access`
(global administrators only). Customer-facing Kit routes and payloads are unchanged.

Implementation is committed, pushed and hosted-qualified on staging and
production. Hosted staff/customer sign-in and the configuration-only setup
journey passed. No invitation feature was added, and no mailbox-delivery claim
is made: test identities used the existing verified account-recovery contract
without sending mail. Existing authentication delivery/configuration is unchanged.

## Follow-Up: Customer-Controlled Staff Collaboration

2026-11-06 owner request: customers can give/revoke access for an existing
LoomAI staff account/email. Implemented and qualified locally and live on
staging and production.

- Customer **Account & team -> LoomAI staff access**: grant by exact existing
  operator email, list grants and confirm revocation. No staff account creation,
  invitation engine, email change, shared password or principal impersonation.
- `/api/customer/staff-access`: customer-session-only list, JSON grant and revoke.
  Customer scope comes from authenticated ownership, never a caller-supplied ID.
- Existing Platform PostgreSQL stores the grant/audit data. Migration
  `V235__customer_staff_access.sql` adds one relationship table, unique customer/
  staff membership, foreign keys and ACTIVE/REVOKED consistency constraints.
  This is not a new service, per-customer database or resource copy.
- Active grants authorize product management in that workspace, including new
  template configuration and existing permitted release operations. They do not
  grant user-directory, staff-administration or global-secret privileges.
- Staff retains PLATFORM_OPERATOR and uses **Customer workspaces** plus the
  existing scoped Kit/Brain/Engage editors. The actual staff actor is audited.
- Each subsequent request checks the live grant; revocation is retained as an
  explicit denial ahead of older deployment assignments. Customer/staff role
  changes, inactive accounts, foreign workspace access and non-session staff
  credentials cannot use a collaboration grant.
- Global Platform administration and existing privileged machine keys remain
  separate infrastructure authority. Legacy deployment assignments with no
  grant history retain their existing scope and are not listed as grants; after
  a grant is revoked they cannot bypass that operator/workspace denial.
- Revocation does not undo previous saved changes or cancel work already
  executing. Regrant reuses the same relationship; grants never change ownership.

No framework/runtime, AI prompt, semantic decision, model/provider, deployment
assignment or hosted account has been changed by this follow-up.

### Final Local Verification

- **593 backend checks across 27 suites passed**, zero failures/errors/skips in
  the final reports. The broad run passed 592/593 initially; an unrelated old
  storage test asserted migration 213 was the latest despite migrating to
  latest. It now verifies that its required migration 213 succeeded. All 17
  checks in that storage suite passed on the targeted rerun. No storage service
  behaviour changed. JPA test slices explicitly import the new access policy.
- Real PostgreSQL/cookie HTTP proof: customer grant -> same operator session
  reads only that customer's Kits, Engage and Smart Brain versions -> revoke
  denies the same routes, including an existing legacy deployment assignment
  -> regrant restores access. The account/role is unchanged throughout.
- Persisted operator bootstrap verifies customer ownership, actual staff audit/
  command identity and denial of stored command replay after revocation. The
  bootstrap factory is a local test double; no infrastructure was created.
- **37 UI unit/API/auth tests passed**. **31 browser scenarios passed**:
  12 assisted Kit, 11 customer staff-access and 8 access-preparation scenarios.
  Covers grant/revoke/regrant, confirmations, foreign receipts, scope changes,
  late responses, operator editor navigation and desktop/mobile bounds.
- Browser qualification found and fixed a real shared transport cancellation
  bug: `platformApi.ts` replaced the caller signal with a timeout signal after
  asynchronous credential resolution and could miss an earlier cancellation.
  It now checks caller cancellation before and after resolving credentials.
  Unit and real-browser tests prove an aborted grant cannot dispatch using the
  next customer's token. Existing setup/access browser regressions also pass.
- TypeScript and production build passed after the transport fix. Existing
  runtime-config/chunk-size warnings remain. `git diff --check` passed.
- Browser network results are local fixtures; PostgreSQL/cookies are local
  integration tests. No claim of real hosted staff/customer qualification,
  delivered invitation mail, provider spend or live deployment is made.

The verification above describes the local qualification before source commit
`431dddf55e899ceaa9fbbd7cdd869be4b5cf7ccd`. Hosted qualification follows.

## Hosted Release And Verification

2026-11-06, completed approximately 22:15 UTC:

- Source committed and pushed on `Platform-V11`: **431dddf55**.
- [Core image CI 42300386618](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42300386618)
  passed for that exact source. Only Platform backend and Console images were
  deployed. No framework/runtime, demo, model or prompt release was needed.
- Platform database backups were taken on both hosts before migration, using
  PostgreSQL custom-format dumps in restricted host backup directories. No
  whole-Coolify backup or customer storage/data migration was performed.
- Staging backend job `giumnmaocrmdyugrdi5i25i0` and Console job
  `t3yzpha4anfertc8uabcdmwf` finished. Both containers are running/healthy and
  their OCI revisions equal the source commit; migration **235:true**.
- Production backend job `tnt2aixejsjr8zxeyh280rrm` and Console job
  `qedvrl7tx3tr7hzzx6iw5c1j` finished with the same exact healthy images and
  migration **235:true**. Both public backend health endpoints returned
  **HTTP 200 / UP**.
- Real browser qualification in each environment: ordinary staff sign-in,
  explicit customer selection, access-preparation dialog, shared dealership
  Kit setup, saved branding update, customer Supabase sign-in, same customer
  Kit, Account & team grant/revoke/regrant controls, and operator Customer
  workspaces navigation. No mocked browser API responses or page errors.
- Real API checks in each environment: ungranted operator denied; granted
  operator reads the customer's Kit, Brain draft and Engage installation;
  unrelated customer and user directory denied; revoke blocks the same session
  on those product routes; regrant reuses the record and restores access.
  Brain denial returns 404 to avoid disclosing an inaccessible resource;
  customer Kit/Engage routes return 403. Operator role remains unchanged.
- Audit readback records Kit creation/update as the actual PLATFORM_ADMIN and
  grant/revoke as CUSTOMER_ADMIN, not an impersonated customer principal.
- At a 390px mobile viewport, the new staff-access panel ends at 374px and
  fits correctly. The older Account/user-management page has a **4px document
  overflow** and an overlapping legacy subheading. These cosmetic issues are
  outside the new panel and recorded, not claimed fixed by this release.

### Fixture Retirement And Boundaries

Both exact temporary configuration-only Kits, Engage installations, consumers
and Brain configurations were deleted through normal Platform APIs. Deletion
operations succeeded without hard-delete or guard overrides. Their grants were
revoked, fixture operators disabled, customer identity links revoked and the
temporary Supabase identities deleted. No infrastructure or paid AI calls were
created by this verification.

Empty fixture customer/workspace metadata and immutable audit history remain.
The existing last-customer-admin protection prevents disabling that final
record; it was not bypassed. Its local password was generated and undisclosed,
and its provider link/identity were revoked/deleted. No reusable login was
published. Existing customer products, accounts and runtime assignments remain
unchanged.

Redacted evidence: [hosted verification](../../../../../../../../verification-support/assisted-customer-setup-01063/2026-11-06-hosted-verification.json).
Detailed release responses, backups, fixture credentials and screenshots are
retained only in restricted private qualification files. Other 010.63 AI
behaviour/research/suggestion proposals remain unimplemented.

## 2026-11-07 Approved Section 4 Implementation In Progress

The owner replaced the default-off/advanced-toggle approach with complete
removal of the framework closer matcher, its phrase lists and property. The
two superseded Platform commits are dropped from the local branch, with a
private/local recovery reference. Unrelated worktree diff content was preserved;
only changed-base blob hashes differ. Final publication uses an exact guarded
force-push lease, after replacement-source qualification.

- [x] Preserve unrelated work and rebase the implementation on 8500671c0.
- [x] Update the approved section 4 and operating rule: no closer toggle; other
  audited defects and web search deferred.
- [x] Implement canonical revisioned profile catalogue/composition and existing
  draft/publication integration. Preserve raw customer instructions and controls.
- [x] Implement bounded existing canonical next-step transport and explicit Off
  policy; profiled deployments use same-turn recommendations rather than a
  second attachment-triggered AI call. This does not fix other legacy fallbacks.
- [x] Finish shared Console simple/Advanced editor and its browser qualification:
  8 unit and 8 browser scenarios, TypeScript/build, desktop/mobile inspection.
  Canonical backend composition/publication/access regressions: 146 passed;
  runtime profile/suggestion/controller qualification: 52 passed before the
  final released-library consumer rerun. Widget canonical transport checks and
  embedded build passed. These local checks are not hosted adoption evidence.
- [ ] Qualify/publish the bounded framework removal and managed-prompt conflict
  correction, then consume immutable released artifacts in runtime builds.
- [ ] Publish replacement source/images, deploy, remove obsolete runtime
  environment entries after adoption, and qualify live customer configuration,
  preview, save/reopen, publication/apply and representative conversations.

No live completion is claimed by this checkpoint. Serving systems still run
the earlier images with temporary default-off environment overrides until the
new framework/runtime is qualified. Profile instructions are stored with the
existing prompt JSON; no new database, provider-temperature rewrite, public
research integration or semantic text-matching gate is introduced.

Framework source/tag and GitHub release now exist at
218730e5b18343576655fbd7108d63b4135f0cfc / ai-fabric-framework-v0.8.46.
989 framework tests, 100 focused checks, 30-project compile, release guards and
packaged-content inspection passed. Central workflow 42341190539 is running;
do not treat this checkpoint as Central publication or live adoption.
