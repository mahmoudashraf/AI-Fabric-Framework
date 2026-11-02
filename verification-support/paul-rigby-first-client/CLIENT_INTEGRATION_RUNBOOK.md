# Client Integration Runbook

Date: 2 November 2026. Scope: bounded 010.55 L2 source-template sidecar.

This is a source/local-test operating guide, **not hosted qualification**. No
hosted provider connection, deployment, indexing, business write or AI query was run
for this sidecar. Local PostgreSQL fixtures are not client resources. The [return-to-integration status](2026-11-02-return-to-integration-status.md)
and [010.55 plan][plan] remain authoritative for work allocation and launch
gates. Main owns current buyer/Desk UI verification; this document does not close
it. Separate Support acceptance does not qualify a dealership.

## 1. Agree Scope And Owners

- Dealer sponsor: one initial branch/team, staff trial dates, acceptance reviewer,
  approved branding, policies and existing contact/enquiry path.
- Dealer/vendor technical contact: authorized stock account/feed and samples,
  media/indexing/retention/LLM-processing permission, notification contract and
  separate target credentials or an explicitly authorized staging account.
- Dealer operations owner: named Desk agents, their own accounts, exact deployment
  assignments, hours, absence wording, escalation and enquiry acknowledgement.
- LoomAI integration/operator: configuration, adapter compatibility, publishing,
  target bindings, source freshness, rollout evidence, incident and disable work.
- Named service owner for each PostgreSQL/vector/document/LLM service: billing,
  region, allowance/alerts, backups, restore, rotation, retention and data exit.
  Record actual names before launch; these role labels are not assignments.

Obtain the [provider checklist](PROVIDER_CONTRACT_CHECKLIST.md) inputs privately.
A retailer login, public listing, sample response or fictional Northfield result
does not grant integration access or prove real-provider acceptance. Outreach can
start before credentials; client launch cannot precede the applicable gates.

## 2. Reuse The Existing Composition

Use one existing external-systems family, not the simulator or a new business
backend. [V196][v196] defines `mkp-template-dealership-external-systems-v1` and
`mkp-template-dealership-external-systems-managed-inbox-v1`, with external stock
DATA, discovery ACTION, engagement ACTION and approved document dependencies.
[V215][v215] publishes Desk-ready revisions without the Basic Inbox dependency
or automatic handover extension; Desk is enabled separately on an actual target.
[V216][v216] deprecates the Basic Inbox plugin and dependent older templates.

The plan records external-systems `1.1.0` and legacy-named managed-inbox `1.8.0`
without Basic Inbox. [V221][v221] adds non-Inbox family `1.2.0`, immutable ID
`mkv-template-dealership-external-systems-v1-2`: external stock `1.0.0`, discovery
`1.1.0` and customer document knowledge `1.0.0` only. It has no required enquiry,
handover or Inbox plugin; Desk remains a recommendation/optional target activation.
This migration is local source, not an executed hosted catalogue publication.
Read the chosen hosted manifest with `GET /api/marketplace/plugins/{pluginId}/versions/{version}`
([controller][marketplace]) before use; never infer adoption from this file.

**SQL selection uses the existing customer setup contract.** The [manifest parser][templateparser]
does not allow `aiStateStorageMode` inside template `providerConfig`. Do not put
it there or invent another field. The reviewed setup must declare the existing
`AI_STATE_STORAGE_MODE` choice (single allowed value `CUSTOMER_POSTGRES`), its
`CUSTOMER_AI_STATE` binding slot and matching external-SQL topology. Customer
bootstrap must submit `aiStateStorageMode: "CUSTOMER_POSTGRES"`; the existing
[bootstrap][customerbootstrap] / [storage initializer][storageselection] persist it
in the editable draft before version publication. Selecting a Marketplace template
alone is not that storage decision. No offer or client bindings are created by V221.

The revision reuses the [recorded dealership baseline][dealerbaseline] where the
template contract supports it: GPT-5.4-mini, ITERATIVE/2,
RAG_IF_ACTIONS_INSUFFICIENT, 12,000 planner characters, 8,000 evidence characters/
10 items, two-source bounded recovery and 8 documents/8,000 context characters.
It does not adopt Support quality candidates or force a framework/image upgrade.
The qualified dealership's `0.4` similarity threshold is a normal draft setting,
but is not supported in template `promptConfig`; V221 does not inject it through
another field. Review effective client settings and quality before launch.
Defaults remain editable; published history is not rewritten or made into
lifetime upgrade restrictions.

