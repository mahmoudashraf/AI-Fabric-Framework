Request query:
{{query}}

Resolved mode:
{{mode}}

Extracted intent JSON:
{{intent_json}}

Eligible READ actions JSON:
{{eligible_actions_json}}

Prior read-action evidence JSON:
{{prior_evidence_json}}

Planner budgets:
- iteration: {{iteration}} / {{max_iterations}}
- max actions this iteration: {{max_actions_per_iteration}}
- max total actions: {{max_total_actions}}
- rag cooperation mode: {{rag_cooperation_mode}}

Instructions:
- If current evidence is already sufficient, return decision=ANSWER_FROM_CONTEXT with no actions.
- If one or more eligible READ actions would materially improve the answer, return those actions.
- If live READ actions are helpful but indexed knowledge is still needed, return decision=EXECUTE_READ_ACTIONS_AND_RAG.
- If READ actions are not the right tool, return decision=USE_RAG_ONLY.
- For compound requests, select all needed read actions within budget. Example: a current-status + policy/rule question should include both the direct status action and the direct policy/rule action when both are eligible.
- Prefer the most specific purpose-built READ action when one directly matches the request.
- For relationship_query, derive params.query from the user's ask plus supplied page/record/context evidence. The user must never be asked to provide "the relationship query"; if you cannot derive a useful relationship query, choose USE_RAG_ONLY or EXECUTE_READ_ACTIONS_AND_RAG with another eligible action.
- For policy/rule/procedure questions, prefer the direct policy/rule action when it is eligible. Do not rely on an unrelated record/status action to answer policy evidence.
- Do not decompose a compare request into multiple generic detail lookups if a single comparison action exists.
- Do not decompose a similar/alternative request into generic detail lookups if a single related-records action exists.
- Do not decompose a current-status check into broader catalog reads if a direct status action exists.
- When prior evidence answers only part of a compound request, propose the remaining read action(s) or use RAG; do not stop early.
- Keep params minimal and only use values that are directly implied by the request.
- Put material filters and criteria into typed params whenever the eligible action schema exposes them. For example, use numeric bound params for price ceilings and boolean params for availability instead of placing those constraints in query text.
- Keep free-text query/search params focused on the subject to retrieve or compare. If a material constraint cannot be represented by typed params and the action result may be insufficient, combine the read action with RAG or use RAG according to the rag cooperation mode.
