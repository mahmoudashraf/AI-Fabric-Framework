# Separate AI-Only PostgreSQL Inbox Package

Offline tooling only. No native record, approval, entitlement, grant, deployment, provider resource, source/index or PostgreSQL readiness is created by preparing a plan. Existing H2 template/version/spec/offer, builders and publisher assertions remain unchanged. No backend or framework changes are part of this tooling.

## Scope And Exact Pins

`managed-postgres-inbox-spec.mjs` reuses the existing Basic Inbox protocol template and approved-artifact validation. It changes only AI operational persistence: one Inbox application, its own private deployment-owned PostgreSQL database, generated distinct `inbox-db` secret purpose and existing typed DATABASE_JDBC/USERNAME/SECRET fields. Application H2 volumes are removed. Callback/operator/tenant/deployment bindings and all six connector projections are retained. `AI_ENABLED=false` remains unchanged. No simulator, stock database, CRM, lead store or business-system lifecycle enters this graph.

- Inbox image/source: `ghcr.io/mahmoudashraf/loomai-basic-inbox-service@sha256:d74871880950f09674e0558cdfb09bdadcce050e89ae7b33312dad7069ac3759` / `2035dc36082fefccbfd3a757d38e493fa8f001fc`. The real registered ID is required from exact normal promoted metadata. The shorthand `dsa1dc` is not expanded or accepted. No new registration/promotion is performed. Artifact role must be MANAGED_DEPENDENCY, ownership contract AI_OPERATIONAL_SERVICE/DEPLOYMENT_OWNED/AI_OPERATIONAL_STATE, production channel, exact manifest/hash/image/source and valid promotion metadata.
- PostgreSQL candidate pin: `postgres@sha256:721873c34ceb9f8d8fc265984940dc982404c105f19ad51be9fdc5970a6080ea`. The helper checks this exact pin against both existing `run-local-budget-restore.mjs` and `java/com/ai/fabric/runtime/budget/RuntimeBudgetExportRestoreTest.java`. This is lineage from a local rehearsal, not target architecture/registry compatibility, PostgreSQL hosting approval or actual Inbox migration/recovery proof. Confucius's independent Inbox proof runner is neither imported nor modified.
- Runtime image/source remain the existing production-promoted `ghcr.io/mahmoudashraf/ai-fabric-runtime@sha256:3809a6585ad19f25c0139d60cf1b9aa742b7831a6213fcec4bdfe6fb52e0b140` / `d2b1a9792dc10ec801d3ce30c170763f1f2ab301`. Its actual native ID/build/capability manifest/hash and production metadata are supplied, not invented. Existing behavior/extension/catalog compatibility and provider-budget/native-Milvus-token requirements are checked.

The offer builder is dealership-config-specific tooling, not a vendor-specific backend protocol. It reuses existing normalization, profile/artifact fingerprints, workspace profile and external-integration helpers. All business connections remain unbound external authorities. No credential values are accepted in review/config/metadata; browser configuration permits only a public `sb_publishable_` key, never an administrator or service-role key. Plans containing public browser configuration and normal API metadata are still private operational files: do not commit them to this public repository.

## Complete Capacity

- Owned Inbox: **500m CPU / 1,024 MiB RAM / 4,096 MiB disk / 1 database / 2 dependency resources** (application plus PostgreSQL).
- MANAGED_POSTGRES runtime AI state plus Inbox: **2,250m / 4,096 MiB / 6,144 MiB disk / 2 AI-state databases / 2 dependency resources / 2 derived indexes**.
- CUSTOMER_POSTGRES runtime AI state plus Inbox: **2,000m / 3,584 MiB / 4,096 MiB disk / 1 platform-owned Inbox database / 2 dependency resources / 2 derived indexes**. Customer runtime AI-state PG is separately bound, not hosted, and is not counted as another platform-owned DB.

One offer selects exactly one runtime AI-state mode. Topology, selected-mode topology, limits and settings agree; there is no silent BYO or H2 fallback. Capacity is a proposed exact demand, not host fit or an allocation. Native fresh placement, measured policy, admission and financial authorization remain mandatory. Review supplies actual policy/lifetime references; placeholders reject, and no existing approval or TTL is silently extended. BYO accounting is tested; BYO hosted generation acceptance is not claimed.

## Inputs From Normal APIs

