# 010.37 Configuration Assistant Verification

This directory contains reproducible qualification sources, not production
credentials or generated customer data. Raw hosted receipts, imported examples,
ledger responses and screenshots are retained in the operator's private evidence
directory. A healthy deployment is not evidence that every model answer is correct.

## Current Delivery

Implemented and qualified on staging and production on 10 November 2026. The
latest [page-aware UX qualification](2026-11-10-page-aware-ux.md) records current
backend/Console images, document-only preparation and ordinary follow-ups.
The [bounded validation-feedback qualification](2026-11-10-validation-feedback-loop.md)
records the correction-policy extension and retained private inference image.
The earlier [hosted qualification](2026-11-10-hosted-qualification.md) records the
original customer/staff journeys, costs, cleanup and accepted limitations.
Do not infer a later deployment from a source commit. The canonical plan is
`010_37_PLATFORM_DEPLOYMENT_CONFIGURATION_ASSISTANT_IMPLEMENTATION_PLAN.md`.

The implementation uses existing Brain, private Data plugin and Engage save
services. The dedicated shared inference profile has no mutation credentials,
business action tools, vector services or customer business database. It consumes
released AI Fabric 0.8.46; no framework change/release is part of this work.

## Reproducible Checks

- `run-framework-contract-probe.mjs`: 17 offline released-library contract checks.
  Provider output is deliberately mocked. This is not paid inference or hosted UI.
- `run-generated-action-probe.mjs`: preserves the first actual invalid model
  catalogue as a negative control, loads a later untouched candidate with the
  released action loader, and optionally executes only its READ route against
  a temporary localhost mock. `--self-test` uses a labelled synthetic positive
  fixture and must not be reported as a hosted-model pass.
- `import-hosted.mjs --self-test`: local harness checks. Explicit `--run` uses
  a genuine customer session against the selected environment. It imports and
  inspects inert documentation, never invokes its operations or a model.
- `hosted-turn.mjs`: one explicitly selected real-model turn against a disposable
  authorized resource. Requires `AUTHORING_PRIVATE_STATE`, `--environment`,
  `--case` and `--command`. It refuses to overwrite evidence or retry a failed
  turn automatically. An explicit `validationFeedback` policy in the command
  enables the server's bounded corrections; omitted policy makes one inference.
  JSON cases under `cases/` contain fictional data.

## Retained Failures And Corrections

The first hosted action response saved its draft but used prose where released
0.8.46 requires a `resultPresentationHint` enum. Platform's existing shallow
validation did not detect that compatibility error. The negative loader proof
is retained; the catalogue now supplies actual enum and route shapes. This was
not fixed by changing or normalizing model output.

The first behaviour response used string profile IDs instead of the existing
profile objects. Exact registered instructions and a machine-readable example
were added to model context. A subsequent paid turn saved the requested profiles.

An early nested action edit was rejected by an unnecessarily broad array-path
restriction. Exact existing numeric-index traversal now permits object-field
changes; whole item replacement/deletion still uses named operations. Tests
cover bounds, credentials, scope and undo. Unused `key` metadata on SET/REMOVE is
preserved in the model response but does not control authoritative diff/undo.

One paid multi-turn response answered an earlier label request rather than its
current two-action request. Released framework ordering was correct. The runtime
now places sanitized context in a separate USER message and the exact current
query last, after native history. An actual released prompt-mapper test verifies
message contents and order. This is guidance, not an intent matcher or retry.

Data definition drafts correctly retained visible validation findings for
unsupported Markdown indexing and invented empty JSON pointers. The actual
existing findings and supported indexing contract are now supplied to inference.
Assistant PDF/API-document import is separate from customer document indexing;
the existing bound-S3 plugin contract currently accepts `.txt` and `.json` only.

An early failed save discarded paid-generation details from the application
turn while the financial ledger remained authoritative. Failed turns now retain
safe proposal/usage evidence and explicitly do not claim a save. The UI also
keeps manual editors mounted across assistant refresh/task state, preventing
unsaved fields from being reset by panel initialization.

No semantic text matching, canned replacement, automatic search-all, hidden
model retry, automatic publish/apply/index or business write is used in these
corrections. Known credential redaction and formal JSON/configuration identities
are trust/serialization controls, not semantic routing.

## CI Exceptions

The initial document-storage browser harness failed after the shared controlled
React export was introduced. Explicit React deduplication fixed the harness;
the subsequent storage workflow `42740706173` passed. Full production UI builds
also passed.

The separate initial environment-quota workflow `42737757366` had one
Docker/Testcontainers environment error (218 tests, zero assertion failures,
one error, six skipped). It is not a green gate and is not evidence of a product
assertion failure. No capacity, draining or quota redesign was added to this task.

## Operational Boundary

The shared authoring allocation uses existing provider accounting, not customer
chat funding. Its finite internal allocation must be renewed normally; upgrades
must preserve internal schema and accounting history. No startup initialization
or reset issues a new allowance. Disable the server-owned assistant flag to
stop generation without removing manual forms or legitimate saved drafts.
