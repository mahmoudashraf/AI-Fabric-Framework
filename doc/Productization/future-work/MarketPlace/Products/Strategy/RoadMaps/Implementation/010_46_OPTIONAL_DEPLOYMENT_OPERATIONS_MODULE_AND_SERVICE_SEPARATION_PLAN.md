# 010.46 Optional Deployment Operations Service Separation Plan

**Status:** Direct-service implementation authorized and in progress; customer-journey hosted acceptance pending.
**Date:** 24 October 2026.
**Source baseline:** Initial inventory at `9cc6721f5c4c6c00138d8561477e2d83149575d3`; boundary and database recommendations rechecked at `924c05fc39c17b97a8af4ae7bada8af871b4f833`, with unrelated working-tree changes left untouched.
**Scope:** Platform/product operations, not AI Fabric semantic orchestration.
**Initial operating model:** The integration team decides whether and where a deployment can run.

## 1. Decision And Intended Outcome

Make automated deployment capacity, quotas, admission reservations, staging-expiry notices, automatic reclamation, graceful draining and advanced recovery optional operational capabilities.

The basic product journey must work without those capabilities being installed, enabled or reachable:

1. An authorized user selects a product/template and configures its integrations.
2. The integration team permits deployment to an approved target through existing access/target configuration.
3. Platform publishes and applies the configuration, provisions the actual required resources and verifies them.
4. The user creates an Engage app and uses the deployment.
5. The user promotes to production and switches the app after production verification.

In this initial model, no capacity sample, capacity reservation, expiry-notice transport, drain certificate or advanced recovery approval is required solely to advance that journey. The separate operations service is delivered directly, but remains optional to that journey.

**Verdict:** The separation is justified. Operational policy is intertwined with customer authorization, release execution, provider funding, source operations, promotion and the console. A configuration flag that only stops workers would not achieve the requested result. Deliver the small core boundary, a real manual-team path and a standalone service together; moving the old coupling unchanged into a service would not help.

This is not permission to bypass tenant isolation, customer authorization, credential ownership, target identity, required integrations or real deployment verification. Nor does it make failed provider operations successful.

### 1.1 Approved Delivery Decision: Direct To Service

**Owner decision, 24 October 2026:** Fix the coupling and deliver the separate service directly. Do not require an intermediate module-only release. The system is under development, there are no external customers and downtime is acceptable. This supersedes the earlier module-first recommendation and its sequencing estimates; it is not permission to misreport provider effects or alter recorded financial history.

Core keeps each control plane's existing database, adding a core-owned release-authorization record. The service gets its own PostgreSQL database and credentials in its first delivery. The database may use an existing PostgreSQL host to avoid another database server; this is ownership isolation, not physical fault isolation. There is no required intermediate schema-only migration.

The critical work is replacing direct admission dependencies with genuine core authorization and a bounded HTTP policy contract. Moving unchanged classes/tables into another process would not achieve that. The service may not query Platform tables, store provider administrator credentials or become a dependency of customer chat/indexing requests. Financial history stays with its financial owner and deployment-local AI state stays with the deployment.

The scope is Platform operations, not a new database beside every dealership. Dealership inventory, customer documents and AutoTrader remain customer/external systems. Existing staging and production control-plane databases remain separate from one another.

The main dealership-quality stream does not depend on implementing this plan. Its existing internal/staff canary can qualify answer settings independently; doing so is not evidence that the customer/manual product path described here has been implemented.

## 2. Current State: Verified Findings

### 2.1 Size And Packaging

Measured from the source tree on the date above. Counts are Java files, including records/interfaces/helpers; they are not counts of independently deployable services or executed tests.

- Platform `deployment/admission`: **82 main Java files, approximately 13,182 lines, 83 test Java files**.
- Platform `deployment/retirement`: **31 main Java files, approximately 3,686 lines, 14 test Java files**.
- Platform `deployment/generation`: **5 main Java files, approximately 780 lines, 3 test Java files**.
- Platform `deployment/budget`: **25 main Java files, approximately 2,819 lines, 22 test Java files**.
- These four packages total **143 main files, approximately 20,467 lines, 122 test files**. Their responsibilities overlap; this is not an instruction to move every line.
- **26 main Java files outside all four packages** directly reference their fully qualified types. This is a lower-bound coupling measure: it excludes indirect dependencies, SQL-only references and UI calls.
- The adjacent `deployment/dependency` package adds **25 main files and 17 test files**. Dependency provisioning is not all optional policy; it must be split by responsibility.
- Runtime `lifecycle` has **15 main files**; Runtime `budget` has **15 main files**. They participate in admission activity, retirement, generation transitions and local usage enforcement.
- Provider accounting already has two product Maven modules: `ai-fabric-provider-budget` and `ai-fabric-provider-budget-openai`, with **3 and 11 main Java files**, respectively. They are not a separate network service.

Capacity/admission/retirement currently live inside the Platform backend artifact. The [backend POM](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/pom.xml:16) does not establish a separate deployment-operations module boundary.

### 2.2 Where The Happy Path Is Coupled

