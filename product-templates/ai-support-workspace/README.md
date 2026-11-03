# AI Support Workspace

## 010.57 Support Experience Source

The source catalogue now also includes Support TEMPLATE `1.1.0`, ticket ACTION
`1.0.0` and optional Desk human-channel INTEGRATION `1.0.1`. They are unqualified
source candidates, not published/live revisions or accepted quality defaults.
See [composition contracts](SUPPORT_EXPERIENCE_CONTRACT.md) and the
[setup and operations guide](SUPPORT_EXPERIENCE_GUIDE.md). The support pack is
`support@1.0.0`; its Technical Support kit uses the ordinary catalogue/bootstrap
and creates configuration only. No ticket history migration is provided.

The template retains customer-owned S3 document DATA and optional specialists.
It does not inherit LoomAI diagnostics, credentials or Console requester roles.
Ticketing and human support are independent. Effective provider capabilities
come from the authenticated requester session, not a template or boolean.
Fresh bootstrap also installs the ticket ACTION using managed target URL and
credential references. Canonical conversations require a newly registered
runtime artifact advertising `standalone-conversation-control`; historical
handover/orchestration-state capabilities alone do not qualify.

The separately reviewed internal `mkp-template-loomai-support-workspace@1.1.0`
source adds diagnostics, Desk and handover pins without changing public defaults.
It uses explicit private-host and `EXTERNAL_SERVICE` profiles, not generic native
ticket install aliases. V229 only adds its family to the existing kit mapping.
See the [internal composition contract](SUPPORT_EXPERIENCE_CONTRACT.md#internal-loomai-instance)
for exact pins, deferred Desk setup and the ordinary API lifecycle. This is not
publication, activation or new live quality evidence.

## Existing Qualified Baseline

Reusable Marketplace support configuration for 010.54. The customer starter is
TEMPLATE `1.0.1` with the required S3 DATA `1.0.0`. The internal LoomAI instance
adds its separately reviewed diagnostics ACTION `1.0.3`; customers do not inherit
LoomAI's case integration or credentials. Its production TEMPLATE is `1.0.0`.
`catalog.json` identifies the current recommended revisions; hosted publication and
rollout receipts are tracked in the dated quality record below. Catalogue
availability is not automatic enrollment in a public self-service offer.

As of 2026-10-31, internal staging and production use 13 real, approved LoomAI
guides from isolated S3 prefixes. The differently configured Acme staging
installation used three fictional guides, its own source binding and an isolated
vector service. Those three assigned releases and active Engage installations
passed normal verification on that date. Acme's disposable test infrastructure
was removed on 2026-11-01; it is not the shared production support service.
The earlier mounted mechanics templates and immutable
ACTION revisions remain historical evidence, not the current storage setup.
See the [execution record](../../doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_54_SUPPORT_WORKSPACE_EXECUTION_RECORD.md)
for exact identities, hosted journeys and known limitations. A new customer's
S3 dependency stays configuration-required until that customer connects it.
This package adds no service, database, execution engine or plugin type.

## Reserved Quality Candidates

The 2026-11-02 follow-up reserves generic `1.0.3` and internal `1.0.2` as
**source-only** candidates, using `fixtures/balanced-support-quality-v2.json`.
They retain generic trust boundaries and the two-action READ-planning maximum,
and add the measured generation/retrieval guidance. No existing published bytes
are replaced and neither is a recommended production default. The current
staging Support release is `rel-abaecf27` on AI Fabric 0.8.44; production remains
`rel-a1a27bce` on 0.8.43. The original nine-turn score improved to 7 pass / 1
partial / 1 fail, but six paraphrases scored 2 / 2 / 2, including an unsupported
negative specialist finding. This is not sufficient default qualification.
See the [dated follow-up](../../verification-support/support-quality-01054/2026-11-02-improvement-cycle.md).

The preceding cycle remains separate:

Customer candidate `1.0.2` and internal candidate `1.0.1` reserve the bounded
configuration tested on LoomAI support. Both are published only on the developer
staging control plane, not accepted as production starter defaults. They are
listed separately in `catalog.json.qualityCandidates`, so the normal publisher
does not silently replace the existing production templates. The candidate stores:

- Editable `gpt-5.4-mini` default, not a lifetime model or funding restriction.
- Executor READ planning: `ITERATIVE`, two iterations,
  `RAG_IF_ACTIONS_INSUFFICIENT`, 12,000 planner characters and 8,000 evidence
  characters / ten items per action.
- RAG context: eight documents / 8,000 characters. The provider's similarity
  threshold, embedding model/dimensions and storage credentials are not replaced.
- An explicit missing-source recovery policy bounded to one authorized document
  domain, plus knowledge overview. It does not override valid model routing,
  search unauthorized sources or silently grant extra domains.
- Generic prompts guide the distinction between documented help and write
  execution, conversational subjects and internal source selection.

Generic assistance remains disabled until the customer configures it. No internal
diagnostic action, Desk agent, source storage or credential is granted to another
company. Initial bootstrap merges declared default leaves and captures them in
the original template snapshot. Later template synchronization does not reset
customer provider or prompt edits. Existing deployments use normal versioning;
they are not silently upgraded. Reset restores their recorded original defaults.

Bootstrap, recorded-original reset and trust separation are verified, but semantic
quality is not yet sufficient to promote these as default configurations for all
new customers. The retained staging sample improved from
5 pass / 3 partial / 1 fail to 6 / 2 / 1. Ordinal follow-up selection and a
specialist projection failure remain; production also produced incorrect optional-
DNS advice in one run, and repeated the wrong-subject follow-up. No original
failure was hidden by a successful retry, text matching or fabricated evidence.
The production LoomAI support deployment retains that earlier tuned configuration;
the staging follow-up is identified above. Production template defaults remain unchanged. See
[quality qualification](../../verification-support/support-quality-01054/README.md).

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

## LoomAI Console Support Experience

For LoomAI's own support installation, **Help & requests** opens the assistant
directly. **Request integration help** opens the intake form above the chat;
**My requests** lists saved conversations separately. Sending a first question
uses the existing durable create/start contract. Visiting the page does not
create a request, submit an AI query or authorize resource diagnostics.

Assigned Support agents' safe names and uploaded profile photos appear above the
conversation. These are authenticated Desk profile projections, not online
presence or a response-time promise. Initials appear when a photo is absent;
account email and permissions are not included in this customer-facing roster.

The same host remains mounted across signed-in customer Console pages, with a
compact dock, minimize/restore and left/right positioning. Navigation does not
reset the chat or its draft. The staff console and public sign-in pages are not
given this customer assistant. These internal Console surfaces do not make
LoomAI's case provider a required dependency of another customer's support
template.

## Contents

- `manifests/ai-support-workspace.1.0.1.manifest.json`: current production `TEMPLATE`; two
  editable documentation areas and one bounded resolution, initially without
  parallel checks. READ slots start
  empty; assistance is explicitly enabled after storage is connected. The
  unversioned filename retains the immutable `1.0.0` snapshot. Candidate `1.0.2`
  adds reserved quality defaults without changing published prior bytes.
- `manifests/support-documents.manifest.json`: normal `DATA`; customer-owned
  S3-compatible documents, deployment-only derived index, explicit approval.
- `manifests/loomai-support-diagnostics.1.0.3.manifest.json`: normal `ACTION` overlay
  for the internal LoomAI support instance; four consented, case-scoped reads
  with explicit model evidence over the parsed action payload. Earlier snapshots
  remain immutable evidence, not the recommended configuration.
- `manifests/loomai-support-workspace.manifest.json`: internal S3-backed LoomAI
  template, with the reviewed diagnostics dependency. It is not a public
  customer default and supplies no credentials. The reusable customer starter
  above remains independent of LoomAI's case service and diagnostic tools.
- `manifests/loomai-support-workspace.1.1.0.manifest.json`: source-only internal
  Support Experience composition; exact ticket/diagnostics/Desk/handover pins,
  no required install defaults, credentials or owned source locations.
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
- [Real LoomAI Support Knowledge Library](SUPPORT_KNOWLEDGE_LIBRARY.md): 13
  source-reviewed customer help guides in `documents/loomai-support/`, with
  maintainer provenance, an exact upload manifest and 18 prepared review questions.
  Packaging alone is not indexing proof; the dated execution record separately
  records the internal staging/production upload, ACTIVE versions and live answers.
- `documents/loomai/` and `documents/acme-cloud/`: the earlier four LoomAI staging
  mechanics guides and three explicitly fictional Acme guides, kept separate
  from the new real support library.
- `fixtures/`: actual API request bodies, source metadata, simulated observations
  and quality scenarios. They are inputs, not fabricated passing evidence.
- `fixtures/service/`: stateless authenticated Acme mock upstream and Dockerfile,
  with two READ endpoints and the three approved fictional text documents.
- [API Runbook](API_RUNBOOK.md): catalog review/publication, bootstrap, configure,
  document indexing, Desk activation and hosted acceptance.
- [Operations](OPERATIONS.md): launch disclosure, queue ownership, the approved
  internal retention target and the limits of correction/removal handling.
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
Workers with declared READ tools use the existing `executor` mode, enabling its
bounded cooperative READ planning. Documentation-only workers use
`navigator_deep`; managers use `resolver`. A worker never gains another area's
tools or WRITE authority, and a documentation question does not force live reads.
Changing this generated configuration requires a new normal publish/apply;
existing immutable versions are not rewritten.

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

Internal diagnostics ACTION `1.0.3` also declares the framework's existing
`llmFacts` mappings. An unconfigured compact object response does not preserve
arbitrary nested status fields. Template/action authors must explicitly project
fields from the unwrapped action payload, not from a duplicated protocol
`data` envelope. The verification probe exercises the actual connector parser
before the facts builder; published `1.0.2` is a wrong-root negative control.
For example:

```json
{
  "llmFacts": {
    "copyFields": ["observedAt", "availability"],
    "lists": [{
      "sourcePath": "resources",
      "target": "statusObservations",
      "maxItems": 8,
      "includeFields": ["resource.id", "availability", "facts.syncState"]
    }]
  }
}
```

This uses the existing ACTION/compiler contract; it adds no query matching,
model answer substitution, new permission or framework fork. See the runbook
for bounded index/readiness projections and the published-JAR facts probe in
`scripts/probe-published-0.8.41-llm-facts.mjs`. Published earlier ACTION snapshots
remain immutable evidence, not the recommended live configuration.

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
