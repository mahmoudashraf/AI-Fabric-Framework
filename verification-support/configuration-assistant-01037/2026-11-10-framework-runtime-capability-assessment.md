# Configuration Assistant: Framework And Runtime Capability Assessment

Date: 10 November 2026. Investigation only; no application implementation or deployment.

**Later owner clarification, same date:** assistant changes may be saved directly
to authorized editable drafts through existing Platform services, without
per-turn acceptance. Review/edit/undo remains available before explicit user
publication. This supersedes the earlier proposal-only/manual-save design, not
the observed source capabilities or probe results. Draft editing remains
unimplemented. The private inference runtime still needs no save credentials.

**Latest clarification:** requested AI changes may replace earlier saved or
unsaved manual values in that scope. Use AI work first, manual refinement
afterward; no manual-conflict approval or new universal edit-version system is
required. Preserve unrelated configuration, use existing resource transactions
and version checks, and prevent cancelled/superseded turns from writing. This
supersedes the earlier manual-edit-preservation design below where applicable.

## Verdict

AI Fabric **0.8.46** provides the generation, structured JSON, history and optional specialist primitives needed by 010.37. No framework change is established as necessary. The assistant is nevertheless **not implemented**: its private runtime route, Platform authoring workflow and editor adapters still need building and hosted qualification.

Use a bounded authoring inference path with registered task contracts, a typed proposal and the existing editors/save/release APIs. **Specialists are not required. Several proposed action definitions can be returned in one JSON envelope.** This is different from executing several business actions.

## Evidence And Limits

- Platform/runtime inspected at `171010aed53d305ba7643ea8b7d076eb1aeba641`.
- Framework source inspected through immutable tag `ai-fabric-framework-v0.8.46`, commit `218730e5b18343576655fbd7108d63b4135f0cfc`. The sibling working tree is an older dirty checkout; it was neither modified nor treated as released source.
- The local packaged runtime contains 20 framework JARs at 0.8.46. **17 offline binary-contract probes passed on Java 21 against these JARs.** They do not compile framework source from that dirty checkout.
- Provider replies are explicit fixtures. No actual inference, customer API call, publication, deployment, indexing or editor adoption occurred. These checks prove contracts, not real-model quality, a fresh empty-cache Central build, or hosted assistant readiness.
- Tagged compound-action and specialist-chain tests were inspected, not rerun. No new framework-wide release-gate claim is made.

Reproduce from the repository root:

```sh
node verification-support/configuration-assistant-01037/run-framework-contract-probe.mjs
```

An optional argument selects another packaged runtime JAR. The runner checks its framework dependency versions, extracts libraries temporarily, compiles the probe with `--release 21 -proc:none`, executes it and removes temporary files. No service is started.

## Multiple Actions: Three Different Capabilities

1. **Recommend existing capabilities:** return a bounded list from a caller-authorized catalogue. The user chooses; no installation/execution occurs. Needs Platform context and UI, not specialists.
2. **Author new action definitions:** generate `changes[]` for search, detail and enquiry plus dependent routes. These are configuration data. Platform validates, preserves other configuration and may save the authorized draft; the user can review/edit/undo before publication. No framework single-action limit prevents this. Keep one selected action form for editing, with optional bounded multi-action review from selected API operations.
3. **Select/execute registered actions:** the framework already has `MultiIntentResponse.intents[]`, compound handling and bounded read-action planning. It still requires real handlers, permissions, valid execution inputs, budgets and declared confirmation. It does not register invented actions or provide an atomic cross-system transaction.

`IntentHandlingStep.handleCompoundIntents` permits one terminal action, suppresses siblings for it and rejects multiple terminal actions. Ordinary compound results may be partial. Multi-action support must not be presented as unrestricted, all-or-nothing execution.

## Scenario Coverage

This covers every 010.37 section 3 area, the documented 20-case quality set and related lifecycle/security failures. **Supported primitive is not the same as implemented authoring.** All assistant-specific import/proposal/adoption paths below remain product work.

### Documentation And Actions

**S01 - OpenAPI 3.0 read, OpenAPI 3.1 nested body, Swagger 2 conversion.** Generation and JSON output are supported. Platform needs maintained parsing/conversion, local-reference handling, source anchors, operation selection and conversion into the real supported action schema. A runtime Swagger page is not a customer API importer. No framework release is needed.

