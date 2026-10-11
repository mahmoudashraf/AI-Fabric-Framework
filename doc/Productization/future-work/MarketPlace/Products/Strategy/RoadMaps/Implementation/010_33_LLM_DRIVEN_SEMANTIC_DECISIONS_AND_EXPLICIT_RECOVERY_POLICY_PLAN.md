# 010.33 LLM-Driven Semantic Decisions And Explicit Recovery Policy Plan

- **Status:** Implementation, immutable AI Fabric `0.8.22` publication, Maven
  Central consumption, and local verification are complete. Private hosted
  replacement and live release gates are in progress.
- **Created:** 2026-10-11
- **Scope:** AI Fabric semantic decisions, recovery behavior, evidence
  selection, public examples, and LoomAI Platform adoption
- **Previous hosted baseline:** AI Fabric `0.8.21`
- **Target release:** AI Fabric `0.8.22`, as one current-only immutable release
- **Compatibility decision:** Greenfield correction. LoomAI supports only the
  latest framework/runtime/Platform contract; no legacy compatibility path or
  parallel old/new runtime is required.
- **Primary rule:** Production code must not use deterministic text matching to
  substitute for, override, repair, route around, or conceal an LLM semantic
  decision. Text processing remains valid when matching is itself an explicit
  policy, protocol, retrieval, validation, testing, or presentation contract
  and cannot alter the governed semantic decision.
- **Deferred scope:** The relationship-query module remains audited but is not
  changed, released, or used as a completion gate in this plan.

Related plans:

- [010.32 Compound Information Retrieval And Knowledge-Source Routing Correction Plan](010_32_COMPOUND_INFORMATION_RETRIEVAL_AND_KNOWLEDGE_SOURCE_ROUTING_CORRECTION_PLAN.md)
- [010.31 Platform-Hosted AI Workspace Installation Plan](010_31_PLATFORM_HOSTED_AI_WORKSPACE_INSTALLATION_AND_ASSIGNED_RUNTIME_DISCOVERY_PLAN.md)
- [010.29 Generic Max Mode Injectable Action UI And Dealership Experience Plan](010_29_GENERIC_MAX_MODE_INJECTABLE_ACTION_UI_AND_DEALERSHIP_EXPERIENCE_PLAN.md)

## 1. Why This Plan Exists

The post-`0.8.21` review found several places where deterministic code attempts
to correct, complete, rank, or replace an LLM decision by inspecting the raw
words in a user query or model prompt. The active examples are:

- English phrase matching for conversational references such as "the second
  option";
- substring matching between a query and a vector-space name;
- query-token matching used to reorder documents and choose excerpts for final
  generation;
- query length and punctuation used to infer advanced-RAG need; and
- hidden intent, routing, and retrieval fallbacks that can make a failed or
  incomplete model decision look successful.

These mechanisms are not merely diagnostic when they mutate an intent, select
a source, choose evidence outside the declared retrieval contract, or change a
retrieval mode. They can make one wording appear reliable while equivalent
paraphrases or other languages fail. They also hide whether the LLM contract
itself is incomplete.

This plan does not ban text processing generally. UI animation, presentation
labels, explicit command grammar, configured security rules, retrieval-provider
ranking, test fixtures, and operational message parsing do not replace model
intelligence merely because they inspect text. They are allowed when their
role is declared and they cannot drive or conceal a semantic runtime decision.

The correction is not to demand perfect model behavior. A model may sometimes
be wrong within an explicitly measured and accepted tolerance. The correction
is to improve the structured model contract, validate it against trusted
policy, use bounded model repair when configured, and surface unresolved
failure honestly. Deterministic code may constrain authority; it may not guess
the user's meaning.

## 2. Non-Negotiable Rules

### 2.1 Prohibited production behavior

Production code must not use natural-language text matching as an implicit
replacement for a model decision to decide or infer:

- intent type or intent completeness;
- action selection, action parameters, or confirmation meaning;
- conversational target/reference resolution;
- vector space, knowledge source, specialist, tool, or provider selection;
- whether advanced RAG, fan-out, or another reasoning mode is needed;
- document relevance, semantic evidence ordering, or the relevant excerpt in
  orchestration after a vector/retrieval provider has supplied scores or ranked
  order;
- policy, safety, sentiment, topic, or domain classification; or
- any successful semantic outcome after the governing model decision failed.

