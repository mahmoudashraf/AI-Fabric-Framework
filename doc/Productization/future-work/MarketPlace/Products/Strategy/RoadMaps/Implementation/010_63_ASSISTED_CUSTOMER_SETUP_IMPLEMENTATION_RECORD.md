# 010.63 Simple Assisted Customer Setup

## Approved Scope

2026-11-06: implement only the simplified account/setup workflow from
[010.63](010_63_ASSISTED_CUSTOMER_SETUP_AI_BEHAVIOUR_AND_DEALERSHIP_OPTIMIZATION_PROPOSAL.md).
At that initial checkpoint, the AI behaviour, suggestions, public research and
dealership-quality proposals were separate, unimplemented scope. The owner's
later section 4 approval and its implementation are recorded below; public
research and the other audited semantic/fallback findings remain deferred.

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

## 2026-11-07 Approved Section 4 Implementation And Verification

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
- [x] Qualify/publish the bounded framework removal and managed-prompt conflict
  correction, then consume immutable released artifacts in runtime builds.
  Central workflow 42341190539 passed. Fresh Central-only Java21 consumer:
  68 focused tests passed, all 20 framework JARs aligned to 0.8.46 with nested
  bytes matching Central and closer accessors absent; security accessors remain.
- [x] Publish replacement source using exact force-push lease against the old
  376e72622 tip. Backend/widget source 10bfdc27f; UI polish fabdd6134;
  runtime regression-fixture correction 50d1b9ab5. Core CIs 42342986880 and
  42343261248 passed. Both hosts run healthy exact-source backend/UI containers,
  schemas unchanged at 235:true. Runtime image CI 42343662497 passed all 609
  runtime tests after a real-methods fixture was supplied its optional Spring
  provider. This adds no production fallback or further framework change.
- [x] Qualify actual staging UI review/cancel, save/reopen, Custom/reapply and
  desktop/mobile controls without mocks. Real existing Supabase customer:
  catalogue/compose/save round trip passed; cross-customer composition denied
  with 404. Original prompts restored. Fresh configuration defaults passed,
  never applied infrastructure, then exact fixture deleted normally. Production
  read-only loaded/enabled editor and absent closer-toggle checks passed.
- [x] Complete all four staging runtime releases with normal verification gates.
  Existing Northfield received consultative/advisory/balanced/contextual;
  neutral Acme received warm/grounded/balanced/contextual. Existing tuned Support
  configurations received a library-only update, not a silent profile reset.
- [x] Compare the same three real anonymous canonical-message turns before and
  after adoption. Baseline: two pass, one fail; Advisory staging: two pass, one
  partial. The mixed follow-up stopped asking for internal knowledge domains,
  answered mileage, separated general motorway advice, and disclosed missing
  specifications. Stock and delivery-policy facts remained correct. Residual
  weak marketing-name inference and repeated suggestions remain visible. No
  lexical quality gate, search-all expansion or hidden retry was added.
- [x] Verify actual public workspace response-to-next-step rendering without
  mocks; one canonical recommendation appeared as a native button. Clicking its
  benign stock query submitted the exact query to the same conversation's
  ordinary message route and completed another read-only search.
- [x] Finish production adoption: Northfield rel-b329b925 / ver-5557856a and
  Formal Support rel-1f5533c5 / ver-18864bc3 are APPLIED_VERIFIED / PASSED.
  Existing consumer assignments were updated normally; app identities remained.
- [x] Remove all 12 obsolete Coolify environment records across six runtimes,
  only after each native image was upgraded. Normal same-image rolling updates
  completed; all six processes are healthy with the flag absent, security not
  disabled, and all other desired environment settings unchanged.
- [x] Finish hosted applied/seven-string prompt-preview, handover-form and
  bounded action-safety evidence; see final closeout below.

Approved section 4 is implemented and hosted-qualified on staging and
production. Four staging and two production runtime releases passed. Temporary
overrides were removed only after each native image was upgraded.
Profile instructions are stored with the
existing prompt JSON; no new database, provider-temperature rewrite, public
research integration or semantic text-matching gate is introduced.

Framework source/tag and GitHub release now exist at
218730e5b18343576655fbd7108d63b4135f0cfc / ai-fabric-framework-v0.8.46.
989 framework tests, 100 focused checks, 30-project compile, release guards and
packaged-content inspection passed. Central publication succeeded. Broad
Framework Build 42340697318 retains the known Incident Room authorization
failures (14); provider-registry/vector jobs passed. This unrelated exception is
not hidden or fixed here. The initial consumer/runtime CI attempts before Central
availability failed resolution; another runtime attempt exposed six missing
test-fixture provider errors. Those attempts remain recorded; the final image
run is green, not a claim that every earlier attempt passed.

