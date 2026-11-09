# External Vehicle Provider Simulator

This is a hosted verification dependency for LoomAI's provider-neutral,
deployment-local integration substrate. It is not an official Auto Trader
sandbox, a complete provider emulator, a customer product, a Platform
data-plane service, or a production dependency.

It provides three isolated profiles:

- `profile-a`: form token exchange, bearer auth, page/size pagination, and a
  query-bound account;
- `profile-b`: API-key auth, cursor pagination, and a path-bound account;
- `autotrader`: synthetic records behind the current publicly documented Auto
  Trader Connect wire shape used by the first dealership canary. It reproduces
  the documented `/authenticate`, `/stock`, bearer-token, pagination, stock
  envelope, ordered stock-media identity/reference shape, notification
  envelope, `AutoTrader-Signature`, epoch-second timestamp, and raw-body HMAC
  contract. Synthetic image bytes are hosted by this simulator; they are not
  Auto Trader assets. It does not imply sandbox access, certification,
  endorsement, or rights to production data.

All profiles expose deterministic fictitious inventory. The protected control
API can reset fixtures, mutate records, create tombstones, inject bounded
failures, and send signed provider events. State is stored in a persistent H2
file so restart tests exercise durable provider state.

## Local contract test

```bash
mvn test
```

## Local container

Build from the repository root:

```bash
docker build \
  -f verification-support/external-vehicle-provider-simulator/deploy/container/Dockerfile \
  -t loomai/external-vehicle-provider-simulator:local \
  .
```

Every credential must be supplied through runtime environment variables. See
[`deploy/coolify/README.md`](deploy/coolify/README.md) for the complete list.

## Public provider routes

```text
POST /api/profile-a/authenticate
GET  /api/profile-a/vehicles?account=...&page=1&pageSize=...
GET  /api/profile-a/vehicles/{vehicleId}?account=...

GET  /api/profile-b/accounts/{ownerRef}/vehicles?cursor=...&limit=...
GET  /api/profile-b/accounts/{ownerRef}/vehicles/{vehicleId}

POST /authenticate
GET  /stock?advertiserId=...&lifecycleState=FORECOURT&page=1&pageSize=...
GET  /stock?advertiserId=...&stockId=...&page=1&pageSize=1
GET  /media/{size}/{imageId}.webp
```

The stock response's ordered `media.images` collection uses `imageId` and
`href`. Supported synthetic delivery sizes are `w300h225`, `w720h540`, and
`w1024h768`. Media delivery is public and immutable-cacheable, while stock
metadata remains protected by the provider bearer-token contract. This route
simulates already-attached stock media only; no Media Updates upload or attach
API is claimed.

