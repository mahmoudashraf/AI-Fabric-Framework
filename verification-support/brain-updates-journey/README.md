# Smart Brain Updates And Version Journey

Scope: owner-approved 010.64 follow-up. UI only; existing draft, publication,
apply, verification, promotion, assignment and authorization contracts remain.

## Implemented

- One labelled environment switch in the shared Brain header, not two selectors.
  Staging/Production are visible; duplicate authorized destinations require an
  explicit chooser. Loading, unavailable and in-flight states retain their locks.
- Review / Publish / Apply / Verify sequence, outcome-led saved changes and one
  task footer. Publication does not imply that a target has changed.
- First-class published-version grid with separate target application and
  verification states, plus search/pagination in All versions.
- Focused version details and exact-version/target Apply review. Runtime packages
  use the real compatible catalogue; no automatic package choice was introduced.
- Historical inspection is separate from the publication continuation. It cannot
  hijack the footer or make an old verification complete a new journey.
- Activity, prerequisites, app assignment and promotion remain distinct tasks.
  Existing production controls remain reachable when staging later fails.
- Complete existing technical version controls remain on a dedicated scoped
  Technical tools view, not nested in a large normal-task drawer. No new release
  engine, database, permission, framework or AI behavior change.

## Qualification

- Production UI build passed. Existing runtime-config script and bundle-size
  warnings remain visible and unchanged in kind.
- 113 focused Brain/setup/auth/Support/profile checks passed. The final smaller
  release/presentation suite is rerun after finishing copy and layout changes.
- `scripts/brain-updates.browser.mjs` passed 19 isolated browser scenarios:
  publication, apply/retry without republishing, stable retry identity, concurrent
  draft/target changes, missing compatible image, read-only access, first setup,
  version-only publication, version search/details, old-version apply, historical
  inspection, false completion prevention, and desktop/mobile layout.
- That harness mutates browser memory only. Its child release-task import is
  explicitly stubbed so it cannot load the real Platform API client; unexpected
  API traffic fails the run.
- API-backed local review used an authorized owned Support fixture and a
  read-only proxy. Environment chooser, retained target through Responses,
  version details, exact apply review/cancel, All versions search, Activity and
  Technical tools passed. No publication, apply, verification request, customer
  configuration, app assignment or AI query was performed.
- Real local layout: 1440x900 and 1280x800, dark/light; no page or horizontal
  overflow in normal desktop review. 390px navigation has natural vertical
  scrolling and no page-level horizontal overflow. Screenshots in `local/`.
- Focused independent review found four actionable edge cases; all were fixed.
  A recheck found one remaining duplicate-target evidence-lock omission, also
  corrected by the shared combined lock and guarded handler.
- Local fixture was observed signed out; temporary tab closed and viewport reset.

## Hosted Rollout

Pending exact-source UI image CI, staging-first deployment and live read-only
browser qualification. Backend/runtime remain on their existing versions.

Write-workflow proof is the isolated harness evidence above, not a claim of a
fresh production publish/apply or resource mutation.
