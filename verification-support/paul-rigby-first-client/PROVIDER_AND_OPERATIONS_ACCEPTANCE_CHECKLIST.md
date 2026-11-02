# Provider And Operations Acceptance Checklist

Date: 2 November 2026. Scope: **010.55 L5/L8 preparation**, not provider implementation or real-client acceptance.
Complements [provider contract inputs](PROVIDER_CONTRACT_CHECKLIST.md) and the [client integration runbook](CLIENT_INTEGRATION_RUNBOOK.md); setup/promotion instructions stay there.
Preparation and the bounded offline checks below are complete. Main owns hosted UI, fresh customer setup and launch evidence. No framework/code changes, AI-provider calls, infrastructure creation or production mutations were made here.
For every unchecked item record **PASS / FAIL / BLOCKED / DEFERRED, date, responsible person and sanitized evidence**. All real-vendor and operating acceptance items remain unaccepted until exercised with approved access.

## 1. Input Record: Owner And Vendor

- [ ] Sponsor / branch / technical contact / acceptance reviewer: `____`; scope: read-only discovery / separately agreed enquiries `____`; pilot dates: `____`.
- [ ] Vendor/product/specification revision and approval reference: `____`; authorized API/feed and media hosts: `____`; advertiser/account/branch scope per target: `____`.
- [ ] Private credential references for staging / production: `____`; authentication fields, expiry format, rotation/revocation owner: `____`. Authorized customer admins may enter credentials in the authenticated Console's designated typed secret fields. Never place service keys/tokens in browser chat, website scripts, URLs or committed documents, including this checklist.
- [ ] Baseline/targeted-stock paths, pagination/completeness rules, limits/error samples and permission to retain/index/send approved fields to the selected LLM: `____`.
- [ ] Notification registration owner/process, callback URL, signing specification/test vectors, retry/order/deduplication rules and signing-secret rotation: `____`.
- [ ] Approved policies/revisions, source storage/prefixes, read-only runtime identities and separate uploader owner: `____`; images/derived-storage/retention/deletion rights: `____`.
- [ ] PostgreSQL/vector/document/LLM owners, regions, spend/usage alerts, backup/restore and data-exit responsibilities: `____`; separate target scopes: `____`.
- [ ] Desk owner, registered agent identities/roles per target, staffed hours/cover, absence wording and existing dealer contact path: `____`.
- [ ] Incident/freshness monitoring person, check frequency, alert destination, escalation/contact and agreed response expectations: `____`. Existing signals do not imply a staffed 24/7 service.

## 2. Actual Stock DATA Contract To Compare

Source defaults are inherited from [V174](../../Platfrom/backend/src/main/resources/db/migration/V174__simulated_dealership_customer_package.sql) by [V196](../../Platfrom/backend/src/main/resources/db/migration/V196__external_dealership_ai_composition.sql). Read the actual published manifest/effective release before applying these defaults to any target.