**Customer authorization and operations policy are mixed.** [CustomerSelfServiceAuthorizationService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/service/CustomerSelfServiceAuthorizationService.java:126) combines entitlement, ownership, permitted configuration and target checks with placement/prerequisite evidence. Its prerequisite validation requires a reservation when `CAPACITY` is required. Disabling capacity therefore needs a deliberate authorization/prerequisite split, not a permissive replacement authorizer.

**Releases require admission allocations.** [AdmissionReleaseCoordinator](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/admission/AdmissionReleaseCoordinator.java:112) rejects an offer-backed release without an allocation binding. [DeploymentService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java:1789) binds admission during release creation. A missing component is not currently an effective manual mode.

**Provisioning crosses admission and funding before native creation.** [DeploymentReleaseExecutionService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseExecutionService.java:306) begins admission provisioning, prepares provider usage, prepares dependencies, provisions resources, then initializes the runtime allowance. These are real sequencing dependencies.

**Financial issuance directly reads the admission schema.** [ProviderUsageAllowanceService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/budget/ProviderUsageAllowanceService.java:460) locks the admission mutex and reads reservations/reviewed manifests. [ProviderUsageAuthorizedReleaseReader](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/budget/ProviderUsageAuthorizedReleaseReader.java:44) requires admission release bindings and authorization receipts. Physical reservation and financial authorization are conceptually different, but execution currently joins them.

**Promotion/readiness and the UI require allocation state.** [CustomerGoLiveReadinessService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/service/CustomerGoLiveReadinessService.java:174) reads the current release reservation. [DeploymentPracticalPromotionService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentPracticalPromotionService.java:208) schedules staging retirement after activation. [CustomerAdmissionCommands](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/ui/src/components/customer/customerAdmissionCommands.ts:51) probes placement, lists allocations and reserves before commands. Backend-only bypass would leave the UI blocked.

**Source operations inherit admission coupling.** [DeploymentAdmissionCustomerPrerequisiteEvidenceProvider](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/admission/DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.java:120) resolves source prerequisites using release/allocation state. Document storage, audience and release ownership remain required; a healthy source must not become unusable merely because a capacity policy expired.

**Replacement incorporates draining.** [CoolifyDeploymentProvider](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/CoolifyDeploymentProvider.java:1306) invokes runtime `drainAndFence` during generation replacement. The [generation coordinator](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/generation/DeploymentRuntimeGenerationService.java:83) is more than a capacity check. Cold replacement needs its own simple execution strategy, not a fabricated graceful-drain receipt.

**Recovery also runs on ordinary paths.** [DeploymentReleaseRecoveryService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentReleaseRecoveryService.java:83) marks interrupted releases on startup and contains reconciliation/redispatch behavior; [DeploymentService](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java:500) invokes recovery during ordinary operations. Basic truthful interruption reporting should remain; advanced reconstruction/resume policy should become optional.

**Worker toggles are not a feature bypass.** [Current application settings](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/application.yml:153) can disable admission lifecycle/release workers, notices and retirement scheduling. Those flags do not remove request-time prerequisites or funding's SQL dependencies.

### 2.3 Data Footprint

Platform migrations define at least **12 admission/capacity-named tables, 2 retirement tables and 9 provider-usage tables**, including mutex/history tables. Two additional directly related tables store source-prerequisite evidence and external-index retention receipts: **25 tables in this explicitly measured set**, not the entire deployment schema.

Important migration anchors:

- [V165](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/db/migration/V165__durable_capacity_admission_and_staging_expiry.sql:2): reviews, grants, reservations, notices and events.
- [V178](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/db/migration/V178__admission_allocation_authorizations.sql:1): admission authorizations, release bindings, activity and extensions.
- [V180](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/db/migration/V180__release_scoped_source_prerequisite_evidence.sql:7): source evidence has foreign keys to admission release bindings and reservations.
- [V190](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/resources/db/migration/V190__provider_usage_allowance.sql:1), V204, V207 and V209: financial grants, allocations, immutable history, validity extensions and top-ups.

Runtime migrations V12/V13/V14/V15_2 define **8 retirement/generation/admission tables**; V19/V20/V21 define **6 provider-budget/binding tables**. These remain deployment-local data. Centralizing operations does not mean moving conversations or every runtime table to a shared service.

### 2.4 Concrete Current Incident: Do Not Confuse Two Problems

The main quality-canary stream reported `MANAGED_VECTOR_TARGET_REQUIRED` during `ensure_vector_backend`.

Verified source cause: [Coolify's caller](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/CoolifyDeploymentProvider.java:2832) uses the two-argument `ensureProvisioned(deployment, version)`. [That overload](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentManagedVectorProvisioningService.java:174) intentionally rejects provisioning that requires target ownership. [Railway's caller](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/RailwayApiProvisioningProvider.java:146) passes the release target explicitly.

**This is a provisioning caller bug, not a capacity rejection. Admission bypass will not fix it.** Fixing the actual caller is a separate, owner-reviewed code change.

