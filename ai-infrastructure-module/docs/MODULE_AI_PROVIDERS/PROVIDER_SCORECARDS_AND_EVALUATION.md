# Provider Scorecards & Evaluation Harness (Real API)

This document defines a **two-lane testing strategy** for LLM providers:

- **Lane A (Hard gates / CI)**: deterministic, contract-level assertions that must not flap.
- **Lane B (Scorecards / Observability)**: rate-based metrics that quantify provider quality over time (non-gating by default).

It is intentionally **provider-agnostic** and focuses on measuring behaviors we care about (structure adherence, constraint fidelity, schema validity), not “did the model say the exact thing”.

---

## 1) Why scorecards (and what they replace / don’t replace)

### The problem
Real API tests are exposed to:
- non-determinism (sampling variance),
- provider drift (model updates),
- prompt sensitivity,
- transient failures (429/5xx/network).

Binary pass/fail per run is a noisy signal and often forces “test-shortcut” compromises.

### The decision
We keep **hard gates** for **system contract and safety**, and add **scorecards** for **model quality**.

### What scorecards do NOT do
- They do not weaken product contracts.
- They do not replace safety/contract checks.
- They do not assert “non-empty results” as correctness (empty can be valid).

---

## 2) Two-lane model stability framework

### Lane A — hard gates (CI)
Hard gates are **deterministic**, must not be stochastic, and are expected to be stable across providers.

**Examples of hard gates (recommended):**
- **JSON/parse contract**: model output must be JSON-only when required.
- **Required fields**: action intents must contain required keys (`action`, required `actionParams`, etc.).
- **Schema validity**: no unknown fields; relationship paths use registered entity types and relationship field names.
- **Normalization invariants**: canonical `OrchestrationResult` surface is stable regardless of provider output.
- **No unsafe execution**: no unintended action execution; strict access policy enforcement.
- **Error-shape stability**: even in errors, response includes the expected stable shape for debugging (e.g., `metadata.plan` for relationship-query).

**Where these live:**
- Unit tests for parsing/normalization/guardrails.
- Targeted Real API integration tests asserting invariants (not exact prose).

### Lane B — scorecards (non-gating, trending)
Scorecards are **statistical**. They are designed to be run:
- nightly / scheduled,
- on-demand when evaluating a provider/model change,
- optionally on PRs (but typically **non-blocking**).

Scorecards produce:
- **rates** (e.g., “constraint fidelity 92%”),
- **failure breakdown** by category,
- **trendable artifacts** (JSON + small summary).

---

## 3) What “correct” means (rubric)

We define correctness in a way that is **provider-agnostic** and aligns to user harm.

### 3.1 Relationship planning correctness (RelationshipQueryPlan)
A plan is **correct** when all of the following hold:

1) **Parse + shape**
   - The planner output is parseable into the plan DTO.
   - Required keys exist (primary entity, candidates, queryStrategy, etc.).

2) **Schema validity**
   - All referenced entity types exist in the schema.
   - All filter fields exist in the schema fields.
   - Relationship paths:
     - `relationshipType` uses the **relationship field name** (not a slug),
     - `fromEntityType`/`toEntityType` are registered,
     - any `conditions[].field` exists on the correct target entity schema.

3) **Constraint fidelity**
   - **No over-constraint**: the plan must not introduce literal constraints not present in the user query.
     - Example failure: query says “from Nike” but plan emits `brand IN [Nike, Adidas]`.
   - **No under-constraint**: the plan must include the constraints explicitly requested by the user.
     - Example failure: query says “blue shoes under $100” but plan omits the color or price constraint.

4) **Execution validity**
   - JPQL generation succeeds (no invalid paths) and execution does not error due to plan structure.
   - **Result count is not part of correctness** (empty results can be a valid outcome).

### 3.2 Orchestrator intent extraction correctness (MultiIntentResponse)
An intent extraction is **correct** when:
- The JSON contract is satisfied (`intents[]` well-formed).
- Action selection is grounded in **AVAILABLE ACTIONS**.
- For `relationship_query`:
  - `actionParams.query` is present and is the **relational-only** portion for compound messages,
  - `actionParams.entityTypes` is plausible and consistent with schema,
  - no hallucinated actions are emitted.

---

## 4) Scorecard metrics (what we measure)

All metrics are reported per **provider**, optionally per **model**, per **scenario category**, and over time.

### 4.1 Structure adherence
- **json_only_rate**: response was JSON-only when required.
- **parse_success_rate**: parsed successfully into expected DTO.
- **required_fields_rate**: required keys were present after normalization.

### 4.2 Schema validity
- **schema_valid_rate**: no unknown fields/relationships/entity types.
- **relationship_path_valid_rate**: relationship paths fully validated (field names, entity slugs, mappings).

### 4.3 Constraint fidelity (most important for user trust)
Split by direction:
- **over_constraint_rate**: introduced constraints not explicitly requested.
  - Track as: `unexpected_literals`, `unexpected_fields`, `unexpected_enums`.
- **under_constraint_rate**: failed to include explicitly requested constraints.
  - Track missing constraints by type: `missing_literal`, `missing_numeric_range`, `missing_entity_scope`.

### 4.4 Entity typing
- **primary_entity_accuracy**
- **candidate_entity_recall**

### 4.5 Compound handling
- **relational_only_extraction_rate**
  - Specifically: `actionParams.query` must not include “summarize/explain/…”

### 4.6 Operational metrics (secondary, but useful)
- latency p50/p95,
- retry/repair counts,
- fallback usage rate,
- token usage / cost estimates (if available).

---

## 5) Scenario catalog: how we define test cases for scoring

Scorecards should be driven by a **scenario catalog**, not ad-hoc test code.

