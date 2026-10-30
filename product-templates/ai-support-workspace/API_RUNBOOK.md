# Catalog And Installation Runbook

These are current Platform controller/record shapes. Use an authenticated,
authorized API client and its normal CSRF/session handling. No credentials or
customer identifiers are supplied by this package. Requests below are not run
by the dry-run scripts. Replace fixture IDs with actual owned resource IDs.

## 1. Review And Publish The Catalog

Run `node scripts/validate-dry-run.mjs --dry-run`. Its printed `requests` contain
the complete normal submission envelopes. Publish the DATA dependency before
the TEMPLATE. Review the LoomAI-only ACTION plugin separately from the fictional
Acme validation plugin; their inclusion in this source package is not approval
to offer the internal integration indiscriminately to other customers.

Use an existing active, verified publisher authorized for the submitting actor.
If one must be created, the supported request is:

```http
POST /api/marketplace/publishers
Content-Type: application/json

{"slug":"your-publisher","displayName":"Your company","contactEmail":"publisher@example.invalid"}
```

Publisher verification is a Platform-admin review, not a customer self-approval:

```http
PUT /api/marketplace/publishers/{publisherId}/verification
Content-Type: application/json

{"verificationStatus":"VERIFIED","status":"ACTIVE"}
```

For each manifest, submit using the exact body produced by the dry-run:

```http
POST /api/marketplace/publishers/{publisherId}/submissions
Content-Type: application/json
```

Send the corresponding full `requests[n].body` emitted by the dry-run. It contains
`pluginSlug`, `releaseChannel` and the complete real `manifest` object; do not
replace the manifest with an abbreviated placeholder. Record the returned
`pluginVersionId`. A Platform admin
then validates and publishes that exact submission:

```http
POST /api/marketplace/submissions/{pluginVersionId}/validate
Content-Type: application/json

{"reviewNotes":"Reviewed source ownership, read-only authority and 010.54 offline contract evidence."}
```

```http
POST /api/marketplace/submissions/{pluginVersionId}/publish
Content-Type: application/json

{"reviewNotes":"Approved catalog revision; hosted readiness remains separately tracked."}
```

Verify with `GET /api/marketplace/plugins/{pluginId}/versions/{manifest.version}`. No direct
database seed, hand-authored install rows or bypass of publisher review is needed.
Do not resubmit an existing immutable plugin/version: inspect it or publish a new
revision when the actual configuration changes.

Acme ACTION `1.0.0` is published staging history. Main did not publish internal
ACTION `1.0.0` and confirmed staging GET returns `404`. Keep both snapshots unchanged. Publish the new
`1.0.1` files listed by `catalog.json`, not corrected bytes under `1.0.0`.
The mounted mechanics TEMPLATE entries use release channel `DEMO`; the dry-run
preserves that field. Publish corrected ACTIONs before their matching TEMPLATE.
The existing mounted DATA dependency is read back, not republished or changed.

## 2. Bootstrap The Reusable Template

```http
POST /api/marketplace/templates/mkp-template-ai-support-workspace/bootstrap
Content-Type: application/json

{"pluginVersion":"1.0.0","name":"My support workspace","environment":"staging","vectorProvisioningMode":"LOCAL_MANAGED","customerId":"OWNED_CUSTOMER_ID","tenantId":"OWNED_TENANT_ID"}
```

The full fixture is `fixtures/requests/bootstrap.json`. Its neutral Lucene starter
uses `LOCAL_MANAGED` vectors; S3 source files are a separate storage integration.
An external vector backend can be selected in the ordinary draft before publish.
Select owned vector and
AI-state storage/provider settings through normal deployment configuration; the
example does not supply infrastructure credentials. Bootstrap's DATA dependency
may be configuration-required until the storage binding is supplied. That is
expected, not a successful publish-ready deployment.

For the LoomAI internal instance, use its fixed internal owner/tenant and the
reviewed Console support identity adapter. For Acme, create a separate owned
deployment/tenant configuration and Acme identity connection. Do not reuse internal
support case principals, assertions, service credentials, storage or Desk agents.

## 3. Configure Knowledge And Optional READ Tools

Get the dependency's actual `installId` with:

```http
GET /api/deployments/{deploymentId}/marketplace-installs
```

Create a read-only owned document-storage binding through the existing storage
connection workflow, then update the install:

