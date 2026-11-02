# Support Quality Improvement Cycle

## Scope

Owner approved the six findings from the live 2026-11-01 Support comparison.
Keep improvements reusable, preserve the original failed runs, and distinguish
deterministic contract correctness from real-provider answer quality. No lexical
semantic routing, hidden answer replacement, automatic search-all, new authority
grants, or unrelated infrastructure/recovery work is permitted.

## Tracked Work

- [x] Platform: separate bounded chain summary from full specialist answer;
  verify generated worker, projection, and manager contracts agree.
- [x] Runtime: preserve chain clarification/completion messages and mark
  `ASKED_USER` grounding insufficient without failing the whole conversation.
- [x] Runtime/framework: pass approved specialist findings as structured JSON,
  preserving useful text within the declared evidence budget.
- [x] Prompts: improve conversational subject resolution in the READ planner;
  distinguish approved product documentation from undisclosed internals.
- [x] Knowledge/generation: clarify optional custom-domain DNS versus the
  supplied standalone address and distinguish optional requirements generally.
- [x] Qualification: focused contract, projection, trust-boundary, prompt,
  consumer, and released-artifact checks; exact staging deployment readback.
- [x] Quality: repeat the original nine turns plus fixed paraphrases using real
  signed-in Support routes; retain every result and manually review semantics.
- [x] Consider a four-action maximum only if the corrected run still lacks
  diagnostic budget; retain two iterations and compare explicitly.
- [x] Production/template decision: adopt only the qualified changes, preserve
  app/deployment identity, and do not label mixed results all-green.

## Baseline

Original nine-turn refined staging: 6 pass, 2 partial, 1 fail. Production:
6 pass, 1 partial, 2 fail. The fixed four-turn production repetition remained
2 pass, 1 partial, 1 fail. Original receipts and reviews remain unchanged.

## Verification Boundaries

Character bounds protect executable projection and finite context contracts,
not semantic confidence. Clarification is a useful conversation outcome, not a
completed diagnosis. A successful action transport or chain status alone is
not proof of sufficient answer evidence. Existing authorization, tenant and
deployment ownership, confirmation, diagnostic consent, and provider spending
bounds remain enforced. No customer source file is changed without separately
authorized storage access; local knowledge edits alone are not a hosted update.

## Progress

- Framework patch: `c43d5dbee6311982c5aee82edb6bee8c61e91d5b`, immutable
  `ai-fabric-framework-v0.8.44`; release guards and 114 focused tests passed.
  Central publication workflow `41365943024` was started; publication and
  consumer resolution must be checked before claiming availability.
- Platform compiler commit `80c98b599823fd520e0a6d553773043db3a75a33` pushed;
  29 focused backend tests passed. Backend image workflow `41366680220` started.
- Runtime adapter: 26 focused adapter tests and three startup tests passed.
  A subsequent combined run exposed a contradictory new test expectation for
  message-only completion; the useful synthesis is preserved while empty
  findings remain insufficient diagnostic grounding. The focused adapter
  rerun passed. No conversation is rejected merely for incomplete evidence.
- Two Support guides were uploaded only to the authorized staging prefix.
  Normal customer refresh, preview, index and reconcile completed: both sources
  are `ACTIVE`, version 2. The other eleven guides and production sources were
  not changed at this checkpoint. Original source bytes were backed up privately.
- An initial uploader request had duplicate case-insensitive metadata keys and
  was rejected with a signature error. The unchanged object was read back before
  the corrected request. This was qualification tooling, not a product/auth fix.
- Additional fixed paraphrases cover original/reversed issue order and optional
  DNS. They supplement the original suite and have no automatic semantic grader.
- Central publication `41365943024` completed successfully. Runtime CI
  `41367502561` retained two failed version-reporting assertions still expecting
  0.8.43; its 588-test runtime run had no other failures. The assertions are
  updated to the new declared version, without bypassing the failed job.
- Runtime/source commit `26eb2cdee` is pushed. The first staging version is
  `ver-704e28d5`, with `reindexRequired=false`; runtime apply was not yet complete
  at that checkpoint.