### 5.1 Scenario schema (recommended)
Store a catalog as YAML/JSON, versioned in git.

Minimum fields:
- `id`: stable identifier (used for trending)
- `category`: e.g. `relationship_plan`, `intent_extraction`
- `userQuery`: the raw user text
- `context`: optional extra info (schemas, allowed actions subset, etc.)
- `expected`:
  - `primaryEntityType` (optional)
  - `requiredConstraints`: list of constraints that MUST appear
  - `forbiddenConstraints`: list of constraints that MUST NOT appear
  - `allowEmptyResults`: boolean (usually true)

### 5.2 Constraint descriptor format (recommended)
Constraints are expressed as a small DSL so evaluation can be deterministic:
- `literal(field="brand.name", includes="Nike")`
- `numeric(field="product.price", op="<", value=100)`
- `set(field="product.color", includesAny=["blue"])`
- `entityTypeIncludes("brand")`
- `relationshipPath(from="product", rel="brand", to="brand")`

### 5.3 Scenario design rules
- Keep queries short and unambiguous.
- Avoid time windows unless specifically testing time extraction (time is a common drift axis).
- Include a balanced mix:
  - broad list queries (must produce empty filters),
  - single-literal filters (Nike-only),
  - multi-literal filters (Nike or Adidas),
  - numeric ranges,
  - compound messages (relational + summarize),
  - relationship-path-heavy queries (transactions → destinationAccount).

---

## 6) Evaluation algorithm (deterministic judging)

### 6.1 Normalize inputs before judging
Always evaluate:
- the **normalized** plan / intent outputs (post guardrails),
- not raw provider text.

### 6.2 Judge functions
Implement judge functions as pure logic:
- `judgePlan(plan, scenario.expected) -> {pass/fail per check, failureReasons[]}`
- `judgeIntent(response, scenario.expected) -> {pass/fail per check, failureReasons[]}`

### 6.3 Failure categorization (required for usable dashboards)
Every failure must be attributed to a stable category:
- `PARSE_ERROR`
- `MISSING_REQUIRED_FIELD`
- `UNKNOWN_SCHEMA_FIELD`
- `UNKNOWN_ENTITY_TYPE`
- `REL_PATH_INVALID`
- `OVER_CONSTRAINT_LITERAL`
- `UNDER_CONSTRAINT_LITERAL`
- `UNDER_CONSTRAINT_RANGE`
- `COMPOUND_QUERY_NOT_STRIPPED`
- `UNSUPPORTED_ACTION_HALLUCINATION`
- `TRANSIENT_PROVIDER_FAILURE` (429/5xx/timeout)

---

## 7) Implementation design (how to build the harness)

### 7.1 Where it lives (recommended)
Add a **non-gating evaluation module** under integration testing:

- `ai-infrastructure-module/integration-Testing/provider-scorecard-tests/`
  - depends on orchestrator + relationship-query modules
  - uses `realapi` profile
  - contains:
    - scenario catalog loader
    - runner that repeats scenarios `K` times
    - judge functions
    - JSON report writer

### 7.2 Execution model
Run a scorecard as:
- for each `provider` in list:
  - for each `scenario`:
    - repeat `K` times (e.g., 20–50)
    - record each attempt as an event
  - aggregate into provider summary + breakdowns

**Important:** keep Lane A tests separate; Lane B runs should not flake CI.

### 7.3 Provider selection / purpose separation
Scorecards must support testing providers by **purpose**:
- orchestration intent extraction (JSON intent)
- relationship planning (plan JSON)
- generation (free-form answers)

Recommended configuration keys (example):
- `ai.providers.orchestration.llm-provider=<provider>`
- `ai.providers.relationship-planning.llm-provider=<provider>` (or reuse orchestration if you don’t split yet)
- `ai.providers.generation.llm-provider=<provider>`

If only one provider is configured, scorecard runner should still work by using the global provider.

### 7.4 Output format (report contract)
Write artifacts under:
- `target/provider-scorecards/<runId>/`

Outputs:
- `summary.json` (top-level, all providers)
- `<provider>.json` (detailed per provider)
- `events.ndjson` (optional, one JSON object per attempt for deep debugging)

Minimum `summary.json` fields:
- `runId`, `timestamp`, `commitSha`, `scenarioSetVersion`
- per provider:
  - `runsPerScenario`
  - metric rates + denominators
  - failure category counts
  - latency p50/p95 (if measured)

### 7.5 CI integration (recommended)
Add a scheduled workflow:
- nightly matrix across providers
- uploads `target/provider-scorecards/**` as artifacts
- posts a short summary (optional)

Alerting rule examples (non-blocking):
- if `constraint_fidelity_rate` drops by > X% week-over-week
- if `schema_valid_rate` drops below a floor (e.g., 99%)

---

## 8) How we use scorecards in practice

### Adoption stages
1) **Stage 0**: build harness + collect baseline
2) **Stage 1**: add dashboards + weekly review
3) **Stage 2**: add alerts on significant regressions
4) **Stage 3 (optional)**: introduce *very conservative* gating thresholds in nightly only

### What to do when a provider regresses
Follow the existing decision tree:
- Is it transient (429/5xx)? → provider retry/backoff/logging.
- Is it schema/contract drift? → improve validation + repair loop.
- Is it constraint fidelity? → prompt + generic guardrails + repair.

Never “solve” a scorecard drop by encoding test literals in production logic.

---

## 9) Relationship-query specific note: “no results” is not a failure

For scoring purposes:
- treat empty results as valid if the plan is schema-valid and constraint-fidelity-correct.
- focus on whether the system produced a valid plan and executed it without structural failure.

