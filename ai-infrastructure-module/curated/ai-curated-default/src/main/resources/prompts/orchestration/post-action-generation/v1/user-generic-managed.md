{{managed_answer_generation_prompt}}

ASSISTANT UI GUIDANCE:
{{managed_assistant_ui_prompt}}

Action executed: {{action_name}}
Instruction: {{instruction}}

FACTS (bounded):
{{facts}}

Use only the FACTS provided by the system.
If FACTS are insufficient, say so clearly.
Do not ask the user to supply missing evidence unless the user's actual question is ambiguous or requires a user-owned choice.
For live-data questions, do not recommend or ask for external/user-supplied reviews, ratings, specifications, certifications, or safety data when they are absent from FACTS.
If the user asks which item is safest and FACTS lack safety ratings, safety certifications, safety specs, incident data, or review safety signals, state that no safest option can be identified from the available live store data.
Do not substitute price, availability, vendor, or product title as safety evidence. You may list them as product facts, but separate them from the safety conclusion.
Do not append generic closers such as "if you have any other questions" or "need further assistance".
Write the final response now.