The prohibition includes hard-coded keywords, substring checks, natural-
language regular expressions, token overlap, stopword scoring, stemming,
plural guessing, punctuation tests, query length, and phrase-order rules when
they perform one of those semantic substitutions. The same techniques are not
automatically prohibited when they implement an explicitly declared
non-semantic contract.

### 2.2 Required semantic authority

Natural-language semantic decisions come from one of these explicit sources:

1. a validated structured LLM result;
2. trusted structured user/application input, such as an attachment ID, tool
   button ID, pinned target, selected scope, or typed form field; or
3. an explicitly selected retrieval/classification provider whose algorithm
   is part of the declared feature contract.

Configuration supplies capability ceilings, allowlists, budgets, and recovery
permission. It must not silently replace a valid model decision with a static
semantic decision.

### 2.3 Imperfect model output

Model imperfection is expected and must be handled honestly:

- make the prompt, schema, available catalog, descriptions, and examples
  sufficiently complete;
- use provider-supported structured output or JSON schema where available;
- validate syntax, schema, identifiers, authorization, and policy
  deterministically;
- allow a bounded LLM repair/replan call when the configured policy permits it;
- otherwise clarify, return partial coverage explicitly, or fail with a typed
  reason; and
- measure quality over paraphrases, reordered clauses, multilingual prompts,
  and real-provider runs rather than making one phrase pass with code.

An 80-90 percent model-quality result can be an honest product-quality issue to
improve. A deterministic phrase patch that produces a misleading 100 percent
for known test wording is not acceptable.

### 2.4 Recovery and fallback

A recovery path that changes semantic behavior is denied unless a named,
server-owned allow-policy explicitly permits it. Every permitted recovery must
be:

- typed and bounded;
- visible in diagnostics and audit data;
- attributable to the original model decision and configured policy;
- unable to broaden tenant, deployment, source, action, or data authority; and
- testable independently from the primary model path.

There must be no catch-all "best effort" semantic fallback. Security policy may
always deny or constrain a request, but it must not reinterpret that request.

### 2.5 Allowed deterministic behavior

The rule does not prohibit deterministic processing that does not infer
natural-language meaning in place of the LLM, including:

- JSON/YAML/schema parsing and structured-output extraction;
- exact ID, enum, action, vector-space, tenant, deployment, and allowlist
  validation;
- authorization, provenance, confirmation-token, and trusted-resource checks;
- bounds, timeouts, rate limits, context budgets, and deterministic truncation;
- MIME type, URI, hash, secret-reference, path, and protocol validation;
- PII or secret detection based on a formally declared detector contract;
- explicit command grammars that users deliberately invoke as commands;
- provider/vector similarity, declared BM25/hybrid search, or a configured
  reranker as an actual retrieval algorithm; and
- exact policy literals only when they are customer-configured policy
  artifacts, use declared matching semantics, and are not presented as general
  language understanding;
- UI thinking animations, progress copy, labels, icons, title fallbacks, and
  renderer selection that affect presentation only and are not treated as
  backend telemetry, authorization, routing, or audit evidence;
- pack-specific presentation matching that stays inside the pack and does not
  alter the query or runtime request contract;
- deterministic local providers isolated to explicit test/smoke profiles and
  never represented as production AI; and
- exception/report parsing and other operational compatibility logic, while a
  typed contract may still be preferred as ordinary engineering improvement.

An ad hoc token scorer in orchestration is not a retrieval provider. An
exception-message substring is not a typed error contract, but it is not an LLM
semantic violation. A frontend animation may provide illustrative progress;
it must not be exported or consumed as proof that the backend performed a
specific operation.

### 2.6 Classification test

Before classifying any text-processing code as prohibited, answer these
questions:

1. Does it create, replace, repair, or override intent, target, action, source,
   mode, policy classification, or semantic evidence selection?
2. Does it turn a missing/invalid model result into an apparently successful
   result without a named recovery policy?
3. Does it broaden authority or hide the original and effective decision?
4. Is text matching the declared feature itself, such as lexical retrieval,
   an explicit command grammar, configured rule, test double, or presentation?

Questions 1-3 identify governed semantic behavior. Question 4 identifies an
allowed deterministic contract unless it is also being used to do one of
1-3.

### 2.7 Decision matrix

