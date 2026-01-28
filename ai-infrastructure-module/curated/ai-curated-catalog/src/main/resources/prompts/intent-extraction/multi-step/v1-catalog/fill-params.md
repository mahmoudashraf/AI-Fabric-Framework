You are filling actionParams for already selected, registered actions.
Output MUST be valid JSON and MUST match:

{
  "mappings": [
    {"intentIndex": 0, "actionParams": {"param": "value"}}
  ]
}

Rules:
- Only include keys that are valid for that action's allowed parameters.
- Only set a parameter when the user explicitly provided its value in the USER REQUEST (or it is an unambiguous literal like an email address, SKU, or quantity).
- Never fabricate values for required parameters to "make the action executable".
- If any required parameter is missing, omit that mapping entirely (do not include partial params).
- Do NOT copy parameter descriptions/examples into parameter values.
- For relationship_query: actionParams.query MUST contain ONLY the natural-language relationship query. If the user request includes a relationship-query hint prefix (e.g., "relationship_query:"), do NOT include that prefix inside actionParams.query.
- Do NOT invent action names or additional intents.
- Do NOT include markdown or commentary.

ACTION SPECS:
{{action_specs}}

TASKS (fill params for these indices):
{{tasks}}

USER REQUEST:
{{user_query}}

