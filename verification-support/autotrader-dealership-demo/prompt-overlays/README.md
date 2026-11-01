# Deployment Prompt Experiments

## Internal Source Selection, 1 November 2026

`internal-source-selection.txt` is a domain-neutral guidance candidate for the
current dealership's ordinary `intentExtractionPrompt` slot. It directs the
model to resolve technical knowledge-source choices internally and clarify
only genuinely missing business details. It contains no phrase-based runtime
router, automatic query rewrite, source-authority override or response filter.

It is being qualified alongside explicitly configured bounded missing-source
recovery, not as a prompt-only guarantee. The framework itself can produce a
technical-domain clarification after READ planning promotes a request to RAG;
guidance in a generation prompt cannot suppress that earlier terminal result.
See the [dated correction record](../../paul-rigby-first-client/2026-11-01-domain-routing-correction.md)
for actual configuration, test and rollout status before using it elsewhere.
The staged candidate removed the technical-domain question in two fresh
four-question runs but did not recover useful contextual answers. It is not a
qualified production fix; production's runtime assignment remains unchanged.

## Historical Booking-Information Experiments

These are rejected, authoring-time experiments, not recommended deployment
defaults or installed production fixes. The original qualified prompt is
`ver-21e8391f`; the dated quality review records actual restoration evidence.

- `action-information-evidence-routing.txt`: tried the existing typed
  INFORMATION `directAnswer` contract. The enabled read planner could promote
  the turn back to RAG and produce a missing-domain clarification.
- `public-action-reference-guidance.txt`: tried explicit workflow reference data
  in the answer-generation slot. Some answers improved, but others omitted the
  fields or mixed two workflows. Do not promote this candidate.

`../public-action-reference.mjs` projects schema documentation for experiments.
It is not runtime authorization, a dynamic capability catalogue, semantic
selection, or indexed business-policy evidence. Anonymous actions are included;
describing an authenticated workflow requires an explicit author-selected exact
action ID. That does not permit anonymous execution. Internal/system/secret
parameters, hidden inputs, connector routes, defaults and actual values are not
projected. The source hash and whole-item bounds make reference drift visible;
regenerate and requalify after any source configuration change.

Run focused offline projection checks with:

```sh
node --test verification-support/autotrader-dealership-demo/public-action-reference.test.mjs
```

The focused live corpus is `../booking-information.scenarios.json`. Use the
existing live-workspace runner with a fresh private output directory, reviewed
installation/scope fixtures, and `--scenarios` pointing to that corpus. It uses
real published widget assets and live anonymous runtime routes, with business
writes blocked. A manually hosted fixture is not managed-assignment acceptance.
Review answers manually against the actual frozen action schemas and actual
indexed source documents; HTTP200 and `success=true` are not semantic passes.

Do not copy historical rich policy fixtures over the current customer S3
policies. The latter specify a30-minute test drive, not45minutes, and do not
state age/licence-history requirements or reservation prices/hold periods.