| Classification | Examples | Plan treatment |
| --- | --- | --- |
| Prohibited semantic substitution | Query words selecting a vector space, phrase regex repairing target resolution, query shape selecting advanced RAG, action intent silently rewritten as information, orchestration token matching overriding retrieved evidence | Remove and replace with complete structured model output, validation, bounded model repair, or an honest typed failure/clarification. |
| Allowed only by named policy | Bounded source fan-out, RAG after insufficient action evidence, no-result retrieval broadening, action-to-information replan, provider failover, moderation-provider failover | Keep only when server-owned configuration permits it, authority remains bounded, and original/effective decisions are observable. |
| Allowed deterministic contract | UI thinking/presentation, explicit command grammar, IDs/enums/schema/authorization checks, configured security rules, vector/BM25/hybrid provider ranking, declared reranking, test doubles, deterministic budgets, operational parsing | Keep. It is outside this semantic correction unless it begins to mutate or conceal a governed decision. |

## 3. Audit Method And Scope

The audit inspected current production source in:

- public AI Fabric core, chat-session, relationship-query, governance,
  execution, providers, and public real-app examples;
- private LoomAI runtime/relay and Platform backend/UI;
- the generic `max-mode-widget`; and
- Shopify-specific workspace presentation code.

Inspection does not imply that every finding belongs in the correction scope.
UI, protocol, operations, and relationship findings remain recorded so the
classification decision is auditable, but only semantic substitution and
hidden semantic recovery are active release blockers.

Searches covered query/prompt/message matching, regular expressions, token
overlap, semantic defaults, fallback/coercion paths, provider fallback, and UI
inference. Each result was classified as:

- **semantic violation:** changes meaning or claims meaning from free text;
- **hidden recovery risk:** replaces or masks a model decision without a clear
  allow-policy;
- **structural/policy behavior:** legitimate deterministic validation;
- **declared retrieval behavior:** acceptable only as an explicit algorithm;
- **presentation/protocol behavior:** allowed when it cannot affect backend
  semantics or authority; or
- **operations debt:** brittle string parsing unrelated to user semantics but
  still better replaced by typed contracts.

This is a source audit, not proof that every path is active in every deployed
mode. Implementation must add runtime diagnostics and tests to prove effective
behavior.

## 4. Confirmed Findings

### 4.1 P0 semantic text matching in framework runtime paths

| Area | Current behavior | Why it is wrong | Required replacement |
| --- | --- | --- | --- |
| `ai-fabric-core/.../intent/vectorspace/BoundedFanOutRouter.java` | `tryQueryMentionMatch` compares query substrings and guessed plurals with vector-space names before applying the configured strategy. | Raw wording can select a knowledge domain and bypass the model's missing decision. | Delete query mention matching. A missing space uses bounded LLM repair, then clarification or an explicitly allowed bounded fan-out policy. |
| `ai-fabric-chat-session/.../WorkingSetTargetSeedingStep.java` | English regular expressions recognize ordinals and phrases such as "of those" and then mutate `requiresTargetResolution`. | It fabricates a structured intent flag from one language and known wording. | Depend on structured LLM target fields or trusted attachments/pins. Optionally perform one model repair with a bounded working-set summary. |
| `ai-fabric-core/.../CompoundReadEvidenceSupport.java` | Stopword/token matching reorders retrieved documents and centers excerpts around query-term substring hits. | It changes the evidence sent to generation and therefore changes the answer; it is not diagnostic. | Preserve provider order/scores and indexing-time chunks. If needed, use a configured reranker or structured LLM compressor returning document/chunk IDs. |
| `ai-fabric-core/.../AdvancedRagSupport.java` | Missing `needsAdvancedRAG` can be replaced by query length, word count, and question-mark heuristics. | Text shape replaces a missing semantic model field. | Require structured model output or an explicit server mode. Missing/invalid output follows configured model repair or a visible failure policy. |
| `ai-fabric-governance/.../AIContentFilterService.java` | On AI failure, built-in words such as `hate`, `idiot`, `kill`, and `buy now` become a rule-based semantic moderation result. | It silently substitutes a weak English keyword classifier for failed model/provider analysis. | Use a typed moderation provider/result. Provider failure fails closed or follows a specifically configured moderation recovery provider; do not guess a category from built-in words. |

The audited relationship-query finding is deferred in section 8. It is not a
P0 item, release requirement, or acceptance criterion for this plan.

### 4.2 P0/P1 hidden model-decision replacement and governed recovery

