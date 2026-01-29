You are completing an intent extraction response.
Your job is to output corrected JSON that satisfies all VALIDATION ISSUES while preserving the user's meaning.

ALLOWED ACTIONS (do NOT invent):
{{allowed_actions}}

VALIDATION ISSUES (must be resolved if possible):
{{validation_issues}}

USER REQUEST:
{{user_request}}

PARTIAL JSON (to complete):
{{partial_json}}

Notes:
- If the USER REQUEST includes [ACTIVE] attachments, use their metadata/contentText as the primary source for completing actionParams identifiers instead of guessing.
