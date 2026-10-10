# 010.32 Compound Information Retrieval And Knowledge-Source Routing Correction Plan

- **Status:** AI Fabric `0.8.20`, Maven Central publication, and the isolated
  Central-only private-runtime build are complete. Staging replacement, live
  corpus, production rollout, and final release gates are in progress.
- **Created:** 2026-10-09
- **Scope:** Generic multi-intent evidence collection, per-intent vector-space
  resolution, exact deployment knowledge-source routing, and Northfield
  dealership adoption
- **Original diagnostic baseline:** AI Fabric `0.8.11`
- **Current hosted baseline:** AI Fabric `0.8.19`
- **Target immutable release:** AI Fabric `0.8.20`
- **First live adopter:** Northfield dealership deployment `dep-f023c863`
- **Compatibility decision:** Greenfield correction. Support only the latest
  Platform, runtime, framework, installation manifest, and experience-pack
  contract. Do not add dual routing or legacy compatibility branches.
- **Release decision:** The compound orchestration correction belongs in the
  public AI Fabric framework. LoomAI must consume the resulting immutable
  framework release; it must not reproduce the fix in private product code.

Related plans:

- [010.26 Auto Trader Dealership First Release And Meeting Demo Plan](010_26_AUTOTRADER_DEALERSHIP_FIRST_RELEASE_AND_MEETING_DEMO_PLAN.md)
- [010.27 Auto Trader Integration Platform Readiness Plan](010_27_AUTOTRADER_INTEGRATION_PLATFORM_READINESS_CHANGE_AND_EVIDENCE_PLAN.md)
- [010.29 Generic Max Mode Injectable Action UI And Dealership Experience Plan](010_29_GENERIC_MAX_MODE_INJECTABLE_ACTION_UI_AND_DEALERSHIP_EXPERIENCE_PLAN.md)
- [010.31 Platform-Hosted AI Workspace Installation Plan](010_31_PLATFORM_HOSTED_AI_WORKSPACE_INSTALLATION_AND_ASSIGNED_RUNTIME_DISCOVERY_PLAN.md)

## 1. Purpose

Correct the live failure in which a compound question such as:

> What electric cars do you have, and what is your delivery policy?

can return current inventory but omit the indexed delivery policy. The correct
result must combine live inventory evidence and approved policy evidence in one
grounded answer, independently of clause order or wording.

This is not an indexing failure. Both knowledge domains are present and each
works when queried alone. The defect is in how routing decisions, source
selection, compound child results, and final response normalization interact.

The solution must remain generic. It must work for any application combining
multiple read actions and knowledge domains, not only vehicles and dealership
policies.

## 2. Executive Decision

The target behavior is **per-request and per-intent automatic retrieval**:

1. The deployment defines which vector spaces and knowledge sources are
   allowed.
2. The LLM proposes the relevant vector space or spaces for each information
   intent.
3. AI Fabric validates that proposal against the server-owned deployment
   allowlist and orchestration policy.
4. The runtime searches only configured sources matching each effective vector
   space.
5. Read-action evidence and RAG evidence are collected as separate obligations.
6. AI Fabric produces one final grounded answer over all successful evidence.

The browser does not choose retrieval spaces. The dealership experience pack
does not force every request to search both inventory and documents. No public
`ALL_ALLOWED` request switch will be introduced.

### 2.1 Who decides now and after the correction

| Stage | Current behavior | Target behavior |
| --- | --- | --- |
| Deployment configuration | Defines an allowlist, sources, and modes | Continues to define the maximum permitted boundary |
| LLM | Recommends a vector space per information intent | Continues to recommend the relevant space or spaces per intent |
| Browser/experience pack | Can send both configured spaces and override the more precise LLM result | Supplies presentation and page context only; has no routing authority |
| AI Fabric | Accepts request hints before retaining the LLM result and later promotes one compound child | Validates each LLM recommendation, preserves every read obligation, and synthesizes all evidence |
| Private runtime | Fans a typed request into unrelated configured sources | Searches only sources matching each validated effective space |

The final effective retrieval decision is therefore made by AI Fabric under
server-owned deployment policy. The LLM recommends; it does not grant access.
The runtime executes the validated decision; it does not independently broaden
it.

The formula is:

```text
available spaces = spaces installed and live in the deployment
allowed spaces   = server-owned orchestration allowlist
suggested spaces = LLM recommendation for each intent
effective spaces = validated suggested spaces intersected with allowed spaces
search sources   = configured sources whose entity type matches an effective space
final answer     = one synthesis over all collected read-action and RAG evidence
```

## 3. Confirmed Live Evidence

### 3.1 Healthy baseline

The live Northfield installation is:

- installation: `awi_pub_456ec9f67744f4d7e231a939e835ac05`;
- deployment: `dep-f023c863`;
- runtime: `https://dep-f023c863.46.224.145.148.sslip.io`;
- inventory vector space: `dealer-vehicle`;
- policy and operations vector space: `document`;
- inventory source: `autotrader-dealership-stock`;
- document source: `dealership-public-document-knowledge`.

