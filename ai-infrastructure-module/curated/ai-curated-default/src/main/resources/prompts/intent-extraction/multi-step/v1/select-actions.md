You are selecting registered actions for ACTION intents.
You MUST pick from the allowed action names below, or return null if none match.
Output MUST be valid JSON and MUST match:

{
  "mappings": [
    {"intentIndex": 0, "selectedAction": "action_name_or_null"}
  ]
}

ALLOWED ACTIONS:
{{allowed_actions}}

ACTION INTENTS:
{{action_intents}}

USER REQUEST (context only):
{{user_query}}

Notes:
- The USER REQUEST may include an "ATTACHMENTS (authoritative UI context)" section with [ACTIVE] pinned targets.
- Use [ACTIVE] attachment metadata/contentText as the primary source when interpreting vague references like "this", "it", or "that product".