| Area | Current behavior | Required policy |
| --- | --- | --- |
| `IntentExtractionPostProcessor` and legacy `IntentQueryExtractor` | Unknown/unavailable actions and target-bound read actions can be changed into INFORMATION/RAG intents. | First attempt bounded model repair/replan. Any later conversion requires an explicit `UNKNOWN_OR_UNAVAILABLE_ACTION` recovery decision; otherwise preserve the error or clarify. |
| `MultiStepIntentExtractionStrategy` | ACTION without a registered action, and even OUT_OF_SCOPE, can be changed to INFORMATION/RAG. | Remove unconditional bias. Preserve the structured decision or use configured LLM repair. Never report a successful information answer merely because an action decision was inconvenient. |
| `IntentHandlingStep.fallbackMismatchedReadActionToInformation` | A grounding-domain mismatch correctly blocks the selected action, then automatically constructs an INFORMATION intent and runs RAG. | The safety block remains mandatory. Recovery must be one of `DENY_AND_FAIL`, `DENY_AND_LLM_REPLAN`, or explicitly allowed `DENY_AND_RAG`, with the original/effective decisions recorded. |
| `IntentHandlingStep.maybeFallbackReadActionToRag` | Insufficient successful read-action evidence may trigger RAG. | Keep only through the existing explicit cooperation contract such as `RAG_IF_ACTIONS_INSUFFICIENT`. Always expose the attempted action and recovery; diagnostics cannot be optional when output changes. |
| `VectorSpaceResolutionStep` and `InformationVectorSpaceRoutingSupport` | Invalid/missing model spaces can become a singleton allowlist, the full allowlist, KB overview spaces, or deep fan-out. | All semantic broadening must require a named missing/invalid-space policy. Default is model repair then clarification/failure. The allowlist constrains; it does not itself prove relevance. |
| `IntentHandlingStep` advanced-RAG no-result path | No relevant result can broaden retrieval to multiple overview spaces. | Permit only through an explicit no-result recovery policy with strict max spaces, source diagnostics, and no authority expansion. |
| `AIProviderConfig`, `AIProviderManager`, and `AIEmbeddingService` | Generation/embedding provider fallback is enabled by default. | This is allowed operational recovery when a named provider chain, compatible data policy, bounds, and visible transition are configured. It is not semantic text matching, but an implicit cross-provider transition is still not acceptable. |
| `ProgressiveIntentExtractionEngine` | Structured LLM strategies are retried, then a synthetic safe default object is built. The current caller detects failure before consuming it. | Keep bounded model-driven repair, but remove/refactor the synthetic semantic default so it cannot later be mistaken for a successful model result. |

### 4.3 Reviewed AI Workspace behavior that is allowed

These findings do not hide an LLM decision and are not release blockers under
the corrected rule:

| File | Current behavior | Classification |
| --- | --- | --- |
| `max-mode-widget/src/components/Chat/AIThinkingAnimation.tsx` | Selects illustrative thinking steps from message words. | Allowed presentation. It must not mutate the request, claim chain-of-thought, become audit evidence, or be consumed as proof of backend execution. Backend events may improve accuracy later but are not required by this plan. |
| `max-mode-widget/src/actionMessage.ts` and `constants.ts` | Parses the deliberate `Action: ...` convention and maps action names to icons. | Allowed explicit protocol and presentation mapping. Typed metadata can remain a future maintainability improvement. |
| `ShopifyShoppingWorkspace.tsx` | Uses pack-specific quick-action labels and document categories to choose UI presentation. | Allowed inside the Shopify experience pack when it changes presentation only and does not alter runtime routing or authority. |
| `useChatFlow.ts` | Creates a display title when a source title is absent. | Allowed presentation fallback. A neutral label may be preferable, but this is not an AI-semantic blocker. |

No generic AI Workspace rewrite is required by this plan. A separate UI plan
may still introduce server progress events or typed presentation metadata for
accuracy, reuse, or maintainability.

### 4.4 Public examples that teach the wrong production pattern

| Example | Finding | Required replacement |
| --- | --- | --- |
| `behavior-churn-signals` | `BehaviorLocalLlmProvider` counts/matches prompt words and is the missing-property default provider. | Make real AI the default production posture. Isolate deterministic behavior under an unmistakable `smoke`/`test` profile that cannot start as production. |
| `chat-capabilities-demo` | `ChatLocalLlmProvider` is a production component that parses prompt/user language with regex and keywords for intents and confirmation. | Move it to test/smoke source/config or require an explicit smoke-only profile with a production startup guard. |
| Explicit demo search services | Some repositories intentionally implement lexical search over app data. | Keep only when named/documented as lexical search, not as intent understanding or an LLM fallback. Feed it structured query/filter parameters selected by the governed AI path. |

