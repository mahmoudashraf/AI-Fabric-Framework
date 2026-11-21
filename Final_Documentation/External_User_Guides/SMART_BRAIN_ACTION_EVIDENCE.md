# Action Evidence Preparation

Smart Brain -> AI capabilities -> edit an action -> Advanced evidence preparation.

Automatic preparation is the default from framework 0.8.58. New actions do not
need an `llmFacts` mapping. Publish and apply a compatible runtime to change the
running Brain; saving a draft alone does not update live behavior.

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

Record memory (`routingConfig.actions.<name>.response.pinned-targets-from-collection`)
is separate: it retains actual IDs for conversational follow-ups. It does not
grant access or make a vector collection, and is not required merely to expose
an action result to answer generation.

Neither preparation mode changes answer-breadth profiles, authentication,
ownership, document approval, configured confirmation or spending limits. General
advice belongs to the existing Grounded/Advisory/Exploratory answer profiles.