Indexing, direct retrieval, anonymous browser bootstrap, conversation ownership,
and the existing single-domain quality scenarios are healthy. Reindexing is not
the first corrective action.

### 3.2 Policy-only queries work

Request `rag-158bbfd1-afee-4a72-ac9a-88366963b24e` returned the correct delivery
policy, including the `GBP 49` delivery charge and requirements. The effective
path was RAG-only.

A document-pinned diagnostic request,
`rag-a3901389-089a-49b7-a53a-a9345679cf08`, also returned the correct policy.
It additionally exposed an independent source-routing defect: the runtime
searched both the document source and the inventory source despite the request
being scoped to `document`.

### 3.3 Inventory-first compound queries fail

Request `rag-1fe3f29e-3de1-4c14-8e79-9353fbefa439` produced two intents. The LLM
initially routed the inventory intent to `dealer-vehicle` and the policy intent
to `document`. The request-level hint then changed both effective scopes to
`dealer-vehicle,document`. The read action succeeded, but the final retrieval
query represented only the inventory obligation and the returned documents
were vehicle records.

The user-shaped request was reproduced as
`rag-911de8c7-b930-4e28-846f-2c891ab52ac2`. It returned inventory and omitted
policy evidence. Repeats `rag-c6ad8eaf-668d-4171-ad2b-ae1ede8246b9` and
`rag-a128a29a-c7a7-4c89-b63e-4586edfc8cf8` showed the same result.

### 3.4 Clause order changes the result

Request `rag-767b5d77-d453-4ff4-b1c3-003cb1b5910a` put policy first. The LLM
emitted one combined intent, its optimized query covered policy and inventory,
and the response correctly contained both policy and cars.

The feature is therefore phrase-order sensitive. That is an orchestration
correctness failure, not an acceptable model-quality variation.

## 4. Root Cause By Layer

### 4.1 Browser request context overrides per-intent routing

The dealership experience pack currently places both configured spaces in
every request as:

```json
{
  "context": {
    "preferredVectorSpaces": ["dealer-vehicle", "document"]
  }
}
```

AI Fabric's `VectorSpaceResolutionStep` currently reads raw request-context
hints before retaining the LLM's per-intent decision. Consequently, a correct
policy-only recommendation can become a broad two-space scope.

This is also the wrong ownership boundary. An anonymous browser may provide
page context and user-visible attachments, but it must not be authoritative for
deployment retrieval policy.

### 4.2 Compound children are executed but one child is promoted

AI Fabric `IntentHandlingStep.handleCompoundIntents` processes child intents
separately and returns a `COMPOUND_HANDLED` wrapper. The current
`OrchestrationResultNormalizer` then chooses one primary child. Its stable
priority places `ACTION_EXECUTED` before `INFORMATION_PROVIDED`.

For an inventory read action plus a policy RAG child, the inventory child can
therefore become the outward response while the successful policy child and its
evidence remain only in nested results or are absent from the normalized
top-level response.

This behavior was designed to keep successful actions from being sunk by soft
child failures. It is not sufficient for multiple successful evidence-bearing
children.

### 4.3 Multi-intent retrieval has no global query hint by design

`RetrievalQueryHintSupport` only applies the response-level retrieval query
hint when there is exactly one retrieval intent. This guard prevents one
intent's optimized query from being incorrectly applied to another intent.

The correction is not to remove the guard and append one global hint to every
child. Each retrieval obligation must preserve its own optimized query and
effective vector space through evidence collection.

### 4.4 Runtime source selection is broader than the requested space

`RuntimeDeploymentSearchSourceRegistry.resolveSearchSources` currently returns
the default private source plus all configured private or eligible shared
sources. It does not first filter them by the request entity type.

`SearchSourceResultSupport.scopedRequest` then replaces the request entity type
with each source's configured entity type. A `document` request can therefore
be rewritten into a `dealer-vehicle` source request instead of excluding that
source.

This explains why the document-only diagnostic listed both source IDs.

### 4.5 Fanout metadata can hide a source mismatch

AI Fabric's current RAG fanout metadata helper writes the queried branch into
the document's `vectorSpace` field. If a source returns a document from another
space, the branch label can overwrite the document's actual source metadata.
Diagnostics may then appear correctly scoped even when source selection was
not.

### 4.6 Existing quality gates do not cover the failure shape

The current live gate proves stock-only, detail, comparison, document-only,
attachments, and governed writes. It does not make mixed inventory-plus-policy
queries in both clause orders a mandatory repeated invariant.

### 4.7 Prompting is not the primary defect

The deployment prompt already tells the runtime to use document evidence for
policies and to satisfy mixed requests. One wording order succeeds. Prompt
refinement may improve extraction consistency after the architecture is fixed,
but a prompt-only change would leave compound normalization and source fanout
incorrect.

## 5. Ownership Boundaries

