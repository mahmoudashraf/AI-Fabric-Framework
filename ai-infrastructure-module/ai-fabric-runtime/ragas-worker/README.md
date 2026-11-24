# Managed Ragas Worker

Internal implementation for 010.73, not another customer service or an engine
selector. The serving default remains the existing evaluator until reviewed
release qualification adopts Ragas. No custom-judge fallback runs when Ragas is
explicitly selected but unavailable.

## Reproducible Installation

- Python 3.11, Ragas 0.4.4. `uv.lock` fixes resolution;
  `requirements.lock` fixes every installed dependency and package hashes.
- Update deliberately with `uv lock`, then regenerate the hash-checked export:
  `uv export --frozen --format requirements-txt --no-dev --no-emit-project --output-file requirements.lock`.
- The runtime Dockerfile bundles this worker and its private virtual environment.
  The `runtime-base` target qualifies Python/native dependencies without needing
  the not-yet-published framework candidate to build the Java application.
- Hash-pinned Python/JRE base images and `pip --require-hashes --no-deps`
  installation prevent an extra dependency from being resolved at startup.

## Execution And Boundaries

Java `RagasWorker` accepts an already-authorized `RagEvaluationSample` and a
parent-owned inference bridge. It never accepts executable code, a provider URL,
credentials or a customer-chosen command. Standard input/output carry only
bounded JSON-line evaluation/generate/embed/result messages. Invalid protocol,
worker failure, cancellation or model failure does not manufacture a score.

The subprocess environment is cleared. One worker may run at a time; frames are
at most 1 MiB; a case admits at most 24 metric inference/embedding calls and
110 seconds in the document workflow. Actual provider/funding services admit and
account for every call on the existing evaluation worker thread. An answer call
is separate, making the displayed per-question ceiling 25 for this engine.
Unknown native embedding token usage is marked unavailable, not zero.

Linux workers additionally limit CPU time, virtual address space (2 GiB, not a
RAM reservation), open descriptors and core dumps. A libseccomp filter denies
Internet socket creation and endpoint connection before importing Ragas;
Python audit hooks provide a second guard. No telemetry, model download or
direct provider SDK connection is enabled. Missing isolation support fails the
worker instead of silently weakening it. The Java application's normal network
access is unchanged. Shutdown terminates an active child.

## Metrics And History

- `faithfulness`: real Ragas `Faithfulness` atomic-claim procedure.
- `relevance`: real `AnswerRelevancy`, strictness 3, using the runtime embedding
  provider through the same bridge.
- `contextPrecision` and `contextRecall`: real reference-based Ragas metrics.
- `referenceAgreement`: real Ragas `FactualCorrectness` F1 with low atomicity
  and coverage; it compares with the supplied reference, not independent truth.
  Missing references/excerpts remain `NOT_SCORED`; synthetic/unreviewed references
  are still labeled by the existing evaluation run.
- Engine/rubric identity is `ragas` / `ragas-0.4.4-v1`. Historical
  `structured-model-judge` records retain their original identity and scores.
- Same owned-source/target history can survive a runtime release; the original
  release ID remains provenance. Customer/deployment/target/source checks remain.
  UI previous-score hints require an exact experiment fingerprint for the source
  version, release, saved questions/options, indexed processing, engine/rubric
  and configured models, plus matching actual model receipts. Missing/legacy
  provenance or incomplete runs are not presented as comparable.

Document Evaluate runs one explicitly selected variant: saved search settings,
vector-only baseline, or saved search without learned reranking. Baselines keep
the same owner/source/version filters and configured finite bounds; they never
change the Brain or enable a provider. Non-default variants require the runtime's
declared preparation capability, so an older runtime cannot silently execute a
different experiment. Retrieval diagnostics and actually evaluated excerpts are
retained. These source-level controls do not complete the mixed/entity corpus
comparison or human-calibration work in 010.73.

Offline tests execute the installed Ragas implementation with scripted model
outputs/embeddings. They prove protocol and metric execution, not natural-query
quality or agreement with a human-reviewed reference set. Production adoption,
paid-model calibration and complete document/entity/mixed comparisons remain
separate release gates in 010.73.

Primary API references: [Ragas LLM adapters](https://docs.ragas.io/en/stable/references/llms/),
[Faithfulness](https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/faithfulness/),
[Factual correctness](https://docs.ragas.io/en/stable/concepts/metrics/available_metrics/factual_correctness/),
[libseccomp public API](https://github.com/seccomp/libseccomp/blob/main/include/seccomp.h.in).