The reported aftermath illustrates a second issue: funding preparation has already created an allocation, while admission is conservatively `OUTCOME_UNKNOWN`. Main reported no native vector/runtime creation and one S3-binding handle; this is not a complete no-effect certificate. This document does not clear the reservation, refund/reissue funds or claim all provider effects absent. The architectural goal is to keep the original error visible and separate that failed attempt from optional operational accounting, without demanding an abstract recovery workflow for every cold retry.

No live access, code fix, deployment or policy change was performed for this plan.

**Separate quality-stream update, 24 October13:12 UTC:** The owner subsequently
approved the narrow caller fix, now committed as `924c05fc3` and verified live on
both control planes after63 focused tests and the backend image build passed.
This does not implement this separation plan. The old failed canary's normal
retry-plan API still rejects its failure boundary with
`PREPARED_RECOVERY_CONTROL_FLOW_UNPROVEN`; no recovery or admission bypass was
added to clear it. Keep the fixed provisioning bug distinct from that remaining
operational-policy friction.

## 3. Ownership: What Moves And What Remains

### 3.1 Move Into The Optional Operations Implementation

- Fleet/target capacity measurements, sample freshness, headroom and demand admission.
- Customer resource quotas, concurrent-environment limits and reservation accounting.
- Allocation expiry, inactivity policy, extension policy, notices and automated staging reclamation.
- Graceful drain coordination, aggregate worker-fencing coverage and advanced retirement scheduling.
- Prepared-failure recovery, post-provision resume, runtime-artifact repair and automatic reconciliation policy.
- Operational policy administration, policy-specific diagnostics, metrics and advanced operator UI.
- Managed-dependency capacity policy adapters, not the actual provisioning of a required Inbox or connector.
- Optional provider-usage cap/funding policy administration, with the separate financial boundary in section 6.

Inventory, activity and resource observations should be supplied through narrow core/runtime adapters. Move orchestration/policy, not the ability to establish which resources belong to a deployment.

### 3.2 Keep In Platform Core

- Authentication, roles, tenant/customer/deployment authorization and product entitlements.
- Reviewed product composition, plugins, template configuration, immutable versions and artifact provenance.
- Credential/secret ownership, external integration scopes, storage audience rules and target identity.
- Deployment-local authority, source/index ownership, consumer assignments, routing and domain configuration.
- Actual provider create/update/stop/delete adapters and resource ownership records.
- Required dependency provisioning and initialization, including an Inbox when the selected product requires one.
- Basic release command identity, duplicate suppression, current attempt/progress, real error reporting and verification.
- Minimal per-target mutation exclusion to avoid two core workers concurrently replacing the same deployment.
- Explicit stop/delete authorization and the distinction between deployment-owned resources and customer-owned systems.

These are not optional capacity policy. Deleting a customer database, using another tenant's credentials or activating a failed runtime must remain forbidden even when all operational policies are disabled.

Existing CPU/RAM/container settings remain the requested execution shape. Disabling quota admission does not remove container boundaries, API payload bounds, connection timeouts, login protection or the provider's real resource limits. Such protections are not deployment-admission quotas.

### 3.3 Keep Runtime Execution Local

Chat, retrieval, indexing, actions, human handover and provider calls must not consult a central operations service on every request.

Optional drain/retirement endpoints and activity reporting may remain runtime capabilities behind an optional adapter. Core cold replacement must not require those capabilities. Local financial enforcement, when explicitly selected, remains local rather than being replaced by a network dependency.

No business inventory database is introduced. Dealership systems and AutoTrader remain external integrations. Customer-owned vector/object/AI-state services retain their ownership boundaries.

## 4. Minimal Boundary And Explicit Modes

### 4.1 Phase-One Boundary

Introduce one core-facing `DeploymentOperationsPort` with a small immutable input/output contract. Initially it can live in a core package; do not start by moving 143 files.

Input: server-derived customer/tenant/deployment, target, version/config hash, command ID, operation kind, requested resource shape and selected execution/funding profile. No secrets, arbitrary caller-supplied approval or raw provider credentials.

Output: `ALLOW`, `DENY` or `UNAVAILABLE`, reason, policy revision when applicable, observation time and optional reservation reference. The core records its own effective decision. A reservation is not a universal authorization credential.

Use existing deployment command/release storage where practical. Add only a compact record if necessary: command ID, actor, scope, version hash, resolved mode, effective decision, reason and timestamps. It must not require an admission grant, manifest, reservation generation, policy sample or funding allocation.

### 4.2 Three Server-Controlled Modes

- **DISABLED / MANUAL_TEAM:** Do not call operations policy or create capacity reservations. Existing authorized integration-team/product target configuration decides who can deploy. Record `POLICY_BYPASSED` with reason `DISABLED_MANUAL_TEAM`; proceed through core provisioning and verification. Do not introduce a new per-attempt approval system unless the team explicitly requests it.
- **OBSERVE:** Deploy without policy approval. Collect one deadline-bounded assessment; show what it would allow/deny and whether observation was unavailable. The first implementation uses the same bounded HTTP call as enforcement, not a background queue. No blocking reservations, expiry jobs or automatic reclamation.
- **ENFORCE:** Use a timely, authenticated policy decision. A received explicit `DENY` blocks that command. A service outage follows the fail-open admission policy below.

