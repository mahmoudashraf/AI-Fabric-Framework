# Kit Address Preview And Setup Check

Date: 2026-11-03

## Live Setup Before The UI Change

Used the signed-in Northfield Engage Canary customer Console. Selected the
Dealership simulator template and customer-provided AI-state PostgreSQL; no
connection credentials were provided and no storage was provisioned.

Pasting a full URL reproduces the reported generic address-format validation.
Entering `northfield-setup-check` passes and reaches review. **Create my
workspace** succeeds and opens kit `kit-7689758a-88a2-4335-b83b-db746b5c7159`,
draft deployment `dep-4466d153`, and installation
`awi_pub_c66c54afe24ccd397201c9446de3f587`. The saved app URL is
`https://northfield-setup-check.apps.loomai.pro`.

The kit remains **Not live yet**. Assignment, domain verification and app
delivery correctly require further setup. No infrastructure, paid service,
provider call, runtime release or public activation was started. One clearly
named configuration-only test kit is retained for review.

## UI Change

- Explicit **App address name** label, example and inline invalid-name guidance.
- Complete non-clickable URL beneath the field, updating from the current value.
- Same validated URL in the standalone canvas and final review.
- Invalid/incomplete values show an example, never a stale or malformed URL.
- Embedded-only configuration has no standalone-address field or preview.
- Existing domain-label contract and unchanged creation/update payloads; no AI,
  backend, template or authentication behavior changes.

## Qualification

51 focused kit, address-preview and auth tests pass, zero failures/skips. Tests
cover formatted labels, full URLs, spaces, uppercase, consecutive/boundary
hyphens, length bounds, empty/invalid presentation and embedded-only behavior.
Production UI build passes with existing runtime-config/chunk-size warnings.

Source `8e3de34c95bb06298b15160b2002c39def256b98` is pushed. Image workflow
`41633161039` passed with UI published and backend correctly skipped. Staging
job `up5h525irzamm0g2nizhvh58` and production job `hplamd97xu69uf67lbsqw9fz`
finished. Native UI containers are healthy with exact source OCI labels:
`/u67gepsowf7oo90dwjdx018r-121226247772` and
`/a6fz2ncuzzulg6lrzzwrjshu-121516740021`.

Production browser checks after the UI rollout:

- Empty name shows a labelled example. Typing `northfield` then adding `-ai`
  updates both field and canvas immediately, without submit.
- A full URL is invalid inline; the actual canvas address disappears and the
  field shows a labelled example, not a stale or malformed link.
- A 63-character name fits at actual 390px width without horizontal overflow.
- Light/dark surfaces are readable. Embedded-only hides both standalone field
  and preview; switching to both restores them.
- Review for both channels displays the complete app URL and website origin.
  No second kit was submitted during this post-change preview check.
- The saved draft kit still loads after reload. No deployment or activation
  was attempted; this is not an end-to-end live runtime qualification.

Screenshots: `kit-address-live-2026-11-03/`. Original theme restored, temporary
preview tab closed and browser viewport reset. The draft kit is retained for
owner review in the Northfield canary account.
