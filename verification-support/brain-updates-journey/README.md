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
- 113 focused Brain/setup/auth/Support/profile checks passed, followed by a final
  32-check release/presentation suite and 19 focused checks after count wording.
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
- Real local layout: 1440x900 and 1280x800; no page or horizontal
  overflow in normal desktop review. 390px navigation has natural vertical
  scrolling and no page-level horizontal overflow. Screenshots in `local/`.
- Focused independent review found four actionable edge cases; all were fixed.
  A recheck found one remaining duplicate-target evidence-lock omission, also
  corrected by the shared combined lock and guarded handler.
- Local fixture was observed signed out; temporary tab closed and viewport reset.

## Hosted Rollout

Initial source `01539661d0ef133653bf9bba06b63d6f9d4388cc` passed UI image CI
`42427318405` (backend skipped). Staging job `dz4h7yc9co4qmanh7gsweayn` finished;
the exact-source UI container is healthy. Live staging passed normal review,
explicit duplicate-destination selection, a genuinely undeployed Production
target, old-version inspection, exact apply review/cancel and version search.
No captured console errors or live release/configuration mutations.

Final wording-only source `f9654a9c745bcfc80f8b1bcd077cc57bf6c3978c` corrects
singular counts and retains unnamed entries in partial-label summaries.
[CI 42427637201](https://github.com/mahmoudashraf/AI-Fabric-Framework/actions/runs/42427637201)
passed; backend image build skipped. On 8 November 2026:

- Staging job `cxxsddueb3xdnhh101vwhvl2` finished; exact-source native UI
  container healthy. Final wording and the undeployed Production view verified.
- Production job `ych71fqbhp3tpbourqq544nf` finished; exact-source native UI
  container healthy. Both backend and customer runtime images remain unchanged.
- Production passed published versions, exact-version/environment Apply review
  and cancellation, historical inspection without changing publication intent,
  version search, Activity and existing Technical tools.
- Explicit destination choice and Staging context retention through Responses
  passed. Return to Production retained the selected exact destination.
- Production dark and light screenshots are in `live/`. Themes were checked on
  the scoped Brain workspace, not the outer document body. The 1440x900 review
  has no page-level scrolling or horizontal overflow. 390px version search and
  resource-drawer navigation have no horizontal page overflow.
- Staging developer and production standard profiles were retained. Both owned
  fixture sessions were observed signed out; temporary tabs closed and viewport
  override reset. No captured console errors on either hosted session.

No live Brain publish/apply, verification request, customer draft save, app
assignment, permission change, provider query or ticket submission was performed.
Only the Platform UI service image was updated through existing Coolify delivery.

Local screenshot names without a mode suffix are layout evidence; final hosted
`production-updates-dark.jpg` and `production-updates-light.jpg` are the confirmed
theme examples. The pictures in the proposal remain illustrative, not evidence.

Write-workflow proof is the isolated harness evidence above, not a claim of a
fresh production publish/apply or resource mutation.