Mode is configured by trusted Platform administration per environment in the first delivery. Per-target mode overrides are deferred; the service itself has target-specific capacity policy. Mode is not a query field, browser setting or LLM decision. Missing optional service means manual-team bypass with a visible warning, not an application startup failure.

### 4.3 Service Unavailable Means Admission May Proceed

The HTTP adapter uses one bounded assessment call, a **1-second total deadline**, no inline retry and a 64 KiB response limit. These are implemented bounds, not measured live performance claims.

- Disabled component, absent service, connection failure, timeout or service `5xx`: effective admission `ALLOW`, recorded as `POLICY_BYPASSED / OPERATIONS_UNAVAILABLE`.
- Invalid/authentication-failed service responses are not trusted allows or denies. Surface a configuration/security alert and treat policy as unavailable; core authorization still runs normally.
- Timely, authenticated, scope-matching `DENY`: respect it. Do not convert a policy rejection into an outage to retry the same command permissively.
- Invalid customer authorization, credentials, product composition, resource ownership or actual provider errors: ordinary core failure, never fail-open.

Once core commits the effective decision, a late response cannot revoke that attempt, stop its resources or change it to denied. The service cannot issue provider commands or reclaim capacity automatically merely because its answer arrived late.

In `ENFORCE`, assessment/reservation uses the core command ID idempotently. An uncertain service response is recorded as uncertain on the service side and bypassed on the core side, not treated as a second deployment. A late reservation is non-executing and expires or is reconciled to the core result; it never gates the already-authorized apply. The service counts the deployment once using its command/resource identity.

No synthetic `ACTIVE` reservation, zero-cost allocation, fake health pass or automatic wallet refund is created. Optional delivery/readback failures must not turn the release into an unrelated recovery workflow.

### 4.4 Visibility Without Another Dependency

Keep policy status separate from runtime health and deployment success:

- `Deployment: VERIFIED`; `Operations policy: BYPASSED - manual team`.
- `Deployment: FAILED - managed vector target missing`; `Operations policy: BYPASSED - unavailable`.
- `Deployment: BLOCKED - capacity policy denied`; `Operations policy: ENFORCED`.

Record mode/decision/reason in existing release audit and expose a small status field to the UI. Add counters for bypass reason, assessment latency and policy denials; avoid a new analytics subsystem. The optional service is not part of core liveness/readiness or Engage request readiness.

## 5. Concrete Deployment And Promotion Behavior

### 5.1 New Deployment And Normal Apply

Core validates access, product configuration, target identity and required integrations; freezes the version; records the command; obtains the mode-aware operations decision; provisions; then verifies.

In manual-team mode there is no prerequisite chain through capacity sampling, physical quotas, reservations, expiry notifications or R7 evidence. Required S3/identity/provider connections are still verified through their own core contracts. Split source prerequisite evidence from the current admission-backed schema rather than issuing placeholder reservations.

Provider preflight should validate required target arguments and configuration before native effects and, where feasible, before funding issuance. This reduces known failures after allocation but does not pretend distributed provisioning is atomic.

### 5.2 Promotion From Customer Staging To Production

Promotion remains generic across products, not dealership-specific. It deploys the approved configuration/artifact to the production target using that target's own integrations and credentials; sources are independently indexed there. It does not transfer vector records or conversations by default.

Manual-team mode skips production capacity sampling/reservation policy. It does not skip production verification, owned consumer binding or explicit app activation. Staging expiry/retirement is an independent optional cleanup operation, not a prerequisite to switch the app to verified production.

### 5.3 Cold Replacement Is The Initial Execution Strategy

Keep one straightforward default: `COLD_REPLACE`, with acknowledged downtime. Stop admission of new work to the old owned instance, stop its owned runtime/connector/workers through provider controls, confirm stop, start the replacement and verify it before reopening routing.

Do not require complete graceful-drain/worker-fencing certificates, empty conversation tables or a recovery-window timer. Do not fabricate such evidence. Retain owned state unless explicit data-disposal authorization exists; stopping/restarting is not deletion.

If a process cannot be confirmed stopped, show the actual stop failure for that same target. This is a genuine execution/concurrency problem, not optional capacity rejection. A failed cleanup on a different target must not globally block new deployments.

`GRACEFUL_REPLACE` can later use the operations module. If unavailable, offer/retain the explicit cold strategy; do not silently downgrade an in-flight graceful operation or claim no work was interrupted.

### 5.4 Failure And Retry

Keep the original release error and known resource handles visible. A new deliberate retry reuses the normal core idempotency/resource-ownership checks; no automatic business-action replay, duplicate native create or fresh financial credit merely because the last attempt failed.

Where the provider response is genuinely uncertain, show the uncertain resource operation and permit one ordinary provider readback/operator check. Do not label it successful or blindly duplicate it. Advanced reconstruction, repair and resume remain optional tools, not the only route to attempt a new deployment.

On backend restart, core can mark an interrupted attempt and retain its native identifiers. No required remote recovery worker, admission lease or notice-delivery proof is introduced. Existing advanced recovery records remain inspectable.