- **Binding:** `mkp-data-dealership-external-stock-v1`; `providerBaseUrl`, `providerHost`, `advertiserId`; secret references `providerKey`, `providerSecret`, `notificationSecret`. Stock stays in the external provider; the connector keeps an AI-serving projection/sync ledger and sends derived evidence to the runtime index, not a new dealer inventory/CRM system.
- **Token:** `POST /authenticate`, form `key`/`secret` -> opaque `/access_token`, absolute `/expires_at`; stock requests use `Authorization: Bearer ...`. [Token service](../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/ProviderTokenService.java) accepts ISO/offset or epoch absolute expiry, caches with 30-second configured skew and a 5-second exchange timeout.
- **Expiry/401:** refresh before a cached token enters the skew window; [HTTP client](../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/ProviderHttpClient.java) allows one generation-aware refresh on 401. Repeated 401/403 or bad expiry remains a visible error, not indefinite retry or an alternate account. Rotation requires the existing scoped binding/configuration and verified apply; local revoke does not revoke the remote credential.
- **Baseline:** `GET /stock?advertiserId=<trusted>&lifecycleState=FORECOURT&page=1&pageSize=200`, max 100 pages; only HTTP 200 is complete. `/results`, `/metadata/stockId`, matching `/advertiser/advertiserId`; only PUBLISHED advertiser adverts are included. Limits: 20,000 records, 10 MiB per response; schedule 900 seconds, not a promised five-minute freshness SLA.
- **Record/data:** make/model/derivative/year, GBP price, mileage, fuel/body/transmission/features; lifecycle/ad status, provider revision/time and first media reference are projected. Missing showroom/range/fees/policy facts must remain unknown unless separately supplied; do not infer branch from group ownership.
- **Targeted:** `GET /stock` with trusted advertiser plus signed-event `stockId`, `page=1&pageSize=1`; accepts complete 200 and declared 404 absence. Current state is re-fetched: event payload is not authoritative stock evidence.
- **Media:** provider `media.images[]` is ordered `{imageId,href}`; current DATA mapping uses only element zero. Simulator serves synthetic WebP at `/media/{size}/{imageId}.webp` (w300h225, w720h540, w1024h768). Real media host/rights/expiry/CSP and unavailable-image behavior need qualification; no media upload/attach API is implemented by this simulator.
- **Scope:** connector injects the protected advertiser and checks each returned record; runtime evidence carries deployment/tenant/source provenance. Host/account approval is not proof the vendor credential has matching remote ACLs. Buyer/model parameters never supply credentials or broaden the advertiser.
- **Retries:** configured max concurrency 2, 50 ms spacing, max 2 attempts for retry-eligible requests at 429/502/503/504, 250 ms backoff; 429 pause 30 seconds / 503 pause 5 seconds. Vendor quota/Retry-After compatibility must be agreed, not inferred from these defaults.

### Notifications And Deletions

- **Route:** `PUT /integrations/webhooks/{sourceId}`; external-template webhook ID `external-dealership-stock-events`, baseline ID `dealership-external-stock-source`. Demo/older releases can have different IDs: use effective configuration, not hard-coded Northfield names.
- **Verification:** JSON <=1 MiB; `AutoTrader-Signature: t=<epoch-seconds>,v1=<HMAC-SHA256(timestamp + '.' + raw-body)>`; 300-second signature window. Identity is `/id` + `/time`, `/type=STOCK_UPDATE`, account `/data/advertiser/advertiserId`, record `/data/metadata/stockId`.
- **Processing:** [webhook service](../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/ProviderWebhookService.java) authenticates raw bytes before parsing, persists/deduplicates events, re-fetches the current record, retries boundedly (3 attempts / 10 seconds), then exposes dead letters. Accepted/duplicate HTTP 200 does not mean indexing completed; foreign account ->422, same identity with changed body ->409.
- **Replay:** operator uses existing authenticated integration operations; replay only accepted stored evidence when the manifest permits it. Rejected events cannot be made valid through replay; sender must provide a correctly authenticated event. No notification-subscription API/provisioning is implied by `registrationExpected=true`.
- **Deletion:** [sync service](../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/HttpDataSyncService.java) handles SOLD/WASTEBIN/DELETED tombstones, targeted absence/non-inclusion, and absence after a completed baseline. Partial/non-complete pagination must fail before absence-delete planning. Runtime work failure remains visible; this is not a cross-service atomic transaction guarantee.
- [ ] With approved vendor access, demonstrate create/change/unpublish/sold/remove -> current projection and vector evidence converge; keep unaffected account/target records. Prove foreign-account denial, malformed/partial baseline, duplicate/conflicting/old notifications, invalid signatures, expiry/rotation and missed-event recovery by full baseline. Record measured delays and terminal indexing work, not just callback 200.

## 3. Simulator Versus Real Provider: Explicit Limits