**Optional no-business-write pilot:** discovery, approved policies, shared Desk
and the dealer's existing enquiry link. Exclude callback/test-drive submission
from both executable actions and the required receiver profile/slot. V221 supplies
that composition; V196's older revisions still require engagement. Do not carry
the older offer's `business-actions` slot/profile into the new read-only setup.
Leaving a required receiver unbound or hiding its UI is not this scope. The
[compiler][compiler] retains genuinely unresolved stock/document dependencies.
Ordinary customer setup and hosted acceptance remain gates. AI-state,
indexing and Desk persistence still occur; "no-write" means no dealer business
request creation, not zero data processing.

## 3. Supported Setup Surfaces And APIs

Routes below describe existing code, not permission to execute now. Use the
ordinary owner session and reviewed offer; staff-only steps stay staff-assisted.
Never put credentials in a global marketplace install, document or receipt.

1. **Create/configure:** customer `/start` and `/deployments` use
   `CustomerDeploymentConsole` ([route map][routes], [setup controls][setup]).
   Under `/api/customer-self-service`, `GET /catalogue`, `POST
   /catalogue/{offerId}/deployments`, then `POST
   /catalogue/{offerId}/deployments/{deploymentId}/versions` create configuration
   and publish it; `POST .../staging-releases` requests staging apply
   ([controller][selfservice]). Select explicit external SQL before publishing.
   Missing offer/storage choices are blockers, not authority to bypass the UI.
2. **External stock/receiver:** setup's **Approved connections** form selects
   published version, target, slot and reviewed profile ([form][externalui]).
   Staff first uses `POST /api/customer-self-service/staff/offers/{offerId}/deployments/{deploymentId}/external-integrations/approvals`.
   Customer commands under `/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/external-integrations`
   are `GET` with exact version/target/slot/profile scope, `GET /approvals`,
   `POST /bind`, and `POST /revoke` ([contract][external]). Binding takes approval,
   entitlement revision, operation key and exact write-only credential fields.
   `CONFIGURED_LOCAL_CREDENTIALS` is not provider health. The [resolver][externalresolver]
   can reuse a same-target binding on later versions only when owner, contract,
   target fingerprint and original approval still match. Changed connections/
   credentials require a new scoped review/binding; production never inherits
   staging secrets. Reconcile uncertain commands before retrying; do not invent new keys
   to conceal unknown outcomes.
3. **External PostgreSQL:** setup's **Customer AI-state PostgreSQL** form appears
   for the saved external mode and reviewed slot ([form][sqlui]). Customer
   `GET/POST /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/ai-state-storage-binding`
   uses exact target/slot/version; POST adds operation key, entitlement revision
   and nested typed `connection` ([controller][sqlcustomer]). Supply host, port,
   database, runtime schema, connector schema, username, password and explicit
   migration authorization. Omit caller `approvalReference`; the server derives
   it. The separate platform-admin `GET/POST
   /api/deployments/{deploymentId}/targets/{targetId}/ai-state-storage-binding`
   is not a substitute customer workflow ([admin controller][sqladmin]).
4. **External vectors:** the original inspected source exposed only platform-admin
   `GET/POST /api/deployments/{deploymentId}/targets/{targetId}/existing-vector-binding`
   ([controller][vectorcontroller], [service][vector]). The 2 November customer
   facade/form addition `b244195d4` reuses that same binder through customer
   `GET/POST /api/customer-self-service/deployments/{deploymentId}/targets/{targetId}/existing-vector-binding`.
   The shared form appears only in Providers and owned Connections, not every
   configuration editor. Select a published version and target, supply the
   cluster/project/region and declared serving-auth type, then check and bind.
   Platform derives approval identity; customers do not submit management keys
   or caller approval references. Provider identity/readback, credential probes,
   retained lifecycle and cross-target cluster isolation remain the existing
   binder's responsibility. Real Zilliz management access and serving credentials
   must still be available to qualify the provider. A stored readback is not a
   fresh provider-health probe. The current demo's customer UI reachability and
   stored production-binding readback passed on 2 November, including the final
   Staging/Production labels. A fresh real-client bind/probe and the full customer
   walkthrough remain open; do not call staff API success a customer walkthrough.