## 6. Provider Budgets, Funding And Limits: Explicitly Optional Policy

### 6.1 Separate Three Concerns

1. **Physical admission:** CPU/RAM/disk, environments, replicas, indexes and capacity reservation. Optional under this plan.
2. **Provider-use policy:** chat/token/cost caps, expiry, approved model sets and automated funding approvals. This must also have an explicit optional mode; it cannot remain a hidden prerequisite while the UI claims all limits are disabled.
3. **Financial history and identity:** who authorized whose provider credential, historical allocations, actual usage, top-ups and uncertain settlements. Disabling future policy does not erase or rewrite these records.

The current Runtime budget feature is conditionally installed, but [ProviderUsageReleaseCoordinator](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/budget/ProviderUsageReleaseCoordinator.java:39) forces it on for offer deployments. The current [budget health grouping](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/budget/RuntimeBudgetHealthGroups.java:18) includes accounting in readiness when present. Both composition and readiness must reflect the chosen mode honestly.

### 6.2 Proposed Trusted Provider-Use Profiles

- **CUSTOMER_KEY / PROVIDER_MANAGED:** Customer supplies an owned provider credential. No LoomAI prepaid allocation or R7 reservation is needed. The provider's own account/quota limits apply; optional LoomAI metering/caps may be enabled. Credential scope and allowed provider endpoint still belong to core.
- **TEAM_AUTHORIZED / OBSERVE:** An authorized integration-team administrator explicitly selects a payer/credential profile for a new deployment, accepts provider charges and disables LoomAI blocking chat/token/cost caps. Native usage is observed where available. No fabricated funded allowance is required to bootstrap or serve AI traffic. This is a real potentially uncapped exposure, not a promise of bounded spend.
- **FUNDED / ENFORCE:** The team intentionally selects finite funded allowances and local runtime enforcement. Existing immutable approval, usage, top-up and uncertain-attempt semantics remain. Its funding authorization must depend on a core owned-release authorization, not a physical capacity reservation.

These are proposed profiles, not existing supported UI options. New manual-team deployments need at least one implemented non-funded option to prove the requested independence. A capacity flag alone is insufficient.

Provider profile is trusted release configuration. A browser, anonymous chat or arbitrary deployment YAML cannot turn a shared paid key into unlimited usage. Capacity service outage never changes a deployment's payer, credential, model authorization, funded terms or local usage history.

### 6.3 Existing Funded Deployments And Accounting Ownership

Do not switch an existing funded runtime to observe/unlimited mode merely because the operations service disappears. A trusted operator can explicitly authorize a newly published provider-use profile; record the transition and keep the prior history. Historical `RESERVED`/`AMBIGUOUS` attempts must not become refunded or settled by that transition.

Keep the existing financial ledger independently owned and retained, initially in the current stores/product modules. Move optional cap/funding-policy orchestration into operations, but do not make financial history part of a disposable capacity database. A network-service outage must not take down existing local provider accounting.

For `FUNDED / ENFORCE`, missing actual funding can still block provider use because that was the explicitly selected contract. For `TEAM_AUTHORIZED / OBSERVE` or customer-key mode, absence of a grant must not block deployment. The UI must state which case applies, rather than making an unconditional unlimited-deployment claim.

**Recommended initial permission:** Only `PLATFORM_ADMIN` may select the team-authorized shared-key profile, scoped to explicitly configured integration-team targets/customers. Reuse the existing role; do not introduce a new role or per-deployment approval workflow for the first delivery. Customer users cannot select it. The owner reviews this scope before rollout: the user's task-specific spend authorization is not a reusable product-wide public permission. Customer-key mode and the capacity boundary can be implemented independently.

## 7. Direct Service Boundary And Data Ownership

### 7.1 Core Boundary And Independent Service

Implement the core-facing contract/client under `Platfrom/backend/.../deployment/operations/` and the independent application under `product-services/deployment-operations-service/`. Do not introduce an intermediate operations Maven module or make the service depend on the executable backend JAR. The wire contract and interoperability tests are the boundary.

Dependency direction: Platform core -> bounded authenticated HTTP policy API. Service -> its own repositories/database. Core owns actual provider effects, required integrations, current release authorization, command identity and final verification. The service owns operational assessments and observations, not customer authorization or financial records.

The manual/outage decision stays in core and must run with the service stopped. Split mixed responsibilities rather than transferring the entire admission package: source ownership, required dependency provisioning and security must not disappear with optional capacity policy. Legacy history evaluators may remain for inspection; they must not become hidden prerequisites or scheduled destructive workers for new releases.

Build/test/publish the service independently. Backend and UI use their existing build paths. Do not tie this to a public AI Fabric framework release or require a new Runtime image where existing explicit feature configuration is sufficient.

### 7.2 Data Ownership And Migration

#### Core Store And Preserved History

Keep existing Flyway history and recorded financial/operational rows unchanged. New core release authorization uses `platform_release_authorizations` in the existing control-plane database, with exact customer/tenant/deployment/target/version/artifact and provider-use mode. New manual commands have no admission reservation or invented financial allocation.