Three existing integration sets rejected apply because their approved target
fingerprints predated target-profile changes. Supported staff review and real
customer binding APIs renewed the same endpoints/config/account scope and
credentials for the new versions. Target-fingerprint changes were confirmed;
no approval, ownership, credential or recipient check was bypassed.

Actual preview qualification exposed a narrow existing caller defect: POC chose
the legacy query route when canonical conversations were enabled independently
of handover. Source bb73fb08f now chooses the canonical message route from the
published provider flag before sending, retaining idempotency and exact scopes;
there is no retry to another route. Seventy-two focused caller/compiler/access
checks passed, CI 42345549889 passed, and both backends run that exact healthy
image. A subsequent private-auth 401 was traced to obsolete global POC
credentials; the runtime correctly required target-bound keys and its own
issuer/audience. Source 0f92d9505 reuses the exact serving release, shared target
resolver and authority service, retaining the actual principal, previous stable
POC session, explicit scopes and endpoint ownership. No global fallback or
broader accepted issuer was added. Auth-context requests only `chat:auth-context`.
One narrow issuer overload preserves server-derived session continuity while
the existing signature and all signing/access/expiry guards remain unchanged.
Final focused verification: 107 backend tests (POC 40, prompt session 2, target
context 22, authority 43) and 9 runtime auth/context checks passed. An unmatched
private-assertion test selector contributed zero tests; no coverage claim is
made for that selector.

Live next-step button inspection also found an existing generic handover wire
format mismatch: the manifest supplies `id` and lower-case field types, while
the form expected `key` and upper-case types. Source baf6ea332 adds one formal
adapter at the form boundary, preserving IDs, requiredness, consent, options and
limits. Unsupported field contracts are visible and cannot silently submit
incorrect contact data. Six unit and seven browser scenarios, TypeScript and
widget build passed. Required-field behaviour is qualified locally; Northfield's
live fields are optional. Final core-image/live qualification passed as recorded
below. Runtime production code and framework artifacts are unchanged
by these two caller/UI integration corrections.

### Final Hosted Closeout

Completed 2026-11-07, approximately 06:40 UTC:

- Core source **baf6ea3328a1ef72d83d01552ffbc02da1582105**, CI
  **42347245959** passed. Both backend and Console containers on both hosts are
  healthy at that exact revision; backend schema remains **235:true**.
- Staging backend/UI jobs: `g11g7w7oyr72yz9nd2fatdga` /
  `j9fa47kjhdhbp8m08plhl8p9`. Production backend/UI jobs:
  `q5ml24rkz69j2zx7yeb172pb` / `po2g4oqyurwk464xfqywazwl`; all finished.
- Serving runtime source remains **50d1b9ab5e615227169ddb9fc368e476840990ee**,
  digest `sha256:c1c28e3b8007981596d46a9b102c442d15f17cf20d9009545c8954c4ab47c5f7`.
  All six release gates passed on framework **0.8.46**. Actual serving-image
  inspection found all 20 framework JARs at 0.8.46; core/execution bytes match
  Central. A helper initially counted two private accounting JARs as framework
  JARs; the corrected classification distinguishes their private product version.
- Additional auth regression CI **42347246040** passed after the final test-only
  runtime change. No runtime production code changed, so its redundant newly
  built image was not redeployed; the qualified serving digest remains above.
- Real existing customer private auth-context passed with only its explicit
  scope and stable POC session. Applied prompts and two seven-string previews
  (Grounded/Brief and Exploratory/Detailed) returned successful model responses.
  Preview does not simulate provider settings or a changed runtime Off policy.
  Tone/length are guidance, not deterministic output guarantees. The neutral
  Acme request correctly did not invent an official contact policy absent from
  its evidence. One check during the rolling backend transition hit the old
  caller; final checks ran after the exact deployment finished.
- Fresh staging and production workspace forms load widget
  `f67a40e41aa0d383`. Unique IDs, proper text/email/tel inputs, independent
  values, 120/254/40 limits and configured email/phone consent passed. Both
  panels closed without a handover submission; zero AI requests were used for
  these form-only checks. Live fixture fields are optional; required-field
  blocking is local browser evidence, not a claimed live required-field run.
- Bounded production safety checks preserved the anonymous-write denial and
  the trusted-selection boundary. A private POC request without a selected
  attachment correctly remained unsubmitted; its incomplete draft cancelled.
  No final enquiry confirmation or completed business write is claimed by this
  sample. Existing focused runtime tests cover confirmation contracts.