**S02 - Prose, examples, cURL, PDF and conflicting documentation.** Bounded text is supported inference input. Platform must extract/redact/reference the source and show contradictions or missing questions. At this tag, the Spring AI OpenAI FILE_URL path accepts selected images, not arbitrary PDF ingestion: use the planned PDF-to-text importer. Imported API documents are not automatically customer answer knowledge.

**S03 - Public documentation URL and very large specifications.** Fetching, SSRF/redirect checks, byte/time bounds and operation narrowing are new Platform work, not unrestricted model tools. A large API may require explicit staged authoring; do not promise it all fits one context.

**S04 - Suggest several relevant operations.** Supported as a structured array. Platform must supply the parsed operation catalogue and receive actual source-operation references. Use LLM interpretation plus user selection, not keyword-based API classification or automatic installation of the entire API.

**S05 - Add one action and refine it over several turns.** Supported generation/history primitives. New work: scoped sessions, confirmed-answer reuse, typed candidate, complete action-definition form and editor adoption. `NEEDS_INPUT` can preserve a useful partial proposal without inventing executable bindings.

**S06 - Create search, detail and enquiry together.** Supported; the binary probe retains three proposed definitions in one response. New work: bounded batch review, consistent dependent routes and preserved catalogue merging. A separate specialist or model call per endpoint is unnecessary.

**S07 - Revise, rename or remove an existing action.** The framework can carry the proposal; Platform owns stable identities and dependent references. New work: explicit Add/Change/Rename/Remove diffs and preservation of unrelated/unknown-but-supported fields. Requested values may replace manual values without conflict approval. Do not replace the catalogue with only the proposed action or use broad text replacement to rename references.

**S08 - Nested/list/required parameters, trusted IDs, optional grounding.** Existing action contracts support typed parameters and internal/trusted resolution. The form/import adapter must map only supported schema constructs and expose gaps. READ actions do not inherently require a vector space. Credentials and hidden targets are selected through trusted bindings, never invented by the model.

**S09 - Write action and confirmation.** Existing declarations and confirmation support this. Authoring prepares the contract and access policy, not its execution. A POST can be read-only and a GET can have side effects: declared semantics/LLM interpretation matter. Tests of a write endpoint require separate explicit authorization; generation never creates the business enquiry itself.

### Connections, Data And Knowledge

**S10 - HTTP action route and authentication.** Existing connector/routing contracts are reusable. New work: endpoint/path/query/body/response proposals and secure binding selection. A documented authentication scheme is not proof an adapter supports it. Verify the specific route adapter; private Data forms currently expose API-key and form-token-exchange paths, not arbitrary OAuth/mTLS.

**S11 - HTTP JSON Data plugin, pagination and deletion.** Existing private definitions/check/version/install are reusable. New work: ID/content/metadata/account mapping and supported pagination/sync settings from actual documents/examples. Missing identity, tenant ownership or complete-snapshot semantics remains visible. Generated JSON does not implement a new protocol or justify a customer business database.

**S12 - S3 documents and indexing policies.** Existing hosted/BYO storage and approval/indexing flows are reusable. New work: source-policy proposal and authorized binding/prefix selection. No provisioning, upload, approval or indexing during inference. Production reindexes approved source files; it does not copy staging vectors.

**S13 - Knowledge collections and retrieval configuration.** Existing entity/source/search contracts are reusable. New work: catalogue-backed field/source proposals. No source access inferred from text, automatically indexed API docs or implicit search-all.

### Other Editors And Deployment Types

**S14 - Tone, breadth, length, next steps and Custom prompts.** Existing managed profiles and prompt compiler support the candidate. New work: intent-to-profile/config adapter, readable preview and exact Advanced diff. Broader advice grants neither web access nor business-fact invention; no phrase rewriting.

**S15 - Models, embeddings and vector service.** Existing configuration/catalogue validate real choices. New work: safe available-service context, supported fields and explanation of actual reindex/compatibility requirements. Do not guess model availability, credentials or dimensions, change funding or provision infrastructure. OpenAI output-option mapping was probed; other providers/models still require separate qualification.

**S16 - Engage branding, starters, tool groups/rail and renderer.** Existing app configuration and real preview are reusable. New work: app-scoped proposal/adoption adapter. No generated executable JavaScript. Active app updates currently require explicit disable/edit/activate; the assistant cannot silently disable the app. Always send its known `rowVersion`.

**S17 - Embed, Max, standalone, domains and auth.** Existing installation/domain/readiness operations remain separate. New work: supported configuration and actionable guidance. Accepting a proposal does not verify DNS, update identity-provider redirects, reassign a deployment or grant runtime origins.