| Concern | Owner | Decision |
| --- | --- | --- |
| Intent extraction and per-intent source recommendation | AI Fabric | LLM-driven, validated by framework policy |
| Compound read-evidence collection and final synthesis | AI Fabric | Public reusable framework behavior |
| Allowed vector spaces | LoomAI deployment control plane | Compiled server-side from installed deployment capabilities |
| Mapping a requested space to configured sources | LoomAI private runtime | Exact entity-type/source match, fail closed on mismatch |
| Knowledge source definitions and health | LoomAI Platform/runtime | Deployment-local and tenant-scoped |
| Browser installation and presentation | AI Workspace and experience pack | No retrieval authority |
| Dealership policy wording and domain guidance | Dealership deployment profile | Prompt/content concern only |
| Live release and assignment | LoomAI Platform | Latest immutable runtime/framework only |

## 6. Target Retrieval Contract

### 6.1 Server-owned boundaries

The Platform must continue compiling the runtime allowlist from reviewed
deployment primitives, including entity configuration, knowledge sources, and
datasets. The existing environment contract is:

```text
LOOMAI_RUNTIME_RETRIEVAL_VECTOR_SPACES_ALLOWLIST
```

This allowlist is the security and capability boundary. It is not a directive
to search every listed space on every request.

### 6.2 Per-intent recommendation

For each information intent, the LLM may recommend one or more vector spaces.
AI Fabric must normalize those names and intersect them with the server-owned
allowlist.

Examples:

| Intent | LLM recommendation | Effective retrieval |
| --- | --- | --- |
| "What is your delivery policy?" | `document` | `document` |
| "Show electric cars" | `dealer-vehicle` | `dealer-vehicle` or read action evidence |
| "Cars and delivery policy" split into two intents | one space per intent | two independent evidence obligations |
| Broad comparison across approved domains | multiple allowed spaces | bounded fanout across those spaces |
| Disallowed or unknown space | invalid | reject, clarify, or policy fallback; never search it |

### 6.3 Fallback behavior

The fallback must remain controlled by orchestration policy:

1. If exactly one space is allowed, it may be selected deterministically.
2. If several spaces are allowed and no valid recommendation exists, honor the
   existing `vectorSpaceSelectionRequired` behavior.
3. If selection is required, return clarification instead of silently
   broadening.
4. If policy explicitly permits bounded fanout, use only the allowed spaces and
   record that fallback in diagnostics.
5. Do not add a browser-controlled `ALL_ALLOWED` flag.

### 6.4 Trusted hints

Trusted server integrations still need an exact-scope mechanism. Preserve the
canonical server metadata keys such as `RAG_PREFERRED_VECTOR_SPACES`, but make
their provenance explicit.

- Raw browser `requestContext.preferredVectorSpaces`, `vectorSpace`, and
  `entityType` are untrusted and must not override an LLM decision.
- A runtime may promote a hint to the canonical trusted metadata only after
  authenticating and authorizing the caller or deriving it from server-owned
  deployment configuration.
- Trusted hints are still intersected with the allowlist.
- Diagnostics identify `LLM`, `TRUSTED_SERVER_HINT`, or `POLICY_FALLBACK` as
  the effective routing source.

This preserves exact trusted integrations without letting an anonymous page
select another customer's source or broaden retrieval.

### 6.5 Curated-module compatibility

The correction is compatible with Platform curated modules and AI Fabric
curated packs. The two layers have complementary responsibilities:

- a Platform curated module seeds the deployment-owned managed prompt bundle;
- its mapped AI Fabric curated pack supplies transparent prompt, profile, and
  mode defaults;
- the generic AI Fabric pipeline still performs per-intent routing, policy
  validation, evidence collection, confirmation, and final normalization;
- the private runtime still performs exact source matching and tenant-scoped
  execution.

A curated module may improve intent extraction vocabulary, explain how to
separate mixed information obligations, select suitable mode defaults, and set
policy defaults such as `vector-space-selection-required` or RAG cooperation.
It must not hard-code product-domain routing in core, expand the deployment
allowlist, trust browser vector-space fields, bypass action governance, or
replace compound evidence aggregation.

Therefore every current and future curated module inherits the generic
correction automatically after its deployment uses the corrected framework
release. A dealership-oriented curated module may later package reusable
automotive prompts, but it is not required to make this fix correct and must
not become the place where the architectural defect is hidden.

### 6.6 Context influence without context routing authority

Request context remains useful as an input to retrieval planning. The
distinction is between semantic evidence context and internal storage routing.

Browser context may carry:

- current or pinned target references;
- page and attachment references;
- an explicit user-selected semantic scope such as `AUTO`, `CURRENT_TARGET`,
  or `ATTACHMENTS_ONLY`;
- locale, channel, and other descriptive request facts.

It must not carry authoritative vector-space names, source IDs, tenant filters,
or provider handles. An explicit user scope may narrow evidence inside the
deployment boundary, but it cannot broaden access.

AI Fabric should derive an internal, typed retrieval plan from the query,
conversation state, targets, attachments, curated prompt guidance, and the
server-provided knowledge-space catalog. For example:

```json
{
  "intentId": "intent-2",
  "kind": "RAG",
  "effectiveVectorSpaces": ["document"],
  "routingSource": "LLM",
  "evidenceScope": "AUTO"
}
```

This internal plan is validated against deployment policy before execution and
is not accepted directly from an anonymous browser. A genuinely trusted
server-to-server caller may send a separately authenticated retrieval
directive, which is still intersected with the same allowlist.

This hybrid model is preferred over either extreme: removing context entirely
would lose valuable page and target signals, while letting public context name
storage routes would couple clients to infrastructure and weaken routing and
security guarantees.

## 7. Target Compound Evidence Flow

### 7.1 Evidence obligations

AI Fabric must represent each read requirement as an evidence obligation:

- RAG retrieval for an information intent;
- a direct action whose metadata marks it `READ` and grounding-eligible;
- an existing trusted target lookup;
- a combination of the above under the configured RAG cooperation mode.

An `ACTION` intent is not automatically a write. A grounding-eligible read
action such as inventory search is read evidence and may be combined safely
with RAG evidence.

### 7.2 Two-phase handling

For a compound read request:

```text
Original user query
  -> extract one or more intents
  -> resolve one evidence plan per intent
  -> validate action and vector-space boundaries
  -> execute each read obligation exactly once
  -> collect bounded action evidence and RAG documents
  -> merge and deduplicate provenance
  -> perform one final generation over the original query and all evidence
  -> return one answer plus all used sources/documents/actions
```

The preferred implementation separates evidence collection from outward answer
generation. It must not generate a full child answer only to discard it and
then execute the same action or retrieval again.

### 7.3 Per-intent query preservation

Each retrieval intent must retain:

- its original intent text;
- its own optimized retrieval query;
- its LLM-suggested vector spaces;
- its validated effective vector spaces;
- the exact source IDs searched;
- the documents returned and selected for context.

Do not apply one response-level query hint to several unrelated retrieval
intents.

### 7.4 Final response semantics

For successful read-only compound requests, the top-level response must expose:

- one synthesized answer that addresses every evidence-backed clause;
- all read actions executed, with each execution represented once;
- merged and deduplicated `sources` and `documents`;
- per-intent routing and evidence diagnostics in debug mode;
- child results retained for audit without making clients reconstruct the
  answer from them.

If one obligation returns no sufficient evidence, the final answer must say
which part could not be grounded. It must not silently omit that clause or
claim a source was absent without attempting the selected source.

### 7.5 Pending and write behavior

This correction must not weaken governed action semantics.

- A write action still follows allowlist, parameter provenance, validation,
  confirmation, and execution policy.
- A pending confirmation or clarification remains top-level and does not
  execute the write.
- The read-evidence finalizer must not merge write receipts into a fabricated
  informational answer.
- A hard access, tenant, deployment, or source-boundary failure remains
  fail-closed.
- Existing one-at-a-time confirmation handling remains until a separate,
  reviewed compound-write design exists.

## 8. Exact Knowledge-Source Routing

### 8.1 Matching rule

For a typed retrieval request, the runtime must resolve only sources whose
canonical `entityType` matches the requested vector space.

```text
requested entityType=document
  -> search all healthy configured sources with entityType=document
  -> do not search dealer-vehicle sources
```

Multiple sources for the same entity type are valid and should be searched
under existing budgets. Sources with a different type are not fallback
candidates.

### 8.2 Untyped and missing-source behavior

- A synthesized default source may inherit the already validated request
  entity type.
- A configured source with no type must not match a typed request.
- A request reaching the registry without a resolved type follows the explicit
  orchestration fallback policy; the registry must not independently search
  every source.
- If no configured source matches an effective space, return a stable
  `NO_MATCHING_KNOWLEDGE_SOURCE` diagnostic and no documents. Do not silently
  broaden to another entity type.

### 8.3 Request scoping

`SearchSourceResultSupport.scopedRequest` must no longer turn a request for one
entity type into a request for another source type. It should assert equality
after normalization and reject mismatches before provider execution.

Existing deployment, tenant, handle reference, active-document-version, and
shared-index security filters remain mandatory.

### 8.4 Provenance metadata

Preserve the source document's actual metadata and add query-path metadata
instead of overwriting it:

```json
{
  "vectorSpace": "document",
  "queriedVectorSpace": "document",
  "knowledgeSourceId": "dealership-public-document-knowledge"
}
```

If actual and queried spaces differ, drop the hit and emit a safe mismatch
diagnostic. Debug output must make the mismatch visible without exposing
credentials, private filters, or document content beyond normal response
policy.

## 9. Workstream A: AI Fabric Framework

The authoritative checkout is:

```text
/Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework
```

Required changes:

1. Introduce a generic compound read-evidence plan/finalization path.
2. Refactor compound information and grounding-eligible read actions to collect
   evidence before one outward generation.
3. Preserve each retrieval intent's optimized query and routing decision.
4. Aggregate top-level actions, sources, documents, and diagnostics instead of
   promoting one successful child.