```http
PUT /api/deployments/{deploymentId}/marketplace-installs/{installId}
Content-Type: application/json

{"pluginVersion":"1.0.0","status":"ENABLED","config":{"documentStorageBindingRef":"OWNED_STORAGE_BINDING_REF"},"secretRefs":{}}
```

For the generic conversation-first product, keep the default areas with empty
`readActions`; no case provider is required. Connected internal/Acme mechanics
use the separate normal-offer path in [Mounted Demo Qualification](MOUNTED_DEMO_QUALIFICATION.md).
The offer includes a reviewed `EXTERNAL_SERVICE` slot and matching profile for
its ACTION `1.0.1`; normal bootstrap initializes its symbolic install fields.
Leave ACTION defaults empty, then approve exact endpoints and bind the server
credential through existing external-integration APIs after version publication.
Do not directly submit the compiler-only `*-install.json` fixtures as a substitute
for that profile. Those fixtures test compilation but do not project endpoints.
No raw service credential belongs in a manifest, draft, public helper or browser.
The explicitly simulated upstream in [`fixtures/service/README.md`](fixtures/service/README.md)
provides the two Acme paths; reuse its existing deployment for qualification,
not another service. It is not a production customer adapter.

Install changes use the existing draft compilation. Inspect install warnings and
resolve missing fields rather than replacing generated action routing. The
approved adapter must authenticate service calls and independently enforce the
verified caller's current ownership/scope; no model parameter selects authority.

### 3.1. Explicit Generic And Acme Identity Policy

Use the existing deployment-local Supabase broker; neither proof needs a custom
customer backend merely to sign in. Configure the owner-approved server policy
**before creating the Engage installation**:

- Generic: `fixtures/requests/generic-identity-profile.json`.
- Acme: `fixtures/requests/acme-identity-profile.json`.