**S18 - Human handover/contact fields and review.** Existing runtime/Desk capabilities can be configured through their real contracts. New work: safe settings/binding proposals and links to actual assignments/review controls. The assistant receives no operator, invitation, assignment or approval authority. Support chat is not automatically the authoring transport.

**S19 - Configure the customer's specialists and chains.** Framework supports typed specialists and sequential/parallel chains. Platform supports its declared behavior catalogue/bundles and conversational assistance areas/resolutions. The assistant must use those supported contracts and registered refs, not invent workers or arbitrary executable nodes. Configuring these capabilities does not require the assistant itself to run as a specialist team.

**S20 - Fixed plans, triggers, schedules and delivery.** Reuse supported event/plan behavior settings. Framework `PlanExecutionRequest` rejects `INTERACTIVE`: fixed plans cannot directly host the conversational authoring flow. Specialist chains are the optional interactive coordination primitive. New unsupported worker/delivery contracts must be disclosed, not replaced by plausible YAML.

**S21 - Kit coordination across Brain, Data plugin and Engage.** Existing resource workflows are reusable. New work: linked setup tasks and separate resource-scoped proposals/saves. No distributed atomic transaction, duplicate Kit creator or hidden deployment engine. Preserve developer staging-only policy.

### Adoption, Failure And Authority

**S22 - Direct draft edits, optional preview and manual edits.** New work: save dependency-consistent task-scoped changes through existing services and show the real saved diff/receipt, without per-turn acceptance. Requested AI values may replace earlier saved/unsaved manual values. Pause manual editing during AI work and restore it afterward; preserve unrelated settings. Preview-only material stays unsaved. One action at a time is UX, not an inference limit. Manual editing remains usable if inference is disabled or down.

**S23 - Save ordering or switching resources.** Observed code facts remain: Brain saves replace supplied sections and lack a request edit token; plugins have `expectedRevision`, Engage has `rowVersion`. The latest owner decision removes a universal concurrency redesign as a prerequisite. Load current state and use existing transactions/checks for scoped edits; AI may replace manual values. Sequential editor operation and turn cancellation/supersession keep the flow simple. Model-supplied resource IDs cannot redirect a save.

**S24 - Bad/truncated JSON, unavailable provider and explicit repair.** Released executor exposes typed failure and attempt controls; probes cover them. The adapter must preserve useful state and manual editing, report the error and never silently change model/mode or turn prose into a successful artifact. Optional semantic hints should not become hard execution gates.

**S25 - Cancel, deadline, late result and spending.** New authoring ingress must correlate resource/turn/base version, discard stale results and propagate real accounting/deadline/cancel checks. Existing runtime budget scopes admit chat/suggestions/embedding work; an authoring scope is not already established. Reuse/extend that boundary explicitly, not by disguising a generation as chat. UI cancellation does not prove the remote provider stopped spending; qualify that separately.

**S26 - Secrets, prompt injection and cross-tenant attempts.** Use server-owned resource binding, field allowlists, secure references/redaction and no mutation tools. Imported/customer text belongs in user messages: `AIGenerationRequest.context` becomes a system message. Fake approval in documentation cannot save, publish or execute. Trust-boundary cases must all pass; semantic tolerance cannot waive authorization.

**S27 - Save, test, index, publish and apply.** Under the later owner decision, Platform may save an assistant draft edit through the normal service without manual acceptance. Review/edit/undo stays available before publication. Tests, indexing, publish and apply remain separate explicit operations; draft authority grants no automatic release. No duplicate save/release engine or framework release is needed.

## Specialist Decision

**Not needed for the initial assistant.** One task-specific structured call can select operations, ask questions and propose related changes. Platform owns retained answers/documents and workflow state. Formal parsing/compilation is deterministic contract work, not another LLM worker.

Specialists become useful for genuinely independent tasks with distinct data/tool scopes, such as API mapping versus knowledge design. The released framework can coordinate them, but LoomAI still must register actual schemas/workers and assemble one authorized proposal. A manager plus workers now adds calls, latency and state without solving a demonstrated missing capability.

The current conversational `SPECIALIST_ASSISTANCE` compiler uses support-oriented answer/summary schemas and read-only consultations. It is not a lossless configuration-artifact channel. The generic specialist finalizer defaults to two attempts and its generation request does not apply native-schema hints. Using it later requires deliberate policy/provider qualification; it is not equivalent to the planned one-attempt authoring adapter.