Relationship-query examples are deferred with the relationship module. They
remain audit evidence but do not block this plan's release.

### 4.5 Lower-priority typed-contract debt

These paths are not natural-language orchestration, but they parse exception or
report prose to make operational classifications:

- `DefaultSpecialistChainManifestCompiler` maps validation exception messages
  to issue categories by substring;
- connector/execution code recognizes timeout/provider/URI failure by message;
- LoomAI `ShopifyCompanionReadinessAuditService` parses a controlled Markdown
  line with a regular expression; and
- private relay code maps exception text such as `timeout` to an error code.

They do not justify blocking the semantic correction and are excluded from its
source guard and completion gate. The owning APIs may expose typed error/report
codes through ordinary maintenance work.

### 4.6 Reviewed deterministic behavior that remains valid

The audit also found many matches that are not violations: collection
membership, exact enums/IDs, JSON extraction, schema regex validation, SQL
injection guards, PII detectors, URI/path checks, MIME/content-type checks,
hash validation, test assertions, declared lexical retrieval, explicit command
grammars, UI presentation matching, and isolated deterministic smoke
providers. These remain unless a separate security or correctness review finds
a defect.

### 4.7 Private Platform Thinker semantic classification

The implementation audit found one additional active Platform violation after
the original framework inventory: `ThinkerResolverService` classified issue
category, write risk, and prompt-injection state by matching English phrases in
`userQuestion`. That behavior is removed.

The operator API now requires a typed `semanticDecision` containing outcome,
issue category, risk class, and reason code. Runtime capture derives the same
contract only from typed runtime result kinds, exact error/policy codes, and
grounding evidence flags. Contradictory operator decisions fail with `400`,
failed or non-grounding actions cannot produce a resolved session, and the
server-owned cross-tenant boundary always overrides the submitted decision and
discards submitted evidence. Decision source and reason are persisted in the
planner trace and audit stream. A private source guard protects Thinker,
runtime request-context handling, and AI Workspace request-semantics files.

## 5. Target Semantic Decision Contract

### 5.1 Structured model output

Intent extraction must return a complete typed result per obligation. The
schema should require, where applicable:

- `intentType`;
- `userGoal` and `optimizedQuery`;
- `actionName` from the provided enum or `null`;
- typed action parameters with provenance;
- `requiresRetrieval` and proposed `vectorSpaces`;
- `requiresTargetResolution`, target references, and target cardinality;
- `requiresAdvancedRag`;
- `missingInformation` and `clarificationQuestion`;
- `confidence` or an explicit uncertainty reason; and
- compound obligation IDs so no child disappears during synthesis.

The prompt must include the exact available action/vector-space catalogs,
short capability descriptions, trusted current-target summaries, applicable
policy ceilings, and the required output schema. Examples may guide the model;
production code must not match those example phrases.

### 5.2 Validation boundary

Deterministic validation may:

- reject unknown IDs or enums;
- intersect a proposal with an allowlist;
- enforce action, tenant, deployment, and parameter authority;
- reject a plan that references missing structured constraints;
- enforce bounds and required fields; and
- report validation errors to a repair call.

Validation may not infer the intended replacement. For example, it can reject
an invalid vector space, but it cannot choose a different one because its name
appears in the query.

### 5.3 Bounded model repair

Use the existing progressive extraction foundation, but define repair as a
first-class observable model call:

1. preserve the original structured response;
2. provide only schema/policy validation errors and the permitted catalogs;
3. request a complete replacement structured result;
4. cap attempts and total latency;
5. validate again; and
6. fail or clarify if still invalid.

Repair must not add authority, and it must never execute an action before the
replacement result passes all existing security gates.

## 6. Explicit Recovery Policy

Introduce one typed, server-owned recovery policy resolved from the curated
mode/deployment profile. Do not add unrelated booleans throughout the pipeline.
The policy should cover these decision points:

| Decision point | Strict default | Optional explicitly allowed behavior |
| --- | --- | --- |
| Invalid/incomplete structured extraction | `LLM_REPAIR_THEN_FAIL` | `LLM_REPAIR_THEN_CLARIFY` |
| Missing conversational target decision | `LLM_REPAIR_THEN_CLARIFY` | Reuse trusted pinned target only when the model explicitly requests target resolution |
| Missing vector-space decision | `LLM_REPAIR_THEN_CLARIFY` | `BOUNDED_ALLOWED_FAN_OUT` |
| Invalid vector-space decision | `LLM_REPAIR_THEN_FAIL` | `LLM_REPAIR_THEN_CLARIFY` |
| Unknown/unavailable action | `LLM_REPAIR_THEN_FAIL` | `LLM_REPLAN_TO_INFORMATION` only if explicitly permitted |
| Action/evidence-domain mismatch | `DENY_AND_LLM_REPLAN` | `DENY_AND_ALLOWED_RAG` |
| Successful but insufficient read action | `RETURN_INSUFFICIENT` | Existing `RAG_IF_ACTIONS_INSUFFICIENT` cooperation |
| Advanced retrieval returns no evidence | `RETURN_NO_EVIDENCE` | Bounded allowed-space recovery |
| Primary LLM/embedding provider failure | `FAIL` | Retry a named, data-policy-compatible provider chain |
| Moderation provider failure | `DENY` | Retry a named moderation provider |

The exact public names may change during API review, but the semantics may not
collapse back into implicit defaults.

## 7. Provider-Ranked Evidence Selection Correction

`CompoundReadEvidenceSupport` must stop using query tokens, stopwords, and
substring hits to perform a second hidden semantic ranking or to discover an
excerpt after retrieval.

The replacement order is:

1. When the configured vector/retrieval provider returns similarity scores,
   use those scores and the provider-ranked order.
2. When it returns a documented stable ranked order without scores, preserve
   that order and record that no comparable score was available.
3. Preserve rank within each obligation/result set. Compound-answer fairness
   may interleave bounded result sets, but must not invent a new lexical score.
4. Use indexed chunks and deterministic character/token budgets. Budget
   truncation may remove tail results but must not semantically reorder them.
5. Do not compare or normalize raw scores across providers unless the provider
   contract or a declared normalization strategy states they are comparable.
6. If secondary reranking is required, call a configured reranking provider
   that returns document/chunk IDs with scores or ranked order.
7. If context compression is required, use a structured LLM compressor that
   selects document/chunk IDs and bounded excerpts while retaining citations.
8. If the provider supplies neither a relevance score nor a ranked-order
   guarantee, preserve returned order under a bounded budget or require a
   declared reranker. Do not restore orchestration-side lexical ranking.

Debug data must state `VECTOR_PROVIDER_SCORE`, `PROVIDER_RANKED_ORDER`,
`DECLARED_RERANKER`, `LLM_COMPRESSOR`, or
`UNRANKED_PROVIDER_ORDER_TRUNCATION` as the selection source. It must also
identify the provider, score presence, obligation/result-set boundary, and any
interleaving or truncation applied.

## 8. Deferred Relationship Module

The audit found query-text sanitization and generated fallback-plan behavior in
the relationship-query module. That concern is real, but the module is
explicitly deferred for the current correction:

- do not change relationship-query schemas, prompts, planners, flags, examples,
  or runtime behavior in this plan;
- do not include relationship-query work in the target framework release or
  its acceptance criteria;
- do not let the deferment justify copying the same pattern into active
  modules; and
- preserve the audit evidence for a dedicated relationship-module review when
  that product capability is resumed.

## 9. AI Workspace Classification

AI Workspace thinking steps, action-message conventions, pack-specific labels,
icons, and title fallbacks are presentation/protocol behavior. They are allowed
by this plan even when they use text matching, provided they:

- do not modify the submitted query, mode, action, target, vector space, or
  authorization context;
- do not become runtime diagnostics, release evidence, or audit truth;
- do not expose private model reasoning as chain-of-thought; and
- keep customer/domain presentation inside the relevant experience pack where
  practical.

Server-provided progress events and typed quick-action presentation metadata
remain valid optional improvements. They are not prerequisites for correcting
the runtime semantic paths in this plan.

## 10. Observability Contract

Every semantic decision and permitted recovery must expose bounded diagnostics:

```json
{
  "decision": "VECTOR_SPACE_SELECTION",
  "originalSource": "MODEL",
  "originalValue": ["document"],
  "validationStatus": "REJECTED_NOT_ALLOWED",
  "recoveryPolicy": "LLM_REPAIR_THEN_CLARIFY",
  "recoveryAttempted": true,
  "effectiveSource": "MODEL_REPAIR",
  "effectiveValue": ["dealer-vehicle"],
  "modelCalls": 2
}
```