Start with `managed-postgres-inbox-publication.example.json`. It is deliberately **not executable**: missing IDs, artifacts and approvals remain PENDING. Populate a private copy only from reviewed normal API readbacks. Do not insert a guessed native version ID or construct fake publication summaries. Synthetic records in tests are not publication inputs.

Required metadata fields and routes:

- `contracts.publisherDetail`: `GET /api/marketplace/publishers/{selection.publisherId}`. Existing publisher must be ACTIVE/VERIFIED and own the existing template family by publisher slug. This tool does not create or verify a publisher.
- `contracts.templateDetail`: `GET /api/marketplace/plugins/mkp-template-dealership-external-systems-managed-inbox-v1`.
- `contracts.sourceTemplate`: `GET /api/marketplace/plugins/mkp-template-dealership-external-systems-managed-inbox-v1/versions/1.0.0`. The exact original published version is the immutable source, never a mutation target.
- `contracts.inboxArtifacts`: `GET /api/deployment-provider/source-artifacts?serviceName=dealership-ai-basic-inbox`. Exactly one exact approved image/source must match. Its full returned native ID supplies the spec; PENDING IDs and copied ownership hashes reject.
- After publication, `contracts.publishedTemplate`: `GET /api/marketplace/plugins/mkp-template-dealership-external-systems-managed-inbox-v1/versions/{selection.templateVersion}`. Its native ID must match the real publisher submission. Marketplace generates `mkv-<native-id>` on submission; the tool never predicts it.
- `contracts.inboxPublication`: `GET /api/customer-self-service/staff/templates/{actualNewTemplateVersionId}/managed-dependency-spec`. Use explicit `null` only for a real 404 when planning a first spec publication. The real native fingerprint is read back; no alternate local serialization is claimed as the backend fingerprint.
- For offer/placement phases, `contracts.pluginVersions`: the six exact `CUSTOMER_PLUGIN_REFS` version GET responses. `contracts.targets`: `GET /api/deployment-provider/target-profiles`. `contracts.runtimeArtifacts`: `GET /api/deployment-provider/source-artifacts?serviceName=ai-fabric-runtime`.
- `contracts.behaviorCatalog` and `contracts.executionExtensionCatalog`: `GET /api/deployment-behaviors` and `GET /api/deployment-execution-extensions`.

`selection.templateVersion=1.1.0` is a proposed new semantic label under the same publisher-owned family, not a created ID. Main must choose unused new `selection.specId` and `selection.offerId`; neither may be an old held identifier. Template append/publish uses existing Marketplace primitives and does not replace the old 1.0.0 manifest. A first plan can be prepared before there is any new submission. Refresh private input readbacks after every confirmed phase.

## Explicit Phases

Each plan is bound to one exact POST path/body, a request-body SHA-256, all readback values, tool/helper/source file hashes, the reviewed origin and a complete plan SHA-256. Planning makes **zero requests**, does not read an auth environment variable and needs no credentials.

1. `template-submit`: `POST /api/marketplace/publishers/{publisherId}/submissions` with only `pluginSlug`, `releaseChannel`, `manifest`. Clone the original manifest, changing semantic version/display name/description only. Record the native returned version ID.
2. `template-validate`: `POST /api/marketplace/submissions/{actualNewVersionId}/validate`, body `{ "reviewNotes": "<actual review reference>" }`. Requires exact SUBMITTED metadata.
3. `template-publish`: same native ID's `/publish`, same body shape. Requires VALIDATED metadata. No implicit validation/promotion.
4. `spec`: `POST /api/customer-self-service/staff/templates/{actualNewVersionId}/managed-dependency-spec`, body `{specId,spec,reviewReference}`. Requires new PUBLISHED template metadata. Read back complete spec and real fingerprint after POST. Existing exact template/spec replay is verified with no write; conflicting immutable content is rejected.
5. `placement-staging`, then `placement-production`: each separately reviewed `POST /api/customer-self-service/staff/targets/{targetId}/placement-evidence`, body `{intent}`. These are real probes/evidence writes, not part of offline planning. Receipts must match the profile, intent and valid timestamps.
6. `offer`: `POST /api/customer-self-service/staff/offers`, body `{offerId,definition,reviewReference}`. Native publication revalidates exact pins and fresh placement in its own transaction. No probes or other POSTs are hidden in this phase. Success is **PUBLISHED_CUSTOMER_READBACK_PENDING**, not runtime readiness.

Example offline command, after replacing PENDING inputs with actual reviewed metadata:

```sh
node verification-support/productization-01036/publish-ai-only-dealership-postgres-package.mjs plan \
  --input /private/tmp/pg-inbox-input.json --phase template-submit \
  --output /private/tmp/pg-inbox-template-submit.plan.json
```

For each subsequent phase, refresh the private metadata and make/review a new plan. Outputs use exclusive creation (`wx`) and mode 0600. Existing files are never overwritten. Changing tool sources, metadata, source pins, target profile, template/spec, mode, policy, review, origin or body requires a new plan and review.

## Later Main-Only Execution

Execution is not authorized or performed by preparing this tooling. Main waits for qualified backend plus audited exact known-NO_EFFECT closure, no active apply, and its normal publication review. Then an already reviewed phase may be executed explicitly:

```sh
node verification-support/productization-01036/publish-ai-only-dealership-postgres-package.mjs execute \
  --plan /private/tmp/pg-inbox-template-submit.plan.json \
  --reviewed-sha256 '<exact reviewed planSha256>' \
  --execute-reviewed-phase template-submit \
  --output /private/tmp/pg-inbox-template-submit.receipt.json
```

Only this explicit mode uses `PLATFORM_ADMIN_API_KEY` via the existing normal API header. The key is not a CLI argument, JSON field, plan, receipt or stdout value. The transport uses a single HTTPS origin, redirect refusal, bounded JSON and timeout. It checks normal `/api/platform/auth/session` ADMIN authority, revalidates every exact readback/source/body pin, and then performs **at most one POST**. Durable ATTEMPT_STARTED checkpoint is fsynced first. Source drift/admin/checkpoint failures cause no publication write.

Transport uncertainty, invalid replies or readback mismatch after a POST becomes **PUBLICATION_OUTCOME_UNKNOWN**. Preserve the receipt; do not retry POST, rotate IDs, relabel a failure as success or advance phases. Reconcile actual native publisher/spec metadata first. Pass `--prior-receipt FILE` when evaluating a prior attempt: any prior `writeAttempted=true` is refused rather than retried. Reusing an output path also refuses. There is no automatic retry loop or cross-API transaction claim. Offer publication has no staff GET that proves customer visibility; immutable customer readback requires its normal later accepted owner entitlement and is outside this tool. Never claim an uncertain offer POST was absent because the customer has not yet accepted an invitation.

## Customer Journey And Remaining Proof

After authoritative no-effect settlement preserves the old release/R7/auth/funding/authority/external-binding history, the **same customer and eligible owner** receive/accept a new entitlement to the new reviewed offer, bootstrap a new deployment/version and obtain its own admission. Do not change the old offer association, held objects or paid allowance to force the new topology. Existing eligible target-scoped grant reuse or normal unused-void correction is a separate funding-owner decision; this tooling performs neither. Consumer/DRAFT-app reassignment and external namespace retention/transfer require owning-service receipts and exact row revisions.

The new candidate starts with fresh independently scoped Inbox/runtime AI state and derived indexes. No vectors, state, business data or old callbacks are copied/replayed. Production uses its own freshly reviewed promotion candidate and independent sources/indexes, not the old frozen offer/R7 plus a PG environment override.

Actual Inbox PostgreSQL driver/Flyway/startup, operational-state persistence/restart/backup/restore, budget high-water reconciliation, target capacity/architecture, secrets, callbacks/operators, hosted staging and production behavior remain gates. A package plan, source hash or successful metadata publication is **not PostgreSQL readiness, hosted restore, full P5/P7 PASS or external-ready acceptance**.

## Offline Tests

```sh
node --test \
  verification-support/productization-01036/test/managed-postgres-inbox-spec.test.mjs \
  verification-support/productization-01036/test/managed-postgres-inbox-offer.test.mjs \
  verification-support/productization-01036/test/managed-postgres-inbox-publication.test.mjs \
  verification-support/productization-01036/test/simulated-dealership-managed-spec.test.mjs \
  verification-support/productization-01036/test/simulated-dealership-offer.test.mjs
```

Tests use synthetic local records and injected transports only. They check exact image/role/source/ownership, H2 immutability, capacity for both modes, API body shapes against current request sources, replay/drift/unsafe input, checkpoint-before-write, unknown outcomes, no blind retry, default no-effects behavior and real CLI private/exclusive planning. They do not publish to a backend, start PostgreSQL or invoke a model/provider. Full test facts, failures and frozen hashes are recorded separately in the private handoff.
