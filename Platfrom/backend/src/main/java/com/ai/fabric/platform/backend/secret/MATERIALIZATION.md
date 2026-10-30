# Deployment And Target Secret Materialization

## Protected Boundary

An editable deployment configuration or Marketplace install can contain a secret
reference, but that reference does not grant ownership of the stored credential.
Coolify and Railway resolve deployment environment placeholders through
`PlatformSecretService.resolveSecretForDeployment(name, deploymentId, targetProfileId)`.
The coordinates come from the deployment/release being provisioned, not the
customer's provider URL, header, install field, or secret name.

A stored deployment owner must match. If `target_profile_id` is present, the
exact target must also match and the row must retain managed Platform ownership.
Missing scope, a foreign deployment, a different target, and malformed target
ownership fail before the credential is returned. An empty owned value never
falls back to a global environment value with the same name. The error contains
no credential value.
The ownership check and value use one repository snapshot; there is no second
global lookup that could observe a newly created/bound credential after the check.

This applies to arbitrary provider `*ApiKeySecretRef` values, generic connector
header placeholders, and frozen target-authority/external-integration environment
materialization. Persisted `MANAGED_` connector credentials are collected for the
same scoped resolution; unresolved managed symbols remain owned by their
existing binding resolver.

## Connectivity And Preflight Reads

Both `GET /api/deployments/{deploymentId}/provider-connectivity` (saved draft)
and `POST /api/deployments/{deploymentId}/provider-connectivity/probe`
(request-supplied provider configuration) reach the same provider-secret resolver.
The customer editor gate proves ownership of that deployment, not of a referenced
credential. `DeploymentProviderSecretResolutionService` therefore resolves every
primary/paired candidate with `resolveSecretForDeployment(name, deploymentId, null)`.
It does not use global value/presence reads to bypass that check, or catch an
ownership denial and silently choose the global fallback.

These interfaces have no trusted target context. They deny target-bound support
credentials before any HTTP request, including when called for the owning
deployment. A `deploymentId` or `targetProfileId` inside customer provider JSON
cannot supply the missing authority. The same resolver protects its other
deployment-only planning, secret-summary and verification consumers. Existing
deployment-only credentials and deliberately shared provider defaults still
resolve. Release environment materialization, unlike these probes, has a trusted
exact target and can resolve a matching target-bound credential.

The separate `GET /api/deployment-provider/target-profiles/{targetProfileId}/preflight`
does not accept customer provider JSON: its controller is staff-only and its
service method already requires `PLATFORM_ADMIN`. This fix does not change that
existing guard or admin-owned target-provider credentials.

## Provisioning New Support Credentials

Store diagnostic and Desk-context credentials separately, with their internal
support deployment and exact target, using the existing service:

```java
secrets.upsertTargetManagedSecret(
    reference, value, supportDeploymentId, supportTargetProfileId, auditDetails);
```

The existing Platform-admin-only managed-secret endpoint also accepts the binding:

```http
PUT /api/platform/secrets/managed/MANAGED_SUPPORT_DIAGNOSTICS_STAGE
Content-Type: application/json

{
  "value": "<server-generated-credential>",
  "deploymentId": "<owning-support-deployment>",
  "targetProfileId": "<exact-support-target>"
}
```

Both coordinates are required when either is supplied. This is storage metadata,
not an assertion embedded in a name or an audit-only `targetProfileId`. It is
persisted by migration V220. Ordinary rotation preserves an existing target
binding; an existing owned credential cannot be reassigned to another deployment
or target. Use separate references for separate targets. CUSTOMER_ADMIN cannot
write or change this metadata through the secret API. The safe summary returns
`deploymentId` and `targetProfileId` for binding readback, never the secret value.

Main owns support configuration and rollout. Before provisioning or enabling
support keys, adopt this bound write path for both keys. The facade can recheck
its configured reference with `resolveSecretForDeployment` using its fixed support
binding. This change does not create any key, enable support, or change a hosted
configuration.

## Existing Contracts

Existing deployment-only managed/override ownership remains deployment-scoped;
a null target does not manufacture a target restriction on historical bindings.
Existing deliberately global credentials, including the shared OpenAI fallback,
retain their existing resolution behavior. Do not use that unscoped contract for
new privileged support credentials. Trusted control-plane callers still have
`resolveSecret`; customer-authored deployment placeholders do not use that API.
This is not a redesign of global provider-secret policy or another secret service.

## Focused Verification

```sh
mvn -q -Dtest=PlatformSecretServiceTest,PlatformSecretTargetMigrationTest,PlatformSecretControllerTargetBindingTest,DeploymentSecretMaterializationTest,DeploymentProviderConnectivitySecretOwnershipTest,DeploymentProviderSecretResolutionServiceTest,DeploymentProviderConnectivityServiceTest test
```

The regressions use test-only values and local fixtures. They cover exact-target
storage/migration, immutable ownership on rotation, CUSTOMER_ADMIN storage denial,
provider URL/key-reference injection, compiled Marketplace header references,
both materialization providers, and preserved shared OpenAI fallback. They are
not hosted rollout or live exfiltration probes.

Connectivity regressions use the real secret storage/resolver and mocked HTTP.
They exercise saved-draft and request-config entry points for orchestration,
generation, embedding and Qdrant references, foreign/owning deployments with
spoofed scope fields, primary/paired override bindings, no HTTP on denial, and
successful shared OpenAI/deployment-only owned credential probes.

Local verification on 2026-10-30: the original 14-class selection passed 259 tests.
After adding probe ownership and single-snapshot lookup coverage, the combined
18-class secret usage, Coolify/Railway provisioning, connectivity, managed vector,
release verification, target authority and external-integration selection passed
350 tests with zero failures, errors, or skips. That run includes all 62 tests in
the bounded selection above. `git diff --check` passed. No hosted operation, key
provisioning, or support configuration change was performed.