- Corrected runtime source `bd915ab055013de28881515ecb5cf836a59053c6`
  passed workflow `41367990965`. The immutable image digest is
  `sha256:861af22e29d2e0ff119e642b7674288dd122ee3b5664cd5837f2d908bba4ca12`.
  Staging artifact `dsa-839d9e3f` is registered; release `rel-abaecf27` is applying.
- The initially empty Central-only consumer cache resolved 20 framework JARs
  at 0.8.44. Packaged core, execution, default and support curated JAR bytes
  match Central. Final consumer verification passed 893 tests with no skips.
  Initial version-assertion failures and a separate unchanged rate-limit timing
  test failure (45 ms against a 50 ms assertion) are retained; the full repeated
  connector/runtime suites passed without changing that rate limiter or test.
- Live implementation receipts and the bounded operator helper are private at
  `~/.codex/private/loomai-support-01054/quality-20261102/`. Existing production
  Support remains on its previous verified release until qualification.

Known bounded follow-up: JSON Schema string lengths and Java projection lengths
use different Unicode counting units. English summary boundaries 1/2000/2001
were verified; supplementary-character compatibility is not claimed in this
cycle. Do not silently widen or bypass projection budgets to hide that limitation.

## Final Hosted Result And Decision

Staging `rel-abaecf27` is `APPLIED_VERIFIED` / `PASSED`; the unchanged installation
uses binding revision 12. Runtime and connector are healthy on the exact digest
above, Desk target readiness is `ENABLED`, and all 13 sources remain `ACTIVE`.
The two revised guides remain active version 2. No new deployment, service,
database, customer identity or app was created.

Original nine-turn suite: **7 pass / 1 partial / 1 fail**, versus **6 / 2 / 1**
before this cycle. Median observed latency is 5,370 ms versus 11,984 ms; maximum
13,221 versus 20,501 ms. These are small-sample observations, not causal or fleet
performance claims. Structured approved specialist text now survives in a
3,195-character LLM evidence projection; the `ASKED_USER` clarification message
and `INSUFFICIENT` grounding are visible rather than a false completed diagnosis.

Six fixed supplementary turns: **2 pass / 2 partial / 2 fail**. The model still
misresolves the first/second issue even with native history, and one specialist
reports that no outdated policy was quoted without inspecting that answer or
the original files. Another answer conflates embedding a workspace in an existing
website with adding a custom hostname. All originals are retained in the primary
and supplementary JSON reviews. No script regrades them by word matching.

Two extra explicit diagnostic turns were useful and performed the actual selected
resource reads, but the model chose direct actions rather than a completed worker
chain. The compiler/framework projection boundary tests passed; this live sample
does not establish successful hosted worker-chain projection. No projection error
was observed across these turns, which is a narrower statement.

The four-action expansion is **not adopted**: the failing primary compound plan
declares `needsMoreSteps=false`, and the wrong-subject follow-up is not evidence
of a budget shortage. Planner READ-action limits are separate from a top-level
intent action; a turn may therefore show a direct specialist plus its bounded
READ plan. Do not label that an automatic limit bypass or assume four calls would
repair the model's semantic decision.

**Production is not upgraded and global defaults are not promoted.** Its current
Support release remains `rel-a1a27bce` on 0.8.43, with the original installation.
Generic TEMPLATE `1.0.3` / internal `1.0.2` reserve the non-secret measured
settings as source-only candidates. Earlier candidate and published template
bytes remain unchanged. Ordinary customer source/provider/agent bindings are
still required; no internal diagnostics or credentials are granted by a generic
template.

All eight generated cases were resolved and consent revoked; all 17 live turns
remained `AI_ACTIVE`, with no failed action transport, write or human transfer.
Desk lifecycle proof from the preceding cycle is retained as history, not relabelled
as a new full lifecycle run. Next semantic recommendations are recorded, not
silently implemented: better LLM-driven issue resolution, multi-issue synthesis
and evidence-claim discipline. No further framework release is queued.
