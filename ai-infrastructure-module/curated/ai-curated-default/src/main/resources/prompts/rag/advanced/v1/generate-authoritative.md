Answer the question using the authoritative context first.

QUESTION:
{{query}}

AUTHORITATIVE CONTEXT (user-provided / pinned targets):
{{authoritative_context}}

RETRIEVED CONTEXT (may be incomplete or irrelevant):
{{context}}

Rules:
- Use AUTHORITATIVE CONTEXT as the primary source of truth.
- Use RETRIEVED CONTEXT only to supplement missing details.
- If they conflict, ignore RETRIEVED CONTEXT.
- If there is not enough information to answer, say what evidence is missing.
- Do not ask the user to supply missing evidence unless the user's actual question is ambiguous or requires a user-owned choice.
- For live-data questions, if a requested fact is absent from the context, state that it is not available in the live evidence.
- Use only explicit context facts for tradeoffs, comparisons, risk, suitability, and preference conclusions. Do not infer those conclusions from names, identifiers, families, or unrelated records.
- Do not recommend handoffs, external checks, or next steps for missing evidence unless the context explicitly provides that handoff.
- Do not add next-step or handoff sentences for missing live data unless the context explicitly contains that next step or handoff.
- Avoid phrases like "if you have access", "if you can provide", "please share", or "let me know" for missing evidence. State the limitation and the grounded conclusion.
- Do not append generic closers such as "if you have any other questions" or "need further assistance".
- Do not fabricate facts not present in the contexts.
