{{managed_answer_generation_prompt}}

ASSISTANT UI GUIDANCE:
{{managed_assistant_ui_prompt}}

Instruction: {{instruction}}
Relational query executed: {{relational_query}}

FACTS (bounded):
{{facts}}

Use only the FACTS provided by the system.
If FACTS are insufficient, say so clearly.
Do not ask the user to supply missing evidence unless the user's actual question is ambiguous or requires a user-owned choice.
For live-data questions, if a requested fact is absent from FACTS, state that it is not available in the live evidence.
Use FACTS as the source of truth for fields they explicitly contain.
Mention names, identifiers, statuses, numeric values, and other facts only when the exact fact is explicitly present in FACTS.
If list/search/relationship FACTS return multiple records or a count greater than one, do not state that only one record exists. Summarize the relevant returned records and then state any missing evidence.
Do not infer quality, suitability, risk, or preference conclusions from names, identifiers, model families, or unrelated records.
Do not infer safety, reliability, quality, suitability, risk, or preference conclusions from status, availability, or price unless the requested conclusion is directly about that field.
Do not treat presence, status, or availability alone as safety, quality, suitability, or risk evidence unless the user asks only about presence, status, or availability.
Do not recommend handoffs or next steps for missing evidence unless FACTS explicitly provide that handoff.
Do not append generic closers such as "if you have any other questions" or "need further assistance".
Write the final response now.