5. Retain existing hard-error, pending confirmation, and clarification rules.
6. Stop consuming raw request-context vector-space fields as authoritative
   hints. Consume only provenance-marked trusted server hints.
7. Preserve actual document vector-space metadata and add
   `queriedVectorSpace`.
8. Add stable partial-evidence behavior and diagnostics.
9. Prove the behavior under the `default`, `commerce`, and `support` curated
   packs without adding pack-name branches in core.
10. Publish the correction as the next immutable framework patch after
   `0.8.11` with release notes describing compound retrieval behavior.

Primary framework locations to inspect:

- `ai-fabric-core/.../pipeline/steps/IntentHandlingStep.java`
- `ai-fabric-core/.../OrchestrationResultNormalizer.java`
- `ai-fabric-core/.../pipeline/steps/VectorSpaceResolutionStep.java`
- `ai-fabric-core/.../pipeline/steps/RetrievalQueryHintSupport.java`
- `ai-fabric-core/.../pipeline/steps/RagContextSupport.java`

The exact class decomposition may change during implementation, but the public
behavior and ownership above are fixed.

## 10. Workstream B: Private Runtime

Required changes in `ai-fabric-runtime`:

1. Filter configured search sources by normalized request entity type before
   constructing adapters.
2. Permit several sources only when they match that same type.
3. Reject source/request type mismatches instead of replacing the request type.
4. Preserve all existing trusted boundary filters and source-handle behavior.
5. Return stable no-match and mismatch diagnostics.
6. Pass only authenticated, server-derived routing hints into AI Fabric's
   trusted metadata contract.
7. Add source-routing health counters by requested space, matched source, empty
   result, mismatch, and degraded search.

Primary runtime locations:

- `RuntimeDeploymentSearchSourceRegistry.java`
- `SearchSourceResultSupport.java`
- runtime chat/request context construction
- search-source health and diagnostics endpoints

This source-selection fix can be developed in parallel with the framework
change, but the hosted release must consume the corrected framework before the
new behavior is declared ready.

## 11. Workstream C: Platform Control Plane

Keep the existing server-side allowlist compiler as the authority. Extend its
validation and readiness evidence:

1. Every active knowledge source used for retrieval must declare a canonical
   entity type.
2. Each source entity type must be present in the deployment's compiled
   allowlist.
3. Several sources may share an entity type; duplicate source IDs or ambiguous
   untyped sources are invalid.
4. Release readiness must prove source health for every required template
   vector space.
5. Assignment and installation activation must continue to require one
   verified current deployment/release.
6. The Platform remains a control plane and asset source. It does not proxy
   per-message retrieval.

No new user-facing "search all sources" control is required for this fix. If a
future product needs a deployment-wide fallback policy, it must be a typed
orchestration-profile setting, not a workspace or browser request flag.

## 12. Workstream D: AI Workspace And Experience Pack

Remove duplicate retrieval authority from the presentation layer:

1. Remove `knowledge.retrievalVectorSpaces` from the AI Workspace installation
   configuration and dealership experience-pack contract.
2. Remove `preferredVectorSpaces` injection from dealership request context.
3. Keep the request `context` object for page context, attachment/target
   references, locale/channel data, installation identity, assignment revision,
   and other non-authoritative request metadata.
4. Sanitize host-provided `config.requestContext` against reserved routing
   fields so a spread object cannot reintroduce `preferredVectorSpaces`,
   `preferred_vector_spaces`, `vectorSpace`, `vector_space`, `entityType`, or
   `entity_type`. Unknown routing-like fields must not be promoted to trusted
   orchestration metadata.
5. Remove stale defaults using `dealership-document`.
6. Keep `knowledge.inventoryVectorSpace` only where it tags a visible vehicle
   page attachment or trusted target; it must not broaden retrieval.
7. Keep UI tool groups, current-page attachments, presentation renderers, and
   anonymous direct runtime transport unchanged.
8. Update installation validation, generated manifests, Console forms, types,
   external guides, browser smoke tests, and quality scripts together.
9. Change quality assertions from "the browser sent both spaces" to "debug
   evidence shows the correct effective space for every intent."

The target browser request still has a context object, for example:

```json
{
  "context": {
    "dealershipId": "northfield-demo",
    "sourceMode": "DEALERSHIP_INVENTORY",
    "workspaceInstallationId": "awi_pub_...",
    "assignmentRevision": "sha256:..."
  }
}
```

These values are descriptive inputs. The runtime validates any value used for
authorization, target ownership, tenant isolation, or deployment routing
against server-owned state.

The dealership demo backend still exposes `retrievalVectorSpaces` in its staff
integration status. Confirm whether any current operator flow consumes that
field. If it is now only a remnant of the retired backend descriptor path,
delete the property, response field, environment setting, and tests rather
than retaining dead configuration.

## 13. Workstream E: Dealership Deployment Guidance

After the generic correction is proven:

1. Keep the deployment prompt's explicit distinction between current inventory
   and dealership policy/operations evidence.
2. Add a concise instruction that a mixed question creates multiple evidence
   obligations and no clause may be silently omitted.
