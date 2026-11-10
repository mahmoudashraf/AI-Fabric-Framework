# Customer Secrets And Pack-Owned Authoring Metadata

## Scope

Two bounded Platform changes, with no framework/runtime release:

- Engage authoring obtains field identities from the selected pack's registry
  metadata. Shared assistant guidance contains no industry-specific field paths
  and no longer sends the catalogue of other experience packs to this task.
- `/smart-brains/:id/secrets` adds customer and delegated-staff management over
  the existing `platform_secrets` and provider-binding tables. No schema migration,
  new vault, new database, new role or new public value-read API is introduced.

## Protected Boundaries

The new `/api/deployments/{id}/secrets` API admits customer owners, Platform
administrators and customer-delegated operators, then requires Brain administrator
access. Operator delegation is checked again for every request. Ownership comes
from the authorized Brain, never request fields. Generated references are
namespaced by the Brain's exact encoded ID. Alias/value bounds protect environment
identifier and request-size execution requirements, not semantic AI decisions.

Only that Brain's non-platform-managed, non-target-bound deployment override
records are mutable. Global, foreign, managed and target-bound credentials are
not exposed or mutable. Returned summaries and audit details contain no values.
Provider creation and binding join one existing transaction and use
`REQUIRE_OVERRIDE`; paired values are atomic. Bound deletion is refused. Custom
deletion is an explicit UI confirmation and can leave a missing connection
reference; it does not silently substitute another key.

Existing Coolify/Railway materializers enforce Brain ownership for the generated
integration references. Existing target-bound S3/vector workflows remain separate.
Only reference names enter authoring context. The secrets page has no assistant
mutation scope. Publication, provisioning and restarting live services are not
part of saving a key.

## Verification Sources

- `CustomerDeploymentSecretServiceTest`: owned storage/rotation, safe summaries,
  provider/paired binding, unsupported purposes, foreign/global/managed/target
  denial, duplicate names, bound deletion and current staff delegation.
- `CustomerDeploymentSecretControllerTest`: admitted roles, machine-role denial,
  no ownership accepted from request JSON, value-free REST response.
- `CustomerStaffAccessPostgresTest`: real migrations, cookie authentication,
  cross-customer denial, same-cookie grant/rotation/revoke, existing provider
  binding, protected deletion and transaction rollback on binding-audit failure.
- Existing materialization/usage/provider-secret tests remain in the selection.
  Materialization additionally checks ordinary customer integration references
  for their owning Brain on both targets and rejection on a foreign Brain.
- `AIWorkspaceAuthoringContractTest` and authoring resource tests: each selected
  pack owns its field path, unknown pack is not substituted, other packs are
  absent from authoring context, secret references never include values.
- `Platfrom/ui/scripts/brain-secrets.browser.mjs`: actual UI/API wrapper with
  in-memory HTTP fixtures, including create/replace/cancel/delete, paired fields,
  failed saves, read-only access, responsive light/dark views and no external calls.
- Existing Brain navigation/settings/scoped-operations checks and full UI build.

The first PostgreSQL run had a test-only SQL table-name error, corrected to
`platform_deployment_provider_secret_bindings`; no product check was bypassed.
The existing navigation browser fixture needed a stub for the newly imported
page; its missing-export timeout was a harness failure, not a deployed regression.

Local final selection: 213 backend tests across 15 classes, including 11 real
PostgreSQL/cookie tests, all passed without skips. UI: six new browser groups,
34 existing navigation/settings/scoped-operation checks, the complete canonical
settings-navigation browser journey, and production build passed. Light/dark
desktop/mobile screenshots were inspected. Generated captures stay outside Git.

## Hosted Qualification

`qualify-ui.mjs --run <staging|production> <new-private-output-directory>` requires
`CUSTOMER_SECRETS_AUTH_REGISTRY`, using an ordinary customer login. It creates
one configuration-only Brain and fictional keys. Real UI create, rotation,
provider selection, deletion, mobile and server permission checks are exercised.
No inference, external provider call, publication or infrastructure is permitted.
Cleanup removes all canary keys/bindings, archives its empty Brain and signs out.
Raw hosted evidence and login material remain outside Git. Hosted source/digest
and exact completed rollout evidence are recorded after native verification;
source changes alone are not deployment proof.
