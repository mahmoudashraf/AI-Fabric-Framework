# 010.17 - Grounding-Eligible Read Action Post-Generation And LLM Facts Plan

Status: implementation direction clarified on 2026-06-03 and made mode-configurable on 2026-06-04. The immediate runtime goal is to force post-action generation from the action result for grounding-eligible read actions when the active orchestration mode enables it. `llmFacts` remains the explicit projection contract for future bounded action-specific shaping.

## Purpose

When an allowed read-only MCP action runs, the action result is the grounded evidence.

For actions such as `produs_catalog_search`, LoomAI should not return raw `ACTION_EXECUTED` output as the user-facing answer. It should:

1. execute the grounding-eligible read action;
2. pass the action result into post-action answer generation;
3. return generated `answer` / `safeSummary` as the primary user response;
4. retain the raw action result as evidence/debug metadata.

This keeps the MCP concept simple: MCP exposes data/tools to the AI runtime, and the runtime uses the allowed read result as grounding evidence.

## Non-Goal

Do not add generic Java logic that guesses business facts by scanning arbitrary MCP JSON keys.

Avoid generic connector behavior such as:

- special casing business keys like `categories` or `packageTemplates`;
- walking every JSON object field and exposing every list as a named LLM fact;
- deriving product-specific fact names from arbitrary JSON keys;
- treating fallback key matching as the production contract.

That kind of behavior is brittle and can accidentally turn connector fallback logic into a hidden data contract.

## Immediate Runtime Contract

For `READ` actions where `groundingEligible=true` and the active mode enables `force-grounding-eligible-read-action-post-generation`:

- force post-action generation even when the extracted intent did not explicitly request generation;
- include the action result payload in the post-action facts sent to the LLM;
- keep the generation prompt bounded by existing post-action max-char limits;
- if generation succeeds, set the generated text as `message`, `summary`, and `answer`;
- if generation fails or returns empty content, return a short deterministic fallback summary rather than raw JSON.

The deterministic fallback should stay structural and compact. It should not attempt domain-specific interpretation.

Default behavior should remain conservative for ordinary modes. The default curated `thinker` mode enables this flag because its purpose is to answer from evidence:

```yaml
ai:
  orchestration:
    modes:
      thinker:
        force-grounding-eligible-read-action-post-generation: true
```

Read-action-resolution allowlist policy remains an independent force path for actions the planner explicitly allowed.

## LLM Facts Direction

`llmFacts` should be used when a marketplace action wants an explicit, product-owned projection.

For ProdUS, a future action manifest can define an explicit full-result or bounded projection contract for:

- `produs_catalog_search`;
- `produs_catalog_export`;
- other read-only ProdUS catalog/workspace/evidence tools.

The simple future shape can be:

```yaml
llmFacts:
  rootPath: toolResult.content.0.text
  mode: FULL_STRUCTURED_JSON
  maxChars: 12000
```

This means:

- parse the MCP result text as JSON;
- send the structured JSON as the action's LLM facts;
- apply size/safety bounds;
- do not use Java field-name guessing as the contract.

If a narrower projection is needed later, the marketplace action manifest should declare it explicitly through `copyFields`, `lists`, and `objects`.

## ProdUS Application

For the ProdUS observed issue:

- `produs_catalog_search` returned useful structured MCP data;
- the raw action evidence appeared in chat because the response path was action-result-first instead of answer-first;
- the correct fix is runtime post-action generation from the read action result;
- the UI/widget can keep its current development rendering behavior while the runtime returns a better primary answer.

## Verification Expectations

Minimum verification:

- unit test that grounding-eligible read actions force post-action generation;
- unit test that mode policy controls whether grounding-eligible read actions force post-action generation;
- unit test that forced generation prompt includes action result data;
- unit test that generation failure returns a compact deterministic fallback, not raw JSON;
- connector tests should not assert generic business-key discovery from arbitrary MCP JSON;
- live ProdUS smoke should show a generated user-facing answer for `produs_catalog_search` / `produs_catalog_export`.