3. Tell generation not to claim policy evidence is missing unless the policy
   source was actually attempted and returned insufficient evidence.
4. Do not add phrase matching, policy keywords, vehicle keywords, or
   dealership-specific router code.
5. Do not increase top-k merely to mask incorrect source selection.
6. Do not switch all traffic to forced parallel or all-source retrieval as a
   workaround.

Prompt changes are final tuning and must be measured independently after the
framework and runtime corrections.

## 14. Implementation Sequence

### Phase 0: Preserve the diagnostic baseline

- Record the current runtime/framework release identity.
- Preserve the request IDs in section 3 and sanitized debug evidence.
- Record vector counts and source health for `dealer-vehicle` and `document`.
- Confirm no unintended write is executed by the diagnostic corpus.

### Phase 1: Correct and release AI Fabric

- Implement compound evidence collection and final synthesis.
- Implement trusted-hint provenance and per-intent query preservation.
- Correct document/query vector-space metadata.
- Run focused framework tests, the affected module reactor, and an empty-cache
  external-consumer check appropriate to the patch scope.
- Publish one immutable framework release.

### Phase 2: Correct the private runtime

- Upgrade the private runtime to the new framework release.
- Implement exact knowledge-source matching and mismatch rejection.
- Add focused runtime tests and diagnostics.
- Build from published framework artifacts, not a local Maven snapshot.

### Phase 3: Remove browser routing ownership

- Remove workspace and experience-pack retrieval-space configuration.
- Update Platform validation, Console defaults, manifest generation, package
  types, guides, and quality scripts.
- Remove the retired dealership backend field if the usage audit confirms it
  has no current caller.

### Phase 4: Local release gates

- Run framework focused and release-publication gates.
- Run private runtime unit and integration tests.
- Run Platform backend and UI focused suites.
- Run generic AI Workspace and dealership pack package tests.
- Run dealership static/browser quality scripts against a local or controlled
  deployment fixture.

### Phase 5: Hosted staging replacement

- Publish the latest Platform/runtime source.
- Deploy the corrected staging control plane and runtime.
- Reapply Northfield's latest deployment version to create one current
  replacement. No old/new simultaneous deployment is required.
- Verify the assigned consumer and installation resolve to that replacement.
- Verify existing vector counts before considering any reindex. This change
  should not require reindexing because it does not alter indexed records or
  embedding schema.

### Phase 6: Live Northfield canary

- Run the complete matrix in section 16 through the production-site workspace
  using live assignment and anonymous runtime routes.
- Inspect debug diagnostics for routing, source matching, evidence collection,
  and final normalization.
- Confirm the browser sends no retrieval-space authority.
- Confirm no Platform per-message proxy is introduced.

### Phase 7: Production release

- Run the full Platform release gate.
- Replace production with the one latest supported release.
- Re-run AI Workspace, Shopify private adapter, ProdUS, and Northfield
  cross-product canaries appropriate to their active capabilities.
- Update this plan and the private session handoff with immutable release,
  deployment, assignment, and gate evidence.

## 15. Test Matrix

### 15.1 AI Fabric unit and integration tests

| Scenario | Required result |
| --- | --- |
| Two information intents, two spaces | Both retrieval obligations retained and synthesized |
| Read action plus information intent | Action evidence and RAG documents appear once in final answer |
| Same clauses in reverse order | Equivalent evidence coverage |
| One intent recommending two spaces | Bounded validated fanout and one final answer |
| Per-intent optimized queries | Each query remains attached to its own intent |
| One child has no evidence | Explicit partial-evidence statement; successful evidence retained |
| Soft child extraction failure | Does not erase successful evidence |
| Hard access failure | Fail closed |
| Write plus information | No write before required confirmation; current pending semantics retained |
| Untrusted browser hint | Does not override LLM/server policy |
| Trusted server hint | Honored only inside allowlist and reported with provenance |
| Default, commerce, and support curated packs | Same generic compound-evidence semantics; only declared prompt/mode defaults differ |

### 15.2 Runtime source-routing tests

| Request | Configured sources | Expected |
| --- | --- | --- |
| `document` | document + vehicle | document source only |
| `dealer-vehicle` | document + vehicle | vehicle source only |
| `document` | two document sources + vehicle | both document sources only |
| unknown type | document + vehicle | no source; stable diagnostic |
| source reports mismatched hit | matching source | hit dropped; mismatch diagnostic |
| shared source with wrong type | private and shared sources | wrong type never searched |
| trusted deployment filters | matching source | all tenant/deployment filters retained |

### 15.3 Platform and workspace tests

- Compiled deployment allowlist contains `dealer-vehicle,document`.
- Activation fails for an untyped or disallowed required source.
- Public installation manifests contain no retrieval-routing authority.
- Dealership pack sends no `preferredVectorSpaces`.
- Visible page attachments still carry their correct target metadata.
- Anonymous, authenticated-broker, and private-adapter transport contracts are
  unchanged.
- Shopify's private adapter continues to keep assignment and assertions
  server-side.

