# 010.35 AI Fabric Coding Assistant Skill Implementation Plan

- Status: planned; implementation not started.
- Created: 2026-10-13.
- Execution priority: 1; no dependency on the other six ideas.
- Source review: framework `561659dc`; private Platform `db58acd9`; AI Fabric `0.8.32`.
- Owner: public AI Fabric framework repository. This private plan tracks the cross-product decision only.
- Related: [execution index](PLATFORM_STARRED_IDEAS_EXECUTION_INDEX.md), [010.37 configuration assistant](010_37_PLATFORM_DEPLOYMENT_CONFIGURATION_ASSISTANT_IMPLEMENTATION_PLAN.md).

## 1. Outcome And Verdict

Proceed with a focused coding-assistant skill that helps a developer build a working application with the released AI Fabric contracts. The skill should guide module selection, data ingestion, retrieval, governed actions, conversation state, and verification without requiring the developer to navigate the full documentation tree first.

The product is reusable developer guidance. It does not require a new execution engine or an always-running agent. A future Platform assistant can reuse the guidance organization, but it needs the separate operator/tool boundary in `010.37`.

## 2. Current Code And Documentation Evidence

Inspected in the public sibling repository:

- `docs/llm-context/AI_FABRIC_RULES_FOR_CODING_ASSISTANTS.md`: existing semantic-decision, action, history, and verification rules.
- `docs/Framework-Dev-Guides/developer-workflows/AI_LLM_CODE_GENERATION_GUIDE.md`: existing developer workflow guidance.
- `docs/Framework-Dev-Guides/retrieval-vectorization/DATA_SYNC_PUSH_API_GUIDE.md`: official indexing API guidance.
- `docs/Framework-Dev-Guides/actions-governance/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`: action integration guidance.
- `docs/Framework-Dev-Guides/testing-verification/RELEASE_SAFETY_CHECKLIST.md`: release verification expectations.
- `docs/course/`: task-specific assistant and review prompts.
- `examples/real-apps/`: applications demonstrating framework usage.
- `ai-infrastructure-module/pom.xml`: current released BOM source version.

A search including hidden repository paths found no existing `SKILL.md` package. Existing prose and course prompts are source material, not a completed distributable skill.

## 3. Ownership And Boundaries

The public skill must contain only reusable framework usage and public references. Hosted LoomAI runtime, connector deployment, Marketplace operations, production endpoints, private credentials, and operator tools belong in private Platform guidance.

The skill must preserve:

- latest supported Maven artifacts rather than copied source or mutable-main Docker builds;
- model-driven semantic interpretation and explicit configured recovery;
- trusted identity/resource provenance and fail-closed isolation;
- application ownership of domain persistence and effects;
- source-evidence-based escalation when a public contract is missing;
- the completed generic conversation-control contracts, without teaching Basic Inbox as framework code.

Do not embed a user-specific filesystem path as an installation requirement. Relative references must work when the skill is copied to another machine.

## 4. Proposed Package

Create an official skill source package under `skills/ai-fabric/` in the public repo:

```text
skills/ai-fabric/
  SKILL.md
  references/
    modules-and-providers.md
    indexing-and-retrieval.md
    actions-and-trust.md
    conversation-and-execution.md
    verification.md
```

The package path is proposed, not present in the reviewed baseline. Tool-specific installation adapters may copy or link this canonical package into supported assistant skill directories; they must not maintain divergent instruction bodies.

`SKILL.md` should contain purpose, activation conditions, a short source-inspection workflow, release selection, task routing, and verification obligations. Keep detailed examples in referenced documents. Deterministic routing of skill documentation by declared task is guidance organization; generated application semantic routing must follow framework philosophy.

## 5. Implementation Phases

### S0. Curate Authoritative Sources

- [ ] Inventory the released module/API names and current examples by capability.
- [ ] Select one small, known-working example for indexing/RAG, actions, sessions, and execution.
- [ ] Separate public rules from LoomAI/private conventions.
- [ ] Document which instructions are normative and which examples demonstrate optional choices.

Deliverable: a short reference inventory with working links and current artifact coordinates.

### S1. Author The Canonical Skill

- [ ] Write the package and capability reference pages.
- [ ] Teach inspection of `pom.xml`, configuration, and application ownership before edits.
- [ ] Include current conversation-control use as an optional capability and distinguish Human Review from handover.
- [ ] Require typed output/contracts, useful errors, and focused verification; prohibit hidden semantic fallbacks.
- [ ] Describe explicit contract escalation instead of generating guessed endpoints or internal-service copies.

Deliverable: an installable skill without private paths, secrets, or duplicated framework implementations.

### S2. Installation And Maintenance

- [ ] Document installation for the intended coding assistants using their actual supported skill formats at implementation time.
- [ ] Keep a single canonical source and verify packaged relative references.
- [ ] Record the supported released artifact version and framework commit.
- [ ] Add documentation link/package validation to the existing documentation workflow.
- [ ] Assign review ownership for API changes that invalidate guidance.

Do not introduce a runtime release just to publish skill prose. A skill correction may ship as a documentation change, with its supported artifact version stated accurately.

### S3. Demonstrate Useful Adoption

- [ ] Use the skill in a clean consumer application with an empty Maven cache and released artifacts.
- [ ] Implement one ingestion/retrieval path, one governed action, and one session lifecycle using supported APIs.
- [ ] Run focused application checks and a real-provider smoke where credentials are available.
- [ ] Include an unsupported request that the assistant correctly identifies as a contract gap.
- [ ] Check generated code for accidental semantic keyword routing, internal API use, and private data exposure.

## 6. Verification And Exit Criteria

The package is complete when its instructions and links resolve outside the author machine, its examples compile against released artifacts, and a coding assistant can produce the specified verified consumer behavior without copied framework sources.

Capture prompts, generated diffs, commands, test results, and substantive failures. Real-provider quality is evaluated with a small meaningful corpus; a successful compile alone does not prove useful guidance. Do not require an unrelated full provider matrix for a documentation-only change.

## 7. Risks And Decisions

- Stale API examples: bind the package to an explicit supported release and test selected consumers.
- Oversized context: use a short entry and task-specific references rather than injecting every document.
- Overclaiming: make supported versus application-owned contracts clear; the skill must not invent public HTTP endpoints.
- Assistant differences: use verified packaging adapters while keeping canonical content shared.

## 8. Framework Release And Tracking

No framework binary change or release is required by this plan. A real reusable API defect discovered during adoption becomes a separately evidenced framework request with focused tests. Track implementation progress here, public package location/commit, consumer evidence, and supported release. Human in the Loop is an existing capability to explain, not an implementation prerequisite to recreate.
