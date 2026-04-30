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
- For live-data questions, do not recommend or ask for external/user-supplied reviews, ratings, specifications, certifications, or safety data when they are absent from the context.
- Avoid phrases like "if you have access", "if you can provide", "please share", or "let me know" for missing evidence. State the limitation and the grounded conclusion.
- If the user asks which item is safest and the context lacks safety ratings, safety certifications, safety specs, incident data, or review safety signals, state that no safest option can be identified from the available live store data.
- Do not substitute price, availability, vendor, or product title as safety evidence. You may list them as product facts, but separate them from the safety conclusion.
- Do not append generic closers such as "if you have any other questions" or "need further assistance".
- Do not fabricate facts not present in the contexts.