Core must not read an optional policy table to authorize a new release. The service receives bounded, server-derived identities and demand. Financial issuance for new releases consumes core release authorization rather than joining admission tables. Existing funded contracts and their historical fingerprints remain enforceable and are never silently changed to uncapped use.

New source commands use the owned provisioned release and unchanged document/integration bindings directly. They do not create V180 source evidence tied to an admission reservation. Retained V180 rows and foreign keys remain historical; physical relocation is unnecessary for the new source path. No fake R7 payloads or fabricated connector health are permitted.

Core retains its existing migration runner. The service has an independent migration location and database user; it must never run core migrations. Do not edit published checksums or let two migration runners own the same tables. New core migrations must not introduce another mandatory reference to an operations reservation.

#### No Required Intermediate Schema Move

A new schema inside the Platform database would not remove application coupling or provide independent service ownership. Skip moving tables twice. Retain old operational history where it is; the service starts with its own policy/observation records. Do not migrate disposable developer fixtures merely to populate the new service.

If historical operational records are later needed by the service, use an explicit maintenance-window copy/read-only archive with one writer. Financial records and runtime state are not part of that copy. A schema name alone gives no isolation when the same unrestricted database user still has access to everything.

This remains a shared database and failure domain. A separate schema does not make a database outage survivable, and same-database joins or transactions must not become the module's public contract.

#### Service Database From The First Delivery

Give the service its own operations database/user and use opaque core IDs, not cross-store foreign keys. The database may use an existing PostgreSQL host if operationally suitable; a second database on that host does not provide host-level fault isolation. Separate database infrastructure is a later operational choice, not a prerequisite for a clean service boundary.

Use the accepted maintenance window to stop legacy automatic workers and verify no destructive effect is in progress before cutover. Preserve required audit history. Do not introduce bidirectional synchronization, dual writers or distributed transactions. Core retains its own command decisions and resource ownership even if the service has no records for a manual deployment. Start in disabled/observe mode; an empty operations database is not evidence that existing servers are empty.

Financial records stay with the financial owner; runtime conversation/index/budget records remain deployment-local. The new store is for operations policy, not an additional inventory database for each customer deployment. Legacy financial or runtime tables must not move merely because their names contain `budget`, `generation` or `retirement`.

No dual-write period, indefinite dual-version support or migration of every historical fixture is required. A maintenance window is acceptable. Preserve audit history and migration checksums; archive rather than drop records merely to unblock the new path.

**Availability boundary:** An unavailable optional module/service/store must not stop a new authorized manual release while core storage and the provider are working. An unavailable core database, invalid authorization or failed native provider operation remains a real deployment failure. Fail-open policy is not a promise that Platform can deploy without its own source of truth.

### 7.3 Optional Service Availability

Service host: `product-services/deployment-operations-service/`, behind an authenticated private assessment/outcome API. Core uses the bounded HTTP client and outage behavior in section 4. There is no module-only release in between.

The service holds policy and operational reservations, not provider administrator credentials. It may propose maintenance/recovery work; core executes an explicitly authorized command against owned resources. Service unavailability means automatic maintenance waits, not that new deployments or existing Engage traffic stop.

Start with admission assessment and status only. Do not require queues, distributed transactions, leader election, another operator console or a general workflow engine to ship the first boundary.

## 8. Phased Implementation And Evidence

### Phase 1: Core Decoupling And The Service Together

Deliver the service and core boundary together, then verify two vertical milestones:

**1A - A fresh staging deployment works:** Manual-team mode, existing security/ownership checks, a core release command, and one explicitly selected team-authorized provider-use profile. The service is independently deployable but may be stopped. No capacity measurement/reservation, notification transport or financial-allocation placeholder is required. Verify the actual runtime, indexed stock/policies and Engage query.

**1B - The same product journey reaches production:** Add mode-aware promotion/app activation, source refresh and explicit cold replacement. Verify the same app against the verified production deployment. Keep the current admin/operator layout; show only a compact effective-mode/status indication where needed.

- [x] Add server-controlled mode, bounded client, compact core command/decision record and effective-policy readback.
- [x] Split security/product prerequisites from capacity/expiry/recovery policy in customer authorization and source operations (local verification; hosted proof pending).
- [x] Remove mandatory allocation binding from manual publish/apply/promotion/activation paths; keep core command/resource identity (local verification; hosted proof pending).
- [x] Implement one non-funded provider-use profile for the manual path. Preserve all financial history; existing funded deployments retain their explicitly selected enforcement contract.
- [x] Make normal UI commands mode-aware; no required probe/list/reserve calls in deployment, Engage, document or integration commands (offline browser verification; hosted journey pending).
- [x] Add straightforward cold replacement and remove legacy automatic worker registration. Inspect actual in-progress destructive operations before hosted cutover.
- [ ] Verify the complete customer journey with operations implementation absent. Record real provider failures without concealing them.

**Acceptance:** Authorized customer create -> staging deploy -> Engage chat/indexed sources -> production promotion -> app switch works without measurements, reservations, expiry notices, remote operations, drain proofs or funded-allocation placeholders. Required product features, including sign-in and handover when selected, still work.

