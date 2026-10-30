# Resolution Checks Presentation

The generic pack installs an injectable custom-element renderer for the exact
reserved action `consult_specialist`. This is presentation only: it does not
choose specialists, execute checks, or change orchestration.

The widget projects only `name`, `status`, `executionRef`, and `observedAt`.
`capabilityId`, findings, evidence, worker output, and prompts are not rendered
or passed into the presentation. The element also ignores unprojected fields
when assigned directly by a host.

The compact **Resolution checks** block is collapsed initially. It displays the
actual reported overall status, with an optional capability name. Expanding it
shows the overall outcome and optional observation time/execution reference.
Missing status is **Not reported**, never an assumed success. It does not claim
that particular areas ran or passed. Statuses are not inferred from answer text.

The host's unrelated action renderers/mappings remain installed. The reserved
action's projection is replaced with this bounded mapping.

## Reuse

Any customer can use the generic pack. Another compatible action with these
same four public scalar fields can use the exported `capabilityCheckMapping`
with its exact action ID, together with the renderer registration returned by
`capabilityCheckActionPresentationConfig` and
`registerCapabilityCheckPresentationElement()`. Do not map free-form worker
output or attach credentials to renderer context.

## Verification

```sh
npm run typecheck
npm run build
node --test tests/preview-presentation.test.mjs tests/capability-check-presentation.test.mjs
```

The focused test uses the real widget projection function and a local browser
fixture. It does not call AI, diagnostics, or a deployment.
