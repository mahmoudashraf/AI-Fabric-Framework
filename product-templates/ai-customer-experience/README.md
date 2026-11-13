# AI Customer Experience Quality Defaults

Status: **published and selected on staging and production, 13 November 2026**.
The normal AI Customer Experience Kit setup inherits these defaults. An actual
customer UI run verified fresh Kit/Brain creation, both Engage channels, response
controls and branded preview, then removed the configuration-only fixture.
See `verification-support/ai-behaviour-01063/2026-11-13-generic-kit-default-adoption.md`.

This source-only builder prepares `mkp-template-document-knowledge-assistant`
1.4.0 from the reviewed published 1.3.0 template. Publication and public-starter
selection are separate explicit operations; source tests alone are not live proof.

The owner approved transferring four generic quality areas from dealership tuning:

- Consultative tone and Balanced answer length.
- Contextual next-step suggestions.
- Iterative READ planning, at most two iterations, with
  `RAG_IF_ACTIONS_INSUFFICIENT` cooperation.
- At least eight retrieval documents/8,000 context characters; READ planning
  context12,000 characters, per-action evidence8,000 characters and10 items.
  Existing larger evidence limits are preserved.

Managed instructions come from the actual behaviour catalogue, not copied
dealership prompts. Existing answer breadth is preserved exactly, including
custom instructions. Without an explicit profile, the catalogue's current
default is used; it is Grounded in the qualified catalogue. Contextual next
steps can use the existing additional model-generation call and normal usage
accounting. This is not public web access or a hidden response rewrite.

The builder does not change model selection, provider output-token limits,
similarity thresholds, source identities, credentials, permissions, approval,
action confirmation, shell branding, other orchestration modes or recovery/fanout
policy. Required DATA pins remain those of the current generic baseline. No
dealership policy, inventory schema, advertiser connection or demo credential is
copied. Existing customer drafts and releases are not background-updated.

```sh
node scripts/build-quality-template.mjs /private/published-template.json /private/behaviour-catalogue.json
node --test scripts/build-quality-template.test.mjs
```

Input may be a manifest or a matching published `{version: {..., manifest}}`
capture. Output is only the new manifest JSON. The builder performs no network
request, publication, customer mutation, indexing or provisioning.

The shared managed-profile resolver is also used by the dealership builder.
Its existing16 tests and the new9 generic tests pass, including change scope,
catalogue identity, breadth/model/threshold preservation, larger budgets,
malformed inputs and CLI wrapper handling. Test catalogue instructions are
synthetic and never publication inputs.

The selected new starter is `public-knowledge-quality-20261113`. It uses the
current published generic DATA1.3.0 dependency and already-qualified0.8.52 runtime.
The older selected starter referenced TEMPLATE1.2.0/DATA1.2.0 and runtime0.8.41;
these historical pins were not reused for new setup. No new framework release,
runtime image or customer infrastructure was created. Existing customer resources
and the dealership/Support starter selections remain unchanged.

For adoption, publish through the normal catalogue workflow, create a new public
starter using the current generic dependency pins and already-qualified current
runtime, then explicitly select it for new setup. Preserve other starter IDs,
funding/model limits and historical customer grants. Template defaults remain
editable bootstrap settings, not restrictions on later upgrades. Fresh Kit
inheritance and any actual answer-quality qualification must be reported
separately; a configuration-only fixture is not an AI-answer benchmark.
