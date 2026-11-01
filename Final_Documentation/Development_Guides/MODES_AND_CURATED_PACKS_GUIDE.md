# Modes + Curated Packs (v1 Guide)

This guide explains how to enable and use **modes** via **curated packs**, with a focus on the v1 behavior implemented in:
- Phase 0 (policy surface + capability bundles)
- Phase 1 (`navigator_deep` retrieval semantics)
- Phase 2 (`executor` tightening)

Related planning:
- `Final_Documentation/System_Archtecture_Guides/PLAN_READ_ONLY_ACTION_RESOLVER_AND_THINKER_MODES.md`

It is intentionally practical: **what to set**, **what to expect**, and **how to debug**.

---

## 1) The mental model

### Mode = “capability bundle” + “RAG budgets”
Per request, the server resolves an `OrchestrationPolicy` with:
- capabilities:
  - `actionsEnabled`
  - `retrievalEnabled`
  - `deepRetrievalEnabled`
  - `suggestionsEnabled`
  - `exposeReadProbeFallbackAttempt` (optional debug visibility for READ→RAG fallback)
- rag budgets:
  - `fanoutEnabled`
  - `maxSpaces`
  - `topKPerSpace`
  - `maxDocumentsReturnedToClient`
  - `maxDocumentsUsedForContext`
  - `maxContextChars`
  - `retrievalVectorSpacesAllowlist`

The pipeline is the authority:
- The LLM can *propose* intents.
- The server policy decides what is *allowed* (fail‑closed).

### Curated pack = “config + prompts”
Curated packs ship:
- a pack YAML (`ai-curated/packs/<pack>.yml`)
- optional prompt overlays (prompt templates)

They do not replace pipeline logic.

---

## 2) How the effective mode is chosen

Request inputs (from `OrchestrationContext`):
- `mode` (optional selector key; application-defined string)
- `position` (optional UI hint; application-defined string; **not used by core for routing**)

Resolution order:
1) If request has `mode` and it exists under `ai.orchestration.modes.*` → requested mode wins.
2) Otherwise, `ai.orchestration.default-mode` is used (must also exist under `ai.orchestration.modes.*`).

Notes:
- Core does **not** route based on `position`. If you want “position → mode”, implement it in your app/web layer as an optional router (curated packs may ship an advisory `position-routing` map, but core ignores it).

---

## 3) Modes shipped in the `commerce` pack (Real App default)

The `commerce` curated pack (`ai-curated-commerce`) configures:

### `navigator` (unchanged)
Use for:
- product discovery / semantic search
- comparisons / summaries grounded in KB + (optional) attachments

Characteristics:
- actions enabled
- retrieval enabled
- deterministic “RAG + generate” behavior (when profile defaults to it)

### `navigator_deep` (deep retrieval)
Use for:
- “go deep” queries that need broader coverage:
  - alternatives
  - reviews / complaints
  - policies

Characteristics:
- actions disabled (fail‑closed if LLM proposes actions)
- retrieval enabled
- deep retrieval enabled (fanout allowed when configured)
- bounded multi‑space retrieval (caps enforced by budgets)

### `executor` (action-first)
Use for:
- orders / returns / refunds / subscriptions
- “do X” operational flows

Characteristics:
- action catalog is included in prompting
- KB overview is suppressed to avoid “browsing” confusion
- retrieval is optional, but if enabled it is **restricted**:
  - requires `retrievalVectorSpacesAllowlist` to be configured
  - requires `vectorSpace` to be explicitly set by the LLM when `requiresRetrieval=true`

### `cart_assistant`
Use for:
- cart-specific action flows (add to cart / checkout helpers)

---

## 4) Key configuration (app-level)

### Enable a curated pack
Example:

```yaml
ai:
  curated:
    pack: commerce
```

Maven:

```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-curated-commerce</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Override a mode
Because pack YAML is loaded with low precedence, your app can override:

```yaml
ai:
  orchestration:
    modes:
      navigator_deep:
        rag:
          max-spaces: 4
```

---

## 5) Executor “restricted retrieval” (important)

If you enable retrieval in an “action-first” mode and require an allowlist, you must provide it:

```yaml
ai:
  orchestration:
    modes:
      executor:
        retrieval-enabled: true
        retrieval-allowlist-required: true
        rag:
          retrieval-vector-spaces-allowlist:
            - policy