Required fields include:

- decision kind and obligation/intent ID;
- original decision source/value;
- validation result and typed reason;
- effective recovery policy and whether it allowed recovery;
- repair/replan attempt count;
- effective decision source/value;
- selected provider/model and any provider transition;
- selected evidence algorithm; and
- explicit partial/failure outcome.

Diagnostics must not expose prompts, private reasoning, credentials, raw
private filters, or unrestricted evidence. A mechanism is "diagnostic" only if
it observes behavior. If it mutates intent, routing, evidence, or output, it is
part of production semantics and must be governed as such.

## 11. Implementation Sequence

### Phase 0 - Freeze and guard

- [x] Adopt the rule in public AI Fabric coding-assistant guidance and both
  framework/Platform philosophy documents.
- [x] Add a focused source guard for runtime semantic-decision packages that
  flags keyword/regex/token-overlap inference over query/prompt/message text.
- [x] Exclude presentation, explicit protocol, configured policy/security,
  declared retrieval providers, tests, and operations parsing from the guard.
- [x] Maintain reviewed exceptions for any permitted text processing located
  inside a guarded semantic package, with a reason and owner for each entry.
- [x] Fail CI on new unreviewed text matching that can substitute for or hide a
  model semantic decision, not on text matching generally.

### Phase 1 - Strengthen structured model output

- [x] Update active intent schemas/prompts with all required fields, catalogs,
  uncertainty, missing-information, and obligation identifiers.
- [x] Use provider-native structured output where supported.
- [x] Make bounded LLM repair observable and remove consumable synthetic
  semantic defaults.
- [x] Add schema-completeness and repair tests; live deployment canaries provide
  the real-provider release evidence in Phase 5.

### Phase 2 - Remove P0 runtime text matching

- [x] Remove query mention matching from `BoundedFanOutRouter`.
- [x] Remove referential phrase regexes from `WorkingSetTargetSeedingStep`.
- [x] Remove lexical ranking/excerpt selection from
  `CompoundReadEvidenceSupport`.
- [x] Remove complexity heuristics from `AdvancedRagSupport`.
- [x] Remove built-in keyword moderation fallback from
  `AIContentFilterService`.

### Phase 3 - Make every semantic recovery explicit

- [x] Introduce and resolve the typed recovery policy once per request.
- [x] Gate intent coercion, action-to-information conversion, source fan-out,
  no-result broadening, and provider retry through it.
- [x] Preserve mandatory security denials independently of recovery.
- [x] Emit original/effective decision diagnostics for every allowed recovery.

### Phase 4 - Protect production example boundaries

- [x] Isolate every deterministic local LLM under test/smoke-only startup
  posture; real provider is the production default.
- [x] Label intentional lexical search as lexical search and accept only
  structured inputs from orchestration.
- [x] Add tests proving UI presentation matching and explicit command parsing
  cannot alter runtime mode, source, target, action, or authority fields.
- [x] Record server progress events and typed presentation metadata as optional
  UI improvements outside this release gate.

### Phase 5 - Verification and release

- [x] Run focused module tests after each phase.
- [x] Run the full public framework reactor and all real-app modules before the
  immutable release.
- [x] Verify an empty-cache external consumer from Maven Central.
- [x] Upgrade private runtime/connector/relay to the one new current release.
- [ ] Publish and apply new deployment versions without running old/new in
  parallel.
- [ ] Run Northfield, ProdUS, Shopify, specialist, and full Platform release
  gates.
- [ ] Update this plan with exact release, source, artifact, deployment, and
  live request evidence.

## 12. Verification Matrix

### 12.1 Model-quality corpus

For each semantic feature, use equivalent requests across:

- paraphrases and synonyms;
- clause order changes;
- pronouns, ordinals, and multi-turn references;
- concise and verbose wording;
- spelling/noise cases;
- at least one non-English language supported by the configured model;
- compound single-domain and multi-domain requests; and
- deliberately ambiguous requests that should clarify.

Measure structured-field completeness and decision correctness. Do not add a
keyword patch for individual failures; improve schema/prompt/catalog/model or
accept a documented tolerance.

### 12.2 Negative invariants

