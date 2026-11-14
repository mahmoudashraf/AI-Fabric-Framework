# Support 1.3.0 Source Candidates

14 November 2026: owner-approved selective defaults, **pending main review and
publication**. These files are not live marketplace versions, selected offers,
applied releases or new conversational-quality qualification. No live operation
or commit is part of this source-only change.

## Published Baseline

The private read-only inventory contains identical published `1.2.0` manifests
in staging and production for both families:

- Public `mkp-template-ai-support-workspace`:
  `mkv-doc231-2dd99be0db02766eabccc9f24675bfd4`.
- Internal `mkp-template-loomai-support-workspace`:
  `mkv-doc231-392c5a8368a9915a76497162aebb142b`.

`fixtures/published-support-quality-baseline.1.2.0.json` contains only those
manifest payloads, their canonical SHA-256 identities and the actual behaviour
catalogue captured for this work. It has no API authentication values. Managed
credential reference names and `${MANAGED_...}` placeholders are preserved, not
resolved. This is reproducible source evidence, not a request to republish history.

## Selective Changes

The offline builder reads the shared Node reader and canonical backend
`bootstrap/brain-quality-defaults.json`; it does not duplicate policy values.
Behaviour instruction text comes from the supplied actual catalogue, with
`consultative`, `grounded`, `balanced` and `contextual` selections.

- Pin the existing `gpt-5.4-mini` alias to `gpt-5.4-mini-2026-03-17`; supply a
  missing model and `2048` output-token cap. Other explicit models/caps survive.
- Supply missing generic intent guidance only. Existing Support prompts,
  including intent and answer generation, remain byte-for-byte unchanged.
- Supply missing knowledge overview and canonical bounded iterative READ/RAG
  cooperation. Raise prompt/evidence budgets only to canonical minima, retaining
  larger existing budgets and all unrelated action/source/time limits.
- Preserve explicit breadth, custom profile instructions and unrelated profile
  metadata. The captured baselines have no managed profiles to preserve.

For the actual `1.2.0` inputs, READ settings, overview and context budgets already
meet the policy. The complete diff is version, model snapshot, missing token cap
and the new managed profile block. All other contribution/domain fields, plugin
pins, install defaults and Support prompts are identical to the published inputs.

No experimental calculation/answer prompt or global similarity threshold is
inserted. Existing thresholds, permissions, confirmation, specialist activation,
human handover, fanout and semantic recovery are unchanged. In particular, the
baseline's existing one-space recovery/fanout remains historical configuration;
this builder neither introduces it when missing nor broadens it.

The builder's checks protect the reviewed input identity, executable profile
contract and finite evidence budgets during an offline transformation. They are
not new runtime validators of imperfect model answers or semantic decisions.

## Reproduction And Verification

The CLI accepts a raw published manifest or the existing read-only API envelope
`{ "version": { "status": "PUBLISHED", "pluginId": "...", "version": "1.2.0",
"manifest": {} } }`. Envelope metadata must match its manifest. Supply an actual
behaviour catalogue; no authentication or private operation helper is needed by
the builder. It prints a manifest and makes no network calls.

```sh
node product-templates/ai-support-workspace/scripts/build-quality-template.mjs <published-template.json> <behaviour-catalogue.json>
node --test product-templates/ai-support-workspace/scripts/*.test.mjs product-templates/ai-support-workspace/fixtures/service/server.test.mjs
git diff --check -- product-templates/ai-support-workspace
```

The regression suite reproduces both committed candidates and compares the full
manifest against its captured baseline with only the four approved changes. It
also exercises missing defaults, explicit custom choices, budgets, authority,
catalogue ambiguity and CLI envelope isolation. Local source tests are not hosted
quality or marketplace publication proof.

`catalog.json.entries[].currentSourceCandidate` identifies the current pending
candidate. The top-level `manifest` fields remain historical source references
for existing tooling, not claims about current live versions. Historical
`qualityCandidates`, `supportExperienceCandidates` and manifest files are untouched.
Main must review and use the normal marketplace lifecycle before claiming
publication or selecting any new offer. Existing Brains and releases are unchanged.
