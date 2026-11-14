# AI Customer Experience Quality Defaults

Status: **Owner-approved selective subset, 14 November 2026; source candidate
1.5.0, publication not yet claimed.** Fixture revision 3 is
`OWNER_APPROVED_SELECTIVE_DEFAULTS_20261114`. This approval supersedes the held
status only for the subset below, not for the earlier experimental bundle or
its quality claims. The last recorded selected baseline is 1.4.0 through
`public-knowledge-quality-20261113`; this source change does not publish or
select its replacement and makes no fresh live-state claim.

This source-only builder prepares `mkp-template-document-knowledge-assistant`
1.5.0 from the reviewed published 1.4.0 template. Publication and public-starter
selection are separate explicit operations; source tests alone are not live proof.

The canonical policy is
`Platfrom/backend/src/main/resources/bootstrap/brain-quality-defaults.json`.
`product-templates/shared/conversational-quality-defaults.mjs` reads it directly;
the local fixture records only template and profile-contract identity, version
and approval status. Do not maintain a second copy of the policy in the fixture.

The owner-approved selective defaults are:

- Consultative tone, Grounded breadth, Balanced answer length and Contextual
  next steps, resolved to instructions from the supplied behaviour catalogue.
- Missing OpenAI model/output-token settings: `gpt-5.4-mini-2026-03-17` and a
  2,048-token default cap. Explicit existing model and token limits are retained.
- Missing knowledge-base overview: enabled. An explicit false remains false.
- Missing generic intent prompt: the `intentExtractionPrompt` from
  `bootstrap/prompts/default-prompt-config.json`, then two newlines and the
  canonical `intentGuidance`. These are the same source resources used for
  default/new Brain composition; no dealership-specific rules are copied.
- Iterative READ planning, at most two iterations, with
  `RAG_IF_ACTIONS_INSUFFICIENT` cooperation.
- At least eight retrieval documents/8,000 context characters; READ planning
  context 12,000 characters, per-action evidence 8,000 characters and 10 items.
  Existing larger evidence limits are preserved.

Managed instructions come from the actual behaviour catalogue, not copied
dealership prompts. Existing answer breadth and all explicitly Custom profiles
are preserved exactly, including their instructions. Missing breadth uses the
canonical Grounded selection, not a dealership or catalogue default. Contextual next
steps can use the existing additional model-generation call and normal usage
accounting. This is not public web access or a hidden response rewrite.

No similarity threshold or `answerGenerationPrompt` is supplied by this subset.
The earlier calculation/latest-question answer prompt is removed from the
candidate defaults. Existing thresholds and custom prompts are preserved, not
rewritten. Corpora/providers still require their own retrieval qualification;
Harbor's relevant policy scoring about 0.36 does not establish a global threshold.

**Approval is not a blanket quality guarantee.** The earlier bundle did not
meet its two-run qualification criterion: v6 scored 3/6 and 4/6, v7 4/6 and 5/6,
v8 3/6 and 5/6, and final v9 4/6 and 5/6. Even with the
generic calculation prompt, the model sometimes calls GBP499 eligible for a
GBP500 free-delivery threshold. An additional exact-boundary query also failed,
and another asked for a technical knowledge domain. Earlier failures are not
discarded or retried into a pass. These trial failures remain failures; this
narrower approval does not reclassify them. See
`verification-support/harbor-brain-creation/2026-11-14-quality-defaults.md`.
The subsequent full GPT-5.4 model-only comparison also scored4/6 and5/6 and
retained the contradictory eligibility claim. It improved explicit boundary
queries but was slower. That comparison did not qualify a universal stronger
model or the earlier bundle;
see `verification-support/harbor-brain-creation/2026-11-14-stronger-model-comparison.md`.

The builder does not replace explicit provider/output-token choices, source identities,
credentials, permissions, approval, action confirmation, storage, shell branding, other
orchestration modes or recovery/fanout policy. Required DATA pins remain those
of the current generic baseline. No
dealership policy, inventory schema, advertiser connection or demo credential is
copied. No fanout, recovery policy, source, plugin type or defaults service is
introduced. There is no lexical/semantic matching or hidden answer rewrite.
Existing customer drafts and releases are not background-updated. Backend Java
default/new Brain creation and all publication, selection and deployment belong
to the separate integration work, not this source-only builder.

```sh
node product-templates/ai-customer-experience/scripts/build-quality-template.mjs /private/published-template.json /private/behaviour-catalogue.json
node --test product-templates/shared/conversational-quality-defaults.test.mjs product-templates/ai-customer-experience/scripts/build-quality-template.test.mjs product-templates/dealership-experience/scripts/build-quality-template.test.mjs
```

Input may be a manifest or a matching published `{version: {..., manifest}}`
capture. Output is only the new manifest JSON. The builder performs no network
request, publication, customer mutation, indexing or provisioning.

Tests cover canonical-resource consistency, exact bootstrap intent composition,
change scope, catalogue identity, custom prompt/profile/model/token/threshold
preservation, minimum and larger budgets, malformed inputs and CLI captures.
The dealership builder and its shared managed-profile resolver are unchanged;
its existing tests are included as regressions. Test catalogue instructions are
synthetic and never publication inputs. Run these commands from the repo root.

The recorded 1.4.0 starter uses generic DATA 1.3.0 and framework 0.8.52.
The earlier fresh Kit/Brain UI check, Engage channels, response controls and
branded preview are recorded in
`verification-support/ai-behaviour-01063/2026-11-13-generic-kit-default-adoption.md`;
that configuration-only fixture was removed. This is historical baseline proof,
not 1.5.0 inheritance or an answer-quality benchmark.
The older selected starter referenced TEMPLATE1.2.0/DATA1.2.0 and runtime0.8.41;
these historical pins were not reused for new setup. No new framework release,
runtime image or customer infrastructure was created by that 13 November rollout.
The separate Harbor qualification uses a narrow Platform connector memory fix
against the unchanged framework 0.8.52. Existing customer resources
and the dealership/Support starter selections remain unchanged.

Publication is left to the integration owner. For adoption, use the normal
catalogue workflow, create a new public
starter using the current generic dependency pins and already-qualified current
runtime, then explicitly select it for new setup. Preserve other starter IDs,
funding/model limits and historical customer grants. Template defaults remain
editable bootstrap settings, not restrictions on later upgrades. Fresh Kit
inheritance and any actual answer-quality qualification must be reported
separately; a configuration-only fixture is not an AI-answer benchmark.
