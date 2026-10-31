# LoomAI Customer Support Knowledge Library

Content revision: `2026-10-31.1`

This is real, customer-facing LoomAI help content, not the fictional Acme or
dealership business policies used by demonstrations. Its claims are reviewed
against the actual Platform, Engage and Desk contracts. Source review is not
proof that every customer has a feature enabled or that a live diagnostic ran.

## Contents

The 13 guides cover products/getting started, accounts/sign-in, deployment
configuration/releases, plugins/connections, Engage website/standalone setup,
custom domains, document indexing, knowledge freshness, actions/confirmation,
specialists/resolution checks, Desk/human handover, integration support/diagnostic
consent, and the dealership experience.

Read the `.txt` files in `documents/loomai-support/`. These are the only inputs
to the new support index. Their practical steps, troubleshooting advice and
limits are intended to help real customers, rather than describe how our
implementation task was executed. No deployment IDs, customer transcripts,
private handoffs, operational credentials or fictional service promises belong
in this library.

`review/support-library.json` declares the exact input set. The accompanying
`review/provenance-*.json` files identify source code and supported claims per
guide. They are maintainer evidence, not knowledge documents. Keep them out of
the indexed source prefix.

## Prepare For Storage

Run from the repository root:

```sh
node product-templates/ai-support-workspace/scripts/prepare-support-library.mjs
node --test product-templates/ai-support-workspace/scripts/prepare-support-library.test.mjs
node product-templates/ai-support-workspace/scripts/prepare-support-library.mjs \
  --output-dir output/support-01054/knowledge-library-2026-10-31-1
```

The output directory must be new. The command verifies the declared document
set and review references, then copies exact guide bytes and produces SHA-256
checksums. It does not contact S3, change Platform configuration, submit indexing
work or query a provider. The delivery manifest explicitly says not indexed.

Upload only the manifest-listed guide objects under each environment's isolated
storage root, for example:

```text
<internal-staging-root>/guides/<guide-filename>.txt
<internal-production-root>/guides/<guide-filename>.txt
```

Bind the corresponding `guides/` prefix, not the bucket root. Do not recursively
upload repository or review directories. Store the delivery manifest outside
the reader's indexed prefix or keep it locally as deployment evidence. Readers
must have no access to the other environment or the fictional Acme source set.
Source deletion is not authorized by this package.

The new storage is being prepared separately through Required for production.
Its bucket, region, prefixes and private credential-file paths must come from
that task's verified output. No such resources or access have been created by
the library-preparation commands.

## Index And Verify

1. Create the normal owned S3-compatible binding for the exact deployment target
   with its separately scoped read-only credentials. Report object-versioning
   support truthfully; document lifecycle versions and bucket versioning are not
   the same feature.
2. Discover the new source objects through the installed document DATA plugin.
3. Register each source and confirm its audience/source approval. Then preview
   its extracted chunks and confirm the correct customer-safe content and scope.
4. Submit indexing through the normal approved document workflow.
5. Reconcile actual indexing work and inspect source/active-version status.
   An upload or accepted queue request is not completion evidence.
6. Retrieve from the target's approved knowledge source, then run the prepared
   answer-quality questions. Inspect returned sources and any READ receipts.
7. Preserve the working prior index until the new source set and retrieval are
   verified. Retire duplicate old derived guides only through the normal
   authorized source lifecycle. Never delete source files as a cleanup shortcut.

The four existing `documents/loomai/` guides and three `documents/acme-cloud/`
guides remain historical staging fixtures. Preparing this library does not
change their registration or the four/three-source hosted evidence recorded in
010.54. Do not report 13 active sources until the new deployment index actually
confirms them. Do not mix the fictional Acme policy library into real LoomAI
customer support knowledge.

## Answer Quality And Maintenance

`review/support-library-quality-scenarios.json` contains 18 realistic questions
and human-review expectations, including compound installation/policy questions,
authorization, unknown action outcomes and human transitions. They are prepared
test inputs, not executed results or exact-string validators. No query-term
routing or hidden recovery is introduced by this content package.

For each revision, inspect the owning code/UI contracts, update the reviewed
date and provenance, regenerate a new delivery directory, and qualify retrieval
after normal indexing. Never invent service levels, refunds, data residency,
retention periods, automatic repairs or partner API access. Missing live
information should remain visible, with a useful next check or human-support
option. Documentation cannot substitute for current authorized observations.
