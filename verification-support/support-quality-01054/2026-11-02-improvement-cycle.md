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
- [ ] Knowledge/generation: clarify optional custom-domain DNS versus the
  supplied standalone address and distinguish optional requirements generally.
- [ ] Qualification: focused contract, projection, trust-boundary, prompt,
  consumer, and released-artifact checks; exact staging deployment readback.
- [ ] Quality: repeat the original nine turns plus fixed paraphrases using real
  signed-in Support routes; retain every result and manually review semantics.
- [ ] Consider a four-action maximum only if the corrected run still lacks
  diagnostic budget; retain two iterations and compare explicitly.
- [ ] Production/template decision: adopt only the qualified changes, preserve
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
- Live implementation receipts and the bounded operator helper are private at
  `~/.codex/private/loomai-support-01054/quality-20261102/`. Existing production
  Support remains on its previous verified release until qualification.

Known bounded follow-up: JSON Schema string lengths and Java projection lengths
use different Unicode counting units. English summary boundaries 1/2000/2001
were verified; supplementary-character compatibility is not claimed in this
cycle. Do not silently widen or bypass projection budgets to hide that limitation.
