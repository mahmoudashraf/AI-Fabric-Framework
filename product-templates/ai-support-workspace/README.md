# AI Support Workspace

Reusable Marketplace support configuration for 010.54. Acme ACTION `1.0.0` was
published on staging. Main explicitly skipped internal ACTION `1.0.0` and
confirmed its staging GET returns `404`. Both source snapshots stay unchanged.
The corrected ACTION `1.0.1` and mounted mechanics TEMPLATEs were separately
published and read back on staging on 2026-10-30. See `catalog.json` and the
010.54 execution record; publication is not hosted journey qualification.
This package adds no service, database, execution engine or plugin type.

## Product Contract

- One `CONVERSATIONAL` front door and one owner-authorized canonical conversation.
- Editable `specialistAssistance` areas, knowledge bindings, READ-tool slots,
  official specialist-chain resolutions and bounded limits.
- Conversation-first operation when no case provider exists. Never invent a
  ticket or require LoomAI's internal case registry for another business.
- Optional shared LoomAI Desk through the existing **Enable Desk** flow, not a
  per-deployment Inbox service or copied Desk credentials.
- Normal draft -> validate -> publish -> apply -> verify. Templates bootstrap
  settings; they do not pin later models, runtime images or customization.

## Contents

- `manifests/ai-support-workspace.manifest.json`: normal `TEMPLATE`; two editable
  documentation areas and one sequential resolution. READ slots start empty.
- `manifests/support-documents.manifest.json`: normal `DATA`; customer-owned
  S3-compatible documents, deployment-only derived index, explicit approval.
- `manifests/loomai-support-diagnostics.1.0.1.manifest.json`: normal `ACTION` overlay
  for the internal LoomAI support instance; four consented, case-scoped reads.
- `manifests/acme-cloud-support-reads.1.0.1.manifest.json`: a second, fictional
  company's own `ACTION` integration; no LoomAI diagnostic authority.
- The two unversioned ACTION filenames retain their immutable `1.0.0` contents;
  they are history, not the next publication payloads. `1.0.1` adds only explicit
  `adapterType: connector-http` and the version increment.
- `manifests/*-support-mounted-mechanics.manifest.json`: two separate `DEMO`
  TEMPLATE variants for normal staging offer bootstrap. Each requires the
  existing mounted DATA plugin and only its own corrected ACTION. Neither
  replaces or modifies the reusable S3 template.
- `overlays/`: ordinary specialist-assistance values and shell configuration.
  Each connected variant has two distinct areas and a cross-area parallel-capable
  resolution. Publication generates official resources; these are not chain DSLs.
- `documents/`: four LoomAI customer-safe guides and three fictional Acme guides.
- `fixtures/`: actual API request bodies, source metadata, simulated observations
  and quality scenarios. They are inputs, not fabricated passing evidence.
- `fixtures/service/`: stateless authenticated Acme mock upstream and Dockerfile,
  with two READ endpoints and the three approved fictional text documents.
- [API Runbook](API_RUNBOOK.md): catalog review/publication, bootstrap, configure,
  document indexing, Desk activation and hosted acceptance.
- [Mounted Demo Qualification](MOUNTED_DEMO_QUALIFICATION.md): the existing
  non-production mounted DATA plugin for a separately labelled internal/Acme
  mechanics canary when S3 is unavailable, not a customer-storage substitution.

## Validate Without Credentials Or API Calls

From this directory, with Node 20+, JDK 21 and the backend Maven dependencies
already cached:

```sh
node scripts/validate-dry-run.mjs --dry-run
```

The command runs the focused backend test offline, then prints normal publisher
submission request bodies. It has no apply mode, HTTP client or credential-file
option. If real contract validation fails, it exits unsuccessfully and emits no
claim that the package is valid. Maven may need a separate ordinary dependency
resolution step on a fresh machine; the dry-run does not turn online mode on.

The test invokes `MarketplaceManifestService`, `ExternalIntegrationPolicy`, the
existing action-contribution compiler, the install-backed authority resolver and
`SpecialistAssistanceCompiler`. It checks immutable ACTION `1.0.0` and S3 source
bytes, the metadata-only `1.0.1` corrections, and both mounted TEMPLATE contracts.
It also deserializes request fixtures into the actual Platform API record types.
This is offline contract evidence, not provider, storage, authentication or Desk
qualification.

To prepare a normal draft update from a previously retrieved owned draft:

```sh
node scripts/compose-draft.mjs /path/to/GET-draft-response.json loomai
node scripts/compose-draft.mjs /path/to/GET-draft-response.json acme-cloud
node scripts/compose-draft.mjs /path/to/GET-draft-response.json generic
```

