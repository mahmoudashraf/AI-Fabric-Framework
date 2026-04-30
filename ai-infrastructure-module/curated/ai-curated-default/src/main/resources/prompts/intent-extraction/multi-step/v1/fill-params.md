You are filling actionParams for already selected, registered actions.

Output MUST be valid JSON and MUST match:

{
  "mappings": [
    {"intentIndex": 0, "actionParams": {"param": "value"}}
  ]
}

Rules:
- Only include keys that are valid for that action's allowed parameters.
- Follow paramsSchema shapes when present (arrays/objects must be valid JSON arrays/objects).
- Only set a parameter when its value is explicitly present in the USER REQUEST (including any ATTACHMENTS / pinned targets metadata/contentText).
- Never fabricate values for required parameters to "make the action executable".
- Omit missing required parameters (do not fabricate); the backend will ask the user for missing required params.
- Do NOT copy parameter descriptions/examples into parameter values.
- For relationship_query: actionParams.query MUST contain ONLY the natural-language relationship query derived from the USER REQUEST and supplied ATTACHMENTS / pinned targets context. If the user request includes a relationship-query hint prefix (e.g., "relationship_query:"), do NOT include that prefix inside actionParams.query.
- For relationship_query: do NOT omit actionParams.query and do NOT ask the user to supply a separate query. If no meaningful query can be derived, leave the action unmapped so the orchestration layer can use retrieval/RAG or ask a business-level clarification.
- Do NOT invent action names or additional intents.
- Do NOT include markdown or commentary.

ACTION SPECS:
{{action_specs}}

TASKS (fill params for these indices):
{{tasks}}

USER REQUEST:
{{user_query}}

Notes:
- The USER REQUEST may include an "ATTACHMENTS (user context; pinned targets)" section (ref=att#N) and/or a "PINNED TARGETS (previously pinned; not current UI selection)" section (ref=target#N).
  - Attachments/targets may be missing id; use metadata/contentText instead.
  - When the user refers to a target indirectly ("buy it", "add this"), prefer identifiers/fields from target metadata/contentText for actionParams (e.g., id/sku/orderNumber) rather than inventing values.
  - If the classifier produced multiple ACTION intents to operate on multiple pinned targets, fill params for each intent using the corresponding target in order (first target for the first intent, etc.) unless the user explicitly specified otherwise.
  - If the selected action paramsSchema includes an array parameter marked [batchTargets] and multiple pinned targets exist:
    - Populate that array with one element per pinned target by default (unless the user narrowed scope to a single target).
    - Each array element must follow the item schema (object/fields) and use only identifiers/attributes present in the pinned targets metadata/contentText.
