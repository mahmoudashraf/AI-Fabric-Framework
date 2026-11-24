# Improve Your Brain's Search

This guide describes the 010.73 implementation. Check the dated verification
record for rollout status; saving a setting is not proof that it is live.

## Choose How Information Is Found

Open **Smart Brains -> your Brain -> Knowledge -> Search settings**.

- **Keep existing search** retains the previous search behavior.
- **Meaning** finds information related to the question's meaning.
- **Words and meaning** combines exact terms with semantic matches. It requires
  a supported Weaviate source with a ready text index. Unsupported services are
  not silently treated as hybrid-capable.

The evidence count and candidate limits bound the work. Advanced settings retain
the detailed context, per-leg, timing and failure allowances. Saving preserves
unrelated settings and does not reindex documents. Review, publish and apply the
normal Brain update before expecting a live change.

When first enabling a profile, the evidence count and context allowance inherit
the Brain's existing answer settings. Explicit search overrides take priority;
the form does not silently shrink a larger saved allowance to an example value.
Applying requires a compatible reviewed runtime, not merely a saved JSON field.

## Optional Reranking

**Prioritize relevant results** uses a separate model to reorder the information
already found. It is off by default. It does not grant access to more documents,
search the public internet, execute actions or guarantee correct answers.

1. Add a **Cohere reranking key** under the Brain's **Keys & secrets**.
2. Select the supported reranking model in Search settings.
3. Explicitly approve sending the question and selected, authorized excerpts to
   Cohere. The assistant cannot give this approval on your behalf.
4. Arrange a separate approved reranking allowance. An AI chat allowance is not
   automatically a reranking allowance.
5. Review and apply the Brain update. Inspect the applied environment's status,
   accounted usage and expiry. Saving a key or draft alone does not activate it.

Accounted usage can include reservations for calls whose final billing outcome
is unknown; it is not an invoice. Missing usage is unavailable, not zero.
Authorization and funding failures are not hidden behind a fallback. An explicit
availability-only fallback can retain the original authorized search order.

### Turn It Off

Turn off **Prioritize relevant results**, or choose **Keep existing search** to
disable the custom search profile too. Review, publish and apply the update.
A saved draft does not stop a running feature. The applied update removes
inactive Platform-owned settings and the reranking key from the runtime, while
keeping your saved choices and the key in your private Console secret store.
Usage history and unrelated settings are retained. Revoking the provider's key
is a separate operation at the provider.

## Test A Document

Under **Knowledge -> Documents**, select a registered source and open **Evaluate**.
Use clear questions and check any expected answer against the actual document.
AI-generated references remain unreviewed until a person reviews them.

The source must also allow authorized Console testing. For a Data plugin you
own, this is the optional **Allow tests in Console** setting under **Freshness &
access** in **My plugins**. It does not change website visitor access or remove
customer ownership and approved-document checks. A saved definition does not
update installed versions: create the new version and apply the normal Brain
update. For a publisher-managed source, ask its owner to approve this access;
do not substitute an anonymous identity when Console access is not allowed.

Choose one search variant for each run: current settings, a vector-only baseline,
or current search without reranking. This does not change the Brain or start
three runs automatically. Older runtimes must be upgraded before non-default
variants can run.

Inspect the actual answer, supplied excerpts, metric explanations and search
execution details. Completed means the test finished, not that the answer was
good. A score can be unavailable; a faithful answer can still rely on an outdated
or incorrect document. Comparable previous scores require matching source,
questions, processing, release, evaluation method and model provenance.

Matching completed variants appear side by side, retaining each answer, metric
explanation, actual excerpts, time and usage. Viewing history never starts more
paid runs. Partial or mismatched results remain readable, not comparable wins.

### Review Expected Evidence

Choose **Use as expected evidence** on an evaluated excerpt, or add its exact ID
under the question's **Expected evidence**. Grade it as not relevant, supporting,
relevant or essential, explicitly confirm review, then save the test settings.
Selecting an excerpt alone is not human review.

Reviewed labels enable exact-ID Recall@k, reciprocal rank and graded nDCG@k
without another model request. They measure the supplied known-relevant pool,
not every relevant chunk. Unjudged results receive no known relevance gain.
Missing, unreviewed, foreign-version or stale labels are **Not scored**, not
zero. After replacing a source, review its new IDs. The assistant may propose
unreviewed labels but cannot attest human review.

Saving labels changes the test plan, not the index. Failed or cancelled runs
retain observed partial stages and usage; cancellation does not undo provider
work already accepted. No replacement answer or successful score is invented.

**Improve document** prepares document processing and question changes.
**Improve Brain search** shares a bounded, attributed sample of the observed
results with the same Build with AI panel to prepare search settings. Historical
observations and partial samples remain labelled. Neither action publishes,
reindexes, creates keys, grants an allowance or approves external processing.
Matching comparison variants accompany the bounded assistant handoff, not only
the selected run. Source pages link to the single Brain search-settings editor
and report actual text-index readiness; unavailable readiness is not success.

Source-only evaluation reads that owned document's active indexed version. It
does not test business writes or establish quality for every knowledge source,
entity type, customer conversation or mixed-corpus question.