Decoupling the existing funded-issuance path from R7 belongs to phase 2; it is not a prerequisite for proving a fresh manual/non-funded deployment in phase 1. Do not claim funded enforcement is independently extractable until that work is verified.

### Phase 2: Independent Service Qualification And Hosted Cutover

- [x] Verify the service builds and runs without Platform/runtime artifacts or access to the Platform database (137 service tests and an actual independent-process wire test).
- [x] Verify authenticated allow/deny/observe/unavailable decisions, bounded response/deadline and idempotent observations locally.
- [x] Verify core authorization, source operations, funding and required dependency setup do not require admission records for new releases locally.
- [x] Verify PostgreSQL financial-history preservation and funded enforcement independently of optional capacity policy locally.
- [ ] Deploy the service with its own database/user and repeat the same bounded journey with the service healthy, stopped and restored.

**Acceptance:** Stopping the service changes policy availability, not core authorization or the ability to deploy/run customer products. Existing financial and operational history remains inspectable. No new recovery feature is required.

### Phase 3: Optional Advanced Operations, Not A First-Delivery Blocker

- [ ] Add graceful drain, expiry notifications or advanced recovery proposals only for a concrete operator need.
- [ ] Keep all such policy proposals off the normal customer request path; core retains authorized provider effects.
- [ ] Document difficult legacy recovery cases rather than rebuilding a universal workflow engine.

**Acceptance:** Stopping the service permits the same authorized manual journey, records a policy bypass and does not stop existing runtimes. Service return neither cancels deployed resources nor double-counts a command.

### Essential Verification, Not An Exhaustive New Gate

1. Operations absent/disabled: full customer staging-to-production journey succeeds through the real UI/routes.
2. OBSERVE reports a would-deny decision but does not block; ENFORCE explicit denial blocks.
3. Service timeout/unavailable permits an authorized command once and records bypass; a late response cannot mutate it.
4. Wrong customer/tenant/credential/resource owner remains denied in every mode.
5. Cold replacement uses actual stop/readback and verification; provider failure stays visible and no duplicate resource/business action is created.
6. Customer-key/team-observe needs no funding allocation; funded enforcement and historical usage/top-up records remain intact.
7. Healthy source discovery/indexing and Engage activation do not depend on capacity-policy freshness.

Reuse existing focused admission, funding, provider, source and customer-journey tests. Add only tests for changed contracts plus a basic PostgreSQL migration preservation check. Hard/unusual recovery cases are documented follow-up work, not an excuse to grow the first release.

## 9. Rollout, Effort, Risks And Unknowns

### Rollout And Rollback

Ship additive core command/mode support first. Freeze optional destructive workers on the chosen canary target, inspect whether any destructive task is actually in progress, then explicitly select manual mode for new commands. Do not leave an old worker free to reclaim a manual deployment because it still sees an expired legacy reservation.

Qualify one customer staging target, then promotion to one production target. Keep existing assignments unchanged until the replacement is verified. Cold downtime is acceptable; simultaneous old/new generations are not a requirement.

Rollback means disabling the new manual entry path and routing new commands to the existing adapter, not retroactively inventing reservations or funding grants for manual releases. Re-enabling enforcement first inventories/adopts existing owned resources as observations. It must not demand that every manual release reconstruct a historical R7 workflow. Prefer configuration rollback; do not roll back schema/history.

### Effort And Risk Assessment

The interface itself is small; completing the manual customer journey is **medium-to-high integration work**, not an afternoon flag change. Funding's R7 SQL dependency, source-evidence foreign keys, replacement generation handling and UI command sequencing are the main work.

The earlier module-first timing estimates are superseded. Track concrete core, service, UI and hosted milestones rather than adding a module-only project. The main integration risk is missed request-time coupling, not creating the small service image. Bound verification to the normal customer journey, real ownership denial, explicit policy denial, outage bypass and honest cold replacement; record difficult historical recovery cases separately.

Accept the initial operational trade-off openly: manual/fail-open admission can overcommit a server; team-observe shared keys can exceed expected spend; cold replacement can interrupt work. These are integration-team choices, not hidden safety guarantees. Provider-native resource failures, authorization failures and real outstanding side effects still need truthful handling.

### Unknowns To Resolve During Implementation, Not By Expanding This Audit

- Exact shared-key observe-mode authorization scope and who may select it; customer-key mode can proceed independently.
- The smallest existing command/attempt record suitable for non-R7 release authorization and retry identity.
- Which existing mixed generation/checkpoint fields can remain simple core provenance versus optional graceful-recovery state.
- One-time disposition of any actually in-progress destructive operation when switching a target to manual mode.
- Verify service database/user separation and hosted reachability during the direct cutover. Dedicated database infrastructure and high availability are not required now.

### Non-Goals And Stop Rule

No new public framework release, semantic text matching, AI fallback, business-system hosting, vector-record transfer, multi-version runtime support, zero-downtime replacement guarantee, universal recovery engine or infrastructure expansion is included.

**Stop rule:** Phase one is complete when the real product journey works with the operations component absent, real failures are clear, core security/ownership hold and the chosen provider-use mode is honest. Do not make the first delivery wait for automated handling of every uncertain historical release.

