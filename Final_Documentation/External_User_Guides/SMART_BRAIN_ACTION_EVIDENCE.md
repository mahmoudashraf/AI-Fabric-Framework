# Action Evidence Preparation

Smart Brain -> AI capabilities -> edit an action -> Advanced evidence preparation.

Automatic preparation is the default from framework 0.8.58. New actions do not
need an `llmFacts` mapping. Publish and apply a compatible runtime to change the
running Brain; saving a draft alone does not update live behavior.

Use framework 0.8.59 or newer for authoritative evidence selection across both
read-planning and post-action generation. It closes a legacy raw-result fallback
that could bypass prepared fields. Empty, absent or failed facts preparation
does not expand back to the raw result.

## Automatic

The connector first produces the approved action result. Its existing response
mapping can select/reshape upstream fields; when no mapping is configured, the
connector already uses the response body as its result. Keep that output narrow
when an API contains confidential fields.

Automatic evidence preserves nested objects and arrays from successful action
results within bounded size/depth limits. It does not read request context,
credentials or HTTP headers. Known structured credential fields and explicitly
sensitive parameter field names are omitted; this does not identify every secret
inside free text or every confidential business field. Failed action data is not
automatically forwarded. Actual action success/failure remains authoritative.

An omitted `llmFacts` uses Automatic. Explicit configuration is also supported:

```json
{"llmFacts":{"mode":"AUTOMATIC"}}
```

## Selected Fields

Use Advanced selected fields for sensitive, large or specially shaped output.
Enter direct result field paths, or edit the existing list/object projection JSON:

```json
{
  "mode": "SELECTED_FIELDS",
  "lists": [{
    "sourcePath": "products",
    "target": "records",
    "maxItems": 8,
    "includeFields": ["id", "name", "price"]
  }]
}
```

This object is `llmFacts` on the action definition, not a route response setting.
The existing selected-field behavior uses a nested `data` object as its default
root when present; `rootPath` can explicitly select another root. Automatic
preserves the whole approved result instead of unwrapping it.
An empty selected projection exposes no result data. It never falls back to the
full result. Existing projections remain selected even if their mode was omitted.
Switching modes retains saved selection rules; Automatic ignores them until
Selected fields is chosen again. Inspect and save the normal shared draft.

These settings control model evidence, not the application's original
customer-visible `ActionResult`. Response mapping and the approved output
contract must also exclude fields the customer should not receive. Selecting
fields for the model alone is not a privacy filter for the application's API.

Record memory (`routingConfig.actions.<name>.response.pinned-targets-from-collection`)
is separate: it retains actual IDs for conversational follow-ups. It does not
grant access or make a vector collection, and is not required merely to expose
an action result to answer generation.

## Explicit UI Attachments

Owner decision, 2026-11-22: when a user explicitly attaches an item in the widget,
its full UI-provided item data is approved for AI context. This can include fields
omitted from the action's Selected projection. Keep nested values, numbers, false
and zero rather than reducing the record to familiar product fields.

Automatic/Selected still controls action evidence included automatically. A card
appearing on screen is not consent to attach it. Selecting one item must not
include neighboring records or fetch fields that were never supplied to the UI.
Do not use model Selected fields as a privacy filter for customer-visible data.

Attachments are user-provided context, not verified business facts or permission
to execute an action. Credentials, authorization headers and private execution
context remain outside item content; existing content bounds remain. Plan 010.70
tracks implementation and rollout. Source fixes are implemented and locally
tested; live adoption is not yet qualified at this checkpoint.

Neither preparation mode changes answer-breadth profiles, authentication,
ownership, document approval, configured confirmation or spending limits. General
advice belongs to the existing Grounded/Advisory/Exploratory answer profiles.
