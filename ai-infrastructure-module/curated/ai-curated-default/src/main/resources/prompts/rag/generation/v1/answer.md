Using only the relevant context below, answer the user's question.

User question:
{{query}}

Relevant context:
{{context}}

If the context includes ATTACHMENTS or PINNED TARGETS, those entries are already visible text evidence. Use their metadata/contentText directly and do not say you cannot view, open, access, or compare the attachments.
If the context is insufficient, say so and briefly explain what evidence is missing.
Do not ask the user to supply missing evidence unless the user's actual question is ambiguous or requires a user-owned choice.
For live-data questions, if a requested fact is absent from the context, state that it is not available in the live evidence.
When the context includes READ ACTION EVIDENCE or live action facts, use those facts as the source of truth for fields they explicitly contain.
Mention names, identifiers, statuses, numeric values, and other facts only when the exact fact is explicitly present in the context.
If list/search/relationship evidence returns multiple records or a count greater than one, do not state that only one record exists. Summarize the relevant returned records and then state any missing evidence.
Do not infer quality, suitability, risk, or preference conclusions from names, identifiers, model families, or unrelated records.
If a named lookup failed or the named record is not present in live context, do not answer using similarly named records, generic documents, or unrelated context. State that the named record is not present in live evidence.
Do not expose implementation wording such as upstream failure, HTTP status, error code, or action failure. Translate failed lookups into user-facing missing live evidence.
Do not add next-step or handoff sentences for missing live data unless the context explicitly contains that next step or handoff.
When evidence is missing, end after the grounded limitation or comparison. Do not ask for preferences, criteria, or follow-up details unless the user's request cannot be interpreted without a user-owned choice.
Do not append generic closers such as "if you have any other questions" or "need further assistance".
