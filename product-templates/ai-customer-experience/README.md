# AI Customer Experience Quality Defaults

Status: **1.4.0 published and selected; 1.5.0 held, not quality-qualified,
14 November 2026**. The normal AI Customer Experience Kit setup inherits the
selected published defaults, not the unpublished candidate. An actual
customer UI run verified fresh Kit/Brain creation, both Engage channels, response
controls and branded preview, then removed the configuration-only fixture.
See `verification-support/ai-behaviour-01063/2026-11-13-generic-kit-default-adoption.md`.

This source-only builder prepares `mkp-template-document-knowledge-assistant`
1.5.0 from the reviewed published 1.4.0 template. Publication and public-starter
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

The next candidate also supplies missing model/intent/recall defaults: GPT-5.4-mini,
knowledge-base overview, generic follow-up/information-vs-execution guidance and
a 0.3 retrieval threshold, plus latest-question/threshold-calculation answer
guidance. Explicit existing model, prompt, overview and threshold
choices are preserved. The threshold is a starting recall setting, not a universal
confidence measure: Harbor's approved relevant policy scores about 0.36 and was
excluded at both 0.5 and 0.4. Other corpora/providers need their own qualification.
Existing tuned dealership thresholds are not silently lowered.

**Do not publish/select the current candidate.** Harbor's final original-six
runs were 4/6 and 5/6, below the two-run qualification criterion. Even with the
generic calculation prompt, the model sometimes calls GBP499 eligible for a
GBP500 free-delivery threshold. An additional exact-boundary query also failed,
and another asked for a technical knowledge domain. Earlier failures are not
discarded or retried into a pass. The selected 1.4.0 defaults already contain the
four generic dealership-derived areas above; they remain selected. See
`verification-support/harbor-brain-creation/2026-11-14-quality-defaults.md`.
The subsequent full GPT-5.4 model-only comparison also scored4/6 and5/6 and
retained the contradictory eligibility claim. It improved explicit boundary
queries but was slower. No model/default promotion is justified by that check;
see `verification-support/harbor-brain-creation/2026-11-14-stronger-model-comparison.md`.

The builder does not change provider output-token limits, source identities,
credentials, permissions, approval, action confirmation, shell branding, other
orchestration modes or recovery/fanout policy. Required DATA pins remain those
of the current generic baseline. No
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
Its existing16 tests and the10 generic tests pass, including change scope,
catalogue identity, breadth/model/threshold preservation, larger budgets,
malformed inputs and CLI wrapper handling. Test catalogue instructions are
synthetic and never publication inputs.

The unchanged selected starter is
`public-knowledge-quality-20261113`. It uses the
current published generic DATA1.3.0 dependency and already-qualified0.8.52 runtime.
The older selected starter referenced TEMPLATE1.2.0/DATA1.2.0 and runtime0.8.41;
these historical pins were not reused for new setup. No new framework release,
runtime image or customer infrastructure was created by that 13 November rollout.
The separate Harbor qualification uses a narrow Platform connector memory fix
against the unchanged framework 0.8.52. Existing customer resources
and the dealership/Support starter selections remain unchanged.

For adoption, publish through the normal catalogue workflow, create a new public
starter using the current generic dependency pins and already-qualified current
runtime, then explicitly select it for new setup. Preserve other starter IDs,
funding/model limits and historical customer grants. Template defaults remain
editable bootstrap settings, not restrictions on later upgrades. Fresh Kit
inheritance and any actual answer-quality qualification must be reported
separately; a configuration-only fixture is not an AI-answer benchmark.
