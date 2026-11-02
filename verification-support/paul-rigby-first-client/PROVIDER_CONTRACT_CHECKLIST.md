# Provider Contract Checklist

Date: 2 November 2026. Companion: [client integration runbook](CLIENT_INTEGRATION_RUNBOOK.md).

All items below are **unaccepted for the real client until evidenced**. Source
inspection/local tests establish contracts, not provider licensing, hosted
connectivity or buyer quality. No live calls, credentials, writes or AI questions
were used; local synthetic PostgreSQL fixtures are not client resources. Main's
demo/Desk verification is separate. Record
PASS / FAIL / BLOCKED / DEFERRED, date, owner and a sanitized evidence reference
for each applicable item; do not fill a PASS from a manifest or sample alone.

The [L2 local qualification](CLIENT_INTEGRATION_RUNBOOK.md#7-bounded-local-qualification)
passed 20 focused tests with zero failures/errors/skips. This qualifies the
source composition only; it does not accept any real-provider box below.

## 1. Obtain The Real Provider Package

- [ ] Use the V221 `1.2.0` non-Inbox external-systems revision only after normal
  hosted catalogue adoption/readback. Its required plugins are stock, discovery
  and customer documents; no enquiry ACTION/receiver is included. Review a matching
  setup with the existing `AI_STATE_STORAGE_MODE` choice/`CUSTOMER_AI_STATE` slot
  and submit explicit `CUSTOMER_POSTGRES` at customer bootstrap. Template selection
  alone does not set SQL mode: that key is not accepted by the manifest parser.
  See the runbook for exact source and retained tuning limits.
- [ ] Dealer sponsor/vendor contact approves exact account, branch, public stock
  fields, images, indexing/derived storage, source retention, LLM processing,
  regions, revocation and deletion. API access alone is not approval for these uses.
- [ ] Receive approved API/feed specification, representative authorized responses,
  credentials/token rules, scope IDs, pagination/rate limits, notification signing
  examples and staging/production access. Ordinary retailer passwords are not API
  credentials. A DMS export can support a dated staff prototype, not a live-feed claim.
- [ ] Receive approved dated policies, media host permission, contact/branding,
  staff emails/hours, service ownership/cost decisions and dealer reviewer.
- [ ] If writes are included, receive the actual receiver specification, field/
  consent/routing rules, identity requirements, safe test destination, idempotency
  and receipt/error contract. Otherwise explicitly select the no-business-write
  scope in the runbook; do not bind a dummy receiver to satisfy setup.

## 2. DATA Contract: External Dealership Stock

[V196][v196] clones the reviewed stock protocol from [V174][data] through the
compatibility-only [V184][compat] revision; changing the URL does not make an
arbitrary API compatible. No AutoTrader partnership or go-live approval is implied.

- [ ] Confirm exact published DATA plugin `mkp-data-dealership-external-stock-v1`
  and manifest hash. Config fields are `providerBaseUrl`, `providerHost`,
  `advertiserId`; write-only secret fields are `providerKey`, `providerSecret`,
  `notificationSecret`. Staff host/account approval and customer binding are
  version/target/slot-specific ([binding contract][binding]).
- [ ] Token exchange matches **POST `/authenticate`**, form fields `key` and
  `secret`, response `/access_token` and absolute `/expires_at`, Bearer header,
  30-second expiry skew and 5-second exchange timeout. Compare real expiry format
  and errors, not just field names. The [HTTP client][http] permits one token
  refresh after 401; do not assume unauthorized requests are retried indefinitely.
- [ ] Baseline matches **GET `/stock`**, trusted `advertiserId`,
  `lifecycleState=FORECOURT`, `page`/`pageSize`, first page 1, page size 200,
  maximum 100 pages. `/results` contains records with `/metadata/stockId` and
  matching `/advertiser/advertiserId`. Review completion/pagination against the
  real provider before treating an absent item as deletion.
- [ ] Verify mapped vehicle/model/derivative/year/fuel/body/transmission/mileage,
  GBP price, features, availability, publication, image ID/URL and update/version
  fields. Current image projection uses `/media/images/0`, not a complete gallery.
  Branch/location, fees or other facts absent from the mapping need explicit
  approved handling; do not infer them from a group-wide account.
- [ ] Include only PUBLISHED adverts; test SOLD/WASTEBIN/DELETED and records absent
  from a completed baseline. Limits are 20,000 records and 10 MiB per response.
  Targeted fetch uses `/stock` with trusted `stockId`, page 1/pageSize 1, 200
  completion and 404 absence. Prove projection and vectors both update/remove.
- [ ] Review actual load/limits: 900-second baseline schedule, concurrency 2,
  50 ms minimum interval, maximum 2 attempts for 429/502/503/504, backoff 250 ms,
  rate-limited pause 30 seconds and unavailable pause 5 seconds. These are code
  settings, not vendor-approved capacity or measured freshness promises.

### Notifications

Source: [manifest webhook][webhook], [webhook controller][webhookcontroller] and
[processing service][webhookservice]. The current contract expects:

- [ ] Connector route `/integrations/webhooks/{sourceId}` with external source
  `external-dealership-stock-events`; declared method **PUT**, JSON and at most
  1 MiB. The generic controller accepts POST/PUT routing, but the selected
  source's method still governs acceptance.
- [ ] `AutoTrader-Signature`, HMAC SHA-256 over timestamp-dot-raw-body, components
  `t`/`v1`, seconds timestamp and 300-second window. Validate exact real-vendor
  canonicalization, headers, encoding, clock skew and key rotation. A simulator's
  similarly named signature is not proof of vendor compatibility.
- [ ] Event identity `/id` plus `/time`, type `/type=STOCK_UPDATE`, account
  `/data/advertiser/advertiserId`, key `/data/metadata/stockId`. Reconciliation
  re-fetches current authorized stock rather than trusting notification fields
  as the stock record. Accepted/duplicate status 200, account mismatch 422;
  bounded reconciliation has maximum 3 attempts and 10-second retry delay.
- [ ] In an approved test scope prove duplicate, changed-payload replay,
  older/out-of-order events, invalid signature, foreign advertiser and missing/
  removed record handling. Record real propagation time; a webhook receipt does
  not prove successful indexing or five-minute buyer freshness.

## 3. ACTION Contracts

**Discovery:** `dealership_search_inventory`, `dealership_get_vehicle` and
`dealership_compare_vehicles` read the deployment-local projection, require a
successful sync and allow at most 1,800 seconds staleness ([V174 routes][reads],
[V196 substitution][v196]). They do not revalidate live availability at the
provider. Detail/comparison IDs are evidence-bound; buyer-entered IDs are not
authority. [V208][metadata] publishes discovery `1.1.0` metadata without changing
template pins: adoption is explicit. Its body-style enum is fixture-reviewed,
not a universal vendor vocabulary.

- [ ] Compare the chosen immutable action pin and typed filter vocabulary with
  actual stock values, including unknown/missing values, current price and
  image/display eligibility. Do not silently relax buyer constraints or turn
  missing optional facts into blanket rejection.
- [ ] Prove stale/never-synced behavior, detail/compare from trusted results,
  changed/sold stock and cross-scope denial. Historical conversation evidence is
  not a fresh provider check.

**Business requests:** [V196 engagement][engagement] inherits [V174 receiver
routes][receiver] through [V179][protected] and V184. Required config is
`receiverActionUrl`; secret field `receiverApiKey`. Both actions require explicit
confirmation; callback permits anonymous use, test-drive submission does not.
This section applies only if a later agreed composition adds enquiry actions;
it is outside V221's read-only pilot, not a receiver to bind during its setup.

- [ ] Receiver accepts **POST** to the configured URL, header
  `X-DEALERSHIP-INTERNAL-KEY`, `Idempotency-Key`, body `actionId`,
  `idempotencyKey`, `params` and `trace` (request/conversation/auth context).
  `params` includes `confirmationAccepted=true` and trusted `vehicleReference`.
  Static `confirmationAccepted` is emitted after the governed confirmation
  flow; it is not permission to bypass that flow.
- [ ] Callback fields: name, phone, explicit consent; email/message optional.
  Test-drive: name, email, phone; preferredDate/message optional. The inherited
  test-drive manifest has no separate consent field; reconcile the dealer's
  approved consent requirements explicitly rather than inventing one in a URL change.
- [ ] Receiver independently enforces credential/tenant/deployment/account scope,
  protected identity where required, current authoritative vehicle eligibility,
  consent requirements and durable idempotency. The ACTION profile has no mapped
  account field; a staff `accountScope` review is not remote ACL verification.
- [ ] Success mapping expects HTTP 200, `body.data` as receipt and `body.message`
  as message. Qualify business rejection and malformed/ambiguous responses:
  HTTP 200 alone is not a delivery proof. Receipt means request accepted/submitted,
  never a guaranteed appointment, reservation or completed sale.
- [ ] Use a safe receiver to prove cancel causes zero request, confirm causes one,
  repeated/double submission creates no duplicate, rejected/expired identity is
  denied and unknown transport outcome is reconciled without a blind new request.
  [Connector execution][execution] can retry an idempotent write; local connector
  receipts alone cannot prove the destination did not duplicate its effect.

If the selected vendor differs, record the mismatch and keep the integration
blocked or outside the agreed scope. Adapt within the existing DATA/ACTION
plugin/connector ownership, not buyer keyword routing, a new CRM or a bridge
service. This sidecar implements no adapter or contract/schema changes.

## 4. Storage, Operations And Real Acceptance

- [ ] PostgreSQL owner supplies approved host/port/database, two dedicated AI
  schemas and restricted role per target. Prove actual TLS chain, probe, Flyway,
  ordinary restart persistence, credential-generation replacement and isolated
  backup/restore. [Probe][sqlprobe] is read-only; bind success does not prove
  migrations, restoration or a particular managed provider/pooler.
- [ ] Vector owner supplies actual reviewed Zilliz identity/region/plan, separate
  serving credentials and management readback access. The [typed binding][vector]
  requires `default` database and rejects an already-bound cluster: separate
  target clusters are needed on this path, not just separate prefixes. Prove
  own-collection access and denial against a known second scope; do not delete
  retained external clusters to demonstrate cleanup. Other catalogue vector
  drivers are not automatically supported by this binding API.
- [ ] Document owner supplies approved external S3 endpoint/region/bucket/prefix
  and runtime list/read credentials, plus a distinct scoped uploader. Verify
  actual provider ACLs, object/version reads, public-content approval, indexing
  and replacement/retirement. [Binding][s3] records read-only intent, not ACL or
  upload capability. Do not reuse the unrelated Support uploader or fictional
  policies as client acceptance. Mounted folders are not the production path.
- [ ] Repeat the same real-provider adapter, immutable configuration and storage
  technology on the same logical deployment/app's staging and production targets,
  with separate credentials, SQL roles/schemas, vector scopes/resources, document
  prefixes and Desk bindings. Production indexes authorized production data;
  do not copy staging vectors, conversations or simulated receipts.
- [ ] Record actual source changes, policy answers, no-match/unknowns, compound
  questions and selected vehicle follow-ups in fresh/continuous sessions. Dealer
  reviews user-visible correctness, including material price/policy/availability
  errors; trust boundaries remain strict even where semantic uncertainty is allowed.
- [ ] Complete assigned-agent Desk claim/reply, visitor receipt, explicit AI return,
  unassigned denial, after-hours/contact and mobile/reload checks. Publish/apply/
  verify, then explicitly assign the same app; do not claim completion from a
  healthy container, page 200, source name or a different Support deployment.
- [ ] Name incident/freshness/usage/backup/rotation/offboarding owners, test the
  runbook's scoped disable path and agree retention/data exit before launch.

**Evidence ladder:** sanitized samples/local tests demonstrate parsing and
contract behavior only. A simulator demonstrates only its fictional protocol
and UI flow. Authorized real-provider staging demonstrates only that exact
scope/build. Real production bindings, fresh indexing, buyer/Desk journey and
dealer sign-off are separately required for launch. None is substituted by this
document or the current source BOM.

[v196]: ../../Platfrom/backend/src/main/resources/db/migration/V196__external_dealership_ai_composition.sql#L30
[data]: ../../Platfrom/backend/src/main/resources/db/migration/V174__simulated_dealership_customer_package.sql#L112
[compat]: ../../Platfrom/backend/src/main/resources/db/migration/V184__durable_dealership_document_vector_profile.sql#L1
[binding]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/externalintegration/README.md#apis
[http]: ../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/ProviderHttpClient.java#L46
[webhook]: ../../Platfrom/backend/src/main/resources/db/migration/V174__simulated_dealership_customer_package.sql#L237
[webhookcontroller]: ../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/controller/ProviderWebhookController.java#L28
[webhookservice]: ../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/ProviderWebhookService.java
[reads]: ../../Platfrom/backend/src/main/resources/db/migration/V174__simulated_dealership_customer_package.sql#L310
[metadata]: ../../Platfrom/backend/src/main/resources/db/migration/V208__dealership_inventory_filter_metadata.sql#L1
[engagement]: ../../Platfrom/backend/src/main/resources/db/migration/V196__external_dealership_ai_composition.sql#L104
[receiver]: ../../Platfrom/backend/src/main/resources/db/migration/V174__simulated_dealership_customer_package.sql#L466
[protected]: ../../Platfrom/backend/src/main/resources/db/migration/V179__productized_dealership_protected_action_and_target_placement.sql#L1
[execution]: ../../ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/service/RestActionExecutionService.java#L136
[sqlprobe]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentAiStateStorageProbe.java#L14
[vector]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentExistingVectorBindingService.java#L99
[s3]: ../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDocumentStorageBindingService.java#L90