## 16. Live Quality Corpus

Run every nondeterministic language scenario at least five times in a fresh
conversation and in a continuing conversation where applicable.

1. "What is your delivery policy?"
2. "Show me electric cars under GBP 40,000."
3. "What electric cars do you have, and what is your delivery policy?"
4. "What is your delivery policy, and which electric cars do you have?"
5. "Compare two electric cars and explain your delivery policy."
6. "Where is the 2025 Aster E1 and can it be delivered?"
7. Attach a vehicle page, then ask about that vehicle and the delivery policy.
8. Ask for unavailable inventory plus the delivery policy.
9. Ask a broad question that legitimately needs both approved spaces.
10. Ask for inventory plus a governed write that requires confirmation.
11. Send an unauthorized browser vector-space hint and verify it has no
    authority.
12. Send a trusted server-scoped hint and verify allowlist enforcement.

For the mixed inventory and policy cases, acceptance requires:

- current inventory facts from read-action or `dealer-vehicle` evidence;
- policy facts from `document` evidence;
- both source domains represented in top-level diagnostics;
- one coherent final answer;
- no duplicate action execution;
- no unsupported policy claim;
- no clause-order sensitivity;
- no unintended write.

The existing dealership gate remains mandatory and should be expanded rather
than replaced. The final report must make the mixed-evidence scenarios visible
as first-class pass/fail rows.

## 17. Debug And Observability Contract

Debug mode must expose a bounded record for each intent:

```json
{
  "intentIndex": 1,
  "intentType": "INFORMATION",
  "llmSuggestedVectorSpaces": ["document"],
  "effectiveVectorSpaces": ["document"],
  "routingSource": "LLM",
  "optimizedQuery": "dealership delivery policy charges requirements",
  "searchedSourceIds": ["dealership-public-document-knowledge"],
  "documentsRetrieved": 6,
  "documentsUsed": 3,
  "evidenceStatus": "SUFFICIENT"
}
```

Top-level diagnostics must also report:

- number of evidence obligations;
- number completed, empty, denied, or failed;
- final synthesis performed or skipped;
- action execution IDs for duplicate detection;
- source mismatch and fallback counters;
- total bounded context characters and document limits.

Never expose provider credentials, runtime signing material, private source
handles, raw internal filters, or unrestricted document content in debug data.

## 18. Security And Isolation Requirements

1. Effective spaces must always be a subset of the server-owned allowlist.
2. Anonymous browser context cannot broaden or redirect retrieval.
3. Tenant and deployment filters apply to every source and every fanout branch.
4. A source mismatch fails closed; it is not corrected by silently changing
   the request entity type.
5. Grounding-eligible read actions retain their action allowlist and parameter
   provenance checks.
6. Write actions retain confirmation and trusted-resource protections.
7. Compound aggregation cannot turn denied or private child data into outward
   evidence.
8. Final synthesis receives only sanitized, bounded evidence already approved
   for the response boundary.

## 19. Non-Goals

This plan does not:

- reindex healthy Northfield records by default;
- add a browser `searchAll`, `ALL_ALLOWED`, or vector-space selector;
- make the Platform a per-message retrieval proxy;
- encode dealership keywords or text matching in generic routing;
- merge or batch unrelated write confirmations;
- replace the existing provider, vector database, or document ingestion path;
- add compatibility with older framework/runtime/workspace versions;
- fix the symptom by globally increasing retrieval top-k;
- force every request through every installed knowledge source.

## 20. Risks And Mitigations

| Risk | Mitigation |
| --- | --- |
| Extra LLM calls increase latency | Use evidence collection plus one final generation; do not generate discarded child answers |
| Duplicate read-action execution | Assign obligation/execution IDs and assert exact-once collection |
| Broad evidence exceeds context budget | Apply existing per-action, per-space, document-count, and total-character budgets before synthesis |
| One failed source sinks useful evidence | Distinguish hard boundary failures from empty/soft evidence and state partial coverage explicitly |
| Hidden cross-source leakage | Exact source matching, mismatch rejection, tenant filters, and live negative tests |
| Framework change regresses simple queries | Keep the single-intent path unchanged and cover it with regression tests |
| Removing browser hints affects trusted integrations | Preserve a provenance-marked server hint contract and migrate real trusted callers before release |
| Prompt tuning hides an unresolved defect | Gate framework/runtime diagnostics before accepting prompt improvements |

## 21. Completion Criteria

This plan is complete only when all of the following are true:

- [x] AI Fabric compound read-evidence behavior is implemented, tested,
  released, and available from the public artifact repository.
- [x] The private runtime consumes that exact immutable release.
- [x] Runtime source selection is exact by requested entity type.
- [x] Source/query vector-space metadata is truthful and mismatch-safe.
- [x] Platform deployment validation and readiness enforce typed sources.
- [x] AI Workspace manifests and dealership requests carry no retrieval-space
  authority.
- [x] Obsolete `dealership-document` defaults are removed.
- [x] The legacy dealership backend retrieval-space field is either proven
  necessary and re-owned or deleted completely.
