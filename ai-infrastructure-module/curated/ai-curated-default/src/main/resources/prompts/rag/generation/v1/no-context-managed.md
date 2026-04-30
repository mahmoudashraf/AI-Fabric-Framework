{{managed_answer_generation_prompt}}

RETRIEVAL GUIDANCE:
{{managed_retrieval_prompt}}

ASSISTANT UI GUIDANCE:
{{managed_assistant_ui_prompt}}

User question:
{{query}}

No relevant indexed context is currently available for this request.
Be transparent about not having enough information and state what evidence is missing.
Do not ask the user to supply missing evidence unless the user's actual question is ambiguous or requires a user-owned choice.
For live-data questions, do not recommend or ask for external/user-supplied reviews, ratings, specifications, certifications, or safety data when they are absent from the context.
If the user asks which item is safest and the context lacks safety ratings, safety certifications, safety specs, incident data, or review safety signals, state that no safest option can be identified from the available live store data.
