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
- Do not fabricate facts not present in the contexts.