These are `securityConfig` fragments, not complete replacement security sections.
Merge into the current owned draft, preserving admin/connector security, exact
target issuer/audience bindings and signing-key references. Replace `project-ref`
with the approved Supabase project and use the exact Engage HTTPS origin. Keep
`externalIdentityBrokerEnabled=true`, `externalIdentityProvider=SUPABASE`,
`externalIdentityAudience=authenticated`, `externalIdentityRequireVerifiedEmail=true`,
issuer `https://<project>.supabase.co/auth/v1` and JWKS
`https://<project>.supabase.co/auth/v1/.well-known/jwks.json`.
Set `externalIdentitySupabasePublicKey` to the same project's publishable or
legacy anon key. Engage's normal deployment update supplies it from the selected
authentication profile when verified email is required. The runtime verifies the
signed JWT first, then reads the provider-owned `/auth/v1/user` record and checks
the exact subject and email confirmation. Editable `user_metadata` is never
confirmation evidence. Revoked identity, provider outage and missing lookup
configuration stay explicit; do not disable email verification to hide them.
See the [Supabase user contract](https://supabase.com/docs/reference/javascript/auth-getuser).

`externalIdentityGrantedScopes` is an explicit server authorization policy. The
provided CSVs contain chat/handover scopes, `vector:document`, exact versioned
specialist refs and, for Acme only, `acme:support:read`. Compare the reviewed
list with the published `specialistAssistance.compiled.invocations[].requiredScopes`
after any area, resolution or tool change. There is no wildcard, automatic
scope derivation at invocation time, or grant merely because a tool is installed.
Remove handover scopes if that capability will not be enabled; never copy
`support:diagnostics:read`, runtime-admin or another customer's scopes here.

Normal provisioning projects this exact explicit list to all three server values:

```text
AI_FABRIC_RUNTIME_EXTERNAL_IDENTITY_GRANTED_SCOPES
AI_FABRIC_RUNTIME_PUBLIC_TOKEN_AUTHENTICATED_DEFAULT_SCOPES
AI_FABRIC_RUNTIME_PUBLIC_TOKEN_AUTHENTICATED_ALLOWED_SCOPES
```

It does **not** copy it to
`AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ANONYMOUS_GRANTED_SCOPES`. Anonymous bootstrap
keeps only its existing chat scopes (and chat lifecycle/handover scopes when
enabled); it gains no private specialist, vector, tool or diagnostic authority.
The native invoker receives the issued identity unchanged and requires every
configured invocation scope to be present. Missing permission is not repaired
by appending scopes or replacing the caller.

In Engage, use `fixtures/requests/engage-supabase-profile.json` as the authentication
and connection fragment of the normal installation request. Supply only the
Supabase **publishable** key, never a service-role key, API secret or scope list
in browser configuration. This mixed policy starts with public-safe conversation
and allows sign-in for protected capabilities. For a sign-in-first proof, select
`interactionPolicy=login-required`, `connectionMode=public-runtime-authenticated`
and `connectionProfileCode=runtime-authenticated-broker` instead. All profile
versions remain `1.0.0`. An existing managed identity projection must be
reconciled through the normal Engage/deployment update flow; do not delete or
forge `aiWorkspaceManagedExternalIdentity` metadata to bypass drift checks.

The browser signs in to that Supabase project, then calls the **assigned runtime**:

```http
POST /api/public/auth/runtime-token
Authorization: Bearer <Supabase user access token>
Origin: https://<exact-engage-host>
Content-Type: application/json

{"schemaVersion":"loomai-workspace-runtime-token-request-v1"}
```

The broker verifies signature, issuer, audience, lifetime, non-anonymous subject,
configured email requirement and allowed origin. It issues a short-lived runtime
token with fixed deployment/customer/tenant context and the reviewed server
scopes, not scopes requested by the browser. Engage then uses that runtime token
for queries. The Acme connector uses its separate server-only API key to reach
the simulated upstream; no browser contacts it with that secret.

**Authorization limit:** the current broker grants its configured scope list to
every identity it accepts from that project. This is appropriate for these
isolated public-safe guides and fictional Acme observations, not private account
diagnostics, operator access or automatic customer membership. Private upstream
resources still need an adapter that verifies their actual caller ownership.
Do not claim the simulator's service-key authentication supplies that check.

**Internal LoomAI support is separate.** Its support identity adapter may derive
compiled scopes only after owner/case authorization and binding to the fixed
internal tenant. The diagnostic facade must still verify a registered case,
selected-resource ownership and current consent on every diagnostic read. Do not
replace this path with the generic/Acme static broker profile or anonymous grants.

## 4. Customize, Publish And Apply

```http
GET /api/deployments/{deploymentId}/draft
```

Compose the `generic`, `loomai` or `acme-cloud` configuration offline with
`scripts/compose-draft.mjs`, using the complete current draft. This applies the
normal bounded provider settings without losing the selected provider/model.
Submit the resulting body through:

```http
PUT /api/deployment-drafts/{draftId}
```

The body contains `behaviorConfig`, `shellConfig` and the current `providerConfig`
with `orchestrationModeOverrides.executor` set to `ITERATIVE`, `maxIterations=2`
and `RAG_IF_ACTIONS_INSUFFICIENT`. Areas, instructions, source/READ bindings,
resolutions and limits are editable. Preserve existing handover configuration.
The shell selects `executor`; this is an existing mode under the unchanged
`CONVERSATIONAL` deployment behavior, not a new SUPPORT behavior.

Do not insert `consult_specialist` into `actionsConfig` or connector routes. The
runtime owns that action. Normal provisioning projects its explicit read-planner
allowlist with the selected installed eligible READs. Do not add a wildcard or
an automatic all-actions fallback. Simple documentation questions need not
invoke specialists; selection remains model-driven within the declared catalogue.

```http
POST /api/deployment-drafts/{draftId}/validate
```

Fix blocking ownership, execution-contract or unresolved-install issues first.
Then:

```http
POST /api/deployment-drafts/{draftId}/publish
```

Read the immutable version's manifest. Its generated
`behaviorConfig.specialistAssistance.compiled` contains official resources and
the exact invocation catalogue; sibling `contentHash` attests its serialized
payload. Do not upload handwritten compiled resources or edit the snapshot.

Inspect `GET /api/deployments/{deploymentId}/versions/{versionId}/compatible-source-artifacts`.
The runtime artifact must attest `SPECIALIST_ASSISTANCE`,
`conversational-specialist-assistance` and its real execution/migration contracts.
Do not substitute a source artifact that only supports older chat behavior.

```http
POST /api/deployments/{deploymentId}/apply/{versionId}?targetProfileId={targetId}&sourceArtifactId={approvedArtifactId}
Idempotency-Key: support-workspace-apply-001
```

Poll that exact release from `GET /api/deployments/{deploymentId}/releases` to
terminal verified success. A healthy prior runtime is not proof of a new apply.
After configuration-only changes, use this same version/release mechanism; the
bootstrap template is not an upgrade pin or an approval gate.

Inspect `GET /api/deployments/{deploymentId}/verification-runs` for this exact
release and version. Pack `conversational-specialist-assistance-v1` must have a
passed `runtime_specialist_assistance_matches_expected` check. Its details must
show matching expected/actual compiled hashes, capability readiness and the exact
invocation catalogue. The runtime overview's `specialistAssistance` readback is
derived from the verified bundle and actual registry ID lookups, not prompts.
The existing behavior-market-readiness runner reuses authenticated conversation
and continuation checks plus this persisted result before submitting pack proof.
This focused pack proves loaded mechanics, not specialist answer quality; run
the separate quality scenarios against both real hosted integrations afterward.

## 5. Index And Prove The Chosen Knowledge Set

Put only the chosen variant's `.txt` files into its own approved source prefix.
Use `fixtures/knowledge-sets.json` metadata and the normal document-knowledge UI
or APIs under:

```text
/api/deployments/{deploymentId}/targets/{targetId}/releases/{releaseId}/document-knowledge
```

The existing operations are connector status, discovery, source registration,
preview, explicit index, reconcile and retrieval proof. Mutating source/index
commands use their own stable `Idempotency-Key`. Index acceptance is not completion;
reconcile the recorded work and prove retrieval before claiming freshness.
Source-object deletion is not requested. Cases and diagnostics are not indexed.

For a separately labelled internal/Acme staging mechanics proof without S3, see
[the existing mounted-demo contract](MOUNTED_DEMO_QUALIFICATION.md). Do not alter
this generic customer template's S3 dependency or report mounted evidence as
customer-storage qualification.

## 6. Enable The Existing Managed Desk

Use **Enable Desk** as the linked customer owner on a verified target with a
clean draft. Its existing API is:

```http
POST /api/deployments/{deploymentId}/desk/enable
Content-Type: application/json

{"targetProfileId":"OWNED_TARGET_ID","releaseId":"VERIFIED_RELEASE_ID","operationKey":"support-workspace-desk-enable-001","enableReviews":false}
```

This existing flow installs the reviewed handover action and Desk integration,
registers the target-specific binding/callback secrets and publishes/applies the
configuration. Do not precreate a Basic Inbox instance or copy another target's
Desk credentials. Read status with:

```http
GET /api/deployments/{deploymentId}/desk?targetProfileId={targetId}&releaseId={releaseId}
```

Assign real signed-in human agents to that deployment's SUPPORT role using Desk.
Ownership alone is not a SUPPORT assignment. Verify the actual binding's access,
claim, reply, return-to-AI and close behavior. Human action-review configuration is
not enabled merely because handover works.

## 7. Internal Console Host Installation

This section is only for LoomAI's own support integration. Other customers use
their ordinary direct-runtime Engage installation or reviewed adapter.

With `platform.support.enabled=true` and its fixed internal owner configured,
create an ordinary Engage installation for that owner's verified, deployment-
bound consumer through
`POST /api/platform/customers/{customerId}/ai-workspace-installations`.
Use generic pack `generic-workspace@1.0.0`, connection mode
`BACKEND_MEDIATED_PRIVATE_RUNTIME`, profile `platform-support-case-host@1.0.0`,
empty `connectionConfiguration`, `EMBEDDED` delivery and the exact Console
origin. The normal public interaction policy is not runtime anonymous access:
the case adapter requires the current authorized Console bearer on every call.
Keep the installation's public configuration free of credentials.

Resolve normal readiness, activate through the same installation lifecycle and
set `platform.support.installation-id` to its public installation identifier.
The private adapter checks this exact profile/version and fixed target. The
profile is not offered to unrelated customers and public script-manifest lookup
returns unavailable; the Console host uses its authenticated case bootstrap,
not a public installer or a forged Shopify adapter. No direct database writes
create this installation. Follow `support/README.md` and
`support/DESK_EXTERNAL_CONTEXT.md` for the fixed binding, separate secrets and
Desk context registration.

## 8. Hosted Acceptance And Evidence

Use `fixtures/quality-scenarios.json` against the real installation routes.
Record exact template/plugin versions, deployment/target/release, document work,
runtime bundle hash, actual specialist receipts and observations. Prove the
generic no-case-provider path, internal consent boundary, independent Acme
identity/tooling, reload/isolation and one real Desk roundtrip.

`fixtures/acme-read-observations.json` may be supplied only to an explicitly
simulated test adapter; it is not a live health result or an authorization source.
Never replace a failed real read silently with fixture data. A missing adapter,
managed value, storage permission or runtime capability is an explicit remaining
integration item, not offline acceptance or an excuse to bypass its boundary.