The [simulator](../external-vehicle-provider-simulator/README.md) has one configured synthetic advertiser per profile, local H2 state, self-issued HMAC bearer tokens and control-key-protected mutation/fault/event endpoints. It is **not** the AutoTrader sandbox or an approved real API connection.
Its `/internal/control/**` operations simulate stock changes and notification delivery; they are not vendor Stock Updates, deal creation, enquiry, reservation, message or media-write APIs. No browser or LLM receives provider credentials.
The synthetic profile checks the repository's selected wire shapes, not today's full vendor specification, production permission model, subscription lifecycle, quota, media licensing or availability guarantees. A passing method named "published contract" is not vendor certification.
The current first-client read-only template excludes business-write receivers. A separate agreed write integration needs real destination/auth/consent/idempotency/error/receipt contracts and its own acceptance; the demo receiver does not qualify it.
These tests use loopback HTTP, in-memory state/H2 and a simulated runtime indexing response. They do not prove real vectors, S3 IAM/TLS, restart/restore, hosted isolation, browser quality or continuous real-provider freshness.

## 4. Operating Acceptance Using Existing Services

- [ ] **Monitor:** customer Connections/Knowledge/release status shows last success, errors, work and source freshness. Existing connector admin `/api/admin/integrations` exposes sanitized token posture, sources, counts and webhook failures; access stays server/operator-only. Freshness is based on successful sync, not a live availability promise. Name who checks errors/costs until actual alert delivery is verified.
- [ ] **Recover expiry/outage:** inspect exact target/release, last sync and token/error class; service owner restores/rotates approved access, LoomAI binds/applies normally and reconciles. Verify a fresh success and boundary-preserving answers. Do not label stale indexed results "live" or silently route to another provider/account.
- [ ] **Recover events:** use existing authenticated `POST /api/admin/integrations/sources/{sourceId}/reconcile` or `POST /api/admin/integrations/webhooks/{sourceId}/events/{eventId}/replay` with operation idempotency. Preserve failed evidence; observe terminal work/dead letters. Registration/provider redelivery still belongs to the vendor agreement.
- [ ] **Replace policy:** approved uploader replaces the object only in its target prefix. In Knowledge use source `refresh` -> `index` -> `reconcile`; changed revision creates a candidate, work success activates it before old-manifest deletion. Failure preserves the old active version. An S3 upload alone does not activate new evidence; scheduled reconciliation processes submitted work, not a general bucket-change watcher.
- [ ] **Remove policy:** existing target/release document-knowledge `DELETE /sources/{sourceId}` requests exact derived-index deletion; reconcile to terminal and prove retired evidence absent. [Lifecycle service](../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/documents/DocumentKnowledgeService.java) never deletes the customer's source object. Source deletion, retained evidence/backups and storage-binding revoke are separate actions; agree retention before claiming data erased.
- [ ] **Offboard staff:** Desk OWNER removes the exact assignment with `DELETE /api/desk/v1/bindings/{bindingId}/agents/{agentId}`. [DeskDirectory](../../product-services/loomai-desk-service/src/main/java/com/loomai/desk/service/DeskDirectory.java) audits removal and checks current assignment on each call; SUPPORT and REVIEWER are separate roles. Recheck denial while another authorized agent still works, cover any claimed conversation, and remove every relevant target assignment. This does not delete the Supabase identity, other assignments or prior messages.
- [ ] **Disable customer entry:** disable every scoped Engage installation through the existing Console action and remove the embed with the site owner; verify fresh entry is unavailable and dealer contact still works. [Installation disable](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceInstallationService.java) is not deployment shutdown or proof all issued sessions/in-flight effects ended.
- [ ] **Stop processing:** operator publishes/applies disabled affected sync/webhook/action capabilities or stops the scoped deployment as agreed, reconciles outstanding operations, and arranges remote-key revocation. [External-integration revoke](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/externalintegration/ExternalIntegrationService.java) blocks future resolution only, retains external data/local credential records, and rejects in-progress-release conflicts. It does not stop existing containers or revoke a remote key.
- [ ] **Restore/data exit:** named service owners prove isolated backup/restore and agreed export/deletion scopes, retention and billing end. Never delete shared Desk/Platform data, unrelated records or customer-owned clusters as fixture cleanup. Follow runbook sections 4-6 rather than inventing a second shutdown/promotion mechanism.