```

Behavior:
- If allowlist is missing/empty and the LLM requests retrieval → `CLARIFICATION_REQUIRED` with `reason=RETRIEVAL_ALLOWLIST_REQUIRED`.
- If allowlist exists but the LLM omits `vectorSpace` (and policy requires explicit selection) → `CLARIFICATION_REQUIRED` with:
  - `reason=VECTOR_SPACE_REQUIRED_BY_POLICY`
  - `allowedVectorSpaces=[...]`
- If the LLM requests a denied space → `CLARIFICATION_REQUIRED` with `reason=VECTOR_SPACE_NOT_ALLOWED_BY_POLICY`.

### Versioned Platform Overrides For Internal Source Selection

Source selection belongs to the assistant, not to the buyer. Prompt guidance
should ask the model to choose registered source entity types from the supplied
catalogue and conversation, and never ask users for a vector space or internal
knowledge-domain name. This guidance does not itself change server recovery:
the framework can produce a missing-domain clarification after a read planner
promotes a request to retrieval without selecting a source.

The existing deployment `providerConfig` accepts these executor overrides:

```json
{
  "orchestrationModeOverrides": {
    "executor": {
      "knowledgeBaseOverviewEnabled": true,
      "semanticRecovery": {
        "missingVectorSpace": "BOUNDED_ALLOWED_FAN_OUT"
      },
      "rag": {
        "fanoutEnabled": true,
        "maxSpaces": 2
      }
    }
  }
}
```

Merge these fields with the deployment's other settings. Publish and apply
through the normal version mechanism; saving a draft does not change a live
runtime. Platform materializes the corresponding
`AI_ORCHESTRATION_MODES_EXECUTOR_*` environment properties. No new framework
release or browser-provided routing hint is needed for this existing policy.

The model's valid source selection remains first. Only an unresolved selection
may use this explicitly allowed recovery, bounded by the generated authorized
source allowlist and `maxSpaces`. Choose a budget covering the intended allowed
sources; do not imply that a capped subset searched every source. `maxSpaces`
must be an integer from1to12; explicit fan-out recovery also requires
`fanoutEnabled=true`. These checks bound retrieval execution, not semantic
confidence. Platform does not accept an arbitrary retrieval allowlist in this
override object; it derives that authority from the configured sources.
Provisioning rejects explicit bounded recovery when that generated list is
empty, rather than allowing a framework default to search every registered
space. A disabled source does not populate the list.

Keep invalid-source/action/tenant/authentication policies unchanged. An invalid
or denied source is not equivalent to a missing source and must not silently
trigger broad retrieval. Setting `vectorSpaceSelectionRequired=false` alone is
not an internal-source resolution strategy. Review response diagnostics for
`EXPLICIT_BOUNDED_FAN_OUT_RECOVERY`, selected spaces and effective budgets;
record recovery as recovery, not as an LLM selection or a reliability guarantee.

### Versioned READ Evidence Budgets

Persist READ planner and per-action evidence budgets in deployment
`providerConfig`, alongside the existing executor planning settings. For example:

```json
{
  "orchestrationModeOverrides": {
    "executor": {
      "readActionResolution": {
        "planningMode": "ITERATIVE",
        "maxIterations": 2,
        "maxPlannerContextChars": 12000,
        "maxActionEvidenceCharsPerAction": 8000,
        "maxActionEvidenceItems": 10,
        "ragCooperationMode": "RAG_IF_ACTIONS_INSUFFICIENT"
      }
    }
  }
}
```

Publish and apply normally; do not tune a live container's environment by hand.
The exact generated environment mappings are:

```text
maxPlannerContextChars            -> AI_ORCHESTRATION_MODES_EXECUTOR_READ_ACTION_RESOLUTION_MAX_PLANNER_CONTEXT_CHARS
maxActionEvidenceCharsPerAction   -> AI_ORCHESTRATION_MODES_EXECUTOR_READ_ACTION_RESOLUTION_MAX_ACTION_EVIDENCE_CHARS_PER_ACTION
maxActionEvidenceItems            -> AI_ORCHESTRATION_MODES_EXECUTOR_READ_ACTION_RESOLUTION_MAX_ACTION_EVIDENCE_ITEMS
```

The contract was checked against immutable framework tag
`ai-fabric-framework-v0.8.42` (commit `c21702acc3eb3226c892d4506b1f40fe38171607`),
specifically `OrchestrationProperties.ReadActionResolutionModeOverrides` and
`OrchestrationPolicy.ReadActionResolutionPolicy`. Evidence items accept integers
from 1 through 100; the framework default is 5. Both character budgets accept
positive Java integers, from 1 through 2147483647; the framework declares no
smaller maximum. Use measured, economical budgets rather than those type limits.
The example preserves the qualified 12000-character planner, 8000-character
per-action and 10-item budgets without changing global defaults.

Absent or null fields generate no environment override, retaining the existing
curated/server mode settings. Strings, booleans, fractional numbers, non-positive
numbers, overflowing integers and item counts above 100 fail draft validation;
they are not coerced into executable budgets. Unknown fields remain unsupported.
These are finite execution-budget checks, not semantic validators. The adapter
affects only executor mode; it neither enables READ planning by itself nor changes
authorized actions, generated source allowlists, authentication, confirmation or
semantic recovery policy. Other modes retain their existing configuration.

---

## 6) Debug fields to look at (UI / logs)

In the orchestrator response `metadata.orchestrationPolicy`:
- `profile`
- `mode`
- `position`
- `modeSource` (`REQUEST_MODE` vs `DEFAULT_MODE`)
- `actionsEnabled`, `retrievalEnabled`, `deepRetrievalEnabled`, `suggestionsEnabled`
- `actionsPreferred`, `knowledgeBaseOverviewEnabled`, `retrievalAllowlistRequired`, `vectorSpaceSelectionRequired`
- `exposeReadProbeFallbackAttempt` (when true, READ→RAG fallback attempts may be surfaced)

In the orchestrator response `result.metadata.readProbe` (only when enabled by policy):
- Present when a READ action returned an empty successful payload and the orchestrator fell back to RAG.
- Contains a structured summary of the attempted READ action (no silent replacement).

In `result.data.ragResponse.metadata` (when retrieval runs):
- `embeddingQuery`
- `optimizedQuery`
- fanout selection information (when multi‑space)

---

## 7) Real App usage (chat-capabilities-demo)

The demo app already accepts:
- `position`
- `mode`

Examples live in:
- `Real_Apps/chat-capabilities-demo/requests/demo.http`

Recommended patterns:
- normal browsing: send `position=landing|catalog|search`
- deep search: send `mode=navigator_deep`
- action flows: send `mode=executor`

If you still want the older “position → mode” convenience, implement it in the demo app (or your app) as a pre-orchestration router that sets `mode` when it is missing.
