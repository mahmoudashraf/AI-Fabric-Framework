# Harbor Stronger-Model Comparison

LoomAI Labs | 14 November 2026 | Comparison complete; defaults not promoted

## Question And Controlled Change

Would a stronger model make the held generic quality candidate consistent enough
to promote? Compare the actual Engage app, not a replacement standalone prompt.

- Baseline: `gpt-5.4-mini-2026-03-17`, guided-a/guided-b from the
  [preceding qualification](./2026-11-14-quality-defaults.md).
- Trial: `gpt-5.4-2026-03-05`, full-a/full-b, with only
  `providerConfig.openaiModel` changed. Prompts, tools, evidence mappings,
  retrieval threshold 0.3, embeddings, indexing, auth, confirmation and limits
  remained identical. Both purpose-model overrides remained absent/inherited.
- The [official model documentation](https://developers.openai.com/api/docs/models/gpt-5.4)
  lists the snapshot and Chat Completions/structured-output support. The existing
  provider identity's model endpoint also confirmed account access before apply.
- The existing test deployment was already TEAM_AUTHORIZED, with its optional
  funded wrapper disabled. This was not changed. No funded model catalogue,
  financial allowance or credential permission was widened or bypassed.
- Same Brain, shared app consumer, both Engage installations, managed vector
  cluster, indexed file and AI-state database. No new infrastructure or vectors.

Trial version `ver-95585cd2`, release `rel-218f985a`, verification
`vrf-07d19625`: APPLIED_VERIFIED/PASSED. The runtime and available extraction /
generation diagnostics confirmed the full snapshot, not just a saved draft name.

## First-Response Results

The same six original questions ran once in each fresh anonymous conversation.
No production answer was retried, replaced, text-matched or graded into a pass.

- **Mini baseline: 4/6 and 5/6. Full model: 4/6 and 5/6.**
- Full-a passed product search, desk comparison, delivery restrictions and indexed
  returns. It failed the delivery-eligibility follow-up and asked which chair
  instead of giving the expected evidence-limited warranty answer. That warranty
  clarification was not a fabricated warranty, but was not a complete answer.
- Full-b passed every original question except the delivery follow-up.
- **R2 failed both times:** the opening explicitly claims free delivery, while the
  body correctly says GBP499 is below GBP500 and gives GBP524. Correct arithmetic
  in the body does not make the contradictory claim correct.
- The extended set improved from **2/4 to 4/4**: a GBP 500 subtotal produced
  free delivery and GBP 500 total; GBP 499 produced GBP 25 delivery and GBP 524
  total; unavailable
  Executive Desk and unused/original-packaging returns were correct.
- All read questions created zero enquiries. The natural enquiry clarified after
  the extended context, so it was not a natural-language completion pass. The
  explicit-ID enquiry required confirmation; one Confirm created one RECEIVED
  receipt; its real lookup created none. No email or stock reservation occurred.
  Reject was not reached in this conversation and is not claimed as requalified.

The full model improved the explicit boundary questions, but not the original
conversation criterion or the repeated delivery contradiction. The sample is
small and does not establish general model superiority or inferiority.

## Observed Latency

Across the twelve original turns per model, measured browser request/response
median was **6.18 seconds for mini** and **10.22 seconds for full**, about 1.65x.
Observed maximums were 9.18 and 17.20 seconds. These are small live samples, not
load-test percentiles; routes and generated lengths vary. The slower model does
not justify becoming the universal default on these results.

## Input And Provider Audit

Read-only inspection used the source matching framework 0.8.52 (`3bfcd272`), not
the older sibling checkout. The existing adapter supports the full model,
inherits absent purpose-model overrides, sends `max_completion_tokens` for GPT-5,
uses JSON-object mode for structured planning, and leaves reasoning effort
unspecified. Its normal temperature fallback is 0.3. CONCISE generation may use
400 tokens even when the provider limit is 2048; no truncation cause was established.

The prior traces include price GBP 499, delivery charge GBP 25 and threshold GBP 500.
The R2 trials have one INFORMATION intent with no direct answer; no separate
model answer was found being prepended to manufacture the contradiction.
The traced generation path uses the current user question and chronological
history. No arithmetic rewrite, stale-query substitution or concrete framework
assembly defect was proven. The exact failing provider envelope remains uncaptured.

One separate minimal-context diagnostic with the full model and the same verified
product/policy facts answered correctly: delivery available to London, free
delivery not available, total GBP 524. It was not an app retry or a passing replacement
for R2. This suggests auditing the real assembled prompt/history is more useful
than simply upgrading model size; it does not prove a causal prompt defect from
one simplified sample.

Diagnostic limitation: a supplemental mini request reached JSON parsing, then a
private harness recording error lost its response. It was not scored or replayed.
The recorder was corrected before the still-unattempted full-model diagnostic.
All actual Engage benchmark records were retained successfully and are unaffected.

## Decision And Follow-Up

**Do not promote the additional defaults or adopt full GPT-5.4 globally.**
The previous mini configuration was restored through normal publish/apply/verify,
preserving app identity and all other settings. The generic 1.4.0 template and
`public-knowledge-quality-20261113` remain selected on staging and production;
the 1.5.0 candidate remains HELD_NOT_QUALIFIED. No fresh candidate Kit was created.

Restored version `ver-a328ccdd`, release `rel-3d2911a9`, verification
`vrf-6589fdee`: **30 passed, 0 failed, 4 skipped**. Final read-only audit proved
every configuration object equals the pre-comparison snapshot, including the
mini model. Both original apps remain ACTIVE/ready on the same consumer. Both
control planes retain the original starter selections and unchanged generic1.4.0
manifest. The real customer still sees its unblocked old starter. A fresh browser
loaded the standalone workspace with HTTP200, visible composer and no page errors;
no paid query was sent during this restoration smoke check.

The unchanged generic/dealership builder tests also passed **26/26**. No serving
image rebuild, framework release, extra infrastructure or new Kit was required.

Next focused investigation: capture the actual final-generation messages/options
and response on a failing turn, redacting secrets and private metadata. Inspect
instruction/history interactions and coverage-versus-free-delivery conclusions.
Do not assume a framework fix, add semantic validators, strip the opening claim,
or introduce an unconfigured second generation call. Any proposed framework
change still requires owner review. For real commercial totals, an authoritative
read-only quote integration remains preferable to hidden framework business math.

## Evidence

Private first-response traces, snapshots, unchanged-config assertions, latency
inputs, native verification, intents and diagnostic limitations are retained at
`~/.codex/private/loomai-harbor-model-comparison-20261114/`.
Do not publish these captures or replay their consumed mutation intents.
No runtime/framework source or serving image changed in this comparison.
