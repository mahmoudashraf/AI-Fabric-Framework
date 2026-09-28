# External Vehicle Provider Simulator

This is a hosted verification dependency for LoomAI's provider-neutral,
deployment-local integration substrate. It is not an Auto Trader emulator,
customer product, Marketplace integration, Platform data-plane service, or
production dependency.

It provides two deliberately different contracts:

- `profile-a`: form token exchange, bearer auth, page/size pagination, and a
  query-bound account;
- `profile-b`: API-key auth, cursor pagination, and a path-bound account.

Both profiles expose deterministic fictitious inventory. The protected control
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
```

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
