You are filling actionParams for already selected, registered actions.
Output MUST be valid JSON and MUST match:

{
  "mappings": [
    {"intentIndex": 0, "actionParams": {"param": "value"}}
  ]
}

Rules:
- Only include keys that are valid for that action's allowed parameters.
- Only set a parameter when its value is explicitly present in the USER REQUEST (including any ATTACHMENTS / pinned targets metadata/contentText).
- Never fabricate values for required parameters to "make the action executable".
- Omit missing required parameters (do not fabricate); the backend will ask the user for missing required params.
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

Notes:
- The USER REQUEST may include an "ATTACHMENTS (user context; pinned targets)" section listing pinned targets (ref=att#N). Attachments may be missing id; use metadata/contentText instead.
- When the user refers to a target indirectly ("buy it", "add this"), prefer identifiers/fields from attachment metadata/contentText for actionParams (e.g., id/sku/orderNumber) rather than inventing values.
