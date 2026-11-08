# Intuitive Updates Change Preview

Date: 8 November 2026. Scope: approved 010.64 follow-up, UI only.

## Implemented

- Clickable outcome rows with structured before/after highlights and field counts.
- Single Current / Proposed panel, exact environment/version and saved draft.
- Known profile labels/instructions, capability/source/collection identity changes,
  confirmation/access flags, supported model fields and workspace greeting/mode.
- Existing editor links retain exact target; read-only users receive View links.
- Arbitrary settings and secrets are not projected. Technical details retain
  changed paths only. Duplicate/missing item identity is not guessed by position.
- Missing evidence and first deployment are distinct. Current runtime can include
  compiler guidance absent from draft; this distinction is explicitly disclosed.
- Publication/apply/retry, permissions and staging developer profile unchanged.

## Verification

- 57 focused preview, Updates, release, workspace and UI-profile tests pass.
- 25 isolated browser scenarios pass, including existing publish/apply safety,
  zero-write preview navigation, safe technical fields, tone/instructions, added
  capability/source, unavailable baseline, new setup, dark/light and 390px layout.
  Harness writes are browser-memory only; real API traffic is forbidden.
- Production UI build passes with existing runtime-config and bundle warnings.
- Local API-backed owned fixture opens actual runtime/draft guidance read-only;
  source preview server blocks configuration writes. No customer mutation or
  provider query is needed for qualification.
- Illustrative fixture screenshots are in `local/`; they are not live evidence.

The independent review found and prompted focused regressions for malformed
boolean/access-mode projection, missing per-section applied evidence, generated
prompt provenance, canonical action/source/collection fields and an unsupported
behaviour editor link. These are corrected before rollout, not waived.

Final review also found cached-unknown evidence wording, compiler-only prompt
counts and ambiguous-list sibling attribution. These are corrected with authored
prompt projection, separate runtime differences and list-only identity checks.
No hidden semantic or execution-policy change was introduced.

## Hosted Closeout

Final source `a20d1b7b272a4420bad08a792e735365aa4f71ef`, following initial
implementation `e8e4b336a5c4267affdc58b8c68bdaf8a6622054`, is committed and
pushed on `Platform-V11`.
[Image CI 42463859075](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42463859075)
passed. Only the UI image was built; backend build skipped.

- Staging job `p4kgfsxcyd5w371zspj66926` finished; exact-source native UI
  container healthy. Owned Support fixture passed outcome row -> Current/Proposed,
  collapsed/expanded field details, unsupported-editor explanation and closing.
- Production job `mgjizfst8na1932lydaj1cic` finished; same exact-source native UI
  container healthy. Owned fixture passed actual runtime/draft comparison,
  Technical details, close, dark/light, editor link and target-preserving return.
- Desktop 1440x900 overview has no page/horizontal overflow. At 390x844 the panel
  stacks values, retains the footer and has no horizontal page overflow.
- Fresh public runtime configuration still reports developer on staging and
  standard on production. No backend, framework, database or customer runtime
  image was changed. Hosted checks made no publish/apply/validate request,
  draft save, app assignment, permission change, ticket or provider query.
- Both hosted test sessions were signed out, temporary tabs closed and viewport
  override reset. No captured console errors. Screenshots in `live/`.

Generated design images are proposals; `local/preview-*.png` are isolated memory
fixtures. Only `live/` captures are hosted UI evidence. Existing build warnings
are not claimed resolved by this scoped change.