## Confirmed Implementation Constraints

1. **The general JSON extractor is permissive.** It accepts wrappers and keeps the first balanced payload; both were reproduced. The private authoring caller must strictly parse the complete raw response with trailing-token checks before handing it to the helper, or use an equally strict conversion path. This is artifact syntax/integrity checking, not semantic text matching. No framework extractor change is required.
2. **Dynamic maps need a deliberate output mode.** Native-schema hints reach OpenAI options. JSON-object mode retains schema guidance while avoiding strict-provider schema loss of dynamic keys. Both were probed. Neither generic parsing nor provider formatting alone validates a LoomAI candidate or authorizes a save.
3. **History is available; source trust belongs to the adapter.** Native history works. The system-message behavior of `context` was reproduced. Keep documentation/customer-authored configuration outside it.
4. **Several definitions are ordinary JSON data.** The probe preserves three changes, arbitrary parameter/route keys, multiple action-selection DTO items and a specialist schema array. It does not prove model selection quality, action execution or editor preservation.

## Recommended Implementation Order

1. Safe projections, registered proposal schema, strict raw-output gate and integration with existing transactional draft saves. No new cross-editor conflict system.
2. Complete per-action form with Advanced JSON and preserved full-catalogue serialization.
3. One real READ action and route from docs, authorized automatic draft save, saved-diff review/edit/undo and explicit connection test; separately prove preview-only mode.
4. Bounded multi-action proposals and iterative revision, then HTTP Data/S3 adapters.
5. Other editor adapters, the plan's real-provider quality set and hosted customer/staff journeys.

No specialist rollout, new plugin type, public search, framework release or unrelated release matrix is a prerequisite. A reproducible provider/contract defect must be raised before any framework fix.

## Source References

Framework files were inspected at the immutable tag above. Paths start under `ai-infrastructure-module/`; Java package directories are abbreviated only in this index:

- `ai-fabric-core`: `AIGenerationRequest`, `MultiIntentResponse`, `AICoreService`, `StructuredJsonCallSpec`, `DefaultStructuredJsonCallExecutor`, `StructuredJsonExtractor`, `StructuredJsonProviderHints`, `IntentExtractionValidator`, `IntentHandlingStep.handleCompoundIntents`.
- `ai-fabric-provider-spring-ai`: `SpringAiPromptMapper`, `SpringAiModelResolver`, tagged `SpringAiProviderAdapterTest` schema-mode tests.
- `ai-fabric-actions-connector`: `ConnectorActionDefinition` explicitly separates declaration from routing.
- `ai-fabric-execution`: `JsonSchemaSpecialistOutputAdapter`, `SpecialistJsonSchemaValidator`, `DefaultStructuredSpecialistOutputFinalizer`, `PlanExecutionRequest`; tagged `DefaultSpecialistChainGatewayTest` covers two workers, parallel order, a bounded question and terminal handoff.

Current Platform/runtime source:

- [Dependency pin](../../ai-infrastructure-module/pom.xml).
- [Chat request](../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/dto/ChatQueryRequest.java), [response](../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/dto/ChatQueryResponse.java), [controller](../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/ChatRuntimeController.java): no versioned authoring envelope.
- [Budget scopes](../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/budget/RuntimeBudgetTurnScopes.java), [specialist consultation](../../ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/specialist/assistance/SpecialistAssistanceActionContributor.java).
- [Draft service](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java), [update request](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/model/UpdateDeploymentDraftRequest.java), [validation](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDraftValidationService.java).
- [Specialist compiler](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/specialist/SpecialistAssistanceCompiler.java), [behavior catalogue](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/behavior/DeploymentBehaviorCatalogService.java).
- [Private plugin API](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/web/CustomerPluginLibraryController.java), [private plugin service](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/CustomerPluginLibraryService.java).
- [Engage service](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceInstallationService.java), [Engage validation](../../Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/aiworkspace/service/AIWorkspaceConfigurationValidator.java).
- [Actions UI](../../Platfrom/ui/src/pages/ActionsPage.tsx), [Data definitions](../../Platfrom/ui/src/pluginLibrary/creatorDocument.ts), [Data forms](../../Platfrom/ui/src/pluginLibrary/CreatorFields.tsx).
- [Widget exports](../../max-mode-widget/src/entries/react.ts), [chat transport](../../max-mode-widget/src/api/chat.ts): no controlled configuration-authoring surface yet.