5. **Documents:** `/document-knowledge?deploymentId=...` selects a target release;
   Manage reuses `DocumentKnowledgePage` ([surface][connections]). Bind external
   read-only S3 through `POST
   /api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/document-storage-binding`;
   `/revoke` removes the local binding, not source objects ([service][s3customer]).
   The shared form also uses `GET/POST
   /api/deployments/{deploymentId}/document-storage-bindings` and `DELETE
   .../{bindingRef}` with customer operation/slot/profile context ([controller][s3controller]).
   Upload approved files externally with a separately scoped uploader. Then use
   `/api/deployments/{deploymentId}/targets/{targetProfileId}/releases/{releaseId}/document-knowledge`:
   `POST /discover`, `POST /sources`, and per-source `POST /refresh`, `/index`,
   `/reconcile`; `GET /sources` shows outcomes ([controller][documents]). These
   are not file-upload routes. The fictional three-file policy upload dependency
   in the status report is separate from obtaining real dealer policies.
6. **Connections/Desk:** `/integrations` shows target-release sync/freshness;
   `/human-support` offers **Enable LoomAI Desk** and the portal link
   ([surface][connections]). `GET /api/deployments/{deploymentId}/desk` takes
   target/release; `POST .../desk/enable` takes target, source release, operation
   key and optional action-review choice ([controller][desk]). Enable performs
   normal configuration/apply/verify work; re-read its resulting release. Agents
   and replies belong in shared Desk, not a per-deployment Inbox.
7. **App/promotion:** `/ai-workspaces` creates standalone/embedded installations,
   branding and delivery settings. Customer setup's Production stage uses
   `POST /api/deployments/{deploymentId}/practical-promotion/plan`,
   `/production-apply`, then `/activate-production-consumer`
   ([controller][promotion]). Existing apps can also **Use verified release**
   through `PUT /api/platform/customers/{customerId}/consumers/{consumerId}/binding`,
   carrying exact deployment/target/release, binding revision and operation key
   ([UI][assignment], [controller][consumer]). Keep the same app/URL; verify the
   actual assignment, not just the latest draft or an apply request.

## 4. Target Parity And Storage Gates

Use the production Console's **same logical customer deployment and Engage app**
on authorized customer-staging and production targets. Freeze the same runtime/
connector builds, framework/plugin/pack versions, model/embedding dimensions,
prompts, mappings, freshness and enabled capabilities. Use the same PostgreSQL
technology/schema pattern, vector technology, document connector and shared Desk
contracts on both servers. Any material change requires focused requalification.

Keep credentials, SQL schema pairs/roles, vector resources/collections, document
prefixes, origins, Desk bindings and allowances separate. Production migrates its
own AI schemas and indexes its own authorized sources: never copy staging chats,
vectors or simulated receipts. This pilot's staged acceptance does not introduce
a platform-wide rule forbidding normal later direct production upgrades.

- **SQL:** [binding][sqlbinding] requires published external mode, exact target
  `aiStateStorageAllowedHosts`, immutable per-version credential approval and
  exclusive schema claims. [Connection][sqlconnection] fixes `sslmode=verify-full`
  with the JVM trust chain. The [read-only probe][sqlprobe] requires one narrowly
  privileged login for two distinct non-public schemas, USAGE/CREATE there, no
  database CREATE/elevated roles/business-table authority or accessible
  security-definer escalation. Provider provisioning must prepare that role and
  schemas. Default administrator accounts and transaction poolers are not assumed
  compatible; prove actual Flyway/session behavior, persistence and isolated restore.
- **Vectors:** the [catalogue][vectorcatalog] supports several external technologies;
  this typed binder specifically requires Zilliz management readback, RUNNING
  identity, `default` database, serving credentials/auth mode and own-prefix
  collection checks. It rejects an already-bound cluster, including another
  target. This route therefore needs separate staging/production cluster IDs,
  not only different collection prefixes. "SHARED" provider posture does not
  override that constraint. Capacity/cost and provider ACL isolation remain real
  inputs; local binding evidence is not a hosted isolation test.