## 5. Offline Verification Performed

Java 21.0.9 / Maven 3.9.11, 2 November 2026. Existing tests were exercised unchanged; fixture calls were loopback only. No tests were skipped in the successful runs.
Simulator command (repository root):
```sh
mvn -o -B -ntp -f verification-support/external-vehicle-provider-simulator/pom.xml -Dtest=VehicleProviderSimulatorHttpTest test
```
**PASS: 6 tests, 0 failures/errors/skips.** Covers both generic profiles; synthetic AutoTrader token/stock/account/media, reservation/sold representation; protected controls; signed and duplicate events. Does not wait for a real token TTL or prove provider expiry/revocation.

Connector attempts 1/2 stopped **before tests**, not assertion failures: default cache lacked `ai-fabric-data-sync:0.8.45`; retry with the populated qualification cache but default settings could not use artifacts recorded under a different repository ID. No dependencies/framework versions were changed or downloaded to conceal those failures.
Attempt 3 used that cache's existing matching Central-only settings, still `-o`, and succeeded:
```sh
# Existing local qualification locations, not new cache provisioning:
CACHE=/Users/mahmoudashraf/.codex/private/loomai-paul-rigby-01055/acceptance-20261102/empty-central-repository
SETTINGS=/Users/mahmoudashraf/.codex/private/loomai-paul-rigby-01055/domain-routing/evidence-handoff-0843/central-only-settings.xml
mvn -o -B -ntp -s "$SETTINGS" -Dmaven.repo.local="$CACHE" -f ai-infrastructure-module/ai-infrastructure-generic-rest-connector/pom.xml -Dtest=ProviderIntegrationFoundationTest,ProviderWebhookServiceTest,ProviderWebhookControllerTest,ProviderRateLimiterTest,SourceProjectionQueryServiceTest test
```
**PASS: 23 tests, 0 failures/errors/skips:** foundation 9, webhook service 6, webhook controller 2, rate limiter 2, projection query 4. Includes concurrent 401 refresh, safe/idempotent retry, page/cursor sync, targeted upsert/delete, tombstones, incomplete-snapshot protection, signature/conflict rejection, retries/dead letter and restart recovery. These are fixture contracts, not vendor acceptance.

Policy lifecycle command used the same offline cache/settings:
```sh
mvn -o -B -ntp -s "$SETTINGS" -Dmaven.repo.local="$CACHE" -f ai-infrastructure-module/ai-fabric-runtime/pom.xml '-Dtest=DocumentKnowledgeServiceLifecycleTest#successfulReplacementActivatesNewVersionBeforeDeletingTheOldManifest+failedReplacementPreservesThePreviouslyActiveVersion+deletionIsIdempotentWaitsForExactWorkAndNeverRemovesTheCustomerObject' test
```
**PASS: 3 tests, 0 failures/errors/skips**, H2/temp-folder lifecycle with mocked queue/work results. It does not qualify customer S3 ACLs or a real vector deletion. Desk offboarding/installation disable were source-inspected, not executed or freshly UI-qualified in this task.
Surefire receipts remain under each module's `target/surefire-reports/` (generated, not committed). Total: **32 passed**; the two pre-test cache-resolution failures remain explicitly recorded above.

## 6. Remaining Handoff

No provider adapter/core change is justified solely by these passing fixtures. Main can continue hosted setup/UI work independently; this document does not close its gates or change the canonical plan/context.
Main-reported UI progress (not independently verified by this side task): the existing owned canary reader was entered through the DocumentKnowledge secret form for configuration-only `dep-2800de9b`, with no IAM or source-object changes. This is separate from the 32 offline tests and does not qualify real-client storage/indexing.
Real L5 acceptance awaits the approved vendor package/account/notifications/media and data-use rights; L8 acceptance awaits named owners, policy upload/storage access, actual target/revoke/disable/restore exercises and launch sign-off. Record any real contract mismatch before implementing; proposed framework changes require owner review first.