## 10. Current Tracking

- [x] Source-based architecture/coupling analysis and bounded inventory completed.
- [x] Current provisioning bug distinguished from admission/funding aftermath.
- [x] Manual-first scope, optional module boundary, provider-use modes and later service contract drafted.
- [x] Main-session review: verified package/test counts, security versus optional-policy boundary, outage semantics and manual-first milestones. Owner implementation approval remains separate.
- [x] Dedicated `Separation efforts` review: responsibilities now, module after the manual path, service later; retain current databases/tables first, make an intermediate operations schema optional, and use a single-writer operations database only at service extraction. Source dependencies rechecked at `924c05fc3`.
- [x] Owner implementation authorization: direct service, no intermediate module-only release; development downtime acceptable.
- [ ] Core/service integration and focused verification complete.
- [ ] Hosted cutover complete.
- [ ] Hosted disabled-component journey verification.

The original audit changed this document only. After explicit owner implementation approval, product code and focused tests are being changed. No live deployment, policy cutover or hosted acceptance is claimed until the execution record below contains its actual result. The separate dealership-quality stream and its assignments/financial holds remain untouched by this implementation.

### Direct-Service Execution Record

- Core release authorization, bounded HTTP policy client and independent V210 migration: implemented and locally verified. All 209 original PostgreSQL migrations plus additive V210 applied and validated; the retained financial-history fixture was unchanged.
- Core customer authorization, source binding checks, publish/apply and promotion: source decoupled from admission reservations; actual release/provider verification remains required.
- New cold replacement uses existing provider stop/readback and explicitly disables optional retirement/drain machinery for that release; it does not fabricate a drain certificate.
- New standalone service owns capacity assessments/observations and its own PostgreSQL database. No Platform/runtime/artifact dependencies, browser credentials, provider effects or central AI request routing.
- Funding separation preserves existing recorded terms/history. New team-authorized mode is server-configured for reviewed targets; `CUSTOMER_KEY` is explicitly unsupported in this first implementation rather than guessed from a shared key.
- Required managed-dependency provisioning uses exact core authority rather than R7, including real ownership, scoped secret, callback and PostgreSQL Inbox checks. Legacy automatic expiry/reconciliation/retirement workers are no longer scheduled in Platform. Customer UI final integration is in progress.
- The stable combined backend suite passed 255 tests. Follow-up client/service/readiness tests passed 21 tests after explicitly pinning ISO timestamps and skipping optional capacity-demand lookup in disabled mode. The actual standalone service JAR and a separate PostgreSQL process passed allow, enforce-deny, observe-deny, outcome and service-shutdown/bypass checks through the Platform client. These are local results, not hosted acceptance.
- Initial test-fixture problems were corrected: a checked-exception override, Mockito restubbing and cookie construction. Those failed attempts were not counted as successful verification. No framework source or release was changed.
- Main dealership quality task holds control-plane rollout until its live run is complete. No current canary, production assignment or historical financial record has been changed here.
- Core/service source is committed and pushed as `7b777686123fd1c7b05ebf71d4cf97acf34bd886`. Core image workflow `40104867575` and independent service workflow `40104867641` both passed. No framework release was required.
- Hosted service image: `ghcr.io/mahmoudashraf/loomai-deployment-operations-service@sha256:167ae0b1ce9b59bf238e45d50c0bf08327d4c86652903dd305f6a82e76fd5f44`. Staging service `liba1dobh9v9rhcedzlnnajz` and production service `yext75rcvxm7jfs236aprssw` are healthy and authenticated with no public port. Stable private Coolify aliases `loomai-deployment-operations-staging` and `loomai-deployment-operations-production` are verified from their respective control-plane containers. Credentials and private native evidence remain under `~/.codex/private/loomai-separation-01046/`, not this document.
- Each environment has one service-owned `loomai_deployment_operations` database and ordinary owner role on its existing PostgreSQL host. The role cannot select Platform deployment tables. This is not a per-dealership database, not business inventory storage and not separate physical database infrastructure.
- Hosted policy starts unconfigured, with no fleet-enforcement claim. Before enabling real enforcement, use one authoritative policy store per physical capacity pool or explicitly partition target budgets: independent developer/customer control planes and target aliases can share a physical host. An empty service database or a missing outcome after bypass is not proof of unused capacity. Fleet adoption and advanced automation remain follow-up work; integration-team manual deployment does not depend on them.
- Point-in-time hosted service measurements: staging 173.7 MiB RAM / 0.17% CPU, production 173.8 MiB / 0.07%; each has a 384 MiB native limit and 256 MiB JVM heap. These are observations, not a capacity guarantee or a new deployment-admission measurement gate.
- Pre-cutover audit: staging has no legacy retirement operations; production has two `PLANNED` and two `RETRYABLE_FAILURE` records, all at `DRAIN`, all with `destructive_started=false`; recorded leases are absent or expired. They are retained without replay/deletion. New source removes automatic worker registration even if obsolete worker flags remain true. Difficult historical recovery remains documented follow-up, not a prerequisite for the fresh customer journey.