- **Documents:** [binding code][s3binding] records read-only/no-source-delete intent
  and credentials, not an S3 access probe or proof that the key cannot write.
  Customer binding supports S3-compatible storage; mounted folders are
  non-production demo/canary only. Prove prefix-restricted provider ACLs and
  actual list/read/index/replacement. A runtime [connector status][s3runtime]
  lists one object; it alone does not prove every document is readable/indexed.

## 5. Launch And Disable Checklist

All boxes are acceptance work, not assertions of completion by this sidecar.

- [ ] Name owners above; approve scope, data processing/retention, service costs,
  bounded usage, support hours and no-write or submission mode in writing.
- [ ] Read exact manifests/versions and target substitutions; remove Northfield,
  simulator, seed/receiver/canary dependencies and misleading Basic Inbox naming
  from the client configuration. Required missing bindings remain visible.
- [ ] Qualify real-provider staging with the checklist, fresh stock/docs, SQL
  restart/restore and vector isolation; complete the ordinary customer setup UI.
- [ ] Main's protected-action and assigned-Desk UI evidence must cover the selected
  scope, including cancel/confirm/receipt or explicit no-write exclusion, agent
  reply/visitor receipt/AI return, mobile/reload and approved unavailable contact.
- [ ] Apply/verify production with its own bindings and fresh index, then explicitly
  assign/activate the same app. Record date, app/deployment/target/version/release,
  immutable images, binding identities, reviewed answers and failures/deferrals.
- [ ] Rehearse disable: `/ai-workspaces` **Disable app** invokes `POST
  /api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/disable`
  ([controller][apps], [implementation][appdisable]). Disable every delivery
  installation in scope and remove the embed with the website owner. Verify
  fresh entry is unavailable while the existing dealer contact path still works.
  App disable does **not** stop the deployment or certify cessation of indexing,
  in-flight effects or every previously issued session; verify these separately.
- [ ] Operator disables affected capabilities/sync through normal configuration
  and verified apply, reconciles in-flight requests/releases and coordinates
  provider-key revocation. External-integration revoke blocks future resolution
  but does not stop an already-running release or revoke a remote key; an
  unfinished release can block revoke. Do not report a local revoke as shutdown.
- [ ] For a bad release, select an eligible previous verified assignment or use
  `/practical-promotion/rollback-production-consumer`; verify actual readback.
  Assignment rollback is not database/data rollback. For termination, offboard
  Desk agents, stop approved processing, export/delete only agreed client scopes
  under retention rules, retain audit evidence and confirm billing end. Never
  delete shared Desk/Platform stores or an externally owned cluster as cleanup.

## 6. Routine Operations

- Expired credentials/provider outage: LoomAI checks selected release, sync error
  and last success; vendor/service owner restores access. Rotate DATA/ACTION or
  SQL through their versioned contracts, apply and requalify; no global fallback.
  Existing-version SQL credentials and existing-vector bindings are immutable;
  schedule vector replacement with an operator, not an assumed in-place edit.
- Policy change: dealer owner approves dated replacement; uploader changes only
  the agreed prefix; LoomAI refreshes/indexes/reconciles and checks retired evidence.
- Staff departure/absence: dealer revokes exact Desk assignments/access and names
  cover or updates waiting/contact expectations; no shared agent passwords.
- Freshness/cost/incident: LoomAI reviews existing Connections, Knowledge, release
  status and alerts; customer `/support` is the Help & requests path. Name who
  monitors and responds where current UI/alerts do not cover a signal. Backups,
  restore drills, deletion requests and escalation remain owned operational work,
  not an implied complete analytics/operations service.

## 7. Bounded Local Qualification

On 2 November, **20 focused tests passed, zero failures/errors/skips**: one
[migration/history test][migrationtest], two `clientReadOnly*` cases in the
[existing PostgreSQL bootstrap suite][bootstraptest], and 17 existing
`DeploymentMarketplaceDraftCompilerTemplateSyncTest` cases. Reproduce locally:

```sh
mvn -o -f Platfrom/backend/pom.xml \
  '-Dtest=ClientDealershipTemplateMigrationPostgresTest,ExternalDealershipBootstrapPostgresTest#clientReadOnly*,DeploymentMarketplaceDraftCompilerTemplateSyncTest' \
  test -q -Dlogging.level.org.flywaydb=ERROR
```

