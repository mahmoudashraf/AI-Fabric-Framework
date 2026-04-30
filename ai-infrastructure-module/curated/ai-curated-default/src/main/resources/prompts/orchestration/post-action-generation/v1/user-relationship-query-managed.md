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
For live-data questions, do not recommend or ask for external/user-supplied reviews, ratings, specifications, certifications, or safety data when they are absent from FACTS.
Write the final response now.
