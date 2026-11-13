# Dealership Quality Bootstrap Candidates

Status: **candidate only, pending Main's live qualification and publication**.

This source-only builder applies the owner-approved Northfield quality settings
as bootstrap defaults to exactly three existing TEMPLATE identities:

- `mkp-template-simulated-dealership-experience-v1`: `1.14.0` -> `1.15.0`.
- `mkp-template-dealership-external-systems-v1`: `1.4.0` -> `1.5.0`.
- `mkp-template-dealership-external-systems-managed-inbox-v1`: `1.10.0` -> `1.11.0`.

These are reviewed baseline versions, not a claim about the current live
catalogue. Main must verify each current version before publication. A mismatch
stops the builder; it does not choose a different version automatically.

## Inputs And Output

`scripts/build-quality-template.mjs` accepts two saved JSON inputs:

1. The approved current TEMPLATE manifest, or its existing `{version: {...,
   status: "PUBLISHED", manifest: {...}}}` API capture. Wrapper identity/version
   must match the manifest. Do not supply a customer draft.
2. The existing API behaviour catalogue returned by
   `GET /api/deployment-ai-behaviour-profiles`.

From this directory:

```sh
node scripts/build-quality-template.mjs /private/current-template.json /private/behaviour-catalogue.json
node --test scripts/build-quality-template.test.mjs
```

The CLI emits **only the new manifest JSON on stdout**. It never overwrites the
input, publishes a version, updates a draft, calls the network or reads auth
material. Save its candidate output separately through the normal review process.
The pure export is `buildDealershipQualityTemplate(manifest, catalogue)`.

## Deliberately Narrow Changes

- Managed behaviour: Consultative / Advisory / Balanced / Contextual, revision 1.
  Exact IDs and instructions come from the supplied API catalogue. Production
  instruction strings are not copied into this builder or from Northfield's draft.
- Executor only: knowledge overview, bounded two-space RAG fan-out, explicitly
  allowed missing-space recovery, iterative READ planning and its approved limits.
- Prompt limits: eight context documents and 8,000 context characters.
- The manifest receives the proposed new version; the historical input is retained.

Every other manifest field is cloned unchanged: existing models/provider choices,
URLs/references, security, source/plugin contributions, action confirmation,
orchestrator modes, specialist configuration and presentation. No customer
credentials or customer-draft inputs are imported. Existing fields in the approved
manifest are preserved, not sanitized or replaced. Review captured input before
sharing generated output. This is not a credential scrubber.

The fixture enables a named, bounded recovery policy; it does not authorize
searching arbitrary sources or force a model-selected source/action. The normal
runtime authorization, source allowlist and action execution contracts still apply.
Contextual suggestions may use the existing additional suggestion-generation call.

## Qualification And Adoption

Node tests cover exact change scope, deep preservation, input immutability,
catalogue-driven instructions, JSON managed-profile round-trip, invalid contracts,
ambiguous IDs/revisions and CLI behaviour. `behaviour-catalogue.test.json` has
**synthetic TEST ONLY instructions** and is not a publication input. These checks
are not live model-quality, server compilation, runtime or customer-journey proof.

Main must review candidate diffs, run existing manifest/profile validation, prove
the live template setup/quality journey and then publish through normal versioning.
No candidate is registered as the recommended production default by these files.

After qualified publication, new deployments can start from the template defaults.
Existing deployments may adopt them by an **explicit template upgrade/reset** into
the editable draft, followed by review and normal publish/apply. Routine template
sync must not overwrite customer custom edits, credentials, bindings or an active
release. Do not turn this offline builder into a background reconciliation writer.