The Auto Trader profile is derived only from the current
[Auto Trader Connect developer documentation](https://developers.autotrader.co.uk/api),
[Stock Sync introduction](https://help.autotrader.co.uk/hc/en-gb/articles/21846314775453-Introduction-to-Stock-Sync),
[Stock Sync go-live checks](https://help.autotrader.co.uk/hc/en-gb/articles/22673947111325-Go-Live-checks-for-Stock-Sync),
and [Media Updates introduction](https://help.autotrader.co.uk/hc/en-gb/articles/21846827095325-Introduction-to-Media-Updates).
Its fixture is versioned so later public-contract changes are deliberate and
reviewable.

## Protected verification controls

All `/internal/control/**` calls require `X-Simulator-Control-Key`.

```text
GET    /internal/control/status
POST   /internal/control/reset
PUT    /internal/control/accounts/{profile}/{accountId}/vehicles/{vehicleId}
DELETE /internal/control/accounts/{profile}/{accountId}/vehicles/{vehicleId}
PUT    /internal/control/accounts/{profile}/{accountId}/fault
POST   /internal/control/accounts/{profile}/{accountId}/events
```

Supported one-shot or bounded fault modes are `UNAUTHORIZED`, `FORBIDDEN`,
`RATE_LIMITED`, `UNAVAILABLE`, `TIMEOUT`, `MALFORMED_RESPONSE`, and
`PARTIAL_PAGE`. Event variants are `VALID`, `DUPLICATE`, `DELAYED`,
`OUT_OF_ORDER`, `MALFORMED`, `WRONG_RESOURCE`, and `WRONG_SIGNATURE`.

Passing this simulator permits only the
`HOSTED_GENERIC_SUBSTRATE_VERIFIED` claim. Real provider sandbox and production
gates remain independent.

The Auto Trader profile additionally permits a narrowly worded
`PUBLIC_DOCUMENT_CONTRACT_CANARY_VERIFIED` engineering claim after its hosted
canary passes. It never permits `AUTOTRADER_SANDBOX_VERIFIED` or
`AUTOTRADER_PRODUCTION_READY`.

The source fixture is now `external-vehicle-provider-v3-inventory-evidence`.
Its optional evidence and stock-media contracts do not inherit older hosted evidence.
A fresh hosted simulator deployment, dealership apply/reindex, and action/RAG
browser proof are required before recording hosted evidence for this extension.

### Optional inventory evidence

The protected control input's optional `evidence` object is persisted in the
existing simulator vehicle table. Its fictional seats, battery range, equipment,
advertiser town and forecourt date are authored here, independently of the website
inventory. `DEMO-1006` deliberately supplies none of these optional facts. Omitting
`evidence` on a complete control PUT clears it; no fixture overlay repairs it.
The default fixture version changes on the next deployment and normal startup
initialization resets the simulator when that version differs. An explicit
`SIMULATOR_FIXTURE_VERSION` override must be considered by the rollout owner.
Reset deletes all profiles' vehicles, account/source versions, event sequences,
faults and fixture-run identity before reseeding. There is no persisted simulator
callback registry: each event control request supplies its `targetUrl`. The reset
code does not modify external connector callback bindings, but is not suitable
for preserving the hosted inventory or other profiles.

For the existing hosted fixture, preserve its exact persisted v2 fixture-version
override and H2 volume through the upgrade. Do not call reset. Apply six bounded
PUTs to the existing AutoTrader account/record IDs through the protected controls,
preserving each record's current base fields and adding the reviewed evidence.
The PUT is a complete record replacement, not a merge; omitted evidence is cleared.
It increments only that account's source version and updates the named record,
without resetting event sequences, other accounts or callback routing. Verify the
same fixture reset ID and unchanged other-profile records afterward. The v3
default is appropriate only when a fresh/reset fixture is explicitly intended.

Official wire fields were checked against the
[Stock API documentation](https://developers.autotrader.co.uk/api#stock-api):
`vehicle.batteryRangeMiles` (miles), `vehicle.seats`, `features[].name/type`,
`advertiser.location.town`, `adverts.reservationStatus`, `metadata.lastUpdated`,
`metadata.lastUpdatedByAdvertiser` and `metadata.dateOnForecourt`. The public
contract does not specify a battery-range test-basis field; none is emitted or
inferred. A missing range is not zero, and a missing feature list does not prove
the absence of equipment. The forecourt date is a separately supplied date,
not the date of the latest update.

The DATA/ACTION fixtures map these to `electricRangeMiles`, `seats`, `features`,
`location` (advertiser town only), `reservationStatus`, `sourceUpdatedAt`,
`sourceUpdatedByAdvertiserAt` and `dateOnForecourt`. Source update timestamps
are distinct from the READ envelope's synchronization time. Stock READs and stored
provider records retain structured features; the dealership pack renders their
supplied names and types. Downstream LLM READ-evidence serialization may still
display feature maps using Java Map-style strings. This cosmetic serialization
limitation remains deferred; no domain-specific connector normalizer, framework
change or mapping DSL is introduced. No field is inferred from
fuel type, derivative names or website seeds. These fixture edits do not revise
published Platform manifests; the rollout owner must revise the actual installed
DATA/ACTION/template mappings, synchronize and verify each target independently.

## Current hosted evidence

The strict staging canary passed on 2026-09-28 using simulator fixture
`external-vehicle-provider-v1`, two isolated deployments, runtime source
`86abb0320c5af2397231cf40077194ba0435efd2`, and verifier source
`79b23348f`. It covered sync, indexing, retrieval, mutation/deletion, provider
faults, signed events, replay/dead-letter recovery, restart persistence,
immutable rollback, destructive PostgreSQL backup/restore, convergence, and
hard decommission.

The bounded, credential-free result is committed at
[`evidence/2026-09-28-hosted-generic-substrate.json`](evidence/2026-09-28-hosted-generic-substrate.json).
The two temporary deployments and every temporary provider resource, backup,
restore helper, and scoped fixture credential were removed after the pass.
This evidence is generic and must never be cited as named-provider evidence.

### Public-document contract canary

The separate 2026-10-03 canary passed 26 checks against one immutable
provider-backed dealership deployment. It covered the synthetic documented
authentication and stock shapes, targeted signed-event reconciliation,
negative notifications, duplicate/delayed delivery, `6 -> 7 -> 6` index
convergence, provider action facts, indexed evidence, and anonymous chat.

Run it only against an approved synthetic staging deployment:

```bash
PLATFORM_BASE_URL=https://<staging-platform> \
PLATFORM_API_KEY_FILE=/path/to/platform-api-key \
RUNTIME_BASE_URL=https://<deployment-runtime> \
CONNECTOR_BASE_URL=https://<deployment-connector> \
SIMULATOR_BASE_URL=https://<simulator> \
SIMULATOR_CONTROL_API_KEY_FILE=/path/to/control-key \
SIMULATOR_AUTOTRADER_ADVERTISER_ID=<synthetic-advertiser> \
SIMULATOR_AUTOTRADER_API_KEY_FILE=/path/to/provider-key \
SIMULATOR_AUTOTRADER_API_SECRET_FILE=/path/to/provider-secret \
EXPECTED_DEPLOYMENT_VERSION_ID=<version-id> \
EXPECTED_SOURCE_ARTIFACT_ID=<source-artifact-id> \
./scripts/verify-autotrader-public-contract-hosted-canary.py \
  --confirm-mutation
```

The script always attempts to tombstone its synthetic canary record and restore
the baseline vector count. `--confirm-mutation` is mandatory to prevent an
accidental read-only invocation from being misreported as the hosted claim.
Bounded evidence is committed at
[`evidence/2026-10-03-autotrader-public-contract-hosted.json`](evidence/2026-10-03-autotrader-public-contract-hosted.json).
It contains no credentials or stock payloads and proves no real Auto Trader
access or approval.

## Hosted LoomAI canary

After deploying the simulator and the current Platform/runtime source to
staging, run the end-to-end verifier from the repository root:

```bash
PLATFORM_BASE_URL=https://<staging-platform> \
PLATFORM_LOGIN_EMAIL_FILE=/path/to/admin-email \
PLATFORM_LOGIN_PASSWORD_FILE=/path/to/admin-password \
SIMULATOR_BASE_URL=https://<simulator> \
SIMULATOR_CONTROL_API_KEY_FILE=/path/to/control-key \
SIMULATOR_PROFILE_A_ACCOUNT_ID=<profile-a-account> \
SIMULATOR_PROFILE_A_KEY_FILE=/path/to/profile-a-key \
SIMULATOR_PROFILE_A_SECRET_FILE=/path/to/profile-a-secret \
SIMULATOR_PROFILE_A_WEBHOOK_SECRET_FILE=/path/to/profile-a-webhook-secret \
SIMULATOR_PROFILE_B_ACCOUNT_ID=<profile-b-account> \
SIMULATOR_PROFILE_B_API_KEY_FILE=/path/to/profile-b-key \
SIMULATOR_PROFILE_B_WEBHOOK_SECRET_FILE=/path/to/profile-b-webhook-secret \
COOLIFY_BASE_URL=https://<staging-coolify> \
COOLIFY_API_TOKEN_FILE=/path/to/coolify-token \
EXPECTED_SOURCE_COMMIT=<full-commit-sha> \
./scripts/verify-external-http-provider-hosted-canary.py
```

The default path is destructive only to resources created by that run. It
publishes immutable internal verification fixtures, creates two temporary
deployments, proves sync/index/event/fault/restart/backup-restore/rollback
behavior, hard-decommissions both deployments, and verifies automatic managed
secret cleanup. The bounded evidence JSON defaults to `/tmp` and contains no
credential values or provider payloads.

`--skip-backup-restore` exists only for script development and can never satisfy
the hosted claim gate. `--keep-resources` is a diagnostic escape hatch and also
prevents a passing claim because decommission evidence is then absent.