The output is an `UpdateDeploymentDraftRequest` body with `behaviorConfig`,
`shellConfig` and the preserved `providerConfig` plus bounded orchestration
settings. Existing provider/model choices and handover remain. It does not
replace security, ownership, storage or live assignment. Review the body locally;
do not publish saved deployment configuration as a public artifact. Read tools
must be installed and configured before a connected overlay is published.

## Orchestration And Release Qualification

`CONVERSATIONAL` remains the deployment behavior, with the existing `executor`
chat mode for governed reads and retrieval. The mode named `conversational`
disables both and is not appropriate for this connected workspace. The composer
applies the existing provider override contract: `ITERATIVE`, two iterations,
`RAG_IF_ACTIONS_INSUFFICIENT`. Simple documented questions can still be answered
directly with approved retrieval; no rule forces every request through all areas.

The native `consult_specialist` is contributed by the runtime registry, never
by a connector ACTION manifest. For enabled assistance, provisioning emits an
explicit read-planner allowlist: `consult_specialist` plus only area-selected,
installed eligible READ actions. Unrelated installed actions, writes and wildcard
authority are not added. Nested workers retain their own narrower declared READ
catalogue and caller-scope checks.

The existing release verifier runs pack `conversational-specialist-assistance-v1`
through check `runtime_specialist_assistance_matches_expected`. It compares the
published compiled hash and exact invocation catalogue with the admin overview,
including actual registry readiness and advertised extension/capability/pack IDs.
The existing behavior-readiness flow also requires normal authenticated query,
continuation and structured-result proof and the persisted exact-version bundle
qualification. Missing or stale readback cannot be replaced with submitted PASS
labels. This is deployment-integrity validation, not model-answer text matching.

## Binding Slots And Boundaries

The generic template contains no endpoint URL, case-provider requirement,
customer secret, internal LoomAI route or runtime credential. Its required DATA
install remains configuration-required until the owner supplies their storage
binding. Do not publish around that unresolved state.

The corrected READ plugins declare explicit `connector-http`, `managedUrlField`
and `secretRef` slots. Current Platform
action compilation requires `${MANAGED_...}` endpoint references, not arbitrary
literal URLs in install configuration. The connector must receive those reviewed
endpoint values and deployment-scoped credential references through trusted
provisioning before apply. No fixture creates an endpoint, resolves a secret or
provisions an adapter. Use the existing reviewed external-integration profile,
offer bootstrap, endpoint approval and credential binding. Offer-less manual
canaries do not receive this projection. The mounted runbook describes the
normal path; do not modify generated routing or inject Coolify environment values.

Generic/Acme hosted proofs use the existing Supabase identity broker with explicit
reviewed server grants; see runbook section 3.1 and the `*-identity-profile.json`
fixtures. Configure this policy before Engage creates its managed identity
projection. Public browser auth configuration contains only the provider's
publishable key and sign-in settings, never private scopes or the connector key.
These isolated profiles expose only approved guides/fictional Acme observations
to accepted signed-in users; they are not private-account membership enforcement.
The native invoker never augments scopes, and anonymous bootstrap gains none of
the specialist/vector/tool grants. Internal case/consent-based support identity
remains a separate path.

LoomAI's adapter additionally verifies the active internal support binding,
original requester, current membership, selected-resource ownership and current
diagnostic consent. Acme must supply its own reviewed identity/resource adapter.
The examples do not imply that generic Platform configuration creates one.

`requiredScopes` constrain invocation; neither the template nor its compiler may
add scopes to the caller. Anonymous guide access does not authorize protected
READ checks. Never pass customer/resource IDs as action parameters to choose
authority. Empty `params` is intentional for these adapter-owned targets.

## Knowledge Ownership

Use a distinct owned storage binding and target-specific prefix for each
installation. Upload only the chosen variant's approved documents. The data
plugin uses `publicationStatus=PUBLIC_APPROVED` because the initial collection is
safe for anonymous guide access. Private account data, transcripts, cases,
diagnostic responses and private handoffs must never enter this collection.

`fixtures/knowledge-sets.json` lists source categories and publication metadata.
The server derives `originalFilename` from the object; do not submit that reserved
metadata field. Supply only approved source metadata such as `sourceCategory`.
Preview, explicitly approve initial indexing, reconcile completion and verify
retrieval. A plugin install does not upload or index these files automatically.
Removing the index never deletes customer source objects.

## Still Requires Hosted Evidence

Seed through the existing Marketplace API, provision the approved runtime,
connect real owned storage and adapters, index the selected guides, configure
identity, then prove both installations and the Desk roundtrip. Use
`fixtures/quality-scenarios.json`; record actual timestamps, release IDs,
observations and failures. Do not treat this source package as completion of
010.54's production dogfooding phase.