These use disposable PostgreSQL 16 containers and mocked provider/Desk clients.
They prove additive history preservation, valid manifest/compiler output,
unbound setup blocking publication, explicit typed external SQL selection,
three READ-only stock actions, symbolic external document wiring, optional
server-owned Desk handover composition and preservation of customer draft edits.
No provider resources, bindings, releases or vectorization runners are created
by these bootstrap cases. Symbolic slots are not successful provider connections.

**Verdict:** locally qualified source revision, not hosted L2 completion or
client launch readiness. A matching reviewed offer (SQL choice, external-stock
profile, document/AI-state slots, target/artifact identities and measured limits)
still needs real inputs and normal publication/entitlement. No real offer,
target policy, source adapter acceptance, provider binding, UI walkthrough,
capacity measurement, indexing or semantic qualification was supplied here.
The full backend suite was not run. Runtime/framework, UI, shared plan/context
and live resources were not changed by this sidecar.

[plan]: ../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_55_PAUL_RIGBY_FIRST_DEALERSHIP_CLIENT_READINESS_AND_PILOT_PLAN.md
[v196]: ../../Platfrom/backend/src/main/resources/db/migration/V196__external_dealership_ai_composition.sql#L127
[v215]: ../../Platfrom/backend/src/main/resources/db/migration/V215__loomai_desk_integration.sql#L46
[v216]: ../../Platfrom/backend/src/main/resources/db/migration/V216__retire_basic_inbox_bootstrap_metadata.sql#L22
[v221]: ../../Platfrom/backend/src/main/resources/db/migration/V221__client_dealership_read_only_external_template.sql
[templateparser]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceManifestService.java#L85
[customerbootstrap]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/service/CustomerDeploymentBootstrapService.java#L88
[storageselection]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/service/CustomerDeploymentStorageSelectionService.java#L27
[dealerbaseline]: 2026-11-01-qualified-baseline-adoption.md#scope
[migrationtest]: ../../Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/marketplace/service/ClientDealershipTemplateMigrationPostgresTest.java#L29
[bootstraptest]: ../../Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/externalintegration/ExternalDealershipBootstrapPostgresTest.java#L135
[externalresolver]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/externalintegration/ExternalIntegrationResolver.java#L187
[marketplace]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/web/MarketplaceController.java#L97
[compiler]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/DeploymentMarketplaceDraftCompilerService.java#L465
[routes]: ../../Platfrom/ui/src/App.tsx#L147
[setup]: ../../Platfrom/ui/src/components/customer/CustomerDeploymentConsole.tsx#L1076
[selfservice]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/web/CustomerSelfServiceController.java#L47
[externalui]: ../../Platfrom/ui/src/components/customer/CustomerExternalIntegrationBindings.tsx#L18
[external]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/externalintegration/README.md#apis
[sqlui]: ../../Platfrom/ui/src/components/customer/CustomerAiStateStorageBinding.tsx#L13
[sqlcustomer]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/web/CustomerAiStateStorageBindingController.java#L15
[sqladmin]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentAiStateStorageBindingController.java#L10
[sqlbinding]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentAiStateStorageBindingService.java#L69
[sqlconnection]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentAiStateStorageConnection.java#L7
[sqlprobe]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentAiStateStorageProbe.java#L14
[vectorcontroller]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentExistingVectorBindingController.java#L24
[vector]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentExistingVectorBindingService.java#L99
[vectorcatalog]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/ManagedDeploymentProfileCatalog.java#L294
[connections]: ../../Platfrom/ui/src/pages/CustomerKnowledgeConnectionsPage.tsx#L23
[s3customer]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/customerselfservice/service/CustomerDocumentStorageBindingService.java#L75
[s3controller]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentDocumentStorageBindingController.java#L23
[s3binding]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentStorageBindingService.java#L90
[s3runtime]: ../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/documents/S3DocumentSourceConnector.java#L62
[documents]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentDocumentKnowledgeController.java#L18
[desk]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/desk/DeskActivationController.java#L7
[promotion]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentPracticalPromotionController.java#L20
[assignment]: ../../Platfrom/ui/src/components/customer/CustomerVerifiedReleaseAssignments.tsx#L25
[consumer]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/tenant/web/PlatformCustomerTenantController.java#L98
[apps]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/web/PlatformAIWorkspaceController.java#L94
[appdisable]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceInstallationService.java#L264
