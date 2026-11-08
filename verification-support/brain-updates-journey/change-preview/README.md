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

- 56 focused preview, Updates, release, workspace and UI-profile tests pass.
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

Hosted rollout and final independent recheck are pending at this checkpoint.