- Production's same three-turn conversation replay returned successful stock,
  mileage/advice and policy answers. Manual assessment remains two pass / one
  partial; suggestion relevance and weak suitability inference are documented,
  not hidden by a filter or extra retry.
- Final production Console desktop/mobile readback passed with the four selected
  profiles, no closer toggle and no page errors. No new infrastructure/database,
  model-temperature change, vector copying or template lock was introduced.

Evidence: [hosted verification](../../../../../../../../verification-support/ai-behaviour-01063/2026-11-07-hosted-verification.json),
[Central consumer](../../../../../../../../verification-support/ai-behaviour-01063/2026-11-07-central-consumer.json),
[serving JAR identity](../../../../../../../../verification-support/ai-behaviour-01063/2026-11-07-hosted-runtime-jars.json),
[manual quality comparison](../../../../../../../../verification-support/ai-behaviour-01063/2026-11-07-quality-comparison.md).
The four other audited semantic/fallback defects and public web search remain
deferred. The separately documented broad framework Incident Room failure is
not relabelled green by these focused/product checks.

## 9 November 2026 - Inventory Evidence Completion

Source `49024a2be` adds provider-owned optional stock facts, generic array/null
preservation and immutable DATA/READ/template evidence revisions. Both backends
are at schema 239; Northfield `ver-689ff79c` is verified on staging
`rel-5846d684` and production `rel-1cf5f756`, retaining existing app identities,
AI settings and enquiry tuning. Framework remains 0.8.46. All five triggered
CI workflows and the focused local checks passed.

Six records indexed independently on each target, with zero failed work. Actual
RAG, READ and UI detail results carry the new evidence. Missing E2 facts remain
absent in the source/index/UI, but one production model follow-up incorrectly
borrowed the E1 location. This is recorded, not masked or re-scored as a pass.
The richer policy pack still needs scoped S3 upload access, source refresh/index
reconciliation and policy-quality verification. Inventory completion is not full
policy or perfect-answer acceptance.

See [full evidence record](../../../../../../../../verification-support/ai-behaviour-01063/2026-11-09-dealership-evidence-completeness.md).

## 13 November 2026 - Policies, Answer Integrity And Defaults

The owner requested all three remaining optimisation workstreams. They are not
all closed. See the [current qualification record](../../../../../../../../verification-support/ai-behaviour-01063/2026-11-13-policy-fallback-defaults.md).

- Fresh policy previews and four actual staging queries confirm that the richer
  approved fictional Northfield pack is still not active. Required for production
  has no qualified current uploader and has presented the exact six-object
  upload grant for owner review. No IAM/bucket permission or source object changed.
- Widget answer preservation is committed/pushed as `7d468d5f6`. Local narrative,
  structured-card, suggestion, source, confirmation, authentication and compound
  checks, TypeScript and build passed. Core image CI `43363826271` passed. Hosted
  rollout and contract checks are recorded separately below when complete.
- Source audit confirms three generic framework fallback paths and an
  unconfigured second RAG generation call. A bounded correction was proposed
  for explicit owner approval; no framework source/release changed in this cycle.
  A successful write must not be relabelled failed or replayed because narration
  fails. Relationship behavior stays deferred.
- Six actual defaults queries were observed once: stock, mileage/advice follow-up
  and sparse E2 on both environments. Staging confused range with mileage;
  production supplied correct odometer values. Both retained missing E2 facts.
  These imperfections are recorded, not hidden by a hard semantic validator or
  answer rewrite. This is not a demand for perfect model output; final template
  adoption waits for the complete source/failure-behavior qualification.
- Source-only template candidates preserve all unrelated manifest fields and
  select managed instructions from the existing profile catalogue. Thirteen
  builder test groups, 29 existing backend tests and actual native profile
  composition passed. No template version/default was published or activated.

Widget hosted closeout: both backends and both Console UIs are native-verified
healthy on `7d468d5f6`. The actual hosted asset is `f9dc5c5a96c7c9bf`. One real
stock query per app and separate authored/missing/error narration UI contract
replays passed, with inspected desktop/mobile screenshots and no captured page
errors. The first staging checker used a non-shadow-aware selector; its failed
report and original real response remain retained, and subsequent UI checks
replayed that response without new inference. Staging's actual stock answer
again borrowed the E1 town for E2; it is a retained model-quality miss, not
rewritten by this correction. No customer runtime/app assignment was upgraded.
See the [bounded hosted proof](../../../../../../../../verification-support/ai-behaviour-01063/2026-11-13-widget-hosted-proof.json).