- No untrusted request can broaden a source/action allowlist.
- Unknown model IDs are rejected without guessed replacements.
- Provider/model failure cannot become a successful synthetic answer.
- A selected action cannot execute after a domain mismatch.
- Recovery-disabled modes never recover silently.
- Every recovery-enabled result identifies its recovery path.
- Equivalent meaning is not dependent on one English phrase.
- Provider-scored results retain provider score order within each result set.
- Stable provider-ranked results remain ordered when scores are unavailable.
- Raw scores from different providers are not treated as comparable without a
  declared normalization contract.
- No orchestration-side query-token scorer reorders evidence or chooses an
  excerpt.
- UI presentation matching never mutates runtime semantic or authority fields.

### 12.3 Existing product canaries

- Northfield: inventory, policies, compound retrieval in both clause orders,
  follow-up references, attachments, comparison, and governed writes.
- ProdUS: exact service-module/package-template retrieval and tenant/deployment
  isolation.
- Shopify: private adapter retrieval, catalog read action, policy answer, and
  no exposure of private runtime assertions.
- Specialists/chains: exact manifest/action/vector-space authority and no
  recovery across tenant or deployment boundaries.

## 13. Release And Rollback

The previous `0.8.21` release remains the live baseline only until the
current-only `0.8.22` replacement completes. It is not compliant with the newly
explicit no-semantic-text-matching rule. No private runtime patch emulates or
hides the corrected framework behavior.

Release the correction as one new immutable AI Fabric version, then rebuild the
private runtime from Maven Central and replace current deployments. Because the
product is greenfield and latest-only, rollback means redeploying the last
known-good complete runtime artifact, not maintaining code compatibility or
simultaneous traffic between framework versions.

## 14. Completion Criteria

This plan is complete only when:

- [x] all P0 semantic text matching listed in section 4 is removed;
- [x] every semantic recovery is denied by default or permitted through one
  explicit typed policy;
- [x] original and effective model decisions are observable;
- [x] evidence sent to generation uses vector/provider scores or provider-ranked
  order when available, otherwise a declared reranker, structured model
  compressor, or explicitly unranked provider order;
- [x] no orchestration-side lexical scorer overrides provider ranking or selects
  query-matched excerpts;
- [x] AI Workspace presentation and explicit protocol matching are verified not
  to influence runtime semantic decisions or authority;
- [x] deterministic local providers cannot start under a production posture;
- [x] CI prevents semantic substitution while allowing reviewed presentation,
  protocol, retrieval, structural, security, test, and operations behavior;
- [x] relationship-query work remains explicitly deferred and is not silently
  included in this release claim;
- [x] public framework guidance and private Platform context agree;
- [x] a new immutable framework release is published and consumed from Central;
- [ ] all affected deployments are replaced with the latest runtime; and
- [ ] focused and full live release gates are green, with any external blocker
  stated separately rather than hidden by fallback.

## 15. Current Checkpoint

Framework implementation is committed and pushed on public `main` at
`d325b6aef8eb5cc7b4c0edb3279d39f5db6004a4`. Exact-source Framework Build
`38136695523` is green, including the framework reactor, restored integration
suites, all real apps, packaged bounded-chain smoke, data-sync smoke, P1
scenario smoke, and vector-provider container contracts. Annotated tag and
GitHub release `ai-fabric-framework-v0.8.22` are published. Maven Central run
`38137843070` passed. The `0.8.22` BOM, core JAR, and execution JAR return HTTP
`200`; empty-cache minimal and agentic external consumers passed from Central.

Private adoption is implemented at `0.8.22`: deployment recovery policy is
validated and compiled into server-owned runtime configuration; AI Workspace
no longer derives semantic fields from attachment/query presentation; the
Platform UI exposes typed recovery controls; and Thinker now consumes explicit
typed semantic decisions rather than classifying question text. Focused Thinker
tests, the AI Workspace semantic-boundary and complete package gates, the
private semantic source guard, the Platform UI production build, the complete
public-site Astro/Playwright verification, and the full Platform backend clean
suite (`924/924`) are green. A clean private runtime/connector/vectorization
build using the same Central-only temporary repository passed, and the runtime
boot JAR contains 20 AI Fabric libraries exclusively at `0.8.22`. Private
source commit, immutable image publication, deployment replacement, and live
canaries remain.

Relationship-query source and behavior remain explicitly deferred. No prompt
tuning, phrase patch, or hidden success fallback is part of this correction.