- [x] Focused local framework, runtime, Platform, workspace, and pack gates are
  green.
- [ ] Northfield is replaced with the one latest deployment release and its
  assignment is verified.
- [ ] Every live corpus scenario passes repeatedly in both clause orders.
- [ ] The complete Platform release gate is green.
- [ ] Shopify, ProdUS, and Northfield regression canaries remain green.
- [ ] This document contains final immutable release and hosted evidence.

## 22. Current Progress

| Work item | Status | Evidence |
| --- | --- | --- |
| Reproduce policy-only behavior | Complete | Correct policy response observed |
| Reproduce mixed inventory-first behavior | Complete | Multiple live request IDs in section 3 |
| Test reversed clause order | Complete | Combined successful response observed |
| Confirm indexing health | Complete | Both domains independently retrievable |
| Identify request-hint override | Complete | Debug routing events and pack source inspected |
| Identify compound child promotion | Complete | AI Fabric source inspected |
| Identify cross-source runtime fanout | Complete | Private runtime source inspected and live source IDs confirmed |
| Define target ownership and flow | Complete | Sections 5 through 8 |
| Implement AI Fabric correction | Complete | `0.8.12` source collects compound read evidence, synthesizes once, retains soft-failure sibling evidence, fails closed on hard boundaries, validates routing provenance, and rejects vector-space mismatches. The complete 36-module reactor and exact-release GitHub Framework Build `37974016810` are green. |
| Implement private runtime correction | Complete locally | Runtime maps typed requests only to enabled exact-type sources, reports bounded no-match/mismatch diagnostics, validates source IDs/types at startup, and exposes source-routing readiness; focused runtime tests are green |
| Remove workspace/pack routing authority | Complete locally | Widget sanitization, dealership pack `1.1.0`, Platform UI/validator, and migration `V154` remove browser-owned retrieval spaces while preserving semantic page/target context |
| Validate Platform persistence and UI | Complete locally | Platform backend full suite: 914 tests green; PostgreSQL 16 clean-schema migration through `V154` green; Platform UI, widget, experience pack, public-site static and browser gates green |
| Publish immutable framework release | Complete | Source commit `7b4b0b14c9639abec7c149af707d3e17be06e965`, tag `ai-fabric-framework-v0.8.12`, GitHub release, and Maven Central workflow `37975306956` are complete. The BOM, core, RAG, and execution artifacts return HTTP `200` from Maven Central. An isolated-cache private-runtime `clean verify` passed all 235 tests, and its boot JAR contains 20 AI Fabric libraries exclusively at `0.8.12`, including `ai-fabric-execution-0.8.12.jar`. |
| Correct target-bound read fallback nondeterminism | Complete | AI Fabric `0.8.20` generically converts only eligible target-bound READ actions without trusted hidden attachment targets into information retrieval. Writes, read-write actions, user-visible parameters, owned-resource resolvers, and genuine target-resolution requests remain fail-closed. Focused tests, 739 core tests, 94 integration tests with 6 expected skips, all 26 real-app modules, both external consumers, release guards, and exact-source GitHub Framework Build `38062143556` are green. |
| Publish AI Fabric `0.8.20` | Complete | Public source commit `6f196d0bade3a5b01252823c78bfe2ae2acc5b23`, tag `ai-fabric-framework-v0.8.20`, and the matching GitHub release are published. Maven Central workflow `38063364642` completed successfully; the BOM, core JAR, and execution JAR each return HTTP `200`. |
| Upgrade private Platform source to `0.8.20` | Complete locally, commit pending | The private runtime, Platform compiler/defaults, runtime capability manifest, tests, and public-site release identity are updated. Runtime/connector/relay, 920 Platform backend tests, all five `ai-fabric-product` modules, and the complete Node 22 public-site/widget/dealership-pack verification are green. A clean four-module reactor using a new temporary Maven repository succeeded in 3 minutes 20 seconds. The runtime boot JAR contains 20 AI Fabric libraries, all and only at `0.8.20`; the temporary repository was removed. |
| Run hosted replacement and release gates | Pending | No `0.8.20` hosted deployment has been triggered. Northfield remains verified on version `ver-e1fbbad0` (`v46`), release `rel-6fe9c7ae`, source artifact `dsa-1c7b4bdb`, and AI Fabric `0.8.19`. The next hosted step is to push the private commit, await its exact runtime image workflow, deploy the staging Platform backend, register/promote that image, publish the unchanged Northfield draft as a no-reindex version, and apply it through `dtp-coolify-staging-behavior`. |

At this checkpoint, production and staging behavior has not yet been changed by
the `0.8.20` follow-up. Framework publication and Central-only consumption are
fully verified. Staging firewall `10915120` was restored before pausing: its
canonical rules hash is
`71ee78c836904fbaffa01c6ff87ead74f6186cda554a9bbd5629e9ef7eca90bc`, and
the current operator IP has zero rule matches. Open a new bounded access window
only when hosted rollout resumes. Do not start a second Maven Central release
or recreate the framework tag.
